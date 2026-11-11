package com.github.avbox.osc.server

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.channels.Channel

internal object SourcePairing {
    const val PREFIX = "/pair-source/"
    data class Source(val name: String, val url: String, val vod: Boolean)
    class Pairing {
        val token: String = UUID.randomUUID().toString()
        val incoming = Channel<Source>(1)
        val expires = android.os.SystemClock.elapsedRealtime() + 300_000
    }
    private val sessions = mutableMapOf<String, Pairing>()

    @Synchronized fun open(): Pairing {
        sessions.values.filter { it.expires <= android.os.SystemClock.elapsedRealtime() }.forEach(::close)
        check(sessions.size < 4)
        return Pairing().also { sessions[it.token] = it }
    }

    @Synchronized fun close(pairing: Pairing) {
        sessions.remove(pairing.token)
        pairing.incoming.cancel()
    }

    fun serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        fun reply(status: Status, body: String) =
            NanoHTTPD.newFixedLengthResponse(status, "text/html; charset=utf-8",
                "<!doctype html><html lang='zh'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>CatTV</title>$body</html>")
                .apply {
                    addHeader("Cache-Control", "no-store")
                    addHeader("Referrer-Policy", "no-referrer")
                    addHeader("Content-Security-Policy", "default-src 'none'; form-action 'self'; frame-ancestors 'none'")
                }
        val pairing = synchronized(this) { sessions[session.uri.removePrefix(PREFIX)] }
            ?.takeIf { it.expires > android.os.SystemClock.elapsedRealtime() }
            ?: return reply(Status.NOT_FOUND, "<p>二维码已失效，请在电视上重新打开。</p>")
        if (session.method == NanoHTTPD.Method.GET) return reply(Status.OK, """
            <h1>添加 CatTV 接口</h1>
            <form method="post">
            <p><label>类型 <select name="kind"><option value="vod">点播</option><option value="live">直播</option></select></label></p>
            <p><label>名称 <input name="name" maxlength="80"></label></p>
            <p><label>地址 <input name="url" type="url" required maxlength="2048" placeholder="https://"></label></p>
            <button>发送到电视</button></form>
        """.trimIndent())
        if (session.method != NanoHTTPD.Method.POST ||
            (session.headers["content-length"]?.toIntOrNull() ?: 0) !in 1..8192 ||
            session.headers["content-type"]?.substringBefore(';') != "application/x-www-form-urlencoded"
        ) return reply(Status.BAD_REQUEST, "<p>请求格式无效。</p>")
        val body = runCatching {
            session.parseBody(mutableMapOf())
            session.parameters
        }.getOrNull() ?: return reply(Status.BAD_REQUEST, "<p>无法读取表单。</p>")
        val url = body["url"]?.singleOrNull()?.trim().orEmpty()
        val name = body["name"]?.singleOrNull()?.trim().orEmpty()
        val kind = body["kind"]?.singleOrNull()
        val uri = runCatching { URI(url) }.getOrNull()
        if (url.length > 2048 || uri?.scheme?.lowercase() !in setOf("http", "https") ||
            uri?.host.isNullOrBlank() || uri?.userInfo != null ||
            name.length > 80 || name.any { it.isISOControl() } || kind !in setOf("vod", "live")
        ) return reply(Status.BAD_REQUEST, "<p>请输入有效的 HTTP 或 HTTPS 接口地址。</p>")
        val sent = synchronized(this) {
            sessions[pairing.token] === pairing &&
                pairing.expires > android.os.SystemClock.elapsedRealtime() &&
                pairing.incoming.trySend(Source(name.ifEmpty { url }, url, kind == "vod")).isSuccess
        }
        return if (sent) reply(Status.OK, "<p>已发送，请在电视上确认保存。</p>")
        else reply(Status.BAD_REQUEST, "<p>请在电视上重新打开二维码。</p>")
    }
}

