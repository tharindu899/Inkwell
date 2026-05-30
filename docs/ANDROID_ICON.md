# Android Icon Notes

The GitHub Actions workflow generates launcher icons from `public/icon-512.png`.

Important sizes:

| Resource | mdpi | hdpi | xhdpi | xxhdpi | xxxhdpi |
|---|---:|---:|---:|---:|---:|
| Legacy `ic_launcher.png` | 48 | 72 | 96 | 144 | 192 |
| Adaptive foreground canvas | 108 | 162 | 216 | 324 | 432 |

Adaptive icons need safe-zone padding because Android launchers apply a mask and may zoom/crop the foreground. The workflow now places the app icon inside about 66% of the adaptive foreground canvas, so the icon stays visible on MIUI and other launchers.
