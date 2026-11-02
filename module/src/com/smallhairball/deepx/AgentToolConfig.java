package com.smallhairball.deepx;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Persistent policy for the small, user-visible Agent toolkit.
 *
 * <p>The file lives in DeepSeek's own private directory because this class normally executes
 * inside the injected host process. Defaults deliberately match the settings page contract:
 * Agent enabled, every listed tool enabled, and the highest permission level selected. Choosing
 * an execution backend is separate; in-app mode remains the safe initial transport until the
 * user explicitly connects Root or Shizuku.</p>
 */
final class AgentToolConfig {
    static final String BACKEND_IN_APP = "in_app";
    static final String BACKEND_ROOT = "root";
    static final String BACKEND_SHIZUKU = "shizuku";

    static final String PERMISSION_EXECUTE = "execute";
    static final String PERMISSION_ALL = "all";

    static final int PROMPT_STRENGTH_BASIC = 1;
    static final int PROMPT_STRENGTH_ENHANCED = 2;
    static final int PROMPT_STRENGTH_IMMERSIVE = 3;

    private static final String DIRECTORY =
            "/data/data/com.deepseek.chat/files/deekseep_agent";
    private static final String FILE_PATH = DIRECTORY + "/settings.json";
    private static final Object LOCK = new Object();

    private static final List<String> TOOLS;
    private static volatile Snapshot cached;
    private static volatile long cachedModified = Long.MIN_VALUE;

    static {
        ArrayList<String> tools = new ArrayList<>();
        tools.add(HeartbeatToolProtocol.TOOL_ASK_USER);
        tools.add(HeartbeatToolProtocol.TOOL_RENDER_RICH_PANEL);
        tools.add(HeartbeatToolProtocol.TOOL_MCP);
        tools.add(HeartbeatToolProtocol.TOOL_GET_CURRENT_TIME);
        tools.add(HeartbeatToolProtocol.TOOL_READ_FILE);
        tools.add(HeartbeatToolProtocol.TOOL_WRITE_FILE);
        tools.add(HeartbeatToolProtocol.TOOL_NETWORK_REQUEST);
        tools.add(HeartbeatToolProtocol.TOOL_SEARCH_WEB);
        tools.add(HeartbeatToolProtocol.TOOL_SHELL);
        tools.add(HeartbeatToolProtocol.TOOL_DELAY);
        tools.add(HeartbeatToolProtocol.TOOL_OPEN_APP);
        tools.add(HeartbeatToolProtocol.TOOL_LIST_APPS);
        tools.add(HeartbeatToolProtocol.TOOL_APP_INFO);
        tools.add(HeartbeatToolProtocol.TOOL_UNINSTALL_APP);
        tools.add(HeartbeatToolProtocol.TOOL_SCREEN_POWER);
        tools.add(HeartbeatToolProtocol.TOOL_MUSIC);
        tools.add(HeartbeatToolProtocol.TOOL_CAPTURE_SCREEN);
        tools.add(HeartbeatToolProtocol.TOOL_TAP_SCREEN);
        tools.add(HeartbeatToolProtocol.TOOL_SWIPE_SCREEN);
        tools.add(HeartbeatToolProtocol.TOOL_PRESS_BACK);
        tools.add(HeartbeatToolProtocol.TOOL_SCHEDULE_ONCE);
        tools.add(HeartbeatToolProtocol.TOOL_SET_PLAN);
        tools.add(HeartbeatToolProtocol.TOOL_CLEAR_PLAN);
        tools.add(HeartbeatToolProtocol.TOOL_SET_INTERVAL);
        tools.add(HeartbeatToolProtocol.TOOL_BIND_CHAT);
        tools.add(HeartbeatToolProtocol.TOOL_CANCEL_HEARTBEAT);
        TOOLS = Collections.unmodifiableList(tools);
    }

    private AgentToolConfig() {}

    static final class Snapshot {
        final boolean enabled;
        final String backend;
        final String permission;
        final int promptStrength;
        final boolean hideToolLogs;
        final boolean disableToolLogDetailsV236;
        final boolean disableToolLogDetailsV241;
        final boolean longContextTxt;
        final boolean collapseThinkingByDefault;
        final Set<String> enabledTools;

