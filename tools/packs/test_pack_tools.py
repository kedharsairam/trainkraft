#!/usr/bin/env python3
"""Offline unit tests for the pack pipeline. Run from this directory:

    python3 -m unittest          # discover (this file matches test*.py)
    python3 -m unittest test_pack_tools -v

No network, no Android/Gradle. The GTFS asset is only touched read-only by
`fog_compile` itself — validator tests below inject a fake train set so they
run anywhere.
"""

import json
import os
import sqlite3
import tempfile
import unittest

import build_pack
import fog_compile
import sweep_avg_delay
from ntes_crypto import (aes_cbc_decrypt, aes_cbc_encrypt, decrypt_block,
                         decrypt_response, encrypt_block, encrypt_payload,
                         expand_key, load_keys)

HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(os.path.dirname(HERE))
FIXTURE = os.path.join(
    REPO_ROOT, "app", "src", "test", "resources",
    "fixtures", "avg_delay_12952.json")
KEYS_FILE = os.path.join(REPO_ROOT, "ntes-keys.json")


class CryptoTest(unittest.TestCase):
    def test_fips197_vector(self):
        # FIPS-197 Appendix B: AES-128 known-answer test.
        key = bytes.fromhex("000102030405060708090a0b0c0d0e0f")
        pt = bytes.fromhex("00112233445566778899aabbccddeeff")
        rk = expand_key(key)
        ct = encrypt_block(pt, rk)
        self.assertEqual(ct.hex(), "69c4e0d86a7b0430d8cdb78070b4c55a")
        self.assertEqual(decrypt_block(ct, rk), pt)

    def test_cbc_roundtrip_odd_lengths(self):
        key, iv = b"K" * 16, b"I" * 16
        for msg in (b"a", b"123456789012345", b"1234567890123456",
                    b"x" * 100):
            self.assertEqual(aes_cbc_decrypt(
                aes_cbc_encrypt(msg, key, iv), key, iv), msg)

    def test_ntes_layer_roundtrip_with_repo_keys(self):
        key16, iv16, sckey, endpoint = load_keys(KEYS_FILE)
        self.assertIn("enquiry.indianrail.gov.in", endpoint)
        msg = ("service=TrainRunningMob&subService=GetAvgDelayJson"
               "&trainNo=12952")
        tok = encrypt_payload(msg, key16, iv16, sckey)
        digest, enc = tok.split("#", 1)
        self.assertEqual(len(digest), 32)  # MD5 hex, upper
        self.assertTrue(enc and len(enc) % 2 == 0)  # hex of b64
        self.assertEqual(decrypt_response(tok, key16, iv16), msg)
        self.assertEqual(decrypt_response(enc, key16, iv16), msg)  # bare ENC

    def test_load_keys_rejects_blank(self):
        with tempfile.NamedTemporaryFile("w", suffix=".json",
                                         delete=False) as f:
            json.dump({"key": "", "iv": "x" * 16, "sckey": "y"}, f)
            path = f.name
        try:
            with self.assertRaises(ValueError):
                load_keys(path)
        finally:
            os.unlink(path)


class DelayMappingTest(unittest.TestCase):
    def test_mapping_cases(self):
        m = sweep_avg_delay.delay_to_minutes
        self.assertEqual(m(""), 0)
        self.assertEqual(m(None), 0)
        self.assertEqual(m("   "), 0)
        self.assertEqual(m("On Time"), 0)
        self.assertEqual(m("on time"), 0)
        self.assertEqual(m("RT"), 0)
        self.assertEqual(m("rt"), 0)
        self.assertEqual(m("00:13"), 13)
        self.assertEqual(m("00:12"), 12)
        self.assertEqual(m("00:00"), 0)
        self.assertEqual(m("01:05"), 65)
        self.assertEqual(m("25:00"), 0)   # hour >= 24 unparseable -> 0
        self.assertEqual(m("abc"), 0)     # garbage -> 0 (NOT NULL col)
        self.assertEqual(m("12:345"), 0)

    def test_app_fixture_values(self):
        # Exact keys/values from avg_delay_12952.json (read-only fixture).
        with open(FIXTURE, encoding="utf-8") as f:
            decoded = f.read()
        rows, note = sweep_avg_delay.parse_avg_delay(decoded)
        self.assertEqual(note, "")
        by_stn = {s: (a, d) for s, a, d in rows}
        self.assertEqual(by_stn["NDLS"], (0, 0))    # "" + "On Time"
        self.assertEqual(by_stn["KOTA"], (13, 12))  # "00:13" + "00:12"
        self.assertEqual(by_stn["MMCT"], (0, 0))    # "On Time" + ""
        self.assertEqual(len(rows), 8)

    def test_empty_list_tolerated(self):
        rows, note = sweep_avg_delay.parse_avg_delay(
            json.dumps({"AlertMsg": "", "vAvgDelayList": []}))
        self.assertEqual(rows, [])
        self.assertTrue(note)
        rows, _ = sweep_avg_delay.parse_avg_delay(json.dumps({}))
        self.assertEqual(rows, [])

    def test_alertmsg_is_failure(self):
        with self.assertRaises(ValueError):
            sweep_avg_delay.parse_avg_delay(
                json.dumps({"AlertMsg": "Invalid train"}))


