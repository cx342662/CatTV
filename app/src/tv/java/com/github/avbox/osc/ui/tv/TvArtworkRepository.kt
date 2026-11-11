package com.github.avbox.osc.ui.tv

import android.graphics.Bitmap
import coil3.SingletonImageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.github.avbox.osc.api.TmdbPoster
import com.github.avbox.osc.bean.Movie
import com.github.avbox.osc.util.AppContextHolder
import com.github.avbox.osc.util.AppLog
import com.github.avbox.osc.util.TmdbImages
import com.github.avbox.osc.util.VodImages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object TvArtworkRepository {
    data class Hero(val image: Bitmap?, val overview: String, val rating: Double, val landscape: Boolean = false, val logo: Bitmap? = null, val genres: List<String> = emptyList())

    data class PosterInfo(val provider: String, val width: Int, val height: Int)
    private val posterInfo = android.util.LruCache<Pair<String?, String?>, PosterInfo>(200)

    fun displayedPoster(video: Movie.Video): PosterInfo? = posterInfo.get(video.sourceKey to video.id)

    suspend fun poster(video: Movie.Video): Bitmap? = withContext(Dispatchers.IO) {
        val key = video.sourceKey to video.id
        posterInfo.remove(key)
        val original = posterPath(video)?.takeIf { it.isNotBlank() }?.let(TmdbImages::fullPosterUrl)
        val sourceUrl = VodImages.largePosterUrl(video.pic)
        val sourceProvider = if (sourceUrl?.contains(".doubanio.com/", ignoreCase = true) == true) "Douban" else "VOD"
        val candidates = buildList {
            if (original != null) {
                add("TMDB original" to TvArtworkUrls.sized(original, "original"))
                val path = original.substringAfterLast('/')
                if (!path.isBlank() && !original.startsWith("https://images.tmdb.org/")) {
                    add("TMDB original" to "https://images.tmdb.org/t/p/original/$path")
                }
                add("TMDB w780" to TvArtworkUrls.sized(original, "w780"))
            }
            add(sourceProvider to sourceUrl)
        }
        var best: Pair<String, Bitmap>? = null
        for ((provider, url) in candidates) {
            val decoded = bitmap(url, 960, 1440) ?: continue
            if (decoded.width > (best?.second?.width ?: 0)) best = provider to decoded
            if (decoded.width >= 700) break
        }
        if ((best?.second?.width ?: 0) < 700 && TmdbImages.isActive()) {
            val alternates = try {
                TmdbPoster.resolveImages(video.name, video.year, video.en).take(3)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppLog.e("TvArtwork", error)
                emptyList()
            }
            for (path in alternates) {
                val url = TvArtworkUrls.sized(TmdbImages.fullPosterUrl(path), "original")
                if (candidates.any { it.second == url }) continue
                val image = bitmap(url, 960, 1440) ?: continue
                if (image.width > (best?.second?.width ?: 0)) best = "TMDB original" to image
                if (image.width >= 700) break
            }
        }
        best?.let { (provider, image) ->
            posterInfo.put(key, PosterInfo(provider, image.width, image.height))
            image
        }
    }

    suspend fun backgroundPoster(video: Movie.Video): Bitmap? = withContext(Dispatchers.IO) {
        val path = posterPath(video)
        bitmap(path?.takeIf { it.isNotBlank() }?.let { TvArtworkUrls.sized(TmdbImages.fullPosterUrl(it), "original") }
            ?: VodImages.largePosterUrl(video.pic), 1920, 1080)
    }

    suspend fun hero(video: Movie.Video): Hero = withContext(Dispatchers.IO) {
        try {
            val path = TmdbPoster.resolveBackdrops(video.name, video.year, video.en).firstOrNull()
            val detail = TmdbPoster.resolveDetail(video.name, video.year, video.en)
            val original = path?.let(TmdbImages::fullPosterUrl)
            val background = original?.let {
                bitmap(TvArtworkUrls.sized(it, "w1920"), 1920, 1080)
                    ?: bitmap(TvArtworkUrls.sized(it, "original"), 1920, 1080)
            }
            Hero(background ?: backgroundPoster(video),
                detail?.overview?.takeIf { it.isNotBlank() } ?: video.des.orEmpty(), detail?.rating ?: 0.0,
                landscape = background != null,
                genres = detail?.genres.orEmpty(),
                logo = runCatching {
                    TmdbPoster.resolveLogo(video.name, video.year, video.en).takeIf { it.isNotBlank() }
                        ?.let { bitmap(TvArtworkUrls.sized(TmdbImages.logoUrl(it), "original"), 1000, 320) }
                }.getOrElse { if (it is CancellationException) throw it; null })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppLog.e("TvArtwork", error)
            Hero(backgroundPoster(video), video.des.orEmpty(), 0.0)
        }
    }

    private suspend fun posterPath(video: Movie.Video): String? = try {
        TmdbPoster.cachedPoster(video.name, video.year, video.en)
            ?: TmdbPoster.resolvePoster(video.name, video.year, video.en)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        AppLog.e("TvArtwork", error)
        null
    }

    private suspend fun bitmap(url: String?, width: Int, height: Int): Bitmap? {
        if (url.isNullOrBlank()) return null
        return try {
            val context = requireNotNull(AppContextHolder.context()) { "App context is not initialized" }
            val result = SingletonImageLoader.get(context).execute(
                ImageRequest.Builder(context).data(url).size(width, height).build(),
            )
            if (result is ErrorResult) AppLog.e("TvArtwork", result.throwable)
            (result as? SuccessResult)?.image?.toBitmap()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppLog.e("TvArtwork", error)
            null
        }
    }
}

/** Reference APK: w500 poster URLs and w1920 hero URLs, independent decode targets. */
internal object TvArtworkUrls {
    fun sized(url: String, size: String): String = url.replace(
        Regex("/t/p/(?:(?:w[0-9]+|original)/)+"), "/t/p/$size/",
    )
}
