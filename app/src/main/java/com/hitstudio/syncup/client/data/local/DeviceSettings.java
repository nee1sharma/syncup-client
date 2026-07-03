package com.hitstudio.syncup.client.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

public final class DeviceSettings {

    private static final String PREFERENCES = "syncup_device";
    private static final String KEY_DEVICE_ID = "device_id";

    private final SharedPreferences preferences;

    public DeviceSettings(Context context) {
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    public synchronized String getDeviceId() {
        String id = preferences.getString(KEY_DEVICE_ID, null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            preferences.edit().putString(KEY_DEVICE_ID, id).apply();
        }
        return id;
    }
}