class FogValidatorTest(unittest.TestCase):
    def setUp(self):
        self.known = {"12952", "14213", "12505"}
        self.base = {
            "season": "2025-26",
            "sources": [{"id": "S1", "url": "https://example.test/s1"}],
        }

    def doc(self, entry):
        d = dict(self.base)
        d["entries"] = [entry]
        return d

    def good(self, **kw):
        e = {"train": "14213", "action": "CANCELLED",
             "from": "2025-12-01", "to": "2026-02-28",
             "note": "n", "source": "S1"}
        e.update(kw)
        return e

    def test_accept_matrix(self):
        for action in ("CANCELLED", "REDUCED_FREQ", "REVISED_TIMING"):
            valid, rejects = fog_compile.validate_entries(
                self.doc(self.good(action=action)), self.known)
            self.assertEqual(rejects, [], action)
            self.assertEqual(len(valid), 1)
            self.assertEqual(valid[0][1], action)
        # raw-URL source (not via sources[] table) also accepted
        valid, rejects = fog_compile.validate_entries(
            self.doc(self.good(source="https://example.test/x")), self.known)
        self.assertEqual(rejects, [])

    def test_reject_matrix(self):
        cases = [
            self.good(train="99999"),                       # unknown train
            self.good(train=""),                            # blank train
            self.good(action="DELAYED"),                    # bad enum
            self.good(action="cancelled"),                  # case-sensitive
            self.good(**{"from": "01-12-2025"}),  # bad date shape
            self.good(**{"to": "2026-13-01"}),              # bad month
            self.good(**{"from": "2026-02-28", "to": "2025-12-01"}),
            self.good(source=""),                           # blank source
            self.good(source="NOPE"),                       # unknown source id
        ]
        for e in cases:
            _, rejects = fog_compile.validate_entries(
                self.doc(e), self.known)
            self.assertEqual(len(rejects), 1, e)

    def test_duplicate_train_rejected(self):
        d = dict(self.base)
        d["entries"] = [self.good(), self.good(action="REDUCED_FREQ")]
        valid, rejects = fog_compile.validate_entries(d, self.known)
        self.assertEqual(len(valid), 1)
        self.assertEqual(len(rejects), 1)

    def test_end_to_end_rejects_exit_nonzero(self):
        with tempfile.TemporaryDirectory() as tmp:
            bad = os.path.join(tmp, "bad.json")
            with open(bad, "w", encoding="utf-8") as f:
                json.dump(self.doc(self.good(train="00000")), f)
            rc = fog_compile.compile_fog(
                bad, "/nonexistent-gtfs.db", os.path.join(tmp, "o.db"))
            # fails at GTFS open OR at validation — either way nonzero,
            # never a silent pass
            self.assertNotEqual(rc, 0)


