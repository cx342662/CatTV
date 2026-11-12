package com.github.avbox.osc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.avbox.osc.R
import java.util.Locale
import kotlin.math.roundToInt

private enum class RatingBadge(
    val source: String,
    val label: String,
    val colors: List<Color> = emptyList(),
    val labelColor: Color = Color.Black,
) {
    IMDb("imdb", "IMDb", listOf(Color(0xFFFFDE55), Color(0xFFF5C518))),
    TMDB("tmdb", "TMDB", listOf(Color(0xFF90CEA1), Color(0xFF01B4E4))),
    Trakt("trakt", "✓", listOf(Color(0xFFED315B), Color(0xFF983CB7)), Color.White),
    Tomatoes("tomatoes", "🍅"),
    Audience("popcorn", "🍿"),
    Metacritic("metacritic", "MC", listOf(Color(0xFF80D537), Color(0xFF4CB320)), Color.White),
    Douban("douban", "", listOf(Color(0xFF289B61), Color(0xFF16834C)), Color.White);

    fun formatted(value: Double): String? = when (this) {
        IMDb, TMDB, Douban -> value.takeIf { it > 0 && it <= 10 }?.let { String.format(Locale.US, "%.1f", it) }
        Trakt -> String.format(Locale.US, "%.1f", value / 10)
        Tomatoes, Audience -> "${value.roundToInt()}%"
        Metacritic -> value.roundToInt().toString()
    }
}

@Composable
internal fun DetailRatingBadges(ratings: Map<String, Double>, numberColor: Color) {
    val badges = RatingBadge.entries.mapNotNull { badge ->
        ratings[badge.source]?.let(badge::formatted)?.let { badge to it }
    }
    if (badges.isEmpty()) return
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        maxItemsInEachRow = 5,
    ) {
        for ((badge, number) in badges) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val labelModifier = if (badge.colors.isEmpty()) Modifier else Modifier
                    .background(
                        Brush.horizontalGradient(badge.colors),
                        if (badge == RatingBadge.Trakt) CircleShape else RoundedCornerShape(4.dp),
                    )
                    .padding(horizontal = 4.dp, vertical = 1.dp)
                Text(
                    text = if (badge == RatingBadge.Douban) stringResource(R.string.detail_douban_rating) else badge.label,
                    color = badge.labelColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = if (badge.colors.isEmpty()) 13.sp else 10.sp,
                    lineHeight = 13.sp,
                    letterSpacing = 0.sp,
                    modifier = labelModifier,
                )
                Text(
                    text = number,
                    color = numberColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}
