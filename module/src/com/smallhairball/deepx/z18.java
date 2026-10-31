package com.smallhairball.deepx;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Applies only Android's documented idle/standby/app-op exemptions to the DeepSeek package. */
final class z18 {
    private static final String PACKAGE = "com.deepseek.chat";
    // Apps do not inherit the interactive shell's PATH.  On ColorOS/realme UI this made a
    // bare "su" lookup fail with IOException(error=2), even though the manager's Root binary
    // is available at /system/bin/su.  Probe the stable Android manager locations explicitly.
    private static final String[] SU_BINARIES = new String[]{
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/debug_ramdisk/su",
            "/data/adb/ksu/bin/su", "/data/adb/ap/bin/su", "/data/adb/magisk/su"
    };
    private static volatile String selectedSu;

    private z18() {}

    static String apply() throws Exception {
        Main.log("root whitelist probe begin appUid=" + android.os.Process.myUid()
                + " package=" + PACKAGE);
        Result identity = root("/system/bin/id -u; /system/bin/id");
        if (identity.code != 0 || !isRootIdentity(identity.output)) {
            Main.log("root whitelist probe rejected exit=" + identity.code
                    + " su=" + printableSu(identity.binary)
                    + " identity=" + diagnostic(identity.output));
            throw new SecurityException("未获得 Root 身份（exit=" + identity.code
                    + "，uid=" + diagnostic(identity.output)
                    + "）；请确认 Root 管理器授权的是 DeepSeek 应用进程");
        }
        selectedSu = identity.binary;
        Main.log("root whitelist probe accepted su=" + printableSu(selectedSu)
                + " identity=" + diagnostic(identity.output));
        String[] commands = new String[]{
                "cmd deviceidle whitelist +" + PACKAGE,
                "am set-inactive " + PACKAGE + " false",
                "am set-standby-bucket " + PACKAGE + " active",
                "cmd appops set " + PACKAGE + " RUN_IN_BACKGROUND allow",
                "cmd appops set " + PACKAGE + " RUN_ANY_IN_BACKGROUND allow"
        };
        List<String> failures = new ArrayList<String>();
        for (String command : commands) {
            Result result = root(command);
            if (result.code != 0) failures.add(shortCommand(command) + ": " + result.output);
        }
        Result allowlist = root("cmd deviceidle whitelist");
        Result bucket = root("am get-standby-bucket " + PACKAGE);
        boolean allowlisted = allowlist.output.contains(PACKAGE);
        String bucketValue = bucket.output.trim();
        boolean active = bucket.code == 0
                && (bucketValue.contains("active") || "10".equals(bucketValue));
        StringBuilder result = new StringBuilder();
        result.append(allowlisted
                        ? "系统白名单添加成功\n后台限制设置完成\n系统白名单："
                        : "后台限制设置完成\n系统白名单：")
                .append(allowlisted ? "已验证" : "未验证")
                .append("\n待机分组：")
                .append(active ? "Active" : (bucketValue.length() == 0 ? "未知" : bucketValue));
        if (!failures.isEmpty()) {
            result.append("\n部分厂商系统不支持：");
            for (String failure : failures) result.append("\n• ").append(failure);
        }
        result.append("\n这不会绕过厂商任务清理；仍建议在系统电池设置中选择“不限制”。");
        return result.toString();
    }

    private static String shortCommand(String command) {
        int space = command.indexOf(' ');
        return space < 0 ? command : command.substring(0, Math.min(command.length(), space + 22));
    }

    private static Result root(String command) throws Exception {
        StartedRoot started = startRoot(command);
        Process process = started.process;
        boolean finished = process.waitFor(8, TimeUnit.SECONDS);
        if (!finished) {
            process.destroy();
            Main.log("root command timeout su=" + printableSu(started.binary)
                    + " command=" + shortCommand(command));
            throw new java.io.IOException("Root 命令超时：" + shortCommand(command));
        }
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), "UTF-8"));
        StringBuilder output = new StringBuilder();
        try {
            String line;
            while ((line = reader.readLine()) != null && output.length() < 1200) {
                if (output.length() > 0) output.append('\n');
                output.append(line);
            }
        } finally {
            reader.close();
        }
        Result result = new Result(process.exitValue(), output.toString().trim(), started.binary);
        Main.log("root command finished su=" + printableSu(started.binary)
                + " command=" + shortCommand(command)
                + " exit=" + result.code + " output=" + diagnostic(result.output));
        return result;
    }

    private static StartedRoot startRoot(String command) throws java.io.IOException {
        java.io.IOException last = null;
        if (selectedSu != null && selectedSu.length() > 0) {
            try {
                return new StartedRoot(new ProcessBuilder(selectedSu, "-c", command)
                        .redirectErrorStream(true).start(), selectedSu);
            } catch (java.io.IOException error) {
                Main.log("cached root binary failed su=" + printableSu(selectedSu)
                        + " error=" + error.getClass().getSimpleName());
                last = error;
                selectedSu = null;
            }
        }
        for (String candidate : SU_BINARIES) {
            try {
                // Do not pre-filter with File.canExecute(): namespace-mounted su binaries can be
                // executable by ProcessBuilder while Java's access check reports false.
                Process process = new ProcessBuilder(candidate, "-c", command)
                        .redirectErrorStream(true).start();
                return new StartedRoot(process, candidate);
            } catch (java.io.IOException error) {
                Main.log("root binary unavailable su=" + candidate
                        + " error=" + error.getClass().getSimpleName()
                        + ":" + String.valueOf(error.getMessage()));
                last = error;
            }
        }
        // Some root managers expose su only through a private PATH entry. Keep this fallback,
        // but turn the old unhelpful error=2 into an actionable message when it is absent.
        try {
            return new StartedRoot(new ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true).start(), "PATH:su");
        } catch (java.io.IOException error) {
            if (last != null) error.addSuppressed(last);
            throw new java.io.IOException(
                    "未找到可执行的 Root 命令；请在 Root 管理器中授予 DeepSeek 权限", error);
        }
    }

    private static boolean isRootIdentity(String value) {
        String text = value == null ? "" : value;
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            if ("0".equals(line.trim())) return true;
        }
        return text.matches("(?s).*(^|\\s)uid=0(?:\\D|$).*");
    }

    private static String diagnostic(String value) {
        String text = value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
        if (text.length() > 240) text = text.substring(0, 240) + "…";
        return text.length() == 0 ? "<empty>" : text;
    }

    private static String printableSu(String value) {
        return value == null || value.length() == 0 ? "<unresolved>" : value;
    }

    private static final class StartedRoot {
        final Process process;
        final String binary;

        StartedRoot(Process process, String binary) {
            this.process = process;
            this.binary = binary;
        }
    }

    private static final class Result {
        final int code;
        final String output;
        final String binary;

        Result(int code, String output, String binary) {
            this.code = code;
            this.output = output == null ? "" : output;
            this.binary = binary == null ? "" : binary;
        }
    }
}
