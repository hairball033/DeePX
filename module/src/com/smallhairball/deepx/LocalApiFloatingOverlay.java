package com.smallhairball.deepx;

import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Edge-sized Local API controller owned by the module foreground keeper. */
final class LocalApiFloatingOverlay {
    private static final String PREFS = "dq0_floating_console";
    private static final String KEY_REQUESTED = "requested";
    private static final String KEY_EDGE = "edge";
    private static final String KEY_Y = "y";
    private static final Handler MAIN;
    static { Handler h; try { h = new Handler(Looper.getMainLooper()); } catch (Throwable t) { h = null; } MAIN = h; }
    private static final int BRAND = 0xFF315BEF;
    private static final int SUCCESS = 0xFF26915A;
    private static final int WARNING = 0xFFD57A18;
    private static final int DANGER = 0xFFC44949;
    /** 43dp visible orb: one third larger than the previous 32dp treatment. */
    private static final float BALL_RADIUS_DP = 21.5f;
    /** Forgiving drag target while preserving a compact edge footprint. */
    private static final int BALL_TOUCH_DP = 56;
    private static final long BALL_IDLE_DELAY_MS = 5_000L;
    private static final float BALL_IDLE_ALPHA = .38f;
    private static final int BALL_IDLE_VISIBLE_DP = 22;

    private static Context app;
    private static WindowManager windows;
    private static EdgeBall ball;
    private static WindowManager.LayoutParams ballParams;
    private static View panel;
    private static WindowManager.LayoutParams panelParams;
    private static TextView stateTitle, stateDetail, endpoint, liveData, feedback;
    private static TextView protocolValue;
    private static EditText keyInput, portInput;
    private static HubInsetSwitch serviceSwitch, httpsSwitch;
    private static boolean updating;

    private LocalApiFloatingOverlay() {}

    static boolean isRequested(Context context) {
        return supportsOverlayHost(context)
                && prefs(context).getBoolean(KEY_REQUESTED, false);
    }

    static void setRequested(Context context, boolean requested) {
        if (context == null) return;
        if (!supportsOverlayHost(context)) {
            dismiss();
            return;
        }
        prefs(context).edit().putBoolean(KEY_REQUESTED, requested).apply();
        if (requested) sync(context); else dismiss();
    }

    static boolean hasPermission(Context context) {
        return context != null && (Build.VERSION.SDK_INT < 23
                || Settings.canDrawOverlays(context));
    }

