package com.openterface.keymod;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.annotation.SuppressLint;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.openterface.fragment.CompositeFragment;
import com.openterface.fragment.GamepadFragment;
import com.openterface.fragment.ImeSavedTextFragment;
import com.openterface.fragment.KeyboardFragment;
import com.openterface.fragment.KeyboardMouseFragment;
import com.openterface.fragment.KmProSettingsFragment;
import com.openterface.fragment.MacrosFragment;
import com.openterface.fragment.MouseFragment;
import com.openterface.fragment.PresentationFragment;
import com.openterface.fragment.ShortcutFragment;
import com.openterface.fragment.ShortcutHubFragment;
import com.openterface.fragment.VoiceInputFragment;
import com.openterface.keymod.prefs.KmProSubmodePrefs;
import com.openterface.keymod.BuildConfig;
import com.openterface.keymod.help.HelpImageConfig;
import com.openterface.keymod.help.HelpImageConfigManager;
import java.io.File;
import com.openterface.keymod.hid.Ch9329HostLockQuery;
import com.openterface.keymod.hid.Ch9329InboundParser;
import com.openterface.keymod.hid.HostKeyboardLockLeds;
import com.openterface.serial.UsbDeviceManager;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.color.MaterialColors;
import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleDevice;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.disposables.Disposable;
import android.Manifest;
import com.polidea.rxandroidble2.scan.ScanSettings;
import android.app.PendingIntent;

public class MainActivity extends AppCompatActivity implements BluetoothDialogFragment.BluetoothConnectionListener {

    private static final String TAG = "MainActivity";
    /** Google Form: bug reports (opened from nav drawer). */
    private static final String BUG_REPORT_FORM_URL =
            "https://docs.google.com/forms/d/e/1FAIpQLScTnJF_Pj_iIMvu8tBPaY_-n45-ffADUFAr8Ws-f6_TckWVTQ/viewform?usp=publish-editor";
    private int appliedThemeResId;
    private UsbSerialPort port;
    private boolean isReading = false;
    private Handler mSerialAsyncHandler;
    private final Ch9329InboundParser hostLockInboundParser = new Ch9329InboundParser();
    private final Handler hostLockPollHandler = new Handler(Looper.getMainLooper());
    private volatile boolean hostLockPollScheduled;
    private final Runnable hostLockPollRunnable =
            new Runnable() {
                @Override
                public void run() {
                    if (!hostLockPollScheduled) {
                        return;
                    }
                    try {
                        if (port != null && isReading) {
                            synchronized (port) {
                                port.write(Ch9329HostLockQuery.GET_INFO_PACKET, WRITE_WAIT_MILLIS_HOST_LOCK);
                            }
                        } else if (isServiceBound
                                && bluetoothService != null
                                && bluetoothService.isConnected()) {
                            bluetoothService.sendData(Ch9329HostLockQuery.GET_INFO_PACKET);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Host keyboard lock poll: " + e.getMessage());
                    }
                    hostLockPollHandler.postDelayed(this, HOST_LOCK_POLL_INTERVAL_MS);
                }
            };
    private static final int WRITE_WAIT_MILLIS_HOST_LOCK = 2000;
    private static final int HOST_LOCK_POLL_INTERVAL_MS = 450;
    private UsbDeviceManager.OnDataReadListener onDataReadListener;
    private static final String ACTION_USB_PERMISSION = "com.openterface.ch32v208serial.USB_PERMISSION";

    // New UI components for Phase 1
    private ImageView connectionButton;
    private ImageView signalBars;
    private ImageButton menuButton;
    private DrawerLayout drawerLayout;
    private View headerLayout;
    private final FragmentManager.FragmentLifecycleCallbacks chromeFragmentCallbacks =
            new FragmentManager.FragmentLifecycleCallbacks() {
                @Override
                public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment f) {
                    if (f.getId() == R.id.fragment_container) {
                        applyAppChromeForHostFragment();
                    }
                }
            };
    private String currentNavMode = LaunchPanelActivity.MODE_KEYBOARD_MOUSE;
    @Nullable
    private String pendingKbMouseSubmode;
    private View drawerImeRestoreTarget;
    private boolean restoreImeAfterDrawerClose;
    private DrawerCloseReason drawerCloseReason = DrawerCloseReason.NONE;

    private enum DrawerCloseReason {
        NONE,
        CANCEL,
        NAVIGATION
    }

    // Sidebar nav item views
    private LinearLayout navKeyboardMouse;
    private LinearLayout navKeyboardMousePro;
    private LinearLayout navGamepad;
    private LinearLayout navShortcuts;
    private LinearLayout navMacros;
    private LinearLayout navVoice;
    private LinearLayout navPresentation;
    private ImageButton targetOsHeaderButton;
    @Nullable
    private View headerRightCluster;
    @Nullable
    private View headerEndPullSpacer;
    @Nullable
    private View kmProSettingsOverlay;
    @Nullable
    private View imeSavedTextOverlay;
    @Nullable
    private ImeSavedTextFragment.Host imeSavedTextHost;
    @Nullable
    private ImageButton kmProSetupHeaderButton;
    @Nullable
    private ImageButton modeGuideHeaderButton;
    private final Handler modeGuideHandler = new Handler(Looper.getMainLooper());
    @Nullable
    private Runnable pendingModeGuideRunnable;
    @Nullable
    private HorizontalScrollView kmProHeaderTabsScroll;
    @Nullable
    private View kmProHeaderTabsSpacer;
    @Nullable
    private ImageButton kmProHeaderTabKeyboard;
    @Nullable
    private ImageButton kmProHeaderTabNumpad;
    @Nullable
    private ImageButton kmProHeaderTabCompose;
    private final ConnectionManager.ConnectionStateListener connectionStateListener =
            new ConnectionManager.ConnectionStateListener() {
                @Override
                public void onConnectionStateChanged(
                        ConnectionManager.ConnectionType type,
                        ConnectionManager.ConnectionState state) {
                    runOnUiThread(() -> {
                        updateConnectionButton(type, state);

                        // Update fragments with new port if USB connected.
                        if (type == ConnectionManager.ConnectionType.USB
                                && state == ConnectionManager.ConnectionState.CONNECTED) {
                            if (bluetoothService != null && bluetoothService.isConnected()) {
                                bluetoothService.disconnect();
                            }
                            port = connectionManager.getUsbPort();
                            isUsbConnected = true;
                            isBluetoothConnected = false;
                            updateFragmentsWithPort(port);
                            startReading();
                        } else if (type == ConnectionManager.ConnectionType.BLUETOOTH
                                && state == ConnectionManager.ConnectionState.CONNECTED) {
                            isUsbConnected = false;
                            isBluetoothConnected = true;
                            if (port != null) {
                                try {
                                    port.close();
                                } catch (IOException e) {
                                    Log.e(TAG, "Error closing port: " + e.getMessage());
                                }
                                port = null;
                                updateFragmentsWithPort(null);
                            }
                        } else if (state == ConnectionManager.ConnectionState.DISCONNECTED
                                || state == ConnectionManager.ConnectionState.ERROR) {
                            isUsbConnected = false;
                            isBluetoothConnected = false;
                            stopHostLockPolling();
                            HostKeyboardLockLeds.get().reset();
                            if (port != null) {
                                try {
                                    port.close();
                                } catch (IOException e) {
                                    Log.e(TAG, "Error closing port: " + e.getMessage());
                                }
                                port = null;
                                updateFragmentsWithPort(null);
                            }
                        }
                        notifyBasicChromeFragments();
                    });
                }

                @Override
                public void onConnectionError(String error) {
                    runOnUiThread(
                            () ->
                                    UiToastLimiter.show(
                                            MainActivity.this,
                                            "main_connection_error",
                                            error,
                                            Toast.LENGTH_SHORT,
                                            1800));
                }

                @Override
                public void onBluetoothRssiChanged(int rssi) {
                    // Top header intentionally does not render RSSI bars.
                }
            };

    /** Global target OS preference key */
    private static final String PREF_TARGET_OS = "target_os";

    /** Callback notified when target OS changes */
    public interface OnTargetOsChangeListener {
        void onTargetOsChanged(String targetOs);
    }
    private final List<OnTargetOsChangeListener> osChangeListeners = new ArrayList<>();

    public void addOsChangeListener(OnTargetOsChangeListener listener) {
        osChangeListeners.add(listener);
    }
    public void removeOsChangeListener(OnTargetOsChangeListener listener) {
        osChangeListeners.remove(listener);
    }
    public String getTargetOs() {
        return getSharedPreferences("AppPrefs", MODE_PRIVATE).getString(PREF_TARGET_OS, "macos");
    }
    public void setTargetOs(String os) {
        getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().putString(PREF_TARGET_OS, os).apply();
        for (OnTargetOsChangeListener listener : osChangeListeners) {
            listener.onTargetOsChanged(os);
        }
        updateTargetOsHeaderIcon();
        notifyBasicChromeFragments();
    }
    
    // Old button bar components (now hidden but kept for backward compatibility)
    private Button usbConnect, bluetooth, keyBoard, mouse, keyBoardMouse, question, info, shortcut;
    private Drawable usbConnectDrawable, bluetoothDrawable, keyBoardDrawable, mouseDrawable, keyBoardMouseDrawable, questionDrawable, infoDrawable, shortcutDrawable;
    private Button activeButton;

    private RxBleClient rxBleClient;
    private Disposable scanSubscription;
    private static final int PERMISSION_REQUEST_CODE = 1;

    private BluetoothService bluetoothService;
    private boolean isServiceBound;

    // Connection Manager
    private ConnectionManager connectionManager;
    
    // Bluetooth Auto Connect Manager
    private BluetoothAutoConnectManager autoConnectManager;
    
    private boolean isUsbConnected = false; // Track USB connection state
    public boolean isBluetoothConnected = false; // Track Bluetooth connection state
    private boolean receiversRegistered = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BluetoothService.BluetoothBinder binder = (BluetoothService.BluetoothBinder) service;
            bluetoothService = binder.getService();
            isServiceBound = true;
            bluetoothService.setRxBleClient(rxBleClient);
            bluetoothService.setHostLockInboundParser(hostLockInboundParser);
            Log.d(TAG, "Bound to BluetoothService");

            // Keep ConnectionManager in sync with the newly bound service.
            if (connectionManager != null) {
                connectionManager.setBluetoothService(bluetoothService);
            }
            
            // Initialize auto-connect manager after service is bound
            initializeAutoConnect();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
            bluetoothService = null;
            Log.d(TAG, "Unbound from BluetoothService");

