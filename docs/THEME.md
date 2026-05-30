# Theme persistence

Inkwell stores the selected theme in localStorage using:

```txt
iw_theme = light | dark
```

The saved theme is now applied before React renders in `src/main.jsx`, so Android app reopen no longer flashes or resets to dark mode.

## User flow

1. Settings → Dark mode toggle OFF = Light mode.
2. `iw_theme=light` is saved.
3. Close/reopen the APK.
4. App starts with `data-theme="light"` immediately.
