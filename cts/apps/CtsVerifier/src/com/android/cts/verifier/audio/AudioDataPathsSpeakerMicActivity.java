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

import android.media.AudioDeviceInfo;
import android.os.Bundle;

import com.android.cts.verifier.R;

// MegaAudio
import org.hyphonate.megaaudio.player.AudioSourceProvider;
import org.hyphonate.megaaudio.player.sources.SinAudioSourceProvider;
import org.hyphonate.megaaudio.player.sources.SparseChannelAudioSourceProvider;
import org.hyphonate.megaaudio.recorder.AudioSinkProvider;
import org.hyphonate.megaaudio.recorder.Recorder;
import org.hyphonate.megaaudio.recorder.sinks.AppCallbackAudioSinkProvider;

public class AudioDataPathsSpeakerMicActivity extends AudioDataPathsBaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.audio_datapaths_speakermic);

        super.onCreate(savedInstanceState);

        setInfoResources(R.string.audio_datapaths_speakermic_test,
                R.string.audio_datapaths_speakermic_info, -1);
    }

    void gatherTestModules(TestManager testManager) {
        AudioSourceProvider sinSourceProvider = new SinAudioSourceProvider();

        AudioSourceProvider leftSineSourceProvider = new SparseChannelAudioSourceProvider(
                SparseChannelAudioSourceProvider.CHANNELMASK_LEFT);
        AudioSourceProvider rightSineSourceProvider = new SparseChannelAudioSourceProvider(
                SparseChannelAudioSourceProvider.CHANNELMASK_RIGHT);

        AudioSinkProvider micSinkProvider =
                new AppCallbackAudioSinkProvider(mAnalysisCallbackHandler);

        boolean doMono = true;
        boolean doInputPresets = true;
        boolean doStereo = true;
        boolean doSampleRates = true;
        boolean doSpeakerSafe = true;

        boolean forceFailure = false;

        TestModule testModule;

        //
        // Built-in Speaker/Mic
        //
        // - Mono
        if (doMono) {
            testModule = new TestModule(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 48000, 1,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 48000, 1);
            testModule.setSectionTitle("Mono");
            testModule.setSources(sinSourceProvider, micSinkProvider);
            testModule.setInputPreset(Recorder.INPUT_PRESET_NONE);
            testModule.setDescription("Spkr:1 Mic:1:PRESET_NONE");
            testManager.addTestModule(testModule);

            if (forceFailure) {
                // Failure Case
                testModule = new TestModule(
                        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 48000, 1,
                        AudioDeviceInfo.TYPE_BUILTIN_MIC, 42, 1);
                testModule.setSources(sinSourceProvider, micSinkProvider);
                testModule.setInputPreset(Recorder.INPUT_PRESET_NONE);
                testModule.setDescription("Spkr:1 Mic:1:PRESET_NONE");
                testManager.addTestModule(testModule);
            }
        }

        if (doInputPresets) {
            // These three ALWAYS fail on Pixel. They require special system permissions.
            // INPUT_PRESET_VOICE_UPLINK, INPUT_PRESET_VOICE_DOWNLINK, INPUT_PRESET_VOICE_CALL
            // INPUT_PRESET_REMOTE_SUBMIX requires special system setup
            // INPUT_PRESET_VOICECOMMUNICATION - the aggressive AEC always causes it to fail

            testModule = new TestModule(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 48000, 1,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 48000, 1);
            testModule.setSectionTitle("Input Presets");
            testModule.setSources(sinSourceProvider, micSinkProvider);
            testModule.setInputPreset(Recorder.INPUT_PRESET_DEFAULT);
            testModule.setDescription("Spkr:1 Mic:1:PRESET_DEFAULT");
            testManager.addTestModule(testModule);

            testModule = new TestModule(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 48000, 1,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 48000, 1);
            testModule.setSources(sinSourceProvider, micSinkProvider);
            testModule.setInputPreset(Recorder.INPUT_PRESET_GENERIC);
            testModule.setDescription("Spkr:1 Mic:1:PRESET_GENERIC");
            testManager.addTestModule(testModule);

            testModule = new TestModule(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 48000, 1,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 48000, 1);
            testModule.setSources(sinSourceProvider, micSinkProvider);
            testModule.setInputPreset(Recorder.INPUT_PRESET_UNPROCESSED);
            testModule.setDescription("Spkr:1 Mic:1:PRESET_UNPROCESSED");
            testManager.addTestModule(testModule);

            testModule = new TestModule(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 48000, 1,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 48000, 1);
            testModule.setSources(sinSourceProvider, micSinkProvider);
            testModule.setInputPreset(Recorder.INPUT_PRESET_CAMCORDER);
            testModule.setDescription("Spkr:1 Mic:1:PRESET_CAMCORDER");
            testManager.addTestModule(testModule);

            testModule = new TestModule(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 48000, 1,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 48000, 1);
            testModule.setSources(sinSourceProvider, micSinkProvider);
            testModule.setInputPreset(Recorder.INPUT_PRESET_VOICERECOGNITION);
            testModule.setDescription("Spkr:1 Mic:1:PRESET_VOICERECOGNITION");
            testManager.addTestModule(testModule);

            testModule = new TestModule(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 48000, 1,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 48000, 1);
            testModule.setSources(sinSourceProvider, micSinkProvider);
            testModule.setInputPreset(Recorder.INPUT_PRESET_VOICEPERFORMANCE);
            testModule.setDescription("Spkr:1 Mic:1:PRESET_VOICEPERFORMANCE");
            testManager.addTestModule(testModule);
        }

        // - Stereo, channels individually
        if (doStereo) {
            testModule = new TestModule(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 48000, 2,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 48000, 1);
            testModule.setSectionTitle("Stereo");
            testModule.setSources(leftSineSourceProvider, micSinkProvider);
            testModule.setDescription("Spkr:2:Left Mic:1");
            testManager.addTestModule(testModule);

            testModule = new TestModule(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 48000, 2,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 48000, 1);
            testModule.setSources(rightSineSourceProvider, micSinkProvider);
            testModule.setDescription("Spkr:2:Right Mic:1");
            testManager.addTestModule(testModule);
        }

        //
        // Let's check some sample rates
        //
        if (doSampleRates) {
            testModule = new TestModule(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 11025, 2,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 48000, 1);
            testModule.setSectionTitle("Sample Rates");
            testModule.setSources(sinSourceProvider, micSinkProvider);
            testModule.setDescription("Spkr:2:11025 Mic:1:48000");
            testManager.addTestModule(testModule);

            testModule = new TestModule(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 48000, 2,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 44100, 1);
            testModule.setSources(sinSourceProvider, micSinkProvider);
            testModule.setDescription("Spkr:2:48000 Mic:1:44100");
            testManager.addTestModule(testModule);

            testModule = new TestModule(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 44100, 2,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 48000, 1);
            testModule.setSources(sinSourceProvider, micSinkProvider);
            testModule.setDescription("Spkr:2:44100 Mic:1:48000");
            testManager.addTestModule(testModule);

            testModule = new TestModule(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 96000, 2,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 48000, 1);
            testModule.setSources(sinSourceProvider, micSinkProvider);
            testModule.setDescription("Spkr:2:96000 Mic:1:48000");
            testManager.addTestModule(testModule);
        }

        if (doSpeakerSafe) {
            int speakerSafeSampleRate = 48000;

            // Left
            testModule = new TestModule(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE, speakerSafeSampleRate, 2,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, speakerSafeSampleRate, 1);
            testModule.setSectionTitle("Speaker Safe");
            testModule.setSources(leftSineSourceProvider, micSinkProvider);
            testModule.setDescription("SpeakerSafe:2:Left Mic:1");
            testManager.addTestModule(testModule);

            // Right
            testModule = new TestModule(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE, speakerSafeSampleRate, 2,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, speakerSafeSampleRate, 1);
            testModule.setSources(rightSineSourceProvider, micSinkProvider);
            testModule.setDescription("SpeakerSafe:2:Right Mic:1");
            testManager.addTestModule(testModule);
        }
    }

    void postValidateTestDevices(int numValidTestModules) {

    }

}