            if (connectionManager != null) {
                connectionManager.setBluetoothService(null);
            }
        }
    };

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    // Use ConnectionManager for USB setup; drop BLE so HID transports match USB.
                    if (bluetoothService != null && bluetoothService.isConnected()) {
                        bluetoothService.disconnect();
                    }
                    if (connectionManager != null) {
                        connectionManager.connectUsb();
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                if (connectionManager != null && 
                    connectionManager.getCurrentConnectionType() == ConnectionManager.ConnectionType.USB) {
                    connectionManager.disconnect();
                    UiToastLimiter.show(
                            context,
                            "usb_device_disconnected",
                            context.getString(R.string.main_toast_usb_device_disconnected),
                            Toast.LENGTH_SHORT,
                            1800);
                }
            }
        }
    };

    private final BroadcastReceiver usbPermissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null && connectionManager != null) {
                            // Reinitialize after the permission is granted
                            connectionManager.connectUsb();
                        }
                    } else {
                        UiToastLimiter.show(
                                context,
                                "usb_permission_denied",
                                context.getString(R.string.main_toast_usb_permission_denied),
                                Toast.LENGTH_SHORT,
                                1800);
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppLocaleManager.applyPersistedLocales(this);
        ThemeManager.applyTheme(this);
        appliedThemeResId = ThemeManager.getSelectedThemeResId(this);
        super.onCreate(savedInstanceState);
        Intent launchIntent = getIntent();
        String earlyLaunchMode = launchIntent.getStringExtra("launch_mode");
        String earlyKbSub =
                KeyboardMouseFragment.normalizeKmBasicSubmode(
                        launchIntent.getStringExtra(KeyboardMouseFragment.EXTRA_INITIAL_SUBMODE));
        if (shouldLockLandscapeForKmBasicKeyboardIntent(earlyLaunchMode, earlyKbSub)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        // Initialize Connection Manager
        connectionManager = new ConnectionManager(this);
        connectionManager.setMainActivity(this); // Pass MainActivity reference
        setupConnectionStateListener();
        
        initializeUIComponents();
        if (connectionManager != null) {
            updateConnectionButton(
                    connectionManager.getCurrentConnectionType(),
                    connectionManager.getCurrentConnectionState());
        }

        rxBleClient = RxBleClient.create(this);
        Intent intent = new Intent(this, BluetoothService.class);
        startService(intent);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        
        // Set BluetoothService in ConnectionManager once bound
        if (connectionManager != null) {
            connectionManager.setBluetoothService(bluetoothService);
        }

        // Handle launch mode from LaunchPanelActivity
        pendingKbMouseSubmode =
                KeyboardMouseFragment.normalizeKmBasicSubmode(
                        getIntent().getStringExtra(KeyboardMouseFragment.EXTRA_INITIAL_SUBMODE));
        String launchMode = getIntent().getStringExtra("launch_mode");
        if (launchMode != null) {
            handleLaunchMode(launchMode);
        } else {
            currentNavMode = LaunchPanelActivity.MODE_KEYBOARD_MOUSE;
            showKeyboardMouseFragment(consumePendingKbMouseSubmode());
        }
        
        // Note: Bluetooth auto-connect will be initialized after service is bound
        // USB auto-connect is still handled by ConnectionManager
        new Handler().postDelayed(() -> {
            if ("USB".equals(connectionManager.getLastConnectionType())) {
                connectionManager.autoConnect();
            }
        }, 500);

        // Show beginner tutorial on first launch (Basic Keyboard & Mouse only; steps target that UI)
        if (!TutorialOverlay.isShown(this)
                && LaunchPanelActivity.MODE_KEYBOARD_MOUSE.equals(currentNavMode)) {
            new Handler().postDelayed(this::showTutorial, 800);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        int selectedThemeResId = ThemeManager.getSelectedThemeResId(this);
        if (selectedThemeResId != appliedThemeResId) {
            recreate();
            return;
        }
        registerUsbReceiversIfNeeded();
        // Don't auto-setup USB here, ConnectionManager handles it
        
        applyAppChromeForHostFragment();
    }
    
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            updateImmersiveForTopFragment();
        }
    }
    
    private void setImmersiveMode() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterUsbReceiversIfNeeded();
    }

    private void registerUsbReceiversIfNeeded() {
        if (receiversRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(ACTION_USB_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(
                    usbPermissionReceiver,
                    new IntentFilter(ACTION_USB_PERMISSION),
                    Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(usbReceiver, filter);
            registerReceiver(usbPermissionReceiver, new IntentFilter(ACTION_USB_PERMISSION));
        }
        receiversRegistered = true;
    }

    private void unregisterUsbReceiversIfNeeded() {
        if (!receiversRegistered) {
            return;
        }
        try {
            unregisterReceiver(usbReceiver);
        } catch (IllegalArgumentException ignored) {
            // Receiver may already be unregistered during lifecycle transitions.
        }
        try {
            unregisterReceiver(usbPermissionReceiver);
        } catch (IllegalArgumentException ignored) {
            // Receiver may already be unregistered during lifecycle transitions.
        }
        receiversRegistered = false;
    }

    @Override
    protected void onDestroy() {
        getSupportFragmentManager().unregisterFragmentLifecycleCallbacks(chromeFragmentCallbacks);
        modeGuideHandler.removeCallbacksAndMessages(null);
        stopHostLockPolling();
        if (connectionManager != null) {
            connectionManager.removeConnectionStateListener(connectionStateListener);
        }
        super.onDestroy();
        if (scanSubscription != null && !scanSubscription.isDisposed()) {
            scanSubscription.dispose();
        }
        
        // Stop auto-connect manager
        if (autoConnectManager != null) {
            autoConnectManager.stopAutoConnect();
        }
        
        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }
        Intent intent = new Intent(this, BluetoothService.class);
        stopService(intent); // stop service
    }

    private void initializeUIComponents() {
        updateImmersiveForTopFragment();

        // Initialize new header buttons
        connectionButton = findViewById(R.id.connection_button);
        signalBars = findViewById(R.id.signal_bars);
        menuButton = findViewById(R.id.menu_button);

        View connectionContainer = findViewById(R.id.connection_container);
        connectionContainer.setOnClickListener(v -> showConnectionDialog());
        
        // Initialize drawer layout and nav items
        drawerLayout = findViewById(R.id.drawer_layout);
        headerLayout = findViewById(R.id.header_layout);
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(chromeFragmentCallbacks, false);
        navKeyboardMouse = findViewById(R.id.nav_keyboard_mouse);
        navKeyboardMousePro = findViewById(R.id.nav_keyboard_mouse_pro);
        navGamepad = findViewById(R.id.nav_gamepad);
        navShortcuts = findViewById(R.id.nav_shortcuts);
        navMacros = findViewById(R.id.nav_macros);
        navVoice = findViewById(R.id.nav_voice);
        navPresentation = findViewById(R.id.nav_presentation);
        setupDrawerImeBehavior();

        targetOsHeaderButton = findViewById(R.id.target_os_header_button);
        headerRightCluster = findViewById(R.id.header_right_cluster);
        headerEndPullSpacer = findViewById(R.id.header_end_pull_spacer);
        if (targetOsHeaderButton != null) {
            targetOsHeaderButton.setOnClickListener(v -> showTargetOsPickerDialog());
            updateTargetOsHeaderIcon();
        }
        kmProSettingsOverlay = findViewById(R.id.km_pro_settings_overlay);
        imeSavedTextOverlay = findViewById(R.id.ime_saved_text_overlay);
        kmProSetupHeaderButton = findViewById(R.id.km_pro_setup_header_button);
        modeGuideHeaderButton = findViewById(R.id.mode_guide_header_button);
        if (modeGuideHeaderButton != null) {
            modeGuideHeaderButton.setOnClickListener(v -> openModeGuideSheet());
        }
        applyHeaderRightClusterNavInsets();
        setupKmProHeaderSubmodeTabs();

        // Display app version in sidebar footer
        TextView versionText = findViewById(R.id.version_text);
        if (versionText != null) {
            versionText.setText(String.format("v%s (%d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
        }
        
        // Initialize old buttons (kept for backward compatibility, but hidden - may be null)
        // These are in hidden layout, won't be found
        keyBoard = findViewById(R.id.keyBoard);
        mouse = findViewById(R.id.mouse);
        keyBoardMouse = findViewById(R.id.keyBoardMouse);
        question = findViewById(R.id.question);
        info = findViewById(R.id.info);
        shortcut = findViewById(R.id.shortcut);

        if (keyBoard != null) keyBoardDrawable = keyBoard.getCompoundDrawables()[1];
        if (mouse != null) mouseDrawable = mouse.getCompoundDrawables()[1];
        if (keyBoardMouse != null) keyBoardMouseDrawable = keyBoardMouse.getCompoundDrawables()[1];
        if (shortcut != null) shortcutDrawable = shortcut.getCompoundDrawables()[1];

        setupButtonListeners();
    }

    /**
     * Hide the weighted {@code app_title} so top mode shortcut icons stay leading-aligned (same as
     * Keyboard & Mouse Pro). Brand remains in drawer / in-mode chrome where applicable.
     */
    private static boolean hideHeaderAppTitleForFragment(@Nullable Fragment f) {
        return f instanceof CompositeFragment
                || f instanceof PresentationFragment
                || f instanceof ShortcutHubFragment
                || f instanceof MacrosFragment
                || f instanceof VoiceInputFragment;
    }

    private void applyAppChromeForHostFragment() {
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        boolean hideAppHeader = f instanceof KeyboardMouseFragment || f instanceof GamepadFragment;
        if (headerLayout != null) {
            headerLayout.setVisibility(hideAppHeader ? View.GONE : View.VISIBLE);
        }
        TextView appTitle = findViewById(R.id.app_title);
        if (appTitle != null) {
            if (hideAppHeader) {
                appTitle.setVisibility(View.VISIBLE);
            } else {
                appTitle.setVisibility(hideHeaderAppTitleForFragment(f) ? View.GONE : View.VISIBLE);
            }
        }
        updateImmersiveForTopFragment();
        applyWindowSystemBarAppearanceForHostFragment(f);
        if (drawerLayout != null) {
            // Pro + gamepad: lock edge swipe so left-edge typing / gestures do not open the drawer;
            // hamburger and programmatic openDrawer() still work.
            int lockMode =
                    (f instanceof GamepadFragment || f instanceof CompositeFragment)
                            ? DrawerLayout.LOCK_MODE_LOCKED_CLOSED
                            : DrawerLayout.LOCK_MODE_UNLOCKED;
            drawerLayout.setDrawerLockMode(lockMode, GravityCompat.START);
        }
        if (!(f instanceof CompositeFragment)
                && kmProSettingsOverlay != null
                && kmProSettingsOverlay.getVisibility() == View.VISIBLE) {
            hideKmProSettingsOverlay();
        } else {
            updateKmProHeaderSetupChrome();
        }
        if (!(f instanceof CompositeFragment)
                && imeSavedTextOverlay != null
                && imeSavedTextOverlay.getVisibility() == View.VISIBLE) {
            hideImeSavedTextOverlay();
        }
        applyKmProHeaderSubmodeChrome(f);
        updateModeGuideHeaderVisibility(f);
        scheduleDeferredModeGuideCheck(f);
    }

    private void setupKmProHeaderSubmodeTabs() {
        kmProHeaderTabsScroll = findViewById(R.id.km_pro_header_tabs_scroll);
        kmProHeaderTabsSpacer = findViewById(R.id.km_pro_header_tabs_spacer);
        kmProHeaderTabKeyboard = findViewById(R.id.km_pro_header_tab_keyboard);
        kmProHeaderTabNumpad = findViewById(R.id.km_pro_header_tab_numpad);
        kmProHeaderTabCompose = findViewById(R.id.km_pro_header_tab_compose);
        if (kmProHeaderTabKeyboard != null) {
            kmProHeaderTabKeyboard.setOnClickListener(
                    v -> onKmProHeaderSubmodeTabClicked(KmProSubmodePrefs.SUBMODE_KEYBOARD));
        }
        if (kmProHeaderTabNumpad != null) {
            kmProHeaderTabNumpad.setOnClickListener(
                    v -> onKmProHeaderSubmodeTabClicked(KmProSubmodePrefs.SUBMODE_NUMPAD));
        }
        if (kmProHeaderTabCompose != null) {
            kmProHeaderTabCompose.setOnClickListener(
                    v -> onKmProHeaderSubmodeTabClicked(KmProSubmodePrefs.SUBMODE_COMPOSE));
        }
        applyKmProHeaderTabsScrollInsets();
    }

    /**
     * Keeps KM Pro submode icon tabs clear of display cutouts: uses {@link WindowInsetsCompat.Type#displayCutout()}
     * left/right. Portrait + top-center cutout with no lateral inset: adds a small inset on the
     * <strong>trailing</strong> edge only so tabs stay close to the menu. Does not apply cutout top
     * padding on this view (would clip tab backgrounds inside the fixed header height).
     */
    private void applyKmProHeaderTabsScrollInsets() {
        if (kmProHeaderTabsScroll == null) {
            return;
        }
        final int baseStart = ViewCompat.getPaddingStart(kmProHeaderTabsScroll);
        final int baseTop = kmProHeaderTabsScroll.getPaddingTop();
        final int baseEnd = ViewCompat.getPaddingEnd(kmProHeaderTabsScroll);
        final int baseBottom = kmProHeaderTabsScroll.getPaddingBottom();
        final float density = getResources().getDisplayMetrics().density;
        // Light lateral inset when portrait has a top-center cutout but no reported side cutout.
        // Keep modest so tabs stay nearer the menu; avoid large symmetric padding on both edges.
        final int lateralGuardForTopCenterHolePx = Math.round(6f * density);
        ViewCompat.setOnApplyWindowInsetsListener(
                kmProHeaderTabsScroll,
                (v, windowInsets) -> {
                    Insets cut =
                            windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());
                    boolean portrait =
                            getResources().getConfiguration().orientation
                                    == Configuration.ORIENTATION_PORTRAIT;
                    int padStart = baseStart + cut.left;
                    int padEnd = baseEnd + cut.right;
                    if (portrait
                            && cut.top >= lateralGuardForTopCenterHolePx
                            && cut.left == 0
                            && cut.right == 0) {
                        padEnd += lateralGuardForTopCenterHolePx;
                    }
                    int padTop = baseTop;
                    ViewCompat.setPaddingRelative(v, padStart, padTop, padEnd, baseBottom);
                    return windowInsets;
                });
        ViewCompat.requestApplyInsets(kmProHeaderTabsScroll);
    }

    private void onKmProHeaderSubmodeTabClicked(@NonNull String submodeKey) {
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (f instanceof CompositeFragment) {
            ((CompositeFragment) f).applyKmProSubmodeFromHost(submodeKey);
        }
        syncKmProHeaderTabSelectionUi();
    }

    /** Updates selected state for KM Pro header tabs from {@link KmProSubmodePrefs}. */
    public void syncKmProHeaderTabSelectionUi() {
        if (kmProHeaderTabKeyboard == null) {
            return;
        }
        String sub = KmProSubmodePrefs.getSubmode(this);
        kmProHeaderTabKeyboard.setSelected(KmProSubmodePrefs.SUBMODE_KEYBOARD.equals(sub));
        if (kmProHeaderTabNumpad != null) {
            kmProHeaderTabNumpad.setSelected(KmProSubmodePrefs.SUBMODE_NUMPAD.equals(sub));
        }
        if (kmProHeaderTabCompose != null) {
            kmProHeaderTabCompose.setSelected(KmProSubmodePrefs.SUBMODE_COMPOSE.equals(sub));
        }
    }

    private void applyKmProHeaderSubmodeChrome(@Nullable Fragment f) {
        boolean show = f instanceof CompositeFragment;
        if (kmProHeaderTabsScroll != null) {
            kmProHeaderTabsScroll.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (show) {
            syncKmProHeaderTabSelectionUi();
        }
        syncKmProHeaderTabsSpacerVisibility();
    }

    /**
     * When KM Pro icon tabs replace the weighted title, a dedicated {@code Space} absorbs flex
     * between the tab strip and the right cluster. Otherwise the scroll view would grow to
     * {@code layout_weight} width and leave a dead band beside the icons.
     */
    private void syncKmProHeaderTabsSpacerVisibility() {
        if (kmProHeaderTabsSpacer != null) {
            TextView appTitle = findViewById(R.id.app_title);
            boolean headerShown = headerLayout == null || headerLayout.getVisibility() == View.VISIBLE;
            boolean showSpacer =
                    headerShown
                            && kmProHeaderTabsScroll != null
                            && kmProHeaderTabsScroll.getVisibility() == View.VISIBLE
                            && appTitle != null
                            && appTitle.getVisibility() != View.VISIBLE;
            kmProHeaderTabsSpacer.setVisibility(showSpacer ? View.VISIBLE : View.GONE);
        }
        syncHeaderEndPullSpacerVisibility();
    }

    /**
     * When the weighted app title and KM Pro tab strip are both hidden, the header row would
     * otherwise leave the right cluster immediately after the menu. A weighted spacer pulls
     * target OS / mode setup / connection to the trailing edge (Presentation, Shortcut Hub, etc.).
     */
    private void syncHeaderEndPullSpacerVisibility() {
        if (headerEndPullSpacer == null) {
            return;
        }
        if (headerLayout == null || headerLayout.getVisibility() != View.VISIBLE) {
            headerEndPullSpacer.setVisibility(View.GONE);
            return;
        }
        TextView appTitle = findViewById(R.id.app_title);
        boolean titleVisible = appTitle != null && appTitle.getVisibility() == View.VISIBLE;
        boolean proTabsVisible =
                kmProHeaderTabsScroll != null
                        && kmProHeaderTabsScroll.getVisibility() == View.VISIBLE;
        headerEndPullSpacer.setVisibility(!titleVisible && !proTabsVisible ? View.VISIBLE : View.GONE);
    }

    private void toggleKmProSettingsOverlay() {
        if (kmProSettingsOverlay == null) {
            return;
        }
        if (kmProSettingsOverlay.getVisibility() == View.VISIBLE) {
            hideKmProSettingsOverlay();
        } else {
            showKmProSettingsOverlay();
        }
    }

    /** Opens full-screen KM Pro settings (strip display, alternate hints, active Shortcut Hub profile). */
    public void showKmProSettingsOverlay() {
        if (kmProSettingsOverlay == null) {
            return;
        }
        View currentFocus = getCurrentFocus();
        if (currentFocus != null) {
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            }
        }
        kmProSettingsOverlay.setVisibility(View.VISIBLE);
        FragmentManager fm = getSupportFragmentManager();
        try {
            fm.executePendingTransactions();
        } catch (IllegalStateException e) {
            Log.w(TAG, "executePendingTransactions before KM Pro settings show", e);
        }
        FragmentTransaction showTx =
                fm.beginTransaction().replace(R.id.km_pro_settings_overlay, new KmProSettingsFragment());
        try {
            if (!fm.isStateSaved()) {
                showTx.commitNow();
            } else {
                showTx.commitAllowingStateLoss();
            }
        } catch (IllegalStateException e) {
            Log.w(TAG, "KM Pro settings show: commitNow failed, retrying with commitAllowingStateLoss", e);
            fm.beginTransaction()
                    .replace(R.id.km_pro_settings_overlay, new KmProSettingsFragment())
                    .commitAllowingStateLoss();
        }
        updateKmProHeaderSetupChrome();
    }

    /** Called from {@link KmProSettingsFragment} when closing setup. */
    public void hideKmProSettingsOverlay() {
        if (kmProSettingsOverlay == null) {
            return;
        }
        FragmentManager fm = getSupportFragmentManager();
        Fragment existing = fm.findFragmentById(R.id.km_pro_settings_overlay);
        if (existing != null) {
            FragmentTransaction hideTx = fm.beginTransaction().remove(existing);
            try {
                if (!fm.isStateSaved()) {
                    hideTx.commitNow();
                } else {
                    hideTx.commitAllowingStateLoss();
                }
            } catch (IllegalStateException e) {
                Log.w(TAG, "KM Pro settings hide: commitNow failed, retrying with commitAllowingStateLoss", e);
                fm.beginTransaction().remove(existing).commitAllowingStateLoss();
            }
        }
        kmProSettingsOverlay.setVisibility(View.GONE);
        refreshOpenKeyboardShortcutStripFromPrefs();
        updateKmProHeaderSetupChrome();
    }

    /** Full-screen overlay for IME saved compose texts library. */
    public void showImeSavedTextOverlay(@NonNull ImeSavedTextFragment.Host host) {
        if (imeSavedTextOverlay == null) {
            return;
        }
        View currentFocus = getCurrentFocus();
        if (currentFocus != null) {
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            }
        }
        imeSavedTextHost = host;
        imeSavedTextOverlay.setVisibility(View.VISIBLE);
        FragmentManager fm = getSupportFragmentManager();
        try {
            fm.executePendingTransactions();
        } catch (IllegalStateException e) {
            Log.w(TAG, "executePendingTransactions before IME saved texts show", e);
        }
        FragmentTransaction showTx =
                fm.beginTransaction().replace(R.id.ime_saved_text_overlay, new ImeSavedTextFragment());
        try {
            if (!fm.isStateSaved()) {
                showTx.commitNow();
            } else {
                showTx.commitAllowingStateLoss();
            }
        } catch (IllegalStateException e) {
            Log.w(TAG, "IME saved texts show: commitNow failed, retrying with commitAllowingStateLoss", e);
            fm.beginTransaction()
                    .replace(R.id.ime_saved_text_overlay, new ImeSavedTextFragment())
                    .commitAllowingStateLoss();
        }
    }

    @Nullable
    public ImeSavedTextFragment.Host getImeSavedTextHost() {
        return imeSavedTextHost;
    }

    public void hideImeSavedTextOverlay() {
        if (imeSavedTextOverlay == null) {
            return;
        }
        FragmentManager fm = getSupportFragmentManager();
        Fragment existing = fm.findFragmentById(R.id.ime_saved_text_overlay);
        if (existing != null) {
            FragmentTransaction hideTx = fm.beginTransaction().remove(existing);
            try {
                if (!fm.isStateSaved()) {
                    hideTx.commitNow();
                } else {
                    hideTx.commitAllowingStateLoss();
                }
            } catch (IllegalStateException e) {
                Log.w(TAG, "IME saved texts hide: commitNow failed, retrying with commitAllowingStateLoss", e);
                fm.beginTransaction().remove(existing).commitAllowingStateLoss();
            }
        }
        imeSavedTextOverlay.setVisibility(View.GONE);
        imeSavedTextHost = null;
    }

    /**
     * Header gear between Target OS and connection: KM Pro setup overlay on Pro mode, or Shortcut Hub
     * per-profile display settings when a profile detail is open.
     */
    public void refreshHeaderSetupGearChrome() {
        updateKmProHeaderSetupChrome();
        updateModeGuideHeaderVisibility(
                getSupportFragmentManager().findFragmentById(R.id.fragment_container));
    }

    private void updateKmProHeaderSetupChrome() {
        if (kmProSetupHeaderButton == null) {
            return;
        }
        Fragment host = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        boolean kmProOverlayOpen =
                kmProSettingsOverlay != null && kmProSettingsOverlay.getVisibility() == View.VISIBLE;

        if (host instanceof CompositeFragment) {
            kmProSetupHeaderButton.setVisibility(View.VISIBLE);
            kmProSetupHeaderButton.setOnClickListener(v -> toggleKmProSettingsOverlay());
            kmProSetupHeaderButton.setContentDescription(getString(R.string.km_pro_setup_button_cd));
            int tint =
                    kmProOverlayOpen
                            ? ThemeManager.getColorPrimary(this)
                            : ContextCompat.getColor(this, R.color.text_secondary);
            kmProSetupHeaderButton.setImageTintList(ColorStateList.valueOf(tint));
            return;
        }

        if (host instanceof ShortcutHubFragment) {
            ShortcutHubFragment hub = (ShortcutHubFragment) host;
            if (hub.isShortcutHubProfileDetailShowing()) {
                kmProSetupHeaderButton.setVisibility(View.VISIBLE);
                kmProSetupHeaderButton.setOnClickListener(v -> hub.openHubDetailSettingsFromHost());
                kmProSetupHeaderButton.setContentDescription(getString(R.string.shortcut_hub_detail_settings_cd));
                kmProSetupHeaderButton.setImageTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(this, R.color.text_secondary)));
                return;
            }
        }

        kmProSetupHeaderButton.setVisibility(View.GONE);
        kmProSetupHeaderButton.setOnClickListener(null);
    }

    /**
     * Gamepad mode uses a fixed dark shell; match status/navigation bar. Other fragments follow the
     * activity theme (DayNight).
     */
    private void applyWindowSystemBarAppearanceForHostFragment(@Nullable Fragment f) {
        Window window = getWindow();
        if (window == null) {
            return;
        }
        WindowInsetsControllerCompat wic = WindowCompat.getInsetsController(window, window.getDecorView());
        if (f instanceof GamepadFragment) {
            int bar = ContextCompat.getColor(this, R.color.gamepad_window_system_bar);
            window.setStatusBarColor(bar);
            window.setNavigationBarColor(bar);
            if (wic != null) {
                wic.setAppearanceLightStatusBars(false);
                wic.setAppearanceLightNavigationBars(false);
            }
            return;
        }
        TypedArray a = getTheme().obtainStyledAttributes(new int[]{
                android.R.attr.statusBarColor,
                android.R.attr.navigationBarColor});
        try {
            int fallback = ContextCompat.getColor(this, R.color.background_light);
            int status = a.getColor(0, fallback);
            int nav = a.getColor(1, fallback);
            window.setStatusBarColor(status);
            window.setNavigationBarColor(nav);
            if (wic != null) {
                boolean light = MaterialColors.isColorLight(status);
                wic.setAppearanceLightStatusBars(light);
                wic.setAppearanceLightNavigationBars(light);
            }
        } finally {
            a.recycle();
        }
    }

    /**
     * Hides the system navigation bar (Back / Home / Recents) with immersive sticky flags, same for
     * KM Basic and KM Pro. Keyboard / touchpad layouts rely on window inset padding
     * (see Basic fragments) rather than clearing system UI for portrait.
     */
    @SuppressLint("deprecation")
    private void updateImmersiveForTopFragment() {
        setImmersiveMode();
    }

    public void openDrawerForBasic() {
        if (drawerLayout == null) {
            return;
        }
        snapshotImeBeforeOpeningDrawer();
        drawerLayout.openDrawer(GravityCompat.START);
    }

    public DrawerLayout getDrawerLayout() {
        return drawerLayout;
    }

    public boolean isBluetoothServiceBound() {
        return isServiceBound;
    }

    public void showTargetOsPickerDialogFromBasic() {
        showTargetOsPickerDialog();
    }

    public void showConnectionDialogFromBasic() {
        showConnectionDialog();
    }

    public void applyBasicTargetOsIcon(@Nullable ImageButton button) {
        if (button == null) {
            return;
        }
        String targetOs = getTargetOs();
        int iconRes;
        int nameRes;
        if ("windows".equals(targetOs)) {
            iconRes = R.drawable.ic_os_windows;
            nameRes = R.string.target_os_windows;
        } else if ("linux".equals(targetOs)) {
            iconRes = R.drawable.ic_os_linux;
            nameRes = R.string.target_os_linux;
        } else {
            iconRes = R.drawable.ic_os_macos;
            nameRes = R.string.target_os_macos;
        }
        button.setImageResource(iconRes);
        button.setContentDescription(getString(R.string.target_os_header_cd_selected, getString(nameRes)));
        button.setColorFilter(headerNeutralActionTint(), PorterDuff.Mode.SRC_IN);
    }

    public void applyBasicConnectionIcon(
            @Nullable ImageView icon,
            ConnectionManager.ConnectionType type,
            ConnectionManager.ConnectionState state) {
        if (icon == null) {
            return;
        }
        icon.setImageResource(connectionHeaderIconRes(type, state));
        icon.setColorFilter(headerConnectionClusterTint(type, state), PorterDuff.Mode.SRC_IN);
        int cdRes;
        switch (type) {
            case USB:
                cdRes = R.string.connection_medium_usb;
                break;
            case BLUETOOTH:
                if (state == ConnectionManager.ConnectionState.CONNECTING) {
                    cdRes = R.string.connection_header_bluetooth_searching;
                } else if (state == ConnectionManager.ConnectionState.CONNECTED) {
                    cdRes = R.string.connection_header_bluetooth_connected;
                } else {
                    cdRes = R.string.connection_medium_bluetooth;
                }
                break;
            default:
                cdRes = R.string.connection;
                break;
        }
        icon.setContentDescription(getString(cdRes));

        // Rotation animation for Bluetooth connecting
        if (type == ConnectionManager.ConnectionType.BLUETOOTH
                && state == ConnectionManager.ConnectionState.CONNECTING) {
            android.view.animation.Animation spin =
                    android.view.animation.AnimationUtils.loadAnimation(
                            this, R.anim.connection_spinning);
            icon.startAnimation(spin);
        } else {
            icon.clearAnimation();
        }
    }

    public void notifyBasicChromeFragments() {
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (f instanceof KeyboardMouseFragment) {
            ((KeyboardMouseFragment) f).refreshBasicEmbeddedChrome();
        } else if (f instanceof GamepadFragment) {
            ((GamepadFragment) f).refreshGamepadEmbeddedChrome();
        }
    }

    private void setupDrawerImeBehavior() {
        if (drawerLayout == null) {
            return;
        }
        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(View drawerView) {
                hideImeForDrawerTransition();
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                if (drawerCloseReason == DrawerCloseReason.CANCEL && restoreImeAfterDrawerClose) {
                    tryRestoreImeAfterDrawerClose();
                }
                clearDrawerImeSnapshot();
            }
        });
    }

    private void snapshotImeBeforeOpeningDrawer() {
        View focused = getCurrentFocus();
        if (focused == null) {
            clearDrawerImeSnapshot();
            drawerCloseReason = DrawerCloseReason.CANCEL;
            return;
        }
        drawerImeRestoreTarget = focused;
        restoreImeAfterDrawerClose = isImeRestoreCandidate(focused);
        drawerCloseReason = DrawerCloseReason.CANCEL;
        focused.clearFocus();
        hideImeForDrawerTransition();
    }

    private void markDrawerCloseAsNavigation() {
        drawerCloseReason = DrawerCloseReason.NAVIGATION;
        restoreImeAfterDrawerClose = false;
        drawerImeRestoreTarget = null;
    }

    private void clearDrawerImeSnapshot() {
        drawerImeRestoreTarget = null;
        restoreImeAfterDrawerClose = false;
        drawerCloseReason = DrawerCloseReason.NONE;
    }

    private boolean isImeRestoreCandidate(View view) {
        return view != null
                && view.getWindowToken() != null
                && view.isShown()
                && view.isEnabled()
                && view.isFocusable();
    }

    private void hideImeForDrawerTransition() {
        View focused = getCurrentFocus();
        if (focused != null) {
            focused.clearFocus();
        }
        InputMethodManager imm =
                (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm == null) {
            return;
        }
        android.os.IBinder token = null;
        if (focused != null) {
            token = focused.getWindowToken();
        }
        if (token == null && drawerLayout != null) {
            token = drawerLayout.getWindowToken();
        }
        if (token == null && getWindow() != null && getWindow().getDecorView() != null) {
            token = getWindow().getDecorView().getWindowToken();
        }
        if (token != null) {
            imm.hideSoftInputFromWindow(token, 0);
        }
    }

    private void tryRestoreImeAfterDrawerClose() {
        View target = drawerImeRestoreTarget;
        if (!isImeRestoreCandidate(target) || !ViewCompat.isAttachedToWindow(target)) {
            return;
        }
        target.requestFocus();
        InputMethodManager imm =
                (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    /**
     * In landscape, 3-button nav can sit on the physical end; pad the Target OS + connection cluster
     * so it stays clear of system bars (same max merge as {@link com.openterface.fragment.BasicComposeFragment}).
     */
    private void applyHeaderRightClusterNavInsets() {
        if (headerRightCluster == null) {
            return;
        }
        final int baseStart = ViewCompat.getPaddingStart(headerRightCluster);
        final int baseTop = headerRightCluster.getPaddingTop();
        final int baseEnd = ViewCompat.getPaddingEnd(headerRightCluster);
        final int baseBottom = headerRightCluster.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(
                headerRightCluster,
                (v, windowInsets) -> {
                    boolean landscape =
                            getResources().getConfiguration().orientation
                                    == Configuration.ORIENTATION_LANDSCAPE;
                    int endPad = baseEnd;
                    if (landscape) {
                        Insets bars =
                                windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
                        Insets cut =
                                windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());
                        endPad = baseEnd + Math.max(bars.right, cut.right);
                    }
                    ViewCompat.setPaddingRelative(v, baseStart, baseTop, endPad, baseBottom);
                    return windowInsets;
                });
        ViewCompat.requestApplyInsets(headerRightCluster);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (headerRightCluster != null) {
            ViewCompat.requestApplyInsets(headerRightCluster);
        }
        if (kmProHeaderTabsScroll != null) {
            ViewCompat.requestApplyInsets(kmProHeaderTabsScroll);
        }
        applyAppChromeForHostFragment();
    }

    private void setupConnectionStateListener() {
        connectionManager.addConnectionStateListener(connectionStateListener);
    }
    
    private void initializeAutoConnect() {
        if (bluetoothService == null || rxBleClient == null || connectionManager == null) {
            Log.w(TAG, "Cannot initialize auto-connect: service or client not ready");
            return;
        }
        if (!connectionManager.isAutoConnectEnabled()) {
            Log.d(TAG, "Bluetooth auto-connect disabled; skipping BluetoothAutoConnectManager");
            return;
        }

        autoConnectManager =
                new BluetoothAutoConnectManager(this, rxBleClient, bluetoothService, connectionManager);
        autoConnectManager.setAutoConnectListener(new BluetoothAutoConnectManager.AutoConnectListener() {
            @Override
            public void onAutoConnectStarted() {
                Log.d(TAG, "Auto-connect started");
                runOnUiThread(() -> {
                    UiToastLimiter.show(
                            MainActivity.this,
                            "auto_connect_started",
                            getString(R.string.main_toast_auto_connecting_bluetooth),
                            Toast.LENGTH_SHORT,
                            2200);
                });
            }

            @Override
            public void onAutoConnectSuccess(RxBleDevice device) {
                Log.d(TAG, "Auto-connect successful: " + device.getName());
                runOnUiThread(() -> {
                    isBluetoothConnected = true;
                    if (connectionManager != null) {
                        connectionManager.connectBluetooth(device);
                    }
                    
                    // Dismiss any open dialogs (Connection dialog or Bluetooth dialog)
                    dismissAllDialogs();
                    
                    UiToastLimiter.show(
                            MainActivity.this,
                            "bluetooth_connected",
                            getString(R.string.main_toast_auto_connected_to, device.getName()),
                            Toast.LENGTH_SHORT,
                            3000);
                });
            }

            @Override
            public void onAutoConnectFailed(String reason) {
                Log.e(TAG, "Auto-connect failed: " + reason);
                runOnUiThread(() -> {
                    UiToastLimiter.show(
                            MainActivity.this,
                            "auto_connect_failed",
                            getString(R.string.main_toast_auto_connect_failed, reason),
                            Toast.LENGTH_SHORT,
                            2500);
                });
            }

            @Override
            public void onAutoConnectRetrying(int retryCount) {
                Log.d(TAG, "Auto-connect retrying: attempt " + retryCount);
                if (retryCount == 1) {
                    runOnUiThread(() -> UiToastLimiter.show(
                            MainActivity.this,
                            "auto_connect_retry",
                            getString(R.string.main_toast_auto_connect_retry),
                            Toast.LENGTH_SHORT,
                            3000));
                }
            }
        });
        
        // Start auto-connect with a slight delay to ensure everything is ready
        new Handler().postDelayed(() -> {
            if (autoConnectManager != null) {
                autoConnectManager.startAutoConnect();
            }
        }, 1000); // 1 second delay
    }
    
    /**
     * Header connection icon tint: theme primary when connected; idle grey otherwise.
     */
    private int headerConnectionClusterTint(
            ConnectionManager.ConnectionType type, ConnectionManager.ConnectionState state) {
        switch (state) {
            case CONNECTED:
                return ThemeManager.getColorPrimary(this);
            default:
                return ContextCompat.getColor(this, R.color.header_connection_idle);
        }
    }

    /** Neutral tint for non-connection actions in the header strip. */
    private int headerNeutralActionTint() {
        return ContextCompat.getColor(this, R.color.text_secondary);
    }

    @DrawableRes
    private int connectionHeaderIconRes(
            ConnectionManager.ConnectionType type, ConnectionManager.ConnectionState state) {
        switch (type) {
            case USB:
                if (state == ConnectionManager.ConnectionState.CONNECTED
                        || state == ConnectionManager.ConnectionState.CONNECTING) {
                    return R.drawable.ic_usb_24;
                }
                return R.drawable.ic_usb_off_24;
            case BLUETOOTH:
                if (state == ConnectionManager.ConnectionState.CONNECTING) {
                    return R.drawable.ic_bluetooth_connecting_24;
                }
                if (state == ConnectionManager.ConnectionState.CONNECTED) {
                    return R.drawable.ic_bluetooth_connected_24;
                }
                return R.drawable.ic_bluetooth;
            case NONE:
            default:
                return R.drawable.ic_bluetooth;
        }
    }

    private void updateConnectionButton(ConnectionManager.ConnectionType type, ConnectionManager.ConnectionState state) {
        if (connectionButton == null) return;

        int connectionTint = headerConnectionClusterTint(type, state);
        int neutralTint = headerNeutralActionTint();
        connectionButton.setImageResource(connectionHeaderIconRes(type, state));
        connectionButton.setColorFilter(connectionTint, PorterDuff.Mode.SRC_IN);
        int cdRes;
        switch (type) {
            case USB:
                cdRes = R.string.connection_medium_usb;
                break;
            case BLUETOOTH:
                if (state == ConnectionManager.ConnectionState.CONNECTING) {
                    cdRes = R.string.connection_header_bluetooth_searching;
                } else if (state == ConnectionManager.ConnectionState.CONNECTED) {
                    cdRes = R.string.connection_header_bluetooth_connected;
                } else {
                    cdRes = R.string.connection_medium_bluetooth;
                }
                break;
            default:
                cdRes = R.string.connection;
                break;
        }
        connectionButton.setContentDescription(getString(cdRes));
        if (targetOsHeaderButton != null) {
            targetOsHeaderButton.setColorFilter(neutralTint, PorterDuff.Mode.SRC_IN);
        }

        switch (state) {
            case CONNECTED:
                if (signalBars != null) {
                    signalBars.setVisibility(View.GONE);
                }
                connectionButton.clearAnimation();
                break;
            case CONNECTING:
                if (signalBars != null) signalBars.setVisibility(View.GONE);
                if (type == ConnectionManager.ConnectionType.BLUETOOTH) {
                    android.view.animation.Animation spin =
                            android.view.animation.AnimationUtils.loadAnimation(
                                    this, R.anim.connection_spinning);
                    connectionButton.startAnimation(spin);
                } else {
                    connectionButton.clearAnimation();
                }
                break;
            case ERROR:
            case DISCONNECTED:
                if (signalBars != null) signalBars.setVisibility(View.GONE);
                connectionButton.clearAnimation();
                break;
        }
        notifyBasicChromeFragments();
    }

    private void showConnectionDialog() {
        ConnectionDialogFragment dialog = ConnectionDialogFragment.newInstance();
        dialog.setConnectionManager(connectionManager);
        dialog.setRxBleClient(rxBleClient);
        dialog.setConnectionDialogListener((type, state) -> updateConnectionButton(type, state));
        dialog.show(getSupportFragmentManager(), "ConnectionDialog");
    }

    /** Opens the bug-report Google Form in the user’s browser (or shows a toast if none). */
    private void openBugReportForm() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(BUG_REPORT_FORM_URL));
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            Toast.makeText(this, R.string.report_bug_no_browser_toast, Toast.LENGTH_LONG).show();
        }
    }

    private void startScan() {
        ScanSettings scanSettings = new ScanSettings.Builder().build();
        scanSubscription = rxBleClient.scanBleDevices(scanSettings)
                .subscribe(
                        (scanResult) -> {
                            String deviceName = scanResult.getBleDevice().getName();
                            Log.d("ScanResult", "find device: " + (deviceName != null ? deviceName : "unknown device"));
                        },
                        (Throwable throwable) -> {
                            Log.e("ScanError", "scan error", throwable);
                        }
                );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                BluetoothDialogFragment dialog = new BluetoothDialogFragment();
                dialog.setRxBleClient(rxBleClient);
                dialog.setConnectionManager(connectionManager);
                dialog.setConnectionListener(this); // Set the listener
                dialog.show(getSupportFragmentManager(), "BluetoothDialog");
            } else {
                Log.e("Permission", "location permission denied");
            }
        }
    }

    private void setupButtonListeners() {
        // Connection button handler removed - click handled by connection_container
        
        // Menu button handler - toggle drawer
        if (menuButton != null) {
            menuButton.setOnClickListener(v -> {
                if (drawerLayout != null) {
                    if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                        drawerLayout.closeDrawer(GravityCompat.START);
                    } else {
                        snapshotImeBeforeOpeningDrawer();
                        drawerLayout.openDrawer(GravityCompat.START);
                    }
                }
            });
        }

        // Sidebar close button (mirrors iOS ellipsis button inside sidebar)
        View sidebarCloseButton = findViewById(R.id.sidebar_close_button);
        if (sidebarCloseButton != null) {
            sidebarCloseButton.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
            });
        }

        // Nav item click listeners
        if (navKeyboardMouse != null) {
            navKeyboardMouse.setOnClickListener(v -> {
                currentNavMode = LaunchPanelActivity.MODE_KEYBOARD_MOUSE;
                updateNavSelection();
                showKeyboardMouseFragment(null);
                markDrawerCloseAsNavigation();
                drawerLayout.closeDrawer(GravityCompat.START);
            });
        }
        if (navKeyboardMousePro != null) {
            navKeyboardMousePro.setOnClickListener(v -> {
                currentNavMode = LaunchPanelActivity.MODE_KEYBOARD_MOUSE_PRO;
                updateNavSelection();
                showCompositeFragment();
                markDrawerCloseAsNavigation();
                drawerLayout.closeDrawer(GravityCompat.START);
            });
        }
        if (navGamepad != null) {
            navGamepad.setOnClickListener(v -> {
                currentNavMode = LaunchPanelActivity.MODE_GAMEPAD;
                updateNavSelection();
                showGamepadFragment();
                markDrawerCloseAsNavigation();
                drawerLayout.closeDrawer(GravityCompat.START);
            });
        }
        if (navShortcuts != null) {
            navShortcuts.setOnClickListener(v -> {
                currentNavMode = LaunchPanelActivity.MODE_SHORTCUTS;
                updateNavSelection();
                showShortcutHubFragment();
                markDrawerCloseAsNavigation();
                drawerLayout.closeDrawer(GravityCompat.START);
            });
        }
        if (navMacros != null) {
            navMacros.setOnClickListener(v -> {
                currentNavMode = LaunchPanelActivity.MODE_MACROS;
                updateNavSelection();
                showMacrosFragment();
                markDrawerCloseAsNavigation();
                drawerLayout.closeDrawer(GravityCompat.START);
            });
        }
        if (navVoice != null) {
            navVoice.setOnClickListener(v -> {
                currentNavMode = LaunchPanelActivity.MODE_VOICE;
                updateNavSelection();
                showVoiceInputFragment();
                markDrawerCloseAsNavigation();
                drawerLayout.closeDrawer(GravityCompat.START);
            });
        }
        if (navPresentation != null) {
            navPresentation.setOnClickListener(v -> {
                currentNavMode = LaunchPanelActivity.MODE_PRESENTATION;
                updateNavSelection();
                showPresentationFragment();
                markDrawerCloseAsNavigation();
                drawerLayout.closeDrawer(GravityCompat.START);
            });
        }

        // Welcome & Guide — returns to LaunchPanelActivity (mode picker + tutorial)
        View chooseModeButton = findViewById(R.id.choose_mode_button);
        if (chooseModeButton != null) {
            chooseModeButton.setOnClickListener(v -> {
                markDrawerCloseAsNavigation();
                drawerLayout.closeDrawer(GravityCompat.START);
                Intent intent = new Intent(MainActivity.this, LaunchPanelActivity.class);
                intent.putExtra(LaunchPanelActivity.SHOW_PANEL, true);
                startActivity(intent);
                finish();
            });
        }

        // Settings button handler (in nav menu)
        View settingsButton = findViewById(R.id.settings_button);
        if (settingsButton != null) {
            settingsButton.setOnClickListener(v -> {
                markDrawerCloseAsNavigation();
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
            });
        }

        View reportBugRow = findViewById(R.id.nav_report_bug);
        if (reportBugRow != null) {
            reportBugRow.setOnClickListener(v -> {
                markDrawerCloseAsNavigation();
                if (drawerLayout != null) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                }
                openBugReportForm();
            });
        }

        if (keyBoard != null) {
            setOnClickListener(keyBoard, keyBoardDrawable, this::showKeyboardFragment);
        }

        if (keyBoardMouse != null) {
            setOnClickListener(keyBoardMouse, keyBoardMouseDrawable, () -> {
                currentNavMode = LaunchPanelActivity.MODE_KEYBOARD_MOUSE_PRO;
                updateNavSelection();
                showCompositeFragment();
            });
        }

        if (mouse != null) {
            setOnClickListener(mouse, mouseDrawable, () -> {
                // Toast.makeText(this, "Mouse button clicked", Toast.LENGTH_SHORT).show();
            });
        }

        if (question != null) {
            question.setOnClickListener(v -> {
                // Toast.makeText(this, "Question button clicked", Toast.LENGTH_SHORT).show();
            });
        }

        if (info != null) {
            info.setOnClickListener(v -> {
                // Toast.makeText(this, "Info button clicked", Toast.LENGTH_SHORT).show();
            });
        }

        if (shortcut != null) {
            setOnClickListener(shortcut, shortcutDrawable, this::showShortCutFragment);
        }
    }

    private void setOnClickListener(Button button, Drawable drawable, Runnable onClickAction) {
        if (button == null || drawable == null) return;
        button.setOnClickListener(v -> {
            resetButtonColors();
            setActiveButton(button, drawable);
            onClickAction.run();
        });
    }

    private void setActiveButton(Button button, Drawable drawable) {
        if (button == null || drawable == null) return;
        int activeColor = getResources().getColor(android.R.color.holo_blue_light);
        button.setTextColor(activeColor);
        drawable.setColorFilter(activeColor, PorterDuff.Mode.SRC_IN);
        activeButton = button;
    }

    private void resetButtonColors() {
        int defaultColor = getResources().getColor(android.R.color.black);
        resetButton(keyBoard, keyBoardDrawable, defaultColor);
        resetButton(mouse, mouseDrawable, defaultColor);
        resetButton(keyBoardMouse, keyBoardMouseDrawable, defaultColor);
        resetButton(shortcut, shortcutDrawable, defaultColor);
    }

    private void resetButton(Button button, Drawable drawable, int defaultColor) {
        if (button != null && drawable != null) {
            button.setTextColor(defaultColor);
            drawable.clearColorFilter();
        }
    }

    // Note: resetConnectionButtons() removed as USB/Bluetooth buttons are now in the connection dialog
    
    private void dismissAllDialogs() {
        try {
            FragmentManager fragmentManager = getSupportFragmentManager();
            
            // Dismiss BluetoothDialogFragment if open
            Fragment bluetoothDialog = fragmentManager.findFragmentByTag("BluetoothDialog");
            if (bluetoothDialog instanceof BluetoothDialogFragment) {
                ((BluetoothDialogFragment) bluetoothDialog).dismiss();
                Log.d(TAG, "Dismissed BluetoothDialogFragment");
            }
            
            // Dismiss ConnectionDialogFragment if open
            Fragment connectionDialog = fragmentManager.findFragmentByTag("ConnectionDialog");
            if (connectionDialog instanceof ConnectionDialogFragment) {
                ((ConnectionDialogFragment) connectionDialog).dismiss();
                Log.d(TAG, "Dismissed ConnectionDialogFragment");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error dismissing dialogs: " + e.getMessage());
        }
    }

    private void updateNavSelection() {
        if (navKeyboardMouse != null) {
            navKeyboardMouse.setSelected(
                    LaunchPanelActivity.MODE_KEYBOARD_MOUSE.equals(currentNavMode));
        }
        if (navKeyboardMousePro != null) {
            navKeyboardMousePro.setSelected(
                    LaunchPanelActivity.MODE_KEYBOARD_MOUSE_PRO.equals(currentNavMode));
        }
        if (navGamepad != null) {
            navGamepad.setSelected(LaunchPanelActivity.MODE_GAMEPAD.equals(currentNavMode));
        }
        if (navShortcuts != null) {
            navShortcuts.setSelected(LaunchPanelActivity.MODE_SHORTCUTS.equals(currentNavMode));
        }
        if (navMacros != null) {
            navMacros.setSelected(LaunchPanelActivity.MODE_MACROS.equals(currentNavMode));
        }
        if (navVoice != null) {
            navVoice.setSelected(LaunchPanelActivity.MODE_VOICE.equals(currentNavMode));
        }
        if (navPresentation != null) {
            navPresentation.setSelected(LaunchPanelActivity.MODE_PRESENTATION.equals(currentNavMode));
        }
    }

    private void updateTargetOsHeaderIcon() {
        if (targetOsHeaderButton == null) return;
        String targetOs = getTargetOs();
        int iconRes;
        int nameRes;
        if ("windows".equals(targetOs)) {
            iconRes = R.drawable.ic_os_windows;
            nameRes = R.string.target_os_windows;
        } else if ("linux".equals(targetOs)) {
            iconRes = R.drawable.ic_os_linux;
            nameRes = R.string.target_os_linux;
        } else {
            iconRes = R.drawable.ic_os_macos;
            nameRes = R.string.target_os_macos;
        }
        targetOsHeaderButton.setImageResource(iconRes);
        targetOsHeaderButton.setContentDescription(
                getString(R.string.target_os_header_cd_selected, getString(nameRes)));
        targetOsHeaderButton.setColorFilter(headerNeutralActionTint(), PorterDuff.Mode.SRC_IN);
    }

    private void showTargetOsPickerDialog() {
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_target_os_picker, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.target_os_picker_title)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        content.findViewById(R.id.picker_os_macos).setOnClickListener(v -> {
            setTargetOs("macos");
            dialog.dismiss();
        });
        content.findViewById(R.id.picker_os_windows).setOnClickListener(v -> {
            setTargetOs("windows");
            dialog.dismiss();
        });
        content.findViewById(R.id.picker_os_linux).setOnClickListener(v -> {
            setTargetOs("linux");
            dialog.dismiss();
        });

        dialog.show();
    }

    private int getThemeColor(int attrId) {
        TypedValue value = new TypedValue();
        if (getTheme().resolveAttribute(attrId, value, true)) {
            return value.data;
        }
        return ThemeManager.getColorPrimary(this);
    }

    private void showKeyboardFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, KeyboardFragment.newInstance(port));
        transaction.commit();
    }

    private void showKeyboardMouseFragment(@Nullable String initialSubmode) {
        if (initialSubmodeImpliesFullKeyboard(initialSubmode)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(
                R.id.fragment_container,
                KeyboardMouseFragment.newInstance(port, initialSubmode));
        transaction.commit();
    }

    /**
     * {@code launch_mode == null} matches the onCreate branch that defaults to KM Basic; extras can
     * still request numpad, touchpad, or settings without {@code launch_mode}. Legacy compose extra is
     * normalized to keyboard before this runs.
     */
    private static boolean shouldLockLandscapeForKmBasicKeyboardIntent(
            @Nullable String launchMode, @Nullable String kbInitialSubmode) {
        if (LaunchPanelActivity.MODE_KEYBOARD_MOUSE.equals(launchMode)) {
            return !KeyboardMouseFragment.SUBMODE_NUMPAD.equals(kbInitialSubmode)
                    && !KeyboardMouseFragment.SUBMODE_COMPOSE.equals(kbInitialSubmode)
                    && !KeyboardMouseFragment.SUBMODE_TOUCHPAD.equals(kbInitialSubmode)
                    && !KeyboardMouseFragment.SUBMODE_SETTINGS.equals(kbInitialSubmode);
        }
        if (launchMode == null) {
            return kbInitialSubmode == null
                    || KeyboardMouseFragment.SUBMODE_KEYBOARD.equals(kbInitialSubmode);
        }
        return false;
    }

    private static boolean initialSubmodeImpliesFullKeyboard(@Nullable String initialSubmode) {
        return initialSubmode == null
                || KeyboardMouseFragment.SUBMODE_KEYBOARD.equals(initialSubmode);
    }

    private void showCompositeFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, CompositeFragment.newInstance(port));
        transaction.commit();
    }

    @Nullable
    private String consumePendingKbMouseSubmode() {
        String s = pendingKbMouseSubmode;
        pendingKbMouseSubmode = null;
        return s;
    }

    private void showGamepadFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, new GamepadFragment());
        transaction.commit();
    }

    private void showMouseFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, MouseFragment.newInstance(port));
        transaction.commit();
    }

    private void showShortCutFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, ShortcutFragment.newInstance());
        transaction.commit();
    }

    private void showMacrosFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, new MacrosFragment());
        transaction.commit();
    }

    private void showShortcutHubFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, new ShortcutHubFragment());
        transaction.commit();
    }

    /** Reloads strip data from prefs after profile/strip imports or when closing Pro setup. */
    public void refreshOpenKeyboardShortcutStripFromPrefs() {
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (f instanceof CompositeFragment) {
            ((CompositeFragment) f).refreshKeyboardShortcutStripFromExternalHub();
        }
    }

    /** Reload alternate-hints pref into the composite keyboard after Pro setup or equivalent. */
    public void refreshKeyboardAlternatesHintsFromPrefs() {
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (f instanceof CompositeFragment) {
            ((CompositeFragment) f).refreshKeyboardAlternatesHintsFromPrefs();
        }
    }

    /** Reload KM Pro key tap preview pref into the composite keyboard after Pro setup. */
    public void refreshKmProKeyTapPreviewFromPrefs() {
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (f instanceof CompositeFragment) {
            ((CompositeFragment) f).refreshKmProKeyTapPreviewFromPrefs();
        }
    }

    /** Rebuild composite keyboard views after KM Pro setup (long-press behavior, etc.). */
    public void refreshCompositeKeyboardLayoutFromKmProSetup() {
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (f instanceof CompositeFragment) {
            ((CompositeFragment) f).refreshCompositeKeyboardLayoutFromKmProSetup();
        }
    }

    /** Apply KM Pro touchpad mouse-key strip / hybrid prefs to the composite touchpad chrome. */
    public void refreshCompositeTouchpadChromeFromKmProSetup() {
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (f instanceof CompositeFragment) {
            ((CompositeFragment) f).refreshProTouchpadChromeFromKmProSetup();
        }
    }

    private void showVoiceInputFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, new VoiceInputFragment());
        transaction.commit();
    }

    private void showPresentationFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, new PresentationFragment());
        transaction.commit();
    }

    /** Switch primary content mode (same behavior as side nav). Used by top-strip PH shortcuts. */
    public void switchToLaunchMode(String mode) {
        handleLaunchMode(mode);
    }

    /**
     * Handle launch mode from LaunchPanelActivity
     */
    private void handleLaunchMode(String mode) {
        if (mode == null) {
            mode = LaunchPanelActivity.MODE_KEYBOARD_MOUSE;
        }
        if (LaunchPanelActivity.MODE_NUMPAD.equals(mode)) {
            currentNavMode = LaunchPanelActivity.MODE_KEYBOARD_MOUSE;
            updateNavSelection();
            consumePendingKbMouseSubmode();
            showKeyboardMouseFragment(KeyboardMouseFragment.SUBMODE_NUMPAD);
            return;
        }
        if (LaunchPanelActivity.MODE_COMPOSE.equals(mode)) {
            currentNavMode = LaunchPanelActivity.MODE_KEYBOARD_MOUSE;
            updateNavSelection();
            consumePendingKbMouseSubmode();
            showKeyboardMouseFragment(null);
            return;
        }

        currentNavMode = mode;
        updateNavSelection();
        switch (mode) {
            case LaunchPanelActivity.MODE_KEYBOARD_MOUSE:
                showKeyboardMouseFragment(consumePendingKbMouseSubmode());
                break;
            case LaunchPanelActivity.MODE_KEYBOARD_MOUSE_PRO:
                consumePendingKbMouseSubmode();
                showCompositeFragment();
                break;
            case LaunchPanelActivity.MODE_GAMEPAD:
                consumePendingKbMouseSubmode();
                showGamepadFragment();
                break;
            case LaunchPanelActivity.MODE_SHORTCUTS:
                consumePendingKbMouseSubmode();
                showShortcutHubFragment();
                break;
            case LaunchPanelActivity.MODE_MACROS:
                consumePendingKbMouseSubmode();
                showMacrosFragment();
                break;
            case LaunchPanelActivity.MODE_VOICE:
                consumePendingKbMouseSubmode();
                showVoiceInputFragment();
                break;
            case LaunchPanelActivity.MODE_PRESENTATION:
                consumePendingKbMouseSubmode();
                showPresentationFragment();
                break;
            default:
                currentNavMode = LaunchPanelActivity.MODE_KEYBOARD_MOUSE;
                updateNavSelection();
                consumePendingKbMouseSubmode();
                showKeyboardMouseFragment(null);
                break;
        }
    }

    private void updateFragmentsWithPort(UsbSerialPort newPort) {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment instanceof KeyboardFragment) {
            ((KeyboardFragment) currentFragment).port = newPort;
            CustomKeyboardView keyboardView = currentFragment.getView() != null ?
                    currentFragment.getView().findViewById(R.id.keyboard_view) : null;
            if (keyboardView != null) {
                keyboardView.setPort(newPort);
            }
        } else if (currentFragment instanceof CompositeFragment) {
            ((CompositeFragment) currentFragment).applyHostPortToKeyboardViews(newPort);
        } else if (currentFragment instanceof KeyboardMouseFragment) {
            ((KeyboardMouseFragment) currentFragment).onPortChanged(newPort);
        } else if (currentFragment instanceof MouseFragment) {
            ((MouseFragment) currentFragment).setPort(newPort);
        }
    }

    private void startReading() {
        startHostLockPolling();
        new Thread(() -> {
            try {
                while (isReading) {
                    byte[] buffer = new byte[1024];
                    int numBytesRead = port.read(buffer, 5);
                    if (numBytesRead > 0) {
                        hostLockInboundParser.append(buffer, numBytesRead);
                        StringBuilder allReadData = new StringBuilder();
                        for (int i = 0; i < numBytesRead; i++) {
                            allReadData.append(String.format("%02X ", buffer[i]));
                        }
                        Log.d(TAG, "Read data: " + allReadData.toString().trim());

                        if (onDataReadListener != null) {
                            onDataReadListener.onDataRead();
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Error reading USB data: " + e.getMessage());
            }
        }).start();
    }

    private void startHostLockPolling() {
        if (hostLockPollScheduled) {
            return;
        }
        hostLockPollScheduled = true;
        hostLockPollHandler.post(hostLockPollRunnable);
    }

    private void stopHostLockPolling() {
        hostLockPollScheduled = false;
        hostLockPollHandler.removeCallbacks(hostLockPollRunnable);
    }

    private void setupUsbSerial() {
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            Toast.makeText(this, R.string.main_toast_no_usb_serial_devices, Toast.LENGTH_LONG).show();
            usbConnect.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.usb_plug_disconnect, 0, 0);
            usbConnect.setTextColor(getResources().getColor(android.R.color.holo_red_light));
            usbConnectDrawable.setColorFilter(getResources().getColor(android.R.color.holo_red_light), PorterDuff.Mode.SRC_IN);
            isUsbConnected = false;
            return;
        }

        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDevice device = driver.getDevice();

        // check USB permission
        if (!manager.hasPermission(device)) {
            Intent permissionIntent = new Intent(ACTION_USB_PERMISSION);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, permissionIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            manager.requestPermission(device, pendingIntent);
            return;
        }

        UsbDeviceConnection connection = manager.openDevice(device);
        if (connection == null) {
            Toast.makeText(this, R.string.main_toast_usb_serial_connection_failed, Toast.LENGTH_LONG).show();
            isUsbConnected = false;
            return;
        }

        port = driver.getPorts().get(0);
        try {
            port.open(manager.openDevice(device));
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            Toast.makeText(this, R.string.main_toast_serial_port_open_success, Toast.LENGTH_LONG).show();

            Drawable usbConnectDrawableTint = ContextCompat.getDrawable(this, R.drawable.usb_plug);
            if (usbConnectDrawableTint != null) {
                DrawableCompat.setTint(usbConnectDrawableTint, ContextCompat.getColor(this, android.R.color.holo_blue_light));
                usbConnect.setCompoundDrawablesWithIntrinsicBounds(null, usbConnectDrawableTint, null, null);
            }

            usbConnect.setTextColor(getResources().getColor(android.R.color.holo_blue_light));
            usbConnectDrawable.setColorFilter(getResources().getColor(android.R.color.holo_blue_light), PorterDuff.Mode.SRC_IN);
            isReading = true;
            isUsbConnected = true;
            updateFragmentsWithPort(port);
            startReading();
        } catch (IOException e) {
            Log.e(TAG, "Failed to open serial port: " + e.getMessage());
            Toast.makeText(this, R.string.main_toast_serial_port_open_failed, Toast.LENGTH_LONG).show();
            isUsbConnected = false;
        }
    }

    @Override
    public void onBluetoothConnectionChanged(boolean isConnected) {
        isBluetoothConnected = isConnected;
        Log.d(TAG, "Bluetooth connection state updated: " + isConnected);

        if (connectionManager != null) {
            connectionManager.setBluetoothService(bluetoothService);
            connectionManager.syncBluetoothConnectionState(isConnected);
        }
        
        // Update ConnectionManager if Bluetooth is connected
        if (isConnected && bluetoothService != null && connectionManager != null) {
            com.polidea.rxandroidble2.RxBleDevice connectedDevice = bluetoothService.getConnectedDevice();
            if (connectedDevice != null) {
                connectionManager.connectBluetooth(connectedDevice);
                Log.d(TAG, "Updated ConnectionManager with BLE device");
            } else {
                Log.d(TAG, "Bluetooth connected but connectedDevice is null; state synced from callback");
            }
            startHostLockPolling();
        } else if (!isConnected && connectionManager != null && 
                   connectionManager.getCurrentConnectionType() == ConnectionManager.ConnectionType.BLUETOOTH) {
            connectionManager.disconnect();
            Log.d(TAG, "Disconnected Bluetooth in ConnectionManager");
        }
        
        if (isConnected) {
            UiToastLimiter.show(
                    this,
                    "bluetooth_connected",
                    getString(R.string.main_toast_bluetooth_connected),
                    Toast.LENGTH_SHORT,
                    2500);
        } else {
            UiToastLimiter.show(
                    this,
                    "bluetooth_disconnected",
                    getString(R.string.main_toast_bluetooth_disconnected),
                    Toast.LENGTH_SHORT,
                    1800);
        }
    }

    /**
     * Get ConnectionManager instance for fragments
     */
    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    public BluetoothService getBluetoothService() {
        return bluetoothService;
    }

    /**
     * Switches the top {@link CompositeFragment} KM Pro tab (keyboard / compose / numpad). Used by the
     * KM Pro mode tour so Compose UI exists before highlight measurement.
     */
    public void ensureKmProFragmentSubmode(@NonNull String submodeKey) {
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (f instanceof CompositeFragment) {
            ((CompositeFragment) f).applyKmProSubmodeFromHost(submodeKey);
            getSupportFragmentManager().executePendingTransactions();
        }
    }

    /** Switches KM Basic to the full keyboard submode for the quick-start tour highlight. */
    public void ensureKmBasicKeyboardSubmodeForGuide() {
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (f instanceof KeyboardMouseFragment) {
            ((KeyboardMouseFragment) f).requestSubmode(KeyboardMouseFragment.SUBMODE_KEYBOARD);
            getSupportFragmentManager().executePendingTransactions();
        }
    }

    /** Opens Default profile detail inside Shortcut Hub for the mode tour. */
    public void ensureShortcutHubDefaultProfileForGuide() {
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (f instanceof ShortcutHubFragment) {
            ((ShortcutHubFragment) f).openDefaultProfileDetailForTour();
            getSupportFragmentManager().executePendingTransactions();
            refreshHeaderSetupGearChrome();
        }
    }

    /** Switches Hub profile detail to the first category tab for the mode tour. */
    public void ensureShortcutHubBrowseFirstCategoryForGuide() {
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (f instanceof ShortcutHubFragment) {
            ((ShortcutHubFragment) f).selectFirstBrowseCategoryForTour();
            getSupportFragmentManager().executePendingTransactions();
        }
    }

    /** Turns on Gamepad Customize so the tour can highlight move mode and the pad surface. */
    public void ensureGamepadEditModeForGuide() {
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (f instanceof GamepadFragment) {
            ((GamepadFragment) f).enterEditModeForTour();
            getSupportFragmentManager().executePendingTransactions();
        }
    }

    /** Returns Gamepad to play mode after a tour step that temporarily enabled Customize. */
    public void ensureGamepadEditModeExitForGuide() {
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (f instanceof GamepadFragment) {
            ((GamepadFragment) f).exitEditModeForTour();
            getSupportFragmentManager().executePendingTransactions();
        }
    }

    public void openModeGuideSheet() {
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        ModeGuidePrefs.GuideHostMode mode = ModeGuidePrefs.guideModeForTopFragment(f);
        if (mode == null) {
            return;
        }
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        sheet.setContentView(R.layout.bottom_sheet_mode_guide);
        TextView title = sheet.findViewById(R.id.mode_guide_sheet_title);
        if (title != null) {
            title.setText(modeGuideSheetTitleFor(mode));
        }
        View replay = sheet.findViewById(R.id.mode_guide_replay_button);
        if (replay != null) {
            replay.setOnClickListener(
                    v -> {
                        sheet.dismiss();
                        showModeTutorialOverlay(mode, false);
                    });
        }
        View close = sheet.findViewById(R.id.mode_guide_close_button);
        if (close != null) {
            close.setOnClickListener(v -> sheet.dismiss());
        }
        sheet.setOnShowListener(
                d -> {
                    BottomSheetDialog dialog = (BottomSheetDialog) d;
                    View decor = dialog.getWindow() != null ? dialog.getWindow().getDecorView() : null;
                    if (decor != null) {
                        decor.post(() -> applyModeGuideBottomSheetLayout(dialog));
                    } else {
                        applyModeGuideBottomSheetLayout(dialog);
                    }
                });
        sheet.show();
    }

    /**
     * {@link BottomSheetDialog} can anchor to the wrong edge and use a portrait-narrow max width when
     * the activity uses {@code configChanges} for orientation and Basic keyboard forces landscape
     * ({@code SENSOR_LANDSCAPE}). Force full activity width, bottom gravity, and expanded behavior.
     */
    private void applyModeGuideBottomSheetLayout(@NonNull BottomSheetDialog dialog) {
        View bottom = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottom == null) {
            return;
        }
        int targetWidth = resolveActivityContentWidthPx();
        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottom);
        behavior.setFitToContents(true);
        behavior.setSkipCollapsed(true);
        behavior.setMaxWidth(targetWidth);
        ViewGroup.LayoutParams lp = bottom.getLayoutParams();
        if (lp != null) {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            bottom.setLayoutParams(lp);
        }
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);

        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams wlp = window.getAttributes();
            wlp.width = WindowManager.LayoutParams.MATCH_PARENT;
            wlp.gravity = Gravity.BOTTOM;
            window.setAttributes(wlp);
        }
    }

    private int resolveActivityContentWidthPx() {
        Window w = getWindow();
        if (w != null) {
            View decor = w.getDecorView();
            int dw = decor.getWidth();
            if (dw > 0) {
                return dw;
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return getWindowManager().getCurrentWindowMetrics().getBounds().width();
        }
        return getResources().getDisplayMetrics().widthPixels;
    }

    /** Called from KM Basic / Gamepad embedded chrome (activity header hidden there). */
    public void openModeGuideFromEmbeddedChrome() {
        openModeGuideSheet();
    }

    private CharSequence modeGuideSheetTitleFor(ModeGuidePrefs.GuideHostMode mode) {
        switch (mode) {
            case KM_BASIC:
                return getString(R.string.mode_guide_sheet_title_basic);
            case KM_PRO:
                return getString(R.string.mode_guide_sheet_title_pro);
            case PRESENTATION:
                return getString(R.string.mode_guide_sheet_title_presentation);
            case GAMEPAD:
                return getString(R.string.mode_guide_sheet_title_gamepad);
            case SHORTCUT_HUB:
                return getString(R.string.mode_guide_sheet_title_shortcut_hub);
            default:
                return getString(R.string.app_name);
        }
    }

    private void updateModeGuideHeaderVisibility(@Nullable Fragment host) {
        if (modeGuideHeaderButton == null) {
            return;
        }
        boolean show = ModeGuidePrefs.supportsHeaderModeGuideButton(host);
        modeGuideHeaderButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void scheduleDeferredModeGuideCheck(@Nullable final Fragment host) {
        if (pendingModeGuideRunnable != null) {
            modeGuideHandler.removeCallbacks(pendingModeGuideRunnable);
        }
        pendingModeGuideRunnable =
                () -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                    if (current != host) {
                        return;
                    }
                    tryAutoShowFirstRunModeGuide(current);
                };
        modeGuideHandler.postDelayed(pendingModeGuideRunnable, 650);
    }

    private boolean isTutorialOverlayShowing() {
        ViewGroup root = findViewById(android.R.id.content);
        if (root == null) {
            return false;
        }
        for (int i = 0; i < root.getChildCount(); i++) {
            if (root.getChildAt(i) instanceof TutorialOverlay) {
                return true;
            }
        }
        return false;
    }

    private void tryAutoShowFirstRunModeGuide(@NonNull Fragment host) {
        if (isTutorialOverlayShowing()) {
            return;
        }
        ModeGuidePrefs.GuideHostMode mode = ModeGuidePrefs.guideModeForTopFragment(host);
        if (mode == null || mode == ModeGuidePrefs.GuideHostMode.KM_BASIC) {
            return;
        }
        if (ModeGuidePrefs.isModeGuideCompleted(this, mode)) {
            return;
        }
        showModeTutorialOverlay(mode, true);
    }

    /**
     * @param markCompletionOnDismiss when true (first auto-run), marks this mode’s guide pref when
     *     the overlay is dismissed or completed.
     */
    public void showModeTutorialOverlay(
            @NonNull ModeGuidePrefs.GuideHostMode mode, boolean markCompletionOnDismiss) {
        if (mode == ModeGuidePrefs.GuideHostMode.KM_BASIC) {
            showKmBasicQuickStartOverlay(markCompletionOnDismiss);
            return;
        }
        if (isTutorialOverlayShowing()) {
            return;
        }
        TutorialOverlay overlay = new TutorialOverlay(this);
        overlay.setMarkBasicQuickStartPrefOnDismiss(false);
        if (markCompletionOnDismiss) {
            overlay.setOnDismissExtra(() -> ModeGuidePrefs.markModeGuideCompleted(MainActivity.this, mode));
        }

        String modeKey = getModeKeyForGuideHostMode(mode);
        TutorialOverlay.Step[] steps;
        switch (mode) {
            case KM_PRO:
                steps = ModeTutorialSteps.kmProGuide(this);
                break;
            case PRESENTATION:
                steps = ModeTutorialSteps.presentationGuide(this);
                break;
            case GAMEPAD:
                steps = ModeTutorialSteps.gamepadGuide(this);
                break;
            case SHORTCUT_HUB:
                steps = ModeTutorialSteps.shortcutHubGuide(this);
                break;
            default:
                return;
        }

        // Load help image config asynchronously, then set steps on overlay
        loadHelpConfigAndApplyWithSteps(overlay, modeKey, steps);

        ViewGroup root = findViewById(android.R.id.content);
        root.addView(
                overlay,
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showKmBasicQuickStartOverlay(boolean markBasicPrefOnDismiss) {
        if (isTutorialOverlayShowing()) {
            return;
        }
        TutorialOverlay overlay = new TutorialOverlay(this);
        overlay.setMarkBasicQuickStartPrefOnDismiss(markBasicPrefOnDismiss);

        TutorialOverlay.Step[] steps = ModeTutorialSteps.kmBasicQuickStart(this);

        // Load help image config asynchronously, then set steps on overlay
        loadHelpConfigAndApplyWithSteps(overlay, "km_basic", steps);

        ViewGroup root = findViewById(android.R.id.content);
        root.addView(
                overlay,
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /**
     * Load help config from assets, then apply config + steps to the overlay.
     * This ensures config is ready before steps are set (which triggers image loading).
     */
    private void loadHelpConfigAndApplyWithSteps(
            final TutorialOverlay overlay, final String modeKey, final TutorialOverlay.Step[] steps) {
        new Thread(() -> {
            HelpImageConfig config = HelpImageConfigManager.getInstance(this).loadLocalTestConfig();
            runOnUiThread(() -> {
                if (config != null) {
                    overlay.setConfig(config, modeKey);
                }
                overlay.setSteps(steps);
            });
        }).start();
    }

    /**
     * Returns the mode key string matching the help-image config’s mode identifier.
     */
    @Nullable
    private static String getModeKeyForGuideHostMode(@NonNull ModeGuidePrefs.GuideHostMode mode) {
        switch (mode) {
            case KM_PRO:
                return "km_pro";
            case PRESENTATION:
                return "presentation";
            case GAMEPAD:
                return "gamepad";
            case SHORTCUT_HUB:
                return "shortcut_hub";
            default:
                return null;
        }
    }

    public static void closeDrawerIfOpen(android.content.Context context) {
        if (!(context instanceof android.app.Activity)) {
            return;
        }
        androidx.drawerlayout.widget.DrawerLayout drawer =
                ((android.app.Activity) context).findViewById(R.id.drawer_layout);
        if (drawer != null && drawer.isDrawerOpen(android.view.Gravity.START)) {
            drawer.closeDrawer(android.view.Gravity.START);
        }
    }

    private void showTutorial() {
        showKmBasicQuickStartOverlay(true);
    }
}