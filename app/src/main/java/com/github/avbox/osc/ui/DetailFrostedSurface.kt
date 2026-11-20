package com.github.avbox.osc.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.github.avbox.osc.ui.components.LocalTopBarGlassBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur

@Composable
internal fun Modifier.detailFrostedSurface(shape: Shape): Modifier {
    val backdrop = LocalTopBarGlassBackdrop.current
    val radius = with(LocalDensity.current) { 18.dp.toPx() }
    val surface = if (backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = { blur(radius) },
            onDrawSurface = { drawRect(Color.White.copy(alpha = 0.10f)) },
        )
    } else clip(shape).background(Color.White.copy(alpha = 0.12f))
    return surface.border(0.8.dp, Color.White.copy(alpha = 0.35f), shape)
}
