package com.smallhairball.deepx;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Data/action bridge for the global chat-history search rendered by the Compose/Miuix layer.
 * The Compose side only draws Miuix widgets; searching and navigation stay here.
 */
public final class ChatSearchBridge {

    private static final int MAX_HITS = 200;

    /** One search hit, flattened for the Compose renderer. */
    public static final class HitData {
        public final String sid;
        public final String title;
        public final String source;
        public final String account;
        public final String snippet;
        public final int highlightStart;
        public final int highlightLength;

        HitData(String sid, String title, String source, String account, String snippet,
                int highlightStart, int highlightLength) {
            this.sid = sid;
            this.title = title;
            this.source = source;
            this.account = account;
            this.snippet = snippet;
            this.highlightStart = highlightStart;
            this.highlightLength = highlightLength;
        }
    }

    private ChatSearchBridge() {}

    /** Runs a search off the UI thread and delivers the hits back on the main thread. */
    public static void search(final Activity act, final String keyword, final boolean allAccounts,
            final Consumer<List<HitData>> callback) {
        if (act == null || keyword == null || keyword.trim().length() == 0) {
            if (callback != null) callback.accept(new ArrayList<>());
            return;
        }
        final String needle = keyword.trim().toLowerCase(Locale.getDefault());
        new Thread(new Runnable() {
            @Override public void run() {
                final List<HitData> hits = new ArrayList<>();
                try {
                    collect(keyword.trim(), needle, allAccounts, hits);
                } catch (Throwable ignored) {
                }
                act.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (callback != null) callback.accept(hits);
                    }
                });
            }
        }, "deepx-chat-search").start();
    }

    /** Jumps to a hit's native conversation. Returns false when it is not in the current sidebar. */
    public static boolean openHit(Activity act, String sid) {
        if (sid == null) return false;
        boolean ok = Main.openNativeSession(sid);
        if (ok) {
            DeePXUi.dismissForNativeNavigation();
        } else if (act != null) {
            Toast.makeText(act, "该对话不在当前账号的原生侧栏列表中（已删除、未同步或属于其他账号）",
                    Toast.LENGTH_LONG).show();
        }
        return ok;
    }

    private static void collect(String keyword, String needle, boolean allAccounts,
            List<HitData> hits) {
        List<File> dbs;
        if (allAccounts) {
            dbs = ChatEditorUi.allDbs();
        } else {
            dbs = new ArrayList<>();
            File current = ChatEditorUi.currentDb();
            if (current != null) dbs.add(current);
            else dbs = ChatEditorUi.allDbs();
        }
        java.util.Map<String, String> labels = ChatEditorUi.loadAccountLabels();
        for (File file : dbs) {
            if (hits.size() >= MAX_HITS) break;
            String accountLabel = labels.get(ChatEditorUi.uuidOf(file));
            SQLiteDatabase db = null;
            Cursor cursor = null;
            try {
                db = SQLiteDatabase.openDatabase(
                        file.getPath(), null, SQLiteDatabase.OPEN_READONLY);
                cursor = db.rawQuery(
                        "SELECT id,title FROM chat_session_list ORDER BY updated_at DESC", null);
                while (cursor.moveToNext() && hits.size() < MAX_HITS) {
                    String sid = cursor.getString(0);
                    String title = cursor.getString(1);
                    if (sid == null) continue;
                    for (ChatEditorUi.Msg message : ChatEditorUi.loadThread(db, sid)) {
                        String body = message.body;
                        if ("USER".equals(message.role)) {
                            int prefixEnd = ChatEditorUi.sysPrefixEnd(body);
                            if (prefixEnd > 0) body = body.substring(prefixEnd);
                        }
                        addMatch(hits, accountLabel, sid, title, body,
                                "USER".equals(message.role) ? "用户输入" : "模型回答", keyword, needle);
                        addMatch(hits, accountLabel, sid, title, message.think,
                                "深度思考", keyword, needle);
                        if (hits.size() >= MAX_HITS) break;
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                if (cursor != null) try { cursor.close(); } catch (Throwable ignored) {}
                if (db != null) try { db.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private static void addMatch(List<HitData> hits, String accountLabel, String sid, String title,
            String value, String source, String keyword, String needle) {
        if (value == null || value.length() == 0 || hits.size() >= MAX_HITS) return;
        int index = value.toLowerCase(Locale.getDefault()).indexOf(needle);
        if (index < 0) return;
        int start = Math.max(0, index - 28);
        int end = Math.min(value.length(), index + keyword.length() + 48);
        String before = value.substring(start, index).replaceAll("\\s+", " ");
        String match = value.substring(index, Math.min(value.length(), index + keyword.length()))
                .replaceAll("\\s+", " ");
        String after = value.substring(Math.min(value.length(), index + keyword.length()), end)
                .replaceAll("\\s+", " ");
        String safeTitle = title == null || title.length() == 0 ? "未命名对话" : title;
        hits.add(new HitData(sid, safeTitle, source, accountLabel, before + match + after,
                before.length(), match.length()));
    }
}