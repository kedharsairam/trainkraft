# Changelog

All notable changes to TrainKraft are documented here.

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
