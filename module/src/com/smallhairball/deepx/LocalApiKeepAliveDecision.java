package com.smallhairball.deepx;

/** Pure state decisions shared by the foreground-keeper bridge and its regression test. */
final class LocalApiKeepAliveDecision {
    private LocalApiKeepAliveDecision() {}

    static boolean heartbeatAlreadySatisfies(boolean enabling, long heartbeatAgeMs,
                                             boolean apiKeepaliveRequested) {
        return enabling && heartbeatAgeMs >= 0L && heartbeatAgeMs <= 15_000L
                && apiKeepaliveRequested;
    }

    static boolean throttleSameDirection(long nowMs, long lastLaunchMs,
                                         boolean lastEnabled, boolean nextEnabled) {
        return lastLaunchMs > 0L && nowMs >= lastLaunchMs
                && nowMs - lastLaunchMs < 3_000L && lastEnabled == nextEnabled;
    }
}
