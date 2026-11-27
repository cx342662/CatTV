package com.github.avbox.osc.util

object PlaybackLogSanitizer {
    private val url = Regex("""https?://[^\s<>"\\]+""", RegexOption.IGNORE_CASE)
    private val secret = Regex("""(?i)(["']?(?:cookie|set-cookie|authorization|proxy-authorization|[\w-]*(?:token|signature|secret|password|sign|auth|credential)[\w-]*)["']?\s*[:=]\s*)("[^"\r\n]*"|'[^'\r\n]*'|[^\r\n,}]+)""")

    fun redact(value: String): String {
        val urls = url.replace(value) { match ->
            val text = match.value.replace(Regex("""(https?://)[^/@]+@"""), "$1[redacted]@")
            val query = text.indexOf('?')
            if (query < 0) text.substringBefore('#') else {
                text.substring(0, query + 1) + text.substring(query + 1).substringBefore('#').split('&').joinToString("&") {
                    it.substringBefore('=') + "=[redacted]"
                }
            }
        }
        return secret.replace(urls) { it.groupValues[1] + "[redacted]" }
    }
}
