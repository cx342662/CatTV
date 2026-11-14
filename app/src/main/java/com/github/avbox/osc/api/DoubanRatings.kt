package com.github.avbox.osc.api

import com.github.avbox.osc.util.AppLog
import com.github.avbox.osc.util.net.Http
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

internal object DoubanRatings {
    private val subjectId = Regex("[1-9][0-9]{0,18}")

    private val labeledScore = Regex("豆瓣(?:评分|分数)?\\s*[:：]?\\s*(\\d+(?:\\.\\d+)?)")
    private val sourceScoreNote = Regex("^(?:评分\\s*[:：]?\\s*)?(\\d+(?:\\.\\d+)?)\\s*分?$")

    fun sourceScore(video: com.github.avbox.osc.bean.Movie.Video, isDouban: Boolean): Double? {
        val note = video.note.orEmpty().trim()
        val score = labeledScore.find(note) ?: sourceScoreNote.matchEntire(note).takeIf { isDouban }
        return video.doubanScore?.takeIf { it > 0 && it <= 10 }
            ?: score?.groupValues?.get(1)?.toDoubleOrNull()?.takeIf { it > 0 && it <= 10 }
    }

    fun bindingKey(title: String, year: Int): String =
        "douban_title_binding_" + com.github.avbox.osc.util.MD5.string2MD5(title.trim() + "|" + year)

    fun parseBinding(input: String): String? {
        val value = input.trim()
        if (subjectId.matches(value)) return value
        val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return null
        if (uri.scheme !in listOf("https", "http") || uri.host != "movie.douban.com") return null
        return Regex("^/subject/([1-9][0-9]{0,18})/?$").matchEntire(uri.path.orEmpty())
            ?.groupValues?.get(1)
    }

    private val ratings by lazy { android.util.LruCache<String, Double>(128) }

    suspend fun score(id: String): Double? {
        if (!subjectId.matches(id)) return null
        ratings.get(id)?.let { return it }
        return try {
            val html = Http.get("https://movie.douban.com/subject/$id/")
            withContext(Dispatchers.Default) {
                Jsoup.parse(html).selectFirst("[property=v:average]")
                    ?.text()?.toDoubleOrNull()?.takeIf { it > 0.0 && it <= 10.0 }
            }?.also { ratings.put(id, it) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLog.e("DoubanRatings", error)
            null
        }
    }
}
