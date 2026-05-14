package com.openterface.fragment;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.SystemClock;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.CustomKeyboardView;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.R;
import com.openterface.keymod.ThemeManager;
import com.openterface.keymod.basic.KmBasicHoldLockController;
import com.openterface.keymod.compose.SavedTextItem;
import com.openterface.keymod.compose.SavedTextRepository;
import com.openterface.keymod.prefs.KmProComposeDraftRetentionPrefs;
import com.openterface.keymod.prefs.KmProEmbeddedComposeDraftHolder;
import com.openterface.keymod.util.ComposeSendPreviewDialog;
import com.openterface.keymod.util.ComposeSendWarningDialog;
import com.openterface.keymod.util.HidTextKeystrokeSender;
import com.openterface.keymod.util.MacUnicodeHexAuditFlow;
import com.openterface.keymod.util.ImeComposeSendGate;
import com.openterface.keymod.util.NonAsciiTextHighlighter;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * KM Basic IME-style compose: same send gating as Pro IME sub-compose ({@link ImeComposeSendGate}),
 * with a combined Clear / undo-clear action, Save to library / Saved texts / Send and in-flight cancel
 * (Send becomes Stop).
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
    private MaterialButton sendBtn;
    @Nullable private View actionsRow;
    @Nullable private View sendProgressWrap;
    @Nullable private LinearProgressIndicator sendProgressBar;
    @Nullable private android.widget.TextView sendProgressLabel;
    @Nullable private View composeBrandLogo;
    /** KM Pro Compose: same top strip as full keyboard; null when not embedded. */
    @Nullable private CustomKeyboardView kmProShortcutStrip;
    @Nullable private View kmProShortcutStripWrap;
    @Nullable private MainActivity.OnTargetOsChangeListener kmProStripOsListener;
    @Nullable private String undoSnapshot;
    /**
     * True after a programmatic clear while {@link #undoSnapshot} holds the pre-clear buffer; any
     * user edit then clears the snapshot so the action returns to Clear-only.
     */
    private boolean undoClearEligible;
    /** Skips user-edit handling in the editor {@link TextWatcher} for programmatic {@link EditText#setText}. */
    private boolean programmaticEditorChange;
    private final AtomicBoolean cancelSend = new AtomicBoolean(false);
    private volatile boolean sending;
    private boolean highlightNonAsciiChars;

    /** Show determinate send progress only for longer sends (avoids flicker on short buffers). */
    private static final int COMPOSE_SEND_PROGRESS_MIN_UNITS = 56;

    private static final long COMPOSE_SEND_PROGRESS_UI_MIN_INTERVAL_MS = 67L;

    /** Last N units: post every update so the bar does not skip ahead then jump to full. */
    private static final int COMPOSE_SEND_PROGRESS_TAIL_UNTHROTTLE = 40;

    private volatile boolean sendProgressUiActive;
    private int lastComposeSendRemainingForA11y = -1;
    private final Object sendProgressPostLock = new Object();
    private long sendProgressLastPostMs;

    /** Match {@link android.Manifest} {@code windowSoftInputMode} for {@link MainActivity}. */
    private static final int ACTIVITY_SOFT_INPUT_MODE =
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

    private boolean isEmbeddedInKmPro() {
        Bundle a = getArguments();
        return a != null && a.getBoolean(ARG_EMBEDDED_IN_KM_PRO, false);
    }

    private void saveKmProEmbeddedDraftIfNeeded() {
        if (!isEmbeddedInKmPro() || getContext() == null) {
            return;
        }
        if (!KmProComposeDraftRetentionPrefs.read(requireContext())) {
            return;
        }
        if (sending) {
            return;
        }
        if (editor == null) {
            return;
        }
        String text = editor.getText() != null ? editor.getText().toString() : "";
        KmProEmbeddedComposeDraftHolder.saveFromEmbeddedEditor(text, undoSnapshot, highlightNonAsciiChars);
    }

    private void restoreKmProEmbeddedDraftIfNeeded() {
        if (!isEmbeddedInKmPro() || getContext() == null || editor == null) {
            return;
        }
        if (!KmProComposeDraftRetentionPrefs.read(requireContext())) {
            return;
        }
        KmProEmbeddedComposeDraftHolder.Snapshot d = KmProEmbeddedComposeDraftHolder.consumeDraft();
        if (d == null) {
            return;
        }
        String et = d.editorText != null ? d.editorText : "";
        runWithProgrammaticEditorChange(() -> editor.setText(et));
        if (et.isEmpty()) {
            undoSnapshot = d.undoSnapshot;
            undoClearEligible = undoSnapshot != null && !undoSnapshot.isEmpty();
        } else {
            undoSnapshot = null;
            undoClearEligible = false;
        }
        highlightNonAsciiChars = d.highlightNonAscii;
        refreshToolbarState();
        refreshComposeBrandLogoVisibility();
        refreshEditorNonAsciiHighlights(false);
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
        sendBtn = view.findViewById(R.id.basic_compose_send);
        actionsRow = view.findViewById(R.id.basic_compose_actions);
        sendProgressWrap = view.findViewById(R.id.basic_compose_send_progress_wrap);
        sendProgressBar = view.findViewById(R.id.basic_compose_send_progress);
        sendProgressLabel = view.findViewById(R.id.basic_compose_send_progress_label);
        composeBrandLogo = view.findViewById(R.id.basic_compose_brand_logo);

        clearBtn.setOnClickListener(v -> onClearOrUndoClearClicked());
        sendBtn.setOnClickListener(v -> onSendClicked());
        if (savedTextsBtn != null) {
            savedTextsBtn.setOnClickListener(v -> onSavedTextsClicked());
        }
        saveLibraryBtn.setOnClickListener(v -> onSaveLibraryClicked());

        prepareComposeSendProgressIndicator();

        editor.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        if (!programmaticEditorChange
                                && undoClearEligible
                                && s != null
                                && s.length() > 0) {
                            undoClearEligible = false;
                            undoSnapshot = null;
                        }
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

        setupKmProEmbeddedShortcutStripIfNeeded(view);

        restoreKmProEmbeddedDraftIfNeeded();
    }

    @Override
    public void onDestroyView() {
        tearDownKmProEmbeddedShortcutStripIfNeeded();
        hideSendProgressUi();
        sendProgressWrap = null;
        sendProgressBar = null;
        sendProgressLabel = null;
        super.onDestroyView();
    }

    private void prepareComposeSendProgressIndicator() {
        if (sendProgressBar != null) {
            sendProgressBar.setIndeterminate(false);
            sendProgressBar.setMax(1000);
        }
    }

    /**
     * Posts compose send progress UI work only while the fragment view is attached, so HID thread
     * callbacks cannot resurrect UI after teardown or completion.
     */
    private void postComposeProgressUi(@NonNull Runnable r) {
        if (getActivity() == null || !isAdded() || getView() == null) {
            return;
        }
        getActivity().runOnUiThread(r);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        View v = getView();
        if (v != null) {
            refreshComposeLayoutState(v);
        }
        prepareComposeSendProgressIndicator();
    }

    /**
     * Portrait: always show Clear / undo-clear / Send and let the editor fill the available compose area
     * so there is no large dead gap above the action row.
     *
     * <p>Landscape: while the IME is open (non-zero bottom IME inset), hide the action row and let
     * the editor use {@code MATCH_PARENT} so typing space is maximized. When the keyboard is
     * dismissed or minimized (IME inset back to 0), show the row again so Send / Clear / undo-clear stay
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
        saveKmProEmbeddedDraftIfNeeded();
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

    private void runWithProgrammaticEditorChange(@NonNull Runnable r) {
        programmaticEditorChange = true;
        try {
            r.run();
        } finally {
            programmaticEditorChange = false;
        }
    }

    private void onClearOrUndoClearClicked() {
        if (sending || editor == null) {
            return;
        }
        if (undoClearEligible && undoSnapshot != null && !undoSnapshot.isEmpty()) {
            performUndoClear();
            return;
        }
        Editable cur = editor.getText();
        if (cur == null || cur.length() == 0) {
            return;
        }
        undoSnapshot = cur.toString();
        undoClearEligible = true;
        runWithProgrammaticEditorChange(() -> editor.setText(""));
        refreshToolbarState();
    }

    private void performUndoClear() {
        if (sending || editor == null || undoSnapshot == null) {
            return;
        }
        final String snap = undoSnapshot;
        runWithProgrammaticEditorChange(
                () -> {
                    editor.setText(snap);
                    int len = editor.getText() != null ? editor.getText().length() : 0;
                    editor.setSelection(len);
                });
        undoSnapshot = null;
        undoClearEligible = false;
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
            showComposeSendWarningDialog(ma, cm, assessment.warningInfo, text, editorIsSourceBuffer);
            return;
        }
        startSend(ma, cm, text, editorIsSourceBuffer, false);
    }

    private void showComposeSendWarningDialog(
            @NonNull MainActivity ma,
            @Nullable ConnectionManager cm,
            @NonNull ImeComposeSendGate.WarningInfo warningInfo,
            @NonNull String pendingSendText,
            boolean editorIsSourceBuffer) {
        if (editor == null || sending) {
            return;
        }
        final Runnable sendWithUnicodeHostEntry =
                isEmbeddedInKmPro() && warningInfo.hasNonAscii
                        ? () -> {
                            MainActivity maNow = mainActivity();
                            if (maNow == null || editor == null || sending) {
                                return;
                            }
                            String now =
                                    editorIsSourceBuffer
                                            ? (editor.getText() != null ? editor.getText().toString() : "")
                                            : pendingSendText;
                            ImeComposeSendGate.SendAssessment reassess =
                                    ImeComposeSendGate.assess(cm, now);
                            if (reassess.hardBlockReasonResId != null) {
                                Toast.makeText(
                                                requireContext(),
                                                reassess.hardBlockReasonResId,
                                                Toast.LENGTH_SHORT)
                                        .show();
                                refreshToolbarState();
                                return;
                            }
                            showUnicodeHostSendConfirmDialog(maNow, cm, now, editorIsSourceBuffer);
                        }
                        : null;
        ComposeSendWarningDialog.show(
                requireContext(),
                warningInfo,
                pendingSendText,
                () -> {
                    MainActivity ma2 = mainActivity();
                    if (ma2 == null || editor == null || sending) {
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
                    startSend(ma2, cm, now, editorIsSourceBuffer, false);
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
                },
                ma.getTargetOs(),
                sendWithUnicodeHostEntry,
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
                    ComposeSendPreviewDialog.showUnicodeHostPlan(
                            requireContext(), preview, ma.getTargetOs());
                },
                "macos".equalsIgnoreCase(ma.getTargetOs())
                        ? () -> MacUnicodeHexAuditFlow.start(ma, ma.getConnectionManager())
                        : null);
    }

    private void showUnicodeHostSendConfirmDialog(
            @NonNull MainActivity ma,
            @Nullable ConnectionManager cm,
            @NonNull String text,
            boolean clearEditorAfterSuccess) {
        if (!isAdded()) {
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.compose_unicode_confirm_title)
                .setMessage(R.string.compose_unicode_confirm_message)
                .setIcon(R.drawable.ic_experiment_24px)
                .setNegativeButton(R.string.compose_unicode_confirm_cancel, null)
                .setPositiveButton(
                        R.string.compose_unicode_confirm_continue,
                        (dialog, which) ->
                                startSend(ma, cm, text, clearEditorAfterSuccess, true))
                .show();
    }

    private void startSend(
            @NonNull MainActivity ma,
            @Nullable ConnectionManager cm,
            @NonNull String text,
            boolean clearEditorAfterSuccess,
            boolean allowUnicode) {
        cancelSend.set(false);
        sending = true;

        final String targetOs = ma.getTargetOs();
        final int sentLen = text.length();
        final int totalUnits = HidTextKeystrokeSender.countSendUnits(text, allowUnicode, targetOs);
        final boolean showSendProgress = totalUnits >= COMPOSE_SEND_PROGRESS_MIN_UNITS;
        sendProgressUiActive = showSendProgress;
        lastComposeSendRemainingForA11y = showSendProgress ? totalUnits : -1;
        sendProgressLastPostMs = 0L;

        if (editor != null) {
            editor.setEnabled(false);
        }
        refreshToolbarState();

        if (showSendProgress) {
            postComposeProgressUi(
                    () -> {
                        if (!isAdded() || getView() == null || !sending || !sendProgressUiActive) {
                            return;
                        }
                        if (sendProgressWrap != null) {
                            sendProgressWrap.setVisibility(View.VISIBLE);
                        }
                        if (sendProgressBar != null) {
                            sendProgressBar.setIndeterminate(false);
                            sendProgressBar.setProgress(0, false);
                        }
                        if (sendProgressLabel != null) {
                            sendProgressLabel.setText(
                                    getString(R.string.compose_send_remaining, totalUnits));
                        }
                        lastComposeSendRemainingForA11y = totalUnits;
                        if (sendBtn != null) {
                            sendBtn.setContentDescription(
                                    getString(R.string.compose_stop_a11y_with_remaining, totalUnits));
                        }
                    });
        }

        final HidTextKeystrokeSender.SendProgressListener progressListener =
                showSendProgress
                        ? (completed, total) -> {
                            if (getActivity() == null || !isAdded()) {
                                return;
                            }
                            postThrottledSendProgress(completed, total);
                        }
                        : null;

        new Thread(
                        () -> {
                            HidTextKeystrokeSender.Result result;
                            try {
                                result =
                                        HidTextKeystrokeSender.send(
                                                text, cm, targetOs, allowUnicode, cancelSend, progressListener);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                result = HidTextKeystrokeSender.Result.CANCELLED;
                            } catch (Exception e) {
                                postSendFinished(
                                        () -> {
                                            sending = false;
                                            if (editor != null) {
                                                editor.setEnabled(true);
                                            }
                                            refreshToolbarState();
                                            Toast.makeText(
                                                            requireContext(),
                                                            e.getMessage(),
                                                            Toast.LENGTH_SHORT)
                                                    .show();
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
                                            undoClearEligible = false;
                                            highlightNonAsciiChars = false;
                                            refreshEditorNonAsciiHighlights(false);
                                            Toast.makeText(
                                                            requireContext(),
                                                            getString(R.string.compose_sent, sentLen),
                                                            Toast.LENGTH_SHORT)
                                                    .show();
                                            if (clearEditorAfterSuccess
                                                    && editor != null
                                                    && !isEmbeddedInKmPro()) {
                                                editor.setText("");
                                            }
                                            refreshToolbarState();
                                        }
                                    });
                        },
                        "basic-compose-send")
                .start();
    }

    private void postThrottledSendProgress(int completed, int total) {
        if (total <= 0) {
            return;
        }
        final int totalClamped = total;
        final int done = Math.min(Math.max(0, completed), totalClamped);
        final int remaining = totalClamped - done;
        final boolean atEnd = done >= totalClamped;
        final boolean inTail = remaining <= COMPOSE_SEND_PROGRESS_TAIL_UNTHROTTLE;
        long now = SystemClock.uptimeMillis();
        synchronized (sendProgressPostLock) {
            if (!atEnd
                    && !inTail
                    && (now - sendProgressLastPostMs) < COMPOSE_SEND_PROGRESS_UI_MIN_INTERVAL_MS) {
                return;
            }
            sendProgressLastPostMs = now;
        }
        final int remainingLabel = Math.max(0, remaining);
        postComposeProgressUi(
                () -> {
                    if (!isAdded() || getView() == null || !sending || !sendProgressUiActive) {
                        return;
                    }
                    if (sendProgressWrap != null) {
                        sendProgressWrap.setVisibility(View.VISIBLE);
                    }
                    if (sendProgressBar != null) {
                        sendProgressBar.setIndeterminate(false);
                        int p;
                        if (atEnd) {
                            p = 1000;
                        } else {
                            // Round for smoother motion; stay below max until the true completion frame.
                            p = Math.min(999, Math.round(1000f * done / (float) totalClamped));
                        }
                        // Same animation mode throughout so the last segment does not snap after motion.
                        sendProgressBar.setProgress(p, true);
                    }
                    if (sendProgressLabel != null) {
                        sendProgressLabel.setText(
                                getString(R.string.compose_send_remaining, remainingLabel));
                    }
                    lastComposeSendRemainingForA11y = remainingLabel;
                    if (sendBtn != null) {
                        sendBtn.setContentDescription(
                                getString(
                                        R.string.compose_stop_a11y_with_remaining, remainingLabel));
                    }
                });
    }

    private void hideSendProgressUi() {
        sendProgressUiActive = false;
        lastComposeSendRemainingForA11y = -1;
        if (sendProgressWrap != null) {
            sendProgressWrap.setVisibility(View.GONE);
        }
        if (sendProgressBar != null) {
            sendProgressBar.setIndeterminate(false);
            sendProgressBar.setProgress(0, false);
        }
        if (sendProgressLabel != null) {
            sendProgressLabel.setText("");
        }
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
        if (isEmbeddedInKmPro()) {
            composeBrandLogo.setVisibility(View.GONE);
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
        if (sendBtn == null || clearBtn == null || saveLibraryBtn == null) {
            return;
        }
        if (!sending) {
            hideSendProgressUi();
        }
        MainActivity ma = mainActivity();
        ConnectionManager cm = ma != null ? ma.getConnectionManager() : null;
        String t = editor != null && editor.getText() != null ? editor.getText().toString() : "";
        ImeComposeSendGate.SendAssessment assessment = ImeComposeSendGate.assess(cm, t);
        boolean canSend = assessment.canSend();
        boolean showWarning = canSend && assessment.warningInfo != null;
        boolean showNonAsciiWarningInKmPro =
                showWarning
                        && isEmbeddedInKmPro()
                        && assessment.warningInfo != null
                        && assessment.warningInfo.hasNonAscii;
        boolean darkMode =
                (sendBtn.getResources().getConfiguration().uiMode
                                & Configuration.UI_MODE_NIGHT_MASK)
                        == Configuration.UI_MODE_NIGHT_YES;

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
        int white = ContextCompat.getColor(sendBtn.getContext(), android.R.color.white);
        ColorStateList whiteStroke = ColorStateList.valueOf(white);
        ColorStateList whiteIcon = ColorStateList.valueOf(white);

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
            if (sendProgressUiActive && lastComposeSendRemainingForA11y >= 0) {
                sendBtn.setContentDescription(
                        getString(
                                R.string.compose_stop_a11y_with_remaining,
                                lastComposeSendRemainingForA11y));
            } else {
                sendBtn.setContentDescription(getString(R.string.compose_stop));
            }
            sendBtn.setEnabled(true);
            sendBtn.setAlpha(1f);
            sendBtn.setStrokeColor(primaryStroke);
            sendBtn.setIconTint(primaryIcon);
            clearBtn.setEnabled(false);
            clearBtn.setAlpha(0.45f);
        } else {
            sendBtn.setText("");
            sendBtn.setIconResource(R.drawable.ic_compose_send_24);
            sendBtn.setContentDescription(getString(R.string.compose_send));
            sendBtn.setEnabled(true);
            sendBtn.setAlpha(canSend ? 1f : 0.45f);
            boolean showUndoClear =
                    undoClearEligible
                            && undoSnapshot != null
                            && !undoSnapshot.isEmpty()
                            && t.isEmpty();
            boolean canClearOrUndo = showUndoClear || !t.isEmpty();
            if (showUndoClear) {
                clearBtn.setIconResource(R.drawable.ic_compose_undo_24);
                clearBtn.setContentDescription(getString(R.string.compose_redo_clear));
            } else {
                clearBtn.setIconResource(R.drawable.ic_compose_clear_24);
                clearBtn.setContentDescription(getString(R.string.compose_clear));
            }
            clearBtn.setEnabled(canClearOrUndo);
            clearBtn.setAlpha(canClearOrUndo ? 1f : 0.45f);

            if (!canSend) {
                sendBtn.setStrokeColor(outlineStroke);
                sendBtn.setIconTint(mutedIcon);
            } else if (showNonAsciiWarningInKmPro) {
                // Keep this clearly actionable in KM Pro Compose when non-ASCII warning is present.
                if (darkMode) {
                    sendBtn.setStrokeColor(whiteStroke);
                    sendBtn.setIconTint(whiteIcon);
                } else {
                    // In light mode, match neighboring action buttons (neutral dark icon + outline).
                    sendBtn.setStrokeColor(outlineStroke);
                    sendBtn.setIconTint(mutedIcon);
                }
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
        if (kmProShortcutStrip != null) {
            kmProShortcutStrip.setPort(newPort);
        }
    }

    /**
     * KM Pro embedded Compose: apply the same USB port and swipe-up hold-lock controller as the
     * main {@link CustomKeyboardView} slot.
     */
    public void syncKmProEmbeddedShortcutStripFromCompositeHost(
            @Nullable UsbSerialPort hostPort, @Nullable KmBasicHoldLockController holdLockController) {
        if (!isEmbeddedInKmPro() || kmProShortcutStrip == null) {
            return;
        }
        kmProShortcutStrip.setPort(hostPort);
        kmProShortcutStrip.setHoldLockController(holdLockController);
    }

    @Nullable
    public CustomKeyboardView getKmProEmbeddedShortcutStripOrNull() {
        return kmProShortcutStrip;
    }

    private void setupKmProEmbeddedShortcutStripIfNeeded(@NonNull View root) {
        kmProShortcutStripWrap = root.findViewById(R.id.basic_compose_km_pro_shortcut_strip_wrap);
        kmProShortcutStrip = root.findViewById(R.id.basic_compose_km_pro_shortcut_strip);
        if (!isEmbeddedInKmPro()
                || kmProShortcutStripWrap == null
                || kmProShortcutStrip == null) {
            return;
        }
        kmProShortcutStripWrap.setVisibility(View.VISIBLE);
        kmProShortcutStrip.setShortcutsStripOnly(true);
        kmProShortcutStrip.setShowExtraPortraitKeys(false);
        kmProShortcutStrip.setPort(port);
        kmProShortcutStrip.reloadForCurrentOrientation();
        if (requireActivity() instanceof MainActivity) {
            MainActivity ma = (MainActivity) requireActivity();
            kmProStripOsListener =
                    os -> {
                        if (kmProShortcutStrip != null) {
                            kmProShortcutStrip.reloadForTargetOs();
                        }
                    };
            ma.addOsChangeListener(kmProStripOsListener);
            kmProShortcutStrip.setOnTopModeShortcutListener(ma::switchToLaunchMode);
        }
        Fragment host = getParentFragment();
        if (host instanceof CompositeFragment) {
            ((CompositeFragment) host).syncKmProComposeShortcutStripFromKeyboardHost();
        }
    }

    private void tearDownKmProEmbeddedShortcutStripIfNeeded() {
        MainActivity ma = mainActivity();
        if (kmProStripOsListener != null && ma != null) {
            ma.removeOsChangeListener(kmProStripOsListener);
        }
        kmProStripOsListener = null;
        if (kmProShortcutStrip != null) {
            kmProShortcutStrip.setOnTopModeShortcutListener(null);
            kmProShortcutStrip.setHoldLockController(null);
        }
        kmProShortcutStrip = null;
        kmProShortcutStripWrap = null;
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
        undoSnapshot = null;
        undoClearEligible = false;
        runWithProgrammaticEditorChange(
                () -> {
                    editor.setText(content);
                    editor.setSelection(content.length());
                });
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
