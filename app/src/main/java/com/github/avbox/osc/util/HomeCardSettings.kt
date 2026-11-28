package com.github.avbox.osc.util

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal val referenceHomeCardLayouts = listOf(1, 0, 5, 2, 8, 14)

internal fun homeCardLayout(serialized: String, source: String, section: String): Int = runCatching {
    if (!homeCardsEnabled(serialized)) return@runCatching -1
    JsonParser.parseString(serialized).asJsonObject.getAsJsonObject(source)?.get(section)?.asInt
        ?.takeIf { it in referenceHomeCardLayouts } ?: -1
}.getOrDefault(-1)

internal fun updateHomeCardLayout(serialized: String, source: String, section: String, layout: Int): String {
    require(layout in referenceHomeCardLayouts)
    val root = runCatching { JsonParser.parseString(serialized).asJsonObject }.getOrElse { JsonObject() }
    val sections = root.get(source)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
    sections.addProperty(section, layout)
    root.add(source, sections)
    return root.toString()
}

internal fun homeCardsEnabled(serialized: String): Boolean = runCatching {
    val root = JsonParser.parseString(serialized).asJsonObject
    root.get("@enabled")?.asBoolean ?: root.entrySet().any { it.value.isJsonObject }
}.getOrDefault(false)

internal fun toggleHomeCards(serialized: String, enabled: Boolean, currentLayout: String): String {
    val root = runCatching { JsonParser.parseString(serialized).asJsonObject }.getOrElse { JsonObject() }
    if (enabled && !homeCardsEnabled(serialized)) root.addProperty("@defaultLayout", currentLayout)
    root.addProperty("@enabled", enabled)
    return root.toString()
}

object HomeCardSettings {
    private const val KEY = "home_card_layouts_v1"
    private val mutableConfig = MutableStateFlow(KV.get(KEY, "{}"))
    val config = mutableConfig.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        save(toggleHomeCards(mutableConfig.value, enabled, HomeSettings.layout.name))
        if (enabled) HomeSettings.layout = HomeSettings.HomeLayout.Horizontal else restoreLayout()
    }

    fun reset() {
        restoreLayout()
        save("{}")
    }

    private fun restoreLayout() {
        val name = runCatching { JsonParser.parseString(mutableConfig.value).asJsonObject.get("@defaultLayout")?.asString }.getOrNull()
        HomeSettings.HomeLayout.entries.firstOrNull { it.name == name }?.let { HomeSettings.layout = it }
    }

    private fun save(next: String) {
        check(KV.put(KEY, next))
        mutableConfig.value = next
    }

    fun apply(source: String, section: String, layout: Int) {
        val enabled = toggleHomeCards(mutableConfig.value, true, HomeSettings.layout.name)
        save(updateHomeCardLayout(enabled, source, section, layout))
        HomeSettings.layout = HomeSettings.HomeLayout.Horizontal
    }
}

