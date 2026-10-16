package com.himanshu.videos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

object HV {
    val Black = Color(0xFF020403)
    val Deep = Color(0xFF06110B)
    val Forest = Color(0xFF1E5A3C)
    val Moss = Color(0xFF2F7A55)
    val Mint = Color(0xFFA9E5C3)
    val Text = Color(0xFFF2F6F3)
    val Sub = Color(0x99E6EFE9)
    val Faint = Color(0x55E6EFE9)
    val Stroke = Color(0x1FFFFFFF)
    val Sheet = Color(0xFF0A130E)
}

@Composable
fun HVTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = HV.Mint,
            onPrimary = Color.Black,
            secondary = HV.Moss,
            background = HV.Black,
            surface = HV.Sheet,
            onSurface = HV.Text,
            onBackground = HV.Text,
            surfaceContainerHigh = HV.Sheet,
            error = Color(0xFFFF9A8F),
        ),
        content = content,
    )
}

/** Frosted glass: soft white fill gradient + light-catching hairline border. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(24.dp), strong: Boolean = false): Modifier =
    this.clip(shape)
        .background(
            Brush.verticalGradient(
                if (strong) listOf(Color(0x33FFFFFF), Color(0x14FFFFFF))
                else listOf(Color(0x1FFFFFFF), Color(0x08FFFFFF))
            )
        )
        .border(
            1.dp,
            Brush.verticalGradient(listOf(Color(0x47FFFFFF), Color(0x0AFFFFFF))),
            shape,
        )

@Composable
fun AppBackground() {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to Color(0xFF0B2216),
                0.35f to HV.Deep,
                0.75f to HV.Black,
                1f to Color.Black,
            )
        )
    )
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                listOf(Color(0x5531875C), Color.Transparent),
                center = Offset(-80f, -40f),
                radius = 1300f,
            )
        )
    )
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                listOf(Color(0x221E5A3C), Color.Transparent),
                center = Offset(1400f, 2600f),
                radius = 1100f,
            )
        )
    )
}
