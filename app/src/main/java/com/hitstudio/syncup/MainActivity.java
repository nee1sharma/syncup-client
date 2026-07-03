package com.hitstudio.syncup;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
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
import com.google.android.material.snackbar.Snackbar;
import com.hitstudio.syncup.data.repository.PresetRepository;
import com.hitstudio.syncup.domain.model.BackupPreset;
import com.hitstudio.syncup.ui.config.BackupConfigActivity;
import com.hitstudio.syncup.ui.util.PresetSummaryFormatter;

public class MainActivity extends AppCompatActivity {

    private ProgressBar presetLoading;
    private TextView presetName;
    private TextView presetSummary;
    private MaterialButton configureButton;
    private MaterialButton backupButton;
    private PresetRepository presetRepository;

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
        presetRepository = PresetRepository.getInstance(this);

        configureButton.setOnClickListener(view -> configLauncher.launch(
                new Intent(this, BackupConfigActivity.class)
        ));
        backupButton.setOnClickListener(view -> Snackbar.make(
                findViewById(R.id.main),
                R.string.backup_disabled_hint,
                Snackbar.LENGTH_LONG
        ).show());

        loadDefaultPreset();
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
}