class ResumeSkipTest(unittest.TestCase):
    def test_resume_skips_done_retries_failed(self):
        trains = ["1", "2", "3", "4"]
        prog = {"done": ["1", "2"],
                "failed": [{"train": "3", "error": "x", "ts": 1}]}
        pending = sweep_avg_delay.select_pending(trains, prog, resume=True)
        self.assertEqual(pending, ["3", "4"])  # failed retried, done skipped

    def test_no_resume_fetches_all(self):
        trains = ["1", "2"]
        prog = {"done": ["1"], "failed": []}
        self.assertEqual(
            sweep_avg_delay.select_pending(trains, prog, resume=False),
            ["1", "2"])

    def test_progress_roundtrip(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = os.path.join(tmp, "progress.json")
            prog = {"done": ["12952"], "failed": []}
            sweep_avg_delay.save_progress(p, prog)
            self.assertEqual(sweep_avg_delay.load_progress(p), prog)
            self.assertEqual(
                sweep_avg_delay.load_progress(p + ".missing"),
                {"done": [], "failed": []})


class StagingUpsertTest(unittest.TestCase):
    def test_pk_upsert_keeps_latest(self):
        with tempfile.TemporaryDirectory() as tmp:
            db = os.path.join(tmp, "s.db")
            con = sqlite3.connect(db)
            con.execute(sweep_avg_delay.STAGING_DDL)
            now = 1000
            con.execute(
                "INSERT OR REPLACE INTO staging_priors VALUES(?,?,?,?,?)",
                ("12952", "KOTA", 13, 12, now))
            con.execute(
                "INSERT OR REPLACE INTO staging_priors VALUES(?,?,?,?,?)",
                ("12952", "KOTA", 15, 14, now + 99))
            con.execute(
                "INSERT OR REPLACE INTO staging_priors VALUES(?,?,?,?,?)",
                ("12952", "RTM", 10, 11, now))
            con.commit()
            rows = con.execute(
                "SELECT trainNumber, stationCode, arrAvgMin, depAvgMin,"
                " fetchedAt FROM staging_priors ORDER BY stationCode"
            ).fetchall()
            con.close()
        self.assertEqual(rows, [("12952", "KOTA", 15, 14, now + 99),
                               ("12952", "RTM", 10, 11, now)])


class ManifestHashTest(unittest.TestCase):
    def _inputs(self, tmp):
        st = os.path.join(tmp, "staging.db")
        con = sqlite3.connect(st)
        con.execute(sweep_avg_delay.STAGING_DDL)
        con.executemany(
            "INSERT INTO staging_priors VALUES(?,?,?,?,?)",
            [("12952", "KOTA", 13, 12, 1700000000),
             ("12952", "RTM", 10, 11, 1700000000),
             ("12787", "BZA", 26, 27, 1700000000)])
        con.commit()
        con.close()
        fog = os.path.join(tmp, "fog.db")
        con = sqlite3.connect(fog)
        con.execute(fog_compile.FOG_DDL)
        con.execute(
            "INSERT INTO fog_overlays VALUES(?,?,?,?,?,?)",
            ("12952", "CANCELLED", "2025-12-01", "2026-02-28",
             "2025-26", "test"))
        con.commit()
        con.close()
        return st, fog

    def test_build_verify_roundtrip(self):
        with tempfile.TemporaryDirectory() as tmp:
            st, fog = self._inputs(tmp)
            out = os.path.join(tmp, "pack.db")
            summary = build_pack.build_pack(st, fog, out, "2026-08",
                                            "2025-26")
            self.assertEqual(summary["delay_rows"], 3)
            self.assertEqual(summary["trains"], 2)
            self.assertEqual(summary["fog_rows"], 1)
            # manifest roundtrip: stored hash == recomputed hash
            with open(out + ".sha256", encoding="utf-8") as f:
                stored = f.read().split()[0]
            self.assertEqual(stored, build_pack.sha256_file(out))
            # contract tables + meta present
            con = sqlite3.connect("file:%s?mode=ro" % out, uri=True)
            try:
                tables = {r[0] for r in con.execute(
                    "SELECT name FROM sqlite_master WHERE type='table'")}
                self.assertTrue({"delay_priors", "fog_overlays",
                                 "pack_meta"} <= tables)
                meta = dict(con.execute("SELECT `key`,`value` FROM `pack_meta`"
                                        ).fetchall())
            finally:
                con.close()
            for k in ("packVersion", "generatedAt", "trainCount",
                      "gtfsVintage", "fogSeason", "source"):
                self.assertIn(k, meta)
            self.assertEqual(meta["packVersion"], "1")
            self.assertEqual(meta["trainCount"], "2")
            self.assertEqual(meta["source"], "ntes-avdelay-sweep")

    def test_refuses_empty_inputs(self):
        with tempfile.TemporaryDirectory() as tmp:
            st = os.path.join(tmp, "staging.db")
            con = sqlite3.connect(st)
            con.execute(sweep_avg_delay.STAGING_DDL)
            con.commit()
            con.close()
            fog = os.path.join(tmp, "fog.db")
            con = sqlite3.connect(fog)
            con.execute(fog_compile.FOG_DDL)
            con.commit()
            con.close()
            with self.assertRaises(ValueError):
                build_pack.build_pack(st, fog, os.path.join(tmp, "p.db"),
                                      "2026-08", "2025-26")


if __name__ == "__main__":
    unittest.main()
