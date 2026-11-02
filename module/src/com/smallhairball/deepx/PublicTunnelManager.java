package com.smallhairball.deepx;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Bundle;
import android.os.Build;
import android.os.SystemClock;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.IDN;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.HttpURLConnection;
import javax.net.ssl.HttpsURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Owns the optional public connector in the module process.
 *
 * <p>The DeepSeek process hosts the HTTP gateway because only that process can call the hooked
 * native transport. The connector belongs here instead: this process already has a foreground
 * service and the Cloudflare token never needs to be persisted in DeepSeek's data directory.</p>
 */
final class PublicTunnelManager {
    static final String PROVIDER_CLOUDFLARE = "cloudflare";
    static final String TRANSPORT_AUTO = "auto";
    static final String TRANSPORT_HTTP2 = "http2";
    static final String TRANSPORT_QUIC = "quic";
    static final String CLOUDFLARED_VERSION = "2026.6.0";

    private static final String TAG = "DeePXPublicTunnel";
    private static final String PREFS = "deekseep_public_tunnel";
    private static final String KEY_REQUESTED = "requested";
    private static final String KEY_DOMAINS = "domains";
    private static final String KEY_DIRECT_ROOT = "direct_root";
    private static final String KEY_TRANSPORT = "transport";
    private static final String KEY_TOKEN_CIPHER = "cloudflare_token_cipher";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "deekseep_public_tunnel_secret_v1";
    private static final String TOKEN_DIR = "deekseep_public_tunnel";
    private static final String TOKEN_FILE = "cloudflare.token";
    private static final String LOG_FILE = "deekseep_cloudflared.log";
    private static final String NATIVE_BINARY = "libcloudflared.so";
    private static final String DOWNLOAD_URL =
            "https://github.com/cloudflare/cloudflared/releases/download/2026.6.0/"
                    + "cloudflared-linux-arm64";
    private static final String DOWNLOAD_SHA256 =
            "8482ebf1e74a2a4a1a9f1e090e17e3de08423f94100ece6789287cb26fb9480f";
    private static final long DOWNLOAD_BYTES = 36_914_076L;
    private static final long MAX_DOWNLOAD_BYTES = 38L * 1024L * 1024L;
    private static final String BINARY_DIR = "cloudflared-bin";
    private static final String BINARY_MARKER = "verified.sha256";
    private static final int MAX_DOMAINS = 16;
    private static final int MAX_LOG_LINES = 80;
    private static final long MAX_LOG_BYTES = 512L * 1024L;
    private static final Object LOCK = new Object();

    private static final ArrayDeque<String> recentLogs = new ArrayDeque<String>();
    private static volatile Process process;
    private static volatile File activeTokenFile;
    private static volatile String activeRootExecutable;
    private static volatile String launchMode = "direct";
    private static volatile String state = "stopped";
    private static volatile String lastError = "";
    private static volatile boolean gatewayRunning;
    private static volatile int gatewayPort;
    private static volatile long startedAtWall;
    private static volatile long connectedAtWall;
    private static volatile long nextStartElapsed;
    private static volatile int failureStreak;
    private static volatile Context applicationContext;
    private static final AtomicBoolean provisioning = new AtomicBoolean(false);
    private static volatile long provisioningBytes;
    private static volatile long provisioningTotal = DOWNLOAD_BYTES;
    private static volatile boolean provisioningCancelled;
    private static volatile HttpURLConnection provisioningConnection;

    private PublicTunnelManager() {}

    static Bundle configure(Context context, Bundle extras) {
        Bundle result = new Bundle();
        if (context == null) {
            result.putBoolean("accepted", false);
            result.putString("error", "模块上下文不可用");
            return result;
        }
        Context app = context.getApplicationContext();
        applicationContext = app;
        String domains;
        String directRoot;
        String transport;
        try {
            domains = normalizeDomains(extras == null ? "" : extras.getString("domains", ""));
            directRoot = normalizeDirectRoot(
                    extras == null ? "" : extras.getString("direct_root", ""));
            transport = normalizeTransport(
                    extras == null ? TRANSPORT_AUTO
                            : extras.getString("transport", TRANSPORT_AUTO));
        } catch (IllegalArgumentException e) {
            result.putBoolean("accepted", false);
            result.putString("error", e.getMessage());
            putStatus(app, result);
            return result;
        }

        SharedPreferences preferences = prefs(app);
        boolean changed = !domains.equals(preferences.getString(KEY_DOMAINS, ""))
                || !directRoot.equals(preferences.getString(KEY_DIRECT_ROOT, ""))
                || !transport.equals(preferences.getString(KEY_TRANSPORT, TRANSPORT_AUTO));
        SharedPreferences.Editor edit = preferences.edit()
                .putString(KEY_DOMAINS, domains)
                .putString(KEY_DIRECT_ROOT, directRoot)
                .putString(KEY_TRANSPORT, transport);
        String suppliedToken = extras == null ? "" : extras.getString("token", "");
        if (suppliedToken != null && suppliedToken.trim().length() > 0) {
            try {
                String token = normalizeCloudflareToken(suppliedToken);
                validateCloudflareToken(token);
                edit.putString(KEY_TOKEN_CIPHER, encrypt(app, token));
                changed = true;
            } catch (Throwable t) {
                result.putBoolean("accepted", false);
                result.putString("error", "Tunnel token 无效或无法安全保存：" + safeMessage(t));
                putStatus(app, result);
                return result;
            }
        }
        edit.apply();

        synchronized (LOCK) {
            failureStreak = 0;
            nextStartElapsed = 0L;
            lastError = "";
            if (changed && process != null) {
                stopProcessLocked("configuration changed");
                if (preferences.getBoolean(KEY_REQUESTED, false)) {
                    nextStartElapsed = SystemClock.elapsedRealtime() + 800L;
                    state = gatewayRunning ? "connecting" : "waiting_local_api";
                }
            }
        }
        reconcile(app);
        result.putBoolean("accepted", true);
        putStatus(app, result);
        return result;
    }

