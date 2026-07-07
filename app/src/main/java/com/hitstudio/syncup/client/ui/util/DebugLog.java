package com.hitstudio.syncup.client.ui.util;

import android.content.Context;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public final class DebugLog {

    private static final String TAG = "LazySyncUp";
    private static final String LOG_FILE_NAME = "lazysyncup-debug.log";
    private static final int MAX_LOG_BYTES = 512 * 1024;

    private DebugLog() {
    }

    public static void d(Context context, String message) {
        write(context, Log.DEBUG, message, null);
    }

    public static void i(Context context, String message) {
        write(context, Log.INFO, message, null);
    }

    public static void w(Context context, String message) {
        write(context, Log.WARN, message, null);
    }

    public static void e(Context context, String message, Throwable error) {
        write(context, Log.ERROR, message, error);
    }

    public static File getLogFile(Context context) {
        return new File(context.getFilesDir(), LOG_FILE_NAME);
    }

    private static synchronized void write(Context context, int priority, String message, Throwable error) {
        String line = String.format(
                "%s %s: %s",
                Instant.now().toString(),
                priorityToString(priority),
                message
        );

        switch (priority) {
            case Log.ERROR:
                Log.e(TAG, message, error);
                break;
            case Log.WARN:
                Log.w(TAG, message, error);
                break;
            case Log.INFO:
                Log.i(TAG, message);
                break;
            default:
                Log.d(TAG, message);
                break;
        }

        if (error != null) {
            line = line + System.lineSeparator() + Log.getStackTraceString(error);
        }

        File logFile = getLogFile(context);
        rotateIfNeeded(logFile, line.length());

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(logFile, true), StandardCharsets.UTF_8)
        )) {
            writer.write(line);
            writer.newLine();
        } catch (IOException ignored) {
            // Logging must never break the backup flow.
        }
    }

    private static void rotateIfNeeded(File logFile, int incomingBytes) {
        if (!logFile.exists()) {
            return;
        }
        long projectedSize = logFile.length() + incomingBytes;
        if (projectedSize <= MAX_LOG_BYTES) {
            return;
        }
        // Keep the log file bounded so it remains easy to inspect.
        if (!logFile.delete()) {
            // If rotation fails, keep appending rather than blocking the caller.
        }
    }

    private static String priorityToString(int priority) {
        if (priority == Log.ERROR) return "E";
        if (priority == Log.WARN) return "W";
        if (priority == Log.INFO) return "I";
        return "D";
    }
}
