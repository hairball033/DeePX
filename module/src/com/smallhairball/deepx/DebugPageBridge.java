package com.smallhairball.deepx;

import android.app.Activity;
import android.os.Environment;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Data bridge for the "调试" (debug) category page rendered with Miuix inside the injected
 * DeepSeek process. The rows mirror the module's own debug page; actions are delegated to the
 * existing diagnostics UIs.
 */
public final class DebugPageBridge {

    public static final String TYPE_SWITCH = "switch";
    public static final String TYPE_LINK = "link";

    private DebugPageBridge() {}

    /** One row shown on the debug page. */
    public static final class Row {
        public final String id;
        public final String title;
        public final String subtitle;
        public final String type;
        public final boolean checked;
        /** True when the subtitle should be drawn in the warning (red) colour. */
        public final boolean warning;

        Row(String id, String title, String subtitle, String type, boolean checked, boolean warning) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.type = type;
            this.checked = checked;
            this.warning = warning;
        }
    }

    private static Row toggle(String id, String title, String subtitle, boolean checked, boolean warning) {
        return new Row(id, title, subtitle, TYPE_SWITCH, checked, warning);
    }

    private static Row link(String id, String title, String subtitle) {
        return new Row(id, title, subtitle, TYPE_LINK, false, false);
    }

    /** Builds the current debug page rows, matching the module's own debug page. */
    public static List<Row> rows() {
        List<Row> out = new ArrayList<>();
        out.add(toggle("srv_log", "记录服务器返回（诊断）", "", safeIsSrvLog(), false));
        out.add(toggle("raw_hook", "高频原始 Hook 日志",
                "无脱敏且日志量很大：可能记录消息正文、Hook 参数、返回值、文件路径、账号信息与凭据。",
                safeIsRawHook(), true));
        out.add(link("clear_raw", "清理高频原始日志", "仅清除日志文件，不改变上方开关。"));
        out.add(toggle("hook_overlay", "Hook 日志显示在屏幕", "透明显示 Hook 日志", safeIsOverlay(), false));
        out.add(link("crash_records", "记录崩溃", ""));
        out.add(link("crash_tests", "崩溃测试", ""));
        out.add(link("compat_report", "兼容性诊断报告", ""));
        out.add(link("perf_stats", "Hook 性能统计", ""));
        out.add(link("trace_export", "脱敏事件追踪与导出", ""));
        out.add(link("custom_request", "发送自定义请求", ""));
        out.add(link("log_export", "导出日志", "选择性导出模块诊断日志 ZIP，自动脱敏 API Key。"));
        out.add(link("config_export", "导出配置", ""));
        out.add(link("config_import", "导入配置", ""));
        return out;
    }

    /** Flips a switch row. Returns the resulting checked state. */
    public static boolean toggle(String id) {
        if (id == null) return false;
        switch (id) {
            case "srv_log":
                try { Main.setSrvLog(!Main.isSrvLog()); } catch (Throwable ignored) { return false; }
                return safeIsSrvLog();
            case "hook_overlay":
                try { HookLogOverlay.setEnabled(!HookLogOverlay.enabled()); } catch (Throwable ignored) { return false; }
                return safeIsOverlay();
            case "raw_hook":
                // Enabling raw tracing requires an explicit risk acknowledgement; the Compose
                // layer handles that confirmation and calls setRawHook(boolean) directly.
                return safeIsRawHook();
            default:
                return false;
        }
    }

    /** Enables/disables the no-redaction raw hook trace after the user acknowledged the risk. */
    public static boolean setRawHook(boolean enabled) {
        try {
            if (!enabled) {
                Main.setRawHookTraceEnabled(false);
                return false;
            }
            return Main.setRawHookTraceEnabled(true);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Runs a link/action row. */
    public static void action(Activity act, String id) {
        if (act == null || id == null) return;
        try {
            switch (id) {
                case "clear_raw":
                    Toast.makeText(act,
                            Main.clearRawHookTrace() ? "日志已清理" : "清理失败",
                            Toast.LENGTH_SHORT).show();
                    break;
                case "crash_records":
                    CrashDiagnosticsUi.showRecords(act);
                    break;
                case "crash_tests":
                    CrashDiagnosticsUi.showCrashTests(act);
                    break;
                case "compat_report":
                case "perf_stats":
                    // Rendered by the Compose report dialog using reportText(id).
                    break;
                case "trace_export":
                    DeveloperDiagnostics.exportTrace(act);
                    break;
                case "custom_request":
                    DeepSeekRequestDebugUi.show(act);
                    break;
                case "log_export":
                    LogExportUi.show(act);
                    break;
                case "config_export":
                    exportConfig(act);
                    break;
                case "config_import":
                    importConfig(act);
                    break;
                default:
                    break;
            }
        } catch (Throwable t) {
            try {
                Toast.makeText(act, "操作失败：" + t.getClass().getSimpleName(), Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) {}
        }
    }

    /** Returns the text body for a report-style row, or "" when the row is not a report. */
    public static String reportText(Activity act, String id) {
        if (act == null || id == null) return "";
        try {
            switch (id) {
                case "compat_report":
                    return DeveloperDiagnostics.compatibilityReport(act);
                case "perf_stats":
                    return DeveloperDiagnostics.performanceReport();
                default:
                    return "";
            }
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** Copies a report body to the system clipboard. */
    public static void copyText(Activity act, String text) {
        if (act == null || text == null) return;
        try {
            DeveloperDiagnostics.copyToClipboard(act, text);
        } catch (Throwable ignored) {}
    }

    // ── Log export ───────────────────────────────────────────────────

    /** One exportable log source exposed to the Compose log-export sheet. */
    public static final class ExportSource {
        public final String name;
        public final String description;
        public final boolean unredacted;

        ExportSource(String name, String description, boolean unredacted) {
            this.name = name;
            this.description = description;
            this.unredacted = unredacted;
        }
    }

    /** Returns the selectable log sources for the export sheet. */
    public static List<ExportSource> exportSources() {
        List<ExportSource> out = new ArrayList<>();
        try {
            for (LogExportUi.SourceInfo info : LogExportUi.sourceInfos()) {
                out.add(new ExportSource(info.name, info.description, info.unredacted));
            }
        } catch (Throwable ignored) {}
        return out;
    }

    /** True when the given source index should be checked by default. */
    public static boolean defaultExportChecked(int index) {
        return index < 2;
    }

    /** The privacy note shown above the export button. */
    public static String exportPrivacyNote() {
        try { return LogExportUi.privacyNote(); } catch (Throwable ignored) { return ""; }
    }

    /** The export button label for the current host. */
    public static String exportLabel() {
        try { return LogExportUi.exportLabel(); } catch (Throwable ignored) { return "导出"; }
    }

    /** Records the selection and launches the SAF destination picker. */
    public static void startLogExport(Activity act, boolean[] selected) {
        if (act == null || selected == null) return;
        try { LogExportUi.startExport(act, selected); } catch (Throwable ignored) {}
    }

    // ── Crash diagnostics ────────────────────────────────────────────

    /** Returns the crash report text (Java + system exit history), or "" when none. */
    public static String crashReport(Activity act) {
        if (act == null) return "";
        try { return CrashDiagnosticsUi.crashReport(act); } catch (Throwable ignored) { return ""; }
    }

    /** Saves the crash report via the SAF "create document" picker. */
    public static void saveCrashReport(Activity act, String report) {
        if (act == null || report == null) return;
        try { CrashDiagnosticsUi.saveReport(act, report); } catch (Throwable ignored) {}
    }

    /** One crash-test entry exposed to the Compose layer. */
    public static final class CrashTest {
        public final String group;
        public final String groupHint;
        public final String title;
        public final String mode;

        CrashTest(String group, String groupHint, String title, String mode) {
            this.group = group;
            this.groupHint = groupHint;
            this.title = title;
            this.mode = mode;
        }
    }

    /** Returns the crash-test entries, grouped like the legacy sheet. */
    public static List<CrashTest> crashTests() {
        List<CrashTest> out = new ArrayList<>();
        try {
            for (CrashDiagnosticsUi.TestEntry entry : CrashDiagnosticsUi.testEntries()) {
                out.add(new CrashTest(entry.group, entry.groupHint, entry.title, entry.mode));
            }
        } catch (Throwable ignored) {}
        return out;
    }

    /** Triggers a crash test for the given mode id. */
    public static void triggerCrash(Activity act, String mode) {
        if (act == null || mode == null) return;
        try { CrashDiagnosticsUi.trigger(act, mode); } catch (Throwable ignored) {}
    }

    // ── Custom request debugger ──────────────────────────────────────

    /** Default request path shown in the request console. */
    public static String requestDefaultPath() {
        try { return DeepSeekRequestDebugUi.defaultPath(); } catch (Throwable ignored) { return "/api/"; }
    }

    /** The explanatory note shown above the request inputs. */
    public static String requestNote() {
        try { return DeepSeekRequestDebugUi.note(); } catch (Throwable ignored) { return ""; }
    }

    /** Sends a debug request through the module's request pipeline. */
    public static boolean sendRequest(Activity act, String method, String path, String body) {
        if (act == null || method == null || path == null) return false;
        try { return DeepSeekRequestDebugUi.send(act, method, path, body); } catch (Throwable ignored) { return false; }
    }

    private static void exportConfig(Activity act) {
        String json = ModuleConfigBridge.exportConfigJson();
        if (json == null) {
            Toast.makeText(act, "导出失败：无法读取配置", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File dir = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "DeePX");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                Toast.makeText(act, "导出失败：无法创建目录", Toast.LENGTH_SHORT).show();
                return;
            }
            File file = new File(dir, "deepx-config.json");
            FileOutputStream out = new FileOutputStream(file);
            out.write(json.getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.close();
            Toast.makeText(act, "配置已导出：" + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(act, "导出失败：" + t.getClass().getSimpleName(), Toast.LENGTH_SHORT).show();
        }
    }

    private static void importConfig(Activity act) {
        try {
            File dir = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "DeePX");
            File file = new File(dir, "deepx-config.json");
            if (!file.isFile()) {
                Toast.makeText(act, "未找到 Download/DeePX/deepx-config.json", Toast.LENGTH_LONG).show();
                return;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            FileInputStream in = new FileInputStream(file);
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            in.close();
            String json = new String(out.toByteArray(), StandardCharsets.UTF_8);
            String result = ModuleConfigBridge.importConfigJson(json);
            Toast.makeText(act, "ok".equals(result)
                            ? "配置已导入，重启 DeepSeek 后生效"
                            : "导入失败：" + result,
                    Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(act, "导入失败：" + t.getClass().getSimpleName(), Toast.LENGTH_SHORT).show();
        }
    }

    private static boolean safeIsSrvLog() {
        try { return Main.isSrvLog(); } catch (Throwable ignored) { return false; }
    }

    private static boolean safeIsRawHook() {
        try { return Main.isRawHookTraceEnabled(); } catch (Throwable ignored) { return false; }
    }

    private static boolean safeIsOverlay() {
        try { return HookLogOverlay.enabled(); } catch (Throwable ignored) { return false; }
    }
}