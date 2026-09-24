package com.trainkraft.app.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the Apache 2.0 section 4 attribution duty.
 *
 * `tools/licenses/generate.py` emits `src/main/assets/licenses.json` from the
 * direct dependencies in `build.gradle.kts`. These tests fail when:
 * - a direct dependency is missing from the JSON (stale attributions), or
 * - a shipped (non-test) entry has an unverified ("Unknown - verify") license.
 *
 * Both files are read via paths relative to the `app/` module dir (the unit
 * test working dir — same pattern as NtesFixtureCaptureTest). Parsing is
 * regex-only on purpose: org.json lives in the stub android.jar and throws
 * outside Robolectric.
 */
class LicensesCoverageTest {

    private fun moduleFile(path: String): File {
        val f = generateSequence(File(".").absoluteFile) { it.parentFile }
            .map { File(it, path) }
            .firstOrNull { it.isFile }
        assertTrue("$path not found above ${File(".").absolutePath}", f != null)
        return f!!
    }

    private val depPattern =
        Regex("""(?:implementation|ksp|testImplementation|androidTestImplementation)\s*\(\s*(?:platform\()?["']([^"']+)["']""")
    private val namePattern = Regex(""""name"\s*:\s*"([^"]+)"""")
    private val fieldPattern = Regex(""""(name|version|license|url)"\s*:""")

    private fun buildDeps(): Set<String> {
        val text = moduleFile("build.gradle.kts").readText()
        return depPattern.findAll(text).map { m ->
            val coord = m.groupValues[1]
            val parts = coord.split(":")
            // Mirror generate.py: strip versions, mark BOM-managed bare coords.
            if (parts.size >= 3) "${parts[0]}:${parts[1]}" else coord
        }.toSet()
    }

    private fun jsonEntries(): List<Map<String, String>> {
        val text = moduleFile("src/main/assets/licenses.json").readText()
        // Split top-level objects by brace depth (flat schema: no nesting).
        val entries = mutableListOf<String>()
        var depth = 0
        var start = -1
        for ((i, c) in text.withIndex()) {
            if (c == '{') {
                if (depth == 0) start = i
                depth++
            } else if (c == '}') {
                depth--
                if (depth == 0 && start >= 0) {
                    entries.add(text.substring(start, i + 1))
                    start = -1
                }
            }
        }
        return entries.map { e ->
            val license = Regex(""""license"\s*:\s*"([^"]+)"""")
                .find(e)?.groupValues?.get(1).orEmpty()
            val testOnly = Regex(""""testOnly"\s*:\s*(true|false)""")
                .find(e)?.groupValues?.get(1) == "true"
            mapOf(
                "name" to (namePattern.find(e)?.groupValues?.get(1).orEmpty()),
                "license" to license,
                "testOnly" to testOnly.toString(),
                "fields" to fieldPattern.findAll(e).map { it.groupValues[1] }.toSet().toString(),
            )
        }
    }

    @Test
    fun everyDirectDepIsAttributed() {
        val deps = buildDeps()
        assertTrue("no dependencies parsed from build.gradle.kts", deps.isNotEmpty())
        val names = jsonEntries().map { it["name"] }.toSet()
        val missing = deps - names
        assertTrue(
            "licenses.json is missing attributions for: $missing " +
                "(run tools/licenses/generate.py)",
            missing.isEmpty(),
        )
    }

    @Test
    fun noUnverifiedShippedLicenses() {
        val unverified = jsonEntries()
            .filter { it["testOnly"] != "true" && it["license"]!!.startsWith("Unknown") }
            .map { it["name"] }
        assertTrue(
            "unverified licenses must be resolved before release: $unverified",
            unverified.isEmpty(),
        )
    }

    @Test
    fun entriesAreWellFormed() {
        val entries = jsonEntries()
        assertTrue("licenses.json has no entries", entries.isNotEmpty())
        val required = setOf("name", "version", "license", "url")
        val text = moduleFile("src/main/assets/licenses.json").readText()
        // Field presence per raw entry block.
        var depth = 0
        var start = -1
        val blocks = mutableListOf<String>()
        for ((i, c) in text.withIndex()) {
            if (c == '{') {
                if (depth == 0) start = i
                depth++
            } else if (c == '}') {
                depth--
                if (depth == 0 && start >= 0) {
                    blocks.add(text.substring(start, i + 1))
                    start = -1
                }
            }
        }
        blocks.forEach { b ->
            val present = fieldPattern.findAll(b).map { it.groupValues[1] }.toSet()
            assertEquals("entry missing fields (want $required): $b", required, present)
        }
    }
}
