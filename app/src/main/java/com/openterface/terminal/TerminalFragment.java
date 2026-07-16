package com.openterface.terminal;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import java.text.SimpleDateFormat;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.openterface.keymod.BluetoothService;
import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.CustomKeyboardView;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.R;

/**
 * Main fragment hosting the terminal UI.
 * Integrates SSH client, transport layer, and connection dialog.
 */
public class TerminalFragment extends Fragment {

    private static final String TAG = "TerminalFragment";
    private static final String ARG_DEMO_TRANSPORT = "arg_demo_transport";
    private static final String DEMO_USB = "usb";
    private static final String DEMO_BLE = "ble";

    public static TerminalFragment newInstance(@Nullable String demoTransport) {
        TerminalFragment fragment = new TerminalFragment();
        if (demoTransport != null) {
            Bundle args = new Bundle();
            args.putString(ARG_DEMO_TRANSPORT, demoTransport);
            fragment.setArguments(args);
        }
        return fragment;
    }

    @Nullable private View rootView;
    private FrameLayout contentContainer;
    private TerminalView terminalView;
    private TerminalSession terminalSession;

    // Custom keyboard (TerminalKeyboardTransport)
    private LinearLayout terminalKeyboardSlot;
    @Nullable private CustomKeyboardView terminalKeyboardView;
    @Nullable private TerminalKeyboardTransport terminalKeyboardTransport;
    private boolean customKeyboardVisible = false;

    // Terminal IME surface: hidden EditText that pops the system IME above the custom keyboard.
    // Text diffs are forwarded to TerminalSession (same pattern as KM Pro's imeHost).
    @Nullable private EditText terminalImeHost;
    private boolean imeSurfaceVisible = false;
    @Nullable private TextWatcher terminalImeTextWatcher;
    private String terminalImeLastProcessed = "";

    // Landscape split keyboard views (only non-null in landscape)
    @Nullable private CustomKeyboardView terminalKeyboardViewLeft;
    @Nullable private CustomKeyboardView terminalKeyboardViewRight;
    @Nullable private TerminalKeyboardTransport terminalKeyboardTransportLeft;
    @Nullable private TerminalKeyboardTransport terminalKeyboardTransportRight;
    @Nullable private FrameLayout terminalSplitTopLeft;
    @Nullable private FrameLayout terminalSplitTopRight;

    private Button connectBtn;
    private TextView statusText;
    private MaterialButton transportBtn;
    private TextView hostLabel;
    private LinearLayout connectionOverlay;
    private Button demoUsbBtn;
    private Button demoBleBtn;
    private LinearLayout demoButtonRow;

    private MainActivity mainActivity;
    private TerminalPrefs prefs;
    private CredentialManager credentialManager;

    // SSH connection state
    private SshClient sshClient;
    private UsbEcmTransport usbEcmTransport;
    private BleEthTransport bleEthTransport;
    private BleEthSocketFactory bleEthSocketFactory;
    private BluetoothService.BleEthDataCallback bleEthCallback;
    private boolean isSshConnected = false;
    private volatile boolean viewDestroyed = false;

