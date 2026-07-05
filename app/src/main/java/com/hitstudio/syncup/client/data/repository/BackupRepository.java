package com.hitstudio.syncup.client.data.repository;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import com.hitstudio.syncup.client.data.local.DeviceSettings;
import com.hitstudio.syncup.client.domain.model.BackupPreset;
import com.hitstudio.syncup.client.domain.model.DateRange;
import com.hitstudio.syncup.client.domain.model.LocalFile;
import com.hitstudio.syncup.client.domain.usecase.DateRangeCalculator;
import com.hitstudio.syncup.client.network.discovery.ServerInfo;
import com.hitstudio.syncup.client.ui.util.DebugLog;
import com.hitstudio.syncup.client.ui.util.Sha256;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

public final class BackupRepository {

    private static final String[] MANIFEST_BATCH_PATHS = {
            "manifest-batches",
            "manifest-batch",
            "manifest"
    };

    public interface Callback {
        void onScanningStarted();
        void onProgress(int filesSent, int totalFiles, String currentFile, long currentFileSent, long currentFileTotal, double speedMbps, long totalSentBytes);
        void onComplete(BackupResult result);
        void onError(Throwable error);
        void onCancelled();
    }

    public static final class BackupResult {
        private final int totalFiles;
        private final long totalBytes;
        private final long durationMillis;
        private final List<String> fileNames;

        public BackupResult(int totalFiles, long totalBytes, long durationMillis, List<String> fileNames) {
            this.totalFiles = totalFiles;
            this.totalBytes = totalBytes;
            this.durationMillis = durationMillis;
            this.fileNames = fileNames;
        }

        public int getTotalFiles() { return totalFiles; }
        public long getTotalBytes() { return totalBytes; }
        public long getDurationMillis() { return durationMillis; }
        public List<String> getFileNames() { return fileNames; }
    }

