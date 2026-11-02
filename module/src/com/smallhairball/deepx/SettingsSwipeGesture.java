package com.smallhairball.deepx;

import android.app.Activity;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.util.Map;
import java.util.WeakHashMap;

/** Right-side, right-to-left gesture detector that never consumes host touch events. */
final class SettingsSwipeGesture {
    private static final Map<Activity, State> STATES = new WeakHashMap<Activity, State>();
    private static long lastOpenedAt;

    private SettingsSwipeGesture() {}

    static boolean observe(Activity activity, MotionEvent event) {
        if (activity == null || event == null
                || (!Main.isSwipeSettingsEnabled() && !Main.isDualChatEnabled())) return false;
        View decor = activity.getWindow() == null
                ? null : activity.getWindow().getDecorView();
        if (decor == null || decor.getWidth() <= 0) return false;
        float density = activity.getResources().getDisplayMetrics().density;
        int action = event.getActionMasked();
        synchronized (STATES) {
            if (action == MotionEvent.ACTION_DOWN) {
                // Keep the start zone comfortably inside gesture-navigation's own edge while
                // accepting a shorter intentional pull.  The previous 96/96dp pair was too
                // demanding on narrow phones and frequently lost the gesture to a small wobble.
                float edgeBand = 124f * density;
                float verticalGuard = 32f * density;
                boolean eligible = event.getX() >= decor.getWidth() - edgeBand
                        && event.getY() >= verticalGuard
                        && event.getY() <= decor.getHeight() - verticalGuard;
                STATES.put(activity, new State(event.getX(), event.getY(),
                        event.getEventTime(), eligible));
                return false;
            }
            State state = STATES.get(activity);
            if (state == null || !state.eligible) return false;
            if (event.getPointerCount() != 1) {
                state.eligible = false;
                return false;
            }
            if (action == MotionEvent.ACTION_CANCEL) {
                STATES.remove(activity);
                return false;
            }
            float travelledX = Math.abs(event.getX() - state.x);
            float travelledY = Math.abs(event.getY() - state.y);
            if (action == MotionEvent.ACTION_MOVE) {
                // A gallery/grid or conversation list owns vertical drags.  Once the pointer has
                // crossed touch slop vertically before establishing a clear horizontal edge
                // gesture, permanently reject this stream.  Merely checking the final ACTION_UP
                // delta made a long list fling look like a dual-mode swipe after finger wobble.
                float slop = 12f * density;
                if (travelledY > slop && travelledY > travelledX * 0.72f) {
                    state.eligible = false;
                }
                return false;
            }
            if (action != MotionEvent.ACTION_UP) return false;
            STATES.remove(activity);
            float dx = state.x - event.getX();
            float dy = Math.abs(event.getY() - state.y);
            long duration = event.getEventTime() - state.at;
            long now = SystemClock.elapsedRealtime();
            boolean accepted = dx >= 68f * density
                    && dy <= 48f * density
                    && dx >= dy * 1.8f
                    && duration >= 45L && duration <= 1_050L
                    && now - lastOpenedAt >= 700L;
            if (accepted) lastOpenedAt = now;
            return accepted;
        }
    }

    static void forget(Activity activity) {
        synchronized (STATES) { STATES.remove(activity); }
    }

    private static final class State {
        final float x;
        final float y;
        final long at;
        boolean eligible;

        State(float x, float y, long at, boolean eligible) {
            this.x = x;
            this.y = y;
            this.at = at;
            this.eligible = eligible;
        }
    }
}
