package com.openterface.keymod.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.openterface.keymod.R;

import java.util.ArrayList;
import java.util.List;

/**
 * History Fragment
 * - AI request history viewer
 * - Clear history functionality
 * - Retry failed requests
 */
public class HistoryFragment extends Fragment {

    private static final String PREF_HISTORY_DATA = "ai_history_data";
    private static final String PREF_MAX_HISTORY = "max_history";

    private ListView historyListView;
    private TextView emptyTextView;
    private Button clearHistoryButton;
    private Button retryButton;

    private SharedPreferences prefs;
    private List<String> historyItems;
    private HistoryAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings_history, container, false);
        
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        
        initializeViews(view);
        loadHistory();
        setupListeners();
        
        return view;
    }

    private void initializeViews(View view) {
        historyListView = view.findViewById(R.id.history_listview);
        emptyTextView = view.findViewById(R.id.empty_textview);
        clearHistoryButton = view.findViewById(R.id.clear_history_button);
        retryButton = view.findViewById(R.id.retry_button);
        
        historyItems = new ArrayList<>();
        adapter = new HistoryAdapter(requireContext(), historyItems);
        historyListView.setAdapter(adapter);
    }

    private void loadHistory() {
        // Load history from SharedPreferences
        // For now, use dummy data
        historyItems.clear();
        historyItems.add("2026-03-20 05:30 - Translate: Hello → 你好");
        historyItems.add("2026-03-20 05:28 - Command: Open browser");
        historyItems.add("2026-03-20 05:25 - Query: What's the weather?");
        historyItems.add("2026-03-20 05:20 - Command: Type email");
        
        if (historyItems.isEmpty()) {
            historyListView.setVisibility(View.GONE);
            emptyTextView.setVisibility(View.VISIBLE);
        } else {
            historyListView.setVisibility(View.VISIBLE);
            emptyTextView.setVisibility(View.GONE);
            adapter.notifyDataSetChanged();
        }
    }

    private void setupListeners() {
        clearHistoryButton.setOnClickListener(v -> {
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_history_clear_title)
                .setMessage(R.string.settings_history_clear_message)
                .setPositiveButton(R.string.settings_history_clear_confirm, (dialog, which) -> {
                    historyItems.clear();
                    adapter.notifyDataSetChanged();
                    historyListView.setVisibility(View.GONE);
                    emptyTextView.setVisibility(View.VISIBLE);
                    prefs.edit().remove(PREF_HISTORY_DATA).apply();
                    Toast.makeText(getContext(), R.string.settings_history_cleared_toast, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.settings_history_cancel, null)
                .show();
        });

        retryButton.setOnClickListener(v -> {
            if (historyItems.isEmpty()) {
                Toast.makeText(getContext(), R.string.settings_history_no_retry_toast, Toast.LENGTH_SHORT).show();
                return;
            }
            // TODO: Implement retry functionality
            Toast.makeText(getContext(), R.string.settings_history_retry_soon_toast, Toast.LENGTH_SHORT).show();
        });

        historyListView.setOnItemClickListener((parent, view, position, id) -> {
            // Show details or retry option
            String item = historyItems.get(position);
            Toast.makeText(getContext(), getString(R.string.settings_history_selected_toast, item), Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Simple History Adapter
     */
    private static class HistoryAdapter extends android.widget.BaseAdapter {
        private final List<String> items;
        private final LayoutInflater inflater;

        public HistoryAdapter(android.content.Context context, List<String> items) {
            this.items = items;
            this.inflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.item_history_entry, parent, false);
            }
            
            TextView textView = convertView.findViewById(android.R.id.text1);
            textView.setText(items.get(position));
            
            return convertView;
        }
    }
}
