package com.smallhairball.deepx;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Data/action bridge for the "聊天" (Chat) category page rendered by the Compose/Miuix layer.
 *
 * <p>The Compose side only draws Miuix widgets; every piece of state and every action still lives
 * here, so the View logic stays in one place and the UI can be re-skinned freely.
 */
public final class ChatPageBridge {

    /**
     * Row kinds understood by the Compose renderer.
     */
    public static final int TYPE_LINK = 0;
    public static final int TYPE_SWITCH = 1;
    public static final int TYPE_TASK = 2;

    /** One row on the chat page. */
    public static final class Row {
        public final String id;
        public final String title;
        public final String subtitle;
        public final int type;
        public final boolean checked;

        Row(String id, String title, String subtitle, int type, boolean checked) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.type = type;
            this.checked = checked;
        }
    }

    private ChatPageBridge() {}

    /** Builds the current chat page rows, matching the module app's own chat page. */
    public static List<Row> rows() {
        List<Row> out = new ArrayList<>();
        out.add(sw("prompt_injection", "系统提示词注入", "",
                Main.isEnabled()));
        out.add(sw("chat_multiselect", "聊天记录多选", "长按会话进入多选",
                Main.isChatMultiSelect()));
        out.add(link("edit_chat", "编辑聊天记录", ""));
        out.add(sw("message_details", "消息时间与详情",
                "在聊天编辑器中显示每条本地消息的时间，点击时间可查看消息 ID 与详情。",
                Main.isMessageDetailsEnabled()));
        out.add(sw("auto_continue", "自动继续生成",
                "长思考被服务器暂停时自动继续，切到后台也会生效。",
                Main.isAutoContinueEnabled()));
        out.add(link("export_md", "导出会话为 Markdown",
                "导出 Markdown，并在下载目录生成可恢复的聊天备份包。"));
        out.add(link("import_chat", "导入聊天记录",
                "选择导出的聊天备份包，只覆盖服务器已重新下发的同 ID 会话。"));
        out.add(link("global_search", "全局搜索聊天记录", ""));
        out.add(task("stats", "会话数据统计",
                "统计本地会话数、消息数、总字数，并按账号分组。"));
        out.add(sw("expert_unlock", "解锁专家模式与图片上传 · 实验性",
                "启用专家模型能力，并通过视觉描述中继图片。",
                Main.isExpertUnlock()));
        out.add(sw("heartbeat", "AI 心跳 · 实验性",
                heartbeatSubtitle(), Main.isProactiveHeartbeatEnabled()));
        return out;
    }

    private static String heartbeatSubtitle() {
        try {
            int minutes = Main.proactiveHeartbeatIntervalMinutes();
            boolean bound = Main.proactiveHeartbeatBoundToCurrentConversation();
            return "每 " + minutes + " 分钟 · " + (bound ? "已绑定" : "未绑定");
        } catch (Throwable error) {
            return "按间隔发送主动消息";
        }
    }

    /**
     * Toggles a switch row.
     *
     * @return the resulting state, so the Compose switch can reflect the real value (some toggles
     *         refuse to turn on and stay off).
     */
    public static boolean toggle(String id) {
        switch (id == null ? "" : id) {
            case "prompt_injection":
                Main.setEnabled(!Main.isEnabled());
                return Main.isEnabled();
            case "chat_multiselect": {
                boolean next = !Main.isChatMultiSelect();
                Main.setChatMultiSelect(next);
                return Main.isChatMultiSelect();
            }
            case "message_details":
                Main.setMessageDetailsEnabled(!Main.isMessageDetailsEnabled());
                return Main.isMessageDetailsEnabled();
            case "auto_continue":
                Main.setAutoContinueEnabled(!Main.isAutoContinueEnabled());
                return Main.isAutoContinueEnabled();
            case "expert_unlock":
                Main.setExpertUnlock(!Main.isExpertUnlock());
                return Main.isExpertUnlock();
            case "heartbeat":
                Main.setProactiveHeartbeatEnabled(appContext, !Main.isProactiveHeartbeatEnabled());
                return Main.isProactiveHeartbeatEnabled();
            default:
                return false;
        }
    }

    /** Context for switches that need one; set by the bridge before the page is shown. */
    private static volatile android.content.Context appContext;

    /** Remembers the host context so context-requiring toggles can work. */
    public static void setContext(android.content.Context context) {
        appContext = context == null ? null : context.getApplicationContext();
    }

    /** Runs a link row's action. */
    public static void action(Activity act, String id) {
        if (act == null || id == null) return;
        switch (id) {
            case "import_prompt":
                importPrompt(act);
                break;
            case "reset_prompt":
                resetPrompt(act);
                break;
            case "edit_chat":
                ChatEditorUi.show(act);
                break;
            case "export_md":
                showTask(act, "导出会话为 Markdown",
                        "导出期间请保持 DeepSeek 运行；备份包卸载后仍保留在 Download/DeePX。",
                        new TaskExecutionUi.Task() {
                            @Override public String run(TaskExecutionUi.Logger logger) throws Throwable {
                                return DeePXTools.exportAllForConsole(act, logger);
                            }
                        });
                break;
            case "import_chat": {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/zip");
                try {
                    act.startActivityForResult(intent, Main.CHAT_IMPORT_REQUEST);
                } catch (Throwable unavailable) {
                    intent.setType("*/*");
                    act.startActivityForResult(intent, Main.CHAT_IMPORT_REQUEST);
                }
                break;
            }
            case "global_search":
                ChatSearchUi.show(act);
                break;
            case "stats":
                showTask(act, "会话数据统计",
                        "仅统计设备上的聊天数据库，不会上传聊天内容。",
                        new TaskExecutionUi.Task() {
                            @Override public String run(TaskExecutionUi.Logger logger) {
                                return DeePXTools.statisticsForConsole(act, logger);
                            }
                        });
                break;
            case "backup_db":
                showTask(act, "立即备份聊天数据库",
                        "备份不会修改原数据库；完成后日志会显示保存目录。",
                        new TaskExecutionUi.Task() {
                            @Override public String run(TaskExecutionUi.Logger logger) throws Throwable {
                                return DeePXTools.backupForConsole(act, logger);
                            }
                        });
                break;
            default:
                break;
        }
    }

    private static void showTask(Activity act, String title, String hint,
            TaskExecutionUi.Task task) {
        try {
            TaskExecutionUi.show(act, title, hint, task);
        } catch (Throwable error) {
            Toast.makeText(act, "执行失败：" + error.getClass().getSimpleName(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /** Current display path of the imported system prompt, for the header row. */
    public static String promptPath() {
        try {
            return Main.getPromptDisplayPath();
        } catch (Throwable error) {
            return "";
        }
    }

    private static void importPrompt(Activity act) {
        if (Main.isEmbeddedPromptEnabled()) {
            Toast.makeText(act, "已启用内置提示词", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Main.onPickComplete = new Runnable() {
                @Override public void run() {
                    // The Compose header re-reads promptPath() on recomposition; nothing else needed.
                }
            };
            Intent intent = new Intent();
            String componentPackage = RuntimeEmbeddingMode.componentPackage(Main.SELF);
            intent.setClassName(componentPackage, componentPackage + ".PromptPickerActivity");
            try {
                act.startActivityForResult(intent, Main.PICK_REQUEST);
            } catch (Throwable notFound) {
                Intent fallback = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                fallback.addCategory(Intent.CATEGORY_OPENABLE);
                fallback.setType("text/*");
                fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                fallback.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                act.startActivityForResult(fallback, Main.PICK_REQUEST);
            }
        } catch (Throwable error) {
            Toast.makeText(act, "导入失败：" + error.getClass().getSimpleName(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private static void resetPrompt(Activity act) {
        if (Main.isEmbeddedPromptEnabled()) {
            Toast.makeText(act, "已启用内置提示词，无法还原", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Main.clearPromptFiles();
            Toast.makeText(act, "已还原设置", Toast.LENGTH_SHORT).show();
        } catch (Throwable error) {
            Toast.makeText(act, "还原失败：" + error.getClass().getSimpleName(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private static Row sw(String id, String title, String subtitle, boolean checked) {
        return new Row(id, title, subtitle, TYPE_SWITCH, checked);
    }

    private static Row link(String id, String title, String subtitle) {
        return new Row(id, title, subtitle, TYPE_LINK, false);
    }

    private static Row task(String id, String title, String subtitle) {
        return new Row(id, title, subtitle, TYPE_TASK, false);
    }
}