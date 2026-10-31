package com.smallhairball.deepx;

/** Pure decision policy kept separate so protection failures can be regression-tested. */
final class z16Decision {
    private z16Decision() {}

    static boolean shouldTerminateHost(String reason) {
        // Retained as a pure compatibility seam for older protected payloads and regression
        // tests.  Runtime detections may disable sensitive features, but no verdict is allowed
        // to kill the injected application process.
        return false;
    }
}
