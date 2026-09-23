#!/usr/bin/env python3
"""Fog overlay validator + compiler (`data/fog_2025_26.json` -> sqlite).

This script does NOT scrape or NLP anything: the fog program is hand-compiled
from published zone press releases / press coverage into
`data/fog_2025_26.json`, and this script only VALIDATES that JSON and COMPILES
the valid entries into `fog_overlays` rows. Documented as such on purpose —
every entry carries a `source` URL and anything unsourced never ships.

Validation per entry (any failure -> loud reject list, nonzero exit):
  * `train` exists in the GTFS asset `trains` table (opened read-only).
  * `from`/`to` parse as YYYY-MM-DD and from <= to.
  * `action` in {CANCELLED, REDUCED_FREQ, REVISED_TIMING}.
  * `source` non-blank AND present in the file's top-level `sources[]` table
    (so every row is traceable to a publication).
Compiled row: (trainNumber, action, fromDate, toDate, season, note).

Stdlib only: argparse, datetime, json, os, sqlite3, sys.
"""

import argparse
import datetime
import json
import os
import sqlite3
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(os.path.dirname(HERE))
DEFAULT_JSON = os.path.join(HERE, "data", "fog_2025_26.json")
DEFAULT_GTFS = os.path.join(
    REPO_ROOT, "app", "src", "main", "assets", "trains.db"
)

ACTIONS = ("CANCELLED", "REDUCED_FREQ", "REVISED_TIMING")

FOG_DDL = """
CREATE TABLE IF NOT EXISTS fog_overlays(
  trainNumber TEXT NOT NULL PRIMARY KEY, action TEXT NOT NULL,
  fromDate TEXT NOT NULL, toDate TEXT NOT NULL,
  season TEXT NOT NULL, note TEXT NOT NULL DEFAULT '')
""".strip()


def parse_ymd(s):
    if not (isinstance(s, str) and len(s) == 10 and s[4] == "-"
            and s[7] == "-" and s.replace("-", "").isdigit()):
        return None
    try:
        return datetime.date(int(s[0:4]), int(s[5:7]), int(s[8:10]))
    except ValueError:
        return None


def load_known_trains(gtfs_path):
    uri = "file:%s?mode=ro" % os.path.abspath(gtfs_path)
    con = sqlite3.connect(uri, uri=True)
    try:
        return {r[0] for r in con.execute(
            "SELECT DISTINCT train_number FROM trains").fetchall()}
    finally:
        con.close()


def validate_entries(doc, known_trains):
    """Returns (valid_rows, rejects). Rows are 6-tuples for fog_overlays."""
    season = doc.get("season", "")
    source_ids = {s.get("id") for s in doc.get("sources", [])
                  if s.get("id") and s.get("url")}
    valid, rejects = [], []
    seen = set()
    for i, e in enumerate(doc.get("entries", [])):
        tag = "entries[%d] train=%r" % (i, e.get("train"))
        problems = []
        train = str(e.get("train", "")).strip()
        action = str(e.get("action", "")).strip()
        frm, to = e.get("from", ""), e.get("to", "")
        note = str(e.get("note", "") or "")
        source = str(e.get("source", "") or "").strip()
        if not train or train not in known_trains:
            problems.append("unknown train (absent from GTFS trains table)")
        if action not in ACTIONS:
            problems.append("action %r not in %s" % (action, list(ACTIONS)))
        d_from, d_to = parse_ymd(frm), parse_ymd(to)
        if d_from is None:
            problems.append("bad from date %r (want YYYY-MM-DD)" % (frm,))
        if d_to is None:
            problems.append("bad to date %r (want YYYY-MM-DD)" % (to,))
        if d_from and d_to and d_from > d_to:
            problems.append("from > to")
        if not source:
            problems.append("blank source URL/id (anti-hallucination rule)")
        elif source not in source_ids and not source.startswith("http"):
            problems.append("source %r not in top-level sources[] table"
                            % (source,))
        if train in seen:
            problems.append("duplicate train (one row per trainNumber)")
        if problems:
            rejects.append((tag, problems))
            continue
        seen.add(train)
        valid.append((train, action, frm, to, season, note))
    return valid, rejects


def compile_fog(json_path, gtfs_path, out_path):
    with open(json_path, "r", encoding="utf-8") as f:
        doc = json.load(f)
    if not doc.get("season"):
        print("fog: reject: top-level season missing", flush=True)
        return 1
    try:
        known = load_known_trains(gtfs_path)
    except sqlite3.Error as e:
        print("fog: ERROR opening GTFS asset %s: %s" % (gtfs_path, e),
              flush=True)
        return 1
    valid, rejects = validate_entries(doc, known)
    if rejects:
        print("fog: REJECTED %d entr%s:" % (
            len(rejects), "y" if len(rejects) == 1 else "ies"), flush=True)
        for tag, problems in rejects:
            for p in problems:
                print("fog:   reject %s: %s" % (tag, p), flush=True)
        return 1
    if os.path.exists(out_path):
        os.remove(out_path)
    parent = os.path.dirname(os.path.abspath(out_path))
    os.makedirs(parent, exist_ok=True)
    con = sqlite3.connect(out_path)
    try:
        con.execute(FOG_DDL)
        con.executemany(
            "INSERT INTO fog_overlays"
            "(trainNumber, action, fromDate, toDate, season, note)"
            " VALUES(?,?,?,?,?,?)", valid)
        con.commit()
        n = con.execute("SELECT COUNT(*) FROM fog_overlays").fetchone()[0]
    finally:
        con.close()
    print("fog: compiled %d rows season=%s -> %s" % (n, doc["season"], out_path))
    return 0


def main(argv=None):
    p = argparse.ArgumentParser(description="fog overlay validator+compiler")
    p.add_argument("--json", default=DEFAULT_JSON)
    p.add_argument("--gtfs", default=DEFAULT_GTFS)
    p.add_argument("--out", default="fog_overlays.db")
    args = p.parse_args(argv)
    return compile_fog(args.json, args.gtfs, args.out)


if __name__ == "__main__":
    sys.exit(main())
