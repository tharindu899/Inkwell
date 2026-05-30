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
export function useTheme() {
  const [theme, setThemeState] = useState(
    () => localStorage.getItem(K.theme) || 'dark'
  );

  // Keep <html data-theme="..."> in sync whenever theme changes
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
  }, [theme]);

  const setTheme = useCallback((t) => {
    localStorage.setItem(K.theme, t);
    setThemeState(t);
  }, []);

  const toggleTheme = useCallback(() => {
    setTheme(theme === 'dark' ? 'light' : 'dark');
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
