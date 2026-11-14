package com.github.avbox.osc.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalDensity
import coil3.imageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.compose.rememberConstraintsSizeResolver
import coil3.compose.rememberAsyncImagePainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithContent
import com.github.avbox.osc.R
import com.github.avbox.osc.bean.TmdbEpisode
import com.github.avbox.osc.bean.VodInfo
import com.github.avbox.osc.data.EpisodeMatcher
import com.github.avbox.osc.ui.components.AVBoxBottomSheet
import com.github.avbox.osc.ui.components.LocalSheetDismiss
import com.github.avbox.osc.ui.theme.filterChipColors
import com.github.avbox.osc.util.TmdbImages
import kotlinx.coroutines.launch

private val EpisodeThumbWidth = 210.dp

private const val EpisodeSheetHeightFraction = 0.94f

@Composable
internal fun EpisodeRow(
    vm: DetailViewModel,
    info: VodInfo,
    episodes: List<VodInfo.VodSeries>,
    playIndex: Int,
    currentFlag: String?,
    episodeNumbers: List<Int>,
    episodeMeta: List<TmdbEpisode>?,
    cover: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitleIcon(painterResource(R.drawable.ic_detail_episodes))
            Text(
                text = stringResource(R.string.detail_episodes),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            PillAction(
                iconRes = if (info.reverseSort) {
                    R.drawable.ic_episode_order_asc
                } else {
                    R.drawable.ic_episode_reverse
                },
                text = stringResource(if (info.reverseSort) R.string.detail_order_asc else R.string.detail_order_desc),
                onClick = { vm.toggleReverse() },
            )
            Spacer(Modifier.width(2.dp))
            PillAction(
                iconRes = R.drawable.ic_episode_grid_all,
                text = stringResource(R.string.common_all),
                onClick = { vm.showEpisodeSheet() },
            )
        }
        val listState = rememberLazyListState()
        var prevReverseSort by remember { mutableStateOf(info.reverseSort) }
        val thumbnails = !cover.isNullOrBlank() || !episodeMeta.isNullOrEmpty()
        LaunchedEffect(playIndex, currentFlag, episodes.size, info.reverseSort) {
            if (episodes.isEmpty()) return@LaunchedEffect
            val reverseChanged = info.reverseSort != prevReverseSort
            prevReverseSort = info.reverseSort
            if (reverseChanged) {
                listState.scrollToItem(0)
            } else if (playIndex >= 0) {
                listState.scrollToItem(minOf(playIndex, episodes.size - 1))
            }
        }
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(episodes) { index, ep ->
                if (thumbnails) {
                    EpisodeCard(
                        name = ep.name ?: (index + 1).toString(),
                        episodeNumber = episodeNumbers.getOrNull(index)?.takeIf { it > 0 } ?: (index + 1),
                        episode = episodeMeta?.getOrNull(index),
                        cover = cover,
                        selected = index == playIndex,
                        onClick = { vm.onEpisodeClick(index) },
                    )
                } else {
                    EpisodeNameCard(
                        name = ep.name ?: (index + 1).toString(),
                        selected = index == playIndex,
                        onClick = { vm.onEpisodeClick(index) },
                        modifier = Modifier.fillParentMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
internal fun rememberEpisodeMeta(
    vm: DetailViewModel,
    name: String?,
    altName: String?,
    year: Int,
    episodeCount: Int,
    seasonHint: Int?,
    episodeNumbers: List<Int>,
    reversed: Boolean,
): List<TmdbEpisode>? {
    val enabled = !name.isNullOrBlank() && episodeCount > 0
    val sourceOrder = remember(episodeNumbers, reversed) {
        if (reversed) episodeNumbers.reversed() else episodeNumbers
    }
    val epoch by TmdbImages.configEpoch.collectAsState()
    LaunchedEffect(vm, name, altName, year, episodeCount, seasonHint, sourceOrder, epoch) {
        vm.ensureTmdbEpisodeMeta(name, altName, year, episodeCount, seasonHint, sourceOrder)
    }
    val meta by vm.tmdbEpisodeMeta.collectAsState()
    if (!enabled) return null
    return if (reversed) meta?.asReversed() else meta
}

@Composable
private fun episodeThumbnailRequest(url: String): ImageRequest {
    val context = LocalContext.current
    val width = with(LocalDensity.current) { EpisodeThumbWidth.roundToPx() }
    val height = (width * 9 / 16).coerceAtLeast(1)
    return remember(context, url, width, height) {
        ImageRequest.Builder(context).data(url).size(width, height)
            .scale(coil3.size.Scale.FILL).build()
    }
}

@Composable
internal fun PrefetchEpisodeThumbnails(episodes: List<TmdbEpisode>?, playIndex: Int) {
    val context = LocalContext.current
    val start = playIndex.coerceIn(0, (episodes.orEmpty().size - 1).coerceAtLeast(0))
    val requests = episodes.orEmpty().drop(start).take(3).mapNotNull { episode ->
        episode.stillPath.takeIf { it.isNotBlank() }?.let { episodeThumbnailRequest(TmdbImages.stillUrl(it)) }
    }
    DisposableEffect(requests) {
        val jobs = requests.map { context.imageLoader.enqueue(it) }
        onDispose { jobs.forEach { it.dispose() } }
    }
}

@Composable
private fun EpisodeCard(
    name: String,
    episodeNumber: Int,
    episode: TmdbEpisode?,
    cover: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val stillPath = episode?.stillPath.orEmpty()
    val title = episode?.name?.takeIf { it.isNotBlank() } ?: name
    Column(
        modifier = Modifier
            .width(EpisodeThumbWidth)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(detailCardColor())
                .then(
                    if (selected) {
                        Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                    } else {
                        Modifier
                    },
                ),
        ) {
            if (!cover.isNullOrBlank()) {
                AsyncImage(
                    model = episodeThumbnailRequest(cover),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (stillPath.isNotEmpty()) {
                AsyncImage(
                    model = episodeThumbnailRequest(TmdbImages.stillUrl(stillPath)),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f)),
                ),
            )
            Text(
                text = stringResource(R.string.detail_episode_cover_number, episodeNumber),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )
    }
}

@Composable
private fun EpisodeNameCard(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .detailGlass(shape)
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = Int.MAX_VALUE,
            softWrap = true,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PillAction(iconRes: Int, text: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun EpisodeSheet(vm: DetailViewModel, slideFromEnd: Boolean, showQuality: Boolean, poster: String? = null) {
    val vodUi by vm.vodUi.collectAsState()
    val show by vm.episodeSheet.collectAsState()
    if (!show) return
    val info = vodUi?.info ?: return
    val currentFlag = info.playFlag
    val episodes = info.seriesMap?.get(currentFlag).orEmpty()
    val playIndex = info.playIndex
    val qualityOptions by vm.qualityOptions.collectAsState()
    val qualitySelected by vm.qualitySelected.collectAsState()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var jump by rememberSaveable(currentFlag) { mutableStateOf("") }

    LaunchedEffect(currentFlag, playIndex, info.reverseSort, episodes.size) {
        if (playIndex in episodes.indices) listState.scrollToItem(playIndex)
    }

    val posterUrl = poster?.takeIf { it.isNotBlank() } ?: info.pic?.takeIf { it.isNotBlank() }
    val panelSize = if (slideFromEnd) Modifier else Modifier.fillMaxHeight(EpisodeSheetHeightFraction)
    val panelModifier = if (posterUrl == null) panelSize else {
        val sizeResolver = rememberConstraintsSizeResolver()
        val context = LocalContext.current
        val request = remember(context, posterUrl, sizeResolver) {
            ImageRequest.Builder(context).data(posterUrl).size(sizeResolver).build()
        }
        val artwork = rememberAsyncImagePainter(request, contentScale = ContentScale.Crop)
        val surface = Color(0xFF20242D)
        panelSize.then(sizeResolver).drawWithContent {
            drawRect(Brush.horizontalGradient(listOf(Color(0xFF463441), Color(0xFF202E37))))
            val imageSize = artwork.intrinsicSize
            if (imageSize.width.isFinite() && imageSize.height.isFinite() &&
                imageSize.width > 0 && imageSize.height > 0
            ) {
                val scale = ContentScale.Crop.computeScaleFactor(imageSize, size)
                val cropSize = Size(imageSize.width * scale.scaleX, imageSize.height * scale.scaleY)
                clipRect {
                    translate(left = (size.width - cropSize.width) / 2f) {
                        with(artwork) { draw(cropSize) }
                    }
                }
                drawRect(Brush.verticalGradient(listOf(surface.copy(alpha = 0.79f), surface.copy(alpha = 0.94f))))
            }
            drawContent()
        }
    }

    AVBoxBottomSheet(
        onDismissRequest = { vm.dismissEpisodeSheet() },
        isScrollable = false,
        slideFromEnd = slideFromEnd,
        modifier = panelModifier,
        containerColor = if (posterUrl == null) Color(0xFF252B34) else Color.Transparent,
    ) {
        val dismissAnimated = LocalSheetDismiss.current
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.detail_all_episodes_count, episodes.size),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 72.dp),
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = dismissAnimated, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Text(stringResource(R.string.common_done), color = Color.White)
                }
            }
            val flags = info.seriesFlags.orEmpty().mapNotNull { it.name?.takeIf(String::isNotBlank) }
            if (flags.size > 1) {
                EpisodeSheetOptions(flags, flags.indexOf(currentFlag)) { vm.onFlagClick(flags[it]) }
            }
            if (showQuality && qualityOptions.size > 1) {
                EpisodeSheetOptions(qualityOptions, qualitySelected, vm::switchQuality)
            }
            val jumpNumber = jump.toIntOrNull()
            val jumpIndex = jumpNumber?.let { number ->
                episodes.indexOfFirst { EpisodeMatcher.extractEpisodeNumber(it.name) == number }
                    .takeIf { it >= 0 } ?: (if (info.reverseSort) episodes.size - number else number - 1)
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = jump,
                    onValueChange = { jump = it.filter(Char::isDigit).take(6) },
                    placeholder = { Text(stringResource(R.string.detail_jump_episode), color = Color.White.copy(alpha = 0.65f)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    enabled = jumpIndex != null && jumpIndex in episodes.indices,
                    onClick = { jumpIndex?.let { scope.launch { listState.scrollToItem(it) } } },
                ) { Text("#", color = Color.White.copy(alpha = if (jumpIndex != null && jumpIndex in episodes.indices) 1f else 0.45f)) }
                TextButton(onClick = vm::toggleReverse) {
                    Text(stringResource(if (info.reverseSort) R.string.detail_episode_ascending else R.string.detail_episode_descending), color = Color.White)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(episodes) { index, episode ->
                    val number = EpisodeMatcher.extractEpisodeNumber(episode.name).takeIf { it > 0 }
                        ?: if (info.reverseSort) episodes.size - index else index + 1
                    val shape = RoundedCornerShape(18.dp)
                    val selected = index == playIndex
                    Row(
                        Modifier.fillMaxWidth().clip(shape)
                            .background(Color.White.copy(alpha = if (selected) 0.22f else 0.10f))
                            .border(1.dp, Color.White.copy(alpha = if (selected) 0.45f else 0.14f), shape)
                            .clickable { vm.onEpisodeClick(index); dismissAnimated() }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(number.toString(), color = Color.White.copy(alpha = 0.7f), modifier = Modifier.width(30.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                            episode.name?.takeIf { it.isNotBlank() } ?: number.toString(),
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            )
                            if (com.github.avbox.osc.ui.components.MediaBadges.display.cards) {
                                com.github.avbox.osc.ui.components.MediaBadgeRow(episode.name.orEmpty())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeSheetOptions(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(labels) { index, label ->
            FilterChip(
                selected = index == selected,
                onClick = { onSelect(index) },
                label = { Text(label) },
                shape = RoundedCornerShape(20.dp),
                colors = FilterChipDefaults.filterChipColors(
                    labelColor = Color.White,
                    selectedLabelColor = Color.White,
                    containerColor = Color.White.copy(alpha = 0.09f),
                    selectedContainerColor = Color.White.copy(alpha = 0.23f),
                ),
            )
        }
    }
}