        Snapshot(boolean enabled, String backend, String permission,
                 Set<String> enabledTools) {
            this(enabled, backend, permission, PROMPT_STRENGTH_BASIC,
                    false, false, false, enabledTools);
        }

        Snapshot(boolean enabled, String backend, String permission,
                 int promptStrength, Set<String> enabledTools) {
            this(enabled, backend, permission, promptStrength, false, false, false,
                    enabledTools);
        }

        Snapshot(boolean enabled, String backend, String permission,
                 int promptStrength, boolean hideToolLogs,
                 Set<String> enabledTools) {
            this(enabled, backend, permission, promptStrength, hideToolLogs,
                    false, false, enabledTools);
        }

        Snapshot(boolean enabled, String backend, String permission,
                 int promptStrength, boolean hideToolLogs,
                 boolean longContextTxt, Set<String> enabledTools) {
            this(enabled, backend, permission, promptStrength, hideToolLogs,
                    longContextTxt, false, enabledTools);
        }

        Snapshot(boolean enabled, String backend, String permission,
                 int promptStrength, boolean hideToolLogs,
                 boolean longContextTxt, boolean collapseThinkingByDefault,
                 Set<String> enabledTools) {
            this(enabled, backend, permission, promptStrength, hideToolLogs,
                    false, false, longContextTxt, collapseThinkingByDefault, enabledTools);
        }

        Snapshot(boolean enabled, String backend, String permission,
                 int promptStrength, boolean hideToolLogs,
                 boolean disableToolLogDetailsV236,
                 boolean disableToolLogDetailsV241,
                 boolean longContextTxt, boolean collapseThinkingByDefault,
                 Set<String> enabledTools) {
            this.enabled = enabled;
            this.backend = cleanBackend(backend);
            this.permission = cleanPermission(permission);
            this.promptStrength = cleanPromptStrength(promptStrength);
            this.hideToolLogs = BuildInfo.PROTECTED_BUILD && hideToolLogs;
            this.disableToolLogDetailsV236 = BuildInfo.PROTECTED_BUILD
                    && HostCompat.isV236() && disableToolLogDetailsV236;
            this.disableToolLogDetailsV241 = BuildInfo.PROTECTED_BUILD
                    && HostCompat.isV241() && disableToolLogDetailsV241;
            this.longContextTxt = longContextTxt;
            this.collapseThinkingByDefault = collapseThinkingByDefault;
            LinkedHashSet<String> kept = new LinkedHashSet<>();
            if (enabledTools != null) {
                for (String tool : enabledTools) {
                    if (isKnownTool(tool)) kept.add(tool);
                }
            }
            this.enabledTools = Collections.unmodifiableSet(kept);
        }

        boolean allows(String tool) {
            return enabled && enabledTools.contains(tool);
        }

        Snapshot withEnabled(boolean value) {
            return new Snapshot(value, backend, permission,
                    promptStrength, hideToolLogs, disableToolLogDetailsV236,
                    disableToolLogDetailsV241,
                    longContextTxt, collapseThinkingByDefault, enabledTools);
        }

        Snapshot withBackend(String value) {
            return new Snapshot(enabled, value, permission,
                    promptStrength, hideToolLogs, disableToolLogDetailsV236,
                    disableToolLogDetailsV241,
                    longContextTxt, collapseThinkingByDefault, enabledTools);
        }

        Snapshot withPermission(String value) {
            return new Snapshot(enabled, backend, value,
                    promptStrength, hideToolLogs, disableToolLogDetailsV236,
                    disableToolLogDetailsV241,
                    longContextTxt, collapseThinkingByDefault, enabledTools);
        }

        Snapshot withPromptStrength(int value) {
            return new Snapshot(enabled, backend, permission,
                    value, hideToolLogs, disableToolLogDetailsV236,
                    disableToolLogDetailsV241,
                    longContextTxt, collapseThinkingByDefault, enabledTools);
        }

        Snapshot withHideToolLogs(boolean value) {
            return new Snapshot(enabled, backend, permission,
                    promptStrength, value, disableToolLogDetailsV236,
                    disableToolLogDetailsV241,
                    longContextTxt, collapseThinkingByDefault, enabledTools);
        }

