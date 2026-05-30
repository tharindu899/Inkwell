# Release check

This project was checked for public GitHub release readiness.

## Before pushing

Run:

```bash
npm ci
npm run release:check
```

Expected result:

- Release file check passes
- Vite production build succeeds
- Runtime dependency audit passes with `npm audit --omit=dev --audit-level=high`

## Required GitHub Actions secrets

Add these in **Repository Settings → Secrets and variables → Actions**:

- `KEYSTORE_BASE64`
- `KEY_ALIAS`
- `KEY_PASSWORD`
- `STORE_PASSWORD`
- `VITE_GOOGLE_CLIENT_ID`

Optional:

- `VITE_GITHUB_REPO` — defaults to `tharindu899/Inkwell`
- `VITE_GITHUB_TOKEN` — only for private repos. Leave empty for public releases.

## Do not commit

- `.env`
- `node_modules/`
- `dist/`
- `android/`
- `*.jks`
- `keystore.txt`

## Documentation checks

- README structure is current.
- `docs/` folder is listed in README.
- App update/install behavior is documented.
- Android icon sizing doc exists.
- Theme persistence doc exists.