    private static volatile BackupRepository instance;
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);
    private final DeviceSettings deviceSettings;
    private final OkHttpClient httpClient;

    private BackupRepository(Context context) {
        this.context = context.getApplicationContext();
        this.deviceSettings = new DeviceSettings(this.context);
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(0, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public static BackupRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (BackupRepository.class) {
                if (instance == null) {
                    instance = new BackupRepository(context);
                }
            }
        }
        return instance;
    }

    public void startBackup(BackupPreset preset, ServerInfo server, Callback callback) {
        isCancelled.set(false);
        DebugLog.i(context, "Backup requested for server " + server.getBaseUrl());
        executor.execute(() -> {
            try {
                postScanningStarted(callback);
                List<LocalFile> files = scanFiles(preset);
                DebugLog.i(context, "Scan completed with " + files.size() + " candidate files");
                
                if (isCancelled.get()) {
                    postCancelled(callback);
                    return;
                }

                if (files.isEmpty()) {
                    postComplete(callback, new BackupResult(0, 0, 0, new ArrayList<>()));
                    return;
                }

                performBackup(files, server, callback);
            } catch (Exception e) {
                if (isCancelled.get()) {
                    postCancelled(callback);
                } else {
                    postError(callback, e);
                }
            }
        });
    }

    private void performBackup(List<LocalFile> files, ServerInfo server, Callback callback) throws IOException, JSONException {
        long backupStartTime = System.currentTimeMillis();
        String baseUrl = server.getBaseUrl();
        String deviceId = deviceSettings.getDeviceId();
        String deviceName = android.os.Build.MODEL;
        String idempotencyKey = UUID.randomUUID().toString();

        // 1. Create a backup run
        JSONObject runRequest = new JSONObject()
                .put("deviceId", deviceId)
                .put("deviceName", deviceName)
                .put("idempotencyKey", idempotencyKey);

        HttpUrl backupsUrl = HttpUrl.get(baseUrl).newBuilder()
                .addPathSegment("backups")
                .build();

        DebugLog.i(context, "Starting backup run against " + backupsUrl + " for device " + deviceName);

        Request postRun = new Request.Builder()
                .url(backupsUrl)
                .header("Idempotency-Key", idempotencyKey)
                .post(RequestBody.create(runRequest.toString(), MediaType.parse("application/json")))
                .build();

        String runId;
        try (Response response = httpClient.newCall(postRun).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            DebugLog.d(context, "Create run response " + response.code() + " from " + backupsUrl + ": " + body);
            if (!response.isSuccessful()) {
                throw new IOException("Failed to create backup run (" + response.code() + "): " + body);
            }
            JSONObject json = new JSONObject(body);
            runId = json.getString("runId");
        }

        // 2. Submit manifest
        JSONArray manifest = new JSONArray();
        Map<String, LocalFile> fileMap = new HashMap<>();

        for (LocalFile file : files) {
            if (isCancelled.get()) {
                postCancelled(callback);
                return;
            }

            String sha256;
            try (InputStream is = context.getContentResolver().openInputStream(file.getUri())) {
                sha256 = Sha256.calculate(is, isCancelled::get);
            }

            JSONObject entry = new JSONObject()
                    .put("clientFileKey", file.getClientFileKey())
                    .put("displayName", file.getDisplayName())
                    .put("sizeBytes", file.getSizeBytes())
                    .put("modifiedAt", Instant.ofEpochMilli(file.getModifiedAtEpochMillis()).toString())
                    .put("capturedAt", Instant.ofEpochMilli(file.getCapturedAtEpochMillis()).toString())
                    .put("sha256", sha256);
            putIfPresent(entry, "relativePath", file.getRelativePath());
            putIfPresent(entry, "mediaType", file.getMediaType());
            putIfPresent(entry, "mimeType", file.getMimeType());

            manifest.put(entry);
            fileMap.put(file.getClientFileKey(), file);
        }

        JSONObject batchRequest = new JSONObject()
                .put("deviceId", deviceId)
                .put("deviceName", deviceName)
                .put("files", manifest);
        DebugLog.d(context, "Manifest payload: " + batchRequest.toString());
        JSONArray planEntries = submitManifestBatch(baseUrl, runId, deviceId, batchRequest);

        // 3. Upload missing files
        List<JSONObject> uploads = new ArrayList<>();
        List<String> allFilesProcessed = new ArrayList<>();
        long totalSentBytes = 0;

        for (int i = 0; i < planEntries.length(); i++) {
            JSONObject entry = planEntries.getJSONObject(i);
            String disposition = entry.getString("disposition");
            String clientFileKey = entry.getString("clientFileKey");
            LocalFile file = fileMap.get(clientFileKey);
            if (file != null) {
                allFilesProcessed.add(file.getDisplayName());
            }

            if ("UPLOAD".equals(disposition) || "RESUME".equals(disposition)) {
                uploads.add(entry);
            } else if ("PRESENT".equals(disposition)) {
                if (file != null) {
                    totalSentBytes += file.getSizeBytes();
                }
            }
        }
        DebugLog.i(context, "Manifest plan: " + uploads.size() + " uploads");

        int totalUploadsCount = uploads.size();
        long sessionSentBytes = 0;

        for (int i = 0; i < uploads.size(); i++) {
            if (isCancelled.get()) {
                postCancelled(callback);
                return;
            }

            JSONObject entry = uploads.get(i);
            String clientFileKey = entry.getString("clientFileKey");
            String transferId = entry.getString("transferId");
            long offset = entry.optLong("uploadOffset", 0);
            LocalFile file = fileMap.get(clientFileKey);

            if (file != null) {
                uploadFile(file, transferId, deviceName, runId, baseUrl, i, totalUploadsCount, sessionSentBytes, offset, callback);
                sessionSentBytes += (file.getSizeBytes() - offset);
            }
        }

        // 4. Complete
        HttpUrl completeUrl = HttpUrl.get(baseUrl).newBuilder()
                .addPathSegment("backups")
                .addPathSegment(runId)
                .addPathSegment("complete")
                .build();

        JSONObject completeRequest = new JSONObject()
                .put("deviceId", deviceId)
                .put("deviceName", deviceName);
        Request postComplete = new Request.Builder()
                .url(completeUrl)
                .post(RequestBody.create(completeRequest.toString(), MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(postComplete).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error body";
                throw new IOException("Failed to complete backup: " + response.code() + " | Response: " + errorBody);
            }
        }

        long duration = System.currentTimeMillis() - backupStartTime;
        postComplete(callback, new BackupResult(allFilesProcessed.size(), sessionSentBytes + (totalSentBytes), duration, allFilesProcessed));
    }

    private void uploadFile(LocalFile file, String transferId, String deviceName, String runId, String baseUrl, int index, int totalFiles, long totalSentSoFar, long startOffset, Callback callback) throws IOException {
        long startTime = System.currentTimeMillis();
        if (startOffset < 0 || startOffset > file.getSizeBytes()) {
            throw new IOException("Invalid upload offset for " + file.getDisplayName() + ": " + startOffset);
        }
        
        HttpUrl contentUrl = HttpUrl.get(baseUrl).newBuilder()
                .addPathSegment("transfers")
                .addPathSegment(transferId)
                .addPathSegment("content")
                .build();

        Request putContent = new Request.Builder()
                .url(contentUrl)
                .put(new ProgressRequestBody(
                        context,
                        file.getUri(),
                        file.getSizeBytes(),
                        startOffset,
                        isCancelled::get,
                        (sent, total) -> {
                            long now = System.currentTimeMillis();
                            double durationSec = (now - startTime) / 1000.0;
                            double speedMbps = durationSec > 0 ? (sent * 8.0 / (1024 * 1024)) / durationSec : 0;
                            postProgress(callback, index + 1, totalFiles, file.getDisplayName(), sent, total, speedMbps, totalSentSoFar + sent);
                        }))
                .header("Upload-Offset", String.valueOf(startOffset))
                .header("X-SyncUp-Device-Id", deviceSettings.getDeviceId())
                .header("X-SyncUp-Device-Name", deviceName)
                .header("X-SyncUp-Run-Id", runId)
                .build();

        try (Response response = httpClient.newCall(putContent).execute()) {
            if (!response.isSuccessful()) throw new IOException("Failed to upload " + file.getDisplayName() + ": " + response.code());
        }
    }

    private JSONArray submitManifestBatch(String baseUrl, String runId, String deviceId, JSONObject batchRequest)
            throws IOException, JSONException {
        IOException lastError = null;
        DebugLog.d(context, "Submitting manifest batch with " + batchRequest.optJSONArray("files").length() + " files");

        for (String manifestPath : MANIFEST_BATCH_PATHS) {
            HttpUrl manifestUrl = HttpUrl.get(baseUrl).newBuilder()
                    .addPathSegment("backups")
                    .addPathSegment(runId)
                    .addPathSegment(manifestPath)
                    .build();

            Request postBatch = new Request.Builder()
                    .url(manifestUrl)
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .header("Device-ID", deviceId)
                    .post(RequestBody.create(batchRequest.toString(), MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(postBatch).execute()) {
                if (response.isSuccessful()) {
                    String body = response.body() != null ? response.body().string() : "";
                    DebugLog.i(context, "Manifest submitted successfully to " + manifestUrl);
                    JSONObject json = new JSONObject(body);
                    return json.getJSONArray("files");
                }

                String errorBody = "No error body";
                if (response.body() != null) {
                    errorBody = response.body().string();
                }
                DebugLog.w(context, "Manifest submission failed with " + response.code() + " at " + manifestUrl + ": " + errorBody);

                if (response.code() == 404) {
                    lastError = new IOException(
                            "Manifest endpoint not found at " + manifestUrl + " | Response: " + errorBody
                    );
                    continue;
                }

                throw new IOException(
                        "Failed to submit manifest (" + response.code() + ") at URL: " + manifestUrl
                                + " | Response: " + errorBody
                );
            }
        }

        if (lastError != null) {
            DebugLog.e(context, "Manifest submission exhausted all fallback endpoints", lastError);
            throw lastError;
        }

        throw new IOException("Failed to submit manifest: no manifest endpoint was available");
    }

    private static void putIfPresent(JSONObject target, String key, String value) throws JSONException {
        if (value != null && !value.trim().isEmpty()) {
            target.put(key, value);
        }
    }

    public void cancel() {
        isCancelled.set(true);
    }

    private List<LocalFile> scanFiles(BackupPreset preset) {
        List<LocalFile> allFiles = new ArrayList<>();
        String folderPrefix = normalizeFolderPrefix(preset.getFolderUri());
        DateRange dateRange = DateRangeCalculator.calculate(
                preset.getDateFilterMode(),
                preset.getCustomFrom(),
                preset.getCustomTo(),
                ZoneId.systemDefault(),
                Clock.systemDefaultZone()
        );

        DebugLog.d(context, "Scanning media store with folder prefix " + (folderPrefix == null ? "<all>" : folderPrefix));

        if (preset.isMediaStoreSource()) {
            if (preset.includesImages()) {
                allFiles.addAll(queryMediaStore(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        "IMAGE",
                        dateRange,
                        folderPrefix
                ));
            }
            if (preset.includesVideos()) {
                allFiles.addAll(queryMediaStore(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        "VIDEO",
                        dateRange,
                        folderPrefix
                ));
            }
        }
        
        return allFiles;
    }

    private List<LocalFile> queryMediaStore(Uri collection, String mediaType, DateRange dateRange, String folderPrefix) {
        List<LocalFile> files = new ArrayList<>();
        String[] projection = new String[]{
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.DATE_ADDED
        };

        String selection = null;
        List<String> selectionArgs = new ArrayList<>();

        if (dateRange != null) {
            selection = MediaStore.MediaColumns.DATE_ADDED + " >= ? AND " + MediaStore.MediaColumns.DATE_ADDED + " < ?";
            selectionArgs.add(String.valueOf(dateRange.getFromInclusive().getEpochSecond()));
            selectionArgs.add(String.valueOf(dateRange.getToExclusive().getEpochSecond()));
        }

        if (folderPrefix != null) {
            if (selection == null) {
                selection = MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
            } else {
                selection += " AND " + MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
            }
            selectionArgs.add(folderPrefix + "%");
        }

        try (Cursor cursor = context.getContentResolver().query(
                collection,
                projection,
                selection,
                selectionArgs.isEmpty() ? null : selectionArgs.toArray(new String[0]),
                MediaStore.MediaColumns.DATE_ADDED + " DESC"
        )) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
                int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
                int pathColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH);
                int mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE);
                int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE);
                int modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED);
                int addedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    Uri contentUri = ContentUris.withAppendedId(collection, id);
                    String name = cursor.getString(nameColumn);
                    String path = cursor.getString(pathColumn);
                    String mime = cursor.getString(mimeColumn);
                    long size = cursor.getLong(sizeColumn);
                    long modified = cursor.getLong(modifiedColumn) * 1000;
                    long added = cursor.getLong(addedColumn) * 1000;

                    String key = mediaType + "_" + id;

                    files.add(new LocalFile(key, contentUri, name, path, mediaType, mime, size, modified, added));
                }
            }
        }
        return files;
    }

    private String normalizeFolderPrefix(String folderUri) {
        if (folderUri == null || folderUri.trim().isEmpty()) {
            return null;
        }
        try {
            Uri uri = Uri.parse(folderUri);
            String treeId = DocumentsContract.getTreeDocumentId(uri);
            if (treeId == null || treeId.trim().isEmpty()) {
                return null;
            }
            String decoded = Uri.decode(treeId);
            int colon = decoded.indexOf(':');
            String path = colon >= 0 ? decoded.substring(colon + 1) : decoded;
            path = path.replace('\\', '/').trim();
            if (path.isEmpty()) {
                return null;
            }
            if (!path.endsWith("/")) {
                path += "/";
            }
            return path;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void postScanningStarted(Callback callback) {
        mainHandler.post(callback::onScanningStarted);
    }

    private void postProgress(Callback callback, int filesSent, int totalFiles, String currentFile, long currentFileSent, long currentFileTotal, double speedMbps, long totalSentBytes) {
        mainHandler.post(() -> callback.onProgress(filesSent, totalFiles, currentFile, currentFileSent, currentFileTotal, speedMbps, totalSentBytes));
    }

    private void postComplete(Callback callback, BackupResult result) {
        mainHandler.post(() -> callback.onComplete(result));
    }

    private void postError(Callback callback, Throwable error) {
        mainHandler.post(() -> callback.onError(error));
    }

    private void postCancelled(Callback callback) {
        mainHandler.post(callback::onCancelled);
    }

    private static final class ProgressRequestBody extends RequestBody {
        private final Context context;
        private final Uri uri;
        private final long size;
        private final long startOffset;
        private final java.util.function.BooleanSupplier isCancelled;
        private final BiConsumer<Long, Long> listener;

        ProgressRequestBody(
                Context context,
                Uri uri,
                long size,
                long startOffset,
                java.util.function.BooleanSupplier isCancelled,
                BiConsumer<Long, Long> listener
        ) {
            this.context = context;
            this.uri = uri;
            this.size = size;
            this.startOffset = startOffset;
            this.isCancelled = isCancelled;
            this.listener = listener;
        }

        @Override
        public MediaType contentType() {
            return MediaType.parse("application/octet-stream");
        }

        @Override
        public long contentLength() {
            if (startOffset > size) {
                return 0;
            }
            return size - startOffset;
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            try (InputStream is = context.getContentResolver().openInputStream(uri)) {
                if (is == null) throw new IOException("Could not open input stream for " + uri);
                if (startOffset > 0) {
                    long remaining = startOffset;
                    while (remaining > 0) {
                        if (isCancelled != null && isCancelled.getAsBoolean()) {
                            throw new IOException("Backup cancelled");
                        }
                        long skipped = is.skip(remaining);
                        if (skipped <= 0) {
                            if (is.read() == -1) {
                                throw new IOException("Could not skip " + startOffset + " bytes");
                            }
                            skipped = 1;
                        }
                        remaining -= skipped;
                    }
                }
                byte[] buffer = new byte[8192];
                int read;
                long sent = startOffset;
                long lastReportAt = System.currentTimeMillis();
                long lastReportedSent = startOffset;
                while ((read = is.read(buffer)) != -1) {
                    if (isCancelled != null && isCancelled.getAsBoolean()) {
                        throw new IOException("Backup cancelled");
                    }
                    sink.write(buffer, 0, read);
                    sent += read;
                    long now = System.currentTimeMillis();
                    if (now - lastReportAt >= 250 || sent == size) {
                        lastReportAt = now;
                        lastReportedSent = sent;
                        listener.accept(sent, size);
                    }
                }
                if (sent != lastReportedSent) {
                    listener.accept(sent, size);
                }
            }
        }
    }
}
