package com.openterface.fragment;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.R;
import com.openterface.keymod.basic.KmBasicHoldLockController;
import com.openterface.keymod.fragments.KeyboardMouseSettingsFragment;
import com.hoho.android.usbserial.driver.UsbSerialPort;

/**
 * Standard &quot;Keyboard &amp; Mouse&quot; mode: KM Basic full-screen sub-modes (keyboard, numpad,
 * touchpad, compose; Basic preferences open from the setup icon in the chrome strip). Pro
 * composite experience lives in {@link CompositeFragment}.
 */
public final class KeyboardMouseFragment extends Fragment {

    public static final String EXTRA_INITIAL_SUBMODE = "kb_mouse_initial_submode";

    private static final String ARG_INITIAL_SUBMODE = "initial_submode";
    private static final String STATE_SUBMODE = "state_submode";

    public static final String SUBMODE_KEYBOARD = "keyboard";
    public static final String SUBMODE_NUMPAD = "numpad";
    public static final String SUBMODE_TOUCHPAD = "touchpad";
    public static final String SUBMODE_COMPOSE = "compose";
    public static final String SUBMODE_SETTINGS = "settings";

    public UsbSerialPort port;

    private String currentSubmode = SUBMODE_KEYBOARD;

    @Nullable private ImageButton chromeMenu;
    @Nullable private TextView tabKeyboard;
    @Nullable private TextView tabTouch;
    @Nullable private TextView tabNum;
    @Nullable private TextView tabIme;
    @Nullable private ImageButton chromeSetup;
    @Nullable private ImageButton chromeTargetOs;
    @Nullable private ImageView chromeConnectionIcon;
    @Nullable private LinearLayout chromeConnectionWrap;
    @Nullable private View kbMouseHost;

    /** Session-scoped modifier / mouse-button locks for KM Basic sub-modes. */
    private final KmBasicHoldLockController holdLockController = new KmBasicHoldLockController();

    private final MainActivity.OnTargetOsChangeListener basicOsListener =
            os -> {
                refreshBasicEmbeddedChrome();
                notifyKeyboardBodyIfShown();
            };

    public KmBasicHoldLockController getHoldLockController() {
        return holdLockController;
    }

    public static KeyboardMouseFragment newInstance(UsbSerialPort port, @Nullable String initialSubmode) {
        KeyboardMouseFragment f = new KeyboardMouseFragment();
        f.port = port;
        Bundle args = new Bundle();
        if (initialSubmode != null) {
            args.putString(ARG_INITIAL_SUBMODE, initialSubmode);
        }
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            currentSubmode = savedInstanceState.getString(STATE_SUBMODE, SUBMODE_KEYBOARD);
        } else {
            Bundle args = getArguments();
            if (args != null && args.containsKey(ARG_INITIAL_SUBMODE)) {
                String s = args.getString(ARG_INITIAL_SUBMODE);
                if (s != null && isKnownSubmode(s)) {
                    currentSubmode = s;
                }
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_keyboard_mouse, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        kbMouseHost = view.findViewById(R.id.kb_mouse_host);
        View chromeInset = view.findViewById(R.id.km_basic_chrome_inset_container);
        applyKmBasicChromeTopInset(chromeInset);
        wireChrome(view);
        showSubmode(currentSubmode);
    }

    /**
     * Adds top padding only to the tab chrome strip (not the whole KM Basic host), using status-bar
     * insets. Scoped this way so we do not shrink the keyboard area or affect other activities.
     * Avoids merging displayCutout into top (can over-pad on some devices when combined with
     * statusBars).
     *
     * <p>In landscape, pads the trailing end with {@code max(navigationBars, displayCutout)} on the
     * end axis so Target OS, Setup, and connection stay clear of side system navigation (same merge as
     * {@link com.openterface.fragment.BasicComposeFragment#setupBasicComposeImeInsets}).
     */
    private void applyKmBasicChromeTopInset(@Nullable View chromeInsetContainer) {
        if (chromeInsetContainer == null) {
            return;
        }
        final int baseStart = ViewCompat.getPaddingStart(chromeInsetContainer);
        final int baseTop = chromeInsetContainer.getPaddingTop();
        final int baseEnd = ViewCompat.getPaddingEnd(chromeInsetContainer);
        final int baseBottom = chromeInsetContainer.getPaddingBottom();
        final int minTop =
                getResources().getDimensionPixelSize(R.dimen.km_basic_chrome_min_top_padding);

        ViewCompat.setOnApplyWindowInsetsListener(
                chromeInsetContainer,
                (v, windowInsets) -> {
                    int statusTop =
                            windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
                    int topPad = baseTop + (statusTop > 0 ? statusTop : minTop);
                    boolean landscape =
                            v.getResources().getConfiguration().orientation
                                    == Configuration.ORIENTATION_LANDSCAPE;
                    int endPad = baseEnd;
                    if (landscape) {
                        Insets bars =
                                windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
                        Insets cut =
                                windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());
                        endPad = baseEnd + Math.max(bars.right, cut.right);
                    }
                    ViewCompat.setPaddingRelative(v, baseStart, topPad, endPad, baseBottom);
                    return windowInsets;
                });
        ViewCompat.requestApplyInsets(chromeInsetContainer);
    }

