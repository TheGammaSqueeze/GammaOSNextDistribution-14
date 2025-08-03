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

package android.voiceinteraction.cts;

import static android.Manifest.permission.CAPTURE_AUDIO_HOTWORD;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.content.Context.ATTENTION_SERVICE;
import static android.service.voice.HotwordDetectedResult.PROXIMITY_FAR;
import static android.service.voice.HotwordDetectedResult.PROXIMITY_NEAR;
import static android.service.voice.HotwordDetectedResult.PROXIMITY_UNKNOWN;
import static android.service.voice.HotwordDetectionService.ENABLE_PROXIMITY_RESULT;
import static android.voiceinteraction.cts.testcore.Helper.CTS_SERVICE_PACKAGE;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.attentionservice.cts.CtsTestAttentionService;
import android.content.res.Resources;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AppModeFull;
import android.provider.DeviceConfig;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.HotwordDetectedResult;
import android.service.voice.HotwordDetectionService;
import android.service.voice.HotwordDetector;
import android.util.Log;
import android.voiceinteraction.cts.services.BaseVoiceInteractionService;
import android.voiceinteraction.cts.services.CtsBasicVoiceInteractionService;
import android.voiceinteraction.cts.testcore.Helper;
import android.voiceinteraction.cts.testcore.VoiceInteractionServiceConnectedRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.RequiresDevice;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.DeviceConfigStateChangerRule;
import com.android.compatibility.common.util.RequiredServiceRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;

/**
 * Tests for using the Attention Service inside VoiceInteractionService using
 * a basic HotwordDetectionService.
 */

@ApiTest(apis = {"android.service.voice.HotwordDetectedResult#getExtras"})
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "No real use case for instant mode hotword detection service")
public final class HotwordDetectionServiceProximityTest {

    private static final String TAG = "HotwordDetectionServiceProximityTest";
    private static final String SERVICE_ENABLED = "service_enabled";
    // The VoiceInteractionService used by this test
    private static final String SERVICE_COMPONENT =
            "android.voiceinteraction.cts.services.CtsBasicVoiceInteractionService";

    private static final double PROXIMITY_NEAR_METERS = 2.0;
    private static final double PROXIMITY_FAR_METERS = 6.0;
    private static final String FAKE_SERVICE_PACKAGE =
            HotwordDetectionServiceProximityTest.class.getPackage().getName();

    @Rule
    public final RequiredServiceRule ATTENTION_SERVICE_RULE =
            new RequiredServiceRule(ATTENTION_SERVICE);

    @Rule
    public final DeviceConfigStateChangerRule mEnableAttentionManagerServiceRule =
            new DeviceConfigStateChangerRule(sInstrumentation.getTargetContext(),
                    DeviceConfig.NAMESPACE_ATTENTION_MANAGER_SERVICE,
                    SERVICE_ENABLED,
                    "true");

    @Rule
    public VoiceInteractionServiceConnectedRule mConnectedRule =
            new VoiceInteractionServiceConnectedRule(
                    getInstrumentation().getTargetContext(), getTestVoiceInteractionService());

    private CtsBasicVoiceInteractionService mService;

    private static final Instrumentation sInstrumentation =
            InstrumentationRegistry.getInstrumentation();

    private final Resources mResources = getInstrumentation().getTargetContext()
            .getResources();

    private boolean mIsProximitySupported;

    @Before
    public void setup() {
        try {
            mIsProximitySupported = mResources.getBoolean(
                    mResources.getIdentifier("config_enableProximityService", "bool", "android"));
        } catch (Resources.NotFoundException e) {
            // this would mean resource is not found in device. skip test
        }
        assumeTrue("Proximity Feature not available on this device. Skipping test.",
                mIsProximitySupported);

        // Set up Attention Service
        CtsTestAttentionService.reset();
        assertThat(setTestableAttentionService(FAKE_SERVICE_PACKAGE)).isTrue();
        assertThat(getAttentionServiceComponent()).contains(FAKE_SERVICE_PACKAGE);
        runShellCommand("cmd attention call checkAttention");

        // VoiceInteractionServiceConnectedRule handles the service connected,
        // the test should be able to get service
        mService = (CtsBasicVoiceInteractionService) BaseVoiceInteractionService.getService();
        // Check the test can get the service
        Objects.requireNonNull(mService);
    }

    @After
    public void tearDown() {
        runShellCommand("cmd attention clearTestableAttentionService");
        mService = null;
    }

