package com.mediaprovenance.hashing;

import com.mediaprovenance.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class HashService {

    public String sha256(byte[] data) {
        if (data == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Cannot hash null data");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data);
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "HASHING_ERROR", "SHA-256 algorithm not available", e);
        }
    }

    public String sha256(InputStream inputStream) {
        if (inputStream == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Cannot hash null stream");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            try (DigestInputStream dis = new DigestInputStream(inputStream, digest)) {
                while (dis.read(buffer) != -1) {
                    // Stream processing
                }
            }
            return bytesToHex(digest.digest());
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "HASHING_ERROR", "Failed to stream and hash data", e);
        }
    }

    public String sha256Utf8(String text) {
        if (text == null) {
            return sha256(new byte[0]);
        }
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(66);
        hexString.append("0x");
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString().toLowerCase();
    }
}
