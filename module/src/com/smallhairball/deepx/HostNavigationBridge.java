package com.smallhairball.deepx;

import android.app.Activity;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.smallhairball.deepx.LegacyXposedModule.Chain;
import com.smallhairball.deepx.LegacyXposedModule.Hooker;

/** Captures the live Compose NavController through exact, independent host adapters. */
final class HostNavigationBridge {
    interface Callback { void complete(boolean success, String detail); }

    private static final List<WeakReference<Object>> CONTROLLERS =
            Collections.synchronizedList(new ArrayList<WeakReference<Object>>());
    private static volatile ClassLoader hostLoader;
    private static volatile boolean installed;

    private HostNavigationBridge() {}

    private static boolean supportedBuild() {
        return HostCompat.isV236() || HostCompat.isV241();
    }

    static synchronized void install(Main owner, ClassLoader loader) {
        if (installed || owner == null || loader == null || !supportedBuild()) return;
        try {
            final String generation = HostCompat.generationName();
            final String controllerName = HostCompat.isV236() ? "vv5" : "p16";
            Class<?> controller = Class.forName(controllerName, false, loader);
            for (Constructor<?> constructor : controller.getDeclaredConstructors()) {
                constructor.setAccessible(true);
                owner.hook(constructor).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object result = chain.proceed();
                        remember(chain.getThisObject());
                        return result;
                    }
                });
            }
            hostLoader = loader;
            installed = true;
            Main.log(generation + " navigation controller capture installed class="
                    + controllerName);
        } catch (Throwable error) {
            Main.log(HostCompat.generationName() + " navigation capture failed: " + error);
        }
    }

    private static void remember(Object controller) {
        if (controller == null) return;
        synchronized (CONTROLLERS) {
            for (int i = CONTROLLERS.size() - 1; i >= 0; i--) {
                Object value = CONTROLLERS.get(i).get();
                if (value == null || value == controller) CONTROLLERS.remove(i);
            }
            CONTROLLERS.add(new WeakReference<Object>(controller));
            while (CONTROLLERS.size() > 8) CONTROLLERS.remove(0);
        }
    }

    static boolean available() {
        synchronized (CONTROLLERS) {
            for (WeakReference<Object> reference : CONTROLLERS) {
                if (reference.get() != null) return true;
            }
        }
        return false;
    }

    static void navigate(final Activity activity, final String route, final Callback callback) {
        if (activity == null || callback == null) return;
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                if (!supportedBuild()
                        || !(HostNavigationCatalog.DIRECT.equals(
                        HostNavigationCatalog.policy(route))
                        || HostNavigationCatalog.HIGH_RISK.equals(
                        HostNavigationCatalog.policy(route)))) {
                    callback.complete(false, "此页面不允许直接启动");
                    return;
                }
                String className = HostNavigationCatalog.obfuscatedRouteClass(route);
                if (className == null || hostLoader == null) {
                    callback.complete(false, "当前页面没有已验证的 "
                            + HostCompat.generationName() + " 映射");
                    return;
                }
                try {
                    Class<?> routeClass = Class.forName(className, false, hostLoader);
                    Field instanceField = routeClass.getField("INSTANCE");
                    Object routeObject = instanceField.get(null);
                    List<Object> snapshot = new ArrayList<Object>();
                    synchronized (CONTROLLERS) {
                        for (int i = CONTROLLERS.size() - 1; i >= 0; i--) {
                            Object controller = CONTROLLERS.get(i).get();
                            if (controller != null) snapshot.add(controller);
                        }
                    }
                    Throwable last = null;
                    for (Object controller : snapshot) {
                        try {
                            Method navigate = null;
                            for (Method method : controller.getClass().getDeclaredMethods()) {
                                if ("n".equals(method.getName())
                                        && method.getParameterTypes().length == 2) {
                                    navigate = method;
                                    break;
                                }
                            }
                            if (navigate == null) continue;
                            navigate.setAccessible(true);
                            navigate.invoke(controller, routeObject, null);
                            remember(controller);
                            callback.complete(true, HostNavigationCatalog.title(route));
                            return;
                        } catch (Throwable error) {
                            last = error.getCause() == null ? error : error.getCause();
                        }
                    }
                    callback.complete(false, last == null
                            ? "尚未捕获宿主导航器，请先进入聊天主页"
                            : "宿主拒绝导航：" + safe(last));
                } catch (Throwable error) {
                    callback.complete(false, "导航映射不可用：" + safe(error));
                }
            }
        });
    }

    private static String safe(Throwable error) {
        String value = error == null ? "unknown" : error.getMessage();
        if (value == null || value.trim().length() == 0) value = error.getClass().getSimpleName();
        return value.length() > 120 ? value.substring(0, 120) : value;
    }
}
