package com.smallhairball.deepx;

import com.dsmod.relay.ExpertRelayGate;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.system.Os;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import de.robv.android.xposed.IXposedHookLoadPackage;
import com.smallhairball.deepx.LegacyXposedModule.Chain;
import com.smallhairball.deepx.LegacyXposedModule.Hooker;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/** Shared hook core used by both domestic and Google Play universal APKs. */

/** Hook group extracted from Main.java: ACCTSEC category (see JavaHookGuide). */
final class HookAccountLoginSecurity {
    static final HookAccountLoginSecurity INSTANCE = new HookAccountLoginSecurity();

    static final String RISK_BYPASS_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_risk_bypass";
    /**
     * Experimental identity-swap tier (default OFF). The registration/login APIs validate the
     * device_id header (android_id) and the smid, so replacing them makes the server reject
     * account actions with "environment exception" / "risk device". Only enable deliberately.
     */
    static final String RISK_IDENTITY_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_risk_identity";

    static final String FAKE_MUTE_UNTIL_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_fake_mute_until";

    static final String FAKE_MUTE_ENABLED_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_fake_mute_enabled";

    static volatile long cachedFakeMuteUntil = Long.MIN_VALUE;

    static final String GOOGLE_LOGIN_UNLOCK_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_google_login_unlock";

    static final String WECHAT_MOBILE_LOGIN_UNLOCK_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_wechat_mobile_login_unlock";

    static final String LOCAL_API_ENABLED_FILE =
            "/data/data/com.deepseek.chat/files/dq0_enabled";

    static final String LOCAL_API_SESSION_FILE =
            "/data/data/com.deepseek.chat/files/dq0_sessions.json";

    private static final String PROACTIVE_HEARTBEAT_HISTORY_DIR =
            "/data/data/com.deepseek.chat/files/deekseep_proactive_history";

    public static final ThreadLocal<Boolean> tlProactiveHeartbeatRequest = new ThreadLocal<>();

    private static final String ACTION_AGENT_COMMAND =
            "com.smallhairball.deepx.action.AGENT_COMMAND";

    private static final String EXTRA_AGENT_COMMAND = "agent_command_json";

    private static final String EXTRA_AGENT_COMMAND_BASE64 =
            "agent_command_base64";

    static final ConcurrentHashMap<String, AgentStepResult>
            AGENT_DELAY_STEPS = new ConcurrentHashMap<>();

    // One shared gate keeps completion and maintenance mutually safe.  Conservative serial
    // requests acquire all permits; parallel requests acquire one; session deletion also
    // acquires all.  Sized generously so a multi-account pool (5 accounts = 5 parallel requests)
    // is never throttled by the gate itself.  This remains correct if the user changes the
    // policy while calls live.
    public static final int LOCAL_API_NATIVE_PERMIT_COUNT = 8;

    public static final Semaphore LOCAL_API_NATIVE_PERMITS =
            new Semaphore(LOCAL_API_NATIVE_PERMIT_COUNT, true);

    // Account selection must be request-local. A process-wide value races immediately when the
    // optional parallel lane is enabled and can put request A's token on request B's builder.
    // The interceptor runs synchronously at the beginning of the Ktor call and copies this value
    // to the concrete header builder before the coroutine can resume on another thread.
    public static final ThreadLocal<z12.Route> tlLocalApiAccountRoute =
            new ThreadLocal<>();

    public static final Map<Object, z12.Route> V241_LOCAL_API_ROUTED_HEADER_BUILDERS =
            Collections.synchronizedMap(new WeakHashMap<Object, z12.Route>());

    // g71.t starts code257 attachment uploads in a coroutine after returning to the main loop.
    // Preserve the request route on only that uploader coroutine (rz discriminator 5), including
    // every resume, so synthetic context TXT upload and completion use the same selected account.
    private static final Map<Object, z12.Route> V241_LOCAL_API_ROUTED_UPLOAD_COROUTINES =
            Collections.synchronizedMap(new WeakHashMap<Object, z12.Route>());

    // ly0.a suspends for file PoW before constructing q71's /api/v0/file/upload_file request, so
    // the outer rz continuation alone cannot carry a ThreadLocal route across that boundary.
    // The code257 long-context lease guarantees one module TXT upload at a time; q71 additionally
    // checks the module-owned filename before consulting this exact-upload fallback.
    static volatile z12.Route V241_ACTIVE_ROUTED_CONTEXT_UPLOAD;

    public static final Map<Object, z12.Route> LOCAL_API_ROUTED_NATIVE_REQUESTS =
            Collections.synchronizedMap(new WeakHashMap<Object, z12.Route>());

    public static final ConcurrentHashMap<String, z12.Route> LOCAL_API_ROUTED_POW =
            new ConcurrentHashMap<String, z12.Route>();

    // Last decision of the long-context TXT relay plus lifetime counters, shown on the advanced
    // status page so an upstream "too long" rejection can be traced to its real relay reason.
    static volatile String lastContextRelayStatus = "尚未触发 Not triggered";

    static final AtomicLong contextRelayOk = new AtomicLong();

    static final AtomicLong contextRelayFailed = new AtomicLong();

    static final AtomicLong contextRelayCacheHits = new AtomicLong();

    public static final Object LOCAL_API_SESSION_LOCK = new Object();

    public static final Map<String, String> LOCAL_API_SESSIONS = new HashMap<>();

    public static final Map<String, Long> LOCAL_API_SESSION_LAST_USED = new HashMap<>();

    // Session ids persisted by an older process can be invalid after host/account changes. Keep
    // them hidden from the normal history index, but never reuse them automatically.
    public static final Set<String> LOCAL_API_RETIRED_SESSION_IDS = new HashSet<>();

    // localApiInternalSessionIds now lives on Main (see "Fields moved back" block); read by
    // Compose session-row hooks on the main thread as Main.localApiInternalSessionIds. Publish
    // an immutable snapshot after every mutation so rendering never waits behind session
    // creation, retries, disk I/O or cleanup network calls.

    // Claude Code creates a fresh client UUID for /new and /clear. Bound the hidden branch
    // directory so abandoned conversations cannot accumulate forever in DeepSeek history.
    public static final int LOCAL_API_SESSION_MAX = 32;

    public static final String LOCAL_API_SESSION_META_KEY = "__deekseep_meta";

    public static final String LOCAL_API_SESSION_RETIRED_KEY = "__deekseep_retired";

    // localApiSessionStatePersistedAt and localApiSessionsLoaded now live on Main (see "Fields
    // moved back" block); referenced here as Main.localApiSessionStatePersistedAt /
    // Main.localApiSessionsLoaded.

    private static volatile boolean googleLoginUnlockInjectedLogged = false;

    private static volatile boolean wechatMobileLoginUnlockInjectedLogged = false;

    static volatile long localApiKeepAliveHeartbeatAt;

    static volatile String localApiKeepAliveError = "尚未启动前台保活";

    static volatile boolean localApiKeepAliveRequested;

    private static final Object V236_FREEZER_LOCK_GUARD = new Object();

    private static ParcelFileDescriptor v236FreezerDescriptor;

    private static FileOutputStream v236FreezerStream;

    private static FileChannel v236FreezerChannel;

    private static FileLock v236FreezerLock;

    private static final Object V241_FREEZER_LOCK_GUARD = new Object();

    private static ParcelFileDescriptor v241FreezerDescriptor;

    private static FileOutputStream v241FreezerStream;

    private static FileChannel v241FreezerChannel;

    private static FileLock v241FreezerLock;

    static final ThreadLocal<Boolean> LOCAL_API_COMPANION_CONTROL =
            new ThreadLocal<Boolean>();

    static volatile boolean localApiFloatingWindowRequested;

    static volatile boolean localApiFloatingWindowPermission;

    static void setEnabled(boolean on) {
        try {
            File ef = new File(HookChatPipeline.ENABLED_FILE);
            if (on) Main.overwriteTextFile(HookChatPipeline.ENABLED_FILE, "");
            else ef.delete();
        } catch (Throwable ignored) {}
    }

    /** True when the Shumei SmAntiFraud risk SDK should be fully neutralized. */
    static boolean isRiskBypassEnabled() {
        return new File(RISK_BYPASS_FILE).isFile();
    }
    /** True when synthetic device identities (smid / android_id / OAID) replace the real ones. */
    static boolean isIdentitySwapEnabled() {
        return new File(RISK_IDENTITY_FILE).isFile();
    }

    static long fakeMuteUntilMillis() {
        long cached = cachedFakeMuteUntil;
        if (cached == Long.MIN_VALUE) {
            String value = Main.readSmallText(FAKE_MUTE_UNTIL_FILE);
            try { cached = value == null ? 0L : Long.parseLong(value.trim()); }
            catch (Throwable ignored) { cached = 0L; }
            cachedFakeMuteUntil = cached;
        }
        return Math.max(0L, cached);
    }

    static boolean isFakeMuteEnabled() {
        boolean enabled = new File(FAKE_MUTE_ENABLED_FILE).isFile();
        if (enabled && fakeMuteUntilMillis() <= System.currentTimeMillis()) {
            try { new File(FAKE_MUTE_ENABLED_FILE).delete(); } catch (Throwable ignored) {}
            enabled = false;
        }
        return enabled;
    }

