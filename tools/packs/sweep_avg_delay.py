#!/usr/bin/env python3
"""NTES GetAvgDelayJson sweep -> staging sqlite (`staging_avg_delay.db`).

Reads every train_number from the GTFS asset (`app/src/main/assets/trains.db`,
table `trains`, opened read-only), POSTs the encrypted GetAvgDelayJson request
for each train, maps per-station delay strings to minutes, and upserts rows
into staging table `staging_priors`.

POLITENESS CONTRACT (non-negotiable — this hits a public railway endpoint):
  * 2.0 s base delay + up to 0.5 s jitter between calls (`--throttle` tunes
    the base; jitter always applies). Raised from 1.2 s on 2026-09-23 after
    the server answered a request burst with connection resets (Errno 104).
  * HTTP 429 / 5xx -> sleep 60 s, retry the same train up to 3 times, then
    record it in `progress.json.failed` and move on (never abort the sweep).
  * Transport failures (reset/timeout/DNS/SSL) get the SAME retry treatment
    as 429/5xx (they are the server pushing back, not train-specific errors).
    8 consecutive transport/429/5xx failures anywhere -> 5-minute cooldown,
    then continue. Any successful response resets the streak.
  * Fatal content (AlertMsg incl. "No Avg. Delay Record", bad shape, decrypt
    failure) is NOT retried — the server answered, the train just has no data.
  * Run the FULL sweep off-peak IST (roughly 00:00-05:00 IST) to minimise
    load on the public endpoint. This script enforces nothing about wall-clock
    time — the operator picks the window. Stated here so the rule survives.
  * Expected wall time for ~10.5k trains at default throttle ~= 8 h
    (10.5k x ~2.7 s avg incl. request latency). The script prints a live ETA
    from the remaining count.

Resumability: `progress.json` holds `{"done": [...], "failed": [...]}`.
Reruns skip `done` trains and retry `failed` ones (`--resume`, on by default).

Crypto: pure-stdlib port of `app/.../data/NtesCrypto.kt`
(see `ntes_crypto.py`; FIPS-197 test vector in `test_pack_tools.py`).
Request shape mirrors `app/.../data/NtesApi.kt`:
  POST {endpoint}  body {"jsonIn": "<enc>"}  UA "Dalvik/2.1.0 (Linux; Android 11)"
  payload "service=TrainRunningMob&subService=GetAvgDelayJson&trainNo=<N>"
Response keys verified against
`app/src/test/resources/fixtures/avg_delay_12952.json`:
  top-level `vAvgDelayList[]` with `stn`, `stnArrDelay`, `stnDepDelay`.
Missing/empty lists -> train recorded done with 0 rows (noted on stdout).

Delay mapping (mirrors `NtesFormats.delayToMinutes`, storage per contract):
  "On Time" / "RT" (any case) -> 0, "HH:MM" -> h*60+m, blank -> 0,
  unparseable -> 0 (NOT NULL column; counted and logged, not fatal).

NEVER run the full sweep from an agent session — validate with:
    python3 sweep_avg_delay.py --trains 12952,12787 --throttle 2
The full sweep runs on the operator's machine, off-peak IST:
    python3 sweep_avg_delay.py

Stdlib only: argparse, base64? no — hashlib/json via ntes_crypto,
sqlite3, time, random, urllib(.request/.error), sys/os.
"""

import argparse
import json
import os
import random
import sqlite3
import sys
import time
import urllib.error
import urllib.request

from ntes_crypto import decrypt_response, encrypt_payload, load_keys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(os.path.dirname(HERE))  # tools/packs -> repo root
DEFAULT_KEYS = os.path.join(REPO_ROOT, "ntes-keys.json")
DEFAULT_GTFS = os.path.join(
    REPO_ROOT, "app", "src", "main", "assets", "trains.db"
)
USER_AGENT = "Dalvik/2.1.0 (Linux; Android 11)"
DEFAULT_ENDPOINT = "https://enquiry.indianrail.gov.in/crisns/AppServAnd"

