package com.mediaprovenance.blockchain;

import com.mediaprovenance.common.ApiException;
import com.mediaprovenance.hashing.HashService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@ConditionalOnProperty(name = "app.blockchain.mode", havingValue = "MOCK", matchIfMissing = true)
public class MockBlockchainClient implements BlockchainClient {

    private static final Logger log = LoggerFactory.getLogger(MockBlockchainClient.class);
    private final HashService hashService;
    private final Map<String, BlockchainRecord> ledger = new ConcurrentHashMap<>();
    private final AtomicLong recordIdCounter = new AtomicLong(1000L);

    public MockBlockchainClient(HashService hashService) {
        this.hashService = hashService;
    }

    @Override
    public synchronized BlockchainRecord registerOriginal(byte[] fileBytes, String operation) {
        String hash = hashService.sha256(fileBytes);
        if (ledger.containsKey(hash)) {
            log.info("Mock Blockchain: Hash [{}] already registered. Returning existing record.", hash);
            return ledger.get(hash);
        }

        long recordId = recordIdCounter.incrementAndGet();
        String txHash = "0xmocktx_" + UUID.randomUUID().toString().replace("-", "");
        String explorerUrl = "https://sepolia.etherscan.io/tx/" + txHash;

        BlockchainRecord record = BlockchainRecord.builder()
                .hash(hash)
                .previousHash(null)
                .recordId(recordId)
                .txHash(txHash)
                .explorerUrl(explorerUrl)
                .status("CONFIRMED")
                .build();

        ledger.put(hash, record);
        log.info("Mock Blockchain: Registered original hash [{}] with recordId [{}]", hash, recordId);
        return record;
    }

    @Override
    public synchronized BlockchainRecord registerVersion(String previousHash, byte[] fileBytes, String operation) {
        String newHash = hashService.sha256(fileBytes);
        if (previousHash != null && !ledger.containsKey(previousHash)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PARENT_NOT_REGISTERED", "Parent hash '" + previousHash + "' is not registered on chain");
        }

        if (ledger.containsKey(newHash)) {
            log.info("Mock Blockchain: Version hash [{}] already registered. Returning existing record.", newHash);
            return ledger.get(newHash);
        }

        long recordId = recordIdCounter.incrementAndGet();
        String txHash = "0xmocktx_" + UUID.randomUUID().toString().replace("-", "");
        String explorerUrl = "https://sepolia.etherscan.io/tx/" + txHash;

        BlockchainRecord record = BlockchainRecord.builder()
                .hash(newHash)
                .previousHash(previousHash)
                .recordId(recordId)
                .txHash(txHash)
                .explorerUrl(explorerUrl)
                .status("CONFIRMED")
                .build();

        ledger.put(newHash, record);
        log.info("Mock Blockchain: Registered version hash [{}] (parent: [{}]) with recordId [{}]", newHash, previousHash, recordId);
        return record;
    }

    @Override
    public BlockchainRecord verify(byte[] fileBytes) {
        String computedHash = hashService.sha256(fileBytes);
        BlockchainRecord record = ledger.get(computedHash);
        if (record != null) {
            return record;
        }
        return BlockchainRecord.builder()
                .hash(computedHash)
                .status("NOT_FOUND")
                .build();
    }

    @Override
    public List<BlockchainRecord> getHistory(String hash) {
        List<BlockchainRecord> history = new ArrayList<>();
        String currentHash = hash;

        while (currentHash != null && ledger.containsKey(currentHash)) {
            BlockchainRecord record = ledger.get(currentHash);
            history.add(0, record); // add at start to preserve root-to-leaf ordering
            currentHash = record.getPreviousHash();
        }

        return history;
    }
}
