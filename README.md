# Inkwell — native Android (Kotlin + Jetpack Compose)

Ground-up native rebuild of the React/Capacitor Inkwell app. The UI is ported
value-for-value from `styles.css`, screen by screen. The web app stays the visual
reference until every screen is ported.

## Status

| Screen            | Web source                    | Kotlin                              | State |
|-------------------|-------------------------------|-------------------------------------|:-----:|
| Login             | `pages/Login.jsx`             | `ui/screens/LoginScreen.kt`         | ✅    |
| Theme (3 modes)   | `:root` / `data-theme` CSS    | `ui/theme/Theme.kt`                 | ✅    |
| Google auth       | `auth/AuthContext.jsx`        | `auth/AuthViewModel.kt`             | ✅    |
| Home              | `pages/Home.jsx`              | —                                   | ⏳ next |
| Editor, Notebooks, Search, Tags, Profile, Settings, Drive sync, update checker | | | ⏳ |

After sign-in the app currently shows a **temporary** placeholder (`HomePlaceholder.kt`)
so the login flow can be tested end to end.

## Build the APK on GitHub

Push to `main` (or run the workflow manually). `.github/workflows/build-apk.yml`
builds a signed APK and attaches it to a GitHub Release.

Uses the **same secrets as before** — nothing new to add:
`KEYSTORE_BASE64`, `KEY_ALIAS`, `KEY_PASSWORD`, `STORE_PASSWORD`, `VITE_GOOGLE_CLIENT_ID`.

* Package name is still `com.inkwell.notes` and the same keystore is used, so the
  existing Google Cloud Android OAuth credential (package + SHA-1) keeps working.
* `versionCode = 1000 + run number`, so the APK installs over the old Capacitor one.
  (Notes saved by the old WebView app are **not** read by the native app yet.)

## Local build

```bash
cp local.properties.example local.properties   # set VITE_GOOGLE_CLIENT_ID
# open in Android Studio (Ladybug+) or: gradle assembleDebug
```

## Layout

```
app/src/main/java/com/inkwell/notes/
├── MainActivity.kt
├── auth/        AuthViewModel, AuthStore
├── data/        AppPrefs            (SharedPreferences; keys iw_theme, iw_gauth)
└── ui/
    ├── InkwellApp.kt                (root / routing)
    ├── theme/Theme.kt               (colours, fonts, radii)
    ├── components/InkText.kt
    └── screens/                     (one file per page)
```
