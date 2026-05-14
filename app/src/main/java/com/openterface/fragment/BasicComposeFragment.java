package com.openterface.fragment;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.R;
import com.openterface.keymod.ThemeManager;
import com.openterface.keymod.compose.SavedTextItem;
import com.openterface.keymod.compose.SavedTextRepository;
import com.openterface.keymod.util.ComposeSendPreviewDialog;
import com.openterface.keymod.util.ComposeSendWarningDialog;
import com.openterface.keymod.util.HidTextKeystrokeSender;
import com.openterface.keymod.util.ImeComposeSendGate;
import com.openterface.keymod.util.NonAsciiTextHighlighter;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * KM Basic IME-style compose: same send gating as Pro IME sub-compose ({@link ImeComposeSendGate}),
 * with Redo clear / Clear / Save to library / Saved texts / Send and in-flight cancel (Send becomes Stop).
 */
public class BasicComposeFragment extends Fragment implements ImeSavedTextFragment.Host {

    public static final String ARG_EMBEDDED_IN_KM_PRO = "embedded_in_km_pro";

    private static final String TAG_KM_PRO_HOST = "BasicComposeFragment";

    public static BasicComposeFragment instantiateWithPort(@Nullable UsbSerialPort p) {
        BasicComposeFragment f = new BasicComposeFragment();
        f.port = p;
        return f;
    }

