package com.smallhairball.deepx.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import com.smallhairball.deepx.data.ThemePrefs
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * Makes a window draw edge-to-edge with transparent system bars, so the status bar and the gesture
 * pill float on top of our own background instead of the black system bar backgrounds.
 *
 * A window that does not opt out of fitting the system windows keeps two reserved strips, and those
 * strips are painted from the window background — which for the module's full-screen dialogs is the
 * black {@code Theme.Black.*} background. Both the module app (Activity window) and every dialog
 * window the module creates need this.
 *
 * Safe to call repeatedly; everything is guarded because older APIs lack parts of it.
 */
internal fun applyEdgeToEdgeWindow(window: android.view.Window?, dark: Boolean) {
    if (window == null) return
    runCatching {
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            // Without this Android paints its own translucent scrim behind the navigation bar.
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

/**
 * DeePX-owned Miuix (Xiaomi HyperOS style) theme.
 *
 * Mirrors the KernelSU / SukiSU theme setup: Monet dynamic colour picking on Android 12+
 * (falling back to a seed colour on older devices), a configurable palette style and colour
 * spec, and a forced light / dark mode. All options come from [ThemePrefs] so the personalisation
 * page drives the whole app instantly.
 *
 * The current values are also published through [LocalColorMode] and the floating-bar locals so
 * the liquid-glass bottom bar and the blur helpers copied from KernelSU keep working unchanged.
 */
@Composable
fun DeePXTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()

    // Reload persisted preferences on first composition (and be resilient to config changes).
    remember { ThemePrefs.load(context); true }

    val colorMode = ThemePrefs.colorMode
    val monetEnabled = ThemePrefs.monetEnabled
    val paletteStyle = ThemePrefs.paletteStyle
    val colorSpec = ThemePrefs.colorSpec
    val keyColor = ThemePrefs.keyColor
    val enableBlur = ThemePrefs.enableBlur
    val floatingBar = ThemePrefs.floatingBottomBar
    val floatingBarBlur = ThemePrefs.floatingBottomBarBlur

    val darkTheme = when (colorMode) {
        ThemePrefs.MODE_LIGHT -> false
        ThemePrefs.MODE_DARK -> true
        else -> systemDark
    }

    val controller = remember(
        colorMode, monetEnabled, paletteStyle, colorSpec, keyColor, darkTheme,
    ) {
        ThemeController(
            colorSchemeMode = ThemePrefs.colorSchemeMode(),
            keyColor = if (keyColor == 0) null else Color(keyColor),
            paletteStyle = paletteStyle,
            colorSpec = colorSpec,
            isDark = darkTheme,
        )
    }

    MiuixTheme(
        controller = controller,
        content = {
            LaunchedEffect(darkTheme) {
                val window = (context as? Activity)?.window ?: return@LaunchedEffect
                applyEdgeToEdgeWindow(window, darkTheme)
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            CompositionLocalProvider(
                LocalContentColor provides colorScheme.onBackground,
                LocalColorMode provides colorMode,
                LocalEnableBlur provides enableBlur,
                LocalEnableFloatingBottomBar provides floatingBar,
                LocalEnableFloatingBottomBarBlur provides floatingBarBlur,
            ) {
                content()
            }
        },
    )
}