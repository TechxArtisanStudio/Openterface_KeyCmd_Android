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
    }

    private static final int VT_USER = 0;
    private static final int VT_ASSISTANT = 1;
    private static final int VT_PLAN = 2;
    private static final int VT_ACT = 3;
    private static final int VT_CLI = 4;
    private static final int VT_MACRO = 5;
    private static final int VT_THINKING = 6;

    private final List<AgentMessage> messages = new ArrayList<>();
    @Nullable private ActBarListener actBarListener;

    public void setActBarListener(@Nullable ActBarListener listener) {
        this.actBarListener = listener;
    }

    public void submitList(@NonNull List<AgentMessage> next) {
        messages.clear();
        messages.addAll(next);
        notifyDataSetChanged();
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
                notifyItemChanged(i);
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
                ((SimpleTextHolder) holder).bind(message.text);
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
    public int getItemCount() {
        return messages.size();
    }

    private static final class SimpleTextHolder extends RecyclerView.ViewHolder {
        private final TextView textView;

        SimpleTextHolder(@NonNull View itemView, boolean user) {
            super(itemView);
            textView = itemView.findViewById(user ? R.id.agent_user_text : R.id.agent_assistant_text);
        }

        void bind(@Nullable String text) {
            textView.setText(text);
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
        }
    }

    private static final class CliHolder extends RecyclerView.ViewHolder {
        private final TextView output;

        CliHolder(@NonNull View itemView) {
            super(itemView);
            output = itemView.findViewById(R.id.agent_terminal_output);
        }

        void bind(@NonNull AgentMessage message) {
            StringBuilder builder = new StringBuilder();
            for (String line : message.terminalLines) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(line);
            }
            output.setText(builder.toString());
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
        private final TextView textView;

        ThinkingHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.agent_thinking_text);
        }

        void bind(@NonNull AgentMessage message) {
            textView.setText(message.text);
        }
    }
}
