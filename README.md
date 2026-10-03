# VeriFrame — AI-Powered Media Provenance & Verification

> **Cloudinary AI Hackathon 2026 — Pixels to Products**  
> **Track 1 — AI Media Pipelines**

VeriFrame is an AI-powered media provenance and verification platform that helps users verify the authenticity and transformation history of digital media.

It combines **Cloudinary**, **SHA-256 fingerprinting**, a **Spring Boot backend**, and a **blockchain-backed provenance layer** to create a verifiable chain from an original media file to its transformed versions.

---

## 1. Problem Statement

Digital images can be copied, modified, transformed, and redistributed without an easy way to determine:

- Where a particular file came from
- Whether a file is the exact registered version
- What transformations were applied
- Which version was derived from which original
- Whether the current file has been modified after registration

Traditional file metadata can be removed or changed, and storing provenance only in a centralized database does not provide an independently verifiable record.

VeriFrame addresses this problem by combining **cryptographic file fingerprinting with Cloudinary media processing and blockchain-backed provenance records**.

---

## 2. Our Solution

VeriFrame creates a provenance record for every registered media version.

The core workflow is:

```text
Upload Media
     ↓
Cloudinary
     ↓
AI / Media Processing
     ↓
Transformation
     ↓
SHA-256 Fingerprint
     ↓
Blockchain Registration
     ↓
Verification
     ↓
Provenance Timeline
```

For example:

```text
ORIGINAL
   │
   ▼
CROP
   │
   ▼
BACKGROUND REMOVAL
```

Each version receives its own SHA-256 fingerprint and is linked to its parent version.

This allows VeriFrame to answer:

> **Is this exact file a registered version, and what is its transformation history?**

---

## 3. Hackathon Track

### Track 1 — AI Media Pipelines

VeriFrame is built around an active media pipeline using Cloudinary.

The application:

- Ingests uploaded media
- Uses Cloudinary for media management
- Uses Cloudinary AI/media capabilities for analysis and processing
- Creates transformed media versions
- Generates cryptographic fingerprints for the resulting files
- Records provenance relationships
- Verifies media against registered fingerprints
- Displays the complete provenance timeline

Cloudinary is therefore an active part of the product workflow rather than simply being used as static file storage.

---


## 🚀 Live Demo

The complete VeriFrame application is deployed on Render and can be tested directly without setting up the project locally.

### Production URLs

| Component | Live URL | Purpose |
|---|---|---|
| **Frontend / Live Demo** | https://veriframe-frontend-x8u4.onrender.com | Main VeriFrame web application |
| **Backend API** | https://veriframe-backend-java.onrender.com | Spring Boot REST API |
| **Backend Health** | https://veriframe-backend-java.onrender.com/actuator/health | Backend + database health check |
| **Blockchain Service** | https://veriframe-o9gg.onrender.com | Blockchain provenance API |
| **Blockchain Health** | https://veriframe-o9gg.onrender.com/health | Blockchain service health check |

### Judge Quick Start

