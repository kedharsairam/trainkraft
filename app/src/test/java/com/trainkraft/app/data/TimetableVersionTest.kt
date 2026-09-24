package com.trainkraft.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pins the timetable-version contract ([TimetableVersionChecker]).
 *
 * Robolectric (not plain JUnit) because the bundled-asset test reads
 * `timetable-version.json` through AssetManager — same justification as
 * [NtesConfigTest]'s snapshot test. The remote fetch is deliberately NOT
 * faked: failure tests point at an unreachable loopback port / malformed
 * URL, which fail fast to null in any sandbox without touching the network.
 */
@RunWith(RobolectricTestRunner::class)
class TimetableVersionTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    // ------------------------------------------------------------ compare

    @Test
    fun `newer remote month is an update`() {
        assertTrue(TimetableVersionChecker.isUpdateAvailable("2026-08", "2026-10"))
        assertTrue(TimetableVersionChecker.isUpdateAvailable("2026-08", "2026-09"))
        assertTrue(TimetableVersionChecker.isUpdateAvailable("2026-12", "2027-01"))
    }

    @Test
    fun `same or older remote is not an update`() {
        assertFalse(TimetableVersionChecker.isUpdateAvailable("2026-08", "2026-08"))
        assertFalse(TimetableVersionChecker.isUpdateAvailable("2026-10", "2026-08"))
        assertFalse(TimetableVersionChecker.isUpdateAvailable("2027-01", "2026-12"))
    }

    @Test
    fun `malformed sides are never an update and never throw`() {
        assertFalse(TimetableVersionChecker.isUpdateAvailable("", "2026-10"))
        assertFalse(TimetableVersionChecker.isUpdateAvailable("2026-08", ""))
        assertFalse(TimetableVersionChecker.isUpdateAvailable("aug-2026", "2026-10"))
        assertFalse(TimetableVersionChecker.isUpdateAvailable("2026-08", "Oct 2026"))
        assertFalse(TimetableVersionChecker.isUpdateAvailable("2026-8", "2026-10"))
        assertFalse(TimetableVersionChecker.isUpdateAvailable("2026-08", "2026-13"))
        assertFalse(TimetableVersionChecker.isUpdateAvailable("2026-00", "2026-10"))
        assertFalse(TimetableVersionChecker.isUpdateAvailable("not json at all", "{bad"))
    }

    // ------------------------------------------------------------ parse

    @Test
    fun `valid shape parses`() {
        val v = TimetableVersionChecker.parse(
            """{"version":"2026-08","generatedAt":"2026-08-15","trains":10594}""",
        )
        assertNotNull(v)
        assertEquals("2026-08", v!!.version)
        assertEquals("2026-08-15", v.generatedAt)
        assertEquals(10594, v.trains)
    }

    @Test
    fun `parse ignores comment field and tolerates whitespace`() {
        val v = TimetableVersionChecker.parse(
            """{ "_comment": "provenance", "version" : "2026-10" ,
                "generatedAt" : "2026-10-12" , "trains" : 10600 }""",
        )
        assertNotNull(v)
        assertEquals("2026-10", v!!.version)
    }

    @Test
    fun `parse rejects missing or invalid fields without throwing`() {
        assertNull(TimetableVersionChecker.parse("{}"))
        assertNull(TimetableVersionChecker.parse("""{"version":"2026-08"}"""))
        assertNull(
            TimetableVersionChecker.parse(
                """{"version":"Aug 2026","generatedAt":"2026-08-15","trains":10594}""",
            ),
        )
        assertNull(
            TimetableVersionChecker.parse(
                """{"version":"2026-08","generatedAt":"","trains":10594}""",
            ),
        )
        assertNull(
            TimetableVersionChecker.parse(
                """{"version":"2026-08","generatedAt":"2026-08-15"}""",
            ),
        )
        assertNull(TimetableVersionChecker.parse("not json"))
        assertNull(TimetableVersionChecker.parse(""))
    }

    // ------------------------------------------------------------ month label

    @Test
    fun `month label renders Oct 2026 style`() {
        assertEquals("Oct 2026", TimetableVersionChecker.monthLabel("2026-10"))
        assertEquals("Jan 2026", TimetableVersionChecker.monthLabel("2026-01"))
        assertEquals("Aug 2026", TimetableVersionChecker.monthLabel("2026-08"))
    }

    @Test
    fun `month label passes malformed input through`() {
        assertEquals("soon", TimetableVersionChecker.monthLabel("soon"))
        assertEquals("", TimetableVersionChecker.monthLabel(""))
    }

    // ------------------------------------------------------------ fetch failures

    @Test
    fun `fetch from unreachable host returns null`() = runBlocking {
        // Nothing listens on loopback port 9 (discard) — refused immediately.
        assertNull(TimetableVersionChecker.fetchRemote("http://127.0.0.1:9/timetable-version.json"))
    }

    @Test
    fun `fetch with malformed url returns null`() = runBlocking {
        assertNull(TimetableVersionChecker.fetchRemote("not-a-url"))
    }

    // ------------------------------------------------------------ bundled asset

    @Test
    fun `bundled asset parses to the shipped snapshot`() {
        // Pins the asset the screen reads: vintage + train count verified
        // against trains.db (10,594 rows, read-only sqlite query 2026-09-24).
        val local = TimetableVersionChecker.readLocal(context)
        assertNotNull("timetable-version.json asset missing or invalid", local)
        assertEquals("2026-08", local!!.version)
        assertEquals(10594, local.trains)
    }
}
