/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.cts.verifier.TestListActivity.sCurrentDisplayMode;
import static com.android.cts.verifier.TestListAdapter.setTestNameSuffix;

import android.os.Bundle;
import android.util.Log;

import com.android.compatibility.common.util.CddTest;
import com.android.cts.verifier.R;

import org.hyphonate.megaaudio.recorder.Recorder;
import org.hyphonate.megaaudio.recorder.RecorderBuilder;
import org.hyphonate.megaaudio.recorder.sinks.AppCallback;
import org.hyphonate.megaaudio.recorder.sinks.AppCallbackAudioSinkProvider;

/**
 * CTS-Test for cold-start latency measurements
 */
@CddTest(requirement = "5.6/C-3-2")
public class AudioInColdStartLatencyActivity
        extends AudioColdStartBaseActivity {
    private static final String TAG = "AudioInColdStartLatencyActivity";
    private static final boolean DEBUG = false;

    private static final int LATENCY_MS_MUST     = 500; // CDD C-3-2
    private static final int LATENCY_MS_RECOMMEND = 100; // CDD C-SR

    // MegaAudio
    private Recorder mRecorder;

    private long mPreviousCallbackTime;

    private long mNominalCallbackDelta;
    private long mCallbackThresholdTime;
    private long mAccumulatedTime;
    private long mNumCallbacks;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.audio_coldstart_in_activity);
        super.onCreate(savedInstanceState);

        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        setInfoResources(
                R.string.audio_coldstart_inputlbl, R.string.audio_coldstart_input_info, -1);
    }

    @Override
    public String getTestId() {
        return setTestNameSuffix(sCurrentDisplayMode, getClass().getName());
    }

    boolean calcTestResult() {
        boolean pass = mColdStartlatencyMS <= 0 ? false : mColdStartlatencyMS <= LATENCY_MS_MUST;
        getPassButton().setEnabled(pass);
        return pass;
    }

    double calcColdStartLatency() {
        mColdStartlatencyMS = nanosToMs(mPreviousCallbackTime - mPreOpenTime);
        return mColdStartlatencyMS;
    }

    void showInResults() {
        calcTestResult();
        showColdStartLatency();
    }

    @Override
    int getRequiredTimeMS() {
        return LATENCY_MS_MUST;
    }

    @Override
    int getRecommendedTimeMS() {
        return LATENCY_MS_RECOMMEND;
    }

    //
    // Audio Streaming
    //
    @Override
    boolean runAudioTest() {
        mPreviousCallbackTime = 0;
        mAccumulatedTime = 0;
        mNumCallbacks = 0;

        try {
            mPreOpenTime = System.nanoTime();
            RecorderBuilder builder = new RecorderBuilder();
            builder.setAudioSinkProvider(
                    new AppCallbackAudioSinkProvider(new ColdStartAppCallback()))
                .setRecorderType(mAudioApi)
                .setChannelCount(NUM_CHANNELS)
                .setSampleRate(mSampleRate)
                .setNumExchangeFrames(mNumExchangeFrames);
            mRecorder = builder.build();
            mPostOpenTime = System.nanoTime();

            mIsTestRunning = true;
        } catch (RecorderBuilder.BadStateException badStateException) {
            mLatencyTxt.setText("Can't Start Recorder.");
            Log.e(TAG, "BadStateException: " + badStateException);
            mIsTestRunning = false;
        }

        mPreStartTime = System.nanoTime();
        mRecorder.startStream();
        mPostStartTime = System.nanoTime();

        showOpenTime();
        showStartTime();

        if (mIsTestRunning) {
            mStartBtn.setEnabled(false);
            mStopBtn.setEnabled(true);
        }
        return mIsTestRunning;
    }

    @Override
    void stopAudio() {
        if (!mIsTestRunning) {
            return;
        }

        mRecorder.stopStream();
        mRecorder.teardownStream();

        mIsTestRunning = false;

        mStartBtn.setEnabled(true);
        mStopBtn.setEnabled(false);
    }

    // Callback for Recorder
    /*
     * Monitor callbacks until they become consistent (i.e. delta between callbacks is below
     * some threshold like 1/8 the "nominal" callback time). This is defined as the "cold start
     * latency". Calculate that time and display the results.
     */
    class ColdStartAppCallback implements AppCallback {
        public void onDataReady(float[] audioData, int numFrames) {
            mNumCallbacks++;

            long time = System.nanoTime();
            if (mPreviousCallbackTime == 0) {
                mNumExchangeFrames = numFrames;
                mNominalCallbackDelta = (long) ((1000000000.0 * (double) mNumExchangeFrames)
                                            / (double) mSampleRate);
                mCallbackThresholdTime = mNominalCallbackDelta + (mNominalCallbackDelta / 8);
                // update attributes with actual buffer size
                // showAttributes();
                mPreviousCallbackTime = time;
            } else {
                long callbackDeltaTime = time - mPreviousCallbackTime;

                mPreviousCallbackTime = time;
                mAccumulatedTime += callbackDeltaTime;

                if (callbackDeltaTime < mCallbackThresholdTime) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopAudio();
                            updateTestStateButtons();
                            calcColdStartLatency();
                            showInResults();
                        }
                    });
                }
            }
        }
    }
}
