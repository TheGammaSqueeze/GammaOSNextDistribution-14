/*
 * Copyright (C) 2022 The Android Open Source Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.cartelemetryapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ConfigListAdaptor extends RecyclerView.Adapter<ConfigListAdaptor.ViewHolder> {
    private List<IConfigData> mConfigs;
    private Callback mCallback;

    public interface Callback {
        void onAddButtonClicked(IConfigData config);
        void onRemoveButtonClicked(IConfigData config);
        void onInfoButtonClicked(IConfigData config);
        void onClearButtonClicked(IConfigData config);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final CheckBox checkBox;
        public final TextView configText;
        public final TextView onReadyText;
        public final TextView sentBytesText;
        public final TextView errorsCountText;
        public final Button addConfigButton;
        public final Button removeConfigButton;
        public final Button infoButton;
        public final Button clearButton;

        public ViewHolder(View view) {
            super(view);
            checkBox = view.findViewById(R.id.checkbox);
            configText = view.findViewById(R.id.config_name_text);
            onReadyText = view.findViewById(R.id.on_ready_times_text);
            sentBytesText = view.findViewById(R.id.sent_bytes_text);
            errorsCountText = view.findViewById(R.id.error_count_text);
            addConfigButton = view.findViewById(R.id.add_config_button);
            removeConfigButton = view.findViewById(R.id.remove_config_button);
            infoButton = view.findViewById(R.id.show_info_button);
            clearButton = view.findViewById(R.id.clear_info_button);
        }
    }

    public ConfigListAdaptor(
            List<IConfigData> configs,
            Callback callback) {
        mConfigs = configs;
        mCallback = callback;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        int pos = holder.getAbsoluteAdapterPosition();
        IConfigData config = mConfigs.get(pos);
        holder.checkBox.setChecked(config.selected);
        holder.configText.setText(config.name);
        holder.onReadyText.setText(String.valueOf(config.onReadyTimes));
        String bytesSentStr = String.valueOf(config.sentBytes) + " B";
        holder.sentBytesText.setText(bytesSentStr);
        holder.errorsCountText.setText(String.valueOf(config.errorCount));
        holder.addConfigButton.setOnClickListener(v -> {
            mCallback.onAddButtonClicked(config);
        });
        holder.removeConfigButton.setOnClickListener(v -> {
            mCallback.onRemoveButtonClicked(config);
        });
        holder.infoButton.setOnClickListener(v -> {
            mCallback.onInfoButtonClicked(config);
        });
        holder.clearButton.setOnClickListener(v -> {
            mCallback.onClearButtonClicked(config);
        });
    }

    @Override
    public int getItemCount() {
        return mConfigs.size();
    }
}