        Snapshot withDisableToolLogDetailsV236(boolean value) {
            return new Snapshot(enabled, backend, permission,
                    promptStrength, hideToolLogs, value, disableToolLogDetailsV241,
                    longContextTxt, collapseThinkingByDefault, enabledTools);
        }

        Snapshot withDisableToolLogDetailsV241(boolean value) {
            return new Snapshot(enabled, backend, permission,
                    promptStrength, hideToolLogs, disableToolLogDetailsV236, value,
                    longContextTxt, collapseThinkingByDefault, enabledTools);
        }

        Snapshot withLongContextTxt(boolean value) {
            return new Snapshot(enabled, backend, permission,
                    promptStrength, hideToolLogs, disableToolLogDetailsV236,
                    disableToolLogDetailsV241,
                    value, collapseThinkingByDefault, enabledTools);
        }

        Snapshot withCollapseThinkingByDefault(boolean value) {
            return new Snapshot(enabled, backend, permission,
                    promptStrength, hideToolLogs, disableToolLogDetailsV236,
                    disableToolLogDetailsV241,
                    longContextTxt, value, enabledTools);
        }

        Snapshot withTool(String tool, boolean value) {
            LinkedHashSet<String> next = new LinkedHashSet<>(enabledTools);
            if (value) next.add(tool);
            else next.remove(tool);
            return new Snapshot(enabled, backend, permission,
                    promptStrength, hideToolLogs, disableToolLogDetailsV236,
                    disableToolLogDetailsV241,
                    longContextTxt, collapseThinkingByDefault, next);
        }
    }

    static Snapshot defaults() {
        return new Snapshot(true, BACKEND_IN_APP, PERMISSION_ALL,
                new LinkedHashSet<>(TOOLS));
    }

    static Snapshot load() {
        synchronized (LOCK) {
            File file = new File(FILE_PATH);
            long modified = file.isFile() ? file.lastModified() : -1L;
            Snapshot present = cached;
            if (present != null && modified == cachedModified) return present;
            Snapshot loaded = defaults();
            if (file.isFile() && file.length() > 0L && file.length() <= 64L * 1024L) {
                BufferedReader reader = null;
                try {
                    reader = new BufferedReader(new FileReader(file));
                    StringBuilder text = new StringBuilder((int) file.length());
                    String line;
                    while ((line = reader.readLine()) != null) text.append(line);
                    loaded = decode(text.toString());
                } catch (Throwable ignored) {
                    loaded = defaults();
                } finally {
                    if (reader != null) try { reader.close(); } catch (Throwable ignored) {}
                }
            }
            cached = loaded;
            cachedModified = modified;
            return loaded;
        }
    }

    static boolean save(Snapshot value) {
        Snapshot safe = value == null ? defaults() : value;
        synchronized (LOCK) {
            File directory = new File(DIRECTORY);
            File destination = new File(FILE_PATH);
            File temporary = new File(FILE_PATH + ".tmp");
            FileWriter writer = null;
            try {
                if (!directory.isDirectory() && !directory.mkdirs()) return false;
                writer = new FileWriter(temporary, false);
                writer.write(encode(safe));
                writer.write('\n');
                writer.flush();
                writer.close();
                writer = null;
                if (!temporary.renameTo(destination)) return false;
                cached = safe;
                cachedModified = destination.lastModified();
                return true;
            } catch (Throwable ignored) {
                return false;
            } finally {
                if (writer != null) try { writer.close(); } catch (Throwable ignored) {}
                if (temporary.exists() && !temporary.equals(destination)) {
                    try { temporary.delete(); } catch (Throwable ignored) {}
                }
            }
        }
    }

    static boolean setEnabled(boolean value) {
        boolean saved = save(load().withEnabled(value));
        if (saved && value) Main.activateOptionalHooksNow();
        return saved;
    }

    static boolean setBackend(String value) {
        return save(load().withBackend(value));
    }

    static boolean setPermission(String value) {
        return save(load().withPermission(value));
    }

    static boolean setPromptStrength(int value) {
        return save(load().withPromptStrength(value));
    }

    static boolean setHideToolLogs(boolean value) {
        return BuildInfo.PROTECTED_BUILD
                && save(load().withHideToolLogs(value));
    }

