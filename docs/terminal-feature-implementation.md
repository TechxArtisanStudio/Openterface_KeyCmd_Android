# Terminal Feature Implementation Plan

## 1. Overview

Add a "Terminal" item to the sidebar navigation. When the user taps it, a `TerminalFragment` is shown containing an interactive terminal emulator. The terminal establishes SSH connections to a target machine via one of two transport paths:

| Transport | Description | When Available |
|-----------|-------------|----------------|
| **USB ECM Bridge** | TCP tunnel over USB CDC ECM interface (USB Ethernet) | Device in Mode 4 (HID+ECM Bridge) |
| **BLE-Eth Tunnel** | TCP tunnel over BLE GATT characteristics (BLE Ethernet) | Device connected via BLE in bridge mode |

The SSH client runs natively on Android — no local proxy process is needed. The architecture mirrors the Python `ble_eth_tunnel.py` proxy logic but is ported to Java and integrated directly into the app.

### Architecture Diagram

```
┌──────────────────────────────────────────────────────┐
│  TerminalFragment                                    │
│  ┌────────────────────────────────────────────────┐  │
│  │  TerminalView (emulator)                       │  │
│  │  - Renders ANSI escape sequences               │  │
│  │  - Handles keyboard input                      │  │
│  └──────────────────────┬─────────────────────────┘  │
│                         │                             │
│  ┌──────────────────────▼─────────────────────────┐  │
│  │  SSHClient (SSH-2.0 protocol)                  │  │
│  │  - Key exchange, encryption                    │  │
│  │  - Channel management                          │  │
│  │  - PTY allocation                              │  │
│  └──────────────────────┬─────────────────────────┘  │
│                         │ raw TCP bytes              │
│  ┌──────────────────────▼─────────────────────────┐  │
│  │  TransportAdapter  (abstract interface)         │  │
│  │  ┌─────────────────┐  ┌──────────────────────┐ │  │
│  │  │ UsbEcmTransport │  │ BleEthTransport      │ │  │
│  │  │  (USB ECM)      │  │  (BLE GATT tunnel)   │ │  │
│  │  └─────────────────┘  └──────────────────────┘ │  │
│  └────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────┘
         │                          │
    USB CDC ECM                 BLE GATT
    (usb-serial)               (RxAndroidBle)
         │                          │
    ┌────▼────┐              ┌──────▼──────┐
    │ Device  │              │  Firmware   │
    │ (ECM)   │              │  (CH32V203) │
    └─────────┘              └──────┬──────┘
                                    │ TCP to target
                              ┌─────▼──────┐
                              │ Target SSH │
                              │ Server     │
                              └────────────┘
```

---

## 2. Dependencies

### 2.1 New Libraries

| Library | Purpose | Version | License |
|---------|---------|---------|---------|
| **Mavericks SSH** (`com.github.mwiede:jsch`) | SSH-2.0 client (key exchange, encryption, channel, PTY) | 0.2.17 | BSD |
| **jackpal Android Terminal Emulator** (`jackpal:termux-api` alternative) OR **Custom View** | Terminal emulator rendering (ANSI escape, PTY simulation) | — | — |

**Terminal Emulator Decision**: We will use a lightweight custom terminal view based on Android `SurfaceView` or `TextView` with a manual ANSI escape sequence parser. The alternative of embedding Termux is too heavy. A simpler approach is to use **Androidterm** patterns or the **termux:shared** library concept.

**Recommended approach**: Use the **Android Terminal Emulator** library concept — implement a VT100/ANSI escape parser + display surface. The rendering logic is ~2000 lines and handles:
- Cursor positioning
- Character attributes (bold, color, underline)
- Scroll regions
- Line wrap

Alternatively, evaluate embedding the **Termux:API** terminal session, but this adds complexity.

**Final recommendation**: Use **JSch** for SSH transport and implement a lightweight terminal renderer. For the terminal renderer, reference the open-source `jackpal.androidterm.emulatorview` patterns (Apache 2.0 license). We can extract the core `EmulatorView` and `TermSession` concepts.

### 2.2 build.gradle Changes

```gradle
dependencies {
    // ... existing dependencies ...
    
    // SSH client
    implementation 'com.github.mwiede:jsch:0.2.17'
    
    // Terminal emulator view (if using external library)
    // implementation 'com.github.jackpal:Android-Terminal-Emulator:v1.0.70'
}
```

### 2.3 Permissions

No additional Android permissions are needed beyond what the app already has:
- BLE permissions are already declared (for BLE-Eth)
- USB permissions are already declared (for USB ECM)

---

## 3. File Inventory

### 3.1 New Files

| File | Package | Purpose |
|------|---------|---------|
| `TerminalFragment.java` | `fragments` | Main fragment hosting the terminal UI |
| `TerminalView.java` | `terminal` | Custom view that renders terminal output and handles input |
| `TerminalSession.java` | `terminal` | Manages terminal state (rows, cols, scrollback buffer) |
| `AnsiEscapeParser.java` | `terminal` | Parses ANSI/VT100 escape sequences |
| `SshClient.java` | `terminal` | Wraps JSch SSH session, manages authentication |
| `TransportAdapter.java` | `terminal` | Abstract interface for raw TCP transport |
| `UsbEcmTransport.java` | `terminal` | TCP transport over USB CDC ECM |
| `BleEthTransport.java` | `terminal` | TCP transport over BLE-Eth tunnel (Java port of ble_eth_tunnel.py) |
| `FrameParser.java` | `terminal` | BLE-Eth frame parser (Java port of Python FrameParser) |
| `DataReassembler.java` | `terminal` | BLE-Eth fragment reassembler (Java port of Python DataReassembler) |
| `TerminalPrefs.java` | `terminal` | Terminal settings (font size, color theme, SSH credentials) |

### 3.2 Modified Files

| File | Change |
|------|--------|
| `nav_menu.xml` | Add Terminal nav item |
| `MainActivity.java` | Add click handler for Terminal nav item |
| `strings.xml` | Add string resources |
| `LaunchPanelActivity.java` | Add `MODE_TERMINAL` constant |
| `activity_main.xml` | Potentially add terminal overlay container |
| `colors.xml` | Add terminal theme colors (optional) |

### 3.3 New Resources

| File | Purpose |
|------|---------|
| `fragment_terminal.xml` | Terminal fragment layout |
| `layout/terminal_connection_dialog.xml` | SSH connection setup dialog |
| `drawable/ic_terminal.xml` | Terminal icon for sidebar |

---

## 4. Detailed Implementation

### 4.1 Sidebar Navigation

#### 4.1.1 `nav_menu.xml` — Add Terminal Item

Insert after the "Report a Bug" item and before "Welcome & Guide":

```xml
<LinearLayout
    android:id="@+id/nav_terminal"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:padding="12dp"
    android:layout_marginStart="8dp"
    android:layout_marginEnd="8dp"
    android:layout_marginTop="2dp"
    android:layout_marginBottom="2dp"
    android:background="@drawable/nav_item_background_selector"
    android:clickable="true"
    android:focusable="true"
    android:contentDescription="@string/terminal_title">

    <ImageView
        android:id="@+id/nav_icon_terminal"
        android:layout_width="20dp"
        android:layout_height="20dp"
        android:src="@drawable/ic_terminal"
        android:importantForAccessibility="no"
        android:tint="?attr/colorPrimary" />

    <TextView
        android:id="@+id/nav_text_terminal"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/terminal_title"
        android:textSize="16sp"
        android:textColor="@color/text_primary"
        android:layout_marginStart="12dp" />

</LinearLayout>
```

#### 4.1.2 `MainActivity.java` — Navigation Handler

Add to the nav initialization block (~line 1600, after Report a Bug handler):

```java
if (navTerminal != null) {
    navTerminal.setOnClickListener(v -> {
        currentNavMode = LaunchPanelActivity.MODE_TERMINAL;
        updateNavSelection();
        showTerminalFragment();
        markDrawerCloseAsNavigation();
        drawerLayout.closeDrawer(GravityCompat.START);
    });
}
```

Add the fragment replacement method:

