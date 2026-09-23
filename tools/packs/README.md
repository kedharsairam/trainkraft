# TrainKraft pack pipeline (Phase A — Python side)

Builds `pack.db`, the offline data pack the Android app imports at first run
(app `PackImporter` reads `pack.db`: `delay_priors` for typical-delay display,
`fog_overlays` for seasonal cancellation/frequency warnings, `pack_meta` for
version/staleness checks). The Android Room side implements the same table
contract; the DDL in `build_pack.py` is byte-identical to that contract — do
not "improve" it.

Pipeline stages (all stdlib-only, no pip installs):

```
GTFS asset + NTES GetAvgDelayJson          hand-compiled fog JSON
        |                                            |
sweep_avg_delay.py                   fog_compile.py (validate+compile)
        |                                            |
staging_avg_delay.db                     fog_overlays.db
        |                                            |
        +------->  build_pack.py  -> pack.db + pack.sha256  -> app
              progress.json (resume state, rerunnable)
```

## 0. Prerequisites

* Python 3 (any 3.8+; developed on 3.13), no third-party packages.
* Repo checkout with `app/src/main/assets/trains.db` (GTFS snapshot,
  10,594 distinct train numbers) and repo-root `ntes-keys.json`
  (`key`/`iv` 16 ASCII chars, `sckey`, `endpoint`).
* Run everything from this directory (`tools/packs/`).

## 1. Test sweep (a few live calls — the only sweep an agent ever runs)

```bash
python3 sweep_avg_delay.py --trains 12952,12787 --throttle 2
```

Fetches average-delay history for 2 trains, writes `staging_avg_delay.db`
(table `staging_priors`) + `progress.json`. Expect `12952 ok rows=8`,
`12787 ok rows=22`. Also try `--limit 5` for the first 5 GTFS trains.

## 2. Full sweep (operator's machine, off-peak IST only)

```bash
python3 sweep_avg_delay.py
```

* POLITENESS CONTRACT: 2.0 s base delay + up to 0.5 s jitter between calls;
  HTTP 429/5xx AND transport failures (reset/timeout/DNS/SSL) → sleep 60 s,
  retry ≤ 3, then record in `progress.json.failed` and move on. 8 consecutive
  transport-side failures anywhere → 5-minute cooldown. Fatal content
  (AlertMsg incl. "No Avg. Delay Record", bad shape, decrypt failure) is
  never retried. Raised from 1.2 s on 2026-09-23 after the server answered
  a burst with connection resets. Run off-peak IST (~00:00–05:00);
  the script enforces nothing about wall-clock time — the operator picks
  the window.
* Wall time ≈ 8 h for ~10.5k trains (~2.7 s/call incl. latency); the
  script prints a live ETA from the remaining count.
* Resumable: reruns skip `progress.json.done` and retry `failed`
  (`--no-resume` to start over). Interrupt-safe — progress is saved per
  train and staging upserts are `INSERT OR REPLACE` on the PK.
* Flags: `--limit N`, `--throttle SEC`, `--trains A,B,C`, `--keys`,
  `--gtfs`, `--staging`, `--progress`.

## 3. Fog compile

```bash
python3 fog_compile.py
```

Validates `data/fog_2025_26.json` and compiles valid entries into
`fog_overlays.db` (table `fog_overlays`, season `2025-26`). Invalid entries
→ loud reject list, nonzero exit. The script never scrapes: it is a
validator + compiler over hand-compiled, per-entry-sourced JSON.

2025-26 dataset: 40 shipped rows (24 `CANCELLED` + 16 `REDUCED_FREQ`) from 3
publications — ECR 24-pair program (ixigo, Nov 2025), NR Lucknow 8-cancelled
+ 14-reduced program (HT, Dec 2025), NFR 14-train program (Indian Express,
Oct 2025). Season status is `SUPERSEDED` (program ended Feb/Mar 2026).

Sourced but NOT shipped (fail GTFS validation — absent from this GTFS
vintage, rejected loudly by design): 12327/12328 Howrah–Dehradun Upasana
Exp and 12523/12524 New Jalpaiguri–New Delhi Exp. If a newer GTFS vintage
carries them, re-add with the same sources and rerun.

## 4. Build pack

```bash
python3 build_pack.py
python3 build_pack.py --staging staging_avg_delay.db \
    --fog-db fog_overlays.db --out pack.db
```

Writes contract tables + `pack_meta` (`packVersion=1`, `generatedAt`,
`trainCount`, `gtfsVintage="2026-08"`, `fogSeason`, `source=
"ntes-avdelay-sweep"`) and `pack.sha256` (hex SHA-256 of `pack.db`).
Refuses to pack unless both data tables have > 0 rows. Verify anytime:

```bash
sha256sum -c pack.sha256
```

## 5. Tests

```bash
python3 -m unittest
```

18 offline tests: AES-128 FIPS-197 vector + CBC/NTES-layer roundtrips,
delay-string mapping (incl. exact `avg_delay_12952.json` fixture values),
fog validator accept/reject matrix, manifest hash roundtrip, resume-skip
logic, staging PK upsert. No network.

## Fog refresh (Nov 2026: Dec 2026–Feb 2027 program)

The Dec 2026–Feb 2027 fog program publishes ~Nov 2026. Refresh is a
data-only update, no code changes:

1. Watch zone press releases (NR/NFR/NER/ECR portals + press coverage).
2. Append/replace entries in `data/fog_2025_26.json` (or add
   `data/fog_2026_27.json` and point `--json` at it) — one row per
   trainNumber, every entry with a `source` URL, dates YYYY-MM-DD.
3. `python3 fog_compile.py [--json data/fog_2026_27.json]` → exit 0.
4. `python3 build_pack.py` → new `pack.db` + `pack.sha256`, bump
   `packVersion` handling on the app side if the importer requires it.

## Files

* `ntes_crypto.py` — pure-stdlib AES-128-CBC + NTES payload layer (port of
  `app/.../data/NtesCrypto.kt`; ciphertext verified byte-identical to
  OpenSSL for the repo keys).
* `sweep_avg_delay.py` — NTES sweep → staging.
* `fog_compile.py` + `data/fog_2025_26.json` — fog validator + compiler.
* `build_pack.py` — `pack.db` + `pack.sha256` assembler.
* `test_pack_tools.py` — offline unittest suite.
