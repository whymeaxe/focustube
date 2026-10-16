package com.himanshu.videos

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private const val DAY = 86_400_000L

@Composable
fun AppRoot(
    vm: MainViewModel,
    pendingVideo: MutableState<String?>,
    pendingShare: MutableState<String?>,
) {
    val s by vm.state.collectAsStateWithLifecycle()
    var playing by rememberSaveable { mutableStateOf<String?>(null) }
    var lastPlaying by rememberSaveable { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var addPrefill by remember { mutableStateOf("") }
    var filterCreator by rememberSaveable { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var toRemove by remember { mutableStateOf<Creator?>(null) }
    var showSite by rememberSaveable { mutableStateOf(false) }

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(pendingVideo.value) {
        pendingVideo.value?.let { playing = it; lastPlaying = it; pendingVideo.value = null }
    }
    LaunchedEffect(pendingShare.value) {
        pendingShare.value?.let { addPrefill = it; showAdd = true; pendingShare.value = null }
    }

    val creatorMap = remember(s.creators) { s.creators.associateBy { it.id } }

    Box(Modifier.fillMaxSize().background(HV.Black)) {
        AppBackground()

        FeedScreen(
            s = s,
            creatorMap = creatorMap,
            filterCreator = filterCreator,
            onFilter = { filterCreator = if (filterCreator == it) null else it },
            tab = tab,
            onTab = { tab = it },
            onRefresh = vm::refresh,
            onPlay = { playing = it; lastPlaying = it },
            onLongPressCreator = { toRemove = it },
            onAdd = { addPrefill = ""; showAdd = true },
            onOpenSite = { showSite = true },
        )

        // Floating "+" — glass orb, bottom right
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(22.dp)
                .size(64.dp)
                .shadow(28.dp, CircleShape, ambientColor = HV.Moss, spotColor = HV.Moss)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF46A577), Color(0xFF1E5A3C), Color(0xFF0E2E1E))))
                .border(1.dp, Brush.verticalGradient(listOf(Color(0x80FFFFFF), Color(0x10FFFFFF))), CircleShape)
                .clickable { addPrefill = ""; showAdd = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add creator", tint = Color.White, modifier = Modifier.size(30.dp))
        }

        AnimatedVisibility(
            visible = playing != null,
            enter = slideInVertically(tween(380)) { it } + fadeIn(tween(250)),
            exit = slideOutVertically(tween(320)) { it } + fadeOut(tween(250)),
        ) {
            val id = playing ?: lastPlaying
            if (id != null) {
                val video = s.videos.firstOrNull { it.id == id }
                PlayerScreen(
                    videoId = id,
                    video = video,
                    creator = video?.let { creatorMap[it.channelId] },
                    related = s.videos.filter { it.channelId == video?.channelId && it.id != id }.take(12),
                    onClose = { playing = null },
                    onPlay = { playing = it; lastPlaying = it },
                )
            }
        }

        AnimatedVisibility(
            visible = showSite,
            enter = slideInVertically(tween(380)) { it } + fadeIn(tween(250)),
            exit = slideOutVertically(tween(320)) { it } + fadeOut(tween(250)),
        ) {
            WebScreen(url = DESIGNER_SITE, onClose = { showSite = false })
        }

        s.update?.let { u ->
            UpdateOverlay(
                info = u,
                progress = s.updateProgress,
                error = s.updateError,
                onUpdate = vm::installUpdate,
                onLater = vm::dismissUpdate,
            )
        }
    }

    if (showAdd) {
        AddCreatorSheet(
            prefill = addPrefill,
            creators = s.creators,
            onAdd = { vm.addCreator(it) },
            onRemove = { toRemove = it },
            onDismiss = { showAdd = false },
        )
    }

    toRemove?.let { c ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            containerColor = HV.Sheet,
            title = { Text("Unfollow ${c.name}?", color = HV.Text, fontWeight = FontWeight.Bold) },
            text = { Text("Their videos disappear from your feed and you stop getting upload alerts.", color = HV.Sub) },
            confirmButton = {
                TextButton(onClick = {
                    if (filterCreator == c.id) filterCreator = null
                    vm.removeCreator(c); toRemove = null
                }) { Text("Unfollow", color = Color(0xFFFF9A8F)) }
            },
            dismissButton = { TextButton(onClick = { toRemove = null }) { Text("Keep", color = HV.Mint) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedScreen(
    s: UiState,
    creatorMap: Map<String, Creator>,
    filterCreator: String?,
    onFilter: (String) -> Unit,
    tab: Int,
    onTab: (Int) -> Unit,
    onRefresh: () -> Unit,
    onPlay: (String) -> Unit,
    onLongPressCreator: (Creator) -> Unit,
    onAdd: () -> Unit,
    onOpenSite: () -> Unit,
) {
    val shown = remember(s.videos, filterCreator, tab) {
        s.videos.filter { v ->
            (filterCreator == null || v.channelId == filterCreator) &&
                when (tab) { 1 -> !v.isShort; 2 -> v.isShort; else -> true }
        }
    }
    PullToRefreshBox(isRefreshing = s.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp),
        ) {
            item { Header(s) }

            if (s.creators.isNotEmpty()) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(top = 18.dp),
                    ) {
                        items(s.creators, key = { it.id }) { c ->
                            CreatorBubble(
                                c = c,
                                selected = filterCreator == c.id,
                                dimmed = filterCreator != null && filterCreator != c.id,
                                onClick = { onFilter(c.id) },
                                onLongClick = { onLongPressCreator(c) },
                            )
                        }
                    }
                }
                item { Tabs(tab, onTab) }
            }

            if (s.error != null) {
                item {
                    Text(
                        s.error, color = Color(0xFFFF9A8F), fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
                    )
                }
            }

            if (s.creators.isEmpty()) {
                item { EmptyState(onAdd) }
            } else if (shown.isEmpty() && !s.refreshing) {
                item {
                    Text(
                        "Nothing here yet. Pull down to refresh.",
                        color = HV.Sub, fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().padding(40.dp),
                    )
                }
            }

            items(shown, key = { it.id }) { v ->
                VideoCard(v, creatorMap[v.channelId], onClick = { onPlay(v.id) })
            }

            item { DesignerCredit(onOpenSite) }
        }
    }
}

