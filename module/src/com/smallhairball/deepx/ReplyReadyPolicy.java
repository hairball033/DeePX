package com.smallhairball.deepx;

import java.util.Locale;

/** Pure state policy for background reply-ready notifications. */
final class ReplyReadyPolicy {
    private ReplyReadyPolicy() {}

    static boolean shouldNotify(String previousStatus, String nextStatus,
                                String role, boolean hostForeground) {
        return !hostForeground
                && "ASSISTANT".equals(normalize(role))
                && isCompleted(nextStatus)
                && isGenerating(previousStatus);
    }

    static boolean isGenerating(String status) {
        String value = normalize(status);
        return "WIP".equals(value)
                || "CHECKING".equals(value)
                || "INCOMPLETE".equals(value)
                || "STREAMING".equals(value)
                || "GENERATING".equals(value)
                || "RESPONDING".equals(value);
    }

    static boolean isCompleted(String status) {
        String value = normalize(status);
        return "FINISHED".equals(value)
                || "COMPLETE".equals(value)
                || "COMPLETED".equals(value)
                || "SUCCESS".equals(value)
                || "SUCCEEDED".equals(value)
                || "DONE".equals(value);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.US);
    }
}