```java
private void showTerminalFragment() {
    FragmentManager fragmentManager = getSupportFragmentManager();
    FragmentTransaction transaction = fragmentManager.beginTransaction();
    transaction.replace(R.id.fragment_container, new TerminalFragment());
    transaction.commit();
}
```

#### 4.1.3 `LaunchPanelActivity.java` — Mode Constant

```java
public static final int MODE_TERMINAL = 9; // (or next available number)
```

#### 4.1.4 `strings.xml`

```xml
<string name="terminal_title">Terminal</string>
<string name="terminal_connect">Connect</string>
<string name="terminal_disconnect">Disconnect</string>
<string name="terminal_transport_usb">USB ECM Bridge</string>
<string name="terminal_transport_ble">BLE-Eth Tunnel</string>
<string name="terminal_host">Target Host</string>
<string name="terminal_port">Port</string>
<string name="terminal_username">Username</string>
<string name="terminal_password">Password</string>
<string name="terminal_connecting">Connecting...</string>
<string name="terminal_connected">Connected</string>
<string name="terminal_disconnected">Disconnected</string>
<string name="terminal_connection_failed">Connection failed</string>
<string name="terminal_no_transport">No transport available. Connect device via USB (ECM Bridge mode) or BLE.</string>
<string name="terminal_font_size">Font Size</string>
<string name="terminal_color_scheme">Color Scheme</string>
```

### 4.2 Terminal Fragment

#### 4.2.1 `fragment_terminal.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/terminal_background">

    <!-- Toolbar -->
    <LinearLayout
        android:id="@+id/terminal_toolbar"
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:background="@color/toolbar_background"
        android:paddingStart="12dp"
        android:paddingEnd="12dp">

        <TextView
            android:id="@+id/terminal_status"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="@string/terminal_disconnected"
            android:textColor="@color/terminal_status_text"
            android:textSize="14sp" />

        <Button
            android:id="@+id/terminal_connect_btn"
            android:layout_width="wrap_content"
            android:layout_height="36dp"
            android:text="@string/terminal_connect"
            android:textSize="12sp"
            android:background="?attr/selectableItemBackgroundBorderless" />

        <ImageButton
            android:id="@+id/terminal_settings_btn"
            android:layout_width="36dp"
            android:layout_height="36dp"
            android:src="@drawable/ic_settings"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="@string/settings_title" />

    </LinearLayout>

    <!-- Terminal Display -->
    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">

        <com.openterface.keymod.terminal.TerminalView
            android:id="@+id/terminal_view"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:focusable="true"
            android:focusableInTouchMode="true" />

        <!-- Connection overlay (shown when disconnected) -->
        <LinearLayout
            android:id="@+id/terminal_connection_overlay"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:orientation="vertical"
            android:gravity="center"
            android:background="@color/terminal_background"
            android:visibility="visible">

            <ImageView
                android:layout_width="64dp"
                android:layout_height="64dp"
                android:src="@drawable/ic_terminal"
                android:tint="@color/text_secondary"
                android:layout_marginBottom="16dp" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Terminal"
                android:textSize="24sp"
                android:textColor="@color/text_primary"
                android:layout_marginBottom="8dp" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/terminal_no_transport"
                android:textSize="14sp"
                android:textColor="@color/text_secondary"
                android:gravity="center"
                android:padding="16dp" />

        </LinearLayout>

    </FrameLayout>

    <!-- Keyboard Toggle (optional, for on-screen keyboard) -->
    <LinearLayout
        android:id="@+id/terminal_bottom_bar"
        android:layout_width="match_parent"
        android:layout_height="36dp"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:background="@color/terminal_bottom_bar"
        android:paddingStart="8dp"
        android:paddingEnd="8dp">

        <Button
            android:id="@+id/terminal_ctrl_btn"
            android:layout_width="wrap_content"
            android:layout_height="match_parent"
            android:text="Ctrl"
            android:textSize="12sp"
            android:background="?attr/selectableItemBackgroundBorderless" />

        <Button
            android:id="@+id/terminal_esc_btn"
            android:layout_width="wrap_content"
            android:layout_height="match_parent"
            android:text="Esc"
            android:textSize="12sp"
            android:background="?attr/selectableItemBackgroundBorderless" />

        <Button
            android:id="@+id/terminal_tab_btn"
            android:layout_width="wrap_content"
            android:layout_height="match_target"
            android:text="Tab"
            android:textSize="12sp"
            android:background="?attr/selectableItemBackgroundBorderless" />

    </LinearLayout>

</LinearLayout>
```

#### 4.2.2 `TerminalFragment.java`

```java
package com.openterface.keymod.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.R;
import com.openterface.keymod.terminal.BleEthTransport;
import com.openterface.keymod.terminal.SshClient;
import com.openterface.keymod.terminal.TerminalSession;
import com.openterface.keymod.terminal.TerminalView;
import com.openterface.keymod.terminal.TransportAdapter;
import com.openterface.keymod.terminal.UsbEcmTransport;
import com.openterface.keymod.terminal.TerminalPrefs;

public class TerminalFragment extends Fragment {

    private TerminalView terminalView;
    private TerminalSession terminalSession;
    private SshClient sshClient;
    private TransportAdapter transport;

    private Button connectBtn;
    private ImageButton settingsBtn;
    private TextView statusText;
    private LinearLayout connectionOverlay;

    private ConnectionManager.ConnectionStateListener connectionStateListener;
    private boolean isConnected = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);

        initViews(view);
        initTerminal();
        setupListeners();
        updateTransportAvailability();

        return view;
    }

    private void initViews(View view) {
        terminalView = view.findViewById(R.id.terminal_view);
        connectBtn = view.findViewById(R.id.terminal_connect_btn);
        settingsBtn = view.findViewById(R.id.terminal_settings_btn);
        statusText = view.findViewById(R.id.terminal_status);
        connectionOverlay = view.findViewById(R.id.terminal_connection_overlay);
    }

    private void initTerminal() {
        TerminalPrefs prefs = new TerminalPrefs(requireContext());
        terminalSession = new TerminalSession(
            prefs.getTerminalRows(),
            prefs.getTerminalCols(),
            prefs.getScrollbackSize()
        );
        terminalView.setTerminalSession(terminalSession);
    }

    private void setupListeners() {
        connectBtn.setOnClickListener(v -> {
            if (sshClient != null && sshClient.isConnected()) {
                disconnect();
            } else {
                showConnectionDialog();
            }
        });

        settingsBtn.setOnClickListener(v -> showTerminalSettings());

        // Register for connection state changes
        if (getConnectionManager() != null) {
            connectionStateListener = (type, state) -> {
                requireActivity().runOnUiThread(this::updateTransportAvailability);
            };
            getConnectionManager().addConnectionStateListener(connectionStateListener);
        }
    }

    private void updateTransportAvailability() {
        if (getConnectionManager() == null) return;
        ConnectionManager.ConnectionState state =
            getConnectionManager().getCurrentConnectionState();
        isConnected = state == ConnectionManager.ConnectionState.CONNECTED;

        // Enable connect button only when a transport is available
        boolean usbAvailable = isUsbEcmAvailable();
        boolean bleAvailable = isBleAvailable();
        connectBtn.setEnabled(usbAvailable || bleAvailable);
        connectionOverlay.setVisibility(
            (usbAvailable || bleAvailable) ? View.GONE : View.VISIBLE
        );
    }

    private boolean isUsbEcmAvailable() {
        // Check: USB connected AND device in ECM bridge mode (Mode 4)
        if (getConnectionManager() == null) return false;
        return getConnectionManager().getCurrentConnectionType()
                   == ConnectionManager.ConnectionType.USB;
        // Note: ECM mode check would require querying UsbModeManager
    }

    private boolean isBleAvailable() {
        if (getConnectionManager() == null) return false;
        return getConnectionManager().getCurrentConnectionType()
                   == ConnectionManager.ConnectionType.BLUETOOTH;
    }

    private void showConnectionDialog() {
        // Show dialog asking for:
        // 1. Transport selection (USB ECM / BLE-Eth)
        // 2. Target IP (default 192.168.11.1 for BLE-Eth, configurable for USB ECM)
        // 3. Port (default 22)
        // 4. Username
        // 5. Password (or key-based auth)
        // On confirmation -> connect()
    }

    private void connect(String host, int port, String username, String password,
                         TransportAdapter transport) {
        statusText.setText(R.string.terminal_connecting);

        sshClient = new SshClient(host, port, username, password, transport);
        sshClient.setListener(new SshClient.Listener() {
            @Override
            public void onConnected() {
                requireActivity().runOnUiThread(() -> {
                    statusText.setText(R.string.terminal_connected);
                    connectionOverlay.setVisibility(View.GONE);
                    connectBtn.setText(R.string.terminal_disconnect);
                });
                // Start reading SSH channel
                sshClient.startShell(terminalSession);
            }

            @Override
            public void onDisconnected() {
                requireActivity().runOnUiThread(() -> {
                    statusText.setText(R.string.terminal_disconnected);
                    connectBtn.setText(R.string.terminal_connect);
                    terminalView.postInvalidate();
                });
            }

            @Override
            public void onDataReceived(byte[] data, int len) {
                terminalSession.append(data, len);
                requireActivity().runOnUiThread(() -> terminalView.postInvalidate());
            }

            @Override
            public void onError(String message) {
                requireActivity().runOnUiThread(() -> {
                    statusText.setText(getString(R.string.terminal_connection_failed)
                        + ": " + message);
                });
            }
        });

        new Thread(sshClient::connect).start();
    }

    private void disconnect() {
        if (sshClient != null) {
            sshClient.disconnect();
        }
    }

    private ConnectionManager getConnectionManager() {
        if (getActivity() instanceof MainActivity) {
            return ((MainActivity) getActivity()).getConnectionManager();
        }
        return null;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateTransportAvailability();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disconnect();
        if (getConnectionManager() != null && connectionStateListener != null) {
            getConnectionManager().removeConnectionStateListener(connectionStateListener);
        }
    }
}
```

### 4.3 Transport Layer

#### 4.3.1 `TransportAdapter.java`

Abstract interface that both USB ECM and BLE-Eth transports implement:

```java
package com.openterface.keymod.terminal;

