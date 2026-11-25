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
| Home — step 1     | `pages/Home.jsx`              | `ui/screens/HomeScreen.kt`          | ✅ UI + data API |
| Home — step 2     | multi-select, long-press, bulk pin/delete, confirm modal, Undo toast | `HomeScreen.kt`, `NoteCard.kt`, `Toast.kt`, `DeleteConfirmModal.kt` | ✅ |
| Home — step 3     | pinned drag-reorder (grip handle) | `HomeScreen.kt`, `NoteCard.kt`, `NotesStore.movePinned` | ✅ |
| Home — step 4     | Create-New menu (FAB) + notebook/tag quick-create modal | `CreateMenu.kt`, `QuickCreateModal.kt`, `Fab.kt` | ✅ UI (navigation = TODO) |
| Navigation        | `App.jsx` routes + back button | `ui/Navigator.kt`, `InkwellApp.kt` | ✅ simple back-stack (no library) |
| Tags              | `pages/Tags.jsx`              | `ui/screens/TagsScreen.kt`, `InnerTopBar.kt` | ✅ (bottom-nav Tags tab works) |
| Notebooks         | `pages/Notebooks.jsx`         | `ui/screens/NotebooksScreen.kt`, `NotebookModals.kt`, `NotebooksViewModel.kt` | ✅ grid, sort, create/edit/delete |
| NotebookDetail    | `pages/NotebookDetail.jsx`    | `ui/screens/NotebookDetailScreen.kt` | ✅ (note tap / FAB → Editor = TODO) |
| Search, Profile, Settings, Editor, Drive sync, update checker | | | ⏳ |

After sign-in the app shows Home. **Temporary:** long-press the avatar to sign out
(until the Profile screen is ported).

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
├── data/        AppPrefs, NotesStore, Seed, model/   (SharedPreferences; keys iw_theme, iw_gauth,
│                                                     iw_notes, iw_notebooks, iw_sort, iw_seed_version)
├── util/        Helpers             (formatDate, stripHtml, fmtNum, greeting …)
└── ui/
    ├── InkwellApp.kt                (root: login gate + current screen)
    ├── Navigator.kt                 (Route + back-stack)
    ├── theme/Theme.kt               (colours, fonts, radii)
    ├── components/                  (InkText, NoteCard, Toast, DeleteConfirmModal …)
    └── screens/                     (one file per page; CreateLayer = FAB/menu/toast shared by pages)
```
