// Optional REST API around provenance.js so frontend/backend teammates can call it over HTTP.
// Every endpoint accepts either a multipart `file` upload OR a JSON/form field `url`
// (e.g. a Cloudinary URL).

require('dotenv').config();

const express = require('express');
const multer = require('multer');
const cors = require('cors');
const { ethers } = require('ethers');
const p = require('./provenance');

const app = express();

app.use(cors());
app.use(express.json());

const upload = multer({
  storage: multer.memoryStorage(),
  limits: {
    fileSize: 50 * 1024 * 1024,
  },
});

const HASH_RE = /^0x[0-9a-fA-F]{64}$/;

const httpError = (status, message) =>
  Object.assign(new Error(message), { status });

/**
 * Get uploaded file or download a URL.
 */
async function getBuffer(req) {
  if (req.file) {
    return req.file.buffer;
  }

  const url = req.body && req.body.url;

  if (url) {
    const r = await fetch(url);

    if (!r.ok) {
      throw httpError(
        400,
        `Could not download url (HTTP ${r.status})`
      );
    }

    return Buffer.from(await r.arrayBuffer());
  }

  throw httpError(
    400,
    'Provide a "file" upload or a "url" field'
  );
}

/**
 * Extract the most useful error message from ethers,
 * Ganache, JSON-RPC and Solidity revert errors.
 */

/**
 * Extract a Solidity revert reason from raw revert data.
 */
function decodeRevertReason(data) {
  if (!data) {
    return null;
  }

  // Some providers return:
  // { result: "0x08c379a0..." }
  if (typeof data === 'object') {
    if (data.result) {
      const decoded = decodeRevertReason(data.result);
      if (decoded) return decoded;
    }

    if (data.data) {
      const decoded = decodeRevertReason(data.data);
      if (decoded) return decoded;
    }

    return null;
  }

  if (typeof data !== 'string') {
    return null;
  }

  // Solidity Error(string)
  if (data.startsWith('0x08c379a0')) {
    try {
      const encoded = '0x' + data.slice(10);

      const decoded =
        ethers.AbiCoder.defaultAbiCoder().decode(
          ['string'],
          encoded
        );

      return decoded[0];
    } catch (_) {
      return null;
    }
  }

  return null;
}


/**
 * Extract the most useful error message from ethers,
 * Ganache and JSON-RPC errors.
 */
function getErrorMessage(e) {
  const messages = [];

  function collect(value) {
    if (!value) return;

    if (typeof value === 'string') {
      messages.push(value);
      return;
    }

    if (typeof value !== 'object') {
      return;
    }

    if (value.message) {
      messages.push(value.message);
    }

    if (value.reason) {
      messages.push(value.reason);
    }

    if (value.shortMessage) {
      messages.push(value.shortMessage);
    }

    if (value.errorName) {
      messages.push(value.errorName);
    }

    if (value.data) {
  const decoded = decodeRevertReason(value.data);

  if (decoded) {
    messages.push(decoded);
  }

  if (typeof value.data === 'object') {
    collect(value.data);
  }
}

if (value.result) {
  const decoded = decodeRevertReason(value.result);

  if (decoded) {
    messages.push(decoded);
  }

  if (typeof value.result === 'object') {
    collect(value.result);
  }
}

    if (value.error) {
      collect(value.error);
    }

    if (value.info) {
      collect(value.info);
    }

    if (value.revert) {
      collect(value.revert);
    }

    if (value.cause) {
      collect(value.cause);
    }
  }

  collect(e);

  // Prefer actual Solidity revert messages.
  const solidityReason = messages.find((message) =>
    /hash already registered|previous hash not registered|previous hash required|empty hash|not registered/i.test(
      message
    )
  );

  if (solidityReason) {
    return solidityReason;
  }

  return (
    messages.find(Boolean) ||
    'Internal server error'
  );
}

/**
 * Convert blockchain/domain errors into HTTP status codes.
 */
function getHttpStatus(e, msg) {
  // Explicit application-level error.
  if (e.status) {
    return e.status;
  }

  // Duplicate media hash.
  if (/hash already registered/i.test(msg)) {
    return 409;
  }

  // Unknown parent hash.
  if (/previous hash not registered/i.test(msg)) {
    return 404;
  }

  // Invalid input.
  if (
    /previous hash required/i.test(msg) ||
    /empty hash/i.test(msg) ||
    /invalid hash/i.test(msg) ||
    /previousHash/i.test(msg) ||
    /operation is required/i.test(msg) ||
    /provide a "file"/i.test(msg) ||
    /could not download url/i.test(msg)
  ) {
    return 400;
  }

  return 500;
}


/**
 * Common async route wrapper.
 */
const wrap = (fn) => (req, res) =>
  fn(req, res).catch((e) => {
    const msg = getErrorMessage(e);
    const status = getHttpStatus(e, msg);

    res.status(status).json({
      error: msg,
    });
  });

/**
 * Health check.
 */
app.get(
  '/health',
  wrap(async (req, res) => {
    res.json({
      status: 'ok',
      records: await p.getRecordCount(),
    });
  })
);

/**
 * Register an original:
 *
 * POST /api/register
 *
 * Accepts:
 * - file
 * - url
 * - operation
 */
app.post(
  '/api/register',
  upload.single('file'),
  wrap(async (req, res) => {
    const buf = await getBuffer(req);

    // Check whether this exact media hash already exists.
    // This avoids sending a transaction that the Solidity
    // contract will reject with "Hash already registered".
    const existing = await p.verifyMedia(buf);

    if (existing.status === 'VERIFIED') {
      throw httpError(
        409,
        'Hash already registered'
      );
    }

    const result = await p.registerOriginal(
      buf,
      req.body.operation || 'ORIGINAL'
    );

    res.status(201).json(result);
  })
);

/**
 * Register a derived version:
 *
 * POST /api/version
 *
 * Required:
 * - file OR url
 * - previousHash
 * - operation
 */
app.post(
  '/api/version',
  upload.single('file'),
  wrap(async (req, res) => {
    const {
      previousHash,
      operation,
    } = req.body;

    if (!HASH_RE.test(previousHash || '')) {
      throw httpError(
        400,
        'previousHash must be a 0x-prefixed 32-byte hex string'
      );
    }

    if (!operation) {
      throw httpError(
        400,
        'operation is required (e.g. CROP)'
      );
    }

    const buf = await getBuffer(req);

    const result = await p.registerDerived(
      previousHash,
      buf,
      operation
    );

    res.status(201).json(result);
  })
);

/**
 * Verify a file:
 *
 * POST /api/verify
 *
 * Returns:
 * VERIFIED or MISMATCH
 */
app.post(
  '/api/verify',
  upload.single('file'),
  wrap(async (req, res) => {
    const result = await p.verifyMedia(
      await getBuffer(req)
    );

    res.json(result);
  })
);

/**
 * Get provenance history:
 *
 * GET /api/history/:hash
 */
app.get(
  '/api/history/:hash',
  wrap(async (req, res) => {
    if (!HASH_RE.test(req.params.hash)) {
      throw httpError(
        400,
        'Invalid hash'
      );
    }

    const history = await p.getHistory(
      req.params.hash
    );

    res.json(history);
  })
);

module.exports = app;

/**
 * Start REST server when executed directly.
 */
if (require.main === module) {
  const port = process.env.PORT || 4000;

  const PORT = process.env.PORT || 4000;
  const HOST = '0.0.0.0';

  app.listen(PORT, HOST, () => {
    console.log(`Provenance API running on ${HOST}:${PORT}`);
  });
}