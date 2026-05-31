/* ══════════════════════════════════════════
   Inkwell — src/main.jsx
   Entry point — wraps tree with routing,
   auth (Google), and global app state.

   NOTE: HashRouter is required for Capacitor/Android.
   BrowserRouter uses the History API which breaks in
   WebView because there is no server to handle /editor
   paths — only the root index.html exists on disk.
   HashRouter keeps all navigation in the URL hash:
   https://localhost/#/editor  →  works perfectly.
   ══════════════════════════════════════════ */

import React from 'react';
import ReactDOM from 'react-dom/client';
import { HashRouter } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import { AppProvider } from './store/AppContext';
import App from './App';
import './styles/styles.css';

// v38: apply saved theme before React renders.
// Without this, reopening the Android app starts with CSS default dark theme
// until Settings page mounts useTheme().
try {
  const savedTheme = localStorage.getItem('iw_theme');
  const theme = savedTheme === 'light' || savedTheme === 'dark' || savedTheme === 'github' ? savedTheme : 'dark';
  document.documentElement.setAttribute('data-theme', theme);
} catch {
  document.documentElement.setAttribute('data-theme', 'dark');
}


ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <HashRouter>
      <AuthProvider>
        <AppProvider>
          <App />
        </AppProvider>
      </AuthProvider>
    </HashRouter>
  </React.StrictMode>
);
