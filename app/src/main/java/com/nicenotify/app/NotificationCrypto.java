package com.nicenotify.app;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class NotificationCrypto {
    private static final String ALIAS = "NiceNotifyHistoryKey";
    private static final String PREFIX = "v1:";

    private NotificationCrypto() {}

    static String encrypt(Context context, String plaintext) throws GeneralSecurityException {
        if (plaintext == null || plaintext.isEmpty()) return "";
        if (plaintext.startsWith(PREFIX)) return plaintext;
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getKey());
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        ByteBuffer combined = ByteBuffer.allocate(cipher.getIV().length + encrypted.length);
        combined.put(cipher.getIV());
        combined.put(encrypted);
        return PREFIX + Base64.encodeToString(combined.array(), Base64.NO_WRAP);
    }

    static String decrypt(Context context, String value) throws GeneralSecurityException {
        if (value == null || value.isEmpty() || !value.startsWith(PREFIX)) return value == null ? "" : value;
        byte[] combined = Base64.decode(value.substring(PREFIX.length()), Base64.NO_WRAP);
        if (combined.length < 13) throw new GeneralSecurityException("Invalid encrypted history record");
        ByteBuffer buffer = ByteBuffer.wrap(combined);
        byte[] iv = new byte[12];
        buffer.get(iv);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    private static SecretKey getKey() throws GeneralSecurityException {
        try {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            java.security.Key existing = store.getKey(ALIAS, null);
            if (existing instanceof SecretKey) return (SecretKey) existing;
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build());
            return generator.generateKey();
        } catch (Exception e) {
            throw new GeneralSecurityException("Could not access the on-device history key", e);
        }
    }
}