    /**
     * Compose buffer embedded in KM Pro composite (portrait-locked); requests IME when shown.
     */
    @NonNull
    public static BasicComposeFragment instantiateForKmProEmbedded(@Nullable UsbSerialPort p) {
        BasicComposeFragment f = new BasicComposeFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_EMBEDDED_IN_KM_PRO, true);
        f.setArguments(args);
        f.port = p;
        return f;
    }

    public UsbSerialPort port;

    private EditText editor;
    @Nullable private MaterialButton savedTextsBtn;
    private MaterialButton saveLibraryBtn;
    private MaterialButton clearBtn;
    private MaterialButton redoBtn;
    private MaterialButton sendBtn;
    @Nullable private View actionsRow;
    @Nullable private View composeBrandLogo;
    @Nullable private String undoSnapshot;
    private final AtomicBoolean cancelSend = new AtomicBoolean(false);
    private volatile boolean sending;
    private boolean highlightNonAsciiChars;

    /** Match {@link android.Manifest} {@code windowSoftInputMode} for {@link MainActivity}. */
    private static final int ACTIVITY_SOFT_INPUT_MODE =
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

    private boolean isEmbeddedInKmPro() {
        Bundle a = getArguments();
        return a != null && a.getBoolean(ARG_EMBEDDED_IN_KM_PRO, false);
    }

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
        savedTextsBtn = view.findViewById(R.id.basic_compose_saved_texts);
        saveLibraryBtn = view.findViewById(R.id.basic_compose_save_library);
        clearBtn = view.findViewById(R.id.basic_compose_clear);
        redoBtn = view.findViewById(R.id.basic_compose_redo);
        sendBtn = view.findViewById(R.id.basic_compose_send);
        actionsRow = view.findViewById(R.id.basic_compose_actions);
        composeBrandLogo = view.findViewById(R.id.basic_compose_brand_logo);

        clearBtn.setOnClickListener(v -> onClearClicked());
        redoBtn.setOnClickListener(v -> onRedoClicked());
        sendBtn.setOnClickListener(v -> onSendClicked());
        if (savedTextsBtn != null) {
            savedTextsBtn.setOnClickListener(v -> onSavedTextsClicked());
        }
        saveLibraryBtn.setOnClickListener(v -> onSaveLibraryClicked());

        editor.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        refreshToolbarState();
                        refreshComposeBrandLogoVisibility();
                        refreshEditorNonAsciiHighlights(false);
                    }
                });
        refreshToolbarState();
        refreshComposeBrandLogoVisibility();
        setupBasicComposeImeInsets(view);
        refreshComposeLayoutState(view);
        view.post(() -> refreshComposeLayoutState(view));
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        View v = getView();
        if (v != null) {
            refreshComposeLayoutState(v);
        }
    }

    /**
     * Portrait: always show Undo / Clear / Send and let the editor fill the available compose area
     * so there is no large dead gap above the action row.
     *
     * <p>Landscape: while the IME is open (non-zero bottom IME inset), hide the action row and let
     * the editor use {@code MATCH_PARENT} so typing space is maximized. When the keyboard is
     * dismissed or minimized (IME inset back to 0), show the row again so Send / Clear / Undo stay
     * reachable without rotating to portrait.
     */
    private void refreshComposeLayoutState(@NonNull View root) {
        if (actionsRow == null || editor == null) {
            return;
        }
        boolean landscape =
                root.getResources().getConfiguration().orientation
                        == Configuration.ORIENTATION_LANDSCAPE;
        int imeBottom = 0;
        WindowInsetsCompat windowInsets = ViewCompat.getRootWindowInsets(root);
        if (windowInsets != null) {
            imeBottom = windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
        }
        boolean hideActionsForLandscapeIme = landscape && imeBottom > 0;

        actionsRow.setVisibility(hideActionsForLandscapeIme ? View.GONE : View.VISIBLE);

        ViewGroup.LayoutParams lp = editor.getLayoutParams();
        if (lp != null) {
            lp.height =
                    (!landscape || hideActionsForLandscapeIme)
                            ? ViewGroup.LayoutParams.MATCH_PARENT
                            : ViewGroup.LayoutParams.WRAP_CONTENT;
            editor.setLayoutParams(lp);
        }

        refreshComposeBrandLogoVisibility();
        root.post(root::requestLayout);
    }

    /**
     * Pad the compose root by {@link WindowInsetsCompat.Type#ime()} bottom so the editor and
     * action row stay above the soft keyboard, and by {@link WindowInsetsCompat.Type#navigationBars()}
     * (merged with {@link WindowInsetsCompat.Type#displayCutout()}) on left / right / bottom so
     * content is not clipped under the system nav bar in portrait (when the IME is hidden) nor
     * under the side nav strip in either landscape orientation. Top is left to
     * {@link KeyboardMouseFragment#applyKmBasicChromeTopInset} since the chrome strip above this
     * fragment owns the status-bar inset. With {@code targetSdk 35} the framework no longer
     * auto-pads under {@code setDecorFitsSystemWindows(true)}, so each fragment must apply its own
     * insets — same pattern as {@link BasicNumPadFragment#installNumpadContentInsets}. Bottom uses
     * {@link Math#max} of {@code ime} and {@code navigationBars} (rather than additive) so the
     * action row is not pushed above the open keyboard with a visible nav-bar-sized gap.
     */
    private void setupBasicComposeImeInsets(@NonNull View root) {
        final int baseStart = ViewCompat.getPaddingStart(root);
        final int baseTop = root.getPaddingTop();
        final int baseEnd = ViewCompat.getPaddingEnd(root);
        final int baseBottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(
                root,
                (v, windowInsets) -> {
                    Insets bars =
                            windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
                    Insets cut =
                            windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());
                    int imeBottom = windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
                    int leftInset = Math.max(bars.left, cut.left);
                    int rightInset = Math.max(bars.right, cut.right);
                    int bottomInset = Math.max(imeBottom, Math.max(bars.bottom, cut.bottom));
                    ViewCompat.setPaddingRelative(
                            v,
                            baseStart + leftInset,
                            baseTop,
                            baseEnd + rightInset,
                            baseBottom + bottomInset);
                    refreshComposeLayoutState(v);
                    return windowInsets;
                });
        root.post(() -> ViewCompat.requestApplyInsets(root));
        root.postDelayed(() -> ViewCompat.requestApplyInsets(root), 120);
    }

    @Override
    public void onResume() {
        super.onResume();
        Window w = requireActivity().getWindow();
        w.setSoftInputMode(ACTIVITY_SOFT_INPUT_MODE);
        View v = getView();
        if (v != null) {
            v.post(() -> ViewCompat.requestApplyInsets(v));
            v.postDelayed(() -> ViewCompat.requestApplyInsets(v), 120);
        }
        if (isEmbeddedInKmPro()) {
            requestEditorImeForKmProEmbedded();
        }
    }

    @Override
    public void onPause() {
        if (getActivity() != null) {
            getActivity().getWindow().setSoftInputMode(ACTIVITY_SOFT_INPUT_MODE);
        }
        super.onPause();
    }

    /** When hosted inside KM Pro Compose tab: focus editor and show soft keyboard. */
    public void requestEditorImeForKmProEmbedded() {
        if (!isEmbeddedInKmPro() || editor == null || !isAdded()) {
            return;
        }
        editor.requestFocus();
        InputMethodManager imm =
                (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) {
            return;
        }
        editor.post(() -> imm.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT));
        editor.postDelayed(() -> imm.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT), 220);
    }

    private void onSavedTextsClicked() {
        if (sending) {
            return;
        }
        MainActivity ma = mainActivity();
        if (ma == null) {
            return;
        }
        ma.showImeSavedTextOverlay(this);
    }

    private void onSaveLibraryClicked() {
        if (sending) {
            return;
        }
        SavedTextRepository repo = new SavedTextRepository(requireContext());
        SavedTextItem added = repo.addFromPlainText(readCurrentEditorText());
        if (added == null) {
            Toast.makeText(requireContext(), R.string.ime_saved_text_save_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(requireContext(), R.string.ime_saved_text_saved, Toast.LENGTH_SHORT).show();
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
        sendBufferAfterGate(ma, cm, text, true);
    }

    private void sendBufferAfterGate(
            @NonNull MainActivity ma,
            @Nullable ConnectionManager cm,
            @NonNull String text,
            boolean editorIsSourceBuffer) {
        ImeComposeSendGate.SendAssessment assessment = ImeComposeSendGate.assess(cm, text);
        if (assessment.hardBlockReasonResId != null) {
            Toast.makeText(requireContext(), assessment.hardBlockReasonResId, Toast.LENGTH_SHORT).show();
            return;
        }
        if (assessment.warningInfo != null) {
            showComposeSendWarningDialog(cm, assessment.warningInfo, text, editorIsSourceBuffer);
            return;
        }
        startSend(ma, cm, text, editorIsSourceBuffer);
    }

    private void showComposeSendWarningDialog(
            @Nullable ConnectionManager cm,
            @NonNull ImeComposeSendGate.WarningInfo warningInfo,
            @NonNull String pendingSendText,
            boolean editorIsSourceBuffer) {
        if (editor == null || sending) {
            return;
        }
        ComposeSendWarningDialog.show(
                requireContext(),
                warningInfo,
                () -> {
                    MainActivity ma = mainActivity();
                    if (ma == null || editor == null || sending) {
                        return;
                    }
                    String now =
                            editorIsSourceBuffer
                                    ? (editor.getText() != null ? editor.getText().toString() : "")
                                    : pendingSendText;
                    ImeComposeSendGate.SendAssessment reassess =
                            ImeComposeSendGate.assess(cm, now);
                    if (reassess.hardBlockReasonResId != null) {
                        Toast.makeText(requireContext(), reassess.hardBlockReasonResId, Toast.LENGTH_SHORT)
                                .show();
                        refreshToolbarState();
                        return;
                    }
                    startSend(ma, cm, now, editorIsSourceBuffer);
                },
                () -> {
                    highlightNonAsciiChars = true;
                    refreshEditorNonAsciiHighlights(true);
                    if (editor != null) {
                        editor.requestFocus();
                    }
                },
                () -> {
                    String preview =
                            editorIsSourceBuffer
                                    ? (editor != null && editor.getText() != null
                                            ? editor.getText().toString()
                                            : "")
                                    : pendingSendText;
                    if (preview.isEmpty()) {
                        return;
                    }
                    ComposeSendPreviewDialog.show(requireContext(), preview);
                });
    }

    private void startSend(
            @NonNull MainActivity ma,
            @Nullable ConnectionManager cm,
            @NonNull String text,
            boolean clearEditorAfterSuccess) {
        cancelSend.set(false);
        sending = true;
        if (editor != null) {
            editor.setEnabled(false);
        }
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
                                            highlightNonAsciiChars = false;
                                            refreshEditorNonAsciiHighlights(false);
                                            Toast.makeText(
                                                            requireContext(),
                                                            getString(R.string.compose_sent, sentLen),
                                                            Toast.LENGTH_SHORT)
                                                    .show();
                                            if (clearEditorAfterSuccess && editor != null) {
                                                editor.setText("");
                                            }
                                            refreshToolbarState();
                                        }
                                    });
                        },
                        "basic-compose-send")
                .start();
    }

    private void refreshEditorNonAsciiHighlights(boolean showNoneFoundToast) {
        if (editor == null || editor.getText() == null) {
            return;
        }
        Editable editable = editor.getText();
        if (!highlightNonAsciiChars) {
            NonAsciiTextHighlighter.clear(editable);
            return;
        }
        int color =
                MaterialColors.getColor(
                        editor,
                        com.google.android.material.R.attr.colorTertiaryContainer,
                        ThemeManager.getColorPrimaryContainer(editor.getContext()));
        int highlighted = NonAsciiTextHighlighter.apply(editable, color);
        if (highlighted == 0) {
            highlightNonAsciiChars = false;
            NonAsciiTextHighlighter.clear(editable);
            if (showNoneFoundToast) {
                Toast.makeText(
                                requireContext(),
                                R.string.compose_send_warning_no_non_ascii_found,
                                Toast.LENGTH_SHORT)
                        .show();
            }
        }
    }

    private void postSendFinished(Runnable r) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(r);
        }
    }

    private void refreshComposeBrandLogoVisibility() {
        if (composeBrandLogo == null || editor == null) {
            return;
        }
        Editable text = editor.getText();
        WindowInsetsCompat windowInsets = ViewCompat.getRootWindowInsets(editor);
        boolean imeVisible =
                windowInsets != null && windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom > 0;
        boolean emptyBuffer = text == null || text.length() == 0;
        // KM Pro embeds this fragment with the soft keyboard shown; do not tie visibility to IME or the
        // wordmark never appears (standalone Basic Compose still hides while IME is up).
        boolean imeAllowsLogo = isEmbeddedInKmPro() || !imeVisible;
        boolean showLogo = !sending && imeAllowsLogo && emptyBuffer;
        composeBrandLogo.setVisibility(showLogo ? View.VISIBLE : View.GONE);
    }

    private void refreshToolbarState() {
        if (sendBtn == null || clearBtn == null || redoBtn == null || saveLibraryBtn == null) {
            return;
        }
        MainActivity ma = mainActivity();
        ConnectionManager cm = ma != null ? ma.getConnectionManager() : null;
        String t = editor != null && editor.getText() != null ? editor.getText().toString() : "";
        ImeComposeSendGate.SendAssessment assessment = ImeComposeSendGate.assess(cm, t);
        boolean canSend = assessment.canSend();
        boolean showWarning = canSend && assessment.warningInfo != null;

        int primary =
                MaterialColors.getColor(
                        sendBtn,
                        com.google.android.material.R.attr.colorPrimary,
                        ThemeManager.getColorPrimary(sendBtn.getContext()));
        int outline =
                MaterialColors.getColor(
                        sendBtn,
                        com.google.android.material.R.attr.colorOutline,
                        ContextCompat.getColor(sendBtn.getContext(), R.color.divider));
        int onSurface =
                MaterialColors.getColor(
                        sendBtn,
                        com.google.android.material.R.attr.colorOnSurface,
                        ContextCompat.getColor(sendBtn.getContext(), R.color.text_primary));
        int warning =
                MaterialColors.getColor(
                        sendBtn,
                        com.google.android.material.R.attr.colorTertiary,
                        ThemeManager.getColorPrimary(sendBtn.getContext()));
        ColorStateList primaryStroke = ColorStateList.valueOf(primary);
        ColorStateList outlineStroke = ColorStateList.valueOf(outline);
        ColorStateList warningStroke = ColorStateList.valueOf(warning);
        ColorStateList primaryIcon = ColorStateList.valueOf(primary);
        ColorStateList warningIcon = ColorStateList.valueOf(warning);
        ColorStateList mutedIcon = ColorStateList.valueOf(onSurface);

        if (editor != null) {
            // Frame border uses state_activated in basic_compose_editor_background: show shape theme
            // (colorPrimary stroke) whenever the buffer has content, independent of send gating.
            editor.setActivated(!t.isEmpty());
        }

        if (savedTextsBtn != null) {
            savedTextsBtn.setEnabled(!sending);
            savedTextsBtn.setAlpha(sending ? 0.45f : 1f);
        }
        saveLibraryBtn.setEnabled(!sending);
        saveLibraryBtn.setAlpha(sending ? 0.45f : 1f);

        if (sending) {
            sendBtn.setText("");
            sendBtn.setIconResource(R.drawable.ic_compose_stop_24);
            sendBtn.setContentDescription(getString(R.string.compose_stop));
            sendBtn.setEnabled(true);
            sendBtn.setAlpha(1f);
            sendBtn.setStrokeColor(primaryStroke);
            sendBtn.setIconTint(primaryIcon);
            clearBtn.setEnabled(false);
            clearBtn.setAlpha(0.45f);
            redoBtn.setEnabled(false);
            redoBtn.setAlpha(0.45f);
        } else {
            sendBtn.setText("");
            sendBtn.setIconResource(R.drawable.ic_compose_send_24);
            sendBtn.setContentDescription(getString(R.string.compose_send));
            sendBtn.setEnabled(true);
            sendBtn.setAlpha(canSend ? 1f : 0.45f);
            boolean canClear = !t.isEmpty();
            clearBtn.setEnabled(canClear);
            clearBtn.setAlpha(canClear ? 1f : 0.45f);
            boolean canRedo = undoSnapshot != null && !undoSnapshot.isEmpty();
            redoBtn.setEnabled(canRedo);
            redoBtn.setAlpha(canRedo ? 1f : 0.45f);

            if (!canSend) {
                sendBtn.setStrokeColor(outlineStroke);
                sendBtn.setIconTint(mutedIcon);
            } else if (showWarning) {
                sendBtn.setStrokeColor(warningStroke);
                sendBtn.setIconTint(warningIcon);
            } else {
                sendBtn.setStrokeColor(primaryStroke);
                sendBtn.setIconTint(primaryIcon);
            }
        }
        refreshComposeBrandLogoVisibility();
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

    // --- ImeSavedTextFragment.Host ---

    @NonNull
    @Override
    public String readCurrentEditorText() {
        if (editor == null || editor.getText() == null) {
            return "";
        }
        return editor.getText().toString();
    }

    @Override
    public void onLoadIntoEditor(@NonNull String content) {
        if (editor == null || sending) {
            return;
        }
        editor.setText(content);
        editor.setSelection(content.length());
        refreshToolbarState();
    }

    @Override
    public void onSendSavedText(@NonNull String content) {
        MainActivity ma = mainActivity();
        if (ma == null || sending) {
            return;
        }
        ConnectionManager cm = ma.getConnectionManager();
        sendBufferAfterGate(ma, cm, content, false);
    }
}
