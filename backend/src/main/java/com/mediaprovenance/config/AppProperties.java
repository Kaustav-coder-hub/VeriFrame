package com.mediaprovenance.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    @Valid
    private Security security = new Security();

    @Valid
    private Cors cors = new Cors();

    @Valid
    private CloudinaryProperties cloudinary = new CloudinaryProperties();

    @Valid
    private BlockchainProperties blockchain = new BlockchainProperties();

    public enum BlockchainMode {
        MOCK, HTTP
    }

    @Getter
    @Setter
    public static class Security {
        @NotBlank(message = "SECURITY_API_KEY must be provided")
        private String apiKey;

        private String headerName = "X-API-KEY";
    }

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:5173", "http://localhost:3000");
    }

    @Getter
    @Setter
    public static class CloudinaryProperties {
        @NotBlank(message = "CLOUDINARY_CLOUD_NAME must be provided")
        private String cloudName;

        @NotBlank(message = "CLOUDINARY_API_KEY must be provided")
        private String apiKey;

        @NotBlank(message = "CLOUDINARY_API_SECRET must be provided")
        private String apiSecret;

        private String autoTaggingCategorization;
    }

    @Getter
    @Setter
    public static class BlockchainProperties {
        @NotNull(message = "BLOCKCHAIN_MODE must be specified (MOCK or HTTP)")
        private BlockchainMode mode = BlockchainMode.MOCK;

        private String serviceUrl = "http://localhost:4000";
        private int connectTimeoutMs = 5000;
        private int readTimeoutStandardMs = 10000;  // for verify and history calls (10 s)
        private int readTimeoutLongMs = 120000;      // for register and version calls (120 s)
    }
}
