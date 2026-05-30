/* ══════════════════════════════════════════
   Inkwell — src/pages/Profile.jsx
   Shows Google account info + writing stats.
   ══════════════════════════════════════════ */

import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import TopBar from '../components/TopBar';
import BottomNav from '../components/BottomNav';
import Toast, { showToast } from '../components/Toast';
import { useAppStore } from '../store/AppContext';
import { useAuth } from '../auth/AuthContext';
import { getStats } from '../store/storage';
import { fmtNum } from '../utils/helpers';

// ── Activity grid: 98 days ──
function buildActivityCells(notes) {
  const countMap = {};
  notes.forEach(n => {
    const k = (n.updatedAt || n.createdAt || '').slice(0, 10);
    if (k) countMap[k] = (countMap[k] || 0) + 1;
  });
  const max   = Math.max(1, ...Object.values(countMap));
  const today = new Date();
  return Array.from({ length: 98 }, (_, i) => {
    const d = new Date(today);
    d.setDate(d.getDate() - (97 - i));
    const k = d.toISOString().slice(0, 10);
    const v = countMap[k] || 0;
    const lvl = v === 0 ? 0 : v / max < 0.25 ? 1 : v / max < 0.5 ? 2 : v / max < 0.75 ? 3 : 4;
    return { k, v, lvl };
  });
}

// ── Streak calculation ──
function calcStreak(notes) {
  const days  = new Set(notes.map(n => (n.updatedAt || '').slice(0, 10)).filter(Boolean));
  let streak = 0, best = 0, cur = 0;
  const today = new Date();
  for (let i = 0; i < 365; i++) {
    const d = new Date(today); d.setDate(d.getDate() - i);
    const k = d.toISOString().slice(0, 10);
    if (days.has(k)) {
      cur++;
      best = Math.max(best, cur);
      if (i === 0 || streak > 0) streak = cur;
    } else if (i > 0) cur = 0;
  }
  return { streak, best };
}

