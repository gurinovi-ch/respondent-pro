package com.respondent.pro.di

import android.content.Context
import androidx.room.Room
import com.respondent.pro.cabinet.ApiKeyInterceptor
import com.respondent.pro.cabinet.BindingStorage
import com.respondent.pro.cabinet.CabinetApi
import com.respondent.pro.cabinet.EncryptedBindingStorage
import com.respondent.pro.cabinet.KabinetConfig
import com.respondent.pro.data.local.AppDatabase
import com.respondent.pro.data.local.FeedbackDao
import com.respondent.pro.data.local.MIGRATION_1_2
import com.respondent.pro.data.local.MIGRATION_2_3
import com.respondent.pro.data.remote.TelegramApi
import com.respondent.pro.standalone.AndroidDeviceInfoSource
import com.respondent.pro.standalone.CompanyNameSource
import com.respondent.pro.standalone.DataStoreStandaloneStore
import com.respondent.pro.standalone.DeviceInfoSource
import com.respondent.pro.standalone.SettingsCompanyNameSource
import com.respondent.pro.standalone.StandaloneApi
import com.respondent.pro.standalone.StandaloneStore
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "respondent_pro.db"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
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
    fun provideCabinetApi(bindingStorage: BindingStorage): CabinetApi {
        val client = okhttp3.OkHttpClient.Builder()
            .addInterceptor(ApiKeyInterceptor(bindingStorage))
            .build()
        return Retrofit.Builder()
            .baseUrl(KabinetConfig.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CabinetApi::class.java)
    }

    @Provides
    @Singleton
    fun provideBindingStorage(@ApplicationContext context: Context): BindingStorage {
        return EncryptedBindingStorage.create(context)
    }

    @Provides
    @Singleton
    fun provideStandaloneStore(impl: DataStoreStandaloneStore): StandaloneStore = impl

    @Provides
    @Singleton
    fun provideDeviceInfoSource(impl: AndroidDeviceInfoSource): DeviceInfoSource = impl

    @Provides
    @Singleton
    fun provideCompanyNameSource(impl: SettingsCompanyNameSource): CompanyNameSource = impl

    @Provides
    @Singleton
    fun provideStandaloneApi(): StandaloneApi =
        Retrofit.Builder()
            .baseUrl(KabinetConfig.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(StandaloneApi::class.java)
}