    /** Forces the host privacy switch off and replaces its enable callback with a stable no-op. */
    void hookTrainingOptOutControl(final ClassLoader loader) {
        try {
            Method method = HostCompat.trainingControlMethod(loader);
            if (method == null) {
                Main.log("native training control not found for " + HostCompat.generationName());
                return;
            }
            final Class<?> callbackType = method.getParameterTypes()[1];
            final Object unit = kotlinUnit(loader);
            final Object noOp = callbackType.isInterface()
                    ? Proxy.newProxyInstance(loader, new Class<?>[]{callbackType},
                    new InvocationHandler() {
                        @Override public Object invoke(Object proxy, Method called, Object[] args) {
                            if (called.getDeclaringClass() == Object.class) {
                                if ("toString".equals(called.getName())) {
                                    return "DeePXTrainingOptOutNoOp";
                                }
                                if ("hashCode".equals(called.getName())) {
                                    return System.identityHashCode(proxy);
                                }
                                if ("equals".equals(called.getName())) {
                                    return args != null && args.length == 1 && proxy == args[0];
                                }
                            }
                            return unit;
                        }
                    }) : null;
            Main.MODULE.hook(method).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    if (!Main.isDataOptOutEnforced()) return chain.proceed();
                    Object[] args = chain.getArgs().toArray();
                    args[0] = Boolean.FALSE;
                    if (noOp != null) args[1] = noOp;
                    return chain.proceed(args);
                }
            });
            Main.log("native training opt-out control hooked: " + method);
        } catch (Throwable error) {
            Main.log("hook native training opt-out control failed: " + error);
        }
    }

    private static Object kotlinUnit(ClassLoader loader) {
        try {
            Class<?> type = Class.forName(HostCompat.unitClass(), false, loader);
            Field field = type.getDeclaredField(HostCompat.unitField());
            field.setAccessible(true);
            return field.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Replaces only the native Compose renderer's mute-state argument. Network models and
     * requests remain untouched, so the host draws its own mute bar without sending fake data. */
    void hookNativeFakeMute(ClassLoader loader) {
        String[][] generations = HostCompat.isV241() ? new String[][]{
                {"s69", "y32"},       // Mainland 2.4.1/code257
        } : new String[][]{
                {"wx8", "zd9"},      // Mainland 2.3.6/code249
                {"w19", "zh9"},       // Google Play 2.3.4
                {"qx8", "js8"},       // Mainland 2.3.4
                {"fp8", "h67"},       // 2.3.0
                {"em8", "ux5"},       // Mainland 2.2.x
                {"kq8", "f77"},       // Google Play 2.2.x
        };
        int installed = 0;
        for (String[] generation : generations) {
            try {
                // JADX displays R8's default-package classes under a synthetic
                // "defpackage" directory, but that prefix is not part of the runtime name.
                final Class<?> muteType = loader.loadClass(generation[0]);
                Class<?> renderer = loader.loadClass(generation[1]);
                final Constructor<?> factory = muteType.getDeclaredConstructor(Double.class);
                factory.setAccessible(true);
                for (Method method : renderer.getDeclaredMethods()) {
                    Class<?>[] params = method.getParameterTypes();
                    int found = -1;
                    for (int i = 0; i < params.length; i++) {
                        if (params[i] == muteType) { found = i; break; }
                    }
                    if (found < 0) continue;
                    method.setAccessible(true);
                    final int index = found;
                    Main.MODULE.hook(method).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            if (!isFakeMuteEnabled()) return chain.proceed();
                            Object[] args = chain.getArgs().toArray(new Object[0]);
                            double seconds = fakeMuteUntilMillis() / 1000.0d;
                            args[index] = factory.newInstance(Double.valueOf(seconds));
                            return chain.proceed(args);
                        }
                    });
                    installed++;
                }
                Main.log("native fake mute renderer hooked model=" + muteType.getName()
                        + " owner=" + renderer.getName());
                break;
            } catch (Throwable ignored) {}
        }
        if (installed == 0) Main.log("native fake mute renderer unavailable for host");
    }

    static boolean isGoogleLoginUnlock() {
        return new File(GOOGLE_LOGIN_UNLOCK_FILE).exists();
    }

    static boolean isWechatMobileLoginUnlock() {
        return new File(WECHAT_MOBILE_LOGIN_UNLOCK_FILE).exists();
    }

    static boolean setLocalApiEnabled(boolean on) {
        if (on && !BuildInfo.LOCAL_API_INCLUDED) {
            z13.stop();
            return false;
        }
        Main module = Main.MODULE;
        Activity activity = module == null ? null : module.curAct.get();
        Context grantContext = activity != null ? activity : Main.hostApplicationContext;
        if (on && BuildInfo.PROTECTED_BUILD
                && (HostCompat.isV236() || HostCompat.isV241())
                && !CloudPromptClient.hasLocalApiGrant(grantContext)) {
            Main.log("local API enable rejected: cloud grant unavailable");
            z13.stop();
            return false;
        }
        if (on && !z16.allowSensitiveFeature(activity)) {
            Main.log("local API enable rejected by runtime protection: "
                    + z16.status());
            z13.stop();
            return false;
        }
        try {
            File flag = new File(LOCAL_API_ENABLED_FILE);
            if (on) Main.overwriteTextFile(LOCAL_API_ENABLED_FILE, "");
            else flag.delete();
        } catch (Throwable t) {
            Main.log("local API marker update failed: " + t);
            return false;
        }
        boolean companionControl = Boolean.TRUE.equals(LOCAL_API_COMPANION_CONTROL.get());
        if (on && activity != null) {
            activateOptionalHooksNow();
            if (!companionControl) Main.requestLocalApiKeepAlive(activity, true);
            Main.startz1(activity);
        }
        if (!on) {
            z13.stop();
            if (activity != null && !companionControl) {
                Main.requestLocalApiKeepAlive(activity, false);
            }
            if (module != null) {
                new Thread(new Runnable() {
                    @Override public void run() {
                        Main current = Main.MODULE;
                        if (current != null) current.deleteReusableApiSessions();
                    }
                }, "DeePX-API-Cleanup").start();
            }
        }
        return true;
    }

    static String setLocalApiHttpsEnabled(Context context, boolean enabled) {
        if (context == null) return UiLanguage.text(
                "DeepSeek 上下文尚未就绪", "DeepSeek context is not ready");
        try {
            z5.setEnabled(context, enabled);
            if (z13.isRunning()) z13.stop();
            if (Main.isLocalApiEnabled()) Main.startz1(context);
            return enabled
                    ? UiLanguage.text(context,
                            "HTTPS 已配置并重新监听；安装 CA 后请点击“校验 HTTPS”",
                            "HTTPS configured and restarted; install the CA, then tap Verify HTTPS")
                    : UiLanguage.text(context, "HTTPS 已关闭，本地 API 已恢复 HTTP",
                            "HTTPS disabled; the Local API returned to HTTP");
        } catch (Throwable error) {
            Main.log("local API HTTPS setting failed: " + Main.safeThrowableMessage(error));
            return UiLanguage.text(context, "HTTPS 设置失败：", "HTTPS setup failed: ")
                    + Main.safeThrowableMessage(error);
        }
    }

    static String localApiRuntimeStatus() {
        String status = z13.runtimeStatus();
        status += UiLanguage.text("\n长上下文转文件：", "\nContext relay: ")
                + lastContextRelayStatus
                + UiLanguage.text("（成功 ", " (ok ")
                + contextRelayOk.get()
                + UiLanguage.text("，缓存 ", ", cached ")
                + contextRelayCacheHits.get()
                + UiLanguage.text("，失败 ", ", failed ")
                + contextRelayFailed.get()
                + UiLanguage.text("）", ")");
        if (z16.enabled()) {
            status += UiLanguage.text("\n运行保护：", "\nRuntime protection: ")
                    + z16.status();
        }
        return status;
    }

    /**
     * DeepSeek login mapping:
     *   cy4.b = List&lt;px4&gt;, px4.a = Google, px4.b = SMS/mobile, px4.f = WeChat.
     * dy4 only changes which native items are present for a region; gy4 keeps the real click
     * routes. Hook both the copy method and constructors so interpreted, JIT and inlined state
     * creation paths all converge on the same two-switch policy.
     *
     * Password/email login is handled by a separate hook on v45 / i45 for the login entry page.
     */
    void hookRegionalLoginUnlock(final ClassLoader cl) {
        try {
            final Class<?> stateType = HostCompat.load(cl, "cy4");
            final Class<?> optionType = HostCompat.load(cl, "px4");
            Field googleField = optionType.getDeclaredField("a");
            Field mobileField = optionType.getDeclaredField("b");
            Field wechatField = optionType.getDeclaredField("f");
            googleField.setAccessible(true);
            mobileField.setAccessible(true);
            wechatField.setAccessible(true);
            final Object googleOption = googleField.get(null);
            final Object mobileOption = mobileField.get(null);
            final Object wechatOption = wechatField.get(null);
            int constructors = 0;
            int copies = 0;

            for (Constructor<?> ctor : stateType.getDeclaredConstructors()) {
                final int listIndex = findAssignableParameter(ctor.getParameterTypes(), List.class);
                if (listIndex < 0) continue;
                Main.MODULE.hook(ctor).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        return proceedWithRegionalLoginOptions(chain, listIndex, googleOption,
                                wechatOption, mobileOption, optionType);
                    }
                });
                try { Main.MODULE.deoptimize(ctor); } catch (Throwable t) {
                    Main.log("regional login ctor deopt skipped: " + t);
                }
                constructors++;
            }

            for (Method method : stateType.getDeclaredMethods()) {
                if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())
                        || method.getReturnType() != stateType) continue;
                final int listIndex = findAssignableParameter(method.getParameterTypes(), List.class);
                if (listIndex < 0) continue;
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        return proceedWithRegionalLoginOptions(chain, listIndex, googleOption,
                                wechatOption, mobileOption, optionType);
                    }
                });
                try { Main.MODULE.deoptimize(method); } catch (Throwable t) {
                    Main.log("regional login state-copy deopt skipped: " + t);
                }
                copies++;
            }
            Main.log("hooked native regional login options: cy4 ctors=" + constructors
                    + ", copies=" + copies + ", google=" + isGoogleLoginUnlock()
                    + ", wechatMobile=" + isWechatMobileLoginUnlock());
        } catch (Throwable t) {
            Main.log("hookRegionalLoginUnlock failed: " + t);
        }
    }

    /**
     * Injects the password/email login option into the login entry page by hooking the
     * v45 (login entry state) constructors. v45.b is the List&lt;i45&gt; of visible login
     * options; we ensure i45.d (password/email login) is always present when Google login
     * unlock is enabled.
     */
    void hookLoginEntryPasswordUnlock(final ClassLoader cl) {
        try {
            final Class<?> stateType = HostCompat.load(cl, "n45");
            final Class<?> optionType = HostCompat.load(cl, "a45");
            Field passwordField = optionType.getDeclaredField("d");
            passwordField.setAccessible(true);
            final Object passwordOption = passwordField.get(null);
            int hooked = 0;
            for (Constructor<?> ctor : stateType.getDeclaredConstructors()) {
                final int listIndex = findAssignableParameter(ctor.getParameterTypes(), List.class);
                if (listIndex < 0) continue;
                Main.MODULE.hook(ctor).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object[] args = chain.getArgs().toArray();
                        List<?> list = args[listIndex] instanceof List
                                ? (List<?>) args[listIndex] : null;
                        if (list != null && !list.isEmpty() && !list.contains(passwordOption)) {
                            if (isGoogleLoginUnlock()) {
                                List<Object> extended = new ArrayList<Object>(list);
                                extended.add(passwordOption);
                                args[listIndex] = extended;
                                return chain.proceed(args);
                            }
                        }
                        return chain.proceed();
                    }
                });
                try { Main.MODULE.deoptimize(ctor); } catch (Throwable t) {
                    Main.log("login entry password ctor deopt skipped: " + t);
                }
                hooked++;
            }
            Main.log("hooked native login entry password unlock: v45 ctors=" + hooked
                    + ", google=" + isGoogleLoginUnlock());
        } catch (Throwable t) {
            Main.log("hookLoginEntryPasswordUnlock failed: " + t);
        }
    }

    interface CandidateLoginCallback {
        void onResult(boolean success, String message);
    }

    static final Object CANDIDATE_LOGIN_LOCK = new Object();

    static volatile CandidateLoginCallback candidateLoginCallback;

    static volatile String candidateLoginOriginalJson;

    static volatile long candidateLoginStartedAt;

    private static final ThreadLocal<Boolean> CANDIDATE_LOGIN_RESTORING =
            new ThreadLocal<Boolean>();

    static void clearCandidateLoginLocked() {
        candidateLoginCallback = null;
        candidateLoginOriginalJson = null;
        candidateLoginStartedAt = 0L;
    }

    private static void failCandidateLogin(String message) {
        final CandidateLoginCallback callback;
        synchronized (CANDIDATE_LOGIN_LOCK) {
            callback = candidateLoginCallback;
            if (callback == null) return;
            clearCandidateLoginLocked();
        }
        callback.onResult(false, message == null ? "登录失败，请稍后重试" : message);
    }

    /** Converts the host's localized password-login error resources into inline candidate errors. */
    void hookCandidateLoginErrorMessages() {
        try {
            Method getString = android.content.res.Resources.class
                    .getDeclaredMethod("getString", int.class);
            Main.MODULE.hook(getString).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    Object result = chain.proceed();
                    if (candidateLoginCallback == null
                            && !AccountRegistrationBridge.hasActiveSession()) return result;
                    int id = ((Number) chain.getArg(0)).intValue();
                    String name;
                    try {
                        name = ((android.content.res.Resources) chain.getThisObject())
                                .getResourceEntryName(id);
                    } catch (Throwable ignored) { return result; }
                    String message = null;
                    if ("forgot_password_invalid_password_toast".equals(name)
                            || "sign_in_password_email_error_toast".equals(name)) {
                        message = "密码错误，请重新输入";
                    } else if ("forgot_password_failed_email_not_exist_toast2".equals(name)
                            || "forgot_password_failed_mobile_not_exist_toast2".equals(name)) {
                        message = "找不到该账号，请检查邮箱或手机号";
                    } else if ("user_is_banned_toast".equals(name)) {
                        message = "该账号已被封禁，无法添加";
                    } else if ("sign_in_only_from_mainland_toast".equals(name)) {
                        message = "当前账号不允许从此地区登录";
                    } else if ("auth_pass_code_error_toast".equals(name)
                            || "auth_pass_code_expired_toast".equals(name)) {
                        message = "登录需要验证码，请使用原生登录方式完成验证";
                    } else if ("exection_environment_execption_toast".equals(name)
                            || "sign_in_failed_toast".equals(name)) {
                        message = result instanceof String && ((String) result).length() > 0
                                ? (String) result : "登录失败，请检查网络后重试";
                    }
                    if (message != null && candidateLoginCallback != null) {
                        failCandidateLogin(message);
                    }
                    AccountRegistrationBridge.hostError(name,
                            result instanceof String ? (String) result : "");
                    return result;
                }
            });
            Main.log("candidate password-login error mapper hooked");
        } catch (Throwable error) {
            Main.log("candidate password-login error mapper unavailable: "
                    + Main.safeThrowableMessage(error));
        }
    }

    /** Captures a successful native login as a slot, then restores the live account object. */
    void hookCandidatePasswordLoginCapture(final ClassLoader cl) {
        try {
            final Class<?> manager = HostCompat.load(cl, "j5");
            final Class<?> user = HostCompat.load(cl, "tw");
            final Method apply = manager.getDeclaredMethod("h", user);
            apply.setAccessible(true);
            Main.MODULE.hook(apply).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    boolean registrationCapture =
                            AccountRegistrationBridge.hasCredentialCapturePending();
                    if (Boolean.TRUE.equals(CANDIDATE_LOGIN_RESTORING.get())
                            || (candidateLoginCallback == null && !registrationCapture)) {
                        return chain.proceed();
                    }
                    Object managerObject = chain.getThisObject();
                    Method current = manager.getDeclaredMethod("b");
                    current.setAccessible(true);
                    Object originalUser = current.invoke(managerObject);
                    Object result = chain.proceed();
                    String candidateJson = AccountManager.readCurrentJson(cl);
                    String originalJson = registrationCapture
                            ? AccountRegistrationBridge.credentialCaptureOriginalJson()
                            : candidateLoginOriginalJson;
                    boolean saved = candidateJson != null
                            && !candidateJson.equals(originalJson)
                            && AccountManager.upsertSlot(candidateJson);
                    try {
                        CANDIDATE_LOGIN_RESTORING.set(Boolean.TRUE);
                        if (originalUser != null) apply.invoke(managerObject, originalUser);
                        else if (originalJson != null) {
                            AccountManager.writeCurrentJson(cl, originalJson);
                        }
                    } finally {
                        CANDIDATE_LOGIN_RESTORING.remove();
                    }
                    if (registrationCapture) {
                        AccountRegistrationBridge.credentialCaptured(saved);
                    } else {
                        final CandidateLoginCallback callback;
                        synchronized (CANDIDATE_LOGIN_LOCK) {
                            callback = candidateLoginCallback;
                            clearCandidateLoginLocked();
                        }
                        if (callback != null) callback.onResult(saved, saved
                                ? "账号已添加，当前登录账号保持不变"
                                : "登录成功，但候选账号凭证保存失败");
                    }
                    return result;
                }
            });
            Main.log("hooked candidate password login capture: j5.h");
        } catch (Throwable t) {
            Main.log("hookCandidatePasswordLoginCapture failed: " + Main.safeThrowableMessage(t));
        }
    }

    /** Connects the official ShuMei WebView result to the opaque plugin registration session. */
    void hookCandidateRegistrationCaptchaCallbacks(final ClassLoader cl) {
        try {
            Class<?> captcha = cl.loadClass("com.ishumei.sdk.captcha.SmCaptchaWebView");
            int hooked = 0;
            for (Method method : captcha.getDeclaredMethods()) {
                final String name = method.getName();
                if (!("notifySuccess".equals(name) || "notifyClose".equals(name)
                        || "notifyWebLoadError".equals(name))) continue;
                method.setAccessible(true);
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object view = chain.getThisObject();
                        Object result = chain.proceed();
                        if (!AccountRegistrationBridge.ownsCaptcha(view)) return result;
                        if ("notifySuccess".equals(name)) {
                            String rid = "";
                            boolean pass = false;
                            if (chain.getArgs().size() == 1
                                    && chain.getArg(0) instanceof JSONObject) {
                                JSONObject data = (JSONObject) chain.getArg(0);
                                rid = data.optString("rid", "");
                                pass = data.optBoolean("pass", false);
                            } else if (chain.getArgs().size() == 2) {
                                rid = String.valueOf(chain.getArg(0));
                                pass = Boolean.TRUE.equals(chain.getArg(1));
                            }
                            AccountRegistrationBridge.captchaSuccess(view, rid, pass);
                        } else if ("notifyClose".equals(name)) {
                            AccountRegistrationBridge.captchaClosed(view);
                        } else {
                            String detail = "安全验证加载失败";
                            if (!chain.getArgs().isEmpty()) {
                                detail += "（" + String.valueOf(chain.getArg(0)) + "）";
                            }
                            AccountRegistrationBridge.captchaError(view, detail);
                        }
                        return result;
                    }
                });
                hooked++;
            }
            Class<?> analytics = HostCompat.load(cl, "c3a");
            Method verificationResult = HostCompat.isV241()
                    ? analytics.getDeclaredMethod("s", String.class, boolean.class,
                            Integer.class, String.class, int.class)
                    : analytics.getDeclaredMethod("r", String.class, boolean.class);
            verificationResult.setAccessible(true);
            Main.MODULE.hook(verificationResult).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    Object result = chain.proceed();
                    AccountRegistrationBridge.codeRequestResult(
                            String.valueOf(chain.getArg(0)),
                            Boolean.TRUE.equals(chain.getArg(1)));
                    return result;
                }
            });
            hooked++;
            Main.log("hooked quick-registration captcha callbacks=" + hooked);
        } catch (Throwable error) {
            Main.log("quick-registration captcha callbacks unavailable: "
                    + Main.safeThrowableMessage(error));
        }
    }

    /**
     * Overrides the domestic DeepSeek region detection so the app sends requests to the global
     * API instead of the domestic API.  The global registration endpoint has less strict
     * Shumei risk checks, making email registration possible.
     *
     * <p>uy8 / vy8 / wy8 implement xy8.  The domestic build uses uy8:
     *   a() = "CN" (region code)  → keep as-is (changing it breaks login page loading)
     *   b() = true  (non-global)  → keep as-is
     *   c() = false (same as global)
     *   d() = true  (phone format) → override to false (email format)
     * </p>
     */
    void hookRegionOverride(final ClassLoader cl) {
        // Code257 keeps the same four-method region contract under a separately obfuscated
        // implementation family.  Do not let 2.4.1 fall through to the working 2.3.x names.
        String[] targets = HostCompat.isV241()
                ? new String[]{"p79", "q79", "r79"}
                : new String[]{"uy8", "vy8", "wy8"};
        for (String name : targets) {
            try {
                Class<?> cls = cl.loadClass(name);
                // d() -> password_login / phone format: force false (email format)
                try {
                    Method d = cls.getDeclaredMethod("d");
                    Main.MODULE.hook(d).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            return Boolean.FALSE;
                        }
                    });
                    Main.log("region override: " + name + ".d() -> false");
                } catch (Throwable ignored) {}
                Main.log("region override installed: " + name);
                return;
            } catch (Throwable ignored) {}
        }
        Main.log("hookRegionOverride: no matching region class found");
    }

    /**
     * Neutralizes the bundled Shumei SmAntiFraud risk SDK so it can neither mark the real device
     * nor report the Xposed/LSPosed environment to the fengkongcloud risk cloud:
     * <ul>
     * <li>{@code getDeviceId()} returns a module-persisted synthetic smid, so the real device
     *     fingerprint never reaches the cloud and cannot be blacklisted.</li>
     * <li>The collection trigger delivers that synthetic smid to the app's callback without
     *     starting collection or upload.</li>
     * <li>{@code startDetector}/{@code stopDetector} are no-ops, so no environment detector
     *     (including the Xposed probe) ever runs.</li>
     * <li>The Xposed probe's static checks are additionally forced to report a clean result.</li>
     * <li>The tracker uploader and the remote-config fetcher never send anything.</li>
     * </ul>
     */
    void installRiskSdkNeutralizer(final ClassLoader cl) {
        if (cl == null) return;
        // FULL (opt-in marker file): replaces device identities in addition to detector results.
        // PARTIAL (default): keeps real identities so the server-side device_id/smid validation
        // used by the registration API keeps passing, and only forges detector results and the
        // root/xposed flags inside collected payloads.
        final boolean fullBypass = isRiskBypassEnabled();
        final boolean identitySwap = isIdentitySwapEnabled();
        Main.log("[RISK] profile=" + (fullBypass ? "NEW-DEVICE" : "PARTIAL default")
                + ", identity-swap=" + (identitySwap ? "ON" : "off"));
        if (fullBypass) {
            try { wipeShumeiIdentityOnce(); }
            catch (Throwable t) { Main.log("[RISK] new-device wipe failed: "
                    + Main.safeThrowableMessage(t)); }
        }
        try {
            Class<?> sm = Class.forName("com.ishumei.smantifraud.SmAntiFraud", false, cl);
            for (final Method method : sm.getDeclaredMethods()) {
                if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) continue;
                String name = method.getName();
                Class<?>[] params = method.getParameterTypes();
                if (identitySwap && "getDeviceId".equals(name) && params.length == 0
                        && method.getReturnType() == String.class) {
                    Main.MODULE.hook(method).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            String smid = riskNeutralizedSmid();
                            Main.log("[RISK] getDeviceId -> synthetic smid len=" + smid.length());
                            return smid;
                        }
                    });
                    try { Main.MODULE.deoptimize(method); } catch (Throwable ignored) {}
                } else if (identitySwap && "getVData".equals(name) && params.length == 0
                        && method.getReturnType() == String.class) {
                    Main.MODULE.hook(method).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            return riskNeutralizedSmid();
                        }
                    });
                    try { Main.MODULE.deoptimize(method); } catch (Throwable ignored) {}
                } else if (identitySwap && ("startDetector".equals(name) || "stopDetector".equals(name))
                        && params.length == 1) {
                    Main.MODULE.hook(method).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            return null;
                        }
                    });
                    try { Main.MODULE.deoptimize(method); } catch (Throwable ignored) {}
                }
            }
            // Forge instead of blocking: the SDK still collects, encrypts and uploads normally,
            // but every detector returns "clean" and the box JSON carries the synthetic smid, so
            // the risk cloud sees a fresh, root-free device instead of a vanished upload.
            try { installSmsdkForge(cl, identitySwap); }
            catch (Throwable t) { Main.log("[RISK] SMSDK forge failed: " + Main.safeThrowableMessage(t)); }
            // The SDK also persists its real device id in a private store. The login header
            // builder can read that store directly (not only through getDeviceId), so force the
            // two known store getters to return the synthetic id as well.
            if (identitySwap) try {
                // NOTE: the class name is easy to typo — the real 2.5.3 name is l111l11I1IIIl
                // (three 1s, then l11). A wrong literal used to fail silently, which is exactly
                // why the smid store getters were never neutralized in the field.
                Class<?> store = Class.forName(
                        "com.ishumei.smantifraud.l111l11I1IIIl", false, cl);
                int storeGetters = 0;
                for (final Method method : store.getDeclaredMethods()) {
                    if (method.getReturnType() != String.class
                            || method.getParameterTypes().length != 0
                            || java.lang.reflect.Modifier.isStatic(method.getModifiers())) continue;
                    String name = method.getName();
                    if (!"l111l11111Il".equals(name)
                            && !"l111l11111I1l".equals(name)
                            && !"l111l1111lI1l".equals(name)
                            && !"l111l1111lIl".equals(name)
                            && !"l111l1111llIl".equals(name)
                            && !"l1111l111111Il".equals(name)) continue;
                    Main.MODULE.hook(method).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            Main.log("[RISK] smid store getter neutralized");
                            return riskNeutralizedSmid();
                        }
                    });
                    try { Main.MODULE.deoptimize(method); } catch (Throwable ignored) {}
                    storeGetters++;
                }
                Main.log("[RISK] smid store getters hooked=" + storeGetters);
            } catch (Throwable t) {
                Main.log("[RISK] smid store hook failed: " + Main.safeThrowableMessage(t));
            }
            try {
                Class<?> detector = Class.forName(
                        "com.ishumei.smantifraud.l1111l111111Il", false, cl);
                for (final Method method : detector.getDeclaredMethods()) {
                    if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) continue;
                    final Class<?> rt = method.getReturnType();
                    if (rt == boolean.class) {
                        Main.MODULE.hook(method).intercept(new Hooker() {
                            @Override public Object intercept(Chain chain) throws Throwable {
                                return Boolean.FALSE;
                            }
                        });
                        try { Main.MODULE.deoptimize(method); } catch (Throwable ignored) {}
                    } else if (rt == String.class) {
                        Main.MODULE.hook(method).intercept(new Hooker() {
                            @Override public Object intercept(Chain chain) throws Throwable {
                                return "";
                            }
                        });
                        try { Main.MODULE.deoptimize(method); } catch (Throwable ignored) {}
                    } else if (rt == int.class) {
                        Main.MODULE.hook(method).intercept(new Hooker() {
                            @Override public Object intercept(Chain chain) throws Throwable {
                                return Integer.valueOf(0);
                            }
                        });
                        try { Main.MODULE.deoptimize(method); } catch (Throwable ignored) {}
                    } else if (rt == long.class) {
                        Main.MODULE.hook(method).intercept(new Hooker() {
                            @Override public Object intercept(Chain chain) throws Throwable {
                                return Long.valueOf(0L);
                            }
                        });
                        try { Main.MODULE.deoptimize(method); } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable t) {
                Main.log("[RISK] detector class hook failed: " + Main.safeThrowableMessage(t));
            }
            Main.log("[RISK] SmAntiFraud neutralizer installed");
            // FULL mode only: DeepSeek reads Settings.Secure.getString("android_id") in
            // bm2.b() and ki9.<init> to build the device_id header that the register API
            // validates, so replacing it (or the smid) makes the server reject accounts as
            // "environment exception". PARTIAL keeps the real identity and only cleans output.
            if (identitySwap || fullBypass) try {
                hookAndroidIdSpoof(cl);
            } catch (Throwable t) {
                Main.log("[RISK] Android ID spoof failed: " + Main.safeThrowableMessage(t));
            }
        } catch (Throwable t) {
            Main.log("[RISK] SmAntiFraud neutralizer unavailable: " + Main.safeThrowableMessage(t));
        }
    }

    /**
     * Forges the Shumei SMSDK so the collected fingerprint is clean while the SDK still encrypts
     * and uploads it normally (a vanished upload is itself a risk signal). Reversing libsmsdk.so
     * (AES-256-CBC IV "0102030405060708" + zlib + base64, OLLVM-obfuscated) is unnecessary: we
     * only rewrite the plaintext that flows into the native encryptor.
     */
    /**
     * New-device mode: erase Shumei persisted identity stores once, so its SDK
     * re-creates a brand-new, cloud-registered device id (fresh smid) instead of
     * reusing a flagged one. Runs once per activation (marker-guarded).
     */
    private static void wipeShumeiIdentityOnce() {
        java.io.File done = new java.io.File(
                "/data/data/com.deepseek.chat/files/deekseep_new_device_done");
        if (done.isFile()) return;
        wipeShumeiIdentity(true);
    }
    /** One-tap reset ("转世"): wipe the Shumei identity now, then end the host. */
    static void resetDeviceIdentity() {
        Main.log("[RISK] reset requested in process=" + currentProcessName());
        try { wipeShumeiIdentity(true); }
        catch (Throwable t) { Main.log("[RISK] reset wipe failed: "
                + Main.safeThrowableMessage(t)); }
        if ("com.deepseek.chat".equals(currentProcessName())) {
            try {
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                        new Runnable() {
                            @Override public void run() {
                                android.os.Process.killProcess(android.os.Process.myPid());
                            }
                        }, 600);
            } catch (Throwable ignored) {}
        } else {
            try {
                Process p = AgentDeviceBridge.startRootProcess(
                        "am force-stop com.deepseek.chat");
                if (p != null) p.waitFor();
            } catch (Throwable t) { Main.log("[RISK] host stop failed: "
                    + Main.safeThrowableMessage(t)); }
        }
    }
    static String currentProcessName() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                return android.app.Application.getProcessName();
            }
        } catch (Throwable ignored) {}
        try {
            java.io.FileInputStream in =
                    new java.io.FileInputStream("/proc/self/cmdline");
            byte[] buffer = new byte[128];
            int count = in.read(buffer);
            in.close();
            if (count > 0) {
                int end = 0;
                while (end < count && buffer[end] != 0) end++;
                return new String(buffer, 0, end, "UTF-8");
            }
        } catch (Throwable ignored) {}
        return "";
    }
    private static void wipeShumeiIdentity(boolean writeDone) {
        android.content.Context context = currentApplication();
        String pkg = context != null ? context.getPackageName() : "com.deepseek.chat";
        String org = null;
        try {
            android.content.SharedPreferences bd = context.getSharedPreferences("bd", 0);
            String bdk = bd.getString("bdk", null);
            if (bdk != null) org = new org.json.JSONObject(bdk).optString("organization", null);
        } catch (Throwable ignored) {}
        String[] plain = new String[]{"auc", "bd", "kd"};
        java.util.ArrayList<String> hex = new java.util.ArrayList<String>();
        if (org != null && org.length() > 0) hex.add(md5Hex(org + "_" + pkg));
        hex.add(md5Hex(pkg + "_shumei"));
        hex.add(md5Hex(pkg + "_android"));
        int cleared = 0;
        int deleted = 0;
        if (context != null) {
            for (String n : plain) {
                try { context.getSharedPreferences(n, 0).edit().clear().commit(); cleared++; }
                catch (Throwable ignored) {}
            }
            for (String n : hex) {
                try { context.getSharedPreferences(n, 0).edit().clear().commit(); cleared++; }
                catch (Throwable ignored) {}
            }
        }
        String base = "/data/data/" + pkg;
        for (String n : plain) deleted += deleteIfExists(base + "/shared_prefs/" + n + ".xml");
        for (String n : hex) deleted += deleteIfExists(base + "/shared_prefs/" + n + ".xml");
        deleted += deleteIfExists(base + "/files/Pers/FlyVerify_SPDB_V2_1");
        deleted += deleteIfExists(base + "/files/Pers/FlyVerify_LOG_1");
        deleted += deleteIfExists(base + "/files/Pers/Fly_Pure_Cache_1");
        deleted += deleteIfExists(base + "/files/Pers/dhp_1");
        deleted += deleteIfExists(base + "/files/Pers/fvv_cms_1");
        deleted += deleteIfExists(base + "/files/fvv/.opdn");
        deleted += deleteIfExists(base + "/files/fvv/.ppd1");
        deleted += deleteIfExists(base + "/files/deekseep_android_id");
        if (writeDone) {
            try { Main.writeText(base + "/files/deekseep_new_device_done", "1"); }
            catch (Throwable ignored) {}
        }
        Main.log("[RISK] new-device wipe done: cleared=" + cleared
                + " deleted=" + deleted + " org=" + org);
    }
    private static int deleteIfExists(String path) {
        try { java.io.File f = new java.io.File(path); return f.isFile() && f.delete() ? 1 : 0; }
        catch (Throwable t) { return 0; }
    }
    private static String md5Hex(String s) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("MD5")
                    .digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Throwable t) { return ""; }
    }
    private static android.content.Context currentApplication() {
        try {
            return (android.content.Context) Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
        } catch (Throwable t) { return null; }
    }
    private void installSmsdkForge(final ClassLoader cl, final boolean replaceIdentities) {
        try {
            // true = initialize: run SMSDK's static block so it System.loadLibrary("smsdk")
            // BEFORE we inline-patch sub_4A48C. Without initialization the target is not mapped.
            Class<?> smsdk = Class.forName("com.ishumei.smantifraud.dfp.SMSDK", true, cl);
            // Native-layer root detector: patch libsmsdk.so sub_4A48C to return 0. This runs
            // after SMSDK's static block has dlopen'd libsmsdk.so, so the target is in memory.
            try {
                z22.initialize();
                boolean patched = z22.patchRootDetector();
                Main.log("[RISK] sub_4A48C native patch -> " + (patched ? "0 (clean)" : "unavailable"));
            } catch (Throwable t) {
                Main.log("[RISK] sub_4A48C native patch failed: " + Main.safeThrowableMessage(t));
            }
            // ma() -> boolean root/magisk/KSU detector (sub_4A48C). Force clean.
            for (final Method method : smsdk.getDeclaredMethods()) {
                String name = method.getName();
                if ("ma".equals(name) && method.getParameterTypes().length == 0
                        && method.getReturnType() == boolean.class) {
                    Main.MODULE.hook(method).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            Main.log("[RISK] SMSDK.ma() root detector -> false");
                            return Boolean.FALSE;
                        }
                    });
                    try { Main.MODULE.deoptimize(method); } catch (Throwable ignored) {}
                } else if ("ma2".equals(name) && method.getParameterTypes().length == 0
                        && method.getReturnType() == int.class) {
                    Main.MODULE.hook(method).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            Main.log("[RISK] SMSDK.ma2() risk score -> 0");
                            return Integer.valueOf(0);
                        }
                    });
                    try { Main.MODULE.deoptimize(method); } catch (Throwable ignored) {}
                }
            }
            // v3(Context, String json, String publicKey, String org, String appId): rewrite the
            // plaintext box JSON (smid/wevent) before the native w3 encrypts it.
            for (final Method method : smsdk.getDeclaredMethods()) {
                if (!"v3".equals(method.getName())
                        || !java.lang.reflect.Modifier.isStatic(method.getModifiers())
                        || method.getParameterTypes().length != 5) continue;
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object raw = chain.getArg(1);
                        String forged = forgeSmsdkBoxJson(raw, replaceIdentities);
                        if (forged != null) {
                            Object[] args = new Object[5];
                            args[0] = chain.getArg(0);
                            args[1] = forged;
                            args[2] = chain.getArg(2);
                            args[3] = chain.getArg(3);
                            args[4] = chain.getArg(4);
                            Main.log("[RISK] SMSDK.v3() box JSON forged");
                            return chain.proceed(args);
                        }
                        return chain.proceed();
                    }
                });
                try { Main.MODULE.deoptimize(method); } catch (Throwable ignored) {}
            }
            Main.log("[RISK] SMSDK forge installed");
        } catch (Throwable t) {
            Main.log("[RISK] SMSDK forge unavailable: " + Main.safeThrowableMessage(t));
        }
    }

    /** Rewrites the box JSON: root/xposed flags are always nulled; smid becomes synthetic in full mode only. */
    private static String forgeSmsdkBoxJson(Object raw, boolean replaceIdentities) {
        if (!(raw instanceof String)) return null;
        String json = ((String) raw).trim();
        if (json.length() == 0 || !json.startsWith("{")) return null;
        try {
            org.json.JSONObject root = new org.json.JSONObject(json);
            // smid appears under "smid"; root/xposed flags are always neutralized, while
            // the smid swap itself only happens in full mode.
            if (replaceIdentities && root.has("smid")) root.put("smid", riskNeutralizedSmid());
            for (String key : new String[]{"hookJava", "xpApp", "root", "isRoot", "magisk"}) {
                if (root.has(key)) root.put(key, JSONObject.NULL);
            }
            return root.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Returns a synthetic android_id that is stable for this device.
     * The server expects a 16-character hex string.
     */
    private static String syntheticAndroidId() {
        String value = syntheticAndroidId;
        if (value != null) return value;
        synchronized (Main.class) {
            value = syntheticAndroidId;
            if (value != null) return value;
            try {
                value = Main.readSmallText(
                        "/data/data/com.deepseek.chat/files/deekseep_android_id");
                if (value == null || value.length() != 16) {
                    byte[] random = new byte[8];
                    new java.security.SecureRandom().nextBytes(random);
                    StringBuilder hex = new StringBuilder(16);
                    for (byte b : random) {
                        hex.append(Character.forDigit((b >> 4) & 0xf, 16))
                                .append(Character.forDigit(b & 0xf, 16));
                    }
                    value = hex.toString();
                    Main.writeText("/data/data/com.deepseek.chat/files/deekseep_android_id", value);
                }
                syntheticAndroidId = value;
            } catch (Throwable t) {
                value = "a1b2c3d4e5f6a7b8";
                syntheticAndroidId = value;
            }
            return value;
        }
    }

    private static volatile String syntheticAndroidId;

    /**
     * Hooks Settings.Secure.getString("android_id") to return a synthetic device id.
     * This is the primary device fingerprint that DeepSeek sends to the server for
     * registration risk checks.
     */
    private void hookAndroidIdSpoof(ClassLoader cl) {
        try {
            Class<?> settingsSecure = android.provider.Settings.Secure.class;
            Method getString = settingsSecure.getDeclaredMethod(
                    "getString", android.content.ContentResolver.class, String.class);
            Main.MODULE.hook(getString).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    Object key = chain.getArg(1);
                    if ("android_id".equals(key)) {
                        return syntheticAndroidId();
                    }
                    return chain.proceed();
                }
            });
Main.log("[RISK] Android ID spoof installed");
            // Hook SharedPreferences.getString so OAID (openudid / clientudid) never
            // leaks the real device identifier to the server.
            try {
                hookOaidSpoof(cl);
            } catch (Throwable t) {
                Main.log("[RISK] OAID spoof failed: " + Main.safeThrowableMessage(t));
            }
        } catch (Throwable t) {
            Main.log("[RISK] SmAntiFraud neutralizer unavailable: " + Main.safeThrowableMessage(t));
        }
    }

    private static final String[] OAID_KEYS = new String[]{
            "openudid", "clientudid", "deviceid", "android_id"
    };

    private void hookOaidSpoof(ClassLoader cl) {
        if (HostCompat.isV241()) {
            hookConcreteOaidPreferencesV241(cl);
            return;
        }
        // Hook SharedPreferences.getString to intercept OAID reads
        try {
            Class<?> sp = Class.forName("android.content.SharedPreferences");
            Method getString = sp.getDeclaredMethod("getString", String.class, String.class);
            Main.MODULE.hook(getString).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    Object key = chain.getArg(0);
                    for (String oaidKey : OAID_KEYS) {
                        if (oaidKey.equals(key)) {
                            return syntheticAndroidId();
                        }
                    }
                    return chain.proceed();
                }
            });
            Main.log("[RISK] OAID spoof installed (SharedPreferences.getString)");
        } catch (Throwable t) {
            Main.log("[RISK] OAID SharedPreferences hook failed: " + Main.safeThrowableMessage(t));
        }
    }

    /** code257 adapter: hook concrete SharedPreferences implementations, never the interface. */
    private void hookConcreteOaidPreferencesV241(ClassLoader cl) {
        int installed = 0;
        String[] owners = new String[]{
                "com.tencent.mmkv.MMKV",
                "android.app.SharedPreferencesImpl"
        };
        for (String ownerName : owners) {
            try {
                Class<?> owner = "android.app.SharedPreferencesImpl".equals(ownerName)
                        ? Class.forName(ownerName)
                        : Class.forName(ownerName, false, cl);
                final Method getString = owner.getDeclaredMethod(
                        "getString", String.class, String.class);
                getString.setAccessible(true);
                Main.MODULE.hook(getString).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object key = chain.getArg(0);
                        for (String oaidKey : OAID_KEYS) {
                            if (oaidKey.equals(key)) return syntheticAndroidId();
                        }
                        return chain.proceed();
                    }
                });
                installed++;
            } catch (Throwable error) {
                Main.log("[RISK] code257 OAID owner unavailable " + ownerName + ": "
                        + Main.safeThrowableMessage(error));
            }
        }
        Main.log("[RISK] code257 OAID concrete hooks=" + installed);
    }

    /** Swallows every void instance method of an SDK IO class (tracker sender / remote config). */
    private void neutralizeRiskIoClass(final ClassLoader cl, String className) {
        try {
            Class<?> type = Class.forName(className, false, cl);
            for (final Method method : type.getDeclaredMethods()) {
                if (method.getReturnType() != void.class
                        || java.lang.reflect.Modifier.isStatic(method.getModifiers())
                        || method.getParameterTypes().length < 1) continue;
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        return null;
                    }
                });
                try { Main.MODULE.deoptimize(method); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Synthetic, per-install stable device id so the risk cloud never associates this device's
     * real fingerprint with anything. Stored in DeepSeek's own private files.
     */
    private static volatile String riskNeutralizedSmid;

    private static String riskNeutralizedSmid() {
        String value = riskNeutralizedSmid;
        if (value != null && value.length() >= 16) return value;
        synchronized (Main.class) {
            value = riskNeutralizedSmid;
            if (value != null && value.length() >= 16) return value;
            try {
                value = Main.readSmallText(
                        "/data/data/com.deepseek.chat/files/deekseep_risk_smid");
                if (value == null || value.length() < 16) {
                    byte[] random = new byte[16];
                    new java.security.SecureRandom().nextBytes(random);
                    StringBuilder hex = new StringBuilder(33);
                    for (byte b : random) {
                        hex.append(Character.forDigit((b >> 4) & 0xf, 16))
                                .append(Character.forDigit(b & 0xf, 16));
                    }
                    value = "D" + hex.toString();
                    Main.writeText("/data/data/com.deepseek.chat/files/deekseep_risk_smid", value);
                }
                riskNeutralizedSmid = value;
            } catch (Throwable t) {
                value = "D" + sha256Hex(String.valueOf(System.nanoTime()));
                riskNeutralizedSmid = value;
            }
            return value;
        }
    }

    /**
     * Login risk diagnostics: records the raw server biz_code when the login/sms-login response
     * is deserialized, and records the risk-toast selection, WITHOUT capturing tokens, phone
     * numbers or any other request/response content. Class names cover 2.3.6 (ox4/ix4) and
     * 2.3.4 (f05/iw5) hosts; every candidate is optional.
     */
    void installLoginRiskLogger(final ClassLoader cl) {
        if (cl == null) return;
        installBizCodeLogger(cl, new String[]{"ox4", "f05"}, "sms");
        installBizCodeLogger(cl, new String[]{"ix4", "iw5"}, "login");
        installBizCodeLogger(cl, new String[]{"kt5", "cw5"}, "google");
        installBizCodeLogger(cl, new String[]{"qt5"}, "wechat");
        installBizCodeLogger(cl, new String[]{"mx5", "f06"}, "onetap");
        installBizCodeLogger(cl, new String[]{"g14", "zz4"}, "toast");
        installBizCodeLogger(cl, new String[]{"qx4"}, "google-toast");
        installBizCodeLogger(cl, new String[]{"sn5"}, "google-toast2");
        try {
            Class<?> flow = Class.forName("gy4", false, cl);
            for (final Method method : flow.getDeclaredMethods()) {
                if (!"f".equals(method.getName())
                        || !java.lang.reflect.Modifier.isStatic(method.getModifiers())
                        || method.getParameterTypes().length != 3) continue;
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        logGoogleLoginResult(chain.getArg(1));
                        return chain.proceed();
                    }
                });
                try { Main.MODULE.deoptimize(method); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        // GMS sign-in failure status (the Google-side step before DeepSeek's oauth endpoint).
        try {
            Class<?> pending = Class.forName("eu9", false, cl);
            for (final Method method : pending.getDeclaredMethods()) {
                if (!"g".equals(method.getName())
                        || method.getParameterTypes().length != 1) continue;
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object status = chain.getArg(0);
                        Object code = null;
                        Object message = null;
                        try { code = Main.readHostField(status, "a"); } catch (Throwable ignored) {}
                        try { message = Main.readHostField(status, "b"); } catch (Throwable ignored) {}
                        Main.log("[LOGIN_RISK] gms sign-in status code=" + code
                                + " msg=" + message);
                        return chain.proceed();
                    }
                });
                try { Main.MODULE.deoptimize(method); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        // The oauth/google/login HTTP request outcome (request wrapper dr3).
        try {
            Class<?> wrapper = Class.forName("dr3", false, cl);
            for (final Method method : wrapper.getDeclaredMethods()) {
                if (!"c".equals(method.getName())
                        || method.getParameterTypes().length != 1) continue;
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object url = null;
                        try {
                            Object request = Main.readHostField(chain.getThisObject(), "a");
                            if (request != null) url = Main.readHostField(request, "a");
                        } catch (Throwable ignored) {}
                        String path = url == null ? "" : String.valueOf(url);
                        boolean googleLogin = path.contains("oauth/google")
                                || path.contains("users/oauth");
                        if (!googleLogin) return chain.proceed();
                        try {
                            Object result = chain.proceed();
                            Main.log("[LOGIN_RISK] google http ok url=" + path + " result="
                                    + (result == null ? "null"
                                            : result.getClass().getSimpleName()));
                            return result;
                        } catch (Throwable error) {
                            Main.log("[LOGIN_RISK] google http failed url=" + path + " "
                                    + error.getClass().getSimpleName() + ": "
                                    + String.valueOf(error.getMessage()));
                            throw error;
                        }
                    }
                });
                try { Main.MODULE.deoptimize(method); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        // The Credential Manager / HiddenActivity result entry: requestCode, resultCode, intent.
        try {
            Class<?> results = Class.forName("ar1", false, cl);
            for (final Method method : results.getDeclaredMethods()) {
                if (!"a".equals(method.getName())
                        || method.getParameterTypes().length != 3
                        || method.getParameterTypes()[0] != int.class
                        || method.getParameterTypes()[1] != int.class) continue;
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        int requestCode = ((Number) chain.getArg(0)).intValue();
                        int resultCode = ((Number) chain.getArg(1)).intValue();
                        Object intent = chain.getArg(2);
                        Main.log("[LOGIN_RISK] sign-in activity result request=" + requestCode
                                + " resultCode=" + resultCode
                                + " intent=" + (intent != null));
                        return chain.proceed();
                    }
                });
                try { Main.MODULE.deoptimize(method); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        // HiddenActivity internal failure logger (intent sender failures surface here).
        try {
            Class<?> hidden = Class.forName(
                    "androidx.credentials.playservices.HiddenActivity", false, cl);
            for (final Method method : hidden.getDeclaredMethods()) {
                if (!"a".equals(method.getName())
                        || method.getParameterTypes().length != 3
                        || method.getParameterTypes()[0] != String.class) continue;
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        String tag = String.valueOf(chain.getArg(0));
                        String kind = String.valueOf(chain.getArg(1));
                        String detail = String.valueOf(chain.getArg(2));
                        Main.log("[LOGIN_RISK] hidden-activity " + tag + " " + kind + " " + detail);
                        return chain.proceed();
                    }
                });
                try { Main.MODULE.deoptimize(method); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        // Every UI event on the login page (clicks/results), the Google sign-in launcher, and
        // the Credential Manager launch entry. Together they show exactly how far a tap gets.
        try {
            Class<?> viewModel = Class.forName("gy4", false, cl);
            for (final Method method : viewModel.getDeclaredMethods()) {
                if (!"g".equals(method.getName())
                        || method.getParameterTypes().length != 1) continue;
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object event = chain.getArg(0);
                        Main.log("[LOGIN_RISK] gy4 event "
                                + (event == null ? "null"
                                        : event.getClass().getSimpleName()));
                        return chain.proceed();
                    }
                });
                try { Main.MODULE.deoptimize(method); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        try {
            Class<?> launcher = Class.forName("ey4", false, cl);
            for (Constructor<?> ctor : launcher.getDeclaredConstructors()) {
                Main.MODULE.hook(ctor).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Main.log("[LOGIN_RISK] google sign-in launcher created");
                        return chain.proceed();
                    }
                });
            }
        } catch (Throwable ignored) {}
        try {
            Class<?> registryLauncher = Class.forName("a7", false, cl);
            for (final Method method : registryLauncher.getDeclaredMethods()) {
                if (!"b0".equals(method.getName())
                        || method.getParameterTypes().length != 2) continue;
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Main.log("[LOGIN_RISK] credential launcher launch");
                        return chain.proceed();
                    }
                });
                try { Main.MODULE.deoptimize(method); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        Main.log("[LOGIN_RISK] biz-code logger installed");
    }

    /** Logs the shape of the Google oauth result: success, structured biz code, or raw failure. */
    private static void logGoogleLoginResult(Object result) {
        if (result == null) {
            Main.log("[LOGIN_RISK] google result=null");
            return;
        }
        String shape = result.getClass().getSimpleName();
        Object code = null;
        try {
            Object envelope = Main.readHostField(result, "a");
            if (envelope != null) {
                code = Main.readHostField(envelope, "a");
            }
        } catch (Throwable ignored) {}
        Main.log("[LOGIN_RISK] google result=" + shape + " code=" + code);
    }

    private void installBizCodeLogger(final ClassLoader cl, String[] classNames,
                                      final String kind) {
        for (String className : classNames) {
            try {
                Class<?> type = Class.forName(className, false, cl);
                for (Constructor<?> ctor : type.getDeclaredConstructors()) {
                    if (ctor.getParameterTypes().length != 1
                            || ctor.getParameterTypes()[0] != int.class) continue;
                    Main.MODULE.hook(ctor).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            int code = ((Number) chain.getArg(0)).intValue();
                            Main.log("[LOGIN_RISK] biz_code kind=" + kind + " code=" + code);
                            return chain.proceed();
                        }
                    });
                    break;
                }
                Main.log("[LOGIN_RISK] biz-code hook class=" + className + " kind=" + kind);
                return;
            } catch (Throwable ignored) {}
        }
        Main.log("[LOGIN_RISK] biz-code hook missing kind=" + kind + " tried="
                + java.util.Arrays.toString(classNames));
    }

    private Object proceedWithRegionalLoginOptions(Chain chain, int listIndex,
                                                   Object googleOption, Object wechatOption,
                                                   Object mobileOption, Class<?> optionType)
            throws Throwable {
        boolean unlockGoogle = isGoogleLoginUnlock();
        boolean unlockWechatMobile = isWechatMobileLoginUnlock();
        if (!unlockGoogle && !unlockWechatMobile) return chain.proceed();
        try {
            Object[] args = chain.getArgs().toArray();
            List<?> original = args[listIndex] instanceof List ? (List<?>) args[listIndex] : null;
            List<?> unlocked = original;
            if (unlockGoogle) {
                unlocked = GoogleLoginUnlock.ensureGoogleFirst(
                        unlocked, googleOption, optionType);
            }
            if (unlockWechatMobile) {
                unlocked = GoogleLoginUnlock.ensureWechatAndMobile(
                        unlocked, googleOption, wechatOption, mobileOption, optionType);
            }
            if (unlocked != null && unlocked != original) {
                args[listIndex] = unlocked;
                if (unlockGoogle && !googleLoginUnlockInjectedLogged
                        && unlocked.contains(googleOption) && !original.contains(googleOption)) {
                    googleLoginUnlockInjectedLogged = true;
                    Main.log("native Google login option injected; preserved domestic options="
                            + original.size());
                }
                if (unlockWechatMobile && !wechatMobileLoginUnlockInjectedLogged
                        && (unlocked.contains(wechatOption) || unlocked.contains(mobileOption))) {
                    wechatMobileLoginUnlockInjectedLogged = true;
                    Main.log("native WeChat + mobile login options enabled; original options="
                            + original.size() + ", unlocked options=" + unlocked.size());
                }
                return chain.proceed(args);
            }
        } catch (Throwable t) {
            Main.log("regional login option injection skipped: " + t);
        }
        return chain.proceed();
    }

    private static int findAssignableParameter(Class<?>[] types, Class<?> wanted) {
        if (types == null || wanted == null) return -1;
        for (int i = 0; i < types.length; i++) {
            if (wanted.isAssignableFrom(types[i])) return i;
        }
        return -1;
    }

    /** Installs the no-op endpoint used by the module's foreground keepalive service. */
    void fa(ClassLoader cl) {
        try {
            Class<?> receiverClass = Class.forName(
                    z21.TARGET_RECEIVER, false, cl);
            Method onReceive = receiverClass.getDeclaredMethod(
                    "onReceive", Context.class, Intent.class);
            onReceive.setAccessible(true);
            Main.MODULE.hook(onReceive).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    Intent intent = chain.getArg(1) instanceof Intent
                            ? (Intent) chain.getArg(1) : null;
                    if (intent == null) {
                        return chain.proceed();
                    }
                    String action = intent.getAction();
                    boolean heartbeatAction = z21.ACTION_HEARTBEAT
                            .equals(action);
                    boolean controlAction = z21.ACTION_CONTROL
                            .equals(action);
                    boolean proactiveAction = ProactiveHeartbeatReceiver.ACTION_REQUEST
                            .equals(action);
                    boolean agentCommandAction = ACTION_AGENT_COMMAND.equals(action);
                    boolean agentDelayAction = AgentDelayReceiver.ACTION_COMPLETE
                            .equals(action);
                    if (!heartbeatAction && !controlAction && !proactiveAction
                            && !agentCommandAction && !agentDelayAction) {
                        return chain.proceed();
                    }
                    if (agentDelayAction) {
                        if (!AgentDelayActivity.TOKEN.equals(
                                intent.getStringExtra(AgentDelayReceiver.EXTRA_TOKEN))) {
                            Main.log("rejected unauthenticated Agent delay result");
                            return null;
                        }
                        Context context = chain.getArg(0) instanceof Context
                                ? (Context) chain.getArg(0) : null;
                        handleAgentDelayComplete(context, intent);
                        return null;
                    }
                    if (agentCommandAction) {
                        if (!z21.CONTROL_TOKEN.equals(
                                intent.getStringExtra(
                                        z21.EXTRA_CONTROL_TOKEN))) {
                            Main.log("rejected unauthenticated Agent command");
                            return null;
                        }
                        Context context = chain.getArg(0) instanceof Context
                                ? (Context) chain.getArg(0) : null;
                        String command = intent.getStringExtra(
                                EXTRA_AGENT_COMMAND);
                        if (command == null || command.length() == 0) {
                            String encoded = intent.getStringExtra(
                                    EXTRA_AGENT_COMMAND_BASE64);
                            if (encoded != null && encoded.length() <= 32 * 1024) {
                                try {
                                    command = new String(
                                            android.util.Base64.decode(
                                                    encoded,
                                                    android.util.Base64.DEFAULT),
                                            java.nio.charset.StandardCharsets.UTF_8);
                                } catch (Throwable ignored) {}
                            }
                        }
                        handleAgentCommand(context, command);
                        return null;
                    }
                    if (proactiveAction) {
                        if (!ProactiveHeartbeatReceiver.TOKEN.equals(
                                intent.getStringExtra(ProactiveHeartbeatReceiver.EXTRA_TOKEN))) {
                            Main.log("rejected unauthenticated proactive heartbeat");
                            return null;
                        }
                        Context context = chain.getArg(0) instanceof Context
                                ? (Context) chain.getArg(0) : null;
                        String requestId = intent.getStringExtra(
                                ProactiveHeartbeatReceiver.EXTRA_REQUEST_ID);
                        boolean taskReminder = intent.getBooleanExtra(
                                ProactiveHeartbeatReceiver.EXTRA_TASK_REMINDER, false);
                        String taskText = intent.getStringExtra(
                                ProactiveHeartbeatReceiver.EXTRA_TASK_TEXT);
                        String taskKind = intent.getStringExtra(
                                ProactiveHeartbeatReceiver.EXTRA_TASK_KIND);
                        String conversationId = intent.getStringExtra(
                                ProactiveHeartbeatReceiver.EXTRA_CONVERSATION_ID);
                        runProactiveHeartbeat(
                                context, requestId, taskText, taskReminder, taskKind,
                                conversationId);
                        return null;
                    }
                    if (!z21.CONTROL_TOKEN.equals(
                            intent.getStringExtra(z21.EXTRA_CONTROL_TOKEN))) {
                        Main.log("rejected unauthenticated local API internal control");
                        return null;
                    }
                    Context context = chain.getArg(0) instanceof Context
                            ? (Context) chain.getArg(0) : null;
                    if (controlAction) {
                        boolean success = false;
                        String message = "不支持的设置操作";
                        String operation = intent.getStringExtra(z21.EXTRA_OPERATION);
                        String value = intent.getStringExtra(z21.EXTRA_VALUE);
                        String protocol = intent.getStringExtra(
                                z21.EXTRA_PROTOCOL);
                        if ((operation == null || operation.length() == 0)
                                && (z2.PROTOCOL_OPENAI.equals(protocol)
                                || z2.PROTOCOL_ANTHROPIC.equals(protocol))) {
                            z13.setProtocolMode(context, protocol);
                            success = true;
                            message = "格式已切换";
                        } else if (z21.OP_SET_ENABLED.equals(operation)) {
                            boolean enabled = Boolean.parseBoolean(value);
                            LOCAL_API_COMPANION_CONTROL.set(Boolean.TRUE);
                            try {
                                success = setLocalApiEnabled(enabled);
                                if (success && enabled) Main.startz1(context);
                            } finally {
                                LOCAL_API_COMPANION_CONTROL.remove();
                            }
                            if (!enabled) {
                                syncV236HostFreezerLock(null, false);
                                syncV241HostFreezerLock(null, false);
                            }
                            message = success
                                    ? (enabled ? "本地 API 已开启" : "本地 API 已关闭")
                                    : "本地 API 启停失败，请查看运行状态";
                        } else if (z21.OP_SET_PROTOCOL.equals(operation)
                                && (z2.PROTOCOL_OPENAI.equals(value)
                                || z2.PROTOCOL_ANTHROPIC.equals(value))) {
                            z13.setProtocolMode(context, value);
                            success = true;
                            message = "兼容格式已保存";
                        } else if (z21.OP_SET_KEY.equals(operation)) {
                            String error = z13.setCustomKey(context, value);
                            success = error == null;
                            message = success ? "API Key 已保存" : error;
                        } else if (z21.OP_ROTATE_KEY.equals(operation)) {
                            String rotated = z13.rotateKey(context);
                            success = rotated != null && rotated.length() > 0;
                            message = success ? "已生成并启用新的随机 Key" : "随机 Key 生成失败";
                        } else if (z21.OP_SET_PORT.equals(operation)) {
                            try {
                                int port = Integer.parseInt(value == null ? "" : value.trim());
                                String error = z13.setPreferredPort(context, port);
                                success = error == null;
                                message = success ? "监听端口已保存" : error;
                                if (success && z13.isRunning() && Main.isLocalApiEnabled()) {
                                    z13.stop();
                                    Main.startz1(context);
                                    message = "监听端口已保存，服务已重新监听";
                                }
                            } catch (Throwable error) {
                                message = "监听端口必须是 1024–65535 的数字";
                            }
                        } else if (z21.OP_SET_HTTPS.equals(operation)) {
                            boolean enabled = Boolean.parseBoolean(value);
                            message = setLocalApiHttpsEnabled(context, enabled);
                            success = true;
                        }
                        Object receiver = chain.getThisObject();
                        if (receiver instanceof BroadcastReceiver
                                && ((BroadcastReceiver) receiver).isOrderedBroadcast()) {
                            BroadcastReceiver ordered = (BroadcastReceiver) receiver;
                            ordered.setResultCode(success
                                    ? Activity.RESULT_OK : Activity.RESULT_CANCELED);
                            ordered.setResultData(message == null ? "设置失败" : message);
                            Bundle details = new Bundle();
                            details.putBoolean("api_enabled", Main.isLocalApiEnabled());
                            details.putBoolean("gateway_running", z13.isRunning());
                            details.putInt("gateway_port", z13.isHttpsRunning()
                                    ? z13.tlsPort() : z13.port());
                            if (BuildInfo.PROTECTED_BUILD && HostCompat.isV241()) {
                                details.putBoolean("port_details_v241", true);
                                details.putInt("preferred_port_v241", z13.preferredPort(context));
                                details.putInt("http_port_v241", z13.port());
                                details.putInt("https_port_v241",
                                        z13.isHttpsRunning() ? z13.tlsPort() : 0);
                            }
                            details.putString(z21.DETAIL_CONNECTION, z13.connectionInfo());
                            details.putString(z21.DETAIL_RUNTIME, localApiRuntimeStatus());
                            details.putString(z21.DETAIL_API_KEY, z13.apiKey());
                            details.putString(z21.DETAIL_PROTOCOL, z13.protocolMode());
                            details.putBoolean("https_enabled", z5.isEnabled(context));
                            ordered.setResultExtras(details);
                        }
                        return null;
                    }
                    localApiFloatingWindowRequested = intent.getBooleanExtra(
                            z21.EXTRA_OVERLAY_REQUESTED, false);
                    localApiFloatingWindowPermission = intent.getBooleanExtra(
                            z21.EXTRA_OVERLAY_PERMISSION, false);
                    localApiKeepAliveRequested = intent.getBooleanExtra(
                            z21.EXTRA_API_KEEPALIVE_REQUESTED, false);
                    boolean active = context != null && Main.isLocalApiEnabled();
                    localApiKeepAliveHeartbeatAt = SystemClock.elapsedRealtime();
                    localApiKeepAliveError = "";
                    if (active) {
                        Main.startz1(context);
                    } else if (z13.isRunning()) {
                        z13.stop();
                    }
                    boolean freezerLockV236 = syncV236HostFreezerLock(
                            intent, active && localApiKeepAliveRequested);
                    boolean freezerLock = syncV241HostFreezerLock(
                            intent, active && localApiKeepAliveRequested);
                    Object receiver = chain.getThisObject();
                    if (receiver instanceof BroadcastReceiver
                            && ((BroadcastReceiver) receiver).isOrderedBroadcast()) {
                        BroadcastReceiver ordered = (BroadcastReceiver) receiver;
                        ordered.setResultCode(Activity.RESULT_OK);
                        ordered.setResultData((active ? "enabled" : "disabled") + "|"
                                + (z13.isRunning() ? "running" : "stopped"));
                        Bundle details = new Bundle();
                        details.putInt("gateway_port",
                                z13.isRunning()
                                        ? (z13.isHttpsRunning() ? z13.tlsPort() : z13.port())
                                        : z13.preferredPort(context));
                        if (BuildInfo.PROTECTED_BUILD && HostCompat.isV241()) {
                            details.putBoolean("port_details_v241", true);
                            details.putInt("preferred_port_v241", z13.preferredPort(context));
                            details.putInt("http_port_v241", z13.port());
                            details.putInt("https_port_v241",
                                    z13.isHttpsRunning() ? z13.tlsPort() : 0);
                            details.putBoolean(
                                    z21.DETAIL_FREEZER_LOCK_V241, freezerLock);
                        } else if (BuildInfo.PROTECTED_BUILD && HostCompat.isV236()) {
                            details.putBoolean(
                                    z21.DETAIL_FREEZER_LOCK_V236, freezerLockV236);
                        }
                        details.putBoolean("api_enabled", active);
                        details.putBoolean("gateway_running", z13.isRunning());
                        details.putString(z21.DETAIL_CONNECTION, z13.connectionInfo());
                        details.putString(z21.DETAIL_RUNTIME, localApiRuntimeStatus());
                        details.putString(z21.DETAIL_API_KEY, z13.apiKey());
                        details.putString(z21.DETAIL_PROTOCOL, z13.protocolMode());
                        details.putBoolean("https_enabled", z5.isEnabled(context));
                        ordered.setResultExtras(details);
                    }
                    return null;
                }
            });
            Main.log("local API cached-freezer keepalive receiver installed");
        } catch (Throwable t) {
            localApiKeepAliveError = "保活接收器安装失败：" + Main.safeThrowableMessage(t);
            Main.log("local API keepalive receiver hook failed: " + t);
        }
    }

    /** Exact code249 cached-app freezer guard; no other host enters this adapter. */
    private static boolean syncV236HostFreezerLock(Intent intent, boolean enabled) {
        if (!BuildInfo.PROTECTED_BUILD || !HostCompat.isV236()) return false;
        synchronized (V236_FREEZER_LOCK_GUARD) {
            if (!enabled) {
                releaseV236HostFreezerLockLocked();
                return false;
            }
            if (v236FreezerLock != null && v236FreezerLock.isValid()
                    && v236FreezerChannel != null && v236FreezerChannel.isOpen()) {
                return true;
            }
            releaseV236HostFreezerLockLocked();
            if (intent == null || intent.getExtras() == null) return false;
            IBinder bridge = intent.getExtras().getBinder(z21.EXTRA_FREEZER_BRIDGE_V236);
            if (bridge == null || !bridge.isBinderAlive()) return false;
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            ParcelFileDescriptor descriptor = null;
            FileOutputStream stream = null;
            FileChannel channel = null;
            FileLock lock = null;
            boolean parcelsRecycled = false;
            try {
                data.writeString(z21.CONTROL_TOKEN);
                if (!bridge.transact(
                        z21.FREEZER_BRIDGE_TRANSACTION_V236, data, reply, 0)) return false;
                reply.readException();
                descriptor = ParcelFileDescriptor.CREATOR.createFromParcel(reply);
                data.recycle();
                reply.recycle();
                parcelsRecycled = true;
                stream = new FileOutputStream(descriptor.getFileDescriptor());
                channel = stream.getChannel();
                lock = channel.tryLock();
                if (lock == null) throw new IllegalStateException("lock already owned");
                v236FreezerDescriptor = descriptor;
                v236FreezerStream = stream;
                v236FreezerChannel = channel;
                v236FreezerLock = lock;
                Main.log("code249 cached-app freezer file lock acquired");
                return true;
            } catch (Throwable error) {
                if (lock != null) try { lock.release(); } catch (Throwable ignored) {}
                if (channel != null) try { channel.close(); } catch (Throwable ignored) {}
                if (stream != null) try { stream.close(); } catch (Throwable ignored) {}
                if (descriptor != null) try { descriptor.close(); } catch (Throwable ignored) {}
                localApiKeepAliveError = "2.3.6 文件锁保活失败："
                        + Main.safeThrowableMessage(error);
                Main.log("code249 cached-app freezer file lock unavailable: " + error);
                return false;
            } finally {
                if (!parcelsRecycled) {
                    data.recycle();
                    reply.recycle();
                }
            }
        }
    }

    private static void releaseV236HostFreezerLockLocked() {
        if (v236FreezerLock != null) {
            try { v236FreezerLock.release(); } catch (Throwable ignored) {}
        }
        if (v236FreezerChannel != null) {
            try { v236FreezerChannel.close(); } catch (Throwable ignored) {}
        }
        if (v236FreezerStream != null) {
            try { v236FreezerStream.close(); } catch (Throwable ignored) {}
        }
        if (v236FreezerDescriptor != null) {
            try { v236FreezerDescriptor.close(); } catch (Throwable ignored) {}
        }
        v236FreezerLock = null;
        v236FreezerChannel = null;
        v236FreezerStream = null;
        v236FreezerDescriptor = null;
    }

    /**
     * code257-only cached-app freezer guard. The foreground companion supplies a duplicated file
     * descriptor over a private Binder. DeepSeek owns the exclusive lock while the non-cached
     * companion waits for it, matching AOSP's file-lock freezer exemption. No older host reads
     * the Binder extra or enters this path.
     */
    private static boolean syncV241HostFreezerLock(Intent intent, boolean enabled) {
        if (!BuildInfo.PROTECTED_BUILD || !HostCompat.isV241()) return false;
        synchronized (V241_FREEZER_LOCK_GUARD) {
            if (!enabled) {
                releaseV241HostFreezerLockLocked();
                return false;
            }
            if (v241FreezerLock != null && v241FreezerLock.isValid()
                    && v241FreezerChannel != null && v241FreezerChannel.isOpen()) {
                return true;
            }
            releaseV241HostFreezerLockLocked();
            if (intent == null || intent.getExtras() == null) return false;
            IBinder bridge = intent.getExtras().getBinder(z21.EXTRA_FREEZER_BRIDGE_V241);
            if (bridge == null || !bridge.isBinderAlive()) return false;
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            ParcelFileDescriptor descriptor = null;
            FileOutputStream stream = null;
            FileChannel channel = null;
            FileLock lock = null;
            boolean parcelsRecycled = false;
            try {
                data.writeString(z21.CONTROL_TOKEN);
                if (!bridge.transact(
                        z21.FREEZER_BRIDGE_TRANSACTION_V241, data, reply, 0)) {
                    return false;
                }
                reply.readException();
                descriptor = ParcelFileDescriptor.CREATOR.createFromParcel(reply);
                // POSIX record locks are dropped when this process closes any descriptor for
                // the same inode. Recycle Binder's temporary descriptor before taking the lock,
                // leaving only the durable ParcelFileDescriptor below.
                data.recycle();
                reply.recycle();
                parcelsRecycled = true;
                stream = new FileOutputStream(descriptor.getFileDescriptor());
                channel = stream.getChannel();
                lock = channel.tryLock();
                if (lock == null) throw new IllegalStateException("lock already owned");
                v241FreezerDescriptor = descriptor;
                v241FreezerStream = stream;
                v241FreezerChannel = channel;
                v241FreezerLock = lock;
                Main.log("code257 cached-app freezer file lock acquired");
                return true;
            } catch (Throwable error) {
                if (lock != null) try { lock.release(); } catch (Throwable ignored) {}
                if (channel != null) try { channel.close(); } catch (Throwable ignored) {}
                if (stream != null) try { stream.close(); } catch (Throwable ignored) {}
                if (descriptor != null) try { descriptor.close(); } catch (Throwable ignored) {}
                localApiKeepAliveError = "2.4.1 文件锁保活失败："
                        + Main.safeThrowableMessage(error);
                Main.log("code257 cached-app freezer file lock unavailable: " + error);
                return false;
            } finally {
                if (!parcelsRecycled) {
                    data.recycle();
                    reply.recycle();
                }
            }
        }
    }

    private static void releaseV241HostFreezerLockLocked() {
        if (v241FreezerLock != null) {
            try { v241FreezerLock.release(); } catch (Throwable ignored) {}
        }
        if (v241FreezerChannel != null) {
            try { v241FreezerChannel.close(); } catch (Throwable ignored) {}
        }
        if (v241FreezerStream != null) {
            try { v241FreezerStream.close(); } catch (Throwable ignored) {}
        }
        if (v241FreezerDescriptor != null) {
            try { v241FreezerDescriptor.close(); } catch (Throwable ignored) {}
        }
        v241FreezerLock = null;
        v241FreezerChannel = null;
        v241FreezerStream = null;
        v241FreezerDescriptor = null;
    }

    private static void runProactiveHeartbeat(final Context context, final String requestId,
                                              final String taskText,
                                              final boolean taskReminder,
                                              final String requestedTaskKind,
                                              final String requestedConversationId) {
        if (context == null || (!taskReminder && !Main.isProactiveHeartbeatEnabled())) return;
        final String reminderText = normalizeReminderTask(taskText);
        if (taskReminder && reminderText.length() == 0) return;
        final String taskKind = ProactiveHeartbeatReceiver.TASK_KIND_HEARTBEAT
                .equals(requestedTaskKind)
                ? ProactiveHeartbeatReceiver.TASK_KIND_HEARTBEAT
                : ProactiveHeartbeatReceiver.TASK_KIND_REMINDER;
        Main.HeartbeatBinding activeBinding = Main.readHeartbeatBinding();
        String suppliedConversation = HeartbeatToolProtocol.cleanScope(
                requestedConversationId);
        final String conversationId = suppliedConversation.length() > 0
                ? suppliedConversation
                : activeBinding.conversationId;
        if (ProactiveHeartbeatReceiver.TASK_KIND_HEARTBEAT.equals(taskKind)
                && conversationId.length() == 0) {
            Main.log("proactive heartbeat skipped because no conversation is bound");
            return;
        }
        new Thread(new Runnable() {
            @Override public void run() {
                String id = requestId == null || requestId.length() == 0
                        ? (taskReminder ? "reminder-" : "heartbeat-")
                        + Long.toHexString(System.currentTimeMillis())
                        : requestId;
                try {
                    Main module = awaitProactiveRuntime(15_000L);
                    Integer nativeParent = null;
                    boolean nativeReasoning = false;
                    String nativeModel = "default";
                    HookSessionManagement.NativeHeartbeatHistory beforeHistory = null;
                    if (conversationId.length() > 0) {
                        try {
                            beforeHistory = module.fetchNativeHeartbeatHistory(conversationId);
                            nativeParent = beforeHistory.head;
                            nativeReasoning = beforeHistory.reasoning;
                            nativeModel = beforeHistory.nativeModel;
                        } catch (Throwable historyError) {
                            Main.log("proactive history prefetch failed sid=" + conversationId
                                    + ": " + Main.safeThrowableMessage(historyError));
                        }
                        Object nativeSession = Main.findNativeSession(conversationId);
                        if (nativeSession != null) {
                            if (nativeParent == null) {
                            Object current = HookSessionManagement.nativeSessionHead(nativeSession);
                            if (!(current instanceof Number)) {
                                current = Main.invokeNoArg(nativeSession, "e");
                            }
                            if (current instanceof Number
                                    && ((Number) current).intValue() > 0) {
                                nativeParent = Integer.valueOf(
                                        ((Number) current).intValue());
                            }
                            }
                            Object messages = MainReflectionSupport.fieldByName(nativeSession, "f");
                            if (messages instanceof Map) {
                                nativeReasoning = nativeHistoryReasoning(
                                        new ArrayList(((Map) messages).values()),
                                        nativeParent);
                            }
                            Object selectedModel = Main.invokeNoArg(nativeSession, "f");
                            if (selectedModel instanceof String
                                    && ((String) selectedModel).trim().length() > 0) {
                                nativeModel = Main.normalizeNativeHeartbeatModel(
                                        (String) selectedModel);
                            }
                        }
                        if (nativeParent == null) {
                            nativeParent = ChatEditorUi.conversationHeadFromAllDbs(
                                    conversationId);
                        }
                        HistoryBridge.Snapshot snapshot =
                                HistoryBridge.snapshot(conversationId);
                        if (snapshot != null) {
                            for (int index = snapshot.rows.size() - 1;
                                 index >= 0; index--) {
                                HistoryBridge.Row row = snapshot.rows.get(index);
                                if (row != null && "USER".equals(row.role)
                                        && row.thinkingEnabled != null) {
                                    // The authenticated history endpoint may omit this nullable
                                    // field. WCDB retains the exact setting used by the visible
                                    // user turn, so it is the reliable final fallback.
                                    nativeReasoning =
                                            row.thinkingEnabled.booleanValue();
                                    break;
                                }
                            }
                        }
                        if (nativeParent == null || nativeParent.intValue() <= 0) {
                            throw new IOException("The bound DeepSeek conversation has no "
                                    + "usable server message head");
                        }
                    }
                    String previous = readHeartbeatHistory(conversationId);
                    if (previous == null) previous = "";
                    if (previous.length() > 5000) {
                        previous = previous.substring(previous.length() - 5000);
                    }
                    long now = System.currentTimeMillis();
                    String instruction;
                    if (taskReminder && ProactiveHeartbeatReceiver.TASK_KIND_REMINDER
                            .equals(taskKind)) {
                        instruction = UiLanguage.text(context,
                                "用户先前明确设置了一个提醒，现在已经到约定时间。提醒事项："
                                        + reminderText + "。请像熟悉的聊天伙伴一样直接、自然、简短地"
                                        + "提醒用户去做这件事。必须说清楚要做什么；不要说时间还没到，"
                                        + "不要提到心跳、定时器、后台、系统提示词或实现方式。"
                                        + "不要使用 Markdown，不超过 100 个汉字。",
                                "The user explicitly scheduled a reminder and its due time has now "
                                        + "arrived. Reminder: " + reminderText
                                        + ". Remind the user directly, naturally, and briefly, like "
                                        + "a familiar conversation partner. Clearly say what they "
                                        + "need to do. Do not say it is too early and do not mention "
                                        + "heartbeats, timers, background work, system prompts, or "
                                        + "implementation details. Use no Markdown and stay under "
                                        + "80 words.");
                    } else {
                        instruction = taskReminder ? reminderText
                                : Main.heartbeatPlanForConversation(conversationId);
                        if (instruction.length() == 0) {
                            instruction = UiLanguage.text(context,
                                    "像熟悉的朋友一样自然、简短地找用户聊聊天；"
                                            + "内容要温暖且具体，不要假装知道未提供的现实情况",
                                    "Start a brief, warm, specific conversation like a familiar "
                                            + "friend, without pretending to know real-world facts "
                                            + "that were not provided");
                        }
                    }
                    String event = HeartbeatToolProtocol.event(
                            taskKind, instruction, now, previous, conversationId,
                            recentBoundConversationContext(conversationId));
                    String prompt = HistoryBridge.wrapSystemPrompt(
                            HeartbeatToolProtocol.systemPrompt(
                                    now, Main.heartbeatPlanForConversation(conversationId),
                                    Main.proactiveHeartbeatIntervalMinutes(), conversationId,
                                    AgentToolConfig.effectiveTools(
                                            Main.isProactiveHeartbeatEnabled())),
                            event);
                    if (conversationId.length() > 0
                            && module.dispatchProactiveThroughNativeUi(
                            context, id, taskReminder, taskKind,
                            conversationId, nativeParent,
                                    nativeReasoning, prompt, reminderText)) {
                        Main.log("proactive heartbeat handed to native chat stream id=" + id
                                + " sid=" + conversationId);
                        return;
                    }
                    z2.CompletionRequest request =
                            new z2.CompletionRequest(
                                    id, taskReminder
                                    ? "deepseek-aux-reminder" : "deepseek-aux-heartbeat",
                                    nativeModel,
                                    prompt, prompt, nativeReasoning, false, 256,
                                    null, null, false)
                                    .withClientSessionScope(
                                            "deekseep-proactive-"
                                                    + conversationId)
                                    .withNativeConversation(
                                            conversationId, nativeParent);
                    tlProactiveHeartbeatRequest.set(Boolean.TRUE);
                    z2.CompletionResult result;
                    try {
                        result = module.executeLocalApiCompletion(request, null);
                    } finally {
                        tlProactiveHeartbeatRequest.remove();
                    }
                    HeartbeatToolProtocol.Result parsed =
                            HeartbeatToolProtocol.parse(
                                    result == null ? null : result.text);
                    Main.executeHeartbeatToolCalls(context, parsed.calls, false);
                    String message = normalizeProactiveMessage(parsed.visibleText);
                    if (message.length() == 0) {
                        throw new IOException("DeepSeek returned an empty proactive message");
                    }
                    if (ProactiveHeartbeatReceiver.TASK_KIND_HEARTBEAT
                            .equals(taskKind)) {
                        rememberProactiveMessage(conversationId, message);
                    }
                    boolean attached = false;
                    if (conversationId.length() > 0 && nativeParent != null) {
                        try {
                            HookSessionManagement.NativeHeartbeatHistory refreshed =
                                    module.refreshNativeHeartbeatHistory(
                                            conversationId,
                                            beforeHistory == null
                                                    ? nativeParent : beforeHistory.head);
                            boolean persisted =
                                    module.persistNativeHeartbeatHistory(refreshed);
                            if (refreshed != null) {
                                HookSessionManagement.PENDING_NATIVE_HEARTBEAT_HISTORIES.put(
                                        refreshed.sid, refreshed);
                            }
                            boolean applied =
                                    module.applyNativeHeartbeatHistory(refreshed);
                            attached = refreshed != null
                                    && refreshed.head != null
                                    && !nativeParent.equals(refreshed.head);
                            Main.log("proactive response attached sid=" + conversationId
                                    + " head=" + (refreshed == null
                                            ? "null" : refreshed.head)
                                    + " persisted=" + persisted
                                    + " applied=" + applied
                                    + " new_head=" + attached);
                        } catch (Throwable historyError) {
                            // The server has already stored the turn. A later normal history load
                            // will pass through the same folding hook, so notification delivery
                            // must not be lost merely because this eager refresh failed.
                            Main.log("proactive history refresh failed sid=" + conversationId
                                    + ": " + Main.safeThrowableMessage(historyError));
                        }
                    }
                    boolean foreground = Main.isDeepSeekForeground();
                    dispatchProactiveHeartbeatResponse(
                            context, id, message, foreground, taskReminder, taskKind,
                            conversationId);
                    Main.log("proactive heartbeat completed id=" + id
                            + " chars=" + message.length()
                            + " reminder=" + taskReminder
                            + " reasoning=" + nativeReasoning
                            + " model=" + nativeModel
                            + " attached=" + attached
                            + " foreground=" + foreground);
                } catch (Throwable t) {
                    tlProactiveHeartbeatRequest.remove();
                    Main.log("proactive heartbeat failed id=" + id + ": " + t);
                    if (taskReminder) {
                        boolean reminderKind =
                                ProactiveHeartbeatReceiver.TASK_KIND_REMINDER
                                        .equals(taskKind);
                        String fallback = reminderKind
                                ? UiLanguage.text(context,
                                "到时间啦，记得" + reminderText,
                                "It's time — remember to " + reminderText)
                                : UiLanguage.text(context,
                                "来找你啦～" + reminderText,
                                "I'm here — " + reminderText);
                        boolean foreground = Main.isDeepSeekForeground();
                        dispatchProactiveHeartbeatResponse(
                                context, id, fallback, foreground, true, taskKind,
                                conversationId);
                    } else {
                        String fallback = UiLanguage.text(context,
                                "来找你聊聊天啦～",
                                "I'm here to chat with you.");
                        boolean foreground = Main.isDeepSeekForeground();
                        dispatchProactiveHeartbeatResponse(
                                context, id, fallback, foreground, false,
                                ProactiveHeartbeatReceiver.TASK_KIND_HEARTBEAT,
                                conversationId);
                    }
                }
            }
        }, taskReminder ? "DeePX-proactive-reminder"
                : "DeePX-proactive-heartbeat").start();
    }

    private static Main awaitProactiveRuntime(long timeoutMs) throws IOException {
        long deadline = SystemClock.elapsedRealtime() + Math.max(0L, timeoutMs);
        while (true) {
            Main module = Main.MODULE;
            if (module != null && Main.hostClassLoader != null
                    && Main.liveR92 != null && Main.liveQ71 != null) {
                return module;
            }
            if (SystemClock.elapsedRealtime() >= deadline) {
                throw new IOException("DeepSeek native transport did not initialize in time");
            }
            try {
                Thread.sleep(250L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("proactive heartbeat initialization was interrupted");
            }
        }
    }

    static boolean isIdleGenerationState(Object state) {
        String name = MainReflectionSupport.simpleName(state);
        // code257 pq.i starts at bq.a. code249 remains xp.a and every older channel keeps its
        // established generation-state name below.
        // 2.5.3 (code275): bs.i compares against nr.a (see e22.f0's la5.b(b.i.getValue(), nr.a),
        // the exact counterpart of zg1.Q's qe4.b(rb8Var.getValue(), bq.a)).
        if (HostCompat.isV253()) return "nr".equals(name);
        if (HostCompat.isV241()) return "bq".equals(name);
        if (HostCompat.isV234()) {
            return HostCompat.isGooglePlay() ? "bq".equals(name) : "xp".equals(name);
        }
        return HostCompat.isV230() ? "np".equals(name) : "gp".equals(name);
    }

    /** Exact code257 shifted za1.G() to zg1.H(); legacy hosts keep their literal G getter. */
    static Object nativeUiChatSession(Object viewModel) {
        if (viewModel == null) return null;
        String getter = HostCompat.isV241()
                ? HostCompat.chatViewModelMethod("G") : "G";
        return Main.invokeNoArg(viewModel, getter);
    }

    /**
     * When the bound conversation is still the active Chat ViewModel, use DeepSeek's own send
     * pipeline. That pipeline owns the Compose message state and SSE reducer, so the assistant
     * bubble appears and streams exactly like an ordinary reply. Background/cold-process cases
     * fall back to the direct native transport and eager history refresh below.
     */
    boolean dispatchProactiveThroughNativeUi(
            Context context, String requestId, boolean taskReminder, String taskKind,
            String sid, Integer previousHead, boolean reasoning, String prompt,
            String fallbackText) {
        WeakReference<Object> reference = HookSessionManagement.ACTIVE_CHAT_VIEW_MODELS.get(sid);
        final Object viewModel = reference == null ? null : reference.get();
        if (reference != null && viewModel == null) {
            HookSessionManagement.ACTIVE_CHAT_VIEW_MODELS.remove(sid, reference);
        }
        if (viewModel == null || prompt == null || prompt.length() == 0) return false;

        Object selected = nativeUiChatSession(viewModel);
        if (selected == null || !sid.equals(String.valueOf(
                Main.readHostField(selected, "a")))) return false;
        Object generationState = Main.invokeNoArg(Main.readHostField(selected, "i"), "getValue");
        if (!isIdleGenerationState(generationState)) {
            Main.log("native proactive stream unavailable because chat is busy sid=" + sid
                    + " state=" + MainReflectionSupport.simpleName(generationState));
            return false;
        }

        Main.NativeUiHeartbeatRequest existing = Main.PENDING_NATIVE_UI_HEARTBEATS.get(sid);
        if (existing != null
                && System.currentTimeMillis() - existing.startedAt < 4L * 60L * 1000L) {
            Main.log("native proactive stream already pending sid=" + sid);
            return false;
        }
        if (existing != null) Main.PENDING_NATIVE_UI_HEARTBEATS.remove(sid, existing);

        final Main.NativeUiHeartbeatRequest pending = new Main.NativeUiHeartbeatRequest(
                context, requestId, taskReminder, taskKind, sid,
                previousHead, reasoning, fallbackText);
        if (Main.PENDING_NATIVE_UI_HEARTBEATS.putIfAbsent(sid, pending) != null) return false;

        final AtomicBoolean invoked = new AtomicBoolean();
        final CountDownLatch completed = new CountDownLatch(1);
        Runnable send = new Runnable() {
            @Override public void run() {
                try {
                    Object current = nativeUiChatSession(viewModel);
                    if (current == null || !pending.sid.equals(String.valueOf(
                            Main.readHostField(current, "a")))) return;
                    Object state = Main.invokeNoArg(Main.readHostField(current, "i"), "getValue");
                    if (!isIdleGenerationState(state)) return;

                    invoked.set(invokeNativeUiTextSend(viewModel, prompt));
                } catch (Throwable error) {
                    Main.log("native proactive stream start failed sid=" + pending.sid
                            + ": " + Main.safeThrowableMessage(error));
                } finally {
                    completed.countDown();
                }
            }
        };
        Handler handler = Main.currentMainHandler();
        if (Looper.myLooper() == Looper.getMainLooper() || handler == null) {
            send.run();
        } else {
            handler.post(send);
            try {
                completed.await(4L, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        if (!invoked.get()) {
            Main.PENDING_NATIVE_UI_HEARTBEATS.remove(sid, pending);
            return false;
        }
        Main.log("native proactive stream started id=" + requestId + " sid=" + sid);
        return true;
    }

    /**
     * Sends a normal, visible user text through the host's own composer pipeline. The mappings are
     * shared by proactive heartbeats and Agent question answers so 2.2.x and 2.3.0 cannot silently
     * diverge.
     */
    static boolean invokeNativeUiTextSend(
            Object viewModel, String prompt) throws Exception {
        if (viewModel == null || prompt == null || prompt.trim().length() == 0) return false;
        ClassLoader cl = viewModel.getClass().getClassLoader();
        Field emptyField = HostCompat.load(cl, "jm7").getDeclaredField("b");
        emptyField.setAccessible(true);
        Object emptyAttachments = emptyField.get(null);
        if (emptyAttachments == null) return false;
        return invokeNativeUiTextSend(viewModel, prompt, emptyAttachments);
    }

    static boolean invokeNativeUiTextSend(
            Object viewModel, String prompt, Object attachments) throws Exception {
        if (viewModel == null || prompt == null || prompt.trim().length() == 0
                || attachments == null) return false;
        ClassLoader cl = viewModel.getClass().getClassLoader();
        Method sendMethod = null;
        if (HostCompat.isV253()) {
            // 2.5.3's send bridge is e22.f0(vm, text, p1 attachments, audioId, boolean, boolean,
            // mask) — one extra boolean compared to 2.4.1's zg1.Q.
            Class<?> persistentList = HostCompat.load(cl, "h1");
            String bridgeName = HostCompat.chatViewModelMethod("P");
            for (Method method : viewModel.getClass().getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (bridgeName.equals(method.getName())
                        && java.lang.reflect.Modifier.isStatic(method.getModifiers())
                        && types.length == 7
                        && types[0] == viewModel.getClass()
                        && types[1] == String.class
                        && types[2] == persistentList
                        && types[3] == String.class
                        && types[4] == boolean.class
                        && types[5] == boolean.class
                        && types[6] == int.class) {
                    sendMethod = method;
                    break;
                }
            }
        } else if (HostCompat.isV234()) {
            Class<?> persistentList = HostCompat.load(cl, "h1");
            String bridgeName = HostCompat.isV241()
                    ? HostCompat.chatViewModelMethod("P") : "P";
            for (Method method : viewModel.getClass().getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (bridgeName.equals(method.getName())
                        && java.lang.reflect.Modifier.isStatic(method.getModifiers())
                        && types.length == 6
                        && types[0] == viewModel.getClass()
                        && types[1] == String.class
                        && types[2] == persistentList
                        && types[3] == String.class
                        && types[4] == boolean.class
                        && types[5] == int.class) {
                    sendMethod = method;
                    break;
                }
            }
        } else if (HostCompat.isV230()) {
            Class<?> persistentList = HostCompat.load(cl, "h1");
            for (Method method : viewModel.getClass().getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if ("R".equals(method.getName())
                        && java.lang.reflect.Modifier.isStatic(method.getModifiers())
                        && types.length == 5
                        && types[0] == viewModel.getClass()
                        && types[1] == String.class
                        && types[2] == persistentList
                        && types[3] == String.class
                        && types[4] == int.class) {
                    sendMethod = method;
                    break;
                }
            }
        } else {
            for (Method method : viewModel.getClass().getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if ("Q".equals(method.getName()) && types.length == 4
                        && types[0] == String.class
                        && types[2] == String.class) {
                    sendMethod = method;
                    break;
                }
            }
        }
        if (sendMethod == null) return false;
        sendMethod.setAccessible(true);
        // za1.Q(String, h1, String, yq7): the first String is the actual user prompt and the
        // third is an optional audio id. 2.3.0 cc1.R is the Kotlin default bridge; bit 8 supplies
        // the absent audio id while retaining the prompt and immutable attachment list.
        if (HostCompat.isV253()) {
            // f0 keeps the same 36-mask contract; the extra boolean mirrors the host default.
            sendMethod.invoke(null, viewModel, prompt, attachments, null, false, false, 36);
        } else if (HostCompat.isV234()) {
            // ef1/kd1/td1.P is the established bridge; code257 independently shifts it to zg1.Q.
            // Mask 36 retains the supplied prompt and attachment vector while defaulting the
            // optional audio/source arguments on both exact contracts.
            sendMethod.invoke(null, viewModel, prompt, attachments, null, false, 36);
        } else if (HostCompat.isV230()) {
            sendMethod.invoke(null, viewModel, prompt, attachments, null, 8);
        } else {
            sendMethod.invoke(viewModel, prompt, attachments, null, null);
        }
        return true;
    }

    /**
     * Uses DeepSeek's authenticated history endpoint and its own Kotlin serializer. Constructing
     * pw0 also runs the global folding hook, so callers receive only the visible conversation
     * chain even though the server retains the anonymous trigger as the transport parent.
     */
    HookSessionManagement.NativeHeartbeatHistory fetchNativeHeartbeatHistory(String conversationId)
            throws Throwable {
        String sid = HeartbeatToolProtocol.cleanScope(conversationId);
        ClassLoader cl = Main.hostClassLoader;
        Object q71 = Main.liveQ71;
        if (sid.length() == 0 || cl == null || q71 == null) {
            throw new IOException("DeepSeek history transport is not ready");
        }
        Object services = MainReflectionSupport.fieldByName(q71, "f");
        Object historyApi = MainReflectionSupport.fieldByName(services, "a");
        if (historyApi == null) throw new IOException("DeepSeek history API is unavailable");

        Class<?> requestType = HostCompat.load(cl, "lj9");
        Constructor<?> requestConstructor =
                requestType.getDeclaredConstructor(
                        Object.class, Object.class, Object.class, Object.class, int.class);
        requestConstructor.setAccessible(true);
        Object historyRequest = requestConstructor.newInstance(
                sid, "stream_close", null, null, Integer.valueOf(7));

        Class<?> continuation = HostCompat.load(cl, "uz1");
        Method fetch = null;
        for (Method method : historyApi.getClass().getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if ("b".equals(method.getName()) && types.length == 2
                    && types[0] == requestType && types[1] == continuation) {
                fetch = method;
                break;
            }
        }
        if (fetch == null) throw new NoSuchMethodException("DeepSeek history fetch");
        Object raw = driveSuspend(cl, fetch, historyApi, new Object[]{historyRequest});
        if (raw == null) throw new IOException("DeepSeek returned no history response");

        Class<?> parserContext = HostCompat.load(cl, "pl9");
        Method parse = raw.getClass().getDeclaredMethod(
                "a", boolean.class, parserContext, continuation);
        Object wrapper = driveSuspend(
                cl, parse, raw, new Object[]{Boolean.FALSE, null});
        if (wrapper == null) throw new IOException("DeepSeek history response was empty");
        Object biz = MainReflectionSupport.fieldByName(wrapper, "a");
        Object bizValue = Main.invokeNoArg(biz, "getValue");
        if (!(bizValue instanceof Number)) bizValue = MainReflectionSupport.fieldByName(biz, "a");
        if (bizValue instanceof Number && ((Number) bizValue).intValue() != 0) {
            throw new IOException("DeepSeek history rejected the request: "
                    + String.valueOf(MainReflectionSupport.fieldByName(wrapper, "b")));
        }

        Object jsonValue = MainReflectionSupport.fieldByName(wrapper, "c");
        Class<?> x94 = HostCompat.load(cl, "x94");
        Field codecField = x94.getDeclaredField("a");
        codecField.setAccessible(true);
        Object codec = codecField.get(null);
        Class<?> pw0 = HostCompat.load(cl, "pw0");
        Field companionField = pw0.getDeclaredField("Companion");
        companionField.setAccessible(true);
        Object companion = companionField.get(null);
        Method serializerMethod = companion.getClass().getMethod("serializer");
        serializerMethod.setAccessible(true);
        Object serializer = serializerMethod.invoke(companion);
        Method decode = codec.getClass().getMethod(
                "a", HostCompat.load(cl, "ch4"), HostCompat.load(cl, "m84"));
        decode.setAccessible(true);
        Object response = decode.invoke(codec, serializer, jsonValue);
        if (response == null) throw new IOException("DeepSeek history could not be decoded");

        Object session = MainReflectionSupport.fieldByName(response, "a");
        String responseSid = HookAttachmentPipeline.stringField(session, "a");
        if (!sid.equals(responseSid)) {
            throw new IOException("DeepSeek returned history for a different conversation");
        }
        Object messagesValue = MainReflectionSupport.fieldByName(response, "b");
        if (!(messagesValue instanceof List)) {
            throw new IOException("DeepSeek returned no history messages");
        }
        List messages = (List) messagesValue;
        Integer head = Main.intField(session, "d");
        if (head == null || head.intValue() <= 0) {
            for (Object message : messages) {
                Integer id = Main.intField(message, "f");
                if (id != null && id.intValue() > 0
                        && (head == null || id.intValue() > head.intValue())) {
                    head = id;
                }
            }
        }
        // za7.i is model_type. za7.g is title_type and commonly contains SYSTEM.
        String model = HookAttachmentPipeline.stringField(session, "i");
        return new HookSessionManagement.NativeHeartbeatHistory(
                response, session, sid, messages, head,
                Main.intField(session, "c"), Main.intField(response, "d"),
                nativeHistoryReasoning(messages, head), model);
    }

    private static boolean nativeHistoryReasoning(List messages, Integer head) {
        if (messages == null || messages.isEmpty()) return false;
        HashMap<Integer, Object> byId = new HashMap<>();
        for (Object message : messages) {
            Integer id = Main.intField(message, "f");
            if (id != null) byId.put(id, message);
        }
        Integer cursor = head;
        HashSet<Integer> seen = new HashSet<>();
        while (cursor != null && seen.add(cursor)) {
            Object message = byId.get(cursor);
            if (message == null) break;
            if ("USER".equals(String.valueOf(MainReflectionSupport.fieldByName(message, "h")))) {
                Object thinking = MainReflectionSupport.fieldByName(message, "u");
                if (thinking instanceof Boolean) {
                    return ((Boolean) thinking).booleanValue();
                }
            }
            cursor = Main.intField(message, "g");
        }
        for (int index = messages.size() - 1; index >= 0; index--) {
            Object message = messages.get(index);
            if (!"USER".equals(String.valueOf(MainReflectionSupport.fieldByName(message, "h")))) continue;
            Object thinking = MainReflectionSupport.fieldByName(message, "u");
            if (thinking instanceof Boolean) {
                return ((Boolean) thinking).booleanValue();
            }
        }
        return false;
    }

    HookSessionManagement.NativeHeartbeatHistory refreshNativeHeartbeatHistory(
            String conversationId, Integer previousHead) throws Throwable {
        HookSessionManagement.NativeHeartbeatHistory latest = null;
        Throwable lastError = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            if (attempt > 0) {
                try {
                    Thread.sleep(350L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw interrupted;
                }
            }
            try {
                latest = fetchNativeHeartbeatHistory(conversationId);
                if (latest.head != null && (previousHead == null
                        || !previousHead.equals(latest.head))) {
                    return latest;
                }
            } catch (Throwable error) {
                lastError = error;
            }
        }
        if (latest != null) return latest;
        throw lastError == null
                ? new IOException("DeepSeek history refresh failed") : lastError;
    }

    /** Persists the exact server IDs and visible parent chain through DeepSeek's own gm8 writer. */
    boolean persistNativeHeartbeatHistory(HookSessionManagement.NativeHeartbeatHistory history)
            throws Throwable {
        Object repository = HookAttachmentPipeline.liveFm8;
        ClassLoader cl = Main.hostClassLoader;
        if (history == null || repository == null || cl == null
                || history.cacheVersion == null) return false;
        ArrayList rows = new ArrayList(history.messages.size());
        for (Object message : history.messages) {
            if (message == null) continue;
            Method toRow = HostCompat.publicMessageMethod(message, "O");
            toRow.setAccessible(true);
            Object row = toRow.invoke(message);
            if (row != null) rows.add(row);
        }

        Class<?> metadataType = HostCompat.load(cl, "am8");
        Object insertedValue = MainReflectionSupport.fieldByName(history.session, "e");
        Object updatedValue = MainReflectionSupport.fieldByName(history.session, "f");
        double inserted = insertedValue instanceof Number
                ? ((Number) insertedValue).doubleValue() : 0D;
        double updated = updatedValue instanceof Number
                ? ((Number) updatedValue).doubleValue() : inserted;
        // am8 is a mutable WCDB entity. Its Kotlin constructor changed parameter ordering between
        // host branches, while the persisted fields a..k stayed stable. Populate the no-arg
        // entity by field name so a successful proactive generation can never be lost merely
        // because a Boolean/Integer constructor slot moved.
        Constructor<?> metadataConstructor = metadataType.getDeclaredConstructor();
        metadataConstructor.setAccessible(true);
        Object metadata = metadataConstructor.newInstance();
        if (!Main.forceSetObjectField(metadata, "a", history.sid)
                || !Main.forceSetObjectField(metadata, "d", history.cacheVersion)
                || !Main.forceSetObjectField(metadata, "f", Double.valueOf(inserted))
                || !Main.forceSetObjectField(metadata, "g", Double.valueOf(updated))
                || !Main.forceSetObjectField(metadata, "h", history.head)) {
            throw new IOException("DeepSeek session metadata fields are incompatible");
        }
        Main.forceSetObjectField(metadata, "b", MainReflectionSupport.fieldByName(history.session, "b"));
        Main.forceSetObjectField(metadata, "c", MainReflectionSupport.fieldByName(history.session, "g"));
        Main.forceSetObjectField(metadata, "e", history.cacheReset);
        Main.forceSetObjectField(metadata, "i", Integer.valueOf(5));
        Main.forceSetObjectField(metadata, "j",
                Boolean.valueOf(Boolean.TRUE.equals(MainReflectionSupport.fieldByName(history.session, "h"))));
        Main.forceSetObjectField(metadata, "k", history.nativeModel);

        Method writer = null;
        for (Method method : repository.getClass().getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if ("b".equals(method.getName()) && types.length == 7
                    && types[0] == String.class && types[1] == int.class
                    && List.class.isAssignableFrom(types[4])) {
                writer = method;
                break;
            }
        }
        if (writer == null) throw new NoSuchMethodException("DeepSeek history writer");
        writer.setAccessible(true);
        writer.invoke(repository, history.sid, history.cacheVersion.intValue(),
                history.cacheReset, history.head, rows,
                MainReflectionSupport.fieldByName(history.response, "c"), metadata);
        return true;
    }

    /** Applies the refreshed messages on the main thread so an already-open chat updates at once. */
    boolean applyNativeHeartbeatHistory(final HookSessionManagement.NativeHeartbeatHistory history) {
        if (history == null) return false;
        final ArrayList<Object> sessions = new ArrayList<>();
        java.util.IdentityHashMap<Object, Boolean> seen = new java.util.IdentityHashMap<>();
        Object directorySession = Main.findNativeSession(history.sid);
        if (directorySession != null) {
            sessions.add(directorySession);
            seen.put(directorySession, Boolean.TRUE);
        }
        WeakReference<Object> activeReference = HookSessionManagement.ACTIVE_CHAT_SESSIONS.get(history.sid);
        Object activeSession = activeReference == null ? null : activeReference.get();
        if (activeReference != null && activeSession == null) {
            HookSessionManagement.ACTIVE_CHAT_SESSIONS.remove(history.sid, activeReference);
        } else if (activeSession != null && !seen.containsKey(activeSession)) {
            sessions.add(activeSession);
            seen.put(activeSession, Boolean.TRUE);
        }
        if (sessions.isEmpty()) return false;
        final AtomicInteger applied = new AtomicInteger();
        final CountDownLatch completed = new CountDownLatch(1);
        Runnable update = new Runnable() {
            @Override public void run() {
                try {
                    for (Object session : sessions) {
                        if (HookSessionManagement.mergeNativeHeartbeatHistoryIntoSession(
                                history, session)) {
                            applied.incrementAndGet();
                        }
                    }
                } finally {
                    completed.countDown();
                }
            }
        };
        Handler handler = Main.currentMainHandler();
        if (Looper.myLooper() == Looper.getMainLooper() || handler == null) {
            update.run();
        } else {
            handler.post(update);
            try {
                completed.await(4L, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        return applied.get() > 0;
    }

    private static String normalizeReminderTask(String value) {
        return HeartbeatToolProtocol.cleanInstruction(value);
    }

    /**
     * Local command seam for repeatable device tests. It is consumed by the already hooked
     * DeepSeek receiver and accepts the same narrow, validated call schema as model output.
     */
    private static void handleAgentCommand(Context context, String json) {
        if (json == null || json.trim().length() == 0 || json.length() > 48 * 1024) {
            Main.log("ignored empty/oversized Agent command");
            return;
        }
        try {
            JSONObject root = new JSONObject(json);
            String visibleText = root.optString("visible_text", "").trim();
            String scope = HeartbeatToolProtocol.cleanScope(
                    root.optString("scope", ""));
            if (scope.length() == 0) {
                scope = currentAgentConversationScope();
            }
            if (visibleText.length() > 0) {
                if (visibleText.length() > 4000) {
                    visibleText = visibleText.substring(0, 4000);
                }
                Main.queueVisibleAgentAnswer(context, scope, visibleText);
                Main.log("Agent command queued visible text sid=" + scope);
                return;
            }
            JSONArray commandCalls = root.optJSONArray("calls");
            JSONObject call = root.optJSONObject("call");
            if (call == null && commandCalls == null) call = root;
            if (scope.length() == 0) {
                // Preview-only scope makes the same command seam usable on a blank new-chat
                // screen. Real model output is still required to carry its server conversation.
                scope = "agent-command-preview";
            }
            JSONObject envelope = new JSONObject();
            if (commandCalls != null) {
                JSONArray normalized = new JSONArray();
                int count = Math.min(4, commandCalls.length());
                long stamp = System.currentTimeMillis();
                for (int index = 0; index < count; index++) {
                    JSONObject item = commandCalls.optJSONObject(index);
                    if (item == null) continue;
                    if (!item.has("scope")) item.put("scope", scope);
                    if (!item.has("id")) item.put("id", "cmd_"
                            + Long.toHexString(stamp) + "_" + index);
                    normalized.put(item);
                }
                envelope.put("calls", normalized);
            } else {
                if (!call.has("scope")) call.put("scope", scope);
                if (!call.has("id")) {
                    call.put("id", "cmd_" + Long.toHexString(
                            System.currentTimeMillis()));
                }
                envelope.put("call", call);
            }
            String framed = HeartbeatToolProtocol.CONTROL_START
                    + "\n" + envelope.toString() + "\n"
                    + HeartbeatToolProtocol.CONTROL_END;
            HeartbeatToolProtocol.Result parsed =
                    HeartbeatToolProtocol.parse(framed);
            int completed = Main.executeHeartbeatToolCalls(
                    context, parsed.calls, true);
            Main.log("Agent command executed calls=" + parsed.calls.size()
                    + " completed=" + completed + " sid=" + scope);
        } catch (Throwable error) {
            Main.log("Agent command failed: " + Main.safeThrowableMessage(error));
        }
    }

    private static String currentAgentConversationScope() {
        String latest = HeartbeatToolProtocol.cleanScope(
                HookChatPipeline.lastInteractiveConversationId);
        if (latest.length() > 0) return latest;
        String sidebar = HeartbeatToolProtocol.cleanScope(HookSessionManagement.sidebarCurrentSid);
        if (sidebar.length() > 0) return sidebar;
        for (Map.Entry<String, WeakReference<Object>> entry
                : HookSessionManagement.ACTIVE_CHAT_VIEW_MODELS.entrySet()) {
            WeakReference<Object> reference = entry.getValue();
            Object viewModel = reference == null ? null : reference.get();
            if (viewModel == null) continue;
            Object session = nativeUiChatSession(viewModel);
            String scope = HeartbeatToolProtocol.cleanScope(
                    String.valueOf(Main.readHostField(session, "a")));
            if (scope.length() > 0 && scope.equals(entry.getKey())) return scope;
        }
        return "";
    }

    static String agentDelayKey(String scope, String id) {
        return HeartbeatToolProtocol.cleanScope(scope) + "|" + String.valueOf(id);
    }

    private static void handleAgentDelayComplete(Context context, Intent intent) {
        String id = intent == null ? "" : intent.getStringExtra(
                AgentDelayReceiver.EXTRA_ID);
        String scope = intent == null ? "" : intent.getStringExtra(
                AgentDelayReceiver.EXTRA_SCOPE);
        long requested = intent == null ? 0L : intent.getLongExtra(
                AgentDelayReceiver.EXTRA_DURATION_MS, 0L);
        long started = intent == null ? 0L : intent.getLongExtra(
                AgentDelayReceiver.EXTRA_STARTED_AT, 0L);
        String safeScope = HeartbeatToolProtocol.cleanScope(scope);
        if (id == null || id.length() == 0 || safeScope.length() == 0
                || requested < 1L || requested > 604_800_000L) {
            Main.log("ignored malformed Agent delay completion");
            return;
        }
        boolean claimed = false;
        for (AgentRunStore.Record record : AgentRunStore.snapshot()) {
            if (safeScope.equals(record.scope) && id.equals(record.callId)
                    && HeartbeatToolProtocol.TOOL_DELAY.equals(record.tool)
                    && AgentRunStore.STATE_EXECUTING.equals(record.state)) {
                claimed = true;
                break;
            }
        }
        if (!claimed) {
            Main.log("ignored unclaimed or duplicate Agent delay completion id=" + id);
            return;
        }
        HeartbeatToolProtocol.ToolCall call = new HeartbeatToolProtocol.ToolCall(
                id, HeartbeatToolProtocol.TOOL_DELAY, safeScope,
                "", "", 0, "", "", -1, -1, -1, -1,
                (int) requested);
        long elapsed = started > 0L
                ? Math.max(0L, System.currentTimeMillis() - started) : requested;
        String output = "requested_ms=" + requested + "; elapsed_ms=" + elapsed;
        String detail = UiLanguage.text(context,
                "延迟结束，可继续下一步",
                "Delay completed; the next step may continue");
        AgentDeviceBridge.ToolResult result = new AgentDeviceBridge.ToolResult(
                true, 0, output, detail, "utf-8", false);
        AgentStepResult step = AGENT_DELAY_STEPS.remove(
                agentDelayKey(safeScope, id));
        if (step != null) step.addResult(call, result);
        else queueHiddenAgentToolResult(context, call, result);
        Main.log("Agent durable delay completed id=" + id
                + " requested_ms=" + requested + " elapsed_ms=" + elapsed);
    }

    static void queueHiddenAgentToolResult(
            Context context, HeartbeatToolProtocol.ToolCall call,
            AgentDeviceBridge.ToolResult result) {
        if (call == null || result == null) return;
        AgentToolTraceStore.complete(call, result);
        String scope = HeartbeatToolProtocol.cleanScope(call.scope);
        if (scope.length() == 0) return;
        String event = HeartbeatToolProtocol.toolResultEvent(
                call, result.success, result.exitCode, result.output,
                result.detail, result.encoding, result.truncated);
        if (event.length() == 0) return;
        String outboxId = AgentRunStore.queueResult(
                call, result.success, event, result.detail);
        Main.queueHiddenAgentEvent(context, scope, event, outboxId);
    }

    /** Collects up to four independent calls and resumes the model exactly once. */
    static final class AgentBatchResult {
        private static final long PARTIAL_WAIT_MS = 6_000L;
        final Context context;
        final String scope;
        private final ArrayList<HeartbeatToolProtocol.ToolCall> calls = new ArrayList<>();
        private final HashMap<String, HeartbeatToolProtocol.ToolResultItem> results =
                new HashMap<>();
        private boolean sealed;
        private boolean initialDelivered;
        private boolean timeoutScheduled;
        private boolean containsMcp;

        AgentBatchResult(Context context, String scope) {
            this.context = context;
            this.scope = scope;
        }

        synchronized void register(HeartbeatToolProtocol.ToolCall call) {
            if (sealed || call == null || calls.size() >= 4) return;
            calls.add(call);
            if (AgentMcpManager.isDynamicTool(call.tool)) containsMcp = true;
        }

        synchronized void addResult(HeartbeatToolProtocol.ToolCall call,
                                    AgentDeviceBridge.ToolResult result) {
            if (call == null || result == null) return;
            boolean registered = false;
            for (HeartbeatToolProtocol.ToolCall expected : calls) {
                if (expected != null && expected.id.equals(call.id)) {
                    registered = true;
                    break;
                }
            }
            if (!registered || results.containsKey(call.id)) return;
            HeartbeatToolProtocol.ToolResultItem item =
                    new HeartbeatToolProtocol.ToolResultItem(
                    call, result.success, result.exitCode, result.output,
                    result.detail, result.encoding, result.truncated);
            results.put(call.id, item);
            if (initialDelivered) {
                deliverLate(item);
                return;
            }
            deliverIfReady();
        }

        synchronized void seal() {
            sealed = true;
            deliverIfReady();
            // MCP is all-settled: never send a synthetic partial result while another MCP call
            // from this assistant turn is still running. Per-call terminal timeouts still make
            // the barrier finite if a server never answers.
            if (!containsMcp && !initialDelivered
                    && !timeoutScheduled && !calls.isEmpty()) {
                timeoutScheduled = true;
                Handler handler = Main.currentMainHandler();
                if (handler == null) handler = new Handler(Looper.getMainLooper());
                handler.postDelayed(new Runnable() {
                    @Override public void run() {
                        synchronized (AgentBatchResult.this) {
                            deliverPartialIfNeeded();
                        }
                    }
                }, PARTIAL_WAIT_MS);
            }
        }

        private void deliverIfReady() {
            if (!sealed || initialDelivered || calls.isEmpty()
                    || results.size() < calls.size()) return;
            ArrayList<HeartbeatToolProtocol.ToolResultItem> ordered = new ArrayList<>();
            boolean allSuccess = true;
            boolean allSearch = true;
            boolean anySuccess = false;
            for (HeartbeatToolProtocol.ToolCall call : calls) {
                HeartbeatToolProtocol.ToolResultItem item = results.get(call.id);
                if (item == null) return;
                ordered.add(item);
                allSuccess &= item.success;
                anySuccess |= item.success;
                allSearch &= HeartbeatToolProtocol.TOOL_SEARCH_WEB.equals(call.tool);
            }
            // Parallel web search is an all-settled operation. One useful result is enough for
            // the model to continue; preserve each failed query in the event without marking
            // the whole batch as failed and prompting unnecessary retries.
            boolean batchSuccess = allSearch ? anySuccess : allSuccess;
            String event = HeartbeatToolProtocol.toolResultBatchEvent(ordered);
            if (event.length() == 0) return;
            initialDelivered = true;
            HeartbeatToolProtocol.ToolCall primary = calls.get(0);
            String outboxId = AgentRunStore.queueResult(
                    primary, batchSuccess, event,
                    "Batched " + calls.size() + " tool results");
            for (int index = 1; index < calls.size(); index++) {
                AgentRunStore.complete(calls.get(index),
                        "Result delivered in batch " + primary.id);
            }
            Main.queueHiddenAgentEvent(context, scope, event, outboxId);
            Main.log("Agent batch result queued sid=" + scope
                    + " calls=" + calls.size() + " ok=" + batchSuccess);
        }

        private void deliverPartialIfNeeded() {
            if (!sealed || initialDelivered || calls.isEmpty()
                    || results.size() >= calls.size()) {
                deliverIfReady();
                return;
            }
            ArrayList<HeartbeatToolProtocol.ToolResultItem> snapshot = new ArrayList<>();
            int pending = 0;
            for (HeartbeatToolProtocol.ToolCall call : calls) {
                HeartbeatToolProtocol.ToolResultItem item = results.get(call.id);
                if (item == null) {
                    pending++;
                    item = new HeartbeatToolProtocol.ToolResultItem(
                            call, false, -1, "", "Waiting for tool response",
                            "utf-8", false, true);
                }
                snapshot.add(item);
            }
            String event = HeartbeatToolProtocol.toolResultBatchEvent(snapshot);
            if (event.length() == 0) return;
            initialDelivered = true;
            // This snapshot is informational and contains unfinished calls, so it must not mark
            // any pending call as durably delivered.  Completed calls are retired explicitly;
            // every late result gets its own durable outbox event below.
            for (HeartbeatToolProtocol.ToolCall call : calls) {
                if (results.containsKey(call.id)) {
                    AgentRunStore.complete(call, "Result delivered in partial batch");
                }
            }
            Main.queueHiddenAgentEvent(context, scope, event, "");
            Main.log("Agent partial batch queued sid=" + scope
                    + " calls=" + calls.size() + " pending=" + pending);
        }

        private void deliverLate(HeartbeatToolProtocol.ToolResultItem item) {
            if (item == null || item.call == null) return;
            String event = HeartbeatToolProtocol.toolResultEvent(
                    item.call, item.success, item.exitCode, item.output,
                    item.detail, item.encoding, item.truncated);
            if (event.length() == 0) return;
            String outboxId = AgentRunStore.queueResult(
                    item.call, item.success, event, item.detail);
            Main.queueHiddenAgentEvent(context, scope, event, outboxId);
            Main.log("Agent late batch result queued sid=" + scope
                    + " tool=" + item.call.tool + " id=" + item.call.id
                    + " ok=" + item.success);
        }
    }

    /** Owns exactly one call until its result (or an attachment/visible answer) is delivered. */
    static final class AgentStepResult {
        private static final long DEFAULT_DEADLINE_MS = 90_000L;
        private static final long QUESTION_DEADLINE_MS = 30L * 60L * 1000L;

        final Context context;
        final String scope;
        private final HeartbeatToolProtocol.ToolCall call;
        private final AgentBatchResult batch;
        private boolean finished;

        AgentStepResult(Context context, String scope,
                        HeartbeatToolProtocol.ToolCall call) {
            this(context, scope, call, null);
        }

        AgentStepResult(Context context, String scope,
                        HeartbeatToolProtocol.ToolCall call,
                        AgentBatchResult batch) {
            this.context = context;
            this.scope = scope;
            this.call = call;
            this.batch = batch;
        }

        long deadlineMs() {
            if (call != null && HeartbeatToolProtocol.TOOL_ASK_USER.equals(call.tool)) {
                return QUESTION_DEADLINE_MS;
            }
            if (HostCompat.isV241() && call != null
                    && HeartbeatToolProtocol.TOOL_SHELL.equals(call.tool)
                    && AgentDeviceBridge.isLongWorkspaceCommandV241(call.command)) {
                return 22L * 60L * 1000L;
            }
            if (call != null && HeartbeatToolProtocol.TOOL_DELAY.equals(call.tool)) {
                return Math.max(DEFAULT_DEADLINE_MS,
                        (long) call.durationMs + 120_000L);
            }
            if (call != null && AgentMcpManager.isDynamicTool(call.tool)) {
                return 10L * 60L * 1000L;
            }
            return DEFAULT_DEADLINE_MS;
        }

        synchronized void addResult(HeartbeatToolProtocol.ToolCall call,
                                    AgentDeviceBridge.ToolResult result) {
            if (finished || call == null || call.id == null || result == null) return;
            if (this.call == null || !call.id.equals(this.call.id)) return;
            finished = true;
            AgentToolTraceStore.complete(this.call, result);
            if (HeartbeatToolProtocol.TOOL_DELAY.equals(this.call.tool)) {
                AGENT_DELAY_STEPS.remove(agentDelayKey(
                        this.call.scope, this.call.id), this);
            }
            if (batch != null) {
                batch.addResult(this.call, result);
                return;
            }
            String event = HeartbeatToolProtocol.toolResultEvent(
                    this.call, result.success, result.exitCode, result.output,
                    result.detail, result.encoding, result.truncated);
            if (event.length() == 0) return;
            String outboxId = AgentRunStore.queueResult(
                    this.call, result.success, event, result.detail);
            Main.queueHiddenAgentEvent(context, scope, event, outboxId);
            Main.log("Agent step result queued sid=" + scope
                    + " tool=" + this.call.tool + " id=" + this.call.id
                    + " ok=" + result.success);
        }

        synchronized void release(String callId) {
            if (finished || callId == null || call == null
                    || !callId.equals(call.id)) return;
            finished = true;
            AgentToolTraceStore.complete(call, new AgentDeviceBridge.ToolResult(
                    true, 0, "delivered_as_attachment_or_visible_answer",
                    "Delivered through the native chat composer", "utf-8", false));
            if (batch != null) {
                batch.addResult(call, new AgentDeviceBridge.ToolResult(
                        true, 0, "delivered_as_attachment_or_visible_answer",
                        "Delivered through the native chat composer",
                        "utf-8", false));
            }
            AgentRunStore.complete(
                    call, "Delivered as a native visible message or attachment");
        }

        /** A missing callback must become a real failure result instead of stalling the model. */
        synchronized void flushIfPending() {
            if (finished) return;
            AgentDeviceBridge.ToolResult timeout = new AgentDeviceBridge.ToolResult(
                    false, -2, "",
                    UiLanguage.text(context,
                            "工具等待结果超时",
                            "Timed out waiting for the tool result"),
                    "utf-8", false);
            addResult(call, timeout);
        }
    }

    static String normalizeProactiveMessage(String value) {
        if (value == null) return "";
        String out = value.trim();
        if (out.startsWith("\"") && out.endsWith("\"") && out.length() > 1) {
            out = out.substring(1, out.length() - 1).trim();
        }
        if (out.length() > 600) out = out.substring(0, 600).trim();
        return out;
    }

    private static String readHeartbeatHistory(String conversationId) {
        File file = heartbeatHistoryFile(conversationId);
        return file == null ? null : Main.readSmallText(file.getAbsolutePath());
    }

    private static File heartbeatHistoryFile(String conversationId) {
        String sid = HeartbeatToolProtocol.cleanScope(conversationId);
        if (sid.length() == 0) return null;
        String name = sid.matches("[A-Za-z0-9._-]{4,120}")
                ? sid : Integer.toHexString(sid.hashCode());
        return new File(PROACTIVE_HEARTBEAT_HISTORY_DIR, name + ".txt");
    }

    static void rememberProactiveMessage(
            String conversationId, String message) {
        try {
            File file = heartbeatHistoryFile(conversationId);
            if (file == null) return;
            String previous = Main.readSmallText(file.getAbsolutePath());
            String line = Main.TS.format(new Date()) + "  " + message;
            String next = previous == null || previous.length() == 0
                    ? line : previous + "\n" + line;
            if (next.length() > 6000) next = next.substring(next.length() - 6000);
            Main.overwriteTextFile(file.getAbsolutePath(), next);
        } catch (Throwable t) {
            Main.log("proactive heartbeat history write failed: " + t);
        }
    }

    private static String recentBoundConversationContext(String conversationId) {
        String sid = HeartbeatToolProtocol.cleanScope(conversationId);
        if (sid.length() == 0) return "";
        try {
            refreshNativeHistorySnapshot(sid);
            HistoryBridge.Snapshot snapshot = HistoryBridge.snapshot(sid);
            List<ChatEditorUi.Msg> thread = ChatEditorUi.loadSnapshotThread(snapshot);
            if (thread == null || thread.isEmpty()) return "";
            StringBuilder context = new StringBuilder();
            int start = Math.max(0, thread.size() - 12);
            for (int i = start; i < thread.size(); i++) {
                ChatEditorUi.Msg message = thread.get(i);
                if (message == null) continue;
                String body = HistoryBridge.stripInjectedSystemPrompts(message.body);
                body = HeartbeatToolProtocol.stripControlBlocks(body).trim();
                if (body.length() == 0) continue;
                if (body.length() > 1200) {
                    body = body.substring(body.length() - 1200);
                }
                context.append(body).append('\n');
            }
            String result = context.toString().trim();
            return result.length() <= 8000
                    ? result : result.substring(result.length() - 8000);
        } catch (Throwable t) {
            Main.log("bound heartbeat context read failed: " + Main.safeThrowableMessage(t));
            return "";
        }
    }

    static void dispatchProactiveHeartbeatResponse(
            Context context, String requestId, String message, boolean foreground,
            boolean taskReminder, String taskKind, String conversationId) {
        try {
            Intent response = new Intent(ProactiveHeartbeatReceiver.ACTION_RESPONSE);
            response.setClassName(Main.runtimeComponentPackage(),
                    ProactiveHeartbeatReceiver.class.getName());
            response.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            response.putExtra(ProactiveHeartbeatReceiver.EXTRA_TOKEN,
                    ProactiveHeartbeatReceiver.TOKEN);
            response.putExtra(ProactiveHeartbeatReceiver.EXTRA_REQUEST_ID, requestId);
            response.putExtra(ProactiveHeartbeatReceiver.EXTRA_MESSAGE, message);
            response.putExtra(ProactiveHeartbeatReceiver.EXTRA_FOREGROUND, foreground);
            response.putExtra(ProactiveHeartbeatReceiver.EXTRA_TASK_REMINDER, taskReminder);
            response.putExtra(ProactiveHeartbeatReceiver.EXTRA_TASK_KIND, taskKind);
            response.putExtra(ProactiveHeartbeatReceiver.EXTRA_CONVERSATION_ID,
                    HeartbeatToolProtocol.cleanScope(conversationId));
            context.sendBroadcast(response);
        } catch (Throwable t) {
            Main.log("proactive heartbeat response dispatch failed: " + t);
        }
    }

    static void activateOptionalHooksNow() {
        Main module = Main.MODULE;
        if (module == null || Main.hostClassLoader == null) return;
        module.ensureOptionalHooksForEnabledFeatures(Main.hostClassLoader);
    }

    static void refreshNativeHistorySnapshot(String sid) {
        try { HistoryBridge.processNativeSession(HookSessionManagement.NATIVE_SESSION_LIST, sid); }
        catch (Throwable t) { Main.log("refresh native history snapshot failed: " + t); }
    }

    z2.CompletionResult executeLocalApiCompletion(
            z2.CompletionRequest request,
            z2.DeltaSink sink) throws Exception {
        if (request != null && request.knownToolCalls != null
                && !request.knownToolCalls.isEmpty()) {
            Main.log("local-api incoming known_tool_calls=" + request.knownToolCalls);
        }
        // ToolCall.scope="" causes executeHeartbeatToolCalls to silently skip every tool call.
        // z1 only sets clientSessionScope when the client provides an explicit session id; ensure
        // a stable non-empty scope so that local-API-originated tool calls are never dropped.
        if (request != null
                && (request.clientSessionScope == null
                        || request.clientSessionScope.length() == 0)) {
            request = request.withClientSessionScope(
                    "local-api-" + request.requestId);
        }
        try {
            Class<?> cls = z14.payloadClass(Main.hostApplicationContext, "com.smallhairball.deepx.z18");
            java.lang.reflect.Method m = cls == null ? null
                    : cls.getDeclaredMethod("executeLocalApiCompletion",
                            z2.CompletionRequest.class, z2.DeltaSink.class);
            if (m == null) {
                throw new z2.GatewayException(503, "engine_missing", "server_error",
                        "Local API execution engine unavailable");
            }
            m.setAccessible(true);
            z2.CompletionResult result = (z2.CompletionResult) m.invoke(null, request, sink);
            if (result != null && result.hasToolCalls()) {
                StringBuilder sb = new StringBuilder("local-api tool_calls=")
                        .append(result.toolCalls.size());
                for (Object tc : result.toolCalls) {
                    sb.append(" [").append(tc).append("]");
                }
                Main.log(sb.toString());
            }
            return result;
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof z2.GatewayException) throw (z2.GatewayException) cause;
            if (cause instanceof Exception) throw (Exception) cause;
            throw new z2.GatewayException(502, "engine_failed", "server_error",
                    "Local API execution engine failed: " + Main.safeThrowableMessage(cause));
        } catch (z2.GatewayException e) {
            throw e;
        } catch (Throwable e) {
            throw new z2.GatewayException(502, "engine_failed", "server_error",
                    "Local API execution engine failed: " + Main.safeThrowableMessage(e));
        }
    }

    /**
     * Binds the native request header builder before Ktor's suspend token lookup. The binding is
     * stored on the builder itself (weakly), so a coroutine resuming on another thread keeps the
     * chosen account without a process-wide header override.
     */
    void hookLocalApiAccountRouting(final ClassLoader loader) {
        if (!BuildInfo.PROTECTED_BUILD || loader == null) return;
        if (!HostCompat.isV241()) {
            Main.MODULE.hookLegacyLocalApiAccountRouting(loader);
            return;
        }
        String authOwner = HostCompat.localApiAuthInterceptorClass();
        String headerOwner = HostCompat.localApiHeaderBuilderClass();
        String setterName = HostCompat.localApiHeaderSetterMethod();
        int marked = 0;
        int replaced = 0;
        try {
            Class<?> owner = Class.forName(authOwner, false, loader);
            Class<?> requestBuilderType = null;
            for (Method method : owner.getDeclaredMethods()) {
                if (!"a".equals(method.getName()) || method.getParameterTypes().length != 2) {
                    continue;
                }
                requestBuilderType = method.getParameterTypes()[0];
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object request = chain.getArg(0);
                        Object builder = Main.readHostField(request, "c");
                        z12.Route route = tlLocalApiAccountRoute.get();
                        if (route == null && builder != null) {
                            route = V241_LOCAL_API_ROUTED_HEADER_BUILDERS.get(builder);
                        }
                        if (route != null) {
                            if (builder != null) {
                                V241_LOCAL_API_ROUTED_HEADER_BUILDERS.put(builder, route);
                                // Do not enter the host's cached, suspend credential provider for
                                // a routed request.  On code257 it can resume with the process
                                // account after this request has already been bound to another
                                // account.  Replacing the inherited header setter afterwards
                                // produced a syntactically correct header, but Ktor's create-call
                                // remained pending until its five-second timeout.  Install the
                                // selected credential at the interceptor boundary instead and
                                // complete the interceptor with the host's Kotlin Unit singleton.
                                Method remove = null;
                                Method put = null;
                                for (Class<?> type = builder.getClass();
                                        type != null && type != Object.class;
                                        type = type.getSuperclass()) {
                                    for (Method candidate : type.getDeclaredMethods()) {
                                        Class<?>[] p = candidate.getParameterTypes();
                                        if ("V0".equals(candidate.getName())
                                                && p.length == 1 && p[0] == String.class) {
                                            remove = candidate;
                                        } else if (HostCompat.localApiHeaderSetterMethod()
                                                .equals(candidate.getName())
                                                && p.length == 2 && p[0] == String.class
                                                && p[1] == String.class) {
                                            put = candidate;
                                        }
                                    }
                                }
                                if (put == null) {
                                    throw new NoSuchMethodException("routed Authorization setter");
                                }
                                if (remove != null) {
                                    remove.setAccessible(true);
                                    remove.invoke(builder, "Authorization");
                                    // The host cookie jar belongs to the process account. Keeping
                                    // those cookies while swapping only Authorization makes the
                                    // PoW challenge account-dependent: the process account works,
                                    // while otherwise valid routed accounts receive
                                    // INVALID_POW_RESPONSE. Routed calls are bearer-authenticated,
                                    // so strip the stale account cookie at the same boundary.
                                    remove.invoke(builder, "Cookie");
                                }
                                put.setAccessible(true);
                                put.invoke(builder, "Authorization", "Bearer " + route.token);
                                Class<?> unit = Class.forName(
                                        HostCompat.unitClass(), false, loader);
                                Field singleton = unit.getDeclaredField(HostCompat.unitField());
                                singleton.setAccessible(true);
                                return singleton.get(null);
                            }
                        }
                        return chain.proceed();
                    }
                });
                marked++;
            }
            // Completion requests are lazy Flows on code257: l14 and its header builder are
            // created while the Local-API worker still owns the Route, but authentication runs
            // later on a coroutine thread.  Bind the builder at construction so the interceptor
            // above can recover the exact request account without a process-wide override.
            if (requestBuilderType != null) {
                for (Constructor<?> constructor : requestBuilderType.getDeclaredConstructors()) {
                    Main.MODULE.hook(constructor).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            Object result = chain.proceed();
                            z12.Route route = tlLocalApiAccountRoute.get();
                            Object request = chain.getThisObject();
                            Object builder = Main.readHostField(request, "c");
                            if (route != null && builder != null) {
                                V241_LOCAL_API_ROUTED_HEADER_BUILDERS.put(builder, route);
                            }
                            return result;
                        }
                    });
                }
                for (Method method : requestBuilderType.getDeclaredMethods()) {
                    if (!"b".equals(method.getName())
                            || method.getParameterTypes().length != 0) continue;
                    Main.MODULE.hook(method).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            Object request = chain.getThisObject();
                            Object builder = Main.readHostField(request, "c");
                            Object rawHeaders = Main.readHostField(builder, "b");
                            Object rawPow = rawHeaders instanceof Map
                                    ? ((Map<?, ?>) rawHeaders).get("X-DS-PoW-Response") : null;
                            String pow = null;
                            if (rawPow instanceof List && !((List<?>) rawPow).isEmpty()) {
                                pow = String.valueOf(((List<?>) rawPow).get(0));
                            } else if (rawPow != null) {
                                pow = String.valueOf(rawPow);
                            }
                            z12.Route route = pow == null ? null
                                    : LOCAL_API_ROUTED_POW.remove(pow);
                            if (route != null && builder != null) {
                                V241_LOCAL_API_ROUTED_HEADER_BUILDERS.put(builder, route);
                                Method put = null;
                                for (Class<?> type = builder.getClass();
                                        type != null && type != Object.class;
                                        type = type.getSuperclass()) {
                                    for (Method candidate : type.getDeclaredMethods()) {
                                        Class<?>[] types = candidate.getParameterTypes();
                                        if (HostCompat.localApiHeaderSetterMethod()
                                                .equals(candidate.getName())
                                                && types.length == 2
                                                && types[0] == String.class
                                                && types[1] == String.class) put = candidate;
                                    }
                                }
                                if (put == null) throw new NoSuchMethodException(
                                        "final completion Authorization setter");
                                put.setAccessible(true);
                                put.invoke(builder, "Authorization", "Bearer " + route.token);
                            }
                            return chain.proceed();
                        }
                    });
                }
            }
            if (HostCompat.isV241()) {
                try {
                    hookV241LocalApiUploadCoroutineRouting(loader);
                } catch (Throwable uploadRouteError) {
                    Main.log("code257 context upload route hook unavailable: "
                            + Main.safeThrowableMessage(uploadRouteError));
                }
                Class<?> completionFactory = Class.forName("fu0", false, loader);
                for (Method method : completionFactory.getDeclaredMethods()) {
                    Class<?>[] p = method.getParameterTypes();
                    if (!"e".equals(method.getName()) || p.length != 2) continue;
                    Main.MODULE.hook(method).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            z12.Route route = LOCAL_API_ROUTED_NATIVE_REQUESTS.get(
                                    chain.getArg(0));
                            if (route == null) return chain.proceed();
                            z12.Route previous = tlLocalApiAccountRoute.get();
                            tlLocalApiAccountRoute.set(route);
                            try {
                                return chain.proceed();
                            } finally {
                                if (previous == null) tlLocalApiAccountRoute.remove();
                                else tlLocalApiAccountRoute.set(previous);
                            }
                        }
                    });
                }
                Class<?> requestHeaders = Class.forName("s95", false, loader);
                for (Method method : requestHeaders.getDeclaredMethods()) {
                    Class<?>[] p = method.getParameterTypes();
                    if (!"y".equals(method.getName()) || !Modifier.isStatic(method.getModifiers())
                            || p.length != 3 || p[1] != String.class
                            || p[2] != String.class) continue;
                    Main.MODULE.hook(method).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            if ("X-DS-PoW-Response".equals(chain.getArg(1))) {
                                String pow = (String) chain.getArg(2);
                                z12.Route route = pow == null ? null
                                        : LOCAL_API_ROUTED_POW.remove(pow);
                                Object request = chain.getArg(0);
                                Object builder = Main.readHostField(request, "c");
                                if (route != null && builder != null) {
                                    V241_LOCAL_API_ROUTED_HEADER_BUILDERS.put(builder, route);
                                    Method remove = null;
                                    Method put = null;
                                    for (Class<?> type = builder.getClass();
                                            type != null && type != Object.class;
                                            type = type.getSuperclass()) {
                                        for (Method candidate : type.getDeclaredMethods()) {
                                            Class<?>[] types = candidate.getParameterTypes();
                                            if ("V0".equals(candidate.getName())
                                                    && types.length == 1
                                                    && types[0] == String.class) {
                                                remove = candidate;
                                            } else if (HostCompat.localApiHeaderSetterMethod()
                                                    .equals(candidate.getName())
                                                    && types.length == 2
                                                    && types[0] == String.class
                                                    && types[1] == String.class) {
                                                put = candidate;
                                            }
                                        }
                                    }
                                    if (put == null) {
                                        throw new NoSuchMethodException(
                                                "completion Authorization setter");
                                    }
                                    if (remove != null) {
                                        remove.setAccessible(true);
                                        remove.invoke(builder, "Authorization");
                                    }
                                    put.setAccessible(true);
                                    put.invoke(builder, "Authorization",
                                            "Bearer " + route.token);
                                }
                            }
                            return chain.proceed();
                        }
                    });
                }
            }
        } catch (Throwable error) {
            Main.log("local API account auth marker unavailable " + authOwner + ": "
                    + Main.safeThrowableMessage(error));
        }
        try {
            Class<?> builder = Class.forName(headerOwner, false, loader);
            // Ktor's concrete header builder validates names/values but inherits l0(String,
            // String) from its abstract base on 2.4.1. getDeclaredMethods() therefore reported
            // header=0 even though JADX showed gv3.l0 at the call site. Walk the hierarchy and
            // hook the actual declaring Member; invokevirtual dispatch reaches this same method.
            for (Class<?> type = builder; type != null && type != Object.class;
                    type = type.getSuperclass()) {
                for (Method method : type.getDeclaredMethods()) {
                    Class<?>[] parameters = method.getParameterTypes();
                    if (!setterName.equals(method.getName()) || parameters.length != 2
                            || parameters[0] != String.class || parameters[1] != String.class) {
                        continue;
                    }
                    Main.MODULE.hook(method).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            Object key = chain.getArg(0);
                            z12.Route route = V241_LOCAL_API_ROUTED_HEADER_BUILDERS.get(
                                    chain.getThisObject());
                            if ("X-DS-PoW-Response".equals(key)) {
                                Object rawPow = chain.getArg(1);
                                z12.Route completionRoute = rawPow == null ? null
                                        : LOCAL_API_ROUTED_POW.remove(String.valueOf(rawPow));
                                if (completionRoute != null) {
                                    Object builder = chain.getThisObject();
                                    V241_LOCAL_API_ROUTED_HEADER_BUILDERS.put(
                                            builder, completionRoute);
                                    for (Class<?> owner = builder.getClass();
                                            owner != null && owner != Object.class;
                                            owner = owner.getSuperclass()) {
                                        for (Method candidate : owner.getDeclaredMethods()) {
                                            Class<?>[] p = candidate.getParameterTypes();
                                            if ("V0".equals(candidate.getName())
                                                    && p.length == 1
                                                    && p[0] == String.class) {
                                                candidate.setAccessible(true);
                                                candidate.invoke(builder, "Cookie");
                                            }
                                        }
                                    }
                                    method.setAccessible(true);
                                    method.invoke(builder, "Authorization",
                                            "Bearer " + completionRoute.token);
                                }
                                return chain.proceed();
                            }
                            if (route == null || !"Authorization".equals(key)) {
                                return chain.proceed();
                            }
                            java.util.List<Object> args = new ArrayList<Object>(chain.getArgs());
                            args.set(1, "Bearer " + route.token);
                            z13.diagnostic("ACCOUNT_HEADER_APPLIED ns="
                                    + route.sessionNamespace);
                            return chain.proceed(args.toArray(new Object[args.size()]));
                        }
                    });
                    replaced++;
                }
            }
        } catch (Throwable error) {
            Main.log("local API account header hook unavailable " + headerOwner + ": "
                    + Main.safeThrowableMessage(error));
        }
        String ready = "ROUTING_HOOK_READY auth=" + marked + " header=" + replaced
                + " generation=" + HostCompat.generationName();
        Main.log(ready);
        z13.diagnostic(ready);
    }

    /**
     * Exact mainland code257 adapter for g71.t's asynchronous attachment coroutine. The initial
     * rz lambda is created while uploadLocalApiContextFile has the selected route installed; its
     * w()/y() calls may run on arbitrary coroutine threads, so carry the route on the continuation
     * object rather than relying on a process-wide credential override.
     */
    private void hookV241LocalApiUploadCoroutineRouting(final ClassLoader loader)
            throws Throwable {
        if (!HostCompat.isV241()) return;
        Class<?> uploadCoroutine = Class.forName("rz", false, loader);
        int constructors = 0;
        int resumes = 0;
        for (Constructor<?> constructor : uploadCoroutine.getDeclaredConstructors()) {
            Class<?>[] p = constructor.getParameterTypes();
            if (p.length == 0 || p[p.length - 1] != int.class) continue;
            Main.MODULE.hook(constructor).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    Object result = chain.proceed();
                    java.util.List<Object> args = chain.getArgs();
                    Object discriminator = args.isEmpty() ? null : args.get(args.size() - 1);
                    z12.Route route = tlLocalApiAccountRoute.get();
                    if (route != null && discriminator instanceof Number
                            && ((Number) discriminator).intValue() == 5) {
                        V241_LOCAL_API_ROUTED_UPLOAD_COROUTINES.put(
                                chain.getThisObject(), route);
                    }
                    return result;
                }
            });
            constructors++;
        }
        for (Method method : uploadCoroutine.getDeclaredMethods()) {
            Class<?>[] p = method.getParameterTypes();
            boolean propagates = "w".equals(method.getName()) && p.length == 2;
            boolean resumesUpload = "y".equals(method.getName()) && p.length == 1;
            if (!propagates && !resumesUpload) continue;
            Main.MODULE.hook(method).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    z12.Route route = V241_LOCAL_API_ROUTED_UPLOAD_COROUTINES.get(
                            chain.getThisObject());
                    if (route == null) return chain.proceed();
                    z12.Route previous = tlLocalApiAccountRoute.get();
                    tlLocalApiAccountRoute.set(route);
                    try {
                        Object result = chain.proceed();
                        if (propagates && result != null
                                && method.getReturnType().isInstance(result)) {
                            V241_LOCAL_API_ROUTED_UPLOAD_COROUTINES.put(result, route);
                        }
                        return result;
                    } finally {
                        if (previous == null) tlLocalApiAccountRoute.remove();
                        else tlLocalApiAccountRoute.set(previous);
                    }
                }
            });
            resumes++;
        }
        // q71 is the code257 multipart upload coroutine created after ly0.a's optional file-PoW
        // suspension. Re-enter the selected route only for our hidden context_*.txt attachment;
        // normal composer images/files and every pre-code257 host remain on their original path.
        Class<?> multipartUpload = Class.forName("q71", false, loader);
        for (Method method : multipartUpload.getDeclaredMethods()) {
            Class<?>[] p = method.getParameterTypes();
            if (!"y".equals(method.getName()) || p.length != 1) continue;
            Main.MODULE.hook(method).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    z12.Route route = V241_ACTIVE_ROUTED_CONTEXT_UPLOAD;
                    Object fileDescriptor = Main.readHostField(chain.getThisObject(), "j");
                    Object rawName = Main.readHostField(fileDescriptor, "a");
                    String fileName = rawName instanceof String ? (String) rawName : "";
                    if (route == null || !fileName.startsWith("context_")
                            || !Main.ACTIVE_HIDDEN_ATTACHMENT_NAMES.contains(fileName)) {
                        return chain.proceed();
                    }
                    z12.Route previous = tlLocalApiAccountRoute.get();
                    tlLocalApiAccountRoute.set(route);
                    try {
                        z13.diagnostic("V241_MULTIPART_ROUTE_APPLIED file=" + fileName
                                + " ns=" + route.sessionNamespace);
                        return chain.proceed();
                    } finally {
                        if (previous == null) tlLocalApiAccountRoute.remove();
                        else tlLocalApiAccountRoute.set(previous);
                    }
                }
            });
            resumes++;
        }
        // i8 discriminator 1 obtains the authed PoW challenge specifically for
        // /api/v0/file/upload_file. It runs before q71 and must use the same routed account;
        // otherwise the multipart request combines a routed bearer token with the process
        // account's PoW and waits until the native 60-second upload timeout.
        Class<?> filePowCoroutine = Class.forName("i8", false, loader);
        for (Method method : filePowCoroutine.getDeclaredMethods()) {
            Class<?>[] p = method.getParameterTypes();
            if (!"y".equals(method.getName()) || p.length != 1) continue;
            Main.MODULE.hook(method).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    z12.Route route = V241_ACTIVE_ROUTED_CONTEXT_UPLOAD;
                    Object rawDiscriminator = Main.readHostField(chain.getThisObject(), "e");
                    if (route == null || !(rawDiscriminator instanceof Number)
                            || ((Number) rawDiscriminator).intValue() != 1) {
                        return chain.proceed();
                    }
                    z12.Route previous = tlLocalApiAccountRoute.get();
                    tlLocalApiAccountRoute.set(route);
                    try {
                        return chain.proceed();
                    } finally {
                        if (previous == null) tlLocalApiAccountRoute.remove();
                        else tlLocalApiAccountRoute.set(previous);
                    }
                }
            });
            resumes++;
        }
        // g71.a suspends while it waits for the source-model attachment state. The actual
        // default->expert/vision request is created later by q63.a(ag3, Continuation) on another
        // coroutine thread, after the caller's ThreadLocal has disappeared. Bind that exact
        // code257 conversion endpoint at request creation; q63's unrelated APIs stay untouched.
        Class<?> fileModelApi = Class.forName("q63", false, loader);
        for (Method method : fileModelApi.getDeclaredMethods()) {
            Class<?>[] p = method.getParameterTypes();
            if (!"a".equals(method.getName()) || p.length != 2
                    || !"ag3".equals(p[0].getName())) continue;
            Main.MODULE.hook(method).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    z12.Route route = V241_ACTIVE_ROUTED_CONTEXT_UPLOAD;
                    if (route == null) return chain.proceed();
                    z12.Route previous = tlLocalApiAccountRoute.get();
                    tlLocalApiAccountRoute.set(route);
                    try {
                        z13.diagnostic("V241_FILE_MODEL_ROUTE_APPLIED ns="
                                + route.sessionNamespace);
                        return chain.proceed();
                    } finally {
                        if (previous == null) tlLocalApiAccountRoute.remove();
                        else tlLocalApiAccountRoute.set(previous);
                    }
                }
            });
            resumes++;
        }
        String ready = "V241_UPLOAD_ROUTE_HOOK_READY constructors=" + constructors
                + " resumes=" + resumes;
        Main.log(ready);
        z13.diagnostic(ready);
    }

    static void loadReusableApiSessionsLocked() {
        if (Main.localApiSessionsLoaded) return;
        Main.localApiSessionsLoaded = true;
        LOCAL_API_SESSIONS.clear();
        LOCAL_API_SESSION_LAST_USED.clear();
        LOCAL_API_RETIRED_SESSION_IDS.clear();
        Main.localApiSessionStatePersistedAt = System.currentTimeMillis();
        String text = Main.readSmallText(LOCAL_API_SESSION_FILE);
        if (text == null || text.length() == 0) {
            publishLocalApiInternalSessionIdsLocked();
            return;
        }
        try {
            JSONObject object = new JSONObject(text);
            JSONArray retired = object.optJSONArray(LOCAL_API_SESSION_RETIRED_KEY);
            if (retired != null) {
                for (int i = 0; i < retired.length(); i++) {
                    String sid = retired.optString(i, null);
                    if (HookSessionManagement.isUsableSessionId(sid)) LOCAL_API_RETIRED_SESSION_IDS.add(sid);
                }
            }
            JSONArray names = object.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String model = names.optString(i);
                    if (LOCAL_API_SESSION_META_KEY.equals(model)
                            || LOCAL_API_SESSION_RETIRED_KEY.equals(model)) continue;
                    String sid = object.optString(model, null);
                    if (HookSessionManagement.isUsableSessionId(sid)) {
                        // A native session id from an earlier process/account is not safe to
                        // reuse. Retire it immediately so the first request does not pay for a
                        // guaranteed invalid_api_session failure and retry.
                        LOCAL_API_RETIRED_SESSION_IDS.add(sid);
                    }
                }
            }
        } catch (Throwable t) {
            Main.log("[dq0] rs state ignored: " + Main.safeThrowableMessage(t));
        } finally {
            publishLocalApiInternalSessionIdsLocked();
        }
    }

    static void publishLocalApiInternalSessionIdsLocked() {
        HashSet<String> hidden = new HashSet<>(LOCAL_API_SESSIONS.values());
        hidden.addAll(LOCAL_API_RETIRED_SESSION_IDS);
        Main.localApiInternalSessionIds = Collections.unmodifiableSet(hidden);
    }

    static void persistReusableApiSessionsLocked() {
        try {
            JSONObject object = new JSONObject();
            JSONObject metadata = new JSONObject();
            for (Map.Entry<String, String> entry : LOCAL_API_SESSIONS.entrySet()) {
                if (HookSessionManagement.isUsableSessionId(entry.getValue())) {
                    object.put(entry.getKey(), entry.getValue());
                    metadata.put(entry.getKey(), reusableApiSessionLastUsedLocked(entry.getKey()));
                }
            }
            object.put(LOCAL_API_SESSION_META_KEY, metadata);
            JSONArray retired = new JSONArray();
            int retiredCount = 0;
            for (String sid : LOCAL_API_RETIRED_SESSION_IDS) {
                if (!HookSessionManagement.isUsableSessionId(sid)) continue;
                retired.put(sid);
                if (++retiredCount >= LOCAL_API_SESSION_MAX * 4) break;
            }
            object.put(LOCAL_API_SESSION_RETIRED_KEY, retired);
            Main.overwriteTextFile(LOCAL_API_SESSION_FILE, object.toString());
            Main.localApiSessionStatePersistedAt = System.currentTimeMillis();
        } catch (Throwable t) {
            Main.log("[dq0] rs state write failed: " + Main.safeThrowableMessage(t));
        }
    }

    private static long reusableApiSessionLastUsedLocked(String key) {
        Long value = LOCAL_API_SESSION_LAST_USED.get(key);
        return value == null || value.longValue() <= 0L
                ? System.currentTimeMillis() : value.longValue();
    }

    void deleteReusableApiSessions() {
        Object transport = Main.liveR92;
        ClassLoader cl = Main.hostClassLoader;
        if (transport == null || cl == null) return;
        boolean acquired = false;
        try {
            acquired = LOCAL_API_NATIVE_PERMITS.tryAcquire(
                    LOCAL_API_NATIVE_PERMIT_COUNT, 30, TimeUnit.SECONDS);
            if (!acquired) {
                Main.log("[dq0] cleanup skipped: native completion still active");
                return;
            }
            List<String> keys;
            synchronized (LOCAL_API_SESSION_LOCK) {
                loadReusableApiSessionsLocked();
                keys = new ArrayList<>(LOCAL_API_SESSIONS.keySet());
            }
            for (String key : keys) {
                String sid;
                synchronized (LOCAL_API_SESSION_LOCK) {
                    sid = LOCAL_API_SESSIONS.get(key);
                }
                if (!HookSessionManagement.isUsableSessionId(sid)) continue;
                boolean deleted = Main.MODULE.deleteThrowawaySession(cl, transport, sid);
                Main.log("[dq0] rs deleted=" + deleted);
                if (!deleted) continue;
                synchronized (LOCAL_API_SESSION_LOCK) {
                    if (sid.equals(LOCAL_API_SESSIONS.get(key))) {
                        LOCAL_API_RETIRED_SESSION_IDS.add(sid);
                        LOCAL_API_SESSIONS.remove(key);
                        LOCAL_API_SESSION_LAST_USED.remove(key);
                        publishLocalApiInternalSessionIdsLocked();
                        persistReusableApiSessionsLocked();
                    }
                }
            }
        } catch (Throwable t) {
            Main.log("[dq0] rs cleanup failed: " + Main.safeThrowableMessage(t));
        } finally {
            if (acquired) LOCAL_API_NATIVE_PERMITS.release(LOCAL_API_NATIVE_PERMIT_COUNT);
        }
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                out.append(Character.forDigit((b >> 4) & 0xf, 16))
                        .append(Character.forDigit(b & 0xf, 16));
            }
            return out.toString();
        } catch (Throwable ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }

    // cachedRunBlocking now lives on Main (see "Fields moved back" block).

    Object driveSuspend(ClassLoader cl, final Method m, final Object target, final Object[] preArgs) throws Throwable {
        Class<?> n02 = HostCompat.load(cl, "n02");
        Class<?> mb3 = HostCompat.load(cl, "mb3");
        // runBlocking(CoroutineContext, Function2)=静态 (n02,mb3)->Object。
        // build 间该 holder 类改名(2.2.1=t82 / 2.2.2=u82)，按候选名 + 结构签名兜底解析。
        Method K = Main.cachedRunBlocking;
        if (K == null) {
            String[] holders = HostCompat.isV230()
                    ? new String[]{HostCompat.name("u82")}
                    : new String[]{"u82", "t82", "v82", "s82", "w82"};
            for (String nm : holders) {
                try {
                    Class<?> holder = cl.loadClass(nm);
                    for (Method mm : holder.getDeclaredMethods()) {
                        Class<?>[] p = mm.getParameterTypes();
                        if (java.lang.reflect.Modifier.isStatic(mm.getModifiers())
                                && p.length == 2 && p[0] == n02 && p[1] == mb3) { K = mm; break; }
                    }
                } catch (Throwable ignored) {}
                if (K != null) { Main.extLog("[VP] runBlocking=" + nm + ".K"); break; }
            }
            if (K != null) Main.cachedRunBlocking = K;
        }
        if (K == null) { Main.extLog("[VP] runBlocking(n02,mb3) not found"); return null; }
        K.setAccessible(true);
        m.setAccessible(true);
        final Object ctx = Main.emptyContextProxy(cl, n02);
        InvocationHandler blockH = new InvocationHandler() {
            public Object invoke(Object proxy, Method mm, Object[] a) throws Throwable {
                if (MainReflectionSupport.isObjectMethod(mm)) return MainReflectionSupport.objectMethod(proxy, mm, a);
                Object cont = (a != null && a.length > 0) ? a[a.length - 1] : null;
                Object[] args = new Object[preArgs.length + 1];
                System.arraycopy(preArgs, 0, args, 0, preArgs.length);
                args[preArgs.length] = cont;
                try {
                    return m.invoke(target, args);
                } catch (java.lang.reflect.InvocationTargetException ite) {
                    throw (ite.getCause() != null ? ite.getCause() : ite);
                }
            }
        };
        Object block = Proxy.newProxyInstance(cl, new Class<?>[]{mb3}, blockH);
        return K.invoke(null, ctx, block);
    }
}