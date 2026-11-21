# Owner-provided Zap icon

Source: the owner's `Zap vs X.png`, 1080 × 1080. Original artwork is preserved
byte-for-byte in `zap-logo-original.png`; no generative redraw or iOS changes.

`play-store-icon.png` is a mechanical 512 × 512 PNG export of the full original.
No corner rounding or shadow is baked in. Play applies its own presentation.

The production Android launcher uses an adaptive icon. Its foreground is the
centered 756 × 756 crop of the original dark canvas, resized to 432 × 432.
Only outer background padding is removed; all white logo pixels are retained.
This fits the mark inside Android's central safe zone for OEM masks. The system
chooses the final shape. Both resources are production-only.

References:
- https://developer.android.com/develop/ui/compose/system/icon_design_adaptive
- https://developer.android.com/distribute/google-play/resources/icon-design-specifications

An icon-source/build check is not evidence of Play listing approval or A16 install.
