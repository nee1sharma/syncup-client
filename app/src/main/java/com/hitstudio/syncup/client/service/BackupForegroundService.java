package com.hitstudio.syncup.client.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.text.TextUtils;
import android.text.format.Formatter;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.hitstudio.syncup.client.MainActivity;
import com.hitstudio.syncup.client.R;
import com.hitstudio.syncup.client.data.repository.BackupRepository;
import com.hitstudio.syncup.client.domain.model.BackupPreset;
import com.hitstudio.syncup.client.domain.model.DateFilterMode;
import com.hitstudio.syncup.client.network.discovery.ServerInfo;
import com.hitstudio.syncup.client.ui.util.DebugLog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class BackupForegroundService extends Service {

    public static final String ACTION_START_BACKUP = "com.hitstudio.syncup.client.action.START_BACKUP";
    public static final String ACTION_CANCEL_BACKUP = "com.hitstudio.syncup.client.action.CANCEL_BACKUP";
    public static final String ACTION_STATE_CHANGED = "com.hitstudio.syncup.client.action.BACKUP_STATE_CHANGED";

    private static final String EXTRA_PRESET_JSON = "extra_preset_json";
    private static final String EXTRA_SERVER_JSON = "extra_server_json";

    private static final String CHANNEL_PROGRESS_ID = "syncup_backup_progress";
    private static final String CHANNEL_RESULTS_ID = "syncup_backup_results";

    private static final int PROGRESS_NOTIFICATION_ID = 4201;
    private static final int SUCCESS_NOTIFICATION_ID = 4202;
    private static final int FAILURE_NOTIFICATION_ID = 4203;

    private static volatile Snapshot currentSnapshot = Snapshot.idle();

    public enum State {
        IDLE,
        STARTING,
        SCANNING,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public static final class Snapshot {
        private final State state;
        private final int filesSent;
        private final int totalFiles;
        private final String currentFile;
        private final long currentFileSent;
        private final long currentFileTotal;
        private final double speedMbps;
        private final long totalSentBytes;
        private final BackupRepository.BackupResult result;
        private final String message;

        private Snapshot(
                State state,
                int filesSent,
                int totalFiles,
                String currentFile,
                long currentFileSent,
                long currentFileTotal,
                double speedMbps,
                long totalSentBytes,
                BackupRepository.BackupResult result,
                String message
        ) {
            this.state = state;
            this.filesSent = filesSent;
            this.totalFiles = totalFiles;
            this.currentFile = currentFile;
            this.currentFileSent = currentFileSent;
            this.currentFileTotal = currentFileTotal;
            this.speedMbps = speedMbps;
            this.totalSentBytes = totalSentBytes;
            this.result = result;
            this.message = message;
        }

        public static Snapshot idle() {
            return new Snapshot(State.IDLE, 0, 0, null, 0, 0, 0, 0, null, null);
        }

        public static Snapshot starting() {
            return new Snapshot(State.STARTING, 0, 0, null, 0, 0, 0, 0, null, null);
        }

        public static Snapshot scanning() {
            return new Snapshot(State.SCANNING, 0, 0, null, 0, 0, 0, 0, null, null);
        }

        public static Snapshot progress(
                int filesSent,
                int totalFiles,
                String currentFile,
                long currentFileSent,
                long currentFileTotal,
                double speedMbps,
                long totalSentBytes
        ) {
            return new Snapshot(
                    State.RUNNING,
                    filesSent,
                    totalFiles,
                    currentFile,
                    currentFileSent,
                    currentFileTotal,
                    speedMbps,
                    totalSentBytes,
                    null,
                    null
            );
        }

        public static Snapshot completed(BackupRepository.BackupResult result) {
            return new Snapshot(
                    State.COMPLETED,
                    0,
                    0,
                    null,
                    0,
                    0,
                    0,
                    0,
                    result,
                    null
            );
        }

        public static Snapshot failed(String message) {
            return new Snapshot(State.FAILED, 0, 0, null, 0, 0, 0, 0, null, message);
        }

        public static Snapshot cancelled() {
            return new Snapshot(State.CANCELLED, 0, 0, null, 0, 0, 0, 0, null, null);
        }

        public State getState() {
            return state;
        }

        public int getFilesSent() {
            return filesSent;
        }

        public int getTotalFiles() {
            return totalFiles;
        }

        public String getCurrentFile() {
            return currentFile;
        }

        public long getCurrentFileSent() {
            return currentFileSent;
        }

        public long getCurrentFileTotal() {
            return currentFileTotal;
        }

        public double getSpeedMbps() {
            return speedMbps;
        }

        public long getTotalSentBytes() {
            return totalSentBytes;
        }

        public BackupRepository.BackupResult getResult() {
            return result;
        }

        public String getMessage() {
            return message;
        }

        public boolean isActive() {
            return state == State.STARTING || state == State.SCANNING || state == State.RUNNING;
        }
    }

    public static Snapshot getCurrentSnapshot() {
        return currentSnapshot;
    }

    public static void startBackup(Context context, BackupPreset preset, ServerInfo server) {
        Intent intent = new Intent(context, BackupForegroundService.class)
                .setAction(ACTION_START_BACKUP)
                .putExtra(EXTRA_PRESET_JSON, encodePreset(preset))
                .putExtra(EXTRA_SERVER_JSON, encodeServer(server));
        ContextCompat.startForegroundService(context, intent);
    }

    public static void cancelBackup(Context context) {
        Intent intent = new Intent(context, BackupForegroundService.class)
                .setAction(ACTION_CANCEL_BACKUP);
        context.startService(intent);
    }

    private NotificationManager notificationManager;
    private BackupRepository backupRepository;
    private PowerManager.WakeLock wakeLock;
    private boolean sessionActive;
    private boolean foregroundStarted;

    @Override
    public void onCreate() {
        super.onCreate();
        backupRepository = BackupRepository.getInstance(getApplicationContext());
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        if (ACTION_CANCEL_BACKUP.equals(action)) {
            requestCancellation();
            return START_NOT_STICKY;
        }

        if (!ACTION_START_BACKUP.equals(action)) {
            return START_NOT_STICKY;
        }

        if (sessionActive) {
            DebugLog.w(getApplicationContext(), "Backup already running; ignoring duplicate start request");
            return START_NOT_STICKY;
        }

        BackupPreset preset = decodePreset(intent.getStringExtra(EXTRA_PRESET_JSON));
        ServerInfo server = decodeServer(intent.getStringExtra(EXTRA_SERVER_JSON));
        if (preset == null || server == null) {
            DebugLog.e(getApplicationContext(), "Backup start request was missing preset or server details", new IllegalArgumentException());
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        sessionActive = true;
        acquireWakeLock();
        updateSnapshot(Snapshot.starting());
        startForegroundNotification(Snapshot.starting());
        startBackupWork(preset, server);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (sessionActive) {
            backupRepository.cancel();
        }
        sessionActive = false;
        foregroundStarted = false;
        releaseWakeLock();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startBackupWork(BackupPreset preset, ServerInfo server) {
        backupRepository.startBackup(preset, server, new BackupRepository.Callback() {
            @Override
            public void onScanningStarted() {
                if (!sessionActive) {
                    return;
                }
                updateSnapshot(Snapshot.scanning());
            }

            @Override
            public void onProgress(
                    int filesSent,
                    int totalFiles,
                    String currentFile,
                    long currentFileSent,
                    long currentFileTotal,
                    double speedMbps,
                    long totalSentBytes
            ) {
                if (!sessionActive) {
                    return;
                }
                updateSnapshot(Snapshot.progress(
                        filesSent,
                        totalFiles,
                        currentFile,
                        currentFileSent,
                        currentFileTotal,
                        speedMbps,
                        totalSentBytes
                ));
            }

            @Override
            public void onComplete(BackupRepository.BackupResult result) {
                if (!sessionActive) {
                    return;
                }
                sessionActive = false;
                updateSnapshot(Snapshot.completed(result));
                showSuccessNotification(result);
                finishForeground();
                stopSelf();
            }

            @Override
            public void onError(Throwable error) {
                if (!sessionActive) {
                    return;
                }
                sessionActive = false;
                String message = error == null ? "Unknown error" : error.getMessage();
                if (TextUtils.isEmpty(message)) {
                    message = error == null ? "Unknown error" : error.getClass().getSimpleName();
                }
                updateSnapshot(Snapshot.failed(message));
                showFailureNotification(message);
                finishForeground();
                stopSelf();
            }

            @Override
            public void onCancelled() {
                if (!sessionActive) {
                    return;
                }
                sessionActive = false;
                updateSnapshot(Snapshot.cancelled());
                finishForeground();
                stopSelf();
            }
        });
    }

    private void requestCancellation() {
        if (!sessionActive) {
            stopSelf();
            return;
        }
        backupRepository.cancel();
    }

    private void startForegroundNotification(Snapshot snapshot) {
        Notification notification = buildProgressNotification(snapshot);
        startForeground(
                PROGRESS_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        );
        foregroundStarted = true;
    }

    private void finishForeground() {
        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            foregroundStarted = false;
        }
        if (notificationManager != null) {
            notificationManager.cancel(PROGRESS_NOTIFICATION_ID);
        }
        releaseWakeLock();
    }

    private void updateSnapshot(Snapshot snapshot) {
        currentSnapshot = snapshot;
        broadcastStateChanged();
        if (foregroundStarted && snapshot.isActive()) {
            notificationManager.notify(PROGRESS_NOTIFICATION_ID, buildProgressNotification(snapshot));
        }
    }

    private void broadcastStateChanged() {
        Intent intent = new Intent(ACTION_STATE_CHANGED);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private void showSuccessNotification(BackupRepository.BackupResult result) {
        if (notificationManager == null || result == null) {
            return;
        }
        String summary = getString(
                R.string.backup_notification_success,
                result.getTotalFiles(),
                Formatter.formatFileSize(this, result.getTotalBytes())
        );
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_RESULTS_ID)
                .setSmallIcon(R.drawable.ic_backup_up)
                .setContentTitle(getString(R.string.backup_summary_title))
                .setContentText(summary)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(summary + "\n" + getString(R.string.backup_summary_duration, formatDuration(result.getDurationMillis()))))
                .setContentIntent(openAppPendingIntent())
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();
        notificationManager.notify(SUCCESS_NOTIFICATION_ID, notification);
    }

    private void showFailureNotification(String message) {
        if (notificationManager == null) {
            return;
        }
        String summary = getString(R.string.backup_notification_failed, message);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_RESULTS_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(getString(R.string.backup_failed, message))
                .setContentText(summary)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(summary))
                .setContentIntent(openAppPendingIntent())
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();
        notificationManager.notify(FAILURE_NOTIFICATION_ID, notification);
    }

    private Notification buildProgressNotification(Snapshot snapshot) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_PROGRESS_ID)
                .setSmallIcon(R.drawable.ic_backup_up)
                .setContentIntent(openAppPendingIntent())
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        getString(R.string.backup_notification_cancel_action),
                        cancelPendingIntent()
                );

        if (snapshot.getState() == State.STARTING) {
            builder.setContentTitle(getString(R.string.backup_progress_running))
                    .setContentText(getString(R.string.backup_notification_preparing))
                    .setProgress(0, 0, true);
            return builder.build();
        }

        if (snapshot.getState() == State.SCANNING) {
            builder.setContentTitle(getString(R.string.backup_progress_running))
                    .setContentText(getString(R.string.backup_notification_scanning))
                    .setProgress(0, 0, true);
            return builder.build();
        }

        String currentFile = snapshot.getCurrentFile();
        String fileProgress = getString(
                R.string.backup_notification_running_progress,
                snapshot.getFilesSent(),
                snapshot.getTotalFiles(),
                Formatter.formatFileSize(this, snapshot.getCurrentFileSent()),
                Formatter.formatFileSize(this, snapshot.getCurrentFileTotal())
        );
        if (TextUtils.isEmpty(currentFile)) {
            currentFile = getString(R.string.backup_notification_scanning);
        }
        builder.setContentTitle(getString(R.string.backup_progress_running))
                .setContentText(getString(R.string.backup_notification_running_file, currentFile))
                .setSubText(fileProgress);

        if (snapshot.getTotalFiles() > 0) {
            builder.setProgress(snapshot.getTotalFiles(), snapshot.getFilesSent(), false);
        } else {
            builder.setProgress(0, 0, true);
        }

        return builder.build();
    }

    private PendingIntent openAppPendingIntent() {
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(
                this,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private PendingIntent cancelPendingIntent() {
        Intent intent = new Intent(this, BackupForegroundService.class).setAction(ACTION_CANCEL_BACKUP);
        return PendingIntent.getService(
                this,
                1002,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void createNotificationChannels() {
        if (notificationManager == null) {
            return;
        }
        NotificationChannel progressChannel = new NotificationChannel(
                CHANNEL_PROGRESS_ID,
                getString(R.string.backup_notification_channel_progress),
                NotificationManager.IMPORTANCE_LOW
        );
        progressChannel.setDescription(getString(R.string.backup_notification_channel_progress));
        NotificationChannel resultsChannel = new NotificationChannel(
                CHANNEL_RESULTS_ID,
                getString(R.string.backup_notification_channel_results),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        resultsChannel.setDescription(getString(R.string.backup_notification_channel_results));
        notificationManager.createNotificationChannels(Arrays.asList(progressChannel, resultsChannel));
    }

    private void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            return;
        }
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager == null) {
            return;
        }
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SyncUp:BackupWakeLock");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire();
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        wakeLock = null;
    }

    private static String encodePreset(BackupPreset preset) {
        try {
            JSONObject json = new JSONObject()
                    .put("id", preset.getId())
                    .put("name", preset.getName())
                    .put("mediaStoreSource", preset.isMediaStoreSource())
                    .put("folderUri", preset.getFolderUri())
                    .put("images", preset.includesImages())
                    .put("videos", preset.includesVideos())
                    .put("documents", preset.includesDocuments())
                    .put("dateFilterMode", preset.getDateFilterMode().name())
                    .put("customFrom", preset.getCustomFrom() == null ? JSONObject.NULL : preset.getCustomFrom().toString())
                    .put("customTo", preset.getCustomTo() == null ? JSONObject.NULL : preset.getCustomTo().toString())
                    .put("defaultPreset", preset.isDefaultPreset())
                    .put("updatedAtEpochMillis", preset.getUpdatedAtEpochMillis());
            return json.toString();
        } catch (JSONException error) {
            throw new IllegalArgumentException("Could not encode backup preset", error);
        }
    }

    private static String encodeServer(ServerInfo server) {
        try {
            JSONArray capabilities = new JSONArray();
            for (String capability : server.getCapabilities()) {
                capabilities.put(capability);
            }
            JSONObject json = new JSONObject()
                    .put("serverId", server.getServerId().toString())
                    .put("serverName", server.getServerName())
                    .put("applicationVersion", server.getApplicationVersion() == null ? JSONObject.NULL : server.getApplicationVersion())
                    .put("baseUrl", server.getBaseUrl())
                    .put("capabilities", capabilities);
            return json.toString();
        } catch (JSONException error) {
            throw new IllegalArgumentException("Could not encode server info", error);
        }
    }

    private static BackupPreset decodePreset(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(jsonString);
            BackupPreset.Builder builder = BackupPreset.builder()
                    .setId(json.optLong("id", 0))
                    .setName(json.optString("name", ""))
                    .setMediaStoreSource(json.optBoolean("mediaStoreSource", false))
                    .setFolderUri(json.isNull("folderUri") ? null : json.optString("folderUri", null))
                    .setImages(json.optBoolean("images", false))
                    .setVideos(json.optBoolean("videos", false))
                    .setDocuments(json.optBoolean("documents", false))
                    .setDateFilterMode(DateFilterMode.valueOf(json.optString("dateFilterMode", DateFilterMode.ALL.name())))
                    .setDefaultPreset(json.optBoolean("defaultPreset", false))
                    .setUpdatedAtEpochMillis(json.optLong("updatedAtEpochMillis", System.currentTimeMillis()));
            if (!json.isNull("customFrom")) {
                String customFrom = json.optString("customFrom", null);
                if (customFrom != null && !customFrom.trim().isEmpty()) {
                    builder.setCustomFrom(LocalDate.parse(customFrom));
                }
            }
            if (!json.isNull("customTo")) {
                String customTo = json.optString("customTo", null);
                if (customTo != null && !customTo.trim().isEmpty()) {
                    builder.setCustomTo(LocalDate.parse(customTo));
                }
            }
            return builder.build();
        } catch (Exception error) {
            Log.e("SyncUp", "Failed to decode backup preset", error);
            return null;
        }
    }

    private static ServerInfo decodeServer(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(jsonString);
            List<String> capabilities = new ArrayList<>();
            JSONArray capabilityArray = json.optJSONArray("capabilities");
            if (capabilityArray != null) {
                for (int i = 0; i < capabilityArray.length(); i++) {
                    capabilities.add(capabilityArray.optString(i));
                }
            }
            String applicationVersion = json.isNull("applicationVersion")
                    ? null
                    : json.optString("applicationVersion", null);
            return new ServerInfo(
                    UUID.fromString(json.getString("serverId")),
                    json.getString("serverName"),
                    applicationVersion,
                    json.getString("baseUrl"),
                    capabilities
            );
        } catch (Exception error) {
            Log.e("SyncUp", "Failed to decode server info", error);
            return null;
        }
    }

    private static String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds %= 60;
        if (minutes > 0) {
            return String.format(Locale.getDefault(), "%dm %ds", minutes, seconds);
        }
        return String.format(Locale.getDefault(), "%ds", seconds);
    }
}
