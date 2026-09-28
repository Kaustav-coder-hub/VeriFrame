package com.mediaprovenance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class SecurityAndFilterTests {

    @Autowired
    private MockMvc mockMvc;

    private static final String DEV_API_KEY = "dev-secret-api-key-change-in-prod";

    @Test
    void postWriteWithoutKey_Returns401ProblemJson() throws Exception {
        mockMvc.perform(post("/api/v1/media/test"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.type").value("urn:verimedia:error:unauthorized"))
                .andExpect(jsonPath("$.title").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void postWriteWithInvalidKey_Returns401ProblemJson() throws Exception {
        mockMvc.perform(post("/api/v1/media/test")
                        .header("X-API-KEY", "wrong-secret-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.type").value("urn:verimedia:error:unauthorized"));
    }

    @Test
    void postWriteWithValidKey_Returns200Ok() throws Exception {
        mockMvc.perform(post("/api/v1/media/test")
                        .header("X-API-KEY", DEV_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    void postVerifyWithoutKey_Returns200Ok() throws Exception {
        mockMvc.perform(post("/api/v1/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("verify_ok"));
    }

    @Test
    void getReadWithoutKey_Returns200Ok() throws Exception {
        mockMvc.perform(get("/api/v1/media/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("read_ok"));
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
        String invalidHeader = "invalid correlation id with spaces";
        mockMvc.perform(get("/actuator/health")
                        .header("X-Correlation-ID", invalidHeader))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", not(invalidHeader)))
                .andExpect(header().string("X-Correlation-ID", matchesPattern("^[a-f0-9\\-]{36}$")));
    }
}