    static boolean setDisableToolLogDetailsV241(boolean value) {
        return BuildInfo.PROTECTED_BUILD && HostCompat.isV241()
                && save(load().withDisableToolLogDetailsV241(value));
    }

    static boolean setDisableToolLogDetailsV236(boolean value) {
        return BuildInfo.PROTECTED_BUILD && HostCompat.isV236()
                && save(load().withDisableToolLogDetailsV236(value));
    }

    static boolean setLongContextTxt(boolean value) {
        return save(load().withLongContextTxt(value));
    }

    static boolean setCollapseThinkingByDefault(boolean value) {
        return save(load().withCollapseThinkingByDefault(value));
    }

    static boolean collapseThinkingByDefault() {
        return load().collapseThinkingByDefault;
    }

    static boolean longContextTxt() {
        return load().longContextTxt;
    }

    static boolean hideToolLogs() {
        return BuildInfo.PROTECTED_BUILD && load().hideToolLogs;
    }

    static boolean disableToolLogDetailsV241() {
        return BuildInfo.PROTECTED_BUILD && HostCompat.isV241()
                && load().disableToolLogDetailsV241;
    }

    static boolean disableToolLogDetailsV236() {
        return BuildInfo.PROTECTED_BUILD && HostCompat.isV236()
                && load().disableToolLogDetailsV236;
    }

    static boolean setToolEnabled(String tool, boolean value) {
        if (!isKnownTool(tool)) return false;
        return save(load().withTool(tool, value));
    }

    static boolean allows(String tool) {
        if (AgentDeviceBridge.workspaceSupportedV241()
                && (HeartbeatToolProtocol.TOOL_READ_FILE.equals(tool)
                || HeartbeatToolProtocol.TOOL_WRITE_FILE.equals(tool)
                || HeartbeatToolProtocol.TOOL_SHELL.equals(tool)
                || HeartbeatToolProtocol.TOOL_DELETE_FILE.equals(tool)
                || HeartbeatToolProtocol.TOOL_TRANSFER_FILE.equals(tool))) {
            AgentDeviceBridge.WorkspacePolicyV241 policy =
                    AgentDeviceBridge.workspacePolicyV241();
            String workspaceTool = HeartbeatToolProtocol.TOOL_READ_FILE.equals(tool)
                    ? AgentDeviceBridge.WORKSPACE_TOOL_READ
                    : HeartbeatToolProtocol.TOOL_WRITE_FILE.equals(tool)
                    ? AgentDeviceBridge.WORKSPACE_TOOL_WRITE
                    : HeartbeatToolProtocol.TOOL_SHELL.equals(tool)
                    ? AgentDeviceBridge.WORKSPACE_TOOL_SHELL
                    : HeartbeatToolProtocol.TOOL_DELETE_FILE.equals(tool)
                    ? AgentDeviceBridge.WORKSPACE_TOOL_DELETE
                    : AgentDeviceBridge.WORKSPACE_TOOL_TRANSFER;
            return load().enabled && AgentDeviceBridge.workspaceEnabledV241()
                    && policy.allows(workspaceTool);
        }
        if (JavaPluginPlatform.isAgentTool(tool)) {
            return load().enabled;
        }
        if (AgentMcpManager.isDynamicTool(tool)) {
            return load().allows(HeartbeatToolProtocol.TOOL_MCP)
                    && AgentMcpManager.isToolEnabled(tool);
        }
        return isKnownTool(tool) && load().allows(tool);
    }

    /**
     * Hot-path master switch for streamed response filtering. Settings writes update
     * {@link #cached} synchronously, so checking the snapshot avoids a filesystem stat for every
     * token while execution-time policy checks continue to use {@link #allows(String)}.
     */
    static boolean enabledFast() {
        Snapshot present = cached;
        return present != null ? present.enabled : load().enabled;
    }

    static List<String> tools() {
        return TOOLS;
    }

    static boolean isKnownTool(String tool) {
        if (tool == null) return false;
        if (AgentDeviceBridge.workspaceSupportedV241()
                && (HeartbeatToolProtocol.TOOL_DELETE_FILE.equals(tool)
                || HeartbeatToolProtocol.TOOL_TRANSFER_FILE.equals(tool))) return true;
        return TOOLS.contains(tool);
    }

