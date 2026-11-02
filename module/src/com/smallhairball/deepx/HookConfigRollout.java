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

/** Hook group extracted from Main.java: CONFIG category (see JavaHookGuide). */
final class HookConfigRollout {
    static final HookConfigRollout INSTANCE = new HookConfigRollout();

    static final String HOT_UPDATE_DISABLED_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_hot_update_disabled";

    static boolean isHotUpdateDisabled() {
        return new File(HOT_UPDATE_DISABLED_FILE).isFile();
    }

    /** Suppresses DeepSeek's shared normal/forced update dialog while the opt-in marker exists. */
    void hookHotUpdateDialog(final ClassLoader loader) {
        try {
            Method method = HostCompat.updateDialogMethod(loader);
            if (method == null) {
                Main.log("client update renderer not found for " + HostCompat.generationName());
                return;
            }
            Main.MODULE.hook(method).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    return isHotUpdateDisabled() ? null : chain.proceed();
                }
            });
            Main.log("client update renderer hooked: " + method);
        } catch (Throwable error) {
            Main.log("hook client update renderer failed: " + error);
        }
    }

    /** Blocks application of newly downloaded remote settings, not only the update dialog. */
    void hookRemoteConfigHotUpdates(final ClassLoader loader) {
        if (loader == null || (!HostCompat.isV236() && !HostCompat.isV241())) return;
        int count = 0;
        String[] owners = HostCompat.isV241()
                ? new String[]{"fv5", "ys1"}
                : new String[]{"lp5", "pp1"};
        for (String ownerName : owners) {
            try {
                Class<?> owner = Class.forName(ownerName, false, loader);
                for (final Method method : owner.getDeclaredMethods()) {
                    Class<?>[] p = method.getParameterTypes();
                    if (method.getReturnType() != void.class || p.length != 4
                            || p[1] != int.class || p[2] != String.class
                            || p[3] != String.class) continue;
                    Main.MODULE.hook(method).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            if (isHotUpdateDisabled()) {
                                Main.log("remote settings update blocked owner="
                                        + method.getDeclaringClass().getSimpleName());
                                return null;
                            }
                            return chain.proceed();
                        }
                    });
                    count++;
                }
            } catch (Throwable error) {
                Main.log("remote settings blocker unavailable owner=" + ownerName + ": "
                        + Main.safeThrowableMessage(error));
            }
        }
        Main.log("remote settings hot-update blocker hooks=" + count);
    }

    /**
     * DeepSeek 2.3.4 does not expose the upload guide prompts as a Boolean MMKV flag. They are a
     * model_configs_v1 rollout rendered by a dedicated empty-returning Compose function, with
     * image/file fallback prompt lists built into both store channels. Intercepting that narrow
     * function gives the manager a real FORCE_OFF state while FOLLOW/FORCE_ON retain DeepSeek's
     * native attachment, empty-input and animation conditions.
     */
    void hookAttachmentGuidePromptRollout(ClassLoader cl) {
        if (cl == null || !HostCompat.isV234()) return;
        String ownerName = HostCompat.isV241() ? "q55"
                : HostCompat.isV236() ? "oa5"
                : HostCompat.isGooglePlay() ? "zf6" : "ra5";
        String methodName = HostCompat.isV241() ? "d"
                : HostCompat.isGooglePlay() ? "d" : "g";
        try {
            Class<?> owner = cl.loadClass(ownerName);
            Class<?> modelConfig = HostCompat.isV241()
                    ? cl.loadClass("ou5") : HostCompat.load(cl, "ni5");
            int count = 0;
            for (Method method : owner.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!methodName.equals(method.getName())
                        || !Modifier.isStatic(method.getModifiers())
                        || method.getReturnType() != void.class
                        || types.length != 9
                        || types[2] != modelConfig
                        || types[3] != String.class) continue;
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        if (RemoteFeatureFlags.mode(Main.hostClassLoader,
                                RemoteFeatureFlags.ATTACHMENT_GUIDE_PROMPTS)
                                == RemoteFeatureFlags.FORCE_OFF) {
                            return null;
                        }
                        return chain.proceed();
                    }
                });
                count++;
            }
            Main.log("attachment guide-prompt rollout hook=" + ownerName + "."
                    + methodName + " count=" + count);
        } catch (Throwable error) {
            Main.log("attachment guide-prompt rollout hook unavailable: " + error);
        }
    }

    /** Returns DeepSeek's built-in empty welcome configuration when the rollout is forced off. */
    void hookHomeWelcomeRollout(final ClassLoader cl) {
        if (cl == null || (!HostCompat.isV236() && !HostCompat.isV241())) return;
        try {
            final String repositoryName = HostCompat.isV241() ? "fv5" : "lp5";
            final String configName = HostCompat.isV241() ? "oi9" : "p99";
            final String getterName = HostCompat.isV241() ? "e" : "e";
            final Class<?> repository = Class.forName(repositoryName, false, cl);
            final Class<?> config = Class.forName(configName, false, cl);
            final Constructor<?> emptyConfig = config.getDeclaredConstructor();
            emptyConfig.setAccessible(true);
            Method getter = repository.getDeclaredMethod(getterName);
            getter.setAccessible(true);
            Main.MODULE.hook(getter).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    if (RemoteFeatureFlags.mode(cl, RemoteFeatureFlags.HOME_WELCOME_MESSAGES)
                            == RemoteFeatureFlags.FORCE_OFF) {
                        return emptyConfig.newInstance();
                    }
                    return chain.proceed();
                }
            });
            Main.log("home welcome-message rollout hook=" + repositoryName + "." + getterName);
        } catch (Throwable error) {
            Main.log("home welcome-message rollout hook unavailable: "
                    + Main.safeThrowableMessage(error));
        }
    }
}
