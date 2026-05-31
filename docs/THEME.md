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


## GitHub theme

Inkwell supports three themes:

- Light
- Dark
- GitHub

Theme value is saved in localStorage key:

```txt
iw_theme
```

Allowed values:

```txt
light
dark
github
```

The GitHub theme uses GitHub dark style colors:

- background: `#0D1117`
- card/surface: `#161B22`
- border: `#30363D`
- accent blue: `#2F81F7`
- text: `#E6EDF3`
