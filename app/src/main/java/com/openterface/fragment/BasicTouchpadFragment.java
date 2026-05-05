package com.openterface.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.openterface.keymod.MainActivity;
import com.openterface.keymod.R;
import com.openterface.keymod.TouchPadView;
import com.openterface.keymod.hid.MouseRelHidTransport;
import com.hoho.android.usbserial.driver.UsbSerialPort;

/**
 * KM Basic touchpad: reuses {@link TouchPadView} gestures with shared relative-mouse HID transport.
 */
public class BasicTouchpadFragment extends Fragment {

    public static BasicTouchpadFragment instantiateWithPort(@Nullable UsbSerialPort p) {
        BasicTouchpadFragment f = new BasicTouchpadFragment();
        f.port = p;
        return f;
    }

    public UsbSerialPort port;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_basic_touchpad, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.basic_touchpad_back).setOnClickListener(v -> {
            Fragment p = getParentFragment();
            if (p instanceof KeyboardMouseFragment) {
                ((KeyboardMouseFragment) p).requestSubmode(KeyboardMouseFragment.SUBMODE_KEYBOARD);
            }
        });
        TouchPadView pad = view.findViewById(R.id.basic_touch_pad);
        pad.setOnTouchPadListener(new TouchPadView.OnTouchPadListener() {
            @Override
            public void onTouchMove(float startX, float startY, float lastX, float lastY) {
                MainActivity ma = mainActivity();
                if (ma == null) {
                    return;
                }
                if (lastX == 0 && lastY == 0) {
                    MouseRelHidTransport.sendScroll(
                            port,
                            ma.getBluetoothService(),
                            ma.isBluetoothServiceBound(),
                            (int) startX,
                            (int) startY);
                } else {
                    MouseRelHidTransport.sendRelMove(
                            port,
                            ma.getBluetoothService(),
                            ma.isBluetoothServiceBound(),
                            false,
                            startX,
                            startY,
                            lastX,
                            lastY);
                }
            }

            @Override
            public void onTouchClick() {
                MainActivity ma = mainActivity();
                if (ma == null) {
                    return;
                }
                MouseRelHidTransport.sendLeftClick(
                        port, ma.getBluetoothService(), ma.isBluetoothServiceBound());
            }

            @Override
            public void onTouchDoubleClick() {
                MainActivity ma = mainActivity();
                if (ma == null) {
                    return;
                }
                MouseRelHidTransport.sendDoubleClick(
                        port, ma.getBluetoothService(), ma.isBluetoothServiceBound());
            }

            @Override
            public void onTouchRightClick() {
                MainActivity ma = mainActivity();
                if (ma == null) {
                    return;
                }
                MouseRelHidTransport.sendRightClick(
                        port, ma.getBluetoothService(), ma.isBluetoothServiceBound());
            }

            @Override
            public void onTouchRelease() {
                MainActivity ma = mainActivity();
                if (ma == null) {
                    return;
                }
                MouseRelHidTransport.releaseAll(
                        port, ma.getBluetoothService(), ma.isBluetoothServiceBound());
            }
        });
    }

    @Nullable
    private MainActivity mainActivity() {
        if (requireActivity() instanceof MainActivity) {
            return (MainActivity) requireActivity();
        }
        return null;
    }

    public void onHostPortChanged(@Nullable UsbSerialPort newPort) {
        port = newPort;
    }
}
