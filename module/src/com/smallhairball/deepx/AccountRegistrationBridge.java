package com.smallhairball.deepx;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.UUID;

/**
 * Stable, plugin-facing adapter over DeepSeek's normal registration ViewModels.
 *
 * <p>The plugin only receives an opaque session id. Passwords, verification codes, captcha rid
 * values and credentials remain in this in-process adapter and are erased when the session ends.
 * This class does not alter risk payloads or turn a server rejection into success.</p>
 */
final class AccountRegistrationBridge {
    interface Callback {
        void onResult(boolean success, String code, String message, JSONObject data);
    }

    private static final Object LOCK = new Object();
    private static final Handler MAIN;
    static { Handler h; try { h = new Handler(Looper.getMainLooper()); } catch (Throwable t) { h = null; } MAIN = h; }
    private static final IdentityHashMap<Object, Session> CAPTCHA_VIEWS =
            new IdentityHashMap<Object, Session>();
    private static Session active;

    private static final class Session {
        final String id = UUID.randomUUID().toString();
        final String pluginId;
        final String channel;
        final String identity;
        final String originalJson;
        final Activity activity;
        final Object viewModel;
        String password;
        String state = "created";
        String lastErrorCode = "";
        String lastErrorMessage = "";
        long operationStartedAt;
        boolean sawSending;
        boolean capturePending;
        Callback callback;
        Dialog captchaDialog;
        Object captchaView;

        Session(String pluginId, String channel, String identity, String password,
                String originalJson, Activity activity, Object viewModel) {
            this.pluginId = pluginId;
            this.channel = channel;
            this.identity = identity;
            this.password = password;
            this.originalJson = originalJson;
            this.activity = activity;
            this.viewModel = viewModel;
        }
    }

    private AccountRegistrationBridge() {}

    static String start(Activity activity, String pluginId, String channel, String identity,
                        String password, Callback callback) {
        if (activity == null) return "当前没有可用界面";
        String kind = channel == null ? "" : channel.trim().toLowerCase(Locale.US);
        String account = identity == null ? "" : identity.trim();
        String secret = password == null ? "" : password;
        if (!("email".equals(kind) || "phone".equals(kind))) return "请选择邮箱或手机号注册";
        if (account.length() == 0 || account.length() > 180) return "请输入有效的邮箱或手机号";
        if ("email".equals(kind)) {
            if (!account.contains("@") || account.startsWith("@") || account.endsWith("@")) {
                return "邮箱格式不正确";
            }
            if (secret.length() < 8 || secret.length() > 50) return "密码长度必须为 8 到 50 位";
        } else {
            String digits = account.replace(" ", "").replace("-", "");
            if (digits.startsWith("+86")) digits = digits.substring(3);
            if (!digits.matches("1[3-9][0-9]{9}")) return "请输入中国大陆手机号";
            account = digits;
            secret = "";
        }
        synchronized (LOCK) {
            if (active != null) return "已有注册流程正在进行，请先完成或取消";
        }
        try {
            ClassLoader cl = activity.getClassLoader();
            Object vm = createViewModel(cl, kind, account, secret);
            String original = AccountManager.readCurrentJson(cl);
            if (original == null || original.length() == 0) return "当前账号状态不可用，无法安全添加候选账号";
            Session session = new Session(pluginId, kind, account, secret, original,
                    activity, vm);
            synchronized (LOCK) {
                if (active != null) return "已有注册流程正在进行，请先完成或取消";
                active = session;
            }
            beginCaptcha(session, callback);
            return null;
        } catch (Throwable error) {
            Main.log("quick registration start failed: " + safe(error));
            return "当前 DeepSeek 版本不支持快捷注册";
        }
    }

    static String resend(String pluginId, String sessionId, Callback callback) {
        Session session = owned(pluginId, sessionId);
        if (session == null) return "注册会话已失效，请重新开始";
        synchronized (LOCK) {
            if (!"code_sent".equals(session.state)) return "当前状态不能重新发送验证码";
        }
        beginCaptcha(session, callback);
        return null;
    }

