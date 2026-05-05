package com.openterface.fragment;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.openterface.keymod.MainActivity;
import com.openterface.keymod.R;
import com.openterface.keymod.basic.BasicPhysicalKeyboardView;
import com.hoho.android.usbserial.driver.UsbSerialPort;

/**
 * KM Basic full-screen keyboard body (physical layout only; chrome lives on {@link KeyboardMouseFragment}).
 */
public class BasicKeyboardFragment extends Fragment {

    public static BasicKeyboardFragment instantiateWithPort(@Nullable UsbSerialPort p) {
        BasicKeyboardFragment f = new BasicKeyboardFragment();
        f.port = p;
        return f;
    }

    public UsbSerialPort port;
    private BasicPhysicalKeyboardView physicalKeyboardView;

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
        installKeyboardContentInsets(physicalKeyboardView);
        bindKeyboard();
    }

    /**
     * Adds navigation bar insets on top of {@link R.dimen#basic_keyboard_content_inset} (start,
     * bottom) and {@link R.dimen#basic_keyboard_content_inset_end} (end). Landscape uses a smaller
     * end base so keys sit slightly closer to the system nav strip. Display cutout is not merged
     * into horizontal padding to avoid oversized side gutters.
     */
    private void installKeyboardContentInsets(@NonNull BasicPhysicalKeyboardView keyboard) {
        final int baseStart =
                getResources().getDimensionPixelSize(R.dimen.basic_keyboard_content_inset);
        final int baseEnd =
                getResources().getDimensionPixelSize(R.dimen.basic_keyboard_content_inset_end);
        ViewCompat.setOnApplyWindowInsetsListener(
                keyboard,
                (v, windowInsets) -> {
                    Insets bars =
                            windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
                    ViewCompat.setPaddingRelative(
                            v,
                            baseStart + bars.left,
                            v.getPaddingTop(),
                            baseEnd + bars.right,
                            baseStart + bars.bottom);
                    return windowInsets;
                });
        ViewCompat.requestApplyInsets(keyboard);
    }

    @Override
    public void onResume() {
        super.onResume();
        bindKeyboard();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        bindKeyboard();
    }

    public void bindKeyboard() {
        MainActivity ma = mainActivity();
        if (physicalKeyboardView != null) {
            physicalKeyboardView.bind(ma, port);
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
