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

import android.mediapc.cts.common.PerformanceClassEvaluator;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.CtsVerifierReportLog;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.analyzers.TapLatencyAnalyzer;
import com.android.cts.verifier.audio.audiolib.AudioSystemFlags;
import com.android.cts.verifier.audio.audiolib.CircularBufferFloat;
import com.android.cts.verifier.audio.audiolib.DisplayUtils;
import com.android.cts.verifier.audio.audiolib.StatUtils;
import com.android.cts.verifier.audio.audiolib.WaveformView;
import com.android.cts.verifier.audio.sources.BlipAudioSourceProvider;

import org.hyphonate.megaaudio.common.BuilderBase;
import org.hyphonate.megaaudio.common.StreamBase;
import org.hyphonate.megaaudio.duplex.DuplexAudioManager;
import org.hyphonate.megaaudio.player.AudioSource;
import org.hyphonate.megaaudio.player.AudioSourceProvider;
import org.hyphonate.megaaudio.recorder.AudioSinkProvider;
import org.hyphonate.megaaudio.recorder.sinks.AppCallback;
import org.hyphonate.megaaudio.recorder.sinks.AppCallbackAudioSinkProvider;
import org.junit.rules.TestName;

import java.util.Locale;

/**
 * CtsVerifier test to measure tap-to-tone latency.
 */
