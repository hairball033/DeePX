#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

OUT=build/adapter-test
rm -rf "$OUT"
mkdir -p "$OUT"

javac -source 8 -target 8 -d "$OUT" \
    src/de/robv/android/xposed/XC_MethodHook.java \
    src/de/robv/android/xposed/XposedBridge.java \
    compat/com/smallhairball/deepx/LegacyXposedModule.java \
    tests/com/smallhairball/deepx/LegacyXposedModuleRegressionTest.java

matrix_output="$(java -cp "$OUT" com.smallhairball.deepx.LegacyXposedModuleRegressionTest)"
printf '%s\n' "$matrix_output"
printf '%s\n' "$matrix_output" | grep -q \
    'Legacy Xposed API 82-102 matrix OK (21 versions)'