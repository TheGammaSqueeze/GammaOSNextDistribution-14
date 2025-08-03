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

package com.android.cts.verifier.audio;

import android.media.AudioDeviceInfo;
import android.os.Bundle;
import android.view.View;

import com.android.cts.verifier.R;

// MegaAudio
import org.hyphonate.megaaudio.player.AudioSourceProvider;
import org.hyphonate.megaaudio.player.sources.SparseChannelAudioSourceProvider;
import org.hyphonate.megaaudio.recorder.AudioSinkProvider;
import org.hyphonate.megaaudio.recorder.sinks.AppCallbackAudioSinkProvider;

public class AudioDataPathsAnalogActivity extends AudioDataPathsBaseActivity {
    private static final String TAG = "AudioDataPathsAnalogActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.audio_datapaths_analog);

        super.onCreate(savedInstanceState);
        setInfoResources(
                R.string.audio_datapaths_analog_test, R.string.audio_datapaths_analog_info, -1);

        // Make sure there are devices to test (as in a device without an analog port),
        // or else enable pass button.
        if (mTestManager.countValidTestModules() == 0) {
            getPassButton().setEnabled(true);
        }
    }

    void gatherTestModules(TestManager testManager) {
        AudioSourceProvider leftSineSourceProvider = new SparseChannelAudioSourceProvider(
                SparseChannelAudioSourceProvider.CHANNELMASK_LEFT);
        AudioSourceProvider rightSineSourceProvider = new SparseChannelAudioSourceProvider(
                SparseChannelAudioSourceProvider.CHANNELMASK_RIGHT);

        AudioSinkProvider micSinkProvider =
                new AppCallbackAudioSinkProvider(mAnalysisCallbackHandler);

        TestModule testModule;

        testModule = new TestModule(
                AudioDeviceInfo.TYPE_WIRED_HEADSET, 48000, 2,
                AudioDeviceInfo.TYPE_WIRED_HEADSET, 48000, 1);
        testModule.setSectionTitle("Analog Jack");
        testModule.setDescription("Analog:2:Left Analog:1");
        testModule.setSources(leftSineSourceProvider, micSinkProvider);
        testManager.addTestModule(testModule);

        testModule = new TestModule(
                AudioDeviceInfo.TYPE_WIRED_HEADSET, 48000, 2,
                AudioDeviceInfo.TYPE_WIRED_HEADSET, 48000, 1);
        testModule.setDescription("Analog:2:Right Analog:1");
        testModule.setSources(rightSineSourceProvider, micSinkProvider);
        testManager.addTestModule(testModule);
    }

    void postValidateTestDevices(int numValidTestModules) {
        View promptView = findViewById(R.id.audio_datapaths_deviceprompt);
        if (mIsHandheld) {
            if (mTestManager.calculatePass()) {
                promptView.setVisibility(View.GONE);
            } else {
                promptView.setVisibility(numValidTestModules == 0 ? View.VISIBLE : View.GONE);
            }
        } else {
            promptView.setVisibility(View.GONE);
        }
    }
}
