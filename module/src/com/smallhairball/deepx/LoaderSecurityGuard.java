package com.smallhairball.deepx;

import android.content.Context;

public final class LoaderSecurityGuard {
    public static final String PROPRIETARY_LOADER_FACTORY = "";
    public static final String PROPRIETARY_LOADER_PROVIDER_SUFFIX = "";
    public static final String HANDSHAKE_METHOD = "";
    public static final String SCOPE_METHOD = "";
    private LoaderSecurityGuard() {}
    public static boolean checkAndEnforceGenericLoaderRejection(ClassLoader cl, Context context) { return true; }
    public static boolean checkAndEnforceAntiEmbedding(ClassLoader cl, Context context, String hostApk) { return true; }
    public static boolean isModuleEmbeddedInHostApk(ClassLoader cl, String hostApk) { return false; }
    public static boolean isGenericUnauthorizedLoader(ClassLoader cl, Context context) { return false; }
    public static boolean verifyProprietaryLoaderOrKill(Context context) { return true; }
    public static void schedulePostAttachBindingVerification(Context context) { }
    public static String computeLoaderDexSha256(Context context) { return ""; }
    public static String computeHmacSha256(String data) { return ""; }
    public static void killHost() { }
}
