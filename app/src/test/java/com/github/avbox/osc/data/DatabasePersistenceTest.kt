package com.github.avbox.osc.data

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.github.avbox.osc.bean.VodInfo
import com.github.avbox.osc.testing.TestApplication
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [28])
class DatabasePersistenceTest {
    private lateinit var context: Context
    private lateinit var db: AppDataBase
    private val name = "quality-test.db"

    private fun open(): AppDataBase = Room.databaseBuilder(context, AppDataBase::class.java, name)
        .setDriver(BundledSQLiteDriver()).build()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(name)
        db = open()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(name)
    }

    @Test
    fun collectionInsertReadReplaceReopenAndDelete() = runBlocking {
        val dao = db.getVodCollectDao()
        val record = VodCollect().apply {
            cid = "fixture"
            sourceKey = "source"
            vodId = "movie"
            name = "Fixture title"
        }
        record.id = dao.insert(record).toInt()
        dao.insert(record)
        assertEquals(1, dao.getAll().size)
        db.close()
        db = open()
        val reopened = db.getVodCollectDao()
        val saved = requireNotNull(reopened.getVodCollect("fixture", "source", "movie"))
        assertEquals("Fixture title", saved.name)
        reopened.delete(saved)
        assertTrue(reopened.getAll().isEmpty())
    }

    @Test
    fun historyUpdatesEpisodeAndLineWithoutAddingRowAndSurvivesReopen() = runBlocking {
        val gson = Gson()
        val dao = db.getVodRecordDao()
        val initial = VodInfo(id = "movie", name = "Fixture", playIndex = 1, playFlag = "line-a")
        val record = VodRecord().apply {
            cid = "fixture"
            sourceKey = "source"
            vodId = initial.id
            dataJson = gson.toJson(initial)
            updateTime = 10
        }
        record.id = dao.insert(record).toInt()
        record.dataJson = gson.toJson(initial.copy(playIndex = 3, playFlag = "line-b"))
        dao.insert(record)
        assertEquals(1, dao.getCount("fixture"))
        assertEquals(1, dao.touch("fixture", "source", "movie", 20))
        assertEquals(0, dao.touch("fixture", "source", "missing", 20))
        db.close()
        db = open()
        val saved = requireNotNull(db.getVodRecordDao().getVodRecord("fixture", "source", "movie"))
        val info = gson.fromJson(saved.dataJson, VodInfo::class.java)
        assertEquals(3, info.playIndex)
        assertEquals("line-b", info.playFlag)
        assertEquals(20L, saved.updateTime)
        db.getVodRecordDao().delete(saved)
        assertEquals(0, db.getVodRecordDao().getCount("fixture"))
    }

    @Test
    fun deletingHistoryOnlyAffectsSelectedSubscription() = runBlocking {
        for (subscription in listOf("a", "b")) {
            db.getVodRecordDao().insert(VodRecord().apply {
                cid = subscription
                vodId = "movie"
                sourceKey = "source"
            })
        }
        db.getVodRecordDao().deleteAll("a")
        assertEquals(0, db.getVodRecordDao().getCount("a"))
        assertEquals(1, db.getVodRecordDao().getCount("b"))
    }
}
