package com.smallhairball.deepx;

import android.app.Activity;

import java.util.ArrayList;
import java.util.List;

/**
 * Data bridge for the "外观设置" (chat appearance) page rendered with Miuix inside the
 * injected DeepSeek process. All state lives in {@link ChatAppearance}; this class only
 * exposes read/write helpers plus a flat list of rows so the Compose layer stays dumb.
 */
public final class AppearanceSettingsBridge {

    public static final String TYPE_SWITCH = "switch";
    public static final String TYPE_LINK = "link";

    private AppearanceSettingsBridge() {}

    /** One row shown on the appearance settings page. */
    public static final class Row {
        public final String id;
        public final String title;
        public final String subtitle;
        public final String type;
        public final boolean checked;

        Row(String id, String title, String subtitle, String type, boolean checked) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.type = type;
            this.checked = checked;
        }
    }

    private static Row toggle(String id, String title, String subtitle, boolean checked) {
        return new Row(id, title, subtitle, TYPE_SWITCH, checked);
    }

    private static Row link(String id, String title, String subtitle) {
        return new Row(id, title, subtitle, TYPE_LINK, false);
    }

    /**
     * Top-level rows. Choice rows (bubble style, glass quality, wallpaper mode ...) are
     * added by later revisions; this first pass covers the master switches and the three
     * primary actions so the page shell can be built.
     */
    public static List<Row> rows() {
        List<Row> out = new ArrayList<>();
        ChatAppearance.Config config = load();
        out.add(toggle("enabled", "启用聊天背景与贴纸", "聊天背景总开关", config.enabled));
        out.add(link("import_image", "导入图片", "设置聊天背景或贴纸图片"));
        out.add(toggle("liquid_glass", "全局液态玻璃", "毛玻璃质感与画质", config.liquidGlassEnabled));
        out.add(link("glass_quality", "液态玻璃画质", "当前: " + choiceSummary("glass_quality")));
        out.add(toggle("depth", "景深效果", "背景虚化层次", config.depthEnabled));
        out.add(toggle("motion", "动态背景", "背景随界面缓慢移动", config.motionEnabled));
        out.add(toggle("shake_parallax", "摇一摇视差", "晃动设备产生视差", config.shakeParallaxEnabled));
        out.add(toggle("spatial_depth", "空间深度", "3D 空间感与朝向", config.spatialDepthEnabled));
        out.add(link("spatial_strength", "空间强度", "当前: " + choiceSummary("spatial_strength")));
        out.add(toggle("bubble", "聊天气泡", "自定义用户与助手气泡", config.bubbleEnabled));
        out.add(link("bubble_style", "气泡风格", "当前: " + choiceSummary("bubble_style")));
        out.add(link("preview", "布局预览", "查看背景与贴纸效果"));
        out.add(link("reset", "重置全部聊天外观", "清除所有背景、贴纸与气泡设置"));
        return out;
    }

    /** Flips a switch row. Returns the resulting checked state. */
    public static boolean toggle(String id) {
        ChatAppearance.Config config = load();
        if (id == null) return false;
        switch (id) {
            case "enabled":
                config.enabled = !config.enabled;
                break;
            case "liquid_glass":
                config.liquidGlassEnabled = !config.liquidGlassEnabled;
                break;
            case "depth":
                config.depthEnabled = !config.depthEnabled;
                break;
            case "motion":
                config.motionEnabled = !config.motionEnabled;
                break;
            case "shake_parallax":
                config.shakeParallaxEnabled = !config.shakeParallaxEnabled;
                break;
            case "spatial_depth":
                config.spatialDepthEnabled = !config.spatialDepthEnabled;
                break;
            case "bubble":
                config.bubbleEnabled = !config.bubbleEnabled;
                break;
            default:
                return false;
        }
        ChatAppearance.save(config);
        return currentChecked(id);
    }

    private static boolean currentChecked(String id) {
        ChatAppearance.Config config = load();
        switch (id) {
            case "enabled": return config.enabled;
            case "liquid_glass": return config.liquidGlassEnabled;
            case "depth": return config.depthEnabled;
            case "motion": return config.motionEnabled;
            case "shake_parallax": return config.shakeParallaxEnabled;
            case "spatial_depth": return config.spatialDepthEnabled;
            case "bubble": return config.bubbleEnabled;
            default: return false;
        }
    }

    /** Runs a link/action row. Choice rows are wired up in later revisions. */
    public static void action(Activity act, String id) {
        if (act == null || id == null) return;
        switch (id) {
            case "preview":
                // Layout preview is the drag-and-drop canvas editor; it stays on the legacy
                // full-screen page for now and is opened directly from the Compose layer.
                break;
            default:
                break;
        }
    }

    /** Result of picking + importing a wallpaper/sticker image, delivered on the UI thread. */
    public interface ImportCallback {
        void onResult(boolean ok, String message);
    }

    /**
     * Opens the system gallery picker and, once an image is chosen, imports it using the role
     * chosen by [background] ({@code true} = wallpaper, {@code false} = page sticker).
     */
    public static void pickAndImport(final Activity act, final boolean background,
                                     final ImportCallback callback) {
        if (act == null) return;
        try {
            Main.pickGalleryImage(act, new Main.GalleryPickCallback() {
                @Override
                public void onPicked(final android.net.Uri uri) {
                    if (uri == null) {
                        if (callback != null) callback.onResult(false, "没有选择图片");
                        return;
                    }
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            final ChatAppearance.ImportResult result =
                                    ChatAppearance.importImage(act, uri, background);
                            act.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (callback != null && result != null) {
                                        callback.onResult(result.ok, result.message);
                                    }
                                }
                            });
                        }
                    }, "DeePX-appearance-import").start();
                }
            });
        } catch (Throwable ignored) {
            if (callback != null) callback.onResult(false, "打开相册失败");
        }
    }

    /** Clears every wallpaper, sticker and bubble setting. Returns whether it succeeded. */
    public static boolean resetAll() {
        try {
            return ChatAppearance.clearAll();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Opens the legacy full-screen appearance editor (drag-and-drop layout preview). */
    public static void openLegacyEditor(Activity act) {
        if (act == null) return;
        try {
            ChatAppearanceUi.show(act);
        } catch (Throwable ignored) {
            // Ignore: the legacy editor is best-effort.
        }
    }

    /** A selectable option: [value] is the persisted key, [label] is what the user sees. */
    public static final class Choice {
        public final String value;
        public final String label;

        Choice(String value, String label) {
            this.value = value;
            this.label = label;
        }
    }

    /** Returns the options for a choice row, or an empty list if [id] is not a choice row. */
    public static List<Choice> choices(String id) {
        List<Choice> out = new ArrayList<>();
        if (id == null) return out;
        switch (id) {
            case "glass_quality":
                out.add(new Choice("auto", "自动"));
                out.add(new Choice("high", "高"));
                out.add(new Choice("balanced", "均衡"));
                out.add(new Choice("saver", "省电"));
                break;
            case "spatial_strength":
                out.add(new Choice("weak", "弱"));
                out.add(new Choice("standard", "标准"));
                out.add(new Choice("strong", "强"));
                break;
            case "bubble_style":
                out.add(new Choice("original", "原始"));
                out.add(new Choice("soft", "柔和"));
                out.add(new Choice("outline", "轻描边"));
                out.add(new Choice("glass", "磨砂玻璃"));
                out.add(new Choice("liquid", "液态玻璃"));
                break;
            default:
                break;
        }
        return out;
    }

    /** Returns the currently selected value for a choice row, or "" when unknown. */
    public static String currentChoice(String id) {
        ChatAppearance.Config config = load();
        if (id == null) return "";
        switch (id) {
            case "glass_quality":
                return config.glassQuality == null ? "auto" : config.glassQuality;
            case "spatial_strength":
                return config.spatialStrength == null ? "standard" : config.spatialStrength;
            case "bubble_style":
                try {
                    return config.bubble(false).preset;
                } catch (Throwable ignored) {
                    return "glass";
                }
            default:
                return "";
        }
    }

    /** Writes the selected value for a choice row back into the appearance config. */
    public static void applyChoice(String id, String value) {
        if (id == null || value == null) return;
        ChatAppearance.Config config = load();
        switch (id) {
            case "glass_quality":
                config.glassQuality = value;
                break;
            case "spatial_strength":
                config.spatialStrength = value;
                break;
            case "bubble_style":
                try {
                    ChatAppearance.BubbleStyle style = config.bubble(false);
                    style.preset = value;
                } catch (Throwable ignored) {
                    return;
                }
                break;
            default:
                return;
        }
        ChatAppearance.save(config);
    }

    /** Short human-readable summary for a choice row, used as the row subtitle. */
    public static String choiceSummary(String id) {
        String current = currentChoice(id);
        for (Choice choice : choices(id)) {
            if (choice.value.equals(current)) return choice.label;
        }
        return "";
    }

    private static ChatAppearance.Config load() {
        try {
            ChatAppearance.Config config = ChatAppearance.load();
            return config == null ? new ChatAppearance.Config() : config;
        } catch (Throwable ignored) {
            return new ChatAppearance.Config();
        }
    }
}