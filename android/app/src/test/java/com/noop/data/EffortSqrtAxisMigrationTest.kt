package com.noop.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * MIGRATION_42_43 converts every stored Effort through the load it stands for. Twin of the Swift
 * `testV49ConvertsStoredEffortThroughItsLoad`, same rows, same expected values.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class EffortSqrtAxisMigrationTest {

    private fun openDb(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            InstrumentationRegistry.getInstrumentation().targetContext,
        )
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {}
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    private fun double(db: SupportSQLiteDatabase, sql: String): Double? =
        db.query(sql).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getDouble(0) else null }

    @Test
    fun migrationVersionPair() {
        assertEquals(42, WhoopDatabase.MIGRATION_42_43.startVersion)
        assertEquals(43, WhoopDatabase.MIGRATION_42_43.endVersion)
        assertTrue(WhoopDatabase.ALL_MIGRATIONS.contains(WhoopDatabase.MIGRATION_42_43))
    }

    @Test
    fun convertsStoredEffortThroughItsLoad() {
        val db = openDb()
        db.execSQL("CREATE TABLE dailyMetric (deviceId TEXT NOT NULL, day TEXT NOT NULL, strain REAL, PRIMARY KEY(deviceId, day))")
        db.execSQL("CREATE TABLE workout (deviceId TEXT NOT NULL, startTs INTEGER NOT NULL, sport TEXT NOT NULL, strain REAL, PRIMARY KEY(deviceId, startTs, sport))")
        db.execSQL("CREATE TABLE metricSeries (deviceId TEXT NOT NULL, day TEXT NOT NULL, `key` TEXT NOT NULL, value REAL NOT NULL, PRIMARY KEY(deviceId, day, `key`))")
        db.execSQL("INSERT INTO dailyMetric VALUES ('d', '2026-11-05', 51.0)")
        db.execSQL("INSERT INTO dailyMetric VALUES ('d', '2026-10-31', 71.8)")
        db.execSQL("INSERT INTO dailyMetric VALUES ('d', '2026-10-30', NULL)")
        db.execSQL("INSERT INTO workout VALUES ('d', 100, 'Cycling', 51.0)")
        db.execSQL("INSERT INTO metricSeries VALUES ('w', '2026-01-01', 'strain', 66.67)")
        db.execSQL("INSERT INTO metricSeries VALUES ('w', '2026-01-01', 'recovery', 66.67)")

        WhoopDatabase.convertStoredEffortToSqrtAxis(db)

        assertEquals(27.65, double(db, "SELECT strain FROM dailyMetric WHERE day = '2026-11-05'")!!, 0.0)
        assertEquals(69.96, double(db, "SELECT strain FROM dailyMetric WHERE day = '2026-10-31'")!!, 0.0)
        assertEquals(null, double(db, "SELECT strain FROM dailyMetric WHERE day = '2026-10-30'"))
        assertEquals(27.65, double(db, "SELECT strain FROM workout WHERE startTs = 100")!!, 0.0)
        assertEquals(55.68, double(db, "SELECT value FROM metricSeries WHERE `key` = 'strain'")!!, 0.0)
        assertEquals("only the strain series is on the Effort axis",
            66.67, double(db, "SELECT value FROM metricSeries WHERE `key` = 'recovery'")!!, 0.0)
        db.close()
    }
}
