package com.smallhairball.deepx;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Capability and extension-point runtime for Java plugin ABI v3.
 *
 * <p>Plugins integrate through stable JSON contracts rather than obfuscated host members. Raw
 * member events remain available only to explicitly authorized developer plugins. This class is
 * intentionally independent from Local API internals.</p>
 */
final class JavaPluginPlatform {
    static final String EXT_AGENT_TOOL = "agent.tool";
    static final String EXT_OUTGOING_TRANSFORM = "chat.outgoing.transform";
    static final String EXT_INCOMING_TRANSFORM = "chat.incoming.transform";
    static final String EXT_PROMPT_PROVIDER = "chat.prompt.provider";
    static final String EXT_MESSAGE_ACTION = "ui.message.action";
    static final String EXT_ACCOUNT_ACTION = "account.action";
    static final String EXT_ATTACHMENT_PROCESSOR = "attachment.processor";
    static final String EXT_FORM_ACTION = "ui.form.action";

    static final String PERMISSION_LIFECYCLE = "lifecycle.observe";
    static final String PERMISSION_UI = "ui.contribute";
    static final String PERMISSION_UI_DIALOG = "ui.dialog";
    static final String PERMISSION_STORAGE = "storage.private";
    static final String PERMISSION_CHAT_OBSERVE = "chat.observe";
    static final String PERMISSION_CHAT_MODIFY = "chat.modify";
    static final String PERMISSION_ACCOUNT_READ = "account.read";
    static final String PERMISSION_ACCOUNT_MANAGE = "account.manage";
    static final String PERMISSION_AGENT_OBSERVE = "agent.observe";
    static final String PERMISSION_AGENT_TOOL = "agent.tool";
    static final String PERMISSION_NETWORK = "network.http";
    static final String PERMISSION_AI_GENERATE = "ai.generate";
    static final String PERMISSION_HOST_RAW = "host.raw";

