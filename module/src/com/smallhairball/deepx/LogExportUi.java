package com.smallhairball.deepx;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Selective, redacted diagnostic ZIP export. */
final class LogExportUi {
    private static final int BRAND = 0xFF4D6BFE;
    private static final int MAX_FILE_BYTES = 8 * 1024 * 1024;
    private static final AtomicReference<boolean[]> PENDING = new AtomicReference<boolean[]>();
    private static final AtomicReference<String> PENDING_NAME = new AtomicReference<String>();

    private static final Source[] SOURCES = new Source[]{
            new Source("dsprobe", "模块 Hook 主日志", new String[]{
                    "/data/data/com.deepseek.chat/files/dsprobe.log",
                    "/storage/emulated/0/dsprobe_m.log"}),
            new Source("version", "模块、DeepSeek 与 Android 版本", new String[0]),
            new Source("server", "服务器返回与诊断开关状态", new String[]{
                    "/data/data/com.deepseek.chat/files/deekseep_srvlog"}),
            new Source("vision", "图片与视觉中继日志", new String[]{
                    "/data/data/com.deepseek.chat/files/deekseep_vision.log",
                    "/storage/emulated/0/deekseep_vision_m.log"}),
            new Source("crash", "崩溃记录", new String[]{
                    "/data/data/com.deepseek.chat/files/dsprobe_crash.log",
                    "/storage/emulated/0/dsprobe_crash.log"}),
            new Source("local-api", "本地 API 日志、状态与会话诊断", new String[]{
                    z13.LOG_FILE,
                    z13.LOG_FILE + ".1",
                    z13.STATUS_FILE,
                    z13.INFO_FILE,
                    "/data/data/com.deepseek.chat/files/dq0_key_state.log",
                    "/data/data/com.deepseek.chat/files/dq0_sessions.json"}),
            new Source("tunnels", "Cloudflare 与 Pinggy 日志", new String[]{
                    "/data/data/com.deepseek.chat/files/deekseep_cloudflared.log",
                    "/data/data/com.deepseek.chat/files/deekseep_pinggy.log"}),
            new Source("raw-hook", "高频原始 Hook 日志（无脱敏，可能泄露隐私）", new String[]{
                    RawHookTrace.LOG_PATH,
                    RawHookTrace.ROTATED_PATH}, true)
    };

    private LogExportUi() {}

    /** Metadata for one exportable log source, mirrored from [SOURCES]. */
    static final class SourceInfo {
        final String name;
        final String description;
        final boolean unredacted;

        SourceInfo(String name, String description, boolean unredacted) {
            this.name = name;
            this.description = description;
            this.unredacted = unredacted;
        }
    }

    /** The selectable log sources, keeping the order used by the legacy page. */
    static SourceInfo[] sourceInfos() {
        ArrayList<SourceInfo> out = new ArrayList<>();
        for (Source source : SOURCES) {
            if (!BuildInfo.LOCAL_API_INCLUDED
                    && ("local-api".equals(source.name) || "tunnels".equals(source.name))) {
                continue;
            }
            out.add(new SourceInfo(source.name, source.description, source.unredacted));
        }
        return out.toArray(new SourceInfo[0]);
    }

    /** Default selection state for the given sources (the first two are pre-checked). */
    static boolean[] defaultSelection() {
        boolean[] selected = new boolean[sourceInfos().length];
        for (int index = 0; index < selected.length && index < 2; index++) selected[index] = true;
        return selected;
    }

