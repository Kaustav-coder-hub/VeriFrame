package com.mediaprovenance.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Security security = new Security();
    private Cors cors = new Cors();
    private CloudinaryProperties cloudinary = new CloudinaryProperties();
    private BlockchainProperties blockchain = new BlockchainProperties();

    @Getter
    @Setter
    public static class Security {
        private String apiKey = "dev-secret-api-key-change-in-prod";
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
        private String cloudName;
        private String apiKey;
        private String apiSecret;
    }

    @Getter
    @Setter
    public static class BlockchainProperties {
        private String mode = "mock"; // mock | http
        private String serviceUrl = "http://localhost:4000";
        private int connectTimeoutMs = 5000;
        private int readTimeoutMs = 10000;
    }
}
