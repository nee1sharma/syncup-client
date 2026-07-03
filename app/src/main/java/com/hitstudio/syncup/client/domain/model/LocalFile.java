package com.hitstudio.syncup.client.domain.model;

import android.net.Uri;
import java.util.Objects;

public final class LocalFile {
    private final String clientFileKey;
    private final Uri uri;
    private final String displayName;
    private final String relativePath;
    private final String mediaType;
    private final String mimeType;
    private final long sizeBytes;
    private final long modifiedAtEpochMillis;
    private final long capturedAtEpochMillis;

    public LocalFile(
            String clientFileKey,
            Uri uri,
            String displayName,
            String relativePath,
            String mediaType,
            String mimeType,
            long sizeBytes,
            long modifiedAtEpochMillis,
            long capturedAtEpochMillis
    ) {
        this.clientFileKey = Objects.requireNonNull(clientFileKey);
        this.uri = Objects.requireNonNull(uri);
        this.displayName = Objects.requireNonNull(displayName);
        this.relativePath = relativePath;
        this.mediaType = mediaType;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.modifiedAtEpochMillis = modifiedAtEpochMillis;
        this.capturedAtEpochMillis = capturedAtEpochMillis;
    }

    public String getClientFileKey() { return clientFileKey; }
    public Uri getUri() { return uri; }
    public String getDisplayName() { return displayName; }
    public String getRelativePath() { return relativePath; }
    public String getMediaType() { return mediaType; }
    public String getMimeType() { return mimeType; }
    public long getSizeBytes() { return sizeBytes; }
    public long getModifiedAtEpochMillis() { return modifiedAtEpochMillis; }
    public long getCapturedAtEpochMillis() { return capturedAtEpochMillis; }
}