RETRY_SLEEP_S = 60
MAX_RETRIES = 3

# Circuit breaker for server pushback (resets/timeouts/429/5xx clustering):
# N consecutive transport-side failures anywhere -> COOLDOWN_S pause.
# Any successful server response resets the streak.
CONSECUTIVE_COOLDOWN_N = 8
COOLDOWN_S = 300

STAGING_DDL = """
CREATE TABLE IF NOT EXISTS staging_priors(
  trainNumber TEXT NOT NULL, stationCode TEXT NOT NULL,
  arrAvgMin INTEGER NOT NULL, depAvgMin INTEGER NOT NULL,
  fetchedAt INTEGER NOT NULL,
  PRIMARY KEY(trainNumber, stationCode))
""".strip()


# --------------------------------------------------------------------------
# pure helpers (unit-tested offline in test_pack_tools.py)
# --------------------------------------------------------------------------

def delay_to_minutes(raw):
    """Map an NTES delay string to minutes (blank/RT/On-Time -> 0)."""
    v = (raw or "").strip()
    if not v:
        return 0
    if v.upper() == "RT" or "on time" in v.lower():
        return 0
    hh, sep, mm = v.partition(":")
    if sep and hh.isdigit() and len(mm) == 2 and mm.isdigit():
        h, m = int(hh), int(mm)
        if h < 24 and m < 60:
            return h * 60 + m
    return 0


def select_pending(all_trains, progress, resume=True):
    """Trains left to fetch: skip done when resuming; always retry failed."""
    if not resume:
        return list(all_trains)
    done = set(progress.get("done", []))
    return [t for t in all_trains if t not in done]


def load_progress(path):
    if os.path.exists(path):
        with open(path, "r", encoding="utf-8") as f:
            obj = json.load(f)
        obj.setdefault("done", [])
        obj.setdefault("failed", [])
        return obj
    return {"done": [], "failed": []}


def save_progress(path, progress):
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(progress, f)
    os.replace(tmp, path)


def load_train_list(gtfs_path, explicit=None, limit=0):
    """All train_numbers from the GTFS asset in numeric order (read-only)."""
    if explicit:
        return [t.strip() for t in explicit.split(",") if t.strip()]
    uri = "file:%s?mode=ro" % os.path.abspath(gtfs_path)
    con = sqlite3.connect(uri, uri=True)
    try:
        rows = con.execute(
            "SELECT DISTINCT train_number FROM trains "
            "ORDER BY CAST(train_number AS INTEGER)"
        ).fetchall()
    finally:
        con.close()
    trains = [r[0] for r in rows]
    if limit and limit > 0:
        trains = trains[:limit]
    return trains


def parse_avg_delay(decoded):
    """Extract [(station, arrMin, depMin)] from a decrypted GetAvgDelayJson.

    Returns (rows, note). Tolerates missing/empty `vAvgDelayList`.
    Raises ValueError on server-side AlertMsg or unparseable JSON.
    """
    try:
        obj = json.loads(decoded)
    except json.JSONDecodeError as e:
        raise ValueError("unparseable JSON response: %s" % e)
    if not isinstance(obj, dict):
        raise ValueError("unexpected response shape (not an object)")
    alert = (obj.get("AlertMsg") or "").strip()
    if alert:
        raise ValueError("server AlertMsg: %s" % alert[:200])
    items = obj.get("vAvgDelayList")
    if not items:
        return [], "empty vAvgDelayList"
    rows = []
    for it in items:
        stn = (it.get("stn") or "").strip()
        if not stn:
            continue
        rows.append(
            (stn,
             delay_to_minutes(it.get("stnArrDelay")),
             delay_to_minutes(it.get("stnDepDelay")))
        )
    return rows, ""


# --------------------------------------------------------------------------
# network
# --------------------------------------------------------------------------

