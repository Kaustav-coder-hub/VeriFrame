package com.mediaprovenance.blockchain;

import com.mediaprovenance.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(BlockchainWriteRetryServiceTest.TestConfig.class)
class BlockchainWriteRetryServiceTest {

    @Autowired
    private BlockchainWriteRetryService retryService;

    @Autowired
    private BlockchainClient blockchainClient;

    @Test
    void serviceUnavailableRetriesAndSucceedsOnSecondAttempt() {
        BlockchainRecord record = BlockchainRecord.builder().status("CONFIRMED").build();
        when(blockchainClient.registerOriginal(any(), eq("NONE")))
                .thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "TEMPORARY", "temporary"))
                .thenReturn(record);

        assertEquals(record, retryService.register(new byte[] {1}, "NONE", null));
        verify(blockchainClient, times(2)).registerOriginal(any(), eq("NONE"));
    }

    @Test
    void notFoundDoesNotRetry() {
        when(blockchainClient.registerVersion(any(), any(), eq("CROP")))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "PARENT_NOT_REGISTERED", "missing parent"));

        assertThrows(ApiException.class, () -> retryService.register(new byte[] {1}, "CROP", "parent"));
        verify(blockchainClient, times(1)).registerVersion(any(), any(), eq("CROP"));
    }

    @Configuration
    @EnableRetry
    static class TestConfig {
        @Bean
        BlockchainClient blockchainClient() {
            return mock(BlockchainClient.class);
        }

        @Bean
        BlockchainWriteRetryService blockchainWriteRetryService(BlockchainClient blockchainClient) {
            return new BlockchainWriteRetryService(blockchainClient);
        }
    }
}