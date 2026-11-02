package com.smallhairball.deepx;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

/** Manages DeepSeek's native local overrides for verified boolean feature settings. */
final class RemoteFeatureFlags {
    static final int FORCE_OFF = -1;
    static final int FOLLOW = 0;
    static final int FORCE_ON = 1;

    static final String CONFIG_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_remote_feature_overrides.json";
    static final String ATTACHMENT_GUIDE_PROMPTS =
            "deekseep_model_config_attachment_guide_prompts";
    static final String HOME_WELCOME_MESSAGES =
            "kv_remote_settings_welcome_msg";
    static final String V241_FORCE_EXPERT_MODEL =
            "deekseep_code257_force_expert_model";
    static final String V241_FORCE_VISION_MODEL =
            "deekseep_code257_force_vision_model";
    static final String V241_DEFAULT_VOICE_INPUT =
            "kv_remote_settings_input_default_voice";
    static final String V236_FORCE_EXPERT_MODEL =
            "deekseep_code249_force_expert_model";
    static final String V236_FORCE_VISION_MODEL =
            "deekseep_code249_force_vision_model";

    /** Keys verified in the 2.3.6/2.4.1 host and intentionally hidden on other hosts. */
    private static final HashSet<String> V236_V241_KEYS = new HashSet<>();
    static {
        String[] keys = {
                "kv_remote_settings_interrupt_and_send_enabled",
                "kv_remote_settings_allow_parallel_streams",
                "kv_remote_settings_pow_prefetch",
                "kv_remote_settings_session_prefetch",
                "kv_remote_settings_sse_smooth_follow",
                "kv_remote_settings_one_tap_login_enabled",
                "kv_remote_settings_dead_link_detection",
                "kv_remote_settings_enable_webview_content_report",
                "kv_remote_settings_gcy_enabled",
                "kv_remote_settings_volcengine_enabled",
                "kv_remote_settings_hcaptcha_enabled",
                "kv_remote_settings_should_use_sm_device_id"
        };
        for (String key : keys) V236_V241_KEYS.add(key);
    }

    static final class Feature {
        final String key;
        final String zh;
        final String en;
        final String detailZh;
        final String detailEn;
        final boolean inverted;
        final boolean nativeBoolean;

        Feature(String key, String zh, String en, String detailZh, String detailEn,
                boolean inverted, boolean nativeBoolean) {
            this.key = key;
            this.zh = zh;
            this.en = en;
            this.detailZh = detailZh;
            this.detailEn = detailEn;
            this.inverted = inverted;
            this.nativeBoolean = nativeBoolean;
        }
    }

    /** Integer settings whose type and local kv_settings_* override path were verified in both hosts. */
    static final class Parameter {
        final String key;
        final String zh;
        final String en;
        final String detailZh;
        final String detailEn;

        Parameter(String suffix, String zh, String en, String detailZh, String detailEn) {
            this.key = "kv_remote_settings_" + suffix;
            this.zh = zh;
            this.en = en;
            this.detailZh = detailZh;
            this.detailEn = detailEn;
        }
    }

