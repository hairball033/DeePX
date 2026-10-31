package com.smallhairball.deepx;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Shared non-Hook account-safety flow. No public-IP or country prerequisite is performed. */
final class AccountSafetyPrank {
    private static final long NOTICE_DELAY_MS = 4_000L;
    private static final long STAGE_INTERVAL_MS = 3_000L;
    // This newer in-module clip is a code257-only presentation change.  The existing code249
    // flow continues to begin playback immediately, exactly as before.
    private static final long V241_VIDEO_START_DELAY_MS = 1_500L;
    private static final long REVEAL_DELAY_MS = 5_000L;
    // Both maintained host branches use the single retained high-quality clip. Keeping a second
    // low-resolution payload inflated every APK and could leave legacy builds pointing at an
    // asset intentionally excluded by current packaging.
    private static final String LEGACY_VIDEO_ASSET = "assets/rickroll_241.mp4";
    private static final String V241_VIDEO_ASSET = "assets/rickroll_241.mp4";
    private static boolean processing;

    private AccountSafetyPrank() {}

    static synchronized void launch(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        if (processing) {
            Toast.makeText(activity, "正在处理，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        processing = true;
        Toast.makeText(activity, "正在处理", Toast.LENGTH_SHORT).show();
        showProcessing(activity);
    }

    private static void showProcessing(final Activity activity) {
        final boolean dark = DeePXUi.isDark(activity);
        final int surface = dark ? 0xFF252527 : Color.WHITE;
        final int ink = dark ? 0xFFF0F0F2 : 0xFF171719;
        final int muted = dark ? 0xFFAAAAB1 : 0xFF5E5E63;
        final int track = dark ? 0xFF35353A : 0xFFF2F2F5;
        final Handler handler = new Handler(Looper.getMainLooper());
        final String[] stages = {
                "正在连接服务器…",
                "正在拉取数据…",
                "正在处理…"
        };
        final long processDuration = stages.length * STAGE_INTERVAL_MS;

        // Prefer the Miuix processing dialog; it auto-closes and hands back via onFinished.
        if (DeePXComposeBridge.showProgress(activity, "账号安全处理", stages, STAGE_INTERVAL_MS,
                new Runnable() {
                    @Override public void run() {
                        clearProcessing();
                        if (!activity.isFinishing()
                                && (android.os.Build.VERSION.SDK_INT < 17
                                || !activity.isDestroyed())) {
                            showFreezeNotice(activity);
                        }
                    }
                })) {
            return;
        }

        LinearLayout card = column(activity, surface, dp(activity, 14));
        card.setPadding(dp(activity, 29), dp(activity, 26), dp(activity, 29),
                dp(activity, 24));
        TextView title = text(activity, "账号安全处理", 18, ink, Typeface.DEFAULT_BOLD);
        card.addView(title);
        final TextView status = text(activity, stages[0], 14, muted, Typeface.DEFAULT);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(activity, 14);
        card.addView(status, statusParams);
        final ProgressBar progress = new ProgressBar(activity, null,
                android.R.attr.progressBarStyleHorizontal);
        // This is activity feedback, not a measurable download. Keep one short line sweeping
        // continuously instead of presenting a determinate percentage/progress fill.
        progress.setIndeterminate(true);
        progress.setIndeterminateTintList(
                android.content.res.ColorStateList.valueOf(0xFF4D6BFE));
        progress.setProgressBackgroundTintList(
                android.content.res.ColorStateList.valueOf(track));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 4));
        progressParams.topMargin = dp(activity, 16);
        card.addView(progress, progressParams);

        final Dialog dialog = new Dialog(activity);
        dialog.setContentView(card);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                    Color.TRANSPARENT));
            // Roughly one fifth more visual area than the previous compact processing popup,
            // while retaining a small screen-edge inset on phones.
            window.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * .94f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        final long startedAt = android.os.SystemClock.uptimeMillis();
        final Runnable ticker = new Runnable() {
            @Override public void run() {
                if (activity.isFinishing()
                        || (android.os.Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) {
                    try { dialog.dismiss(); } catch (Throwable ignored) {}
                    clearProcessing();
                    return;
                }
                long elapsed = Math.min(processDuration,
                        android.os.SystemClock.uptimeMillis() - startedAt);
                int stage = Math.min(stages.length - 1,
                        (int) (elapsed / STAGE_INTERVAL_MS));
                status.setText(stages[stage]);
                if (elapsed < processDuration) {
                    handler.postDelayed(this, 250L);
                    return;
                }
                try { dialog.dismiss(); } catch (Throwable ignored) {}
                handler.postDelayed(() -> {
                    clearProcessing();
                    if (!activity.isFinishing()
                            && (android.os.Build.VERSION.SDK_INT < 17
                            || !activity.isDestroyed())) {
                        showFreezeNotice(activity);
                    }
                }, NOTICE_DELAY_MS);
            }
        };
        handler.post(ticker);
    }

    private static synchronized void clearProcessing() {
        processing = false;
    }

    private static void showFreezeNotice(final Activity activity) {
        final boolean dark = (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        final int surface = dark ? 0xFF252527 : Color.WHITE;
        final int ink = dark ? 0xFFF0F0F2 : 0xFF171719;
        final int muted = dark ? 0xFFAAAAB1 : 0xFF5E5E63;
        final int divider = dark ? 0xFF3B3B40 : 0xFFE3E3E7;

        LinearLayout card = column(activity, surface, dp(activity, 12));
        // Keep the compact host-dialog rhythm, but leave a modest lower inset so the action does
        // not look vertically crushed. This is intentionally still much smaller than the old
        // oversized lower half.
        card.setPadding(dp(activity, 24), dp(activity, 23), dp(activity, 24), dp(activity, 8));
        TextView title = text(activity, "账号冻结提醒", 18, ink, Typeface.DEFAULT_BOLD);
        card.addView(title);
        TextView body = text(activity, randomFreezeMessage(), 14, muted, Typeface.DEFAULT);
        body.setLineSpacing(dp(activity, 3), 1f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = dp(activity, 13);
        card.addView(body, bodyParams);
        View line = new View(activity);
        line.setBackgroundColor(divider);
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 1));
        lineParams.topMargin = dp(activity, 13);
        card.addView(line, lineParams);
        TextView action = text(activity, "去处理", 15, ink, Typeface.DEFAULT);
        action.setGravity(Gravity.CENTER);
        action.setClickable(true);
        card.addView(action, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 44)));

        final Dialog dialog = new Dialog(activity);
        dialog.setContentView(card);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                    Color.TRANSPARENT));
            window.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * .84f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        action.setOnClickListener(view -> {
            dialog.dismiss();
            showInternalPage(activity, dark);
        });
    }

    private static void showInternalPage(Activity activity, boolean dark) {
        if (activity == null || activity.isFinishing()) return;
        // code257 is deliberately isolated: a white page with one centered clip.  The older
        // code249 black fullscreen implementation below is retained as its own path.
        if (HostCompat.isV241()) {
            showV241InternalPage(activity);
            return;
        }
        final Dialog dialog = new Dialog(activity,
                android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        LinearLayout bar = new LinearLayout(activity);
        bar.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        bar.setPadding(dp(activity, 12), dp(activity, 24), dp(activity, 12), dp(activity, 8));
        TextView close = text(activity, "‹", 32, Color.WHITE, Typeface.DEFAULT);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("返回");
        bar.addView(close, new LinearLayout.LayoutParams(dp(activity, 42), dp(activity, 42)));
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 74)));
        final VideoView video = new VideoView(activity);
        video.setBackgroundColor(Color.BLACK);
        root.addView(video, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        close.setOnClickListener(view -> {
            try { video.stopPlayback(); } catch (Throwable ignored) {}
            dialog.dismiss();
        });
        dialog.setContentView(root);
        dialog.show();
        final Runnable startVideo = new Runnable() {
            @Override public void run() {
                if (!dialog.isShowing() || activity.isFinishing()
                        || (android.os.Build.VERSION.SDK_INT >= 17
                        && activity.isDestroyed())) return;
                File file = extractVideo(activity);
                if (file == null || !file.isFile()) return;
                video.setVideoPath(file.getAbsolutePath());
                video.setOnPreparedListener(mediaPlayer -> {
                    mediaPlayer.setLooping(true);
                    video.start();
                });
            }
        };
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(startVideo, HostCompat.isV241()
                ? V241_VIDEO_START_DELAY_MS : 0L);
        handler.postDelayed(() -> {
            if (!dialog.isShowing() || activity.isFinishing()
                    || (android.os.Build.VERSION.SDK_INT >= 17
                    && activity.isDestroyed())) return;
            new android.app.AlertDialog.Builder(activity)
                    .setTitle("提示")
                    .setMessage("你被骗了")
                    .setPositiveButton("确定", null)
                    .show();
        }, REVEAL_DELAY_MS);
    }

    /** Exact code257-only playback surface. The older host path above remains unchanged. */
    private static void showV241InternalPage(final Activity activity) {
        final Dialog dialog = new Dialog(activity,
                android.R.style.Theme_Light_NoTitleBar_Fullscreen);
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(Color.WHITE);

        // SurfaceView owns a separate compositor layer. On several OEM builds that layer stayed
        // behind the fullscreen dialog's white window even after MediaPlayer reported prepared.
        // TextureView is composited inside this exact code257 page, so the centered video and the
        // back control have deterministic z-order.
        final android.view.TextureView texture = new android.view.TextureView(activity);
        texture.setOpaque(true);
        texture.setKeepScreenOn(true);
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int videoWidth = Math.max(dp(activity, 240), Math.round(screenWidth * .92f));
        int videoHeight = Math.round(videoWidth * 9f / 16f);
        root.addView(texture,
                new FrameLayout.LayoutParams(videoWidth, videoHeight, Gravity.CENTER));

        FrameLayout back = new FrameLayout(activity);
        back.setClickable(true);
        back.setFocusable(true);
        back.setContentDescription("返回");
        View backGlyph = new HubMaterialGlyphView(activity, "ds_action_back", Color.BLACK);
        back.addView(backGlyph, new FrameLayout.LayoutParams(
                dp(activity, 22), dp(activity, 22), Gravity.CENTER));
        FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(
                dp(activity, 48), dp(activity, 48), Gravity.TOP | Gravity.START);
        backParams.leftMargin = dp(activity, 8);
        backParams.topMargin = dp(activity, 24);
        root.addView(back, backParams);
        if (android.os.Build.VERSION.SDK_INT >= 21) back.setElevation(dp(activity, 8));
        back.setOnClickListener(view -> dialog.dismiss());

        final android.media.MediaPlayer[] player = new android.media.MediaPlayer[1];
        final android.view.Surface[] playbackSurface = new android.view.Surface[1];
        final File[] videoFile = new File[1];
        final boolean[] textureReady = new boolean[]{false};
        final boolean[] preparing = new boolean[]{false};
        final Runnable[] prepare = new Runnable[1];
        prepare[0] = () -> {
            if (!dialog.isShowing() || !textureReady[0] || preparing[0]
                    || videoFile[0] == null) return;
            preparing[0] = true;
            try {
                android.media.MediaPlayer mediaPlayer = new android.media.MediaPlayer();
                player[0] = mediaPlayer;
                mediaPlayer.setDataSource(videoFile[0].getAbsolutePath());
                android.graphics.SurfaceTexture surfaceTexture = texture.getSurfaceTexture();
                if (surfaceTexture == null) throw new IllegalStateException(
                        "TextureView surface is unavailable");
                playbackSurface[0] = new android.view.Surface(surfaceTexture);
                mediaPlayer.setSurface(playbackSurface[0]);
                mediaPlayer.setScreenOnWhilePlaying(true);
                mediaPlayer.setLooping(true);
                mediaPlayer.setOnPreparedListener(prepared -> {
                    if (!dialog.isShowing()) return;
                    Main.log("code257 account-safety video prepared");
                    try {
                        prepared.start();
                        Main.log("code257 account-safety video started");
                    }
                    catch (Throwable error) {
                        Main.log("code257 account-safety video start failed: "
                                + error.getClass().getSimpleName());
                    }
                });
                mediaPlayer.setOnErrorListener((failed, what, extra) -> {
                    Main.log("code257 account-safety video error what=" + what
                            + " extra=" + extra);
                    return true;
                });
                mediaPlayer.prepareAsync();
            } catch (Throwable error) {
                preparing[0] = false;
                Main.log("code257 account-safety video prepare failed: "
                        + error.getClass().getSimpleName() + ": "
                        + String.valueOf(error.getMessage()));
                try { if (player[0] != null) player[0].release(); }
                catch (Throwable ignored) {}
                player[0] = null;
            }
        };
        texture.setSurfaceTextureListener(new android.view.TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(
                    android.graphics.SurfaceTexture surface, int width, int height) {
                textureReady[0] = true;
                Main.log("code257 account-safety texture available "
                        + width + "x" + height);
                prepare[0].run();
            }
            @Override public void onSurfaceTextureSizeChanged(
                    android.graphics.SurfaceTexture surface, int width, int height) {}
            @Override public boolean onSurfaceTextureDestroyed(
                    android.graphics.SurfaceTexture surface) {
                textureReady[0] = false;
                return true;
            }
            @Override public void onSurfaceTextureUpdated(
                    android.graphics.SurfaceTexture surface) {}
        });

        dialog.setContentView(root);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(ignored -> {
            android.media.MediaPlayer current = player[0];
            player[0] = null;
            if (current != null) {
                try { current.stop(); } catch (Throwable ignoredStop) {}
                try { current.release(); } catch (Throwable ignoredRelease) {}
            }
            android.view.Surface currentSurface = playbackSurface[0];
            playbackSurface[0] = null;
            if (currentSurface != null) {
                try { currentSurface.release(); } catch (Throwable ignoredRelease) {}
            }
        });
        dialog.show();
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            if (!dialog.isShowing() || activity.isFinishing()
                    || (android.os.Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) {
                return;
            }
            File file = extractVideo(activity);
            if (file == null || !file.isFile() || file.length() < 1024L) {
                Main.log("code257 account-safety video unavailable");
                return;
            }
            videoFile[0] = file;
            Main.log("code257 account-safety video extracted bytes=" + file.length());
            prepare[0].run();
        }, V241_VIDEO_START_DELAY_MS);
        handler.postDelayed(() -> {
            if (!dialog.isShowing() || activity.isFinishing()
                    || (android.os.Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) return;
            new android.app.AlertDialog.Builder(activity)
                    .setTitle("提示")
                    .setMessage("你被骗了")
                    .setPositiveButton("确定", null)
                    .show();
        }, REVEAL_DELAY_MS);
    }

    private static File extractVideo(Activity activity) {
        try {
            byte[] bytes = readEmbeddedVideo();
            if (bytes == null || bytes.length < 1024) return null;
            File output = new File(activity.getCacheDir(), HostCompat.isV241()
                    ? "deekseep_rickroll_241.mp4" : "deekseep_rickroll.mp4");
            FileOutputStream stream = new FileOutputStream(output, false);
            try {
                stream.write(bytes);
                stream.flush();
                stream.getFD().sync();
            } finally {
                stream.close();
            }
            return output;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static byte[] readEmbeddedVideo() {
        // LSPosed can define module classes from its own in-memory loader without mapping the
        // module APK into /proc/self/maps. On code257 that made the asset scan find only the host
        // APK, so the white page appeared but playback never received a data source. Resolve the
        // packaged entry through the module class loader first; preserve the APK scan as fallback.
        if (HostCompat.isV241()) {
            byte[] direct = readClassLoaderAsset(V241_VIDEO_ASSET);
            if (direct != null) return direct;
        }
        Set<String> paths = new LinkedHashSet<String>();
        Context context = Main.hostApplicationContext;
        if (context != null) {
            try {
                ApplicationInfo info = context.getPackageManager().getApplicationInfo(
                        "com.smallhairball.deepx", 0);
                if (info != null) {
                    if (info.sourceDir != null) paths.add(info.sourceDir);
                    if (info.splitSourceDirs != null) {
                        for (String path : info.splitSourceDirs) {
                            if (path != null) paths.add(path);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        // PackageManager can be blocked by Android 11+ package visibility; fall back to the
        // process's own mapped APK paths (no root required), same as the native-core loader.
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/maps"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int start = line.indexOf('/');
                int end = line.indexOf(".apk", start);
                if (start >= 0 && end > start) paths.add(line.substring(start, end + 4));
            }
        } catch (Throwable ignored) {}
        for (String path : paths) {
            byte[] bytes = readZipAsset(path, HostCompat.isV241()
                    ? V241_VIDEO_ASSET : LEGACY_VIDEO_ASSET);
            if (bytes != null) return bytes;
        }
        return null;
    }

    private static byte[] readClassLoaderAsset(String entryName) {
        InputStream input = null;
        try {
            ClassLoader loader = AccountSafetyPrank.class.getClassLoader();
            input = loader == null ? null : loader.getResourceAsStream(entryName);
            if (input == null) return null;
            ByteArrayOutputStream output = new ByteArrayOutputStream(3 * 1024 * 1024);
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count > 0) output.write(buffer, 0, count);
                if (output.size() > 16 * 1024 * 1024) return null;
            }
            byte[] bytes = output.toByteArray();
            return bytes.length >= 1024 ? bytes : null;
        } catch (Throwable error) {
            Main.log("code257 account-safety classloader video read failed: "
                    + error.getClass().getSimpleName());
            return null;
        } finally {
            if (input != null) try { input.close(); } catch (Throwable ignored) {}
        }
    }

    private static byte[] readZipAsset(String apkPath, String entryName) {
        ZipFile zip = null;
        try {
            zip = new ZipFile(apkPath);
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null || entry.isDirectory()
                    || entry.getSize() > 16L * 1024L * 1024L) {
                return null;
            }
            InputStream input = zip.getInputStream(entry);
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream(64 * 1024);
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (count > 0) output.write(buffer, 0, count);
                    if (output.size() > 16 * 1024 * 1024) return null;
                }
                return output.toByteArray();
            } finally {
                input.close();
            }
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (zip != null) try { zip.close(); } catch (Throwable ignored) {}
        }
    }

    private static String randomFreezeMessage() {
        if (ThreadLocalRandom.current().nextBoolean()) {
            return "检测到当前设备存在可能影响 DeepSeek 正常运行的外挂或第三方软件。\n\n"
                    + "为保护账号与数据安全，相关数据已被临时冻结。请尽快卸载或关闭相关软件后前往处理中心提交申诉；"
                    + "如持续在异常环境下使用，账号可能面临永久限制。";
        }
        return "经系统核验，该账号存在违反《DeepSeek用户协议》及平台使用规范的风险行为，"
                + "已被临时冻结并退出登录。\n\n请前往处理中心完成核验并提交申诉；如逾期未处理或再次出现违规行为，"
                + "平台可能对账号采取进一步限制措施。";
    }

    private static LinearLayout column(Activity activity, int color, int radius) {
        LinearLayout view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(radius);
        view.setBackground(background);
        return view;
    }

    private static TextView text(Activity activity, String value, float size, int color,
                                 Typeface face) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        view.setTextColor(color);
        view.setTypeface(face);
        return view;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}