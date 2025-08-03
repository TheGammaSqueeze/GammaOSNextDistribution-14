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

package com.android.cts.voiceinteraction;

import static com.android.cts.voiceinteraction.ControllableHotwordDetectionService.KEY_FORCE_HOTWORD_RESULT_PHRASE_ID;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.expectThrows;

import android.app.compat.CompatChanges;
import android.content.Context;
import android.os.ConditionVariable;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.service.voice.AlwaysOnHotwordDetector;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests covering the System API compatibility changes in
 * {@link android.service.voice.VoiceInteractionService}
 */
@RunWith(AndroidJUnit4.class)
public class VoiceInteractionCompatTests extends AbstractVoiceInteractionServiceTest {
    private static final String TAG = VoiceInteractionCompatTests.class.getSimpleName();
    private static final long ENFORCE_HOTWORD_PHRASE_ID = 215066299L;

    // START of change ENABLED tests

    @Test
    public void enforceHotwordPhraseIdEnabled() {
        Log.i(TAG, "enforceHotwordPhraseIdEnabled");
        assertThat(CompatChanges.isChangeEnabled(ENFORCE_HOTWORD_PHRASE_ID)).isTrue();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertThat(CompatChanges.isChangeEnabled(ENFORCE_HOTWORD_PHRASE_ID,
                ProxyVoiceInteractionService.class.getPackageName(),
                context.getUser())).isTrue();
    }

    @Test
    public void enforceHotwordPhraseIdEnabled_rejectNotMatchingPhraseId() throws Exception {
        Log.i(TAG, "enforceHotwordPhraseIdEnabled_rejectNotMatchingPhraseId");
        expectThrows(DetectorErrorException.class, () ->
                createBasicDetectorWithTrustedServiceAndTriggerKeyphrase(1, 10));
    }

    @Test
    public void enforceHotwordPhraseIdEnabled_rejectPhraseIdNotSet() throws Exception {
        Log.i(TAG, "enforceHotwordPhraseIdEnabled_rejectPhraseIdNotSet");
        expectThrows(DetectorErrorException.class, () ->
                createBasicDetectorWithTrustedServiceAndTriggerKeyphrase(1,
                        DEFAULT_HOTWORD_DETECTED_RESULT_PHRASE_ID));
    }

    @Test
    public void enforceHotwordPhraseIdEnabled_acceptMatchingPhraseId() throws Exception {
        Log.i(TAG, "enforceHotwordPhraseIdEnabled_acceptMatchingPhraseId");
        assertThat(createBasicDetectorWithTrustedServiceAndTriggerKeyphrase(1, 1)
                .mHotwordDetectedResult.getHotwordPhraseId()).isEqualTo(1);
    }

    // END of change ENABLED tests

    // START of change DISABLED tests

    @Test
    public void enforceHotwordPhraseIdDisabled() {
        Log.i(TAG, "enforceHotwordPhraseIdDisabled");
        assertThat(CompatChanges.isChangeEnabled(ENFORCE_HOTWORD_PHRASE_ID)).isFalse();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertThat(CompatChanges.isChangeEnabled(ENFORCE_HOTWORD_PHRASE_ID,
                ProxyVoiceInteractionService.class.getPackageName(),
                context.getUser())).isFalse();
    }

    @Test
    public void enforceHotwordPhraseIdDisabled_acceptNotMatchingPhraseId() throws Exception {
        Log.i(TAG, "enforceHotwordPhraseIdDisabled_acceptNotMatchingPhraseId");
        assertThat(createBasicDetectorWithTrustedServiceAndTriggerKeyphrase(1, 10)
                .mHotwordDetectedResult.getHotwordPhraseId()).isEqualTo(10);
    }

    @Test
    public void enforceHotwordPhraseIdDisabled_acceptPhraseIdNotSet() throws Exception {
        Log.i(TAG, "enforceHotwordPhraseIdDisabled_acceptPhraseIdNotSet");
        assertThat(createBasicDetectorWithTrustedServiceAndTriggerKeyphrase(1,
                DEFAULT_HOTWORD_DETECTED_RESULT_PHRASE_ID)
                .mHotwordDetectedResult.getHotwordPhraseId()).isEqualTo(0);
    }

    @Test
    public void enforceHotwordPhraseIdDisabled_acceptMatchingPhraseId() throws Exception {
        Log.i(TAG, "enforceHotwordPhraseIdDisabled_acceptMatchingPhraseId");
        assertThat(createBasicDetectorWithTrustedServiceAndTriggerKeyphrase(1, 1)
                .mHotwordDetectedResult.getHotwordPhraseId()).isEqualTo(1);
    }

    // END of change DISABLED tests

