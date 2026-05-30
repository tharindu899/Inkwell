/* ══════════════════════════════════════════
   Inkwell — src/App.jsx
   Routes. Wraps everything in a Google
   login gate — unauthenticated users land
   on the Login page.
   ══════════════════════════════════════════ */

import { useEffect, useState } from 'react';
import { Routes, Route, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';

import UpdateChecker  from './components/UpdateChecker';
import Login          from './pages/Login';
import Home           from './pages/Home';
import Editor         from './pages/Editor';
import Notebooks      from './pages/Notebooks';
import NotebookDetail from './pages/NotebookDetail';
import Profile        from './pages/Profile';
import Search         from './pages/Search';
import Settings       from './pages/Settings';
import Tags           from './pages/Tags';


// ─── Android hardware back button ───────────────────────────
// Capacitor WebView does not automatically close the app from
// HashRouter's home route, so handle it explicitly.
function AndroidBackHandler() {
  const location = useLocation();
  const navigate = useNavigate();

  useEffect(() => {
    let listener;
    let cancelled = false;

    async function setupBackButton() {
      try {
        const { App: CapacitorApp } = await import('@capacitor/app');
        if (cancelled) return;

        listener = await CapacitorApp.addListener('backButton', () => {
          const path = window.location.hash.replace(/^#/, '') || location.pathname;
          const isRoot = path === '/' || path === '/login' || path === '';

          if (isRoot) {
            CapacitorApp.exitApp();
          } else {
            navigate(-1);
          }
        });
      } catch {
        // Running in the browser preview, not Capacitor.
      }
    }

    setupBackButton();

    return () => {
      cancelled = true;
      listener?.remove?.();
    };
  }, [location.pathname, navigate]);

  return null;
}


// ─── Stop copy/select on non-editor pages ─────────────────
// Android WebView can select/copy random UI labels. Keep text copy only
// inside the note editor/title and code-block Copy action.
function CopySelectGuard() {
  useEffect(() => {
    const EDITOR_SELECTOR = '.editor-body, .editor-title, [data-inkwell-copy-ok="1"]';

    const isEditorNode = (node) => {
      if (!node) return false;
      const el = node.nodeType === Node.TEXT_NODE ? node.parentElement : node;
      return !!el?.closest?.(EDITOR_SELECTOR);
    };

    const canUseText = (target) => {
      if (isEditorNode(target)) return true;
      if (isEditorNode(document.activeElement)) return true;

      // Copy/cut events on Android WebView often arrive with <body> as the
      // event target even when the real selection is inside the note body.
      const sel = document.getSelection?.();
      if (sel && sel.rangeCount > 0) {
        return isEditorNode(sel.anchorNode) || isEditorNode(sel.focusNode);
      }
      return false;
    };

    const blockCopyOutsideEditor = (event) => {
      if (!canUseText(event.target)) event.preventDefault();
    };

    const blockSelectOutsideEditor = (event) => {
      if (!canUseText(event.target)) event.preventDefault();
    };

    document.addEventListener('copy', blockCopyOutsideEditor, true);
    document.addEventListener('cut', blockCopyOutsideEditor, true);
    document.addEventListener('selectstart', blockSelectOutsideEditor, true);

    return () => {
      document.removeEventListener('copy', blockCopyOutsideEditor, true);
      document.removeEventListener('cut', blockCopyOutsideEditor, true);
      document.removeEventListener('selectstart', blockSelectOutsideEditor, true);
    };
  }, []);

  return null;
}

// ─── Auth Guard ───────────────────────────
function RequireAuth({ children }) {
  const { isLoggedIn } = useAuth();
  if (!isLoggedIn) return <Navigate to="/login" replace />;
  return children;
}

// ─── Offline banner ───────────────────────
function OfflineBanner() {
  const [offline, setOffline] = useState(() => !navigator.onLine);
  useEffect(() => {
    const goOffline = () => setOffline(true);
    const goOnline  = () => setOffline(false);
    window.addEventListener('offline', goOffline);
    window.addEventListener('online', goOnline);
    return () => {
      window.removeEventListener('offline', goOffline);
      window.removeEventListener('online', goOnline);
    };
  }, []);
  if (!offline) return null;
  return (
    <div style={{
      position: 'fixed', top: 0, left: 0, right: 0, zIndex: 9999,
      background: '#1a1a2e', color: '#f8c94a',
      padding: '8px 16px', fontSize: 13, fontWeight: 500,
      display: 'flex', alignItems: 'center', gap: 8,
      boxShadow: '0 2px 8px rgba(0,0,0,.4)',
    }}>
      <i className="fa-solid fa-wifi" style={{ fontSize: 12, opacity: .7 }} />
      You're offline — changes will sync when reconnected
    </div>
  );
}

export default function App() {
  const { isLoggedIn } = useAuth();

  return (
    <>
      <OfflineBanner />
      <AndroidBackHandler />
      <CopySelectGuard />
      <UpdateChecker />
      <Routes>
      {/* Public */}
      <Route
        path="/login"
        element={isLoggedIn ? <Navigate to="/" replace /> : <Login />}
      />

      {/* Protected */}
      <Route path="/" element={<RequireAuth><Home /></RequireAuth>} />
      <Route path="/editor" element={<RequireAuth><Editor /></RequireAuth>} />
      <Route path="/editor/:id" element={<RequireAuth><Editor /></RequireAuth>} />
      <Route path="/notebooks" element={<RequireAuth><Notebooks /></RequireAuth>} />
      <Route path="/notebooks/:id" element={<RequireAuth><NotebookDetail /></RequireAuth>} />
      <Route path="/profile" element={<RequireAuth><Profile /></RequireAuth>} />
      <Route path="/search" element={<RequireAuth><Search /></RequireAuth>} />
      <Route path="/settings" element={<RequireAuth><Settings /></RequireAuth>} />
      <Route path="/tags" element={<RequireAuth><Tags /></RequireAuth>} />

      {/* Catch-all */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
    </>
  );
}
