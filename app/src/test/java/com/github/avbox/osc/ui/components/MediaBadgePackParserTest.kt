package com.github.avbox.osc.ui.components

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.github.avbox.osc.util.kvcodec.KVDecoder
import com.github.avbox.osc.util.kv.KVKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [35])
class MediaBadgePackParserTest {
    @Test fun savedLibraryAndTogglesRoundTripThroughExistingCodec() {
        val codec = KVDecoder().apply { setRegistry(KVKeySpec()) }
        val saved = """{"urls":["https://example.com/badges.json"],"selected":"https://example.com/badges.json"}"""
        val encoded = codec.encode(saved)
        assertEquals(saved, codec.decode<String>("media_badges_library", encoded, null))
        assertEquals(false, codec.decode<Boolean>("media_badges_enabled", codec.encode(false), true))
        assertEquals(true, codec.decode<Boolean>("background_playback", codec.encode(true), false))
    }

    @Test fun rejectsDisabledAndInvalidRules() {
        val rules = MediaBadgePackParser.parse("""{"filters":[{"name":"disabled","pattern":".*","isEnabled":false},{"name":"broken","pattern":"["},{"name":"valid","pattern":"4k","imageURL":"http://example.com/a.svg"}]}""")
        assertEquals(1, rules.size)
        assertNull(rules.single().image)
    }
}
