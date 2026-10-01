# VeriFrame Backend

Spring Boot service for **media provenance**. It uploads images to Cloudinary, SHA-256 hashes the exact bytes, registers the hashes on a blockchain (mock or real), and lets anyone verify a file against its recorded history.

- **Stack:** Java 21, Spring Boot 3.3.3, Spring Security, JPA + Flyway, H2 (local) / PostgreSQL (prod), Cloudinary SDK, springdoc-openapi
- **Base URL:** `http://localhost:8080`
- **API prefix:** `/api/v1`

---

## 1. Prerequisites

| Tool | Version |
|---|---|
| JDK | 21 |
| Maven | not needed, wrapper included (`mvnw` / `mvnw.cmd`) |
| Docker + Docker Compose | only for the Docker / PostgreSQL run |
| Cloudinary account | needed for real uploads (cloud name, API key, API secret) |
| `curl`, `jq` | only for the smoke test |

---

## 2. Configuration

Copy the example file and fill in your values:

```bash
cd backend
cp .env.example .env
```

| Variable | Default | Description |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` | `local` (H2 + Swagger) or `prod` (PostgreSQL) |
| `SERVER_PORT` | `8080` | HTTP port |
| `SECURITY_API_KEY` | `dev-secret-api-key-change-in-prod` | Required in the `X-API-KEY` header for write requests |
| `FRONTEND_ORIGIN` | `http://localhost:5173,http://localhost:3000` | CORS allowed origins (comma separated) |
| `CLOUDINARY_CLOUD_NAME` | `demo` (local only) | Cloudinary cloud name |
| `CLOUDINARY_API_KEY` | `1234567890` (local only) | Cloudinary API key |
| `CLOUDINARY_API_SECRET` | placeholder (local only) | Cloudinary API secret |
| `CLOUDINARY_AUTO_TAGGING_CATEGORIZATION` | `aws_rek_tagging` | Cloudinary AI auto-tagging add-on |
| `BLOCKCHAIN_MODE` | `MOCK` | `MOCK` (in-memory ledger) or `HTTP` (real blockchain service) |
| `BLOCKCHAIN_SERVICE_URL` | `http://localhost:4000` | Blockchain service URL (used when mode is `HTTP`) |
| `BLOCKCHAIN_CONNECT_TIMEOUT_MS` | `5000` | Connect timeout |
| `BLOCKCHAIN_READ_TIMEOUT_STANDARD_MS` | `10000` | Read timeout for normal calls |
| `BLOCKCHAIN_READ_TIMEOUT_LONG_MS` | `120000` | Read timeout for writes (mining) |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `5432` / `mediaprovenance` | PostgreSQL (prod profile) |
| `DB_USERNAME` / `DB_PASSWORD` | `postgres` / `postgres` | PostgreSQL credentials |
| `UPLOAD_MAX_FILE_SIZE` | `10MB` | Max size of one file |
| `UPLOAD_MAX_REQUEST_SIZE` | `12MB` | Max size of one request |

> With the default `demo` Cloudinary credentials, uploads will fail. Set real Cloudinary credentials to test upload and transform.

---

## 3. Running commands

### A. Local (H2 in-memory DB, Swagger enabled, mock blockchain)

Linux / macOS:

```bash
cd backend
export CLOUDINARY_CLOUD_NAME=your_cloud_name
export CLOUDINARY_API_KEY=your_api_key
export CLOUDINARY_API_SECRET=your_api_secret
./mvnw spring-boot:run
```

Windows (PowerShell):

```powershell
cd backend
$env:CLOUDINARY_CLOUD_NAME="your_cloud_name"
$env:CLOUDINARY_API_KEY="your_api_key"
$env:CLOUDINARY_API_SECRET="your_api_secret"
.\mvnw.cmd spring-boot:run
```

Server starts at **http://localhost:8080**. Swagger UI: **http://localhost:8080/swagger-ui.html**

### B. Build a jar and run it

```bash
cd backend
./mvnw clean package -DskipTests
java -jar target/media-provenance-backend-0.0.1-SNAPSHOT.jar
```

