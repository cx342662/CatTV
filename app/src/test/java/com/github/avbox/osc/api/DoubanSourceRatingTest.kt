package com.github.avbox.osc.api

import com.github.avbox.osc.bean.Movie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DoubanSourceRatingTest {
    @Test fun labeledRatingWorksOnAnySource() {
        val video = Movie.Video().apply { note = "豆瓣评分：8.6" }
        assertEquals(8.6, DoubanRatings.sourceScore(video, false))
    }

    @Test fun unlabeledScoreOnlyBelongsToDoubanSource() {
        val video = Movie.Video().apply { note = "8.6" }
        assertNull(DoubanRatings.sourceScore(video, false))
        assertEquals(8.6, DoubanRatings.sourceScore(video, true))
        video.note = "更新至8集"
        assertNull(DoubanRatings.sourceScore(video, true))
    }

    @Test fun sourceFieldTakesPriorityAndInvalidValuesAreRejected() {
        val video = Movie.Video().apply { doubanScore = 9.1; note = "豆瓣 8.6" }
        assertEquals(9.1, DoubanRatings.sourceScore(video, false))
        video.doubanScore = Double.NaN
        video.note = "豆瓣 11.0"
        assertNull(DoubanRatings.sourceScore(video, false))
    }
}
