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

package android.voiceinteraction.cts;


import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.service.voice.SandboxedDetectionInitializer.INITIALIZATION_STATUS_SUCCESS;
import static android.voiceinteraction.common.Utils.toggleVisualQueryAccessibilitySettings;
import static android.voiceinteraction.cts.testcore.Helper.CTS_SERVICE_PACKAGE;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresDevice;
import android.service.voice.VisualQueryDetector;
import android.util.Log;
import android.voiceinteraction.common.Utils;
import android.voiceinteraction.cts.services.BaseVoiceInteractionService;
import android.voiceinteraction.cts.services.CtsBasicVoiceInteractionService;
import android.voiceinteraction.cts.testcore.AssumptionCheckerRule;
import android.voiceinteraction.cts.testcore.Helper;
import android.voiceinteraction.cts.testcore.VoiceInteractionServiceConnectedRule;
import android.voiceinteraction.service.MainVisualQueryDetectionService;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.DisableAnimationRule;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Objects;

@AppModeFull(reason = "No real use case for instant mode")
public class VisualQueryDetectionServiceBasicTest {
    private static final String TAG = "VisualQueryDetectionServiceTest";
    // The VoiceInteractionService used by this test
    private static final String SERVICE_COMPONENT =
            "android.voiceinteraction.cts.services.CtsBasicVoiceInteractionService";
    private static final long SETUP_WAIT_MS = 10_000;
    private static final long TEST_WAIT_TIMEOUT_MS = 2_000;

    private PackageManager mPackageManager;

    private CtsBasicVoiceInteractionService mService;

    private static String sDefaultScreenOffTimeoutValue;

    @Rule
    public AssumptionCheckerRule checkVisualQueryDetectionServiceEnabledRule =
            new AssumptionCheckerRule(() -> Utils.SYSPROP_VISUAL_QUERY_SERVICE_ENABLED,
            "Testing VisualQueryDetectionService requires enabling the feature");

    @Rule
    public VoiceInteractionServiceConnectedRule mConnectedRule =
            new VoiceInteractionServiceConnectedRule(
                    getInstrumentation().getTargetContext(), getTestVoiceInteractionService());

    @Rule
    public DisableAnimationRule mDisableAnimationRule = new DisableAnimationRule();

    @BeforeClass
    public static void extendScreenOffTimeout() throws Exception {
        // Change screen off timeout to 20 minutes.
        sDefaultScreenOffTimeoutValue = SystemUtil.runShellCommand(
                "settings get system screen_off_timeout");
        SystemUtil.runShellCommand("settings put system screen_off_timeout 1200000");
    }

    @AfterClass
    public static void restoreScreenOffTimeout() {
        SystemUtil.runShellCommand(
                "settings put system screen_off_timeout " + sDefaultScreenOffTimeoutValue);
    }

    @Before
    public void setup() {
        mPackageManager = getInstrumentation().getContext().getPackageManager();

        // VoiceInteractionServiceConnectedRule handles the service connected,
        // the test should be able to get service
        mService = (CtsBasicVoiceInteractionService) BaseVoiceInteractionService.getService();
        // Check the test can get the service
        Objects.requireNonNull(mService);

        // Wait the original VisualQueryDetector finish clean up to avoid flaky
        SystemClock.sleep(SETUP_WAIT_MS);
    }

    @After
    public void tearDown() {
        mService = null;
    }

    public String getTestVoiceInteractionService() {
        Log.d(TAG, "getTestVoiceInteractionService()");
        return new ComponentName(CTS_SERVICE_PACKAGE, SERVICE_COMPONENT).flattenToString();
    }

    @Test
    public void testVisualQueryDetectionService_validComponentName_triggerSuccess()
            throws Throwable {
        // Assertion is done in the private method.
        VisualQueryDetector visualQueryDetector = createVisualQueryDetector();
        visualQueryDetector.destroy();
    }

