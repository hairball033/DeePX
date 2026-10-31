package com.smallhairball.deepx.ui

import android.os.Build
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Material 3 license dialog rendered inside the injected DeepSeek process through
 * [DeePXHostLauncher.showLicenseDialog].
 *
 * The colour scheme mirrors the module app's own MD3 dialog theme
 * ({@code Theme.Material3.DynamicColors}): on Android 12+ the Monet wallpaper palette is used so
 * this dialog matches the in-app one, with the static M3 baseline as the pre-12 fallback.
 */
@Composable
fun LicenseAlertDialog(
    title: String,
    body: String,
    closeLabel: String,
    dark: Boolean,
    githubLabel: String? = null,
    onGithub: (() -> Unit)? = null,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme) {
        AlertDialog(
            onDismissRequest = onClose,
            confirmButton = {
                TextButton(onClick = onClose) {
                    Text(text = closeLabel, fontSize = 15.sp)
                }
            },
            dismissButton = if (githubLabel != null && onGithub != null) {
                {
                    TextButton(onClick = onGithub) {
                        Text(text = githubLabel, fontSize = 15.sp)
                    }
                }
            } else null,
            title = {
                Text(text = title, fontSize = 18.sp, textAlign = TextAlign.Start)
            },
            text = {
                Text(
                    text = body,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                )
            },
        )
    }
}