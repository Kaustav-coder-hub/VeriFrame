package com.mediaprovenance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class SecurityAndFilterTests {

    @Autowired
    private MockMvc mockMvc;

    private static final String DEV_API_KEY = "dev-secret-api-key-change-in-prod";

    // Small 1x1 white JPEG magic bytes for realistic testing
    private static final byte[] MINI_JPEG = {
        (byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE0, 0x00, 0x10,
        0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01,
        0x00, 0x01, 0x00, 0x00, (byte)0xFF, (byte)0xDB, 0x00, 0x43
    };

    @Test
    void postVerifyWithoutKey_Returns200Ok() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", MINI_JPEG);
        // Verify is public — no key needed, may return MISMATCH/NOT_FOUND but not 401
        mockMvc.perform(multipart("/api/v1/verify").file(file))
                .andExpect(status().isOk());
    }

    @Test
    void getPassportWithoutKey_Returns404ForUnknownId() throws Exception {
        mockMvc.perform(get("/api/v1/media/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }

    @Test
    void correlationId_ValidHeader_Preserved() throws Exception {
        String validCorrelationId = "valid-correlation-id-12345";
        mockMvc.perform(get("/actuator/health")
                        .header("X-Correlation-ID", validCorrelationId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", validCorrelationId));
    }

    @Test
    void correlationId_LogInjectionHeader_ReplacedWithUuid() throws Exception {
        // Header contains spaces which fail the regex ^[A-Za-z0-9._-]{8,64}$
        String invalidHeader = "invalid correlation id with spaces";
        mockMvc.perform(get("/actuator/health")
                        .header("X-Correlation-ID", invalidHeader))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", not(invalidHeader)))
                .andExpect(header().string("X-Correlation-ID", matchesPattern("^[a-f0-9\\-]{36}$")));
    }

    @Test
    void actuatorHealth_IsPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
