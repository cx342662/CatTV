package com.github.avbox.osc.api

import com.github.avbox.osc.bean.Movie
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

internal data class DoubanRatingQuery(
    val title: String,
    val year: Int,
    val sourceKey: String?,
    val videoId: String?,
    val subjectId: String?,
    val sourceScore: Double?,
    val binding: String?,
    val sourceIsDouban: Boolean,
    val altTitle: String? = null,
)

internal class DoubanRatingResolver(
    private val metadataSources: () -> List<String>,
    private val search: suspend (String, String) -> List<Movie.Video>,
    private val detail: suspend (String, String) -> Movie.Video?,
    private val fetchScore: suspend (String) -> Double?,
    private val cachedScore: (DoubanRatingQuery) -> Double? = { null },
) {
    suspend fun resolve(query: DoubanRatingQuery): Double? {
        if (query.binding != null) {
            val subject = DoubanRatings.parseBinding(query.binding) ?: return null
            return fetchScore(subject).validRating()
        }
        query.sourceScore.validRating()?.let { return it }
        cachedScore(query).validRating()?.let { return it }
        val subject = DoubanRatings.parseBinding(query.subjectId.orEmpty())
            ?: DoubanRatings.parseBinding(query.videoId.orEmpty()).takeIf { query.sourceIsDouban }
        if (subject != null) fetchScore(subject).validRating()?.let { return it }
        if (query.title.isBlank()) return null
        for (source in metadataSources().distinct().take(3)) {
            val result = try {
                withTimeoutOrNull(6500) {
                    val candidate = selectCandidate(query, search(source, query.title)) ?: return@withTimeoutOrNull null
                    DoubanRatings.sourceScore(candidate, true)?.let { return@withTimeoutOrNull it }
                    val subjectId = DoubanRatings.parseBinding(candidate.doubanId.orEmpty())
                        ?: DoubanRatings.parseBinding(candidate.id.orEmpty())
                    if (subjectId != null) fetchScore(subjectId).validRating()?.let { return@withTimeoutOrNull it }
                    val candidateId = candidate.id ?: return@withTimeoutOrNull null
                    val full = detail(source, candidateId)
                    if (full != null) {
                        val verified = selectCandidate(query, listOf(full)) ?: return@withTimeoutOrNull null
                        DoubanRatings.sourceScore(verified, true)?.let { return@withTimeoutOrNull it }
                    }
                    val id = DoubanRatings.parseBinding(full?.doubanId ?: candidate.doubanId.orEmpty())
                        ?: DoubanRatings.parseBinding(full?.id ?: candidateId)
                    id?.let { fetchScore(it).validRating() }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                com.github.avbox.osc.util.AppLog.e("DoubanRatingResolver", error)
                null
            }
            result.validRating()?.let { return it }
        }
        return null
    }

    companion object {
        private val titleSeparators = Regex("[\\s\\p{P}\\p{Z}]+")

        fun sameTitle(left: String?, right: String?): Boolean {
            fun normalized(value: String?) = value.orEmpty().trim().lowercase(Locale.ROOT).replace(titleSeparators, "")
            val a = normalized(left)
            return a.isNotEmpty() && a == normalized(right)
        }

        fun selectCandidate(query: DoubanRatingQuery, candidates: List<Movie.Video>): Movie.Video? {
            val matches = candidates.filter { candidate ->
                listOf(candidate.name, candidate.en).any { name ->
                    sameTitle(name, query.title) || sameTitle(name, query.altTitle)
                }
            }
            val dated = if (query.year > 0) {
                matches.filter { it.year == query.year }.ifEmpty { matches.filter { it.year <= 0 } }
            } else matches
            return dated.distinctBy {
                it.doubanId?.takeIf(String::isNotBlank) ?: it.id?.takeIf(String::isNotBlank) ?: it
            }.singleOrNull()
        }
    }
}

internal fun Double?.validRating(): Double? = this?.takeIf { it > 0.0 && it <= 10.0 }
