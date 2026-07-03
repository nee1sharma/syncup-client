package com.hitstudio.syncup.client.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {BackupPresetEntity.class}, version = 1, exportSchema = false)
public abstract class SyncUpDatabase extends RoomDatabase {

    private static volatile SyncUpDatabase instance;

    public abstract BackupPresetDao backupPresetDao();

    public static SyncUpDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (SyncUpDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    SyncUpDatabase.class,
                                    "syncup.syncup.syncup.db"
                            )
                            .build();
                }
            }
        }
        return instance;
    }
}