    /*
     * Most entries are DeepSeek Boolean settings. The attachment guide is a 2.3.4 model-config
     * rollout rather than a standalone Boolean key, but is kept in the same manager because it is
     * presented and overridden with the same three-state contract.
     */
    static final Feature[] FEATURES = {
            feature("conversation_search_enabled", "会话搜索", "Conversation search",
                    "控制 DeepSeek 自带的会话搜索入口。",
                    "Controls DeepSeek's built-in conversation search entry."),
            feature("show_new_chat_button_above_input", "输入框上方新建对话",
                    "New chat above input",
                    "控制输入区域上方的原生新建对话按钮。",
                    "Controls the native new-chat button above the composer."),
            feature("voice_input_enabled", "语音输入", "Voice input",
                    "控制 DeepSeek 自带的语音输入能力。",
                    "Controls DeepSeek's built-in voice input."),
            feature("input_default_voice", "默认语音模式（2.4.1）",
                    "Default voice mode (2.4.1)",
                    "让 2.4.1/code257 的输入区默认进入原生语音模式；仍需同时开启语音输入。",
                    "Makes the 2.4.1/code257 composer enter its native voice mode by default; "
                            + "Voice input must also be enabled."),
            scopedFeature("interrupt_and_send_enabled", "中断并发送",
                    "Interrupt and send", "生成中发送新消息时中断当前回复。",
                    "Interrupts the current response when sending a new message."),
            scopedFeature("allow_parallel_streams", "允许并行流式请求",
                    "Allow parallel streams", "允许多个流式请求同时运行，可能增加账号风控风险。",
                    "Allows concurrent streams and may increase server risk controls."),
            scopedFeature("pow_prefetch", "预取 PoW",
                    "Prefetch PoW", "提前预取 PoW，可能改善首次请求速度，也可能增加请求频率。",
                    "Prefetches PoW; may improve latency but increases request frequency."),
            scopedFeature("session_prefetch", "预取会话",
                    "Prefetch sessions", "提前加载会话数据，可能增加网络和账号请求量。",
                    "Prefetches sessions and may increase network/account requests."),
            scopedFeature("sse_smooth_follow", "流式平滑跟随",
                    "Smooth stream follow", "控制流式输出是否平滑跟随。",
                    "Controls smooth following during streaming."),
            scopedFeature("one_tap_login_enabled", "一键登录",
                    "One-tap login", "恢复宿主的一键登录入口。",
                    "Restores the host one-tap login entry."),
            scopedFeature("dead_link_detection", "死链检测",
                    "Dead-link detection", "控制服务端死链检测。",
                    "Controls server dead-link detection."),
            scopedFeature("enable_webview_content_report", "网页内容上报",
                    "WebView content report", "控制网页内容上报，修改可能触发服务端风控。",
                    "Controls WebView content reporting and may trigger server risk controls."),
            scopedFeature("gcy_enabled", "GCY 服务能力",
                    "GCY service", "控制宿主 GCY 服务能力，修改后果自负。",
                    "Controls the host GCY capability; use at your own risk."),
            scopedFeature("volcengine_enabled", "火山引擎能力",
                    "Volcengine capability", "控制宿主火山引擎能力，修改后果自负。",
                    "Controls the host Volcengine capability; use at your own risk."),
            scopedFeature("hcaptcha_enabled", "验证码能力",
                    "hCaptcha", "控制 hCaptcha 能力，修改可能导致登录或请求异常。",
                    "Controls hCaptcha and may break login or requests."),
            scopedFeature("should_use_sm_device_id", "使用 SM 设备标识",
                    "Use SM device ID", "控制宿主是否使用 SM 设备标识，修改可能影响设备风控。",
                    "Controls SM device identity usage and may affect device risk controls."),
            invertedFeature("hide_assistant_avatar", "显示助手头像", "Show assistant avatar",
                    "开启时显示聊天页的助手头像。",
                    "Shows assistant avatars in chat when enabled."),
            feature("copy_text_without_markdown_syntax", "复制纯文本",
                    "Copy without Markdown",
                    "复制消息时移除 Markdown 标记。",
                    "Removes Markdown syntax when copying a message."),
            feature("select_text_without_markdown_syntax", "选择纯文本",
                    "Select without Markdown",
                    "选择消息文字时使用移除 Markdown 后的文本。",
                    "Uses text without Markdown syntax for text selection."),
            feature("optimize_markdown", "Markdown 优化", "Markdown optimization",
                    "控制 DeepSeek 的新版 Markdown 渲染优化。",
                    "Controls DeepSeek's optimized Markdown renderer."),
            feature("sse_auto_scroll_one_screen", "流式回复整屏跟随",
                    "One-screen stream follow",
                    "控制流式生成时的一屏自动滚动策略。",
                    "Controls one-screen auto scrolling while streaming."),
            feature("allow_file_with_search", "联网搜索允许文件", "Files with web search",
                    "控制上传文件与联网搜索能否同时使用。",
                    "Controls whether files and web search can be used together."),
            feature("disable_single_dollar_latex", "禁用单美元公式",
                    "Disable single-dollar LaTeX",
                    "不把单个美元符号包裹的内容解析为公式。",
                    "Prevents single-dollar spans from being parsed as LaTeX."),
            modelFeature(ATTACHMENT_GUIDE_PROMPTS, "上传图片候选语句",
                    "Attachment prompt suggestions",
                    "上传图片或文件后，在输入框上方显示原生候选语句。",
                    "Shows DeepSeek's native prompt suggestions above the composer after "
                            + "an image or file is attached."),
            modelFeature(HOME_WELCOME_MESSAGES, "主页模型随机候选词",
                    "Random home model prompts",
                    "控制服务器下发的主页随机欢迎语，例如“该从哪里开始”。强制关闭后使用宿主内置默认文案。",
                    "Controls server-delivered random home greetings. Off restores the host's "
                            + "built-in default greeting."),
            modelFeature(V236_FORCE_EXPERT_MODEL, "强制显示专家模式（2.3.6）",
                    "Force Expert model (2.3.6)",
                    "2.3.6 热更新已停用专家模式；强制恢复原生专家模型入口。",
                    "The 2.3.6 rollout disabled Expert; force its native model entry back."),
            modelFeature(V236_FORCE_VISION_MODEL, "强制显示识图模式（2.3.6）",
                    "Force Vision model (2.3.6)",
                    "2.3.6 热更新已停用独立识图模式；强制恢复原生识图模型入口。",
                    "The 2.3.6 rollout disabled standalone Vision; force its native entry back."),
            modelFeature(V241_FORCE_EXPERT_MODEL, "强制显示专家模式",
                    "Force Expert model",
                    "热更新已停用专家模式；强制恢复原生专家模型入口。",
                    "The current rollout disabled Expert; force its native model entry back."),
            modelFeature(V241_FORCE_VISION_MODEL, "强制显示识图模式",
                    "Force Vision model",
                    "热更新已停用独立识图模式；强制恢复原生识图模型入口。",
                    "The current rollout disabled standalone Vision; force its native entry back.")
    };

