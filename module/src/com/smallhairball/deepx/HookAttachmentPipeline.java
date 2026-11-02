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

/** Hook group extracted from Main.java: ATTACH category (see JavaHookGuide). */
final class HookAttachmentPipeline {
    static final HookAttachmentPipeline INSTANCE = new HookAttachmentPipeline();

    static final String EXPERT_UNLOCK_FILE = "/data/data/com.deepseek.chat/files/deekseep_expert_unlock";

    static final String EDITOR_IMAGE_URI_PREFIX =
            "content://com.deepseek.chat.provider/tmp_captured_images/";

    // 专家模式解锁：俘获任意"已启用"模型的真 feature 模板，回填给 expert
    private static volatile Object tplThink;

    private static volatile Object tplSearch;

    private static volatile Object tplFile;

    // sf5(模型配置) 字段：a=model_type f=enabled g=switchable j=think k=search l=file(gf5)；GF5_C=gf5.c 最大文件数
    private static Field EX_A, EX_F, EX_G, EX_J, EX_K, EX_L, GF5_C;

    private static final java.util.List<Object> expertInsts = new java.util.ArrayList<>();

    // ── 专家图片→视觉描述中继（expert-image → vision relay）────────────────
    // ★正式功能开关：expert 模式带图 → 后台视觉描述中继。存在=开启。
    static final String EXPERT_RELAY_FILE = "/data/data/com.deepseek.chat/files/deekseep_expert_relay";

    static final String V236_EXPERT_RELAY_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_expert_relay_code249";

    // 已成功走过中继的原会话。按 sid 落独立标记，重启后历史同步不再依赖服务端模型字段。
    static final String EXPERT_RELAY_SESSION_DIR =
            "/data/data/com.deepseek.chat/files/deekseep_expert_relay_sessions";

    static final String RELAY_PROMPT_MARKER = "【图片内容（自动识别）】";

    static final String RELAY_PROMPT_MARKER_EN = "[Image content (automatically recognized)]";

    // 中继捕获的图片 fragment（qs7 JSON）按原会话 sid 落盘，供强杀重开后 pw0/fm8 注入。
    static final String RELAY_IMAGE_DIR =
            "/data/data/com.deepseek.chat/files/deekseep_relay_images";

    // 发给 vision 的中性描述指令（绝不能带用户越狱系统提示，否则 vision 会拒答）。
    static final String VISION_DESCRIBE_PROMPT =
            "请客观描述这张图片，100到200字：包括主要事物、颜色、场景、画面细节，以及逐字转录图中出现的所有文字。只做客观描述，不评价、不拒绝、不添加与图片无关的内容。";

    static final String VISION_DESCRIBE_PROMPT_EN =
            "Objectively describe this image in 100–200 words. Include the main subjects, colors, scene, visual details, and a verbatim transcription of all visible text. Describe only what is present; do not evaluate, refuse, or add unrelated content.";

    private static String relayPromptMarker() {
        return UiLanguage.text(RELAY_PROMPT_MARKER, RELAY_PROMPT_MARKER_EN);
    }

    private static String visionDescribePrompt() {
        return UiLanguage.text(VISION_DESCRIBE_PROMPT, VISION_DESCRIBE_PROMPT_EN);
    }

    static volatile Object liveFm8;

    static final Map<Object, ModelFileOutput.StreamingAccumulator> MODEL_FILE_STREAMS =
            Collections.synchronizedMap(
                    new WeakHashMap<Object, ModelFileOutput.StreamingAccumulator>());

    // localApiLastSessionError now lives on Main (see "Fields moved back" block).

    private static final HashSet<String> expertRelaySessionIds = new HashSet<>();

    private static final ConcurrentHashMap<String, Object> FROZEN_EDIT_REAPPLY_TOKENS =
            new ConcurrentHashMap<>();

    // DeepSeek 自己的文件 API（pv0）。编辑器复用宿主登录态调用 fork_file_task，
    // 为复制到聊天记录的图片取得新的 file_id/signed_path，避免旧签名重开后失效。
    static volatile Object IMAGE_FILE_API;

    static volatile Object IMAGE_COMPOSER;

    static volatile ClassLoader IMAGE_HOST_CL;

