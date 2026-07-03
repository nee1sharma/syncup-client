package com.hitstudio.syncup.client.domain.usecase;

public final class PresetValidationResult {

    public enum Error {
        NAME_REQUIRED,
        SOURCE_REQUIRED,
        FILE_TYPE_REQUIRED,
        DOCUMENT_FOLDER_REQUIRED,
        CUSTOM_DATES_REQUIRED,
        CUSTOM_DATE_ORDER_INVALID
    }

    private final Error error;

    private PresetValidationResult(Error error) {
        this.error = error;
    }

    public static PresetValidationResult valid() {
        return new PresetValidationResult(null);
    }

    public static PresetValidationResult invalid(Error error) {
        return new PresetValidationResult(error);
    }

    public boolean isValid() {
        return error == null;
    }

    public Error getError() {
        return error;
    }
}
