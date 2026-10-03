# VeriFrame

> AI-powered media provenance and verification for authenticating digital media and tracing transformations across versions.

VeriFrame brings together Cloudinary, cryptographic hashing, a Spring Boot backend, and blockchain-backed provenance to create a verifiable chain of ownership and transformation for media files.

It helps answer one critical question: "Is this the exact file that was registered, and what transformation history led to it?"

---

## Why VeriFrame?

Digital media can be copied, transformed, and re-shared without a trusted record of its origin. Metadata is easy to alter, and centralized logs alone are not independently verifiable.

VeriFrame solves this by combining:

- Cloudinary-powered media processing and transformation
- SHA-256 hashing for exact file fingerprinting
- Parent-child version chaining for provenance tracking
- Blockchain-backed registration for tamper-evident records
- Fast verification workflows for matching or mismatched media

---

## The Core Workflow

```text
Upload media
  ↓
Cloudinary processing
  ↓
Transformations (crop, background removal, etc.)
  ↓
SHA-256 fingerprint generation
  ↓
Blockchain provenance registration
  ↓
Verification and timeline lookup
```

```text
ORIGINAL
   │
   ▼
CROP
   │
   ▼
BACKGROUND REMOVAL
```

Each version receives a unique fingerprint and is linked to its parent version, creating a secure provenance chain.

---

## Key Features

### 📤 Media Registration

Register an uploaded image or media file and store its exact cryptographic fingerprint.

### ☁️ Cloudinary Media Pipeline

Use Cloudinary for:

- media upload
- AI/media analysis
- transformation workflows
- delivery and asset processing

### ✂️ Transformations and Versioning

Track new versions created through operations such as:

- Crop
- Background removal
- other derived media changes

### 🔐 SHA-256 Fingerprinting

Every file version is reduced to a deterministic SHA-256 hash. Even a single-byte change results in a different hash.

### ⛓️ Blockchain Provenance

Each registered media record is stored through the `MediaProvenance` smart contract to create an independently verifiable source of truth.

### ✅ Exact Verification

Upload a file to verify whether it matches a registered version exactly.

### 📜 Provenance Timeline

Display the history of each file including:

- operation
- timestamp
- SHA-256 hash
- record ID
- parent hash
- blockchain status
- transaction hash
- confirmation time
- explorer link

### 🚨 Tamper Detection

If a file is modified after registration, its SHA-256 fingerprint changes and VeriFrame identifies the mismatch.

---

## Verification States

| Status              | Meaning                                                    |
| ------------------- | ---------------------------------------------------------- |
| `VERIFIED`          | The uploaded file matches a registered version exactly     |
| `MISMATCH`          | The file does not match the registered hash                |
| `NOT_FOUND`         | No provenance record exists for that fingerprint           |
| `PENDING`           | The registration is still awaiting blockchain confirmation |
| `PROVENANCE_FAILED` | Registration or provenance recording failed                |

---

## System Architecture

```text
                         ┌─────────────────────┐
                         │        User         │
                         │ Upload / Verify     │
                         └──────────┬──────────┘
                                    │
                                    ▼
                         ┌─────────────────────┐
                         │   React + Vite      │
                         │     Frontend        │
                         └──────────┬──────────┘
                                    │ REST API
                                    ▼
                    ┌──────────────────────────────────┐
                    │          Spring Boot             │
                    │            Backend               │
                    │ Upload | Transform | SHA-256     │
                    │ Provenance | Verification        │
                    └──────────────┬───────────────────┘
                                   │
                     ┌─────────────┼─────────────┐
                     ▼             ▼             ▼
            ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
            │   Cloudinary │  │   Database   │  │  SHA-256     │
            │ Upload + AI  │  │ Media +      │  │ fingerprint  │
            │ Transform    │  │ Provenance   │  │              │
            └──────┬───────┘  └──────┬───────┘  └──────┬───────┘
                   │                 │                 │
                   └─────────────────┴─────────────────┘
                                              │
                                              ▼
                                      ┌──────────────────┐
                                      │ Blockchain Layer │
                                      │ Node.js + Smart  │
                                      │ Contract         │
                                      └──────────────────┘
```

