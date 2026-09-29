package com.mediaprovenance.blockchain;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.mediaprovenance.common.ApiException;
import com.mediaprovenance.config.AppProperties;
import com.mediaprovenance.hashing.HashService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
class HttpBlockchainClientTest {

    private static final WireMockServer wireMock = new WireMockServer(options().dynamicPort());

    static {
        wireMock.start();
    }

    @Autowired
    private BlockchainClient activeBlockchainClient;

    private HttpBlockchainClient client;
    private HashService hashService;
    private byte[] fileBytes;
    private String fileHash;

    @DynamicPropertySource
    static void blockchainProperties(DynamicPropertyRegistry registry) {
        registry.add("app.blockchain.mode", () -> "HTTP");
        registry.add("app.blockchain.service-url", () -> wireMock.baseUrl());
        registry.add("app.blockchain.connect-timeout-ms", () -> "500");
        registry.add("app.blockchain.read-timeout-standard-ms", () -> "500");
        registry.add("app.blockchain.read-timeout-long-ms", () -> "500");
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        hashService = new HashService();
        client = newHttpClient(500, 500);
        fileBytes = "real media bytes".getBytes(StandardCharsets.UTF_8);
        fileHash = hashService.sha256(fileBytes);
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @Test
    void httpModeActivatesHttpBlockchainClient() {
        assertInstanceOf(HttpBlockchainClient.class, activeBlockchainClient);
    }

    @Test
    void registerOriginalSuccessSendsExactMultipartFile() {
        wireMock.stubFor(post(urlEqualTo("/api/register"))
                .withMultipartRequestBody(aMultipart().withName("file").withBody(binaryEqualTo(fileBytes)))
                .withMultipartRequestBody(aMultipart().withName("operation").withBody(equalTo("ORIGINAL")))
                .willReturn(okJson("""
                        {"hash":"%s","recordId":7,"txHash":"0xtx","explorerUrl":"https://scan/tx"}
                        """.formatted(fileHash)).withStatus(201)));

        BlockchainRecord record = client.registerOriginal(fileBytes, "ORIGINAL");

        assertEquals(fileHash, record.getHash());
        assertEquals(7L, record.getRecordId());
        assertEquals("0xtx", record.getTxHash());
        assertEquals("https://scan/tx", record.getExplorerUrl());
        wireMock.verify(postRequestedFor(urlEqualTo("/api/register")));
    }

    @Test
    void registerOriginalHashMismatchThrowsHashMismatchApiException() {
        wireMock.stubFor(post(urlEqualTo("/api/register"))
                .willReturn(okJson("""
                        {"hash":"0x%s","recordId":7,"txHash":"0xtx","explorerUrl":"https://scan/tx"}
                        """.formatted("c".repeat(64))).withStatus(201)));

        ApiException exception = assertThrows(ApiException.class, () -> client.registerOriginal(fileBytes, "ORIGINAL"));

        assertEquals("HASH_MISMATCH", exception.getErrorCode());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatus());
    }

    @Test
    void registerVersionSuccessSendsPreviousHashAndParsesResponse() {
        String parentHash = "0x" + "d".repeat(64);
        wireMock.stubFor(post(urlEqualTo("/api/version"))
                .withMultipartRequestBody(aMultipart().withName("file").withBody(binaryEqualTo(fileBytes)))
                .withMultipartRequestBody(aMultipart().withName("previousHash").withBody(equalTo(parentHash)))
                .willReturn(okJson("""
                        {"hash":"%s","previousHash":"%s","recordId":8,"txHash":"0xv","explorerUrl":"https://scan/v"}
                        """.formatted(fileHash, parentHash)).withStatus(201)));

        BlockchainRecord record = client.registerVersion(parentHash, fileBytes, "CROP");

        assertEquals(fileHash, record.getHash());
        assertEquals(parentHash, record.getPreviousHash());
        assertEquals(8L, record.getRecordId());
        assertEquals("0xv", record.getTxHash());
    }

    @Test
    void registerVersionParentNotRegisteredIsNonRetryableApiException() {
        wireMock.stubFor(post(urlEqualTo("/api/version"))
                .willReturn(aResponse().withStatus(404).withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Parent hash not registered\"}")));

        ApiException exception = assertThrows(ApiException.class,
                () -> client.registerVersion("0x" + "d".repeat(64), fileBytes, "CROP"));

