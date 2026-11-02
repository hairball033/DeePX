package com.smallhairball.deepx;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Experimental Features → Agent settings page. */
final class AgentSettingsUi {
    private AgentSettingsUi() {}

    /** Compose bridge: opens the advanced options surface. */
    static void showAdvancedBridge(Activity activity) {
        showAdvanced(activity);
    }

    static void show(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        final boolean dark = DeePXUi.isDark(activity);
        final int background = dark ? 0xFF191A1C : 0xFFF6F7F8;
        final int barColor = dark ? 0xFF191A1C : 0xFFF6F7F8;
        final int cardColor = dark ? 0xFF252629 : 0xFFFFFFFF;
        final int textColor = dark ? 0xFFF0F0F0 : 0xFF1A1A1A;
        final int subColor = dark ? 0xFFAAAAAF : 0xFF777B82;
        final int dividerColor = dark ? 0xFF3A3A3D : 0xFFEEEEEE;

        final Dialog dialog = new Dialog(
                activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        final LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(background);

        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(activity, 8), statusBarHeight(activity),
                dp(activity, 16), 0);
        bar.setBackgroundColor(barColor);
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(activity, 56) + statusBarHeight(activity)));
        TextView back = text(activity, "\u2039", 28, textColor, false);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(activity, 8), 0, dp(activity, 8), 0);
        back.setClickable(true);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View ignored) {
                close(dialog, root);
            }
        });
        bar.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 40)));
        TextView title = text(activity, "Agent", 18, textColor, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(activity, 8);
        bar.addView(title, titleParams);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 16), dp(activity, 16),
                dp(activity, 16), dp(activity, 28));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        final AgentToolConfig.Snapshot initial = AgentToolConfig.load();
        final LinearLayout mainCard = card(activity, cardColor);
        content.addView(mainCard);
        LinearLayout identity = new LinearLayout(activity);
        identity.setOrientation(LinearLayout.HORIZONTAL);
        identity.setGravity(Gravity.CENTER_VERTICAL);
        identity.setPadding(dp(activity, 16), dp(activity, 15),
                dp(activity, 16), dp(activity, 4));
        LinearLayout identityText = new LinearLayout(activity);
        identityText.setOrientation(LinearLayout.VERTICAL);
        identityText.addView(text(activity,
                UiLanguage.text(activity, "本地执行工作台", "Local execution workspace"),
                17, textColor, true));
        identityText.addView(text(activity,
                UiLanguage.text(activity, "工具、权限与运行状态集中管理", "Tools, permissions and runs in one place"),
                12, subColor, false));
        identity.addView(identityText, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView statusBadge = text(activity,
                initial.enabled
                        ? UiLanguage.text(activity, "● 运行中", "● Active")
                        : UiLanguage.text(activity, "○ 已停用", "○ Inactive"),
                12, initial.enabled
                        ? (dark ? 0xFF8BD5A7 : 0xFF287A49) : subColor, true);
        identity.addView(statusBadge);
        mainCard.addView(identity);
        LinearLayout overview = new LinearLayout(activity);
        overview.setOrientation(LinearLayout.HORIZONTAL);
        overview.setPadding(dp(activity, 12), dp(activity, 12),
                dp(activity, 12), dp(activity, 12));
        addOverviewMetric(activity, overview,
                UiLanguage.text(activity, "执行后端", "BACKEND"),
                backendShortLabel(activity, initial.backend), textColor, subColor, 0);
        addOverviewMetric(activity, overview,
                UiLanguage.text(activity, "已启用工具", "TOOLS"),
                String.valueOf(initial.enabledTools.size()), textColor, subColor, 1);
        addOverviewMetric(activity, overview,
                UiLanguage.text(activity, "权限", "ACCESS"),
                permissionLabel(activity, initial.permission), textColor, subColor, 2);
        mainCard.addView(overview);
        TextView note = text(activity,
                UiLanguage.text(activity,
                        "工具只作用于发起调用的当前对话，不会串到其他窗口。",
                        "Tools remain scoped to the chat that invoked them."),
                12, subColor, false);
        note.setLineSpacing(dp(activity, 2), 1f);
        note.setPadding(dp(activity, 16), dp(activity, 10),
                dp(activity, 16), dp(activity, 14));
        mainCard.addView(note);
        mainCard.addView(divider(activity, dividerColor));

        final Switch master = switchView(activity, dark);
        master.setChecked(initial.enabled);
        mainCard.addView(switchRow(activity,
                UiLanguage.text(activity, "启用 Agent", "Enable Agent"),
                UiLanguage.text(activity,
                        "关闭后不会向模型提供本地工具，也不会执行工具控制块",
                        "When off, local tools are neither offered nor executed"),
                textColor, subColor, master));
        master.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    private boolean reverting;

                    @Override public void onCheckedChanged(
                            CompoundButton button, boolean checked) {
                        if (reverting) return;
                        if (AgentToolConfig.setEnabled(checked)) {
                            statusBadge.setText(checked
                                    ? UiLanguage.text(activity, "● 运行中", "● Active")
                                    : UiLanguage.text(activity, "○ 已停用", "○ Inactive"));
                            statusBadge.setTextColor(checked
                                    ? (dark ? 0xFF8BD5A7 : 0xFF287A49) : subColor);
                            if (checked) Main.resumeAgentOutbox(activity);
                            return;
                        }
                        reverting = true;
                        button.setChecked(!checked);
                        reverting = false;
                        Toast.makeText(activity, UiLanguage.text(activity,
                                "Agent 设置保存失败", "Could not save Agent settings"),
                                Toast.LENGTH_SHORT).show();
                    }
                });

        mainCard.addView(divider(activity, dividerColor));
        LinearLayout runRow = new LinearLayout(activity);
        runRow.setOrientation(LinearLayout.HORIZONTAL);
        runRow.setGravity(Gravity.CENTER_VERTICAL);
        runRow.setPadding(dp(activity, 16), dp(activity, 13),
                dp(activity, 14), dp(activity, 13));
        LinearLayout runLabels = new LinearLayout(activity);
        runLabels.setOrientation(LinearLayout.VERTICAL);
        runLabels.addView(text(activity,
                UiLanguage.text(activity, "运行记录", "Run history"),
                15, textColor, true));
        runLabels.addView(text(activity,
                UiLanguage.text(activity,
                        "查看状态，重试或取消尚未回传的结果",
                        "Inspect status and retry or cancel undelivered results"),
                12, subColor, false));
        runRow.addView(runLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView runArrow = text(activity, "\u203a", 25, subColor, false);
        runRow.addView(runArrow);
        runRow.setClickable(true);
        runRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View ignored) {
                AgentRunUi.show(activity);
            }
        });
        mainCard.addView(runRow);

        sectionTitle(content, activity,
                UiLanguage.text(activity, "工具提示强度", "Tool prompt intensity"),
                textColor);
        LinearLayout promptCard = card(activity, cardColor);
        promptCard.setPadding(dp(activity, 16), dp(activity, 14),
                dp(activity, 16), dp(activity, 12));
        content.addView(promptCard);
        final TextView promptLevel = text(activity,
                promptStrengthLabel(activity, initial.promptStrength),
                15, textColor, true);
        promptCard.addView(promptLevel);
        final TextView promptDescription = text(activity,
                promptStrengthDescription(activity, initial.promptStrength),
                12, subColor, false);
        promptDescription.setLineSpacing(dp(activity, 1), 1f);
        LinearLayout.LayoutParams promptDescriptionParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        promptDescriptionParams.topMargin = dp(activity, 4);
        promptCard.addView(promptDescription, promptDescriptionParams);

        final SeekBar promptStrength = new SeekBar(activity);
        promptStrength.setMax(2);
        promptStrength.setKeyProgressIncrement(1);
        promptStrength.setProgress(initial.promptStrength - 1);
        promptStrength.setSplitTrack(false);
        promptStrength.setProgressTintList(
                android.content.res.ColorStateList.valueOf(DeePXUi.BRAND));
        promptStrength.setThumbTintList(
                android.content.res.ColorStateList.valueOf(DeePXUi.BRAND));
        promptStrength.setContentDescription(UiLanguage.text(activity,
                "三级工具提示强度", "Three-level tool prompt intensity"));
        LinearLayout.LayoutParams promptSliderParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 42));
        promptSliderParams.topMargin = dp(activity, 7);
        promptCard.addView(promptStrength, promptSliderParams);

        LinearLayout promptLabels = new LinearLayout(activity);
        promptLabels.setOrientation(LinearLayout.HORIZONTAL);
        promptLabels.addView(promptTick(activity,
                UiLanguage.text(activity, "基础", "Basic"), subColor, Gravity.START));
        promptLabels.addView(promptTick(activity,
                UiLanguage.text(activity, "增强", "Enhanced"), subColor, Gravity.CENTER));
        promptLabels.addView(promptTick(activity,
                UiLanguage.text(activity, "沉浸", "Immersive"), subColor, Gravity.END));
        promptCard.addView(promptLabels, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        promptStrength.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    private boolean reverting;

                    @Override public void onProgressChanged(
                            SeekBar seekBar, int progress, boolean fromUser) {
                        int level = Math.max(
                                AgentToolConfig.PROMPT_STRENGTH_BASIC,
                                Math.min(AgentToolConfig.PROMPT_STRENGTH_IMMERSIVE,
                                        progress + 1));
                        promptLevel.setText(promptStrengthLabel(activity, level));
                        promptDescription.setText(
                                promptStrengthDescription(activity, level));
                        if (!fromUser || reverting) return;
                        if (AgentToolConfig.setPromptStrength(level)) return;
                        reverting = true;
                        int previous = AgentToolConfig.load().promptStrength;
                        seekBar.setProgress(previous - 1);
                        reverting = false;
                        Toast.makeText(activity, UiLanguage.text(activity,
                                "提示词强度保存失败",
                                "Could not save prompt intensity"),
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override public void onStartTrackingTouch(SeekBar seekBar) {}

                    @Override public void onStopTrackingTouch(SeekBar seekBar) {}
                });

        sectionTitle(content, activity,
                UiLanguage.text(activity, "执行后端", "Execution backend"),
                textColor);
        final LinearLayout backendCard = card(activity, cardColor);
        content.addView(backendCard);
        final TextView backendStatus = text(activity, "", 12, subColor, false);
        backendStatus.setPadding(dp(activity, 16), dp(activity, 13),
                dp(activity, 16), dp(activity, 4));
        backendCard.addView(backendStatus);

        final LinkedHashMap<String, TextView> backendButtons = new LinkedHashMap<>();
        LinearLayout backendChoices = new LinearLayout(activity);
        backendChoices.setOrientation(LinearLayout.HORIZONTAL);
        backendChoices.setPadding(dp(activity, 12), dp(activity, 8),
                dp(activity, 12), dp(activity, 14));
        addBackendButton(activity, backendChoices, backendButtons,
                AgentToolConfig.BACKEND_IN_APP,
                UiLanguage.text(activity, "应用内", "In app"),
                textColor, dark);
        addBackendButton(activity, backendChoices, backendButtons,
                AgentToolConfig.BACKEND_ROOT, "Root", textColor, dark);
        addBackendButton(activity, backendChoices, backendButtons,
                AgentToolConfig.BACKEND_SHIZUKU, "Shizuku", textColor, dark);
        backendCard.addView(backendChoices);
        updateBackendButtons(activity, backendButtons,
                initial.backend, dark, textColor);
        backendStatus.setText(backendDescription(
                activity, initial.backend));
        for (final Map.Entry<String, TextView> entry : backendButtons.entrySet()) {
            entry.getValue().setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View ignored) {
                    final String requested = entry.getKey();
                    backendStatus.setText(UiLanguage.text(activity,
                            "正在检测连接\u2026", "Checking connection\u2026"));
                    AgentDeviceBridge.probe(
                            activity, requested,
                            new AgentDeviceBridge.StatusCallback() {
                                @Override public void onStatus(
                                        AgentDeviceBridge.Status status) {
                                    if (status.connected
                                            && AgentToolConfig.setBackend(requested)) {
                                        updateBackendButtons(
                                                activity, backendButtons,
                                                requested, dark, textColor);
                                        backendStatus.setText(status.detail);
                                    } else {
                                        backendStatus.setText(status.detail);
                                        if (AgentToolConfig.BACKEND_SHIZUKU
                                                .equals(requested)) {
                                            openShizukuManager(activity);
                                        }
                                    }
                                }
                            });
                }
            });
        }
        if (!AgentToolConfig.BACKEND_IN_APP.equals(initial.backend)) {
            AgentDeviceBridge.probe(activity, initial.backend,
                    new AgentDeviceBridge.StatusCallback() {
                        @Override public void onStatus(
                                AgentDeviceBridge.Status status) {
                            backendStatus.setText(status.detail);
                        }
                    });
        }

        if (AgentDeviceBridge.workspaceSupportedV241()) {
            sectionTitle(content, activity,
                    UiLanguage.text(activity, "工作区", "Workspace"), textColor);
            LinearLayout workspaceCard = card(activity, cardColor);
            workspaceCard.setPadding(dp(activity, 16), dp(activity, 14),
                    dp(activity, 16), dp(activity, 14));
            LinearLayout workspaceHeading = new LinearLayout(activity);
            workspaceHeading.setGravity(Gravity.CENTER_VERTICAL);
            TextView workspaceTitle = text(activity,
                    UiLanguage.text(activity, "Agent 终端", "Agent terminal"),
                    16, textColor, true);
            workspaceHeading.addView(workspaceTitle, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            View workspaceGear = DeePXUi.plainGearControl(activity, textColor,
                    UiLanguage.text(activity, "工作区权限设置", "Workspace permissions"),
                    view -> showWorkspacePolicyV241(activity));
            LinearLayout.LayoutParams workspaceGearParams = new LinearLayout.LayoutParams(
                    dp(activity, 40), dp(activity, 40));
            workspaceGearParams.rightMargin = dp(activity, 4);
            workspaceHeading.addView(workspaceGear, workspaceGearParams);
            final Switch workspaceSwitch = switchView(activity, dark);
            workspaceSwitch.setChecked(AgentDeviceBridge.workspaceEnabledV241());
            workspaceHeading.addView(workspaceSwitch);
            workspaceCard.addView(workspaceHeading);
            TextView workspaceHint = text(activity,
                    UiLanguage.text(activity,
                            "固定目录与 Agent Shell 共用。可选安装 GitHub 官方 Termux 命令环境，补齐 pkg、apt、curl 等依赖。",
                            "Shares a persistent directory with Agent Shell. Optionally install the official GitHub Termux environment for pkg, apt, curl and dependencies."),
                    12, subColor, false);
            workspaceHint.setPadding(0, dp(activity, 4), 0, dp(activity, 10));
            workspaceCard.addView(workspaceHint);
            TextView openTerminal = text(activity,
                    UiLanguage.text(activity, "打开终端  ›", "Open terminal  ›"),
                    14, DeePXUi.BRAND, true);
            openTerminal.setPadding(0, dp(activity, 8), 0, dp(activity, 4));
            openTerminal.setClickable(true);
            openTerminal.setEnabled(workspaceSwitch.isChecked());
            openTerminal.setAlpha(workspaceSwitch.isChecked() ? 1f : 0.45f);
            openTerminal.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View ignored) {
                    showWorkspaceTerminalV241(activity);
                }
            });
            workspaceSwitch.setOnCheckedChangeListener((button, checked) -> {
                if (!AgentDeviceBridge.setWorkspaceEnabledV241(checked)) {
                    button.setChecked(!checked);
                    Toast.makeText(activity, UiLanguage.text(activity,
                            "工作区开关保存失败", "Could not save workspace setting"),
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                openTerminal.setEnabled(checked);
                openTerminal.setAlpha(checked ? 1f : 0.45f);
            });
            workspaceCard.addView(openTerminal);
            content.addView(workspaceCard);
        }

        sectionTitle(content, activity,
                UiLanguage.text(activity, "权限模式", "Permission mode"),
                textColor);
        final LinearLayout permissionCard = card(activity, cardColor);
        content.addView(permissionCard);
        LinearLayout permissionRow = new LinearLayout(activity);
        permissionRow.setOrientation(LinearLayout.HORIZONTAL);
        permissionRow.setGravity(Gravity.CENTER_VERTICAL);
        permissionRow.setPadding(dp(activity, 16), dp(activity, 14),
                dp(activity, 12), dp(activity, 14));
        LinearLayout permissionLabels = new LinearLayout(activity);
        permissionLabels.setOrientation(LinearLayout.VERTICAL);
        permissionLabels.addView(text(activity,
                UiLanguage.text(activity, "访问授权", "Access authorization"),
                16, textColor, true));
        final TextView permissionDescription = text(activity,
                permissionDescription(activity, initial.permission),
                12, subColor, false);
        permissionLabels.addView(permissionDescription);
        permissionRow.addView(permissionLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView permissionButton = text(activity,
                permissionLabel(activity, initial.permission) + "  \u25be",
                14, textColor, true);
        permissionButton.setGravity(Gravity.CENTER);
        permissionButton.setPadding(dp(activity, 12), dp(activity, 9),
                dp(activity, 12), dp(activity, 9));
        permissionButton.setBackground(rounded(
                dark ? 0xFF38383C : 0xFFF0F1F4, dp(activity, 10)));
        permissionButton.setClickable(true);
        permissionRow.addView(permissionButton);
        permissionCard.addView(permissionRow);
        permissionButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View anchor) {
                showPermissionPopup(activity, anchor, dark,
                        textColor, subColor,
                        permissionButton, permissionDescription);
            }
        });

        sectionTitle(content, activity,
                UiLanguage.text(activity, "工具", "Tools"), textColor);
        LinearLayout toolsCard = card(activity, cardColor);
        content.addView(toolsCard);
        boolean chinese = UiLanguage.isChinese(activity);
        int index = 0;
        for (final String tool : AgentToolConfig.tools()) {
            if (AgentDeviceBridge.workspaceSupportedV241()
                    && (HeartbeatToolProtocol.TOOL_READ_FILE.equals(tool)
                    || HeartbeatToolProtocol.TOOL_WRITE_FILE.equals(tool)
                    || HeartbeatToolProtocol.TOOL_SHELL.equals(tool))) continue;
            if (index++ > 0) toolsCard.addView(
                    divider(activity, dividerColor));
            final Switch toggle = switchView(activity, dark);
            toggle.setChecked(initial.enabledTools.contains(tool));
            toolsCard.addView(switchRow(activity,
                    AgentToolConfig.displayName(tool, chinese),
                    AgentToolConfig.description(tool, chinese),
                    textColor, subColor, toggle));
            if (HeartbeatToolProtocol.TOOL_MCP.equals(tool)) {
                final TextView mcpSettings = text(activity,
                        UiLanguage.text(activity, "MCP 设置  ›", "MCP settings  ›"),
                        13, textColor, true);
                AgentMcpManager.Status mcpStatus = AgentMcpManager.status();
                String state = mcpStatus.state == AgentMcpManager.State.CONNECTED
                        ? "已连接 · " + mcpStatus.toolCount + " 个工具" : "未连接";
                mcpSettings.setText(state + "    " + mcpSettings.getText());
                mcpSettings.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
                mcpSettings.setPadding(dp(activity, 16), 0,
                        dp(activity, 16), dp(activity, 11));
                mcpSettings.setClickable(true);
                mcpSettings.setOnClickListener(v -> AgentMcpUi.show(activity));
                toolsCard.addView(mcpSettings);
            }
            toggle.setOnCheckedChangeListener(
                    new CompoundButton.OnCheckedChangeListener() {
                        private boolean reverting;

                        @Override public void onCheckedChanged(
                                CompoundButton button, boolean checked) {
                            if (reverting) return;
                            if (AgentToolConfig.setToolEnabled(tool, checked)) return;
                            reverting = true;
                            button.setChecked(!checked);
                            reverting = false;
                            Toast.makeText(activity, UiLanguage.text(activity,
                                    "工具设置保存失败",
                                    "Could not save tool settings"),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        }

        // Advanced controls intentionally live behind the last, bottom-aligned entry.  The
        // primary page stays a short operational overview instead of an endless settings wall.
        LinearLayout.LayoutParams advancedEntryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        advancedEntryParams.topMargin = dp(activity, 24);
        final LinearLayout advancedEntry = card(activity, cardColor);
        advancedEntry.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 13), dp(activity, 14));
        LinearLayout advancedRow = new LinearLayout(activity);
        advancedRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout advancedLabels = new LinearLayout(activity);
        advancedLabels.setOrientation(LinearLayout.VERTICAL);
        advancedLabels.addView(text(activity,
                UiLanguage.text(activity, "高级选项", "Advanced options"), 15, textColor, true));
        advancedLabels.addView(text(activity,
                UiLanguage.text(activity, "显示、上下文与思考链默认行为", "Display, context, and thinking defaults"),
                12, subColor, false));
        advancedRow.addView(advancedLabels, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView advancedArrow = text(activity, "›", 25, subColor, false);
        advancedArrow.setGravity(Gravity.CENTER);
        advancedRow.addView(advancedArrow, new LinearLayout.LayoutParams(dp(activity, 22), dp(activity, 34)));
        advancedEntry.addView(advancedRow);
        advancedEntry.setClickable(true);
        advancedEntry.setOnClickListener(v -> showAdvanced(activity));
        content.addView(advancedEntry, advancedEntryParams);

        DeePXUi.addBuildFooter(activity, content, subColor);

        UiLanguage.localizeTree(activity, root);
        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(background));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
        dialog.show();
        root.setTranslationX(activity.getResources()
                .getDisplayMetrics().widthPixels);
        root.animate().translationX(0f).setDuration(230L).start();
        dialog.setOnKeyListener(new Dialog.OnKeyListener() {
            @Override public boolean onKey(
                    android.content.DialogInterface ignored,
                    int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK
                        && event.getAction() == KeyEvent.ACTION_UP) {
                    close(dialog, root);
                    return true;
                }
                return false;
            }
        });
    }

    /** A genuine second-level settings surface: these controls are not duplicated on the main page. */
    private static void showAdvanced(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        final boolean dark = DeePXUi.isDark(activity);
        final int background = dark ? 0xFF1B1B1D : 0xFFF5F6F8;
        final int barColor = dark ? 0xFF232326 : 0xFFFFFFFF;
        final int cardColor = dark ? 0xFF2A2A2D : 0xFFFFFFFF;
        final int textColor = dark ? 0xFFF0F0F0 : 0xFF1A1A1A;
        final int subColor = dark ? 0xFFAAAAAF : 0xFF777B82;
        final int dividerColor = dark ? 0xFF3A3A3D : 0xFFEEEEEE;
        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(background);
        LinearLayout bar = new LinearLayout(activity);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(activity, 8), statusBarHeight(activity), dp(activity, 16), 0);
        bar.setBackgroundColor(barColor);
        TextView back = text(activity, "‹", 28, textColor, false);
        back.setGravity(Gravity.CENTER);
        bar.addView(back, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 56)));
        bar.addView(text(activity, UiLanguage.text(activity, "高级选项", "Advanced options"), 18, textColor, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                dp(activity, 56) + statusBarHeight(activity)));
        ScrollView scroll = new ScrollView(activity);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 16), dp(activity, 16), dp(activity, 16), dp(activity, 28));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        AgentToolConfig.Snapshot current = AgentToolConfig.load();
        LinearLayout card = card(activity, cardColor);
        content.addView(card);
        if (BuildInfo.PROTECTED_BUILD) {
            Switch hideLogs = switchView(activity, dark);
            hideLogs.setChecked(current.hideToolLogs);
            card.addView(switchRow(activity, UiLanguage.text(activity, "隐藏工具调用日志", "Hide tool invocation logs"),
                    UiLanguage.text(activity, "不显示调用记录，但工具仍会执行", "Hide invocation records while tools continue to run"),
                    textColor, subColor, hideLogs));
            bindSettingSwitch(activity, hideLogs, checked -> AgentToolConfig.setHideToolLogs(checked));
            card.addView(divider(activity, dividerColor));
            if (HostCompat.isV236() || HostCompat.isV241()) {
                Switch disableDetails = switchView(activity, dark);
                disableDetails.setChecked(HostCompat.isV236()
                        ? current.disableToolLogDetailsV236
                        : current.disableToolLogDetailsV241);
                card.addView(switchRow(activity,
                        UiLanguage.text(activity, "禁用调用日志详细窗",
                                "Disable invocation detail window"),
                        UiLanguage.text(activity,
                                "保留调用日志卡片，点击时不再打开输入和输出详情",
                                "Keep invocation cards but do not open input/output details on tap"),
                        textColor, subColor, disableDetails));
                bindSettingSwitch(activity, disableDetails,
                        checked -> {
                            if (HostCompat.isV236()) {
                                return AgentToolConfig.setDisableToolLogDetailsV236(checked);
                            } else if (HostCompat.isV241()) {
                                return AgentToolConfig.setDisableToolLogDetailsV241(checked);
                            }
                            return false;
                        });
                card.addView(divider(activity, dividerColor));
            }
        }
        Switch longContext = switchView(activity, dark);
        longContext.setChecked(current.longContextTxt);
        card.addView(switchRow(activity, UiLanguage.text(activity, "长上下文转 TXT", "Long context to TXT"),
                UiLanguage.text(activity, "超过 8000 字时后台打包早期上下文；聊天中不显示文件", "Package earlier context in the background beyond 8,000 characters"),
                textColor, subColor, longContext));
        bindSettingSwitch(activity, longContext, checked -> AgentToolConfig.setLongContextTxt(checked));
        card.addView(divider(activity, dividerColor));
        Switch collapseThinking = switchView(activity, dark);
        collapseThinking.setChecked(current.collapseThinkingByDefault);
        card.addView(switchRow(activity, UiLanguage.text(activity, "默认收起思考链", "Collapse thinking by default"),
                UiLanguage.text(activity, "仍保留完整思考内容；每条新思考默认收起，可随时在原生界面展开", "Keeps the complete thinking content; new blocks start collapsed and remain expandable"),
                textColor, subColor, collapseThinking));
        bindSettingSwitch(activity, collapseThinking, checked -> AgentToolConfig.setCollapseThinkingByDefault(checked));
        DeePXUi.addBuildFooter(activity, content, subColor);
        back.setClickable(true);
        back.setOnClickListener(v -> close(dialog, root));
        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) { window.setBackgroundDrawable(new ColorDrawable(background)); window.setLayout(-1, -1); }
        dialog.show();
        root.setTranslationX(activity.getResources().getDisplayMetrics().widthPixels * .12f);
        root.setAlpha(0f);
        root.animate().translationX(0f).alpha(1f).setDuration(180L).start();
    }

    private interface SaveToggle { boolean save(boolean value); }

    private static void bindSettingSwitch(final Activity activity, final Switch toggle,
                                          final SaveToggle saver) {
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            boolean reverting;
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                if (reverting || saver.save(checked)) return;
                reverting = true; toggle.setChecked(!checked); reverting = false;
                Toast.makeText(activity, UiLanguage.text(activity, "设置保存失败", "Could not save setting"), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static void showPermissionPopup(
            final Activity activity, final View anchor,
            boolean dark, int textColor, int subColor,
            final TextView label, final TextView description) {
        final LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 8), dp(activity, 8),
                dp(activity, 8), dp(activity, 8));
        panel.setBackground(rounded(
                dark ? 0xFF343438 : 0xFFFFFFFF, dp(activity, 14)));
        if (Build.VERSION.SDK_INT >= 21) panel.setElevation(dp(activity, 12));
        final PopupWindow popup = new PopupWindow(
                panel, dp(activity, 224),
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(0x00000000));
        popup.setOutsideTouchable(true);
        addPermissionOption(activity, panel, popup,
                AgentToolConfig.PERMISSION_EXECUTE,
                UiLanguage.text(activity, "允许执行", "Allow execution"),
                UiLanguage.text(activity,
                        "全部工具仅使用 DeepSeek 自身权限",
                        "All tools use DeepSeek's own identity"),
                textColor, subColor, label, description);
        addPermissionOption(activity, panel, popup,
                AgentToolConfig.PERMISSION_ALL,
                UiLanguage.text(activity, "全部允许", "Allow all"),
                UiLanguage.text(activity,
                        "允许已连接后端访问文件、Shell 与前台界面",
                        "Allow the backend to access files, shell, and foreground UI"),
                textColor, subColor, label, description);
        panel.measure(
                View.MeasureSpec.makeMeasureSpec(
                        dp(activity, 224), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(
                        dp(activity, 260), View.MeasureSpec.AT_MOST));
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int x = Math.max(dp(activity, 8),
                location[0] + anchor.getWidth() - dp(activity, 224));
        int y = Math.max(dp(activity, 8),
                location[1] - panel.getMeasuredHeight() - dp(activity, 8));
        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y);
    }

    private static void addPermissionOption(
            final Activity activity, LinearLayout panel,
            final PopupWindow popup, final String value,
            String title, String detail, int textColor, int subColor,
            final TextView label, final TextView description) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(activity, 12), dp(activity, 10),
                dp(activity, 12), dp(activity, 10));
        row.addView(text(activity, title, 15, textColor, true));
        row.addView(text(activity, detail, 11, subColor, false));
        row.setClickable(true);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View ignored) {
                if (AgentToolConfig.setPermission(value)) {
                    label.setText(permissionLabel(activity, value) + "  \u25be");
                    description.setText(permissionDescription(activity, value));
                    popup.dismiss();
                } else {
                    Toast.makeText(activity, UiLanguage.text(activity,
                            "权限设置保存失败",
                            "Could not save permission settings"),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        panel.addView(row);
    }

    private static LinearLayout switchRow(
            Context context, String title, String description,
            int textColor, int subColor, Switch toggle) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 16), dp(context, 13),
                dp(context, 12), dp(context, 13));
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(context, title, 15, textColor, true));
        TextView detail = text(context, description, 12, subColor, false);
        detail.setLineSpacing(dp(context, 1), 1f);
        labels.addView(detail);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelsParams.rightMargin = dp(context, 12);
        row.addView(labels, labelsParams);
        row.addView(toggle);
        return row;
    }

    private static void addBackendButton(
            Context context, LinearLayout parent,
            Map<String, TextView> targets, String key, String label,
            int textColor, boolean dark) {
        TextView button = text(context, label, 14, textColor, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(context, 6), dp(context, 10),
                dp(context, 6), dp(context, 10));
        button.setClickable(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (!targets.isEmpty()) params.leftMargin = dp(context, 8);
        parent.addView(button, params);
        targets.put(key, button);
    }

    private static void updateBackendButtons(
            Context context, Map<String, TextView> buttons,
            String selected, boolean dark, int textColor) {
        for (Map.Entry<String, TextView> entry : buttons.entrySet()) {
            boolean active = entry.getKey().equals(selected);
            entry.getValue().setTextColor(active
                    ? (dark ? 0xFFFFFFFF : 0xFF2948D8) : textColor);
            entry.getValue().setBackground(rounded(
                    active
                            ? (dark ? 0xFF3D4A79 : 0xFFE6EBFF)
                            : (dark ? 0xFF363639 : 0xFFF0F1F4),
                    dp(context, 11)));
        }
    }

    private static String backendDescription(Context context, String backend) {
        if (AgentToolConfig.BACKEND_ROOT.equals(backend)) {
            return UiLanguage.text(context,
                    "已选择 Root；点按可重新校验授权",
                    "Root selected; tap it to verify authorization again");
        }
        if (AgentToolConfig.BACKEND_SHIZUKU.equals(backend)) {
            return UiLanguage.text(context,
                    "已选择 Shizuku；连接时若服务未启动，会尝试通过 Root 自动启动",
                    "Shizuku selected; Root will try to start its service when needed");
        }
        return UiLanguage.text(context,
                "应用内模式无需额外权限，只操作当前 DeepSeek 窗口",
                "In-app mode needs no extra permission and only controls DeepSeek");
    }

    private static void openShizukuManager(Activity activity) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(
                            "moe.shizuku.privileged.api",
                            "moe.shizuku.manager.MainActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Throwable error) {
            Toast.makeText(activity, UiLanguage.text(activity,
                    "无法打开 Shizuku，请确认已经安装",
                    "Could not open Shizuku; make sure it is installed"),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private static String permissionLabel(Context context, String value) {
        return AgentToolConfig.PERMISSION_EXECUTE.equals(value)
                ? UiLanguage.text(context, "允许执行", "Allow execution")
                : UiLanguage.text(context, "全部允许", "Allow all");
    }

    private static String permissionDescription(Context context, String value) {
        return AgentToolConfig.PERMISSION_EXECUTE.equals(value)
                ? UiLanguage.text(context,
                "文件、Shell 与界面动作只使用 DeepSeek 应用自身权限",
                "Files, shell, and UI actions only use DeepSeek's app identity")
                : UiLanguage.text(context,
                "允许所选 Root/Shizuku 后端访问文件、执行 Shell 和操作前台界面",
                "Allow the selected Root/Shizuku backend to access files, run shell, "
                        + "and control the foreground UI");
    }

    private static TextView promptTick(
            Context context, String value, int color, int gravity) {
        TextView label = text(context, value, 11, color, false);
        label.setGravity(gravity);
        label.setSingleLine(true);
        label.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return label;
    }

    private static String promptStrengthLabel(Context context, int value) {
        if (value >= AgentToolConfig.PROMPT_STRENGTH_IMMERSIVE) {
            return UiLanguage.text(context,
                    "第三档 · 沉浸", "Level 3 · Immersive");
        }
        if (value >= AgentToolConfig.PROMPT_STRENGTH_ENHANCED) {
            return UiLanguage.text(context,
                    "第二档 · 增强", "Level 2 · Enhanced");
        }
        return UiLanguage.text(context,
                "第一档 · 基础", "Level 1 · Basic");
    }

    private static String promptStrengthDescription(Context context, int value) {
        if (value >= AgentToolConfig.PROMPT_STRENGTH_IMMERSIVE) {
            return UiLanguage.text(context,
                    "更主动地组合全部已启用工具；闲聊和人设中可用面板表现心情、好感与阶段变化",
                    "More proactively combines every enabled tool; casual and role-play chats "
                            + "may visualize mood, affinity, and state changes");
        }
        if (value >= AgentToolConfig.PROMPT_STRENGTH_ENHANCED) {
            return UiLanguage.text(context,
                    "让模型在普通聊天和工作中按语境适度使用全部已启用工具",
                    "Lets the model use every enabled tool contextually in casual and work chats");
        }
        return UiLanguage.text(context,
                "保持当前策略，只在用户明确要求或完成任务确实需要时调用",
                "Keeps the current policy and calls tools only when explicitly requested or needed");
    }

    private static void sectionTitle(
            LinearLayout parent, Context context, String value, int color) {
        int muted = (color & 0x00FFFFFF) | 0xA6000000;
        TextView title = text(context, value, 12, muted, true);
        title.setLetterSpacing(0.04f);
        title.setPadding(dp(context, 4), dp(context, 24),
                dp(context, 4), dp(context, 10));
        parent.addView(title);
    }

    private static void showWorkspacePolicyV241(final Activity activity) {
        if (!AgentDeviceBridge.workspaceSupportedV241()) return;
        final boolean dark = DeePXUi.isDark(activity);
        final int background = dark ? 0xFF191A1C : 0xFFF6F7F8;
        final int cardColor = dark ? 0xFF252629 : 0xFFFFFFFF;
        final int textColor = dark ? 0xFFF0F0F0 : 0xFF1A1A1A;
        final int subColor = dark ? 0xFFAAAAAF : 0xFF6D7279;
        final int dividerColor = dark ? 0xFF3A3A3D : 0xFFEEEEEE;
        final Dialog dialog = new Dialog(activity,
                android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(background);

        LinearLayout bar = new LinearLayout(activity);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(activity, 8), statusBarHeight(activity), dp(activity, 16), 0);
        TextView back = text(activity, "‹", 28, textColor, false);
        back.setGravity(Gravity.CENTER);
        back.setClickable(true);
        back.setOnClickListener(view -> dialog.dismiss());
        bar.addView(back, new LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 40)));
        TextView title = text(activity,
                UiLanguage.text(activity, "工作区权限", "Workspace permissions"),
                18, textColor, true);
        bar.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(activity, 56) + statusBarHeight(activity)));

        ScrollView scroll = new ScrollView(activity);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 16), dp(activity, 16),
                dp(activity, 16), dp(activity, 28));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView explanation = text(activity, UiLanguage.text(activity,
                "默认仅操作 Agent 私有工作区。外部文件只能复制进工作区，或把工作区成品复制出去；专用文件工具不会直接修改或删除外部原文件。",
                "By default, Agent only works inside its private workspace. External files can only be copied in, or finished workspace files copied out; dedicated file tools never modify or delete the external originals."),
                13, subColor, false);
        explanation.setLineSpacing(dp(activity, 2), 1f);
        explanation.setPadding(dp(activity, 4), 0, dp(activity, 4), dp(activity, 14));
        content.addView(explanation);

        sectionTitle(content, activity,
                UiLanguage.text(activity, "命令环境", "Command environment"), textColor);
        addWorkspaceTermuxEnvironmentV241(activity, content, cardColor,
                dark, textColor, subColor);

        sectionTitle(content, activity,
                UiLanguage.text(activity, "工具权限", "Tool permissions"), textColor);

        AgentDeviceBridge.WorkspacePolicyV241 policy =
                AgentDeviceBridge.workspacePolicyV241();
        LinearLayout tools = card(activity, cardColor);
        content.addView(tools);
        addWorkspacePolicyToggleV241(activity, tools,
                AgentDeviceBridge.WORKSPACE_TOOL_READ,
                UiLanguage.text(activity, "读取文件", "Read files"),
                UiLanguage.text(activity, "读取工作区内容", "Read workspace contents"),
                policy.read, false, dark, textColor, subColor);
        tools.addView(divider(activity, dividerColor));
        addWorkspacePolicyToggleV241(activity, tools,
                AgentDeviceBridge.WORKSPACE_TOOL_WRITE,
                UiLanguage.text(activity, "写入文件", "Write files"),
                UiLanguage.text(activity, "创建或修改工作区文件", "Create or modify workspace files"),
                policy.write, true, dark, textColor, subColor);
        tools.addView(divider(activity, dividerColor));
        addWorkspacePolicyToggleV241(activity, tools,
                AgentDeviceBridge.WORKSPACE_TOOL_SHELL,
                UiLanguage.text(activity, "自定义 Shell 指令", "Custom Shell commands"),
                UiLanguage.text(activity, "允许 Agent 执行 Shell；完全允许 + Root 时，外部目录命令直接使用 Root", "Allow Agent Shell; Full access + Root runs external-directory commands as Root"),
                policy.shell, true, dark, textColor, subColor);
        tools.addView(divider(activity, dividerColor));
        addWorkspacePolicyToggleV241(activity, tools,
                AgentDeviceBridge.WORKSPACE_TOOL_DELETE,
                UiLanguage.text(activity, "删除文件", "Delete files"),
                UiLanguage.text(activity, "仅删除工作区内的文件或空目录", "Only delete files or empty folders inside the workspace"),
                policy.delete, true, dark, textColor, subColor);
        tools.addView(divider(activity, dividerColor));
        addWorkspacePolicyToggleV241(activity, tools,
                AgentDeviceBridge.WORKSPACE_TOOL_TRANSFER,
                UiLanguage.text(activity, "传输文件", "Transfer files"),
                UiLanguage.text(activity, "以复制方式导入或导出，不覆盖外部文件", "Copy files in or out without overwriting external files"),
                policy.transfer, true, dark, textColor, subColor);

        sectionTitle(content, activity,
                UiLanguage.text(activity, "授权方式", "Authorization mode"), textColor);
        LinearLayout modeCard = card(activity, cardColor);
        final Switch allowAll = switchView(activity, dark);
        allowAll.setChecked(policy.allowAll);
        modeCard.addView(switchRow(activity,
                UiLanguage.text(activity, "完全允许", "Full access"),
                UiLanguage.text(activity,
                        "关闭时每次操作都要确认；开启后已启用工具可直接执行，并可能访问外部目录",
                        "When off, every operation needs confirmation. When on, enabled tools run directly and may access external folders"),
                textColor, subColor, allowAll));
        content.addView(modeCard);
        final boolean[] changing = new boolean[]{false};
        allowAll.setOnCheckedChangeListener((button, checked) -> {
            if (changing[0]) return;
            if (!checked) {
                if (!AgentDeviceBridge.setWorkspaceAllowAllV241(false)) {
                    changing[0] = true;
                    button.setChecked(true);
                    changing[0] = false;
                }
                return;
            }
            new android.app.AlertDialog.Builder(activity)
                    .setTitle(UiLanguage.text(activity,
                            "确认开启完全允许", "Confirm Full access"))
                    .setMessage(UiLanguage.text(activity,
                            "开启后，模型可直接执行你已启用的文件与命令工具，并可能访问外部目录。错误命令可能导致数据损失。是否继续？",
                            "The model may directly run enabled file and command tools and access external folders. A bad command can cause data loss. Continue?"))
                    .setNegativeButton(UiLanguage.text(activity, "取消", "Cancel"),
                            (alert, which) -> {
                                changing[0] = true;
                                button.setChecked(false);
                                changing[0] = false;
                            })
                    .setPositiveButton(UiLanguage.text(activity,
                            "我已了解，开启", "I understand, enable"),
                            (alert, which) -> {
                                if (!AgentDeviceBridge.setWorkspaceAllowAllV241(true)) {
                                    changing[0] = true;
                                    button.setChecked(false);
                                    changing[0] = false;
                                    Toast.makeText(activity, UiLanguage.text(activity,
                                            "设置保存失败", "Could not save setting"),
                                            Toast.LENGTH_SHORT).show();
                                }
                            })
                    .setOnCancelListener(alert -> {
                        changing[0] = true;
                        button.setChecked(false);
                        changing[0] = false;
                    })
                    .show();
        });

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) window.setBackgroundDrawable(new ColorDrawable(background));
        dialog.show();
        if (window != null) window.setLayout(-1, -1);
    }

    private static void addWorkspaceTermuxEnvironmentV241(
            final Activity activity, LinearLayout parent, int cardColor,
            boolean dark, int textColor, int subColor) {
        LinearLayout environment = card(activity, cardColor);
        environment.setPadding(dp(activity, 16), dp(activity, 14),
                dp(activity, 12), dp(activity, 14));
        LinearLayout heading = new LinearLayout(activity);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(activity,
                UiLanguage.text(activity, "Termux 命令环境", "Termux command environment"),
                15, textColor, true));
        final TextView status = text(activity, "", 12, subColor, false);
        status.setPadding(0, dp(activity, 3), dp(activity, 10), 0);
        labels.addView(status);
        heading.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView action = text(activity, "", 13, DeePXUi.BRAND, true);
        action.setGravity(Gravity.CENTER);
        action.setMinHeight(dp(activity, 40));
        action.setPadding(dp(activity, 12), dp(activity, 8),
                dp(activity, 12), dp(activity, 8));
        action.setBackground(rounded(dark ? 0xFF303238 : 0xFFF1F3F6,
                dp(activity, 8)));
        heading.addView(action);
        final TextView remove = text(activity,
                UiLanguage.text(activity, "删除环境", "Remove"),
                13, dark ? 0xFFFF8A80 : 0xFFB3261E, true);
        remove.setGravity(Gravity.CENTER);
        remove.setMinHeight(dp(activity, 40));
        remove.setPadding(dp(activity, 10), dp(activity, 8),
                dp(activity, 10), dp(activity, 8));
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        removeParams.leftMargin = dp(activity, 8);
        heading.addView(remove, removeParams);
        environment.addView(heading);
        TextView detail = text(activity, UiLanguage.text(activity,
                "从 Termux 官方 GitHub 下载约 31MB，解压约 90MB。环境只保存在工作区；Root 仅用于建立隔离挂载，命令仍以应用身份运行。",
                "Downloads about 31MB from the official Termux GitHub release and extracts about 90MB. It stays inside the workspace; Root only creates the isolated mount and commands still run as the app user."),
                12, subColor, false);
        detail.setLineSpacing(dp(activity, 2), 1f);
        detail.setPadding(0, dp(activity, 12), 0, 0);
        environment.addView(detail);
        parent.addView(environment);

        Runnable refresh = () -> updateWorkspaceTermuxEnvironmentV241(
                activity, status, action, remove);
        action.setOnClickListener(view -> {
            if (AgentDeviceBridge.termuxEnvironmentInstalledV241(activity)
                    || "installing".equals(
                    AgentDeviceBridge.termuxEnvironmentStatusV241(activity))) return;
            new android.app.AlertDialog.Builder(activity)
                    .setTitle(UiLanguage.text(activity,
                            "安装 Termux 命令环境", "Install Termux command environment"))
                    .setMessage(UiLanguage.text(activity,
                            "将从 termux/termux-packages 官方 GitHub 下载与当前 CPU 匹配的 bootstrap，并校验 SHA-256。需要约 120MB 可用空间和 Root 挂载能力。继续？",
                            "The matching bootstrap will be downloaded from the official termux/termux-packages GitHub release and verified with SHA-256. About 120MB of free space and Root mount support are required. Continue?"))
                    .setNegativeButton(UiLanguage.text(activity, "取消", "Cancel"), null)
                    .setPositiveButton(UiLanguage.text(activity, "下载并安装", "Download and install"),
                            (alert, which) -> {
                                final android.app.ProgressDialog progress =
                                        new android.app.ProgressDialog(activity);
                                progress.setIndeterminate(true);
                                progress.setCancelable(false);
                                progress.setTitle(UiLanguage.text(activity,
                                        "正在准备命令环境", "Preparing command environment"));
                                progress.setMessage(UiLanguage.text(activity,
                                        "正在下载并校验官方依赖…",
                                        "Downloading and verifying official dependencies…"));
                                action.setEnabled(false);
                                action.setAlpha(0.48f);
                                action.setText(UiLanguage.text(activity,
                                        "安装中", "Installing"));
                                status.setText(UiLanguage.text(activity,
                                        "正在安装…", "Installing…"));
                                progress.show();
                                AgentDeviceBridge.installTermuxEnvironmentV241(activity,
                                        result -> {
                                            try { progress.dismiss(); }
                                            catch (Throwable ignored) {}
                                            updateWorkspaceTermuxEnvironmentV241(
                                                    activity, status, action, remove);
                                            new android.app.AlertDialog.Builder(activity)
                                                    .setTitle(result.success
                                                            ? UiLanguage.text(activity,
                                                            "安装完成", "Installation complete")
                                                            : UiLanguage.text(activity,
                                                            "安装失败", "Installation failed"))
                                                    .setMessage(result.success
                                                            ? UiLanguage.text(activity,
                                                            "pkg、apt、curl 已可用。之后可执行 pkg install -y python git 安装所需工具。",
                                                            "pkg, apt and curl are ready. You can now run pkg install -y python git for additional tools.")
                                                            : result.detail)
                                                    .setPositiveButton(UiLanguage.text(
                                                            activity, "确定", "OK"), null)
                                                    .show();
                                        });
                            })
                    .show();
        });
        remove.setOnClickListener(view -> {
            if (!AgentDeviceBridge.termuxEnvironmentInstalledV241(activity)) return;
            new android.app.AlertDialog.Builder(activity)
                    .setTitle(UiLanguage.text(activity,
                            "删除 Termux 命令环境", "Remove Termux environment"))
                    .setMessage(UiLanguage.text(activity,
                            "将停止当前工作区终端并删除已下载的软件环境。工作区中的普通文件不会删除。继续？",
                            "The active workspace terminal will stop and downloaded command packages will be removed. Regular workspace files are kept. Continue?"))
                    .setNegativeButton(UiLanguage.text(activity, "取消", "Cancel"), null)
                    .setPositiveButton(UiLanguage.text(activity, "删除环境", "Remove"),
                            (dialog, which) -> {
                                remove.setEnabled(false);
                                AgentDeviceBridge.removeTermuxEnvironmentV241(activity, result -> {
                                    updateWorkspaceTermuxEnvironmentV241(
                                            activity, status, action, remove);
                                    Toast.makeText(activity, result.success
                                                    ? UiLanguage.text(activity,
                                                    "命令环境已删除，工作区文件已保留",
                                                    "Environment removed; workspace files kept")
                                                    : result.detail,
                                            Toast.LENGTH_LONG).show();
                                });
                            })
                    .show();
        });
        refresh.run();
    }

    private static void updateWorkspaceTermuxEnvironmentV241(
            Context context, TextView status, TextView action, TextView remove) {
        String state = AgentDeviceBridge.termuxEnvironmentStatusV241(context);
        boolean installed = "installed".equals(state);
        boolean installing = "installing".equals(state);
        status.setText(installed
                ? UiLanguage.text(context, "已安装 · pkg / apt / curl 可用",
                "Installed · pkg / apt / curl ready")
                : installing
                ? UiLanguage.text(context, "正在安装…", "Installing…")
                : UiLanguage.text(context, "未安装 · 当前使用 Android 基础 Shell",
                "Not installed · using the basic Android shell"));
        action.setText(installed
                ? UiLanguage.text(context, "已安装", "Installed")
                : installing
                ? UiLanguage.text(context, "安装中", "Installing")
                : UiLanguage.text(context, "从 GitHub 下载", "Download from GitHub"));
        action.setEnabled(!installed && !installing);
        action.setAlpha(installed || installing ? 0.48f : 1f);
        remove.setVisibility(installed ? View.VISIBLE : View.GONE);
        remove.setEnabled(installed && !installing);
        remove.setAlpha(installed && !installing ? 1f : 0.48f);
    }

    private static void addWorkspacePolicyToggleV241(final Activity activity,
            LinearLayout parent, final String tool, String title, String detail,
            boolean checked, boolean risky, boolean dark, int textColor, int subColor) {
        final Switch toggle = switchView(activity, dark);
        toggle.setChecked(checked);
        parent.addView(switchRow(activity, title, detail, textColor, subColor, toggle));
        final boolean[] changing = new boolean[]{false};
        toggle.setOnCheckedChangeListener((button, enabled) -> {
            if (changing[0]) return;
            if (!enabled || !risky) {
                if (!AgentDeviceBridge.setWorkspaceToolEnabledV241(tool, enabled)) {
                    changing[0] = true;
                    button.setChecked(!enabled);
                    changing[0] = false;
                }
                return;
            }
            new android.app.AlertDialog.Builder(activity)
                    .setTitle(UiLanguage.text(activity,
                            "确认开启高风险工具", "Confirm sensitive tool"))
                    .setMessage(UiLanguage.text(activity,
                            "此工具会让 AI 处理文件或执行命令。默认确认模式仍会逐次询问；若之后开启完全允许，可能访问外部目录并造成数据损失。",
                            "This lets AI handle files or execute commands. Confirmation mode still asks each time; enabling Full access later may expose external folders and cause data loss."))
                    .setNegativeButton(UiLanguage.text(activity, "取消", "Cancel"),
                            (alert, which) -> {
                                changing[0] = true;
                                button.setChecked(false);
                                changing[0] = false;
                            })
                    .setPositiveButton(UiLanguage.text(activity, "开启", "Enable"),
                            (alert, which) -> {
                                if (!AgentDeviceBridge.setWorkspaceToolEnabledV241(tool, true)) {
                                    changing[0] = true;
                                    button.setChecked(false);
                                    changing[0] = false;
                                }
                            })
                    .setOnCancelListener(alert -> {
                        changing[0] = true;
                        button.setChecked(false);
                        changing[0] = false;
                    })
                    .show();
        });
    }

    private static void showWorkspaceTerminalV241(final Activity activity) {
        if (!AgentDeviceBridge.workspaceSupportedV241()
                || !AgentDeviceBridge.workspaceEnabledV241()) return;
        if (!AgentDeviceBridge.termuxEnvironmentInstalledV241(activity)) {
            Toast.makeText(activity, UiLanguage.text(activity,
                    "请先在工作区设置中下载命令环境",
                    "Download the command environment in Workspace settings first"),
                    Toast.LENGTH_LONG).show();
            return;
        }
        final boolean dark = DeePXUi.isDark(activity);
        final int background = dark ? 0xFF151619 : 0xFFFFFFFF;
        final int foreground = dark ? 0xFFF1F1F1 : 0xFF171717;
        final int secondary = dark ? 0xFFAAAAAF : 0xFF666A70;
        final Dialog terminal = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 16), statusBarHeight(activity) + dp(activity, 12),
                dp(activity, 16), dp(activity, 16));
        root.setBackgroundColor(background);
        TextView title = text(activity, "‹  " + UiLanguage.text(activity,
                "工作区终端", "Workspace terminal"), 18, foreground, true);
        title.setPadding(0, 0, 0, dp(activity, 12));
        title.setClickable(true);
        title.setOnClickListener(v -> terminal.dismiss());
        root.addView(title);
        TextView repository = text(activity, UiLanguage.text(activity,
                "Termux 官方资源库：" + AgentDeviceBridge.V241_TERMUX_REPOSITORY,
                "Official Termux repository: " + AgentDeviceBridge.V241_TERMUX_REPOSITORY),
                11, DeePXUi.BRAND, false);
        repository.setPadding(0, 0, 0, dp(activity, 10));
        repository.setClickable(true);
        repository.setOnClickListener(v -> {
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW,
                        android.net.Uri.parse(AgentDeviceBridge.V241_TERMUX_REPOSITORY)));
            } catch (Throwable ignored) {}
        });
        root.addView(repository);
        final WorkspaceVtTerminalV241 vtScreen = new WorkspaceVtTerminalV241();
        final WorkspaceConsoleV241 console = new WorkspaceConsoleV241(activity);
        console.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        console.setTextColor(foreground);
        console.setTypeface(Typeface.MONOSPACE);
        console.setGravity(Gravity.TOP | Gravity.START);
        console.setSingleLine(false);
        console.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        console.setHorizontallyScrolling(false);
        console.setCursorVisible(true);
        console.setBackgroundColor(dark ? 0xFF101114 : 0xFFF7F8FA);
        console.setPadding(dp(activity, 12), dp(activity, 12),
                dp(activity, 12), dp(activity, 12));
        console.setText("");
        console.setSelection(console.length());
        final int[] inputStart = new int[]{console.length()};
        final boolean[] internalEdit = new boolean[]{false};
        final boolean[] terminalReady = new boolean[]{false};
        final List<String> commandHistory = new ArrayList<>();
        final int[] historyIndex = new int[]{0};
        final String[] historyDraft = new String[]{""};
        final String[] transcriptBeforeVt = new String[]{""};
        final boolean[] vtActive = new boolean[]{false};
        final long[] volumeChordAt = new long[]{0L};
        console.bindEditableFloor(inputStart);
        console.setRawInputSink(value -> AgentDeviceBridge.sendWorkspaceInputV241(value));
        console.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start,
                    int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start,
                    int before, int count) {}
            @Override public void afterTextChanged(Editable value) {
                if (internalEdit[0] || inputStart[0] > value.length()) return;
                int newline = value.toString().indexOf('\n', inputStart[0]);
                if (newline < 0) return;
                // IMEs insert the newline at the current cursor, not necessarily at the end.
                // Submit the complete editable command in its visual order. The old code only
                // sent the prefix before the cursor and then deleted the untouched suffix.
                String command = terminalCommandAtNewlineV241(
                        value, inputStart[0], newline);
                internalEdit[0] = true;
                value.delete(inputStart[0], value.length());
                inputStart[0] = value.length();
                console.setEditableFloor(inputStart[0]);
                internalEdit[0] = false;
                if (!terminalReady[0]) {
                    appendConsoleAtInput(console, inputStart, internalEdit,
                            "[terminal session is not ready]\n");
                    return;
                }
                String kept = command.trim();
                if (kept.length() > 0
                        && (commandHistory.isEmpty()
                        || !kept.equals(commandHistory.get(commandHistory.size() - 1)))) {
                    commandHistory.add(kept);
                    if (commandHistory.size() > 200) commandHistory.remove(0);
                }
                historyIndex[0] = commandHistory.size();
                historyDraft[0] = "";
                if (!AgentDeviceBridge.sendWorkspaceInputV241(command + "\n")) {
                    appendConsoleAtInput(console, inputStart, internalEdit,
                            "[terminal input unavailable]\n");
                }
            }
        });
        root.addView(console, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout firstKeys = terminalKeyRowV241(activity);
        addTerminalKeyV241(activity, firstKeys, "Esc", foreground, dark, () -> {
            if (AgentDeviceBridge.workspaceTerminalAtPromptV241()) {
                replaceTerminalInputV241(console, inputStart, "");
            } else {
                AgentDeviceBridge.sendWorkspaceInputV241("\u001b");
            }
        });
        addTerminalKeyV241(activity, firstKeys, "Tab", foreground, dark,
                () -> sendTerminalSequenceV241(console, inputStart, "\t", true));
        addTerminalKeyV241(activity, firstKeys, "Ctrl+C", foreground, dark, () -> {
            replaceTerminalInputV241(console, inputStart, "");
            AgentDeviceBridge.interruptWorkspaceCommandV241();
        });
        addTerminalKeyV241(activity, firstKeys, "Home", foreground, dark,
                () -> moveTerminalCursorV241(console, inputStart, KeyEvent.KEYCODE_MOVE_HOME));
        addTerminalKeyV241(activity, firstKeys, "End", foreground, dark,
                () -> moveTerminalCursorV241(console, inputStart, KeyEvent.KEYCODE_MOVE_END));
        root.addView(firstKeys, terminalKeyRowParamsV241(activity));

        LinearLayout secondKeys = terminalKeyRowV241(activity);
        addTerminalKeyV241(activity, secondKeys, "←", foreground, dark,
                () -> moveTerminalCursorV241(console, inputStart, KeyEvent.KEYCODE_DPAD_LEFT));
        addTerminalKeyV241(activity, secondKeys, "↑", foreground, dark,
                () -> moveTerminalHistoryV241(console, inputStart, commandHistory,
                        historyIndex, historyDraft, true));
        addTerminalKeyV241(activity, secondKeys, "↓", foreground, dark,
                () -> moveTerminalHistoryV241(console, inputStart, commandHistory,
                        historyIndex, historyDraft, false));
        addTerminalKeyV241(activity, secondKeys, "→", foreground, dark,
                () -> moveTerminalCursorV241(console, inputStart, KeyEvent.KEYCODE_DPAD_RIGHT));
        addTerminalKeyV241(activity, secondKeys, "PgUp", foreground, dark,
                () -> console.scrollBy(0, -Math.max(dp(activity, 120), console.getHeight() / 2)));
        addTerminalKeyV241(activity, secondKeys, "PgDn", foreground, dark,
                () -> console.scrollBy(0, Math.max(dp(activity, 120), console.getHeight() / 2)));
        root.addView(secondKeys, terminalKeyRowParamsV241(activity));

        terminal.setOnKeyListener((dialog, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP
                    || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    volumeChordAt[0] = android.os.SystemClock.elapsedRealtime();
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_C && event.getAction() == KeyEvent.ACTION_DOWN
                    && android.os.SystemClock.elapsedRealtime() - volumeChordAt[0] <= 1600L) {
                replaceTerminalInputV241(console, inputStart, "");
                AgentDeviceBridge.interruptWorkspaceCommandV241();
                volumeChordAt[0] = 0L;
                return true;
            }
            return false;
        });
        terminal.setOnDismissListener(dialog -> AgentDeviceBridge.cancelWorkspaceCommandV241());
        terminal.setContentView(root);
        Window window = terminal.getWindow();
        if (window != null) {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                    | android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
                    | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            if (Build.VERSION.SDK_INT >= 30) {
                final int left = dp(activity, 16);
                final int top = statusBarHeight(activity) + dp(activity, 12);
                final int right = dp(activity, 16);
                final int baseBottom = dp(activity, 16);
                window.setDecorFitsSystemWindows(false);
                window.setSoftInputMode(
                        android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                                | android.view.WindowManager.LayoutParams
                                        .SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                root.setOnApplyWindowInsetsListener((view, insets) -> {
                    android.graphics.Insets ime = insets.getInsets(
                            android.view.WindowInsets.Type.ime());
                    android.graphics.Insets navigation = insets.getInsets(
                            android.view.WindowInsets.Type.navigationBars());
                    int keyboard = insets.isVisible(android.view.WindowInsets.Type.ime())
                            ? Math.max(0, ime.bottom - navigation.bottom) : 0;
                    view.setPadding(left, top, right,
                            Math.max(baseBottom, keyboard + navigation.bottom));
                    return insets;
                });
            } else {
                window.setSoftInputMode(
                        android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                                | android.view.WindowManager.LayoutParams
                                        .SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
        }
        terminal.show();
        if (Build.VERSION.SDK_INT >= 30) root.requestApplyInsets();
        console.requestFocus();
        terminalReady[0] = true;
        AgentDeviceBridge.startWorkspaceTerminalSessionV241(activity,
                new AgentDeviceBridge.WorkspaceStreamCallback() {
            @Override public void onOutput(String chunk) {
                boolean wasVt = vtActive[0];
                vtScreen.feed(chunk);
                boolean isVt = vtScreen.isAlternateScreen();
                if (!wasVt && isVt) {
                    transcriptBeforeVt[0] = console.getText().toString();
                    vtActive[0] = true;
                    console.setRawInputMode(true);
                }
                if (isVt) {
                    replaceConsoleWithVtV241(console, inputStart, internalEdit, vtScreen);
                } else if (wasVt) {
                    vtActive[0] = false;
                    console.setRawInputMode(false);
                    internalEdit[0] = true;
                    console.setText(transcriptBeforeVt[0]);
                    inputStart[0] = console.length();
                    console.setEditableFloor(inputStart[0]);
                    console.setSelection(console.length());
                    internalEdit[0] = false;
                    String visible = stripTerminalControlV241(chunk);
                    if (visible.length() > 0) {
                        appendConsoleAtInput(console, inputStart, internalEdit, visible);
                    }
                } else {
                    appendConsoleAtInput(console, inputStart, internalEdit,
                            stripTerminalControlV241(chunk));
                }
            }

            @Override public void onComplete(AgentDeviceBridge.ToolResult result) {
                terminalReady[0] = false;
                if (terminal.isShowing() && !result.success) {
                    appendConsoleAtInput(console, inputStart, internalEdit,
                            "[" + result.detail + "]\n");
                }
            }
        });
    }

    private static void appendConsoleAtInput(EditText console, int[] inputStart,
            boolean[] internalEdit, String chunk) {
        if (console == null || chunk == null || chunk.length() == 0) return;
        Editable value = console.getText();
        int insertion = Math.max(0, Math.min(inputStart[0], value.length()));
        int selection = Math.max(0, console.getSelectionStart());
        internalEdit[0] = true;
        value.insert(insertion, chunk);
        inputStart[0] = insertion + chunk.length();
        if (console instanceof WorkspaceConsoleV241) {
            ((WorkspaceConsoleV241) console).setEditableFloor(inputStart[0]);
        }
        int target = selection >= insertion ? selection + chunk.length() : selection;
        console.setSelection(Math.max(0, Math.min(target, value.length())));
        internalEdit[0] = false;
        console.post(new Runnable() {
            @Override public void run() {
                console.bringPointIntoView(console.getSelectionStart());
            }
        });
    }

    private static final class WorkspaceConsoleV241 extends EditText {
        private int[] editableFloorRef;
        private int editableFloor;
        private boolean correctingSelection;
        private boolean rawInputMode;
        private RawInputSinkV241 rawInputSink;

        WorkspaceConsoleV241(Context context) {
            super(context);
        }

        void bindEditableFloor(int[] floor) {
            editableFloorRef = floor;
            setEditableFloor(floor == null ? 0 : floor[0]);
        }

        void setRawInputSink(RawInputSinkV241 sink) { rawInputSink = sink; }

        void setRawInputMode(boolean enabled) {
            rawInputMode = enabled;
            setCursorVisible(true);
        }

        void setEditableFloor(int floor) {
            editableFloor = Math.max(0, Math.min(floor, length()));
            clampSelection();
        }

        private int floor() {
            if (editableFloorRef != null) {
                editableFloor = Math.max(0, Math.min(editableFloorRef[0], length()));
            }
            return editableFloor;
        }

        private void clampSelection() {
            if (correctingSelection || getText() == null) return;
            int floor = floor();
            int start = getSelectionStart();
            int end = getSelectionEnd();
            if (start >= floor && end >= floor) return;
            correctingSelection = true;
            int safeStart = Math.max(floor, Math.max(0, start));
            int safeEnd = Math.max(floor, Math.max(0, end));
            setSelection(Math.min(safeStart, length()), Math.min(safeEnd, length()));
            correctingSelection = false;
        }

        @Override protected void onSelectionChanged(int start, int end) {
            super.onSelectionChanged(start, end);
            clampSelection();
        }

        @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_DEL && getSelectionStart() <= floor()
                    && getSelectionStart() == getSelectionEnd()) return true;
            return super.onKeyDown(keyCode, event);
        }

        @Override public android.view.inputmethod.InputConnection onCreateInputConnection(
                android.view.inputmethod.EditorInfo outAttrs) {
            final android.view.inputmethod.InputConnection base =
                    super.onCreateInputConnection(outAttrs);
            if (base == null) return null;
            return new android.view.inputmethod.InputConnectionWrapper(base, false) {
                @Override public boolean commitText(CharSequence text, int newCursorPosition) {
                    if (rawInputMode && rawInputSink != null) {
                        return rawInputSink.send(text == null ? "" : text.toString());
                    }
                    return super.commitText(text, newCursorPosition);
                }

                @Override public boolean setComposingText(
                        CharSequence text, int newCursorPosition) {
                    // Composition updates are cumulative (for example p -> pi -> pin -> 拼).
                    // Sending each intermediate value duplicates CJK input. Wait for commitText.
                    if (rawInputMode && rawInputSink != null) return true;
                    return super.setComposingText(text, newCursorPosition);
                }

                @Override public boolean sendKeyEvent(KeyEvent event) {
                    if (rawInputMode && rawInputSink != null
                            && event != null && event.getAction() == KeyEvent.ACTION_DOWN) {
                        if (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                            return rawInputSink.send("\n");
                        }
                        if (event.getKeyCode() == KeyEvent.KEYCODE_DEL) {
                            return rawInputSink.send("\u007f");
                        }
                    }
                    return super.sendKeyEvent(event);
                }

                @Override public boolean deleteSurroundingText(int beforeLength,
                        int afterLength) {
                    if (rawInputMode && rawInputSink != null && beforeLength > 0) {
                        return rawInputSink.send("\u007f");
                    }
                    int available = Math.max(0, getSelectionStart() - floor());
                    return super.deleteSurroundingText(
                            Math.min(Math.max(0, beforeLength), available), afterLength);
                }

                @Override public boolean deleteSurroundingTextInCodePoints(int beforeLength,
                        int afterLength) {
                    if (rawInputMode && rawInputSink != null && beforeLength > 0) {
                        return rawInputSink.send("\u007f");
                    }
                    int available = Math.max(0, getSelectionStart() - floor());
                    return super.deleteSurroundingTextInCodePoints(
                            Math.min(Math.max(0, beforeLength), available), afterLength);
                }
            };
        }
    }

    private interface RawInputSinkV241 {
        boolean send(String value);
    }

    private static void replaceConsoleWithVtV241(WorkspaceConsoleV241 console,
            int[] inputStart, boolean[] internalEdit, WorkspaceVtTerminalV241 screen) {
        internalEdit[0] = true;
        String rendered = screen.render();
        console.setText(rendered);
        inputStart[0] = 0;
        console.setEditableFloor(0);
        console.setSelection(Math.max(0,
                Math.min(screen.cursorOffset(), console.length())));
        internalEdit[0] = false;
    }

    static String stripTerminalControlV241(String value) {
        if (value == null || value.length() == 0) return "";
        return value.replaceAll("\\u001B\\[[0-?]*[ -/]*[@-~]", "")
                .replaceAll("\\u001B\\][^\\u0007]*(?:\\u0007|\\u001B\\\\)", "")
                .replace("\r", "");
    }

    private static LinearLayout terminalKeyRowV241(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private static LinearLayout.LayoutParams terminalKeyRowParamsV241(Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 40));
        params.topMargin = dp(context, 6);
        return params;
    }

    private static void addTerminalKeyV241(Context context, LinearLayout row,
            String label, int foreground, boolean dark, final Runnable action) {
        TextView key = text(context, label, 11, foreground, true);
        key.setGravity(Gravity.CENTER);
        key.setSingleLine(true);
        GradientDrawable background = rounded(
                dark ? 0xFF24262B : 0xFFEEF0F3, dp(context, 7));
        background.setStroke(dp(context, 1), dark ? 0xFF34373D : 0xFFDDE0E5);
        key.setBackground(background);
        key.setClickable(true);
        key.setFocusable(true);
        key.setOnClickListener(v -> {
            if (action != null) action.run();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        if (row.getChildCount() > 0) params.leftMargin = dp(context, 6);
        row.addView(key, params);
    }

    private static void sendTerminalSequenceV241(EditText console, int[] inputStart,
            String sequence, boolean insertWhenIdle) {
        if (AgentDeviceBridge.workspaceCommandRunningV241()
                && !AgentDeviceBridge.workspaceTerminalAtPromptV241()) {
            AgentDeviceBridge.sendWorkspaceInputV241(sequence);
            return;
        }
        if (!insertWhenIdle || console == null || sequence == null) return;
        Editable value = console.getText();
        int selection = Math.max(inputStart[0], console.getSelectionStart());
        selection = Math.min(selection, value.length());
        value.insert(selection, sequence);
        console.setSelection(selection + sequence.length());
    }

    private static void moveTerminalCursorV241(EditText console, int[] inputStart,
            int keyCode) {
        if (console == null) return;
        if (AgentDeviceBridge.workspaceCommandRunningV241()
                && !AgentDeviceBridge.workspaceTerminalAtPromptV241()) {
            String sequence = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? "\u001b[D"
                    : keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? "\u001b[C"
                    : keyCode == KeyEvent.KEYCODE_DPAD_UP ? "\u001b[A"
                    : keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? "\u001b[B"
                    : keyCode == KeyEvent.KEYCODE_MOVE_HOME ? "\u001b[H"
                    : keyCode == KeyEvent.KEYCODE_MOVE_END ? "\u001b[F" : "";
            if (sequence.length() > 0) AgentDeviceBridge.sendWorkspaceInputV241(sequence);
            return;
        }
        Editable value = console.getText();
        int floor = Math.max(0, Math.min(inputStart[0], value.length()));
        int selection = Math.max(floor, Math.min(console.getSelectionStart(), value.length()));
        if (keyCode == KeyEvent.KEYCODE_MOVE_HOME) selection = floor;
        else if (keyCode == KeyEvent.KEYCODE_MOVE_END) selection = value.length();
        else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) selection = Math.max(floor, selection - 1);
        else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) selection = Math.min(value.length(), selection + 1);
        else {
            console.setSelection(selection);
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) android.text.Selection.moveUp(value, console.getLayout());
            else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) android.text.Selection.moveDown(value, console.getLayout());
            selection = Math.max(floor, console.getSelectionStart());
        }
        console.setSelection(selection);
        console.requestFocus();
    }

    private static void moveTerminalHistoryV241(EditText console, int[] inputStart,
            List<String> history, int[] historyIndex, String[] draft, boolean older) {
        if (console == null) return;
        if (!AgentDeviceBridge.workspaceTerminalAtPromptV241()) {
            AgentDeviceBridge.sendWorkspaceInputV241(older ? "\u001b[A" : "\u001b[B");
            return;
        }
        if (history == null || history.isEmpty()) return;
        int current = Math.max(0, Math.min(historyIndex[0], history.size()));
        if (older) {
            if (current == history.size()) {
                Editable value = console.getText();
                int floor = Math.max(0, Math.min(inputStart[0], value.length()));
                draft[0] = value.subSequence(floor, value.length()).toString();
            }
            if (current > 0) current--;
        } else if (current < history.size()) {
            current++;
        }
        historyIndex[0] = current;
        replaceTerminalInputV241(console, inputStart,
                current == history.size() ? draft[0] : history.get(current));
    }

    private static void replaceTerminalInputV241(
            EditText console, int[] inputStart, String replacement) {
        if (console == null || inputStart == null) return;
        Editable value = console.getText();
        int floor = Math.max(0, Math.min(inputStart[0], value.length()));
        value.replace(floor, value.length(), replacement == null ? "" : replacement);
        console.setSelection(value.length());
        console.requestFocus();
    }

    static String terminalCommandAtNewlineV241(
            CharSequence value, int inputStart, int newline) {
        if (value == null) return "";
        int length = value.length();
        int floor = Math.max(0, Math.min(inputStart, length));
        int split = Math.max(floor, Math.min(newline, length));
        String before = value.subSequence(floor, split).toString();
        String after = split < length
                ? value.subSequence(split + 1, length).toString() : "";
        return (before + after).replace("\r", "");
    }

    private static LinearLayout card(Context context, int color) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable surface = rounded(color, dp(context, 13));
        surface.setStroke(dp(context, 1), DeePXUi.isDark(context)
                ? 0xFF383A3E : 0xFFE5E7EA);
        card.setBackground(surface);
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(0f);
        return card;
    }

    private static void addOverviewMetric(Context context, LinearLayout parent,
            String label, String value, int textColor, int subColor, int index) {
        LinearLayout metric = new LinearLayout(context);
        metric.setOrientation(LinearLayout.VERTICAL);
        metric.setPadding(dp(context, 8), dp(context, 10),
                dp(context, 8), dp(context, 10));
        TextView caption = text(context, label, 10, subColor, true);
        caption.setLetterSpacing(0.04f);
        metric.addView(caption);
        TextView data = text(context, value, 14, textColor, true);
        data.setSingleLine(true);
        data.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams dataParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dataParams.topMargin = dp(context, 3);
        metric.addView(data, dataParams);
        GradientDrawable inset = rounded(DeePXUi.isDark(context)
                ? 0xFF202124 : 0xFFF5F6F7, dp(context, 9));
        metric.setBackground(inset);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (index > 0) params.leftMargin = dp(context, 8);
        parent.addView(metric, params);
    }

    private static String backendShortLabel(Context context, String backend) {
        if (AgentToolConfig.BACKEND_ROOT.equals(backend)) return "Root";
        if (AgentToolConfig.BACKEND_SHIZUKU.equals(backend)) return "Shizuku";
        return UiLanguage.text(context, "应用内", "In app");
    }

    private static View divider(Context context, int color) {
        View divider = new View(context);
        divider.setBackgroundColor(color);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1));
        params.leftMargin = dp(context, 16);
        divider.setLayoutParams(params);
        return divider;
    }

    private static Switch switchView(Context context, boolean dark) {
        Switch value = new HubInsetSwitch(context);
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
        };
        value.setThumbTintList(new android.content.res.ColorStateList(
                states, new int[]{DeePXUi.BRAND,
                dark ? 0xFFCCCCCC : 0xFFFFFFFF}));
        value.setTrackTintList(new android.content.res.ColorStateList(
                states, new int[]{0xFFADBFFF,
                dark ? 0xFF555555 : 0xFFBFBFBF}));
        value.setBackground(null);
        return value;
    }

    private static TextView text(
            Context context, String value, float sp,
            int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private static GradientDrawable rounded(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private static int statusBarHeight(Context context) {
        int resource = context.getResources().getIdentifier(
                "status_bar_height", "dimen", "android");
        return resource > 0
                ? context.getResources().getDimensionPixelSize(resource) : 0;
    }

    private static int dp(Context context, float value) {
        return DeePXUi.dp(context, value);
    }

    private static void close(final Dialog dialog, View root) {
        root.animate()
                .translationX(root.getResources().getDisplayMetrics().widthPixels)
                .setDuration(190L)
                .withEndAction(new Runnable() {
                    @Override public void run() {
                        try { dialog.dismiss(); } catch (Throwable ignored) {}
                    }
                })
                .start();
    }
}