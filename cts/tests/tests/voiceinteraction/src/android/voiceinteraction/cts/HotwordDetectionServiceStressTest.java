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

import static android.Manifest.permission.CAPTURE_AUDIO_HOTWORD;
import static android.Manifest.permission.MANAGE_HOTWORD_DETECTION;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.content.pm.PackageManager.FEATURE_MICROPHONE;
import static android.voiceinteraction.cts.testcore.Helper.CTS_SERVICE_PACKAGE;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.app.AppOpsManager;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.HotwordDetectionService;
import android.service.voice.HotwordDetector;
import android.util.Log;
import android.voiceinteraction.common.Utils;
import android.voiceinteraction.cts.services.BaseVoiceInteractionService;
import android.voiceinteraction.cts.services.CtsBasicVoiceInteractionService;
import android.voiceinteraction.cts.testcore.Helper;
import android.voiceinteraction.cts.testcore.VoiceInteractionServiceConnectedRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.RequiresDevice;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.DisableAnimationRule;
import com.android.compatibility.common.util.RequiredFeatureRule;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Stress tests for {@link HotwordDetectionService}.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "No real use case for instant mode hotword detection service")
public class HotwordDetectionServiceStressTest extends AbstractHdsTestCase {

    private static final String TAG = "HotwordDetectionServiceStressTest";

    private static final int NUMBER_OF_HDS_STRESS_LOOPS = 5;

    // The VoiceInteractionService used by this test
    private static final String SERVICE_COMPONENT =
            "android.voiceinteraction.cts.services.CtsBasicVoiceInteractionService";

    private CountDownLatch mLatch = null;

    private String mOpNoted = "";

    private final AppOpsManager mAppOpsManager = sInstrumentation.getContext()
            .getSystemService(AppOpsManager.class);

    private final AppOpsManager.OnOpNotedListener mOnOpNotedListener =
            (op, uid, pkgName, attributionTag, flags, result) -> {
                Log.d(TAG, "Get OnOpNotedListener callback op = " + op + ", uid = " + uid);
                // We adopt ShellPermissionIdentity for RECORD_AUDIO to pass the permission check,
                // so the uid should be the shell uid.
                if (Process.SHELL_UID == uid && op.equals(AppOpsManager.OPSTR_RECORD_AUDIO)) {
                    if (mLatch != null) {
                        mLatch.countDown();
                    }
                }
                // We do not adopt ShellPermissionIdentity for RECEIVE_SANDBOX_TRIGGER_AUDIO so the
                // uid should be the current process uid.
                if (Process.myUid() == uid && op.equals(RECEIVE_SANDBOX_TRIGGER_AUDIO_OP_STR)) {
                    if (mLatch != null) {
                        mLatch.countDown();
                    }
                }
                mOpNoted = op;
            };

    private CtsBasicVoiceInteractionService mService;

    private static String sWasIndicatorEnabled;
    private static String sDefaultScreenOffTimeoutValue;
    private static final Instrumentation sInstrumentation =
            InstrumentationRegistry.getInstrumentation();
    private static final PackageManager sPkgMgr = sInstrumentation.getContext().getPackageManager();

    @Rule
    public VoiceInteractionServiceConnectedRule mConnectedRule =
            new VoiceInteractionServiceConnectedRule(
                    getInstrumentation().getTargetContext(), getTestVoiceInteractionService());

    @Rule
    public DisableAnimationRule mDisableAnimationRule = new DisableAnimationRule();

    @Rule
    public RequiredFeatureRule REQUIRES_MIC_RULE = new RequiredFeatureRule(FEATURE_MICROPHONE);

    @BeforeClass
    public static void enableIndicators() {
        sWasIndicatorEnabled = Helper.getIndicatorEnabledState();
        Helper.setIndicatorEnabledState(Boolean.toString(true));
    }

    @AfterClass
    public static void resetIndicators() {
        Helper.setIndicatorEnabledState(sWasIndicatorEnabled);
    }

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
        // VoiceInteractionServiceConnectedRule handles the service connected,
        // the test should be able to get service
        mService = (CtsBasicVoiceInteractionService) BaseVoiceInteractionService.getService();

