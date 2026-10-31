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

/** Hook group extracted from Main.java: AGENT category (see JavaHookGuide). */
final class HookAgentPipeline {
    static final HookAgentPipeline INSTANCE = new HookAgentPipeline();

    private static final Map<Object, HeartbeatResponseStream> HEARTBEAT_RESPONSE_STREAMS =
            Collections.synchronizedMap(
                    new WeakHashMap<Object, HeartbeatResponseStream>());

    private static final Map<Object, ThinkingAgentStream> THINKING_AGENT_STREAMS =
            Collections.synchronizedMap(
                    new WeakHashMap<Object, ThinkingAgentStream>());

    private static final AtomicInteger NATIVE_MARKDOWN_SANITIZE_HITS =
            new AtomicInteger();

    // Only RESPONSE fragment State instances are registered here. The c38 method hook below is
    // application-wide at the method level, but performs no parsing or mutation unless its exact
    // receiver is one of these weak keys.
    private static final Map<Object, WeakReference<Object>> HEARTBEAT_RESPONSE_STATES =
            Collections.synchronizedMap(
                    new WeakHashMap<Object, WeakReference<Object>>());

    private static final Map<Object, WeakReference<Object>> THINKING_AGENT_STATES =
            Collections.synchronizedMap(
                    new WeakHashMap<Object, WeakReference<Object>>());

    private static final long AGENT_TOOL_EXECUTION_CLAIM_TTL_MS =
            TimeUnit.MINUTES.toMillis(15);

    private static final ConcurrentHashMap<String, Long>
            AGENT_TOOL_EXECUTION_CLAIMS = new ConcurrentHashMap<>();

    private static final AtomicBoolean HEARTBEAT_STATUS_STYLE_HIT_LOGGED =
            new AtomicBoolean();

    private static final AtomicBoolean HEARTBEAT_STATUS_STYLE_ERROR_LOGGED =
            new AtomicBoolean();
    /** One-shot log for the visible-thread {@code <system>} prompt strip (avoids per-read spam). */
    private static final AtomicBoolean VISIBLE_USER_STRIP_LOGGED =
            new AtomicBoolean();

    // Writes produced by our privacy filter re-enter DeepSeek's StateFlow setter hook. Mark them
    // so the already-hidden opening brackets cannot be consumed a second time as fresh SSE data.
    private static final ThreadLocal<Boolean> tlHeartbeatInternalStateWrite =
            new ThreadLocal<>();

    private static final AtomicBoolean AGENT_TOOL_CANVAS_HOOK_INSTALLED =
            new AtomicBoolean(false);

    /**
     * Turns a recognized-but-invalid model call into a real private failure result. Nothing is
     * dispatched to the device: this only closes the Agent step and gives the model one chance to
     * correct its schema instead of silently ending after a false promise.
     */
    static void queueRejectedAgentToolResult(
            Context context, HeartbeatToolProtocol.RejectedCall rejected) {
        if (rejected == null || rejected.call == null) return;
        Context effective = context != null ? context : HookSessionManagement.currentHostContext();
        if (effective == null) return;
        HeartbeatToolProtocol.ToolCall call = rejected.call;
        if (!claimAgentToolExecution(call)) {
            Main.log("ignored duplicate rejected Agent call tool="
                    + call.tool + " id=" + call.id + " scope=" + call.scope);
            return;
        }
        String detail;
        if (HeartbeatToolProtocol.TOOL_RENDER_RICH_PANEL.equals(call.tool)) {
            detail = UiLanguage.text(effective,
                    "富视觉参数未通过校验，绘制没有执行。请换一个新的 id，使用更简单的合法"
                            + " panel 重试一次；pixel_art 的 pixels 应为等宽字符串数组，"
                            + "palette 应为颜色数组，透明像素使用点号。若重试仍失败，请直接"
                            + "告诉用户未完成，不能声称已经画好。",
                    "The rich-visual arguments failed validation and nothing was rendered. "
                            + "Retry once with a new id and a simpler valid panel. For pixel_art, "
                            + "use equal-width strings in pixels, an array for palette, and dots "
                            + "for transparency. If that retry fails, tell the user it did not "
                            + "complete; do not claim it was drawn.");
        } else {
            detail = UiLanguage.text(effective,
                    "工具参数未通过校验，操作没有执行。请按工具定义修正参数，换一个新的 id"
                            + " 后最多重试一次；再次失败时应如实说明，不能声称已经完成。",
                    "The tool arguments failed validation and the operation was not run. "
                            + "Correct the arguments according to the tool schema and retry at "
                            + "most once with a new id. If it fails again, report that honestly "
                            + "instead of claiming completion.");
        }
        HookAccountLoginSecurity.AgentStepResult step = new HookAccountLoginSecurity.AgentStepResult(effective, call.scope, call);
        queueSimpleAgentToolResult(
                effective, step, call, false, rejected.reason, detail);
        Main.log("Agent validation failure queued tool=" + call.tool
                + " id=" + call.id + " scope=" + call.scope
                + " reason=" + rejected.reason);
    }

    static boolean claimAgentToolExecution(
            HeartbeatToolProtocol.ToolCall call) {
        if (call == null) return false;
        String scope = HeartbeatToolProtocol.cleanScope(call.scope);
        if (scope.length() == 0 || call.id == null || call.tool == null) {
            return false;
        }
        // A call id is unique inside its conversation.  Do not let a malformed retry reuse the
        // same id with another tool name to bypass the side-effect guard.
        String fingerprint = scope + "|" + call.id;
        long now = System.currentTimeMillis();
        while (true) {
            Long previous = AGENT_TOOL_EXECUTION_CLAIMS.putIfAbsent(
                    fingerprint, Long.valueOf(now));
            if (previous == null) break;
            if (now - previous.longValue()
                    <= AGENT_TOOL_EXECUTION_CLAIM_TTL_MS) {
                return false;
            }
            if (AGENT_TOOL_EXECUTION_CLAIMS.replace(
                    fingerprint, previous, Long.valueOf(now))) {
                break;
            }
        }
        if (AGENT_TOOL_EXECUTION_CLAIMS.size() > 256) {
            long oldestAllowed = now - AGENT_TOOL_EXECUTION_CLAIM_TTL_MS;
            for (Map.Entry<String, Long> entry
                    : AGENT_TOOL_EXECUTION_CLAIMS.entrySet()) {
                Long claimedAt = entry.getValue();
                if (claimedAt == null
                        || claimedAt.longValue() < oldestAllowed) {
                    AGENT_TOOL_EXECUTION_CLAIMS.remove(
                            entry.getKey(), claimedAt);
                }
            }
        }
        if (!"agent-command-preview".equals(scope)
                && !AgentRunStore.claim(call)) {
            AGENT_TOOL_EXECUTION_CLAIMS.remove(
                    fingerprint, Long.valueOf(now));
            Main.log("ignored durably claimed local tool call tool="
                    + call.tool + " id=" + call.id + " scope=" + scope);
            return false;
        }
        return true;
    }

    static void queueSimpleAgentToolResult(
            Context context, HookAccountLoginSecurity.AgentStepResult step,
            HeartbeatToolProtocol.ToolCall call,
            boolean success, String output, String detail) {
        AgentDeviceBridge.ToolResult result = new AgentDeviceBridge.ToolResult(
                success, success ? 0 : 1, output, detail,
                "utf-8", false);
        if (step != null) {
            step.addResult(call, result);
        } else {
            HookAccountLoginSecurity.queueHiddenAgentToolResult(context, call, result);
        }
    }

    static Activity currentHostActivity() {
        Main module = Main.MODULE;
        if (module == null || module.curAct == null) return null;
        return module.curAct.get();
    }

    private static final ThreadLocal<ArrayDeque<ArrayDeque<String>>>
            AGENT_TOOL_PARSE_LABEL_STACK = new ThreadLocal<>();

    private static final Map<Object, String> AGENT_TOOL_DRAW_OBJECTS =
            Collections.synchronizedMap(new WeakHashMap<Object, String>());

    private static final AtomicInteger AGENT_TOOL_DRAW_DIAGNOSTICS = new AtomicInteger();

    private static final ArrayList<AgentToolHit> AGENT_TOOL_HITS = new ArrayList<>();

    private static final AtomicBoolean AGENT_TOOL_TOUCH_HOOK_INSTALLED = new AtomicBoolean(false);

    private static final AtomicBoolean AGENT_TOOL_COMPOSE_TOUCH_HOOK_INSTALLED = new AtomicBoolean(false);

    private static final AtomicLong AGENT_TOOL_LAST_DETAIL_TAP = new AtomicLong();

    private static final Object AGENT_TOOL_GESTURE_LOCK = new Object();

    private static long AGENT_TOOL_GESTURE_DOWN_TIME = -1L;

    private static float AGENT_TOOL_GESTURE_DOWN_X;

    private static float AGENT_TOOL_GESTURE_DOWN_Y;

    private static boolean AGENT_TOOL_GESTURE_MOVED;

    private static AgentToolDrawRow AGENT_TOOL_GESTURE_DOWN_ROW;

    private static final ThreadLocal<int[]> AGENT_TOOL_VISIT_BUDGET = new ThreadLocal<int[]>() {
        @Override protected int[] initialValue() { return new int[]{0}; }
    };

    private static volatile Method ACCESSIBILITY_SET_SEALED;

    private static volatile Method ACCESSIBILITY_GET_CHILD_ID;

    private static final AtomicInteger AGENT_TOOL_TOUCH_DIAGNOSTICS = new AtomicInteger();

    static final ConcurrentHashMap<String, NativeSearchGroup> AGENT_NATIVE_SEARCH_CALLS =
            new ConcurrentHashMap<>();

    private static final ThreadLocal<Boolean> AGENT_NATIVE_SEARCH_RENDERING =
            new ThreadLocal<>();

    static final class NativeSearchGroup {
        final List<String> traceKeys;
        final List<String> queries;
        NativeSearchGroup(List<String> traceKeys, List<String> queries) {
            this.traceKeys = Collections.unmodifiableList(
                    new ArrayList<>(traceKeys));
            this.queries = Collections.unmodifiableList(
                    new ArrayList<>(queries));
        }
    }

    /**
     * Canvas box renderers the agent tool-log uses, resolved per host generation.
     *
     * <p>These class names are <b>recycled</b> by R8 between releases, so they deliberately bypass the
     * shared {@code HostCompat.name()} tables: in 2.5.3 the old rounded-box name {@code mf6} now
     * belongs to the resource loader the whale animation depends on, so mapping it globally would
     * hijack that entry. Verified shape for shape against the decompiled sources:
     *
     * <pre>
     * 2.3.6  frame ve3   oval l96
     * 2.4.1  frame aj3   oval mf6
     * 2.5.2  frame ys3   oval rr6
     * 2.5.3  frame qb4   oval ve7
     * </pre>
     *
     * {@code qb4} carries the same fields as 2.4.1's {@code aj3} ({@code final no0 j; float k;
     * float l; yh2 m; yh2 n}) plus {@code c(me,float,float)} and {@code int d()}; {@code ve7}'s draw
     * body is line-for-line the same as 2.4.1's {@code mf6}, including
     * {@code Math.min(...) * 0.5f}.
     *
     * @param frame {@code true} for the frame/container box, {@code false} for the rounded (oval) box.
     */
    private static String agentToolBoxName(boolean frame) {
        if (HostCompat.isV253()) return frame ? "qb4" : "ve7";
        if (HostCompat.isV252()) return frame ? "ys3" : "rr6";
        if (HostCompat.isV241()) return frame ? "aj3" : "mf6";
        return frame ? "ve3" : "l96";
    }

