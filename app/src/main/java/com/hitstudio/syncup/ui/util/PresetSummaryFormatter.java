package com.hitstudio.syncup.ui.util;

import android.content.Context;

import com.hitstudio.syncup.R;
import com.hitstudio.syncup.domain.model.BackupPreset;
import com.hitstudio.syncup.domain.model.DateFilterMode;

import java.util.ArrayList;
import java.util.List;

public final class PresetSummaryFormatter {

    private PresetSummaryFormatter() {
    }

    public static String format(Context context, BackupPreset preset) {
        return context.getString(
                R.string.preset_summary_format,
                formatTypes(context, preset),
                formatDate(context, preset.getDateFilterMode()),
                formatSources(context, preset)
        );
    }

    private static String formatTypes(Context context, BackupPreset preset) {
        List<String> types = new ArrayList<>();
        if (preset.includesImages()) {
            types.add(context.getString(R.string.summary_images));
        }
        if (preset.includesVideos()) {
            types.add(context.getString(R.string.summary_videos));
        }
        if (preset.includesDocuments()) {
            types.add(context.getString(R.string.summary_documents));
        }
        return join(types);
    }

    private static String formatDate(Context context, DateFilterMode mode) {
        switch (mode) {
            case TODAY:
                return context.getString(R.string.date_today);
            case YESTERDAY:
                return context.getString(R.string.date_yesterday);
            case LAST_7_DAYS:
                return context.getString(R.string.date_last_7_days);
            case CUSTOM_RANGE:
                return context.getString(R.string.date_custom);
            case ALL:
            default:
                return context.getString(R.string.date_all);
        }
    }

    private static String formatSources(Context context, BackupPreset preset) {
        if (preset.isMediaStoreSource() && preset.hasFolderSource()) {
            return context.getString(R.string.summary_both_sources);
        }
        if (preset.hasFolderSource()) {
            return context.getString(R.string.summary_custom_folder);
        }
        return context.getString(R.string.summary_media_library);
    }

    private static String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) {
                result.append(" & ");
            }
            result.append(value);
        }
        return result.toString();
    }
}
