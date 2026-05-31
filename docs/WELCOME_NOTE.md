# App Welcome Note

The first-time welcome note is generated from:

```txt
src/utils/seed.js
```

It demonstrates common editor toolbar features:

- H1 / H2 / H3 headings
- paragraph text
- bold / italic / underline / strikethrough
- blockquote
- bullet list
- numbered list
- checklist
- inline code
- code block
- table
- link
- image placeholder
- tags and notebook usage

The welcome note is only created when local note storage is empty. It does not overwrite real user notes.

## Current app welcome note

- Current welcome note title: **Welcome to Inkwell**
- The actual app welcome note is created/updated by `src/utils/seed.js`.
- It now includes examples for all main toolbar options.
- Existing real user notes are not overwritten.
- If a `welcome-note` already exists, only that built-in app welcome note is refreshed.

- The visible welcome note does not show a fixed app version or version tag.
- The internal seed version can still refresh the built-in welcome note after updates.
