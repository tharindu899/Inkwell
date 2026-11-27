# Inkwell — native Kotlin (Jetpack Compose)

UI direction: **Bold Sheet** (lime `#D9F36B` + ink `#111111`, dark bottom sheet, pill nav).

## Part 1 (this zip)
- Gradle project (Kotlin 2.0.21, AGP 8.7.3, Compose BOM 2024.12.01, minSdk 26)
- Theme: `ui/theme/` (Color, Type, Theme)
- Native Google sign-in (Credential Manager): `auth/`
- Login screen + animations: `ui/login/LoginScreen.kt`
- Temporary Home placeholder with Sign out: `ui/home/HomePlaceholder.kt`
- GitHub Actions APK build: `.github/workflows/build-apk.yml`

## GitHub setup (same secrets as before)
`KEYSTORE_BASE64`, `KEY_ALIAS`, `KEY_PASSWORD`, `STORE_PASSWORD`, `VITE_GOOGLE_CLIENT_ID`
(the Web client ID is passed to the app as `GOOGLE_CLIENT_ID`).

Google Cloud Console must have an **Android OAuth client** for package
`com.inkwell.notes` with the release keystore SHA-1 (printed in the run summary).

## Local build
`gradle :app:assembleDebug -PGOOGLE_CLIENT_ID=xxxx.apps.googleusercontent.com`

## Next parts
2 Home screen + navigation · 3 Notes storage · 4 Editor (8 sub-parts) · 5 Search/Tags/Notebooks · 6 Settings/Profile/Drive sync/updater
