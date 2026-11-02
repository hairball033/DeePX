package com.smallhairball.deepx;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded persistent truth for graphical Agent tool rows.
 *
 * The conversation card is emitted before the tool finishes, so its immutable Markdown payload
 * can only carry the call identity and input. Results are joined here by the same conversation +
 * call id and read when the user opens the detail sheet. Keeping this store bounded avoids
 * retaining large file/MCP responses for the lifetime of a long-running DeepSeek process.
 */
final class AgentToolTraceStore {
    private static final String STORE_PATH =
            "/data/data/com.deepseek.chat/files/dq_agent_tool_traces.json";
    private static final int MAX_STORE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_PERSISTED_RECORDS = 96;
    private static final int MAX_RECORDS = 192;
    private static final int MAX_INPUT_CHARS = 16 * 1024;
    private static final int MAX_OUTPUT_CHARS = 48 * 1024;
    private static final Object LOCK = new Object();
    private static final LinkedHashMap<String, Trace> TRACES = new LinkedHashMap<>();
    private static final ArrayDeque<String> ORDER = new ArrayDeque<>();
    private static boolean loaded;

    static final class Trace {
        final String key;
        final String input;
        final String output;
        final boolean finished;
        final boolean success;
        final long startedAt;
        final long finishedAt;

        Trace(String key, String input, String output, boolean finished,
              boolean success, long startedAt, long finishedAt) {
            this.key = key == null ? "" : key;
            this.input = input == null ? "{}" : input;
            this.output = output == null ? "" : output;
            this.finished = finished;
            this.success = success;
            this.startedAt = startedAt;
            this.finishedAt = finishedAt;
        }
    }

    private AgentToolTraceStore() {}

    static String key(HeartbeatToolProtocol.ToolCall call) {
        if (call == null) return "";
        String scope = HeartbeatToolProtocol.cleanScope(call.scope);
        String id = call.id == null ? "" : call.id.trim();
        return scope.length() == 0 || id.length() == 0 ? "" : scope + "\u001f" + id;
    }

    static String inputJson(HeartbeatToolProtocol.ToolCall call) {
        if (call == null) return "{}";
        try {
            JSONObject input = new JSONObject();
            if (AgentMcpManager.isDynamicTool(call.tool)) {
                String raw = call.content == null ? "" : call.content.trim();
                if (raw.length() == 0) return "{}";
                try { return trim(new JSONObject(raw).toString(2), MAX_INPUT_CHARS); }
                catch (Throwable ignored) { return trim(raw, MAX_INPUT_CHARS); }
            }
            if (HeartbeatToolProtocol.TOOL_SCHEDULE_ONCE.equals(call.tool)) {
                input.put("at", call.at).put("instruction", call.instruction);
            } else if (HeartbeatToolProtocol.TOOL_SET_PLAN.equals(call.tool)) {
                input.put("instruction", call.instruction);
            } else if (HeartbeatToolProtocol.TOOL_SET_INTERVAL.equals(call.tool)) {
                input.put("minutes", call.minutes);
            } else if (HeartbeatToolProtocol.TOOL_CANCEL_HEARTBEAT.equals(call.tool)) {
                input.put("mode", call.mode).put("target_id", call.targetId);
            } else if (HeartbeatToolProtocol.TOOL_TAP_SCREEN.equals(call.tool)) {
                input.put("x", call.x).put("y", call.y);
            } else if (HeartbeatToolProtocol.TOOL_SWIPE_SCREEN.equals(call.tool)) {
                input.put("x", call.x).put("y", call.y)
                        .put("to_x", call.toX).put("to_y", call.toY)
                        .put("duration_ms", call.durationMs);
            } else if (HeartbeatToolProtocol.TOOL_OPEN_APP.equals(call.tool)) {
                input.put("package", call.targetId);
            } else if (HeartbeatToolProtocol.TOOL_SCREEN_POWER.equals(call.tool)) {
                input.put("mode", call.mode);
            } else if (HeartbeatToolProtocol.TOOL_ASK_USER.equals(call.tool)) {
                JSONArray questions = new JSONArray();
                for (HeartbeatToolProtocol.Question question : call.questions) {
                    JSONObject item = new JSONObject().put("text", question.text);
                    item.put("options", new JSONArray(question.options));
                    questions.put(item);
                }
                input.put("questions", questions);
            } else if (HeartbeatToolProtocol.TOOL_READ_FILE.equals(call.tool)) {
                input.put("path", call.path).put("offset", call.offset)
                        .put("max_bytes", call.maxBytes);
            } else if (HeartbeatToolProtocol.TOOL_WRITE_FILE.equals(call.tool)) {
                input.put("path", call.path).put("content", call.content)
                        .put("append", call.append)
                        .put("create_parents", call.createParents);
            } else if (HeartbeatToolProtocol.TOOL_NETWORK_REQUEST.equals(call.tool)) {
                input.put("method", call.mode).put("url", call.path);
                if (call.instruction.length() > 0) input.put("headers", call.instruction);
                if (call.content.length() > 0) input.put("body", call.content);
                if (call.timeoutMs > 0) input.put("timeout_ms", call.timeoutMs);
            } else if (HeartbeatToolProtocol.TOOL_SEARCH_WEB.equals(call.tool)) {
                input.put("query", call.instruction);
            } else if (HeartbeatToolProtocol.TOOL_SHELL.equals(call.tool)) {
                input.put("command", call.command);
                if (call.timeoutMs > 0) input.put("timeout_ms", call.timeoutMs);
            } else if (HeartbeatToolProtocol.TOOL_DELAY.equals(call.tool)) {
                input.put("duration_ms", call.durationMs);
            } else if (HeartbeatToolProtocol.TOOL_MUSIC.equals(call.tool)) {
                input.put("action", call.mode).put("provider", call.targetId);
                if (call.instruction.length() > 0) input.put("query", call.instruction);
                if (call.path.length() > 0) input.put("path", call.path);
            } else if (HeartbeatToolProtocol.TOOL_RENDER_RICH_PANEL.equals(call.tool)) {
                input.put("title", call.instruction).put("panel", call.content);
            }
            return trim(input.toString(2), MAX_INPUT_CHARS);
        } catch (Throwable ignored) {
            return "{}";
        }
    }

