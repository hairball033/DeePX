package com.smallhairball.deepx;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Full-screen child page for persistent and direct public Local API endpoints. */
final class z19 {
    private static final int BRAND = 0xFF4D6BFE;

    private z19() {}

    static void show(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        final boolean dark = isDark(activity);
        final int bg = dark ? 0xFF1B1B1D : 0xFFF5F6F8;
        final int bar = dark ? 0xFF232326 : 0xFFFFFFFF;
        final int cardColor = dark ? 0xFF2A2A2D : 0xFFFFFFFF;
        final int text = dark ? 0xFFECECEC : 0xFF1A1A1A;
        final int sub = dark ? 0xFFAAAAAF : 0xFF70757D;
        final int divider = dark ? 0xFF3A3A3D : 0xFFEEEEEE;

        final Dialog dialog = new Dialog(
                activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        final LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);

        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(bar);
        int statusTop = statusBarHeight(activity);
        top.setPadding(dp(activity, 8), statusTop, dp(activity, 16), 0);
        root.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 56) + statusTop));

        TextView back = new TextView(activity);
        back.setText("\u2039");
        back.setTextColor(text);
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(activity, 8), 0, dp(activity, 8), 0);
        top.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 40)));

        TextView title = label(activity,
                "本地 API 高级设置", "Local API advanced settings",
                18, text, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(activity, 8);
        top.addView(title, titleParams);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 16), dp(activity, 16),
                dp(activity, 16), dp(activity, 24));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final Bundle initial = Main.localApiPublicTunnelStatus(activity);
        final Bundle initialPinggy = Main.localApiPinggyTunnelStatus(activity);

        LinearLayout keepAliveCard = card(activity, cardColor);
        content.addView(keepAliveCard, matchWrap());
        LinearLayout keepAliveRow = new LinearLayout(activity);
        keepAliveRow.setOrientation(LinearLayout.HORIZONTAL);
        keepAliveRow.setGravity(Gravity.CENTER_VERTICAL);
        keepAliveRow.setPadding(dp(activity, 16), dp(activity, 12),
                dp(activity, 12), dp(activity, 12));
        LinearLayout keepAliveCopy = new LinearLayout(activity);
        keepAliveCopy.setOrientation(LinearLayout.VERTICAL);
        keepAliveCopy.addView(label(activity,
                "关闭保活", "Disable keepalive", 15, text, true));
        keepAliveCopy.addView(label(activity,
                "停止前台服务、周期心跳和唤醒锁以减少发热；本地 API 仍可用，但系统回收 DeepSeek 后会离线",
                "Stop the foreground service, periodic heartbeat, and wake lock to reduce heat. The Local API remains available until Android stops DeepSeek",
                12, sub, false));
        keepAliveRow.addView(keepAliveCopy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch keepAliveDisabled = new HubInsetSwitch(activity);
        keepAliveDisabled.setChecked(Main.isLocalApiKeepAliveDisabled());
        keepAliveDisabled.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    private boolean reverting;

                    @Override public void onCheckedChanged(
                            CompoundButton button, boolean checked) {
                        if (reverting) return;
                        boolean applied = Main.setLocalApiKeepAliveDisabled(activity, checked);
                        if (!applied) {
                            reverting = true;
                            button.setChecked(!checked);
                            reverting = false;
                            Toast.makeText(activity,
                                    t(activity, "保活设置失败，请查看模块日志",
                                            "Could not update keepalive; check module logs"),
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        Toast.makeText(activity, checked
                                        ? t(activity, "保活已关闭，本地 API 将随 DeepSeek 进程运行",
                                                "Keepalive disabled; the API now follows the DeepSeek process")
                                        : t(activity, "保活已恢复", "Keepalive restored"),
                                Toast.LENGTH_SHORT).show();
                    }
                });
        keepAliveRow.addView(keepAliveDisabled);
        keepAliveCard.addView(keepAliveRow);

        LinearLayout tlsCard = card(activity, cardColor);
        LinearLayout.LayoutParams tlsCardParams = matchWrap();
        tlsCardParams.topMargin = dp(activity, 14);
        content.addView(tlsCard, tlsCardParams);
        final LinearLayout tlsRow = new LinearLayout(activity);
        tlsRow.setOrientation(LinearLayout.HORIZONTAL);
        tlsRow.setGravity(Gravity.CENTER_VERTICAL);
        tlsRow.setPadding(dp(activity, 16), dp(activity, 12),
                dp(activity, 12), dp(activity, 12));
        tlsRow.setClickable(true);
        tlsRow.setFocusable(true);
        LinearLayout tlsCopy = new LinearLayout(activity);
        tlsCopy.setOrientation(LinearLayout.VERTICAL);
        tlsCopy.addView(label(activity,
                "启用 HTTPS", "Enable HTTPS", 15, text, true));
        tlsCopy.addView(label(activity,
                "本机独立证书；进入 Root / 免 Root 部署页",
                "Per-device certificate; open Root / rootless deployment",
                12, sub, false));
        tlsRow.addView(tlsCopy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch tlsEnabled = new HubInsetSwitch(activity);
        tlsEnabled.setChecked(Main.isLocalApiHttpsEnabled(activity));
        tlsRow.addView(tlsEnabled);
        tlsCard.addView(tlsRow);
        final java.util.concurrent.atomic.AtomicBoolean tlsUpdating =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        tlsEnabled.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(
                            CompoundButton button, boolean checked) {
                        if (tlsUpdating.get()) return;
                        if (checked) {
                            z7.show(activity, true, new Runnable() {
                                @Override public void run() {
                                    tlsUpdating.set(true);
                                    tlsEnabled.setChecked(
                                            Main.isLocalApiHttpsEnabled(activity));
                                    tlsUpdating.set(false);
                                }
                            });
                            return;
                        }
                        tlsUpdating.set(true);
                        new Thread(new Runnable() {
                            @Override public void run() {
                                final String result = Main.setLocalApiHttpsEnabled(
                                        activity, false);
                                activity.runOnUiThread(new Runnable() {
                                    @Override public void run() {
                                        tlsEnabled.setChecked(
                                                Main.isLocalApiHttpsEnabled(activity));
                                        tlsUpdating.set(false);
                                        android.widget.Toast.makeText(activity,
                                                result, android.widget.Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                        }, "DeePX-TLS-Disable").start();
                    }
                });
        tlsRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (Main.isLocalApiHttpsEnabled(activity)) {
                    z7.show(activity, false, null);
                } else {
                    tlsEnabled.setChecked(true);
                }
            }
        });

        final Switch[] promptSwHolder = new Switch[1];

        // ── 本地 API 提示词注入 ──────────────────────────────────────
        LinearLayout promptCard = card(activity, cardColor);
        LinearLayout.LayoutParams promptCardParams = matchWrap();
        promptCardParams.topMargin = dp(activity, 14);
        content.addView(promptCard, promptCardParams);

        LinearLayout promptImportSection = new LinearLayout(activity);
        promptImportSection.setOrientation(LinearLayout.VERTICAL);
        promptImportSection.setGravity(Gravity.CENTER_HORIZONTAL);
        promptImportSection.setPadding(dp(activity, 16), dp(activity, 18),
                dp(activity, 16), dp(activity, 14));

        final TextView importPromptBtn = new TextView(activity);
        importPromptBtn.setText(t(activity, "导入提示词", "Import prompt"));
        importPromptBtn.setTextColor(BRAND);
        importPromptBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        importPromptBtn.setTypeface(Typeface.DEFAULT);
        importPromptBtn.setGravity(Gravity.CENTER);
        importPromptBtn.setPadding(dp(activity, 18), dp(activity, 8),
                dp(activity, 18), dp(activity, 8));
        GradientDrawable importPromptBg = new GradientDrawable();
        importPromptBg.setColor(dark ? 0xFF252545 : 0xFFEEF1FF);
        importPromptBg.setCornerRadius(dp(activity, 6));
        importPromptBtn.setBackground(importPromptBg);
        importPromptBtn.setClickable(true);
        importPromptBtn.setFocusable(true);
        promptImportSection.addView(importPromptBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final TextView promptPathText = new TextView(activity);
        promptPathText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        promptPathText.setTextColor(sub);
        promptPathText.setGravity(Gravity.CENTER);
        promptPathText.setText(Main.getLocalApiDefaultPromptPath().length() > 0
                ? Main.getLocalApiDefaultPromptPath()
                : t(activity, "尚未导入提示词", "No prompt imported"));
        LinearLayout.LayoutParams pptlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pptlp.topMargin = dp(activity, 8);
        promptImportSection.addView(promptPathText, pptlp);
        promptCard.addView(promptImportSection);

        importPromptBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                Main.localApiPromptPickComplete = new Runnable() {
                    @Override public void run() {
                        promptPathText.setText(Main.getLocalApiDefaultPromptPath().length() > 0
                                ? Main.getLocalApiDefaultPromptPath()
                                : t(activity, "尚未导入提示词", "No prompt imported"));
                    }
                };
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/*");
                try {
                    activity.startActivityForResult(intent,
                            Main.LOCAL_API_PROMPT_IMPORT_REQUEST);
                } catch (android.content.ActivityNotFoundException e) {
                    intent.setType("*/*");
                    activity.startActivityForResult(intent,
                            Main.LOCAL_API_PROMPT_IMPORT_REQUEST);
                }
            }
        });

        LinearLayout promptRow = new LinearLayout(activity);
        promptRow.setOrientation(LinearLayout.HORIZONTAL);
        promptRow.setGravity(Gravity.CENTER_VERTICAL);
        promptRow.setPadding(dp(activity, 16), dp(activity, 10),
                dp(activity, 12), dp(activity, 12));
        LinearLayout promptCopy = new LinearLayout(activity);
        promptCopy.setOrientation(LinearLayout.VERTICAL);
        promptCopy.addView(label(activity,
                "注入提示词", "Inject prompt", 15, text, true));
        promptCopy.addView(label(activity,
                "为所有本地 API 请求自动注入系统提示词",
                "Auto-inject a system prompt into every local API request",
                12, sub, false));
        promptRow.addView(promptCopy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch promptSw = new HubInsetSwitch(activity);
        promptSwHolder[0] = promptSw;
        promptSw.setChecked(Main.isLocalApiDefaultPromptEnabled());
        promptSw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                Main.setLocalApiDefaultPromptEnabled(checked);
            }
        });
        promptRow.addView(promptSw);
        promptCard.addView(promptRow);

        LinearLayout relayCard = card(activity, cardColor);
        LinearLayout.LayoutParams relayCardParams = matchWrap();
        relayCardParams.topMargin = dp(activity, 14);
        content.addView(relayCard, relayCardParams);
        LinearLayout relayRow = new LinearLayout(activity);
        relayRow.setOrientation(LinearLayout.HORIZONTAL);
        relayRow.setGravity(Gravity.CENTER_VERTICAL);
        relayRow.setPadding(dp(activity, 16), dp(activity, 12),
                dp(activity, 12), dp(activity, 12));
        LinearLayout relayCopy = new LinearLayout(activity);
        relayCopy.setOrientation(LinearLayout.VERTICAL);
        relayCopy.addView(label(activity,
                "超长上下文转文件", "Move long context to a file", 15, text, true));
        relayCopy.addView(label(activity,
                "超过输出上限或 8000 字时自动附加 TXT",
                "Attach overflow as TXT beyond the output limit or 8,000 characters",
                12, sub, false));
        relayRow.addView(relayCopy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch relayEnabled = new HubInsetSwitch(activity);
        relayEnabled.setChecked(Main.isLocalApiContextRelayEnabled());
        relayEnabled.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(
                            CompoundButton button, boolean checked) {
                        Main.setLocalApiContextRelayEnabled(checked);
                    }
                });
        relayRow.addView(relayEnabled);
        relayCard.addView(relayRow);

        LinearLayout serialCard = card(activity, cardColor);
        LinearLayout.LayoutParams serialCardParams = matchWrap();
        serialCardParams.topMargin = dp(activity, 14);
        content.addView(serialCard, serialCardParams);
        LinearLayout serialRow = new LinearLayout(activity);
        serialRow.setOrientation(LinearLayout.HORIZONTAL);
        serialRow.setGravity(Gravity.CENTER_VERTICAL);
        serialRow.setPadding(dp(activity, 16), dp(activity, 12),
                dp(activity, 12), dp(activity, 12));
        LinearLayout serialCopy = new LinearLayout(activity);
        serialCopy.setOrientation(LinearLayout.VERTICAL);
        serialCopy.addView(label(activity,
                "串行机制", "Serial request policy", 15, text, true));
        serialCopy.addView(label(activity,
                "减少服务器中断，但会降低并发效率；建议与多账号池一起使用",
                "Reduces server interruptions but lowers throughput; recommended with the multi-account pool",
                12, sub, false));
        serialRow.addView(serialCopy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch serialEnabled = new HubInsetSwitch(activity);
        serialEnabled.setChecked(Main.isLocalApiSerialEnabled());
        serialEnabled.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(
                            CompoundButton button, boolean checked) {
                        Main.setLocalApiSerialEnabled(checked);
                    }
                });
        serialRow.addView(serialEnabled);
        serialCard.addView(serialRow);

        // ── 本地 API 防撤回 ──────────────────────────────────────────
        LinearLayout noCensorCard = card(activity, cardColor);
        LinearLayout.LayoutParams noCensorCardParams = matchWrap();
        noCensorCardParams.topMargin = dp(activity, 14);
        content.addView(noCensorCard, noCensorCardParams);
        LinearLayout noCensorRow = new LinearLayout(activity);
        noCensorRow.setOrientation(LinearLayout.HORIZONTAL);
        noCensorRow.setGravity(Gravity.CENTER_VERTICAL);
        noCensorRow.setPadding(dp(activity, 16), dp(activity, 12),
                dp(activity, 12), dp(activity, 12));
        LinearLayout noCensorCopy = new LinearLayout(activity);
        noCensorCopy.setOrientation(LinearLayout.VERTICAL);
        noCensorCopy.addView(label(activity,
                "本地 API 防撤回", "Local API anti-censor", 15, text, true));
        noCensorCopy.addView(label(activity,
                "阻止服务端对本地 API 回复进行安全审查替换",
                "Prevent content-filter replacement of local API responses",
                12, sub, false));
        noCensorRow.addView(noCensorCopy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch noCensorSw = new HubInsetSwitch(activity);
        noCensorSw.setChecked(Main.isLocalApiNoCensor());
        noCensorSw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                Main.setLocalApiNoCensor(checked);
            }
        });
        noCensorRow.addView(noCensorSw);
        noCensorCard.addView(noCensorRow);

        LinearLayout reasoningCard = card(activity, cardColor);
        LinearLayout.LayoutParams reasoningCardParams = matchWrap();
        reasoningCardParams.topMargin = dp(activity, 14);
        content.addView(reasoningCard, reasoningCardParams);
        LinearLayout reasoningRow = new LinearLayout(activity);
        reasoningRow.setOrientation(LinearLayout.HORIZONTAL);
        reasoningRow.setGravity(Gravity.CENTER_VERTICAL);
        reasoningRow.setPadding(dp(activity, 16), dp(activity, 12),
                dp(activity, 12), dp(activity, 12));
        LinearLayout reasoningCopy = new LinearLayout(activity);
        reasoningCopy.setOrientation(LinearLayout.VERTICAL);
        reasoningCopy.addView(settingTitle(activity,
                "强制深度思考", "Force deep reasoning", text,
                new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        showReasoningModelSettings(activity);
                    }
                }));
        reasoningCopy.addView(label(activity,
                "忽略客户端开关；所有本地 API 请求启用原生 thinking 并返回思考链",
                "Ignore client switches; every Local API request enables native thinking and returns its reasoning chain",
                12, sub, false));
        reasoningRow.addView(reasoningCopy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch reasoningEnabled = new HubInsetSwitch(activity);
        reasoningEnabled.setChecked(Main.isLocalApiForceReasoningEnabled());
        reasoningEnabled.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(
                            CompoundButton button, boolean checked) {
                        Main.setLocalApiForceReasoningEnabled(checked);
                    }
                });
        reasoningRow.addView(reasoningEnabled);
        reasoningCard.addView(reasoningRow);

        LinearLayout customModelCard = card(activity, cardColor);
        LinearLayout.LayoutParams customModelCardParams = matchWrap();
        customModelCardParams.topMargin = dp(activity, 14);
        content.addView(customModelCard, customModelCardParams);
        LinearLayout customModelRow = new LinearLayout(activity);
        customModelRow.setOrientation(LinearLayout.HORIZONTAL);
        customModelRow.setGravity(Gravity.CENTER_VERTICAL);
        customModelRow.setPadding(dp(activity, 16), dp(activity, 12),
                dp(activity, 12), dp(activity, 12));
        final TextView customModelCount = label(activity, "", "", 13, sub, false);
        final Runnable refreshCustomModelCount = () -> {
            int count = customModels().length();
            customModelCount.setText(t(activity, count + " 个  ›", count + "  ›"));
        };
        LinearLayout customModelCopy = new LinearLayout(activity);
        customModelCopy.setOrientation(LinearLayout.VERTICAL);
        customModelCopy.addView(settingTitle(activity,
                "自定义模型", "Custom models", text,
                view -> showCustomModelSettings(activity, refreshCustomModelCount)));
        customModelCopy.addView(label(activity,
                "维护客户端模型名与原生模型的映射",
                "Map client model names to native models", 12, sub, false));
        customModelRow.addView(customModelCopy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        customModelCount.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        customModelRow.addView(customModelCount, new LinearLayout.LayoutParams(
                dp(activity, 72), dp(activity, 44)));
        refreshCustomModelCount.run();
        customModelRow.setClickable(true);
        customModelRow.setOnClickListener(
                view -> showCustomModelSettings(activity, refreshCustomModelCount));
        customModelCard.addView(customModelRow);

        LinearLayout busyFallbackCard = card(activity, cardColor);
        LinearLayout.LayoutParams busyFallbackCardParams = matchWrap();
        busyFallbackCardParams.topMargin = dp(activity, 14);
        content.addView(busyFallbackCard, busyFallbackCardParams);
        LinearLayout busyFallbackRow = new LinearLayout(activity);
        busyFallbackRow.setOrientation(LinearLayout.HORIZONTAL);
        busyFallbackRow.setGravity(Gravity.CENTER_VERTICAL);
        busyFallbackRow.setPadding(dp(activity, 16), dp(activity, 12),
                dp(activity, 12), dp(activity, 12));
        LinearLayout busyFallbackCopy = new LinearLayout(activity);
        busyFallbackCopy.setOrientation(LinearLayout.VERTICAL);
        busyFallbackCopy.addView(label(activity,
                "专家繁忙回退快速模式", "Fallback when expert is busy", 15, text, true));
        busyFallbackCopy.addView(label(activity,
                "深度思考模型繁忙时改用快速模型；开启多账号随机路由时以换账号优先",
                "Switch to the fast model when deep thinking is busy; multi-account routing takes priority when enabled",
                12, sub, false));
        busyFallbackRow.addView(busyFallbackCopy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch busyFallbackEnabled = new HubInsetSwitch(activity);
        busyFallbackEnabled.setChecked(Main.isLocalApiExpertBusyFallbackEnabled());
        busyFallbackEnabled.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(
                            CompoundButton button, boolean checked) {
                        Main.setLocalApiExpertBusyFallbackEnabled(checked);
                    }
                });
        busyFallbackRow.addView(busyFallbackEnabled);
        busyFallbackCard.addView(busyFallbackRow);

        LinearLayout recoveryCard = card(activity, cardColor);
        LinearLayout.LayoutParams recoveryCardParams = matchWrap();
        recoveryCardParams.topMargin = dp(activity, 14);
        content.addView(recoveryCard, recoveryCardParams);
        LinearLayout recoveryRow = new LinearLayout(activity);
        recoveryRow.setOrientation(LinearLayout.HORIZONTAL);
        recoveryRow.setGravity(Gravity.CENTER_VERTICAL);
        recoveryRow.setPadding(dp(activity, 16), dp(activity, 12),
                dp(activity, 12), dp(activity, 12));
        LinearLayout recoveryCopy = new LinearLayout(activity);
        recoveryCopy.setOrientation(LinearLayout.VERTICAL);
        recoveryCopy.addView(label(activity,
                "自动补救", "Automatic recovery", 15, text, true));
        recoveryCopy.addView(label(activity,
                "无正文或流中断时自动重试 / 续接，并累计补救次数",
                "Auto-retry empty output or interrupted streams and count recoveries",
                12, sub, false));
        recoveryRow.addView(recoveryCopy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch recoveryEnabled = new HubInsetSwitch(activity);
        recoveryEnabled.setChecked(Main.isLocalApiAutoRecoveryEnabled());
        recoveryRow.addView(recoveryEnabled);
        recoveryCard.addView(recoveryRow);
        recoveryCard.addView(line(activity, divider));
        final TextView recoveryStatus = body(activity, "", "", sub);
        recoveryStatus.setText(t(activity,
                "已经为您补救 " + Main.localApiRecoveryCount() + " 次",
                "Recovered " + Main.localApiRecoveryCount() + " time(s) for you"));
        recoveryCard.addView(recoveryStatus, inset(activity, 8, 4));
        recoveryEnabled.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(
                            CompoundButton button, boolean checked) {
                        Main.setLocalApiAutoRecoveryEnabled(checked);
                        recoveryStatus.setText(t(activity,
                                "已经为您补救 " + Main.localApiRecoveryCount() + " 次",
                                "Recovered " + Main.localApiRecoveryCount() + " time(s) for you"));
                    }
                });

        LinearLayout routingCard = card(activity, cardColor);
        LinearLayout.LayoutParams routingCardParams = matchWrap();
        routingCardParams.topMargin = dp(activity, 14);
        content.addView(routingCard, routingCardParams);
        LinearLayout routingRow = new LinearLayout(activity);
        routingRow.setOrientation(LinearLayout.HORIZONTAL);
        routingRow.setGravity(Gravity.CENTER_VERTICAL);
        routingRow.setPadding(dp(activity, 16), dp(activity, 12),
                dp(activity, 12), dp(activity, 12));
        final Switch[] routingSwitchHolder = new Switch[1];
        final Runnable[] routingRefreshHolder = new Runnable[1];
        LinearLayout routingCopy = new LinearLayout(activity);
        routingCopy.setOrientation(LinearLayout.VERTICAL);
        routingCopy.addView(settingTitle(activity,
                "多账号随机路由", "Random multi-account routing", text,
                new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        if (routingSwitchHolder[0] != null && routingRefreshHolder[0] != null) {
                            showRoutingAccountSettings(activity, routingSwitchHolder[0],
                                    routingRefreshHolder[0]);
                        }
                    }
                }));
        routingCopy.addView(label(activity,
                "每次请求选择不同账号；完整上下文与长文本附件保持不变",
                "Choose another account per request while preserving context and attachments",
                12, sub, false));
        routingRow.addView(routingCopy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch routingEnabled = new HubInsetSwitch(activity);
        routingSwitchHolder[0] = routingEnabled;
        routingEnabled.setChecked(Main.isLocalApiMultiAccountRoutingEnabled());
        routingRow.addView(routingEnabled);
        routingCard.addView(routingRow);
        routingCard.addView(line(activity, divider));
        final TextView routingStatus = body(activity, "", "", sub);
        final Runnable refreshRoutingStatus = new Runnable() {
            @Override public void run() {
                int count = Main.localApiRoutingAccountCount();
                routingStatus.setText(t(activity,
                        "账号池：" + count + " 个可用账号（至少需要 2 个）",
                        "Account pool: " + count + " available (at least 2 required)"));
            }
        };
        routingRefreshHolder[0] = refreshRoutingStatus;
        refreshRoutingStatus.run();
        routingCard.addView(routingStatus, inset(activity, 8, 4));
        final TextView manageRoutingAccounts = action(activity,
                "导入 / 管理账号", "Import / manage accounts", BRAND, dark);
        routingCard.addView(manageRoutingAccounts, inset(activity, 4, 12));
        final boolean[] routingSync = new boolean[]{false};
        routingEnabled.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(
                            CompoundButton button, boolean checked) {
                        if (routingSync[0]) return;
                        if (Main.setLocalApiMultiAccountRoutingEnabled(checked)) {
                            refreshRoutingStatus.run();
                            return;
                        }
                        routingSync[0] = true;
                        routingEnabled.setChecked(false);
                        routingSync[0] = false;
                        refreshRoutingStatus.run();
                        if (checked) {
                            new AlertDialog.Builder(activity)
                                    .setTitle(t(activity,
                                            "需要更多账号", "More accounts required"))
                                    .setMessage(t(activity,
                                            "多账号随机路由至少需要两个已导入且凭据有效的账号。请先登录或导入另一个账号。",
                                            "Random routing needs at least two imported accounts with valid credentials. Sign in to or import another account first."))
                                    .setPositiveButton(t(activity,
                                            "管理账号", "Manage accounts"),
                                            (ignored, which) -> AccountUi.show(activity))
                                    .setNegativeButton(t(activity, "取消", "Cancel"), null)
                                    .show();
                        }
                    }
                });
        manageRoutingAccounts.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                AccountUi.show(activity);
                routingStatus.postDelayed(refreshRoutingStatus, 800L);
            }
        });

        LinearLayout pinggyCard = card(activity, cardColor);
        LinearLayout.LayoutParams pinggyCardParams = matchWrap();
        pinggyCardParams.topMargin = dp(activity, 14);
        content.addView(pinggyCard, pinggyCardParams);
        pinggyCard.addView(section(activity,
                "一小时临时网址（Pinggy）", "One-hour temporary URL (Pinggy)", text));
        pinggyCard.addView(body(activity,
                "无需注册或填写密码。点击申请后，APP 会按 Pinggy 官方 SSH 流程自动提交空密码"
                        + "（等同于在密码提示处直接回车），再把随机 HTTPS 地址转发到本地 API。"
                        + "本机和局域网监听会继续运行；免费地址约 60 分钟后失效。",
                "No account or password is required. The app follows Pinggy's documented SSH "
                        + "flow and submits an empty password (the same as pressing Enter), then "
                        + "forwards the random HTTPS address to the local API. Local and LAN "
                        + "listeners stay active; the free address expires after about 60 minutes.",
                sub), inset(activity, 0, 8));

        final TextView pinggyStatus = info(activity,
                pinggyStatusText(activity, initialPinggy), text, dark);
        pinggyCard.addView(pinggyStatus, inset(activity, 0, 10));

        LinearLayout pinggyActions = new LinearLayout(activity);
        pinggyActions.setOrientation(LinearLayout.HORIZONTAL);
        pinggyActions.setPadding(dp(activity, 16), dp(activity, 4),
                dp(activity, 16), dp(activity, 8));
        final TextView startPinggy = action(activity,
                "申请临时网址", "Request temporary URL", BRAND, dark);
        pinggyActions.addView(startPinggy, weighted());
        final TextView copyPinggy = action(activity,
                "复制公网 URL", "Copy public URL", BRAND, dark);
        LinearLayout.LayoutParams copyPinggyParams = weighted();
        copyPinggyParams.leftMargin = dp(activity, 8);
        pinggyActions.addView(copyPinggy, copyPinggyParams);
        pinggyCard.addView(pinggyActions);

        final TextView stopPinggy = action(activity,
                "关闭临时网址", "Close temporary URL", 0xFFE05252, dark);
        pinggyCard.addView(stopPinggy, inset(activity, 0, 8));
        final TextView pinggyLogToggle = action(activity,
                "展开 Pinggy 日志", "Show Pinggy log", sub, dark);
        pinggyCard.addView(pinggyLogToggle, inset(activity, 0, 10));
        final TextView pinggyLogs = info(activity,
                initialPinggy.getString("recent_log", ""), sub, dark);
        pinggyLogs.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        pinggyLogs.setVisibility(View.GONE);
        pinggyCard.addView(pinggyLogs, inset(activity, 0, 12));

        LinearLayout tunnelCard = card(activity, cardColor);
        LinearLayout.LayoutParams tunnelCardParams = matchWrap();
        tunnelCardParams.topMargin = dp(activity, 14);
        content.addView(tunnelCard, tunnelCardParams);
        tunnelCard.addView(section(activity,
                "Cloudflare 自有域名", "Cloudflare custom domains", text));

        TextView tunnelIntro = body(activity,
                BuildInfo.SHI_V5_V241 && HostCompat.isV241()
                        ? "用于长期使用自己的域名。首次明确开启时会从 Cloudflare 官方固定版本下载并校验 cloudflared，之后在模块前台保活进程中运行；本机与局域网监听不会关闭。一个 Tunnel 可以配置多个域名。"
                        : "用于长期使用自己的域名。APP 内置 cloudflared，并在模块的前台保活进程中运行；"
                        + "本机与局域网监听不会关闭。一个 Tunnel 可以配置多个域名。",
                BuildInfo.SHI_V5_V241 && HostCompat.isV241()
                        ? "For persistent custom domains. On first explicit enable, the app downloads and verifies a pinned official Cloudflare build, then runs it in the module foreground process. Local and LAN endpoints remain active."
                        : "Use your own domains persistently. The bundled cloudflared connector runs in "
                        + "the module foreground process while local and LAN endpoints remain active. "
                        + "One tunnel can serve multiple domains.",
                sub);
        tunnelCard.addView(tunnelIntro, inset(activity, 0, 8));

        LinearLayout enableRow = new LinearLayout(activity);
        enableRow.setOrientation(LinearLayout.HORIZONTAL);
        enableRow.setGravity(Gravity.CENTER_VERTICAL);
        enableRow.setPadding(dp(activity, 16), dp(activity, 10),
                dp(activity, 12), dp(activity, 12));
        LinearLayout enableCopy = new LinearLayout(activity);
        enableCopy.setOrientation(LinearLayout.VERTICAL);
        enableCopy.addView(label(activity, "启用持久公网入口",
                "Enable persistent public endpoint", 15, text, true));
        enableCopy.addView(label(activity,
                "域名映射完成后再打开", "Turn on after hostname routing is configured",
                12, sub, false));
        enableRow.addView(enableCopy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch enabled = new HubInsetSwitch(activity);
        enabled.setChecked(initial.getBoolean("requested", false));
        enableRow.addView(enabled);
        tunnelCard.addView(enableRow);

        tunnelCard.addView(line(activity, divider));
        tunnelCard.addView(fieldTitle(activity,
                "Tunnel token", "Tunnel token", text));
        LinearLayout tokenRow = new LinearLayout(activity);
        tokenRow.setOrientation(LinearLayout.HORIZONTAL);
        tokenRow.setGravity(Gravity.CENTER_VERTICAL);
        tokenRow.setPadding(dp(activity, 16), 0, dp(activity, 16), dp(activity, 8));
        final EditText token = edit(activity, text, sub, dark);
        token.setHint(initial.getBoolean("token_configured", false)
                ? t(activity, "已安全保存；留空不会覆盖", "Stored securely; leave blank to keep it")
                : t(activity, "粘贴 cloudflared 连接器 token", "Paste the cloudflared connector token"));
        token.setSingleLine(true);
        token.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setTransformationMethod(PasswordTransformationMethod.getInstance());
        tokenRow.addView(token, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView reveal = action(activity, "显示", "Show", BRAND, dark);
        LinearLayout.LayoutParams revealParams = new LinearLayout.LayoutParams(
                dp(activity, 68), ViewGroup.LayoutParams.WRAP_CONTENT);
        revealParams.leftMargin = dp(activity, 8);
        tokenRow.addView(reveal, revealParams);
        tunnelCard.addView(tokenRow);

        tunnelCard.addView(fieldTitle(activity,
                "公网域名（每行一个）", "Public domains (one per line)", text));
        final EditText domains = edit(activity, text, sub, dark);
        domains.setHint("api.example.com");
        domains.setSingleLine(false);
        domains.setMinLines(2);
        domains.setMaxLines(6);
        domains.setGravity(Gravity.TOP);
        domains.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        domains.setText(initial.getString("domains", ""));
        tunnelCard.addView(domains, inset(activity, 0, 8));

        tunnelCard.addView(fieldTitle(activity,
                "固定本地监听端口", "Fixed local listener port", text));
        final EditText port = edit(activity, text, sub, dark);
        port.setSingleLine(true);
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        port.setText(String.valueOf(Main.localApiPreferredPort(activity)));
        tunnelCard.addView(port, inset(activity, 0, 8));

        LinearLayout transportRow = new LinearLayout(activity);
        transportRow.setOrientation(LinearLayout.HORIZONTAL);
        transportRow.setGravity(Gravity.CENTER_VERTICAL);
        transportRow.setPadding(dp(activity, 16), dp(activity, 8),
                dp(activity, 16), dp(activity, 12));
        transportRow.addView(label(activity, "边缘传输", "Edge transport",
                14, text, true), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final String[] transport = new String[]{
                normalizeTransport(initial.getString("transport",
                        PublicTunnelManager.TRANSPORT_AUTO))
        };
        final TextView transportValue = action(activity,
                transportName(activity, transport[0]),
                transportName(activity, transport[0]), BRAND, dark);
        transportRow.addView(transportValue);
        tunnelCard.addView(transportRow);

        final TextView status = info(activity, statusText(activity, initial), text, dark);
        tunnelCard.addView(status, inset(activity, 0, 10));

        LinearLayout primaryActions = new LinearLayout(activity);
        primaryActions.setOrientation(LinearLayout.HORIZONTAL);
        primaryActions.setPadding(dp(activity, 16), dp(activity, 4),
                dp(activity, 16), dp(activity, 8));
        final TextView save = action(activity, "保存配置", "Save", BRAND, dark);
        primaryActions.addView(save, weighted());
        final TextView copyPublic = action(activity,
                "复制公网 URL", "Copy public URL", BRAND, dark);
        LinearLayout.LayoutParams copyPublicParams = weighted();
        copyPublicParams.leftMargin = dp(activity, 8);
        primaryActions.addView(copyPublic, copyPublicParams);
        tunnelCard.addView(primaryActions);

        LinearLayout setupActions = new LinearLayout(activity);
        setupActions.setOrientation(LinearLayout.HORIZONTAL);
        setupActions.setPadding(dp(activity, 16), 0,
                dp(activity, 16), dp(activity, 12));
        final TextView copyOrigin = action(activity,
                "复制源站地址", "Copy origin", 0xFFE07A22, dark);
        setupActions.addView(copyOrigin, weighted());
        final TextView openDashboard = action(activity,
                "打开 Cloudflare", "Open Cloudflare", 0xFFE07A22, dark);
        LinearLayout.LayoutParams dashboardParams = weighted();
        dashboardParams.leftMargin = dp(activity, 8);
        setupActions.addView(openDashboard, dashboardParams);
        tunnelCard.addView(setupActions);

        TextView setupHelp = body(activity,
                "Cloudflare 端只需配置一次：Zero Trust → Networks / Connectors → 选择该 Tunnel → "
                        + "Published application routes。把上面每个域名的 Service 都设为本页显示的"
                        + "源站地址，然后从连接器安装命令中复制 token 粘贴到这里。不要启用会要求"
                        + "浏览器登录的 Access 验证，否则普通 API 客户端无法调用。",
                "One-time Cloudflare setup: Zero Trust → Networks / Connectors → select the "
                        + "tunnel → Published application routes. Point every hostname above to "
                        + "the origin shown here, then paste the token from the connector install "
                        + "command. Do not require interactive Access login for API clients.",
                sub);
        tunnelCard.addView(setupHelp, inset(activity, 0, 14));

        final TextView logToggle = action(activity,
                "展开连接日志", "Show connector log", sub, dark);
        LinearLayout.LayoutParams logToggleParams = inset(activity, 0, 10);
        tunnelCard.addView(logToggle, logToggleParams);
        final TextView logs = info(activity, initial.getString("recent_log", ""), sub, dark);
        logs.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        logs.setVisibility(View.GONE);
        tunnelCard.addView(logs, inset(activity, 0, 12));

        LinearLayout directCard = card(activity, cardColor);
        LinearLayout.LayoutParams directCardParams = matchWrap();
        directCardParams.topMargin = dp(activity, 14);
        content.addView(directCard, directCardParams);
        directCard.addView(section(activity,
                "公网 IP / 路由器直连", "Public IP / router forwarding", text));
        directCard.addView(body(activity,
                "本地 API 已监听 0.0.0.0，所以设备若确实拥有公网 IP，可以把路由器公网端口转发到"
                        + "这台设备的固定端口，再让域名 A/AAAA 记录指向该公网 IP。APP 不能把任意 "
                        + "IP 绑定到手机，也不能绕过运营商 CGNAT；移动网络通常无法使用这种方式。"
                        + "直接使用 HTTP 会明文传输 API Key，公网使用时应另配 HTTPS 反向代理。",
                "The local API already listens on 0.0.0.0. If the device is behind a real public "
                        + "IP, forward a router WAN port to this fixed port and point an A/AAAA "
                        + "record at that IP. An app cannot assign an arbitrary IP or bypass carrier "
                        + "CGNAT; cellular networks usually cannot use this mode. Plain HTTP exposes "
                        + "the API key, so add an HTTPS reverse proxy for Internet use.",
                sub), inset(activity, 0, 8));
        directCard.addView(fieldTitle(activity,
                "外部根地址（仅保存、复制和校验）",
                "External root URL (stored for copy/validation)", text));
        final EditText directRoot = edit(activity, text, sub, dark);
        directRoot.setSingleLine(true);
        directRoot.setHint("http://203.0.113.10:8765");
        directRoot.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_URI);
        directRoot.setText(initial.getString("direct_root", ""));
        directCard.addView(directRoot, inset(activity, 0, 8));
        final TextView copyDirect = action(activity,
                "复制直连 API URL", "Copy direct API URL", BRAND, dark);
        directCard.addView(copyDirect, inset(activity, 0, 14));

        TextView security = info(activity,
                t(activity,
                        "公网暴露后，API Key 就等同于当前 DeepSeek 账号的调用权限。请使用随机长 Key，"
                                + "不要把 Key 写进公开脚本或截图；Cloudflare 提供 TLS，但不会替你"
                                + "限制模型调用次数。",
                        "Once public, the API key grants use of the signed-in DeepSeek account. "
                                + "Use a long random key and never publish it. Cloudflare supplies "
                                + "TLS but does not enforce model-call quotas for this gateway."),
                text, dark);
        LinearLayout.LayoutParams securityParams = matchWrap();
        securityParams.topMargin = dp(activity, 14);
        content.addView(security, securityParams);

        final TextView feedback = label(activity, "", "", 12, BRAND, true);
        feedback.setGravity(Gravity.CENTER);
        feedback.setPadding(dp(activity, 12), dp(activity, 10),
                dp(activity, 12), dp(activity, 8));
        content.addView(feedback, matchWrap());

        final boolean[] syncingSwitch = new boolean[]{false};
        final boolean[] tokenVisible = new boolean[]{false};
        final boolean[] logsVisible = new boolean[]{false};
        final boolean[] pinggyLogsVisible = new boolean[]{false};
        final boolean[] cloudflaredDownloadSeen = new boolean[]{
                initial.getBoolean("binary_downloading", false)
        };
        final boolean[] cloudflaredReadyAnnounced = new boolean[]{
                initial.getBoolean("binary_available", false)
        };
        final Dialog[] cloudflaredProgressDialog = new Dialog[]{null};
        final ProgressBar[] cloudflaredProgressBar = new ProgressBar[]{null};
        final TextView[] cloudflaredProgressText = new TextView[]{null};
        final boolean[] cloudflaredBackgroundDownload = new boolean[]{false};
        final Bundle[] latest = new Bundle[]{initial};
        final Bundle[] latestPinggy = new Bundle[]{initialPinggy};
        final String[] announcedPinggyRoot = new String[]{
                initialPinggy.getString("primary_root", "")
        };
        final String[] lastPinggyState = new String[]{
                initialPinggy.getString("state", "stopped")
        };
        final boolean[] pinggyDisconnectNotified = new boolean[]{false};

        startPinggy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                String portError = savePreferredPort(activity, port);
                if (portError != null) {
                    feedback.setText(portError);
                    return;
                }
                if (!Main.isLocalApiEnabled() && !Main.setLocalApiEnabled(true)) {
                    feedback.setText(t(activity,
                            "请先通过本地 API 的后台运行校验",
                            "Approve background operation for the local API first"));
                    return;
                }
                announcedPinggyRoot[0] = "";
                Bundle result = Main.setLocalApiPinggyTunnelEnabled(activity, true);
                latestPinggy[0] = result;
                feedback.setText(resultMessage(activity, result,
                        "正在连接 Pinggy；出现密码请求时会自动提交空密码…",
                        "Connecting to Pinggy; an empty password is submitted automatically…"));
                pinggyStatus.setText(pinggyStatusText(activity, result));
            }
        });

        copyPinggy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                Bundle current = Main.localApiPinggyTunnelStatus(activity);
                latestPinggy[0] = current;
                String endpoint = Main.localApiEndpointForPublicRoot(
                        current.getString("primary_root", ""));
                if (endpoint.length() == 0) {
                    feedback.setText(t(activity,
                            "临时网址尚未连接", "The temporary URL is not connected yet"));
                    return;
                }
                copy(activity, "Pinggy API URL", endpoint);
                feedback.setText(t(activity,
                        "Pinggy 公网 API URL 已复制",
                        "Pinggy public API URL copied"));
            }
        });

        stopPinggy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                Bundle result = Main.setLocalApiPinggyTunnelEnabled(activity, false);
                latestPinggy[0] = result;
                announcedPinggyRoot[0] = "";
                feedback.setText(resultMessage(activity, result,
                        "Pinggy 临时网址已关闭", "Pinggy temporary URL closed"));
                pinggyStatus.setText(pinggyStatusText(activity, result));
            }
        });

        pinggyLogToggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                pinggyLogsVisible[0] = !pinggyLogsVisible[0];
                pinggyLogs.setVisibility(pinggyLogsVisible[0] ? View.VISIBLE : View.GONE);
                pinggyLogToggle.setText(pinggyLogsVisible[0]
                        ? t(activity, "收起 Pinggy 日志", "Hide Pinggy log")
                        : t(activity, "展开 Pinggy 日志", "Show Pinggy log"));
            }
        });

        reveal.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                tokenVisible[0] = !tokenVisible[0];
                int selection = token.getSelectionStart();
                token.setTransformationMethod(tokenVisible[0]
                        ? null : PasswordTransformationMethod.getInstance());
                if (selection >= 0) token.setSelection(Math.min(selection, token.length()));
                reveal.setText(tokenVisible[0]
                        ? t(activity, "隐藏", "Hide") : t(activity, "显示", "Show"));
            }
        });

        transportValue.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (PublicTunnelManager.TRANSPORT_AUTO.equals(transport[0])) {
                    transport[0] = PublicTunnelManager.TRANSPORT_HTTP2;
                } else if (PublicTunnelManager.TRANSPORT_HTTP2.equals(transport[0])) {
                    transport[0] = PublicTunnelManager.TRANSPORT_QUIC;
                } else {
                    transport[0] = PublicTunnelManager.TRANSPORT_AUTO;
                }
                transportValue.setText(transportName(activity, transport[0]));
            }
        });

        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                String error = saveConfiguration(activity, token, domains, port,
                        transport[0], directRoot, latest);
                feedback.setText(error);
                if (latest[0].getBoolean("accepted", false)) {
                    token.setText("");
                    status.setText(statusText(activity, latest[0]));
                }
            }
        });

        enabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                if (syncingSwitch[0]) return;
                if (!checked) {
                    Bundle result = Main.setLocalApiPublicTunnelEnabled(activity, false);
                    latest[0] = result;
                    feedback.setText(resultMessage(activity, result,
                            "公网入口已停止", "Public endpoint stopped"));
                    status.setText(statusText(activity, result));
                    return;
                }
                if (!Main.isLocalApiEnabled() && !Main.setLocalApiEnabled(true)) {
                    syncingSwitch[0] = true;
                    enabled.setChecked(false);
                    syncingSwitch[0] = false;
                    feedback.setText(t(activity,
                            "请先通过本地 API 的后台运行校验",
                            "Approve background operation for the local API first"));
                    return;
                }
                String saved = saveConfiguration(activity, token, domains, port,
                        transport[0], directRoot, latest);
                if (!latest[0].getBoolean("accepted", false)) {
                    syncingSwitch[0] = true;
                    enabled.setChecked(false);
                    syncingSwitch[0] = false;
                    feedback.setText(saved);
                    return;
                }
                token.setText("");
                if (BuildInfo.SHI_V5_V241 && HostCompat.isV241()
                        && !latest[0].getBoolean("binary_available", false)) {
                    syncingSwitch[0] = true;
                    enabled.setChecked(false);
                    syncingSwitch[0] = false;
                    new AlertDialog.Builder(activity)
                            .setTitle(t(activity,
                                    "需要下载 Cloudflare 依赖",
                                    "Cloudflare dependency required"))
                            .setMessage(t(activity,
                                    "当前私有版本槽位中没有已校验的 cloudflared。是否从 Cloudflare 官方固定版本下载并完成 SHA-256、大小和 ELF 校验？下载期间本机与局域网本地 API 不受影响。",
                                    "No verified cloudflared exists in the private version slot. Download the pinned official Cloudflare build and verify its SHA-256, size, and ELF header? Local and LAN API access remains available during the download."))
                            .setPositiveButton(t(activity, "下载并开启", "Download and enable"),
                                    (ignored, which) -> {
                                        syncingSwitch[0] = true;
                                        enabled.setChecked(true);
                                        syncingSwitch[0] = false;
                                        Bundle result = Main.setLocalApiPublicTunnelEnabled(
                                                activity, true);
                                        Main.log("CF download enable accepted="
                                                + result.getBoolean("accepted", false)
                                                + " err=" + result.getString("error", ""));
                                        latest[0] = result;
                                        if (!result.getBoolean("accepted", false)) {
                                            syncingSwitch[0] = true;
                                            enabled.setChecked(false);
                                            syncingSwitch[0] = false;
                                        } else {
                                            cloudflaredDownloadSeen[0] = true;
                                            cloudflaredReadyAnnounced[0] = false;
                                            cloudflaredBackgroundDownload[0] = false;
                                            showCloudflaredProgressDialog(activity,
                                                    enabled, syncingSwitch, feedback,
                                                    cloudflaredProgressDialog,
                                                    cloudflaredProgressBar,
                                                    cloudflaredProgressText,
                                                    cloudflaredBackgroundDownload);
                                        }
                                        feedback.setText(resultMessage(activity, result,
                                                "正在下载并校验 cloudflared…",
                                                "Downloading and verifying cloudflared…"));
                                        status.setText(statusText(activity, result));
                                    })
                            .setNegativeButton(t(activity, "取消", "Cancel"), null)
                            .show();
                    feedback.setText(t(activity,
                            "等待确认下载 cloudflared；本地 API 仍可正常使用",
                            "Waiting for cloudflared download confirmation; the local API remains available"));
                    return;
                }
                Bundle result = Main.setLocalApiPublicTunnelEnabled(activity, true);
                latest[0] = result;
                if (!result.getBoolean("accepted", false)) {
                    syncingSwitch[0] = true;
                    enabled.setChecked(false);
                    syncingSwitch[0] = false;
                }
                feedback.setText(resultMessage(activity, result,
                        "正在启动 Cloudflare Tunnel…", "Starting Cloudflare Tunnel…"));
                status.setText(statusText(activity, result));
            }
        });

        copyPublic.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                Bundle current = Main.localApiPublicTunnelStatus(activity);
                latest[0] = current;
                String endpoint = Main.localApiEndpointForPublicRoot(
                        current.getString("primary_root", ""));
                if (endpoint.length() == 0) {
                    feedback.setText(t(activity,
                            "尚未保存公网域名", "No public domain has been saved"));
                    return;
                }
                copy(activity, "Public API URL", endpoint);
                feedback.setText(t(activity, "公网 API URL 已复制",
                        "Public API URL copied"));
            }
        });

        copyOrigin.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                Bundle current = Main.localApiPublicTunnelStatus(activity);
                String origin = current.getString("origin", Main.localApiRootEndpoint());
                copy(activity, "Cloudflare origin", origin);
                feedback.setText(t(activity,
                        "源站地址已复制；把它填入 Cloudflare 的 Service",
                        "Origin copied; use it as the Cloudflare Service"));
            }
        });

        openDashboard.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                try {
                    activity.startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://one.dash.cloudflare.com/")));
                } catch (Throwable t) {
                    feedback.setText(t(activity,
                            "无法打开浏览器", "Could not open a browser"));
                }
            }
        });

        copyDirect.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                String error = saveConfiguration(activity, token, domains, port,
                        transport[0], directRoot, latest);
                if (!latest[0].getBoolean("accepted", false)) {
                    feedback.setText(error);
                    return;
                }
                token.setText("");
                String endpoint = Main.localApiEndpointForPublicRoot(
                        latest[0].getString("direct_root", ""));
                if (endpoint.length() == 0) {
                    feedback.setText(t(activity,
                            "请先填写公网 IP 或外部地址",
                            "Enter a public IP or external URL first"));
                    return;
                }
                copy(activity, "Direct API URL", endpoint);
                feedback.setText(t(activity,
                        "直连 API URL 已复制", "Direct API URL copied"));
            }
        });

        logToggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                logsVisible[0] = !logsVisible[0];
                logs.setVisibility(logsVisible[0] ? View.VISIBLE : View.GONE);
                logToggle.setText(logsVisible[0]
                        ? t(activity, "收起连接日志", "Hide connector log")
                        : t(activity, "展开连接日志", "Show connector log"));
            }
        });

        final Runnable refresh = new Runnable() {
            @Override public void run() {
                if (!dialog.isShowing()) return;
                Bundle current = Main.localApiPublicTunnelStatus(activity);
                latest[0] = current;
                status.setText(statusText(activity, current));
                updateCloudflaredProgressDialog(activity, current, feedback,
                        cloudflaredProgressDialog, cloudflaredProgressBar,
                        cloudflaredProgressText);
                if (current.getBoolean("binary_downloading", false)) {
                    cloudflaredDownloadSeen[0] = true;
                    cloudflaredReadyAnnounced[0] = false;
                } else if (cloudflaredDownloadSeen[0]
                        && current.getBoolean("binary_available", false)
                        && !cloudflaredReadyAnnounced[0]) {
                    cloudflaredReadyAnnounced[0] = true;
                    new AlertDialog.Builder(activity)
                            .setTitle(t(activity,
                                    "Cloudflare 依赖下载完成",
                                    "Cloudflare dependency downloaded"))
                            .setMessage(t(activity,
                                    "cloudflared 已下载到私有版本槽位并通过 SHA-256、大小和 ELF 校验，Tunnel 会在本地 API 可用时自动启动。",
                                    "cloudflared was downloaded to the private version slot and passed SHA-256, size, and ELF verification. The tunnel starts automatically when the local API is available."))
                            .setPositiveButton(t(activity, "知道了", "OK"), null)
                            .show();
                }
                Bundle currentPinggy = Main.localApiPinggyTunnelStatus(activity);
                latestPinggy[0] = currentPinggy;
                pinggyStatus.setText(pinggyStatusText(activity, currentPinggy));
                if (pinggyLogsVisible[0]) {
                    String value = currentPinggy.getString("recent_log", "");
                    pinggyLogs.setText(value.length() == 0
                            ? t(activity, "暂无日志", "No log entries yet") : value);
                }
                String pinggyRoot = currentPinggy.getString("primary_root", "");
                String pinggyState = currentPinggy.getString("state", "");
                if ("connected".equals(pinggyState)
                        && pinggyRoot.length() > 0
                        && !pinggyRoot.equals(announcedPinggyRoot[0])) {
                    announcedPinggyRoot[0] = pinggyRoot;
                    pinggyDisconnectNotified[0] = false;
                    showPinggyReadyDialog(activity, pinggyRoot, feedback);
                }
                if (("error".equals(pinggyState) || "expired".equals(pinggyState))
                        && !pinggyDisconnectNotified[0]
                        && ("connected".equals(lastPinggyState[0])
                        || currentPinggy.getString("error", "").contains("已断开")
                        || "expired".equals(pinggyState))) {
                    pinggyDisconnectNotified[0] = true;
                    showPinggyDisconnectedDialog(activity, currentPinggy, feedback);
                }
                lastPinggyState[0] = pinggyState;
                if (logsVisible[0]) {
                    String value = current.getString("recent_log", "");
                    logs.setText(value.length() == 0
                            ? t(activity, "暂无日志", "No log entries yet") : value);
                }
                boolean requested = current.getBoolean("requested", false);
                if (enabled.isChecked() != requested) {
                    syncingSwitch[0] = true;
                    enabled.setChecked(requested);
                    syncingSwitch[0] = false;
                }
                status.postDelayed(this, 1000L);
            }
        };

        final Runnable close = new Runnable() {
            @Override public void run() {
                int distance = activity.getResources().getDisplayMetrics().widthPixels;
                root.animate().translationX(distance).setDuration(190L)
                        .withEndAction(new Runnable() {
                            @Override public void run() { dialog.dismiss(); }
                        }).start();
            }
        };
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { close.run(); }
        });
        dialog.setOnKeyListener(new Dialog.OnKeyListener() {
            @Override public boolean onKey(android.content.DialogInterface ignored,
                                           int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK
                        && event.getAction() == KeyEvent.ACTION_UP) {
                    close.run();
                    return true;
                }
                return false;
            }
        });

        DeePXUi.addBuildFooter(activity, content, sub);
        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(bg));
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.show();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
        int distance = activity.getResources().getDisplayMetrics().widthPixels;
        root.setTranslationX(distance);
        root.animate().translationX(0f).setDuration(220L).start();
        status.post(refresh);
    }

    private static String savePreferredPort(Activity activity, EditText port) {
        String value = port.getText().toString().trim();
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (Throwable t) {
            return t(activity, "监听端口必须是数字", "Listener port must be numeric");
        }
        if (parsed < 1024 || parsed > 65535) {
            return t(activity, "监听端口必须在 1024–65535 之间",
                    "Listener port must be between 1024 and 65535");
        }
        if (parsed == Main.localApiPreferredPort(activity)) return null;
        String result = Main.setLocalApiPreferredPort(activity, value);
        return Main.localApiPreferredPort(activity) == parsed ? null : result;
    }

    private static void showPinggyReadyDialog(final Activity activity, String root,
                                              final TextView feedback) {
        final String endpoint = Main.localApiEndpointForPublicRoot(root);
        if (endpoint.length() == 0 || activity.isFinishing()) return;
        new AlertDialog.Builder(activity)
                .setTitle(t(activity, "临时公网网址已就绪",
                        "Temporary public URL is ready"))
                .setMessage(t(activity,
                        "有效期约 60 分钟，倒计时以高级设置页显示为准。本机与局域网地址仍可同时使用。\n\n",
                        "The URL is valid for about 60 minutes; the advanced page shows the "
                                + "live countdown. Local and LAN addresses remain available.\n\n")
                        + endpoint)
                .setPositiveButton(t(activity, "一键复制", "Copy"), (dialog, which) -> {
                    copy(activity, "Pinggy API URL", endpoint);
                    feedback.setText(t(activity,
                            "Pinggy 公网 API URL 已复制",
                            "Pinggy public API URL copied"));
                })
                .setNegativeButton(t(activity, "关闭", "Close"), null)
                .show();
    }

    private static void showPinggyDisconnectedDialog(final Activity activity, Bundle status,
                                                     final TextView feedback) {
        if (activity.isFinishing()) return;
        String reason = status == null ? "" : status.getString("error", "").trim();
        if (reason.length() == 0) {
            reason = t(activity, "远端连接已关闭", "The remote connection was closed");
        }
        final String message = t(activity,
                "原临时公网网址已经失效，系统不会自动申请新网址。\n\n失败原因：",
                "The previous temporary public URL is no longer valid. A replacement URL will "
                        + "not be requested automatically.\n\nReason: ") + reason;
        new AlertDialog.Builder(activity)
                .setTitle(t(activity, "临时公网网址已断连",
                        "Temporary public URL disconnected"))
                .setMessage(message)
                .setPositiveButton(t(activity, "知道了", "OK"), (dialog, which) ->
                        feedback.setText(t(activity,
                                "如需继续使用，请手动重新申请网址",
                                "Request a new URL manually to continue")))
                .show();
    }

    private static String pinggyStatusText(Context context, Bundle status) {
        if (status == null) return t(context,
                "无法读取 Pinggy 状态", "Could not read Pinggy status");
        if (!status.getBoolean("accepted", false)) {
            return t(context, "模块连接：不可用\n", "Module connector: unavailable\n")
                    + status.getString("error", "");
        }
        String rawState = status.getString("state", "stopped");
        String state;
        if ("connected".equals(rawState)) state = t(context, "已连接", "Connected");
        else if ("connecting".equals(rawState)) state = t(context,
                "正在连接", "Connecting");
        else if ("waiting_local_api".equals(rawState)) state = t(context,
                "等待本地 API", "Waiting for local API");
        else if ("expired".equals(rawState)) state = t(context, "已到期", "Expired");
        else if ("error".equals(rawState)) state = t(context, "已断开", "Disconnected");
        else state = t(context, "未开启", "Not running");

        String root = status.getString("primary_root", "");
        String endpoint = Main.localApiEndpointForPublicRoot(root);
        StringBuilder value = new StringBuilder();
        value.append(t(context, "连接状态：", "Connector: ")).append(state)
                .append('\n').append(t(context, "SSH 客户端：", "SSH client: "))
                .append(status.getString("ssh_client", "JSch"))
                .append('\n').append(t(context, "认证：", "Authentication: "))
                .append(t(context, "自动空密码回车", "automatic empty-password Enter"))
                .append('\n').append(t(context, "源站：", "Origin: "))
                .append(status.getString("origin", "http://127.0.0.1:8765"))
                .append('\n').append(t(context, "公网 API：", "Public API: "))
                .append(endpoint.length() == 0
                        ? t(context, "尚未分配", "not allocated") : endpoint);
        long remaining = status.getLong("remaining_ms", 0L);
        if (remaining > 0L) {
            value.append('\n').append(t(context, "剩余时间：", "Time remaining: "))
                    .append(formatDuration(remaining));
        }
        String fingerprint = status.getString("host_fingerprint", "");
        if (fingerprint.length() > 0) {
            value.append('\n').append(t(context, "主机指纹：", "Host fingerprint: "))
                    .append(fingerprint);
        }
        String error = status.getString("error", "");
        if (error.length() > 0) {
            value.append('\n').append(t(context, "提示：", "Message: "))
                    .append(UiLanguage.dynamic(context, error));
        }
        return value.toString();
    }

    private static String formatDuration(long millis) {
        long seconds = Math.max(0L, (millis + 999L) / 1000L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainder = seconds % 60L;
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, remainder);
    }

    private static String formatDownloadProgress(Bundle status) {
        long received = Math.max(0L, status.getLong("download_received", 0L));
        long total = Math.max(0L, status.getLong("download_total", 0L));
        double receivedMiB = received / (1024.0d * 1024.0d);
        if (total <= 0L) return String.format(Locale.US, "%.1f MiB", receivedMiB);
        double totalMiB = total / (1024.0d * 1024.0d);
        int percent = (int) Math.min(100L, received * 100L / total);
        return String.format(Locale.US, "%.1f / %.1f MiB (%d%%)",
                receivedMiB, totalMiB, percent);
    }

    private static void showCloudflaredProgressDialog(
            final Activity activity, final Switch enabled, final boolean[] syncingSwitch,
            final TextView feedback, final Dialog[] progressDialog,
            final ProgressBar[] progressBar, final TextView[] progressText,
            final boolean[] backgroundDownload) {
        if (activity == null || activity.isFinishing()) {
            Main.log("CF progress dialog: activity null or finishing");
            return;
        }
        Main.log("CF progress dialog: showing");
        final boolean dark = isDark(activity);
        final int subColor = dark ? 0xFFAAAAAF : 0xFF70757D;

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 24), dp(activity, 20),
                dp(activity, 24), dp(activity, 8));

        ProgressBar bar = new ProgressBar(activity, null,
                android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(0);
        bar.setIndeterminate(false);
        bar.setMinimumHeight(dp(activity, 14));
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            bar.setProgressTintList(android.content.res.ColorStateList.valueOf(BRAND));
            bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    dark ? 0xFF3A3A3D : 0xFFE2E3E8));
        }
        progressBar[0] = bar;
        content.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 14)));

        TextView text = new TextView(activity);
        text.setTextColor(subColor);
        text.setTextSize(13);
        text.setGravity(Gravity.CENTER);
        text.setText(t(activity,
                "正在下载并校验 cloudflared… 0%",
                "Downloading and verifying cloudflared… 0%"));
        progressText[0] = text;
        LinearLayout.LayoutParams textParams = matchWrap();
        textParams.topMargin = dp(activity, 14);
        content.addView(text, textParams);

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(t(activity,
                        "正在下载 Cloudflare 依赖",
                        "Downloading Cloudflare dependency"))
                .setView(content)
                .setCancelable(false)
                .setPositiveButton(t(activity, "后台下载", "Download in background"),
                        (d, which) -> {
                            backgroundDownload[0] = true;
                            progressDialog[0] = null;
                            progressBar[0] = null;
                            progressText[0] = null;
                            Main.log("CF progress dialog: background download chosen");
                            feedback.setText(t(activity,
                                    "cloudflared 正在后台下载，完成后会提示",
                                    "cloudflared is downloading in the background; "
                                            + "you will be notified when done"));
                        })
                .setNegativeButton(t(activity, "取消", "Cancel"),
                        (d, which) -> {
                            backgroundDownload[0] = false;
                            progressDialog[0] = null;
                            progressBar[0] = null;
                            progressText[0] = null;
                            Main.log("CF progress dialog: cancel chosen");
                            Main.cancelLocalApiPublicTunnelProvisioning(activity);
                            syncingSwitch[0] = true;
                            enabled.setChecked(false);
                            syncingSwitch[0] = false;
                            feedback.setText(t(activity,
                                    "已取消 cloudflared 下载",
                                    "cloudflared download cancelled"));
                        })
                .create();
        dialog.show();
        progressDialog[0] = dialog;
    }

    private static void updateCloudflaredProgressDialog(Activity activity, Bundle current,
                                                        TextView feedback,
                                                        Dialog[] progressDialog,
                                                        ProgressBar[] progressBar,
                                                        TextView[] progressText) {
        if (progressDialog[0] == null || !progressDialog[0].isShowing()) return;
        if (current.getBoolean("binary_downloading", false)) {
            long received = Math.max(0L, current.getLong("download_received", 0L));
            long total = Math.max(0L, current.getLong("download_total", 0L));
            int percent = total > 0L ? (int) Math.min(100L, received * 100L / total) : 0;
            if (progressBar[0] != null) progressBar[0].setProgress(percent);
            if (progressText[0] != null) {
                progressText[0].setText(formatDownloadProgress(current));
            }
        } else {
            Main.log("CF progress dialog: download ended available="
                    + current.getBoolean("binary_available", false));
            Dialog dialog = progressDialog[0];
            progressDialog[0] = null;
            progressBar[0] = null;
            progressText[0] = null;
            try { dialog.dismiss(); } catch (Throwable ignored) {}
            if (!current.getBoolean("binary_available", false)) {
                feedback.setText(t(activity,
                        "cloudflared 下载未完成，请重新开启",
                        "cloudflared download was not completed; please retry"));
            }
        }
    }

    private static String saveConfiguration(Activity activity, EditText token,
                                            EditText domains, EditText port,
                                            String transport, EditText directRoot,
                                            Bundle[] latest) {
        String portText = port.getText().toString().trim();
        int parsed;
        try {
            parsed = Integer.parseInt(portText);
        } catch (Throwable t) {
            Bundle failure = new Bundle();
            failure.putBoolean("accepted", false);
            failure.putString("error", t(activity,
                    "监听端口必须是数字", "Listener port must be numeric"));
            latest[0] = failure;
            return failure.getString("error");
        }
        if (parsed != Main.localApiPreferredPort(activity)) {
            String portResult = Main.setLocalApiPreferredPort(activity, portText);
            if (parsed < 1024 || parsed > 65535) {
                Bundle failure = new Bundle();
                failure.putBoolean("accepted", false);
                failure.putString("error", portResult);
                latest[0] = failure;
                return portResult;
            }
        }
        Bundle result = Main.configureLocalApiPublicTunnel(activity,
                token.getText().toString(), domains.getText().toString(),
                transport, directRoot.getText().toString());
        latest[0] = result;
        return resultMessage(activity, result,
                "配置已安全保存", "Configuration saved securely");
    }

    private static String statusText(Context context, Bundle status) {
        if (status == null) return t(context,
                "无法读取公网连接状态", "Could not read public connector status");
        if (!status.getBoolean("accepted", false)) {
            return t(context, "模块连接：不可用\n", "Module connector: unavailable\n")
                    + status.getString("error", "");
        }
        String rawState = status.getString("state", "stopped");
        String state;
        if ("connected".equals(rawState)) state = t(context, "已连接", "Connected");
        else if ("downloading".equals(rawState)) state = t(context,
                "正在下载并校验依赖", "Downloading and verifying dependency");
        else if ("connecting".equals(rawState)) state = t(context, "正在连接", "Connecting");
        else if ("retry_wait".equals(rawState)) state = t(context,
                "等待重试", "Waiting to retry");
        else if ("waiting_local_api".equals(rawState)) state = t(context,
                "等待本地 API", "Waiting for local API");
        else if ("error".equals(rawState)) state = t(context, "错误", "Error");
        else state = t(context, "已停止", "Stopped");
        String root = status.getString("primary_root", "");
        String endpoint = Main.localApiEndpointForPublicRoot(root);
        String error = status.getString("error", "");
        StringBuilder value = new StringBuilder();
        value.append(t(context, "连接状态：", "Connector: ")).append(state)
                .append('\n').append(t(context, "cloudflared：", "cloudflared: "))
                .append(status.getBoolean("binary_available", false)
                        ? status.getString("cloudflared_version", "")
                        : status.getBoolean("binary_downloading", false)
                        ? formatDownloadProgress(status)
                        : t(context, "尚未下载", "not downloaded"))
                .append('\n').append(t(context, "源站：", "Origin: "))
                .append(status.getString("origin", "http://127.0.0.1:8765"))
                .append('\n').append(t(context, "公网 API：", "Public API: "))
                .append(endpoint.length() == 0
                        ? t(context, "尚未配置", "not configured") : endpoint)
                .append('\n').append(t(context, "凭据：", "Credential: "))
                .append(status.getBoolean("token_configured", false)
                        ? t(context, "已用 Android Keystore 加密保存",
                                "encrypted with Android Keystore")
                        : t(context, "尚未保存", "not stored"));
        long retry = status.getLong("retry_in_ms", 0L);
        if (retry > 0L) {
            value.append('\n').append(t(context, "重试倒计时：", "Retry in: "))
                    .append((retry + 999L) / 1000L).append('s');
        }
        if (error != null && error.length() > 0) {
            value.append('\n').append(t(context, "诊断：", "Diagnostic: "))
                    .append(UiLanguage.dynamic(context, error));
        }
        value.append('\n').append(t(context, "日志：", "Log: "))
                .append(status.getString("log_file", ""));
        return value.toString();
    }

    private static String resultMessage(Context context, Bundle result,
                                        String successZh, String successEn) {
        if (result != null && result.getBoolean("accepted", false)) {
            return t(context, successZh, successEn);
        }
        String error = result == null ? "" : result.getString("error", "");
        return error.length() == 0
                ? t(context, "操作失败", "Operation failed")
                : UiLanguage.dynamic(context, error);
    }

    private static String normalizeTransport(String value) {
        if (PublicTunnelManager.TRANSPORT_HTTP2.equals(value)) {
            return PublicTunnelManager.TRANSPORT_HTTP2;
        }
        if (PublicTunnelManager.TRANSPORT_QUIC.equals(value)) {
            return PublicTunnelManager.TRANSPORT_QUIC;
        }
        return PublicTunnelManager.TRANSPORT_AUTO;
    }

    private static String transportName(Context context, String value) {
        if (PublicTunnelManager.TRANSPORT_HTTP2.equals(value)) return "HTTP/2  \u203A";
        if (PublicTunnelManager.TRANSPORT_QUIC.equals(value)) return "QUIC  \u203A";
        return t(context, "自动（推荐）  \u203A", "Auto (recommended)  \u203A");
    }

    private static void copy(Context context, String label, String value) {
        ClipboardManager clipboard = (ClipboardManager)
                context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
    }

    private static LinearLayout settingTitle(Activity activity, String zh, String en,
                                             int color, View.OnClickListener listener) {
        LinearLayout line = new LinearLayout(activity);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.addView(label(activity, zh, en, 15, color, true),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
        View gear = DeePXUi.plainGearControl(activity, color,
                t(activity, "设置", "Settings"), listener);
        LinearLayout.LayoutParams gearParams = new LinearLayout.LayoutParams(
                dp(activity, 36), dp(activity, 36));
        gearParams.leftMargin = dp(activity, 2);
        line.addView(gear, gearParams);
        return line;
    }

    private static JSONArray customModels() {
        try { return new JSONArray(Main.localApiModelCatalogJson()); }
        catch (Throwable ignored) { return new JSONArray(); }
    }

    private static String nativeModelLabel(Context context, String value) {
        if ("expert".equals(value)) return t(context, "专家模式", "Expert");
        if ("vision".equals(value)) return t(context, "识图模式", "Vision");
        return t(context, "快速模式", "Fast");
    }

    private static void showCustomModelSettings(final Activity activity,
                                                final Runnable outerRefresh) {
        showCustomModelSettings(activity, outerRefresh, new HashSet<String>());
    }

    private static void showCustomModelSettings(final Activity activity,
                                                final Runnable outerRefresh,
                                                final Set<String> selected) {
        final boolean dark = isDark(activity);
        final int panelColor = dark ? 0xFF29292C : 0xFFFFFFFF;
        final int textColor = dark ? 0xFFF0F0F2 : 0xFF1B1B1D;
        final int subColor = dark ? 0xFFAAAAB0 : 0xFF747983;
        final int dividerColor = dark ? 0xFF3B3B40 : 0xFFECEEF2;
        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        FrameLayout backdrop = new FrameLayout(activity);
        backdrop.setBackgroundColor(0x52000000);
        backdrop.setOnClickListener(view -> dialog.dismiss());
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 16), dp(activity, 10));
        panel.setClickable(true);
        GradientDrawable background = new GradientDrawable();
        background.setColor(panelColor);
        background.setCornerRadius(dp(activity, 16));
        panel.setBackground(background);
        if (android.os.Build.VERSION.SDK_INT >= 21) panel.setElevation(dp(activity, 12));

        LinearLayout heading = new LinearLayout(activity);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(label(activity, "自定义模型", "Custom models", 17,
                textColor, true), new LinearLayout.LayoutParams(0, dp(activity, 44), 1f));
        final boolean selecting = selected != null && !selected.isEmpty();
        TextView add = label(activity,
                selecting ? "删除 " + selected.size() : "＋",
                selecting ? "Delete " + selected.size() : "+",
                selecting ? 14 : 24, selecting ? 0xFFE05252 : BRAND, selecting);
        add.setGravity(Gravity.CENTER);
        heading.addView(add, new LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 44)));
        panel.addView(heading);

        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        final JSONArray models = customModels();
        for (int i = 0; i < models.length(); i++) {
            final int index = i;
            final JSONObject item = models.optJSONObject(i);
            if (item == null) continue;
            if (rows.getChildCount() > 0) rows.addView(line(activity, dividerColor));
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(activity, 4), 0, 0, 0);
            final String modelId = item.optString("id", "");
            final boolean isDefault = "deepseek-flash".equalsIgnoreCase(modelId);
            if (selecting) {
                if (!isDefault) {
                    RouteCheckView check = new RouteCheckView(activity, selected.contains(modelId));
                    row.addView(check, new LinearLayout.LayoutParams(
                            dp(activity, 32), dp(activity, 56)));
                } else {
                    View spacer = new View(activity);
                    row.addView(spacer, new LinearLayout.LayoutParams(
                            dp(activity, 32), dp(activity, 56)));
                }
            }
            LinearLayout copy = new LinearLayout(activity);
            copy.setOrientation(LinearLayout.VERTICAL);
            LinearLayout idRow = new LinearLayout(activity);
            idRow.setOrientation(LinearLayout.HORIZONTAL);
            idRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView id = label(activity, item.optString("id", ""),
                    item.optString("id", ""), 14, textColor, true);
            id.setSingleLine(true);
            id.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            idRow.addView(id);
            if (isDefault) {
                TextView tag = label(activity, "  " + t(activity, "默认", "Default"),
                        "  " + t(activity, "默认", "Default"), 11, BRAND, true);
                idRow.addView(tag);
            }
            copy.addView(idRow);
            copy.addView(label(activity,
                    "映射到 " + nativeModelLabel(activity, item.optString("native_model")),
                    "Maps to " + nativeModelLabel(activity, item.optString("native_model")),
                    12, subColor, false));
            row.addView(copy, new LinearLayout.LayoutParams(0, dp(activity, 56), 1f));
            if (!selecting) {
                if (!isDefault) {
                    TextView remove = label(activity, "−", "−", 22, subColor, false);
                    remove.setGravity(Gravity.CENTER);
                    remove.setOnClickListener(view -> {
                        JSONArray updated = customModels();
                        JSONArray kept = new JSONArray();
                        for (int j = 0; j < updated.length(); j++) {
                            if (j != index) kept.put(updated.opt(j));
                        }
                        Main.setLocalApiModelCatalogJson(kept.toString());
                        Set<String> reasoning = new HashSet<String>(
                                Main.localApiForceReasoningModels());
                        reasoning.remove(modelId.toLowerCase(Locale.US));
                        Main.setLocalApiForceReasoningModels(reasoning);
                        if (outerRefresh != null) outerRefresh.run();
                        dialog.dismiss();
                        showCustomModelSettings(activity, outerRefresh);
                    });
                    row.addView(remove, new LinearLayout.LayoutParams(
                            dp(activity, 44), dp(activity, 56)));
                } else {
                    TextView defBadge = label(activity, t(activity, "默认", "Default"),
                            t(activity, "默认", "Default"), 12, subColor, false);
                    defBadge.setGravity(Gravity.CENTER);
                    row.addView(defBadge, new LinearLayout.LayoutParams(
                            dp(activity, 44), dp(activity, 56)));
                }
            }
            row.setOnClickListener(view -> {
                if (!selecting) {
                    showCustomModelEditor(activity, index, item, dialog, outerRefresh);
                    return;
                }
                if (isDefault) return;
                Set<String> next = new HashSet<String>(selected);
                if (next.contains(modelId)) next.remove(modelId); else next.add(modelId);
                dialog.dismiss();
                showCustomModelSettings(activity, outerRefresh, next);
            });
            row.setOnLongClickListener(view -> {
                if (isDefault) return true;
                Set<String> next = new HashSet<String>(selected);
                next.add(modelId);
                dialog.dismiss();
                showCustomModelSettings(activity, outerRefresh, next);
                return true;
            });
            rows.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 56)));
        }
        scroll.addView(rows);
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        TextView done = label(activity, "完成", "Done", 14, BRAND, true);
        done.setGravity(Gravity.CENTER);
        done.setOnClickListener(view -> dialog.dismiss());
        panel.addView(done, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 46)));
        add.setOnClickListener(view -> {
            if (!selecting) {
                showCustomModelEditor(activity, -1, null, dialog, outerRefresh);
                return;
            }
            JSONArray updated = customModels();
            JSONArray kept = new JSONArray();
            for (int i = 0; i < updated.length(); i++) {
                JSONObject item = updated.optJSONObject(i);
                if (item == null || !selected.contains(item.optString("id", ""))) {
                    kept.put(updated.opt(i));
                }
            }
            Main.setLocalApiModelCatalogJson(kept.toString());
            Set<String> reasoning = new HashSet<String>(Main.localApiForceReasoningModels());
            reasoning.removeAll(selected);
            Main.setLocalApiForceReasoningModels(reasoning);
            if (outerRefresh != null) outerRefresh.run();
            dialog.dismiss();
            showCustomModelSettings(activity, outerRefresh);
        });

        int width = activity.getResources().getDisplayMetrics().widthPixels;
        int height = activity.getResources().getDisplayMetrics().heightPixels;
        backdrop.addView(panel, new FrameLayout.LayoutParams(
                Math.min(dp(activity, 336), width - dp(activity, 32)),
                Math.min(dp(activity, 390), height - dp(activity, 112)), Gravity.CENTER));
        dialog.setContentView(backdrop);
        Window window = dialog.getWindow();
        if (window != null) window.setBackgroundDrawable(new ColorDrawable(0x00000000));
        dialog.show();
        window = dialog.getWindow();
        if (window != null) window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static void showCustomModelEditor(final Activity activity, final int index,
                                              final JSONObject existing,
                                              final Dialog manager,
                                              final Runnable outerRefresh) {
        final boolean isDefault = existing != null
                && "deepseek-flash".equalsIgnoreCase(existing.optString("id", ""));
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(activity, 20), dp(activity, 4), dp(activity, 20), 0);
        EditText name = new EditText(activity);
        name.setSingleLine(true);
        name.setHint(t(activity, "例如 my-deepseek", "For example my-deepseek"));
        name.setText(existing == null ? "" : existing.optString("id", ""));
        if (isDefault) {
            name.setEnabled(false);
            name.setFocusable(false);
        }
        form.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 52)));
        Spinner mapping = new Spinner(activity, Spinner.MODE_DROPDOWN);
        String[] labels = new String[]{t(activity, "快速模式", "Fast"),
                t(activity, "专家模式", "Expert"), t(activity, "识图模式", "Vision")};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity,
                android.R.layout.simple_spinner_dropdown_item, labels);
        mapping.setAdapter(adapter);
        String oldTarget = existing == null ? "default" : existing.optString("native_model", "default");
        mapping.setSelection("expert".equals(oldTarget) ? 1 : ("vision".equals(oldTarget) ? 2 : 0));
        form.addView(mapping, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 52)));
        AlertDialog editor = new AlertDialog.Builder(activity)
                .setTitle(index < 0 ? t(activity, "添加模型", "Add model")
                        : t(activity, "编辑模型", "Edit model"))
                .setView(form)
                .setNegativeButton(t(activity, "取消", "Cancel"), null)
                .setPositiveButton(t(activity, "保存", "Save"), null)
                .create();
        editor.setOnShowListener(ignored -> editor.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String id = name.getText().toString().trim().toLowerCase(Locale.US);
                    if (id.length() == 0) {
                        name.setError(t(activity, "请输入模型名称", "Enter a model name"));
                        return;
                    }
                    JSONArray models = customModels();
                    for (int i = 0; i < models.length(); i++) {
                        JSONObject value = models.optJSONObject(i);
                        if (i != index && value != null
                                && id.equalsIgnoreCase(value.optString("id", ""))) {
                            name.setError(t(activity, "模型名称已存在", "Model name already exists"));
                            return;
                        }
                    }
                    String target = mapping.getSelectedItemPosition() == 1 ? "expert"
                            : (mapping.getSelectedItemPosition() == 2 ? "vision" : "default");
                    JSONObject changed = new JSONObject();
                    try { changed.put("id", id).put("native_model", target); }
                    catch (Throwable ignoredJson) { return; }
                    JSONArray updated = new JSONArray();
                    for (int i = 0; i < models.length(); i++) {
                        updated.put(i == index ? changed : models.opt(i));
                    }
                    if (index < 0) updated.put(changed);
                    if (!Main.setLocalApiModelCatalogJson(updated.toString())) return;
                    if (existing != null) {
                        String oldId = existing.optString("id", "").toLowerCase(Locale.US);
                        Set<String> reasoning = new HashSet<String>(
                                Main.localApiForceReasoningModels());
                        if (reasoning.remove(oldId)) {
                            reasoning.add(id);
                            Main.setLocalApiForceReasoningModels(reasoning);
                        }
                    }
                    editor.dismiss();
                    manager.dismiss();
                    if (outerRefresh != null) outerRefresh.run();
                    showCustomModelSettings(activity, outerRefresh);
                }));
        editor.show();
    }

    private static void showReasoningModelSettings(final Activity activity) {
        final List<String> ids = new ArrayList<String>();
        final List<String> labels = new ArrayList<String>();
        JSONArray models = customModels();
        for (int i = 0; i < models.length(); i++) {
            JSONObject item = models.optJSONObject(i);
            if (item == null) continue;
            String id = item.optString("id", "").trim();
            if (id.length() == 0) continue;
            ids.add(id);
            labels.add(id);
        }
        final Set<String> selected = new HashSet<String>(
                Main.localApiForceReasoningModels());
        showSelectionDialog(activity,
                t(activity, "强制深度思考 · 模型", "Force reasoning · Models"),
                ids, labels, selected, new SelectionSave() {
                    @Override public void save(Set<String> values) {
                        Main.setLocalApiForceReasoningModels(values);
                    }
                });
    }

    private static void showRoutingAccountSettings(final Activity activity,
                                                    final Switch routingSwitch,
                                                    final Runnable refreshStatus) {
        final List<AccountManager.Account> accounts =
                AccountManager.accountsForUi(activity.getClassLoader());
        final Set<String> selected = new HashSet<String>(
                z12.selectedAccountIds(activity.getClassLoader()));
        final List<String> ids = new ArrayList<String>();
        final List<String> labels = new ArrayList<String>();
        for (int i = 0; i < accounts.size(); i++) {
            final AccountManager.Account account = accounts.get(i);
            if (account == null || account.id == null) continue;
            ids.add(account.id);
            labels.add(routingAccountLabel(account));
        }
        showSelectionDialog(activity,
                t(activity, "随机路由 · 账号", "Random routing · Accounts"),
                ids, labels, selected, new SelectionSave() {
                    @Override public void save(Set<String> values) {
                    z12.setSelectedAccountIds(values);
                    if (z12.accountCount(activity.getClassLoader()) < 2
                            && Main.isLocalApiMultiAccountRoutingEnabled()) {
                        Main.setLocalApiMultiAccountRoutingEnabled(false);
                        routingSwitch.setChecked(false);
                    }
                    refreshStatus.run();
                    }
        });
    }

    private interface SelectionSave { void save(Set<String> values); }

    private static void showSelectionDialog(final Activity activity, String title,
                                            List<String> ids, List<String> labels,
                                            final Set<String> selected,
                                            final SelectionSave save) {
        final boolean dark = isDark(activity);
        final int panelColor = dark ? 0xFF29292C : 0xFFFFFFFF;
        final int textColor = dark ? 0xFFF0F0F2 : 0xFF1B1B1D;
        final int divider = dark ? 0xFF3B3B40 : 0xFFECEEF2;
        final Dialog dialog = new Dialog(activity,
                android.R.style.Theme_Translucent_NoTitleBar);
        FrameLayout backdrop = new FrameLayout(activity);
        backdrop.setBackgroundColor(0x52000000);
        backdrop.setOnClickListener(view -> dialog.dismiss());

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 16), dp(activity, 14),
                dp(activity, 16), dp(activity, 10));
        panel.setClickable(true);
        GradientDrawable panelBackground = new GradientDrawable();
        panelBackground.setColor(panelColor);
        panelBackground.setCornerRadius(dp(activity, 16));
        panel.setBackground(panelBackground);
        if (android.os.Build.VERSION.SDK_INT >= 21) panel.setElevation(dp(activity, 12));

        TextView heading = label(activity, title, title, 17, textColor, true);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 40)));

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFocusable(false);
        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        scroll.addView(rows, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int count = Math.min(ids.size(), labels.size());
        for (int i = 0; i < count; i++) {
            final String id = ids.get(i);
            final RouteCheckView check = new RouteCheckView(activity, selected.contains(id));
            if (i > 0) rows.addView(line(activity, divider));
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(activity, 4), 0, dp(activity, 4), 0);
            row.addView(check, new LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 44)));
            TextView value = label(activity, labels.get(i), labels.get(i), 14,
                    textColor, false);
            value.setSingleLine(true);
            value.setGravity(Gravity.CENTER_VERTICAL);
            value.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            row.addView(value, new LinearLayout.LayoutParams(0, dp(activity, 44), 1f));
            row.setClickable(true);
            row.setOnClickListener(view -> {
                if (selected.contains(id)) selected.remove(id); else selected.add(id);
                check.setChecked(selected.contains(id));
            });
            rows.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 44)));
        }
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView cancel = label(activity, "取消", "Cancel", 14, 0xFF727780, true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setOnClickListener(view -> dialog.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(dp(activity, 64), dp(activity, 44)));
        TextView confirm = label(activity, "保存", "Save", 14, BRAND, true);
        confirm.setGravity(Gravity.CENTER);
        confirm.setOnClickListener(view -> {
            save.save(new HashSet<String>(selected));
            dialog.dismiss();
        });
        actions.addView(confirm, new LinearLayout.LayoutParams(dp(activity, 64), dp(activity, 44)));
        panel.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 48)));

        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                Math.min(dp(activity, 336), screenWidth - dp(activity, 32)),
                Math.min(dp(activity, 380), screenHeight - dp(activity, 112)), Gravity.CENTER);
        backdrop.addView(panel, panelParams);
        dialog.setContentView(backdrop);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(0x00000000));
        }
        dialog.show();
        window = dialog.getWindow();
        if (window != null) window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static String routingAccountLabel(AccountManager.Account account) {
        try {
            JSONObject credential = new JSONObject(account.credJson);
            String email = credential.optString("email", "").trim();
            if (email.length() > 0) return email;
            String mobile = credential.optString("mobile_number", "").trim();
            if (mobile.length() > 0) return mobile;
        } catch (Throwable ignored) {}
        return account.label == null || account.label.trim().length() == 0
                ? account.id : account.label;
    }

    private static final class RouteCheckView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path tick = new Path();
        private boolean checked;

        RouteCheckView(Context context, boolean checked) {
            super(context);
            this.checked = checked;
        }

        void setChecked(boolean checked) {
            this.checked = checked;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float radius = dp(getContext(), 9);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            paint.setStrokeWidth(dp(getContext(), 1.5f));
            paint.setStyle(checked ? Paint.Style.FILL : Paint.Style.STROKE);
            paint.setColor(checked ? BRAND : 0xFF9AA0AA);
            canvas.drawCircle(cx, cy, radius, paint);
            if (!checked) return;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 1.8f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(0xFFFFFFFF);
            tick.reset();
            tick.moveTo(cx - radius * .45f, cy);
            tick.lineTo(cx - radius * .10f, cy + radius * .34f);
            tick.lineTo(cx + radius * .50f, cy - radius * .38f);
            canvas.drawPath(tick, paint);
        }
    }

    private static LinearLayout card(Context context, int color) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(context, 8));
        card.setBackground(background);
        return card;
    }

    private static TextView section(Context context, String zh, String en, int color) {
        TextView view = label(context, zh, en, 16, color, true);
        view.setPadding(dp(context, 16), dp(context, 16),
                dp(context, 16), dp(context, 7));
        return view;
    }

    private static TextView fieldTitle(Context context, String zh, String en, int color) {
        TextView view = label(context, zh, en, 13, color, true);
        view.setPadding(dp(context, 16), dp(context, 9),
                dp(context, 16), dp(context, 5));
        return view;
    }

    private static TextView body(Context context, String zh, String en, int color) {
        TextView view = label(context, zh, en, 12, color, false);
        view.setLineSpacing(0f, 1.15f);
        return view;
    }

    private static TextView info(Context context, String value, int color, boolean dark) {
        TextView view = label(context, value, value, 12, color, false);
        view.setPadding(dp(context, 12), dp(context, 10),
                dp(context, 12), dp(context, 10));
        view.setTextIsSelectable(true);
        GradientDrawable background = new GradientDrawable();
        background.setColor(dark ? 0xFF202024 : 0xFFF4F6FA);
        background.setCornerRadius(dp(context, 6));
        view.setBackground(background);
        return view;
    }

    private static EditText edit(Context context, int text, int hint, boolean dark) {
        EditText view = new EditText(context);
        view.setTextColor(text);
        view.setHintTextColor(hint);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setPadding(dp(context, 12), dp(context, 10),
                dp(context, 12), dp(context, 10));
        GradientDrawable background = new GradientDrawable();
        background.setColor(dark ? 0xFF202024 : 0xFFF4F6FA);
        background.setCornerRadius(dp(context, 6));
        background.setStroke(dp(context, 1), dark ? 0xFF48484E : 0xFFD8DCE5);
        view.setBackground(background);
        return view;
    }

    private static TextView action(Context context, String zh, String en,
                                   int color, boolean dark) {
        TextView view = label(context, zh, en, 13, color, false);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(context, 10), dp(context, 10),
                dp(context, 10), dp(context, 10));
        view.setClickable(true);
        view.setFocusable(true);
        GradientDrawable background = new GradientDrawable();
        background.setColor(dark ? 0xFF2A2A2E : 0xFFF1F3F8);
        background.setCornerRadius(dp(context, 6));
        view.setBackground(background);
        return view;
    }

    private static LinearLayout switchRow(Context context, String zh, String en,
                                            int text, int sub, Switch sw) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 16), dp(context, 10),
                dp(context, 12), dp(context, 12));
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(label(context, zh, en, 15, text, true));
        row.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(sw);
        return row;
    }

    private static TextView label(Context context, String zh, String en,
                                  int size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(t(context, zh, en));
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private static View line(Context context, int color) {
        View view = new View(context);
        view.setBackgroundColor(color);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1)));
        return view;
    }

    private static LinearLayout.LayoutParams inset(Context context,
                                                   int top, int bottom) {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(dp(context, 16), dp(context, top),
                dp(context, 16), dp(context, bottom));
        return params;
    }

    private static LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static int statusBarHeight(Context context) {
        int id = context.getResources().getIdentifier(
                "status_bar_height", "dimen", "android");
        return id > 0 ? context.getResources().getDimensionPixelSize(id) : 0;
    }

    private static boolean isDark(Context context) {
        int mode = context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private static int dp(Context context, float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String t(Context context, String zh, String en) {
        return UiLanguage.text(context, zh, en);
    }
}
