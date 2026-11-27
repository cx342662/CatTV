package com.github.avbox.osc.player.core

import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.avbox.osc.testing.TestApplication
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = TestApplication::class)
class PlayerHttpRoundTripTest {
    @Test fun preservesSpiderHeadersThroughRedirectAndReadsFlvBytes() {
        ServerSocket(0).use { server ->
            server.soTimeout = 5000
            val executor = Executors.newSingleThreadExecutor()
            val requests = executor.submit<List<List<String>>> {
                (0..1).map { index ->
                    server.accept().use { socket ->
                        socket.soTimeout = 5000
                        val reader = socket.getInputStream().bufferedReader()
                        val lines = mutableListOf<String>()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            lines += line
                        }
                        val body = if (index == 0) "" else "FLV"
                        val head = if (index == 0) "HTTP/1.1 302 Found\r\nLocation: /live/room.flv\r\n" else "HTTP/1.1 200 OK\r\nContent-Type: video/x-flv\r\n"
                        socket.getOutputStream().write((head + "Content-Length: ${body.length}\r\nConnection: close\r\n\r\n" + body).toByteArray())
                        lines
                    }
                }
            }
            val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
            val factory = OkHttpDataSource.Factory(client).setUserAgent("CatTV-test-UA")
            factory.setDefaultRequestProperties(mapOf("Referer" to "https://room.example/", "Cookie" to "session=test-only"))
            val source = factory.createDataSource()
            try {
                assertEquals(3L, source.open(DataSpec(Uri.parse("http://127.0.0.1:${server.localPort}/start"))))
                val bytes = ByteArray(3)
                assertEquals(3, source.read(bytes, 0, bytes.size))
                assertEquals("FLV", bytes.toString(Charsets.UTF_8))
                assertEquals(200, source.responseCode)
                assertTrue(source.uri.toString().contains("/live/room.flv"))
                for (request in requests.get(5, TimeUnit.SECONDS)) {
                    assertTrue(request.any { it.equals("User-Agent: CatTV-test-UA", true) })
                    assertTrue(request.any { it.equals("Referer: https://room.example/", true) })
                    assertTrue(request.any { it.equals("Cookie: session=test-only", true) })
                }
            } finally {
                source.close()
                executor.shutdownNow()
                client.dispatcher.executorService.shutdownNow()
                client.connectionPool.evictAll()
            }
        }
    }
}
