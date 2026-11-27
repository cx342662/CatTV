package com.github.avbox.osc.bean

object StreamLabels {
    private val quality = Regex("""(?i)(?:原画|蓝光|超清|高清|流畅|标清|杜比|HDR|(?:\d+(?:\.\d+)?\s*(?:K|M|P|FPS|Mbps)))(?:[\s_+\-]|\d|K|M|P|FPS|Mbps|HDR|杜比|原画|蓝光|超清|高清|流畅|标清)*""")
    fun isQuality(name: String?): Boolean = name?.trim()?.let { quality.matches(it) } == true
}
