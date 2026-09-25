package com.trainkraft.app.presentation

import android.app.Application
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kraft.ui.components.KraftTopBar
import com.kraft.ui.tokens.KraftRadius
import com.kraft.ui.tokens.KraftSpacing
import com.trainkraft.app.data.PnrApi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PnrScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val factory = remember {
        PnrViewModel.Factory(context.applicationContext as Application)
    }
    val viewModel: PnrViewModel = viewModel(factory = factory)

    val step by viewModel.step.collectAsState()
    val captchaBitmap by viewModel.captchaBitmap.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val pnrResult by viewModel.pnrResult.collectAsState()

    var pnrInput by remember { mutableStateOf("") }
    var captchaInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            KraftTopBar(
                title = when (step) {
                    PnrViewModel.Step.INPUT -> "PNR Status"
                    PnrViewModel.Step.CAPTCHA -> "Enter Captcha"
                    PnrViewModel.Step.RESULT -> "PNR Result"
                },
                navigationIcon = {
                    IconButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.goBack()
                        if (step == PnrViewModel.Step.INPUT) onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when (step) {
                PnrViewModel.Step.INPUT -> PnrInputStep(
                    pnrInput = pnrInput,
                    onPnrChange = { pnrInput = it },
                    onSubmit = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.loadCaptcha()
                    },
                    isLoading = isLoading,
                    error = error,
                )
                PnrViewModel.Step.CAPTCHA -> PnrCaptchaStep(
                    captchaBitmap = captchaBitmap,
                    captchaInput = captchaInput,
                    onCaptchaChange = { captchaInput = it },
                    onSubmit = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.submitPnr(pnrInput, captchaInput)
                    },
                    onRefresh = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        captchaInput = ""
                        viewModel.refreshCaptcha()
                    },
                    isLoading = isLoading,
                    error = error,
                )
                PnrViewModel.Step.RESULT -> PnrResultStep(
                    result = pnrResult,
                    onNewQuery = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        pnrInput = ""
                        captchaInput = ""
                        viewModel.reset()
                    },
                )
            }
        }
    }
}

@Composable
private fun PnrInputStep(
    pnrInput: String,
    onPnrChange: (String) -> Unit,
    onSubmit: () -> Unit,
    isLoading: Boolean,
    error: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(KraftSpacing.Spacing16),
        verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing16),
    ) {
        Text(
            text = "Enter your 10-digit PNR number",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = pnrInput,
            onValueChange = { value ->
                // Allow only digits, max 10
                val filtered = value.filter { it.isDigit() }.take(10)
                onPnrChange(filtered)
            },
            label = { Text("PNR Number") },
            placeholder = { Text("e.g. 2512345678") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { onSubmit() }),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(KraftRadius.Standard),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )

        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = onSubmit,
            enabled = pnrInput.length == 10 && !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(KraftRadius.Medium),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Get Captcha")
            }
        }

        Text(
            text = "Captcha will be loaded from indianrail.gov.in",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun PnrCaptchaStep(
    captchaBitmap: android.graphics.Bitmap?,
    captchaInput: String,
    onCaptchaChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onRefresh: () -> Unit,
    isLoading: Boolean,
    error: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(KraftSpacing.Spacing16),
        verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing16),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Solve the captcha to check PNR status",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Captcha image
        Surface(
            shape = RoundedCornerShape(KraftRadius.Standard),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (captchaBitmap != null) {
                Image(
                    bitmap = captchaBitmap.asImageBitmap(),
                    contentDescription = "CAPTCHA image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp)
                        .padding(KraftSpacing.Spacing16),
                )
            } else if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }
        }

        // Refresh button
        TextButton(onClick = onRefresh) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text("Refresh Captcha")
        }

        // Captcha input
        OutlinedTextField(
            value = captchaInput,
            onValueChange = { onCaptchaChange(it.uppercase()) },
            label = { Text("Enter Captcha") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { onSubmit() }),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(KraftRadius.Standard),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )

        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = onSubmit,
            enabled = captchaInput.isNotBlank() && !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(KraftRadius.Medium),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Check Status")
            }
        }
    }
}

@Composable
private fun PnrResultStep(
    result: PnrApi.PnrResult?,
    onNewQuery: () -> Unit,
) {
    if (result == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(KraftSpacing.Spacing16),
        verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing12),
    ) {
        // Journey info card
        ResultSection("Journey Info") {
            ResultRow("PNR", result.pnrNumber)
            ResultRow("Train", "${result.trainNumber} — ${result.trainName}")
            ResultRow("Date", result.dateOfJourney)
            ResultRow("From", result.sourceStation)
            ResultRow("To", result.destinationStation)
            ResultRow("Class", result.journeyClass)
            ResultRow("Quota", result.quota)
            ResultRow("Boarding", result.boardingPoint)
            ResultRow("Reservation Upto", result.reservationUpto)
            ResultRow("Chart Status", result.chartStatus)
        }

        // Passenger details
        if (result.passengers.isNotEmpty()) {
            ResultSection("Passengers") {
                result.passengers.forEach { p ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "Passenger ${p.serialNumber}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    ResultRow("Booking", p.bookingStatus.ifBlank { "—" })
                    ResultRow("Current", p.currentStatus.ifBlank { "—" })
                    if (p.currentCoachId.isNotBlank()) {
                        ResultRow("Coach", p.currentCoachId)
                    }
                }
            }
        }

        Spacer(Modifier.height(KraftSpacing.Spacing8))

        Button(
            onClick = onNewQuery,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(KraftRadius.Medium),
        ) {
            Text("Check Another PNR")
        }
    }
}

@Composable
private fun ResultSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(KraftRadius.Medium),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .cardOutline(RoundedCornerShape(KraftRadius.Medium)),
    ) {
        Column(
            modifier = Modifier.padding(KraftSpacing.Spacing16),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.6f),
        )
    }
}