    static boolean isHeartbeatTool(String tool) {
        return HeartbeatToolProtocol.TOOL_SCHEDULE_ONCE.equals(tool)
                || HeartbeatToolProtocol.TOOL_SET_PLAN.equals(tool)
                || HeartbeatToolProtocol.TOOL_CLEAR_PLAN.equals(tool)
                || HeartbeatToolProtocol.TOOL_SET_INTERVAL.equals(tool)
                || HeartbeatToolProtocol.TOOL_BIND_CHAT.equals(tool)
                || HeartbeatToolProtocol.TOOL_CANCEL_HEARTBEAT.equals(tool);
    }

    static Set<String> effectiveTools(boolean heartbeatFeatureEnabled) {
        Snapshot snapshot = load();
        if (!snapshot.enabled) return Collections.emptySet();
        LinkedHashSet<String> enabled = new LinkedHashSet<>(snapshot.enabledTools);
        if (!heartbeatFeatureEnabled) {
            enabled.remove(HeartbeatToolProtocol.TOOL_SCHEDULE_ONCE);
            enabled.remove(HeartbeatToolProtocol.TOOL_SET_PLAN);
            enabled.remove(HeartbeatToolProtocol.TOOL_CLEAR_PLAN);
            enabled.remove(HeartbeatToolProtocol.TOOL_SET_INTERVAL);
            enabled.remove(HeartbeatToolProtocol.TOOL_BIND_CHAT);
            enabled.remove(HeartbeatToolProtocol.TOOL_CANCEL_HEARTBEAT);
        }
        return Collections.unmodifiableSet(enabled);
    }

    static String displayName(String tool, boolean chinese) {
        if (HeartbeatToolProtocol.TOOL_ASK_USER.equals(tool)) {
            return chinese ? "询问用户" : "Ask user";
        }
        if (HeartbeatToolProtocol.TOOL_GET_CURRENT_TIME.equals(tool)) {
            return chinese ? "获取当前时间" : "Get current time";
        }
        if (HeartbeatToolProtocol.TOOL_RENDER_RICH_PANEL.equals(tool)) {
            return chinese ? "生成富视觉" : "Render rich visual";
        }
        if (HeartbeatToolProtocol.TOOL_MCP.equals(tool)) return "MCP";
        if (HeartbeatToolProtocol.TOOL_CAPTURE_SCREEN.equals(tool)) {
            return chinese ? "获取屏幕截图" : "Capture screen";
        }
        if (HeartbeatToolProtocol.TOOL_READ_FILE.equals(tool)) {
            return chinese ? "读取文件" : "Read file";
        }
        if (HeartbeatToolProtocol.TOOL_WRITE_FILE.equals(tool)) {
            return chinese ? "写入文件" : "Write file";
        }
        if (HeartbeatToolProtocol.TOOL_NETWORK_REQUEST.equals(tool)) {
            return chinese ? "网络请求" : "Network request";
        }
        if (HeartbeatToolProtocol.TOOL_SEARCH_WEB.equals(tool)) {
            return chinese ? "联网搜索" : "Web search";
        }
        if (HeartbeatToolProtocol.TOOL_SHELL.equals(tool)) {
            return chinese ? "基础 Shell" : "Basic shell";
        }
        if (HeartbeatToolProtocol.TOOL_DELAY.equals(tool)) {
            return chinese ? "延迟执行" : "Delay execution";
        }
        if (HeartbeatToolProtocol.TOOL_OPEN_APP.equals(tool)) {
            return chinese ? "打开应用" : "Open app";
        }
        if (HeartbeatToolProtocol.TOOL_LIST_APPS.equals(tool)) {
            return chinese ? "应用列表" : "List installed apps";
        }
        if (HeartbeatToolProtocol.TOOL_APP_INFO.equals(tool)) {
            return chinese ? "应用详情" : "App details";
        }
        if (HeartbeatToolProtocol.TOOL_UNINSTALL_APP.equals(tool)) {
            return chinese ? "卸载应用" : "Uninstall app";
        }
        if (HeartbeatToolProtocol.TOOL_SCREEN_POWER.equals(tool)) {
            return chinese ? "屏幕电源" : "Screen power";
        }
        if (HeartbeatToolProtocol.TOOL_MUSIC.equals(tool)) {
            return chinese ? "播放音乐" : "Music";
        }
        if (HeartbeatToolProtocol.TOOL_TAP_SCREEN.equals(tool)) {
            return chinese ? "点击屏幕" : "Tap screen";
        }
        if (HeartbeatToolProtocol.TOOL_SWIPE_SCREEN.equals(tool)) {
            return chinese ? "滑动屏幕" : "Swipe screen";
        }
        if (HeartbeatToolProtocol.TOOL_PRESS_BACK.equals(tool)) {
            return chinese ? "返回上一层" : "Back";
        }
        if (HeartbeatToolProtocol.TOOL_SCHEDULE_ONCE.equals(tool)) {
            return chinese ? "设置一次性心跳" : "Schedule one-time heartbeat";
        }
        if (HeartbeatToolProtocol.TOOL_SET_PLAN.equals(tool)) {
            return chinese ? "更新心跳约定" : "Update heartbeat plan";
        }
        if (HeartbeatToolProtocol.TOOL_CLEAR_PLAN.equals(tool)) {
            return chinese ? "清除心跳约定" : "Clear heartbeat plan";
        }
        if (HeartbeatToolProtocol.TOOL_SET_INTERVAL.equals(tool)) {
            return chinese ? "设置心跳间隔" : "Set heartbeat interval";
        }
        if (HeartbeatToolProtocol.TOOL_BIND_CHAT.equals(tool)) {
            return chinese ? "绑定当前对话" : "Bind current chat";
        }
        if (HeartbeatToolProtocol.TOOL_CANCEL_HEARTBEAT.equals(tool)) {
            return chinese ? "取消心跳" : "Cancel heartbeat";
        }
        return tool == null ? "" : tool;
    }