---

## Technology Stack

### Frontend

- React
- Vite
- JavaScript
- CSS

### Backend

- Java 21
- Spring Boot
- Spring Security
- Spring Data JPA
- Flyway
- H2 for local development
- PostgreSQL-compatible configuration
- Cloudinary Java SDK

### AI / Media

- Cloudinary
- AI-powered media processing
- SHA-256 fingerprinting

### Blockchain

- Solidity
- Ethereum-compatible blockchain integration
- ethers.js
- Node.js
- Express
- Ganache for local testing

### Tools

- Git
- GitHub
- npm
- Maven Wrapper

---

## Project Structure

```text
VeriFrame/
├── frontend/
│   ├── src/
│   │   ├── components/
│   │   ├── lib/
│   │   ├── pages/
│   │   ├── App.jsx
│   │   ├── main.jsx
│   │   ├── styles.css
│   │   └── package.json
│   ├── vite.config.js
│   ├── index.html
│   └── README.md
│
├── backend/
│   ├── src/
│   ├── scripts/
│   ├── pom.xml
│   ├── mvnw
│   ├── mvnw.cmd
│   ├── Dockerfile
│   └── README.md
│
├── blockchain/
│   ├── contracts/
│   ├── src/
│   ├── scripts/
│   ├── test/
│   ├── package.json
│   └── README.md
│
├── ai/
│   ├── src/
│   ├── tests/
│   └── requirements.txt
│
├── docs/
├── docker-compose.yml
├── README.md
├── .gitignore
├── pytest.ini
└── .env.example
```

---

## Prerequisites

Before running the project, install:

- Git
- Node.js 20+
- npm
- Java 21

Verify versions:

```bash
git --version
node --version
npm --version
java --version
```

You will also need:

- a Cloudinary account
- Cloudinary credentials
- an Ethereum-compatible RPC endpoint
- a test wallet and private key

> Never commit secrets, API keys, or private keys to version control.

---

## Quick Start

### 1) Clone the repository

```bash
git clone <PUBLIC_GITHUB_REPOSITORY_URL>
cd VeriFrame
```

### 2) Configure environment files

```bash
cp .env.example .env
cp frontend/.env.example frontend/.env
cp backend/.env.example backend/.env
cp blockchain/.env.example blockchain/.env
```

Set your service credentials in each `.env` file.

### 3) Install dependencies

#### Blockchain

```bash
cd blockchain
npm install
```

#### Frontend

```bash
cd ../frontend
npm install
```

### 4) Compile and deploy the smart contract

```bash
cd blockchain
npm run compile
npm run deploy
```

Copy the deployed contract address into the blockchain environment configuration.

---

## Run the Application

### Terminal 1: Blockchain service

```bash
cd blockchain
npm start
```

Health check:

```bash
curl http://localhost:4000/health
```

### Terminal 2: Backend

```bash
cd backend
set -a
source .env
set +a
./mvnw spring-boot:run
```

Health check:

```bash
curl http://localhost:8080/actuator/health
```

### Terminal 3: Frontend

```bash
cd frontend
npm run dev
```

Open the app on:

```text
http://localhost:5173
```

---

## How to Use VeriFrame

### Step 1 — Register an original media file

Upload a file through the frontend register flow.

```text
Upload
  ↓
Cloudinary
  ↓
SHA-256
  ↓
Blockchain
  ↓
CONFIRMED
```

### Step 2 — Apply a transformation

Create a derived version such as:

```text
CROP
```

### Step 3 — Record another version

Generate another derived file such as:

```text
BG_REMOVAL
```

### Step 4 — Verify the original

Upload the exact original media on the verification page.

Expected result:

```text
VERIFIED
```

### Step 5 — Tamper test

