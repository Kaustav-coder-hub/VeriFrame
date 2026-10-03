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
 * Handles asynchronous blockchain registration.
 *
 * MediaService is responsible for triggering this service after its
 * database transaction has committed.
 *
 * Keeping @Async in a separate Spring bean allows Spring's AOP proxy
 * to execute the method asynchronously.
 */
@Service
public class ChainRegistrationService {

    private static final Logger log =
            LoggerFactory.getLogger(ChainRegistrationService.class);

    private final ProvenanceRecordRepository provenanceRepository;
    private final BlockchainWriteRetryService blockchainWriteRetryService;

    public ChainRegistrationService(
            ProvenanceRecordRepository provenanceRepository,
            BlockchainWriteRetryService blockchainWriteRetryService) {

        this.provenanceRepository = provenanceRepository;
        this.blockchainWriteRetryService = blockchainWriteRetryService;
    }

    /**
     * Register a media hash on the blockchain asynchronously.
     *
     * This method is called by MediaService after the database transaction
     * has committed, so the ProvenanceRecord should already exist.
     */
    @Async
    public void registerAsync(
            UUID provenanceId,
            byte[] fileBytes,
            String operation,
            String previousHash) {

        ProvenanceRecord provenance =
                provenanceRepository.findById(provenanceId).orElse(null);

        if (provenance == null) {
            log.error(
                    "Cannot register on chain: ProvenanceRecord not found [{}]",
                    provenanceId
            );
            return;
        }

        try {

            BlockchainRecord chainRecord =
                    blockchainWriteRetryService.register(
                            fileBytes,
                            operation,
                            previousHash
                    );

            provenance.setChainStatus("CONFIRMED");
            provenance.setTxHash(chainRecord.getTxHash());
            provenance.setRecordId(chainRecord.getRecordId());
            provenance.setExplorerUrl(chainRecord.getExplorerUrl());
            provenance.setConfirmedAt(Instant.now());
            provenance.setAttempts(provenance.getAttempts() + 1);

            provenanceRepository.save(provenance);

            log.info(
                    "Blockchain registration CONFIRMED for provenance [{}], tx [{}]",
                    provenanceId,
                    chainRecord.getTxHash()
            );

        } catch (Exception e) {

            log.error(
                    "Chain registration FAILED for provenance [{}]: {}",
                    provenanceId,
                    e.getMessage(),
                    e
            );

            provenance.setChainStatus("FAILED");
            provenance.setErrorMessage(
                    truncate(e.getMessage(), 15000)
            );
            provenance.setAttempts(
                    provenance.getAttempts() + 1
            );

            provenanceRepository.save(provenance);
        }
    }

    private String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen
                ? s.substring(0, maxLen)
                : s;
    }
}