    void hookAgentToolLogRoundedRect(ClassLoader cl) {
        if (HostCompat.isV241()) {
            hookV241AgentToolLogRoundedRect(cl);
            return;
        }
        if (!HostCompat.isV236()) return;
        if (!AGENT_TOOL_CANVAS_HOOK_INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> ovalBox = cl.loadClass(agentToolBoxName(false));
            Method draw = null;
            for (Method candidate : ovalBox.getDeclaredMethods()) {
                Class<?>[] p = candidate.getParameterTypes();
                if ("c".equals(candidate.getName()) && p.length == 3
                        && p[1] == float.class && p[2] == float.class) {
                    draw = candidate;
                    break;
                }
            }
            if (draw == null) throw new NoSuchMethodException("l96.c");
            int constructorHooks = 0;
            for (Constructor<?> constructor : ovalBox.getDeclaredConstructors()) {
                Main.MODULE.hook(constructor).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object result = chain.proceed();
                        String payload = pollAgentToolParseLabel();
                        Object box = chain.getThisObject();
                        if (payload != null && box != null) {
                            AGENT_TOOL_DRAW_OBJECTS.put(box, payload);
                        }
                        return result;
                    }
                });
                constructorHooks++;
            }
            Main.MODULE.hook(draw).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    return interceptAgentToolBoxDraw(chain);
                }
            });
            // New tool logs use fbox only as an invisible layout carrier. Hook ve3 as well as
            // l96 so current output never creates an oval at all; l96 remains supported for
            // already-persisted history generated by earlier 1.7.5 builds.
            Class<?> frameBox = cl.loadClass(agentToolBoxName(true));
            int frameConstructorHooks = 0;
            for (Constructor<?> constructor : frameBox.getDeclaredConstructors()) {
                Main.MODULE.hook(constructor).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object result = chain.proceed();
                        Object box = chain.getThisObject();
                        // phantom/no7 and legacy l96 also execute ve3's constructor. They are
                        // inner layout nodes and must never consume the payload intended for the
                        // outer fbox, otherwise the visible frame would miss its first draw.
                        if (box == null || box.getClass() != frameBox) return result;
                        String payload = discoverAgentToolDrawLabel(box);
                        if (payload == null) payload = pollAgentToolParseLabel();
                        if (payload != null && box != null) {
                            AGENT_TOOL_DRAW_OBJECTS.put(box, payload);
                        }
                        return result;
                    }
                });
                frameConstructorHooks++;
            }
            Method frameDraw = null;
            for (Method candidate : frameBox.getDeclaredMethods()) {
                Class<?>[] p = candidate.getParameterTypes();
                if ("c".equals(candidate.getName()) && p.length == 3
                        && p[1] == float.class && p[2] == float.class) {
                    frameDraw = candidate;
                    break;
                }
            }
            if (frameDraw == null) throw new NoSuchMethodException("ve3.c");
            Main.MODULE.hook(frameDraw).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    return interceptAgentToolBoxDraw(chain);
                }
            });
            // code249's AndroidComposeView override is not invoked on all 2.3.6 builds even
            // though reflection finds it. Receive the gesture once at Activity level, then
            // resolve the live ComposeView from that Activity for exact virtual-node lookup.
            hookAgentToolLogTouchSurface(cl);
            Main.log("agent tool-log direct Canvas renderer installed constructors="
                    + constructorHooks + " frameConstructors=" + frameConstructorHooks);
        } catch (Throwable error) {
            AGENT_TOOL_CANVAS_HOOK_INSTALLED.set(false);
            Main.log("agent tool-log direct Canvas renderer unavailable: " + error);
        }
    }

    /** Exact code257 counterpart; code249's l96/ve3 hook above remains untouched. */
    private void hookV241AgentToolLogRoundedRect(ClassLoader cl) {
        if (!HostCompat.isV241()) return;
        if (!AGENT_TOOL_CANVAS_HOOK_INSTALLED.compareAndSet(false, true)) return;
        try {
            // Official code257: l96 -> mf6 (rounded box), ve3 -> aj3 (frame box). 2.5.2 and 2.5.3 shuffle
            // both names again, so resolve them per host generation.
            final String ovalName = agentToolBoxName(false);
            final String frameName = agentToolBoxName(true);
            Class<?> ovalBox = cl.loadClass(ovalName);
            Method draw = null;
            for (Method candidate : ovalBox.getDeclaredMethods()) {
                Class<?>[] p = candidate.getParameterTypes();
                if ("c".equals(candidate.getName()) && p.length == 3
                        && p[1] == float.class && p[2] == float.class) {
                    draw = candidate;
                    break;
                }
            }
            if (draw == null) throw new NoSuchMethodException(ovalName + ".c");
            int constructorHooks = 0;
            for (Constructor<?> constructor : ovalBox.getDeclaredConstructors()) {
                Main.MODULE.hook(constructor).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object result = chain.proceed();
                        String payload = pollAgentToolParseLabel();
                        Object box = chain.getThisObject();
                        if (payload != null && box != null) {
                            AGENT_TOOL_DRAW_OBJECTS.put(box, payload);
                        }
                        return result;
                    }
                });
                constructorHooks++;
            }
            Main.MODULE.hook(draw).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    return interceptAgentToolBoxDraw(chain);
                }
            });

            Class<?> frameBox = cl.loadClass(frameName);
            int frameConstructorHooks = 0;
            for (Constructor<?> constructor : frameBox.getDeclaredConstructors()) {
                Main.MODULE.hook(constructor).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object result = chain.proceed();
                        Object box = chain.getThisObject();
                        // mf6 and other aj3 children also execute the base constructor. Bind only
                        // the exact outer frame instance so the payload reaches its visible draw.
                        if (box == null || box.getClass() != frameBox) return result;
                        String payload = discoverAgentToolDrawLabel(box);
                        if (payload == null) payload = pollAgentToolParseLabel();
                        if (payload != null) AGENT_TOOL_DRAW_OBJECTS.put(box, payload);
                        return result;
                    }
                });
                frameConstructorHooks++;
            }
            Method frameDraw = null;
            for (Method candidate : frameBox.getDeclaredMethods()) {
                Class<?>[] p = candidate.getParameterTypes();
                if ("c".equals(candidate.getName()) && p.length == 3
                        && p[1] == float.class && p[2] == float.class) {
                    frameDraw = candidate;
                    break;
                }
            }
            if (frameDraw == null) throw new NoSuchMethodException(frameName + ".c");
            Main.MODULE.hook(frameDraw).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    return interceptAgentToolBoxDraw(chain);
                }
            });
            // code257 has the same duplicate Activity + Compose delivery. Keep this adapter on
            // the Compose leaf so bounds move with the conversation during drawer transitions.
            hookAgentToolLogComposeTouchSurface(cl);
            Main.log("code257 agent tool-log Canvas renderer installed constructors="
                    + constructorHooks + " frameConstructors=" + frameConstructorHooks);
        } catch (Throwable error) {
            AGENT_TOOL_CANVAS_HOOK_INSTALLED.set(false);
            Main.log("code257 agent tool-log Canvas renderer unavailable: "
                    + Main.safeThrowableMessage(error));
        }
    }

    private void hookAgentToolLogTouchSurface(ClassLoader cl) {
        if (!AGENT_TOOL_TOUCH_HOOK_INSTALLED.compareAndSet(false, true)) return;
        try {
            // Exact current code249 Activity inheritance adapter. MainActivity -> uq -> xc3 ->
            // hv1 -> gv1, and gv1 overrides dispatchTouchEvent; hooking Activity never sees it.
            Class<?> touchActivity = HostCompat.isV236()
                    ? cl.loadClass("gv1") : Activity.class;
            Method dispatch = touchActivity.getDeclaredMethod(
                    "dispatchTouchEvent", MotionEvent.class);
            dispatch.setAccessible(true);
            Main.MODULE.hook(dispatch).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    MotionEvent event = (MotionEvent) chain.getArg(0);
                    Object result = chain.proceed();
                    Activity activity = (Activity) chain.getThisObject();
                    View composeView = findAgentToolComposeView(
                            activity.getWindow().getDecorView());
                    handleAgentToolDetailTap(activity, composeView, event);
                    return result;
                }
            });
            Main.log("agent tool Activity touch hook=" + touchActivity.getName());
        } catch (Throwable error) { Main.log("agent tool-log touch hook unavailable: " + Main.safeThrowableMessage(error)); }
    }

    private static View findAgentToolComposeView(View root) {
        if (root == null) return null;
        if ("androidx.compose.ui.platform.AndroidComposeView".equals(
                root.getClass().getName())) return root;
        // code249 R8-renames the physical AndroidComposeView even though its accessibility
        // snapshots still advertise the public Compose class names. The virtual-node provider
        // is the stable runtime identity; ordinary wrapper/content views do not own one.
        try {
            if (HostCompat.isV236() && root.getAccessibilityNodeProvider() != null) {
                return root;
            }
        } catch (Throwable ignored) {}
        if (!(root instanceof android.view.ViewGroup)) return null;
        android.view.ViewGroup group = (android.view.ViewGroup) root;
        for (int index = 0; index < group.getChildCount(); index++) {
            View found = findAgentToolComposeView(group.getChildAt(index));
            if (found != null) return found;
        }
        return null;
    }

    /** MainActivity overrides dispatchTouchEvent on some channels; Compose is the stable leaf. */
    private void hookAgentToolLogComposeTouchSurface(ClassLoader cl) {
        if (!AGENT_TOOL_COMPOSE_TOUCH_HOOK_INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> composeView = cl.loadClass("androidx.compose.ui.platform.AndroidComposeView");
            int count = 0;
            for (Method method : composeView.getDeclaredMethods()) {
                Class<?>[] p = method.getParameterTypes();
                if (!"dispatchTouchEvent".equals(method.getName()) || p.length != 1
                        || p[0] != MotionEvent.class || method.getReturnType() != boolean.class) {
                    continue;
                }
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        MotionEvent event = (MotionEvent) chain.getArg(0);
                        Object result = chain.proceed();
                        Object owner = chain.getThisObject();
                        handleAgentToolDetailTap(currentHostActivity(),
                                owner instanceof View ? (View) owner : null, event);
                        return result;
                    }
                });
                count++;
            }
            Main.log("agent tool Compose touch hooks=" + count);
        } catch (Throwable error) {
            AGENT_TOOL_COMPOSE_TOUCH_HOOK_INSTALLED.set(false);
            Main.log("agent tool Compose touch hook unavailable: " + Main.safeThrowableMessage(error));
        }
    }

    private static void handleAgentToolDetailTap(
            Activity activity, View composeView, MotionEvent event) {
        if (activity == null || event == null) return;
        // Exact code257 user policy. code249 and every other host retain their existing detail
        // interaction unchanged; the card renderer and model result transport remain active.
        if (HostCompat.isV236() && AgentToolConfig.disableToolLogDetailsV236()) return;
        if (HostCompat.isV241() && AgentToolConfig.disableToolLogDetailsV241()) return;
        int action = event.getActionMasked();
        float touchSlop = android.view.ViewConfiguration.get(activity)
                .getScaledTouchSlop();
        synchronized (AGENT_TOOL_GESTURE_LOCK) {
            if (action == MotionEvent.ACTION_DOWN) {
                if (AGENT_TOOL_GESTURE_DOWN_TIME != event.getDownTime()) {
                    AGENT_TOOL_GESTURE_DOWN_TIME = event.getDownTime();
                    AGENT_TOOL_GESTURE_DOWN_X = event.getRawX();
                    AGENT_TOOL_GESTURE_DOWN_Y = event.getRawY();
                    AGENT_TOOL_GESTURE_MOVED = false;
                    // A release can only activate the row that was under the original finger
                    // down. Merely scrolling across a tool card must never open its detail sheet.
                    AGENT_TOOL_GESTURE_DOWN_ROW = findSemanticAgentToolRow(
                            activity, composeView, event.getRawX(), event.getRawY());
                    int diagnostic = AGENT_TOOL_TOUCH_DIAGNOSTICS.incrementAndGet();
                    if (diagnostic <= 24) Main.log("agent tool detail down row="
                            + (AGENT_TOOL_GESTURE_DOWN_ROW == null ? "none"
                            : AGENT_TOOL_GESTURE_DOWN_ROW.name)
                            + " x=" + event.getRawX() + " y=" + event.getRawY());
                }
                return;
            }
            if (action == MotionEvent.ACTION_MOVE) {
                if (AGENT_TOOL_GESTURE_DOWN_TIME == event.getDownTime()) {
                    float dx = event.getRawX() - AGENT_TOOL_GESTURE_DOWN_X;
                    float dy = event.getRawY() - AGENT_TOOL_GESTURE_DOWN_Y;
                    if (dx * dx + dy * dy > touchSlop * touchSlop) {
                        AGENT_TOOL_GESTURE_MOVED = true;
                    }
                }
                return;
            }
            if (action == MotionEvent.ACTION_CANCEL) {
                AGENT_TOOL_GESTURE_DOWN_TIME = -1L;
                AGENT_TOOL_GESTURE_MOVED = true;
                AGENT_TOOL_GESTURE_DOWN_ROW = null;
                return;
            }
            if (action != MotionEvent.ACTION_UP
                    || AGENT_TOOL_GESTURE_DOWN_TIME != event.getDownTime()) return;
            float dx = event.getRawX() - AGENT_TOOL_GESTURE_DOWN_X;
            float dy = event.getRawY() - AGENT_TOOL_GESTURE_DOWN_Y;
            boolean isTap = !AGENT_TOOL_GESTURE_MOVED
                    && AGENT_TOOL_GESTURE_DOWN_ROW != null
                    && dx * dx + dy * dy <= touchSlop * touchSlop
                    && event.getEventTime() - event.getDownTime() <= 550L;
            AGENT_TOOL_GESTURE_DOWN_TIME = -1L;
            if (!isTap) {
                AGENT_TOOL_GESTURE_DOWN_ROW = null;
                return;
            }
        }
        long now = SystemClock.uptimeMillis();
        long previous = AGENT_TOOL_LAST_DETAIL_TAP.get();
        if (now - previous < 180L) return;
        AgentToolDrawRow selected = findSemanticAgentToolRow(
                activity, composeView, event.getRawX(), event.getRawY());
        AgentToolDrawRow downRow;
        synchronized (AGENT_TOOL_GESTURE_LOCK) {
            downRow = AGENT_TOOL_GESTURE_DOWN_ROW;
            AGENT_TOOL_GESTURE_DOWN_ROW = null;
        }
        if (!sameAgentToolRow(downRow, selected)) return;
        if (selected == null) {
            int diagnostic = AGENT_TOOL_TOUCH_DIAGNOSTICS.incrementAndGet();
            if (diagnostic <= 12) Main.log("agent tool detail miss x=" + event.getRawX()
                    + " y=" + event.getRawY() + " provider="
                    + (composeView != null && composeView.getAccessibilityNodeProvider() != null));
            return;
        }
        if (!AGENT_TOOL_LAST_DETAIL_TAP.compareAndSet(previous, now)) return;
        final AgentToolDrawRow row = selected;
        Main.log("agent tool detail hit row=" + row.name);
        activity.getWindow().getDecorView().post(new Runnable() {
            @Override public void run() {
                showAgentToolDetailSheet(activity, row);
            }
        });
    }

    private static boolean sameAgentToolRow(
            AgentToolDrawRow left, AgentToolDrawRow right) {
        if (left == null || right == null) return false;
        if (left == right) return true;
        if (left.traceKey.length() > 0 || right.traceKey.length() > 0) {
            return left.traceKey.equals(right.traceKey);
        }
        return left.name.equals(right.name)
                && left.detail.equals(right.detail)
                && left.input.equals(right.input);
    }

    /**
     * JLaTeX draws into an off-screen bitmap, so its Canvas coordinates are not screen
     * coordinates. Compose does, however, expose the exact formula rectangle as an accessibility
     * node. Match that rectangle to the measured carrier and map the tap to one row. This keeps
     * hit testing stable across recomposition, scrolling and opening/closing the sidebar.
     */
    private static AgentToolDrawRow findSemanticAgentToolRow(
            Activity activity, View composeView, float screenX, float screenY) {
        // No Agent tool-log card is on screen (e.g. the register/login surface): skip the
        // accessibility traversal entirely. Traversing the auth Compose tree previously froze the
        // main thread (e3.createAccessibilityNodeInfo -> ea5.p hang) and triggered an ANR.
        synchronized (AGENT_TOOL_HITS) {
            if (AGENT_TOOL_HITS.isEmpty()) return null;
        }
        int[] budget = AGENT_TOOL_VISIT_BUDGET.get();
        budget[0] = 256;
        AccessibilityNodeInfo root = null;
        try {
            AccessibilityNodeProvider provider = composeView == null
                    ? null : composeView.getAccessibilityNodeProvider();
            if (provider != null) {
                if (HostCompat.isV236()) {
                    try {
                        List<AccessibilityNodeInfo> formulaNodes =
                                provider.findAccessibilityNodeInfosByText("\uFFFD", View.NO_ID);
                        if (formulaNodes != null && !formulaNodes.isEmpty()) {
                            SemanticToolHit formulaBest = new SemanticToolHit();
                            for (AccessibilityNodeInfo formulaNode : formulaNodes) {
                                try {
                                    sealAccessibilityNode(formulaNode);
                                    evaluateSemanticAgentToolNode(formulaNode,
                                            screenX, screenY, 0, formulaBest);
                                } finally {
                                    if (formulaNode != null) try { formulaNode.recycle(); }
                                    catch (Throwable ignored) {}
                                }
                            }
                            if (formulaBest.row != null) return formulaBest.row;
                        }
                    } catch (Throwable ignored) {}
                }
                root = provider.createAccessibilityNodeInfo(View.NO_ID);
                if (root == null) root = provider.createAccessibilityNodeInfo(-1);
                if (root != null) {
                    sealAccessibilityNode(root);
                    SemanticToolHit providerBest = new SemanticToolHit();
                    findSemanticAgentToolRow(provider, root, screenX, screenY,
                            0, providerBest);
                    if (providerBest.interactiveAtPoint) return null;
                    if (providerBest.row != null) return providerBest.row;
                    try { root.recycle(); } catch (Throwable ignored) {}
                    root = null;
                }
            }
            View decor = activity.getWindow().getDecorView();
            root = decor.createAccessibilityNodeInfo();
            sealAccessibilityNode(root);
            SemanticToolHit best = new SemanticToolHit();
            findSemanticAgentToolRow(root, screenX, screenY, 0, best);
            return best.interactiveAtPoint ? null : best.row;
        } catch (Throwable error) {
            Main.log("agent tool semantic hit failed: " + Main.safeThrowableMessage(error));
            return null;
        } finally {
            if (root != null) try { root.recycle(); } catch (Throwable ignored) {}
        }
    }

    private static void findSemanticAgentToolRow(
            AccessibilityNodeProvider provider, AccessibilityNodeInfo node,
            float x, float y, int depth, SemanticToolHit best) {
        if (provider == null || node == null || depth > 80) return;
        int[] budget = AGENT_TOOL_VISIT_BUDGET.get();
        if (budget[0] <= 0) return;
        budget[0]--;
        sealAccessibilityNode(node);
        evaluateSemanticAgentToolNode(node, x, y, depth, best);
        int count = node.getChildCount();
        for (int index = 0; index < count; index++) {
            AccessibilityNodeInfo child = null;
            try {
                long childId = accessibilityChildNodeId(node, index);
                if (childId == Long.MIN_VALUE) continue;
                // AccessibilityNodeInfo packs the host accessibility view id in the low
                // 32 bits and Compose's virtual descendant id in the high 32 bits. Passing
                // the low half back to the provider only asks for the host view repeatedly,
                // so no semantic tool-log child can ever be hit.
                // Android packs the host accessibility-view id in the high half and the virtual
                // Compose descendant in the low half. code249 must request the low virtual id;
                // preserve every other host adapter's existing traversal until separately tested.
                int establishedId = (int) (childId >> 32);
                int alternateId = (int) childId;
                int[] virtualIds = HostCompat.isV236() && establishedId != alternateId
                        ? new int[]{establishedId, alternateId}
                        : new int[]{establishedId};
                for (int virtualId : virtualIds) {
                    child = provider.createAccessibilityNodeInfo(virtualId);
                    if (child != null) findSemanticAgentToolRow(provider, child,
                            x, y, depth + 1, best);
                    if (child != null) {
                        try { child.recycle(); } catch (Throwable ignored) {}
                        child = null;
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                if (child != null) try { child.recycle(); } catch (Throwable ignored) {}
            }
        }
    }

    private static long accessibilityChildNodeId(AccessibilityNodeInfo node, int index) {
        try {
            Method method = ACCESSIBILITY_GET_CHILD_ID;
            if (method == null) {
                method = AccessibilityNodeInfo.class.getDeclaredMethod("getChildId", int.class);
                method.setAccessible(true);
                ACCESSIBILITY_GET_CHILD_ID = method;
            }
            Object value = method.invoke(node, index);
            return value instanceof Number ? ((Number) value).longValue() : Long.MIN_VALUE;
        } catch (Throwable ignored) { return Long.MIN_VALUE; }
    }

    private static void findSemanticAgentToolRow(
            AccessibilityNodeInfo node, float x, float y, int depth,
            SemanticToolHit best) {
        if (node == null || depth > 80) return;
        int[] budget = AGENT_TOOL_VISIT_BUDGET.get();
        if (budget[0] <= 0) return;
        budget[0]--;
        sealAccessibilityNode(node);
        evaluateSemanticAgentToolNode(node, x, y, depth, best);
        Rect containing = new Rect();
        node.getBoundsInScreen(containing);
        if (!containing.contains(Math.round(x), Math.round(y))) return;
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = null;
            try {
                child = node.getChild(i);
                findSemanticAgentToolRow(child, x, y, depth + 1, best);
            } finally {
                if (child != null) try { child.recycle(); } catch (Throwable ignored) {}
            }
        }
    }

    private static void evaluateSemanticAgentToolNode(
            AccessibilityNodeInfo node, float x, float y, int depth,
            SemanticToolHit best) {
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (!bounds.contains(Math.round(x), Math.round(y))) return;
        if (HostCompat.isV236() && isV236FormulaContainer(node)) {
            // Provider-created empty formula children do not reliably expose getParent(). The
            // parent is visited first, so retain its object-replacement identity for descendants.
            best.formulaContainerAtPoint = true;
            evaluateV236FormulaContainer(bounds, y, best);
            if (best.row != null) return;
        }
        if ((HostCompat.isV236() || HostCompat.isV241())
                && isV236InteractiveSemanticNode(node)) {
            // The editable composer and drawer controls can share the same dimensions as an
            // off-screen formula bitmap. Any real interactive semantic at the finger position
            // vetoes the complete hit, even if an ancestor matched an old Canvas carrier.
            best.interactiveAtPoint = true;
            best.row = null;
            best.score = Float.MAX_VALUE;
            return;
        }
        if (best.interactiveAtPoint) return;
        float density = android.content.res.Resources.getSystem()
                .getDisplayMetrics().density;
        if (bounds.width() >= 120f * density && bounds.height() >= 28f * density) {
            synchronized (AGENT_TOOL_HITS) {
                for (int i = AGENT_TOOL_HITS.size() - 1; i >= 0; i--) {
                    AgentToolHit hit = AGENT_TOOL_HITS.get(i);
                    if (hit.rows.isEmpty() || hit.carrierBounds.width() <= 0f
                            || hit.carrierBounds.height() <= 0f) continue;
                    // Both supported adapters draw into an off-screen formula bitmap. Bounds
                    // alone are not identity: a drawer row or settings input can have the same
                    // dimensions as an old carrier. Require the current accessibility node to
                    // still describe this exact private formula/tool row before accepting it.
                    if ((HostCompat.isV236() || HostCompat.isV241())
                            && !matchesAgentToolSemanticNode(node, hit)
                            && !(HostCompat.isV236() && best.formulaContainerAtPoint
                            && isV236EmptyFormulaLeaf(node))) continue;
                    // Compose exposes the full formula carrier. A one-row Canvas card is shorter
                    // than that carrier, while a four-row card nearly fills it. Comparing against
                    // cardBounds therefore made single calls miss and batches appear to work.
                    float widthError = Math.abs(bounds.width() - hit.carrierBounds.width())
                            / Math.max(bounds.width(), hit.carrierBounds.width());
                    float heightError = Math.abs(bounds.height() - hit.carrierBounds.height())
                            / Math.max(bounds.height(), hit.carrierBounds.height());
                    float score = widthError + heightError + depth * 0.0001f;
                    if (score >= best.score || score > 0.42f) continue;
                    float fraction = Math.max(0f, Math.min(0.999f,
                            (y - bounds.top) / Math.max(1f, bounds.height())));
                    int rowIndex = hit.rowAt(fraction);
                    if (rowIndex < 0) continue;
                    best.score = score;
                    best.row = hit.rows.get(rowIndex);
                }
            }
        }
    }

    private static void evaluateV236FormulaContainer(
            Rect bounds, float y, SemanticToolHit best) {
        if (!HostCompat.isV236() || bounds == null || best == null) return;
        synchronized (AGENT_TOOL_HITS) {
            for (int index = AGENT_TOOL_HITS.size() - 1; index >= 0; index--) {
                AgentToolHit hit = AGENT_TOOL_HITS.get(index);
                if (hit.rows.isEmpty() || hit.sourceWidth <= 0f || hit.sourceHeight <= 0f) {
                    continue;
                }
                float widthError = Math.abs(bounds.width() - hit.sourceWidth)
                        / Math.max(bounds.width(), hit.sourceWidth);
                if (widthError > 0.08f) continue;
                float formulaTop = bounds.bottom - hit.sourceHeight;
                if (y < formulaTop || y >= bounds.bottom) continue;
                float fraction = (y - formulaTop) / hit.sourceHeight;
                int rowIndex = hit.rowAt(fraction);
                if (rowIndex < 0) continue;
                best.score = widthError;
                best.row = hit.rows.get(rowIndex);
                return;
            }
        }
    }

    private static boolean matchesAgentToolSemanticNode(
            AccessibilityNodeInfo node, AgentToolHit hit) {
        if (node == null || hit == null) return false;
        AccessibilityNodeInfo current = node;
        boolean recycleCurrent = false;
        try {
            // Both 2.3.6 and 2.4.1 draw the tool-log card into a Canvas leaf with empty
            // semantics, so the real card is never the direct node. Walk a bounded ancestor
            // chain looking for the private DSLG2 payload / row identity; the composer and
            // sidebar never carry that payload and stay rejected.
            for (int depth = 0; current != null && depth <= 6; depth++) {
                if (hasAgentToolSemanticIdentity(current, hit)) return true;
                if (HostCompat.isV236() && isV236ToolFormulaSemanticNode(current)) return true;
                AccessibilityNodeInfo parent = null;
                try { parent = current.getParent(); } catch (Throwable ignored) {}
                if (recycleCurrent) {
                    try { current.recycle(); } catch (Throwable ignored) {}
                }
                current = parent;
                recycleCurrent = current != null;
            }
            return HostCompat.isV236() && isV236ToolFormulaSemanticNode(node);
        } finally {
            if (recycleCurrent && current != null) {
                try { current.recycle(); } catch (Throwable ignored) {}
            }
        }
    }

    private static boolean isV236ToolFormulaSemanticNode(
            AccessibilityNodeInfo node) {
        if (!HostCompat.isV236() || node == null) return false;
        try {
            CharSequence className = node.getClassName();
            if (className == null || !"android.view.View".contentEquals(className)) {
                return false;
            }
            if (node.isEditable() || node.isClickable() || node.isLongClickable()
                    || node.isFocusable() || node.isFocused() || node.isCheckable()
                    || node.isScrollable() || node.isPassword()) return false;
            CharSequence text = node.getText();
            CharSequence description = node.getContentDescription();
            if ((text != null && text.length() > 0)
                    || (description != null && description.length() > 0)) return false;
            List<AccessibilityNodeInfo.AccessibilityAction> actions = node.getActionList();
            if (actions != null) {
                for (AccessibilityNodeInfo.AccessibilityAction action : actions) {
                    if (action == null) continue;
                    int id = action.getId();
                    if (id == AccessibilityNodeInfo.ACTION_CLICK
                            || id == AccessibilityNodeInfo.ACTION_LONG_CLICK
                            || id == AccessibilityNodeInfo.ACTION_FOCUS
                            || id == AccessibilityNodeInfo.ACTION_SET_TEXT) return false;
                }
            }
            AccessibilityNodeInfo parent = null;
            try {
                parent = node.getParent();
                if (parent == null) return false;
                CharSequence parentClass = parent.getClassName();
                if (parentClass == null
                        || !"android.widget.TextView".contentEquals(parentClass)) return false;
                CharSequence parentText = parent.getText();
                return parentText != null && parentText.toString().indexOf('\uFFFD') >= 0;
            } finally {
                if (parent != null) try { parent.recycle(); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isV236FormulaContainer(AccessibilityNodeInfo node) {
        if (!HostCompat.isV236() || node == null) return false;
        try {
            CharSequence type = node.getClassName();
            CharSequence text = node.getText();
            return type != null && "android.widget.TextView".contentEquals(type)
                    && text != null && text.toString().indexOf('\uFFFD') >= 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isV236EmptyFormulaLeaf(AccessibilityNodeInfo node) {
        if (!HostCompat.isV236() || node == null) return false;
        try {
            CharSequence type = node.getClassName();
            CharSequence text = node.getText();
            CharSequence description = node.getContentDescription();
            return type != null && "android.view.View".contentEquals(type)
                    && (text == null || text.length() == 0)
                    && (description == null || description.length() == 0)
                    && !node.isEditable() && !node.isClickable()
                    && !node.isLongClickable() && !node.isCheckable();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasAgentToolSemanticIdentity(
            AccessibilityNodeInfo node, AgentToolHit hit) {
        StringBuilder semantic = new StringBuilder();
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        if (text != null) semantic.append(text);
        if (description != null) semantic.append('\n').append(description);
        String value = semantic.toString();
        if (value.contains(RichPanelRenderer.TOOL_LOG_PAYLOAD_PREFIX)) return true;
        for (AgentToolDrawRow row : hit.rows) {
            if (row.name.length() > 0 && value.contains(row.name)) return true;
            if (row.detail.length() >= 4 && value.contains(row.detail)) return true;
        }
        return false;
    }

    private static boolean isV236InteractiveSemanticNode(AccessibilityNodeInfo node) {
        if ((!HostCompat.isV236() && !HostCompat.isV241()) || node == null) return false;
        try {
            // Assistant messages are long-clickable for the host copy menu. That does not make
            // their nested formula card an input control. Veto only actual editor/form controls.
            if (node.isEditable() || node.isCheckable()) return true;
            CharSequence className = node.getClassName();
            String type = className == null ? "" : className.toString();
            if (type.contains("EditText") || type.contains("Button")) return true;
            List<AccessibilityNodeInfo.AccessibilityAction> actions = node.getActionList();
            if (actions == null) return false;
            for (AccessibilityNodeInfo.AccessibilityAction action : actions) {
                if (action == null) continue;
                int id = action.getId();
                if (id == AccessibilityNodeInfo.ACTION_SET_TEXT) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /** App-created node snapshots are unsealed; query APIs reject them until framework-sealed. */
    private static void sealAccessibilityNode(AccessibilityNodeInfo node) {
        if (node == null) return;
        try {
            Method method = ACCESSIBILITY_SET_SEALED;
            if (method == null) {
                method = AccessibilityNodeInfo.class.getDeclaredMethod(
                        "setSealed", boolean.class);
                method.setAccessible(true);
                ACCESSIBILITY_SET_SEALED = method;
            }
            method.invoke(node, true);
        } catch (Throwable error) {
            // A failure is surfaced by the caller's diagnostic instead of crashing touch input.
        }
    }

    private static final class SemanticToolHit {
        float score = Float.MAX_VALUE;
        AgentToolDrawRow row;
        boolean interactiveAtPoint;
        boolean formulaContainerAtPoint;
    }

    private static void showAgentToolDetailSheet(Activity activity, AgentToolDrawRow row) {
        if (activity == null || activity.isFinishing() || row == null) return;
        final boolean dark = DeePXUi.isDark(activity);
        int surface = dark ? 0xFF27282B : 0xFFFFFFFF;
        int body = dark ? 0xFF18191B : 0xFFF4F5F7;
        int text = dark ? 0xFFF1F2F4 : 0xFF202124;
        int sub = dark ? 0xFFB5BAC2 : 0xFF70757D;
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(activity); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(DeePXUi.dp(activity, 20), DeePXUi.dp(activity, 12), DeePXUi.dp(activity, 20), DeePXUi.dp(activity, 22));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(surface); bg.setCornerRadii(new float[]{DeePXUi.dp(activity, 22),DeePXUi.dp(activity,22),DeePXUi.dp(activity,22),DeePXUi.dp(activity,22),0,0,0,0}); panel.setBackground(bg);
        View handle = new View(activity); GradientDrawable hd = new GradientDrawable(); hd.setColor(dark ? 0xFF73777E : 0xFFC7CBD1); hd.setCornerRadius(DeePXUi.dp(activity, 4)); handle.setBackground(hd);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(DeePXUi.dp(activity, 34), DeePXUi.dp(activity, 4)); hp.gravity = Gravity.CENTER_HORIZONTAL; hp.bottomMargin = DeePXUi.dp(activity, 16); panel.addView(handle, hp);
        TextView title = new TextView(activity); title.setText(row.name); title.setTextSize(18); title.setTypeface(Typeface.DEFAULT_BOLD); title.setTextColor(text); panel.addView(title);
        AgentToolTraceStore.Trace trace = AgentToolTraceStore.find(
                row.traceKey, row.input);
        TextView state = new TextView(activity);
        state.setText(trace.finished
                ? (trace.success ? "已完成" : "执行失败") : "执行中");
        state.setTextSize(12);
        state.setTextColor(trace.finished && !trace.success
                ? (dark ? 0xFFFF9A9A : 0xFFB3261E)
                : (trace.finished ? (dark ? 0xFF86D5A6 : 0xFF267849) : sub));
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(-1, -2);
        stateParams.topMargin = DeePXUi.dp(activity, 4);
        panel.addView(state, stateParams);
        ScrollView scroll = new ScrollView(activity); LinearLayout content = new LinearLayout(activity); content.setOrientation(LinearLayout.VERTICAL); scroll.addView(content); panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        String input = trace.input.length() == 0 ? row.input : trace.input;
        if (input.length() == 0) input = "{}";
        String output = trace.finished ? trace.output : "等待工具返回…";
        if (output.length() == 0) output = trace.success ? "执行完成（无文本输出）" : "执行失败（无文本输出）";
        addAgentToolCodeBlock(activity, content, "调用输入", input, body, text, sub);
        addAgentToolCodeBlock(activity, content, "调用输出", output, body, text, sub);
        dialog.setContentView(panel);
        Window window=dialog.getWindow();
        if(window!=null){window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.setGravity(Gravity.BOTTOM);}
        dialog.show();
        if(window!=null){window.setLayout(-1,Math.min(DeePXUi.dp(activity,560),activity.getResources().getDisplayMetrics().heightPixels*3/4));}
        panel.setTranslationY(DeePXUi.dp(activity,80)); panel.animate().translationY(0).setDuration(190).start();
    }

    private static void addAgentToolCodeBlock(Activity a, LinearLayout parent, String label, String code, int fill, int text, int sub) {
        TextView heading=new TextView(a); heading.setText(label); heading.setTextSize(12); heading.setTextColor(sub); LinearLayout.LayoutParams h=new LinearLayout.LayoutParams(-1,-2); h.topMargin=DeePXUi.dp(a,10); parent.addView(heading,h);
        TextView block=new TextView(a); block.setText(code); block.setTextSize(12); block.setTypeface(Typeface.MONOSPACE); block.setTextColor(text); block.setPadding(DeePXUi.dp(a,12),DeePXUi.dp(a,10),DeePXUi.dp(a,12),DeePXUi.dp(a,10)); GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(DeePXUi.dp(a,10));block.setBackground(d); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=DeePXUi.dp(a,4);parent.addView(block,p);
    }

    private static Object interceptAgentToolBoxDraw(Chain chain) throws Throwable {
        Object box = chain.getThisObject();
        String label = AGENT_TOOL_DRAW_OBJECTS.get(box);
        if (label == null) {
            label = pollAgentToolParseLabel();
            if (label == null) label = discoverAgentToolDrawLabel(box);
            if (label != null && isCompleteAgentToolDrawPayload(label)) {
                AGENT_TOOL_DRAW_OBJECTS.put(box, label);
            }
        }
        if (label == null) return chain.proceed();
        // A streamed Markdown frame may end halfway through the hex payload. It is a private
        // transport node, not a user-visible placeholder: suppress it completely until the next
        // complete frame can draw the final card in one pass.
        if (!isCompleteAgentToolDrawPayload(label)) return null;
        drawAgentToolLogDirect(box, chain.getArg(0),
                ((Number) chain.getArg(1)).floatValue(),
                ((Number) chain.getArg(2)).floatValue(), label);
        return null;
    }

    private static boolean isCompleteAgentToolDrawPayload(String value) {
        if (value == null || !value.startsWith(RichPanelRenderer.TOOL_LOG_PAYLOAD_PREFIX)) {
            return false;
        }
        int start = RichPanelRenderer.TOOL_LOG_PAYLOAD_PREFIX.length();
        int length = value.length() - start;
        if (length <= 0 || (length & 1) != 0) return false;
        for (int index = start; index < value.length(); index++) {
            if (!isAgentToolHexCharacter(value.charAt(index))) return false;
        }
        return true;
    }

    private static void drawAgentToolLogDirect(
            Object box, Object drawEnvironment, float x, float baseline, String label)
            throws Exception {
        List<AgentToolDrawRow> rows = decodeAgentToolDrawRows(label);
        if (rows.isEmpty()) return;
        Canvas canvas = (Canvas) Main.readHostField(drawEnvironment, "c");
        float width = ((Number) Main.readHostField(box, "d")).floatValue();
        float ascent = ((Number) Main.readHostField(box, "e")).floatValue();
        float descent = ((Number) Main.readHostField(box, "f")).floatValue();
        float stroke = ((Number) Main.readHostField(box, "k")).floatValue();
        float left = x + stroke * 0.5f;
        float top = baseline - ascent + stroke * 0.5f;
        float right = left + width - stroke;
        float bottom = top + ascent + descent - stroke;
        RectF available = new RectF(left, top, right, bottom);
        Matrix formulaMatrix = new Matrix();
        canvas.getMatrix(formulaMatrix);
        formulaMatrix.mapRect(available);

        android.util.DisplayMetrics metrics = HookSessionManagement.currentHostContext() == null
                ? android.content.res.Resources.getSystem().getDisplayMetrics()
                : HookSessionManagement.currentHostContext().getResources().getDisplayMetrics();
        float density = Math.max(1.0f, metrics.density);
        float scaledDensity = Math.max(1.0f, metrics.scaledDensity);
        float radiusPx = 8.0f * density;
        float borderPx = Math.max(1.0f, density);
        float horizontalPadding = 12.0f * density;
        float iconSize = 18.0f * density;
        float iconGap = 10.0f * density;
        float rightPadding = 14.0f * density;
        float nameLineHeight = 18.0f * density;
        float detailLineHeight = 15.0f * density;
        float detailGap = 3.0f * density;
        float rowGap = 4.0f * density;
        float verticalPadding = 7.0f * density;

        Paint namePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        namePaint.setTextSize(13.0f * scaledDensity);
        namePaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        namePaint.setTextAlign(Paint.Align.LEFT);
        Paint detailPaint = new Paint(namePaint);
        detailPaint.setTextSize(11.0f * scaledDensity);
        detailPaint.setTypeface(Typeface.create("monospace", Typeface.NORMAL));

        float longestText = 0.0f;
        for (AgentToolDrawRow row : rows) {
            longestText = Math.max(longestText, namePaint.measureText(row.name));
            if (row.detail.length() > 0) {
                longestText = Math.max(longestText, detailPaint.measureText(row.detail));
            }
        }
        float desiredWidth = horizontalPadding + iconSize + iconGap
                + longestText + rightPadding;
        float minWidth = 156.0f * density;
        float cardWidth = Math.min(available.width(), Math.max(minWidth, desiredWidth));
        float maxTextWidth = Math.max(1.0f, cardWidth - horizontalPadding
                - iconSize - iconGap - rightPadding);

        ArrayList<AgentToolDrawLayout> layouts = new ArrayList<>();
        float contentHeight = 0.0f;
        for (AgentToolDrawRow row : rows) {
            List<String> nameLines = wrapCanvasText(
                    row.name, namePaint, maxTextWidth, 0, false);
            List<String> detailLines = row.detail.length() == 0
                    ? Collections.<String>emptyList()
                    : wrapCanvasText(row.detail, detailPaint, maxTextWidth, 3, true);
            float textHeight = nameLines.size() * nameLineHeight;
            if (!detailLines.isEmpty()) {
                textHeight += detailGap + detailLines.size() * detailLineHeight;
            }
            // A trace row is a frequently tapped control, not a decorative caption. Give every
            // row one third more vertical room while keeping the compact grouped-panel rhythm.
            float rowHeight = Math.max(iconSize, textHeight) * 1.33f;
            AgentToolDrawLayout layout = new AgentToolDrawLayout(
                    row, nameLines, detailLines, rowHeight);
            layouts.add(layout);
            contentHeight += rowHeight;
        }
        contentHeight += Math.max(0, layouts.size() - 1) * rowGap;
        float desiredHeight = verticalPadding * 2.0f + contentHeight;
        // DeepSeek caps the formula bitmap at roughly 118dp. Four rows at the expanded 1.33x
        // touch height need slightly more than that, so merely increasing the TeX carrier cannot
        // grow the actual Canvas. Fit only the row-height surplus into the real mapped box while
        // preserving text/icon size. This guarantees the final row and divider remain visible.
        if (desiredHeight > available.height() && !layouts.isEmpty()) {
            float totalGap = Math.max(0, layouts.size() - 1) * rowGap;
            float rawRows = Math.max(1.0f, contentHeight - totalGap);
            float availableRows = Math.max(1.0f,
                    available.height() - verticalPadding * 2.0f - totalGap);
            float fit = Math.min(1.0f, availableRows / rawRows);
            contentHeight = totalGap;
            for (AgentToolDrawLayout layout : layouts) {
                layout.height *= fit;
                contentHeight += layout.height;
            }
            desiredHeight = verticalPadding * 2.0f + contentHeight;
        }
        float cardHeight = Math.min(available.height(), desiredHeight);
        RectF bounds = new RectF(
                available.left,
                available.top,
                available.left + cardWidth,
                available.top + cardHeight);
        float[] rowRanges = new float[layouts.size() * 2];
        float hitCursor = bounds.top + verticalPadding;
        float carrierHeight = Math.max(1.0f, available.height());
        for (int index = 0; index < layouts.size(); index++) {
            AgentToolDrawLayout layout = layouts.get(index);
            float start = index == 0 ? bounds.top : hitCursor - rowGap * 0.5f;
            float end = index + 1 == layouts.size()
                    ? bounds.bottom : hitCursor + layout.height + rowGap * 0.5f;
            rowRanges[index * 2] = Math.max(0.0f,
                    Math.min(1.0f, (start - available.top) / carrierHeight));
            rowRanges[index * 2 + 1] = Math.max(0.0f,
                    Math.min(1.0f, (end - available.top) / carrierHeight));
            hitCursor += layout.height + (index + 1 < layouts.size() ? rowGap : 0.0f);
        }
        synchronized (AGENT_TOOL_HITS) {
            AGENT_TOOL_HITS.add(new AgentToolHit(
                    new RectF(available), new RectF(bounds),
                    canvas.getWidth(), canvas.getHeight(),
                    rowRanges, new ArrayList<>(rows)));
            while (AGENT_TOOL_HITS.size() > 96) AGENT_TOOL_HITS.remove(0);
        }
        int diagnostic = AGENT_TOOL_DRAW_DIAGNOSTICS.incrementAndGet();
        if (diagnostic <= 12) {
            Main.log("agent tool-log canvas hit=" + diagnostic
                    + " rows=" + rows.size()
                    + " canvas=" + canvas.getWidth() + "x" + canvas.getHeight()
                    + " mapped=" + available.left + "," + available.top
                    + ".." + available.right + "," + available.bottom
                    + " card=" + bounds.left + "," + bounds.top
                    + ".." + bounds.right + "," + bounds.bottom);
        }

        int save = canvas.save();
        try {
            canvas.setMatrix(new Matrix());
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
            paint.setStyle(Paint.Style.FILL);
            Context themeContext = HookSessionManagement.currentHostContext();
            boolean dark = themeContext != null
                    && (themeContext.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            int surfaceColor = dark ? Color.rgb(31, 33, 38) : Color.rgb(255, 255, 255);
            int borderColor = dark ? Color.rgb(70, 75, 84) : Color.rgb(194, 199, 207);
            int iconColor = dark ? Color.rgb(187, 193, 203) : Color.rgb(82, 89, 99);
            int dotColor = dark ? Color.rgb(150, 158, 170) : Color.rgb(112, 120, 131);
            int nameColor = dark ? Color.rgb(235, 237, 241) : Color.rgb(61, 67, 76);
            int detailColor = dark ? Color.rgb(172, 179, 190) : Color.rgb(118, 125, 136);
            int dividerColor = dark ? Color.rgb(54, 58, 65) : Color.rgb(231, 233, 237);
            paint.setColor(surfaceColor);
            canvas.drawRoundRect(bounds, radiusPx, radiusPx, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(borderPx);
            paint.setColor(borderColor);
            RectF inset = new RectF(bounds);
            inset.inset(borderPx * 0.5f, borderPx * 0.5f);
            canvas.drawRoundRect(inset, radiusPx, radiusPx, paint);

            float iconLeft = bounds.left + horizontalPadding;
            float iconCenterX = iconLeft + iconSize * 0.5f;
            float textLeft = iconLeft + iconSize + iconGap;
            float cursorY = bounds.top + verticalPadding;
            float[] rowCenters = new float[layouts.size()];
            for (int index = 0; index < layouts.size(); index++) {
                AgentToolDrawLayout layout = layouts.get(index);
                rowCenters[index] = cursorY + layout.height * 0.5f;
                cursorY += layout.height + (index + 1 < layouts.size() ? rowGap : 0.0f);
            }

            if (layouts.size() > 1) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1.0f, density));
                paint.setColor(dark ? Color.rgb(78, 84, 94) : Color.rgb(207, 211, 217));
                float railTop = rowCenters[0] + iconSize * 0.62f;
                canvas.drawLine(iconCenterX, railTop, iconCenterX,
                        rowCenters[rowCenters.length - 1], paint);
            }
            paint.setColor(iconColor);
            drawRikkaToolsIcon(canvas, paint, iconLeft,
                    rowCenters[0] - iconSize * 0.5f,
                    iconSize, 1.5f * density);

            for (int index = 1; index < layouts.size(); index++) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(dotColor);
                canvas.drawCircle(iconCenterX, rowCenters[index], 2.25f * density, paint);
            }

            namePaint.setColor(nameColor);
            detailPaint.setColor(detailColor);
            Paint.FontMetrics nameMetrics = namePaint.getFontMetrics();
            Paint.FontMetrics detailMetrics = detailPaint.getFontMetrics();
            cursorY = bounds.top + verticalPadding;
            for (int index = 0; index < layouts.size(); index++) {
                AgentToolDrawLayout layout = layouts.get(index);
                float textHeight = layout.nameLines.size() * nameLineHeight;
                if (!layout.detailLines.isEmpty()) {
                    textHeight += detailGap + layout.detailLines.size() * detailLineHeight;
                }
                float lineTop = cursorY + (layout.height - textHeight) * 0.5f;
                for (String line : layout.nameLines) {
                    float textBaseline = lineTop
                            + (nameLineHeight - (nameMetrics.descent - nameMetrics.ascent)) * 0.5f
                            - nameMetrics.ascent;
                    canvas.drawText(line, textLeft, textBaseline, namePaint);
                    lineTop += nameLineHeight;
                }
                if (!layout.detailLines.isEmpty()) lineTop += detailGap;
                for (String line : layout.detailLines) {
                    float textBaseline = lineTop
                            + (detailLineHeight
                            - (detailMetrics.descent - detailMetrics.ascent)) * 0.5f
                            - detailMetrics.ascent;
                    canvas.drawText(line, textLeft, textBaseline, detailPaint);
                    lineTop += detailLineHeight;
                }
                cursorY += layout.height;
                if (index + 1 < layouts.size()) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(Math.max(1.0f, 0.7f * density));
                    paint.setColor(dividerColor);
                    float dividerY = cursorY + rowGap * 0.5f;
                    canvas.drawLine(textLeft, dividerY,
                            bounds.right - rightPadding, dividerY, paint);
                    cursorY += rowGap;
                }
            }
        } finally {
            canvas.restoreToCount(save);
        }
    }

    private static final class AgentToolDrawRow {
        final String name;
        final String detail;
        final String traceKey;
        final String input;
        AgentToolDrawRow(String name, String detail) {
            this(name, detail, "", "");
        }
        AgentToolDrawRow(String name, String detail, String traceKey, String input) {
            this.name = name == null ? "" : name.trim();
            this.detail = detail == null ? "" : detail.trim();
            this.traceKey = traceKey == null ? "" : traceKey.trim();
            this.input = input == null ? "" : input.trim();
        }
    }

    private static final class AgentToolHit {
        final RectF carrierBounds;
        final RectF cardBounds;
        final float sourceWidth;
        final float sourceHeight;
        final float[] rowRanges;
        final List<AgentToolDrawRow> rows;
        AgentToolHit(RectF carrierBounds, RectF cardBounds,
                     float sourceWidth, float sourceHeight,
                     float[] rowRanges, List<AgentToolDrawRow> rows) {
            this.carrierBounds = carrierBounds;
            this.cardBounds = cardBounds;
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
            this.rowRanges = rowRanges == null ? new float[0] : rowRanges;
            this.rows = rows;
        }

        int rowAt(float carrierFraction) {
            for (int index = 0; index < rows.size()
                    && index * 2 + 1 < rowRanges.length; index++) {
                if (carrierFraction >= rowRanges[index * 2]
                        && carrierFraction < rowRanges[index * 2 + 1]) return index;
            }
            return -1;
        }
    }

    private static final class AgentToolDrawLayout {
        final AgentToolDrawRow row;
        final List<String> nameLines;
        final List<String> detailLines;
        float height;
        AgentToolDrawLayout(AgentToolDrawRow row, List<String> nameLines,
                            List<String> detailLines, float height) {
            this.row = row;
            this.nameLines = nameLines;
            this.detailLines = detailLines;
            this.height = height;
        }
    }

    private static List<AgentToolDrawRow> decodeAgentToolDrawRows(String value) {
        String label = value == null ? "" : value.trim();
        ArrayList<AgentToolDrawRow> rows = new ArrayList<>();
        if (!label.startsWith(RichPanelRenderer.TOOL_LOG_PAYLOAD_PREFIX)) {
            if (label.length() > 0) rows.add(new AgentToolDrawRow(label, ""));
            return rows;
        }
        try {
            String encoded = label.substring(
                    RichPanelRenderer.TOOL_LOG_PAYLOAD_PREFIX.length());
            if ((encoded.length() & 1) != 0) throw new IllegalArgumentException("odd hex");
            byte[] bytes = new byte[encoded.length() / 2];
            for (int index = 0; index < bytes.length; index++) {
                int high = Character.digit(encoded.charAt(index * 2), 16);
                int low = Character.digit(encoded.charAt(index * 2 + 1), 16);
                if (high < 0 || low < 0) throw new IllegalArgumentException("invalid hex");
                bytes[index] = (byte) ((high << 4) | low);
            }
            JSONArray array = new JSONArray(new String(bytes, StandardCharsets.UTF_8));
            for (int index = 0; index < array.length() && rows.size() < 4; index++) {
                JSONObject item = array.optJSONObject(index);
                if (item == null) continue;
                String name = item.optString("n", "").trim();
                String detail = item.optString("d", "").trim();
                String traceKey = item.optString("k", "").trim();
                String input = item.optString("i", "").trim();
                if (name.length() > 0) {
                    rows.add(new AgentToolDrawRow(name, detail, traceKey, input));
                }
            }
        } catch (Throwable error) {
            Main.log("agent tool-log payload decode failed: " + Main.safeThrowableMessage(error));
        }
        return rows;
    }

    private static List<String> wrapCanvasText(
            String value, Paint paint, float maxWidth,
            int maximumLines, boolean ellipsizeLast) {
        ArrayList<String> lines = new ArrayList<>();
        String remaining = value == null ? "" : value.trim();
        while (remaining.length() > 0
                && (maximumLines <= 0 || lines.size() < maximumLines)) {
            boolean lastAllowed = maximumLines > 0 && lines.size() + 1 >= maximumLines;
            if (paint.measureText(remaining) <= maxWidth) {
                lines.add(remaining);
                remaining = "";
                break;
            }
            if (lastAllowed && ellipsizeLast) {
                lines.add(ellipsizeCanvasLabel(remaining, paint, maxWidth));
                remaining = "";
                break;
            }
            int count = paint.breakText(remaining, true, maxWidth, null);
            if (count <= 0) count = Character.charCount(remaining.codePointAt(0));
            int breakAt = preferredCanvasBreak(remaining, count);
            String line = remaining.substring(0, breakAt).trim();
            if (line.length() == 0) {
                breakAt = Math.min(remaining.length(), Math.max(1, count));
                line = remaining.substring(0, breakAt);
            }
            lines.add(line);
            remaining = remaining.substring(breakAt).trim();
        }
        if (lines.isEmpty()) lines.add("");
        return lines;
    }

    private static int preferredCanvasBreak(String value, int maximum) {
        int safeMaximum = Math.min(value.length(), Math.max(1, maximum));
        int minimum = Math.max(1, safeMaximum / 2);
        for (int index = safeMaximum - 1; index >= minimum; index--) {
            char c = value.charAt(index);
            if (Character.isWhitespace(c) || c == '_' || c == '/' || c == '-' || c == '.') {
                return index + 1;
            }
        }
        return safeMaximum;
    }

    private static String ellipsizeCanvasLabel(String value, Paint paint, float maxWidth) {
        String text = value == null ? "" : value.trim();
        if (paint.measureText(text) <= maxWidth) return text;
        final String ellipsis = "…";
        float room = Math.max(0.0f, maxWidth - paint.measureText(ellipsis));
        int count = paint.breakText(text, true, room, null);
        return text.substring(0, Math.max(0, count)).trim() + ellipsis;
    }

    /** Exact 24x24 path geometry used by RikkaHub's HugeIcons.Tools icon. */
    private static void drawRikkaToolsIcon(
            Canvas canvas, Paint source, float left, float top, float size,
            float strokeWidth) {
        Paint paint = new Paint(source);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        float s = size / 24.0f;
        Path p = new Path();
        p.moveTo(left + 13*s, top + 11*s); p.lineTo(left + 18*s, top + 6*s);
        p.moveTo(left + 19*s, top + 7*s); p.lineTo(left + 17*s, top + 5*s);
        p.lineTo(left + 19.5f*s, top + 3.5f*s); p.lineTo(left + 20.5f*s, top + 4.5f*s);
        p.lineTo(left + 19*s, top + 7*s);
        p.moveTo(left + 4.025f*s, top + 8.975f*s);
        p.cubicTo(left + 3.014f*s, top + 7.964f*s, left + 2.751f*s, top + 6.488f*s, left + 3.235f*s, top + 5.235f*s);
        p.lineTo(left + 4.657f*s, top + 6.657f*s); p.lineTo(left + 6.657f*s, top + 6.657f*s);
        p.lineTo(left + 6.657f*s, top + 4.657f*s); p.lineTo(left + 5.235f*s, top + 3.235f*s);
        p.cubicTo(left + 6.488f*s, top + 2.751f*s, left + 7.964f*s, top + 3.014f*s, left + 8.975f*s, top + 4.025f*s);
        p.cubicTo(left + 9.986f*s, top + 5.036f*s, left + 10.249f*s, top + 6.513f*s, left + 9.764f*s, top + 7.766f*s);
        p.lineTo(left + 16.234f*s, top + 14.236f*s);
        p.cubicTo(left + 17.487f*s, top + 13.751f*s, left + 18.964f*s, top + 14.014f*s, left + 19.975f*s, top + 15.025f*s);
        p.cubicTo(left + 20.986f*s, top + 16.036f*s, left + 21.249f*s, top + 17.512f*s, left + 20.765f*s, top + 18.765f*s);
        p.lineTo(left + 19.343f*s, top + 17.343f*s); p.lineTo(left + 17.343f*s, top + 17.343f*s);
        p.lineTo(left + 17.343f*s, top + 19.343f*s); p.lineTo(left + 18.765f*s, top + 20.765f*s);
        p.cubicTo(left + 17.512f*s, top + 21.249f*s, left + 16.036f*s, top + 20.986f*s, left + 15.025f*s, top + 19.975f*s);
        p.cubicTo(left + 14.015f*s, top + 18.964f*s, left + 13.751f*s, top + 17.490f*s, left + 14.235f*s, top + 16.237f*s);
        p.lineTo(left + 7.763f*s, top + 9.765f*s);
        p.moveTo(left + 12.203f*s, top + 14.5f*s); p.lineTo(left + 6.599f*s, top + 20.104f*s);
        p.cubicTo(left + 6.071f*s, top + 20.632f*s, left + 5.215f*s, top + 20.632f*s, left + 4.688f*s, top + 20.104f*s);
        p.lineTo(left + 3.896f*s, top + 19.312f*s);
        p.cubicTo(left + 3.368f*s, top + 18.785f*s, left + 3.368f*s, top + 17.929f*s, left + 3.896f*s, top + 17.401f*s);
        p.lineTo(left + 9.5f*s, top + 11.797f*s);
        canvas.drawPath(p, paint);
    }

    private static void beginAgentToolLabelParse(String source) {
        ArrayDeque<String> labels = new ArrayDeque<>();
        final String prefix = RichPanelRenderer.TOOL_LOG_MARKER_PREFIX;
        int from = 0;
        while (source != null && (from = source.indexOf(prefix, from)) >= 0) {
            int start = from + prefix.length();
            int end = source.indexOf(" }", start);
            if (end < 0) break;
            String label = source.substring(start, end).trim();
            // The width phantom used to share this visual prefix and was accidentally queued as
            // if it were a payload. Only private DSLG2 transport belongs to a box binding.
            if (label.startsWith(RichPanelRenderer.TOOL_LOG_PAYLOAD_PREFIX)) {
                labels.addLast(label);
            }
            from = end + 2;
        }
        ArrayDeque<ArrayDeque<String>> stack = AGENT_TOOL_PARSE_LABEL_STACK.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            AGENT_TOOL_PARSE_LABEL_STACK.set(stack);
        }
        stack.push(labels);
    }

    private static String pollAgentToolParseLabel() {
        ArrayDeque<ArrayDeque<String>> stack = AGENT_TOOL_PARSE_LABEL_STACK.get();
        if (stack == null || stack.isEmpty()) return null;
        ArrayDeque<String> labels = stack.peek();
        return labels == null || labels.isEmpty() ? null : labels.pollFirst();
    }

    private static void endAgentToolLabelParse() {
        ArrayDeque<ArrayDeque<String>> stack = AGENT_TOOL_PARSE_LABEL_STACK.get();
        if (stack == null || stack.isEmpty()) return;
        stack.pop();
        if (stack.isEmpty()) AGENT_TOOL_PARSE_LABEL_STACK.remove();
    }

    /**
     * ART does not expose l96's synthesized constructor on every 2.3.6 build. The encoded
     * marker is nevertheless part of that exact oval-box child tree, so recover it from the
     * owning object instead of falling back to a recomposition-sensitive global FIFO.
     */
    private static String discoverAgentToolDrawLabel(Object box) {
        if (box == null) return null;
        StringBuilder text = new StringBuilder(512);
        IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
        collectAgentToolBoxText(box, text, visited, 0);
        String source = text.toString();
        int start = source.indexOf(RichPanelRenderer.TOOL_LOG_PAYLOAD_PREFIX);
        if (start < 0) return null;
        int end = start + RichPanelRenderer.TOOL_LOG_PAYLOAD_PREFIX.length();
        while (end < source.length() && isAgentToolHexCharacter(source.charAt(end))) {
            end++;
        }
        return end > start + RichPanelRenderer.TOOL_LOG_PAYLOAD_PREFIX.length()
                ? source.substring(start, end) : null;
    }

    private static void collectAgentToolBoxText(
            Object value, StringBuilder output,
            IdentityHashMap<Object, Boolean> visited, int depth) {
        if (value == null || output.length() >= 8192 || depth > 14
                || visited.size() > 320) return;
        if (value instanceof CharSequence) {
            output.append(value);
            return;
        }
        if (value instanceof char[]) {
            output.append((char[]) value);
            return;
        }
        Class<?> type = value.getClass();
        if (type.isPrimitive() || type.isEnum() || value instanceof Number
                || value instanceof Boolean || value instanceof Class) return;
        if (visited.put(value, Boolean.TRUE) != null) return;
        if (type.isArray()) {
            int length = Math.min(java.lang.reflect.Array.getLength(value), 256);
            for (int index = 0; index < length; index++) {
                collectAgentToolBoxText(java.lang.reflect.Array.get(value, index),
                        output, visited, depth + 1);
            }
            return;
        }
        if (value instanceof Iterable) {
            int count = 0;
            for (Object item : (Iterable<?>) value) {
                collectAgentToolBoxText(item, output, visited, depth + 1);
                if (++count >= 256 || output.length() >= 8192) break;
            }
            return;
        }
        String className = type.getName();
        if (!(className.indexOf('.') < 0
                || className.startsWith("defpackage.")
                || className.startsWith("java.util."))) return;
        for (Class<?> cursor = type; cursor != null && cursor != Object.class;
             cursor = cursor.getSuperclass()) {
            Field[] fields;
            try { fields = cursor.getDeclaredFields(); }
            catch (Throwable ignored) { continue; }
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers())
                        || field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    collectAgentToolBoxText(field.get(value), output,
                            visited, depth + 1);
                } catch (Throwable ignored) {}
                if (output.length() >= 8192) return;
            }
        }
    }

    private static boolean isAgentToolHexCharacter(char value) {
        return (value >= '0' && value <= '9')
                || (value >= 'A' && value <= 'F');
    }

    void hookHeartbeatToolResponses(ClassLoader cl) {
        int liveHooks = 0;
        int staticHooks = 0;
        String[] liveClasses = new String[]{"fo2", "ho2"};
        for (String legacyClassName : liveClasses) {
            final boolean executeTools = "fo2".equals(legacyClassName);
            String className = HostCompat.name(legacyClassName);
            final String appendMethod =
                    HostCompat.method(legacyClassName, "g");
            try {
                Class<?> liveResponse = cl.loadClass(className);
                for (Constructor<?> ctor : liveResponse.getDeclaredConstructors()) {
                    Main.MODULE.hook(ctor).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            Object result = chain.proceed();
                            try {
                                registerHeartbeatFragmentState(
                                        chain.getThisObject(), executeTools);
                                sanitizeLiveHeartbeatResponse(
                                        chain.getThisObject(), false, executeTools);
                            } catch (Throwable t) {
                                Main.log("heartbeat response constructor filter failed: " + t);
                            }
                            return result;
                        }
                    });
                    liveHooks++;
                }
                for (Method method : liveResponse.getDeclaredMethods()) {
                    final String name = method.getName();
                    Class<?>[] types = method.getParameterTypes();
                    final String replaceMethod = HostCompat.isV253() ? "h"
                            : HostCompat.isV234() && HostCompat.isGooglePlay() ? "j" : "i";
                    if ((!appendMethod.equals(name) && !replaceMethod.equals(name))
                            || types.length == 0 || types[0] != String.class) continue;
                    Main.MODULE.hook(method).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            if ("content".equals(chain.getArg(0))) {
                                String decoded = decodeHeartbeatJsonString(chain.getArg(1));
                                if (decoded != null) {
                                    Object fragment = chain.getThisObject();
                                    Object state = liveResponseTextState(fragment);
                                    Object current = state == null
                                            ? null : Main.invokeNoArg(state, "getValue");
                                    boolean append = appendMethod.equals(name);
                                    String hostText = append
                                            ? (current instanceof String ? (String) current : "")
                                                    + decoded
                                            : decoded;
                                    Main.HeartbeatSanitizedUpdate update = executeTools
                                            ? prepareHeartbeatStateUpdate(
                                                    fragment, hostText, append, true)
                                            : prepareThinkingStateUpdate(
                                                    fragment, hostText, append);
                                    if (state != null
                                            && setHeartbeatSanitizedState(
                                                    state, update.safe)) {
                                        markHeartbeatContentChanged(
                                                chain.getArg(chain.getArgs().size() - 1));
                                        if (!update.calls.isEmpty()) {
                                            Main.log("Agent live pre-write accepted calls="
                                                    + update.calls.size()
                                                    + " method=" + name);
                                        }
                                        Main.dispatchHeartbeatStateUpdate(update);
                                        return null;
                                    }
                                }
                            }
                            Object result = chain.proceed();
                            try {
                                if ("content".equals(chain.getArg(0))) {
                                        sanitizeLiveHeartbeatResponse(
                                            chain.getThisObject(), appendMethod.equals(name),
                                            executeTools);
                                }
                            } catch (Throwable t) {
                                Main.log("heartbeat streaming response filter failed: " + t);
                            }
                            return result;
                        }
                    });
                    liveHooks++;
                }
            } catch (Throwable t) {
                Main.log("heartbeat live response hook unavailable for "
                        + className + ": " + t);
            }
        }
        String[] staticClasses = new String[]{"at7", "ht7"};
        for (String legacyClassName : staticClasses) {
            final boolean renderToolRows = "at7".equals(legacyClassName);
            String className = HostCompat.name(legacyClassName);
            try {
                Class<?> staticResponse = cl.loadClass(className);
                for (Constructor<?> ctor : staticResponse.getDeclaredConstructors()) {
                    Class<?>[] types = ctor.getParameterTypes();
                    final int contentIndex;
                    if ((types.length == 3 || types.length == 4)
                            && types[1] == String.class) {
                        contentIndex = 1;
                    } else if (types.length >= 5
                            && types[0] == String.class
                            && types[1] == int.class
                            && types[2] == String.class) {
                        // 2.3.4's normal RESPONSE/THINK fragments are constructed as
                        // (type, id, content, ...). This is the live p() render path. Hooking
                        // only the kotlinx serialization constructor (mask, type, id, content,
                        // ...) cleans the database eventually but lets the raw control block
                        // flash and remain in the active Compose tree.
                        contentIndex = 2;
                    } else if (types.length >= 4 && types[3] == String.class) {
                        contentIndex = 3;
                    } else {
                        continue;
                    }
                    Main.MODULE.hook(ctor).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            Object raw = chain.getArg(contentIndex);
                            if (!(raw instanceof String)) return chain.proceed();
                            HeartbeatToolProtocol.Result parsed = renderToolRows
                                    ? HeartbeatToolProtocol.parseForConversation((String) raw)
                                    : HeartbeatToolProtocol.parse((String) raw);
                            String safe = renderToolRows
                                    ? HeartbeatToolProtocol.renderConversationToolRows(
                                            (String) raw)
                                    : (AgentToolConfig.hideToolLogs()
                                            ? HeartbeatToolProtocol
                                                    .sanitizeThinkingToolNarration((String) raw)
                                            : parsed.visibleText);
                            Object result;
                            if (safe.equals(raw)) {
                                result = chain.proceed();
                            } else {
                                Object[] args = chain.getArgs().toArray();
                                args[contentIndex] = safe;
                                result = chain.proceed(args);
                            }
                            // Some 2.3.4 responses are materialized directly as their immutable
                            // RESPONSE fragment and never pass through the mutable streaming
                            // object. The old code hid/rendered those calls here but executed tools
                            // only in the streaming hook, leaving a convincing status row with no
                            // real action. Use the final fragment as an authorized fallback. The
                            // durable call-id claim in executeHeartbeatToolCalls prevents a second
                            // execution when both paths do fire.
                            if (renderToolRows) {
                                dispatchStaticAgentToolFallback(parsed);
                            }
                            return result;
                        }
                    });
                    staticHooks++;
                }
            } catch (Throwable t) {
                Main.log("heartbeat static response hook unavailable for "
                        + className + ": " + t);
            }
        }
        Main.log("heartbeat hidden-tool response hooks live=" + liveHooks
                + " static=" + staticHooks);
    }

    /**
     * Intercepts 2.3.4's non-inlined JSON Patch dispatcher.  ART/R8 may bypass hooks on the
     * concrete us2.e/i methods, but every nested fragment patch still reaches cj0.g before the
     * RESPONSE State and its Markdown cache are updated.
     */
    void hookHeartbeatPatchDispatcher(ClassLoader cl) {
        if (!HostCompat.isV234() && !HostCompat.isV253()) return;
        final String dispatcherName = HostCompat.isV253() ? "yc2"
                : HostCompat.isV241() ? "jo0"
                : HostCompat.isGooglePlay() ? "pi0" : "cj0";
        final String dispatcherMethod = HostCompat.isV253() ? "d"
                : HostCompat.isV241() ? "g"
                : HostCompat.isGooglePlay() ? "f" : "g";
        try {
            Class<?> dispatcher = cl.loadClass(dispatcherName);
            final String responseClassName = HostCompat.name("fo2");
            final String thinkingClassName = HostCompat.name("ho2");
            int installed = 0;
            for (Method method : dispatcher.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!dispatcherMethod.equals(method.getName())
                        || !Modifier.isStatic(method.getModifiers())
                        || method.getReturnType() != void.class
                        || types.length != 5
                        || !List.class.isAssignableFrom(types[2])) continue;
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object fragment = chain.getArg(0);
                        Object patch = chain.getArg(1);
                        List<?> remainingPath = (List<?>) chain.getArg(2);
                        if (fragment == null || patch == null
                                || (!responseClassName.equals(
                                        fragment.getClass().getSimpleName())
                                    && !thinkingClassName.equals(
                                        fragment.getClass().getSimpleName()))
                                || remainingPath == null
                                || remainingPath.size() != 1
                                || !"content".equals(String.valueOf(
                                        remainingPath.get(0)))) {
                            return chain.proceed();
                        }

                        String operation = String.valueOf(Main.readHostField(patch, "b"));
                        boolean append = "APPEND".equals(operation);
                        boolean replace = "SET".equals(operation);
                        if (!append && !replace) return chain.proceed();

                        Object jsonElement = Main.readHostField(patch, "c");
                        String decoded = decodeHeartbeatJsonString(jsonElement);
                        if (decoded == null) {
                            if (Main.isSrvLog()) {
                                Main.srvLog("[HB-PATCH] RESPONSE/content decode failed op="
                                        + operation + " json="
                                        + (jsonElement == null ? "null"
                                                : jsonElement.getClass().getSimpleName()));
                            }
                            return chain.proceed();
                        }

                        Object state = liveResponseTextState(fragment);
                        Object current = state == null
                                ? null : Main.invokeNoArg(state, "getValue");
                        if (!(current instanceof String)) return chain.proceed();
                        String hostText = append
                                ? (String) current + decoded : decoded;
                        boolean response = responseClassName.equals(
                                fragment.getClass().getSimpleName());
                        Main.HeartbeatSanitizedUpdate update = response
                                ? prepareHeartbeatDeltaUpdate(
                                        fragment, (String) current,
                                        decoded, append, true)
                                : prepareThinkingDeltaUpdate(
                                        fragment, (String) current,
                                        decoded, append);
                        if (update.safe.equals(hostText)) {
                            return chain.proceed();
                        }
                        if (!setHeartbeatSanitizedState(state, update.safe)) {
                            Main.log("heartbeat patch-dispatcher State write failed fragment="
                                    + fragment.getClass().getSimpleName());
                            return chain.proceed();
                        }
                        markHeartbeatContentChanged(chain.getArg(4));
                        Main.log("heartbeat patch dispatcher hid streamed "
                                + (response ? "control block" : "THINK tool narration")
                                + " op=" + operation
                                + " raw=" + hostText.length()
                                + " visible=" + update.safe.length());
                        Main.dispatchHeartbeatStateUpdate(update);
                        return null;
                    }
                });
                installed++;
            }
            Main.log("heartbeat JSON Patch dispatcher hooks=" + installed
                    + " owner=" + dispatcherName + "." + dispatcherMethod);
        } catch (Throwable error) {
            Main.log("heartbeat JSON Patch dispatcher hook unavailable: " + error);
        }
    }

    private static void registerHeartbeatFragmentState(
            Object fragment, boolean response) {
        if (fragment == null || !AgentToolConfig.enabledFast()) return;
        Object type = Main.readHostField(fragment, "a");
        String expected = response ? "RESPONSE" : "THINK";
        if (type != null && !expected.equals(String.valueOf(type))) return;
        Object state = liveResponseTextState(fragment);
        if (state == null) return;
        Map<Object, WeakReference<Object>> states = response
                ? HEARTBEAT_RESPONSE_STATES : THINKING_AGENT_STATES;
        states.put(state, new WeakReference<Object>(fragment));
    }

    /**
     * Hooks the actual 2.3.4 mutable RESPONSE State write. R8 can inline the concrete fragment
     * patch method, but the channel-specific StateFlow emission remains the single point before
     * the Markdown AST is built and cached (c38 on mainland, b78 on Google Play).
     */
    void hookTrackedHeartbeatStateWrites(ClassLoader cl) {
        if (!HostCompat.isV234()) return;
        final String stateOwner = HostCompat.isV241() ? "rb8"
                : HostCompat.isV236() ? "i38"
                : HostCompat.isGooglePlay() ? "b78" : "c38";
        try {
            Class<?> mutableState = cl.loadClass(stateOwner);
            int installed = 0;
            for (Method method : mutableState.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                final int valueIndex;
                if ("l".equals(method.getName()) && types.length == 1) {
                    valueIndex = 0;
                } else if ("m".equals(method.getName()) && types.length == 2) {
                    valueIndex = 1;
                } else {
                    continue;
                }
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        if (Boolean.TRUE.equals(tlHeartbeatInternalStateWrite.get())) {
                            return chain.proceed();
                        }
                        Object state = chain.getThisObject();
                        WeakReference<Object> reference =
                                HEARTBEAT_RESPONSE_STATES.get(state);
                        boolean response = reference != null;
                        if (reference == null) {
                            reference = THINKING_AGENT_STATES.get(state);
                        }
                        Object fragment = reference == null ? null : reference.get();
                        Object incoming = chain.getArg(valueIndex);
                        if (fragment == null) {
                            if (reference != null) {
                                HEARTBEAT_RESPONSE_STATES.remove(state);
                                THINKING_AGENT_STATES.remove(state);
                            }
                            // Code249 sometimes allocates a replacement i38 State after the
                            // RESPONSE constructor hook has run (notably after a second tool
                            // round). The marker is module-private, so discovering it here is an
                            // unambiguous response boundary: claim the concrete State itself and
                            // keep the privacy gate alive across every later SSE write.
                            if (!(incoming instanceof String)
                                    || !shouldMonitorHeartbeatFragment(
                                            state, (String) incoming)) {
                                return chain.proceed();
                            }
                            fragment = state;
                            response = true;
                            HEARTBEAT_RESPONSE_STATES.put(
                                    state, new WeakReference<Object>(state));
                            Main.log("heartbeat RESPONSE state claimed from private SSE marker owner="
                                    + state.getClass().getSimpleName());
                        }
                        if (!(incoming instanceof String)
                                || (response && !shouldMonitorHeartbeatFragment(
                                        fragment, (String) incoming))) {
                            return chain.proceed();
                        }
                        String raw = (String) incoming;
                        Main.HeartbeatSanitizedUpdate update = response
                                ? prepareHeartbeatStateUpdate(
                                        fragment, raw, true, true)
                                : prepareThinkingStateUpdate(fragment, raw, true);
                        Object result;
                        if (update.safe.equals(raw)) {
                            result = chain.proceed();
                        } else {
                            Object[] args = chain.getArgs().toArray();
                            args[valueIndex] = update.safe;
                            result = chain.proceed(args);
                            synchronized (HEARTBEAT_RESPONSE_STREAMS) {
                                if (response) {
                                    HeartbeatResponseStream stream =
                                            HEARTBEAT_RESPONSE_STREAMS.get(fragment);
                                    if (stream != null && !stream.stateWriteSanitizeLogged) {
                                        stream.stateWriteSanitizeLogged = true;
                                        Main.log("heartbeat RESPONSE state write sanitized before"
                                                + " Markdown fragment="
                                                + fragment.getClass().getSimpleName());
                                    }
                                } else {
                                    Main.log("heartbeat THINK state write hid tool narration fragment="
                                            + fragment.getClass().getSimpleName());
                                }
                            }
                        }
                        Main.dispatchHeartbeatStateUpdate(update);
                        return result;
                    }
                });
                installed++;
            }
            Main.log("heartbeat tracked RESPONSE state hooks=" + installed
                    + " owner=" + stateOwner);
        } catch (Throwable error) {
            Main.log("heartbeat tracked RESPONSE state hook unavailable: " + error);
        }
    }

    /**
     * The mainland 2.3.4 message body renders each yb0 fragment through we0.d.  That fragment
     * State can outlive both the mutable message reducer and the final persisted e48 object, so
     * it is the reliable screen-facing boundary for incremental control-block suppression.
     */
    void hookHeartbeatFragmentRenderBoundary(ClassLoader cl) {
        if (!HostCompat.isV234()) return;
        String rendererName = HostCompat.isV241() ? "ch2"
                : HostCompat.isGooglePlay() ? "zj4" : "we0";
        String responseName = HostCompat.isV241() ? "kc0"
                : HostCompat.isGooglePlay() ? "jd0" : "yb0";
        String renderMethod = HostCompat.isV241() ? "d"
                : HostCompat.isGooglePlay() ? "c" : "d";
        try {
            Class<?> renderer = cl.loadClass(rendererName);
            Class<?> responseFragment = cl.loadClass(responseName);
            int installed = 0;
            for (Method method : renderer.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!renderMethod.equals(method.getName())
                        || !Modifier.isStatic(method.getModifiers())
                        || types.length != 13
                        || types[0] != responseFragment) continue;
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        sanitizeHeartbeatFragmentAtRenderBoundary(chain.getArg(0));
                        return chain.proceed();
                    }
                });
                installed++;
            }
            Main.log("heartbeat fragment screen monitor hooks=" + installed
                    + " renderer=" + rendererName + "." + renderMethod);
        } catch (Throwable error) {
            Main.log("heartbeat fragment screen monitor unavailable: " + error);
        }
    }

    /** Intercepts the lambda that reads RESPONSE State immediately before Markdown parsing. */
    void hookHeartbeatMarkdownInputBoundary(ClassLoader cl) {
        if (!HostCompat.isV234()) return;
        try {
            String markdownInputOwner = HostCompat.isV241() ? "ah5"
                    : HostCompat.isV236() ? "ua5"
                    : HostCompat.isGooglePlay() ? "rc5" : "ma5";
            Class<?> markdownInput = cl.loadClass(markdownInputOwner);
            int installed = 0;
            for (Method method : markdownInput.getDeclaredMethods()) {
                if (!"r".equals(method.getName())
                        || method.getParameterTypes().length != 2) continue;
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object lambda = chain.getThisObject();
                        Object variant = Main.readHostField(lambda, "a");
                        if (variant instanceof Number
                                && (((Number) variant).intValue() == 0
                                || ((Number) variant).intValue() == 1)) {
                            // Intercept the exact State consumed by DeepSeek's native Markdown
                            // renderer.  File payloads are removed here before pa5 parses them as
                            // an ordinary code block, while the side card receives each delta.
                            sanitizeModelFileMarkdownStateBeforeRead(
                                    Main.readHostField(lambda, "e"));
                            sanitizeHeartbeatMarkdownStateBeforeRead(
                                    Main.readHostField(lambda, "e"));
                        }
                        return chain.proceed();
                    }
                });
                installed++;
            }
            Main.log("heartbeat Markdown input hooks=" + installed
                    + " renderer=" + markdownInputOwner + ".r");
        } catch (Throwable error) {
            Main.log("heartbeat Markdown input hook unavailable: " + error);
        }
    }

    /**
     * Hooks the native Markdown source-to-AST call for every supported host generation.
     *
     * <p>Filtering only the mutable SSE State is insufficient on optimized 2.3.x builds: Compose
     * may retain an AST produced before that State was replaced, which explains the historical
     * "first call hides, later calls leak" behaviour.  Replacing argument zero here establishes
     * a stateless privacy boundary on every parse, independent of fragment identity, cache
     * lifetime, process restarts, or which earlier hook ART happened to inline.</p>
     */
    void hookHeartbeatNativeMarkdownParser(ClassLoader cl) {
        final String ownerName;
        if (HostCompat.isV253()) {
            // 2.5.3 renamed the owner only: td6 keeps dh5's exact method names and shapes
            // (h(String,boolean,boolean,int,boolean,le6):o7a parser,
            // d(String,o7a,ot6,jh4,int):void renderer; anchored by the unique string
            // markdown_overflow_show_all_button + constant 160.0f).
            ownerName = "td6";
        } else if (HostCompat.isV234()) {
            ownerName = HostCompat.isV241() ? "dh5"
                    : HostCompat.isV236() ? "xa5"
                    : HostCompat.isGooglePlay() ? "uc5" : "pa5";
        } else if (HostCompat.isV230()) {
            ownerName = "x45";
        } else {
            ownerName = "t25";
        }
        try {
            Class<?> owner = cl.loadClass(ownerName);
            int parserHooks = 0;
            int rendererHooks = 0;
            for (Method method : owner.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (types.length == 0 || types[0] != String.class) continue;
                boolean legacyParser = !HostCompat.isV230()
                        && "a".equals(method.getName())
                        && types.length == 1
                        && method.getReturnType() != void.class;
                boolean modernParser = (HostCompat.isV230() || HostCompat.isV253())
                        && "h".equals(method.getName())
                        && method.getReturnType() != void.class
                        && types.length >= 5 && types.length <= 6;
                boolean modernRenderer = (HostCompat.isV230() || HostCompat.isV253())
                        && "d".equals(method.getName())
                        && method.getReturnType() == void.class
                        && types.length == 5;
                if (!legacyParser && !modernParser && !modernRenderer) continue;
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object raw = chain.getArg(0);
                        if (!(raw instanceof String)) return chain.proceed();
                        String source = (String) raw;
                        String safe = sanitizeAgentTransportAtRenderBoundary(source);
                        beginAgentToolLabelParse(safe);
                        try {
                            if (safe.equals(source)) return chain.proceed();
                            Object[] args = chain.getArgs().toArray();
                            args[0] = safe;
                            int hits = NATIVE_MARKDOWN_SANITIZE_HITS.incrementAndGet();
                            if (hits <= 12 || hits % 100 == 0) {
                                Main.log("heartbeat native Markdown source sanitized owner="
                                        + ownerName + "." + method.getName()
                                        + " hit=" + hits + " raw=" + source.length()
                                        + " visible=" + safe.length());
                            }
                            return chain.proceed(args);
                        } finally {
                            endAgentToolLabelParse();
                        }
                    }
                });
                if (modernRenderer) rendererHooks++; else parserHooks++;
            }
            Main.log("heartbeat native Markdown privacy hooks parser=" + parserHooks
                    + " renderer=" + rendererHooks + " owner=" + ownerName);
        } catch (Throwable error) {
            Main.log("heartbeat native Markdown privacy hook unavailable owner="
                    + ownerName + ": " + error);
        }
    }

    /** Decodes the kotlinx JsonElement carried by the host's content patch without host symbols. */
    private static String decodeHeartbeatJsonString(Object jsonElement) {
        if (jsonElement == null) return null;
        try {
            Object decoded = new org.json.JSONTokener(
                    String.valueOf(jsonElement)).nextValue();
            return decoded instanceof String ? (String) decoded : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void markHeartbeatContentChanged(Object patchState) {
        if (patchState == null) return;
        for (Class<?> type = patchState.getClass(); type != null;
             type = type.getSuperclass()) {
            try {
                Field changed = type.getDeclaredField("b");
                if (changed.getType() != boolean.class) continue;
                changed.setAccessible(true);
                changed.setBoolean(patchState, true);
                return;
            } catch (Throwable ignored) {}
        }
    }

    private static void dispatchStaticAgentToolFallback(
            HeartbeatToolProtocol.Result parsed) {
        if (parsed == null || !AgentToolConfig.enabledFast()) return;
        ArrayList<HeartbeatToolProtocol.ToolCall> authorized = new ArrayList<>();
        for (HeartbeatToolProtocol.ToolCall call : parsed.calls) {
            if (isAuthorizedInteractiveAgentToolCall(call)) authorized.add(call);
        }
        if (!authorized.isEmpty()) {
            Main.log("Agent static response fallback accepted calls="
                    + authorized.size());
            Main.executeHeartbeatToolCalls(HookSessionManagement.currentHostContext(), authorized, true);
        }
        for (HeartbeatToolProtocol.RejectedCall rejected : parsed.rejectedCalls) {
            HeartbeatToolProtocol.ToolCall call = rejected == null
                    ? null : rejected.call;
            if (isAuthorizedInteractiveAgentToolCall(call)) {
                queueRejectedAgentToolResult(HookSessionManagement.currentHostContext(), rejected);
            }
        }
    }

    void hookHeartbeatToolStatusStyle(
            ClassLoader cl, String rendererClassName, String styleClassName) {
        try {
            Class<?> renderer = cl.loadClass(rendererClassName);
            Class<?> styleClass = cl.loadClass(styleClassName);
            final Method styleCopy = findHeartbeatToolStatusStyleCopy(styleClass);
            int stringHooks = 0;
            int annotatedHooks = 0;
            for (Method method : renderer.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if ("b".equals(method.getName()) && types.length == 18
                        && types[0] == String.class
                        && types[2] == long.class && types[4] == long.class
                        && types[13] == styleClass
                        && types[15] == int.class && types[16] == int.class
                        && types[17] == int.class) {
                    Main.MODULE.hook(method).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            Object raw = chain.getArg(0);
                            if (!(raw instanceof String)) {
                                return chain.proceed();
                            }
                            String original = (String) raw;
                            String marked = sanitizeAgentTransportAtRenderBoundary(original);
                            boolean registered = HeartbeatToolProtocol
                                    .isRegisteredToolStatusText(marked);
                            if (HeartbeatToolProtocol.isCompletePrivateTransportBody(marked)) {
                                Object[] hiddenArgs = chain.getArgs().toArray();
                                hiddenArgs[0] = "";
                                return chain.proceed(hiddenArgs);
                            }
                            if (!HeartbeatToolProtocol.hasToolStatusStyleMarker(marked)
                                    && !registered) {
                                if (marked.equals(original)) return chain.proceed();
                                Object[] filteredArgs = chain.getArgs().toArray();
                                filteredArgs[0] = marked;
                                return chain.proceed(filteredArgs);
                            }
                            Object[] args = chain.getArgs().toArray();
                            args[0] = HeartbeatToolProtocol
                                    .stripToolStatusStyleMarkers(marked);
                            if (HeartbeatToolProtocol.isIsolatedToolStatusText(marked)
                                    || registered) {
                                if (renderNativeAgentSearch(marked, args[14])) return null;
                                if (renderNativeAgentToolLog(marked, args[14])) return null;
                                try {
                                    args[2] = Long.valueOf(
                                            HeartbeatToolProtocol.TOOL_STATUS_GRAY_COLOR);
                                    Object style = args[13];
                                    long fontSize =
                                            scaledHeartbeatToolStatusFontSize(style);
                                    args[4] = Long.valueOf(fontSize);
                                    if (style != null) {
                                        args[13] = copyHeartbeatToolStatusTextStyle(
                                                styleCopy, style, fontSize);
                                    }
                                    Object mask = args[17];
                                    if (mask instanceof Number) {
                                        args[17] = Integer.valueOf(
                                                HeartbeatToolProtocol
                                                        .explicitToolStatusStyleMask(
                                                                ((Number) mask).intValue()));
                                    }
                                    logHeartbeatToolStatusStyleHit(
                                            rendererClassName, "String");
                                } catch (Throwable t) {
                                    logHeartbeatToolStatusStyleError(
                                            rendererClassName, "String", t);
                                }
                            }
                            return chain.proceed(args);
                        }
                    });
                    stringHooks++;
                    continue;
                }
                if (!"c".equals(method.getName()) || types.length != 17
                        || !CharSequence.class.isAssignableFrom(types[0])
                        || types[2] != long.class || types[3] != long.class
                        || types[12] != styleClass
                        || types[14] != int.class || types[15] != int.class
                        || types[16] != int.class) {
                    continue;
                }
                final Constructor<?> annotatedTextConstructor =
                        findAnnotatedTextConstructor(types[0]);
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object raw = chain.getArg(0);
                        if (!(raw instanceof CharSequence)) return chain.proceed();
                        String original = raw.toString();
                        String marked = sanitizeAgentTransportAtRenderBoundary(original);
                        boolean registered = HeartbeatToolProtocol
                                .isRegisteredToolStatusText(marked);
                        if (HeartbeatToolProtocol.isCompletePrivateTransportBody(marked)) {
                            Object[] hiddenArgs = chain.getArgs().toArray();
                            hiddenArgs[0] = newAnnotatedText(
                                    annotatedTextConstructor, "");
                            return chain.proceed(hiddenArgs);
                        }
                        if (!HeartbeatToolProtocol.hasToolStatusStyleMarker(marked)
                                && !registered) {
                            if (marked.equals(original)) return chain.proceed();
                            Object[] filteredArgs = chain.getArgs().toArray();
                            filteredArgs[0] = newAnnotatedText(
                                    annotatedTextConstructor, marked);
                            return chain.proceed(filteredArgs);
                        }
                        Object[] args = chain.getArgs().toArray();
                        String clean =
                                HeartbeatToolProtocol.stripToolStatusStyleMarkers(marked);
                        try {
                            args[0] = newAnnotatedText(
                                    annotatedTextConstructor, clean);
                            if (HeartbeatToolProtocol.isIsolatedToolStatusText(marked)
                                    || registered) {
                                if (renderNativeAgentSearch(marked, args[13])) return null;
                                if (renderNativeAgentToolLog(marked, args[13])) return null;
                                Object style = args[12];
                                if (style != null) {
                                    long fontSize =
                                            scaledHeartbeatToolStatusFontSize(style);
                                    args[12] = copyHeartbeatToolStatusTextStyle(
                                            styleCopy, style, fontSize);
                                }
                                logHeartbeatToolStatusStyleHit(
                                        rendererClassName, "AnnotatedString");
                            }
                        } catch (Throwable t) {
                            logHeartbeatToolStatusStyleError(
                                    rendererClassName, "AnnotatedString", t);
                        }
                        return chain.proceed(args);
                    }
                });
                annotatedHooks++;
            }
            Main.log("heartbeat tool status Compose hooks string=" + stringHooks
                    + " annotated=" + annotatedHooks
                    + " renderer=" + rendererClassName);
        } catch (Throwable t) {
            Main.log("heartbeat tool status renderer unavailable "
                    + rendererClassName + ": " + t);
        }
    }

    private static Method findHeartbeatToolStatusStyleCopy(
            Class<?> styleClass) throws NoSuchMethodException {
        for (Method method : styleClass.getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if ("e".equals(method.getName())
                    && (types.length == 13 || types.length == 14)
                    && types[0] == styleClass
                    && types[1] == long.class && types[2] == long.class
                    && types[types.length - 1] == int.class
                    && java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(styleClass.getName() + ".e(TextStyle copy)");
    }

    private static Constructor<?> findAnnotatedTextConstructor(Class<?> textClass)
            throws NoSuchMethodException {
        try {
            Constructor<?> direct = textClass.getDeclaredConstructor(String.class);
            direct.setAccessible(true);
            return direct;
        } catch (NoSuchMethodException ignored) {
            Constructor<?> current = textClass.getDeclaredConstructor(
                    List.class, String.class);
            current.setAccessible(true);
            return current;
        }
    }

    private static Object newAnnotatedText(
            Constructor<?> constructor, String text) throws Exception {
        return constructor.getParameterTypes().length == 1
                ? constructor.newInstance(text)
                : constructor.newInstance(Collections.emptyList(), text);
    }

    void hookHeartbeatToolStatusBasicText(
            ClassLoader cl, String rendererClassName, String methodName,
            String styleClassName) {
        try {
            Class<?> renderer = cl.loadClass(rendererClassName);
            Class<?> styleClass = cl.loadClass(styleClassName);
            final Method styleCopy = findHeartbeatToolStatusStyleCopy(styleClass);
            int hooked = 0;
            for (Method method : renderer.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!methodName.equals(method.getName()) || types.length != 14
                        || !CharSequence.class.isAssignableFrom(types[0])
                        || types[2] != styleClass
                        || types[4] != int.class || types[5] != boolean.class
                        || types[6] != int.class || types[7] != int.class
                        || !Map.class.isAssignableFrom(types[8])
                        || types[11] != int.class || types[12] != int.class
                        || types[13] != int.class) {
                    continue;
                }
                final Constructor<?> annotatedTextConstructor =
                        findAnnotatedTextConstructor(types[0]);
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object raw = chain.getArg(0);
                        if (!(raw instanceof CharSequence)) return chain.proceed();
                        String original = raw.toString();
                        String rendered = sanitizeAgentTransportAtRenderBoundary(original);
                        if (HeartbeatToolProtocol.isCompletePrivateTransportBody(rendered)) {
                            Object[] hiddenArgs = chain.getArgs().toArray();
                            hiddenArgs[0] = newAnnotatedText(
                                    annotatedTextConstructor, "");
                            return chain.proceed(hiddenArgs);
                        }
                        boolean marked =
                                HeartbeatToolProtocol.hasToolStatusStyleMarker(rendered);
                        boolean registered =
                                HeartbeatToolProtocol.isRegisteredToolStatusText(rendered);
                        if (!marked && !registered) {
                            if (rendered.equals(original)) return chain.proceed();
                            Object[] filteredArgs = chain.getArgs().toArray();
                            filteredArgs[0] = newAnnotatedText(
                                    annotatedTextConstructor, rendered);
                            return chain.proceed(filteredArgs);
                        }
                        Object[] args = chain.getArgs().toArray();
                        try {
                            if (marked || registered) {
                                args[0] = newAnnotatedText(
                                        annotatedTextConstructor,
                                        HeartbeatToolProtocol
                                                .stripToolStatusStyleMarkers(rendered));
                            }
                            if (registered) {
                                if (renderNativeAgentSearch(rendered, args[10])) return null;
                                if (renderNativeAgentToolLog(rendered, args[10])) return null;
                                Object style = args[2];
                                if (style != null) {
                                    long fontSize =
                                            scaledHeartbeatToolStatusFontSize(style);
                                    args[2] = copyHeartbeatToolStatusTextStyle(
                                            styleCopy, style, fontSize);
                                }
                                logHeartbeatToolStatusStyleHit(
                                        rendererClassName, "BasicText");
                            }
                        } catch (Throwable t) {
                            logHeartbeatToolStatusStyleError(
                                    rendererClassName, "BasicText", t);
                        }
                        return chain.proceed(args);
                    }
                });
                hooked++;
            }
            Main.log("heartbeat tool status BasicText hooks=" + hooked
                    + " renderer=" + rendererClassName + "." + methodName);
        } catch (Throwable t) {
            Main.log("heartbeat tool status BasicText renderer unavailable "
                    + rendererClassName + "." + methodName + ": " + t);
        }
    }

    private static Object copyHeartbeatToolStatusTextStyle(
            Method styleCopy, Object style, long fontSize) throws Exception {
        Class<?>[] parameterTypes = styleCopy.getParameterTypes();
        Object[] values = new Object[parameterTypes.length];
        values[0] = style;
        values[1] = Long.valueOf(HeartbeatToolProtocol.TOOL_STATUS_GRAY_COLOR);
        values[2] = Long.valueOf(fontSize);
        for (int index = 3; index < values.length; index++) {
            Class<?> type = parameterTypes[index];
            if (type == long.class) values[index] = Long.valueOf(0L);
            else if (type == int.class) values[index] = Integer.valueOf(0);
            else if (type == boolean.class) values[index] = Boolean.FALSE;
            else values[index] = null;
        }
        values[values.length - 1] = Integer.valueOf(
                HeartbeatToolProtocol.TOOL_STATUS_TEXT_STYLE_COPY_MASK);
        return styleCopy.invoke(null, values);
    }

    private static volatile Method nativeAgentToolLogRenderer;

    /** Uses DeepSeek 2.3.6's real search composable and result rows. */
    private static boolean renderNativeAgentSearch(String marked, Object composer) {
        if (composer == null || Boolean.TRUE.equals(AGENT_NATIVE_SEARCH_RENDERING.get())) {
            return false;
        }
        String clean = HeartbeatToolProtocol.stripToolStatusStyleMarkers(marked).trim();
        NativeSearchGroup group = AGENT_NATIVE_SEARCH_CALLS.get(clean);
        if (group == null || group.traceKeys.isEmpty()) return false;
        ArrayList<AgentToolTraceStore.Trace> traces = new ArrayList<>();
        boolean allFinished = true;
        boolean anySuccess = false;
        String failureDetail = null;
        for (String key : group.traceKeys) {
            AgentToolTraceStore.Trace trace = AgentToolTraceStore.find(key, "{}");
            traces.add(trace);
            allFinished &= trace.finished;
            anySuccess |= trace.finished && trace.success;
            if (failureDetail == null && trace.finished && !trace.success) {
                failureDetail = nativeSearchFailureDetail(trace.output);
            }
        }
        try {
            AGENT_NATIVE_SEARCH_RENDERING.set(Boolean.TRUE);
            ClassLoader loader = composer.getClass().getClassLoader();
            Object results = createNativeSearchResults(loader, traces);
            boolean rendered = renderNativeSearchState(loader, composer,
                    group.queries, results, allFinished, anySuccess, failureDetail);
            if (rendered) Main.log("native Agent search rendered calls=" + traces.size()
                    + " finished=" + allFinished + " successful=" + anySuccess
                    + " results=" + (results instanceof List
                    ? ((List<?>) results).size() : 0));
            return rendered;
        } catch (Throwable error) {
            logHeartbeatToolStatusStyleError("DeepSeekNativeSearch", "Compose", error);
            Main.log("native Agent search render failed: " + Main.safeThrowableMessage(error));
            return false;
        } finally {
            AGENT_NATIVE_SEARCH_RENDERING.remove();
        }
    }

    private static boolean renderNativeSearchState(
            ClassLoader loader, Object composer, List<String> queryTexts,
            Object results, boolean allFinished, boolean anySuccess,
            String failureDetail) throws Exception {
        // code257 re-obfuscated the complete native TOOL_SEARCH Compose chain. Keep its
        // symbols local to the 2.4.1 adapter so code249 and every older host continue to
        // execute the exact pre-existing rg3/fh0 implementation.
        final boolean v241 = HostCompat.isV241();
        Class<?> stateType = Class.forName(v241 ? "il3" : "rg3", false, loader);
        Object execution = staticHostField(loader, v241 ? "g51" : "t31", "a");
        boolean hasResults = results instanceof List && !((List<?>) results).isEmpty();
        String statusField = !allFinished ? "a"
                : hasResults ? "b" : anySuccess ? "c" : "d";
        Object status = staticHostField(loader, v241 ? "lc0" : "zb0", statusField);
        Object modifier = staticHostField(loader, v241 ? "jv5" : "pp5", "a");
        Constructor<?> queryConstructor = null;
        for (Constructor<?> constructor : Class.forName(v241 ? "qo5" : "gj5", false, loader)
                .getDeclaredConstructors()) {
            Class<?>[] types = constructor.getParameterTypes();
            if (types.length == 2 && types[0] == int.class
                    && types[1] == String.class) {
                constructor.setAccessible(true);
                queryConstructor = constructor;
                break;
            }
        }
        ArrayList<Object> queries = new ArrayList<>();
        if (queryConstructor != null && queryTexts != null) {
            for (String query : queryTexts) {
                if (query == null || query.trim().length() == 0) continue;
                queries.add(queryConstructor.newInstance(
                        Integer.valueOf(1), query.trim()));
            }
        }
        Object executionState = constantHostState(loader, stateType, execution);
        Object statusState = constantHostState(loader, stateType, status);
        Object resultsState = constantHostState(loader, stateType, results);
        Object queriesState = constantHostState(loader, stateType, queries);
        Object tipState = constantHostState(loader, stateType,
                allFinished && !anySuccess ? failureDetail : null);
        Object expandedState = constantHostState(loader, stateType, Boolean.FALSE);
        Class<?> owner = Class.forName(v241 ? "mt4" : "fh0", false, loader);
        for (Method method : owner.getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if (!(v241 ? "h" : "c").equals(method.getName()) || types.length != 10
                    || types[0] != String.class || types[9] != int.class) continue;
            method.setAccessible(true);
            method.invoke(null, "TOOL_SEARCH", executionState, statusState,
                    resultsState, queriesState, tipState, expandedState,
                    modifier, composer, Integer.valueOf(0));
            return true;
        }
        return false;
    }

    private static Object constantHostState(
            ClassLoader loader, final Class<?> stateType, final Object value) {
        return Proxy.newProxyInstance(loader, new Class<?>[]{stateType},
                new InvocationHandler() {
                    @Override public Object invoke(Object proxy, Method method, Object[] args) {
                        String name = method.getName();
                        if ("u".equals(name) || "getValue".equals(name)) return value;
                        if ("toString".equals(name)) return "NativeSearchState(" + value + ")";
                        if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                        if ("equals".equals(name)) return proxy == (args == null ? null : args[0]);
                        return null;
                    }
                });
    }

    private static Object staticHostField(
            ClassLoader loader, String owner, String field) throws Exception {
        Field value = Class.forName(owner, false, loader).getDeclaredField(field);
        value.setAccessible(true);
        return value.get(null);
    }

    private static Object createNativeSearchResults(
            ClassLoader loader, List<AgentToolTraceStore.Trace> traces) throws Exception {
        final boolean v241 = HostCompat.isV241();
        Class<?> listType = Class.forName(v241 ? "wo5" : "mj5", false, loader);
        Constructor<?> listConstructor = listType.getDeclaredConstructor();
        listConstructor.setAccessible(true);
        Object nativeList = listConstructor.newInstance();
        Field backingField = listType.getDeclaredField("a");
        backingField.setAccessible(true);
        Object backing = backingField.get(nativeList);
        Method add = backing.getClass().getMethod("add", Object.class);
        Constructor<?> resultConstructor = null;
        for (Constructor<?> constructor : Class.forName(v241 ? "to5" : "jj5", false, loader)
                .getDeclaredConstructors()) {
            if (constructor.getParameterTypes().length == 9) {
                constructor.setAccessible(true);
                resultConstructor = constructor;
                break;
            }
        }
        if (resultConstructor == null) return nativeList;
        HashSet<String> seenUrls = new HashSet<>();
        int citationIndex = 1;
        for (int traceIndex = 0; traces != null && traceIndex < traces.size(); traceIndex++) {
            AgentToolTraceStore.Trace trace = traces.get(traceIndex);
            if (trace == null || !trace.finished || !trace.success) continue;
            JSONObject traceJson = new JSONObject(trace.output);
            JSONObject payload = traceJson.optJSONObject("output");
            JSONArray pages = payload == null ? null : payload.optJSONArray("results");
            if (pages == null) continue;
            for (int index = 0; index < pages.length() && citationIndex <= 10; index++) {
                JSONObject page = pages.optJSONObject(index);
                if (page == null) continue;
                String url = page.optString("url", "");
                String title = page.optString("title", "");
                String snippet = page.optString("snippet", page.optString("text", ""));
                if (snippet.length() > 360) snippet = snippet.substring(0, 360) + "…";
                if (url.length() == 0 || title.length() == 0 || !seenUrls.add(url)) continue;
                Object nativeResult = resultConstructor.newInstance(
                        Integer.valueOf(175), url, title, snippet,
                        Integer.valueOf(citationIndex++), null,
                        page.optString("site_name", ""), null,
                        Collections.singletonList(Integer.valueOf(traceIndex)));
                add.invoke(backing, nativeResult);
            }
            if (citationIndex > 10) break;
        }
        return nativeList;
    }

    private static String nativeSearchFailureDetail(String traceOutput) {
        try {
            String detail = new JSONObject(traceOutput).optString("detail", "");
            return detail.length() == 0 ? null : detail;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean renderNativeAgentToolLog(String marked, Object composer) {
        if (composer == null || marked == null) return false;
        String clean = HeartbeatToolProtocol.stripToolStatusStyleMarkers(marked);
        ClassLoader hostLoader = composer.getClass().getClassLoader();
        if (NativeModelFileCardRenderer.renderToolLog(hostLoader, composer, clean)) {
            logHeartbeatToolStatusStyleHit("NativeModelFileCardRenderer", "card");
            return true;
        }
        try {
            Method renderer = nativeAgentToolLogRenderer;
            if (renderer == null) {
                Class<?> owner = Class.forName("com.smallhairball.deepx.AgentToolLogCompose",
                        true, Main.class.getClassLoader());
                for (Method candidate : owner.getDeclaredMethods()) {
                    Class<?>[] types = candidate.getParameterTypes();
                    if ("Render".equals(candidate.getName()) && types.length == 3
                            && types[0] == String.class && types[2] == int.class) {
                        candidate.setAccessible(true);
                        renderer = candidate;
                        nativeAgentToolLogRenderer = candidate;
                        break;
                    }
                }
            }
            if (renderer == null || !renderer.getParameterTypes()[1].isInstance(composer)) {
                return false;
            }
            renderer.invoke(null,
                    clean,
                    composer, Integer.valueOf(0));
            return true;
        } catch (Throwable error) {
            logHeartbeatToolStatusStyleError("AgentToolLogCompose", "native", error);
            return false;
        }
    }

    /**
     * Last-resort privacy boundary for both assistant and user text composables. The host can
     * render an optimistic row before its mutable response object or visible-thread list reaches
     * our model hooks. Sanitizing only strings that resemble our private prefix keeps the normal
     * text hot path allocation-free while ensuring neither a streamed call envelope nor a hidden
     * result event can flash on screen.
     */
    private static String sanitizeAgentTransportAtRenderBoundary(String value) {
        return HeartbeatToolProtocol.sanitizeAtRenderBoundary(value);
    }

    private static long scaledHeartbeatToolStatusFontSize(Object style) {
        try {
            Object spanStyle = Main.readHostField(style, "a");
            Object packedValue = Main.readHostField(spanStyle, "b");
            if (packedValue instanceof Number) {
                long packed = ((Number) packedValue).longValue();
                float source = Float.intBitsToFloat((int) packed);
                long unit = packed & 0xFFFFFFFF00000000L;
                if (unit != 0L && !Float.isNaN(source)
                        && !Float.isInfinite(source) && source > 0.0f) {
                    float target = source
                            * HeartbeatToolProtocol.TOOL_STATUS_FONT_SCALE;
                    return unit
                            | (((long) Float.floatToRawIntBits(target))
                            & 0xFFFFFFFFL);
                }
            }
        } catch (Throwable ignored) {}
        return HeartbeatToolProtocol.TOOL_STATUS_FONT_SIZE;
    }

    private static void logHeartbeatToolStatusStyleHit(
            String rendererClassName, String overload) {
        if (HEARTBEAT_STATUS_STYLE_HIT_LOGGED.compareAndSet(false, true)) {
            Main.log("heartbeat tool status style applied renderer="
                    + rendererClassName + " overload=" + overload);
        }
    }

    private static void logHeartbeatToolStatusStyleError(
            String rendererClassName, String overload, Throwable error) {
        if (HEARTBEAT_STATUS_STYLE_ERROR_LOGGED.compareAndSet(false, true)) {
            Main.log("heartbeat tool status style failed renderer="
                    + rendererClassName + " overload=" + overload + ": " + error);
        }
    }

    private static void sanitizeLiveHeartbeatResponse(
            Object fragment, boolean appendUpdate, boolean executeTools) {
        if (fragment == null) return;
        Object stateValue = liveResponseTextState(fragment);
        if (stateValue == null) return;
        Object current = Main.invokeNoArg(stateValue, "getValue");
        if (!(current instanceof String)) return;
        String hostText = (String) current;
        Main.HeartbeatSanitizedUpdate update = prepareHeartbeatStateUpdate(
                fragment, hostText, appendUpdate, executeTools);
        if (!update.safe.equals(hostText)) {
            setHeartbeatSanitizedState(stateValue, update.safe);
            synchronized (HEARTBEAT_RESPONSE_STREAMS) {
                HeartbeatResponseStream stream = HEARTBEAT_RESPONSE_STREAMS.get(fragment);
                if (stream != null && !stream.liveSanitizeLogged) {
                    stream.liveSanitizeLogged = true;
                    Main.log("heartbeat live response sanitized before frame fragment="
                            + fragment.getClass().getSimpleName());
                }
            }
        }
        if (!update.calls.isEmpty()) {
            Main.log("Agent live response accepted calls=" + update.calls.size());
        }
        Main.dispatchHeartbeatStateUpdate(update);
    }

    /** Stateful screen monitor: hide from a partial opening marker through its closing marker. */
    static void sanitizeHeartbeatFragmentAtRenderBoundary(Object fragment) {
        if (fragment == null) return;
        String type = String.valueOf(Main.readHostField(fragment, "a"));
        if ("THINK".equals(type)) {
            sanitizeThinkingFragmentAtRenderBoundary(fragment);
            return;
        }
        if (!"RESPONSE".equals(type)) return;
        sanitizeModelFileStreamAtRenderBoundary(fragment);
        if (!AgentToolConfig.enabledFast()) return;
        Object state = liveResponseTextState(fragment);
        Object current = state == null ? null : Main.invokeNoArg(state, "getValue");
        if (!(current instanceof String)) return;
        String raw = (String) current;
        if (!shouldMonitorHeartbeatFragment(fragment, raw)) return;

        // appendUpdate=true is essential here. Once the opening marker has been removed from the
        // mutable State, DeepSeek appends the next SSE delta to the safe prefix. The retained raw
        // stream reconstructs those deltas and keeps them hidden until CONTROL_END arrives.
        Main.HeartbeatSanitizedUpdate update = prepareHeartbeatStateUpdate(
                fragment, raw, true, true);
        if (!update.safe.equals(raw)
                && setHeartbeatSanitizedState(state, update.safe)) {
            synchronized (HEARTBEAT_RESPONSE_STREAMS) {
                HeartbeatResponseStream stream = HEARTBEAT_RESPONSE_STREAMS.get(fragment);
                if (stream != null && !stream.renderSanitizeLogged) {
                    stream.renderSanitizeLogged = true;
                    Main.log("heartbeat screen monitor hid streamed control block fragment="
                            + fragment.getClass().getSimpleName());
                }
            }
        }
        Main.dispatchHeartbeatStateUpdate(update);
    }

    private static void sanitizeThinkingFragmentAtRenderBoundary(Object fragment) {
        if (!AgentToolConfig.enabledFast() || !AgentToolConfig.hideToolLogs()) return;
        Object state = liveResponseTextState(fragment);
        Object current = state == null ? null : Main.invokeNoArg(state, "getValue");
        if (!(current instanceof String)) return;
        String raw = (String) current;
        Main.HeartbeatSanitizedUpdate update = prepareThinkingStateUpdate(
                fragment, raw, true);
        if (!update.safe.equals(raw)
                && setHeartbeatSanitizedState(state, update.safe)) {
            ThinkingAgentStream stream = THINKING_AGENT_STREAMS.get(fragment);
            if (stream != null && !stream.renderSanitizeLogged) {
                stream.renderSanitizeLogged = true;
                Main.log("heartbeat THINK screen monitor hid tool narration fragment="
                        + fragment.getClass().getSimpleName());
            }
        }
    }

    /** Hides a dual-chat file body on the first streamed header and drives its live card. */
    private static void sanitizeModelFileStreamAtRenderBoundary(Object fragment) {
        if (fragment == null || !NativeDualChatBridge.isActive()) return;
        Object state = liveResponseTextState(fragment);
        sanitizeModelFileStreamState(fragment, state);
    }

    /** Runs immediately before DeepSeek's own Markdown parser reads the streamed response. */
    private static void sanitizeModelFileMarkdownStateBeforeRead(Object observableState) {
        if (observableState == null || !NativeDualChatBridge.isActive()) return;
        sanitizeModelFileStreamState(observableState, observableState);
    }

    private static void sanitizeModelFileStreamState(Object streamKey, Object state) {
        Object currentValue = state == null ? null : Main.invokeNoArg(state, "getValue");
        if (!(currentValue instanceof String)) return;
        String hostText = (String) currentValue;
        ModelFileOutput.StreamingParsed parsed;
        Object canonicalState = HookAttachmentPipeline.canonicalMutableTextState(state);
        Object canonicalKey = canonicalState == null ? streamKey : canonicalState;
        synchronized (HookAttachmentPipeline.MODEL_FILE_STREAMS) {
            ModelFileOutput.StreamingAccumulator stream =
                    HookAttachmentPipeline.MODEL_FILE_STREAMS.get(canonicalKey);
            if (stream == null) {
                stream = new ModelFileOutput.StreamingAccumulator();
                HookAttachmentPipeline.MODEL_FILE_STREAMS.put(canonicalKey, stream);
            }
            parsed = stream.update(hostText);
        }
        if (!parsed.visibleText.equals(hostText)) {
            writeObservableTextState(state, parsed.visibleText);
        }
        if (!parsed.files.isEmpty()) {
            NativeDualChatBridge.updateFileStream(
                    HookChatPipeline.lastInteractiveConversationId, parsed.files);
        }
    }

    private static void sanitizeHeartbeatMarkdownStateBeforeRead(Object observableState) {
        if (observableState == null || !AgentToolConfig.enabledFast()) return;
        Object canonicalState = HookAttachmentPipeline.canonicalMutableTextState(observableState);
        WeakReference<Object> thinkingReference =
                THINKING_AGENT_STATES.get(canonicalState);
        Object thinkingFragment = thinkingReference == null
                ? null : thinkingReference.get();
        if (thinkingFragment != null && AgentToolConfig.hideToolLogs()) {
            Object currentThinking = Main.invokeNoArg(observableState, "getValue");
            if (!(currentThinking instanceof String)) return;
            String rawThinking = (String) currentThinking;
            Main.HeartbeatSanitizedUpdate thinkingUpdate =
                    prepareThinkingStateUpdate(
                            thinkingFragment, rawThinking, true);
            if (!thinkingUpdate.safe.equals(rawThinking)) {
                writeHeartbeatObservableTextState(observableState, thinkingUpdate.safe);
            }
            return;
        }
        Object current = Main.invokeNoArg(observableState, "getValue");
        if (!(current instanceof String)) return;
        String raw = (String) current;
        if (!shouldMonitorHeartbeatFragment(observableState, raw)) return;

        Main.HeartbeatSanitizedUpdate update = prepareHeartbeatStateUpdate(
                observableState, raw, true, true);
        if (!update.safe.equals(raw)) {
            boolean written = writeHeartbeatObservableTextState(
                    observableState, update.safe);
            if (written) {
                synchronized (HEARTBEAT_RESPONSE_STREAMS) {
                    HeartbeatResponseStream stream =
                            HEARTBEAT_RESPONSE_STREAMS.get(observableState);
                    if (stream != null && !stream.markdownSanitizeLogged) {
                        stream.markdownSanitizeLogged = true;
                        Main.log("heartbeat Markdown input hid streamed control block state="
                                + observableState.getClass().getSimpleName());
                    }
                }
            }
        }
        Main.dispatchHeartbeatStateUpdate(update);
    }

    static boolean shouldMonitorHeartbeatFragment(Object fragment, String value) {
        synchronized (HEARTBEAT_RESPONSE_STREAMS) {
            HeartbeatResponseStream stream = heartbeatResponseStreamLocked(fragment, false);
            if (stream != null && stream.initialized
                    && stream.privateGateOpen) {
                return true;
            }
        }
        if (value == null || value.length() == 0) return false;
        if (value.indexOf("DEEKSEEP_LOCAL_TOOL") >= 0
                || value.indexOf("DEEKSEEP\\_LOCAL\\_TOOL") >= 0) return true;
        String marker = HeartbeatToolProtocol.CONTROL_START;
        int maximum = Math.min(value.length(), marker.length() - 1);
        for (int length = maximum; length > 0; length--) {
            if (value.regionMatches(value.length() - length,
                    marker, 0, length)) return true;
        }
        return false;
    }

    static Main.HeartbeatSanitizedUpdate prepareHeartbeatStateUpdate(
            Object fragment, String hostText, boolean appendUpdate,
            boolean executeTools) {
        ArrayList<HeartbeatToolProtocol.ToolCall> freshCalls = new ArrayList<>();
        ArrayList<HeartbeatToolProtocol.RejectedCall> freshRejectedCalls =
                new ArrayList<>();
        String safe;
        synchronized (HEARTBEAT_RESPONSE_STREAMS) {
            HeartbeatResponseStream stream = heartbeatResponseStreamLocked(fragment, true);
            if (appendUpdate && stream.initialized
                    && hostText.startsWith(stream.visible)) {
                stream.raw = stream.raw
                        + hostText.substring(stream.visible.length());
            } else {
                if (HostCompat.isV241() && stream.initialized
                        && !hostText.equals(stream.visible)
                        && !hostText.startsWith(stream.visible)) {
                    resetV241HeartbeatResponseStream(stream);
                }
                stream.raw = hostText;
            }
            HeartbeatToolProtocol.Result parsed = executeTools
                    ? HeartbeatToolProtocol.parseForConversation(stream.raw)
                    : HeartbeatToolProtocol.parse(stream.raw);
            safe = parsed.visibleText;
            stream.privateGateOpen = parsed.incompleteControlBlock;
            stream.visible = safe;
            stream.initialized = true;
            if (executeTools && !parsed.rejectedCalls.isEmpty()
                    && !stream.invalidControlLogged
                    && stream.raw.indexOf(HeartbeatToolProtocol.CONTROL_END) >= 0) {
                stream.invalidControlLogged = true;
                HeartbeatToolProtocol.RejectedCall rejected =
                        parsed.rejectedCalls.get(0);
                HeartbeatToolProtocol.ToolCall call = rejected.call;
                Main.log("Agent control block rejected before execution"
                        + " reason=" + rejected.reason
                        + " tool=" + (call == null ? "" : call.tool)
                        + " scope=" + (call == null ? "" : call.scope)
                        + " response_chars=" + stream.raw.length());
            } else if (executeTools && parsed.calls.isEmpty()
                    && parsed.rejectedCalls.isEmpty()
                    && !stream.invalidControlLogged
                    && stream.raw.indexOf(HeartbeatToolProtocol.CONTROL_START) >= 0
                    && stream.raw.indexOf(HeartbeatToolProtocol.CONTROL_END) >= 0) {
                stream.invalidControlLogged = true;
                Main.log("Agent control block was hidden but could not be parsed"
                        + " response_chars=" + stream.raw.length());
            }
            // The request-side scope lease identifies a real visible-chat generation. Do not use
            // the local-API semaphore here: an unrelated request can own it for a moment and used
            // to make this valid call disappear permanently.
            if (executeTools && AgentToolConfig.enabledFast()) {
                for (HeartbeatToolProtocol.ToolCall call : parsed.calls) {
                    if (!isAuthorizedInteractiveAgentToolCall(call)) {
                        if (!stream.unauthorizedCallLogged) {
                            stream.unauthorizedCallLogged = true;
                            Main.log("Agent control block ignored outside authorized visible chat"
                                    + " scope=" + call.scope + " tool=" + call.tool);
                        }
                        continue;
                    }
                    String fingerprint = call.scope + "|" + call.id + "|" + call.tool;
                    if (stream.executed.add(fingerprint)) freshCalls.add(call);
                }
                for (HeartbeatToolProtocol.RejectedCall rejected
                        : parsed.rejectedCalls) {
                    HeartbeatToolProtocol.ToolCall call = rejected == null
                            ? null : rejected.call;
                    if (!isAuthorizedInteractiveAgentToolCall(call)) {
                        if (!stream.unauthorizedCallLogged) {
                            stream.unauthorizedCallLogged = true;
                            Main.log("Rejected Agent control block ignored outside authorized"
                                    + " visible chat scope="
                                    + (call == null ? "" : call.scope));
                        }
                        continue;
                    }
                    String fingerprint = call.scope + "|" + call.id + "|"
                            + call.tool + "|rejected";
                    if (stream.executed.add(fingerprint)) {
                        freshRejectedCalls.add(rejected);
                    }
                }
            }
        }
        return new Main.HeartbeatSanitizedUpdate(
                safe, freshCalls, freshRejectedCalls);
    }

    /**
     * Rebuilds the private stream from the decoded SSE delta itself.  Once a control prefix is
     * removed from host State, the host's visible value no longer contains the bytes that still
     * have to be parsed.  Using currentVisible + delta (or guessing from startsWith) can therefore
     * lose/duplicate the opening marker and leak the remaining JSON.  The dispatcher sees every
     * delta exactly once before the concrete fragment writer, so it is the canonical byte stream.
     */
    private static Main.HeartbeatSanitizedUpdate prepareHeartbeatDeltaUpdate(
            Object fragment, String currentVisible, String decodedDelta,
            boolean append, boolean executeTools) {
        String rebuilt;
        synchronized (HEARTBEAT_RESPONSE_STREAMS) {
            HeartbeatResponseStream stream = heartbeatResponseStreamLocked(fragment, true);
            String delta = decodedDelta == null ? "" : decodedDelta;
            if (append) {
                String visible = currentVisible == null ? "" : currentVisible;
                if (HostCompat.isV241() && stream.initialized
                        && !visible.equals(stream.visible)) {
                    // Fragment ids are only message-local in code257. A replacement wrapper can
                    // therefore collide with an older logical accumulator while its real State
                    // starts from another value. Never write the older visible text into the new
                    // fragment: that was the source of repeated reasoning and repeated controls.
                    resetV241HeartbeatResponseStream(stream);
                    rebuilt = visible + delta;
                } else if (stream.initialized) {
                    rebuilt = stream.raw + delta;
                } else {
                    rebuilt = visible + delta;
                }
            } else {
                rebuilt = delta;
            }
            if (!stream.privateGateOpen
                    && HeartbeatToolProtocol.hasPartialPrivateOpening(rebuilt)) {
                stream.privateGateOpen = true;
                if (!stream.logicalGateLogged) {
                    stream.logicalGateLogged = true;
                    Main.log("heartbeat logical SSE privacy gate opened key="
                            + currentInteractiveAgentResponseKey());
                }
            }
            if (Main.isSrvLog() && !stream.patchMarkerLogged
                    && shouldMonitorHeartbeatFragment(fragment, rebuilt)) {
                stream.patchMarkerLogged = true;
                Main.srvLog("[HB-PATCH] private stream gate opened"
                        + " fragment=" + fragment.getClass().getSimpleName()
                        + " raw=" + rebuilt.length());
            }
        }
        return prepareHeartbeatStateUpdate(
                fragment, rebuilt, false, executeTools);
    }

    private static Main.HeartbeatSanitizedUpdate prepareThinkingStateUpdate(
            Object fragment, String hostText, boolean appendUpdate) {
        String safe;
        synchronized (THINKING_AGENT_STREAMS) {
            ThinkingAgentStream stream = thinkingAgentStreamLocked(fragment, true);
            String host = hostText == null ? "" : hostText;
            if (appendUpdate && stream.initialized
                    && host.startsWith(stream.visible)) {
                stream.raw += host.substring(stream.visible.length());
            } else if (!stream.initialized || !host.equals(stream.visible)) {
                if (HostCompat.isV241() && stream.initialized
                        && !host.startsWith(stream.visible)) {
                    resetV241ThinkingStream(stream);
                }
                stream.raw = host;
            }
            safe = AgentToolConfig.hideToolLogs()
                    ? HeartbeatToolProtocol.sanitizeThinkingToolNarration(stream.raw)
                    : HeartbeatToolProtocol.sanitizeAtRenderBoundary(stream.raw);
            if (safe.length() < stream.raw.length()) stream.privateGateOpen = true;
            // Once the first invocation line is recognized, no later THINK bytes are public.
            // The model's user-facing continuation arrives independently in RESPONSE.
            if (stream.privateGateOpen && stream.initialized
                    && stream.visible.length() < safe.length()) {
                safe = stream.visible;
            }
            stream.visible = safe;
            stream.initialized = true;
        }
        return new Main.HeartbeatSanitizedUpdate(
                safe, new ArrayList<HeartbeatToolProtocol.ToolCall>(),
                new ArrayList<HeartbeatToolProtocol.RejectedCall>());
    }

    private static Main.HeartbeatSanitizedUpdate prepareThinkingDeltaUpdate(
            Object fragment, String currentVisible, String decodedDelta,
            boolean append) {
        String rebuilt;
        synchronized (THINKING_AGENT_STREAMS) {
            ThinkingAgentStream stream = thinkingAgentStreamLocked(fragment, true);
            String delta = decodedDelta == null ? "" : decodedDelta;
            if (append) {
                String visible = currentVisible == null ? "" : currentVisible;
                if (HostCompat.isV241() && stream.initialized
                        && !visible.equals(stream.visible)) {
                    resetV241ThinkingStream(stream);
                    rebuilt = visible + delta;
                } else {
                    rebuilt = stream.initialized
                            ? stream.raw + delta : visible + delta;
                }
            } else {
                rebuilt = delta;
            }
        }
        return prepareThinkingStateUpdate(fragment, rebuilt, false);
    }

    private static void resetV241HeartbeatResponseStream(HeartbeatResponseStream stream) {
        if (stream == null) return;
        stream.raw = "";
        stream.visible = "";
        stream.initialized = false;
        stream.privateGateOpen = false;
        stream.invalidControlLogged = false;
        stream.unauthorizedCallLogged = false;
        stream.executed.clear();
    }

    private static void resetV241ThinkingStream(ThinkingAgentStream stream) {
        if (stream == null) return;
        stream.raw = "";
        stream.visible = "";
        stream.initialized = false;
        stream.privateGateOpen = false;
        stream.renderSanitizeLogged = false;
    }

    /**
     * 2.2.x and 2.3.0 store streamed response text in field c; 2.3.4 inserted an Integer at c and
     * moved the mutable text state to d. Probe the value contract instead of binding execution to
     * one obfuscated field name so both host generations keep using the same parser.
     */
    private static Object liveResponseTextState(Object fragment) {
        Object candidate = Main.readHostField(fragment, "c");
        if (Main.invokeNoArg(candidate, "getValue") instanceof String) return candidate;
        candidate = Main.readHostField(fragment, "d");
        if (Main.invokeNoArg(candidate, "getValue") instanceof String) return candidate;
        return null;
    }

    private static String currentInteractiveAgentResponseKey() {
        String scope = HeartbeatToolProtocol.cleanScope(HookChatPipeline.lastInteractiveConversationId);
        Long generation = HookChatPipeline.INTERACTIVE_AGENT_RESPONSE_GENERATIONS.get(scope);
        return scope.length() == 0 || generation == null
                ? "" : scope + "|" + generation.longValue();
    }

    /** Caller holds THINKING_AGENT_STREAMS while linking object and generation identities. */
    private static ThinkingAgentStream thinkingAgentStreamLocked(
            Object fragment, boolean create) {
        ThinkingAgentStream objectStream = THINKING_AGENT_STREAMS.get(fragment);
        String responseKey = currentInteractiveAgentResponseKey();
        String fragmentId = fragmentStableId(fragment);
        // A generation can contain several THINK/RESPONSE records. Sharing one accumulator for
        // the whole generation joins unrelated SSE streams and can re-expose a private suffix.
        // Only bridge recreated wrapper objects when DeepSeek exposes the stable record id.
        String logicalKey = responseKey.length() == 0 || fragmentId.length() == 0
                ? "" : responseKey + "|think|" + fragmentId;
        ThinkingAgentStream logicalStream = logicalKey.length() == 0
                ? null : HookChatPipeline.THINKING_LOGICAL_STREAMS.get(logicalKey);
        ThinkingAgentStream stream = logicalStream != null
                ? logicalStream : objectStream;
        if (stream == null && create) stream = new ThinkingAgentStream();
        if (stream != null) {
            if (fragment != null) THINKING_AGENT_STREAMS.put(fragment, stream);
            if (logicalKey.length() > 0) {
                HookChatPipeline.THINKING_LOGICAL_STREAMS.put(logicalKey, stream);
            }
        }
        return stream;
    }

    /** Caller holds HEARTBEAT_RESPONSE_STREAMS while linking object and logical identities. */
    private static HeartbeatResponseStream heartbeatResponseStreamLocked(
            Object fragment, boolean create) {
        HeartbeatResponseStream objectStream = HEARTBEAT_RESPONSE_STREAMS.get(fragment);
        String responseKey = currentInteractiveAgentResponseKey();
        String fragmentId = fragmentStableId(fragment);
        String logicalKey = responseKey.length() == 0 || fragmentId.length() == 0
                ? "" : responseKey + "|response|" + fragmentId;
        HeartbeatResponseStream logicalStream = logicalKey.length() == 0
                ? null : HookChatPipeline.HEARTBEAT_LOGICAL_RESPONSE_STREAMS.get(logicalKey);
        HeartbeatResponseStream stream = logicalStream != null
                ? logicalStream : objectStream;
        if (stream == null && create) stream = new HeartbeatResponseStream();
        if (stream != null) {
            if (fragment != null) HEARTBEAT_RESPONSE_STREAMS.put(fragment, stream);
            if (logicalKey.length() > 0) {
                HookChatPipeline.HEARTBEAT_LOGICAL_RESPONSE_STREAMS.put(logicalKey, stream);
            }
        }
        return stream;
    }

    /** Stable streamed-message identity (us2.b on 2.3.4 and its mapped GP counterparts). */
    private static String fragmentStableId(Object fragment) {
        if (fragment == null) return "";
        Object value = Main.readHostField(fragment, "b");
        if (!(value instanceof String) && !(value instanceof Number)) return "";
        String id = String.valueOf(value).trim();
        if (id.length() == 0 || id.length() > 160) return "";
        return id.replace('|', '_');
    }

    private static boolean isAuthorizedInteractiveAgentToolCall(
            HeartbeatToolProtocol.ToolCall call) {
        if (call == null) return false;
        String scope = HeartbeatToolProtocol.cleanScope(call.scope);
        Long expiry = HookChatPipeline.INTERACTIVE_AGENT_TOOL_SCOPES.get(scope);
        if (expiry == null) return false;
        if (expiry.longValue() >= System.currentTimeMillis()) return true;
        HookChatPipeline.INTERACTIVE_AGENT_TOOL_SCOPES.remove(scope, expiry);
        return false;
    }

    private static boolean writeObservableTextState(Object state, String value) {
        Object mutableState = state;
        boolean written = setMutableStateValue(mutableState, value);
        for (int depth = 0; !written && depth < 3; depth++) {
            Object delegate = Main.readHostField(mutableState, "a");
            if (delegate == null || delegate == mutableState) break;
            mutableState = delegate;
            written = setMutableStateValue(mutableState, value);
        }
        return written;
    }

    private static boolean writeHeartbeatObservableTextState(Object state, String value) {
        Boolean previous = tlHeartbeatInternalStateWrite.get();
        tlHeartbeatInternalStateWrite.set(Boolean.TRUE);
        try {
            return writeObservableTextState(state, value);
        } finally {
            if (previous == null) tlHeartbeatInternalStateWrite.remove();
            else tlHeartbeatInternalStateWrite.set(previous);
        }
    }

    private static boolean setHeartbeatSanitizedState(Object state, Object value) {
        Boolean previous = tlHeartbeatInternalStateWrite.get();
        tlHeartbeatInternalStateWrite.set(Boolean.TRUE);
        try {
            return setMutableStateValue(state, value);
        } finally {
            if (previous == null) tlHeartbeatInternalStateWrite.remove();
            else tlHeartbeatInternalStateWrite.set(previous);
        }
    }

    private static boolean setMutableStateValue(Object state, Object value) {
        if (state == null) return false;
        for (Class<?> type = state.getClass(); type != null;
             type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!"l".equals(method.getName())
                        || method.getParameterTypes().length != 1) continue;
                try {
                    method.setAccessible(true);
                    method.invoke(state, value);
                    return true;
                } catch (Throwable ignored) {}
            }
        }
        return false;
    }

    static final class HeartbeatResponseStream {
        String raw = "";
        String visible = "";
        boolean initialized;
        boolean invalidControlLogged;
        boolean unauthorizedCallLogged;
        boolean liveSanitizeLogged;
        boolean renderSanitizeLogged;
        boolean markdownSanitizeLogged;
        boolean stateWriteSanitizeLogged;
        boolean patchMarkerLogged;
        boolean privateGateOpen;
        boolean logicalGateLogged;
        final HashSet<String> executed = new HashSet<>();
    }

    static final class ThinkingAgentStream {
        String raw = "";
        String visible = "";
        boolean initialized;
        boolean privateGateOpen;
        boolean renderSanitizeLogged;
    }

    /** Keeps the anonymous transport request out of Compose while retaining its assistant child. */
    void hookProactiveVisibleThreadFilter(final ClassLoader cl) {
        try {
            Class<?> sessionType = HostCompat.load(cl, "tp");
            String visibleThreadMethod = HostCompat.isV253() ? "z"
                    : HostCompat.isV241() ? "w"
                    : HostCompat.isV234() ? "v" : "s";
            // 2.5.3's visible-thread getter is bs.z() (same body as pq.w()), and rg9 implements
            // a mutable List with working remove(int)/remove(Object), so allow in-place removal.
            final boolean mutableSnapshotList = HostCompat.isV234() || HostCompat.isV253();
            int installed = 0;
            for (Method method : sessionType.getDeclaredMethods()) {
                if (!visibleThreadMethod.equals(method.getName())
                        || method.getParameterTypes().length != 0
                        || !List.class.isAssignableFrom(method.getReturnType())) continue;
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object result = chain.proceed();
                        if (!(result instanceof List)) return result;
                        List source = (List) result;
                        // A cold history render shows the persisted REQUEST fragment verbatim;
                        // strip the injected <system>…</system> wrapper right here so the visible
                        // thread never displays it (in-place mutation covers cached rows too).
                        try {
                            int stripped = 0;
                            for (int index = 0; index < source.size(); index++) {
                                stripped += HistoryBridge.sanitizeVisibleUserMessage(
                                        source.get(index));
                            }
                            if (stripped > 0 && VISIBLE_USER_STRIP_LOGGED.compareAndSet(false, true)) {
                                Main.log("visible-thread prompt strip cleaned=" + stripped);
                            }
                        } catch (Throwable t) {
                            Main.log("visible-thread prompt strip failed: "
                                    + Main.safeThrowableMessage(t));
                        }
                        ArrayList<Object> kept = null;
                        for (int index = 0; index < source.size(); index++) {
                            Object message = source.get(index);
                            if (!Main.isHiddenAgentTransportUserMessage(message)) continue;
                            if (kept == null) kept = new ArrayList<Object>(source);
                            kept.remove(message);
                        }
                        if (kept == null) return result;
                        if (mutableSnapshotList) {
                            int removedCount = source.size() - kept.size();
                            try {
                                for (int index = source.size() - 1; index >= 0; index--) {
                                    if (Main.isHiddenAgentTransportUserMessage(source.get(index))) {
                                        source.remove(index);
                                    }
                                }
                                Main.log("visible-thread filter removed hidden messages="
                                        + removedCount);
                                return result;
                            } catch (Throwable mutationError) {
                                Main.log("native visible-thread mutation failed: "
                                        + Main.safeThrowableMessage(mutationError));
                            }
                        }
                        try {
                            Constructor<?> constructor =
                                    result.getClass().getDeclaredConstructor();
                            constructor.setAccessible(true);
                            Object filtered = constructor.newInstance();
                            if (!(filtered instanceof List)) return result;
                            ((List) filtered).addAll(kept);
                            Main.log("visible-thread filter removed hidden messages="
                                    + (source.size() - kept.size()));
                            return filtered;
                        } catch (Throwable copyError) {
                            Main.log("native proactive visible-thread copy failed: "
                                    + Main.safeThrowableMessage(copyError));
                            return result;
                        }
                    }
                });
                installed++;
            }
            Main.log("installed native proactive visible-thread filter "
                    + sessionType.getName() + "." + visibleThreadMethod + " x" + installed);
        } catch (Throwable error) {
            Main.log("hook proactive visible-thread filter failed: " + error);
        }
    }

    /**
     * DeepSeek's native pipeline performs the actual SSE reduction. Observe its final apply only
     * to post the notification and replace the local database with the folded visible chain.
     */
    void hookNativeUiHeartbeatCompletion(final ClassLoader cl) {
        try {
            Class<?> sessionType = HostCompat.load(cl, "tp");
            Class<?> messageType = HostCompat.load(cl, "uo");
            Class<?> viewModelType = HostCompat.load(cl, "za1");
            // code257's final request outcome is xw0. Resolve it only for that
            // generation; the legacy bu0 mapping and reducer path remain untouched.
            Class<?> outcomeType = HostCompat.isV253()
                    ? cl.loadClass("yf1")
                    : HostCompat.isV241() ? cl.loadClass("xw0")
                    : HostCompat.load(cl, "bu0");
            int installed = 0;
            for (Method method : sessionType.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                boolean singleApply = HostCompat.isV241()
                        ? ((HostCompat.sessionReplaceWithTextMethod().equals(method.getName())
                                && types.length == 2)
                            || (HostCompat.sessionReplaceMethod().equals(method.getName())
                                && types.length == 1))
                        : "p".equals(method.getName()) && types.length == 2;
                boolean finalMerge = HostCompat.isV241()
                        ? HostCompat.sessionMergeMethod().equals(method.getName())
                        : "u".equals(method.getName());
                if (singleApply
                        && messageType.isAssignableFrom(types[0])) {
                    Main.MODULE.hook(method).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            Object result = chain.proceed();
                            maybeCompleteNativeUiHeartbeat(
                                    chain.getThisObject(), chain.getArg(0));
                            return result;
                        }
                    });
                    installed++;
                } else if (finalMerge && types.length == 2
                        && types[0] == sessionType
                        && List.class.isAssignableFrom(types[1])) {
                    Main.MODULE.hook(method).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            Object result = chain.proceed();
                            Object session = chain.getArg(0);
                            Object values = chain.getArg(1);
                            if (values instanceof List) {
                                for (Object message : (List) values) {
                                    maybeCompleteNativeUiHeartbeat(session, message);
                                }
                            }
                            return result;
                        }
                    });
                    installed++;
                }
            }
            for (Method method : viewModelType.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                String outcomeMethod = HostCompat.isV253()
                        ? HostCompat.chatViewModelMethod("N")
                        : HostCompat.isV241() ? "N"
                        : HostCompat.chatViewModelMethod("N");
                if (!outcomeMethod.equals(method.getName())
                        || types.length != 2
                        || types[0] != outcomeType) continue;
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object viewModel = chain.getThisObject();
                        Object session = Main.invokeNoArg(viewModel,
                                HostCompat.chatViewModelMethod("G"));
                        String sid = String.valueOf(Main.readHostField(session, "a"));
                        Main.NativeUiHeartbeatRequest pending =
                                Main.PENDING_NATIVE_UI_HEARTBEATS.get(sid);
                        if (pending != null) {
                            Main.log("native proactive outcome id=" + pending.requestId
                                    + " event=" + MainReflectionSupport.truncateForLog(
                                            MainReflectionSupport.deepDump(chain.getArg(0), 4), 1800));
                        }
                        Object result = chain.proceed();
                        if (pending != null) {
                            Object values = Main.readHostField(session, "f");
                            if (values instanceof Map) {
                                for (Object message : ((Map) values).values()) {
                                    maybeCompleteNativeUiHeartbeat(session, message);
                                }
                            }
                        }
                        return result;
                    }
                });
                installed++;
            }
            Main.log("installed native proactive stream completion hooks x" + installed);
        } catch (Throwable error) {
            Main.log("hook native proactive stream completion failed: " + error);
        }
    }

    private static void maybeCompleteNativeUiHeartbeat(
            Object session, Object assistantMessage) {
        if (session == null || assistantMessage == null) return;
        String sid = String.valueOf(Main.readHostField(session, "a"));
        Main.NativeUiHeartbeatRequest pending = Main.PENDING_NATIVE_UI_HEARTBEATS.get(sid);
        if (pending == null) return;
        if (System.currentTimeMillis() - pending.startedAt > 4L * 60L * 1000L) {
            Main.PENDING_NATIVE_UI_HEARTBEATS.remove(sid, pending);
            return;
        }
        Object roleValue = Main.invokeNoArg(assistantMessage, "A");
        if (roleValue == null) roleValue = MainReflectionSupport.fieldByName(assistantMessage, "h");
        if (!"ASSISTANT".equals(String.valueOf(roleValue))) return;

        Integer parentId = Main.intField(assistantMessage, "g");
        if (parentId == null) {
            Object parentValue = Main.invokeNoArg(assistantMessage, "w");
            if (parentValue instanceof Number) {
                parentId = Integer.valueOf(((Number) parentValue).intValue());
            }
        }
        Object messages = Main.readHostField(session, "f");
        Object parent = messages instanceof Map && parentId != null
                ? ((Map) messages).get(parentId) : null;
        if (!isAnonymousHeartbeatUserMessage(parent)) return;
        if (!pending.completing.compareAndSet(false, true)) return;
        Main.PENDING_NATIVE_UI_HEARTBEATS.remove(sid, pending);

        final Object finalMessage = assistantMessage;
        new Thread(new Runnable() {
            @Override public void run() {
                completeNativeUiHeartbeat(pending, finalMessage);
            }
        }, "DeePX-native-proactive-finish").start();
    }

    private static void completeNativeUiHeartbeat(
            Main.NativeUiHeartbeatRequest pending, Object assistantMessage) {
        String message = HookChatPipeline.visibleAssistantMessageText(assistantMessage);
        boolean persisted = false;
        boolean applied = false;
        Integer head = null;
        HookSessionManagement.NativeHeartbeatHistory refreshed = null;
        try {
            // code257 can publish the assistant shell several seconds before its final SSE text
            // reaches that object or authenticated history. Wait for actual visible content;
            // treating the first empty shell as completion silently loses both chat and notice.
            int attempts = HostCompat.isV241() ? 24 : 1;
            for (int attempt = 0; attempt < attempts; attempt++) {
                Thread.sleep(attempt == 0 ? 250L : 350L);
                if (message.length() == 0) {
                    message = HookChatPipeline.visibleAssistantMessageText(assistantMessage);
                }
                Main module = Main.MODULE;
                if (module != null) {
                    try {
                        HookSessionManagement.NativeHeartbeatHistory candidate =
                                module.refreshNativeHeartbeatHistory(
                                        pending.sid, pending.previousHead);
                        if (candidate != null) refreshed = candidate;
                    } catch (Throwable refreshError) {
                        if (!HostCompat.isV241() || attempt == attempts - 1) {
                            throw refreshError;
                        }
                    }
                }
                if (message.length() == 0) {
                    message = visibleHeadAssistantMessageText(refreshed);
                }
                if (message.length() > 0) break;
            }
            Main module = Main.MODULE;
            if (module != null && refreshed != null) {
                persisted = module.persistNativeHeartbeatHistory(refreshed);
                head = refreshed.head;
                HookSessionManagement.PENDING_NATIVE_HEARTBEAT_HISTORIES.put(refreshed.sid, refreshed);
                applied = module.applyNativeHeartbeatHistory(refreshed);
            }
        } catch (Throwable error) {
            Main.log("native proactive final history refresh failed sid=" + pending.sid
                    + ": " + Main.safeThrowableMessage(error));
        }
        // tp.p may expose the newly-created assistant shell before the final SSE
        // fragments have been copied onto that particular object. The refreshed
        // server history is authoritative and already contains the completed
        // response, so use its head message when the early object was empty.
        if (message.length() == 0) {
            message = visibleHeadAssistantMessageText(refreshed);
        }
        if (ProactiveHeartbeatReceiver.TASK_KIND_HEARTBEAT
                .equals(pending.taskKind) && message.length() > 0) {
            HookAccountLoginSecurity.rememberProactiveMessage(pending.sid, message);
        }
        Context context = pending.context == null
                ? HookSessionManagement.currentHostContext() : pending.context;
        if (HostCompat.isV241() && context != null && message.length() == 0) {
            String task = pending.fallbackText;
            if (pending.taskReminder && task.length() > 0) {
                message = ProactiveHeartbeatReceiver.TASK_KIND_REMINDER
                        .equals(pending.taskKind)
                        ? UiLanguage.text(context, "到时间啦，记得" + task,
                        "It's time — remember to " + task)
                        : UiLanguage.text(context, "来找你啦～" + task,
                        "I'm here — " + task);
            } else {
                message = UiLanguage.text(context,
                        "来找你聊聊天啦～", "I'm here to chat with you.");
            }
            Main.log("native proactive empty response used notification fallback id="
                    + pending.requestId);
        }
        if (context != null && message.length() > 0) {
            HookAccountLoginSecurity.dispatchProactiveHeartbeatResponse(
                    context, pending.requestId, message,
                    Main.isDeepSeekForeground(), pending.taskReminder,
                    pending.taskKind, pending.sid);
        }
        Main.log("native proactive stream completed id=" + pending.requestId
                + " sid=" + pending.sid
                + " chars=" + message.length()
                + " head=" + head
                + " persisted=" + persisted
                + " applied=" + applied);
    }

    private static String visibleHeadAssistantMessageText(
            HookSessionManagement.NativeHeartbeatHistory history) {
        if (history == null || history.messages == null
                || history.messages.isEmpty()) return "";
        if (history.head != null) {
            for (Object candidate : history.messages) {
                if (!history.head.equals(Main.intField(candidate, "f"))) continue;
                String text = HookChatPipeline.visibleAssistantMessageText(candidate);
                if (text.length() > 0) return text;
            }
        }
        for (int index = history.messages.size() - 1; index >= 0; index--) {
            Object candidate = history.messages.get(index);
            Object role = MainReflectionSupport.fieldByName(candidate, "h");
            if (role == null) role = Main.invokeNoArg(candidate, "A");
            if (!"ASSISTANT".equals(String.valueOf(role))) continue;
            String text = HookChatPipeline.visibleAssistantMessageText(candidate);
            if (text.length() > 0) return text;
        }
        return "";
    }

    private static boolean isAnonymousHeartbeatUserMessage(Object message) {
        if (message == null) return false;
        Object role = privateTransportMessageRole(message);
        return "USER".equals(String.valueOf(role))
                && messageContainsAnonymousHeartbeatEvent(message);
    }

    static Object privateTransportMessageRole(Object message) {
        // Static and optimistic 2.3.x message rows keep the serialized role in h. Calling the
        // translated interface method first is unsafe on 2.3.4: some concrete rows expose a
        // non-role method at that slot, yielding a non-null value that prevents the h fallback.
        Object role = MainReflectionSupport.fieldByName(message, "h");
        String name = String.valueOf(role);
        if ("USER".equals(name) || "ASSISTANT".equals(name)) return role;
        // 2.5.3 renamed the role getter C() -> D() (jw.C() / rx.D() / tx.D() all return this.i).
        return Main.invokeNoArg(message, HostCompat.isV253()
                ? "D" : HostCompat.messageMethod("A"));
    }

    private static boolean messageContainsAnonymousHeartbeatEvent(Object message) {
        return HookAttachmentPipeline.messageContainsPrivateTransport(message, false);
    }
}