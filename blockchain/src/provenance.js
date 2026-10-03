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
 * The standard ethers JsonRpcProvider was timing out against
 * the current Sepolia RPC endpoint.
 *
 * JsonRpcApiProvider expects _send() to return an array of
 * JSON-RPC response objects, so the RPC response is normalized
 * to an array below.
 */
class NativeFetchProvider extends ethers.JsonRpcApiProvider {
  constructor(url) {
    super();
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

    const result = await response.json();

    return Array.isArray(result) ? result : [result];
  }
}

/**
 * Provider
 *
 * Uses the RPC endpoint from .env.
 *
 * The provider does NOT hard-code Sepolia's chain ID,
 * allowing the same code to work with:
 *
 * - Local Ganache during tests
 * - Sepolia in production
 */
const provider = new NativeFetchProvider(
  process.env.SEPOLIA_RPC_URL
);

/**
 * Wallet used for blockchain transactions.
 */
const wallet = new ethers.Wallet(
  process.env.PRIVATE_KEY,
  provider
);

/**
 * NonceManager
 *
 * Keeps transaction nonces synchronized when multiple
 * blockchain transactions are created from the same wallet.
 *
 * Without this, two transactions can sometimes receive
 * the same/stale nonce and Ganache rejects the transaction
 * with errors such as:
 *
 * "account has nonce of: 4 tx has nonce of: 3"
 */
const signer = new ethers.NonceManager(wallet);

/**
 * Smart contract instance.
 *
 * IMPORTANT:
 * The contract now uses the NonceManager instead of the
 * raw wallet.
 */
const contract = new ethers.Contract(
  process.env.CONTRACT_ADDRESS,
  ABI,
  signer
);

const EXPLORER = 'https://sepolia.etherscan.io/tx/';

/**
 * SHA-256 of the raw media bytes.
 *
 * Returns:
 * 0x + 64 hexadecimal characters
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
 * API requests do not attempt to submit transactions simultaneously.
 *
 * The NonceManager above additionally keeps nonce allocation
 * synchronized with the signer.
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