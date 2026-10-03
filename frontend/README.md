# Frontend — Media Provenance

React + Vite UI covering the hackathon MVP flow: upload → fingerprint → register →
create a derived version → see the timeline → verify a file.

## Screens
- **Home** (`/`) — explains the product, links to Register and Verify.
- **Register** (`/register`) — upload/create page + media details + provenance
  timeline combined into one flow: drop a file, it's fingerprinted and registered,
  then you can register a derived version (CROP, BG_REMOVAL, AI_ENHANCE, OPTIMIZE)
  linked to the original, and see the lineage build up.
- **Verify** (`/verify`) — drop a file, see VERIFIED or MISMATCH against the
  registry, and the matching record's timeline if found.

## Talking to the blockchain module
This UI calls the REST API from `/blockchain` (`src/server.js`): `/api/register`,
`/api/version`, `/api/verify`, `/api/history/:hash`. See `src/lib/api.js`.

Set the API's URL in `.env` (copy `.env.example`):
```
VITE_API_URL=http://localhost:4000
```
**Demo mode:** if `VITE_API_URL` is unset or the backend is unreachable, the UI
falls back to an in-memory mock so the flow is still clickable/demoable without
the backend running. Register/timeline work fully in this mode; Verify in mock
mode only recognizes files registered earlier in the same browser session
(it can't recompute a real hash match without the backend).

## Setup
```bash
npm install
cp .env.example .env   # point at the real API once it's running
npm run dev             # http://localhost:5173
npm run build            # production build -> dist/
```

## Design
Warm paper background, near-black "seal" cards for verdicts (registered /
verified / mismatch), amber accent for provenance markers, serif headings with
a grotesk sans for UI text and monospace for hashes. Tokens live at the top of
`src/styles.css`.
