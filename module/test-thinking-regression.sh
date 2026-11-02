#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

source ../scripts/android-tools.sh
OUT="build/thinking-test"
JSON_JAR="$OUT/lib/json-20240303.jar"
DEPS_DIR="build/test-deps"
JSON_CACHE="$DEPS_DIR/json-20240303.jar"
JSON_SHA256="3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed"

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/lib" "$DEPS_DIR"

# Tests use the already-built universal Main/adapter classes; this keeps
# the regression suite aligned with the APK that is actually distributed.
UNIVERSAL_CLASSES="../module-universal/build/classes"
if [[ ! -f "$UNIVERSAL_CLASSES/com/smallhairball/deepx/Main.class" ]]; then
    echo "Universal classes are missing; build module-universal before running tests" >&2
    exit 1
fi

if [[ ! -f "$JSON_CACHE" ]] || ! printf '%s  %s\n' "$JSON_SHA256" "$JSON_CACHE" \
        | sha256sum -c - >/dev/null 2>&1; then
    tmp="$JSON_CACHE.tmp"
    rm -f "$tmp"
    downloaded=false
    for base in \
        https://repo.maven.apache.org/maven2 \
        https://repo1.maven.org/maven2 \
        https://maven.aliyun.com/repository/public; do
        if curl -fsSL --connect-timeout 15 --max-time 60 \
                "$base/org/json/json/20240303/json-20240303.jar" -o "$tmp" \
                && printf '%s  %s\n' "$JSON_SHA256" "$tmp" \
                    | sha256sum -c - >/dev/null 2>&1; then
            mv "$tmp" "$JSON_CACHE"
            downloaded=true
            break
        fi
        rm -f "$tmp"
    done
    if [[ "$downloaded" != true ]]; then
        echo "Could not download a verified org.json test dependency" >&2
        exit 1
    fi
fi
cp "$JSON_CACHE" "$JSON_JAR"

javac -source 8 -target 8 -cp "$JSON_JAR:$ANDROID_JAR:$UNIVERSAL_CLASSES:build/classes" \
    -d "$OUT/classes" \
    tests/com/smallhairball/deepx/ChatEditorThinkingRegressionTest.java \
    tests/com/smallhairball/deepx/ChatEditorHistoryImageRegressionTest.java \
    tests/com/smallhairball/deepx/HistoryBridgeRegressionTest.java \
    tests/com/smallhairball/deepx/NativeSessionDeleteRegressionTest.java \
    tests/com/smallhairball/deepx/NativeSessionRefreshRegressionTest.java \
    tests/com/smallhairball/deepx/ResponsePreserverRegressionTest.java \
    tests/com/smallhairball/deepx/AccountCredentialCodecRegressionTest.java \
    tests/com/smallhairball/deepx/AccountServerValidationRegressionTest.java \
    tests/com/smallhairball/deepx/GoogleLoginUnlockRegressionTest.java \
    tests/com/smallhairball/deepx/NativeApiPatchDecoderRegressionTest.java \
    tests/com/smallhairball/deepx/HostCompatRegressionTest.java \
    tests/com/smallhairball/deepx/UiLanguagePolicyRegressionTest.java \
    tests/com/smallhairball/deepx/ChatAppearanceConfigRegressionTest.java \
    tests/com/smallhairball/deepx/ImageCutoutRegressionTest.java \
    tests/com/smallhairball/deepx/HeartbeatToolProtocolRegressionTest.java \
    tests/com/smallhairball/deepx/RichPanelRendererRegressionTest.java \
    tests/com/smallhairball/deepx/AgentRunStoreRegressionTest.java \
    tests/com/smallhairball/deepx/ChatBackupFormatRegressionTest.java \
    tests/com/smallhairball/deepx/DeepSeekCacheCleanerRegressionTest.java \
    tests/com/smallhairball/deepx/RemoteFeatureFlagsRegressionTest.java \
    tests/com/smallhairball/deepx/ProcessManagerRegressionTest.java \
    tests/com/smallhairball/deepx/ReplyReadyPolicyRegressionTest.java \
    tests/com/smallhairball/deepx/AutoContinuePolicyRegressionTest.java \
    src/com/smallhairball/deepx/NativeApiPatchDecoder.java \
    src/com/smallhairball/deepx/SpatialMotionController.java \
    src/com/smallhairball/deepx/SpatialLayerCache.java \
    src/com/smallhairball/deepx/ChatAppearance.java \
    src/com/smallhairball/deepx/ImageCutoutUi.java \
    src/com/smallhairball/deepx/HeartbeatToolProtocol.java \
    src/com/smallhairball/deepx/RichPanelRenderer.java \
    src/com/smallhairball/deepx/AgentRunStore.java \
    src/com/smallhairball/deepx/ChatBackupStore.java \
    src/com/smallhairball/deepx/DeePXTools.java \
    src/com/smallhairball/deepx/AgentToolConfig.java \
    src/com/smallhairball/deepx/AgentQuestionUi.java \
    src/com/smallhairball/deepx/AgentDeviceBridge.java \
    src/com/smallhairball/deepx/ProactiveHeartbeatReceiver.java \
    src/com/smallhairball/deepx/QqMusicPlaybackService.java \
    src/com/smallhairball/deepx/LocalAudioPlaybackService.java \
    src/com/smallhairball/deepx/LocalAudioControlActivity.java \
    src/com/smallhairball/deepx/QqMusicSessionAccessService.java \
    src/com/smallhairball/deepx/HistoryBridge.java \
    src/com/smallhairball/deepx/XposedActivationProvider.java \
    src/com/smallhairball/deepx/XposedActivationReceiver.java \
    src/com/smallhairball/deepx/AccountCredentialCodec.java \
    src/com/smallhairball/deepx/AccountManager.java \
    src/com/smallhairball/deepx/RemoteFeatureFlags.java \
    src/com/smallhairball/deepx/ProcessManagerActivity.java \
    src/com/smallhairball/deepx/ReplyReadyPolicy.java \
    src/com/smallhairball/deepx/AutoContinuePolicy.java \
    src/com/smallhairball/deepx/GoogleLoginUnlock.java \
    src/com/smallhairball/deepx/ResponsePreserver.java \
    src/com/smallhairball/deepx/HostCompat.java \
    tests/tp.java \
    tests/h61.java \
    tests/sl8.java \
    tests/kv.java \
    tests/lq.java \
    tests/kv0.java \
    tests/iu0.java \
    tests/td1.java \
    tests/hv.java \
    tests/x94.java \
    tests/n02.java \
    tests/p64.java \
    tests/c74.java

