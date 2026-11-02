package com.smallhairball.deepx;

/** Decides whether the top-right module entry must remain available. */
final class NativeSettingsEntryPolicy {
    private NativeSettingsEntryPolicy() {}

    /**
     * Installing a Compose hook does not prove that the native row was rendered. Keep the
     * floating entry until one native row has completed successfully, so a host update can never
     * leave the user with neither entry.
     */
    static boolean showFloating(boolean nativeModeEnabled,
                                boolean nativeHookInstalled,
                                boolean nativeRowEmitted) {
        return !nativeModeEnabled || !nativeHookInstalled || !nativeRowEmitted;
    }
}