    static void begin(HeartbeatToolProtocol.ToolCall call) {
        String key = key(call);
        if (key.length() == 0) return;
        synchronized (LOCK) {
            ensureLoadedLocked();
            Trace old = TRACES.get(key);
            if (old != null && old.finished) return;
            putLocked(key, new Trace(key, inputJson(call), "", false, false,
                    System.currentTimeMillis(), 0L));
        }
    }

    static void complete(HeartbeatToolProtocol.ToolCall call,
                         AgentDeviceBridge.ToolResult result) {
        if (call == null || result == null) return;
        String key = key(call);
        if (key.length() == 0) return;
        synchronized (LOCK) {
            ensureLoadedLocked();
            Trace old = TRACES.get(key);
            long started = old == null ? System.currentTimeMillis() : old.startedAt;
            String input = old == null ? inputJson(call) : old.input;
            putLocked(key, new Trace(key, input, outputJson(result), true,
                    result.success, started, System.currentTimeMillis()));
            persistLocked();
        }
    }

    static Trace find(String key, String fallbackInput) {
        String safe = key == null ? "" : key;
        synchronized (LOCK) {
            ensureLoadedLocked();
            Trace trace = TRACES.get(safe);
            if (trace != null) return trace;
        }
        String input = fallbackInput == null || fallbackInput.trim().length() == 0
                ? "{}" : fallbackInput.trim();
        return new Trace(safe, input, "", false, false, 0L, 0L);
    }

    static String restoredCarrierForScope(String scope) {
        String safeScope = scope == null ? "" : scope.trim();
        if (!HostCompat.isV236() || safeScope.length() == 0) return "";
        ArrayList<RichPanelRenderer.ToolLogRow> rows = new ArrayList<>();
        synchronized (LOCK) {
            ensureLoadedLocked();
            String prefix = safeScope + "\u001f";
            for (String key : ORDER) {
                Trace trace = TRACES.get(key);
                if (trace == null || !trace.finished || !key.startsWith(prefix)) continue;
                String detail = restoredDetail(trace);
                rows.add(new RichPanelRenderer.ToolLogRow(
                        restoredName(detail), detail, trace.key, trace.input));
            }
        }
        return RichPanelRenderer.renderToolLogRows(rows);
    }

    static long earliestFinishedTraceStartedAt(String scope) {
        String safeScope = scope == null ? "" : scope.trim();
        if (!HostCompat.isV236() || safeScope.length() == 0) return 0L;
        long earliest = Long.MAX_VALUE;
        synchronized (LOCK) {
            ensureLoadedLocked();
            String prefix = safeScope + "\u001f";
            for (String key : ORDER) {
                Trace trace = TRACES.get(key);
                if (trace == null || !trace.finished || !key.startsWith(prefix)) continue;
                if (trace.startedAt > 0L && trace.startedAt < earliest) {
                    earliest = trace.startedAt;
                }
            }
        }
        return earliest == Long.MAX_VALUE ? 0L : earliest;
    }

