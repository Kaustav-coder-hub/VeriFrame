# VeriFrame — AI/Core Specification

**Project:** VeriFrame — AI-Powered Media Provenance & Verification  
**Owner:** AI + Core Product

## 1. Media Identity

Every uploaded media asset receives a deterministic cryptographic fingerprint.

```json
{
  "mediaId": "VF-<unique-id>",
  "hash": "<sha256-hex>",
  "algorithm": "SHA-256",
  "mimeType": "image/jpeg",
  "size": 123456,
  "createdAt": "2026-09-26T18:00:00Z",
  "operation": "ORIGINAL"
}
```

Rules:
- `hash` is the SHA-256 hash of the exact file bytes.
- Hashes use lowercase hexadecimal.
- `mediaId` identifies the VeriFrame record; `hash` identifies the exact file.
- Original files use `operation = ORIGINAL`.
- Every transformed/derived file gets a new hash.

## 2. Hashing Contract

**Input:** file or byte stream.

**Output:**
```json
{
  "hash": "64-character-lowercase-hex-string",
  "algorithm": "SHA-256"
}
```

Cloudinary transformations create new media representations, so:
`ORIGINAL HASH != TRANSFORMED HASH`

Python interface:
```python
def sha256_file(file_path: str) -> str:
    """Return lowercase SHA-256 hex digest for the exact file bytes."""
```

Do not hash filenames, URLs, public IDs, or metadata instead of file bytes.

## 3. Provenance Model

Media history is represented as a chain of versions.

Original:
```json
{
  "mediaHash": "HASH_A",
  "previousHash": null,
  "operation": "ORIGINAL"
}
```

Derived:
```json
{
  "mediaHash": "HASH_B",
  "previousHash": "HASH_A",
  "operation": "CROP"
}
```

Example: `ORIGINAL → CROP → ENHANCE`

Each derived version has its own hash, references its immediate parent hash, records the operation, and can be independently verified.

MVP operations: `ORIGINAL`, `CROP`, `RESIZE`, `ENHANCE`, `BACKGROUND_REMOVAL`, `FORMAT_CONVERSION`, `AI_GENERATED`, `OTHER`.

## 4. Provenance Object

```json
{
  "mediaId": "VF-123456",
  "mediaHash": "HASH_B",
  "previousHash": "HASH_A",
  "operation": "CROP",
  "cloudinary": {
    "publicId": "veriframe/asset_123",
    "resourceType": "image",
    "version": 1234567890
  },
  "createdAt": "2026-09-26T18:10:00Z"
}
```

Cloudinary fields are integration metadata and do not replace the cryptographic hash.

## 5. AI Analysis Output

Cloudinary AI and/or other AI processing should be normalized into one application-level format.

```json
{
  "model": "cloudinary-ai",
  "tags": [
    {"label": "person", "confidence": 0.96},
    {"label": "outdoor", "confidence": 0.91}
  ],
  "quality": {"score": 0.88}
}
```

AI results are metadata, not the media identity. Confidence values are between `0` and `1`. Provider-specific responses are normalized before use.

## 6. Verification States

### VERIFIED
The submitted file's SHA-256 exactly matches a registered media version.

### MISMATCH
A related provenance record exists, but the submitted file does not match the registered hash.

### NOT_FOUND
No registered media/provenance record is found.

Recommended response:
```json
{
  "status": "VERIFIED",
  "currentHash": "HASH_B",
  "registeredHash": "HASH_B",
  "mediaId": "VF-123456",
  "operation": "CROP",
  "previousHash": "HASH_A"
}
```

## 7. Verification Logic

```text
Submitted File
      |
      v
SHA-256
      |
      v
Current Hash
      |
      v
Lookup registered provenance
      |
      +---- exact hash ----> VERIFIED
      |
      +---- related record --> MISMATCH
      |
      +---- no record ------> NOT_FOUND
```

The exact SHA-256 match is the primary identity signal. Cloudinary metadata, AI tags, filenames, URLs, and visual similarity provide context but do not replace exact hash verification.

## 8. Backend Interface

