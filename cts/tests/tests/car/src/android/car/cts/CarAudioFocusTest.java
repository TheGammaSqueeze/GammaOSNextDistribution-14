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

package android.car.cts;

import static android.Manifest.permission.MODIFY_AUDIO_SETTINGS_PRIVILEGED;
import static android.car.media.CarAudioManager.AUDIO_FEATURE_DYNAMIC_ROUTING;
import static android.media.AudioManager.AUDIOFOCUS_GAIN;
import static android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK;
import static android.media.AudioManager.AUDIOFOCUS_LOSS;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_DELAYED;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_FAILED;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.UiAutomation;
import android.car.Car;
import android.car.media.CarAudioManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Looper;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Instant Apps cannot get car related permissions")
public final class CarAudioFocusTest extends AbstractCarTestCase {

    private static final String TAG = "CarAudioFocusTest.class.getSimpleName()";
    private static final long TEST_TIMING_TOLERANCE_MS = 100;
    private static final int TEST_TOLERANCE_MAX_ITERATIONS = 5;
    private static final int INTERACTION_REJECT = 0;  // Focus not granted
    private static final int INTERACTION_EXCLUSIVE = 1;  // Focus granted, others loose focus
    private static final int INTERACTION_CONCURRENT = 2;  // Focus granted, others keep focus

    private static final UiAutomation UI_AUTOMATION =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    // CarAudioContext.MUSIC
    private static final AudioAttributes ATTR_MEDIA = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build();
    // CarAudioContext.NAVIGATION
    private static final AudioAttributes ATTR_NAVIGATION = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();
    // CarAudioContext.VOICE_COMMAND
    private static final AudioAttributes ATTR_VOICE_COMMAND = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();
    // CarAudioContext.CALL_RING
    private static final AudioAttributes ATTR_CALL_RING = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();
    // CarAudioContext.CALL
    private static final AudioAttributes ATTR_CALL = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();
    // CarAudioContext.ALARM
    private static final AudioAttributes ATTR_ALARM = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();
    // CarAudioContext.NOTIFICATION
    private static final AudioAttributes ATTR_NOTIFICATION = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();
    // CarAudioContext.SYSTEM_SOUND
    private static final AudioAttributes ATTR_SYSTEM_SOUND = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();

    private final Set<AudioFocusRequest> mAudioFocusRequestsSet = new HashSet<>();

    private AudioManager mAudioManager;
    private CarAudioManager mCarAudioManager;

    @Before
    public void setUp() throws Exception {
        UI_AUTOMATION.adoptShellPermissionIdentity(MODIFY_AUDIO_SETTINGS_PRIVILEGED);
        mAudioManager = mContext.getSystemService(AudioManager.class);
        mCarAudioManager = (CarAudioManager) getCar().getCarManager(Car.AUDIO_SERVICE);
        assertWithMessage("CarAudioManager instance").that(mCarAudioManager).isNotNull();

        assumeDynamicRoutingIsEnabled();
    }

    @After
    public void cleanUp() {
        Iterator<AudioFocusRequest> iterator = mAudioFocusRequestsSet.iterator();
        while (iterator.hasNext()) {
            AudioFocusRequest request = iterator.next();
            mAudioManager.abandonAudioFocusRequest(request);
            Log.d(TAG, "cleanUp Removing: "
                    + usageToString(request.getAudioAttributes().getSystemUsage()));
        }
        UI_AUTOMATION.dropShellPermissionIdentity();
    }

    @Test
    public void requestAudioFocus_forRequestWithDelayedFocus_requestGranted() {
        AudioFocusRequest mediaAudioFocusRequest = delayedFocusRequestBuilder().build();

        mAudioFocusRequestsSet.add(mediaAudioFocusRequest);
        assertWithMessage("Request with delayed focus should be granted")
                .that(mAudioManager.requestAudioFocus(mediaAudioFocusRequest))
                .isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
    }

    @Test
    public void requestAudioFocus_forRequestWithDelayedFocus_whileOnCall_requestDelayed() {
        AudioFocusRequest phoneAudioFocusRequest = phoneFocusRequestBuilder().build();

        mAudioFocusRequestsSet.add(phoneAudioFocusRequest);
        mAudioManager.requestAudioFocus(phoneAudioFocusRequest);

        AudioFocusRequest mediaAudioFocusRequest = delayedFocusRequestBuilder().build();

        mAudioFocusRequestsSet.add(mediaAudioFocusRequest);
        assertWithMessage("Media focus request during call should be delayed")
                .that(mAudioManager.requestAudioFocus(mediaAudioFocusRequest))
                .isEqualTo(AUDIOFOCUS_REQUEST_DELAYED);
    }

