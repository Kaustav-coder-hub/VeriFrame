import { useRef, useState } from 'react';
import { verifyMedia, getHistory, isLiveBackend } from '../lib/api';

function short(value) {
  return value
    ? `${value.slice(0, 10)}…${value.slice(-6)}`
    : '';
}

function fmtTime(item) {
  const iso =
    item?.createdAt ??
    item?.timestamp ??
    item?.confirmedAt ??
    item;

  if (!iso) return '';

  const date = new Date(iso);

  if (Number.isNaN(date.getTime())) {
    return '';
  }

  return date.toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export default function Verify() {
  const [file, setFile] = useState(null);
  const [drag, setDrag] = useState(false);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState(null);
  const [history, setHistory] = useState([]);

  const inputRef = useRef(null);

  // ─────────────────────────────────────────────
  // VERIFY FILE
  // ─────────────────────────────────────────────

  async function run(f) {
    if (!f) return;

    setFile(f);
    setBusy(true);
    setResult(null);
    setHistory([]);

    try {
      const res = await verifyMedia(f);

      setResult(res);

      /*
       * IMPORTANT:
       * /verify returns both the computed hash and mediaId.
       *
       * Provenance endpoint expects mediaId (UUID),
       * NOT the SHA-256 hash.
       */
      if (res.status === 'VERIFIED' && res.mediaId) {
        const provenance = await getHistory(res.mediaId);

        setHistory(
          Array.isArray(provenance)
            ? provenance
            : []
        );
      }
    } catch (error) {
      console.error('Verification failed:', error);

      setResult({
        status: 'ERROR',
        message:
          error?.message ||
          'Verification failed.',
      });

      setHistory([]);
    } finally {
      setBusy(false);
    }
  }

  // ─────────────────────────────────────────────
  // RESET
  // ─────────────────────────────────────────────

  function reset() {
    setFile(null);
    setResult(null);
    setHistory([]);
    setBusy(false);

    if (inputRef.current) {
      inputRef.current.value = '';
    }
  }

  // ─────────────────────────────────────────────
  // MATCHED VERSION
  // ─────────────────────────────────────────────

  const matchedVersion =
    result?.status === 'VERIFIED' && history.length > 0
      ? (
          history.find(
            (h) =>
              (h.hash || h.mediaHash) ===
              result.hash
          ) || history[history.length - 1]
        )
      : null;

  // ─────────────────────────────────────────────
  // RENDER
  // ─────────────────────────────────────────────

  return (
    <div>

      {/* ─────────────────────────────────────────
          HEADER
      ───────────────────────────────────────── */}

      <section
        style={{
          padding: '4px 0 28px',
        }}
      >
        <h1
          style={{
            fontFamily: 'var(--serif)',
            fontSize: 30,
            margin: '0 0 6px',
            fontWeight: 500,
          }}
        >
          Verify a file
        </h1>

        <p
          className="muted"
          style={{
            margin: 0,
            fontSize: 15,
          }}
        >
          Check whether a file matches a version already
          on record.

          {!isLiveBackend &&
            ' Demo mode checks only files registered this session.'}
        </p>
      </section>

      {/* ─────────────────────────────────────────
          DROPZONE
      ───────────────────────────────────────── */}

      {!file && (
        <div
          className={`dropzone${drag ? ' drag' : ''}`}
          onClick={() =>
            inputRef.current?.click()
          }
          onDragOver={(e) => {
            e.preventDefault();
            setDrag(true);
          }}
          onDragLeave={() =>
            setDrag(false)
          }
          onDrop={(e) => {
            e.preventDefault();
            setDrag(false);

            if (e.dataTransfer.files[0]) {
              run(e.dataTransfer.files[0]);
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
            Drop a file to verify, or click to browse
          </p>

          <p className="hint">
            Its fingerprint is checked against the
            provenance registry.
          </p>

          <input
            ref={inputRef}
            type="file"
            onChange={(e) => {
              if (e.target.files[0]) {
                run(e.target.files[0]);
              }
            }}
          />
        </div>
      )}

      {/* ─────────────────────────────────────────
          SELECTED FILE
      ───────────────────────────────────────── */}

      {file && (
        <div className="card">
          <div className="file-row">
            <div>
              <div className="name">
                {file.name}
              </div>

              <div className="meta">
                {(file.size / 1024).toFixed(0)} KB
              </div>
            </div>

            <button
              className="btn btn-ghost"
              onClick={reset}
            >
              Check another file
            </button>
          </div>
        </div>
      )}

      {/* ─────────────────────────────────────────
          LOADING
      ───────────────────────────────────────── */}

      {busy && (
        <p className="center-note">
          Recomputing fingerprint and checking the
          registry…
        </p>
      )}

      {/* ─────────────────────────────────────────
          VERIFICATION RESULT
      ───────────────────────────────────────── */}

      {result && !busy && (
        <div
          className="seal-card"
          style={{ marginTop: 14 }}
        >
          <p className="verdict-label">
            Verification result
          </p>

          <p className="verdict">
            <span
              className={`verdict-dot ${
                result.status === 'VERIFIED'
                  ? 'ok'
                  : 'bad'
              }`}
            />

            {result.status === 'VERIFIED'
              ? 'Verified'
              : result.status === 'MISMATCH'
                ? 'Mismatch'
                : 'Verification failed'}
          </p>

          {/* HASH */}

          {result.hash && (
            <div className="row">
              <span>Fingerprint</span>
              <span>
                {short(result.hash)}
              </span>
            </div>
          )}

          {/* VERIFIED DETAILS */}

          {result.status === 'VERIFIED' &&
            matchedVersion && (
              <>
                <div className="row">
                  <span>Operation</span>
                  <span>
                    {matchedVersion.operation ||
                      'ORIGINAL'}
                  </span>
                </div>

                <div className="row">
                  <span>Blockchain</span>
                  <span>
                    {matchedVersion.chainStatus ||
                      'UNKNOWN'}
                  </span>
                </div>

                {matchedVersion.recordId != null && (
                  <div className="row">
                    <span>Record ID</span>
                    <span>
                      #{matchedVersion.recordId}
                    </span>
                  </div>
                )}

                {matchedVersion.txHash && (
                  <div className="row">
                    <span>Transaction</span>

                    <span>
                      {short(
                        matchedVersion.txHash
                      )}
                    </span>
                  </div>
                )}

                {matchedVersion.createdAt && (
                  <div className="row">
                    <span>Registered</span>

                    <span>
                      {fmtTime(
                        matchedVersion.createdAt
                      )}
                    </span>
                  </div>
                )}

                {matchedVersion.confirmedAt && (
                  <div className="row">
                    <span>Confirmed</span>

                    <span>
                      {fmtTime(
                        matchedVersion.confirmedAt
                      )}
                    </span>
                  </div>
                )}

                {matchedVersion.explorerUrl && (
                  <div className="row">
                    <span>Explorer</span>

                    <a
                      href={
                        matchedVersion.explorerUrl
                      }
                      target="_blank"
                      rel="noreferrer"
                      style={{
                        color: 'inherit',
                        textDecoration:
                          'underline',
                      }}
                    >
                      View transaction
                    </a>
                  </div>
                )}
              </>
            )}

          {/* MISMATCH */}

          {result.status === 'MISMATCH' && (
            <div className="row">
              <span>Meaning</span>

              <span
                style={{
                  textAlign: 'right',
                }}
              >
                This fingerprint does not match
                a registered version.
              </span>
            </div>
          )}

          {/* ERROR */}

          {result.status === 'ERROR' && (
            <div className="row">
              <span>Error</span>

              <span
                style={{
                  textAlign: 'right',
                }}
              >
                {result.message ||
                  'Unable to verify this file.'}
              </span>
            </div>
          )}
        </div>
      )}

      {/* ─────────────────────────────────────────
          COMPLETE PROVENANCE TIMELINE
      ───────────────────────────────────────── */}

      {history.length > 0 && (
        <div
          className="card"
          style={{ marginTop: 14 }}
        >
          <p className="card-title">
            Provenance timeline
          </p>

          <p className="card-sub">
            Original to this version
          </p>

          <div className="lineage">

            {/* IMPORTANT:
                ONLY ONE history.map().
                Do NOT nest another history.map()
                inside this one.
            */}

            {history.map((h, i) => {
              const hash =
                h.hash || h.mediaHash;

              const parentHash =
                h.previousHash;

              const operation =
                h.operation || 'ORIGINAL';

              return (
                <div
                  key={
                    h.recordId != null
                      ? `record-${h.recordId}`
                      : `${hash}-${i}`
                  }
                  className={`lineage-item${
                    i === 0
                      ? ' origin'
                      : ''
                  }`}
                >

                  {/* OPERATION */}

                  <div className="lineage-op">
                    {operation}
                  </div>

                  {/* TIME + HASH */}

                  <div className="lineage-meta">
                    {fmtTime(h)}

                    {hash && ' · '}

                    {short(hash)}
                  </div>

                  {/* RECORD */}

                  {h.recordId != null && (
                    <div
                      style={{
                        marginTop: 4,
                        fontSize: 12,
                        color:
                          'var(--ink-soft)',
                        lineHeight: 1.6,
                      }}
                    >
                      Record: #{h.recordId}
                    </div>
                  )}

                  {/* PARENT */}

                  {parentHash && (
                    <div
                      style={{
                        fontSize: 12,
                        color:
                          'var(--ink-soft)',
                        lineHeight: 1.6,
                      }}
                    >
                      Parent:{' '}
                      {short(parentHash)}
                    </div>
                  )}

                  {/* BLOCKCHAIN */}

                  <div
                    style={{
                      fontSize: 12,
                      color:
                        'var(--ink-soft)',
                      lineHeight: 1.6,
                    }}
                  >
                    Blockchain:{' '}
                    {h.chainStatus ||
                      'UNKNOWN'}
                  </div>

                  {/* TRANSACTION */}

                  {h.txHash && (
                    <div
                      style={{
                        fontSize: 12,
                        color:
                          'var(--ink-soft)',
                        lineHeight: 1.6,
                      }}
                    >
                      TX: {short(h.txHash)}
                    </div>
                  )}

                  {/* CONFIRMED */}

                  {h.confirmedAt && (
                    <div
                      style={{
                        fontSize: 12,
                        color:
                          'var(--ink-soft)',
                        lineHeight: 1.6,
                      }}
                    >
                      Confirmed:{' '}
                      {fmtTime(
                        h.confirmedAt
                      )}
                    </div>
                  )}

                  {/* BLOCKCHAIN EXPLORER */}

                  {h.explorerUrl && (
                    <a
                      href={h.explorerUrl}
                      target="_blank"
                      rel="noreferrer"
                      style={{
                        display:
                          'inline-block',
                        marginTop: 4,
                        fontSize: 12,
                        color: 'inherit',
                      }}
                    >
                      View on blockchain →
                    </a>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}