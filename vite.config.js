/* ══════════════════════════════════════════
   Inkwell — vite.config.js
   ══════════════════════════════════════════ */

import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { readFileSync } from 'fs';

const pkg = JSON.parse(readFileSync('./package.json', 'utf-8'));

export default defineConfig({
  plugins: [react()],

  // Bake version + repo into the bundle so UpdateChecker can read them.
  // VITE_GITHUB_REPO must be set as a GitHub Secret (e.g. "alice/inkwell").
  define: {
    'import.meta.env.VITE_APP_VERSION': JSON.stringify(pkg.version),
  },

  // IMPORTANT for Capacitor: use relative paths so
  // Android WebView can load assets from the filesystem.
  base: './',

  server: {
    port: 5173,
    open: true,
    headers: {
      'Service-Worker-Allowed': '/',
    },
  },

  build: {
    outDir: 'dist',
    sourcemap: false,
  },

  publicDir: 'public',
});