    @Test
    @RequiresDevice
    public void testAttentionService_onDetectFromDsp() throws Throwable {
        // Create AlwaysOnHotwordDetector and wait the HotwordDetectionService ready
        AlwaysOnHotwordDetector alwaysOnHotwordDetector = createAlwaysOnHotwordDetector();

        try {
            adoptShellPermissionIdentityForHotword();

            // Trigger recognition for test
            triggerHardwareRecognitionEventForTest(alwaysOnHotwordDetector);

            // wait onDetected() called and verify the result
            HotwordDetectedResult hotwordDetectedResult1 = waitHotwordServiceOnDetectedResult();

            // by default, proximity should not be returned.
            verifyProximityBundle(hotwordDetectedResult1, null);

            // proximity is unknown
            CtsTestAttentionService.respondProximity(PROXIMITY_UNKNOWN);

            // Trigger recognition for test
            triggerHardwareRecognitionEventForTest(alwaysOnHotwordDetector);

            // wait onDetected() called and verify the result
            HotwordDetectedResult hotwordDetectedResult2 = waitHotwordServiceOnDetectedResult();

            // when proximity is unknown, proximity should not be returned.
            verifyProximityBundle(hotwordDetectedResult2, null);

            // proximity is PROXIMITY_NEAR_METERS
            CtsTestAttentionService.respondProximity(PROXIMITY_NEAR_METERS);

            // Trigger recognition for test
            triggerHardwareRecognitionEventForTest(alwaysOnHotwordDetector);

            // wait onDetected() called and verify the result
            HotwordDetectedResult hotwordDetectedResult3 = waitHotwordServiceOnDetectedResult();

            // when proximity is PROXIMITY_NEAR_METERS, proximity should be PROXIMITY_NEAR.
            verifyProximityBundle(hotwordDetectedResult3, PROXIMITY_NEAR);

            // proximity is PROXIMITY_FAR_METERS
            CtsTestAttentionService.respondProximity(PROXIMITY_FAR_METERS);

            // Trigger recognition for test
            triggerHardwareRecognitionEventForTest(alwaysOnHotwordDetector);

            // wait onDetected() called and verify the result
            HotwordDetectedResult hotwordDetectedResult4 = waitHotwordServiceOnDetectedResult();

            // when proximity is PROXIMITY_FAR_METERS, proximity should be PROXIMITY_FAR.
            verifyProximityBundle(hotwordDetectedResult4, PROXIMITY_FAR);
        } finally {
            alwaysOnHotwordDetector.destroy();
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresDevice
    public void testAttentionService_onDetectFromMic_noUpdates() throws Throwable {
        // Create SoftwareHotwordDetector
        HotwordDetector softwareHotwordDetector = createSoftwareHotwordDetector();
        try {
            adoptShellPermissionIdentityForHotword();

            mService.initDetectRejectLatch();
            softwareHotwordDetector.startRecognition();

            // wait onDetected() called and verify the result
            HotwordDetectedResult hotwordDetectedResult = waitHotwordServiceOnDetectedResult();

            verifyProximityBundle(hotwordDetectedResult, null);
        } finally {
            softwareHotwordDetector.destroy();
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresDevice
    public void testAttentionService_onDetectFromMic_unknownProximity() throws Throwable {
        // Create SoftwareHotwordDetector
        HotwordDetector softwareHotwordDetector = createSoftwareHotwordDetector();

        CtsTestAttentionService.respondProximity(PROXIMITY_UNKNOWN);

        try {
            adoptShellPermissionIdentityForHotword();

            mService.initDetectRejectLatch();
            softwareHotwordDetector.startRecognition();

            // wait onDetected() called and verify the result
            HotwordDetectedResult hotwordDetectedResult = waitHotwordServiceOnDetectedResult();

            verifyProximityBundle(hotwordDetectedResult, null);
        } finally {
            softwareHotwordDetector.destroy();
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresDevice
    public void testAttentionService_onDetectFromMic_updatedProximity() throws Throwable {
        // Create SoftwareHotwordDetector
        HotwordDetector softwareHotwordDetector = createSoftwareHotwordDetector();

        CtsTestAttentionService.respondProximity(PROXIMITY_NEAR_METERS);

        try {
            adoptShellPermissionIdentityForHotword();

            mService.initDetectRejectLatch();
            softwareHotwordDetector.startRecognition();

            // wait onDetected() called and verify the result
            HotwordDetectedResult hotwordDetectedResult = waitHotwordServiceOnDetectedResult();

            verifyProximityBundle(hotwordDetectedResult, PROXIMITY_NEAR);
        } finally {
            softwareHotwordDetector.destroy();
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresDevice
    public void testAttentionService_onDetectFromExternalSource_doesNotReceiveProximity()
            throws Throwable {
        AlwaysOnHotwordDetector alwaysOnHotwordDetector = createAlwaysOnHotwordDetector();
        CtsTestAttentionService.respondProximity(PROXIMITY_FAR);

        try {
            adoptShellPermissionIdentityForHotword();

            ParcelFileDescriptor audioStream = Helper.createFakeAudioStream();
            mService.initDetectRejectLatch();
            alwaysOnHotwordDetector.startRecognition(audioStream,
                    Helper.createFakeAudioFormat(),
                    Helper.createFakePersistableBundleData());

            // wait onDetected() called and verify the result
            HotwordDetectedResult hotwordDetectedResult = waitHotwordServiceOnDetectedResult();

            verifyProximityBundle(hotwordDetectedResult, null);
        } finally {
            alwaysOnHotwordDetector.destroy();
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    public String getTestVoiceInteractionService() {
        Log.d(TAG, "getTestVoiceInteractionService()");
        return CTS_SERVICE_PACKAGE + "/" + SERVICE_COMPONENT;
    }

    private static String getAttentionServiceComponent() {
        return runShellCommand("cmd attention getAttentionServiceComponent");
    }

    private static boolean setTestableAttentionService(String service) {
        return runShellCommand("cmd attention setTestableAttentionService " + service)
                .equals("true");
    }

    // TODO: use a base test case and move common part to base test class
    private void triggerHardwareRecognitionEventForTest(
            AlwaysOnHotwordDetector alwaysOnHotwordDetector) {
        mService.initDetectRejectLatch();
        alwaysOnHotwordDetector.triggerHardwareRecognitionEventForTest(
                /* status= */ 0, /* soundModelHandle= */ 100,
                /* halEventReceivedMillis= */ 12345, /* captureAvailable= */ true,
                /* captureSession= */ 101, /* captureDelayMs= */ 1000,
                /* capturePreambleMs= */ 1001, /* triggerInData= */ true,
                Helper.createFakeAudioFormat(), new byte[1024],
                Helper.createFakeKeyphraseRecognitionExtraList());
    }

    private HotwordDetectedResult waitHotwordServiceOnDetectedResult() throws Throwable {
        mService.waitOnDetectOrRejectCalled();
        AlwaysOnHotwordDetector.EventPayload detectedResult =
                mService.getHotwordServiceOnDetectedResult();
        return detectedResult.getHotwordDetectedResult();
    }

    private void adoptShellPermissionIdentityForHotword() {
        // Drop any identity adopted earlier.
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.dropShellPermissionIdentity();
        // need to retain the identity until the callback is triggered
        uiAutomation.adoptShellPermissionIdentity(RECORD_AUDIO, CAPTURE_AUDIO_HOTWORD);
    }

    /**
     * Create software hotword detector and wait for ready
     */
    private HotwordDetector createSoftwareHotwordDetector() throws Throwable {
        // Create SoftwareHotwordDetector
        mService.createSoftwareHotwordDetector();

        mService.waitSandboxedDetectionServiceInitializedCalledOrException();

        // verify callback result
        assertThat(mService.getSandboxedDetectionServiceInitializedResult()).isEqualTo(
                HotwordDetectionService.INITIALIZATION_STATUS_SUCCESS);
        HotwordDetector softwareHotwordDetector = mService.getSoftwareHotwordDetector();
        Objects.requireNonNull(softwareHotwordDetector);

        return softwareHotwordDetector;
    }

    /**
     * Create AlwaysOnHotwordDetector and wait for ready
     */
    private AlwaysOnHotwordDetector createAlwaysOnHotwordDetector() throws Throwable {
        // Create AlwaysOnHotwordDetector and wait ready.
        mService.createAlwaysOnHotwordDetector();

        mService.waitSandboxedDetectionServiceInitializedCalledOrException();

        // verify callback result
        assertThat(mService.getSandboxedDetectionServiceInitializedResult()).isEqualTo(
                HotwordDetectionService.INITIALIZATION_STATUS_SUCCESS);
        AlwaysOnHotwordDetector alwaysOnHotwordDetector = mService.getAlwaysOnHotwordDetector();
        Objects.requireNonNull(alwaysOnHotwordDetector);

        return alwaysOnHotwordDetector;
    }

    // simply check that the proximity values are equal.
    private void verifyProximityBundle(HotwordDetectedResult hotwordDetectedResult,
            Integer expected) {
        assertThat(hotwordDetectedResult).isNotNull();
        if (expected == null || !ENABLE_PROXIMITY_RESULT) {
            assertThat(hotwordDetectedResult.getProximity()).isEqualTo(PROXIMITY_UNKNOWN);
        } else {
            assertThat(hotwordDetectedResult.getProximity()).isEqualTo(expected);
        }
    }
}
