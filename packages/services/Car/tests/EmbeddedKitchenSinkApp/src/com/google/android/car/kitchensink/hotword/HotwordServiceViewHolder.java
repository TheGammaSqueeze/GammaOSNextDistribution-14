/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.google.android.car.kitchensink.hotword;

import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.car.kitchensink.R;

import java.util.Objects;

final class HotwordServiceViewHolder extends RecyclerView.ViewHolder
        implements HotwordServiceUpdatedCallback {
    private final TextView mStatusTextView;
    private final Button mStartServiceButton;
    private final Button mStartRecordingButton;
    private final Button mStopRecordingButton;
    private final Button mStopServiceButton;
    private final HotwordServiceViewChangeCallback mServiceChangeCallback;

    HotwordServiceViewHolder(@NonNull View itemView,
            HotwordServiceViewChangeCallback serviceCallback) {
        super(itemView);

        mServiceChangeCallback = Objects.requireNonNull(serviceCallback,
                "Hotword service view change callback can not be null");
        mStartServiceButton = itemView.findViewById(R.id.start_service_button);
        mStartServiceButton.setOnClickListener(v -> {
            mServiceChangeCallback.onStartService(getAbsoluteAdapterPosition());
        });

        mStopServiceButton = itemView.findViewById(R.id.stop_service_button);
        mStopServiceButton.setOnClickListener(v -> {
            mServiceChangeCallback.onsStopService(getAbsoluteAdapterPosition());
        });

        mStartRecordingButton = itemView.findViewById(R.id.start_recording_button);
        mStartRecordingButton.setOnClickListener(v -> {
            mServiceChangeCallback.onStartRecording(getAbsoluteAdapterPosition());
        });


        mStopRecordingButton = itemView.findViewById(R.id.stop_recording_button);
        mStopRecordingButton.setOnClickListener(v -> {
            mServiceChangeCallback.onStopRecording(getAbsoluteAdapterPosition());
        });

        mStartRecordingButton.setVisibility(View.GONE);
        mStopRecordingButton.setVisibility(View.GONE);
        mStopServiceButton.setVisibility(View.GONE);

        mStatusTextView = itemView.findViewById(R.id.status_message_text_view);

        setStatusMessage("Service Not Started");
    }

    private void setStatusMessage(String string) {
        mStatusTextView.setText(string);
    }

    @Override
    public void onServiceStarted(String message) {
        mStartServiceButton.setVisibility(View.GONE);
        mStopServiceButton.setVisibility(View.VISIBLE);
        mStartRecordingButton.setVisibility(View.VISIBLE);
        mStopRecordingButton.setVisibility(View.GONE);

        setStatusMessage(message);
    }

    @Override
    public void onServiceStopped(String message) {
        mStartServiceButton.setVisibility(View.VISIBLE);
        mStopServiceButton.setVisibility(View.GONE);
        mStartRecordingButton.setVisibility(View.GONE);
        mStopRecordingButton.setVisibility(View.GONE);

        setStatusMessage(message);
    }

    @Override
    public void onRecordingStarted(String message) {
        mStopServiceButton.setVisibility(View.VISIBLE);
        mStartRecordingButton.setVisibility(View.GONE);
        mStopRecordingButton.setVisibility(View.VISIBLE);

        setStatusMessage(message);
    }

    @Override
    public void onRecordingStopped(String message) {
        mStartRecordingButton.setVisibility(View.VISIBLE);
        mStopRecordingButton.setVisibility(View.GONE);

        setStatusMessage(message);
    }
}
