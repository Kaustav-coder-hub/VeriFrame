package com.mediaprovenance.media;

import com.mediaprovenance.ai.AiResultRepository;
import com.mediaprovenance.blockchain.ChainRegistrationService;
import com.mediaprovenance.cloudinary.CloudinaryGateway;
import com.mediaprovenance.cloudinary.CloudinaryUploadResult;
import com.mediaprovenance.hashing.HashService;
import com.mediaprovenance.provenance.ProvenanceRecordRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MediaServiceTransformTest {

    @Test
    void transformUsesDerivedBytesFromGatewayWithoutSecondDownload() {
        UUID mediaId = UUID.randomUUID();
        MediaAsset asset = MediaAsset.builder().id(mediaId).build();
        MediaVersion source = MediaVersion.builder()
                .id(UUID.randomUUID())
                .mediaAsset(asset)
                .sha256Hash("0x" + "a".repeat(64))
                .cloudinaryPublicId("source")
                .build();
        byte[] derivedBytes = new byte[] {1, 2, 3};
        CloudinaryUploadResult result = CloudinaryUploadResult.builder()
                .publicId("derived")
                .assetId("asset")
                .secureUrl("https://res.cloudinary.com/example/derived.jpg")
                .derivedBytes(derivedBytes)
                .build();

        MediaAssetRepository assetRepository = mock(MediaAssetRepository.class);
        MediaVersionRepository versionRepository = mock(MediaVersionRepository.class);
        AiResultRepository aiResultRepository = mock(AiResultRepository.class);
        ProvenanceRecordRepository provenanceRepository = mock(ProvenanceRecordRepository.class);
        CloudinaryGateway cloudinaryGateway = mock(CloudinaryGateway.class);
        HashService hashService = mock(HashService.class);
        ChainRegistrationService chainRegistrationService = mock(ChainRegistrationService.class);
        when(assetRepository.findById(mediaId)).thenReturn(Optional.of(asset));
        when(versionRepository.findByMediaAssetIdOrderByCreatedAtAsc(mediaId)).thenReturn(List.of(source));
        when(cloudinaryGateway.transformAndUpload("source", "CROP", mediaId.toString())).thenReturn(result);
        when(hashService.sha256(derivedBytes)).thenReturn("0x" + "b".repeat(64));
        when(versionRepository.findBySha256Hash(anyString())).thenReturn(Optional.empty());

        MediaService service = new MediaService(assetRepository, versionRepository, aiResultRepository,
                provenanceRepository, cloudinaryGateway, hashService, chainRegistrationService);

        service.transform(mediaId, "CROP", null);

        verify(cloudinaryGateway, never()).downloadDerivedBytes(anyString());
        verify(hashService).sha256(derivedBytes);
    }
}