### C. Docker Compose (PostgreSQL + backend, `prod` profile)

Run from the repository root (where `docker-compose.yml` is):

```bash
docker compose up --build        # start
docker compose up -d --build     # start in background
docker compose down              # stop
docker compose down -v           # stop and delete the database volume
```

Pass your own secrets through the shell or a root `.env` file:

```bash
SECURITY_API_KEY=my-secret \
CLOUDINARY_CLOUD_NAME=xxx CLOUDINARY_API_KEY=xxx CLOUDINARY_API_SECRET=xxx \
docker compose up --build
```

In the `prod` profile, Swagger and the H2 console are **disabled**.

### D. Run with a real blockchain service

Start the blockchain module (`blockchain/`, port 4000), then:

```bash
export BLOCKCHAIN_MODE=HTTP
export BLOCKCHAIN_SERVICE_URL=http://localhost:4000
./mvnw spring-boot:run
```

### E. Tests

```bash
cd backend
./mvnw test
```

### F. End-to-end smoke test (backend must be running)

```bash
cd backend
export API_KEY="dev-secret-api-key-change-in-prod"
./scripts/smoke.sh                              # default http://localhost:8080
BASE_URL=http://host:9090 ./scripts/smoke.sh    # custom URL
```

It runs 10 steps: upload, poll AI tags, CROP transform, wait for blockchain confirmation, get passport, get provenance, verify the original (`VERIFIED`), verify a tampered file (`MISMATCH`).

---

## 4. Authentication

| Request type | Auth |
|---|---|
| `POST /api/v1/media` and `POST /api/v1/media/**` | **Required:** header `X-API-KEY: <SECURITY_API_KEY>` |
| `GET` endpoints, `POST /api/v1/verify`, actuator | Public, no key |

A missing or wrong key returns `401 UNAUTHORIZED`.

---

## 5. All endpoints

| # | Method | Path | Auth | Content type | Description |
|---|---|---|---|---|---|
| 1 | `POST` | `/api/v1/media` | API key | `multipart/form-data` | Upload an image, hash it, register on chain |
| 2 | `POST` | `/api/v1/media/{mediaId}/transform` | API key | `application/json` | Create a derived version (CROP or BG_REMOVAL) |
| 3 | `GET` | `/api/v1/media/{mediaId}` | Public | n/a | Get the Media Passport (full details) |
| 4 | `GET` | `/api/v1/media/{mediaId}/provenance` | Public | n/a | Get the provenance timeline |
| 5 | `POST` | `/api/v1/verify` | Public | `multipart/form-data` | Verify a file against recorded hashes |
| 6 | `GET` | `/actuator/health` | Public | n/a | Health check |
| 7 | `GET` | `/actuator/info` | Public | n/a | App info |
| 8 | `GET` | `/swagger-ui.html` | Public, **local profile only** | n/a | Swagger UI |
| 9 | `GET` | `/v3/api-docs` | Public, **local profile only** | n/a | OpenAPI JSON |
| 10 | `ANY` | `/h2-console/**` | Public, **local profile only** | n/a | H2 database console |
| 11 | `OPTIONS` | `/**` | Public | n/a | CORS preflight |

---

### 5.1 `POST /api/v1/media` : Upload

**Headers:** `X-API-KEY`
**Form field:** `file` (required). It must be a JPEG, PNG, GIF or WebP image, max 10 MB.

```bash
curl -X POST http://localhost:8080/api/v1/media \
  -H "X-API-KEY: dev-secret-api-key-change-in-prod" \
  -F "file=@photo.jpg"
```

**Response:** `202 Accepted` for a new upload, `200 OK` if the same file (same hash) already exists.

```json
{
  "mediaId": "uuid",
  "originalVersion": {
    "id": "uuid",
    "versionType": "ORIGINAL",
    "operation": "NONE",
    "parentVersionId": null,
    "sha256Hash": "0x...",
    "cloudinaryUrl": "https://res.cloudinary.com/...",
    "optimizedUrl": "https://res.cloudinary.com/...",
    "width": 1080,
    "height": 720,
    "bytes": 123456,
    "createdAt": "2026-10-02T10:00:00Z"
  },
  "ai": {},
  "provenance": {},
  "duplicate": false
}
```