# Current sources compiled by this run must precede the previous universal APK classes.
# Otherwise Java loads a stale HostCompat/Main from module-universal/build/classes and the
# compatibility regression tests validate yesterday's APK instead of the pending build.
TEST_CP="$JSON_JAR:$ANDROID_JAR:$OUT/classes:$UNIVERSAL_CLASSES:build/classes"

java -cp "$TEST_CP" \
    com.smallhairball.deepx.ChatEditorThinkingRegressionTest

java -cp "$TEST_CP" \
    com.smallhairball.deepx.ChatEditorHistoryImageRegressionTest

java -cp "$TEST_CP" \
    com.smallhairball.deepx.HistoryBridgeRegressionTest

java -cp "$TEST_CP" \
    com.smallhairball.deepx.NativeSessionDeleteRegressionTest

java -cp "$TEST_CP" \
    com.smallhairball.deepx.NativeSessionRefreshRegressionTest

java -cp "$TEST_CP" \
    com.smallhairball.deepx.ResponsePreserverRegressionTest

java -cp "$TEST_CP" \
    com.smallhairball.deepx.AccountCredentialCodecRegressionTest

java -cp "$TEST_CP" \
    com.smallhairball.deepx.AccountServerValidationRegressionTest

java -cp "$TEST_CP" \
    com.smallhairball.deepx.GoogleLoginUnlockRegressionTest

java -cp "$TEST_CP" \
    com.smallhairball.deepx.NativeApiPatchDecoderRegressionTest

java -cp "$TEST_CP" \
    com.smallhairball.deepx.HostCompatRegressionTest

java -cp "$TEST_CP" \
    com.smallhairball.deepx.UiLanguagePolicyRegressionTest

java -cp "$TEST_CP" \
    com.smallhairball.deepx.ChatAppearanceConfigRegressionTest

java -cp "$TEST_CP" \
    com.smallhairball.deepx.ImageCutoutRegressionTest

java -cp "$TEST_CP" \
    com.smallhairball.deepx.HeartbeatToolProtocolRegressionTest

java -cp "$TEST_CP" \
    com.smallhairball.deepx.RichPanelRendererRegressionTest

java -cp "$TEST_CP" \
    com.smallhairball.deepx.AgentRunStoreRegressionTest

java -cp "$TEST_CP" \
    com.smallhairball.deepx.ChatBackupFormatRegressionTest

java -cp "$TEST_CP" \
    com.smallhairball.deepx.DeepSeekCacheCleanerRegressionTest

java -cp "$TEST_CP" \
    com.smallhairball.deepx.RemoteFeatureFlagsRegressionTest

java -cp "$TEST_CP" \
    com.smallhairball.deepx.ProcessManagerRegressionTest

java -cp "$TEST_CP" \
    com.smallhairball.deepx.ReplyReadyPolicyRegressionTest

java -cp "$TEST_CP" \
    com.smallhairball.deepx.AutoContinuePolicyRegressionTest

./test-language-catalog.sh