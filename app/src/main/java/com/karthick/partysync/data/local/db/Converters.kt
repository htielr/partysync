package com.karthick.partysync.data.local.db

import androidx.room.TypeConverter
import com.karthick.partysync.domain.model.FileSyncStatus
import com.karthick.partysync.domain.model.MappingSyncStatus
import com.karthick.partysync.domain.model.SyncMode
import com.karthick.partysync.domain.model.UploadSessionStatus

class Converters {
    @TypeConverter
    fun syncModeToString(value: SyncMode): String = value.name

    @TypeConverter
    fun stringToSyncMode(value: String): SyncMode = SyncMode.valueOf(value)

    @TypeConverter
    fun mappingStatusToString(value: MappingSyncStatus): String = value.name

    @TypeConverter
    fun stringToMappingStatus(value: String): MappingSyncStatus = MappingSyncStatus.valueOf(value)

    @TypeConverter
    fun fileStatusToString(value: FileSyncStatus): String = value.name

    @TypeConverter
    fun stringToFileStatus(value: String): FileSyncStatus = FileSyncStatus.valueOf(value)

    @TypeConverter
    fun uploadSessionStatusToString(value: UploadSessionStatus): String = value.name

    @TypeConverter
    fun stringToUploadSessionStatus(value: String): UploadSessionStatus = UploadSessionStatus.valueOf(value)
}
