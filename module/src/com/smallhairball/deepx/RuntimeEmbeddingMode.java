package com.smallhairball.deepx;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

/** Exact host identities for a thin base APK that passively loads the universal module APK. */
final class RuntimeEmbeddingMode {
    private static final String HOST = "com.deepseek.chat";
    private static final String V236_VERSION_NAME = "2.3.6";
    private static final long V236_VERSION_CODE = 249L;
    private static final String V241_VERSION_NAME = "2.4.1";
    private static final long V241_VERSION_CODE = 257L;

    private static volatile boolean passiveHostLoader;

    private RuntimeEmbeddingMode() {}

    private static volatile String sLastFailureReason = "unknown";

    static String getLastFailureReason() {
        return sLastFailureReason;
    }

    static boolean activatePassiveHostLoader(Context context) {
        if (!isExactSupportedHost(context)) return false;
        passiveHostLoader = true;
        return true;
    }

    static boolean isPassiveHostLoader() {
        // HostCompat is initialized by Main before any component routing occurs. Keeping the
        // exact version check here prevents an embedded-loader marker from leaking into an older
        // host path when the same universal module APK is reused by a patching framework.
        return passiveHostLoader && (HostCompat.isV236() || HostCompat.isV241());
    }

    static String componentPackage(String normalModulePackage) {
        return isPassiveHostLoader() ? HOST : normalModulePackage;
    }

    private static boolean isExactSupportedHost(Context context) {
        if (context == null) {
            sLastFailureReason = "context_null";
            return false;
        }
        if (!HOST.equals(context.getPackageName())) {
            sLastFailureReason = "pkg_mismatch:" + context.getPackageName();
            return false;
        }
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(HOST, 0);
            if (info == null) {
                sLastFailureReason = "info_null";
                return false;
            }
            long code = Build.VERSION.SDK_INT >= 28
                    ? info.getLongVersionCode() : info.versionCode;
            if (HostCompat.isV236()) {
                boolean match = V236_VERSION_NAME.equals(info.versionName) && code == V236_VERSION_CODE;
                if (!match) sLastFailureReason = "v236_mismatch:" + info.versionName + "/" + code;
                return match;
            }
            if (HostCompat.isV241()) {
                boolean match = V241_VERSION_NAME.equals(info.versionName) && code == V241_VERSION_CODE;
                if (!match) sLastFailureReason = "v241_mismatch:" + info.versionName + "/" + code;
                return match;
            }
            sLastFailureReason = "hostcompat_not_v236_nor_v241(isV241=" + HostCompat.isV241()
                    + ",isV236=" + HostCompat.isV236()
                    + ",isGP=" + HostCompat.isGooglePlay()
                    + ",isV250=" + HostCompat.isV250() + ")";
            return false;
        } catch (Throwable t) {
            sLastFailureReason = "exception:" + t.getClass().getSimpleName() + ":" + t.getMessage();
            return false;
        }
    }
}
