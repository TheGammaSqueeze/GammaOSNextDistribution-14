/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.car.audio;

import static android.media.AudioAttributes.USAGE_ALARM;
import static android.media.AudioAttributes.USAGE_ANNOUNCEMENT;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.AudioAttributes.USAGE_ASSISTANT;
import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioAttributes.USAGE_NOTIFICATION;
import static android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE;
import static android.media.AudioAttributes.USAGE_VIRTUAL_SOURCE;
import static android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION;
import static android.telephony.TelephonyManager.CALL_STATE_IDLE;
import static android.telephony.TelephonyManager.CALL_STATE_OFFHOOK;
import static android.telephony.TelephonyManager.CALL_STATE_RINGING;

import static com.android.car.audio.CarAudioService.CAR_DEFAULT_AUDIO_ATTRIBUTE;
import static com.android.car.audio.CarAudioService.SystemClockWrapper;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import android.media.AudioAttributes;

import com.android.car.audio.CarAudioContext.AudioContext;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class CarVolumeTest {

    private static final @CarVolume.CarVolumeListVersion int VERSION_ZERO = 0;
    private static final @CarVolume.CarVolumeListVersion int VERSION_ONE = 1;
    private static final @CarVolume.CarVolumeListVersion int VERSION_TWO = 2;
    private static final @CarVolume.CarVolumeListVersion int VERSION_THREE = 3;
    private static final long START_TIME = 10000;
    private static final long START_TIME_ONE_SECOND = 11000;
    private static final long START_TIME_FOUR_SECOND = 14000;
    private static final int KEY_EVENT_TIMEOUT_MS = 3000;
    private static final int TRIAL_COUNTS = 10;

    public static final CarAudioContext TEST_CAR_AUDIO_CONTEXT =
            new CarAudioContext(CarAudioContext.getAllContextsInfo(),
                    /* useCoreAudioRouting= */ false);

    public static final AudioAttributes TEST_ASSISTANT_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_ASSISTANT);
    public static final AudioAttributes TEST_ALARM_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_ALARM);
    public static final AudioAttributes TEST_CALL_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_VOICE_COMMUNICATION);
    public static final AudioAttributes TEST_CALL_RING_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_NOTIFICATION_RINGTONE);
    public static final AudioAttributes TEST_NAVIGATION_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);
    public static final AudioAttributes TEST_MEDIA_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA);
    public static final AudioAttributes TEST_NOTIFICATION_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_NOTIFICATION);
    public static final AudioAttributes TEST_ANNOUNCEMENT_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_ANNOUNCEMENT);

    @Mock
    private SystemClockWrapper mMockClock;

    private CarVolume mCarVolume;

    @Before
    public void setUp() throws Exception {
        when(mMockClock.uptimeMillis()).thenReturn(START_TIME);
        mCarVolume = new CarVolume(TEST_CAR_AUDIO_CONTEXT, mMockClock,
                VERSION_TWO, KEY_EVENT_TIMEOUT_MS);

    }

    @Test
    public void constructor_withVersionLessThanOne_failsTooLow() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            new CarVolume(TEST_CAR_AUDIO_CONTEXT, mMockClock, VERSION_ZERO, KEY_EVENT_TIMEOUT_MS);
        });

        assertWithMessage("Constructor Exception")
                .that(thrown).hasMessageThat().contains("too low");
    }

    @Test
    public void constructor_withVersionGreaterThanTwo_failsTooHigh() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            new CarVolume(TEST_CAR_AUDIO_CONTEXT, mMockClock, VERSION_THREE, KEY_EVENT_TIMEOUT_MS);
        });

        assertWithMessage("Constructor Exception")
                .that(thrown).hasMessageThat().contains("too high");
    }

    @Test
    public void constructor_withNullSystemClock_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> {
            new CarVolume(TEST_CAR_AUDIO_CONTEXT, null, VERSION_ONE, KEY_EVENT_TIMEOUT_MS);
        });

        assertWithMessage("Constructor Exception")
                .that(thrown).hasMessageThat().contains("Clock");
    }

    @Test
    public void constructor_withNullCarAudioContext_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> {
            new CarVolume(null, mMockClock, VERSION_ONE, KEY_EVENT_TIMEOUT_MS);
        });

        assertWithMessage("Constructor with null car audio context exception")
                .that(thrown).hasMessageThat().contains("Car audio context");
    }

    @Test
    public void getSuggestedAudioContext_withNullActivePlayback_fails() {
        assertThrows(NullPointerException.class,
                () -> mCarVolume.getSuggestedAudioContextAndSaveIfFound(
                null, CALL_STATE_IDLE, new ArrayList<>()));
    }

    @Test
    public void getSuggestedAudioContext_withNullHallAttributes_fails() {
        assertThrows(NullPointerException.class,
                () -> mCarVolume.getSuggestedAudioContextAndSaveIfFound(
                new ArrayList<>(), CALL_STATE_IDLE, null));
    }

    @Test
    public void getSuggestedAudioContext_withNoActivePlaybackAndIdleTelephony_returnsDefault() {
        @AudioContext int suggestedContext =
                mCarVolume.getSuggestedAudioContextAndSaveIfFound(new ArrayList<>(),
                CALL_STATE_IDLE, new ArrayList<>());

        assertThat(suggestedContext).isEqualTo(TEST_CAR_AUDIO_CONTEXT
                .getContextForAudioAttribute(CAR_DEFAULT_AUDIO_ATTRIBUTE));
    }

    @Test
    public void getSuggestedAudioContext_withOneConfiguration_returnsAssociatedContext() {
        List<AudioAttributes> activePlaybackAttributes =
                ImmutableList.of(TEST_ASSISTANT_ATTRIBUTE);

        @AudioContext int suggestedContext = mCarVolume
                .getSuggestedAudioContextAndSaveIfFound(activePlaybackAttributes, CALL_STATE_IDLE,
                        new ArrayList<>());

        assertThat(suggestedContext).isEqualTo(TEST_CAR_AUDIO_CONTEXT
                .getContextForAttributes(TEST_ASSISTANT_ATTRIBUTE));
    }

    @Test
    public void getSuggestedAudioContext_withCallStateOffHook_returnsCallContext() {
        @AudioContext int suggestedContext =
                mCarVolume.getSuggestedAudioContextAndSaveIfFound(new ArrayList<>(),
                        CALL_STATE_OFFHOOK, new ArrayList<>());

        assertThat(suggestedContext).isEqualTo(TEST_CAR_AUDIO_CONTEXT
                .getContextForAudioAttribute(TEST_CALL_ATTRIBUTE));
    }

    @Test

    public void getSuggestedAudioContext_withV1AndCallStateRinging_returnsCallRingContext() {
        CarVolume carVolume = new CarVolume(TEST_CAR_AUDIO_CONTEXT, mMockClock,
                VERSION_ONE, KEY_EVENT_TIMEOUT_MS);

        @AudioContext int suggestedContext =
                carVolume.getSuggestedAudioContextAndSaveIfFound(new ArrayList<>(),
                CALL_STATE_RINGING, new ArrayList<>());

        assertThat(suggestedContext).isEqualTo(TEST_CAR_AUDIO_CONTEXT
                .getContextForAudioAttribute(TEST_CALL_RING_ATTRIBUTE));
    }

    @Test
    public void getSuggestedAudioContext_withActivePlayback_returnsHighestPriorityContext() {
        List<AudioAttributes> activePlaybackAttributes =
                ImmutableList.of(TEST_ALARM_ATTRIBUTE, TEST_CALL_ATTRIBUTE,
                        TEST_NOTIFICATION_ATTRIBUTE);

        @AudioContext int suggestedContext = mCarVolume
                .getSuggestedAudioContextAndSaveIfFound(activePlaybackAttributes, CALL_STATE_IDLE,
                        new ArrayList<>());

        assertThat(suggestedContext).isEqualTo(TEST_CAR_AUDIO_CONTEXT
                .getContextForAudioAttribute(TEST_CALL_ATTRIBUTE));
    }

    @Test
    public void getSuggestedAudioContext_withLowerPriorityActivePlaybackAndCall_returnsCall() {
        List<AudioAttributes> activePlaybackAttributes =
                ImmutableList.of(TEST_ALARM_ATTRIBUTE,
                        CarAudioContext.getAudioAttributeFromUsage(USAGE_NOTIFICATION));

        @AudioContext int suggestedContext = mCarVolume
                .getSuggestedAudioContextAndSaveIfFound(activePlaybackAttributes,
                        CALL_STATE_OFFHOOK, new ArrayList<>());

        assertThat(suggestedContext).isEqualTo(TEST_CAR_AUDIO_CONTEXT
                .getContextForAudioAttribute(TEST_CALL_ATTRIBUTE));
    }

    @Test
    public void getSuggestedAudioContext_withV1AndNavigationConfigurationAndCall_returnsNav() {
        CarVolume carVolume = new CarVolume(TEST_CAR_AUDIO_CONTEXT, mMockClock,
                VERSION_ONE, KEY_EVENT_TIMEOUT_MS);
        List<AudioAttributes> activePlaybackAttributes = ImmutableList.of(CarAudioContext
                .getAudioAttributeFromUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE));

        @AudioContext int suggestedContext = carVolume
                .getSuggestedAudioContextAndSaveIfFound(activePlaybackAttributes,
                        CALL_STATE_OFFHOOK, new ArrayList<>());

        assertThat(suggestedContext).isEqualTo(TEST_CAR_AUDIO_CONTEXT
                .getContextForAudioAttribute(TEST_NAVIGATION_ATTRIBUTE));
    }

    @Test
    public void getSuggestedAudioContext_withV2AndNavigationConfigurationAndCall_returnsCall() {
        List<AudioAttributes> activePlaybackAttributes = ImmutableList.of(CarAudioContext
                .getAudioAttributeFromUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE));

        @AudioContext int suggestedContext = mCarVolume
                .getSuggestedAudioContextAndSaveIfFound(activePlaybackAttributes,
                        CALL_STATE_OFFHOOK, new ArrayList<>());

        assertThat(suggestedContext).isEqualTo(TEST_CAR_AUDIO_CONTEXT
                .getContextForAudioAttribute(TEST_CALL_ATTRIBUTE));
    }

    @Test
    public void getSuggestedAudioContext_withUnprioritizedAttribute_returnsDefault() {
        List<AudioAttributes> activePlaybackAttributes = ImmutableList.of(CarAudioContext
                        .getAudioAttributeFromUsage(CarAudioContext.getInvalidContext()));

        @AudioContext int suggestedContext = mCarVolume
                .getSuggestedAudioContextAndSaveIfFound(activePlaybackAttributes,
                        CALL_STATE_IDLE, new ArrayList<>());

        assertThat(suggestedContext).isEqualTo(TEST_CAR_AUDIO_CONTEXT
                .getContextForAudioAttribute(CAR_DEFAULT_AUDIO_ATTRIBUTE));
    }

    @Test
    public void getSuggestedAudioContext_withHalActiveAttribute_returnsHalActive() {
        List<AudioAttributes> activeHalAudioAttributes = new ArrayList<>(1);
        activeHalAudioAttributes.add(TEST_ASSISTANT_ATTRIBUTE);

        @AudioContext int suggestedContext =
                mCarVolume.getSuggestedAudioContextAndSaveIfFound(new ArrayList<>(),
                CALL_STATE_IDLE, activeHalAudioAttributes);

        assertThat(suggestedContext).isEqualTo(
                TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_ASSISTANT_ATTRIBUTE));
    }

    @Test
    public void getSuggestedAudioContext_withHalUnprioritizedAttribute_returnsDefault() {
        List<AudioAttributes> activeHalAudioAttributes = new ArrayList<>(1);
        activeHalAudioAttributes.add(CarAudioContext
                .getAudioAttributeFromUsage(USAGE_VIRTUAL_SOURCE));

        @AudioContext int suggestedContext =
                mCarVolume.getSuggestedAudioContextAndSaveIfFound(new ArrayList<>(),
                CALL_STATE_IDLE, activeHalAudioAttributes);

        assertThat(suggestedContext).isEqualTo(TEST_CAR_AUDIO_CONTEXT
                .getContextForAudioAttribute(CAR_DEFAULT_AUDIO_ATTRIBUTE));
    }

    @Test
    public void getSuggestedAudioContext_withConfigAndHalActiveAttribute_returnsConfigActive() {
        List<AudioAttributes> activeHalAudioAttributes =
                ImmutableList.of(TEST_ASSISTANT_ATTRIBUTE);
        List<AudioAttributes> activePlaybackAttributes = ImmutableList.of(TEST_MEDIA_ATTRIBUTE);

        @AudioContext int suggestedContext = mCarVolume
                .getSuggestedAudioContextAndSaveIfFound(activePlaybackAttributes,
                        CALL_STATE_IDLE, activeHalAudioAttributes);

        assertThat(suggestedContext).isEqualTo(
                TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_MEDIA_ATTRIBUTE));
    }

    @Test
    public void getSuggestedAudioContext_withConfigAndHalActiveAttribute_returnsHalActive() {
        List<AudioAttributes> activeHalAudioAttributes = ImmutableList.of(TEST_MEDIA_ATTRIBUTE);
        List<AudioAttributes> activePlaybackAttributes =
                ImmutableList.of(TEST_ASSISTANT_ATTRIBUTE);

        @AudioContext int suggestedContext = mCarVolume
                .getSuggestedAudioContextAndSaveIfFound(activePlaybackAttributes,
                        CALL_STATE_IDLE, activeHalAudioAttributes);

        assertThat(suggestedContext).isEqualTo(
                TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_MEDIA_ATTRIBUTE));
    }

    @Test
    public void getSuggestedAudioContext_withHalActiveAttributeAndActiveCall_returnsCall() {
        List<AudioAttributes> activeHalAudioAttributes = ImmutableList.of(TEST_MEDIA_ATTRIBUTE);
        List<AudioAttributes> activePlaybackAttributes = ImmutableList.of();

        @AudioContext int suggestedContext = mCarVolume.getSuggestedAudioContextAndSaveIfFound(
                activePlaybackAttributes, CALL_STATE_OFFHOOK, activeHalAudioAttributes);

        assertThat(suggestedContext).isEqualTo(TEST_CAR_AUDIO_CONTEXT
                .getContextForAudioAttribute(TEST_CALL_ATTRIBUTE));
    }

    @Test
    public void getSuggestedAudioContext_withMultipleHalActiveAttributes_returnsMusic() {
        List<AudioAttributes> activeHalAudioAttributes =
                ImmutableList.of(TEST_MEDIA_ATTRIBUTE,
                TEST_ANNOUNCEMENT_ATTRIBUTE, TEST_ASSISTANT_ATTRIBUTE);

        @AudioContext int suggestedContext = mCarVolume
                .getSuggestedAudioContextAndSaveIfFound(Collections.EMPTY_LIST,
                        CALL_STATE_IDLE, activeHalAudioAttributes);

        assertThat(suggestedContext).isEqualTo(
                TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_MEDIA_ATTRIBUTE));
    }

    @Test
    public void getSuggestedAudioContext_withStillActiveContext_returnsPrevActiveContext() {
        List<AudioAttributes> activePlaybackAttributes =
                ImmutableList.of(TEST_ASSISTANT_ATTRIBUTE);

        mCarVolume.getSuggestedAudioContextAndSaveIfFound(activePlaybackAttributes,
                CALL_STATE_IDLE, new ArrayList<>(/* initialCapacity= */ 0));

        when(mMockClock.uptimeMillis()).thenReturn(START_TIME_ONE_SECOND);

        @AudioContext int suggestedContext =
                mCarVolume.getSuggestedAudioContextAndSaveIfFound(new ArrayList<>(),
                CALL_STATE_IDLE, new ArrayList<>(/* initialCapacity= */ 0));

        assertThat(suggestedContext).isEqualTo(
                TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_ASSISTANT_ATTRIBUTE));
    }

    @Test
    public void
            getSuggestedAudioContext_withStillActiveContext_retPrevActiveContextMultipleTimes() {
        List<AudioAttributes> activePlaybackAttributes =
                ImmutableList.of(TEST_ASSISTANT_ATTRIBUTE);

        mCarVolume.getSuggestedAudioContextAndSaveIfFound(activePlaybackAttributes,
                CALL_STATE_IDLE, new ArrayList<>(/* initialCapacity= */ 0));

        long deltaTime = KEY_EVENT_TIMEOUT_MS - 1;
        for (int volumeCounter = 1; volumeCounter < TRIAL_COUNTS; volumeCounter++) {
            when(mMockClock.uptimeMillis())
                    .thenReturn(START_TIME + (volumeCounter * deltaTime));

            @AudioContext int suggestedContext =
                    mCarVolume.getSuggestedAudioContextAndSaveIfFound(new ArrayList<>(),
                            CALL_STATE_IDLE, new ArrayList<>(/* initialCapacity= */ 0));
            assertThat(suggestedContext).isEqualTo(
                    TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_ASSISTANT_ATTRIBUTE));
        }
    }

    @Test
    public void
            getSuggestedAudioContext_withActContextAndNewHigherPrioContext_returnPrevActContext() {
        List<AudioAttributes> activePlaybackAttributes =
                ImmutableList.of(TEST_ASSISTANT_ATTRIBUTE);

        mCarVolume.getSuggestedAudioContextAndSaveIfFound(activePlaybackAttributes,
                CALL_STATE_IDLE, new ArrayList<>(/* initialCapacity= */ 0));

        when(mMockClock.uptimeMillis()).thenReturn(START_TIME_ONE_SECOND);

        @AudioContext int suggestedContext =
                mCarVolume.getSuggestedAudioContextAndSaveIfFound(new ArrayList<>(),
                CALL_STATE_OFFHOOK, new ArrayList<>(/* initialCapacity= */ 0));

        assertThat(suggestedContext).isEqualTo(
                TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_ASSISTANT_ATTRIBUTE));
    }

    @Test
    public void getSuggestedAudioContext_afterActiveContextTimeout_returnsDefaultContext() {
        List<AudioAttributes> activePlaybackAttributes =
                ImmutableList.of(TEST_ASSISTANT_ATTRIBUTE);

        mCarVolume.getSuggestedAudioContextAndSaveIfFound(activePlaybackAttributes,
                CALL_STATE_IDLE, new ArrayList<>(/* initialCapacity= */ 0));

        when(mMockClock.uptimeMillis()).thenReturn(START_TIME_FOUR_SECOND);

        @AudioContext int suggestedContext =
                mCarVolume.getSuggestedAudioContextAndSaveIfFound(new ArrayList<>(),
                CALL_STATE_IDLE, new ArrayList<>(/* initialCapacity= */ 0));

        assertThat(suggestedContext).isEqualTo(TEST_CAR_AUDIO_CONTEXT
                .getContextForAudioAttribute(CAR_DEFAULT_AUDIO_ATTRIBUTE));
    }

    @Test
    public void
            getSuggestedAudioContext_afterActiveContextTimeoutAndNewContext_returnsNewContext() {
        List<AudioAttributes> activePlaybackAttributes =
                ImmutableList.of(TEST_ASSISTANT_ATTRIBUTE);

        mCarVolume.getSuggestedAudioContextAndSaveIfFound(activePlaybackAttributes,
                CALL_STATE_IDLE, new ArrayList<>(/* initialCapacity= */ 0));

        when(mMockClock.uptimeMillis()).thenReturn(START_TIME_FOUR_SECOND);

        @AudioContext int suggestedContext =
                mCarVolume.getSuggestedAudioContextAndSaveIfFound(new ArrayList<>(),
                CALL_STATE_OFFHOOK, new ArrayList<>(/* initialCapacity= */ 0));

        assertThat(suggestedContext).isEqualTo(TEST_CAR_AUDIO_CONTEXT
                .getContextForAudioAttribute(TEST_CALL_ATTRIBUTE));
    }

    @Test
    public void
            getSuggestedAudioContext_afterMultipleQueriesAndNewContextCall_returnsNewContext() {
        List<AudioAttributes> activePlaybackAttributes =
                ImmutableList.of(TEST_ASSISTANT_ATTRIBUTE);

        mCarVolume.getSuggestedAudioContextAndSaveIfFound(activePlaybackAttributes,
                CALL_STATE_IDLE, new ArrayList<>(/* initialCapacity= */ 0));

        long deltaTime = KEY_EVENT_TIMEOUT_MS - 1;

        for (int volumeCounter = 1; volumeCounter < TRIAL_COUNTS; volumeCounter++) {
            when(mMockClock.uptimeMillis()).thenReturn(START_TIME + volumeCounter * deltaTime);

            mCarVolume.getSuggestedAudioContextAndSaveIfFound(new ArrayList<>(), CALL_STATE_IDLE,
                    new ArrayList<>(/* initialCapacity= */ 0));
        }

        when(mMockClock.uptimeMillis())
                .thenReturn(START_TIME + (TRIAL_COUNTS * deltaTime) + KEY_EVENT_TIMEOUT_MS);

        @AudioContext int newContext =
                mCarVolume.getSuggestedAudioContextAndSaveIfFound(new ArrayList<>(),
                CALL_STATE_OFFHOOK, new ArrayList<>(/* initialCapacity= */ 0));

        assertThat(newContext).isEqualTo(TEST_CAR_AUDIO_CONTEXT
                .getContextForAudioAttribute(TEST_CALL_ATTRIBUTE));
    }

    @Test
    public void getSuggestedAudioContext_afterResetSelectedVolumeContext_returnsDefaultContext() {
        List<AudioAttributes> activePlaybackAttributes =
                ImmutableList.of(TEST_ASSISTANT_ATTRIBUTE);

        mCarVolume.getSuggestedAudioContextAndSaveIfFound(activePlaybackAttributes,
                CALL_STATE_IDLE, new ArrayList<>(/* initialCapacity= */ 0));

        when(mMockClock.uptimeMillis()).thenReturn(START_TIME_ONE_SECOND);

        mCarVolume.resetSelectedVolumeContext();

        @AudioContext int suggestedContext =
                mCarVolume.getSuggestedAudioContextAndSaveIfFound(Collections.EMPTY_LIST,
                        CALL_STATE_IDLE, Collections.EMPTY_LIST);

        assertThat(suggestedContext).isEqualTo(TEST_CAR_AUDIO_CONTEXT
                .getContextForAudioAttribute(CAR_DEFAULT_AUDIO_ATTRIBUTE));
    }


    @Test
    public void isAnyContextActive_withOneConfigurationAndMatchedContext_returnsTrue() {
        @AudioContext int[] activeContexts =
                {TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_ASSISTANT_ATTRIBUTE)};
        List<AudioAttributes> activePlaybackAttributes =
                ImmutableList.of(TEST_ASSISTANT_ATTRIBUTE);

        assertThat(mCarVolume.isAnyContextActive(activeContexts, activePlaybackAttributes,
                CALL_STATE_IDLE, new ArrayList<>(/* initialCapacity= */ 0))).isTrue();
    }

    @Test
    public void isAnyContextActive_withOneConfigurationAndMismatchedContext_returnsFalse() {
        @AudioContext int[] activeContexts =
                {TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_ALARM_ATTRIBUTE)};
        List<AudioAttributes> activePlaybackAttributes =
                ImmutableList.of(TEST_ASSISTANT_ATTRIBUTE);

        assertThat(mCarVolume.isAnyContextActive(activeContexts, activePlaybackAttributes,
                CALL_STATE_IDLE, new ArrayList<>(/* initialCapacity= */ 0))).isFalse();
    }

    @Test
    public void isAnyContextActive_withOneConfigurationAndMultipleContexts_returnsTrue() {
        @AudioContext int[] activeContexts =
                {TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_ALARM_ATTRIBUTE),
                        TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_MEDIA_ATTRIBUTE),
                        TEST_CAR_AUDIO_CONTEXT
                                 .getContextForAudioAttribute(TEST_ASSISTANT_ATTRIBUTE)};
        List<AudioAttributes> activePlaybackAttributes =
                ImmutableList.of(TEST_ASSISTANT_ATTRIBUTE);

        assertThat(mCarVolume.isAnyContextActive(activeContexts, activePlaybackAttributes,
                CALL_STATE_IDLE, new ArrayList<>(/* initialCapacity= */ 0))).isTrue();
    }

    @Test
    public void isAnyContextActive_withOneConfigurationAndMultipleContexts_returnsFalse() {
        @AudioContext int[] activeContexts = {
                TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_ALARM_ATTRIBUTE),
                TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_MEDIA_ATTRIBUTE),
                TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_ASSISTANT_ATTRIBUTE)
        };
        List<AudioAttributes> activePlaybackAttributes =
                ImmutableList.of(CarAudioContext.getAudioAttributeFromUsage(USAGE_NOTIFICATION));

        assertThat(mCarVolume.isAnyContextActive(activeContexts, activePlaybackAttributes,
                CALL_STATE_IDLE, new ArrayList<>(/* initialCapacity= */ 0))).isFalse();
    }

    @Test
    public void isAnyContextActive_withactiveHalAudioAttributesAndMatchedContext_returnsTrue() {
        @AudioContext int[] activeContexts =
                {TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_ASSISTANT_ATTRIBUTE)};
        List<AudioAttributes> activeHalAudioAttributes =
                ImmutableList.of(TEST_MEDIA_ATTRIBUTE, TEST_ANNOUNCEMENT_ATTRIBUTE,
                        TEST_ASSISTANT_ATTRIBUTE);

        assertThat(mCarVolume.isAnyContextActive(activeContexts,  Collections.EMPTY_LIST,
                CALL_STATE_IDLE, activeHalAudioAttributes)).isTrue();
    }

    @Test
    public void
            isAnyContextActive_withactiveHalAudioAttributesAndMismatchedContext_returnsFalse() {
        @AudioContext int[] activeContexts =
                {TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_ALARM_ATTRIBUTE)};
        List<AudioAttributes> activeHalAudioAttributes =
                ImmutableList.of(TEST_MEDIA_ATTRIBUTE, TEST_ANNOUNCEMENT_ATTRIBUTE,
                        TEST_ASSISTANT_ATTRIBUTE);

        assertThat(mCarVolume.isAnyContextActive(activeContexts, Collections.EMPTY_LIST,
                CALL_STATE_IDLE, activeHalAudioAttributes)).isFalse();
    }

    @Test
    public void isAnyContextActive_withActiveCallAndMatchedContext_returnsTrue() {
        @AudioContext int[] activeContexts =
                {TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_CALL_ATTRIBUTE)};

        assertThat(mCarVolume.isAnyContextActive(activeContexts, Collections.EMPTY_LIST,
                CALL_STATE_OFFHOOK, new ArrayList<>(/* initialCapacity= */ 0))).isTrue();
    }

    @Test
    public void isAnyContextActive_withActiveCallAndMismatchedContext_returnsFalse() {
        @AudioContext int[] activeContexts =
                {TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_ASSISTANT_ATTRIBUTE)};

        assertThat(mCarVolume.isAnyContextActive(activeContexts, Collections.EMPTY_LIST,
                CALL_STATE_OFFHOOK, new ArrayList<>(/* initialCapacity= */ 0))).isFalse();
    }

    @Test
    public void isAnyContextActive_withNullContexts_fails() {
        @AudioContext int[] activeContexts = null;

        assertThrows(NullPointerException.class,
                () -> mCarVolume.isAnyContextActive(activeContexts,
                        Collections.EMPTY_LIST, CALL_STATE_OFFHOOK, Collections.EMPTY_LIST));
    }

    @Test
    public void isAnyContextActive_withEmptyContexts_fails() {
        @AudioContext int[] activeContexts = {};

        assertThrows(IllegalArgumentException.class,
                () -> mCarVolume.isAnyContextActive(activeContexts,
                        Collections.EMPTY_LIST, CALL_STATE_OFFHOOK, Collections.EMPTY_LIST));
    }

    @Test
    public void isAnyContextActive_withNullActivePlayback_fails() {
        @AudioContext int[] activeContexts =
                {TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_ALARM_ATTRIBUTE)};
        List<AudioAttributes> activePlaybackAttributes = null;

        assertThrows(NullPointerException.class,
                () -> mCarVolume.isAnyContextActive(activeContexts,
                        activePlaybackAttributes, CALL_STATE_OFFHOOK,
                        Collections.EMPTY_LIST));
    }

    @Test
    public void isAnyContextActive_withNullHalUsages_fails() {
        @AudioContext int[] activeContexts =
                {TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_ALARM_ATTRIBUTE)};

        assertThrows(NullPointerException.class,
                () -> mCarVolume.isAnyContextActive(activeContexts,
                        Collections.EMPTY_LIST, CALL_STATE_OFFHOOK, null));
    }
}
