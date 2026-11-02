package com.smallhairball.deepx;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.net.Uri;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.WeakHashMap;

/**
 * Hosts two real DeepSeek root compositions in one vertically resizable surface.
 *
 * <p>No chat control is redrawn by the module: each pane receives the exact root content lambda
 * installed by DeepSeek's MainActivity.  Consequently the composer, thinking/search controls,
 * typography, colours, messages and navigation keep following the installed host build.</p>
 */
final class NativeDualChatBridge {
    private static final String HIDDEN_SESSION_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_dual_hidden_sessions";
    private static final Map<Activity, Workspace> OPEN =
            new WeakHashMap<Activity, Workspace>();
    private static final HashSet<String> HIDDEN_SESSIONS = new HashSet<String>();
    private static final Map<String, Integer> SESSION_PANES = new HashMap<String, Integer>();
    private static final Map<String, ModelFileOutput.StreamingFile> LIVE_FILES =
            new HashMap<String, ModelFileOutput.StreamingFile>();
    /** Latest immutable file snapshot for each hidden native session. */
    private static final Map<String, List<ModelFileOutput.StreamingFile>> SESSION_FILES =
            new HashMap<String, List<ModelFileOutput.StreamingFile>>();
    private static WeakReference<Object> rootContent = new WeakReference<Object>(null);
    private static WeakReference<Activity> rootActivity = new WeakReference<Activity>(null);
    private static ClassLoader hostLoader;
    private static boolean sessionsLoaded;
    private static int buildingDepth;
    private static int activeWorkspaces;
    private static int activePane;

    private NativeDualChatBridge() {}

    static synchronized boolean isBuilding() {
        return buildingDepth > 0;
    }

    static synchronized boolean isActive() {
        return activeWorkspaces > 0;
    }

    static synchronized void bindActiveSession(String sessionId) {
        String sid = cleanSessionId(sessionId);
        if (sid.length() > 0) SESSION_PANES.put(sid, Integer.valueOf(activePane));
    }

    static void updateFileStream(final String sessionId,
            final List<ModelFileOutput.StreamingFile> files) {
        if (files == null || files.isEmpty()) return;
        synchronized (NativeDualChatBridge.class) {
            String sid = cleanSessionId(sessionId);
            if (sid.length() > 0) {
                SESSION_FILES.put(sid, Collections.unmodifiableList(
                        new ArrayList<ModelFileOutput.StreamingFile>(files)));
            }
            for (ModelFileOutput.StreamingFile file : files) {
                if (file == null || file.name == null) continue;
                LIVE_FILES.put(file.name, file);
            }
            while (LIVE_FILES.size() > 8) {
                String first = LIVE_FILES.keySet().iterator().next();
                LIVE_FILES.remove(first);
            }
        }
    }

    /**
     * Returns the session-owned snapshot used by the native message renderer. Keeping this keyed
     * by DeepSeek's composition-local session id avoids relying on mutable State object identity:
     * the host replaces that object during SSE recomposition on both CN and Play builds.
     */
    static synchronized List<ModelFileOutput.StreamingFile> filesForSession(String sessionId) {
        List<ModelFileOutput.StreamingFile> files = SESSION_FILES.get(cleanSessionId(sessionId));
        return files == null
                ? Collections.<ModelFileOutput.StreamingFile>emptyList() : files;
    }

    static boolean openFileLink(Activity activity, Uri uri) {
        if (activity == null || uri == null
                || !"deekseep-file".equals(uri.getScheme())) return false;
        String name = uri.getQueryParameter("name");
        ModelFileOutput.StreamingFile file;
        synchronized (NativeDualChatBridge.class) {
            file = LIVE_FILES.get(name);
        }
        if (file == null || !file.complete) {
            Toast.makeText(activity, UiLanguage.text(activity,
                    "文件仍在生成或已失效", "The file is still generating or expired"),
                    Toast.LENGTH_SHORT).show();
            return true;
        }
        ModelFileViewerUi.show(activity,
                new ModelFileOutput.FileItem(file.name, file.content));
        return true;
    }

