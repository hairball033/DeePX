package com.smallhairball.deepx;

import android.app.Activity;
import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * Bridge between the Compose "Java plugin" page and {@link JavaPluginManager}. Exposes the plugin
 * list, the learning entries and the hook catalogue as plain data for the Miuix layer.
 */
public final class JavaPluginPageBridge {
    private JavaPluginPageBridge() {}

    /** One installed plugin card. */
    public static final class Plugin {
        public final String id;
        public final String name;
        public final String versionAuthor;
        public final String description;
        public final String status;
        public final String statusKind;
        public final boolean enabled;
        public final boolean builtIn;

        Plugin(String id, String name, String versionAuthor, String description, String status,
               String statusKind, boolean enabled, boolean builtIn) {
            this.id = id;
            this.name = name;
            this.versionAuthor = versionAuthor;
            this.description = description;
            this.status = status;
            this.statusKind = statusKind;
            this.enabled = enabled;
            this.builtIn = builtIn;
        }
    }

    /** A learning/development link row. */
    public static final class Link {
        public final String id;
        public final String title;
        public final String subtitle;

        Link(String id, String title, String subtitle) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    /** Header summary of the plugin environment. */
    public static final class Summary {
        public final String title;
        public final String stats;
        public final String warning;
        public final List<Plugin> plugins;
        public final List<Link> links;

        Summary(String title, String stats, String warning, List<Plugin> plugins,
                List<Link> links) {
            this.title = title;
            this.stats = stats;
            this.warning = warning;
            this.plugins = plugins;
            this.links = links;
        }
    }

    public static Summary summary(Activity activity) {
        if (activity == null) return null;
        List<JavaPluginManager.Info> plugins = JavaPluginManager.list(activity);
        int enabled = 0;
        List<Plugin> rows = new ArrayList<>();
        for (JavaPluginManager.Info info : plugins) {
            if (info.enabled) enabled++;
            String status;
            String kind;
            if (info.error != null && info.error.length() > 0) {
                status = UiLanguage.text(activity, "异常 · " + info.error, "Error · " + info.error);
                kind = "error";
            } else if (info.builtIn) {
                status = UiLanguage.text(activity, "内置 · 可随时运行", "Built-in · ready to run");
                kind = "good";
            } else if (info.enabled) {
                status = info.loaded
                        ? UiLanguage.text(activity, "运行中", "Running")
                        : UiLanguage.text(activity, "已启用 · 等待加载", "Enabled · awaiting load");
                kind = "good";
            } else {
                status = UiLanguage.text(activity, "已停用", "Disabled");
                kind = "muted";
            }
            rows.add(new Plugin(
                    info.id,
                    info.name,
                    "v" + info.version + " · " + info.author,
                    info.description == null || info.description.length() == 0
                            ? UiLanguage.text(activity, "没有说明", "No description")
                            : info.description,
                    status,
                    kind,
                    info.enabled,
                    info.builtIn));
        }
        int hookCount = JavaPluginHookCatalog.all().size();
        List<Link> links = new ArrayList<>();
        links.add(new Link("tutorial",
                UiLanguage.text(activity, "使用教程", "Tutorial"),
                UiLanguage.text(activity, "从目录结构、D8 编译到导入和排障。",
                        "From layout and D8 compile to import and troubleshooting.")));
        links.add(new Link("ai_reference",
                UiLanguage.text(activity, "导出给 AI", "Export for AI"),
                UiLanguage.text(activity,
                        "导出当前版本真实可用的接口、参数、约束和逐点调用示例。",
                        "Export the real interfaces, parameters and per-point examples.")));
        links.add(new Link("tutorial_zip",
                UiLanguage.text(activity, "导出教程 ZIP", "Export tutorial ZIP"),
                UiLanguage.text(activity, "包含清单、Java 源码、文件说明、构建教程和完整 Hook 表。",
                        "Includes manifest, Java source, docs, build guide and the full hook table.")));
        links.add(new Link("hook_catalog",
                UiLanguage.text(activity, "Hook 点目录", "Hook catalogue"),
                UiLanguage.text(activity, hookCount + " 个可查询点；包含本机实际成员四阶段事件。",
                        hookCount + " queryable points; includes four-stage member events.")));
        return new Summary(
                UiLanguage.text(activity, "插件运行环境", "Plugin runtime"),
                UiLanguage.text(activity,
                        plugins.size() + " 个插件  ·  " + enabled + " 个启用  ·  "
                                + hookCount + " 个 Hook 点",
                        plugins.size() + " plugins  ·  " + enabled + " enabled  ·  "
                                + hookCount + " hook points"),
                UiLanguage.text(activity,
                        "插件与 DeepSeek 同进程运行。只导入可信 ZIP；Local API 永不向插件开放。",
                        "Plugins run in-process with DeepSeek. Import only trusted ZIPs; the Local "
                                + "API is never exposed to plugins."),
                rows,
                links);
    }

