package com.mediaprovenance.blockchain;

import com.mediaprovenance.provenance.ProvenanceRecord;
import com.mediaprovenance.provenance.ProvenanceRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Extracted from MediaService so Spring's AOP proxy can intercept the @Async annotation.
 * Self-invocation within the same bean bypasses the proxy and @Async becomes a no-op.
 */
@Service
public class ChainRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(ChainRegistrationService.class);

    private final ProvenanceRecordRepository provenanceRepository;
    private final BlockchainWriteRetryService blockchainWriteRetryService;

    public ChainRegistrationService(ProvenanceRecordRepository provenanceRepository,
                                    BlockchainWriteRetryService blockchainWriteRetryService) {
        this.provenanceRepository = provenanceRepository;
        this.blockchainWriteRetryService = blockchainWriteRetryService;
    }

    /**
     * Register a hash on the blockchain asynchronously.
     * Called after the upload/transform transaction has committed, so the ProvenanceRecord is already persisted.
     * In MOCK mode this completes instantly; in HTTP mode it may block for up to 120 s.
     */
    @Async
    public void registerAsync(UUID provenanceId, byte[] fileBytes, String operation, String previousHash) {
        ProvenanceRecord provenance = provenanceRepository.findById(provenanceId).orElse(null);
        if (provenance == null) {
            log.error("Cannot register on chain: ProvenanceRecord not found [{}]", provenanceId);
            return;
        }

        try {
            BlockchainRecord chainRecord = blockchainWriteRetryService.register(fileBytes, operation, previousHash);

            provenance.setChainStatus("CONFIRMED");
            provenance.setTxHash(chainRecord.getTxHash());
            provenance.setRecordId(chainRecord.getRecordId());
            provenance.setExplorerUrl(chainRecord.getExplorerUrl());
            provenance.setConfirmedAt(Instant.now());
            provenance.setAttempts(provenance.getAttempts() + 1);
            provenanceRepository.save(provenance);
            log.info("Chain registration CONFIRMED for provenance [{}], txHash [{}]", provenanceId, chainRecord.getTxHash());
        } catch (Exception e) {
            log.error("Chain registration FAILED for provenance [{}]: {}", provenanceId, e.getMessage());
            provenance.setChainStatus("FAILED");
            provenance.setErrorMessage(truncate(e.getMessage(), 15000));
            provenance.setAttempts(provenance.getAttempts() + 1);
            provenanceRepository.save(provenance);
        }
    }

    private String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
