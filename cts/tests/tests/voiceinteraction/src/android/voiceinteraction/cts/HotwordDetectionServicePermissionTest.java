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
import static android.content.pm.PackageManager.FEATURE_MICROPHONE;
import static android.content.pm.PackageManager.FEATURE_TELEPHONY;
import static android.voiceinteraction.cts.testcore.Helper.CTS_SERVICE_PACKAGE;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.app.UiAutomation;
import android.content.Context;
import android.media.AudioManager;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.HotwordDetectionService;
import android.util.Log;
import android.voiceinteraction.cts.services.BaseVoiceInteractionService;
import android.voiceinteraction.cts.services.TestPermissionVoiceInteractionService;
import android.voiceinteraction.cts.testcore.Helper;
import android.voiceinteraction.cts.testcore.VoiceInteractionServiceConnectedRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.RequiresDevice;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.RequiredFeatureRule;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;


/**
 * Tests for testing the permissions in the HotwordDetectionService.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "No real use case for instant mode hotword detection service")
public class HotwordDetectionServicePermissionTest {

    private static final String TAG = "HotwordDetectionServicePermissionTest";
    // The VoiceInteractionService used by this test
    private static final String SERVICE_COMPONENT =
            "android.voiceinteraction.cts.services.TestPermissionVoiceInteractionService";

    private TestPermissionVoiceInteractionService mService;

    @Rule
    public VoiceInteractionServiceConnectedRule mConnectedRule =
            new VoiceInteractionServiceConnectedRule(
                    getInstrumentation().getTargetContext(), getTestVoiceInteractionService());

    @Rule
    public RequiredFeatureRule REQUIRES_MIC_RULE = new RequiredFeatureRule(FEATURE_MICROPHONE);

    @Rule
    public RequiredFeatureRule REQUIRES_TELEPHONY_RULE = new RequiredFeatureRule(FEATURE_TELEPHONY);

    private AudioManager mAudioManager;

    public void setup() {
        // VoiceInteractionServiceConnectedRule handles the service connected,
        // the test should be able to get service
        mService = (TestPermissionVoiceInteractionService) BaseVoiceInteractionService.getService();
        // Check the test can get the service
        Objects.requireNonNull(mService);

        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        mAudioManager = context.getSystemService(AudioManager.class);

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.CALL_AUDIO_INTERCEPTION);

        // Wait the original HotwordDetectionService finish clean up to avoid flaky
        // This also waits for mic indicator disappear
        SystemClock.sleep(10_000);
    }

    @After
    public void tearDown() {
        mService = null;
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    @Test
    @RequiresDevice
    public void testHotwordDetectionService_hasCaptureAudioOutputPermission() throws Throwable {
        if (!isPstnCallAudioInterceptable()) {
            Log.d(TAG, "Ignore testHotwordDetectionService_hasCaptureAudioOutputPermission");
            return;
        }
        // Create AlwaysOnHotwordDetector and wait the HotwordDetectionService ready
        mService.createAlwaysOnHotwordDetector();

        mService.waitSandboxedDetectionServiceInitializedCalledOrException();

        // verify callback result
        assertThat(mService.getSandboxedDetectionServiceInitializedResult()).isEqualTo(
                HotwordDetectionService.INITIALIZATION_STATUS_SUCCESS);

        AlwaysOnHotwordDetector alwaysOnHotwordDetector = mService.getAlwaysOnHotwordDetector();
        Objects.requireNonNull(alwaysOnHotwordDetector);

        try {
            adoptShellPermissionIdentityForHotword();

            mService.initDetectRejectLatch();
            alwaysOnHotwordDetector.triggerHardwareRecognitionEventForTest(/* status */ 0,
                    /* soundModelHandle */ 100, /* halEventReceivedMillis */ 12345,
                    /* captureAvailable */ true, /* captureSession */ 101,
                    /* captureDelayMs */ 1000, /* capturePreambleMs */ 1001,
                    /* triggerInData */ true, Helper.createFakeAudioFormat(), /* data */ null,
                    Helper.createFakeKeyphraseRecognitionExtraList());
            // wait onDetected() called and verify the result
            mService.waitOnDetectOrRejectCalled();
            AlwaysOnHotwordDetector.EventPayload detectResult =
                    mService.getHotwordServiceOnDetectedResult();

            Helper.verifyDetectedResult(detectResult, Helper.DETECTED_RESULT);
        } finally {
            alwaysOnHotwordDetector.destroy();
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    private void adoptShellPermissionIdentityForHotword() {
        // Drop any identity adopted earlier.
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.dropShellPermissionIdentity();
        // need to retain the identity until the callback is triggered
        uiAutomation.adoptShellPermissionIdentity(RECORD_AUDIO, CAPTURE_AUDIO_HOTWORD);
    }

    private boolean isPstnCallAudioInterceptable() {
        boolean result;
        try {
            result = mAudioManager.isPstnCallAudioInterceptable();
            Log.d(TAG, "isPstnCallAudioInterceptable result = " + result);
        } catch (Exception e) {
            Log.d(TAG, "isPstnCallAudioInterceptable Exception = " + e);
            return false;
        }
        return result;
    }

    public String getTestVoiceInteractionService() {
        Log.d(TAG, "getTestVoiceInteractionService()");
        return CTS_SERVICE_PACKAGE + "/" + SERVICE_COMPONENT;
    }
}
