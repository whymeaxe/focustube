package com.himanshu.videos

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {
    companion object { const val EXTRA_VIDEO = "videoId" }

    private val vm: MainViewModel by viewModels()
    private val pendingVideo = mutableStateOf<String?>(null)
    private val pendingShare = mutableStateOf<String?>(null)
    private val inPip = mutableStateOf(false)

    private var pipAllowed = false
    private var pipRatio = Rational(16, 9)

    val pipSupported: Boolean
        get() = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (savedInstanceState == null) handle(intent)
        setContent {
            HVTheme {
                AppRoot(
                    vm, pendingVideo, pendingShare,
                    inPip = inPip.value,
                    pipSupported = pipSupported,
                    onPipChange = ::setPip,
                )
            }
        }
    }

    /** Called by the UI whenever a video opens/closes or the setting changes. */
    fun setPip(allowed: Boolean, vertical: Boolean) {
        pipAllowed = allowed && pipSupported
        pipRatio = if (vertical) Rational(9, 16) else Rational(16, 9)
        if (Build.VERSION.SDK_INT >= 31) runCatching { setPictureInPictureParams(pipParams()) }
    }

    private fun pipParams(): PictureInPictureParams =
        PictureInPictureParams.Builder()
            .setAspectRatio(pipRatio)
            .apply {
                if (Build.VERSION.SDK_INT >= 31) {
                    setAutoEnterEnabled(pipAllowed)
                    setSeamlessResizeEnabled(true)
                }
            }
            .build()

    // Android 8-11: shrink into a small window when the user presses Home while a video is open.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (pipAllowed && Build.VERSION.SDK_INT < 31) runCatching { enterPictureInPictureMode(pipParams()) }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        if (i == null) return
        i.getStringExtra(EXTRA_VIDEO)?.let {
            vm.reloadCache()
            pendingVideo.value = it
        }
        if (i.action == Intent.ACTION_SEND) {
            pendingShare.value = i.getStringExtra(Intent.EXTRA_TEXT)
        }
    }
}
