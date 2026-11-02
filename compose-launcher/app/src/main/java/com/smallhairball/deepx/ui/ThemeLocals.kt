package com.smallhairball.deepx.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Theme + layout preferences exposed through CompositionLocals, mirroring the KernelSU / SukiSU
 * `Theme.kt` locals so the floating bottom bar and blur helpers copied from those projects keep
 * working unchanged.
 */

/** Forced colour mode: 0 = follow system, 1 = force light, 2 = force dark. */
val LocalColorMode = staticCompositionLocalOf { 0 }

/** Whether the top bar / bottom bar should be drawn with a translucent blur backdrop. */
val LocalEnableBlur = staticCompositionLocalOf { false }

/** Whether the bottom navigation bar floats as a pill above the content. */
val LocalEnableFloatingBottomBar = staticCompositionLocalOf { false }

/** Whether the floating bottom bar applies the liquid-glass blur treatment. */
val LocalEnableFloatingBottomBarBlur = staticCompositionLocalOf { false }

/** Resolves the effective dark state, honouring a forced light / dark [LocalColorMode]. */
@Composable
@ReadOnlyComposable
fun isInDarkTheme(): Boolean {
    return when (LocalColorMode.current) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
}