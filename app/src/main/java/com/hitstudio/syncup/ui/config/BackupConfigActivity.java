package com.hitstudio.syncup.ui.config;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.hitstudio.syncup.R;
import com.hitstudio.syncup.data.repository.PresetRepository;
import com.hitstudio.syncup.domain.model.BackupPreset;
import com.hitstudio.syncup.domain.model.DateFilterMode;
import com.hitstudio.syncup.domain.usecase.PresetValidationResult;
import com.hitstudio.syncup.domain.usecase.ValidateBackupPreset;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

public class BackupConfigActivity extends AppCompatActivity {

    private final ValidateBackupPreset validateBackupPreset = new ValidateBackupPreset();
    private final DateTimeFormatter dateFormatter =
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

    private TextInputLayout presetNameLayout;
    private EditText presetNameInput;
    private MaterialCheckBox mediaStoreSource;
    private MaterialCheckBox includeImages;
    private MaterialCheckBox includeVideos;
    private MaterialCheckBox includeDocuments;
    private TextView folderValue;
    private MaterialButton removeFolderButton;
    private RadioGroup dateFilterGroup;
    private LinearLayout customDateContainer;
    private MaterialButton customFromButton;
    private MaterialButton customToButton;
    private TextView configError;
    private MaterialButton saveButton;
    private ProgressBar saveProgress;

    private PresetRepository presetRepository;
    private BackupPreset loadedPreset;
    private Uri folderUri;
    private LocalDate customFrom;
    private LocalDate customTo;

