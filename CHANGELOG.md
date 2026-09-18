# Changelog

All notable changes to TrainKraft are documented here.

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
