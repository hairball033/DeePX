package com.smallhairball.deepx.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smallhairball.deepx.UiLanguage
import com.smallhairball.deepx.data.AppVersion
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

private const val PAGE_HOME = 0
private const val PAGE_SETTINGS = 1

/**
 * Root application shell: Miuix (Xiaomi HyperOS style) scaffold with a top app bar, a swipeable
 * [HorizontalPager] and a bottom navigation bar wired to the pager. The overflow menu lives in the
 * top app bar's actions so its popup anchors to the button. Structure mirrors the SukiSU-Ultra
 * manager (Miuix) implementation, including the optional floating liquid-glass bottom bar.
 */
@Composable
fun ModuleApp(
    targetPackage: String,
    repositoryUrl: String,
    qqGroupUrl: String,
    showFullBackup: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onOpenDeepSeek: () -> Unit,
    onLicense: () -> Unit,
    onAbout: () -> Unit,
    onOpenTheme: () -> Unit,
) {
    val context = LocalContext.current
    fun tr(zh: String, en: String): String = UiLanguage.text(context, zh, en)

    val pagerState = rememberPagerState(initialPage = PAGE_HOME, pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()

    val enableBlur = LocalEnableBlur.current
    val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
    val enableFloatingBottomBarBlur = LocalEnableFloatingBottomBarBlur.current

    // Top-bar backdrop samples the page background; the floating bar uses its own backdrop so it
    // can refract the content that scrolls behind it. Seeding the backdrop with a solid surface
    // colour first is essential: an empty backdrop would sample as transparent/black and the
    // glass pill would render as a dark blob.
    val blurBackdrop = rememberBlurBackdrop(enableBlur)
    val surfaceColor = colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    val onSelectPage: (Int) -> Unit = { page ->
        scope.launch { pagerState.animateScrollToPage(page) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            BlurredBar(blurBackdrop) {
                TopAppBar(
                    color = if (blurBackdrop != null) {
                        colorScheme.surface.copy(alpha = 0f)
                    } else {
                        colorScheme.surface
                    },
                    title = AppVersion.appName,
                    subtitle = AppVersion.subtitle(),
                    scrollBehavior = scrollBehavior,
                    actions = {
                        OverflowMenu(
                            icon = Icons.Rounded.MoreVert,
                            contentDescription = tr("更多", "More"),
                            items = listOf(
                                OverflowMenuItem(tr("关于", "About"), onAbout),
                            ),
                        )
                    },
                )
            }
        },
        bottomBar = {
            if (!enableFloatingBottomBar) {
                BlurredBar(blurBackdrop) {
                    NavigationBar(
                        color = if (blurBackdrop != null) {
                            colorScheme.surface.copy(alpha = 0f)
                        } else {
                            colorScheme.surface
                        },
                    ) {
                        NavigationBarItem(
                            modifier = Modifier.weight(1f),
                            selected = pagerState.currentPage == PAGE_HOME,
                            onClick = { onSelectPage(PAGE_HOME) },
                            icon = Icons.Rounded.Home,
                            label = tr("首页", "Home"),
                        )
                        NavigationBarItem(
                            modifier = Modifier.weight(1f),
                            selected = pagerState.currentPage == PAGE_SETTINGS,
                            onClick = { onSelectPage(PAGE_SETTINGS) },
                            icon = Icons.Rounded.Settings,
                            label = tr("设置", "Settings"),
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (enableFloatingBottomBar && enableFloatingBottomBarBlur) Modifier.layerBackdrop(backdrop) else Modifier),
            ) { page ->
                when (page) {
                    PAGE_HOME -> HomeScreen(
                        innerPadding = innerPadding,
                        scrollBehavior = scrollBehavior,
                        targetPackage = targetPackage,
                        onOpenDeepSeek = onOpenDeepSeek,
                    )

                    else -> SettingsScreen(
                        innerPadding = innerPadding,
                        scrollBehavior = scrollBehavior,
                        repositoryUrl = repositoryUrl,
                        qqGroupUrl = qqGroupUrl,
                        showFullBackup = showFullBackup,
                        onExport = onExport,
                        onImport = onImport,
                        onBackup = onBackup,
                        onRestore = onRestore,
                        onLicense = onLicense,
                        onOpenTheme = onOpenTheme,
                    )
                }
            }

            if (enableFloatingBottomBar) {
                val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val bottomPadding = if (bottomInset != 0.dp) 8.dp + bottomInset else 28.dp
                FloatingBottomBar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 28.dp, end = 28.dp, bottom = bottomPadding),
                    selectedIndex = pagerState.currentPage,
                    onSelected = onSelectPage,
                    backdrop = backdrop,
                    tabsCount = 2,
                    isBlurEnabled = enableFloatingBottomBarBlur,
                ) { activateTab ->
                    FloatingBottomBarItem(
                        selected = pagerState.currentPage == PAGE_HOME,
                        onClick = { activateTab(PAGE_HOME) },
                        modifier = Modifier.defaultMinSize(minWidth = 76.dp),
                    ) {
                        Icon(Icons.Rounded.Home, contentDescription = tr("首页", "Home"))
                        Text(tr("首页", "Home"), fontSize = 11.sp, maxLines = 1)
                    }
                    FloatingBottomBarItem(
                        selected = pagerState.currentPage == PAGE_SETTINGS,
                        onClick = { activateTab(PAGE_SETTINGS) },
                        modifier = Modifier.defaultMinSize(minWidth = 76.dp),
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = tr("设置", "Settings"))
                        Text(tr("设置", "Settings"), fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}