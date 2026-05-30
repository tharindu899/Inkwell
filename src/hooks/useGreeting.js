/* ══════════════════════════════════════════
   Inkwell — src/hooks/useGreeting.js
   Returns a time-aware greeting that updates
   automatically every minute.
   ══════════════════════════════════════════ */

import { useState, useEffect } from 'react';

// ─── Pure helper (no React) ───────────────
export function getGreeting() {
  const h = new Date().getHours();
  if (h >= 5  && h < 12) return { prefix: 'Good', hl: 'Morning'   };
  if (h >= 12 && h < 17) return { prefix: 'Good', hl: 'Afternoon' };
  if (h >= 17 && h < 21) return { prefix: 'Good', hl: 'Evening'   };
  return                         { prefix: 'Good', hl: 'Night'     };
}

// ─── Hook ─────────────────────────────────
// Returns { prefix: 'Good', hl: 'Morning' }
// Re-evaluates every 60 s so the greeting
// changes without a page reload.
export function useGreeting() {
  const [greeting, setGreeting] = useState(getGreeting);

  useEffect(() => {
    const id = setInterval(() => setGreeting(getGreeting()), 60_000);
    return () => clearInterval(id);
  }, []);

  return greeting;
}
