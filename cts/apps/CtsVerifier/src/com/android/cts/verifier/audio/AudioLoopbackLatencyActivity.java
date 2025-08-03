/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.content.res.Resources;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.CtsVerifierReportLog;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.audiolib.AudioSystemFlags;
import com.android.cts.verifier.audio.audiolib.AudioUtils;
import com.android.cts.verifier.audio.audiolib.DisplayUtils;
import com.android.cts.verifier.audio.audiolib.StatUtils;

import org.hyphonate.megaaudio.common.Globals;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * CtsVerifier Audio Loopback Latency Test
 */
@CddTest(requirements = {"5.10/C-1-2,C-1-5", "5.6/H-1-3"})
public class AudioLoopbackLatencyActivity extends PassFailButtons.Activity {
    private static final String TAG = "AudioLoopbackLatencyActivity";

    // JNI load
    static {
        try {
            System.loadLibrary("audioloopback_jni");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Error loading Audio Loopback JNI library");
            Log.e(TAG, "e: " + e);
            e.printStackTrace();
        }

        /* TODO: gracefully fail/notify if the library can't be loaded */
    }
    protected AudioManager mAudioManager;

    // UI
    TextView[] mResultsText = new TextView[NUM_TEST_ROUTES];

    TextView mAudioLevelText;
    SeekBar mAudioLevelSeekbar;

    TextView mTestStatusText;
    ProgressBar mProgressBar;
    int mMaxLevel;

    TextView mTestInstructions;

    private OnBtnClickListener mBtnClickListener = new OnBtnClickListener();
    private Button[] mStartButtons = new Button[NUM_TEST_ROUTES];

    String mYesString;
    String mNoString;

    String mPassString;
    String mFailString;
    String mNotTestedString;
    String mNotRequiredString;
    String mRequiredString;

    // These flags determine the maximum allowed latency
    private boolean mClaimsProAudio;
    private boolean mClaimsLowLatency;
    private boolean mClaimsMediaPerformance;
    private boolean mClaimsOutput;
    private boolean mClaimsInput;

    // Useful info
    private boolean mSupportsMMAP = AudioUtils.isMMapSupported();
    private boolean mSupportsMMAPExclusive = AudioUtils.isMMapExclusiveSupported();

    private boolean mIsWatch;
    private boolean mIsTV;
    private boolean mIsAutomobile;
    private boolean mIsHandheld;
    private int     mSpeakerDeviceId = AudioDeviceInfo.TYPE_UNKNOWN;
    private int     mMicDeviceId = AudioDeviceInfo.TYPE_UNKNOWN;

    // Peripheral(s)
    private static final int NUM_TEST_ROUTES =       3;
    private static final int TESTROUTE_DEVICE =      0; // device speaker + mic
    private static final int TESTROUTE_ANALOG_JACK = 1;
    private static final int TESTROUTE_USB =         2;
    private int mTestRoute = TESTROUTE_DEVICE;

    // Loopback Logic
    private NativeAnalyzerThread mNativeAnalyzerThread = null;

    protected static final int NUM_TEST_PHASES = 5;
    protected int mTestPhase = 0;

    private static final double CONFIDENCE_THRESHOLD_AMBIENT = 0.6;
    private static final double CONFIDENCE_THRESHOLD_WIRED = 0.6;

    public static final double LATENCY_NOT_MEASURED = 0.0;
    public static final double LATENCY_BASIC = 250.0; // chaned from 300 in CDD 15 for VIC
    public static final double LATENCY_PRO_AUDIO_AT_LEAST_ONE = 25.0;
    public static final double LATENCY_PRO_AUDIO_ANALOG = 20.0;
    public static final double LATENCY_PRO_AUDIO_USB = 25.0;
    public static final double LATENCY_MPC_AT_LEAST_ONE = 80.0;

    public static final double TIMESTAMP_ACCURACY_MS = 30.0;

    // The audio stream callback threads should stop and close
    // in less than a few hundred msec. This is a generous timeout value.
    private static final int STOP_TEST_TIMEOUT_MSEC = 2 * 1000;

    private static final String LOG_ERROR_STR = "Could not log metric.";

    private TestSpec[] mTestSpecs = new TestSpec[NUM_TEST_ROUTES];
    class TestSpec {
        private static final String TAG = "AudioLoopbackLatencyActivity.TestSpec";
        // impossibly low latencies (indicating something in the test went wrong).
        protected static final double LOWEST_REASONABLE_LATENCY_MILLIS = 1.0;

        final int mRouteId;

        // runtime assigned device ID
        static final int DEVICEID_NONE = -1;
        int mInputDeviceId;
        int mOutputDeviceId;

        String mDeviceName;

        double[] mLatencyMS = new double[NUM_TEST_PHASES];
        double[] mConfidence = new double[NUM_TEST_PHASES];

        double[] mTimestampLatencyMS = new double[NUM_TEST_PHASES];

        double mMeanLatencyMS;
        double mMeanAbsoluteDeviation;
        double mMeanConfidence;
        double mRequiredConfidence;
        double mMeanTimestampLatencyMS;
        int mSampleRate;
        boolean mIsLowLatencyStream;
        boolean mHas24BitHardwareSupport;
        int mHardwareFormat;

        boolean mRouteAvailable; // Have we seen this route/device at any time
        boolean mRouteConnected; // is the route available NOW
        boolean mTestRun;

        TestSpec(int routeId, double requiredConfidence) {
            mRouteId = routeId;
            mRequiredConfidence = requiredConfidence;

            mInputDeviceId = DEVICEID_NONE;
            mOutputDeviceId = DEVICEID_NONE;

            // Default to true if test not run.
            mHas24BitHardwareSupport = true;
        }

