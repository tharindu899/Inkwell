# Inkwell Native UI - Parts 1 to 3 (Soft Bento, Jetpack Compose)

Package: `com.inkwell.notes` (copy the files under `app/src/main/java/com/inkwell/notes/` into your project).

## Part 3 (new): Tags + Search
- Tags: "All tags" cloud with counts, tap a tag to filter (accent chip + removable label), "All notes" by default, help card when there are no tags
- Search: auto-focused search bar with clear button, modes (All / Title / Content / Tags), notebook filter chips, result count, matches highlighted in title/preview/tags, recent searches (last 8, tap to reuse, Clear all)
- FAB now matches the web menu: Search notes / Create tag (name dialog) / New notebook / New note
- `store.pendingNewTag` holds the tag from "Create tag" for the editor part to pick up

## Part 2: Notebooks + Notebook detail
- Notebooks: total bar (count-up), sort chips (Name / Notes / Created), 2-column cards tinted with the notebook colour, reorder animation when sorting
- Notebook card menu: Edit / View notes / Delete (bottom sheet that slides out properly)
- Create / edit dialog: name, 8 colours, 18 icons (same colours and icon keys as the web app)
- Delete confirm: "Notes in this notebook will not be deleted"
- Notebook detail: hero (icon, name, count), sort chips, note list, FAB "new note in notebook", options sheet (All notebooks / Search in all notes / Delete)
- Toast pill ("Notebook created / updated / deleted"), springy dialog pop-in
- `InkStore` = in-memory data holder (notes, notebooks, sort, toast). Replaced by real storage + Drive sync in the data-layer part
- Home FAB "New notebook" opens the Notebooks tab with the create dialog

## What is in Part 1
- Theme: Light / Dark / GitHub (same GitHub colors as the web app), colors animate when you switch
- Components: pressScale, staggeredEntrance, InkChip, PastelCard, StatCard, floating sliding bottom bar, speed-dial FAB
- Navigation: same routes as the web app, animated screen transitions, bottom bar only on Home / Tags / Notebook
- Home screen: greeting top bar, count-up stats, sort chips, wide pinned card + staggered bento grid
- Temporary: the Settings icon on Home cycles the theme so you can preview all three

## Dependencies (app/build.gradle.kts)
```kotlin
implementation(platform("androidx.compose:compose-bom:2024.10.01")) // or your latest BOM
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")
implementation("androidx.compose.material:material-icons-extended")
implementation("androidx.compose.ui:ui-tooling-preview")
implementation("androidx.activity:activity-compose:1.9.3")
implementation("androidx.navigation:navigation-compose:2.8.3")
```
Needs Kotlin 2.x with the Compose compiler plugin, `SortOrder.entries` needs Kotlin 1.9+.
Manifest: set `android:name` of the activity to `.MainActivity` and use a NoActionBar theme.

## Build on GitHub (no Android Studio needed)
1. Put the contents of this folder at the **root** of a GitHub repo (the `.github`, `app`, `build.gradle.kts` ... all at top level).
2. Push to `main`. `.github/workflows/build-apk.yml` builds the APK, uploads it as an artifact and makes a GitHub Release.
3. Optional signed release APK: add the same 4 secrets as your web repo: `KEYSTORE_BASE64`, `KEY_ALIAS`, `KEY_PASSWORD`, `STORE_PASSWORD`.
   Without them it builds a debug APK instead. Same keystore + same package = it updates your installed Inkwell.
4. `VERSION_CODE_OFFSET` (top of the yml) keeps versionCode above your old Capacitor builds.

## Next parts
4 Login + Profile + Settings, 5 hook to the real data/editor.