    @Test
    public void abandonAudioFocusRequest_forCall_whileFocusDelayed_focusGained() {
        AudioFocusRequest phoneAudioFocusRequest = phoneFocusRequestBuilder().build();

        mAudioManager.requestAudioFocus(phoneAudioFocusRequest);
        mAudioFocusRequestsSet.add(phoneAudioFocusRequest);

        FocusChangeListener mediaFocusChangeListener = new FocusChangeListener();
        AudioFocusRequest mediaAudioFocusRequest = delayedFocusRequestBuilder()
                .setOnAudioFocusChangeListener(mediaFocusChangeListener).build();

        mAudioManager.requestAudioFocus(mediaAudioFocusRequest);
        mAudioFocusRequestsSet.add(mediaAudioFocusRequest);

        mAudioManager.abandonAudioFocusRequest(phoneAudioFocusRequest);
        mAudioFocusRequestsSet.remove(phoneAudioFocusRequest);

        String message = "Delayed focus request should be granted after call ended";
        assertWithMessage(message).that(mediaFocusChangeListener
                .waitForFocusChangeAndAssertFocus(TEST_TIMING_TOLERANCE_MS, AUDIOFOCUS_GAIN,
                        "Could not gain focus for delayed focus after call ended"))
                .isTrue();
    }

    @Test
    public void abandonAudioFocusRequest_forDelayedRequest_whileOnCall_requestGranted() {
        AudioFocusRequest phoneAudioFocusRequest = phoneFocusRequestBuilder().build();

        mAudioManager.requestAudioFocus(phoneAudioFocusRequest);
        mAudioFocusRequestsSet.add(phoneAudioFocusRequest);

        AudioFocusRequest mediaAudioFocusRequest = delayedFocusRequestBuilder().build();

        mAudioManager.requestAudioFocus(mediaAudioFocusRequest);
        mAudioFocusRequestsSet.add(mediaAudioFocusRequest);

        assertWithMessage("Abandoning delayed focus should be granted during call")
                .that(mAudioManager.abandonAudioFocusRequest(mediaAudioFocusRequest))
                .isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
        mAudioFocusRequestsSet.remove(mediaAudioFocusRequest);
    }

    @Test
    public void
            abandonAudioFocusRequest_forCall_afterDelayedAbandon_delayedRequestDoesNotGainsFocus() {
        AudioFocusRequest phoneAudioFocusRequest = phoneFocusRequestBuilder().build();

        mAudioManager.requestAudioFocus(phoneAudioFocusRequest);
        mAudioFocusRequestsSet.add(phoneAudioFocusRequest);

        FocusChangeListener mediaFocusChangeListener = new FocusChangeListener();
        AudioFocusRequest mediaAudioFocusRequest = delayedFocusRequestBuilder()
                .setOnAudioFocusChangeListener(mediaFocusChangeListener).build();

        mAudioManager.requestAudioFocus(mediaAudioFocusRequest);
        mAudioFocusRequestsSet.add(mediaAudioFocusRequest);

        mAudioManager.abandonAudioFocusRequest(mediaAudioFocusRequest);

        mAudioManager.abandonAudioFocusRequest(phoneAudioFocusRequest);
        mAudioFocusRequestsSet.remove(phoneAudioFocusRequest);

        String message = "Abandoned delayed focus request should not be granted after call ended";
        assertWithMessage(message).that(mediaFocusChangeListener
                .waitForFocusChangeAndAssertFocus(TEST_TIMING_TOLERANCE_MS, AUDIOFOCUS_GAIN,
                        "Focus gained for abandoned delayed request after call"))
                .isFalse();
        mAudioFocusRequestsSet.remove(mediaAudioFocusRequest);
    }

    @Test
    public void
            requestAudioFocus_multipleTimesForSameDelayedRequest_delayedRequestDoesNotGainsFocus() {
        AudioFocusRequest phoneAudioFocusRequest = phoneFocusRequestBuilder().build();

        mAudioManager.requestAudioFocus(phoneAudioFocusRequest);
        mAudioFocusRequestsSet.add(phoneAudioFocusRequest);

        FocusChangeListener mediaFocusChangeListener = new FocusChangeListener();
        AudioFocusRequest mediaAudioFocusRequest = delayedFocusRequestBuilder()
                .setOnAudioFocusChangeListener(mediaFocusChangeListener).build();

        mAudioManager.requestAudioFocus(mediaAudioFocusRequest);
        mAudioFocusRequestsSet.add(mediaAudioFocusRequest);

        mAudioManager.requestAudioFocus(mediaAudioFocusRequest);

        String message = "Delayed request after same request should not gain focus";
        assertWithMessage(message).that(mediaFocusChangeListener
                .waitForFocusChangeAndAssertFocus(TEST_TIMING_TOLERANCE_MS,
                        AUDIOFOCUS_LOSS,
                        "Focus gained for delayed request after same request"))
                .isFalse();
    }

