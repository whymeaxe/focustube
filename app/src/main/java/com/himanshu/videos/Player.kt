package com.himanshu.videos

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage

@Composable
fun PlayerScreen(
    videoId: String,
    video: Video?,
    creator: Creator?,
    related: List<Video>,
    onClose: () -> Unit,
    onPlay: (String) -> Unit,
) {
    val ctx = LocalContext.current
    BackHandler(onBack = onClose)

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        // Ambient backdrop: the thumbnail, heavily blurred (like a music app's album glow)
        AsyncImage(
            model = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(80.dp).alpha(0.55f),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0x66000000),
                    0.45f to Color(0xCC06110B),
                    1f to Color(0xF2000000),
                )
            )
        )

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(42.dp).glass(CircleShape).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.KeyboardArrowDown, "Close", tint = Color.White, modifier = Modifier.size(26.dp)) }
                Spacer(Modifier.weight(1f))
                Text(
                    "NOW PLAYING", color = HV.Sub, fontSize = 11.sp,
                    letterSpacing = 2.5.sp, fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(42.dp))
            }

            val playerShape = RoundedCornerShape(22.dp)
            val playerMod = if (video?.isShort == true) {
                Modifier.fillMaxWidth(0.62f).aspectRatio(9f / 16f)
            } else {
                Modifier.fillMaxWidth().padding(horizontal = 12.dp).aspectRatio(16f / 9f)
            }
            Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                key(videoId) {
                    YouTubePlayer(
                        videoId,
                        playerMod.clip(playerShape).border(1.dp, HV.Stroke, playerShape).background(Color.Black),
                    )
                }
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 60.dp),
            ) {
                item {
                    Text(
                        video?.title ?: "Loading…",
                        color = HV.Text, fontSize = 22.sp, lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(creator, Modifier.size(44.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                video?.channelName ?: creator?.name ?: "",
                                color = HV.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                listOf(video?.let { timeAgo(it.published) } ?: "", viewsText(video?.views ?: 0))
                                    .filter { it.isNotBlank() }.joinToString("  ·  "),
                                color = HV.Sub, fontSize = 13.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassAction(Icons.Filled.PlayArrow, "Open in YouTube", Modifier.weight(1f)) {
                            openInYouTube(ctx, videoId)
                        }
                        GlassAction(Icons.Filled.Share, "Share", Modifier.weight(0.6f)) {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, "https://youtu.be/$videoId")
                            ctx.startActivity(Intent.createChooser(send, "Share video"))
                        }
                    }
                    if (!video?.description.isNullOrBlank()) {
                        var expanded by remember(videoId) { mutableStateOf(false) }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            video!!.description,
                            color = HV.Sub, fontSize = 14.sp, lineHeight = 20.sp,
                            maxLines = if (expanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .glass(RoundedCornerShape(18.dp))
                                .clickable { expanded = !expanded }
                                .padding(16.dp)
                                .animateContentSize(),
                        )
                    }
                    if (related.isNotEmpty()) {
                        Spacer(Modifier.height(26.dp))
                        Text(
                            "MORE FROM ${(video?.channelName ?: "").uppercase()}",
                            color = HV.Faint, fontSize = 11.sp, letterSpacing = 2.5.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
                items(related, key = { it.id }) { v ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onPlay(v.id) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.width(150.dp).aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, HV.Stroke, RoundedCornerShape(14.dp))
                        ) { Thumb(v.id, Modifier.fillMaxSize()) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                v.title, color = HV.Text, fontSize = 14.sp, lineHeight = 19.sp,
                                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(timeAgo(v.published), color = HV.Sub, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassAction(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(48.dp)
            .glass(RoundedCornerShape(16.dp), strong = true)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = HV.Mint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = HV.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

fun openInYouTube(ctx: Context, id: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$id")))
    } catch (e: Exception) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$id")))
        } catch (_: Exception) {
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** YouTube's official embed player inside a WebView, with real fullscreen support. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubePlayer(videoId: String, modifier: Modifier) {
    val activity = LocalContext.current.findActivity()
    var customView by remember { mutableStateOf<View?>(null) }
    var customCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    fun exitFullscreen() {
        val a = activity ?: return
        customView?.let { (a.window.decorView as FrameLayout).removeView(it) }
        customView = null
        val cb = customCallback
        customCallback = null
        cb?.onCustomViewHidden()
        a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        WindowInsetsControllerCompat(a.window, a.window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }

    BackHandler(enabled = customView != null) { exitFullscreen() }
    DisposableEffect(Unit) { onDispose { if (customView != null) exitFullscreen() } }

    val html = """
        <!DOCTYPE html><html><head>
        <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
        <style>html,body{margin:0;padding:0;background:#000;height:100%;overflow:hidden}
        iframe{position:absolute;top:0;left:0;width:100%;height:100%;border:0}</style>
        </head><body>
        <iframe src="https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1&rel=0&modestbranding=1&fs=1"
          allow="autoplay; encrypted-media; picture-in-picture; fullscreen; web-share"
          referrerpolicy="strict-origin-when-cross-origin" allowfullscreen></iframe>
        </body></html>
    """.trimIndent()

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(android.graphics.Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                if (Build.VERSION.SDK_INT >= 26) settings.safeBrowsingEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                        val url = request?.url?.toString() ?: return false
                        // Taps on the YouTube logo / title inside the player -> open the YouTube app
                        if (request.isForMainFrame && (url.contains("youtube.com/watch") || url.contains("youtu.be/"))) {
                            Regex("(?:v=|youtu\\.be/)([\\w-]{11})").find(url)?.groupValues?.get(1)?.let { openInYouTube(ctx, it) }
                            return true
                        }
                        return false
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                        val a = activity ?: return
                        if (customView != null) { callback?.onCustomViewHidden(); return }
                        customView = view
                        customCallback = callback
                        view?.setBackgroundColor(android.graphics.Color.BLACK)
                        (a.window.decorView as FrameLayout).addView(
                            view,
                            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                        )
                        a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        WindowCompat.getInsetsController(a.window, a.window.decorView).apply {
                            hide(WindowInsetsCompat.Type.systemBars())
                            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        }
                    }

                    override fun onHideCustomView() { exitFullscreen() }
                }
                loadDataWithBaseURL("https://com.himanshu.videos", html, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.stopLoading(); it.loadUrl("about:blank"); it.destroy() },
    )
}
