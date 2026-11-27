package com.github.avbox.osc.util

import org.junit.Assert.*
import org.junit.Test

class PlaybackLogSanitizerTest {
    @Test fun hidesQueryValuesAndUserInfo() {
        val text = PlaybackLogSanitizer.redact("https://user:pass@live.example/live/room.flv?wsSecret=abc&expires=123")
        assertFalse(text.contains("abc"))
        assertFalse(text.contains("pass"))
        assertFalse(text.contains("123"))
        assertTrue(text.contains("/live/room.flv"))
    }
    @Test fun hidesHeadersJsonAndStackMessages() {
        val text = PlaybackLogSanitizer.redact("Cookie: session=private; uid=hidden\nAuthorization: Bearer secret\n{\"token\":\"private-token\",\"parse\":0}")
        assertFalse(text.contains("private"))
        assertFalse(text.contains("hidden"))
        assertFalse(text.contains("Bearer"))
        assertTrue(text.contains("parse"))
    }
}
