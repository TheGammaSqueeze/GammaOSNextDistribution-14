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

import static android.voiceinteraction.cts.testcore.Helper.CTS_SERVICE_PACKAGE;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.AppModeFull;
import android.service.voice.VisualQueryDetectionService;
import android.util.Log;
import android.voiceinteraction.common.Utils;
import android.voiceinteraction.cts.services.BaseVoiceInteractionService;
import android.voiceinteraction.cts.services.NonIsolatedHotwordDetectionVoiceInteractionService;
import android.voiceinteraction.cts.testcore.AssumptionCheckerRule;
import android.voiceinteraction.cts.testcore.VoiceInteractionServiceConnectedRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;

/**
 * Tests for {@link VisualQueryDetectionService} without isolated tags defined.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "No real use case for instant mode visual query detection service")
public class VisualQueryDetectionServiceNonIsolatedTest {

    private static final String TAG = "VisualQueryDetectionServiceNonIsolatedTest";

    private static final String SERVICE_COMPONENT =
            "android.voiceinteraction.cts.services"
                    + ".NonIsolatedHotwordDetectionVoiceInteractionService";

    @Rule
    public AssumptionCheckerRule checkVisualQueryDetectionServiceEnabledRule =
            new AssumptionCheckerRule(() -> Utils.SYSPROP_VISUAL_QUERY_SERVICE_ENABLED,
            "Testing VisualQueryDetectionService requires enabling the feature");

    @Rule
    public VoiceInteractionServiceConnectedRule mConnectedRule =
            new VoiceInteractionServiceConnectedRule(
                    getInstrumentation().getTargetContext(), getTestVoiceInteractionService());

    public String getTestVoiceInteractionService() {
        Log.d(TAG, "getTestVoiceInteractionService()");
        return CTS_SERVICE_PACKAGE + "/" + SERVICE_COMPONENT;
    }

    @Test
    public void testVisualQueryDetectionService_noIsolatedTags_triggerFailure()
            throws Throwable {
        // VoiceInteractionServiceConnectedRule handles the service connected, we should be able
        // to get service.
        NonIsolatedHotwordDetectionVoiceInteractionService service =
                (NonIsolatedHotwordDetectionVoiceInteractionService)
                        BaseVoiceInteractionService.getService();

        // Check we can get the service, we need service object to call the service provided method
        Objects.requireNonNull(service);

        // Create alwaysOnHotwordDetector
        service.createVisualQueryDetector();

        // Wait the result and verify expected result
        service.waitSandboxedDetectionServiceInitializedCalledOrException();
        // Verify IllegalStateException throws
        assertThat(service.isCreateDetectorIllegalStateExceptionThrow()).isTrue();
    }
}
