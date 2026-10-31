package com.smallhairball.deepx;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Data/action bridge for the "账号与隐私" (Account &amp; privacy) page rendered by the Compose/Miuix
 * layer. State and actions stay here; the Compose side only draws Miuix widgets.
 */
public final class AccountPageBridge {

    public static final int TYPE_LINK = 0;
    public static final int TYPE_SWITCH = 1;

    /** One row on the account page. */
    public static final class Row {
        public final String id;
        public final String title;
        public final String subtitle;
        public final int type;
        public final boolean checked;

        Row(String id, String title, String subtitle, int type, boolean checked) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.type = type;
            this.checked = checked;
        }
    }

    private static volatile Context appContext;

    private AccountPageBridge() {}

    /** Remembers the host context for switches that need one. */
    public static void setContext(Context context) {
        appContext = context == null ? null : context.getApplicationContext();
    }

    /** Builds the current account page rows, matching the module app's own account page. */
    public static List<Row> rows() {
        List<Row> out = new ArrayList<>();
        out.add(sw("no_censor", "去除安全审查", "保留被替换前的完整回答",
                Main.isNoCensor()));
        out.add(sw("wechat_login", "解锁微信与手机号登录",
                "解锁海外用户的微信与手机号登录入口。",
                Main.isWechatMobileLoginUnlock()));
        out.add(sw("google_login", "解锁 Google 和邮箱登录",
                "解锁国内用户的 Google 与邮箱密码登录入口。",
                Main.isGoogleLoginUnlock()));
        out.add(sw("risk_bypass", "绕过风控 SDK",
                "默认已启用轻量净化（检测结果归零）。打开后 = 新设备身份模式：伪造设备标识并一次性重置数美本地身份，注册/登录以全新设备进行。重启 DeepSeek 后生效。",
                Main.isRiskBypassEnabled()));
        out.add(link("reset_device_id", "重置设备 ID",
                "清除数美本地身份并结束 DeepSeek，下次启动为全新设备（转世）。"));
        out.add(sw("anti_ban", "防止封号", "“降低封号概率”", false));
        out.add(sw("data_optout", "禁用数据用于优化体验",
                "开启后立即关闭 DeepSeek 的“数据用于优化体验”，并阻止再次开启。",
                Main.isDataOptOutEnforced()));
        out.add(sw("fake_mute", "本地禁言 · 实验性", fakeMuteDescription(),
                Main.isFakeMuteEnabled()));
        out.add(link("accounts", "多账号管理", ""));
        return out;
    }

    private static String fakeMuteDescription() {
        try {
            long until = Main.fakeMuteUntilMillis();
            if (until <= System.currentTimeMillis()) return "尚未设置有效截止时间";
            String stamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",
                    java.util.Locale.getDefault()).format(new java.util.Date(until));
            return (Main.isFakeMuteEnabled() ? "已开启至 " : "已保存，开关未开启 · ") + stamp;
        } catch (Throwable error) {
            return "尚未设置有效截止时间";
        }
    }

    /**
     * Toggles a switch row.
     *
     * @return the resulting state, so the Compose switch can reflect the real value.
     */
    public static boolean toggle(String id) {
        switch (id == null ? "" : id) {
            case "no_censor":
                Main.setNoCensor(!Main.isNoCensor());
                return Main.isNoCensor();
            case "wechat_login":
                Main.setWechatMobileLoginUnlock(!Main.isWechatMobileLoginUnlock());
                return Main.isWechatMobileLoginUnlock();
            case "google_login":
                Main.setGoogleLoginUnlock(!Main.isGoogleLoginUnlock());
                return Main.isGoogleLoginUnlock();
            case "risk_bypass":
                Main.setRiskBypassEnabled(!Main.isRiskBypassEnabled());
                return Main.isRiskBypassEnabled();
            case "anti_ban":
                // This row is an action trigger, not a persisted switch; handled in action().
                return false;
            case "data_optout":
                Main.setDataOptOutEnforced(appContext, !Main.isDataOptOutEnforced());
                return Main.isDataOptOutEnforced();
            case "fake_mute":
                Main.setFakeMuteEnabled(!Main.isFakeMuteEnabled());
                return Main.isFakeMuteEnabled();
            default:
                return false;
        }
    }

    /**
     * Sets the local-mute deadline to {@code now + millisFromNow}. Pass a non-positive value to clear
     * it. Enabling is handled separately.
     *
     * @return the new display description.
     */
    public static String setFakeMuteDeadline(long millisFromNow) {
        try {
            if (millisFromNow <= 0) {
                Main.clearFakeMute();
                return "尚未设置有效截止时间";
            }
            long until = System.currentTimeMillis() + millisFromNow;
            Main.setFakeMuteUntilMillis(until);
            Main.setFakeMuteEnabled(true);
            return fakeMuteDescription();
        } catch (Throwable error) {
            return "尚未设置有效截止时间";
        }
    }

    /** Current fake-mute description, for the Compose row. */
    public static String fakeMuteDetail() {
        return fakeMuteDescription();
    }

    /** Runs a link/action row. */
    public static void action(Activity act, String id) {
        if (act == null || id == null) return;
        switch (id) {
            case "anti_ban":
                try {
                    AccountSafetyPrank.launch(act);
                } catch (Throwable error) {
                    Toast.makeText(act, "无法启动：" + error.getClass().getSimpleName(),
                            Toast.LENGTH_SHORT).show();
                }
                break;
            case "reset_device_id":
                try {
                    HookAccountLoginSecurity.resetDeviceIdentity();
                    Toast.makeText(act, "已重置，正在结束 DeepSeek…",
                            Toast.LENGTH_SHORT).show();
                } catch (Throwable error) {
                    Toast.makeText(act, "重置失败：" + error.getClass().getSimpleName(),
                            Toast.LENGTH_SHORT).show();
                }
                break;
            case "accounts":
                AccountUi.show(act);
                break;
            default:
                break;
        }
    }

    private static Row sw(String id, String title, String subtitle, boolean checked) {
        return new Row(id, title, subtitle, TYPE_SWITCH, checked);
    }

    private static Row link(String id, String title, String subtitle) {
        return new Row(id, title, subtitle, TYPE_LINK, false);
    }
}