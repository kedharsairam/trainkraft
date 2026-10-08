/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.trainkraft.app.presentation

import androidx.compose.ui.unit.dp

/**
 * The dimensions that belong to this app and to no other.
 *
 * Spacing, radius, type and touch targets come from kraft-foundation and are shared across the
 * portfolio. The values here are content-area heights that would be wrong in any other app —
 * the same test that decides an app's accent belongs to the app and not to the foundation.
 *
 * RowHeight is shared by eight screens, which is the whole reason it is declared rather than
 * rounded: 52dp is "a touch target with room", and moving eight rows to 48 or 56 would
 * change the density of every list in the app for the sake of a number. The minimums keep
 * content areas from collapsing while data loads; each is the smallest height its screen
 * was designed around.
 */
object TrainMetrics {

    /**
     * Standard row height across lists and cards. Taller than the 44dp touch floor by 8dp —
     * room for two lines of text or a status dot beside one.
     */
    val RowHeight = 52.dp

    /** Home hero minimum: the greeting and the next-train card never collapse while loading. */
    val HeroMinHeight = 88.dp

    /** PNR card minimum: status rows keep their shape before results arrive. */
    val CardMinHeight = 80.dp

    /** Loading placeholder height: a centered spinner needs a box to center in. */
    val LoadingBoxHeight = 120.dp
}
