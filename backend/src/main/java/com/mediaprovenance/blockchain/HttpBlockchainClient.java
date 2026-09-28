package com.mediaprovenance.blockchain;

import com.mediaprovenance.common.ApiException;
import com.mediaprovenance.hashing.HashService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.blockchain.mode", havingValue = "HTTP")
public class HttpBlockchainClient implements BlockchainClient {

    private static final Logger log = LoggerFactory.getLogger(HttpBlockchainClient.class);
    private final RestClient longClient;
    private final RestClient standardClient;
    private final HashService hashService;

    public HttpBlockchainClient(
            @Qualifier("blockchainLongRestClient") RestClient longClient,
            @Qualifier("blockchainStandardRestClient") RestClient standardClient,
            HashService hashService) {
        this.longClient = longClient;
        this.standardClient = standardClient;
        this.hashService = hashService;
    }

    @Override
    public BlockchainRecord registerOriginal(byte[] fileBytes, String operation) {
        String computedHash = hashService.sha256(fileBytes);
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return "image.jpg";
            }
        });
        if (operation != null && !operation.isBlank()) {
            builder.part("operation", operation);
        }

        try {
            Map<String, Object> response = longClient.post()
                    .uri("/api/register")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(builder.build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            return parseAndAssertRecord(response, computedHash, null);
        } catch (HttpClientErrorException.Conflict conflict) {
            log.info("Node Service HTTP 409: Hash [{}] is already registered on chain. Fetching existing history.", computedHash);
            List<BlockchainRecord> history = getHistory(computedHash);
            if (!history.isEmpty()) {
                return history.get(history.size() - 1);
            }
            throw new ApiException(HttpStatus.CONFLICT, "HASH_ALREADY_REGISTERED", "Hash already registered on chain", conflict);
        } catch (Exception e) {
            log.error("Failed to register original media on blockchain service: {}", e.getMessage(), e);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "BLOCKCHAIN_SERVICE_ERROR", "Failed to communicate with blockchain service: " + e.getMessage(), e);
        }
    }

    @Override
    public BlockchainRecord registerVersion(String previousHash, byte[] fileBytes, String operation) {
        String computedHash = hashService.sha256(fileBytes);
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return "derived.jpg";
            }
        });
        if (previousHash != null) {
            builder.part("previousHash", previousHash);
        }
        if (operation != null && !operation.isBlank()) {
            builder.part("operation", operation);
        }

        try {
            Map<String, Object> response = longClient.post()
                    .uri("/api/version")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(builder.build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            return parseAndAssertRecord(response, computedHash, previousHash);
        } catch (HttpClientErrorException.NotFound notFound) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PARENT_NOT_REGISTERED", "Parent hash '" + previousHash + "' is not registered on chain", notFound);
        } catch (HttpClientErrorException.Conflict conflict) {
            log.info("Node Service HTTP 409: Version hash [{}] is already registered. Fetching existing history.", computedHash);
            List<BlockchainRecord> history = getHistory(computedHash);
            if (!history.isEmpty()) {
                return history.get(history.size() - 1);
            }
            throw new ApiException(HttpStatus.CONFLICT, "HASH_ALREADY_REGISTERED", "Hash already registered on chain", conflict);
        } catch (Exception e) {
            log.error("Failed to register version on blockchain service: {}", e.getMessage(), e);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "BLOCKCHAIN_SERVICE_ERROR", "Failed to communicate with blockchain service: " + e.getMessage(), e);
        }
    }

    @Override
    public BlockchainRecord verify(byte[] fileBytes) {
        String computedHash = hashService.sha256(fileBytes);
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return "verify.jpg";
            }
        });

        try {
            Map<String, Object> response = standardClient.post()
                    .uri("/api/verify")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(builder.build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            String status = response != null && response.get("status") != null ? response.get("status").toString() : "MISMATCH";
            String returnedHash = response != null && response.get("hash") != null ? response.get("hash").toString() : computedHash;

            if (!computedHash.equalsIgnoreCase(returnedHash)) {
                log.warn("Returned hash [{}] from Node service does not match Java-computed hash [{}]", returnedHash, computedHash);
            }

            return BlockchainRecord.builder()
                    .hash(computedHash)
                    .status(status)
                    .build();
        } catch (Exception e) {
            log.error("Failed to verify media on blockchain service: {}", e.getMessage(), e);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "BLOCKCHAIN_SERVICE_ERROR", "Failed to communicate with blockchain service for verification", e);
        }
    }

    @Override
    public List<BlockchainRecord> getHistory(String hash) {
        try {
            List<Map<String, Object>> response = standardClient.get()
                    .uri("/api/history/{hash}", hash)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            if (response == null) {
                return List.of();
            }

            return response.stream().map(map -> BlockchainRecord.builder()
                    .hash(map.get("hash") != null ? map.get("hash").toString() : null)
                    .previousHash(map.get("previousHash") != null ? map.get("previousHash").toString() : null)
                    .recordId(map.get("recordId") != null ? Long.parseLong(map.get("recordId").toString()) : null)
                    .txHash(map.get("txHash") != null ? map.get("txHash").toString() : null)
                    .explorerUrl(map.get("explorerUrl") != null ? map.get("explorerUrl").toString() : null)
                    .status("CONFIRMED")
                    .build()
            ).toList();
        } catch (HttpClientErrorException.NotFound notFound) {
            return List.of();
        } catch (Exception e) {
            log.error("Failed to fetch blockchain history for hash [{}]: {}", hash, e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "BLOCKCHAIN_SERVICE_ERROR", "Failed to fetch history from blockchain service", e);
        }
    }

    private BlockchainRecord parseAndAssertRecord(Map<String, Object> response, String computedHash, String expectedPreviousHash) {
        if (response == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "BLOCKCHAIN_RESPONSE_EMPTY", "Empty response from blockchain service");
        }

        String returnedHash = response.get("hash") != null ? response.get("hash").toString() : null;
        if (returnedHash != null && !computedHash.equalsIgnoreCase(returnedHash)) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "HASH_MISMATCH", "Blockchain returned hash " + returnedHash + " which does not match Java-computed hash " + computedHash);
        }

        Long recordId = response.get("recordId") != null ? Long.parseLong(response.get("recordId").toString()) : null;
        String txHash = response.get("txHash") != null ? response.get("txHash").toString() : null;
        String explorerUrl = response.get("explorerUrl") != null ? response.get("explorerUrl").toString() : null;
        String previousHash = response.get("previousHash") != null ? response.get("previousHash").toString() : expectedPreviousHash;

        return BlockchainRecord.builder()
                .hash(computedHash)
                .previousHash(previousHash)
                .recordId(recordId)
                .txHash(txHash)
                .explorerUrl(explorerUrl)
                .status("CONFIRMED")
                .build();
    }
}