    @Test
    public void requestAudioFocus_multipleTimesForSameFocusListener_requestFailed() {
        AudioFocusRequest phoneAudioFocusRequest = phoneFocusRequestBuilder().build();

        mAudioManager.requestAudioFocus(phoneAudioFocusRequest);
        mAudioFocusRequestsSet.add(phoneAudioFocusRequest);

        FocusChangeListener focusChangeLister = new FocusChangeListener();
        AudioFocusRequest mediaAudioFocusRequest = delayedFocusRequestBuilder()
                .setOnAudioFocusChangeListener(focusChangeLister).build();

        mAudioManager.requestAudioFocus(mediaAudioFocusRequest);
        mAudioFocusRequestsSet.add(mediaAudioFocusRequest);

        AudioFocusRequest systemSoundRequest = delayedFocusRequestBuilder()
                .setOnAudioFocusChangeListener(focusChangeLister)
                .setAudioAttributes(ATTR_SYSTEM_SOUND).build();

        mAudioFocusRequestsSet.add(systemSoundRequest);
        assertWithMessage("Second focus request on the same listener should fail")
                .that(mAudioManager.requestAudioFocus(systemSoundRequest))
                .isEqualTo(AUDIOFOCUS_REQUEST_FAILED);
        mAudioFocusRequestsSet.remove(systemSoundRequest);
    }

    @Test
    public void requestAudioFocus_forCallWhileMediaHoldsFocus_callShouldReceiveFocus() {
        AudioFocusRequest mediaAudioFocusRequest = delayedFocusRequestBuilder().build();
        mAudioFocusRequestsSet.add(mediaAudioFocusRequest);
        mAudioManager.requestAudioFocus(mediaAudioFocusRequest);
        AudioFocusRequest phoneAudioFocusRequest = phoneFocusRequestBuilder().build();
        mAudioFocusRequestsSet.add(phoneAudioFocusRequest);
        mAudioManager.requestAudioFocus(phoneAudioFocusRequest);

        int results = mAudioManager.requestAudioFocus(phoneAudioFocusRequest);

        assertWithMessage("Call focus request during media")
                .that(results).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
    }

    @Test
    public void requestAudioFocus_forAssistantWhileMediaHoldsFocus_assistantShouldReceiveFocus() {
        AudioFocusRequest mediaAudioFocusRequest = delayedFocusRequestBuilder().build();
        mAudioFocusRequestsSet.add(mediaAudioFocusRequest);
        mAudioManager.requestAudioFocus(mediaAudioFocusRequest);
        AudioFocusRequest assistantAudioFocusRequest = assistantFocusRequestBuilder().build();
        mAudioFocusRequestsSet.add(assistantAudioFocusRequest);
        mAudioManager.requestAudioFocus(assistantAudioFocusRequest);

        int results = mAudioManager.requestAudioFocus(assistantAudioFocusRequest);

        assertWithMessage("Assistant focus request during media")
                .that(results).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
    }

    @Test
    public void requestAudioFocus_forNavWhileMediaHoldsFocus_navShouldReceiveFocus() {
        AudioFocusRequest mediaAudioFocusRequest = delayedFocusRequestBuilder().build();
        mAudioFocusRequestsSet.add(mediaAudioFocusRequest);
        mAudioManager.requestAudioFocus(mediaAudioFocusRequest);
        AudioFocusRequest navigationAudioFocusRequest = navigationFocusRequestBuilder().build();
        mAudioFocusRequestsSet.add(navigationAudioFocusRequest);
        mAudioManager.requestAudioFocus(navigationAudioFocusRequest);

        int results = mAudioManager.requestAudioFocus(navigationAudioFocusRequest);

        assertWithMessage("Navigation focus request during media")
                .that(results).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
    }

    @Test
    public void individualAttributeFocusRequest_focusRequestGranted() {
        // Make sure each usage is able to request and release audio focus individually
        requestAndLoseFocusForAttribute(ATTR_MEDIA);
        requestAndLoseFocusForAttribute(ATTR_NAVIGATION);
        requestAndLoseFocusForAttribute(ATTR_VOICE_COMMAND);
        requestAndLoseFocusForAttribute(ATTR_CALL_RING);
        requestAndLoseFocusForAttribute(ATTR_CALL);
        requestAndLoseFocusForAttribute(ATTR_ALARM);
        requestAndLoseFocusForAttribute(ATTR_NOTIFICATION);
        requestAndLoseFocusForAttribute(ATTR_SYSTEM_SOUND);
    }

