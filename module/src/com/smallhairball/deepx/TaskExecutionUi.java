package com.smallhairball.deepx;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.atomic.AtomicBoolean;

/** Full-screen, auto-scrolling execution console for one-shot module jobs. */
final class TaskExecutionUi {
    interface Logger { void line(String value); }
    interface Task { String run(Logger logger) throws Throwable; }

    private TaskExecutionUi() {}

    /**
     * Renders the console with the Compose/Miuix layer when it is available, filling the Dialog the
     * caller already built and streaming the same lines through {@link TaskConsoleBridge}. Returns
     * {@code false} when Compose could not be attached, in which case the caller keeps the legacy
     * View console (and the untouched Dialog) below.
     *
     * <p>The window treatment mirrors the Miuix home page: edge-to-edge system bars so the clock and
     * battery stay visible over the page background, plus the traditional Activity push/pop window
     * animation for opening and closing.
     */
    private static boolean attachComposeConsole(final Activity activity, final Dialog dialog,
            final boolean dark, final int canvas, String rawTitle, String rawHint,
            final Task task) {
        final String title = rawTitle == null ? "" : rawTitle;
        final String hint = rawHint == null ? "" : rawHint;
        if (!DeePXComposeBridge.attachTaskConsole(activity, dialog, dark, title, hint)) {
            return false;
        }
        Window composeWindow = dialog.getWindow();
        if (composeWindow != null) {
            composeWindow.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            composeWindow.setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(canvas));
            DeePXUi.applyEdgeToEdge(composeWindow, dark);
            composeWindow.setWindowAnimations(android.R.style.Animation_Activity);
        }
        // The legacy console ignored back until the job had finished; keep that gate.
        dialog.setOnKeyListener(new Dialog.OnKeyListener() {
            @Override public boolean onKey(android.content.DialogInterface ignored,
                                           int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK
                        && event.getAction() == KeyEvent.ACTION_UP) {
                    if (TaskConsoleBridge.snapshot().complete) {
                        try { dialog.dismiss(); } catch (Throwable ignored2) {}
                    }
                    return true;
                }
                return false;
            }
        });
        dialog.show();
        TaskConsoleBridge.start(title, hint, new TaskConsoleBridge.Runner() {
            @Override public String run(TaskConsoleBridge.Sink sink) throws Throwable {
                return task.run(new Logger() {
                    @Override public void line(String value) {
                        sink.line(value);
                    }
                });
            }
        });
        return true;
    }

    static void show(final Activity activity, String rawTitle, String rawHint,
                     final Task task) {
        if (activity == null || activity.isFinishing() || task == null) return;
        final boolean dark = DeePXUi.isDark(activity);
        final int canvas = dark ? 0xFF1B1B1D : 0xFFF1F2F4;
        // One full-screen Dialog is created up front and shared by both paths: the Compose console
        // takes over its content view, the legacy View console keeps drawing into it. This mirrors
        // how the Miuix home page reuses the Dialog the View code already built.
        final Dialog dialog = new Dialog(
                activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setCanceledOnTouchOutside(false);
        if (attachComposeConsole(activity, dialog, dark, canvas, rawTitle, rawHint, task)) return;

        final int bar = dark ? 0xFF202023 : 0xFFF7F7F8;
        final int text = dark ? 0xFFECECEE : 0xFF202024;
        final String englishTitle = UiLanguageCatalog.toEnglish(rawTitle == null ? "" : rawTitle);
        final String englishHint = UiLanguageCatalog.toEnglish(rawHint == null ? "" : rawHint);
        final AtomicBoolean complete = new AtomicBoolean(false);

        final FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(canvas);
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        root.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(bar);
        int statusTop = statusBarHeight(activity);
        top.setPadding(DeePXUi.dp(activity, 8), statusTop,
                DeePXUi.dp(activity, 8), 0);
        page.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                DeePXUi.dp(activity, 64) + statusTop));

        FrameLayout back = new FrameLayout(activity);
        back.setClickable(true);
        back.setFocusable(true);
        View backGlyph = new HubMaterialGlyphView(activity, "ds_action_back", text);
        back.addView(backGlyph, new FrameLayout.LayoutParams(
                DeePXUi.dp(activity, 24), DeePXUi.dp(activity, 24), Gravity.CENTER));
        top.addView(back, new LinearLayout.LayoutParams(
                DeePXUi.dp(activity, 48), DeePXUi.dp(activity, 48)));

        TextView heading = new TextView(activity);
        heading.setText(UiLanguage.text(activity, "执行", "Action"));
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        heading.setTypeface(Typeface.DEFAULT);
        heading.setTextColor(text);
        LinearLayout.LayoutParams headingLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        headingLp.leftMargin = DeePXUi.dp(activity, 4);
        top.addView(heading, headingLp);

        FrameLayout save = new FrameLayout(activity);
        save.setClickable(true);
        save.setFocusable(true);
        save.setContentDescription(UiLanguage.text(activity, "保存日志", "Save log"));
        View saveGlyph = new HubMaterialGlyphView(activity, "ds_action_save", text);
        save.addView(saveGlyph, new FrameLayout.LayoutParams(
                DeePXUi.dp(activity, 24), DeePXUi.dp(activity, 24), Gravity.CENTER));
        top.addView(save, new LinearLayout.LayoutParams(
                DeePXUi.dp(activity, 48), DeePXUi.dp(activity, 48)));

        final ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        final TextView logView = new TextView(activity);
        logView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        logView.setTextColor(text);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        logView.setGravity(Gravity.START);
        logView.setLineSpacing(DeePXUi.dp(activity, 1), 1.05f);
        logView.setPadding(DeePXUi.dp(activity, 8), DeePXUi.dp(activity, 8),
                DeePXUi.dp(activity, 8),
                DeePXUi.dp(activity, 64) + navigationBarHeight(activity));
        scroll.addView(logView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final FrameLayout close = new FrameLayout(activity);
        close.setClickable(true);
        close.setFocusable(true);
        close.setContentDescription(UiLanguage.text(activity, "关闭", "Close"));
        close.setBackground(roundRect(DeePXUi.BRAND, DeePXUi.dp(activity, 28)));
        View closeGlyph = new HubMaterialGlyphView(activity, "ds_action_close", 0xFFFFFFFF);
        close.addView(closeGlyph, new FrameLayout.LayoutParams(
                DeePXUi.dp(activity, 40), DeePXUi.dp(activity, 40), Gravity.CENTER));
        close.setVisibility(View.GONE);
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(
                DeePXUi.dp(activity, 56), DeePXUi.dp(activity, 56),
                Gravity.END | Gravity.BOTTOM);
        closeLp.setMargins(DeePXUi.dp(activity, 20), 0,
                DeePXUi.dp(activity, 20),
                DeePXUi.dp(activity, 20) + navigationBarHeight(activity));
        root.addView(close, closeLp);

        final StringBuilder logText = new StringBuilder();
        final Logger logger = new Logger() {
            @Override public void line(final String value) {
                final String safe = UiLanguageCatalog.toEnglish(value == null ? "" : value);
                synchronized (logText) {
                    if (safe.startsWith("\u001B[H\u001B[J")) {
                        logText.setLength(0);
                        logText.append(safe.substring(6));
                    } else {
                        logText.append(safe).append('\n');
                    }
                }
                activity.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        synchronized (logText) { logView.setText(logText.toString()); }
                        scroll.post(new Runnable() {
                            @Override public void run() {
                                scroll.smoothScrollTo(0, Math.max(0, logView.getHeight()));
                            }
                        });
                    }
                });
            }
        };

        View.OnClickListener dismissWhenComplete = new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (complete.get()) DeePXUi.slideOutAndDismiss(dialog, root);
            }
        };
        back.setOnClickListener(dismissWhenComplete);
        close.setOnClickListener(dismissWhenComplete);
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                saveLog(activity, logText);
            }
        });

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(canvas));
        }
        DeePXUi.openWithSlide(dialog, root);
        dialog.setOnKeyListener(new Dialog.OnKeyListener() {
            @Override public boolean onKey(android.content.DialogInterface ignored,
                                           int keyCode, android.view.KeyEvent event) {
                if (keyCode == android.view.KeyEvent.KEYCODE_BACK
                        && event.getAction() == android.view.KeyEvent.ACTION_UP) {
                    if (complete.get()) DeePXUi.slideOutAndDismiss(dialog, root);
                    return true;
                }
                return false;
            }
        });

        new Thread(new Runnable() {
            @Override public void run() {
                if (englishTitle.length() > 0) logger.line("Task: " + englishTitle);
                if (englishHint.length() > 0) logger.line("Note: " + englishHint);
                logger.line("Execution started.");
                try {
                    final String result = task.run(logger);
                    logger.line("Execution completed successfully.");
                    if (result != null && result.trim().length() > 0) {
                        activity.runOnUiThread(new Runnable() {
                            @Override public void run() {
                                showResultDialog(activity, rawTitle, result, true);
                            }
                        });
                    }
                } catch (Throwable error) {
                    String message = error.getMessage();
                    if (message == null || message.trim().length() == 0) {
                        message = error.getClass().getSimpleName();
                    }
                    final String failure = message;
                    logger.line("Execution failed: " + failure);
                    activity.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            showResultDialog(activity, rawTitle, failure, false);
                        }
                    });
                } finally {
                    complete.set(true);
                    activity.runOnUiThread(new Runnable() {
                        @Override public void run() { close.setVisibility(View.VISIBLE); }
                    });
                }
            }
        }, "DeePX-task-console").start();
    }

    private static void saveLog(Activity activity, StringBuilder source) {
        String snapshot;
        synchronized (source) { snapshot = source.toString(); }
        String path = TaskConsoleBridge.writeLogFile(activity, snapshot);
        if (path != null) {
            Toast.makeText(activity, "Log saved: " + path, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(activity, "Failed to save log", Toast.LENGTH_LONG).show();
        }
    }

    private static void showResultDialog(Activity activity, String rawTitle,
                                         String result, boolean success) {
        if (activity == null || activity.isFinishing()) return;
        TextView value = new TextView(activity);
        value.setText(result);
        value.setTextIsSelectable(true);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        value.setPadding(DeePXUi.dp(activity, 20), DeePXUi.dp(activity, 8),
                DeePXUi.dp(activity, 20), DeePXUi.dp(activity, 8));
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(value, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        String title = success
                ? UiLanguage.dynamic(activity, rawTitle)
                : UiLanguage.text(activity, "执行失败", "Execution failed");
        new android.app.AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton(UiLanguage.text(activity, "确定", "OK"), null)
                .show();
    }

    private static GradientDrawable roundRect(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private static int statusBarHeight(Activity activity) {
        return systemBarHeight(activity, "status_bar_height");
    }

    private static int navigationBarHeight(Activity activity) {
        return systemBarHeight(activity, "navigation_bar_height");
    }

    private static int systemBarHeight(Activity activity, String name) {
        try {
            int id = activity.getResources().getIdentifier(name, "dimen", "android");
            return id == 0 ? 0 : activity.getResources().getDimensionPixelSize(id);
        } catch (Throwable ignored) {
            return 0;
        }
    }
}