        void startTest() {
            mTestRun = true;

            java.util.Arrays.fill(mLatencyMS, 0.0);
            java.util.Arrays.fill(mConfidence, 0.0);
            java.util.Arrays.fill(mTimestampLatencyMS, 0.0);
        }

        void recordPhase(int phase, double latencyMS, double confidence,
                double timestampLatencyMS) {
            mLatencyMS[phase] = latencyMS;
            mConfidence[phase] = confidence;
            mTimestampLatencyMS[phase] = timestampLatencyMS;
        }

        void handleTestCompletion() {
            mMeanLatencyMS = StatUtils.calculateMean(mLatencyMS);
            mMeanAbsoluteDeviation =
                    StatUtils.calculateMeanAbsoluteDeviation(
                            mMeanLatencyMS, mLatencyMS, mLatencyMS.length);
            mMeanConfidence = StatUtils.calculateMean(mConfidence);
            mMeanTimestampLatencyMS = StatUtils.calculateMean(mTimestampLatencyMS);
            if (mNativeAnalyzerThread != null) {
                mSampleRate = mNativeAnalyzerThread.getSampleRate();
                mIsLowLatencyStream = mNativeAnalyzerThread.isLowLatencyStream();
                mHas24BitHardwareSupport = mNativeAnalyzerThread.has24BitHardwareSupport();
                mHardwareFormat = mNativeAnalyzerThread.getHardwareFormat();
            }
        }

        boolean isMeasurementValid() {
            return mTestRun && mMeanLatencyMS > 1.0 && mMeanConfidence >= mRequiredConfidence;
        }

        boolean has24BitHardwareSupport() {
            return mHas24BitHardwareSupport;
        }

        String getResultString() {
            String result;

            if (!mRouteAvailable) {
                result = "Route Not Available";
            } else if (!mTestRun) {
                result = "Test Not Run";
            } else if (mMeanConfidence < mRequiredConfidence) {
                result = String.format(
                        "Test Finished\nInsufficient Confidence (%.2f < %.2f). No Results.",
                        mMeanConfidence, mRequiredConfidence);
            } else if (mMeanLatencyMS <= LOWEST_REASONABLE_LATENCY_MILLIS) {
                result = String.format(
                        "Test Finished\nLatency unrealistically low (%.2f < %.2f). No Results.",
                        mMeanLatencyMS, LOWEST_REASONABLE_LATENCY_MILLIS);
            } else {
                result = String.format(
                        "Test Finished\nMean Latency:%.2f ms\n"
                                + "Mean Absolute Deviation: %.2f\n"
                                + "Confidence: %.2f\n"
                                + "Low Latency Path: %s\n"
                                + "24 Bit Hardware Support: %s\n"
                                + "Timestamp Latency:%.2f ms",
                        mMeanLatencyMS,
                        mMeanAbsoluteDeviation,
                        mMeanConfidence,
                        mIsLowLatencyStream ? mYesString : mNoString,
                        mHas24BitHardwareSupport ? mYesString : mNoString,
                        mMeanTimestampLatencyMS);
            }

            return result;
        }

        // ReportLog Schema (per route)
        private static final String KEY_ROUTEINDEX = "route_index";
        private static final String KEY_LATENCY = "latency";
        private static final String KEY_CONFIDENCE = "confidence";
        private static final String KEY_MEANABSDEVIATION = "mean_absolute_deviation";
        private static final String KEY_IS_PERIPHERAL_ATTACHED = "is_peripheral_attached";
        private static final String KEY_INPUT_PERIPHERAL_NAME = "input_peripheral";
        private static final String KEY_OUTPUT_PERIPHERAL_NAME = "output_peripheral";
        private static final String KEY_TEST_PERIPHERAL_NAME = "test_peripheral_name";
        private static final String KEY_TIMESTAMP_LATENCY = "timestamp_latency";
        private static final String KEY_SAMPLE_RATE = "sample_rate";
        private static final String KEY_IS_LOW_LATENCY = "is_low_latency";
        private static final String KEY_HAS_24_BIT_HARDWARE_SUPPORT =
                "has_24_bit_hardware_support";
        private static final String KEY_HARDWARE_FORMAT = "hardware_format";

        void recordTestResults(CtsVerifierReportLog reportLog) {
            reportLog.addValue(
                    KEY_ROUTEINDEX,
                    mRouteId,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_LATENCY,
                    mMeanLatencyMS,
                    ResultType.LOWER_BETTER,
                    ResultUnit.MS);

            reportLog.addValue(
                    KEY_CONFIDENCE,
                    mMeanConfidence,
                    ResultType.HIGHER_BETTER,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_MEANABSDEVIATION,
                    mMeanAbsoluteDeviation,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_TEST_PERIPHERAL_NAME,
                    mDeviceName,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_TIMESTAMP_LATENCY,
                    mMeanTimestampLatencyMS,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_SAMPLE_RATE,
                    mSampleRate,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_IS_LOW_LATENCY,
                    mIsLowLatencyStream,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_HAS_24_BIT_HARDWARE_SUPPORT,
                    mHas24BitHardwareSupport,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_HARDWARE_FORMAT,
                    mHardwareFormat,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);
        }

