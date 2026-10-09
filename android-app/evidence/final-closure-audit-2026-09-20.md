# Justnoise Android — Abschlussaudit und Restplan

Stand: 20. September 2026. Das ursprüngliche Quellcode-, Artefakt- und
Evidenzaudit war read-only. Die spätere, vom Eigentümer autorisierte
Play-Vorbereitung änderte ausschließlich die Android-Release-ID und den exakten
Android-Auth-Rücksprung; Supabase erhielt den neuen zusätzlichen Redirect. iOS,
Gerät, Nutzerkonten und bestehende Redirects blieben unverändert. Anschließend wurde
die autorisierte App angelegt und der geprüfte AAB im internen Track veröffentlicht.

## Aktueller maßgeblicher Abschlussstand

**100 % für den vereinbarten technischen internen Play-Test-Meilenstein.** Die
frische Play-Installation auf dem A16 und der angeforderte Eigentümer-Smoke-Test
wurden erfolgreich gemeldet. Das ist keine öffentliche Store-Freigabe oder
Google-Review-Zusage. Der signierte Kandidat, die Kernfunktionen und der aktive
interne Track sind vorhanden.

| Bereich | Maßgeblicher Stand | Noch offen |
| --- | --- | --- |
| Signierter APK/AAB-Kandidat | **PASS** — 1.0.0 (1), `shop.justnoise.app`, Prüfsummen und Release-Prüfbericht vorhanden; Google Play hat exakt diesen AAB als Version 1 verarbeitet; Eigentümer meldet Play-Install-Smoke auf dem A16 erfolgreich. | Keine Wiederholung ohne betroffene Änderung oder neuen Befund. |
| Modi, Mehrfach-App-Auswahl, manueller Zap, Zeitpläne, History | **PASS im vereinbarten MVP-Umfang** — implementiert; normale und geplante Abläufe sowie Chrome-Sperre/Freigabe wurden auf dem A16 bestätigt. | Keine unveränderten Abläufe wiederholen. |
| PostHog | **PASS für den begrenzten Release-Nachweis** — echtes Opt-in-Start/Ende empfangen; echtes Opt-out-Start/Ende bei validierter Verbindung ohne neue Events. | Accountwechsel-/Prozessneustart-Sonderfall nicht live abgenommen; kein Grund, den bestandenen Zyklus zu wiederholen. |
| Pflichtkonto | Google-Anmeldung und zwei getrennte Supabase-Konten sind belegt; 142 lokale Account-Tests inklusive vollständigem synthetischem A→B→A-Rundlauf bestehen. | A→B→A, Recovery, Registrierung und Löschung wurden nicht gegen echte Konten ausgeführt. Der Eigentümer hat diesen Block wegen fehlendem Passwort ausdrücklich übersprungen; keine Kontomutation durchführen. |
| Google-Login-Darstellung | Funktionaler PKCE-Browserfluss ist belegt; Google-Projekt führt App-Name Justnoise und Logo. | Branding ist unverifiziert, autorisierte Domain ist Supabase und es existiert kein Android-OAuth-Client. Native Anmeldung ist deshalb kein sicherer Last-Minute-Tausch. |
| Samsung-Systemanzeige | **PASS auf dem Eigentümer-A16** — der finale Lauf zeigte Artwork, Modus, laufenden Timer und Samsung Now Bar. | OEM-Darstellung bleibt systemgesteuert; nach keiner Runtime-Änderung erneut testen. |
| Play Console | **PASS für internen Test** — App `4972093831482851244`, Track `4701314183475386469`, Status Active; Version 1 verfügbar für interne Tester, keine Validierungsfehler. | Zwei nicht blockierende Diagnosehinweise (Mapping/native Symbole); Public Setup/Review/Production sind getrennt. |
| iOS | **Unverändert** und weiterhin Referenz. | Fehlende aktuelle Referenzaufnahmen begrenzen nur die visuelle Pixel-Abnahme. |

