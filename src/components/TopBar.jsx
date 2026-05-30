import { useNavigate } from 'react-router-dom';
import { useGreeting } from '../hooks/useGreeting';
import { useAuth } from '../auth/AuthContext';
import { useState, useEffect } from 'react';

function useSyncStatus() {
  const [status, setStatus] = useState('idle'); // 'idle' | 'syncing' | 'synced' | 'error'
  useEffect(() => {
    let clearTimer;
    const handler = (e) => {
      setStatus(e.detail.status);
      clearTimeout(clearTimer);
      if (e.detail.status === 'synced' || e.detail.status === 'error') {
        clearTimer = setTimeout(() => setStatus('idle'), 3000);
      }
    };
    window.addEventListener('iw-sync', handler);
    return () => { window.removeEventListener('iw-sync', handler); clearTimeout(clearTimer); };
  }, []);
  return status;
}

function SyncDot() {
  const status = useSyncStatus();
  if (status === 'idle') return null;
  const color = status === 'syncing' ? 'var(--accent)' : status === 'error' ? 'var(--danger)' : '#34a853';
  const icon  = status === 'syncing' ? 'fa-spinner fa-spin' : status === 'error' ? 'fa-circle-exclamation' : 'fa-circle-check';
  const label = status === 'syncing' ? 'Syncing…' : status === 'error' ? 'Sync failed' : 'Synced';
  return (
    <span style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 11, color, fontWeight: 500, marginRight: 4 }}>
      <i className={`fa-solid ${icon}`} style={{ fontSize: 10 }} />
      {label}
    </span>
  );
}

function initials(name = 'U') {
  return (name || 'U').trim().charAt(0).toUpperCase() || 'U';
}

function HomeTopBar() {
  const navigate  = useNavigate();
  const greeting  = useGreeting();
  const { user }  = useAuth();

  const firstName = user?.name?.split(' ')[0] || 'User';
  const picture   = user?.picture || '';

  return (
    <header className="top-bar app-nav home-nav">
      <button className="profile-avatar" onClick={() => navigate('/profile')} title="Profile">
        {picture
          ? <img src={picture} alt={firstName} className="profile-avatar-img" referrerPolicy="no-referrer" />
          : initials(firstName)
        }
      </button>
      <div className="app-nav-title-block">
        <div className="greeting">{greeting.prefix}, <span className="hl">{firstName}</span></div>
        <div className="top-bar-title-inline">My Notes</div>
      </div>
      <div className="top-bar-actions">
        <SyncDot />
        <button className="icon-btn" onClick={() => navigate('/search')} title="Search">
          <i className="fa-solid fa-magnifying-glass" />
        </button>
        <button className="icon-btn" onClick={() => navigate('/settings')} title="Settings">
          <i className="fa-solid fa-gear" />
        </button>
      </div>
    </header>
  );
}

function InnerTopBar({ title, actions, noBack = false }) {
  const navigate = useNavigate();

  return (
    <header className="top-bar app-nav inner-nav">
      <div className="top-bar-left">
        {!noBack && (
          <button className="back-btn" onClick={() => navigate(-1)} title="Back">
            <i className="fa-solid fa-arrow-left" />
          </button>
        )}
      </div>
      <div className="top-bar-title">{title}</div>
      <div className="top-bar-actions">{actions}</div>
    </header>
  );
}

export default function TopBar({ greeting, title, actions, noBack }) {
  if (greeting) return <HomeTopBar />;
  return <InnerTopBar title={title} actions={actions} noBack={noBack} />;
}