/**
 * Abstract transport for raw TCP byte streaming.
 * The SSH client reads/writes through this interface, unaware of the
 * underlying transport (USB or BLE).
 */
public interface TransportAdapter {

    interface Listener {
        void onDataReceived(byte[] data, int len);
        void onDisconnected();
        void onError(String message);
    }

    /**
     * Open a TCP connection to the target.
     * @param host target IP
     * @param port target port
     * @param timeoutMs connection timeout
     */
    void connect(String host, int port, long timeoutMs);

    /** Send data to the remote side. */
    void send(byte[] data, int offset, int len);

    /** Close the transport. */
    void disconnect();

    /** Whether the transport is active. */
    boolean isConnected();

    void setListener(Listener listener);
}
```

#### 4.3.2 `UsbEcmTransport.java`

When the device is in ECM Bridge mode (USB Mode 4), the device appears as a USB Ethernet adapter. The Android side gets a virtual Ethernet interface. Since Android doesn't expose raw Ethernet sockets easily, we use the USB serial interface:

**Important**: For USB ECM mode, the firmware creates a CDC ECM USB interface that provides a standard Ethernet-over-USB link. Android recognizes this as a network interface (`usb0`). SSH traffic goes through the standard TCP/IP stack — no custom framing needed.

```java
package com.openterface.keymod.terminal;

/**
 * TCP transport over USB CDC ECM.
 * 
 * When the device is in HID+ECM Bridge mode (Mode 4), Android creates
 * a virtual network interface (usb0). The target machine is reachable
 * at its IP on this interface (typically 192.168.x.x subnet).
 * 
 * This transport uses standard Java Sockets since ECM provides
 * a standard IP network interface.
 */
public class UsbEcmTransport implements TransportAdapter {

    private Listener listener;
    private java.net.Socket socket;
    private java.io.InputStream inputStream;
    private java.io.OutputStream outputStream;
    private volatile boolean running = false;

    @Override
    public void connect(String host, int port, long timeoutMs) {
        try {
            socket = new java.net.Socket();
            socket.connect(new java.net.InetSocketAddress(host, port), (int) timeoutMs);
            socket.setSoTimeout(0); // blocking read
            socket.setTcpNoDelay(true); // SSH needs low latency

            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
            running = true;

            // Start read thread
            new Thread(this::readLoop, "UsbEcm-Read").start();

            if (listener != null) {
                // Connected — caller handles onConnected
            }
        } catch (java.io.IOException e) {
            if (listener != null) {
                listener.onError("USB ECM connect failed: " + e.getMessage());
            }
        }
    }

    private void readLoop() {
        byte[] buffer = new byte[4096];
        try {
            while (running) {
                int len = inputStream.read(buffer);
                if (len < 0) break;
                if (listener != null) {
                    listener.onDataReceived(buffer, len);
                }
            }
        } catch (java.io.IOException e) {
            if (running && listener != null) {
                listener.onDisconnected();
            }
        }
    }

    @Override
    public void send(byte[] data, int offset, int len) {
        try {
            outputStream.write(data, offset, len);
            outputStream.flush();
        } catch (java.io.IOException e) {
            if (listener != null) {
                listener.onError("USB ECM send failed: " + e.getMessage());
            }
        }
    }

    @Override
    public void disconnect() {
        running = false;
        try {
            if (socket != null) socket.close();
        } catch (java.io.IOException e) { /* ignore */ }
        if (listener != null) {
            listener.onDisconnected();
        }
    }

    @Override
    public boolean isConnected() {
        return socket != null && socket.isConnected() && running;
    }

    @Override
    public void setListener(Listener listener) {
        this.listener = listener;
    }
}
```

#### 4.3.3 `BleEthTransport.java`

Java port of the Python `ble_eth_tunnel.py` protocol. This is the most complex piece.

```java
package com.openterface.keymod.terminal;

/**
 * TCP transport over BLE-Eth tunnel.
 * 
 * This implements the BLE-to-Ethernet tunneling protocol in Java,
 * mirroring the Python ble_eth_tunnel.py implementation.
 * 
 * Protocol:
 * 1. CONNECT -> device opens TCP connection to target
 * 2. DATA (fragmented) -> tunnel TCP payload bidirectionally
 * 3. DISCONNECT -> close tunnel
 * 
 * Runs over RxAndroidBle (already used by the app).
 */
public class BleEthTransport implements TransportAdapter {

    // Protocol constants (must match firmware ble_eth_protocol.h)
    private static final byte CMD_CONNECT = 0x10;
    private static final byte CMD_DATA = 0x11;
    private static final byte CMD_DISCONNECT = 0x12;
    private static final byte CMD_INFO = 0x1F;
    private static final byte CMD_CONNECT_RESP = (byte) 0x90;
    private static final byte CMD_DATA_RESP = (byte) 0x91;
    private static final byte CMD_DISCONN_RESP = (byte) 0x92;
    private static final byte CMD_CONN_CLOSED = (byte) 0xD2;
    private static final byte CMD_INFO_RESP = (byte) 0x9F;

    private static final int MAX_PAYLOAD_LEN = 249;
    private static final int FRAG_HEADER_LEN = 3;
    private static final int MAX_FRAG_DATA = MAX_PAYLOAD_LEN - FRAG_HEADER_LEN; // 246
    private static final byte FRAG_MORE = (byte) 0x80;
    private static final byte FRAG_FIRST = 0x40;

    private Listener listener;
    private BluetoothService bluetoothService; // existing BLE service
    private int connId = -1;
    private volatile boolean running = false;

    // Frame parser state
    private final FrameParser frameParser = new FrameParser();
    private final DataReassembler dataReassembler = new DataReassembler();

    // Pending connect future (uses CountDownLatch for sync)
    private java.util.concurrent.CountDownLatch connectLatch;
    private int pendingConnId = -1;
    private int pendingConnStatus = -1;

    public BleEthTransport(BluetoothService bluetoothService) {
        this.bluetoothService = bluetoothService;
    }

