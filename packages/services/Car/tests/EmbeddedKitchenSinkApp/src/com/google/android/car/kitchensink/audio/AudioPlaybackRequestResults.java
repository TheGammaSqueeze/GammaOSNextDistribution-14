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

public final class AudioPlaybackRequestResults extends DialogFragment {

    private final String mUserName;
    private final boolean mAllowedToPlay;

    public static AudioPlaybackRequestResults newInstance(String userName, boolean allowed) {
        return new AudioPlaybackRequestResults(userName, allowed);
    }

    private AudioPlaybackRequestResults(String userName, boolean allowed) {
        mUserName = Objects.requireNonNull(userName, "User name can not be null");
        mAllowedToPlay = allowed;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.notify_request_results, container);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextView message = view.findViewById(R.id.message);
        message.setText(getString(R.string.request_result_message, mUserName,
                mAllowedToPlay ? "allowed" : "not allowed"));
        Button closeButton = view.findViewById(R.id.close_button);
        closeButton.setOnClickListener((v) -> dismiss());
    }
}

