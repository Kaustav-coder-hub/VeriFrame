package com.mediaprovenance.media;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class MediaVersionDto {
    UUID id;
    String versionType;
    String operation;
    UUID parentVersionId;
    String sha256Hash;
    String cloudinaryUrl;
    String optimizedUrl;
    int width;
    int height;
    long bytes;
    Instant createdAt;
}