    @Override
    public void connect(String host, int port, long timeoutMs) {
        try {
            // Build CONNECT frame
            byte[] frame = buildConnect(host, port);

            // Set up response listener
            connectLatch = new java.util.concurrent.CountDownLatch(1);
            bluetoothService.addDataCallback(this::handleIncomingFrame);

            // Send CONNECT
            bluetoothService.writeCharacteristic(frame);

            // Wait for response
            boolean success = connectLatch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!success) {
                listener.onError("CONNECT timed out");
                return;
            }
            if (pendingConnStatus != 0x00) {
                listener.onError("CONNECT failed: status=0x" + Integer.toHexString(pendingConnStatus));
                return;
            }

            connId = pendingConnId;
            running = true;
            // Connected — SSH client proceeds
        } catch (Exception e) {
            listener.onError("BLE-Eth connect failed: " + e.getMessage());
        }
    }

    @Override
    public void send(byte[] data, int offset, int len) {
        if (connId < 0 || !running) return;

        byte[] payload = new byte[len];
        System.arraycopy(data, offset, payload, 0, len);

        if (len <= MAX_FRAG_DATA) {
            byte[] frame = buildDataSingle(connId, payload);
            bluetoothService.writeCharacteristic(frame);
        } else {
            byte[][] frames = buildDataFragmented(connId, payload);
            for (byte[] frame : frames) {
                bluetoothService.writeCharacteristic(frame);
            }
        }
    }

    @Override
    public void disconnect() {
        running = false;
        if (connId >= 0) {
            byte[] frame = buildDisconnect(connId);
            bluetoothService.writeCharacteristic(frame);
            connId = -1;
        }
        bluetoothService.removeDataCallback(this::handleIncomingFrame);
        if (listener != null) {
            listener.onDisconnected();
        }
    }

    @Override
    public boolean isConnected() {
        return running && connId >= 0;
    }

    @Override
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    // --- Frame handling ---

    private void handleIncomingFrame(byte[] data) {
        frameParser.feed(data);
        while (!frameParser.isEmpty()) {
            ParsedFrame frame = frameParser.pop();
            handleFrame(frame);
        }
    }

    private void handleFrame(ParsedFrame frame) {
        switch (frame.cmd) {
            case CMD_CONNECT_RESP:
                if (frame.payload.length >= 2) {
                    pendingConnId = frame.payload[0] & 0xFF;
                    pendingConnStatus = frame.payload[1] & 0xFF;
                    if (connectLatch != null) {
                        connectLatch.countDown();
                    }
                }
                break;

            case CMD_DATA_RESP: // 0x91 — shared with DATA_PUSH
                if (looksLikeDataAck(frame.payload)) {
                    // ACK from firmware — can be ignored for basic operation
                    // (Advanced: track for flow control)
                } else {
                    // Incoming data push
                    ReassembledData reassembled = dataReassembler.feed(frame.payload);
                    if (reassembled != null && listener != null) {
                        listener.onDataReceived(reassembled.data, reassembled.data.length);
                    }
                }
                break;

            case CMD_DISCONN_RESP:
            case CMD_CONN_CLOSED:
                if (frame.payload.length >= 1) {
                    int closedConnId = frame.payload[0] & 0xFF;
                    if (closedConnId == connId) {
                        running = false;
                        connId = -1;
                        if (listener != null) {
                            listener.onDisconnected();
                        }
                    }
                }
                break;
        }
    }

    // --- Frame builders ---

    private static byte[] buildFrame(int addr, int cmd, byte[] payload) {
        byte[] frame = new byte[6 + (payload != null ? payload.length : 0)];
        frame[0] = 0x57;
        frame[1] = (byte) 0xAB;
        frame[2] = (byte) addr;
        frame[3] = (byte) cmd;
        frame[4] = (byte) (payload != null ? payload.length : 0);
        if (payload != null) {
            System.arraycopy(payload, 0, frame, 5, payload.length);
        }
        int checksum = 0;
        for (int i = 0; i < frame.length - 1; i++) {
            checksum += frame[i] & 0xFF;
        }
        frame[frame.length - 1] = (byte) (checksum & 0xFF);
        return frame;
    }

    private static byte[] buildConnect(String ip, int port) {
        String[] parts = ip.split("\\.");
        byte[] payload = new byte[6];
        for (int i = 0; i < 4; i++) {
            payload[i] = (byte) Integer.parseInt(parts[i]);
        }
        payload[4] = (byte) ((port >> 8) & 0xFF);
        payload[5] = (byte) (port & 0xFF);
        return buildFrame(0x00, CMD_CONNECT, payload);
    }

    private static byte[] buildDisconnect(int connId) {
        return buildFrame(0x00, CMD_DISCONNECT, new byte[]{(byte) connId});
    }

    private static byte[] buildDataSingle(int connId, byte[] data) {
        byte[] payload = new byte[3 + data.length];
        payload[0] = FRAG_FIRST;
        payload[1] = 0x00; // seq
        payload[2] = (byte) connId;
        System.arraycopy(data, 0, payload, 3, data.length);
        return buildFrame(0x00, CMD_DATA, payload);
    }

    private static byte[][] buildDataFragmented(int connId, byte[] data) {
        int totalFrags = (data.length + MAX_FRAG_DATA - 1) / MAX_FRAG_DATA;
        byte[][] frames = new byte[totalFrags][];
        int offset = 0;
        for (int seq = 0; seq < totalFrags; seq++) {
            int chunkLen = Math.min(MAX_FRAG_DATA, data.length - offset);
            byte[] payload = new byte[3 + chunkLen];
            byte flags = (byte) (totalFrags & 0x0F);
            if (seq == 0) flags |= FRAG_FIRST;
            if (seq < totalFrags - 1) flags |= FRAG_MORE;
            payload[0] = flags;
            payload[1] = (byte) seq;
            payload[2] = (byte) connId;
            System.arraycopy(data, offset, payload, 3, chunkLen);
            frames[seq] = buildFrame(0x00, CMD_DATA, payload);
            offset += chunkLen;
        }
        return frames;
    }

    private static boolean looksLikeDataAck(byte[] payload) {
        if (payload == null || payload.length == 0) return false;
        int status = payload[0] & 0xFF;
        return status == 0x00 || (status & 0xF0) == 0xE0;
    }

    // --- Inner classes: FrameParser, ParsedFrame, ReassembledData ---
    // (Java port of Python equivalents — see Section 4.3.4-4.3.5)
}
```

#### 4.3.4 `FrameParser.java`

State machine parser for BLE-Eth frames. Direct Java port of the Python `FrameParser` class:

```java
package com.openterface.keymod.terminal;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * State machine parser for BLE-Eth frames.
 * Ported from Python ble_eth_tunnel.py FrameParser.
 */
public class FrameParser {

    private static final int FRAME_HEAD1 = 0x57;
    private static final int FRAME_HEAD2 = 0xAB;
    private static final int FRAME_HEAD_LEN = 5;
    private static final int MAX_PAYLOAD_LEN = 249;

    private int state = 0;
    private byte[] buf = new byte[MAX_PAYLOAD_LEN + FRAME_HEAD_LEN + 1];
    private int bufLen = 0;
    private int payloadLen = 0;
    private int checksum = 0;

    private final Deque<ParsedFrame> receivedFrames = new ArrayDeque<>();

    public void feed(byte[] data) {
        for (byte b : data) {
            process(b & 0xFF);
        }
    }