@Composable
private fun Header(s: UiState) {
    Column(
        Modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 22.dp, end = 22.dp, top = 26.dp)
    ) {
        Text(
            "ONLY YOUR CREATORS",
            color = HV.Mint.copy(alpha = 0.8f), fontSize = 11.sp,
            letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            buildAnnotatedString {
                append("Focus")
                withStyle(SpanStyle(color = HV.Mint)) { append("Tube") }
            },
            color = HV.Text, fontSize = 48.sp, lineHeight = 50.sp,
            fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill("${s.creators.size} following")
            Spacer(Modifier.width(8.dp))
            val today = s.videos.count { System.currentTimeMillis() - it.published < DAY }
            Pill(if (today > 0) "$today new today" else "${s.videos.size} videos", accent = today > 0)
        }
    }
}

/** "Designed by Himanshu Khandelwal" signature — tapping opens his site inside the app. */
@Composable
private fun DesignerCredit(onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.width(36.dp).height(1.dp)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, HV.Faint, Color.Transparent)))
        )
        Spacer(Modifier.height(18.dp))
        Column(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "DESIGNED BY",
                color = HV.Faint, fontSize = 10.sp, letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Himanshu Khandelwal",
                color = HV.Sub, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(2.dp))
            Text("himanshukhandelwal.in ↗", color = HV.Mint.copy(alpha = 0.7f), fontSize = 11.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Independent app · not affiliated with YouTube or Google",
            color = HV.Faint, fontSize = 10.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

@Composable
private fun Pill(text: String, accent: Boolean = false) {
    Box(
        Modifier
            .glass(RoundedCornerShape(50))
            .then(if (accent) Modifier.background(HV.Moss.copy(alpha = 0.35f)) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, color = if (accent) HV.Mint else HV.Sub, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CreatorBubble(c: Creator, selected: Boolean, dimmed: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            Modifier
                .size(66.dp)
                .clip(CircleShape)
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) Brush.linearGradient(listOf(HV.Mint, HV.Moss))
                    else Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x0AFFFFFF))),
                    CircleShape,
                )
                .padding(if (selected) 4.dp else 3.dp),
        ) {
            Avatar(c, Modifier.fillMaxSize(), alpha = if (dimmed) 0.4f else 1f)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            c.name, color = if (dimmed) HV.Faint else HV.Text, fontSize = 11.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun Avatar(c: Creator?, modifier: Modifier, alpha: Float = 1f) {
    Box(
        modifier.clip(CircleShape).background(Brush.linearGradient(listOf(HV.Forest, HV.Deep))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            (c?.name?.firstOrNull() ?: '•').uppercase(),
            color = HV.Mint, fontWeight = FontWeight.Bold, fontSize = 18.sp,
        )
        if (c?.avatar != null) {
            AsyncImage(
                model = c.avatar, contentDescription = c.name, contentScale = ContentScale.Crop,
                alpha = alpha, modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun Tabs(tab: Int, onTab: (Int) -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .glass(RoundedCornerShape(50))
            .padding(4.dp)
    ) {
        listOf("All", "Videos", "Shorts").forEachIndexed { i, label ->
            val on = tab == i
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .then(
                        if (on) Modifier.background(Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x1AFFFFFF))))
                        else Modifier
                    )
                    .clickable { onTab(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, color = if (on) HV.Text else HV.Sub, fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        Modifier
            .padding(20.dp)
            .padding(top = 30.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(28.dp))
            .clickable(onClick = onAdd)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(58.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(HV.Moss, HV.Forest))),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.height(16.dp))
        Text("Follow your first creator", color = HV.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Tap + and paste a channel link or @handle. Your feed will show their uploads — nothing else.",
            color = HV.Sub, fontSize = 14.sp, lineHeight = 20.sp,
        )
    }
}

@Composable
private fun VideoCard(v: Video, creator: Creator?, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(22.dp))
                .background(HV.Deep)
                .border(1.dp, HV.Stroke, RoundedCornerShape(22.dp))
        ) {
            Thumb(v.id, Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color(0x99000000))
                )
            )
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (System.currentTimeMillis() - v.published < DAY) Badge("NEW", HV.Mint, Color.Black)
                if (v.isShort) Badge("SHORT", Color(0x66000000), Color.White)
            }
            Box(
                Modifier.align(Alignment.BottomEnd).padding(12.dp).size(38.dp)
                    .glass(CircleShape, strong = true),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
        }
        Row(Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp)) {
            Avatar(creator, Modifier.size(38.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    v.title, color = HV.Text, fontSize = 16.sp, lineHeight = 21.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    letterSpacing = (-0.2).sp,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    listOf(v.channelName, timeAgo(v.published), viewsText(v.views)).filter { it.isNotBlank() }
                        .joinToString("  ·  "),
                    color = HV.Sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Badge(text: String, bg: Color, fg: Color) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

/** Sharp 1280px thumbnail with automatic fallback for older uploads. */
@Composable
fun Thumb(id: String, modifier: Modifier) {
    var fallback by remember(id) { mutableStateOf(false) }
    AsyncImage(
        model = if (fallback) "https://i.ytimg.com/vi/$id/hqdefault.jpg" else "https://i.ytimg.com/vi/$id/maxresdefault.jpg",
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onError = { fallback = true },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCreatorSheet(
    prefill: String,
    creators: List<Creator>,
    onAdd: suspend (String) -> String?,
    onRemove: (Creator) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf(prefill) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (loading || text.isBlank()) return
        scope.launch {
            loading = true; error = null
            val err = onAdd(text)
            loading = false
            if (err == null) {
                text = ""
                sheet.hide(); onDismiss()
            } else error = err
        }
    }
    LaunchedEffect(prefill) { if (prefill.isNotBlank()) submit() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = HV.Sheet,
        scrimColor = Color(0xB3000000),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 26.dp).imePadding()) {
            Text("Follow a creator", color = HV.Text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "Paste a channel link, an @handle, or any video link from them. You'll get a notification whenever they upload.",
                color = HV.Sub, fontSize = 14.sp, lineHeight = 20.sp,
            )
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; error = null },
                placeholder = { Text("@mkbhd or youtube.com/@…", color = HV.Faint) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                trailingIcon = {
                    Text(
                        "Paste", color = HV.Mint, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp))
                            .clickable { clipboard.getText()?.text?.let { text = it } }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HV.Mint.copy(alpha = 0.6f),
                    unfocusedBorderColor = HV.Stroke,
                    focusedContainerColor = Color(0x0FFFFFFF),
                    unfocusedContainerColor = Color(0x0AFFFFFF),
                    focusedTextColor = HV.Text,
                    unfocusedTextColor = HV.Text,
                    cursorColor = HV.Mint,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            if (error != null) {
                Text(error!!, color = Color(0xFFFF9A8F), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
            }
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF8FD9B0), HV.Mint)))
                    .clickable(enabled = !loading) { submit() },
                contentAlignment = Alignment.Center,
            ) {
                if (loading) CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                else Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Notifications, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Follow & notify me", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            if (creators.isNotEmpty()) {
                Spacer(Modifier.height(26.dp))
                Text("FOLLOWING", color = HV.Faint, fontSize = 11.sp, letterSpacing = 2.5.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.fillMaxWidth().height((creators.size.coerceAtMost(5) * 60).dp)) {
                    items(creators, key = { it.id }) { c ->
                        Row(
                            Modifier.fillMaxWidth().height(60.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(c, Modifier.size(40.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                c.name, color = HV.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Box(
                                Modifier.size(36.dp).glass(CircleShape).clickable { onRemove(c) },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.Close, "Unfollow", tint = HV.Sub, modifier = Modifier.size(18.dp)) }
                        }
                    }
                }
            }
        }
    }
}
