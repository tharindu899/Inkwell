# Inkwell APK Update Install

## Current version check

When you tap **Check for updates**:

- If the GitHub Release version is newer than the installed APK, Inkwell shows **Download & Install**.
- If the installed APK already matches the latest GitHub Release, Inkwell shows **You are already updated** and hides the download button.

## Download and install

For a new update:

1. Inkwell downloads the APK inside the app.
2. The update sheet shows MB and percent progress.
3. Android Package Installer opens directly.
4. You manually tap **Update / Install**.
5. The temporary APK is deleted from app cache after a short delay.

Android does not allow silent installs for normal apps, so the final Install/Update tap is required.
