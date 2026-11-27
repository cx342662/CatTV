package com.github.avbox.osc.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.github.avbox.osc.R
import com.github.avbox.osc.bean.Movie

internal val LocalCardPreviewMode = staticCompositionLocalOf { false }
private val SamplePortraits = intArrayOf(R.drawable.card_sample_p1, R.drawable.card_sample_p2, R.drawable.card_sample_p3, R.drawable.card_sample_p4, R.drawable.card_sample_p5, R.drawable.card_sample_p6, R.drawable.card_sample_p7, R.drawable.card_sample_p8)
private val SampleBackdrops = intArrayOf(R.drawable.card_sample_b1, R.drawable.card_sample_b2, R.drawable.card_sample_b3, R.drawable.card_sample_b4, R.drawable.card_sample_b5, R.drawable.card_sample_b6, R.drawable.card_sample_b7, R.drawable.card_sample_b8)

internal fun localCardPreviewVideos(titles: List<String>, description: String): List<Movie.Video> = titles.mapIndexed { index, title ->
    Movie.Video().also {
        it.id = "preview-$index"
        it.sourceKey = "local-card-preview"
        it.name = title
        it.year = if (index == 0) 2021 else 2026
        it.des = description
    }
}

@Composable
internal fun LocalCardPreviewArtwork(name: String?, modifier: Modifier = Modifier) {
    val index = when (name) {
        "时光代理人", "Link Click" -> 0
        "火柴盒大电影", "Matchbox" -> 1
        "人性本兽", "Beast" -> 2
        "魔女嘉莉", "Carrie" -> 3
        "蜘蛛侠：崭新之日", "Spider-Man" -> 4
        "暗潮之下", "Under the Tide" -> 5
        "Doctor X", "剧场版 Doctor-X" -> 6
        else -> 7
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val image = if (maxWidth > maxHeight) SampleBackdrops[index] else SamplePortraits[index]
        Image(painterResource(image), contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}
