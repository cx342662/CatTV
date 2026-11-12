package com.github.avbox.osc.bean

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DoubanScoreTest {
    @Test
    fun detailCarriesValidSourceScore() {
        val source = AbsJson().AbsJsonVod().apply { vod_douban_score = " 8.2 " }
        assertEquals(8.2, VodInfo.fromVideo(source.toXmlVideo()).doubanScore)
    }

    @Test
    fun invalidOrMissingScoreIsOmitted() {
        val source = AbsJson().AbsJsonVod()
        for (score in listOf(null, "", "NaN", "0", "10.1")) {
            source.vod_douban_score = score
            assertNull(VodInfo.fromVideo(source.toXmlVideo()).doubanScore)
        }
    }
}
