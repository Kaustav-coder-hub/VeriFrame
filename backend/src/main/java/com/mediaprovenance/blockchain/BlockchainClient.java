package com.mediaprovenance.blockchain;

import java.util.List;

public interface BlockchainClient {
    BlockchainRecord registerOriginal(byte[] fileBytes, String operation);
    BlockchainRecord registerVersion(String previousHash, byte[] fileBytes, String operation);
    BlockchainRecord verify(byte[] fileBytes);
    List<BlockchainRecord> getHistory(String hash);
}