    @Test
    public void testVoiceInteractionService_withoutManageHotwordDetectionPermission_triggerFailure()
            throws Throwable {
        // Create VisualQueryDetector and wait result
        mService.createVisualQueryDetectorWithoutManageHotwordDetectionPermission();

        // Wait the result and verify expected result
        mService.waitSandboxedDetectionServiceInitializedCalledOrException();

        // Verify IllegalStateException throws
        assertThat(mService.isCreateDetectorSecurityExceptionThrow()).isTrue();
    }

    @Test
    public void testVoiceInteractionService_holdBindHotwordDetectionPermission_triggerFailure()
            throws Throwable {
        // Create VisualQueryDetector and wait result
        mService.createVisualQueryDetectorHoldBindVisualQueryDetectionPermission();

        // Wait the result and verify expected result
        mService.waitSandboxedDetectionServiceInitializedCalledOrException();

        // Verify SecurityException throws
        assertThat(mService.isCreateDetectorSecurityExceptionThrow()).isTrue();
    }

    @Test
    public void testVoiceInteractionService_disallowCreateVisualQueryDetectorTwice()
            throws Throwable {
        final boolean enableMultipleDetectors = Helper.isEnableMultipleDetectors();
        assumeTrue("Not support multiple hotword detectors", enableMultipleDetectors);

        // Create first VisualQueryDetector, it's fine.
        VisualQueryDetector visualQueryDetector = createVisualQueryDetector();

        // Create second VisualQueryDetector, it will get the IllegalStateException due to
        // the previous VisualQueryDetector is not destroy.
        mService.createVisualQueryDetector();
        mService.waitSandboxedDetectionServiceInitializedCalledOrException();

        // Verify IllegalStateException throws
        assertThat(mService.isCreateDetectorIllegalStateExceptionThrow()).isTrue();

        visualQueryDetector.destroy();
    }

