package com.smallhairball.deepx;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** UI for DeepSeek-native remote feature switches. */
final class RemoteFeatureFlagsUi {
    private RemoteFeatureFlagsUi() {}

    static void show(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        final boolean dark = DeePXUi.isDark(activity);
        final int background = dark ? 0xFF1B1B1D : 0xFFF5F6F8;
        final int bar = dark ? 0xFF232326 : 0xFFFFFFFF;
        final int card = dark ? 0xFF2A2A2D : 0xFFFFFFFF;
        final int text = dark ? 0xFFECECEC : 0xFF1A1A1A;
        final int secondary = dark ? 0xFF9A9A9E : 0xFF777777;
        final int divider = dark ? 0xFF3A3A3D : 0xFFEEEEEE;

        final Dialog dialog = new Dialog(
                activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        final LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(background);

        LinearLayout appBar = new LinearLayout(activity);
        appBar.setOrientation(LinearLayout.HORIZONTAL);
        appBar.setGravity(Gravity.CENTER_VERTICAL);
        appBar.setBackgroundColor(bar);
        int status = DeePXUi.statusBarHeight(activity);
        appBar.setPadding(dp(activity, 8), status, dp(activity, 16), 0);
        root.addView(appBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 56) + status));

        TextView back = new TextView(activity);
        back.setText("\u2039");
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        back.setTextColor(text);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(activity, 8), 0, dp(activity, 8), 0);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                DeePXUi.slideOutAndDismiss(dialog, root);
            }
        });
        appBar.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 40)));

        TextView title = new TextView(activity);
        title.setText(UiLanguage.text(activity, "DeepSeek 原生功能管理",
                "DeepSeek native features"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(text);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(activity, 8);
        appBar.addView(title, titleParams);

        ScrollView scroll = new ScrollView(activity);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 16), dp(activity, 16),
                dp(activity, 16), dp(activity, 24));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView note = new TextView(activity);
        note.setText(UiLanguage.text(activity,
                "这里集中管理当前 DeepSeek APK 自带的布尔灰度功能与模型配置灰度。"
                        + "选择后会自动重启，让内存缓存同步生效。",
                "This page manages the current DeepSeek APK's Boolean and model-config "
                        + "rollouts. DeepSeek restarts after a change so in-memory caches reload."));
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        note.setTextColor(secondary);
        note.setLineSpacing(dp(activity, 2), 1f);
        note.setPadding(dp(activity, 4), 0, dp(activity, 4), dp(activity, 14));
        content.addView(note);

        final LinearLayout featureCard = new LinearLayout(activity);
        featureCard.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBackground = new GradientDrawable();
        cardBackground.setColor(card);
        cardBackground.setCornerRadius(dp(activity, 12));
        featureCard.setBackground(cardBackground);
        content.addView(featureCard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int displayed = 0;
        for (RemoteFeatureFlags.Feature feature : RemoteFeatureFlags.FEATURES) {
            if (!RemoteFeatureFlags.isSupported(feature)) continue;
            if (displayed++ > 0) featureCard.addView(divider(activity, divider));
            addFeatureRow(activity, featureCard, feature, text, secondary, dark);
        }
        // The reviewed code257 APK contains the native session multi-select screen, menu action,
        // analytics event and batch endpoints, but no independent Boolean rollout key in its
        // remote-settings repository. Surface that verified capability here without inventing a
        // fake kv_remote_settings_* key that would never control the host.
        if (HostCompat.isV241()) {
            if (displayed++ > 0) featureCard.addView(divider(activity, divider));
            addV241NativeMultiSelectRow(activity, featureCard, text, secondary, dark);
        }
        for (RemoteFeatureFlags.Parameter parameter : RemoteFeatureFlags.PARAMETERS) {
            if (!RemoteFeatureFlags.isSupported(parameter)) continue;
            if (displayed++ > 0) featureCard.addView(divider(activity, divider));
            addParameterRow(activity, featureCard, parameter, text, secondary, dark);
        }

        TextView reset = new TextView(activity);
        reset.setText(UiLanguage.text(activity,
                "全部恢复为跟随服务器", "Reset all to follow server"));
        reset.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        reset.setTextColor(DeePXUi.BRAND);
        reset.setGravity(Gravity.CENTER);
        reset.setTypeface(Typeface.DEFAULT_BOLD);
        reset.setBackground(touchBackground(dark, true));
        reset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (HostCompat.isV241()) {
                    reset.setEnabled(false);
                    reset.setText(UiLanguage.text(activity,
                            "正在保存…", "Saving…"));
                    RemoteFeatureFlags.resetAllV241Async(activity.getClassLoader(),
                            new RemoteFeatureFlags.WriteCallback() {
                                @Override public void onComplete(boolean success) {
                                    reset.setEnabled(true);
                                    reset.setText(UiLanguage.text(activity,
                                            "全部恢复为跟随服务器",
                                            "Reset all to follow server"));
                                    if (success) {
                                        Toast.makeText(activity, UiLanguage.text(activity,
                                                "已恢复，正在重启 DeepSeek",
                                                "Reset; restarting DeepSeek"),
                                                Toast.LENGTH_SHORT).show();
                                        restartHost(activity);
                                    } else {
                                        Toast.makeText(activity, UiLanguage.text(activity,
                                                "恢复失败", "Reset failed"),
                                                Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                    return;
                }
                if (RemoteFeatureFlags.resetAll(activity.getClassLoader())) {
                    Toast.makeText(activity, UiLanguage.text(activity,
                            "已恢复，正在重启 DeepSeek",
                            "Reset; restarting DeepSeek"),
                            Toast.LENGTH_SHORT).show();
                    restartHost(activity);
                } else {
                    Toast.makeText(activity, UiLanguage.text(activity,
                            "恢复失败", "Reset failed"), Toast.LENGTH_SHORT).show();
                }
            }
        });
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 46));
        resetParams.topMargin = dp(activity, 12);
        content.addView(reset, resetParams);

        UiLanguage.localizeTree(activity, root);
        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(background));
        }
        DeePXUi.openWithSlide(dialog, root);
        dialog.setOnKeyListener(new Dialog.OnKeyListener() {
            @Override public boolean onKey(DialogInterface ignored, int code,
                    android.view.KeyEvent event) {
                if (code == android.view.KeyEvent.KEYCODE_BACK
                        && event.getAction() == android.view.KeyEvent.ACTION_UP) {
                    DeePXUi.slideOutAndDismiss(dialog, root);
                    return true;
                }
                return false;
            }
        });
    }

    private static void addParameterRow(final Activity activity, LinearLayout parent,
            final RemoteFeatureFlags.Parameter parameter, int text, int secondary, boolean dark) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 16), dp(activity, 13), dp(activity, 14), dp(activity, 13));
        row.setClickable(true);
        row.setBackground(touchBackground(dark, false));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView heading = new TextView(activity);
        heading.setText(UiLanguage.text(activity, parameter.zh, parameter.en));
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        heading.setTextColor(text);
        labels.addView(heading);
        TextView detail = new TextView(activity);
        detail.setText(UiLanguage.text(activity, parameter.detailZh, parameter.detailEn));
        detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        detail.setTextColor(secondary);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(activity, 3);
        labels.addView(detail, detailParams);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelsParams.rightMargin = dp(activity, 10);
        row.addView(labels, labelsParams);

        final TextView value = new TextView(activity);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        value.setTextColor(DeePXUi.BRAND);
        value.setGravity(Gravity.CENTER);
        value.setPadding(dp(activity, 8), dp(activity, 5), dp(activity, 8), dp(activity, 5));
        Integer current = RemoteFeatureFlags.parameterValue(activity.getClassLoader(), parameter.key);
        value.setText(current == null ? UiLanguage.text(activity, "跟随", "Follow")
                : String.valueOf(current.intValue()));
        row.addView(value, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View ignored) {
                final EditText input = new EditText(activity);
                input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
                Integer current = RemoteFeatureFlags.parameterValue(activity.getClassLoader(), parameter.key);
                if (current != null) input.setText(String.valueOf(current.intValue()));
                input.setSelectAllOnFocus(true);
                int margin = dp(activity, 24);
                LinearLayout holder = new LinearLayout(activity);
                holder.setPadding(margin, 0, margin, 0);
                holder.addView(input, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                new AlertDialog.Builder(activity)
                        .setTitle(UiLanguage.text(activity, parameter.zh, parameter.en))
                        .setMessage(UiLanguage.text(activity,
                                "警告：该值直接写入宿主本地配置。异常值可能导致语音、上传或请求异常，并可能触发服务器风控；后果自负。",
                                "Warning: this is written directly to the host local configuration. "
                                        + "Invalid values may break functionality or trigger server risk controls."))
                        .setView(holder)
                        .setNegativeButton(UiLanguage.text(activity, "取消", "Cancel"), null)
                        .setPositiveButton(UiLanguage.text(activity, "应用并重启", "Apply and restart"),
                                new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface dialog, int which) {
                                        String raw = input.getText().toString().trim();
                                        try {
                                            int wanted = Integer.parseInt(raw);
                                            if (RemoteFeatureFlags.setParameterValue(activity.getClassLoader(),
                                                    parameter.key, wanted)) {
                                                Toast.makeText(activity, UiLanguage.text(activity,
                                                        "参数已写入，正在重启 DeepSeek",
                                                        "Parameter saved; restarting DeepSeek"),
                                                        Toast.LENGTH_SHORT).show();
                                                restartHost(activity);
                                            } else {
                                                Toast.makeText(activity, UiLanguage.text(activity,
                                                        "保存失败", "Could not save parameter"),
                                                        Toast.LENGTH_SHORT).show();
                                            }
                                        } catch (Throwable error) {
                                            Toast.makeText(activity, UiLanguage.text(activity,
                                                    "请输入 32 位整数范围内的数值", "Enter a 32-bit integer"),
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                }).show();
            }
        });
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static void addFeatureRow(final Activity activity, LinearLayout parent,
            final RemoteFeatureFlags.Feature feature, int text, int secondary, boolean dark) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 16), dp(activity, 13),
                dp(activity, 14), dp(activity, 13));
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(touchBackground(dark, false));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView heading = new TextView(activity);
        heading.setText(UiLanguage.text(activity, feature.zh, feature.en));
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        heading.setTextColor(text);
        labels.addView(heading);
        TextView detail = new TextView(activity);
        detail.setText(UiLanguage.text(activity, feature.detailZh, feature.detailEn));
        detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        detail.setTextColor(secondary);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(activity, 3);
        labels.addView(detail, detailParams);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelsParams.rightMargin = dp(activity, 10);
        row.addView(labels, labelsParams);

        final TextView state = new TextView(activity);
        state.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        state.setGravity(Gravity.CENTER);
        state.setPadding(dp(activity, 8), dp(activity, 5),
                dp(activity, 8), dp(activity, 5));
        updateState(activity, state, feature.key, dark);
        row.addView(state, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (Boolean.TRUE.equals(state.getTag())) return;
                showModePicker(activity, feature, state, dark);
            }
        });
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static void showModePicker(final Activity activity,
            final RemoteFeatureFlags.Feature feature, final TextView state, final boolean dark) {
        final int current = RemoteFeatureFlags.mode(activity.getClassLoader(), feature.key);
        final int checked = current == RemoteFeatureFlags.FORCE_ON ? 1
                : current == RemoteFeatureFlags.FORCE_OFF ? 2 : 0;
        boolean server = RemoteFeatureFlags.rawValue(
                activity.getClassLoader(), feature.key, false);
        String follow = UiLanguage.text(activity,
                "跟随服务器（当前" + (server ? "开启" : "关闭") + "）",
                "Follow server (currently " + (server ? "on" : "off") + ")");
        final String[] choices = {
                follow,
                UiLanguage.text(activity, "强制开启并重启", "Force on and restart"),
                UiLanguage.text(activity, "强制关闭并重启", "Force off and restart")
        };
        AlertDialog.Builder pickerBuilder = new AlertDialog.Builder(activity)
                .setTitle(UiLanguage.text(activity, feature.zh, feature.en));
        if (feature.key.contains("parallel") || feature.key.contains("prefetch")
                || feature.key.contains("report") || feature.key.contains("gcy")
                || feature.key.contains("volcengine") || feature.key.contains("device_id")) {
            pickerBuilder.setMessage(UiLanguage.text(activity,
                    "警告：此项可能增加请求频率、改变设备标识或触发服务器风控。启用后果由使用者自行承担。",
                    "Warning: this may increase request frequency, change device identity, or trigger "
                            + "server risk controls. Use at your own risk."));
        }
        final String linkedParameterKey = "kv_remote_settings_pow_prefetch".equals(feature.key)
                ? "kv_remote_settings_pow_prefetch_count"
                : "kv_remote_settings_session_prefetch".equals(feature.key)
                        ? "kv_remote_settings_session_prefetch_count" : null;
        if (linkedParameterKey != null) {
            pickerBuilder.setNeutralButton(UiLanguage.text(activity, "编辑预取数量", "Edit prefetch count"),
                    new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) {
                            showParameterEditor(activity,
                                    RemoteFeatureFlags.parameterForKey(linkedParameterKey));
                        }
                    });
        }
        AlertDialog dialog = pickerBuilder
                .setSingleChoiceItems(choices, checked, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface picker, int which) {
                        final int wanted = which == 1 ? RemoteFeatureFlags.FORCE_ON
                                : which == 2 ? RemoteFeatureFlags.FORCE_OFF
                                : RemoteFeatureFlags.FOLLOW;
                        if (HostCompat.isV241()) {
                            state.setTag(Boolean.TRUE);
                            state.setText(UiLanguage.text(activity, "保存中", "Saving"));
                            picker.dismiss();
                            RemoteFeatureFlags.setModeV241Async(activity.getClassLoader(),
                                    feature.key, wanted,
                                    new RemoteFeatureFlags.WriteCallback() {
                                        @Override public void onComplete(boolean success) {
                                            state.setTag(Boolean.FALSE);
                                            updateState(activity, state, feature.key, dark);
                                            if (success) {
                                                Toast.makeText(activity,
                                                        UiLanguage.text(activity,
                                                                "原生设置已写入，正在重启 DeepSeek",
                                                                "Native setting saved; restarting DeepSeek"),
                                                        Toast.LENGTH_SHORT).show();
                                                restartHost(activity);
                                            } else {
                                                Toast.makeText(activity,
                                                        UiLanguage.text(activity,
                                                                "设置保存失败",
                                                                "Could not save setting"),
                                                        Toast.LENGTH_SHORT).show();
                                            }
                                        }
                                    });
                            return;
                        }
                        if (RemoteFeatureFlags.setMode(
                                activity.getClassLoader(), feature.key, wanted)) {
                            updateState(activity, state, feature.key, dark);
                            Toast.makeText(activity, UiLanguage.text(activity,
                                    "原生设置已写入，正在重启 DeepSeek",
                                    "Native setting saved; restarting DeepSeek"),
                                    Toast.LENGTH_SHORT).show();
                            restartHost(activity);
                        } else {
                            Toast.makeText(activity, UiLanguage.text(activity,
                                    "设置保存失败", "Could not save setting"),
                                    Toast.LENGTH_SHORT).show();
                        }
                        picker.dismiss();
                    }
                })
                .setNegativeButton(UiLanguage.text(activity, "取消", "Cancel"), null)
                .create();
        dialog.show();
    }

    private static void showParameterEditor(final Activity activity,
            final RemoteFeatureFlags.Parameter parameter) {
        if (activity == null || parameter == null) return;
        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        Integer current = RemoteFeatureFlags.parameterValue(activity.getClassLoader(), parameter.key);
        if (current != null) input.setText(String.valueOf(current.intValue()));
        input.setSelectAllOnFocus(true);
        int margin = dp(activity, 24);
        LinearLayout holder = new LinearLayout(activity);
        holder.setPadding(margin, 0, margin, 0);
        holder.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(activity)
                .setTitle(UiLanguage.text(activity, parameter.zh, parameter.en))
                .setMessage(UiLanguage.text(activity,
                        "警告：该值直接写入宿主本地配置。异常值可能导致功能异常，并可能触发服务器风控；后果自负。",
                        "Warning: this is written directly to the host local configuration. "
                                + "Invalid values may break functionality or trigger server risk controls."))
                .setView(holder)
                .setNegativeButton(UiLanguage.text(activity, "取消", "Cancel"), null)
                .setPositiveButton(UiLanguage.text(activity, "应用并重启", "Apply and restart"),
                        new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                try {
                                    int wanted = Integer.parseInt(input.getText().toString().trim());
                                    if (RemoteFeatureFlags.setParameterValue(activity.getClassLoader(),
                                            parameter.key, wanted)) {
                                        Toast.makeText(activity, UiLanguage.text(activity,
                                                "参数已写入，正在重启 DeepSeek",
                                                "Parameter saved; restarting DeepSeek"),
                                                Toast.LENGTH_SHORT).show();
                                        restartHost(activity);
                                    } else {
                                        Toast.makeText(activity, UiLanguage.text(activity,
                                                "保存失败", "Could not save parameter"),
                                                Toast.LENGTH_SHORT).show();
                                    }
                                } catch (Throwable error) {
                                    Toast.makeText(activity, UiLanguage.text(activity,
                                            "请输入 32 位整数范围内的数值", "Enter a 32-bit integer"),
                                            Toast.LENGTH_SHORT).show();
                                }
                            }
                        }).show();
    }

    private static void addV241NativeMultiSelectRow(final Activity activity,
            LinearLayout parent, int text, int secondary, boolean dark) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 16), dp(activity, 13),
                dp(activity, 14), dp(activity, 13));
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(touchBackground(dark, false));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView heading = new TextView(activity);
        heading.setText(UiLanguage.text(activity,
                "原生聊天记录多选", "Native chat multi-select"));
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        heading.setTextColor(text);
        labels.addView(heading);
        TextView detail = new TextView(activity);
        detail.setText(UiLanguage.text(activity,
                "code257 已内置于会话长按菜单，没有独立服务器灰度键。",
                "Built into code257's chat long-press menu; it has no separate server flag."));
        detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        detail.setTextColor(secondary);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(activity, 3);
        labels.addView(detail, detailParams);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelsParams.rightMargin = dp(activity, 10);
        row.addView(labels, labelsParams);

        TextView state = new TextView(activity);
        state.setText(UiLanguage.text(activity, "原生内置", "Built in"));
        state.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        state.setTextColor(DeePXUi.BRAND);
        state.setGravity(Gravity.CENTER);
        state.setPadding(dp(activity, 8), dp(activity, 5),
                dp(activity, 8), dp(activity, 5));
        GradientDrawable badge = new GradientDrawable();
        badge.setColor(dark ? 0xFF36363A : 0xFFF0F2F7);
        badge.setCornerRadius(dp(activity, 7));
        state.setBackground(badge);
        row.addView(state, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View ignored) {
                new AlertDialog.Builder(activity)
                        .setTitle(UiLanguage.text(activity,
                                "原生聊天记录多选", "Native chat multi-select"))
                        .setMessage(UiLanguage.text(activity,
                                "已在 DeepSeek 2.4.1/code257 中确认原生多选页面、长按菜单入口和批量接口。该版本没有对应的独立远程灰度键，因此这里显示真实内置状态，不写入无效伪开关。",
                                "The native selection screen, long-press entry, and batch APIs are present in DeepSeek 2.4.1/code257. This build has no independent remote flag, so this row reports the real built-in state instead of writing a non-functional synthetic key."))
                        .setPositiveButton(UiLanguage.text(activity, "知道了", "OK"), null)
                        .show();
            }
        });
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static void updateState(Activity activity, TextView view, String key, boolean dark) {
        int mode = RemoteFeatureFlags.mode(activity.getClassLoader(), key);
        String label;
        int color;
        if (mode == RemoteFeatureFlags.FORCE_ON) {
            label = UiLanguage.text(activity, "强制开", "On");
            color = DeePXUi.BRAND;
        } else if (mode == RemoteFeatureFlags.FORCE_OFF) {
            label = UiLanguage.text(activity, "强制关", "Off");
            color = dark ? 0xFFE3A1A1 : 0xFFB34444;
        } else {
            label = UiLanguage.text(activity, "跟随", "Follow");
            color = dark ? 0xFFB5B5B9 : 0xFF666666;
        }
        view.setText(label);
        view.setTextColor(color);
        GradientDrawable background = new GradientDrawable();
        background.setColor(dark ? 0xFF36363A : 0xFFF0F2F7);
        background.setCornerRadius(dp(activity, 7));
        view.setBackground(background);
    }

    /** Relaunches the host after the current process exits so constructor-cached flags reload. */
    private static void restartHost(final Activity activity) {
        try {
            Intent launch = activity.getPackageManager()
                    .getLaunchIntentForPackage(activity.getPackageName());
            AlarmManager alarms = (AlarmManager) activity.getSystemService(Context.ALARM_SERVICE);
            if (launch == null || alarms == null) throw new IllegalStateException("no launch intent");
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pending = PendingIntent.getActivity(activity, 0xD53E,
                    launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            alarms.set(AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + 600L, pending);
            activity.finishAffinity();
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override public void run() {
                    android.os.Process.killProcess(android.os.Process.myPid());
                }
            }, 180L);
        } catch (Throwable error) {
            Main.log("native feature-setting restart failed: " + error);
            Toast.makeText(activity, UiLanguage.text(activity,
                    "设置已写入，请手动完整重启 DeepSeek",
                    "Setting saved; fully restart DeepSeek manually"),
                    Toast.LENGTH_LONG).show();
        }
    }

    private static View divider(Context context, int color) {
        View divider = new View(context);
        divider.setBackgroundColor(color);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        params.setMargins(dp(context, 16), 0, dp(context, 16), 0);
        divider.setLayoutParams(params);
        return divider;
    }

    private static StateListDrawable touchBackground(boolean dark, boolean rounded) {
        int pressed = dark ? 0xFF38383C : 0xFFE8EAF0;
        int normal = rounded ? (dark ? 0xFF2A2A2D : 0xFFFFFFFF) : 0x00000000;
        GradientDrawable pressedDrawable = new GradientDrawable();
        pressedDrawable.setColor(pressed);
        if (rounded) pressedDrawable.setCornerRadius(12f);
        GradientDrawable normalDrawable = new GradientDrawable();
        normalDrawable.setColor(normal);
        if (rounded) normalDrawable.setCornerRadius(12f);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, pressedDrawable);
        states.addState(new int[]{}, normalDrawable);
        states.setEnterFadeDuration(80);
        states.setExitFadeDuration(120);
        return states;
    }

    private static int dp(Context context, int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                value, context.getResources().getDisplayMetrics()));
    }
}
