package com.respondent.pro.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.respondent.pro.data.model.Feedback

@Database(entities = [Feedback::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun feedbackDao(): FeedbackDao
}
