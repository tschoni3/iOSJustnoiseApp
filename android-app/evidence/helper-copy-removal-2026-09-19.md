# Owner-requested helper-copy removal — September 19, 2026

The owner explicitly rejected the History interruption/uncertain-duration
paragraphs and requested removal of permanent helper text throughout Android.
This supersedes the previous visual requirement to display those warnings.

Removed from presentation:

- History header/footer/day-detail warning paragraphs, interpretation/zone helper
  lines and warning additions to calendar spoken labels.
- Completion helper/warning card. Real target count, duration, Session time and
  Done remain.
- Modes' recent-selection, app-selection and Save explanatory paragraphs; the
  always-visible character counter. Invalid-name feedback remains conditional.
- Legacy selection explanatory paragraphs; unavailable items remain labelled.
- Schedule list/editor explanatory paragraphs and healthy revalidation commentary.
  Next start, failed execution and missing-setup recovery remain.
- Settings explanatory subtitles and repeated support/about prose. Permission/
  Live Update state remains a short truthful label.
- Permanent import-success/workspace/login helper copy in Account. Import offer
  and confirmation, analytics consent, deletion confirmation and errors remain.

No underlying session/History flags, duration calculations, auth authority,
schedule/NFC behavior or permissions were changed by this presentation cleanup.
No iOS edits. The existing schedule-readiness diagnostic is included, but this
build does not fix or close the failed 22:54 automatic start.

Signed candidate `1.0.0-1-766a1b5b` built in 51 seconds. Build-integrated vital
lint and signed APK/AAB verification pass. Related UI assertions were updated,
not run; no JVM suite, emulator or owner-phone instrumentation was performed.
The candidate is not installed; A16 visual acceptance is pending. The installed
diagnostic build remains `cfa5e637`. No second test schedule was armed.

| Artifact | SHA-256 |
| --- | --- |
| APK | `f3c473bf92f4e9d6c9a5b2a3da2403116c021773f67bb301e40094cd2defc450` |
| AAB | `766a1b5bb740422c68ed8ec767679a80189b59fd1493a0896cf9c6c2b48691c7` |

Snapshot includes both signed artifacts and `verification.json`. Distribution
remains HOLD; clean presentation is not evidence of core-feature acceptance.
