package com.github.avbox.osc.boot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootGuardGlassCrashTest {
    private fun lensCrash() = UnsupportedOperationException("unsupported shape").apply {
        stackTrace = arrayOf(
            StackTraceElement("com.kyant.backdrop.effects.LensKt", "lens", "Lens.kt", 62),
            StackTraceElement("com.github.avbox.osc.ui.components.GlassTopBarKt", "glassSurface", "GlassTopBar.kt", 89),
            StackTraceElement("androidx.compose.ui.node.LayoutNodeDrawScope", "draw", "LayoutNodeDrawScope.kt", 132),
        )
    }

    @Test fun glassRenderingCrashDoesNotDisableSource() {
        assertFalse(BootGuard.looksSourceRelated(lensCrash()))
    }

    @Test fun actualSourceFrameStillActivatesProtection() {
        val crash = lensCrash()
        crash.stackTrace = crash.stackTrace + StackTraceElement("com.example.spider.CustomSource", "homeContent", "CustomSource.kt", 10)
        assertTrue(BootGuard.looksSourceRelated(crash))
    }
}
