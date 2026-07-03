package com.hitstudio.syncup.client.domain.usecase;

import com.hitstudio.syncup.client.domain.model.BackupPreset;
import com.hitstudio.syncup.client.domain.model.DateFilterMode;

public final class ValidateBackupPreset {

    public PresetValidationResult execute(BackupPreset preset) {
        if (preset.getName().trim().isEmpty()) {
            return PresetValidationResult.invalid(PresetValidationResult.Error.NAME_REQUIRED);
        }
        if (!preset.isMediaStoreSource() && !preset.hasFolderSource()) {
            return PresetValidationResult.invalid(PresetValidationResult.Error.SOURCE_REQUIRED);
        }
        if (!preset.includesImages() && !preset.includesVideos() && !preset.includesDocuments()) {
            return PresetValidationResult.invalid(PresetValidationResult.Error.FILE_TYPE_REQUIRED);
        }
        if (preset.includesDocuments() && !preset.hasFolderSource()) {
            return PresetValidationResult.invalid(PresetValidationResult.Error.DOCUMENT_FOLDER_REQUIRED);
        }
        if (preset.getDateFilterMode() == DateFilterMode.CUSTOM_RANGE) {
            if (preset.getCustomFrom() == null || preset.getCustomTo() == null) {
                return PresetValidationResult.invalid(
                        PresetValidationResult.Error.CUSTOM_DATES_REQUIRED
                );
            }
            if (preset.getCustomTo().isBefore(preset.getCustomFrom())) {
                return PresetValidationResult.invalid(
                        PresetValidationResult.Error.CUSTOM_DATE_ORDER_INVALID
                );
            }
        }
        return PresetValidationResult.valid();
    }
}
