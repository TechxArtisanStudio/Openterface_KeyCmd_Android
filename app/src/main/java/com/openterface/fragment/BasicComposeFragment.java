package com.openterface.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.R;
import com.openterface.keymod.util.HidTextKeystrokeSender;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * KM Basic IME-style compose: buffered text send via {@link HidTextKeystrokeSender}.
 */
public class BasicComposeFragment extends Fragment {

    public static BasicComposeFragment instantiateWithPort(@Nullable UsbSerialPort p) {
        BasicComposeFragment f = new BasicComposeFragment();
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
        return inflater.inflate(R.layout.fragment_basic_compose, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.basic_compose_back).setOnClickListener(v -> {
            Fragment p = getParentFragment();
            if (p instanceof KeyboardMouseFragment) {
                ((KeyboardMouseFragment) p).requestSubmode(KeyboardMouseFragment.SUBMODE_KEYBOARD);
            }
        });
        EditText editor = view.findViewById(R.id.basic_compose_editor);
        Button send = view.findViewById(R.id.basic_compose_send);
        send.setOnClickListener(v -> {
            MainActivity ma = mainActivity();
            if (ma == null) {
                return;
            }
            ConnectionManager cm = ma.getConnectionManager();
            if (cm == null) {
                Toast.makeText(requireContext(), R.string.compose_no_connection, Toast.LENGTH_SHORT).show();
                return;
            }
            String text = editor.getText() != null ? editor.getText().toString() : "";
            if (text.isEmpty()) {
                Toast.makeText(requireContext(), R.string.compose_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            send.setEnabled(false);
            new Thread(() -> {
                try {
                    HidTextKeystrokeSender.Result r = HidTextKeystrokeSender.send(
                            text,
                            cm,
                            ma.getTargetOs(),
                            false,
                            new AtomicBoolean(false));
                    requireActivity().runOnUiThread(() -> {
                        send.setEnabled(true);
                        if (r == HidTextKeystrokeSender.Result.COMPLETED) {
                            Toast.makeText(
                                            requireContext(),
                                            getString(R.string.compose_sent, text.length()),
                                            Toast.LENGTH_SHORT)
                                    .show();
                            editor.setText("");
                        }
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    requireActivity().runOnUiThread(() -> send.setEnabled(true));
                } catch (Exception e) {
                    requireActivity().runOnUiThread(() -> {
                        send.setEnabled(true);
                        Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            }, "basic-compose-send").start();
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