Frühere chronologische Abschnitte dieses Dokuments bleiben als Auditspur erhalten.
Bei Widersprüchen gilt die Tabelle oben. Ein bestandener Hardwaretest wird nur nach
einer betroffenen Build-Änderung, einem beobachteten Fehler oder einer konkret
fehlenden Assertion wiederholt.

Unmittelbar vor dem finalen Eigentümertest wurde der A16 erneut passiv geprüft:
installierte Version 1.0.0 (1), APK-Hash identisch zum Kandidaten, Accessibility
aktiviert/gebunden und keine aktive Justnoise-Sitzungsbenachrichtigung. Damit ist
der dokumentierte Final-Test-Ausgangspunkt aktuell belegt, ohne die App zu bedienen.

## Ziel und Abgrenzung

Ziel bleibt ein sicheres, iOS-nahes Android-Erlebnis mit eigenen Modi/App-Auswahl,
Zap-Sitzungen, automatisch startenden Zeitplänen, History sowie den später ergänzten
Pflichtkonten, PostHog und der vereinbarten Samsung-Systemanzeige. iOS bleibt die
unveränderte Referenz. Interner Test ist der erste Meilenstein, nicht öffentlicher
Launch oder eine Zusage der Google-Freigabe.

Nicht neu eröffnen: Architekturumbau, zweites/A17-Testhandy, Capture, Widget,
Cloud-Synchronisierung, Wochen-Insights oder vollständige iOS-Funktionsparität.
Bereits bestätigte manuelle/geplante Zap-Samples gelten weiter; nur betroffene
oder fehlende Ergebnisse werden nachgetestet. Datenschutz-/Shop-Veröffentlichung
wurde vom Eigentümer ausgeschlossen; notwendige Abhängigkeiten werden hier
ausgewiesen, nicht ausgeführt.

## Verifizierter technischer Stand

- Lokaler Play-Kandidat `build/candidates/1.0.0-1-94741f2a/`, Justnoise 1.0.0 (1),
  `shop.justnoise.app`, signierte APK und AAB samt Prüfbericht.
- AAB SHA-256:
  `94741f2afee9319b25e5cdca11ac126baf768d1dc755ec74ccb27e1cd86e697d`.
- Aktuelle lokale XML-Berichte: 470 Tests / 52 Suites, keine Fehler oder Skips.
  Tests in diesem Audit nicht wiederholt. Release-Lint: 0 Fehler, 74 Warnungen,
  2 Hinweise laut letzter verifizierter Ausführung; nicht als warnungsfrei bezeichnen.
- Eigene Modi und Modusreferenzen in Zeitplänen sind implementiert und vom Nutzer
  bereits in realen manuellen/geplanten Abläufen bestätigt.
- PostHog-Opt-out-Neustartfehler ist im neuen Kandidaten lokal abgesichert.
  Zustimmung gilt pro App-Prozess, nach Neustart erneut optional einzuschalten.
- Kandidat `8dc7ba54` am 20. September um 14:52:07 nach bestätigtem
  Sitzungsende datenerhaltend installiert; installierter APK-Hash stimmt überein.
  Nutzer bestätigt direkten Wechsel zu Justnoise, weiterhin gesperrtes Chrome
  während der Sitzung und wieder nutzbares Chrome nach Zap-Stop. Der reale Stop ist
  in History sichtbar; die begrenzte PostHog-Opt-in-/Opt-out-Abnahme ist abgeschlossen.
- Entwicklerkonto laut Nutzer verifiziert. Der spätere read-only Console-Check fand
  noch keine angelegte App; keine erneute Identitätsprüfung als Aufgabe erfinden.

## Befunde und verbleibende Grenzen

1. **Overlay-Korrektur im betroffenen Pfad bestätigt.** Der Nutzer hat
   auf `8dc7ba54` den direkten Wechsel zu Justnoise sowie erneute Chrome-Sperre und
   Freigabe nach Zap-Stop bestätigt. Der Abschluss erscheint in der echten History.
   Diesen bestandenen Ablauf nicht erneut öffnen. Siehe
   [A16-Update und Abnahme](a16-update-8dc7ba54-2026-09-20.md).
