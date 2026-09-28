package com.mediaprovenance.media;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class ProvenanceDto {
    UUID id;
    UUID mediaVersionId;
    String hash;
    String previousHash;
    String operation;
    String chainStatus;
    String txHash;
    Long recordId;
    String explorerUrl;
    String errorMessage;
    Instant createdAt;
    Instant confirmedAt;
}
