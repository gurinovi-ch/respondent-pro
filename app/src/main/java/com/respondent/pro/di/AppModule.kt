package com.respondent.pro.di

import android.content.Context
import androidx.room.Room
import com.respondent.pro.data.local.AppDatabase
import com.respondent.pro.data.local.FeedbackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "respondent_pro.db"
        ).build()
    }

    @Provides
    fun provideFeedbackDao(db: AppDatabase): FeedbackDao = db.feedbackDao()
}