---

### 5.2 `POST /api/v1/media/{mediaId}/transform` : Transform

**Headers:** `X-API-KEY`, `Content-Type: application/json`
**Path:** `mediaId` (UUID)
**Body:**

| Field | Type | Required | Description |
|---|---|---|---|
| `operation` | string | yes | `CROP` or `BG_REMOVAL` (any other value falls back to the CROP transformation but is stored under that name) |
| `sourceVersionId` | UUID | no | Version to transform. Defaults to the latest version |

```bash
curl -X POST http://localhost:8080/api/v1/media/<mediaId>/transform \
  -H "X-API-KEY: dev-secret-api-key-change-in-prod" \
  -H "Content-Type: application/json" \
  -d '{"operation":"CROP"}'
```

**Response:** `202 Accepted` with the new version:

```json
{
  "id": "uuid",
  "versionType": "TRANSFORMED",
  "operation": "CROP",
  "parentVersionId": "uuid",
  "sha256Hash": "0x...",
  "cloudinaryUrl": "https://...",
  "optimizedUrl": "https://...",
  "width": 1080,
  "height": 1080,
  "bytes": 98765,
  "createdAt": "2026-10-02T10:01:00Z"
}
```

The CROP result is `1080x1080`, `c_fill`, `g_auto`, JPG. Blockchain registration runs in the background, so check `/provenance` for `chainStatus`.

---

### 5.3 `GET /api/v1/media/{mediaId}` : Media Passport

```bash
curl http://localhost:8080/api/v1/media/<mediaId>
```

**Response:** `200 OK`

```json
{
  "id": "uuid",
  "cloudinaryPublicId": "string",
  "cloudinaryAssetId": "string",
  "originalFilename": "photo.jpg",
  "mimeType": "image/jpeg",
  "createdAt": "2026-10-02T10:00:00Z",
  "versions": [ { "...": "MediaVersion objects (see 5.1)" } ],
  "aiResults": [ { "...": "AI tagging results" } ],
  "provenance": [ { "...": "Provenance objects (see 5.4)" } ]
}
```

---

### 5.4 `GET /api/v1/media/{mediaId}/provenance` : Provenance timeline

```bash
curl http://localhost:8080/api/v1/media/<mediaId>/provenance
```

**Response:** `200 OK`, an array ordered oldest to newest:

```json
[
  {
    "id": "uuid",
    "mediaVersionId": "uuid",
    "hash": "0x...",
    "previousHash": null,
    "operation": "NONE",
    "chainStatus": "CONFIRMED",
    "txHash": "0x...",
    "recordId": 1001,
    "explorerUrl": "https://sepolia.etherscan.io/tx/0x...",
    "errorMessage": null,
    "createdAt": "2026-10-02T10:00:00Z",
    "confirmedAt": "2026-10-02T10:00:05Z"
  }
]
```

`chainStatus` values: `PENDING`, `CONFIRMED`, `FAILED`.

---

### 5.5 `POST /api/v1/verify` : Verify a file

No API key needed.

**Form fields:**

| Field | Required | Description |
|---|---|---|
| `file` | yes | The file to check |
| `mediaId` | no | UUID of a media asset to verify against |

```bash
curl -X POST http://localhost:8080/api/v1/verify \
  -F "file=@photo.jpg" \
  -F "mediaId=<mediaId>"
```

**Response body:**

```json
{
  "status": "VERIFIED",
  "hash": "0x...",
  "mediaId": "uuid",
  "matchedVersionId": "uuid",
  "chainStatus": "CONFIRMED",
  "txHash": "0x...",
  "explorerUrl": "https://...",
  "message": "string",
  "source": "string"
}
```

| `status` | HTTP code | Meaning |
|---|---|---|
| `VERIFIED` | 200 | File hash matches a registered version |
| `MISMATCH` | 200 | File was changed, hash does not match |
| `NOT_FOUND` | 200 | No record found for this file |
| `PENDING` | 409 | Blockchain registration not finished yet, retry shortly |
| `PROVENANCE_FAILED` | 409 | Blockchain registration failed |

