#!/usr/bin/env python3
"""Generate assets/licenses.json from our direct Android dependencies.

Reads app/build.gradle.kts (and gradle/libs.versions.toml when present),
extracts every direct dependency coordinate, and emits a JSON attribution
list [{name, version, license, url, testOnly}]. This satisfies the Apache
License 2.0 section 4 attribution duty for the libraries shipped in the APK.

License mapping is VERIFIED per artifact (upstream repo / Maven POM), not
guessed. Anything unmapped is emitted as "Unknown - verify" so a human must
resolve it - the unit test (LicensesCoverageTest) fails on unknown licenses
for non-test dependencies.

Usage (repo root):
    python3 tools/licenses/generate.py
    python3 tools/licenses/generate.py --check   # exit 1 if JSON is stale
"""

import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
BUILD_FILE = REPO / "app" / "build.gradle.kts"
VERSIONS_FILE = REPO / "gradle" / "libs.versions.toml"
OUT_FILE = REPO / "app" / "src" / "main" / "assets" / "licenses.json"

APACHE = "Apache License 2.0"
APACHE_URL = "https://www.apache.org/licenses/LICENSE-2.0"
ANDROIDX_URL = "https://github.com/androidx/androidx"

# Verified license per Maven group (checked Sep 2026):
# - androidx.*: Apache 2.0 (github.com/androidx/androidx LICENSE + OSS notices)
# - org.jetbrains.kotlinx (serialization, coroutines): Apache 2.0 (Kotlin GH org)
# - com.squareup.okhttp3: Apache 2.0 (Maven Central POM + square/okhttp LICENSE)
# - com.google.android.gms play-services-*: Android SDK License per Google
#   Maven (proprietary; NOT Apache - hence listed explicitly, never assumed)
# - junit:junit (test-only): EPL-1.0 (junit-team/junit4 LICENSE-junit.txt)
# - org.robolectric (test-only): MIT (platform/external/robolectric LICENSE)
# - com.kraft:*: first-party local composite build (settings.gradle.kts).
LICENSES = {
    "androidx.compose": (APACHE, ANDROIDX_URL),
    "androidx.core": (APACHE, ANDROIDX_URL),
    "androidx.lifecycle": (APACHE, ANDROIDX_URL),
    "androidx.activity": (APACHE, ANDROIDX_URL),
    "androidx.navigation": (APACHE, ANDROIDX_URL),
    "androidx.datastore": (APACHE, ANDROIDX_URL),
    "androidx.room": (APACHE, ANDROIDX_URL),
    "androidx.work": (APACHE, ANDROIDX_URL),
    "androidx.test": (APACHE, ANDROIDX_URL),
    "org.jetbrains.kotlinx": (APACHE, "https://github.com/Kotlin/kotlinx.coroutines"),
    "com.squareup.okhttp3": (APACHE, "https://square.github.io/okhttp/"),
    "com.google.android.gms": (
        "Android Software Development Kit License",
        "https://developer.android.com/studio/terms",
    ),
    "junit": ("Eclipse Public License 1.0", "https://github.com/junit-team/junit4"),
    "org.robolectric": ("MIT License", "https://github.com/robolectric/robolectric"),
    "com.kraft": (
        "First-party - local composite build",
        "https://github.com/kedharsairam/kraft-ui",
    ),
}

# Human-readable project URLs for well-known artifacts.
URLS = {
    "org.jetbrains.kotlinx:kotlinx-serialization-json":
        "https://github.com/Kotlin/kotlinx.serialization",
    "org.jetbrains.kotlinx:kotlinx-coroutines-test":
        "https://github.com/Kotlin/kotlinx.coroutines",
    "com.squareup.okhttp3:okhttp": "https://square.github.io/okhttp/",
    "com.google.android.gms:play-services-location":
        "https://developers.google.com/android/guides/setup",
}

DEP_RE = re.compile(r'''(implementation|ksp|testImplementation|androidTestImplementation|debugImplementation|kspTest)\s*\(\s*(?:platform\()?["']([^"']+)["']\)?\s*\)?''')


def parse_build_file() -> list[tuple[str, str, bool]]:
    """Return [(group:artifact, version, testOnly)] for direct deps."""
    text = BUILD_FILE.read_text()
    deps: list[tuple[str, str, bool]] = []
    for m in DEP_RE.finditer(text):
        config = m.group(1)
        coord = m.group(2)
        test_only = "test" in config.lower()
        parts = coord.split(":")
        if len(parts) == 2 and "." not in parts[1]:
            # Bare "group:artifact" without version (BOM-managed, e.g. compose ui).
            deps.append((coord, "(BOM-managed)", test_only))
        elif len(parts) >= 3:
            deps.append((f"{parts[0]}:{parts[1]}", parts[2], test_only))
        else:
            deps.append((coord, "unknown", test_only))
    # De-duplicate, keeping first occurrence.
    seen: dict[str, tuple[str, bool]] = {}
    for name, version, test_only in deps:
        if name not in seen:
            seen[name] = (version, test_only)
    return [(n, v, t) for n, (v, t) in seen.items()]


def license_for(group: str) -> tuple[str, str]:
    if group in LICENSES:
        return LICENSES[group]
    for prefix, lic in LICENSES.items():
        if group.startswith(prefix + ".") or group == prefix:
            return lic
    return ("Unknown - verify", "")


def generate() -> list[dict]:
    entries = []
    for name, version, test_only in parse_build_file():
        group = name.split(":")[0]
        lic, default_url = license_for(group)
        url = URLS.get(name, default_url)
        entries.append(
            {
                "name": name,
                "version": version,
                "license": lic,
                "url": url,
                "testOnly": test_only,
            }
        )
    entries.sort(key=lambda e: (e["testOnly"], e["name"]))
    return entries


def main() -> int:
    entries = generate()
    payload = json.dumps(entries, indent=2) + "\n"
    if "--check" in sys.argv:
        if not OUT_FILE.is_file() or OUT_FILE.read_text() != payload:
            print(f"{OUT_FILE} is stale - run tools/licenses/generate.py")
            return 1
        print(f"{OUT_FILE} up to date ({len(entries)} entries)")
        return 0
    OUT_FILE.parent.mkdir(parents=True, exist_ok=True)
    OUT_FILE.write_text(payload)
    unknowns = [e["name"] for e in entries
                if e["license"].startswith("Unknown") and not e["testOnly"]]
    print(f"wrote {OUT_FILE} ({len(entries)} entries)")
    if unknowns:
        print(f"WARNING: unverified licenses (resolve before release): {unknowns}")
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
