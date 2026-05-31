/* ══════════════════════════════════════════
   Inkwell — src/components/Toast.jsx

   Imperative toast via a React portal.
   Replaces the original showToast() DOM helper.

   ── Usage ────────────────────────────────
   1. Mount <Toast /> once inside App.jsx (or
      a layout wrapper). It renders into
      document.body via a portal.

   2. Call the exported helper from anywhere:
        import { showToast } from './Toast';
        showToast('Note saved!');
        showToast('Deleted', 'fa-trash');

   ── API ───────────────────────────────────
   showToast(message, faIcon?)
     message  string   — text to display
     faIcon   string   — Font Awesome icon class
                         default: 'fa-check'
   ══════════════════════════════════════════ */

import { useState, useEffect, useCallback, useRef } from 'react';
import { createPortal } from 'react-dom';

// Module-level setter — lets showToast() reach into the component.
let _setToast = null;

export function showToast(msg, icon = 'fa-check', action = null) {
  if (_setToast) _setToast({ msg, icon, action, key: Date.now() });
}

export default function Toast() {
  const [toast, setToast]   = useState(null);   // { msg, icon, key } | null
  const timerRef            = useRef(null);

  // Register the module-level setter on mount
  useEffect(() => {
    _setToast = setToast;
    return () => { _setToast = null; };
  }, []);

  // Auto-dismiss after 2600 ms, or 5 seconds when an Undo action is shown.
  useEffect(() => {
    if (!toast) return;
    clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => setToast(null), toast.action ? 5000 : 2600);
    return () => clearTimeout(timerRef.current);
  }, [toast]);

  return createPortal(
    <div className={`toast${toast ? ' show' : ''}`}>
      {toast && (
        <>
          <i className={`fa-solid ${toast.icon}`} />
          <span className="toast-msg">{toast.msg}</span>
          {toast.action && (
            <button
              type="button"
              className="toast-action"
              onClick={() => {
                const fn = toast.action?.onClick;
                setToast(null);
                fn?.();
              }}
            >
              {toast.action.label || 'Undo'}
            </button>
          )}
        </>
      )}
    </div>,
    document.body
  );
}