        // Check the test can get the service
        Objects.requireNonNull(mService);

        // Set whether voice activation permission check is enabled.
        mService.setVoiceActivationPermissionEnabled(mVoiceActivationPermissionEnabled);

        // Wait the original HotwordDetectionService finish clean up to avoid flaky
        // This also waits for mic indicator disappear
        SystemClock.sleep(5_000);
    }

    @After
    public void tearDown() {
        mService = null;
    }

    public String getTestVoiceInteractionService() {
        Log.d(TAG, "getTestVoiceInteractionService()");
        return CTS_SERVICE_PACKAGE + "/" + SERVICE_COMPONENT;
    }

    @Test
    public void testHotwordDetectionService_createAlwaysOnHotwordDetector_success()
            throws Throwable {
        for (int i = 0; i < NUMBER_OF_HDS_STRESS_LOOPS; i++) {
            // Create AlwaysOnHotwordDetector
            AlwaysOnHotwordDetector alwaysOnHotwordDetector = createAlwaysOnHotwordDetector();

            // Destroy detector
            alwaysOnHotwordDetector.destroy();
        }
    }

    @Test
    public void testHotwordDetectionService_createSoftwareHotwordDetector_success()
            throws Throwable {
        for (int i = 0; i < NUMBER_OF_HDS_STRESS_LOOPS; i++) {
            // Create SoftwareHotwordDetector
            HotwordDetector softwareHotwordDetector = createSoftwareHotwordDetector();

            // Destroy detector
            softwareHotwordDetector.destroy();
        }
    }

    @Test
    @RequiresDevice
    public void testHotwordDetectionService_onDetectFromDsp_success() throws Throwable {
        // Create AlwaysOnHotwordDetector
        AlwaysOnHotwordDetector alwaysOnHotwordDetector = createAlwaysOnHotwordDetector();

        try {
            startWatchingNoted();
            adoptShellPermissionIdentityForHotword();

            for (int i = 0; i < NUMBER_OF_HDS_STRESS_LOOPS; i++) {
                mLatch = new CountDownLatch(1);
                verifyOnDetectFromDspSuccess(alwaysOnHotwordDetector);

                // Verify RECORD_AUDIO noted
                verifyRecordAudioNote(/* shouldNote= */ true);
            }

        } finally {
            // Destroy detector
            alwaysOnHotwordDetector.destroy();
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
            stopWatchingNoted();
        }
    }

    @Test
    public void testHotwordDetectionService_onDetectFromExternalSource_success() throws Throwable {
        // Create AlwaysOnHotwordDetector
        AlwaysOnHotwordDetector alwaysOnHotwordDetector = createAlwaysOnHotwordDetector();

        try {
            startWatchingNoted();
            adoptShellPermissionIdentityForHotword();

            for (int i = 0; i < NUMBER_OF_HDS_STRESS_LOOPS; i++) {
                mLatch = new CountDownLatch(1);
                ParcelFileDescriptor audioStream = Helper.createFakeAudioStream();
                mService.resetHotwordServiceOnDetectedAndOnRejectedResult();
                mService.initDetectRejectLatch();
                alwaysOnHotwordDetector.startRecognition(audioStream,
                        Helper.createFakeAudioFormat(),
                        Helper.createFakePersistableBundleData());

                // Wait onDetected() called and verify the result
                mService.waitOnDetectOrRejectCalled();
                AlwaysOnHotwordDetector.EventPayload detectResult =
                        mService.getHotwordServiceOnDetectedResult();

                Helper.verifyDetectedResult(detectResult, Helper.DETECTED_RESULT);

                // Verify RECORD_AUDIO noted
                verifyRecordAudioNote(/* shouldNote= */ true);
            }

        } finally {
            // Destroy detector
            alwaysOnHotwordDetector.destroy();
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
            stopWatchingNoted();
        }
    }

    @Test
    @RequiresDevice
    public void testHotwordDetectionService_onDetectFromMic_success() throws Throwable {
        // Create SoftwareHotwordDetector
        HotwordDetector softwareHotwordDetector = createSoftwareHotwordDetector();

        try {
            startWatchingNoted();
            adoptShellPermissionIdentityForHotword();

            PersistableBundle persistableBundle = new PersistableBundle();
            persistableBundle.putInt(Helper.KEY_TEST_SCENARIO,
                    Utils.EXTRA_HOTWORD_DETECTION_SERVICE_CLEAR_SOFTWARE_DETECTION_JOB);

            for (int i = 0; i < NUMBER_OF_HDS_STRESS_LOOPS; i++) {
                mLatch = new CountDownLatch(1);
                verifySoftwareDetectorDetectSuccess(softwareHotwordDetector);

                // Verify RECORD_AUDIO noted
                verifyRecordAudioNote(/* shouldNote= */ true);

                softwareHotwordDetector.updateState(persistableBundle,
                        Helper.createFakeSharedMemoryData());
            }

        } finally {
            // Destroy detector
            softwareHotwordDetector.destroy();
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
            stopWatchingNoted();
        }
    }

    @Test
    @RequiresDevice
    public void testMultipleDetectors_onDetectFromDspAndMic_success() throws Throwable {
        assumeTrue("Not support multiple hotword detectors",
                Helper.isEnableMultipleDetectors());

        // Create AlwaysOnHotwordDetector
        AlwaysOnHotwordDetector alwaysOnHotwordDetector = createAlwaysOnHotwordDetector();

        // Create SoftwareHotwordDetector
        HotwordDetector softwareHotwordDetector = createSoftwareHotwordDetector();

        try {
            adoptShellPermissionIdentityForHotword();

            PersistableBundle persistableBundle = new PersistableBundle();
            persistableBundle.putInt(Helper.KEY_TEST_SCENARIO,
                    Utils.EXTRA_HOTWORD_DETECTION_SERVICE_CLEAR_SOFTWARE_DETECTION_JOB);

            for (int i = 0; i < NUMBER_OF_HDS_STRESS_LOOPS; i++) {
                // Test AlwaysOnHotwordDetector to be able to detect well
                verifyOnDetectFromDspSuccess(alwaysOnHotwordDetector);

                // Test SoftwareHotwordDetector to be able to detect well
                verifySoftwareDetectorDetectSuccess(softwareHotwordDetector);

                softwareHotwordDetector.updateState(persistableBundle,
                        Helper.createFakeSharedMemoryData());
            }

        } finally {
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
            // Destroy the always on detector
            alwaysOnHotwordDetector.destroy();

            // Destroy the software detector
            softwareHotwordDetector.destroy();
        }
    }

    private void verifyOnDetectFromDspSuccess(AlwaysOnHotwordDetector alwaysOnHotwordDetector)
            throws Throwable {
        mService.resetHotwordServiceOnDetectedAndOnRejectedResult();
        mService.initDetectRejectLatch();
        alwaysOnHotwordDetector.triggerHardwareRecognitionEventForTest(
                /* status= */ 0, /* soundModelHandle= */ 100,
                /* halEventReceivedMillis */ 12345, /* captureAvailable= */ true,
                /* captureSession= */ 101, /* captureDelayMs= */ 1000,
                /* capturePreambleMs= */ 1001, /* triggerInData= */ true,
                Helper.createFakeAudioFormat(), new byte[1024],
                Helper.createFakeKeyphraseRecognitionExtraList());

        // Wait onDetected() called and verify the result
        mService.waitOnDetectOrRejectCalled();
        AlwaysOnHotwordDetector.EventPayload detectResult =
                mService.getHotwordServiceOnDetectedResult();

        Helper.verifyDetectedResult(detectResult, Helper.DETECTED_RESULT);
    }

    private void verifySoftwareDetectorDetectSuccess(HotwordDetector softwareHotwordDetector)
            throws Exception {
        mService.resetHotwordServiceOnDetectedAndOnRejectedResult();
        mService.initDetectRejectLatch();
        softwareHotwordDetector.startRecognition();

        // Wait onDetected() called and verify the result
        mService.waitOnDetectOrRejectCalled();
        AlwaysOnHotwordDetector.EventPayload detectResult =
                mService.getHotwordServiceOnDetectedResult();
        Helper.verifyDetectedResult(detectResult, Helper.DETECTED_RESULT);
    }

    /**
     * Create AlwaysOnHotwordDetector and wait for ready
     */
    private AlwaysOnHotwordDetector createAlwaysOnHotwordDetector()
            throws Throwable {
        // Create AlwaysOnHotwordDetector and wait ready.
        mService.createAlwaysOnHotwordDetectorWithOnFailureCallback(/* useExecutor= */
                false, /* runOnMainThread= */ false);

        mService.waitSandboxedDetectionServiceInitializedCalledOrException();

        // verify callback result
        // TODO: not use Deprecated variable
        assertThat(mService.getSandboxedDetectionServiceInitializedResult()).isEqualTo(
                HotwordDetectionService.INITIALIZATION_STATUS_SUCCESS);
        AlwaysOnHotwordDetector alwaysOnHotwordDetector = mService.getAlwaysOnHotwordDetector();
        Objects.requireNonNull(alwaysOnHotwordDetector);

        return alwaysOnHotwordDetector;
    }

    /**
     * Create software hotword detector and wait for ready
     */
    private HotwordDetector createSoftwareHotwordDetector() throws Throwable {
        // Create SoftwareHotwordDetector
        mService.createSoftwareHotwordDetectorWithOnFailureCallback(/* useExecutor= */
                    false, /* runOnMainThread= */ false);

        mService.waitSandboxedDetectionServiceInitializedCalledOrException();

        // verify callback result
        // TODO: not use Deprecated variable
        assertThat(mService.getSandboxedDetectionServiceInitializedResult()).isEqualTo(
                HotwordDetectionService.INITIALIZATION_STATUS_SUCCESS);
        HotwordDetector softwareHotwordDetector = mService.getSoftwareHotwordDetector();
        Objects.requireNonNull(softwareHotwordDetector);

        return softwareHotwordDetector;
    }

    private void adoptShellPermissionIdentityForHotword() {
        // Drop any identity adopted earlier.
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.dropShellPermissionIdentity();
        // need to retain the identity until the callback is triggered
        uiAutomation.adoptShellPermissionIdentity(RECORD_AUDIO, CAPTURE_AUDIO_HOTWORD,
                MANAGE_HOTWORD_DETECTION);
    }

    private void startWatchingNoted() {
        runWithShellPermissionIdentity(() -> {
            if (mAppOpsManager != null) {
                mAppOpsManager.startWatchingNoted(new String[]{
                        AppOpsManager.OPSTR_RECORD_AUDIO, RECEIVE_SANDBOX_TRIGGER_AUDIO_OP_STR},
                        mOnOpNotedListener);
            }
        });
    }

    private void stopWatchingNoted() {
        runWithShellPermissionIdentity(() -> {
            if (mAppOpsManager != null) {
                mAppOpsManager.stopWatchingNoted(mOnOpNotedListener);
            }
        });
    }

    private void verifyRecordAudioNote(boolean shouldNote) throws Exception {
        if (sPkgMgr.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            // TODO: test TV indicator
        } else if (sPkgMgr.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            // TODO: test Auto indicator
        } else if (sPkgMgr.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            // The privacy chips/indicators are not implemented on Wear
        } else if (mVoiceActivationPermissionEnabled) {
            boolean isNoted = mLatch.await(Helper.CLEAR_CHIP_MS, TimeUnit.MILLISECONDS);
            assertThat(isNoted).isEqualTo(shouldNote);
            if (isNoted) {
                assertThat(mOpNoted).isEqualTo(RECEIVE_SANDBOX_TRIGGER_AUDIO_OP_STR);
            }
        } else {
            boolean isNoted = mLatch.await(Helper.CLEAR_CHIP_MS, TimeUnit.MILLISECONDS);
            assertThat(isNoted).isEqualTo(shouldNote);
            if (isNoted) {
                assertThat(mOpNoted).isEqualTo(AppOpsManager.OPSTR_RECORD_AUDIO);
            }
        }
    }
}
