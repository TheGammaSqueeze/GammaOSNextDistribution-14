/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.google.android.car.kitchensink.audio;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;

import com.google.android.car.kitchensink.R;

import java.util.Objects;

public final class AcceptAudioDialog extends DialogFragment {

    private final OnAudioAllowedCallback mOnAudioAllowedCallback;
    private final String mUserName;

    public static AcceptAudioDialog newInstance(OnAudioAllowedCallback callback, String userName) {
        return new AcceptAudioDialog(callback, userName);
    }

    private AcceptAudioDialog(OnAudioAllowedCallback callback, String userName) {
        mOnAudioAllowedCallback = Objects.requireNonNull(callback, "Callback can not be null");
        mUserName = Objects.requireNonNull(userName, "User name can not be null");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.accept_audio_layout, container);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextView message = view.findViewById(R.id.message);
        message.setText(getString(R.string.allow_passenger_playback_confirmation, mUserName));
        Button acceptButton = view.findViewById(R.id.accept_button);
        acceptButton.setOnClickListener((v) -> handleAcceptAudioPlayback(/* allowed= */ true));
        Button rejectButton = view.findViewById(R.id.reject_button);
        rejectButton.setOnClickListener((v) -> handleAcceptAudioPlayback(/* allowed= */ false));
    }

    private void handleAcceptAudioPlayback(boolean allowed) {
        mOnAudioAllowedCallback.onAudioAllowed(allowed);
        dismissNow();
    }

    public interface OnAudioAllowedCallback {
        void onAudioAllowed(boolean allowed);
    }
}
