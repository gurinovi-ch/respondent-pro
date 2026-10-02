package com.respondent.pro.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.respondent.pro.cabinet.BindingStorage
import com.respondent.pro.cabinet.CabinetApi
import com.respondent.pro.cabinet.EncryptedBindingStorage
import com.respondent.pro.cabinet.KabinetConfig
import com.respondent.pro.data.local.AppDatabase
import com.respondent.pro.data.local.FeedbackDao
import com.respondent.pro.data.remote.TelegramApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private const val TELEGRAM_BASE_URL = "https://api.telegram.org"

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE feedbacks ADD COLUMN startedAt INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE feedbacks ADD COLUMN isComplete INTEGER NOT NULL DEFAULT 1")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "respondent_pro.db"
        ).addMigrations(MIGRATION_1_2).build()
    }

    @Provides
    fun provideFeedbackDao(db: AppDatabase): FeedbackDao = db.feedbackDao()

    @Provides
    @Singleton
    fun provideRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(TELEGRAM_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideTelegramApi(retrofit: Retrofit): TelegramApi {
        return retrofit.create(TelegramApi::class.java)
    }

    /** API кабинета — отдельный Retrofit со своим baseUrl (не путать с Telegram). */
    @Provides
    @Singleton
    fun provideCabinetApi(): CabinetApi {
        return Retrofit.Builder()
            .baseUrl(KabinetConfig.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CabinetApi::class.java)
    }

    @Provides
    @Singleton
    fun provideBindingStorage(@ApplicationContext context: Context): BindingStorage {
        return EncryptedBindingStorage.create(context)
    }
}
