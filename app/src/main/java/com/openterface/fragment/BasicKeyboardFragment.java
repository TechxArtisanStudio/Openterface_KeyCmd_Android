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
     * Applies {@link R.dimen#basic_keyboard_content_inset} (start), {@link
     * R.dimen#basic_keyboard_content_inset_end} (end), and {@link R.dimen#basic_keyboard_content_inset_bottom}
     * plus {@link WindowInsetsCompat.Type#navigationBars()} <strong>bottom</strong> only. Landscape
     * side insets (nav + cutout) are handled once on {@link KeyboardMouseFragment}'s root so we do not
     * double-stack horizontal system insets here. Dimens are read inside the listener so rotation stays
     * correct with {@code configChanges}. Re-install on {@link #onConfigurationChanged}.
     */
    private void installKeyboardContentInsets(@NonNull BasicPhysicalKeyboardView keyboard) {
        ViewCompat.setOnApplyWindowInsetsListener(
                keyboard,
                (v, windowInsets) -> {
                    Insets bars =
                            windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
                    int baseStart =
                            getResources().getDimensionPixelSize(R.dimen.basic_keyboard_content_inset);
                    int baseEnd =
                            getResources()
                                    .getDimensionPixelSize(R.dimen.basic_keyboard_content_inset_end);
                    int baseBottom =
                            getResources()
                                    .getDimensionPixelSize(R.dimen.basic_keyboard_content_inset_bottom);
                    ViewCompat.setPaddingRelative(
                            v,
                            baseStart,
                            v.getPaddingTop(),
                            baseEnd,
                            baseBottom + bars.bottom);
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
        if (physicalKeyboardView != null) {
            installKeyboardContentInsets(physicalKeyboardView);
        }
        bindKeyboard();
    }

    public void bindKeyboard() {
        MainActivity ma = mainActivity();
        if (physicalKeyboardView != null) {
            Fragment p = getParentFragment();
            if (p instanceof KeyboardMouseFragment) {
                physicalKeyboardView.setHoldLockController(
                        ((KeyboardMouseFragment) p).getHoldLockController());
            } else {
                physicalKeyboardView.setHoldLockController(null);
            }
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
