package com.smallhairball.deepx;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.RandomAccessFile;
/**
 * smspatch — pure-Java, in-process memory patcher for libsmsdk.so's native root detector.
 *
 * <p>The previous implementation shipped a separate {@code libsmspatch.so} loaded through JNI;
 * that library never made it into the universal build (no jniLibs packaging step) and the stale
 * copy left in the app's files directory fails {@code JNI_OnLoad} with {@code JNI_ERR}, so
 * {@code patchRootDetector()} was never callable. This replacement needs no native artifact.
 *
 * <p>{@code sub_4A48C} (boolean, the magisk/KernelSU detector that the Java-side
 * {@code SMSDK.ma()} hook cannot reach) is overwritten at its entry with
 * {@code mov w0, #0 ; ret}; every native caller then observes "no root". The shipped
 * libsmsdk.so maps its first LOAD segment with {@code vaddr == file offset}, so
 * {@code runtime = mappingBase + 0x4A48C} (verified against the AArch64 disassembly:
 * {@code sub sp, sp, #0xb0} + stp prologue at exactly that address).
 *
 * <p>Two delivery paths: a direct /proc/self/mem write (FOLL_FORCE semantics can patch the
 * read-only private .text mapping), and a root-backend fallback
 * ({@code su -c dd ... /proc/<pid>/mem}) mirroring the Agent's privileged shell channel.
 */
final class z22 {
    /** Symbol offset (== virtual address; first LOAD segment: offset 0, vaddr 0). */
    private static final long SYMBOL_OFFSET = 0x4A48CL;
    /** {@code mov w0, #0 ; ret} (AArch64 little endian). */
    private static final byte[] PATCH = {
            0x00, 0x00, (byte) 0x80, 0x52,
            (byte) 0xC0, 0x03, 0x5F, (byte) 0xD6,
    };
    private static final String TARGET_LIB = "libsmsdk.so";
    private static volatile boolean patched;
    private z22() {}
    /** Kept for call-site compatibility; the .so loader is gone. */
    static void initialize() {}
    /** Patches sub_4A48C to return 0 (no root). Returns true when the bytes landed. */
    static boolean patchRootDetector() {
        if (patched) return true;
        long base = findLibraryBase();
        if (base <= 0) {
            log("smspatch: " + TARGET_LIB + " is not mapped yet");
            return false;
        }
        long target = base + SYMBOL_OFFSET;
        if (patchViaSelfMemory(target)) {
            patched = true;
            log("[RISK] sub_4A48C patched via /proc/self/mem @0x" + Long.toHexString(target));
            return true;
        }
        if (patchViaRootBackend(target)) {
            patched = true;
            log("[RISK] sub_4A48C patched via root backend @0x" + Long.toHexString(target));
            return true;
        }
        log("[RISK] sub_4A48C patch failed (self-mem and root paths)");
        return false;
    }
    /** First mapping of libsmsdk.so in /proc/self/maps; its start == ELF load bias. */
    private static long findLibraryBase() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/self/maps"));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.indexOf(TARGET_LIB) < 0) continue;
                    int dash = line.indexOf('-');
                    if (dash <= 0) continue;
                    try {
                        return Long.parseLong(line.substring(0, dash), 16);
                    } catch (NumberFormatException ignored) {
                        // keep scanning
                    }
                }
            } finally {
                try { reader.close(); } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            log("smspatch: maps read failed: " + Main.safeThrowableMessage(t));
        }
        return -1L;
    }
    private static boolean patchViaSelfMemory(long target) {
        try {
            RandomAccessFile memory = new RandomAccessFile("/proc/self/mem", "rw");
            try {
                memory.seek(target);
                memory.write(PATCH);
                try {
                    memory.seek(target);
                    byte[] back = new byte[PATCH.length];
                    memory.readFully(back);
                    for (int i = 0; i < PATCH.length; i++) {
                        if (back[i] != PATCH[i]) return false;
                    }
                } catch (Throwable ignored) {
                    // Read-back can be restricted even when the write succeeded; trust the write.
                }
                return true;
            } finally {
                try { memory.close(); } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            log("smspatch: self-mem write failed: " + Main.safeThrowableMessage(t));
            return false;
        }
    }
    /**
     * Root fallback, replicating the proven manual recipe end to end:
     * {@code echo <b64> | base64 -d | dd of=/proc/<pid>/mem bs=1 seek=<addr> conv=notrunc}
     * followed by a read-back verification ({@code dd ... | base64}) whose output must come back
     * as the patch bytes. Tries the usual su locations, no Context/file needed on this path.
     */
    private static boolean patchViaRootBackend(long target) {
        String pid = String.valueOf(android.os.Process.myPid());
        String address = Long.toString(target);
        String command = "echo AACAUsADX9Y= | base64 -d | dd of=/proc/" + pid
                + "/mem bs=1 seek=" + address + " conv=notrunc 2>/dev/null; "
                + "dd if=/proc/" + pid + "/mem bs=1 skip=" + address
                + " count=8 2>/dev/null | base64";
        try {
            // Reuse the exact channel the Agent's privileged shell already proved on this
            // device: AgentDeviceBridge walks the full su-binary candidate list (including the
            // KernelSU/APatch/Magisk paths reachable from the app namespace) with a sanitized
            // PATH env, instead of trusting a single hard-coded location. Direct
            // /system/bin/su is NOT visible inside the app's mount namespace (KernelSU hides
            // it), which is why the fixed list here used to fail with ENOENT/EACCES.
            Process process = AgentDeviceBridge.startRootProcess(command);
            boolean finished = process.waitFor(
                    6000L, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroy();
                log("smspatch: root patch timed out");
                return false;
            }
            String output = readProcessOutput(process);
            if (output.indexOf("AACAUsADX9Y=") >= 0) return true;
            log("smspatch: root patch verify failed output=" + output.trim());
            return false;
        } catch (Throwable t) {
            log("smspatch: root patch spawn failed: " + Main.safeThrowableMessage(t));
            return false;
        }
    }
    private static String readProcessOutput(Process process) {
        try {
            java.io.InputStream in = process.getInputStream();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[256];
            int count;
            while ((count = in.read(buffer)) >= 0) {
                if (count > 0) out.write(buffer, 0, count);
                if (out.size() > 8192) break;
            }
            return new String(out.toByteArray(), "UTF-8");
        } catch (Throwable ignored) {
            return "";
        }
    }
    private static void log(String message) {
        try { Main.log(message); } catch (Throwable ignored) {}
    }
}