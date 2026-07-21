package com.karthick.partysync.di

import android.content.Context
import androidx.room.Room
import com.karthick.partysync.data.local.db.AppDatabase
import com.karthick.partysync.data.local.db.FolderMappingDao
import com.karthick.partysync.data.local.db.SyncFileStateDao
import com.karthick.partysync.data.local.db.Up2kSessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .addMigrations(AppDatabase.MIGRATION_2_3)
            // Safety net for any future unhandled version bump — the 2->3 jump above is a
            // real migration and won't hit this fallback. See the "Multi-server support" plan
            // for why the earlier 1->2 jump used a one-time destructive migration instead.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideFolderMappingDao(db: AppDatabase): FolderMappingDao = db.folderMappingDao()

    @Provides
    fun provideSyncFileStateDao(db: AppDatabase): SyncFileStateDao = db.syncFileStateDao()

    @Provides
    fun provideUp2kSessionDao(db: AppDatabase): Up2kSessionDao = db.up2kSessionDao()
}
