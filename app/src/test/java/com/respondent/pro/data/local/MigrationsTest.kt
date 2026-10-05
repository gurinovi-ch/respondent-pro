package com.respondent.pro.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationsTest {

    /**
     * androidx.sqlite 2.4.0: FrameworkSQLiteDatabase — internal, но JVM-конструктор публичный.
     * Оборачиваем SQLiteDatabase в SupportSQLiteDatabase через рефлексию,
     * чтобы вызвать Migration.migrate() напрямую.
     */
    private fun supportDb(db: SQLiteDatabase): SupportSQLiteDatabase =
        Class.forName("androidx.sqlite.db.framework.FrameworkSQLiteDatabase")
            .getConstructor(SQLiteDatabase::class.java)
            .newInstance(db) as SupportSQLiteDatabase

    private fun createV2Database(): SQLiteDatabase {
        val db = SQLiteDatabase.create(null)
        db.execSQL(
            """CREATE TABLE feedbacks (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                rating INTEGER NOT NULL,
                text TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                startedAt INTEGER NOT NULL,
                sentToTelegram INTEGER NOT NULL,
                isComplete INTEGER NOT NULL,
                errorMessage TEXT
            )"""
        )
        db.execSQL(
            "INSERT INTO feedbacks (rating, text, createdAt, startedAt, sentToTelegram, isComplete) " +
                "VALUES (5, 'старый отзыв', 1000, 500, 1, 1)"
        )
        return db
    }

    @Test
    fun `MIGRATION_2_3 добавляет колонки и не трогает старые строки`() {
        val db = createV2Database()

        MIGRATION_2_3.migrate(supportDb(db))

        val c = db.rawQuery("SELECT serverSyncedAt, clientKey FROM feedbacks", null)
        c.moveToFirst()
        assertTrue(c.isNull(0))   // legacy-строка ещё не синхронизирована с сервером
        assertTrue(c.isNull(1))   // ключ появится при следующей отправке (legacy-фолбэк)
        c.close()

        // Новая строка с clientKey помещается в колонку
        db.execSQL(
            "INSERT INTO feedbacks (rating, text, createdAt, startedAt, sentToTelegram, isComplete, clientKey) " +
                "VALUES (4, 'новый', 2000, 1500, 0, 1, 'uuid-1')"
        )
        val c2 = db.rawQuery("SELECT clientKey FROM feedbacks ORDER BY id DESC LIMIT 1", null)
        c2.moveToFirst()
        assertEquals("uuid-1", c2.getString(0))
        c2.close()
        db.close()
    }

    @Test
    fun `MIGRATION_1_2 осталась рабочей после переноса в Migrations_kt`() {
        val db = SQLiteDatabase.create(null)
        db.execSQL(
            """CREATE TABLE feedbacks (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                rating INTEGER NOT NULL,
                text TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                sentToTelegram INTEGER NOT NULL,
                errorMessage TEXT
            )"""
        )

        MIGRATION_1_2.migrate(supportDb(db)) // не должно бросить

        // Колонки из миграции существуют:
        val probe = db.rawQuery("SELECT startedAt, isComplete FROM feedbacks", null)
        assertTrue(probe.columnCount == 2)
        probe.close(); db.close()
    }
}
