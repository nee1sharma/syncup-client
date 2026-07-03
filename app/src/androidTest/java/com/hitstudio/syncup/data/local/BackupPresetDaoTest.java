package com.hitstudio.syncup.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.hitstudio.syncup.domain.model.BackupPreset;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BackupPresetDaoTest {

    private SyncUpDatabase database;
    private BackupPresetDao dao;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, SyncUpDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = database.backupPresetDao();
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void saveAsDefaultPersistsAndUpdatesPreset() {
        BackupPresetEntity entity =
                BackupPresetEntity.fromDomain(BackupPreset.initialDefault());
        long id = dao.saveAsDefault(entity);

        BackupPresetEntity stored = dao.getDefault();
        assertNotNull(stored);
        assertEquals(id, stored.id);
        assertTrue(stored.isDefault);

        stored.name = "Updated preset";
        dao.saveAsDefault(stored);

        assertEquals("Updated preset", dao.getDefault().name);
    }
}
