package com.mediaprovenance.hashing;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HashServiceTest {

    private final HashService hashService = new HashService();

    @Test
    void sha256_KnownVectorAbc_ReturnsExpectedHexHash() {
        String input = "abc";
        String expectedHash = "0xba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

        String actualHashBytes = hashService.sha256(input.getBytes(StandardCharsets.UTF_8));
        assertEquals(expectedHash, actualHashBytes);

        String actualHashStream = hashService.sha256(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
        assertEquals(expectedHash, actualHashStream);
    }
}