def fetch_train(train_no, opener, endpoint, key16, iv16, sckey, timeout=30):
    """One encrypted GetAvgDelayJson call -> decrypted response string.

    Returns (decoded_str, None) on success or (None, http_status_or_None)
    for retryable transport states; raises for fatal errors.
    """
    payload = ("service=TrainRunningMob&subService=GetAvgDelayJson"
               "&trainNo=%s" % train_no)
    body = json.dumps({"jsonIn": encrypt_payload(payload, key16, iv16, sckey)}
                      ).encode("utf-8")
    req = urllib.request.Request(
        endpoint, data=body,
        headers={"Content-Type": "application/json", "User-Agent": USER_AGENT},
        method="POST",
    )
    try:
        with opener(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        if e.code == 429 or 500 <= e.code < 600:
            return None, e.code
        raise ValueError("HTTP %s" % e.code)
    try:
        obj = json.loads(raw)
    except json.JSONDecodeError:
        raise ValueError("non-JSON response (%d chars)" % len(raw))
    if "jsonIn" not in obj:
        alert = ""
        if isinstance(obj, dict):
            alert = str(obj.get("AlertMsg") or obj.get("alertMsg") or "")
        raise ValueError("server error path: %s" % (alert[:200] or raw[:200]))
    try:
        return decrypt_response(obj["jsonIn"], key16, iv16), None
    except Exception as e:
        raise ValueError("decrypt failed (keys rotated?): %s" % e)


# --------------------------------------------------------------------------
# sweep
# --------------------------------------------------------------------------

def run_sweep(args):
    key16, iv16, sckey, endpoint = load_keys(args.keys)
    if args.endpoint:
        endpoint = args.endpoint
    trains = load_train_list(args.gtfs, explicit=args.trains, limit=args.limit)
    progress = load_progress(args.progress) if args.resume else {"done": [], "failed": []}
    pending = select_pending(trains, progress, resume=args.resume)
    failed_by_train = {f["train"]: f for f in progress.get("failed", [])}
    for t in pending:  # retried below: drop stale failure, re-add on failure
        failed_by_train.pop(t, None)

    con = sqlite3.connect(args.staging)
    con.execute(STAGING_DDL)
    con.commit()

    opener = urllib.request.urlopen
    total = len(pending)
    done_ok = rows_total = 0
    failed = 0
    empty = 0
    req_times = []
    consec_transport = 0  # circuit-breaker streak (see header contract)
    t_start = time.time()
    print("sweep: %d trains pending (of %d listed), throttle=%.1fs+jitter, "
          "staging=%s" % (total, len(trains), args.throttle, args.staging))
    if total:
        print("sweep: est. wall time ~%.1fh at %.1fs/call"
              % (total * (args.throttle + 0.5) / 3600.0, args.throttle + 0.5))

    for i, train in enumerate(pending):
        t0 = time.time()
        decoded = None
        err = ""
        for attempt in range(MAX_RETRIES + 1):
            try:
                decoded, status = fetch_train(
                    train, opener, endpoint, key16, iv16, sckey)
            except ValueError as e:
                err = str(e)  # fatal for this train (AlertMsg/shape/decrypt)
                decoded = None
                consec_transport = 0  # server answered: connectivity proven
                break
            except Exception as e:
                # Transport failure (reset/timeout/DNS/SSL): the server
                # pushing back, NOT a train-specific error — same backoff
                # treatment as HTTP 429/5xx, then record and move on.
                err = "%s: %s" % (type(e).__name__, e)
                decoded = None
                consec_transport += 1
                if consec_transport >= CONSECUTIVE_COOLDOWN_N:
                    print("sweep: %d consecutive transport failures -> "
                          "cooling down %ds"
                          % (consec_transport, COOLDOWN_S), flush=True)
                    time.sleep(COOLDOWN_S)
                    consec_transport = 0
                if attempt < MAX_RETRIES:
                    time.sleep(RETRY_SLEEP_S)
                    continue
                break
            if decoded is not None:
                err = ""
                consec_transport = 0
                break
            err = "HTTP %s" % status  # retryable 429/5xx
            consec_transport += 1
            if consec_transport >= CONSECUTIVE_COOLDOWN_N:
                print("sweep: %d consecutive transport failures -> "
                      "cooling down %ds"
                      % (consec_transport, COOLDOWN_S), flush=True)
                time.sleep(COOLDOWN_S)
                consec_transport = 0
            if attempt < MAX_RETRIES:
                print("sweep: train %s HTTP %s -> sleep %ds (attempt %d/%d)"
                      % (train, status, RETRY_SLEEP_S,
                         attempt + 1, MAX_RETRIES),
                      flush=True)
                time.sleep(RETRY_SLEEP_S)
        fetched_at = int(time.time())
        if decoded is not None:
            try:
                rows, note = parse_avg_delay(decoded)
            except ValueError as e:
                err = str(e)
                decoded = None
            else:
                err = ""
        if decoded is not None:
            with con:
                con.executemany(
                    "INSERT OR REPLACE INTO staging_priors"
                    "(trainNumber, stationCode, arrAvgMin, depAvgMin, fetchedAt)"
                    " VALUES(?,?,?,?,?)",
                    [(train, stn, a, d, fetched_at) for stn, a, d in rows],
                )
            progress["done"].append(train)
            done_ok += 1
            rows_total += len(rows)
            if not rows:
                empty += 1
            print("sweep: %s ok rows=%d%s" % (train, len(rows),
                                             " (%s)" % note if note else ""),
                  flush=True)
        else:
            failed += 1
            failed_by_train[train] = {"train": train, "error": err,
                                      "ts": fetched_at}
            print("sweep: %s FAILED %s" % (train, err), flush=True)
        progress["failed"] = sorted(failed_by_train.values(),
                                    key=lambda f: f["train"])
        save_progress(args.progress, progress)

        dt = time.time() - t0
        req_times.append(dt)
        if (i + 1) % 25 == 0 or (i + 1) == total:
            avg = sum(req_times) / len(req_times)
            remain = total - (i + 1)
            eta_h = remain * (args.throttle + 0.25 + avg) / 3600.0
            print("sweep: %d/%d done ok=%d failed=%d rows=%d eta~%.1fh"
                  % (i + 1, total, done_ok, failed, rows_total, eta_h),
                  flush=True)
        if i + 1 < total:
            time.sleep(args.throttle + random.uniform(0, 0.5))

    con.close()
    wall = time.time() - t_start
    print("sweep: FINISHED ok=%d failed=%d rows=%d empty=%d wall=%.1fs"
          % (done_ok, failed, rows_total, empty, wall))
    if failed:
        print("sweep: failed trains (in %s): %s"
              % (args.progress,
                 ", ".join(sorted(failed_by_train)[-20:])))
    return 0 if not failed else 2


def main(argv=None):
    p = argparse.ArgumentParser(description="NTES GetAvgDelayJson sweep")
    p.add_argument("--keys", default=DEFAULT_KEYS)
    p.add_argument("--endpoint", default="",
                   help="override endpoint from keys file")
    p.add_argument("--gtfs", default=DEFAULT_GTFS)
    p.add_argument("--staging", default="staging_avg_delay.db")
    p.add_argument("--progress", default="progress.json")
    p.add_argument("--limit", type=int, default=0,
                   help="only first N trains of GTFS order (0=all)")
    p.add_argument("--trains", default="",
                   help="explicit comma list, e.g. 12952,12787 (for testing)")
    p.add_argument("--throttle", type=float, default=2.0,
                   help="base politeness delay in seconds (jitter +0-0.5s "
                        "always added)")
    p.add_argument("--resume", dest="resume", action="store_true",
                   default=True)
    p.add_argument("--no-resume", dest="resume", action="store_false")
    args = p.parse_args(argv)
    return run_sweep(args)


if __name__ == "__main__":
    sys.exit(main())
