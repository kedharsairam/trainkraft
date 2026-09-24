package com.trainkraft.app.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.kraft.ui.components.KraftTopBar
import com.kraft.ui.tokens.KraftSpacing

/**
 * Help, Terms and Privacy screens.
 *
 * Plain scrollable text behind [KraftTopBar] + back. Every claim is grounded
 * in the repo: permissions in `AndroidManifest.xml`, backup exclusions in
 * `res/xml/backup_rules.xml` + `data_extraction_rules.xml`, background
 * behavior in the [com.trainkraft.app.TrackingService] /
 * [com.trainkraft.app.TravelService] /
 * [com.trainkraft.app.LiveStatusNotificationWorker] KDocs, feature wording in
 * the search/train-detail/travel screens, and network endpoints in
 * `NtesApi` / `NtesConfig` / `PnrApi`.
 */
@Composable
fun HelpScreen(onBack: () -> Unit) {
    LegalScaffold(title = "Help & how to use", onBack = onBack) {
        LegalSection("Search") {
            LegalBody(
                "Type a train number, name, or station code in the search box " +
                    "(for example 12951, Rajdhani, or NDLS). Train results come " +
                    "from the timetable stored on your phone, so search works " +
                    "offline. Live running status needs the internet.",
            )
        }
        LegalSection("Follow a train (bell)") {
            LegalBody(
                "Open a train and tap the bell to follow it. The app checks " +
                    "its live status about every 15 minutes in the background " +
                    "and notifies you only when something meaningful changes: " +
                    "a worse delay, a cancellation or diversion, or arrival at " +
                    "the destination. Tap the bell again to unfollow.",
            )
        }
        LegalSection("Go-live (minute checks)") {
            LegalBody(
                "Go-live is the closer watch: the app checks the train every " +
                    "minute (every 30 seconds when it is close to a stop) while " +
                    "a foreground notification shows it is running. It stops " +
                    "when you stop it, when the journey completes, or if the " +
                    "system kills it — the 15-minute background check keeps " +
                    "running regardless. Go-live uses more battery than the bell.",
            )
        }
        LegalSection("Station alarms") {
            LegalBody(
                "On a train's page you can set an alarm for a specific stop, " +
                    "or switch on the next-stop approach watch. The alarm fires " +
                    "at the scheduled time even if the app is closed; the " +
                    "approach watch notifies once when the train is at, near, " +
                    "or about 15 minutes from your watched station. Each alarm " +
                    "fires once. Exact alarms need the Alarms permission — if " +
                    "you deny it, the app falls back to approximate timing.",
            )
        }
        LegalSection("Travel mode (on-board GPS)") {
            LegalBody(
                "Travel mode is for when you are on the train. It uses your " +
                    "phone's GPS to show speed and distance to the next stop. " +
                    "It runs only while the travel screen is open, never in " +
                    "the background, and it never sends your location anywhere " +
                    "— fixes stay on the device. It uses the most battery of " +
                    "any feature (roughly a few percent per hour); stop it " +
                    "from its notification or the travel screen when you arrive.",
            )
        }
        LegalSection("Offline behavior") {
            LegalBody(
                "The timetable, your followed trains, and recent live-status " +
                    "answers are kept on the device, so previously viewed " +
                    "trains stay readable without internet. Anything you have " +
                    "not opened before — a new train's live status, station " +
                    "board, or trains-between result — needs a connection, " +
                    "because those answers come from Indian Railways servers. " +
                    "Cached live data expires after the cache duration you set " +
                    "in Settings.",
            )
        }
    }
}

