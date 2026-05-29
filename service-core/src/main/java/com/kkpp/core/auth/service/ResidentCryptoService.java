package com.kkpp.core.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class ResidentCryptoService {

    @Value("${resident-id.hmac-key}")
    private String residentIdHmacKey;

    @Value("${resident-id.encryption-key}")
    private String residentIdEncryptionKey;

    public String normalize(String residentId) {
        if (!StringUtils.hasText(residentId)) {
            return residentId;
        }
        return residentId.replace("-", "");
    }

    public String encrypt(String residentId) {
        if (!StringUtils.hasText(residentId)) {
            return null;
        }
        try {
            SecretKeySpec key = deriveAesKey();
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ciphertext = cipher.doFinal(residentId.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return "v2$" + Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            log.error("주민번호 암호화에 실패했습니다.", e);
            throw new IllegalStateException("주민번호 암호화에 실패했습니다.", e);
        }
    }

    // 실제로 복호화해서 회원 조회할 때 사용 예정
    public String decrypt(String encrypted) {
        if (!StringUtils.hasText(encrypted)) {
            return null;
        }
        if (!encrypted.startsWith("v2$")) {
            log.warn("알 수 없는 주민번호 암호화 형식입니다. encrypted={}", encrypted);
            throw new IllegalArgumentException("알 수 없는 주민번호 암호화 형식입니다.");
        }
        try {
            SecretKeySpec key = deriveAesKey();
            byte[] combined = Base64.getDecoder().decode(encrypted.substring(3));
            byte[] iv = Arrays.copyOfRange(combined, 0, 12);
            byte[] ciphertext = Arrays.copyOfRange(combined, 12, combined.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            log.error("주민번호 복호화에 실패했습니다.", e);
            throw new IllegalStateException("주민번호 복호화에 실패했습니다.", e);
        }
    }

    public String hmac(String residentId) {
        if (!StringUtils.hasText(residentId)) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(residentIdHmacKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(residentId.getBytes(StandardCharsets.UTF_8));
            return "v2$" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("HmacSHA256 알고리즘을 사용할 수 없습니다.", e);
            throw new IllegalStateException("HmacSHA256 알고리즘을 사용할 수 없습니다.", e);
        }
    }

    private SecretKeySpec deriveAesKey() throws NoSuchAlgorithmException {
        byte[] raw = MessageDigest.getInstance("SHA-256")
                .digest(residentIdEncryptionKey.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(raw, "AES");
    }
}