@CddTest(requirement = "5.6")
public class AudioTap2ToneActivity
        extends PassFailButtons.Activity
        implements View.OnClickListener, AppCallback {
    private static final String TAG = "AudioTap2ToneActivity";

    private boolean mHasMic;
    private boolean mHasSpeaker;

    private boolean mIsRecording;

    private int mApi = BuilderBase.TYPE_OBOE | BuilderBase.SUB_TYPE_OBOE_DEFAULT;

    private DuplexAudioManager mDuplexAudioManager;
    private AudioSource mBlipSource;

    private Button mStartBtn;
    private Button mStopBtn;

    private TextView mBuffSizeView;
    private TextView mSpecView;
    private TextView mResultsView;
    private TextView mStatsView;
    private TextView mPhaseView;

    private WaveformView mWaveformView;

    // Test Constants are from OboeTester.AudioMidiTester
    private static final float MAX_TOUCH_LATENCY = 0.200f;
    private static final float MAX_OUTPUT_LATENCY = 0.600f;
    private static final float ANALYSIS_TIME_MARGIN = 0.250f;

    private static final float ANALYSIS_TIME_DELAY = MAX_OUTPUT_LATENCY;
    private static final float ANALYSIS_TIME_TOTAL = MAX_TOUCH_LATENCY + MAX_OUTPUT_LATENCY;
    private static final float ANALYSIS_TIME_MAX = ANALYSIS_TIME_TOTAL + ANALYSIS_TIME_MARGIN;
    private static final int ANALYSIS_SAMPLE_RATE = 48000; // need not match output rate

    private static final int NUM_RECORD_CHANNELS = 1;

    private CircularBufferFloat mInputBuffer;

    private Runnable mAnalysisTask;
    private int mTaskCountdown;

    private TapLatencyAnalyzer mTapLatencyAnalyzer = new TapLatencyAnalyzer();

    // Stats for latency
    private double mMaxRequiredLatency;

    // REQUIRED CDD  [5.6/H-1-2]
    private static final int MAX_TAP_2_TONE_LATENCY_BASIC = 300;  // ms
    // Requirement for "R" and "S"
    private static final int MAX_TAP_2_TONE_LATENCY_RS = 100;  // ms
    // Requirement for "T" (or later)
    private static final int MAX_TAP_2_TONE_LATENCY_T     = 80;   // ms
    // Requirement for any builds declaring "ProAudio" and "LowLatency"
    private static final int MAX_TAP_2_TONE_LATENCY_PRO     = 80;   // ms
    private static final int MAX_TAP_2_TONE_LATENCY_LOW     = 80;   // ms

    // Test API (back-end) IDs
    private static final int NUM_TEST_APIS = 2;
    private static final int TEST_API_NATIVE = 0;
    private static final int TEST_API_JAVA = 1;
    private int mActiveTestAPI = TEST_API_NATIVE;

    private int[] mNumMeasurements = new int[NUM_TEST_APIS];
    private int[] mLatencySumSamples = new int[NUM_TEST_APIS];
    private double[] mLatencyMin = new double[NUM_TEST_APIS];   // ms
    private double[] mLatencyMax = new double[NUM_TEST_APIS];   // ms
    private double[] mLatencyAve = new double[NUM_TEST_APIS];   // ms

    // Test State
    private static final int NUM_TEST_PHASES = 5;
    private int mTestPhase;
    private boolean mArmed = true;  // OK to fire another beep

    private double[] mLatencyMillis = new double[NUM_TEST_PHASES];

    public final TestName testName = new TestName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.audio_tap2tone_activity);

        super.onCreate(savedInstanceState);

        mHasMic = AudioSystemFlags.claimsInput(this);
        mHasSpeaker = AudioSystemFlags.claimsOutput(this);

        // Setup UI
        String yesString = getResources().getString(R.string.audio_general_yes);
        String noString = getResources().getString(R.string.audio_general_no);

        mRequireReportLogToPass = true;

        boolean claimsProAudio = AudioSystemFlags.claimsProAudio(this);
        boolean claimsLowLatencyAudio = AudioSystemFlags.claimsLowLatencyAudio(this);

        ((TextView) findViewById(R.id.audio_t2t_version))
                .setText(String.format(Locale.getDefault(),
                         getString(R.string.version_number_format),
                         Common.VERSION_CODE));
        ((TextView) findViewById(R.id.audio_t2t_mic))
                .setText(mHasMic ? yesString : noString);
        ((TextView) findViewById(R.id.audio_t2t_speaker))
                .setText(mHasSpeaker ? yesString : noString);
        ((TextView) findViewById(R.id.audio_t2t_pro_audio))
                .setText(claimsProAudio ? yesString : noString);
        ((TextView) findViewById(R.id.audio_t2t_low_latency))
                .setText(claimsLowLatencyAudio ? yesString : noString);

        String mediaPerformanceClassString;
        int mpc = Build.VERSION.MEDIA_PERFORMANCE_CLASS;
        if (mpc >= Build.VERSION_CODES.TIRAMISU) {
            mediaPerformanceClassString = "T (or greater)";
        } else if (mpc == Build.VERSION_CODES.S)  {
            mediaPerformanceClassString = "S";
        } else if (mpc == Build.VERSION_CODES.R) {
            mediaPerformanceClassString = "R";
        } else {
            mediaPerformanceClassString = "other [" + mpc + "]";
            Log.e(TAG, "Unexpected Media Performance Class: " + mpc);
        }
        ((TextView) findViewById(R.id.audio_t2t_mpc)).setText(mediaPerformanceClassString);

        // Note: These tests need to be ordered such that we find the LOWEST allowable latency
        mMaxRequiredLatency = MAX_TAP_2_TONE_LATENCY_BASIC;
        if (claimsProAudio) {
            mMaxRequiredLatency = Math.min(mMaxRequiredLatency, MAX_TAP_2_TONE_LATENCY_PRO);
        }
        if (claimsLowLatencyAudio) {
            mMaxRequiredLatency = Math.min(mMaxRequiredLatency, MAX_TAP_2_TONE_LATENCY_LOW);
        }
        if (mpc == Build.VERSION_CODES.R || mpc == Build.VERSION_CODES.S) {
            mMaxRequiredLatency = Math.min(mMaxRequiredLatency, MAX_TAP_2_TONE_LATENCY_RS);
        }
        if (mpc >= Build.VERSION_CODES.TIRAMISU) {
            mMaxRequiredLatency = Math.min(mMaxRequiredLatency, MAX_TAP_2_TONE_LATENCY_T);
        }

        ((TextView) findViewById(R.id.audio_t2t_required_latency))
                .setText("" + mMaxRequiredLatency + "ms");

        mBuffSizeView = (TextView) findViewById(R.id.audio_t2t_buffer_frames);

        mStartBtn = (Button) findViewById(R.id.tap2tone_startBtn);
        mStartBtn.setOnClickListener(this);
        mStopBtn = (Button) findViewById(R.id.tap2tone_stopBtn);
        mStopBtn.setOnClickListener(this);

        ((RadioButton) findViewById(R.id.audioJavaApiBtn)).setOnClickListener(this);
        RadioButton nativeApiRB = findViewById(R.id.audioNativeApiBtn);
        nativeApiRB.setChecked(true);
        nativeApiRB.setOnClickListener(this);

        ((Button) findViewById(R.id.tap2tone_clearResults)).setOnClickListener(this);

        getPassButton().setOnClickListener(this);

        mSpecView = (TextView) findViewById(R.id.tap2tone_specTxt);
        mResultsView = (TextView) findViewById(R.id.tap2tone_resultTxt);
        mStatsView = (TextView) findViewById(R.id.tap2tone_statsTxt);
        mPhaseView = (TextView) findViewById(R.id.tap2tone_phaseInfo);

        mWaveformView = (WaveformView) findViewById(R.id.tap2tone_waveView);
        // Start a blip test when the waveform view is tapped.
        mWaveformView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                int action = event.getActionMasked();
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_POINTER_DOWN:
                        trigger();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_POINTER_UP:
                        break;
                }
                // Must return true or we do not get the ACTION_MOVE and
                // ACTION_UP events.
                return true;
            }
        });

        setPassFailButtonClickListeners();
        setInfoResources(R.string.audio_tap2tone, R.string.audio_tap2tone_info, -1);

        // Setup analysis
        int numBufferSamples = (int) (ANALYSIS_TIME_MAX * ANALYSIS_SAMPLE_RATE);
        mInputBuffer = new CircularBufferFloat(numBufferSamples);

        // MegaAudio Initialization
        StreamBase.setup(this);

        stopAudio();
        calculateTestPass();

        DisplayUtils.setKeepScreenOn(this, true);
    }

    @Override
    public void onStop() {
        stopAudio();
        super.onStop();
    }

    private void startAudio() {
        if (mIsRecording) {
            return;
        }

        if (mDuplexAudioManager == null) {
            AudioSourceProvider sourceProvider = new BlipAudioSourceProvider();
            AudioSinkProvider sinkProvider = new AppCallbackAudioSinkProvider(this);
            mDuplexAudioManager = new DuplexAudioManager(sourceProvider, sinkProvider);
            mDuplexAudioManager.setNumRecorderChannels(NUM_RECORD_CHANNELS);
        }

        if (mDuplexAudioManager.buildStreams(mApi, BuilderBase.TYPE_JAVA) == StreamBase.OK) {
            mBuffSizeView.setText(
                    getString(R.string.audio_general_play_colon)
                            + mDuplexAudioManager.getNumPlayerBufferFrames()
                            + getString(R.string.audio_general_record_colon)
                            + mDuplexAudioManager.getNumRecorderBufferFrames());
            mDuplexAudioManager.start();

            mBlipSource = (AudioSource) mDuplexAudioManager.getAudioSource();

            mIsRecording = true;
        } else {
            mIsRecording = false;
            mResultsView.setText(getString(R.string.audio_tap2tone_bad_streams));
        }
        enableAudioButtons(!mIsRecording, mIsRecording);
    }

    private void stopAudio() {
        if (mIsRecording) {
            mDuplexAudioManager.stop();
            // is there a teardown method here?
            mIsRecording = false;
        }
        enableAudioButtons(!mIsRecording, mIsRecording);
    }

    private void resetStats() {
        mNumMeasurements[mActiveTestAPI] = 0;
        mLatencySumSamples[mActiveTestAPI] = 0;
        mLatencyMin[mActiveTestAPI] =
            mLatencyMax[mActiveTestAPI] =
            mLatencyAve[mActiveTestAPI] = 0;

        java.util.Arrays.fill(mLatencyMillis, 0.0);

        mTestPhase = 0;
    }

    private void clearResults() {
        resetStats();
        mSpecView.setText("");
        mResultsView.setText("");
        mStatsView.setText("");
    }

    private void enableAudioButtons(boolean enableStart, boolean enableStop) {
        mStartBtn.setEnabled(enableStart);
        mStopBtn.setEnabled(enableStop);
    }

    private void calculateTestPass() {
        if (!mHasMic || !mHasSpeaker) {
            mSpecView.setText("");
            mResultsView.setText(getResources().getString(R.string.audio_tap2tone_noio));
            enableAudioButtons(false, false);
            getPassButton().setEnabled(true);
        } else {
            boolean testCompleted = mTestPhase >= NUM_TEST_PHASES;
            if (!testCompleted) {
                mSpecView.setText(getResources().getString(
                        R.string.audio_general_testnotcompleted));
                getPassButton().setEnabled(false);
                return;
            }

            double averageLatency = mLatencyAve[mActiveTestAPI];
            boolean pass = isReportLogOkToPass()
                    && averageLatency != 0 && averageLatency <= mMaxRequiredLatency;

            if (pass) {
                mSpecView.setText("Average: " + averageLatency + " ms <= "
                        + mMaxRequiredLatency + " ms -- PASS");
            } else if (!isReportLogOkToPass()) {
                mSpecView.setText(getResources().getString(R.string.audio_general_reportlogtest));
            } else {
                mSpecView.setText("Average: " + averageLatency + " ms > "
                        + mMaxRequiredLatency + " ms -- FAIL");
            }
            getPassButton().setEnabled(pass);
        }
    }

    private void trigger() {
        if (mIsRecording) {
            if (mArmed) {
                mArmed = false;

                mBlipSource.trigger();

                // schedule an analysis to start in the near future
                mAnalysisTask = new Runnable() {
                    public void run() {
                        new Thread() {
                            public void run() {
                                analyzeCapturedAudio();
                            }
                        }.start();
                    }
                };
                mTaskCountdown = (int) (mDuplexAudioManager.getRecorder().getSampleRate()
                                            * ANALYSIS_TIME_DELAY);

            }
        }
    }

    /**
     * A holder for analysis results/
     */
    public static class TestResult {
        public float[] samples;
        public float[] filtered;
        public int frameRate;
        public TapLatencyAnalyzer.TapLatencyEvent[] events;
    }

    private void processTest(TestResult result) {
        if (mTestPhase == NUM_TEST_PHASES) {
            mTestPhase--;
        }

        int[] cursors = new int[2];
        cursors[0] = result.events[0].sampleIndex;
        cursors[1] = result.events[1].sampleIndex;
        mWaveformView.setCursorData(cursors);

        int latencySamples = cursors[1] - cursors[0];
        mLatencySumSamples[mActiveTestAPI] += latencySamples;
        mNumMeasurements[mActiveTestAPI]++;

        double latencyMillis = 1000 * latencySamples / result.frameRate;
        mLatencyMillis[mTestPhase] = latencyMillis;

        if (mLatencyMin[mActiveTestAPI] == 0
                || mLatencyMin[mActiveTestAPI] > latencyMillis) {
            mLatencyMin[mActiveTestAPI] = latencyMillis;
        }
        if (mLatencyMax[mActiveTestAPI] == 0
                || mLatencyMax[mActiveTestAPI] < latencyMillis) {
            mLatencyMax[mActiveTestAPI] = latencyMillis;
        }

        mLatencyAve[mActiveTestAPI] = StatUtils.calculateMean(mLatencyMillis);
        double meanAbsoluteDeviation = StatUtils.calculateMeanAbsoluteDeviation(
                mLatencyAve[mActiveTestAPI], mLatencyMillis, mTestPhase + 1);

        mTestPhase++;

        mLatencyAve[mActiveTestAPI] = 1000
                * (mLatencySumSamples[mActiveTestAPI] / mNumMeasurements[mActiveTestAPI])
                / result.frameRate;
        mResultsView.setText("Phase: " + mTestPhase + " : " + latencyMillis
                + " ms, Ave: " + mLatencyAve[mActiveTestAPI] + " ms");
        mStatsView.setText("Deviation: " + String.format("%.2f",meanAbsoluteDeviation));

        mPhaseView.setText("" + mTestPhase + " of " + NUM_TEST_PHASES + " completed.");
    }

    private void analyzeCapturedAudio() {
        if (!mIsRecording) {
            return;
        }
        int sampleRate = mDuplexAudioManager.getRecorder().getSampleRate();
        int numSamples = (int) (sampleRate * ANALYSIS_TIME_TOTAL);
        float[] buffer = new float[numSamples];

        int numRead = mInputBuffer.readMostRecent(buffer);

        TestResult result = new TestResult();
        result.samples = buffer;
        result.frameRate = sampleRate;
        result.events = mTapLatencyAnalyzer.analyze(buffer, 0, numRead);
        result.filtered = mTapLatencyAnalyzer.getFilteredBuffer();

        // This will come in on a background thread, so switch to the UI thread to update the UI.
        runOnUiThread(new Runnable() {
            public void run() {
                if (result.events.length < 2) {
                    mResultsView.setText(
                            getResources().getString(R.string.audio_tap2tone_too_few));
                    mStatsView.setText("");
                } else if (result.events.length > 2) {
                    mResultsView.setText(
                            getResources().getString(R.string.audio_tap2tone_too_many));
                    mStatsView.setText("");
                } else {
                    processTest(result);
                }

                mWaveformView.setSampleData(result.filtered);
                mWaveformView.postInvalidate();

                mArmed = true;

                calculateTestPass();
            }
        });
    }

    //
    // View.OnClickListener overrides
    //
    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.tap2tone_startBtn) {
            startAudio();
        } else if (id == R.id.tap2tone_stopBtn) {
            stopAudio();
        } else if (id == R.id.audioJavaApiBtn) {
            stopAudio();
            clearResults();
            calculateTestPass();
            mApi = BuilderBase.TYPE_JAVA;
            mActiveTestAPI = TEST_API_JAVA;
        } else if (id == R.id.audioNativeApiBtn) {
            stopAudio();
            clearResults();
            calculateTestPass();
            mApi = BuilderBase.TYPE_OBOE | BuilderBase.SUB_TYPE_OBOE_DEFAULT;
            mActiveTestAPI = TEST_API_NATIVE;
        } else if (id == R.id.tap2tone_clearResults) {
            clearResults();
            calculateTestPass();
        }
    }

    @Override
    public boolean requiresReportLog() {
        return true;
    }

    @Override
    public String getReportFileName() {
        return PassFailButtons.AUDIO_TESTS_REPORT_LOG_NAME;
    }

    @Override
    public final String getReportSectionName() {
        return setTestNameSuffix(sCurrentDisplayMode, SECTION_TAP_2_TONE_LATENCY);
    }

    // ReportLog Schema
    // Note that each key will be suffixed with the ID of the API tested
    private static final String SECTION_TAP_2_TONE_LATENCY = "tap_to_tone_latency";
    private static final String KEY_LATENCY_MIN = "latency_min_";
    private static final String KEY_LATENCY_MAX = "latency_max_";
    private static final String KEY_LATENCY_AVE = "latency_ave_";
    private static final String KEY_LATENCY_NUM_MEASUREMENTS = "latency_num_measurements_";

    private void reportTestResultForApi(int api) {
        CtsVerifierReportLog reportLog = getReportLog();
        reportLog.addValue(
                KEY_LATENCY_MIN + api,
                mLatencyMin[api],
                ResultType.NEUTRAL,
                ResultUnit.NONE);
        reportLog.addValue(
                KEY_LATENCY_MAX + api,
                mLatencyMax[api],
                ResultType.NEUTRAL,
                ResultUnit.NONE);
        reportLog.addValue(
                KEY_LATENCY_AVE + api,
                mLatencyAve[api],
                ResultType.NEUTRAL,
                ResultUnit.NONE);
        reportLog.addValue(
                KEY_LATENCY_NUM_MEASUREMENTS + api,
                mNumMeasurements[api],
                ResultType.NEUTRAL,
                ResultUnit.NONE);
    }

    @Override
    public void recordTestResults() {
        reportTestResultForApi(TEST_API_NATIVE);
        reportTestResultForApi(TEST_API_JAVA);

        getReportLog().addValue(
                Common.KEY_VERSION_CODE,
                Common.VERSION_CODE,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        getReportLog().submit();

        recordPerfClassResults();
    }

    /** Records perf class results and returns if mpc is met */
    private void recordPerfClassResults() {
        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(testName);
        PerformanceClassEvaluator.AudioTap2ToneLatencyRequirement r5_6__h_1_1 =
                pce.addR5_6__H_1_1();

        r5_6__h_1_1.setNativeLatency(mLatencyAve[TEST_API_NATIVE]);
        r5_6__h_1_1.setJavaLatency(mLatencyAve[TEST_API_JAVA]);

        pce.submitAndVerify();
    }

     //
    // AppCallback overrides
    //
    @Override
    public void onDataReady(float[] audioData, int numFrames) {
        mInputBuffer.write(audioData);

        // Analysis?
        if (mTaskCountdown > 0) {
            mTaskCountdown -= numFrames;
            if (mTaskCountdown <= 0) {
                mTaskCountdown = 0;
                new Thread(mAnalysisTask).start(); // run asynchronously with audio thread
            }
        }
    }
}
