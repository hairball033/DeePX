package com.smallhairball.deepx;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;

import java.util.Map;
import java.util.WeakHashMap;

/** Conservative gesture-navigation fix for OEM builds that leave an opaque black footer. */
final class SystemBarCompat {
    private static final Map<Activity, Boolean> APPLIED = new WeakHashMap<Activity, Boolean>();

    private SystemBarCompat() {}

    static void apply(final Activity activity) {
        if (activity == null || Build.VERSION.SDK_INT < 29 || activity.isFinishing()) return;
        final Window window = activity.getWindow();
        final View decor = window == null ? null : window.getDecorView();
        if (decor == null) return;
        decor.post(new Runnable() {
            @Override public void run() {
                synchronized (APPLIED) {
                    if (Boolean.TRUE.equals(APPLIED.get(activity))) return;
                }
                try {
                    WindowInsets insets = decor.getRootWindowInsets();
                    if (insets == null) return;
                    int bottom = insets.getSystemWindowInsetBottom();
                    int gestureMaximum = Math.round(40f
                            * activity.getResources().getDisplayMetrics().density);
                    // Three-button navigation is normally taller. Never change its deliberate
                    // opaque navigation surface or icon contrast.
                    if (bottom <= 0 || bottom > gestureMaximum) return;
                    int color = window.getNavigationBarColor();
                    if (Color.alpha(color) < 240
                            || Color.red(color) > 18 || Color.green(color) > 18
                            || Color.blue(color) > 18) return;
                    window.setNavigationBarContrastEnforced(false);
                    window.setNavigationBarDividerColor(Color.TRANSPARENT);
                    window.setNavigationBarColor(Color.TRANSPARENT);
                    int flags = decor.getSystemUiVisibility()
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
                    decor.setSystemUiVisibility(flags);
                    synchronized (APPLIED) { APPLIED.put(activity, Boolean.TRUE); }
                    Main.log("gesture navigation black-bar compatibility applied bottom="
                            + bottom);
                } catch (Throwable error) {
                    Main.log("gesture navigation compatibility skipped: "
                            + error.getClass().getSimpleName());
                }
            }
        });
    }

    static void forget(Activity activity) {
        synchronized (APPLIED) { APPLIED.remove(activity); }
    }
}
