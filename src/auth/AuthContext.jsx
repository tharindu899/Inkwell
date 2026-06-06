/* ══════════════════════════════════════════
   Inkwell — src/auth/AuthContext.jsx
   Google OAuth2 auth.

   ┌─ Web / PWA ──────────────────────────┐
   │  Google Identity Services (GIS)      │
   │  popup flow — works in Chrome/Safari │
   └──────────────────────────────────────┘
   ┌─ Android / Native (Capacitor) ───────┐
   │  @codetrix-studio/capacitor-google-auth │
   │  native Google Sign-In SDK           │
   │  (GIS popup is blocked in WebView)   │
   └──────────────────────────────────────┘
   ══════════════════════════════════════════ */

import {
  createContext, useContext, useState, useEffect, useRef, useCallback,
} from 'react';

// ─── Config ───────────────────────────────
export const CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID || '';

// Scopes: identity + Drive app-data folder
const SCOPES = [
  'openid',
  'email',
  'profile',
  'https://www.googleapis.com/auth/drive.appdata',
];
const SCOPES_STR = SCOPES.join(' ');

const AUTH_KEY = 'iw_gauth';

// ─── Platform detection ───────────────────
// Capacitor sets window.Capacitor when running as a native app.
function isNative() {
  try {
    return !!(window.Capacitor?.isNativePlatform?.());
  } catch {
    return false;
  }
}

// ─── Helpers ──────────────────────────────
function loadSaved() {
  try {
    const raw = localStorage.getItem(AUTH_KEY);
    if (!raw) return null;
    const d = JSON.parse(raw);
    if (d?.email || d?.name) {
      return {
        ...d,
        accessToken: d.accessToken || '',
        expiry: Number(d.expiry || 0),
        savedAt: d.savedAt || Date.now(),
      };
    }
    return null;
  } catch {
    return null;
  }
}

