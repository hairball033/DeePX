package com.smallhairball.deepx;

import android.app.Activity;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Session state for the "执行" (task console) page while it is rendered by the Compose/Miuix layer.
 *
 * <p>The legacy page streams its output into a {@code TextView} it owns. The Miuix page instead
 * polls {@link #snapshot()} from Compose, so streaming stays process-local and nothing has to cross
 * the reflective boundary between the module and the merged compose-launcher artifact. Only one
 * session can be live at a time, which matches the single full-screen console the legacy code
 * showed.
 */
public final class TaskConsoleBridge {

    /** Receives one output line of the running job. */
    public interface Sink {
        void line(String value);
    }

    /** The one-shot job rendered by the console; returns the optional result text. */
    public interface Runner {
        String run(Sink sink) throws Throwable;
    }

    /** Immutable view of the console session, consumed by the Compose page. */
    public static final class Snapshot {
        public final long generation;
        public final String title;
        public final String hint;
        public final String log;
        public final String result;
        public final String savedPath;
        public final boolean running;
        public final boolean complete;
        public final boolean failed;

        Snapshot(long generation, String title, String hint, String log, String result,
                String savedPath, boolean running, boolean complete, boolean failed) {
            this.generation = generation;
            this.title = title;
            this.hint = hint;
            this.log = log;
            this.result = result;
            this.savedPath = savedPath;
            this.running = running;
            this.complete = complete;
            this.failed = failed;
        }

        public boolean hasResult() {
            return result != null && result.trim().length() > 0;
        }
    }

    /** Screen-clear escape the native console understands; it resets the whole buffer. */
    private static final String CLEAR_ESCAPE = "\u001B[H\u001B[J";

    private static final Object LOCK = new Object();
    private static final StringBuilder LOG = new StringBuilder();
    private static long generation;
    private static String title = "";
    private static String hint = "";
    private static String result = "";
    private static String savedPath = "";
    private static boolean running;
    private static boolean complete;
    private static boolean failed;

    private TaskConsoleBridge() {}

    /**
     * Clears the buffer and runs {@code runner} on a background thread, streaming every line into
     * the shared log. Safe to call from the UI thread; returns immediately.
     */
    public static void start(final String rawTitle, final String rawHint, final Runner runner) {
        if (runner == null) return;
        final String englishTitle = UiLanguageCatalog.toEnglish(rawTitle == null ? "" : rawTitle);
        final String englishHint = UiLanguageCatalog.toEnglish(rawHint == null ? "" : rawHint);
        synchronized (LOCK) {
            LOG.setLength(0);
            title = rawTitle == null ? "" : rawTitle;
            hint = rawHint == null ? "" : rawHint;
            result = "";
            savedPath = "";
            running = true;
            complete = false;
            failed = false;
            generation++;
        }
        new Thread(new Runnable() {
            @Override public void run() {
                if (englishTitle.length() > 0) emit("Task: " + englishTitle);
                if (englishHint.length() > 0) emit("Note: " + englishHint);
                emit("Execution started.");
                try {
                    String value = runner.run(new Sink() {
                        @Override public void line(String text) {
                            emit(text);
                        }
                    });
                    emit("Execution completed successfully.");
                    synchronized (LOCK) {
                        if (value != null) result = value;
                    }
                } catch (Throwable error) {
                    String message = error.getMessage();
                    if (message == null || message.trim().length() == 0) {
                        message = error.getClass().getSimpleName();
                    }
                    emit("Execution failed: " + message);
                    synchronized (LOCK) {
                        failed = true;
                        result = message;
                    }
                } finally {
                    synchronized (LOCK) {
                        running = false;
                        complete = true;
                    }
                }
            }
        }, "DeePX-task-console").start();
    }

    /** Snapshot for the Compose page; cheap enough to poll while the job runs. */
    public static Snapshot snapshot() {
        synchronized (LOCK) {
            return new Snapshot(generation, title, hint, LOG.toString(), result, savedPath,
                    running, complete, failed);
        }
    }

    /** Drops the session so a later page does not show stale output. */
    public static void reset() {
        synchronized (LOCK) {
            LOG.setLength(0);
            title = "";
            hint = "";
            result = "";
            savedPath = "";
            running = false;
            complete = false;
            failed = false;
            generation++;
        }
    }

    /** Writes the session log to {@code deekseep/task_logs} and toasts the path. */
    public static boolean saveLog(Activity activity) {
        String path = writeSessionLog(activity);
        if (path != null) {
            Toast.makeText(activity, "Log saved: " + path, Toast.LENGTH_LONG).show();
            return true;
        }
        Toast.makeText(activity, "Failed to save log", Toast.LENGTH_LONG).show();
        return false;
    }

    /** Writes the session log, returning the file path or {@code null} on failure. */
    public static String writeSessionLog(Activity activity) {
        String snapshot;
        synchronized (LOCK) {
            snapshot = LOG.toString();
        }
        return writeLogFile(activity, snapshot);
    }

    /**
     * Writes {@code body} to a timestamped file under {@code deekseep/task_logs}. Shared with the
     * legacy View console so both paths produce identical files.
     */
    public static String writeLogFile(Activity activity, String body) {
        if (activity == null) return null;
        try {
            File external = activity.getExternalFilesDir(null);
            File root = new File(external != null ? external : activity.getFilesDir(),
                    "deekseep/task_logs");
            if (!root.exists() && !root.mkdirs()) throw new IllegalStateException("mkdir failed");
            File output = new File(root, "task_"
                    + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date())
                    + ".log");
            FileOutputStream stream = new FileOutputStream(output, false);
            try {
                stream.write((body == null ? "" : body).getBytes(StandardCharsets.UTF_8));
                stream.flush();
            } finally {
                stream.close();
            }
            synchronized (LOCK) {
                savedPath = output.getPath();
            }
            return output.getPath();
        } catch (Throwable error) {
            return null;
        }
    }

    /** Appends one line, honouring the console's clear-screen escape. */
    private static void emit(String value) {
        final String safe = UiLanguageCatalog.toEnglish(value == null ? "" : value);
        synchronized (LOCK) {
            if (safe.startsWith(CLEAR_ESCAPE)) {
                LOG.setLength(0);
                LOG.append(safe.substring(CLEAR_ESCAPE.length()));
            } else {
                LOG.append(safe).append('\n');
            }
        }
    }
}
