package com.himanshu.videos

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Glass update card. When the update is required it covers the whole app and can't be dismissed. */
@Composable
fun UpdateOverlay(
    info: UpdateInfo,
    progress: Float?,
    error: String?,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
) {
    BackHandler { if (!info.required) onLater() }
    Box(
        Modifier
            .fillMaxSize()
            .background(if (info.required) Color(0xF2020403) else Color(0xB3000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (!info.required) onLater()
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF13281C), HV.Sheet)))
                .glass(RoundedCornerShape(30.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(24.dp),
        ) {
            Box(
                Modifier.size(52.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF46A577), HV.Forest))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown, null, tint = Color.White,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                if (info.required) "UPDATE REQUIRED" else "NEW VERSION",
                color = HV.Mint, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                info.name, color = HV.Text, fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "You're on v${BuildConfig.VERSION_NAME}" +
                    if (info.required) ". This version no longer works — update to keep using FocusTube." else "",
                color = HV.Sub, fontSize = 13.sp,
            )
            if (info.notes.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    info.notes, color = HV.Sub, fontSize = 14.sp, lineHeight = 20.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .glass(RoundedCornerShape(16.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                )
            }
            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(error, color = Color(0xFFFF9A8F), fontSize = 13.sp)
            }
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF8FD9B0), HV.Mint)))
                    .clickable(enabled = progress == null, onClick = onUpdate),
                contentAlignment = Alignment.Center,
            ) {
                if (progress == null) {
                    Text("Update now", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (progress >= 1f) "Opening installer…" else "Downloading ${(progress * 100).toInt()}%",
                            color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            color = Color.Black,
                            trackColor = Color(0x33000000),
                            modifier = Modifier.fillMaxWidth(0.6f).height(3.dp).clip(RoundedCornerShape(2.dp)),
                        )
                    }
                }
            }
            if (!info.required) {
                Text(
                    "Later", color = HV.Sub, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onLater)
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                )
            }
        }
    }
}
