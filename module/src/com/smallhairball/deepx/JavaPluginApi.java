package com.smallhairball.deepx;

import android.app.Activity;

import org.json.JSONObject;

import java.util.Collections;
import java.util.List;

/** Stable capability-oriented ABI exposed to imported Java plugins. */
public final class JavaPluginApi {
    public static final int ABI_VERSION = 3;

    private JavaPluginApi() {}

    public interface Plugin {
        void onLoad(Context context) throws Exception;
        void onStart() throws Exception;
        void onStop() throws Exception;
        void onUnload() throws Exception;
    }

    public interface HookCallback {
        void onHook(Event event) throws Exception;
    }

    /** Callback for a plugin-contributed module action row. */
    public interface ActionCallback {
        void onClick(Activity activity) throws Exception;
    }

    /** Generic callback used by stable host capabilities. */
    public interface ResultCallback {
        void onResult(Result result);
    }

    /** One implementation of a named extension point. */
    public interface ExtensionCallback {
        Result onInvoke(JSONObject request) throws Exception;
    }

    /** Handle returned for every dynamic contribution. */
    public interface Registration {
        String id();
        void unregister();
    }

    public interface Context {
        int abiVersion();
        String pluginId();
        /** ABI v3: stable host generation, for diagnostics rather than symbol lookup. */
        String hostVersion();
        /** ABI v3: permissions granted from META-INF/deekseep/plugin.json. */
        boolean hasPermission(String permission);
        List<String> grantedPermissions();
        void subscribe(String hookPoint, HookCallback callback);
        /** ABI v2: subscribe to dynamic runtime points, for example host.member.*.after. */
        void subscribePattern(String hookPattern, HookCallback callback);
        /** Snapshot of concrete runtime phase IDs installed on this host version. */
        List<String> runtimeHookPoints();
        /**
         * Register a row in a module section. ABI v2 placements are
         * module.engineering, module.chat, module.account and module.appearance.
         */
        void registerAction(String placement, String actionId, String title,
                            String description, ActionCallback callback);
        /** ABI v3: invoke a versioned host service, for example runtime.info or account.list. */
        Result call(String capability, JSONObject request);
        void callAsync(String capability, JSONObject request, ResultCallback callback);
        /** ABI v3: contribute an implementation such as agent.tool or chat.outgoing.transform. */
        Registration registerExtension(String extensionPoint, String extensionId,
                                       JSONObject descriptor, ExtensionCallback callback);
        void log(String message);
        String readSetting(String key, String fallback);
        boolean writeSetting(String key, String value);
        Activity currentActivity();
    }

    /** Immutable result envelope. New fields can be added inside data without breaking ABI. */
    public static final class Result {
        public final boolean success;
        public final String code;
        public final String message;
        public final JSONObject data;

        public Result(boolean success, String code, String message, JSONObject data) {
            this.success = success;
            this.code = code == null ? "" : code;
            this.message = message == null ? "" : message;
            this.data = data == null ? new JSONObject() : data;
        }

        public static Result ok(JSONObject data) {
            return new Result(true, "ok", "", data);
        }

        public static Result error(String code, String message) {
            return new Result(false, code, message, new JSONObject());
        }
    }

    public static final class Event {
        public final String hookPoint;
        public final long timestamp;
        public final JSONObject data;

        public Event(String hookPoint, JSONObject data) {
            this.hookPoint = hookPoint == null ? "" : hookPoint;
            this.timestamp = System.currentTimeMillis();
            this.data = data == null ? new JSONObject() : data;
        }

        public String string(String key, String fallback) {
            return data.optString(key, fallback);
        }

        public boolean bool(String key, boolean fallback) {
            return data.has(key) ? data.optBoolean(key, fallback) : fallback;
        }

        public int integer(String key, int fallback) {
            return data.has(key) ? data.optInt(key, fallback) : fallback;
        }

        public long longValue(String key, long fallback) {
            return data.has(key) ? data.optLong(key, fallback) : fallback;
        }
    }
}
