package com.smallhairball.deepx;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Bridge between the Compose "DeepSeek native features" page and
 * {@link RemoteFeatureFlags}. Exposes the supported feature matrix as plain data so the
 * Compose layer can render Miuix rows without touching host internals directly.
 */
public final class FeatureFlagsPageBridge {
    private FeatureFlagsPageBridge() {}

    /** One row on the native-feature page. */
    public static final class Row {
        public final String id;
        public final String title;
        public final String subtitle;
        public final String state;
        public final int mode;
        public final boolean risky;

        Row(String id, String title, String subtitle, String state, int mode, boolean risky) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.state = state;
            this.mode = mode;
            this.risky = risky;
        }
    }

    /** Options shown in the per-row mode picker. */
    public static final class ModeOption {
        public final String label;
        public final int value;

        ModeOption(String label, int value) {
            this.label = label;
            this.value = value;
        }
    }

    public static String summary(Context context) {
        return UiLanguage.text(context,
                "这里集中管理当前 DeepSeek APK 自带的布尔灰度功能与模型配置灰度。选择后会自动重启，让内存缓存同步生效。",
                "This page manages the current DeepSeek APK's Boolean and model-config rollouts. "
                        + "DeepSeek restarts after a change so in-memory caches reload.");
    }

    /** Reads the supported feature rows. */
    public static List<Row> rows(Activity activity) {
        List<Row> out = new ArrayList<>();
        if (activity == null) return out;
        for (RemoteFeatureFlags.Feature feature : RemoteFeatureFlags.FEATURES) {
            if (!RemoteFeatureFlags.isSupported(feature)) continue;
            int mode = RemoteFeatureFlags.mode(activity.getClassLoader(), feature.key);
            boolean risky = isRisky(feature.key);
            out.add(new Row(
                    feature.key,
                    UiLanguage.text(activity, feature.zh, feature.en),
                    UiLanguage.text(activity, feature.detailZh, feature.detailEn),
                    stateLabel(activity, mode),
                    mode,
                    risky));
        }
        return out;
    }

    /** Static translation for a stored mode. */
    static String stateLabel(Context context, int mode) {
        if (mode == RemoteFeatureFlags.FORCE_ON) return UiLanguage.text(context, "强制开", "On");
        if (mode == RemoteFeatureFlags.FORCE_OFF) return UiLanguage.text(context, "强制关", "Off");
        return UiLanguage.text(context, "跟随", "Follow");
    }

    /** True when toggling this feature may change request patterns or device identity. */
    static boolean isRisky(String key) {
        return key.contains("parallel") || key.contains("prefetch")
                || key.contains("report") || key.contains("gcy")
                || key.contains("volcengine") || key.contains("device_id");
    }

    /** The three-state picker options for a row. */
    public static List<ModeOption> modeOptions(Activity activity, String key) {
        List<ModeOption> out = new ArrayList<>();
        if (activity == null) return out;
        RemoteFeatureFlags.Feature feature = RemoteFeatureFlags.featureForKey(key);
        boolean server = feature != null
                && RemoteFeatureFlags.rawValue(activity.getClassLoader(), key, false);
        out.add(new ModeOption(UiLanguage.text(activity,
                "跟随服务器（当前" + (server ? "开启" : "关闭") + "）",
                "Follow server (currently " + (server ? "on" : "off") + ")"),
                RemoteFeatureFlags.FOLLOW));
        out.add(new ModeOption(UiLanguage.text(activity, "强制开启并重启", "Force on and restart"),
                RemoteFeatureFlags.FORCE_ON));
        out.add(new ModeOption(UiLanguage.text(activity, "强制关闭并重启", "Force off and restart"),
                RemoteFeatureFlags.FORCE_OFF));
        return out;
    }

    /** The stored mode for a single feature key. */
    public static int currentMode(Activity activity, String key) {
        if (activity == null) return RemoteFeatureFlags.FOLLOW;
        return RemoteFeatureFlags.mode(activity.getClassLoader(), key);
    }

    /** Warning copy for a risky row, or an empty string when none applies. */
    public static String riskNote(Activity activity) {
        if (activity == null) return "";
        return UiLanguage.text(activity,
                "警告：此项可能增加请求频率、改变设备标识或触发服务器风控。启用后果由使用者自行承担。",
                "Warning: this may increase request frequency, change device identity, or trigger "
                        + "server risk controls. Use at your own risk.");
    }

    /**
     * Writes a new mode for a feature. DeepSeek restarts on success so the in-memory cache
     * reloads. Runs asynchronously on code257 hosts.
     */
    public static void setMode(final Activity activity, final String key, final int wanted) {
        if (activity == null) return;
        if (HostCompat.isV241()) {
            RemoteFeatureFlags.setModeV241Async(activity.getClassLoader(), key, wanted,
                    new RemoteFeatureFlags.WriteCallback() {
                        @Override public void onComplete(boolean success) {
                            finish(activity, success,
                                    "原生设置已写入，正在重启 DeepSeek",
                                    "设置保存失败");
                        }
                    });
            return;
        }
        boolean ok = RemoteFeatureFlags.setMode(activity.getClassLoader(), key, wanted);
        finish(activity, ok, "原生设置已写入，正在重启 DeepSeek", "设置保存失败");
    }

    /** Restores every feature to "follow server" and restarts the host on success. */
    public static void resetAll(final Activity activity) {
        if (activity == null) return;
        if (HostCompat.isV241()) {
            RemoteFeatureFlags.resetAllV241Async(activity.getClassLoader(),
                    new RemoteFeatureFlags.WriteCallback() {
                        @Override public void onComplete(boolean success) {
                            finish(activity, success, "已恢复，正在重启 DeepSeek", "恢复失败");
                        }
                    });
            return;
        }
        boolean ok = RemoteFeatureFlags.resetAll(activity.getClassLoader());
        finish(activity, ok, "已恢复，正在重启 DeepSeek", "恢复失败");
    }

    private static void finish(final Activity activity, boolean success, String okZh, String failZh) {
        if (activity == null) return;
        final String okText = UiLanguage.text(activity, okZh,
                "Native setting saved; restarting DeepSeek");
        final String failText = UiLanguage.text(activity, failZh, "Could not save setting");
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() {
                Toast.makeText(activity, success ? okText : failText, Toast.LENGTH_SHORT).show();
                if (success) restartHost(activity);
            }
        });
    }

    /** Launches DeepSeek so the user's next open reloads the overrides. */
    static void restartHost(Activity activity) {
        if (activity == null) return;
        try {
            Intent intent = activity.getPackageManager()
                    .getLaunchIntentForPackage("com.deepseek.chat");
            if (intent == null) return;
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            activity.startActivity(intent);
        } catch (Throwable ignored) {}
    }
}