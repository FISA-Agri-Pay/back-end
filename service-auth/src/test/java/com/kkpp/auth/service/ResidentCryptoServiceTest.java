package com.kkpp.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ResidentCryptoServiceTest {

    private ResidentCryptoService residentCryptoService;

    @BeforeEach
    void setUp() {
        residentCryptoService = new ResidentCryptoService();
        ReflectionTestUtils.setField(residentCryptoService, "residentIdHmacKey", "test-hmac-secret-key");
        ReflectionTestUtils.setField(residentCryptoService, "residentIdEncryptionKey", "test-encryption-secret-key");
    }

    @Test
    void normalizeRemovesHyphenAndKeepsBlankValues() {
        assertThat(residentCryptoService.normalize("900101-1234567")).isEqualTo("9001011234567");
        assertThat(residentCryptoService.normalize("")).isEqualTo("");
        assertThat(residentCryptoService.normalize(null)).isNull();
    }

    @Test
    void hmacReturnsVersionedHashAndHandlesBlankValues() {
        String hash = residentCryptoService.hmac("9001011234567");

        assertThat(hash).startsWith("v2$");
        assertThat(hash).hasSize(67);
        assertThat(residentCryptoService.hmac("")).isNull();
        assertThat(residentCryptoService.hmac(null)).isNull();
    }

    @Test
    void encryptAndDecryptRoundTripResidentId() {
        String encrypted = residentCryptoService.encrypt("9001011234567");

        assertThat(encrypted).startsWith("v2$");
        assertThat(residentCryptoService.decrypt(encrypted)).isEqualTo("9001011234567");
    }

    @Test
    void encryptAndDecryptHandleBlankValuesAsNull() {
        assertThat(residentCryptoService.encrypt("")).isNull();
        assertThat(residentCryptoService.encrypt(null)).isNull();
        assertThat(residentCryptoService.decrypt("")).isNull();
        assertThat(residentCryptoService.decrypt(null)).isNull();
    }

    @Test
    void decryptRejectsUnsupportedOrBrokenCipherText() {
        assertThatThrownBy(() -> residentCryptoService.decrypt("v1$abc"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> residentCryptoService.decrypt("v2$broken-base64"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void encryptionFailsWhenKeyIsMissing() {
        ReflectionTestUtils.setField(residentCryptoService, "residentIdEncryptionKey", null);

        assertThatThrownBy(() -> residentCryptoService.encrypt("9001011234567"))
                .isInstanceOf(NullPointerException.class);
    }
}
