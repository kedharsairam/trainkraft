#!/usr/bin/env python3
"""Convert GTFS zip to SQLite for TrainKraft Android Room database.

Usage:
    python gtfs_to_sqlite.py [--input GTFS_ZIP] [--output TRAINS_DB]

Defaults:
    INPUT:  C:\\Users\\kedhar\\OpenCode\\projects\\app-references\\train-status-deep\\gtfs-20260830.zip
    OUTPUT: C:\\Users\\kedhar\\OpenCode\\projects\\trainkraft\\app\\src\\main\\assets\\trains.db

Notes:
    - stdlib only (csv, sqlite3, zipfile, re). No pandas needed.
    - shapes.txt is never read (saves ~6MB).
    - Run with any Python 3.8+: `python tools/gtfs_to_sqlite.py`
      or via uv: `uv run --python 3.11 --no-project python tools/gtfs_to_sqlite.py`
"""

import argparse
import csv
import io
import re
import sqlite3
import sys
import zipfile
from pathlib import Path

DEFAULT_INPUT = Path(
    r"C:\Users\kedhar\OpenCode\projects\app-references\train-status-deep\gtfs-20260830.zip"
)
DEFAULT_OUTPUT = Path(
    r"C:\Users\kedhar\OpenCode\projects\trainkraft\app\src\main\assets\trains.db"
)

TRAIN_NUMBER_RE = re.compile(r"\d{5}")

SCHEMA_SQL = """
CREATE TABLE stations(stop_id TEXT PRIMARY KEY, code TEXT NOT NULL, name TEXT NOT NULL, lat REAL, lon REAL);
CREATE TABLE trains(route_id TEXT PRIMARY KEY, train_number TEXT NOT NULL, name TEXT NOT NULL, type TEXT);
CREATE TABLE trips(trip_id TEXT PRIMARY KEY, route_id TEXT NOT NULL, service_id TEXT NOT NULL);
CREATE TABLE calendar(service_id TEXT PRIMARY KEY, mon INTEGER, tue INTEGER, wed INTEGER, thu INTEGER, fri INTEGER, sat INTEGER, sun INTEGER, start_date INTEGER, end_date INTEGER);
CREATE TABLE stop_times(trip_id TEXT NOT NULL, seq INTEGER NOT NULL, stop_id TEXT NOT NULL, arr_min INTEGER, dep_min INTEGER, day_offset INTEGER DEFAULT 0, PRIMARY KEY(trip_id, seq));
CREATE VIRTUAL TABLE stations_fts USING fts5(code, name, content='stations', content_rowid='rowid');
"""


def parse_gtfs_time(value):
    """Parse 'HH:MM:SS' (HH may exceed 23) -> (total_minutes, day_offset).

    Rule: '25:30:00' -> (1530, 1). Empty/unparseable -> (None, 0).
    """
    if value is None:
        return None, 0
    s = value.strip()
    if not s:
        return None, 0
    try:
        parts = s.split(":")
        h = int(parts[0])
        m = int(parts[1]) if len(parts) > 1 else 0
        total = h * 60 + m
        return total, h // 24
    except (ValueError, IndexError):
        return None, 0


def first_train_number(text):
    """Return first 5-digit sequence in text, else None."""
    if not text:
        return None
    m = TRAIN_NUMBER_RE.search(text)
    return m.group(0) if m else None


def read_csv_from_zip(zf, name):
    """Yield dict rows for `name` inside zip. Raises KeyError if missing."""
    with zf.open(name) as fh:
        # utf-8-sig handles BOM if present
        text = io.TextIOWrapper(fh, encoding="utf-8-sig", newline="")
        reader = csv.DictReader(text)
        for row in reader:
            yield row


def to_int_or_none(value):
    if value is None:
        return None
    s = value.strip()
    if not s:
        return None
    try:
        return int(s)
    except ValueError:
        try:
            return int(float(s))
        except ValueError:
            return None


def to_float_or_none(value):
    if value is None:
        return None
    s = value.strip()
    if not s:
        return None
    try:
        return float(s)
    except ValueError:
        return None


