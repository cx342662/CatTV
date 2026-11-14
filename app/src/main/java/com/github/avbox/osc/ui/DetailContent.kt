package com.github.avbox.osc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.avbox.osc.R
import com.github.avbox.osc.api.ApiConfig
import com.github.avbox.osc.data.FollowDays
import com.github.avbox.osc.ui.components.FollowReminderSheet

internal val HeroScrollFadeDistance = 300.dp

internal fun heroScrollProgress(firstVisibleIndex: Int, firstVisibleOffset: Int, referencePx: Int): Float = when {
    firstVisibleIndex > 0 -> 1f
    firstVisibleIndex < 0 -> 0f
    referencePx <= 0 -> 0f
    else -> (firstVisibleOffset.toFloat() / referencePx).coerceIn(0f, 1f)
}

@Composable
internal fun DetailContent(
    activity: DetailActivity,
    vm: DetailViewModel,
    listState: LazyListState,
    inlinePlayback: Boolean,
    onPosterPic: (String) -> Unit,
    onSeed: (Int?) -> Unit,
) {
    val vodUi by vm.vodUi.collectAsState()
    val info = vodUi?.info ?: return

    val flags = info.seriesFlags.orEmpty()
    val currentFlag = info.playFlag
    val episodes = info.seriesMap?.get(currentFlag).orEmpty()
    val playIndex = info.playIndex
    var episodeCover by remember(info.id, info.sourceKey, info.pic) { mutableStateOf(info.pic) }
    val episodeNumbers = remember(episodes) {
        episodes.map { com.github.avbox.osc.data.EpisodeMatcher.extractEpisodeNumber(it.name) }
    }
    val episodeMeta = rememberEpisodeMeta(
        vm, info.name, info.en, info.year, episodes.size,
        vm.seasonHintFor(info.name, currentFlag), episodeNumbers, info.reverseSort,
    )
    PrefetchEpisodeThumbnails(episodeMeta, playIndex)
    val collected by vm.collected.collectAsState()
    val followRecord by vm.follow.collectAsState()
    val description = remember(info.des) { removeHtmlTag(info.des) }
    var followScheduleOpen by rememberSaveable { mutableStateOf(false) }

    val currentSource = ApiConfig.get().getSource(vm.firstsourceKey)
    val displaySourceName = currentSource?.name ?: vm.firstsourceKey
    val heroRollPaused by vm.fullScreen.collectAsState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item(key = "hero") {
            if (inlinePlayback) {
                val detail = rememberTmdbDetail(vm, info.name, info.en, info.year)
                val ratings by vm.mdbListRatings.collectAsState()
                val douban by vm.doubanRating.collectAsState()
                val lookupId = vm.doubanBinding(info.sourceKey ?: vm.firstsourceKey, info.id, info.name.orEmpty(), info.year)
                    ?: info.doubanId
                androidx.compose.runtime.LaunchedEffect(vm, lookupId) { vm.ensureDoubanRating(lookupId) }
                val inlineRatings = ratings + listOfNotNull(
                    detail?.rating?.takeIf { it > 0 && it <= 10 }?.let { "tmdb" to it },
                    (douban?.takeIf { it.first == lookupId }?.second ?: info.doubanScore)?.let { "douban" to it },
                )
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AsyncImage(
                        model = info.pic, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.width(104.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(info.name.orEmpty(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        val date = detail?.releaseDate?.takeIf { it.isNotBlank() } ?: info.year.takeIf { it > 0 }?.toString()
                        val meta = listOfNotNull(date, info.type?.takeIf { it.isNotBlank() }).joinToString(" · ")
                        if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelMedium)
                        DetailRatingBadges(inlineRatings, MaterialTheme.colorScheme.onSurface)
                        if (description.isNotBlank()) Text(description, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = vm::toggleCollect) { Text(stringResource(if (collected) R.string.detail_inline_unfavorite else R.string.detail_inline_favorite)) }
                    TextButton(onClick = activity::expandInlinePlayer) { Text(stringResource(R.string.detail_inline_fullscreen)) }
                }
            } else DetailHero(
                title = info.name ?: "TVBox",
                altTitle = info.en,
                picture = info.pic,
                year = info.year,
                area = info.area,
                type = info.type,
                note = info.note,
                doubanScore = info.doubanScore,
                doubanId = info.doubanId,
                bindingSource = info.sourceKey ?: vm.firstsourceKey,
                bindingVideoId = info.id,
                description = description,
                collected = collected,
                followed = followRecord != null,
                onPosterPic = { url ->
                    episodeCover = url.takeIf { it.isNotBlank() } ?: info.pic
                    onPosterPic(url)
                },
                onSeed = onSeed,
                onBack = { activity.onBackPressedDispatcher.onBackPressed() },
                onPlay = { vm.onPlayRequested(DetailPlaybackEntry.PlayCapsule) },
                onMusic = { activity.openMusicPlayer() },
                onCast = { activity.openCast() },
                onCollect = { vm.toggleCollect() },
                onFollow = { followScheduleOpen = true },
                vm = vm,
                rollPaused = heroRollPaused,
            )
        }

        if (episodes.isNotEmpty()) {
            item(key = "episodes") {
                EpisodeRow(vm, info, episodes, playIndex, currentFlag, episodeNumbers, episodeMeta, episodeCover)
            }
        }

        if (flags.isNotEmpty()) {
            item(key = "flags") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                    ) {
                        SectionTitleIcon(painterResource(R.drawable.ic_detail_line))
                        Text(
                            text = stringResource(R.string.detail_line),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = stringResource(R.string.detail_source, displaySourceName),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .widthIn(max = 180.dp),
                        )
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(flags, key = { i, f -> "${i}_${f.name}" }) { _, flag ->
                            LineCard(
                                name = flag.name ?: "",
                                count = info.seriesMap?.get(flag.name)?.size ?: 0,
                                selected = flag.name == currentFlag,
                                onClick = { vm.onFlagClick(flag.name ?: "") },
                            )
                        }
                    }
                }
            }
        }

        item(key = "sources") {
            SourceSection(vm, currentSourceName = displaySourceName)
        }

        item(key = "tmdb_info") {
            TmdbCastSection(vm, info.name, info.en, info.year)
        }

        item(key = "tmdb_genres") {
            TmdbGenreSection(vm, info.name, info.en, info.year)
        }
    }

    if (followScheduleOpen) {
        FollowReminderSheet(
            initialDays = followRecord?.let { FollowDays.decode(it.updateDays) }?.takeIf { it.isNotEmpty() }
                ?: setOf(0),
            initialHour = followRecord?.updateHour ?: 21,
            onDismissRequest = { followScheduleOpen = false },
            onSave = { days, hour -> vm.saveFollow(days, hour) },
        )
    }
}

@Composable
private fun LineCard(
    name: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DetailItemCard(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.width(DetailItemWidth),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.detail_line_video_count, count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
