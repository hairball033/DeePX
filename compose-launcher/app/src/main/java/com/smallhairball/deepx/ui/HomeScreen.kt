package com.smallhairball.deepx.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.smallhairball.deepx.UiLanguage
import com.smallhairball.deepx.data.AppData
import com.smallhairball.deepx.data.AppVersion
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** Home tab: activation state, build info, device info and a shortcut into DeepSeek. */
@Composable
fun HomeScreen(
    innerPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    targetPackage: String,
    onOpenDeepSeek: () -> Unit,
) {
    val context = LocalContext.current
    fun tr(zh: String, en: String): String = UiLanguage.text(context, zh, en)

    val activation by produceState(
        initialValue = AppData.activationState(context, targetPackage),
        producer = {
            // 立即发一次当前值
            value = AppData.activationState(context, targetPackage)
            // 每 3 秒轮询：DeepSeek 可能在我们没在前台时写入了心跳
            while (true) {
                kotlinx.coroutines.delay(3000L)
                value = AppData.activationState(context, targetPackage)
            }
        },
    )
    val device = remember { AppData.deviceSnapshot(context) }

    LazyColumn(
        modifier = Modifier
            .fillMaxHeight()
            .scrollEndHaptic()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .padding(horizontal = 12.dp),
        contentPadding = innerPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        overscrollEffect = null,
    ) {
        item {
            Column(
                modifier = Modifier.padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ActivationCard(activation)
                InfoPanel(
                    icon = Icons.Filled.Info,
                    title = tr("构建信息", "Build information"),
                    rows = listOf(
                        tr("模块版本", "Module version") to AppVersion.displayVersion,
                        tr("DeepSeek 版本", "DeepSeek version") to
                            AppData.deepSeekVersion(context, targetPackage),
                        tr("兼容接口", "Compatibility") to AppVersion.apiVersion,
                        tr("构建时间", "Build date") to AppVersion.buildDate,
                    ),
                )
                InfoPanel(
                    icon = Icons.Outlined.Devices,
                    title = tr("设备信息", "Device information"),
                    rows = listOf(
                        tr("设备", "Device") to device.product,
                        tr("系统", "System") to device.android,
                        tr("处理器架构", "ABI") to device.abi,
                        tr("内核版本", "Kernel") to device.kernel,
                        tr("屏幕", "Display") to device.display,
                        tr("安全补丁", "Security patch") to device.securityPatch,
                    ),
                )
                ActionCard(
                    icon = Icons.AutoMirrored.Outlined.ExitToApp,
                    title = tr("打开 DeepSeek", "Open DeepSeek"),
                    subtitle = tr(
                        "打开 DeepSeek 并直接进入模块设置",
                        "Open DeepSeek and go straight to the module settings",
                    ),
                    onClick = onOpenDeepSeek,
                )
            }
        }
    }
}