package com.github.avbox.osc.api

import com.github.avbox.osc.bean.Movie
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class DetailDoubanRatingTest {
    private fun query() = DoubanRatingQuery("可能的爱", 2026, "playable", "opaque-id", null, null, null, false)
    private fun video(id: String, title: String = "可能的爱", year: Int = 2026, note: String? = null) =
        Movie.Video().apply { this.id = id; name = title; this.year = year; this.note = note }

    @Test fun coldDetailWithoutSubjectIdLooksUpConfiguredDoubanSource() = runBlocking {
        var searches = 0
        val resolver = DoubanRatingResolver(
            metadataSources = { listOf("douban-metadata") },
            search = { source, title ->
                assertEquals("douban-metadata", source)
                assertEquals("可能的爱", title)
                searches++
                listOf(video("db-id", note = "8.6"))
            }, detail = { _, _ -> fail("Search already has a score"); null },
            fetchScore = { fail("No subject HTML needed"); null },
        )
        assertEquals(8.6, resolver.resolve(query()))
        assertEquals(1, searches)
    }

    @Test fun metadataDetailSuppliesMissingListScore() = runBlocking {
        val resolver = DoubanRatingResolver(
            metadataSources = { listOf("douban") },
            search = { _, _ -> listOf(video("opaque-db-id")) },
            detail = { _, id -> assertEquals("opaque-db-id", id); video(id, note = "豆瓣评分：8.6") },
            fetchScore = { null },
        )
        assertEquals(8.6, resolver.resolve(query()))
    }

    @Test fun genericNumericPlaybackIdIsNotUsedAsDoubanSubjectId() = runBlocking {
        val resolver = DoubanRatingResolver(
            metadataSources = { emptyList() }, search = { _, _ -> emptyList() },
            detail = { _, _ -> null }, fetchScore = { fail("Playback IDs are not Douban IDs"); null },
        )
        assertNull(resolver.resolve(query().copy(videoId = "12345")))
    }

    @Test fun differentYearAndPartialTitleAreRejected() = runBlocking {
        val resolver = DoubanRatingResolver(
            metadataSources = { listOf("douban") },
            search = { _, _ -> listOf(video("old", year = 2000, note = "9.0"), video("other", title = "可能的爱情", note = "9.1")) },
            detail = { _, _ -> fail("Unmatched film must not be loaded"); null },
            fetchScore = { fail("Unmatched film must not be fetched"); null },
        )
        assertNull(resolver.resolve(query()))
    }

    @Test fun ambiguousSameTitleWithoutYearIsRejected() {
        assertNull(DoubanRatingResolver.selectCandidate(query().copy(year = 0),
            listOf(video("original", year = 2000), video("remake", year = 2026))))
        assertNull(DoubanRatingResolver.selectCandidate(query(),
            listOf(video("", note = "8.1"), video("", note = "8.9"))))
    }

    @Test fun manualBindingHasPriorityOverSourceAndCache() = runBlocking {
        val resolver = DoubanRatingResolver(
            metadataSources = { emptyList() }, search = { _, _ -> emptyList() }, detail = { _, _ -> null },
            fetchScore = { id -> assertEquals("1306612", id); 8.6 }, cachedScore = { 9.9 },
        )
        assertEquals(8.6, resolver.resolve(query().copy(sourceScore = 7.0,
            binding = "https://movie.douban.com/subject/1306612/")))
    }

    @Test fun navigationScoreWorksWithoutCacheOrMetadataSources() = runBlocking {
        val resolver = DoubanRatingResolver(
            metadataSources = { fail("Navigation score is sufficient"); emptyList() },
            search = { _, _ -> emptyList() }, detail = { _, _ -> null }, fetchScore = { null },
        )
        assertEquals(8.6, resolver.resolve(query().copy(sourceScore = 8.6)))
    }

    @Test fun switchingBetweenDetailLayoutsDoesNotCancelSharedLookup() = runBlocking {
        val result = CompletableDeferred<Double?>()
        var requests = 0
        val loader = DoubanRatingLoader(this) { requests++; result.await() }
        loader.load(query())
        yield()
        loader.load(query())
        result.complete(8.6)
        yield()
        assertEquals(1, requests)
        assertEquals(8.6, loader.rating.value)
        loader.load(query())
        assertEquals(8.6, loader.rating.value)
    }

    @Test fun lateResultFromPreviousFilmCannotOverwriteCurrentFilm() = runBlocking {
        val old = CompletableDeferred<Double?>()
        val loader = DoubanRatingLoader(this) { request ->
            if (request.videoId == "old") withContext(NonCancellable) { old.await() } else 8.6
        }
        loader.load(query().copy(videoId = "old"))
        yield()
        loader.load(query())
        yield()
        assertEquals(8.6, loader.rating.value)
        old.complete(4.0)
        yield()
        yield()
        assertEquals(8.6, loader.rating.value)
        loader.load(null)
        assertNull(loader.rating.value)
    }
}