    static String description(String tool, boolean chinese) {
        if (HeartbeatToolProtocol.TOOL_ASK_USER.equals(tool)) {
            return chinese ? "以底部选项卡询问，答案会作为可见消息发回当前对话"
                    : "Ask with a bottom sheet; the answer is sent visibly to this chat";
        }
        if (HeartbeatToolProtocol.TOOL_CAPTURE_SCREEN.equals(tool)) {
            return chinese ? "截取当前屏幕；外部屏幕需要 Root 或 Shizuku"
                    : "Capture the screen; external screens require Root or Shizuku";
        }
        if (HeartbeatToolProtocol.TOOL_READ_FILE.equals(tool)) {
            return chinese ? "读取文本或二进制文件片段；高权限路径需要 Root 或 Shizuku"
                    : "Read text or binary file slices; privileged paths need Root or Shizuku";
        }
        if (HeartbeatToolProtocol.TOOL_WRITE_FILE.equals(tool)) {
            return chinese ? "覆盖或追加 UTF-8 文本；高权限路径需要 Root 或 Shizuku"
                    : "Overwrite or append UTF-8 text; privileged paths need Root or Shizuku";
        }
        if (HeartbeatToolProtocol.TOOL_NETWORK_REQUEST.equals(tool)) {
            return chinese ? "默认通过 curl 发起 HTTP/HTTPS 请求并返回响应"
                    : "Send HTTP/HTTPS requests through curl and return the response";
        }
        if (HeartbeatToolProtocol.TOOL_SEARCH_WEB.equals(tool)) {
            return chinese ? "通过 Bing 搜索网页，返回标题、来源、摘要和引用地址"
                    : "Search Bing and return page titles, sources, snippets, and citation URLs";
        }
        if (HeartbeatToolProtocol.TOOL_SHELL.equals(tool)) {
            return chinese ? "使用 Android 系统 PATH 执行 which、cp、cat 等基础命令"
                    : "Run which, cp, cat, and other commands on Android's system PATH";
        }
        if (HeartbeatToolProtocol.TOOL_DELAY.equals(tool)) {
            return chinese ? "等待指定毫秒后再继续下一步，支持秒、分钟或小时级任务"
                    : "Wait for the requested milliseconds before continuing to the next step";
        }
        if (HeartbeatToolProtocol.TOOL_OPEN_APP.equals(tool)) {
            return chinese ? "按应用包名真实启动应用，需要 Root 或 Shizuku"
                    : "Actually launch an app by package name; Root or Shizuku is required";
        }
        if (HeartbeatToolProtocol.TOOL_LIST_APPS.equals(tool)) {
            return chinese ? "读取已安装应用的名称、包名和版本；不读取应用内容"
                    : "Read installed app names, packages, and versions; does not read app contents";
        }
        if (HeartbeatToolProtocol.TOOL_APP_INFO.equals(tool)) {
            return chinese ? "查询指定包名的版本、启用状态与启动入口"
                    : "Query a package's version, enabled state, and launch entry";
        }
        if (HeartbeatToolProtocol.TOOL_UNINSTALL_APP.equals(tool)) {
            return chinese ? "打开系统卸载确认页；必须由用户亲自确认，不会静默卸载"
                    : "Open the system uninstall confirmation; user approval is always required";
        }
        if (HeartbeatToolProtocol.TOOL_SCREEN_POWER.equals(tool)) {
            return chinese ? "真实熄屏或唤醒屏幕，需要 Root 或 Shizuku"
                    : "Actually sleep or wake the screen; Root or Shizuku is required";
        }
        if (HeartbeatToolProtocol.TOOL_MUSIC.equals(tool)) {
            return chinese ? "播放在线歌曲或设备中的音频文件，并控制播放状态"
                    : "Play online songs or on-device audio files and control playback";
        }
        if (HeartbeatToolProtocol.TOOL_TAP_SCREEN.equals(tool)
                || HeartbeatToolProtocol.TOOL_SWIPE_SCREEN.equals(tool)
                || HeartbeatToolProtocol.TOOL_PRESS_BACK.equals(tool)) {
            return chinese ? "应用内可直接执行，外部界面需要高权限后端"
                    : "Works in-app; external UI needs a privileged backend";
        }
        if (HeartbeatToolProtocol.TOOL_GET_CURRENT_TIME.equals(tool)) {
            return chinese ? "读取设备本地时间，精确到秒"
                    : "Read local device time to the second";
        }
        if (HeartbeatToolProtocol.TOOL_RENDER_RICH_PANEL.equals(tool)) {
            return chinese ? "绘制面板、符号图案、灯笼、仪表、流程图、像素画或数学卡片"
                    : "Draw panels, symbol art, lanterns, gauges, flows, pixel art, or math cards";
        }
        if (HeartbeatToolProtocol.TOOL_MCP.equals(tool)) {
            return chinese ? "连接外部 MCP 服务并同步可用工具"
                    : "Connect an MCP server and sync its tools";
        }
        return chinese ? "作用域固定为当前对话，不创建全局任务"
                : "Scoped to the current chat; never creates a global task";
    }

