package com.github.avbox.osc.ui.page

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.avbox.osc.bean.Movie
import com.github.avbox.osc.ui.components.HeroSpotlight
import com.github.avbox.osc.ui.components.ProvideTmdbPosterLookup
import com.github.avbox.osc.ui.components.VodCard
import com.github.avbox.osc.ui.components.VodCardStyle

/**
 * Independent landscape TV home. Does not call the phone HomePage or its
 * AppTopBarScaffold / bottom tab navigation.
 */
@Composable
fun TvMainScreen(vm: HomeViewModel) {
    val context = LocalContext.current
    val rec by vm.rec.collectAsStateWithLifecycle()
    val partitions by vm.partitions.collectAsStateWithLifecycle()
    val sorts by vm.sorts.collectAsStateWithLifecycle()
    val loading by vm.pageLoading.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF080A10)),
    ) {
        TvTopNavigation()
        if (loading && rec.videos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("正在加载影视内容…", color = Color.White)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (rec.videos.isNotEmpty()) {
                    item(key = "tv-hero") {
                        ProvideTmdbPosterLookup(vm) {
                            HeroSpotlight(
                                videos = rec.videos.take(HomeViewModel.HERO_VIDEO_COUNT),
                                backdropColor = Color(0xFF080A10),
                                onBackdropSeed = {},
                                onPosterPic = {},
                                onCardClick = { video -> handleCardClick(vm, video, context) },
                                modifier = Modifier.fillMaxWidth(),
                                fadeToBackdrop = false,
                            )
                        }
                    }
                }
                item(key = "tv-categories") {
                    TvCategoryRail(
                        labels = listOf("更新推荐", "热门电影", "热播剧集", "热播综艺", "电影筛选", "电视筛选", "排行榜") +
                            sorts.mapNotNull { it.name?.takeIf(String::isNotBlank) }.distinct(),
                    )
                }
                items(partitions.filter { it.videos.isNotEmpty() }, key = { "tv-sort-${it.sort.id}" }) { partition ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = partition.sort.name.orEmpty(),
                            color = Color.White,
                            fontSize = 22.sp,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(start = 42.dp),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 42.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(partition.videos, key = { it.id }) { video ->
                                VodCard(
                                    video = video,
                                    onClick = { handleCardClick(vm, video, context) },
                                    onLongClick = {},
                                    modifier = Modifier.width(150.dp).tvFocusSurface(),
                                    style = VodCardStyle.Overlay,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvTopNavigation() {
    val labels = listOf("首页", "直播", "搜索", "历史", "收藏", "推送", "设置")
    Row(
        modifier = Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 42.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Text(
            text = "CatTV",
            color = Color.White,
            fontSize = 27.sp,
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.width(20.dp))
        labels.forEachIndexed { index, label ->
            Text(
                text = label,
                color = if (index == 0) Color.White else Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .tvFocusSurface()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun TvCategoryRail(labels: List<String>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 42.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(labels.distinct(), key = { it }) { label ->
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .tvFocusSurface()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }
    }
}

private fun Modifier.tvFocusSurface(): Modifier = this
    .onFocusChanged { }
    .focusable()