2. **Now Bar im finalen Eigentümertest belegt.** Eine ältere Sichtprüfung zeigte
   nur das promotete Lock-Screen-Element. Im späteren konsolidierten Lauf bestätigte
   der Eigentümer ausdrücklich laufenden Timer und Samsung Now Bar. Das neuere reale
   Ergebnis ist maßgeblich; kein API-Flag wird als Ersatz für diese Sichtprüfung
   verwendet.
3. **Kontolebenszyklus auf Eigentümerwunsch übersprungen.** Google-Login und zwei
   getrennte Backend-Konten sind belegt. A→B→A, Registrierung, Recovery und Löschung
   wurden wegen des fehlenden Passworts nicht ausgeführt. Die reale PostHog-Opt-in-/
   Opt-out-Empfangskontrolle ist dagegen abgeschlossen. Kein Besitzerkonto löschen,
   zurücksetzen oder für diesen Audit abmelden.
   Die sichtbare Supabase-Domain im Google-Browserfluss ist separat als bekannte
   Branding-Grenze dokumentiert; sie ist kein Hinweis auf ein fremdes Konto.
4. **Tatsächlicher Release enthält veralteten Informationstext.** Nicht nur ein
   Dokument: `aapt2 dump resources` des Kandidaten bestätigt im gebündelten Hinweis
   Aussagen wie „no app accounts ... analytics“ und „There is no server account to
   delete“. Das widerspricht der vorhandenen Account-/PostHog-Implementierung.
   Der Hinweis trägt weiterhin Draft-Kennzeichnung. Vor Verteilen korrigieren und
   mit der verantwortlichen Datenschutzperson abstimmen; nicht einfach verstecken.
   Die hier ausgeschlossene Datenschutz-Veröffentlichung wird nicht vorgenommen.
5. **Release-/Reviewer-Dokumente sind teilweise historisch.**
   `docs/ANDROID_PLAY_INTERNAL_UPLOAD.md` beschreibt unter anderem noch fehlende
   Accounts und einen fehlenden signierten Release. Nicht als aktuelle Checkliste
   oder Reviewer-Anleitung übernehmen. Login plus physischer Zap müssen in der
   tatsächlichen Prüf-Anleitung berücksichtigt werden.
6. **Visuelle Endabnahme unvollständig.** Nur drei echte iOS-Release-Referenzbilder
   sind vorhanden: Onboarding 1/2 und Aktivierung. Authentifizierte Hauptzustände
   fehlen als aktuelle iOS-Aufnahmen. Quellcode-Maße sind keine Bildabnahme.

## iOS-/Android-Vergleich

Aktuell erneut gelesen: iOS `Core/ContentView.swift`, `Core/AppFeatures.swift`,
`Features/Modes/ModesView.swift`, `Features/History/SessionHistoryView.swift`,
`Features/Schedule/SchedulesView.swift` sowie Androids echte Zap-/History-/Mode-
Oberflächen, Account-Einstieg und Consent-Anbindung. Die vorhandenen Source-
Baselines wurden gegen den neuen Umfang eingeordnet, nicht ungeprüft übernommen.
Ältere Baselines sagen noch „one selection/no modes“; das ist überholt.