    private void process(int b) {
        switch (state) {
            case 0: // wait head1
                if (b == FRAME_HEAD1) {
                    bufLen = 0;
                    buf[bufLen++] = (byte) b;
                    checksum = b;
                    state = 1;
                }
                break;
            case 1: // wait head2
                if (b == FRAME_HEAD2) {
                    buf[bufLen++] = (byte) b;
                    checksum += b;
                    state = 2;
                } else if (b == FRAME_HEAD1) {
                    bufLen = 0;
                    buf[bufLen++] = (byte) b;
                    checksum = b;
                    state = 1;
                } else {
                    state = 0;
                }
                break;
            case 2: // wait addr
                buf[bufLen++] = (byte) b;
                checksum += b;
                state = 3;
                break;
            case 3: // wait cmd
                buf[bufLen++] = (byte) b;
                checksum += b;
                state = 4;
                break;
            case 4: // wait len
                buf[bufLen++] = (byte) b;
                checksum += b;
                payloadLen = b;
                if (payloadLen == 0) {
                    state = 6;
                } else if (payloadLen > MAX_PAYLOAD_LEN) {
                    state = 0;
                } else {
                    state = 5;
                }
                break;
            case 5: // wait payload
                buf[bufLen++] = (byte) b;
                checksum += b;
                if (bufLen >= FRAME_HEAD_LEN + payloadLen) {
                    state = 6;
                }
                break;
            case 6: // wait checksum
                int expected = checksum & 0xFF;
                if (b == expected) {
                    int cmd = buf[3] & 0xFF;
                    byte[] payload = new byte[payloadLen];
                    System.arraycopy(buf, 5, payload, 0, payloadLen);
                    receivedFrames.add(new ParsedFrame(buf[2] & 0xFF, cmd, payload));
                }
                state = 0;
                break;
        }
    }

    public boolean isEmpty() { return receivedFrames.isEmpty(); }
    public ParsedFrame pop() { return receivedFrames.poll(); }

    public static class ParsedFrame {
        public final int addr;
        public final int cmd;
        public final byte[] payload;
        public ParsedFrame(int addr, int cmd, byte[] payload) {
            this.addr = addr;
            this.cmd = cmd;
            this.payload = payload;
        }
    }
}
```

#### 4.3.5 `DataReassembler.java`

```java
package com.openterface.keymod.terminal;

/**
 * Reassembles fragmented DATA frames into complete payloads.
 * Ported from Python ble_eth_tunnel.py DataReassembler.
 */
public class DataReassembler {

    private static final int FRAG_HEADER_LEN = 3;
    private static final byte FRAG_MORE = (byte) 0x80;
    private static final byte FRAG_FIRST = 0x40;
    private static final int FRAG_COUNT_MASK = 0x0F;

    private boolean active = false;
    private int connId = 0;
    private int totalFrags = 0;
    private int nextSeq = 0;
    private java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();

    public ReassembledData feed(byte[] payload) {
        if (payload.length < FRAG_HEADER_LEN) return null;

        int flags = payload[0] & 0xFF;
        int seq = payload[1] & 0xFF;
        connId = payload[2] & 0xFF;
        int fragDataLen = payload.length - FRAG_HEADER_LEN;

        int total = flags & FRAG_COUNT_MASK;
        if (total == 0) total = 1;

        if ((flags & FRAG_FIRST) != 0) {
            active = true;
            this.totalFrags = total;
            nextSeq = 0;
            buf = new java.io.ByteArrayOutputStream();
        }

        if (!active) return null;

        if (seq != nextSeq) {
            active = false;
            return null; // out of order, discard
        }

        buf.write(payload, FRAG_HEADER_LEN, fragDataLen);
        nextSeq++;

        if ((flags & FRAG_MORE) == 0) {
            active = false;
            return new ReassembledData(connId, buf.toByteArray());
        }
        return null;
    }

    public static class ReassembledData {
        public final int connId;
        public final byte[] data;
        public ReassembledData(int connId, byte[] data) {
            this.connId = connId;
            this.data = data;
        }
    }
}
```

### 4.4 SSH Client

#### 4.4.1 `SshClient.java`

Wraps JSch to manage the SSH session:

```java
package com.openterface.keymod.terminal;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

/**
 * SSH-2.0 client wrapper using JSch.
 * The SSH client runs over a TransportAdapter (USB ECM or BLE-Eth).
 */
public class SshClient {

    public interface Listener {
        void onConnected();
        void onDisconnected();
        void onDataReceived(byte[] data, int len);
        void onError(String message);
    }

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final TransportAdapter transport;
    private Listener listener;

    private Session session;
    private Channel shellChannel;
    private volatile boolean connected = false;

    public SshClient(String host, int port, String username,
                     String password, TransportAdapter transport) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.transport = transport;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public boolean isConnected() {
        return connected;
    }

    /**
     * Establish SSH connection. Call on background thread.
     */
    public void connect() {
        try {
            JSch jsch = new JSch();

            // Disable host key checking for local forwarded sessions
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            // SSH profile optimized for BLE tunnel (narrow cipher/KEX set)
            config.put("kex", "curve25519-sha256");
            config.put("server_host_key", "ssh-ed25519,rsa-sha2-512,rsa-sha2-256");
            config.put("cipher.s2c", "aes128-ctr,aes128-ctr");
            config.put("cipher.c2s", "aes128-ctr,aes128-ctr");
            config.put("mac.s2c", "hmac-sha2-256");
            config.put("mac.c2s", "hmac-sha2-256");
            config.put("compression.s2c", "none");
            config.put("compression.c2s", "none");

            session = jsch.getSession(username, host, port);
            session.setPassword(password);
            session.setConfig(config);
            session.setConnectTimeout(15000);

            // Set the socket factory to use our transport
            // JSch normally creates its own Socket; we need to intercept
            // to use the TransportAdapter instead.
            // We'll use a custom SocketFactory that delegates to the transport.
            session.setSocketFactory(new TransportSocketFactory(transport));

            session.connect(20000); // SSH handshake timeout
            connected = true;

            if (listener != null) {
                listener.onConnected();
            }

        } catch (Exception e) {
            connected = false;
            if (listener != null) {
                listener.onError(e.getMessage());
            }
        }
    }

    /**
     * Start an interactive shell channel.
     * Data from the shell is delivered to the TerminalSession via the listener.
     */
    public void startShell(TerminalSession terminalSession) {
        try {
            shellChannel = session.openChannel("shell");

            // Set terminal size
            int cols = terminalSession.getColumns();
            int rows = terminalSession.getRows();
            ((com.jcraft.jsch.ChannelShell) shellChannel).setPtySize(cols, rows, cols * 8, rows * 16);

            // Input: terminal keystrokes -> SSH channel
            // Output: SSH channel -> terminal session
            java.io.InputStream in = shellChannel.getInputStream();
            java.io.OutputStream out = shellChannel.getOutputStream();
            shellChannel.connect();

            // Read thread: SSH -> TerminalView
            new Thread(() -> {
                byte[] buffer = new byte[4096];
                try {
                    while (connected && !shellChannel.isClosed()) {
                        int len = in.read(buffer);
                        if (len < 0) break;
                        if (listener != null) {
                            listener.onDataReceived(buffer, len);
                        }
                    }
                } catch (java.io.IOException e) {
                    // Connection lost
                } finally {
                    connected = false;
                    if (listener != null) {
                        listener.onDisconnected();
                    }
                }
            }, "SshShell-Read").start();

            // Write callback: bind terminal keystrokes to SSH output
            terminalSession.setKeySender(data -> {
                try {
                    out.write(data);
                    out.flush();
                } catch (java.io.IOException e) {
                    if (listener != null) {
                        listener.onError("SSH write failed: " + e.getMessage());
                    }
                }
            });

        } catch (Exception e) {
            if (listener != null) {
                listener.onError("Shell channel failed: " + e.getMessage());
            }
        }
    }

    /**
     * Send window resize notification to the remote side.
     */
    public void resizeTerminal(int cols, int rows) {
        if (shellChannel != null && shellChannel.isConnected()) {
            try {
                ((com.jcraft.jsch.ChannelShell) shellChannel).setPtySize(
                    cols, rows, cols * 8, rows * 16);
            } catch (Exception e) {
                // Ignore resize errors
            }
        }
    }

