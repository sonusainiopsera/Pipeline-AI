package com.opsera.pipelineassistant.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AesEncryptionUtilTest {

    private static final String KEY = "test-key-for-unit-tests-32chars!";

    private final AesEncryptionUtil util = new AesEncryptionUtil(KEY);

    @Test
    void encryptDecryptRoundtrip_returnsOriginalPlaintext() {
        String plaintext = "JBSWY3DPEHPK3PXP";

        String ciphertext = util.encrypt(plaintext);
        String decrypted = util.decrypt(ciphertext);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void encrypt_samePlaintextTwice_producesDifferentCiphertexts() {
        String plaintext = "TOTP_SECRET_12345";

        String first = util.encrypt(plaintext);
        String second = util.encrypt(plaintext);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void decrypt_withWrongKey_throwsException() {
        AesEncryptionUtil encryptor = new AesEncryptionUtil(KEY);
        AesEncryptionUtil decryptor = new AesEncryptionUtil("different-key-that-will-not-work!!");

        String ciphertext = encryptor.encrypt("sensitive-secret");

        assertThrows(IllegalStateException.class, () -> decryptor.decrypt(ciphertext));
    }

    @Test
    void encrypt_producesNonEmptyBase64String() {
        String ciphertext = util.encrypt("any-plaintext");

        assertThat(ciphertext).isNotEmpty();
        assertThat(ciphertext).matches("[A-Za-z0-9+/=]+");
    }

    @Test
    void encryptDecrypt_handlesLongPlaintext() {
        String longSecret = "A".repeat(200);

        String ciphertext = util.encrypt(longSecret);
        String decrypted = util.decrypt(ciphertext);

        assertThat(decrypted).isEqualTo(longSecret);
    }

    @Test
    void decrypt_withGarbageInput_throwsException() {
        assertThrows(IllegalStateException.class, () -> util.decrypt("not-valid-base64!!!"));
    }
}
