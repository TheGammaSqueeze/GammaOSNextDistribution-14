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

import static com.android.cts.verifier.TestListActivity.sCurrentDisplayMode;
import static com.android.cts.verifier.TestListAdapter.setTestNameSuffix;

import android.graphics.Color;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.TextView;

import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.CtsVerifierReportLog;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.analyzers.BaseSineAnalyzer;
import com.android.cts.verifier.audio.audiolib.AudioDeviceUtils;
import com.android.cts.verifier.audio.audiolib.AudioSystemFlags;
import com.android.cts.verifier.audio.audiolib.DisplayUtils;
import com.android.cts.verifier.audio.audiolib.WaveScopeView;
import com.android.cts.verifier.libs.ui.HtmlFormatter;

// MegaAudio
import org.hyphonate.megaaudio.common.BuilderBase;
import org.hyphonate.megaaudio.common.Globals;
import org.hyphonate.megaaudio.common.StreamBase;
import org.hyphonate.megaaudio.duplex.DuplexAudioManager;
import org.hyphonate.megaaudio.player.AudioSource;
import org.hyphonate.megaaudio.player.AudioSourceProvider;
import org.hyphonate.megaaudio.recorder.AudioSinkProvider;
import org.hyphonate.megaaudio.recorder.sinks.AppCallback;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

/**
 * CtsVerifier test for audio data paths.
 */
