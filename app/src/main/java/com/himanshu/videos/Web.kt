package com.himanshu.videos

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

const val DESIGNER_SITE = "https://himanshukhandelwal.in"

/** Full-screen in-app browser with a glass top bar. Back steps through page history, then closes. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen(url: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var host by remember { mutableStateOf(Uri.parse(url).host ?: url) }

    BackHandler {
        if (canGoBack) webView?.goBack() else onClose()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(HV.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        Box(Modifier.fillMaxWidth().background(HV.Sheet).statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(42.dp).glass(CircleShape).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.KeyboardArrowDown, "Close", tint = Color.White, modifier = Modifier.size(26.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Himanshu Khandelwal", color = HV.Text, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1,
                    )
                    Text(host, color = HV.Sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(
                    Modifier.size(42.dp).glass(CircleShape).clickable {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, webView?.url ?: url)
                        ctx.startActivity(Intent.createChooser(send, "Share"))
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Share, "Share", tint = HV.Mint, modifier = Modifier.size(20.dp)) }
            }
            if (progress in 0.01f..0.99f) {
                LinearProgressIndicator(
                    progress = { progress },
                    color = HV.Mint,
                    trackColor = Color.Transparent,
                    modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.BottomCenter),
                )
            }
        }

        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f).navigationBarsPadding()
                .clip(RoundedCornerShape(0.dp)),
            factory = { c ->
                WebView(c).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(android.graphics.Color.WHITE)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val u = request?.url ?: return false
                            // mailto:, tel:, whatsapp:, intent: etc. go to the right app
                            if (u.scheme != "http" && u.scheme != "https") {
                                runCatching { c.startActivity(Intent(Intent.ACTION_VIEW, u)) }
                                return true
                            }
                            return false
                        }

                        override fun onPageStarted(view: WebView?, u: String?, favicon: Bitmap?) {
                            u?.let { host = Uri.parse(it).host ?: it }
                        }

                        override fun doUpdateVisitedHistory(view: WebView?, u: String?, isReload: Boolean) {
                            canGoBack = view?.canGoBack() == true
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress / 100f
                        }
                    }
                    loadUrl(url)
                    webView = this
                }
            },
            onRelease = { it.stopLoading(); it.destroy() },
        )
    }
}