    /**
     * Records the selection and launches the SAF destination picker, exactly like the legacy
     * page's export button. The ZIP is produced later by {@link #handleResult}.
     */
    static void startExport(final Activity activity, boolean[] selected) {
        if (activity == null || selected == null) return;
        boolean any = false;
        for (boolean value : selected) any |= value;
        if (!any) {
            Toast.makeText(activity, t(activity,
                    "请至少选择一项日志", "Select at least one log source"),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        PENDING.set(selected.clone());
        String name = exportName();
        PENDING_NAME.set(name);
        Intent picker;
        if (HostCompat.isV241()) {
            picker = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/zip")
                    .putExtra(Intent.EXTRA_TITLE, name)
                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            picker = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            | Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        }
        activity.startActivityForResult(picker, Main.LOG_EXPORT_REQUEST);
    }

    /** The privacy note shown above the export button. */
    static String privacyNote() {
        return "普通日志会隐藏 API Key 和 Authorization；“高频原始 Hook 日志”按设计不脱敏，可能包含消息、账号及凭据。";
    }

    /** The export button label, matching the current host capabilities. */
    static String exportLabel() {
        return HostCompat.isV241() ? "选择文件并导出" : "选择文件夹并导出";
    }

    static void show(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        final boolean dark = isDark(activity);
        final int background = dark ? 0xFF1B1B1D : 0xFFF5F6F8;
        final int barColor = dark ? 0xFF232326 : 0xFFFFFFFF;
        final int cardColor = dark ? 0xFF2A2A2D : 0xFFFFFFFF;
        final int textColor = dark ? 0xFFECECEC : 0xFF1A1A1A;
        final int subColor = dark ? 0xFFAAAAAF : 0xFF70757D;

        final Dialog dialog = new Dialog(
                activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(background);

        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(activity, 8), statusBarHeight(activity), dp(activity, 16), 0);
        top.setBackgroundColor(barColor);
        root.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(activity, 56) + statusBarHeight(activity)));
        TextView back = text(activity, "‹", 28, textColor, false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { dialog.dismiss(); }
        });
        top.addView(back, new LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 44)));
        TextView title = text(activity, t(activity, "导出日志", "Export logs"),
                18, textColor, true);
        top.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ScrollView scroll = new ScrollView(activity);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 16), dp(activity, 16),
                dp(activity, 16), dp(activity, 20));
        scroll.addView(content);

        final boolean[] selected = new boolean[SOURCES.length];
        for (int index = 0; index < SOURCES.length; index++) {
            final int item = index;
            Source source = SOURCES[index];
            if (!BuildInfo.LOCAL_API_INCLUDED
                    && ("local-api".equals(source.name) || "tunnels".equals(source.name))) {
                continue;
            }
            LinearLayout card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(activity, 16), dp(activity, 14),
                    dp(activity, 12), dp(activity, 14));
            card.setBackground(rounded(cardColor, dp(activity, 10)));
            card.setClickable(true);
            LinearLayout copy = new LinearLayout(activity);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.addView(text(activity, source.name, 16, textColor, true));
            copy.addView(text(activity, source.description, 12, subColor, false));
            card.addView(copy, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            final CheckBox check = new CheckBox(activity);
            check.setButtonTintList(new android.content.res.ColorStateList(
                    new int[][]{{android.R.attr.state_checked}, new int[]{}},
                    new int[]{BRAND, subColor}));
            check.setChecked(index < 2);
            selected[index] = index < 2;
            check.setOnCheckedChangeListener((button, checked) -> selected[item] = checked);
            card.addView(check, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            card.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { check.setChecked(!check.isChecked()); }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (index > 0) params.topMargin = dp(activity, 10);
            content.addView(card, params);
        }

        TextView privacy = text(activity, t(activity,
                "普通日志会隐藏 API Key 和 Authorization；“高频原始 Hook 日志”按设计不脱敏，可能包含消息、账号及凭据。",
                "Normal logs redact API keys and Authorization. Raw Hook traces are intentionally unredacted and may contain messages, account data, and credentials."),
                12, subColor, false);
        privacy.setPadding(dp(activity, 4), dp(activity, 14), dp(activity, 4), dp(activity, 10));
        content.addView(privacy);
        TextView export = text(activity, t(activity,
                HostCompat.isV241() ? "选择文件并导出" : "选择文件夹并导出",
                HostCompat.isV241() ? "Choose file and export" : "Choose folder and export"),
                14, 0xFFFFFFFF, true);
        export.setGravity(Gravity.CENTER);
        export.setPadding(dp(activity, 12), dp(activity, 12),
                dp(activity, 12), dp(activity, 12));
        export.setBackground(rounded(BRAND, dp(activity, 8)));
        content.addView(export);
        export.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                boolean any = false;
                for (boolean value : selected) any |= value;
                if (!any) {
                    Toast.makeText(activity, t(activity,
                            "请至少选择一项日志", "Select at least one log source"),
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                PENDING.set(selected.clone());
                String name = exportName();
                PENDING_NAME.set(name);
                Intent picker;
                if (HostCompat.isV241()) {
                    // Some Android 16 document providers reject createDocument() on a tree URI.
                    // Let the provider create the exact ZIP target itself on code257.
                    picker = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("application/zip")
                            .putExtra(Intent.EXTRA_TITLE, name)
                            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                    | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } else {
                    picker = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                    | Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                }
                activity.startActivityForResult(picker, Main.LOG_EXPORT_REQUEST);
            }
        });

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            window.setStatusBarColor(barColor);
            window.setNavigationBarColor(background);
        }
        dialog.show();
    }

    static void handleResult(final Activity activity, int resultCode, Intent data) {
        final boolean[] selected = PENDING.getAndSet(null);
        final String pendingName = PENDING_NAME.getAndSet(null);
        if (activity == null || resultCode != Activity.RESULT_OK || data == null
                || data.getData() == null || selected == null) return;
        final Uri selection = data.getData();
        try {
            activity.getContentResolver().takePersistableUriPermission(selection,
                    data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
        } catch (Throwable ignored) {}
        new Thread(new Runnable() {
            @Override public void run() {
                String message;
                try {
                    message = export(activity, selection, selected, pendingName);
                } catch (Throwable error) {
                    Main.log("log export failed: " + safe(error));
                    message = t(activity, "导出失败：", "Export failed: ") + safe(error);
                }
                final String result = message;
                activity.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        Toast.makeText(activity, result, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }, "DeePX-Log-Export").start();
    }

    private static String export(Activity activity, Uri selection, boolean[] selected,
                                 String requestedName) throws Exception {
        String name = requestedName == null || requestedName.length() == 0
                ? exportName() : requestedName;
        Uri target;
        if (HostCompat.isV241()) {
            target = selection;
        } else {
            Uri parent = DocumentsContract.buildDocumentUriUsingTree(
                    selection, DocumentsContract.getTreeDocumentId(selection));
            target = DocumentsContract.createDocument(activity.getContentResolver(), parent,
                    "application/zip", name);
        }
        if (target == null) throw new java.io.IOException("document provider rejected ZIP");
        OutputStream raw = activity.getContentResolver().openOutputStream(target, "w");
        if (raw == null) throw new java.io.IOException("could not open output ZIP");
        ZipOutputStream zip = new ZipOutputStream(raw);
        try {
            for (int index = 0; index < SOURCES.length && index < selected.length; index++) {
                if (!selected[index]) continue;
                Source source = SOURCES[index];
                if ("version".equals(source.name)) {
                    add(zip, "version.txt", version(activity).getBytes(StandardCharsets.UTF_8));
                }
                for (String path : source.paths) {
                    addFile(zip, source.name, new File(path), source.unredacted);
                }
            }
            zip.finish();
        } finally {
            zip.close();
        }
        return t(activity, "日志已导出：", "Logs exported: ") + name;
    }

    private static String exportName() {
        return "DeePX-logs-" + new SimpleDateFormat(
                "yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".zip";
    }

    private static void addFile(ZipOutputStream zip, String group, File file,
                                boolean unredacted) throws Exception {
        if (!file.isFile() || file.length() <= 0L) return;
        int size = (int) Math.min(file.length(), MAX_FILE_BYTES);
        byte[] bytes = new byte[size];
        FileInputStream input;
        try {
            input = new FileInputStream(file);
        } catch (Throwable ignored) {
            return; // not readable (permission denied, etc.) — skip silently
        }
        int offset = 0;
        try {
            while (offset < bytes.length) {
                int count = input.read(bytes, offset, bytes.length - offset);
                if (count < 0) break;
                offset += count;
            }
        } finally { input.close(); }
        String text = new String(bytes, 0, offset, StandardCharsets.UTF_8);
        if (!unredacted) text = redact(text);
        add(zip, group + "/" + file.getName(), text.getBytes(StandardCharsets.UTF_8));
    }

    private static String redact(String value) {
        String safe = value == null ? "" : value;
        String key = z13.apiKey();
        if (key != null && key.length() >= 8) safe = safe.replace(key, "[REDACTED_API_KEY]");
        safe = safe.replaceAll("(?i)(Authorization\\s*[:=]\\s*Bearer\\s+)[^\\s\\\"']+",
                "$1[REDACTED]");
        safe = safe.replaceAll("(?i)(api_key\\s*=\\s*)[^\\r\\n]+",
                "$1[REDACTED]");
        return safe;
    }

    private static String version(Activity activity) {
        String host = "unknown";
        try {
            PackageInfo info = activity.getPackageManager().getPackageInfo(
                    "com.deepseek.chat", 0);
            long code = Build.VERSION.SDK_INT >= 28
                    ? info.getLongVersionCode() : info.versionCode;
            host = info.versionName + " (" + code + ")";
        } catch (Throwable ignored) {}
        return "module=" + BuildInfo.MODULE_VERSION + "\n"
                + "module_api=" + BuildInfo.API_VERSION + "\n"
                + "protected_build=" + BuildInfo.PROTECTED_BUILD + "\n"
                + "deepseek=" + host + "\n"
                + "android=" + Build.VERSION.RELEASE + "\n"
                + "sdk=" + Build.VERSION.SDK_INT + "\n"
                + "device=" + Build.MANUFACTURER + " " + Build.MODEL + "\n";
    }

    private static void add(ZipOutputStream zip, String name, byte[] bytes) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private static TextView text(Activity activity, String value, float size,
                                 int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private static GradientDrawable rounded(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static int statusBarHeight(Activity activity) {
        int id = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? activity.getResources().getDimensionPixelSize(id) : 0;
    }

    private static boolean isDark(Activity activity) {
        return (activity.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private static String t(Activity activity, String zh, String en) {
        return UiLanguage.text(activity, zh, en);
    }

    private static String safe(Throwable error) {
        String message = error == null ? "" : error.getMessage();
        return error == null ? "unknown" : error.getClass().getSimpleName()
                + (message == null || message.length() == 0 ? "" : ": " + message);
    }

    private static final class Source {
        final String name;
        final String description;
        final String[] paths;
        final boolean unredacted;

        Source(String name, String description, String[] paths) {
            this(name, description, paths, false);
        }

        Source(String name, String description, String[] paths, boolean unredacted) {
            this.name = name;
            this.description = description;
            this.paths = paths;
            this.unredacted = unredacted;
        }
    }
}