# Inkwell APK Update Install

Inkwell now keeps the update flow inside the app on Android.

## What changed

- No GitHub browser popup during APK download.
- The native Android plugin downloads the APK from the GitHub Release URL.
- The APK is saved only to app cache.
- Android Package Installer opens directly.
- The temporary APK is deleted from cache after a short delay.
- If the installed version already matches the latest release, the app shows **You are already updated** and hides the download button.

## Android limitation

Android does not allow silent installs for normal apps.  
The user must still tap **Install / Update** in Android Package Installer.

## Requirements

- Repo must be public.
- The latest GitHub Release must include an `.apk` asset.
- The app build must bake the current version correctly through `VITE_APP_VERSION`.

## Troubleshooting

| Problem | Fix |
|--------|-----|
| Browser opens | Install the newest APK built from v37 or newer |
| Installer does not open | Enable “Install unknown apps” for Inkwell |
| Already updated | Installed version equals latest release tag |
| No APK found | Attach the APK file to the GitHub Release |
