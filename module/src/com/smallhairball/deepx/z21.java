package com.smallhairball.deepx;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

/**
 * Foreground companion for the localhost API.
 *
 * <p>The actual gateway must stay inside DeepSeek because it calls obfuscated native transport
 * objects captured by the Xposed hooks. A cached activity process, however, can be frozen even
 * after the OEM battery screen reports "unrestricted". This small companion periodically sends
 * an explicit no-op broadcast. ActivityManager unfreezes the target before delivering it, and the
 * injected receiver hook consumes it before DeepSeek's share-result implementation can see it.</p>
 */
public final class z21 extends Service {
    static final String TARGET_PACKAGE = "com.deepseek.chat";
    static final String TARGET_RECEIVER = "com.deepseek.chat.system.ShareResultReceiver";
    static final String ACTION_HEARTBEAT = "com.smallhairball.deepx.action.LOCAL_API_KEEPALIVE";
    static final String ACTION_CONTROL = "com.smallhairball.deepx.action.LOCAL_API_CONTROL";

    static final String ACTION_START = "com.smallhairball.deepx.action.START_LOCAL_API_KEEPALIVE";
    static final String EXTRA_CONTROL_TOKEN = "dk_ctrl";
    static final String EXTRA_PROTOCOL = "protocol";
    static final String EXTRA_OPERATION = "operation";
    static final String EXTRA_VALUE = "value";
    static final String EXTRA_OVERLAY_REQUESTED = "overlay_requested";
    static final String EXTRA_OVERLAY_PERMISSION = "overlay_permission";
    static final String EXTRA_OVERLAY_ONLY = "overlay_only";
    static final String EXTRA_API_KEEPALIVE_REQUESTED = "api_keepalive_requested";
    static final String EXTRA_FREEZER_BRIDGE_V236 = "freezer_bridge_v236";
    static final String EXTRA_FREEZER_BRIDGE_V241 = "freezer_bridge_v241";
    static final String OP_SET_ENABLED = "set_enabled";
    static final String OP_SET_PROTOCOL = "set_protocol";
    static final String OP_SET_KEY = "set_key";
    static final String OP_ROTATE_KEY = "rotate_key";
    static final String OP_SET_PORT = "set_port";
    static final String OP_SET_HTTPS = "set_https";
    static final String DETAIL_CONNECTION = "connection";
    static final String DETAIL_RUNTIME = "runtime";
    static final String DETAIL_API_KEY = "api_key";
    static final String DETAIL_PROTOCOL = "protocol";
    static final String DETAIL_FREEZER_LOCK_V236 = "freezer_lock_v236";
    static final String DETAIL_FREEZER_LOCK_V241 = "freezer_lock_v241";
    static final int FREEZER_BRIDGE_TRANSACTION_V236 = 0x44534B30;
    static final int FREEZER_BRIDGE_TRANSACTION_V241 = 0x44534B31;
    static final String CONTROL_TOKEN = "dk-ka-v1";
    private static final String PREFS = "dq0_keepalive";
    private static final String KEY_REQUESTED = "requested";
    private static final String CHANNEL_ID = "dq0";
    private static final int NOTIFICATION_ID = 0xD5A1;
    private static final long HEARTBEAT_MS = 5_000L;
    private static final long ACK_TIMEOUT_MS = 90_000L;
    private static final String TAG = "dk";

    private static volatile boolean running;
    private static volatile long startedElapsed;
    private static volatile long lastBroadcastElapsed;
    private static volatile long lastAckElapsed;
    private static volatile boolean lastGatewayRunning;
    private static volatile String lastError = "";
    private static volatile String lastConnectionInfo = "等待 DeepSeek 状态";
    private static volatile String lastRuntimeStatus = "尚未收到实时数据";
    private static volatile String lastApiKey = "";
    private static volatile String lastProtocol = "openai";
    private static volatile int lastGatewayPort;
    // Populated only by the exact DeepSeek 2.4.1/code257 host adapter. gateway_port is the
    // currently advertised endpoint (HTTPS may be preferred+1), while this value remains what
    // the user entered in the listener-port field.
    private static volatile boolean lastV241PortDetails;
    private static volatile int lastV241PreferredPort;
    private static volatile int lastV241HttpPort;
    private static volatile int lastV241HttpsPort;
    private static volatile boolean lastHttpsEnabled;
    private static volatile boolean lastApiEnabled;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wakeLock;
    private final Object freezerBridgeLock = new Object();
    private RandomAccessFile freezerBridgeFile;
    private FileChannel freezerBridgeChannel;
    private RandomAccessFile freezerWaitFile;
    private FileChannel freezerWaitChannel;
    private Thread freezerWaiter;