Conceptual fingerprint endpoint:
```http
POST /core/fingerprint
```

Output:
```json
{"hash": "HASH_A", "algorithm": "SHA-256"}
```

Conceptual verification endpoint:
```http
POST /core/verify
```

Input:
```json
{"currentHash": "HASH_A", "registeredHash": "HASH_A"}
```

Output:
```json
{"status": "VERIFIED"}
```

Backend may change paths/auth/transport, but the semantic data contract should remain equivalent.

## 9. Blockchain Interface

AI/Core should not depend directly on a blockchain SDK. It provides:

```json
{
  "mediaHash": "HASH_A",
  "previousHash": null,
  "operation": "ORIGINAL"
}
```

or for a derived version:
```json
{
  "mediaHash": "HASH_B",
  "previousHash": "HASH_A",
  "operation": "CROP"
}
```

Blockchain developer handles testnet, wallet, contract, deployment, ABI, transactions, lookup, and integration. Actual media is never stored on-chain.

## 10. Cloudinary Integration Boundary

```text
User Upload
    |
    v
Backend
    |
    v
Cloudinary Upload
    |
    +--> AI analysis / metadata
    +--> Transformation
    +--> Optimized delivery
    |
    v
Media bytes
    |
    v
SHA-256
    |
    v
VeriFrame Provenance
    |
    v
Blockchain proof
```

Cloudinary credentials remain server-side.

## 11. Security Rules

- Never commit `.env`.
- Never expose Cloudinary API Secret in frontend code.
- Never commit blockchain private keys.
- Never commit database passwords.
- Never store actual media on-chain.
- `.env.example` contains variable names only.
- Hashes do not replace access control or encryption.

## 12. MVP Scope

### Must have
- SHA-256 fingerprinting
- Original registration
- Derived media/provenance model
- VERIFIED/MISMATCH/NOT_FOUND
- AI analysis normalization
- Cloudinary integration boundary
- Blockchain integration contract
- Automated core tests

### Later
- Video/audio provenance
- Advanced visual similarity
- Moderation
- Search API
- Multiple chains
- Advanced AI-generated-media detection

Focus on images first.

## 13. Core Test Cases

1. **Original:** Register → hash → verify same image → `VERIFIED`
2. **Modified:** Register image → modify → hash → `MISMATCH`
3. **Unknown:** Upload unrelated image → hash → no record → `NOT_FOUND`
4. **Deterministic hashing:** identical bytes always return the same SHA-256 value.

## 14. Definition of Done

- [ ] SHA-256 hashing works reliably.
- [ ] Hash format documented.
- [ ] Media identity model defined.
- [ ] Provenance model implemented.
- [ ] Verification returns VERIFIED/MISMATCH/NOT_FOUND.
- [ ] AI output has normalized schema.
- [ ] Core tests pass.
- [ ] Backend contract documented.
- [ ] Blockchain data contract documented.
- [ ] No secrets committed.
- [ ] Core logic is independent of frontend.
- [ ] Core logic is independent of a particular blockchain SDK.

## 15. Team Integration

### AI/Core → Backend
Provide the hash interface, provenance schema, verification interface, AI schema, and example request/response JSON.

### AI/Core → Blockchain
Provide `mediaHash`, `previousHash`, `operation`, and `mediaId` if required.

### Backend → Frontend
Backend exposes application-level responses. Frontend should not need blockchain internals.

## 16. Architectural Principle

**Separate identity, provenance, AI metadata, storage, and blockchain proof.**

```text
                 +-------------------+
                 |    Cloudinary     |
                 | upload/AI/transform|
                 +---------+---------+
                           |
                           v
+---------+       +-------------------+
| Frontend| ----> |      Backend      |
+---------+       +---------+---------+
                            |
             +--------------+--------------+
             |              |              |
             v              v              v
          AI/Core        Database      Blockchain
             |
             v
          SHA-256
             |
             v
        Verification
```

The design should allow Cloudinary, the AI provider, database, or blockchain network to change independently without changing the fundamental media identity and verification rules.
