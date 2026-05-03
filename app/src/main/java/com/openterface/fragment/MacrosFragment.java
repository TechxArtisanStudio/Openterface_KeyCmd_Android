package com.openterface.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.openterface.keymod.CreateMacroBottomSheet;
import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.MacrosManager;
import com.openterface.keymod.MacrosManager.KeyEvent;
import com.openterface.keymod.MacrosManager.Macro;
import com.openterface.keymod.MacrosManager.MacrosListener;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Macros hub: list layout and Material actions aligned with Shortcut Hub.
 */
public class MacrosFragment extends Fragment implements MacrosListener {

    private MacrosManager macrosManager;
    private ConnectionManager connectionManager;

    private MaterialButton editModeButton;
    private MaterialButton addMacroButton;
    private RecyclerView macrosRecyclerView;
    private TextView emptyTextView;

    private final List<Macro> macrosList = new ArrayList<>();
    private MacroListAdapter listAdapter;
    private boolean editMode = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_macros, container, false);

        macrosManager = MacrosManager.getInstance(requireContext());
        macrosManager.setListener(this);

        if (getActivity() instanceof MainActivity) {
            connectionManager = ((MainActivity) getActivity()).getConnectionManager();
        }

        initializeViews(view);
        setupListeners();
        loadMacros();
        updateEditModeUi();

        return view;
    }

    private void initializeViews(View view) {
        editModeButton = view.findViewById(R.id.edit_mode_button);
        addMacroButton = view.findViewById(R.id.add_macro_button);
        macrosRecyclerView = view.findViewById(R.id.macros_recycler);
        emptyTextView = view.findViewById(R.id.empty_textview);

        macrosRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        listAdapter = new MacroListAdapter(requireContext(), macrosList, this::onMacroRowClick,
                this::onMacroRowLongClick);
        macrosRecyclerView.setAdapter(listAdapter);
    }

    private void onMacroRowClick(Macro macro) {
        if (editMode) {
            showMacroEditorDialog(macro);
        } else {
            playMacro(macro);
        }
    }

    private void onMacroRowLongClick(Macro macro) {
        showMacroActionsMenu(macro);
    }

    private void setupListeners() {
        addMacroButton.setOnClickListener(v -> showMacroEditorDialog(null));

        editModeButton.setOnClickListener(v -> {
            editMode = !editMode;
            updateEditModeUi();
            listAdapter.notifyDataSetChanged();
        });
    }

    private void updateEditModeUi() {
        editModeButton.setText(editMode
                ? getString(R.string.macros_done_layout)
                : getString(R.string.macros_edit_layout));
    }

    private void loadMacros() {
        macrosList.clear();
        macrosList.addAll(macrosManager.getAllMacros());
        listAdapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateEmptyState() {
        boolean empty = macrosList.isEmpty();
        macrosRecyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyTextView.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void playMacro(Macro macro) {
        if (connectionManager == null || !connectionManager.isConnected()) {
            Toast.makeText(getContext(), R.string.macros_toast_not_connected, Toast.LENGTH_SHORT).show();
            return;
        }
        macrosManager.playMacro(macro, connectionManager);
        Toast.makeText(getContext(), getString(R.string.macros_toast_playing, macro.name), Toast.LENGTH_SHORT).show();
    }

    private void showMacroActionsMenu(Macro macro) {
        CharSequence[] options = new CharSequence[]{
                getString(R.string.macros_action_edit),
                getString(R.string.macros_action_delete)
        };
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(macro.name)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showMacroEditorDialog(macro);
                    } else {
                        showDeleteConfirmDialog(macro);
                    }
                })
                .show();
    }

    private void showDeleteConfirmDialog(Macro macro) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.macros_delete_title)
                .setMessage(getString(R.string.macros_delete_message, macro.name))
                .setPositiveButton(R.string.macros_delete_confirm, (dialog, which) -> {
                    macrosManager.deleteMacro(macro);
                    loadMacros();
                    Toast.makeText(getContext(), R.string.macros_toast_deleted, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showMacroEditorDialog(@Nullable Macro macro) {
        if (!(getActivity() instanceof AppCompatActivity)) {
            return;
        }
        CreateMacroBottomSheet.show(
                (AppCompatActivity) requireActivity(),
                macrosManager,
                macro,
                this::loadMacros);
    }

    @Override
    public void onRecordingStarted(Macro macro) {}

    @Override
    public void onKeyEventRecorded(KeyEvent event) {}

    @Override
    public void onMacroSaved(Macro macro) {
        loadMacros();
    }

    @Override
    public void onPlaybackStarted(Macro macro) {}

    @Override
    public void onPlaybackComplete(Macro macro) {
        Toast.makeText(getContext(), R.string.macros_toast_playback_complete, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onPlaybackStopped() {}

    @Override
    public void onMacroDeleted(Macro macro) {
        loadMacros();
    }

    @Override
    public void onMacrosImported(List<Macro> importedMacros) {
        loadMacros();
    }

    @Override
    public void onMacrosCleared() {
        loadMacros();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        macrosManager.setListener(null);
    }

    private static final class MacroListAdapter extends RecyclerView.Adapter<MacroListAdapter.Holder> {

        private final Context context;
        private final List<Macro> macros;
        private final OnMacroRowClick onClick;
        private final OnMacroRowLongClick onLongClick;

        MacroListAdapter(Context context, List<Macro> macros,
                OnMacroRowClick onClick, OnMacroRowLongClick onLongClick) {
            this.context = context;
            this.macros = macros;
            this.onClick = onClick;
            this.onLongClick = onLongClick;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View row = LayoutInflater.from(context).inflate(R.layout.item_macro_hub_row, parent, false);
            return new Holder(row);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Macro macro = macros.get(position);
            holder.name.setText(macro.name);
            String data = macro.data != null ? macro.data.trim() : "";
            if (data.isEmpty()) {
                holder.preview.setText(R.string.macros_preview_empty);
            } else {
                holder.preview.setText(data);
            }
            holder.scheduledBadge.setVisibility(macro.isScheduled ? View.VISIBLE : View.GONE);
            holder.itemView.setOnClickListener(v -> onClick.onClick(macro));
            holder.itemView.setOnLongClickListener(v -> {
                onLongClick.onLongClick(macro);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return macros.size();
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView preview;
            final TextView scheduledBadge;

            Holder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.macro_row_name);
                preview = itemView.findViewById(R.id.macro_row_preview);
                scheduledBadge = itemView.findViewById(R.id.macro_row_scheduled_badge);
            }
        }
    }

    @FunctionalInterface
    private interface OnMacroRowClick {
        void onClick(Macro macro);
    }

    @FunctionalInterface
    private interface OnMacroRowLongClick {
        void onLongClick(Macro macro);
    }
}