    static Bundle setRequested(Context context, boolean requested) {
        Bundle result = new Bundle();
        if (context == null) {
            result.putBoolean("accepted", false);
            result.putString("error", "模块上下文不可用");
            return result;
        }
        Context app = context.getApplicationContext();
        applicationContext = app;
        SharedPreferences preferences = prefs(app);
        appendLog(app, "SET_REQUESTED requested=" + requested
                + " hasToken=" + hasToken(preferences)
                + " domainsLen=" + preferences.getString(KEY_DOMAINS, "").trim().length()
                + " closedV241=" + isClosedV241Runtime()
                + " hostV236=" + HostCompat.isV236()
                + " hostV241=" + HostCompat.isV241()
                + " binaryAvailable=" + binaryAvailable(app));
        if (requested) {
            if (isClosedV241Runtime() && !binaryAvailable(app)) {
                preferences.edit().putBoolean(KEY_REQUESTED, true).apply();
                synchronized (LOCK) {
                    state = "downloading";
                    lastError = "正在下载并校验 cloudflared 依赖";
                }
                startProvisioning(app);
                result.putBoolean("accepted", true);
                putStatus(app, result);
                return result;
            }
            if (!binaryAvailable(app)) {
                result.putBoolean("accepted", false);
                lastError = "当前安装包缺少与设备 ABI 匹配的 cloudflared";
                putStatus(app, result);
                return result;
            }
        }
        preferences.edit().putBoolean(KEY_REQUESTED, requested).apply();
        synchronized (LOCK) {
            failureStreak = 0;
            nextStartElapsed = 0L;
            lastError = "";
        }
        reconcile(app);
        result.putBoolean("accepted", true);
        putStatus(app, result);
        return result;
    }

    static Bundle status(Context context) {
        Bundle result = new Bundle();
        if (context == null) {
            result.putBoolean("accepted", false);
            result.putString("error", "模块上下文不可用");
            return result;
        }
        applicationContext = context.getApplicationContext();
        result.putBoolean("accepted", true);
        putStatus(applicationContext, result);
        return result;
    }

    static void onGatewayState(Context context, boolean localApiEnabled,
                               boolean running, int port) {
        if (context == null) return;
        applicationContext = context.getApplicationContext();
        gatewayRunning = localApiEnabled && running && port > 0;
        gatewayPort = port > 0 ? port : 0;
        reconcile(context.getApplicationContext());
    }

    static void shutdown(Context context) {
        synchronized (LOCK) {
            gatewayRunning = false;
            gatewayPort = 0;
            stopProcessLocked("foreground service stopped");
            state = context != null && prefs(context).getBoolean(KEY_REQUESTED, false)
                    ? "waiting_local_api" : "stopped";
        }
    }

    static boolean isConnected() {
        return "connected".equals(state) && process != null;
    }

    private static void reconcile(Context app) {
        synchronized (LOCK) {
            boolean requested = prefs(app).getBoolean(KEY_REQUESTED, false);
            if (!requested) {
                stopProcessLocked("disabled");
                state = "stopped";
                return;
            }
            if (!gatewayRunning || gatewayPort <= 0) {
                stopProcessLocked("local API unavailable");
                state = "waiting_local_api";
                return;
            }
            if (isClosedV241Runtime() && !binaryAvailable(app)) {
                state = "downloading";
                lastError = "正在下载并校验 cloudflared 依赖";
                startProvisioning(app);
                return;
            }
            if (process != null) return;
            if (SystemClock.elapsedRealtime() < nextStartElapsed) {
                state = "retry_wait";
                return;
            }
            startProcessLocked(app);
        }
    }

