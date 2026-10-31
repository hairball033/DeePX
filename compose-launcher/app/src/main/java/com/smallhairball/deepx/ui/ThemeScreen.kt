package com.smallhairball.deepx.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.CallToAction
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.DesignServices
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smallhairball.deepx.UiLanguage
import com.smallhairball.deepx.data.ThemePrefs
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars

/** Preset accent colours (ARGB) offered on the personalisation page. `0` means "follow theme". */
private val KEY_COLOR_VALUES = listOf(
    0,
    0xFFF44336.toInt(), 0xFFE91E63.toInt(), 0xFF9C27B0.toInt(), 0xFF673AB7.toInt(),
    0xFF3F51B5.toInt(), 0xFF2196F3.toInt(), 0xFF00BCD4.toInt(), 0xFF009688.toInt(),
    0xFF4FAF50.toInt(), 0xFFFFEB3B.toInt(), 0xFFFFC107.toInt(), 0xFFFF9800.toInt(),
    0xFF795548.toInt(), 0xFF607D8F.toInt(), 0xFFFF9CA8.toInt(),
)

/**
 * Personalisation page (主题 / 个性化). Mirrors the SukiSU-Ultra colour palette screen with the
 * subset of options DeePX actually exposes: dark mode, Monet, accent colour, palette style,
 * colour spec, blur and the floating liquid-glass bottom bar.
 */
