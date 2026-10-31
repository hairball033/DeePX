package com.smallhairball.deepx;

import android.app.Activity;

/** Compatibility facade for the native two-pane DeepSeek workspace. */
final class DualChatUi {
    private DualChatUi() {}

    static void show(Activity activity) {
        NativeDualChatBridge.show(activity);
    }

    static void forget(Activity activity) {
        NativeDualChatBridge.forget(activity);
    }
}
