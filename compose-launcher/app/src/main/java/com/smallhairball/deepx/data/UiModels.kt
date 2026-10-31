package com.smallhairball.deepx.data

/** Runtime activation summary shown on the home page. */
data class ActivationState(
    val active: Boolean,
    val title: String,
    val detail: String,
)

/** Snapshot of the host device shown on the home page. */
data class DeviceSnapshot(
    val product: String,
    val android: String,
    val abi: String,
    val kernel: String,
    val display: String,
    val securityPatch: String,
)