1. Open the **[VeriFrame Live Demo](https://veriframe-frontend-x8u4.onrender.com)**.
2. Upload/register an original image.
3. Wait for the provenance status to become **CONFIRMED**.
4. Create transformations such as **CROP** and **BACKGROUND REMOVAL**.
5. Open the provenance/history view to see the chain:

```text
ORIGINAL → CROP → BACKGROUND REMOVAL
```

6. Use **Verify** with the exact registered file and confirm that the result is **VERIFIED**.
7. Modify the image or use an unregistered file and verify it again. The expected result is **MISMATCH**.

> **Note about Render cold starts:** The deployed backend and blockchain services may take a short time to wake up after a period of inactivity on Render's free infrastructure. If the first request takes longer than expected, wait for the service to wake and retry the request.

For the most reliable judging experience, open the live demo and the two health endpoints shortly before the judging session and perform one complete upload → transformation → verification flow.

# 4. Key Features

### 📤 Media Registration

Upload an image and register its exact file fingerprint.

### ☁️ Cloudinary Media Pipeline

Use Cloudinary for:

- Media upload
- Media management
- AI/media analysis
- Image transformations
- Media delivery

### ✂️ Media Transformations

Create derived versions such as:

- Crop
- Background removal

### 🔐 SHA-256 Fingerprinting

Every exact file version receives a SHA-256 fingerprint.

Even a small change to the file produces a different hash.

### ⛓️ Blockchain Provenance

Registered fingerprints and their relationships are recorded through the `MediaProvenance` smart contract.

### 🔗 Parent-Child Versioning

Derived media references the fingerprint of its parent:

```text
Original Hash
     ↓
Crop Hash
     ↓
Background Removal Hash
```

### ✅ Exact Verification

Upload a file and VeriFrame checks whether its fingerprint matches a registered record.

### 📜 Provenance Timeline

The application displays:

- Operation
- Timestamp
- SHA-256 hash
- Record ID
- Parent hash
- Blockchain status
- Transaction hash
- Confirmation time
- Explorer link

### 🚨 Tamper Detection

If a registered file is modified, its SHA-256 fingerprint changes and VeriFrame can identify the mismatch.

---

# 5. Verification Results

VeriFrame can return statuses such as:

| Status | Meaning |
|---|---|
| `VERIFIED` | Exact file hash is registered |
| `MISMATCH` | File does not match the expected registered version |
| `NOT_FOUND` | No provenance record exists for the hash |
| `PENDING` | Blockchain registration is still processing |
| `PROVENANCE_FAILED` | Blockchain provenance registration failed |

---

# 6. How VeriFrame Works

## Original Media

```text
Image
  ↓
SHA-256
  ↓
Original Hash
  ↓
Blockchain Record
```

## Derived Media

```text
Original
   │
   ├── CROP
   │      ↓
   │   New Hash
   │      ↓
   │   Blockchain Record
   │
   └── BG_REMOVAL
          ↓
       New Hash
          ↓
       Blockchain Record
```

Each derived version stores the hash of its parent.

---

# 7. Cloudinary Integration

Cloudinary is a core component of the VeriFrame media pipeline.

## Media Upload

Uploaded media is sent through the backend to Cloudinary.

```text
User
 ↓
React Frontend
 ↓
Spring Boot Backend
 ↓
Cloudinary
```

## AI / Media Analysis

Cloudinary AI/media capabilities are used to analyze and process uploaded media.

## Transformations

Cloudinary transformations are used to create derived media versions such as:

```text
Original
   ↓
Crop
   ↓
Background Removal
```

## Provenance Integration

After media processing, the resulting file is fingerprinted:

```text
Cloudinary Output
       ↓
Exact File Bytes
       ↓
SHA-256
       ↓
Parent / Child Relationship
       ↓
Blockchain
```

This means Cloudinary participates directly in the actual product workflow.

---

# 8. System Architecture

```text
                       ┌────────────────────┐
                       │       USER         │
                       │ Upload / Verify    │
                       └─────────┬──────────┘
                                 │
                                 ▼
                       ┌────────────────────┐
                       │    React + Vite    │
                       │     Frontend       │
                       └─────────┬──────────┘
                                 │ REST API
                                 ▼
                 ┌──────────────────────────────┐
                 │        Spring Boot           │
                 │          Backend             │
                 │                              │
                 │ Upload                       │
                 │ Transform                    │
                 │ SHA-256                      │
                 │ Provenance                   │
                 │ Verification                 │
                 └───────┬──────────────┬───────┘
                         │              │
                         ▼              ▼
              ┌────────────────┐  ┌───────────────┐
              │   Cloudinary   │  │   Database    │
              │                │  │               │
              │ Upload         │  │ Media         │
              │ AI/Analysis    │  │ Versions      │
              │ Transform      │  │ Provenance    │
              │ Delivery       │  │ Status        │
              └───────┬────────┘  └───────────────┘
                      │
                      ▼
               ┌──────────────┐
               │   SHA-256    │
               │ Fingerprint  │
               └──────┬───────┘
                      │
                      ▼
             ┌───────────────────┐
             │ Blockchain Service│
             │    Node.js        │
             └─────────┬─────────┘
                       │
                       ▼
             ┌────────────────────┐
             │ MediaProvenance.sol│
             │   Smart Contract   │
             └────────────────────┘
```

---

# 9. Technology Stack

## Frontend

- React
- Vite
- JavaScript
- CSS

## Backend

- Java 21
- Spring Boot
- Spring Security
- Spring Data JPA
- Flyway
- H2 for local development
- PostgreSQL-compatible database configuration
- Cloudinary Java SDK

## Media / AI

- Cloudinary
- Cloudinary AI/media capabilities
- SHA-256

## Blockchain

- Solidity
- Ethereum-compatible blockchain
- ethers.js
- Node.js
- Express
- Ganache for local blockchain testing

## Development

- Git
- GitHub
- npm
- Maven Wrapper

---

# 10. Project Structure

```text
VeriFrame/
│
├── frontend/
│   ├── src/
│   │   ├── pages/
│   │   │   ├── Home.jsx
│   │   │   ├── Register.jsx
│   │   │   └── Verify.jsx
│   │   ├── ...
│   │   ├── package.json
│   │   └── .env.example
│
├── backend/
│   ├── src/
│   │   ├── main/
│   │   └── test/
│   ├── pom.xml
│   ├── mvnw
│   ├── mvnw.cmd
│   └── .env.example
│
├── blockchain/
│   ├── contracts/
│   │   └── MediaProvenance.sol
│   ├── src/
│   │   ├── provenance.js
│   │   └── server.js
│   ├── scripts/
│   │   ├── compile.js
│   │   ├── deploy.js
│   │   ├── demo-test.js
│   │   └── generate-wallet.js
│   ├── test/
│   ├── package.json
│   └── .env.example
│
├── ai/
│   ├── src/
│   └── tests/
│
├── docs/
├── .env.example
├── .gitignore
└── README.md
```

---

# 11. Prerequisites

Install the following:

- Git
- Node.js 20+
- npm
- Java 21

Check:

```bash
git --version
node --version
npm --version
java --version
```

The backend includes the Maven Wrapper, so a global Maven installation is not required.

You will also need:

- A Cloudinary account
- Cloudinary credentials
- An Ethereum-compatible RPC endpoint for blockchain deployment
- A test wallet/private key for the blockchain environment

> **Never commit API secrets, passwords, or private keys to GitHub.**

---

# 12. Clone the Repository

```bash
git clone <PUBLIC_GITHUB_REPOSITORY_URL>
cd VeriFrame
```

Replace `<PUBLIC_GITHUB_REPOSITORY_URL>` with the final public repository URL.

---

# 13. Environment Configuration

Use the example environment files provided in the repository.

```bash
cp .env.example .env
cp frontend/.env.example frontend/.env
cp backend/.env.example backend/.env
cp blockchain/.env.example blockchain/.env
```

Configure the values for your environment.

---

## Cloudinary

Configure:

```env
CLOUDINARY_CLOUD_NAME=your_cloud_name
CLOUDINARY_API_KEY=your_api_key
CLOUDINARY_API_SECRET=your_api_secret
```

---

## Frontend

Configure the backend URL:

```env
VITE_API_URL=http://localhost:8080
```

Use any project-specific frontend API key/configuration required by the supplied `.env.example`.

---

## Blockchain

Configure:

```env
SEPOLIA_RPC_URL=your_rpc_url
PRIVATE_KEY=your_test_wallet_private_key
CONTRACT_ADDRESS=your_deployed_contract_address
```

Use a dedicated testnet wallet.

---

# 14. Install Dependencies

## Blockchain

```bash
cd blockchain
npm install
```

## Frontend

```bash
cd ../frontend
npm install
```

The backend uses the included Maven Wrapper and does not require a global Maven installation.

---

# 15. Compile the Smart Contract

```bash
cd blockchain
npm run compile
```

Expected:

```text
Compiled OK -> artifacts/MediaProvenance.json
```

---

# 16. Deploy the Smart Contract

Configure the blockchain `.env` first.

Then:

```bash
npm run deploy
```

Copy the deployed contract address into:

```env
CONTRACT_ADDRESS=...
```

---

# 17. Start the Blockchain Service

From the `blockchain` directory:

```bash
npm start
```

Expected:

```text
Provenance API on http://localhost:4000
```

Health check:

```bash
curl http://localhost:4000/health
```

Keep this terminal running.

---

# 18. Start the Backend

Open a second terminal:

```bash
cd VeriFrame/backend
```

Load the environment:

```bash
set -a
source .env
set +a
```

Start Spring Boot:

```bash
./mvnw spring-boot:run
```

Backend:

```text
http://localhost:8080
```

Health check:

```bash
curl http://localhost:8080/actuator/health
```

Expected:

```json
{
  "status": "UP"
}
```

Keep this terminal running.

---

# 19. Start the Frontend

Open a third terminal:

```bash
cd VeriFrame/frontend
npm run dev
```

Open the URL shown by Vite, normally:

```text
http://localhost:5173
```

---

# 20. Running the Complete Application

Three services need to be running:

### Terminal 1

```bash
cd blockchain
npm start
```

### Terminal 2

```bash
cd backend
set -a
source .env
set +a
./mvnw spring-boot:run
```

### Terminal 3

```bash
cd frontend
npm run dev
```

Then open:

```text
http://localhost:5173
```

---

# 21. How to Use VeriFrame

## Step 1 — Register an Original

Upload an image through the Register page.

The system:

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

---

## Step 2 — Create a Transformation

Apply a supported transformation such as:

```text
CROP
```

The resulting file receives a new SHA-256 fingerprint.

---

## Step 3 — Create Another Version

Apply:

```text
BG_REMOVAL
```

The provenance chain becomes:

```text
NONE
 ↓
CROP
 ↓
BG_REMOVAL
```

---

## Step 4 — Verify the Original

Upload the exact original file on the Verify page.

Expected result:

```text
VERIFIED
```

The provenance timeline shows the registered history.

---

## Step 5 — Test Tampering

Modify the image and verify it again.

Expected result:

```text
MISMATCH
```

Even a small change to the file changes its SHA-256 fingerprint.

---

# 22. Judge Demo Flow

For a short demonstration:

```text
00:00 — Introduce the media provenance problem

00:20 — Open VeriFrame

00:40 — Upload original image

01:00 — Show Cloudinary processing

01:20 — Show SHA-256 + blockchain confirmation

01:40 — Apply CROP

02:00 — Apply BG_REMOVAL

02:20 — Show provenance timeline

02:40 — Verify original → VERIFIED

03:00 — Modify image → MISMATCH

03:20 — Summarize Cloudinary + SHA-256 + blockchain
```

---

# 23. Blockchain Design

The smart contract is:

```text
MediaProvenance.sol
```

A provenance record contains:

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

For an original file:

```text
previousHash = zero hash
```

For a derived version:

```text
previousHash = parent media hash
```

This creates a linked provenance history.

---

# 24. Why SHA-256?

SHA-256 generates a deterministic fingerprint from the exact file bytes.

Example:

```text
Original File
     ↓
SHA-256
     ↓
HASH-A
```

After modification:

```text
Modified File
     ↓
SHA-256
     ↓
HASH-B
```

Therefore:

```text
HASH-A ≠ HASH-B
```

VeriFrame uses this property to determine whether a file matches a registered version.

---

# 25. API Overview

## Backend

Base URL:

```text
http://localhost:8080/api/v1
```

Important endpoints include:

```text
POST /media
GET  /media/{mediaId}/provenance
POST /media/{mediaId}/transform
POST /verify
```

## Blockchain Service

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

# 26. Testing

## Blockchain

```bash
cd blockchain
npm test
```

The blockchain tests cover:

- Deterministic SHA-256 hashing
- Original registration
- Verification
- Tampered file detection
- One-byte modification detection
- Version chaining
- Duplicate registration rejection
- Unknown parent rejection
- API flow
- Error handling
- Health endpoint

## Backend

```bash
cd backend
./mvnw test
```

## Frontend Production Build

```bash
cd frontend
npm run build
```

---

# 27. Security

The following files must never be committed:

```text
.env
backend/.env
frontend/.env
blockchain/.env
```

Never expose:

```text
Cloudinary API Secret
API keys
Database passwords
Private keys
JWT secrets
```

The repository should contain only safe example configuration files.

Before making the repository public, review the Git history for accidentally committed credentials as well as checking the current files.

---

# 28. Troubleshooting

## Backend does not start

Check Java:

```bash
java --version
```

The project requires Java 21.

Then try:

```bash
./mvnw clean package -DskipTests
```

---

## Blockchain service does not start

Check:

```bash
curl http://localhost:4000/health
```

Then verify:

```env
SEPOLIA_RPC_URL=...
PRIVATE_KEY=...
CONTRACT_ADDRESS=...
```

---

## Frontend cannot connect to backend

Confirm:

```text
Frontend   → http://localhost:5173
Backend    → http://localhost:8080
Blockchain → http://localhost:4000
```

Check:

```env
VITE_API_URL=http://localhost:8080
```

---

## Verification shows `PENDING`

Blockchain registration may still be processing.

Wait for the transaction to be confirmed and retry verification.

---

## Verification shows `MISMATCH`

The uploaded file's SHA-256 fingerprint does not match the registered hash.

Verify that you are using the exact registered file.

---

# 29. Why VeriFrame?

VeriFrame connects three important layers:

```text
Cloudinary
   +
Cryptographic Fingerprinting
   +
Blockchain Provenance
```

Cloudinary handles the media lifecycle and transformation pipeline.

SHA-256 provides an exact fingerprint of each file.

Blockchain provides a tamper-evident provenance record connecting versions together.

Together, these components create a practical media verification workflow.

---

# 30. Links

| Resource | URL |
|---|---|
| Live Demo | https://veriframe-frontend-x8u4.onrender.com |
| Backend API | https://veriframe-backend-java.onrender.com |
| Backend Health | https://veriframe-backend-java.onrender.com/actuator/health |
| Blockchain Service | https://veriframe-o9gg.onrender.com |
| Blockchain Health | https://veriframe-o9gg.onrender.com/health |
| GitHub | `<PUBLIC_GITHUB_URL>` |
| Demo Video | `<DEMO_VIDEO_URL>` |
| LinkedIn Project Post | `<LINKEDIN_POST_URL>` |

---

# 31. Team

## VeriFrame

**Cloudinary AI Hackathon 2026 — Pixels to Products**

Team Members:

- `<TEAM_MEMBER_1>`
- `<TEAM_MEMBER_2>`
- `<TEAM_MEMBER_3>`
- `<TEAM_MEMBER_4>`

Replace the placeholders with the final team details.

---

## One-Line Summary

> **VeriFrame uses Cloudinary for AI-powered media processing and transformation, SHA-256 for exact file fingerprinting, and blockchain-backed provenance to make digital media history verifiable and tamper-evident.**
