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

import android.media.AudioTimestamp;
import android.os.Bundle;
import android.util.Log;

import com.android.compatibility.common.util.CddTest;
import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.audiolib.SettingsUtils;

import org.hyphonate.megaaudio.player.Player;
import org.hyphonate.megaaudio.player.PlayerBuilder;
import org.hyphonate.megaaudio.player.sources.SilenceAudioSourceProvider;

import java.util.Timer;
import java.util.TimerTask;

/**
 * CTS-Test for cold-start latency measurements
 */
@CddTest(requirement = "5.6/C-1-2")
public class AudioOutColdStartLatencyActivity
        extends AudioColdStartBaseActivity {
    private static final String TAG = "AudioOutColdStartLatencyActivity";
    private static final boolean DEBUG = false;

    private static final int LATENCY_MS_MUST      = 500; // CDD C-1-2
    private static final int LATENCY_MS_RECOMMEND = 100; // CDD C-SR

    // MegaAudio
    private Player mPlayer;

    // Time Stamps
    Timer mTimer;
    private AudioTimestamp mPullTimestamp = new AudioTimestamp();

    private boolean mTouchSoundsActive;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.audio_coldstart_out_activity);
        super.onCreate(savedInstanceState);

        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        setInfoResources(
                R.string.audio_coldstart_outputlbl, R.string.audio_coldstart_output_info, -1);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mTouchSoundsActive = SettingsUtils.areTouchSoundsActive(this);
        updateTestStateButtons();
    }

    @Override
    protected void updateTestStateButtons() {
        String message = getResources().getString(
                mTouchSoundsActive
                        ? R.string.audio_coldstart_touchsounds_message
                        : R.string.audio_coldstart_notouchsounds_message);
        mAttributesTxt.setText(message);
        enableTest(!mTouchSoundsActive && !mIsTestRunning);
        mStopBtn.setEnabled(!mTouchSoundsActive && mIsTestRunning);
    }

    @Override
    public String getTestId() {
        return setTestNameSuffix(sCurrentDisplayMode, getClass().getName());
    }

    private void enableTest(boolean enabled) {
        mStartBtn.setEnabled(enabled);
    }

    private boolean calcTestResult() {
        boolean pass = mColdStartlatencyMS <= 0 ? false : mColdStartlatencyMS <= LATENCY_MS_MUST;
        getPassButton().setEnabled(pass);
        return pass;
    }

    private double calcColdStartLatency(AudioTimestamp timestamp) {
        // how long ago was frame 0?
        long frame0Delta
                = msToNanos((1000.0 * (double)timestamp.framePosition) / (double) mSampleRate);
        long frame0Time = timestamp.nanoTime - frame0Delta;
        double coldStartLatency = frame0Time - mPreOpenTime;

        mColdStartlatencyMS = nanosToMs(coldStartLatency);
        return mColdStartlatencyMS;
    }

    void startOutTimer() {
        TimerTask task = new TimerTask() {
            public void run() {
                boolean gotTimestamp = mPlayer.getTimestamp(mPullTimestamp);
                if (gotTimestamp) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            calcColdStartLatency(mPullTimestamp);
                            stopAudio();
                            calcTestResult();
                            updateTestStateButtons();
                            showColdStartLatency();
                        }
                    });

                } else {
                    Log.e(TAG, "NO TIME STAMP");
                    mLatencyTxt.setText("NO TIME STAMP");
                }

                mTimer = null;
            }
        };

        mTimer = new Timer();
        mTimer.schedule(task, 500L /*delay*/);
    }

    void stopOutTimer() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
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
        try {
            mPreOpenTime = System.nanoTime();
            PlayerBuilder builder = new PlayerBuilder();
            builder.setSourceProvider(new SilenceAudioSourceProvider())
                .setPlayerType(mAudioApi)
                .setChannelCount(NUM_CHANNELS)
                .setSampleRate(mSampleRate)
                .setNumExchangeFrames(mNumExchangeFrames);
            mPlayer = builder.build();
            mPostOpenTime = System.nanoTime();

            mIsTestRunning = true;
        } catch (PlayerBuilder.BadStateException badStateException) {
            Log.e(TAG, "BadStateException: " + badStateException);
            mLatencyTxt.setText("Can't Start Player.");
            mIsTestRunning = false;
        }

        mPreStartTime = System.nanoTime();
        mPlayer.startStream();
        mPostStartTime = System.nanoTime();

        if (mIsTestRunning) {
            startOutTimer();
        }
        return mIsTestRunning;
    }

    @Override
    void stopAudio() {
        if (!mIsTestRunning) {
            return;
        }

        mPlayer.stopStream();
        mPlayer.teardownStream();

        mIsTestRunning = false;

        stopOutTimer();
    }
}
