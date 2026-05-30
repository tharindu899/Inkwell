/* ══════════════════════════════════════════
   Inkwell — src/pages/Login.jsx
   Full-screen Google sign-in page.
   ══════════════════════════════════════════ */

import { useAuth, CLIENT_ID } from '../auth/AuthContext';

export default function Login() {
  const { signIn, gisReady, signingIn, error } = useAuth();
  const hasClientId = !!CLIENT_ID;

  return (
    <div className="login-shell">
      {/* ── Decorative ink blobs ── */}
      <div className="login-blob b1" />
      <div className="login-blob b2" />

      <div className="login-card">
        {/* ── Logo ── */}
        <div className="login-logo-wrap">
          <img
            src="/icon.svg"
            alt="Inkwell app icon"
            className="login-logo-img"
            width="72"
            height="72"
            decoding="async"
          />
        </div>

        <h1 className="login-title">Inkwell</h1>
        <p className="login-sub">Your notes. Synced. Private.</p>

        {/* ── Feature list ── */}
        <ul className="login-features">
          {[
            ['✦', 'Write beautifully, anywhere'],
            ['☁', 'Auto-sync via Google Drive'],
            ['🔒', 'Your data stays yours'],
          ].map(([icon, text]) => (
            <li key={text} className="login-feature-item">
              <span className="lfi-icon">{icon}</span>
              <span>{text}</span>
            </li>
          ))}
        </ul>

        {/* ── Sign-in area ── */}
        {!hasClientId ? (
          <div className="login-env-warning">
            <i className="fa-solid fa-triangle-exclamation" />
            <div>
              <strong>Setup required</strong>
              <p>Add <code>VITE_GOOGLE_CLIENT_ID</code> to your <code>.env</code> file.<br />See <code>.env.example</code> for details.</p>
            </div>
          </div>
        ) : (
          <button
            className={`google-btn${signingIn ? ' loading' : ''}`}
            onClick={() => signIn('select_account')}
            disabled={!gisReady || signingIn}
          >
            {signingIn ? (
              <>
                <span className="google-btn-spinner" />
                Signing in…
              </>
            ) : (
              <>
                <svg className="google-g" viewBox="0 0 48 48" xmlns="http://www.w3.org/2000/svg">
                  <path fill="#EA4335" d="M24 9.5c3.5 0 6.6 1.2 9.1 3.2l6.8-6.8C35.9 2.2 30.3 0 24 0 14.7 0 6.7 5.4 2.9 13.3l7.9 6.1C12.6 13.5 17.9 9.5 24 9.5z"/>
                  <path fill="#4285F4" d="M46.5 24.5c0-1.6-.1-3.1-.4-4.5H24v8.5h12.7c-.6 3-2.3 5.5-4.8 7.2l7.5 5.8c4.4-4.1 7.1-10.1 7.1-17z"/>
                  <path fill="#FBBC05" d="M10.8 28.6A14.4 14.4 0 0 1 9.5 24c0-1.6.3-3.2.8-4.6l-7.9-6.1A23.8 23.8 0 0 0 0 24c0 3.9.9 7.5 2.4 10.7l8.4-6.1z"/>
                  <path fill="#34A853" d="M24 48c6.5 0 11.9-2.1 15.9-5.8l-7.5-5.8c-2.2 1.5-5 2.4-8.4 2.4-6.1 0-11.4-4.1-13.2-9.7l-8.4 6.1C6.8 42.7 14.8 48 24 48z"/>
                  <path fill="none" d="M0 0h48v48H0z"/>
                </svg>
                Sign in with Google
              </>
            )}
          </button>
        )}

        {error && (
          <div className="login-error-msg">
            <i className="fa-solid fa-circle-exclamation" /> {error}
          </div>
        )}

        <p className="login-footer">
          Your notes are stored locally and optionally backed up to your own Google Drive — never our servers.
        </p>
      </div>
    </div>
  );
}
