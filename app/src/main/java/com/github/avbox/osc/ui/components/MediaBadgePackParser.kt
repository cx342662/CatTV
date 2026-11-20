package com.github.avbox.osc.ui.components

import com.google.re2j.Pattern
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

internal object MediaBadgePackParser {
    fun parse(json: String): List<MediaBadgeRule> {
        require(json.length <= 2_000_000)
        val filters = JSONObject(json).getJSONArray("filters")
        return (0 until minOf(filters.length(), 3_000)).mapNotNull { index ->
            val item = filters.optJSONObject(index) ?: return@mapNotNull null
            if (!item.optBoolean("isEnabled", true)) return@mapNotNull null
            val label = item.optString("name").trim().take(40)
            val raw = item.optString("pattern")
            if (label.isEmpty() || raw.length !in 1..2_000) return@mapNotNull null
            val pattern = runCatching { Pattern.compile(raw, Pattern.CASE_INSENSITIVE) }.getOrNull() ?: return@mapNotNull null
            val rawImage = item.optString("imageURL")
            val image = rawImage.toHttpUrlOrNull()?.takeIf { it.isHttps }?.toString()
            MediaBadgeRule(label, pattern, image, item.optString("groupId").ifBlank { label })
        }
    }

}
