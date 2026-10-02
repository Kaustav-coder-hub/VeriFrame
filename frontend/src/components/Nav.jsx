import { NavLink } from 'react-router-dom';

const links = [
  { to: '/', label: 'Home', end: true },
  { to: '/register', label: 'Register' },
  { to: '/verify', label: 'Verify' },
];

export default function Nav() {
  return (
    <div className="register-strip">
      <NavLink to="/" className="wordmark" style={{ textDecoration: 'none' }}>
        <span className="seal">V</span>
        VeriFrame
      </NavLink>
      <nav className="nav-links">
        {links.map((l) => (
          <NavLink
            key={l.to}
            to={l.to}
            end={l.end}
            className={({ isActive }) => (isActive ? 'active' : '')}
          >
            {l.label}
          </NavLink>
        ))}
      </nav>
    </div>
  );
}
