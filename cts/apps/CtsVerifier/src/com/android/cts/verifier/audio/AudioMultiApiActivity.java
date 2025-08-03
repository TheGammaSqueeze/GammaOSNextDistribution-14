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

package com.android.cts.verifier.audio;

import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

// MegaAudio
import org.hyphonate.megaaudio.common.BuilderBase;

abstract class AudioMultiApiActivity
        extends PassFailButtons.Activity
        implements View.OnClickListener {
    private static final String TAG = "AudioMultiApiActivity";

    protected int mAudioApi = BuilderBase.TYPE_OBOE | BuilderBase.SUB_TYPE_OBOE_AAUDIO;

    // Test API (back-end) IDs
    protected static final int NUM_TEST_APIS = 2;
    protected static final int TEST_API_NATIVE = 0;
    protected static final int TEST_API_JAVA = 1;
    protected int mActiveTestAPI = TEST_API_NATIVE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ((RadioButton) findViewById(R.id.audioJavaApiBtn)).setOnClickListener(this);
        RadioButton nativeApiRB = findViewById(R.id.audioNativeApiBtn);
        nativeApiRB.setChecked(true);
        nativeApiRB.setOnClickListener(this);
    }

    @Override
    public void setTestResultAndFinish(boolean passed) {
        super.setTestResultAndFinish(passed);
    }

    public abstract void onApiChange(int api);

    //
    // View.OnClickListener
    //
    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.audioJavaApiBtn) {
            mAudioApi = BuilderBase.TYPE_JAVA;
            onApiChange(mActiveTestAPI = TEST_API_JAVA);
        } else if (id == R.id.audioNativeApiBtn) {
            mAudioApi = BuilderBase.TYPE_OBOE | BuilderBase.SUB_TYPE_OBOE_AAUDIO;
            onApiChange(mActiveTestAPI = TEST_API_NATIVE);
        }
    }
}