    /** Toggles a plugin. */
    public static boolean setEnabled(Activity activity, String id, boolean enabled) {
        if (activity == null) return false;
        return JavaPluginManager.setEnabled(activity, id, enabled);
    }

    /** Removes a plugin. */
    public static boolean remove(Activity activity, String id) {
        if (activity == null) return false;
        return JavaPluginManager.remove(activity, id);
    }

    /** Result listener for the built-in test, safe to call from the Compose layer. */
    public interface TestListener {
        void onDone(boolean success, String message);
    }

    /** Runs the built-in plugin self-test. */
    public static void runBuiltInTest(Activity activity, TestListener listener) {
        if (activity == null) return;
        JavaPluginManager.runBuiltInTest(activity, new JavaPluginManager.Callback() {
            @Override public void done(boolean success, String message) {
                if (listener != null) listener.onDone(success, message);
            }
        });
    }

    /** Manifest text for a plugin. */
    public static String manifest(Activity activity, String id) {
        if (activity == null) return "";
        for (JavaPluginManager.Info info : JavaPluginManager.list(activity)) {
            if (info.id.equals(id)) {
                StringBuilder text = new StringBuilder()
                        .append("ID\n").append(info.id)
                        .append("\n\n").append(UiLanguage.text(activity, "入口类", "Entry class"))
                        .append("\n").append(info.entryClass)
                        .append("\n\nABI\n").append(info.minAbi).append(" — ")
                        .append(info.maxAbi <= 0 ? UiLanguage.text(activity, "不限", "Any")
                                : String.valueOf(info.maxAbi));
                text.append("\n\n").append(UiLanguage.text(activity, "授权能力", "Permissions"))
                        .append("\n");
                if (info.permissions.isEmpty()) {
                    text.append("• ").append(UiLanguage.text(activity, "无", "None")).append("\n");
                }
                for (String permission : info.permissions) {
                    text.append("• ").append(permission).append("\n");
                }
                text.append("\n").append(UiLanguage.text(activity, "插件依赖", "Dependencies"))
                        .append("\n");
                if (info.dependencies.isEmpty()) {
                    text.append("• ").append(UiLanguage.text(activity, "无", "None")).append("\n");
                }
                for (JavaPluginManager.Dependency dependency : info.dependencies) {
                    text.append("• ").append(dependency.id)
                            .append(dependency.minVersion.length() == 0 ? ""
                                    : " >= " + dependency.minVersion)
                            .append(dependency.optional
                                    ? UiLanguage.text(activity, "（可选）", " (optional)") : "")
                            .append("\n");
                }
                text.append("\n").append(UiLanguage.text(activity, "声明事件", "Declared events"))
                        .append("\n");
                for (String hook : info.hooks) {
                    text.append("• ").append(hook).append("\n");
                }
                return text.toString();
            }
        }
        return "";
    }

    /** Tutorial body text. */
    public static String tutorialText() {
        try { return JavaPluginManager.tutorialText(); } catch (Throwable ignored) { return ""; }
    }

    /** Import-confirmation copy. */
    public static String importWarning(Context context) {
        return UiLanguage.text(context,
                "插件代码将在 DeepSeek 进程内运行，并拥有与应用相同的进程权限；清单权限用于约束正式 API，"
                        + "不是 Android 进程沙箱。只安装你信任且能审查源码的插件。\n\nABI v3 格式要求 ZIP "
                        + "根目录包含 classes.dex，并在 META-INF/deekseep/plugin.json 声明 permissions。",
                "Plugin code runs inside the DeepSeek process with the same process privileges as the "
                        + "app; manifest permissions constrain the official API but are not an Android "
                        + "sandbox. Only install plugins you trust and can review.\n\nABI v3 requires a "
                        + "classes.dex at the ZIP root and a META-INF/deekseep/plugin.json manifest.");
    }

