# Inkwell APK Update Install

Inkwell can check GitHub Releases, download a new APK inside the app, open Android Package Installer directly, and remove the temporary APK file afterward.

## User flow

1. Open **Settings**.
2. Tap **Check for updates**.
3. If your APK is already the latest version, the app shows **You are already updated**.
4. If a newer APK exists, tap **Download & Install**.
5. Wait for the MB / percent progress bar.
6. Android Package Installer opens.
7. Tap **Install / Update**.
8. The temporary APK is deleted from app cache after a delay.

## Requirements

- Repository must be public.
- GitHub Actions must publish a GitHub Release.
- The release must include an `.apk` asset.
- The APK version baked into the app must be lower than the release tag to show a real update.

## Android limitation

Android does not allow silent installation for normal apps. The user must confirm the install/update on the system installer screen.

## Troubleshooting

| Problem | Fix |
|--------|-----|
| Repo not found | Make `tharindu899/Inkwell` public |
| No update found | Create a GitHub Release with an `.apk` asset |
| Shows already updated | Installed version equals latest release version |
| Installer does not open | Android blocked file install; open the release page fallback |
