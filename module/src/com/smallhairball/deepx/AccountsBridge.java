package com.smallhairball.deepx;

import android.app.Activity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Data/action bridge for the multi-account page rendered by the Compose/Miuix layer. The page only
 * draws Miuix widgets; account listing, switching, import/export/delete/validate reuse the existing
 * {@link AccountUi} implementation.
 */
public final class AccountsBridge {

    /** One account, flattened for the Compose renderer. */
    public static final class AccountData {
        public final String id;
        public final String label;
        public final String provider;
        public final boolean current;

        AccountData(String id, String label, String provider, boolean current) {
            this.id = id;
            this.label = label;
            this.provider = provider;
            this.current = current;
        }
    }

    private AccountsBridge() {}

    /** Lists the saved accounts, or an empty list when none are available. */
    public static List<AccountData> list(Activity act) {
        List<AccountData> out = new ArrayList<>();
        if (act == null) return out;
        try {
            for (AccountManager.Account a : AccountUi.accounts(act)) {
                out.add(new AccountData(a.id, displayLabel(a), a.provider, a.current));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** For Google accounts, prefix "Google  " and append the email; otherwise the plain label. */
    private static String displayLabel(AccountManager.Account a) {
        if (a == null) return "";
        if ("GOOGLE".equals(a.provider)) {
            String email = emailOf(a);
            if (email != null && !email.isEmpty()) {
                return "Google  " + email;
            }
        }
        return a.label == null ? "" : a.label;
    }

    private static String emailOf(AccountManager.Account a) {
        try {
            org.json.JSONObject o = new org.json.JSONObject(a.credJson);
            String email = o.isNull("email") ? null : o.optString("email", null);
            return email == null ? "" : email;
        } catch (Throwable error) {
            return "";
        }
    }

    /** Human-readable subtitle like the original page (provider tag / identity). */
    public static String subtitle(String provider) {
        if (provider == null) return "";
        switch (provider) {
            case "GOOGLE": return "Google";
            case "WECHAT": return "微信";
            case "APPLE": return "Apple";
            case "PHONE": return "手机号";
            default: return "邮箱";
        }
    }

    public static void switchTo(Activity act, String id) {
        if (act == null || id == null) return;
        try {
            for (AccountManager.Account a : AccountUi.accounts(act)) {
                if (id.equals(a.id)) {
                    AccountUi.actionSwitch(act, a);
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static void add(Activity act) {
        if (act == null) return;
        try { AccountUi.actionAdd(act); } catch (Throwable ignored) {}
    }

    /** "Native login" branch: log out the current account and go to the native login page. */
    public static void nativeAdd(Activity act) {
        if (act == null) return;
        try { AccountUi.actionNativeAdd(act); } catch (Throwable ignored) {}
    }

    /** "Password login" branch: verify identity/password and add the account as a candidate. */
    public static void passwordAdd(Activity act, String identity, String password,
            java.util.function.BiConsumer<Boolean, String> callback) {
        if (act == null) return;
        try {
            Main.addCandidateAccountWithPassword(act, identity, password,
                    new HookAccountLoginSecurity.CandidateLoginCallback() {
                        @Override public void onResult(final boolean success, final String message) {
                            act.runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    if (callback != null) callback.accept(success, message);
                                }
                            });
                        }
                    });
        } catch (Throwable error) {
            if (callback != null) callback.accept(false, "失败：" + error.getClass().getSimpleName());
        }
    }

    public static void importAccounts(Activity act) {
        if (act == null) return;
        try { AccountUi.actionImport(act); } catch (Throwable ignored) {}
    }

    public static void validate(Activity act) {
        if (act == null) return;
        try { AccountUi.actionBatchValidate(act); } catch (Throwable ignored) {}
    }

    public static void delete(Activity act, Set<String> ids) {
        if (act == null || ids == null || ids.isEmpty()) return;
        try { AccountUi.actionDelete(act, ids); } catch (Throwable ignored) {}
    }

    public static void export(Activity act, Set<String> ids) {
        if (act == null || ids == null || ids.isEmpty()) return;
        try { AccountUi.actionExport(act, ids); } catch (Throwable ignored) {}
    }
}