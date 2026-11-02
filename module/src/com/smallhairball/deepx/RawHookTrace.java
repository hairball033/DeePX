package com.smallhairball.deepx;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.lang.reflect.Member;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Opt-in, deliberately unredacted trace for module development.
 *
 * <p>This logger sits below every LegacyXposedModule hook. It is disabled by default and never
 * copies data to shared storage on its own. Values are bounded only to keep one hostile toString()
 * or an infinite stream from exhausting the host process; no credential or content redaction is
 * performed.</p>
 */
final class RawHookTrace {
    static final String FLAG_PATH =
            "/data/data/com.deepseek.chat/files/deekseep_raw_hook_enabled";
    static final String LOG_PATH =
            "/data/data/com.deepseek.chat/files/deekseep_raw_hook.log";
    static final String ROTATED_PATH = LOG_PATH + ".1";
    private static final long MAX_BYTES = 64L * 1024L * 1024L;
    private static final int MAX_VALUE_CHARS = 64 * 1024;
    // Hook callbacks can run on Compose's input/render thread. Queue the raw event shape and do
    // every expensive member/value toString() on the writer thread; the old String queue still
    // expanded whole argument graphs synchronously and caused visible random scroll stalls.
    private static final ArrayBlockingQueue<TraceRecord> QUEUE = new ArrayBlockingQueue<>(8192);
    private static final AtomicLong IDS = new AtomicLong();
    private static final AtomicLong DROPPED = new AtomicLong();
    private static final SimpleDateFormat CLOCK =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private static volatile boolean workerStarted;
    private static volatile boolean cachedEnabled;
    private static volatile long checkedAt;

    private RawHookTrace() {}

    private static final class TraceRecord {
        static final int REGISTER = 1;
        static final int ENTER = 2;
        static final int EXIT = 3;
        static final int THROW = 4;
        static final int MODULE = 5;

        final int kind;
        final long createdAt;
        final int pid;
        final String thread;
        final long id;
        final Member member;
        final Object receiver;
        final Object[] args;
        final Object result;
        final long durationNanos;
        final String message;

        TraceRecord(int kind, long id, Member member, Object receiver, Object[] args,
                Object result, long durationNanos, String message) {
            this.kind = kind;
            this.createdAt = System.currentTimeMillis();
            this.pid = android.os.Process.myPid();
            this.thread = Thread.currentThread().getName();
            this.id = id;
            this.member = member;
            this.receiver = receiver;
            this.args = args == null ? null : args.clone();
            this.result = result;
            this.durationNanos = durationNanos;
            this.message = message;
        }
    }