    static void openLiveFile(String name) {
        Activity activity = rootActivity.get();
        ModelFileOutput.StreamingFile file;
        synchronized (NativeDualChatBridge.class) {
            file = LIVE_FILES.get(name);
        }
        if (activity == null || activity.isFinishing()) return;
        if (file == null || !file.complete) {
            Toast.makeText(activity, UiLanguage.text(activity,
                    "文件仍在生成", "The file is still generating"),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        ModelFileViewerUi.show(activity,
                new ModelFileOutput.FileItem(file.name, file.content));
    }

    private static synchronized void recordActivePane(int pane) {
        activePane = pane <= 0 ? 0 : 1;
    }

    static synchronized void capture(Activity activity, Object content, ClassLoader loader) {
        if (activity == null || content == null || buildingDepth > 0) return;
        if (!"com.deepseek.chat.MainActivity".equals(activity.getClass().getName())) return;
        rootActivity = new WeakReference<Activity>(activity);
        rootContent = new WeakReference<Object>(content);
        hostLoader = loader != null ? loader : activity.getClassLoader();
        Main.log("captured DeepSeek native root for dual workspace content="
                + content.getClass().getName());
    }

    static void show(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        Object content;
        ClassLoader loader;
        synchronized (NativeDualChatBridge.class) {
            Workspace current = OPEN.get(activity);
            if (current != null && current.dialog.isShowing()) return;
            content = rootContent.get();
            loader = hostLoader;
        }
        if (loader == null) loader = activity.getClassLoader();
        if (content == null) {
            content = findContentFromActivity(activity);
            if (content != null) {
                capture(activity, content, loader);
            }
        }
        if (content == null || loader == null
                || !"com.deepseek.chat.MainActivity".equals(activity.getClass().getName())) {
            Toast.makeText(activity, UiLanguage.text(activity,
                    "原生聊天界面尚未准备好，请返回主页后重试",
                    "The native chat surface is not ready; return home and try again"),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Workspace workspace = new Workspace(activity, loader, content);
            synchronized (NativeDualChatBridge.class) {
                OPEN.put(activity, workspace);
                activeWorkspaces++;
            }
            workspace.show();
        } catch (Throwable error) {
            Main.log("native dual workspace creation failed: " + error);
            Toast.makeText(activity, UiLanguage.text(activity,
                    "双开模式创建失败，已安全回到原界面",
                    "Could not create dual mode; the original screen was kept"),
                    Toast.LENGTH_LONG).show();
        }
    }

    static void forget(Activity activity) {
        Workspace workspace;
        synchronized (NativeDualChatBridge.class) {
            workspace = OPEN.remove(activity);
        }
        if (workspace != null) workspace.close();
    }

    private static Object findContentFromActivity(Activity activity) {
        if (activity == null || activity.getWindow() == null) return null;
        View decor = activity.getWindow().getDecorView();
        return findContentFromView(decor);
    }

    private static Object findContentFromView(View view) {
        if (view == null) return null;
        if ("androidx.compose.ui.platform.ComposeView".equals(view.getClass().getName())) {
            for (Field field : view.getClass().getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object val = field.get(view);
                    if (val != null) {
                        try {
                            Method getValue = val.getClass().getMethod("getValue");
                            getValue.setAccessible(true);
                            Object actualContent = getValue.invoke(val);
                            if (actualContent != null) {
                                Main.log("found ComposeView content from field " + field.getName());
                                return actualContent;
                            }
                        } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                Object found = findContentFromView(vg.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    static synchronized void markHiddenSession(String sessionId) {
        String sid = cleanSessionId(sessionId);
        if (sid.length() == 0) return;
        loadHiddenSessionsLocked();
        if (!HIDDEN_SESSIONS.add(sid)) return;
        try {
            File file = new File(HIDDEN_SESSION_FILE);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            FileWriter writer = new FileWriter(file, true);
            try {
                writer.write(sid);
                writer.write('\n');
                writer.flush();
            } finally {
                writer.close();
            }
            Main.log("dual workspace session hidden sid=" + sid);
        } catch (Throwable error) {
            Main.log("dual workspace hidden-session persistence failed: " + error);
        }
    }

    static synchronized boolean isHiddenSession(String sessionId) {
        String sid = cleanSessionId(sessionId);
        if (sid.length() == 0) return false;
        loadHiddenSessionsLocked();
        return HIDDEN_SESSIONS.contains(sid);
    }

    private static void loadHiddenSessionsLocked() {
        if (sessionsLoaded) return;
        sessionsLoaded = true;
        File file = new File(HIDDEN_SESSION_FILE);
        if (!file.isFile() || file.length() <= 0L || file.length() > 256L * 1024L) return;
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    String sid = cleanSessionId(line);
                    if (sid.length() > 0) HIDDEN_SESSIONS.add(sid);
                }
            } finally {
                reader.close();
            }
        } catch (Throwable error) {
            Main.log("dual workspace hidden-session state ignored: " + error);
        }
    }

    private static String cleanSessionId(String value) {
        if (value == null) return "";
        String sid = value.trim();
        if (sid.length() < 6 || sid.length() > 160 || "null".equals(sid)) return "";
        for (int i = 0; i < sid.length(); i++) {
            char ch = sid.charAt(i);
            if (!(Character.isLetterOrDigit(ch) || ch == '-' || ch == '_' || ch == ':')) {
                return "";
            }
        }
        return sid;
    }

    private static NativePane nativeComposeView(Activity activity, ClassLoader loader, Object content)
            throws Throwable {
        Class<?> composeType = loader.loadClass("androidx.compose.ui.platform.ComposeView");
        Constructor<?> chosen = null;
        for (Constructor<?> constructor : composeType.getDeclaredConstructors()) {
            Class<?>[] types = constructor.getParameterTypes();
            if (types.length == 1 && (types[0].isInstance(activity) || types[0].isAssignableFrom(activity.getClass()) || Context.class.isAssignableFrom(types[0]))) {
                chosen = constructor;
                break;
            }
        }
        if (chosen == null) {
            for (Constructor<?> constructor : composeType.getConstructors()) {
                Class<?>[] types = constructor.getParameterTypes();
                if (types.length >= 1 && (types[0].isInstance(activity) || types[0].isAssignableFrom(activity.getClass()) || Context.class.isAssignableFrom(types[0]))) {
                    chosen = constructor;
                    break;
                }
            }
        }
        if (chosen == null) throw new NoSuchMethodException("ComposeView(host activity)");
        chosen.setAccessible(true);
        Object compose;
        synchronized (NativeDualChatBridge.class) { buildingDepth++; }
        try {
            Class<?>[] pTypes = chosen.getParameterTypes();
            Object[] args = new Object[pTypes.length];
            args[0] = activity;
            for (int i = 1; i < pTypes.length; i++) {
                args[i] = null;
            }
            compose = chosen.newInstance(args);
            View view = (View) compose;
            IsolatedOwners owners = isolatedOwners(activity, loader);
            installOwners(view, activity, loader, owners);
            Method setter = null;
            for (Method method : composeType.getMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if ("setContent".equals(method.getName()) && types.length == 1) {
                    if (types[0].isInstance(content) || types[0].isAssignableFrom(content.getClass())) {
                        setter = method;
                        break;
                    }
                    if (setter == null) setter = method;
                }
            }
            if (setter == null) throw new NoSuchMethodException("ComposeView.setContent");
            setter.setAccessible(true);
            setter.invoke(compose, content);
            return new NativePane(view, owners);
        } finally {
            synchronized (NativeDualChatBridge.class) { buildingDepth--; }
        }
    }

    /**
     * Each native root must own a separate ViewModelStore. Sharing MainActivity's store made both
     * navigation roots mutate the same chat state during recomposition, which presented as a
     * continuous empty/chat flash. Lifecycle and saved-state still follow the real Activity.
     */
    private static IsolatedOwners isolatedOwners(final Activity activity, ClassLoader loader) {
        try {
            final Class<?> ownerType = loader.loadClass("androidx.lifecycle.ViewModelStoreOwner");
            final Class<?> lifecycleOwnerType = loader.loadClass("androidx.lifecycle.LifecycleOwner");
            final Class<?> savedOwnerType = loader.loadClass(
                    "androidx.savedstate.SavedStateRegistryOwner");
            final Class<?> storeType = loader.loadClass("androidx.lifecycle.ViewModelStore");
            final Object store = storeType.getDeclaredConstructor().newInstance();
            final Object[] registry = new Object[1];
            final Object[] lifecycleRegistry = new Object[1];
            Object proxy = Proxy.newProxyInstance(loader,
                    new Class<?>[]{ownerType, lifecycleOwnerType, savedOwnerType},
                    (candidate, method, args) -> {
                        if ("getViewModelStore".equals(method.getName())) return store;
                        if ("getLifecycle".equals(method.getName())) {
                            return lifecycleRegistry[0];
                        }
                        if ("getSavedStateRegistry".equals(method.getName())) {
                            return registry[0];
                        }
                        if ("toString".equals(method.getName())) {
                            return "DeePXDualPaneOwners";
                        }
                        if ("hashCode".equals(method.getName())) {
                            return System.identityHashCode(candidate);
                        }
                        if ("equals".equals(method.getName())) {
                            return candidate == (args == null ? null : args[0]);
                        }
                        return null;
                    });
            Class<?> lifecycleRegistryType = loader.loadClass(
                    "androidx.lifecycle.LifecycleRegistry");
            Object isolatedLifecycle = null;
            for (Constructor<?> constructor : lifecycleRegistryType.getDeclaredConstructors()) {
                Class<?>[] p = constructor.getParameterTypes();
                if (p.length < 1 || !p[0].isInstance(proxy)) continue;
                Object[] values = new Object[p.length];
                values[0] = proxy;
                for (int i = 1; i < p.length; i++) {
                    values[i] = p[i] == boolean.class ? Boolean.FALSE : null;
                }
                constructor.setAccessible(true);
                isolatedLifecycle = constructor.newInstance(values);
                break;
            }
            if (isolatedLifecycle == null) {
                throw new NoSuchMethodException("LifecycleRegistry(owner)");
            }
            lifecycleRegistry[0] = isolatedLifecycle;
            // A shared SavedStateRegistry makes both native roots compete for identical Compose
            // restoration keys. That race was the remaining empty/chat flash after separating
            // ViewModelStore. Give each pane an attached, empty registry of its own.
            Class<?> controllerType = loader.loadClass(
                    "androidx.savedstate.SavedStateRegistryController");
            Object controller = null;
            for (Method method : controllerType.getMethods()) {
                Class<?>[] p = method.getParameterTypes();
                if ("create".equals(method.getName())
                        && java.lang.reflect.Modifier.isStatic(method.getModifiers())
                        && p.length == 1 && p[0].isInstance(proxy)) {
                    controller = method.invoke(null, proxy);
                    break;
                }
            }
            if (controller == null) {
                Object companion = controllerType.getField("Companion").get(null);
                Method create = companion.getClass().getMethod("create", savedOwnerType);
                controller = create.invoke(companion, proxy);
            }
            Method registryGetter = controllerType.getMethod("getSavedStateRegistry");
            registry[0] = registryGetter.invoke(controller);
            try { controllerType.getMethod("performAttach").invoke(controller); }
            catch (NoSuchMethodException ignored) {}
            controllerType.getMethod("performRestore", android.os.Bundle.class)
                    .invoke(controller, new Object[]{null});
            Class<?> stateType = loader.loadClass("androidx.lifecycle.Lifecycle$State");
            Object resumed = java.lang.Enum.valueOf((Class) stateType, "RESUMED");
            lifecycleRegistryType.getMethod("setCurrentState", stateType)
                    .invoke(isolatedLifecycle, resumed);
            return new IsolatedOwners(proxy, proxy, proxy, store);
        } catch (Throwable error) {
            Main.log("dual pane isolated saved-state fallback: " + error);
            try {
                final Class<?> ownerType = loader.loadClass(
                        "androidx.lifecycle.ViewModelStoreOwner");
                final Class<?> storeType = loader.loadClass("androidx.lifecycle.ViewModelStore");
                final Object store = storeType.getDeclaredConstructor().newInstance();
                Object proxy = Proxy.newProxyInstance(loader, new Class<?>[]{ownerType},
                        (candidate, method, args) -> {
                            if ("getViewModelStore".equals(method.getName())) return store;
                            if ("hashCode".equals(method.getName())) {
                                return System.identityHashCode(candidate);
                            }
                            if ("equals".equals(method.getName())) {
                                return candidate == (args == null ? null : args[0]);
                            }
                            return "DeePXDualPaneViewModelOwner";
                        });
                return new IsolatedOwners(activity, proxy, activity, store);
            } catch (Throwable ignored) {
                return new IsolatedOwners(activity, activity, activity, null);
            }
        }
    }

    private static void setViewTreeLifecycleOwner(View view, Object owner, ClassLoader loader) {
        if (view == null || owner == null || loader == null) return;
        try {
            Class<?> cls = loader.loadClass("androidx.lifecycle.ViewTreeLifecycleOwner");
            for (Method m : cls.getMethods()) {
                if ("set".equals(m.getName()) && m.getParameterTypes().length == 2) {
                    m.invoke(null, view, owner);
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void setViewTreeViewModelStoreOwner(View view, Object owner, ClassLoader loader) {
        if (view == null || owner == null || loader == null) return;
        try {
            Class<?> cls = loader.loadClass("androidx.lifecycle.ViewTreeViewModelStoreOwner");
            for (Method m : cls.getMethods()) {
                if ("set".equals(m.getName()) && m.getParameterTypes().length == 2) {
                    m.invoke(null, view, owner);
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void setViewTreeSavedStateRegistryOwner(View view, Object owner, ClassLoader loader) {
        if (view == null || owner == null || loader == null) return;
        try {
            Class<?> cls = loader.loadClass("androidx.savedstate.ViewTreeSavedStateRegistryOwner");
            for (Method m : cls.getMethods()) {
                if ("set".equals(m.getName()) && m.getParameterTypes().length == 2) {
                    m.invoke(null, view, owner);
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void installOwners(View view, Activity activity, ClassLoader loader,
                                      IsolatedOwners owners) {
        setViewTreeLifecycleOwner(view, owners.lifecycleOwner, loader);
        setViewTreeViewModelStoreOwner(view, owners.viewModelOwner, loader);
        setViewTreeSavedStateRegistryOwner(view, owners.savedStateOwner, loader);

        String[] names = {"view_tree_lifecycle_owner", "view_tree_view_model_store_owner",
                "view_tree_saved_state_registry_owner"};
        for (String name : names) {
            try {
                int id = resolveHostViewTreeId(activity, loader, name);
                if (id != 0) {
                    Object owner = "view_tree_view_model_store_owner".equals(name)
                            ? owners.viewModelOwner
                            : ("view_tree_saved_state_registry_owner".equals(name)
                                    ? owners.savedStateOwner : owners.lifecycleOwner);
                    view.setTag(id, owner);
                }
            } catch (Throwable ignored) {}
        }
    }

    private static int resolveHostViewTreeId(Activity activity, ClassLoader loader, String name) {
        try {
            Class<?> ids = Class.forName("com.deepseek.chat.R$id", false, loader);
            Field field = ids.getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof Number) return ((Number) value).intValue();
        } catch (Throwable ignored) {}
        return activity.getResources().getIdentifier(name, "id", "com.deepseek.chat");
    }

    private static final class IsolatedOwners {
        final Object lifecycleOwner;
        final Object viewModelOwner;
        final Object savedStateOwner;
        final Object viewModelStore;

        IsolatedOwners(Object lifecycleOwner, Object viewModelOwner, Object savedStateOwner,
                       Object viewModelStore) {
            this.lifecycleOwner = lifecycleOwner;
            this.viewModelOwner = viewModelOwner;
            this.savedStateOwner = savedStateOwner;
            this.viewModelStore = viewModelStore;
        }

        void clear() {
            if (viewModelStore == null) return;
            try { viewModelStore.getClass().getMethod("clear").invoke(viewModelStore); }
            catch (Throwable ignored) {}
        }
    }

    private static final class NativePane {
        final View view;
        final IsolatedOwners owners;

        NativePane(View view, IsolatedOwners owners) {
            this.view = view;
            this.owners = owners;
        }
    }

    private static final class Workspace {
        final Activity activity;
        final Dialog dialog;
        final SplitHost split;
        final IsolatedOwners topOwners;
        final IsolatedOwners bottomOwners;
        boolean closed;

        Workspace(Activity activity, ClassLoader loader, Object content) throws Throwable {
            this.activity = activity;
            boolean dark = (activity.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            NativePane top = nativeComposeView(activity, loader, content);
            NativePane bottom = nativeComposeView(activity, loader, content);
            topOwners = top.owners;
            bottomOwners = bottom.owners;
            split = new SplitHost(activity, top.view, bottom.view, dark);
            // AbstractComposeView deliberately climbs to the child directly below
            // android.R.id.content before resolving its window Recomposer. Tags placed only on
            // each ComposeView are therefore below the lookup boundary. Propagate the host's real
            // lifecycle/saved-state owners to the split root before it is attached to the dialog.
            installHostWindowOwners(split, activity, loader);
            dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            dialog.setContentView(split);
            dialog.setCanceledOnTouchOutside(false);
            dialog.setOnDismissListener(ignored -> finish());
        }

        void show() {
            dialog.show();
            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
                WindowManager.LayoutParams attributes = window.getAttributes();
                attributes.windowAnimations = 0;
                attributes.dimAmount = 0f;
                window.setAttributes(attributes);
                window.setSoftInputMode(
                        // DeepSeek's native Compose input already consumes WindowInsets.ime.
                        // Resizing the dialog as well applied the keyboard height twice and left
                        // a large blank band above the IME in dual mode.
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    window.setDecorFitsSystemWindows(false);
                }
            }
            Main.log("native dual workspace shown without transition animation");
        }

        void close() {
            try { dialog.dismiss(); } catch (Throwable ignored) { finish(); }
        }

        void finish() {
            synchronized (NativeDualChatBridge.class) {
                if (closed) return;
                closed = true;
                OPEN.remove(activity);
                if (activeWorkspaces > 0) activeWorkspaces--;
            }
            topOwners.clear();
            bottomOwners.clear();
            Main.log("native dual workspace closed");
        }
    }

    private static void installHostWindowOwners(
            View target, Activity activity, ClassLoader loader) {
        if (target == null || activity == null) return;
        setViewTreeLifecycleOwner(target, activity, loader);
        setViewTreeSavedStateRegistryOwner(target, activity, loader);
        String[] names = {"view_tree_lifecycle_owner",
                "view_tree_saved_state_registry_owner"};
        View decor = activity.getWindow() == null
                ? null : activity.getWindow().getDecorView();
        for (String name : names) {
            try {
                int id = resolveHostViewTreeId(activity, loader, name);
                if (id == 0) continue;
                Object owner = null;
                for (View cursor = decor; cursor != null;) {
                    owner = cursor.getTag(id);
                    if (owner != null) break;
                    Object parent = cursor.getParent();
                    cursor = parent instanceof View ? (View) parent : null;
                }
                if (owner == null) owner = activity;
                target.setTag(id, owner);
            } catch (Throwable error) {
                Main.log("dual pane host owner propagation failed " + name + ": " + error);
            }
        }
    }

    /** Two native panes separated by a hairline and a slightly thicker drag handle. */
    private static final class SplitHost extends ViewGroup {
        final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint handle = new Paint(Paint.ANTI_ALIAS_FLAG);
        final float density;
        final Activity activity;
        final boolean dark;
        final LinearLayout topFiles;
        final LinearLayout bottomFiles;
        final Map<String, FileCard> topCards = new HashMap<String, FileCard>();
        final Map<String, FileCard> bottomCards = new HashMap<String, FileCard>();
        float ratio = 0.5f;
        float downY;
        float downRatio;
        float pendingRatio = 0.5f;
        boolean dragging;
        boolean frameLayoutPending;

        SplitHost(Context context, View top, View bottom, boolean dark) {
            super(context);
            this.activity = (Activity) context;
            this.dark = dark;
            density = context.getResources().getDisplayMetrics().density;
            setWillNotDraw(false);
            setClipChildren(false);
            setBackgroundColor(dark ? 0xFF101114 : 0xFFF7F8FA);
            line.setColor(dark ? 0xFF4A4D54 : 0xFFD6D9DF);
            line.setStrokeWidth(Math.max(1f, density));
            handle.setColor(dark ? 0xFF868B95 : 0xFF8C929D);
            addView(top, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            addView(bottom, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            topFiles = fileContainer(context);
            bottomFiles = fileContainer(context);
            addView(topFiles, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            addView(bottomFiles, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);
            int gap = dp(12f);
            int available = Math.max(0, height - gap);
            int first = Math.round(available * ratio);
            int second = available - first;
            getChildAt(0).measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(first, MeasureSpec.EXACTLY));
            getChildAt(1).measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(second, MeasureSpec.EXACTLY));
            int cardWidth = Math.max(0, width - dp(28f));
            int cardHeight = dp(132f);
            topFiles.measure(MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(cardHeight, MeasureSpec.AT_MOST));
            bottomFiles.measure(MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(cardHeight, MeasureSpec.AT_MOST));
            setMeasuredDimension(width, height);
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int gap = dp(12f);
            int available = Math.max(0, getHeight() - gap);
            int first = Math.round(available * ratio);
            getChildAt(0).layout(0, 0, getWidth(), first);
            getChildAt(1).layout(0, first + gap, getWidth(), getHeight());
            layoutFileContainer(topFiles, first);
            layoutFileContainer(bottomFiles, getHeight());
        }

        @Override protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            // Draw last so both the line and its handle remain above the two Compose roots even
            // after the preview leaves the old divider gap.
            float center = dividerCenter();
            canvas.drawLine(0f, center, getWidth(), center, line);
            float halfWidth = dp(22f);
            float halfHeight = dp(3.5f);
            canvas.drawRoundRect(getWidth() / 2f - halfWidth, center - halfHeight,
                    getWidth() / 2f + halfWidth, center + halfHeight,
                    halfHeight, halfHeight, handle);
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                    && Math.abs(event.getY() - dividerCenter()) <= dp(24f)) {
                dragging = true;
                downY = event.getY();
                downRatio = ratio;
                pendingRatio = ratio;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            return dragging;
        }

        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            if (event != null && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                recordActivePane(event.getY() < dividerCenter() ? 0 : 1);
            }
            return super.dispatchTouchEvent(event);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                dragging = true;
                downY = event.getY();
                downRatio = ratio;
                pendingRatio = ratio;
                return true;
            }
            if (!dragging) return false;
            if (action == MotionEvent.ACTION_MOVE) {
                float available = Math.max(1f, getHeight() - dp(12f));
                pendingRatio = Math.max(0.22f, Math.min(0.78f,
                        downRatio + (event.getY() - downY) / available));
                scheduleRealPaneLayout();
                return true;
            }
            if (action == MotionEvent.ACTION_UP) {
                dragging = false;
                if (ratio != pendingRatio) {
                    ratio = pendingRatio;
                    requestLayout();
                    invalidate();
                }
                return true;
            }
            if (action == MotionEvent.ACTION_CANCEL) {
                dragging = false;
                pendingRatio = ratio;
                requestLayout();
                invalidate();
                return true;
            }
            return true;
        }

        private float dividerCenter() {
            float visibleRatio = dragging ? pendingRatio : ratio;
            return Math.round((getHeight() - dp(12f)) * visibleRatio) + dp(6f);
        }

        private void scheduleRealPaneLayout() {
            if (frameLayoutPending) return;
            frameLayoutPending = true;
            postOnAnimation(new Runnable() {
                @Override public void run() {
                    frameLayoutPending = false;
                    if (!dragging) return;
                    ratio = pendingRatio;
                    requestLayout();
                    invalidate();
                }
            });
        }

        void updateFileCards(int pane, List<ModelFileOutput.StreamingFile> files) {
            LinearLayout container = pane <= 0 ? topFiles : bottomFiles;
            Map<String, FileCard> cards = pane <= 0 ? topCards : bottomCards;
            for (ModelFileOutput.StreamingFile file : files) {
                if (file == null || file.name == null) continue;
                FileCard card = cards.get(file.name);
                if (card == null) {
                    // Keep the overlay bounded in a half-height chat. Completed older files remain
                    // accessible in the native answer; the two newest live cards stay visible.
                    if (cards.size() >= 2) {
                        String first = cards.keySet().iterator().next();
                        FileCard removed = cards.remove(first);
                        if (removed != null) container.removeView(removed.root);
                    }
                    card = new FileCard(activity, dark, file.name);
                    cards.put(file.name, card);
                    container.addView(card.root, new LinearLayout.LayoutParams(
                            LayoutParams.MATCH_PARENT, dp(58f)));
                }
                card.update(file);
            }
            if (!cards.isEmpty() && container.getVisibility() != VISIBLE) {
                container.setVisibility(VISIBLE);
            }
        }

        private LinearLayout fileContainer(Context context) {
            LinearLayout container = new LinearLayout(context);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setGravity(Gravity.BOTTOM);
            container.setPadding(0, dp(2f), 0, dp(2f));
            container.setVisibility(GONE);
            container.setElevation(dp(8f));
            return container;
        }

        private void layoutFileContainer(View container, int panelBottom) {
            if (container.getVisibility() == GONE) return;
            int width = container.getMeasuredWidth();
            int height = container.getMeasuredHeight();
            int right = getWidth() - dp(14f);
            int bottom = panelBottom - dp(76f);
            container.layout(right - width, Math.max(0, bottom - height), right, bottom);
        }

        private final class FileCard {
            final LinearLayout root;
            final TextView name;
            final TextView status;
            ModelFileOutput.StreamingFile current;
            long lastUiAt;
            int lastUiChars = -1;

            FileCard(Context context, boolean dark, String fileName) {
                root = new LinearLayout(context);
                root.setOrientation(LinearLayout.VERTICAL);
                root.setGravity(Gravity.CENTER_VERTICAL);
                root.setPadding(dp(13f), dp(7f), dp(13f), dp(7f));
                GradientDrawable background = new GradientDrawable();
                background.setColor(dark ? 0xF52A2D34 : 0xF5FFFFFF);
                background.setStroke(dp(1f), dark ? 0xFF4C5260 : 0xFFD9DDE5);
                background.setCornerRadius(dp(10f));
                root.setBackground(background);
                name = new TextView(context);
                name.setText(fileName);
                name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
                name.setTextColor(dark ? 0xFFF2F3F5 : 0xFF17191D);
                name.setTypeface(Typeface.DEFAULT_BOLD);
                name.setSingleLine(true);
                root.addView(name, new LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
                status = new TextView(context);
                status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
                status.setTextColor(dark ? 0xFFADB3BE : 0xFF68707D);
                status.setSingleLine(true);
                root.addView(status, new LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            }

            void update(ModelFileOutput.StreamingFile file) {
                current = file;
                long now = System.currentTimeMillis();
                int chars = file.content == null ? 0 : file.content.length();
                if (!file.complete && lastUiChars >= 0 && chars - lastUiChars < 128
                        && now - lastUiAt < 250L) return;
                lastUiAt = now;
                lastUiChars = chars;
                status.setText(UiLanguage.text(activity,
                        file.complete ? "已生成 · 点击查看 · " : "正在生成 · ",
                        file.complete ? "Ready · tap to open · " : "Generating · ")
                        + chars + UiLanguage.text(activity, " 字符", " chars"));
                root.setAlpha(file.complete ? 1f : 0.88f);
                root.setClickable(file.complete);
                root.setOnClickListener(file.complete ? new OnClickListener() {
                    @Override public void onClick(View view) {
                        ModelFileOutput.StreamingFile value = current;
                        if (value != null && value.complete) {
                            ModelFileViewerUi.show(activity,
                                    new ModelFileOutput.FileItem(value.name, value.content));
                        }
                    }
                } : null);
            }
        }

        private int dp(float value) {
            return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                    value, getResources().getDisplayMetrics()));
        }
    }
}
