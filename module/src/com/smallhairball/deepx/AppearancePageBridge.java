package com.smallhairball.deepx;

import android.app.Activity;

import java.util.ArrayList;
import java.util.List;

/**
 * Data/action bridge for the "界面美化" (Appearance) page rendered by the Compose/Miuix layer.
 * State and actions stay here; the Compose side only draws Miuix widgets.
 */
public final class AppearancePageBridge {

    public static final int TYPE_LINK = 0;
    public static final int TYPE_SWITCH = 1;

    /** One row on the appearance page. */
    public static final class Row {
        public final String id;
        public final String title;
        public final String subtitle;
        public final int type;
        public final boolean checked;
        public final boolean hasGear;

        Row(String id, String title, String subtitle, int type, boolean checked, boolean hasGear) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.type = type;
            this.checked = checked;
            this.hasGear = hasGear;
        }
    }

    private AppearancePageBridge() {}

    /** Builds the current appearance page rows, matching the module's own appearance page. */
    public static List<Row> rows() {
        List<Row> out = new ArrayList<>();
        out.add(link("greeting", "主页欢迎语", greetingDetail()));
        out.add(sw("legacy_entry", "使用旧版入口",
                "开启：使用右上角 DeePX 悬浮入口（旧版）。关闭：使用 DeepSeek 原生设置中的“插件”入口（新版）。",
                Main.isLegacySettingsEntryEnabled()));
        out.add(swGear("theme_color", "主题色", "自定义 DeepSeek 原生主题颜色",
                ThemeColorConfig.get().enabled));
        out.add(link("appearance", "外观设置",
                "自定义背景、贴纸、气泡、透明度、取景和空间动效。"));
        out.add(link("assistant_avatar", "自定义 DeepSeek 头像",
                "需要在灰度功能管理里面开启“显示助手头像”，否则聊天页不会显示自定义头像。"));
        out.add(swGear("whale_motion", "鲸鱼图标动效", whaleMotionDetail(),
                Main.isWelcomeWhaleMotionEnabled()));
        out.add(swGear("text_wave", "深海文字波纹", textWaveDetail(),
                Main.isTextWaveEnabled()));
        return out;
    }

    /** Opens the tuning sheet (gear) for a row that has one. */
    public static void gear(Activity act, String id) {
        // Tuning is now handled entirely in the Compose layer; nothing to forward here.
    }

    // ── Speed tuning (whale / text wave) ─────────────────────────────

    public static float whaleSpeed() {
        try { return Main.welcomeWhaleMotionSpeed(); } catch (Throwable e) { return 0.3f; }
    }

    public static void setWhaleSpeed(float speed) {
        try { Main.setWelcomeWhaleMotionSpeed(speed); } catch (Throwable ignored) {}
    }

    public static float textWaveSpeed() {
        try { return Main.textWaveSpeed(); } catch (Throwable e) { return 0.6f; }
    }

    public static void setTextWaveSpeed(float speed) {
        try { Main.setTextWaveSpeed(speed); } catch (Throwable ignored) {}
    }

    // ── Theme colour ─────────────────────────────────────────────────

    public static boolean themeEnabled() {
        try { return ThemeColorConfig.get().enabled; } catch (Throwable e) { return false; }
    }

    public static void setThemeEnabled(boolean enabled) {
        try { ThemeColorConfig.setEnabled(enabled); } catch (Throwable ignored) {}
    }

    public static int themePrimary() {
        try { return ThemeColorConfig.get().primary; } catch (Throwable e) { return 0xFF4D6BFE; }
    }

    public static int themeSecondary() {
        try { return ThemeColorConfig.get().secondary; } catch (Throwable e) { return 0xFF8B5CF6; }
    }

    public static boolean themeGradient() {
        try { return ThemeColorConfig.get().gradient; } catch (Throwable e) { return false; }
    }

    public static void setThemeColors(int primary, int secondary, boolean gradient) {
        try {
            ThemeColorConfig.Value v = ThemeColorConfig.get();
            v.primary = primary;
            v.secondary = secondary;
            v.gradient = gradient;
            ThemeColorConfig.save(v);
        } catch (Throwable ignored) {}
    }

    // ── Assistant avatar ─────────────────────────────────────────────

    public static boolean hasCustomAvatar() {
        try { return ChatAppearance.assistantAvatarFileForRender() != null; } catch (Throwable e) { return false; }
    }

    /** Imports a chosen avatar image. Returns a human-readable result message. */
    public static String importAvatar(Activity act, android.net.Uri uri) {
        try {
            ChatAppearance.ImportResult result = ChatAppearance.importAssistantAvatar(act, uri);
            return result == null ? "" : result.message;
        } catch (Throwable error) {
            return "导入失败：" + error.getClass().getSimpleName();
        }
    }

    /**
     * Opens the native gallery picker and reports back the chosen image's import result. Uses the
     * module's own picker rather than a Compose launcher, because the host process has no
     * ActivityResultRegistryOwner.
     */
    public static void pickAvatar(final Activity act,
            final java.util.function.Consumer<String> onResult) {
        if (act == null) return;
        try {
            Main.pickGalleryImage(act, new Main.GalleryPickCallback() {
                @Override public void onPicked(android.net.Uri uri) {
                    final String message;
                    if (uri == null) {
                        message = "";
                    } else {
                        message = importAvatar(act, uri);
                    }
                    act.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (onResult != null) onResult.accept(message);
                        }
                    });
                }
            });
        } catch (Throwable error) {
            if (onResult != null) onResult.accept("选择失败：" + error.getClass().getSimpleName());
        }
    }

    private static String greetingDetail() {
        try {
            String greeting = Main.homeGreeting();
            if (greeting == null || greeting.trim().length() == 0) {
                return "当前: 跟随 DeepSeek 默认文案";
            }
            return "当前: " + greeting;
        } catch (Throwable error) {
            return "当前: 跟随 DeepSeek 默认文案";
        }
    }

    private static String whaleMotionDetail() {
        try {
            return String.format(java.util.Locale.US,
                    "首页鲸鱼持续旋转 · %.1f×", Main.welcomeWhaleMotionSpeed());
        } catch (Throwable error) {
            return "首页鲸鱼持续旋转";
        }
    }

    private static String textWaveDetail() {
        try {
            return String.format(java.util.Locale.US,
                    "右上向左下斜落 · 宽波峰约占字宽 1/3 · %.1f×", Main.textWaveSpeed());
        } catch (Throwable error) {
            return "右上向左下斜落";
        }
    }

    /**
     * Toggles a switch row.
     *
     * @return the resulting state (some toggles refuse to change and stay as they were).
     */
    public static boolean toggle(String id) {
        switch (id == null ? "" : id) {
            case "legacy_entry": {
                boolean next = !Main.isLegacySettingsEntryEnabled();
                Main.setLegacySettingsEntryEnabled(next);
                return Main.isLegacySettingsEntryEnabled();
            }
            case "theme_color": {
                boolean next = !ThemeColorConfig.get().enabled;
                ThemeColorConfig.setEnabled(next);
                return ThemeColorConfig.get().enabled;
            }
            case "whale_motion":
                Main.setWelcomeWhaleMotionEnabled(!Main.isWelcomeWhaleMotionEnabled());
                return Main.isWelcomeWhaleMotionEnabled();
            case "text_wave":
                Main.setTextWaveEnabled(!Main.isTextWaveEnabled());
                return Main.isTextWaveEnabled();
            default:
                return false;
        }
    }

    /** Runs a link row. */
    public static void action(Activity act, String id) {
        if (act == null || id == null) return;
        switch (id) {
            case "appearance":
                ChatAppearanceUi.show(act);
                break;
            case "assistant_avatar":
                ChatAppearanceUi.showAssistantAvatar(act);
                break;
            default:
                break;
        }
    }

    /** Saves the home greeting; empty string restores the default. */
    public static boolean setGreeting(String greeting) {
        try {
            return Main.setHomeGreeting(greeting == null ? "" : greeting);
        } catch (Throwable error) {
            return false;
        }
    }

    private static Row sw(String id, String title, String subtitle, boolean checked) {
        return new Row(id, title, subtitle, TYPE_SWITCH, checked, false);
    }

    private static Row swGear(String id, String title, String subtitle, boolean checked) {
        return new Row(id, title, subtitle, TYPE_SWITCH, checked, true);
    }

    private static Row link(String id, String title, String subtitle) {
        return new Row(id, title, subtitle, TYPE_LINK, false, false);
    }
}