    private final ActivityResultLauncher<Uri> folderPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri == null) {
                    return;
                }
                try {
                    getContentResolver().takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );
                    folderUri = uri;
                    renderFolder();
                } catch (SecurityException error) {
                    Snackbar.make(
                            findViewById(R.id.config_root),
                            R.string.folder_permission_failed,
                            Snackbar.LENGTH_LONG
                    ).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_backup_config);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.config_root), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        bindViews();
        bindActions();

        presetRepository = PresetRepository.getInstance(this);
        setFormEnabled(false);
        presetRepository.getOrCreateDefault(new PresetRepository.Callback<BackupPreset>() {
            @Override
            public void onSuccess(BackupPreset preset) {
                if (!canRender()) {
                    return;
                }
                loadedPreset = preset;
                populateForm(preset);
                setFormEnabled(true);
            }

            @Override
            public void onError(Throwable error) {
                if (!canRender()) {
                    return;
                }
                showError(R.string.preset_load_failed);
                setFormEnabled(true);
            }
        });
    }

    private void bindViews() {
        presetNameLayout = findViewById(R.id.preset_name_layout);
        presetNameInput = findViewById(R.id.preset_name_input);
        mediaStoreSource = findViewById(R.id.media_store_source);
        includeImages = findViewById(R.id.include_images);
        includeVideos = findViewById(R.id.include_videos);
        includeDocuments = findViewById(R.id.include_documents);
        folderValue = findViewById(R.id.folder_value);
        removeFolderButton = findViewById(R.id.remove_folder_button);
        dateFilterGroup = findViewById(R.id.date_filter_group);
        customDateContainer = findViewById(R.id.custom_date_container);
        customFromButton = findViewById(R.id.custom_from_button);
        customToButton = findViewById(R.id.custom_to_button);
        configError = findViewById(R.id.config_error);
        saveButton = findViewById(R.id.save_preset_button);
        saveProgress = findViewById(R.id.save_progress);
    }

    private void bindActions() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(view -> finish());
        findViewById(R.id.choose_folder_button).setOnClickListener(
                view -> folderPicker.launch(folderUri)
        );
        removeFolderButton.setOnClickListener(view -> removeFolder());
        customFromButton.setOnClickListener(view -> showDatePicker(true));
        customToButton.setOnClickListener(view -> showDatePicker(false));
        dateFilterGroup.setOnCheckedChangeListener((group, checkedId) ->
                customDateContainer.setVisibility(
                        checkedId == R.id.date_custom ? View.VISIBLE : View.GONE
                )
        );
        saveButton.setOnClickListener(view -> savePreset());
    }

    private void populateForm(BackupPreset preset) {
        presetNameInput.setText(preset.getName());
        mediaStoreSource.setChecked(preset.isMediaStoreSource());
        includeImages.setChecked(preset.includesImages());
        includeVideos.setChecked(preset.includesVideos());
        includeDocuments.setChecked(preset.includesDocuments());
        folderUri = preset.hasFolderSource() ? Uri.parse(preset.getFolderUri()) : null;
        customFrom = preset.getCustomFrom();
        customTo = preset.getCustomTo();
        dateFilterGroup.check(radioIdFor(preset.getDateFilterMode()));
        renderFolder();
        renderCustomDates();
    }

    private void savePreset() {
        hideError();
        BackupPreset preset = buildPreset();
        PresetValidationResult validation = validateBackupPreset.execute(preset);
        if (!validation.isValid()) {
            showValidationError(validation.getError());
            return;
        }

        setSaving(true);
        presetRepository.saveDefault(preset, new PresetRepository.Callback<BackupPreset>() {
            @Override
            public void onSuccess(BackupPreset value) {
                if (!canRender()) {
                    return;
                }
                releaseReplacedFolderGrant(value.getFolderUri());
                setResult(RESULT_OK);
                Snackbar.make(
                        findViewById(R.id.config_root),
                        R.string.preset_saved,
                        Snackbar.LENGTH_SHORT
                ).addCallback(new Snackbar.Callback() {
                    @Override
                    public void onDismissed(Snackbar transientBottomBar, int event) {
                        finish();
                    }
                }).show();
            }

            @Override
            public void onError(Throwable error) {
                if (!canRender()) {
                    return;
                }
                setSaving(false);
                showError(R.string.preset_save_failed);
            }
        });
    }

    private BackupPreset buildPreset() {
        long id = loadedPreset == null ? 0 : loadedPreset.getId();
        return BackupPreset.builder()
                .setId(id)
                .setName(String.valueOf(presetNameInput.getText()))
                .setMediaStoreSource(mediaStoreSource.isChecked())
                .setFolderUri(folderUri == null ? null : folderUri.toString())
                .setImages(includeImages.isChecked())
                .setVideos(includeVideos.isChecked())
                .setDocuments(includeDocuments.isChecked())
                .setDateFilterMode(selectedDateMode())
                .setCustomFrom(customFrom)
                .setCustomTo(customTo)
                .setDefaultPreset(true)
                .setUpdatedAtEpochMillis(System.currentTimeMillis())
                .build();
    }

    private void removeFolder() {
        folderUri = null;
        renderFolder();
    }

    private void releaseReplacedFolderGrant(String savedFolderUri) {
        if (loadedPreset == null || !loadedPreset.hasFolderSource()) {
            return;
        }
        String previousFolderUri = loadedPreset.getFolderUri();
        if (previousFolderUri.equals(savedFolderUri)) {
            return;
        }
        try {
            getContentResolver().releasePersistableUriPermission(
                    Uri.parse(previousFolderUri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (SecurityException ignored) {
            // The grant may already have been revoked outside the app.
        }
    }

    private void renderFolder() {
        boolean selected = folderUri != null;
        folderValue.setText(selected ? folderDisplayName(folderUri) : getString(R.string.no_folder_selected));
        removeFolderButton.setVisibility(selected ? View.VISIBLE : View.GONE);
    }

    private String folderDisplayName(Uri uri) {
        try {
            return Uri.decode(DocumentsContract.getTreeDocumentId(uri));
        } catch (RuntimeException ignored) {
            return uri.toString();
        }
    }

    private void showDatePicker(boolean fromDate) {
        LocalDate initial = fromDate ? customFrom : customTo;
        if (initial == null) {
            initial = LocalDate.now();
        }
        new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    LocalDate selected = LocalDate.of(year, month + 1, dayOfMonth);
                    if (fromDate) {
                        customFrom = selected;
                    } else {
                        customTo = selected;
                    }
                    renderCustomDates();
                },
                initial.getYear(),
                initial.getMonthValue() - 1,
                initial.getDayOfMonth()
        ).show();
    }

    private void renderCustomDates() {
        customFromButton.setText(customFrom == null
                ? getString(R.string.date_from)
                : getString(R.string.date_button_format, dateFormatter.format(customFrom)));
        customToButton.setText(customTo == null
                ? getString(R.string.date_to)
                : getString(R.string.date_to_button_format, dateFormatter.format(customTo)));
    }

    private DateFilterMode selectedDateMode() {
        int checkedId = dateFilterGroup.getCheckedRadioButtonId();
        if (checkedId == R.id.date_today) {
            return DateFilterMode.TODAY;
        }
        if (checkedId == R.id.date_yesterday) {
            return DateFilterMode.YESTERDAY;
        }
        if (checkedId == R.id.date_last_7_days) {
            return DateFilterMode.LAST_7_DAYS;
        }
        if (checkedId == R.id.date_custom) {
            return DateFilterMode.CUSTOM_RANGE;
        }
        return DateFilterMode.ALL;
    }

    private int radioIdFor(DateFilterMode mode) {
        switch (mode) {
            case TODAY:
                return R.id.date_today;
            case YESTERDAY:
                return R.id.date_yesterday;
            case LAST_7_DAYS:
                return R.id.date_last_7_days;
            case CUSTOM_RANGE:
                return R.id.date_custom;
            case ALL:
            default:
                return R.id.date_all;
        }
    }

    private void showValidationError(PresetValidationResult.Error error) {
        presetNameLayout.setError(null);
        switch (error) {
            case NAME_REQUIRED:
                presetNameLayout.setError(getString(R.string.error_name_required));
                presetNameInput.requestFocus();
                break;
            case SOURCE_REQUIRED:
                showError(R.string.error_source_required);
                break;
            case FILE_TYPE_REQUIRED:
                showError(R.string.error_file_type_required);
                break;
            case DOCUMENT_FOLDER_REQUIRED:
                showError(R.string.error_document_folder_required);
                break;
            case CUSTOM_DATES_REQUIRED:
                showError(R.string.error_custom_dates_required);
                break;
            case CUSTOM_DATE_ORDER_INVALID:
                showError(R.string.error_custom_date_order);
                break;
        }
    }

    private void hideError() {
        presetNameLayout.setError(null);
        configError.setVisibility(View.GONE);
    }

    private void showError(int messageResource) {
        configError.setText(messageResource);
        configError.setVisibility(View.VISIBLE);
    }

    private void setFormEnabled(boolean enabled) {
        saveButton.setEnabled(enabled);
        findViewById(R.id.choose_folder_button).setEnabled(enabled);
    }

    private void setSaving(boolean saving) {
        saveProgress.setVisibility(saving ? View.VISIBLE : View.GONE);
        saveButton.setEnabled(!saving);
    }

    private boolean canRender() {
        return !isFinishing() && !isDestroyed();
    }
}
