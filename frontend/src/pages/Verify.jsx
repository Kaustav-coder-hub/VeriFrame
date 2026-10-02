import { useRef, useState } from 'react';
import { verifyMedia, getHistory, isLiveBackend } from '../lib/api';

function short(hash) {
  return hash ? `${hash.slice(0, 10)}…${hash.slice(-6)}` : '';
}

function fmtTime(iso) {
  return new Date(iso).toLocaleString(undefined, {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

export default function Verify() {
  const [file, setFile] = useState(null);
  const [drag, setDrag] = useState(false);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState(null);
  const [history, setHistory] = useState([]);
  const inputRef = useRef(null);

  async function run(f) {
    setFile(f);
    setBusy(true);
    setResult(null);
    const res = await verifyMedia(f);
    setResult(res);
    if (res.status === 'VERIFIED') setHistory(await getHistory(res.hash));
    else setHistory([]);
    setBusy(false);
  }

  function reset() { setFile(null); setResult(null); setHistory([]); }

  return (
    <div>
      <section style={{ padding: '4px 0 28px' }}>
        <h1 style={{ fontFamily: 'var(--serif)', fontSize: 30, margin: '0 0 6px', fontWeight: 500 }}>
          Verify a file
        </h1>
        <p className="muted" style={{ margin: 0, fontSize: 15 }}>
          Check whether a file matches a version already on record.
          {!isLiveBackend && ' Demo mode checks only files registered this session.'}
        </p>
      </section>

      {!file && (
        <div
          className={`dropzone${drag ? ' drag' : ''}`}
          onClick={() => inputRef.current.click()}
          onDragOver={(e) => { e.preventDefault(); setDrag(true); }}
          onDragLeave={() => setDrag(false)}
          onDrop={(e) => {
            e.preventDefault(); setDrag(false);
            if (e.dataTransfer.files[0]) run(e.dataTransfer.files[0]);
          }}
          role="button"
          tabIndex={0}
        >
          <p style={{ margin: 0, fontSize: 15, fontWeight: 500 }}>Drop a file to verify, or click to browse</p>
          <p className="hint">Its fingerprint is checked against the provenance registry.</p>
          <input ref={inputRef} type="file" onChange={(e) => e.target.files[0] && run(e.target.files[0])} />
        </div>
      )}

      {file && (
        <div className="card">
          <div className="file-row">
            <div>
              <div className="name">{file.name}</div>
              <div className="meta">{(file.size / 1024).toFixed(0)} KB</div>
            </div>
            <button className="btn btn-ghost" onClick={reset}>Check another file</button>
          </div>
        </div>
      )}

      {busy && <p className="center-note">Recomputing fingerprint and checking the registry…</p>}

      {result && !busy && (
        <div className={`seal-card`} style={{ marginTop: 14 }}>
          <p className="verdict-label">Verification result</p>
          <p className="verdict">
            <span className={`verdict-dot ${result.status === 'VERIFIED' ? 'ok' : 'bad'}`} />
            {result.status === 'VERIFIED' ? 'Verified' : 'Mismatch'}
          </p>
          <div className="row"><span>Fingerprint</span><span>{short(result.hash)}</span></div>
          {result.record && (
            <>
              <div className="row"><span>Operation</span><span>{result.record.operation}</span></div>
              <div className="row"><span>Registered</span><span>{fmtTime(result.record.timestamp)}</span></div>
            </>
          )}
          {result.status === 'MISMATCH' && (
            <div className="row"><span>Meaning</span><span style={{ textAlign: 'right' }}>No matching record found</span></div>
          )}
        </div>
      )}

      {history.length > 0 && (
        <div className="card">
          <p className="card-title">Provenance timeline</p>
          <p className="card-sub">Original to this version</p>
          <div className="lineage">
            {history.map((h, i) => (
              <div key={h.mediaHash} className={`lineage-item${i === 0 ? ' origin' : ''}`}>
                <div className="lineage-op">{h.operation}</div>
                <div className="lineage-meta">{fmtTime(h.timestamp)} · {short(h.mediaHash)}</div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