    @Test
    public void exclusiveInteractionsForFocusGain_requestGrantedAndFocusLossSent() {
        assumeOEMServiceIsNotEnabled();
        // For each interaction the focus request is granted and on the second request
        // focus lost is dispatched to the first focus listener

        // Test Exclusive interactions with audio focus gain request without pause
        // instead of ducking
        testExclusiveInteractions(AUDIOFOCUS_GAIN, false);
        // Test Exclusive interactions with audio focus gain request with pause instead of ducking
        testExclusiveInteractions(AUDIOFOCUS_GAIN, true);
    }

    @Test
    public void exclusiveInteractionsTransient_requestGrantedAndFocusLossSent() {
        assumeOEMServiceIsNotEnabled();
        // For each interaction the focus request is granted and on the second request
        // focus lost transient is dispatched to the first focus listener

        // Test Exclusive interactions with audio focus gain transient request
        // without pause instead of ducking
        testExclusiveInteractions(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, false);
        // Test Exclusive interactions with audio focus gain transient request
        // with pause instead of ducking
        testExclusiveInteractions(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, true);
    }

    @Test
    public void exclusiveInteractionsTransientMayDuck_requestGrantedAndFocusLossSent() {
        assumeOEMServiceIsNotEnabled();
        // For each interaction the focus request is granted and on the second request
        // focus lost transient is dispatched to the first focus listener

        // Test exclusive interactions with audio focus transient may duck focus request
        // without pause instead of ducking
        testExclusiveInteractions(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, false);
        // Test exclusive interactions with audio focus transient may duck focus request
        // with pause instead of ducking
        testExclusiveInteractions(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, true);
    }

    @Test
    public void rejectedInteractions_focusRequestRejected() {
        assumeOEMServiceIsNotEnabled();
        // Test different paired interaction between different usages
        // for each interaction pair the first focus request will be granted but the second
        // will be rejected
        int interaction = INTERACTION_REJECT;
        int gain = AUDIOFOCUS_GAIN;

        testInteraction(ATTR_VOICE_COMMAND, ATTR_NAVIGATION, interaction, gain, false);
        testInteraction(ATTR_VOICE_COMMAND, ATTR_NOTIFICATION, interaction, gain, false);
        testInteraction(ATTR_VOICE_COMMAND, ATTR_SYSTEM_SOUND, interaction, gain, false);

        testInteraction(ATTR_CALL_RING, ATTR_MEDIA, interaction, gain, false);
        testInteraction(ATTR_CALL_RING, ATTR_ALARM, interaction, gain, false);
        testInteraction(ATTR_CALL_RING, ATTR_NOTIFICATION, interaction, gain, false);

        testInteraction(ATTR_CALL, ATTR_MEDIA, interaction, gain, false);
        testInteraction(ATTR_CALL, ATTR_VOICE_COMMAND, interaction, gain, false);
        testInteraction(ATTR_CALL, ATTR_SYSTEM_SOUND, interaction, gain, false);
    }

    @Test
    public void concurrentInteractionsFocusGain_requestGrantedAndFocusLossSent() {
        assumeOEMServiceIsNotEnabled();
        // Test concurrent interactions i.e. interactions that can
        // potentially gain focus at the same time.
        // For this test permanent focus gain is requested by two usages.
        // The focus request will be granted for both and on the second focus request focus
        // lost will dispatched to the first focus listener listener.
        testConcurrentInteractions(AUDIOFOCUS_GAIN, false);
    }

    @Test
    public void concurrentInteractionsTransientGain_requestGrantedAndFocusLossTransientSent() {
        assumeOEMServiceIsNotEnabled();
        // Test concurrent interactions i.e. interactions that can
        // potentially gain focus at the same time.
        // For this test permanent focus gain is requested by first usage and focus gain transient
        // is requested by second usage.
        // The focus request will be granted for both and on the second focus request focus
        // lost transient will dispatched to the first focus listener listener.
        testConcurrentInteractions(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, false);
        // Repeat the test this time with pause for ducking on first listener
        testConcurrentInteractions(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, true);
    }

    @Test
    public void concurrentInteractionsTransientGainMayDuck_requestGrantedAndNoFocusLossSent() {
        assumeOEMServiceIsNotEnabled();
        // Test concurrent interactions i.e. interactions that can
        // potentially gain focus at the same time.
        // For this test permanent focus gain is requested by first usage and focus gain transient
        // may duck is requested by second usage.
        // The focus request will be granted for both but no focus lost is sent to the first focus
        // listener, as each usage actually has shared focus and  should play at the same time.
        testConcurrentInteractions(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, false);
        // Test the same behaviour but this time with pause for ducking on the first focus listener
        testConcurrentInteractions(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, true);
    }

