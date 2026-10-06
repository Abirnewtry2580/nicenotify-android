package com.nicenotify.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PinManager {
    private static final String FILE = "app_lock";
    private static final String SALT = "pin_salt";
    private static final String HASH = "pin_hash";
    private static final int ITERATIONS = 120000;

    private PinManager() {}

    public static boolean hasPin(Context context) {
        return prefs(context).contains(HASH);
    }

    public static void setPin(Context context, String pin) throws Exception {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] hash = derive(pin, salt);
        prefs(context).edit()
                .putString(SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
                .apply();
    }

    public static boolean verify(Context context, String pin) {
        SharedPreferences preferences = prefs(context);
        String saltValue = preferences.getString(SALT, null);
        String hashValue = preferences.getString(HASH, null);
        if (saltValue == null || hashValue == null) return false;
        try {
            byte[] salt = Base64.decode(saltValue, Base64.NO_WRAP);
            byte[] expected = Base64.decode(hashValue, Base64.NO_WRAP);
            return MessageDigest.isEqual(expected, derive(pin, salt));
        } catch (Exception e) {
            return false;
        }
    }

    public static void clear(Context context) {
        prefs(context).edit().remove(SALT).remove(HASH).apply();
    }

    private static byte[] derive(String pin, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
