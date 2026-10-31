package com.smallhairball.deepx;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Decouples native model callbacks from the client socket and emits queued SSE frames at a stable
 * cadence. DeepSeek can resume a Flow with hundreds of small deltas in one scheduler turn; writing
 * those callbacks directly to the socket produces a visible stall followed by one large burst.
 *
 * <p>The queue is deliberately bounded. A temporarily slow Agent receives buffered frames in
 * order, while a permanently blocked or disconnected socket eventually applies backpressure
 * instead of consuming unbounded process memory. Exactly one writer owns the real OutputStream,
 * so heartbeat, content, tool and terminal frames cannot interleave at byte level.</p>
 */
public final class z9 extends OutputStream {
    static final long DEFAULT_FRAME_GAP_MS = 16L;
    static final int DEFAULT_QUEUE_CAPACITY = 2048;
    private static final long ENQUEUE_POLL_MS = 250L;
    private static final long DRAIN_TIMEOUT_MS = 45_000L;

    private static final Frame END = new Frame(new byte[0], true);

    private final OutputStream downstream;
    private final long frameGapNanos;
    private final ArrayBlockingQueue<Frame> queue;
    private final Thread writer;
    private volatile IOException failure;
    private volatile boolean finishing;
    private volatile boolean finished;

    public z9(OutputStream downstream, String requestId) {
        this(downstream, requestId, DEFAULT_FRAME_GAP_MS, DEFAULT_QUEUE_CAPACITY);
    }

    public z9(OutputStream downstream, String requestId,
                     long frameGapMs, int queueCapacity) {
        if (downstream == null) throw new NullPointerException("downstream");
        this.downstream = downstream;
        this.frameGapNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0L, frameGapMs));
        this.queue = new ArrayBlockingQueue<Frame>(Math.max(8, queueCapacity));
        String suffix = requestId == null ? "stream" : requestId;
        this.writer = new Thread(new Runnable() {
            @Override public void run() { drainLoop(); }
        }, "DeePX-SSE-Pacer-" + suffix);
        this.writer.setDaemon(true);
        this.writer.start();
    }

    @Override public void write(int value) throws IOException {
        write(new byte[] {(byte) value}, 0, 1);
    }

    @Override public void write(byte[] value, int offset, int length) throws IOException {
        if (value == null) throw new NullPointerException("value");
        if (offset < 0 || length < 0 || offset + length > value.length) {
            throw new IndexOutOfBoundsException();
        }
        if (length == 0) return;
        enqueue(new Frame(Arrays.copyOfRange(value, offset, offset + length), false));
    }

    /** Enqueueing a complete SSE frame is the flush boundary; the writer flushes every frame. */
    @Override public void flush() throws IOException {
        throwIfFailed();
    }

    public boolean isDisconnected() {
        return failure != null;
    }

    IOException failure() {
        return failure;
    }

    /** Stops accepting frames and waits until all previously accepted frames reach the socket. */
    void finishAndAwait() throws IOException {
        synchronized (this) {
            if (!finishing) {
                finishing = true;
                enqueueTerminal();
            }
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(DRAIN_TIMEOUT_MS);
        while (writer.isAlive()) {
            long left = deadline - System.nanoTime();
            if (left <= 0L) {
                fail(new IOException("timed out draining paced SSE frames"));
                try { downstream.close(); } catch (Throwable ignored) {}
                writer.interrupt();
                break;
            }
            try {
                writer.join(Math.max(1L, Math.min(1000L,
                        TimeUnit.NANOSECONDS.toMillis(left))));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                fail(new IOException("interrupted while draining paced SSE frames", interrupted));
                writer.interrupt();
                break;
            }
        }
        throwIfFailed();
    }

    private void enqueue(Frame frame) throws IOException {
        if (finishing || finished) throw new IOException("paced SSE stream is finishing");
        while (true) {
            throwIfFailed();
            try {
                if (queue.offer(frame, ENQUEUE_POLL_MS, TimeUnit.MILLISECONDS)) return;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while queueing SSE frame", interrupted);
            }
        }
    }

    private void enqueueTerminal() throws IOException {
        while (writer.isAlive()) {
            throwIfFailed();
            try {
                if (queue.offer(END, ENQUEUE_POLL_MS, TimeUnit.MILLISECONDS)) return;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while closing SSE queue", interrupted);
            }
        }
        throwIfFailed();
    }

    private void drainLoop() {
        long nextWriteAt = 0L;
        try {
            while (true) {
                Frame frame = queue.take();
                if (frame.terminal) break;
                waitUntil(nextWriteAt);
                downstream.write(frame.bytes);
                downstream.flush();
                nextWriteAt = System.nanoTime() + frameGapNanos;
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            if (!finishing) fail(new IOException("paced SSE writer interrupted", interrupted));
        } catch (IOException io) {
            fail(io);
        } catch (Throwable failure) {
            fail(new IOException("paced SSE writer failed", failure));
        } finally {
            finished = true;
            queue.clear();
        }
    }

    private static void waitUntil(long targetNanos) throws InterruptedException {
        while (targetNanos > 0L) {
            long remaining = targetNanos - System.nanoTime();
            if (remaining <= 0L) return;
            long millis = TimeUnit.NANOSECONDS.toMillis(remaining);
            int nanos = (int) (remaining - TimeUnit.MILLISECONDS.toNanos(millis));
            Thread.sleep(millis, nanos);
        }
    }

    private void fail(IOException error) {
        if (failure == null) failure = error;
    }

    private void throwIfFailed() throws IOException {
        IOException error = failure;
        if (error != null) throw error;
    }

    private static final class Frame {
        final byte[] bytes;
        final boolean terminal;
        Frame(byte[] bytes, boolean terminal) {
            this.bytes = bytes;
            this.terminal = terminal;
        }
    }
}
