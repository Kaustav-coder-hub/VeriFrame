// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/// @title MediaProvenance
/// @notice Stores hashes + version links of media. Media itself stays off-chain.
contract MediaProvenance {
    struct Record {
        uint256 recordId;
        bytes32 mediaHash;     // SHA-256 of the media bytes
        bytes32 previousHash;  // 0x0 for an original, else hash of parent version
        string operation;      // ORIGINAL, CROP, BG_REMOVAL, AI_ENHANCE, ...
        uint256 timestamp;
        address creator;
    }

    uint256 public recordCount;
    mapping(uint256 => Record) private records;
    mapping(bytes32 => uint256) private hashToRecordId; // 0 = not registered

    event MediaRegistered(
        uint256 indexed recordId,
        bytes32 indexed mediaHash,
        bytes32 indexed previousHash,
        string operation,
        address creator,
        uint256 timestamp
    );

    /// @notice Register an original media file
    function registerMedia(bytes32 mediaHash, string calldata operation)
        external
        returns (uint256)
    {
        return _register(mediaHash, bytes32(0), operation);
    }

    /// @notice Register a derived version (crop, enhance, etc.) of an existing record
    function registerVersion(
        bytes32 previousHash,
        bytes32 newHash,
        string calldata operation
    ) external returns (uint256) {
        require(previousHash != bytes32(0), "Previous hash required");
        require(hashToRecordId[previousHash] != 0, "Previous hash not registered");
        return _register(newHash, previousHash, operation);
    }

    function _register(
        bytes32 mediaHash,
        bytes32 previousHash,
        string calldata operation
    ) internal returns (uint256) {
        require(mediaHash != bytes32(0), "Empty hash");
        require(hashToRecordId[mediaHash] == 0, "Hash already registered");

        recordCount++;
        records[recordCount] = Record(
            recordCount,
            mediaHash,
            previousHash,
            operation,
            block.timestamp,
            msg.sender
        );
        hashToRecordId[mediaHash] = recordCount;

        emit MediaRegistered(
            recordCount,
            mediaHash,
            previousHash,
            operation,
            msg.sender,
            block.timestamp
        );
        return recordCount;
    }

    /// @notice Verification: does this hash exist on-chain? If so, return its record.
    function verifyHash(bytes32 mediaHash)
        external
        view
        returns (bool exists, Record memory record)
    {
        uint256 id = hashToRecordId[mediaHash];
        if (id == 0) {
            return (false, record);
        }
        return (true, records[id]);
    }

    function getRecord(uint256 recordId) external view returns (Record memory) {
        require(recordId >= 1 && recordId <= recordCount, "Record not found");
        return records[recordId];
    }

    /// @notice Full lineage for the timeline. Index 0 = original, last = given hash.
    function getHistory(bytes32 mediaHash) external view returns (Record[] memory) {
        uint256 cur = hashToRecordId[mediaHash];
        require(cur != 0, "Not registered");

        uint256 count = 0;
        uint256 tmp = cur;
        while (tmp != 0) {
            count++;
            bytes32 p = records[tmp].previousHash;
            tmp = p == bytes32(0) ? 0 : hashToRecordId[p];
        }

        Record[] memory chain = new Record[](count);
        for (uint256 i = count; i > 0; i--) {
            chain[i - 1] = records[cur];
            bytes32 p = records[cur].previousHash;
            cur = p == bytes32(0) ? 0 : hashToRecordId[p];
        }
        return chain;
    }
}
