package com.himanshu.videos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val creators: List<Creator> = emptyList(),
    val videos: List<Video> = emptyList(),
    val refreshing: Boolean = false,
    val error: String? = null,
    val update: UpdateInfo? = null,
    val updateProgress: Float? = null,
    val updateError: String? = null,
    val pip: Boolean = true,
    val keepAwake: Boolean = true,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val _state = MutableStateFlow(
        UiState(store.creators(), store.cachedFeed(), pip = store.pipEnabled(), keepAwake = store.keepAwake())
    )
    val state = _state.asStateFlow()
    private var refreshJob: Job? = null

    init {
        refresh()
        checkUpdate()
    }

    private var lastUpdateCheck = 0L
    private var dismissedCode = 0
    private var dismissedAt = 0L

    /** Runs every time the app comes to the foreground (throttled to once a minute). */
    fun checkUpdate() {
        val now = System.currentTimeMillis()
        if (now - lastUpdateCheck < 60_000L || _state.value.updateProgress != null) return
        lastUpdateCheck = now
        viewModelScope.launch {
            val u = Updater.check() ?: return@launch
            // "Later" hides a normal update for 6 hours; required updates always show.
            val snoozed = !u.required && u.code <= dismissedCode && System.currentTimeMillis() - dismissedAt < 6 * 3_600_000L
            if (!snoozed) _state.update { it.copy(update = u) }
        }
    }

    fun setPip(v: Boolean) { store.setPipEnabled(v); _state.update { it.copy(pip = v) } }
    fun setKeepAwake(v: Boolean) { store.setKeepAwake(v); _state.update { it.copy(keepAwake = v) } }

    /** Manual check from Settings. Returns true if an update was found (its card is shown). */
    suspend fun checkUpdateNow(): Boolean {
        val u = Updater.check() ?: return false
        _state.update { it.copy(update = u) }
        return true
    }

    fun dismissUpdate() {
        _state.value.update?.let { dismissedCode = it.code; dismissedAt = System.currentTimeMillis() }
        _state.update { it.copy(update = null, updateError = null) }
    }

    fun installUpdate() {
        val u = _state.value.update ?: return
        if (_state.value.updateProgress != null) return
        val app = getApplication<Application>()
        if (!Updater.canInstall(app)) {
            Updater.openInstallPermission(app)
            _state.update { it.copy(updateError = "Allow FocusTube to install updates, then come back and tap Update again.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(updateProgress = 0f, updateError = null) }
            try {
                Updater.downloadAndInstall(app, u) { p -> _state.update { it.copy(updateProgress = p) } }
                _state.update { it.copy(updateProgress = 1f) }
            } catch (e: Exception) {
                _state.update { it.copy(updateProgress = null, updateError = e.message ?: "Update failed. Try again.") }
                return@launch
            }
            // Installer is now on screen; reset so the button is usable if they cancel.
            kotlinx.coroutines.delay(4000)
            _state.update { it.copy(updateProgress = null) }
        }
    }

    fun reloadCache() {
        _state.update { it.copy(creators = store.creators(), videos = store.cachedFeed()) }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val creators = _state.value.creators
            if (creators.isEmpty()) {
                _state.update { it.copy(refreshing = false, error = null) }
                return@launch
            }
            _state.update { it.copy(refreshing = true, error = null) }
            val results = withContext(Dispatchers.IO) {
                creators.map { c -> async { c.id to runCatching { YouTube.feed(c.id).second }.getOrNull() } }.awaitAll()
            }
            val ok = results.filter { it.second != null }
            val failed = results.filter { it.second == null }.map { it.first }.toSet()
            val fetched = ok.flatMap { it.second!! }
            val ids = creators.map { it.id }.toSet()
            val keep = _state.value.videos.filter { it.channelId in failed && it.channelId in ids }
            val merged = (fetched + keep).distinctBy { it.id }.sortedByDescending { it.published }
            withContext(Dispatchers.IO) {
                store.saveFeed(merged)
                store.saveSeen(store.seen() + fetched.map { it.id })
                store.saveKnown(store.known() + ok.map { it.first })
            }
            _state.update {
                it.copy(
                    videos = merged,
                    refreshing = false,
                    error = if (ok.isEmpty()) "Couldn't reach YouTube. Pull down to retry." else null,
                )
            }
        }
    }

    /** Returns an error message, or null on success. */
    suspend fun addCreator(input: String): String? = try {
        val c = withContext(Dispatchers.IO) { YouTube.resolve(input) }
        if (_state.value.creators.any { it.id == c.id }) {
            "You already follow ${c.name}"
        } else {
            val list = _state.value.creators + c
            withContext(Dispatchers.IO) { store.saveCreators(list) }
            _state.update { it.copy(creators = list) }
            refresh()
            null
        }
    } catch (e: Exception) {
        e.message ?: "Couldn't add that creator"
    }

    fun removeCreator(c: Creator) {
        val list = _state.value.creators.filterNot { it.id == c.id }
        val vids = _state.value.videos.filterNot { it.channelId == c.id }
        _state.update { it.copy(creators = list, videos = vids) }
        viewModelScope.launch(Dispatchers.IO) {
            store.saveCreators(list)
            store.saveFeed(vids)
            store.saveKnown(store.known() - c.id)
        }
    }
}
