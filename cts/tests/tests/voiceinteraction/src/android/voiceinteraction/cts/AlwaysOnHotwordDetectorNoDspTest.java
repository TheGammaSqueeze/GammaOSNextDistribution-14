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
import static android.Manifest.permission.MANAGE_SOUND_TRIGGER;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.content.pm.PackageManager.FEATURE_MICROPHONE;
import static android.voiceinteraction.cts.testcore.Helper.CTS_SERVICE_PACKAGE;
import static android.voiceinteraction.cts.testcore.Helper.KEYPHRASE_LOCALE;
import static android.voiceinteraction.cts.testcore.Helper.KEYPHRASE_TEXT;
import static android.voiceinteraction.cts.testcore.Helper.MANAGE_VOICE_KEYPHRASES;
import static android.voiceinteraction.cts.testcore.Helper.createKeyphraseArray;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.compat.testing.PlatformCompatChangeRule;
import android.content.Context;
import android.hardware.soundtrigger.SoundTrigger;
import android.media.soundtrigger.SoundTriggerInstrumentation;
import android.media.soundtrigger.SoundTriggerManager;
import android.platform.test.annotations.AppModeFull;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.SandboxedDetectionInitializer;
import android.os.Build;
import android.util.Log;
import android.voiceinteraction.cts.services.CtsBasicVoiceInteractionService;
import android.voiceinteraction.cts.testcore.AssumptionCheckerRule;
import android.voiceinteraction.cts.testcore.Helper;
import android.voiceinteraction.cts.testcore.VoiceInteractionServiceConnectedClassRule;
import android.voiceinteraction.cts.testcore.VoiceInteractionServiceOverrideEnrollmentRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.RequiredFeatureRule;

import libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges;
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.util.Objects;
import java.util.UUID;

