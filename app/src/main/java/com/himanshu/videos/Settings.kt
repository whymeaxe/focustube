package com.himanshu.videos

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    s: UiState,
    pipSupported: Boolean,
    onPip: (Boolean) -> Unit,
    onKeepAwake: (Boolean) -> Unit,
    onCheckUpdate: suspend () -> Boolean,
    onOpenSite: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = HV.Sheet,
        scrimColor = Color(0xB3000000),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 30.dp)) {
            Text("Settings", color = HV.Text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp)
            Spacer(Modifier.height(18.dp))

            Group("WATCHING")
            Panel {
                ToggleRow(
                    title = "Picture-in-picture",
                    subtitle = if (pipSupported) "Leave the app and the video keeps playing in a small floating window"
                    else "Not supported on this phone",
                    checked = s.pip && pipSupported,
                    enabled = pipSupported,
                    onChange = onPip,
                )
                Divider()
                ToggleRow(
                    title = "Keep screen on",
                    subtitle = "Stop the screen from turning off while a video is open",
                    checked = s.keepAwake,
                    onChange = onKeepAwake,
                )
            }

            Spacer(Modifier.height(18.dp))
            Group("ALERTS")
            Panel {
                LinkRow("Upload notifications", "Sound, vibration and lock-screen options") {
                    val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { ctx.startActivity(i) }
                }
            }

            Spacer(Modifier.height(18.dp))
            Group("APP")
            Panel {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !checking) {
                            scope.launch {
                                checking = true
                                val found = onCheckUpdate()
                                checking = false
                                if (found) onDismiss()
                                else Toast.makeText(ctx, "You're on the latest version", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Check for updates", color = HV.Text, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
                        Text("Version ${BuildConfig.VERSION_NAME}", color = HV.Sub, fontSize = 13.sp)
                    }
                    if (checking) CircularProgressIndicator(color = HV.Mint, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    else Icon(Icons.Filled.KeyboardArrowRight, null, tint = HV.Faint)
                }
                Divider()
                LinkRow("Designed by Himanshu Khandelwal", "himanshukhandelwal.in", onOpenSite)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Independent app · not affiliated with YouTube or Google",
                color = HV.Faint, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun Group(label: String) {
    Text(
        label, color = HV.Faint, fontSize = 11.sp, letterSpacing = 2.5.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp))) { content() }
}

@Composable
private fun Divider() {
    HorizontalDivider(color = HV.Stroke, thickness = 1.dp, modifier = Modifier.padding(horizontal = 18.dp))
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) HV.Text else HV.Faint, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = HV.Sub, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF05130B),
                checkedTrackColor = HV.Mint,
                checkedBorderColor = HV.Mint,
                uncheckedThumbColor = HV.Sub,
                uncheckedTrackColor = Color(0x14FFFFFF),
                uncheckedBorderColor = HV.Stroke,
            ),
        )
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = HV.Text, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = HV.Sub, fontSize = 13.sp)
        }
        Box(Modifier.width(4.dp))
        Icon(Icons.Filled.KeyboardArrowRight, null, tint = HV.Faint)
    }
}
