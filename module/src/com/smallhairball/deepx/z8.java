package com.smallhairball.deepx;

import java.util.Locale;

/** Keeps routine Local API traffic out of every diagnostics sink. */
public final class z8 {
    private z8() {}

    public static boolean shouldWriteApiMessage(String message) {
        return isError(message) || isRequestTiming(message) || isOperationalDiagnostic(message);
    }

    /**
     * Small, credential-free proof points needed to distinguish a working native bridge from a
     * request that merely happened to succeed through the current account. Keep this allow-list
     * narrow: full prompts, account ids and authorization values must never reach the API log.
     */
    static boolean isOperationalDiagnostic(String message) {
        if (message == null || message.length() == 0) return false;
        return message.startsWith("ROUTING_HOOK_READY")
                || message.startsWith("ACCOUNT_ROUTE_BOUND")
                || message.startsWith("ACCOUNT_HEADER_APPLIED")
                || message.startsWith("ACCOUNT_REROUTED")
                || message.startsWith("CONTEXT_RELAY_");
    }

    /** Successful request timing is persisted so latency regressions stay traceable. */
    static boolean isRequestTiming(String message) {
        if (message == null || message.length() == 0) return false;
        return message.startsWith("CHAT_OK")
                || message.startsWith("RESPONSES_OK")
                || message.startsWith("CHAT_BEGIN")
                || message.startsWith("RESPONSES_BEGIN");
    }

    static boolean shouldSuppressGeneralMessage(String message) {
        return f8(message) && !isError(message);
    }

    static boolean f8(String message) {
        if (message == null || message.length() == 0) return false;
        String lower = message.toLowerCase(Locale.US);
        return lower.contains("local api")
                || lower.contains("local-api")
                || lower.contains("local_api")
                || lower.contains("deekseep_api");
    }

    static boolean isError(String message) {
        if (message == null || message.length() == 0) return false;
        String normalized = message.toLowerCase(Locale.US)
                .replace('_', ' ')
                .replace('-', ' ');
        return containsWord(normalized, "fail")
                || normalized.contains("failed")
                || normalized.contains("failure")
                || normalized.contains("error")
                || normalized.contains("exception")
                || normalized.contains("timeout")
                || normalized.contains("timed out")
                || normalized.contains("blocked")
                || normalized.contains("rejected")
                || normalized.contains("denied")
                || normalized.contains("unavailable")
                || normalized.contains("invalid")
                || normalized.contains("corrupt")
                || normalized.contains("unable")
                || normalized.contains("fatal")
                || normalized.contains("crash")
                || normalized.contains("broken")
                || normalized.contains("missing")
                || normalized.contains("not found")
                || normalized.contains("queue is full");
    }

    private static boolean containsWord(String text, String word) {
        int from = 0;
        while (true) {
            int at = text.indexOf(word, from);
            if (at < 0) return false;
            int end = at + word.length();
            boolean left = at == 0 || !Character.isLetterOrDigit(text.charAt(at - 1));
            boolean right = end == text.length()
                    || !Character.isLetterOrDigit(text.charAt(end));
            if (left && right) return true;
            from = at + 1;
        }
    }
}
