package com.respondent.pro.di

import com.respondent.pro.data.remote.LocalChannel
import com.respondent.pro.data.remote.ServerChannel
import com.respondent.pro.data.remote.SettingsLocalChannel
import com.respondent.pro.data.remote.CabinetChannel
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ChannelModule {
    @Binds abstract fun bindServerChannel(impl: CabinetChannel): ServerChannel
    @Binds abstract fun bindLocalChannel(impl: SettingsLocalChannel): LocalChannel
}