    // Demo state
    private TerminalDemoController demoController;
    private boolean isDemoActive = false;
    @Nullable
    private TerminalDemoController.DemoTransport activeDemoTransport;
    @Nullable
    private String activeSessionHost;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof MainActivity) {
            mainActivity = (MainActivity) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        prefs = new TerminalPrefs(requireContext());
        credentialManager = new CredentialManager(requireContext());
        credentialManager.migrateFromTerminalPrefs(requireContext());
        credentialManager.ensureDefaultKeyCmdProfile();

        // Use a FrameLayout container so we can swap layouts on orientation change
        contentContainer = new FrameLayout(requireContext());
        contentContainer.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        inflateTerminalLayout(inflater, contentContainer);
        initTerminal();
        setupListeners();
        updateConnectionState();
        maybeStartPendingDemo();

        // Restore keyboard visibility state after process death
        if (savedInstanceState != null) {
            boolean wasKeyboardVisible = savedInstanceState.getBoolean("custom_keyboard_visible", false);
            if (wasKeyboardVisible) {
                // Delay to ensure View is fully initialized.
                // Note: Do NOT set customKeyboardVisible here — showCustomKeyboard()
                // checks it as an early-return guard and will skip showing the keyboard.
                rootView.post(this::showCustomKeyboard);
            }
        }

        return contentContainer;
    }

    /**
     * Inflate the appropriate terminal layout (portrait or landscape) into the container.
     * Android resource qualifiers handle portrait vs landscape selection automatically.
     */
    private void inflateTerminalLayout(@NonNull LayoutInflater inflater,
                                       @NonNull ViewGroup container) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
        container.addView(view);
        initViews(view);

        // In landscape, the split keyboards are always visible in the layout —
        // attach their transports immediately so they are ready for input.
        boolean isLandscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        if (isLandscape) {
            // Always create the top shortcut panels (they don't need a terminal session).
            setupLandscapeTopPanels();
            // Attach transports only when the terminal session is already available.
            if (terminalSession != null) {
                attachLandscapeSplitKeyboardTransport();
            }
        }
    }

    /**
     * Re-inflate the terminal layout after orientation change.
     * Preserves SSH connection and terminal session, only rebuilds views.
     */
    private void reInflateLayout() {
        if (contentContainer == null) return;
        viewDestroyed = false; // reset for new view hierarchy

        // Clean up old view references but preserve SSH/session state
        teardownTerminalKeyboard();
        contentContainer.removeAllViews();

        inflateTerminalLayout(LayoutInflater.from(requireContext()), contentContainer);
        setupListeners();
        updateConnectionState();

        // Restore keyboard visibility
        if (customKeyboardVisible) {
            rootView.post(this::showCustomKeyboard);
        }
    }

    private void maybeStartPendingDemo() {
        Bundle args = getArguments();
        if (args == null) {
            return;
        }
        String transport = args.getString(ARG_DEMO_TRANSPORT);
        if (transport == null) {
            return;
        }
        args.remove(ARG_DEMO_TRANSPORT);
        mainHandler.postDelayed(() -> {
            if (!isAdded()) {
                return;
            }
            if (DEMO_BLE.equalsIgnoreCase(transport)) {
                showConnectionDialog(TerminalDemoController.DemoTransport.BLE);
            } else if (DEMO_USB.equalsIgnoreCase(transport)) {
                showConnectionDialog(TerminalDemoController.DemoTransport.USB);
            } else {
                showConnectionDialog(TerminalDemoController.DemoTransport.BLE);
            }
        }, 350);
    }

    private void initViews(View view) {
        rootView = view;
        terminalView = view.findViewById(R.id.terminal_view);
        connectBtn = view.findViewById(R.id.terminal_connect_btn);
        statusText = view.findViewById(R.id.terminal_status);
        transportBtn = view.findViewById(R.id.terminal_transport_btn);
        hostLabel = view.findViewById(R.id.terminal_host_label);
        connectionOverlay = view.findViewById(R.id.terminal_connection_overlay);
        demoButtonRow = view.findViewById(R.id.terminal_empty_button_row);
        demoUsbBtn = view.findViewById(R.id.terminal_demo_usb_btn);
        demoBleBtn = view.findViewById(R.id.terminal_demo_ble_btn);
        demoController = new TerminalDemoController();
        if (demoButtonRow != null) {
            applyEmptyStateButtonLayout();
        }

        boolean isLandscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;

        if (isLandscape) {
            // Landscape split layout: left keyboard | center terminal | right keyboard
            terminalKeyboardViewLeft = view.findViewById(R.id.terminal_keyboard_view_left);
            terminalKeyboardViewRight = view.findViewById(R.id.terminal_keyboard_view_right);
            terminalSplitTopLeft = view.findViewById(R.id.terminal_split_top_left);
            terminalSplitTopRight = view.findViewById(R.id.terminal_split_top_right);
            terminalKeyboardSlot = null;
            terminalImeHost = null;
        } else {
            // Portrait layout: terminal on top, keyboard slot below with IME host
            terminalKeyboardSlot = view.findViewById(R.id.terminal_keyboard_slot);
            terminalImeHost = view.findViewById(R.id.terminal_ime_host);
            terminalKeyboardViewLeft = null;
            terminalKeyboardViewRight = null;
            terminalSplitTopLeft = null;
            terminalSplitTopRight = null;
        }
    }

    private void applyEmptyStateButtonLayout() {
        if (demoButtonRow == null || demoBleBtn == null || demoUsbBtn == null) {
            return;
        }
        boolean isLandscape =
                getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        int width = getResources().getDimensionPixelSize(R.dimen.terminal_empty_button_width);
        int height = getResources().getDimensionPixelSize(R.dimen.terminal_empty_button_height);
        int gap = getResources().getDimensionPixelSize(R.dimen.terminal_empty_button_gap);
        demoButtonRow.setOrientation(isLandscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

        LinearLayout.LayoutParams bleParams = new LinearLayout.LayoutParams(width, height);
        demoBleBtn.setLayoutParams(bleParams);

        LinearLayout.LayoutParams usbParams = new LinearLayout.LayoutParams(width, height);
        if (isLandscape) {
            usbParams.setMarginStart(gap);
        } else {
            usbParams.topMargin = gap;
        }
        demoUsbBtn.setLayoutParams(usbParams);
    }

    private void initTerminal() {
        if (terminalSession == null) {
            terminalSession = new TerminalSession(
                    prefs.getTerminalRows(),
                    prefs.getTerminalCols(),
                    prefs.getScrollbackSize()
            );
        }
        terminalView.setTerminalSession(terminalSession);
        terminalView.setFontSize(prefs.getFontSize());
    }

    /**
     * Dynamically create CustomKeyboardView and inject TerminalKeyboardTransport.
     * Portrait: single keyboard in terminalKeyboardSlot.
     * Landscape: two split keyboards (terminalKeyboardViewLeft, terminalKeyboardViewRight)
     * already inflated from XML — only create transports here.
     * Key presses are sent to terminalSession (which exists even when SSH is not connected).
     */
    private void attachKeyboardTransport() {
        if (terminalSession == null) return;

        boolean isLandscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;

        if (isLandscape) {
            attachLandscapeSplitKeyboardTransport();
        } else {
            attachPortraitKeyboardTransport();
        }
    }

    /** Portrait: inflate CustomKeyboardView into keyboard slot, set up single transport. */
    private void attachPortraitKeyboardTransport() {
        if (terminalKeyboardSlot == null || terminalSession == null) return;

        if (terminalKeyboardView == null) {
            // Inflate from XML to get keyBackground, theme, padding attributes
            View inflated = LayoutInflater.from(requireContext())
                    .inflate(R.layout.fragment_keyboard, terminalKeyboardSlot, false);
            terminalKeyboardView = inflated.findViewById(R.id.keyboard_view);
            terminalKeyboardSlot.addView(inflated, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0, 1f));

            OutputStream sessionOutput = createTerminalSessionOutput();
            terminalKeyboardTransport = new TerminalKeyboardTransport(sessionOutput);
            terminalKeyboardView.setTransport(terminalKeyboardTransport);

            // IME toggle: toggle between custom keyboard letter body and system IME.
            // Unlike KM Pro (which uses a shared preference), the terminal tracks its
            // own IME state locally so it does not follow KM Pro's last-used mode.
            terminalKeyboardView.setOnKmProSecondaryLayoutToggleListener(source -> {
                if (imeSurfaceVisible) {
                    hideTerminalImeSurface();
                } else {
                    showTerminalImeSurface();
                }
                refreshTerminalToggleKeyLabel();
            });
            // Ensure the toggle key label reflects the terminal's own state,
            // not whatever KM Pro last wrote to KmProSubmodePrefs.
            refreshTerminalToggleKeyLabel();
        }
    }

    /** Landscape: set up transports on the two split keyboard views. */
    private void attachLandscapeSplitKeyboardTransport() {
        if (terminalSession == null) return;

        if (terminalKeyboardViewLeft == null || terminalKeyboardViewRight == null) return;

        if (terminalKeyboardTransportLeft == null) {
            OutputStream leftOutput = createTerminalSessionOutput();
            terminalKeyboardTransportLeft = new TerminalKeyboardTransport(leftOutput);
            terminalKeyboardViewLeft.setTransport(terminalKeyboardTransportLeft);
            terminalKeyboardViewLeft.setSplitPart(CustomKeyboardView.SPLIT_LEFT);
            terminalKeyboardViewLeft.setSplitPartner(terminalKeyboardViewRight);

            // IME toggle on left keyboard: show system IME on terminal view
            terminalKeyboardViewLeft.setOnKmProSecondaryLayoutToggleListener(source -> {
                if (terminalView != null) {
                    terminalView.showKeyboard();
                }
            });
        }

        if (terminalKeyboardTransportRight == null) {
            OutputStream rightOutput = createTerminalSessionOutput();
            terminalKeyboardTransportRight = new TerminalKeyboardTransport(rightOutput);
            terminalKeyboardViewRight.setTransport(terminalKeyboardTransportRight);
            terminalKeyboardViewRight.setSplitPart(CustomKeyboardView.SPLIT_RIGHT);
            terminalKeyboardViewRight.setSplitPartner(terminalKeyboardViewLeft);

            terminalKeyboardViewRight.setOnKmProSecondaryLayoutToggleListener(source -> {
                if (terminalView != null) {
                    terminalView.showKeyboard();
                }
            });
        }

        // Create the shared top shortcut panels (3 scrollable rows) across both keyboards.
        // Post to ensure view hierarchy is fully laid out before building panels.
        setupLandscapeTopPanels();
    }

    /**
     * Build and attach the 3-row scrollable top shortcut panel in landscape split mode.
     * Uses post() to wait for layout pass so dimensions are valid.
     * Also ensures splitPart is set BEFORE the top panel is created, so the keyboard view
     * does not render its internal top rows (which would squeeze the letter area).
     */
    private void setupLandscapeTopPanels() {
        if (terminalKeyboardViewLeft == null || terminalKeyboardViewRight == null
                || terminalSplitTopLeft == null || terminalSplitTopRight == null) {
            return;
        }
        // Ensure split parts are set immediately so updateKeyboard() suppresses internal top rows.
        // This must happen BEFORE createSplitLandscapeTopPanel() is posted.
        terminalKeyboardViewLeft.setSplitPart(CustomKeyboardView.SPLIT_LEFT);
        terminalKeyboardViewRight.setSplitPart(CustomKeyboardView.SPLIT_RIGHT);
        terminalKeyboardViewLeft.setSplitPartner(terminalKeyboardViewRight);
        terminalKeyboardViewRight.setSplitPartner(terminalKeyboardViewLeft);

        terminalKeyboardViewLeft.post(() -> {
            if (terminalKeyboardViewLeft == null
                    || terminalSplitTopLeft == null || terminalSplitTopRight == null) {
                return;
            }
            terminalKeyboardViewLeft.createSplitLandscapeTopPanel(
                    terminalSplitTopLeft, null, terminalSplitTopRight);
        });
    }

    /**
     * Create an OutputStream that forwards writes to the current terminalSession.
     * The OutputStream accesses terminalSession by field reference, so it automatically
     * uses the current session even after resetTerminalSession().
     */
    private OutputStream createTerminalSessionOutput() {
        return new OutputStream() {
            @Override
            public void write(int b) {
                if (terminalSession != null) {
                    terminalSession.onKeyInput(new byte[]{(byte) b});
                }
            }

            @Override
            public void write(byte[] b) {
                if (terminalSession != null) {
                    terminalSession.onKeyInput(b);
                }
            }

            @Override
            public void write(byte[] b, int off, int len) {
                if (terminalSession != null) {
                    byte[] sub = new byte[len];
                    System.arraycopy(b, off, sub, 0, len);
                    terminalSession.onKeyInput(sub);
                }
            }
        };
    }

    /**
     * Show the custom keyboard. In portrait, makes the keyboard slot visible and hides IME surface.
     * In landscape, the split keyboards are always part of the layout — nothing to show.
     */
    private void showCustomKeyboard() {
        boolean isLandscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        if (isLandscape) {
            // Landscape: split keyboards are always visible as part of the layout
            hideTerminalImeSurface();
            return;
        }

        if (terminalKeyboardSlot == null) {
            Log.w(TAG, "showCustomKeyboard: terminalKeyboardSlot is null");
            return;
        }
        if (customKeyboardVisible && !imeSurfaceVisible) return; // already showing

        try {
            attachKeyboardTransport();
            hideTerminalImeSurface();
            terminalKeyboardSlot.setVisibility(View.VISIBLE);
            if (terminalKeyboardView != null) {
                terminalKeyboardView.setKmProPortraitLetterBodyVisible(true);
            }
            customKeyboardVisible = true;
            Log.v(TAG, "Custom keyboard shown successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to show custom keyboard, falling back to system IME", e);
            if (terminalView != null) {
                terminalView.showKeyboard();
            }
        }
    }

    /** Hide the custom keyboard in portrait. No-op in landscape. */
    private void hideCustomKeyboard() {
        boolean isLandscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        if (isLandscape) return; // Landscape split keyboards are always visible

        if (terminalKeyboardSlot == null) return;
        if (!customKeyboardVisible) return;

        terminalKeyboardSlot.setVisibility(View.GONE);
        customKeyboardVisible = false;
        Log.v(TAG, "Custom keyboard hidden");
    }

    // ── Terminal IME surface (portrait only) ─────────────────────────────────

    /**
     * Show the terminal IME surface: hide custom keyboard letter body, show IME host EditText,
     * focus it to trigger the system IME. Text changes are forwarded to the terminal session.
     * Mirrors KM Pro's showKmProImeSurface() pattern.
     */
    private void showTerminalImeSurface() {
        if (!isAdded() || imeSurfaceVisible) return;
        if (terminalImeHost == null || terminalKeyboardView == null
                || terminalKeyboardSlot == null) return;

        hideCustomKeyboard();

        // Ensure keyboard slot is visible so IME host is part of the layout
        terminalKeyboardSlot.setVisibility(View.VISIBLE);
        customKeyboardVisible = true;

        // Hide custom keyboard letter body, keep shortcut strip visible
        terminalKeyboardView.setKmProPortraitLetterBodyVisible(false);

        // Show and focus IME host to trigger system IME
        terminalImeHost.setVisibility(View.VISIBLE);
        terminalImeHost.requestFocus();
        attachTerminalImeTextWatcher();
        InputMethodManager imm =
                (InputMethodManager)
                        requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            terminalImeHost.post(
                    () -> imm.showSoftInput(terminalImeHost,
                            InputMethodManager.SHOW_IMPLICIT));
        }

        imeSurfaceVisible = true;
        Log.v(TAG, "Terminal IME surface shown");
    }

    /** Hide the terminal IME surface: hide IME host, restore custom keyboard letter body. */
    private void hideTerminalImeSurface() {
        if (!imeSurfaceVisible) return;

        detachTerminalImeTextWatcher();
        if (terminalImeHost != null) {
            InputMethodManager imm =
                    (InputMethodManager)
                            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(terminalImeHost.getWindowToken(), 0);
            }
            terminalImeHost.clearFocus();
            terminalImeHost.setVisibility(View.GONE);
            terminalImeLastProcessed = "";
        }
        if (terminalKeyboardView != null) {
            terminalKeyboardView.setKmProPortraitLetterBodyVisible(true);
        }

        imeSurfaceVisible = false;
        Log.v(TAG, "Terminal IME surface hidden");
    }

    /**
     * Refresh the terminal keyboard's IME toggle key label ("IME"/"BI") based on the
     * terminal's own imeSurfaceVisible state. Also writes to KmProSubmodePrefs so the
     * shared toggle label stays in sync and so KM Pro restores this state when the user
     * switches back to KM Pro mode.
     */
    private void refreshTerminalToggleKeyLabel() {
        if (!isAdded() || terminalKeyboardView == null) return;
        try {
            com.openterface.keymod.prefs.KmProSubmodePrefs.setPortraitInputSurface(
                    requireContext(), imeSurfaceVisible);
        } catch (Exception ignored) {}
        terminalKeyboardView.reloadForCurrentOrientation();
    }

    /**
     * Attach a TextWatcher to terminalImeHost that diffs text changes and forwards them
     * to terminalSession as byte input (same pattern as KmProImeDirectSendController but
     * sends to TerminalSession instead of HID).
     */
    private void attachTerminalImeTextWatcher() {
        if (terminalImeHost == null || terminalImeTextWatcher != null) return;
        terminalImeLastProcessed = safeImeText();
        terminalImeTextWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (terminalSession == null || terminalImeHost == null) return;
                final String now = s.toString();
                final String oldS = terminalImeLastProcessed;
                if (oldS.equals(now)) return;
                // Compute LCP-based diff and send to terminal
                int lcp = 0;
                int n = Math.min(oldS.length(), now.length());
                while (lcp < n && oldS.charAt(lcp) == now.charAt(lcp)) lcp++;
                int deleteCount = oldS.length() - lcp;
                for (int i = 0; i < deleteCount; i++) {
                    terminalSession.onKeyInput(new byte[]{0x7F}); // Backspace
                }
                String insert = now.substring(lcp);
                if (!insert.isEmpty()) {
                    terminalSession.onKeyInput(insert.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                terminalImeLastProcessed = now;
            }
        };
        terminalImeHost.addTextChangedListener(terminalImeTextWatcher);
    }

    private void detachTerminalImeTextWatcher() {
        if (terminalImeHost != null && terminalImeTextWatcher != null) {
            terminalImeHost.removeTextChangedListener(terminalImeTextWatcher);
        }
        terminalImeTextWatcher = null;
    }

    private String safeImeText() {
        if (terminalImeHost == null) return "";
        Editable ed = terminalImeHost.getText();
        return ed != null ? ed.toString() : "";
    }

    // ── Keyboard teardown ────────────────────────────────────────────────────

    /** Remove keyboard views and reset state. Called on SSH disconnect. */
    private void teardownTerminalKeyboard() {
        if (rootView == null) return;

        boolean isLandscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;

        // Hide IME surface first
        hideTerminalImeSurface();

        if (isLandscape) {
            // Landscape: disconnect split keyboard transports
            if (terminalKeyboardTransportLeft != null) {
                terminalKeyboardTransportLeft.disconnect();
                terminalKeyboardTransportLeft = null;
            }
            if (terminalKeyboardTransportRight != null) {
                terminalKeyboardTransportRight.disconnect();
                terminalKeyboardTransportRight = null;
            }
            if (terminalKeyboardViewLeft != null) {
                terminalKeyboardViewLeft.clearCustomTransport();
            }
            if (terminalKeyboardViewRight != null) {
                terminalKeyboardViewRight.clearCustomTransport();
            }
        } else {
            // Portrait: disconnect single keyboard transport
            if (terminalKeyboardTransport != null) {
                terminalKeyboardTransport.disconnect();
                terminalKeyboardTransport = null;
            }
            if (terminalKeyboardView != null) {
                terminalKeyboardView.clearCustomTransport();
            }
            if (terminalKeyboardSlot != null) {
                terminalKeyboardSlot.removeAllViews();
                terminalKeyboardSlot.setVisibility(View.GONE);
            }
            terminalKeyboardView = null;
        }
        customKeyboardVisible = false;
    }

    /** Toggle custom keyboard visibility (portrait only). */
    private void toggleCustomKeyboard() {
        boolean isLandscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        if (isLandscape) return; // No-op in landscape

        if (customKeyboardVisible) {
            hideCustomKeyboard();
        } else {
            showCustomKeyboard();
        }
    }

    private void setupListeners() {
        connectBtn.setOnClickListener(v -> {
            Log.v(TAG, "TerminalFragment connectBtn clicked, isSshConnected=" + isSshConnected
                    + " isDemoActive=" + isDemoActive);
            if (isSessionActive()) {
                if (isDemoActive) {
                    stopDemo();
                } else {
                    disconnect();
                }
            } else {
                showConnectChoiceDialog();
            }
        });

        if (demoUsbBtn != null) {
            demoUsbBtn.setOnClickListener(v ->
                    showConnectionDialog(TerminalDemoController.DemoTransport.USB));
        }
        if (demoBleBtn != null) {
            demoBleBtn.setOnClickListener(v ->
                    showConnectionDialog(TerminalDemoController.DemoTransport.BLE));
        }

        terminalView.setOnClickListener(v -> {
            boolean isLandscape = getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_LANDSCAPE;
            if (isLandscape) {
                // In landscape, tapping terminal toggles system IME on terminal view
                if (terminalView != null) {
                    terminalView.showKeyboard();
                }
            } else {
                // In portrait, tapping terminal toggles custom keyboard
                if (terminalView != null) {
                    toggleCustomKeyboard();
                }
            }
        });

        // No IME insets listener here. MainActivity uses adjustResize so the window
        // already shrinks for the IME. Adding extra bottom padding would push the
        // keyboard slot ABOVE the system IME (the opposite of what we want).
    }

    private void updateConnectionState() {
        // Update transport button visibility and icon
        if (transportBtn != null) {
            if (isSshConnected || isDemoActive) {
                transportBtn.setVisibility(View.VISIBLE);
                if (isDemoActive && activeDemoTransport == TerminalDemoController.DemoTransport.USB) {
                    transportBtn.setText(R.string.terminal_transport_usb);
                    transportBtn.setIconResource(R.drawable.ic_usb_24);
                } else {
                    transportBtn.setText(R.string.terminal_transport_ble);
                    transportBtn.setIconResource(R.drawable.ic_bluetooth_24);
                }
            } else {
                transportBtn.setVisibility(View.GONE);
            }
        }

        // Update status text
        if (statusText != null) {
            if (isSshConnected || isDemoActive) {
                statusText.setText(R.string.terminal_connected);
            } else {
                statusText.setText(R.string.terminal_disconnected);
            }
        }

        // Update host label
        if (hostLabel != null) {
            if (isSessionActive()) {
                hostLabel.setVisibility(View.VISIBLE);
                String hostText = activeSessionHost != null
                        ? activeSessionHost
                        : TerminalDemoController.DEMO_HOST;
                hostLabel.setText(hostText);
                Log.v(TAG, "Host label set to VISIBLE, text=" + hostText
                        + ", isSshConnected=" + isSshConnected
                        + ", isDemoActive=" + isDemoActive);
            } else {
                hostLabel.setVisibility(View.GONE);
                Log.v(TAG, "Host label set to GONE, isSshConnected=" + isSshConnected
                        + ", isDemoActive=" + isDemoActive);
            }
        } else {
            Log.v(TAG, "hostLabel is null, cannot update visibility");
        }

        // Update connect/disconnect button - always visible
        if (connectBtn != null) {
            connectBtn.setVisibility(View.VISIBLE);
            if (isSessionActive()) {
                connectBtn.setText(R.string.terminal_disconnect);
                connectionOverlay.setVisibility(View.GONE);
                // Hide demo buttons when connected
                if (demoButtonRow != null) {
                    demoButtonRow.setVisibility(View.GONE);
                }
            } else {
                connectBtn.setText(R.string.terminal_connect);
                // Show connection overlay with demo buttons when disconnected
                if (connectionOverlay != null) {
                    connectionOverlay.setVisibility(View.VISIBLE);
                }
                // Show demo buttons row
                if (demoButtonRow != null) {
                    demoButtonRow.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    /** Offer preview demo or real SSH when disconnected. */
    private void showConnectChoiceDialog() {
        showConnectionDialog(null);
    }

    /**
     * Show SSH connection dialog with card-style device list and transport selection.
     */
    private void showConnectionDialog() {
        showConnectionDialog(null);
    }

    private void showConnectionDialog(@Nullable TerminalDemoController.DemoTransport demoTransport) {
        if (getContext() == null) return;
        stopDemo();

        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.terminal_connection_dialog, null);

        // Bind UI elements
        RadioGroup transportGroup = dialogView.findViewById(R.id.terminal_transport_group);
        RadioButton usbRadio = dialogView.findViewById(R.id.transport_usb);
        RadioButton bleRadio = dialogView.findViewById(R.id.transport_ble);
        LinearLayout deviceListContainer = dialogView.findViewById(R.id.device_list_container);
        TextView emptyText = dialogView.findViewById(R.id.device_empty_text);
        TextView longPressHint = dialogView.findViewById(R.id.long_press_hint);
        MaterialButton addProfileBtn = dialogView.findViewById(R.id.add_profile_button);
        View titleContainer = dialogView.findViewById(R.id.title_container);
        ImageView searchButton = dialogView.findViewById(R.id.search_device_button);
        SearchView searchView = dialogView.findViewById(R.id.device_search_view);
        // Load all profiles
        final List<CredentialProfile> allProfiles = credentialManager.getAllProfiles();
        final int[] selectedProfileIndex = {-1};

        // Resolve theme colors for card styling
        final TypedValue primaryTypedValue = new TypedValue();
        requireContext().getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorPrimary, primaryTypedValue, true);
        final int themePrimary = primaryTypedValue.data;

        if (allProfiles.isEmpty()) {
            deviceListContainer.setVisibility(View.GONE);
            titleContainer.setVisibility(View.GONE);
            longPressHint.setVisibility(View.GONE);
            emptyText.setVisibility(View.VISIBLE);
            addProfileBtn.setVisibility(View.VISIBLE);
            addProfileBtn.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), CredentialActivity.class);
                requireContext().startActivity(intent);
            });
        } else {
            // Build initial list (no filter)
            buildDeviceList(allProfiles, selectedProfileIndex, deviceListContainer,
                    "", themePrimary);

            // Toggle search view visibility
            searchButton.setOnClickListener(v -> {
                boolean showing = searchView.getVisibility() == View.VISIBLE;
                if (showing) {
                    // Hide search, show title
                    searchView.setVisibility(View.GONE);
                    searchView.setQuery("", false);
                    titleContainer.setVisibility(View.VISIBLE);
                    buildDeviceList(allProfiles, selectedProfileIndex, deviceListContainer,
                            "", themePrimary);
                } else {
                    // Show search, hide title
                    titleContainer.setVisibility(View.GONE);
                    searchView.setVisibility(View.VISIBLE);
                    searchView.requestFocus();
                }
            });

            // Filter list on search text change
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    return false;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    String query = newText != null ? newText.toLowerCase() : "";
                    buildDeviceList(allProfiles, selectedProfileIndex, deviceListContainer,
                            query, themePrimary);
                    return true;
                }
            });

            addProfileBtn.setVisibility(View.GONE);
        }

        // Check transport availability and set defaults
        if (demoTransport != null) {
            transportGroup.check(demoTransport == TerminalDemoController.DemoTransport.USB
                    ? R.id.transport_usb : R.id.transport_ble);
            usbRadio.setEnabled(demoTransport == TerminalDemoController.DemoTransport.USB);
            bleRadio.setEnabled(demoTransport == TerminalDemoController.DemoTransport.BLE);
        } else {
            boolean usbAvailable = isUsbEcmAvailable();
            boolean bleAvailable = isBleAvailable();

            if (usbAvailable && !bleAvailable) {
                transportGroup.check(R.id.transport_usb);
                bleRadio.setEnabled(false);
            } else if (bleAvailable && !usbAvailable) {
                transportGroup.check(R.id.transport_ble);
                usbRadio.setEnabled(false);
            } else if (bleAvailable) {
                transportGroup.check(R.id.transport_ble);
            } else if (!usbAvailable && !bleAvailable) {
                Toast.makeText(getContext(), R.string.terminal_disconnected_hint, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        int positiveText = demoTransport != null
                ? R.string.terminal_start_demo
                : R.string.terminal_connect;

        new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setPositiveButton(positiveText, (dialog, which) -> {
                    if (allProfiles.isEmpty()) {
                        Toast.makeText(getContext(), R.string.terminal_no_devices, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (selectedProfileIndex[0] < 0 || selectedProfileIndex[0] >= allProfiles.size()) {
                        Toast.makeText(getContext(), R.string.terminal_select_device, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    CredentialProfile profile = allProfiles.get(selectedProfileIndex[0]);
                    String host = profile.getHost();
                    int finalPort = profile.getPort();
                    String username = profile.getUsername();

                    if (host == null || host.isEmpty() || username == null || username.isEmpty()) {
                        Toast.makeText(getContext(), R.string.terminal_host_username_required, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    boolean useUsb = transportGroup.getCheckedRadioButtonId() == R.id.transport_usb;

                    // Save selected profile as active
                    credentialManager.setActiveProfileId(profile.getId());

                    if (demoTransport != null) {
                        statusText.setText(R.string.terminal_connecting);
                        if (hostLabel != null) {
                            hostLabel.setText(host);
                        }
                        startDemo(demoTransport, host);
                    } else {
                        Log.v(TAG, "Dialog positive: authType=" + profile.getAuthType() + " useUsb=" + useUsb);
                        connect(profile, useUsb);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Build the device card list inside the container, filtered by searchQuery.
     * Pre-selects the active profile. Shows empty text if no matches.
     */
    private void buildDeviceList(List<CredentialProfile> allProfiles,
                                  int[] selectedProfileIndex,
                                  LinearLayout deviceListContainer,
                                  String searchQuery, int themePrimary) {
        deviceListContainer.removeAllViews();
        selectedProfileIndex[0] = -1;

        // Filter profiles by name, description, or tags
        List<CredentialProfile> filtered = new ArrayList<>();
        List<Integer> originalIndices = new ArrayList<>();
        for (int i = 0; i < allProfiles.size(); i++) {
            CredentialProfile p = allProfiles.get(i);
            if (searchQuery.isEmpty()) {
                filtered.add(p);
                originalIndices.add(i);
            } else {
                boolean matches = p.getDisplayLabel().toLowerCase().contains(searchQuery)
                        || p.getShortDescription().toLowerCase().contains(searchQuery);
                if (!matches) {
                    // Check if any tag matches
                    for (String tag : p.getTags()) {
                        if (tag.toLowerCase().contains(searchQuery)) {
                            matches = true;
                            break;
                        }
                    }
                }
                if (matches) {
                    filtered.add(p);
                    originalIndices.add(i);
                }
            }
        }

        if (filtered.isEmpty()) {
            TextView noMatch = new TextView(getContext());
            noMatch.setText(R.string.terminal_no_match);
            noMatch.setTextSize(14f);
            noMatch.setTextColor(getResources().getColor(R.color.text_secondary));
            noMatch.setGravity(android.view.Gravity.CENTER);
            noMatch.setPadding(0, 24, 0, 24);
            deviceListContainer.addView(noMatch);
            return;
        }

        final LayoutInflater cardInflater = LayoutInflater.from(getContext());
        CredentialProfile activeProfile = credentialManager.getActiveProfile();

        for (int i = 0; i < filtered.size(); i++) {
            final CredentialProfile profile = filtered.get(i);
            final int originalIndex = originalIndices.get(i);

            View cardView = cardInflater.inflate(
                    R.layout.item_dialog_device, deviceListContainer, false);

            MaterialCardView card = cardView.findViewById(R.id.device_card);
            ImageView radioIndicator = cardView.findViewById(R.id.device_radio);
            TextView nameText = cardView.findViewById(R.id.device_name);
            TextView descText = cardView.findViewById(R.id.device_details);

            nameText.setText(profile.getDisplayLabel());
            descText.setText(profile.getShortDescription());

            applyUnselectedCardStyle(card, radioIndicator, nameText, descText);

            // Pre-select active profile
            boolean isActive = activeProfile != null
                    && profile.getId().equals(activeProfile.getId());
            if (isActive) {
                selectedProfileIndex[0] = originalIndex;
                applySelectedCardStyle(card, radioIndicator, nameText, descText, themePrimary);
            }

            card.setOnClickListener(v -> {
                int prevIndex = selectedProfileIndex[0];
                selectedProfileIndex[0] = originalIndex;

                // Reset previously selected card
                if (prevIndex >= 0 && prevIndex < deviceListContainer.getChildCount()) {
                    View prevChild = deviceListContainer.getChildAt(prevIndex);
                    if (prevChild instanceof MaterialCardView) {
                        MaterialCardView prevCard = (MaterialCardView) prevChild;
                        ImageView prevRadio = prevCard.findViewById(R.id.device_radio);
                        TextView prevName = prevCard.findViewById(R.id.device_name);
                        TextView prevDesc = prevCard.findViewById(R.id.device_details);
                        applyUnselectedCardStyle(prevCard, prevRadio, prevName, prevDesc);
                    }
                }

                applySelectedCardStyle(card, radioIndicator, nameText, descText, themePrimary);
            });

            card.setOnLongClickListener(v -> {
                showDeviceInfoDialog(profile);
                return true;
            });

            deviceListContainer.addView(cardView);
        }
    }

    /**
     * Apply selected card style: theme primary border + bullseye radio indicator.
     * Uses MaterialCardView API so stroke/color are set via properties.
     */
    private void applySelectedCardStyle(MaterialCardView card, ImageView radio,
                                         TextView nameText, TextView descText, int themePrimary) {
        // Card: soft light-gray stroke via MaterialCardView stroke API
        int strokeWidth = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 1.5f, getResources().getDisplayMetrics());
        card.setStrokeWidth(strokeWidth);
        card.setStrokeColor(0xFFBDBDBD);
        card.setCardBackgroundColor(getResources().getColor(R.color.terminal_toolbar_background));

        // Radio indicator: ring with inner dot (bullseye style)
        GradientDrawable ringBg = new GradientDrawable();
        ringBg.setShape(GradientDrawable.OVAL);
        int radioSize = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics());
        ringBg.setSize(radioSize, radioSize);
        ringBg.setColor(android.graphics.Color.TRANSPARENT);
        ringBg.setStroke(
                (int) TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics()),
                themePrimary);

        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(themePrimary);

        int gap = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 5, getResources().getDisplayMetrics());
        LayerDrawable radioBg = new LayerDrawable(
                new android.graphics.drawable.Drawable[]{ringBg, dotBg});
        radioBg.setLayerInset(1, gap, gap, gap, gap);
        radio.setImageDrawable(radioBg);

        nameText.setTextColor(getResources().getColor(R.color.text_primary));
        descText.setTextColor(getResources().getColor(R.color.text_secondary));
    }

    /**
     * Apply unselected card style: no stroke + hollow gray radio indicator.
     */
    private void applyUnselectedCardStyle(MaterialCardView card, ImageView radio,
                                           TextView nameText, TextView descText) {
        card.setStrokeWidth(0);
        card.setCardBackgroundColor(getResources().getColor(R.color.terminal_toolbar_background));

        GradientDrawable radioBg = new GradientDrawable();
        radioBg.setShape(GradientDrawable.OVAL);
        int radioSize = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics());
        radioBg.setSize(radioSize, radioSize);
        radioBg.setStroke(
                (int) TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics()),
                getResources().getColor(R.color.gray_400));
        radio.setImageDrawable(radioBg);

        nameText.setTextColor(getResources().getColor(R.color.text_primary));
        descText.setTextColor(getResources().getColor(R.color.text_secondary));
    }

    /**
     * Show a device info dialog with full profile details when the user
     * long-presses a device card in the connection dialog.
     */
    private void showDeviceInfoDialog(CredentialProfile profile) {
        if (getContext() == null) return;

        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_device_info, null);

        TextView nameText = dialogView.findViewById(R.id.info_device_name);
        TextView hostText = dialogView.findViewById(R.id.info_host);
        TextView portText = dialogView.findViewById(R.id.info_port);
        TextView usernameText = dialogView.findViewById(R.id.info_username);
        TextView authTypeText = dialogView.findViewById(R.id.info_auth_type);
        TextView tagsText = dialogView.findViewById(R.id.info_tags);
        TextView notesText = dialogView.findViewById(R.id.info_notes);
        TextView createdText = dialogView.findViewById(R.id.info_created);
        TextView updatedText = dialogView.findViewById(R.id.info_updated);

        nameText.setText(profile.getDisplayLabel());
        hostText.setText(profile.getHost() != null ? profile.getHost() : "");
        portText.setText(String.valueOf(profile.getPort()));
        usernameText.setText(profile.getUsername() != null ? profile.getUsername() : "");

        String authType = profile.isSshKeyAuth()
                ? getString(R.string.credential_auth_type_ssh_key)
                : getString(R.string.credential_auth_type_password);
        authTypeText.setText(authType);

        List<String> tags = profile.getTags();
        if (tags != null && !tags.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < tags.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(tags.get(i));
            }
            tagsText.setText(sb.toString());
        } else {
            tagsText.setText(R.string.terminal_device_info_no_tags);
            tagsText.setTextColor(getResources().getColor(R.color.text_secondary));
        }

        String notes = profile.getNotes();
        if (notes != null && !notes.isEmpty()) {
            notesText.setText(notes);
        } else {
            notesText.setText(R.string.terminal_device_info_no_notes);
            notesText.setTextColor(getResources().getColor(R.color.text_secondary));
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        createdText.setText(dateFormat.format(new Date(profile.getCreatedAt())));
        updatedText.setText(dateFormat.format(new Date(profile.getUpdatedAt())));

        new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setNegativeButton(R.string.credential_edit, (dialog, which) -> {
                    Intent intent = new Intent(requireContext(), CredentialActivity.class);
                    intent.putExtra(CredentialActivity.EXTRA_EDIT_PROFILE_ID, profile.getId());
                    requireContext().startActivity(intent);
                })
                .setPositiveButton(R.string.terminal_device_info_close, null)
                .show();
    }

    private boolean isSessionActive() {
        return isSshConnected || isDemoActive;
    }

    private void resetTerminalSession() {
        terminalSession = new TerminalSession(
                prefs.getTerminalRows(),
                prefs.getTerminalCols(),
                prefs.getScrollbackSize()
        );
        terminalView.setTerminalSession(terminalSession);
    }

    private void startDemo(@NonNull TerminalDemoController.DemoTransport transport) {
        startDemo(transport, TerminalDemoController.DEMO_HOST);
    }

    private void startDemo(@NonNull TerminalDemoController.DemoTransport transport, @NonNull String host) {
        if (getContext() == null || terminalSession == null || demoController == null) {
            return;
        }
        disconnect();
        resetTerminalSession();
        isDemoActive = true;
        activeDemoTransport = transport;
        activeSessionHost = host;
        updateConnectionState();
        statusText.setText(R.string.terminal_connecting);

        demoController.start(requireContext(), terminalSession, transport,
                new TerminalDemoController.Listener() {
                    @Override
                    public void onOutputAppended() {
                        if (terminalView != null) {
                            terminalView.invalidate();
                        }
                    }

                    @Override
                    public void onFinished() {
                        if (!isDemoActive) {
                            return;
                        }
                        statusText.setText(R.string.terminal_connected);
                        if (terminalView != null) {
                            terminalView.invalidate();
                        }
                    }
                });
    }

    private void stopDemo() {
        if (demoController != null) {
            demoController.stop();
        }
        if (!isDemoActive) {
            return;
        }
        isDemoActive = false;
        activeDemoTransport = null;
        activeSessionHost = null;
        teardownTerminalKeyboard();
        resetTerminalSession();
        updateConnectionState();
    }

    /**
     * Connect to SSH via the selected transport.
     */
    private void connect(CredentialProfile profile, boolean useUsb) {
        Log.v(TAG, "connect called: authType=" + profile.getAuthType() + " useUsb=" + useUsb);
        stopDemo();
        activeSessionHost = profile.getHost();
        statusText.setText(R.string.terminal_connecting);

        if (useUsb) {
            connectUsbEcm(profile);
        } else {
            connectBleEth(profile);
        }
    }

    /**
     * Connect via USB ECM transport (direct socket).
     */
    private void connectUsbEcm(CredentialProfile profile) {
        final String host = profile.getHost();
        final int port = profile.getPort();
        final String username = profile.getUsername();
        final String password = profile.getPassword();
        usbEcmTransport = new UsbEcmTransport();

        // Set up transport listener BEFORE connect so the read thread can deliver data.
        usbEcmTransport.setListener(new TransportAdapter.Listener() {
            @Override
            public void onDataReceived(byte[] data, int len) {
                if (sshClient != null) {
                    sshClient.onDataReceivedFromTransport(data, len);
                }
            }

            @Override
            public void onDisconnected() {
                mainHandler.post(() -> {
                    if (viewDestroyed) return;
                    isSshConnected = false;
                    teardownTerminalKeyboard();
                    if (statusText != null) {
                        statusText.setText(R.string.terminal_disconnected);
                    }
                    if (connectBtn != null) {
                        connectBtn.setText(R.string.terminal_connect);
                    }
                    if (connectionOverlay != null) {
                        connectionOverlay.setVisibility(View.VISIBLE);
                    }
                    if (terminalView != null) {
                        terminalView.postInvalidate();
                    }
                });
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "SSH error: " + message);
                mainHandler.post(() -> {
                    if (viewDestroyed || getContext() == null) return;
                    String displayMessage;
                    if (message.contains("AUTH_FAILED")) {
                        displayMessage = getString(R.string.terminal_auth_failed);
                    } else {
                        displayMessage = getFriendlyErrorMessage(message);
                    }
                    if (statusText != null) {
                        statusText.setText(displayMessage);
                    }
                    Toast.makeText(getContext(), displayMessage, Toast.LENGTH_LONG).show();
                    isSshConnected = false;
                    teardownTerminalKeyboard();
                    updateConnectionState();
                });
            }
        });

        // First establish TCP connection
        new Thread(() -> {
            usbEcmTransport.connect(host, port, 15000);

            if (!usbEcmTransport.isConnected()) {
                mainHandler.post(() -> {
                    if (viewDestroyed || getContext() == null) return;
                    if (statusText != null) {
                        statusText.setText(getString(R.string.terminal_connection_failed) + ": USB ECM");
                    }
                    if (connectionOverlay != null) {
                        connectionOverlay.setVisibility(View.VISIBLE);
                    }
                });
                return;
            }

            // Then establish SSH session over the TCP connection
            // Use the actual host from the dialog, not from prefs.
            runSshSession(profile, usbEcmTransport);
        }).start();
    }

    /**
     * Connect via BLE-Eth transport. Uses the app's BluetoothService.
     */
    private void connectBleEth(CredentialProfile profile) {
        final String host = profile.getHost();
        final int port = profile.getPort();
        Log.v(TAG, "connectBleEth called");
        if (mainActivity == null) {
            Log.e(TAG, "connectBleEth: mainActivity is null");
            return;
        }
        BluetoothService bluetoothService = mainActivity.getBluetoothService();
        Log.v(TAG, "connectBleEth: bluetoothService=" + (bluetoothService != null)
                + " isConnected=" + (bluetoothService != null && bluetoothService.isConnected()));
        if (bluetoothService == null || !bluetoothService.isConnected()) {
            mainHandler.post(() -> {
                if (viewDestroyed || getContext() == null) return;
                if (statusText != null) {
                    statusText.setText(R.string.terminal_connection_failed + ": BLE not connected");
                }
                if (connectionOverlay != null) {
                    connectionOverlay.setVisibility(View.VISIBLE);
                }
            });
            return;
        }

        // Clean up any previous BLE-Eth connection
        if (bleEthCallback != null) {
            bluetoothService.removeBleEthCallback(bleEthCallback);
            bleEthCallback = null;
        }
        if (bleEthTransport != null) {
            bleEthTransport.disconnect();
            bleEthTransport = null;
        }
        if (bleEthSocketFactory != null) {
            bleEthSocketFactory = null;
        }

        // Send DISCONNECT for all possible connection slots (0-5) to clean up
        // any stale firmware state. The firmware's TCP tunnel slot may be stuck
        // from a previous stalled session, causing new CONNECT to fail.
        for (int cid = 0; cid <= 5; cid++) {
            byte[] cleanupFrame = buildDisconnectFrame(cid);
            Log.v(TAG, "connectBleEth: sending cleanup DISCONNECT for connId=" + cid);
            bluetoothService.writeBleEthData(cleanupFrame);
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        }
        // Wait for firmware to process DISCONNECT frames.
        // Firmware typically completes within 500ms; 3000ms provides safe margin
        // for BLE latency and edge cases without excessive user wait time.
        Log.v(TAG, "connectBleEth: waiting 3000ms for firmware cleanup");
        try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
        Log.v(TAG, "connectBleEth: cleanup complete, creating new BleEthTransport");

        bleEthTransport = new BleEthTransport(bluetoothService::writeBleEthData);
        Log.v(TAG, "connectBleEth: BleEthTransport created");

        // Register for incoming BLE-Eth data
        bleEthCallback = data -> {
            Log.v(TAG, "BLE-Eth callback: received " + data.length + " bytes");
            BleEthTransport transport = bleEthTransport;
            if (transport != null && !viewDestroyed) {
                transport.handleIncomingData(data);
            }
        };
        Log.v(TAG, "connectBleEth: registering BLE-Eth callback with BluetoothService");
        bluetoothService.addBleEthCallback(bleEthCallback);
        Log.v(TAG, "connectBleEth: callback registered, starting connect thread");

        // Create SocketFactory that bridges JSch to BleEthTransport.
        // Pass the real target host/port so connectTunnel() sends the correct CONNECT frame.
        bleEthSocketFactory = new BleEthSocketFactory(bleEthTransport, host, port);
        Log.v(TAG, "connectBleEth: SocketFactory created, starting connect thread");

        // Start SSH session on background thread (SocketFactory handles BLE-Eth connect)
        new Thread(() -> {
            Log.v(TAG, "BLE-Eth SSH connect thread started");
            runSshSessionWithSocketFactory(profile, bleEthTransport, bleEthSocketFactory);
            Log.v(TAG, "BLE-Eth SSH connect thread finished");
        }).start();
    }

    /**
     * Create the shared SSH client listener.
     * Handles connect/disconnect lifecycle, data routing, and error display.
     * Used by both {@link #runSshSession} and {@link #runSshSessionWithSocketFactory}.
     */
    private SshClient.Listener createSshListener() {
        return new SshClient.Listener() {
            @Override
            public void onConnected() {
                mainHandler.post(() -> {
                    if (viewDestroyed) return;
                    isSshConnected = true;
                    updateConnectionState();
                    mainHandler.postDelayed(() -> {
                        if (!viewDestroyed && rootView != null && isSshConnected) {
                            showCustomKeyboard();
                        }
                    }, 150);
                });
                // Start the shell channel (runs on background thread)
                SshClient client = sshClient;
                if (client != null && !viewDestroyed) {
                    client.startShell(terminalSession);
                }
            }

            @Override
            public void onDisconnected() {
                mainHandler.post(() -> {
                    if (viewDestroyed) return;
                    isSshConnected = false;
                    updateConnectionState();
                    teardownTerminalKeyboard();
                    if (terminalView != null) {
                        terminalView.postInvalidate();
                    }
                });
            }

            @Override
            public void onDataReceived(byte[] data, int len) {
                terminalSession.append(data, len);
                mainHandler.post(() -> {
                    if (!viewDestroyed && terminalView != null) {
                        terminalView.invalidate();
                    }
                });
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "SSH error: " + message);
                mainHandler.post(() -> {
                    if (viewDestroyed || getContext() == null) return;
                    String displayMessage;
                    if (message != null && message.contains("AUTH_FAILED")) {
                        displayMessage = getString(R.string.terminal_auth_failed);
                    } else {
                        displayMessage = getFriendlyErrorMessage(message);
                    }
                    if (statusText != null) {
                        statusText.setText(displayMessage);
                    }
                    Toast.makeText(getContext(), displayMessage, Toast.LENGTH_LONG).show();
                    isSshConnected = false;
                    teardownTerminalKeyboard();
                    updateConnectionState();
                });
            }
        };
    }

    /**
     * Run the SSH session with a custom SocketFactory (for BLE-Eth).
     */
    private void runSshSessionWithSocketFactory(CredentialProfile profile,
                                                 TransportAdapter transport,
                                                 com.jcraft.jsch.SocketFactory socketFactory) {
        sshClient = new SshClient(profile, transport, socketFactory);
        sshClient.setListener(createSshListener());
        sshClient.connect();
    }

    /**
     * Run the SSH session over an established transport (USB ECM - direct socket).
     */
    private void runSshSession(CredentialProfile profile, TransportAdapter transport) {
        sshClient = new SshClient(profile, transport);
        sshClient.setListener(createSshListener());
        sshClient.connect();
    }

    /**
     * Disconnect the current SSH session.
     */
    private void disconnect() {
        if (sshClient != null) {
            sshClient.disconnect();
            sshClient = null;
        }
        // Clean up BLE-Eth callback
        if (bleEthCallback != null && mainActivity != null) {
            BluetoothService bs = mainActivity.getBluetoothService();
            if (bs != null) {
                bs.removeBleEthCallback(bleEthCallback);
            }
            bleEthCallback = null;
        }
        bleEthTransport = null;
        bleEthSocketFactory = null;
        usbEcmTransport = null;
        isSshConnected = false;
        teardownTerminalKeyboard();
        updateConnectionState();
    }

    /**
     * Build a raw DISCONNECT BLE-Eth frame for a given connection ID.
     */
    private static byte[] buildDisconnectFrame(int connId) {
        byte[] frame = new byte[7];
        frame[0] = 0x57;
        frame[1] = (byte) 0xAB;
        frame[2] = 0x00;  // addr
        frame[3] = 0x12;  // CMD_DISCONNECT
        frame[4] = 0x01;  // payload length
        frame[5] = (byte) connId;
        int checksum = 0;
        for (int i = 0; i < 6; i++) {
            checksum += frame[i] & 0xFF;
        }
        frame[6] = (byte) (checksum & 0xFF);
        return frame;
    }

    /**
     * Convert byte array to hex string for logging.
     */
    private static String bytesToHex(byte[] data) {
        if (data == null) return "null";
        StringBuilder sb = new StringBuilder(data.length * 3);
        for (byte b : data) {
            sb.append(String.format("%02X ", b & 0xFF));
        }
        return sb.toString().trim();
    }

    private boolean isUsbEcmAvailable() {
        if (mainActivity == null) return false;
        ConnectionManager cm = mainActivity.getConnectionManager();
        return cm != null && cm.isConnected();
    }

    private boolean isBleAvailable() {
        if (mainActivity == null) return false;
        BluetoothService bluetoothService = mainActivity.getBluetoothService();
        return bluetoothService != null && bluetoothService.isConnected();
    }

    /**
     * Convert technical error messages to user-friendly ones.
     * @param errorMessage The raw error message from SSH client
     * @return A user-friendly error message
     */
    private String getFriendlyErrorMessage(String errorMessage) {
        if (errorMessage == null) {
            return getString(R.string.terminal_connection_failed);
        }

        String lowerError = errorMessage.toLowerCase();

        // Connection refused - target host is reachable but not accepting connections
        if (lowerError.contains("connection refused") || lowerError.contains("refused")) {
            return getString(R.string.terminal_connection_failed) + ": " + getString(R.string.terminal_error_connection_refused);
        }

        // Connection timeout - network issue or host not responding
        if (lowerError.contains("timeout") || lowerError.contains("timed out")) {
            return getString(R.string.terminal_connection_failed) + ": " + getString(R.string.terminal_error_connection_timeout);
        }

        // Unknown host - DNS resolution failed.
        // NOTE: Distinguishes from "UnknownHostKey" (JSch host-key verification failure)
        // which contains "UnknownHostKey" but the user only sees "unknownhost" when
        // mapped to the i18n "Unknown host" message above. We check the more specific
        // JSch message "UnknownHostKey" first so it has its own mapping.
        if (lowerError.contains("unknownhostkey") || lowerError.contains("unknown host key")) {
            return getString(R.string.terminal_connection_failed) + ": " + getString(R.string.terminal_error_host_key_unknown);
        }

        if (lowerError.contains("unknownhost") || lowerError.contains("unknown host") || lowerError.contains("resolve")) {
            return getString(R.string.terminal_connection_failed) + ": " + getString(R.string.terminal_error_unknown_host);
        }

        // No route to host - network routing issue
        if (lowerError.contains("no route") || lowerError.contains("network is unreachable")) {
            return getString(R.string.terminal_connection_failed) + ": " + getString(R.string.terminal_error_no_route);
        }

        // For other errors, show a generic message
        return getString(R.string.terminal_connection_failed) + ": " + getString(R.string.terminal_error_generic);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateConnectionState();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Re-inflate layout to switch between portrait and landscape layouts.
        // MainActivity uses configChanges="orientation" so the Activity
        // is not recreated; only onConfigurationChanged is called.
        reInflateLayout();
        // Notify remote PTY of new terminal size after rotation.
        if (sshClient != null && isSshConnected && terminalSession != null) {
            mainHandler.postDelayed(() -> {
                if (sshClient != null && isSshConnected && terminalSession != null) {
                    sshClient.resizeTerminal(terminalSession.getColumns(), terminalSession.getRows());
                }
            }, 300);  // Wait for layout to complete and onSizeChanged() to update dimensions
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("custom_keyboard_visible", customKeyboardVisible);
    }

    @Override
    public void onDestroyView() {
        // 1. Set guard flag FIRST to prevent background callbacks from accessing Views
        viewDestroyed = true;

        // 2. Cancel all Handler callbacks to prevent accessing destroyed Views
        mainHandler.removeCallbacksAndMessages(null);

        // 3. Stop demo and disconnect SSH
        stopDemo();
        disconnect();

        // 4. Clean up keyboard transport
        teardownTerminalKeyboard();

        // 5. Clear all View references to prevent memory leaks
        rootView = null;
        terminalView = null;
        terminalKeyboardSlot = null;
        terminalImeHost = null;
        terminalKeyboardView = null;
        terminalKeyboardViewLeft = null;
        terminalKeyboardViewRight = null;
        terminalSplitTopLeft = null;
        terminalSplitTopRight = null;
        connectBtn = null;
        statusText = null;
        hostLabel = null;
        transportBtn = null;
        connectionOverlay = null;
        demoButtonRow = null;
        demoUsbBtn = null;
        demoBleBtn = null;
        mainActivity = null;

        super.onDestroyView();
    }
}
