// Talks to the blockchain REST API (see /blockchain/src/server.js).
// Falls back to in-memory mock data if VITE_API_URL is unset or unreachable,
// so the UI is demo-able before the backend/blockchain pieces are wired up.

const BASE = import.meta.env.VITE_API_URL;

function randomHash() {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  return '0x' + Array.from(bytes).map((b) => b.toString(16).padStart(2, '0')).join('');
}

// --- in-memory mock store (used only when no backend is configured/reachable) ---
const mock = { records: new Map() };

function mockRegister(previousHash, operation) {
  const hash = randomHash();
  const recordId = mock.records.size + 1;
  const record = {
    recordId,
    mediaHash: hash,
    previousHash: previousHash || null,
    operation,
    timestamp: new Date().toISOString(),
    creator: '0xMockWallet000000000000000000000000000',
  };
  mock.records.set(hash, record);
  return { hash, recordId, txHash: '0xmock' + recordId, explorerUrl: null, previousHash: previousHash || null };
}

function mockHistory(hash) {
  const chain = [];
  let cur = mock.records.get(hash);
  while (cur) {
    chain.unshift(cur);
    cur = cur.previousHash ? mock.records.get(cur.previousHash) : null;
  }
  return chain;
}

async function call(path, options) {
  if (!BASE) throw new Error('no-backend');
  const res = await fetch(BASE + path, options);
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.error || `Request failed (${res.status})`);
  }
  return res.json();
}

export async function registerOriginal(file, operation = 'ORIGINAL') {
  try {
    const fd = new FormData();
    fd.append('file', file);
    fd.append('operation', operation);
    return await call('/api/register', { method: 'POST', body: fd });
  } catch {
    return mockRegister(null, operation);
  }
}

export async function registerVersion(file, previousHash, operation) {
  try {
    const fd = new FormData();
    fd.append('file', file);
    fd.append('previousHash', previousHash);
    fd.append('operation', operation);
    return await call('/api/version', { method: 'POST', body: fd });
  } catch {
    return mockRegister(previousHash, operation);
  }
}

export async function verifyMedia(file) {
  try {
    const fd = new FormData();
    fd.append('file', file);
    return await call('/api/verify', { method: 'POST', body: fd });
  } catch {
    // Without a real backend we can't recompute the hash of an arbitrary file against
    // the mock store meaningfully, so mock verification just checks our own session records.
    for (const record of mock.records.values()) {
      if (record.__file === file) return { status: 'VERIFIED', hash: record.mediaHash, record };
    }
    return { status: 'MISMATCH', hash: randomHash(), record: null };
  }
}

export async function getHistory(hash) {
  try {
    return await call(`/api/history/${hash}`);
  } catch {
    return mockHistory(hash);
  }
}

export const isLiveBackend = Boolean(BASE);