    static String complete(String pluginId, String sessionId, String code, Callback callback) {
        final Session session = owned(pluginId, sessionId);
        if (session == null) return "注册会话已失效，请重新开始";
        final String otp = code == null ? "" : code.trim();
        if (!otp.matches("[0-9]{4,8}")) return "请输入 4 到 8 位数字验证码";
        synchronized (LOCK) {
            if (!"code_sent".equals(session.state)) return "请先获取验证码";
            session.state = "submitting";
            session.callback = callback;
            session.capturePending = true;
            session.operationStartedAt = System.currentTimeMillis();
            session.lastErrorCode = "";
            session.lastErrorMessage = "";
        }
        try {
            dispatchSubmit(session, otp);
            MAIN.postDelayed(new Runnable() {
                @Override public void run() {
                    synchronized (LOCK) {
                        if (active != session || !session.capturePending
                                || System.currentTimeMillis() - session.operationStartedAt < 44000L) return;
                    }
                    finish(session, false, "registration_timeout", "注册请求超时，请稍后重试", null);
                }
            }, 44500L);
            return null;
        } catch (Throwable error) {
            Main.log("quick registration submit failed: " + safe(error));
            finish(session, false, "registration_failed", "注册提交失败，请稍后重试", null);
            return null;
        }
    }

    static boolean cancel(String pluginId, String sessionId) {
        Session session = owned(pluginId, sessionId);
        if (session == null) return false;
        finish(session, false, "registration_cancelled", "注册已取消", null);
        return true;
    }

    static boolean ownsCaptcha(Object view) {
        synchronized (LOCK) { return CAPTCHA_VIEWS.containsKey(view); }
    }

    static void captchaSuccess(Object view, String rid, boolean passed) {
        final Session session;
        synchronized (LOCK) { session = CAPTCHA_VIEWS.get(view); }
        if (session == null || !passed || rid == null || rid.trim().length() == 0) return;
        detachCaptcha(session);
        try {
            dispatchCaptchaToken(session, rid.trim());
            session.operationStartedAt = System.currentTimeMillis();
            session.sawSending = false;
            pollCodeDelivery(session);
        } catch (Throwable error) {
            Main.log("quick registration captcha dispatch failed: " + safe(error));
            finish(session, false, "captcha_dispatch_failed", "安全验证结果提交失败", null);
        }
    }

    static void captchaClosed(Object view) {
        Session session;
        synchronized (LOCK) { session = CAPTCHA_VIEWS.get(view); }
        if (session != null) {
            detachCaptcha(session);
            finish(session, false, "captcha_cancelled", "已取消安全验证", null);
        }
    }

    static void captchaError(Object view, String detail) {
        Session session;
        synchronized (LOCK) { session = CAPTCHA_VIEWS.get(view); }
        if (session != null) {
            detachCaptcha(session);
            finish(session, false, "captcha_failed",
                    detail == null || detail.length() == 0 ? "安全验证加载失败" : detail, null);
        }
    }

    static boolean hasCredentialCapturePending() {
        synchronized (LOCK) { return active != null && active.capturePending; }
    }

    static boolean hasActiveSession() {
        synchronized (LOCK) { return active != null; }
    }

    static String credentialCaptureOriginalJson() {
        synchronized (LOCK) { return active == null ? null : active.originalJson; }
    }

    static void credentialCaptured(boolean saved) {
        Session session;
        synchronized (LOCK) { session = active; }
        if (session == null || !session.capturePending) return;
        JSONObject data = new JSONObject();
        put(data, "sessionId", session.id);
        put(data, "state", "completed");
        put(data, "channel", session.channel);
        put(data, "identity", mask(session.identity, session.channel));
        finish(session, saved, saved ? "ok" : "candidate_save_failed", saved
                ? "注册成功，已加入候选账号；当前账号保持不变"
                : "注册成功，但候选账号凭证保存失败", data);
    }