    @Override
    public void onResume() {
        super.onResume();
        applyOrientationForCurrentSubmode();
        updateKbMouseHostVisibilityForCurrentState();
        MainActivity ma = mainActivity();
        if (ma != null) {
            ma.addOsChangeListener(basicOsListener);
        }
        refreshBasicEmbeddedChrome();
        notifyKeyboardBodyIfShown();
    }

    @Override
    public void onPause() {
        if (getActivity() != null) {
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        }
        MainActivity ma = mainActivity();
        if (ma != null) {
            ma.removeOsChangeListener(basicOsListener);
        }
        super.onPause();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateKbMouseHostVisibilityForCurrentState();
        refreshBasicEmbeddedChrome();
        notifyKeyboardBodyIfShown();
    }

    /**
     * Full-width PC keyboard is only practical in landscape. Compose &amp; send locks to portrait
     * (including upside-down) only. Other KM Basic submodes follow full rotation.
     */
    private void applyOrientationForCurrentSubmode() {
        if (getActivity() == null) {
            return;
        }
        if (SUBMODE_KEYBOARD.equals(currentSubmode)) {
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } else if (SUBMODE_COMPOSE.equals(currentSubmode)) {
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        } else {
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        }
    }

    private void wireChrome(@NonNull View root) {
        chromeMenu = root.findViewById(R.id.basic_km_menu_button);
        tabKeyboard = root.findViewById(R.id.basic_km_tab_keyboard);
        tabTouch = root.findViewById(R.id.basic_km_tab_touchpad);
        tabNum = root.findViewById(R.id.basic_km_tab_numpad);
        tabIme = root.findViewById(R.id.basic_km_tab_ime);
        chromeSetup = root.findViewById(R.id.basic_km_setup_button);
        chromeTargetOs = root.findViewById(R.id.basic_km_target_os);
        chromeConnectionWrap = root.findViewById(R.id.basic_km_connection);
        chromeConnectionIcon = root.findViewById(R.id.basic_km_connection_icon);

        if (chromeMenu != null) {
            chromeMenu.setOnClickListener(
                    v -> {
                        MainActivity ma = mainActivity();
                        if (ma != null) {
                            ma.openDrawerForBasic();
                        }
                    });
        }
        if (tabKeyboard != null) {
            tabKeyboard.setOnClickListener(v -> requestSubmode(SUBMODE_KEYBOARD));
        }
        if (tabTouch != null) {
            tabTouch.setOnClickListener(v -> requestSubmode(SUBMODE_TOUCHPAD));
        }
        if (tabNum != null) {
            tabNum.setOnClickListener(v -> requestSubmode(SUBMODE_NUMPAD));
        }
        if (tabIme != null) {
            tabIme.setOnClickListener(v -> requestSubmode(SUBMODE_COMPOSE));
        }
        if (chromeSetup != null) {
            chromeSetup.setOnClickListener(v -> requestSubmode(SUBMODE_SETTINGS));
        }
        if (chromeTargetOs != null) {
            chromeTargetOs.setOnClickListener(
                    v -> {
                        MainActivity ma = mainActivity();
                        if (ma != null) {
                            ma.showTargetOsPickerDialogFromBasic();
                        }
                    });
        }
        if (chromeConnectionWrap != null) {
            chromeConnectionWrap.setOnClickListener(
                    v -> {
                        MainActivity ma = mainActivity();
                        if (ma != null) {
                            ma.showConnectionDialogFromBasic();
                        }
                    });
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_SUBMODE, currentSubmode);
    }

    @Override
    public void onDestroy() {
        MainActivity ma = mainActivity();
        if (ma != null) {
            holdLockController.clearAllAndReleaseHid(
                    port, ma.getBluetoothService(), ma.isBluetoothServiceBound());
        } else {
            holdLockController.clearAllAndReleaseHid(null, null, false);
        }
        super.onDestroy();
    }

    /** Called from {@link com.openterface.keymod.MainActivity} when the serial port changes. */
    public void onPortChanged(UsbSerialPort newPort) {
        MainActivity ma = mainActivity();
        UsbSerialPort oldPort = port;
        if (newPort == null && ma != null && holdLockController.hasAnyLock()) {
            holdLockController.clearAllAndReleaseHid(
                    oldPort, ma.getBluetoothService(), ma.isBluetoothServiceBound());
        }
        port = newPort;
        Fragment child = getChildFragmentManager().findFragmentById(R.id.kb_mouse_host);
        if (child instanceof BasicKeyboardFragment) {
            ((BasicKeyboardFragment) child).onHostPortChanged(newPort);
        } else if (child instanceof BasicNumPadFragment) {
            ((BasicNumPadFragment) child).onHostPortChanged(newPort);
        } else if (child instanceof BasicTouchpadFragment) {
            ((BasicTouchpadFragment) child).onHostPortChanged(newPort);
        } else if (child instanceof BasicComposeFragment) {
            ((BasicComposeFragment) child).onHostPortChanged(newPort);
        }
        refreshBasicEmbeddedChrome();
        notifyKeyboardBodyIfShown();
    }

