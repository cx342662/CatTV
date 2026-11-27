package com.github.avbox.osc.util

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeCardSettingsTest {
    @Test fun layoutSurvivesSerializationAndIsScopedToSourceAndSection() {
        val first = updateHomeCardLayout("{}", "source-A", "movies", 3)
        val second = updateHomeCardLayout(first, "source-A", "series", 1)
        val third = updateHomeCardLayout(second, "source-B", "movies", 12)
        assertEquals(3, homeCardLayout(third, "source-A", "movies"))
        assertEquals(1, homeCardLayout(third, "source-A", "series"))
        assertEquals(12, homeCardLayout(third, "source-B", "movies"))
        assertEquals(-1, homeCardLayout(third, "source-C", "movies"))
    }

    @Test fun malformedAndUnknownValuesKeepExistingHomeLayout() {
        assertEquals(-1, homeCardLayout("broken", "source", "movies"))
        assertEquals(-1, homeCardLayout("{\"source\":{\"movies\":99}}", "source", "movies"))
        assertEquals(-1, homeCardLayout("{\"source\":null}", "source", "movies"))
    }

    @Test fun sourceAndSectionNamesAreEscaped() {
        val encoded = updateHomeCardLayout("{}", "a\"b", "x\\y", 13)
        assertEquals(13, homeCardLayout(encoded, "a\"b", "x\\y"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidSelectionCannotBeSaved() {
        updateHomeCardLayout("{}", "source", "movies", 14)
    }
}