    static void hostError(String resourceName, String fallback) {
        Session session;
        synchronized (LOCK) { session = active; }
        if (session == null) return;
        String[] mapped = mapError(resourceName, fallback);
        if (mapped == null) return;
        synchronized (LOCK) {
            if (active != session) return;
            session.lastErrorCode = mapped[0];
            session.lastErrorMessage = mapped[1];
        }
        if (session.capturePending) finish(session, false, mapped[0], mapped[1], null);
    }

    /** Receives DeepSeek's own verification-code request verdict from c3a.r(). */
    static void codeRequestResult(String operation, boolean success) {
        final Session session;
        synchronized (LOCK) { session = active; }
        if (session == null || !"captcha".equals(session.state)) return;
        String expected = "email".equals(session.channel)
                ? "create_email_verification_code" : "create_sms_verification_code";
        if (!expected.equals(operation)) return;
        if (success) {
            markCodeSent(session);
            return;
        }
        MAIN.postDelayed(new Runnable() {
            @Override public void run() {
                synchronized (LOCK) {
                    if (active != session || !"captcha".equals(session.state)) return;
                    String code = session.lastErrorCode.length() == 0
                            ? "verification_send_failed" : session.lastErrorCode;
                    String message = session.lastErrorMessage.length() == 0
                            ? "验证码发送失败，请检查账号、网络或服务器提示"
                            : session.lastErrorMessage;
                    operationResult(session, false, code, message, null);
                }
            }
        }, 240L);
    }

    private static Object createViewModel(ClassLoader cl, String channel, String identity,
                                          String password) throws Exception {
        if ("email".equals(channel)) {
            Class<?> vmType = HostCompat.load(cl, "uu7");
            Object vm = vmType.getDeclaredConstructor().newInstance();
            Method update = vmType.getDeclaredMethod("g", HostCompat.load(cl, "pu7"));
            update.setAccessible(true);
            update.invoke(vm, stringEvent(cl, "lu7", identity));
            update.invoke(vm, stringEvent(cl, "nu7", password));
            update.invoke(vm, stringEvent(cl, "mu7", password));
            return vm;
        }
        Class<?> vmType = HostCompat.load(cl, "ly7");
        Object vm = vmType.getDeclaredConstructor().newInstance();
        Method update = vmType.getDeclaredMethod("f", HostCompat.load(cl, "iy7"));
        update.setAccessible(true);
        update.invoke(vm, stringEvent(cl, "gy7", identity));
        return vm;
    }

    private static Object stringEvent(ClassLoader cl, String name, String value) throws Exception {
        Constructor<?> ctor = HostCompat.load(cl, name).getDeclaredConstructor(String.class);
        ctor.setAccessible(true);
        return ctor.newInstance(value);
    }

    private static void beginCaptcha(Session session, Callback callback) {
        synchronized (LOCK) {
            session.callback = callback;
            session.state = "captcha";
            session.lastErrorCode = "";
            session.lastErrorMessage = "";
        }
        try {
            showCaptcha(session);
        } catch (Throwable error) {
            Main.log("quick registration captcha open failed: " + safe(error));
            finish(session, false, "captcha_unavailable", "官方安全验证组件无法启动", null);
        }
    }

