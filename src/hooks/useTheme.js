/* ══════════════════════════════════════════
   Inkwell — src/hooks/useTheme.js
   Theme + font-size persistence via localStorage.
   Syncs the data-theme attribute on <html>.
   ══════════════════════════════════════════ */

import { useState, useEffect, useCallback } from 'react';
import { K } from '../store/storage';

// ─── Font size options (mirrors original) ─
export const FONT_SIZES = {
  small:  '13px',
  medium: '15px',
  large:  '17px',
};

// ─── useTheme ─────────────────────────────
const THEME_KEYS = ['light', 'dark', 'github'];
const normalizeTheme = (value) => (THEME_KEYS.includes(value) ? value : 'dark');

export function useTheme() {
  const [theme, setThemeState] = useState(() => {
    try { return normalizeTheme(localStorage.getItem(K.theme)); }
    catch { return 'dark'; }
  });

  // Keep <html data-theme="..."> and storage in sync whenever theme changes.
  useEffect(() => {
    const next = normalizeTheme(theme);
    document.documentElement.setAttribute('data-theme', next);
    try {
      if (localStorage.getItem(K.theme) !== next) localStorage.setItem(K.theme, next);
    } catch {}
  }, [theme]);

  // If another part of the app/tab changes the theme, update this hook too.
  useEffect(() => {
    const syncTheme = () => {
      try {
        const saved = normalizeTheme(localStorage.getItem(K.theme));
        setThemeState(saved);
        document.documentElement.setAttribute('data-theme', saved);
      } catch {}
    };
    window.addEventListener('storage', syncTheme);
    window.addEventListener('inkwell-theme-changed', syncTheme);
    return () => {
      window.removeEventListener('storage', syncTheme);
      window.removeEventListener('inkwell-theme-changed', syncTheme);
    };
  }, []);

  const setTheme = useCallback((t) => {
    const next = normalizeTheme(t);
    try { localStorage.setItem(K.theme, next); } catch {}
    document.documentElement.setAttribute('data-theme', next);
    setThemeState(next);
    window.dispatchEvent(new CustomEvent('inkwell-theme-changed', { detail: next }));
  }, []);

  const toggleTheme = useCallback(() => {
    setTheme(theme === 'dark' ? 'github' : theme === 'github' ? 'light' : 'dark');
  }, [theme, setTheme]);

  return { theme, setTheme, toggleTheme };
}

// ─── useFontSize ──────────────────────────
export function useFontSize() {
  const [fontSize, setFontSizeState] = useState(
    () => localStorage.getItem(K.fontSize) || 'medium'
  );

  const setFontSize = useCallback((s) => {
    if (!FONT_SIZES[s]) return;
    localStorage.setItem(K.fontSize, s);
    setFontSizeState(s);
  }, []);

  // Expose the resolved CSS value alongside the key
  const fontSizePx = FONT_SIZES[fontSize] || FONT_SIZES.medium;

  // Keep --editor-fs on <html> in sync so the editor body picks it up
  useEffect(() => {
    document.documentElement.style.setProperty('--editor-fs', fontSizePx);
  }, [fontSizePx]);

  return { fontSize, fontSizePx, setFontSize };
}
