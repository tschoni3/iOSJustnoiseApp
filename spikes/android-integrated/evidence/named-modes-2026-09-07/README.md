# Named modes: local implementation evidence

Final development APK SHA-256:
`9d5efacf4eb4a06aca96374253c374078ef045b26ef5f70fc4cc29f889c0a36c`.
Package `com.justnoise.androiddev`; not a Play release. iOS and the owner's A16
installation/data were unchanged.

The final local verifier passed 289 JVM tests (zero failures/errors/skips), 36
Python package checks, lint/builds and release APK boundary. The first expanded
management test run exposed a test assumption that a picker contained one row;
it correctly also contained a saved removal-only row. The assertion now identifies
the intended row, and the complete suite was rerun successfully.

Two focused emulator passes each passed all 17 cases (9 real-store mode flows,
8 schedule UI/restoration flows), with no failures or skipped cases. The final pass
used the exact APK above and took 48.481 seconds. Tests were serial-qualified to
`emulator-5556`, hardware `ranchu`, and guarded before synthetic fixture cleanup.
No physical-phone instrumentation was run.

- `catalog.png`: real persisted Noise and new Sleep; Noise remains explicitly
  selected because Save does not implicitly select Sleep.
- `compact-editor.png`: real mode/app data in a 360×640 / 200% font-scale content
  harness. The app list scrolls while Cancel/Save remain reachable. The surrounding
  emulator display is not resized; this is not an A16 or system-inset screenshot.

Logs:
`/private/tmp/justnoise-named-modes-final-2026-09-07.log` and
`/private/tmp/justnoise-named-modes-emulator-final-2026-09-07.log`.

See [implementation and remaining A16 check](../../../../docs/ANDROID_NAMED_MODES.md).