    static void requestPermission(Context context) {
        if (context == null || hasPermission(context)) return;
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + context.getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable ignored) {}
    }

    static void sync(final Context context) {
        if (context == null) return;
        if (!supportsOverlayHost(context)) {
            dismiss();
            return;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(new Runnable() { @Override public void run() { sync(context); } });
            return;
        }
        app = context.getApplicationContext();
        if (!isRequested(app) || !hasPermission(app)) {
            removeWindows();
            return;
        }
        if (windows == null) windows = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        if (windows == null) return;
        if (ball == null) attachBall();
        refreshNow();
    }

    /**
     * Host hooks use HostCompat.isV241(). The foreground overlay itself lives in the companion
     * package process, where host-obfuscated classes are intentionally unavailable, so that
     * process must verify the exact installed host identity instead of inheriting compatibility.
     */
    private static boolean supportsOverlayHost(Context context) {
        if (HostCompat.isV236()) return true;
        if (HostCompat.isV241()) return true;
        if (context == null) return false;
        try {
            android.content.pm.PackageInfo info = context.getPackageManager()
                    .getPackageInfo("com.deepseek.chat", 0);
            long code = Build.VERSION.SDK_INT >= 28
                    ? info.getLongVersionCode() : info.versionCode;
            if (code == 249L && "2.3.6".equals(info.versionName)) return true;
            return code == 257L && "2.4.1".equals(info.versionName);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static void refresh() {
        MAIN.post(new Runnable() { @Override public void run() { refreshNow(); } });
    }

    static void dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(new Runnable() { @Override public void run() { dismiss(); } });
            return;
        }
        removeWindows();
    }

    private static void attachBall() {
        if (windows == null || app == null || ball != null) return;
        ball = new EdgeBall(app);
        int touch = dp(BALL_TOUCH_DP);
        ballParams = new WindowManager.LayoutParams(touch, touch, overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        ballParams.gravity = Gravity.TOP | Gravity.START;
        DisplayMetrics metrics = app.getResources().getDisplayMetrics();
        boolean right = prefs(app).getBoolean(KEY_EDGE, true);
        ballParams.x = right ? Math.max(0, metrics.widthPixels - touch) : 0;
        ballParams.y = clamp(prefs(app).getInt(KEY_Y, metrics.heightPixels / 3),
                dp(28), Math.max(dp(28), metrics.heightPixels - touch - dp(28)));
        try {
            windows.addView(ball, ballParams);
            ball.playEntrance();
            ball.scheduleIdle();
        }
        catch (Throwable error) { ball = null; ballParams = null; }
    }

    private static void togglePanel() {
        if (panel == null) showPanel(); else closePanel();
    }

    private static void showPanel() {
        if (windows == null || app == null || panel != null) return;
        if (ball != null) ball.wakeForInteraction(false);
        final boolean dark = isDark(app);
        final int canvas = dark ? 0xFF191A1D : 0xFFF7F8FA;
        final int surface = dark ? 0xFF24262A : 0xFFFFFFFF;
        final int inset = dark ? 0xFF1D1F23 : 0xFFF2F4F7;
        final int ink = dark ? 0xFFF1F2F4 : 0xFF1B1D21;
        final int secondary = dark ? 0xFFB5B8BF : 0xFF626771;
        final int muted = dark ? 0xFF858A94 : 0xFF8B9099;
        final int line = dark ? 0xFF3B3E45 : 0xFFE1E4E9;

        LinearLayout root = new DismissOnOutsideLayout(app);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(14));
        root.setBackground(round(surface, line, 16, 1));

        LinearLayout head = new LinearLayout(app);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout identity = new LinearLayout(app);
        identity.setOrientation(LinearLayout.VERTICAL);
        identity.addView(label("本地 API", 18, ink, true));
        identity.addView(label("边缘控制台 · 实时连接", 11, muted, false));
        head.addView(identity, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = label("×", 24, secondary, false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("关闭悬浮面板");
        close.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { closePanel(); } });
        head.addView(close, new LinearLayout.LayoutParams(dp(40), dp(40)));
        root.addView(head);

        ScrollView scroll = new ScrollView(app);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(app);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(10), 0, dp(6));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout hero = new LinearLayout(app);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(14), dp(13), dp(14), dp(13));
        hero.setBackground(round(inset, line, 12, 1));
        stateTitle = label("读取状态…", 17, ink, true);
        stateDetail = label("等待保活心跳", 12, secondary, false);
        endpoint = label("", 11, muted, false);
        endpoint.setTypeface(Typeface.MONOSPACE);
        endpoint.setTextIsSelectable(true);
        hero.addView(stateTitle);
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(-1, -2);
        detailLp.topMargin = dp(3); hero.addView(stateDetail, detailLp);
        LinearLayout.LayoutParams endpointLp = new LinearLayout.LayoutParams(-1, -2);
        endpointLp.topMargin = dp(8); hero.addView(endpoint, endpointLp);
        content.addView(hero);

        content.addView(section("服务", muted));
        LinearLayout serviceRow = settingRow("启用本地 API", "关闭后小球仍可用于重新开启", ink, secondary);
        serviceSwitch = themedSwitch(dark);
        serviceRow.addView(serviceSwitch, new LinearLayout.LayoutParams(dp(56), dp(34)));
        content.addView(serviceRow);

        LinearLayout protocolRow = settingRow("兼容格式", "OpenAI Chat / Responses 或 Anthropic", ink, secondary);
        protocolValue = actionText("OpenAI  ›", ink, inset, line);
        protocolRow.addView(protocolValue);
        content.addView(protocolRow);

        LinearLayout httpsRow = settingRow("HTTPS", "启用内置 TLS 监听", ink, secondary);
        httpsSwitch = themedSwitch(dark);
        httpsRow.addView(httpsSwitch, new LinearLayout.LayoutParams(dp(56), dp(34)));
        content.addView(httpsRow);

        content.addView(section("连接凭证", muted));
        keyInput = input("API Key", ink, secondary, inset, line);
        keyInput.setSingleLine(true);
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        content.addView(keyInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        LinearLayout keyActions = actions();
        TextView saveKey = button("保存 Key", Color.WHITE, BRAND, BRAND, true);
        TextView rotate = button("随机生成", WARNING, inset, line, false);
        keyActions.addView(saveKey, weight());
        keyActions.addView(rotate, weightWithStart()); content.addView(keyActions);

        portInput = input("监听端口（1024–65535）", ink, secondary, inset, line);
        portInput.setSingleLine(true); portInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        LinearLayout.LayoutParams portLp = new LinearLayout.LayoutParams(-1, dp(44));
        portLp.topMargin = dp(8); content.addView(portInput, portLp);
        LinearLayout connectionActions = actions();
        TextView savePort = button("保存端口", ink, inset, line, false);
        TextView copyConfig = button("复制 URL + Key", Color.WHITE, BRAND, BRAND, true);
        connectionActions.addView(savePort, weight());
        connectionActions.addView(copyConfig, weightWithStart());
        content.addView(connectionActions);

        content.addView(section("实时数据", muted));
        liveData = label("尚未收到状态", 11, secondary, false);
        liveData.setTypeface(Typeface.MONOSPACE);
        liveData.setTextIsSelectable(true);
        liveData.setLineSpacing(dp(2), 1f);
        liveData.setPadding(dp(12), dp(11), dp(12), dp(11));
        liveData.setBackground(round(inset, line, 10, 1));
        content.addView(liveData);

        feedback = label("", 11, BRAND, false);
        feedback.setGravity(Gravity.CENTER);
        feedback.setMinHeight(dp(28));
        feedback.setPadding(0, dp(8), 0, 0);
        root.addView(feedback);

        serviceSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                if (updating) return;
                control(z21.OP_SET_ENABLED, String.valueOf(checked));
            }
        });
        httpsSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                if (updating) return;
                control(z21.OP_SET_HTTPS, String.valueOf(checked));
            }
        });
        protocolValue.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                control(z21.OP_SET_PROTOCOL, "anthropic".equals(z21.protocolValue()) ? "openai" : "anthropic");
            }
        });
        saveKey.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { control(z21.OP_SET_KEY, keyInput.getText().toString()); }
        });
        rotate.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { control(z21.OP_ROTATE_KEY, ""); }
        });
        savePort.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { control(z21.OP_SET_PORT, portInput.getText().toString()); }
        });
        copyConfig.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                copy("本地 API 连接配置", "URL: " + displayedEndpoint()
                        + "\nAPI Key: " + z21.apiKeyValue());
            }
        });

        panel = root;
        DisplayMetrics metrics = app.getResources().getDisplayMetrics();
        int width = Math.min(dp(304), metrics.widthPixels - dp(32));
        int height = Math.min(dp(528), metrics.heightPixels - dp(96));
        panelParams = new WindowManager.LayoutParams(width, height, overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        boolean right = prefs(app).getBoolean(KEY_EDGE, true);
        panelParams.gravity = Gravity.TOP | (right ? Gravity.END : Gravity.START);
        panelParams.x = dp(8); panelParams.y = dp(36);
        panelParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        try {
            windows.addView(panel, panelParams);
            panel.setAlpha(0f);
            panel.setScaleX(.965f);
            panel.setScaleY(.965f);
            panel.setTranslationX(right ? dp(24) : -dp(24));
            final View openingPanel = panel;
            openingPanel.post(new Runnable() {
                @Override public void run() {
                    if (panel != openingPanel) return;
                    openingPanel.animate().cancel();
                    openingPanel.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .translationX(0f)
                            .setInterpolator(new DecelerateInterpolator(1.7f))
                            .setDuration(210L)
                            .start();
                }
            });
            refreshNow();
        } catch (Throwable error) {
            panel = null; panelParams = null;
        }
    }

    private static void refreshNow() {
        if (ball != null) ball.invalidate();
        if (panel == null) return;
        updating = true;
        try {
            boolean running = z21.gatewayRunning();
            stateTitle.setText(running ? "监听正常" : z21.apiEnabled() ? "正在连接" : "服务已关闭");
            stateTitle.setTextColor(running ? SUCCESS : z21.apiEnabled() ? WARNING : DANGER);
            long age = z21.heartbeatAgeMs();
            if (age < 0) {
                stateDetail.setText("等待 DeepSeek 心跳");
            } else if (z21.hasV241PortDetails()) {
                String ports = "HTTP " + z21.v241HttpPort();
                if (z21.v241HttpsPort() > 0) {
                    ports += " · HTTPS " + z21.v241HttpsPort();
                }
                stateDetail.setText("心跳 " + Math.max(0L, age / 1000L)
                        + " 秒前 · 配置端口 " + z21.v241PreferredPort() + " · " + ports);
            } else {
                stateDetail.setText("心跳 " + Math.max(0L, age / 1000L)
                        + " 秒前 · 端口 " + z21.gatewayPort());
            }
            endpoint.setText(displayedEndpoint());
            serviceSwitch.setChecked(z21.apiEnabled());
            httpsSwitch.setChecked(z21.httpsEnabled());
            protocolValue.setText("anthropic".equals(z21.protocolValue()) ? "Anthropic  ›" : "OpenAI  ›");
            if (!keyInput.hasFocus()) keyInput.setText(z21.apiKeyValue());
            int configuredPort = z21.hasV241PortDetails()
                    ? z21.v241PreferredPort() : z21.gatewayPort();
            if (!portInput.hasFocus() && configuredPort > 0) {
                portInput.setText(String.valueOf(configuredPort));
            }
            String runtime = z21.runtimeStatus();
            liveData.setText(runtime == null || runtime.length() == 0 ? "尚未收到状态" : runtime);
        } finally { updating = false; }
    }

    private static void control(String operation, String value) {
        if (feedback != null) feedback.setText("正在应用…");
        z21.sendGatewayControl(app, operation, value, new z21.ControlCallback() {
            @Override public void onResult(boolean success, String message) {
                if (feedback != null) {
                    feedback.setTextColor(success ? SUCCESS : DANGER);
                    feedback.setText(message);
                }
                refreshNow();
                MAIN.postDelayed(new Runnable() { @Override public void run() { refreshNow(); } }, 180L);
                MAIN.postDelayed(new Runnable() { @Override public void run() {
                    if (feedback != null) feedback.setText("");
                } }, 2_200L);
            }
        });
    }

    private static void copy(String label, String value) {
        if (app == null || value == null || value.length() == 0) return;
        ClipboardManager clipboard = (ClipboardManager) app.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
        if (feedback != null) { feedback.setTextColor(SUCCESS); feedback.setText(label + " 已复制"); }
    }

    private static String displayedEndpoint() {
        String connection = z21.connectionInfo();
        if (connection != null) {
            String[] lines = connection.split("\\n");
            for (String line : lines) {
                int split = line.indexOf('：');
                if (split >= 0 && line.substring(0, split).contains("本机地址")) return line.substring(split + 1).trim();
                split = line.indexOf(": ");
                if (split >= 0 && line.substring(0, split).contains("Local address")) return line.substring(split + 2).trim();
            }
        }
        return (z21.httpsEnabled() ? "https" : "http") + "://127.0.0.1:"
                + (z21.gatewayPort() > 0 ? z21.gatewayPort() : 8765) + "/v1";
    }

    private static void closePanel() {
        final View old = panel;
        final boolean right = app != null && prefs(app).getBoolean(KEY_EDGE, true);
        panel = null; panelParams = null;
        stateTitle = stateDetail = endpoint = liveData = feedback = protocolValue = null;
        keyInput = portInput = null; serviceSwitch = httpsSwitch = null;
        if (old == null || windows == null) return;
        old.animate().cancel();
        old.animate()
                .alpha(0f)
                .scaleX(.97f)
                .scaleY(.97f)
                .translationX(right ? dp(20) : -dp(20))
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .setDuration(160L)
                .setListener(new android.animation.AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(android.animation.Animator animation) {
                        old.setVisibility(View.INVISIBLE);
                        if (windows != null) {
                            try { windows.removeViewImmediate(old); } catch (Throwable ignored) {
                                try { windows.removeView(old); } catch (Throwable ignoredAgain) {}
                            }
                        }
                        if (ball != null) ball.scheduleIdle();
                    }
                })
                .start();
    }

    private static void removeWindows() {
        if (ball != null) ball.cancelIdle();
        if (windows != null) {
            if (panel != null) try { windows.removeView(panel); } catch (Throwable ignored) {}
            if (ball != null) try { windows.removeView(ball); } catch (Throwable ignored) {}
        }
        panel = null; panelParams = null; ball = null; ballParams = null;
        stateTitle = stateDetail = endpoint = liveData = feedback = protocolValue = null;
        keyInput = portInput = null; serviceSwitch = httpsSwitch = null;
    }

    private static int overlayType() {
        return Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static boolean isDark(Context context) {
        return (context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private static TextView label(String text, float size, int color, boolean bold) {
        TextView view = new TextView(app); view.setText(text); view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private static TextView section(String text, int color) {
        TextView view = label(text, 11, color, true);
        view.setLetterSpacing(.05f); view.setPadding(dp(2), dp(18), dp(2), dp(7));
        return view;
    }

    private static LinearLayout settingRow(String title, String detail, int ink, int secondary) {
        LinearLayout row = new LinearLayout(app); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(9), dp(2), dp(9));
        LinearLayout text = new LinearLayout(app); text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(title, 14, ink, true)); text.addView(label(detail, 11, secondary, false));
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1f)); return row;
    }

    private static HubInsetSwitch themedSwitch(boolean dark) {
        HubInsetSwitch view = new HubInsetSwitch(app);
        int[][] states = {new int[]{android.R.attr.state_checked}, new int[]{-android.R.attr.state_checked}};
        view.setThumbTintList(new android.content.res.ColorStateList(states,
                new int[]{BRAND, dark ? 0xFFCACDD3 : Color.WHITE}));
        view.setTrackTintList(new android.content.res.ColorStateList(states,
                new int[]{0xFF9DB2FF, dark ? 0xFF555961 : 0xFFC6CAD1}));
        view.setBackground(null); return view;
    }

    private static EditText input(String hint, int ink, int secondary, int fill, int line) {
        EditText view = new EditText(app); view.setHint(hint); view.setTextColor(ink);
        view.setHintTextColor(secondary); view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        view.setPadding(dp(11), 0, dp(11), 0); view.setBackground(round(fill, line, 8, 1));
        view.setSelectAllOnFocus(false); view.setMinHeight(dp(44)); return view;
    }

    private static TextView actionText(String text, int ink, int fill, int line) {
        TextView view = label(text, 12, ink, true); view.setGravity(Gravity.CENTER);
        view.setPadding(dp(11), dp(8), dp(9), dp(8)); view.setBackground(round(fill, line, 8, 1));
        view.setClickable(true); return view;
    }

    private static LinearLayout actions() {
        LinearLayout row = new LinearLayout(app); row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(40));
        params.topMargin = dp(7); row.setLayoutParams(params); return row;
    }

    private static TextView button(String text, int color, int fill, int line, boolean bold) {
        TextView view = label(text, 11, color, bold); view.setGravity(Gravity.CENTER);
        view.setBackground(round(fill, line, 8, 1)); view.setClickable(true);
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    v.animate().scaleX(.97f).scaleY(.97f).alpha(.82f).setDuration(70L).start();
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                        || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(90L).start();
                }
                return false;
            }
        }); return view;
    }

    private static LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, -1, 1f); }
    private static LinearLayout.LayoutParams weightWithStart() {
        LinearLayout.LayoutParams value = weight(); value.leftMargin = dp(6); return value;
    }

    private static GradientDrawable round(int fill, int line, float radius, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius)); drawable.setStroke(dp(strokeDp), line); return drawable;
    }

    private static int dp(float value) {
        if (app == null) return (int) value;
        return Math.round(value * app.getResources().getDisplayMetrics().density);
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    /** Receives the first touch outside this overlay while allowing that touch into the app below. */
    private static final class DismissOnOutsideLayout extends LinearLayout {
        DismissOnOutsideLayout(Context context) { super(context); }

        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
                if ((keyInput != null && keyInput.hasFocus())
                        || (portInput != null && portInput.hasFocus())) {
                    MAIN.postDelayed(new Runnable() {
                        @Override public void run() {
                            boolean editing = (keyInput != null && keyInput.hasFocus())
                                    || (portInput != null && portInput.hasFocus());
                            if (!editing) closePanel();
                        }
                    }, 120L);
                } else {
                    closePanel();
                }
                return false;
            }
            return super.dispatchTouchEvent(event);
        }
    }

    private static final class EdgeBall extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path iconMask = new Path();
        private final Drawable deepSeekIcon;
        private ValueAnimator snapAnimator;
        private float downX, downY, rawStartX, rawStartY;
        private int startX, startY;
        private boolean moved;
        private final Runnable idle = new Runnable() {
            @Override public void run() { tuckIntoEdge(); }
        };

        EdgeBall(Context context) {
            super(context);
            setContentDescription("DeepSeek 本地 API 小窗保活");
            Drawable icon = null;
            try {
                icon = context.getPackageManager().getApplicationIcon("com.deepseek.chat");
            } catch (Throwable ignored) {}
            deepSeekIcon = icon;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() * .5f, cy = getHeight() * .5f, radius = dp(BALL_RADIUS_DP);
            paint.setStyle(Paint.Style.FILL); paint.setColor(0xF2FFFFFF);
            canvas.drawCircle(cx, cy, radius + dp(2), paint);
            if (deepSeekIcon != null) {
                // PackageManager may return a legacy square or an AdaptiveIconDrawable whose
                // platform mask is not circular. Always apply our own orb mask so no icon corner
                // can escape the floating ball on any launcher/OEM skin.
                float iconRadius = radius - dp(.7f);
                int insetX = Math.round(cx - iconRadius);
                int insetY = Math.round(cy - iconRadius);
                int size = Math.round(iconRadius * 2f);
                iconMask.reset();
                iconMask.addCircle(cx, cy, iconRadius, Path.Direction.CW);
                int save = canvas.save();
                canvas.clipPath(iconMask);
                deepSeekIcon.setBounds(insetX, insetY, insetX + size, insetY + size);
                deepSeekIcon.draw(canvas);
                canvas.restoreToCount(save);
            } else {
                paint.setColor(BRAND);
                canvas.drawCircle(cx, cy, radius, paint);
            }
            // Keep status readable without replacing the requested DeepSeek application icon.
            float statusX = cx + radius * .68f, statusY = cy + radius * .68f;
            paint.setColor(0xF7FFFFFF);
            canvas.drawCircle(statusX, statusY, dp(6.2f), paint);
            paint.setColor(z21.gatewayRunning() ? SUCCESS : z21.apiEnabled() ? WARNING : DANGER);
            canvas.drawCircle(statusX, statusY, dp(4.4f), paint);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (ballParams == null || windows == null) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (snapAnimator != null) snapAnimator.cancel();
                    wakeForInteraction(false);
                    downX = event.getX(); downY = event.getY(); rawStartX = event.getRawX();
                    rawStartY = event.getRawY(); startX = ballParams.x; startY = ballParams.y;
                    moved = false; return true;
                case MotionEvent.ACTION_MOVE:
                    animate().cancel();
                    setTranslationX(0f);
                    setTranslationY(0f);
                    float dx = event.getRawX() - rawStartX, dy = event.getRawY() - rawStartY;
                    if (Math.abs(dx) + Math.abs(dy) > dp(5)) moved = true;
                    DisplayMetrics metrics = getResources().getDisplayMetrics();
                    ballParams.x = clamp(startX + Math.round(dx), 0, metrics.widthPixels - getWidth());
                    ballParams.y = clamp(startY + Math.round(dy), dp(24), metrics.heightPixels - getHeight() - dp(24));
                    try { windows.updateViewLayout(this, ballParams); } catch (Throwable ignored) {}
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved && Math.abs(event.getX() - downX) < dp(7)
                            && Math.abs(event.getY() - downY) < dp(7)) { togglePanel(); return true; }
                    snapToEdge(); return true;
                case MotionEvent.ACTION_CANCEL: snapToEdge(); return true;
                default: return true;
            }
        }

        private void snapToEdge() {
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            final boolean right = ballParams.x + getWidth() / 2 >= metrics.widthPixels / 2;
            final int from = ballParams.x;
            final int to = right ? metrics.widthPixels - getWidth() : 0;
            if (from == to) {
                persistPosition(right);
                scheduleIdle();
                return;
            }
            snapAnimator = ValueAnimator.ofInt(from, to);
            snapAnimator.setDuration(190L);
            snapAnimator.setInterpolator(new DecelerateInterpolator(1.8f));
            snapAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator animation) {
                    if (ballParams == null || windows == null) return;
                    ballParams.x = (Integer) animation.getAnimatedValue();
                    try { windows.updateViewLayout(EdgeBall.this, ballParams); }
                    catch (Throwable ignored) {}
                }
            });
            snapAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator animation) {
                    if (ballParams != null) {
                        ballParams.x = to;
                        persistPosition(right);
                    }
                    snapAnimator = null;
                    scheduleIdle();
                }
            });
            snapAnimator.start();
        }

        void cancelIdle() {
            MAIN.removeCallbacks(idle);
            animate().cancel();
        }

        void playEntrance() {
            setAlpha(0f);
            setScaleX(.68f);
            setScaleY(.68f);
            post(new Runnable() {
                @Override public void run() {
                    if (ball != EdgeBall.this) return;
                    animate().cancel();
                    animate().alpha(1f).scaleX(1f).scaleY(1f)
                            .setInterpolator(new OvershootInterpolator(.9f))
                            .setDuration(280L).start();
                }
            });
        }

        void scheduleIdle() {
            MAIN.removeCallbacks(idle);
            if (panel == null) MAIN.postDelayed(idle, BALL_IDLE_DELAY_MS);
        }

        void wakeForInteraction(boolean scheduleAgain) {
            cancelIdle();
            if (ballParams != null && windows != null) {
                DisplayMetrics metrics = getResources().getDisplayMetrics();
                boolean right = prefs(getContext()).getBoolean(KEY_EDGE, true);
                animateWindowX(right ? metrics.widthPixels - getWidth() : 0,
                        1f, 180L, new DecelerateInterpolator(1.8f));
            } else {
                animate().alpha(1f).setDuration(140L).start();
            }
            if (scheduleAgain) scheduleIdle();
        }

        private void tuckIntoEdge() {
            if (panel != null || ballParams == null || windows == null) return;
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            boolean right = prefs(getContext()).getBoolean(KEY_EDGE, true);
            int visible = Math.min(getWidth(), dp(BALL_IDLE_VISIBLE_DP));
            animateWindowX(right ? metrics.widthPixels - visible : -(getWidth() - visible),
                    BALL_IDLE_ALPHA, 260L, new DecelerateInterpolator(1.6f));
        }

        private void animateWindowX(int targetX, float targetAlpha, long duration,
                android.animation.TimeInterpolator interpolator) {
            if (ballParams == null || windows == null) return;
            int oldX = ballParams.x;
            animate().cancel();
            ballParams.x = targetX;
            try {
                windows.updateViewLayout(this, ballParams);
                setTranslationX(oldX - targetX);
                animate().translationX(0f).alpha(targetAlpha)
                        .setInterpolator(interpolator).setDuration(duration).start();
            } catch (Throwable ignored) {
                setTranslationX(0f);
                setAlpha(targetAlpha);
            }
        }

        private void persistPosition(boolean right) {
            if (ballParams == null) return;
            prefs(getContext()).edit().putBoolean(KEY_EDGE, right)
                    .putInt(KEY_Y, ballParams.y).apply();
        }
    }
}
