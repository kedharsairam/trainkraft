package com.trainkraft.app.presentation

import androidx.test.core.app.ApplicationProvider
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
        viewModel = PnrViewModel(ApplicationProvider.getApplicationContext())
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