/**
 * Tests for {@link AlwaysOnHotwordDetector} which do not use a fake testing SoundTrigger HAL via
 * {@link SoundTriggerInstrumentation}. Instead, the real HAL is used and expectations about the
 * HAL are assumed at the beginning of each test.
 *
 * <p>Tests in this file assume that the DUT does not have a registered HAL
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "No real use case for instant mode hotword detector")
public class AlwaysOnHotwordDetectorNoDspTest {

    private static final String TAG = AlwaysOnHotwordDetectorNoDspTest.class.getSimpleName();

    /**
     * Gates returning {@code IllegalStateException} in {@link AlwaysOnHotwordDetector} when no DSP
     * module
     * is available. If the change is not enabled, the existing behavior of not throwing an
     * exception and delivering {@link AlwaysOnHotwordDetector#STATE_HARDWARE_UNAVAILABLE} is
     * retained.
     */
    private static final long THROW_ON_INITIALIZE_IF_NO_DSP = 269165460L;

    // The VoiceInteractionService used by this test
    private static final String SERVICE_COMPONENT =
            "android.voiceinteraction.cts.services.CtsBasicVoiceInteractionService";

    // For destroying in teardown
    private AlwaysOnHotwordDetector mAlwaysOnHotwordDetector = null;

    private static final Context sContext = getInstrumentation().getTargetContext();
    private static final SoundTrigger.Keyphrase[] KEYPHRASE_ARRAY = createKeyphraseArray(sContext);

    @Rule(order = 0)
    public RequiredFeatureRule REQUIRES_MIC_RULE = new RequiredFeatureRule(FEATURE_MICROPHONE);

    // Our method of overriding compat changes relies on userdebug
    // TODO(b/284369268)
    @Rule(order = 1)
    public AssumptionCheckerRule checkUserDebugRule = new AssumptionCheckerRule(
            Build::isDebuggable, "Overriding compat change below targetSdk requires userdebug");

    @Rule(order = 2)
    public VoiceInteractionServiceOverrideEnrollmentRule mEnrollOverrideRule =
            new VoiceInteractionServiceOverrideEnrollmentRule(getService());

    @Rule(order = 3)
    public TestRule compatChangeRule = new PlatformCompatChangeRule();


    @ClassRule
    public static final VoiceInteractionServiceConnectedClassRule sServiceRule =
            new VoiceInteractionServiceConnectedClassRule(
                    sContext, getTestVoiceInteractionServiceName());

    private static String getTestVoiceInteractionServiceName() {
        Log.d(TAG, "getTestVoiceInteractionServiceName()");
        return CTS_SERVICE_PACKAGE + "/" + SERVICE_COMPONENT;
    }

    private static CtsBasicVoiceInteractionService getService() {
        return (CtsBasicVoiceInteractionService) sServiceRule.getService();
    }

    private void adoptSoundTriggerPermissions() {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        RECORD_AUDIO, CAPTURE_AUDIO_HOTWORD, MANAGE_HOTWORD_DETECTION,
                        MANAGE_VOICE_KEYPHRASES, MANAGE_SOUND_TRIGGER,
                        "android.permission.LOG_COMPAT_CHANGE",
                        "android.permission.READ_COMPAT_CHANGE_CONFIG");
    }


    @Before
    public void setup() {
        adoptSoundTriggerPermissions();
        mAlwaysOnHotwordDetector = null;
        // Wait onAvailabilityChanged() callback called following AOHD creation.
        getService().initAvailabilityChangeLatch();

        // Load appropriate keyphrase model
        // Required for the model to enter the enrolled state
        mEnrollOverrideRule.getModelManager().updateKeyphraseSoundModel(
                new SoundTrigger.KeyphraseSoundModel(new UUID(5, 7),
                        new UUID(7, 5), /* data= */ null, KEYPHRASE_ARRAY));
    }

    @After
    public void tearDown() {
        // Destroy the framework session
        if (mAlwaysOnHotwordDetector != null) {
            mAlwaysOnHotwordDetector.destroy();
        }

        // Clear the service state
        getService().resetState();

        // Drop any permissions we may still have
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
    }

    @ApiTest(apis = {
            "android.service.voice.VoiceInteractionService#createAlwaysOnHotwordDetector",
    })
    @Test
    @EnableCompatChanges({THROW_ON_INITIALIZE_IF_NO_DSP})
    public void testCreateAlwaysOnHotwordDetector_noSTModule_throwsExceptionWhenChangeIdEnabled() {
        // Test is only valid if there is no SoundTrigger HAL on the device
        assumeTrue(Objects.requireNonNull(sContext.getSystemService(SoundTriggerManager.class))
                .getModuleProperties() == null);

        assertThrows(IllegalStateException.class,
                () -> getService().createAlwaysOnHotwordDetector(KEYPHRASE_TEXT,
                        KEYPHRASE_LOCALE,
                        Helper.createFakePersistableBundleData(),
                        Helper.createFakeSharedMemoryData(),
                        CtsBasicVoiceInteractionService.getDetectorCallbackExecutor(),
                        getService().createAlwaysOnHotwordDetectorCallbackWithListeners()));
    }

    @ApiTest(apis = {
            "android.service.voice.VoiceInteractionService#createAlwaysOnHotwordDetector",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onAvailabilityChanged",
            "android.service.voice.AlwaysOnHotwordDetector"
                    + ".Callback#onHotwordDetectionServiceInitialized",
    })
    @Test
    @DisableCompatChanges({THROW_ON_INITIALIZE_IF_NO_DSP})
    public void testCreateAlwaysOnHotwordDetector_noSTModule_stateUnavailableWhenChangeIdDisabled()
            throws Exception {
        // Test is only valid if there is no SoundTrigger HAL on the device
        assumeTrue(Objects.requireNonNull(sContext.getSystemService(SoundTriggerManager.class))
                .getModuleProperties() == null);

        // We must init this latch ourselves because we are not using a helper API to create the
        // detector. In other cases the service will init this latch for us when a helper create
        // detector API is used.
        getService().initDetectorInitializedLatch();
        mAlwaysOnHotwordDetector = getService().createAlwaysOnHotwordDetector(KEYPHRASE_TEXT,
                KEYPHRASE_LOCALE,
                Helper.createFakePersistableBundleData(),
                Helper.createFakeSharedMemoryData(),
                CtsBasicVoiceInteractionService.getDetectorCallbackExecutor(),
                getService().createAlwaysOnHotwordDetectorCallbackWithListeners());
        assertThat(mAlwaysOnHotwordDetector).isNotNull();

        getService().waitSandboxedDetectionServiceInitializedCalledOrException();

        // verify callback result
        assertThat(getService().getSandboxedDetectionServiceInitializedResult())
                .isEqualTo(SandboxedDetectionInitializer.INITIALIZATION_STATUS_SUCCESS);

        // verify we have entered the ENROLLED state
        getService().waitAvailabilityChangedCalled();
        assertThat(getService().getHotwordDetectionServiceAvailabilityResult())
                .isEqualTo(AlwaysOnHotwordDetector.STATE_HARDWARE_UNAVAILABLE);
    }
}
