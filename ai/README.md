cat > ai/README.md <<'EOF'
# VeriFrame AI/Core

AI and cryptographic core for VeriFrame — an AI-powered media provenance and verification platform.

## Responsibilities

The AI/Core module handles:

- SHA-256 media fingerprinting
- Media identity generation
- Media provenance tracking
- Derived-version relationships
- Media verification
- Verification statuses:
  - `VERIFIED`
  - `MISMATCH`
  - `NOT_FOUND`

## Architecture

```text
Media File
    ↓
SHA-256 Hash
    ↓
Media Identity
    ↓
Provenance Engine
    ↓
Verification Engine
    ↓
VERIFIED / MISMATCH / NOT_FOUND