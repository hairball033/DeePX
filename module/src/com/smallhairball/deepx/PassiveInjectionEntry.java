package com.smallhairball.deepx;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;

import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Stable entry used by a thin, framework-free host Loader.
 *
 * <p>The Loader reads this class from the one installed universal module APK. It does not copy
 * the module DEX/SO into DeepSeek and does not need the module application process to be alive.
 * All hooks and product logic continue to come from the same module artifact.</p>
 */
public final class PassiveInjectionEntry {
    public static final int ABI_VERSION = 1;
    private static final AtomicBoolean ATTACHED = new AtomicBoolean();

    private PassiveInjectionEntry() {}

    public static String attach(Context hostContext, ClassLoader hostClassLoader) {
        if (hostContext == null || hostClassLoader == null) return "host_unavailable";
        // Main.handleLoadPackage() normally performs this, but that only runs below via the
        // reflective LoadPackageParam dispatch; the host-generation check just below needs it
        // done first, since this passive-loader path has no earlier Xposed callback to rely on.
        HostCompat.initialize(hostClassLoader);
        if (!RuntimeEmbeddingMode.activatePassiveHostLoader(hostContext)) {
            return "unsupported_host[" + RuntimeEmbeddingMode.getLastFailureReason() + "]";
        }
        if (BuildInfo.PROTECTED_BUILD) {
            String hostApk = hostContext.getApplicationInfo() != null
                    ? hostContext.getApplicationInfo().sourceDir : null;
            if (!LoaderSecurityGuard.checkAndEnforceAntiEmbedding(
                    PassiveInjectionEntry.class.getClassLoader(), hostContext, hostApk)) {
                return "embedded_module_rejected";
            }
            if (!LoaderSecurityGuard.verifyProprietaryLoaderOrKill(hostContext)) {
                return "unauthorized_loader";
            }
        }
        String process = processName();
        if (!"com.deepseek.chat".equals(process)) return "secondary_process";
        final String pid = String.valueOf(android.os.Process.myPid());
        if (pid.equals(System.getProperty("deekseep.module.injected.pid"))) {
            return "already_injected_by_framework";
        }
        if (!ATTACHED.compareAndSet(false, true)) return "already_attached";
        try {
            // The provider nonce handshake above is the immediate admission check.  Run the
            // full base/module/Loader binding on its own worker so APK I/O does not delay the
            // first frame, while a later mismatch still terminates the unauthorized host.
            if (BuildInfo.PROTECTED_BUILD) {
                LoaderSecurityGuard.schedulePostAttachBindingVerification(hostContext);
            }
            Main.hostApplicationContext = hostContext;
            ApplicationInfo appInfo = hostContext.getApplicationInfo();
            java.lang.reflect.Constructor<LoadPackageParam> constructor =
                    LoadPackageParam.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            LoadPackageParam param = constructor.newInstance();
            param.packageName = "com.deepseek.chat";
            param.processName = process;
            param.classLoader = hostClassLoader;
            param.appInfo = appInfo;
            param.isFirstApplication = true;
            new Main().handleLoadPackage(param);
            return "attached";
        } catch (Throwable error) {
            ATTACHED.set(false);
            java.io.StringWriter sw = new java.io.StringWriter();
            error.printStackTrace(new java.io.PrintWriter(sw));
            return "attach_failed:" + error.getClass().getSimpleName() + " " + sw;
        }
    }

    private static String processName() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                String value = Application.getProcessName();
                if (value != null) return value;
            }
        } catch (Throwable ignored) {}
        try {
            java.io.FileInputStream input = new java.io.FileInputStream("/proc/self/cmdline");
            try {
                byte[] buffer = new byte[160];
                int count = input.read(buffer);
                int end = 0;
                while (end < count && buffer[end] != 0) end++;
                return count <= 0 ? "" : new String(buffer, 0, end, "UTF-8");
            } finally {
                input.close();
            }
        } catch (Throwable ignored) {
            return "";
        }
    }
}
