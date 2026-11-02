package com.smallhairball.deepx;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.concurrent.atomic.AtomicBoolean;

/** HTTPS trust deployment page shared by rooted and rootless Local API users. */
final class z7 {
    private static final int BRAND = 0xFF4D6BFE;

    private z7() {}

    static void show(final Activity activity, final boolean requestEnable,
                     final Runnable onSettingChanged) {
        if (activity == null || activity.isFinishing()) return;
        final boolean dark = isDark(activity);
        final int background = dark ? 0xFF1B1B1D : 0xFFF5F6F8;
        final int barColor = dark ? 0xFF232326 : 0xFFFFFFFF;
        final int cardColor = dark ? 0xFF2A2A2D : 0xFFFFFFFF;
        final int textColor = dark ? 0xFFECECEC : 0xFF1A1A1A;
        final int subColor = dark ? 0xFFAAAAAF : 0xFF70757D;

        final Dialog dialog = new Dialog(
                activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        final LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(background);

        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(barColor);
        int statusTop = statusBarHeight(activity);
        top.setPadding(dp(activity, 8), statusTop, dp(activity, 16), 0);
        root.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 56) + statusTop));

        TextView back = text(activity, "‹", "‹", 28, textColor, false);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(activity, 8), 0, dp(activity, 8), 0);
        top.addView(back, new LinearLayout.LayoutParams(
                dp(activity, 44), dp(activity, 44)));
        TextView title = text(activity, "HTTPS 与 CA 证书",
                "HTTPS and CA certificate", 18, textColor, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(activity, 8);
        top.addView(title, titleParams);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        final LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 16), dp(activity, 16),
                dp(activity, 16), dp(activity, 28));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final TextView status = info(activity,
                requestEnable
                        ? t(activity, "正在生成本机独立证书…",
                                "Generating a device-specific certificate…")
                        : Main.localApiHttpsStatus(activity),
                textColor, dark);
        content.addView(status, matchWrap());

        LinearLayout tabs = new LinearLayout(activity);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tabsParams = matchWrap();
        tabsParams.topMargin = dp(activity, 14);
        content.addView(tabs, tabsParams);
        final TextView rootTab = tab(activity, "Root 用户", "Root", dark);
        final TextView rootlessTab = tab(activity, "免 Root 用户", "Rootless", dark);
        tabs.addView(rootTab, weighted());
        LinearLayout.LayoutParams rootlessParams = weighted();
        rootlessParams.leftMargin = dp(activity, 8);
        tabs.addView(rootlessTab, rootlessParams);

        final LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 16), dp(activity, 14),
                dp(activity, 16), dp(activity, 16));
        panel.setBackground(rounded(cardColor, dp(activity, 8)));
        LinearLayout.LayoutParams panelParams = matchWrap();
        panelParams.topMargin = dp(activity, 10);
        content.addView(panel, panelParams);

        final TextView feedback = text(activity, "", "", 12, BRAND, false);
        feedback.setGravity(Gravity.CENTER);
        feedback.setPadding(dp(activity, 12), dp(activity, 12),
                dp(activity, 12), 0);
        content.addView(feedback, matchWrap());

        final TextView verify = action(activity,
                "校验 HTTPS", "Verify HTTPS", dark);
        LinearLayout.LayoutParams verifyParams = matchWrap();
        verifyParams.topMargin = dp(activity, 10);
        content.addView(verify, verifyParams);

        final AtomicBoolean busy = new AtomicBoolean(requestEnable);
        final boolean[] rootMode = {true};
        final Runnable[] render = new Runnable[1];
        render[0] = new Runnable() {
            @Override public void run() {
                styleTab(rootTab, rootMode[0], dark);
                styleTab(rootlessTab, !rootMode[0], dark);
                panel.removeAllViews();
                if (rootMode[0]) {
                    panel.addView(text(activity,
                            "Root 证书模块", "Root certificate module",
                            16, textColor, true));
                    TextView explanation = text(activity,
                            "导出兼容 Magisk、Magisk Alpha、KernelSU、SukiSU Ultra 和 APatch "
                                    + "模块格式的 ZIP。刷入时会输出仓库链接并识别当前 Root 环境。"
                                    + "模块只包含公开 CA 证书，不包含服务器或 CA 私钥。",
                            "Export a module ZIP compatible with Magisk, Magisk Alpha, "
                                    + "KernelSU, SukiSU Ultra and APatch. Installation prints "
                                    + "the repository and detected root environment. The module "
                                    + "contains only the public CA certificate—never a private key.",
                            12, subColor, false);
                    explanation.setLineSpacing(0f, 1.18f);
                    LinearLayout.LayoutParams explanationParams = matchWrap();
                    explanationParams.topMargin = dp(activity, 8);
                    panel.addView(explanation, explanationParams);
                    final TextView installSystem = action(activity,
                            "Root 一键安装为系统证书", "Install as system CA with Root", dark);
                    LinearLayout.LayoutParams installParams = matchWrap();
                    installParams.topMargin = dp(activity, 14);
                    panel.addView(installSystem, installParams);
                    installSystem.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View view) {
                            z7.run(activity, busy, feedback, status,
                                    new Work() {
                                        @Override public String execute() throws Exception {
                                            return z5.installRootSystemCertificate(activity);
                                        }
                                    }, null);
                        }
                    });
                    final TextView export = action(activity,
                            "导出 Root 模块", "Export root module", dark);
                    LinearLayout.LayoutParams actionParams = matchWrap();
                    actionParams.topMargin = dp(activity, 10);
                    panel.addView(export, actionParams);
                    export.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View view) {
                            z7.run(activity, busy, feedback, status,
                                    new Work() {
                                        @Override public String execute() throws Exception {
                                            return t(activity, "已保存到下载目录：",
                                                    "Saved to Downloads: ")
                                                    + z5.exportRootModule(activity);
                                        }
                                    }, null);
                        }
                    });
                } else {
                    panel.addView(text(activity,
                            "系统证书安装", "System certificate installation",
                            16, textColor, true));
                    TextView explanation = text(activity,
                            "“安装到本机”会打开 Android 的系统证书入口。电脑或其他局域网客户端"
                                    + "仍需导入导出的 CA 文件，才能正常校验手机提供的 HTTPS。"
                                    + "部分应用默认不信任用户 CA，这是 Android 的应用安全策略。",
                            "Install on this device opens Android's certificate installer. "
                                    + "A computer or another LAN client must still import the "
                                    + "exported CA file to verify HTTPS served by the phone. Some "
                                    + "apps intentionally do not trust user CAs.",
                            12, subColor, false);
                    explanation.setLineSpacing(0f, 1.18f);
                    LinearLayout.LayoutParams explanationParams = matchWrap();
                    explanationParams.topMargin = dp(activity, 8);
                    panel.addView(explanation, explanationParams);
                    LinearLayout actions = new LinearLayout(activity);
                    actions.setOrientation(LinearLayout.HORIZONTAL);
                    LinearLayout.LayoutParams actionsParams = matchWrap();
                    actionsParams.topMargin = dp(activity, 14);
                    panel.addView(actions, actionsParams);
                    final TextView install = action(activity,
                            "安装到本机", "Install here", dark);
                    actions.addView(install, weighted());
                    final TextView export = action(activity,
                            "导出 CA", "Export CA", dark);
                    LinearLayout.LayoutParams exportParams = weighted();
                    exportParams.leftMargin = dp(activity, 8);
                    actions.addView(export, exportParams);
                    install.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View view) {
                            if (!busy.compareAndSet(false, true)) return;
                            try {
                                z5.openUserCertificateInstaller(activity);
                                feedback.setText(t(activity,
                                        "已打开系统证书安装入口",
                                        "Opened the system certificate installer"));
                            } catch (Throwable error) {
                                feedback.setText(t(activity,
                                        "无法打开证书安装入口：",
                                        "Could not open certificate installer: ")
                                        + safe(error));
                            } finally {
                                busy.set(false);
                            }
                        }
                    });
                    export.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View view) {
                            z7.run(activity, busy, feedback, status,
                                    new Work() {
                                        @Override public String execute() throws Exception {
                                            return t(activity, "已保存到下载目录：",
                                                    "Saved to Downloads: ")
                                                    + z5.exportCaCertificate(activity);
                                        }
                                    }, null);
                        }
                    });
                }
            }
        };
        rootTab.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                rootMode[0] = true;
                render[0].run();
            }
        });
        rootlessTab.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                rootMode[0] = false;
                render[0].run();
            }
        });
        render[0].run();

        verify.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                z7.run(activity, busy, feedback, status, new Work() {
                    @Override public String execute() {
                        return Main.validateLocalApiHttps(activity);
                    }
                }, onSettingChanged);
            }
        });

        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { dialog.dismiss(); }
        });
        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            window.setStatusBarColor(barColor);
            window.setNavigationBarColor(background);
        }
        dialog.setOnKeyListener(new Dialog.OnKeyListener() {
            @Override public boolean onKey(android.content.DialogInterface ignored,
                                           int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK
                        && event.getAction() == KeyEvent.ACTION_UP) {
                    dialog.dismiss();
                    return true;
                }
                return false;
            }
        });
        dialog.show();

        if (requestEnable) {
            run(activity, busy, feedback, status, new Work() {
                @Override public String execute() {
                    return Main.setLocalApiHttpsEnabled(activity, true);
                }
            }, new Runnable() {
                @Override public void run() {
                    if (onSettingChanged != null) onSettingChanged.run();
                }
            }, true);
        }
    }

    private static void run(final Activity activity, final AtomicBoolean busy,
                            final TextView feedback, final TextView status,
                            final Work work, final Runnable done) {
        run(activity, busy, feedback, status, work, done, false);
    }

    private static void run(final Activity activity, final AtomicBoolean busy,
                            final TextView feedback, final TextView status,
                            final Work work, final Runnable done,
                            boolean alreadyClaimed) {
        if (!alreadyClaimed && !busy.compareAndSet(false, true)) return;
        feedback.setText(t(activity, "正在处理…", "Working…"));
        new Thread(new Runnable() {
            @Override public void run() {
                String result;
                try { result = work.execute(); }
                catch (Throwable error) {
                    result = t(activity, "操作失败：", "Operation failed: ") + safe(error);
                }
                final String finalResult = result;
                activity.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        feedback.setText(finalResult);
                        status.setText(Main.localApiHttpsStatus(activity));
                        busy.set(false);
                        if (done != null) done.run();
                    }
                });
            }
        }, "DeePX-TLS-Setup").start();
    }

    private interface Work {
        String execute() throws Exception;
    }

    private static TextView tab(Activity activity, String zh, String en, boolean dark) {
        TextView view = text(activity, zh, en, 13,
                dark ? 0xFFECECEC : 0xFF1A1A1A, true);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(activity, 42));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private static void styleTab(TextView view, boolean active, boolean dark) {
        view.setTextColor(active ? 0xFFFFFFFF
                : (dark ? 0xFFB8B8BD : 0xFF5F6570));
        view.setBackground(rounded(active ? BRAND
                : (dark ? 0xFF2A2A2E : 0xFFE9ECF3), dp(view.getContext(), 7)));
    }

    private static TextView action(Activity activity, String zh, String en, boolean dark) {
        TextView view = text(activity, zh, en, 13, BRAND, true);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(activity, 44));
        view.setPadding(dp(activity, 10), dp(activity, 8),
                dp(activity, 10), dp(activity, 8));
        view.setClickable(true);
        view.setFocusable(true);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},
                rounded(dark ? 0xFF35353A : 0xFFE1E5EE, dp(activity, 7)));
        states.addState(new int[0],
                rounded(dark ? 0xFF202024 : 0xFFF1F3F8, dp(activity, 7)));
        view.setBackground(states);
        return view;
    }

    private static TextView info(Activity activity, String value, int color, boolean dark) {
        TextView view = text(activity, value, value, 12, color, false);
        view.setTextIsSelectable(true);
        view.setPadding(dp(activity, 12), dp(activity, 11),
                dp(activity, 12), dp(activity, 11));
        view.setBackground(rounded(dark ? 0xFF202024 : 0xFFFFFFFF, dp(activity, 8)));
        return view;
    }

    private static TextView text(Activity activity, String zh, String en,
                                 int size, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(t(activity, zh, en));
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private static GradientDrawable rounded(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private static LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static int statusBarHeight(Activity activity) {
        int id = activity.getResources().getIdentifier(
                "status_bar_height", "dimen", "android");
        return id == 0 ? 0 : activity.getResources().getDimensionPixelSize(id);
    }

    private static boolean isDark(Activity activity) {
        int mode = activity.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private static int dp(android.content.Context context, float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String t(android.content.Context context, String zh, String en) {
        return UiLanguage.text(context, zh, en);
    }

    private static String safe(Throwable error) {
        if (error == null) return "unknown";
        String message = error.getMessage();
        return error.getClass().getSimpleName()
                + (message == null || message.length() == 0 ? "" : ": " + message);
    }
}
