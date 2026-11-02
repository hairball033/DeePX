package com.smallhairball.deepx;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Thin facade over the encrypted routing core.  The main DEX only knows this facade plus the
 * {@link Route} data shape and account enumeration; the selection strategy (busy table,
 * stickiness, whole-pool cycling) lives inside the decrypted payload ({@code z12core}).
 * Every call degrades to a safe default when the payload is unavailable.
 */
public final class z12 {
    private static final String ENABLED_FILE = AccountManager.FILES_DIR
            + "/dq0_account_routing";
    private static final String SELECTED_FILE = AccountManager.FILES_DIR
            + "/dq0_account_routing_selected";
    private static final String CORE = "com.smallhairball.deepx.z12core";
    private static final Map<String, Method> METHODS = new HashMap<String, Method>();

    public static final class Route {
        public final String accountId;
        public final String token;
        public final String sessionNamespace;

        public Route(String accountId, String token) {
            this.accountId = accountId;
            this.token = token;
            this.sessionNamespace = shortHash(accountId);
        }
    }

    private z12() {}

    public static boolean enabled() {
        return BuildInfo.PROTECTED_BUILD && new File(ENABLED_FILE).isFile();
    }

    public static synchronized boolean setEnabled(boolean enabled, ClassLoader loader) {
        if (!BuildInfo.PROTECTED_BUILD) return false;
        if (enabled && available(loader).size() < 2) return false;
        try {
            File flag = new File(ENABLED_FILE);
            if (!enabled) return !flag.exists() || flag.delete();
            File parent = flag.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return false;
            FileOutputStream out = new FileOutputStream(flag, false);
            try {
                out.write("1\n".getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            } finally {
                out.close();
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static int accountCount(ClassLoader loader) {
        return available(loader).size();
    }

    static Set<String> selectedAccountIds(ClassLoader loader) {
        Set<String> configured = readSelectedIds();
        if (configured != null) return configured;
        HashSet<String> all = new HashSet<String>();
        try {
            AccountManager.snapshotCurrent(loader);
            for (AccountManager.Account account : AccountManager.listSlots()) {
                if (account != null && account.id != null && account.id.trim().length() > 0) {
                    all.add(account.id.trim());
                }
            }
        } catch (Throwable ignored) {}
        return all;
    }

    static synchronized boolean setSelectedAccountIds(Set<String> ids) {
        try {
            File file = new File(SELECTED_FILE);
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return false;
            ArrayList<String> sorted = new ArrayList<String>();
            if (ids != null) {
                for (String id : ids) {
                    if (id != null && id.trim().length() > 0) sorted.add(id.trim());
                }
            }
            java.util.Collections.sort(sorted);
            FileOutputStream out = new FileOutputStream(file, false);
            try {
                for (String id : sorted) out.write((id + "\n").getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            } finally {
                out.close();
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Account enumeration is intentionally in the main DEX (depends on AccountManager); the
     *  selection policy is not. Exposed public so the encrypted core can consume it. */
    public static List<Route> listRoutes(ClassLoader loader) {
        return available(loader);
    }

    /**
     * Returns the credential owned by the host process itself for code257 requests whose native
     * attachments are uploaded through the host composer.  The composer uploader always uses the
     * process account, so routing the later completion through a different imported credential
     * makes DeepSeek reject the otherwise valid file id as account-scoped.
     *
     * <p>The caller is guarded by {@code HostCompat.isV241()}.  Keep this as a lookup only: legacy
     * routing selection, stickiness and busy-account policy remain untouched.</p>
     */
    public static Route currentHostRouteForV241Attachment(ClassLoader loader) {
        if (!HostCompat.isV241() || !enabled()) return null;
        // A code257 composer attachment belongs to the active host account, independently of
        // which accounts the user selected for ordinary random routing.
        return currentHostAttachmentRoute(loader, true);
    }

    /**
     * code249 also scopes composer-uploaded file ids to the process account. Keep its lookup in
     * an exact 2.3.6 adapter so fixing TXT/image relay never changes code257 or legacy routing.
     */
    public static Route currentHostRouteForV236Attachment(ClassLoader loader) {
        if (!HostCompat.isV236() || !enabled()) return null;
        return currentHostAttachmentRoute(loader, false);
    }

    private static Route currentHostAttachmentRoute(ClassLoader loader,
                                                    boolean allowV241ProcessOwner) {
        try {
            String raw = AccountManager.readCurrentJson(loader);
            if (raw == null || raw.trim().length() == 0) return null;
            JSONObject credential = new JSONObject(raw);
            String id = credential.optString("id", "").trim();
            String token = credential.optString("token", "").trim();
            if (id.length() == 0 || token.length() < 8) return null;
            Set<String> selected = readSelectedIds();
            if (!allowV241ProcessOwner && selected != null && !selected.contains(id)) return null;
            return new Route(id, token);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static List<Route> available(ClassLoader loader) {
        LinkedHashMap<String, Route> unique = new LinkedHashMap<String, Route>();
        Set<String> selected = readSelectedIds();
        try {
            AccountManager.snapshotCurrent(loader);
            for (AccountManager.Account account : AccountManager.listSlots()) {
                if (account == null || account.id == null || account.credJson == null) continue;
                String id = account.id.trim();
                if (selected != null && !selected.contains(id)) continue;
                String token = new JSONObject(account.credJson).optString("token", "").trim();
                if (id.length() == 0 || token.length() < 8) continue;
                unique.put(id, new Route(id, token));
            }
        } catch (Throwable ignored) {}
        return new ArrayList<Route>(unique.values());
    }

    /** null means an unconfigured installation, which intentionally routes through all accounts. */
    private static Set<String> readSelectedIds() {
        File file = new File(SELECTED_FILE);
        if (!file.isFile()) return null;
        HashSet<String> ids = new HashSet<String>();
        FileInputStream input = null;
        try {
            input = new FileInputStream(file);
            byte[] buffer = new byte[(int) Math.min(file.length(), 256 * 1024L)];
            int read = input.read(buffer);
            if (read <= 0) return ids;
            String raw = new String(buffer, 0, read, StandardCharsets.UTF_8);
            for (String line : raw.split("\\r?\\n")) {
                String id = line.trim();
                if (id.length() > 0) ids.add(id);
            }
        } catch (Throwable ignored) {
            return ids;
        } finally {
            if (input != null) try { input.close(); } catch (Throwable ignored) {}
        }
        return ids;
    }

    private static String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                out.append(String.format(java.util.Locale.US, "%02x", digest[i] & 0xff));
            }
            return out.toString();
        } catch (Throwable ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static Method coreMethod(String name, Class<?>... types) {
        Method cached = METHODS.get(name);
        if (cached != null) return cached;
        try {
            Class<?> core = z14.payloadClass(null, CORE);
            if (core == null) return null;
            Method m = core.getDeclaredMethod(name, types);
            m.setAccessible(true);
            synchronized (METHODS) { METHODS.put(name, m); }
            return m;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Route chooseForRequest(ClassLoader loader, String requestId)
            throws z2.GatewayException {
        Method m = coreMethod("chooseForRequest", ClassLoader.class, String.class);
        if (m == null) return null;
        try {
            return (Route) m.invoke(null, loader, requestId);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof z2.GatewayException) throw (z2.GatewayException) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void release(String accountId) {
        Method m = coreMethod("release", String.class);
        if (m == null) return;
        try { m.invoke(null, accountId); } catch (Throwable ignored) {}
    }

    public static void markRateLimited(String accountId) {
        Method m = coreMethod("markRateLimited", String.class);
        if (m == null) return;
        try { m.invoke(null, accountId); } catch (Throwable ignored) {}
    }

    public static void markInvalid(String accountId) {
        Method m = coreMethod("markInvalid", String.class);
        if (m == null) return;
        try { m.invoke(null, accountId); } catch (Throwable ignored) {}
    }

    public static boolean isInvalidCredentialFailure(String value) {
        Method m = coreMethod("isInvalidCredentialFailure", String.class);
        if (m == null) return false;
        try {
            Object r = m.invoke(null, value);
            return r != null && (Boolean) r;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static Route chooseForRetry(ClassLoader loader, String requestId, Route previous)
            throws z2.GatewayException {
        Method m = coreMethod("chooseForRetry", ClassLoader.class, String.class, Route.class);
        if (m == null) return previous;
        try {
            return (Route) m.invoke(null, loader, requestId, previous);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof z2.GatewayException) throw (z2.GatewayException) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            return previous;
        } catch (Throwable ignored) {
            return previous;
        }
    }

    public static Route chooseForRetryExcluding(ClassLoader loader, String requestId, Route previous,
                                         Set<String> tried)
            throws z2.GatewayException {
        Method m = coreMethod("chooseForRetryExcluding", ClassLoader.class, String.class,
                Route.class, Set.class);
        if (m == null) return null;
        try {
            return (Route) m.invoke(null, loader, requestId, previous, tried);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof z2.GatewayException) throw (z2.GatewayException) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean isNativeBusyLimit(String value) {
        Method m = coreMethod("isNativeBusyLimit", String.class);
        if (m == null) return false;
        try {
            Object r = m.invoke(null, value);
            return r != null && (Boolean) r;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isExpertBusyFallback(String value) {
        Method m = coreMethod("isExpertBusyFallback", String.class);
        if (m == null) return false;
        try {
            Object r = m.invoke(null, value);
            return r != null && (Boolean) r;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isTransientApiFailure(z2.GatewayException error) {
        Method m = coreMethod("isTransientApiFailure", z2.GatewayException.class);
        if (m == null) return false;
        try {
            Object r = m.invoke(null, error);
            return r != null && (Boolean) r;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static z2.ErrorClass classifyUpstreamError(String message, String body) {
        Method m = coreMethod("classifyUpstreamError", String.class, String.class);
        if (m == null) return null;
        try {
            Object r = m.invoke(null, message, body);
            return r instanceof z2.ErrorClass ? (z2.ErrorClass) r : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