---

### 5.6 Actuator

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/info
```

Health details are shown in `local` and hidden in `prod`.

---

## 6. Error format

All errors use RFC 7807 `application/problem+json`:

```json
{
  "type": "urn:verimedia:error:media_not_found",
  "title": "MEDIA_NOT_FOUND",
  "status": 404,
  "detail": "Media asset not found: <uuid>",
  "timestamp": "2026-10-02T10:00:00Z",
  "correlationId": "..."
}
```

| HTTP | `title` | When |
|---|---|---|
| 400 | `INVALID_INPUT`, `INVALID_IMAGE`, `UNSUPPORTED_MEDIA_TYPE`, `MISSING_REQUEST_PART`, `MALFORMED_JSON`, `VALIDATION_ERROR`, `TYPE_MISMATCH`, `CONSTRAINT_VIOLATION`, `SSRF_GUARD_TRIGGERED` | Bad request, empty or non-image file, missing `file`, bad JSON or UUID |
| 401 | `UNAUTHORIZED` | Missing or invalid `X-API-KEY` on write endpoints |
| 403 | `ACCESS_DENIED` | Insufficient permissions |
| 404 | `MEDIA_NOT_FOUND`, `VERSION_NOT_FOUND`, `PARENT_NOT_REGISTERED` | Unknown media, version or parent hash |
| 409 | `HASH_ALREADY_REGISTERED` | Hash already on chain |
| 413 | `PAYLOAD_TOO_LARGE` | File larger than the upload limit |
| 429 | `TOO_MANY_REQUESTS` | More than **60 requests per minute per IP** on `POST /api/v1/media*` and `POST /api/v1/verify*` |
| 500 | `INTERNAL_SERVER_ERROR`, `CLOUDINARY_UPLOAD_ERROR`, `DOWNLOAD_ERROR`, `HASHING_ERROR`, `HASH_MISMATCH` | Server or Cloudinary failure |
| 503 | `BLOCKCHAIN_SERVICE_ERROR` | Blockchain service unreachable (HTTP mode) |

Every response includes an `X-Correlation-ID` header. You can send your own (8 to 64 chars: letters, digits, `.`, `_`, `-`).

---

## 7. Typical flow

```bash
KEY="dev-secret-api-key-change-in-prod"

# 1. Upload
curl -X POST localhost:8080/api/v1/media -H "X-API-KEY: $KEY" -F "file=@photo.jpg"

# 2. Transform
curl -X POST localhost:8080/api/v1/media/<mediaId>/transform \
  -H "X-API-KEY: $KEY" -H "Content-Type: application/json" -d '{"operation":"CROP"}'

# 3. Wait for chainStatus = CONFIRMED
curl localhost:8080/api/v1/media/<mediaId>/provenance

# 4. Verify
curl -X POST localhost:8080/api/v1/verify -F "file=@photo.jpg" -F "mediaId=<mediaId>"
```

---

## 8. Blockchain modes

| Mode | Behaviour |
|---|---|
| `MOCK` (default) | In-memory ledger, fake `0xmocktx_...` hashes. No setup needed. Data is lost on restart. |
| `HTTP` | Calls the Node.js blockchain service: `POST /api/register`, `POST /api/version`, `POST /api/verify`, `GET /api/history/{hash}`. Failed writes are retried and pending records are recovered on startup. |

---

## 9. Project structure

```
backend/
├── src/main/java/com/mediaprovenance/
│   ├── media/          # controller, service, DTOs, entities
│   ├── verification/   # verify logic
│   ├── blockchain/     # mock + HTTP clients, retry, recovery
│   ├── cloudinary/     # Cloudinary gateway
│   ├── hashing/        # SHA-256 service
│   ├── provenance/     # provenance records
│   ├── ai/             # AI results
│   ├── config/         # security, CORS, properties
│   └── common/         # exceptions, rate limit, correlation ID
├── src/main/resources/ # application*.yml, Flyway migrations
├── scripts/smoke.sh    # end-to-end test
├── Dockerfile
└── pom.xml
```