package com.nerd.erp.infrastructure.mail;

import com.nerd.erp.infrastructure.config.mail.MailProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Chiffrement AES-256-GCM des secrets stockés en base (mots de passe SMTP des workspaces).
 * La clé ({@code nerd.mail.secrets-key}, 32 octets Base64) ne quitte jamais le serveur.
 */
@Component
public class SecretCipher {

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(MailProperties properties) {
        String raw = properties.secretsKey();
        if (raw == null || raw.isBlank()) {
            this.key = null;
            return;
        }
        byte[] bytes = Base64.getDecoder().decode(raw.trim());
        if (bytes.length != 32) {
            throw new IllegalStateException("nerd.mail.secrets-key doit faire 32 octets (Base64)");
        }
        this.key = new SecretKeySpec(bytes, "AES");
    }

    public boolean isConfigured() {
        return key != null;
    }

    public String encrypt(String plain) {
        requireKey();
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + enc.length).put(iv).put(enc).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Chiffrement impossible", e);
        }
    }

    public String decrypt(String encoded) {
        requireKey();
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(all, 0, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(all, IV_LENGTH, all.length - IV_LENGTH), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Déchiffrement impossible (clé changée ?)", e);
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new IllegalStateException("nerd.mail.secrets-key n'est pas configurée");
        }
    }
}
