package com.smallhairball.deepx;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;

import java.util.ArrayList;
import java.util.List;

/**
 * Bridge between the Compose "Agent" settings page and {@link AgentToolConfig}. Exposes the
 * workspace snapshot, the tool matrix and the execution-backend state as plain data so the
 * Compose layer can render Miuix cards without reaching into host internals.
 */
public final class AgentPageBridge {
    private AgentPageBridge() {}

    /** One togglable tool row. */
    public static final class ToolRow {
        public final String id;
        public final String title;
        public final String subtitle;
        public boolean checked;
        public final boolean isMcp;

        ToolRow(String id, String title, String subtitle, boolean checked, boolean isMcp) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.checked = checked;
            this.isMcp = isMcp;
        }
    }

    /** A header metric (backend / tool count / permission). */
    public static final class Metric {
        public final String label;
        public final String value;

        Metric(String label, String value) {
            this.label = label;
            this.value = value;
        }
    }

    /** A selectable execution backend. */
    public static final class Backend {
        public final String id;
        public final String label;

        Backend(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    /** Current state of the workspace card. */
    public static final class Snapshot {
        public final boolean enabled;
        public final String statusBadge;
        public final boolean statusActive;
        public final List<Metric> metrics;
        public final String note;
        public final int promptStrength;
        public final String promptLevel;
        public final String promptDescription;
        public final String backend;
        public final List<Backend> backends;
        public final String backendDetail;
        public final String permission;
        public final String permissionLabel;
        public final String permissionDescription;

        Snapshot(boolean enabled, String statusBadge, boolean statusActive, List<Metric> metrics,
                 String note, int promptStrength, String promptLevel, String promptDescription,
                 String backend, List<Backend> backends, String backendDetail, String permission,
                 String permissionLabel, String permissionDescription) {
            this.enabled = enabled;
            this.statusBadge = statusBadge;
            this.statusActive = statusActive;
            this.metrics = metrics;
            this.note = note;
            this.promptStrength = promptStrength;
            this.promptLevel = promptLevel;
            this.promptDescription = promptDescription;
            this.backend = backend;
            this.backends = backends;
            this.backendDetail = backendDetail;
            this.permission = permission;
            this.permissionLabel = permissionLabel;
            this.permissionDescription = permissionDescription;
        }
    }

    public static Snapshot snapshot(Activity activity) {
        if (activity == null) {
            return null;
        }
        AgentToolConfig.Snapshot initial = AgentToolConfig.load();
        List<Metric> metrics = new ArrayList<>();
        metrics.add(new Metric(UiLanguage.text(activity, "执行后端", "BACKEND"),
                backendShortLabel(activity, initial.backend)));
        metrics.add(new Metric(UiLanguage.text(activity, "已启用工具", "TOOLS"),
                String.valueOf(initial.enabledTools.size())));
        metrics.add(new Metric(UiLanguage.text(activity, "权限", "ACCESS"),
                permissionLabel(activity, initial.permission)));

        List<Backend> backends = new ArrayList<>();
        backends.add(new Backend(AgentToolConfig.BACKEND_IN_APP,
                UiLanguage.text(activity, "应用内", "In app")));
        backends.add(new Backend(AgentToolConfig.BACKEND_ROOT, "Root"));
        backends.add(new Backend(AgentToolConfig.BACKEND_SHIZUKU, "Shizuku"));

        return new Snapshot(
                initial.enabled,
                UiLanguage.text(activity, initial.enabled ? "● 运行中" : "○ 已停用",
                        initial.enabled ? "● Active" : "○ Inactive"),
                initial.enabled,
                metrics,
                UiLanguage.text(activity,
                        "工具只作用于发起调用的当前对话，不会串到其他窗口。",
                        "Tools remain scoped to the chat that invoked them."),
                initial.promptStrength,
                promptStrengthLabel(activity, initial.promptStrength),
                promptStrengthDescription(activity, initial.promptStrength),
                initial.backend,
                backends,
                agentBackendDetail(activity, initial.backend),
                initial.permission,
                permissionLabel(activity, initial.permission),
                permissionDescription(activity, initial.permission));
    }

    /** Toggles the master Agent switch. */
    public static boolean setEnabled(Activity activity, boolean value) {
        boolean ok = AgentToolConfig.setEnabled(value);
        if (ok && value && activity != null) {
            try { Main.resumeAgentOutbox(activity); } catch (Throwable ignored) {}
        }
        return ok;
    }

    /** Writes the tool prompt intensity (1..3). */
    public static boolean setPromptStrength(int level) {
        return AgentToolConfig.setPromptStrength(level);
    }

    /** Turns a single tool on or off. */
    public static boolean setToolEnabled(String tool, boolean value) {
        return AgentToolConfig.setToolEnabled(tool, value);
    }

    /** The tool matrix, filtered exactly like the native page. */
    public static List<ToolRow> tools(Activity activity) {
        List<ToolRow> out = new ArrayList<>();
        if (activity == null) {
            return out;
        }
        AgentToolConfig.Snapshot initial = AgentToolConfig.load();
        boolean chinese = UiLanguage.isChinese(activity);
        boolean v241 = false;
        try { v241 = AgentDeviceBridge.workspaceSupportedV241(); } catch (Throwable ignored) {}
        for (String tool : AgentToolConfig.tools()) {
            if (v241 && (HeartbeatToolProtocol.TOOL_READ_FILE.equals(tool)
                    || HeartbeatToolProtocol.TOOL_WRITE_FILE.equals(tool)
                    || HeartbeatToolProtocol.TOOL_SHELL.equals(tool))) {
                continue;
            }
            boolean isMcp = HeartbeatToolProtocol.TOOL_MCP.equals(tool);
            String subtitle = AgentToolConfig.description(tool, chinese);
            if (isMcp) {
                subtitle = mcpLine(activity) + "    " + subtitle;
            }
            out.add(new ToolRow(
                    tool,
                    AgentToolConfig.displayName(tool, chinese),
                    subtitle,
                    initial.enabledTools.contains(tool),
                    isMcp));
        }
        return out;
    }

    /** MCP connection summary shown on the MCP row. */
    public static String mcpLine(Context context) {
        try {
            AgentMcpManager.Status status = AgentMcpManager.status();
            if (status.state == AgentMcpManager.State.CONNECTED) {
                return UiLanguage.text(context, "已连接 · " + status.toolCount + " 个工具",
                        "Connected · " + status.toolCount + " tools");
            }
        } catch (Throwable ignored) {}
        return UiLanguage.text(context, "未连接", "Not connected");
    }

    /** Opens the MCP settings surface. */
    public static void openMcp(Activity activity) {
        if (activity == null) return;
        try { AgentMcpUi.show(activity); } catch (Throwable ignored) {}
    }

    /** Opens the run-history surface. */
    public static void openRunHistory(Activity activity) {
        if (activity == null) return;
        try { AgentRunUi.show(activity); } catch (Throwable ignored) {}
    }

    /** Opens the advanced options surface. */
    public static void openAdvanced(Activity activity) {
        if (activity == null) return;
        try { AgentSettingsUi.showAdvancedBridge(activity); } catch (Throwable ignored) {}
    }

    /**
     * Probes and (on success) switches the execution backend. The callback runs on the main
     * thread.
     */
    public static void selectBackend(Activity activity, String backend,
            AgentDeviceBridge.StatusCallback callback) {
        if (activity == null) return;
        AgentDeviceBridge.probe(activity, backend, new AgentDeviceBridge.StatusCallback() {
            @Override public void onStatus(AgentDeviceBridge.Status status) {
                if (status.connected) {
                    AgentToolConfig.setBackend(backend);
                }
                if (callback != null) callback.onStatus(status);
            }
        });
    }

    /** Result listener for {@link #selectBackendSimple}, safe to call from the Compose layer. */
    public interface BackendListener {
        void onResult(boolean connected, String detail);
    }

    /**
     * Probes and (on success) switches the execution backend, reporting the outcome through a
     * public listener so the Compose layer does not need access to the package-private status
     * type.
     */
    public static void selectBackendSimple(Activity activity, String backend,
            BackendListener listener) {
        if (activity == null) return;
        AgentDeviceBridge.probe(activity, backend, new AgentDeviceBridge.StatusCallback() {
            @Override public void onStatus(AgentDeviceBridge.Status status) {
                if (status.connected) {
                    AgentToolConfig.setBackend(backend);
                }
                if (listener != null) listener.onResult(status.connected, status.detail);
            }
        });
    }

    /** Permission options for the access picker. */
    public static List<Backend> permissionOptions(Activity activity) {
        List<Backend> out = new ArrayList<>();
        if (activity == null) return out;
        out.add(new Backend(AgentToolConfig.PERMISSION_EXECUTE,
                permissionLabel(activity, AgentToolConfig.PERMISSION_EXECUTE)));
        out.add(new Backend(AgentToolConfig.PERMISSION_ALL,
                permissionLabel(activity, AgentToolConfig.PERMISSION_ALL)));
        return out;
    }

    /** Writes the access permission level. */
    public static boolean setPermission(String value) {
        return AgentToolConfig.setPermission(value);
    }

    /** Detail copy for the currently selected backend. */
    public static String agentBackendDetail(Context context, String backend) {
        if (AgentToolConfig.BACKEND_ROOT.equals(backend)) {
            return UiLanguage.text(context, "以 Root 身份执行系统级工具。",
                    "Runs system-level tools as Root.");
        }
        if (AgentToolConfig.BACKEND_SHIZUKU.equals(backend)) {
            return UiLanguage.text(context, "通过 Shizuku 以 ADB 权限执行。",
                    "Executes via Shizuku with ADB privileges.");
        }
        return UiLanguage.text(context, "仅在应用内执行，不申请系统权限。",
                "Runs inside the app without system privileges.");
    }

    static String backendShortLabel(Context context, String backend) {
        if (AgentToolConfig.BACKEND_ROOT.equals(backend)) return "Root";
        if (AgentToolConfig.BACKEND_SHIZUKU.equals(backend)) return "Shizuku";
        return UiLanguage.text(context, "应用内", "In app");
    }

    static String permissionLabel(Context context, String value) {
        if (AgentToolConfig.PERMISSION_ALL.equals(value)) {
            return UiLanguage.text(context, "全部允许", "Allow all");
        }
        return UiLanguage.text(context, "仅执行", "Execute only");
    }

    static String permissionDescription(Context context, String value) {
        if (AgentToolConfig.PERMISSION_ALL.equals(value)) {
            return UiLanguage.text(context,
                    "允许执行、读取与写入类工具。", "Allows execute, read and write tools.");
        }
        return UiLanguage.text(context,
                "仅允许执行类工具，不含文件读写。", "Execute-class tools only; no file I/O.");
    }

    static String promptStrengthLabel(Context context, int value) {
        if (value == AgentToolConfig.PROMPT_STRENGTH_IMMERSIVE) {
            return UiLanguage.text(context, "第三档 · 沉浸", "Level 3 · Immersive");
        }
        if (value == AgentToolConfig.PROMPT_STRENGTH_ENHANCED) {
            return UiLanguage.text(context, "第二档 · 增强", "Level 2 · Enhanced");
        }
        return UiLanguage.text(context, "第一档 · 基础", "Level 1 · Basic");
    }

    static String promptStrengthDescription(Context context, int value) {
        if (value == AgentToolConfig.PROMPT_STRENGTH_IMMERSIVE) {
            return UiLanguage.text(context,
                    "积极提示工具，可能影响无关话题的回答风格。",
                    "Actively suggests tools; may affect unrelated replies.");
        }
        if (value == AgentToolConfig.PROMPT_STRENGTH_ENHANCED) {
            return UiLanguage.text(context,
                    "在需要工具时主动提示，兼顾自然对话。",
                    "Suggests tools when useful while keeping replies natural.");
        }
        return UiLanguage.text(context,
                "仅在用户明确要求或实测未能满足需要时启用。",
                "Only when explicitly required or when answers fall short.");
    }

    /** Launches the Shizuku manager app when the Shizuku backend cannot connect. */
    static void openShizukuManager(Activity activity) {
        if (activity == null) return;
        try {
            Intent intent = activity.getPackageManager()
                    .getLaunchIntentForPackage("moe.shizuku.privileged.api");
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
            }
        } catch (ActivityNotFoundException ignored) {
        } catch (Throwable ignored) {}
    }

    /** One Agent run record prepared for the Compose layer. */
    public static final class RunRow {
        public final String outboxId;
        public final String title;
        public final String state;
        public final String metadata;
        public final String detail;
        public final boolean pending;
        public final String stateKind;

        RunRow(String outboxId, String title, String state, String metadata, String detail,
               boolean pending, String stateKind) {
            this.outboxId = outboxId;
            this.title = title;
            this.state = state;
            this.metadata = metadata;
            this.detail = detail;
            this.pending = pending;
            this.stateKind = stateKind;
        }
    }

    /** Summary of the run-history page. */
    public static final class RunSummary {
        public final String headline;
        public final String explanation;
        public final int pending;
        public final int finished;
        public final List<RunRow> rows;

        RunSummary(String headline, String explanation, int pending, int finished,
                   List<RunRow> rows) {
            this.headline = headline;
            this.explanation = explanation;
            this.pending = pending;
            this.finished = finished;
            this.rows = rows;
        }
    }

    /** Reads the run-history snapshot for the Compose layer. */
    public static RunSummary runSummary(Activity activity) {
        if (activity == null) return null;
        List<AgentRunStore.Record> records = AgentRunStore.snapshot();
        int active = 0;
        int pending = 0;
        int finished = 0;
        for (AgentRunStore.Record record : records) {
            if (record.hasPendingResult()) pending++;
            else if (record.isFinished()) finished++;
            else active++;
        }
        List<RunRow> rows = new ArrayList<>();
        boolean chinese = UiLanguage.isChinese(activity);
        java.text.SimpleDateFormat format =
                new java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault());
        for (AgentRunStore.Record record : records) {
            String time = format.format(new java.util.Date(record.updatedAt));
            String metadata = time + "  ·  " + compactId(record.scope)
                    + "  ·  " + compactId(record.callId);
            String detail = record.detail != null ? record.detail : "";
            if (AgentRunStore.STATE_CANCELLED.equals(record.state)) detail = "";
            rows.add(new RunRow(
                    record.outboxId,
                    AgentToolConfig.displayName(record.tool, chinese),
                    runStateLabel(activity, record.state),
                    metadata,
                    detail,
                    record.hasPendingResult(),
                    runStateKind(record.state)));
        }
        return new RunSummary(
                UiLanguage.text(activity,
                        "执行中 " + active + " · 待回传 " + pending + " · 已结束 " + finished,
                        "Active " + active + " · Pending delivery " + pending
                                + " · Finished " + finished),
                UiLanguage.text(activity,
                        "工具结果会绑定原对话持久保存。切回对应对话后会自动继续；进程重启不会自动重做有副作用的操作。",
                        "Tool results remain bound to their original chat. They resume when that "
                                + "chat is available, and side effects are never repeated after a restart."),
                pending,
                finished,
                rows);
    }

    /** Resumes the outbox and re-sends every pending result. Returns the queued count. */
    public static int retryAllPending(Activity activity) {
        if (activity == null) return 0;
        int count = 0;
        for (AgentRunStore.Record record : AgentRunStore.pending()) {
            if (Main.retryAgentOutbox(activity, record.outboxId)) count++;
        }
        return count;
    }

    /** Clears finished run records. Returns how many were removed. */
    public static int clearFinishedRuns() {
        return Main.clearFinishedAgentRuns();
    }

    /** Retries a single result. */
    public static boolean retryRun(Activity activity, String outboxId) {
        if (activity == null) return false;
        return Main.retryAgentOutbox(activity, outboxId);
    }

    /** Cancels a single pending result delivery. */
    public static void cancelRun(String outboxId) {
        if (outboxId == null) return;
        Main.cancelAgentOutbox(outboxId);
    }

    /** Reloads the outbox before the Compose layer re-reads the snapshot. */
    public static void resumeOutbox(Activity activity) {
        if (activity == null) return;
        try { Main.resumeAgentOutbox(activity); } catch (Throwable ignored) {}
    }

    /** Cancel-delivery confirmation copy. */
    public static String cancelRunMessage(Context context) {
        return UiLanguage.text(context,
                "工具不会重新执行，但模型也不会收到这条结果并继续当前 Agent 循环。",
                "The tool will not run again, but the model will not receive this result or "
                        + "continue the current Agent loop.");
    }

    static String runStateLabel(Context context, String state) {
        if (AgentRunStore.STATE_EXECUTING.equals(state)) {
            return UiLanguage.text(context, "执行中", "Running");
        }
        if (AgentRunStore.STATE_WAITING_USER.equals(state)) {
            return UiLanguage.text(context, "等待回答", "Waiting for answer");
        }
        if (AgentRunStore.STATE_RESULT_READY.equals(state)) {
            return UiLanguage.text(context, "等待回传", "Pending delivery");
        }
        if (AgentRunStore.STATE_DELIVERING.equals(state)) {
            return UiLanguage.text(context, "正在回传", "Delivering");
        }
        if (AgentRunStore.STATE_WAITING_CHAT.equals(state)) {
            return UiLanguage.text(context, "等待原对话", "Waiting for chat");
        }
        if (AgentRunStore.STATE_COMPLETED.equals(state)) {
            return UiLanguage.text(context, "已完成", "Completed");
        }
        if (AgentRunStore.STATE_FAILED.equals(state)) {
            return UiLanguage.text(context, "失败", "Failed");
        }
        return UiLanguage.text(context, "已取消", "Cancelled");
    }

    /** Coarse state bucket for colouring: "error" | "done" | "active". */
    static String runStateKind(String state) {
        if (AgentRunStore.STATE_FAILED.equals(state)
                || AgentRunStore.STATE_CANCELLED.equals(state)) {
            return "error";
        }
        if (AgentRunStore.STATE_COMPLETED.equals(state)) return "done";
        return "active";
    }

    static String compactId(String value) {
        String safe = value == null ? "" : value;
        return safe.length() <= 14 ? safe : safe.substring(0, 14) + "…";
    }

    /** One advanced-options switch. */
    public static final class AdvancedRow {
        public final String id;
        public final String title;
        public final String subtitle;
        public final boolean checked;

        AdvancedRow(String id, String title, String subtitle, boolean checked) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.checked = checked;
        }
    }

    static String advancedTitle(Context context) {
        return UiLanguage.text(context, "高级选项", "Advanced options");
    }

    static String advancedSubtitle(Context context) {
        return UiLanguage.text(context, "显示、上下文与思考链默认行为",
                "Display, context, and thinking defaults");
    }

    /** Reads the advanced-options switch matrix. */
    public static List<AdvancedRow> advancedRows(Activity activity) {
        List<AdvancedRow> out = new ArrayList<>();
        if (activity == null) return out;
        AgentToolConfig.Snapshot current = AgentToolConfig.load();
        if (BuildInfo.PROTECTED_BUILD) {
            out.add(new AdvancedRow("hide_tool_logs",
                    UiLanguage.text(activity, "隐藏工具调用日志", "Hide tool invocation logs"),
                    UiLanguage.text(activity, "不显示调用记录，但工具仍会执行",
                            "Hide invocation records while tools continue to run"),
                    current.hideToolLogs));
            if (HostCompat.isV236() || HostCompat.isV241()) {
                out.add(new AdvancedRow("disable_log_details",
                        UiLanguage.text(activity, "禁用调用日志详细窗",
                                "Disable invocation detail window"),
                        UiLanguage.text(activity,
                                "保留调用日志卡片，点击时不再打开输入和输出详情",
                                "Keep invocation cards but do not open input/output details on tap"),
                        HostCompat.isV236()
                                ? current.disableToolLogDetailsV236
                                : current.disableToolLogDetailsV241));
            }
        }
        out.add(new AdvancedRow("long_context_txt",
                UiLanguage.text(activity, "长上下文转 TXT", "Long context to TXT"),
                UiLanguage.text(activity,
                        "超过 8000 字时后台打包早期上下文；聊天中不显示文件",
                        "Package earlier context in the background beyond 8,000 characters"),
                current.longContextTxt));
        out.add(new AdvancedRow("collapse_thinking",
                UiLanguage.text(activity, "默认收起思考链", "Collapse thinking by default"),
                UiLanguage.text(activity,
                        "仍保留完整思考内容；每条新思考默认收起，可随时在原生界面展开",
                        "Keeps the complete thinking content; new blocks start collapsed and remain expandable"),
                current.collapseThinkingByDefault));
        return out;
    }

    /** Flips a single advanced switch. Returns the resulting checked state. */
    public static boolean setAdvanced(Activity activity, String id, boolean value) {
        if (id == null) return false;
        if ("hide_tool_logs".equals(id)) {
            return AgentToolConfig.setHideToolLogs(value);
        }
        if ("disable_log_details".equals(id)) {
            if (HostCompat.isV236()) return AgentToolConfig.setDisableToolLogDetailsV236(value);
            if (HostCompat.isV241()) return AgentToolConfig.setDisableToolLogDetailsV241(value);
            return false;
        }
        if ("long_context_txt".equals(id)) {
            return AgentToolConfig.setLongContextTxt(value);
        }
        if ("collapse_thinking".equals(id)) {
            return AgentToolConfig.setCollapseThinkingByDefault(value);
        }
        return false;
    }
}