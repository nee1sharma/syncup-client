package com.hitstudio.syncup.client.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.hitstudio.syncup.client.data.local.BackupPresetDao;
import com.hitstudio.syncup.client.data.local.BackupPresetEntity;
import com.hitstudio.syncup.client.data.local.SyncUpDatabase;
import com.hitstudio.syncup.client.domain.model.BackupPreset;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PresetRepository {

    public interface Callback<T> {
        void onSuccess(T value);

        void onError(Throwable error);
    }

    private static volatile PresetRepository instance;

    private final BackupPresetDao dao;
    private final ExecutorService databaseExecutor;
    private final Handler mainHandler;

    private PresetRepository(Context context) {
        dao = SyncUpDatabase.getInstance(context).backupPresetDao();
        databaseExecutor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public static PresetRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (PresetRepository.class) {
                if (instance == null) {
                    instance = new PresetRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public void getOrCreateDefault(Callback<BackupPreset> callback) {
        databaseExecutor.execute(() -> {
            try {
                BackupPresetEntity entity = dao.getDefault();
                if (entity == null) {
                    entity = BackupPresetEntity.fromDomain(BackupPreset.initialDefault());
                    dao.saveAsDefault(entity);
                }
                BackupPreset preset = entity.toDomain();
                mainHandler.post(() -> callback.onSuccess(preset));
            } catch (Throwable error) {
                mainHandler.post(() -> callback.onError(error));
            }
        });
    }

    public void saveDefault(BackupPreset preset, Callback<BackupPreset> callback) {
        databaseExecutor.execute(() -> {
            try {
                BackupPresetEntity entity = BackupPresetEntity.fromDomain(preset);
                entity.updatedAtEpochMillis = System.currentTimeMillis();
                dao.saveAsDefault(entity);
                BackupPreset saved = entity.toDomain();
                mainHandler.post(() -> callback.onSuccess(saved));
            } catch (Throwable error) {
                mainHandler.post(() -> callback.onError(error));
            }
        });
    }
}
