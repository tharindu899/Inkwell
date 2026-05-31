# Inkwell APK Update Install

Inkwell keeps APK updates inside the app on Android.

## Current behavior

| State | App message | Main button |
|------|-------------|-------------|
| Installed APK is latest | You are already updated | Done |
| New release APK exists | New APK update available | Install |
| Release has no APK asset | No APK file attached | None |

## Install flow

1. Open **Settings → Check for updates**.
2. If a new release exists, tap **Install**.
3. Inkwell downloads the APK inside the app.
4. Android Package Installer opens directly.
5. Tap **Install / Update** manually.
6. Temporary APK is deleted from app cache after a short delay.

## Android limitation

Silent install is not allowed for normal Android apps. The system installer confirmation is required.

## Troubleshooting

| Problem | Fix |
|--------|-----|
| Browser opens | Install APK built from v37 or newer |
| Installer does not open | Enable “Install unknown apps” for Inkwell |
| Already updated | Installed version equals latest release tag |
| No APK found | Attach the `.apk` file to the GitHub Release |

## Release changelog

The update popup can show a short changelog from the GitHub Release description/body.

Keep it short and clean:

```md
### Fixed
- Fixed selected delete confirmation
- Fixed Tags page colors
- Improved editor tag selector
```

For full history, use `docs/CHANGELOG.md`.
