package com.smallhairball.deepx;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Data bridge for the "工程" (engineering) category page rendered with Miuix inside the injected
 * DeepSeek process. The rows mirror the module's own engineering page; actions delegate to the
 * existing task console / settings UIs.
 */
public final class EngineeringPageBridge {

    public static final String TYPE_SWITCH = "switch";
    public static final String TYPE_LINK = "link";
    public static final String TYPE_TASK = "task";

    private EngineeringPageBridge() {}

    /** One row shown on the engineering page. */
    public static final class Row {
        public final String id;
        public final String title;
        public final String subtitle;
        public final String type;
        public final boolean checked;
        public final boolean hasGear;

        Row(String id, String title, String subtitle, String type, boolean checked, boolean hasGear) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.type = type;
            this.checked = checked;
            this.hasGear = hasGear;
        }
    }

    private static Row toggle(String id, String title, String subtitle, boolean checked) {
        return new Row(id, title, subtitle, TYPE_SWITCH, checked, false);
    }

    private static Row toggleGear(String id, String title, String subtitle, boolean checked) {
        return new Row(id, title, subtitle, TYPE_SWITCH, checked, true);
    }

    private static Row link(String id, String title, String subtitle) {
        return new Row(id, title, subtitle, TYPE_LINK, false, false);
    }

    private static Row task(String id, String title, String subtitle) {
        return new Row(id, title, subtitle, TYPE_TASK, false, false);
    }

    /** Builds the current engineering page rows, matching the module's own engineering page. */
    public static List<Row> rows(Activity act) {
        List<Row> out = new ArrayList<>();
        out.add(toggle("reply_notify", "回复完成通知",
                "切到后台后，模型完成回答时发送系统通知。", safeIsReplyNotify()));
        out.add(task("backup_now", "立即备份聊天数据库",
                "复制全部聊天数据库到应用外部目录。"));
        out.add(toggle("auto_backup", "自动备份聊天数据库", "每日自动备份", safeIsAutoBackup()));
        out.add(link("language", "语言", languageSummary(act)));
        out.add(toggle("swipe_settings", "左滑进入设置",
                "开启后，从界面右侧内缘向左滑动即可打开 DeePX 设置。手势只观察触摸，不会阻断 DeepSeek 原有滑动或系统返回手势。",
                safeIsSwipe()));
        out.add(toggle("dual_chat", "双开模式",
                "从界面右侧内缘向左滑，打开两个独立模型会话。开启后此手势优先于“左滑进入设置”。",
                safeIsDualChat()));
        out.add(toggle("hot_update", "禁用热更新 · 实验性",
                "阻止 DeepSeek 展示普通或强制更新弹窗；不影响商店手动更新。", safeIsHotUpdate()));
        out.add(link("gray_features", "灰度功能管理器 · 实验性",
                "使用 DeepSeek 原生设置覆盖层；保留宿主自带的全部灰度项目。"));
        out.add(toggleGear("cache_cleaner", "自动清理缓存", cacheCleanerDetail(), safeIsCacheCleaner()));
        out.add(link("process_manager", "进程管理 · Root",
                "查看 DeepSeek 与模块进程，并精确冻结、解冻或杀死。"));
        out.add(link("agent", "Agent · 实验性",
                "管理本地工具、权限模式以及 Root / Shizuku 后端。"));
        out.add(link("java_plugin", "Java 插件 · 实验性",
                "导入、启停和测试 Java 插件；查看 ABI 教程与 Hook 点目录。"));
        return out;
    }

    /** Flips a switch row. Returns the resulting checked state. */
    public static boolean toggle(String id) {
        if (id == null) return false;
        switch (id) {
            case "reply_notify":
                try { Main.setReplyReadyNotificationsEnabled(!Main.isReplyReadyNotificationsEnabled()); } catch (Throwable ignored) { return false; }
                return safeIsReplyNotify();
            case "auto_backup":
                try { Main.setAutoBackup(!Main.isAutoBackup()); } catch (Throwable ignored) { return false; }
                return safeIsAutoBackup();
            case "swipe_settings":
                try { Main.setSwipeSettingsEnabled(!Main.isSwipeSettingsEnabled()); } catch (Throwable ignored) { return false; }
                return safeIsSwipe();
            case "dual_chat":
                try { Main.setDualChatEnabled(!Main.isDualChatEnabled()); } catch (Throwable ignored) { return false; }
                return safeIsDualChat();
            case "hot_update":
                try { Main.setHotUpdateDisabled(!Main.isHotUpdateDisabled()); } catch (Throwable ignored) { return false; }
                return safeIsHotUpdate();
            case "cache_cleaner":
                try { DeepSeekCacheCleaner.setEnabled(!DeepSeekCacheCleaner.isEnabled()); } catch (Throwable ignored) { return false; }
                return safeIsCacheCleaner();
            default:
                return false;
        }
    }

    /** Runs a link/action/task row. */
    public static void action(Activity act, String id) {
        if (act == null || id == null) return;
        try {
            switch (id) {
                case "backup_now":
                    TaskExecutionUi.show(act, "立即备份聊天数据库",
                            "备份不会修改原数据库；完成后日志会显示保存目录。",
                            new TaskExecutionUi.Task() {
                                @Override public String run(TaskExecutionUi.Logger logger) throws Throwable {
                                    return DeePXTools.backupForConsole(act, logger);
                                }
                            });
                    break;
                case "language":
                    // Language picking is handled inside DeePXUi with a follow-up page refresh;
                    // the Compose layer forwards back here so the legacy picker can run.
                    DeePXUi.showLanguagePickerBridge(act);
                    break;
                case "gray_features":
                    RemoteFeatureFlagsUi.show(act);
                    break;
                case "process_manager":
                    startProcessManager(act);
                    break;
                case "agent":
                    AgentSettingsUi.show(act);
                    break;
                case "java_plugin":
                    JavaPluginUi.show(act);
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

    /** Opens the cache-cleaner retention picker (gear row). */
    public static void openCacheCleanerGear(Activity act) {
        if (act == null) return;
        try {
            DeePXUi.showCacheCleanerDaysPickerBridge(act);
        } catch (Throwable ignored) {}
    }

    // ── Language picker (Compose-side Miuix) ─────────────────────────

    /** One selectable language option. */
    public static final class LangOption {
        public final String mode;
        public final String title;
        public final String description;

        LangOption(String mode, String title, String description) {
            this.mode = mode;
            this.title = title;
            this.description = description;
        }
    }

    /** The language options, matching the legacy picker. */
    public static List<LangOption> languageOptions() {
        List<LangOption> out = new ArrayList<>();
        out.add(new LangOption("auto", "跟随 DeepSeek（自动）",
                "DeepSeek 为中文时使用中文；其他任何语言使用英文。"));
        out.add(new LangOption("chinese", "Chinese", "始终显示中文"));
        out.add(new LangOption("english", "English", "始终显示英文"));
        return out;
    }

    /** The currently selected language mode id ("auto" / "chinese" / "english"). */
    public static String currentLanguageMode(Activity act) {
        try { return act == null ? "auto" : UiLanguage.currentMode(act); } catch (Throwable ignored) { return "auto"; }
    }

    /** Persists the selected language mode. Returns true on success. */
    public static boolean setLanguageMode(Activity act, String mode) {
        if (act == null || mode == null) return false;
        try { return UiLanguage.setMode(act, mode); } catch (Throwable ignored) { return false; }
    }

    // ── Cache-cleaner retention picker (Compose-side Miuix) ──────────

    /** One selectable retention option. */
    public static final class CacheOption {
        public final int days;
        public final String label;

        CacheOption(int days, String label) {
            this.days = days;
            this.label = label;
        }
    }

    /** The cache retention options: 3 / 7 / 30 days. */
    public static List<CacheOption> cacheOptions() {
        List<CacheOption> out = new ArrayList<>();
        out.add(new CacheOption(3, "保留 3 天"));
        out.add(new CacheOption(7, "保留 7 天"));
        out.add(new CacheOption(30, "保留 30 天"));
        return out;
    }

    /** The current retention window in days. */
    public static int currentCacheDays() {
        try { return DeepSeekCacheCleaner.days(); } catch (Throwable ignored) { return 7; }
    }

    /** Persists the selected retention window. Returns true on success. */
    public static boolean setCacheDays(int days) {
        try { return DeepSeekCacheCleaner.setDays(days); } catch (Throwable ignored) { return false; }
    }

    /** The current language summary shown as the row subtitle. */
    public static String languageSummary(Activity act) {
        try { return act == null ? "" : UiLanguage.effectiveSummary(act); } catch (Throwable ignored) { return ""; }
    }

    /** The cache-cleaner description with the current retention window. */
    public static String cacheCleanerDetail() {
        try {
            return "每天检查一次，仅清理 DeepSeek 已确认的图片、Markdown 图表缓存中超过 "
                    + DeepSeekCacheCleaner.days() + " 天的文件。";
        } catch (Throwable ignored) {
            return "每天检查一次，仅清理 DeepSeek 已确认的图片、Markdown 图表缓存。";
        }
    }

    private static void startProcessManager(Activity act) {
        try {
            Intent intent = new Intent();
            String componentPackage = RuntimeEmbeddingMode.componentPackage(Main.SELF);
            intent.setClassName(componentPackage, componentPackage + ".ProcessManagerActivity");
            act.startActivity(intent);
        } catch (Throwable error) {
            Toast.makeText(act, "无法打开进程管理，请确认模块 APK 已更新", Toast.LENGTH_LONG).show();
        }
    }

    private static boolean safeIsReplyNotify() {
        try { return Main.isReplyReadyNotificationsEnabled(); } catch (Throwable ignored) { return false; }
    }

    private static boolean safeIsAutoBackup() {
        try { return Main.isAutoBackup(); } catch (Throwable ignored) { return false; }
    }

    private static boolean safeIsSwipe() {
        try { return Main.isSwipeSettingsEnabled(); } catch (Throwable ignored) { return false; }
    }

    private static boolean safeIsDualChat() {
        try { return Main.isDualChatEnabled(); } catch (Throwable ignored) { return false; }
    }

    private static boolean safeIsHotUpdate() {
        try { return Main.isHotUpdateDisabled(); } catch (Throwable ignored) { return false; }
    }

    private static boolean safeIsCacheCleaner() {
        try { return DeepSeekCacheCleaner.isEnabled(); } catch (Throwable ignored) { return false; }
    }
}