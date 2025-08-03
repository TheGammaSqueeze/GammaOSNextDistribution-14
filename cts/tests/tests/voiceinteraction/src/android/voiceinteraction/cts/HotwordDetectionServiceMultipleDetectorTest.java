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
import static android.Manifest.permission.CAPTURE_AUDIO_HOTWORD;
import static android.Manifest.permission.MANAGE_HOTWORD_DETECTION;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.service.voice.SandboxedDetectionInitializer.INITIALIZATION_STATUS_SUCCESS;
import static android.voiceinteraction.cts.testcore.Helper.CTS_SERVICE_PACKAGE;
import static android.voiceinteraction.cts.testcore.Helper.MANAGE_VOICE_KEYPHRASES;
import static android.voiceinteraction.cts.testcore.Helper.createKeyphraseRecognitionExtraList;
import static android.voiceinteraction.cts.testcore.Helper.waitForFutureDoneAndAssertSuccessful;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import android.app.UiAutomation;
import android.content.ComponentName;
import android.hardware.soundtrigger.SoundTrigger;
import android.media.soundtrigger.SoundTriggerInstrumentation;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.VisualQueryDetector;
import android.soundtrigger.cts.instrumentation.SoundTriggerInstrumentationObserver;
import android.util.Log;
import android.voiceinteraction.common.Utils;
import android.voiceinteraction.cts.services.BaseVoiceInteractionService;
import android.voiceinteraction.cts.services.CtsBasicVoiceInteractionService;
import android.voiceinteraction.cts.testcore.AssumptionCheckerRule;
import android.voiceinteraction.cts.testcore.Helper;
import android.voiceinteraction.cts.testcore.VoiceInteractionServiceConnectedRule;
import android.voiceinteraction.service.MainVisualQueryDetectionService;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.RequiresDevice;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

@AppModeFull(reason = "No real use case for instant mode")
public class HotwordDetectionServiceMultipleDetectorTest {
    private static final String TAG = "HotwordDetectionServiceMultipleDetectorTest";
    // The VoiceInteractionService used by this test
    private static final String SERVICE_COMPONENT =
            "android.voiceinteraction.cts.services.CtsBasicVoiceInteractionService";
    private static final long SETUP_WAIT_MS = 10_000;

    private SoundTrigger.Keyphrase[] mKeyphraseArray;

    private CtsBasicVoiceInteractionService mService;

    private final SoundTriggerInstrumentationObserver mInstrumentationObserver =
            new SoundTriggerInstrumentationObserver();

    @Rule
    public AssumptionCheckerRule checkVisualQueryDetectionServiceEnabledRule =
            new AssumptionCheckerRule(() -> Utils.SYSPROP_VISUAL_QUERY_SERVICE_ENABLED,
                    "Testing VisualQueryDetectionService requires enabling the feature");

    @Rule
    public VoiceInteractionServiceConnectedRule mConnectedRule =
            new VoiceInteractionServiceConnectedRule(
                    getInstrumentation().getTargetContext(), getTestVoiceInteractionService());

    @Before
    public void setup() {
        // VoiceInteractionServiceConnectedRule handles the service connected,
        // the test should be able to get service
        mService = (CtsBasicVoiceInteractionService) BaseVoiceInteractionService.getService();
        // Check the test can get the service
        Objects.requireNonNull(mService);

        mKeyphraseArray = Helper.createKeyphraseArray(mService);

        // Hook up SoundTriggerInjection to inject/observe STHAL operations.
        // Requires MANAGE_SOUND_TRIGGER
        runWithShellPermissionIdentity(() ->
                mInstrumentationObserver.attachInstrumentation());

        // Wait the original VisualQueryDetector or HotwordDetectionService to finish clean up to
        // avoid flakiness.
        SystemClock.sleep(SETUP_WAIT_MS);
    }

