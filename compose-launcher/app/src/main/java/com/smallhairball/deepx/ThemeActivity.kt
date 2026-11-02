package com.smallhairball.deepx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import com.smallhairball.deepx.ui.DeePXTheme
import com.smallhairball.deepx.ui.ThemeScreen

/**
 * Dedicated Activity hosting the personalisation (theme) page.
 *
 * Running it as its own Activity keeps the theme screen fully decoupled from [SettingsActivity]
 * and lets every change take effect immediately: [DeePXTheme] reads its options from
 * [com.smallhairball.deepx.data.ThemePrefs] Compose state, so editing a setting recomposes the
 * whole screen without restarting the process.
 */
class ThemeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        UiLanguage.refreshSystem(this)
        setContent {
            DeePXTheme {
                ThemeScreen(
                    innerPadding = PaddingValues(0.dp),
                    onBack = { finish() },
                )
            }
        }
    }
}