@Composable
fun TermsScreen(onBack: () -> Unit) {
    LegalScaffold(title = "Terms of use", onBack = onBack) {
        LegalSection("What this app is") {
            LegalBody(
                "TrainKraft shows Indian train timetables and live running " +
                    "status. It is an unofficial app by an independent " +
                    "developer and is not affiliated with, endorsed by, or " +
                    "connected to Indian Railways, the Centre for Railway " +
                    "Information Systems (CRIS), or any government body.",
            )
        }
        LegalSection("Personal use") {
            LegalBody(
                "Use TrainKraft for personal, non-commercial purposes only. " +
                    "Train schedule data belongs to Indian Railways; the " +
                    "TrainKraft app and its design belong to its developer. " +
                    "Open-source libraries used by the app keep their own " +
                    "licenses — see Settings > Open-source licenses.",
            )
        }
        LegalSection("Data as-is") {
            LegalBody(
                "Timetables change and live data can be delayed or wrong. " +
                    "Times, delays, platform hints, and alarms are guidance " +
                    "only: always confirm with official railway sources " +
                    "before you travel. Station alarms are a convenience, not " +
                    "a guarantee — a missed alarm is not the app's liability. " +
                    "Do not rely on the app for safety-critical decisions.",
            )
        }
        LegalSection("Fair use") {
            LegalBody(
                "Do not abuse the app: no scraping, no automated bulk " +
                    "queries, nothing that degrades Indian Railways services " +
                    "for others. Accounts do not exist and there is nothing " +
                    "to share or transfer.",
            )
        }
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    LegalScaffold(title = "Privacy policy", onBack = onBack) {
        LegalSection("The short version") {
            LegalBody(
                "No account. No servers of ours. Your trains, alarms, and " +
                    "settings stay on your phone. The app has no crash " +
                    "reporting and no analytics — nothing about how you use " +
                    "the app is collected or sent anywhere.",
            )
        }
        LegalSection("What stays on your device") {
            LegalBody(
                "Followed trains, station alarms, travel-mode GPS fixes, " +
                    "cached live-status answers, settings, and the timetable " +
                    "database all live on the device. Followed trains and " +
                    "related data are explicitly excluded from Android cloud " +
                    "backup and device-to-device transfer, so they never " +
                    "leave the phone through backups either.",
            )
        }
        LegalSection("What leaves your phone") {
            LegalBody(
                "Only the queries you ask for: live status, station boards, " +
                    "trains-between, schedules, and PNR enquiries go to " +
                    "Indian Railways servers (enquiry.indianrail.gov.in and " +
                    "indianrail.gov.in), and the app fetches a small key file " +
                    "from the project's public GitHub page to talk to them. " +
                    "These requests carry the train number, station code, or " +
                    "PNR you looked up — that is inherent to asking for the " +
                    "answer. No identity, no account, and no tracking " +
                    "identifier is attached by the app.",
            )
        }
        LegalSection("Location") {
            LegalBody(
                "Location is used only while travel mode is open, to show " +
                    "speed and distance to the next stop. Fixes are processed " +
                    "on the device and never uploaded. The app never requests " +
                    "background location access, so no location is collected " +
                    "when travel mode is not running.",
            )
        }
        LegalSection("Notifications and background work") {
            LegalBody(
                "Status checks run on your phone: a periodic background check " +
                    "about every 15 minutes for followed trains, a " +
                    "minute-level foreground check while Go-live is on, and " +
                    "scheduled alarms for station alerts. Their results feed " +
                    "notifications on the device only.",
            )
        }
        LegalSection("Permissions used") {
            LegalBody(
                "Internet (fetch live data), notification permission " +
                    "(show alerts on Android 13 and above), precise location " +
                    "only while travel mode is open, exact alarms (fire " +
                    "station alarms on time), and foreground-service entries " +
                    "for live tracking and travel mode. Each is used only " +
                    "for the feature described above.",
            )
        }
        LegalSection("Children") {
            LegalBody(
                "The app collects no personal data from anyone, including " +
                    "children. Live-status and PNR queries you type are sent " +
                    "to Indian Railways servers only to fetch the answer.",
            )
        }
        LegalSection("Contact") {
            LegalBody(
                "Questions about these terms or this policy: open an issue " +
                    "at github.com/kedharsairam/trainkraft via Settings > " +
                    "Report an issue.",
            )
        }
    }
}

@Composable
private fun LegalScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Scaffold(
        topBar = {
            KraftTopBar(
                title = title,
                navigationIcon = {
                    IconButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = KraftSpacing.Spacing16,
                    vertical = KraftSpacing.Spacing8,
                ),
        ) {
            content()
            Spacer(modifier = Modifier.height(KraftSpacing.Spacing16))
        }
    }
}

@Composable
private fun LegalSection(title: String, body: @Composable () -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = KraftSpacing.Spacing8),
    )
    Spacer(modifier = Modifier.height(4.dp))
    body()
    Spacer(modifier = Modifier.height(KraftSpacing.Spacing8))
}

@Composable
private fun LegalBody(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
