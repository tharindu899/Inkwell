/* ──────────────────────────────────────────
   Inkwell — src/utils/haptics.js
   Thin wrapper around Capacitor Haptics.
   Falls back silently in browser / non-native.
   ────────────────────────────────────────── */

export async function haptic(style = 'light') {
  try {
    const { Capacitor } = await import('@capacitor/core');
    if (!Capacitor.isNativePlatform?.()) return;
    const { Haptics, ImpactStyle } = await import('@capacitor/haptics');
    const styleMap = {
      light:  ImpactStyle.Light,
      medium: ImpactStyle.Medium,
      heavy:  ImpactStyle.Heavy,
    };
    await Haptics.impact({ style: styleMap[style] ?? ImpactStyle.Light });
  } catch {
    // Not available in browser preview — silently ignored
  }
}
