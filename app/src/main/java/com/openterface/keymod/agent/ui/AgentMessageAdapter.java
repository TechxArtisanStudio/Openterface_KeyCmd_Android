package com.openterface.keymod.agent.ui;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.openterface.keymod.R;

import java.util.ArrayList;
import java.util.List;

public final class AgentMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface ActBarListener {
        void onApprove();

        void onEditPlan();

        void onCancel();

        /** Called when the user long-presses the act bar to re-execute the current plan. */
        void onReexecute();
    }

    public interface RetryListener {
        void onRetry();
    }

    private static final int VT_USER = 0;
    private static final int VT_ASSISTANT = 1;
    private static final int VT_PLAN = 2;
    private static final int VT_ACT = 3;
    private static final int VT_CLI = 4;
    private static final int VT_MACRO = 5;
    private static final int VT_THINKING = 6;

    /** Payload for partial rebind of THINKING items — updates text without resetting the ProgressBar animation. */
    private static final Object PAYLOAD_THINKING_TEXT = new Object();

    private final List<AgentMessage> messages = new ArrayList<>();
    @Nullable private ActBarListener actBarListener;
    @Nullable private RetryListener retryListener;

    public void setActBarListener(@Nullable ActBarListener listener) {
        this.actBarListener = listener;
    }

    public void setRetryListener(@Nullable RetryListener listener) {
        this.retryListener = listener;
    }

    public void submitList(@NonNull List<AgentMessage> next) {
        messages.clear();
        messages.addAll(next);
        notifyDataSetChanged();
    }

    /**
     * Append a message to the internal list AND notify the RecyclerView.
     * Use this instead of modifying an external list + notifyItemInserted()
     * to keep adapter state in sync.
     */
    public void addItem(@NonNull AgentMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    /**
     * Replace the message at the given position AND notify the RecyclerView.
     * Use this instead of modifying an external list + notifyItemChanged()
     * to keep adapter state in sync.
     */
    public void setItem(int position, @NonNull AgentMessage message) {
        if (position < 0 || position >= messages.size()) return;
        messages.set(position, message);
        notifyItemChanged(position);
    }

    /**
     * Replace the last message in the list and rebind that position.
     * Used for streaming token updates to avoid a full notifyDataSetChanged().
     * Returns true if a message was replaced.
     */
    public boolean updateLastMessage(@NonNull AgentMessage updated) {
        if (messages.isEmpty()) return false;
        messages.set(messages.size() - 1, updated);
        notifyItemChanged(messages.size() - 1);
        return true;
    }

    /**
     * Find the THINKING message by type, update its text, and rebind only that position.
     * Avoids notifyDataSetChanged() which would destroy the ProgressBar animation.
     * Returns true if a thinking message was found and updated.
     */
    public boolean updateThinkingMessage(@NonNull String text) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).type == AgentMessage.Type.THINKING) {
                messages.set(i, AgentMessage.thinking(text));
                notifyItemChanged(i, PAYLOAD_THINKING_TEXT);
                return true;
            }
        }
        return false;
    }

    @Override
    public int getItemViewType(int position) {
        switch (messages.get(position).type) {
            case USER:
                return VT_USER;
            case ASSISTANT:
                return VT_ASSISTANT;
            case PLAN:
                return VT_PLAN;
            case ACT_BAR:
                return VT_ACT;
            case EXECUTION_CLI:
                return VT_CLI;
            case EXECUTION_MACRO:
                return VT_MACRO;
            case THINKING:
                return VT_THINKING;
            default:
                return VT_MACRO;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case VT_USER:
                return new SimpleTextHolder(inflater.inflate(R.layout.item_agent_message_user, parent, false), true);
            case VT_ASSISTANT:
                return new SimpleTextHolder(inflater.inflate(R.layout.item_agent_message_assistant, parent, false), false);
            case VT_PLAN:
                return new PlanHolder(inflater.inflate(R.layout.item_agent_message_plan, parent, false));
            case VT_ACT:
                return new ActBarHolder(inflater.inflate(R.layout.item_agent_message_act_bar, parent, false));
            case VT_CLI:
                return new CliHolder(inflater.inflate(R.layout.item_agent_message_execution_cli, parent, false));
            case VT_MACRO:
            default:
                return new MacroHolder(inflater.inflate(R.layout.item_agent_message_execution_macro, parent, false));
            case VT_THINKING:
                return new ThinkingHolder(inflater.inflate(R.layout.item_agent_thinking, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        AgentMessage message = messages.get(position);
        switch (holder.getItemViewType()) {
            case VT_USER:
                ((SimpleTextHolder) holder).bind(message.text);
                break;
            case VT_ASSISTANT:
                ((SimpleTextHolder) holder).bind(message, retryListener);
                break;
            case VT_PLAN:
                ((PlanHolder) holder).bind(message);
                break;
            case VT_ACT:
                ((ActBarHolder) holder).bind(actBarListener);
                break;
            case VT_CLI:
                ((CliHolder) holder).bind(message);
                break;
            case VT_MACRO:
                ((MacroHolder) holder).bind(message);
                break;
            case VT_THINKING:
                ((ThinkingHolder) holder).bind(message);
                break;
            default:
                break;
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position,
                                  @NonNull java.util.List<Object> payloads) {
        if (!payloads.isEmpty() && PAYLOAD_THINKING_TEXT.equals(payloads.get(0))
                && holder instanceof ThinkingHolder) {
            // Partial rebind: update text only — keeps the ProgressBar animation running.
            ((ThinkingHolder) holder).textView.setText(messages.get(position).text);
            return;
        }
        // No matching payload — fall back to full bind.
        onBindViewHolder(holder, position);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    private static final class SimpleTextHolder extends RecyclerView.ViewHolder {
        private final TextView textView;
        private final ImageView errorIcon;
        private final MaterialButton retryButton;
        private final View contentRow;
        private final boolean isUser;

        SimpleTextHolder(@NonNull View itemView, boolean user) {
            super(itemView);
            this.isUser = user;
            textView = itemView.findViewById(user ? R.id.agent_user_text : R.id.agent_assistant_text);
            if (user) {
                errorIcon = null;
                retryButton = null;
                contentRow = null;
            } else {
                errorIcon = itemView.findViewById(R.id.agent_error_icon);
                retryButton = itemView.findViewById(R.id.agent_retry_button);
                contentRow = itemView.findViewById(R.id.agent_assistant_content_row);
            }
        }

        void bind(@Nullable CharSequence text) {
            textView.setText(text);
            // Hide retry button by default for plain text binds
            if (retryButton != null) {
                retryButton.setVisibility(View.GONE);
            }
        }

        /** Bind with full message state — sets error background, icon visibility, retry button. */
        void bind(@NonNull AgentMessage message, @Nullable RetryListener retryListener) {
            textView.setText(message.text);

            if (isUser) return;  // User bubbles never have error state

            if (message.isError) {
                // Error state: red background + warning icon + optional retry button
                if (contentRow != null) {
                    contentRow.setBackgroundResource(R.drawable.agent_bubble_error);
                }
                if (errorIcon != null) {
                    errorIcon.setVisibility(View.VISIBLE);
                    errorIcon.setColorFilter(0xFFEF4444); // presentation_red
                }
                if (retryButton != null) {
                    retryButton.setVisibility(message.canRetry ? View.VISIBLE : View.GONE);
                    retryButton.setOnClickListener(v -> {
                        if (retryListener != null) retryListener.onRetry();
                    });
                }
            } else {
                // Normal state
                if (contentRow != null) {
                    contentRow.setBackgroundResource(R.drawable.agent_bubble_assistant);
                }
                if (errorIcon != null) {
                    errorIcon.setVisibility(View.GONE);
                }
                if (retryButton != null) {
                    retryButton.setVisibility(View.GONE);
                    retryButton.setOnClickListener(null);
                }
            }
        }
    }

    private static final class PlanHolder extends RecyclerView.ViewHolder {
        private final LinearLayout container;
        private final View hidBadge;

        PlanHolder(@NonNull View itemView) {
            super(itemView);
            container = itemView.findViewById(R.id.agent_plan_steps_container);
            hidBadge = itemView.findViewById(R.id.agent_plan_hid_badge);
        }

        void bind(@NonNull AgentMessage message) {
            // HID mode badge
            hidBadge.setVisibility(message.isHidMode ? View.VISIBLE : View.GONE);

            // HID mode: orange border via stroke
            if (message.isHidMode && itemView instanceof com.google.android.material.card.MaterialCardView) {
                ((com.google.android.material.card.MaterialCardView) itemView)
                        .setStrokeColor(0xFFFF9500);
            }

            container.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(itemView.getContext());
            for (AgentPlanStep step : message.planSteps) {
                View row = inflater.inflate(R.layout.item_agent_plan_step_row, container, false);
                TextView index = row.findViewById(R.id.agent_plan_step_index);
                ImageView icon = row.findViewById(R.id.agent_plan_step_icon);
                TextView title = row.findViewById(R.id.agent_plan_step_title);
                TextView subtitle = row.findViewById(R.id.agent_plan_step_subtitle);
                index.setText(String.valueOf(step.index));
                title.setText(step.title);
                icon.setImageResource(iconFor(step.kind));
                if (!TextUtils.isEmpty(step.subtitle)) {
                    subtitle.setVisibility(View.VISIBLE);
                    subtitle.setText(step.subtitle);
                } else {
                    subtitle.setVisibility(View.GONE);
                }
                container.addView(row);
            }
        }

        private static int iconFor(@NonNull AgentPlanStep.Kind kind) {
            switch (kind) {
                case TERMINAL:
                    return R.drawable.ic_terminal;
                case MACRO:
                    return R.drawable.ic_list;
                case HID:
                default:
                    return R.drawable.keyboard;
            }
        }
    }

    private static final class ActBarHolder extends RecyclerView.ViewHolder {
        ActBarHolder(@NonNull View itemView) {
            super(itemView);
        }

        void bind(@Nullable ActBarListener listener) {
            MaterialButton approve = itemView.findViewById(R.id.agent_approve_button);
            MaterialButton edit = itemView.findViewById(R.id.agent_edit_plan_button);
            MaterialButton cancel = itemView.findViewById(R.id.agent_cancel_button);
            approve.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onApprove();
                }
            });
            edit.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEditPlan();
                }
            });
            cancel.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCancel();
                }
            });
            // Long-press anywhere on the act bar to re-execute the current plan
            itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onReexecute();
                    return true;
                }
                return false;
            });
        }
    }

    private static final class CliHolder extends RecyclerView.ViewHolder {
        private final TextView statusBadge;
        private final TextView commandText;
        private final TextView outputText;
        private final ImageView headerIcon;
        private final TextView headerLabel;
        private final com.google.android.material.card.MaterialCardView cardView;

        CliHolder(@NonNull View itemView) {
            super(itemView);
            statusBadge = itemView.findViewById(R.id.agent_cli_status);
            commandText = itemView.findViewById(R.id.agent_cli_command);
            outputText = itemView.findViewById(R.id.agent_cli_output);
            headerIcon = itemView.findViewById(R.id.agent_cli_icon);
            headerLabel = itemView.findViewById(R.id.agent_cli_header_label);
            cardView = (com.google.android.material.card.MaterialCardView) itemView;
        }

        void bind(@NonNull AgentMessage message) {
            List<String> lines = message.terminalLines;
            if (lines.isEmpty()) return;

            // First line is the command (e.g. "$ hostname")
            String cmd = lines.get(0);
            commandText.setText(cmd);

            // Remaining lines are output
            boolean hasOutput = lines.size() > 1;
            if (hasOutput) {
                StringBuilder builder = new StringBuilder();
                for (int i = 1; i < lines.size(); i++) {
                    if (builder.length() > 0) builder.append('\n');
                    builder.append(lines.get(i));
                }
                outputText.setText(builder.toString());
                outputText.setVisibility(View.VISIBLE);
            } else {
                outputText.setVisibility(View.GONE);
            }

            // Use explicit cliSuccess field from AgentController (not string matching)
            // cliSuccess: null = running, true = success, false = failed
            if (message.cliSuccess == null) {
                // Running state: orange badge, no border, terminal icon
                statusBadge.setText(R.string.agent_cli_running);
                statusBadge.setTextColor(0xFFF57C00); // orange
                cardView.setStrokeColor(android.content.res.ColorStateList.valueOf(0x00000000).getDefaultColor());
                cardView.setStrokeWidth(0);
                headerIcon.setImageResource(R.drawable.ic_terminal);
                headerIcon.setColorFilter(0xFF81C784);
                headerLabel.setTextColor(0xFF81C784);
            } else if (message.cliSuccess) {
                // Success state: green badge, no border, terminal icon
                statusBadge.setText(R.string.agent_cli_done);
                statusBadge.setTextColor(0xFF4CAF50); // green
                cardView.setStrokeColor(android.content.res.ColorStateList.valueOf(0x00000000).getDefaultColor());
                cardView.setStrokeWidth(0);
                headerIcon.setImageResource(R.drawable.ic_terminal);
                headerIcon.setColorFilter(0xFF81C784);
                headerLabel.setTextColor(0xFF81C784);
            } else {
                // Failed state: red badge, red card border, warning icon
                statusBadge.setText(R.string.agent_cli_failed);
                statusBadge.setTextColor(0xFFEF4444); // red
                cardView.setStrokeColor(0xFFEF4444);
                cardView.setStrokeWidth(Math.round(1.5f * itemView.getResources().getDisplayMetrics().density));
                headerIcon.setImageResource(R.drawable.ic_warning);
                headerIcon.setColorFilter(0xFFEF4444);
                headerLabel.setTextColor(0xFFEF4444);
            }
        }
    }

    private static final class MacroHolder extends RecyclerView.ViewHolder {
        private final TextView statusChip;
        private final ProgressBar progressBar;
        private final LinearLayout stepsContainer;

        MacroHolder(@NonNull View itemView) {
            super(itemView);
            statusChip = itemView.findViewById(R.id.agent_macro_status_chip);
            progressBar = itemView.findViewById(R.id.agent_macro_progress);
            stepsContainer = itemView.findViewById(R.id.agent_macro_steps_container);
        }

        void bind(@NonNull AgentMessage message) {
            progressBar.setProgress(message.macroProgress);
            if (!TextUtils.isEmpty(message.macroStatusChip)) {
                statusChip.setVisibility(View.VISIBLE);
                statusChip.setText(message.macroStatusChip);
            } else {
                statusChip.setVisibility(View.GONE);
            }
            stepsContainer.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(itemView.getContext());
            for (int i = 0; i < message.macroSteps.size(); i++) {
                View row = inflater.inflate(R.layout.item_agent_macro_step_row, stepsContainer, false);
                ImageView check = row.findViewById(R.id.agent_macro_step_check);
                TextView label = row.findViewById(R.id.agent_macro_step_label);
                label.setText(message.macroSteps.get(i));
                boolean done = i <= message.macroCurrentStep;
                check.setVisibility(done ? View.VISIBLE : View.INVISIBLE);
                label.setTextColor(itemView.getResources().getColor(
                        done ? R.color.text_primary : R.color.text_secondary,
                        itemView.getContext().getTheme()));
                stepsContainer.addView(row);
            }
        }
    }

    private static final class ThinkingHolder extends RecyclerView.ViewHolder {
        final TextView textView;  // package-private: accessible from payload partial bind

        ThinkingHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.agent_thinking_text);
        }

        void bind(@NonNull AgentMessage message) {
            textView.setText(message.text);
        }
    }
}
