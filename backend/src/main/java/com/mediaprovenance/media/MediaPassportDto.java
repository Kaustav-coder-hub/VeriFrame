package com.mediaprovenance.media;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class MediaPassportDto {
    UUID id;
    String cloudinaryPublicId;
    String cloudinaryAssetId;
    String originalFilename;
    String mimeType;
    Instant createdAt;
    List<MediaVersionDto> versions;
    List<Object> aiResults;
    List<ProvenanceDto> provenance;
}
