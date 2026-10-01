package com.mediaprovenance.verification;

import com.mediaprovenance.blockchain.BlockchainClient;
import com.mediaprovenance.hashing.HashService;
import com.mediaprovenance.media.MediaAsset;
import com.mediaprovenance.media.MediaVersion;
import com.mediaprovenance.media.MediaVersionRepository;
import com.mediaprovenance.provenance.ProvenanceRecord;
import com.mediaprovenance.provenance.ProvenanceRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class VerificationServiceTest {

    private static final String HASH = "0x" + "a".repeat(64);
    private final HashService hashService = mock(HashService.class);
    private final MediaVersionRepository versionRepository = mock(MediaVersionRepository.class);
    private final ProvenanceRecordRepository provenanceRepository = mock(ProvenanceRecordRepository.class);
    private final BlockchainClient blockchainClient = mock(BlockchainClient.class);
    private final UUID mediaId = UUID.randomUUID();
    private final MediaVersion version = MediaVersion.builder()
            .id(UUID.randomUUID())
            .mediaAsset(MediaAsset.builder().id(mediaId).build())
            .sha256Hash(HASH)
            .build();
    private final ProvenanceRecord failedProvenance = ProvenanceRecord.builder()
            .id(UUID.randomUUID())
            .mediaVersion(version)
            .chainStatus("FAILED")
            .errorMessage("temporary 503")
            .build();
    private VerificationService verificationService;

    @BeforeEach
    void setUp() {
        verificationService = new VerificationService(hashService, versionRepository, provenanceRepository, blockchainClient);
        when(hashService.sha256(new byte[] {1})).thenReturn(HASH);
        when(versionRepository.findBySha256Hash(HASH)).thenReturn(Optional.of(version));
        when(provenanceRepository.findByMediaVersionId(version.getId())).thenReturn(Optional.of(failedProvenance));
    }

    @Test
    void failedProvenanceWithMediaId_ReturnsDistinctRetryableOutcome() {
        when(versionRepository.findByMediaAssetIdOrderByCreatedAtAsc(mediaId)).thenReturn(List.of(version));

        VerificationResponse response = verificationService.verify(new byte[] {1}, mediaId);

        assertEquals("PROVENANCE_FAILED", response.getStatus());
        assertEquals("FAILED", response.getChainStatus());
        assertTrue(response.getMessage().contains("temporary 503"));
    }

    @Test
    void failedProvenanceWithoutMediaId_ReturnsDistinctRetryableOutcome() {
        VerificationResponse response = verificationService.verify(new byte[] {1}, null);

        assertEquals("PROVENANCE_FAILED", response.getStatus());
        assertEquals(version.getId().toString(), response.getMatchedVersionId());
    }
}