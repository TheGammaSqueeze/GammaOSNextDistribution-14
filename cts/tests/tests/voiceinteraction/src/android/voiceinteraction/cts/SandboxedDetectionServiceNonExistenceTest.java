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

package android.voiceinteraction.cts;

import static android.voiceinteraction.cts.testcore.Helper.CTS_SERVICE_PACKAGE;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.AppModeFull;
import android.util.Log;
import android.voiceinteraction.cts.services.BaseVoiceInteractionService;
import android.voiceinteraction.cts.services.CtsMainVoiceInteractionService;
import android.voiceinteraction.cts.testcore.VoiceInteractionServiceConnectedRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Objects;

/**
 * Tests for using the VoiceInteractionService without a basic HotwordDetectionService
 * or VisualQueryDetectionService.
 */
@AppModeFull(reason = "No real ue case for instant mode hotword detection service")
public final class SandboxedDetectionServiceNonExistenceTest extends AbstractHdsTestCase {
    private static final String TAG = "SandboxedDetectionServiceNonExistenceTest";
    private static final String SERVICE_COMPONENT =
            "android.voiceinteraction.cts.services.CtsMainVoiceInteractionService";

    @Rule
    public VoiceInteractionServiceConnectedRule mConnectedRule =
            new VoiceInteractionServiceConnectedRule(getInstrumentation().getTargetContext(),
                    getTestVoiceInteractionService());

    private CtsMainVoiceInteractionService mService;

    @Before
    public void setup() {
        // VoiceInteractionServiceConnectedRule handles the service connected, we should be
        // able to get service
        mService = (CtsMainVoiceInteractionService) BaseVoiceInteractionService.getService();

        // Check we can get the service, we need service object to call the service provided method
        Objects.requireNonNull(mService);

        // Set whether voice activation permission enabled.
        mService.setVoiceActivationPermissionEnabled(mVoiceActivationPermissionEnabled);
    }

    public String getTestVoiceInteractionService() {
        Log.d(TAG, "getTestVoiceInteractionService()");
        return CTS_SERVICE_PACKAGE + "/" + SERVICE_COMPONENT;
    }

    @Test
    public void testHotwordDetectionService_noHotwordDetectionComponentName_triggerFailure()
            throws Throwable {
        // VoiceInteractionServiceConnectedRule handles the service connected, we should be able
        // to get service.
        CtsMainVoiceInteractionService service =
                (CtsMainVoiceInteractionService) BaseVoiceInteractionService.getService();
        // Check we can get the service, we need service object to call the service provided method
        Objects.requireNonNull(service);

        // Create alwaysOnHotwordDetector
        service.createAlwaysOnHotwordDetector();

        // Wait the result and verify expected result
        service.waitSandboxedDetectionServiceInitializedCalledOrException();
        // Verify IllegalStateException throws
        assertThat(service.isCreateDetectorIllegalStateExceptionThrow()).isTrue();
    }

    @Test
    public void testVisualQueryDetectionService_noVisualQueryDetectionComponentName_triggerFailure()
            throws Throwable {
        // VoiceInteractionServiceConnectedRule handles the service connected, we should be able
        // to get service.
        CtsMainVoiceInteractionService service =
                (CtsMainVoiceInteractionService) CtsMainVoiceInteractionService.getService();
        // Check we can get the service, we need service object to call the service provided method
        Objects.requireNonNull(service);

        // Create VisualQueryDetector
        service.createVisualQueryDetector();

        // Wait the result and verify expected result
        service.waitSandboxedDetectionServiceInitializedCalledOrException();
        // Verify IllegalStateException throws
        assertThat(service.isCreateDetectorIllegalStateExceptionThrow()).isTrue();
    }
}