def convert(gtfs_zip: Path, out_db: Path):
    if not gtfs_zip.is_file():
        print(f"ERROR: input not found: {gtfs_zip}", file=sys.stderr)
        sys.exit(1)
    out_db.parent.mkdir(parents=True, exist_ok=True)
    if out_db.exists():
        out_db.unlink()  # fresh build so schema/counts are exact

    skipped_routes = 0
    skipped_trips = 0
    skipped_stop_times = 0

    with zipfile.ZipFile(gtfs_zip, "r") as zf:
        names = set(zf.namelist())
        for required in ("stops.txt", "routes.txt", "trips.txt", "calendar.txt", "stop_times.txt"):
            if required not in names:
                print(f"ERROR: {required} missing from {gtfs_zip}", file=sys.stderr)
                sys.exit(1)
        # NOTE: shapes.txt intentionally never opened.

        # ---- Stage 1: stations ----
        stations = []
        for r in read_csv_from_zip(zf, "stops.txt"):
            stop_id = (r.get("stop_id") or "").strip()
            code = (r.get("stop_code") or "").strip() or stop_id
            name = (r.get("stop_name") or "").strip()
            if not stop_id or not code or not name:
                if not code:
                    continue  # rule 2: skip entries without code
                if not stop_id or not name:
                    continue
            stations.append(
                (
                    stop_id,
                    code,
                    name,
                    to_float_or_none(r.get("stop_lat")),
                    to_float_or_none(r.get("stop_lon")),
                )
            )

        # ---- Stage 2: buffer routes + trips (need headsign fallback) ----
        route_rows = list(read_csv_from_zip(zf, "routes.txt"))
        trip_rows = list(read_csv_from_zip(zf, "trips.txt"))

        headsign_by_route = {}
        for t in trip_rows:
            rid = (t.get("route_id") or "").strip()
            hs = (t.get("trip_headsign") or "").strip()
            if rid and hs:
                headsign_by_route.setdefault(rid, []).append(hs)

        trains = []
        valid_route_ids = set()
        for r in route_rows:
            route_id = (r.get("route_id") or "").strip()
            if not route_id:
                skipped_routes += 1
                continue
            short = (r.get("route_short_name") or "").strip()
            long = (r.get("route_long_name") or "").strip()
            number = first_train_number(short)
            if number is None:
                # rule 3 fallback: trip_headsign
                for hs in headsign_by_route.get(route_id, []):
                    number = first_train_number(hs)
                    if number:
                        break
            if number is None:
                number = first_train_number(long)  # last-resort, same 5-digit rule
            if number is None:
                skipped_routes += 1
                continue
            name = long or short or number
            train_type = (r.get("route_desc") or "").strip() or None
            trains.append((route_id, number, name, train_type))
            valid_route_ids.add(route_id)

        # ---- Stage 3: trips (skip trips whose route was skipped) ----
        trips = []
        valid_trip_ids = set()
        for t in trip_rows:
            trip_id = (t.get("trip_id") or "").strip()
            route_id = (t.get("route_id") or "").strip()
            service_id = (t.get("service_id") or "").strip()
            if not trip_id or not route_id or not service_id:
                skipped_trips += 1
                continue
            if route_id not in valid_route_ids:
                skipped_trips += 1
                continue
            trips.append((trip_id, route_id, service_id))
            valid_trip_ids.add(trip_id)

        # ---- Stage 4: calendar ----
        calendar = []
        for r in read_csv_from_zip(zf, "calendar.txt"):
            sid = (r.get("service_id") or "").strip()
            if not sid:
                continue
            calendar.append(
                (
                    sid,
                    to_int_or_none(r.get("monday")),
                    to_int_or_none(r.get("tuesday")),
                    to_int_or_none(r.get("wednesday")),
                    to_int_or_none(r.get("thursday")),
                    to_int_or_none(r.get("friday")),
                    to_int_or_none(r.get("saturday")),
                    to_int_or_none(r.get("sunday")),
                    to_int_or_none(r.get("start_date")),
                    to_int_or_none(r.get("end_date")),
                )
            )

        # ---- Stage 5: stop_times ----
        stop_times = []
        for r in read_csv_from_zip(zf, "stop_times.txt"):
            trip_id = (r.get("trip_id") or "").strip()
            stop_id = (r.get("stop_id") or "").strip()
            if not trip_id or not stop_id:
                skipped_stop_times += 1  # rule 6: missing stop_id
                continue
            if trip_id not in valid_trip_ids:
                skipped_stop_times += 1  # orphan of skipped route/trip
                continue
            try:
                seq = int((r.get("stop_sequence") or "").strip())
            except ValueError:
                skipped_stop_times += 1
                continue
            arr_min, arr_off = parse_gtfs_time(r.get("arrival_time"))
            dep_min, dep_off = parse_gtfs_time(r.get("departure_time"))
            day_offset = max(arr_off, dep_off)
            stop_times.append((trip_id, seq, stop_id, arr_min, dep_min, day_offset))

    # ---- Stage 6: write SQLite ----
    conn = sqlite3.connect(str(out_db))
    try:
        conn.executescript(SCHEMA_SQL)
        conn.executemany(
            "INSERT OR REPLACE INTO stations(stop_id, code, name, lat, lon) VALUES (?,?,?,?,?)",
            stations,
        )
        conn.executemany(
            "INSERT OR REPLACE INTO trains(route_id, train_number, name, type) VALUES (?,?,?,?)",
            trains,
        )
        conn.executemany(
            "INSERT OR REPLACE INTO trips(trip_id, route_id, service_id) VALUES (?,?,?)",
            trips,
        )
        conn.executemany(
            "INSERT OR REPLACE INTO calendar(service_id, mon, tue, wed, thu, fri, sat, sun, start_date, end_date)"
            " VALUES (?,?,?,?,?,?,?,?,?,?)",
            calendar,
        )
        conn.executemany(
            "INSERT OR REPLACE INTO stop_times(trip_id, seq, stop_id, arr_min, dep_min, day_offset)"
            " VALUES (?,?,?,?,?,?)",
            stop_times,
        )
        # FTS populate (rule 7)
        conn.execute(
            "INSERT INTO stations_fts(rowid, code, name) SELECT rowid, code, name FROM stations"
        )
        # Indexes (rule 8)
        conn.execute("CREATE INDEX idx_stop_times_stop ON stop_times(stop_id)")
        conn.execute("CREATE INDEX idx_trips_route ON trips(route_id)")
        conn.execute("CREATE INDEX idx_trains_number ON trains(train_number)")
        conn.commit()

        counts = {}
        for table in ("stations", "trains", "trips", "calendar", "stop_times", "stations_fts"):
            counts[table] = conn.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]

        conn.commit()
        conn.execute("VACUUM;")  # rule 9
        conn.close()
    except Exception:
        conn.close()
        raise

    size_bytes = out_db.stat().st_size
    size_mb = size_bytes / (1024 * 1024)

    print(f"Input : {gtfs_zip}")
    print(f"Output: {out_db}")
    print(f"DB size: {size_bytes} bytes ({size_mb:.2f} MB)")
    for table in ("stations", "trains", "trips", "calendar", "stop_times", "stations_fts"):
        print(f"  {table}: {counts[table]} rows")
    print(f"Skipped: {skipped_routes} routes (no train number), "
          f"{skipped_trips} trips (bad/orphan route), "
          f"{skipped_stop_times} stop_times (missing stop_id/orphan/bad seq)")
    return counts, size_bytes


def main():
    parser = argparse.ArgumentParser(description="GTFS -> TrainKraft trains.db converter")
    parser.add_argument("--input", default=str(DEFAULT_INPUT), help="GTFS zip path")
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT), help="Output SQLite path")
    args = parser.parse_args()
    convert(Path(args.input), Path(args.output))


if __name__ == "__main__":
    main()
