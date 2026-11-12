package com.github.avbox.osc.api

import com.github.avbox.osc.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.util.concurrent.TimeUnit

internal object MdbListRatings {
    private val baseUrl = "https://api.mdblist.com/".toHttpUrl()
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun ratings(id: Int, mediaType: String?, apiKey: String): Map<String, Double>? {
        if (id <= 0 || apiKey.isBlank()) return null
        val type = when (mediaType) {
            "movie" -> "movie"
            TmdbApi.MEDIA_TV -> "show"
            else -> return null
        }
        val url = baseUrl.newBuilder()
            .addPathSegments("tmdb/$type/$id/")
            .addQueryParameter("apikey", apiKey)
            .build()
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        AppLog.e("MDBList HTTP ${response.code}")
                        return@use null
                    }
                    val body = response.body?.string() ?: return@use null
                    val item = when (val payload = JSONTokener(body).nextValue()) {
                        is JSONObject -> payload
                        is JSONArray -> payload.optJSONObject(0)
                        else -> null
                    }
                    val rows = item?.optJSONArray("ratings") ?: return@use null
                    buildMap {
                        for (index in 0 until rows.length()) {
                            val rating = rows.optJSONObject(index) ?: continue
                            val source = rating.optString("source").lowercase()
                            val value = rating.optDouble("value")
                            val max = if (source == "imdb") 10.0 else 100.0
                            if (source.isNotEmpty() && value > 0.0 && value <= max) {
                                put(source, value)
                            }
                        }
                    }
                }
            } catch (_: IOException) {
                AppLog.e("MDBList request failed")
                null
            } catch (_: org.json.JSONException) {
                AppLog.e("MDBList response could not be parsed")
                null
            }
        }
    }
}