    private void testConcurrentInteractions(int gain, boolean pauseForDucking) {
        // Test paired concurrent interactions i.e. interactions that can
        // potentially gain focus at the same time.
        int interaction = INTERACTION_CONCURRENT;
        testInteraction(ATTR_MEDIA, ATTR_NAVIGATION, interaction, gain, pauseForDucking);
        testInteraction(ATTR_MEDIA, ATTR_NOTIFICATION, interaction, gain, pauseForDucking);
        testInteraction(ATTR_MEDIA, ATTR_SYSTEM_SOUND, interaction, gain, pauseForDucking);

        testInteraction(ATTR_NAVIGATION, ATTR_MEDIA, interaction, gain, pauseForDucking);
        testInteraction(ATTR_NAVIGATION, ATTR_NAVIGATION, interaction, gain, pauseForDucking);
        testInteraction(ATTR_NAVIGATION, ATTR_CALL_RING, interaction, gain, pauseForDucking);
        testInteraction(ATTR_NAVIGATION, ATTR_ALARM, interaction, gain, pauseForDucking);
        testInteraction(ATTR_NAVIGATION, ATTR_NOTIFICATION, interaction, gain, pauseForDucking);
        testInteraction(ATTR_NAVIGATION, ATTR_SYSTEM_SOUND, interaction, gain, pauseForDucking);

        testInteraction(ATTR_VOICE_COMMAND, ATTR_MEDIA, interaction, gain, pauseForDucking);
        testInteraction(ATTR_VOICE_COMMAND, ATTR_VOICE_COMMAND, interaction, gain, pauseForDucking);

        testInteraction(ATTR_CALL_RING, ATTR_NAVIGATION, interaction, gain, pauseForDucking);
        testInteraction(ATTR_CALL_RING, ATTR_VOICE_COMMAND, interaction, gain, pauseForDucking);
        testInteraction(ATTR_CALL_RING, ATTR_CALL_RING, interaction, gain, pauseForDucking);
        testInteraction(ATTR_CALL_RING, ATTR_CALL, interaction, gain, pauseForDucking);

        testInteraction(ATTR_CALL, ATTR_NAVIGATION, interaction, gain, pauseForDucking);
        testInteraction(ATTR_CALL, ATTR_CALL_RING, interaction, gain, pauseForDucking);
        testInteraction(ATTR_CALL, ATTR_CALL, interaction, gain, pauseForDucking);
        testInteraction(ATTR_CALL, ATTR_ALARM, interaction, gain, pauseForDucking);
        testInteraction(ATTR_CALL, ATTR_NOTIFICATION, interaction, gain, pauseForDucking);

        testInteraction(ATTR_ALARM, ATTR_MEDIA, interaction, gain, pauseForDucking);
        testInteraction(ATTR_ALARM, ATTR_NAVIGATION, interaction, gain, pauseForDucking);
        testInteraction(ATTR_ALARM, ATTR_ALARM, interaction, gain, pauseForDucking);
        testInteraction(ATTR_ALARM, ATTR_NOTIFICATION, interaction, gain, pauseForDucking);
        testInteraction(ATTR_ALARM, ATTR_SYSTEM_SOUND, interaction, gain, pauseForDucking);

        testInteraction(ATTR_NOTIFICATION, ATTR_MEDIA, interaction, gain, pauseForDucking);
        testInteraction(ATTR_NOTIFICATION, ATTR_NAVIGATION, interaction, gain, pauseForDucking);
        testInteraction(ATTR_NOTIFICATION, ATTR_ALARM, interaction, gain, pauseForDucking);
        testInteraction(ATTR_NOTIFICATION, ATTR_NOTIFICATION, interaction, gain, pauseForDucking);
        testInteraction(ATTR_NOTIFICATION, ATTR_SYSTEM_SOUND, interaction, gain, pauseForDucking);

        testInteraction(ATTR_SYSTEM_SOUND, ATTR_MEDIA, interaction, gain, pauseForDucking);
        testInteraction(ATTR_SYSTEM_SOUND, ATTR_NAVIGATION, interaction, gain, pauseForDucking);
        testInteraction(ATTR_SYSTEM_SOUND, ATTR_ALARM, interaction, gain, pauseForDucking);
        testInteraction(ATTR_SYSTEM_SOUND, ATTR_NOTIFICATION, interaction, gain, pauseForDucking);
        testInteraction(ATTR_SYSTEM_SOUND, ATTR_SYSTEM_SOUND, interaction, gain, pauseForDucking);
    }

