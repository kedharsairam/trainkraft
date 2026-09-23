package com.trainkraft.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Phase E travel route: builder mirrors trainDetail validation exactly. */
@RunWith(RobolectricTestRunner::class)
class TravelRouteTest {

    @Test
    fun `travel builds the route trimmed`() {
        assertEquals("travel/12951", TrainKraftDestinations.travel("12951"))
        assertEquals("travel/12951", TrainKraftDestinations.travel("  12951  "))
    }

    @Test
    fun `travel rejects the same inputs trainDetail rejects`() {
        val bad = listOf("", "   ", "NDLS", "12", "1234567", "12A51")
        for (input in bad) {
            assertThrows(IllegalArgumentException::class.java) {
                TrainKraftDestinations.travel(input)
            }
            assertThrows(IllegalArgumentException::class.java) {
                TrainKraftDestinations.trainDetail(input)
            }
        }
    }

    @Test
    fun `travel route pattern carries the trainNumber arg`() {
        assertEquals("travel/{trainNumber}", TrainKraftDestinations.TRAVEL_ROUTE)
        assertTrue(TrainKraftDestinations.isValidTrainNumber("12951"))
    }
}
