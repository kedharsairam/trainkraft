package com.trainkraft.app.presentation

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.trainkraft.app.data.PnrApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PnrViewModelTest {

    private lateinit var viewModel: PnrViewModel

    @Before
    fun setup() {
        // Injected fakes: unit tests must never hit the real PNR endpoint.
        viewModel = PnrViewModel(
            ApplicationProvider.getApplicationContext(),
            fetchCaptchaFn = { Result.success(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)) },
            refreshCaptchaFn = { Result.success(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)) },
            queryPnrFn = { _, _ ->
                Result.success(
                    PnrApi.PnrResult(
                        pnrNumber = "1234567890",
                        trainNumber = "12952",
                        trainName = "MUMBAI RAJDHANI EXP",
                        dateOfJourney = "23-09-2026",
                        sourceStation = "NDLS",
                        destinationStation = "MMCT",
                        boardingPoint = "NDLS",
                        reservationUpto = "MMCT",
                        journeyClass = "3A",
                        chartStatus = "CHART NOT PREPARED",
                        quota = "GN",
                        passengers = emptyList(),
                        rawJson = "{}",
                    )
                )
            },
            resetSessionFn = {},
        )
    }

    @Test
    fun `submitPnr with 10 digits sets no error`() = runTest {
        viewModel.submitPnr("1234567890", "")
        assertNull(viewModel.error.value)
    }

    @Test
    fun `submitPnr with fewer than 10 digits sets error`() = runTest {
        viewModel.submitPnr("12345", "")
        assertEquals("PNR must be exactly 10 digits", viewModel.error.value)
    }

    @Test
    fun `submitPnr with letters sets error`() = runTest {
        viewModel.submitPnr("12345abcde", "")
        assertEquals("PNR must be exactly 10 digits", viewModel.error.value)
    }

    @Test
    fun `submitPnr with empty string sets error`() = runTest {
        viewModel.submitPnr("", "")
        assertEquals("PNR must be exactly 10 digits", viewModel.error.value)
    }

    @Test
    fun `submitPnr with spaces validates 10 digits`() = runTest {
        viewModel.submitPnr("  1234567890  ", "")
        assertNull(viewModel.error.value)
    }

    @Test
    fun `submitPnr with special characters sets error`() = runTest {
        viewModel.submitPnr("12345-6789", "")
        assertEquals("PNR must be exactly 10 digits", viewModel.error.value)
    }
}
