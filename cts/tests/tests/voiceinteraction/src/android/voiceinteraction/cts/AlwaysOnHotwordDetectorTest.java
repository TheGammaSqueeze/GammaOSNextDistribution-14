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
import static android.Manifest.permission.DEVICE_POWER;
import static android.Manifest.permission.MANAGE_HOTWORD_DETECTION;
import static android.Manifest.permission.MANAGE_SENSOR_PRIVACY;
import static android.Manifest.permission.OBSERVE_SENSOR_PRIVACY;
import static android.Manifest.permission.POWER_SAVER;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.SOUND_TRIGGER_RUN_IN_BATTERY_SAVER;
import static android.content.pm.PackageManager.FEATURE_MICROPHONE;
import static android.service.voice.SoundTriggerFailure.ERROR_CODE_MODULE_DIED;
import static android.service.voice.SoundTriggerFailure.ERROR_CODE_RECOGNITION_RESUME_FAILED;
import static android.service.voice.SoundTriggerFailure.ERROR_CODE_UNKNOWN;
import static android.voiceinteraction.cts.testcore.Helper.CTS_SERVICE_PACKAGE;
import static android.voiceinteraction.cts.testcore.Helper.MANAGE_VOICE_KEYPHRASES;
import static android.voiceinteraction.cts.testcore.Helper.createKeyphraseArray;
import static android.voiceinteraction.cts.testcore.Helper.createKeyphraseRecognitionExtraList;
import static android.voiceinteraction.cts.testcore.Helper.waitForFutureDoneAndAssertSuccessful;
import static android.voiceinteraction.cts.testcore.Helper.waitForVoidFutureAndAssertSuccessful;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.app.AppOpsManager;
import android.content.Context;
import android.hardware.SensorPrivacyManager;
import android.hardware.soundtrigger.SoundTrigger;
import android.media.soundtrigger.SoundTriggerInstrumentation;
import android.media.soundtrigger.SoundTriggerInstrumentation.RecognitionSession;
import android.os.BatterySaverPolicyConfig;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.FailureSuggestedAction;
import android.service.voice.HotwordRejectedResult;
import android.service.voice.SandboxedDetectionInitializer;
import android.service.voice.SoundTriggerFailure;
import android.soundtrigger.cts.instrumentation.SoundTriggerInstrumentationObserver;
import android.soundtrigger.cts.instrumentation.SoundTriggerInstrumentationObserver.ModelSessionObserver;
import android.util.Log;
import android.voiceinteraction.common.Utils;
import android.voiceinteraction.cts.services.CtsBasicVoiceInteractionService;
import android.voiceinteraction.cts.testcore.Helper;
import android.voiceinteraction.cts.testcore.VoiceInteractionServiceConnectedClassRule;
import android.voiceinteraction.cts.testcore.VoiceInteractionServiceOverrideEnrollmentRule;

import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.BatteryUtils;
import com.android.compatibility.common.util.RequiredFeatureRule;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Tests for {@link AlwaysOnHotwordDetector} APIs. */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "No real use case for instant mode hotword detector")
public class AlwaysOnHotwordDetectorTest extends AbstractHdsTestCase {

    private static final String TAG = "AlwaysOnHotwordDetectorTest";
    // The VoiceInteractionService used by this test
    private static final String SERVICE_COMPONENT =
            "android.voiceinteraction.cts.services.CtsBasicVoiceInteractionService";

    private static final int WAIT_EXPECTED_NO_CALL_TIMEOUT_IN_MS = 750;

    private static final Context sContext = getInstrumentation().getTargetContext();
    private static final SoundTrigger.Keyphrase[] KEYPHRASE_ARRAY = createKeyphraseArray(sContext);

    private final SoundTriggerInstrumentationObserver mInstrumentationObserver =
            new SoundTriggerInstrumentationObserver();

    // For destroying in teardown
    private AlwaysOnHotwordDetector mAlwaysOnHotwordDetector = null;

    @Rule
    public RequiredFeatureRule REQUIRES_MIC_RULE = new RequiredFeatureRule(FEATURE_MICROPHONE);

