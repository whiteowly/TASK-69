package com.croh.files;

import com.croh.crypto.EncryptionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class FileStorageService {

    private final EncryptionService encryptionService;
    private final String basePath;

    public FileStorageService(EncryptionService encryptionService,
                              @Value("${croh.storage.base-path}") String basePath) {
        this.encryptionService = encryptionService;
        this.basePath = basePath;
    }

    /**
     * Encrypts content and writes to disk. Returns the relative file path.
     */
    public String store(byte[] content, String subDir) {
        byte[] encrypted = encryptionService.encryptBytes(content);
        String relativePath = subDir + "/" + UUID.randomUUID() + ".enc";
        Path fullPath = Path.of(basePath).resolve(relativePath);
        try {
            Files.createDirectories(fullPath.getParent());
            Files.write(fullPath, encrypted);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }
        return relativePath;
    }

    /**
     * Reads an encrypted file from disk and returns the decrypted bytes.
     */
    public byte[] read(String relativePath) {
        Path fullPath = Path.of(basePath).resolve(relativePath);
        try {
            byte[] encrypted = Files.readAllBytes(fullPath);
            return encryptionService.decryptBytes(encrypted);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file", e);
        }
    }

    /**
     * Computes SHA-256 hex checksum of the original (pre-encryption) content.
     */
    public String computeChecksum(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
