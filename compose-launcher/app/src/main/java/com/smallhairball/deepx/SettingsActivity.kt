package com.smallhairball.deepx

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smallhairball.deepx.data.AppData
import com.smallhairball.deepx.data.AppVersion
import com.smallhairball.deepx.ui.DeePXTheme
import com.smallhairball.deepx.ui.ModuleApp
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Standalone module application UI. The screen structure lives in [ModuleApp] (Miuix); this
 * Activity owns lifecycle, file/backup result plumbing and the Material 3 text dialog.
 */
class SettingsActivity : ComponentActivity() {
    companion object {
        @JvmField val VERSION: String = AppVersion.version
        private const val TARGET_PACKAGE = "com.deepseek.chat"
        private const val REPOSITORY = "https://github.com/hairball033/DeePX"
        private const val QQ_GROUP_URL = "https://qun.qq.com/universal-share/share?ac=1&authKey=IWUlWa7KL4tc%2FMtrPbuqrNepTkSzuJMh%2BJz4ZjroNpRhoNeYWKn2vL%2BAleVUq5d3&busi_data=eyJncm91cENvZGUiOiIxMTI0NDM4NjY2IiwidG9rZW4iOiJZYkNMdmU0ZXYwbEExcVIxWmdYblVoQ3pKclM2c0xJT0tpbWhCTlBUZm9iN3FRRzQwT3ZKZjhrWUlqSkhQd3F1IiwidWluIjoiMzE5MzMxNDQ5NCJ9&data=_lEL0zKr4yL8xQrp-nZbQA_mmVcjC07UHrwVpIg8Q75YmoO8CpB7TT3b6OSm-HKzoD8-09M4VAT9jgp6J4sRmg&svctype=4&tempid=h5_group_info"
    }