    /** Disconnect SSH session. */
    public void disconnect() {
        connected = false;
        if (shellChannel != null && shellChannel.isConnected()) {
            shellChannel.disconnect();
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        transport.disconnect();
    }

    /**
     * Custom JSch SocketFactory that delegates to TransportAdapter.
     * JSch calls SocketFactory.createSocket() to get the underlying socket.
     * We return a wrapper that uses the transport's send/receive.
     */
    private static class TransportSocketFactory implements com.jcraft.jsch.SocketFactory {

        private final TransportAdapter transport;

        TransportSocketFactory(TransportAdapter transport) {
            this.transport = transport;
        }

        @Override
        public java.net.Socket createSocket(String host, int port) {
            // The transport.connect() has already been called by SshClient.connect()
            // We need a socket wrapper that JSch can use for its IO.
            return new TransportBackedSocket(transport);
        }

        @Override
        public java.io.InputStream getInputStream(java.net.Socket socket) {
            return ((TransportBackedSocket) socket).getInputStream();
        }

        @Override
        public java.io.OutputStream getOutputStream(java.net.Socket socket) {
            return ((TransportBackedSocket) socket).getOutputStream();
        }
    }

    /**
     * A java.net.Socket wrapper backed by a TransportAdapter.
     * JSch uses Socket.getInputStream() and Socket.getOutputStream().
     */
    private static class TransportBackedSocket extends java.net.Socket {

        private final TransportAdapter transport;
        private final TransportPipedInputStream inputStream;
        private final TransportPipedOutputStream outputStream;

        TransportBackedSocket(TransportAdapter transport) {
            this.transport = transport;
            this.inputStream = new TransportPipedInputStream();
            this.outputStream = new TransportPipedOutputStream();

            transport.setListener(new TransportAdapter.Listener() {
                @Override
                public void onDataReceived(byte[] data, int len) {
                    inputStream.write(data, 0, len);
                }

                @Override
                public void onDisconnected() {
                    inputStream.close();
                }

                @Override
                public void onError(String message) {
                    inputStream.close();
                }
            });
        }

        public TransportPipedInputStream getInputStream() { return inputStream; }
        public TransportPipedOutputStream getOutputStream() { return outputStream; }

        @Override
        public boolean isConnected() {
            return transport.isConnected();
        }

        @Override
        public void close() {
            transport.disconnect();
        }
    }

    /**
     * Piped input stream that receives data from the transport.
     * Uses a blocking queue for backpressure.
     */
    private static class TransportPipedInputStream extends java.io.InputStream {

        private final java.util.concurrent.LinkedBlockingQueue<byte[]> queue
            = new java.util.concurrent.LinkedBlockingQueue<>();
        private byte[] currentBuffer = null;
        private int currentPos = 0;
        private volatile boolean closed = false;

        @Override
        public int read() throws java.io.IOException {
            byte[] b = new byte[1];
            int n = read(b, 0, 1);
            return n == -1 ? -1 : b[0] & 0xFF;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws java.io.IOException {
            while (true) {
                if (closed) return -1;

                if (currentBuffer == null || currentPos >= currentBuffer.length) {
                    try {
                        currentBuffer = queue.take(); // blocking
                        if (currentBuffer == null) { // poison pill
                            closed = true;
                            return -1;
                        }
                        currentPos = 0;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new java.io.IOException("Read interrupted");
                    }
                }

                int available = currentBuffer.length - currentPos;
                int toCopy = Math.min(available, len);
                System.arraycopy(currentBuffer, currentPos, buf, off, toCopy);
                currentPos += toCopy;
                return toCopy;
            }
        }

        public void write(byte[] data, int offset, int len) {
            byte[] copy = new byte[len];
            System.arraycopy(data, offset, copy, 0, len);
            queue.offer(copy);
        }

        @Override
        public void close() {
            closed = true;
            queue.offer(null); // poison pill
        }
    }

    /**
     * Output stream that sends data through the transport.
     */
    private static class TransportPipedOutputStream extends java.io.OutputStream {
        private final TransportAdapter transport;

        TransportPipedOutputStream() {
            // The transport is set on the parent socket
            this.transport = null; // actually handled via parent
        }

        // Actually, we simplify: the SSH client's startShell() gets
        // the output stream directly from the channel and wires it.
        // The SocketFactory approach is complex — simpler to use JSch's
        // Channel.getInputStream()/getOutputStream() directly and
        // bridge to the transport manually.
    }
}
```

**Note**: The `SocketFactory` approach with JSch is complex because JSch expects a real `java.net.Socket`. A simpler alternative is to not use `SocketFactory` at all, and instead:

1. For **USB ECM**: Use real sockets (the network interface is available)
2. For **BLE-Eth**: Use JSch's `ChannelShell.getInputStream()/getOutputStream()` directly, with the transport bridged manually

The simplified approach is preferred — see the alternative design below.

### 4.5 Alternative SSH Architecture (Simplified)

Instead of forcing JSch through a custom `SocketFactory`, we separate the concerns:

```
TerminalView <---> TerminalSession <---> SshClient
                                                |
                                       TransportAdapter
                                       (UsbEcm or BleEth)
```

**Simplified SshClient**:
- For **USB ECM**: JSch creates its own `java.net.Socket(host, port)` — no custom transport needed, the ECM interface provides real IP routing.
- For **BLE-Eth**: We implement SSH manually or use a library that accepts `InputStream`/`OutputStream` directly.

**Recommended for BLE-Eth**: Use **Apache MINA SSHD** client which accepts custom `IoSession`, or implement a minimal SSH-2.0 client for the BLE tunnel case. The JSch library requires a socket, making it awkward for BLE.

**Alternative**: For BLE-Eth, the Python approach works well — a local proxy that bridges TCP to BLE. On Android, we can run an in-process TCP proxy (no external process) that:
1. Listens on `127.0.0.1:PORT`
2. Bridges TCP -> BLE-Eth tunnel -> target
3. JSch connects to `127.0.0.1:PORT`

This is the cleanest approach:

```
JSch (localhost:PORT) → InProcessTcpProxy → BleEthTransport → BLE → Device → Target SSH
```

The `InProcessTcpProxy` runs on a background thread and uses Java's `ServerSocket` + `Socket`:

```java
package com.openterface.keymod.terminal;

/**
 * In-process TCP proxy for BLE-Eth SSH.
 * Listens on localhost:port, bridges connections through BleEthTransport.
 * JSch connects to localhost:port as if it were a real SSH server.
 */
public class BleSshProxy {

    private java.net.ServerSocket serverSocket;
    private final int localPort;
    private final BleEthTransport transport;
    private volatile boolean running = false;

    public BleSshProxy(int localPort, BleEthTransport transport) {
        this.localPort = localPort;
        this.transport = transport;
    }

    /** Start the proxy. JSch should then connect to localhost:localPort. */
    public void start() {
        new Thread(() -> {
            try {
                serverSocket = new java.net.ServerSocket(localPort, 1, java.net.InetAddress.getByName("127.0.0.1"));
                running = true;
                java.net.Socket clientSocket = serverSocket.accept();

                // Bidirectional forwarding: client <-> transport
                // Client -> SSH -> transport -> BLE -> device -> target
                // Target -> device -> BLE -> transport -> client
                forwardBidirectional(clientSocket, transport);
            } catch (java.io.IOException e) {
                // Handle error
            }
        }, "BleSshProxy").start();
    }

    private void forwardBidirectional(java.net.Socket clientSocket, TransportAdapter transport) {
        // Local -> BLE
        new Thread(() -> {
            try {
                java.io.InputStream in = clientSocket.getInputStream();
                byte[] buf = new byte[4096];
                while (running) {
                    int len = in.read(buf);
                    if (len < 0) break;
                    transport.send(buf, 0, len);
                }
            } catch (java.io.IOException e) {
                running = false;
            }
        }).start();

        // BLE -> Local (via transport listener)
        transport.setListener(new TransportAdapter.Listener() {
            @Override
            public void onDataReceived(byte[] data, int len) {
                try {
                    clientSocket.getOutputStream().write(data, 0, len);
                    clientSocket.getOutputStream().flush();
                } catch (java.io.IOException e) {
                    running = false;
                }
            }

            @Override
            public void onDisconnected() {
                try { clientSocket.close(); } catch (Exception e) {}
            }

            @Override
            public void onError(String message) {
                try { clientSocket.close(); } catch (Exception e) {}
            }
        });
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (java.io.IOException e) {}
    }
}
```

### 4.6 Terminal View

#### 4.6.1 `TerminalView.java`

Custom view that renders terminal output. This is based on VT100/ANSI terminal emulator patterns:

```java
package com.openterface.keymod.terminal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

/**
 * Terminal display surface. Renders characters using the TerminalSession
 * screen buffer and handles touch/keyboard input.
 */
public class TerminalView extends View {

    private TerminalSession session;
    private Paint textPaint;
    private Paint bgPaint;
    private float fontSize = 14f;
    private float charWidth, charHeight;
    private int cursorX, cursorY;
    private boolean cursorVisible = true;

    // Color scheme
    private static final int DEFAULT_BG = Color.BLACK;
    private static final int DEFAULT_FG = Color.WHITE;
    private static final int CURSOR_COLOR = Color.WHITE;

    public TerminalView(Context context) { super(context); init(); }
    public TerminalView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTypeface(Typeface.MONOSPACE);
        textPaint.setTextSize(fontSize);
        textPaint.setColor(DEFAULT_FG);

        bgPaint = new Paint();
        bgPaint.setColor(DEFAULT_BG);

        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    public void setTerminalSession(TerminalSession session) {
        this.session = session;
    }

    public void setFontSize(float size) {
        this.fontSize = size;
        textPaint.setTextSize(fontSize);
        measureCharSize();
        invalidate();
    }

    private void measureCharSize() {
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        charHeight = fm.bottom - fm.top;
        // Approximate character width using 'M'
        charWidth = textPaint.measureText("M");
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (session == null) return;

        // Draw background
        canvas.drawColor(DEFAULT_BG);

        // Draw text from screen buffer
        int cols = session.getColumns();
        int rows = session.getRows();

        for (int row = 0; row < rows; row++) {
            StringBuilder line = session.getLine(row);
            int[] colors = session.getLineColors(row);

            for (int col = 0; col < line.length(); col++) {
                char ch = line.charAt(col);
                if (ch == 0) continue; // blank

                int fg = DEFAULT_FG;
                int bg = DEFAULT_BG;
                if (colors != null && col < colors.length) {
                    fg = colors[col] & 0x00FFFFFF; // strip alpha flag
                    bg = ((colors[col] >> 24) & 0xFF) != 0
                        ? ((colors[col] >> 24) & 0x00FFFFFF) : DEFAULT_BG;
                }

                // Draw cell background if different
                if (bg != DEFAULT_BG) {
                    bgPaint.setColor(bg);
                    canvas.drawRect(
                        col * charWidth, row * charHeight,
                        (col + 1) * charWidth, (row + 1) * charHeight,
                        bgPaint
                    );
                }

                textPaint.setColor(fg);
                canvas.drawText(String.valueOf(ch), col * charWidth,
                    (row + 1) * charHeight + textPaint.getFontMetrics().bottom, textPaint);
            }
        }

        // Draw cursor
        if (cursorVisible) {
            Paint cursorPaint = new Paint();
            cursorPaint.setColor(CURSOR_COLOR);
            cursorPaint.setAlpha(128);
            canvas.drawRect(
                session.getCursorX() * charWidth,
                session.getCursorY() * charHeight,
                (session.getCursorX() + 1) * charWidth,
                (session.getCursorY() + 1) * charHeight,
                cursorPaint
            );
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (session == null) return super.onKeyDown(keyCode, event);

        byte[] seq = keyToByteSequence(keyCode, event);
        if (seq != null) {
            session.onKeyInput(seq);
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    /**
     * Map Android key codes to terminal byte sequences.
     * E.g., Enter -> \r, Tab -> \t, Escape -> 0x1B,
     * arrow keys -> CSI sequences.
     */
    private byte[] keyToByteSequence(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
                return new byte[]{'\r'};
            case KeyEvent.KEYCODE_TAB:
                return new byte[]{'\t'};
            case KeyEvent.KEYCODE_ESCAPE:
                return new byte[]{0x1B};
            case KeyEvent.KEYCODE_DPAD_UP:
                return "\033[A".getBytes();
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return "\033[B".getBytes();
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return "\033[D".getBytes();
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return "\033[C".getBytes();
            case KeyEvent.KEYCODE_DEL:
                return new byte[]{0x7F}; // Backspace
            case KeyEvent.KEYCODE_FORWARD_DEL:
                return "\033[3~".getBytes();
            case KeyEvent.KEYCODE_MOVE_HOME:
                return "\033[H".getBytes();
            case KeyEvent.KEYCODE_MOVE_END:
                return "\033[F".getBytes();
            default:
                if (event.isCtrlPressed()) {
                    int ascii = event.getUnicodeChar();
                    if (ascii >= 'a' && ascii <= 'z') {
                        return new byte[]{(byte) (ascii - 'a' + 1)}; // Ctrl+A = 0x01
                    }
                    if (ascii >= 'A' && ascii <= 'Z') {
                        return new byte[]{(byte) (ascii - 'A' + 1)};
                    }
                }
                return null;
        }
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            | EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI;
        return new BaseInputConnection(this, false) {
            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                if (session != null) {
                    session.onKeyInput(text.toString().getBytes());
                }
                return true;
            }

            @Override
            public boolean sendKeyEvent(KeyEvent event) {
                return TerminalView.this.onKeyDown(event.getKeyCode(), event);
            }

            @Override
            public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                // Backspace from soft keyboard
                if (session != null && beforeLength > 0) {
                    session.onKeyInput(new byte[]{0x7F});
                }
                return true;
            }
        };
    }

    /** Show soft keyboard. */
    public void showKeyboard() {
        InputMethodManager imm = (InputMethodManager)
            getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
    }

    /** Hide soft keyboard. */
    public void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager)
            getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(getWindowToken(), 0);
    }
}
```

#### 4.6.2 `TerminalSession.java`

Manages the terminal state machine:

```java
package com.openterface.keymod.terminal;

