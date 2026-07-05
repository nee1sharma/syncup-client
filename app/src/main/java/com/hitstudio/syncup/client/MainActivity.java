package com.hitstudio.syncup.client;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.hitstudio.syncup.client.data.repository.BackupRepository;
import com.hitstudio.syncup.client.data.repository.PresetRepository;
import com.hitstudio.syncup.client.domain.model.BackupPreset;
import com.hitstudio.syncup.client.network.discovery.ServerDiscoveryRepository;
import com.hitstudio.syncup.client.network.discovery.ServerInfo;
import com.hitstudio.syncup.client.service.BackupForegroundService;
import com.hitstudio.syncup.client.ui.config.BackupConfigActivity;
import com.hitstudio.syncup.client.ui.util.PresetSummaryFormatter;

public class MainActivity extends AppCompatActivity {

    private ProgressBar presetLoading;
    private TextView presetName;
    private View presetDetailsContainer;
    private TextView presetSummary;
    private MaterialButton configureButton;
    private MaterialButton backupButton;
    private TextView serverStatus;
    private View serverDetailsContainer;
    private TextView serverStatusExplanation;
    private TextView serverVersionTop;
    private LinearLayout serverActions;
    private PresetRepository presetRepository;
    private ServerDiscoveryRepository serverDiscoveryRepository;
    private BackupRepository backupRepository;

    private View backupProgressCard;
    private View backupRunningLayout;
    private View backupSuccessLayout;
    private TextView backupProgressTitle;
    private LinearProgressIndicator backupOverallProgress;
    private TextView backupFilesCount;
    private TextView currentFileName;
    private TextView currentFileSize;
    private TextView uploadSpeed;
    private TextView totalSentSize;
    private MaterialButton cancelBackupButton;

    private TextView backupSummaryFiles;
    private TextView backupSummaryDuration;
    private TextView backupSummarySize;
    private MaterialButton viewDetailsButton;
    private List<String> backedUpFiles = new ArrayList<>();

    private BackupPreset currentPreset;
    private ServerInfo currentServer;
    private boolean isPresetLoaded = false;
    private boolean isServerConnected = false;
    private boolean isBackupReceiverRegistered = false;

