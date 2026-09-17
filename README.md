# TrainKraft

Private train tracking for India. No ads. No analytics. No trackers.

<p align="center">
  <a href="https://github.com/kedharsairam/trainkraft/releases/latest"><img src="https://img.shields.io/github/v/release/kedharsairam/trainkraft?style=for-the-badge&label=Download" alt="Download APK"></a>
  <img src="https://img.shields.io/badge/License-MIT-green?style=for-the-badge" alt="MIT License">
</p>

---

## Features

**Offline timetable** — 10,500+ trains, 8,500+ stations bundled. Search trains, stations, schedules, routes. Works without internet.

**Live status** — Real-time train running status via NTES API. Delays, ETAs, last location. Graceful fallback to cached + scheduled times when offline.

**Station board** — Live departures from any station with platform info.

**Destination watch** — Save trips, get ready for geofence alarms (coming soon).

**Private** — No accounts. No tracking. Timetable on-device. API queries contain only train numbers.

---

## Tech

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Database | Room (GTFS timetable, 13MB) |
| Network | OkHttp + NTES AppServAnd API |
| Crypto | AES-128-CBC (NTES payload) |

---

## Data

- Timetable: OpenStreetMap GTFS (Aug 2026), 10.5k trains
- Live: Indian Railways NTES (unofficial API port)
- No official IRCTC partnership. Not affiliated with Indian Railways.

---

## Build

```bash
# Generate timetable DB from GTFS (one-time)
uv run --python 3.11 python tools/gtfs_to_sqlite.py

./gradlew assembleDebug
```

Requires JDK 21+, Android SDK 36.

---

## Privacy

No permissions beyond internet + location (for future alarms). No analytics. See source.

## License

[MIT](LICENSE)