/**
 * Terminal session state: screen buffer, cursor, scrollback.
 * Implements basic VT100/ANSI escape sequence processing.
 */
public class TerminalSession {

    private final int rows, cols, scrollbackSize;
    private char[][] screen;       // visible screen
    private int[][] screenColors;  // color attributes per cell
    private int cursorX, cursorY;
    private int scrollRegionTop, scrollRegionBottom;
    private int currentFg, currentBg;
    private boolean bold, underline, inverse;
    private java.util.List<char[]> scrollback;

    // Callback for sending keystrokes to remote
    public interface KeySender { void send(byte[] data); }
    private KeySender keySender;

    public TerminalSession(int rows, int cols, int scrollbackSize) {
        this.rows = rows;
        this.cols = cols;
        this.scrollbackSize = scrollbackSize;
        this.scrollback = new java.util.ArrayList<>();
        clearScreen();
    }

    private void clearScreen() {
        screen = new char[rows][cols];
        screenColors = new int[rows][cols];
        cursorX = 0;
        cursorY = 0;
        scrollRegionTop = 0;
        scrollRegionBottom = rows - 1;
        currentFg = 7; // white
        currentBg = 0; // black
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                screen[r][c] = ' ';
            }
        }
    }

    /**
     * Process incoming data from the remote side.
     * Parses ANSI escape sequences and updates screen state.
     */
    public void append(byte[] data, int len) {
        for (int i = 0; i < len; i++) {
            processChar(data[i]);
        }
    }

    /**
     * Minimal VT100 state machine.
     * Full implementation would handle CSI sequences (color, cursor move,
     * erase, scroll, etc.). This sketch shows the architecture.
     */
    private void processChar(byte b) {
        // Simplified — full implementation needs escape sequence parser
        if (b == '\n' || b == '\r') {
            cursorX = 0;
            if (b == '\n') {
                cursorY++;
                if (cursorY > scrollRegionBottom) {
                    scrollUp();
                    cursorY = scrollRegionBottom;
                }
            }
        } else if (b == 0x1B) {
            // Escape sequence — need multi-byte parser
            // Full implementation: accumulate escape sequence, dispatch
        } else if (b >= 0x20) {
            // Printable character
            if (cursorX < cols && cursorY < rows) {
                screen[cursorY][cursorX] = (char) b;
                screenColors[cursorY][cursorX] = (currentBg << 24) | currentFg;
                cursorX++;
                if (cursorX >= cols) {
                    cursorX = 0;
                    cursorY++;
                    if (cursorY > scrollRegionBottom) {
                        scrollUp();
                        cursorY = scrollRegionBottom;
                    }
                }
            }
        }
    }

    private void scrollUp() {
        // Save top line to scrollback
        if (scrollbackSize > 0) {
            scrollback.add(screen[scrollRegionTop].clone());
            while (scrollback.size() > scrollbackSize) {
                scrollback.remove(0);
            }
        }
        // Shift lines up
        for (int r = scrollRegionTop; r < scrollRegionBottom; r++) {
            System.arraycopy(screen[r + 1], 0, screen[r], 0, cols);
            System.arraycopy(screenColors[r + 1], 0, screenColors[r], 0, cols);
        }
        // Clear bottom line
        for (int c = 0; c < cols; c++) {
            screen[scrollRegionBottom][c] = ' ';
            screenColors[scrollRegionBottom][c] = 0;
        }
    }

    public void onKeyInput(byte[] data) {
        if (keySender != null) {
            keySender.send(data);
        }
    }

    public void setKeySender(KeySender sender) { this.keySender = sender; }

    public StringBuilder getLine(int row) {
        StringBuilder sb = new StringBuilder(cols);
        for (int c = 0; c < cols; c++) {
            sb.append(screen[row][c]);
        }
        return sb;
    }

    public int[] getLineColors(int row) {
        return screenColors[row];
    }

    public int getCursorX() { return cursorX; }
    public int getCursorY() { return cursorY; }
    public int getColumns() { return cols; }
    public int getRows() { return rows; }
}
```

### 4.7 Terminal Preferences

#### 4.7.1 `TerminalPrefs.java`

```java
package com.openterface.keymod.terminal;

