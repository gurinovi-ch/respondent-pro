package com.respondent.pro.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE feedbacks ADD COLUMN startedAt INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE feedbacks ADD COLUMN isComplete INTEGER NOT NULL DEFAULT 1")
    }
}

/**
 * v2 → v3: очередь отправки в C web.
 * serverSyncedAt NULL = ждёт отправки; clientKey NULL = legacy-строка
 * (при отправке ключ выводится из id — см. CabinetChannel).
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE feedbacks ADD COLUMN serverSyncedAt INTEGER")
        database.execSQL("ALTER TABLE feedbacks ADD COLUMN clientKey TEXT")
    }
}