    static final Parameter[] PARAMETERS = {
            parameter("input_view_voice_gesture_duration_ms", "语音手势触发时长（ms）",
                    "Voice gesture duration (ms)", "长按/手势进入语音的触发时长。",
                    "Gesture duration before entering voice input."),
            parameter("max_duration_ms", "最长录音时长（ms）", "Max recording duration (ms)",
                    "单次语音输入的最长录音时间。", "Maximum duration of one voice recording."),
            parameter("record_stop_delay_ms", "录音停止延迟（ms）", "Recording stop delay (ms)",
                    "松手后的录音停止延迟。", "Delay before recording stops after release."),
            parameter("record_empty_detect_time_ms", "空录音检测（ms）", "Empty-recording detection (ms)",
                    "连续静音检测时长；填 0 表示关闭。", "Continuous-silence detection; 0 disables it."),
            parameter("opus_bitrate", "语音编码码率", "Opus bitrate",
                    "录音 Opus 编码码率。异常数值可能使语音不可用。",
                    "Opus recording bitrate. Invalid values may break voice input."),
            parameter("pow_prefetch_count", "PoW 预取数量", "PoW prefetch count",
                    "提前预取的 PoW 数量，数值过大可能增加风控风险。",
                    "Number of PoW challenges to prefetch; large values may increase risk controls."),
            parameter("session_prefetch_count", "会话预取数量", "Session prefetch count",
                    "提前加载的会话数量，数值过大可能增加请求量。",
                    "Number of sessions to prefetch; large values increase request volume."),
            parameter("max_input_file_count", "最大输入文件数", "Maximum input file count",
                    "单次允许选择的输入文件数；服务器仍可拒绝超限请求。",
                    "Files selectable per request; the server may still reject excess files."),
            parameter("max_upload_file_size", "最大上传文件大小（字节）", "Maximum upload size (bytes)",
                    "仅修改本地界面限制，不保证服务端接受。", "Changes only the local UI limit; server acceptance is not guaranteed.")
    };

    private static final Object LOCK = new Object();
    private static volatile Map<String, Integer> modes = Collections.emptyMap();
    private static volatile Map<String, Integer> parameterValues = Collections.emptyMap();
    private static volatile Map<String, Boolean> legacyServerValues = Collections.emptyMap();
    private static volatile boolean loaded;
    private static volatile boolean installed;
    private static volatile boolean migrated;
    private static volatile int loadedFormatVersion;

    interface WriteCallback {
        void onComplete(boolean success);
    }

    private RemoteFeatureFlags() {}

