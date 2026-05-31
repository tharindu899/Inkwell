# Changelog

All important Inkwell updates are tracked here.

Use this file for the full update history.  
Use the GitHub Release description/body for the short changelog shown inside the app update popup.

---

## v1.2.1

### Changed
- Removed visible version text/tag from app welcome note.

### Fixed
- Settings theme selector is now icon-only and compact on mobile.

### Added
- Added GitHub as a third app theme.

### Fixed
- Fixed welcome note seed build syntax by escaping markdown backtick examples.

### Added
- App welcome note now includes full toolbar examples.

### Updated
- App welcome note version updated to v1.2.1.
- Welcome note seed refresh version updated to v1.2.1.

---

## v1.2.0

### Added
- App welcome note now includes examples for all main editor toolbar options.
- Added `docs/WELCOME_NOTE.md`.
- Refreshes existing `welcome-note` safely without touching real user notes.

### Updated
- Changelog version corrected to the current release run.

---

## v1.0.9

### Fixed
- Added selected delete confirmation modal.
- Added 5-second Undo after selected delete.
- Fixed selected/unselected tag colors on Tags page.
- Improved Tags page tag pill styling.
- Aligned notebook icon styling inside note cards.
- Improved editor tag selector and Manage Tags modal.

### Updated
- README structure and docs updated.
- Release checklist updated.

---

## v1.0.8

### Fixed
- Theme selection now persists after closing and reopening the Android app.
- Update checker now shows **You are already updated** when installed version matches latest release.
- Update popup layout improved for light and dark themes.
- In-app APK update install flow improved.

### Added
- Theme persistence documentation.
- Android update install documentation.

---

## v1.0.7

### Fixed
- Tags page layout improved.
- Editor tag selection improved.
- Manage Tags modal added.
- Tag chips made more compact.

---

## v1.0.6

### Fixed
- Light mode code block colors improved.
- Code block copy/action buttons improved in light mode.

---

## v1.0.5

### Added
- In-app APK update checker.
- APK size, download count, version, and progress display.
- Android Package Installer opening from the app.

---

## v1.0.4

### Fixed
- App icon and Android launcher icon sizing improved.
- Added app icon preview documentation.

---

## Release description template

Copy this into a GitHub Release body when publishing a new APK:

```md
## Inkwell vX.X.X

### Fixed
- ...

### Added
- ...

### Updated
- ...

### Install
Tap **Install** in the app update popup or download the APK from this release.
```