    static boolean enabled() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - checkedAt > 500L) {
            cachedEnabled = new File(FLAG_PATH).isFile();
            checkedAt = now;
        }
        return cachedEnabled;
    }

    static synchronized boolean setEnabled(boolean value) {
        try {
            File flag = new File(FLAG_PATH);
            if (value) {
                File parent = flag.getParentFile();
                if (parent != null && !parent.isDirectory()) parent.mkdirs();
                try (FileOutputStream output = new FileOutputStream(flag, false)) {
                    output.write("UNREDACTED\n".getBytes(StandardCharsets.UTF_8));
                    output.flush();
                }
                cachedEnabled = true;
                checkedAt = android.os.SystemClock.elapsedRealtime();
                startWorker();
                enqueue(new TraceRecord(TraceRecord.MODULE, 0L,
                        null, null, null, null, 0L,
                        "! WARNING: UNREDACTED HIGH-FREQUENCY TRACE ENABLED; messages, "
                                + "arguments, return values, paths, account data and credentials may appear"));
            } else {
                if (flag.exists() && !flag.delete()) return false;
                cachedEnabled = false;
                checkedAt = android.os.SystemClock.elapsedRealtime();
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static void registered(Member member) {
        if (!enabled()) return;
        enqueue(new TraceRecord(TraceRecord.REGISTER, 0L, member,
                null, null, null, 0L, null));
    }

    static long enter(Member member, Object receiver, Object[] args) {
        if (!enabled()) return 0L;
        long id = IDS.incrementAndGet();
        enqueue(new TraceRecord(TraceRecord.ENTER, id, member,
                receiver, args, null, 0L, null));
        return id;
    }

    static void exit(long id, Member member, Object result, long durationNanos) {
        if (id == 0L) return;
        enqueue(new TraceRecord(TraceRecord.EXIT, id, member,
                null, null, result, durationNanos, null));
    }

    static void failed(long id, Member member, Throwable error, long durationNanos) {
        if (id == 0L) return;
        enqueue(new TraceRecord(TraceRecord.THROW, id, member,
                null, null, error, durationNanos, null));
    }

    static void module(String message) {
        if (enabled()) enqueue(new TraceRecord(TraceRecord.MODULE, 0L,
                null, null, null, null, 0L, message));
    }

    static synchronized boolean clear() {
        try {
            QUEUE.clear();
            boolean ok = true;
            File current = new File(LOG_PATH);
            File rotated = new File(ROTATED_PATH);
            if (current.exists()) ok &= current.delete();
            if (rotated.exists()) ok &= rotated.delete();
            if (enabled()) enqueue(new TraceRecord(TraceRecord.MODULE, 0L,
                    null, null, null, null, 0L,
                    "! TRACE CLEARED while capture remains enabled"));
            return ok;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void enqueue(TraceRecord event) {
        startWorker();
        if (!QUEUE.offer(event)) DROPPED.incrementAndGet();
    }

    private static synchronized void startWorker() {
        if (workerStarted) return;
        workerStarted = true;
        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                while (true) {
                    try {
                        ArrayList<TraceRecord> batch = new ArrayList<>(256);
                        batch.add(QUEUE.take());
                        QUEUE.drainTo(batch, 255);
                        long dropped = DROPPED.getAndSet(0L);
                        writeBatch(batch, dropped);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        return;
                    } catch (Throwable ignored) {}
                }
            }
        }, "DeePX-Raw-Hook-Trace");
        worker.setDaemon(true);
        worker.start();
    }

    private static synchronized void writeBatch(ArrayList<TraceRecord> batch, long dropped)
            throws Exception {
        File file = new File(LOG_PATH);
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();
        if (file.isFile() && file.length() >= MAX_BYTES) {
            File rotated = new File(ROTATED_PATH);
            if (rotated.exists()) rotated.delete();
            file.renameTo(rotated);
        }
        try (FileWriter writer = new FileWriter(file, true)) {
            if (dropped > 0L) writer.write("! DROPPED " + dropped
                    + " trace records because the bounded writer queue was full\n");
            for (TraceRecord record : batch) writer.write(format(record));
        }
    }

    private static String format(TraceRecord record) {
        StringBuilder line = new StringBuilder(320);
        synchronized (CLOCK) {
            line.append(CLOCK.format(new Date(record.createdAt)));
        }
        line.append(" pid=").append(record.pid).append(' ');
        if (record.kind == TraceRecord.REGISTER) {
            line.append("REGISTER member=").append(memberText(record.member));
        } else if (record.kind == TraceRecord.ENTER) {
            line.append("ENTER id=").append(record.id)
                    .append(" thread=").append(record.thread)
                    .append(" member=").append(memberText(record.member))
                    .append(" receiver=").append(value(record.receiver)).append(" args=[");
            if (record.args != null) for (int i = 0; i < record.args.length; i++) {
                if (i > 0) line.append(", ");
                line.append(i).append('=').append(value(record.args[i]));
            }
            line.append(']');
        } else if (record.kind == TraceRecord.EXIT || record.kind == TraceRecord.THROW) {
            line.append(record.kind == TraceRecord.EXIT ? "EXIT" : "THROW")
                    .append(" id=").append(record.id)
                    .append(" durationUs=")
                    .append(TimeUnit.NANOSECONDS.toMicros(record.durationNanos))
                    .append(" member=").append(memberText(record.member))
                    .append(record.kind == TraceRecord.EXIT ? " result=" : " error=")
                    .append(value(record.result));
        } else {
            line.append("MODULE ").append(bounded(record.message));
        }
        return line.append('\n').toString();
    }

    private static String memberText(Member member) {
        try { return bounded(String.valueOf(member)); }
        catch (Throwable error) { return "<member-toString-failed:" + error.getClass().getName() + ">"; }
    }

    private static String value(Object value) {
        if (value == null) return "null";
        try {
            return value.getClass().getName() + "{" + bounded(String.valueOf(value)) + "}";
        } catch (Throwable error) {
            return value.getClass().getName() + "{<toString-failed:"
                    + error.getClass().getName() + ">}";
        }
    }

    private static String bounded(String value) {
        String safe = value == null ? "null" : value;
        return safe.length() <= MAX_VALUE_CHARS ? safe
                : safe.substring(0, MAX_VALUE_CHARS) + "<TRUNCATED_FOR_SIZE>";
    }
}