    private static void showCaptcha(final Session session) throws Exception {
        Activity activity = session.activity;
        ClassLoader cl = activity.getClassLoader();
        Class<?> webType = cl.loadClass("com.ishumei.sdk.captcha.SmCaptchaWebView");
        Object web = webType.getDeclaredConstructor(android.content.Context.class)
                .newInstance(activity);
        Class<?> optionType = cl.loadClass(
                "com.ishumei.sdk.captcha.SmCaptchaWebView$SmOption");
        Object option = optionType.getDeclaredConstructor().newInstance();
        invokeSetter(optionType, option, "setOrganization", String.class,
                "P9usCUBauxft8eAmUXaZ");
        invokeSetter(optionType, option, "setAppId", String.class, "default");
        invokeSetter(optionType, option, "setMode", String.class, "spatial");
        invokeSetter(optionType, option, "setTimeout", int.class, 15000);
        Class<?> listenerType = cl.loadClass("com.ishumei.sdk.captcha.SimpleResultListener");
        Object listener = listenerType.getDeclaredConstructor().newInstance();

        Dialog dialog = new Dialog(activity);
        boolean dark = (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int paper = dark ? 0xFF18191B : Color.WHITE;
        int ink = dark ? 0xFFF1F1F2 : 0xFF16171A;
        int line = dark ? 0xFF34363A : 0xFFE3E5E8;
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(activity, 20), dp(activity, 18), dp(activity, 20), dp(activity, 20));
        GradientDrawable panel = new GradientDrawable();
        panel.setColor(paper);
        panel.setCornerRadius(dp(activity, 18));
        panel.setStroke(dp(activity, 1), line);
        body.setBackground(panel);

        LinearLayout heading = new LinearLayout(activity);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(activity);
        title.setText("安全验证");
        title.setTextColor(ink);
        title.setTextSize(17);
        title.setTypeface(android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD);
        heading.addView(title, new LinearLayout.LayoutParams(0, dp(activity, 40), 1));
        TextView close = new TextView(activity);
        close.setText("关闭");
        close.setTextColor(dark ? 0xFFB6B8BD : 0xFF62666D);
        close.setTextSize(13);
        close.setGravity(Gravity.CENTER);
        heading.addView(close, new LinearLayout.LayoutParams(dp(activity, 52), dp(activity, 40)));
        body.addView(heading, new LinearLayout.LayoutParams(-1, dp(activity, 40)));

        FrameLayout frame = new FrameLayout(activity);
        frame.setBackgroundColor(paper);
        frame.addView((View) web, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(
                -1, dp(activity, 330));
        frameParams.topMargin = dp(activity, 8);
        body.addView(frame, frameParams);
        dialog.setContentView(body);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.dimAmount = 0.38f;
            window.setAttributes(attrs);
        }
        close.setOnClickListener(v -> captchaClosed(web));
        synchronized (LOCK) {
            CAPTCHA_VIEWS.put(web, session);
            session.captchaDialog = dialog;
            session.captchaView = web;
        }
        dialog.show();
        if (window != null) window.setLayout(
                Math.min(dp(activity, 384), activity.getResources().getDisplayMetrics().widthPixels
                        - dp(activity, 32)), WindowManager.LayoutParams.WRAP_CONTENT);
        Method init = webType.getDeclaredMethod("initWithOption", optionType, listenerType);
        init.setAccessible(true);
        Object status = init.invoke(web, option, listener);
        if (status instanceof Number && ((Number) status).intValue() != 0) {
            throw new IllegalStateException("captcha init status=" + status);
        }
    }

    private static void invokeSetter(Class<?> type, Object target, String name,
                                     Class<?> argumentType, Object value) throws Exception {
        Method method = type.getDeclaredMethod(name, argumentType);
        method.setAccessible(true);
        method.invoke(target, value);
    }

    private static void dispatchCaptchaToken(Session session, String rid) throws Exception {
        ClassLoader cl = session.activity.getClassLoader();
        Class<?> tokenType = HostCompat.load(cl, "yp0");
        Constructor<?> tokenCtor = tokenType.getDeclaredConstructor(String.class, String.class);
        tokenCtor.setAccessible(true);
        Object token = tokenCtor.newInstance(rid, "CN");
        if ("email".equals(session.channel)) {
            Class<?> vmType = HostCompat.load(cl, "uu7");
            Constructor<?> eventCtor = HostCompat.load(cl, "iu7")
                    .getDeclaredConstructor(HostCompat.load(cl, "zp0"));
            eventCtor.setAccessible(true);
            Method dispatch = vmType.getDeclaredMethod("f", HostCompat.load(cl, "ju7"));
            dispatch.setAccessible(true);
            dispatch.invoke(session.viewModel, eventCtor.newInstance(token));
        } else {
            Class<?> vmType = HostCompat.load(cl, "ly7");
            Constructor<?> eventCtor = HostCompat.load(cl, "dy7")
                    .getDeclaredConstructor(HostCompat.load(cl, "zp0"));
            eventCtor.setAccessible(true);
            Method dispatch = vmType.getDeclaredMethod("e", HostCompat.load(cl, "ey7"));
            dispatch.setAccessible(true);
            dispatch.invoke(session.viewModel, eventCtor.newInstance(token));
        }
    }

