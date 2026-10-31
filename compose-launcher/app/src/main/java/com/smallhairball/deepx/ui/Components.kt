package com.smallhairball.deepx.ui

import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smallhairball.deepx.data.ActivationState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * Faint diagonal author watermark. Kept at a very low alpha so it is imperceptible during normal
 * use but still captured in screenshots (暗水印). Increase the alpha argument to make it more
 * visible, decrease it to make it fainter.
 */
fun Modifier.watermarkOverlay(): Modifier = drawWithContent {
    drawContent()
    val text = "Smallhairball"
    val paint = Paint().apply {
        color = android.graphics.Color.argb(18, 128, 128, 128)
        textSize = 14f * density
        isAntiAlias = true
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val spacingX = 220f * density
    val spacingY = 140f * density
    val native = drawContext.canvas.nativeCanvas
    native.save()
    native.rotate(-30f, size.width / 2f, size.height / 2f)
    var y = -spacingY
    while (y < size.height + spacingY) {
        var x = -spacingX
        while (x < size.width + spacingX) {
            native.drawText(text, x, y, paint)
            x += spacingX
        }
        y += spacingY
    }
    native.restore()
}

/**
 * Big status block inspired by SukiSU's home status card: a large tinted container with a huge
 * semi-transparent state icon anchored to the bottom-end corner. Colours come from the active
 * Miuix [colorScheme] roles, so the whole thing follows the Monet dynamic palette.
 */
@Composable
fun ActivationCard(state: ActivationState) {
    val container = if (state.active) colorScheme.tertiaryContainer else colorScheme.errorContainer
    val onContainer = if (state.active) colorScheme.onTertiaryContainer else colorScheme.onErrorContainer
    val accent = if (state.active) colorScheme.primary else colorScheme.error

    Card(
        modifier = Modifier.fillMaxWidth(),
        pressFeedbackType = PressFeedbackType.Tilt,
        colors = CardDefaults.defaultColors(
            color = container,
            contentColor = onContainer,
        ),
    ) {
        Box {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(27.dp, 31.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Icon(
                    imageVector = if (state.active) {
                        Icons.Rounded.CheckCircleOutline
                    } else {
                        Icons.Rounded.ErrorOutline
                    },
                    contentDescription = null,
                    modifier = Modifier.size(110.dp),
                    tint = accent.copy(alpha = 0.8f),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 18.dp),
            ) {
                Text(
                    text = state.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = onContainer,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.detail,
                    fontSize = 14.sp,
                    color = onContainer.copy(alpha = 0.78f),
                    modifier = Modifier.padding(end = 72.dp),
                )
            }
        }
    }
}

/** Small rounded status pill used to decorate titles with a short state label. */
@Composable
fun StatusTag(
    label: String,
    backgroundColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(
            color = backgroundColor,
            shape = RoundedCornerShape(6.dp),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = contentColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/**
 * Vertical info block: an icon, a title and its value, laid out like SukiSU's home info rows.
 * Rows are stacked inside one [Card] instead of one card per row, which keeps the page dense
 * and calm.
 */
@Composable
fun InfoPanel(icon: ImageVector, title: String, rows: List<Pair<String, String>>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(14.dp))
                Text(title, style = MiuixTheme.textStyles.title4)
            }
            Spacer(Modifier.height(16.dp))
            rows.forEachIndexed { index, row ->
                InfoText(
                    label = row.first,
                    value = row.second,
                    bottomPadding = if (index == rows.lastIndex) 0.dp else 20.dp,
                )
            }
        }
    }
}

/** One label/value row: small label on top, the value underneath in a softer colour. */
@Composable
private fun InfoText(label: String, value: String, bottomPadding: Dp) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = bottomPadding)) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MiuixTheme.textStyles.body1,
            color = colorScheme.onSurface,
        )
    }
}

/** Clickable card with a leading icon, a title and a subtitle. */
@Composable
fun ActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        showIndication = true,
        pressFeedbackType = PressFeedbackType.Sink,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MiuixTheme.textStyles.title4)
                Text(
                    text = subtitle,
                    style = MiuixTheme.textStyles.footnote1,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}