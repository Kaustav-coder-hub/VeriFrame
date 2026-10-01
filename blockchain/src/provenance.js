// Backend module for the blockchain layer. Requires: npm i ethers dotenv  (ethers v6)
require('dotenv').config();
const { ethers } = require('ethers');
const crypto = require('crypto');

const REC = 'tuple(uint256 recordId, bytes32 mediaHash, bytes32 previousHash, string operation, uint256 timestamp, address creator)';
const ABI = [
  'function registerMedia(bytes32 mediaHash, string operation) returns (uint256)',
  'function registerVersion(bytes32 previousHash, bytes32 newHash, string operation) returns (uint256)',
  `function verifyHash(bytes32 mediaHash) view returns (bool exists, ${REC} record)`,
  `function getHistory(bytes32 mediaHash) view returns (${REC}[])`,
  'function recordCount() view returns (uint256)',
  'event MediaRegistered(uint256 indexed recordId, bytes32 indexed mediaHash, bytes32 indexed previousHash, string operation, address creator, uint256 timestamp)',
];

const provider = new ethers.JsonRpcProvider(process.env.SEPOLIA_RPC_URL, undefined, { cacheTimeout: -1 });
const wallet = new ethers.Wallet(process.env.PRIVATE_KEY, provider);
const contract = new ethers.Contract(process.env.CONTRACT_ADDRESS, ABI, wallet);
const EXPLORER = 'https://sepolia.etherscan.io/tx/';

/** SHA-256 of the raw media bytes (Buffer) -> 0x-prefixed bytes32 hex */
function hashMedia(buffer) {
  return '0x' + crypto.createHash('sha256').update(buffer).digest('hex');
}

function fmt(r) {
  return {
    recordId: Number(r.recordId),
    mediaHash: r.mediaHash,
    previousHash: r.previousHash === ethers.ZeroHash ? null : r.previousHash,
    operation: r.operation,
    timestamp: new Date(Number(r.timestamp) * 1000).toISOString(),
    creator: r.creator,
  };
}

// Send transactions one at a time so concurrent API requests never reuse a nonce.
let queue = Promise.resolve();
function enqueue(fn) {
  const run = queue.then(fn);
  queue = run.catch(() => {});
  return run;
}

async function _finish(tx) {
  const receipt = await tx.wait();
  let recordId = null;
  for (const log of receipt.logs) {
    try {
      const parsed = contract.interface.parseLog(log);
      if (parsed && parsed.name === 'MediaRegistered') recordId = Number(parsed.args.recordId);
    } catch (_) {}
  }
  return { recordId, txHash: tx.hash, explorerUrl: EXPLORER + tx.hash };
}

/** Register an original file. Returns { hash, recordId, txHash, explorerUrl } */
async function registerOriginal(buffer, operation = 'ORIGINAL') {
  const hash = hashMedia(buffer);
  return enqueue(async () => {
    const tx = await contract.registerMedia(hash, operation);
    return { hash, ...(await _finish(tx)) };
  });
}

/** Register a derived version. parentHash = hash of the parent version. */
async function registerDerived(parentHash, buffer, operation) {
  const hash = hashMedia(buffer);
  return enqueue(async () => {
    const tx = await contract.registerVersion(parentHash, hash, operation);
    return { hash, previousHash: parentHash, ...(await _finish(tx)) };
  });
}

/** Verify an uploaded file. Returns { status: 'VERIFIED' | 'MISMATCH', hash, record } */
async function verifyMedia(buffer) {
  const hash = hashMedia(buffer);
  const [exists, record] = await contract.verifyHash(hash);
  return exists
    ? { status: 'VERIFIED', hash, record: fmt(record) }
    : { status: 'MISMATCH', hash, record: null };
}

/** Timeline data for the frontend: oldest -> newest */
async function getHistory(hash) {
  const chain = await contract.getHistory(hash);
  return chain.map(fmt);
}

async function getRecordCount() {
  return Number(await contract.recordCount());
}

module.exports = { hashMedia, registerOriginal, registerDerived, verifyMedia, getHistory, getRecordCount };
