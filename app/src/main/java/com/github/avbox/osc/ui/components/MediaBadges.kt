package com.github.avbox.osc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
import com.github.avbox.osc.util.KV
import com.github.avbox.osc.util.KVKeys
import com.github.avbox.osc.util.net.Http
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.google.re2j.Pattern
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class MediaBadgeRule(
    val label: String,
    val pattern: Pattern,
    val image: String? = null,
    val group: String = label,
)

internal object MediaBadges {
    data class Display(val enabled: Boolean, val player: Boolean, val followControls: Boolean, val cards: Boolean)

    var display by mutableStateOf(readDisplay())
        private set
    private data class Library(val urls: List<String>, val selected: String)

    private const val libraryKey = KVKeys.MEDIA_BADGES + "_library"
    private const val legacyUrlKey = KVKeys.MEDIA_BADGES + "_url"
    private const val legacyPackKey = KVKeys.MEDIA_BADGES + "_pack"

    private var library by mutableStateOf(readLibrary())
    val packUrls: List<String> get() = library.urls
    val packUrl: String get() = library.selected

    var revision by mutableStateOf(0)
        private set
    private var imported = if (packUrl.isEmpty()) emptyList() else runCatching {
        parsePack(readPack(packUrl))
    }.getOrDefault(emptyList())

    private fun packKey(url: String) = KVKeys.MEDIA_BADGES + "_saved:" + url

    private fun readPack(url: String): String = KV.get<String>(packKey(url))
        ?: KV.get(legacyPackKey, "").takeIf { url == KV.get(legacyUrlKey, "") }
        ?: ""

    private fun readLibrary(): Library {
        val saved = KV.get<String>(libraryKey)
        if (saved != null) {
            val json = JSONObject(saved)
            val urls = json.getJSONArray("urls")
            val entries = List(urls.length()) { urls.getString(it) }
            return Library(entries, json.optString("selected").takeIf { it in entries }.orEmpty())
        }
        val legacy = KV.get(legacyUrlKey, "")
        return Library(listOf(legacy).filter(String::isNotEmpty), legacy)
    }

    private fun saveLibrary(value: Library, rules: List<MediaBadgeRule>) {
        val json = JSONObject().put("urls", JSONArray(value.urls)).put("selected", value.selected)
        check(KV.put(libraryKey, json.toString()))
        imported = rules
        library = value
        revision++
    }

    private val builtIn = listOf(
        "8K" to "\\b(?:8k|4320p?)\\b", "4K" to "\\b(?:4k|uhd|2160p?)\\b",
        "1080p" to "\\b(?:1080[pi]?|fhd)\\b", "720p" to "\\b720p?\\b",
        "WEB-DL" to "\\bweb[ ._-]?dl\\b", "BluRay" to "\\bblu[ ._-]?ray\\b",
        "REMUX" to "\\bremux\\b", "Dolby Vision" to "\\b(?:dolby[ ._-]?vision|dovi|dv)\\b",
        "HDR10+" to "\\bhdr10\\+", "HDR10" to "\\bhdr10\\b", "HDR" to "\\bhdr\\b",
        "H.265" to "\\b(?:hevc|[hx][ ._-]?265)\\b", "H.264" to "\\b(?:avc|[hx][ ._-]?264)\\b",
        "AAC" to "\\baac\\b", "AV1" to "\\bav1\\b", "Atmos" to "\\batmos\\b", "Dolby Digital Plus" to "\\b(?:eac3|ddp)\\b",
        "DTS" to "\\bdts\\b", "10-bit" to "\\b10[ ._-]?bit\\b",
    ).map { (label, pattern) -> MediaBadgeRule(label, Pattern.compile(pattern, Pattern.CASE_INSENSITIVE), group = if (label.startsWith("HDR")) "HDR" else label) }

    private fun readDisplay() = Display(
        KV.get(KVKeys.MEDIA_BADGES + "_enabled", true),
        KV.get(KVKeys.MEDIA_BADGES + "_player", true),
        KV.get(KVKeys.MEDIA_BADGES + "_controls", true),
        KV.get(KVKeys.MEDIA_BADGES + "_cards", true),
    )

