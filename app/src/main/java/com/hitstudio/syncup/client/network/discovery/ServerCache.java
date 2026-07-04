package com.hitstudio.syncup.client.network.discovery;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.UUID;

final class ServerCache {

    private static final String PREFERENCES = "syncup_server";
    private static final String KEY_ID = "server_id";
    private static final String KEY_NAME = "server_name";
    private static final String KEY_VERSION = "server_version";
    private static final String KEY_BASE_URL = "base_url";

    private final SharedPreferences preferences;

    ServerCache(Context context) {
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    ServerInfo get() {
        String id = preferences.getString(KEY_ID, null);
        String name = preferences.getString(KEY_NAME, null);
        String version = preferences.getString(KEY_VERSION, null);
        String baseUrl = preferences.getString(KEY_BASE_URL, null);
        if (id == null || name == null || baseUrl == null) {
            return null;
        }
        try {
            return new ServerInfo(
                    UUID.fromString(id),
                    name,
                    version,
                    baseUrl,
                    Collections.emptyList()
            );
        } catch (IllegalArgumentException ignored) {
            clear();
            return null;
        }
    }

    void put(ServerInfo server) {
        preferences.edit()
                .putString(KEY_ID, server.getServerId().toString())
                .putString(KEY_NAME, server.getServerName())
                .putString(KEY_VERSION, server.getServerVersion())
                .putString(KEY_BASE_URL, server.getBaseUrl())
                .apply();
    }

    void clear() {
        preferences.edit().clear().commit();
    }
}
