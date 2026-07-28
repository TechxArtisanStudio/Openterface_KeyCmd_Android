package com.openterface.keymod.agent.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.openterface.keymod.R;
import com.openterface.keymod.agent.core.AgentPlan;

import java.util.ArrayList;
import java.util.List;

/**
 * BottomSheet for editing an Agent execution plan before running it.
 *
 * <p>Shows each step with editable title + command fields.
 * Supports deleting steps and adding new ones.
 * Returns the edited plan via {@link Listener#onPlanEdited(AgentPlan)}.</p>
 */
public class EditPlanSheet extends BottomSheetDialogFragment {

    private static final String TAG = "EditPlanSheet";

    // ── Listener ─────────────────────────────────────────────────────────

    public interface Listener {
        /** Called when user confirms the edited plan. */
        void onPlanEdited(@NonNull AgentPlan editedPlan);
    }

    @Nullable private Listener listener;
    @Nullable private AgentPlan originalPlan;

    // ── Views ────────────────────────────────────────────────────────────

    private RecyclerView stepList;
    private TextView emptyHint;
    private MaterialButton addStepButton;
    private MaterialButton confirmButton;
    private MaterialButton cancelButton;

    private StepAdapter adapter;
    private final List<EditableStep> editableSteps = new ArrayList<>();

    // ── Factory ──────────────────────────────────────────────────────────

    /** Create a sheet for the given plan. Call before {@link #show}. */
    @NonNull
    public static EditPlanSheet forPlan(@NonNull AgentPlan plan, @Nullable Listener listener) {
        EditPlanSheet sheet = new EditPlanSheet();
        sheet.originalPlan = plan;
        sheet.listener = listener;
        return sheet;
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            BottomSheetBehavior<?> behavior = ((BottomSheetDialog) d).getBehavior();
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
            behavior.setHideable(true);
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_edit_plan, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Load steps from original plan
        if (originalPlan != null) {
            for (AgentPlan.Step step : originalPlan.steps) {
                editableSteps.add(new EditableStep(step));
            }
        }

        bindViews(view);
        setupRecyclerView();
        setupButtons();
        updateEmptyState();
    }

    // ── View binding ─────────────────────────────────────────────────────

    private void bindViews(@NonNull View view) {
        stepList = view.findViewById(R.id.edit_plan_step_list);
        emptyHint = view.findViewById(R.id.edit_plan_empty_hint);
        addStepButton = view.findViewById(R.id.edit_plan_add_step);
        confirmButton = view.findViewById(R.id.edit_plan_confirm);
        cancelButton = view.findViewById(R.id.edit_plan_cancel);
    }

    private void setupRecyclerView() {
        adapter = new StepAdapter();
        stepList.setLayoutManager(new LinearLayoutManager(requireContext()));
        stepList.setAdapter(adapter);
    }

    private void setupButtons() {
        addStepButton.setOnClickListener(v -> addStep());

        confirmButton.setOnClickListener(v -> {
            hideKeyboard();
            confirmAndDismiss();
        });

        cancelButton.setOnClickListener(v -> dismiss());
    }

    // ─ Step management ──────────────────────────────────────────────────

    private void addStep() {
        String defaultTitle = "Step " + (editableSteps.size() + 1);
        EditableStep newStep = new EditableStep(
                editableSteps.size(), defaultTitle, "", "", "", "terminal");
        editableSteps.add(newStep);
        adapter.notifyItemInserted(editableSteps.size() - 1);
        updateEmptyState();

        // Scroll to the new step
        stepList.scrollToPosition(editableSteps.size() - 1);

        // Focus the title field of the new step
        stepList.postDelayed(() -> {
            View lastChild = stepList.getChildAt(adapter.getItemCount() - 1);
            if (lastChild != null) {
                EditText titleField = lastChild.findViewById(R.id.step_title_input);
                if (titleField != null) {
                    titleField.requestFocus();
                    titleField.setSelection(titleField.getText().length());
                    showKeyboard(titleField);
                }
            }
        }, 100);
    }

    private void removeStep(int position) {
        if (position < 0 || position >= editableSteps.size()) return;
        editableSteps.remove(position);
        adapter.notifyItemRemoved(position);
        // Re-index remaining steps
        for (int i = 0; i < editableSteps.size(); i++) {
            editableSteps.get(i).index = i;
        }
        adapter.notifyItemRangeChanged(0, editableSteps.size());
        updateEmptyState();
    }

