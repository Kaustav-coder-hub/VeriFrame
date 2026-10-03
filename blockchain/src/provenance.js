// Backend module for the blockchain layer.
// Requires: npm i ethers dotenv (ethers v6)

require('dotenv').config();

const { ethers } = require('ethers');
const crypto = require('crypto');

const REC =
  'tuple(uint256 recordId, bytes32 mediaHash, bytes32 previousHash, string operation, uint256 timestamp, address creator)';

const ABI = [
  'function registerMedia(bytes32 mediaHash, string operation) returns (uint256)',
  'function registerVersion(bytes32 previousHash, bytes32 newHash, string operation) returns (uint256)',
  `function verifyHash(bytes32 mediaHash) view returns (bool exists, ${REC} record)`,
  `function getHistory(bytes32 mediaHash) view returns (${REC}[])`,
  'function recordCount() view returns (uint256)',
  'event MediaRegistered(uint256 indexed recordId, bytes32 indexed mediaHash, bytes32 indexed previousHash, string operation, address creator, uint256 timestamp)',
];

/**
 * Custom ethers v6 provider using Node's native fetch().
 *
 * This avoids the JsonRpcProvider transport timeout that was
 * occurring with the Sepolia RPC endpoint.
 */
class NativeFetchProvider extends ethers.JsonRpcApiProvider {
  constructor(url) {
    super({
      chainId: 11155111,
      name: 'sepolia',
    });

    this.url = url;
  }

  async _send(payload) {
    const response = await fetch(this.url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    });

    if (!response.ok) {
      throw new Error(
        `RPC HTTP ${response.status}: ${await response.text()}`
      );
    }

    return await response.json();
  }
}

/**
 * Sepolia provider
 */
const provider = new NativeFetchProvider(
  process.env.SEPOLIA_RPC_URL
);

/**
 * Wallet used for blockchain transactions
 */
const wallet = new ethers.Wallet(
  process.env.PRIVATE_KEY,
  provider
);

/**
 * Smart contract instance
 */
const contract = new ethers.Contract(
  process.env.CONTRACT_ADDRESS,
  ABI,
  wallet
);

const EXPLORER = 'https://sepolia.etherscan.io/tx/';

/**
 * SHA-256 of the raw media bytes.
 *
 * Returns:
 * 0x + 64 hexadecimal characters
 *
 * Example:
 * 0xabc123...
 */
function hashMedia(buffer) {
  return (
    '0x' +
    crypto
      .createHash('sha256')
      .update(buffer)
      .digest('hex')
  );
}

/**
 * Convert blockchain record into a frontend-friendly object.
 */
function fmt(r) {
  return {
    recordId: Number(r.recordId),
    mediaHash: r.mediaHash,

    previousHash:
      r.previousHash === ethers.ZeroHash
        ? null
        : r.previousHash,

    operation: r.operation,

    timestamp: new Date(
      Number(r.timestamp) * 1000
    ).toISOString(),

    creator: r.creator,
  };
}

/**
 * Transaction queue.
 *
 * Blockchain transactions are sent one at a time so concurrent
 * API requests do not reuse the same nonce.
 */
let queue = Promise.resolve();

function enqueue(fn) {
  const run = queue.then(fn);

  queue = run.catch(() => {});

  return run;
}

/**
 * Wait for a transaction to be mined and extract the
 * MediaRegistered event.
 */
async function _finish(tx) {
  const receipt = await tx.wait();

  let recordId = null;

  for (const log of receipt.logs) {
    try {
      const parsed = contract.interface.parseLog(log);

      if (
        parsed &&
        parsed.name === 'MediaRegistered'
      ) {
        recordId = Number(parsed.args.recordId);
      }
    } catch (_) {
      // Ignore logs belonging to other contracts/events.
    }
  }

  return {
    recordId,
    txHash: tx.hash,
    explorerUrl: EXPLORER + tx.hash,
  };
}

/**
 * Register an original media file.
 *
 * Returns:
 * {
 *   hash,
 *   recordId,
 *   txHash,
 *   explorerUrl
 * }
 */
async function registerOriginal(
  buffer,
  operation = 'ORIGINAL'
) {
  const hash = hashMedia(buffer);

  return enqueue(async () => {
    const tx = await contract.registerMedia(
      hash,
      operation
    );

    return {
      hash,
      ...(await _finish(tx)),
    };
  });
}

/**
 * Register a derived media version.
 *
 * parentHash = hash of the previous/original version.
 *
 * operation:
 *   CROP
 *   BG_REMOVAL
 */
async function registerDerived(
  parentHash,
  buffer,
  operation
) {
  const hash = hashMedia(buffer);

  return enqueue(async () => {
    const tx = await contract.registerVersion(
      parentHash,
      hash,
      operation
    );

    return {
      hash,
      previousHash: parentHash,
      ...(await _finish(tx)),
    };
  });
}

/**
 * Verify an uploaded media file.
 *
 * Returns:
 *
 * VERIFIED:
 * {
 *   status: 'VERIFIED',
 *   hash,
 *   record
 * }
 *
 * MISMATCH:
 * {
 *   status: 'MISMATCH',
 *   hash,
 *   record: null
 * }
 */
async function verifyMedia(buffer) {
  const hash = hashMedia(buffer);

  const [exists, record] =
    await contract.verifyHash(hash);

  return exists
    ? {
        status: 'VERIFIED',
        hash,
        record: fmt(record),
      }
    : {
        status: 'MISMATCH',
        hash,
        record: null,
      };
}

/**
 * Get the complete provenance history
 * for a media hash.
 *
 * Returns timeline:
 * oldest -> newest
 */
async function getHistory(hash) {
  const chain =
    await contract.getHistory(hash);

  return chain.map(fmt);
}

/**
 * Get total number of blockchain records.
 */
async function getRecordCount() {
  return Number(
    await contract.recordCount()
  );
}

/**
 * Export blockchain functions.
 */
module.exports = {
  hashMedia,
  registerOriginal,
  registerDerived,
  verifyMedia,
  getHistory,
  getRecordCount,
};