    private static Feature feature(String suffix, String zh, String en,
            String detailZh, String detailEn) {
        return new Feature("kv_remote_settings_" + suffix, zh, en, detailZh, detailEn,
                false, true);
    }

    private static Feature scopedFeature(String suffix, String zh, String en,
            String detailZh, String detailEn) {
        return feature(suffix, zh, en, detailZh, detailEn);
    }

    private static Parameter parameter(String suffix, String zh, String en,
            String detailZh, String detailEn) {
        return new Parameter(suffix, zh, en, detailZh, detailEn);
    }

    static Parameter parameterForKey(String key) {
        for (Parameter parameter : PARAMETERS) if (parameter.key.equals(key)) return parameter;
        return null;
    }

    static boolean isSupported(Parameter parameter) {
        return parameter != null && (HostCompat.isV236() || HostCompat.isV241());
    }

    static Integer parameterValue(ClassLoader loader, String key) {
        Parameter parameter = parameterForKey(key);
        if (!isSupported(parameter)) return null;
        SharedPreferences preferences = AccountManager.defaultMmkv(loader);
        if (preferences != null) {
            String local = localKey(key);
            try {
                if (preferences.contains(local)) return Integer.valueOf(preferences.getInt(local, 0));
            } catch (Throwable ignored) {}
        }
        ensureLoaded();
        return parameterValues.get(key);
    }

    static boolean setParameterValue(ClassLoader loader, String key, int value) {
        Parameter parameter = parameterForKey(key);
        SharedPreferences preferences = AccountManager.defaultMmkv(loader);
        if (!isSupported(parameter) || preferences == null) return false;
        synchronized (LOCK) {
            ensureLoadedLocked();
            try {
                String local = localKey(key);
                boolean oldPresent = preferences.contains(local);
                int old = oldPresent ? preferences.getInt(local, 0) : 0;
                if (!preferences.edit().putInt(local, value).commit()) return false;
                HashMap<String, Integer> next = new HashMap<>(parameterValues);
                next.put(key, Integer.valueOf(value));
                if (!saveLocked(modes, next)) {
                    SharedPreferences.Editor rollback = preferences.edit();
                    if (oldPresent) rollback.putInt(local, old); else rollback.remove(local);
                    rollback.commit();
                    return false;
                }
                parameterValues = Collections.unmodifiableMap(next);
                return true;
            } catch (Throwable error) {
                Main.log("native parameter write failed key=" + key + ": " + error);
                return false;
            }
        }
    }

    private static Feature invertedFeature(String suffix, String zh, String en,
            String detailZh, String detailEn) {
        return new Feature("kv_remote_settings_" + suffix, zh, en, detailZh, detailEn,
                true, true);
    }

    private static Feature modelFeature(String key, String zh, String en,
            String detailZh, String detailEn) {
        return new Feature(key, zh, en, detailZh, detailEn, false, false);
    }

    static String localKey(String remoteKey) {
        final String prefix = "kv_remote_settings_";
        if (remoteKey == null || !remoteKey.startsWith(prefix)) return remoteKey;
        return "kv_settings_" + remoteKey.substring(prefix.length());
    }

    static Feature featureForKey(String key) {
        for (Feature feature : FEATURES) if (feature.key.equals(key)) return feature;
        return null;
    }

    private static boolean wasManagedByVersion1(String key) {
        if (featureForKey(key) != null) return true;
        return "kv_remote_settings_show_new_chat_button_above_input".equals(key)
                || "kv_remote_settings_hide_assistant_avatar".equals(key)
                || "kv_remote_settings_sse_auto_scroll_one_screen".equals(key);
    }

    static boolean isSupported(Feature feature) {
        if (feature == null) return false;
        if (V236_V241_KEYS.contains(feature.key)) {
            return HostCompat.isV236() || HostCompat.isV241();
        }
        if (V241_DEFAULT_VOICE_INPUT.equals(feature.key)) return HostCompat.isV241();
        if ((V236_FORCE_EXPERT_MODEL.equals(feature.key)
                        || V236_FORCE_VISION_MODEL.equals(feature.key))
                && HostCompat.isV236()) {
            return true;
        }
        if ((V241_FORCE_EXPERT_MODEL.equals(feature.key)
                        || V241_FORCE_VISION_MODEL.equals(feature.key))
                && HostCompat.isV241()) {
            return true;
        }
        return (feature.nativeBoolean && !V241_DEFAULT_VOICE_INPUT.equals(feature.key))
                || (ATTACHMENT_GUIDE_PROMPTS.equals(feature.key)
                        && (HostCompat.isV234() || HostCompat.isV241()))
                || (HOME_WELCOME_MESSAGES.equals(feature.key)
                        && (HostCompat.isV236() || HostCompat.isV241()));
    }