    static String encode(Snapshot snapshot) {
        try {
            JSONObject root = new JSONObject();
            root.put("version", 13);
            root.put("enabled", snapshot.enabled);
            root.put("backend", cleanBackend(snapshot.backend));
            root.put("permission", cleanPermission(snapshot.permission));
            root.put("prompt_strength", cleanPromptStrength(
                    snapshot.promptStrength));
            root.put("hide_tool_logs",
                    BuildInfo.PROTECTED_BUILD && snapshot.hideToolLogs);
            root.put("disable_tool_log_details_v241",
                    BuildInfo.PROTECTED_BUILD && HostCompat.isV241()
                            && snapshot.disableToolLogDetailsV241);
            root.put("disable_tool_log_details_v236",
                    BuildInfo.PROTECTED_BUILD && HostCompat.isV236()
                            && snapshot.disableToolLogDetailsV236);
            root.put("long_context_txt", snapshot.longContextTxt);
            root.put("collapse_thinking_by_default", snapshot.collapseThinkingByDefault);
            JSONArray tools = new JSONArray();
            for (String tool : TOOLS) {
                if (snapshot.enabledTools.contains(tool)) tools.put(tool);
            }
            root.put("enabled_tools", tools);
            return root.toString();
        } catch (Throwable ignored) {
            return "{\"version\":6,\"enabled\":true,\"backend\":\"in_app\","
                    + "\"permission\":\"all\",\"prompt_strength\":1,"
                    + "\"hide_tool_logs\":false,\"long_context_txt\":false,"
                    + "\"disable_tool_log_details_v241\":false,"
                    + "\"disable_tool_log_details_v236\":false,"
                    + "\"collapse_thinking_by_default\":false,"
                    + "\"enabled_tools\":[]}";
        }
    }

