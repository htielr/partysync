package com.karthick.partysync.di

import com.karthick.partysync.data.local.prefs.ServerRepository
import com.karthick.partysync.data.local.prefs.ServerRepositoryImpl
import com.karthick.partysync.data.local.prefs.SettingsRepository
import com.karthick.partysync.data.local.prefs.SettingsRepositoryImpl
import com.karthick.partysync.data.repository.FolderMappingRepository
import com.karthick.partysync.data.repository.FolderMappingRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindFolderMappingRepository(impl: FolderMappingRepositoryImpl): FolderMappingRepository

    @Binds
    @Singleton
    abstract fun bindServerRepository(impl: ServerRepositoryImpl): ServerRepository
}