    private static void pollCodeDelivery(final Session session) {
        MAIN.postDelayed(new Runnable() {
            @Override public void run() {
                synchronized (LOCK) {
                    if (active != session || !"captcha".equals(session.state)) return;
                    if (session.lastErrorMessage.length() > 0) {
                        operationResult(session, false, session.lastErrorCode,
                                session.lastErrorMessage, null);
                        return;
                    }
                }
                long elapsed = System.currentTimeMillis() - session.operationStartedAt;
                try {
                    if (isCodeRequestLoading(session)) session.sawSending = true;
                } catch (Throwable error) {
                    Main.log("quick registration send-state probe failed: " + safe(error));
                }
                if (elapsed >= 20000L) {
                    operationResult(session, false, "verification_send_timeout",
                            "验证码发送超时，请检查网络后重试", null);
                    return;
                }
                MAIN.postDelayed(this, 180L);
            }
        }, 180L);
    }

    private static void markCodeSent(Session session) {
        synchronized (LOCK) {
            if (active != session || !"captcha".equals(session.state)) return;
            session.state = "code_sent";
        }
        JSONObject data = new JSONObject();
        put(data, "sessionId", session.id);
        put(data, "state", "code_sent");
        put(data, "channel", session.channel);
        put(data, "maskedDestination", mask(session.identity, session.channel));
        put(data, "resendAfterSeconds", 60);
        put(data, "message", "验证码已发送至 " + mask(session.identity, session.channel));
        operationResult(session, true, "ok", "验证码已发送", data);
    }

    private static boolean isCodeRequestLoading(Session session) throws Exception {
        Class<?> vmType = session.viewModel.getClass();
        Field serviceField = vmType.getDeclaredField("email".equals(session.channel) ? "f" : "e");
        serviceField.setAccessible(true);
        Object service = serviceField.get(session.viewModel);
        Field stateField = service.getClass().getDeclaredField("d");
        stateField.setAccessible(true);
        Object stateFlow = stateField.get(service);
        Method getValue = stateFlow.getClass().getMethod("getValue");
        Object value = getValue.invoke(stateFlow);
        Class<?> stateType = HostCompat.load(session.activity.getClassLoader(), "i39");
        Field loading = stateType.getDeclaredField("c");
        loading.setAccessible(true);
        return value == loading.get(null);
    }

    private static void dispatchSubmit(Session session, String code) throws Exception {
        ClassLoader cl = session.activity.getClassLoader();
        if ("email".equals(session.channel)) {
            Class<?> vmType = HostCompat.load(cl, "uu7");
            Method update = vmType.getDeclaredMethod("g", HostCompat.load(cl, "pu7"));
            update.setAccessible(true);
            update.invoke(session.viewModel, stringEvent(cl, "ou7", code));
            Field submit = HostCompat.load(cl, "hu7").getDeclaredField("b");
            submit.setAccessible(true);
            Method dispatch = vmType.getDeclaredMethod("f", HostCompat.load(cl, "ju7"));
            dispatch.setAccessible(true);
            dispatch.invoke(session.viewModel, submit.get(null));
        } else {
            Class<?> vmType = HostCompat.load(cl, "ly7");
            Method update = vmType.getDeclaredMethod("f", HostCompat.load(cl, "iy7"));
            update.setAccessible(true);
            update.invoke(session.viewModel, stringEvent(cl, "hy7", code));
            Field submit = HostCompat.load(cl, "cy7").getDeclaredField("b");
            submit.setAccessible(true);
            Method dispatch = vmType.getDeclaredMethod("e", HostCompat.load(cl, "ey7"));
            dispatch.setAccessible(true);
            dispatch.invoke(session.viewModel, submit.get(null));
        }
    }

