package com.mediaprovenance.blockchain;

import com.mediaprovenance.cloudinary.CloudinaryGateway;
import com.mediaprovenance.hashing.HashService;
import com.mediaprovenance.media.MediaVersion;
import com.mediaprovenance.provenance.ProvenanceRecord;
import com.mediaprovenance.provenance.ProvenanceRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PendingChainRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PendingChainRecovery.class);

    private final ProvenanceRecordRepository provenanceRepository;
    private final ChainRegistrationService chainRegistrationService;
    private final CloudinaryGateway cloudinaryGateway;
    private final HashService hashService;

    public PendingChainRecovery(ProvenanceRecordRepository provenanceRepository,
                                ChainRegistrationService chainRegistrationService,
                                CloudinaryGateway cloudinaryGateway,
                                HashService hashService) {
        this.provenanceRepository = provenanceRepository;
        this.chainRegistrationService = chainRegistrationService;
        this.cloudinaryGateway = cloudinaryGateway;
        this.hashService = hashService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<ProvenanceRecord> pending;
        try {
            pending = provenanceRepository.findByChainStatus("PENDING");
        } catch (Exception e) {
            log.error("Pending chain recovery could not load pending records: {}", e.getMessage(), e);
            log.info("Re-drove 0 pending chain registrations (attempted: 0, skipped due to hash mismatch/download failure: 0)");
            return;
        }
        int attempted = 0;
        int skipped = 0;

        for (ProvenanceRecord provenance : pending) {
            try {
                MediaVersion version = provenance.getMediaVersion();
                byte[] mediaBytes = cloudinaryGateway.downloadDerivedBytes(version.getCloudinaryUrl());
                String downloadedHash = hashService.sha256(mediaBytes);

                if (!version.getSha256Hash().equalsIgnoreCase(downloadedHash)) {
                    log.error("Recovery aborted for provenance [{}], media version [{}]: re-downloaded bytes do not match stored hash",
                            provenance.getId(), version.getId());
                    markFailed(provenance, "recovery aborted: re-downloaded bytes do not match stored hash");
                    skipped++;
                    continue;
                }

                chainRegistrationService.registerAsync(
                        provenance.getId(),
                        mediaBytes,
                        provenance.getOperation(),
                        provenance.getPreviousHash());
                attempted++;
            } catch (Exception e) {
                skipped++;
                log.error("Pending chain recovery failed for provenance [{}]: {}", provenance.getId(), e.getMessage(), e);
                markFailed(provenance, "recovery failed: " + truncate(e.getMessage(), 15000));
            }
        }

        log.info("Re-drove {} pending chain registrations (attempted: {}, skipped due to hash mismatch/download failure: {})",
                pending.size(), attempted, skipped);
    }

    private void markFailed(ProvenanceRecord provenance, String errorMessage) {
        provenance.setChainStatus("FAILED");
        provenance.setErrorMessage(errorMessage);
        try {
            provenanceRepository.save(provenance);
        } catch (Exception e) {
            log.error("Could not persist FAILED recovery status for provenance [{}]: {}",
                    provenance.getId(), e.getMessage(), e);
        }
    }

    private String truncate(String message, int maxLength) {
        return message != null && message.length() > maxLength ? message.substring(0, maxLength) : message;
    }
}