@Composable
fun ThemeScreen(
    innerPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    fun tr(zh: String, en: String): String = UiLanguage.text(context, zh, en)

    val scrollBehavior = MiuixScrollBehavior()

    val colorMode = ThemePrefs.colorMode
    val monetEnabled = ThemePrefs.monetEnabled
    val keyColor = ThemePrefs.keyColor
    val paletteStyle = ThemePrefs.paletteStyle
    val colorSpec = ThemePrefs.colorSpec
    val enableBlur = ThemePrefs.enableBlur
    val floatingBar = ThemePrefs.floatingBottomBar
    val floatingBarBlur = ThemePrefs.floatingBottomBarBlur

    val colorNames = listOf(
        tr("默认", "Default"),
        tr("红", "Red"), tr("粉", "Pink"), tr("紫", "Purple"), tr("深紫", "Deep Purple"),
        tr("靛蓝", "Indigo"), tr("蓝", "Blue"), tr("青", "Cyan"), tr("蓝绿", "Teal"),
        tr("绿", "Green"), tr("黄", "Yellow"), tr("琥珀", "Amber"), tr("橙", "Orange"),
        tr("棕", "Brown"), tr("蓝灰", "Blue Grey"), tr("樱", "Sakura"),
    )
    val paletteNames = ThemePaletteStyle.entries.map { it.name }
    val specNames = ThemeColorSpec.entries.map { it.name }

    Scaffold(
        topBar = {
            TopAppBar(
                title = tr("个性化", "Personalisation"),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = tr("返回", "Back"),
                            tint = colorScheme.onBackground,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(
                top = inner.calculateTopPadding() + 12.dp,
                bottom = inner.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            overscrollEffect = null,
        ) {
            item { ThemePreviewCard() }

            item {
                Column {
                    val themeItems = listOf(
                        tr("跟随系统", "System"),
                        tr("浅色", "Light"),
                        tr("深色", "Dark"),
                    )
                    TabRow(
                        tabs = themeItems,
                        selectedTabIndex = colorMode.coerceIn(0, 2),
                        onTabSelected = { ThemePrefs.setColorMode(context, it) },
                    )

                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        Column {
                            SwitchPreference(
                                title = tr("动态取色 (Monet)", "Dynamic colour (Monet)"),
                                summary = tr("从壁纸提取配色（Android 12+）", "Extract colours from the wallpaper (Android 12+)"),
                                startAction = {
                                    Icon(
                                        Icons.Rounded.Wallpaper,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 6.dp),
                                        tint = colorScheme.onBackground,
                                    )
                                },
                                checked = monetEnabled,
                                onCheckedChange = { ThemePrefs.setMonet(context, it) },
                            )
                            OverlayDropdownPreference(
                                title = tr("主题色", "Accent colour"),
                                items = colorNames,
                                startAction = {
                                    Icon(
                                        Icons.Rounded.Colorize,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 6.dp),
                                        tint = colorScheme.onBackground,
                                    )
                                },
                                selectedIndex = KEY_COLOR_VALUES
                                    .indexOf(keyColor)
                                    .takeIf { it >= 0 } ?: 0,
                                onSelectedIndexChange = { index ->
                                    ThemePrefs.setKeyColor(context, KEY_COLOR_VALUES[index])
                                },
                            )
                            // Palette style and colour spec only make sense when a palette is actually
                            // generated (Monet on, or a fixed accent colour chosen).
                            if (monetEnabled || keyColor != 0) {
                                OverlayDropdownPreference(
                                    title = tr("调色板风格", "Palette style"),
                                    items = paletteNames,
                                    startAction = {
                                        Icon(
                                            Icons.Rounded.Style,
                                            contentDescription = null,
                                            modifier = Modifier.padding(end = 6.dp),
                                            tint = colorScheme.onBackground,
                                        )
                                    },
                                    selectedIndex = paletteNames
                                        .indexOf(paletteStyle.name)
                                        .coerceAtLeast(0),
                                    onSelectedIndexChange = { index ->
                                        ThemePrefs.setPaletteStyle(context, ThemePaletteStyle.entries[index])
                                    },
                                )
                                OverlayDropdownPreference(
                                    title = tr("色彩规范", "Colour spec"),
                                    items = specNames,
                                    startAction = {
                                        Icon(
                                            Icons.Rounded.DesignServices,
                                            contentDescription = null,
                                            modifier = Modifier.padding(end = 6.dp),
                                            tint = colorScheme.onBackground,
                                        )
                                    },
                                    selectedIndex = specNames
                                        .indexOf(colorSpec.name)
                                        .coerceAtLeast(0),
                                    onSelectedIndexChange = { index ->
                                        ThemePrefs.setColorSpec(context, ThemeColorSpec.entries[index])
                                    },
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            SwitchPreference(
                                title = tr("界面模糊", "Interface blur"),
                                summary = tr("顶栏与底栏毛玻璃（Android 13+）", "Frosted top and bottom bars (Android 13+)"),
                                startAction = {
                                    Icon(
                                        Icons.Rounded.BlurOn,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 6.dp),
                                        tint = colorScheme.onBackground,
                                    )
                                },
                                checked = enableBlur,
                                enabled = true,
                                onCheckedChange = { ThemePrefs.setEnableBlur(context, it) },
                            )
                        } else {
                            SwitchPreference(
                                title = tr("界面模糊", "Interface blur"),
                                summary = tr("需要 Android 13 及以上", "Requires Android 13 or newer"),
                                startAction = {
                                    Icon(
                                        Icons.Rounded.BlurOn,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 6.dp),
                                        tint = colorScheme.onBackground,
                                    )
                                },
                                checked = false,
                                enabled = false,
                                onCheckedChange = {},
                            )
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = tr("悬浮底栏", "Floating bottom bar"),
                        summary = tr("底部导航栏以悬浮胶囊呈现", "Show the navigation bar as a floating pill"),
                        startAction = {
                            Icon(
                                Icons.Rounded.CallToAction,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp),
                                tint = colorScheme.onBackground,
                            )
                        },
                        checked = floatingBar,
                        onCheckedChange = { ThemePrefs.setFloatingBottomBar(context, it) },
                    )
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = tr("液态玻璃", "Liquid glass"),
                        summary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            tr(
                                "底栏的玻璃折射效果（需先开启悬浮底栏）",
                                "Glass refraction on the bar (enable the floating bar first)",
                            )
                        } else {
                            tr("需要 Android 13 及以上", "Requires Android 13 or newer")
                        },
                        startAction = {
                            Icon(
                                Icons.Rounded.WaterDrop,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp),
                                tint = colorScheme.onBackground,
                            )
                        },
                        checked = floatingBarBlur,
                        enabled = floatingBar && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                        onCheckedChange = { ThemePrefs.setFloatingBottomBarBlur(context, it) },
                    )
                }
            }
        }
    }
}

/** Compact live preview of the active palette: background, accent card and key-colour swatch. */
@Composable
private fun ThemePreviewCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colorScheme.primary),
                )
                Spacer(Modifier.size(14.dp))
                Column {
                    Text("DeePX Ultra", fontSize = 16.sp, color = colorScheme.onSurface)
                    Text(
                        "HyperOS · Miuix",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colorScheme.tertiaryContainer)
                    .border(1.dp, colorScheme.outline, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    "  Container preview",
                    fontSize = 12.sp,
                    color = colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}