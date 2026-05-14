package com.openterface.keymod.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Guided macOS-only Unicode Hex path audit: same HID path as production {@link
 * HidTextKeystrokeSender#sendUnicodeCharMacOS(int, ConnectionManager)}.
 */
public final class MacUnicodeHexAuditFlow {

    private static final String TAG = "MacUnicodeHexAudit";

    private final MainActivity activity;
    private final ConnectionManager cm;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final List<String> failures = new ArrayList<>();
    private int[] presetCodePoints = MacUnicodeHexAuditPresets.copyPresetB();
    private String presetLabel = "";
    private int roundIndex;
    private boolean stoppedEarly;
    @Nullable private AlertDialog sendingOverlay;

    public static void start(@NonNull MainActivity activity, @Nullable ConnectionManager cm) {
        if (cm == null || !"macos".equalsIgnoreCase(activity.getTargetOs())) {
            return;
        }
        new MacUnicodeHexAuditFlow(activity, cm).beginPrereq();
    }

    private MacUnicodeHexAuditFlow(MainActivity activity, ConnectionManager cm) {
        this.activity = activity;
        this.cm = cm;
    }

    private void beginPrereq() {
        activity.runOnUiThread(
                () -> {
                    if (activity.isFinishing()) {
                        shutdownExecutorQuiet();
                        return;
                    }
                    new MaterialAlertDialogBuilder(activity)
                            .setTitle(R.string.unicode_hex_audit_prereq_title)
                            .setMessage(R.string.unicode_hex_audit_prereq_message)
                            .setNegativeButton(
                                    R.string.unicode_hex_audit_cancel_audit,
                                    (d, w) -> shutdownExecutorQuiet())
                            .setOnCancelListener(d -> shutdownExecutorQuiet())
                            .setPositiveButton(
                                    R.string.unicode_hex_audit_prereq_ready,
                                    (d, w) -> {
                                        d.dismiss();
                                        postUi(this::showPresetChooser);
                                    })
                            .show();
                });
    }

    private void showPresetChooser() {
        if (activity.isFinishing()) {
            shutdownExecutorQuiet();
            return;
        }
        String[] items =
                new String[] {
                    activity.getString(R.string.unicode_hex_audit_preset_b_label),
                    activity.getString(R.string.unicode_hex_audit_preset_a_label),
                };
        final int[] checked = {0};
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.unicode_hex_audit_preset_title)
                .setSingleChoiceItems(items, 0, (dialog, which) -> checked[0] = which)
                .setNegativeButton(
                        R.string.unicode_hex_audit_cancel_audit,
                        (d, w) -> shutdownExecutorQuiet())
                .setOnCancelListener(d -> shutdownExecutorQuiet())
                .setPositiveButton(
                        R.string.unicode_hex_audit_preset_start,
                        (d, w) -> {
                            d.dismiss();
                            presetLabel = items[checked[0]];
                            presetCodePoints =
                                    checked[0] == 0
                                            ? MacUnicodeHexAuditPresets.copyPresetB()
                                            : MacUnicodeHexAuditPresets.copyPresetA();
                            roundIndex = 0;
                            stoppedEarly = false;
                            failures.clear();
                            postUi(this::showSendRoundDialog);
                        })
                .show();
    }

    private void showSendRoundDialog() {
        if (cancelled.get() || activity.isFinishing()) {
            return;
        }
        if (roundIndex >= presetCodePoints.length) {
            showSummary();
            return;
        }
        int cp = presetCodePoints[roundIndex];
        String glyph = new String(Character.toChars(cp));
        String hex = MacUnicodeHexAuditPresets.hexForCodePoint(cp);
        String uPlus = String.format(Locale.ROOT, "U+%04X", cp);
        int r = roundIndex + 1;
        int n = presetCodePoints.length;
        String body =
                activity.getString(R.string.unicode_hex_audit_round_message, r, n, glyph, hex, uPlus);

        new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.unicode_hex_audit_round_title, r, n))
                .setMessage(body)
                .setNegativeButton(
                        R.string.unicode_hex_audit_stop,
                        (d, w) -> {
                            stoppedEarly = true;
                            cancelled.set(true);
                            executor.shutdownNow();
                            d.dismiss();
                            postUi(this::showSummary);
                        })
                .setOnCancelListener(
                        d -> {
                            if (!cancelled.get()) {
                                stoppedEarly = true;
                                cancelled.set(true);
                                executor.shutdownNow();
                                postUi(this::showSummary);
                            }
                        })
                .setPositiveButton(
                        R.string.unicode_hex_audit_send,
                        (d, w) -> {
                            d.dismiss();
                            if (!cm.isConnected()) {
                                Toast.makeText(
                                                activity,
                                                R.string.unicode_hex_audit_not_connected,
                                                Toast.LENGTH_LONG)
                                        .show();
                                postUi(this::showSendRoundDialog);
                                return;
                            }
                            startSendWorker(cp);
                        })
                .show();
    }

    private void startSendWorker(int codePoint) {
        executor.execute(
                () -> {
                    try {
                        if (cancelled.get()) {
                            return;
                        }
                        showSendingOverlay();
                        HidTextKeystrokeSender.sendUnicodeCharMacOS(codePoint, cm);
                        Thread.sleep(150);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        dismissSendingOverlay();
                        if (!cancelled.get()) {
                            activity.runOnUiThread(() -> showPostSendDialog(codePoint));
                        }
                    }
                });
    }

    private void showSendingOverlay() {
        activity.runOnUiThread(
                () -> {
                    if (activity.isFinishing()) {
                        return;
                    }
                    dismissSendingOverlay();
                    sendingOverlay =
                            new MaterialAlertDialogBuilder(activity)
                                    .setMessage(R.string.unicode_hex_audit_sending)
                                    .setCancelable(false)
                                    .create();
                    sendingOverlay.show();
                });
    }

    private void dismissSendingOverlay() {
        activity.runOnUiThread(
                () -> {
                    if (sendingOverlay != null) {
                        try {
                            if (sendingOverlay.isShowing()) {
                                sendingOverlay.dismiss();
                            }
                        } catch (RuntimeException ignored) {
                        }
                        sendingOverlay = null;
                    }
                });
    }

    private void showPostSendDialog(int codePoint) {
        if (activity.isFinishing() || cancelled.get()) {
            return;
        }
        String glyph = new String(Character.toChars(codePoint));
        String hex = MacUnicodeHexAuditPresets.hexForCodePoint(codePoint);
        String uPlus = String.format(Locale.ROOT, "U+%04X", codePoint);
        String msg =
                activity.getString(R.string.unicode_hex_audit_post_message, glyph, hex, uPlus);

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.unicode_hex_audit_post_title)
                .setMessage(msg)
                .setPositiveButton(
                        R.string.unicode_hex_audit_looks_correct,
                        (d, w) -> {
                            d.dismiss();
                            roundIndex++;
                            postUi(this::showSendRoundDialog);
                        })
                .setNegativeButton(
                        R.string.unicode_hex_audit_wrong,
                        (d, w) -> {
                            d.dismiss();
                            postUi(() -> wrongFollowUp(codePoint));
                        })
                .setNeutralButton(
                        R.string.unicode_hex_audit_stop,
                        (d, w) -> {
                            stoppedEarly = true;
                            cancelled.set(true);
                            executor.shutdownNow();
                            d.dismiss();
                            postUi(this::showSummary);
                        })
                .show();
    }

    private void wrongFollowUp(int codePoint) {
        if (activity.isFinishing() || cancelled.get()) {
            return;
        }
        Log.w(
                TAG,
                "wrong glyph preset="
                        + presetLabel
                        + " round="
                        + (roundIndex + 1)
                        + " cp=0x"
                        + Integer.toHexString(codePoint));
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.unicode_hex_audit_wrong_title)
                .setMessage(R.string.unicode_hex_audit_wrong_hint)
                .setPositiveButton(
                        R.string.unicode_hex_audit_retry,
                        (d, w) -> {
                            d.dismiss();
                            postUi(this::showSendRoundDialog);
                        })
                .setNegativeButton(
                        R.string.unicode_hex_audit_next_round,
                        (d, w) -> {
                            d.dismiss();
                            failures.add(formatFailureLine(codePoint));
                            roundIndex++;
                            postUi(this::showSendRoundDialog);
                        })
                .setNeutralButton(
                        R.string.unicode_hex_audit_stop,
                        (d, w) -> {
                            stoppedEarly = true;
                            cancelled.set(true);
                            executor.shutdownNow();
                            d.dismiss();
                            postUi(this::showSummary);
                        })
                .show();
    }

    @NonNull
    private String formatFailureLine(int codePoint) {
        String g = new String(Character.toChars(codePoint));
        String hex = MacUnicodeHexAuditPresets.hexForCodePoint(codePoint);
        return activity.getString(R.string.unicode_hex_audit_failed_line, roundIndex + 1, g, hex);
    }

    private void showSummary() {
        activity.runOnUiThread(
                () -> {
                    if (activity.isFinishing()) {
                        shutdownExecutorQuiet();
                        return;
                    }
                    boolean completedAll =
                            !stoppedEarly
                                    && failures.isEmpty()
                                    && roundIndex >= presetCodePoints.length;
                    StringBuilder body = new StringBuilder();
                    if (completedAll) {
                        body.append(activity.getString(R.string.unicode_hex_audit_summary_success));
                    } else if (failures.isEmpty() && stoppedEarly) {
                        body.append(activity.getString(R.string.unicode_hex_audit_summary_stopped));
                    } else {
                        body.append(activity.getString(R.string.unicode_hex_audit_summary_failed_intro));
                        body.append("\n\n");
                        for (String line : failures) {
                            body.append("• ").append(line).append('\n');
                        }
                    }
                    body.append("\n\n")
                            .append(activity.getString(R.string.unicode_hex_audit_summary_scope_note));

                    String report = presetLabel + "\n" + body;

                    MaterialAlertDialogBuilder b =
                            new MaterialAlertDialogBuilder(activity)
                                    .setTitle(R.string.unicode_hex_audit_summary_title)
                                    .setMessage(body.toString())
                                    .setPositiveButton(
                                            R.string.unicode_hex_audit_summary_close,
                                            (d, w) -> shutdownExecutorQuiet());
                    if (!completedAll) {
                        b.setNeutralButton(
                                R.string.unicode_hex_audit_copy_report,
                                (d, w) -> copyReport(report));
                    }
                    b.show();
                });
    }

    private void copyReport(@NonNull String report) {
        ClipboardManager cb =
                (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cb != null) {
            cb.setPrimaryClip(ClipData.newPlainText("unicode_hex_audit", report));
            Toast.makeText(activity, R.string.unicode_hex_audit_report_copied, Toast.LENGTH_SHORT)
                    .show();
        }
    }

    private void postUi(@NonNull Runnable r) {
        activity.getWindow().getDecorView().post(r);
    }

    private void shutdownExecutorQuiet() {
        executor.shutdown();
    }
}
