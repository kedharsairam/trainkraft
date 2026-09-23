package com.trainkraft.app.presentation

import com.trainkraft.app.data.shouldImportPack
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Agent-B-UI: pack-bootstrap import decision (pure, JVM).
 *
 * [shouldImportPack] imports when the asset is newer than the local
 * `pack_meta` (version or generation), or when local was never stamped —
 * otherwise the bootstrap is a no-op (`Current`).
 */
class PackBootstrapLogicTest {

    @Test
    fun `fresh local pack with no stamp imports`() {
        assertTrue(shouldImportPack("1", "2026-09-23T00:00:00Z", "1", "2026-09-23T00:00:00Z", null))
        assertTrue(shouldImportPack("1", "2026-09-23T00:00:00Z", null, null, ""))
    }

    @Test
    fun `matching version and generation is current`() {
        assertFalse(
            shouldImportPack(
                "1", "2026-09-23T00:00:00Z",
                "1", "2026-09-23T00:00:00Z", "1758600000000",
            ),
        )
    }

    @Test
    fun `newer asset version imports`() {
        assertFalse(
            shouldImportPack(
                "1", "2026-09-23T00:00:00Z",
                "1", "2026-09-23T00:00:00Z", "1758600000000",
            ),
        )
        assertTrue(
            shouldImportPack(
                "2", "2026-09-23T00:00:00Z",
                "1", "2026-09-23T00:00:00Z", "1758600000000",
            ),
        )
    }

    @Test
    fun `regenerated asset with same version imports`() {
        assertTrue(
            shouldImportPack(
                "1", "2026-09-24T00:00:00Z",
                "1", "2026-09-23T00:00:00Z", "1758600000000",
            ),
        )
    }
}
