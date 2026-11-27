package com.github.avbox.osc.ui.components

import androidx.compose.ui.graphics.Shape
import com.kyant.backdrop.effects.supportsLensShape

internal fun supportsGlassLens(shape: Shape): Boolean =
    supportsLensShape(shape)
