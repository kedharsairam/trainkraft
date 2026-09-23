# Changelog

All notable changes to TrainKraft are documented here.

## [0.5.0] - 2026-09-24

### Added
- Answer-first live tracking detail: status headline, server-timestamped freshness badge (`Live · HH:MM` / `Cached · Xm ago`) with manual refresh, journey-progress bar, 4-day instance strip, three-zone timeline (actuals / Now card / Expected with dynamic `PF*`), collapsible non-stop disclosures, reversal dividers, per-stop km rail and coach sheets, red cancellations banner
- Between-stations date carousel with per-day train counts, dynamic type filters, departure/duration/arrival sort, and cards with duration, day-dots, class chips, Schedule/Track actions
- Home hub: hero search card, quick-action grid (PNR promoted from Settings), tracked-trains overview, read-only alerts-status row
- Foreground tracking service (60s loop, 30s near-arrival boost, worker-dedup invariant) with Go-live two-tier UX and first-run onboarding sheet
- Station/destination/per-stop alarms (exact with inexact fallback, one-shot gates) and notification-tap deep links
- On-device GPS travel mode (foreground-only, advisory arrival, revoke-safe) with glanceable speed/distance screen
- `liveTracking` presence flag: stopping Go-live never unfollows (bell owns rows, flag owns minute presence; cold-start reset)
- Fog-program notices from published overlays; pack provenance caption

### Fixed
- Room asset wipe on every open (scoped destructive migration), ShimmerList infinite-measure crash (kraft-ui)
- v3 user-data rescue moved out of the Room migration transaction (WAL-mode throw ate followed trains); drops-only migration + pre-open migrator
- Per-stop alarm rows remember persisted watches across recreation, suppress fired one-shots

### Changed
- Two-database privacy split: `user.db` (tracked trains, cache, traces — backup-excluded) vs `trains.db` (static timetable, re-seeds from asset)
- Worker interval corrected to the 15-minute OS floor; NTES key cache invalidates on decrypt failure
- Product law: the app shows what is, what was, and what's published — it computes nothing about the future (no predictions, no priors display, no forecasts)

## [0.4.0] - 2026-09-23

### Added
- Typed NTES data layer: strict kotlinx-serialization DTOs for all seven endpoints, built from captured production fixtures (`app/src/test/resources/fixtures/`) and schema-validated in tests
- `NtesRepository` pipeline: network-first with strict parse → response-cache fallback → explicit failure; screens label the source honestly (Live / Cached / Offline badges on station board and between-stations)
- Manual DI container (`AppContainer`, fully lazy — nothing touches the DB on app start)
- Official-schedule fallback (NTES GetTrainSchedule) for trains missing from the GTFS snapshot, labeled in the UI
- Station board fetches live NTES departures (platform, delay, cancellations) merged over the offline full-day GTFS timetable
- Between-stations queries live NTES first (regional board/alight resolution), GTFS timetable as labeled fallback
- `NotificationPolicy`: notify only on delay-category worsening (≤5 / 6–15 / >15 min), a ≥15-min shift inside SEVERE, cancellation, or completion — the first poll sets a silent baseline; station movement never notifies
- Persisted tracking rows (`tracked_trains`, DB v3): bell state survives process death; journey completion deletes the row and stops the worker
- CI runs the unit-test suite on every push/PR (plus lint)
- Live-train detail is answer-first: headline status, server-timestamped freshness badge (`Live · HH:MM` / `Cached · Xm ago`) with manual refresh, journey-progress bar (`covered / total km · %`), 4-day instance strip (completed / running / yet-to-start) replacing the calendar picker, and a three-zone timeline — past stops show scheduled vs actual + delay chips, the current stop is one elevated card, future stops show `Exp.` predictions with dynamic `PF*` platforms
- Non-stop stations collapse into expandable `▸ N non-stop stations` disclosure rows (from NTES `WTTSTNS`), direction reversals get a divider row, and every stop carries its km marker plus a per-stop coach-position bottom sheet
- Cancellations/diversions surface as a red exceptions banner (`TrainExcpInfo`, newly wired through the repository)
- Between-stations gains a 7-day date carousel with per-day train counts (derived from `DayOfRun`), dynamic type filters, departure/duration/arrival sort, and cards with duration, day-dots, class chips, and Schedule/Track actions
- Home is an information hub: hero search card, quick-action grid (trains-between, PNR, station board, tracked trains), a tracked-trains overview backed by the persisted bells, and a read-only alerts-status row — PNR promoted out of Settings
- Shared freshness primitives (`FreshnessBadge`, `DelayChip`, `StatusPill`, tabular figures) keep Live/Cached/Failed labeling identical on every screen
- DTO contract extended from captured production fixtures (incl. live 12787 pull): per-stop `DARR`/`DDEP` delays, `WTTSTNS`, `reversalNumber`, ETA/ETD-unavailable flags, `LDSRC` journey progress, `TrainExcpDto` — test suite grew 50 → 115