        void addToJson(JSONObject jsonObject) {
            try {
                jsonObject.put(
                        KEY_ROUTEINDEX,
                        mRouteId);

                jsonObject.put(
                        KEY_LATENCY,
                        mMeanLatencyMS);

                jsonObject.put(
                        KEY_CONFIDENCE,
                        mMeanConfidence);

                jsonObject.put(
                        KEY_MEANABSDEVIATION,
                        mMeanAbsoluteDeviation);

                jsonObject.put(
                        KEY_TEST_PERIPHERAL_NAME,
                        mDeviceName);

                jsonObject.put(
                        KEY_TIMESTAMP_LATENCY,
                        mMeanTimestampLatencyMS);

                jsonObject.put(
                        KEY_SAMPLE_RATE,
                        mSampleRate);

                jsonObject.put(
                        KEY_IS_LOW_LATENCY,
                        mIsLowLatencyStream);

                jsonObject.put(
                        KEY_HAS_24_BIT_HARDWARE_SUPPORT,
                        mHas24BitHardwareSupport);

                jsonObject.put(
                        KEY_HARDWARE_FORMAT,
                        mHardwareFormat);
            } catch (JSONException e) {
                Log.e(TAG, LOG_ERROR_STR, e);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.audio_loopback_latency_activity);

        setPassFailButtonClickListeners();
        setInfoResources(R.string.audio_loopback_latency_test, R.string.audio_loopback_info, -1);

        mRequireReportLogToPass = true;

        mClaimsOutput = AudioSystemFlags.claimsOutput(this);
        mClaimsInput = AudioSystemFlags.claimsInput(this);
        mClaimsProAudio = AudioSystemFlags.claimsProAudio(this);
        mClaimsLowLatency = AudioSystemFlags.claimsLowLatencyAudio(this);
        mClaimsMediaPerformance = Build.VERSION.MEDIA_PERFORMANCE_CLASS != 0;
        mIsWatch = AudioSystemFlags.isWatch(this);
        mIsTV = AudioSystemFlags.isTV(this);
        mIsAutomobile = AudioSystemFlags.isAutomobile(this);
        mIsHandheld = AudioSystemFlags.isHandheld(this);

        // Setup test specifications
        double mustLatency;

        // Speaker/Mic Path
        mTestSpecs[TESTROUTE_DEVICE] =
                new TestSpec(TESTROUTE_DEVICE, CONFIDENCE_THRESHOLD_AMBIENT);
        mTestSpecs[TESTROUTE_DEVICE].mRouteAvailable = true;    // Always

        // Analog Jack Path
        mTestSpecs[TESTROUTE_ANALOG_JACK] =
                new TestSpec(TESTROUTE_ANALOG_JACK, CONFIDENCE_THRESHOLD_WIRED);

        // USB Path
        mTestSpecs[TESTROUTE_USB] =
                new TestSpec(TESTROUTE_USB, CONFIDENCE_THRESHOLD_WIRED);

        // Setup UI
        Resources resources = getResources();
        mYesString = resources.getString(R.string.audio_general_yes);
        mNoString = resources.getString(R.string.audio_general_no);
        mPassString = resources.getString(R.string.audio_general_teststatus_pass);
        mFailString = resources.getString(R.string.audio_general_teststatus_fail);
        mNotTestedString = resources.getString(R.string.audio_general_not_tested);
        mNotRequiredString = resources.getString(R.string.audio_general_not_required);
        mRequiredString = resources.getString(R.string.audio_general_required);

        // Pro Audio
        ((TextView) findViewById(R.id.audio_loopback_pro_audio)).setText(
                (mClaimsProAudio ? mYesString : mNoString));

        // Low Latency
        ((TextView) findViewById(R.id.audio_loopback_low_latency)).setText(
                (mClaimsLowLatency ? mYesString : mNoString));

        // Media Performance Class
        ((TextView) findViewById(R.id.audio_loopback_mpc)).setText(
                (mClaimsMediaPerformance ? String.valueOf(Build.VERSION.MEDIA_PERFORMANCE_CLASS)
                        : mNoString));

        // MMAP
        ((TextView) findViewById(R.id.audio_loopback_mmap)).setText(
                (mSupportsMMAP ? mYesString : mNoString));
        ((TextView) findViewById(R.id.audio_loopback_mmap_exclusive)).setText(
                (mSupportsMMAPExclusive ? mYesString : mNoString));

        // Device Type
        ((TextView) findViewById(R.id.audio_loopback_is_watch)).setText(
                (mIsWatch ? mYesString : mNoString));
        ((TextView) findViewById(R.id.audio_loopback_is_TV)).setText(
                (mIsTV ? mYesString : mNoString));
        ((TextView) findViewById(R.id.audio_loopback_is_automobile)).setText(
                (mIsAutomobile ? mYesString : mNoString));
        ((TextView) findViewById(R.id.audio_loopback_is_handheld)).setText(
                (mIsHandheld ? mYesString : mNoString));

        // Individual Test Results
        mResultsText[TESTROUTE_DEVICE] =
                (TextView) findViewById(R.id.audio_loopback_speakermicpath_info);
        mResultsText[TESTROUTE_ANALOG_JACK] =
                (TextView) findViewById(R.id.audio_loopback_headsetpath_info);
        mResultsText[TESTROUTE_USB] =
                (TextView) findViewById(R.id.audio_loopback_usbpath_info);

        mStartButtons[TESTROUTE_DEVICE] =
                (Button) findViewById(R.id.audio_loopback_speakermicpath_btn);
        mStartButtons[TESTROUTE_DEVICE].setOnClickListener(mBtnClickListener);

        mStartButtons[TESTROUTE_ANALOG_JACK] =
                (Button) findViewById(R.id.audio_loopback_headsetpath_btn);
        mStartButtons[TESTROUTE_ANALOG_JACK].setOnClickListener(mBtnClickListener);

        mStartButtons[TESTROUTE_USB] = (Button) findViewById(R.id.audio_loopback_usbpath_btn);
        mStartButtons[TESTROUTE_USB].setOnClickListener(mBtnClickListener);

        findViewById(R.id.audio_loopback_calibrate_button).setOnClickListener(mBtnClickListener);

        mTestInstructions = (TextView) findViewById(R.id.audio_loopback_instructions);

        mAudioManager = getSystemService(AudioManager.class);
        scanPeripheralList(mAudioManager.getDevices(AudioManager.GET_DEVICES_ALL));

        connectLoopbackUI();

        if (mustRunTest()) {
            getPassButton().setEnabled(false);
            enableStartButtons(true);
        } else {
            getPassButton().setEnabled(isReportLogOkToPass());
            enableStartButtons(false);
        }

        mAudioManager.registerAudioDeviceCallback(new ConnectListener(), new Handler());

        showTestInstructions();
        handleTestCompletion(false);

        DisplayUtils.setKeepScreenOn(this, true);
    }