    @Test
    public void testVisualQueryDetectionService_destroyVisualQueryDetector_activeDetectorRemoved()
            throws Throwable {
        // Create VisualQueryDetector
        VisualQueryDetector visualQueryDetector = createVisualQueryDetector();

        // Destroy VisualQueryDetector
        visualQueryDetector.destroy();

        try {
            adoptShellPermissionIdentityForVisualQueryDetection();
            // Can no longer use the detector because it is in an invalid state
            assertThrows(IllegalStateException.class, visualQueryDetector::startRecognition);
        } finally {
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresDevice
    public void testVisualQueryDetectionService_startRecogintion_testCameraOpen()
            throws Throwable {
        assumeTrue(hasFeature(PackageManager.FEATURE_CAMERA));

        // Create VisualQueryDetector
        VisualQueryDetector visualQueryDetector = createVisualQueryDetector();
        runWithShellPermissionIdentity(() -> {
            PersistableBundle options = Helper.createFakePersistableBundleData();
            options.putInt(MainVisualQueryDetectionService.KEY_VQDS_TEST_SCENARIO,
                    MainVisualQueryDetectionService.SCENARIO_TEST_PERCEPTION_MODULES);
            visualQueryDetector.updateState(options, Helper.createFakeSharedMemoryData());
        });
        try {
            adoptShellPermissionIdentityForVisualQueryDetection();
            mService.initQueryFinishRejectLatch(1);
            visualQueryDetector.startRecognition();
            // wait onStartDetection() called and verify the result
            mService.waitOnQueryFinishedRejectCalled();
            // verify results
            ArrayList<String> streamedQueries = mService.getStreamedQueriesResult();
            assertThat(streamedQueries.size()).isEqualTo(1);
            assertThat(streamedQueries.get(0)).isEqualTo(
                    MainVisualQueryDetectionService.PERCEPTION_MODULE_SUCCESS);
        } finally {
            // Drop identity adopted.
            visualQueryDetector.destroy();
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresDevice
    public void testVisualQueryDetectionService_startRecogintion_noQuery()
            throws Throwable {
        // Create VisualQueryDetector
        VisualQueryDetector visualQueryDetector = createVisualQueryDetector();
        runWithShellPermissionIdentity(() -> {
            PersistableBundle options = Helper.createFakePersistableBundleData();
            options.putInt(MainVisualQueryDetectionService.KEY_VQDS_TEST_SCENARIO,
                    MainVisualQueryDetectionService.SCENARIO_ATTENTION_LEAVE);
            visualQueryDetector.updateState(options, Helper.createFakeSharedMemoryData());
        });
        try {
            adoptShellPermissionIdentityForVisualQueryDetection();
            visualQueryDetector.startRecognition();
            //TODO: Wait for the callback for more stable testing instead of sleeping.
            SystemClock.sleep(TEST_WAIT_TIMEOUT_MS); // reduce flakiness

            // verify results
            ArrayList<String> streamedQueries = mService.getStreamedQueriesResult();
            assertThat(streamedQueries.size()).isEqualTo(0);
        } finally {
            // Drop identity adopted.
            visualQueryDetector.destroy();
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresDevice
    public void testVisualQueryDetectionService_startRecogintion_attentionQueryFinishedLeave()
            throws Throwable {
        // Create VisualQueryDetector
        VisualQueryDetector visualQueryDetector = createVisualQueryDetector();
        runWithShellPermissionIdentity(() -> {
            PersistableBundle options = Helper.createFakePersistableBundleData();
            options.putInt(MainVisualQueryDetectionService.KEY_VQDS_TEST_SCENARIO,
                    MainVisualQueryDetectionService.SCENARIO_ATTENTION_QUERY_FINISHED_LEAVE);
            visualQueryDetector.updateState(options, Helper.createFakeSharedMemoryData());
        });
        try {
            adoptShellPermissionIdentityForVisualQueryDetection();

            mService.initQueryFinishRejectLatch(1);
            visualQueryDetector.startRecognition();

            // wait onStartDetection() called and verify the result
            mService.waitOnQueryFinishedRejectCalled();

            // verify results
            ArrayList<String> streamedQueries = mService.getStreamedQueriesResult();
            assertThat(streamedQueries.get(0)).isEqualTo(
                    MainVisualQueryDetectionService.FAKE_QUERY_FIRST
                            + MainVisualQueryDetectionService.FAKE_QUERY_SECOND);
            assertThat(streamedQueries.size()).isEqualTo(1);

        } finally {
            visualQueryDetector.destroy();
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresDevice
    public void testVisualQueryDetectionService_startRecogintion_audioVisualAttentionQueryStream()
            throws Throwable {
        // Create VisualQueryDetector
        VisualQueryDetector visualQueryDetector = createVisualQueryDetector();
        runWithShellPermissionIdentity(() -> {
            PersistableBundle options = Helper.createFakePersistableBundleData();
            options.putInt(MainVisualQueryDetectionService.KEY_VQDS_TEST_SCENARIO,
                    MainVisualQueryDetectionService.SCENARIO_AUDIO_VISUAL_ATTENTION_STREAM);
            visualQueryDetector.updateState(options, Helper.createFakeSharedMemoryData());
        });
        try {
            adoptShellPermissionIdentityForVisualQueryDetection();

            mService.initQueryFinishRejectLatch(1);
            visualQueryDetector.startRecognition();

            // wait onStartDetection() called and verify the result
            mService.waitOnQueryFinishedRejectCalled();

            // verify results
            ArrayList<String> streamedQueries = mService.getStreamedQueriesResult();
            assertThat(streamedQueries.get(0)).isEqualTo(
                    MainVisualQueryDetectionService.FAKE_QUERY_FIRST);
            assertThat(streamedQueries.size()).isEqualTo(1);

        } finally {
            visualQueryDetector.destroy();
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresDevice
    public void testVisualQueryDetectionService_startRecogintion_AccessibilityAttentionQueryStream()
            throws Throwable {
        // Create VisualQueryDetector
        VisualQueryDetector visualQueryDetector = createVisualQueryDetector();
        runWithShellPermissionIdentity(() -> {
            PersistableBundle options = Helper.createFakePersistableBundleData();
            options.putInt(MainVisualQueryDetectionService.KEY_VQDS_TEST_SCENARIO,
                    MainVisualQueryDetectionService.SCENARIO_ACCESSIBILITY_ATTENTION_STREAM);
            visualQueryDetector.updateState(options, Helper.createFakeSharedMemoryData());
        });
        try {
            adoptShellPermissionIdentityForVisualQueryDetection();

            mService.initQueryFinishRejectLatch(1);
            visualQueryDetector.startRecognition();

            // wait onStartDetection() called and verify the result
            mService.waitOnQueryFinishedRejectCalled();

            // verify results
            ArrayList<String> streamedQueries = mService.getStreamedQueriesResult();
            assertThat(streamedQueries.get(0)).isEqualTo(
                    MainVisualQueryDetectionService.FAKE_QUERY_FIRST);
            assertThat(streamedQueries.size()).isEqualTo(1);

        } finally {
            visualQueryDetector.destroy();
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresDevice
    public void testVisualQueryDetectionService_startRecogintion_enableA11ySettingStreamSuccess()
            throws Throwable {
        // Create VisualQueryDetector
        VisualQueryDetector visualQueryDetector = createVisualQueryDetector();

        // Sleep as this should be toggled by UI instead.
        toggleVisualQueryAccessibilitySettings(true);
        SystemClock.sleep(SETUP_WAIT_MS);

        runWithShellPermissionIdentity(() -> {
            PersistableBundle options = Helper.createFakePersistableBundleData();
            options.putInt(MainVisualQueryDetectionService.KEY_VQDS_TEST_SCENARIO,
                    MainVisualQueryDetectionService.SCENARIO_STREAM_WITH_ACCESSIBILITY_DATA);
            visualQueryDetector.updateState(options, Helper.createFakeSharedMemoryData());
        });
        try {
            adoptShellPermissionIdentityForVisualQueryDetection();

            mService.initQueryFinishRejectLatch(1);
            visualQueryDetector.startRecognition();

            // wait onStartDetection() called and verify the result
            mService.waitOnQueryFinishedRejectCalled();

            // verify results
            ArrayList<byte[]> streamedAccessibilityData = mService.getAccessibilityDataResult();
            assertThat(streamedAccessibilityData.get(0)).isEqualTo(
                    MainVisualQueryDetectionService.TEST_BYTES);
            assertThat(streamedAccessibilityData.size()).isEqualTo(1);

        } finally {
            visualQueryDetector.destroy();
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresDevice
    public void testVisualQueryDetectionService_startRecogintion_disableA11ySettingStreamFailure()
            throws Throwable {
        // Create VisualQueryDetector
        VisualQueryDetector visualQueryDetector = createVisualQueryDetector();

        // Sleep as this should be toggled by UI instead.
        toggleVisualQueryAccessibilitySettings(false);
        SystemClock.sleep(SETUP_WAIT_MS);

        runWithShellPermissionIdentity(() -> {
            PersistableBundle options = Helper.createFakePersistableBundleData();
            options.putInt(MainVisualQueryDetectionService.KEY_VQDS_TEST_SCENARIO,
                    MainVisualQueryDetectionService.SCENARIO_STREAM_WITH_ACCESSIBILITY_DATA);
            visualQueryDetector.updateState(options, Helper.createFakeSharedMemoryData());
        });
        try {
            adoptShellPermissionIdentityForVisualQueryDetection();

            mService.initOnFailureLatch();
            visualQueryDetector.startRecognition();

            // wait onFailure() called since this would not be permitted.
            mService.waitOnFailureCalled();

            // verify results - nothing should be here
            ArrayList<byte[]> streamedAccessibilityData = mService.getAccessibilityDataResult();
            assertThat(streamedAccessibilityData.size()).isEqualTo(0);

        } finally {
            visualQueryDetector.destroy();
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresDevice
    public void testVisualQueryDetectionService_startRecogintion_attentionQueryRejectedLeave()
            throws Throwable {
        // Create VisualQueryDetector
        VisualQueryDetector visualQueryDetector = createVisualQueryDetector();
        runWithShellPermissionIdentity(() -> {
            PersistableBundle options = Helper.createFakePersistableBundleData();
            options.putInt(MainVisualQueryDetectionService.KEY_VQDS_TEST_SCENARIO,
                    MainVisualQueryDetectionService.SCENARIO_ATTENTION_QUERY_REJECTED_LEAVE);
            visualQueryDetector.updateState(options, Helper.createFakeSharedMemoryData());
        });
        try {
            adoptShellPermissionIdentityForVisualQueryDetection();

            mService.initQueryFinishRejectLatch(1);
            visualQueryDetector.startRecognition();

            // wait onStartDetection() called and verify the result
            mService.waitOnQueryFinishedRejectCalled();

            // verify results
            ArrayList<String> streamedQueries = mService.getStreamedQueriesResult();
            assertThat(streamedQueries.get(0)).isEqualTo(
                    MainVisualQueryDetectionService.FAKE_QUERY_FIRST);
            assertThat(streamedQueries.size()).isEqualTo(1);
        } finally {
            visualQueryDetector.destroy();
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresDevice
    public void testVisualQueryDetectionService_startRecogintion_attentionDoubleQueryFinishedLeave()
            throws Throwable {
        // Create VisualQueryDetector
        VisualQueryDetector visualQueryDetector = createVisualQueryDetector();
        runWithShellPermissionIdentity(() -> {
            PersistableBundle options = Helper.createFakePersistableBundleData();
            options.putInt(MainVisualQueryDetectionService.KEY_VQDS_TEST_SCENARIO,
                    MainVisualQueryDetectionService.SCENARIO_ATTENTION_DOUBLE_QUERY_FINISHED_LEAVE);
            visualQueryDetector.updateState(options, Helper.createFakeSharedMemoryData());
        });
        try {
            adoptShellPermissionIdentityForVisualQueryDetection();

            mService.initQueryFinishRejectLatch(2);
            visualQueryDetector.startRecognition();

            // wait onStartDetection() called and verify the result
            mService.waitOnQueryFinishedRejectCalled();

            // verify results
            ArrayList<String> streamedQueries = mService.getStreamedQueriesResult();
            assertThat(streamedQueries.get(0)).isEqualTo(
                    MainVisualQueryDetectionService.FAKE_QUERY_FIRST);
            assertThat(streamedQueries.get(1)).isEqualTo(
                    MainVisualQueryDetectionService.FAKE_QUERY_SECOND);
            assertThat(streamedQueries.size()).isEqualTo(2);
        } finally {
            visualQueryDetector.destroy();
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresDevice
    public void testVisualQueryDetectionService_startRecogintion_multipleQueryStreamFinish()
            throws Throwable {
        // Create VisualQueryDetector
        VisualQueryDetector visualQueryDetector = createVisualQueryDetector();
        runWithShellPermissionIdentity(() -> {
            PersistableBundle options = Helper.createFakePersistableBundleData();
            options.putInt(MainVisualQueryDetectionService.KEY_VQDS_TEST_SCENARIO,
                    MainVisualQueryDetectionService.SCENARIO_MULTIPLE_QUERIES_FINISHED);
            visualQueryDetector.updateState(options, Helper.createFakeSharedMemoryData());
        });
        try {
            adoptShellPermissionIdentityForVisualQueryDetection();

            mService.initQueryFinishRejectLatch(Utils.NUM_TEST_QUERY_SESSION_MULTIPLE);
            visualQueryDetector.startRecognition();

            // wait onStartDetection() called and verify the result
            mService.waitOnQueryFinishedRejectCalled();

            // verify results
            ArrayList<String> streamedQueries = mService.getStreamedQueriesResult();
            assertThat(streamedQueries.size()).isEqualTo(Utils.NUM_TEST_QUERY_SESSION_MULTIPLE);
            for (int i = 0; i < Utils.NUM_TEST_QUERY_SESSION_MULTIPLE; i++) {
                if ((i & 1) == 0) {
                    assertThat(streamedQueries.get(i)).isEqualTo(
                            MainVisualQueryDetectionService.FAKE_QUERY_FIRST);
                } else {
                    assertThat(streamedQueries.get(i)).isEqualTo(
                            MainVisualQueryDetectionService.FAKE_QUERY_SECOND);
                }
            }
        } finally {
            visualQueryDetector.destroy();
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresDevice
    public void testVisualQueryDetectionService_startRecogintion_complexResultsQueryOnlySuccess()
            throws Throwable {
        // Create VisualQueryDetector
        VisualQueryDetector visualQueryDetector = createVisualQueryDetector();
        runWithShellPermissionIdentity(() -> {
            PersistableBundle options = Helper.createFakePersistableBundleData();
            options.putInt(MainVisualQueryDetectionService.KEY_VQDS_TEST_SCENARIO,
                    MainVisualQueryDetectionService.SCENARIO_COMPLEX_RESULT_STREAM_QUERY_ONLY);
            visualQueryDetector.updateState(options, Helper.createFakeSharedMemoryData());
        });
        try {
            adoptShellPermissionIdentityForVisualQueryDetection();

            mService.initQueryFinishRejectLatch(1);
            visualQueryDetector.startRecognition();

            // wait onStartDetection() called and verify the result
            mService.waitOnQueryFinishedRejectCalled();

            // verify results
            ArrayList<String> streamedQueries = mService.getStreamedQueriesResult();
            assertThat(streamedQueries.get(0)).isEqualTo(
                    MainVisualQueryDetectionService.FAKE_QUERY_FIRST
                            + MainVisualQueryDetectionService.FAKE_QUERY_SECOND);
            assertThat(streamedQueries.size()).isEqualTo(1);
        } finally {
            visualQueryDetector.destroy();
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresDevice
    public void testVisualQueryDetectionService_startRecogintion_noAttention()
            throws Throwable {
        // Create VisualQueryDetector
        VisualQueryDetector visualQueryDetector = createVisualQueryDetector();
        runWithShellPermissionIdentity(() -> {
            PersistableBundle options = Helper.createFakePersistableBundleData();
            options.putInt(MainVisualQueryDetectionService.KEY_VQDS_TEST_SCENARIO,
                    MainVisualQueryDetectionService.SCENARIO_QUERY_NO_ATTENTION);
            visualQueryDetector.updateState(options, Helper.createFakeSharedMemoryData());
        });
        try {
            adoptShellPermissionIdentityForVisualQueryDetection();

            visualQueryDetector.startRecognition();
            //TODO: Wait for the callback for more stable testing instead of sleeping.
            SystemClock.sleep(TEST_WAIT_TIMEOUT_MS); // reduce flakiness

            // verify results
            ArrayList<String> streamedQueries = mService.getStreamedQueriesResult();
            assertThat(streamedQueries.size()).isEqualTo(0);
        } finally {
            visualQueryDetector.destroy();
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresDevice
    public void testVisualQueryDetectionService_startRecogintion_noQueryFinish()
            throws Throwable {
        // Create VisualQueryDetector
        VisualQueryDetector visualQueryDetector = createVisualQueryDetector();
        runWithShellPermissionIdentity(() -> {
            PersistableBundle options = Helper.createFakePersistableBundleData();
            options.putInt(MainVisualQueryDetectionService.KEY_VQDS_TEST_SCENARIO,
                    MainVisualQueryDetectionService.SCENARIO_QUERY_NO_QUERY_FINISH);
            visualQueryDetector.updateState(options, Helper.createFakeSharedMemoryData());
        });
        try {
            adoptShellPermissionIdentityForVisualQueryDetection();

            visualQueryDetector.startRecognition();
            //TODO: Wait for the callback for more stable testing instead of sleeping.
            SystemClock.sleep(TEST_WAIT_TIMEOUT_MS); // reduce flakiness

            // verify results
            ArrayList<String> streamedQueries = mService.getStreamedQueriesResult();
            assertThat(streamedQueries.size()).isEqualTo(0);
        } finally {
            visualQueryDetector.destroy();
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresDevice
    public void testVisualQueryDetectionService_onStopDetection() throws Throwable {
        // Create VisualQueryDetector
        VisualQueryDetector visualQueryDetector = createVisualQueryDetector();
        try {
            adoptShellPermissionIdentityForVisualQueryDetection();

            // The VisualQueryDetectionService can't report any result after recognition is stopped.
            // So restart it after stopping; then the service can report a special result.
            visualQueryDetector.startRecognition();
            mService.initQueryFinishRejectLatch(1);
            visualQueryDetector.stopRecognition();
            visualQueryDetector.startRecognition();
            //TODO: Wait for the callback for more stable testing instead of sleeping.
            SystemClock.sleep(TEST_WAIT_TIMEOUT_MS);
            // verify results
            ArrayList<String> streamedQueries = mService.getStreamedQueriesResult();
            assertThat(streamedQueries.get(0)).isEqualTo(
                    MainVisualQueryDetectionService.FAKE_QUERY_SECOND);
            assertThat(streamedQueries.size()).isEqualTo(1);

        } finally {
            // Drop identity adopted.
            visualQueryDetector.destroy();
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    public void testVisualQueryDetectionService_onServiceRestarted() throws Throwable {
        // Create VisualQueryDetector
        VisualQueryDetector visualQueryDetector = createVisualQueryDetector();

        mService.initOnVisualQueryDetectionServiceRestartedLatch();
        // force re-start by shell command
        runShellCommand("cmd voiceinteraction restart-detection");

        // wait onHotwordDetectionServiceRestarted() called
        mService.waitOnVisualQueryDetectionServiceRestartedCalled();

        // Destroy the always on detector
        visualQueryDetector.destroy();
    }

    @Test
    public void testVisualQueryDetectionService_openFileReadOnly_success() throws Throwable {
        mService.createTestFile("");

        VisualQueryDetector visualQueryDetector = createVisualQueryDetector();
        runWithShellPermissionIdentity(() -> {
            PersistableBundle options = Helper.createFakePersistableBundleData();
            options.putInt(MainVisualQueryDetectionService.KEY_VQDS_TEST_SCENARIO,
                    MainVisualQueryDetectionService.SCENARIO_READ_FILE_MMAP_READ_ONLY);
            visualQueryDetector.updateState(options, Helper.createFakeSharedMemoryData());
        });
        try {
            // leverage isolated detection callback APIs to verify file contents
            adoptShellPermissionIdentityForVisualQueryDetection();

            mService.initQueryFinishRejectLatch(1);
            visualQueryDetector.startRecognition();

            // wait onStartDetection() called and verify the result
            mService.waitOnQueryFinishedRejectCalled();

            // verify results
            ArrayList<String> streamedQueries = mService.getStreamedQueriesResult();
            assertThat(streamedQueries.get(0)).isEqualTo(Utils.TEST_RESOURCE_FILE_CONTENT);
            assertThat(streamedQueries.size()).isEqualTo(1);
        } finally {
            visualQueryDetector.destroy();
            // Drop identity adopted.
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
            mService.removeTestFiles();
        }
    }

    @Test
    public void testVisualQueryDetectionService_openFileNotExist_failure() throws Throwable {
        VisualQueryDetector visualQueryDetector = createVisualQueryDetector();
        runWithShellPermissionIdentity(() -> {
            PersistableBundle options = Helper.createFakePersistableBundleData();
            options.putInt(MainVisualQueryDetectionService.KEY_VQDS_TEST_SCENARIO,
                    MainVisualQueryDetectionService.SCENARIO_READ_FILE_FILE_NOT_EXIST);
            visualQueryDetector.updateState(options, Helper.createFakeSharedMemoryData());
        });
        try {
            // leverage isolated detection callback APIs to verify file contents
            adoptShellPermissionIdentityForVisualQueryDetection();

            mService.initQueryFinishRejectLatch(1);
            visualQueryDetector.startRecognition();

            // wait onStartDetection() called and verify the result
            mService.waitOnQueryFinishedRejectCalled();

            // verify results
            ArrayList<String> streamedQueries = mService.getStreamedQueriesResult();
            assertThat(streamedQueries.get(0)).isEqualTo(
                    MainVisualQueryDetectionService.MSG_FILE_NOT_FOUND);
            assertThat(streamedQueries.size()).isEqualTo(1);
        } finally {
            visualQueryDetector.destroy();
            // Drop identity adopted.
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    public void testVisualQueryDetectionService_openFileWrite_failure() throws Throwable {
        mService.createTestFile("");

        VisualQueryDetector visualQueryDetector = createVisualQueryDetector();
        runWithShellPermissionIdentity(() -> {
            PersistableBundle options = Helper.createFakePersistableBundleData();
            options.putInt(MainVisualQueryDetectionService.KEY_VQDS_TEST_SCENARIO,
                    MainVisualQueryDetectionService.SCENARIO_READ_FILE_MMAP_WRITE);
            visualQueryDetector.updateState(options, Helper.createFakeSharedMemoryData());
        });
        try {
            // leverage isolated detection callback APIs to verify file contents
            adoptShellPermissionIdentityForVisualQueryDetection();

            mService.initQueryFinishRejectLatch(1);
            visualQueryDetector.startRecognition();

            // wait onStartDetection() called and verify the result
            mService.waitOnQueryFinishedRejectCalled();

            // verify results
            ArrayList<String> streamedQueries = mService.getStreamedQueriesResult();
            assertThat(streamedQueries.get(0)).isEqualTo(
                    MainVisualQueryDetectionService.MSG_FILE_NOT_WRITABLE);
            assertThat(streamedQueries.size()).isEqualTo(1);
        } finally {
            visualQueryDetector.destroy();
            // Drop identity adopted.
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
            mService.removeTestFiles();
        }
    }

    @Test
    public void testVisualQueryDetectionService_openFileMultiple_success() throws Throwable {
        for (int i = 0; i < Utils.NUM_TEST_RESOURCE_FILE_MULTIPLE; i++) {
            mService.createTestFile(Integer.toString(i));
        }

        VisualQueryDetector visualQueryDetector = createVisualQueryDetector();
        runWithShellPermissionIdentity(() -> {
            PersistableBundle options = Helper.createFakePersistableBundleData();
            options.putInt(MainVisualQueryDetectionService.KEY_VQDS_TEST_SCENARIO,
                    MainVisualQueryDetectionService.SCENARIO_READ_FILE_MMAP_MULTIPLE);
            visualQueryDetector.updateState(options, Helper.createFakeSharedMemoryData());
        });
        try {
            // leverage isolated detection callback APIs to verify file contents
            adoptShellPermissionIdentityForVisualQueryDetection();

            mService.initQueryFinishRejectLatch(Utils.NUM_TEST_RESOURCE_FILE_MULTIPLE);
            visualQueryDetector.startRecognition();

            // wait onStartDetection() called and verify the result
            mService.waitOnQueryFinishedRejectCalled();

            // verify results
            ArrayList<String> streamedQueries = mService.getStreamedQueriesResult();
            assertThat(streamedQueries.size()).isEqualTo(Utils.NUM_TEST_RESOURCE_FILE_MULTIPLE);
            for (String result: streamedQueries) {
                assertThat(result).isEqualTo(Utils.TEST_RESOURCE_FILE_CONTENT);
            }
        } finally {
            visualQueryDetector.destroy();
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
            mService.removeTestFiles();
        }
    }

    private void adoptShellPermissionIdentityForVisualQueryDetection() {
        // Drop any identity adopted earlier.
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.dropShellPermissionIdentity();
        // need to retain the identity until the callback is triggered
        uiAutomation.adoptShellPermissionIdentity(RECORD_AUDIO, CAMERA);
    }

     /**
     * Create VisualQueryDetector and wait for ready
     */
    private VisualQueryDetector createVisualQueryDetector() throws Throwable {

        mService.createVisualQueryDetector();

        mService.waitSandboxedDetectionServiceInitializedCalledOrException();

        assertThat(mService.getSandboxedDetectionServiceInitializedResult()).isEqualTo(
                INITIALIZATION_STATUS_SUCCESS);

        // The VisualQueryDetector should be created correctly
        VisualQueryDetector visualQueryDetector = mService.getVisualQueryDetector();
        Objects.requireNonNull(visualQueryDetector);

        return visualQueryDetector;
    }

    private boolean hasFeature(String feature) {
        return mPackageManager.hasSystemFeature(feature);
    }

}
