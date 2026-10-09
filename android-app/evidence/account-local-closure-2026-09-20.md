# Android account boundary — final local closure

Date: September 20, 2026. Scope: synthetic local verification only. No owner
account, Supabase record, password, phone state, analytics consent or iOS source
was changed.

## Result

**PASS for the local account/runtime boundary.** A new production-module
regression covers one uninterrupted A -> B -> A round trip through the real
`AccountRuntime`, `AccountCoordinator`, auth journal and product workspace stores,
using only synthetic in-memory auth/HTTP data.

The test proves that:

- account A and B receive separate mode/app-selection stores;
- signing out restores the pre-account local workspace;
- returning to A restores A's data and never exposes B's data;
- the device-global Zap pairing survives account switches;
- optional analytics is disabled at the account boundary; and
- product access is reopened only for the current reconciled account.

Test: `AccountRuntimeTest.accountAToBToARestoresOnlyEachWorkspaceAndKeepsGlobalPairing`.

The targeted test passed. The complete `com.justnoise.app.auth.*` release group
then passed **142 tests**, followed by the complete production release suite:
**470 tests across 52 suites, zero failures, errors or skips**. Release lint passed
with **0 errors, 74 warnings and 2 hints**. Product contracts passed (18 colors,
27 copy entries and 89 behavior cases), and the complete Android pre-device
verifier plus its 44 artifact regressions passed.

The production APK/AAB are byte-identical to candidate `8dc7ba54`; test source is
not packaged into the application. Independent package/certificate/signature/
alignment verification passed again. Therefore the A16 already has the exact
runtime binary and no reinstall is required.

## Deliberate evidence boundary

This does not convert the skipped live owner-account exercise into a physical
PASS. A -> B -> A, registration, password recovery and deletion against the live
backend remain unexecuted because the owner does not know the required password
and asked to skip the operation. No sign-out, reset or deletion should be attempted
on the owner account merely to close that evidence gap.