    private void updateEmptyState() {
        boolean empty = editableSteps.isEmpty();
        emptyHint.setVisibility(empty ? View.VISIBLE : View.GONE);
        stepList.setVisibility(empty ? View.GONE : View.VISIBLE);
        confirmButton.setEnabled(!empty);
        confirmButton.setAlpha(empty ? 0.5f : 1f);
    }

    // ── Confirm ──────────────────────────────────────────────────────────

    private void confirmAndDismiss() {
        if (editableSteps.isEmpty()) return;

        List<AgentPlan.Step> newSteps = new ArrayList<>();
        for (int i = 0; i < editableSteps.size(); i++) {
            EditableStep es = editableSteps.get(i);
            String title = es.title.trim();
            if (title.isEmpty()) {
                title = "Step " + (i + 1);
            }
            newSteps.add(new AgentPlan.Step(
                    i, title, es.command, es.keys, es.macroId, es.kind));
        }

        String summary = originalPlan != null ? originalPlan.summary : "";
        AgentPlan edited = new AgentPlan(summary, newSteps);

        if (listener != null) {
            listener.onPlanEdited(edited);
        }
        dismiss();
    }

    // ── Keyboard ─────────────────────────────────────────────────────────

    private void showKeyboard(@NonNull View view) {
        InputMethodManager imm = (InputMethodManager) requireContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideKeyboard() {
        View current = getView();
        if (current != null) {
            InputMethodManager imm = (InputMethodManager) requireContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(current.getWindowToken(), 0);
        }
    }

    // ── Adapter ──────────────────────────────────────────────────────────

    class StepAdapter extends RecyclerView.Adapter<StepAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_edit_plan_step, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            EditableStep step = editableSteps.get(position);

            holder.indexLabel.setText(String.valueOf(position + 1));
            holder.titleInput.setText(step.title);
            holder.commandInput.setText(getPayloadText(step));

            // Update placeholder based on kind
            String hint;
            switch (step.kind) {
                case "hid":   hint = getString(R.string.edit_plan_hint_keys); break;
                case "macro": hint = getString(R.string.edit_plan_hint_macro); break;
                default:      hint = getString(R.string.edit_plan_hint_command); break;
            }
            holder.commandInput.setHint(hint);

            // Title change listener
            holder.titleInput.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) step.title = holder.titleInput.getText().toString();
            });

            // Command change listener
            holder.commandInput.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    String text = holder.commandInput.getText().toString();
                    setPayloadFromText(step, text);
                }
            });

            // Delete button
            holder.deleteButton.setOnClickListener(v -> removeStep(holder.getAdapterPosition()));
        }

        @Override
        public int getItemCount() {
            return editableSteps.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final TextView indexLabel;
            final EditText titleInput;
            final EditText commandInput;
            final ImageButton deleteButton;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                indexLabel = itemView.findViewById(R.id.step_index_label);
                titleInput = itemView.findViewById(R.id.step_title_input);
                commandInput = itemView.findViewById(R.id.step_command_input);
                deleteButton = itemView.findViewById(R.id.step_delete_button);
            }
        }
    }

    // ── EditableStep ────────────────────────────────────────────────────

    /** Mutable copy of a plan step for editing. */
    static class EditableStep {
        int index;
        String title;
        String command;
        String keys;
        String macroId;
        String kind;

        EditableStep(@NonNull AgentPlan.Step step) {
            this.index = step.index;
            this.title = step.title;
            this.command = step.command != null ? step.command : "";
            this.keys = step.keys != null ? step.keys : "";
            this.macroId = step.macroId != null ? step.macroId : "";
            this.kind = step.kind;
        }

        EditableStep(int index, String title, String command,
                     String keys, String macroId, String kind) {
            this.index = index;
            this.title = title;
            this.command = command;
            this.keys = keys;
            this.macroId = macroId;
            this.kind = kind;
        }
    }

    /** Get the display text for the command field based on step kind. */
    private String getPayloadText(@NonNull EditableStep step) {
        switch (step.kind) {
            case "hid":   return step.keys;
            case "macro": return step.macroId;
            default:      return step.command;
        }
    }

    /** Set the payload field from the command field text based on step kind. */
    private void setPayloadFromText(@NonNull EditableStep step, @NonNull String text) {
        switch (step.kind) {
            case "hid":   step.keys = text; break;
            case "macro": step.macroId = text; break;
            default:      step.command = text; break;
        }
    }
}