    private static final Set<String> KNOWN_PERMISSIONS = Collections.newSetFromMap(
            new ConcurrentHashMap<String, Boolean>());
    private static final Map<String, String> EXTENSION_PERMISSIONS = new LinkedHashMap<>();
    private static final Map<String, Extension> EXTENSIONS = new LinkedHashMap<>();
    private static final AtomicLong GENERATION = new AtomicLong(1L);
    private static final Handler MAIN;
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            1, 4, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(64), r -> {
        Thread thread = new Thread(r, "DeePX-PluginCapability");
        thread.setDaemon(true);
        return thread;
    }, new ThreadPoolExecutor.AbortPolicy());
    private static final ScheduledExecutorService TIMEOUTS =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "DeePX-PluginTimeout");
                thread.setDaemon(true);return thread;
            });

    static {
        Handler mainHandler;
        try { mainHandler = new Handler(Looper.getMainLooper()); } catch (Throwable t) { mainHandler = null; }
        MAIN = mainHandler;
        Collections.addAll(KNOWN_PERMISSIONS,
                PERMISSION_LIFECYCLE, PERMISSION_UI, PERMISSION_UI_DIALOG,
                PERMISSION_STORAGE, PERMISSION_CHAT_OBSERVE, PERMISSION_CHAT_MODIFY,
                PERMISSION_ACCOUNT_READ, PERMISSION_ACCOUNT_MANAGE,
                PERMISSION_AGENT_OBSERVE, PERMISSION_AGENT_TOOL,
                PERMISSION_NETWORK, PERMISSION_AI_GENERATE, PERMISSION_HOST_RAW);
        EXTENSION_PERMISSIONS.put(EXT_AGENT_TOOL, PERMISSION_AGENT_TOOL);
        EXTENSION_PERMISSIONS.put(EXT_OUTGOING_TRANSFORM, PERMISSION_CHAT_MODIFY);
        EXTENSION_PERMISSIONS.put(EXT_PROMPT_PROVIDER, PERMISSION_CHAT_MODIFY);
        EXTENSION_PERMISSIONS.put(EXT_FORM_ACTION, PERMISSION_UI);
    }

    private JavaPluginPlatform() {}

    static final class Extension {
        final String key;
        final String pluginId;
        final String point;
        final String id;
        final JSONObject descriptor;
        final JavaPluginApi.ExtensionCallback callback;
        final AtomicBoolean active = new AtomicBoolean(true);

        Extension(String key, String pluginId, String point, String id,
                  JSONObject descriptor, JavaPluginApi.ExtensionCallback callback) {
            this.key = key;
            this.pluginId = pluginId;
            this.point = point;
            this.id = id;
            this.descriptor = descriptor;
            this.callback = callback;
        }
    }

    static boolean isKnownPermission(String value) {
        return value != null && KNOWN_PERMISSIONS.contains(value.trim());
    }

    static List<String> knownPermissions() {
        ArrayList<String> out = new ArrayList<>(KNOWN_PERMISSIONS);
        Collections.sort(out);
        return Collections.unmodifiableList(out);
    }

    static boolean permissionForEvent(String hook, String permission) {
        if (hook == null) return true;
        if (hook.startsWith(JavaPluginRuntimeBridge.PREFIX)) {
            return PERMISSION_HOST_RAW.equals(permission);
        }
        if (hook.startsWith("chat.") || hook.startsWith("conversation.")
                || hook.startsWith("attachment.") || hook.startsWith("image.")) {
            return PERMISSION_CHAT_OBSERVE.equals(permission)
                    || PERMISSION_CHAT_MODIFY.equals(permission);
        }
        if (hook.startsWith("account.") || hook.startsWith("login.")) {
            return PERMISSION_ACCOUNT_READ.equals(permission)
                    || PERMISSION_ACCOUNT_MANAGE.equals(permission);
        }
        if (hook.startsWith("agent.") || hook.startsWith("mcp.")) {
            return PERMISSION_AGENT_OBSERVE.equals(permission)
                    || PERMISSION_AGENT_TOOL.equals(permission);
        }
        if (hook.startsWith("app.") || hook.startsWith("navigation.")) {
            return PERMISSION_LIFECYCLE.equals(permission);
        }
        return true;
    }

    static JavaPluginApi.Registration register(
            String pluginId, String point, String extensionId, JSONObject descriptor,
            JavaPluginApi.ExtensionCallback callback) {
        String safePoint = point == null ? "" : point.trim();
        String permission = EXTENSION_PERMISSIONS.get(safePoint);
        if (permission == null) {
            throw new IllegalArgumentException("unknown extension point " + safePoint);
        }
        JavaPluginManager.requirePermission(pluginId, permission);
        String safeId = cleanId(extensionId);
        if (safeId.length() == 0) throw new IllegalArgumentException("invalid extension id");
        if (callback == null) throw new IllegalArgumentException("missing extension callback");
        JSONObject safeDescriptor = copy(descriptor);
        String key = pluginId + ":" + safePoint + ":" + safeId;
        final Extension extension = new Extension(
                key, pluginId, safePoint, safeId, safeDescriptor, callback);
        synchronized (EXTENSIONS) {
            int count = 0;
            for (Extension value : EXTENSIONS.values()) {
                if (value.pluginId.equals(pluginId) && ++count >= 48) {
                    throw new IllegalStateException("too many plugin extensions");
                }
            }
            Extension old = EXTENSIONS.put(key, extension);
            if (old != null) old.active.set(false);
        }
        GENERATION.incrementAndGet();
        return new JavaPluginApi.Registration() {
            @Override public String id() { return extension.id; }
            @Override public void unregister() { unregisterExtension(extension); }
        };
    }

    static void removePlugin(String pluginId) {
        if (pluginId == null) return;
        synchronized (EXTENSIONS) {
            ArrayList<String> remove = new ArrayList<>();
            for (Extension extension : EXTENSIONS.values()) {
                if (pluginId.equals(extension.pluginId)) {
                    extension.active.set(false);
                    remove.add(extension.key);
                }
            }
            for (String key : remove) EXTENSIONS.remove(key);
        }
        GENERATION.incrementAndGet();
    }

    static long generation() { return GENERATION.get(); }

    static List<JSONObject> descriptors(String point) {
        ArrayList<JSONObject> out = new ArrayList<>();
        for (Extension extension : snapshot(point)) {
            JSONObject value = copy(extension.descriptor);
            put(value, "pluginId", extension.pluginId);
            put(value, "extensionId", extension.id);
            if (EXT_AGENT_TOOL.equals(point)) put(value, "tool", toolName(extension));
            out.add(value);
        }
        return Collections.unmodifiableList(out);
    }

    static boolean isAgentTool(String tool) {
        return findAgentTool(tool) != null;
    }

    static boolean hasAgentTools() {
        return !snapshot(EXT_AGENT_TOOL).isEmpty();
    }

    static String agentPromptContract(String scope) {
        StringBuilder out = new StringBuilder();
        for (Extension extension : snapshot(EXT_AGENT_TOOL)) {
            String name = toolName(extension);
            String title = bounded(extension.descriptor.optString("name", extension.id), 120);
            String description = bounded(extension.descriptor.optString("description", ""), 1200);
            JSONObject schema = extension.descriptor.optJSONObject("inputSchema");
            if (schema == null) schema = new JSONObject();
            out.append('\n').append(name).append('：')
                    .append(description.length() == 0 ? title : description)
                    .append("；arguments 必须符合 JSON Schema ").append(schema.toString())
                    .append("。调用格式：{\"id\":\"唯一短标识\",\"tool\":\"")
                    .append(name).append("\",\"scope\":\"").append(scope)
                    .append("\",\"arguments\":{}}。");
        }
        return out.toString();
    }

    static void executeAgentToolAsync(String tool, String rawArguments,
                                      JavaPluginApi.ResultCallback callback) {
        final Extension extension = findAgentTool(tool);
        if (extension == null) {
            callback.onResult(JavaPluginApi.Result.error("tool_not_found", "插件工具不存在"));
            return;
        }
        final AtomicBoolean completed = new AtomicBoolean(false);
        final Future<?> task;
        try { task = EXECUTOR.submit(() -> {
            JavaPluginApi.Result result;
            try {
                JSONObject request = new JSONObject(rawArguments == null
                        || rawArguments.trim().length() == 0 ? "{}" : rawArguments);
                result = extension.callback.onInvoke(request);
                if (result == null) result = JavaPluginApi.Result.error(
                        "empty_result", "插件没有返回执行结果");
            } catch (Throwable error) {
                JavaPluginManager.recordPluginFailure(extension.pluginId, error);
                result = JavaPluginApi.Result.error(
                        "plugin_exception", safe(error));
            }
            final JavaPluginApi.Result finalResult = result;
            if (!completed.compareAndSet(false, true)) return;
            MAIN.post(() -> callback.onResult(finalResult));
        }); } catch (Throwable error) {
            callback.onResult(JavaPluginApi.Result.error("runtime_busy", "插件运行队列繁忙"));
            return;
        }
        TIMEOUTS.schedule(() -> {
            if (!completed.compareAndSet(false, true)) return;
            task.cancel(true);
            MAIN.post(() -> callback.onResult(JavaPluginApi.Result.error(
                    "tool_timeout", "插件工具执行超过 120 秒")));
        }, 120L, TimeUnit.SECONDS);
    }

    static String transformOutgoing(String conversationId, String text) {
        String current = text == null ? "" : text;
        for (Extension extension : snapshot(EXT_OUTGOING_TRANSFORM)) {
            final JSONObject request = new JSONObject();
            put(request, "conversationId", conversationId == null ? "" : conversationId);
            put(request, "text", current);
            Future<JavaPluginApi.Result> future=null;
            try {
                future = EXECUTOR.submit(() -> extension.callback.onInvoke(request));
                JavaPluginApi.Result result = future.get(80L, TimeUnit.MILLISECONDS);
                if (result != null && result.success) {
                    String transformed = result.data.optString("text", current);
                    if (transformed.length() <= 256 * 1024) current = transformed;
                }
            } catch (Throwable error) {
                if(future!=null)future.cancel(true);
                JavaPluginManager.recordPluginFailure(extension.pluginId, error);
            }
        }
        return current;
    }

    static String systemPrompt(String conversationId) {
        StringBuilder out = new StringBuilder();
        for (Extension extension : snapshot(EXT_PROMPT_PROVIDER)) {
            JSONObject request = new JSONObject();
            put(request, "conversationId", conversationId == null ? "" : conversationId);
            Future<JavaPluginApi.Result> future=null;
            try {
                future = EXECUTOR.submit(
                        () -> extension.callback.onInvoke(request));
                JavaPluginApi.Result result = future.get(80L, TimeUnit.MILLISECONDS);
                if (result == null || !result.success) continue;
                String prompt = result.data.optString("prompt", "").trim();
                if (prompt.length() == 0 || prompt.length() > 64 * 1024) continue;
                if (out.length() > 0) out.append("\n\n");
                out.append(prompt);
            } catch (Throwable error) {
                if(future!=null)future.cancel(true);
                JavaPluginManager.recordPluginFailure(extension.pluginId, error);
            }
        }
        return out.toString();
    }

    static JavaPluginApi.Result call(
            Context context, String pluginId, String capability, JSONObject request) {
        String name = capability == null ? "" : capability.trim();
        JSONObject input = request == null ? new JSONObject() : request;
        try {
            if ("runtime.info".equals(name)) {
                JSONObject data = new JSONObject();
                put(data, "abi", JavaPluginApi.ABI_VERSION);
                put(data, "hostVersion", HostCompat.generationName());
                put(data, "pluginId", pluginId);
                put(data, "extensionGeneration", generation());
                return JavaPluginApi.Result.ok(data);
            }
            if ("runtime.extensions".equals(name)) {
                String point = input.optString("point", "");
                JSONArray data = new JSONArray();
                for (JSONObject item : descriptors(point)) data.put(item);
                return JavaPluginApi.Result.ok(new JSONObject().put("items", data));
            }
            if ("runtime.invoke_own_extension".equals(name)) {
                String point=input.optString("point","").trim();
                String extensionId=cleanId(input.optString("id",""));
                JSONObject extensionInput=input.optJSONObject("input");
                Extension extension;
                synchronized(EXTENSIONS){extension=EXTENSIONS.get(
                        pluginId+":"+point+":"+extensionId);}
                if(extension==null||!extension.active.get())return JavaPluginApi.Result.error(
                        "extension_not_found","插件自己的扩展不存在");
                JavaPluginApi.Result result=extension.callback.onInvoke(
                        extensionInput==null?new JSONObject():extensionInput);
                return result==null?JavaPluginApi.Result.error(
                        "empty_result","扩展没有返回结果"):result;
            }
            if ("storage.get".equals(name)) {
                JavaPluginManager.requirePermission(pluginId, PERMISSION_STORAGE);
                String key = cleanSettingKey(input.optString("key", ""));
                if (key.length() == 0) return invalid("invalid_key");
                String fallback = bounded(input.optString("fallback", ""), 64 * 1024);
                String value = context.getSharedPreferences(
                        "deekseep_plugin_" + pluginId, 0).getString(key, fallback);
                return JavaPluginApi.Result.ok(new JSONObject().put("value", value));
            }
            if ("storage.put".equals(name)) {
                JavaPluginManager.requirePermission(pluginId, PERMISSION_STORAGE);
                String key = cleanSettingKey(input.optString("key", ""));
                String value = input.optString("value", "");
                if (key.length() == 0 || value.length() > 64 * 1024) return invalid("invalid_value");
                boolean saved = context.getSharedPreferences(
                        "deekseep_plugin_" + pluginId, 0).edit().putString(key, value).commit();
                return saved ? JavaPluginApi.Result.ok(new JSONObject())
                        : JavaPluginApi.Result.error("write_failed", "插件存储写入失败");
            }
            if ("storage.delete".equals(name)) {
                JavaPluginManager.requirePermission(pluginId, PERMISSION_STORAGE);
                String key = cleanSettingKey(input.optString("key", ""));
                if (key.length() == 0) return invalid("invalid_key");
                boolean saved = context.getSharedPreferences(
                        "deekseep_plugin_" + pluginId, 0).edit().remove(key).commit();
                return saved ? JavaPluginApi.Result.ok(new JSONObject())
                        : JavaPluginApi.Result.error("write_failed", "插件存储删除失败");
            }
            if ("ui.toast".equals(name)) {
                JavaPluginManager.requirePermission(pluginId, PERMISSION_UI_DIALOG);
                final String text = bounded(input.optString("text", ""), 240);
                if (text.length() == 0) return invalid("empty_text");
                MAIN.post(() -> Toast.makeText(context, text, Toast.LENGTH_SHORT).show());
                return JavaPluginApi.Result.ok(new JSONObject());
            }
            if ("ui.open_uri".equals(name)) {
                JavaPluginManager.requirePermission(pluginId, PERMISSION_UI_DIALOG);
                String raw = bounded(input.optString("uri", ""), 2048);
                Uri uri = Uri.parse(raw);
                String scheme = uri.getScheme();
                if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) {
                    return invalid("unsupported_uri");
                }
                Activity activity = JavaPluginManager.currentActivitySnapshot();
                if (activity == null) return JavaPluginApi.Result.error(
                        "no_activity", "当前没有可用界面");
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                activity.startActivity(intent);
                return JavaPluginApi.Result.ok(new JSONObject());
            }
            if ("ui.form.open".equals(name)) {
                JavaPluginManager.requirePermission(pluginId, PERMISSION_UI);
                Activity activity = JavaPluginManager.currentActivitySnapshot();
                if (activity == null) return JavaPluginApi.Result.error(
                        "no_activity", "当前没有可用界面");
                JSONObject spec = copy(input);
                MAIN.post(() -> JavaPluginPageUi.show(activity, pluginId, spec));
                return JavaPluginApi.Result.ok(new JSONObject());
            }
            if ("ui.clipboard.set".equals(name)) {
                JavaPluginManager.requirePermission(pluginId, PERMISSION_UI_DIALOG);
                String text = bounded(input.optString("text", ""), 512 * 1024);
                android.content.ClipboardManager clipboard =
                        (android.content.ClipboardManager) context.getSystemService(
                                Context.CLIPBOARD_SERVICE);
                if (clipboard == null) return JavaPluginApi.Result.error(
                        "clipboard_unavailable", "剪贴板不可用");
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText(
                        bounded(input.optString("label", "DeePX 插件"), 80), text));
                return JavaPluginApi.Result.ok(new JSONObject());
            }
            if ("ai.generate".equals(name)) {
                JavaPluginManager.requirePermission(pluginId, PERMISSION_AI_GENERATE);
                String system = bounded(input.optString("system", ""), 64 * 1024);
                String prompt = bounded(input.optString("prompt", ""), 256 * 1024).trim();
                if (prompt.length() == 0) return invalid("empty_prompt");
                String model = bounded(input.optString("model", "deepseek-chat"), 80);
                int maxOutput = Math.max(128, Math.min(32768,
                        input.optInt("maxOutputTokens", 4096)));
                z2.CompletionResult result = Main.completePluginAi(
                        pluginId, model, system, prompt, maxOutput);
                JSONObject data = new JSONObject();
                put(data, "text", result.text);
                put(data, "reasoning", result.reasoning);
                put(data, "finishReason", result.finishReason);
                return JavaPluginApi.Result.ok(data);
            }
            if ("account.list".equals(name)) {
                JavaPluginManager.requirePermission(pluginId, PERMISSION_ACCOUNT_READ);
                JSONArray items = new JSONArray();
                for (AccountManager.Account account
                        : AccountManager.accountsForUi(Main.hostClassLoader)) {
                    JSONObject item = new JSONObject();
                    put(item, "id", account.id);
                    put(item, "label", account.label);
                    put(item, "provider", account.provider);
                    put(item, "avatar", account.avatar);
                    put(item, "current", account.current);
                    items.put(item);
                }
                return JavaPluginApi.Result.ok(new JSONObject().put("items", items));
            }
            if ("account.password_candidate".equals(name)) {
                JavaPluginManager.requirePermission(pluginId, PERMISSION_ACCOUNT_MANAGE);
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    return JavaPluginApi.Result.error("async_required",
                            "账号验证必须通过 callAsync 或插件表单操作调用");
                }
                final Activity activity = JavaPluginManager.currentActivitySnapshot();
                if (activity == null) return JavaPluginApi.Result.error(
                        "no_activity", "当前没有可用界面");
                final String identity = bounded(input.optString("identity", ""), 180);
                final String password = bounded(input.optString("password", ""), 80);
                final CountDownLatch done = new CountDownLatch(1);
                final AtomicReference<JavaPluginApi.Result> answer =
                        new AtomicReference<JavaPluginApi.Result>();
                MAIN.post(() -> {
                    String error = Main.addCandidateAccountWithPassword(
                            activity, identity, password, (success, message) -> {
                                JSONObject data = new JSONObject();
                                put(data, "message", message);
                                answer.set(success ? JavaPluginApi.Result.ok(data)
                                        : JavaPluginApi.Result.error(
                                                "login_failed", message));
                                done.countDown();
                            });
                    if (error != null) {
                        answer.set(JavaPluginApi.Result.error("login_rejected", error));
                        done.countDown();
                    }
                });
                if (!done.await(36L, TimeUnit.SECONDS)) {
                    return JavaPluginApi.Result.error("login_timeout", "账号验证超时");
                }
                JavaPluginApi.Result result = answer.get();
                return result == null ? JavaPluginApi.Result.error(
                        "login_failed", "账号验证没有返回结果") : result;
            }
            if (name.startsWith("account.registration.")) {
                JavaPluginManager.requirePermission(pluginId, PERMISSION_ACCOUNT_MANAGE);
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    return JavaPluginApi.Result.error("async_required",
                            "注册流程必须通过 callAsync 或插件表单操作调用");
                }
                final CountDownLatch done = new CountDownLatch(1);
                final AtomicReference<JavaPluginApi.Result> answer =
                        new AtomicReference<JavaPluginApi.Result>();
                final AccountRegistrationBridge.Callback callback =
                        (success, code, message, data) -> {
                            answer.set(success ? JavaPluginApi.Result.ok(data)
                                    : JavaPluginApi.Result.error(code, message));
                            done.countDown();
                        };
                final String action = name.substring("account.registration.".length());
                if ("cancel".equals(action)) {
                    boolean cancelled = AccountRegistrationBridge.cancel(pluginId,
                            bounded(input.optString("sessionId", ""), 80));
                    return cancelled ? JavaPluginApi.Result.ok(new JSONObject()
                            .put("state", "cancelled")) : JavaPluginApi.Result.error(
                            "registration_session_not_found", "注册会话已失效");
                }
                MAIN.post(() -> {
                    String error;
                    if ("start".equals(action)) {
                        Activity activity = JavaPluginManager.currentActivitySnapshot();
                        if (activity == null) {
                            answer.set(JavaPluginApi.Result.error(
                                    "no_activity", "当前没有可用界面"));
                            done.countDown();
                            return;
                        }
                        error = AccountRegistrationBridge.start(activity, pluginId,
                                bounded(input.optString("channel", ""), 16),
                                bounded(input.optString("identity", ""), 180),
                                bounded(input.optString("password", ""), 80), callback);
                    } else if ("resend".equals(action)) {
                        error = AccountRegistrationBridge.resend(pluginId,
                                bounded(input.optString("sessionId", ""), 80), callback);
                    } else if ("complete".equals(action)) {
                        error = AccountRegistrationBridge.complete(pluginId,
                                bounded(input.optString("sessionId", ""), 80),
                                bounded(input.optString("code", ""), 16), callback);
                    } else {
                        error = "未知注册操作";
                    }
                    if (error != null) {
                        answer.set(JavaPluginApi.Result.error(
                                "registration_rejected", error));
                        done.countDown();
                    }
                });
                long waitSeconds = "complete".equals(action) ? 50L : 150L;
                if (!done.await(waitSeconds, TimeUnit.SECONDS)) {
                    return JavaPluginApi.Result.error("registration_timeout",
                            "注册流程等待超时");
                }
                JavaPluginApi.Result result = answer.get();
                return result == null ? JavaPluginApi.Result.error(
                        "registration_failed", "注册流程没有返回结果") : result;
            }
            if ("account.validate".equals(name)) {
                JavaPluginManager.requirePermission(pluginId, PERMISSION_ACCOUNT_READ);
                String id = input.optString("id", "").trim();
                AccountManager.Account selected = null;
                for (AccountManager.Account account
                        : AccountManager.accountsForUi(Main.hostClassLoader)) {
                    if (id.equals(account.id)) { selected = account; break; }
                }
                if (selected == null) return JavaPluginApi.Result.error(
                        "account_not_found", "找不到指定账号");
                AccountManager.ServerValidation checked =
                        AccountManager.validateWithServer(context, selected.credJson);
                JSONObject data = new JSONObject();
                put(data, "valid", checked.valid);
                put(data, "retryable", checked.retryable);
                put(data, "detail", checked.detail);
                put(data, "message", checked.error == null ? "账号可用" : checked.error);
                return JavaPluginApi.Result.ok(data);
            }
            if ("agent.mcp.list".equals(name)) {
                JavaPluginManager.requirePermission(pluginId, PERMISSION_AGENT_OBSERVE);
                JSONArray items = new JSONArray();
                for (AgentMcpManager.Config config : AgentMcpManager.loadAll()) {
                    JSONObject item = new JSONObject();
                    put(item, "name", config.name);
                    put(item, "enabled", config.enabled);
                    put(item, "transport", config.transport);
                    put(item, "toolCount", config.tools.size());
                    items.put(item);
                }
                return JavaPluginApi.Result.ok(new JSONObject().put("items", items));
            }
            if ("network.http".equals(name)) {
                JavaPluginManager.requirePermission(pluginId, PERMISSION_NETWORK);
                return http(input);
            }
            return JavaPluginApi.Result.error("unknown_capability", "未知能力：" + name);
        } catch (SecurityException error) {
            return JavaPluginApi.Result.error("permission_denied", safe(error));
        } catch (Throwable error) {
            JavaPluginManager.recordPluginFailure(pluginId, error);
            return JavaPluginApi.Result.error("capability_failed", safe(error));
        }
    }

    static void callAsync(Context context, String pluginId, String capability,
                          JSONObject request, JavaPluginApi.ResultCallback callback) {
        if (callback == null) return;
        submit(() -> callback.onResult(call(context, pluginId, capability, request)), callback);
    }

    static void invokeFormActionAsync(String pluginId, String extensionId,
                                      JSONObject request,
                                      JavaPluginApi.ResultCallback callback) {
        Extension extension;
        synchronized (EXTENSIONS) {
            extension = EXTENSIONS.get(pluginId + ":" + EXT_FORM_ACTION + ":"
                    + cleanId(extensionId));
        }
        if (extension == null || !extension.active.get()) {
            callback.onResult(JavaPluginApi.Result.error(
                    "action_not_found", "插件页面操作不存在"));
            return;
        }
        submit(() -> {
            try {
                JavaPluginApi.Result result = extension.callback.onInvoke(
                        request == null ? new JSONObject() : request);
                callback.onResult(result == null ? JavaPluginApi.Result.error(
                        "empty_result", "插件页面操作没有返回结果") : result);
            } catch (Throwable error) {
                JavaPluginManager.recordPluginFailure(pluginId, error);
                callback.onResult(JavaPluginApi.Result.error(
                        "action_failed", safe(error)));
            }
        }, callback);
    }

    private static JavaPluginApi.Result http(JSONObject request) throws Exception {
        String rawUrl = request.optString("url", "").trim();
        URL url = new URL(rawUrl);
        String protocol = url.getProtocol();
        if (!("https".equalsIgnoreCase(protocol) || "http".equalsIgnoreCase(protocol))) {
            return invalid("unsupported_protocol");
        }
        String method = request.optString("method", "GET").trim().toUpperCase(Locale.US);
        if (!("GET".equals(method) || "POST".equals(method) || "PUT".equals(method)
                || "PATCH".equals(method) || "DELETE".equals(method))) {
            return invalid("unsupported_method");
        }
        int timeout = Math.max(1000, Math.min(30000, request.optInt("timeoutMs", 15000)));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod(method);
            JSONObject headers = request.optJSONObject("headers");
            if (headers != null) {
                java.util.Iterator<String> keys = headers.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (!key.matches("[A-Za-z0-9-]{1,80}")
                            || "host".equalsIgnoreCase(key)
                            || "content-length".equalsIgnoreCase(key)) continue;
                    connection.setRequestProperty(key,
                            bounded(headers.optString(key, ""), 4096));
                }
            }
            String body = request.optString("body", "");
            if (body.length() > 1024 * 1024) return invalid("body_too_large");
            if (body.length() > 0 && !"GET".equals(method)) {
                connection.setDoOutput(true);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = connection.getResponseCode();
            InputStream input = status >= 400 ? connection.getErrorStream()
                    : connection.getInputStream();
            String response = input == null ? "" : readLimited(input, 1024 * 1024);
            JSONObject data = new JSONObject();
            put(data, "status", status);
            put(data, "body", response);
            put(data, "contentType", connection.getContentType());
            return JavaPluginApi.Result.ok(data);
        } finally {
            connection.disconnect();
        }
    }

    private static List<Extension> snapshot(String point) {
        ArrayList<Extension> out = new ArrayList<>();
        synchronized (EXTENSIONS) {
            for (Extension extension : EXTENSIONS.values()) {
                if (extension.active.get() && (point == null || point.length() == 0
                        || point.equals(extension.point))) out.add(extension);
            }
        }
        return out;
    }

    private static Extension findAgentTool(String tool) {
        if (tool == null || !tool.startsWith("plugin__")) return null;
        for (Extension extension : snapshot(EXT_AGENT_TOOL)) {
            if (tool.equals(toolName(extension))) return extension;
        }
        return null;
    }

    private static String toolName(Extension extension) {
        return "plugin__" + token(extension.pluginId) + "__" + token(extension.id);
    }

    private static void unregisterExtension(Extension extension) {
        if (extension == null || !extension.active.compareAndSet(true, false)) return;
        synchronized (EXTENSIONS) { EXTENSIONS.remove(extension.key, extension); }
        GENERATION.incrementAndGet();
    }

    private static void submit(Runnable runnable, JavaPluginApi.ResultCallback callback) {
        try { EXECUTOR.execute(runnable); }
        catch (Throwable error) {
            if (callback != null) callback.onResult(JavaPluginApi.Result.error(
                    "runtime_busy", "插件运行队列繁忙"));
        }
    }

    private static JavaPluginApi.Result invalid(String code) {
        return JavaPluginApi.Result.error(code, "请求参数无效");
    }

    private static String readLimited(InputStream input, int maximum) throws Exception {
        try (InputStream source = input;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0, count;
            while ((count = source.read(buffer)) >= 0) {
                if (count == 0) continue;
                int kept = Math.min(count, maximum - total);
                if (kept > 0) output.write(buffer, 0, kept);
                total += kept;
                if (total >= maximum) break;
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static JSONObject copy(JSONObject value) {
        try { return value == null ? new JSONObject() : new JSONObject(value.toString()); }
        catch (Throwable ignored) { return new JSONObject(); }
    }

    private static String cleanId(String value) {
        String safe = value == null ? "" : value.trim();
        return safe.matches("[A-Za-z0-9_.-]{2,80}") ? safe : "";
    }

    private static String cleanSettingKey(String value) {
        String safe = value == null ? "" : value.trim();
        return safe.matches("[A-Za-z0-9_.-]{1,96}") ? safe : "";
    }

    private static String token(String value) {
        String safe = value == null ? "" : value.toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9_]+", "_");
        return safe.length() == 0 ? "plugin" : safe;
    }

    private static String bounded(String value, int maximum) {
        String safe = value == null ? "" : value;
        return safe.length() <= maximum ? safe : safe.substring(0, maximum);
    }

    private static String safe(Throwable error) {
        if (error == null) return "unknown";
        String message = error.getMessage();
        return error.getClass().getSimpleName() + (message == null ? "" : ": "
                + bounded(message, 300));
    }

    private static void put(JSONObject object, String key, Object value) {
        try { object.put(key, value == null ? JSONObject.NULL : value); }
        catch (Throwable ignored) {}
    }
}
