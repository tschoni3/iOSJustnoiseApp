# History source-first baseline — 2026-09-06

The owner approved implementing the real Android History screen from the unchanged
iOS Release source before missing rendered references are supplied. This is a
source baseline, **not** captured visual acceptance. The approved scope and timing
exceptions are in [the implementation plan](../../ANDROID_PLAY_IMPLEMENTATION.md).
No iOS source, stored history, shared contract or restriction behavior is changed.

## Observed iOS presentation

- `SessionHistoryView.swift`: native dark toolbar; “Timeline” 20pt semibold with
  12pt chevron opens **90 Days / Year**. Trailing brand icon is 26pt; Settings
  follows with 24pt spacing. The summary background is #0E0E0D, 162pt high, with
  30pt bottom corners. Cards have 16pt outer horizontal margin, 12pt gap and 14pt
  bottom margin. Each card is 128pt high, #131312, corner radius 10pt, label 13pt
  semibold orange92%, value 28pt bold white. The source labels are **Zaps** and
  **Protected Attention** and totals are all-time, independent of the selected
  calendar window. [Source](../../../justnoiseradio/Features/History/SessionHistoryView.swift#L29).
- `DayDotGridView.swift`: white background, 6pt grid spacing and 16pt padding.
  Ninety Days uses four columns, today−60 through today+29, and initially scrolls
  to the row containing today−14. Year is the current local calendar year and
  tries 8–120 columns to fit every circle into the available viewport. Its footer
  shows days remaining after today and rounded percentage of the year including
  today. [Grid](../../../justnoiseradio/Features/History/DayDotGridView.swift#L7),
  [layout](../../../justnoiseradio/Features/History/DayDotGridView.swift#L335).
- A selected day opens a dark #141414 card, corner radius 24pt, padding 22pt,
  maximum width 290pt, with `dd.MM.yyyy` in 24pt bold and 17pt detail lines.
  Outside tap, close and native dismissal leave history unchanged.
  [Detail](../../../justnoiseradio/Features/History/DayDotGridView.swift#L151).
- The shared density palette is #EBEBEB / #C0C0C0 / #777777 / #191919, with
  thresholds at 15 minutes, two hours and six hours. Today pulses orange in iOS;
  selected dots gain an orange outline and 1.08 scale.
  [Dot](../../../justnoiseradio/Features/History/DayDotView.swift#L3).

## Deliberate Android presentation differences

- Cards say **Zaps / Session time**, with an immediately visible all-completed /
  elapsed-time explanation. Known interruption and timing-uncertainty counts stay
  immediately under the cards, as well as in the selected-day details. The header
  can therefore grow beyond the source's 162pt; it never hides warnings to achieve
  a matching height. No continuous-protection or reflection-count claim is shown.
- The 90-day grid keeps four columns at ordinary widths. Year uses an adaptive
  **scrollable** grid with at least 48dp day targets instead of tiny fit-to-screen
  targets. Extremely narrow windows reduce columns. Compact windows and enlarged
  text scroll the summary with the calendar, keeping controls and full values
  reachable; Android native Back and a 48dp Settings entry remain visible.
- Today has a **static orange marker**, avoiding a perpetual pulse and honoring
  reduced-motion intent. Every day exposes its localized full date, duration,
  session count and warning status to accessibility services. The range menu
  exposes the selected window as its accessibility state.
- Day details allow up to 340dp width and scroll at large text, with a 48dp Close
  action plus outside/native Back dismissal. The visible date retains the source
  numeric format; no reflection or weekly-insight placeholder is added.

## Real data and lifecycle boundary

`IntegratedHistoryViewModel` uses only the existing blocking
`Gate0Preferences.loadHistoryTimeline(range)` API on a worker, independently of
list pagination. This preserves all stored UUIDs, rows, target counts, interruption
flags, outbox and deletion guards. No preview records enter the real application.

The read projection uses the current device zone; 90 Days is today−60 through
today+29 and Year is the current local year. Foreground, date and zone changes
trigger fresh reads. Backgrounding invalidates late publication and clears the
visible snapshot. Only window/day UI identity is saved for recreation, not session
records; a failed or incomplete read shows an error and explicit Retry, not an
invented empty timeline.

The already approved Android-only allocation conserves each stored millisecond
across local-midnight portions, retains sub-minute slices and keeps timing
uncertainty independent of sticky interruptions. A session spanning midnight may
touch multiple days but counts only once in all-time totals. The UI does not
rewrite or reinterpret the SQLite schema, repeat sessions or change NFC policy.

Tests and emulator renders remain implementation evidence, not physical blocking,
current iOS rendered equivalence, Play approval or final A16 visual acceptance.