    /** Opens generated-file links inside the current DeepSeek Activity instead of a browser. */
    void hookModelFileLinks() {
        int hooked = 0;
        try {
            Method startActivity = Activity.class.getMethod("startActivity", Intent.class);
            startActivity.setAccessible(true);
            Main.MODULE.hook(startActivity).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    Object owner = chain.getThisObject();
                    Object argument = chain.getArg(0);
                    if (owner instanceof Activity && argument instanceof Intent) {
                        Uri uri = ((Intent) argument).getData();
                        if (NativeDualChatBridge.openFileLink((Activity) owner, uri)) {
                            return null;
                        }
                    }
                    return chain.proceed();
                }
            });
            hooked++;
        } catch (Throwable error) {
            Main.log("model file Activity link hook unavailable: " + Main.safeThrowableMessage(error));
        }
        try {
            Method startActivity = ContextWrapper.class.getMethod(
                    "startActivity", Intent.class);
            startActivity.setAccessible(true);
            Main.MODULE.hook(startActivity).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    Object owner = chain.getThisObject();
                    Object argument = chain.getArg(0);
                    Activity activity = activityFromContext(
                            owner instanceof Context ? (Context) owner : null);
                    if (activity != null && argument instanceof Intent) {
                        Uri uri = ((Intent) argument).getData();
                        if (NativeDualChatBridge.openFileLink(activity, uri)) return null;
                    }
                    return chain.proceed();
                }
            });
            hooked++;
        } catch (Throwable error) {
            Main.log("model file ContextWrapper link hook unavailable: "
                    + Main.safeThrowableMessage(error));
        }
        Main.log("model file native-message link hooks installed=" + hooked);
    }

    private static Activity activityFromContext(Context context) {
        Context current = context;
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (current instanceof Activity) return (Activity) current;
            if (!(current instanceof ContextWrapper)) return null;
            Context next = ((ContextWrapper) current).getBaseContext();
            if (next == current) return null;
            current = next;
        }
        return null;
    }

    /**
     * The host normally stores a server-relative value in fp.signed_path.  us.a(host) then
     * turns that value into https://host/api{signed_path}.  Editor gallery images deliberately
     * use the app's own FileProvider instead, so passing them through the server URL builder
     * produces an invalid https URL even though the durable file and cache mirror are intact.
     * Keep the host path untouched for every normal attachment and unwrap only our private,
     * narrowly-scoped FileProvider prefix.
     */
    void hookLocalEditorImageUris(final ClassLoader cl) {
        try {
            Class<?> imagePath = HostCompat.load(cl, "us");
            final Field signedPath = imagePath.getDeclaredField("b");
            signedPath.setAccessible(true);
            Method resolve = imagePath.getDeclaredMethod("a", String.class);
            resolve.setAccessible(true);
            Main.MODULE.hook(resolve).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    Object raw = signedPath.get(chain.getThisObject());
                    if (raw instanceof String
                            && ((String) raw).startsWith(EDITOR_IMAGE_URI_PREFIX)) {
                        Uri local = Uri.parse((String) raw);
                        Main.log("resolved local editor image uri=" + local.getLastPathSegment());
                        return local;
                    }
                    return chain.proceed();
                }
            });
            Main.log("hooked local editor image URI resolver");
        } catch (Throwable t) {
            Main.log("hook local editor image URI resolver failed: " + t);
        }
    }

    // 专家模式解锁旗标（hookExpertUnlock 读它决定是否给 expert 回填 feature 模板）
    static boolean isExpertUnlock() {
        if (HostCompat.isV236()) {
            return RemoteFeatureFlags.mode(RemoteFeatureFlags.V236_FORCE_EXPERT_MODEL)
                    == RemoteFeatureFlags.FORCE_ON;
        }
        if (HostCompat.isV241()) {
            return RemoteFeatureFlags.mode(RemoteFeatureFlags.V241_FORCE_EXPERT_MODEL)
                    == RemoteFeatureFlags.FORCE_ON;
        }
        return new File(EXPERT_UNLOCK_FILE).exists();
    }

    // 热更新后专家入口和图片中继分别管理；中继保留旧实现但默认暂停。
    static boolean isExpertRelayEnabled() {
        if (BuildInfo.PROTECTED_BUILD && BuildInfo.LOCAL_API_INCLUDED && HostCompat.isV236()) {
            return new File(V236_EXPERT_RELAY_FILE).exists();
        }
        return new File(EXPERT_RELAY_FILE).exists();
    }

    /** Hides the module wallpaper before DeepSeek draws its native full-screen image viewer. */
    void hookNativeImagePreviewBoundary(final ClassLoader cl) {
        if (!HostCompat.isV234()) return;
        int resourceHooks = 0;
        try {
            Method getText = android.content.res.Resources.class.getDeclaredMethod(
                    "getText", int.class);
            Method getString = android.content.res.Resources.class.getDeclaredMethod(
                    "getString", int.class);
            Method getFormatted = android.content.res.Resources.class.getDeclaredMethod(
                    "getString", int.class, Object[].class);
            Method[] boundaries = new Method[]{getText, getString, getFormatted};
            for (Method boundary : boundaries) {
                boundary.setAccessible(true);
                Main.MODULE.hook(boundary).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object idValue = chain.getArg(0);
                        int id = idValue instanceof Number
                                ? ((Number) idValue).intValue() : 0;
                        // image_preview_load_failed .. image_preview_save_successfully. These
                        // IDs are shared by both 2.3.4 store channels and form a stable native
                        // resource boundary even when the R8 composable owner changes.
                        if (id >= 0x7f0f012c && id <= 0x7f0f0130) {
                            Activity activity = Main.MODULE.curAct.get();
                            if (activity != null) {
                                ChatAppearance.onNativeImagePreviewRendered(activity);
                            }
                        }
                        return chain.proceed();
                    }
                });
                resourceHooks++;
            }
        } catch (Throwable error) {
            Main.log("native image preview resource boundary unavailable: " + error);
        }
        // Mainland 2.3.4/2.3.6 keep the full-screen image preview composable in no0.
        // au1 is an unrelated R8 utility class and therefore installed zero hooks, which left
        // the chat wallpaper visible behind the native viewer on the mainland channel.
        final String owner = HostCompat.isV241() ? "pe4"
                : HostCompat.isGooglePlay() ? "fw1" : "no0";
        try {
            Class<?> type = cl.loadClass(owner);
            int installed = 0;
            for (Method method : type.getDeclaredMethods()) {
                Class<?>[] p = method.getParameterTypes();
                int uriCount = 0;
                int composerIndex = -1;
                for (int i = 0; i < p.length; i++) {
                    if (p[i] == Uri.class) uriCount++;
                    String simple = p[i].getSimpleName();
                    if ("androidx.compose.runtime.Composer".equals(p[i].getName())
                            || "bu1".equals(simple) || "yx1".equals(simple)
                            || "gy1".equals(simple) || "r12".equals(simple)) {
                        composerIndex = i;
                    }
                }
                if (!Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class
                        || uriCount < 2 || composerIndex < 0 || p.length < 10) continue;
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Activity activity = Main.MODULE.curAct.get();
                        if (activity != null) ChatAppearance.onNativeImagePreviewRendered(activity);
                        return chain.proceed();
                    }
                });
                installed++;
            }
            Main.log("native image preview appearance boundary=" + owner + " x" + installed
                    + " resources=" + resourceHooks);
        } catch (Throwable error) {
            Main.log("native image preview owner boundary unavailable: " + error
                    + " resources=" + resourceHooks);
        }
    }

    /** Appends the host's native upload-file card at the bottom of the matching message body. */
    void hookNativeModelFileCards(final ClassLoader cl) {
        if (!HostCompat.isV234()) return;
        final String rendererName = HostCompat.isV241() ? "ch2"
                : HostCompat.isGooglePlay() ? "zj4" : "we0";
        try {
            Class<?> renderer = cl.loadClass(rendererName);
            int installed = 0;
            for (Method method : renderer.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!Modifier.isStatic(method.getModifiers())
                        || method.getReturnType() != void.class
                        || types.length != 14
                        || types[0] != String.class
                        || types[1] != int.class
                        || types[12] != int.class
                        || types[13] != int.class) continue;
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object result = chain.proceed();
                        if (!NativeDualChatBridge.isActive()) return result;
                        Object state = canonicalMutableTextState(chain.getArg(2));
                        ModelFileOutput.StreamingParsed latest = null;
                        synchronized (MODEL_FILE_STREAMS) {
                            ModelFileOutput.StreamingAccumulator stream =
                                    MODEL_FILE_STREAMS.get(state);
                            if (stream != null) latest = stream.latest();
                        }
                        List<ModelFileOutput.StreamingFile> files = latest == null
                                ? Collections.<ModelFileOutput.StreamingFile>emptyList()
                                : latest.files;
                        if (files.isEmpty()) {
                            files = NativeDualChatBridge.filesForSession(
                                    String.valueOf(chain.getArg(0)));
                        }
                        if (!files.isEmpty()) {
                            NativeModelFileCardRenderer.render(
                                    cl, chain.getArg(11), files);
                        }
                        return result;
                    }
                });
                installed++;
            }
            Main.log("native model-file message cards=" + installed
                    + " renderer=" + rendererName);
        } catch (Throwable error) {
            Main.log("native model-file message card hook unavailable: " + error);
        }
    }

    /** Returns the concrete mutable String State behind DeepSeek's read-only StateFlow facade. */
    static Object canonicalMutableTextState(Object state) {
        Object current = state;
        Object lastStringState = Main.invokeNoArg(current, "getValue") instanceof String
                ? current : null;
        for (int depth = 0; current != null && depth < 3; depth++) {
            if (setMutableStateValueProbe(current)) return current;
            Object delegate = Main.readHostField(current, "a");
            if (delegate == null || delegate == current) break;
            current = delegate;
            if (Main.invokeNoArg(current, "getValue") instanceof String) {
                lastStringState = current;
            }
        }
        return lastStringState == null ? state : lastStringState;
    }

    private static boolean setMutableStateValueProbe(Object state) {
        if (state == null) return false;
        for (Class<?> type = state.getClass(); type != null;
             type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if ("l".equals(method.getName())
                        && method.getParameterTypes().length == 1) return true;
            }
        }
        return false;
    }

    // ── 专家模式解锁：sf5(模型配置)构造后强改 final 字段点亮思考/搜索/上传 ──
    // 服务器默认给 expert 返回 f/g=true 但 j/k/l=null(禁思考/搜索/文件)；构造后回填真模板即本地点亮。
    void hookExpertUnlock(ClassLoader cl) {
        try {
            final Class<?> sf5 = HostCompat.load(cl, "sf5");
            final Class<?> gf5c = HostCompat.load(cl, "gf5");
            EX_A = sf5.getDeclaredField("a"); EX_A.setAccessible(true);
            EX_F = sf5.getDeclaredField("f"); EX_F.setAccessible(true);
            EX_G = sf5.getDeclaredField("g"); EX_G.setAccessible(true);
            EX_J = sf5.getDeclaredField("j"); EX_J.setAccessible(true);
            EX_K = sf5.getDeclaredField("k"); EX_K.setAccessible(true);
            EX_L = sf5.getDeclaredField("l"); EX_L.setAccessible(true);
            try { GF5_C = gf5c.getDeclaredField("c"); GF5_C.setAccessible(true); } catch (Throwable ignored) {}
            int n = 0;
            for (Constructor<?> ctor : sf5.getDeclaredConstructors()) {
                Class<?>[] pt = ctor.getParameterTypes();
                // synthetic 反序列化构造器：sf5(int i, String a, ... , of5 j[10], lf5 k[11], gf5 l[12], ...)
                // i 是 kotlinx bitmask，位缺失时字段被置 null。构造后再反射写 final 对 App 编译读取点不可见，
                // 故改为「构造前」把模板塞进 args 并置位 bitmask → 字段出生即非空，任何读取路径都能看到。
                final boolean synth = pt.length >= 13 && pt[0] == int.class;
                Main.MODULE.hook(ctor).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object r;
                        if (synth) {
                            Object[] a = chain.getArgs().toArray();
                            try {
                                applyV236ForcedModelAvailabilityToConstructorArgs(a);
                                applyV241ForcedModelAvailabilityToConstructorArgs(a);
                                if (a != null && a.length >= 13 && "expert".equals(a[1])
                                        && isExpertUnlock()) {
                                    int mask = (a[0] instanceof Integer) ? (Integer) a[0] : 0;
                                    // f(32)/g(64) 位缺失时构造器默认 true，无需动；只补 j(512)/k(1024)/l(2048)
                                    if (tplThink != null)  { a[10] = tplThink;  mask |= 512; }
                                    if (tplSearch != null) { a[11] = tplSearch; mask |= 1024; }
                                    if (tplFile != null)   { a[12] = tplFile;   mask |= 2048; }
                                    a[0] = mask;
                                    Main.log("expert ctor-inject (j=" + (tplThink!=null) + " k=" + (tplSearch!=null)
                                            + " file=" + gf5Info(tplFile) + ")");
                                }
                            } catch (Throwable t) { Main.log("expert ctor-inject err: " + t); }
                            r = chain.proceed(a);
                        } else {
                            r = chain.proceed();
                        }
                        try { onSf5Built(chain.getThisObject()); }
                        catch (Throwable t) { Main.log("expert unlock err: " + t); }
                        return r;
                    }
                });
                // If the runtime inlines sf5 construction, the constructor hook does not run and
                // the instance's k/l fields remain null.
                // deoptimize 强制运行时不内联该构造器，让所有构造路径都走进 hook。
                try { boolean d = Main.MODULE.deoptimize(ctor); Main.log("deopt sf5 ctor ok=" + d); }
                catch (Throwable t) { Main.log("deopt sf5 ctor err: " + t); }
                n++;
            }
            Main.log("hooked sf5 ctors x" + n + " (expert unlock)");
            // 兜底：构造 hook 可能漏掉「模块加载前已反序列化」的实例，而 UI 门禁读的正是那个旧实例。
            // sf5.b(boolean,bu1) 是模型芯片渲染时取图标的方法，选中的模型必然被渲染 → 借此俘获真正被消费的实例并即时点亮。
            int m = 0;
            for (java.lang.reflect.Method mtd : sf5.getDeclaredMethods()) {
                if (!"b".equals(mtd.getName())) continue;
                Class<?>[] pt = mtd.getParameterTypes();
                if (pt.length != 2 || pt[0] != boolean.class) continue;
                Main.MODULE.hook(mtd).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        try {
                            Object self = chain.getThisObject();
                            if (self != null) {
                                // 无论哪个模型渲染，先尝试俘获模板(default/vision 的 j/k/l 真货)
                                Object j = EX_J.get(self), k = EX_K.get(self), l = EX_L.get(self);
                                if (j != null && tplThink == null) tplThink = j;
                                if (k != null && tplSearch == null) tplSearch = k;
                                if (l != null && gf5Count(l) > 0 && l != tplFile) tplFile = l;
                                if ("expert".equals(EX_A.get(self)) && isExpertUnlock()) {
                                    if (EX_L.get(self) == null || gf5Count(EX_L.get(self)) <= 0
                                            || EX_J.get(self) == null || EX_K.get(self) == null) {
                                        synchronized (expertInsts) {
                                            boolean has = false;
                                            for (Object e : expertInsts) if (e == self) { has = true; break; }
                                            if (!has) expertInsts.add(self);
                                        }
                                        applyExpert(self);
                                    }
                                }
                            }
                        } catch (Throwable t) { Main.log("expert b() patch err: " + t); }
                        return chain.proceed();
                    }
                });
                m++;
            }
            Main.log("hooked sf5.b() x" + m + " (expert gate catch)");
        } catch (Throwable t) { Main.log("hookExpertUnlock failed: " + t); }
    }

    // 上传门禁 y91.a(Object,uz1)：事件对象里携带被 UI 消费的真实 sf5。在判空前扫描 arg0 的字段找到 sf5，
    // 打印它的 identityHashCode + l/k/j 状态（对比构造时 patch 的 @hash），并就地点亮 → 直接命中真正被读的实例。
    void installExpertUploadGate(ClassLoader cl) {
        try {
            final Class<?> sf5 = HostCompat.load(cl, "sf5");
            final Class<?> y91 = HostCompat.load(cl, "y91");
            int n = 0;
            for (final java.lang.reflect.Method mtd : y91.getDeclaredMethods()) {
                if (!"a".equals(mtd.getName())) continue;
                Class<?>[] pt = mtd.getParameterTypes();
                if (pt.length != 2 || pt[0] != Object.class) continue;
                Main.MODULE.hook(mtd).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        try {
                            Object ev = chain.getArg(0);
                            if (ev != null) {
                                for (Field f : ev.getClass().getDeclaredFields()) {
                                    if (!sf5.isAssignableFrom(f.getType())) continue;
                                    f.setAccessible(true);
                                    Object s = f.get(ev);
                                    if (s == null) continue;
                                    boolean isExpert = "expert".equals(EX_A.get(s));
                                    Main.log("[GATE] y91.a sf5 @" + Integer.toHexString(System.identityHashCode(s))
                                            + " a=" + EX_A.get(s) + " l=" + gf5Info(EX_L.get(s))
                                            + " k=" + (EX_K.get(s)!=null) + " j=" + (EX_J.get(s)!=null));
                                    if (isExpert && isExpertUnlock()) applyExpert(s);
                                }
                            }
                        } catch (Throwable t) { Main.log("[GATE] err: " + t); }
                        return chain.proceed();
                    }
                });
                n++;
            }
            Main.log("installed expert upload gate on y91.a x" + n);
        } catch (Throwable t) { Main.log("installExpertUploadGate failed: " + t); }
    }

    // 每个 sf5(模型配置)构造后回调：俘获可用模板 + 给 expert 回填 + 事后 back-fill
    private static void onSf5Built(Object o) throws Exception {
        applyV236ForcedModelAvailability(o);
        applyV241ForcedModelAvailability(o);
        Object j = EX_J.get(o), k = EX_K.get(o), l = EX_L.get(o);
        if (j != null && tplThink == null) tplThink = j;
        if (k != null && tplSearch == null) tplSearch = k;
        if (l != null && gf5Count(l) > 0 && l != tplFile) {
            tplFile = l;   // c>0 才是真能上传的配置
            Main.log("expert tplFile captured model=" + EX_A.get(o) + " " + gf5Info(l));
        }
        boolean isExpert = "expert".equals(EX_A.get(o));
        if (isExpert && isExpertUnlock()) {
            synchronized (expertInsts) {
                boolean has = false;
                for (Object e : expertInsts) if (e == o) { has = true; break; }
                if (!has) expertInsts.add(o);
            }
            applyExpert(o);
        }
        backfillExperts();  // 模板可能晚于 expert 才构造出来，事后统一回填
    }

    private static void applyV241ForcedModelAvailabilityToConstructorArgs(Object[] args) {
        if (!HostCompat.isV241()
                || args == null || args.length < 8 || !(args[1] instanceof String)) return;
        String model = (String) args[1];
        String key = "expert".equals(model)
                ? RemoteFeatureFlags.V241_FORCE_EXPERT_MODEL
                : "vision".equals(model)
                ? RemoteFeatureFlags.V241_FORCE_VISION_MODEL : null;
        if (key == null) return;
        int mode = RemoteFeatureFlags.mode(key);
        if (mode == RemoteFeatureFlags.FOLLOW) return;
        boolean enabled = mode == RemoteFeatureFlags.FORCE_ON;
        int mask = args[0] instanceof Integer ? ((Integer) args[0]).intValue() : 0;
        args[0] = Integer.valueOf(mask | 32 | 64);
        args[6] = Boolean.valueOf(enabled);
        args[7] = Boolean.valueOf(enabled);
    }

    private static void applyV236ForcedModelAvailabilityToConstructorArgs(Object[] args) {
        if (!HostCompat.isV236()
                || args == null || args.length < 8 || !(args[1] instanceof String)) return;
        String model = (String) args[1];
        String key = "expert".equals(model)
                ? RemoteFeatureFlags.V236_FORCE_EXPERT_MODEL
                : "vision".equals(model)
                ? RemoteFeatureFlags.V236_FORCE_VISION_MODEL : null;
        if (key == null) return;
        int mode = RemoteFeatureFlags.mode(key);
        if (mode == RemoteFeatureFlags.FOLLOW) return;
        boolean enabled = mode == RemoteFeatureFlags.FORCE_ON;
        int mask = args[0] instanceof Integer ? ((Integer) args[0]).intValue() : 0;
        args[0] = Integer.valueOf(mask | 32 | 64);
        args[6] = Boolean.valueOf(enabled);
        args[7] = Boolean.valueOf(enabled);
    }

    private static void applyV241ForcedModelAvailability(Object config) {
        if (!HostCompat.isV241() || config == null) return;
        try {
            String model = String.valueOf(EX_A.get(config));
            String key = "expert".equals(model)
                    ? RemoteFeatureFlags.V241_FORCE_EXPERT_MODEL
                    : "vision".equals(model)
                    ? RemoteFeatureFlags.V241_FORCE_VISION_MODEL : null;
            if (key == null) return;
            int mode = RemoteFeatureFlags.mode(key);
            if (mode == RemoteFeatureFlags.FOLLOW) return;
            boolean enabled = mode == RemoteFeatureFlags.FORCE_ON;
            EX_F.set(config, Boolean.valueOf(enabled));
            EX_G.set(config, Boolean.valueOf(enabled));
            Main.log("code257 model availability override model=" + model
                    + " enabled=" + enabled);
        } catch (Throwable error) {
            Main.log("code257 model availability override failed: "
                    + Main.safeThrowableMessage(error));
        }
    }

    private static void applyV236ForcedModelAvailability(Object config) {
        if (!HostCompat.isV236() || config == null) return;
        try {
            String model = String.valueOf(EX_A.get(config));
            String key = "expert".equals(model)
                    ? RemoteFeatureFlags.V236_FORCE_EXPERT_MODEL
                    : "vision".equals(model)
                    ? RemoteFeatureFlags.V236_FORCE_VISION_MODEL : null;
            if (key == null) return;
            int mode = RemoteFeatureFlags.mode(key);
            if (mode == RemoteFeatureFlags.FOLLOW) return;
            boolean enabled = mode == RemoteFeatureFlags.FORCE_ON;
            EX_F.set(config, Boolean.valueOf(enabled));
            EX_G.set(config, Boolean.valueOf(enabled));
            Main.log("code249 model availability override model=" + model
                    + " enabled=" + enabled);
        } catch (Throwable error) {
            Main.log("code249 model availability override failed: "
                    + Main.safeThrowableMessage(error));
        }
    }

    private static void applyExpert(Object o) throws Exception {
        EX_F.set(o, Boolean.TRUE);
        EX_G.set(o, Boolean.TRUE);
        if (EX_J.get(o) == null && tplThink != null) EX_J.set(o, tplThink);
        if (EX_K.get(o) == null && tplSearch != null) EX_K.set(o, tplSearch);
        Object curL = EX_L.get(o);
        if (tplFile != null && (curL == null || gf5Count(curL) <= 0)) EX_L.set(o, tplFile);
        Main.log("expert applied @" + Integer.toHexString(System.identityHashCode(o))
                + " (j=" + (EX_J.get(o)!=null) + " k=" + (EX_K.get(o)!=null)
                + " file=" + gf5Info(EX_L.get(o)) + ")");
    }

    private static void backfillExperts() {
        if (tplFile == null && tplThink == null && tplSearch == null) return;
        synchronized (expertInsts) {
            for (Object o : expertInsts) {
                try {
                    if (EX_L.get(o) == null || gf5Count(EX_L.get(o)) <= 0
                            || EX_J.get(o) == null || EX_K.get(o) == null) applyExpert(o);
                } catch (Throwable ignored) {}
            }
        }
    }

    // 读 gf5.c(最大文件数)；读不到返回 -1，null 返回 0
    private static int gf5Count(Object gf5) {
        if (gf5 == null) return 0;
        if (GF5_C == null) return -1;
        try { Object v = GF5_C.get(gf5); return (v instanceof Integer) ? (Integer) v : -1; }
        catch (Throwable t) { return -1; }
    }

    private static String gf5Info(Object gf5) {
        if (gf5 == null) return "null";
        return "{c=" + gf5Count(gf5) + " cls=" + gf5.getClass().getName() + "}";
    }

    // ── 设置页入口生命周期 ─────────────────────────────────────────

    private static volatile boolean imageApiCaptureLogged;

    private static volatile boolean imageComposerCaptureLogged;

    void installImageCredentialBridge(final ClassLoader cl) {
        int installed = 0;
        try {
            Class<?> apiClass = HostCompat.load(cl, "pv0");
            for (Constructor<?> ctor : apiClass.getDeclaredConstructors()) {
                Main.MODULE.hook(ctor).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object result = chain.proceed();
                        IMAGE_FILE_API = chain.getThisObject();
                        IMAGE_HOST_CL = cl;
                        if (!imageApiCaptureLogged) {
                            imageApiCaptureLogged = true;
                            Main.log("captured file api pv0="
                                    + chain.getThisObject().getClass().getName());
                        }
                        return result;
                    }
                });
                installed++;
            }
        } catch (Throwable t) { Main.log("capture pv0 failed: " + t); }

        // 兜底：即使 pv0 比模块安装钩子更早构造，也能从之后创建的 k31.c.d 取回同一实例。
        try {
            Class<?> composerClass = HostCompat.load(cl, "k31");
            for (Constructor<?> ctor : composerClass.getDeclaredConstructors()) {
                Main.MODULE.hook(ctor).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object result = chain.proceed();
                        try {
                            IMAGE_COMPOSER = chain.getThisObject();
                            Object repository = Main.readHostField(chain.getThisObject(), "c");
                            Object api = Main.readHostField(repository, "d");
                            if (api != null) {
                                IMAGE_FILE_API = api;
                                IMAGE_HOST_CL = cl;
                            }
                            if (!imageComposerCaptureLogged) {
                                imageComposerCaptureLogged = true;
                                Main.log("captured composer k31="
                                        + chain.getThisObject().getClass().getName()
                                        + " api=" + (api == null ? "null"
                                        : api.getClass().getName()));
                            }
                        } catch (Throwable ignored) {}
                        return result;
                    }
                });
                installed++;
            }
        } catch (Throwable t) { Main.log("capture k31 file api failed: " + t); }
        Main.log("installed image credential bridge constructors=" + installed);
    }

    /** Removes the gateway's reusable server sessions before DeepSeek persists/renders its page. */
    void f9(final ClassLoader cl) {
        try {
            Class<?> pageType = HostCompat.load(cl, "sb1");
            int installed = 0;
            for (Constructor<?> ctor : pageType.getDeclaredConstructors()) {
                Class<?>[] types = ctor.getParameterTypes();
                if (types.length != 3 || types[0] != int.class
                        || !List.class.isAssignableFrom(types[1])
                        || types[2] != boolean.class) continue;
                Main.MODULE.hook(ctor).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object raw = chain.getArg(1);
                        if (!(raw instanceof List)) return chain.proceed();
                        List source = (List) raw;
                        ArrayList filtered = null;
                        for (int i = 0; i < source.size(); i++) {
                            Object session = source.get(i);
                            String sid = String.valueOf(Main.readHostField(session, "a"));
                            if (Main.isLocalApiInternalSession(sid)) {
                                if (filtered == null) filtered = new ArrayList(source);
                                filtered.remove(session);
                            }
                        }
                        if (filtered == null) return chain.proceed();
                        Object[] args = chain.getArgs().toArray();
                        args[1] = filtered;
                        Main.log("[dq0] hidden rs(s) from cloud page="
                                + (source.size() - filtered.size()));
                        return chain.proceed(args);
                    }
                });
                installed++;
            }
            Main.log("installed local API session visibility filter sb1 x" + installed);
        } catch (Throwable t) {
            Main.log("f9 failed: " + t);
        }
    }

    static String stringField(Object obj, String name) {
        try {
            name = HostCompat.staticMessageField(obj, name);
            Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            Object v = f.get(obj);
            return v instanceof String ? (String) v : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    // 2) 通用历史清理/快照 + 专家图片保留。2.2.1=fm8/rl8，2.2.2=gm8/sl8。
    void installExpertHistoryImagePreserver(final ClassLoader cl) {
        int repoCount = 0;
        int ctorCount = 0;
        int writeCount = 0;
        for (String legacyRepoName : new String[]{"gm8", "fm8"}) {
            String repoName = HostCompat.name(legacyRepoName);
            try {
                final Class<?> repo = cl.loadClass(repoName);
                ArrayList<Method> writers = new ArrayList<>();
                for (Method m : repo.getDeclaredMethods()) {
                    Class<?>[] pts = m.getParameterTypes();
                    if ("b".equals(m.getName()) && pts.length == 7
                            && pts[0] == String.class && pts[1] == int.class
                            && List.class.isAssignableFrom(pts[4])) writers.add(m);
                }
                if (writers.isEmpty()) continue; // 当前 fm8 是 synthetic Transaction，不能当仓库捕获。
                repoCount++;
                for (Constructor<?> ctor : repo.getDeclaredConstructors()) {
                    Main.MODULE.hook(ctor).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            Object r = chain.proceed();
                            liveFm8 = chain.getThisObject();
                            return r;
                        }
                    });
                    ctorCount++;
                }
                for (Method writer : writers) {
                    Main.MODULE.hook(writer).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            Object[] args = chain.getArgs().toArray();
                            String writtenSid = args.length > 0 && args[0] instanceof String
                                    ? (String) args[0] : null;
                            try {
                                liveFm8 = chain.getThisObject();
                                String sid = args.length > 0 && args[0] instanceof String
                                        ? (String) args[0] : null;
                                if (Main.isNoCensorForSession(sid)) {
                                    Object rows = args.length > 4 ? args[4] : null;
                                    int restored = ResponsePreserver.restoreRepositoryRows(cl, sid, rows);
                                    if (restored > 0) {
                                        Main.log("restored preserved responses before history write=" + restored
                                                + " sid=" + sid);
                                    }
                                }
                                // 2.3.4 reuses these exact row instances for the optimistic
                                // message currently being rendered. Mutating their fragments in
                                // place makes Compose briefly classify the just-sent row as a
                                // failed history load. The online-history bridge and the startup
                                // database migration already remove our private wrapper before it
                                // can become visible, so leave live 2.3.4 rows untouched here.
                                if (!HostCompat.isV234()) {
                                    int cleaned = HistoryBridge.sanitizeRepositoryRows(args);
                                    if (cleaned > 0) {
                                        Main.log("history repository prompts cleaned=" + cleaned);
                                    }
                                }
                                preserveImagesBeforeLocalWrite(cl, chain.getThisObject(), args);
                            } catch (Throwable t) {
                                Main.extLog("[HISTORY] repository preserve err: " + t + "\n" + Main.stackToString(t));
                            }
                            Object result = chain.proceed();
                            if (writtenSid != null && ChatEditorUi
                                    .localSessionIdsFromAllBackups().contains(writtenSid)) {
                                scheduleFrozenEditReapply(cl, writtenSid);
                            }
                            return result;
                        }
                    });
                    writeCount++;
                }
            } catch (Throwable ignored) {}
        }
        Main.log("installed history repositories=" + repoCount + " ctor=" + ctorCount + " write=" + writeCount);

        try {
            Class<?> pw0 = HostCompat.load(cl, "pw0");
            int n = 0;
            for (Constructor<?> ctor : pw0.getDeclaredConstructors()) {
                Main.MODULE.hook(ctor).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object r = chain.proceed();
                        try {
                            String sid = String.valueOf(Main.readHostField(chain.getThisObject(), "a"));
                            if (Main.isNoCensorForSession(sid)) {
                                int snapshotted = ResponsePreserver.snapshotHistoryResponse(
                                        cl, chain.getThisObject());
                                if (snapshotted > 0) {
                                    Main.log("snapshotted normal responses before cold-sync restore="
                                            + snapshotted);
                                }
                                int restored = ResponsePreserver.restoreHistoryResponse(
                                        cl, chain.getThisObject());
                                if (restored > 0) {
                                    Main.log("restored preserved responses in online history=" + restored);
                                }
                            }
                        } catch (Throwable t) {
                            Main.extLog("[HISTORY] response restore err: " + t + "\n" + Main.stackToString(t));
                        }
                        try {
                            // Capture the final form after expert relay restores FILE fragments
                            // and removes its internal vision-description text.
                            preserveImagesInHistoryResponse(cl, chain.getThisObject());
                        } catch (Throwable t) {
                            Main.extLog("[HISTORY] pw0 image preserve err: " + t + "\n" + Main.stackToString(t));
                        }
                        try {
                            int folded = foldProactiveHeartbeatHistory(chain.getThisObject());
                            if (folded > 0) {
                                Main.log("folded internal proactive history turns=" + folded);
                            }
                        } catch (Throwable t) {
                            Main.extLog("[HISTORY] proactive fold err: " + t + "\n"
                                    + Main.stackToString(t));
                        }
                        try {
                            HistoryBridge.Result bridge = HistoryBridge.processHistoryResponse(chain.getThisObject());
                            if (bridge.cleaned > 0) Main.log("online history prompts cleaned=" + bridge.cleaned);
                        }
                        catch (Throwable t) {
                            Main.extLog("[HISTORY] pw0 bridge err: " + t + "\n" + Main.stackToString(t));
                        }
                        return r;
                    }
                });
                n++;
            }
            Main.log("installed online history bridge pw0 ctor x" + n);
        } catch (Throwable t) {
            Main.log("installExpertHistoryImagePreserver pw0 failed: " + t);
        }
    }

    private static void scheduleFrozenEditReapply(final ClassLoader cl, final String sid) {
        if (sid == null || sid.length() == 0) return;
        final Object token = new Object();
        FROZEN_EDIT_REAPPLY_TOKENS.put(sid, token);
        Handler handler = Main.currentMainHandler();
        if (handler == null) handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (FROZEN_EDIT_REAPPLY_TOKENS.get(sid) != token) return;
                Thread worker = new Thread(new Runnable() {
                    @Override public void run() {
                        if (!ChatEditorUi.reapplyFrozenEdits(sid)) return;
                        FROZEN_EDIT_REAPPLY_TOKENS.remove(sid, token);
                        final Object session = Main.findNativeSession(sid);
                        Handler mainHandler = Main.currentMainHandler();
                        if (mainHandler != null && session != null) {
                            mainHandler.post(new Runnable() {
                                @Override public void run() {
                                    HookSessionManagement.hydrateFrozenNativeSession(cl, session, sid);
                                }
                            });
                        }
                        Main.log("reapplied editor-frozen rows after native append sid=" + sid);
                    }
                }, "DeePX-Edit-Reapply");
                worker.setDaemon(true);
                worker.start();
            }
        }, 1800L);
    }

    /**
     * A proactive completion is submitted to the real bound conversation so the resulting
     * assistant message remains part of that chat. The synthetic user event is transport-only:
     * remove it from every server-history response and connect its assistant child directly to
     * the previously visible message. Repeating this on every history load keeps the server's
     * canonical branch intact while ensuring the internal event is never rendered or persisted.
     */
    private static int foldProactiveHeartbeatHistory(Object historyResponse) {
        if (historyResponse == null) return 0;
        Object messagesValue = MainReflectionSupport.fieldByName(historyResponse, "b");
        if (!(messagesValue instanceof List)) return 0;
        List messages = (List) messagesValue;
        HashMap<Integer, Integer> hiddenParents = new HashMap<>();
        for (Object message : messages) {
            if (message == null
                    || !"USER".equals(String.valueOf(MainReflectionSupport.fieldByName(message, "h")))
                    || !messageContainsHiddenAgentTransport(message)) continue;
            Integer id = Main.intField(message, "f");
            if (id != null) {
                hiddenParents.put(id, Main.intField(message, "g"));
            }
        }
        if (hiddenParents.isEmpty()) return 0;

        ArrayList kept = new ArrayList(Math.max(0, messages.size() - hiddenParents.size()));
        for (Object message : messages) {
            Integer id = Main.intField(message, "f");
            if (id != null && hiddenParents.containsKey(id)) continue;
            Integer parent = resolveVisibleHeartbeatParent(
                    Main.intField(message, "g"), hiddenParents);
            Integer originalParent = Main.intField(message, "g");
            if (originalParent == null ? parent != null : !originalParent.equals(parent)) {
                Main.forceSetObjectField(message, "g", parent);
            }
            kept.add(message);
        }
        Main.forceSetObjectField(historyResponse, "b", kept);

        Object session = MainReflectionSupport.fieldByName(historyResponse, "a");
        Integer current = Main.intField(session, "d");
        Integer visibleCurrent = resolveVisibleHeartbeatParent(current, hiddenParents);
        if (current == null ? visibleCurrent != null : !current.equals(visibleCurrent)) {
            Main.forceSetObjectField(session, "d", visibleCurrent);
        }
        return hiddenParents.size();
    }

    private static Integer resolveVisibleHeartbeatParent(
            Integer parent, Map<Integer, Integer> hiddenParents) {
        Integer result = parent;
        HashSet<Integer> seen = new HashSet<>();
        while (result != null && hiddenParents.containsKey(result) && seen.add(result)) {
            result = hiddenParents.get(result);
        }
        return result;
    }

    static boolean messageContainsHiddenAgentTransport(Object message) {
        return messageContainsPrivateTransport(message, true);
    }

    static boolean messageContainsPrivateTransport(
            Object message, boolean includeToolResults) {
        List fragmentsValue = HookChatPipeline.messageFragments(message);
        if (fragmentsValue == null) return false;
        for (Object fragment : fragmentsValue) {
            boolean request = HostCompat.simpleNameIs(fragment, "xs7")
                    || "REQUEST".equals(String.valueOf(MainReflectionSupport.fieldByName(fragment, "a")));
            if (!request) continue;
            Object content = MainReflectionSupport.fieldByName(fragment, "c");
            if (!(content instanceof String)) continue;
            // Normal chat requests receive a system prompt that documents EVENT_START, so a
            // broad contains() check would erase the user's real message on the next history
            // sync. Only the post-system-wrapper body of a transport event may be folded.
            String body = HistoryBridge.stripInjectedSystemPrompts(
                    (String) content).trim();
            if (HeartbeatToolProtocol.isCompleteHeartbeatEventBody(body)
                    || (includeToolResults
                    && HeartbeatToolProtocol.isCompleteToolResultBody(body))) {
                return true;
            }
        }
        return false;
    }

    /** 2.3.4 builds the request inside a suspend lambda named x(Object). Capture the lambda's
     * native attachment list and pq/lq session model immediately before it enters transport. */
    void hookSendPointFps234(final ClassLoader cl, final String className) {
        try {
            // These R8 classes live in the default package. "defpackage" is only JADX's
            // source-directory label and must never be included in ClassLoader lookups.
            Class<?> type = cl.loadClass(className);
            Method send = null;
            for (Method candidate : type.getDeclaredMethods()) {
                Class<?>[] params = candidate.getParameterTypes();
                if ("x".equals(candidate.getName()) && params.length == 1
                        && params[0] == Object.class) {
                    send = candidate;
                    break;
                }
            }
            if (send == null) return;
            Main.MODULE.hook(send).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    if (!isExpertRelayEnabled()) return chain.proceed();
                    Main.tlPendingFps.remove();
                    Main.tlPendingModel.remove();
                    try {
                        Object self = chain.getThisObject();
                        List attachments = findSendPointAttachments234(self);
                        String model = findSendPointModel234(self);
                        if (attachments != null && !attachments.isEmpty()) {
                            Main.tlPendingFps.set(attachments);
                            if (model != null) Main.tlPendingModel.set(model);
                            Main.extLog("[RELAY] send-point " + className
                                    + " attachments=" + attachments.size()
                                    + " images=" + countImageFpList(attachments)
                                    + " effectiveModel=" + model);
                        }
                        return chain.proceed();
                    } finally {
                        Main.tlPendingFps.remove();
                        Main.tlPendingModel.remove();
                    }
                }
            });
            Main.log("installed 2.3.4 send-point attachment capture on "
                    + className + ".x");
        } catch (Throwable error) {
            Main.log("2.3.4 send-point capture skipped " + className + ": "
                    + Main.safeThrowableMessage(error));
        }
    }

    private static List findSendPointAttachments234(Object sendPoint) {
        if (sendPoint == null) return null;
        for (Field field : sendPoint.getClass().getDeclaredFields()) {
            try {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                Object value = field.get(sendPoint);
                if (value instanceof List && looksLikeNativeAttachmentList((List) value)) {
                    return (List) value;
                }
                if (value == null) continue;
                for (Method method : value.getClass().getDeclaredMethods()) {
                    if (!"n".equals(method.getName())
                            || method.getParameterTypes().length != 0
                            || !List.class.isAssignableFrom(method.getReturnType())) continue;
                    method.setAccessible(true);
                    Object result = method.invoke(value);
                    if (result instanceof List && looksLikeNativeAttachmentList((List) result)) {
                        return (List) result;
                    }
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static boolean looksLikeNativeAttachmentList(List values) {
        if (values == null) return false;
        if (values.isEmpty()) return true;
        Object first = values.get(0);
        if (first == null) return false;
        return HostCompat.simpleNameIs(first, "fp")
                || MainReflectionSupport.fieldByName(first, "k") instanceof Boolean;
    }

    private static String findSendPointModel234(Object sendPoint) {
        if (sendPoint == null) return null;
        for (Field field : sendPoint.getClass().getDeclaredFields()) {
            try {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                Object value = field.get(sendPoint);
                if (value == null) continue;
                Method getter = value.getClass().getDeclaredMethod("g");
                if (getter.getReturnType() != String.class) continue;
                getter.setAccessible(true);
                Object result = getter.invoke(value);
                String model = result == null ? "" : String.valueOf(result).trim();
                if ("default".equals(model) || "vision".equals(model)
                        || "expert".equals(model)) return model;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    void hookSendPointFps(final ClassLoader cl, final String cls, final boolean directList) {
        try {
            Class<?> c = HostCompat.load(cl, cls);
            final Method y = c.getDeclaredMethod("y", Object.class);
            Main.MODULE.hook(y).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    if (!isExpertRelayEnabled()) return chain.proceed();
                    Main.tlPendingFps.remove();
                    Main.tlPendingModel.remove();
                    try {
                        try {
                            List fps = null;
                            if (directList) {
                                Object v = MainReflectionSupport.fieldByName(chain.getThisObject(), "i");   // fu0.i = List<fp>
                                if (v instanceof List) fps = (List) v;
                            } else {
                                Object kv = MainReflectionSupport.fieldByName(chain.getThisObject(), "f");   // uu0.f = kv 消息
                                Object v = kv == null ? null : Main.invokeNoArg(kv, "l");    // kv.l() = List<fp>
                                if (v instanceof List) fps = (List) v;
                            }
                            int attachmentCount = fps == null ? 0 : fps.size();
                            int imageCount = countImageFpList(fps);
                            if (attachmentCount > 0) {
                                String model = readSendPointModel(chain.getThisObject(), directList);
                                Main.tlPendingFps.set(fps);
                                if (model != null) Main.tlPendingModel.set(model);
                                Main.extLog("[RELAY] send-point " + cls + " attachments="
                                        + attachmentCount + " images=" + imageCount
                                        + " effectiveModel=" + model);
                            }
                        } catch (Throwable t) {
                            Main.extLog("[RELAY] fp/model capture(" + cls + ") err: " + t);
                        }
                        return chain.proceed();
                    } finally {
                        // transport normally consumes both values synchronously; clear leftovers on every exit.
                        Main.tlPendingFps.remove();
                        Main.tlPendingModel.remove();
                    }
                }
            });
            Main.log("installed send-point fp capture on "
                    + HostCompat.name(cls) + ".y");
        } catch (Throwable t) { Main.log("hookSendPointFps " + cls + " failed: " + t); }
    }

    private static String readSendPointModel(Object sendPoint, boolean directList) {
        Object session = MainReflectionSupport.fieldByName(sendPoint, directList ? "g" : "h"); // fu0.g / uu0.h = tp
        Object model = session == null ? null : Main.invokeNoArg(session, "f"); // tp.f() = current model
        return model instanceof String ? (String) model : null;
    }

    static int countImageFpList(List fps) {
        if (fps == null) return 0;
        int n = 0;
        for (Object fp : fps) if (Boolean.TRUE.equals(MainReflectionSupport.fieldByName(fp, "k"))) n++;
        return n;
    }

    private void preserveImagesInHistoryResponse(ClassLoader cl, Object pw0) throws Throwable {
        if (!isExpertRelayEnabled() || pw0 == null) return;
        Object session = MainReflectionSupport.fieldByName(pw0, "a");
        String sid = stringField(session, "a");
        String model = stringField(session, "i");
        if (!HookSessionManagement.isUsableSessionId(sid)) {
            Main.extLog("[HISTORY] pw0 skip: sid 无效 model=" + String.valueOf(model));
            return;
        }

        Object messagesObj = MainReflectionSupport.fieldByName(pw0, "b");
        List messages = messagesObj instanceof List ? (List) messagesObj : null;
        boolean tracked = isTrackedExpertRelaySession(sid);
        boolean marker = historyMessagesContainRelayMarker(messages);
        Main.extLog("[HISTORY] pw0 seen sid=" + sid + " model=" + String.valueOf(model)
                + " tracked=" + tracked + " marker=" + marker
                + " messages=" + (messages == null ? -1 : messages.size())
                + " liveFm8=" + (liveFm8 != null));
        if (!marker) {
            Main.extLog("[HISTORY] pw0 scope skip sid=" + sid + " model=" + String.valueOf(model));
            return;
        }
        if (messages == null) {
            Main.extLog("[HISTORY] pw0 skip: messages 不是 List sid=" + sid
                    + " actual=" + MainReflectionSupport.simpleName(messagesObj));
            return;
        }

        Object fm8 = liveFm8;
        if (fm8 == null) {
            Main.extLog("[HISTORY] pw0 skip: fm8 尚未捕获 sid=" + sid);
            return;
        }
        Map<Integer, Object> localRows = indexLocalRows(readLocalRl8Rows(fm8, sid));
        boolean hasPersisted = relayImageFile(sid) != null && relayImageFile(sid).isFile();
        Main.extLog("[HISTORY] pw0 local sid=" + sid + " rows=" + localRows.size()
                + " persistedImages=" + hasPersisted);
        if (localRows.isEmpty() && !hasPersisted) {
            Main.extLog("[HISTORY] pw0 skip: 本地历史为空且无落盘图片 sid=" + sid);
            return;
        }

        int changed = 0;
        int imageFiles = 0;
        int candidates = 0;
        int detailLogs = 0;
        for (Object message : messages) {
            if (!HostCompat.simpleNameIs(message, "kv")) continue;
            Integer messageId = Main.intField(message, "f");
            if (messageId == null) continue;
            Object serverObj = MainReflectionSupport.fieldByName(message, "t");
            List serverFragments = serverObj instanceof List ? (List) serverObj : Collections.emptyList();
            boolean messageMarker = fragmentListContainsRelayMarker(serverFragments);
            int serverImages = countImageFiles(serverFragments);
            if (!messageMarker) continue;

            Object oldRow = localRows.get(messageId);
            String oldJson = stringField(oldRow, "l");
            List oldFragments = decodeStaticFragments(cl, oldJson);
            int oldImages = oldFragments == null ? 0 : countImageFiles(oldFragments);
            if (oldImages == 0) {
                List persisted = loadPersistedImageFragments(cl, sid);
                if (persisted != null) { oldFragments = persisted; oldImages = countImageFiles(persisted); }
            }
            candidates++;
            if (detailLogs++ < 16) {
                Main.extLog("[HISTORY] pw0 msg sid=" + sid + " id=" + messageId
                        + " relayMarker=" + messageMarker + " serverImages=" + serverImages
                        + " localRow=" + (oldRow != null)
                        + " localJsonLen=" + (oldJson == null ? 0 : oldJson.length())
                        + " imageSrc=" + oldImages);
            }
            if (serverImages > 0 || oldFragments == null || oldImages == 0) continue;

            ArrayList merged = mergeLocalImageFragments(serverFragments, oldFragments);
            if (Main.forceSetObjectField(message, "t", merged)) {
                if (!tracked) {
                    rememberExpertRelaySession(sid, "pw0-verified-merge");
                    tracked = true;
                }
                changed++;
                imageFiles += oldImages;
                Main.extLog("[HISTORY] 内存回填 sid=" + sid + " msg=" + messageId
                        + " images=" + oldImages + " fragments=" + merged.size());
            }
        }
        if (changed > 0) {
            Main.extLog("[HISTORY] ✓ pw0 expert 图片保留完成 sid=" + sid
                    + " messages=" + changed + " images=" + imageFiles);
        } else {
            Main.extLog("[HISTORY] pw0 done sid=" + sid + " candidates=" + candidates
                    + " changed=0");
        }
    }

    private void preserveImagesBeforeLocalWrite(ClassLoader cl, Object fm8, Object[] args) throws Throwable {
        if (!isExpertRelayEnabled() || fm8 == null || args == null || args.length < 7) return;
        Object sessionMeta = args[6];
        String model = stringField(sessionMeta, "k");
        String sid = args[0] instanceof String ? (String) args[0] : null;
        if (!HookSessionManagement.isUsableSessionId(sid)) {
            Main.extLog("[HISTORY] fm8 skip: sid 无效 model=" + String.valueOf(model));
            return;
        }
        List incomingRows = args[4] instanceof List ? (List) args[4] : null;
        boolean tracked = isTrackedExpertRelaySession(sid);
        Map<Object, List> decodedIncoming = new java.util.IdentityHashMap<>();
        boolean marker = false;
        if (incomingRows != null) {
            for (Object incoming : incomingRows) {
                if (!isHistoryPersistenceRow(incoming)) continue;
                String json = stringField(incoming, "l");
                if (!serializedMayContainRelayMarker(json)) continue;
                List fragments = decodeStaticFragments(cl, json);
                if (fragmentListContainsRelayMarker(fragments)) {
                    decodedIncoming.put(incoming, fragments);
                    marker = true;
                }
            }
        }
        Main.extLog("[HISTORY] fm8 seen sid=" + sid + " model=" + String.valueOf(model)
                + " tracked=" + tracked + " marker=" + marker
                + " incoming=" + (incomingRows == null ? -1 : incomingRows.size()));
        if (incomingRows == null) {
            Main.extLog("[HISTORY] fm8 skip: incoming 不是 List sid=" + sid
                    + " actual=" + MainReflectionSupport.simpleName(args[4]));
            return;
        }
        if (!marker) {
            Main.extLog("[HISTORY] fm8 scope skip sid=" + sid + " model=" + String.valueOf(model));
            return;
        }

        Map<Integer, Object> localRows = indexLocalRows(readLocalRl8Rows(fm8, sid));
        boolean hasPersisted = relayImageFile(sid) != null && relayImageFile(sid).isFile();
        Main.extLog("[HISTORY] fm8 local sid=" + sid + " rows=" + localRows.size()
                + " persistedImages=" + hasPersisted);
        if (localRows.isEmpty() && !hasPersisted) {
            Main.extLog("[HISTORY] fm8 skip: 本地历史为空且无落盘图片 sid=" + sid);
            return;
        }
        int changed = 0;
        int candidates = 0;
        int detailLogs = 0;
        for (Object incoming : incomingRows) {
            if (!isHistoryPersistenceRow(incoming)) continue;
            Integer messageId = Main.intField(incoming, "a");
            if (messageId == null) continue;
            List serverFragments = decodedIncoming.get(incoming);
            if (serverFragments == null) continue;
            boolean messageMarker = fragmentListContainsRelayMarker(serverFragments);
            int serverImages = countImageFiles(serverFragments);

            Object oldRow = localRows.get(messageId);
            String oldJson = stringField(oldRow, "l");
            List oldFragments = decodeStaticFragments(cl, oldJson);
            int oldImages = oldFragments == null ? 0 : countImageFiles(oldFragments);
            if (oldImages == 0) {
                List persisted = loadPersistedImageFragments(cl, sid);
                if (persisted != null) { oldFragments = persisted; oldImages = countImageFiles(persisted); }
            }
            candidates++;
            if (detailLogs++ < 16) {
                Main.extLog("[HISTORY] fm8 msg sid=" + sid + " id=" + messageId
                        + " relayMarker=" + messageMarker + " serverImages=" + serverImages
                        + " localRow=" + (oldRow != null)
                        + " localJsonLen=" + (oldJson == null ? 0 : oldJson.length())
                        + " imageSrc=" + oldImages);
            }
            if (!messageMarker || serverImages > 0 || oldFragments == null || oldImages == 0) continue;

            ArrayList merged = mergeLocalImageFragments(serverFragments, oldFragments);
            String mergedJson = encodeStaticFragments(cl, merged);
            if (mergedJson == null || mergedJson.length() == 0) continue;
            if (Main.forceSetObjectField(incoming, "l", mergedJson)) {
                if (!tracked) {
                    rememberExpertRelaySession(sid, "fm8-verified-merge");
                    tracked = true;
                }
                changed++;
                Main.extLog("[HISTORY] 落库回填 sid=" + sid + " msg=" + messageId
                        + " images=" + oldImages + " jsonLen=" + mergedJson.length());
            }
        }
        if (changed > 0) {
            Main.extLog("[HISTORY] ✓ fm8 expert 图片落库保护完成 sid=" + sid + " messages=" + changed);
        } else {
            Main.extLog("[HISTORY] fm8 done sid=" + sid + " candidates=" + candidates
                    + " changed=0");
        }
    }

    private static boolean historyMessagesContainRelayMarker(List messages) {
        if (messages == null) return false;
        for (Object message : messages) {
            Object fragments = MainReflectionSupport.fieldByName(message, "t");
            if (fragments instanceof List && fragmentListContainsRelayMarker((List) fragments)) return true;
        }
        return false;
    }

    private static boolean serializedMayContainRelayMarker(String json) {
        return json != null && (json.contains(RELAY_PROMPT_MARKER)
                || json.contains(RELAY_PROMPT_MARKER_EN) || json.contains("\\u3010"));
    }

    private static boolean fragmentListContainsRelayMarker(List fragments) {
        if (fragments == null) return false;
        for (Object fragment : fragments) {
            boolean request = HostCompat.simpleNameIs(fragment, "xs7")
                    || "REQUEST".equals(String.valueOf(MainReflectionSupport.fieldByName(fragment, "a")));
            if (!request) continue;
            Object content = MainReflectionSupport.fieldByName(fragment, "c");
            if (content instanceof String && (((String) content).contains(RELAY_PROMPT_MARKER)
                    || ((String) content).contains(RELAY_PROMPT_MARKER_EN))) return true;
        }
        return false;
    }

    private static File relaySessionMarkerFile(String sid) {
        if (!HookSessionManagement.isUsableSessionId(sid) || ".".equals(sid) || "..".equals(sid) || sid.length() > 160
                || !sid.matches("[A-Za-z0-9._-]+")) return null;
        return new File(EXPERT_RELAY_SESSION_DIR, sid);
    }

    private static boolean isTrackedExpertRelaySession(String sid) {
        if (!HookSessionManagement.isUsableSessionId(sid)) return false;
        synchronized (expertRelaySessionIds) {
            if (expertRelaySessionIds.contains(sid)) return true;
        }
        File marker = relaySessionMarkerFile(sid);
        if (marker == null || !marker.isFile()) return false;
        synchronized (expertRelaySessionIds) {
            expertRelaySessionIds.add(sid);
        }
        return true;
    }

    private static void rememberExpertRelaySession(String sid, String source) {
        if (!HookSessionManagement.isUsableSessionId(sid)) return;
        synchronized (expertRelaySessionIds) {
            expertRelaySessionIds.add(sid);
        }
        File marker = relaySessionMarkerFile(sid);
        if (marker == null) {
            Main.extLog("[HISTORY] relay sid 仅内存登记（文件名不安全） source=" + source
                    + " sid=" + MainReflectionSupport.truncateForLog(sid, 80));
            return;
        }
        try {
            Main.overwriteTextFile(marker.getAbsolutePath(), sid);
            Main.extLog("[HISTORY] relay sid 已登记 source=" + source + " sid=" + sid);
        } catch (Throwable t) {
            Main.extLog("[HISTORY] relay sid 落盘失败 source=" + source + " sid=" + sid + ": " + t);
        }
    }

    private static File relayImageFile(String sid) {
        if (!HookSessionManagement.isUsableSessionId(sid) || ".".equals(sid) || "..".equals(sid) || sid.length() > 160
                || !sid.matches("[A-Za-z0-9._-]+")) return null;
        return new File(RELAY_IMAGE_DIR, sid + ".json");
    }

    private void persistRelayImages(ClassLoader cl, String sid, Object expertReq) {
        List fps = HookChatPipeline.ew0Fps.remove(expertReq);
        if (fps == null) { Main.extLog("[HISTORY] persistImages skip: 无捕获 fp sid=" + sid); return; }
        ArrayList imageFps = new ArrayList();
        for (Object fp : fps) if (Boolean.TRUE.equals(MainReflectionSupport.fieldByName(fp, "k"))) imageFps.add(fp);
        if (imageFps.isEmpty()) { Main.extLog("[HISTORY] persistImages skip: 无图片 fp sid=" + sid); return; }
        File out = relayImageFile(sid);
        if (out == null) { Main.extLog("[HISTORY] persistImages skip: sid 文件名不安全 sid=" + MainReflectionSupport.truncateForLog(sid, 80)); return; }
        try {
            Class<?> fileFragment = HostCompat.load(cl, "rs7");
            Constructor<?> ctor;
            Object frag;
            if (HostCompat.isV230()) {
                ctor = fileFragment.getDeclaredConstructor(
                        int.class, String.class, List.class);
                ctor.setAccessible(true);
                frag = ctor.newInstance(1, "FILE", imageFps);
            } else {
                ctor = fileFragment.getDeclaredConstructor(List.class);
                ctor.setAccessible(true);
                frag = ctor.newInstance(imageFps);
            }
            String json = encodeStaticFragments(cl, java.util.Collections.singletonList(frag));
            if (json == null || json.length() == 0) { Main.extLog("[HISTORY] persistImages 编码失败 sid=" + sid); return; }
            File dir = out.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            Main.overwriteTextFile(out.getAbsolutePath(), json);
            Main.extLog("[HISTORY] persistImages ✓ sid=" + sid + " images=" + imageFps.size()
                    + " jsonLen=" + json.length());
            for (int i = 0; i < imageFps.size(); i++) {
                Main.extLog("[HISTORY] persistImages fp[" + i + "]=" + MainReflectionSupport.summarizeFp(imageFps.get(i)));
            }
        } catch (Throwable t) { Main.extLog("[HISTORY] persistImages err sid=" + sid + ": " + t); }
    }

    private static List loadPersistedImageFragments(ClassLoader cl, String sid) {
        File f = relayImageFile(sid);
        if (f == null || !f.isFile()) return null;
        try {
            String json = Main.readSmallText(f.getAbsolutePath());
            List frags = decodeStaticFragments(cl, json);
            if (frags == null || countImageFiles(frags) == 0) return null;
            return frags;
        } catch (Throwable t) { Main.extLog("[HISTORY] loadPersistedImages err sid=" + sid + ": " + t); return null; }
    }

    private static ArrayList readLocalRl8Rows(Object fm8, String sid) throws Throwable {
        if (fm8 == null || sid == null) return new ArrayList();
        Method tableForSession = fm8.getClass().getDeclaredMethod("a", String.class);
        tableForSession.setAccessible(true);
        Object sl8 = tableForSession.invoke(fm8, sid);
        Object table = MainReflectionSupport.fieldByName(sl8, "b");
        if (table == null) return new ArrayList();

        Object binding = MainReflectionSupport.fieldByName(table, "d");
        if (binding == null) return new ArrayList();
        Method allColumns = binding.getClass().getDeclaredMethod("c");
        allColumns.setAccessible(true);
        Object columns = allColumns.invoke(binding);
        if (columns == null || !columns.getClass().isArray()) return new ArrayList();

        Method selectFactory = table.getClass().getDeclaredMethod("U");
        selectFactory.setAccessible(true);
        Object select = selectFactory.invoke(table);
        Method selectColumns = select.getClass().getDeclaredMethod("z", columns.getClass());
        selectColumns.setAccessible(true);
        selectColumns.invoke(select, new Object[]{columns});
        Method allRows = select.getClass().getDeclaredMethod("x");
        allRows.setAccessible(true);
        Object rows = allRows.invoke(select);
        return rows instanceof ArrayList ? (ArrayList) rows : new ArrayList();
    }

    private static Map<Integer, Object> indexLocalRows(List rows) {
        HashMap<Integer, Object> out = new HashMap<>();
        if (rows == null) return out;
        for (Object row : rows) {
            Integer id = Main.intField(row, "a");
            if (id != null) out.put(id, row);
        }
        return out;
    }

    private static List decodeStaticFragments(ClassLoader cl, String json) {
        if (cl == null || json == null || json.trim().length() == 0) return null;
        try {
            Class<?> ch4 = HostCompat.load(cl, "ch4");
            Class<?> x94 = HostCompat.load(cl, "x94");
            Field jsonField = x94.getDeclaredField("a");
            jsonField.setAccessible(true);
            Object jsonCodec = jsonField.get(null);
            Class<?> xv0 = HostCompat.load(cl, "xv0");
            Field serializerField = xv0.getDeclaredField("a");
            serializerField.setAccessible(true);
            Object serializer = serializerField.get(null);
            Method decode = jsonCodec.getClass().getMethod("b", ch4, String.class);
            decode.setAccessible(true);
            Object wrapper = decode.invoke(jsonCodec, serializer, json);
            Object list = MainReflectionSupport.fieldByName(wrapper, "a");
            return list instanceof List ? (List) list : null;
        } catch (Throwable t) {
            Main.extLog("[HISTORY] fragments decode skip: " + t + " json=" + MainReflectionSupport.truncateForLog(json, 180));
            return null;
        }
    }

    private static String encodeStaticFragments(ClassLoader cl, List fragments) {
        if (cl == null || fragments == null) return null;
        try {
            Class<?> ch4 = HostCompat.load(cl, "ch4");
            Class<?> x94 = HostCompat.load(cl, "x94");
            Field jsonField = x94.getDeclaredField("a");
            jsonField.setAccessible(true);
            Object jsonCodec = jsonField.get(null);
            Class<?> xv0 = HostCompat.load(cl, "xv0");
            Field serializerField = xv0.getDeclaredField("a");
            serializerField.setAccessible(true);
            Object serializer = serializerField.get(null);
            Class<?> zv0 = HostCompat.load(cl, "zv0");
            Constructor<?> wrapperCtor = zv0.getDeclaredConstructor(List.class);
            wrapperCtor.setAccessible(true);
            Object wrapper = wrapperCtor.newInstance(fragments);
            Method encode = jsonCodec.getClass().getMethod("c", ch4, Object.class);
            encode.setAccessible(true);
            return String.valueOf(encode.invoke(jsonCodec, serializer, wrapper));
        } catch (Throwable t) {
            Main.extLog("[HISTORY] fragments encode skip: " + t);
            return null;
        }
    }

    private static ArrayList mergeLocalImageFragments(List serverFragments, List oldFragments) {
        ArrayList merged = new ArrayList();
        if (serverFragments != null) merged.addAll(serverFragments);
        stripRelayDescriptionText(merged);
        HashSet<Integer> usedIds = new HashSet<>();
        int nextId = 1;
        for (Object fragment : merged) {
            Integer id = Main.intField(fragment, "b");
            if (id == null) continue;
            usedIds.add(id);
            if (id.intValue() >= nextId) nextId = id.intValue() + 1;
        }
        int insertAt = 0;
        while (insertAt < merged.size() && isFileFragment(merged.get(insertAt))) insertAt++;
        if (oldFragments != null) {
            for (Object fragment : oldFragments) {
                if (!retainOnlyImageFiles(fragment)) continue;
                Integer id = Main.intField(fragment, "b");
                if (id == null || usedIds.contains(id)) {
                    while (usedIds.contains(Integer.valueOf(nextId))) nextId++;
                    id = Integer.valueOf(nextId++);
                    if (!Main.forceSetObjectField(fragment, "b", id)) continue;
                }
                usedIds.add(id);
                merged.add(insertAt++, fragment);
            }
        }
        return merged;
    }

    private static void stripRelayDescriptionText(List fragments) {
        if (fragments == null) return;
        for (Object fragment : fragments) {
            boolean request = HostCompat.simpleNameIs(fragment, "xs7")
                    || "REQUEST".equals(String.valueOf(MainReflectionSupport.fieldByName(fragment, "a")));
            if (!request) continue;
            Object content = MainReflectionSupport.fieldByName(fragment, "c");
            if (!(content instanceof String)) continue;
            String text = (String) content;
            int zhIndex = text.indexOf(RELAY_PROMPT_MARKER);
            int enIndex = text.indexOf(RELAY_PROMPT_MARKER_EN);
            int idx = zhIndex < 0 ? enIndex : (enIndex < 0 ? zhIndex : Math.min(zhIndex, enIndex));
            if (idx < 0) continue;
            String kept = text.substring(0, idx);
            kept = stripInjectedSystemPrompt(kept);
            int nl = kept.length();
            while (nl > 0 && (kept.charAt(nl - 1) == '\n' || kept.charAt(nl - 1) == '\r'
                    || kept.charAt(nl - 1) == ' ')) nl--;
            kept = kept.substring(0, nl);
            Main.forceSetObjectField(fragment, "c", kept);
        }
    }

    private static String stripInjectedSystemPrompt(String text) {
        return HistoryBridge.stripInjectedSystemPrompts(text);
    }

    private static boolean isHistoryPersistenceRow(Object row) {
        if (row == null) return false;
        String name = MainReflectionSupport.simpleName(row);
        if ("rl8".equals(name) || "sl8".equals(name)) return true;
        return Main.intField(row, "a") != null && MainReflectionSupport.fieldByName(row, "l") instanceof String;
    }

    private static boolean retainOnlyImageFiles(Object fragment) {
        if (!isFileFragment(fragment)) return false;
        Object filesObj = MainReflectionSupport.fieldByName(fragment, "c");
        if (!(filesObj instanceof List)) return false;
        List files = (List) filesObj;
        ArrayList images = new ArrayList();
        for (Object file : files) {
            if (Boolean.TRUE.equals(MainReflectionSupport.fieldByName(file, "k"))) images.add(file);
        }
        if (images.isEmpty()) return false;
        return images.size() == files.size() || Main.forceSetObjectField(fragment, "c", images);
    }

    private static int countImageFiles(List fragments) {
        if (fragments == null) return 0;
        int count = 0;
        for (Object fragment : fragments) count += countImageFilesInFragment(fragment);
        return count;
    }

    private static int countImageFilesInFragment(Object fragment) {
        if (!isFileFragment(fragment)) return 0;
        Object filesObj = MainReflectionSupport.fieldByName(fragment, "c");
        if (!(filesObj instanceof List)) return 0;
        int count = 0;
        for (Object file : (List) filesObj) {
            if (Boolean.TRUE.equals(MainReflectionSupport.fieldByName(file, "k"))) count++;
        }
        return count;
    }

    private static boolean isFileFragment(Object fragment) {
        if (fragment == null) return false;
        if (HostCompat.simpleNameIs(fragment, "rs7")) return true;
        return "FILE".equals(String.valueOf(MainReflectionSupport.fieldByName(fragment, "a")));
    }

    // 已登记待中继的冷 Flow(b41 实例) -> {expertReq, r92}。等下游 collect(b41.b) 时才跑中继。
    final java.util.Map<Object, Object[]> relayFlowMap =
            new java.util.IdentityHashMap<Object, Object[]>();

    // hook b41.b(q03,uz1)=Flow.collect。返回类型是 Object，返回真实 Flow 不会触发返回值强转。
    // 仅当 this 是已登记的 expert 带图冷 Flow 时介入；否则原样放行(热路径，identity 命中开销 O(1))。
    void installExpertFlowCollectHook(ClassLoader cl) {
        try {
            Class<?> b41 = HostCompat.isV241()
                    ? cl.loadClass("d81") : HostCompat.load(cl, "b41");
            Class<?> q03 = HostCompat.isV241()
                    ? cl.loadClass("ja3") : HostCompat.load(cl, "q03");
            Method bColl = null;
            for (Method m : b41.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (m.getName().equals("b") && p.length == 2 && p[0] == q03) { bColl = m; break; }
            }
            if (bColl == null) { Main.log("expert flow collect hook: b41.b(q03,uz1) not found"); return; }
            Main.MODULE.hook(bColl).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    Object self = chain.getThisObject();
                    Object[] entry;
                    synchronized (relayFlowMap) { entry = relayFlowMap.remove(self); }
                    if (entry == null) return chain.proceed();          // 非中继流，原样放行
                    Object[] a = chain.getArgs().toArray();
                    Object collector = a.length > 0 ? a[0] : null;
                    Object cont = a.length > 1 ? a[1] : null;
                    final Object expertReq = entry[0];
                    final Object r92 = entry[1];
                    try {
                        if (Looper.getMainLooper() != null
                                && Looper.getMainLooper().getThread() == Thread.currentThread()) {
                            // 主线程不能阻塞跑中继(7s 网络=ANR)，直接转发原流(服务端会拒但不闪退)
                            Main.extLog("[RELAY] collect 在主线程，跳过中继直接转发原 Flow");
                            return chain.proceed();
                        }
                        Main.extLog("[RELAY] collect 命中(flow=" + System.identityHashCode(self)
                                + " thread=" + Thread.currentThread().getName() + ")，开始中继");
                        runExpertImageRelay(r92, expertReq);
                        // 用改写后的 expertReq 重建一个新冷 Flow，collect 它(而不是带图的原流)
                        Object freshFlow = null;
                        Method bM = null;
                        for (Method mm : r92.getClass().getDeclaredMethods()) {
                            if (mm.getName().equals("b") && mm.getParameterTypes().length == 2) { bM = mm; break; }
                        }
                        if (bM != null) { bM.setAccessible(true); freshFlow = bM.invoke(r92, expertReq, null); }
                        if (freshFlow == null) {
                            Main.extLog("[RELAY] 重取 expert Flow 失败，转发原 Flow");
                            return chain.proceed();
                        }
                        // freshFlow 也是 b41，反射调用其 b() 会再次进本 hook；但它未登记 → 直接放行原始 collect
                        return bCollInvoke(chain, freshFlow, collector, cont);
                    } catch (Throwable t) {
                        Main.extLog("[RELAY] collect 中继异常，转发原 Flow: " + t + "\n" + Main.stackToString(t));
                        return chain.proceed();
                    }
                }
            });
            Main.log("installed expert flow collect hook on b41.b x1");
        } catch (Throwable t) { Main.log("installExpertFlowCollectHook failed: " + t); }
    }

    private Object bCollInvoke(Chain chain, Object flow, Object collector, Object cont) throws Throwable {
        Method m = (Method) chain.getExecutable();
        m.setAccessible(true);
        return m.invoke(flow, collector, cont);
    }

    private String describeOneImage(Object r92, ClassLoader cl, Object expertReq,
                                    List fileIds, String label, long t0) {
        String sid = null;
        try {
            Object pow = mintCompletionPow(cl, Main.liveQ71);
            if (!(pow instanceof String) || ((String) pow).length() == 0) {
                Main.extLog("[RELAY]" + label + " 铸 PoW 失败；abort"); return null;
            }
            sid = createThrowawaySession(cl, r92);
            if (sid == null) { Main.extLog("[RELAY]" + label + " 建临时会话失败；abort"); return null; }
            Main.extLog("[RELAY]" + label + " 临时会话=" + sid
                    + " (setup " + (System.currentTimeMillis() - t0) + "ms)");
            Object visionReq = shallowCloneEw0(expertReq);
            if (visionReq == null) { Main.extLog("[RELAY]" + label + " clone 失败；abort"); return null; }
            setFieldByName(visionReq, "a", sid);
            setFieldByName(visionReq, "b", null);
            setFieldByName(visionReq, "c", visionDescribePrompt());
            setFieldByName(visionReq, "i", "vision");
            setFieldByName(visionReq, "e", Boolean.FALSE);
            setFieldByName(visionReq, "f", Boolean.FALSE);
            setFieldByName(visionReq, "k", pow);
            if (fileIds != null) setFieldByName(visionReq, "d", new ArrayList(fileIds));

            Method bM = null;
            for (Method m : r92.getClass().getDeclaredMethods()) {
                if (m.getName().equals("b") && m.getParameterTypes().length == 2) { bM = m; break; }
            }
            if (bM == null) { Main.extLog("[RELAY]" + label + " r92.b 未找到；abort"); return null; }
            bM.setAccessible(true);
            Object flow = bM.invoke(r92, visionReq, null);
            if (flow == null) { Main.extLog("[RELAY]" + label + " vision r92.b 返回 null；abort"); return null; }
            String desc = collectFlow(cl, flow);
            Main.extLog("[RELAY]" + label + " 描述 len=" + (desc == null ? 0 : desc.length())
                    + " total=" + (System.currentTimeMillis() - t0) + "ms : "
                    + MainReflectionSupport.truncateForLog(String.valueOf(desc), 240));
            return desc;
        } catch (Throwable t) {
            Main.extLog("[RELAY]" + label + " describeOneImage threw: " + t);
            return null;
        } finally {
            if (sid != null) {
                try {
                    boolean del = Main.MODULE.deleteThrowawaySession(cl, r92, sid);
                    Main.extLog("[RELAY]" + label + " 删除临时会话 " + sid + " -> " + del);
                } catch (Throwable t) { Main.extLog("[RELAY]" + label + " 删除临时会话失败: " + t); }
            }
        }
    }

    private String describeImagesParallel(final Object r92, final ClassLoader cl,
                                          final Object expertReq, final List<String> fileIds,
                                          final long t0) {
        final int n = fileIds.size();
        final String[] results = new String[n];
        Thread[] threads = new Thread[n];
        for (int i = 0; i < n; i++) {
            final int idx = i;
            final String fileId = fileIds.get(i);
            threads[i] = new Thread(new Runnable() {
                public void run() {
                    results[idx] = describeOneImage(r92, cl, expertReq,
                            java.util.Collections.singletonList(fileId), " 图" + (idx + 1), t0);
                }
            });
            threads[i].start();
        }
        for (int i = 0; i < n; i++) {
            try { threads[i].join(120000); } catch (Throwable ignored) {}
        }
        StringBuilder sb = new StringBuilder();
        int ok = 0;
        for (int i = 0; i < n; i++) {
            String d = results[i];
            if (d == null || d.trim().length() == 0) continue;
            if (sb.length() > 0) sb.append("\n\n");
            sb.append("图").append(i + 1).append("：\n").append(d.trim());
            ok++;
        }
        Main.extLog("[RELAY] 并行描述完成 images=" + n + " ok=" + ok
                + " total=" + (System.currentTimeMillis() - t0) + "ms");
        return sb.length() > 0 ? sb.toString() : null;
    }

    private void runExpertImageRelay(Object r92, Object expertReq) throws Throwable {
        if (r92 == null) { Main.extLog("[RELAY] no live r92; abort"); return; }
        final ClassLoader cl = r92.getClass().getClassLoader();
        long t0 = System.currentTimeMillis();

        if (Main.liveQ71 == null) { Main.extLog("[RELAY] liveQ71 未捕获；abort（保持带图 expert 不动）"); return; }

        Object dOld0 = MainReflectionSupport.fieldByName(expertReq, "d");
        ArrayList<String> fileIds = new ArrayList<String>();
        if (dOld0 instanceof List) {
            for (Object o : (List) dOld0) if (o != null) fileIds.add(String.valueOf(o));
        }

        String desc;
        if (fileIds.size() <= 1) {
            desc = describeOneImage(r92, cl, expertReq,
                    fileIds.isEmpty() ? null : fileIds, "", t0);
        } else {
            desc = describeImagesParallel(r92, cl, expertReq, fileIds, t0);
        }

        if (desc != null && desc.trim().length() > 0) {
            Object cOld = MainReflectionSupport.fieldByName(expertReq, "c");
            Object dOld = MainReflectionSupport.fieldByName(expertReq, "d");
            ArrayList filesOld = dOld instanceof List ? new ArrayList((List) dOld) : null;
            String newC = String.valueOf(cOld) + "\n\n" + relayPromptMarker() + "\n" + desc.trim();
            setFieldByName(expertReq, "c", newC);
            if (dOld instanceof java.util.List) {
                try { ((java.util.List) dOld).clear(); }
                catch (Throwable t) { setFieldByName(expertReq, "d", new java.util.ArrayList()); }
            } else {
                setFieldByName(expertReq, "d", new java.util.ArrayList());
            }
            Object cAfter = MainReflectionSupport.fieldByName(expertReq, "c");
            Object dAfter = MainReflectionSupport.fieldByName(expertReq, "d");
            boolean promptOk = newC.equals(cAfter);
            boolean filesOk = dAfter instanceof List && ((List) dAfter).isEmpty();
            if (promptOk && filesOk) {
                String relaySid = stringField(expertReq, "a");
                rememberExpertRelaySession(relaySid, "relay-success");
                try { persistRelayImages(cl, relaySid, expertReq); }
                catch (Throwable t) { Main.extLog("[RELAY] persistRelayImages err: " + t); }
                Main.extLog("[RELAY] ✓ expert 已改写为纯文本，newPromptLen=" + newC.length()
                        + " 文件已清空");
            } else {
                setFieldByName(expertReq, "c", cOld);
                if (dOld instanceof List) {
                    try {
                        ((List) dOld).clear();
                        ((List) dOld).addAll(filesOld);
                        setFieldByName(expertReq, "d", dOld);
                    } catch (Throwable ignored) {
                        setFieldByName(expertReq, "d", filesOld);
                    }
                } else {
                    setFieldByName(expertReq, "d", dOld);
                }
                Main.extLog("[RELAY] expert 改写校验失败，已尝试恢复原请求 promptOk="
                        + promptOk + " filesOk=" + filesOk);
            }
        } else {
            Main.extLog("[RELAY] 描述为空；保持带图 expert 不动（服务端仍会拒，与未开启前一致）");
        }
    }

    public static Throwable deepestCause(Throwable throwable) {
        if (throwable == null) return null;
        Throwable value = throwable;
        HashSet<Throwable> seen = new HashSet<>();
        while (value.getCause() != null && value.getCause() != value && seen.add(value)) {
            value = value.getCause();
        }
        return value;
    }

    // NativeSessionEndpoint now lives on Main (see Main's "Fields moved back" block) because
    // protected-payload-src declares locals of type `Main.NativeSessionEndpoint`.

    private static boolean hasStringRequestConstructor(Class<?> type) {
        if (type == null) return false;
        try {
            type.getDeclaredConstructor(String.class);
            return true;
        } catch (Throwable ignored) {
            // code257's ChatSessionDeleteRequest is a Kotlin data class with
            // (mask, String id, ArrayList ids) instead of a Java String convenience ctor.
            if (HostCompat.isV241()) {
                for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                    Class<?>[] p = constructor.getParameterTypes();
                    if (p.length == 3 && p[0] == int.class && p[1] == String.class
                            && java.util.List.class.isAssignableFrom(p[2])) return true;
                }
            }
            return false;
        }
    }

    static boolean isSessionDeleteRequestType(Class<?> type) {
        if (type == null) return false;
        if (HostCompat.isV241()) {
            // code257 contains unrelated request/enum types with String-shaped constructors.
            // The reviewed delete endpoint accepts jh1 only; structural guessing selected
            // to9.w(de1, Continuation) in affected logs and passed the session id as a String.
            return HostCompat.localApiSessionDeleteRequestClass().equals(type.getName());
        }
        return hasStringRequestConstructor(type);
    }

    /**
     * Resolves the create/delete service by its endpoint shape instead of trusting field {@code b}
     * alone. That field is the session API in the supported mainland build, but some Play/R8
     * generations assign it to an unrelated model also named i91. A valid session service has a
     * suspend create method and a sibling suspend delete method whose request owns a String id.
     */
    public static Main.NativeSessionEndpoint resolveSessionCreateEndpoint(Object transport,
                                                                        String preferred) {
        if (transport == null) return null;
        ArrayList<Object> services = new ArrayList<>();
        Object fast = MainReflectionSupport.fieldByName(transport, "b");
        if (fast != null) services.add(fast);
        for (Class<?> owner = transport.getClass(); owner != null; owner = owner.getSuperclass()) {
            for (Field field : owner.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(transport);
                    if (value != null && value != transport && !services.contains(value)) {
                        services.add(value);
                    }
                } catch (Throwable ignored) {}
            }
        }
        Main.NativeSessionEndpoint best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Object service : services) {
            Method[] methods;
            try { methods = service.getClass().getDeclaredMethods(); }
            catch (Throwable ignored) { continue; }
            boolean hasDeleteShape = false;
            for (Method candidate : methods) {
                Class<?>[] p = candidate.getParameterTypes();
                if (p.length == 2 && isSessionDeleteRequestType(p[0])) {
                    hasDeleteShape = true;
                    break;
                }
            }
            if (!hasDeleteShape) continue;
            for (Method candidate : methods) {
                if (candidate.getParameterTypes().length != 1) continue;
                String name = candidate.getName();
                int score = name.equals(preferred) ? 100 : -100;
                // Known create symbols across the inspected 2.2/2.3 mainland and Play services.
                if ("a".equals(name) || "u".equals(name) || "p".equals(name)) score += 50;
                if (service == fast) score += 10;
                if (score > bestScore) {
                    bestScore = score;
                    best = new Main.NativeSessionEndpoint(service, candidate);
                }
            }
        }
        return bestScore >= 0 ? best : null;
    }

    private String createThrowawaySession(ClassLoader cl, Object r92) {
        try {
            String createName = HostCompat.localApiSessionCreateMethod();
            Main.NativeSessionEndpoint endpoint = resolveSessionCreateEndpoint(r92, createName);
            if (endpoint == null) {
                Object legacy = MainReflectionSupport.fieldByName(r92, "b");
                Main.localApiLastSessionError = "verified session create endpoint missing; transport="
                        + r92.getClass().getName() + ", fieldB="
                        + (legacy == null ? "null" : legacy.getClass().getName())
                        + ", preferred=" + createName;
                Main.extLog("[RELAY] session create method 未找到: "
                        + Main.localApiLastSessionError);
                return null;
            }
            Object res = HookAccountLoginSecurity.INSTANCE.driveSuspend(cl, endpoint.method, endpoint.service, new Object[0]);
            String sid = extractSessionId(res);
            if (sid == null) {
                Main.localApiLastSessionError = "create response contained no session id: "
                        + MainReflectionSupport.truncateForLog(MainReflectionSupport.deepDump(res, 4), 800);
            } else {
                Main.localApiLastSessionError = "ok";
            }
            return sid;
        } catch (Throwable t) {
            Main.localApiLastSessionError = Main.safeThrowableMessage(t);
            Main.extLog("[RELAY] createThrowawaySession err: " + Main.localApiLastSessionError
                    + "\n" + Main.stackToString(deepestCause(t)));
            return null;
        }
    }

    public static String extractSessionId(Object response) {
        if (response == null) return null;
        if (response instanceof String) {
            return extractSessionId((String) response);
        }
        Object rawBody = MainReflectionSupport.fieldByName(response, "j");
        if (rawBody instanceof String) {
            String rawId = extractSessionId((String) rawBody);
            if (HookSessionManagement.isUsableSessionId(rawId)) return rawId;
        }

        // All supported hosts decode this endpoint into:
        // ServerBodyResponse.c -> BizDataWrapper.c -> ChatSessionCreateBizData.a
        // -> ServerChatSession.a (id). Class names change in every R8 generation, while this
        // serialized field chain has remained stable from 2.2.0 through both 2.3.4 channels.
        String[][] paths = {
                {"c", "c", "a", "a"},
                {"c", "a", "a"},
                {"a", "a"}
        };
        for (String[] path : paths) {
            Object value = response;
            for (String field : path) {
                value = MainReflectionSupport.fieldByName(value, field);
                if (value == null) break;
            }
            if (value instanceof String && HookSessionManagement.isUsableSessionId((String) value)) {
                return (String) value;
            }
        }
        return null;
    }

    public static String extractSessionId(String body) {
        if (body == null) return null;
        int cs = body.indexOf("\"chat_session\"");
        if (cs < 0) return null;
        int idk = body.indexOf("\"id\":\"", cs);
        if (idk < 0) return null;
        int start = idk + 6;
        int end = body.indexOf('"', start);
        if (end < 0) return null;
        String id = body.substring(start, end);
        return id.length() > 0 ? id : null;
    }

    private Object mintCompletionPow(ClassLoader cl, Object q71) throws Throwable {
        Method jm = null;
        for (Method m : q71.getClass().getDeclaredMethods()) {
            if (m.getName().equals("j") && m.getParameterTypes().length == 1) { jm = m; break; }
        }
        if (jm == null) { Main.extLog("[VP] q71.j not found"); return null; }
        Object res = HookAccountLoginSecurity.INSTANCE.driveSuspend(cl, jm, q71, new Object[0]);
        Main.extLog("[VP] q71.j resumed: " + MainReflectionSupport.deepDump(res, 2));
        if (res == null) return null;
        Object a = MainReflectionSupport.fieldByName(res, "a");   // b36{a=base64 pow, b=error}
        return a;
    }

    private Object shallowCloneEw0(Object src) {
        if (src == null) return null;
        try {
            Class<?> cls = src.getClass();
            Class<?> unsafeCls = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = unsafeCls.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Object unsafe = theUnsafe.get(null);
            Method alloc = unsafeCls.getMethod("allocateInstance", Class.class);
            Object dst = alloc.invoke(unsafe, cls);
            for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    try { f.set(dst, f.get(src)); } catch (Throwable ignored) {}
                }
            }
            return dst;
        } catch (Throwable t) {
            Main.extLog("[VP] shallowCloneEw0 failed: " + t);
            return null;
        }
    }

    private static void setFieldByName(Object obj, String name, Object val) {
        if (obj == null) return;
        for (Class<?> c = obj.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(obj, val);
                return;
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable t) { return; }
        }
    }

    private String collectFlow(ClassLoader cl, Object flow) {
        final StringBuilder descBuf = new StringBuilder();
        try {
            Method collectM = null;
            for (Class<?> itf : MainReflectionSupport.allInterfaces(flow.getClass())) {
                Method cand = null; int two = 0;
                for (Method m : itf.getDeclaredMethods()) {
                    if (m.getParameterTypes().length == 2) { cand = m; two++; }
                }
                if (two == 1 && cand.getParameterTypes()[1].isInterface()) { collectM = cand; break; }
            }
            if (collectM == null) { Main.extLog("[VP] Flow interface (1x 2-arg method) not found"); return null; }
            final Class<?> collectorCls = collectM.getParameterTypes()[0];
            final Class<?> contCls = collectM.getParameterTypes()[1];
            Class<?> ccTmp = null;
            for (Method m : contCls.getMethods()) {
                if (m.getParameterTypes().length == 0 && m.getReturnType().isInterface()) { ccTmp = m.getReturnType(); break; }
            }
            final Class<?> ccCls = ccTmp;
            Main.extLog("[VP] collect=" + collectM.getName() + " collector=" + collectorCls.getName()
                    + " cont=" + contCls.getName() + " ctx=" + (ccCls == null ? "null" : ccCls.getName()));
            final Object ctx = (ccCls != null) ? Main.emptyContextProxy(cl, ccCls) : null;

            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            final int[] count = {0};
            final StringBuilder acc = new StringBuilder();

            InvocationHandler contH = new InvocationHandler() {
                public Object invoke(Object proxy, Method m, Object[] a) {
                    if (MainReflectionSupport.isObjectMethod(m)) return MainReflectionSupport.objectMethod(proxy, m, a);
                    int p = m.getParameterTypes().length;
                    if (p == 0) return ctx;                        // getContext()
                    Main.extLog("[VP] flow completed; events=" + count[0]
                            + " resumeArg=" + (a != null && a.length > 0 ? String.valueOf(a[0]) : "?"));
                    latch.countDown();
                    return null;
                }
            };
            final Object rootCont = Proxy.newProxyInstance(cl, new Class<?>[]{contCls}, contH);

            InvocationHandler collH = new InvocationHandler() {
                public Object invoke(Object proxy, Method m, Object[] a) {
                    if (MainReflectionSupport.isObjectMethod(m)) return MainReflectionSupport.objectMethod(proxy, m, a);
                    if (m.getParameterTypes().length == 2) {       // emit(value, cont)
                        try {
                            Object value = a[0];
                            count[0]++;
                            String s = MainReflectionSupport.summarizeFlowEvent(value);
                            if (count[0] <= 80) Main.extLog("[VP] emit#" + count[0] + " " + s);
                            acc.append(s).append('\n');
                            String delta = extractContentDeltaFromEvent(value);
                            if (delta != null) descBuf.append(delta);
                        } catch (Throwable t) { Main.extLog("[VP] emit err " + t); }
                        return null;
                    }
                    return null;
                }
            };
            Object collector = Proxy.newProxyInstance(cl, new Class<?>[]{collectorCls}, collH);

            collectM.setAccessible(true);
            Main.extLog("[VP] invoking collect on " + flow.getClass().getName());
            Object ret;
            try {
                ret = collectM.invoke(flow, collector, rootCont);
            } catch (java.lang.reflect.InvocationTargetException ite) {
                Throwable c = ite.getCause() != null ? ite.getCause() : ite;
                Main.extLog("[VP] collect threw: " + c + "\n" + Main.stackToString(c));
                return descBuf.toString();
            }
            Main.extLog("[VP] collect returned: " + String.valueOf(ret));
            latch.await(90, java.util.concurrent.TimeUnit.SECONDS);
            Main.extLog("[VP] DONE events=" + count[0] + " accLen=" + acc.length()
                    + " descLen=" + descBuf.length()
                    + " acc=" + MainReflectionSupport.truncateForLog(acc.toString(), 1200));
        } catch (Throwable t) {
            Main.extLog("[VP] collectFlow failed: " + t + "\n" + Main.stackToString(t));
        }
        return descBuf.toString();
    }

    private String extractContentDeltaFromEvent(Object value) {
        try {
            Object event = MainReflectionSupport.fieldByName(value, "a");
            if (event == null || MainReflectionSupport.fieldByName(event, "j") instanceof String) return null;
            Object ename = MainReflectionSupport.fieldByName(event, "a");
            if (ename != null) return null;
            Object bj = MainReflectionSupport.fieldByName(event, "b");
            if (!(bj instanceof String)) return null;
            return extractContentDelta((String) bj);
        } catch (Throwable t) { return null; }
    }

    private static String extractContentDelta(String json) {
        if (json == null) return null;
        int vi = json.indexOf("\"v\":\"");
        if (vi < 0) return null;
        boolean bareDelta = json.startsWith("{\"v\":\"");
        boolean appendContent = json.contains("content") && json.contains("APPEND");
        if (!bareDelta && !appendContent) return null;
        int start = vi + 5;
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '\\' && i + 1 < json.length()) {
                char nx = json.charAt(i + 1);
                switch (nx) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': break;
                    default:  sb.append(nx);
                }
                i++;
                continue;
            }
            if (ch == '"') break;
            sb.append(ch);
        }
        return sb.toString();
    }
}