    /** Launches the plugin ZIP picker. */
    public static void pickImport(Activity activity) {
        if (activity == null) return;
        try {
            android.content.Intent intent = new android.content.Intent(
                    android.content.Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(android.content.Intent.CATEGORY_OPENABLE)
                    .setType("application/zip")
                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            activity.startActivityForResult(intent, JavaPluginManager.IMPORT_REQUEST);
        } catch (Throwable ignored) {}
    }

    /** Launches the tutorial-ZIP destination picker. */
    public static void exportTutorial(Activity activity) {
        if (activity == null) return;
        try {
            JavaPluginManager.tutorialArchive(activity);
            android.content.Intent intent = new android.content.Intent(
                    android.content.Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(android.content.Intent.CATEGORY_OPENABLE)
                    .setType("application/zip")
                    .putExtra(android.content.Intent.EXTRA_TITLE,
                            "deepx-java-plugin-tutorial-v3.zip");
            activity.startActivityForResult(intent, JavaPluginManager.IMPORT_REQUEST + 1);
        } catch (Throwable ignored) {}
    }

    /** Launches the AI-reference destination picker. */
    public static void exportAiReference(Activity activity) {
        if (activity == null) return;
        try {
            JavaPluginManager.aiReferenceDocument(activity);
            android.content.Intent intent = new android.content.Intent(
                    android.content.Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(android.content.Intent.CATEGORY_OPENABLE)
                    .setType("text/markdown")
                    .putExtra(android.content.Intent.EXTRA_TITLE,
                            "DeePX-Java-Plugin-AI-Reference-v3.md");
            activity.startActivityForResult(intent, JavaPluginManager.IMPORT_REQUEST + 2);
        } catch (Throwable ignored) {}
    }

    /** One hook catalogue entry. */
    public static final class Hook {
        public final String id;
        public final String title;
        public final String category;
        public final String detail;
        public final boolean wired;

        Hook(String id, String title, String category, String detail, boolean wired) {
            this.id = id;
            this.title = title;
            this.category = category;
            this.detail = detail;
            this.wired = wired;
        }
    }

    /** The hook catalogue as a flat list. */
    public static List<Hook> hooks() {
        List<Hook> out = new ArrayList<>();
        for (JavaPluginHookCatalog.Point point : JavaPluginHookCatalog.all()) {
            out.add(new Hook(point.id, point.title, point.category, point.description,
                    point.wired));
        }
        return out;
    }

    /** Hook catalogue stats text. */
    public static String hookStats(Activity activity) {
        if (activity == null) return "";
        int total = JavaPluginHookCatalog.all().size();
        int wired = 0;
        for (JavaPluginHookCatalog.Point point : JavaPluginHookCatalog.all()) {
            if (point.wired) wired++;
        }
        return UiLanguage.text(activity,
                total + " 个节点 · " + wired + " 个已接线",
                total + " points · " + wired + " wired");
    }

    /** Hook catalogue header note. */
    public static String hookNote(Activity activity) {
        if (activity == null) return "";
        return UiLanguage.text(activity,
                "目录只显示当前版本真正可订阅的稳定接口与本机实际成员事件。点按查看完整用法。",
                "Only stable, subscribable interfaces and real member events for this build are "
                        + "listed. Tap a point for full usage.");
    }

    /** Full detail text for a hook point. */
    public static String hookDetail(String id) {
        JavaPluginHookCatalog.Point point = JavaPluginHookCatalog.find(id);
        if (point == null) return "";
        StringBuilder text = new StringBuilder();
        text.append(point.title).append("\n\n");
        if (point.parameters != null && point.parameters.length() > 0) {
            text.append("参数\n").append(point.parameters).append("\n\n");
        }
        if (point.usage != null && point.usage.length() > 0) {
            text.append("用法\n").append(point.usage).append("\n\n");
        }
        if (point.canDo != null && point.canDo.length() > 0) {
            text.append("可以做什么\n").append(point.canDo).append("\n\n");
        }
        if (point.constraints != null && point.constraints.length() > 0) {
            text.append("约束\n").append(point.constraints).append("\n\n");
        }
        if (point.source != null && point.source.length() > 0) {
            text.append("来源\n").append(point.source);
        }
        return text.toString();
    }
}