    private static Session owned(String pluginId, String sessionId) {
        synchronized (LOCK) {
            if (active == null || pluginId == null || sessionId == null) return null;
            if (!pluginId.equals(active.pluginId) || !sessionId.equals(active.id)) return null;
            if (System.currentTimeMillis() - active.operationStartedAt > 10L * 60L * 1000L
                    && "code_sent".equals(active.state)) {
                clearLocked(active);
                return null;
            }
            return active;
        }
    }

    private static void operationResult(Session session, boolean success, String code,
                                        String message, JSONObject data) {
        Callback callback;
        synchronized (LOCK) {
            if (active != session) return;
            callback = session.callback;
            session.callback = null;
        }
        if (callback != null) callback.onResult(success, code, message,
                data == null ? new JSONObject() : data);
    }

    private static void finish(Session session, boolean success, String code,
                               String message, JSONObject data) {
        Callback callback;
        synchronized (LOCK) {
            if (active != session) return;
            callback = session.callback;
            clearLocked(session);
        }
        detachCaptcha(session);
        if (callback != null) callback.onResult(success, code, message,
                data == null ? new JSONObject() : data);
    }

    private static void clearLocked(Session session) {
        session.password = "";
        session.capturePending = false;
        session.state = "done";
        if (session.captchaView != null) CAPTCHA_VIEWS.remove(session.captchaView);
        if (active == session) active = null;
    }

    private static void detachCaptcha(Session session) {
        final Dialog dialog;
        synchronized (LOCK) {
            if (session.captchaView != null) CAPTCHA_VIEWS.remove(session.captchaView);
            session.captchaView = null;
            dialog = session.captchaDialog;
            session.captchaDialog = null;
        }
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
    }

    private static String[] mapError(String name, String fallback) {
        if (name == null) return null;
        if (name.contains("pass_code_error")) return pair("invalid_code", "验证码错误，请重新输入");
        if (name.contains("pass_code_expired") || name.contains("registration_expired")) {
            return pair("expired_code", "验证码已过期，请重新获取");
        }
        if (name.contains("email_exists") || name.contains("account_exist")) {
            return pair("account_exists", "该账号已注册，可直接使用登录添加");
        }
        if (name.contains("password_invalid")) return pair("invalid_password", "密码不符合注册要求");
        if (name.contains("domain_not_supported")) return pair("unsupported_email", "暂不支持该邮箱域名");
        if (name.contains("invalid_phone_number")) return pair("invalid_phone", "手机号格式不正确");
        if (name.contains("mobile_banned") || name.contains("user_is_banned")) {
            return pair("account_banned", "该账号已被限制，无法注册");
        }
        if (name.contains("from_mainland") || name.contains("region")) {
            return pair("region_restricted", fallbackOr(fallback, "当前地区不支持此注册方式"));
        }
        if (name.contains("environment_execption") || name.contains("environment_exception")) {
            return pair("environment_rejected", fallbackOr(fallback, "设备环境未通过服务器校验"));
        }
        if (name.contains("sign_up") || name.contains("verification_failed")) {
            return pair("registration_failed", fallbackOr(fallback, "注册失败，请稍后重试"));
        }
        return null;
    }

    private static String fallbackOr(String value, String fallback) {
        return value == null || value.trim().length() == 0 ? fallback : value;
    }

    private static String[] pair(String code, String message) {
        return new String[]{code, message};
    }

    private static String mask(String identity, String channel) {
        if (identity == null) return "";
        if ("email".equals(channel)) {
            int at = identity.indexOf('@');
            if (at <= 1) return "***" + (at >= 0 ? identity.substring(at) : "");
            return identity.substring(0, Math.min(2, at)) + "***" + identity.substring(at);
        }
        return identity.length() < 7 ? "***" : identity.substring(0, 3) + "****"
                + identity.substring(identity.length() - 4);
    }

    private static int dp(Activity activity, float value) {
        return DeePXUi.dp(activity, value);
    }

    private static String safe(Throwable error) {
        Throwable value = error;
        while (value.getCause() != null && value.getCause() != value) value = value.getCause();
        String message = value.getMessage();
        return value.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    private static void put(JSONObject object, String key, Object value) {
        try { object.put(key, value); } catch (Throwable ignored) {}
    }
}