    private static void startProcessLocked(final Context app) {
        SharedPreferences preferences = prefs(app);
        File binary = binaryFile(app);
        if (!binaryAvailable(app)) {
            state = "error";
            lastError = isClosedV241Runtime()
                    ? "cloudflared 尚未下载或完整性校验失败"
                    : "安装包中没有适用于当前设备架构的 cloudflared";
            return;
        }
        String encrypted = preferences.getString(KEY_TOKEN_CIPHER, "");
        String token;
        try {
            token = decrypt(app, encrypted);
            validateCloudflareToken(token);
        } catch (Throwable t) {
            state = "error";
            lastError = "无法读取 Tunnel token：" + safeMessage(t);
            return;
        }

        try {
            File tokenDir = new File(app.getNoBackupFilesDir(), TOKEN_DIR);
            if (!tokenDir.isDirectory() && !tokenDir.mkdirs()) {
                throw new IllegalStateException("无法创建私有凭据目录");
            }
            tokenDir.setReadable(false, false);
            tokenDir.setWritable(false, false);
            tokenDir.setExecutable(false, false);
            tokenDir.setReadable(true, true);
            tokenDir.setWritable(true, true);
            tokenDir.setExecutable(true, true);
            File tokenFile = new File(tokenDir, TOKEN_FILE);
            FileOutputStream output = new FileOutputStream(tokenFile, false);
            try {
                output.write(token.getBytes(StandardCharsets.UTF_8));
                output.flush();
                try { output.getFD().sync(); } catch (Throwable ignored) {}
            } finally {
                try { output.close(); } catch (Throwable ignored) {}
            }
            tokenFile.setReadable(false, false);
            tokenFile.setWritable(false, false);
            tokenFile.setExecutable(false, false);
            tokenFile.setReadable(true, true);
            tokenFile.setWritable(true, true);
            activeTokenFile = tokenFile;

            List<String> command = new ArrayList<String>();
            command.add(binary.getAbsolutePath());
            command.add("tunnel");
            command.add("--no-autoupdate");
            command.add("--grace-period");
            command.add("2s");
            command.add("--loglevel");
            command.add("info");
            command.add("--transport-loglevel");
            command.add("warn");
            command.add("--metrics");
            command.add("127.0.0.1:0");
            command.add("--edge-ip-version");
            // cloudflared 2026.4 changed its default from IPv4 to auto. Android devices with an
            // unusable IPv6 route can then select an IPv6 edge and fail before fallback.
            command.add("4");
            String transport = normalizeTransport(
                    preferences.getString(KEY_TRANSPORT, TRANSPORT_AUTO));
            if (!TRANSPORT_AUTO.equals(transport)) {
                // cloudflared keeps this compatibility flag hidden in recent help text, but it is
                // still the supported way to force QUIC or HTTP/2 when one path is blocked.
                command.add("--protocol");
                command.add(transport);
            }
            command.add("run");
            // The Linux cloudflared binary cannot read Android's netd resolver configuration.
            // Without explicit resolvers Go falls back to localhost:53 ([::1] on this device),
            // where no DNS daemon is listening. Pass Android's active IPv4 DNS servers directly.
            List<String> resolvers = activeIpv4Resolvers(app);
            for (String resolver : resolvers) {
                command.add("--dns-resolver-addrs");
                command.add(resolver);
            }
            command.add("--token-file");
            command.add(tokenFile.getAbsolutePath());

            Process launched = startCloudflaredProcess(app, binary, command);
            process = launched;
            state = "connecting";
            lastError = "";
            startedAtWall = System.currentTimeMillis();
            connectedAtWall = 0L;
            appendLog(app, "START cloudflared=" + CLOUDFLARED_VERSION
                    + " launcher=" + launchMode + " transport=" + transport
                    + " edge_ip=4 dns="
                    + resolvers + " origin=http://127.0.0.1:" + gatewayPort);
            startReader(app, launched);
        } catch (Throwable t) {
            process = null;
            deleteActiveTokenFile();
            cleanupRootExecutableAsync();
            state = "error";
            lastError = "启动 cloudflared 失败：" + safeMessage(t);
            scheduleRetryLocked();
            appendLog(app, "START_FAILED " + lastError);
            Log.w(TAG, lastError, t);
        }
    }

    private static void startReader(final Context app, final Process launched) {
        Thread reader = new Thread(new Runnable() {
            @Override public void run() {
                int exitCode = -1;
                try {
                    BufferedReader input = new BufferedReader(new InputStreamReader(
                            launched.getInputStream(), StandardCharsets.UTF_8));
                    String line;
                    while ((line = input.readLine()) != null) {
                        handleProcessLine(app, launched, line);
                    }
                    exitCode = launched.waitFor();
                } catch (Throwable t) {
                    synchronized (LOCK) {
                        if (process == launched) {
                            lastError = "读取 cloudflared 状态失败：" + safeMessage(t);
                        }
                    }
                } finally {
                    synchronized (LOCK) {
                        if (process == launched) {
                            process = null;
                            deleteActiveTokenFile();
                            boolean requested = prefs(app).getBoolean(KEY_REQUESTED, false);
                            if (requested && gatewayRunning) {
                                state = "error";
                                if (lastError.length() == 0) {
                                    lastError = "cloudflared 已退出（code=" + exitCode + "）";
                                }
                                scheduleRetryLocked();
                            } else {
                                state = requested ? "waiting_local_api" : "stopped";
                            }
                            appendLog(app, "EXIT code=" + exitCode + " state=" + state);
                        }
                    }
                    cleanupRootExecutableAsync();
                }
            }
        }, "DeePX-cloudflared-log");
        reader.setDaemon(true);
        reader.start();
    }

    private static void handleProcessLine(Context app, Process launched, String raw) {
        String line = sanitizeLog(raw);
        if (line.length() == 0) return;
        appendLog(app, line);
        String lower = line.toLowerCase(Locale.US);
        synchronized (LOCK) {
            if (process != launched) return;
            if (lower.contains("registered tunnel connection")
                    || lower.contains("tunnel connection registered")) {
                state = "connected";
                connectedAtWall = System.currentTimeMillis();
                lastError = "";
                failureStreak = 0;
                nextStartElapsed = 0L;
                cleanupRootExecutableAsync();
            } else if (lower.contains("dns query failed")
                    || lower.contains("lookup v2-origintunnel")
                    || lower.contains("lookup cfd-features")) {
                state = "error";
                lastError = "Cloudflare DNS 解析失败；已绕过 Android 的 [::1]:53，"
                        + "请检查当前网络的 IPv4 DNS 是否可访问";
            } else if (lower.contains("hard_fail=true")
                    || lower.contains("environment has critical failures")) {
                state = "connecting";
                lastError = "当前网络无法连接 Cloudflare Tunnel 边缘：请允许出站 UDP/TCP 7844，"
                        + "或切换网络/传输方式";
            } else if (lower.contains("unable to establish connection with cloudflare edge")) {
                if (!"connected".equals(state)) state = "connecting";
                lastError = compactCloudflareError(line);
            } else if (lower.contains("invalid tunnel token")
                    || lower.contains("failed to parse tunnel token")
                    || lower.contains("unauthorized")) {
                state = "error";
                lastError = "Cloudflare 拒绝了 Tunnel token，请重新复制连接器 token";
            }
        }
    }

