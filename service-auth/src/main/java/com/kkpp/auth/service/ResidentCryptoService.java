package com.kkpp.auth.service;

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

    // 주민등록번호 원문을 DB에 저장하지 않기 위한 암호화/HMAC 전담 서비스입니다.
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
            // 암호화 실패 로그입니다. 주민등록번호 원문은 절대 남기지 않습니다.
            log.atError()
                    .addKeyValue("event", "auth.resident-id.encrypt.failed")
                    .addKeyValue("failureState", "ENCRYPTION_FAILED")
                    .addKeyValue("exceptionType", e.getClass().getSimpleName())
                    .setCause(e)
                    .log("주민등록번호 암호화에 실패했습니다.");
            throw new IllegalStateException("주민등록번호 암호화에 실패했습니다.", e);
        }
    }

    public String decrypt(String encrypted) {
        if (!StringUtils.hasText(encrypted)) {
            return null;
        }
        if (!encrypted.startsWith("v2$")) {
            // 지원하지 않는 암호문 버전 또는 형식이 들어온 경우입니다.
            log.atWarn()
                    .addKeyValue("event", "auth.resident-id.decrypt.failed")
                    .addKeyValue("failureState", "UNSUPPORTED_CIPHERTEXT_FORMAT")
                    .log("지원하지 않는 주민등록번호 암호문 형식입니다.");
            throw new IllegalArgumentException("지원하지 않는 주민등록번호 암호문 형식입니다.");
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
            // 복호화 실패 로그입니다. 암호문 내용도 식별 가능한 값이라 남기지 않습니다.
            log.atError()
                    .addKeyValue("event", "auth.resident-id.decrypt.failed")
                    .addKeyValue("failureState", "DECRYPTION_FAILED")
                    .addKeyValue("exceptionType", e.getClass().getSimpleName())
                    .setCause(e)
                    .log("주민등록번호 복호화에 실패했습니다.");
            throw new IllegalStateException("주민등록번호 복호화에 실패했습니다.", e);
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
            // HMAC 생성 실패 로그입니다. 주민등록번호 원문과 key 값은 남기지 않습니다.
            log.atError()
                    .addKeyValue("event", "auth.resident-id.hmac.failed")
                    .addKeyValue("algorithm", "HmacSHA256")
                    .addKeyValue("failureState", "HMAC_FAILED")
                    .addKeyValue("exceptionType", e.getClass().getSimpleName())
                    .setCause(e)
                    .log("주민등록번호 HMAC 생성에 실패했습니다.");
            throw new IllegalStateException("HmacSHA256 알고리즘을 사용할 수 없습니다.", e);
        }
    }

    private SecretKeySpec deriveAesKey() throws NoSuchAlgorithmException {
        byte[] raw = MessageDigest.getInstance("SHA-256")
                .digest(residentIdEncryptionKey.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(raw, "AES");
    }
}
