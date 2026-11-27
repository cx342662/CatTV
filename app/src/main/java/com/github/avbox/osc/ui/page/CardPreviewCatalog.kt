package com.github.avbox.osc.ui.page

import com.github.avbox.osc.bean.MovieSort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object CardPreviewCatalog {
    private val mutableSources = MutableStateFlow<Map<String, List<MovieSort.SortData>>>(emptyMap())
    val sources = mutableSources.asStateFlow()

    fun record(source: String, categories: List<MovieSort.SortData>) {
        mutableSources.value = mutableSources.value + (source to categories.toList())
    }
}
