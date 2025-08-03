/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.cts.verifier.camera.its;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.hardware.camera2.CameraManager;
import android.hardware.cts.helpers.CameraUtils;
import android.hardware.devicestate.DeviceStateManager;
import android.mediapc.cts.common.PerformanceClassEvaluator;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.Pair;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;

import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.DialogTestListActivity;
import com.android.cts.verifier.R;
import com.android.cts.verifier.TestResult;
import com.android.internal.util.ArrayUtils;
import com.android.cts.verifier.CtsVerifierReportLog;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.rules.TestName;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test for Camera features that require that the camera be aimed at a specific test scene.
 * This test activity requires a USB connection to a computer, and a corresponding host-side run of
 * the python scripts found in the CameraITS directory.
 */
public class ItsTestActivity extends DialogTestListActivity {
    private static final String TAG = "ItsTestActivity";
    private static final String EXTRA_CAMERA_ID = "camera.its.extra.CAMERA_ID";
    private static final String EXTRA_RESULTS = "camera.its.extra.RESULTS";
    private static final String EXTRA_VERSION = "camera.its.extra.VERSION";
    private static final String CURRENT_VERSION = "1.0";
    private static final String ACTION_ITS_RESULT =
            "com.android.cts.verifier.camera.its.ACTION_ITS_RESULT";
    private static final String ACTION_ITS_DO_JCA_CAPTURE =
            "com.android.cts.verifier.camera.its.ACTION_ITS_DO_JCA_CAPTURE";
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final String JCA_PACKAGE_NAME = "com.google.jetpackcamera";
    private static final String JCA_ACTIVITY_NAME = "MainActivity";
    private static final String JCA_FILES_CHILD_PATHNAME = "Images/JCATestCaptures";
    public static final String JCA_CAPTURE_PATH_TAG = "JCA_CAPTURE_PATH";
    public static final String JCA_CAPTURE_STATUS_TAG = "JCA_CAPTURE_STATUS";

    private static final String RESULT_PASS = "PASS";
    private static final String RESULT_FAIL = "FAIL";
    private static final String RESULT_NOT_EXECUTED = "NOT_EXECUTED";
    private static final Set<String> RESULT_VALUES = new HashSet<String>(
            Arrays.asList(new String[] {RESULT_PASS, RESULT_FAIL, RESULT_NOT_EXECUTED}));
    private static final int MAX_SUMMARY_LEN = 200;

    private static final Pattern MPC12_CAMERA_LAUNCH_PATTERN =
            Pattern.compile("camera_launch_time_ms:(\\d+(\\.\\d+)?)");
    private static final Pattern MPC12_JPEG_CAPTURE_PATTERN =
            Pattern.compile("1080p_jpeg_capture_time_ms:(\\d+(\\.\\d+)?)");
    private static final int AVAILABILITY_TIMEOUT_MS = 10;

    private static final Pattern PERF_METRICS_YUV_PLUS_JPEG_PATTERN =
            Pattern.compile("test_yuv_plus_jpeg_rms_diff:(\\d+(\\.\\d+)?)");
    private static final Pattern PERF_METRICS_YUV_PLUS_RAW_PATTERN =
            Pattern.compile("test_yuv_plus_raw_rms_diff:(\\d+(\\.\\d+)?)");
    private static final Pattern PERF_METRICS_IMU_DRIFT_PATTERN =
            Pattern.compile("test_imu_drift_.*");

    private static final String REPORT_LOG_NAME = "CtsCameraItsTestCases";