Modify the file and verify again.

Expected result:

```text
MISMATCH
```

---

## Demo Flow

```text
00:00 — Introduce the provenance problem
00:20 — Open the app
00:40 — Upload the original asset
01:00 — Show Cloudinary processing
01:20 — Show SHA-256 + blockchain confirmation
01:40 — Apply CROP
02:00 — Apply BG_REMOVAL
02:20 — Show provenance timeline
02:40 — Verify original → VERIFIED
03:00 — Modify asset → MISMATCH
03:20 — Summarize Cloudinary + SHA-256 + blockchain
```

---

## Blockchain Design

The smart contract is `MediaProvenance.sol`.

Each provenance record stores:

```text
recordId
mediaHash
previousHash
operation
timestamp
creator
```

The contract supports:

```solidity
registerMedia()
registerVersion()
verifyHash()
getHistory()
getRecord()
```

For original media, `previousHash` is a zero hash. For derived media, it points to the parent media hash, creating an immutable version history.

---

## Why SHA-256 Matters

```text
Original file
   ↓
SHA-256
   ↓
HASH-A
```

```text
Modified file
   ↓
SHA-256
   ↓
HASH-B
```

Because `HASH-A != HASH-B`, VeriFrame can reliably detect tampering and verify the exact registered version.

---

## API Overview

### Backend

Base URL:

```text
http://localhost:8080/api/v1
```

Common endpoints:

```text
POST /media
GET  /media/{mediaId}/provenance
POST /media/{mediaId}/transform
POST /verify
```

### Blockchain service

Base URL:

```text
http://localhost:4000
```

Endpoints:

```text
GET  /health
POST /api/register
POST /api/version
POST /api/verify
GET  /api/history/:hash
```

---

## Testing

### Blockchain

```bash
cd blockchain
npm test
```

### Backend

```bash
cd backend
./mvnw test
```

### Frontend build

```bash
cd frontend
npm run build
```

---

## Security

The following must never be committed:

```text
.env
backend/.env
frontend/.env
blockchain/.env
```

Never expose:

- Cloudinary API secret
- database credentials
- API keys
- JWT secrets
- private keys

---

## Troubleshooting

### Backend will not start

Check Java:

```bash
java --version
```

Then run:

```bash
./mvnw clean package -DskipTests
```

### Blockchain service fails

Check:

```bash
curl http://localhost:4000/health
```

Verify your environment values:

```env
SEPOLIA_RPC_URL=...
PRIVATE_KEY=...
CONTRACT_ADDRESS=...
```

### Frontend cannot connect to backend

Confirm the services are running on:

```text
Frontend   → http://localhost:5173
Backend    → http://localhost:8080
Blockchain → http://localhost:4000
```

And check:

```env
VITE_API_URL=http://localhost:8080
```

### Verification shows `PENDING`

Wait for blockchain confirmation and retry.

### Verification shows `MISMATCH`

The uploaded file does not exactly match the registered hash; verify that you are using the exact original file.

---

## Project Summary

> VeriFrame uses Cloudinary for AI-powered media processing, SHA-256 for exact file fingerprinting, and blockchain-backed provenance to make digital media history verifiable and tamper-evident.

---

## Team & Links

### Hackathon

**Cloudinary AI Hackathon 2026 — Pixels to Products**  
**Track 1 — AI Media Pipelines**

### Team Members

- `<TEAM_MEMBER_1>`
- `<TEAM_MEMBER_2>`
- `<TEAM_MEMBER_3>`
- `<TEAM_MEMBER_4>`

### Useful Links

| Resource      | URL                   |
| ------------- | --------------------- |
| Live Demo     | `<LIVE_DEMO_URL>`     |
| GitHub        | `<PUBLIC_GITHUB_URL>` |
| Demo Video    | `<DEMO_VIDEO_URL>`    |
| LinkedIn Post | `<LINKEDIN_POST_URL>` |

Replace the placeholder values before submission or public release.