    private final Object freezerBridgeLockV236 = new Object();
    private RandomAccessFile freezerBridgeFileV236;
    private FileChannel freezerBridgeChannelV236;
    private RandomAccessFile freezerWaitFileV236;
    private FileChannel freezerWaitChannelV236;
    private Thread freezerWaiterV236;

    private final IBinder freezerBridgeV236 = new Binder() {
        @Override protected boolean onTransact(
                int code, Parcel data, Parcel reply, int flags) {
            if (code != FREEZER_BRIDGE_TRANSACTION_V236 || reply == null) return false;
            ParcelFileDescriptor duplicate = null;
            try {
                String token = data == null ? null : data.readString();
                if (!CONTROL_TOKEN.equals(token)) throw new SecurityException("invalid token");
                synchronized (freezerBridgeLockV236) {
                    ensureFreezerBridgeChannelV236Locked();
                    duplicate = ParcelFileDescriptor.dup(
                            freezerBridgeFileV236.getFD());
                }
                reply.writeNoException();
                duplicate.writeToParcel(reply, 0);
                return true;
            } catch (Throwable error) {
                reply.writeException(new IllegalStateException(safeMessage(error)));
                return true;
            } finally {
                if (duplicate != null) try { duplicate.close(); } catch (Throwable ignored) {}
            }
        }
    };

    private final IBinder freezerBridgeV241 = new Binder() {
        @Override protected boolean onTransact(
                int code, Parcel data, Parcel reply, int flags) {
            if (code != FREEZER_BRIDGE_TRANSACTION_V241 || reply == null) return false;
            ParcelFileDescriptor duplicate = null;
            try {
                String token = data == null ? null : data.readString();
                if (!CONTROL_TOKEN.equals(token)) throw new SecurityException("invalid token");
                synchronized (freezerBridgeLock) {
                    ensureFreezerBridgeChannelLocked();
                    duplicate = ParcelFileDescriptor.dup(freezerBridgeFile.getFD());
                }
                reply.writeNoException();
                duplicate.writeToParcel(reply, 0);
                return true;
            } catch (Throwable error) {
                reply.writeException(new IllegalStateException(safeMessage(error)));
                return true;
            } finally {
                if (duplicate != null) try { duplicate.close(); } catch (Throwable ignored) {}
            }
        }
    };