    private final ResultReceiver mResultsReceiver = new ResultReceiver();
    private final BroadcastReceiver mCommandReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Logt.i(TAG, "Received ITS test command");
            if (ACTION_ITS_DO_JCA_CAPTURE.equals(intent.getAction())) {
                Logt.i(TAG, "Doing JCA intent capture");
                doJcaCapture();
            } else {
                Logt.e(TAG, "Unknown intent action " + intent.getAction());
            }
        }
    };
    private String mJcaCapturePath = "";
    private boolean mReceiverRegistered = false;

    public final TestName mTestName = new TestName();
    private  boolean mIsFoldableDevice = false;
    private  boolean mIsDeviceFolded = false;
    private  boolean mFoldedTestSetupDone = false;
    private  boolean mUnfoldedTestSetupDone = false;
    private  Set<Pair<String, String>> mUnavailablePhysicalCameras =
            new HashSet<Pair<String, String>>();
    private CameraManager mCameraManager = null;
    private DeviceStateManager mDeviceStateManager = null;
    private HandlerThread mCameraThread = null;
    private Handler mCameraHandler = null;

    // Initialized in onCreate
    List<String> mToBeTestedCameraIds = null;
    private  String mPrimaryRearCameraId = null;
    private  String mPrimaryFrontCameraId = null;
    private  List<String> mToBeTestedCameraIdsUnfolded = null;
    private  List<String> mToBeTestedCameraIdsFolded = null;
    private  String mPrimaryRearCameraIdUnfolded = null;
    private  String mPrimaryFrontCameraIdUnfolded = null;
    private ArrayTestListAdapter mAdapter;

    // Scenes
    private static final List<String> mSceneIds = List.of(
            "scene0",
            "scene1_1",
            "scene1_2",
            "scene2_a",
            "scene2_b",
            "scene2_c",
            "scene2_d",
            "scene2_e",
            "scene2_f",
            "scene3",
            "scene4",
            "scene5",
            "scene6",
            "scene7",
            "scene_extensions/scene_hdr",
            "scene_extensions/scene_night",
            "scene_video",
            "sensor_fusion",
            "scene_flash");

    // This must match scenes of SUB_CAMERA_TESTS in tools/run_all_tests.py
    private static final List<String> mHiddenPhysicalCameraSceneIds = List.of(
            "scene0",
            "scene1_1",
            "scene1_2",
            "scene2_a",
            "scene2_b",
            "scene4",
            "scene_video",
            "sensor_fusion");

    // TODO: cache the following in saved bundle
    private Set<ResultKey> mAllScenes = null;
    // (camera, scene) -> (pass, fail)
    private final HashMap<ResultKey, Boolean> mExecutedScenes = new HashMap<>();
    // map camera id to ITS summary report path
    private final HashMap<ResultKey, String> mSummaryMap = new HashMap<>();
    // All primary cameras for which MPC level test has run
    private Set<ResultKey> mExecutedMpcTests = null;
    private static final String MPC_LAUNCH_REQ_NUM = "2.2.7.2/7.5/H-1-6";
    private static final String MPC_JPEG_CAPTURE_REQ_NUM = "2.2.7.2/7.5/H-1-5";
    // Performance class evaluator used for writing test result
    PerformanceClassEvaluator mPce = new PerformanceClassEvaluator(mTestName);
    PerformanceClassEvaluator.CameraLatencyRequirement mJpegLatencyReq =
            mPce.addR7_5__H_1_5();
    PerformanceClassEvaluator.CameraLatencyRequirement mLaunchLatencyReq =
            mPce.addR7_5__H_1_6();
    private CtsVerifierReportLog mReportLog;
    // Json Array to store all jsob objects with ITS metrics information
    // stored in the report log
    private final JSONArray mFinalPerfMetricsArr = new JSONArray();

    private static class HandlerExecutor implements Executor {
        private final Handler mHandler;

        HandlerExecutor(Handler handler) {
            mHandler = handler;
        }

        @Override
        public void execute(Runnable runCmd) {
            mHandler.post(runCmd);
        }
    }

    final class ResultKey {
        public final String cameraId;
        public final String sceneId;

        public ResultKey(String cameraId, String sceneId) {
            this.cameraId = cameraId;
            this.sceneId = sceneId;
        }

        @Override
        public boolean equals(final Object o) {
            if (o == null) return false;
            if (this == o) return true;
            if (o instanceof ResultKey) {
                final ResultKey other = (ResultKey) o;
                return cameraId.equals(other.cameraId) && sceneId.equals(other.sceneId);
            }
            return false;
        }

        @Override
        public int hashCode() {
            int h = cameraId.hashCode();
            h = ((h << 5) - h) ^ sceneId.hashCode();
            return h;
        }
    }

    public ItsTestActivity() {
        super(R.layout.its_main,
                R.string.camera_its_test,
                R.string.camera_its_test_info,
                R.string.camera_its_test);
    }

    private final Comparator<ResultKey> mComparator = new Comparator<ResultKey>() {
        @Override
        public int compare(ResultKey k1, ResultKey k2) {
            if (k1.cameraId.equals(k2.cameraId))
                return k1.sceneId.compareTo(k2.sceneId);
            return k1.cameraId.compareTo(k2.cameraId);
        }
    };

    class ResultReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Received result for Camera ITS tests");
            if (ACTION_ITS_RESULT.equals(intent.getAction())) {
                String version = intent.getStringExtra(EXTRA_VERSION);
                if (version == null || !version.equals(CURRENT_VERSION)) {
                    Log.e(TAG, "Its result version mismatch: expect " + CURRENT_VERSION +
                            ", got " + ((version == null) ? "null" : version));
                    ItsTestActivity.this.showToast(R.string.its_version_mismatch);
                    return;
                }

                String cameraId = intent.getStringExtra(EXTRA_CAMERA_ID);
                String results = intent.getStringExtra(EXTRA_RESULTS);
                if (cameraId == null || results == null) {
                    Log.e(TAG, "cameraId = " + ((cameraId == null) ? "null" : cameraId) +
                            ", results = " + ((results == null) ? "null" : results));
                    return;
                }

                if (mIsFoldableDevice) {
                    if (!mIsDeviceFolded) {
                        if (!mToBeTestedCameraIdsUnfolded.contains(cameraId)) {
                            Log.e(TAG, "Unknown camera id " + cameraId + " reported to ITS");
                            return;
                        }
                    } else {
                        if (!mToBeTestedCameraIdsFolded.contains(cameraId)) {
                            Log.e(TAG, "Unknown camera id " + cameraId + " reported to ITS");
                            return;
                        }
                    }
                } else {
                    if (!mToBeTestedCameraIds.contains(cameraId)) {
                        Log.e(TAG, "Unknown camera id " + cameraId + " reported to ITS");
                        return;
                    }
                }

                try {
                    /* Sample JSON results string
                    {
                       "scene0":{
                          "result":"PASS",
                          "summary":"/sdcard/cam0_scene0.txt"
                       },
                       "scene1":{
                          "result":"NOT_EXECUTED"
                       },
                       "scene2":{
                          "result":"FAIL",
                          "summary":"/sdcard/cam0_scene2.txt"
                       }
                    }
                    */
                    JSONObject jsonResults = new JSONObject(results);
                    Log.d(TAG,"Results received:" + jsonResults.toString());
                    Set<String> scenes = new HashSet<>();
                    Iterator<String> keys = jsonResults.keys();
                    while (keys.hasNext()) {
                        scenes.add(keys.next());
                    }

                    JSONObject camJsonObj = new JSONObject();
                    camJsonObj.put("camera_id", cameraId);
                    // Update test execution results
                    for (String scene : scenes) {
                        JSONObject sceneResult = jsonResults.getJSONObject(scene);
                        Log.v(TAG, sceneResult.toString());
                        String result = sceneResult.getString("result");
                        if (result == null) {
                            Log.e(TAG, "Result for " + scene + " is null");
                            return;
                        }
                        Log.i(TAG, "ITS camera" + cameraId + " " + scene + ": result:" + result);
                        if (!RESULT_VALUES.contains(result)) {
                            Log.e(TAG, "Unknown result for " + scene + ": " + result);
                            return;
                        }
                        ResultKey key = new ResultKey(cameraId, scene);
                        if (result.equals(RESULT_PASS) || result.equals(RESULT_FAIL)) {
                            boolean pass = result.equals(RESULT_PASS);
                            mExecutedScenes.put(key, pass);
                            // Get start/end time per camera/scene for result history collection.
                            mStartTime = sceneResult.getLong("start");
                            mEndTime = sceneResult.getLong("end");
                            setTestResult(testId(cameraId, scene), pass ?
                                    TestResult.TEST_RESULT_PASSED : TestResult.TEST_RESULT_FAILED);
                            Log.e(TAG, "setTestResult for " + testId(cameraId, scene) + ": " + result);
                            String summary = sceneResult.optString("summary");
                            if (!summary.equals("")) {
                                mSummaryMap.put(key, summary);
                            }
                        } // do nothing for NOT_EXECUTED scenes

                        if (sceneResult.isNull("mpc_metrics")) {
                            continue;
                        }
                        // Update MPC level
                        JSONArray metrics = sceneResult.getJSONArray("mpc_metrics");
                        for (int i = 0; i < metrics.length(); i++) {
                            String mpcResult = metrics.getString(i);
                            if (!matchMpcResult(cameraId, mpcResult)) {
                                Log.e(TAG, "Error parsing MPC result string:" + mpcResult);
                                return;
                            }
                        }

                        if (sceneResult.isNull("performance_metrics")) {
                            continue;
                        }

                        // Update performance metrics with metrics data from all
                        // scenes for each camera
                        JSONArray mArr = sceneResult.getJSONArray("performance_metrics");
                        for (int i = 0; i < mArr.length(); i++) {
                            String perfResult = mArr.getString(i);
                            if (!matchPerfMetricsResult(perfResult, camJsonObj)) {
                                Log.e(TAG, "Error parsing perf result string:" + perfResult);
                                return;
                            }
                        }
                    }
                    // Add performance metrics for all scenes along with camera_id as json arr
                    // to CtsVerifierReportLog for each camera.
                    mFinalPerfMetricsArr.put(camJsonObj);
                    mReportLog.addValues("perf_metrics", mFinalPerfMetricsArr);
                } catch (org.json.JSONException e) {
                    Log.e(TAG, "Error reading json result string:" + results , e);
                    return;
                }

                // Submitting the report log generates a CtsCameraITSTestCases.reportlog.json
                // on device at path /sdcard/ReportLogFiles
                mReportLog.submit();

                // Set summary if all scenes reported
                if (mSummaryMap.keySet().containsAll(mAllScenes)) {
                    // Save test summary
                    StringBuilder summary = new StringBuilder();
                    for (String path : mSummaryMap.values()) {
                        appendFileContentToSummary(summary, path);
                    }
                    if (summary.length() > MAX_SUMMARY_LEN) {
                        Log.w(TAG, "ITS summary report too long: len: " + summary.length());
                    }
                    ItsTestActivity.this.getReportLog().setSummary(
                            summary.toString(), 1.0, ResultType.NEUTRAL, ResultUnit.NONE);
                }

                // Display current progress
                StringBuilder progress = new StringBuilder();
                for (ResultKey k : mAllScenes) {
                    String status = RESULT_NOT_EXECUTED;
                    if (mExecutedScenes.containsKey(k)) {
                        status = mExecutedScenes.get(k) ? RESULT_PASS : RESULT_FAIL;
                    }
                    progress.append(String.format("Cam %s, %s: %s\n",
                            k.cameraId, k.sceneId, status));
                }
                TextView progressView = (TextView) findViewById(R.id.its_progress);
                progressView.setMovementMethod(new ScrollingMovementMethod());
                progressView.setText(progress.toString());


                // Enable pass button if all scenes pass
                boolean allScenesPassed = true;
                for (ResultKey k : mAllScenes) {
                    Boolean pass = mExecutedScenes.get(k);
                    if (pass == null || pass == false) {
                        allScenesPassed = false;
                        break;
                    }
                }
                if (allScenesPassed) {
                    Log.i(TAG, "All scenes passed.");
                    // Enable pass button
                    ItsTestActivity.this.getPassButton().setEnabled(true);
                    ItsTestActivity.this.setTestResultAndFinish(true);
                } else {
                    ItsTestActivity.this.getPassButton().setEnabled(false);
                }
            }
        }

        private void appendFileContentToSummary(StringBuilder summary, String path) {
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new FileReader(path));
                String line = null;
                do {
                    line = reader.readLine();
                    if (line != null) {
                        summary.append(line);
                    }
                } while (line != null);
            } catch (FileNotFoundException e) {
                Log.e(TAG, "Cannot find ITS summary file at " + path);
                summary.append("Cannot find ITS summary file at " + path);
            } catch (IOException e) {
                Log.e(TAG, "IO exception when trying to read " + path);
                summary.append("IO exception when trying to read " + path);
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                    }
                }
            }
        }

        private boolean matchMpcResult(String cameraId, String mpcResult) {
            Matcher launchMatcher = MPC12_CAMERA_LAUNCH_PATTERN.matcher(mpcResult);
            boolean launchMatches = launchMatcher.matches();

            Matcher jpegMatcher = MPC12_JPEG_CAPTURE_PATTERN.matcher(mpcResult);
            boolean jpegMatches = jpegMatcher.matches();

            if (!launchMatches && !jpegMatches) {
                return false;
            }
            if (!cameraId.equals(mPrimaryRearCameraId) &&
                    !cameraId.equals(mPrimaryFrontCameraId)) {
                return false;
            }

            if (launchMatches) {
                float latency = Float.parseFloat(launchMatcher.group(1));
                if (cameraId.equals(mPrimaryRearCameraId)) {
                    mLaunchLatencyReq.setRearCameraLatency(latency);
                } else {
                    mLaunchLatencyReq.setFrontCameraLatency(latency);
                }
                mExecutedMpcTests.add(new ResultKey(cameraId, MPC_LAUNCH_REQ_NUM));
            } else {
                float latency = Float.parseFloat(jpegMatcher.group(1));
                if (cameraId.equals(mPrimaryRearCameraId)) {
                    mJpegLatencyReq.setRearCameraLatency(latency);
                } else {
                    mJpegLatencyReq.setFrontCameraLatency(latency);
                }
                mExecutedMpcTests.add(new ResultKey(cameraId, MPC_JPEG_CAPTURE_REQ_NUM));
            }

            // Save MPC info once both front primary and rear primary data are collected.
            if (mExecutedMpcTests.size() == 4) {
                mPce.submitAndVerify();
            }
            return true;
        }

        private boolean matchPerfMetricsResult(String perfMetricsResult, JSONObject obj) {
            Matcher yuvPlusJpegMetricsMatcher = PERF_METRICS_YUV_PLUS_JPEG_PATTERN.matcher(
                        perfMetricsResult);
            boolean yuvPlusJpegMetricsMatches = yuvPlusJpegMetricsMatcher.matches();

            Matcher yuvPlusRawMetricsMatcher = PERF_METRICS_YUV_PLUS_RAW_PATTERN.matcher(
                        perfMetricsResult);
            boolean yuvPlusRawMetricsMatches = yuvPlusRawMetricsMatcher.matches();

            Matcher imuDriftMetricsMatcher = PERF_METRICS_IMU_DRIFT_PATTERN.matcher(
                        perfMetricsResult);
            boolean imuDriftMetricsMatches = imuDriftMetricsMatcher.matches();

            if (!yuvPlusJpegMetricsMatches && !yuvPlusRawMetricsMatches
                        && !imuDriftMetricsMatches) {
                return false;
            }

            try {
                if (yuvPlusJpegMetricsMatches) {
                    Log.i(TAG, "jpeg pattern  matches");
                    float diff = Float.parseFloat(yuvPlusJpegMetricsMatcher.group(1));
                    obj.put("yuv_plus_jpeg_rms_diff", diff);
                }

                if (yuvPlusRawMetricsMatches) {
                    Log.i(TAG, "raw pattern  matches");
                    float diff = Float.parseFloat(yuvPlusRawMetricsMatcher.group(1));
                    obj.put("yuv_plus_raw_rms_diff", diff);
                }

                if (imuDriftMetricsMatches) {
                    Log.i(TAG, "imu drift matches");
                    // remove "test_" from the result
                    String result = perfMetricsResult.replaceFirst("^test_","");
                    String resultKey = result.split(":")[0].strip();
                    if (resultKey.contains("seconds") || resultKey.contains("hz")) {
                        float value = Float.parseFloat(result.split(":")[1].strip());
                        obj.put(resultKey, value);
                    } else {
                        String value = result.split(":")[1].strip();
                        obj.put(resultKey, value);
                    }
                }
            } catch (org.json.JSONException e) {
                Log.e(TAG, "Error when serializing the metrics into a JSONObject" , e);
            }

            return true;
        }
    }


    private class FoldStateListener implements
            DeviceStateManager.DeviceStateCallback {
        private int[] mFoldedDeviceStates;
        private boolean mFirstFoldCheck = false;

        FoldStateListener(Context context) {
            Resources systemRes = Resources.getSystem();
            int foldedStatesArrayIdentifier = systemRes.getIdentifier("config_foldedDeviceStates",
                    "array", "android");
            mFoldedDeviceStates = systemRes.getIntArray(foldedStatesArrayIdentifier);
        }

        @Override
        public final void onStateChanged(int state) {
            boolean folded = ArrayUtils.contains(mFoldedDeviceStates, state);
            Log.i(TAG, "Is device folded? " + mIsDeviceFolded);
            if (!mFirstFoldCheck || mIsDeviceFolded != folded) {
                mIsDeviceFolded = folded;
                mFirstFoldCheck = true;
                if (mFoldedTestSetupDone && mUnfoldedTestSetupDone) {
                    Log.i(TAG, "Setup is done for both the states.");
                } else {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Log.i(TAG, "set up from onStateChanged");
                            getCameraIdsForFoldableDevice();
                            setupItsTestsForFoldableDevice(mAdapter);
                        }
                    });
                }
            } else {
                Log.i(TAG, "Last state is same as new state.");
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Hide the test if all camera devices are legacy
        mCameraManager = this.getSystemService(CameraManager.class);
        if (mReportLog == null) {
            mReportLog =
                    new CtsVerifierReportLog(REPORT_LOG_NAME, "camera_its_results");
        }
        Context context = this.getApplicationContext();
        if (mAllScenes == null) {
            mAllScenes = new TreeSet<>(mComparator);
        }
        if (mExecutedMpcTests == null) {
            mExecutedMpcTests = new TreeSet<>(mComparator);
        }
        mCameraThread = new HandlerThread("ItsTestActivityThread");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());
        HandlerExecutor handlerExecutor = new HandlerExecutor(mCameraHandler);
        // mIsFoldableDevice is set True for foldables to listen to callback
        // in FoldStateListener
        mIsFoldableDevice = isFoldableDevice();
        Log.i(TAG, "Is device foldable? " + mIsFoldableDevice);
        if (mIsFoldableDevice) {
            FoldStateListener foldStateListener = new FoldStateListener(context);
            mDeviceStateManager = context.getSystemService(DeviceStateManager.class);
            // onStateChanged will be called upon registration which helps determine
            // if the foldable device has changed the folded/unfolded state or not.
            mDeviceStateManager.registerCallback(handlerExecutor, foldStateListener);
        }
        if (!mIsFoldableDevice) {
            try {
                ItsUtils.ItsCameraIdList cameraIdList =
                        ItsUtils.getItsCompatibleCameraIds(mCameraManager);
                mToBeTestedCameraIds = cameraIdList.mCameraIdCombos;
                mPrimaryRearCameraId = cameraIdList.mPrimaryRearCameraId;
                mPrimaryFrontCameraId = cameraIdList.mPrimaryFrontCameraId;
            } catch (ItsException e) {
                Toast.makeText(ItsTestActivity.this,
                        "Received error from camera service while checking device capabilities: "
                                + e, Toast.LENGTH_SHORT).show();
            }
        }

        super.onCreate(savedInstanceState);

        if (!mIsFoldableDevice) {
            if (mToBeTestedCameraIds.size() == 0) {
                showToast(R.string.all_exempted_devices);
                ItsTestActivity.this.getReportLog().setSummary(
                        "PASS: all cameras on this device are exempted from ITS",
                        1.0, ResultType.NEUTRAL, ResultUnit.NONE);
                setTestResultAndFinish(true);
            }
        }
        // Default locale must be set to "en-us"
        Locale locale = Locale.getDefault();
        if (!Locale.US.equals(locale)) {
            String toastMessage = "Unsupported default language " + locale + "! "
                    + "Please switch the default language to English (United States) in "
                    + "Settings > Language & input > Languages";
            Toast.makeText(ItsTestActivity.this, toastMessage, Toast.LENGTH_LONG).show();
            ItsTestActivity.this.getReportLog().setSummary(
                    "FAIL: Default language is not set to " + Locale.US,
                    1.0, ResultType.NEUTRAL, ResultUnit.NONE);
            setTestResultAndFinish(false);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private List<String> getCameraIdsAvailableForTesting() {
        List<String> toBeTestedCameraIds = new ArrayList<String>();
        List<String> availableCameraIdList = new ArrayList<String>();
        try {
            ItsUtils.ItsCameraIdList cameraIdList =
                    ItsUtils.getItsCompatibleCameraIds(mCameraManager);
            toBeTestedCameraIds = cameraIdList.mCameraIdCombos;
            mPrimaryRearCameraId = cameraIdList.mPrimaryRearCameraId;
            mPrimaryFrontCameraId = cameraIdList.mPrimaryFrontCameraId;
            mUnavailablePhysicalCameras = getUnavailablePhysicalCameras();
            Log.i(TAG, "unavailablePhysicalCameras:"
                    + mUnavailablePhysicalCameras.toString());
            for (String str : toBeTestedCameraIds) {
                if (str.contains(".")) {
                    String[] strArr = str.split("\\.");
                    if (mUnavailablePhysicalCameras.contains(new Pair<>(strArr[0], strArr[1]))) {
                        toBeTestedCameraIds.remove(str);
                    }
                }
            }
            Log.i(TAG, "AvailablePhysicalCameras to be tested:"
                    + Arrays.asList(toBeTestedCameraIds.toString()));
        } catch (ItsException e) {
            Log.i(TAG, "Received error from camera service while checking device capabilities: "
                    + e);
        } catch (Exception e) {
            Log.i(TAG, "Exception: " + e);
        }

        return toBeTestedCameraIds;
    }

    // Get camera ids available for testing for device in
    // each state: folded and unfolded.
    protected void getCameraIdsForFoldableDevice() {
        boolean deviceFolded = mIsDeviceFolded;
        try {
            if (mIsDeviceFolded) {
                mToBeTestedCameraIdsFolded = getCameraIdsAvailableForTesting();
            } else {
                mToBeTestedCameraIdsUnfolded = getCameraIdsAvailableForTesting();
            }
        } catch (Exception e) {
            Log.i(TAG, "Exception: " + e);
        }
    }

    @Override
    public void showManualTestDialog(final DialogTestListItem test,
            final DialogTestListItem.TestCallback callback) {
        //Nothing todo for ITS
    }

    protected String testTitle(String cam, String scene) {
        return "Camera: " + cam + ", " + scene;
    }

    // CtsVerifier has a "Folded" toggle that selectively surfaces some tests.
    // To separate the tests in folded and unfolded states, CtsVerifier adds a [folded]
    // suffix to the test id in its internal database depending on the state of the "Folded"
    // toggle button. However, CameraITS has tests that it needs to persist across both folded
    // and unfolded states.To get the test results to persist, we need CtsVerifier to store and
    // look up the same test id regardless of the toggle button state.
    // TODO(b/282804139): Update CTS tests to allow activities to write tests that persist
    // across the states
    protected String testId(String cam, String scene) {
        return "Camera_ITS_" + cam + "_" + scene + "[folded]";
    }

    protected boolean isFoldableDevice() {
        Context context = this.getApplicationContext();
        return CameraUtils.isDeviceFoldable(context);
    }

    protected boolean isDeviceFolded() {
        return mIsDeviceFolded;
    }

    protected Set<Pair<String, String>> getUnavailablePhysicalCameras() throws ItsException {
        final LinkedBlockingQueue<Pair<String, String>> unavailablePhysicalCamEventQueue =
                new LinkedBlockingQueue<>();
        mCameraThread = new HandlerThread("ItsCameraThread");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());
        try {
            CameraManager.AvailabilityCallback ac = new CameraManager.AvailabilityCallback() {
                @Override
                public void onPhysicalCameraUnavailable(String cameraId, String physicalCameraId) {
                    unavailablePhysicalCamEventQueue.offer(new Pair<>(cameraId, physicalCameraId));
                }
            };
            mCameraManager.registerAvailabilityCallback(ac, mCameraHandler);
            Set<Pair<String, String>> unavailablePhysicalCameras =
                    new HashSet<Pair<String, String>>();
            Pair<String, String> candidatePhysicalIds =
                    unavailablePhysicalCamEventQueue.poll(AVAILABILITY_TIMEOUT_MS,
                    java.util.concurrent.TimeUnit.MILLISECONDS);
            while (candidatePhysicalIds != null) {
                unavailablePhysicalCameras.add(candidatePhysicalIds);
                candidatePhysicalIds =
                        unavailablePhysicalCamEventQueue.poll(AVAILABILITY_TIMEOUT_MS,
                        java.util.concurrent.TimeUnit.MILLISECONDS);
            }
            mCameraManager.unregisterAvailabilityCallback(ac);
            return unavailablePhysicalCameras;
        } catch (Exception e) {
            throw new ItsException("Exception: ", e);
        }
    }

    protected void setupItsTests(ArrayTestListAdapter adapter) {
        for (String cam : mToBeTestedCameraIds) {
            List<String> scenes = cam.contains(ItsUtils.CAMERA_ID_TOKENIZER)
                    ? mHiddenPhysicalCameraSceneIds : mSceneIds;
            for (String scene : scenes) {
                // Add camera and scene combinations in mAllScenes to avoid adding n/a scenes for
                // devices with sub-cameras.
                mAllScenes.add(new ResultKey(cam, scene));
                adapter.add(new DialogTestListItem(this,
                        testTitle(cam, scene),
                        testId(cam, scene)));
            }
            if (mExecutedMpcTests == null) {
                mExecutedMpcTests = new TreeSet<>(mComparator);
            }
            Log.d(TAG, "Total combinations to test on this device:" + mAllScenes.size());
        }
    }

    protected void setupItsTestsForFoldableDevice(ArrayTestListAdapter adapter) {
        List<String> toBeTestedCameraIds = new ArrayList<String>();
        if (mIsDeviceFolded) {
            toBeTestedCameraIds = mToBeTestedCameraIdsFolded;
        } else {
            toBeTestedCameraIds = mToBeTestedCameraIdsUnfolded;
        }

        for (String cam : toBeTestedCameraIds) {
            List<String> scenes = cam.contains(ItsUtils.CAMERA_ID_TOKENIZER)
                    ? mHiddenPhysicalCameraSceneIds : mSceneIds;
            for (String scene : scenes) {
                // Add camera and scene combinations in mAllScenes to avoid adding n/a scenes for
                // devices with sub-cameras.
                if (cam.contains(mPrimaryFrontCameraId) && mIsDeviceFolded) {
                    scene = scene + "_folded";
                }
                // Rear camera scenes will be added only once.
                if (mAllScenes.contains(new ResultKey(cam, scene))) {
                    continue;
                }
                // TODO(ruchamk): Remove extra logging after testing.
                Log.i(TAG, "Adding cam_id: " + cam + "scene: " + scene);
                mAllScenes.add(new ResultKey(cam, scene));
                adapter.add(new DialogTestListItem(this,
                        testTitle(cam, scene),
                        testId(cam, scene)));
            }
        }
        Log.d(TAG, "Total combinations to test on this device:"
                + mAllScenes.size() + " folded? " + mIsDeviceFolded);
        if (mIsDeviceFolded) {
            mFoldedTestSetupDone = true;
            Log.i(TAG, "mFoldedTestSetupDone");
        } else {
            mUnfoldedTestSetupDone = true;
            Log.i(TAG, "mUnfoldedTestSetupDone");
        }
        if (mFoldedTestSetupDone && mUnfoldedTestSetupDone) {
            Log.d(TAG, "Total combinations to test on this foldable "
                    + "device for both states:" + mAllScenes.size());
        }
        adapter.loadTestResults();
    }

    @Override
    protected void setupTests(ArrayTestListAdapter adapter) {
        mAdapter = adapter;
        if (mIsFoldableDevice) {
            if (mFoldedTestSetupDone && mUnfoldedTestSetupDone) {
                Log.i(TAG, "Set up is done");
            }
        } else {
            setupItsTests(adapter);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mCameraManager == null) {
            showToast(R.string.no_camera_manager);
        } else {
            Log.d(TAG, "register ITS result receiver and command receiver");
            IntentFilter filter = new IntentFilter(ACTION_ITS_RESULT);
            registerReceiver(mResultsReceiver, filter, Context.RECEIVER_EXPORTED);
            filter = new IntentFilter(ACTION_ITS_DO_JCA_CAPTURE);
            registerReceiver(mCommandReceiver, filter, Context.RECEIVER_EXPORTED);
            mReceiverRegistered = true;
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "unregister ITS result receiver");
        if (mReceiverRegistered) {
            unregisterReceiver(mResultsReceiver);
            unregisterReceiver(mCommandReceiver);
        }
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setContentView(R.layout.its_main);
        setInfoResources(R.string.camera_its_test, R.string.camera_its_test_info, -1);
        setPassFailButtonClickListeners();
        // Changing folded state can incorrectly enable pass button
        ItsTestActivity.this.getPassButton().setEnabled(false);
    }

    @Override
    public void handleActivityResult(int requestCode, int resultCode, Intent data) {
        Logt.i(TAG, "request code: " + requestCode + ", result code: " + resultCode);
        if (requestCode == REQUEST_IMAGE_CAPTURE) {
            if (resultCode != RESULT_OK) {
                Logt.e(TAG, "Capture failed!");
            }
            Intent serviceIntent = new Intent(this, ItsService.class);
            serviceIntent.putExtra(JCA_CAPTURE_PATH_TAG, mJcaCapturePath);
            serviceIntent.putExtra(JCA_CAPTURE_STATUS_TAG, resultCode);
            startService(serviceIntent);
        } else {
            super.handleActivityResult(requestCode, resultCode, data);
        }
    }

    private void doJcaCapture() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File imageDir = new File(this.getExternalFilesDir(null), JCA_FILES_CHILD_PATHNAME);
        imageDir.mkdirs();
        if (!imageDir.exists()) {
            Logt.e(TAG, "Could not create image directory");
            return;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                .withZone(ZoneId.systemDefault());
        String timestamp = formatter.format(Instant.now());
        File imageFile = new File(imageDir, "ITS_JCA_" + timestamp + ".jpg");
        Logt.i(TAG, "file path: " + imageFile.toString());
        mJcaCapturePath = imageFile.toString();
        Uri photoUri = FileProvider.getUriForFile(
                this,
                "com.android.cts.verifier.managedprovisioning.fileprovider",
                imageFile);
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
        takePictureIntent.setComponent(new ComponentName(
                JCA_PACKAGE_NAME, JCA_PACKAGE_NAME + "." + JCA_ACTIVITY_NAME));
        takePictureIntent.setFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        } catch (ActivityNotFoundException e) {
            Logt.e(TAG, "Error starting image capture intent activity: " + e);
        }
    }
}