    private val main = Handler(Looper.getMainLooper())
    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) requestExport(uri) }
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) requestImport(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        UiLanguage.refreshSystem(this)
        setContent {
            DeePXTheme {
                ModuleApplication()
            }
        }
        // The module's own app gets the same remote announcement / update prompt. Delayed slightly so
        // it lands on top of an already drawn screen instead of fighting the first frame.
        main.postDelayed(
            {
                runCatching { RemoteNotice.check(this@SettingsActivity) }
            },
            1300L,
        )
    }

    private fun tr(zh: String, en: String): String = UiLanguage.text(this, zh, en)

    @androidx.compose.runtime.Composable
    private fun ModuleApplication() {
        var aboutOpen by remember { mutableStateOf(false) }
        var licenseOpen by remember { mutableStateOf(false) }
        val context = LocalContext.current

        ModuleApp(
            targetPackage = TARGET_PACKAGE,
            repositoryUrl = REPOSITORY,
            qqGroupUrl = QQ_GROUP_URL,
            showFullBackup = isFullBackupHostSupported(),
            onExport = { exportLauncher.launch(AppVersion.configFileName()) },
            onImport = {
                importLauncher.launch(arrayOf("application/json", "text/json", "text/plain"))
            },
            onBackup = { ClosedFullDataBackupBridge.beginBackup(this@SettingsActivity) },
            onRestore = { ClosedFullDataBackupBridge.beginRestore(this@SettingsActivity) },
            onOpenDeepSeek = {
                val launchIntent = packageManager.getLaunchIntentForPackage(TARGET_PACKAGE)
                if (launchIntent == null) {
                    toast(tr("未安装 DeepSeek", "DeepSeek is not installed"))
                } else {
                    startActivity(launchIntent)
                    main.postDelayed({
                        runCatching {
                            sendBroadcast(ModuleConfigBridge.requestOpenSettings())
                        }
                    }, 900L)
                }
            },
            onLicense = { licenseOpen = true },
            onAbout = { aboutOpen = true },
            onOpenTheme = {
                startActivity(Intent(this@SettingsActivity, ThemeActivity::class.java))
            },
        )

        LaunchedEffect(licenseOpen) {
            if (licenseOpen) {
                licenseOpen = false
                showTextDialog(context, "GNU General Public License v3.0", AppData.licenseText(context))
            }
        }
        LaunchedEffect(aboutOpen) {
            if (aboutOpen) {
                aboutOpen = false
                showTextDialog(
                    context,
                    tr("关于", "About"),
                    tr(
                        "由于原模块 Deekseep 作者因为压力问题闭源、停更，且最后开源代码未适配新版 DeepSeek。因此，我创建了此项目来继续保持开源维护并增强。\n\n模块版本：${AppVersion.displayVersion}\nDeepSeek：${AppData.deepSeekVersion(context, TARGET_PACKAGE)}",
                        "The original Deekseep module was closed-source and discontinued by its author due to pressure, and the last open-source code did not support the latest DeepSeek. Therefore, I created this project to continue open-source maintenance and enhancement.\n\nModule: ${AppVersion.displayVersion}\nDeepSeek: ${AppData.deepSeekVersion(context, TARGET_PACKAGE)}",
                    ),
                )
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == Main.FULL_DATA_BACKUP_REQUEST) {
            ClosedFullDataBackupBridge.handleExportResult(this, resultCode, data)
        } else if (requestCode == Main.FULL_DATA_RESTORE_REQUEST) {
            ClosedFullDataBackupBridge.handleRestoreResult(this, resultCode, data)
        }
    }

    /**
     * Material 3 text dialog (View-based MaterialAlertDialogBuilder). Kept as an Activity-owned
     * host so the Miuix content stays decoupled from dialog presentation.
     */
    private fun showTextDialog(context: Context, title: String, body: String) {
        val themed = androidx.appcompat.view.ContextThemeWrapper(
            context,
            R.style.Theme_DeePX_Material3,
        )
        MaterialAlertDialogBuilder(themed)
            .setTitle(title)
            .setMessage(body)
            .setPositiveButton(tr("关闭", "Close")) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun requestExport(uri: Uri) {
        val reply = object : ResultReceiver(main) {
            override fun onReceiveResult(code: Int, data: Bundle?) {
                if (code != ModuleConfigBridge.RESULT_OK) {
                    toast(tr("请先启动一次 DeepSeek，再重试", "Open DeepSeek once, then try again"))
                    return
                }
                runCatching {
                    contentResolver.openOutputStream(uri, "wt")!!.use { output ->
                        output.write(
                            data?.getString(ModuleConfigBridge.EXTRA_JSON, "")
                                .orEmpty().toByteArray(StandardCharsets.UTF_8),
                        )
                    }
                }.onSuccess { toast(tr("配置已导出", "Configuration exported")) }
                    .onFailure { toast(it.message.orEmpty()) }
            }
        }
        sendBroadcast(ModuleConfigBridge.request(ModuleConfigBridge.MODE_EXPORT, null, reply))
    }

    private fun requestImport(uri: Uri) {
        runCatching {
            val output = ByteArrayOutputStream()
            contentResolver.openInputStream(uri)!!.use { it.copyTo(output, 4096) }
            String(output.toByteArray(), StandardCharsets.UTF_8)
        }.onSuccess { json ->
            val reply = object : ResultReceiver(main) {
                override fun onReceiveResult(code: Int, data: Bundle?) {
                    toast(
                        if (code == ModuleConfigBridge.RESULT_OK) {
                            tr("配置已导入，重新打开 DeepSeek 后生效", "Imported. Reopen DeepSeek to apply.")
                        } else {
                            tr("请先启动一次 DeepSeek，再重试", "Open DeepSeek once, then try again")
                        },
                    )
                }
            }
            sendBroadcast(ModuleConfigBridge.request(ModuleConfigBridge.MODE_IMPORT, json, reply))
        }.onFailure { toast(it.message.orEmpty()) }
    }

    @Suppress("DEPRECATION")
    private fun isFullBackupHostSupported(): Boolean =
        AppData.isFullBackupHostSupported(this, TARGET_PACKAGE)

    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
}