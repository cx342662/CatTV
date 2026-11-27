package com.github.avbox.osc.ui.page

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.avbox.osc.api.ApiConfig
import com.github.avbox.osc.util.HomeCardSettings
import com.github.avbox.osc.util.homeCardLayout
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal class CardPreviewViewModel : ViewModel() {
    private val sourceKey = ApiConfig.get().homeSourceBean().key.orEmpty()
    val config = HomeCardSettings.config
    val categories = CardPreviewCatalog.sources.map { it[sourceKey].orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selected(section: String, serialized: String): Int = homeCardLayout(serialized, sourceKey, section)

    fun apply(section: String, layout: Int) {
        if (sourceKey.isNotEmpty()) HomeCardSettings.apply(sourceKey, section, layout)
    }

    fun canApply(): Boolean = sourceKey.isNotEmpty()
}
