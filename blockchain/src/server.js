// Optional REST API around provenance.js so frontend/backend teammates can call it over HTTP.
// Every endpoint accepts either a multipart `file` upload OR a JSON/form field `url` (e.g. a Cloudinary URL).
require('dotenv').config();
const express = require('express');
const multer = require('multer');
const cors = require('cors');
const p = require('./provenance');

const app = express();
app.use(cors());
app.use(express.json());
const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 50 * 1024 * 1024 } });

const HASH_RE = /^0x[0-9a-fA-F]{64}$/;
const httpError = (status, message) => Object.assign(new Error(message), { status });

async function getBuffer(req) {
  if (req.file) return req.file.buffer;
  const url = req.body && req.body.url;
  if (url) {
    const r = await fetch(url);
    if (!r.ok) throw httpError(400, `Could not download url (HTTP ${r.status})`);
    return Buffer.from(await r.arrayBuffer());
  }
  throw httpError(400, 'Provide a "file" upload or a "url" field');
}

const wrap = (fn) => (req, res) =>
  fn(req, res).catch((e) => {
    const nested = e.info && e.info.error && e.info.error.message; // some nodes nest the revert reason here
    const msg = e.reason || (e.revert && e.revert.args && e.revert.args[0]) || nested || e.shortMessage || e.message;
    let status = e.status || 500;
    if (/already registered/i.test(msg)) status = 409;
    else if (/not registered/i.test(msg)) status = 404;
    res.status(status).json({ error: msg });
  });

app.get('/health', wrap(async (req, res) => {
  res.json({ status: 'ok', records: await p.getRecordCount() });
}));

// Register an original: POST /api/register  (file|url, operation?)
app.post('/api/register', upload.single('file'), wrap(async (req, res) => {
  const buf = await getBuffer(req);
  res.status(201).json(await p.registerOriginal(buf, req.body.operation || 'ORIGINAL'));
}));

// Register a derived version: POST /api/version  (file|url, previousHash, operation)
app.post('/api/version', upload.single('file'), wrap(async (req, res) => {
  const { previousHash, operation } = req.body;
  if (!HASH_RE.test(previousHash || '')) throw httpError(400, 'previousHash must be a 0x-prefixed 32-byte hex string');
  if (!operation) throw httpError(400, 'operation is required (e.g. CROP)');
  const buf = await getBuffer(req);
  res.status(201).json(await p.registerDerived(previousHash, buf, operation));
}));

// Verify a file: POST /api/verify  (file|url) -> { status: VERIFIED | MISMATCH, hash, record }
app.post('/api/verify', upload.single('file'), wrap(async (req, res) => {
  res.json(await p.verifyMedia(await getBuffer(req)));
}));

// Timeline: GET /api/history/:hash -> [oldest ... newest]
app.get('/api/history/:hash', wrap(async (req, res) => {
  if (!HASH_RE.test(req.params.hash)) throw httpError(400, 'Invalid hash');
  res.json(await p.getHistory(req.params.hash));
}));

module.exports = app;

if (require.main === module) {
  const port = process.env.PORT || 4000;
  app.listen(port, () => console.log(`Provenance API on http://localhost:${port}`));
}