    fun update(value: Display) {
        KV.put(KVKeys.MEDIA_BADGES + "_enabled", value.enabled)
        KV.put(KVKeys.MEDIA_BADGES + "_player", value.player)
        KV.put(KVKeys.MEDIA_BADGES + "_controls", value.followControls)
        KV.put(KVKeys.MEDIA_BADGES + "_cards", value.cards)
        display = value
    }

    suspend fun importPack(input: String) {
        val url = requireNotNull(input.trim().lineSequence().joinToString("") { it.trim() }.toHttpUrlOrNull())
        require(url.isHttps)
        val key = url.toString()
        val json = Http.getLimitedUtf8(key, 2_000_000)
        val rules = withContext(Dispatchers.Default) { parsePack(json) }
        require(rules.isNotEmpty())
        check(KV.put(packKey(key), json))
        saveLibrary(Library((packUrls + key).distinct(), key), rules)
    }

    suspend fun selectPack(url: String) {
        if (url == packUrl) return
        val rules = if (url.isEmpty()) emptyList() else withContext(Dispatchers.Default) {
            parsePack(readPack(url)).also { require(it.isNotEmpty()) }
        }
        if (url.isEmpty() || url in packUrls) saveLibrary(library.copy(selected = url), rules)
    }

    fun deletePack(url: String) {
        val selected = url == packUrl
        saveLibrary(
            Library(packUrls - url, if (selected) "" else packUrl),
            if (selected) emptyList() else imported,
        )
        KV.delete(packKey(url))
        if (url == KV.get(legacyUrlKey, "")) {
            KV.delete(legacyPackKey)
            KV.delete(legacyUrlKey)
        }
    }

    internal fun parsePack(json: String): List<MediaBadgeRule> {
        require(json.length <= 2_000_000)
        val filters = JSONObject(json).getJSONArray("filters")
        return (0 until minOf(filters.length(), 3_000)).mapNotNull { index ->
            val item = filters.optJSONObject(index) ?: return@mapNotNull null
            if (!item.optBoolean("isEnabled", true)) return@mapNotNull null
            val label = item.optString("name").trim().take(40)
            val raw = item.optString("pattern")
            if (label.isEmpty() || raw.length !in 1..2_000) return@mapNotNull null
            val pattern = runCatching { Pattern.compile(raw, Pattern.CASE_INSENSITIVE) }.getOrNull() ?: return@mapNotNull null
            val image = item.optString("imageURL").toHttpUrlOrNull()?.takeIf { it.isHttps }?.toString()
            MediaBadgeRule(label, pattern, image, item.optString("groupId").ifBlank { label })
        }
    }

    fun match(value: String): List<MediaBadgeRule> =
        imported.ifEmpty { builtIn }.asSequence().filter { it.pattern.matcher(value.take(240)).find() }
            .distinctBy { it.group }.take(7).toList()
}

@Composable
internal fun MediaBadgeRow(value: String, modifier: Modifier = Modifier, preview: Boolean = false, onVideo: Boolean = false) {
    if (!preview && !MediaBadges.display.enabled) return
    val revision = MediaBadges.revision
    val badges = remember(value, revision) { MediaBadges.match(value) }
    if (badges.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (badge in badges) {
            val context = LocalContext.current
            val image = badge.image
            if (image != null) {
                var failed by remember(image) { mutableStateOf(false) }
                if (!failed) {
                    val request = remember(context, image) {
                        ImageRequest.Builder(context).data(image).decoderFactory(SvgDecoder.Factory()).build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = badge.label,
                        modifier = Modifier.height(22.dp).widthIn(min = 22.dp, max = 84.dp)
                            .then(if (preview) Modifier.background(androidx.compose.ui.graphics.Color(0xFF292929), RoundedCornerShape(3.dp)) else Modifier),
                        onError = { failed = true },
                    )
                    continue
                }
            }
            Text(
                badge.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (onVideo) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.background(
                    if (onVideo) androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f) else MaterialTheme.colorScheme.secondaryContainer,
                    RoundedCornerShape(4.dp),
                )
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
    }
}
