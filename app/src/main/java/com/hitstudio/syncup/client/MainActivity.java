package com.hitstudio.syncup.client;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

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
import com.hitstudio.syncup.client.ui.config.BackupConfigActivity;
import com.hitstudio.syncup.client.ui.util.PresetSummaryFormatter;

public class MainActivity extends AppCompatActivity {

    private ProgressBar presetLoading;
    private TextView presetName;
    private TextView presetSummary;
    private MaterialButton configureButton;
    private MaterialButton backupButton;
    private TextView serverStatus;
    private TextView serverStatusExplanation;
    private LinearLayout serverActions;
    private PresetRepository presetRepository;
    private ServerDiscoveryRepository serverDiscoveryRepository;
    private BackupRepository backupRepository;

    private View backupProgressCard;
    private TextView backupProgressTitle;
    private LinearProgressIndicator backupOverallProgress;
    private TextView backupFilesCount;
    private TextView currentFileName;
    private TextView currentFileSize;
    private TextView uploadSpeed;
    private TextView totalSentSize;
    private MaterialButton cancelBackupButton;

    private BackupPreset currentPreset;
    private ServerInfo currentServer;
    private boolean isPresetLoaded = false;
    private boolean isServerConnected = false;

    private final ActivityResultLauncher<Intent> configLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> loadDefaultPreset()
            );

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (result.containsValue(true)) {
                    startBackup();
                } else {
                    Snackbar.make(findViewById(R.id.main), "Permissions required to scan files", Snackbar.LENGTH_LONG).show();
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
        presetSummary = findViewById(R.id.preset_summary);
        configureButton = findViewById(R.id.configure_button);
        backupButton = findViewById(R.id.backup_button);
        serverStatus = findViewById(R.id.server_status);
        serverStatusExplanation = findViewById(R.id.server_status_explanation);
        serverActions = findViewById(R.id.server_actions);
        backupProgressCard = findViewById(R.id.backup_progress_card);
        backupOverallProgress = findViewById(R.id.backup_overall_progress);
        backupFilesCount = findViewById(R.id.backup_files_count);
        currentFileName = findViewById(R.id.current_file_name);
        currentFileSize = findViewById(R.id.current_file_size);
        uploadSpeed = findViewById(R.id.upload_speed);
        totalSentSize = findViewById(R.id.total_sent_size);
        backupProgressTitle = findViewById(R.id.backup_progress_title);
        cancelBackupButton = findViewById(R.id.cancel_backup_button);
        presetRepository = PresetRepository.getInstance(this);
        serverDiscoveryRepository = ServerDiscoveryRepository.getInstance(this);
        backupRepository = BackupRepository.getInstance(this);

        configureButton.setOnClickListener(view -> configLauncher.launch(
                new Intent(this, BackupConfigActivity.class)
        ));
        backupButton.setOnClickListener(view -> checkPermissionsAndStartBackup());
        cancelBackupButton.setOnClickListener(view -> backupRepository.cancel());
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
        loadDefaultPreset();
        discoverServer();
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
        isServerConnected = false;
        updateBackupButtonState();
    }

    private void updateBackupButtonState() {
        backupButton.setEnabled(isPresetLoaded && isServerConnected);
    }

    private void checkPermissionsAndStartBackup() {
        permissionLauncher.launch(new String[]{
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO
        });
    }

    private void startBackup() {
        if (currentPreset == null || currentServer == null) return;

        backupButton.setVisibility(View.GONE);
        backupProgressCard.setVisibility(View.VISIBLE);

        backupRepository.startBackup(currentPreset, currentServer, new BackupRepository.Callback() {
            @Override
            public void onScanningStarted() {
                backupProgressTitle.setText(R.string.backup_status_scanning);
                backupOverallProgress.setIndeterminate(true);
            }

            @Override
            public void onProgress(int filesSent, int totalFiles, String fileName, long fileSent, long fileTotal, double speedMbps, long totalSent) {
                backupProgressTitle.setText(R.string.backup_status_running);
                backupOverallProgress.setIndeterminate(false);
                backupOverallProgress.setMax(totalFiles);
                backupOverallProgress.setProgress(filesSent);

                backupFilesCount.setText(getString(R.string.backup_files_count_format, filesSent, totalFiles));
                currentFileName.setText(fileName);
                currentFileSize.setText(formatSizeProgress(fileSent, fileTotal));
                uploadSpeed.setText(String.format(java.util.Locale.getDefault(), "%.1f MB/s", speedMbps));
                totalSentSize.setText(getString(R.string.backup_total_sent_format, Formatter.formatFileSize(MainActivity.this, totalSent)));
            }

            @Override
            public void onComplete() {
                if (!canRender()) return;
                backupProgressCard.setVisibility(View.GONE);
                backupButton.setVisibility(View.VISIBLE);
                Snackbar.make(findViewById(R.id.main), R.string.backup_complete, Snackbar.LENGTH_LONG).show();
            }

            @Override
            public void onError(Throwable error) {
                if (!canRender()) return;
                backupProgressCard.setVisibility(View.GONE);
                backupButton.setVisibility(View.VISIBLE);
                Snackbar.make(findViewById(R.id.main), getString(R.string.backup_failed, error.getMessage()), Snackbar.LENGTH_LONG).show();
            }

            @Override
            public void onCancelled() {
                if (!canRender()) return;
                backupProgressCard.setVisibility(View.GONE);
                backupButton.setVisibility(View.VISIBLE);
                Snackbar.make(findViewById(R.id.main), R.string.backup_cancelled, Snackbar.LENGTH_SHORT).show();
            }
        });
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