    private final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            if (!isRequested(z21.this) && !LocalApiFloatingOverlay.isRequested(z21.this)) {
                stopSelf();
                return;
            }
            long now = SystemClock.elapsedRealtime();
            if (startedElapsed > 0L && now - startedElapsed >= ACK_TIMEOUT_MS
                    && (lastAckElapsed <= 0L || now - lastAckElapsed >= ACK_TIMEOUT_MS)) {
                lastError = LocalApiFloatingOverlay.isRequested(z21.this)
                        ? "DeepSeek 长时间未响应；小窗仍在等待连接"
                        : "DeepSeek 长时间未确认保活，服务已自动停止";
                Log.w(TAG, lastError);
                if (!LocalApiFloatingOverlay.isRequested(z21.this)) {
                    requestedPrefs(z21.this).edit()
                            .putBoolean(KEY_REQUESTED, false).apply();
                    stopSelf();
                    return;
                }
            }
            try {
                Intent ping = new Intent(ACTION_HEARTBEAT);
                ping.setComponent(new ComponentName(TARGET_PACKAGE, TARGET_RECEIVER));
                ping.putExtra(EXTRA_CONTROL_TOKEN, CONTROL_TOKEN);
                ping.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                ping.putExtra(EXTRA_OVERLAY_REQUESTED,
                        LocalApiFloatingOverlay.isRequested(z21.this));
                ping.putExtra(EXTRA_OVERLAY_PERMISSION, LocalApiFloatingOverlay.hasPermission(z21.this));
                ping.putExtra(EXTRA_API_KEEPALIVE_REQUESTED, isRequested(z21.this));
                long installedHostCode = installedHostCode(z21.this);
                if (installedHostCode == 249L) {
                    Bundle bridge = new Bundle();
                    bridge.putBinder(EXTRA_FREEZER_BRIDGE_V236, freezerBridgeV236);
                    ping.putExtras(bridge);
                } else if (lastV241PortDetails) {
                    Bundle bridge = new Bundle();
                    bridge.putBinder(EXTRA_FREEZER_BRIDGE_V241, freezerBridgeV241);
                    ping.putExtras(bridge);
                }
                sendOrderedBroadcast(ping, null, new BroadcastReceiver() {
                    @Override public void onReceive(Context context, Intent intent) {
                        if (getResultCode() != Activity.RESULT_OK) return;
                        String state = getResultData();
                        lastAckElapsed = SystemClock.elapsedRealtime();
                        android.os.Bundle details = getResultExtras(false);
                        applyStatusDetails(details);
                        syncV236FreezerWaiter(details);
                        syncV241FreezerWaiter(details);
                        if (details == null || !details.containsKey("gateway_running")) {
                            lastGatewayRunning = state != null && state.endsWith("|running");
                        }
                        if (details == null || !details.containsKey("api_enabled")) {
                            lastApiEnabled = state != null && state.startsWith("enabled|");
                        }
                        int gatewayPort = lastGatewayPort;
                        boolean enabled = state != null && state.startsWith("enabled|");
                        PublicTunnelManager.onGatewayState(
                                z21.this, enabled,
                                lastGatewayRunning, gatewayPort);
                        PinggyTunnelManager.onGatewayState(
                                z21.this, enabled,
                                lastGatewayRunning, gatewayPort);
                        if (state != null && state.startsWith("disabled|")) {
                            requestedPrefs(z21.this).edit()
                                    .putBoolean(KEY_REQUESTED, false).apply();
                            if (!LocalApiFloatingOverlay.isRequested(z21.this)) stopSelf();
                        }
                        LocalApiFloatingOverlay.sync(z21.this);
                        LocalApiFloatingOverlay.refresh();
                    }
                }, handler, Activity.RESULT_CANCELED, null, null);
                lastBroadcastElapsed = now;
            } catch (Throwable t) {
                lastError = "发送保活心跳失败：" + safeMessage(t);
                Log.w(TAG, lastError, t);
            }
            handler.postDelayed(this, HEARTBEAT_MS);
        }
    };

    static boolean setEnabled(Context context, boolean enabled) {
        if (context == null) {
            lastError = "模块上下文不可用";
            return false;
        }
        Context app = context.getApplicationContext();
        requestedPrefs(app).edit().putBoolean(KEY_REQUESTED, enabled).apply();
        Intent intent = new Intent(app, z21.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_CONTROL_TOKEN, CONTROL_TOKEN);
        try {
            if (enabled) {
                if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(intent);
                else app.startService(intent);
            } else if (LocalApiFloatingOverlay.isRequested(app)) {
                // The floating console is itself a user-requested foreground surface. Keep this
                // service alive so the user can turn the API back on from the edge panel.
                LocalApiFloatingOverlay.sync(app);
            } else {
                app.stopService(intent);
            }
            return true;
        } catch (Throwable t) {
            lastError = (enabled ? "启动" : "停止") + "前台保活失败：" + safeMessage(t);
            Log.w(TAG, lastError, t);
            return false;
        }
    }

    /** Starts or stops the same foreground owner without changing the API keepalive preference. */
    static boolean syncOverlay(Context context) {
        if (context == null) return false;
        Context app = context.getApplicationContext();
        boolean requested = LocalApiFloatingOverlay.isRequested(app);
        Intent intent = new Intent(app, z21.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_CONTROL_TOKEN, CONTROL_TOKEN)
                .putExtra(EXTRA_OVERLAY_ONLY, true);
        try {
            if (requested) {
                if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(intent);
                else app.startService(intent);
            } else {
                LocalApiFloatingOverlay.dismiss();
                if (!isRequested(app)) app.stopService(intent);
            }
            return true;
        } catch (Throwable t) {
            lastError = "同步小窗保活失败：" + safeMessage(t);
            Log.w(TAG, lastError, t);
            return false;
        }
    }

    static void sendProtocolControl(Context context, String protocol) {
        if (context == null) return;
        Intent control = new Intent(ACTION_CONTROL)
                .setComponent(new ComponentName(TARGET_PACKAGE, TARGET_RECEIVER))
                .putExtra(EXTRA_CONTROL_TOKEN, CONTROL_TOKEN)
                .putExtra(EXTRA_PROTOCOL, protocol)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        context.sendBroadcast(control);
    }

    static void acknowledge(boolean enabled, boolean gatewayRunning) {
        lastAckElapsed = SystemClock.elapsedRealtime();
        lastGatewayRunning = gatewayRunning;
        if (!enabled) lastError = "DeepSeek 已关闭本地 API";
    }

    interface ControlCallback { void onResult(boolean success, String message); }

    static void sendGatewayControl(Context context, String operation, String value,
                                   final ControlCallback callback) {
        if (context == null) {
            if (callback != null) callback.onResult(false, "模块上下文不可用");
            return;
        }
        Intent control = new Intent(ACTION_CONTROL)
                .setComponent(new ComponentName(TARGET_PACKAGE, TARGET_RECEIVER))
                .putExtra(EXTRA_CONTROL_TOKEN, CONTROL_TOKEN)
                .putExtra(EXTRA_OPERATION, operation)
                .putExtra(EXTRA_VALUE, value == null ? "" : value)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        context.sendOrderedBroadcast(control, null, new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                boolean success = getResultCode() == Activity.RESULT_OK;
                String message = getResultData();
                applyStatusDetails(getResultExtras(false));
                if (success && OP_SET_ENABLED.equals(operation)) {
                    setEnabled(c, Boolean.parseBoolean(value));
                }
                if (message == null || message.length() == 0) {
                    message = success ? "设置已保存" : "DeepSeek 未返回设置结果";
                }
                if (callback != null) callback.onResult(success, message);
                handlerSafeRefresh();
            }
        }, new Handler(Looper.getMainLooper()), Activity.RESULT_CANCELED,
                "等待 DeepSeek 响应", null);
    }

    private static void handlerSafeRefresh() {
        LocalApiFloatingOverlay.refresh();
    }

    private static void applyStatusDetails(android.os.Bundle details) {
        if (details == null) return;
        if (details.containsKey("gateway_running")) {
            lastGatewayRunning = details.getBoolean("gateway_running", lastGatewayRunning);
        }
        if (details.containsKey("api_enabled")) {
            lastApiEnabled = details.getBoolean("api_enabled", lastApiEnabled);
        }
        lastGatewayPort = details.getInt("gateway_port", lastGatewayPort);
        lastV241PortDetails = details.getBoolean("port_details_v241", false);
        if (lastV241PortDetails) {
            lastV241PreferredPort = details.getInt(
                    "preferred_port_v241", lastV241PreferredPort);
            lastV241HttpPort = details.getInt("http_port_v241", lastV241HttpPort);
            lastV241HttpsPort = details.getInt("https_port_v241", lastV241HttpsPort);
        } else {
            // A downgrade/restart into any older host immediately returns to its original
            // single gateway_port presentation even if the companion process survived.
            lastV241PreferredPort = 0;
            lastV241HttpPort = 0;
            lastV241HttpsPort = 0;
        }
        lastConnectionInfo = details.getString(DETAIL_CONNECTION, lastConnectionInfo);
        lastRuntimeStatus = details.getString(DETAIL_RUNTIME, lastRuntimeStatus);
        lastApiKey = details.getString(DETAIL_API_KEY, lastApiKey);
        lastProtocol = details.getString(DETAIL_PROTOCOL, lastProtocol);
        lastHttpsEnabled = details.getBoolean("https_enabled", lastHttpsEnabled);
        lastAckElapsed = SystemClock.elapsedRealtime();
    }

    static void putStatus(android.os.Bundle result) {
        long now = SystemClock.elapsedRealtime();
        result.putBoolean("running", running);
        result.putBoolean("requested", running || startedElapsed > 0L);
        result.putBoolean("gateway_running", lastGatewayRunning);
        result.putLong("last_broadcast_age_ms", age(now, lastBroadcastElapsed));
        result.putLong("last_ack_age_ms", age(now, lastAckElapsed));
        result.putString("error", lastError == null ? "" : lastError);
    }

    @Override public void onCreate() {
        super.onCreate();
        try {
            createNotificationChannel();
            startForeground(NOTIFICATION_ID, buildNotification());
            acquireWakeLock();
            running = true;
            startedElapsed = SystemClock.elapsedRealtime();
            lastError = "";
            handler.removeCallbacks(heartbeat);
            handler.post(heartbeat);
            Log.i(TAG, "ka started");
            LocalApiFloatingOverlay.sync(this);
        } catch (Throwable t) {
            lastError = "前台保活初始化失败：" + safeMessage(t);
            Log.e(TAG, lastError, t);
            stopSelf();
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        boolean systemRestart = intent == null
                && (isRequested(this) || LocalApiFloatingOverlay.isRequested(this));
        if (!systemRestart && (intent == null || !ACTION_START.equals(intent.getAction())
                || !CONTROL_TOKEN.equals(intent.getStringExtra(EXTRA_CONTROL_TOKEN)))) {
            lastError = "拒绝了无效的保活启动请求";
            requestedPrefs(this).edit().putBoolean(KEY_REQUESTED, false).apply();
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        boolean overlayOnly = intent != null
                && intent.getBooleanExtra(EXTRA_OVERLAY_ONLY, false);
        if (!systemRestart && !overlayOnly) {
            requestedPrefs(this).edit().putBoolean(KEY_REQUESTED, true).apply();
        }
        if (!isRequested(this) && !LocalApiFloatingOverlay.isRequested(this)) return START_NOT_STICKY;
        handler.removeCallbacks(heartbeat);
        handler.post(heartbeat);
        return START_STICKY;
    }

    @Override public void onDestroy() {
        running = false;
        lastGatewayRunning = false;
        startedElapsed = 0L;
        handler.removeCallbacks(heartbeat);
        PublicTunnelManager.shutdown(this);
        PinggyTunnelManager.shutdown(this);
        releaseWakeLock();
        stopV236FreezerWaiter();
        stopV241FreezerWaiter();
        try { stopForeground(true); } catch (Throwable ignored) {}
        LocalApiFloatingOverlay.dismiss();
        Log.i(TAG, "ka stopped");
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    private void ensureFreezerBridgeChannelLocked() throws Exception {
        if (freezerBridgeChannel != null && freezerBridgeChannel.isOpen()
                && freezerBridgeFile != null) return;
        File file = new File(getFilesDir(), "dq0_code257_freezer.lock");
        freezerBridgeFile = new RandomAccessFile(file, "rw");
        freezerBridgeChannel = freezerBridgeFile.getChannel();
    }

    private void ensureFreezerBridgeChannelV236Locked() throws Exception {
        if (freezerBridgeChannelV236 != null && freezerBridgeChannelV236.isOpen()
                && freezerBridgeFileV236 != null) return;
        File file = new File(getFilesDir(), "dq0_code249_freezer.lock");
        freezerBridgeFileV236 = new RandomAccessFile(file, "rw");
        freezerBridgeChannelV236 = freezerBridgeFileV236.getChannel();
    }

    private void syncV236FreezerWaiter(Bundle details) {
        boolean ready = details != null
                && details.getBoolean(DETAIL_FREEZER_LOCK_V236, false);
        if (!ready) {
            stopV236FreezerWaiter();
            return;
        }
        synchronized (freezerBridgeLockV236) {
            if (freezerWaiterV236 != null && freezerWaiterV236.isAlive()) return;
            try {
                ensureFreezerBridgeChannelV236Locked();
                freezerWaitFileV236 = new RandomAccessFile(
                        new File(getFilesDir(), "dq0_code249_freezer.lock"), "rw");
                freezerWaitChannelV236 = freezerWaitFileV236.getChannel();
            } catch (Throwable error) {
                lastError = "2.3.6 文件锁保活初始化失败：" + safeMessage(error);
                return;
            }
            final FileChannel channel = freezerWaitChannelV236;
            freezerWaiterV236 = new Thread(new Runnable() {
                @Override public void run() {
                    FileLock acquired = null;
                    try {
                        Log.i(TAG, "code249 freezer lock waiter active");
                        acquired = channel.lock();
                        Log.w(TAG, "code249 freezer lock waiter unexpectedly acquired lock");
                    } catch (Throwable error) {
                        Log.w(TAG, "code249 freezer lock waiter ended: " + safeMessage(error));
                    } finally {
                        if (acquired != null) try { acquired.release(); } catch (Throwable ignored) {}
                    }
                }
            }, "DeePX-code249-freezer-waiter");
            freezerWaiterV236.setDaemon(true);
            freezerWaiterV236.start();
        }
    }

    private void stopV236FreezerWaiter() {
        synchronized (freezerBridgeLockV236) {
            Thread waiter = freezerWaiterV236;
            freezerWaiterV236 = null;
            FileChannel bridgeChannel = freezerBridgeChannelV236;
            RandomAccessFile bridgeFile = freezerBridgeFileV236;
            FileChannel waitChannel = freezerWaitChannelV236;
            RandomAccessFile waitFile = freezerWaitFileV236;
            freezerBridgeChannelV236 = null;
            freezerBridgeFileV236 = null;
            freezerWaitChannelV236 = null;
            freezerWaitFileV236 = null;
            if (waitChannel != null) try { waitChannel.close(); } catch (Throwable ignored) {}
            if (waitFile != null) try { waitFile.close(); } catch (Throwable ignored) {}
            if (bridgeChannel != null) try { bridgeChannel.close(); } catch (Throwable ignored) {}
            if (bridgeFile != null) try { bridgeFile.close(); } catch (Throwable ignored) {}
            if (waiter != null) waiter.interrupt();
        }
    }

    private static long installedHostCode(Context context) {
        if (context == null) return -1L;
        try {
            android.content.pm.PackageInfo info = context.getPackageManager()
                    .getPackageInfo(TARGET_PACKAGE, 0);
            return Build.VERSION.SDK_INT >= 28
                    ? info.getLongVersionCode() : info.versionCode;
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    /**
     * Waits on the lock owned by code257. AOSP exempts a cached lock owner from freezing while
     * it blocks a non-cached process; this service is non-cached because it is foreground.
     */
    private void syncV241FreezerWaiter(Bundle details) {
        boolean ready = details != null
                && details.getBoolean(DETAIL_FREEZER_LOCK_V241, false);
        if (!ready) {
            stopV241FreezerWaiter();
            return;
        }
        synchronized (freezerBridgeLock) {
            if (freezerWaiter != null && freezerWaiter.isAlive()) return;
            try {
                ensureFreezerBridgeChannelLocked();
            } catch (Throwable error) {
                lastError = "2.4.1 文件锁保活初始化失败：" + safeMessage(error);
                return;
            }
            final FileChannel channel;
            try {
                // This must be a separate open-file-description. Waiting on the descriptor
                // duplicated to code257 would share the same flock ownership and never block.
                freezerWaitFile = new RandomAccessFile(
                        new File(getFilesDir(), "dq0_code257_freezer.lock"), "rw");
                freezerWaitChannel = freezerWaitFile.getChannel();
                channel = freezerWaitChannel;
            } catch (Throwable error) {
                lastError = "2.4.1 文件锁等待端初始化失败：" + safeMessage(error);
                return;
            }
            freezerWaiter = new Thread(new Runnable() {
                @Override public void run() {
                    FileLock acquired = null;
                    try {
                        Log.i(TAG, "code257 freezer lock waiter active");
                        acquired = channel.lock();
                        Log.w(TAG, "code257 freezer lock waiter unexpectedly acquired lock");
                    } catch (Throwable error) {
                        Log.w(TAG, "code257 freezer lock waiter ended: " + safeMessage(error));
                    } finally {
                        if (acquired != null) try { acquired.release(); } catch (Throwable ignored) {}
                        synchronized (freezerBridgeLock) {
                            if (freezerWaiter == Thread.currentThread()) {
                                freezerWaiter = null;
                                if (freezerWaitChannel != null) {
                                    try { freezerWaitChannel.close(); } catch (Throwable ignored) {}
                                }
                                if (freezerWaitFile != null) {
                                    try { freezerWaitFile.close(); } catch (Throwable ignored) {}
                                }
                                freezerWaitChannel = null;
                                freezerWaitFile = null;
                            }
                        }
                    }
                }
            }, "DeePX-code257-freezer-waiter");
            freezerWaiter.setDaemon(true);
            freezerWaiter.start();
        }
    }

    private void stopV241FreezerWaiter() {
        synchronized (freezerBridgeLock) {
            Thread waiter = freezerWaiter;
            if (waiter != null) Log.i(TAG, "code257 freezer lock waiter stopping");
            freezerWaiter = null;
            FileChannel channel = freezerBridgeChannel;
            RandomAccessFile file = freezerBridgeFile;
            FileChannel waitChannel = freezerWaitChannel;
            RandomAccessFile waitFile = freezerWaitFile;
            freezerBridgeChannel = null;
            freezerBridgeFile = null;
            freezerWaitChannel = null;
            freezerWaitFile = null;
            if (waitChannel != null) try { waitChannel.close(); } catch (Throwable ignored) {}
            if (waitFile != null) try { waitFile.close(); } catch (Throwable ignored) {}
            if (channel != null) try { channel.close(); } catch (Throwable ignored) {}
            if (file != null) try { file.close(); } catch (Throwable ignored) {}
            if (waiter != null) waiter.interrupt();
        }
    }


    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                UiLanguage.text(this, "DeepSeek 本地 API", "DeepSeek Local API"),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(UiLanguage.text(this,
                "保持本地 API 与 SSE 流在后台可用",
                "Keeps the local API and SSE streams available in the background"));
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(TARGET_PACKAGE);
        PendingIntent pending = null;
        if (launch != null) {
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            pending = PendingIntent.getActivity(this, 0, launch, flags);
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(NotificationIcons.smallIcon(this))
                .setContentTitle(UiLanguage.text(this,
                        "DeepSeek 本地 API 正在运行", "DeepSeek Local API is running"))
                .setContentText(UiLanguage.text(this,
                        PublicTunnelManager.isConnected() || PinggyTunnelManager.isConnected()
                                ? "本地、局域网与公网入口均在运行"
                                : "正在保持后台监听与流式响应稳定",
                        PublicTunnelManager.isConnected() || PinggyTunnelManager.isConnected()
                                ? "Local, LAN, and public endpoints are active"
                                : "Keeping background listening and streaming responses stable"))
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false);
        if (pending != null) builder.setContentIntent(pending);
        return builder.build();
    }

    private void acquireWakeLock() {
        try {
            PowerManager power = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (power == null) return;
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "DeePX:ka");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire();
        } catch (Throwable t) {
            lastError = "CPU 保活不可用：" + safeMessage(t);
            Log.w(TAG, lastError, t);
        }
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Throwable ignored) {}
        wakeLock = null;
    }

    private static SharedPreferences requestedPrefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean isRequested(Context context) {
        return requestedPrefs(context).getBoolean(KEY_REQUESTED, false);
    }

    private static long age(long now, long value) {
        return value <= 0L ? -1L : Math.max(0L, now - value);
    }

    static boolean apiEnabled() { return lastApiEnabled; }
    static boolean gatewayRunning() { return lastGatewayRunning; }
    static boolean httpsEnabled() { return lastHttpsEnabled; }
    static int gatewayPort() { return lastGatewayPort; }
    static boolean hasV241PortDetails() { return lastV241PortDetails; }
    static int v241PreferredPort() { return lastV241PreferredPort; }
    static int v241HttpPort() { return lastV241HttpPort; }
    static int v241HttpsPort() { return lastV241HttpsPort; }
    static String connectionInfo() { return lastConnectionInfo; }
    static String runtimeStatus() { return lastRuntimeStatus; }
    static String apiKeyValue() { return lastApiKey; }
    static String protocolValue() { return lastProtocol; }
    static long heartbeatAgeMs() {
        return age(SystemClock.elapsedRealtime(), lastAckElapsed);
    }
    static String lastErrorValue() { return lastError == null ? "" : lastError; }
    private static String safeMessage(Throwable t) {
        if (t == null) return "unknown";

        String message = t.getMessage();
        return message == null || message.length() == 0
                ? t.getClass().getSimpleName() : message;
    }
}
