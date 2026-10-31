package com.smallhairball.deepx.data

import android.content.Context
import android.os.Build
import android.system.Os
import com.smallhairball.deepx.UiLanguage
import com.smallhairball.deepx.XposedActivationProvider

/** Data sources backing the home page. Kept free of any Activity dependency. */
object AppData {

    private fun tr(context: Context, zh: String, en: String): String =
        UiLanguage.text(context, zh, en)

    /** DeepSeek version string, e.g. "2.4.1 (257)". */
    @Suppress("DEPRECATION")
    fun deepSeekVersion(context: Context, packageName: String): String = runCatching {
        val info = context.packageManager.getPackageInfo(packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        "${info.versionName} ($code)"
    }.getOrDefault(tr(context, "未安装", "Not installed"))

    /** Whether the protected full-data backup is supported by the current DeepSeek build. */
    @Suppress("DEPRECATION")
    fun isFullBackupHostSupported(context: Context, packageName: String): Boolean = runCatching {
        val info = context.packageManager.getPackageInfo(packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        code == 249L || code == 257L || code == 268L
    }.getOrDefault(false)

    /** Current module activation summary for the home page. */
    fun activationState(context: Context, packageName: String): ActivationState {
        val framework = XposedActivationProvider.isFrameworkConnected()
        val target = XposedActivationProvider.isTargetRecentlyActive(context)
        val active = framework || target
        return ActivationState(
            active = active,
            title = if (active) tr(context, "模块已激活", "Module active")
                else tr(context, "模块未激活", "Module inactive"),
            detail = if (active) {
                "Xposed API ${AppVersion.apiVersion} · DeepSeek ${
                    deepSeekVersion(context, packageName)
                }"
            } else {
                tr(
                    context,
                    "未检测到注入，请在 LSPosed 中启用模块并勾选 DeepSeek",
                    "No injection detected. Enable the module and select DeepSeek in LSPosed.",
                )
            },
        )
    }

    /** Snapshot of the host device for the home page. */
    fun deviceSnapshot(context: Context): DeviceSnapshot {
        val uname = runCatching { Os.uname() }.getOrNull()
        val metrics = context.resources.displayMetrics
        val product = listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val kernelRelease = uname?.release?.takeIf { it.isNotBlank() }
            ?: System.getProperty("os.version", tr(context, "未知", "Unknown"))
        val kernelMachine = uname?.machine?.takeIf { it.isNotBlank() }
        return DeviceSnapshot(
            product = product.ifBlank { Build.DEVICE },
            android = "Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}",
            abi = Build.SUPPORTED_ABIS.joinToString(", "),
            kernel = listOfNotNull(kernelRelease, kernelMachine).joinToString(" · "),
            display = "${metrics.widthPixels} × ${metrics.heightPixels} · ${metrics.densityDpi} dpi",
            securityPatch = Build.VERSION.SECURITY_PATCH.ifBlank {
                tr(context, "未知", "Unknown")
            },
        )
    }

    /** Bundled GPL-3.0 license text. */
    fun licenseText(context: Context): String = runCatching {
        val id = context.resources.getIdentifier("gpl_3_0", "raw", context.packageName)
        context.resources.openRawResource(id).bufferedReader().use { it.readText() }
    }.getOrDefault("GNU General Public License version 3")
}