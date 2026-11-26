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
| NotebookDetail    | `pages/NotebookDetail.jsx`    | `ui/screens/NotebookDetailScreen.kt` | ✅ (note tap / FAB → Editor) |
| Search            | `pages/Search.jsx`            | `ui/screens/SearchScreen.kt`, `NotesStore.searchNotes` + recent searches (`iw_searches`) | ✅ modes, notebook filter, highlight, recents (note tap → Editor) |
| Profile           | `pages/Profile.jsx`           | `ui/screens/ProfileScreen.kt`       | ✅ hero, stats, streak, activity grid, sign-out confirm |
| Settings          | `pages/Settings.jsx`          | `ui/screens/SettingsScreen.kt`, `SettingsViewModel.kt` | ✅ theme (live), font size, editor toggles, local export/import, storage, danger zone, about |
| Drive sync        | `auth/googleDrive.js`         | `drive/DriveApi.kt`, `auth/TokenProvider.kt` | ✅ backup / restore / last-backup (appDataFolder, 401 → token refresh + retry) |
| Update checker    | `components/UpdateChecker.jsx` | `update/UpdateApi.kt`, `ApkInstaller.kt`, `UpdateViewModel.kt`, `ui/screens/UpdateHost.kt` | ✅ auto check on launch/resume (10 min throttle), manual check in Settings, in-app APK download → system installer. Repo = `VITE_GITHUB_REPO` → `GITHUB_REPOSITORY` → `tharindu899/Inkwell` |
| Editor — part 1   | `pages/Editor.jsx` (shell, load/save) | `ui/screens/EditorScreen.kt`, `EditorController.kt` | ✅ top bar (back · reading mode · pin · delete · Save), title + body, tags/notebook rows (display), word/char count, 2 s auto-save, flush on pause/leave, delete confirm. Navigation wired from Home, All Notes, Notebook detail (+ FAB), Search, tag quick-create |
| Editor — part 2   | formatting model + toolbar | `editor/RichDoc.kt` (text + marks + per-line blocks, undo-friendly), `editor/HtmlCodec.kt` (HTML ⇄ model, markdown), `editor/RichTransformation.kt`, `ui/screens/EditorToolbar.kt` | ✅ bold/italic/underline/strike, H1–H3/¶, bullets, numbers, quote, align, indent, highlight, sup/sub, clear, undo/redo, pending format at caret. Notes with tables/code/links/images/checklists stay read-only until their part |
| Editor — part 3   | checklist, nested + collapsible lists, markdown shortcuts | `editor/RichDoc.kt` (TASK / CODE blocks, nesting via `Para.indent`, `checked`, `collapsed`, shortcut + Enter / Backspace rules), `editor/RichLayout.kt` (prefix / hidden-line geometry + offset mapping), `editor/HtmlCodec.kt` (web checklist markup, nested `<ul>/<ol>`, `data-collapsed`, `<pre><code>`), `EditorScreen.kt` (tap a checkbox / chevron, both drawn on a Canvas), `EditorToolbar.kt` (checklist + `M↓` toggle) | ✅ tap-to-check, nested items (Indent / Outdent move the whole sub-list), chevron collapse (saved as `data-collapsed`), `# ` `## ` `### ` `> ` `- ` `1. ` `[] ` ` ``` ` shortcuts + `/check` `/code` + Enter, list exit on Enter / Backspace, shortcuts on/off (`markdownMode` pref, same as Settings). Basic code blocks (monospace lines); copy / jump / delete buttons come with Part 5 |
| Editor — part 4   | tags + notebook pickers | `ui/screens/EditorSheets.kt` | ✅ manage-tags modal, move-to-notebook sheet (+ create) |
| Editor — part 5   | code, tables, inserts | `editor/TableData.kt`, `editor/MarkdownCodec.kt`, `editor/RichDoc.kt` (inline code / links in the mark Int, divider + table as one-char object paragraphs, paste fragments), `editor/HtmlCodec.kt` (`<code>`, `<a>`, `<hr>`, `<table>`), `ui/screens/EditorTables.kt`, `EditorSheets.kt` (link dialog, cell sheet), `EditorScreen.kt` (divider + code cards, table / code-button overlays, link tap in Reading mode) | ✅ inline code, code blocks (Copy / Bottom / Delete), tables (add / delete rows + columns, header toggle, sideways scroll), link insert / edit / remove, divider, date + time, paste detection (markdown / HTML source / code). Code lines wrap instead of scrolling per block; pasted images become alt text until Part 6 |
| Editor — part 6+  | images/drawing, md source, export, toolbar customiser | | ⏳ |

After sign-in the app shows Home. Tap the avatar for Profile (Sign out lives there).

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
│                                                     iw_notes, iw_notebooks, iw_sort, iw_seed_version, iw_searches,
│                                                     iw_prefs, iw_fontSize, iw_profile)
├── editor/      RichDoc, HtmlCodec, RichTransformation   (editor document model + codec)
├── util/        Helpers             (formatDate, stripHtml, fmtNum, greeting …)
└── ui/
    ├── InkwellApp.kt                (root: login gate + current screen)
    ├── Navigator.kt                 (Route + back-stack)
    ├── theme/Theme.kt               (colours, fonts, radii)
    ├── components/                  (InkText, NoteCard, Toast, DeleteConfirmModal …)
    └── screens/                     (one file per page; CreateLayer = FAB/menu/toast shared by pages)
```