| Bereich | Aktueller Abgleich | Für Abschluss erforderlich |
| --- | --- | --- |
| Onboarding/Aktivierung | Zwei Seiten plus Zap-Aktivierung als Vorlage; Android ergänzt eigene Zustimmung/Permissions. iOS-Aufnahme sagt „unblocked“, Android-Auswahl ist „to block“: nicht blind Text kopieren. | Aktuelle Android-Aufnahmen gegen vorhandene iOS-Referenzen prüfen. |
| Zap idle/active | Zentriertes Logo, Kalender links, History rechts, große Überschrift, Zap-Artwork, dunkler Idle-/heller Aktivzustand. Android übernimmt dieselbe grundlegende Slot-/Abstandslogik. | Beide Zustände, Timer, Moduskapsel, Unzap, Insets und große Schrift am finalen Build abnehmen. |
| Modi/App-Auswahl | iOS-WheelPicker, Android auswählbare Liste/Karten. Eigene Namen und App-Sets sind vorhanden. Native App-Auswahl ist plattformabhängig. | Unterschied bewusst freigeben oder Android gezielt visuell angleichen; Save/Cancel und aktive Auswahl prüfen. |
| Schedules | Kompakte Liste, Plus, Editor, Wochentage und Moduszuordnung vorhanden; native Picker unterscheiden sich. | Aktuelle Liste/Edit-Sheet vergleichen, automatischen Start mit korrektem Modus bestätigen. |
| History | Dunkler Summary-Kopf und heller Tagesraster; 90 Days/Year und Details vorhanden. Android heißt bewusst „Session time“ statt „Protected Attention“. Jahresansicht hat größere/scrollbare Touch-Ziele. | Bilder/Abstände/Tagdetail vergleichen; tatsächlichen Stop-Datensatz und Mitternachtsaufteilung bestätigen. |
| Konto/Settings | Android Pflichtkonto mit Google/E-Mail; Android-Permissions und Recovery bleiben nativ. Keine Übernahme ausgeschlossener iOS-Features. | Kontoflüsse live prüfen, Kopie/Fehlerzustände und echte Settings vergleichen. |
| Completion/Blocker | Echte Session-/Blockerzustände implementiert, Android-eigene Navigation/Fluchtwege notwendig. | Neueste reale Screens prüfen; keine Debug-/überholten Hilfstexte, Navigation erreichbar. |
| Live Activity/Now Bar | Timer, Modus, Artwork und Samsung Now Bar sind auf dem A16 im finalen Lauf sichtbar. Die Positionierung bleibt systemgesteuert. | Keine weitere App-Korrektur für den unveränderten Kandidaten. |

Sichtprüfung dieses Audits: iOS-Onboarding-Referenz vom 6. September, A16-Idle und
History vom 8. September. Die älteren Android-Aufnahmen enthalten inzwischen
entfernte Hilfstexte und sind deshalb keine Referenz für den finalen Kandidaten.
Es wurden keine aktuellen iOS-Hauptscreen-Aufnahmen erfunden und keine fehlenden
Zustände mit Vorschau-Daten ersetzt. **Keine belastbare Pixel-Paritätsquote.**

## Endliche Abschlussliste

1. **P0 — Bestandenen Endkandidaten einfrieren.** Keine erneute Installation,
   Paarung, Session oder PostHog-Runde ohne betroffene Codeänderung oder echten Fehler.
   APK/AAB und Prüfsummen bleiben die technische Übergabe.
2. **P0 — Konten als bewusst übersprungen dokumentieren.** Die fehlenden A→B→A-,
   Recovery-, Registrierungs- und Löschungsnachweise bleiben Restrisiko des internen
   Tests. Erneut öffnen erst, wenn ein geeignetes Testkonto verfügbar ist; Besitzerkonto
   weder abmelden, löschen noch zurücksetzen.
3. **P0 — PostHog nicht erneut testen.** Der begrenzte echte Opt-in-/Opt-out-Zyklus
   ist PASS. Nur der bewusst nicht ausgeführte Accountwechsel-/Prozessneustart-
   Sonderfall bleibt offen; keine künstlichen Produktiv-Events erzeugen.
   Der lokale Account-Rundlauf ist separat als PASS dokumentiert.
4. **P1 — Visuelle iOS-Abnahme.** Kurze Aufnahme der fehlenden iOS-Release-Zustände
   (Zap idle/active, Modi/Picker, Schedule, History/Tag/Year, Settings/Konto,
   Completion, Blocker), danach gleiche Android-Zustände am Kandidaten. Nur
   beobachtete Android-Abweichungen korrigieren. Kompakte Ansicht/große Schrift
   einschließen. Keine iOS-Codeänderung und kein vollständiges Redesign.
