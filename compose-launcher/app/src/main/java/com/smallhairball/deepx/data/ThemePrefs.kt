package com.smallhairball.deepx.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

/**
 * Lightweight, SharedPreferences-backed store for the personalisation options exposed on the
 * theme settings page. Values are mirrored in Compose state so the whole app reacts instantly
 * without restarting the activity.
 */
object ThemePrefs {

    private const val FILE = "deepx_theme"
    private const val KEY_COLOR_MODE = "color_mode"
    private const val KEY_PALETTE_STYLE = "palette_style"
    private const val KEY_COLOR_SPEC = "color_spec"
    private const val KEY_KEY_COLOR = "key_color"
    private const val KEY_MONET = "monet"
    private const val KEY_ENABLE_BLUR = "enable_blur"
    private const val KEY_FLOATING_BAR = "floating_bottom_bar"
    private const val KEY_FLOATING_BAR_BLUR = "floating_bottom_bar_blur"

    /** Colour mode values. Kept int-based so they round-trip through the local store. */
    const val MODE_SYSTEM = 0
    const val MODE_LIGHT = 1
    const val MODE_DARK = 2

    /** Default accent colour. `0` means "follow the theme / Monet". */
    const val DEFAULT_KEY_COLOR = 0

    var colorMode by mutableIntStateOf(MODE_SYSTEM)
        private set
    var monetEnabled by mutableStateOf(false)
        private set
    var paletteStyle by mutableStateOf(ThemePaletteStyle.TonalSpot)
        private set
    var colorSpec by mutableStateOf(ThemeColorSpec.Spec2025)
        private set
    var keyColor by mutableIntStateOf(DEFAULT_KEY_COLOR)
        private set
    var enableBlur by mutableStateOf(false)
        private set
    var floatingBottomBar by mutableStateOf(false)
        private set
    var floatingBottomBarBlur by mutableStateOf(false)
        private set

    fun load(context: Context) {
        val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        colorMode = sp.getInt(KEY_COLOR_MODE, MODE_SYSTEM)
        monetEnabled = sp.getBoolean(KEY_MONET, false)
        keyColor = sp.getInt(KEY_KEY_COLOR, DEFAULT_KEY_COLOR)
        enableBlur = sp.getBoolean(KEY_ENABLE_BLUR, false)
        floatingBottomBar = sp.getBoolean(KEY_FLOATING_BAR, false)
        floatingBottomBarBlur = sp.getBoolean(KEY_FLOATING_BAR_BLUR, false)
        paletteStyle = sp.getString(KEY_PALETTE_STYLE, null)
            ?.let { runCatching { ThemePaletteStyle.valueOf(it) }.getOrNull() }
            ?: ThemePaletteStyle.TonalSpot
        colorSpec = sp.getString(KEY_COLOR_SPEC, null)
            ?.let { runCatching { ThemeColorSpec.valueOf(it) }.getOrNull() }
            ?: ThemeColorSpec.Spec2025
    }

    private inline fun edit(context: Context, block: android.content.SharedPreferences.Editor.() -> Unit) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().apply(block).apply()
    }

    fun setColorMode(context: Context, value: Int) {
        colorMode = value
        edit(context) { putInt(KEY_COLOR_MODE, value) }
    }

    fun setMonet(context: Context, enabled: Boolean) {
        monetEnabled = enabled
        edit(context) { putBoolean(KEY_MONET, enabled) }
    }

    fun setKeyColor(context: Context, value: Int) {
        keyColor = value
        edit(context) { putInt(KEY_KEY_COLOR, value) }
    }

    fun setPaletteStyle(context: Context, style: ThemePaletteStyle) {
        paletteStyle = style
        edit(context) { putString(KEY_PALETTE_STYLE, style.name) }
    }

    fun setColorSpec(context: Context, spec: ThemeColorSpec) {
        colorSpec = spec
        edit(context) { putString(KEY_COLOR_SPEC, spec.name) }
    }

    fun setEnableBlur(context: Context, enabled: Boolean) {
        enableBlur = enabled
        edit(context) { putBoolean(KEY_ENABLE_BLUR, enabled) }
    }

    fun setFloatingBottomBar(context: Context, enabled: Boolean) {
        floatingBottomBar = enabled
        if (!enabled) {
            floatingBottomBarBlur = false
            edit(context) { putBoolean(KEY_FLOATING_BAR_BLUR, false) }
        }
        edit(context) { putBoolean(KEY_FLOATING_BAR, enabled) }
    }

    fun setFloatingBottomBarBlur(context: Context, enabled: Boolean) {
        floatingBottomBarBlur = enabled
        edit(context) { putBoolean(KEY_FLOATING_BAR_BLUR, enabled) }
    }

    /** Resolves the Miuix colour scheme mode for the current preferences. */
    fun colorSchemeMode(): ColorSchemeMode = when (colorMode) {
        MODE_LIGHT -> ColorSchemeMode.Light
        MODE_DARK -> ColorSchemeMode.Dark
        // A fixed accent colour needs a Monet mode so Miuix generates the palette from that seed;
        // empty key colour + Monet on = follow the wallpaper; otherwise plain system light/dark.
        else -> if (keyColor != 0 || monetEnabled) ColorSchemeMode.MonetSystem else ColorSchemeMode.System
    }
}