package com.smallhairball.deepx;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Proxy;

/**
 * UI bridge for the Closed-only encrypted full backup implementation.
 * The actual crypto/root implementation lives in editions/closed and is loaded reflectively so
 * an Open artifact has neither the implementation nor a visible entry.
 */
final class ClosedFullDataBackupBridge {
    private static final int BRAND_COLOR = 0xFF4D6BFE;
    private static volatile String pendingExportPassword;

    private ClosedFullDataBackupBridge() {}

    static int dp(Context c, float dip) {
        return Math.round(dip * c.getResources().getDisplayMetrics().density);
    }

    static boolean isDark(Context c) {
        return DeePXUi.isDark(c);
    }

    static boolean supported(Context context) {
        if (context == null) return false;
        try {
            android.content.pm.PackageInfo info = context.getPackageManager()
                    .getPackageInfo("com.deepseek.chat", 0);
            long code = android.os.Build.VERSION.SDK_INT >= 28
                    ? info.getLongVersionCode() : info.versionCode;
            if (code != 249L && code != 257L && code != 268L) return false;
            Class.forName("com.smallhairball.deepx.FullDataBackup");
            Class.forName("com.smallhairball.deepx.FullDataBackup$Callback");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static void beginBackup(final Activity activity) {
        if (!supported(activity)) return;
        try {
            AccountManager.syncAllAccounts();
        } catch (Throwable ignored) {}
        showPassword(activity, true, null);
    }

    static void beginRestore(final Activity activity) {
        if (!supported(activity)) return;
        try {
            Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/octet-stream")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            activity.startActivityForResult(picker, Main.FULL_DATA_RESTORE_REQUEST);
        } catch (Throwable error) {
            toast(activity, "无法打开备份文件：" + error.getClass().getSimpleName());
        }
    }

    static void handleExportResult(Activity activity, int result, Intent data) {
        String password = pendingExportPassword;
        pendingExportPassword = null;
        if (result != Activity.RESULT_OK || data == null || data.getData() == null || password == null) return;
        try {
            AccountManager.syncAllAccounts();
        } catch (Throwable ignored) {}
        invoke("backup", activity, data.getData(), password, false);
    }

    static void handleRestoreResult(Activity activity, int result, Intent data) {
        if (result != Activity.RESULT_OK || data == null || data.getData() == null) return;
        try {
            activity.getContentResolver().takePersistableUriPermission(data.getData(),
                    data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Throwable ignored) {}
        showPassword(activity, false, data.getData());
    }

    private static void showPassword(final Activity activity, final boolean backup, final Uri source) {
        final boolean dark = isDark(activity);
        final int bgColor = dark ? 0xFF222329 : 0xFFFFFFFF;
        final int textColor = dark ? 0xFFF3F4F6 : 0xFF111827;
        final int subColor = dark ? 0xFF9CA3AF : 0xFF6B7280;
        final int strokeColor = dark ? 0xFF32343E : 0xFFE5E7EB;
        final int inputBg = dark ? 0xFF2B2D36 : 0xFFF9FAFB;
        final int inputStroke = dark ? 0xFF3F424E : 0xFFE5E7EB;

        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        int padH = dp(activity, 22);
        int padV = dp(activity, 20);
        card.setPadding(padH, padV, padH, padV);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(bgColor);
        cardBg.setCornerRadius(dp(activity, 20));
        cardBg.setStroke(dp(activity, 1), strokeColor);
        card.setBackground(cardBg);

        // 1. 标题
        TextView title = new TextView(activity);
        title.setText(backup ? "全量加密备份" : "恢复全量备份");
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(textColor);
        card.addView(title, lp(-1, -2, 0, 0, 0, dp(activity, 8)));

        // 2. 提示横幅 (Banner)
        LinearLayout banner = new LinearLayout(activity);
        banner.setOrientation(LinearLayout.HORIZONTAL);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        int bPadH = dp(activity, 12);
        int bPadV = dp(activity, 10);
        banner.setPadding(bPadH, bPadV, bPadH, bPadV);

        GradientDrawable bannerBg = new GradientDrawable();
        bannerBg.setColor(dark ? 0xFF2E261B : 0xFFFFFBEB);
        bannerBg.setCornerRadius(dp(activity, 10));
        bannerBg.setStroke(dp(activity, 1), dark ? 0xFF4D381B : 0xFFFDE68A);
        banner.setBackground(bannerBg);

        TextView note = new TextView(activity);
        note.setText(backup
                ? "密码仅用于本次本地 AES-256-GCM 强加密。DeePX 不会上传或保存密码；若忘记密码，备份数据将永久无法解密恢复。"
                : "恢复会停止 DeepSeek 与模块并覆盖现有全部私有数据。若密码错误、文件损坏或路径异常，不会修改现有数据。");
        note.setTextSize(12);
        note.setTextColor(dark ? 0xFFFBBF24 : 0xFFB45309);
        note.setLineSpacing(dp(activity, 2), 1.15f);
        banner.addView(note, lp(-1, -2, 0, 0, 0, 0));
        card.addView(banner, lp(-1, -2, 0, 0, 0, dp(activity, 14)));

        // 3. 密码输入框组件
        final EditText first = new EditText(activity);
        View firstContainer = createInputField(activity, first, backup ? "设置备份密码（至少 8 位）" : "输入备份解密密码", dark, inputBg, inputStroke, textColor, subColor);
        card.addView(firstContainer, lp(-1, -2, 0, 0, 0, backup ? dp(activity, 10) : dp(activity, 4)));

        final EditText second;
        if (backup) {
            second = new EditText(activity);
            View secondContainer = createInputField(activity, second, "再次输入确认密码", dark, inputBg, inputStroke, textColor, subColor);
            card.addView(secondContainer, lp(-1, -2, 0, 0, 0, dp(activity, 4)));
        } else {
            second = null;
        }

        // 错误提示文本（默认不可见）
        final TextView errorMsg = new TextView(activity);
        errorMsg.setTextSize(12);
        errorMsg.setTextColor(0xFFEF4444);
        errorMsg.setVisibility(View.GONE);
        card.addView(errorMsg, lp(-1, -2, 0, dp(activity, 4), 0, dp(activity, 10)));

        // 4. 底部操作按钮区域
        LinearLayout buttons = new LinearLayout(activity);
        buttons.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);

        TextView cancel = createButton(activity, "取消", false, dark, subColor);
        TextView confirm = createButton(activity, backup ? "选择保存位置" : "校验并恢复", true, dark, textColor);

        buttons.addView(cancel, lp(-2, -2, 0, 0, dp(activity, 10), 0));
        buttons.addView(confirm, lp(-2, -2, 0, 0, 0, 0));
        card.addView(buttons, lp(-1, -2, 0, dp(activity, 12), 0, 0));

        cancel.setOnClickListener(v -> dialog.dismiss());
        confirm.setOnClickListener(v -> {
            String password = first.getText() == null ? "" : first.getText().toString();
            String repeat = second == null || second.getText() == null ? password : second.getText().toString();
            if (password.length() < 8) {
                errorMsg.setText("密码长度至少需要 8 个字符");
                errorMsg.setVisibility(View.VISIBLE);
                return;
            }
            if (backup && !password.equals(repeat)) {
                errorMsg.setText("两次输入的密码不一致，请核对后重试");
                errorMsg.setVisibility(View.VISIBLE);
                return;
            }
            errorMsg.setVisibility(View.GONE);
            dialog.dismiss();
            if (backup) {
                pendingExportPassword = password;
                try {
                    Intent destination = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream")
                            .putExtra(Intent.EXTRA_TITLE, "deepx-full-" + System.currentTimeMillis() + ".dskb")
                            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    activity.startActivityForResult(destination, Main.FULL_DATA_BACKUP_REQUEST);
                } catch (Throwable error) {
                    pendingExportPassword = null;
                    toast(activity, "无法打开保存位置：" + error.getClass().getSimpleName());
                }
            } else if (source != null) {
                invoke("restore", activity, source, password, true);
            }
        });

        // 实时输入清空错误提示
        TextWatcher clearError = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (errorMsg.getVisibility() == View.VISIBLE) errorMsg.setVisibility(View.GONE);
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        first.addTextChangedListener(clearError);
        if (second != null) second.addTextChangedListener(clearError);

        dialog.setContentView(card);
        setupWindow(dialog, activity);
        dialog.show();
    }

    private static View createInputField(final Activity activity, final EditText input, String hint,
                                         final boolean dark, int bgColor, int strokeColor,
                                         int textColor, int hintColor) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 14), 0, dp(activity, 4), 0);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(activity, 12));
        bg.setStroke(dp(activity, 1), strokeColor);
        row.setBackground(bg);

        input.setHint(hint);
        input.setHintTextColor(hintColor);
        input.setTextColor(textColor);
        input.setTextSize(14);
        input.setSingleLine(true);
        input.setBackground(null);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setPadding(0, dp(activity, 12), dp(activity, 8), dp(activity, 12));
        row.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final ImageView eyeBtn = new ImageView(activity);
        final EyeDrawable eyeDrawable = new EyeDrawable(dark ? 0xFF8E93A4 : 0xFF6B7280);
        eyeBtn.setImageDrawable(eyeDrawable);
        eyeBtn.setContentDescription("显示或隐藏密码");
        eyeBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        eyeBtn.setBackground(new RippleDrawable(
                android.content.res.ColorStateList.valueOf(dark ? 0x22FFFFFF : 0x18000000),
                null, null));
        eyeBtn.setClickable(true);
        eyeBtn.setFocusable(true);
        eyeBtn.setOnClickListener(v -> {
            boolean isPassword = (input.getInputType() & InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) == 0;
            if (isPassword) {
                input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                eyeDrawable.setRevealed(true);
            } else {
                input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                eyeDrawable.setRevealed(false);
            }
            input.setSelection(input.length());
        });
        row.addView(eyeBtn, new LinearLayout.LayoutParams(dp(activity, 40), dp(activity, 40)));

        return row;
    }

    private static TextView createButton(Activity activity, String text, boolean primary,
                                         boolean dark, int subColor) {
        TextView btn = new TextView(activity);
        btn.setText(text);
        btn.setTextSize(14);
        btn.setTypeface(Typeface.DEFAULT, primary ? Typeface.BOLD : Typeface.NORMAL);
        btn.setGravity(Gravity.CENTER);
        int padH = dp(activity, primary ? 18 : 14);
        int padV = dp(activity, 10);
        btn.setPadding(padH, padV, padH, padV);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(activity, 10));
        if (primary) {
            bg.setColor(BRAND_COLOR);
            btn.setTextColor(0xFFFFFFFF);
        } else {
            bg.setColor(dark ? 0xFF2B2D36 : 0xFFF3F4F6);
            btn.setTextColor(subColor);
        }
        btn.setBackground(bg);
        btn.setClickable(true);
        btn.setFocusable(true);
        return btn;
    }

    static final class ProgressDialogHolder {
        final Dialog dialog;
        final SmoothProgressBar progressBar;
        final TextView bytesText;
        final TextView percentText;

        ProgressDialogHolder(Dialog dialog, SmoothProgressBar progressBar, TextView bytesText, TextView percentText) {
            this.dialog = dialog;
            this.progressBar = progressBar;
            this.bytesText = bytesText;
            this.percentText = percentText;
        }
    }

    private static void invoke(String operation, final Activity activity, Uri uri,
                               String password, final boolean restore) {
        try {
            final ProgressDialogHolder holder = progressDialog(activity, restore ? "正在解密并校验备份" : "正在全量备份并加密", restore);
            holder.dialog.setCancelable(false);
            holder.dialog.setCanceledOnTouchOutside(false);
            if (!activity.isFinishing() && !activity.isDestroyed()) {
                holder.dialog.show();
            }

            final TextView progressText = holder.bytesText;
            final TextView percentText = holder.percentText;
            final SmoothProgressBar progressBar = holder.progressBar;

            Class<?> implementation = Class.forName("com.smallhairball.deepx.FullDataBackup");
            Class<?> callbackType = Class.forName("com.smallhairball.deepx.FullDataBackup$Callback");
            Object callback = Proxy.newProxyInstance(callbackType.getClassLoader(),
                    new Class[]{callbackType}, (proxy, method, args) -> {
                        if ("progress".equals(method.getName())) {
                            long complete = args != null && args.length > 0 && args[0] instanceof Long ? (Long) args[0] : 0L;
                            long total = args != null && args.length > 1 && args[1] instanceof Long ? (Long) args[1] : -1L;
                            activity.runOnUiThread(() -> {
                                if (progressBar == null) return;
                                if (total > 0) {
                                    float ratio = Math.min(1.0f, (float) complete / (float) total);
                                    progressBar.setProgress(ratio);
                                    int pct = (int) (ratio * 100);
                                    if (percentText != null) percentText.setText(pct + "%");
                                    if (progressText != null) {
                                        progressText.setText(humanBytes(complete) + " / " + humanBytes(total));
                                    }
                                } else {
                                    progressBar.setIndeterminate(true);
                                    if (percentText != null) percentText.setText("");
                                    if (progressText != null) {
                                        progressText.setText("已处理 " + humanBytes(complete));
                                    }
                                }
                            });
                        } else if ("done".equals(method.getName())) {
                            boolean ok = args != null && args.length > 0 && Boolean.TRUE.equals(args[0]);
                            String message = args != null && args.length > 1 ? String.valueOf(args[1]) : "";
                            activity.runOnUiThread(() -> {
                                if (!activity.isFinishing() && !activity.isDestroyed()) {
                                    try {
                                        holder.dialog.dismiss();
                                    } catch (Throwable ignored) {}
                                }
                                toast(activity, message);
                            });
                        }
                        return null;
                    });
            implementation.getMethod(operation, Context.class, Uri.class, String.class, callbackType)
                    .invoke(null, activity, uri, password, callback);
        } catch (Throwable error) {
            toast(activity, "全量备份组件不可用：" + String.valueOf(error.getMessage()));
        }
    }

    private static ProgressDialogHolder progressDialog(Activity activity, String title, boolean restore) {
        final boolean dark = isDark(activity);
        final int bgColor = dark ? 0xFF222329 : 0xFFFFFFFF;
        final int textColor = dark ? 0xFFF3F4F6 : 0xFF111827;
        final int subColor = dark ? 0xFF9CA3AF : 0xFF6B7280;
        final int strokeColor = dark ? 0xFF32343E : 0xFFE5E7EB;

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        int padH = dp(activity, 22);
        int padV = dp(activity, 22);
        card.setPadding(padH, padV, padH, padV);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(bgColor);
        cardBg.setCornerRadius(dp(activity, 20));
        cardBg.setStroke(dp(activity, 1), strokeColor);
        card.setBackground(cardBg);

        // 1. 标题
        TextView titleView = new TextView(activity);
        titleView.setText(title);
        titleView.setTextSize(18);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleView.setTextColor(textColor);
        card.addView(titleView, lp(-1, -2, 0, 0, 0, dp(activity, 4)));

        // 2. 副标题说明
        TextView subView = new TextView(activity);
        subView.setText(restore
                ? "正在校验解密数据完整性与路径安全性，请勿关闭应用…"
                : "正在打包 DeepSeek 私有数据与模块配置并加密，请勿关闭应用…");
        subView.setTextSize(12);
        subView.setTextColor(subColor);
        subView.setLineSpacing(dp(activity, 2), 1.15f);
        card.addView(subView, lp(-1, -2, 0, 0, 0, dp(activity, 18)));

        // 3. 丝滑全宽胶囊进度条 (MATCH_PARENT)
        SmoothProgressBar bar = new SmoothProgressBar(activity);
        bar.setTag("backup-progress-bar");
        bar.setIndeterminate(true);
        card.addView(bar, lp(-1, dp(activity, 8), 0, 0, 0, dp(activity, 10)));

        // 4. 双端对齐进度信息 (左: 已处理大小，右: 百分比)
        RelativeLayout infoRow = new RelativeLayout(activity);

        TextView bytesText = new TextView(activity);
        bytesText.setTag("backup-progress-bytes");
        bytesText.setText("准备开始…");
        bytesText.setTextSize(13);
        bytesText.setTextColor(subColor);
        RelativeLayout.LayoutParams leftLp = new RelativeLayout.LayoutParams(-2, -2);
        leftLp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        leftLp.addRule(RelativeLayout.CENTER_VERTICAL);
        infoRow.addView(bytesText, leftLp);

        TextView pctText = new TextView(activity);
        pctText.setTag("backup-progress-percent");
        pctText.setText("");
        pctText.setTextSize(13);
        pctText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        pctText.setTextColor(BRAND_COLOR);
        RelativeLayout.LayoutParams rightLp = new RelativeLayout.LayoutParams(-2, -2);
        rightLp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        rightLp.addRule(RelativeLayout.CENTER_VERTICAL);
        infoRow.addView(pctText, rightLp);

        card.addView(infoRow, lp(-1, -2, 0, 0, 0, 0));

        dialog.setContentView(card);
        setupWindow(dialog, activity);
        return new ProgressDialogHolder(dialog, bar, bytesText, pctText);
    }

    private static void setupWindow(Dialog dialog, Activity activity) {
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setDimAmount(0.52f);
            w.setWindowAnimations(android.R.style.Animation_Dialog);
            int width = Math.min(dp(activity, 380),
                    (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.90f));
            w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private static LinearLayout.LayoutParams lp(int width, int height, float weight,
                                                int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height, weight);
        p.topMargin = top;
        p.rightMargin = right;
        p.bottomMargin = bottom;
        return p;
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static void toast(Activity activity, String text) {
        Toast.makeText(activity, text, Toast.LENGTH_LONG).show();
    }

    /**
     * 高质感平滑胶囊进度条 View。
     * - 支持 MATCH_PARENT 任意宽度自适应；
     * - 支持确定进度的丝滑插值动画 (ValueAnimator)；
     * - 支持不确定进度的优雅胶囊往返扫描动画；
     * - 符合 baseline-ui 的轻量、无闪烁、不漏帧设计。
     */
    private static final class SmoothProgressBar extends View {
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        private boolean indeterminate = true;
        private float currentProgress = 0f;
        private float targetProgress = 0f;
        private ValueAnimator progressAnimator;

        private float indeterminateOffset = 0f;
        private ValueAnimator indeterminateAnimator;

        SmoothProgressBar(Context context) {
            super(context);
            boolean dark = isDark(context);
            trackPaint.setColor(dark ? 0xFF32343E : 0xFFE5E7EB);
            progressPaint.setColor(BRAND_COLOR);
            startIndeterminateAnimation();
        }

        void setIndeterminate(boolean ind) {
            if (this.indeterminate == ind) return;
            this.indeterminate = ind;
            if (ind) {
                startIndeterminateAnimation();
            } else {
                stopIndeterminateAnimation();
            }
            invalidate();
        }

        void setProgress(float ratio) {
            float clamped = Math.max(0f, Math.min(1f, ratio));
            if (this.indeterminate) {
                setIndeterminate(false);
            }
            if (Math.abs(clamped - targetProgress) < 0.001f) return;
            this.targetProgress = clamped;
            if (progressAnimator != null) progressAnimator.cancel();
            progressAnimator = ValueAnimator.ofFloat(currentProgress, targetProgress);
            progressAnimator.setDuration(180);
            progressAnimator.setInterpolator(new DecelerateInterpolator());
            progressAnimator.addUpdateListener(animation -> {
                currentProgress = (Float) animation.getAnimatedValue();
                invalidate();
            });
            progressAnimator.start();
        }

        private void startIndeterminateAnimation() {
            if (indeterminateAnimator != null) return;
            indeterminateAnimator = ValueAnimator.ofFloat(0f, 1f);
            indeterminateAnimator.setDuration(1200);
            indeterminateAnimator.setRepeatCount(ValueAnimator.INFINITE);
            indeterminateAnimator.setRepeatMode(ValueAnimator.REVERSE);
            indeterminateAnimator.setInterpolator(new DecelerateInterpolator());
            indeterminateAnimator.addUpdateListener(animation -> {
                indeterminateOffset = (Float) animation.getAnimatedValue();
                invalidate();
            });
            indeterminateAnimator.start();
        }

        private void stopIndeterminateAnimation() {
            if (indeterminateAnimator != null) {
                indeterminateAnimator.cancel();
                indeterminateAnimator = null;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;
            float radius = h / 2f;

            // 1. 轨道背景
            rect.set(0, 0, w, h);
            canvas.drawRoundRect(rect, radius, radius, trackPaint);

            // 2. 进度填充
            if (indeterminate) {
                float sliderW = w * 0.35f;
                float startX = (w - sliderW) * indeterminateOffset;
                rect.set(startX, 0, startX + sliderW, h);
                canvas.drawRoundRect(rect, radius, radius, progressPaint);
            } else {
                float fillW = Math.max(h, w * currentProgress);
                rect.set(0, 0, fillW, h);
                canvas.drawRoundRect(rect, radius, radius, progressPaint);
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            stopIndeterminateAnimation();
            if (progressAnimator != null) {
                progressAnimator.cancel();
                progressAnimator = null;
            }
        }
    }

    /**
     * 矢量绘制眼睛 Drawable，支持显隐状态平滑切换。
     */
    private static final class EyeDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean revealed = false;

        EyeDrawable(int color) {
            paint.setColor(color);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        void setRevealed(boolean revealed) {
            if (this.revealed != revealed) {
                this.revealed = revealed;
                invalidateSelf();
            }
        }

        @Override
        public void draw(Canvas canvas) {
            RectF b = new RectF(getBounds());
            float cx = b.centerX();
            float cy = b.centerY();
            float w = b.width();
            float h = b.height();
            if (w <= 0 || h <= 0) return;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2.2f, w * 0.055f));

            // 外轮廓轮廓弧线
            RectF eyeBounds = new RectF(cx - w * 0.32f, cy - h * 0.20f,
                    cx + w * 0.32f, cy + h * 0.20f);
            canvas.drawOval(eyeBounds, paint);

            // 瞳孔
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, cy, w * 0.10f, paint);

            // 隐藏状态下的斜杠
            if (!revealed) {
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawLine(cx - w * 0.26f, cy - h * 0.24f,
                        cx + w * 0.26f, cy + h * 0.24f, paint);
            }
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
}