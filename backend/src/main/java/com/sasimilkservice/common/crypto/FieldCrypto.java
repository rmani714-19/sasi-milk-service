package com.sasimilkservice.common.crypto;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * AES-256-GCM encryption + a deterministic HMAC "blind index" for
 * producer bank-account numbers.
 *
 * <ul>
 *   <li>{@link #encrypt} → base64({@code iv(12) || ciphertext||tag}) stored in
 *       {@code producer_bank_accounts.account_number_enc}.</li>
 *   <li>{@link #blindIndex} → hex HMAC-SHA256 stored in
 *       {@code account_number_hash} for dedupe / lookup without decryption.</li>
 * </ul>
 *
 * The key is supplied via configuration (AWS Secrets Manager in real
 * environments). Key rotation is out of scope for the scaffold.
 */
@Component
public class FieldCrypto {

    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec aesKey;
    private final SecretKeySpec macKey;
    private final SecureRandom random = new SecureRandom();

    public FieldCrypto(CryptoProperties props) {
        byte[] key = Base64.getDecoder().decode(props.fieldKey());
        if (key.length != 32) {
            throw new IllegalStateException("app.crypto.field-key must decode to 32 bytes (got " + key.length + ")");
        }
        this.aesKey = new SecretKeySpec(key, "AES");
        this.macKey = new SecretKeySpec(key, "HmacSHA256");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("field encryption failed", e);
        }
    }

    public String decrypt(String stored) {
        try {
            byte[] in = Base64.getDecoder().decode(stored);
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(in, 0, iv, 0, IV_LEN);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] pt = cipher.doFinal(in, IV_LEN, in.length - IV_LEN);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("field decryption failed", e);
        }
    }

    public String blindIndex(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(macKey);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("blind index failed", e);
        }
    }

    public static String lastFour(String value) {
        String digits = value.replaceAll("\\D", "");
        return digits.length() <= 4 ? digits : digits.substring(digits.length() - 4);
    }
}
