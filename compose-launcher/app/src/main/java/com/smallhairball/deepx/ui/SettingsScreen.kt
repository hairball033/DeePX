package com.smallhairball.deepx.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.smallhairball.deepx.BuildInfo
import com.smallhairball.deepx.UiLanguage
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** Settings tab: config import/export, full-data backup, community links and license. */
@Composable
fun SettingsScreen(
    innerPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    repositoryUrl: String,
    qqGroupUrl: String,
    showFullBackup: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onLicense: () -> Unit,
    onOpenTheme: () -> Unit,
) {
    val context = LocalContext.current
    fun tr(zh: String, en: String): String = UiLanguage.text(context, zh, en)

    LazyColumn(
        modifier = Modifier
            .fillMaxHeight()
            .scrollEndHaptic()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .padding(horizontal = 12.dp),
        contentPadding = innerPadding,
        overscrollEffect = null,
    ) {
        item {
            Column(
                modifier = Modifier.padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ActionCard(
                    icon = Icons.Rounded.Palette,
                    title = tr("个性化", "Personalisation"),
                    subtitle = tr("主题、配色、动态取色与悬浮底栏", "Theme, colours, Monet and the floating bottom bar"),
                    onClick = onOpenTheme,
                )
                ActionCard(
                    icon = Icons.Outlined.ArrowUpward,
                    title = tr("导出配置", "Export configuration"),
                    subtitle = tr("保存当前功能开关", "Save current feature settings"),
                    onClick = onExport,
                )
                ActionCard(
                    icon = Icons.Outlined.ArrowDownward,
                    title = tr("导入配置", "Import configuration"),
                    subtitle = tr("恢复已保存的功能开关", "Restore saved feature settings"),
                    onClick = onImport,
                )
                if (BuildInfo.PROTECTED_BUILD && showFullBackup) {
                    ActionCard(
                        icon = Icons.Outlined.ArrowUpward,
                        title = tr("一键备份全部数据", "Back up all data"),
                        subtitle = tr(
                            "加密保存模块配置、DeepSeek 数据、聊天记录与日志",
                            "Encrypt module settings, DeepSeek data, chats, and logs",
                        ),
                        onClick = onBackup,
                    )
                    ActionCard(
                        icon = Icons.Outlined.ArrowDownward,
                        title = tr("一键恢复全部数据", "Restore all data"),
                        subtitle = tr(
                            "校验备份后覆盖当前私有数据，需要 Root",
                            "Verify and replace current private data; Root required",
                        ),
                        onClick = onRestore,
                    )
                }
                ActionCard(
                    icon = Icons.Outlined.Forum,
                    title = tr("交流群", "Community group"),
                    subtitle = tr(
                        "加入 QQ 群获取更新与支持",
                        "Join the QQ group for updates and support",
                    ),
                    onClick = { openUrl(context, qqGroupUrl) },
                )
                ActionCard(
                    icon = Icons.Outlined.Code,
                    title = tr("GitHub 仓库", "GitHub repository"),
                    subtitle = repositoryUrl,
                    onClick = { openUrl(context, repositoryUrl) },
                )
                if (!BuildInfo.PROTECTED_BUILD) {
                    ActionCard(
                        icon = Icons.Outlined.Gavel,
                        title = tr("开源声明", "Open-source notice"),
                        subtitle = "GNU General Public License v3.0",
                        onClick = onLicense,
                    )
                }
            }
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(url),
            ),
        )
    }
}