    private void testExclusiveInteractions(int gain, boolean pauseForDucking) {
        // Test exclusive interaction, interaction where each usage will not share focus with other
        // another usage. As a result once focus is gained any current focus listener
        // in this interaction will lose focus.
        int interaction = INTERACTION_EXCLUSIVE;

        testInteraction(ATTR_MEDIA, ATTR_MEDIA, interaction, gain, pauseForDucking);
        testInteraction(ATTR_MEDIA, ATTR_VOICE_COMMAND, interaction, gain, pauseForDucking);
        testInteraction(ATTR_MEDIA, ATTR_CALL_RING, interaction, gain, pauseForDucking);
        testInteraction(ATTR_MEDIA, ATTR_CALL, interaction, gain, pauseForDucking);
        testInteraction(ATTR_MEDIA, ATTR_ALARM, interaction, gain, pauseForDucking);

        testInteraction(ATTR_NAVIGATION, ATTR_VOICE_COMMAND, interaction, gain, pauseForDucking);
        testInteraction(ATTR_NAVIGATION, ATTR_CALL, interaction, gain, pauseForDucking);

        testInteraction(ATTR_VOICE_COMMAND, ATTR_CALL_RING, interaction, gain, pauseForDucking);
        testInteraction(ATTR_VOICE_COMMAND, ATTR_CALL, interaction, gain, pauseForDucking);

        testInteraction(ATTR_ALARM, ATTR_VOICE_COMMAND, interaction, gain, pauseForDucking);
        testInteraction(ATTR_ALARM, ATTR_CALL_RING, interaction, gain, pauseForDucking);
        testInteraction(ATTR_ALARM, ATTR_CALL, interaction, gain, pauseForDucking);

        testInteraction(ATTR_NOTIFICATION, ATTR_VOICE_COMMAND, interaction, gain, pauseForDucking);
        testInteraction(ATTR_NOTIFICATION, ATTR_CALL_RING, interaction, gain, pauseForDucking);
        testInteraction(ATTR_NOTIFICATION, ATTR_CALL, interaction, gain, pauseForDucking);

        testInteraction(ATTR_SYSTEM_SOUND, ATTR_VOICE_COMMAND, interaction, gain, pauseForDucking);
        testInteraction(ATTR_SYSTEM_SOUND, ATTR_CALL_RING, interaction, gain, pauseForDucking);
        testInteraction(ATTR_SYSTEM_SOUND, ATTR_CALL, interaction, gain, pauseForDucking);
    }


