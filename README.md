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
| Testing | JUnit4 + Robolectric (311 unit tests) |

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

## Permissions

Nine are declared. Three are only requested when you turn on live tracking; the
rest have to exist before that is possible.

| Permission | Asked by | Why |
| --- | --- | --- |
| `INTERNET` | live status, PNR | The only way to reach NTES and Indian Railways |
| `ACCESS_NETWORK_STATE` | every network call | Tells a live answer from a cached one, so the app can say which you are looking at |
| `POST_NOTIFICATIONS` | delay alerts | Nothing is posted without it, and it stays off until you opt in |
| `ACCESS_FINE_LOCATION` | travel mode | A foreground service you start when you board, so your real arrival is noticed rather than assumed from the timetable |
| `ACCESS_COARSE_LOCATION` | travel mode | The coarse grant the platform pairs with the fine one |
| `FOREGROUND_SERVICE_LOCATION` | travel mode | Keeps that check running with the screen off |
| `FOREGROUND_SERVICE_DATA_SYNC` | delay polling | The worker watching for a delay getting worse |
| `SCHEDULE_EXACT_ALARM` | station alarms | An alarm set for 04:12 that fires at 04:12 |
| `FOREGROUND_SERVICE` | both services | The base grant either service type needs |

Background location is deliberately not requested. Travel mode runs between two
taps of yours and does not follow you home.

## Privacy

No accounts. No analytics. No trackers. The timetable lives on the device, and
outbound queries carry a train number and nothing else.

## Support

If you enjoy TrainKraft, buy me a coffee:

<p align="center">
  <a href="https://buymeacoffee.com/kedhartech"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" width="182"></a>
</p>

## License

[MIT](LICENSE)
