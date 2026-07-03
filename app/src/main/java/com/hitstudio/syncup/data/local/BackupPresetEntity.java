package com.hitstudio.syncup.data.local;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.hitstudio.syncup.domain.model.BackupPreset;
import com.hitstudio.syncup.domain.model.DateFilterMode;

import java.time.LocalDate;

@Entity(tableName = "backup_presets")
public class BackupPresetEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String name;

    @ColumnInfo(name = "media_store_source")
    public boolean mediaStoreSource;

    @ColumnInfo(name = "folder_uri")
    public String folderUri;

    @ColumnInfo(name = "include_images")
    public boolean includeImages;

    @ColumnInfo(name = "include_videos")
    public boolean includeVideos;

    @ColumnInfo(name = "include_documents")
    public boolean includeDocuments;

    @ColumnInfo(name = "date_filter_mode")
    public String dateFilterMode;

    @ColumnInfo(name = "custom_from_epoch_day")
    public Long customFromEpochDay;

    @ColumnInfo(name = "custom_to_epoch_day")
    public Long customToEpochDay;

    @ColumnInfo(name = "is_default")
    public boolean isDefault;

    @ColumnInfo(name = "updated_at_epoch_millis")
    public long updatedAtEpochMillis;

    public static BackupPresetEntity fromDomain(BackupPreset preset) {
        BackupPresetEntity entity = new BackupPresetEntity();
        entity.id = preset.getId();
        entity.name = preset.getName();
        entity.mediaStoreSource = preset.isMediaStoreSource();
        entity.folderUri = preset.getFolderUri();
        entity.includeImages = preset.includesImages();
        entity.includeVideos = preset.includesVideos();
        entity.includeDocuments = preset.includesDocuments();
        entity.dateFilterMode = preset.getDateFilterMode().name();
        entity.customFromEpochDay = preset.getCustomFrom() == null
                ? null
                : preset.getCustomFrom().toEpochDay();
        entity.customToEpochDay = preset.getCustomTo() == null
                ? null
                : preset.getCustomTo().toEpochDay();
        entity.isDefault = preset.isDefaultPreset();
        entity.updatedAtEpochMillis = preset.getUpdatedAtEpochMillis();
        return entity;
    }

    public BackupPreset toDomain() {
        return BackupPreset.builder()
                .setId(id)
                .setName(name)
                .setMediaStoreSource(mediaStoreSource)
                .setFolderUri(folderUri)
                .setImages(includeImages)
                .setVideos(includeVideos)
                .setDocuments(includeDocuments)
                .setDateFilterMode(DateFilterMode.valueOf(dateFilterMode))
                .setCustomFrom(toLocalDate(customFromEpochDay))
                .setCustomTo(toLocalDate(customToEpochDay))
                .setDefaultPreset(isDefault)
                .setUpdatedAtEpochMillis(updatedAtEpochMillis)
                .build();
    }

    private static LocalDate toLocalDate(Long epochDay) {
        return epochDay == null ? null : LocalDate.ofEpochDay(epochDay);
    }
}
