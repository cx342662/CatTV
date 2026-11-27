package com.kyant.backdrop.effects

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.graphics.Shape
import com.kyant.shapes.RoundedRectangularShape

fun supportsLensShape(shape: Shape): Boolean =
    shape is CornerBasedShape || shape is RoundedRectangularShape
