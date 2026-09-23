package com.trainkraft.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import org.json.JSONObject

/**
 * MANUAL fixture capture against the live NTES endpoint.
 *
 * Skipped unless CAPTURE_NTES_FIXTURES=1 (never runs in CI or normal builds):
 * ```
 * CAPTURE_NTES_FIXTURES=1 ./gradlew :app:testDebugUnitTest \
 *     --tests '*NtesFixtureCaptureTest*' --no-daemon
 * ```
 *
 * Writes decrypted raw responses to `app/src/test/resources/fixtures/`
 * as `<name>.json` — the ground truth that the strict DTO parser tests run against. Uses the app's
 * own [NtesCrypto]/[NtesApi] stack, so a successful capture also proves the
 * production client end-to-end.
 */
@RunWith(RobolectricTestRunner::class)
class NtesFixtureCaptureTest {

    /** Repo-root `ntes-keys.json` (test working dir = the `app/` module dir). */
    private fun repoNtesKeys(): NtesKeys {
        val keysFile = generateSequence(File(".").absoluteFile) { it.parentFile }
            .map { File(it, "ntes-keys.json") }
            .firstOrNull { it.isFile }
        assertTrue(
            "ntes-keys.json not found above ${File(".").absolutePath}",
            keysFile != null,
        )
        val json = JSONObject(keysFile!!.readText())
        return NtesKeys(
            key = json.getString("key"),
            iv = json.getString("iv"),
            sckey = json.getString("sckey"),
        )
    }

    @Test
    fun captureAllFixtures() = runBlocking {
        Assume.assumeTrue(
            "set CAPTURE_NTES_FIXTURES=1 to run",
            System.getenv("CAPTURE_NTES_FIXTURES") == "1",
        )
        val keys = repoNtesKeys()
        val out = File("src/test/resources/fixtures").apply { mkdirs() }

        suspend fun capture(
            name: String,
            allowAlert: Boolean = false,
            call: suspend () -> Result<String>,
        ) {
            val r = call()
            r.onSuccess { body ->
                check(body.isNotBlank()) { "$name: empty body" }
                File(out, "$name.json").writeText(body)
                println("CAPTURED $name (${body.length} chars)")
            }
            r.onFailure { e ->
                if (allowAlert) {
                    // e.g. TrainExcpInfo: {"AlertMsg": "No Exceptional Details..."}
                    // is a valid server response — capture the envelope verbatim.
                    val envelope = JSONObject().put("AlertMsg", e.message ?: "").toString()
                    File(out, "$name.json").writeText(envelope)
                    println("CAPTURED $name (alert: ${e.message})")
                } else {
                    error("$name failed: ${e.message}")
                }
            }
        }

        // En-route: 12951 MMCT→NDLS departed 17:00 yesterday, arrives ~08:00 today.
        capture("live_status_12951_22sep") { NtesApi.liveStatus("12951", "22-SEP-2026", keys) }
        // Today's run of 12952 NDLS→MMCT (departs this evening — scheduled state).
        capture("live_status_12952_23sep") { NtesApi.liveStatus("12952", "23-SEP-2026", keys) }
        capture("avg_delay_12952") { NtesApi.avgDelay("12952", keys) }
        capture("station_live_NDLS") { NtesApi.trainsAtStation("NDLS", 4, keys) }
        capture("trains_between_NDLS_MMCT") { NtesApi.trainsBetween("NDLS", "MMCT", keys = keys) }
        capture("schedule_12952") { NtesApi.trainSchedule("12952", "", keys) }
        capture("find_train_12952") { NtesApi.findTrain("12952", keys) }
        capture("exceptions_12952", allowAlert = true) { NtesApi.trainExceptions("12952", keys) }
        capture("instance_12952") { NtesApi.trainInstance("12952", keys) }
    }
}
