package com.mediaprovenance.verification;

import com.mediaprovenance.blockchain.BlockchainClient;
import com.mediaprovenance.blockchain.BlockchainRecord;
import com.mediaprovenance.hashing.HashService;
import com.mediaprovenance.media.MediaVersion;
import com.mediaprovenance.media.MediaVersionRepository;
import com.mediaprovenance.provenance.ProvenanceRecord;
import com.mediaprovenance.provenance.ProvenanceRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Isolated verification logic. P1 owns the final rules; changes must be limited to this class only.
 *
 * Verification semantics:
 * 1. mediaId given and hash matches none of that media's versions -> MISMATCH (demo "modified file" path)
 * 2. hash matches a DB version and its chain record is CONFIRMED -> VERIFIED
 * 3. no mediaId and hash is unknown everywhere -> NOT_FOUND
 */
@Service
public class VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);

    private final HashService hashService;
    private final MediaVersionRepository versionRepository;
    private final ProvenanceRecordRepository provenanceRepository;
    private final BlockchainClient blockchainClient;

    public VerificationService(HashService hashService,
                               MediaVersionRepository versionRepository,
                               ProvenanceRecordRepository provenanceRepository,
                               BlockchainClient blockchainClient) {
        this.hashService = hashService;
        this.versionRepository = versionRepository;
        this.provenanceRepository = provenanceRepository;
        this.blockchainClient = blockchainClient;
    }

    public VerificationResponse verify(byte[] fileBytes, UUID mediaId) {
        String computedHash = hashService.sha256(fileBytes);
        log.info("Verifying file with computed hash [{}], mediaId [{}]", computedHash, mediaId);

        // Case 1: mediaId supplied — check if hash belongs to this media's versions
        if (mediaId != null) {
            List<MediaVersion> mediaVersions = versionRepository.findByMediaAssetIdOrderByCreatedAtAsc(mediaId);
            boolean hashBelongsToMedia = mediaVersions.stream()
                    .anyMatch(v -> v.getSha256Hash().equalsIgnoreCase(computedHash));

            if (!hashBelongsToMedia) {
                log.info("MISMATCH: Hash [{}] does not match any version of media [{}]", computedHash, mediaId);
                return VerificationResponse.builder()
                        .status("MISMATCH")
                        .hash(computedHash)
                        .mediaId(mediaId)
                        .message("The file has been modified or does not match the registered media.")
                        .build();
            }
        }

        // Case 2: Look up hash in DB
        Optional<MediaVersion> matchedVersion = versionRepository.findBySha256Hash(computedHash);
        if (matchedVersion.isPresent()) {
            MediaVersion version = matchedVersion.get();
            Optional<ProvenanceRecord> provenanceOpt = provenanceRepository.findByMediaVersionId(version.getId());

            if (provenanceOpt.isPresent()) {
                ProvenanceRecord provenance = provenanceOpt.get();
                String chainStatus = provenance.getChainStatus();

                if ("CONFIRMED".equals(chainStatus)) {
                    // Re-verify on chain to ensure the record is still valid
                    try {
                        BlockchainRecord chainRecord = blockchainClient.verify(fileBytes);
                        if ("CONFIRMED".equalsIgnoreCase(chainRecord.getStatus()) || "VERIFIED".equalsIgnoreCase(chainRecord.getStatus())) {
                            log.info("VERIFIED: Hash [{}] confirmed on chain", computedHash);
                            return VerificationResponse.builder()
                                    .status("VERIFIED")
                                    .hash(computedHash)
                                    .mediaId(version.getMediaAsset().getId())
                                    .matchedVersionId(version.getId().toString())
                                    .chainStatus("CONFIRMED")
                                    .txHash(provenance.getTxHash())
                                    .explorerUrl(provenance.getExplorerUrl())
                                    .message("File is authentic and verified on the blockchain.")
                                    .source("CHAIN_CONFIRMED")
                                    .build();
                        }
                    } catch (Exception e) {
                        log.warn("Chain verification failed for hash [{}], falling back to DB record: {}", computedHash, e.getMessage());
                        return VerificationResponse.builder()
                                .status("VERIFIED")
                                .hash(computedHash)
                                .mediaId(version.getMediaAsset().getId())
                                .matchedVersionId(version.getId().toString())
                                .chainStatus("CONFIRMED")
                                .txHash(provenance.getTxHash())
                                .explorerUrl(provenance.getExplorerUrl())
                                .message("File verified via DB confirmed record (chain service temporarily unavailable).")
                                .source("DB_CONFIRMED_RECORD")
                                .build();
                    }
                } else if ("PENDING".equals(chainStatus)) {
                    log.info("PENDING: Hash [{}] known but chain not yet confirmed", computedHash);
                    return VerificationResponse.builder()
                            .status("PENDING")
                            .hash(computedHash)
                            .mediaId(version.getMediaAsset().getId())
                            .matchedVersionId(version.getId().toString())
                            .chainStatus("PENDING")
                            .message("Provenance record is pending blockchain confirmation. Retry in a moment.")
                            .build();
                }
            }
        }

        // Case 3: no mediaId, hash unknown
        if (mediaId == null) {
            log.info("NOT_FOUND: Hash [{}] is unknown in DB and chain", computedHash);
            return VerificationResponse.builder()
                    .status("NOT_FOUND")
                    .hash(computedHash)
                    .message("No matching record found for this file.")
                    .build();
        }

        // fallback: mediaId given but hash not found globally
        return VerificationResponse.builder()
                .status("NOT_FOUND")
                .hash(computedHash)
                .mediaId(mediaId)
                .message("No matching record found for this file.")
                .build();
    }
}