    //
    // UI State
    //
    private void showTestInstructions() {
        if (mustRunTest()) {
            mTestInstructions.setText(getString(R.string.audio_loopback_test_all_paths));
        } else {
            mTestInstructions.setText(getString(R.string.audio_loopback_test_not_required));
        }
    }

    private void enableStartButtons(boolean enable) {
        if (enable) {
            for (int routeId = TESTROUTE_DEVICE; routeId <= TESTROUTE_USB; routeId++) {
                mStartButtons[routeId].setEnabled(mTestSpecs[routeId].mRouteConnected);
            }
        } else {
            for (int routeId = TESTROUTE_DEVICE; routeId <= TESTROUTE_USB; routeId++) {
                mStartButtons[routeId].setEnabled(false);
            }
        }
    }

    private void connectLoopbackUI() {
        mAudioLevelText = (TextView)findViewById(R.id.audio_loopback_level_text);
        mAudioLevelSeekbar = (SeekBar)findViewById(R.id.audio_loopback_level_seekbar);
        mMaxLevel = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        mAudioLevelSeekbar.setMax(mMaxLevel);
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (int)(0.7 * mMaxLevel), 0);
        refreshLevel();

        mAudioLevelSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                        progress, 0);
                Log.i(TAG,"Level set to: " + progress);
                refreshLevel();
            }
        });

        mTestStatusText = (TextView) findViewById(R.id.audio_loopback_status_text);
        mProgressBar = (ProgressBar) findViewById(R.id.audio_loopback_progress_bar);
        showWait(false);
    }

    void startCalibrationDialog() {
        AudioLoopbackCalibrationDialog calibrationDialog =
                new AudioLoopbackCalibrationDialog(this);
        calibrationDialog.show();
    }

    //
    // Peripheral Connection Logic
    //
    void clearDeviceIds() {
        for (TestSpec testSpec : mTestSpecs) {
            testSpec.mInputDeviceId = testSpec.mInputDeviceId = TestSpec.DEVICEID_NONE;
        }
    }

    void clearDeviceConnected() {
        for (TestSpec testSpec : mTestSpecs) {
            testSpec.mRouteConnected = false;
        }
    }

    void scanPeripheralList(AudioDeviceInfo[] devices) {
        clearDeviceIds();
        clearDeviceConnected();

        mSpeakerDeviceId = AudioDeviceInfo.TYPE_UNKNOWN;
        mMicDeviceId = AudioDeviceInfo.TYPE_UNKNOWN;
        for (AudioDeviceInfo devInfo : devices) {
            switch (devInfo.getType()) {
                // TESTROUTE_DEVICE (i.e. Speaker & Mic)
                // This needs to be handled differently. The other devices can be assumed
                // to contain both input & output devices in the same type.
                // For built-in we need to see both TYPES to be sure to have both input & output.
                case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                    mSpeakerDeviceId = devInfo.getId();
                    break;
                case AudioDeviceInfo.TYPE_BUILTIN_MIC:
                    mMicDeviceId = devInfo.getId();
                    break;

                // TESTROUTE_ANALOG_JACK
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                case AudioDeviceInfo.TYPE_AUX_LINE:
                    if (devInfo.isSink()) {
                        mTestSpecs[TESTROUTE_ANALOG_JACK].mOutputDeviceId = devInfo.getId();
                    } else if (devInfo.isSource()) {
                        mTestSpecs[TESTROUTE_ANALOG_JACK].mInputDeviceId = devInfo.getId();
                    }
                    mTestSpecs[TESTROUTE_ANALOG_JACK].mRouteAvailable = true;
                    mTestSpecs[TESTROUTE_ANALOG_JACK].mRouteConnected = true;
                    mTestSpecs[TESTROUTE_ANALOG_JACK].mDeviceName =
                            devInfo.getProductName().toString();
                    break;

                // TESTROUTE_USB
                case AudioDeviceInfo.TYPE_USB_DEVICE:
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                    if (devInfo.isSink()) {
                        mTestSpecs[TESTROUTE_USB].mOutputDeviceId = devInfo.getId();
                    } else if (devInfo.isSource()) {
                        mTestSpecs[TESTROUTE_USB].mInputDeviceId = devInfo.getId();
                    }
                    mTestSpecs[TESTROUTE_USB].mRouteAvailable = true;
                    mTestSpecs[TESTROUTE_USB].mRouteConnected = true;
                    mTestSpecs[TESTROUTE_USB].mDeviceName = devInfo.getProductName().toString();
            }
        }

        // do we have BOTH a Speaker and Mic?
        if (hasInternalPath()) {
            mTestSpecs[TESTROUTE_DEVICE].mOutputDeviceId = mSpeakerDeviceId;
            mTestSpecs[TESTROUTE_DEVICE].mInputDeviceId = mMicDeviceId;
            mTestSpecs[TESTROUTE_DEVICE].mRouteAvailable = true;
            mTestSpecs[TESTROUTE_DEVICE].mRouteConnected = true;
            mTestSpecs[TESTROUTE_DEVICE].mDeviceName =
                    getResources().getString(R.string.audio_loopback_test_internal_devices);
        }

        enableStartButtons(mustRunTest());
    }

    private class ConnectListener extends AudioDeviceCallback {
        ConnectListener() {}

        //
        // AudioDevicesManager.OnDeviceConnectionListener
        //
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            scanPeripheralList(mAudioManager.getDevices(AudioManager.GET_DEVICES_ALL));
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            scanPeripheralList(mAudioManager.getDevices(AudioManager.GET_DEVICES_ALL));
        }
    }

    /**
     * refresh Audio Level seekbar and text
     */
    private void refreshLevel() {
        int currentLevel = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        mAudioLevelSeekbar.setProgress(currentLevel);

        String levelText = String.format("%s: %d/%d",
                getResources().getString(R.string.audio_loopback_level_text),
                currentLevel, mMaxLevel);
        mAudioLevelText.setText(levelText);
    }

    //
    // show active progress bar
    //
    protected void showWait(boolean show) {
        mProgressBar.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
    }

    //
    // Common logging
    //

    @Override
    public String getTestId() {
        return setTestNameSuffix(sCurrentDisplayMode, getClass().getName());
    }

    @Override
    public boolean requiresReportLog() {
        return true;
    }

    @Override
    public String getReportFileName() { return PassFailButtons.AUDIO_TESTS_REPORT_LOG_NAME; }

    @Override
    public final String getReportSectionName() {
        return setTestNameSuffix(sCurrentDisplayMode, "audio_loopback_latency_activity");
    }

    // Global Test-Schema
    private static final String KEY_IS_PRO_AUDIO = "is_pro_audio";
    private static final String KEY_TEST_MMAP = "supports_mmap";
    private static final String KEY_TEST_MMAPEXCLUSIVE = "supports_mmap_exclusive";
    private static final String KEY_LEVEL = "level";

    // Contains the results for all routes
    private static final String KEY_PATHS = "paths";

    private void recordGlobalResults(CtsVerifierReportLog reportLog) {
        int audioLevel = mAudioLevelSeekbar.getProgress();
        reportLog.addValue(
                KEY_LEVEL,
                audioLevel,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_IS_PRO_AUDIO,
                mClaimsProAudio,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_TEST_MMAP,
                mSupportsMMAP,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_TEST_MMAPEXCLUSIVE,
                mSupportsMMAPExclusive,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                Common.KEY_VERSION_CODE,
                Common.VERSION_CODE,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
    }

    private void recordAllRoutes(CtsVerifierReportLog reportLog) {
        JSONArray jsonArray = new JSONArray();
        for (int route = 0; route < NUM_TEST_ROUTES; route++) {
            if (mTestSpecs[route].isMeasurementValid()) {
                JSONObject jsonObject = new JSONObject();
                mTestSpecs[route].addToJson(jsonObject);
                jsonArray.put(jsonObject);
            }
        }

        if (jsonArray.length() > 0) {
            reportLog.addValues(KEY_PATHS, jsonArray);
        }
    }

    @Override
    public void recordTestResults() {
        // Look for a valid route with the minimum latency.
        int bestRoute = -1;
        double minLatency = Double.MAX_VALUE;
        for (int route = 0; route < NUM_TEST_ROUTES; route++) {
            if (mTestSpecs[route].isMeasurementValid()) {
                if (mTestSpecs[route].mMeanLatencyMS < minLatency) {
                    bestRoute = route;
                    minLatency = mTestSpecs[route].mMeanLatencyMS;
                }
            }
        }

        if (bestRoute >= 0) {
            CtsVerifierReportLog reportLog = getReportLog();
            recordGlobalResults(reportLog);
            mTestSpecs[bestRoute].recordTestResults(reportLog);
            recordAllRoutes(reportLog);
            reportLog.submit();
        }
    }

    private void startAudioTest(Handler messageHandler, int testRouteId) {
        enableStartButtons(false);
        mResultsText[testRouteId].setText("Running...");

        mTestRoute = testRouteId;

        mTestSpecs[mTestRoute].startTest();

        getPassButton().setEnabled(false);

        mTestPhase = 0;

        // Set mmap enabled as mmap supported to get best latency
        Globals.setMMapEnabled(Globals.isMMapSupported());

        mNativeAnalyzerThread = new NativeAnalyzerThread(this);
        if (mNativeAnalyzerThread != null) {
            mNativeAnalyzerThread.setMessageHandler(messageHandler);
            // This value matches AAUDIO_INPUT_PRESET_VOICE_RECOGNITION
            mNativeAnalyzerThread.setInputPreset(MediaRecorder.AudioSource.VOICE_RECOGNITION);
            startTestPhase();
        } else {
            Log.e(TAG, "Couldn't allocate native analyzer thread");
            mTestStatusText.setText(getResources().getString(R.string.audio_loopback_failure));
        }
    }

    private void startTestPhase() {
        if (mNativeAnalyzerThread != null) {
            Log.i(TAG, "mTestRoute: " + mTestRoute
                    + " mInputDeviceId: " + mTestSpecs[mTestRoute].mInputDeviceId
                    + " mOutputDeviceId: " + mTestSpecs[mTestRoute].mOutputDeviceId);
            mNativeAnalyzerThread.startTest(
                    mTestSpecs[mTestRoute].mInputDeviceId, mTestSpecs[mTestRoute].mOutputDeviceId);

            // what is this for?
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleTestPhaseCompletion() {
        if (mNativeAnalyzerThread != null && mTestPhase < NUM_TEST_PHASES) {
            double latency = mNativeAnalyzerThread.getLatencyMillis();
            double confidence = mNativeAnalyzerThread.getConfidence();
            double timestampLatency = mNativeAnalyzerThread.getTimestampLatencyMillis();
            TestSpec testSpec = mTestSpecs[mTestRoute];
            testSpec.recordPhase(mTestPhase, latency, confidence, timestampLatency);

            String result = String.format(
                    "Test %d Finished\nLatency: %.2f ms\nConfidence: %.2f\n"
                    + "TimestampLatency: %.2f\n",
                    mTestPhase, latency, confidence, timestampLatency);

            mTestStatusText.setText(result);
            try {
                mNativeAnalyzerThread.stopTest(STOP_TEST_TIMEOUT_MSEC);
                // Thread.sleep(/*STOP_TEST_TIMEOUT_MSEC*/500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }


            mTestPhase++;
            if (mTestPhase >= NUM_TEST_PHASES) {
                handleTestCompletion(true);
            } else {
                startTestPhase();
            }
        }
    }

    private String generateStatusString(
            LoopbackLatencyRequirements requirements, boolean showResult) {

        if (!isReportLogOkToPass()) {
            return getResources().getString(R.string.audio_general_reportlogtest);
        }

        if (!mustRunTest()) {
            return getResources().getString(R.string.audio_loopback_test_non_handheld);
        }

        boolean pass = calcPass(requirements);
        StringBuilder sb = new StringBuilder();
        sb.append(requirements.getResultsString());
        if (showResult) {
            sb.append("\n" + (pass ? mPassString : mFailString));
        }
        return sb.toString();
    }

    private boolean mustRunTest() {
        return mIsHandheld  && hasInternalPath();
    }

    boolean hasInternalPath() {
        return mSpeakerDeviceId != AudioDeviceInfo.TYPE_UNKNOWN
                && mMicDeviceId != AudioDeviceInfo.TYPE_UNKNOWN;
    }

    private boolean calcPass(LoopbackLatencyRequirements requirements) {
        if (!isReportLogOkToPass()) {
            // Can't pass if we can't write the ReportLog
            return false;
        }
        if (!mustRunTest()) {
            // just grant a pass on non-handheld devices
            return true;
        }
        boolean pass = requirements.evaluate(mClaimsProAudio,
                Build.VERSION.MEDIA_PERFORMANCE_CLASS,
                mTestSpecs[TESTROUTE_DEVICE].isMeasurementValid()
                        ? mTestSpecs[TESTROUTE_DEVICE].mMeanLatencyMS : 0.0,
                mTestSpecs[TESTROUTE_ANALOG_JACK].isMeasurementValid()
                        ? mTestSpecs[TESTROUTE_ANALOG_JACK].mMeanLatencyMS :  0.0,
                mTestSpecs[TESTROUTE_USB].isMeasurementValid()
                        ? mTestSpecs[TESTROUTE_USB].mMeanLatencyMS : 0.0,
                mTestSpecs[TESTROUTE_ANALOG_JACK].has24BitHardwareSupport(),
                mTestSpecs[TESTROUTE_USB].has24BitHardwareSupport(),
                mTestSpecs[TESTROUTE_DEVICE].isMeasurementValid()
                        ? mTestSpecs[TESTROUTE_DEVICE].mMeanTimestampLatencyMS : 0.0,
                mTestSpecs[TESTROUTE_ANALOG_JACK].isMeasurementValid()
                        ? mTestSpecs[TESTROUTE_ANALOG_JACK].mMeanTimestampLatencyMS :  0.0,
                mTestSpecs[TESTROUTE_USB].isMeasurementValid()
                        ? mTestSpecs[TESTROUTE_USB].mMeanTimestampLatencyMS : 0.0);

        return pass;
    }

    private void handleTestCompletion(boolean showResult) {
        TestSpec testSpec = mTestSpecs[mTestRoute];
        testSpec.handleTestCompletion();

        // Make sure the test thread is finished. It should already be done.
        if (mNativeAnalyzerThread != null) {
            try {
                mNativeAnalyzerThread.stopTest(STOP_TEST_TIMEOUT_MSEC);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        mResultsText[mTestRoute].setText(testSpec.getResultString());

        LoopbackLatencyRequirements requirements = new LoopbackLatencyRequirements();
        boolean pass = calcPass(requirements);

        getPassButton().setEnabled(pass);

        mTestStatusText.setText(generateStatusString(requirements, showResult));

        showWait(false);
        enableStartButtons(mustRunTest());
    }

    /**
     * handler for messages from audio thread
     */
    private Handler mMessageHandler = new Handler() {
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch(msg.what) {
                case NativeAnalyzerThread.NATIVE_AUDIO_THREAD_MESSAGE_REC_STARTED:
                    Log.v(TAG,"got message native rec started!!");
                    showWait(true);
                    mTestStatusText.setText(String.format("[phase: %d] - Test Running...",
                            (mTestPhase + 1)));
                    break;
                case NativeAnalyzerThread.NATIVE_AUDIO_THREAD_MESSAGE_OPEN_ERROR:
                    Log.v(TAG,"got message native rec can't start!!");
                    mTestStatusText.setText("Test Error opening streams.");
                    handleTestCompletion(true);
                    break;
                case NativeAnalyzerThread.NATIVE_AUDIO_THREAD_MESSAGE_REC_ERROR:
                    Log.v(TAG,"got message native rec can't start!!");
                    mTestStatusText.setText("Test Error while recording.");
                    handleTestCompletion(true);
                    break;
                case NativeAnalyzerThread.NATIVE_AUDIO_THREAD_MESSAGE_REC_COMPLETE_ERRORS:
                    mTestStatusText.setText("Test FAILED due to errors.");
                    handleTestCompletion(true);
                    break;
                case NativeAnalyzerThread.NATIVE_AUDIO_THREAD_MESSAGE_ANALYZING:
                    mTestStatusText.setText(String.format("[phase: %d] - Analyzing ...",
                            mTestPhase + 1));
                    break;
                case NativeAnalyzerThread.NATIVE_AUDIO_THREAD_MESSAGE_REC_COMPLETE:
                    handleTestPhaseCompletion();
                    break;
                default:
                    break;
            }
        }
    };

    private class OnBtnClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            int id = v.getId();
            if (id == R.id.audio_loopback_speakermicpath_btn) {
                startAudioTest(mMessageHandler, TESTROUTE_DEVICE);
            } else if (id == R.id.audio_loopback_headsetpath_btn) {
                startAudioTest(mMessageHandler, TESTROUTE_ANALOG_JACK);
            }  else if (id == R.id.audio_loopback_usbpath_btn) {
                startAudioTest(mMessageHandler, TESTROUTE_USB);
            } else if (id == R.id.audio_loopback_calibrate_button) {
                startCalibrationDialog();
            }
        }
    }

    class LoopbackLatencyRequirements {
        public static final int MPC_NONE = 0;
        public static final int MPC_R = Build.VERSION_CODES.R;
        public static final int MPC_S = Build.VERSION_CODES.S;
        public static final int MPC_T = Build.VERSION_CODES.TIRAMISU;

        String mResultsString = new String();

        String getResultsString() {
            return mResultsString;
        }

        static final int RESULTCODE_NONE = 0;
        static final int RESULTCODE_PASS = 1;
        static final int RESULTCODE_FAIL_NOINTERNAL = 2;
        static final int RESULTCODE_FAIL_BASIC = 3;
        static final int RESULTCODE_FAIL_MPC = 4;
        static final int RESULTCODE_FAIL_PROONEPATH = 5;
        static final int RESULTCODE_FAIL_PROLIMITS_ANALOG = 6;
        static final int RESULTCODE_FAIL_PROLIMITS_USB = 7;
        static final int RESULTCODE_FAIL_24BIT = 8;
        static final int RESULTCODE_FAIL_PRO_NOWIRED = 9;
        static final int RESULTCODE_WARNING_TIMESTAMP = 10;
        int mResultCode = 0;

        private boolean checkLatency(double measured, double limit) {
            return measured == LATENCY_NOT_MEASURED || measured <= limit;
        }

        private boolean checkTimestampLatencyAccuracy(double measuredLatency,
                double timestampLatency) {
            return (timestampLatency < 0.0) || (measuredLatency == LATENCY_NOT_MEASURED)
                    || (Math.abs(measuredLatency - timestampLatency) <= TIMESTAMP_ACCURACY_MS);
        }

        private void setResultCode(boolean pass, int code) {
            // only set the first non-none result code
            Log.i(TAG, "setResultCode(" + pass + ", " + code + ")");
            if (!pass && mResultCode == RESULTCODE_NONE) {
                mResultCode = code;
            }
        }

        public String getResultCodeText() {
            Resources resources = getResources();
            switch (mResultCode) {
                case RESULTCODE_NONE:
                    return resources.getString(R.string.audio_loopback_resultcode_none);
                case RESULTCODE_PASS:
                    return resources.getString(R.string.audio_loopback_resultcode_pass);
                case RESULTCODE_FAIL_NOINTERNAL:
                    return resources.getString(
                            R.string.audio_loopback_resultcode_nointernal);
                case RESULTCODE_FAIL_BASIC:
                    return resources.getString(
                            R.string.audio_loopback_resultcode_failbasic);
                case RESULTCODE_FAIL_MPC:
                    return resources.getString(R.string.audio_loopback_resultcode_failmpc);
                case RESULTCODE_FAIL_PROONEPATH:
                    return resources.getString(R.string.audio_loopback_resultcode_failpro);
                case RESULTCODE_FAIL_PROLIMITS_ANALOG:
                    return resources.getString(R.string.audio_loopback_resultcode_failproanalog);
                case RESULTCODE_FAIL_PROLIMITS_USB:
                    return resources.getString(R.string.audio_loopback_resultcode_failprousb);
                case RESULTCODE_FAIL_24BIT:
                    return resources.getString(R.string.audio_loopback_resultcode_fail24bit);
                case RESULTCODE_FAIL_PRO_NOWIRED:
                    return resources.getString(
                            R.string.audio_loopback_resultcode_failproaudiowired);
                case RESULTCODE_WARNING_TIMESTAMP:
                    return resources.getString(R.string.audio_loopback_resultcode_warningtimestamp);
                default:
                    // this should never happen
                    return resources.getString(R.string.audio_loopback_resultcode_invalid);
            }
        }

        public boolean evaluate(boolean proAudio,
                                       int mediaPerformanceClass,
                                       double deviceLatency,
                                       double analogLatency,
                                       double usbLatency,
                                       boolean analog24BitHardwareSupport,
                                       boolean usb24BitHardwareSupport,
                                       double deviceTimestampLatency,
                                       double analogTimestampLatency,
                                       double usbTimestampLatency) {

            Log.i(TAG, "evaluate()");
            mResultCode = RESULTCODE_NONE;

            // Required to test the Mic/Speaker path
            boolean internalPathRun = deviceLatency != LATENCY_NOT_MEASURED;
            Log.i(TAG, "  internalPathRun:" + internalPathRun);
            setResultCode(internalPathRun, RESULTCODE_FAIL_NOINTERNAL);

            // All devices must be under the basic limit.
            boolean basicPass = checkLatency(deviceLatency, LATENCY_BASIC)
                    && checkLatency(analogLatency, LATENCY_BASIC)
                    && checkLatency(usbLatency, LATENCY_BASIC);
            Log.i(TAG, "  basicPass:" + basicPass);
            setResultCode(basicPass, RESULTCODE_FAIL_BASIC);

            // For Media Performance Class T the RT latency must be <= 80 msec on one path.
            boolean mpcAtLeastOnePass;
            if (mClaimsMediaPerformance) {
                mpcAtLeastOnePass =
                    (mediaPerformanceClass < MPC_T)
                            || checkLatency(deviceLatency, LATENCY_MPC_AT_LEAST_ONE)
                            || checkLatency(analogLatency, LATENCY_MPC_AT_LEAST_ONE)
                            || checkLatency(usbLatency, LATENCY_MPC_AT_LEAST_ONE);
            } else {
                mpcAtLeastOnePass = true;
            }
            Log.i(TAG, "  mpcAtLeastOnePass:" + mpcAtLeastOnePass);
            setResultCode(mpcAtLeastOnePass, RESULTCODE_FAIL_MPC);

            // For ProAudio, the RT latency must be <= 25 msec on one path.
            boolean proAudioAtLeastOnePass = !proAudio
                    || checkLatency(deviceLatency, LATENCY_PRO_AUDIO_AT_LEAST_ONE)
                    || checkLatency(analogLatency, LATENCY_PRO_AUDIO_AT_LEAST_ONE)
                    || checkLatency(usbLatency, LATENCY_PRO_AUDIO_AT_LEAST_ONE);
            Log.i(TAG, "  proAudioAtLeastOnePass:" + proAudioAtLeastOnePass);
            setResultCode(proAudioAtLeastOnePass, RESULTCODE_FAIL_PROONEPATH);

            // For ProAudio, analog and USB have specific limits
            boolean proAudioLimitsPass = !proAudio;
            if (proAudio) {
                if (analogLatency > 0.0) {
                    proAudioLimitsPass = analogLatency <= LATENCY_PRO_AUDIO_ANALOG;
                    setResultCode(proAudioLimitsPass, RESULTCODE_FAIL_PROLIMITS_ANALOG);
                } else if (usbLatency > 0.0) {
                    // USB audio must be supported if 3.5mm jack not supported
                    proAudioLimitsPass =  usbLatency <= LATENCY_PRO_AUDIO_USB;
                    setResultCode(proAudioLimitsPass, RESULTCODE_FAIL_PROLIMITS_USB);
                }
            }

            if (!proAudioLimitsPass) {
                setResultCode(false, RESULTCODE_FAIL_PRO_NOWIRED);
            }

            // For Media Performance Class T, usb and analog should support >=24 bit audio.
            boolean has24BitHardwareSupportPass = (mediaPerformanceClass < MPC_T)
                    || (analog24BitHardwareSupport && usb24BitHardwareSupport);
            Log.i(TAG, "  has24BitHardwareSupportPass:" + has24BitHardwareSupportPass);
            setResultCode(has24BitHardwareSupportPass, RESULTCODE_FAIL_24BIT);

            // Timestamp latencies must be accurate enough.
            boolean timestampPass =
                    checkTimestampLatencyAccuracy(deviceLatency, deviceTimestampLatency)
                    && checkTimestampLatencyAccuracy(analogLatency, analogTimestampLatency)
                    && checkTimestampLatencyAccuracy(usbLatency, usbTimestampLatency);
            Log.i(TAG, "  timestampPass:" + timestampPass);
            setResultCode(timestampPass, RESULTCODE_WARNING_TIMESTAMP);

            boolean pass =
                    internalPathRun
                    && basicPass
                    && mpcAtLeastOnePass
                    && proAudioAtLeastOnePass
                    && proAudioLimitsPass
                    && has24BitHardwareSupportPass;
            Log.i(TAG, "  pass:" + pass);

            // Build the results explanation
            StringBuilder sb = new StringBuilder();
            if (proAudio) {
                sb.append("[Pro Audio]");
            } else if (mediaPerformanceClass != MPC_NONE) {
                sb.append("[MPC " + mediaPerformanceClass + "]");
            } else {
                sb.append("[Basic Audio]");
            }
            sb.append(" ");

            sb.append("\nSpeaker/Mic: " + (deviceLatency != LATENCY_NOT_MEASURED
                    ? String.format("%.2fms ", deviceLatency)
                    : (mNotTestedString + " - " + mRequiredString)));
            sb.append("\nHeadset: " + (analogLatency != LATENCY_NOT_MEASURED
                    ? String.format("%.2fms ", analogLatency)
                    : (mNotTestedString + " - " + mNotRequiredString)));
            sb.append("\nUSB: " + (usbLatency != LATENCY_NOT_MEASURED
                    ? String.format("%.2fms ", usbLatency)
                    : (mNotTestedString + " - " + mNotRequiredString)));

            mResultsString = sb.toString();
            if (mResultCode > RESULTCODE_PASS) {
                mResultsString = mResultsString + "\n" + getResultCodeText();
            }

            return pass;
        }
    }
}
