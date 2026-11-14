package com.github.avbox.osc.bean

interface TmdbPosterCache {
    fun tmdbCachedRating(name: String?, year: Int, altName: String?): Double? = null

    fun tmdbCachedPoster(name: String?, year: Int, altName: String?): String?
}