        assertEquals("PARENT_NOT_REGISTERED", exception.getErrorCode());
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    void duplicateOriginalFallsBackToLastHistoryEntry() {
        wireMock.stubFor(post(urlEqualTo("/api/register"))
                .willReturn(aResponse().withStatus(409)));
        wireMock.stubFor(get(urlPathEqualTo("/api/history/" + fileHash))
                .willReturn(okJson("""
                        [{"recordId":1,"mediaHash":"%s","previousHash":null,"operation":"ORIGINAL","timestamp":1,"creator":"0xa"},
                         {"recordId":2,"mediaHash":"%s","previousHash":"0xparent","operation":"CROP","timestamp":2,"creator":"0xb"}]
                        """.formatted(fileHash, fileHash))));

        BlockchainRecord record = client.registerOriginal(fileBytes, "ORIGINAL");

        assertEquals(2L, record.getRecordId());
        assertEquals("0xparent", record.getPreviousHash());
        wireMock.verify(getRequestedFor(urlPathEqualTo("/api/history/" + fileHash)));
    }

    @Test
    void duplicateVersionFallsBackToLastHistoryEntry() {
        String parentHash = "0x" + "d".repeat(64);
        wireMock.stubFor(post(urlEqualTo("/api/version"))
            .willReturn(aResponse().withStatus(409)));
        wireMock.stubFor(get(urlPathEqualTo("/api/history/" + fileHash))
            .willReturn(okJson("[{\"recordId\":3,\"mediaHash\":\"" + fileHash + "\",\"operation\":\"CROP\"}]")));

        BlockchainRecord record = client.registerVersion(parentHash, fileBytes, "CROP");

        assertEquals(3L, record.getRecordId());
        assertEquals(fileHash, record.getHash());
    }

    @Test
    void verifyMapsStatusAndKeepsJavaComputedHashWhenNodeReturnsWrongHash() {
        wireMock.stubFor(post(urlEqualTo("/api/verify"))
                .willReturn(okJson("{\"status\":\"VERIFIED\",\"hash\":\"0x" + "e".repeat(64) + "\",\"record\":null}")));

        BlockchainRecord record = client.verify(fileBytes);

        assertEquals("VERIFIED", record.getStatus());
        assertEquals(fileHash, record.getHash());
    }

    @Test
    void getHistoryMapsDocumentedMediaHashOrderingAnd404ToEmpty() {
        wireMock.stubFor(get(urlPathEqualTo("/api/history/" + fileHash))
                .willReturn(okJson("""
                        [{"recordId":1,"mediaHash":"0xone","previousHash":null,"operation":"ORIGINAL","timestamp":1,"creator":"0xa"},
                         {"recordId":2,"mediaHash":"0xtwo","previousHash":"0xone","operation":"CROP","timestamp":2,"creator":"0xb"}]
                        """)));

        List<BlockchainRecord> history = client.getHistory(fileHash);

        assertEquals(List.of(1L, 2L), history.stream().map(BlockchainRecord::getRecordId).toList());
        assertEquals("0xone", history.get(0).getHash());
        assertEquals("0xone", history.get(1).getPreviousHash());

        wireMock.resetAll();
        wireMock.stubFor(get(urlPathEqualTo("/api/history/" + fileHash)).willReturn(aResponse().withStatus(404)));
        assertTrue(client.getHistory(fileHash).isEmpty());
    }

    @Test
    void registerTimeoutBecomesServiceUnavailableWithTimeoutCause() {
        wireMock.stubFor(post(urlEqualTo("/api/register"))
                .willReturn(aResponse().withStatus(201).withFixedDelay(1500)
                        .withBody("{\"hash\":\"" + fileHash + "\"}")));
        HttpBlockchainClient shortTimeoutClient = newHttpClient(100, 100);

        ApiException exception = assertThrows(ApiException.class,
                () -> shortTimeoutClient.registerOriginal(fileBytes, "ORIGINAL"));

        assertEquals("BLOCKCHAIN_SERVICE_ERROR", exception.getErrorCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
        assertNotNull(exception.getCause());
    }

    private HttpBlockchainClient newHttpClient(int standardTimeout, int longTimeout) {
        SimpleClientHttpRequestFactory standardFactory = new SimpleClientHttpRequestFactory();
        standardFactory.setConnectTimeout(Duration.ofMillis(standardTimeout));
        standardFactory.setReadTimeout(Duration.ofMillis(standardTimeout));
        SimpleClientHttpRequestFactory longFactory = new SimpleClientHttpRequestFactory();
        longFactory.setConnectTimeout(Duration.ofMillis(longTimeout));
        longFactory.setReadTimeout(Duration.ofMillis(longTimeout));
        return new HttpBlockchainClient(
                RestClient.builder().baseUrl(wireMock.baseUrl()).requestFactory(longFactory).build(),
                RestClient.builder().baseUrl(wireMock.baseUrl()).requestFactory(standardFactory).build(),
                hashService);
    }
}