    static boolean hostValue(Feature feature, boolean userValue) {
        return feature != null && feature.inverted ? !userValue : userValue;
    }

    static int userModeFromHost(Feature feature, boolean hostValue) {
        return hostValue(feature, hostValue) ? FORCE_ON : FORCE_OFF;
    }

    /** Config fallback used before the host MMKV is available. */
    static int mode(String key) {
        ensureLoaded();
        Integer value = modes.get(key);
        return value == null ? FOLLOW : value.intValue();
    }

    /** The actual state in DeepSeek's own local-settings layer. */
    static int mode(ClassLoader loader, String key) {
        Feature feature = featureForKey(key);
        if (feature != null && !feature.nativeBoolean) return mode(key);
        SharedPreferences preferences = AccountManager.defaultMmkv(loader);
        String local = localKey(key);
        if (preferences != null && local != null) {
            try {
                if (preferences.contains(local)) {
                    return userModeFromHost(featureForKey(key),
                            preferences.getBoolean(local, false));
                }
                return FOLLOW;
            } catch (Throwable ignored) {}
        }
        return mode(key);
    }

    static boolean effectiveValue(ClassLoader loader, String key) {
        int actualMode = mode(loader, key);
        if (actualMode != FOLLOW) return actualMode == FORCE_ON;
        return rawValue(loader, key, false);
    }

    /** Reads the untouched value last delivered by DeepSeek's server. */
    static boolean rawValue(ClassLoader loader, String key, boolean fallback) {
        Feature feature = featureForKey(key);
        // 2.3.4 ships both the image/file prompt resources and hard-coded fallback lists. The
        // server can replace those lists through model_configs_v1, but there is no Boolean MMKV
        // key to read. Therefore FOLLOW accurately means using the native available state.
        if (feature != null && !feature.nativeBoolean) {
            if (V236_FORCE_EXPERT_MODEL.equals(feature.key)
                    || V236_FORCE_VISION_MODEL.equals(feature.key)
                    || V241_FORCE_EXPERT_MODEL.equals(feature.key)
                    || V241_FORCE_VISION_MODEL.equals(feature.key)) return false;
            if (HOME_WELCOME_MESSAGES.equals(feature.key)) {
                SharedPreferences preferences = AccountManager.defaultMmkv(loader);
                return preferences != null && preferences.contains(HOME_WELCOME_MESSAGES);
            }
            return true;
        }
        SharedPreferences preferences = AccountManager.defaultMmkv(loader);
        if (preferences == null) return fallback;
        boolean hostFallback = hostValue(feature, fallback);
        try { return hostValue(feature, preferences.getBoolean(key, hostFallback)); }
        catch (Throwable ignored) { return fallback; }
    }

    static boolean setMode(ClassLoader loader, String key, int wanted) {
        Feature feature = featureForKey(key);
        if (feature == null || !isSupported(feature)
                || (wanted != FORCE_OFF && wanted != FOLLOW && wanted != FORCE_ON)) return false;
        SharedPreferences preferences = AccountManager.defaultMmkv(loader);
        if (feature.nativeBoolean && preferences == null) return false;
        synchronized (LOCK) {
            ensureLoadedLocked();
            HashMap<String, Integer> next = new HashMap<>(modes);
            if (wanted == FOLLOW) next.remove(key); else next.put(key, wanted);
            try {
                if (!feature.nativeBoolean) {
                    if (!saveLocked(next, parameterValues)) return false;
                    modes = Collections.unmodifiableMap(next);
                    return true;
                }
                String local = localKey(key);
                boolean oldPresent = preferences.contains(local);
                boolean oldValue = oldPresent && preferences.getBoolean(local, false);
                SharedPreferences.Editor editor = preferences.edit();
                if (wanted == FOLLOW) editor.remove(local);
                else editor.putBoolean(local,
                        hostValue(feature, wanted == FORCE_ON));
                if (!editor.commit()) return false;
                boolean verified = wanted == FOLLOW
                        ? !preferences.contains(local)
                        : preferences.contains(local) && preferences.getBoolean(local, false)
                                == hostValue(feature, wanted == FORCE_ON);
                if (!verified || !saveLocked(next, parameterValues)) {
                    SharedPreferences.Editor rollback = preferences.edit();
                    if (oldPresent) rollback.putBoolean(local, oldValue);
                    else rollback.remove(local);
                    rollback.commit();
                    Main.log("native feature-setting verification failed key=" + key
                            + " verified=" + verified);
                    return false;
                }
            } catch (Throwable error) {
                Main.log("native feature-setting write failed key=" + key + ": " + error);
                return false;
            }
            modes = Collections.unmodifiableMap(next);
            return true;
        }
    }