    static long earliestStartedAtForKeys(java.util.Set<String> keys) {
        if (!HostCompat.isV236() || keys == null || keys.isEmpty()) return 0L;
        long earliest = Long.MAX_VALUE;
        synchronized (LOCK) {
            ensureLoadedLocked();
            for (String key : keys) {
                Trace trace = TRACES.get(key);
                if (trace != null && trace.startedAt > 0L && trace.startedAt < earliest) {
                    earliest = trace.startedAt;
                }
            }
        }
        return earliest == Long.MAX_VALUE ? 0L : earliest;
    }

    private static String restoredDetail(Trace trace) {
        try {
            String detail = new JSONObject(trace.output).optString("detail", "").trim();
            if (detail.length() > 0) return detail;
        } catch (Throwable ignored) {}
        return trace.success ? "已完成" : "执行失败";
    }

    private static String restoredName(String detail) {
        if (detail == null) return "工具调用";
        String[] known = {"Shell", "MCP", "Java", "截图", "文件", "网络", "应用", "音乐"};
        for (String value : known) if (detail.contains(value)) return value;
        return "工具调用";
    }

    private static String outputJson(AgentDeviceBridge.ToolResult result) {
        try {
            JSONObject output = new JSONObject();
            output.put("ok", result.success);
            output.put("exit_code", result.exitCode);
            output.put("encoding", result.encoding);
            output.put("truncated", result.truncated);
            if (result.detail.length() > 0) output.put("detail", result.detail);
            String raw = trim(result.output, MAX_OUTPUT_CHARS);
            if (raw.length() > 0) {
                try { output.put("output", new JSONObject(raw)); }
                catch (Throwable objectError) {
                    try { output.put("output", new JSONArray(raw)); }
                    catch (Throwable arrayError) { output.put("output", raw); }
                }
            } else {
                output.put("output", "");
            }
            return output.toString(2);
        } catch (Throwable ignored) {
            return trim(result.output, MAX_OUTPUT_CHARS);
        }
    }

    private static void putLocked(String key, Trace trace) {
        TRACES.put(key, trace);
        ORDER.remove(key);
        ORDER.addLast(key);
        while (ORDER.size() > MAX_RECORDS) {
            String oldest = ORDER.removeFirst();
            TRACES.remove(oldest);
        }
    }

    private static boolean supportsPersistence() {
        return HostCompat.isV236() || HostCompat.isV241();
    }

    private static void ensureLoadedLocked() {
        if (loaded || !supportsPersistence()) return;
        loaded = true;
        File file = new File(STORE_PATH);
        if (!file.isFile() || file.length() <= 0 || file.length() > MAX_STORE_BYTES) return;
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream((int) file.length())) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) > 0) {
                if (output.size() + count > MAX_STORE_BYTES) return;
                output.write(buffer, 0, count);
            }
            JSONArray values = new JSONArray(new String(
                    output.toByteArray(), StandardCharsets.UTF_8));
            for (int index = 0; index < values.length(); index++) {
                JSONObject value = values.optJSONObject(index);
                if (value == null) continue;
                String key = value.optString("key", "").trim();
                if (key.length() == 0) continue;
                putLocked(key, new Trace(key,
                        trim(value.optString("input", "{}"), MAX_INPUT_CHARS),
                        trim(value.optString("output", ""), MAX_OUTPUT_CHARS),
                        value.optBoolean("finished", false),
                        value.optBoolean("success", false),
                        value.optLong("started_at", 0L),
                        value.optLong("finished_at", 0L)));
            }
        } catch (Throwable ignored) {
            TRACES.clear();
            ORDER.clear();
        }
    }

    private static void persistLocked() {
        if (!supportsPersistence()) return;
        File target = new File(STORE_PATH);
        File temporary = new File(STORE_PATH + ".tmp");
        try {
            JSONArray values = new JSONArray();
            int skip = Math.max(0, ORDER.size() - MAX_PERSISTED_RECORDS);
            int position = 0;
            for (String key : ORDER) {
                if (position++ < skip) continue;
                Trace trace = TRACES.get(key);
                if (trace == null || !trace.finished) continue;
                values.put(new JSONObject()
                        .put("key", trace.key)
                        .put("input", trace.input)
                        .put("output", trace.output)
                        .put("finished", true)
                        .put("success", trace.success)
                        .put("started_at", trace.startedAt)
                        .put("finished_at", trace.finishedAt));
            }
            byte[] bytes = values.toString().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_STORE_BYTES) return;
            try (FileOutputStream output = new FileOutputStream(temporary, false)) {
                output.write(bytes);
                output.flush();
                output.getFD().sync();
            }
            if (target.exists() && !target.delete()) return;
            if (!temporary.renameTo(target)) temporary.delete();
        } catch (Throwable ignored) {
            temporary.delete();
        }
    }

    private static String trim(String value, int max) {
        String safe = value == null ? "" : value;
        return safe.length() <= max ? safe : safe.substring(0, max) + "\n…(truncated)";
    }
}
