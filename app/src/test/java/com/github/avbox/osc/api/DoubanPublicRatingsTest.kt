package com.github.avbox.osc.api

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DoubanPublicRatingsTest {
    private val query = DoubanRatingQuery("测试电影", 2020, "any-site", "opaque", null, null, null, false)
    private fun provider(year: Int = 2020, rating: String = "8.2", detailTitle: String = "测试电影") = DoubanPublicRatings { url, _ ->
        if (url.contains("subject_suggest")) """[{"id":"12345","type":"movie","title":"测试电影","year":"$year"}]"""
        else """{"id":"12345","title":"$detailTitle","year":"$year","rating":{"value":$rating}}"""
    }

    @Test fun genericSiteGetsIndependentRealRating() = runBlocking {
        assertEquals(8.2, provider().lookup(query))
    }
    @Test fun wrongYearIsRejected() = runBlocking {
        assertNull(provider(year = 1999).lookup(query))
    }
    @Test fun differentDetailTitleIsRejected() = runBlocking {
        assertNull(provider(detailTitle = "其他电影").lookup(query))
    }
    @Test fun unratedFilmRemainsHidden() = runBlocking {
        assertNull(provider(rating = "null").lookup(query))
        assertNull(provider(rating = "0").lookup(query))
    }
    @Test fun independentLookupWorksWithoutConfiguredMetadataSite() = runBlocking {
        val resolver = DoubanRatingResolver(
            metadataSources = { emptyList() }, search = { _, _ -> emptyList() },
            detail = { _, _ -> null }, fetchScore = { null }, publicScore = provider()::lookup,
        )
        assertEquals(8.2, resolver.resolve(query))
    }
}