    private final BroadcastReceiver backupStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!canRender()) {
                return;
            }
            BackupForegroundService.Snapshot snapshot = BackupForegroundService.getCurrentSnapshot();
            renderBackupSnapshot(snapshot);
            if (snapshot == null) {
                return;
            }
            if (snapshot.getState() == BackupForegroundService.State.COMPLETED) {
                Snackbar.make(findViewById(R.id.main), R.string.backup_complete, Snackbar.LENGTH_LONG).show();
            } else if (snapshot.getState() == BackupForegroundService.State.FAILED && snapshot.getMessage() != null) {
                Snackbar.make(
                        findViewById(R.id.main),
                        getString(R.string.backup_failed, snapshot.getMessage()),
                        Snackbar.LENGTH_LONG
                ).show();
            } else if (snapshot.getState() == BackupForegroundService.State.CANCELLED) {
                Snackbar.make(findViewById(R.id.main), R.string.backup_cancelled, Snackbar.LENGTH_SHORT).show();
            }
        }
    };

    private final ActivityResultLauncher<Intent> configLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> loadDefaultPreset()
            );

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (allPermissionsGranted(result)) {
                    startBackup();
                } else {
                    Snackbar.make(
                            findViewById(R.id.main),
                            R.string.backup_permissions_required,
                            Snackbar.LENGTH_LONG
                    ).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        presetLoading = findViewById(R.id.preset_loading);
        presetName = findViewById(R.id.preset_name);
        presetDetailsContainer = findViewById(R.id.preset_details_container);
        presetSummary = findViewById(R.id.preset_summary);
        configureButton = findViewById(R.id.configure_button);
        backupButton = findViewById(R.id.backup_button);
        serverStatus = findViewById(R.id.server_status);
        serverDetailsContainer = findViewById(R.id.server_details_container);
        serverStatusExplanation = findViewById(R.id.server_status_explanation);
        serverVersionTop = findViewById(R.id.server_version_top);
        serverActions = findViewById(R.id.server_actions);
        backupProgressCard = findViewById(R.id.backup_progress_card);
        backupRunningLayout = findViewById(R.id.backup_running_layout);
        backupSuccessLayout = findViewById(R.id.backup_success_layout);
        backupOverallProgress = findViewById(R.id.backup_overall_progress);
        backupFilesCount = findViewById(R.id.backup_files_count);
        currentFileName = findViewById(R.id.current_file_name);
        currentFileSize = findViewById(R.id.current_file_size);
        uploadSpeed = findViewById(R.id.upload_speed);
        totalSentSize = findViewById(R.id.total_sent_size);
        backupProgressTitle = findViewById(R.id.backup_progress_title);
        cancelBackupButton = findViewById(R.id.cancel_backup_button);
        backupSummaryFiles = findViewById(R.id.backup_summary_files);
        backupSummaryDuration = findViewById(R.id.backup_summary_duration);
        backupSummarySize = findViewById(R.id.backup_summary_size);
        viewDetailsButton = findViewById(R.id.view_details_button);

        presetRepository = PresetRepository.getInstance(this);
        serverDiscoveryRepository = ServerDiscoveryRepository.getInstance(this);
        backupRepository = BackupRepository.getInstance(this);

        findViewById(R.id.server_card).setOnClickListener(view -> {
            if (serverDetailsContainer.getVisibility() == View.VISIBLE) {
                serverDetailsContainer.setVisibility(View.GONE);
            } else {
                serverDetailsContainer.setVisibility(View.VISIBLE);
            }
        });
        findViewById(R.id.preset_card).setOnClickListener(view -> {
            if (presetDetailsContainer.getVisibility() == View.VISIBLE) {
                presetDetailsContainer.setVisibility(View.GONE);
            } else {
                presetDetailsContainer.setVisibility(View.VISIBLE);
            }
        });
        configureButton.setOnClickListener(view -> configLauncher.launch(
                new Intent(this, BackupConfigActivity.class)
        ));
        backupButton.setOnClickListener(view -> checkPermissionsAndStartBackup());
        cancelBackupButton.setOnClickListener(view -> backupRepository.cancel());
        viewDetailsButton.setOnClickListener(view -> showBackupDetailsDialog());
        findViewById(R.id.retry_discovery_button).setOnClickListener(view -> {
            // Clear cache to ensure we get fresh server info (like a new name)
            ServerDiscoveryRepository.getInstance(this).clearCache();
            renderServerSearching();
            ServerDiscoveryRepository.getInstance(this).discover(true, serverCallback(false));
        });
        findViewById(R.id.manual_server_button).setOnClickListener(
                view -> showManualServerDialog()
        );

        renderAppVersion();
        loadCachedServerVersion();
        loadDefaultPreset();
        discoverServer();
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerBackupStateReceiver();
        renderBackupSnapshot(BackupForegroundService.getCurrentSnapshot());
    }

    @Override
    protected void onStop() {
        unregisterBackupStateReceiver();
        super.onStop();
    }

    private void loadCachedServerVersion() {
        ServerInfo cached = serverDiscoveryRepository.getCachedServer();
        if (cached != null && cached.getApplicationVersion() != null) {
            serverVersionTop.setText(getString(R.string.server_version_format, cached.getApplicationVersion()));
            serverVersionTop.setVisibility(View.VISIBLE);
        }
    }

    private void renderAppVersion() {
        TextView versionText = findViewById(R.id.app_version);
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = pInfo.versionName;
            versionText.setText(getString(R.string.app_version_format, version));
        } catch (PackageManager.NameNotFoundException e) {
            versionText.setVisibility(View.GONE);
        }
    }

    private void loadDefaultPreset() {
        renderLoading();
        presetRepository.getOrCreateDefault(new PresetRepository.Callback<BackupPreset>() {
            @Override
            public void onSuccess(BackupPreset preset) {
                if (!canRender()) {
                    return;
                }
                presetLoading.setVisibility(View.GONE);
                presetName.setText(preset.getName());
                presetSummary.setText(PresetSummaryFormatter.format(MainActivity.this, preset));
                presetName.setVisibility(View.VISIBLE);
                presetSummary.setVisibility(View.VISIBLE);
                configureButton.setEnabled(true);
                currentPreset = preset;
                isPresetLoaded = true;
                updateBackupButtonState();
            }

            @Override
            public void onError(Throwable error) {
                if (!canRender()) {
                    return;
                }
                presetLoading.setVisibility(View.GONE);
                presetName.setText(R.string.preset_load_failed);
                presetName.setVisibility(View.VISIBLE);
                presetSummary.setVisibility(View.GONE);
                configureButton.setEnabled(true);
                currentPreset = null;
                isPresetLoaded = false;
                updateBackupButtonState();
            }
        });
    }

    private void renderLoading() {
        presetLoading.setVisibility(View.VISIBLE);
        presetName.setVisibility(View.GONE);
        presetSummary.setVisibility(View.GONE);
        configureButton.setEnabled(false);
    }

    private boolean canRender() {
        return !isFinishing() && !isDestroyed();
    }

    private void discoverServer() {
        renderServerSearching();
        serverDiscoveryRepository.discover(serverCallback(false));
    }

    private ServerDiscoveryRepository.Callback serverCallback(boolean manual) {
        return new ServerDiscoveryRepository.Callback() {
            @Override
            public void onConnected(ServerInfo server) {
                if (canRender()) {
                    renderServerConnected(server);
                }
            }

            @Override
            public void onUnavailable(Throwable error) {
                if (!canRender()) {
                    return;
                }
                renderServerUnavailable();
                if (manual) {
                    Snackbar.make(
                            findViewById(R.id.main),
                            R.string.server_address_invalid,
                            Snackbar.LENGTH_LONG
                    ).show();
                }
            }
        };
    }

    private void renderServerSearching() {
        serverStatus.setText(R.string.server_status_planned);
        serverStatus.setTextColor(getColor(R.color.status_planned_on));
        serverStatus.setBackgroundResource(R.drawable.bg_status_planned);
        serverStatusExplanation.setText(R.string.server_status_explanation);
        serverActions.setVisibility(View.GONE);
        isServerConnected = false;
        updateBackupButtonState();
    }

    private void renderServerConnected(ServerInfo server) {
        serverStatus.setText(R.string.server_status_connected);
        serverStatus.setTextColor(getColor(R.color.status_connected_on));
        serverStatus.setBackgroundResource(R.drawable.bg_status_connected);
        serverStatusExplanation.setText(getString(
                R.string.server_status_connected_to,
                server.getServerName(),
                server.getHost()
        ));
        serverActions.setVisibility(View.GONE);
        if (server.getApplicationVersion() != null) {
            serverVersionTop.setText(getString(R.string.server_version_format, server.getApplicationVersion()));
            serverVersionTop.setVisibility(View.VISIBLE);
        } else {
            serverVersionTop.setVisibility(View.GONE);
        }
        currentServer = server;
        isServerConnected = true;
        updateBackupButtonState();
    }

    private void renderServerUnavailable() {
        serverStatus.setText(R.string.server_status_unavailable);
        serverStatus.setTextColor(getColor(R.color.status_unavailable_on));
        serverStatus.setBackgroundResource(R.drawable.bg_status_unavailable);
        serverStatusExplanation.setText(R.string.server_status_unavailable_explanation);
        serverActions.setVisibility(View.VISIBLE);
        serverDetailsContainer.setVisibility(View.VISIBLE);
        isServerConnected = false;
        updateBackupButtonState();
    }

    private void updateBackupButtonState() {
        backupButton.setEnabled(isPresetLoaded && isServerConnected);
    }

    private void checkPermissionsAndStartBackup() {
        permissionLauncher.launch(requiredBackupPermissions());
    }

    private String[] requiredBackupPermissions() {
        return new String[]{
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO,
                android.Manifest.permission.POST_NOTIFICATIONS
        };
    }

    private void startBackup() {
        if (currentPreset == null || currentServer == null) return;

        renderBackupSnapshot(BackupForegroundService.Snapshot.starting());
        try {
            BackupForegroundService.startBackup(this, currentPreset, currentServer);
        } catch (RuntimeException error) {
            renderBackupSnapshot(BackupForegroundService.Snapshot.idle());
            Snackbar.make(
                    findViewById(R.id.main),
                    getString(R.string.backup_failed, error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()),
                    Snackbar.LENGTH_LONG
            ).show();
        }
    }

    private boolean allPermissionsGranted(Map<String, Boolean> result) {
        for (String permission : requiredBackupPermissions()) {
            if (!Boolean.TRUE.equals(result.get(permission))) {
                return false;
            }
        }
        return true;
    }

    private void registerBackupStateReceiver() {
        if (isBackupReceiverRegistered) {
            return;
        }
        registerReceiver(
                backupStateReceiver,
                new IntentFilter(BackupForegroundService.ACTION_STATE_CHANGED),
                Context.RECEIVER_NOT_EXPORTED
        );
        isBackupReceiverRegistered = true;
    }

    private void unregisterBackupStateReceiver() {
        if (!isBackupReceiverRegistered) {
            return;
        }
        unregisterReceiver(backupStateReceiver);
        isBackupReceiverRegistered = false;
    }

    private void renderBackupSnapshot(BackupForegroundService.Snapshot snapshot) {
        if (snapshot == null) {
            snapshot = BackupForegroundService.Snapshot.idle();
        }

        switch (snapshot.getState()) {
            case STARTING:
                backupButton.setVisibility(View.GONE);
                backupProgressCard.setVisibility(View.VISIBLE);
                backupRunningLayout.setVisibility(View.VISIBLE);
                backupSuccessLayout.setVisibility(View.GONE);
                backupProgressTitle.setText(R.string.backup_status_scanning);
                backupOverallProgress.setIndeterminate(true);
                backupFilesCount.setText("");
                currentFileName.setText("");
                currentFileSize.setText("");
                uploadSpeed.setText("");
                totalSentSize.setText("");
                backedUpFiles.clear();
                break;
            case SCANNING:
                backupButton.setVisibility(View.GONE);
                backupProgressCard.setVisibility(View.VISIBLE);
                backupRunningLayout.setVisibility(View.VISIBLE);
                backupSuccessLayout.setVisibility(View.GONE);
                backupProgressTitle.setText(R.string.backup_status_scanning);
                backupOverallProgress.setIndeterminate(true);
                backupFilesCount.setText("");
                currentFileName.setText("");
                currentFileSize.setText("");
                uploadSpeed.setText("");
                totalSentSize.setText("");
                backedUpFiles.clear();
                break;
            case RUNNING:
                backupButton.setVisibility(View.GONE);
                backupProgressCard.setVisibility(View.VISIBLE);
                backupRunningLayout.setVisibility(View.VISIBLE);
                backupSuccessLayout.setVisibility(View.GONE);
                backupProgressTitle.setText(R.string.backup_status_running);
                backupOverallProgress.setIndeterminate(false);
                backupOverallProgress.setMax(Math.max(1, snapshot.getTotalFiles()));
                backupOverallProgress.setProgress(snapshot.getFilesSent());
                backupFilesCount.setText(getString(R.string.backup_files_count_format, snapshot.getFilesSent(), snapshot.getTotalFiles()));
                currentFileName.setText(snapshot.getCurrentFile() == null ? "" : snapshot.getCurrentFile());
                currentFileSize.setText(formatSizeProgress(snapshot.getCurrentFileSent(), snapshot.getCurrentFileTotal()));
                uploadSpeed.setText(String.format(java.util.Locale.getDefault(), "%.1f MB/s", snapshot.getSpeedMbps()));
                totalSentSize.setText(getString(R.string.backup_total_sent_format, Formatter.formatFileSize(this, snapshot.getTotalSentBytes())));
                break;
            case COMPLETED:
                renderBackupCompleted(snapshot.getResult());
                break;
            case FAILED:
                renderBackupIdle();
                break;
            case CANCELLED:
                renderBackupIdle();
                break;
            case IDLE:
            default:
                renderBackupIdle();
                break;
        }
    }

    private void renderBackupCompleted(BackupRepository.BackupResult result) {
        backupButton.setVisibility(View.VISIBLE);
        backupProgressCard.setVisibility(View.VISIBLE);
        backupRunningLayout.setVisibility(View.GONE);
        backupSuccessLayout.setVisibility(View.VISIBLE);

        if (result == null) {
            renderBackupIdle();
            return;
        }

        backupSummaryFiles.setText(getString(R.string.backup_summary_files, result.getTotalFiles()));
        backupSummaryDuration.setText(getString(R.string.backup_summary_duration, formatDuration(result.getDurationMillis())));
        backupSummarySize.setText(getString(R.string.backup_summary_size, Formatter.formatFileSize(this, result.getTotalBytes())));

        backedUpFiles.clear();
        backedUpFiles.addAll(result.getFileNames());
    }

    private void renderBackupIdle() {
        backupButton.setVisibility(View.VISIBLE);
        backupProgressCard.setVisibility(View.GONE);
        backupRunningLayout.setVisibility(View.GONE);
        backupSuccessLayout.setVisibility(View.GONE);
        backupProgressTitle.setText(R.string.backup_status_scanning);
        backupOverallProgress.setIndeterminate(true);
        backupFilesCount.setText("");
        currentFileName.setText("");
        currentFileSize.setText("");
        uploadSpeed.setText("");
        totalSentSize.setText("");
        backedUpFiles.clear();
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds %= 60;
        if (minutes > 0) {
            return String.format(java.util.Locale.getDefault(), "%dm %ds", minutes, seconds);
        } else {
            return String.format(java.util.Locale.getDefault(), "%ds", seconds);
        }
    }

    private void showBackupDetailsDialog() {
        if (backedUpFiles == null || backedUpFiles.isEmpty()) return;

        String[] filesArray = backedUpFiles.toArray(new String[0]);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.backup_details_title)
                .setItems(filesArray, null)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private String formatSizeProgress(long sent, long total) {
        return Formatter.formatFileSize(this, sent) + " / " + Formatter.formatFileSize(this, total);
    }

    private void showManualServerDialog() {
        View content = getLayoutInflater().inflate(R.layout.dialog_server_address, null);
        TextInputLayout inputLayout = content.findViewById(R.id.server_address_layout);
        EditText input = content.findViewById(R.id.server_address_input);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.server_address_title)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.server_address_connect, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(
                androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE
        ).setOnClickListener(view -> {
            String address = String.valueOf(input.getText()).trim();
            if (address.isEmpty()) {
                inputLayout.setError(getString(R.string.server_address_invalid));
                return;
            }
            dialog.dismiss();
            renderServerSearching();
            serverDiscoveryRepository.connectManually(address, serverCallback(true));
        }));
        dialog.show();
    }
}
