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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.audiolib.DisplayUtils;

// MegaAudio
import org.hyphonate.megaaudio.common.BuilderBase;
import org.hyphonate.megaaudio.common.Globals;
import org.hyphonate.megaaudio.common.StreamBase;
import org.hyphonate.megaaudio.common.StreamState;
import org.hyphonate.megaaudio.player.AudioSourceProvider;
import org.hyphonate.megaaudio.player.OboePlayer;
import org.hyphonate.megaaudio.player.PlayerBuilder;
import org.hyphonate.megaaudio.player.sources.SilenceAudioSourceProvider;
import org.hyphonate.megaaudio.recorder.AudioSinkProvider;
import org.hyphonate.megaaudio.recorder.OboeRecorder;
import org.hyphonate.megaaudio.recorder.RecorderBuilder;
import org.hyphonate.megaaudio.recorder.sinks.NopAudioSinkProvider;

import java.util.ArrayList;

// @CddTest(requirement = "7.8.2.1/C-1-1,C-1-2,C-1-3,C-1-4,C-2-1")

/**
 * CTS Verifier Test module for AAudio Stream Disconnect events
 */
public class AudioDisconnectActivity
        extends PassFailButtons.Activity
        implements View.OnClickListener {
    private static final String TAG = AudioDisconnectActivity.class.getSimpleName();
    private static final boolean DEBUG = false;

    private BroadcastReceiver mPluginReceiver = new PluginBroadcastReceiver();

    // MegaAudio
    private OboePlayer mPlayer;
    private OboeRecorder mRecorder;
    private StreamBase  mStream;

    private int mNumExchangeFrames;
    private int mSystemSampleRate;

    // UI
    private TextView mHasPortQueryText;
    private Button mHasAnalogPortYesBtn;
    private Button mHasAnalogPortNoBtn;

    private Button mStartBtn;
    private Button mStopBtn;

    private TextView mUserPromptTx;
    private TextView mDebugMessageTx;
    private TextView mResultsTx;

    // Test State
    private boolean mHasHeadset;
    private boolean mIsAudioRunning;
    private volatile int mPlugCount;

    static {
        StreamBase.loadMegaAudioLibrary();
    }

    // Lowlatency/not lowlatency
    // MMAP/not MMAP (legacy i.e. audioflinger).
    // Shared/Exclusive (not legacy)
    class TestConfiguration {
        static final int IO_INPUT = 0;
        static final int IO_OUTPUT = 1;
        int mDirection;
        int mSampleRate;
        int mNumChannels;

        static final int OPTION_NONE = 0x00000000;
        static final int OPTION_LOWLATENCY = 0x00000001;
        static final int OPTION_EXCLUSIVE = 0x00000002;
        static final int OPTION_MMAP = 0x00000004;
        int mOptions;

        static final int RESULT_NOTTESTED = -1;
        static final int RESULT_TIMEOUT = -2;
        static final int RESULT_SKIPPED = -3;
        static final int RESULT_DETECTED = 0; // i.e. the disconnect notification was received

        int mInsertPlugResult;
        int mInsertDisconnectResult;
        int mRemovalPlugResult;
        int mRemovalDisconnectResult;

        TestConfiguration(int direction, int sampleRate, int numChannels, int options) {
            mDirection = direction;

            mSampleRate = sampleRate;
            mNumChannels = numChannels;

            mOptions = options;

            mInsertPlugResult = RESULT_NOTTESTED;
            mInsertDisconnectResult = RESULT_NOTTESTED;
            mRemovalPlugResult = RESULT_NOTTESTED;
            mRemovalDisconnectResult = RESULT_NOTTESTED;
        }

        boolean isLowLatency() {
            return (mOptions & OPTION_LOWLATENCY) != 0;
        }

        boolean isExclusive() {
            return (mOptions & OPTION_EXCLUSIVE) != 0;
        }

        boolean isMMap() {
            return (mOptions & OPTION_MMAP) != 0;
        }

        static String resultToString(int resultCode) {
            switch (resultCode) {
                case RESULT_NOTTESTED:
                    return "NT";

                case RESULT_TIMEOUT:
                    return "TO";

                case RESULT_SKIPPED:
                    return "SK";

                case RESULT_DETECTED:
                    return "OK";

                default:
                    return "??";
            }
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();

            sb.append("-----------\n");
            sb.append("" + (mDirection == TestConfiguration.IO_INPUT ? "IN" : "OUT")
                    + " " + mSampleRate + " " + mNumChannels
                    + (isLowLatency() ? " LOW" : "")
                    + (isExclusive() ? " EX" : "")
                    + (isMMap() ? " MMAP" : "")
                    + "\n");
            sb.append("insert:" + resultToString(mInsertPlugResult)
                    + " result:" + resultToString(mInsertDisconnectResult));
            sb.append(" remove:" + resultToString(mRemovalPlugResult)
                    + " result:" + resultToString(mInsertDisconnectResult) + "\n");

            return sb.toString();
        }

        boolean isPass() {
            return (mInsertPlugResult == RESULT_DETECTED
                        || mInsertPlugResult == RESULT_SKIPPED)
                    && (mInsertDisconnectResult == RESULT_DETECTED
                        || mInsertDisconnectResult == RESULT_SKIPPED)
                    && (mRemovalPlugResult == RESULT_DETECTED
                        || mRemovalPlugResult == RESULT_SKIPPED)
                    && (mRemovalDisconnectResult == RESULT_DETECTED
                        || mRemovalDisconnectResult == RESULT_SKIPPED);
        }

        void setSkipped() {
            mInsertPlugResult = RESULT_SKIPPED;
            mInsertDisconnectResult = RESULT_SKIPPED;
            mRemovalPlugResult = RESULT_SKIPPED;
            mRemovalDisconnectResult = RESULT_SKIPPED;
        }
    }

    private ArrayList<TestConfiguration> mTestConfigs = new ArrayList<TestConfiguration>();

    void setTestConfigs() {
//        This logic will cover the four main data paths.
//        if (isMMapSupported) {
//            LOWLATENCY + MMAP + EXCLUSIVE
//            LOWLATENCY + MMAP // shared
//        }
//        LOWLATENCY // legacy
//        NONE


        // Player
        // mTestConfigs.add(new TestConfiguration(true, false, 41000, 2));
        mTestConfigs.add(new TestConfiguration(TestConfiguration.IO_OUTPUT,
                mSystemSampleRate, 2,
                TestConfiguration.OPTION_LOWLATENCY));
        mTestConfigs.add(new TestConfiguration(TestConfiguration.IO_OUTPUT,
                mSystemSampleRate, 2,
                TestConfiguration.OPTION_LOWLATENCY
                        | TestConfiguration.OPTION_MMAP));
        mTestConfigs.add(new TestConfiguration(TestConfiguration.IO_OUTPUT,
                mSystemSampleRate, 2,
                TestConfiguration.OPTION_LOWLATENCY
                        | TestConfiguration.OPTION_MMAP
                        | TestConfiguration.OPTION_EXCLUSIVE));
        mTestConfigs.add(new TestConfiguration(TestConfiguration.IO_OUTPUT,
                mSystemSampleRate, 2,
                TestConfiguration.OPTION_NONE));

        // Recorder
        mTestConfigs.add(new TestConfiguration(TestConfiguration.IO_INPUT,
                mSystemSampleRate, 1,
                TestConfiguration.OPTION_LOWLATENCY));
        mTestConfigs.add(new TestConfiguration(TestConfiguration.IO_INPUT,
                mSystemSampleRate, 1,
                TestConfiguration.OPTION_LOWLATENCY
                        | TestConfiguration.OPTION_MMAP));
        mTestConfigs.add(new TestConfiguration(TestConfiguration.IO_INPUT,
                mSystemSampleRate, 1,
                TestConfiguration.OPTION_LOWLATENCY
                        | TestConfiguration.OPTION_MMAP
                        | TestConfiguration.OPTION_EXCLUSIVE));
        mTestConfigs.add(new TestConfiguration(TestConfiguration.IO_INPUT,
                mSystemSampleRate, 1,
                TestConfiguration.OPTION_NONE));
    }

    void resetTestConfigs() {
        for (TestConfiguration testConfig : mTestConfigs) {
            testConfig.mInsertPlugResult = TestConfiguration.RESULT_NOTTESTED;
            testConfig.mInsertDisconnectResult = TestConfiguration.RESULT_NOTTESTED;
            testConfig.mRemovalPlugResult = TestConfiguration.RESULT_NOTTESTED;
            testConfig.mRemovalDisconnectResult = TestConfiguration.RESULT_NOTTESTED;
        }
    }

    void setTextMessage(TextView textView, String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textView.setText(message);
            }
        });
    }

    class Tester implements Runnable {
        private void runTest() {
            boolean abortTest = false;
            int timeoutCount;
            mPlugCount = 0;

            for (int testConfigIndex = 0;
                    testConfigIndex < mTestConfigs.size();
                    testConfigIndex++) {
                TestConfiguration testConfig = mTestConfigs.get(testConfigIndex);

                if (testConfig.isMMap() || testConfig.isExclusive()) {
                    if (!Globals.isMMapSupported()) {
                        testConfig.setSkipped();
                        continue;
                    }
                }
                startAudio(testConfig);

                // Wait for stream to start...
                setTextMessage(mUserPromptTx, "Waiting for stream to start.");
                try {
                    int oldPlugCount;
                    int error;

                    //
                    // Wait for Stream to start
                    //
                    timeoutCount = TIME_TO_FAILURE_MILLIS / POLL_DURATION_MILLIS;
                    while (!abortTest && timeoutCount-- > 0
                            && mStream.getStreamState() != StreamState.STARTED) {
                        setTextMessage(mDebugMessageTx, "Waiting for stream to start. state:"
                                + mStream.getStreamState() + " count:" + timeoutCount);
                        Thread.sleep(POLL_DURATION_MILLIS);
                    }
                    if (timeoutCount <= 0) {
                        setTextMessage(mUserPromptTx, "TIMEOUT waiting for stream to start");
                        abortTest = true;
                        break;
                    }

                    //
                    // Prompt for headset connect
                    //
                    setTextMessage(mUserPromptTx, "Insert headset now!");

                    // Wait for plug count to change
                    oldPlugCount = mPlugCount;
                    timeoutCount = TIME_TO_FAILURE_MILLIS / POLL_DURATION_MILLIS;
                    while (!abortTest && timeoutCount-- > 0 && mPlugCount == oldPlugCount) {
                        setTextMessage(mDebugMessageTx, "Waiting for plug event "
                                + mPlugCount + ":" + oldPlugCount + " count: " + timeoutCount);
                        Thread.sleep(POLL_DURATION_MILLIS);
                    }
                    if (timeoutCount <= 0) {
                        setTextMessage(mUserPromptTx, "TIMEOUT waiting for plug event");
                        testConfig.mInsertPlugResult = TestConfiguration.RESULT_TIMEOUT;
                        abortTest = true;
                        break;
                    }

                    testConfig.mInsertPlugResult = TestConfiguration.RESULT_DETECTED;

                    // Wait for stream to disconnect.
                    timeoutCount = TIME_TO_FAILURE_MILLIS / POLL_DURATION_MILLIS;
                    while (!abortTest && (timeoutCount > 0)
                            && mStream.getStreamState() == StreamState.STARTED) {
                        setTextMessage(mDebugMessageTx, "state:" + mStream.getStreamState()
                                + " count:" + timeoutCount);
                        Thread.sleep(POLL_DURATION_MILLIS);
                        timeoutCount--;
                    }
                    if (timeoutCount <= 0) {
                        setTextMessage(mUserPromptTx, "TIMEOUT waiting for disconnect");
                        testConfig.mInsertPlugResult = TestConfiguration.RESULT_TIMEOUT;
                        abortTest = true;
                        break;
                    }

                    error = mStream.getLastErrorCallbackResult();
                    if (error != OboePlayer.ERROR_DISCONNECTED) {
                        // Need to address this
                        abortTest = true;
                    }
                    testConfig.mInsertDisconnectResult = TestConfiguration.RESULT_DETECTED;

                    // need to restart the stream
                    restartAudio(testConfig);

                    //
                    // Prompt for headset Remove
                    //
                    setTextMessage(mUserPromptTx, "Remove headset now!");

                    // Wait for plug count to change
                    oldPlugCount = mPlugCount;
                    timeoutCount = TIME_TO_FAILURE_MILLIS / POLL_DURATION_MILLIS;
                    while (!abortTest && timeoutCount-- > 0 && mPlugCount == oldPlugCount) {
                        setTextMessage(mDebugMessageTx, "Waiting for plug event "
                                + mPlugCount + ":" + oldPlugCount + " count: " + timeoutCount);
                        Thread.sleep(POLL_DURATION_MILLIS);
                    }
                    if (timeoutCount <= 0) {
                        setTextMessage(mUserPromptTx, "TIMEOUT waiting for plug event");
                        testConfig.mRemovalPlugResult = TestConfiguration.RESULT_TIMEOUT;
                        abortTest = true;
                        break;
                    }

                    testConfig.mRemovalPlugResult = TestConfiguration.RESULT_DETECTED;

                    // Wait for stream to disconnect.
                    timeoutCount = TIME_TO_FAILURE_MILLIS / POLL_DURATION_MILLIS;
                    while (!abortTest && (timeoutCount > 0)
                            && mStream.getStreamState() == StreamState.STARTED) {
                        setTextMessage(mDebugMessageTx, "state:" + mStream.getStreamState()
                                + " count:" + timeoutCount);
                        Thread.sleep(POLL_DURATION_MILLIS);
                        timeoutCount--;
                    }
                    if (timeoutCount <= 0) {
                        setTextMessage(mUserPromptTx, "TIMEOUT waiting for disconnect");
                        testConfig.mRemovalDisconnectResult = TestConfiguration.RESULT_TIMEOUT;
                        abortTest = true;
                        break;
                    }

                    error = mStream.getLastErrorCallbackResult();
                    if (error != OboePlayer.ERROR_DISCONNECTED) {
                        // Need to address this
                    }
                    testConfig.mRemovalDisconnectResult = TestConfiguration.RESULT_DETECTED;

                } catch (InterruptedException ex) {
                    Log.e(TAG, "InterruptedException: " + ex);
                    abortTest = true;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showTestResults();
                    }
                });
                stopAudio();
            } // while (true)

            endTest();
        }

        public void run() {
            runTest();
        }
    }

    //
    // Test Process
    //
    void startTest() {
        resetTestConfigs();

        enableTestButtons(false, true);

        (new Thread(new Tester())).start();
    }

    void endTest() {
        showTestResults();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mDebugMessageTx.setText("");
                enableTestButtons(true, false);
                boolean passed = calcTestPass();
                getPassButton().setEnabled(passed);
                String passStr = getResources().getString(
                        passed ? R.string.audio_general_teststatus_pass
                               : R.string.audio_general_teststatus_fail);
                mUserPromptTx.setText(passStr);
            }
        });
    }

    void showTestResults() {
        StringBuilder sb = new StringBuilder();

        for (TestConfiguration testConfig : mTestConfigs) {
            if (testConfig.mInsertPlugResult != TestConfiguration.RESULT_NOTTESTED
                    || testConfig.mRemovalPlugResult != TestConfiguration.RESULT_NOTTESTED) {
                sb.append(testConfig.toString());
            }
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mResultsTx.setText(sb.toString());
            }
        });
    }

    boolean calcTestPass() {
        for (TestConfiguration testConfig : mTestConfigs) {
            if (!testConfig.isPass()) {
                return false;
            }
        }
        return true;
    }

    public AudioDisconnectActivity() {
        super();
    }

    // Test Phases
    private static final int TESTPHASE_NONE = -1;
    private static final int TESTPHASE_WAITFORSTART = 0;
    private static final int TESTPHASE_WAITFORCONNECT = 1;
    private int mTestPhase = TESTPHASE_NONE;

    // Test Parameters
    public static final int POLL_DURATION_MILLIS = 50;
    public static final int TIME_TO_FAILURE_MILLIS = 10000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.audio_disconnect_activity);
        super.onCreate(savedInstanceState);

        setInfoResources(R.string.audio_disconnect_test, R.string.audio_disconnect_info, -1);

        // Analog Port?
        mHasPortQueryText = (TextView) findViewById(R.id.analog_headset_query);
        mHasAnalogPortYesBtn = (Button) findViewById(R.id.headset_analog_port_yes);
        mHasAnalogPortYesBtn.setOnClickListener(this);
        mHasAnalogPortNoBtn = (Button) findViewById(R.id.headset_analog_port_no);
        mHasAnalogPortNoBtn.setOnClickListener(this);

        (mStartBtn = (Button) findViewById(R.id.connection_start_btn)).setOnClickListener(this);
        (mStopBtn = (Button) findViewById(R.id.connection_stop_btn)).setOnClickListener(this);

        mUserPromptTx = (TextView) findViewById(R.id.user_prompt_tx);
        mDebugMessageTx = (TextView) findViewById(R.id.debug_message_tx);
        mResultsTx = (TextView) findViewById(R.id.results_tx);

        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        StreamBase.setup(this);
        mSystemSampleRate = StreamBase.getSystemSampleRate();
        mNumExchangeFrames = StreamBase.getNumBurstFrames(BuilderBase.TYPE_NONE);

        setTestConfigs();

        enableTestButtons(false, false);

        DisplayUtils.setKeepScreenOn(this, true);
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        this.registerReceiver(mPluginReceiver, filter);
    }

    @Override
    public void onPause() {
        this.unregisterReceiver(mPluginReceiver);
        super.onPause();
    }

    //
    // PassFailButtons Overrides
    //
    private boolean startAudio(TestConfiguration config) {
        Log.i(TAG, "startAudio()...");
        if (mIsAudioRunning) {
            stopAudio();
        }

        Globals.setMMapEnabled(config.isMMap());
        if (config.mDirection == TestConfiguration.IO_OUTPUT) {
            AudioSourceProvider sourceProvider = new SilenceAudioSourceProvider();
            try {
                PlayerBuilder playerBuilder = new PlayerBuilder();
                playerBuilder.setPerformanceMode(config.isLowLatency()
                        ? BuilderBase.PERFORMANCE_MODE_LOWLATENCY
                        : BuilderBase.PERFORMANCE_MODE_NONE);
                playerBuilder.setSharingMode(config.isExclusive()
                                ? BuilderBase.SHARING_MODE_EXCLUSIVE
                                : BuilderBase.SHARING_MODE_SHARED);
                playerBuilder.setChannelCount(config.mNumChannels);
                playerBuilder.setSampleRate(config.mSampleRate);
                playerBuilder.setSourceProvider(sourceProvider);
                playerBuilder.setPlayerType(BuilderBase.TYPE_OBOE);
                mPlayer = (OboePlayer) playerBuilder.build();
                mPlayer.startStream();
                mIsAudioRunning = true;
                mStream = mPlayer;
            } catch (PlayerBuilder.BadStateException badStateException) {
                Log.e(TAG, "BadStateException: " + badStateException);
                mIsAudioRunning = false;
            }
        } else {
            AudioSinkProvider sinkProvider = new NopAudioSinkProvider();
            try {
                RecorderBuilder recorderBuilder = new RecorderBuilder();
                recorderBuilder.setRecorderType(BuilderBase.TYPE_OBOE);
                recorderBuilder.setAudioSinkProvider(sinkProvider);
                recorderBuilder.setChannelCount(config.mNumChannels);
                recorderBuilder.setSampleRate(config.mSampleRate);
                recorderBuilder.setChannelCount(config.mNumChannels);
                recorderBuilder.setNumExchangeFrames(mNumExchangeFrames);
                recorderBuilder.setPerformanceMode(config.isLowLatency()
                        ? BuilderBase.PERFORMANCE_MODE_LOWLATENCY
                        : BuilderBase.PERFORMANCE_MODE_NONE);
                recorderBuilder.setSharingMode(config.isExclusive()
                        ? BuilderBase.SHARING_MODE_EXCLUSIVE
                        : BuilderBase.SHARING_MODE_SHARED);
                mRecorder = (OboeRecorder) recorderBuilder.build();
                mRecorder.startStream();
                mIsAudioRunning = true;
                mStream = mRecorder;
            } catch (RecorderBuilder.BadStateException badStateException) {
                Log.e(TAG, "BadStateException: " + badStateException);
                mIsAudioRunning = false;
            }
        }
        Globals.setMMapEnabled(Globals.isMMapSupported());

        Log.i(TAG, "  mIsAudioRunning: " + mIsAudioRunning);
        return mIsAudioRunning;
    }

    private boolean restartAudio(TestConfiguration config) {
        mIsAudioRunning = false;
        return startAudio(config);
    }

    private void stopAudio() {
        if (!mIsAudioRunning) {
            return;
        }

        if (mPlayer != null) {
            mPlayer.stopStream();
            mPlayer.teardownStream();
        }

        if (mRecorder != null) {
            mRecorder.stopStream();
            mRecorder.teardownStream();
        }

        mIsAudioRunning = false;
    }

    void enableTestButtons(boolean start, boolean stop) {
        mStartBtn.setEnabled(start);
        mStopBtn.setEnabled(stop);
    }

    void hideHasHeadsetUI() {
        mHasPortQueryText.setText(getResources().getString(
                R.string.analog_headset_port_detected));
        mHasAnalogPortYesBtn.setVisibility(View.GONE);
        mHasAnalogPortNoBtn.setVisibility(View.GONE);
    }

    //
    // View.OnClickHandler
    //
    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.headset_analog_port_yes) {
            enableTestButtons(true, false);
        } else if (id == R.id.headset_analog_port_no) {
            String passStr = getResources().getString(
                    R.string.audio_general_teststatus_pass);
            mUserPromptTx.setText(passStr);
            getPassButton().setEnabled(true);
            enableTestButtons(false, false);
        } else if (id == R.id.connection_start_btn) {
            mResultsTx.setText("");
            startTest();
        } else if (id == R.id.connection_stop_btn) {
            stopAudio();
        }
    }

    /**
     * Receive a broadcast Intent when a headset is plugged in or unplugged.
     * Display a count on screen.
     */
    public class PluginBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!mHasHeadset) {
                mHasHeadset = true;
                hideHasHeadsetUI();
                enableTestButtons(true, false);
            }
            mPlugCount++;
        }
    }
}
