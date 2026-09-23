# GTFS-Source Spike — Repeatable Fresh GTFS for Indian Railways

Timebox: 30 minutes (2026-09-23). Question: is there a repeatable,
fresh GTFS-static source for Indian Railways that the `tools/packs`
pipeline can pull on a schedule, instead of inheriting the bundled
snapshot's staleness?

## What the app ships today

Bundled `app/src/main/assets/trains.db` (queried 2026-09-23):
`calendar` range 20250101–20270830, 10,594 trains / 8,550 stations /
207,224 stop-times. Service-day-accurate only while the true timetable
stays close to this snapshot; new trains, renumberings, and calendar
extensions past Aug-2027 are invisible to it.

## Candidates checked

### 1. Mobility Database mdb-2867 — "Indian Railways, Unofficial data GTFS Schedule Feed"
- URL: https://mobilitydatabase.org/feeds/gtfs/mdb-2867
- Upstream generator: https://github.com/Neo2308/indianrailways-gtfs
  (Go toolchain; listed consumers: Transitous, MobilityDatabase,
  CatenaryMaps, TransitRouter).
- Vintage: upstream README status report says **"Data generated: Around
  9th Nov 2025"** — ~10 months stale at spike time. Feed page itself is
  JS-rendered; vintage/license/source-URL fields could not be read
  without a browser session (checked via fetch, 2026-09-23).
- License: **unconfirmed** (could not read the feed page fields; upstream
  repo shows no LICENSE file at a glance).
- Refresh mechanics: upstream regenerates by re-running its Go fetcher
  against a keyed API (`X_API_KEY`, source API undocumented in the 30-min
  window) — i.e. freshness depends on that maintainer re-running it, not
  on a live upstream GTFS URL. The MobilityDB copy therefore refreshes
  only when someone re-submits.
- Verdict: **not adoptable as a scheduled pull today** — stale snapshot
  behind a catalog, same staleness class as what we ship, plus unclear
  license for bundling.

### 2. github.com/vickyg3/IndiaTrainsGTFS
- Claims GTFS data, "updated from time to time".
- Actual: 1 commit, README-only (0 stars); local `--depth 1` clone in
  `app-references/train-status/IndiaTrainsGTFS/` is likewise README-only
  (see that dir's NOTES.md). No feed files on any visible branch.
- License: none (no LICENSE file).
- Verdict: **not a source — stub repo.**

### 3. data.gov.in — "Indian Railways Train Time Table" catalog
- URL: https://www.data.gov.in/catalog/indian-railways-train-time-table
- What: OGD Platform India catalog entry (Government Open Data License –
  India). Format is timetable tables/CSVs, **not GTFS**; would need a
  converter written and maintained against its update cadence (unknown —
  not verified in the window).
- Verdict: **possible future converter target, not a GTFS source.**

### 4. Transitland / OpenMobilityData
- No official Indian Railways feed found in the window; the only IR
  presence in these ecosystems is the unofficial dump above (via
  Catenary's atlas PR). Indian Railways publishes no official GTFS.
- Verdict: **no adoptable feed.**

## Verdict (honest, in timebox)

**Inconclusive-to-negative: no repeatable fresh-GTFS source found in
30 minutes.** The closest candidate (mdb-2867) is itself a ~Nov-2025
community scrape of unclear license with no live refresh loop.

## Consequence for packs

Packs inherit the Aug-2026 snapshot staleness. Shrink the blast radius:
1. `pack_meta.gtfsVintage` MUST record the snapshot vintage every pack
   carries (pipeline stamps it; Phase B surfaces it).
2. Manual refresh path: `tools/gtfs_to_sqlite.py` stays the documented
   way to rebuild `trains.db` from a newer GTFS zip; Phase B must treat
   delay priors as corrections *on top of* whatever snapshot is bundled,
   never as a timetable replacement.
3. Revisit when: mdb-2867 shows a 2026 vintage, an official IR GTFS
   appears, or the data.gov.in converter gets built (tracked follow-up,
   not Phase A scope).

## URLs checked (2026-09-23)
- https://mobilitydatabase.org/feeds/gtfs/mdb-2867
- https://github.com/Neo2308/indianrailways-gtfs (+ README status report)
- https://github.com/vickyg3/IndiaTrainsGTFS
- https://www.data.gov.in/catalog/indian-railways-train-time-table
- https://gtfs.org/resources/sharing-data/ (directory, no IR feed)
- Raw catalog fetches on `MobilityData/mobility-database-catalogs`
  (both tried paths 404 — catalog layout has moved since the docs indexed it)
