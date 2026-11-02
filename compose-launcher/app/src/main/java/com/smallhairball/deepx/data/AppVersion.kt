package com.smallhairball.deepx.data

import com.smallhairball.deepx.BuildInfo

/**
 * Single source of truth for DeePX version metadata.
 *
 * The authoritative values are injected at build time by `compose-launcher/prepare-development-inputs.sh`
 * into [BuildInfo]. Every screen must read the version through this object instead of touching
 * [BuildInfo] directly, so a version bump only ever needs one place to change.
 */
object AppVersion {

    /** Semantic module version, e.g. "1.0.0". */
    @JvmStatic
    val version: String
        get() = BuildInfo.MODULE_VERSION

    /** Build edition label, e.g. "Ultra". */
    @JvmStatic
    val edition: String
        get() = BuildInfo.BUILD_EDITION

    /** Full display string, e.g. "1.0.0 Ultra". */
    @JvmStatic
    val displayVersion: String
        get() = BuildInfo.DISPLAY_VERSION

    /** Build date stamp, e.g. "20260926". */
    @JvmStatic
    val buildDate: String
        get() = BuildInfo.BUILD_DATE

    /** Compatible Xposed interface summary. */
    @JvmStatic
    val apiVersion: String
        get() = BuildInfo.API_VERSION

    /** Human-facing application name including the edition suffix. */
    @JvmStatic
    val appName: String
        get() = "DeePX Ultra"

    /** Short subtitle shown under the app name, e.g. "1.0.0 Ultra (33)". */
    @JvmStatic
    fun subtitle(): String = "$displayVersion (20260926)"

    /** Default export file name for configuration snapshots. */
    @JvmStatic
    fun configFileName(): String = "deepx-config-$version.json"
}