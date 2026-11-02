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

/** Hook group extracted from Main.java: SESSION category (see JavaHookGuide). */
final class HookSessionManagement {
    static final HookSessionManagement INSTANCE = new HookSessionManagement();

    static final String CHAT_MULTISELECT_FILE = "/data/data/com.deepseek.chat/files/deekseep_chat_multiselect";

    // ── 侧栏聊天记录多选删除（sidebar multi-select delete）─────────────
    static final Map<String, Object> SIDEBAR_DELETE_ACTIONS = new HashMap<>();

    private static final Map<String, Object> SIDEBAR_CLICK_ACTIONS = new HashMap<>();

    private static final HashSet<String> SIDEBAR_SELECTED = new HashSet<>();

    private static volatile View sidebarSelectOverlay;

    private static volatile boolean sidebarSelectMode = false;

    static volatile String sidebarCurrentSid;

    private static volatile long sidebarBoundsLogAt;

    // mq5.i 暴露的主会话抽屉 DrawerState；仅跟踪这一实例，避免其他 Compose 抽屉干扰背景位移。
    private static volatile Object sidebarDrawerState;

    private static volatile int sidebarDrawerWidthPx;

    private static volatile Object sidebarLiveLoggedState;

    // 本次多选会话是否已确认看到行处于屏内（左坐标非负）；用于收起检测的解锁
    private static volatile boolean sidebarConfirmedOpen = false;

    // 会话行真实 Compose 坐标（decor/window 空间：left,top,right,bottom），由 onGloballyPositioned 回调写入
    private static final Map<String, int[]> SIDEBAR_ROW_BOUNDS = new ConcurrentHashMap<>();

    private static final Map<String, Long> SIDEBAR_ROW_BOUNDS_AT = new ConcurrentHashMap<>();

    // Persistent overlay rows are moved with Compose every frame. Recreating every checkbox on a
    // timer caused the old fixed-in-place scroll artifact and occasional ViewGroup crashes.
    private static final Map<String, TextView> SIDEBAR_MARK_VIEWS =
            new ConcurrentHashMap<>();

    private static final Map<String, Rect> SIDEBAR_FALLBACK_BOUNDS = new HashMap<>();

    private static volatile long sidebarFallbackBoundsAt;

    private static volatile WeakReference<TextView> sidebarSelectionTitle =
            new WeakReference<>(null);

    private static volatile WeakReference<TextView> sidebarSelectionDelete =
            new WeakReference<>(null);

    // 每个 sid 复用同一个 ib3 回调，保证 lw5 元素 equals 稳定，避免 Compose 节点抖动
    private static final Map<String, Object> SIDEBAR_BOUNDS_CB = new HashMap<>();

    // bm4(LayoutCoordinates) 方法；code249 为 h()=attached、j()=size、r(Offset)=localToWindow。
    private static volatile Method BM4_I, BM4_K, BM4_W;

    // 2.3.6 split the WCDB writer (yx8) from the session repository (fh). The latter owns the
    // native h(lq, Continuation) loader that materialises message rows into the selected session.
    private static volatile Object liveNativeSessionRepository;

    static final ConcurrentHashMap<String, WeakReference<Object>>
            ACTIVE_CHAT_SESSIONS = new ConcurrentHashMap<>();

    static final ConcurrentHashMap<String, WeakReference<Object>>
            ACTIVE_CHAT_VIEW_MODELS = new ConcurrentHashMap<>();

    static final ConcurrentHashMap<String, NativeHeartbeatHistory>
            PENDING_NATIVE_HEARTBEAT_HISTORIES = new ConcurrentHashMap<>();

    private static volatile long lastComposeStateLog;

    // Captured from mc.f: DeepSeek's complete native session list, click handler, and the
    // central s61 event sink.  Sending h61(tp) through that sink is DeepSeek's real deletion
    // path: server request first, then native list/WCDB cleanup on success.
    static volatile Object NATIVE_SESSION_LIST;

    // Canonical ed0.e SnapshotStateList.  mc.f only renders this state; replacing its argument
    // with a merged copy is not enough because navigation and the active-chat validator continue
    // to observe the original list.
    static volatile Object NATIVE_SESSION_STATE;

    static volatile Object NATIVE_SESSION_CLICK;

    static volatile Object NATIVE_SESSION_EVENTS;

    static final ConcurrentHashMap<String, Long> RECENTLY_DELETED_SESSION_IDS =
            new ConcurrentHashMap<>();

    private static final long DELETED_SESSION_VISIBILITY_GRACE_MS = 120000L;

    static final Map<String, Object> LOCAL_NATIVE_SESSIONS = new HashMap<>();

    static volatile HashSet<String> LOCAL_SESSION_IDS = new HashSet<>();

    static volatile long LOCAL_SESSION_IDS_AT;

    static volatile String LOCAL_SESSION_IDS_DB_PATH;

    private static volatile long LOCAL_NATIVE_MERGE_LOG_AT;

    private static volatile long LOCAL_NATIVE_STATE_REPAIR_LOG_AT;

    private static volatile long LOCAL_DIRECTORY_MERGE_LOG_AT;

    private static volatile long LOCAL_DIRECTORY_HEAD_LOG_AT;

    private static final ThreadLocal<Boolean> LOCAL_DIRECTORY_SYNC = new ThreadLocal<>();

    private static final HashSet<Class<?>> NATIVE_CLICK_HOOKED_CLASSES = new HashSet<>();

    private static volatile String PENDING_LOCAL_OPEN_SID;

    // ── 侧栏聊天记录多选删除（modern Compose Hooker 版）────────────────

    static boolean isChatMultiSelect() {
        return new File(CHAT_MULTISELECT_FILE).exists();
    }

