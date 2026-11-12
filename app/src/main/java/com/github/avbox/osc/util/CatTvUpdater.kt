package com.github.avbox.osc.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.github.avbox.osc.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * CatTV's opt-in updater. Sources updates exclusively from the project's GitHub Releases.
 * Never installs a release with a different package ID, missing digest, or different signing
 * certificate. Install is always confirmed by Android's system package installer.
 */
object CatTvUpdater {
    private const val RELEASE_API = "https://api.github.com/repos/cx342662/CatTV/releases/latest"
    private const val MAX_APK_BYTES = 250L * 1024 * 1024
    private val downloadLock = Mutex()

    data class UpdateInfo(
        val versionName: String,
        val assetUrl: String,
        val expectedSha256: String,
        val assetSize: Long,
    )

    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "CatTV-Android")
        }
        try {
            if (connection.responseCode == 404) return@withContext null
            check(connection.responseCode == 200) { "GitHub 返回状态 ${connection.responseCode}" }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (json.optBoolean("draft") || json.optBoolean("prerelease")) return@withContext null
            val version = json.optString("tag_name").trimStart('v', 'V')
            if (!isNewerVersion(version, BuildConfig.VERSION_NAME)) return@withContext null
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                val digest = asset.optString("digest")
                // Fail closed: no published sha256 digest = no automatic installation.
                if (!digest.startsWith("sha256:") || digest.length != 71) continue
                val url = asset.getString("browser_download_url")
                val size = asset.optLong("size", 0L)
                if (!url.startsWith("https://github.com/cx342662/CatTV/releases/download/") ||
                    size !in 1..MAX_APK_BYTES) continue
                return@withContext UpdateInfo(version, url, digest.substringAfter(':'), size)
            }
            error("此版本没有带 SHA-256 校验的 APK，请联系维护者")
        } finally {
            connection.disconnect()
        }
    }

    suspend fun downloadAndInstall(
        context: Context,
        update: UpdateInfo,
        onProgress: (Int) -> Unit,
    ) = downloadLock.withLock {
        val apk = withContext(Dispatchers.IO) {
            val file = File(context.externalCacheDir ?: context.cacheDir, "cattv-update.apk")
            val con = (URL(update.assetUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "CatTV-Android")
            }
            try {
                check(con.responseCode == 200) { "下载失败，HTTP ${con.responseCode}" }
                val sha = MessageDigest.getInstance("SHA-256")
                var total = 0L
                var progress = 0
                con.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            total += n
                            check(total <= MAX_APK_BYTES && total <= update.assetSize) { "APK 大小异常" }
                            sha.update(buffer, 0, n)
                            out.write(buffer, 0, n)
                            val percent = (total * 100 / update.assetSize).toInt()
                            if (percent != progress) {
                                progress = percent
                                withContext(Dispatchers.Main.immediate) { onProgress(percent) }
                            }
                        }
                    }
                }
                check(total == update.assetSize) { "APK 文件下载不完整" }
                ensureActive()
                val actual = sha.digest().joinToString("") { "%02x".format(it) }
                check(actual.equals(update.expectedSha256, ignoreCase = true)) { "APK 校验失败" }
                verifyArchive(context, file)
                file
            } catch (e: Exception) {
                file.delete()
                throw e
            } finally {
                con.disconnect()
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    @Suppress("DEPRECATION")
    private fun verifyArchive(context: Context, apk: File) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28)
            PackageManager.GET_SIGNING_CERTIFICATES
        else PackageManager.GET_SIGNATURES
        val candidate = pm.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: error("不是有效的 Android 安装包")
        check(candidate.packageName == context.packageName) { "安装包应用标识不一致" }
        val installed = pm.getPackageInfo(context.packageName, flags)
        val incoming = if (Build.VERSION.SDK_INT >= 28)
            candidate.signingInfo?.apkContentsSigners
        else candidate.signatures
        val current = if (Build.VERSION.SDK_INT >= 28)
            installed.signingInfo?.apkContentsSigners
        else installed.signatures
        check(!incoming.isNullOrEmpty() && !current.isNullOrEmpty() &&
                incoming.map { it.toCharsString() }.toSet() == current.map { it.toCharsString() }.toSet()
        ) { "APK 签名与当前安装版本不一致，无法安全覆盖更新" }
        val incomingCode = if (Build.VERSION.SDK_INT >= 28) candidate.longVersionCode
                           else candidate.versionCode.toLong()
        val currentCode = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode
                          else installed.versionCode.toLong()
        check(incomingCode > currentCode) { "安装包版本号没有提高" }
    }

    private fun isNewerVersion(candidate: String, current: String): Boolean {
        fun parts(v: String): List<Int> {
            val numbers = v.split('.')
            if (numbers.isEmpty() || numbers.any { it.toIntOrNull() == null }) return emptyList()
            return numbers.map { it.toInt() }
        }
        val a = parts(candidate)
        val b = parts(current)
        if (a.isEmpty() || b.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
