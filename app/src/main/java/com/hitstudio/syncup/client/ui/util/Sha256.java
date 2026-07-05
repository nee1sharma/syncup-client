package com.hitstudio.syncup.client.ui.util;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.BooleanSupplier;

public final class Sha256 {
    private Sha256() {}

    public static String calculate(InputStream inputStream) throws IOException {
        return calculate(inputStream, () -> false);
    }

    public static String calculate(InputStream inputStream, BooleanSupplier isCancelled) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                if (isCancelled != null && isCancelled.getAsBoolean()) {
                    throw new IOException("Backup cancelled");
                }
                digest.update(buffer, 0, read);
            }
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not supported", e);
        }
    }
}