### Fixed
- Notifications fired on every 10-minute poll (including pure station movement) — now only meaningful changes
- Live-status platform lookup scanned for a top-level key that never exists (could never show a platform) — platform now comes from the next unreached stop
- Average-delay failures vanished silently — now show a one-line "unavailable" note
- GTFS import script was Windows-only (hard-coded paths) — now path-relative defaults
- `backup_rules.xml` referenced removed files; version bumped to 0.4.0 (versionCode 8)
- Room `createFromAsset` + unconditional `fallbackToDestructiveMigration` re-copied the asset DB on every open, wiping `tracked_trains`/`cached_responses` — now scoped to `fallbackToDestructiveMigrationFrom(1)` (smoke-found, verified: tracked rows survive force-stop)
- Unconstrained `ShimmerList` `LazyColumn` crashed with an infinite-height constraint inside the between-stations screen (kraft-ui; smoke-found, verified on device)

### Changed
- ViewModels moved off raw `JSONObject` string parsing to typed `LoadResult<T>` state (live status, average delay, boards)
- Test suite grew 27 → 115 (fixture schema tests, notification-policy decision tests, PNR fakes, date-carousel/day-run logic, detail headline/zone/progress/label logic — tests never hit production endpoints)

## [0.3.0] - 2026-09-19

### Changed
- AGP 8.9.1 → 9.3.1 (built-in Kotlin, no standalone kotlin-android plugin)
- Gradle 8.11.1 → 9.7.1
- Kotlin 2.1.0 → 2.2.10 (bundled with AGP 9.3)
- compileSdk / targetSdk 36 → 37
- KSP 2.1.0-1.0.29 → 2.2.10-2.0.2
- OkHttp 4.12.0 → 5.5.0 (migrated to kotlin.time.Duration API)
- kotlinx-coroutines-test 1.10.0 → 1.11.0
- Robolectric 4.14.1 → 4.17

### Fixed
- Removed standalone kotlin-android plugin (AGP 9.x bundles Kotlin natively)
- Migrated kotlinOptions DSL to kotlin { compilerOptions { } }
- OkHttp timeout API: TimeUnit → kotlin.time.Duration
- .gradle/ cache untracked from git (was in repo despite .gitignore)
- NTES remote key URL pointed to 'main' but repo uses 'master' (404, keys never loaded)
- Empty key crash: validate keys before encryption, show user-friendly error
- Pre-fetch NTES keys in Application.onCreate for faster first-use

## [0.2.0] - 2026-09-18

### Added
- PNR status checking (3-step flow: input → captcha → parsed result)
- Between stations feature (find all trains between two stations)
- Coach position display (arrival/departure positions from NTES)
- Live delay tracking with color-coded severity (green/orange/red)
- Average delay data from NTES GetAvgDelayJson endpoint
- Live status notifications via WorkManager (10-minute polling, per-train toggle)
- Share train info as clean text
- 24-hour time format toggle in Settings
- Platform info in live status
- Date picker for live status queries
- Sealed UiState classes for all ViewModels
- 27 unit tests (GtfsTime, NtesCrypto, PnrViewModel validation)
- Network security config (cleartext blocking for Indian Rail endpoints)
- ProGuard rules (Room, OkHttp, Compose keep rules)

### Fixed
- NTES keys removed from source code, scrubbed from git history
- PNR URL encoding (HttpUrl.Builder instead of string interpolation)
- Double DB query in TrainDetailViewModel
- Dead mapDatabaseError branches in SearchViewModel
- Deprecated fallbackToDestructiveMigration (updated to dropAllTables)
- Deprecated largeTopAppBarColors (replaced with topAppBarColors)
- FQN references cleaned up across all Composables and ViewModels
- PNR validation hardened (10-digit check in ViewModel, not just UI)
- backup_rules.xml updated to exclude ntes-keys-cache.json

### Changed
- TrainDetailScreen.kt split into TrainDetailScreen + TrainDetailComponents
- Shared composables extracted (SectionHeader, DayBadge, TypeBadge) → SharedComponents.kt
- ViewModelFactory boilerplate consolidated
- Dead code removed (ThemeColors, KraftTheme alias)
- rootProject.name fixed (Kalc → TrainKraft)
- Unused dependencies removed (kotlinx-serialization-json, security-crypto, glance-appwidget)

## [0.1.0] - 2026-09-17

### Added
- Offline timetable (10.5k trains, 8.5k stations from GTFS)
- Live train status via NTES API
- Station board with departures
- Train search (stations + trains)
- Dark-only UI with Apple-inspired design
- Room database with GTFS data
- AES-128-CBC encryption for NTES API
- Remote key configuration (ntes-keys.json on GitHub)
