# Blockchain Module — Media Provenance

Tamper-evident provenance layer for the Cloudinary AI Hackathon 2026.
Only **hashes, version links, operation names and timestamps** go on-chain. Media stays on Cloudinary.

## Contract: `contracts/MediaProvenance.sol`
| Function | Purpose |
|---|---|
| `registerMedia(hash, operation)` | Register an original file |
| `registerVersion(previousHash, newHash, operation)` | Register a derived version (CROP, BG_REMOVAL, ...) linked to its parent |
| `verifyHash(hash)` | `(exists, record)` -> VERIFIED / MISMATCH |
| `getHistory(hash)` | Full lineage, oldest -> newest (for the timeline page) |
| `getRecord(id)`, `recordCount()` | Lookups |

Record = `recordId, mediaHash, previousHash, operation, timestamp, creator`.
Rules: a hash can only be registered once; a version's parent must already be registered.

## Backend API: `src/provenance.js`
```js
const p = require('./blockchain/src/provenance');

const orig = await p.registerOriginal(fileBuffer);                 // { hash, recordId, txHash, explorerUrl }
const v2   = await p.registerDerived(orig.hash, cropBuffer, 'CROP');
const res  = await p.verifyMedia(uploadedBuffer);                  // { status: 'VERIFIED'|'MISMATCH', hash, record }
const line = await p.getHistory(v2.hash);                          // [{recordId, mediaHash, previousHash, operation, timestamp, creator}]
p.hashMedia(buffer);                                               // 0x-prefixed SHA-256
```
Transactions are queued internally, so concurrent API requests are safe.

**Important:** hash the exact bytes of the file you register and later verify. Download one fixed
version from Cloudinary (avoid `f_auto`/`q_auto` for the hashed copy — bytes vary per browser).

## REST API (optional): `npm start` -> http://localhost:4000
Every POST accepts a multipart `file` upload **or** a `url` field (e.g. a Cloudinary URL; the server downloads and hashes the bytes).

| Endpoint | Body | Returns |
|---|---|---|
| `POST /api/register` | `file` or `url`, `operation?` | `201 { hash, recordId, txHash, explorerUrl }` |
| `POST /api/version` | `file` or `url`, `previousHash`, `operation` | `201 { hash, previousHash, recordId, txHash, explorerUrl }` |
| `POST /api/verify` | `file` or `url` | `{ status: "VERIFIED" \| "MISMATCH", hash, record }` |
| `GET /api/history/:hash` | - | timeline array, oldest -> newest |
| `GET /health` | - | `{ status, records }` |

Errors: `409` hash already registered, `404` parent hash not registered, `400` bad input.

```bash
curl -F file=@photo.jpg http://localhost:4000/api/register
curl -F file=@cropped.jpg -F previousHash=0x<original hash> -F operation=CROP http://localhost:4000/api/version
curl -F file=@cropped.jpg http://localhost:4000/api/verify
```

## Setup
```bash
npm install
npm test                     # compile + 12 tests on a local in-memory chain (no ETH needed)
cp .env.example .env         # fill in RPC URL + TEST wallet private key (Sepolia faucet ETH)
npm run deploy               # deploys to Sepolia, prints CONTRACT_ADDRESS -> put it in .env
npm run demo                 # end-to-end demo on the configured network
npm start                    # optional REST API on port 4000
```
Alternative deploy: paste `contracts/MediaProvenance.sol` into Remix (Solidity >= 0.8.20, EVM `paris`).

## Security
Use a throwaway wallet with test ETH only. Never commit `.env` or private keys.
