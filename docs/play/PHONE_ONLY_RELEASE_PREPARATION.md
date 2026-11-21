# Phone-only release preparation — 2026-10-11

Owner decision: prepare Justnoise for Android smartphones; defer tablets.
Do not publish a public production release yet. Do not change existing internal
distribution or delete owner data merely to prepare this scope.

Owner acceptance, 2026-10-11: both changed onboarding screens are visually accepted
("die Onboarding-Screens sind okay. Passt soweit"). No repeat of these checks is
required unless their presentation changes again. Owner also confirmed tablet
exclusion before public publication; the reviewed exclusions are now applied. This does
not authorize public rollout or assert account screenshot behavior was tested.

Continuation, 2026-10-11: owner requested proceeding with the next open step.
Live Play navigation reached Device catalog → Get started, which displays the
first-use Device catalog Terms of Service with Accept/Cancel. The owner explicitly
confirmed accepting these terms; acceptance completed. The visible supported
Tablet list contained 278 model entries, including 59 foldable smartphones.
219 other tablet/large-display model entries were excluded and their visible
status verified as `Excluded manually`; all 59 foldable entries remain `Supported`.
Evidence: `android-app/evidence/device-catalog-phone-only-2026-10-11.json` and
`android-app/evidence/device-catalog-phone-only-2026-10-11.png`.

## Frozen technical package

- Package: `shop.justnoise.app`.
- Version: `1.0.16 (18)`.
- Candidate: `android-app/build/candidates/1.0.16-18-6b2d94af/`.
- Signed AAB SHA-256:
  `6b2d94af85855798b0bc0c0c3a420334d783d26a651311aa0300c25518b9ebfd`.
- Existing upload certificate; 555 JVM tests, signed package and full local
  boundary verification passed; release lint has zero errors and 109 warnings.
- Already published to internal testing, not public production. No re-upload
  or rebuild is needed solely to prepare the intended distribution scope.

## Phone-only distribution decision

Use Play Console Device catalog exclusions for tablet models. Do not add a fake
telephony requirement or a density allowlist: those could exclude suitable phones,
fail to exclude cellular tablets or create an unsupported hardware dependency.
`supports-screens` alone is not proof of phone-only distribution.

Applied distribution change and remaining boundaries:

1. Open Monitor and improve → Reach and devices → Device catalog.
2. Filter supported devices to Tablets and review the visible model list. The
   export did not yield a usable download; UI-backed row evidence was saved instead.
3. Exclude only tablet models and verify all their variants; retain phone models,
   especially Samsung Galaxy A16/A17. Do not infer tablet identity from density.
4. Verify exclusions per model, not a zero-Tablet category count: Google classifies
   several foldable phones as Tablet. Preserving those phones leaves 59 supported
   entries in this category. The 219 selected entries were verified excluded;
   Galaxy A16 and A17 search results remain Supported. Manual model exclusions do not
   automatically exclude future tablet models; review new models before launch.
5. Onboarding copy/layout is owner-accepted. Normal-account screenshot behavior
   remains separately unverified; check only that affected path if needed.
   Previously accepted feature tests remain
   accepted; no repeated setup, session, schedule or data-deletion tests.
6. Only after explicit public-release authorization prepare/promote the production
   release; inspect current Play validation without accepting legal terms for the
   owner or inventing store declarations.

Exclusions can affect distribution beyond one release and were applied following
the owner's instruction to continue with tablet exclusion. No public production
release was published. This is a current reviewed-model exclusion, not a universal
future-proof phone-only filter or evidence of tablet hardware testing.
Store-policy/privacy work remains outside this development task, not implicitly
completed. Public Play checks and any remaining affected-path acceptance are separate from
the technical signed-package PASS.

References:
- https://support.google.com/googleplay/android-developer/answer/7353455
- https://developer.android.com/guide/topics/manifest/compatible-screens-element
