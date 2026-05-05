package com.openterface.fragment;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.R;
import com.openterface.keymod.util.HidTextKeystrokeSender;
import com.openterface.keymod.util.ImeComposeSendGate;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * KM Basic IME-style compose: same send gating as Pro IME sub-compose ({@link ImeComposeSendGate}),
 * with Clear / Redo clear / Send and in-flight cancel (Send becomes Stop).
 */
public class BasicComposeFragment extends Fragment {

    public static BasicComposeFragment instantiateWithPort(@Nullable UsbSerialPort p) {
        BasicComposeFragment f = new BasicComposeFragment();
        f.port = p;
        return f;
    }

    public UsbSerialPort port;

    private EditText editor;
    private MaterialButton clearBtn;
    private MaterialButton redoBtn;
    private MaterialButton sendBtn;
    @Nullable
    private String undoSnapshot;
    private final AtomicBoolean cancelSend = new AtomicBoolean(false);
    private volatile boolean sending;

    /** Restore manifest default when leaving compose (other screens expect resize). */
    private static final int DEFAULT_ACTIVITY_SOFT_INPUT_MODE =
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

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

        editor = view.findViewById(R.id.basic_compose_editor);
        editor.setImeOptions(editor.getImeOptions() | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        clearBtn = view.findViewById(R.id.basic_compose_clear);
        redoBtn = view.findViewById(R.id.basic_compose_redo);
        sendBtn = view.findViewById(R.id.basic_compose_send);

        clearBtn.setOnClickListener(v -> onClearClicked());
        redoBtn.setOnClickListener(v -> onRedoClicked());
        sendBtn.setOnClickListener(v -> onSendClicked());

        editor.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        refreshToolbarState();
                    }
                });
        refreshToolbarState();
    }

    @Override
    public void onResume() {
        super.onResume();
        Window w = requireActivity().getWindow();
        // Landscape IME often uses extracted fullscreen UI that hides our editor; pan keeps the
        // focused field above a docked keyboard. IME_FLAG_NO_EXTRACT_UI asks IM not to use extract UI.
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
    }

    @Override
    public void onPause() {
        if (getActivity() != null) {
            getActivity().getWindow().setSoftInputMode(DEFAULT_ACTIVITY_SOFT_INPUT_MODE);
        }
        super.onPause();
    }

    private void onClearClicked() {
        if (sending || editor == null) {
            return;
        }
        Editable cur = editor.getText();
        if (cur != null && cur.length() > 0) {
            undoSnapshot = cur.toString();
        }
        editor.setText("");
        refreshToolbarState();
    }

    private void onRedoClicked() {
        if (sending || editor == null || undoSnapshot == null) {
            return;
        }
        editor.setText(undoSnapshot);
        undoSnapshot = null;
        refreshToolbarState();
    }

    private void onSendClicked() {
        MainActivity ma = mainActivity();
        if (ma == null || editor == null || sendBtn == null) {
            return;
        }
        if (sending) {
            cancelSend.set(true);
            return;
        }
        ConnectionManager cm = ma.getConnectionManager();
        String text = editor.getText() != null ? editor.getText().toString() : "";
        Integer blocked = ImeComposeSendGate.resolveSendBlockedReasonResId(cm, text);
        if (blocked != null) {
            int duration =
                    blocked == R.string.compose_ascii_warning ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT;
            Toast.makeText(requireContext(), blocked, duration).show();
            return;
        }

        cancelSend.set(false);
        sending = true;
        editor.setEnabled(false);
        refreshToolbarState();

        final String targetOs = ma.getTargetOs();
        final int sentLen = text.length();

        new Thread(
                        () -> {
                            HidTextKeystrokeSender.Result result;
                            try {
                                result = HidTextKeystrokeSender.send(text, cm, targetOs, false, cancelSend);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                result = HidTextKeystrokeSender.Result.CANCELLED;
                            } catch (Exception e) {
                                postSendFinished(() -> {
                                    sending = false;
                                    if (editor != null) {
                                        editor.setEnabled(true);
                                    }
                                    refreshToolbarState();
                                    Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                                return;
                            }
                            HidTextKeystrokeSender.Result finalResult = result;
                            postSendFinished(
                                    () -> {
                                        sending = false;
                                        if (editor != null) {
                                            editor.setEnabled(true);
                                        }
                                        refreshToolbarState();
                                        if (finalResult == HidTextKeystrokeSender.Result.CANCELLED) {
                                            Toast.makeText(
                                                            requireContext(),
                                                            R.string.compose_cancelled,
                                                            Toast.LENGTH_SHORT)
                                                    .show();
                                        } else {
                                            undoSnapshot = null;
                                            Toast.makeText(
                                                            requireContext(),
                                                            getString(R.string.compose_sent, sentLen),
                                                            Toast.LENGTH_SHORT)
                                                    .show();
                                            if (editor != null) {
                                                editor.setText("");
                                            }
                                            refreshToolbarState();
                                        }
                                    });
                        },
                        "basic-compose-send")
                .start();
    }

    private void postSendFinished(Runnable r) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(r);
        }
    }

    private void refreshToolbarState() {
        if (sendBtn == null || clearBtn == null || redoBtn == null) {
            return;
        }
        MainActivity ma = mainActivity();
        ConnectionManager cm = ma != null ? ma.getConnectionManager() : null;
        String t = editor != null && editor.getText() != null ? editor.getText().toString() : "";
        Integer blocked = ImeComposeSendGate.resolveSendBlockedReasonResId(cm, t);
        boolean canSend = blocked == null;

        if (sending) {
            sendBtn.setText(R.string.compose_stop);
            sendBtn.setEnabled(true);
            sendBtn.setAlpha(1f);
            clearBtn.setEnabled(false);
            clearBtn.setAlpha(0.45f);
            redoBtn.setEnabled(false);
            redoBtn.setAlpha(0.45f);
        } else {
            sendBtn.setText(R.string.compose_send);
            sendBtn.setEnabled(true);
            sendBtn.setAlpha(canSend ? 1f : 0.45f);
            boolean canClear = !t.isEmpty();
            clearBtn.setEnabled(canClear);
            clearBtn.setAlpha(canClear ? 1f : 0.45f);
            boolean canRedo = undoSnapshot != null && !undoSnapshot.isEmpty();
            redoBtn.setEnabled(canRedo);
            redoBtn.setAlpha(canRedo ? 1f : 0.45f);
        }
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
