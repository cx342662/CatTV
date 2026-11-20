package com.github.avbox.osc.api

import com.github.avbox.osc.bean.Movie
import com.github.avbox.osc.util.net.Http
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

internal class DoubanPublicRatings(
    private val request: suspend (String, Map<String, String>) -> String = { url, parameters ->
        Http.once(url) {
            headers("Referer", "https://m.douban.com/")
            headers("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
            params(parameters)
        }
    },
) {
    suspend fun lookup(query: DoubanRatingQuery): Double? = safely {
        val text = request("https://movie.douban.com/j/subject_suggest", mapOf("q" to query.title))
        val candidates = JsonParser.parseString(text).asJsonArray.mapNotNull { element ->
            val item = element.asJsonObject
            val kind = item.get("type")?.asString
            if (kind !in listOf("movie", "tv")) return@mapNotNull null
            Movie.Video().apply {
                id = DoubanRatings.parseBinding(item.get("id")?.asString.orEmpty())
                name = item.get("title")?.asString
                en = item.get("sub_title")?.asString
                year = item.get("year")?.asString?.toIntOrNull() ?: 0
                type = kind
            }.takeIf { it.id != null }
        }
        val candidate = DoubanRatingResolver.selectCandidate(query, candidates) ?: return@safely null
        val json = JsonParser.parseString(request("https://m.douban.com/rexxar/api/v2/${candidate.type}/${candidate.id}", emptyMap())).asJsonObject
        val verified = Movie.Video().apply {
            id = json.get("id")?.asString
            name = json.get("title")?.asString
            year = json.get("year")?.asString?.toIntOrNull() ?: 0
        }
        if (verified.id != candidate.id || DoubanRatingResolver.selectCandidate(query, listOf(verified)) == null) return@safely null
        json.getAsJsonObject("rating")?.get("value")?.takeUnless { it.isJsonNull }?.asDouble.validRating()
    }

    suspend fun score(id: String): Double? = safely {
        val json = JsonParser.parseString(request("https://m.douban.com/rexxar/api/v2/movie/$id", emptyMap())).asJsonObject
        if (json.get("id")?.asString != id) return@safely null
        json.getAsJsonObject("rating")?.get("value")?.takeUnless { it.isJsonNull }?.asDouble.validRating()
    }

    private suspend fun safely(block: suspend () -> Double?): Double? = try {
        withTimeoutOrNull(8000) { block() }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        com.github.avbox.osc.util.AppLog.e("DoubanPublicRatings", error)
        null
    }
}
