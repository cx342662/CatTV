package com.github.avbox.osc.util

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeCardSettingsTest {
    @Test fun onlyReferenceChoicesAreExposedAndOldExtraLayoutsFallBack() {
        assertEquals(listOf(1, 0, 5, 2, 8, 14), referenceHomeCardLayouts)
        assertEquals(-1, homeCardLayout("{\"source\":{\"movies\":3}}", "source", "movies"))
        assertEquals(-1, homeCardLayout("{\"source\":{\"movies\":9}}", "source", "movies"))
    }

    @Test fun disablingPreservesSelectionAndReenablingRestoresIt() {
        val selected = updateHomeCardLayout("{}", "source", "movies", 5)
        val off = toggleHomeCards(selected, false, "Vertical")
        assertEquals(false, homeCardsEnabled(off))
        assertEquals(-1, homeCardLayout(off, "source", "movies"))
        val on = toggleHomeCards(off, true, "Vertical")
        assertEquals(true, homeCardsEnabled(on))
        assertEquals(5, homeCardLayout(on, "source", "movies"))
        assertEquals(false, homeCardsEnabled("{}"))
    }

    @Test fun portraitDeckCanBeSavedAndRestored() {
        assertEquals(14, homeCardLayout(updateHomeCardLayout("{}", "source", "movies", 14), "source", "movies"))
    }
    @Test fun layoutSurvivesSerializationAndIsScopedToSourceAndSection() {
        val first = updateHomeCardLayout("{}", "source-A", "movies", 5)
        val second = updateHomeCardLayout(first, "source-A", "series", 1)
        val third = updateHomeCardLayout(second, "source-B", "movies", 8)
        assertEquals(5, homeCardLayout(third, "source-A", "movies"))
        assertEquals(1, homeCardLayout(third, "source-A", "series"))
        assertEquals(8, homeCardLayout(third, "source-B", "movies"))
        assertEquals(-1, homeCardLayout(third, "source-C", "movies"))
    }

    @Test fun malformedAndUnknownValuesKeepExistingHomeLayout() {
        assertEquals(-1, homeCardLayout("broken", "source", "movies"))
        assertEquals(-1, homeCardLayout("{\"source\":{\"movies\":99}}", "source", "movies"))
        assertEquals(-1, homeCardLayout("{\"source\":null}", "source", "movies"))
    }

    @Test fun sourceAndSectionNamesAreEscaped() {
        val encoded = updateHomeCardLayout("{}", "a\"b", "x\\y", 14)
        assertEquals(14, homeCardLayout(encoded, "a\"b", "x\\y"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidSelectionCannotBeSaved() {
        updateHomeCardLayout("{}", "source", "movies", 15)
    }
}