    public void requestSubmode(@NonNull String submode) {
        showSubmode(submode);
    }

    @NonNull
    public String getCurrentSubmodePublic() {
        return currentSubmode;
    }

    public void refreshBasicEmbeddedChrome() {
        MainActivity ma = mainActivity();
        if (ma == null) {
            return;
        }
        if (chromeTargetOs != null) {
            ma.applyBasicTargetOsIcon(chromeTargetOs);
        }
        ConnectionManager cm = ma.getConnectionManager();
        if (cm != null && chromeConnectionIcon != null) {
            ma.applyBasicConnectionIcon(
                    chromeConnectionIcon, cm.getCurrentConnectionType(), cm.getCurrentConnectionState());
        }
        updateTabSelection();
    }

    private void updateTabSelection() {
        if (tabKeyboard != null) {
            tabKeyboard.setSelected(SUBMODE_KEYBOARD.equals(currentSubmode));
        }
        if (tabTouch != null) {
            tabTouch.setSelected(SUBMODE_TOUCHPAD.equals(currentSubmode));
        }
        if (tabNum != null) {
            tabNum.setSelected(SUBMODE_NUMPAD.equals(currentSubmode));
        }
        if (tabIme != null) {
            tabIme.setSelected(SUBMODE_COMPOSE.equals(currentSubmode));
        }
        if (chromeSetup != null) {
            boolean settings = SUBMODE_SETTINGS.equals(currentSubmode);
            chromeSetup.setSelected(settings);
            int tint =
                    ContextCompat.getColor(
                            chromeSetup.getContext(),
                            settings ? R.color.primary : R.color.text_secondary);
            chromeSetup.setImageTintList(ColorStateList.valueOf(tint));
        }
    }

    private void notifyKeyboardBodyIfShown() {
        Fragment child = getChildFragmentManager().findFragmentById(R.id.kb_mouse_host);
        if (child instanceof BasicKeyboardFragment) {
            ((BasicKeyboardFragment) child).bindKeyboard();
        }
    }

    private void showSubmode(@NonNull String submode) {
        if (!isKnownSubmode(submode)) {
            submode = SUBMODE_KEYBOARD;
        }
        currentSubmode = submode;
        applyOrientationForCurrentSubmode();
        updateKbMouseHostVisibilityForCurrentState();
        Fragment f = buildChildForSubmode(submode);
        FragmentTransaction tx = getChildFragmentManager().beginTransaction();
        tx.replace(R.id.kb_mouse_host, f);
        tx.commit();
        getChildFragmentManager().executePendingTransactions();
        refreshBasicEmbeddedChrome();
        notifyKeyboardBodyIfShown();
    }

    /**
     * Avoids one squeezed portrait layout pass of the full PC keyboard: when the keyboard submode
     * is active but the device is still portrait (before {@link #applyOrientationForCurrentSubmode}
     * takes effect), the host stays {@link View#GONE} until {@link #onConfigurationChanged} runs in
     * landscape.
     */
    private void updateKbMouseHostVisibilityForCurrentState() {
        if (kbMouseHost == null) {
            return;
        }
        if (!SUBMODE_KEYBOARD.equals(currentSubmode)) {
            kbMouseHost.setVisibility(View.VISIBLE);
            return;
        }
        int o = getResources().getConfiguration().orientation;
        kbMouseHost.setVisibility(o == Configuration.ORIENTATION_PORTRAIT ? View.GONE : View.VISIBLE);
    }

    @NonNull
    private Fragment buildChildForSubmode(@NonNull String submode) {
        switch (submode) {
            case SUBMODE_KEYBOARD:
                return BasicKeyboardFragment.instantiateWithPort(port);
            case SUBMODE_NUMPAD:
                return BasicNumPadFragment.instantiateWithPort(port);
            case SUBMODE_TOUCHPAD:
                return BasicTouchpadFragment.instantiateWithPort(port);
            case SUBMODE_COMPOSE:
                return BasicComposeFragment.instantiateWithPort(port);
            case SUBMODE_SETTINGS:
                return new KeyboardMouseSettingsFragment();
            default:
                return BasicKeyboardFragment.instantiateWithPort(port);
        }
    }

    private static boolean isKnownSubmode(@Nullable String s) {
        return SUBMODE_KEYBOARD.equals(s)
                || SUBMODE_NUMPAD.equals(s)
                || SUBMODE_TOUCHPAD.equals(s)
                || SUBMODE_COMPOSE.equals(s)
                || SUBMODE_SETTINGS.equals(s);
    }

    @Nullable
    private MainActivity mainActivity() {
        if (getActivity() instanceof MainActivity) {
            return (MainActivity) getActivity();
        }
        return null;
    }
}