    /**
     * Test paired usage interactions with gainType and pause instead ducking
     *
     * @param attributes1     Attributes of the first usage (first focus requester) in the
     *                        interaction
     * @param attributes2     Attributes of the second usage (second focus requester) in the
     *                        interaction
     * @param interaction     type of interaction {@link INTERACTION_REJECT}, {@link
     *                        INTERACTION_EXCLUSIVE}, {@link INTERACTION_CONCURRENT}
     * @param gainType        Type of gain {@link AUDIOFOCUS_GAIN} , {@link
     *                        AUDIOFOCUS_GAIN_TRANSIENT}, {@link
     *                        AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK}
     * @param pauseForDucking flag to indicate if the first focus listener should pause instead of
     *                        ducking
     * @throws Exception
     */
    private void testInteraction(AudioAttributes attributes1, AudioAttributes attributes2,
            int interaction, int gainType, boolean pauseForDucking) {
        FocusChangeListener focusChangeListener1 = new FocusChangeListener();
        AudioFocusRequest audioFocusRequest1 = new AudioFocusRequest
                .Builder(AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes1)
                .setOnAudioFocusChangeListener(focusChangeListener1)
                .setForceDucking(false)
                .setWillPauseWhenDucked(pauseForDucking)
                .build();

        FocusChangeListener focusChangeListener2 = new FocusChangeListener();
        AudioFocusRequest audioFocusRequest2 = new AudioFocusRequest
                .Builder(gainType)
                .setAudioAttributes(attributes2)
                .setOnAudioFocusChangeListener(focusChangeListener2)
                .setForceDucking(false)
                .build();

        int expectedLoss = 0;

        // Each focus gain type will return a different focus lost type
        assertWithMessage("Unknown gain type")
                .that(gainType == AUDIOFOCUS_GAIN
                        || gainType == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                        || gainType == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).isTrue();
        switch (gainType) {
            case AUDIOFOCUS_GAIN:
                expectedLoss = AUDIOFOCUS_LOSS;
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                expectedLoss = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                expectedLoss = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
                // Note loss or gain will not be sent as both can live concurrently
                if (interaction == INTERACTION_CONCURRENT && !pauseForDucking) {
                    expectedLoss = AudioManager.AUDIOFOCUS_NONE;
                }
                break;
        }

        int secondRequestResultsExpected = AUDIOFOCUS_REQUEST_GRANTED;

        if (interaction == INTERACTION_REJECT) {
            secondRequestResultsExpected = AUDIOFOCUS_REQUEST_FAILED;
        }

        int requestResult = mAudioManager.requestAudioFocus(audioFocusRequest1);
        String message = "Focus gain request failed  for 1st "
                + usageToString(attributes1.getSystemUsage());
        assertWithMessage(message).that(requestResult).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
        mAudioFocusRequestsSet.add(audioFocusRequest1);

        requestResult = mAudioManager.requestAudioFocus(audioFocusRequest2);
        message = "Focus gain request failed for 2nd "
                + usageToString(attributes2.getSystemUsage());
        assertWithMessage(message).that(requestResult).isEqualTo(secondRequestResultsExpected);
        mAudioFocusRequestsSet.add(audioFocusRequest2);

        // If the results is rejected for second one we only have to clean up first
        // as the second focus request is rejected
        if (interaction == INTERACTION_REJECT) {
            requestResult = mAudioManager.abandonAudioFocusRequest(audioFocusRequest1);
            mAudioFocusRequestsSet.clear();
            message = "Focus loss request failed for 1st "
                    + usageToString(attributes1.getSystemUsage());
            assertWithMessage(message).that(requestResult).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
        }

        // If exclusive we expect to lose focus on 1st one
        // unless we have a concurrent interaction
        if (interaction == INTERACTION_EXCLUSIVE || interaction == INTERACTION_CONCURRENT) {
            message = "Focus change was not dispatched for 1st "
                    + usageToString(attributes1.getSystemUsage());
            boolean shouldStop = false;
            int counter = 0;
            while (!shouldStop && counter++ < TEST_TOLERANCE_MAX_ITERATIONS) {
                boolean gainedFocusLoss = focusChangeListener1.waitForFocusChangeAndAssertFocus(
                        TEST_TIMING_TOLERANCE_MS, expectedLoss, message);
                shouldStop = gainedFocusLoss
                        || (expectedLoss == AudioManager.AUDIOFOCUS_NONE);
            }
            message += " with expected loss";
            assertWithMessage(message).that(shouldStop).isTrue();
            focusChangeListener1.resetFocusChangeAndWait();

            if (expectedLoss == AUDIOFOCUS_LOSS) {
                mAudioFocusRequestsSet.remove(audioFocusRequest1);
            }
            requestResult = mAudioManager.abandonAudioFocusRequest(audioFocusRequest2);
            mAudioFocusRequestsSet.remove(audioFocusRequest2);
            message = "Focus loss request failed  for 2nd "
                    + usageToString(attributes2.getSystemUsage());
            assertWithMessage(message).that(requestResult).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);


            // If the loss was transient then we should have received back on 1st
            if ((gainType == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                    || gainType == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)) {

                // Since ducking and concurrent can exist together
                // this needs to be skipped as the focus lost is not sent
                if (!(interaction == INTERACTION_CONCURRENT
                        && gainType == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                        && !pauseForDucking)) {
                    message = "Focus change was not dispatched for 1st "
                            + usageToString(attributes1.getSystemUsage());

                    boolean focusGained = false;
                    int count = 0;
                    while (!focusGained && count++ < TEST_TOLERANCE_MAX_ITERATIONS) {
                        focusGained = focusChangeListener1.waitForFocusChangeAndAssertFocus(
                                TEST_TIMING_TOLERANCE_MS,
                                AUDIOFOCUS_GAIN, message);
                    }
                    message = "Focus for 1st request "
                            + usageToString(attributes1.getSystemUsage())
                            + " does not gain focus back";
                    assertWithMessage(message).that(focusGained).isTrue();
                    focusChangeListener1.resetFocusChangeAndWait();
                }
                // For concurrent focus interactions still needs to be released
                message = "Focus loss request failed  for 1st  "
                        + usageToString(attributes1.getSystemUsage());
                requestResult = mAudioManager.abandonAudioFocusRequest(audioFocusRequest1);
                mAudioFocusRequestsSet.remove(audioFocusRequest1);
                assertWithMessage(message).that(requestResult)
                        .isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
            }
        }
    }

    /**
     * Verifies usage can request audio focus and release it
     *
     * @param attribute usage attribute to request focus
     */
    private void requestAndLoseFocusForAttribute(AudioAttributes attribute) {
        FocusChangeListener focusChangeListener = new FocusChangeListener();
        AudioFocusRequest audioFocusRequest = new AudioFocusRequest
                .Builder(AUDIOFOCUS_GAIN)
                .setAudioAttributes(attribute)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .setForceDucking(false)
                .build();


        int requestResult = mAudioManager.requestAudioFocus(audioFocusRequest);
        String message = "Focus gain request failed  for "
                + usageToString(attribute.getSystemUsage());
        assertWithMessage(message).that(requestResult).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
        mAudioFocusRequestsSet.add(audioFocusRequest);

        // Verify no focus changed dispatched
        message = "Focus change was dispatched for "
                + usageToString(attribute.getSystemUsage());

        assertWithMessage(message + " within time tolerance")
                .that(focusChangeListener.waitForFocusChangeAndAssertFocus(
                        TEST_TIMING_TOLERANCE_MS, AudioManager.AUDIOFOCUS_NONE, message)).isFalse();
        focusChangeListener.resetFocusChangeAndWait();

        requestResult = mAudioManager.abandonAudioFocusRequest(audioFocusRequest);
        message = "Focus loss request failed  for "
                + usageToString(attribute.getSystemUsage());
        assertWithMessage(message).that(requestResult).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
        mAudioFocusRequestsSet.remove(audioFocusRequest);
    }

    private static AudioFocusRequest.Builder delayedFocusRequestBuilder() {
        AudioManager.OnAudioFocusChangeListener listener = new FocusChangeListener();
        return new AudioFocusRequest.Builder(AUDIOFOCUS_GAIN)
                .setAcceptsDelayedFocusGain(true).setAudioAttributes(ATTR_MEDIA)
                .setOnAudioFocusChangeListener(listener);
    }

    private static AudioFocusRequest.Builder phoneFocusRequestBuilder() {
        AudioManager.OnAudioFocusChangeListener listener = new FocusChangeListener();
        return new AudioFocusRequest.Builder(AUDIOFOCUS_GAIN)
                .setAudioAttributes(ATTR_CALL)
                .setOnAudioFocusChangeListener(listener);
    }

    private static AudioFocusRequest.Builder assistantFocusRequestBuilder() {
        AudioManager.OnAudioFocusChangeListener listener = new FocusChangeListener();
        return new AudioFocusRequest.Builder(AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(ATTR_VOICE_COMMAND)
                .setOnAudioFocusChangeListener(listener);
    }

    private static AudioFocusRequest.Builder navigationFocusRequestBuilder() {
        AudioManager.OnAudioFocusChangeListener listener = new FocusChangeListener();
        return new AudioFocusRequest.Builder(AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(ATTR_NAVIGATION)
                .setOnAudioFocusChangeListener(listener);
    }

    private static final class FocusChangeListener
            implements AudioManager.OnAudioFocusChangeListener {
        private final Semaphore mChangeEventSignal = new Semaphore(0);
        private int mFocusChange = AudioManager.AUDIOFOCUS_NONE;

        private boolean waitForFocusChangeAndAssertFocus(long timeoutMs, int expectedFocus,
                String message) {
            boolean acquired = false;
            try {
                acquired = mChangeEventSignal.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {

            }
            if (acquired) {
                assertWithMessage(message).that(mFocusChange).isEqualTo(expectedFocus);
            }
            return acquired;
        }

        private void resetFocusChangeAndWait() {
            mFocusChange = AudioManager.AUDIOFOCUS_NONE;
            mChangeEventSignal.drainPermits();
        }

        @Override
        public void onAudioFocusChange(int focusChange) {
            // should be dispatched to main thread.
            assertWithMessage("Focus should be dispatched to main thread")
                    .that(Looper.myLooper()).isEqualTo(Looper.getMainLooper());
            mFocusChange = focusChange;
            mChangeEventSignal.release();
        }
    }

    private static String usageToString(int usage) {
        switch(usage) {
            case AudioAttributes.USAGE_UNKNOWN:
                return "USAGE_UNKNOWN";
            case AudioAttributes.USAGE_MEDIA:
                return "USAGE_MEDIA";
            case AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE:
                return "USAGE_ASSISTANCE_NAVIGATION_GUIDANCE";
            case AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY:
                return "USAGE_ASSISTANCE_ACCESSIBILITY";
            case AudioAttributes.USAGE_NOTIFICATION_RINGTONE:
                return "USAGE_NOTIFICATION_RINGTONE";
            case AudioAttributes.USAGE_VOICE_COMMUNICATION:
                return "USAGE_VOICE_COMMUNICATION";
            case AudioAttributes.USAGE_ALARM:
                return "USAGE_ALARM";
            case AudioAttributes.USAGE_NOTIFICATION:
                return "USAGE_NOTIFICATION";
            case AudioAttributes.USAGE_ASSISTANCE_SONIFICATION:
                return "USAGE_ASSISTANCE_SONIFICATION";
            default:
                return "usage not tested " + usage;
        }
    }

    private void assumeDynamicRoutingIsEnabled() {
        assumeTrue("Dynamic routing is disabled.", isDynamicRoutingEnabled());
    }

    private void assumeOEMServiceIsNotEnabled() {
        assumeFalse("Oem service is enabled",
                mCarAudioManager.isAudioFeatureEnabled(
                        CarAudioManager.AUDIO_FEATURE_OEM_AUDIO_SERVICE));
    }

    private boolean isDynamicRoutingEnabled() {
        return mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_DYNAMIC_ROUTING);
    }
}