export default function Profile() {
  const navigate           = useNavigate();
  const { notes }          = useAppStore();
  const { user, signOut }  = useAuth();

  const [confirmSignOut, setConfirmSignOut] = useState(false);

  const stats              = getStats();
  const { streak, best }   = calcStreak(notes);
  const cells              = buildActivityCells(notes);

  function handleSignOut() {
    signOut();
    navigate('/login', { replace: true });
  }

  return (
    <div className="app-shell">
      <TopBar back title="Profile" />

      <main className="page-content">

        {/* ── Google Account Hero ── */}
        <div className="profile-hero">
          <div className="avatar-wrap">
            {user?.picture ? (
              <img
                src={user.picture}
                alt={user.name}
                className="p-avatar-img"
                referrerPolicy="no-referrer"
              />
            ) : (
              <div className="p-avatar">
                <span>{(user?.name || 'U')[0].toUpperCase()}</span>
              </div>
            )}
            {/* Google badge */}
            <div className="google-badge" title="Signed in with Google">
              <svg viewBox="0 0 48 48" xmlns="http://www.w3.org/2000/svg" width="14" height="14">
                <path fill="#EA4335" d="M24 9.5c3.5 0 6.6 1.2 9.1 3.2l6.8-6.8C35.9 2.2 30.3 0 24 0 14.7 0 6.7 5.4 2.9 13.3l7.9 6.1C12.6 13.5 17.9 9.5 24 9.5z"/>
                <path fill="#4285F4" d="M46.5 24.5c0-1.6-.1-3.1-.4-4.5H24v8.5h12.7c-.6 3-2.3 5.5-4.8 7.2l7.5 5.8c4.4-4.1 7.1-10.1 7.1-17z"/>
                <path fill="#FBBC05" d="M10.8 28.6A14.4 14.4 0 0 1 9.5 24c0-1.6.3-3.2.8-4.6l-7.9-6.1A23.8 23.8 0 0 0 0 24c0 3.9.9 7.5 2.4 10.7l8.4-6.1z"/>
                <path fill="#34A853" d="M24 48c6.5 0 11.9-2.1 15.9-5.8l-7.5-5.8c-2.2 1.5-5 2.4-8.4 2.4-6.1 0-11.4-4.1-13.2-9.7l-8.4 6.1C6.8 42.7 14.8 48 24 48z"/>
              </svg>
            </div>
          </div>
          <div className="p-name">{user?.name || 'Inkwell User'}</div>
          <div className="p-email">{user?.email || ''}</div>
          <div className="p-google-tag">
            <i className="fa-brands fa-google" style={{ fontSize: '10px' }} />
            &nbsp;Google Account
          </div>
        </div>

        {/* ── Stats ── */}
        <div className="p-stats" style={{ marginBottom: '16px' }}>
          <div className="ps"><span className="ps-num">{stats.notes}</span><div className="ps-label">Notes</div></div>
          <div className="ps"><span className="ps-num">{stats.notebooks}</span><div className="ps-label">Books</div></div>
          <div className="ps"><span className="ps-num">{fmtNum(stats.words)}</span><div className="ps-label">Words</div></div>
        </div>

        {/* ── Streak ── */}
        <div className="streak-card">
          <div className="streak-icon">🔥</div>
          <div className="streak-info">
            <div className="s-num">{streak}</div>
            <div className="s-label">day writing streak</div>
          </div>
          <div style={{ marginLeft: 'auto', textAlign: 'right' }}>
            <div style={{ fontSize: '11px', color: 'var(--text-3)' }}>Longest</div>
            <div style={{ fontFamily: 'var(--font-s)', fontSize: '16px', fontWeight: 700, color: 'var(--text-2)' }}>
              {best}
            </div>
          </div>
        </div>

        {/* ── Activity grid ── */}
        <div className="settings-group" style={{ padding: '14px 18px' }}>
          <div className="act-label">Notes written — last 14 weeks</div>
          <div className="activity-grid">
            {cells.map(({ k, v, lvl }) => (
              <div key={k} className={`act-cell l${lvl}`} title={`${k}: ${v} note${v !== 1 ? 's' : ''}`} />
            ))}
          </div>
        </div>

        {/* ── Quick actions ── */}
        <div className="settings-group" style={{ marginTop: '16px' }}>
          <div className="si" onClick={() => navigate('/settings')}>
            <div className="si-icon"><i className="fa-solid fa-gear" /></div>
            <div className="si-body"><div className="si-label">Settings</div><div className="si-sub">Theme, font size, Drive sync</div></div>
            <div className="si-right"><i className="fa-solid fa-chevron-right" /></div>
          </div>
          <div className="si" onClick={() => navigate('/notebooks')}>
            <div className="si-icon"><i className="fa-solid fa-book-open" /></div>
            <div className="si-body"><div className="si-label">My notebooks</div><div className="si-sub">View and manage notebooks</div></div>
            <div className="si-right"><i className="fa-solid fa-chevron-right" /></div>
          </div>
        </div>

        {/* ── Sign out ── */}
        <div className="settings-group" style={{ marginTop: '8px' }}>
          <div className="si" onClick={() => setConfirmSignOut(true)}>
            <div className="si-icon danger"><i className="fa-solid fa-right-from-bracket" /></div>
            <div className="si-body">
              <div className="si-label" style={{ color: 'var(--danger)' }}>Sign out</div>
              <div className="si-sub">Signed in as {user?.email}</div>
            </div>
          </div>
        </div>

      </main>

      {/* ── Confirm Sign Out ── */}
      {confirmSignOut && (
        <div className="modal-overlay show">
          <div className="modal">
            <div className="modal-title">Sign out?</div>
            <div className="modal-sub">
              Your notes are saved on this device. Sign back in anytime to sync with Google Drive.
            </div>
            <div className="modal-actions">
              <button className="btn btn-ghost" onClick={() => setConfirmSignOut(false)}>Cancel</button>
              <button
                className="btn btn-primary"
                style={{ background: 'var(--danger)' }}
                onClick={handleSignOut}
              >
                Sign out
              </button>
            </div>
          </div>
        </div>
      )}

      <BottomNav active="settings" />
      <Toast />
    </div>
  );
}
