package com.github.avbox.osc.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class DoubanRatingLoader(
    private val scope: CoroutineScope,
    private val resolve: suspend (DoubanRatingQuery) -> Double?,
) {
    private val current = MutableStateFlow<Double?>(null)
    val rating = current.asStateFlow()
    private var request: DoubanRatingQuery? = null
    private var job: Job? = null

    fun load(query: DoubanRatingQuery?) {
        if (query == request) return
        request = query
        job?.cancel()
        current.value = query?.sourceScore.validRating().takeIf { query?.binding == null }
        if (query == null) return
        job = scope.launch {
            val result = resolve(query).validRating()
            if (request == query) current.value = result
        }
    }
}
