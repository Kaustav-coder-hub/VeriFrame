package com.mediaprovenance.media;

import com.mediaprovenance.ai.AiResult;
import com.mediaprovenance.ai.AiResultRepository;
import com.mediaprovenance.blockchain.BlockchainClient;
import com.mediaprovenance.blockchain.BlockchainRecord;
import com.mediaprovenance.cloudinary.CloudinaryAiResultDto;
import com.mediaprovenance.cloudinary.CloudinaryGateway;
import com.mediaprovenance.cloudinary.CloudinaryUploadResult;
import com.mediaprovenance.common.ApiException;
import com.mediaprovenance.hashing.HashService;
import com.mediaprovenance.provenance.ProvenanceRecord;
import com.mediaprovenance.provenance.ProvenanceRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    private final MediaAssetRepository assetRepository;
    private final MediaVersionRepository versionRepository;
    private final AiResultRepository aiResultRepository;
    private final ProvenanceRecordRepository provenanceRepository;
    private final CloudinaryGateway cloudinaryGateway;
    private final HashService hashService;
    private final BlockchainClient blockchainClient;

    public MediaService(MediaAssetRepository assetRepository,
                        MediaVersionRepository versionRepository,
                        AiResultRepository aiResultRepository,
                        ProvenanceRecordRepository provenanceRepository,
                        CloudinaryGateway cloudinaryGateway,
                        HashService hashService,
                        BlockchainClient blockchainClient) {
        this.assetRepository = assetRepository;
        this.versionRepository = versionRepository;
        this.aiResultRepository = aiResultRepository;
        this.provenanceRepository = provenanceRepository;
        this.cloudinaryGateway = cloudinaryGateway;
        this.hashService = hashService;
        this.blockchainClient = blockchainClient;
    }

    /**
     * Upload a new image. Computes the SHA-256 hash of the original bytes BEFORE sending to Cloudinary.
     * If a version with the same hash already exists, returns the existing passport with duplicate=true.
     * This supports rehearsal re-uploads of the same demo image without creating duplicates.
     */
    @Transactional
    public MediaUploadResponse upload(byte[] fileBytes, String originalFilename) {
        // 1. Hash the original bytes first, before any Cloudinary processing
        String sha256Hash = hashService.sha256(fileBytes);
        log.info("Upload requested for file [{}] with hash [{}]", originalFilename, sha256Hash);

        // 2. Deduplication: if hash already exists, return existing passport
        Optional<MediaVersion> existing = versionRepository.findBySha256Hash(sha256Hash);
        if (existing.isPresent()) {
            MediaVersion existingVersion = existing.get();
            log.info("Duplicate upload detected: hash [{}] already exists as version [{}]", sha256Hash, existingVersion.getId());
            MediaPassportDto passport = buildPassport(existingVersion.getMediaAsset().getId());
            return MediaUploadResponse.builder()
                    .mediaId(existingVersion.getMediaAsset().getId())
                    .originalVersion(toVersionDto(existingVersion))
                    .duplicate(true)
                    .build();
        }

        // 3. Upload to Cloudinary
        UUID mediaId = UUID.randomUUID();
        CloudinaryUploadResult uploadResult = cloudinaryGateway.upload(fileBytes, originalFilename, mediaId.toString());

        // 4. AI tagging (graceful: never fails the pipeline)
        CloudinaryAiResultDto aiResultDto = cloudinaryGateway.analyzeAi(uploadResult.getPublicId());

        // 5. Build optimized URL for display only (never used for hashing)
        String optimizedUrl = cloudinaryGateway.buildOptimizedUrl(uploadResult.getPublicId());

        // 6. Persist MediaAsset
        MediaAsset asset = MediaAsset.builder()
                .id(mediaId)
                .cloudinaryPublicId(uploadResult.getPublicId())
                .cloudinaryAssetId(uploadResult.getAssetId())
                .originalFilename(sanitizeFilename(originalFilename))
                .mimeType(detectMimeType(fileBytes))
                .createdAt(Instant.now())
                .build();
        assetRepository.save(asset);

        // 7. Persist MediaVersion (ORIGINAL)
        UUID versionId = UUID.randomUUID();
        MediaVersion version = MediaVersion.builder()
                .id(versionId)
                .mediaAsset(asset)
                .versionType("ORIGINAL")
                .operation("NONE")
                .sha256Hash(sha256Hash)
                .cloudinaryPublicId(uploadResult.getPublicId())
                .cloudinaryUrl(uploadResult.getSecureUrl())
                .optimizedUrl(optimizedUrl)
                .width(uploadResult.getWidth())
                .height(uploadResult.getHeight())
                .bytes(uploadResult.getBytes())
                .createdAt(Instant.now())
                .build();
        versionRepository.save(version);

        // 8. Persist AiResult
        AiResult aiResult = AiResult.builder()
                .id(UUID.randomUUID())
                .mediaVersion(version)
                .provider(aiResultDto.getProvider())
                .tagsJson(aiResultDto.getTagsJson())
                .rawJson(aiResultDto.getRawJson())
                .createdAt(Instant.now())
                .build();
        aiResultRepository.save(aiResult);

        // 9. Create PENDING provenance record
        ProvenanceRecord provenance = ProvenanceRecord.builder()
                .id(UUID.randomUUID())
                .mediaVersion(version)
                .operation("NONE")
                .cloudinaryAssetId(uploadResult.getAssetId())
                .chainStatus("PENDING")
                .createdAt(Instant.now())
                .build();
        provenanceRepository.save(provenance);

        // 10. Register on chain asynchronously
        registerOnChainAsync(provenance.getId(), fileBytes, "NONE", null);

        return MediaUploadResponse.builder()
                .mediaId(mediaId)
                .originalVersion(toVersionDto(version))
                .duplicate(false)
                .build();
    }

    /**
     * Transform an existing media version. Applies the requested Cloudinary transformation,
     * downloads the derived bytes in a fixed format (never f_auto), hashes them, and creates a new version.
     */
    @Transactional
    public MediaVersionDto transform(UUID mediaId, String operation, UUID sourceVersionId) {
        MediaAsset asset = assetRepository.findById(mediaId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media asset not found: " + mediaId));

        // Find the source version (default to latest if not specified)
        MediaVersion sourceVersion;
        if (sourceVersionId != null) {
            sourceVersion = versionRepository.findById(sourceVersionId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "VERSION_NOT_FOUND", "Source version not found: " + sourceVersionId));
        } else {
            List<MediaVersion> versions = versionRepository.findByMediaAssetIdOrderByCreatedAtAsc(mediaId);
            if (versions.isEmpty()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "VERSION_NOT_FOUND", "No versions found for media: " + mediaId);
            }
            sourceVersion = versions.get(versions.size() - 1);
        }

        // Apply transformation: download derived bytes in fixed format (never f_auto) then re-upload
        CloudinaryUploadResult transformResult = cloudinaryGateway.transformAndUpload(
                sourceVersion.getCloudinaryPublicId(), operation, mediaId.toString());

        // Hash the derived bytes — download them explicitly in fixed format for deterministic hashing
        byte[] derivedBytes = cloudinaryGateway.downloadDerivedBytes(transformResult.getSecureUrl());
        String derivedHash = hashService.sha256(derivedBytes);

        // Validate: transformation producing identical bytes to its parent is rejected
        if (derivedHash.equalsIgnoreCase(sourceVersion.getSha256Hash())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "IDENTICAL_TRANSFORM",
                    "Transformation produced bytes identical to the parent version. No new version created.");
        }

        // Check if this derived hash already exists (idempotent transforms)
        Optional<MediaVersion> existingDerived = versionRepository.findBySha256Hash(derivedHash);
        if (existingDerived.isPresent()) {
            log.info("Transform hash [{}] already exists as version [{}]", derivedHash, existingDerived.get().getId());
            return toVersionDto(existingDerived.get());
        }

        String optimizedUrl = cloudinaryGateway.buildOptimizedUrl(transformResult.getPublicId());

        UUID newVersionId = UUID.randomUUID();
        MediaVersion newVersion = MediaVersion.builder()
                .id(newVersionId)
                .mediaAsset(asset)
                .versionType("TRANSFORMED")
                .operation(operation.toUpperCase())
                .parentVersionId(sourceVersion.getId())
                .sha256Hash(derivedHash)
                .cloudinaryPublicId(transformResult.getPublicId())
                .cloudinaryUrl(transformResult.getSecureUrl())
                .optimizedUrl(optimizedUrl)
                .width(transformResult.getWidth())
                .height(transformResult.getHeight())
                .bytes(transformResult.getBytes())
                .createdAt(Instant.now())
                .build();
        versionRepository.save(newVersion);

        // Get parent provenance hash for chain linkage
        Optional<ProvenanceRecord> parentProvenance = provenanceRepository.findByMediaVersionId(sourceVersion.getId());
        String parentHash = sourceVersion.getSha256Hash();

        ProvenanceRecord provenance = ProvenanceRecord.builder()
                .id(UUID.randomUUID())
                .mediaVersion(newVersion)
                .previousHash(parentHash)
                .operation(operation.toUpperCase())
                .cloudinaryAssetId(transformResult.getAssetId())
                .chainStatus("PENDING")
                .createdAt(Instant.now())
                .build();
        provenanceRepository.save(provenance);

        // Register on chain asynchronously
        registerOnChainAsync(provenance.getId(), derivedBytes, operation.toUpperCase(), parentHash);

        return toVersionDto(newVersion);
    }

    public MediaPassportDto getPassport(UUID mediaId) {
        MediaAsset asset = assetRepository.findById(mediaId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media asset not found: " + mediaId));
        return buildPassport(mediaId);
    }

    public List<ProvenanceDto> getProvenance(UUID mediaId) {
        assetRepository.findById(mediaId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media asset not found: " + mediaId));

        List<MediaVersion> versions = versionRepository.findByMediaAssetIdOrderByCreatedAtAsc(mediaId);
        return versions.stream()
                .map(v -> provenanceRepository.findByMediaVersionId(v.getId())
                        .map(p -> ProvenanceDto.builder()
                                .id(p.getId())
                                .mediaVersionId(v.getId())
                                .hash(v.getSha256Hash())
                                .previousHash(p.getPreviousHash())
                                .operation(p.getOperation())
                                .chainStatus(p.getChainStatus())
                                .txHash(p.getTxHash())
                                .recordId(p.getRecordId())
                                .explorerUrl(p.getExplorerUrl())
                                .errorMessage(p.getErrorMessage())
                                .createdAt(p.getCreatedAt())
                                .confirmedAt(p.getConfirmedAt())
                                .build())
                        .orElse(null))
                .filter(p -> p != null)
                .toList();
    }

    @Async
    public void registerOnChainAsync(UUID provenanceId, byte[] fileBytes, String operation, String previousHash) {
        ProvenanceRecord provenance = provenanceRepository.findById(provenanceId).orElse(null);
        if (provenance == null) {
            log.error("Cannot register on chain: ProvenanceRecord not found [{}]", provenanceId);
            return;
        }

        try {
            BlockchainRecord chainRecord;
            if (previousHash == null) {
                chainRecord = blockchainClient.registerOriginal(fileBytes, operation);
            } else {
                chainRecord = blockchainClient.registerVersion(previousHash, fileBytes, operation);
            }

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
            provenance.setErrorMessage(e.getMessage());
            provenance.setAttempts(provenance.getAttempts() + 1);
            provenanceRepository.save(provenance);
        }
    }

    private MediaPassportDto buildPassport(UUID mediaId) {
        MediaAsset asset = assetRepository.findById(mediaId).orElseThrow();
        List<MediaVersion> versions = versionRepository.findByMediaAssetIdOrderByCreatedAtAsc(mediaId);
        List<ProvenanceDto> provenances = getProvenance(mediaId);

        return MediaPassportDto.builder()
                .id(asset.getId())
                .cloudinaryPublicId(asset.getCloudinaryPublicId())
                .cloudinaryAssetId(asset.getCloudinaryAssetId())
                .originalFilename(asset.getOriginalFilename())
                .mimeType(asset.getMimeType())
                .createdAt(asset.getCreatedAt())
                .versions(versions.stream().map(this::toVersionDto).toList())
                .provenance(provenances)
                .build();
    }

    private MediaVersionDto toVersionDto(MediaVersion v) {
        return MediaVersionDto.builder()
                .id(v.getId())
                .versionType(v.getVersionType())
                .operation(v.getOperation())
                .parentVersionId(v.getParentVersionId())
                .sha256Hash(v.getSha256Hash())
                .cloudinaryUrl(v.getCloudinaryUrl())
                .optimizedUrl(v.getOptimizedUrl())
                .width(v.getWidth())
                .height(v.getHeight())
                .bytes(v.getBytes())
                .createdAt(v.getCreatedAt())
                .build();
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "image.jpg";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String detectMimeType(byte[] bytes) {
        if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) return "image/jpeg";
        if (bytes[0] == (byte) 0x89 && bytes[1] == (byte) 0x50) return "image/png";
        if (bytes[0] == (byte) 0x47 && bytes[1] == (byte) 0x49) return "image/gif";
        if (bytes[0] == (byte) 0x52 && bytes[1] == (byte) 0x49) return "image/webp";
        return "application/octet-stream";
    }
}
