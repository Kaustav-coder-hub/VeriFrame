package com.mediaprovenance.blockchain;

import com.mediaprovenance.cloudinary.CloudinaryGateway;
import com.mediaprovenance.hashing.HashService;
import com.mediaprovenance.media.MediaAsset;
import com.mediaprovenance.media.MediaVersion;
import com.mediaprovenance.provenance.ProvenanceRecord;
import com.mediaprovenance.provenance.ProvenanceRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PendingChainRecoveryTest {

        private static final DefaultApplicationArguments ARGUMENTS = new DefaultApplicationArguments();

    @Test
        void matchingDownloadedBytesAreRegistered() {
        ProvenanceRecordRepository repository = mock(ProvenanceRecordRepository.class);
        ChainRegistrationService registrationService = mock(ChainRegistrationService.class);
                CloudinaryGateway cloudinaryGateway = mock(CloudinaryGateway.class);
                HashService hashService = mock(HashService.class);
                byte[] downloadedBytes = new byte[] {1, 2, 3};
                String hash = "0x" + "b".repeat(64);
                ProvenanceRecord provenance = pendingRecord(hash, "https://res.cloudinary.com/example/original.jpg");
        when(repository.findByChainStatus("PENDING")).thenReturn(List.of(provenance));
                when(cloudinaryGateway.downloadDerivedBytes(provenance.getMediaVersion().getCloudinaryUrl())).thenReturn(downloadedBytes);
                when(hashService.sha256(downloadedBytes)).thenReturn(hash);

                recovery(repository, registrationService, cloudinaryGateway, hashService).run(ARGUMENTS);

                verify(registrationService).registerAsync(provenance.getId(), downloadedBytes, "CROP", "0xparent");
                verify(repository, never()).save(any());
        }

        @Test
        void mismatchedDownloadedBytesAreMarkedFailedWithoutRegistration() {
                ProvenanceRecordRepository repository = mock(ProvenanceRecordRepository.class);
                ChainRegistrationService registrationService = mock(ChainRegistrationService.class);
                CloudinaryGateway cloudinaryGateway = mock(CloudinaryGateway.class);
                HashService hashService = mock(HashService.class);
                byte[] downloadedBytes = new byte[] {4, 5, 6};
                ProvenanceRecord provenance = pendingRecord("0x" + "b".repeat(64), "https://res.cloudinary.com/example/derived.jpg");
                when(repository.findByChainStatus("PENDING")).thenReturn(List.of(provenance));
                when(cloudinaryGateway.downloadDerivedBytes(anyString())).thenReturn(downloadedBytes);
                when(hashService.sha256(downloadedBytes)).thenReturn("0x" + "c".repeat(64));

                recovery(repository, registrationService, cloudinaryGateway, hashService).run(ARGUMENTS);

                verify(registrationService, never()).registerAsync(any(), any(), any(), any());
                verify(repository).save(provenance);
                assertEquals("FAILED", provenance.getChainStatus());
                assertEquals("recovery aborted: re-downloaded bytes do not match stored hash", provenance.getErrorMessage());
        }

        @Test
        void downloadFailureDoesNotBlockHealthyRecord() {
                ProvenanceRecordRepository repository = mock(ProvenanceRecordRepository.class);
                ChainRegistrationService registrationService = mock(ChainRegistrationService.class);
                CloudinaryGateway cloudinaryGateway = mock(CloudinaryGateway.class);
                HashService hashService = mock(HashService.class);
                byte[] downloadedBytes = new byte[] {7, 8, 9};
                ProvenanceRecord failed = pendingRecord("0x" + "b".repeat(64), "https://res.cloudinary.com/example/bad.jpg");
                ProvenanceRecord healthy = pendingRecord("0x" + "d".repeat(64), "https://res.cloudinary.com/example/good.jpg");
                when(repository.findByChainStatus("PENDING")).thenReturn(List.of(failed, healthy));
                when(cloudinaryGateway.downloadDerivedBytes(failed.getMediaVersion().getCloudinaryUrl()))
                                .thenThrow(new RuntimeException("Cloudinary unavailable"));
                when(cloudinaryGateway.downloadDerivedBytes(healthy.getMediaVersion().getCloudinaryUrl())).thenReturn(downloadedBytes);
                when(hashService.sha256(downloadedBytes)).thenReturn(healthy.getMediaVersion().getSha256Hash());

                recovery(repository, registrationService, cloudinaryGateway, hashService).run(ARGUMENTS);

                verify(registrationService).registerAsync(healthy.getId(), downloadedBytes, "CROP", "0xparent");
                verify(repository).save(failed);
        }

        @Test
        void emptyPendingBatchDoesNotTouchRecoveryDependencies() {
                ProvenanceRecordRepository repository = mock(ProvenanceRecordRepository.class);
                ChainRegistrationService registrationService = mock(ChainRegistrationService.class);
                CloudinaryGateway cloudinaryGateway = mock(CloudinaryGateway.class);
                HashService hashService = mock(HashService.class);
                when(repository.findByChainStatus("PENDING")).thenReturn(List.of());

                recovery(repository, registrationService, cloudinaryGateway, hashService).run(ARGUMENTS);

                verifyNoInteractions(registrationService, cloudinaryGateway, hashService);
        }

        private PendingChainRecovery recovery(ProvenanceRecordRepository repository,
                                                                                  ChainRegistrationService registrationService,
                                                                                  CloudinaryGateway cloudinaryGateway,
                                                                                  HashService hashService) {
                return new PendingChainRecovery(repository, registrationService, cloudinaryGateway, hashService);
        }

        private ProvenanceRecord pendingRecord(String hash, String cloudinaryUrl) {
                MediaVersion version = MediaVersion.builder()
                                .id(UUID.randomUUID())
                                .mediaAsset(MediaAsset.builder().id(UUID.randomUUID()).build())
                                .sha256Hash(hash)
                                .cloudinaryUrl(cloudinaryUrl)
                                .build();
                return ProvenanceRecord.builder()
                                .id(UUID.randomUUID())
                                .mediaVersion(version)
                                .operation("CROP")
                                .previousHash("0xparent")
                                .chainStatus("PENDING")
                                .build();
    }
}