// ─── Context ──────────────────────────────
const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser]           = useState(() => loadSaved());
  // Native is always "ready" — no script to wait for
  const [gisReady, setGisReady]   = useState(() => isNative());
  const [signingIn, setSigningIn] = useState(false);
  const [error, setError]         = useState(null);
  const tokenClientRef            = useRef(null); // GIS token client (web)
  const googleAuthRef             = useRef(null); // native GoogleAuth module

  // ── Load native plugin (Android/iOS only) ──
  useEffect(() => {
    if (!isNative() || !CLIENT_ID) return;
    import('@codetrix-studio/capacitor-google-auth').then(({ GoogleAuth }) => {
      googleAuthRef.current = GoogleAuth;
      GoogleAuth.initialize({
        clientId: CLIENT_ID,
        scopes: SCOPES,
        grantOfflineAccess: false,
      });
    }).catch((err) => {
      console.error('[Inkwell] Failed to load native GoogleAuth:', err);
    });
  }, []);

  // ── Web: poll until GIS script is loaded ──
  useEffect(() => {
    if (isNative()) return;
    let id;
    function check() {
      if (window.google?.accounts?.oauth2) { setGisReady(true); return; }
      id = setTimeout(check, 150);
    }
    check();
    return () => clearTimeout(id);
  }, []);

  // ── Web: build GIS token client once ready ──
  useEffect(() => {
    if (isNative() || !gisReady || !CLIENT_ID) return;

    tokenClientRef.current = window.google.accounts.oauth2.initTokenClient({
      client_id: CLIENT_ID,
      scope: SCOPES_STR,
      callback: async (resp) => {
        setSigningIn(false);
        if (resp.error) {
          setError(resp.error_description || resp.error);
          return;
        }
        try {
          const r = await fetch('https://www.googleapis.com/oauth2/v3/userinfo', {
            headers: { Authorization: `Bearer ${resp.access_token}` },
          });
          if (!r.ok) throw new Error('userinfo failed');
          const info = await r.json();

          const userData = {
            name:        info.name  || info.email,
            email:       info.email || '',
            picture:     info.picture || '',
            accessToken: resp.access_token,
            expiry:      Date.now() + (resp.expires_in || 3600) * 1000,
            savedAt:     Date.now(),
          };
          localStorage.setItem(AUTH_KEY, JSON.stringify(userData));
          setUser(userData);
          setError(null);
        } catch {
          setError('Could not fetch profile. Try again.');
        }
      },
    });
  }, [gisReady]);

  // ── Sign In ──
  const signIn = useCallback(async (prompt = 'select_account') => {
    setError(null);

    // ── Native: use Google Sign-In SDK ──
    if (isNative()) {
      if (!googleAuthRef.current) {
        setError('Google sign-in is loading. Try again in a moment.');
        return;
      }
      setSigningIn(true);
      try {
        const result = await googleAuthRef.current.signIn();
        const userData = {
          name:        result.name        || result.email,
          email:       result.email       || '',
          picture:     result.imageUrl    || '',
          accessToken: result.authentication?.accessToken || '',
          // Use expires_in from the result if available; fall back to 1 h
          expiry:      Date.now() + ((result.authentication?.expires_in ?? 3600) * 1000),
          savedAt:     Date.now(),
        };
        localStorage.setItem(AUTH_KEY, JSON.stringify(userData));
        setUser(userData);
      } catch (err) {
        const msg = String(err?.message || err);
        // 12501 = sign-in cancelled by user — not an error worth showing
        if (!msg.includes('12501') && !msg.toLowerCase().includes('cancel')) {
          setError('Sign-in failed. Please try again.');
        }
      } finally {
        setSigningIn(false);
      }
      return;
    }

    // ── Web: use GIS popup flow ──
    if (!tokenClientRef.current) {
      setError('Google sign-in is still loading. Try again in a moment.');
      return;
    }
    setSigningIn(true);
    tokenClientRef.current.requestAccessToken({ prompt });
  }, []);

  // ── Silent token refresh ──
  const refreshToken = useCallback(() => {
    return new Promise((resolve) => {

      // Native: ask plugin for a fresh token
      if (isNative()) {
        if (!googleAuthRef.current) { resolve(null); return; }
        googleAuthRef.current.refresh()
          .then((res) => {
            if (!res?.accessToken) { resolve(null); return; }
            setUser((prev) => {
              if (!prev) { resolve(null); return prev; }
              const updated = {
                ...prev,
                accessToken: res.accessToken,
                expiry:      Date.now() + 3600 * 1000,
              };
              localStorage.setItem(AUTH_KEY, JSON.stringify(updated));
              resolve(updated.accessToken);
              return updated;
            });
          })
          .catch(() => resolve(null));
        return;
      }

      // Web GIS silent refresh
      if (!tokenClientRef.current) { resolve(null); return; }
      const orig = tokenClientRef.current.callback;
      // Track whether a real user-visible sign-in was in progress so the
      // silent refresh doesn't accidentally reset the spinner.
      const wasSigningIn = false; // silent refresh never calls setSigningIn(true)
      tokenClientRef.current.callback = (resp) => {
        tokenClientRef.current.callback = orig;
        if (wasSigningIn) setSigningIn(false);
        if (resp.error) {
          setError(null);
          resolve(null);
          return;
        }
        setUser((prev) => {
          if (!prev) { resolve(resp.access_token); return prev; }
          const updated = {
            ...prev,
            accessToken: resp.access_token,
            expiry:      Date.now() + (resp.expires_in || 3600) * 1000,
            savedAt:     prev.savedAt || Date.now(),
          };
          localStorage.setItem(AUTH_KEY, JSON.stringify(updated));
          resolve(updated.accessToken);
          return updated;
        });
      };
      tokenClientRef.current.requestAccessToken({ prompt: '' });
    });
  }, []);

  // ── Get valid token (refresh if needed) ──
  const getToken = useCallback(async () => {
    if (!user) return null;
    if (user.accessToken && user.expiry > Date.now() + 2 * 60 * 1000) {
      return user.accessToken;
    }
    return refreshToken();
  }, [user, refreshToken]);

  // ── Cached token only (no popup, no refresh) ──
  const getCachedToken = useCallback(() => {
    if (!user?.accessToken) return null;
    if (user.expiry <= Date.now() + 2 * 60 * 1000) return null;
    return user.accessToken;
  }, [user]);

  // ── Sign Out ──
  const signOut = useCallback(async () => {
    if (isNative() && googleAuthRef.current) {
      try { await googleAuthRef.current.signOut(); } catch {}
    } else if (user?.accessToken) {
      try { window.google?.accounts?.oauth2?.revoke(user.accessToken); } catch {}
    }
    localStorage.removeItem(AUTH_KEY);
    setUser(null);
  }, [user]);

  return (
    <AuthContext.Provider value={{
      user, gisReady, signingIn, error,
      signIn, signOut, getToken, getCachedToken,
      isLoggedIn: !!user,
    }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be inside <AuthProvider>');
  return ctx;
}
