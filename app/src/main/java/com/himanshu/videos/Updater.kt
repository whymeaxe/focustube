package com.himanshu.videos

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val code: Int,
    val name: String,
    val notes: String,
    val apkUrl: String,
    val required: Boolean,
)

/**
 * Self-updater for direct (non-Play) distribution.
 * Every GitHub release tagged "focustube-v<N>" with an .apk asset is a version N.
 * Put "[required]" in the release text to force every older install to update before use.
 */
object Updater {
    private const val UPDATE_CHANNEL = "app_updates"

    private fun http(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", "FocusTube/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
        }

    /** Newest release above the installed version, or null. Network errors return null. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        if (!BuildConfig.SELF_UPDATE) return@withContext null
        runCatching {
            val c = http("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases?per_page=30")
            val body = try {
                if (c.responseCode !in 200..299) return@runCatching null
                c.inputStream.bufferedReader().use { it.readText() }
            } finally { c.disconnect() }

            val arr = JSONArray(body)
            var best: UpdateInfo? = null
            var requiredAbove = false
            for (i in 0 until arr.length()) {
                val r = arr.getJSONObject(i)
                if (r.optBoolean("draft")) continue
                val tag = r.optString("tag_name")
                if (!tag.startsWith(BuildConfig.UPDATE_TAG_PREFIX)) continue
                val code = tag.removePrefix(BuildConfig.UPDATE_TAG_PREFIX).toIntOrNull() ?: continue
                val assets = r.optJSONArray("assets") ?: continue
                var apk: String? = null
                for (j in 0 until assets.length()) {
                    val a = assets.getJSONObject(j)
                    if (a.optString("name").endsWith(".apk")) { apk = a.optString("browser_download_url"); break }
                }
                if (apk.isNullOrBlank()) continue
                val text = r.optString("body")
                if (code > BuildConfig.VERSION_CODE && text.contains("[required]", ignoreCase = true)) requiredAbove = true
                if (best == null || code > best.code) {
                    best = UpdateInfo(
                        code = code,
                        name = r.optString("name").ifBlank { "FocusTube v1.0.$code" },
                        notes = text.replace(Regex("\\[required]", RegexOption.IGNORE_CASE), "").trim(),
                        apkUrl = apk,
                        required = text.contains("[required]", ignoreCase = true),
                    )
                }
            }
            // If any newer version is required, the newest one inherits "required".
            best?.takeIf { it.code > BuildConfig.VERSION_CODE }?.let { it.copy(required = it.required || requiredAbove) }
        }.getOrNull()
    }

    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    /** Opens the "Install unknown apps → Allow from this source" toggle for FocusTube. */
    fun openInstallPermission(ctx: Context) {
        val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }
    }

    /** Streams the APK straight into an install session, then hands off to Android's installer. */
    suspend fun downloadAndInstall(ctx: Context, info: UpdateInfo, onProgress: (Float) -> Unit) =
        withContext(Dispatchers.IO) {
            val installer = ctx.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(ctx.packageName)
                if (Build.VERSION.SDK_INT >= 31) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
            val sessionId = installer.createSession(params)
            val session = installer.openSession(sessionId)
            try {
                val c = http(info.apkUrl)
                try {
                    if (c.responseCode !in 200..299) throw IOException("Download failed (${c.responseCode})")
                    val total = c.contentLengthLong
                    session.openWrite("base.apk", 0, if (total > 0) total else -1).use { out ->
                        c.inputStream.use { input ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                if (total > 0) onProgress(done.toFloat() / total)
                            }
                        }
                        session.fsync(out)
                    }
                } finally { c.disconnect() }

                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val pi = PendingIntent.getBroadcast(
                    ctx, sessionId, Intent(ctx, InstallReceiver::class.java).setPackage(ctx.packageName), flags
                )
                session.commit(pi.intentSender)
            } catch (e: Exception) {
                session.abandon()
                throw e
            } finally {
                session.close()
            }
        }

    /** Called by the background worker: one notification per new version. */
    @SuppressLint("MissingPermission")
    suspend fun notifyIfNew(ctx: Context) {
        val info = check() ?: return
        val p = ctx.getSharedPreferences("hv", Context.MODE_PRIVATE)
        if (p.getInt("update_notified", 0) >= info.code) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val ch = NotificationChannel(UPDATE_CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT)
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        val open = PendingIntent.getActivity(
            ctx, 7001,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, UPDATE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(0xFF2F7A55.toInt())
            .setContentTitle(if (info.required) "Required update available" else "FocusTube update available")
            .setContentText("${info.name} — tap to update")
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(ctx).notify(7001, n)
        p.edit().putInt("update_notified", info.code).apply()
    }
}

/** Receives install-session results and launches Android's confirmation screen when needed. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { ctx.startActivity(confirm) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> Unit
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                Toast.makeText(
                    ctx,
                    "This update is signed differently. Uninstall FocusTube and install the latest version again",
                    Toast.LENGTH_LONG,
                ).show()
            else -> Toast.makeText(
                ctx,
                "Update failed: " + (intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "unknown error"),
                Toast.LENGTH_LONG,
            ).show()
        }
    }
}
