# Editor Core Notes

This document tracks editor stability behavior.

## Fixed / protected

- Unsaved edits flush when Android sends the app to background.
- Unsaved edits flush when the page hides/unloads.
- Auto-save does not run while Reading Mode is active.
- Save errors are caught and shown as a toast instead of crashing.
- Duplicate rapid saves are reduced with a save hash.
- Checklist starter note uses the correct checklist DOM.
- Images and source-mode text are protected from overflow on mobile.
- Code block action buttons are not selectable.

## Manual test checklist

1. Create note, type text, immediately press Android back, reopen note.
2. Edit note, close app from recent apps, reopen.
3. Toggle Reading Mode and return to editor.
4. Create checklist note from quick action.
5. Paste long Markdown/code block and check horizontal scroll.
6. Add image/drawing and save/reopen.

## Tag UX

- Saved tag chips can be tapped in the editor to attach them quickly.
- The + button next to the tag input saves the typed tag.
- Quick-create Tag and Quick-create Notebook now use in-app themed modals instead of native prompts.


- Tag selector updated: the editor footer now stays compact, and full tag management opens in a modal.

- Manage Tags modal is now compact, smaller, and uses a 2-column action layout.

- Manage Tags modal now uses horizontal scrolling compact tag chips and removes duplicate Add controls.
