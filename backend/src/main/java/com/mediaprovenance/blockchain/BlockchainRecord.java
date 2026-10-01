package com.mediaprovenance.blockchain;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BlockchainRecord {
    String hash;
    String previousHash;
    Long recordId;
    String txHash;
    String explorerUrl;
    String status; // VERIFIED | MISMATCH | NOT_FOUND
}
