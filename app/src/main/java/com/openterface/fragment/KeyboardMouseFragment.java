package com.openterface.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.openterface.keymod.R;
import com.hoho.android.usbserial.driver.UsbSerialPort;

/**
 * Standard &quot;Keyboard &amp; Mouse&quot; mode: KM Basic full-screen sub-modes (keyboard, numpad,
 * touchpad, compose). Pro composite experience lives in {@link CompositeFragment}.
 */
public final class KeyboardMouseFragment extends Fragment {

    public static final String EXTRA_INITIAL_SUBMODE = "kb_mouse_initial_submode";

    private static final String ARG_INITIAL_SUBMODE = "initial_submode";
    private static final String STATE_SUBMODE = "state_submode";

    public static final String SUBMODE_KEYBOARD = "keyboard";
    public static final String SUBMODE_NUMPAD = "numpad";
    public static final String SUBMODE_TOUCHPAD = "touchpad";
    public static final String SUBMODE_COMPOSE = "compose";

    public UsbSerialPort port;

    private String currentSubmode = SUBMODE_KEYBOARD;

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
        showSubmode(currentSubmode);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_SUBMODE, currentSubmode);
    }

    /** Called from {@link com.openterface.keymod.MainActivity} when the serial port changes. */
    public void onPortChanged(UsbSerialPort newPort) {
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
    }

    public void requestSubmode(@NonNull String submode) {
        showSubmode(submode);
    }

    @NonNull
    public String getCurrentSubmodePublic() {
        return currentSubmode;
    }

    public void refreshBasicEmbeddedChrome() {
        Fragment child = getChildFragmentManager().findFragmentById(R.id.kb_mouse_host);
        if (child instanceof BasicKeyboardFragment) {
            ((BasicKeyboardFragment) child).refreshChrome();
        }
    }

    private void showSubmode(@NonNull String submode) {
        if (!isKnownSubmode(submode)) {
            submode = SUBMODE_KEYBOARD;
        }
        currentSubmode = submode;
        Fragment f = buildChildForSubmode(submode);
        FragmentTransaction tx = getChildFragmentManager().beginTransaction();
        tx.replace(R.id.kb_mouse_host, f);
        tx.commit();
        getChildFragmentManager().executePendingTransactions();
        Fragment child = getChildFragmentManager().findFragmentById(R.id.kb_mouse_host);
        if (child instanceof BasicKeyboardFragment) {
            ((BasicKeyboardFragment) child).refreshChrome();
        }
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
            default:
                return BasicKeyboardFragment.instantiateWithPort(port);
        }
    }

    private static boolean isKnownSubmode(@Nullable String s) {
        return SUBMODE_KEYBOARD.equals(s)
                || SUBMODE_NUMPAD.equals(s)
                || SUBMODE_TOUCHPAD.equals(s)
                || SUBMODE_COMPOSE.equals(s);
    }
}