    /**
     * Exact code257 UI adapter. MMKV's synchronous commit and the atomic JSON mirror both touch
     * storage, so doing them from a switch callback made the Compose host visibly pause. Older
     * host branches continue to use {@link #setMode(ClassLoader, String, int)} unchanged.
     */
    static void setModeV241Async(final ClassLoader loader, final String key, final int wanted,
                                 final WriteCallback callback) {
        if (!HostCompat.isV241()) {
            if (callback != null) callback.onComplete(false);
            return;
        }
        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                final boolean success = setMode(loader, key, wanted);
                if (callback == null) return;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override public void run() { callback.onComplete(success); }
                });
            }
        }, "DeePX-code257-feature-write");
        worker.setDaemon(true);
        worker.start();
    }

    static boolean resetAll(ClassLoader loader) {
        SharedPreferences preferences = AccountManager.defaultMmkv(loader);
        if (preferences == null) return false;
        synchronized (LOCK) {
            ensureLoadedLocked();
            try {
                HashMap<String, Boolean> previous = new HashMap<>();
                HashMap<String, Integer> previousParameters = new HashMap<>();
                SharedPreferences.Editor editor = preferences.edit();
                for (Feature feature : FEATURES) {
                    if (!feature.nativeBoolean) continue;
                    String local = localKey(feature.key);
                    if (preferences.contains(local)) {
                        previous.put(local, preferences.getBoolean(local, false));
                    }
                    editor.remove(local);
                }
                for (Parameter parameter : PARAMETERS) {
                    String local = localKey(parameter.key);
                    if (preferences.contains(local)) previousParameters.put(local,
                            Integer.valueOf(preferences.getInt(local, 0)));
                    editor.remove(local);
                }
                if (!editor.commit()) return false;
                for (Feature feature : FEATURES) {
                    if (feature.nativeBoolean
                            && preferences.contains(localKey(feature.key))) return false;
                }
                if (!saveLocked(Collections.<String, Integer>emptyMap(),
                        Collections.<String, Integer>emptyMap())) {
                    SharedPreferences.Editor rollback = preferences.edit();
                    for (Map.Entry<String, Boolean> entry : previous.entrySet()) {
                        rollback.putBoolean(entry.getKey(), entry.getValue());
                    }
                    for (Map.Entry<String, Integer> entry : previousParameters.entrySet()) {
                        rollback.putInt(entry.getKey(), entry.getValue().intValue());
                    }
                    rollback.commit();
                    return false;
                }
            } catch (Throwable error) {
                Main.log("native feature-setting reset failed: " + error);
                return false;
            }
            modes = Collections.emptyMap();
            parameterValues = Collections.emptyMap();
            return true;
        }
    }

    /** Exact code257 counterpart to {@link #setModeV241Async}. */
    static void resetAllV241Async(final ClassLoader loader, final WriteCallback callback) {
        if (!HostCompat.isV241()) {
            if (callback != null) callback.onComplete(false);
            return;
        }
        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                final boolean success = resetAll(loader);
                if (callback == null) return;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override public void run() { callback.onComplete(success); }
                });
            }
        }, "DeePX-code257-feature-reset");
        worker.setDaemon(true);
        worker.start();
    }

    static int overriddenCount() {
        ensureLoaded();
        return modes.size();
    }

    static int overriddenCount(ClassLoader loader) {
        int count = 0;
        for (Feature feature : FEATURES) {
            if (isSupported(feature) && mode(loader, feature.key) != FOLLOW) count++;
        }
        return count;
    }

    /**
     * Applies overrides before DeepSeek constructs its feature repositories. This is the same
     * layer used by DeepSeek's own internal settings screen, so cached server values cannot win.
     */
    static void install(Main module, ClassLoader loader) {
        if (module == null || loader == null || installed) return;
        synchronized (LOCK) {
            if (installed) return;
            ensureLoadedLocked();
            installed = true;
            Main.log("installed DeepSeek native feature-setting manager (2.2.x/2.3.x)");
        }
    }

    static void enforce(ClassLoader loader) {
        synchronized (LOCK) {
            ensureLoadedLocked();
            // MMKV may not yet be initialised at the package-load callback. Activity resume is the
            // first stable host lifecycle point; perform the one-time v1 migration here.
            if (!migrated) migrated = migrateAndEnforceLocked(loader);
            else enforceLocked(loader, false);
        }
    }

    private static boolean migrateAndEnforceLocked(ClassLoader loader) {
        SharedPreferences preferences = AccountManager.defaultMmkv(loader);
        if (preferences == null) return false;
        try {
            SharedPreferences.Editor editor = preferences.edit();

            // Version 1 incorrectly overwrote the server layer. Restore its remembered value once.
            for (Map.Entry<String, Boolean> entry : legacyServerValues.entrySet()) {
                if (wasManagedByVersion1(entry.getKey()) && entry.getValue() != null) {
                    editor.putBoolean(entry.getKey(), entry.getValue().booleanValue());
                }
            }

            // v2/v3 could leave this remote key carrying the module's former forced value. The
            // host default is hide=true (show=false) in every inspected generation. Remove the
            // polluted value and its rollout id once, then let DeepSeek refresh it normally.
            if (loadedFormatVersion >= 2 && loadedFormatVersion < 4) {
                editor.remove("kv_remote_settings_hide_assistant_avatar");
                editor.remove("kv_remote_settings_id_hide_assistant_avatar");
            }

            // Preserve overrides made by DeepSeek's own hidden settings UI when adopting v2.
            HashMap<String, Integer> adopted = new HashMap<>(modes);
            for (Feature feature : FEATURES) {
                if (!feature.nativeBoolean) continue;
                String local = localKey(feature.key);
                if (!adopted.containsKey(feature.key) && preferences.contains(local)) {
                    adopted.put(feature.key, userModeFromHost(feature,
                            preferences.getBoolean(local, false)));
                }
            }
            modes = Collections.unmodifiableMap(adopted);
            for (Map.Entry<String, Integer> entry : adopted.entrySet()) {
                Feature feature = featureForKey(entry.getKey());
                if (feature != null && feature.nativeBoolean && isSupported(feature)) {
                    editor.putBoolean(localKey(entry.getKey()),
                            hostValue(feature, entry.getValue() == FORCE_ON));
                }
            }
            if (!editor.commit()) return false;
            for (Map.Entry<String, Integer> entry : adopted.entrySet()) {
                Feature feature = featureForKey(entry.getKey());
                if (feature != null && feature.nativeBoolean && isSupported(feature)) {
                    boolean user = entry.getValue() == FORCE_ON;
                    Main.log("native feature override applied key=" + entry.getKey()
                            + " user=" + user + " host=" + hostValue(feature, user));
                }
            }
            legacyServerValues = Collections.emptyMap();
            return saveLocked(adopted, parameterValues);
        } catch (Throwable error) {
            Main.log("native feature-setting migration failed: " + error);
            return false;
        }
    }

    private static boolean enforceLocked(ClassLoader loader, boolean commit) {
        SharedPreferences preferences = AccountManager.defaultMmkv(loader);
        if (preferences == null) return false;
        try {
            SharedPreferences.Editor editor = preferences.edit();
            for (Map.Entry<String, Integer> entry : modes.entrySet()) {
                Feature feature = featureForKey(entry.getKey());
                if (feature != null && feature.nativeBoolean && isSupported(feature)) {
                    editor.putBoolean(localKey(entry.getKey()),
                            hostValue(feature, entry.getValue() == FORCE_ON));
                }
            }
            for (Map.Entry<String, Integer> entry : parameterValues.entrySet()) {
                Parameter parameter = parameterForKey(entry.getKey());
                if (isSupported(parameter)) {
                    editor.putInt(localKey(entry.getKey()), entry.getValue().intValue());
                }
            }
            if (commit) return editor.commit();
            editor.apply();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void ensureLoaded() {
        if (loaded) return;
        synchronized (LOCK) { ensureLoadedLocked(); }
    }

    private static void ensureLoadedLocked() {
        if (loaded) return;
        HashMap<String, Integer> loadedModes = new HashMap<>();
        HashMap<String, Integer> loadedParameters = new HashMap<>();
        HashMap<String, Boolean> loadedLegacyServer = new HashMap<>();
        int formatVersion = 0;
        File file = new File(CONFIG_FILE);
        if (file.isFile() && file.length() <= 64 * 1024L) {
            FileReader reader = null;
            try {
                reader = new FileReader(file);
                StringBuilder json = new StringBuilder((int) file.length());
                char[] buffer = new char[4096];
                int count;
                while ((count = reader.read(buffer)) >= 0) {
                    if (count > 0) json.append(buffer, 0, count);
                }
                JSONObject root = new JSONObject(json.length() == 0 ? "{}" : json.toString());
                formatVersion = root.optInt("version", 0);
                JSONObject overrides = root.optJSONObject("overrides");
                if (overrides != null) {
                    Iterator<String> keys = overrides.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        int value = overrides.optInt(key, FOLLOW);
                        if (featureForKey(key) != null && value != FOLLOW) {
                            loadedModes.put(key, value > 0 ? FORCE_ON : FORCE_OFF);
                        }
                    }
                }
                JSONObject values = root.optJSONObject("values");
                if (values != null) {
                    Iterator<String> keys = values.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        if (parameterForKey(key) != null && values.has(key)) {
                            loadedParameters.put(key, Integer.valueOf(values.optInt(key)));
                        }
                    }
                }
                // Only v1 wrote this object. It is consumed during install and omitted thereafter.
                JSONObject originals = root.optJSONObject("server_values");
                if (originals != null) {
                    Iterator<String> keys = originals.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        if (wasManagedByVersion1(key) && originals.has(key)) {
                            loadedLegacyServer.put(key, originals.optBoolean(key));
                        }
                    }
                }
            } catch (Throwable ignored) {
                loadedModes.clear();
                loadedParameters.clear();
                loadedLegacyServer.clear();
                formatVersion = 0;
            } finally {
                if (reader != null) try { reader.close(); } catch (Throwable ignored) {}
            }
        }
        modes = Collections.unmodifiableMap(loadedModes);
        parameterValues = Collections.unmodifiableMap(loadedParameters);
        legacyServerValues = Collections.unmodifiableMap(loadedLegacyServer);
        loadedFormatVersion = formatVersion;
        loaded = true;
    }

    private static boolean saveLocked(Map<String, Integer> nextModes,
            Map<String, Integer> nextParameters) {
        File target = new File(CONFIG_FILE);
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return false;
        File temp = new File(CONFIG_FILE + ".tmp");
        FileWriter writer = null;
        try {
            JSONObject root = new JSONObject();
            root.put("format", "deekseep-native-feature-overrides");
            root.put("version", 4);
            JSONObject overrides = new JSONObject();
            for (Map.Entry<String, Integer> entry : nextModes.entrySet()) {
                if (featureForKey(entry.getKey()) != null && entry.getValue() != FOLLOW) {
                    overrides.put(entry.getKey(), entry.getValue().intValue());
                }
            }
            root.put("overrides", overrides);
            JSONObject values = new JSONObject();
            for (Map.Entry<String, Integer> entry : nextParameters.entrySet()) {
                if (parameterForKey(entry.getKey()) != null) {
                    values.put(entry.getKey(), entry.getValue().intValue());
                }
            }
            root.put("values", values);
            writer = new FileWriter(temp, false);
            writer.write(root.toString());
            writer.flush();
            writer.close();
            writer = null;
            if (target.exists() && !target.delete()) return false;
            return temp.renameTo(target);
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (writer != null) try { writer.close(); } catch (Throwable ignored) {}
            if (temp.exists() && !temp.equals(target)) {
                try { temp.delete(); } catch (Throwable ignored) {}
            }
        }
    }
}