    static Snapshot decode(String value) {
        try {
            JSONObject root = new JSONObject(value == null ? "" : value);
            boolean enabled = root.optBoolean("enabled", true);
            String backend = root.optString("backend", BACKEND_IN_APP);
            String permission = root.optString("permission", PERMISSION_ALL);
            int promptStrength = cleanPromptStrength(
                    root.optInt("prompt_strength", PROMPT_STRENGTH_BASIC));
            boolean hideToolLogs = BuildInfo.PROTECTED_BUILD
                    && root.optBoolean("hide_tool_logs", false);
            boolean disableToolLogDetailsV241 = BuildInfo.PROTECTED_BUILD
                    && HostCompat.isV241()
                    && root.optBoolean("disable_tool_log_details_v241", false);
            boolean disableToolLogDetailsV236 = BuildInfo.PROTECTED_BUILD
                    && HostCompat.isV236()
                    && root.optBoolean("disable_tool_log_details_v236", false);
            boolean longContextTxt = root.optBoolean("long_context_txt", false);
            boolean collapseThinking = root.optBoolean("collapse_thinking_by_default", false);
            int version = root.optInt("version", 1);
            JSONArray array = root.optJSONArray("enabled_tools");
            LinkedHashSet<String> tools = new LinkedHashSet<>();
            if (array == null) {
                tools.addAll(TOOLS);
            } else {
                for (int index = 0; index < array.length(); index++) {
                    String tool = array.optString(index, "");
                    if (isKnownTool(tool)) tools.add(tool);
                }
                if (version < 2) {
                    tools.add(HeartbeatToolProtocol.TOOL_READ_FILE);
                    tools.add(HeartbeatToolProtocol.TOOL_WRITE_FILE);
                    tools.add(HeartbeatToolProtocol.TOOL_SHELL);
                }
                if (version < 3) {
                    tools.add(HeartbeatToolProtocol.TOOL_RENDER_RICH_PANEL);
                }
                if (version < 5) {
                    tools.add(HeartbeatToolProtocol.TOOL_DELAY);
                    tools.add(HeartbeatToolProtocol.TOOL_OPEN_APP);
                    tools.add(HeartbeatToolProtocol.TOOL_SCREEN_POWER);
                }
                if (version < 6) {
                    tools.add(HeartbeatToolProtocol.TOOL_DELAY);
                    tools.add(HeartbeatToolProtocol.TOOL_MUSIC);
                }
                if (version < 7) {
                    tools.add(HeartbeatToolProtocol.TOOL_NETWORK_REQUEST);
                }
                if (version < 8) tools.add(HeartbeatToolProtocol.TOOL_MCP);
                if (version < 11) tools.add(HeartbeatToolProtocol.TOOL_SEARCH_WEB);
            }
            return new Snapshot(enabled, backend, permission,
                    promptStrength, hideToolLogs, disableToolLogDetailsV236,
                    disableToolLogDetailsV241,
                    longContextTxt, collapseThinking, tools);
        } catch (Throwable ignored) {
            return defaults();
        }
    }

    private static String cleanBackend(String value) {
        if (BACKEND_ROOT.equals(value) || BACKEND_SHIZUKU.equals(value)) return value;
        return BACKEND_IN_APP;
    }

    private static String cleanPermission(String value) {
        return PERMISSION_EXECUTE.equals(value) ? PERMISSION_EXECUTE : PERMISSION_ALL;
    }

    private static int cleanPromptStrength(int value) {
        if (value <= PROMPT_STRENGTH_BASIC) return PROMPT_STRENGTH_BASIC;
        if (value >= PROMPT_STRENGTH_IMMERSIVE) return PROMPT_STRENGTH_IMMERSIVE;
        return PROMPT_STRENGTH_ENHANCED;
    }
}