    @Rule
    public VoiceInteractionServiceOverrideEnrollmentRule mEnrollOverrideRule =
            new VoiceInteractionServiceOverrideEnrollmentRule(getService());

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
                        SOUND_TRIGGER_RUN_IN_BATTERY_SAVER, DEVICE_POWER, POWER_SAVER,
                        MANAGE_SENSOR_PRIVACY, OBSERVE_SENSOR_PRIVACY, MANAGE_VOICE_KEYPHRASES);
    }

    private void createAndEnrollAlwaysOnHotwordDetector() throws InterruptedException {
        createAndEnrollAlwaysOnHotwordDetector(null);
    }

    private void createAndEnrollAlwaysOnHotwordDetector(@Nullable PersistableBundle options)
            throws InterruptedException {
        mAlwaysOnHotwordDetector = null;
        // Wait onAvailabilityChanged() callback called following AOHD creation.
        getService().initAvailabilityChangeLatch();

        // Load appropriate keyphrase model
        // Required for the model to enter the enrolled state
        runWithShellPermissionIdentity(
                () -> mEnrollOverrideRule.getModelManager().updateKeyphraseSoundModel(
                        new SoundTrigger.KeyphraseSoundModel(new UUID(5, 7),
                                new UUID(7, 5), /* data= */ null, KEYPHRASE_ARRAY)),
                MANAGE_VOICE_KEYPHRASES);

        // Create alwaysOnHotwordDetector
        getService().createAlwaysOnHotwordDetectorWithOnFailureCallback(
                /* useExecutor= */ true, /* mainThread= */ true, options);
        try {
            // Wait onHotwordDetectionServiceInitialized() callback
            getService().waitSandboxedDetectionServiceInitializedCalledOrException();
        } finally {
            // Get the AlwaysOnHotwordDetector instance even if there is an error happened to avoid
            // that we don't destroy the detector in tearDown method. It may be null here. We will
            // check the status below.
            mAlwaysOnHotwordDetector = getService().getAlwaysOnHotwordDetector();
        }
        // Verify that detector creation didn't throw
        assertThat(getService().isCreateDetectorIllegalStateExceptionThrow()).isFalse();
        assertThat(getService().isCreateDetectorSecurityExceptionThrow()).isFalse();

        // verify callback result
        assertThat(getService().getSandboxedDetectionServiceInitializedResult())
                .isEqualTo(SandboxedDetectionInitializer.INITIALIZATION_STATUS_SUCCESS);

        assertThat(mAlwaysOnHotwordDetector).isNotNull();

        // verify we have entered the ENROLLED state
        getService().waitAvailabilityChangedCalled();
        assertThat(getService().getHotwordDetectionServiceAvailabilityResult())
                .isEqualTo(AlwaysOnHotwordDetector.STATE_KEYPHRASE_ENROLLED);

    }

    @BeforeClass
    public static void setupClass() {
        // TODO(b/276393203) delete this
        SystemClock.sleep(8_000);
    }

    @Before
    public void setup() {
        // Hook up SoundTriggerInstrumentation to inject/observe STHAL operations.
        // Requires MANAGE_SOUND_TRIGGER
        runWithShellPermissionIdentity(mInstrumentationObserver::attachInstrumentation);

        // Set whether voice activation permission check is enabled.
        getService().setVoiceActivationPermissionEnabled(mVoiceActivationPermissionEnabled);
    }

    @After
    public void tearDown() {
        // Destroy the framework session
        if (mAlwaysOnHotwordDetector != null) {
            mAlwaysOnHotwordDetector.destroy();
        }

        // Clean up any unexpected HAL state
        try {
            mInstrumentationObserver.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Clear the service state
        getService().resetState();
        // Drop any permissions we may still have
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
    }

    @ApiTest(apis = {
            "android.media.voice.KeyphraseModelManager#updateKeyphraseSoundModel",
            "android.service.voice.VoiceInteractionService#createAlwaysOnHotwordDetector",
            "android.service.voice.AlwaysOnHotwordDetector#startRecognition",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onAvailabilityChanged",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onDetected",
            "android.service.voice.AlwaysOnHotwordDetector"
                    + ".Callback#onHotwordDetectionServiceInitialized",
    })
    @Test
    public void testStartRecognition_success() throws Exception {
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();
        // Start recognition
        mAlwaysOnHotwordDetector.startRecognition(0, new byte[]{1, 2, 3, 4, 5});
        RecognitionSession recognitionSession = waitForFutureDoneAndAssertSuccessful(
                mInstrumentationObserver.getOnRecognitionStartedFuture());
        assertThat(recognitionSession).isNotNull();

        getService().initDetectRejectLatch();
        recognitionSession.triggerRecognitionEvent(new byte[]{0x11, 0x22},
                createKeyphraseRecognitionExtraList());
        getService().waitOnDetectOrRejectCalled();
        AlwaysOnHotwordDetector.EventPayload detectResult =
                getService().getHotwordServiceOnDetectedResult();

        Helper.verifyDetectedResult(detectResult, Helper.DETECTED_RESULT);
    }

    @ApiTest(apis = {
            "android.media.voice.KeyphraseModelManager#updateKeyphraseSoundModel",
            "android.service.voice.VoiceInteractionService#createAlwaysOnHotwordDetector",
            "android.service.voice.AlwaysOnHotwordDetector#startRecognition",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onAvailabilityChanged",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onDetected",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onError",
            "android.service.voice.AlwaysOnHotwordDetector"
                    + ".Callback#onHotwordDetectionServiceInitialized",
    })
    @Test
    public void testHalIsDead_onFailureReceived() throws Exception {
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();

        // We don't get callbacks if we don't have a recognition started
        mAlwaysOnHotwordDetector.startRecognition(0, new byte[]{1, 2, 3, 4, 5});
        assertThat(waitForFutureDoneAndAssertSuccessful(
                mInstrumentationObserver.getOnRecognitionStartedFuture())).isNotNull();

        // Cause a restart
        getService().initOnFailureLatch();
        mInstrumentationObserver.getGlobalCallbackObserver().getInstrumentation().triggerRestart();
        getService().waitOnFailureCalled();
        var failure = getService().getSoundTriggerFailure();
        assertThat(failure.getErrorCode()).isEqualTo(ERROR_CODE_MODULE_DIED);
    }

    @ApiTest(apis = {
            "android.media.voice.KeyphraseModelManager#updateKeyphraseSoundModel",
            "android.service.voice.VoiceInteractionService#createAlwaysOnHotwordDetector",
            "android.service.voice.AlwaysOnHotwordDetector#startRecognition",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onAvailabilityChanged",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onDetected",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onError",
            "android.service.voice.AlwaysOnHotwordDetector"
                    + ".Callback#onHotwordDetectionServiceInitialized",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onRecognitionPaused",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onRecognitionResumed",
    })
    @Test
    public void testRecognitionResumedFailed_onFailureReceived() throws Exception {
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();

        SoundTriggerInstrumentation instrumentation =
                mInstrumentationObserver.getGlobalCallbackObserver().getInstrumentation();
        mAlwaysOnHotwordDetector.startRecognition(0, new byte[]{1, 2, 3, 4, 5});
        RecognitionSession recognitionSession = waitForFutureDoneAndAssertSuccessful(
                mInstrumentationObserver.getOnRecognitionStartedFuture());
        assertThat(recognitionSession).isNotNull();

        instrumentation.setResourceContention(true);

        getService().initOnRecognitionPausedLatch();
        // Induce a recognition pause
        recognitionSession.triggerAbortRecognition();
        getService().waitOnRecognitionPausedCalled();

        getService().initOnFailureLatch();
        // Framework will attempt to resume recognition, but will fail due to set contention
        instrumentation.triggerOnResourcesAvailable();

        getService().waitOnFailureCalled();
        var failure = getService().getSoundTriggerFailure();
        assertThat(failure.getErrorCode()).isEqualTo(ERROR_CODE_RECOGNITION_RESUME_FAILED);
        instrumentation.setResourceContention(false);
    }

    @ApiTest(apis = {
            "android.media.voice.KeyphraseModelManager#updateKeyphraseSoundModel",
            "android.service.voice.AlwaysOnHotwordDetector#startRecognition",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onAvailabilityChanged",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onDetected",
            "android.service.voice.AlwaysOnHotwordDetector"
                    + ".Callback#onHotwordDetectionServiceInitialized",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onRecognitionPaused",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onRecognitionResumed",
            "android.service.voice.VoiceInteractionService#createAlwaysOnHotwordDetector",
    })
    @Test
    public void testAbortRecognitionAndOnResourceAvailable_recognitionPausedAndResumed()
            throws Exception {
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();

        SoundTriggerInstrumentation instrumentation =
                mInstrumentationObserver.getGlobalCallbackObserver().getInstrumentation();
        mAlwaysOnHotwordDetector.startRecognition(0, new byte[]{1, 2, 3, 4, 5});
        RecognitionSession recognitionSession = waitForFutureDoneAndAssertSuccessful(
                mInstrumentationObserver.getOnRecognitionStartedFuture());
        assertThat(recognitionSession).isNotNull();

        ModelSessionObserver modelSession = mInstrumentationObserver
                .getGlobalCallbackObserver().getOnModelLoadedFuture().get();

        modelSession.resetOnRecognitionStartedFuture();
        getService().initOnRecognitionPausedLatch();
        // Induce a recognition pause
        recognitionSession.triggerAbortRecognition();
        getService().waitOnRecognitionPausedCalled();
        // Check that STService didn't attempt to start immediately on receiving abort
        assertThat(modelSession.getOnRecognitionStartedFuture().isDone()).isFalse();

        getService().initOnRecognitionResumedLatch();
        instrumentation.triggerOnResourcesAvailable();
        getService().waitOnRecognitionResumedCalled();
        recognitionSession = waitForFutureDoneAndAssertSuccessful(
                modelSession.getOnRecognitionStartedFuture());

        // Same flow, but ensure we don't get an onError by setting contention
        getService().initOnRecognitionPausedLatch();
        instrumentation.setResourceContention(true);
        // Induce a recognition pause
        recognitionSession.triggerAbortRecognition();
        getService().waitOnRecognitionPausedCalled();
        modelSession.resetOnRecognitionStartedFuture();
        getService().initOnRecognitionResumedLatch();
        // This will trigger resources available
        instrumentation.setResourceContention(false);
        getService().waitOnRecognitionResumedCalled();
        assertThat(getService().getSoundTriggerFailure()).isNull();
        recognitionSession = waitForFutureDoneAndAssertSuccessful(
                modelSession.getOnRecognitionStartedFuture());
    }

    @ApiTest(apis = {
            "android.media.voice.KeyphraseModelManager#updateKeyphraseSoundModel",
            "android.service.voice.AlwaysOnHotwordDetector#startRecognition",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onAvailabilityChanged",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onDetected",
            "android.service.voice.AlwaysOnHotwordDetector"
                    + ".Callback#onHotwordDetectionServiceInitialized",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onRecognitionPaused",
            "android.service.voice.VoiceInteractionService#createAlwaysOnHotwordDetector",
    })
    @Test
    public void testStartRecognitionNoFlagBatterySaverAllEnabled_noRecognitionPaused()
            throws Exception {
        BatteryUtils.assumeBatterySaverFeature();

        final PowerManager powerManager = sContext.getSystemService(PowerManager.class);
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();

        try {
            mAlwaysOnHotwordDetector.startRecognition(0, new byte[]{1, 2, 3, 4, 5});
            assertThat(
                    waitForFutureDoneAndAssertSuccessful(
                            mInstrumentationObserver.getOnRecognitionStartedFuture())).isNotNull();

            BatteryUtils.runDumpsysBatteryUnplug();

            // enable battery saver with SOUND_TRIGGER_MODE_ALL_ENABLED, no onRecognitionPaused
            // called
            getService().initOnRecognitionPausedLatch();
            setSoundTriggerPowerSaveMode(powerManager, PowerManager.SOUND_TRIGGER_MODE_ALL_ENABLED);
            BatteryUtils.enableBatterySaver(/* isEnabled= */ true);
            assertThat(getService().waitNoOnRecognitionPausedCalled()).isTrue();
        } finally {
            BatteryUtils.runDumpsysBatteryReset();
            BatteryUtils.resetBatterySaver();
        }
    }

    @ApiTest(apis = {
            "android.media.voice.KeyphraseModelManager#updateKeyphraseSoundModel",
            "android.service.voice.AlwaysOnHotwordDetector#startRecognition",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onAvailabilityChanged",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onDetected",
            "android.service.voice.AlwaysOnHotwordDetector"
                    + ".Callback#onHotwordDetectionServiceInitialized",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onRecognitionPaused",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onRecognitionResumed",
            "android.service.voice.VoiceInteractionService#createAlwaysOnHotwordDetector",
    })
    @Test
    public void testStartRecognitionNoFlagBatterySaverCriticalOnly_recognitionPaused()
            throws Exception {
        BatteryUtils.assumeBatterySaverFeature();

        final PowerManager powerManager = sContext.getSystemService(PowerManager.class);
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();

        try {
            mAlwaysOnHotwordDetector.startRecognition(0, new byte[]{1, 2, 3, 4, 5});
            assertThat(
                    waitForFutureDoneAndAssertSuccessful(
                            mInstrumentationObserver.getOnRecognitionStartedFuture())).isNotNull();

            BatteryUtils.runDumpsysBatteryUnplug();

            // enable battery saver with SOUND_TRIGGER_MODE_CRITICAL_ONLY, onRecognitionPaused
            // called
            getService().initOnRecognitionPausedLatch();
            setSoundTriggerPowerSaveMode(powerManager,
                    PowerManager.SOUND_TRIGGER_MODE_CRITICAL_ONLY);
            BatteryUtils.enableBatterySaver(/* isEnabled= */ true);
            getService().waitOnRecognitionPausedCalled();

            // disable battery saver, onRecognitionResumed called
            getService().initOnRecognitionResumedLatch();
            BatteryUtils.enableBatterySaver(/* isEnabled= */ false);
            getService().waitOnRecognitionResumedCalled();
        } finally {
            BatteryUtils.runDumpsysBatteryReset();
            BatteryUtils.resetBatterySaver();
        }
    }

    @ApiTest(apis = {
            "android.media.voice.KeyphraseModelManager#updateKeyphraseSoundModel",
            "android.service.voice.AlwaysOnHotwordDetector#startRecognition",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onAvailabilityChanged",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onDetected",
            "android.service.voice.AlwaysOnHotwordDetector"
                    + ".Callback#onHotwordDetectionServiceInitialized",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onRecognitionPaused",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onRecognitionResumed",
            "android.service.voice.VoiceInteractionService#createAlwaysOnHotwordDetector",
    })
    @Test
    public void testStartRecognitionNoFlagBatterySaverAllDisabled_recognitionPaused()
            throws Exception {
        BatteryUtils.assumeBatterySaverFeature();

        final PowerManager powerManager = sContext.getSystemService(PowerManager.class);
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();

        try {
            mAlwaysOnHotwordDetector.startRecognition(0, new byte[]{1, 2, 3, 4, 5});
            assertThat(
                    waitForFutureDoneAndAssertSuccessful(
                            mInstrumentationObserver.getOnRecognitionStartedFuture())).isNotNull();

            BatteryUtils.runDumpsysBatteryUnplug();

            // enable battery saver with SOUND_TRIGGER_MODE_ALL_DISABLED, onRecognitionPaused
            // called
            getService().initOnRecognitionPausedLatch();
            setSoundTriggerPowerSaveMode(powerManager,
                    PowerManager.SOUND_TRIGGER_MODE_ALL_DISABLED);
            BatteryUtils.enableBatterySaver(/* isEnabled= */ true);
            getService().waitOnRecognitionPausedCalled();

            // disable battery saver, onRecognitionResumed called
            getService().initOnRecognitionResumedLatch();
            BatteryUtils.enableBatterySaver(/* isEnabled= */ false);
            getService().waitOnRecognitionResumedCalled();
        } finally {
            BatteryUtils.runDumpsysBatteryReset();
            BatteryUtils.resetBatterySaver();
        }
    }

    @ApiTest(apis = {
            "android.media.voice.KeyphraseModelManager#updateKeyphraseSoundModel",
            "android.service.voice.AlwaysOnHotwordDetector#startRecognition",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onAvailabilityChanged",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onDetected",
            "android.service.voice.AlwaysOnHotwordDetector"
                    + ".Callback#onHotwordDetectionServiceInitialized",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onRecognitionPaused",
            "android.service.voice.VoiceInteractionService#createAlwaysOnHotwordDetector",
    })
    @Test
    public void testStartRecognitionWithFlagBatterySaverAllEnabled_noRecognitionPaused()
            throws Exception {
        BatteryUtils.assumeBatterySaverFeature();

        final PowerManager powerManager = sContext.getSystemService(PowerManager.class);
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();
        try {
            mAlwaysOnHotwordDetector.startRecognition(
                    AlwaysOnHotwordDetector.RECOGNITION_FLAG_RUN_IN_BATTERY_SAVER,
                    new byte[]{1, 2, 3, 4, 5});
            assertThat(
                    waitForFutureDoneAndAssertSuccessful(
                            mInstrumentationObserver.getOnRecognitionStartedFuture())).isNotNull();

            BatteryUtils.runDumpsysBatteryUnplug();

            // enable battery saver with SOUND_TRIGGER_MODE_ALL_ENABLED, no onRecognitionPaused
            // called
            getService().initOnRecognitionPausedLatch();
            setSoundTriggerPowerSaveMode(powerManager, PowerManager.SOUND_TRIGGER_MODE_ALL_ENABLED);
            BatteryUtils.enableBatterySaver(/* isEnabled= */ true);
            assertThat(getService().waitNoOnRecognitionPausedCalled()).isTrue();
        } finally {
            BatteryUtils.runDumpsysBatteryReset();
            BatteryUtils.resetBatterySaver();
        }
    }

    @ApiTest(apis = {
            "android.media.voice.KeyphraseModelManager#updateKeyphraseSoundModel",
            "android.service.voice.AlwaysOnHotwordDetector#startRecognition",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onAvailabilityChanged",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onDetected",
            "android.service.voice.AlwaysOnHotwordDetector"
                    + ".Callback#onHotwordDetectionServiceInitialized",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onRecognitionPaused",
            "android.service.voice.VoiceInteractionService#createAlwaysOnHotwordDetector",
    })
    @Test
    public void testStartRecognitionWithFlagBatterySaverCriticalOnly_noRecognitionPaused()
            throws Exception {
        BatteryUtils.assumeBatterySaverFeature();

        final PowerManager powerManager = sContext.getSystemService(PowerManager.class);
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();
        try {
            mAlwaysOnHotwordDetector.startRecognition(
                    AlwaysOnHotwordDetector.RECOGNITION_FLAG_RUN_IN_BATTERY_SAVER,
                    new byte[]{1, 2, 3, 4, 5});
            assertThat(
                    waitForFutureDoneAndAssertSuccessful(
                            mInstrumentationObserver.getOnRecognitionStartedFuture())).isNotNull();

            BatteryUtils.runDumpsysBatteryUnplug();

            // enable battery saver with SOUND_TRIGGER_MODE_CRITICAL_ONLY, no onRecognitionPaused
            // called
            getService().initOnRecognitionPausedLatch();
            setSoundTriggerPowerSaveMode(powerManager,
                    PowerManager.SOUND_TRIGGER_MODE_CRITICAL_ONLY);
            BatteryUtils.enableBatterySaver(/* isEnabled= */ true);
            assertThat(getService().waitNoOnRecognitionPausedCalled()).isTrue();
        } finally {
            BatteryUtils.runDumpsysBatteryReset();
            BatteryUtils.resetBatterySaver();
        }
    }

    @ApiTest(apis = {
            "android.media.voice.KeyphraseModelManager#updateKeyphraseSoundModel",
            "android.service.voice.AlwaysOnHotwordDetector#startRecognition",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onAvailabilityChanged",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onDetected",
            "android.service.voice.AlwaysOnHotwordDetector"
                    + ".Callback#onHotwordDetectionServiceInitialized",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onRecognitionPaused",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onRecognitionResumed",
            "android.service.voice.VoiceInteractionService#createAlwaysOnHotwordDetector",
    })
    @Test
    public void testStartRecognitionWithFlagBatterySaverAllDisabled_recognitionPaused()
            throws Exception {
        BatteryUtils.assumeBatterySaverFeature();

        final PowerManager powerManager = sContext.getSystemService(PowerManager.class);
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();

        try {
            mAlwaysOnHotwordDetector.startRecognition(
                    AlwaysOnHotwordDetector.RECOGNITION_FLAG_RUN_IN_BATTERY_SAVER,
                    new byte[]{1, 2, 3, 4, 5});
            assertThat(
                    waitForFutureDoneAndAssertSuccessful(
                            mInstrumentationObserver.getOnRecognitionStartedFuture())).isNotNull();

            BatteryUtils.runDumpsysBatteryUnplug();

            // enable battery saver with SOUND_TRIGGER_MODE_ALL_DISABLED, onRecognitionPaused
            // called
            getService().initOnRecognitionPausedLatch();
            setSoundTriggerPowerSaveMode(powerManager,
                    PowerManager.SOUND_TRIGGER_MODE_ALL_DISABLED);
            BatteryUtils.enableBatterySaver(/* isEnabled= */ true);
            getService().waitOnRecognitionPausedCalled();

            // disable battery saver, onRecognitionResumed called
            getService().initOnRecognitionResumedLatch();
            BatteryUtils.enableBatterySaver(/* isEnabled= */ false);
            getService().waitOnRecognitionResumedCalled();
        } finally {
            BatteryUtils.runDumpsysBatteryReset();
            BatteryUtils.resetBatterySaver();
        }
    }

    @ApiTest(apis = {
            "android.media.voice.KeyphraseModelManager#updateKeyphraseSoundModel",
            "android.service.voice.AlwaysOnHotwordDetector#destroy",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onAvailabilityChanged",
            "android.service.voice.AlwaysOnHotwordDetector"
                    + ".Callback#onHotwordDetectionServiceInitialized",
            "android.service.voice.VoiceInteractionService#createAlwaysOnHotwordDetector",
    })
    @Test
    public void testAfterDestroy_detectorIsInvalid() throws Exception {
        createAndEnrollAlwaysOnHotwordDetector();
        adoptSoundTriggerPermissions();
        mAlwaysOnHotwordDetector.destroy();

        assertThrows(IllegalStateException.class, () ->
                mAlwaysOnHotwordDetector.startRecognition());
        assertThrows(IllegalStateException.class, () ->
                mAlwaysOnHotwordDetector.stopRecognition());
        assertThrows(IllegalStateException.class, () ->
                mAlwaysOnHotwordDetector.getParameter(
                        AlwaysOnHotwordDetector.MODEL_PARAM_THRESHOLD_FACTOR));
        assertThrows(IllegalStateException.class, () ->
                mAlwaysOnHotwordDetector.setParameter(
                        AlwaysOnHotwordDetector.MODEL_PARAM_THRESHOLD_FACTOR, 10));
        assertThrows(IllegalStateException.class, () ->
                mAlwaysOnHotwordDetector.queryParameter(
                        AlwaysOnHotwordDetector.MODEL_PARAM_THRESHOLD_FACTOR));
    }

    @Test
    public void testOnPhoneCall_recognitionPausedAndResumed() throws Exception {
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();

        SoundTriggerInstrumentation instrumentation =
                mInstrumentationObserver.getGlobalCallbackObserver().getInstrumentation();
        final var modelSessionFuture = mInstrumentationObserver.getGlobalCallbackObserver()
                .getOnModelLoadedFuture();
        final var firstRecogSessionFuture = Futures.transformAsync(modelSessionFuture,
                ModelSessionObserver::getOnRecognitionStartedFuture, Runnable::run);

        mAlwaysOnHotwordDetector.startRecognition();
        final ModelSessionObserver modelSession
            = waitForFutureDoneAndAssertSuccessful(modelSessionFuture);
        final RecognitionSession firstRecogSession = waitForFutureDoneAndAssertSuccessful(
                firstRecogSessionFuture);

        assertThat(modelSession).isNotNull();
        assertThat(firstRecogSession).isNotNull();

        getService().initOnRecognitionPausedLatch();
        instrumentation.setInPhoneCallState(true);
        getService().waitOnRecognitionPausedCalled();

        modelSession.resetOnRecognitionStartedFuture();
        final var secondRecogSessionFuture = modelSession.getOnRecognitionStartedFuture();

        getService().initOnRecognitionResumedLatch();
        instrumentation.setInPhoneCallState(false);
        getService().waitOnRecognitionResumedCalled();

        // Check that no failure received. Technically racey.
        assertThat(getService().getHotwordDetectionServiceFailure()).isNull();
        // Assert that recognition is properly restarted
        final RecognitionSession secondRecogSession = waitForFutureDoneAndAssertSuccessful(
                secondRecogSessionFuture);
        assertThat(secondRecogSession).isNotNull();
        assertThat(secondRecogSession).isNotEqualTo(firstRecogSession);

        getService().initDetectRejectLatch();
        secondRecogSession.triggerRecognitionEvent(
                new byte[]{0x11, 0x22},
                createKeyphraseRecognitionExtraList());
        getService().waitOnDetectOrRejectCalled();
        AlwaysOnHotwordDetector.EventPayload detectResult =
                getService().getHotwordServiceOnDetectedResult();
        Helper.verifyDetectedResult(detectResult, Helper.DETECTED_RESULT);
    }

    @Test
    public void testStartRecognitionDuringContention_succeedsPausesThenResumes()
        throws Exception {
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();

        SoundTriggerInstrumentation instrumentation =
                mInstrumentationObserver.getGlobalCallbackObserver().getInstrumentation();
        instrumentation.setResourceContention(true);
        getService().initOnRecognitionPausedLatch();
        assertThat(mAlwaysOnHotwordDetector.startRecognition(0, new byte[]{1, 2, 3, 4, 5}))
                .isTrue();
        final var recogFuture = mInstrumentationObserver.getOnRecognitionStartedFuture();
        assertThrows(TimeoutException.class, () -> recogFuture.get(
                    WAIT_EXPECTED_NO_CALL_TIMEOUT_IN_MS,
                    TimeUnit.MILLISECONDS));

        getService().waitOnRecognitionPausedCalled();

        getService().initOnRecognitionResumedLatch();
        instrumentation.setResourceContention(false);
        getService().waitOnRecognitionResumedCalled();
        // Verify that recognition is really resumed
        RecognitionSession recognitionSession = waitForFutureDoneAndAssertSuccessful(
                recogFuture);
        assertThat(recognitionSession).isNotNull();

        getService().initDetectRejectLatch();
        recognitionSession.triggerRecognitionEvent(new byte[]{0x11, 0x22},
                createKeyphraseRecognitionExtraList());
        getService().waitOnDetectOrRejectCalled();
        AlwaysOnHotwordDetector.EventPayload detectResult =
                getService().getHotwordServiceOnDetectedResult();
        Helper.verifyDetectedResult(detectResult, Helper.DETECTED_RESULT);
    }

    @Test
    public void testStartRecognitionDuringBatterySaver_succeedsPausesThenResumes()
        throws Exception {
        BatteryUtils.assumeBatterySaverFeature();

        final PowerManager powerManager = sContext.getSystemService(PowerManager.class);
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();

        try {
            BatteryUtils.runDumpsysBatteryUnplug();
            // enable battery saver with SOUND_TRIGGER_MODE_CRITICAL_ONLY, onRecognitionPaused
            // called
            setSoundTriggerPowerSaveMode(powerManager,
                    PowerManager.SOUND_TRIGGER_MODE_CRITICAL_ONLY);
            BatteryUtils.enableBatterySaver(/* isEnabled= */ true);

            getService().initOnRecognitionPausedLatch();
            assertThat(mAlwaysOnHotwordDetector.startRecognition(0, new byte[]{1, 2, 3, 4, 5}))
                    .isTrue();
            getService().waitOnRecognitionPausedCalled();
            // No recognition session, since device state prohibits it
            final var recogFuture = mInstrumentationObserver.getOnRecognitionStartedFuture();
            assertThrows(TimeoutException.class, () -> recogFuture.get(
                        WAIT_EXPECTED_NO_CALL_TIMEOUT_IN_MS,
                        TimeUnit.MILLISECONDS));

            // disable battery saver, onRecognitionResumed called
            getService().initOnRecognitionResumedLatch();
            BatteryUtils.enableBatterySaver(/* isEnabled= */ false);
            getService().waitOnRecognitionResumedCalled();
            // Verify that recognition is really resumed
            RecognitionSession recognitionSession = waitForFutureDoneAndAssertSuccessful(
                    recogFuture);
            assertThat(recognitionSession).isNotNull();

            getService().initDetectRejectLatch();
            recognitionSession.triggerRecognitionEvent(new byte[]{0x11, 0x22},
                    createKeyphraseRecognitionExtraList());
            getService().waitOnDetectOrRejectCalled();
            AlwaysOnHotwordDetector.EventPayload detectResult =
                    getService().getHotwordServiceOnDetectedResult();
            Helper.verifyDetectedResult(detectResult, Helper.DETECTED_RESULT);

        } finally {
            BatteryUtils.runDumpsysBatteryReset();
            BatteryUtils.resetBatterySaver();
        }
    }

    @Test
    public void testStartRecognitionFail_leavesModelUnrequested()
        throws Exception {
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();

        SoundTriggerInstrumentation instrumentation =
                mInstrumentationObserver.getGlobalCallbackObserver().getInstrumentation();
        instrumentation.setResourceContention(true);
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();

        // Should fail without permissions
        assertThat(mAlwaysOnHotwordDetector.startRecognition(0, new byte[]{1, 2, 3, 4, 5}))
                .isFalse();

        final var recogFuture = mInstrumentationObserver.getOnRecognitionStartedFuture();
        assertThrows(TimeoutException.class, () -> recogFuture.get(
                    WAIT_EXPECTED_NO_CALL_TIMEOUT_IN_MS,
                    TimeUnit.MILLISECONDS));

        instrumentation.setResourceContention(false);

        // Verify that recognition is not resumed
        assertThrows(TimeoutException.class, () -> recogFuture.get(
                    WAIT_EXPECTED_NO_CALL_TIMEOUT_IN_MS,
                    TimeUnit.MILLISECONDS));

        // Attempt to successfully start recognition
        adoptSoundTriggerPermissions();
        assertThat(mAlwaysOnHotwordDetector.startRecognition(0, new byte[]{1, 2, 3, 4, 5}))
                .isTrue();

        RecognitionSession recognitionSession = waitForFutureDoneAndAssertSuccessful(
                recogFuture);
        assertThat(recognitionSession).isNotNull();
        getService().initDetectRejectLatch();
        recognitionSession.triggerRecognitionEvent(new byte[]{0x11, 0x22},
                createKeyphraseRecognitionExtraList());
        getService().waitOnDetectOrRejectCalled();
        AlwaysOnHotwordDetector.EventPayload detectResult =
                getService().getHotwordServiceOnDetectedResult();
        Helper.verifyDetectedResult(detectResult, Helper.DETECTED_RESULT);
    }

    @Test
    public void testOnDetected_appropriateAppOpsNoted() throws Exception {
        // Set up recognition
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();
        // Start recognition
        mAlwaysOnHotwordDetector.startRecognition(0, new byte[]{1, 2, 3, 4, 5});
        RecognitionSession recognitionSession = waitForFutureDoneAndAssertSuccessful(
                mInstrumentationObserver.getOnRecognitionStartedFuture());
        assertThat(recognitionSession).isNotNull();

        // Hook up AppOps Listener
        final SettableFuture<String> appOpFuture = SettableFuture.create();
        AppOpsManager appOpsManager = sContext.getSystemService(AppOpsManager.class);
        final String[] OPS_TO_WATCH =
                new String[] {
                    AppOpsManager.OPSTR_RECEIVE_AMBIENT_TRIGGER_AUDIO,
                    AppOpsManager.OPSTR_RECORD_AUDIO_HOTWORD,
                    AppOpsManager.OPSTR_RECORD_AUDIO,
                    RECEIVE_SANDBOX_TRIGGER_AUDIO_OP_STR,
                };

        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity();

        appOpsManager.startWatchingNoted(
                OPS_TO_WATCH,
                (op, uid, pkgName, attributionTag, flags, result) -> {
                    appOpFuture.set(op);
                });

        // Trigger recognition
        getService().initDetectRejectLatch();
        recognitionSession.triggerRecognitionEvent(new byte[]{0x11, 0x22},
                createKeyphraseRecognitionExtraList());
        getService().waitOnDetectOrRejectCalled();
        AlwaysOnHotwordDetector.EventPayload detectResult =
                getService().getHotwordServiceOnDetectedResult();

        Helper.verifyDetectedResult(detectResult, Helper.DETECTED_RESULT);
        var receivedOp = waitForFutureDoneAndAssertSuccessful(appOpFuture);
        // We have noted one of the record ops
        assertThat(Arrays.asList(OPS_TO_WATCH)).contains(receivedOp);
    }

    @Test
    public void testOnRejected_noAppOpsNoted() throws Exception {
        // Set up recognition
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();
        // Start recognition
        mAlwaysOnHotwordDetector.startRecognition(0, new byte[]{1, 2, 3, 4, 5});
        RecognitionSession recognitionSession = waitForFutureDoneAndAssertSuccessful(
                mInstrumentationObserver.getOnRecognitionStartedFuture());
        assertThat(recognitionSession).isNotNull();

        // Hook up AppOps Listener
        final SettableFuture<String> appOpFuture = SettableFuture.create();

        AppOpsManager appOpsManager = sContext.getSystemService(AppOpsManager.class);
        final String[] OPS_TO_WATCH =
                new String[] {
                    AppOpsManager.OPSTR_RECEIVE_AMBIENT_TRIGGER_AUDIO,
                    AppOpsManager.OPSTR_RECORD_AUDIO_HOTWORD,
                    AppOpsManager.OPSTR_RECORD_AUDIO,
                    RECEIVE_SANDBOX_TRIGGER_AUDIO_OP_STR,
                };

        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity();

        appOpsManager.startWatchingNoted(
                OPS_TO_WATCH,
                (op, uid, pkgName, attributionTag, flags, result) -> {
                    appOpFuture.set(op);
                });

        // Trigger recognition which will be rejected (empty data)
        getService().initDetectRejectLatch();
        recognitionSession.triggerRecognitionEvent(new byte[0],
                createKeyphraseRecognitionExtraList());
        getService().waitOnDetectOrRejectCalled();
        HotwordRejectedResult rejectResult = getService().getHotwordServiceOnRejectedResult();
        assertThat(rejectResult).isEqualTo(Helper.REJECTED_RESULT);
        // Verify that no ops were noted
        assertThat(appOpFuture.isDone()).isFalse();
    }

    @Test
    public void testAppOpsLostReacquired_recognitionPausedResumed() throws Exception {
        // We use the privacy sensor toggle to revoke appops, and not all devices support it
        // Changing runtime permissions using the shell doesn't fire callbacks (b/280692605)
        assumeTrue(sContext.getSystemService(SensorPrivacyManager.class).supportsSensorToggle(
                    SensorPrivacyManager.Sensors.MICROPHONE));
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();
        // Wire futures
        SoundTriggerInstrumentation instrumentation =
                mInstrumentationObserver.getGlobalCallbackObserver().getInstrumentation();
        final var modelSessionFuture = mInstrumentationObserver.getGlobalCallbackObserver()
                .getOnModelLoadedFuture();

        final var firstRecogSessionFuture = Futures.transformAsync(modelSessionFuture,
                ModelSessionObserver::getOnRecognitionStartedFuture, Runnable::run);

        // Start recognition
        mAlwaysOnHotwordDetector.startRecognition(0, new byte[]{1, 2, 3, 4, 5});
        final ModelSessionObserver modelSession
            = waitForFutureDoneAndAssertSuccessful(modelSessionFuture);
        assertThat(waitForFutureDoneAndAssertSuccessful(firstRecogSessionFuture)).isNotNull();
        assertThat(modelSession).isNotNull();

        getService().initOnRecognitionPausedLatch();

        // Wire futures for resumed recognition
        getService().initOnRecognitionResumedLatch();
        modelSession.resetOnRecognitionStartedFuture();
        final var secondRecogSessionFuture = modelSession.getOnRecognitionStartedFuture();

        // Toggle the privacy sensor, which will cause us to lose appops
        sContext.getSystemService(SensorPrivacyManager.class).setSensorPrivacy(
                SensorPrivacyManager.Sensors.MICROPHONE, true);
        try {
            getService().waitOnRecognitionPausedCalled();
        } finally {
            // Toggle the privacy sensor off, which will cause us regain appops
            sContext.getSystemService(SensorPrivacyManager.class).setSensorPrivacy(
                    SensorPrivacyManager.Sensors.MICROPHONE, false);
        }

        getService().waitOnRecognitionResumedCalled();

        // Verify recognition is properly restarted by triggering an event
        final RecognitionSession secondRecogSession = waitForFutureDoneAndAssertSuccessful(
                secondRecogSessionFuture);
        getService().initDetectRejectLatch();
        secondRecogSession.triggerRecognitionEvent(new byte[]{0x11, 0x22},
                createKeyphraseRecognitionExtraList());
        getService().waitOnDetectOrRejectCalled();
        AlwaysOnHotwordDetector.EventPayload detectResult =
                getService().getHotwordServiceOnDetectedResult();
        Helper.verifyDetectedResult(detectResult, Helper.DETECTED_RESULT);
    }

    @Test
    public void testRecognitionNotRequested_afterResumeFailed() throws Exception {
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();

        SoundTriggerInstrumentation instrumentation =
                mInstrumentationObserver.getGlobalCallbackObserver().getInstrumentation();

        final var modelSessionFuture = mInstrumentationObserver.getGlobalCallbackObserver()
                .getOnModelLoadedFuture();

        final var firstRecogSessionFuture = Futures.transformAsync(modelSessionFuture,
                ModelSessionObserver::getOnRecognitionStartedFuture, Runnable::run);


        mAlwaysOnHotwordDetector.startRecognition(0, new byte[]{1, 2, 3, 4, 5});
        final var modelSession = waitForFutureDoneAndAssertSuccessful(modelSessionFuture);
        assertThat(modelSession).isNotNull();
        final var firstRecogSession = waitForFutureDoneAndAssertSuccessful(
                firstRecogSessionFuture);
        assertThat(firstRecogSession).isNotNull();

        modelSession.resetOnRecognitionStartedFuture();
        final var secondRecogSessionFuture = modelSession.getOnRecognitionStartedFuture();

        instrumentation.setResourceContention(true);

        getService().initOnRecognitionPausedLatch();
        // Induce a recognition pause
        firstRecogSession.triggerAbortRecognition();
        getService().waitOnRecognitionPausedCalled();

        getService().initOnFailureLatch();
        // Framework will attempt to resume recognition, but will fail due to set contention
        instrumentation.triggerOnResourcesAvailable();
        getService().waitOnFailureCalled();
        var failure = getService().getSoundTriggerFailure();
        assertThat(failure.getErrorCode()).isEqualTo(ERROR_CODE_RECOGNITION_RESUME_FAILED);
        assertThat(secondRecogSessionFuture.isDone()).isFalse();
        // Triggers available callback, and start will now succeed
        instrumentation.setResourceContention(false);
        // We should now be in the not requested state
        assertThrows(TimeoutException.class, () -> secondRecogSessionFuture.get(
                    WAIT_EXPECTED_NO_CALL_TIMEOUT_IN_MS,
                    TimeUnit.MILLISECONDS));
    }

    private static void setSoundTriggerPowerSaveMode(PowerManager powerManager, int mode) {
        final BatterySaverPolicyConfig newFullPolicyConfig =
                new BatterySaverPolicyConfig.Builder(powerManager.getFullPowerSavePolicy())
                        .setSoundTriggerMode(mode)
                        .build();
        powerManager.setFullPowerSavePolicy(newFullPolicyConfig);
    }

    @ApiTest(apis = {
            "android.media.voice.KeyphraseModelManager#updateKeyphraseSoundModel",
            "android.service.voice.AlwaysOnHotwordDetector#destroy",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onAvailabilityChanged",
            "android.service.voice.AlwaysOnHotwordDetector"
                    + ".Callback#onHotwordDetectionServiceInitialized",
            "android.service.voice.VoiceInteractionService#createAlwaysOnHotwordDetector",
    })
    @Test
    public void testDestroy_halModelUnloadedAndClientDetached() throws Exception {
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();

        mAlwaysOnHotwordDetector.startRecognition(0, new byte[]{1, 2, 3, 4, 5});
        assertThat(waitForFutureDoneAndAssertSuccessful(
                mInstrumentationObserver.getOnRecognitionStartedFuture())).isNotNull();

        ModelSessionObserver modelSessionObserver = waitForFutureDoneAndAssertSuccessful(
                mInstrumentationObserver.getGlobalCallbackObserver().getOnModelLoadedFuture());
        assertThat(modelSessionObserver).isNotNull();

        // destroy detector to trigger a client detach
        mAlwaysOnHotwordDetector.destroy();

        waitForVoidFutureAndAssertSuccessful(modelSessionObserver.getOnRecognitionStoppedFuture());
        waitForVoidFutureAndAssertSuccessful(modelSessionObserver.getOnModelUnloadedFuture());
        waitForVoidFutureAndAssertSuccessful(
                mInstrumentationObserver.getGlobalCallbackObserver().getOnClientDetachedFuture());
    }

    @ApiTest(apis = {
            "android.media.voice.KeyphraseModelManager#updateKeyphraseSoundModel",
            "android.service.voice.VoiceInteractionService#createAlwaysOnHotwordDetector",
            "android.service.voice.AlwaysOnHotwordDetector#destroy",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onAvailabilityChanged",
            "android.service.voice.AlwaysOnHotwordDetector"
                    + ".Callback#onHotwordDetectionServiceInitialized",
    })
    @Test
    public void testDestroy_clientDetachedWhenNoModelLoaded() throws Exception {
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();

        waitForVoidFutureAndAssertSuccessful(
                mInstrumentationObserver.getGlobalCallbackObserver().getOnClientAttachedFuture());

        // destroy detector to trigger a client detach
        mAlwaysOnHotwordDetector.destroy();

        waitForVoidFutureAndAssertSuccessful(
                mInstrumentationObserver.getGlobalCallbackObserver().getOnClientDetachedFuture());
    }

    @ApiTest(apis = {
            "android.media.voice.KeyphraseModelManager#updateKeyphraseSoundModel",
            "android.service.voice.AlwaysOnHotwordDetector#destroy",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onAvailabilityChanged",
            "android.service.voice.AlwaysOnHotwordDetector"
                    + ".Callback#onHotwordDetectionServiceInitialized",
            "android.service.voice.VoiceInteractionService#createAlwaysOnHotwordDetector",
    })
    @Test
    public void testDestroy_doubleCallsAreNoop() throws Exception {
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();
        waitForVoidFutureAndAssertSuccessful(
                mInstrumentationObserver.getGlobalCallbackObserver().getOnClientAttachedFuture());

        // destroy detector to trigger a client detach
        mAlwaysOnHotwordDetector.destroy();

        waitForVoidFutureAndAssertSuccessful(
                mInstrumentationObserver.getGlobalCallbackObserver().getOnClientDetachedFuture());
        mInstrumentationObserver.getGlobalCallbackObserver().resetOnClientAttachedFuture();
        mInstrumentationObserver.getGlobalCallbackObserver().resetOnClientDetachedFuture();

        // 2nd destroy does nothing to HAL
        mAlwaysOnHotwordDetector.destroy();

        // Verify that the client attach/detach futures were never completed after the second
        // destroy. It is okay to not wait for this as the destroy call is synchronous.
        assertThat(mInstrumentationObserver.getGlobalCallbackObserver()
                        .getOnClientAttachedFuture().isDone()).isFalse();
        assertThat(mInstrumentationObserver.getGlobalCallbackObserver()
                        .getOnClientDetachedFuture().isDone()).isFalse();
    }

    @ApiTest(apis = {
            "android.media.voice.KeyphraseModelManager#updateKeyphraseSoundModel",
            "android.service.voice.AlwaysOnHotwordDetector#startRecognition",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onAvailabilityChanged",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onDetected",
            "android.service.voice.AlwaysOnHotwordDetector"
                    + ".Callback#onHotwordDetectionServiceInitialized",
            "android.service.voice.VoiceInteractionService#createAlwaysOnHotwordDetector",
    })
    @Test
    public void testDestroy_destroyAndRecreateCreatesNewHotwordDetectionService() throws Exception {
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();

        mAlwaysOnHotwordDetector.destroy();

        PersistableBundle options = new PersistableBundle();
        options.putInt(Helper.KEY_TEST_SCENARIO,
                Utils.EXTRA_HOTWORD_DETECTION_SERVICE_SEND_SUCCESS_IF_CREATED_AFTER);
        options.putLong(Utils.KEY_TIMESTAMP_MILLIS, SystemClock.elapsedRealtime());
        // create call verifies initialize success.
        // This means the HotwordDetectionService was recreated between AOHD destroy and create.
        createAndEnrollAlwaysOnHotwordDetector(options);
    }

    @ApiTest(apis = {
            "android.media.voice.KeyphraseModelManager#updateKeyphraseSoundModel",
            "android.service.voice.AlwaysOnHotwordDetector#startRecognition",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onAvailabilityChanged",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onDetected",
            "android.service.voice.AlwaysOnHotwordDetector"
                    + ".Callback#onHotwordDetectionServiceInitialized",
            "android.service.voice.VoiceInteractionService#createAlwaysOnHotwordDetector",
    })
    @Test
    public void testOnDetected_timestampIsAfterRecognitionStarted() throws Exception {
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();
        // Start recognition
        mAlwaysOnHotwordDetector.startRecognition(0, new byte[]{1, 2, 3, 4, 5});
        RecognitionSession recognitionSession = waitForFutureDoneAndAssertSuccessful(
                mInstrumentationObserver.getOnRecognitionStartedFuture());
        assertThat(recognitionSession).isNotNull();

        getService().initDetectRejectLatch();
        long timestampCheckpoint = SystemClock.elapsedRealtime();
        recognitionSession.triggerRecognitionEvent(new byte[]{0x11, 0x22},
                createKeyphraseRecognitionExtraList());
        getService().waitOnDetectOrRejectCalled();
        AlwaysOnHotwordDetector.EventPayload detectResult =
                getService().getHotwordServiceOnDetectedResult();

        assertThat(detectResult.getHalEventReceivedMillis()).isGreaterThan(timestampCheckpoint);
    }

    @ApiTest(apis = {
            "android.media.voice.KeyphraseModelManager#updateKeyphraseSoundModel",
            "android.service.voice.AlwaysOnHotwordDetector#startRecognition",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onAvailabilityChanged",
            "android.service.voice.AlwaysOnHotwordDetector.Callback#onFailure",
            "android.service.voice.AlwaysOnHotwordDetector"
                    + ".Callback#onHotwordDetectionServiceInitialized",
            "android.service.voice.VoiceInteractionService#createAlwaysOnHotwordDetector",
    })
    @Test
    public void testDspEnrollment_enrollmentStopsRunningSession_onFailureAndEnrolledReceived()
            throws Exception {
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();
        // Start recognition
        mAlwaysOnHotwordDetector.startRecognition(0, new byte[]{1, 2, 3, 4, 5});
        ModelSessionObserver modelSessionObserver = waitForFutureDoneAndAssertSuccessful(
                mInstrumentationObserver.getGlobalCallbackObserver().getOnModelLoadedFuture());
        assertThat(modelSessionObserver).isNotNull();
        assertThat(waitForFutureDoneAndAssertSuccessful(
                modelSessionObserver.getOnRecognitionStartedFuture())).isNotNull();

        getService().initOnFailureLatch();
        getService().initAvailabilityChangeLatch();

        // enroll a new model triggering a stopRecognition
        mEnrollOverrideRule.getModelManager().updateKeyphraseSoundModel(
                new SoundTrigger.KeyphraseSoundModel(new UUID(5, 7),
                        new UUID(7, 5), /* data= */ null, KEYPHRASE_ARRAY));

        // verify that both a failure callback was received and the model was stopped
        // this indicates that the stopRecognition call internal to AlwaysOnHotwordDetector was
        // made successfully
        getService().waitOnFailureCalled();
        waitForVoidFutureAndAssertSuccessful(modelSessionObserver.getOnRecognitionStoppedFuture());

        SoundTriggerFailure soundTriggerFailure = getService().getSoundTriggerFailure();
        assertThat(soundTriggerFailure.getErrorCode()).isEqualTo(ERROR_CODE_UNKNOWN);
        assertThat(soundTriggerFailure.getErrorMessage()).isEqualTo(
                "stopped recognition because of enrollment update");
        assertThat(soundTriggerFailure.getSuggestedAction()).isEqualTo(
                FailureSuggestedAction.RESTART_RECOGNITION);

        getService().waitAvailabilityChangedCalled();
        assertThat(getService().getHotwordDetectionServiceAvailabilityResult()).isEqualTo(
                AlwaysOnHotwordDetector.STATE_KEYPHRASE_ENROLLED);

        // a second update to the enrollment database wil not trigger another onFailure because
        // the model is already stopped
        getService().initOnFailureLatch();
        getService().initAvailabilityChangeLatch();

        mEnrollOverrideRule.getModelManager().updateKeyphraseSoundModel(
                new SoundTrigger.KeyphraseSoundModel(new UUID(5, 7),
                        new UUID(7, 5), /* data= */ null, KEYPHRASE_ARRAY));

        getService().waitAvailabilityChangedCalled();
        assertThat(getService().getHotwordDetectionServiceAvailabilityResult()).isEqualTo(
                AlwaysOnHotwordDetector.STATE_KEYPHRASE_ENROLLED);

        assertThat(getService().isOnFailureLatchOpen()).isTrue();
    }
}
