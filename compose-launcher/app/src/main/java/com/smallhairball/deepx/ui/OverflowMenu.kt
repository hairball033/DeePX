package com.smallhairball.deepx.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayListPopup

/**
 * Top-bar overflow action: an [IconButton] plus a Miuix [OverlayListPopup] anchored to it.
 *
 * The button and the popup must live in the same composable subtree (and inside the Miuix
 * [top.yukonga.miuix.kmp.basic.Scaffold]) so the popup can resolve the button as its anchor.
 */
@Composable
fun OverflowMenu(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    items: List<OverflowMenuItem>,
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(
        onClick = { expanded = true },
        holdDownState = expanded,
    ) {
        Icon(icon, contentDescription)
    }

    OverlayListPopup(
        show = expanded,
        popupPositionProvider = DeePXPopupDefaults.MenuPositionProvider,
        alignment = PopupPositionProvider.Align.TopEnd,
        onDismissRequest = { expanded = false },
    ) {
        ListPopupColumn {
            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .clickable {
                            expanded = false
                            item.onClick()
                        }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Text(text = item.label)
                }
            }
        }
    }
}

/** A single entry inside [OverflowMenu]. */
class OverflowMenuItem(
    val label: String,
    val onClick: () -> Unit,
)