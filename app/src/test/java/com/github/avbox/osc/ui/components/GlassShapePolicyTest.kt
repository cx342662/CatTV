package com.github.avbox.osc.ui.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassShapePolicyTest {
    @Test fun cloverUsesBlurWithoutUnsupportedLens() {
        assertFalse(supportsGlassLens(GenericShape { _, _ -> }))
    }

    @Test fun supportedButtonsRetainLens() {
        assertTrue(supportsGlassLens(CircleShape))
        assertTrue(supportsGlassLens(RoundedCornerShape(16.dp)))
    }
}

