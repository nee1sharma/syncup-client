package com.hitstudio.syncup.client.domain.model;

import java.time.LocalDate;
import java.util.Objects;

public final class BackupPreset {

    private final long id;
    private final String name;
    private final boolean mediaStoreSource;
    private final String folderUri;
    private final boolean images;
    private final boolean videos;
    private final boolean documents;
    private final DateFilterMode dateFilterMode;
    private final LocalDate customFrom;
    private final LocalDate customTo;
    private final boolean defaultPreset;
    private final long updatedAtEpochMillis;

    private BackupPreset(Builder builder) {
        id = builder.id;
        name = Objects.requireNonNull(builder.name);
        mediaStoreSource = builder.mediaStoreSource;
        folderUri = builder.folderUri;
        images = builder.images;
        videos = builder.videos;
        documents = builder.documents;
        dateFilterMode = Objects.requireNonNull(builder.dateFilterMode);
        customFrom = builder.customFrom;
        customTo = builder.customTo;
        defaultPreset = builder.defaultPreset;
        updatedAtEpochMillis = builder.updatedAtEpochMillis;
    }

    public static BackupPreset initialDefault() {
        return builder()
                .setName("Recent photos & videos")
                .setMediaStoreSource(true)
                .setImages(true)
                .setVideos(true)
                .setDocuments(false)
                .setDateFilterMode(DateFilterMode.LAST_7_DAYS)
                .setDefaultPreset(true)
                .setUpdatedAtEpochMillis(System.currentTimeMillis())
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isMediaStoreSource() {
        return mediaStoreSource;
    }

    public String getFolderUri() {
        return folderUri;
    }

    public boolean hasFolderSource() {
        return folderUri != null && !folderUri.trim().isEmpty();
    }

    public boolean includesImages() {
        return images;
    }

    public boolean includesVideos() {
        return videos;
    }

    public boolean includesDocuments() {
        return documents;
    }

    public DateFilterMode getDateFilterMode() {
        return dateFilterMode;
    }

    public LocalDate getCustomFrom() {
        return customFrom;
    }

    public LocalDate getCustomTo() {
        return customTo;
    }

    public boolean isDefaultPreset() {
        return defaultPreset;
    }

    public long getUpdatedAtEpochMillis() {
        return updatedAtEpochMillis;
    }

    public Builder buildUpon() {
        return builder()
                .setId(id)
                .setName(name)
                .setMediaStoreSource(mediaStoreSource)
                .setFolderUri(folderUri)
                .setImages(images)
                .setVideos(videos)
                .setDocuments(documents)
                .setDateFilterMode(dateFilterMode)
                .setCustomFrom(customFrom)
                .setCustomTo(customTo)
                .setDefaultPreset(defaultPreset)
                .setUpdatedAtEpochMillis(updatedAtEpochMillis);
    }

    public static final class Builder {
        private long id;
        private String name = "";
        private boolean mediaStoreSource;
        private String folderUri;
        private boolean images;
        private boolean videos;
        private boolean documents;
        private DateFilterMode dateFilterMode = DateFilterMode.ALL;
        private LocalDate customFrom;
        private LocalDate customTo;
        private boolean defaultPreset;
        private long updatedAtEpochMillis;

        private Builder() {
        }

        public Builder setId(long value) {
            id = value;
            return this;
        }

        public Builder setName(String value) {
            name = value == null ? "" : value.trim();
            return this;
        }

        public Builder setMediaStoreSource(boolean value) {
            mediaStoreSource = value;
            return this;
        }

        public Builder setFolderUri(String value) {
            folderUri = value == null || value.trim().isEmpty() ? null : value;
            return this;
        }

        public Builder setImages(boolean value) {
            images = value;
            return this;
        }

        public Builder setVideos(boolean value) {
            videos = value;
            return this;
        }

        public Builder setDocuments(boolean value) {
            documents = value;
            return this;
        }

        public Builder setDateFilterMode(DateFilterMode value) {
            dateFilterMode = value;
            return this;
        }

        public Builder setCustomFrom(LocalDate value) {
            customFrom = value;
            return this;
        }

        public Builder setCustomTo(LocalDate value) {
            customTo = value;
            return this;
        }

        public Builder setDefaultPreset(boolean value) {
            defaultPreset = value;
            return this;
        }

        public Builder setUpdatedAtEpochMillis(long value) {
            updatedAtEpochMillis = value;
            return this;
        }

        public BackupPreset build() {
            return new BackupPreset(this);
        }
    }
}
