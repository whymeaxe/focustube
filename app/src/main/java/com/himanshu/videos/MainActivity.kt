package com.himanshu.videos

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (savedInstanceState == null) handle(intent)
        setContent {
            HVTheme { AppRoot(vm, pendingVideo, pendingShare) }
        }
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
