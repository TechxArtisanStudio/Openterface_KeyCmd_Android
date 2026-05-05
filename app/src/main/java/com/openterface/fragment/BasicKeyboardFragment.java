package com.openterface.fragment;

import android.content.res.Configuration;
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
import androidx.fragment.app.Fragment;

import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.R;
import com.openterface.keymod.basic.BasicPhysicalKeyboardView;
import com.hoho.android.usbserial.driver.UsbSerialPort;

/**
 * KM Basic full-screen keyboard: row-1 chrome + physical layout (no {@code CustomKeyboardView} strip).
 */
public class BasicKeyboardFragment extends Fragment {

    public static BasicKeyboardFragment instantiateWithPort(@Nullable UsbSerialPort p) {
        BasicKeyboardFragment f = new BasicKeyboardFragment();
        f.port = p;
        return f;
    }

    public UsbSerialPort port;
    private BasicPhysicalKeyboardView physicalKeyboardView;
    private ImageButton targetOsButton;
    private ImageView connectionIcon;
    private TextView tabTouch;
    private TextView tabNum;
    private TextView tabIme;

    private final MainActivity.OnTargetOsChangeListener basicOsListener =
            os -> {
                bindKeyboard();
                refreshChrome();
            };

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_basic_keyboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp != null) {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        }
        physicalKeyboardView = view.findViewById(R.id.basic_physical_keyboard);
        ImageButton menu = view.findViewById(R.id.basic_km_menu_button);
        targetOsButton = view.findViewById(R.id.basic_km_target_os);
        connectionIcon = view.findViewById(R.id.basic_km_connection_icon);
        LinearLayout connectionWrap = view.findViewById(R.id.basic_km_connection);
        tabTouch = view.findViewById(R.id.basic_km_tab_touchpad);
        tabNum = view.findViewById(R.id.basic_km_tab_numpad);
        tabIme = view.findViewById(R.id.basic_km_tab_ime);

        menu.setOnClickListener(v -> {
            MainActivity ma = mainActivity();
            if (ma != null) {
                ma.openDrawerForBasic();
            }
        });
        targetOsButton.setOnClickListener(v -> {
            MainActivity ma = mainActivity();
            if (ma != null) {
                ma.showTargetOsPickerDialogFromBasic();
            }
        });
        connectionWrap.setOnClickListener(v -> {
            MainActivity ma = mainActivity();
            if (ma != null) {
                ma.showConnectionDialogFromBasic();
            }
        });

        tabTouch.setOnClickListener(v -> switchSub(KeyboardMouseFragment.SUBMODE_TOUCHPAD));
        tabNum.setOnClickListener(v -> switchSub(KeyboardMouseFragment.SUBMODE_NUMPAD));
        tabIme.setOnClickListener(v -> switchSub(KeyboardMouseFragment.SUBMODE_COMPOSE));

        refreshChrome();
        bindKeyboard();
    }

    private void switchSub(String mode) {
        Fragment p = getParentFragment();
        if (p instanceof KeyboardMouseFragment) {
            ((KeyboardMouseFragment) p).requestSubmode(mode);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        MainActivity ma = mainActivity();
        if (ma != null) {
            ma.addOsChangeListener(basicOsListener);
        }
        refreshChrome();
        bindKeyboard();
    }

    @Override
    public void onPause() {
        MainActivity ma = mainActivity();
        if (ma != null) {
            ma.removeOsChangeListener(basicOsListener);
        }
        super.onPause();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // MainActivity handles configChanges without recreate; re-measure/rebuild keyboard rows.
        refreshChrome();
        bindKeyboard();
    }

    public void bindKeyboard() {
        MainActivity ma = mainActivity();
        if (physicalKeyboardView != null) {
            physicalKeyboardView.bind(ma, port);
        }
    }

    public void refreshChrome() {
        MainActivity ma = mainActivity();
        if (ma == null) {
            return;
        }
        ma.applyBasicTargetOsIcon(targetOsButton);
        ConnectionManager cm = ma.getConnectionManager();
        if (cm != null && connectionIcon != null) {
            ma.applyBasicConnectionIcon(connectionIcon, cm.getCurrentConnectionType(), cm.getCurrentConnectionState());
        }
        updateTabSelection();
    }

    private void updateTabSelection() {
        Fragment p = getParentFragment();
        String mode = KeyboardMouseFragment.SUBMODE_KEYBOARD;
        if (p instanceof KeyboardMouseFragment) {
            mode = ((KeyboardMouseFragment) p).getCurrentSubmodePublic();
        }
        if (tabTouch != null) {
            tabTouch.setSelected(KeyboardMouseFragment.SUBMODE_TOUCHPAD.equals(mode));
        }
        if (tabNum != null) {
            tabNum.setSelected(KeyboardMouseFragment.SUBMODE_NUMPAD.equals(mode));
        }
        if (tabIme != null) {
            tabIme.setSelected(KeyboardMouseFragment.SUBMODE_COMPOSE.equals(mode));
        }
    }

    public void onHostPortChanged(@Nullable UsbSerialPort newPort) {
        port = newPort;
        if (physicalKeyboardView != null) {
            physicalKeyboardView.onHostPortChanged(newPort);
        }
    }

    @Nullable
    private MainActivity mainActivity() {
        if (requireActivity() instanceof MainActivity) {
            return (MainActivity) requireActivity();
        }
        return null;
    }
}
