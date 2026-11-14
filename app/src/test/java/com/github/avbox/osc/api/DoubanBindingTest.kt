package com.github.avbox.osc.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DoubanBindingTest {
    @Test
    fun acceptsSubjectIdsAndCanonicalLinks() {
        assertEquals("1306612", DoubanRatings.parseBinding(" 1306612 "))
        assertEquals("1306612", DoubanRatings.parseBinding("https://movie.douban.com/subject/1306612/?from=share"))
    }

    @Test
    fun rejectsOtherHostsAndPaths() {
        assertNull(DoubanRatings.parseBinding("https://movie.douban.com.example.org/subject/1306612/"))
        assertNull(DoubanRatings.parseBinding("https://movie.douban.com/subject/1306612/comments"))
        assertNull(DoubanRatings.parseBinding("0"))
    }
}