public abstract class AudioDataPathsBaseActivity
        extends AudioMultiApiActivity
        implements View.OnClickListener, AppCallback {
    private static final String TAG = "AudioDataPathsActivity";

    // ReportLog Schema
    private static final String SECTION_AUDIO_DATAPATHS = "audio_datapaths";

    protected boolean mHasMic;
    protected boolean mHasSpeaker;

    // This determines whether or not passing all test-modules is required to pass the test overall
    private boolean mIsLessThanV;

    // UI
    private View mStartBtn;
    private View mCancelButton;
    private View mClearResultsBtn;

    private TextView mRoutesTx;
    private WebView mResultsView;

    private WaveScopeView mWaveView = null;

    private  HtmlFormatter mHtmlFormatter = new HtmlFormatter();

    // Test Manager
    protected TestManager mTestManager = new TestManager();
    private boolean mTestHasBeenRun;
    private boolean mTestCanceled;

    // Audio I/O
    private AudioManager mAudioManager;
    private boolean mSupportsMMAP;
    private boolean mSupportsMMAPExclusive;

    protected boolean mHasUsb;
    protected boolean mIsHandheld;

    // Analysis
    private BaseSineAnalyzer mAnalyzer = new BaseSineAnalyzer();

    private DuplexAudioManager mDuplexAudioManager;

    protected AppCallback mAnalysisCallbackHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // MegaAudio Initialization
        StreamBase.setup(this);

        //
        // Header Fields
        //
        mSupportsMMAP = Globals.isMMapSupported();
        mSupportsMMAPExclusive = Globals.isMMapExclusiveSupported();

        mHasMic = AudioSystemFlags.claimsInput(this);
        mHasSpeaker = AudioSystemFlags.claimsOutput(this);

        // Use as a proxy for "has a USB port"
        mHasUsb = AudioSystemFlags.claimsProAudio(this);
        mIsHandheld = AudioSystemFlags.isHandheld(this);

        String yesString = getResources().getString(R.string.audio_general_yes);
        String noString = getResources().getString(R.string.audio_general_no);
        ((TextView) findViewById(R.id.audio_datapaths_mic))
                .setText(mHasMic ? yesString : noString);
        ((TextView) findViewById(R.id.audio_datapaths_speaker))
                .setText(mHasSpeaker ? yesString : noString);
        ((TextView) findViewById(R.id.audio_datapaths_MMAP))
                .setText(mSupportsMMAP ? yesString : noString);
        ((TextView) findViewById(R.id.audio_datapaths_MMAP_exclusive))
                .setText(mSupportsMMAPExclusive ? yesString : noString);

        mIsLessThanV = Build.VERSION.SDK_INT <= Build.VERSION_CODES.UPSIDE_DOWN_CAKE;

        findViewById(R.id.audio_datapaths_calibrate_button).setOnClickListener(this);

        mStartBtn = findViewById(R.id.audio_datapaths_start);
        mStartBtn.setOnClickListener(this);
        mCancelButton = findViewById(R.id.audio_datapaths_cancel);
        mCancelButton.setOnClickListener(this);
        mCancelButton.setEnabled(false);
        mClearResultsBtn = findViewById(R.id.audio_datapaths_clearresults);
        mClearResultsBtn.setOnClickListener(this);

        mRoutesTx = (TextView) findViewById(R.id.audio_datapaths_routes);

        mResultsView = (WebView) findViewById(R.id.audio_datapaths_results);

        mWaveView = (WaveScopeView) findViewById(R.id.uap_recordWaveView);
        mWaveView.setBackgroundColor(Color.DKGRAY);
        mWaveView.setTraceColor(Color.WHITE);

        setPassFailButtonClickListeners();

        mAudioManager = getSystemService(AudioManager.class);

        mAnalysisCallbackHandler = this;

        mTestManager.initializeTests();

        mAudioManager.registerAudioDeviceCallback(new AudioDeviceConnectionCallback(), null);

        DisplayUtils.setKeepScreenOn(this, true);

        getPassButton().setEnabled(!mIsHandheld);
        if (!mIsHandheld) {
            displayNonHandheldMessage();
        }
    }

    @Override
    public void onStop() {
        stopTest();
        super.onStop();
    }

    //
    // UI Helpers
    //
    private void showDeviceView() {
        mRoutesTx.setVisibility(View.VISIBLE);
        mWaveView.setVisibility(View.VISIBLE);

        mResultsView.setVisibility(View.GONE);
    }

    private void showResultsView() {
        mRoutesTx.setVisibility(View.GONE);
        mWaveView.setVisibility(View.GONE);

        mResultsView.setVisibility(View.VISIBLE);
    }

    void enableTestButtons(boolean startEnabled, boolean stopEnabled) {
        mStartBtn.setEnabled(startEnabled);
        mClearResultsBtn.setEnabled(startEnabled);
        mCancelButton.setEnabled(stopEnabled);
    }

    class TestModule implements Cloneable {
        //
        // Analysis Type
        //
        public static final int TYPE_SIGNAL_PRESENCE    = 0;
        public static final int TYPE_SIGNAL_ABSENCE     = 1;
        private int mAnalysisType = TYPE_SIGNAL_PRESENCE;

        //
        // Datapath specifications
        //
        // Playback Specification
        final int mOutDeviceType; // TYPE_BUILTIN_SPEAKER for example
        final int mOutSampleRate;
        final int mOutChannelCount;
        //TODO - Add usage and content types to output stream

        // Device for capturing the (played) signal
        final int mInDeviceType;  // TYPE_BUILTIN_MIC for example
        final int mInSampleRate;
        final int mInChannelCount;
        int mAnalysisChannel = 0;
        int mInputPreset;

        AudioDeviceInfo mOutDeviceInfo;
        AudioDeviceInfo mInDeviceInfo;

        static final int TRANSFER_LEGACY = 0;
        static final int TRANSFER_MMAP_SHARED = 1;
        static final int TRANSFER_MMAP_EXCLUSIVE = 2;
        int mTransferType = TRANSFER_LEGACY;

        public AudioSourceProvider mSourceProvider;
        public AudioSinkProvider mSinkProvider;

        private String mSectionTitle = null;
        private String mDescription = "";

        int[] mTestState;

        TestResults[] mTestResults;

        // Pass/Fail criteria (with defaults)
        static final double MIN_SIGNAL_PASS_MAGNITUDE = 0.01;
        static final double MAX_SIGNAL_PASS_JITTER = 0.1;
        static final double MAX_XTALK_PASS_MAGNITUDE = 0.02;

        TestModule(int outDeviceType, int outSampleRate, int outChannelCount,
                   int inDeviceType, int inSampleRate, int inChannelCount) {
            mOutDeviceType = outDeviceType;
            mOutSampleRate = outSampleRate;
            mOutChannelCount = outChannelCount;

            // Default
            mInDeviceType = inDeviceType;
            mInChannelCount = inChannelCount;
            mInSampleRate = inSampleRate;

            initializeTestState();
        }

        private void initializeTestState() {
            mTestState = new int[NUM_TEST_APIS];
            for (int api = 0; api < NUM_TEST_APIS; api++) {
                mTestState[api] = TestModule.TESTSTATUS_NOT_RUN;
            }
            mTestResults = new TestResults[NUM_TEST_APIS];

        }
        /**
         * We need a more-or-less deep copy so as not to share mTestState and mTestResults
         * arrays. We are only using this to setup closely related test modules, so it is
         * sufficient to initialize mTestState and mTestResults to their "not tested" states.
         *
         * @return The (mostly) cloned TestModule object.
         */
        @Override
        public TestModule clone() throws CloneNotSupportedException {
            // this will clone all the simple data members
            TestModule clonedModule = (TestModule) super.clone();

            // Each clone needs it own set of states and results
            clonedModule.initializeTestState();

            return clonedModule;
        }

        public void setAnalysisType(int type) {
            mAnalysisType = type;
        }

        // Test states that indicate a not run or successful (not failures) test are
        // zero or positive
        // Test states that indicate an executed test that failed are negative.
        public static final int TESTSTATUS_NOT_RUN = 1;
        public static final int TESTSTATUS_RUN = 0;
        public static final int TESTSTATUS_BAD_START = -1;
        public static final int TESTSTATUS_BAD_ROUTING = -2;
        public static final int TESTSTATUS_BAD_ANALYSIS_CHANNEL = -3;
        public static final int TESTSTATUS_BAD_MMAP = -4;
        public static final int TESTSTATUS_BAD_SHARINGMODE = -5;

        void clearTestState(int api) {
            mTestState[api] = TESTSTATUS_NOT_RUN;
            mTestResults[api] = null;
        }

        int getTestState(int api) {
            return mTestState[api];
        }

        int setTestState(int api, int state) {
            return mTestState[api] = state;
        }

        String getOutDeviceName() {
            return AudioDeviceUtils.getDeviceTypeName(mOutDeviceType);
        }

        String getInDeviceName() {
            return AudioDeviceUtils.getDeviceTypeName(mInDeviceType);
        }

        void setSectionTitle(String title) {
            mSectionTitle = title;
        }

        String getSectionTitle() {
            return mSectionTitle;
        }

        void setDescription(String description) {
            mDescription = description;
        }

        String getDescription() {
            switch (mTransferType) {
                case TRANSFER_LEGACY:
                    return mDescription + "-" + getString(R.string.audio_datapaths_legacy);

                case TRANSFER_MMAP_SHARED:
                    return mDescription + "-" + getString(R.string.audio_datapaths_mmap_shared);

                case TRANSFER_MMAP_EXCLUSIVE:
                    return mDescription + "-" + getString(R.string.audio_datapaths_mmap_exclusive);
            }
            return mDescription + "-" + getString(R.string.audio_datapaths_invalid_transfer);
        }

        void setAnalysisChannel(int channel) {
            mAnalysisChannel = channel;
        }

        void setSources(AudioSourceProvider sourceProvider, AudioSinkProvider sinkProvider) {
            mSourceProvider = sourceProvider;
            mSinkProvider = sinkProvider;
        }

        void setInputPreset(int preset) {
            mInputPreset = preset;
        }

        void setTransferType(int type) {
            mTransferType = type;
        }

        boolean canRun() {
            return mInDeviceInfo != null && mOutDeviceInfo != null;
        }

        void setTestResults(int api, BaseSineAnalyzer analyzer) {
            mTestResults[api] = new TestResults(api,
                    analyzer.getMagnitude(),
                    analyzer.getMaxMagnitude(),
                    analyzer.getPhaseOffset(),
                    analyzer.getPhaseJitter());
        }

        //
        // Predicates
        //
        // Ran to completion and results supplied
        boolean hasRun(int api) {
            return mTestResults[api] != null;
        }

        // Ran and passed the criteria
        boolean hasPassed(int api) {
            boolean passed = false;
            if (hasRun(api)) {
                if (mAnalysisType == TYPE_SIGNAL_PRESENCE) {
                    passed = mTestResults[api].mMaxMagnitude >= MIN_SIGNAL_PASS_MAGNITUDE
                            && mTestResults[api].mPhaseJitter <= MAX_SIGNAL_PASS_JITTER;
                } else {
                    passed = mTestResults[api].mMaxMagnitude <= MAX_XTALK_PASS_MAGNITUDE;
                }
            }
            return passed;
        }

        // Should've been able to run, but ran into errors opening/starting streams
        boolean hasError(int api) {
            // TESTSTATUS_NOT_RUN && TESTSTATUS_RUN are not errors
            return mTestState[api] < 0;
        }

        boolean wasTestValid(int api) {
            return false;
        }

        //
        // UI Helpers
        //
        String getTestStateString(int api) {
            int state = getTestState(api);
            switch (state) {
                case TESTSTATUS_NOT_RUN:
                    return " NOT TESTED";
                case TESTSTATUS_RUN:
                    if (mTestResults[api] == null) {
                        // This can happen when the test sequence is cancelled.
                        return " NO RESULTS";
                    } else {
                        return hasPassed(api) ? " PASS" : " FAIL";
                    }
                case TESTSTATUS_BAD_START:
                    return " BAD START";
                case TESTSTATUS_BAD_ROUTING:
                    return " BAD ROUTE";
                case TESTSTATUS_BAD_ANALYSIS_CHANNEL:
                    return " BAD ANALYSIS CHANNEL";
                case TESTSTATUS_BAD_MMAP:
                    return " BAD MMAP MODE";
                case TESTSTATUS_BAD_SHARINGMODE:
                    return " BAD SHARING MODE";
                default:
                    return " UNKNOWN STATE ID [" + state + "]";
            }
        }

        //
        // Process
        //
        // TEMP
        private int startTest(int api) {
            Log.d(TAG, "startTest(" + api + ") - " + getDescription());
            if (mOutDeviceInfo != null && mInDeviceInfo != null) {
                mAnalyzer.reset();
                mAnalyzer.setSampleRate(mInSampleRate);
                if (mAnalysisChannel < mInChannelCount) {
                    mAnalyzer.setInputChannel(mAnalysisChannel);
                } else {
                    Log.e(TAG, "Invalid analysis channel " + mAnalysisChannel
                            + " for " + mInChannelCount + " input signal.");
                    return setTestState(api, TESTSTATUS_BAD_ANALYSIS_CHANNEL);
                }


                // Player
                mDuplexAudioManager.setSources(mSourceProvider, mSinkProvider);
                mDuplexAudioManager.setPlayerRouteDevice(mOutDeviceInfo);
                mDuplexAudioManager.setPlayerSampleRate(mOutSampleRate);
                mDuplexAudioManager.setNumPlayerChannels(mOutChannelCount);
                mDuplexAudioManager.setPlayerSharingMode(mTransferType == TRANSFER_MMAP_EXCLUSIVE
                        ? BuilderBase.SHARING_MODE_EXCLUSIVE : BuilderBase.SHARING_MODE_SHARED);

                // Recorder
                mDuplexAudioManager.setRecorderRouteDevice(mInDeviceInfo);
                mDuplexAudioManager.setInputPreset(mInputPreset);
                mDuplexAudioManager.setRecorderSampleRate(mInSampleRate);
                mDuplexAudioManager.setNumRecorderChannels(mInChannelCount);
                mDuplexAudioManager.setRecorderSharingMode(mTransferType == TRANSFER_MMAP_EXCLUSIVE
                        ? BuilderBase.SHARING_MODE_EXCLUSIVE : BuilderBase.SHARING_MODE_SHARED);

                boolean enableMMAP = mTransferType != TRANSFER_LEGACY;
                Globals.setMMapEnabled(enableMMAP);
                if (Globals.isMMapEnabled() != enableMMAP) {
                    Log.d(TAG, "  Invalid MMAP request - " + getDescription());
                    Globals.setMMapEnabled(Globals.isMMapSupported());
                    return setTestState(api, TESTSTATUS_BAD_MMAP);
                }
                try {
                    // Open the streams.
                    // Note AudioSources and AudioSinks get allocated at this point
                    mDuplexAudioManager.buildStreams(mAudioApi, mAudioApi);
                } finally {
                    Globals.setMMapEnabled(Globals.isMMapSupported());
                }

                // (potentially) Adjust AudioSource parameters
                AudioSource audioSource = mSourceProvider.getActiveSource();

                // Set the sample rate for the source (the sample rate for the player gets
                // set in the DuplexAudioManager.Builder.
                audioSource.setSampleRate(mOutSampleRate);

                // Adjust the player frequency to match with the quantized frequency
                // of the analyzer.
                audioSource.setFreq((float) mAnalyzer.getAdjustedFrequency());

                mWaveView.setNumChannels(mInChannelCount);

                if (mDuplexAudioManager.start() != StreamBase.OK) {
                    Log.e(TAG, "  Couldn't start duplex streams - " + getDescription());
                    return setTestState(api, TESTSTATUS_BAD_START);
                }

                // Validate routing
                if (!mDuplexAudioManager.validateRouting()) {
                    Log.w(TAG, "  Invalid Routing - " + getDescription());
                    return setTestState(api, TESTSTATUS_BAD_ROUTING);
                }

                // Validate Sharing Mode
                if (!mDuplexAudioManager.validateSharingModes()) {
                    Log.w(TAG, "  Invalid Sharing Mode - " + getDescription());
                    return setTestState(api, TESTSTATUS_BAD_SHARINGMODE);
                }

                return setTestState(api, TESTSTATUS_RUN);
            }

            return setTestState(api, TESTSTATUS_NOT_RUN);
        }

        int advanceTestPhase(int api) {
            return 0;
        }

        //
        // HTML Reporting
        //
        HtmlFormatter generateReport(int api, HtmlFormatter htmlFormatter) {
            // Description
            htmlFormatter.openParagraph()
                    .appendText(getDescription());
            if (hasPassed(api)) {
                htmlFormatter.appendBreak()
                        .openBold()
                        .appendText(getTestStateString(api))
                        .closeBold();
            } else {
                boolean isErrorState = hasError(api);
                if (isErrorState) {
                    htmlFormatter.openTextColor("red");
                }

                htmlFormatter.appendBreak()
                        .openBold()
                        .appendText(getTestStateString(api))
                        .closeBold();
                if (mTestState[api] == TESTSTATUS_NOT_RUN) {
                    htmlFormatter.appendText(mTestCanceled
                            ? " - Test Cancelled" : " - Invalid Route or Sharing Mode");
                }
                if (isErrorState) {
                    htmlFormatter.closeTextColor();
                }
            }

            TestResults results = mTestResults[api];
            if (results != null) {
                // we can get null here if the test was cancelled
                Locale locale = Locale.getDefault();
                String maxMagString = String.format(
                        locale, "mag:%.5f ", results.mMaxMagnitude);
                String phaseJitterString = String.format(
                        locale, "jitter:%.5f ", results.mPhaseJitter);

                boolean passMagnitude = mAnalysisType == TYPE_SIGNAL_PRESENCE
                        ? results.mMaxMagnitude >= MIN_SIGNAL_PASS_MAGNITUDE
                        : results.mMaxMagnitude <= MAX_XTALK_PASS_MAGNITUDE;

                // Do we want a threshold value for jitter in crosstalk tests?
                boolean passJitter =
                        results.mPhaseJitter <= MAX_SIGNAL_PASS_JITTER;

                // Values / Criteria
                // NOTE: The criteria is why the test passed or failed, not what
                // was needed to pass.
                // So, for a cross-talk test, "mag:0.01062 > 0.01000" means that the test
                // failed, because 0.01062 > 0.01000
                htmlFormatter.appendBreak();
                htmlFormatter.openTextColor(passMagnitude ? "black" : "red");
                if (mAnalysisType == TYPE_SIGNAL_PRESENCE) {
                    htmlFormatter.appendText(maxMagString
                            + String.format(locale,
                            passMagnitude ? " >= %.5f " : " < %.5f ",
                            MIN_SIGNAL_PASS_MAGNITUDE));
                } else {
                    htmlFormatter.appendText(maxMagString
                            + String.format(locale,
                            passMagnitude ? " <= %.5f " : " > %.5f ",
                            MAX_XTALK_PASS_MAGNITUDE));
                }
                htmlFormatter.closeTextColor();

                htmlFormatter.openTextColor(passJitter ? "black" : "red");
                if (mAnalysisType == TYPE_SIGNAL_PRESENCE) {
                    htmlFormatter.appendText(phaseJitterString
                                    + String.format(locale, passJitter ? " <= %.5f" : " > %.5f",
                                    MAX_SIGNAL_PASS_JITTER));
                } else {
                    htmlFormatter.appendText(phaseJitterString);
                }
                htmlFormatter.closeTextColor();

                htmlFormatter.appendBreak();
            } // results != null

            htmlFormatter.closeParagraph();

            return htmlFormatter;
        }

        //
        // CTS VerifierReportLog stuff
        //
        // ReportLog Schema
        private static final String KEY_TESTDESCRIPTION = "test_description";
        // Output Specification
        private static final String KEY_OUT_DEVICE_TYPE = "out_device_type";
        private static final String KEY_OUT_DEVICE_NAME = "out_device_name";
        private static final String KEY_OUT_DEVICE_RATE = "out_device_rate";
        private static final String KEY_OUT_DEVICE_CHANS = "out_device_chans";

        // Input Specification
        private static final String KEY_IN_DEVICE_TYPE = "in_device_type";
        private static final String KEY_IN_DEVICE_NAME = "in_device_name";
        private static final String KEY_IN_DEVICE_RATE = "in_device_rate";
        private static final String KEY_IN_DEVICE_CHANS = "in_device_chans";
        private static final String KEY_IN_PRESET = "in_preset";

        void generateReportLog(int api) {
            if (!canRun() || mTestResults[api] == null) {
                return;
            }

            CtsVerifierReportLog reportLog = newReportLog();

            // Description
            reportLog.addValue(
                    KEY_TESTDESCRIPTION,
                    getDescription(),
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            // Output Specification
            reportLog.addValue(
                    KEY_OUT_DEVICE_NAME,
                    getOutDeviceName(),
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_OUT_DEVICE_TYPE,
                    mOutDeviceType,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_OUT_DEVICE_RATE,
                    mOutSampleRate,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_OUT_DEVICE_CHANS,
                    mOutChannelCount,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            // Input Specifications
            reportLog.addValue(
                    KEY_IN_DEVICE_NAME,
                    getInDeviceName(),
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_IN_DEVICE_TYPE,
                    mInDeviceType,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_IN_DEVICE_RATE,
                    mInSampleRate,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_IN_DEVICE_CHANS,
                    mInChannelCount,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_IN_PRESET,
                    mInputPreset,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            // Results
            mTestResults[api].generateReportLog(reportLog);

            reportLog.submit();
        }
    }

    /*
     * TestResults
     */
    class TestResults {
        int mApi;
        double mMagnitude;
        double mMaxMagnitude;
        double mPhase;
        double mPhaseJitter;

        TestResults(int api, double magnitude, double maxMagnitude, double phase,
                    double phaseJitter) {
            mApi = api;
            mMagnitude = magnitude;
            mMaxMagnitude = maxMagnitude;
            mPhase = phase;
            mPhaseJitter = phaseJitter;
        }

        // ReportLog Schema
        private static final String KEY_TESTAPI = "test_api";
        private static final String KEY_MAXMAGNITUDE = "max_magnitude";
        private static final String KEY_PHASEJITTER = "phase_jitter";

        void generateReportLog(CtsVerifierReportLog reportLog) {
            reportLog.addValue(
                    KEY_TESTAPI,
                    mApi,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_MAXMAGNITUDE,
                    mMaxMagnitude,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_PHASEJITTER,
                    mPhaseJitter,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);
        }
    }

    abstract void gatherTestModules(TestManager testManager);

    abstract void postValidateTestDevices(int numValidTestModules);

    /*
     * TestManager
     */
    class TestManager {
        static final String TAG = "TestManager";

        // Audio Device Type ID -> TestProfile
        private ArrayList<TestModule> mTestModules = new ArrayList<TestModule>();

        public int mApi;

        private int    mPhaseCount;

        // which route are we running
        static final int TESTSTEP_NONE = -1;
        private int mTestStep = TESTSTEP_NONE;

        private Timer mTimer;

        public void initializeTests() {
            // Get the test modules from the sub-class
            gatherTestModules(this);

            validateTestDevices();
            displayTestDevices();
        }

        public void clearTestState() {
            for (TestModule module: mTestModules) {
                module.clearTestState(mApi);
            }
        }

        public void addTestModule(TestModule module) {
            // We're going to expand each module to three, one for each transfer type
            module.setTransferType(TestModule.TRANSFER_LEGACY);
            mTestModules.add(module);

            if (mSupportsMMAP) {
                try {
                    TestModule moduleMMAP = module.clone();
                    moduleMMAP.setTransferType(TestModule.TRANSFER_MMAP_SHARED);
                    mTestModules.add(moduleMMAP);
                } catch (CloneNotSupportedException ex) {
                    Log.e(TAG, "Couldn't clone TestModule - TRANSFER_MMAP_SHARED");
                }
            }

            if (mSupportsMMAPExclusive) {
                try {
                    TestModule moduleExclusive = module.clone();
                    moduleExclusive.setTransferType(TestModule.TRANSFER_MMAP_EXCLUSIVE);
                    mTestModules.add(moduleExclusive);
                } catch (CloneNotSupportedException ex) {
                    Log.e(TAG, "Couldn't clone TestModule - TRANSFER_MMAP_EXCLUSIVE");
                }
            }
        }

        public void validateTestDevices() {
            // do we have the output device we need
            AudioDeviceInfo[] outputDevices =
                    mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for (TestModule testModule : mTestModules) {
                // Check to see if we have a (physical) device of this type
                for (AudioDeviceInfo devInfo : outputDevices) {
                    testModule.mOutDeviceInfo = null;
                    if (testModule.mOutDeviceType == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                            && !mHasSpeaker) {
                        break;
                    } else if (testModule.mOutDeviceType == devInfo.getType()) {
                        testModule.mOutDeviceInfo = devInfo;
                        break;
                    }
                }
            }

            // do we have the input device we need
            AudioDeviceInfo[] inputDevices =
                    mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
            for (TestModule testModule : mTestModules) {
                // Check to see if we have a (physical) device of this type
                for (AudioDeviceInfo devInfo : inputDevices) {
                    testModule.mInDeviceInfo = null;
                    if (testModule.mInDeviceType == AudioDeviceInfo.TYPE_BUILTIN_MIC
                            && !mHasMic) {
                        break;
                    } else if (testModule.mInDeviceType == devInfo.getType()) {
                        testModule.mInDeviceInfo = devInfo;
                        break;
                    }
                }
            }

            // Is the Transfer Mode valid for this API?
            for (TestModule testModule : mTestModules) {
                if (mApi == TEST_API_JAVA
                        && testModule.mTransferType != TestModule.TRANSFER_LEGACY) {
                    // MMAP transfer modes are not supported on JAVA
                    testModule.mInDeviceInfo = null;
                    testModule.mOutDeviceInfo = null;
                }
            }

            postValidateTestDevices(countValidTestModules());
        }

        public int getNumTestModules() {
            return mTestModules.size();
        }

        public int countValidTestModules() {
            int numValid = 0;
            for (TestModule testModule : mTestModules) {
                if (testModule.mOutDeviceInfo != null && testModule.mInDeviceInfo != null) {
                    numValid++;
                }
            }
            return numValid;
        }

        public int countValidOrPassedTestModules() {
            int numValid = 0;
            for (TestModule testModule : mTestModules) {
                if ((testModule.mOutDeviceInfo != null && testModule.mInDeviceInfo != null)
                        || testModule.hasPassed(mApi)) {
                    numValid++;
                }
            }
            return numValid;
        }

        public int countTestedTestModules() {
            int numTested = 0;
            for (TestModule testModule : mTestModules) {
                if (testModule.hasRun(mApi)) {
                    numTested++;
                }
            }
            return numTested;
        }

        public void displayTestDevices() {
            StringBuilder sb = new StringBuilder();
            sb.append("Tests:");
            int testStep = 0;
            for (TestModule testModule : mTestModules) {
                sb.append("\n");
                if (testModule.getSectionTitle() != null) {
                    sb.append("---" + testModule.getSectionTitle() + "---\n");
                }
                if (testStep == mTestStep) {
                    sb.append(">>>");
                }
                sb.append(testModule.getDescription());

                if (testModule.canRun() && testStep != mTestStep) {
                    sb.append(" *");
                }

                if (testStep == mTestStep) {
                    sb.append("<<<");
                }

                sb.append(testModule.getTestStateString(mApi));
                testStep++;
            }
            mRoutesTx.setText(sb.toString());

            showDeviceView();
        }

        public TestModule getActiveTestModule() {
            return mTestStep != TESTSTEP_NONE && mTestStep < mTestModules.size()
                    ? mTestModules.get(mTestStep)
                    : null;
        }

        private int countFailures(int api) {
            int numFailed = 0;
            for (TestModule module : mTestModules) {
                if (module.canRun() && (module.hasError(api) || !module.hasPassed(api))) {
                    numFailed++;
                }
            }
            return numFailed;
        }

        public int startTest(TestModule testModule) {
            if (mTestCanceled) {
                return TestModule.TESTSTATUS_NOT_RUN;
            }

            return testModule.startTest(mApi);
        }

        private static final int MS_PER_SEC = 1000;
        private static final int TEST_TIME_IN_SECONDS = 2;
        public void startTest(int api) {
            showDeviceView();

            mApi = api;

            mTestStep = TESTSTEP_NONE;
            mTestCanceled = false;

            (mTimer = new Timer()).scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    completeTestStep();
                    advanceTestModule();
                }
            }, 0, TEST_TIME_IN_SECONDS * MS_PER_SEC);
        }

        public void stopTest() {
            if (mTestStep != TESTSTEP_NONE) {
                mTestStep = TESTSTEP_NONE;

                if (mTimer != null) {
                    mTimer.cancel();
                    mTimer = null;
                }
                mDuplexAudioManager.stop();
            }
        }

        protected boolean calculatePass() {
            int numFailures = countFailures(mApi);
            int numUntested = countValidTestModules() - countTestedTestModules();
            return mTestHasBeenRun && !mTestCanceled && numFailures == 0 && numUntested == 0;
        }

        public void completeTest() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    enableTestButtons(true, false);

                    mRoutesTx.setVisibility(View.GONE);
                    mWaveView.setVisibility(View.GONE);

                    mHtmlFormatter.clear();
                    mHtmlFormatter.openDocument();
                    mTestManager.generateReport(mHtmlFormatter);

                    mTestHasBeenRun = true;
                    boolean passEnabled = passBtnEnabled();
                    getPassButton().setEnabled(passEnabled);

                    mHtmlFormatter.openParagraph();
                    if (!mTestCanceled) {
                        int numFailures = countFailures(mApi);
                        int numUntested = getNumTestModules() - countTestedTestModules();
                        mHtmlFormatter.appendText("There were " + numFailures + " failures.");
                        mHtmlFormatter.appendBreak();
                        mHtmlFormatter.appendText(
                                "There were " + numUntested + " untested paths.");

                        if (numFailures == 0 && numUntested == 0) {
                            mHtmlFormatter.appendBreak();
                            mHtmlFormatter.appendText("All tests passed.");
                        }
                        mHtmlFormatter.closeParagraph();
                        mHtmlFormatter.openParagraph();
                    }

                    if (passEnabled) {
                        mHtmlFormatter.appendText("Although not all test modules passed, "
                                + "for this OS version you may enter a PASS.");
                        mHtmlFormatter.appendBreak();
                        mHtmlFormatter.appendText("In future versions, "
                                + "ALL test modules will be required to pass.");
                    }
                    mHtmlFormatter.closeParagraph();

                    mHtmlFormatter.closeDocument();
                    mResultsView.loadData(mHtmlFormatter.toString(),
                            "text/html; charset=utf-8", "utf-8");
                    showResultsView();
                }
            });
        }

        public void completeTestStep() {
            if (mTestStep != TESTSTEP_NONE) {
                mDuplexAudioManager.stop();
                // Give the audio system a chance to settle from the previous state
                // It is often the case that the Java API will not route to the specified
                // device if we teardown/startup too quickly. This sleep cirmumvents that.
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                    Log.e(TAG, "sleep failed?");
                }

                TestModule testModule = getActiveTestModule();
                if (testModule != null && testModule.canRun()) {
                    testModule.setTestResults(mApi, mAnalyzer);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            displayTestDevices();
                            mWaveView.resetPersistentMaxMagnitude();
                        }
                    });
                }
            }
        }

        public void advanceTestModule() {
            if (mTestCanceled) {
                // test shutting down. Bail.
                return;
            }

            while (++mTestStep < mTestModules.size()) {
                // update the display to show progress
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        displayTestDevices();
                    }
                });

                // Scan until we find a TestModule that starts playing/recording
                TestModule testModule = mTestModules.get(mTestStep);
                if (!testModule.hasPassed(mApi)) {
                    int status = startTest(testModule);
                    if (status == TestModule.TESTSTATUS_RUN) {
                        // Allow this test to run to completion.
                        Log.d(TAG, "Run Test Module:" + testModule.getDescription());
                        break;
                    }
                    Log.d(TAG, "Cancel Test Module:" + testModule.getDescription()
                            + " status:" + testModule.getTestStateString(mApi));
                    // Otherwise, playing/recording failed, look for the next TestModule
                    mDuplexAudioManager.stop();
                }
            }

            if (mTestStep >= mTestModules.size()) {
                stopTest();
                completeTest();
            }
        }

        HtmlFormatter generateReport(HtmlFormatter htmlFormatter) {
            for (TestModule module : mTestModules) {
                module.generateReport(mApi, htmlFormatter);
            }

            return htmlFormatter;
        }

        //
        // CTS VerifierReportLog stuff
        //
        void generateReportLog() {
            int testIndex = 0;
            for (TestModule module : mTestModules) {
                for (int api = TEST_API_NATIVE; api < NUM_TEST_APIS; api++) {
                    module.generateReportLog(api);
                }
            }
        }
    }

    //
    // Process Handling
    //
    private void startTest(int api) {
        if (mDuplexAudioManager == null) {
            mDuplexAudioManager = new DuplexAudioManager(null, null);
        }

        enableTestButtons(false, true);
        getPassButton().setEnabled(false);

        mTestManager.startTest(api);
    }

    private void stopTest() {
        mTestManager.stopTest();
        mTestManager.displayTestDevices();

        enableTestButtons(true, false);
    }

    boolean passBtnEnabled() {
        return mIsLessThanV || !mIsHandheld || mTestManager.calculatePass();
    }

    void displayNonHandheldMessage() {
        mHtmlFormatter.clear();
        mHtmlFormatter.openDocument();
        mHtmlFormatter.openParagraph();
        mHtmlFormatter.appendText(getResources().getString(R.string.audio_exempt_nonhandheld));
        mHtmlFormatter.closeParagraph();

        mHtmlFormatter.closeDocument();
        mResultsView.loadData(mHtmlFormatter.toString(),
                "text/html; charset=utf-8", "utf-8");
        showResultsView();
    }

    //
    // PassFailButtons Overrides
    //
    @Override
    public boolean requiresReportLog() {
        return true;
    }

    //
    // CTS VerifierReportLog stuff
    //
    @Override
    public String getReportFileName() {
        return PassFailButtons.AUDIO_TESTS_REPORT_LOG_NAME;
    }

    @Override
    public final String getReportSectionName() {
        return setTestNameSuffix(sCurrentDisplayMode, SECTION_AUDIO_DATAPATHS);
    }

    @Override
    public void recordTestResults() {
// TODO Remove all report logging from this file. This is a quick fix.
// This code generates multiple records in the JSON file.
// That duplication is invalid JSON and causes the database
// ingestion to fail.
//        mTestManager.generateReportLog();
    }

    //
    // AudioMultiApiActivity Overrides
    //
    @Override
    public void onApiChange(int api) {
        stopTest();
        mTestManager.mApi = api;
        mTestManager.validateTestDevices();
        mResultsView.invalidate();
    }

    //
    // View.OnClickHandler
    //
    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.audio_datapaths_start) {
            startTest(mActiveTestAPI);
        } else if (id == R.id.audio_datapaths_cancel) {
            mTestCanceled = true;
            stopTest();
            mTestManager.completeTest();
        } else if (id == R.id.audio_datapaths_clearresults) {
            mTestManager.clearTestState();
            mTestManager.displayTestDevices();
        } else if (id == R.id.audioJavaApiBtn || id == R.id.audioNativeApiBtn) {
            super.onClick(view);
            mTestCanceled = true;
            stopTest();
            mTestManager.clearTestState();
            showDeviceView();
            mTestManager.displayTestDevices();
        } else if (id == R.id.audio_datapaths_calibrate_button) {
            AudioLoopbackCalibrationDialog calibrationDialog =
                    new AudioLoopbackCalibrationDialog(this);
            calibrationDialog.show();
        }
    }

    //
    // (MegaAudio) AppCallback overrides
    //
    @Override
    public void onDataReady(float[] audioData, int numFrames) {
        TestModule testModule = mTestManager.getActiveTestModule();
        if (testModule != null) {
            mAnalyzer.analyzeBuffer(audioData, testModule.mInChannelCount, numFrames);
            mWaveView.setPCMFloatBuff(audioData, testModule.mInChannelCount, numFrames);
        }
    }

    //
    // AudioDeviceCallback overrides
    //
    private class AudioDeviceConnectionCallback extends AudioDeviceCallback {
        void stateChangeHandler() {
            mTestManager.validateTestDevices();
            if (!mTestManager.calculatePass()) {
                // if we are in a pass state, leave the report on the screen
                if (!mIsHandheld) {
                    displayNonHandheldMessage();
                    getPassButton().setEnabled(true);
                } else {
                    showDeviceView();
                    mTestManager.displayTestDevices();
                    if (mTestHasBeenRun) {
                        getPassButton().setEnabled(passBtnEnabled());
                    }
                }
            }
        }

        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            stateChangeHandler();
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            stateChangeHandler();
        }
    }
}
