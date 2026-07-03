package com.hitstudio.syncup.client.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

@Dao
public abstract class BackupPresetDao {

    @Query("SELECT * FROM backup_presets WHERE is_default = 1 ORDER BY updated_at_epoch_millis DESC LIMIT 1")
    public abstract BackupPresetEntity getDefault();

    @Query("UPDATE backup_presets SET is_default = 0 WHERE is_default = 1")
    protected abstract void clearDefault();

    @Insert
    protected abstract long insert(BackupPresetEntity entity);

    @Update
    protected abstract int update(BackupPresetEntity entity);

    @Transaction
    public long saveAsDefault(BackupPresetEntity entity) {
        clearDefault();
        entity.isDefault = true;
        if (entity.id == 0) {
            entity.id = insert(entity);
        } else if (update(entity) == 0) {
            entity.id = insert(entity);
        }
        return entity.id;
    }
}
