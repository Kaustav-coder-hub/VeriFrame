import { useRef, useState } from 'react';
import { registerOriginal, registerVersion, getHistory, isLiveBackend } from '../lib/api';

const OPERATIONS = ['CROP', 'BG_REMOVAL', 'AI_ENHANCE', 'OPTIMIZE'];

function short(hash) {
  return hash ? `${hash.slice(0, 10)}…${hash.slice(-6)}` : '';
}

function fmtTime(iso) {
  return new Date(iso).toLocaleString(undefined, {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

export default function Register() {
  const [file, setFile] = useState(null);
  const [drag, setDrag] = useState(false);
  const [stage, setStage] = useState('idle'); // idle | analyzing | registered
  const [record, setRecord] = useState(null);
  const [history, setHistory] = useState([]);
  const [op, setOp] = useState(OPERATIONS[0]);
  const [versionFile, setVersionFile] = useState(null);
  const [busy, setBusy] = useState(false);
  const inputRef = useRef(null);
  const versionInputRef = useRef(null);

  async function handleFile(f) {
    setFile(f);
    setStage('analyzing');
    setBusy(true);
    const result = await registerOriginal(f, 'ORIGINAL');
    setRecord(result);
    setHistory(await getHistory(result.hash));
    setStage('registered');
    setBusy(false);
  }

  async function handleVersion() {
    if (!versionFile || !record) return;
    setBusy(true);
    const result = await registerVersion(versionFile, record.hash, op);
    setRecord(result);
    setHistory(await getHistory(result.hash));
    setVersionFile(null);
    setBusy(false);
  }

  function reset() {
    setFile(null); setRecord(null); setHistory([]); setStage('idle'); setVersionFile(null);
  }

  return (
    <div>
      <section style={{ padding: '4px 0 28px' }}>
        <h1 style={{ fontFamily: 'var(--serif)', fontSize: 30, margin: '0 0 6px', fontWeight: 500 }}>
          Register media
        </h1>
        <p className="muted" style={{ margin: 0, fontSize: 15 }}>
          Upload a file to fingerprint it and open a provenance record.
          {!isLiveBackend && ' Running in demo mode — connect VITE_API_URL to use the live chain.'}
        </p>
      </section>

      {stage === 'idle' && (
        <div
          className={`dropzone${drag ? ' drag' : ''}`}
          onClick={() => inputRef.current.click()}
          onDragOver={(e) => { e.preventDefault(); setDrag(true); }}
          onDragLeave={() => setDrag(false)}
          onDrop={(e) => {
            e.preventDefault(); setDrag(false);
            if (e.dataTransfer.files[0]) handleFile(e.dataTransfer.files[0]);
          }}
          role="button"
          tabIndex={0}
        >
          <p style={{ margin: 0, fontSize: 15, fontWeight: 500 }}>Drop an image or video, or click to browse</p>
          <p className="hint">Sent to Cloudinary for storage; nothing leaves your device until you drop a file here.</p>
          <input
            ref={inputRef}
            type="file"
            accept="image/*,video/*"
            onChange={(e) => e.target.files[0] && handleFile(e.target.files[0])}
          />
        </div>
      )}

      {stage !== 'idle' && (
        <div className="card">
          <div className="file-row">
            <div>
              <div className="name">{file?.name}</div>
              <div className="meta">{file ? `${(file.size / 1024).toFixed(0)} KB` : ''}</div>
            </div>
            {stage === 'analyzing'
              ? <span className="badge pending">Fingerprinting…</span>
              : <button className="btn btn-ghost" onClick={reset}>Start over</button>}
          </div>
        </div>
      )}

      {stage === 'registered' && record && (
        <>
          <div className="seal-card" style={{ marginTop: 14 }}>
            <p className="verdict-label">Provenance record</p>
            <p className="verdict">
              <span className="verdict-dot ok" />
              Registered
            </p>
            <div className="row"><span>Operation</span><span>{history[history.length - 1]?.operation || 'ORIGINAL'}</span></div>
            <div className="row"><span>Fingerprint</span><span>{short(record.hash)}</span></div>
            {record.previousHash && <div className="row"><span>Derived from</span><span>{short(record.previousHash)}</span></div>}
            <div className="row"><span>Transaction</span><span>{short(record.txHash)}</span></div>
          </div>

          <div className="card">
            <p className="card-title">Create a derived version</p>
            <p className="card-sub">Simulates an AI transformation or edit, then links it to this record</p>
            <div className="chip-row">
              {OPERATIONS.map((o) => (
                <button key={o} className={`chip${op === o ? ' active' : ''}`} onClick={() => setOp(o)}>
                  {o.replace('_', ' ').toLowerCase()}
                </button>
              ))}
            </div>
            <div className="field">
              <label>New file for this version</label>
              <div className="file-row" style={{ cursor: 'pointer' }} onClick={() => versionInputRef.current.click()}>
                <span className="name">{versionFile ? versionFile.name : 'Choose a file…'}</span>
                <span className="meta">{versionFile ? `${(versionFile.size / 1024).toFixed(0)} KB` : ''}</span>
              </div>
              <input
                ref={versionInputRef}
                type="file"
                style={{ display: 'none' }}
                onChange={(e) => e.target.files[0] && setVersionFile(e.target.files[0])}
              />
            </div>
            <button className="btn btn-primary" style={{ marginTop: 16 }} disabled={!versionFile || busy} onClick={handleVersion}>
              {busy ? <span className="spinner" /> : null} Register version
            </button>
          </div>

          <div className="card">
            <p className="card-title">Provenance timeline</p>
            <p className="card-sub">Original to current — oldest first</p>
            {history.length === 0 ? (
              <p className="center-note">No history yet.</p>
            ) : (
              <div className="lineage">
                {history.map((h, i) => (
                  <div key={h.mediaHash} className={`lineage-item${i === 0 ? ' origin' : ''}`}>
                    <div className="lineage-op">{h.operation}</div>
                    <div className="lineage-meta">{fmtTime(h.timestamp)} · {short(h.mediaHash)}</div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}
