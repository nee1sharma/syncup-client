package com.hitstudio.syncup.client.domain.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.hitstudio.syncup.client.domain.model.BackupPreset;
import com.hitstudio.syncup.client.domain.model.DateFilterMode;
import com.hitstudio.syncup.client.domain.usecase.PresetValidationResult;
import com.hitstudio.syncup.client.domain.usecase.ValidateBackupPreset;

import org.junit.Test;

import java.time.LocalDate;

public class ValidateBackupPresetTest {

    private final ValidateBackupPreset validator = new ValidateBackupPreset();

    @Test
    public void initialPresetIsValid() {
        assertTrue(validator.execute(BackupPreset.initialDefault()).isValid());
    }

    @Test
    public void requiresAtLeastOneSource() {
        BackupPreset preset = BackupPreset.initialDefault().buildUpon()
                .setMediaStoreSource(false)
                .setFolderUri(null)
                .build();

        assertEquals(
                PresetValidationResult.Error.SOURCE_REQUIRED,
                validator.execute(preset).getError()
        );
    }

    @Test
    public void documentsRequireSelectedFolder() {
        BackupPreset preset = BackupPreset.initialDefault().buildUpon()
                .setImages(false)
                .setVideos(false)
                .setDocuments(true)
                .build();

        assertEquals(
                PresetValidationResult.Error.DOCUMENT_FOLDER_REQUIRED,
                validator.execute(preset).getError()
        );
    }

    @Test
    public void customEndCannotPrecedeStart() {
        BackupPreset preset = BackupPreset.initialDefault().buildUpon()
                .setDateFilterMode(DateFilterMode.CUSTOM_RANGE)
                .setCustomFrom(LocalDate.of(2026, 7, 3))
                .setCustomTo(LocalDate.of(2026, 7, 1))
                .build();

        assertEquals(
                PresetValidationResult.Error.CUSTOM_DATE_ORDER_INVALID,
                validator.execute(preset).getError()
        );
    }
}
