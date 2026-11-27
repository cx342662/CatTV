package com.github.avbox.osc.util

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal fun homeCardLayout(serialized: String, source: String, section: String): Int = runCatching {
    JsonParser.parseString(serialized).asJsonObject.getAsJsonObject(source)?.get(section)?.asInt
        ?.takeIf { it in 0..14 } ?: -1
}.getOrDefault(-1)

internal fun updateHomeCardLayout(serialized: String, source: String, section: String, layout: Int): String {
    require(layout in 0..14)
    val root = runCatching { JsonParser.parseString(serialized).asJsonObject }.getOrElse { JsonObject() }
    val sections = root.get(source)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
    sections.addProperty(section, layout)
    root.add(source, sections)
    return root.toString()
}

object HomeCardSettings {
    private const val KEY = "home_card_layouts_v1"
    private val mutableConfig = MutableStateFlow(KV.get(KEY, "{}"))
    val config = mutableConfig.asStateFlow()

    fun apply(source: String, section: String, layout: Int) {
        val next = updateHomeCardLayout(mutableConfig.value, source, section, layout)
        check(KV.put(KEY, next))
        mutableConfig.value = next
        HomeSettings.layout = HomeSettings.HomeLayout.Horizontal
    }
}

