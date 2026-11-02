package com.smallhairball.deepx;

import android.content.Context;
import java.util.Collections;
import java.util.List;
import org.json.JSONObject;

final class CloudPromptClient {
    interface Callback<T> { void done(T value, Throwable error); }
    static final class Prompt {
        final String id = "", title = "", note = "";
        final int size = 0, likes = 0, comments = 0, views = 0;
        final boolean pinned = false;
    }
    static final class Account {
        final String username = "", role = "member";
        final int points = 0, streak = 0, experience = 0, level = 0;
    }
    static final class ForumPost {
        final String id = "", title = "", content = "", username = "";
        final long createdAt = 0;
    }
    static final class Comment {
        final String id = "", username = "", content = "";
        final int likes = 0;
    }
    private CloudPromptClient() {}
    static boolean supported() { return false; }
    static boolean hasConsent(Context context) { return false; }
    static boolean hasLocalApiGrant(Context context) { return false; }
    static boolean hasValidLicense(Context context) { return true; }
    static boolean isLicenseValidSilent(Context context) { return true; }
    static String getDeviceCode(Context context) { return ""; }
    static String getSavedCard(Context context) { return ""; }
    static byte[] localApiPayloadKey(Context context) { return null; }
    static String localApiPayloadSha256(Context context) { return ""; }
    static void saveConsent(Context context) { }
    static String privacySummary() { return ""; }
    static void activate(Context context, Callback<Boolean> callback) {
        callback.done(Boolean.FALSE, new UnsupportedOperationException("Closed edition only"));
    }
    static void activate(Context context, String card, Callback<Boolean> callback) {
        callback.done(Boolean.FALSE, new UnsupportedOperationException("Closed edition only"));
    }
    static void list(Context context, Callback<List<Prompt>> callback) {
        callback.done(Collections.emptyList(), null);
    }
    static void upload(Context context, String title, String note, byte[] bytes, Callback<String> callback) {
        callback.done(null, new UnsupportedOperationException("Closed edition only"));
    }
    static void download(Context context, Prompt prompt, Callback<String> callback) {
        callback.done(null, new UnsupportedOperationException("Closed edition only"));
    }
    static void register(Context context, String username, String password, Callback<Account> callback) {
        callback.done(null, new UnsupportedOperationException("Closed edition only"));
    }
    static void login(Context context, String username, String password, Callback<Account> callback) {
        callback.done(null, new UnsupportedOperationException("Closed edition only"));
    }
    static void checkin(Context context, Callback<Account> callback) {
        callback.done(null, new UnsupportedOperationException("Closed edition only"));
    }
    static void forumList(Context context, Callback<List<ForumPost>> callback) {
        callback.done(Collections.emptyList(), null);
    }
    static void forumPost(Context context, String title, String content, Callback<Boolean> callback) {
        callback.done(Boolean.FALSE, new UnsupportedOperationException("Closed edition only"));
    }
    static void promptAction(Context context, String action, String promptId, String content, Callback<JSONObject> callback) {
        callback.done(null, new UnsupportedOperationException("Closed edition only"));
    }
    static void comments(Context context, String promptId, Callback<List<Comment>> callback) {
        callback.done(Collections.emptyList(), null);
    }
    static void commentLike(Context context, String id, Callback<Boolean> callback) {
        callback.done(Boolean.FALSE, new UnsupportedOperationException("Closed edition only"));
    }
    static void communityHome(Context context, Callback<JSONObject> callback) {
        callback.done(null, new UnsupportedOperationException("Closed edition only"));
    }
    static void notifications(Context context, boolean markRead, Callback<JSONObject> callback) {
        callback.done(null, new UnsupportedOperationException("Closed edition only"));
    }
    static void adminAccounts(Context context, Callback<JSONObject> callback) {
        callback.done(null, new UnsupportedOperationException("Closed edition only"));
    }
    static void adminModerate(Context context, String accountId, boolean canComment, boolean canLike,
            boolean canPost, String role, Callback<JSONObject> callback) {
        callback.done(null, new UnsupportedOperationException("Closed edition only"));
    }
    static void adminAnnouncement(Context context, String title, String content, Callback<JSONObject> callback) {
        callback.done(null, new UnsupportedOperationException("Closed edition only"));
    }
}
