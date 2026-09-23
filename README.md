# TrainKraft

Private train tracking for India. No ads. No analytics. No trackers.

<p align="center">
  <a href="https://github.com/kedharsairam/trainkraft/releases/latest"><img src="https://img.shields.io/github/v/release/kedharsairam/trainkraft?style=for-the-badge&label=Download" alt="Download APK"></a>
  <img src="https://img.shields.io/badge/License-MIT-green?style=for-the-badge" alt="MIT License">
</p>

---

## Features

**Offline timetable** — 10,500+ trains, 8,500+ stations bundled. Search trains, stations, schedules, routes. Works without internet.

**Live status** — Real-time train running status via NTES API. Delays, ETAs, last location, platform info. Graceful fallback to cached + scheduled times when offline.

**PNR status** — Check PNR status directly in the app. Solves captcha, shows per-passenger status, chart status, train info.

**Between stations** — Find all trains running between two stations. Swap button, autocomplete search, duration display.

**Station board** — Live departures from any station with platform info.

**Coach position** — See where your coach will be on the platform before the train arrives.

**Delay tracker** — Color-coded delay severity (green ≤5min, orange ≤15min, red >15min). Average delay data from NTES.

**Live notifications** — Opt-in per-train notifications via WorkManager. Polls NTES every 10 minutes and alerts only on meaningful changes: delay worsening, cancellation, or journey completed.

**Share** — Share train info (name, number, route, stops) as clean text.

**24h format** — Toggle between 12h and 24h time display in Settings.

**Private** — No accounts. No tracking. Timetable on-device. API queries contain only train numbers.

---

## Tech

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Database | Room (GTFS timetable, 19.7MB) |
| Network | OkHttp + NTES AppServAnd API |
| Crypto | AES-128-CBC (NTES payload) |
| Background | WorkManager (live notifications) |
| Testing | JUnit4 + Robolectric (50 tests) |

---

## Data

- Timetable: OpenStreetMap GTFS (Aug 2026), 10.5k trains
- Live: Indian Railways NTES (unofficial API port)
- PNR: indianrail.gov.in (official, captcha-based)
- No official IRCTC partnership. Not affiliated with Indian Railways.

---

## Build

```bash
# Generate timetable DB from GTFS (one-time)
uv run --python 3.11 python tools/gtfs_to_sqlite.py

./gradlew assembleDebug
```

Requires JDK 21+, Android SDK 37.

---

## Privacy

No permissions beyond internet + notifications. No analytics. No tracking. See source.

## License

[MIT](LICENSE)
