package com.smallhairball.deepx;

import org.json.JSONObject;

public final class ChatBackupFormatRegressionTest {
    public static void main(String[] args) throws Exception {
        JSONObject valid = new JSONObject()
                .put("format", "deekseep-chat-backup")
                .put("version", 1);
        check(DeePXTools.validBackupManifest(valid), "current manifest accepted");
        check(!DeePXTools.validBackupManifest(new JSONObject()
                .put("format", "deekseep-chat-backup").put("version", 2)),
                "unknown format version rejected");
        check(DeePXTools.portableSessionEntry(
                "sessions/account__session-id.json"), "session entry accepted");
        check(!DeePXTools.portableSessionEntry(
                "sessions/../manifest.json"), "relative traversal rejected");
        check(!DeePXTools.portableSessionEntry(
                "sessions\\account__session.json"), "backslash traversal rejected");
        System.out.println("Chat backup format regression tests passed");
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
