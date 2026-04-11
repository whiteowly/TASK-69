package com.croh.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class EncryptionServiceTest {

    private final EncryptionService service = new EncryptionService("test-key-for-unit-tests-only-00");

    @Test
    void encrypt_thenDecrypt_returnsOriginalString() {
        String plaintext = "1992-01-20";
        String encrypted = service.encrypt(plaintext);
        String decrypted = service.decrypt(encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void encrypted_valuesDifferFromPlaintext() {
        String plaintext = "1992-01-20";
        String encrypted = service.encrypt(plaintext);
        assertNotEquals(plaintext, encrypted);
    }

    @Test
    void encrypt_producesUniqueIvEachTime() {
        String plaintext = "same-input";
        String encrypted1 = service.encrypt(plaintext);
        String encrypted2 = service.encrypt(plaintext);
        assertNotEquals(encrypted1, encrypted2); // unique IV per encryption
        assertEquals(plaintext, service.decrypt(encrypted1));
        assertEquals(plaintext, service.decrypt(encrypted2));
    }

    @Test
    void encryptBytes_thenDecryptBytes_returnsOriginal() {
        byte[] plainBytes = "secret document content".getBytes();
        byte[] encrypted = service.encryptBytes(plainBytes);
        byte[] decrypted = service.decryptBytes(encrypted);
        assertArrayEquals(plainBytes, decrypted);
    }

    @Test
    void encryptBytes_outputDiffersFromInput() {
        byte[] plainBytes = "secret content".getBytes();
        byte[] encrypted = service.encryptBytes(plainBytes);
        assertNotEquals(new String(plainBytes), new String(encrypted));
    }

    @Test
    void encrypt_null_returnsNull() {
        assertNull(service.encrypt(null));
        assertNull(service.decrypt(null));
        assertNull(service.encryptBytes(null));
        assertNull(service.decryptBytes(null));
    }
}
