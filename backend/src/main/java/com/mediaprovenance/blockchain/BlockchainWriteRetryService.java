package com.mediaprovenance.blockchain;

import com.mediaprovenance.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

@Service
public class BlockchainWriteRetryService {

    private final BlockchainClient blockchainClient;

    public BlockchainWriteRetryService(BlockchainClient blockchainClient) {
        this.blockchainClient = blockchainClient;
    }

    @Retryable(
            retryFor = TransientBlockchainException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2, maxDelay = 15000)
    )
    public BlockchainRecord register(byte[] fileBytes, String operation, String previousHash) {
        try {
            if (previousHash == null) {
                return blockchainClient.registerOriginal(fileBytes, operation);
            }
            return blockchainClient.registerVersion(previousHash, fileBytes, operation);
        } catch (ApiException ex) {
            if (isRetryable(ex)) {
                throw new TransientBlockchainException(ex.getMessage(), ex);
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (isTimeout(ex)) {
                throw new TransientBlockchainException(ex.getMessage(), ex);
            }
            throw ex;
        }
    }

    private boolean isRetryable(ApiException ex) {
        HttpStatus status = ex.getStatus();
        return status.is5xxServerError() || status == HttpStatus.SERVICE_UNAVAILABLE;
    }

    private boolean isTimeout(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof TimeoutException || current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return error instanceof ResourceAccessException;
    }
}