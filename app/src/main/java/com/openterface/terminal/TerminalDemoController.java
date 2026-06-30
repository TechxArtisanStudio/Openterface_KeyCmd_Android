package com.openterface.terminal;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Feeds a canned ANSI terminal session for marketing demos (no SSH / transport).
 * USB mode uses large chunks; BLE mode uses small chunks with longer delays.
 */
public final class TerminalDemoController {

    public enum DemoTransport {
        USB,
        BLE
    }

    public interface Listener {
        void onOutputAppended();

        void onFinished();
    }

    public static final String DEMO_HOST = CredentialManager.DEFAULT_KEYCMD_HOST;

    private static final int USB_CHUNK_BYTES = 256;
    private static final int USB_DELAY_MS = 6;
    private static final int BLE_CHUNK_BYTES = 4;
    private static final int BLE_DELAY_MS = 85;
    private static final int BLE_START_DELAY_MS = 450;

    private static final String ASSET_USB = "terminal/demo_usb.ansi";
    private static final String ASSET_BLE = "terminal/demo_ble.ansi";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean running;
    @Nullable
    private Thread feedThread;
    @Nullable
    private DemoTransport currentTransport;

    public void start(
            @NonNull Context context,
            @NonNull TerminalSession session,
            @NonNull DemoTransport transport,
            @NonNull Listener listener) {
        stop();
        running = true;
        currentTransport = transport;

        byte[] script = loadScript(context, transport);
        int chunkSize = transport == DemoTransport.USB ? USB_CHUNK_BYTES : BLE_CHUNK_BYTES;
        int delayMs = transport == DemoTransport.USB ? USB_DELAY_MS : BLE_DELAY_MS;
        int startDelayMs = transport == DemoTransport.BLE ? BLE_START_DELAY_MS : 120;

        feedThread = new Thread(() -> {
            try {
                Thread.sleep(startDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            for (int offset = 0; offset < script.length && running; ) {
                int len = Math.min(chunkSize, script.length - offset);
                byte[] chunk = Arrays.copyOfRange(script, offset, offset + len);
                session.append(chunk, len);
                offset += len;
                mainHandler.post(listener::onOutputAppended);
                if (offset >= script.length || !running) {
                    break;
                }
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            if (running) {
                mainHandler.post(listener::onFinished);
            }
        }, "TerminalDemoFeed");
        feedThread.start();
    }

    public void stop() {
        running = false;
        if (feedThread != null) {
            feedThread.interrupt();
            feedThread = null;
        }
        currentTransport = null;
    }

    public boolean isRunning() {
        return running;
    }

    @Nullable
    public DemoTransport getCurrentTransport() {
        return currentTransport;
    }

    private static byte[] loadScript(@NonNull Context context, @NonNull DemoTransport transport) {
        String asset = transport == DemoTransport.USB ? ASSET_USB : ASSET_BLE;
        try (InputStream in = context.getAssets().open(asset)) {
            byte[] data = readAll(in);
            if (data.length > 0) {
                return data;
            }
        } catch (IOException ignored) {
            // Fall back to embedded script.
        }
        return buildEmbeddedDemoScript();
    }

    private static byte[] readAll(@NonNull InputStream in) throws IOException {
        byte[] buffer = new byte[4096];
        int read;
        int total = 0;
        while ((read = in.read(buffer, total, buffer.length - total)) != -1) {
            total += read;
            if (total == buffer.length) {
                buffer = Arrays.copyOf(buffer, buffer.length * 2);
            }
        }
        return Arrays.copyOf(buffer, total);
    }

    /** Shared demo content when asset files are missing. */
    @NonNull
    static byte[] buildEmbeddedDemoScript() {
        StringBuilder sb = new StringBuilder();
        sb.append("\033[2J\033[H");
        sb.append("\033[1;32m  Openterface KeyCmd Terminal\r\n\033[0m");
        sb.append("\033[90m  ────────────────────────────────────────\r\n\r\n\033[0m");
        sb.append("\033[33m  Last login: Fri Jun 26 10:42:15 2026 from keymod.local\r\n\r\n\033[0m");
        sb.append("\033[1;37mroot@openterface\033[0m:\033[1;34m~\033[0m# uname -a\r\n");
        sb.append("Linux openterface 6.1.0-keymod #1 SMP aarch64 GNU/Linux\r\n\r\n");
        sb.append("\033[1;37mroot@openterface\033[0m:\033[1;34m~\033[0m# ls --color=auto\r\n");
        sb.append("\033[1;34mbin\033[0m  \033[1;34mdev\033[0m  \033[1;34metc\033[0m  \033[1;34mhome\033[0m  ");
        sb.append("\033[1;34mlib\033[0m  \033[1;34mproc\033[0m  \033[1;34mroot\033[0m  \033[1;34msys\033[0m  ");
        sb.append("\033[36mtmp\033[0m  \033[1;34musr\033[0m  \033[1;34mvar\033[0m\r\n\r\n");
        sb.append("\033[1;37mroot@openterface\033[0m:\033[1;34m~\033[0m# \033[?25h");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