import android.content.Context;
import android.content.SharedPreferences;

public class TerminalPrefs {

    private static final String PREFS = "terminal_prefs";
    private final SharedPreferences prefs;

    public TerminalPrefs(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public float getFontSize() {
        return prefs.getFloat("font_size", 12f);
    }

    public void setFontSize(float size) {
        prefs.edit().putFloat("font_size", size).apply();
    }

    public int getTerminalRows() { return prefs.getInt("rows", 24); }
    public int getTerminalCols() { return prefs.getInt("cols", 80); }
    public int getScrollbackSize() { return prefs.getInt("scrollback", 2000); }

    public String getLastHost() { return prefs.getString("last_host", "192.168.11.1"); }
    public void setLastHost(String host) {
        prefs.edit().putString("last_host", host).apply();
    }

    public String getLastUsername() { return prefs.getString("last_user", ""); }
    public void setLastUsername(String user) {
        prefs.edit().putString("last_user", user).apply();
    }

    public int getColorScheme() { return prefs.getInt("color_scheme", 0); }
}
```

---

## 5. Connection Dialog

The connection dialog collects SSH parameters before establishing the connection:

```
┌──────────────────────────────────┐
│         Connect to SSH           │
├──────────────────────────────────┤
│                                  │
│ Transport:  [● USB ECM] [○ BLE] │
│                                  │
│ Target Host: [192.168.11.1    ] │
│ Port:        [22             ]   │
│ Username:    [               ]   │
│ Password:    [•••••••••••••  ]   │
│                                  │
│ [Remember credentials] ☐         │
│                                  │
│        [Cancel]    [Connect]     │
└──────────────────────────────────┘
```

The dialog auto-detects available transports:
- If USB ECM available → show as option
- If BLE available → show as option
- If only one available → pre-select and disable radio
- If none available → show error overlay

---

## 6. Implementation Phases

### Phase 1: Navigation + Basic Terminal View (Week 1)

**Goal**: Terminal nav item + static terminal emulator view

1. Add Terminal icon (`ic_terminal.xml`)
2. Add Terminal nav item to `nav_menu.xml`
3. Add nav handler in `MainActivity.java`
4. Add `MODE_TERMINAL` to `LaunchPanelActivity`
5. Create `fragment_terminal.xml` layout
6. Create `TerminalFragment.java` skeleton
7. Create `TerminalView.java` with basic text rendering
8. Create `TerminalSession.java` with minimal VT100 parser
9. Add string resources

**Deliverable**: Terminal appears in sidebar, shows a basic terminal view (no connectivity yet).

### Phase 2: USB ECM Transport + SSH (Week 2)

**Goal**: SSH over USB ECM bridge

1. Create `TransportAdapter.java` interface
2. Create `UsbEcmTransport.java`
3. Add JSch dependency to `build.gradle`
4. Create `SshClient.java` (USB path using real sockets)
5. Create connection dialog UI
6. Wire up connect/disconnect flow
7. Test SSH to target over USB ECM

**Deliverable**: Working SSH terminal over USB ECM bridge.

### Phase 3: BLE-Eth Transport (Week 3)

**Goal**: SSH over BLE-Eth tunnel

1. Create `FrameParser.java` (Java port)
2. Create `DataReassembler.java` (Java port)
3. Create `BleEthTransport.java`
4. Create `BleSshProxy.java` (in-process TCP proxy)
5. Integrate BLE-Eth into connection flow
6. Test SSH over BLE-Eth tunnel

**Deliverable**: Working SSH terminal over BLE-Eth.

### Phase 4: Polish + ANSI Support (Week 4)

**Goal**: Full terminal experience

1. Complete ANSI escape sequence parser (colors, cursor movement, scroll regions)
2. Terminal settings (font size, color themes)
3. Ctrl/Esc/Tab buttons in bottom bar
4. Terminal resize on orientation change
5. Handle SSH key-based authentication
6. Error handling and reconnection
7. Performance optimization for BLE (window size limits)

**Deliverable**: Production-ready terminal with full features.

---

## 7. Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|------------|
| JSch `SocketFactory` complexity | Medium | Use in-process TCP proxy for BLE (Section 4.5) |
| BLE-Eth throughput limits | High | Enforce terminal window size limits (75x15 default), disable compression |
| ANSI parser completeness | Medium | Start with minimal parser, add sequences incrementally based on real usage |
| SSH auth failures on BLE tunnel | Medium | Use conservative SSH profile (Section 4.4.1), disable pubkey auth by default |
| Terminal rendering performance | Low | Use `SurfaceView` instead of `View` if redraw is slow |

---

## 8. SSH Profile for BLE-Eth

Based on the validated Python configuration (`ble-eth-ssh-usage.md`), the SSH client should use:

```
StrictHostKeyChecking=no
UserKnownHostsFile=/dev/null
ObscureKeystrokeTiming=no
PreferredAuthentications=keyboard-interactive,password
PubkeyAuthentication=no
Compression=no
KexAlgorithms=curve25519-sha256
HostKeyAlgorithms=ssh-ed25519,rsa-sha2-512,rsa-sha2-256
Ciphers=aes128-ctr
MACs=hmac-sha2-256
```

This profile avoids SSH features that are problematic over the BLE tunnel (compression, public key auth, multiple KEX algorithms).

---

## 9. Terminal Window Size Management

Per the BLE-Eth SSH usage guide, large terminal windows can cause BLE tunnel instability:

- Default: 80x24 (standard)
- For BLE-Eth: auto-restrict to 75x15 when BLE transport is active
- Show warning if user attempts to resize beyond safe limits on BLE
- For USB ECM: no restriction (USB bandwidth is sufficient)

The terminal should send `SIGWINCH` (via JSch `setPtySize()`) when the view size changes.

---

## 10. Testing Plan

| Test | Transport | Description |
|------|-----------|-------------|
| T1 | USB ECM | Connect to target, run basic commands (ls, pwd, echo) |
| T2 | USB ECM | Interactive session with vim/nano |
| T3 | USB ECM | Long-running session (top for 10 min) |
| T4 | BLE-Eth | Connect to target, run basic commands |
| T5 | BLE-Eth | Stability test at 75x15 window for 5 min |
| T6 | BLE-Eth | Stress test with `top` — verify disconnect behavior |
| T7 | BLE-Eth | Reconnect after BLE disconnect |
| T8 | Both | SSH key-based authentication |
| T9 | Both | Terminal resize during active session |
| T10 | Both | Copy/paste functionality |
