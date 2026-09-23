#!/usr/bin/env python3
"""Assemble `pack.db` (contract DDL verbatim) + `pack.sha256`.

Inputs: staging sqlite from `sweep_avg_delay.py` (table `staging_priors`),
fog sqlite from `fog_compile.py` (table `fog_overlays`).
Output: `pack.db` with the exact schema the Android Room side expects —
the DDL strings below are byte-identical to the Phase-A/peer contract, do
not "improve" them:

  CREATE TABLE IF NOT EXISTS `delay_priors` (
    `trainNumber` TEXT NOT NULL, `stationCode` TEXT NOT NULL,
    `arrAvgMin` INTEGER NOT NULL DEFAULT 0, `depAvgMin` INTEGER NOT NULL DEFAULT 0,
    `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`trainNumber`, `stationCode`));
  CREATE TABLE IF NOT EXISTS `fog_overlays` (
    `trainNumber` TEXT NOT NULL PRIMARY KEY, `action` TEXT NOT NULL,
    `fromDate` TEXT NOT NULL, `toDate` TEXT NOT NULL,
    `season` TEXT NOT NULL, `note` TEXT NOT NULL DEFAULT '');
  CREATE TABLE IF NOT EXISTS `pack_meta` (
    `key` TEXT NOT NULL PRIMARY KEY, `value` TEXT NOT NULL);

`staging_priors(trainNumber, stationCode, arrAvgMin, depAvgMin, fetchedAt)`
maps 1:1 onto `delay_priors(... , updatedAt=fetchedAt)`. `pack_meta` rows:
packVersion=1, generatedAt (UTC ISO), trainCount (DISTINCT trains in
delay_priors), gtfsVintage, fogSeason, source="ntes-avdelay-sweep".

Also writes `<out>.sha256` = hex SHA-256 of pack.db (manifest the app /
release process verifies before bundling).

Fails (nonzero exit) unless both data tables end up with > 0 rows.

Stdlib only: argparse, datetime, hashlib, os, sqlite3, sys, time.
"""

import argparse
import datetime
import hashlib
import os
import sqlite3
import sys

DELAY_PRIORS_DDL = (
    "CREATE TABLE IF NOT EXISTS `delay_priors` (\n"
    "  `trainNumber` TEXT NOT NULL, `stationCode` TEXT NOT NULL,\n"
    "  `arrAvgMin` INTEGER NOT NULL DEFAULT 0, "
    "`depAvgMin` INTEGER NOT NULL DEFAULT 0,\n"
    "  `updatedAt` INTEGER NOT NULL, "
    "PRIMARY KEY(`trainNumber`, `stationCode`));"
)

FOG_OVERLAYS_DDL = (
    "CREATE TABLE IF NOT EXISTS `fog_overlays` (\n"
    "  `trainNumber` TEXT NOT NULL PRIMARY KEY, `action` TEXT NOT NULL,\n"
    "  `fromDate` TEXT NOT NULL, `toDate` TEXT NOT NULL,\n"
    "  `season` TEXT NOT NULL, `note` TEXT NOT NULL DEFAULT '');"
)

PACK_META_DDL = (
    "CREATE TABLE IF NOT EXISTS `pack_meta` "
    "(`key` TEXT NOT NULL PRIMARY KEY, `value` TEXT NOT NULL);"
)


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def build_pack(staging_path, fog_path, out_path, gtfs_vintage, fog_season):
    for p in (staging_path, fog_path):
        if not os.path.exists(p):
            raise FileNotFoundError("missing input: %s" % p)
    if os.path.exists(out_path):
        os.remove(out_path)
    src_staging = sqlite3.connect("file:%s?mode=ro"
                                  % os.path.abspath(staging_path), uri=True)
    src_fog = sqlite3.connect("file:%s?mode=ro"
                              % os.path.abspath(fog_path), uri=True)
    dst = sqlite3.connect(out_path)
    try:
        dst.execute(DELAY_PRIORS_DDL)
        dst.execute(FOG_OVERLAYS_DDL)
        dst.execute(PACK_META_DDL)
        staging_rows = src_staging.execute(
            "SELECT trainNumber, stationCode, arrAvgMin, depAvgMin, fetchedAt"
            " FROM staging_priors").fetchall()
        dst.executemany(
            "INSERT OR REPLACE INTO `delay_priors`"
            "(`trainNumber`,`stationCode`,`arrAvgMin`,`depAvgMin`,`updatedAt`)"
            " VALUES(?,?,?,?,?)", staging_rows)
        fog_rows = src_fog.execute(
            "SELECT trainNumber, action, fromDate, toDate, season, note"
            " FROM fog_overlays").fetchall()
        if fog_season:
            fog_rows = [(t, a, f, to, fog_season, n)
                        for t, a, f, to, _s, n in fog_rows]
        dst.executemany(
            "INSERT OR REPLACE INTO `fog_overlays`"
            "(`trainNumber`,`action`,`fromDate`,`toDate`,`season`,`note`)"
            " VALUES(?,?,?,?,?,?)", fog_rows)
        n_delay = dst.execute("SELECT COUNT(*) FROM `delay_priors`"
                              ).fetchone()[0]
        n_trains = dst.execute(
            "SELECT COUNT(DISTINCT `trainNumber`) FROM `delay_priors`"
        ).fetchone()[0]
        n_fog = dst.execute("SELECT COUNT(*) FROM `fog_overlays`"
                            ).fetchone()[0]
        if n_delay == 0 or n_fog == 0:
            raise ValueError("refusing to pack: delay_rows=%d fog_rows=%d "
                             "(both must be > 0)" % (n_delay, n_fog))
        meta = {
            "packVersion": "1",
            "generatedAt": datetime.datetime.now(
                datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "trainCount": str(n_trains),
            "delayRowCount": str(n_delay),
            "fogRowCount": str(n_fog),
            "gtfsVintage": gtfs_vintage,
            "fogSeason": fog_season,
            "source": "ntes-avdelay-sweep",
        }
        dst.executemany(
            "INSERT OR REPLACE INTO `pack_meta`(`key`,`value`) VALUES(?,?)",
            sorted(meta.items()))
        dst.commit()
    finally:
        src_staging.close()
        src_fog.close()
        dst.close()
    digest = sha256_file(out_path)
    with open(out_path + ".sha256", "w", encoding="utf-8") as f:
        f.write("%s  %s\n" % (digest, os.path.basename(out_path)))
    print("pack: delay_rows=%d trains=%d fog_rows=%d -> %s"
          % (n_delay, n_trains, n_fog, out_path))
    print("pack: sha256=%s" % digest)
    print("pack: meta=%s" % (meta,))
    return {"delay_rows": n_delay, "trains": n_trains, "fog_rows": n_fog,
            "sha256": digest, "meta": meta}


def main(argv=None):
    p = argparse.ArgumentParser(description="assemble pack.db")
    p.add_argument("--staging", default="staging_avg_delay.db")
    p.add_argument("--fog-db", default="fog_overlays.db")
    p.add_argument("--out", default="pack.db")
    p.add_argument("--gtfs-vintage", default="2026-08")
    p.add_argument("--fog-season", default="2025-26")
    args = p.parse_args(argv)
    try:
        build_pack(args.staging, args.fog_db, args.out,
                   args.gtfs_vintage, args.fog_season)
    except (FileNotFoundError, ValueError, sqlite3.Error) as e:
        print("pack: ERROR %s" % e, flush=True)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
