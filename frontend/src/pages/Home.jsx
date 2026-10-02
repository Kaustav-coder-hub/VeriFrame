import { Link } from 'react-router-dom';

const steps = [
  'Upload',
  'Cloudinary processes it',
  'AI analyzes or transforms it',
  'Fingerprint is hashed',
  'Record is registered on-chain',
];

export default function Home() {
  return (
    <div>
      <section className="hero">
        <h1>A passport for every image and video.</h1>
        <p>
          Upload media and VeriFrame gives it a verifiable history: where it came from,
          what was done to it, and whether a file you're looking at now matches the
          version that was originally registered.
        </p>
        <div className="hero-actions">
          <Link to="/register" className="btn btn-primary">Register media</Link>
          <Link to="/verify" className="btn btn-ghost">Verify a file</Link>
        </div>
      </section>

      <div className="flow-row">
        {steps.map((s, i) => (
          <span key={s} style={{ display: 'contents' }}>
            <div className="flow-step">
              <span className="num">{i + 1}</span>
              <span className="label">{s}</span>
            </div>
            {i < steps.length - 1 && <span className="flow-arrow">→</span>}
          </span>
        ))}
      </div>

      <div className="grid-2" style={{ marginTop: 28 }}>
        <div className="card">
          <p className="card-title">What stays on-chain</p>
          <p className="card-sub">Small and purposeful</p>
          <p style={{ fontSize: 14, color: 'var(--ink-soft)', lineHeight: 1.6, margin: 0 }}>
            A media fingerprint, the hash of its parent version, the operation name,
            a timestamp, and the registering account. The image or video itself never
            leaves Cloudinary.
          </p>
        </div>
        <div className="card">
          <p className="card-title">What you can check later</p>
          <p className="card-sub">Match or mismatch, instantly</p>
          <p style={{ fontSize: 14, color: 'var(--ink-soft)', lineHeight: 1.6, margin: 0 }}>
            Drop in any file and VeriFrame recomputes its fingerprint and checks it
            against the registry — no account or wallet needed to verify.
          </p>
        </div>
      </div>
    </div>
  );
}
