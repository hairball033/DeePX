package com.smallhairball.deepx;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** MCP server collection: terse list first, focused editor only when needed. */
final class AgentMcpUi {
    private AgentMcpUi() {}

    static void show(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        final boolean dark = DeePXUi.isDark(activity);
        final int bg = dark ? 0xFF1B1B1D : 0xFFF5F6F8;
        final int surface = dark ? 0xFF29292C : 0xFFFFFFFF;
        final int text = dark ? 0xFFF2F2F2 : 0xFF1B1B1D;
        final int sub = dark ? 0xFFAAAAB0 : 0xFF73777E;
        final int line = dark ? 0xFF414145 : 0xFFE4E5E8;
        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        LinearLayout root = new LinearLayout(activity); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(bg);
        LinearLayout bar = new LinearLayout(activity); bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(activity, 10), statusBar(activity), dp(activity, 14), 0); bar.setBackgroundColor(surface);
        TextView back = label(activity, "‹", 28, text, false); back.setGravity(Gravity.CENTER); bar.addView(back, new LinearLayout.LayoutParams(dp(activity, 46), dp(activity, 56)));
        bar.addView(label(activity, "MCP 工具", 18, text, true), new LinearLayout.LayoutParams(0, dp(activity, 56), 1f));
        TextView add = label(activity, "＋", 27, text, false); add.setGravity(Gravity.CENTER); bar.addView(add, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 56)));
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(activity, 56) + statusBar(activity)));
        ScrollView scroll = new ScrollView(activity); final LinearLayout list = new LinearLayout(activity); list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(activity, 16), dp(activity, 18), dp(activity, 16), dp(activity, 32)); scroll.addView(list); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        final Runnable refresh = new Runnable() { @Override public void run() { renderList(activity, list, dark, surface, text, sub, line, this); } };
        refresh.run();
        back.setClickable(true); back.setOnClickListener(v -> close(dialog, root));
        add.setClickable(true); add.setOnClickListener(v -> showEditor(activity, null, -1, refresh));
        dialog.setContentView(root); Window window = dialog.getWindow(); if (window != null) { window.setBackgroundDrawable(new ColorDrawable(bg)); window.setLayout(-1, -1); }
        dialog.show(); root.setTranslationX(activity.getResources().getDisplayMetrics().widthPixels * .12f); root.setAlpha(0f); root.animate().translationX(0).alpha(1).setDuration(180).start();
    }

    private static void renderList(final Activity activity, LinearLayout list, boolean dark, int surface, int text, int sub, int line, final Runnable refresh) {
        list.removeAllViews(); List<AgentMcpManager.Config> profiles = AgentMcpManager.loadAll();
        TextView intro = label(activity, profiles.isEmpty() ? "还没有 MCP 服务" : "已配置的 MCP 服务", 14, sub, false);
        intro.setPadding(dp(activity, 4), 0, dp(activity, 4), dp(activity, 10)); list.addView(intro);
        if (profiles.isEmpty()) { TextView empty = label(activity, "点击右上角 ＋ 添加服务。保存并检测成功后，工具会自动同步到当前 Agent。", 14, sub, false); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(activity, 22), dp(activity, 38), dp(activity, 22), dp(activity, 38)); empty.setBackground(round(surface, line, dp(activity, 16))); list.addView(empty); return; }
        for (int i = 0; i < profiles.size(); i++) {
            final int index = i; final AgentMcpManager.Config config = profiles.get(i);
            LinearLayout card = new LinearLayout(activity); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 13), dp(activity, 13)); card.setBackground(round(surface, line, dp(activity, 15)));
            LinearLayout head = new LinearLayout(activity); head.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout labels = new LinearLayout(activity); labels.setOrientation(LinearLayout.VERTICAL);
            labels.addView(label(activity, config.name.length() == 0 ? "未命名 MCP" : config.name, 16, text, true));
            TextView endpoint = label(activity, config.url.length() == 0 ? "尚未填写地址" : config.url, 11, sub, false); endpoint.setSingleLine(true); labels.addView(endpoint);
            head.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            Switch enabled = mcpSwitch(activity, dark); enabled.setChecked(config.enabled); head.addView(enabled); card.addView(head);
            AgentMcpManager.Status state = AgentMcpManager.status(); boolean online = config.enabled && state.state == AgentMcpManager.State.CONNECTED;
            TextView status = label(activity, online ? "● 在线 · 已同步 " + config.tools.size() + " 个工具" : config.enabled ? "● 未连接 · 点击卡片重新检测" : "○ 已停用", 12, online ? (dark ? 0xFF87DDAA : 0xFF218653) : sub, false);
            status.setPadding(0, dp(activity, 10), 0, 0); card.addView(status);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2); if (i > 0) cp.topMargin = dp(activity, 10); list.addView(card, cp);
            enabled.setOnCheckedChangeListener((button, checked) -> { ArrayList<AgentMcpManager.Config> next = new ArrayList<>(AgentMcpManager.loadAll()); next.set(index, config.with(checked, config.name, config.url, config.transport, config.headers)); if (AgentMcpManager.saveAll(next)) { if (checked) AgentMcpManager.probeAllAsync(activity, ignored -> refresh.run()); else refresh.run(); } else { button.setChecked(!checked); Toast.makeText(activity, "MCP 配置保存失败", Toast.LENGTH_SHORT).show(); } });
            card.setClickable(true); card.setOnClickListener(v -> showEditor(activity, config, index, refresh));
        }
    }

    private static void showEditor(final Activity activity, final AgentMcpManager.Config original, final int index, final Runnable refresh) {
        final boolean dark = DeePXUi.isDark(activity); final int surface = dark ? 0xFF29292C : 0xFFFFFFFF; final int inset = dark ? 0xFF202023 : 0xFFF5F6F8; final int text = dark ? 0xFFF2F2F2 : 0xFF1B1B1D; final int sub = dark ? 0xFFAAAAB0 : 0xFF73777E; final int line = dark ? 0xFF414145 : 0xFFE4E5E8;
        final AgentMcpManager.Config value = original == null ? AgentMcpManager.defaults() : original;
        final Dialog dialog = new Dialog(activity); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(activity); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(activity, 20), dp(activity, 18), dp(activity, 20), dp(activity, 16)); panel.setBackground(round(surface, line, dp(activity, 22)));
        LinearLayout title = new LinearLayout(activity); title.setGravity(Gravity.CENTER_VERTICAL); title.addView(label(activity, original == null ? "添加 MCP 服务" : "编辑 MCP 服务", 19, text, true), new LinearLayout.LayoutParams(0, -2, 1)); final Switch enabled = mcpSwitch(activity, dark); enabled.setChecked(value.enabled); title.addView(enabled); panel.addView(title);
        ScrollView scroll = new ScrollView(activity); LinearLayout form = new LinearLayout(activity); form.setOrientation(LinearLayout.VERTICAL); scroll.addView(form); panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        final EditText name = input(activity, "服务名称", value.name, inset, line, text, false); form.addView(name, params(-1, dp(activity, 48), 12, activity));
        final EditText url = input(activity, "Streamable HTTP / SSE 地址", value.url, inset, line, text, false); form.addView(url, params(-1, dp(activity, 48), 10, activity));
        final TextView transport = label(activity, AgentMcpManager.TRANSPORT_SSE.equals(value.transport) ? "传输方式  SSE  ▾" : "传输方式  Streamable HTTP  ▾", 14, text, true); transport.setTag(value.transport); transport.setGravity(Gravity.CENTER_VERTICAL); transport.setPadding(dp(activity, 13), 0, dp(activity, 13), 0); transport.setBackground(round(inset, line, dp(activity, 11))); form.addView(transport, params(-1, dp(activity, 48), 10, activity)); transport.setOnClickListener(v -> { boolean sse = AgentMcpManager.TRANSPORT_SSE.equals(transport.getTag()); transport.setTag(sse ? AgentMcpManager.TRANSPORT_HTTP : AgentMcpManager.TRANSPORT_SSE); transport.setText(sse ? "传输方式  Streamable HTTP  ▾" : "传输方式  SSE  ▾"); });
        LinearLayout headerHead = new LinearLayout(activity); headerHead.setGravity(Gravity.CENTER_VERTICAL); headerHead.addView(label(activity, "自定义请求头", 14, text, true), new LinearLayout.LayoutParams(0, -2, 1)); TextView plus = label(activity, "＋ 添加", 13, DeePXUi.BRAND, true); headerHead.addView(plus); form.addView(headerHead, params(-1, -2, 14, activity));
        final LinearLayout headers = new LinearLayout(activity); headers.setOrientation(LinearLayout.VERTICAL); form.addView(headers); final ArrayList<HeaderRow> rows = new ArrayList<>(); for (AgentMcpManager.Header h : value.headers) addHeader(activity, headers, rows, h.name, h.value, inset, line, text, sub); plus.setOnClickListener(v -> addHeader(activity, headers, rows, "", "", inset, line, text, sub));
        TextView hint = label(activity, "请求值视为敏感信息，不会写入调用日志。", 11, sub, false); hint.setPadding(0, dp(activity, 8), 0, dp(activity, 4)); form.addView(hint);
        LinearLayout actions = new LinearLayout(activity); actions.setGravity(Gravity.CENTER_VERTICAL); actions.setPadding(0, dp(activity, 15), 0, 0); TextView remove = label(activity, "删除", 14, 0xFFC23B3B, true); actions.addView(remove, new LinearLayout.LayoutParams(0, dp(activity, 44), 1)); TextView cancel = action(activity, "取消", inset, line, text); actions.addView(cancel, new LinearLayout.LayoutParams(-2, dp(activity, 44))); TextView save = action(activity, "保存并检测", DeePXUi.BRAND, DeePXUi.BRAND, 0xFFFFFFFF); LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, dp(activity, 44)); sp.leftMargin = dp(activity, 9); actions.addView(save, sp); panel.addView(actions);
        remove.setVisibility(original == null ? View.INVISIBLE : View.VISIBLE); cancel.setOnClickListener(v -> dialog.dismiss()); remove.setOnClickListener(v -> { ArrayList<AgentMcpManager.Config> next = new ArrayList<>(AgentMcpManager.loadAll()); if (index >= 0 && index < next.size() && AgentMcpManager.saveAll(removeAt(next, index))) { dialog.dismiss(); refresh.run(); } });
        save.setOnClickListener(v -> { ArrayList<AgentMcpManager.Header> hs = new ArrayList<>(); for (HeaderRow row : rows) { String k = row.name.getText().toString().trim(); if (k.length() > 0) hs.add(new AgentMcpManager.Header(k, row.value.getText().toString())); } AgentMcpManager.Config nextConfig = value.with(enabled.isChecked(), name.getText().toString(), url.getText().toString(), String.valueOf(transport.getTag()), hs); ArrayList<AgentMcpManager.Config> next = new ArrayList<>(AgentMcpManager.loadAll()); if (index < 0) next.add(nextConfig); else next.set(index, nextConfig); if (!AgentMcpManager.saveAll(next)) { Toast.makeText(activity, "MCP 配置保存失败", Toast.LENGTH_SHORT).show(); return; } if (!nextConfig.enabled) { dialog.dismiss(); refresh.run(); return; } save.setEnabled(false); AgentMcpManager.probeAllAsync(activity, state -> { save.setEnabled(true); if (state.state == AgentMcpManager.State.CONNECTED) { Toast.makeText(activity, "已保存并检测", Toast.LENGTH_SHORT).show(); dialog.dismiss(); refresh.run(); } else Toast.makeText(activity, "检测失败：" + state.detail, Toast.LENGTH_LONG).show(); }); });
        dialog.setContentView(panel); Window window = dialog.getWindow(); if (window != null) { window.setBackgroundDrawable(new ColorDrawable(0)); WindowManager.LayoutParams lp = window.getAttributes(); lp.width = Math.min(dp(activity, 440), activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 28)); lp.height = Math.min(dp(activity, 650), activity.getResources().getDisplayMetrics().heightPixels - dp(activity, 70)); window.setAttributes(lp); } dialog.show();
    }

    private static ArrayList<AgentMcpManager.Config> removeAt(ArrayList<AgentMcpManager.Config> list, int index) { list.remove(index); return list; }
    private static void addHeader(Activity a, LinearLayout parent, final List<HeaderRow> rows, String key, String value, int inset, int line, int text, int sub) { LinearLayout row = new LinearLayout(a); row.setGravity(Gravity.CENTER_VERTICAL); EditText k = input(a, "请求头", key, inset, line, text, false); EditText v = input(a, "请求值", value, inset, line, text, true); row.addView(k, new LinearLayout.LayoutParams(0, dp(a, 48), .42f)); LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(0, dp(a, 48), .58f); vp.leftMargin = dp(a, 7); row.addView(v, vp); TextView x = label(a, "×", 22, sub, false); x.setGravity(Gravity.CENTER); row.addView(x, new LinearLayout.LayoutParams(dp(a, 36), dp(a, 48))); LinearLayout.LayoutParams rp = params(-1, dp(a, 48), 7, a); parent.addView(row, rp); HeaderRow record = new HeaderRow(k, v); rows.add(record); x.setOnClickListener(z -> { rows.remove(record); parent.removeView(row); }); }
    private static final class HeaderRow { final EditText name, value; HeaderRow(EditText n, EditText v) { name=n; value=v; } }
    private static EditText input(Activity a, String hint, String value, int fill, int line, int text, boolean secret) { EditText out = new EditText(a); out.setHint(hint); out.setText(value == null ? "" : value); out.setTextSize(13); out.setTextColor(text); out.setSingleLine(true); out.setPadding(dp(a, 12), 0, dp(a, 12), 0); out.setBackground(round(fill, line, dp(a, 11))); if (secret) out.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); return out; }
    private static TextView action(Activity a, String value, int fill, int line, int text) { TextView out=label(a,value,14,text,true); out.setGravity(Gravity.CENTER); out.setPadding(dp(a,16),0,dp(a,16),0); out.setBackground(round(fill,line,dp(a,11))); return out; }
    private static Switch mcpSwitch(Activity activity, boolean dark) {
        Switch value = new HubInsetSwitch(activity);
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
        };
        value.setThumbTintList(new android.content.res.ColorStateList(states,
                new int[]{DeePXUi.BRAND, dark ? 0xFFCCCCCC : 0xFFFFFFFF}));
        value.setTrackTintList(new android.content.res.ColorStateList(states,
                new int[]{0xFFADBFFF, dark ? 0xFF555555 : 0xFFBFBFBF}));
        value.setBackground(null);
        return value;
    }
    private static TextView label(Activity a, String value, float size, int color, boolean bold) { TextView out=new TextView(a); out.setText(value); out.setTextSize(size); out.setTextColor(color); if (bold) out.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return out; }
    private static GradientDrawable round(int fill,int line,float radius) { GradientDrawable out=new GradientDrawable(); out.setColor(fill); out.setStroke(1,line); out.setCornerRadius(radius); return out; }
    private static LinearLayout.LayoutParams params(int w,int h,int top,Activity a) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h); p.topMargin=dp(a,top); return p; }
    private static void close(final Dialog d, View root) { root.animate().translationX(root.getWidth()).setDuration(150).withEndAction(d::dismiss).start(); }
    private static int statusBar(Activity a) { int r=a.getResources().getIdentifier("status_bar_height","dimen","android"); return r>0?a.getResources().getDimensionPixelSize(r):0; }
    private static int dp(Activity a,float v) { return Math.round(v*a.getResources().getDisplayMetrics().density); }
}
