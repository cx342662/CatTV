package com.github.avbox.osc.ui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Dedicated entry surface for the ARM32 Android TV build.
 * Phone builds continue to use MainContent's tab scaffold.
 * HomePage reuses the existing source, TMDB lookup, cache and fallback pipeline.
 */
@Composable
fun TvMainScreen(vm: HomeViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        HomePage(vm = vm)
    }
}
