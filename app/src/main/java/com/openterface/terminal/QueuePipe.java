package com.openterface.terminal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe unidirectional byte pipe built on a blocking queue.
 *
 * Java's PipedInputStream tracks the last thread that wrote to its paired
 * PipedOutputStream. When that thread terminates, subsequent reads throw
 * "Write end dead". This is fatal for JSch's architecture: the SSH handshake
 * writes from a transient "connect" thread that exits mid-session.
 *
 * QueuePipe replaces the Piped streams with a LinkedBlockingQueue and custom
 * InputStream/OutputStream adapters. Liveness no longer depends on any
 * particular thread surviving.
 *
 * Convention: a zero-length byte[] in the queue is the EOF sentinel. The
 * writer rejects actual zero-length writes (a safe assumption for SSH traffic),
 * so a zero-length entry can only be pushed by close()/signalEndOfStream().
 *
 * Each instance represents ONE direction. BleEthTransport uses two:
 *   - inboundPipe  : transport writes -&gt; JSch reads
 *   - outboundPipe : JSch writes -&gt; reader thread reads &amp; sends via BLE
 */
public class QueuePipe {

    private final LinkedBlockingQueue<byte[]> queue;
    private final PipeInputStream inputStream;
    private final PipeOutputStream outputStream;
    private volatile boolean closed = false;

    public QueuePipe(int capacity) {
        this.queue = new LinkedBlockingQueue<>(capacity);
        this.inputStream = new PipeInputStream();
        this.outputStream = new PipeOutputStream();
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }

    public void close() {
        if (closed) return;
        closed = true;
        signalEndOfStream();
    }

    public boolean isClosed() {
        return closed;
    }

    /**
     * Push the EOF sentinel so any blocked reader unblocks and returns -1.
     */
    public void signalEndOfStream() {
        try {
            queue.offer(new byte[0]);
        } catch (Throwable ignored) {
        }
    }

    // --- reading end ---

    private class PipeInputStream extends InputStream {
        private byte[] leftover;
        private int leftoverPos;

        @Override
        public int read() throws IOException {
            byte[] single = new byte[1];
            int n = read(single, 0, 1);
            return n < 0 ? -1 : (single[0] & 0xFF);
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            if (buf == null) throw new NullPointerException();
            if (off < 0 || len < 0 || off + len > buf.length) {
                throw new IndexOutOfBoundsException();
            }
            if (len == 0) return 0;

            // 1) Drain any leftover from a previous read.
            if (leftover != null) {
                int toCopy = Math.min(len, leftover.length - leftoverPos);
                System.arraycopy(leftover, leftoverPos, buf, off, toCopy);
                leftoverPos += toCopy;
                if (leftoverPos >= leftover.length) {
                    leftover = null;
                    leftoverPos = 0;
                }
                return toCopy;
            }

            // 2) Block waiting for data.
            byte[] data;
            try {
                data = queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Read interrupted", e);
            }

            // 3) EOF sentinel (zero-length entry).
            if (data == null || data.length == 0) {
                return -1;
            }

            // 4) Copy into caller's buffer; stash any remainder.
            int toCopy = Math.min(len, data.length);
            System.arraycopy(data, 0, buf, off, toCopy);
            if (toCopy < data.length) {
                leftover = data;
                leftoverPos = toCopy;
            }
            return toCopy;
        }

        @Override
        public int available() {
            int n = queue.size();
            if (leftover != null) n += (leftover.length - leftoverPos);
            return n;
        }

        @Override
        public void close() {
            // Closing the input side clears the queue so the writer doesn't block forever.
            queue.clear();
        }
    }

    // --- writing end ---

    private class PipeOutputStream extends OutputStream {
        private volatile boolean writerClosed = false;

        @Override
        public void write(int b) throws IOException {
            write(new byte[]{(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] buf, int off, int len) throws IOException {
            if (writerClosed) {
                throw new IOException("Pipe output stream closed");
            }
            if (buf == null) throw new NullPointerException();
            if (off < 0 || len < 0 || off + len > buf.length) {
                throw new IndexOutOfBoundsException();
            }
            if (len == 0) return; // Zero-length writes are rejected; zero-length = EOF sentinel.

            byte[] copy = new byte[len];
            System.arraycopy(buf, off, copy, 0, len);

            try {
                // Use offer() with timeout so we can check closed flag periodically.
                // This prevents deadlock when the reader has disappeared.
                while (!writerClosed) {
                    if (queue.offer(copy, 1, TimeUnit.SECONDS)) {
                        return;
                    }
                }
                throw new IOException("Pipe output stream closed");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Write interrupted", e);
            }
        }

        @Override
        public void close() throws IOException {
            writerClosed = true;
            signalEndOfStream();
        }

        @Override
        public void flush() throws IOException {
            // No-op: queue entries are visible to the reader immediately on put().
        }
    }
}