    @After
    public void tearDown() {
        try {
            mInstrumentationObserver.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        mService.resetState();
        mService = null;
    }

    public String getTestVoiceInteractionService() {
        Log.d(TAG, "getTestVoiceInteractionService()");
        return new ComponentName(CTS_SERVICE_PACKAGE, SERVICE_COMPONENT).flattenToString();
    }

    @Test
    @RequiresDevice
    public void  testVoiceInteractionService_createMultipleDetectorSuccess()
            throws Throwable {
        // onitialize variables
        AlwaysOnHotwordDetector alwaysOnHotwordDetector = null;
        VisualQueryDetector visualQueryDetector = null;
        try {
            // Assertion is done in the private method.
            alwaysOnHotwordDetector = createAlwaysOnHotwordDetector();
            visualQueryDetector = createVisualQueryDetector();
        } finally {
            if (alwaysOnHotwordDetector != null) {
                alwaysOnHotwordDetector.destroy();
            }
            if (visualQueryDetector != null) {
                visualQueryDetector.destroy();
            }
        }
    }

    @Test
    @RequiresDevice
    public void  testVoiceInteractionService_createMultipleDetectorRestartSuccess()
            throws Throwable {
        // initialize variables
        AlwaysOnHotwordDetector alwaysOnHotwordDetector = null;
        VisualQueryDetector visualQueryDetector = null;
        try {
            // Assertion is done in the private method.
            alwaysOnHotwordDetector = createAlwaysOnHotwordDetector();
            visualQueryDetector = createVisualQueryDetector();
            mService.initOnVisualQueryDetectionServiceRestartedLatch();
            mService.initOnHotwordDetectionServiceRestartedLatch();
            // force re-start by shell command
            runShellCommand("cmd voiceinteraction restart-detection");
            // wait onHotwordDetectionServiceRestarted() called
            mService.waitOnVisualQueryDetectionServiceRestartedCalled();
            mService.waitOnHotwordDetectionServiceRestartedCalled();
        } finally {
            if (alwaysOnHotwordDetector != null) {
                alwaysOnHotwordDetector.destroy();
            }
            if (visualQueryDetector != null) {
                visualQueryDetector.destroy();
            }
        }
    }

    @Test
    @RequiresDevice
    public void testHotwordDetectionServiceMultipleDetectors_detectHotwordDSPThenVisualQuery()
            throws Throwable {

        // initialize variables
        AlwaysOnHotwordDetector alwaysOnHotwordDetector = null;
        VisualQueryDetector visualQueryDetector = null;

        try {
            // Create AlwaysOnHotwordDetector
            alwaysOnHotwordDetector = createAlwaysOnHotwordDetector();

            // Create VisualQueryDetector
            visualQueryDetector = createVisualQueryDetector();

            // local variables in lambda expression needs to be final
            final VisualQueryDetector finalVisualQueryDetector = visualQueryDetector;
            final AlwaysOnHotwordDetector finalAlwaysOnHotwordDetector = alwaysOnHotwordDetector;
            // Update HotwordDetectionService options to enable Audio egress
            runWithShellPermissionIdentity(() -> {
                PersistableBundle options = Helper.createFakePersistableBundleData();
                // Scenario for hotword detection service
                options.putInt(Helper.KEY_TEST_SCENARIO,
                        Utils.EXTRA_HOTWORD_DETECTION_SERVICE_ENABLE_AUDIO_EGRESS);
                // Scenario for visual query detection service
                options.putInt(MainVisualQueryDetectionService.KEY_VQDS_TEST_SCENARIO,
                        MainVisualQueryDetectionService.SCENARIO_ATTENTION_QUERY_FINISHED_LEAVE);
                finalVisualQueryDetector.updateState(options, Helper.createFakeSharedMemoryData());
                finalAlwaysOnHotwordDetector.updateState(
                        options,
                        Helper.createFakeSharedMemoryData());
            }, MANAGE_HOTWORD_DETECTION);

            // For hotword detection
            verifyHotwordOnDetectFromDspSuccess(alwaysOnHotwordDetector);
            // For visual query detection
            verifyVisualQueryOnDetectSuccess(visualQueryDetector);
        } finally {
            // destroy detector
            alwaysOnHotwordDetector.destroy();
            visualQueryDetector.destroy();
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresDevice
    public void testHotwordDetectionServiceMultipleDetectors_detectVisualQueryThenHotwordDSP()
            throws Throwable {

        // initialize variables
        AlwaysOnHotwordDetector alwaysOnHotwordDetector = null;
        VisualQueryDetector visualQueryDetector = null;

        try {
            // Create VisualQueryDetector
            visualQueryDetector = createVisualQueryDetector();

            // Create AlwaysOnHotwordDetector
            alwaysOnHotwordDetector = createAlwaysOnHotwordDetector();

            // local variables in lambda expression needs to be final
            final AlwaysOnHotwordDetector finalAlwaysOnHotwordDetector = alwaysOnHotwordDetector;
            final VisualQueryDetector finalVisualQueryDetector = visualQueryDetector;

            // Update HotwordDetectionService options to enable Audio egress
            runWithShellPermissionIdentity(() -> {
                PersistableBundle options = Helper.createFakePersistableBundleData();
                // Scenario for hotword detection service
                options.putInt(Helper.KEY_TEST_SCENARIO,
                        Utils.EXTRA_HOTWORD_DETECTION_SERVICE_ENABLE_AUDIO_EGRESS);
                // Scenario for visual query detection service
                options.putInt(MainVisualQueryDetectionService.KEY_VQDS_TEST_SCENARIO,
                        MainVisualQueryDetectionService.SCENARIO_ATTENTION_QUERY_FINISHED_LEAVE);
                finalAlwaysOnHotwordDetector.updateState(
                        options,
                        Helper.createFakeSharedMemoryData());
                finalVisualQueryDetector.updateState(options, Helper.createFakeSharedMemoryData());
            }, MANAGE_HOTWORD_DETECTION);

            // For visual query detection
            verifyVisualQueryOnDetectSuccess(visualQueryDetector);

            // For hotword detection
            verifyHotwordOnDetectFromDspSuccess(alwaysOnHotwordDetector);
        } finally {
            // destroy detector
            visualQueryDetector.destroy();
            alwaysOnHotwordDetector.destroy();
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresDevice
    public void testHotwordDetectionServiceMultipleDetectors_restart_detectVisualQueryThenHotword()
            throws Throwable {

        // initialize variables
        AlwaysOnHotwordDetector alwaysOnHotwordDetector = null;
        VisualQueryDetector visualQueryDetector = null;

        try {
            // Assertion is done in the private method.
            alwaysOnHotwordDetector = createAlwaysOnHotwordDetector();
            visualQueryDetector = createVisualQueryDetector();
            mService.initOnVisualQueryDetectionServiceRestartedLatch();
            mService.initOnHotwordDetectionServiceRestartedLatch();
            // force re-start by shell command
            runShellCommand("cmd voiceinteraction restart-detection");
            // wait onHotwordDetectionServiceRestarted() called
            mService.waitOnVisualQueryDetectionServiceRestartedCalled();
            mService.waitOnHotwordDetectionServiceRestartedCalled();
            // local variables in lambda expression needs to be final
            final AlwaysOnHotwordDetector finalAlwaysOnHotwordDetector = alwaysOnHotwordDetector;
            final VisualQueryDetector finalVisualQueryDetector = visualQueryDetector;
            // Update HotwordDetectionService options to enable Audio egress
            runWithShellPermissionIdentity(() -> {
                PersistableBundle options = Helper.createFakePersistableBundleData();
                // Scenario for hotword detection service
                options.putInt(Helper.KEY_TEST_SCENARIO,
                        Utils.EXTRA_HOTWORD_DETECTION_SERVICE_ENABLE_AUDIO_EGRESS);
                // Scenario for visual query detection service
                options.putInt(MainVisualQueryDetectionService.KEY_VQDS_TEST_SCENARIO,
                        MainVisualQueryDetectionService.SCENARIO_ATTENTION_QUERY_FINISHED_LEAVE);
                finalAlwaysOnHotwordDetector.updateState(
                        options,
                        Helper.createFakeSharedMemoryData());
                finalVisualQueryDetector.updateState(options, Helper.createFakeSharedMemoryData());
            }, MANAGE_HOTWORD_DETECTION);

            // For visual query detection
            verifyVisualQueryOnDetectSuccess(visualQueryDetector);

            // For hotword detection
            verifyHotwordOnDetectFromDspSuccess(alwaysOnHotwordDetector);
        } finally {
            // destroy detector
            visualQueryDetector.destroy();
            alwaysOnHotwordDetector.destroy();
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    private void adoptShellPermissionIdentity() {
        // Drop any identity adopted earlier.
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.dropShellPermissionIdentity();
        // need to retain the identity until the callback is triggered
        uiAutomation.adoptShellPermissionIdentity(RECORD_AUDIO, CAPTURE_AUDIO_HOTWORD, CAMERA);
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


    private void verifyHotwordOnDetectFromDspSuccess(
            AlwaysOnHotwordDetector alwaysOnHotwordDetector) throws Throwable {
        try {
            adoptShellPermissionIdentity();
            alwaysOnHotwordDetector.startRecognition(0, new byte[]{1, 2, 3, 4, 5});
            SoundTriggerInstrumentation.RecognitionSession recognitionSession =
                    waitForFutureDoneAndAssertSuccessful(mInstrumentationObserver
                            .getOnRecognitionStartedFuture());
            assertThat(recognitionSession).isNotNull();

            mService.initDetectRejectLatch();
            recognitionSession.triggerRecognitionEvent(new byte[1024],
                    createKeyphraseRecognitionExtraList());

            // wait onDetected() called and verify the result
            mService.waitOnDetectOrRejectCalled();
            AlwaysOnHotwordDetector.EventPayload detectResult =
                    mService.getHotwordServiceOnDetectedResult();

            Helper.verifyDetectedResult(detectResult, Utils.AUDIO_EGRESS_DETECTED_RESULT);
        } finally {
            runWithShellPermissionIdentity(() -> mService.disableOverrideRegisterModel(),
                    MANAGE_VOICE_KEYPHRASES);
        }
    }

    private void verifyVisualQueryOnDetectSuccess(VisualQueryDetector visualQueryDetector)
            throws Throwable {
        adoptShellPermissionIdentity();
        mService.initQueryFinishRejectLatch(1);
        visualQueryDetector.startRecognition();
        mService.waitOnQueryFinishedRejectCalled();
        ArrayList<String> streamedQueries = mService.getStreamedQueriesResult();
        assertThat(streamedQueries.get(0)).isEqualTo(
                MainVisualQueryDetectionService.FAKE_QUERY_FIRST
                        + MainVisualQueryDetectionService.FAKE_QUERY_SECOND);
        assertThat(streamedQueries.size()).isEqualTo(1);
    }

    /**
     * Create AlwaysOnHotwordDetector with SoundTrigger injection.
     */
    private AlwaysOnHotwordDetector createAlwaysOnHotwordDetector() throws Throwable {
        final UUID uuid = new UUID(5, 7);
        final UUID vendorUuid = new UUID(7, 5);

        // Start override enrolled model with a custom model for test purposes.
        runWithShellPermissionIdentity(() -> mService.enableOverrideRegisterModel(
                new SoundTrigger.KeyphraseSoundModel(uuid, vendorUuid, /* data= */ null,
                        mKeyphraseArray)), MANAGE_VOICE_KEYPHRASES);

        // Call initAvailabilityChangeLatch for onAvailabilityChanged() callback called
        // following AlwaysOnHotwordDetector creation.
        mService.initAvailabilityChangeLatch();

        // Create AlwaysOnHotwordDetector and wait ready.
        mService.createAlwaysOnHotwordDetector();

        mService.waitSandboxedDetectionServiceInitializedCalledOrException();

        // verify initialization callback result
        assertThat(mService.getSandboxedDetectionServiceInitializedResult()).isEqualTo(
                INITIALIZATION_STATUS_SUCCESS);
        AlwaysOnHotwordDetector alwaysOnHotwordDetector = mService.getAlwaysOnHotwordDetector();
        Objects.requireNonNull(alwaysOnHotwordDetector);

        // verify have entered the ENROLLED state
        mService.waitAvailabilityChangedCalled();
        assertThat(mService.getHotwordDetectionServiceAvailabilityResult()).isEqualTo(
                AlwaysOnHotwordDetector.STATE_KEYPHRASE_ENROLLED);

        return alwaysOnHotwordDetector;
    }

}
