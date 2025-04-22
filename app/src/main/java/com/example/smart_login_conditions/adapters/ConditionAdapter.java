package com.example.smart_login_conditions.adapters;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smart_login_conditions.databinding.ItemConditionBinding;
import com.example.smart_login_conditions.interfaces.ConditionActionListener;
import com.example.smart_login_conditions.models.Condition;
import com.example.smart_login_conditions.utils.PermissionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConditionAdapter extends RecyclerView.Adapter<ConditionAdapter.ConditionViewHolder> {

    private List<Condition> conditionList;
    private final ConditionActionListener listener;
    private String lastCallerName;
    private final Map<String, String> inputValues = new HashMap<>();

    public ConditionAdapter(List<Condition> conditionList, ConditionActionListener listener) {
        this.conditionList = conditionList;
        this.listener = listener;
    }

    public void setLastCallerName(String name) {
        this.lastCallerName = name;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ConditionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemConditionBinding binding = ItemConditionBinding.inflate(inflater, parent, false);
        return new ConditionViewHolder(binding);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull ConditionViewHolder holder, int position) {
        Condition condition = conditionList.get(position);
        holder.binding.conditionTXTName.setText(condition.getName());
        holder.binding.conditionTXTStatus.setText(condition.getStatus());


        switch (condition.getType()) {
            case ACTION_BUTTON:
                holder.binding.conditionBTNAction.setVisibility(View.VISIBLE);
                holder.binding.conditionETInput.setVisibility(View.GONE);
                break;

            case INPUT_FIELD:
                holder.binding.conditionETInput.setVisibility(View.VISIBLE);
                holder.binding.conditionBTNAction.setVisibility(View.GONE);

                if (condition.getName().equals("Call Match")) {
                    if (lastCallerName == null || lastCallerName.isEmpty()) {
                        holder.binding.conditionETInput.setHint("No recent caller");
                        holder.binding.conditionTXTStatus.setText("❌ No recent call");
                        holder.binding.conditionTXTStatus.setTextColor(Color.parseColor("#F44336"));
                    } else {
                        holder.binding.conditionETInput.setHint("Enter caller name...");
                        holder.binding.conditionETInput.setEnabled(true);

                        String currentValue = inputValues.get(condition.getName());
                        if (currentValue != null) {
                            holder.binding.conditionETInput.setText(currentValue);
                        } else {
                            holder.binding.conditionETInput.setText("");
                        }

                        holder.binding.conditionETInput.addTextChangedListener(new TextWatcher() {
                            @Override
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                            }

                            @Override
                            public void onTextChanged(CharSequence s, int start, int before, int count) {
                                inputValues.put(condition.getName(), s.toString().trim());

                                boolean passed = s.toString().trim().equalsIgnoreCase(lastCallerName);
                                condition.setPassed(passed);
                                condition.setStatus(passed ?
                                        "✔ Password matched" :
                                        "❌ Incorrect password");

                                holder.binding.conditionTXTStatus.setText(condition.getStatus());
                                holder.binding.conditionTXTStatus.setTextColor(
                                        passed ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336")
                                );
                            }

                            @Override
                            public void afterTextChanged(Editable s) {
                            }
                        });
                    }
                }
                break;
            case AUTOMATIC:
                holder.binding.conditionETInput.setVisibility(View.GONE);
                holder.binding.conditionBTNAction.setVisibility(View.GONE);
                break;
        }

        holder.binding.conditionTXTStatus.setText(condition.getStatus());
        holder.binding.conditionTXTStatus.setTextColor(
                condition.isPassed() ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336")
        );

        holder.binding.conditionBTNAction.setOnClickListener(v ->
                listener.onActionClicked(condition));

        holder.binding.conditionETInput.setOnClickListener(v ->listener.onActionClicked(condition));

    }

    @Override
    public int getItemCount() {
        return conditionList.size();
    }

    public static class ConditionViewHolder extends RecyclerView.ViewHolder {
        ItemConditionBinding binding;

        public ConditionViewHolder(@NonNull ItemConditionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