5. **P1 — Now-Bar-Abschluss: erledigt.** Der finale Eigentümertest bestätigte Timer
   und Samsung Now Bar auf dem A16. Nur nach einer betroffenen Runtime-Änderung oder
   einem neuen Fehler erneut öffnen.
6. **P0 — Widersprüchliche Release-Texte bereinigen.** In-App-Hinweise technisch
   richtigstellen; verantwortliche Person finalisiert Policy/URL und Reviewer-
   Zugang. Accessibility-Erklärung/Video müssen zum tatsächlichen Produkt passen.
   Datenschutz- und externe Veröffentlichung bleiben außerhalb der bisherigen
   Ausführungsfreigabe; dieser Punkt ist eine ausgewiesene Abhängigkeit.
7. **P1 — Reproduzierbare finale Übergabe.** Der Kandidat ist eingefroren,
   Prüfsummen liegen vor und VersionCode 1 wurde von Play verarbeitet. Schlüsselbackup
   bleibt zu bestätigen. Nur bei Runtime-Änderungen erneut bauen und
   die betroffenen Tests/Lint/Signatur wiederholen. Offene Arbeiten im stark veränderten
   Checkout nicht blind zusammen committen.
8. **P1 — Interner Play-Upload: erledigt.** Live Paket und Play App Signing wurden
   akzeptiert; der AAB ist ohne Validierungsfehler im aktiven internen Track. Danach
   Installation aus dem internen Track prüfen; diese
   Play-signierte Installation nicht mit dem jetzigen Sideload verwechseln und
   nicht die Eigentümerdaten für einen Signaturwechsel löschen.

## Abschlussdefinition / Reihenfolge

Die verbleibenden Blöcke sind: (A) visuelle Quellen und sachlich korrekte Release-
Texte, (B) bewusste Freigabe der übersprungenen Kontolebenszyklus-Abnahme,
(C) Play-App-Anlage, Console-Verarbeitung und interner Upload/Handoff. Eine weitere
Korrekturrunde ist nur bei einem echten Befund erforderlich.

Das Entwicklungsprojekt kann zum vereinbarten Meilenstein geschlossen werden,
wenn alle erforderlichen Ergebnisse belegt oder Abweichungen ausdrücklich
akzeptiert sind und ein eindeutig zugeordnetes, signiertes Paket übergeben ist.
Wenn der Upload selbst Teil des Abschlusses sein soll, gehören erfolgreiche
Console-Verarbeitung und eine Installation aus dem internen Track zusätzlich dazu.
Öffentliche Veröffentlichung/Google-Genehmigung ist ein eigener Meilenstein.

## Offizielle Abgrenzung — heute geprüft

### Read-only continuation: live prerequisites and Now Bar

Rechecked on September 20, approximately 07:04 Europe/Berlin. No app update,
session action, setting/permission change, account mutation or telemetry was sent.

- The A16 is currently reachable by USB (and a second wireless transport of the
  same device). Package metadata still reports the September 19 23:39:14 install,
  version 1.0.0 (1); the latest corrective candidate is not installed.
- Both `POST_NOTIFICATIONS` and `POST_PROMOTED_NOTIFICATIONS` are granted for
  Justnoise. Its notification 7101 is currently present with `PROMOTED_ONGOING`,
  `ONGOING_EVENT` and `SILENT`. Therefore asking the owner to grant the ordinary
  notification permission again is not the next diagnostic step. This read-only
  record does not prove a current bottom Now Bar rendering or session correctness.
- PostHog's existing US-region browser tab still displays the login form. There
  is no authenticated project/event evidence; live analytics acceptance is blocked
  on the owner signing in. No credentials or artificial events were supplied.