    // 会话行渲染器 mc.e(tp,..,xa3 click,..,xa3 delete,..,qg5 modifier,..) 12 参。
    // modern：拦到后按需改 args[4]=长按代理、args[9]=追加坐标捕获的 Modifier，再一次性 proceed(args)。
    void hookSidebarMultiSelectDelete(final ClassLoader cl) {
        try {
            // code257 split the old sc container: the row renderer moved to ch2.f,
            // while the session directory moved independently to gi0.c. Resolve
            // the row owner locally so the working code249 sc.k path stays intact.
            final Class<?> mc = HostCompat.isV241()
                    ? cl.loadClass("ch2") : HostCompat.load(cl, "mc");
            final Class<?> tp = HostCompat.load(cl, "tp");
            final Class<?> xa3 = HostCompat.load(cl, "xa3");
            final int activeIndex = HostCompat.isV241() ? 3 : 2;
            final int clickIndex = HostCompat.isV241() ? 4 : 3;
            final int longPressIndex = HostCompat.isV241() ? 5 : 4;
            // code257's ch2.f moved the actual delete Function0 from legacy slot 7 to slot 9.
            // Slot 7 is no longer the destructive action; retaining it made the overlay report a
            // submitted batch while no host conversation was deleted.
            final int deleteIndex = HostCompat.isV241() ? 9 : 7;
            final int modifierIndex = HostCompat.isV241() ? 10 : 9;
            final int expectedLength = HostCompat.isV241() ? 13 : 12;
            final String rowMethod = HostCompat.isV241()
                    ? "f" : HostCompat.method("mc", "e");
            int n = 0;
            for (Method m : mc.getDeclaredMethods()) {
                Class<?>[] pts = m.getParameterTypes();
                if (!m.getName().equals(rowMethod)
                        || pts.length != expectedLength || pts[0] != tp) continue;
                if (!xa3.isAssignableFrom(pts[longPressIndex])
                        || !xa3.isAssignableFrom(pts[deleteIndex])) continue;
                Main.MODULE.hook(m).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object[] args = null;
                        try {
                            Object[] a = chain.getArgs().toArray();
                            final Object tpObj = a[0];
                            final String sid = String.valueOf(MainReflectionSupport.fieldByName(tpObj, "a"));
                            if (sid != null && sid.length() > 0 && !"null".equals(sid)) {
                                boolean active = Boolean.TRUE.equals(a[activeIndex]);
                                if (active) {
                                    String oldSid = sidebarCurrentSid;
                                    sidebarCurrentSid = sid;
                                    if (sidebarSelectMode && oldSid != null && !oldSid.equals(sid)) {
                                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                                            public void run() { slideOutSidebarOverlayAndExit(); }
                                        });
                                    }
                                }
                                synchronized (SIDEBAR_DELETE_ACTIONS) {
                                    if (a[clickIndex] != null) {
                                        SIDEBAR_CLICK_ACTIONS.put(sid, a[clickIndex]);
                                    }
                                    if (a[deleteIndex] != null) {
                                        SIDEBAR_DELETE_ACTIONS.put(sid, a[deleteIndex]);
                                    }
                                }
                                boolean multiSelect = isChatMultiSelect();
                                boolean changed = false;
                                if (multiSelect && a[clickIndex] != null) {
                                    a[clickIndex] = buildSidebarRowClickProxy(
                                            cl, sid, a[clickIndex]);
                                    changed = true;
                                }
                                if (multiSelect) {
                                    a[longPressIndex] = buildSidebarLongPressProxy(cl, sid);
                                    changed = true;
                                }
                                // Capture native row geometry continuously. If the feature is
                                // enabled after the LazyColumn has already composed its rows,
                                // waiting until selection mode means no placement callback exists
                                // and the old fixed-spacing fallback puts checks on unrelated rows.
                                if (a.length > modifierIndex && a[modifierIndex] != null) {
                                    Object wrapped = wrapModifierWithBoundsCapture(
                                            cl, sid, a[modifierIndex]);
                                    if (wrapped != null) {
                                        a[modifierIndex] = wrapped;
                                        changed = true;
                                    }
                                }
                                if (changed) {
                                    args = a;
                                }
                            }
                        } catch (Throwable t) { Main.log("sidebar multi-select hook row err: " + t); }
                        return args != null ? chain.proceed(args) : chain.proceed();
                    }
                });
                n++;
            }
            Main.log("installed sidebar multi-select delete hook mc.e x" + n);
            if (HostCompat.isV241()) hookV241SidebarNativeMenuSuppression(cl);
        } catch (Throwable t) { Main.log("hookSidebarMultiSelectDelete failed: " + t); }
    }

    /**
     * code257 wraps the supplied long-press callback in ma(case 23), opens its native menu state,
     * and only then invokes the callback. Replacing ch2.f's callback alone therefore displayed
     * both menus. Suppress only that exact wrapper when its callback is our multi-select proxy.
     */
    private void hookV241SidebarNativeMenuSuppression(final ClassLoader cl) {
        try {
            Class<?> wrapper = cl.loadClass("ma");
            int installed = 0;
            for (Method method : wrapper.getDeclaredMethods()) {
                if (!"u".equals(method.getName())
                        || method.getParameterTypes().length != 0) continue;
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        if (isChatMultiSelect()) {
                            Object holder = chain.getThisObject();
                            Object kind = Main.readHostField(holder, "a");
                            Object callback = Main.readHostField(holder, "c");
                            if (Integer.valueOf(23).equals(kind)
                                    && "DeePXSidebarMultiSelect".equals(
                                    String.valueOf(callback))) {
                                invokeXa3(callback);
                                return ui8Unit(cl);
                            }
                        }
                        return chain.proceed();
                    }
                });
                installed++;
            }
            Main.log("installed code257 native long-press menu suppression ma.u x" + installed);
        } catch (Throwable error) {
            Main.log("code257 native long-press menu suppression failed: " + error);
        }
    }

    private Object buildSidebarLongPressProxy(final ClassLoader cl, final String sid) throws Exception {
        final Class<?> xa3 = HostCompat.load(cl, "xa3");
        return Proxy.newProxyInstance(cl, new Class[]{xa3}, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                String name = method.getName();
                if ("toString".equals(name)) return "DeePXSidebarMultiSelect";
                if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                if ("equals".equals(name)) return proxy == (args == null ? null : args[0]);
                if ("u".equals(name) && method.getParameterTypes().length == 0) {
                    final Activity act = Main.MODULE.curAct.get();
                    if (act != null) {
                        act.runOnUiThread(new Runnable() {
                            public void run() { enterSidebarSelectMode(act, sid); }
                        });
                    }
                    return ui8Unit(cl);
                }
                return ui8Unit(cl);
            }
        });
    }

    /** Observes a native row tap while selection mode is active, without covering its scroll UI. */
    private Object buildSidebarRowClickProxy(final ClassLoader cl, final String sid,
                                              final Object original) throws Exception {
        final Class<?> xa3 = HostCompat.load(cl, "xa3");
        return Proxy.newProxyInstance(cl, new Class[]{xa3}, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                String name = method.getName();
                if ("toString".equals(name)) return "DeePXSidebarRowTap";
                if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                if ("equals".equals(name)) return proxy == (args == null ? null : args[0]);
                if ("u".equals(name) && method.getParameterTypes().length == 0
                        && sidebarSelectMode) {
                    toggleSidebarSelection(sid);
                    Main.log("sidebar row selection toggled sid=" + sid
                            + " selected=" + SIDEBAR_SELECTED.contains(sid)
                            + " count=" + SIDEBAR_SELECTED.size());
                    refreshSidebarSelectionUi();
                    return ui8Unit(cl);
                }
                return invokeXa3Returning(original, cl);
            }
        });
    }

    // 把 onGloballyPositioned(callback) 追加到会话行的 Modifier(qg5) 上：modifier.then(new lw5(cb))
    private Object wrapModifierWithBoundsCapture(ClassLoader cl, String sid, Object modifier) {
        try {
            if (HostCompat.isV241()) {
                Class<?> modifierType = cl.loadClass("mv5");
                Class<?> callbackType = cl.loadClass("tl3");
                if (!modifierType.isInstance(modifier)) return null;
                Object callback;
                synchronized (SIDEBAR_BOUNDS_CB) {
                    callback = SIDEBAR_BOUNDS_CB.get(sid);
                    if (callback == null) {
                        callback = buildBoundsCallback(cl, sid);
                        SIDEBAR_BOUNDS_CB.put(sid, callback);
                    }
                }
                // code257 bu9.D0 is the exact structural successor of code249
                // z66.Y: its modifier node forwards LayoutCoordinates to Function1.
                Class<?> layoutKt = cl.loadClass("bu9");
                Method positioned = layoutKt.getDeclaredMethod(
                        "D0", modifierType, callbackType);
                positioned.setAccessible(true);
                return positioned.invoke(null, modifier, callback);
            }
            if (HostCompat.isV236()) {
                Class<?> modifierType = cl.loadClass("sp5");
                Class<?> callbackType = cl.loadClass("ch3");
                if (!modifierType.isInstance(modifier)) return null;
                Object callback;
                synchronized (SIDEBAR_BOUNDS_CB) {
                    callback = SIDEBAR_BOUNDS_CB.get(sid);
                    if (callback == null) {
                        callback = buildBoundsCallback(cl, sid);
                        SIDEBAR_BOUNDS_CB.put(sid, callback);
                    }
                }
                // code249: z66.Y is Modifier.onGloballyPositioned. od2.B looks
                // deceptively similar (sp5, ch3) but is graphicsLayer and its
                // callback receives a GraphicsLayerScope, never LayoutCoordinates.
                Class<?> layoutKt = cl.loadClass("z66");
                Method positioned = layoutKt.getDeclaredMethod(
                        "Y", modifierType, callbackType);
                positioned.setAccessible(true);
                return positioned.invoke(null, modifier, callback);
            }
            Class<?> qg5 = HostCompat.load(cl, "qg5");
            if (!qg5.isInstance(modifier)) return null;
            Class<?> ib3 = HostCompat.load(cl, "ib3");
            Class<?> lw5 = HostCompat.load(cl, "lw5");
            Object cb;
            synchronized (SIDEBAR_BOUNDS_CB) {
                cb = SIDEBAR_BOUNDS_CB.get(sid);
                if (cb == null) { cb = buildBoundsCallback(cl, sid); SIDEBAR_BOUNDS_CB.put(sid, cb); }
            }
            java.lang.reflect.Constructor<?> ctor = lw5.getDeclaredConstructor(ib3);
            ctor.setAccessible(true);
            Object element = ctor.newInstance(cb);
            Method w = qg5.getMethod(HostCompat.method("qg5", "w"), qg5);
            return w.invoke(modifier, element);
        } catch (Throwable t) { Main.log("wrap sidebar bounds capture failed: " + t); return null; }
    }

    // ib3(Function1) 代理：Compose 布局后回调 g(bm4 coords)，把行的窗口坐标写入 SIDEBAR_ROW_BOUNDS
    private Object buildBoundsCallback(final ClassLoader cl, final String sid) throws Exception {
        final Class<?> ib3 = HostCompat.isV241()
                ? cl.loadClass("tl3") : HostCompat.load(cl, "ib3");
        final Class<?> bm4 = HostCompat.load(cl, "bm4");
        if (BM4_I == null) {
            BM4_I = bm4.getMethod(HostCompat.isV241() ? "g"
                    : HostCompat.isV236() ? "h" : HostCompat.method("bm4", "i"));
            BM4_K = bm4.getMethod(HostCompat.isV241() ? "i"
                    : HostCompat.isV236() ? "j" : HostCompat.method("bm4", "k"));
            BM4_W = bm4.getMethod(HostCompat.isV241() ? "q"
                    : HostCompat.isV236() ? "r" : HostCompat.method("bm4", "w"),
                    long.class);
        }
        return Proxy.newProxyInstance(cl, new Class[]{ib3}, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                String name = method.getName();
                if ("toString".equals(name)) return "DeePXSidebarBounds";
                if ("hashCode".equals(name)) return sid.hashCode();
                if ("equals".equals(name)) return proxy == (args == null ? null : args[0]);
                if ("g".equals(name) && args != null && args.length == 1 && args[0] != null) {
                    try {
                        Object coords = args[0];
                        if (Boolean.TRUE.equals(BM4_I.invoke(coords))) {
                            long size = (Long) BM4_K.invoke(coords);
                            int wpx = (int) (size >> 32);
                            int hpx = (int) (size & 0xFFFFFFFFL);
                            // code249 ts4.r(Offset.Zero) is localToWindow and is updated by the
                            // LazyColumn placement pass while scrolling. Older hosts expose the
                            // inverse windowToLocal shape and keep the historical negation.
                            long pos = (Long) BM4_W.invoke(coords, 0L);
                            int x = (int) Float.intBitsToFloat((int) (pos >> 32));
                            int y = (int) Float.intBitsToFloat((int) (pos & 0xFFFFFFFFL));
                            if (!HostCompat.isV236() && !HostCompat.isV241()) {
                                x = -x;
                                y = -y;
                            }
                            if (wpx > 0 && hpx > 0) SIDEBAR_ROW_BOUNDS.put(
                                    sid, new int[]{x, y, x + wpx, y + hpx});
                            if (wpx > 0 && hpx > 0) SIDEBAR_ROW_BOUNDS_AT.put(
                                    sid, Long.valueOf(SystemClock.uptimeMillis()));
                        }
                    } catch (Throwable ignored) {}
                }
                return ui8Unit(cl);
            }
        });
    }

    // 从捕获到的真实坐标构造 sid→Rect（仅当前会话列表里的）
    private static Map<String, Rect> captureBoundsFor(List<ChatEditorUi.Session> sessions) {
        Map<String, Rect> out = new HashMap<>();
        for (int i = 0; i < sessions.size(); i++) {
            String id = sessions.get(i).id;
            int[] b = SIDEBAR_ROW_BOUNDS.get(id);
            if (b != null && b[3] > b[1]) out.put(id, new Rect(b[0], b[1], b[2], b[3]));
        }
        return out;
    }

    // 侧栏收起时 mq5.i 的 toggle 回调(xa3)：包一层，收起动作触发时把多选覆盖层滑出并退出。
    void hookSidebarToggleCleanup(final ClassLoader cl) {
        try {
            // code257's sidebar toggle composable is qp0.s(fv2,a81,il3,mv5,r12,int).
            // Keep the earlier mq5 adapter untouched for all legacy hosts.
            Class<?> mq5 = HostCompat.isV241()
                    ? cl.loadClass("qp0") : HostCompat.load(cl, "mq5");
            final Class<?> xa3 = HostCompat.load(cl, "xa3");
            final String toggleMethod = HostCompat.isV241()
                    ? "s" : HostCompat.method("mq5", "i");
            int n = 0;
            for (Method m : mq5.getDeclaredMethods()) {
                Class<?>[] pts = m.getParameterTypes();
                if (!m.getName().equals(toggleMethod)
                        || pts.length != 6 || !xa3.isAssignableFrom(pts[2])) continue;
                Main.MODULE.hook(m).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object[] args = null;
                        try {
                            Object[] a = chain.getArgs().toArray();
                            Object drawerHost = a[0];
                            Object state = Main.readHostField(drawerHost, "a");
                            if (HostCompat.simpleNameIs(state, "bn2")) {
                                if (sidebarDrawerState != state) {
                                    sidebarDrawerWidthPx = 0;
                                    sidebarLiveLoggedState = null;
                                }
                                sidebarDrawerState = state;
                                int width = resolveSidebarDrawerWidth(state, cl);
                                if (width > 0 && width != sidebarDrawerWidthPx) {
                                    boolean firstResolvedWidth = sidebarDrawerWidthPx <= 0;
                                    sidebarDrawerWidthPx = width;
                                    if (firstResolvedWidth) {
                                        Main.log("sidebar drawer anchors resolved, width=" + width);
                                    }
                                }
                            }
                            if (a[2] != null) { a[2] = buildSidebarToggleProxy(cl, a[2]); args = a; }
                        } catch (Throwable t) { Main.log("sidebar toggle cleanup row err: " + t); }
                        return args != null ? chain.proceed(args) : chain.proceed();
                    }
                });
                n++;
            }
            Main.log("installed sidebar toggle cleanup hook mq5.i x" + n);
        } catch (Throwable t) { Main.log("hookSidebarToggleCleanup failed: " + t); }

        // DrawerState.c() is the exact animated pixel offset: closed≈-width, open=0. It is read
        // on every native drawer frame and therefore also covers closing, swipe gestures, and
        // interrupted/reversed animations.
        try {
            Class<?> bn2 = HostCompat.load(cl, "bn2");
            int n = 0;
            for (Method m : bn2.getDeclaredMethods()) {
                if (!m.getName().equals("c") || m.getParameterTypes().length != 0
                        || m.getReturnType() != float.class) {
                    continue;
                }
                Main.MODULE.hook(m).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object result = chain.proceed();
                        try {
                            if (result instanceof Number) {
                                Object state = chain.getThisObject();
                                float offset = ((Number) result).floatValue();
                                // mq5 normally supplies the exact conversation DrawerState. If
                                // that capture happens late, a valid pair of Closed/Open anchors
                                // lets the live getter safely identify the same state itself.
                                if (state != sidebarDrawerState) {
                                    int candidateWidth =
                                            resolveSidebarDrawerWidth(state, cl);
                                    if (candidateWidth > 0
                                            && offset >= -candidateWidth * 1.05f
                                            && offset <= candidateWidth * 0.05f) {
                                        sidebarDrawerState = state;
                                        sidebarDrawerWidthPx = candidateWidth;
                                        sidebarLiveLoggedState = null;
                                        Main.log("sidebar drawer live candidate resolved, width="
                                                + candidateWidth);
                                    }
                                }
                                if (state != sidebarDrawerState) return result;
                                int width = sidebarDrawerWidthPx;
                                if (width <= 0) {
                                    width = resolveSidebarDrawerWidth(
                                            state, cl);
                                    if (width > 0) sidebarDrawerWidthPx = width;
                                }
                                if (sidebarLiveLoggedState != state) {
                                    sidebarLiveLoggedState = state;
                                    Main.log("sidebar live curve active, width=" + width);
                                }
                                ChatAppearance.onSidebarOffset(offset, width);
                            }
                        } catch (Throwable ignored) {}
                        return result;
                    }
                });
                n++;
            }
            Main.log("installed sidebar live-offset hook bn2.c x" + n);
        } catch (Throwable t) {
            Main.log("hook sidebar live offset failed: " + t);
        }

        // mq5.i creates n51(case 0) as the icon's real click action. Keep this only as a diagnostic
        // destination signal. The supported host's later DrawerState.c() frames exclusively drive
        // the follower target, preventing an eager endpoint from erasing the visible lag.
        try {
            // qp0.s creates y91(case 0) as the code257 icon click action. The
            // legacy synthetic action keeps its existing lookup below.
            Class<?> n51 = HostCompat.isV241()
                    ? cl.loadClass("y91") : HostCompat.load(cl, "n51");
            int n = 0;
            for (Method m : n51.getDeclaredMethods()) {
                if (!m.getName().equals("u") || m.getParameterTypes().length != 0) continue;
                Main.MODULE.hook(m).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        try {
                            Object action = chain.getThisObject();
                            Object kind = Main.readHostField(action, "a");
                            Object drawerState = Main.readHostField(action, "c");
                            Object drawerHost = Main.readHostField(action, "f");
                            if (Integer.valueOf(0).equals(kind)
                                    && drawerState != null && drawerHost != null
                                    && HostCompat.simpleNameIs(drawerState, "bn2")
                                    && HostCompat.simpleNameIs(drawerHost, "zm2")) {
                                if (sidebarDrawerState != drawerState) {
                                    sidebarDrawerState = drawerState;
                                    sidebarDrawerWidthPx = 0;
                                    sidebarLiveLoggedState = null;
                                }
                                int resolvedWidth =
                                        resolveSidebarDrawerWidth(drawerState, cl);
                                if (resolvedWidth > 0) {
                                    sidebarDrawerWidthPx = resolvedWidth;
                                }
                            }
                        } catch (Throwable t) {
                            Main.log("sidebar appearance toggle signal failed: " + t);
                        }
                        return chain.proceed();
                    }
                });
                n++;
            }
            Main.log("installed sidebar appearance click hook n51.u x" + n);
        } catch (Throwable t) {
            Main.log("hook sidebar appearance click failed: " + t);
        }
    }

    private static int resolveSidebarDrawerWidth(Object drawerState, ClassLoader cl) {
        if (drawerState == null || cl == null) return 0;
        try {
            Object anchored = Main.readHostField(drawerState, "c");
            if (anchored == null) return 0;
            Method anchorsMethod = anchored.getClass().getDeclaredMethod("b");
            anchorsMethod.setAccessible(true);
            Object anchors = anchorsMethod.invoke(anchored);
            if (anchors == null) return 0;
            Class<?> cn2 = HostCompat.load(cl, "cn2");
            Field closedField = cn2.getDeclaredField("a");
            Field openField = cn2.getDeclaredField("b");
            closedField.setAccessible(true);
            openField.setAccessible(true);
            Object closed = closedField.get(null);
            Object open = openField.get(null);
            Method anchorMethod =
                    anchors.getClass().getDeclaredMethod("d", Object.class);
            anchorMethod.setAccessible(true);
            float closedOffset =
                    ((Number) anchorMethod.invoke(anchors, closed)).floatValue();
            float openOffset =
                    ((Number) anchorMethod.invoke(anchors, open)).floatValue();
            if (Float.isNaN(closedOffset) || Float.isNaN(openOffset)) return 0;
            return Math.max(0, Math.round(Math.abs(openOffset - closedOffset)));
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private Object buildSidebarToggleProxy(final ClassLoader cl, final Object original) throws Exception {
        final Class<?> xa3 = HostCompat.load(cl, "xa3");
        return Proxy.newProxyInstance(cl, new Class[]{xa3}, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                String name = method.getName();
                if ("toString".equals(name)) return "DeePXSidebarToggleCleanup";
                if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                if ("equals".equals(name)) return proxy == (args == null ? null : args[0]);
                if ("u".equals(name) && method.getParameterTypes().length == 0) {
                    if (sidebarSelectMode) slideOutSidebarOverlayAndExit();
                    return invokeXa3Returning(original, cl);
                }
                return invokeXa3Returning(original, cl);
            }
        });
    }

    // 2.2.2：Kotlin Unit 是 ui8（静态字段 a）；legacy 的 ti8 在本 build 不是 Unit。
    public static Object ui8Unit(ClassLoader cl) {
        try {
            Field f = HostCompat.load(cl, "ui8").getDeclaredField("a");
            f.setAccessible(true);
            return f.get(null);
        } catch (Throwable ignored) { return null; }
    }

    private static void enterSidebarSelectMode(final Activity act, String startSid) {
        SIDEBAR_SELECTED.clear();
        if (startSid != null && startSid.length() > 0) SIDEBAR_SELECTED.add(startSid);
        sidebarSelectMode = true;
        sidebarConfirmedOpen = false;
        showSidebarSelectOverlay(act);
    }

    private static void showSidebarSelectOverlay(final Activity act) {
        final List<ChatEditorUi.Session> sessions = loadCurrentSidebarSessions(act);
        if (sessions.isEmpty()) {
            UiLanguage.toast(act, "没有可删除的本地对话", Toast.LENGTH_SHORT).show();
            return;
        }

        removeSidebarSelectOverlay();

        final boolean dark = DeePXUi.isDark(act);
        final int cardBg = dark ? 0xFF2A2A2D : 0xFFFFFFFF;
        final int text = dark ? 0xFFECECEC : 0xFF1A1A1A;
        final int div = dark ? 0xFF3A3A3D : 0xFFEAEAEA;
        final int brand = DeePXUi.BRAND;
        final int danger = 0xFFE53935;
        final int checkColor = dark ? 0xFFECECEC : 0xFF1A1A1A;
        final int screenW = act.getResources().getDisplayMetrics().widthPixels;
        final float screenDp = screenW / act.getResources().getDisplayMetrics().density;
        // 手机端(<600dp)侧栏并非铺满屏宽：右侧约 1/5 仍露出聊天区，故取约 4/5 屏宽；平板/大屏限 320dp。
        final int sidebarW = screenDp < 600.0f
                ? Math.round(screenW * 0.8f)
                : Math.min(DeePXUi.dp(act, 320), screenW);

        final FrameLayout root = new FrameLayout(act);
        root.setClickable(false);
        root.setFocusable(false);
        sidebarSelectOverlay = root;

        final FrameLayout marks = new FrameLayout(act);
        marks.setClickable(false);
        marks.setFocusable(false);
        root.addView(marks, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        SIDEBAR_MARK_VIEWS.clear();
        SIDEBAR_FALLBACK_BOUNDS.clear();
        sidebarFallbackBoundsAt = 0L;

        LinearLayout top = new LinearLayout(act);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(DeePXUi.dp(act, 12), 0, DeePXUi.dp(act, 10), 0);
        top.setClickable(true);
        GradientDrawable topBg = new GradientDrawable();
        topBg.setColor(cardBg);
        topBg.setCornerRadius(DeePXUi.dp(act, 16));
        topBg.setStroke(1, div);
        top.setBackground(topBg);
        if (android.os.Build.VERSION.SDK_INT >= 21) top.setElevation(DeePXUi.dp(act, 8));
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(sidebarW - DeePXUi.dp(act, 20), DeePXUi.dp(act, 46));
        topLp.leftMargin = DeePXUi.dp(act, 10);
        topLp.topMargin = DeePXUi.statusBarHeight(act) + DeePXUi.dp(act, 8);
        root.addView(top, topLp);

        TextView cancel = new TextView(act);
        cancel.setText("取消");
        cancel.setTextColor(brand);
        cancel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(DeePXUi.dp(act, 4), 0, DeePXUi.dp(act, 10), 0);
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { exitSidebarSelectMode(); }
        });
        top.addView(cancel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final TextView title = new TextView(act);
        title.setTextColor(text);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setSingleLine(true);
        top.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView delete = new TextView(act);
        sidebarSelectionTitle = new WeakReference<>(title);
        sidebarSelectionDelete = new WeakReference<>(delete);
        final TextView selectAll = new TextView(act);
        selectAll.setText(UiLanguage.text(act, "全选", "Select all"));
        selectAll.setTextColor(brand);
        selectAll.setTypeface(Typeface.DEFAULT_BOLD);
        selectAll.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        selectAll.setGravity(Gravity.CENTER);
        selectAll.setPadding(DeePXUi.dp(act, 8), 0, DeePXUi.dp(act, 8), 0);
        selectAll.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean allSelected = !sessions.isEmpty();
                for (int i = 0; i < sessions.size(); i++) {
                    String sid = sessions.get(i).id;
                    if (sid != null && !SIDEBAR_SELECTED.contains(sid)) {
                        allSelected = false;
                        break;
                    }
                }
                SIDEBAR_SELECTED.clear();
                if (!allSelected) {
                    for (int i = 0; i < sessions.size(); i++) {
                        String sid = sessions.get(i).id;
                        if (sid != null && sid.length() > 0) SIDEBAR_SELECTED.add(sid);
                    }
                }
                updateSidebarSelectTitle(title, delete);
                refreshSidebarSelectionUi();
            }
        });
        top.addView(selectAll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        delete.setTextColor(danger);
        delete.setTypeface(Typeface.DEFAULT_BOLD);
        delete.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        delete.setGravity(Gravity.CENTER);
        delete.setPadding(DeePXUi.dp(act, 10), 0, DeePXUi.dp(act, 4), 0);
        top.addView(delete, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        delete.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final int n = SIDEBAR_SELECTED.size();
                if (n <= 0) {
                    UiLanguage.toast(act, "先勾选要删除的对话", Toast.LENGTH_SHORT).show();
                    return;
                }
                confirmSidebarBatchDelete(act, sessions, n);
            }
        });
        updateSidebarSelectTitle(title, delete);

        UiLanguage.localizeTree(act, root);
        ViewGroup decor = (ViewGroup) act.getWindow().getDecorView();
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                sidebarW,
                ViewGroup.LayoutParams.MATCH_PARENT);
        lp.gravity = Gravity.START | Gravity.TOP;
        decor.addView(root, lp);

        final Runnable[] refresh = new Runnable[1];
        refresh[0] = new Runnable() {
            public void run() {
                if (!sidebarSelectMode || sidebarSelectOverlay != root || root.getParent() == null) return;
                refreshSidebarMarkLayer(act, marks, sessions, title, delete, sidebarW, checkColor);
                root.postOnAnimation(this);
            }
        };
        root.post(refresh[0]);
    }

    private static void updateSidebarSelectTitle(TextView title, TextView delete) {
        int n = SIDEBAR_SELECTED.size();
        Context context = title.getContext();
        title.setText(n > 0
                ? UiLanguage.text(context, "已选择 " + n, n + " selected")
                : UiLanguage.text(context, "选择对话", "Select chats"));
        delete.setText(n > 0
                ? UiLanguage.text(context, "删除(" + n + ")", "Delete (" + n + ")")
                : UiLanguage.text(context, "删除", "Delete"));
    }

    private static void refreshSidebarSelectionUi() {
        TextView title = sidebarSelectionTitle.get();
        TextView delete = sidebarSelectionDelete.get();
        if (title != null && delete != null) updateSidebarSelectTitle(title, delete);
        for (Map.Entry<String, TextView> entry : SIDEBAR_MARK_VIEWS.entrySet()) {
            TextView mark = entry.getValue();
            if (!SIDEBAR_SELECTED.contains(entry.getKey())) {
                removeSidebarMark(entry.getKey(), mark);
            } else if (mark != null) {
                updateSidebarMarkState(mark, entry.getKey(),
                        DeePXUi.isDark(mark.getContext()) ? 0xFFECECEC : 0xFF1A1A1A);
            }
        }
    }

    private static void refreshSidebarMarkLayer(final Activity act, final FrameLayout marks,
                                                final List<ChatEditorUi.Session> sessions,
                                                final TextView title, final TextView delete,
                                                final int sidebarW, final int checkColor) {
        if (marks == null || marks.getParent() == null) return;
        Map<String, Rect> bounds = captureBoundsFor(sessions);
        if (sidebarRowsOnScreen(bounds, sidebarW)) sidebarConfirmedOpen = true;
        else if (sidebarConfirmedOpen && isSidebarCollapsed(bounds, sidebarW)) {
            logSidebarBoundsState("sidebar collapsed detected (rows off-screen) -> slide out overlay");
            slideOutSidebarOverlayAndExit();
            return;
        }
        // Placement callbacks are authoritative while scrolling, but LazyColumn only keeps a
        // window of rows composed. A non-empty partial map must not suppress the accessibility
        // fallback for every other visible row; that was why taps updated the count while no
        // checkbox appeared beside the tapped conversation.
        boolean selectedBoundMissing = false;
        for (String selectedSid : SIDEBAR_SELECTED) {
            if (!bounds.containsKey(selectedSid)) {
                selectedBoundMissing = true;
                break;
            }
        }
        // Accessibility traversal is a fallback only for a selected row whose Compose placement
        // callback has not arrived. Never scan the complete semantics tree on a timer during an
        // ordinary multi-select session: that creates visible main-thread stalls on long lists.
        long now = SystemClock.uptimeMillis();
        if (selectedBoundMissing
                && (SIDEBAR_FALLBACK_BOUNDS.isEmpty()
                || now - sidebarFallbackBoundsAt >= 500L)) {
            SIDEBAR_FALLBACK_BOUNDS.clear();
            SIDEBAR_FALLBACK_BOUNDS.putAll(
                    resolveSidebarSessionBounds(act, sessions, sidebarW));
            sidebarFallbackBoundsAt = now;
        }
        if (selectedBoundMissing) {
            for (Map.Entry<String, Rect> entry : SIDEBAR_FALLBACK_BOUNDS.entrySet()) {
                if (!bounds.containsKey(entry.getKey())) bounds.put(entry.getKey(), entry.getValue());
            }
        }
        if (bounds.isEmpty()) {
            // Never synthesize rows at a fixed 44dp cadence. LazyColumn headers, pinned groups and
            // font scaling make that mapping false; a selected SID would then appear beside a
            // different conversation. The top selected-count remains available until the next
            // real placement callback arrives.
            logSidebarBoundsState("sidebar marks waiting for native row coordinates");
            return;
        }
        StringBuilder dbg = new StringBuilder("sidebar marks: matched=" + bounds.size() + " raw=");
        for (int i = 0; i < sessions.size() && i < 4; i++) {
            Rect rr = bounds.get(sessions.get(i).id);
            if (rr != null) dbg.append("[").append(rr.left).append(",").append(rr.top)
                    .append(",").append(rr.width()).append("x").append(rr.height()).append("]");
        }
        logSidebarBoundsState(dbg.toString());
        HashSet<String> shown = new HashSet<>();
        for (int i = 0; i < sessions.size(); i++) {
            ChatEditorUi.Session s = sessions.get(i);
            if (s == null || s.id == null || !SIDEBAR_SELECTED.contains(s.id)) continue;
            Rect r = bounds.get(s.id);
            if (r == null) continue;
            if (r.bottom <= 0 || r.top >= act.getResources()
                    .getDisplayMetrics().heightPixels) continue;
            if (isSidebarBoundSuperseded(s.id, r, bounds)) continue;
            int markH = Math.max(DeePXUi.dp(act, 18), r.height());
            int top = Math.max(0, r.top + (r.height() - markH) / 2);
            // 手机端适配：勾选热区对齐到该行真实右边缘 r.right，而非全局 sidebarW。
            addSidebarCheckMark(act, marks, s, title, delete, sidebarW, checkColor,
                    top, markH, sidebarW);
            shown.add(s.id);
        }
        for (Map.Entry<String, TextView> entry : SIDEBAR_MARK_VIEWS.entrySet()) {
            TextView mark = entry.getValue();
            if (mark != null && !shown.contains(entry.getKey())) {
                // Physically detach recycled/off-screen marks. Keeping a GONE TextView in the
                // overlay lets a stale LazyColumn SID become visible again on a later frame and
                // is the source of the reported ghost checkmark after deselect/delete.
                removeSidebarMark(entry.getKey(), mark);
            }
        }
    }

    private static void removeSidebarMark(String sid, TextView expected) {
        if (sid == null) return;
        TextView mark = SIDEBAR_MARK_VIEWS.get(sid);
        if (mark == null || (expected != null && mark != expected)) return;
        if (!SIDEBAR_MARK_VIEWS.remove(sid, mark)) return;
        try {
            ViewParent parent = mark.getParent();
            if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(mark);
        } catch (Throwable ignored) {}
    }

    /** A disposed LazyColumn item can leave its last coordinate behind. If a newly placed row now
     * occupies the same slot, only the newer SID may own the visual check. */
    private static boolean isSidebarBoundSuperseded(
            String sid, Rect row, Map<String, Rect> bounds) {
        Long ownAt = SIDEBAR_ROW_BOUNDS_AT.get(sid);
        long own = ownAt == null ? 0L : ownAt.longValue();
        for (Map.Entry<String, Rect> entry : bounds.entrySet()) {
            if (sid.equals(entry.getKey())) continue;
            Rect other = entry.getValue();
            if (other == null) continue;
            int tolerance = Math.max(4, Math.min(row.height(), other.height()) / 2);
            if (Math.abs(row.centerY() - other.centerY()) > tolerance) continue;
            Long otherAt = SIDEBAR_ROW_BOUNDS_AT.get(entry.getKey());
            if (otherAt != null && otherAt.longValue() > own) return true;
        }
        return false;
    }

    // 侧栏收起检测：收起抽屉不走 mq5.i.u 回调，但 onGloballyPositioned 会把行左坐标从 0 平移到 -sidebarW。
    private static boolean isSidebarCollapsed(Map<String, Rect> bounds, int sidebarW) {
        if (bounds == null || bounds.isEmpty()) return false;
        int threshold = -sidebarW / 2;
        for (Rect r : bounds.values()) {
            if (r != null && r.left <= threshold) return true;
        }
        return false;
    }

    // 有任一行左坐标接近屏内（> -1/4 sidebarW）即视为侧栏已展开，用于解锁收起检测
    private static boolean sidebarRowsOnScreen(Map<String, Rect> bounds, int sidebarW) {
        if (bounds == null || bounds.isEmpty()) return false;
        int threshold = -sidebarW / 4;
        for (Rect r : bounds.values()) {
            if (r != null && r.left > threshold) return true;
        }
        return false;
    }

    private static void logSidebarBoundsState(String msg) {
        long now = System.currentTimeMillis();
        if (now - sidebarBoundsLogAt < 2500) return;
        sidebarBoundsLogAt = now;
        Main.log(msg);
    }

    static void addSidebarCheckMark(final Activity act, final FrameLayout marks,
                                            final ChatEditorUi.Session s,
                                            final TextView title, final TextView delete,
                                            final int sidebarW, final int checkColor,
                                            int top, int rowH, int rowRight) {
        TextView existing = SIDEBAR_MARK_VIEWS.get(s.id);
        final TextView mark;
        if (existing == null) {
            mark = new TextView(act);
            mark.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
            mark.setIncludeFontPadding(false);
            mark.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            mark.setTypeface(Typeface.DEFAULT_BOLD);
            mark.setPadding(0, 0, DeePXUi.dp(act, 19), 0);
            // Native Compose owns the complete row touch stream so vertical dragging continues
            // to scroll. The wrapped native row callback above turns only a completed tap into a
            // selection; this visual mark must never intercept DOWN/MOVE itself.
            mark.setClickable(false);
            mark.setFocusable(false);
            SIDEBAR_MARK_VIEWS.put(s.id, mark);
            marks.addView(mark);
        } else {
            mark = existing;
        }
        updateSidebarMarkState(mark, s.id, checkColor);
        mark.setVisibility(View.VISIBLE);
        // Selection mode owns the complete native row. This prevents a title tap from opening the
        // chat and makes every point in the row toggle the checkbox as users expect.
        int rightEdge = rowRight > 0 ? Math.min(rowRight, sidebarW) : sidebarW;
        int touchW = Math.max(0, rightEdge);
        FrameLayout.LayoutParams markLp = new FrameLayout.LayoutParams(touchW, rowH);
        markLp.leftMargin = 0;
        markLp.topMargin = top;
        mark.setLayoutParams(markLp);
    }

    private static Map<String, Rect> resolveSidebarSessionBounds(Activity act,
                                                                 List<ChatEditorUi.Session> sessions,
                                                                 int sidebarW) {
        Map<String, Rect> out = new HashMap<>();
        HashSet<String> wanted = new HashSet<>();
        for (int i = 0; i < sessions.size(); i++) {
            String k = sidebarTitleKey(sessions.get(i));
            if (k.length() > 0) wanted.add(k);
        }
        if (wanted.isEmpty()) return out;

        Map<String, ArrayList<Rect>> byTitle = new HashMap<>();
        AccessibilityNodeInfo root = null;
        try {
            View decor = act.getWindow().getDecorView();
            int[] decorLoc = new int[2];
            decor.getLocationOnScreen(decorLoc);
            root = decor.createAccessibilityNodeInfo();
            int minTop = DeePXUi.statusBarHeight(act) + DeePXUi.dp(act, 70);
            collectSidebarTitleBounds(root, decorLoc, sidebarW, minTop, wanted, byTitle, 0);
        } catch (Throwable t) {
            Main.log("resolve sidebar a11y bounds failed: " + t);
        } finally {
            if (root != null) try { root.recycle(); } catch (Throwable ignored) {}
        }

        for (ArrayList<Rect> list : byTitle.values()) {
            Collections.sort(list, new Comparator<Rect>() {
                public int compare(Rect a, Rect b) {
                    if (a.top != b.top) return a.top - b.top;
                    return a.left - b.left;
                }
            });
        }
        for (int i = 0; i < sessions.size(); i++) {
            ChatEditorUi.Session s = sessions.get(i);
            String k = sidebarTitleKey(s);
            if (k.length() == 0) continue;
            ArrayList<Rect> list = byTitle.get(k);
            if (list == null || list.isEmpty()) continue;
            out.put(s.id, list.remove(0));
        }
        return out;
    }

    private static void collectSidebarTitleBounds(AccessibilityNodeInfo node, int[] decorLoc,
                                                  int sidebarW, int minTop, HashSet<String> wanted,
                                                  Map<String, ArrayList<Rect>> byTitle,
                                                  int depth) {
        if (node == null || depth > 80) return;
        try {
            collectSidebarTextBound(node, node.getText(), decorLoc, sidebarW, minTop, wanted, byTitle);
            collectSidebarTextBound(node, node.getContentDescription(), decorLoc, sidebarW, minTop, wanted, byTitle);
            int n = node.getChildCount();
            for (int i = 0; i < n; i++) {
                AccessibilityNodeInfo child = null;
                try {
                    child = node.getChild(i);
                    collectSidebarTitleBounds(child, decorLoc, sidebarW, minTop, wanted, byTitle, depth + 1);
                } catch (Throwable ignored) {
                } finally {
                    if (child != null) try { child.recycle(); } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void collectSidebarTextBound(AccessibilityNodeInfo node, CharSequence cs,
                                                int[] decorLoc, int sidebarW, int minTop,
                                                HashSet<String> wanted,
                                                Map<String, ArrayList<Rect>> byTitle) {
        if (node == null || cs == null) return;
        String text = cs.toString().trim();
        if (!wanted.contains(text)) return;
        Rect r = new Rect();
        node.getBoundsInScreen(r);
        r.offset(-decorLoc[0], -decorLoc[1]);
        if (isLikelySidebarTitleBounds(r, sidebarW, minTop)) putSidebarTitleRect(byTitle, text, r);
    }

    private static boolean isLikelySidebarTitleBounds(Rect r, int sidebarW, int minTop) {
        if (r == null || r.isEmpty()) return false;
        if (r.top < minTop) return false;
        if (r.right <= 0 || r.left >= sidebarW) return false;
        if (r.height() <= 0 || r.height() > 80) return false;
        return r.width() > 0;
    }

    private static void putSidebarTitleRect(Map<String, ArrayList<Rect>> byTitle,
                                            String title, Rect r) {
        ArrayList<Rect> list = byTitle.get(title);
        if (list == null) {
            list = new ArrayList<>();
            byTitle.put(title, list);
        }
        for (int i = 0; i < list.size(); i++) {
            Rect old = list.get(i);
            if (Math.abs(old.centerY() - r.centerY()) <= 3 && Math.abs(old.left - r.left) <= 3) return;
        }
        list.add(new Rect(r));
    }

    private static String sidebarTitleKey(ChatEditorUi.Session s) {
        if (s == null || s.title == null) return "";
        return s.title.trim();
    }

    private static void updateSidebarMarkState(TextView mark, String sid, int checkColor) {
        boolean checked = sid != null && SIDEBAR_SELECTED.contains(sid);
        mark.setText(checked ? "\u2713" : "");
        mark.setTextColor(checkColor);
        mark.setBackground(null);
    }

    private static void toggleSidebarSelection(String sid) {
        if (sid == null) return;
        if (SIDEBAR_SELECTED.contains(sid)) SIDEBAR_SELECTED.remove(sid);
        else SIDEBAR_SELECTED.add(sid);
    }

    static void exitSidebarSelectMode() {
        sidebarSelectMode = false;
        SIDEBAR_SELECTED.clear();
        removeSidebarSelectOverlay();
    }

    // 侧边栏收回时调用：多选覆盖层向上滑出并淡出后再移除。
    private static void slideOutSidebarOverlayAndExit() {
        sidebarSelectMode = false;
        SIDEBAR_SELECTED.clear();
        final View v = sidebarSelectOverlay;
        sidebarSelectOverlay = null;
        SIDEBAR_MARK_VIEWS.clear();
        SIDEBAR_FALLBACK_BOUNDS.clear();
        sidebarFallbackBoundsAt = 0L;
        sidebarSelectionTitle = new WeakReference<>(null);
        sidebarSelectionDelete = new WeakReference<>(null);
        if (v == null) return;
        final Runnable anim = new Runnable() {
            public void run() {
                try {
                    int dist = v.getHeight() > 0 ? v.getHeight()
                            : v.getResources().getDisplayMetrics().heightPixels;
                    v.animate().translationY(-dist).alpha(0f).setDuration(220)
                            .setInterpolator(new android.view.animation.AccelerateInterpolator())
                            .withEndAction(new Runnable() {
                                public void run() {
                                    try {
                                        ViewGroup p = (ViewGroup) v.getParent();
                                        if (p != null) p.removeView(v);
                                    } catch (Throwable ignored) {}
                                }
                            }).start();
                } catch (Throwable t) {
                    try {
                        ViewGroup p = (ViewGroup) v.getParent();
                        if (p != null) p.removeView(v);
                    } catch (Throwable ignored) {}
                }
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) anim.run();
        else new Handler(Looper.getMainLooper()).post(anim);
    }

    private static void removeSidebarSelectOverlay() {
        View v = sidebarSelectOverlay;
        sidebarSelectOverlay = null;
        SIDEBAR_MARK_VIEWS.clear();
        SIDEBAR_FALLBACK_BOUNDS.clear();
        sidebarFallbackBoundsAt = 0L;
        sidebarSelectionTitle = new WeakReference<>(null);
        sidebarSelectionDelete = new WeakReference<>(null);
        if (v == null) return;
        try {
            ViewGroup p = (ViewGroup) v.getParent();
            if (p != null) p.removeView(v);
        } catch (Throwable ignored) {}
    }

    private static void confirmSidebarBatchDelete(final Activity act,
                                                  final List<ChatEditorUi.Session> sessions,
                                                  int n) {
        final Dialog dlg = new Dialog(act);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        boolean dark = DeePXUi.isDark(act);
        int cardColor = dark ? 0xFF2A2A2D : 0xFFFFFFFF;
        int textColor = dark ? 0xFFECECEC : 0xFF1A1A1A;
        int subColor = dark ? 0xFFB0B0B4 : 0xFF666666;
        int divColor = dark ? 0xFF3A3A3D : 0xFFEAEAEA;

        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(DeePXUi.dp(act, 22), DeePXUi.dp(act, 20),
                DeePXUi.dp(act, 22), DeePXUi.dp(act, 10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cardColor);
        bg.setCornerRadius(DeePXUi.dp(act, 18));
        card.setBackground(bg);

        TextView title = new TextView(act);
        title.setText(UiLanguage.text(act,
                "删除 " + n + " 个对话", "Delete " + n + " chats"));
        title.setTextColor(textColor);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        card.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView msg = new TextView(act);
        msg.setText("删除后会从当前列表移除。未被原版列表加载的条目会用本地数据库删除兜底。");
        msg.setTextColor(subColor);
        msg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        msg.setLineSpacing(DeePXUi.dp(act, 2), 1.0f);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = DeePXUi.dp(act, 10);
        card.addView(msg, mlp);

        View line = new View(act);
        line.setBackgroundColor(divColor);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        llp.topMargin = DeePXUi.dp(act, 18);
        card.addView(line, llp);

        LinearLayout buttons = new LinearLayout(act);
        buttons.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, DeePXUi.dp(act, 48));
        card.addView(buttons, blp);

        TextView cancel = new TextView(act);
        cancel.setText("取消");
        cancel.setTextColor(subColor);
        cancel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(DeePXUi.dp(act, 14), 0, DeePXUi.dp(act, 14), 0);
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { dlg.dismiss(); }
        });
        buttons.addView(cancel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView del = new TextView(act);
        del.setText("删除");
        del.setTextColor(0xFFE53935);
        del.setTypeface(Typeface.DEFAULT_BOLD);
        del.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        del.setGravity(Gravity.CENTER);
        del.setPadding(DeePXUi.dp(act, 14), 0, DeePXUi.dp(act, 4), 0);
        del.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                dlg.dismiss();
                deleteSidebarSelected(act, sessions);
            }
        });
        buttons.addView(del, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        UiLanguage.localizeTree(act, card);
        dlg.setContentView(card);
        dlg.show();
        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setDimAmount(0.32f);
            w.setLayout(Math.min(DeePXUi.dp(act, 320),
                    act.getResources().getDisplayMetrics().widthPixels - DeePXUi.dp(act, 48)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private static List<ChatEditorUi.Session> loadCurrentSidebarSessions(Activity act) {
        List<ChatEditorUi.Session> out = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        File f = ChatEditorUi.currentDb(act.getClassLoader());
        if (f == null) return out;
        SQLiteDatabase d = null;
        Cursor c = null;
        try {
            d = SQLiteDatabase.openDatabase(f.getPath(), null, SQLiteDatabase.OPEN_READONLY);
            c = d.rawQuery("SELECT id,title FROM chat_session_list ORDER BY updated_at DESC", null);
            while (c.moveToNext()) {
                ChatEditorUi.Session s = new ChatEditorUi.Session();
                s.id = c.getString(0);
                s.title = c.getString(1);
                s.dbPath = f.getPath();
                if (s.id != null && !Main.isLocalApiInternalSession(s.id)) {
                    out.add(s);
                    seen.add(s.id);
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (c != null) try { c.close(); } catch (Throwable ignored) {}
            if (d != null) try { d.close(); } catch (Throwable ignored) {}
        }
        // A just-synchronized cloud conversation may be visible in the native sidebar before its
        // directory row reaches SQLite. Include it so batch selection/deletion is not silently
        // limited to the older database snapshot.
        for (Object[] row : nativeSessionDirectory()) {
            if (row == null || row.length < 2 || row[0] == null) continue;
            String sid = String.valueOf(row[0]);
            if (sid.length() == 0 || !seen.add(sid)) continue;
            ChatEditorUi.Session s = new ChatEditorUi.Session();
            s.id = sid;
            s.title = row[1] == null ? "" : String.valueOf(row[1]);
            s.dbPath = f.getPath();
            s.nativeOnly = true;
            out.add(s);
        }
        return out;
    }

    private static void deleteSidebarSelected(final Activity act, List<ChatEditorUi.Session> sessions) {
        final ArrayList<NativeDeleteRequest> nativeQueue = new ArrayList<>();
        int matched = 0;
        final Map<String, List<String>> local = new HashMap<>();
        HashSet<String> selected = new HashSet<>(SIDEBAR_SELECTED);
        HashSet<String> editorLocal = ChatEditorUi.localSessionIdsFromAllBackups();
        // Freeze every host object before the first delete event mutates Compose's directory.
        // Looking sessions up lazily is what made large batches lose their native chain midway.
        final Object eventSink = NATIVE_SESSION_EVENTS;
        for (int i = 0; i < sessions.size(); i++) {
            ChatEditorUi.Session s = sessions.get(i);
            if (s.id == null || !selected.contains(s.id)) continue;
            matched++;
            Object action;
            synchronized (SIDEBAR_DELETE_ACTIONS) {
                action = SIDEBAR_DELETE_ACTIONS.get(s.id);
            }
            if (editorLocal.contains(s.id)) {
                // Editor-owned rows have no cloud delete endpoint. Delete them only through the
                // module's SQLite/sidecar transaction; sending a native request first races WCDB
                // and returns the misleading code-1 "conversation deleted" response.
                List<String> ids = local.get(s.dbPath);
                if (ids == null) {
                    ids = new ArrayList<>();
                    local.put(s.dbPath, ids);
                }
                ids.add(s.id);
            } else {
                nativeQueue.add(new NativeDeleteRequest(
                        s.id, Main.findNativeSession(s.id), eventSink, action));
            }
        }
        exitSidebarSelectMode();
        if (matched <= 0) {
            UiLanguage.toast(act, "没有匹配到可删除的对话", Toast.LENGTH_SHORT).show();
            return;
        }
        UiLanguage.toast(act, "正在删除 " + matched + " 个对话", Toast.LENGTH_SHORT).show();
        runNativeDeleteQueue(act, nativeQueue, local,
                Math.max(0, selected.size() - matched));
    }

    private static boolean invokeXa3(Object action) {
        if (action == null) return false;
        try {
            for (Method m : action.getClass().getMethods()) {
                if (!m.getName().equals("u") || m.getParameterTypes().length != 0) continue;
                m.setAccessible(true);
                m.invoke(action);
                return true;
            }
            for (Method m : action.getClass().getDeclaredMethods()) {
                if (!m.getName().equals("u") || m.getParameterTypes().length != 0) continue;
                m.setAccessible(true);
                m.invoke(action);
                return true;
            }
        } catch (Throwable t) { Main.log("invoke sidebar delete action failed: " + t); }
        return false;
    }

    private static Object invokeXa3Returning(Object action, ClassLoader cl) {
        if (action == null) return ui8Unit(cl);
        try {
            for (Method m : action.getClass().getMethods()) {
                if (!m.getName().equals("u") || m.getParameterTypes().length != 0) continue;
                m.setAccessible(true);
                return m.invoke(action);
            }
            for (Method m : action.getClass().getDeclaredMethods()) {
                if (!m.getName().equals("u") || m.getParameterTypes().length != 0) continue;
                m.setAccessible(true);
                return m.invoke(action);
            }
        } catch (Throwable t) { Main.log("invoke sidebar toggle action failed: " + t); }
        return ui8Unit(cl);
    }

    static final class NativeHeartbeatHistory {
        final Object response;
        final Object session;
        final String sid;
        final List messages;
        final Integer head;
        final Integer cacheVersion;
        final Integer cacheReset;
        final boolean reasoning;
        final String nativeModel;

        NativeHeartbeatHistory(Object response, Object session, String sid,
                               List messages, Integer head,
                               Integer cacheVersion, Integer cacheReset,
                               boolean reasoning, String nativeModel) {
            this.response = response;
            this.session = session;
            this.sid = sid;
            this.messages = messages;
            this.head = head;
            this.cacheVersion = cacheVersion;
            this.cacheReset = cacheReset;
            this.reasoning = reasoning;
            this.nativeModel = Main.normalizeNativeHeartbeatModel(nativeModel);
        }
    }

    static boolean mergeNativeHeartbeatHistoryIntoSession(
            NativeHeartbeatHistory history, Object session) {
        if (history == null || session == null
                || !history.sid.equals(String.valueOf(
                        Main.readHostField(session, "a")))) return false;
        try {
            Method merge = session.getClass().getMethod(
                    "v", List.class, Integer.class, boolean.class);
            merge.setAccessible(true);
            merge.invoke(session, history.messages, history.head, false);
            Main.forceSetObjectField(session, "n", history.cacheVersion);
            Main.forceSetObjectField(session, "o", history.cacheReset);
            HistoryBridge.processNativeSession(session, history.sid);
            return true;
        } catch (Throwable error) {
            Main.log("proactive native session apply failed sid=" + history.sid
                    + ": " + Main.safeThrowableMessage(error));
            return false;
        }
    }

    /** Called when a native chat ViewModel becomes available, including after switching chats. */
    private static void recoverAgentRunsForScope(Context context, String scope) {
        if (!AgentToolConfig.enabledFast()) return;
        String safeScope = HeartbeatToolProtocol.cleanScope(scope);
        if (safeScope.length() == 0) return;
        for (AgentRunStore.Record record : AgentRunStore.pending()) {
            if (safeScope.equals(record.scope)) {
                Main.queueHiddenAgentEvent(
                        context, record.scope, record.event, record.outboxId);
            }
        }
    }

    static Context currentHostContext() {
        Context application = Main.hostApplicationContext;
        return application != null ? application : HookAgentPipeline.currentHostActivity();
    }

    private static HashSet<String> localOnlySessionIds(ClassLoader cl) {
        File file = null;
        String dbPath = "";
        try {
            file = ChatEditorUi.currentDb(cl);
            dbPath = file == null ? "" : file.getAbsolutePath();
        } catch (Throwable ignored) {}
        HashSet<String> cached = LOCAL_SESSION_IDS;
        if (LOCAL_SESSION_IDS_AT > 0L
                && dbPath.equals(LOCAL_SESSION_IDS_DB_PATH)) {
            return new HashSet<>(cached);
        }
        HashSet<String> found = new HashSet<>();
        try {
            found = ChatEditorUi.localSessionDisplayIdsFromBackups(file);
        } catch (Throwable t) {
            Main.log("read local-only sidecars failed: " + t);
        }
        LOCAL_SESSION_IDS = found;
        LOCAL_SESSION_IDS_AT = System.currentTimeMillis();
        LOCAL_SESSION_IDS_DB_PATH = dbPath;
        return new HashSet<>(found);
    }

    /** Builds the same host session model used by the sidebar, using an existing row only as a
     * source for model/mode defaults. No message state is shared between the two sessions. */
    static Object createEditorLocalNativeSession(String sid, List state) {
        if (sid == null || state == null || state.isEmpty()) return null;
        Object template = null;
        for (Object candidate : new ArrayList<Object>(state)) {
            if (candidate != null && Main.readHostField(candidate, "a") instanceof String) {
                template = candidate;
                break;
            }
        }
        if (template == null) {
            for (WeakReference<Object> reference : ACTIVE_CHAT_SESSIONS.values()) {
                Object candidate = reference == null ? null : reference.get();
                if (candidate != null && Main.readHostField(candidate, "a") instanceof String) {
                    template = candidate;
                    break;
                }
            }
        }
        if (template == null) return null;
        try {
            Constructor<?> target = null;
            for (Constructor<?> constructor : template.getClass().getDeclaredConstructors()) {
                Class<?>[] p = constructor.getParameterTypes();
                if (p.length == 11 && p[0] == String.class && p[1] == String.class
                        && p[2] == String.class && p[3] == double.class
                        && p[4] == double.class && p[8] == boolean.class) {
                    target = constructor;
                    break;
                }
            }
            if (target == null) return null;
            Class<?>[] p = target.getParameterTypes();
            Object templateModelState = Main.readHostField(template, "d");
            Object modelValue = Main.invokeNoArg(templateModelState, "getValue");
            Object modelState = newHostMutableState(template.getClass().getClassLoader(),
                    p[9], modelValue);
            if (modelState == null) return null;
            Object modeState = Main.readHostField(template, "j");
            Object conversationMode = Main.invokeNoArg(modeState, "getValue");
            if (HostCompat.isV241()) {
                // code257's final constructor argument is hq (message-load state), not the
                // pre-code257 conversation mode. Reusing a neighbouring pq's gq here also
                // reuses its k88 visible-message list, contaminating the new local SID with a
                // foreign row and sending it through the host's deleted-session presenter.
                conversationMode = newV241EmptyMessageLoadState(conversationMode, p[10]);
            }
            if (conversationMode == null || !p[10].isInstance(conversationMode)) return null;
            Object[] metadata = ChatEditorUi.localSessionNativeMetadata(sid);
            double now = System.currentTimeMillis() / 1000.0d;
            String title = metadata == null ? "新对话" : String.valueOf(metadata[0]);
            String titleType = metadata == null ? "SYSTEM" : String.valueOf(metadata[1]);
            double inserted = metadata != null && metadata[2] instanceof Number
                    ? ((Number) metadata[2]).doubleValue() : now;
            double updated = metadata != null && metadata[3] instanceof Number
                    ? ((Number) metadata[3]).doubleValue() : inserted;
            target.setAccessible(true);
            return target.newInstance(sid, title, titleType, inserted, updated,
                    null, null, Integer.valueOf(5), false, modelState, conversationMode);
        } catch (Throwable error) {
            Main.log("create editor-local native session failed sid=" + sid + " err=" + error);
            return null;
        }
    }

    /** Exact code257 equivalent of {@code new gq(null, 7)}; never called by legacy hosts. */
    private static Object newV241EmptyMessageLoadState(Object templateState,
                                                        Class<?> expectedType) {
        if (!HostCompat.isV241() || templateState == null || expectedType == null) return null;
        try {
            Class<?> stateClass = templateState.getClass();
            for (Constructor<?> constructor : stateClass.getDeclaredConstructors()) {
                Class<?>[] types = constructor.getParameterTypes();
                if (types.length != 2 || types[1] != int.class || types[0].isPrimitive()) continue;
                constructor.setAccessible(true);
                Object fresh = constructor.newInstance(null, Integer.valueOf(7));
                if (expectedType.isInstance(fresh)) return fresh;
            }
        } catch (Throwable error) {
            Main.log("create code257 empty message state failed: " + error);
        }
        return null;
    }

    private static Object newHostMutableState(ClassLoader loader, Class<?> stateType,
                                               Object value) {
        if (loader == null || stateType == null) return null;
        String[] knownFactories = HostCompat.isV241()
                ? new String[]{"pi5"}
                : HostCompat.isV236()
                ? new String[]{"eu1"}
                : HostCompat.isV234()
                ? (HostCompat.isGooglePlay() ? new String[]{"rv1"} : new String[]{"no0"})
                : new String[]{"rv1", "no0", "eg1", "oh0"};
        for (String ownerName : knownFactories) {
            try {
                Class<?> owner = Class.forName(ownerName, false, loader);
                for (Method method : owner.getDeclaredMethods()) {
                    Class<?>[] p = method.getParameterTypes();
                    if (!Modifier.isStatic(method.getModifiers()) || p.length != 1
                            || !stateType.isAssignableFrom(method.getReturnType())) continue;
                    method.setAccessible(true);
                    Object result = method.invoke(null, value);
                    if (stateType.isInstance(result)) return result;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /**
     * DeepSeek's p68 cloud-directory transaction asks aw.a() for every local session, then drops
     * tables whose ids are absent from the server response. Hide only editor-owned sidecar ids from
     * that one comparison. Incoming server rows and ordinary server-side deletions stay untouched.
     */
    void hookLocalSessionDirectoryMerge(final ClassLoader cl) {
        try {
            Class<?> transaction = HostCompat.load(cl, "p68");
            Class<?> directoryDao = HostCompat.load(cl, "aw");
            int transactionHooks = 0;
            int directoryHooks = 0;
            for (Method method : transaction.getDeclaredMethods()) {
                if (!HostCompat.method("p68", "a").equals(method.getName())
                        || method.getParameterTypes().length != 0
                        || method.getReturnType() != void.class) continue;
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        int preservedHeads = preserveFrozenDirectoryHeads(chain.getThisObject());
                        if (preservedHeads > 0) {
                            long now = System.currentTimeMillis();
                            if (now - LOCAL_DIRECTORY_HEAD_LOG_AT > 5000L) {
                                LOCAL_DIRECTORY_HEAD_LOG_AT = now;
                                Main.log("preserved frozen conversation heads during cloud sync="
                                        + preservedHeads);
                            }
                        }
                        Boolean previous = LOCAL_DIRECTORY_SYNC.get();
                        LOCAL_DIRECTORY_SYNC.set(Boolean.TRUE);
                        try {
                            return chain.proceed();
                        } finally {
                            if (previous == null) LOCAL_DIRECTORY_SYNC.remove();
                            else LOCAL_DIRECTORY_SYNC.set(previous);
                        }
                    }
                });
                transactionHooks++;
            }
            for (Method method : directoryDao.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!HostCompat.method("aw", "a").equals(method.getName())
                        || !java.lang.reflect.Modifier.isStatic(method.getModifiers())
                        || types.length != 1 || types[0] != directoryDao
                        || !List.class.isAssignableFrom(method.getReturnType())) continue;
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object result = chain.proceed();
                        if (!Boolean.TRUE.equals(LOCAL_DIRECTORY_SYNC.get())
                                || !(result instanceof List)) return result;
                        HashSet<String> localIds = ChatEditorUi.localSessionIdsFromAllBackups();
                        if (localIds.isEmpty()) return result;
                        List rows = (List) result;
                        int removed = 0;
                        for (int i = rows.size() - 1; i >= 0; i--) {
                            Object row = rows.get(i);
                            Object value = Main.readHostField(row, "a");
                            String sid = value == null ? null : String.valueOf(value);
                            if (sid != null && localIds.contains(sid)) {
                                rows.remove(i);
                                removed++;
                            }
                        }
                        if (removed > 0) {
                            long now = System.currentTimeMillis();
                            if (now - LOCAL_DIRECTORY_MERGE_LOG_AT > 5000L) {
                                LOCAL_DIRECTORY_MERGE_LOG_AT = now;
                                Main.log("excluded editor-local sessions from cloud prune=" + removed);
                            }
                        }
                        return result;
                    }
                });
                directoryHooks++;
            }
            Main.log("installed local cloud-directory merge p68=" + transactionHooks
                    + " aw=" + directoryHooks);
        } catch (Throwable t) {
            Main.log("hookLocalSessionDirectoryMerge failed: " + t);
        }
    }

    /**
     * The delayed server refresh is applied in ed0.h.  That method mutates ed0.e, the canonical
     * SnapshotStateList observed by navigation, before p68 updates the WCDB directory.  Keeping a
     * local tp only in mc.f's render argument therefore leaves the active-chat validator looking
     * at a server-only list and the editor-created conversation disappears a few seconds after a
     * cold start.  Capture editor-owned tp objects before every coroutine leg and put only those
     * missing objects back into the same state list after the leg completes.  Server additions,
     * metadata updates, ordering, and ordinary server-side deletions remain host-owned.
     */
    void hookLocalNativeSessionRefresh(final ClassLoader cl) {
        try {
            Class<?> repository = HostCompat.load(cl, "ed0");
            Class<?> continuation = HostCompat.load(cl, "uz1");
            int installed = 0;
            for (Method method : repository.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!HostCompat.method("ed0", "h").equals(method.getName())
                        || types.length != 1
                        || types[0] != continuation) continue;
                try { Main.MODULE.deoptimize(method); } catch (Throwable ignored) {}
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        liveNativeSessionRepository = chain.getThisObject();
                        Object before = Main.readHostField(chain.getThisObject(),
                                HostCompat.editorSessionStateField());
                        HashSet<String> localIds = localOnlySessionIds(cl);
                        if (before instanceof List
                                && HostCompat.simpleNameIs(before, "uo7")) {
                            preserveEditorLocalNativeSessions((List) before, localIds);
                        }
                        try {
                            return chain.proceed();
                        } finally {
                            Object after = Main.readHostField(chain.getThisObject(),
                                    HostCompat.editorSessionStateField());
                            if (after instanceof List
                                    && HostCompat.simpleNameIs(after, "uo7")) {
                                int restored = preserveEditorLocalNativeSessions(
                                        (List) after, localIds);
                                if (restored > 0) {
                                    long now = System.currentTimeMillis();
                                    if (now - LOCAL_NATIVE_STATE_REPAIR_LOG_AT > 1000L) {
                                        LOCAL_NATIVE_STATE_REPAIR_LOG_AT = now;
                                        Main.log("restored editor-local sessions into native state="
                                                + restored + " host sessions="
                                                + ((List) after).size());
                                    }
                                }
                            }
                        }
                    }
                });
                installed++;
            }
            Main.log("installed editor-local native-state refresh guard ed0.h x" + installed);
        } catch (Throwable t) {
            Main.log("hookLocalNativeSessionRefresh failed: " + t);
        }
    }

    /** Package-visible for the JVM regression: merge into the canonical host list, not a copy. */
    static int preserveEditorLocalNativeSessions(List state, HashSet<String> localIds) {
        if (state == null || localIds == null || localIds.isEmpty()) return 0;
        // Cold starts have no in-memory LOCAL_NATIVE_SESSIONS cache. Materialise the sidebar row
        // from the durable sidecar before merging, otherwise locally-created conversations remain
        // present in SQLite but invisible until a server row happens to share their id.
        for (String sid : new HashSet<String>(localIds)) {
            boolean cached;
            synchronized (LOCAL_NATIVE_SESSIONS) {
                cached = LOCAL_NATIVE_SESSIONS.containsKey(sid);
            }
            if (!cached) {
                Object session = createEditorLocalNativeSession(sid, state);
                if (session != null) {
                    synchronized (LOCAL_NATIVE_SESSIONS) {
                        LOCAL_NATIVE_SESSIONS.put(sid, session);
                    }
                }
            }
        }
        HashSet<String> seen = new HashSet<>();
        ArrayList<Object> missing = new ArrayList<>();
        synchronized (LOCAL_NATIVE_SESSIONS) {
            LOCAL_NATIVE_SESSIONS.keySet().retainAll(localIds);
            try {
                for (Object session : new ArrayList<Object>(state)) {
                    Object value = Main.readHostField(session, "a");
                    String sid = value == null ? null : String.valueOf(value);
                    if (sid == null || sid.length() == 0 || "null".equals(sid)) continue;
                    seen.add(sid);
                    if (localIds.contains(sid) && !isSessionRecentlyDeleted(sid)) {
                        LOCAL_NATIVE_SESSIONS.put(sid, session);
                    }
                }
                for (String sid : localIds) {
                    if (seen.contains(sid) || isSessionRecentlyDeleted(sid)) continue;
                    Object session = LOCAL_NATIVE_SESSIONS.get(sid);
                    if (session != null) missing.add(session);
                }
            } catch (Throwable t) {
                Main.log("capture editor-local native state failed: " + t);
                return 0;
            }
        }
        int restored = 0;
        for (Object session : missing) {
            String sid = String.valueOf(Main.readHostField(session, "a"));
            if (isSessionRecentlyDeleted(sid)) continue;
            boolean alreadyPresent = false;
            try {
                for (Object current : new ArrayList<Object>(state)) {
                    if (sid.equals(String.valueOf(Main.readHostField(current, "a")))) {
                        alreadyPresent = true;
                        break;
                    }
                }
                if (!alreadyPresent && state.add(session)) restored++;
            } catch (Throwable t) {
                Main.log("restore editor-local native session failed sid=" + sid + ": " + t);
            }
        }
        if (restored > 0) sortNativeSessionDirectory(state);
        NATIVE_SESSION_STATE = state;
        NATIVE_SESSION_LIST = state;
        return restored;
    }

    static void sortNativeSessionDirectory(List state) {
        if (state == null || state.size() < 2) return;
        try {
            Collections.sort(state, new Comparator<Object>() {
                @Override public int compare(Object left, Object right) {
                    boolean leftPinned = nativeSessionPinned(left);
                    boolean rightPinned = nativeSessionPinned(right);
                    if (leftPinned != rightPinned) return leftPinned ? -1 : 1;
                    Object leftUpdated = Main.readHostField(left, "c");
                    Object rightUpdated = Main.readHostField(right, "c");
                    double l = leftUpdated instanceof Number
                            ? ((Number) leftUpdated).doubleValue() : 0d;
                    double r = rightUpdated instanceof Number
                            ? ((Number) rightUpdated).doubleValue() : 0d;
                    return l == r ? 0 : (l > r ? -1 : 1);
                }
            });
        } catch (Throwable t) {
            Main.log("sort editor-local native sessions failed: " + t);
        }
    }

    private static boolean nativeSessionPinned(Object session) {
        Object state = Main.readHostField(session, "h");
        return Boolean.TRUE.equals(Main.invokeNoArg(state, "getValue"));
    }

    static Object nativeSessionHead(Object session) {
        return Main.invokeNoArg(session, HostCompat.isV234() ? "f" : "t");
    }

    /**
     * p68 deliberately keeps cache_version but overwrites current_message_id from the lightweight
     * server directory.  Some directory entries omit that field.  Copy the valid local head into
     * only those null incoming entries before WCDB applies the normal title/count merge.
     */
    private static int preserveFrozenDirectoryHeads(Object transaction) {
        try {
            // 2.3.4 wraps the WCDB transaction in synthetic multi-interface classes.  The GP
            // wrapper prepends an integer discriminator, shifting payload/repository from a/b to
            // b/c; the mainland wrapper still uses a/b.
            String incomingField = HostCompat.isV234() && HostCompat.isGooglePlay()
                    ? "b" : "a";
            String repositoryField = HostCompat.isV234() && HostCompat.isGooglePlay()
                    ? "c" : "b";
            Object incomingValue = Main.readHostField(transaction, incomingField);
            Object repository = Main.readHostField(transaction, repositoryField);
            Object directory = Main.readHostField(repository, "d");
            if (!(incomingValue instanceof List) || directory == null) return 0;

            Method reader = null;
            for (Method method : directory.getClass().getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (HostCompat.method("aw", "a").equals(method.getName())
                        && java.lang.reflect.Modifier.isStatic(method.getModifiers())
                        && types.length == 1 && types[0] == directory.getClass()
                        && List.class.isAssignableFrom(method.getReturnType())) {
                    reader = method;
                    break;
                }
            }
            if (reader == null) return 0;
            reader.setAccessible(true);
            Object localValue = reader.invoke(null, directory);
            if (!(localValue instanceof List)) return 0;

            HashMap<String, Object> frozenHeads = new HashMap<>();
            for (Object local : (List) localValue) {
                Object version = Main.readHostField(local, "d");
                Object head = Main.readHostField(local, "h");
                Object id = Main.readHostField(local, "a");
                if (version instanceof Number
                        && ((Number) version).intValue() == Integer.MAX_VALUE
                        && head != null && id != null) {
                    String sid = String.valueOf(id);
                    frozenHeads.put(sid, head);
                    if (head instanceof Number) {
                        Main.FROZEN_SESSION_HEADS.put(sid, ((Number) head).intValue());
                    }
                }
            }
            if (frozenHeads.isEmpty()) return 0;

            int preserved = 0;
            for (Object incoming : (List) incomingValue) {
                Object id = Main.readHostField(incoming, "a");
                if (id == null || Main.readHostField(incoming, "h") != null) continue;
                Object head = frozenHeads.get(String.valueOf(id));
                if (head == null) continue;
                if (Main.forceSetObjectField(incoming, "h", head)) preserved++;
            }
            return preserved;
        } catch (Throwable t) {
            Main.log("preserve frozen conversation heads failed: " + t);
            return 0;
        }
    }

    /** Local-only editor conversations have no detail endpoint; their za1 constructor already
     * loads the WCDB table.  Suppress the redundant fa1 remote reload that otherwise reports the
     * session as deleted and replaces the successfully loaded local state with an empty chat. */
    void hookLocalSessionRemoteReload(final ClassLoader cl) {
        try {
            Class<?> viewModel = HostCompat.load(cl, "za1");
            Class<?> action = HostCompat.isV241()
                    ? Class.forName("jg1", false, cl)
                    : HostCompat.isV236()
                    ? Class.forName("ed1", false, cl)
                    : HostCompat.load(cl, "na1");
            int installed = 0;
            for (Method method : viewModel.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                String reloadMethod = HostCompat.isV241() ? "E"
                        : HostCompat.isV236() ? "D" : "E";
                if (!reloadMethod.equals(method.getName()) || types.length != 1 || types[0] != action
                        || method.getReturnType() != void.class) continue;
                try { Main.MODULE.deoptimize(method); } catch (Throwable ignored) {}
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        try {
                            Object event = chain.getArg(0);
                            String remoteReloadEvent = HostCompat.isV241()
                                    ? "eg1" : HostCompat.isV236()
                                    ? "vc1" : HostCompat.isV230() ? "eb1" : "fa1";
                            if (event != null && remoteReloadEvent.equals(
                                    event.getClass().getSimpleName())) {
                                Object session = Main.invokeNoArg(chain.getThisObject(),
                                        HostCompat.chatViewModelMethod("G"));
                                Object id = Main.readHostField(session, "a");
                                String sid = id == null ? null : String.valueOf(id);
                                if (sid != null && Main.FROZEN_SESSION_HEADS.containsKey(sid)) {
                                    boolean localOnly = ChatEditorUi
                                            .localSessionIdsFromAllBackups().contains(sid);
                                    if (HostCompat.isV241()) {
                                        // code257 eg1 is the exact boolean/String successor of
                                        // code249 vc1. Its cloud detail request is equally
                                        // redundant once the frozen local session is hydrated.
                                        if (localOnly && isFrozenNativeSessionHydrated(session)) {
                                            Main.log("skipped code257 cloud reload for hydrated "
                                                    + "editor-local sid=" + sid);
                                            return null;
                                        }
                                    } else if (HostCompat.isV236()) {
                                        // code249 D(vc1) starts md1's cloud detail reload after
                                        // the native click callback has already hydrated the
                                        // editor-owned WCDB rows. That cloud request can only
                                        // return code 1 and drive the deleted-session presenter.
                                        if (localOnly && isFrozenNativeSessionHydrated(session)) {
                                            Main.log("skipped code249 cloud reload for hydrated "
                                                    + "editor-local sid=" + sid);
                                            return null;
                                        }
                                    } else if (!HostCompat.isV230()) {
                                        // 2.2.x: the constructor already loaded the WCDB table, so
                                        // the reload would only issue the redundant remote detail
                                        // request that reports the session as server-deleted.
                                        if (localOnly
                                                || isFrozenNativeSessionHydrated(session)) {
                                            Main.log("skipped remote detail reload for editor-frozen"
                                                    + " sid=" + sid + " hydrated="
                                                    + isFrozenNativeSessionHydrated(session));
                                            return null;
                                        }
                                    } else {
                                        // 2.3.0: the reload flow itself drives the local WCDB load
                                        // (ub1 -> vm9.P) before the detail request, so it must run;
                                        // the vm9.W hook neutralizes the deletion response for
                                        // editor-frozen ids.
                                        Main.log("allowed editor-local detail reload sid=" + sid
                                                + " localOnly=" + localOnly + " hydrated="
                                                + isFrozenNativeSessionHydrated(session));
                                    }
                                }
                            }
                        } catch (Throwable t) {
                            Main.log("inspect editor-local remote reload failed: " + t);
                        }
                        return chain.proceed();
                    }
                });
                installed++;
            }
            Main.log("installed editor-local remote reload guard "
                    + viewModel.getSimpleName() + "."
                    + (HostCompat.isV241() ? "E"
                    : HostCompat.isV236() ? "D" : "E") + " x" + installed);
        } catch (Throwable t) {
            Main.log("hookLocalSessionRemoteReload failed: " + t);
        }
    }

    /**
     * 2.3.0 only: the allowed detail reload runs ub1, which performs the local WCDB load via
     * vm9.P and then issues the remote detail request via vm9.W.  Editor-frozen sessions have no
     * server counterpart; short-circuiting W with an empty success result lets the flow finish
     * without the server-deleted shutdown branch.
     */
    void hookNativeDetailRequest(final ClassLoader cl) {
        if (!HostCompat.isV230() || HostCompat.isV234()) return;
        try {
            Class<?> repository = HostCompat.load(cl, "lj9");
            Class<?> result = HostCompat.load(cl, "ds5");
            Constructor<?> okCtor = null;
            for (Constructor<?> ctor : result.getDeclaredConstructors()) {
                if (ctor.getParameterTypes().length == 2) {
                    okCtor = ctor;
                    break;
                }
            }
            if (okCtor == null) throw new NoSuchMethodException("ds5 ctor");
            final Constructor<?> okCtorF = okCtor;
            okCtor.setAccessible(true);
            int installed = 0;
            for (Method method : repository.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!"W".equals(method.getName()) || types.length != 4
                        || !"aq".equals(types[0].getSimpleName())
                        || !"d22".equals(types[3].getSimpleName())) continue;
                try { Main.MODULE.deoptimize(method); } catch (Throwable ignored) {}
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object session = chain.getArg(0);
                        Object id = Main.readHostField(session, "a");
                        String sid = id == null ? null : String.valueOf(id);
                        if (sid != null && Main.FROZEN_SESSION_HEADS.containsKey(sid)) {
                            boolean localOnly = ChatEditorUi
                                    .localSessionIdsFromAllBackups().contains(sid);
                            if (localOnly) {
                                Main.log("short-circuited editor-local detail request sid=" + sid);
                                return okCtorF.newInstance((Object) null, (Object) null);
                            }
                        }
                        return chain.proceed();
                    }
                });
                installed++;
            }
            Main.log("installed editor-local detail request guard vm9.W x" + installed);
        } catch (Throwable t) {
            Main.log("hookNativeDetailRequest failed: " + t);
        }
    }

    /**
     * A conversation created by the editor intentionally has no cloud counterpart. DeepSeek still
     * performs its normal detail request when that row is opened; biz code 1 is handled by at0.a()
     * as a server-side deletion, which shows a toast and removes the otherwise valid local tp.
     * Suppress only that exact result for ids owned by our sidecars. All cloud conversations and
     * every other error continue through the host unchanged.
     */
    void hookLocalSessionDeletedResponse(final ClassLoader cl) {
        // code257 no longer routes this result through the old at0.a helper.
        // zg1.N and its final wv0.a presenter are guarded by the version-specific
        // branch in hookLocalSessionDeletedFlow instead.
        if (HostCompat.isV241()) return;
        try {
            Class<?> handler = HostCompat.load(cl, "at0");
            Class<?> resultType = HostCompat.load(cl, "op5");
            Class<?> ownerType = HostCompat.load(cl, "yg3");
            int installed = 0;
            for (Method method : handler.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!"a".equals(method.getName()) || types.length != 3
                        || types[0] != resultType || types[1] != boolean.class
                        || types[2] != ownerType || method.getReturnType() != void.class) continue;
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        try {
                            Object[] args = chain.getArgs().toArray();
                            Object status = Main.readHostField(args[0], "a");
                            Object code = Main.readHostField(status, "a");
                            if (code instanceof Number && ((Number) code).intValue() == 1) {
                            Object viewModel = Main.readHostField(args[2], "b");
                            Object session = Main.invokeNoArg(viewModel, "G");
                            Object id = Main.readHostField(session, "a");
                            String sid = id == null ? null : String.valueOf(id);
                            HashSet<String> localIds = ChatEditorUi.localSessionIdsFromAllBackups();
                            String pending = PENDING_LOCAL_OPEN_SID;
                            // The selected editor-local conversation remains authoritative until
                            // another sidebar row is selected. Do not expire this guard after 30s:
                            // feedback is commonly sent after reading a long answer, and its
                            // code-1 response otherwise reaches DeepSeek's "conversation deleted"
                            // branch once the old timeout elapses.
                            boolean pendingFresh = pending != null && localIds.contains(pending);
                            boolean directLocal = sid != null && localIds.contains(sid);
                            Main.log("observed server-deleted result currentSid=" + sid
                                    + " pendingLocal=" + pending + " localIds=" + localIds.size()
                                    + " direct=" + directLocal + " pendingFresh=" + pendingFresh);
                            if (directLocal || pendingFresh) {
                                Main.log("suppressed server-deleted result for editor-local sid="
                                        + (directLocal ? sid : pending));
                                return null;
                            }
                            }
                        } catch (Throwable t) {
                            Main.log("inspect local session deleted result failed: " + t);
                        }
                        return chain.proceed();
                    }
                });
                installed++;
            }
            Main.log("installed editor-local deleted-response guard at0.a x" + installed);
        } catch (Throwable t) {
            Main.log("hookLocalSessionDeletedResponse failed: " + t);
        }
    }

    /**
     * Real UI traffic reaches the deletion branch through za1.N(). ART may inline the tiny at0.a
     * helper into that caller, so hooking at0 alone is insufficient even though reflective probes
     * hit it. Stop the exact code-1 event at the ViewModel boundary before it can show the toast or
     * replace the selected conversation with a new empty session.
     */
    void hookLocalSessionDeletedFlow(final ClassLoader cl) {
        if (HostCompat.isV234()) {
            hookV234LocalSessionDeletedFlow(cl);
            hookV234LocalSessionDeletionPresenter(cl);
            return;
        }
        try {
            Class<?> viewModelType = HostCompat.load(cl, "za1");
            Class<?> eventType = HostCompat.load(cl, "bu0");
            Class<?> optionType = HostCompat.load(cl, "zs0");
            Class<?> envelopeType = HostCompat.load(cl, "au0");
            Class<?> errorType = HostCompat.load(cl, "op5");
            int installed = 0;
            for (Method method : viewModelType.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!HostCompat.chatViewModelMethod("N").equals(method.getName())
                        || types.length != 2
                        || types[0] != eventType || types[1] != optionType
                        || method.getReturnType() != void.class) continue;
                try { Main.log("deopt za1.N ok=" + Main.MODULE.deoptimize(method)); }
                catch (Throwable t) { Main.log("deopt za1.N failed: " + t); }
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        try {
                            Object event = chain.getArg(0);
                            if (envelopeType.isInstance(event)) {
                                Object error = Main.readHostField(event, "a");
                                if (errorType.isInstance(error)) {
                                    Object status = Main.readHostField(error, "a");
                                    Object code = Main.readHostField(status, "a");
                                    if (code instanceof Number
                                            && ((Number) code).intValue() == 1) {
                                        Object session = Main.invokeNoArg(
                                                chain.getThisObject(),
                                                HostCompat.chatViewModelMethod("G"));
                                        Object id = Main.readHostField(session, "a");
                                        String sid = id == null ? null : String.valueOf(id);
                                        HashSet<String> localIds =
                                                ChatEditorUi.localSessionIdsFromAllBackups();
                                        String pending = PENDING_LOCAL_OPEN_SID;
                                        boolean pendingFresh = pending != null
                                                && localIds.contains(pending);
                                        boolean directLocal = sid != null
                                                && localIds.contains(sid);
                                        Main.log("observed ViewModel deleted event currentSid=" + sid
                                                + " pendingLocal=" + pending
                                                + " direct=" + directLocal
                                                + " pendingFresh=" + pendingFresh);
                                        if (directLocal || pendingFresh) {
                                            Main.log("suppressed ViewModel deleted event for "
                                                    + "editor-local sid="
                                                    + (directLocal ? sid : pending));
                                            return null;
                                        }
                                    }
                                }
                            }
                        } catch (Throwable t) {
                            Main.log("inspect ViewModel deleted event failed: " + t);
                        }
                        return chain.proceed();
                    }
                });
                installed++;
            }
            Main.log("installed editor-local ViewModel deletion guard za1.N x" + installed);
        } catch (Throwable t) {
            Main.log("hookLocalSessionDeletedFlow failed: " + t);
        }
    }

    /**
     * 2.3.4 replaced the old au0(op5) deletion event with a result envelope consumed by
     * ChatSessionComponent.M. Both store channels keep the same structural path:
     * result.a -> error.a -> integer code. Hook that stable shape instead of channel-specific
     * obfuscated names so local editor conversations cannot be mistaken for cloud deletions.
     */
    private void hookV234LocalSessionDeletedFlow(final ClassLoader cl) {
        try {
            Class<?> viewModelType = HostCompat.load(cl, "za1");
            int installed = 0;
            for (Method method : viewModelType.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!HostCompat.chatViewModelMethod("M").equals(method.getName())
                        || types.length != 2
                        || method.getReturnType() != void.class) continue;
                try { Main.MODULE.deoptimize(method); } catch (Throwable ignored) {}
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        try {
                            Integer code = v234CompletionErrorCode(chain.getArg(0));
                            if (code != null && code.intValue() == 1) {
                                Object session = Main.invokeNoArg(chain.getThisObject(),
                                        HostCompat.chatViewModelMethod("G"));
                                Object id = Main.readHostField(session, "a");
                                String sid = id == null ? null : String.valueOf(id);
                                HashSet<String> localIds =
                                        ChatEditorUi.localSessionIdsFromAllBackups();
                                String pending = PENDING_LOCAL_OPEN_SID;
                                boolean pendingFresh = pending != null
                                        && localIds.contains(pending);
                                boolean directLocal = sid != null && localIds.contains(sid);
                                Main.log("observed 2.3.4 deletion result currentSid=" + sid
                                        + " pendingLocal=" + pending + " direct="
                                        + directLocal + " pendingFresh=" + pendingFresh);
                                if (directLocal || pendingFresh) {
                                    Main.log("suppressed 2.3.4 server-deleted result for editor-local sid="
                                            + (directLocal ? sid : pending));
                                    return null;
                                }
                            }
                        } catch (Throwable t) {
                            Main.log("inspect 2.3.4 local deletion result failed: " + t);
                        }
                        return chain.proceed();
                    }
                });
                installed++;
            }
            Main.log("installed 2.3.4 editor-local deletion guard "
                    + viewModelType.getSimpleName() + ".M x" + installed);
        } catch (Throwable t) {
            Main.log("hookV234LocalSessionDeletedFlow failed: " + t);
        }
    }

    /**
     * ChatSessionComponent.M delegates code-1 failures to a tiny presenter (ju0 on mainland,
     * cw0 on GP). R8 may inline or bypass M depending on the installed profile, so guarding M
     * alone is not a reliable deletion boundary. The presenter is the last common point before
     * both the "conversation deleted" toast and tg3.g(sessionId) removal are executed.
     */
    private void hookV234LocalSessionDeletionPresenter(final ClassLoader cl) {
        try {
            Class<?> presenter = Class.forName(HostCompat.isV241()
                    ? "wv0" : HostCompat.isGooglePlay() ? "cw0" : "ju0", false, cl);
            int installed = 0;
            for (Method method : presenter.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!"a".equals(method.getName()) || types.length != 3
                        || types[1] != boolean.class || method.getReturnType() != void.class) {
                    continue;
                }
                try { Main.MODULE.deoptimize(method); } catch (Throwable ignored) {}
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        try {
                            Object errorEnvelope = chain.getArg(0);
                            Object status = Main.readHostField(errorEnvelope, "a");
                            Object code = Main.readHostField(status, "a");
                            if (code instanceof Number && ((Number) code).intValue() == 1) {
                                Object ownerBox = chain.getArg(2);
                                Object viewModel = Main.readHostField(ownerBox, "a");
                                Object session = Main.invokeNoArg(viewModel,
                                        HostCompat.chatViewModelMethod("G"));
                                Object id = Main.readHostField(session, "a");
                                String sid = id == null ? null : String.valueOf(id);
                                HashSet<String> localIds =
                                        ChatEditorUi.localSessionIdsFromAllBackups();
                                String pending = PENDING_LOCAL_OPEN_SID;
                                boolean directLocal = sid != null && localIds.contains(sid);
                                boolean pendingLocal = pending != null
                                        && localIds.contains(pending);
                                Main.log("observed 2.3.4 deletion presenter currentSid=" + sid
                                        + " pendingLocal=" + pending + " direct="
                                        + directLocal + " pending=" + pendingLocal);
                                if (directLocal || pendingLocal) {
                                    Main.log("suppressed final 2.3.4 deletion presenter for editor-local"
                                            + " sid=" + (directLocal ? sid : pending));
                                    return null;
                                }
                            }
                        } catch (Throwable t) {
                            Main.log("inspect 2.3.4 deletion presenter failed: " + t);
                        }
                        return chain.proceed();
                    }
                });
                installed++;
            }
            Main.log("installed 2.3.4 deletion presenter guard "
                    + presenter.getSimpleName() + ".a x" + installed);
        } catch (Throwable t) {
            Main.log("hookV234LocalSessionDeletionPresenter failed: " + t);
        }
    }

    /**
     * code249 has a second code-1 deletion path in the feedback component (p41.a). It is separate
     * from ChatSessionComponent.M/ju0 and therefore survived the earlier guard: a thumbs-up/down
     * request on an editor-owned conversation could still show "chat deleted" and evict the row.
     * Stop only that exact error for a durable editor sidecar; normal feedback and server chats
     * keep their host behaviour.
     */
    void hookV236FeedbackDeletedFlow(final ClassLoader cl) {
        if (!HostCompat.isV236()) return;
        try {
            Class<?> component = Class.forName("p41", false, cl);
            Class<?> sessionType = Class.forName("lq", false, cl);
            Class<?> feedbackType = Class.forName("gi5", false, cl);
            Class<?> resultType = Class.forName("vy5", false, cl);
            int installed = 0;
            for (Method method : component.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!"a".equals(method.getName())
                        || !Modifier.isStatic(method.getModifiers())
                        || method.getReturnType() != void.class
                        || types.length != 4 || types[0] != component
                        || types[1] != sessionType || types[2] != feedbackType
                        || types[3] != resultType) continue;
                try { Main.MODULE.deoptimize(method); } catch (Throwable ignored) {}
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        try {
                            Object session = chain.getArg(1);
                            Object result = chain.getArg(3);
                            Object codeHolder = Main.readHostField(result, "a");
                            Object code = Main.readHostField(codeHolder, "a");
                            Object id = Main.readHostField(session, "a");
                            String sid = id == null ? null : String.valueOf(id);
                            if (code instanceof Number
                                    && ((Number) code).intValue() == 1
                                    && sid != null
                                    && ChatEditorUi.localSessionIdsFromAllBackups()
                                            .contains(sid)) {
                                Main.log("suppressed code249 feedback-deleted result for editor-local"
                                        + " sid=" + sid);
                                return null;
                            }
                        } catch (Throwable error) {
                            Main.log("inspect code249 feedback deletion failed: " + error);
                        }
                        return chain.proceed();
                    }
                });
                installed++;
            }
            Main.log("installed code249 feedback deletion guard p41.a x" + installed);
        } catch (Throwable error) {
            Main.log("hookV236FeedbackDeletedFlow failed: " + error);
        }
    }

    private static Integer v234CompletionErrorCode(Object result) {
        if (result == null) return null;
        Object envelope = Main.readHostField(result, "a");
        if (envelope == null) return null;
        Object error = Main.readHostField(envelope, "a");
        if (error == null) return null;
        Object code = Main.readHostField(error, "a");
        return code instanceof Number ? Integer.valueOf(((Number) code).intValue()) : null;
    }

    void hookNativeSessionNavigator(final ClassLoader cl) {
        try {
            // code257 moved the directory composable from code249 sc.l to gi0.c.
            // This is deliberately local because the row renderer moved elsewhere.
            Class<?> mc = HostCompat.isV241()
                    ? cl.loadClass("gi0") : HostCompat.load(cl, "mc");
            Class<?> ib3 = HostCompat.isV241()
                    ? cl.loadClass("tl3") : HostCompat.load(cl, "ib3");
            int installed = 0;
            for (Method method : mc.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                final int clickIndex = HostCompat.isV241()
                        ? 6 : HostCompat.isV234() ? 5 : 4;
                final int eventIndex = HostCompat.isV241()
                        ? 7 : HostCompat.isV234() ? 6 : 5;
                int expectedLength = HostCompat.isV241()
                        ? 15 : HostCompat.isV234() ? 14 : 13;
                String navigatorMethod = HostCompat.isV241()
                        ? "c" : HostCompat.method("mc", "f");
                if (!navigatorMethod.equals(method.getName())
                        || types.length != expectedLength) continue;
                if (!List.class.isAssignableFrom(types[0])
                        || !ib3.isAssignableFrom(types[clickIndex])
                        || !ib3.isAssignableFrom(types[eventIndex])) continue;
                final Class<?> sessionListType = types[0];
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object[] replacement = null;
                        try {
                            Object[] args = chain.getArgs().toArray();
                            if (args[0] instanceof List && args[clickIndex] != null) {
                                hookNativeSessionClickCallback(args[clickIndex], cl);
                                List source = (List) args[0];
                                List visible = null;
                                for (Object session : new ArrayList(source)) {
                                    String id = String.valueOf(Main.readHostField(session, "a"));
                                    if (Main.isLocalApiInternalSession(id)) {
                                        if (visible == null) {
                                            visible = copyListForHook(source, sessionListType);
                                            if (visible == null) {
                                                Main.log("cannot preserve concrete session-list type "
                                                        + sessionListType.getName()
                                                        + "; keeping the host list unchanged");
                                                break;
                                            }
                                        }
                                        visible.remove(session);
                                    }
                                }
                                if (visible != null) {
                                    source = visible;
                                    args[0] = source;
                                    replacement = args;
                                }
                                int serverSize = source.size();
                                HashSet<String> localIds = localOnlySessionIds(cl);
                                HashSet<String> seen = new HashSet<>();
                                ArrayList<Object> missingLocalRows = new ArrayList<>();
                                synchronized (LOCAL_NATIVE_SESSIONS) {
                                    LOCAL_NATIVE_SESSIONS.keySet().retainAll(localIds);
                                    for (Object session : new ArrayList(source)) {
                                        String sid = String.valueOf(Main.readHostField(session, "a"));
                                        if (sid == null || sid.length() == 0 || "null".equals(sid)) continue;
                                        seen.add(sid);
                                        if (localIds.contains(sid)) {
                                            LOCAL_NATIVE_SESSIONS.put(sid, session);
                                        }
                                    }
                                    for (String sid : localIds) {
                                        if (seen.contains(sid)) continue;
                                        Object localSession = LOCAL_NATIVE_SESSIONS.get(sid);
                                        if (localSession != null) {
                                            missingLocalRows.add(localSession);
                                            seen.add(sid);
                                        }
                                    }
                                }
                                List merged = source;
                                if (!missingLocalRows.isEmpty()) {
                                    List mergedCopy = copyListForHook(
                                            source, sessionListType);
                                    if (mergedCopy != null) {
                                        merged = mergedCopy;
                                        merged.addAll(missingLocalRows);
                                    }
                                }
                                if (merged.size() != serverSize) {
                                    // Appending durable local rows without sorting placed them at
                                    // the very bottom of long cloud histories, which looked like
                                    // they had not been created at all.
                                    sortNativeSessionDirectory(merged);
                                    if (merged != source) args[0] = merged;
                                    long now = System.currentTimeMillis();
                                    if (now - LOCAL_NATIVE_MERGE_LOG_AT > 5000L) {
                                        LOCAL_NATIVE_MERGE_LOG_AT = now;
                                        Main.log("preserved local native sessions="
                                                + (merged.size() - serverSize)
                                                + " server sessions=" + serverSize);
                                    }
                                }
                                NATIVE_SESSION_LIST = args[0];
                                NATIVE_SESSION_CLICK = args[clickIndex];
                                NATIVE_SESSION_EVENTS = args[eventIndex];
                                if (args[0] != source) replacement = args;
                            }
                        } catch (Throwable t) { Main.log("capture native session navigator failed: " + t); }
                        return replacement == null ? chain.proceed() : chain.proceed(replacement);
                    }
                });
                installed++;
            }
            Main.log("installed native session navigator hook mc.f x" + installed);
        } catch (Throwable t) { Main.log("hookNativeSessionNavigator failed: " + t); }
    }

    /**
     * Copies a host list without erasing a concrete parameter type such as Compose's
     * SnapshotStateList. Returning {@code null} tells the caller to fail open with the original
     * list when that host version offers no safe no-argument copy path.
     */
    static List copyListForHook(List source, Class<?> parameterType) {
        if (source == null || parameterType == null) return null;
        try {
            Constructor<?> constructor = source.getClass().getDeclaredConstructor();
            constructor.setAccessible(true);
            Object value = constructor.newInstance();
            if (value instanceof List && parameterType.isInstance(value)) {
                List copy = (List) value;
                copy.addAll(source);
                return copy;
            }
        } catch (Throwable ignored) {}
        if (parameterType.isAssignableFrom(ArrayList.class)) {
            return new ArrayList(source);
        }
        return null;
    }

    private void hookNativeSessionClickCallback(Object callback, final ClassLoader cl) {
        if (callback == null) return;
        Class<?> callbackClass = callback.getClass();
        synchronized (NATIVE_CLICK_HOOKED_CLASSES) {
            if (!NATIVE_CLICK_HOOKED_CLASSES.add(callbackClass)) return;
        }
        int installed = 0;
        try {
            for (Class<?> type = callbackClass; type != null; type = type.getSuperclass()) {
                for (Method method : type.getDeclaredMethods()) {
                    if (!"g".equals(method.getName())
                            || method.getParameterTypes().length != 1) continue;
                    Main.MODULE.hook(method).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            if (chain.getThisObject() != NATIVE_SESSION_CLICK) {
                                return chain.proceed();
                            }
                            Object session = chain.getArg(0);
                            if (session == null
                                    || !HostCompat.simpleNameIs(session, "tp")) {
                                return chain.proceed();
                            }
                            try {
                                Object id = Main.readHostField(session, "a");
                                String sid = id == null ? null : String.valueOf(id);
                                if (sid != null && sid.length() > 0 && !"null".equals(sid)) {
                                    HashSet<String> locals =
                                            ChatEditorUi.localSessionIdsFromAllBackups();
                                    if (Main.FROZEN_SESSION_HEADS.containsKey(sid)
                                            && (HostCompat.isV234() || !HostCompat.isV230())) {
                                        // 2.3.0 abandoned the wg1/pe case-7 mapper used here;
                                        // 2.3.4 restored an equivalent mapped loader. Skipping all
                                        // isV230-family builds accidentally disabled hydration on
                                        // 2.3.4 and made edited/local chats open as empty/deleted.
                                        hydrateFrozenNativeSession(cl, session, sid);
                                    }
                                    Object messages = Main.readHostField(session, "f");
                                    Object transactions = Main.readHostField(session, "q");
                                    Object messageState = Main.readHostField(session, "j");
                                    Object stateValue = messageState == null ? null
                                            : Main.invokeNoArg(messageState, "getValue");
                                    Object stateRows = Main.readHostField(stateValue, "a");
                                    Main.log("native click state sid=" + sid
                                            + " messages=" + (messages instanceof Map
                                            ? ((Map) messages).size() : -1)
                                            + " transactions=" + (transactions instanceof Map
                                            ? ((Map) transactions).size() : -1)
                                            + " head=" + nativeSessionHead(session)
                                            + " n=" + Main.readHostField(session, "n")
                                            + " o=" + Main.readHostField(session, "o")
                                            + " state=" + (stateValue == null ? "null"
                                            : stateValue.getClass().getName())
                                            + " rows=" + (stateRows instanceof List
                                            ? ((List) stateRows).size() : -1));
                                    if (locals.contains(sid)) {
                                        PENDING_LOCAL_OPEN_SID = sid;
                                        Main.log("native click selected editor-local sid=" + sid);
                                    } else {
                                        PENDING_LOCAL_OPEN_SID = null;
                                        Main.log("native click selected server sid=" + sid);
                                    }
                                }
                            } catch (Throwable t) {
                                Main.log("inspect native session click failed: " + t);
                            }
                            return chain.proceed();
                        }
                    });
                    installed++;
                }
            }
            Main.log("installed native session click callback hooks=" + installed
                    + " class=" + callbackClass.getName());
        } catch (Throwable t) {
            Main.log("hook native session click callback failed: " + t);
        }
    }

    /**
     * Reuses DeepSeek's own gm8 -> sl8 -> kv pipeline to materialise an editor-frozen WCDB table
     * into the exact tp object selected by the sidebar.  This avoids both Android-SQLite/WCDB
     * cross-engine reads and hand-built host message objects.
     */
    static boolean hydrateFrozenNativeSession(ClassLoader cl, Object session, String sid) {
        if (session == null || sid == null) return false;
        try {
            Object messages = Main.readHostField(session, "f");
            Object head = nativeSessionHead(session);
            // Do not trust a populated host cache for editor-frozen sessions. A later server
            // append can replace edited fragments under the same message IDs; remap WCDB so the
            // sidecar-backed local display remains authoritative.
            Object repository = HookAttachmentPipeline.liveFm8;
            Integer localHead = Main.FROZEN_SESSION_HEADS.get(sid);
            if (repository == null || localHead == null) return false;

            if (HostCompat.isV241()) {
                return hydrateV241FrozenNativeSession(
                        cl, repository, session, sid, localHead);
            }
            if (HostCompat.isV236()) {
                // Never call fh.h(lq, i42) here. In code249 that method is the native DELETE
                // coroutine (its later legs remove the lq from the directory and delete WCDB),
                // not a history loader. The correct code249 WCDB reader is resolved below from
                // its structural contract before any host method is invoked.
                return hydrateV236FrozenNativeSession(cl, repository, session, sid, localHead);
            }

            Class<?> continuation = HostCompat.load(cl, "uz1");
            Class<?> unitType = HostCompat.load(cl, "ui8");
            Field unitField = unitType.getDeclaredField("a");
            unitField.setAccessible(true);
            Object unit = unitField.get(null);

            Class<?> loaderType = HostCompat.load(cl, "ve1");
            Constructor<?> loaderCtor = loaderType.getDeclaredConstructor(
                    HostCompat.load(cl, "gm8"), String.class, continuation, int.class);
            loaderCtor.setAccessible(true);
            Object loader = loaderCtor.newInstance(repository, sid, null, 0);
            Method executeLoader = loaderType.getDeclaredMethod(
                    HostCompat.isV234() ? "x" : "y", Object.class);
            executeLoader.setAccessible(true);
            Object rows = executeLoader.invoke(loader, unit);
            if (!(rows instanceof List) || ((List) rows).isEmpty()) {
                Main.log("frozen native hydration found no WCDB rows sid=" + sid);
                return false;
            }

            Class<?> mapperType = HostCompat.load(cl, "ie");
            Constructor<?> mapperCtor = null;
            for (Constructor<?> ctor : mapperType.getDeclaredConstructors()) {
                Class<?>[] types = ctor.getParameterTypes();
                if (types.length == 5 && types[4] == int.class) {
                    mapperCtor = ctor;
                    break;
                }
            }
            if (mapperCtor == null) throw new NoSuchMethodException("ie case-7 constructor");
            mapperCtor.setAccessible(true);
            Object mapper = mapperCtor.newInstance(session, rows, localHead, null,
                    HostCompat.isV234() ? 8 : 7);
            Method executeMapper = mapperType.getDeclaredMethod(
                    HostCompat.isV234() ? "x" : "y", Object.class);
            executeMapper.setAccessible(true);
            executeMapper.invoke(mapper, unit);

            Object after = Main.readHostField(session, "f");
            Object afterHead = nativeSessionHead(session);
            boolean hydrated = after instanceof Map && ((Map) after).size() > 1
                    && afterHead != null;
            Main.log("frozen native hydration sid=" + sid + " rows=" + ((List) rows).size()
                    + " messages=" + (after instanceof Map ? ((Map) after).size() : -1)
                    + " head=" + afterHead + " ok=" + hydrated);
            return hydrated;
        } catch (Throwable t) {
            Throwable cause = t instanceof java.lang.reflect.InvocationTargetException
                    && ((java.lang.reflect.InvocationTargetException) t).getCause() != null
                    ? ((java.lang.reflect.InvocationTargetException) t).getCause() : t;
            Main.log("frozen native hydration failed sid=" + sid + ": " + cause);
            return false;
        }
    }

    private static boolean isFrozenNativeSessionHydrated(Object session) {
        Object messages = Main.readHostField(session, "f");
        return messages instanceof Map && ((Map) messages).size() > 1
                && nativeSessionHead(session) != null;
    }

    private static boolean hydrateV241FrozenNativeSession(
            ClassLoader cl, Object repository, Object session, String sid, Integer localHead) {
        if (cl == null || repository == null || session == null
                || sid == null || localHead == null) return false;
        try {
            Class<?> continuation = Class.forName("r72", false, cl);
            Class<?> unitType = Class.forName("d39", false, cl);
            Field unitField = unitType.getDeclaredField("a");
            unitField.setAccessible(true);
            Object unit = unitField.get(null);

            // code257 bm1(case 0) is the exact successor of code249 pi1(case 0): both select
            // every WCDB row for chat_session_messages_<sid> in message-id order.
            Class<?> readerType = Class.forName("bm1", false, cl);
            Constructor<?> readerCtor = readerType.getDeclaredConstructor(
                    repository.getClass(), String.class, continuation, int.class);
            readerCtor.setAccessible(true);
            Object reader = readerCtor.newInstance(repository, sid, null, 0);
            Method executeReader = readerType.getDeclaredMethod("y", Object.class);
            executeReader.setAccessible(true);
            Object rows = executeReader.invoke(reader, unit);
            if (!(rows instanceof List) || ((List) rows).isEmpty()) {
                rows = buildV241MessageRowsFromSidecar(cl, sid);
                Main.log("2.4.1 WCDB rows unavailable; sidecar rows sid=" + sid + " rows="
                        + (rows instanceof List ? ((List) rows).size() : -1));
                if (!(rows instanceof List) || ((List) rows).isEmpty()) return false;
            }

            // code257 te(case 8) independently replaces code249 pe(case 7). It maps those rows
            // into this pq instance and restores the durable current_message_id.
            Class<?> mapperType = Class.forName("te", false, cl);
            Constructor<?> mapperCtor = mapperType.getDeclaredConstructor(
                    Object.class, Object.class, Object.class, continuation, int.class);
            mapperCtor.setAccessible(true);
            Object mapper = mapperCtor.newInstance(
                    session, rows, localHead, null, 8);
            Method executeMapper = mapperType.getDeclaredMethod("y", Object.class);
            executeMapper.setAccessible(true);
            executeMapper.invoke(mapper, unit);

            Object after = Main.readHostField(session, "f");
            Object afterHead = nativeSessionHead(session);
            boolean hydrated = after instanceof Map && ((Map) after).size() > 1
                    && afterHead != null;
            Main.log("2.4.1 frozen native hydration sid=" + sid
                    + " rows=" + ((List) rows).size()
                    + " messages=" + (after instanceof Map ? ((Map) after).size() : -1)
                    + " head=" + afterHead + " ok=" + hydrated);
            return hydrated;
        } catch (Throwable error) {
            Throwable cause = error instanceof java.lang.reflect.InvocationTargetException
                    && ((java.lang.reflect.InvocationTargetException) error).getCause() != null
                    ? ((java.lang.reflect.InvocationTargetException) error).getCause() : error;
            Main.log("2.4.1 frozen hydration failed sid=" + sid + ": " + cause);
            return false;
        }
    }

    /** Builds exact code257 b69 ORM rows for te(case 8), without touching older hosts. */
    private static List<Object> buildV241MessageRowsFromSidecar(ClassLoader cl, String sid) {
        ArrayList<Object> out = new ArrayList<>();
        if (!HostCompat.isV241() || cl == null || sid == null) return out;
        try {
            List<Object[]> rawRows = ChatEditorUi.localSessionNativeMessageRows(sid);
            if (rawRows.isEmpty()) return out;
            Class<?> type = Class.forName("b69", false, cl);
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            String[] fields = {"a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m"};
            for (Object[] raw : rawRows) {
                if (raw == null || raw.length != fields.length) continue;
                Object row = constructor.newInstance();
                Main.forceSetObjectField(row, fields[0], Integer.valueOf(((Number) raw[0]).intValue()));
                Main.forceSetObjectField(row, fields[1], raw[1] == null ? null
                        : Integer.valueOf(((Number) raw[1]).intValue()));
                Main.forceSetObjectField(row, fields[2], raw[2] == null ? null : String.valueOf(raw[2]));
                Main.forceSetObjectField(row, fields[3], raw[3] == null ? null
                        : Boolean.valueOf(((Number) raw[3]).intValue() != 0));
                Main.forceSetObjectField(row, fields[4], raw[4] == null ? "" : String.valueOf(raw[4]));
                Main.forceSetObjectField(row, fields[5], Double.valueOf(raw[5] instanceof Number
                        ? ((Number) raw[5]).doubleValue() : 0d));
                Main.forceSetObjectField(row, fields[6], raw[6] == null ? null : String.valueOf(raw[6]));
                Main.forceSetObjectField(row, fields[7], Integer.valueOf(raw[7] instanceof Number
                        ? ((Number) raw[7]).intValue() : 0));
                Main.forceSetObjectField(row, fields[8], Boolean.valueOf(raw[8] instanceof Number
                        && ((Number) raw[8]).intValue() != 0));
                Main.forceSetObjectField(row, fields[9], Boolean.valueOf(raw[9] instanceof Number
                        && ((Number) raw[9]).intValue() != 0));
                Main.forceSetObjectField(row, fields[10], raw[10] == null ? null : String.valueOf(raw[10]));
                Main.forceSetObjectField(row, fields[11], raw[11] == null ? null : String.valueOf(raw[11]));
                Main.forceSetObjectField(row, fields[12], raw[12] == null ? null : String.valueOf(raw[12]));
                out.add(row);
            }
        } catch (Throwable error) {
            Main.log("build code257 message rows from sidecar failed sid=" + sid + ": " + error);
        }
        return out;
    }

    private static boolean hydrateV236FrozenNativeSession(
            ClassLoader cl, Object repository, Object session, String sid, Integer localHead) {
        if (cl == null || repository == null || session == null
                || sid == null || localHead == null) return false;
        try {
            Class<?> continuation = HostCompat.load(cl, "uz1");
            Class<?> unitType = HostCompat.load(cl, "ui8");
            Field unitField = unitType.getDeclaredField("a");
            unitField.setAccessible(true);
            Object unit = unitField.get(null);

            // code249's former gi1 reader is pi1(case 0): it selects every WCDB row from
            // chat_session_messages_<sid>, ordered by message id. Calling its suspend-lambda body
            // directly is safe here because this case performs a synchronous WCDB select only.
            Class<?> readerType = Class.forName("pi1", false, cl);
            Constructor<?> readerCtor = readerType.getDeclaredConstructor(
                    repository.getClass(), String.class, continuation, int.class);
            readerCtor.setAccessible(true);
            Object reader = readerCtor.newInstance(repository, sid, null, 0);
            Method executeReader = readerType.getDeclaredMethod("x", Object.class);
            executeReader.setAccessible(true);
            Object rows = executeReader.invoke(reader, unit);
            if (!(rows instanceof List) || ((List) rows).isEmpty()) {
                Main.log("2.3.6 frozen hydration found no WCDB rows sid=" + sid);
                return false;
            }

            // pe(case 7) is the matching code249 main-state reducer. It materialises the selected
            // rows into this exact lq instance and applies the durable current_message_id.
            Class<?> mapperType = Class.forName("pe", false, cl);
            Constructor<?> mapperCtor = mapperType.getDeclaredConstructor(
                    Object.class, Object.class, Object.class, continuation, int.class);
            mapperCtor.setAccessible(true);
            Object mapper = mapperCtor.newInstance(
                    session, rows, localHead, null, 7);
            Method executeMapper = mapperType.getDeclaredMethod("x", Object.class);
            executeMapper.setAccessible(true);
            executeMapper.invoke(mapper, unit);

            Object after = Main.readHostField(session, "f");
            Object afterHead = nativeSessionHead(session);
            boolean hydrated = after instanceof Map && ((Map) after).size() > 1
                    && afterHead != null;
            Main.log("2.3.6 frozen native hydration sid=" + sid
                    + " rows=" + ((List) rows).size()
                    + " messages=" + (after instanceof Map ? ((Map) after).size() : -1)
                    + " head=" + afterHead + " ok=" + hydrated);
            return hydrated;
        } catch (Throwable error) {
            Throwable cause = error instanceof java.lang.reflect.InvocationTargetException
                    && ((java.lang.reflect.InvocationTargetException) error).getCause() != null
                    ? ((java.lang.reflect.InvocationTargetException) error).getCause() : error;
            Main.log("2.3.6 frozen hydration failed sid=" + sid + ": " + cause);
            return false;
        }
    }

    void hookHistoryLoadDiagnostics(final ClassLoader cl) {
        try {
            final boolean v241 = HostCompat.isV241();
            Class<?> rawLoader = v241
                    ? Class.forName("bm1", false, cl) : HostCompat.load(cl, "ve1");
            int rawHooks = 0;
            for (Method method : rawLoader.getDeclaredMethods()) {
                if (!"y".equals(method.getName())
                        || method.getParameterTypes().length != 1) continue;
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object result = chain.proceed();
                        try {
                            Object kind = Main.readHostField(chain.getThisObject(), "e");
                            Object sid = Main.readHostField(chain.getThisObject(), "g");
                            if (kind instanceof Number && ((Number) kind).intValue() == 0) {
                                // code257 loads persisted rows through bm1 -> te directly on
                                // cold start. Restore the narrow, locally-preserved filter
                                // replacement before te materialises its native message map;
                                // the pw0 online-history path is not reached for this route.
                                int restored = HostCompat.isV241()
                                        ? ResponsePreserver.restoreRepositoryRows(cl,
                                        String.valueOf(sid), result) : 0;
                                if (restored > 0) {
                                    Main.log("restored preserved responses in raw WCDB load sid="
                                            + sid + " rows=" + restored);
                                }
                                Main.log("WCDB raw message load sid=" + sid + " rows="
                                        + (result instanceof List ? ((List) result).size() : -1)
                                        + " result=" + (result == null ? "null"
                                        : result.getClass().getName()));
                            }
                        } catch (Throwable t) {
                            Main.log("inspect WCDB raw load failed: " + t);
                        }
                        return result;
                    }
                });
                rawHooks++;
            }

            Class<?> mapper = v241
                    ? Class.forName("te", false, cl) : HostCompat.load(cl, "ie");
            int mapperHooks = 0;
            for (Method method : mapper.getDeclaredMethods()) {
                if (!"y".equals(method.getName())
                        || method.getParameterTypes().length != 1) continue;
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object self = chain.getThisObject();
                        Object kind = Main.readHostField(self, "e");
                        int expectedKind = v241 ? 8 : 7;
                        if (!(kind instanceof Number)
                                || ((Number) kind).intValue() != expectedKind) {
                            return chain.proceed();
                        }
                        Object session = Main.readHostField(self, "f");
                        Object rows = Main.readHostField(self, "g");
                        Object messages = Main.readHostField(session, "f");
                        String sid = String.valueOf(Main.readHostField(session, "a"));
                        Main.log("native message map begin sid=" + sid + " rows="
                                + (rows instanceof List ? ((List) rows).size() : -1)
                                + " cache=" + (messages instanceof Map
                                ? ((Map) messages).size() : -1));
                        try {
                            Object result = chain.proceed();
                            Object after = Main.readHostField(session, "f");
                            Main.log("native message map end sid=" + sid + " cache="
                                    + (after instanceof Map ? ((Map) after).size() : -1));
                            return result;
                        } catch (Throwable t) {
                            Main.log("native message map failed sid=" + sid + " error=" + t);
                            throw t;
                        }
                    }
                });
                mapperHooks++;
            }
            Main.log("installed history-load diagnostics ve1=" + rawHooks
                    + " ie=" + mapperHooks);
        } catch (Throwable t) {
            Main.log("hook history-load diagnostics failed: " + t);
        }
    }

    /**
     * The sidebar and the chat ViewModel can hold distinct tp instances for the same session ID.
     * Capture za1.G() so a proactive response updates the instance actually observed by the open
     * conversation instead of waiting for process recreation to reload WCDB.
     */
    void hookActiveChatSessionCapture(final ClassLoader cl) {
        try {
            Class<?> viewModel = HostCompat.load(cl, "za1");
            Class<?> sessionType = HostCompat.load(cl, "tp");
            int installed = 0;
            for (Method method : viewModel.getDeclaredMethods()) {
                if (!HostCompat.chatViewModelMethod("G").equals(method.getName())
                        || method.getParameterTypes().length != 0
                        || method.getReturnType() != sessionType) continue;
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object session = chain.proceed();
                        try {
                            String sid = String.valueOf(Main.readHostField(session, "a"));
                            if (isUsableSessionId(sid)) {
                                ACTIVE_CHAT_SESSIONS.put(
                                        sid, new WeakReference<Object>(session));
                                Object owner = chain.getThisObject();
                                WeakReference<Object> previousViewModel =
                                        ACTIVE_CHAT_VIEW_MODELS.put(
                                                sid, new WeakReference<Object>(owner));
                                if (previousViewModel == null
                                        || previousViewModel.get() != owner) {
                                    recoverAgentRunsForScope(
                                            currentHostContext(), sid);
                                }
                                NativeHeartbeatHistory pending =
                                        PENDING_NATIVE_HEARTBEAT_HISTORIES.get(sid);
                                if (pending != null
                                        && mergeNativeHeartbeatHistoryIntoSession(
                                                pending, session)) {
                                    PENDING_NATIVE_HEARTBEAT_HISTORIES.remove(
                                            sid, pending);
                                    Main.log("proactive history applied to active ViewModel sid="
                                            + sid + " head=" + pending.head);
                                }
                            }
                        } catch (Throwable error) {
                            Main.log("active chat session capture failed: "
                                    + Main.safeThrowableMessage(error));
                        }
                        return session;
                    }
                });
                installed++;
            }
            Main.log("installed active chat session capture za1.G x" + installed);
        } catch (Throwable error) {
            Main.log("hook active chat session capture failed: " + error);
        }
    }

    void hookComposeVisibleThreadState(final ClassLoader cl) {
        if (!HostCompat.isV230() || HostCompat.isV234()) return;
        try {
            Class<?> stateLambda = cl.loadClass("o5");
            Method u = stateLambda.getDeclaredMethod("u");
            Main.MODULE.hook(u).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    Object result = chain.proceed();
                    if (!(result instanceof List)) return result;
                    List source = (List) result;
                    boolean anyHidden = false;
                    for (int index = 0; index < source.size(); index++) {
                        if (Main.isHiddenAgentTransportUserMessage(source.get(index))) {
                            anyHidden = true;
                            break;
                        }
                    }
                    if (!anyHidden) return result;
                    ArrayList<Object> kept = new ArrayList<Object>(source.size());
                    for (Object message : source) {
                        if (!Main.isHiddenAgentTransportUserMessage(message)) kept.add(message);
                    }
                    long now = System.currentTimeMillis();
                    if (now - lastComposeStateLog > 5000L) {
                        lastComposeStateLog = now;
                        Main.log("compose visible state filtered hidden="
                                + (source.size() - kept.size()));
                    }
                    return kept;
                }
            });
            Main.log("installed compose visible-thread state filter o5.u");
        } catch (Throwable error) {
            Main.log("hook compose visible-thread state filter failed: " + error);
        }
    }

    // 当前侧栏的 tp 目录可能比 SQLite 的 chat_session_list 更早拿到新会话。
    // 编辑器每次打开时合并这份只读元数据，避免刚创建的对话暂时消失。
    static List<Object[]> nativeSessionDirectory() {
        ArrayList<Object[]> out = new ArrayList<>();
        Object value = NATIVE_SESSION_LIST;
        if (!(value instanceof List)) return out;
        try {
            for (Object session : new ArrayList<Object>((List) value)) {
                String sid = String.valueOf(Main.readHostField(session, "a"));
                if (sid == null || sid.length() == 0 || "null".equals(sid)) continue;
                if (isSessionRecentlyDeleted(sid)) continue;
                if (Main.isLocalApiInternalSession(sid)) continue;
                Object titleState = Main.readHostField(session, "g");
                Object title = titleState == null ? null : Main.invokeNoArg(titleState, "getValue");
                Object updated = Main.readHostField(session, "c");
                Object model = Main.invokeNoArg(session, "f");
                out.add(new Object[]{sid, title instanceof String ? title : "", updated, model});
            }
        } catch (Throwable t) { Main.log("native session directory failed: " + t); }
        return out;
    }

    static final class NativeDeleteRequest {
        final String sid;
        final Object session;
        final Object events;
        final Object fallbackAction;

        NativeDeleteRequest(String sid, Object session, Object events, Object fallbackAction) {
            this.sid = sid;
            this.session = session;
            this.events = events;
            this.fallbackAction = fallbackAction;
        }
    }

    static boolean executeNativeDelete(NativeDeleteRequest request) {
        if (request == null || request.sid == null || request.sid.length() == 0) return false;
        String sid = request.sid;
        Object session = request.session;
        Object events = request.events;
        if (HostCompat.isV241() && invokeXa3(request.fallbackAction)) {
            // ch2.f slot 9 is code257's verified native delete Function0. Its event class is not
            // the legacy h61(tp), so invoking the captured host callback is the exact code257
            // route and must precede the unchanged legacy event constructor below.
            markSessionDeletedLocally(sid);
            Main.log("requested code257 native sidebar delete sid=" + sid);
            return true;
        }
        if (session != null && events != null) {
            try {
                ClassLoader cl = session.getClass().getClassLoader();
                Class<?> eventType = HostCompat.load(cl, "h61");
                Constructor<?> eventCtor = null;
                for (Constructor<?> ctor : eventType.getDeclaredConstructors()) {
                    Class<?>[] types = ctor.getParameterTypes();
                    if (types.length == 1 && types[0].isAssignableFrom(session.getClass())) {
                        eventCtor = ctor;
                        break;
                    }
                }
                if (eventCtor == null) throw new NoSuchMethodException("h61(tp)");
                eventCtor.setAccessible(true);
                Object event = eventCtor.newInstance(session);
                if (Main.invokeHostOneArg(events, event)) {
                    markSessionDeletedLocally(sid);
                    Main.log("requested native DeepSeek session delete sid=" + sid);
                    return true;
                }
            } catch (Throwable t) {
                Main.log("native DeepSeek delete event failed sid=" + sid + ": " + t);
            }
        }

        if (invokeXa3(request.fallbackAction)) {
            markSessionDeletedLocally(sid);
            Main.log("requested native sidebar delete fallback sid=" + sid);
            return true;
        }
        Main.log("native DeepSeek delete unavailable sid=" + sid);
        return false;
    }

    private static void runNativeDeleteQueue(final Activity act,
                                             final ArrayList<NativeDeleteRequest> queue,
                                             final Map<String, List<String>> local,
                                             final int unmatched) {
        final Handler handler = new Handler(Looper.getMainLooper());
        final AtomicInteger index = new AtomicInteger();
        final AtomicInteger nativeOk = new AtomicInteger();
        final AtomicInteger nativeFail = new AtomicInteger();
        final Runnable worker = new Runnable() {
            String waitingSid;
            long waitingDeadline;
            @Override public void run() {
                if (waitingSid != null) {
                    if (!nativeSessionPresentRaw(waitingSid)) {
                        clearSidebarSessionCaches(waitingSid);
                        waitingSid = null;
                    } else if (SystemClock.uptimeMillis() >= waitingDeadline) {
                        // Never overlap another native delete when the host did not acknowledge
                        // the previous one. Concurrent WCDB deletes are the proven corruption and
                        // crash source; aborting the remainder is recoverable and explicit.
                        int remaining = Math.max(0, queue.size() - index.get());
                        nativeFail.addAndGet(remaining);
                        Main.log("batch delete stopped safely: host did not settle sid=" + waitingSid
                                + " remaining=" + remaining);
                        index.set(queue.size());
                        waitingSid = null;
                    } else {
                        handler.postDelayed(this, 120L);
                        return;
                    }
                }
                int i = index.getAndIncrement();
                if (i < queue.size()) {
                    NativeDeleteRequest request = queue.get(i);
                    if (executeNativeDelete(request)) {
                        nativeOk.incrementAndGet();
                        waitingSid = request.sid;
                        waitingDeadline = SystemClock.uptimeMillis() + 20_000L;
                    } else {
                        nativeFail.incrementAndGet();
                        clearSidebarSessionCaches(request.sid);
                    }
                    handler.postDelayed(this, 120L);
                    return;
                }
                runLocalDeleteCleanup(act, local, nativeOk.get(),
                        nativeFail.get() + unmatched);
            }
        };
        handler.post(worker);
    }

    private static boolean nativeSessionPresentRaw(String sid) {
        Object sessions = NATIVE_SESSION_STATE instanceof List
                ? NATIVE_SESSION_STATE : NATIVE_SESSION_LIST;
        if (!(sessions instanceof List) || sid == null) return false;
        try {
            for (Object session : new ArrayList<Object>((List) sessions)) {
                if (sid.equals(String.valueOf(Main.readHostField(session, "a")))) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static void clearSidebarSessionCaches(String sid) {
        if (sid == null) return;
        synchronized (SIDEBAR_DELETE_ACTIONS) {
            SIDEBAR_DELETE_ACTIONS.remove(sid);
            SIDEBAR_CLICK_ACTIONS.remove(sid);
        }
        SIDEBAR_ROW_BOUNDS.remove(sid);
        SIDEBAR_ROW_BOUNDS_AT.remove(sid);
        synchronized (SIDEBAR_BOUNDS_CB) {
            SIDEBAR_BOUNDS_CB.remove(sid);
        }
    }

    private static void runLocalDeleteCleanup(final Activity act,
                                              final Map<String, List<String>> local,
                                              final int nativeOk,
                                              final int nativeFail) {
        Thread cleanup = new Thread(new Runnable() {
            @Override public void run() {
                int localOk = 0;
                int localFail = 0;
                for (Map.Entry<String, List<String>> entry : local.entrySet()) {
                    SQLiteDatabase db = null;
                    try {
                        db = SQLiteDatabase.openDatabase(entry.getKey(), null,
                                SQLiteDatabase.OPEN_READWRITE);
                        int cleaned = ChatEditorUi.deleteSessionsLocal(db, entry.getValue());
                        localOk += cleaned;
                        localFail += Math.max(0, entry.getValue().size() - cleaned);
                    } catch (Throwable failure) {
                        localFail += entry.getValue().size();
                        Main.log("batch local session cleanup failed: " + failure);
                    } finally {
                        if (db != null) try { db.close(); } catch (Throwable ignored) {}
                    }
                }
                final StringBuilder result = new StringBuilder()
                        .append("DeepSeek 已删除 ").append(nativeOk)
                        .append(" 个，本地已清理 ").append(localOk).append(" 个");
                if (nativeFail > 0) result.append("，原生失败 ").append(nativeFail).append(" 个");
                if (localFail > 0) result.append("，本地失败 ").append(localFail).append(" 个");
                final Activity current = act;
                if (current != null && !current.isFinishing()) {
                    current.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            UiLanguage.toast(current, result.toString(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }, "DeePX-batch-delete-cleanup");
        cleanup.setDaemon(true);
        cleanup.start();
    }

    /**
     * Optimistically removes an explicitly deleted session from captured in-memory directories.
     * The real host request still decides server state.  The short tombstone only prevents the
     * editor from immediately re-merging a stale tp while that request is in flight.
     */
    static synchronized void markSessionDeletedLocally(String sid) {
        if (sid == null || sid.length() == 0) return;
        RECENTLY_DELETED_SESSION_IDS.put(sid, System.currentTimeMillis());
        HashSet<String> localIds = new HashSet<>(LOCAL_SESSION_IDS);
        localIds.remove(sid);
        LOCAL_SESSION_IDS = localIds;
        LOCAL_SESSION_IDS_AT = System.currentTimeMillis();
        Main.FROZEN_SESSION_HEADS.remove(sid);
        HistoryBridge.forgetSession(sid);
        ResponsePreserver.forgetSession(sid);
        synchronized (LOCAL_NATIVE_SESSIONS) {
            LOCAL_NATIVE_SESSIONS.remove(sid);
        }
        // Never mutate DeepSeek's SnapshotStateList here. The native h61 reducer owns that list;
        // concurrently removing from it caused large batches to lose later session objects and
        // could crash Compose. RECENTLY_DELETED_SESSION_IDS is sufficient to prevent re-merging.
    }

    static boolean isSessionRecentlyDeleted(String sid) {
        Long at = RECENTLY_DELETED_SESSION_IDS.get(sid);
        if (at == null) return false;
        if (System.currentTimeMillis() - at.longValue()
                <= DELETED_SESSION_VISIBILITY_GRACE_MS) return true;
        RECENTLY_DELETED_SESSION_IDS.remove(sid, at);
        return false;
    }

    public static boolean isUsableSessionId(String sid) {
        return sid != null && sid.length() > 0 && !"null".equals(sid);
    }
}
