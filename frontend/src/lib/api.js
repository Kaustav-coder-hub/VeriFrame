const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

const API_KEY =
  import.meta.env.VITE_API_KEY || 'dev-secret-api-key-change-in-prod';
if (!API_URL) {
  throw new Error("VITE_API_URL is not configured");
}

if (!API_KEY) {
  throw new Error("VITE_API_KEY is not configured");
}

async function call(path, options = {}) {
  const headers = {
    ...(options.headers || {}),
  };

  // Backend requires API key for POST / protected endpoints
  if (options.method === 'POST') {
    headers['X-API-KEY'] = API_KEY;
  }

  const response = await fetch(`${API_URL}${path}`, {
    ...options,
    headers,
  });

  const body = await response.json().catch(() => null);

  if (!response.ok) {
    throw new Error(
      body?.message ||
        body?.error ||
        `Request failed (${response.status})`
    );
  }

  return body;
}


// ─────────────────────────────────────────────
// REGISTER ORIGINAL MEDIA
// ─────────────────────────────────────────────

export async function registerOriginal(file) {
  const fd = new FormData();
  fd.append('file', file);

  const result = await call('/api/v1/media', {
    method: 'POST',
    body: fd,
  });

  return normalizeUploadResult(result);
}


// ─────────────────────────────────────────────
// GET MEDIA PASSPORT
// ─────────────────────────────────────────────

export async function getMediaPassport(mediaId) {
  return call(`/api/v1/media/${mediaId}`);
}


// ─────────────────────────────────────────────
// GET PROVENANCE / HISTORY
// ─────────────────────────────────────────────

export async function getHistory(mediaId) {
  return call(`/api/v1/media/${mediaId}/provenance`);
}


// ─────────────────────────────────────────────
// CREATE DERIVED VERSION
// ─────────────────────────────────────────────

export async function registerVersion(mediaId, operation) {
  return call(`/api/v1/media/${mediaId}/transform`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      operation,
    }),
  });
}


// ─────────────────────────────────────────────
// VERIFY MEDIA
// ─────────────────────────────────────────────

export async function verifyMedia(file) {
  const fd = new FormData();
  fd.append('file', file);

  return call('/api/v1/verify', {
    method: 'POST',
    body: fd,
  });
}


// ─────────────────────────────────────────────
// NORMALIZE BACKEND RESPONSE
// ─────────────────────────────────────────────

function normalizeUploadResult(result) {
  return {
    ...result,

    mediaId: result.mediaId,

    hash:
      result.hash ||
      result.currentHash ||
      result.mediaHash ||
      result.originalVersion?.sha256Hash ||
      null,

    txHash:
      result.txHash ||
      result.originalVersion?.txHash ||
      null,

    previousHash:
      result.previousHash ||
      null,

    recordId:
      result.recordId ||
      null,
  };
}

export const isLiveBackend = true;