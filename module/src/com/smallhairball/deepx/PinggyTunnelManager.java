package com.smallhairball.deepx;

import android.content.Context;
import android.os.Bundle;

final class PinggyTunnelManager {
    private PinggyTunnelManager() {}
    static Bundle status(Context context) {
        Bundle b = new Bundle();
        b.putBoolean("available", false);
        b.putBoolean("running", false);
        return b;
    }
    static Bundle setRequested(Context context, boolean requested) {
        Bundle b = new Bundle();
        b.putBoolean("accepted", false);
        return b;
    }
    static void onGatewayState(Context context, boolean localApiEnabled, boolean gatewayRunning, int port) {}
    static void shutdown(Context context) {}
    static boolean isConnected() { return false; }
}