- The current public Android documentation explicitly permits `BigTextStyle`,
  a chronometer, a low-importance channel, ongoing state and a promotion request;
  it also explicitly allows additional OEM eligibility criteria. Source inspection
  confirms those requested characteristics in `SessionNotifications.kt`, including
  dismissal handling. It does not establish Samsung eligibility.
- Samsung's support guide requires the app to be supported and enabled in
  Settings > Lock screen and AOD > Now bar > Live notifications, with lock-screen
  notification display enabled. These Samsung-specific UI conditions have not
  been freshly verified. At the next owner-attended device pass, inspect them
  once, before proposing another template change; keep developer override off.
- The Samsung developer-forum link circulated by third-party libraries now
  redirects to a page visibly reporting "Post Not Found". It cannot be treated as
  current official confirmation of a public/private API or a partnership rule.

Decision: no speculative `ProgressStyle`, fake progress, private Samsung metadata,
new permissions or ordinary fallback are introduced. The exact Now Bar criterion
remains open. The next actionable evidence is the normal Samsung support/settings
surface and actual locked-screen rendering, not another successful build.

Sources: [Android Live Updates](https://developer.android.com/develop/ui/views/notifications/live-update)
(updated September 16, 2026), [Samsung Now Bar support](https://www.samsung.com/sg/support/mobile-devices/how-to-use-the-now-bar-on-the-lock-screen-of-your-samsung-galaxy-device/)
(updated April 13, 2026).

### Evening device follow-up — normal Samsung settings inspected

The earlier request to inspect normal Samsung settings has now been carried out
on candidate 8dc7ba54 without changing settings. Justnoise is absent from the
complete Now Bar > Live notifications > Show all list. Its own notification page
already allows notifications, lock-screen display and Always show content. The
earlier timer/artwork card and promoted framework flag are real, but do not close
the requested bottom Now Bar criterion. Further speculative template rebuilds are
not justified by a missing notification permission. See
`a16-update-8dc7ba54-2026-09-20.md` for screenshots and bounded conclusions.

Live PostHog start/end receipt is now proven after owner opt-in; account/profile
payload boundaries were inspected, and actual server IP/GeoIP enrichment was found.
After owner opt-out a real session was observed over wireless ADB with validated
Wi-Fi Internet, while a completed PostHog query still showed only the old pair.
The subsequent physical stop and final completed query closed the bounded end-side
opt-out check with no new event. Only live account-transition/process-restart
acceptance remains open. These later facts supersede the morning login/install
blockers without treating the complete connected goal as achieved.

### Play Console read-only check — evening follow-up

The authenticated organization account **JustNoise** is reachable. Its app list
currently says **Create your first app** and contains no applications. Therefore
The later create-app form reported `com.justnoise.app` as already in use and
`shop.justnoise.app` as available. Version code 1 is not yet registered or accepted
by Play Console. The new signed AAB remains locally valid, but console-side
package/version/signing acceptance cannot be claimed before an app is created and
the bundle is actually processed.

This historical read-only checkpoint was superseded later the same day: the owner
accepted the declarations, app/package/version were registered, and the internal
tester track was activated. Public production rollout remains separate work.

- [Google: Internal testing](https://support.google.com/googleplay/android-developer/answer/9845334):
  interner Test kann vor vollständig abgeschlossener App-Einrichtung starten;
  deswegen nicht jede Public-Launch-Aufgabe zum ersten Upload-Blocker machen.
- [Google: Accessibility](https://support.google.com/googleplay/android-developer/answer/10964491):
  Erklärung und Demonstrationsvideo müssen Zugriff, Zustimmung/Ablehnung und
  Kernfunktion nachvollziehbar zeigen; keine automatische Freigabe zusagen.
- [Google: Data safety](https://support.google.com/googleplay/android-developer/answer/10787469):
  ausschließlich interne Tracks sind von diesem Formular ausgenommen. Das
  rechtfertigt keine sachlich falschen Informationen in der App.
