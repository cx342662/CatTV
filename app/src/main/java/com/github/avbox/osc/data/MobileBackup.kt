package com.github.avbox.osc.data

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import androidx.annotation.Keep
import com.github.avbox.osc.util.KV
import com.github.avbox.osc.util.KVKeys
import com.github.avbox.osc.util.MD5
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

internal object MobileBackup {
    private const val VERSION = 1
    private const val MAX_BYTES = 16 * 1024 * 1024
    private val lock = Mutex()
    private val gson = Gson()
    private val settings = setOf(KVKeys.API_URL, KVKeys.LIVE_API_URL, KVKeys.API_LINE_SOURCE, KVKeys.LIVE_API_LINE_SOURCE)
    private val subscriptions = setOf(KVKeys.SUBSCRIBE_LIST, KVKeys.LIVE_SUBSCRIBE_LIST)

    @Keep
    private data class Snapshot(
        val version: Int,
        val sources: Map<String, String>,
        val subscriptions: Map<String, List<String>>,
        val history: List<VodRecord>,
        val favorites: List<VodCollect>,
        val progressIndex: Map<String, String>,
        val positions: Map<String, Long>,
    )

    suspend fun export(open: () -> OutputStream?) = lock.withLock {
        withContext(Dispatchers.IO) {
            val index = KV.keys(WatchProgressIndex.KEY_PREFIX).associateWith { KV.get(it, "") }
            val db = AppDataManager.get()
            val snapshot = db.withReadTransaction {
                val positions = progressKeys(index).mapNotNull { key ->
                    (AppGraph.cacheRepository.get(key) as? Long)?.let { key to it }
                }.toMap()
                Snapshot(
                    VERSION,
                    settings.associateWith { KV.get(it, "") },
                    subscriptions.associateWith { KV.get(it, arrayListOf<String>()) },
                    db.getVodRecordDao().backupRows(),
                    db.getVodCollectDao().getAll(),
                    index,
                    positions,
                )
            }
            val bytes = gson.toJson(snapshot).toByteArray(Charsets.UTF_8)
            require(bytes.size <= MAX_BYTES)
            requireNotNull(open()).use { it.write(bytes) }
        }
    }

    suspend fun restore(open: () -> InputStream?): Unit = lock.withLock {
        withContext(Dispatchers.IO) {
            val bytes = requireNotNull(open()).use { input ->
                val result = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(result.size() + count <= MAX_BYTES)
                    result.write(buffer, 0, count)
                }
                result.toByteArray()
            }
            val snapshot = requireNotNull(gson.fromJson(bytes.toString(Charsets.UTF_8), Snapshot::class.java))
            require(snapshot.version == VERSION)
            require(snapshot.sources.keys == settings && snapshot.subscriptions.keys == subscriptions)
            require(snapshot.sources.values.all { it.length <= 8192 })
            require(snapshot.subscriptions.values.all { entries -> entries.all { it.length <= 8192 } })
            require(snapshot.history.all { !it.vodId.isNullOrBlank() && it.cid != null && it.sourceKey != null })
            require(snapshot.favorites.all { !it.vodId.isNullOrBlank() && it.cid != null && it.sourceKey != null })
            val index = snapshot.progressIndex
            require(index.all { (key, value) ->
                key.startsWith(WatchProgressIndex.KEY_PREFIX) && WatchProgressIndex.decode(value) != null
            })
            val allowedPositions = progressKeys(index)
            require(snapshot.positions.keys.all { it in allowedPositions } && snapshot.positions.values.all { it >= 0 })

            val db = AppDataManager.get()
            val changes = LinkedHashMap<String, Any>()
            for ((key, value) in index) {
                val entry = requireNotNull(WatchProgressIndex.decode(value))
                var merged = KV.get(key, "")
                for (episode in entry.eps) merged = WatchProgressIndex.withEp(merged, episode, entry.at)
                changes[key] = merged
            }
            changes.putAll(snapshot.sources)
            snapshot.subscriptions.forEach { (key, entries) ->
                changes[key] = ArrayList((KV.get(key, arrayListOf<String>()) + entries).distinct())
            }
            val previous = changes.keys.associateWith { key ->
                if (key in subscriptions) KV.get<ArrayList<String>>(key) else KV.get<String>(key)
            }
            try {
                db.withWriteTransaction<Unit> {
                    val history = db.getVodRecordDao()
                    for (record in snapshot.history) {
                        val existing = history.getVodRecord(record.cid, record.sourceKey, record.vodId)
                        if (existing != null && existing.updateTime > record.updateTime) continue
                        record.id = existing?.id ?: 0
                        history.insert(record)
                    }
                    val favorites = db.getVodCollectDao()
                    for (favorite in snapshot.favorites) {
                        if (favorites.getVodCollect(favorite.cid, favorite.sourceKey, favorite.vodId) != null) continue
                        favorite.id = 0
                        favorites.insert(favorite)
                    }
                    for ((key, value) in snapshot.positions) {
                        if (AppGraph.cacheRepository.get(key) == null) AppGraph.cacheRepository.save(key, value)
                    }
                    changes.forEach { (key, value) -> check(KV.put(key, value)) }
                }
            } catch (error: Exception) {
                previous.forEach { (key, value) -> KV.put(key, value) }
                throw error
            }
        }
    }

    private fun progressKeys(index: Map<String, String>): Set<String> =
        index.values.flatMap { WatchProgressIndex.decode(it)?.eps.orEmpty() }
            .mapNotNull(MD5::string2MD5).toSet()
}
