package com.smallhairball.deepx;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import androidx.core.graphics.PathParser;

/**
 * Theme-aware Material glyphs for the UI injected into DeepSeek.
 *
 * <p>Injected code cannot assume that the host may open this module's resource package. Keeping
 * the official 24 dp Material paths in a tiny View makes the icons deterministic in both the
 * mainland and Play builds, while still following the host's light/dark text colour.</p>
 */
final class HubMaterialGlyphView extends View {
    private static final float VIEWPORT = 24f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int paintColor;
    private final Path source = new Path();
    private final Path rendered = new Path();
    private final Matrix transform = new Matrix();
    private boolean outlineOnly;

    HubMaterialGlyphView(Context context, String name, int color) {
        super(context);
        setGlyph(name, color);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setGlyph(String name, int color) {
        paintColor = color;
        paint.setColor(color);
        outlineOnly = "ds_project_community".equals(name);
        paint.setStyle(outlineOnly ? Paint.Style.STROKE : Paint.Style.FILL);
        if (outlineOnly) {
            paint.setStrokeWidth(4.4f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }
        Path parsed = PathParser.createPathFromPathData(pathFor(name));
        source.reset();
        if (parsed != null) source.set(parsed);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        if (size <= 0f || source.isEmpty()) return;
        // The community glyph uses a deliberately heavier outline. Give that stroke an inset
        // viewport so its rounded feet and side silhouette are not clipped by the 24 dp view.
        float drawingSize = outlineOnly ? size * 0.82f : size;
        float scale = drawingSize / VIEWPORT;
        float left = (getWidth() - drawingSize) * 0.5f;
        float top = (getHeight() - drawingSize) * 0.5f;
        transform.reset();
        transform.setScale(scale, scale);
        transform.postTranslate(left, top);
        rendered.reset();
        source.transform(transform, rendered);
        canvas.drawPath(rendered, paint);
    }

    private static String pathFor(String name) {
        if ("ds_category_chat".equals(name)) {
            return "M20,2H4C2.9,2 2,2.9 2,4v18l4,-4h14c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM6,9h12v2H6V9zM6,5h12v2H6V5zM6,13h8v2H6v-2z";
        }
        if ("ds_category_account".equals(name)) {
            return "M12,1 3,5v6c0,5.55 3.84,10.74 9,12 5.16,-1.26 9,-6.45 9,-12V5l-9,-4zM12,5c1.66,0 3,1.34 3,3s-1.34,3 -3,3 -3,-1.34 -3,-3 1.34,-3 3,-3zM12,19.3c-2.5,-0.8 -4.58,-2.67 -5.69,-5.07C7.62,13.45 9.58,13 12,13s4.38,0.45 5.69,1.23C16.58,16.63 14.5,18.5 12,19.3z";
        }
        if ("ds_category_appearance".equals(name)) {
            return "M12,3C7.03,3 3,6.58 3,11c0,3.87 3.13,7 7,7h1.65c0.82,0 1.35,-0.88 0.96,-1.6 -0.29,-0.54 0.1,-1.2 0.71,-1.2H15c3.31,0 6,-2.69 6,-6 0,-3.42 -4.03,-6.2 -9,-6.2zM7.5,12C6.67,12 6,11.33 6,10.5S6.67,9 7.5,9 9,9.67 9,10.5 8.33,12 7.5,12zM10,7.5C9.17,7.5 8.5,6.83 8.5,6S9.17,4.5 10,4.5s1.5,0.67 1.5,1.5S10.83,7.5 10,7.5zM14,7.5c-0.83,0 -1.5,-0.67 -1.5,-1.5S13.17,4.5 14,4.5s1.5,0.67 1.5,1.5S14.83,7.5 14,7.5zM17,11c-0.83,0 -1.5,-0.67 -1.5,-1.5S16.17,8 17,8s1.5,0.67 1.5,1.5S17.83,11 17,11z";
        }
        if ("ds_category_debug".equals(name)) {
            return "M20,8h-2.81c-0.45,-0.78 -1.07,-1.45 -1.82,-1.96L17,4.41 15.59,3l-2.17,2.17C12.96,5.06 12.49,5 12,5s-0.96,0.06 -1.42,0.17L8.41,3 7,4.41l1.62,1.63C7.88,6.55 7.26,7.22 6.81,8H4v2h2.09C6.03,10.33 6,10.66 6,11v1H4v2h2v1c0,0.34 0.03,0.67 0.09,1H4v2h2.81c1.04,1.79 2.97,3 5.19,3s4.15,-1.21 5.19,-3H20v-2h-2.09c0.06,-0.33 0.09,-0.66 0.09,-1v-1h2v-2h-2v-1c0,-0.34 -0.03,-0.67 -0.09,-1H20V8zM14,16h-4v-2h4v2zM14,12h-4v-2h4v2z";
        }
        if ("ds_category_engineering".equals(name)) {
            return "M9.4,16.6 4.8,12l4.6,-4.6L8,6l-6,6 6,6 1.4,-1.4zM14.6,16.6 19.2,12l-4.6,-4.6L16,6l6,6 -6,6 -1.4,-1.4z";
        }
        if ("ds_category_cloud".equals(name)) {
            return "M19.35,10.04A7.49,7.49 0,0 0,5.5,8 6,6 0,0 0,6,20h13a5,5 0,0 0,0.35,-9.96zM19,18H6a4,4 0,0 1,-0.33,-7.99l1.07,-0.09 0.5,-0.95A5.49,5.49 0,0 1,17.4,10.5l0.15,1.1 1.1,0.08A3,3 0,0 1,19,18z";
        }
        if ("ds_category_help".equals(name)) {
            return "M11,18h2v-2h-2v2zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8zM12,6c-2.21,0 -4,1.79 -4,4h2c0,-1.1 0.9,-2 2,-2s2,0.9 2,2c0,2 -3,1.75 -3,5h2c0,-2.25 3,-2.5 3,-5 0,-2.21 -1.79,-4 -4,-4z";
        }
        if ("ds_project_community".equals(name)) {
            return "M12,2 C7.1,2 4,5.6 4,10.1 L4,12.4 C3.2,14.1 2.5,16.1 2.6,18.1 C2.7,19.8 3.6,20.6 4.7,20.2 L6.8,18.5 C7.3,20.5 8.1,21.8 9.6,22 L8.2,23 C7.7,23.4 8.1,24 8.8,24 L11.4,24 C11.7,24 11.9,23.8 12,23.6 C12.1,23.8 12.3,24 12.6,24 L15.2,24 C15.9,24 16.3,23.4 15.8,23 L14.4,22 C15.9,21.8 16.7,20.5 17.2,18.5 L19.3,20.2 C20.4,20.6 21.3,19.8 21.4,18.1 C21.5,16.1 20.8,14.1 20,12.4 L20,10.1 C20,5.6 16.9,2 12,2 Z";
        }
        if ("ds_project_telegram".equals(name)) {
            // Simple Icons v16.12.0 Telegram, 24x24 path (CC0-1.0 project license).
            return "M11.944,0A12,12 0,0 0,0 12a12,12 0,0 0,12 12 12,12 0,0 0,12 -12A12,12 0,0 0,12 0a12,12 0,0 0,-0.056 0zm4.962,7.224c0.1,-0.002 0.321,0.023 0.465,0.14a0.506,0.506 0,0 1,0.171 0.325c0.016,0.093 0.036,0.306 0.02,0.472 -0.18,1.898 -0.962,6.502 -1.36,8.627 -0.168,0.9 -0.499,1.201 -0.82,1.23 -0.696,0.065 -1.225,-0.46 -1.9,-0.902 -1.056,-0.693 -1.653,-1.124 -2.678,-1.8 -1.185,-0.78 -0.417,-1.21 0.258,-1.91 0.177,-0.184 3.247,-2.977 3.307,-3.23 0.007,-0.032 0.014,-0.15 -0.056,-0.212s-0.174,-0.041 -0.249,-0.024c-0.106,0.024 -1.793,1.14 -5.061,3.345 -0.48,0.33 -0.913,0.49 -1.302,0.48 -0.428,-0.008 -1.252,-0.241 -1.865,-0.44 -0.752,-0.245 -1.349,-0.374 -1.297,-0.789 0.027,-0.216 0.325,-0.437 0.893,-0.663 3.498,-1.524 5.83,-2.529 6.998,-3.014 3.332,-1.386 4.025,-1.627 4.476,-1.635z";
        }
        if ("ds_action_execute".equals(name)) {
            return "M8,5.18v13.64c0,0.78 0.86,1.25 1.52,0.83l10.72,-6.82c0.61,-0.39 0.61,-1.27 0,-1.66L9.52,4.35C8.86,3.93 8,4.4 8,5.18z";
        }
        if ("ds_action_save".equals(name)) {
            return "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z";
        }
        if ("ds_action_back".equals(name)) {
            return "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z";
        }
        if ("ds_action_close".equals(name)) {
            return "M18.3,5.71 12,12l6.3,6.29 -1.41,1.42L10.59,13.41 4.29,19.71 2.88,18.29 9.17,12 2.88,5.71 4.29,4.29 10.59,10.59 16.89,4.29z";
        }
        if ("ds_action_settings".equals(name)) {
            return "M19.43,12.98c0.04,-0.32 0.07,-0.65 0.07,-0.98s-0.02,-0.66 -0.07,-0.98l2.11,-1.65c0.19,-0.15 0.24,-0.42 0.12,-0.64l-2,-3.46c-0.12,-0.22 -0.37,-0.31 -0.6,-0.22l-2.49,1c-0.52,-0.4 -1.08,-0.73 -1.69,-0.98L14.5,2.42C14.47,2.18 14.25,2 14,2h-4c-0.25,0 -0.46,0.18 -0.5,0.42L9.12,5.07c-0.61,0.25 -1.18,0.59 -1.69,0.98l-2.49,-1c-0.23,-0.08 -0.48,0 -0.6,0.22l-2,3.46c-0.13,0.22 -0.07,0.49 0.12,0.64l2.11,1.65c-0.04,0.32 -0.08,0.66 -0.08,0.98s0.03,0.66 0.08,0.98l-2.11,1.65c-0.19,0.15 -0.24,0.42 -0.12,0.64l2,3.46c0.12,0.22 0.37,0.31 0.6,0.22l2.49,-1c0.52,0.4 1.08,0.73 1.69,0.98l0.38,2.65c0.04,0.24 0.25,0.42 0.5,0.42h4c0.25,0 0.46,-0.18 0.5,-0.42l0.38,-2.65c0.61,-0.25 1.18,-0.58 1.69,-0.98l2.49,1c0.23,0.08 0.48,0 0.6,-0.22l2,-3.46c0.12,-0.22 0.07,-0.49 -0.12,-0.64l-2.11,-1.65zM12,15.5A3.5,3.5 0,1 1,12,8a3.5,3.5 0,0 1,0,7.5z";
        }
        if ("ds_tab_explore".equals(name)) {
            return "M12,10.9c-0.61,0 -1.1,0.49 -1.1,1.1s0.49,1.1 1.1,1.1c0.61,0 1.1,-0.49 1.1,-1.1s-0.49,-1.1 -1.1,-1.1zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM14.19,14.19L6,18l3.81,-8.19L18,6l-3.81,8.19z";
        }
        if ("ds_tab_forum".equals(name)) {
            return "M21,6h-2v9H6v2c0,0.55 0.45,1 1,1h11l4,4V7c0,-0.55 -0.45,-1 -1,-1zM17,12V3c0,-0.55 -0.45,-1 -1,-1H3c-0.55,0 -1,0.45 -1,1v14l4,-4h10c0.55,0 1,-0.45 1,-1z";
        }
        if ("ds_tab_profile".equals(name)) {
            return "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,5c1.66,0 3,1.34 3,3 0,1.66 -1.34,3 -3,3s-3,-1.34 -3,-3c0,-1.66 1.34,-3 3,-3zM12,19.2c-2.5,0 -4.71,-1.28 -6,-3.22 0.03,-1.99 4,-3.08 6,-3.08 1.99,0 5.97,1.09 6,3.08 -1.29,1.94 -3.5,3.22 -6,3.22z";
        }
        if ("ds_action_add".equals(name)) {
            return "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z";
        }
        if ("ds_action_notifications".equals(name)) {
            return "M12,22a2.42,2.42 0,0 0,2.4,-2.4h-4.8A2.42,2.42 0,0 0,12,22zM18,16v-5a6,6 0,0 0,-5,-5.91V4a1,1 0,0 0,-2,0v1.09A6,6 0,0 0,6,11v5l-2,2v1h16v-1z";
        }
        if ("ds_action_thumb".equals(name)) {
            return "M1,21h4V9H1v12zM23,10c0,-1.1 -0.9,-2 -2,-2h-6.31l0.95,-4.57 0.03,-0.32c0,-0.41 -0.17,-0.79 -0.44,-1.06L14.17,1 7.59,7.59C7.22,7.95 7,8.45 7,9v10c0,1.1 0.9,2 2,2h9c0.83,0 1.54,-0.5 1.84,-1.22l3.02,-7.05c0.09,-0.23 0.14,-0.47 0.14,-0.73v-2z";
        }
        if ("ds_action_thumb_outline".equals(name)) {
            return "M9,21h9c0.83,0 1.54,-0.5 1.84,-1.22l3.02,-7.05c0.09,-0.23 0.14,-0.47 0.14,-0.73v-2c0,-1.1 -0.9,-2 -2,-2h-6.31l0.95,-4.57 0.03,-0.32c0,-0.41 -0.17,-0.79 -0.44,-1.06L14.17,1 7.59,7.59C7.22,7.95 7,8.45 7,9v10c0,1.1 0.9,2 2,2zM9,9l4.34,-4.34L12,10h9v2l-3,7H9V9zM1,9h4v12H1z";
        }
        if ("ds_action_star".equals(name)) {
            return "M12,17.27 18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21zM12,15.4l-3.76,2.27 1,-4.28 -3.32,-2.88 4.38,-0.38L12,6.1l1.71,4.04 4.38,0.38 -3.32,2.88 1,4.28z";
        }
        if ("ds_action_star_filled".equals(name)) {
            return "M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21z";
        }
        if ("ds_action_comment".equals(name)) {
            return "M4,4h16c1.1,0 2,0.9 2,2v10c0,1.1 -0.9,2 -2,2H8l-6,4V6c0,-1.1 0.9,-2 2,-2zM6,8v2h12V8H6zm0,4v2h8v-2H6z";
        }
        if ("ds_action_copy".equals(name)) {
            return "M16,1H4c-1.1,0 -2,0.9 -2,2v14h2V3h12V1zm3,4H8c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2zm0,16H8V7h11v14z";
        }
        return "";
    }
}