    /**
     * Test helper method that does the following in order:
     * 1. Create AlwaysOnHotwordDetector with the test VoiceInteractionService that is linked to
     * the trusted test HotwordDetectionService
     * 2. Send an update the test HotwordDetectionService to always respond to a detection request
     * with the provided phrase ID param
     * 3. Enroll a fake SoundTrigger model with the HotwordDetector session
     * 4. Force a fake DSP detection event to be sent to the test HotwordDetectionService
     * 5. Verify that the onDetected event is successful and return the EventPayload
     *
     * @param dspTriggerKeyphraseId                 Keyphrase ID sent to the HotwordDetectionService
     *                                              via a fake DSP trigger
     * @param hotwordDetectionServiceResultPhraseId Keyphrase ID the HotwordDetectionService will
     *                                              always respond with when receiving a detection
     *                                              request
     * @return EventPayloadParceleable that is returned by the HotwordDetector onDetected callback
     * @throws RemoteException        Thrown on failing IPC communication
     * @throws InterruptedException   Thrown when if an onDetected event is not received within the
     *                                timeout window
     * @throws DetectorErrorException Thrown when an
     *                                {@link AlwaysOnHotwordDetector.Callback#onError()} was seen
     *                                while waiting for
     *                                {@link
     *            AlwaysOnHotwordDetector.Callback#onDetected(AlwaysOnHotwordDetector.EventPayload)}
     */
    private EventPayloadParcelable createBasicDetectorWithTrustedServiceAndTriggerKeyphrase(
            int dspTriggerKeyphraseId,
            int hotwordDetectionServiceResultPhraseId) throws RemoteException,
            InterruptedException, DetectorErrorException {
        // Create detector using trusted process
        final PersistableBundle forcePhraseIdOptions = new PersistableBundle();
        forcePhraseIdOptions.putInt(KEY_FORCE_HOTWORD_RESULT_PHRASE_ID,
                hotwordDetectionServiceResultPhraseId);
        final AtomicReference<EventPayloadParcelable> onDetectedEventPayload =
                new AtomicReference<>();
        final ConditionVariable onDetectedReceivedCondition = new ConditionVariable();
        final ConditionVariable detectionServiceInitializedCondition = new ConditionVariable();
        final AtomicBoolean onErrorReceived = new AtomicBoolean();
        IProxyAlwaysOnHotwordDetector trustedDetector =
                mTestServiceInterface.createAlwaysOnHotwordDetectorWithTrustedService(
                        TEST_DETECTOR_KEYPHRASE,
                        Locale.ENGLISH.toLanguageTag(),
                        forcePhraseIdOptions,
                        /* sharedMemory */ null,
                        new IProxyDetectorCallback.Stub() {
                            @Override
                            public void onAvailabilityChanged(int status) {
                                Log.i(TAG, "onAvailabilityChanged: status=" + status);
                            }

                            @Override
                            public void onDetected(EventPayloadParcelable eventPayload) {
                                Log.i(TAG, "onDetected: eventPayload=" + eventPayload);
                                onDetectedEventPayload.set(eventPayload);
                                onDetectedReceivedCondition.open();
                            }

                            @Override
                            public void onHotwordDetectionServiceInitialized(int status) {
                                Log.i(TAG,
                                        "onHotwordDetectionServiceInitialized: status=" + status);
                                detectionServiceInitializedCondition.open();
                            }

                            @Override
                            public void onError() {
                                Log.i(TAG, "onError");
                                onErrorReceived.set(true);
                            }
                        });
        // wait for initial result, but we do not care what the value is because we will enroll
        // again
        trustedDetector.waitForNextAvailabilityUpdate((int) TEST_SERVICE_TIMEOUT.toMillis());

        enrollFakeSoundModelWithDetector(trustedDetector, Locale.ENGLISH);
        assertThat(trustedDetector.waitForNextAvailabilityUpdate(
                (int) TEST_SERVICE_TIMEOUT.toMillis())).isEqualTo(
                AlwaysOnHotwordDetector.STATE_KEYPHRASE_ENROLLED);

        assertThat(detectionServiceInitializedCondition.block(
                TEST_SERVICE_TIMEOUT.toMillis())).isTrue();

        // simulate a keyphrase trigger into trusted service
        trustedDetector.triggerHardwareRecognitionEventWithFakeAudioStream(
                dspTriggerKeyphraseId,
                /* confidenceLevel */ 100);

        if (onDetectedReceivedCondition.block(TEST_SERVICE_TIMEOUT.toMillis())) {
            return onDetectedEventPayload.get();
        }
        if (onErrorReceived.get()) {
            throw new DetectorErrorException("onError seen waiting for onDetected");
        }
        throw new InterruptedException("Timeout waiting for onDetected");
    }
}

