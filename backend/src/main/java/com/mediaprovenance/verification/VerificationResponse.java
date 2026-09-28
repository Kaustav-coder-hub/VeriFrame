package com.mediaprovenance.verification;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class VerificationResponse {
    String status; // VERIFIED | MISMATCH | NOT_FOUND
    String hash;
    UUID mediaId;
    String matchedVersionId;
    String chainStatus;
    String txHash;
    String explorerUrl;
    String message;
    String source;
}
