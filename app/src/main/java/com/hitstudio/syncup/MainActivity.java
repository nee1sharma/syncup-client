package com.hitstudio.syncup;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

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
import com.hitstudio.syncup.data.repository.PresetRepository;
import com.hitstudio.syncup.domain.model.BackupPreset;
import com.hitstudio.syncup.network.discovery.ServerDiscoveryRepository;
import com.hitstudio.syncup.network.discovery.ServerInfo;
import com.hitstudio.syncup.ui.config.BackupConfigActivity;
import com.hitstudio.syncup.ui.util.PresetSummaryFormatter;

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

    private boolean isPresetLoaded = false;
    private boolean isServerConnected = false;

    private final ActivityResultLauncher<Intent> configLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> loadDefaultPreset()
            );

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
        presetRepository = PresetRepository.getInstance(this);
        serverDiscoveryRepository = ServerDiscoveryRepository.getInstance(this);

        configureButton.setOnClickListener(view -> configLauncher.launch(
                new Intent(this, BackupConfigActivity.class)
        ));
        backupButton.setOnClickListener(view -> Snackbar.make(
                findViewById(R.id.main),
                R.string.backup_starting,
                Snackbar.LENGTH_LONG
        ).show());
        findViewById(R.id.retry_discovery_button).setOnClickListener(
                view -> discoverServer()
        );
        findViewById(R.id.manual_server_button).setOnClickListener(
                view -> showManualServerDialog()
        );

        loadDefaultPreset();
        discoverServer();
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
                server.getBaseUrl()
        ));
        serverActions.setVisibility(View.GONE);
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