    private static String compactCloudflareError(String line) {
        if (line == null) return "无法连接 Cloudflare 边缘";
        String lower = line.toLowerCase(Locale.US);
        if (lower.contains("tls handshake") || lower.contains("eof")) {
            return "连接 Cloudflare 边缘时 TLS 握手被网络中断（通常是 7844 端口被拦截）";
        }
        if (lower.contains("quic")) {
            return "QUIC 连接失败，可在高级设置中尝试 HTTP/2";
        }
        return "暂时无法连接 Cloudflare 边缘，cloudflared 正在自动重试";
    }

    private static void stopProcessLocked(String reason) {
        Process active = process;
        process = null;
        connectedAtWall = 0L;
        deleteActiveTokenFile();
        cleanupRootExecutableAsync();
        if (active == null) return;
        appendLog(applicationContext, "STOP reason=" + reason);
        try { active.destroy(); } catch (Throwable ignored) {}
        final Process finishing = active;
        Thread reaper = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    long deadline = SystemClock.elapsedRealtime() + 4_000L;
                    boolean exited = false;
                    while (SystemClock.elapsedRealtime() < deadline) {
                        try {
                            finishing.exitValue();
                            exited = true;
                            break;
                        } catch (IllegalThreadStateException stillRunning) {
                            SystemClock.sleep(100L);
                        }
                    }
                    if (!exited) {
                        try {
                            java.lang.reflect.Method force = Process.class.getMethod(
                                    "destroyForcibly");
                            force.invoke(finishing);
                        } catch (Throwable ignored) {
                            try { finishing.destroy(); } catch (Throwable ignoredAgain) {}
                        }
                    }
                } catch (Throwable ignored) {
                    try { finishing.destroy(); } catch (Throwable ignoredAgain) {}
                }
            }
        }, "DeePX-cloudflared-stop");
        reaper.setDaemon(true);
        reaper.start();
    }

    private static void scheduleRetryLocked() {
        failureStreak = Math.min(6, failureStreak + 1);
        long delay = Math.min(60_000L, 3_000L << Math.min(4, failureStreak - 1));
        nextStartElapsed = SystemClock.elapsedRealtime() + delay;
    }

    private static void putStatus(Context app, Bundle out) {
        SharedPreferences preferences = prefs(app);
        String domains = preferences.getString(KEY_DOMAINS, "");
        String directRoot = preferences.getString(KEY_DIRECT_ROOT, "");
        boolean requested = preferences.getBoolean(KEY_REQUESTED, false);
        long retryMs = Math.max(0L, nextStartElapsed - SystemClock.elapsedRealtime());
        out.putBoolean("requested", requested);
        out.putBoolean("token_configured", hasToken(preferences));
        out.putBoolean("configured", hasToken(preferences) && domains.trim().length() > 0);
        out.putString("provider", PROVIDER_CLOUDFLARE);
        out.putString("domains", domains);
        out.putString("primary_root", firstPublicRoot(domains));
        out.putString("direct_root", directRoot == null ? "" : directRoot);
        out.putString("transport", normalizeTransport(
                preferences.getString(KEY_TRANSPORT, TRANSPORT_AUTO)));
        out.putString("state", state);
        out.putString("error", lastError == null ? "" : lastError);
        out.putString("recent_log", recentLogText());
        out.putBoolean("gateway_running", gatewayRunning);
        out.putInt("gateway_port", gatewayPort);
        out.putString("origin", "http://127.0.0.1:"
                + (gatewayPort > 0 ? gatewayPort : 8765));
        out.putBoolean("binary_available", binaryAvailable(app));
        out.putBoolean("binary_downloading", provisioning.get());
        out.putLong("download_received", provisioningBytes);
        out.putLong("download_total", provisioningTotal);
        out.putString("cloudflared_version", CLOUDFLARED_VERSION);
        out.putString("launcher", launchMode);
        out.putLong("started_at", startedAtWall);
        out.putLong("connected_at", connectedAtWall);
        out.putLong("retry_in_ms", retryMs);
        out.putString("log_file", new File(app.getFilesDir(), LOG_FILE).getAbsolutePath());
    }

    private static boolean hasToken(SharedPreferences preferences) {
        String cipher = preferences.getString(KEY_TOKEN_CIPHER, "");
        return cipher != null && cipher.length() > 20;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static File binaryFile(Context context) {
        if (isClosedV241Runtime()) {
            File root = new File(context.getNoBackupFilesDir(), BINARY_DIR);
            return new File(new File(root, CLOUDFLARED_VERSION + "-arm64"), "cloudflared");
        }
        String directory = context.getApplicationInfo().nativeLibraryDir;
        return new File(directory == null ? "" : directory, NATIVE_BINARY);
    }

    private static boolean binaryAvailable(Context context) {
        File binary = binaryFile(context);
        if (!isClosedV241Runtime()) return binary.isFile();
        File marker = new File(binary.getParentFile(), BINARY_MARKER);
        if (!binary.isFile() || binary.length() != DOWNLOAD_BYTES || !marker.isFile()) return false;
        try {
            FileInputStream input = new FileInputStream(marker);
            byte[] bytes = new byte[(int) Math.min(128L, marker.length())];
            int read;
            try { read = input.read(bytes); } finally { input.close(); }
            return read > 0 && DOWNLOAD_SHA256.equals(
                    new String(bytes, 0, read, StandardCharsets.US_ASCII).trim());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isClosedV241Runtime() {
        if (!BuildInfo.SHI_V5_V241) return false;
        if (HostCompat.isV236() || HostCompat.isV241()) return true;
        // HostCompat is only initialized inside the host process. The tunnel manager can run in
        // the module process, so fall back to the target version code reported by the host.
        Context app = applicationContext;
        long reported = app == null ? 0L : XposedActivationProvider.targetVersionCode(app);
        return reported == BuildInfo.SHI_V5_HOST_VERSION_CODE
                || reported == BuildInfo.SHI_V5_V236_HOST_VERSION_CODE;
    }

    private static void startProvisioning(final Context context) {
        appendLog(context, "START_PROVISIONING closedV241=" + isClosedV241Runtime()
                + " provisioning=" + provisioning.get());
        if (!isClosedV241Runtime() || context == null
                || !provisioning.compareAndSet(false, true)) return;
        final Context app = context.getApplicationContext();
        provisioningBytes = 0L;
        provisioningTotal = DOWNLOAD_BYTES;
        provisioningCancelled = false;
        provisioningConnection = null;
        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                File temporary = null;
                HttpURLConnection connection = null;
                try {
                    if (!supportsArm64()) throw new IllegalStateException(
                            "当前设备不是 arm64-v8a，code249/code257 精简版不提供此 ABI");
                    File target = binaryFile(app);
                    File directory = target.getParentFile();
                    if (!directory.isDirectory() && !directory.mkdirs()) {
                        throw new IllegalStateException("无法创建 cloudflared 私有槽位");
                    }
                    temporary = new File(directory, "cloudflared.download");
                    if (temporary.exists() && !temporary.delete()) {
                        throw new IllegalStateException("无法替换未完成的依赖下载");
                    }
                    connection = openTrustedDownload(new URL(DOWNLOAD_URL));
                    provisioningConnection = connection;
                    long declared = connection.getContentLengthLong();
                    if (declared > 0L && declared != DOWNLOAD_BYTES) {
                        throw new IllegalStateException("下载大小与固定版本不一致：" + declared);
                    }
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    InputStream input = connection.getInputStream();
                    FileOutputStream output = new FileOutputStream(temporary, false);
                    long total = 0L;
                    try {
                        byte[] buffer = new byte[64 * 1024];
                        int read;
                        while ((read = input.read(buffer)) >= 0) {
                            if (provisioningCancelled) {
                                throw new IllegalStateException("下载已取消");
                            }
                            total += read;
                            if (total > MAX_DOWNLOAD_BYTES) {
                                throw new IllegalStateException("cloudflared 下载超过严格大小上限");
                            }
                            output.write(buffer, 0, read);
                            digest.update(buffer, 0, read);
                            provisioningBytes = total;
                        }
                        output.flush();
                        output.getFD().sync();
                    } finally {
                        try { input.close(); } catch (Throwable ignored) {}
                        try { output.close(); } catch (Throwable ignored) {}
                    }
                    String actual = hex(digest.digest());
                    if (total != DOWNLOAD_BYTES || !DOWNLOAD_SHA256.equals(actual)) {
                        throw new IllegalStateException("cloudflared SHA-256/大小校验失败");
                    }
                    FileInputStream elf = new FileInputStream(temporary);
                    byte[] magic = new byte[4];
                    int magicRead;
                    try { magicRead = elf.read(magic); } finally { elf.close(); }
                    if (magicRead != 4 || magic[0] != 0x7f || magic[1] != 'E'
                            || magic[2] != 'L' || magic[3] != 'F') {
                        throw new IllegalStateException("下载内容不是 ELF 可执行文件");
                    }
                    if (!temporary.setReadable(true, true)
                            || !temporary.setWritable(true, true)
                            || !temporary.setExecutable(true, true)) {
                        throw new IllegalStateException("无法设置依赖的私有执行权限");
                    }
                    if (target.exists() && !target.delete()) {
                        throw new IllegalStateException("无法替换旧版 cloudflared");
                    }
                    if (!temporary.renameTo(target)) {
                        throw new IllegalStateException("cloudflared 原子发布失败");
                    }
                    temporary = null;
                    target.setReadable(true, true);
                    target.setWritable(false, false);
                    target.setExecutable(true, true);
                    File marker = new File(directory, BINARY_MARKER);
                    File markerTemporary = new File(directory, BINARY_MARKER + ".tmp");
                    FileOutputStream markerOutput = new FileOutputStream(markerTemporary, false);
                    try {
                        markerOutput.write((DOWNLOAD_SHA256 + "\n").getBytes(
                                StandardCharsets.US_ASCII));
                        markerOutput.flush();
                        markerOutput.getFD().sync();
                    } finally { markerOutput.close(); }
                    if (marker.exists() && !marker.delete()) {
                        throw new IllegalStateException("无法替换依赖校验标记");
                    }
                    if (!markerTemporary.renameTo(marker)) {
                        throw new IllegalStateException("依赖校验标记发布失败");
                    }
                    synchronized (LOCK) {
                        state = gatewayRunning ? "connecting" : "waiting_local_api";
                        lastError = "";
                        nextStartElapsed = 0L;
                    }
                    appendLog(app, "PROVISION_OK cloudflared=" + CLOUDFLARED_VERSION
                            + " bytes=" + total + " sha256=" + actual);
                } catch (Throwable error) {
                    synchronized (LOCK) {
                        state = provisioningCancelled ? "stopped" : "error";
                        lastError = provisioningCancelled
                                ? ""
                                : "cloudflared 下载/校验失败：" + safeMessage(error)
                                        + "；本地 API 不受影响，可关闭后重试";
                    }
                    appendLog(app, provisioningCancelled
                            ? "PROVISION_CANCELLED"
                            : "PROVISION_FAILED " + lastError);
                    if (temporary != null && temporary.exists()) temporary.delete();
                } finally {
                    if (connection != null) connection.disconnect();
                    provisioningConnection = null;
                    provisioning.set(false);
                    if (binaryAvailable(app)) reconcile(app);
                }
            }
        }, "DeePX-cloudflared-provision");
        worker.setDaemon(true);
        worker.start();
    }

    static void cancelProvisioning() {
        provisioningCancelled = true;
        HttpURLConnection connection = provisioningConnection;
        if (connection != null) {
            try { connection.disconnect(); } catch (Throwable ignored) {}
        }
        Context app = applicationContext;
        if (app != null) {
            prefs(app).edit().putBoolean(KEY_REQUESTED, false).apply();
            synchronized (LOCK) {
                state = "stopped";
                lastError = "";
                nextStartElapsed = 0L;
            }
        }
    }

    private static HttpURLConnection openTrustedDownload(URL initial) throws Exception {
        URL current = initial;
        for (int redirects = 0; redirects <= 5; redirects++) {
            if (!"https".equalsIgnoreCase(current.getProtocol()) || !trustedDownloadHost(
                    current.getHost())) throw new IllegalStateException("拒绝不可信下载地址");
            HttpsURLConnection connection = (HttpsURLConnection) current.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(30_000);
            connection.setRequestProperty("Accept", "application/octet-stream");
            connection.setRequestProperty("User-Agent", "DeePX-code257/" + BuildInfo.MODULE_VERSION);
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null) throw new IllegalStateException("下载重定向缺少地址");
                current = new URL(current, location);
                continue;
            }
            if (status != 200) {
                connection.disconnect();
                throw new IllegalStateException("下载服务器返回 HTTP " + status);
            }
            return connection;
        }
        throw new IllegalStateException("下载重定向次数过多");
    }

    private static boolean trustedDownloadHost(String host) {
        if (host == null) return false;
        String value = host.toLowerCase(Locale.US);
        return "github.com".equals(value)
                || value.endsWith(".githubusercontent.com")
                || value.endsWith(".github.com");
    }

    private static boolean supportsArm64() {
        for (String abi : Build.SUPPORTED_ABIS) if ("arm64-v8a".equals(abi)) return true;
        return false;
    }

    /**
     * Android 10+ forbids direct execve from an app-writable directory for targetSdk 29+.
     * Keep the verified download in the private versioned slot, then use the existing root
     * environment only as an execution bridge when the platform returns EACCES. Legacy hosts
     * continue to execute their APK-bundled nativeLibraryDir binary directly.
     */
    private static Process startCloudflaredProcess(
            Context app, File binary, List<String> command) throws Exception {
        ProcessBuilder direct = new ProcessBuilder(command);
        direct.directory(app.getFilesDir());
        direct.redirectErrorStream(true);
        try {
            Process launched = direct.start();
            launchMode = "direct";
            return launched;
        } catch (IOException denied) {
            if (!isClosedV241Runtime() || !isExecutionDenied(denied)) throw denied;
            appendLog(app, "DIRECT_EXEC_BLOCKED using verified root execution bridge");
        }

        String su = rootBinary();
        if (su == null) {
            throw new IOException("Android 禁止从私有下载槽执行；当前未检测到 Root，"
                    + "请改用 Pinggy 或在 Root 环境中重试");
        }
        String staged = "/data/local/tmp/deekseep-cloudflared-"
                + app.getApplicationInfo().uid + "-" + CLOUDFLARED_VERSION;
        StringBuilder shell = new StringBuilder(512);
        shell.append("cp -- ").append(shellQuote(binary.getAbsolutePath())).append(' ')
                .append(shellQuote(staged))
                .append(" && chmod 0700 ").append(shellQuote(staged))
                .append(" && exec ").append(shellQuote(staged));
        for (int i = 1; i < command.size(); i++) {
            shell.append(' ').append(shellQuote(command.get(i)));
        }
        ProcessBuilder root = new ProcessBuilder(su, "-c", shell.toString());
        root.directory(app.getFilesDir());
        root.redirectErrorStream(true);
        Process launched = root.start();
        activeRootExecutable = staged;
        launchMode = "root-slot";
        return launched;
    }

    private static boolean isExecutionDenied(IOException error) {
        String value = String.valueOf(error.getMessage()).toLowerCase(Locale.US);
        return value.contains("permission denied") || value.contains("eacces")
                || value.contains("error=13");
    }

    private static String rootBinary() {
        String[] candidates = new String[]{"/system/bin/su", "/system/xbin/su"};
        for (String candidate : candidates) if (new File(candidate).canExecute()) return candidate;
        return null;
    }

    private static String shellQuote(String value) {
        if (value == null) return "''";
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static void cleanupRootExecutableAsync() {
        final String staged = activeRootExecutable;
        activeRootExecutable = null;
        final String su = rootBinary();
        if (staged == null || su == null
                || !staged.startsWith("/data/local/tmp/deekseep-cloudflared-")) return;
        Thread cleanup = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    Process removal = new ProcessBuilder(
                            su, "-c", "rm -- " + shellQuote(staged)).start();
                    removal.waitFor();
                } catch (Throwable ignored) {}
            }
        }, "DeePX-cloudflared-slot-cleanup");
        cleanup.setDaemon(true);
        cleanup.start();
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(String.format(Locale.US, "%02x", value));
        return out.toString();
    }

    private static void deleteActiveTokenFile() {
        File token = activeTokenFile;
        activeTokenFile = null;
        if (token != null) {
            try {
                if (token.exists() && !token.delete()) token.deleteOnExit();
            } catch (Throwable ignored) {}
        }
    }

    private static String normalizeTransport(String value) {
        if (TRANSPORT_HTTP2.equalsIgnoreCase(value)) return TRANSPORT_HTTP2;
        if (TRANSPORT_QUIC.equalsIgnoreCase(value)) return TRANSPORT_QUIC;
        return TRANSPORT_AUTO;
    }

    /** Returns reachable Android/netd IPv4 resolvers in cloudflared's required ip:port format. */
    private static List<String> activeIpv4Resolvers(Context context) {
        LinkedHashSet<String> out = new LinkedHashSet<String>();
        try {
            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(
                    Context.CONNECTIVITY_SERVICE);
            Network active = manager == null ? null : manager.getActiveNetwork();
            LinkProperties properties = manager == null || active == null
                    ? null : manager.getLinkProperties(active);
            if (properties != null) {
                for (InetAddress address : properties.getDnsServers()) {
                    if (!(address instanceof Inet4Address) || address.isAnyLocalAddress()
                            || address.isLoopbackAddress()) continue;
                    out.add(address.getHostAddress() + ":53");
                    if (out.size() >= 4) break;
                }
            }
        } catch (Throwable error) {
            Log.w(TAG, "Could not read Android DNS servers", error);
        }
        // Some OEMs hide LinkProperties from secondary app processes. A direct IPv4 resolver is
        // still better than Go's guaranteed-broken [::1]:53 fallback on Android.
        if (out.isEmpty()) {
            out.add("1.1.1.1:53");
            out.add("1.0.0.1:53");
        }
        return new ArrayList<String>(out);
    }

    private static String normalizeCloudflareToken(String input) {
        String value = input == null ? "" : input.trim();
        if (value.length() == 0) throw new IllegalArgumentException("token 为空");
        value = value.replace('\r', ' ').replace('\n', ' ').trim();
        String[] parts = value.split("\\s+");
        for (int i = 0; i < parts.length; i++) {
            String part = unquote(parts[i]);
            if ("--token".equals(part) && i + 1 < parts.length) {
                return unquote(parts[i + 1]);
            }
        }
        if (parts.length > 1) {
            for (int i = parts.length - 1; i >= 0; i--) {
                String part = unquote(parts[i]);
                if (part.length() >= 80 && part.indexOf('-') != 0) return part;
            }
        }
        return unquote(value);
    }

    private static String unquote(String value) {
        String out = value == null ? "" : value.trim();
        while (out.length() >= 2
                && ((out.charAt(0) == '"' && out.charAt(out.length() - 1) == '"')
                || (out.charAt(0) == '\'' && out.charAt(out.length() - 1) == '\''))) {
            out = out.substring(1, out.length() - 1).trim();
        }
        return out;
    }

    private static void validateCloudflareToken(String token) throws Exception {
        if (token == null || token.length() < 80 || token.length() > 4096) {
            throw new IllegalArgumentException("token 长度不正确");
        }
        byte[] decoded;
        try {
            decoded = Base64.decode(token, Base64.DEFAULT);
        } catch (Throwable first) {
            decoded = Base64.decode(token, Base64.URL_SAFE | Base64.NO_WRAP);
        }
        JSONObject value = new JSONObject(new String(decoded, StandardCharsets.UTF_8));
        boolean compact = value.optString("a", "").length() > 0
                && value.optString("t", "").length() > 0
                && value.optString("s", "").length() > 0;
        boolean verbose = value.optString("accountTag", "").length() > 0
                && value.optString("tunnelID", "").length() > 0
                && value.optString("tunnelSecret", "").length() > 0;
        if (!compact && !verbose) {
            throw new IllegalArgumentException("不是 Cloudflare Tunnel connector token");
        }
    }

    private static String normalizeDomains(String raw) {
        String value = raw == null ? "" : raw.replace(',', '\n').replace(';', '\n');
        String[] lines = value.split("\\r?\\n");
        Set<String> domains = new LinkedHashSet<String>();
        for (String line : lines) {
            String host = normalizeDomain(line);
            if (host.length() == 0) continue;
            domains.add(host);
            if (domains.size() > MAX_DOMAINS) {
                throw new IllegalArgumentException("最多保存 " + MAX_DOMAINS + " 个域名");
            }
        }
        StringBuilder out = new StringBuilder();
        for (String domain : domains) {
            if (out.length() > 0) out.append('\n');
            out.append(domain);
        }
        return out.toString();
    }

    private static String normalizeDomain(String input) {
        String value = input == null ? "" : input.trim();
        if (value.length() == 0) return "";
        if (value.startsWith("http://") || value.startsWith("https://")) {
            try {
                URI uri = URI.create(value);
                value = uri.getHost();
            } catch (Throwable t) {
                throw new IllegalArgumentException("域名格式不正确：" + input);
            }
        }
        if (value == null) throw new IllegalArgumentException("域名格式不正确：" + input);
        int slash = value.indexOf('/');
        if (slash >= 0) value = value.substring(0, slash);
        int colon = value.lastIndexOf(':');
        if (colon > 0 && value.indexOf(':') == colon) value = value.substring(0, colon);
        value = value.trim();
        if (value.startsWith("*.")) {
            throw new IllegalArgumentException("请填写可直接访问的完整域名，不要填写通配符：" + value);
        }
        String ascii;
        try {
            ascii = IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES)
                    .toLowerCase(Locale.US);
        } catch (Throwable t) {
            throw new IllegalArgumentException("域名格式不正确：" + value);
        }
        if (ascii.length() < 3 || ascii.length() > 253 || ascii.indexOf('.') <= 0) {
            throw new IllegalArgumentException("域名格式不正确：" + value);
        }
        String[] labels = ascii.split("\\.");
        for (String label : labels) {
            if (label.length() == 0 || label.length() > 63
                    || label.startsWith("-") || label.endsWith("-")
                    || !label.matches("[a-z0-9-]+")) {
                throw new IllegalArgumentException("域名格式不正确：" + value);
            }
        }
        return ascii;
    }

    private static String normalizeDirectRoot(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() == 0) return "";
        if (!value.contains("://")) value = "http://" + value;
        URI uri;
        try {
            uri = URI.create(value);
        } catch (Throwable t) {
            throw new IllegalArgumentException("公网 IP/直连地址格式不正确");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.US);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("公网直连地址只支持 http:// 或 https://");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("公网 IP/直连地址格式不正确");
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        if ("/v1".equals(path) || "/v1/".equals(path)) path = "";
        if (path.length() > 0 && !"/".equals(path)) {
            throw new IllegalArgumentException("直连地址不能带自定义路径；OpenAI 的 /v1 会自动补上");
        }
        String host = uri.getHost();
        if (host.indexOf(':') >= 0 && !host.startsWith("[")) host = "[" + host + "]";
        int port = uri.getPort();
        return scheme + "://" + host + (port > 0 ? ":" + port : "");
    }

    private static String firstPublicRoot(String domains) {
        if (domains == null || domains.trim().length() == 0) return "";
        int line = domains.indexOf('\n');
        String first = line < 0 ? domains : domains.substring(0, line);
        return "https://" + first.trim();
    }

    private static String encrypt(Context context, String plain) throws Exception {
        SecretKey key = secretKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        byte[] blob = new byte[1 + iv.length + encrypted.length];
        blob[0] = (byte) iv.length;
        System.arraycopy(iv, 0, blob, 1, iv.length);
        System.arraycopy(encrypted, 0, blob, 1 + iv.length, encrypted.length);
        return Base64.encodeToString(blob, Base64.NO_WRAP);
    }

    private static String decrypt(Context context, String encoded) throws Exception {
        if (encoded == null || encoded.length() == 0) {
            throw new IllegalArgumentException("尚未保存 token");
        }
        byte[] blob = Base64.decode(encoded, Base64.NO_WRAP);
        int ivLength = blob.length == 0 ? 0 : blob[0] & 0xff;
        if (ivLength < 12 || blob.length <= 1 + ivLength) {
            throw new IllegalArgumentException("加密凭据已损坏");
        }
        byte[] iv = new byte[ivLength];
        byte[] encrypted = new byte[blob.length - 1 - ivLength];
        System.arraycopy(blob, 1, iv, 0, ivLength);
        System.arraycopy(blob, 1 + ivLength, encrypted, 0, encrypted.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private static SecretKey secretKey() throws Exception {
        KeyStore store = KeyStore.getInstance(KEYSTORE);
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) {
            KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry)
                    store.getEntry(KEY_ALIAS, null);
            if (entry != null && entry.getSecretKey() != null) return entry.getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }

    private static String sanitizeLog(String raw) {
        if (raw == null) return "";
        String line = raw.replaceAll("\\u001B\\[[;\\d]*[ -/]*[@-~]", "").trim();
        line = line.replaceAll("eyJ[A-Za-z0-9_+=/.-]{48,}", "<redacted-token>");
        if (line.length() > 1200) line = line.substring(0, 1200) + "…";
        return line;
    }

    private static void appendLog(Context context, String raw) {
        String line = sanitizeLog(raw);
        if (line.length() == 0) return;
        synchronized (recentLogs) {
            recentLogs.addLast(line);
            while (recentLogs.size() > MAX_LOG_LINES) recentLogs.removeFirst();
        }
        if (context == null) return;
        try {
            File log = new File(context.getFilesDir(), LOG_FILE);
            if (log.isFile() && log.length() > MAX_LOG_BYTES) {
                File old = new File(context.getFilesDir(), LOG_FILE + ".old");
                if (old.exists()) old.delete();
                log.renameTo(old);
            }
            FileWriter writer = new FileWriter(log, true);
            try {
                writer.write(System.currentTimeMillis() + " " + line + "\n");
            } finally {
                writer.close();
            }
        } catch (Throwable ignored) {}
    }

    private static String recentLogText() {
        StringBuilder out = new StringBuilder();
        synchronized (recentLogs) {
            int skip = Math.max(0, recentLogs.size() - 12);
            int index = 0;
            for (String line : recentLogs) {
                if (index++ < skip) continue;
                if (out.length() > 0) out.append('\n');
                out.append(line);
            }
        }
        return out.toString();
    }

    private static String safeMessage(Throwable t) {
        if (t == null) return "unknown";
        String message = t.getMessage();
        return message == null || message.trim().length() == 0
                ? t.getClass().getSimpleName() : sanitizeLog(message);
    }
}
