package com.github.avbox.osc.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.github.avbox.osc.bean.Movie

internal val LocalCardPreviewMode = staticCompositionLocalOf { false }

internal fun localCardPreviewVideos(titles: List<String>, description: String): List<Movie.Video> = titles.mapIndexed { index, title ->
    Movie.Video().also {
        it.id = "preview-$index"
        it.sourceKey = "local-card-preview"
        it.name = title
        it.year = 2020 + index
        it.des = description
    }
}

@Composable
internal fun LocalCardPreviewArtwork(name: String?, modifier: Modifier = Modifier) {
    val colors = listOf(Color(0xff244a4c), Color(0xff594b69), Color(0xff4c5d73), Color(0xff785445))
    val seed = name.orEmpty().hashCode() and Int.MAX_VALUE
    Box(modifier.fillMaxSize().background(colors[seed % colors.size])) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0xffe9d8b5), size.minDimension * .16f, Offset(size.width * .7f, size.height * .24f))
            val landscape = Path().apply {
                moveTo(0f, size.height * .78f)
                lineTo(size.width * .32f, size.height * .36f)
                lineTo(size.width * .61f, size.height * .69f)
                lineTo(size.width * .83f, size.height * .48f)
                lineTo(size.width, size.height * .72f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(landscape, Color.White.copy(alpha = .18f))
        }
        Text("CatTV", Modifier.align(Alignment.BottomStart).padding(12.dp), color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}
