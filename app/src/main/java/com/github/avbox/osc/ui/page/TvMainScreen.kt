package com.github.avbox.osc.ui.page

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.avbox.osc.api.AppBootstrap
import com.github.avbox.osc.bean.Movie
import com.github.avbox.osc.ui.ConfigManageActivity
import com.github.avbox.osc.ui.LivePlayActivity
import com.github.avbox.osc.ui.PreferenceSettingsActivity
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
    val boot by AppBootstrap.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { AppBootstrap.start() }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF080A10)),
    ) {
        TvTopNavigation(
            onLive = { context.startActivity(Intent(context, LivePlayActivity::class.java)) },
            onSources = { ConfigManageActivity.start(context) },
            onSettings = { PreferenceSettingsActivity.start(context) },
            onRefresh = { vm.reload() },
        )
        if (loading && rec.videos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("正在加载影视内容…", color = Color.White)
            }
        } else if (boot !is AppBootstrap.Boot.Ready) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(if (boot is AppBootstrap.Boot.Error) "接口配置加载失败" else "正在初始化影视接口…", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text((boot as? AppBootstrap.Boot.Error)?.msg ?: "正在读取本机已保存的接口配置", color = Color.White.copy(alpha = 0.7f))
                    TvAction("重试") { AppBootstrap.retry() }
                    TvAction("接口管理") { ConfigManageActivity.start(context) }
                }
            }
        } else if (rec.videos.isEmpty() && partitions.none { it.videos.isNotEmpty() }) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(if (vm.sources.value.isEmpty()) "还没有配置影视接口" else "接口已读取，但首页暂时没有影片数据", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text(if (vm.sources.value.isEmpty()) "请先添加你自己的影视源，添加后会自动加载首页内容。" else "请检查默认接口是否可用，或重新加载分类和影片列表。", color = Color.White.copy(alpha = 0.72f))
                    TvAction("添加 / 管理接口") { ConfigManageActivity.start(context) }
                    TvAction("重新加载") { vm.reload() }
                }
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
                            items(partition.videos, key = { it.id.orEmpty() }) { video ->
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
private fun TvTopNavigation(onLive: () -> Unit, onSources: () -> Unit, onSettings: () -> Unit, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val labels = listOf("首页", "直播", "搜索", "历史", "收藏", "接口", "设置", "刷新")
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
                    .clickable {
                        when (label) {
                            "直播" -> onLive()
                            "接口" -> onSources()
                            "设置" -> onSettings()
                            "刷新" -> onRefresh()
                            "搜索" -> context.startActivity(Intent(context, com.github.avbox.osc.ui.SearchActivity::class.java))
                        }
                    }
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

@Composable
private fun TvAction(label: String, onClick: () -> Unit) {
    Text(label, color = Color.White, style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF30343D))
            .clickable(onClick = onClick).tvFocusSurface().padding(horizontal = 22.dp, vertical = 12.dp))
}

private fun Modifier.tvFocusSurface(): Modifier = this
    .onFocusChanged { }
    .focusable()
