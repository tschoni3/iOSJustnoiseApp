# Schedule portability audit

Status: **SAFE ELIGIBILITY RULES FROZEN — TEMPORAL SEMANTICS DEFERRED**

This document characterizes the shipped iOS implementation without treating framework behavior or accidental quirks as Android requirements. It authorizes only the framework-neutral rules in `product/behavior/portable-fixtures.v1.json`; it does not authorize production Android scheduling.

## Shipped iOS observations

| Concern | Current iOS behavior | Portability conclusion |
| --- | --- | --- |
| Persisted shape | `Schedule` stores an identifier, name, mode identifier, Foundation `Date`, integer `Calendar.current` weekdays, enabled flag, and optional last-fire `Date`. | Stable identity and enabled state are portable. The mixed instant/local-component representation is not. |
| One-time versus repeating | An empty weekday array means one-time; a non-empty array means repeating. | The distinction is frozen. Native weekday integers are not; portable fixtures use named weekdays. |
| Eligibility | Save and candidate selection require an enabled schedule, a resolvable mode, and a non-empty application/category/website selection. | Frozen with deterministic failure precedence: `disabled`, `modeUnavailable`, then `targetSelectionEmpty`. |
| Candidate count | The bridge computes every eligible next fire but arms only the earliest. Equal fire dates keep the first schedule encountered. | Observed only. Multiple simultaneous schedules and tie behavior remain undecided. |
| Local-time calculation | `nextFireDate` extracts local components with `Calendar.current`. Repeating candidates use the device’s current calendar/time zone at calculation time. | No creation-zone or travel policy exists. Do not copy this implicitly. |
| Near/past time | A candidate less than 90 seconds away is shifted to a minute boundary after the lead-time buffer. A past one-time schedule is rolled to tomorrow at the same clock time. | Observed only. This can change the intended wall time and is not accepted catch-up behavior. |
| Monitoring duration | The system interval ends 20 minutes after the chosen fire and is shortened to 23:59 if it would cross midnight. | Observed only. Product duration and midnight behavior are unresolved. |
| Weekday enforcement | A repeating Device Activity interval is armed and the extension also checks the current integer weekday before applying shields. | Platform defense-in-depth, not a portable recurrence algorithm. |
| Manual-session collision | Foreground rebalancing pauses while a session is active, but the background start callback has no explicit portable collision decision. | Collision behavior requires a product rule and physical evidence. |
| Start ownership | The extension writes the active flag, owner `ext`, and a session-start marker; foreground reconciliation may adopt that start and transfer owner to `app`. | Cross-process ownership is iOS-specific. Android needs its own adapter and recovery evidence. |
| Interval-end cleanup | The extension treats any session-start marker as evidence of a manual session even though it also writes that marker on scheduled start. The source branch therefore does not prove automatic shield cleanup when the app never foregrounds. | This is a **device-test risk**, not a portable rule or a confirmed field failure. Do not claim unattended cleanup until physical evidence closes it. |
| One-time completion | Foreground marker consumption disables a fired one-time schedule. Manual stop also disables the active one-time schedule. | Product intent appears one-shot, but app-never-opened and process-death paths remain unproven. |
| Recovery/rearming | Foreground load consumes fire markers and mutations debounce a rebalance. A single named Device Activity monitor is stopped/replaced when the selected candidate changes. | Reboot, force-stop, permission revocation, missed callback, and multi-schedule recovery remain unresolved. |
| Mode deletion | A mode can currently be deleted while schedules still reference its identifier. Candidate selection excludes the orphan, and the UI may show `Unknown Mode`. | Existing iOS conformance gap; Android must not silently inherit it. |

## Frozen cross-platform safety slice

The `scheduleEligibility` fixtures freeze only these decisions:

1. Empty `repeatWeekdays` is `oneTime`; one or more named weekdays is `repeating`.
2. A disabled schedule is ineligible.
3. An enabled schedule with no resolvable mode is ineligible.
4. An enabled schedule with a mode but no blocking target is ineligible.
5. Only a schedule passing all three gates is eligible to enter a platform scheduler.
6. When more than one eligibility gate fails, the diagnostic precedence is `disabled` → `modeUnavailable` → `targetSelectionEmpty`.

Swift and Kotlin pure rules consume the same cases. These rules do not calculate a fire instant, choose among multiple eligible schedules, apply restrictions, or recover a session.

## Product decisions required before Android schedule implementation

1. Choose the persisted time model: absolute instant, floating local wall time, or wall time plus named IANA time zone.
2. Decide travel behavior for repeating schedules: follow the device or retain the creation zone.
3. Define nonexistent spring-forward times and duplicated fall-back times.
4. Define scheduled-session duration, midnight crossing, and whether the user may configure duration.
5. Define ordering/tie rules and whether multiple eligible schedules can be armed.
6. Define collisions with manual or already scheduled sessions, including mode/target ownership.
7. Define missed-fire grace/catch-up behavior after reboot, force-stop, Doze, app update, clock change, or permission loss.
8. Define exactly who clears restrictions if the app never foregrounds and how stale active state is reconciled.
9. Define whether notifications are derived from the same selected occurrence or are independent reminders.

Each decision needs portable fixtures plus native domain tests. Background delivery, restriction ownership, reboot, time-zone change, daylight-saving behavior, and cleanup still require physical Pixel/Samsung evidence.
