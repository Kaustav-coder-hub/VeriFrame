import { useRef, useState } from 'react';
import {
  registerOriginal,
  registerVersion,
  getHistory,
  getMediaPassport,
  isLiveBackend,
} from '../lib/api';

const OPERATIONS = ['CROP', 'BG_REMOVAL'];

function short(hash) {
  return hash ? `${hash.slice(0, 10)}…${hash.slice(-6)}` : '—';
}

function fmtTime(iso) {
  if (!iso) return 'Pending';

  const date = new Date(iso);

  if (Number.isNaN(date.getTime())) {
    return 'Pending';
  }

  return date.toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export default function Register() {
  const [file, setFile] = useState(null);
  const [drag, setDrag] = useState(false);
  const [stage, setStage] = useState('idle');
  const [record, setRecord] = useState(null);
  const [history, setHistory] = useState([]);
  const [op, setOp] = useState(OPERATIONS[0]);
  const [busy, setBusy] = useState(false);

  const inputRef = useRef(null);

  // ─────────────────────────────────────────────
  // REGISTER ORIGINAL
  // ─────────────────────────────────────────────

  async function handleFile(f) {
    if (!f) return;

    setFile(f);
    setStage('analyzing');
    setBusy(true);

    try {
      const result = await registerOriginal(f);

      if (!result?.mediaId) {
        throw new Error('Backend did not return a mediaId.');
      }

      // Get complete media passport
      const passport = await getMediaPassport(result.mediaId);

      setRecord({
        ...result,
        ...passport,
      });

      // Get provenance timeline
      const provenance = await getHistory(result.mediaId);

      setHistory(provenance || []);
      setStage('registered');
    } catch (error) {
      console.error('Registration failed:', error);

      alert(
        `Registration failed: ${
          error?.message || 'Unknown error'
        }`
      );

      setStage('idle');
      setFile(null);
    } finally {
      setBusy(false);
    }
  }

  // ─────────────────────────────────────────────
  // CREATE DERIVED VERSION
  // ─────────────────────────────────────────────

  async function handleVersion() {
    if (!record?.mediaId || busy) return;

    setBusy(true);

    try {
      // Backend performs the Cloudinary transformation.
      const result = await registerVersion(
        record.mediaId,
        op
      );

      // Refresh complete passport
      const passport = await getMediaPassport(
        record.mediaId
      );

      setRecord({
        ...record,
        ...passport,
        ...result,
      });

      // Refresh provenance timeline
      const provenance = await getHistory(
        record.mediaId
      );

      setHistory(provenance || []);
    } catch (error) {
      console.error('Transformation failed:', error);

      alert(
        `Transformation failed: ${
          error?.message || 'Unknown error'
        }`
      );
    } finally {
      setBusy(false);
    }
  }

  // ─────────────────────────────────────────────
  // RESET
  // ─────────────────────────────────────────────

  function reset() {
    setFile(null);
    setRecord(null);
    setHistory([]);
    setStage('idle');
    setOp(OPERATIONS[0]);

    if (inputRef.current) {
      inputRef.current.value = '';
    }
  }

  // ─────────────────────────────────────────────
  // CURRENT DATA
  // ─────────────────────────────────────────────

  const latest =
    history.length > 0
      ? history[history.length - 1]
      : null;

  const originalVersion =
    record?.originalVersion || null;

  const currentHash =
    latest?.hash ||
    latest?.mediaHash ||
    record?.hash ||
    record?.currentHash ||
    originalVersion?.sha256Hash ||
    null;

  const currentOperation =
    latest?.operation ||
    record?.operation ||
    originalVersion?.operation ||
    'NONE';

  const txHash =
    latest?.txHash ||
    record?.txHash ||
    null;

  const chainStatus =
    latest?.chainStatus ||
    null;

  return (
    <div className="register-page">
      {/* ─────────────────────────────────────────
          HEADER
      ───────────────────────────────────────── */}

      <section style={{ padding: '4px 0 28px' }}>
        <h1
          style={{
            fontFamily: 'var(--serif)',
            fontSize: 30,
            margin: '0 0 6px',
            fontWeight: 500,
          }}
        >
          Register media
        </h1>

        <p
          className="muted"
          style={{
            margin: 0,
            fontSize: 15,
          }}
        >
          Upload a file to fingerprint it and open a
          provenance record.
          {!isLiveBackend &&
            ' Running in demo mode — connect VITE_API_URL to use the live chain.'}
        </p>
      </section>

      {/* ─────────────────────────────────────────
          UPLOAD
      ───────────────────────────────────────── */}

      {stage === 'idle' && (
        <div
          className={`dropzone${drag ? ' drag' : ''}`}
          onClick={() => inputRef.current?.click()}
          onDragOver={(e) => {
            e.preventDefault();
            setDrag(true);
          }}
          onDragLeave={() => setDrag(false)}
          onDrop={(e) => {
            e.preventDefault();
            setDrag(false);

            const droppedFile =
              e.dataTransfer.files?.[0];

            if (droppedFile) {
              handleFile(droppedFile);
            }
          }}
          role="button"
          tabIndex={0}
        >
          <p
            style={{
              margin: 0,
              fontSize: 15,
              fontWeight: 500,
            }}
          >
            Drop an image, or click to browse
          </p>

          <p className="hint">
            The original file is fingerprinted before
            Cloudinary processing.
          </p>

          <input
            ref={inputRef}
            type="file"
            accept="image/*"
            style={{ display: 'none' }}
            onChange={(e) => {
              const selectedFile =
                e.target.files?.[0];

              if (selectedFile) {
                handleFile(selectedFile);
              }
            }}
          />
        </div>
      )}

      {/* ─────────────────────────────────────────
          ANALYZING
      ───────────────────────────────────────── */}

      {stage === 'analyzing' && (
        <div className="card">
          <p style={{ margin: 0 }}>
            Registering{' '}
            <strong>{file?.name}</strong>…
          </p>

          <p className="hint">
            Uploading to Cloudinary, generating the
            fingerprint and creating the provenance record.
          </p>
        </div>
      )}

      {/* ─────────────────────────────────────────
          REGISTERED
      ───────────────────────────────────────── */}

      {stage === 'registered' && record && (
        <>
          {/* FILE CARD */}

          <div
            className="card"
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
            }}
          >
            <div>
              <strong>{file?.name}</strong>

              <div className="hint">
                {file
                  ? `${Math.round(
                      file.size / 1024
                    )} KB`
                  : ''}
              </div>
            </div>

            <button
              className="btn btn-ghost"
              onClick={reset}
              disabled={busy}
            >
              Start over
            </button>
          </div>

          {/* ─────────────────────────────────────
              PROVENANCE RECORD
          ───────────────────────────────────── */}

          <section
            className="card"
            style={{
              background: '#1c1b18',
              color: '#f5f2ea',
              marginTop: 10,
            }}
          >
            <p
              style={{
                margin: '0 0 6px',
                fontSize: 12,
                opacity: 0.65,
              }}
            >
              Provenance record
            </p>

            <h2
              style={{
                margin: '0 0 18px',
                fontFamily: 'var(--serif)',
                fontWeight: 500,
              }}
            >
              <span
                style={{
                  color:
                    chainStatus === 'FAILED'
                      ? '#e25c5c'
                      : '#45e27a',
                }}
              >
                ●
              </span>{' '}
              {chainStatus === 'FAILED'
                ? 'Registration failed'
                : 'Registered'}
            </h2>

            <div className="detail-row">
              <span>Operation</span>
              <strong>{currentOperation}</strong>
            </div>

            <div className="detail-row">
              <span>Fingerprint</span>
              <strong>
                {short(currentHash)}
              </strong>
            </div>

            <div className="detail-row">
              <span>Transaction</span>
              <strong>
                {short(txHash)}
              </strong>
            </div>

            {chainStatus && (
              <div className="detail-row">
                <span>Blockchain</span>
                <strong>{chainStatus}</strong>
              </div>
            )}
          </section>

          {/* ─────────────────────────────────────
              DERIVED VERSION
          ───────────────────────────────────── */}

          <section className="card">
            <h3 style={{ marginTop: 0 }}>
              Create a derived version
            </h3>

            <p className="hint">
              Apply a Cloudinary transformation and link
              the resulting fingerprint to this record.
            </p>

            <div className="chips">
              {OPERATIONS.map((operation) => (
                <button
                  key={operation}
                  className={
                    op === operation
                      ? 'chip active'
                      : 'chip'
                  }
                  onClick={() => setOp(operation)}
                  disabled={busy}
                >
                  {operation.replace('_', ' ').toLowerCase()}
                </button>
              ))}
            </div>

            <div
              className="card"
              style={{
                marginTop: 14,
                padding: '12px 14px',
              }}
            >
              {op === 'CROP'
                ? 'Cloudinary crop transformation'
                : 'Cloudinary background removal transformation'}
            </div>

            <button
              className="btn btn-primary"
              style={{ marginTop: 12 }}
              onClick={handleVersion}
              disabled={busy}
            >
              {busy
                ? 'Processing…'
                : 'Register version'}
            </button>
          </section>

          {/* ─────────────────────────────────────
              PROVENANCE TIMELINE
          ───────────────────────────────────── */}

          <section className="card">
            <h3 style={{ marginTop: 0 }}>
              Provenance timeline
            </h3>

            <p className="hint">
              Original to current — oldest first
            </p>

            {history.length === 0 ? (
              <p className="hint">
                Waiting for provenance data…
              </p>
            ) : (
              <div>
                {history.map((h, index) => {
                  const hash =
                    h.hash ||
                    h.mediaHash ||
                    null;

                  return (
                    <div
                      key={
                        h.id ||
                        `${hash}-${index}`
                      }
                      style={{
                        display: 'flex',
                        gap: 12,
                        padding: '12px 0',
                        borderBottom:
                          index ===
                          history.length - 1
                            ? 'none'
                            : '1px solid var(--border)',
                      }}
                    >
                      <div
                        style={{
                          width: 10,
                          height: 10,
                          borderRadius: '50%',
                          background:
                            h.chainStatus ===
                            'CONFIRMED'
                              ? '#45e27a'
                              : h.chainStatus ===
                                'FAILED'
                              ? '#e25c5c'
                              : '#d79b3f',
                          marginTop: 5,
                          flexShrink: 0,
                        }}
                      />

                      <div>
                        <strong>
                          {h.operation || 'NONE'}
                        </strong>

                        <div className="hint">
                          {fmtTime(h.createdAt)}
                        </div>

                        <div
                          className="hint"
                          style={{
                            fontFamily:
                              'monospace',
                            marginTop: 3,
                          }}
                        >
                          {short(hash)}
                        </div>

                        {h.chainStatus && (
                          <div className="hint">
                            Blockchain:{' '}
                            {h.chainStatus}
                          </div>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </section>
        </>
      )}
    </div>
  );
}