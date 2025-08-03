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

package com.android.car.audio;

import static android.media.AudioManager.AUDIOFOCUS_GAIN;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.media.AudioFocusInfo;
import android.media.AudioManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public final class CarZonesAudioFocusConcurrentTest extends CarZonesAudioFocusTestBase {
    @Rule
    public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    private final AudioClientInfo mExcludedAudioClientInfo;
    private final AudioClientInfo mAcceptedAudioClientInfo;

    public CarZonesAudioFocusConcurrentTest(AudioClientInfo excludedAudioClientInfo,
            AudioClientInfo acceptedAudioClientInfo) {
        mExcludedAudioClientInfo = excludedAudioClientInfo;
        mAcceptedAudioClientInfo = acceptedAudioClientInfo;
    }

    @Parameterized.Parameters
    public static Collection provideParams() {
        return Arrays.asList(
                new Object[][]{
                        {MEDIA_INFO_1, NAVIGATION_INFO_1},
                        {MEDIA_INFO_1, NOTIFICATION_INFO_1},
                        {MEDIA_INFO_1, SYSTEM_SOUND_INFO_1},
                        {MEDIA_INFO_1, SAFETY_INFO_1},
                        {MEDIA_INFO_1, VEHICLE_STATUS_INFO_1},

                        {NAVIGATION_INFO_1, MEDIA_INFO_1},
                        {NAVIGATION_INFO_1, NAVIGATION_INFO_2},
                        {NAVIGATION_INFO_1, CALL_RING_INFO_1},
                        {NAVIGATION_INFO_1, ALARM_INFO_1},
                        {NAVIGATION_INFO_1, NOTIFICATION_INFO_1},
                        {NAVIGATION_INFO_1, SYSTEM_SOUND_INFO_1},
                        {NAVIGATION_INFO_1, SAFETY_INFO_1},
                        {NAVIGATION_INFO_1, VEHICLE_STATUS_INFO_1},
                        {NAVIGATION_INFO_1, ANNOUNCEMENT_INFO_1},

                        {VOICE_COMMAND_INFO_1, MEDIA_INFO_1},
                        {VOICE_COMMAND_INFO_1, VOICE_COMMAND_INFO_2},
                        {VOICE_COMMAND_INFO_1, SAFETY_INFO_1},
                        {VOICE_COMMAND_INFO_1, VEHICLE_STATUS_INFO_1},

                        {CALL_RING_INFO_1, NAVIGATION_INFO_1},
                        {CALL_RING_INFO_1, VOICE_COMMAND_INFO_1},
                        {CALL_RING_INFO_1, CALL_RING_INFO_2},
                        {CALL_RING_INFO_1, CALL_INFO_1},
                        {CALL_RING_INFO_1, SAFETY_INFO_1},
                        {CALL_RING_INFO_1, VEHICLE_STATUS_INFO_1},

                        {CALL_INFO_1, NAVIGATION_INFO_1},
                        {CALL_INFO_1, CALL_RING_INFO_1},
                        {CALL_INFO_1, CALL_INFO_2},
                        {CALL_INFO_1, ALARM_INFO_1},
                        {CALL_INFO_1, NOTIFICATION_INFO_1},
                        {CALL_INFO_1, EMERGENCY_INFO_1},
                        {CALL_INFO_1, SAFETY_INFO_1},
                        {CALL_INFO_1, VEHICLE_STATUS_INFO_1},

                        {ALARM_INFO_1, MEDIA_INFO_1},
                        {ALARM_INFO_1, NAVIGATION_INFO_1},
                        {ALARM_INFO_1, ALARM_INFO_2},
                        {ALARM_INFO_1, NOTIFICATION_INFO_1},
                        {ALARM_INFO_1, SYSTEM_SOUND_INFO_1},
                        {ALARM_INFO_1, SAFETY_INFO_1},
                        {ALARM_INFO_1, VEHICLE_STATUS_INFO_1},

                        {NOTIFICATION_INFO_1, MEDIA_INFO_1},
                        {NOTIFICATION_INFO_1, NAVIGATION_INFO_1},
                        {NOTIFICATION_INFO_1, ALARM_INFO_1},
                        {NOTIFICATION_INFO_1, NOTIFICATION_INFO_2},
                        {NOTIFICATION_INFO_1, SYSTEM_SOUND_INFO_1},
                        {NOTIFICATION_INFO_1, SAFETY_INFO_1},
                        {NOTIFICATION_INFO_1, VEHICLE_STATUS_INFO_1},
                        {NOTIFICATION_INFO_1, ANNOUNCEMENT_INFO_1},

                        {SYSTEM_SOUND_INFO_1, MEDIA_INFO_1},
                        {SYSTEM_SOUND_INFO_1, NAVIGATION_INFO_1},
                        {SYSTEM_SOUND_INFO_1, ALARM_INFO_1},
                        {SYSTEM_SOUND_INFO_1, NOTIFICATION_INFO_1},
                        {SYSTEM_SOUND_INFO_1, SYSTEM_SOUND_INFO_2},
                        {SYSTEM_SOUND_INFO_1, SAFETY_INFO_1},
                        {SYSTEM_SOUND_INFO_1, VEHICLE_STATUS_INFO_1},
                        {SYSTEM_SOUND_INFO_1, ANNOUNCEMENT_INFO_1},

                        {EMERGENCY_INFO_1, CALL_INFO_1},
                        {EMERGENCY_INFO_1, EMERGENCY_INFO_2},
                        {EMERGENCY_INFO_1, SAFETY_INFO_1},

                        {SAFETY_INFO_1, MEDIA_INFO_1},
                        {SAFETY_INFO_1, NAVIGATION_INFO_1},
                        {SAFETY_INFO_1, CALL_RING_INFO_1},
                        {SAFETY_INFO_1, CALL_INFO_1},
                        {SAFETY_INFO_1, ALARM_INFO_1},
                        {SAFETY_INFO_1, NOTIFICATION_INFO_1},
                        {SAFETY_INFO_1, SYSTEM_SOUND_INFO_1},
                        {SAFETY_INFO_1, EMERGENCY_INFO_1},
                        {SAFETY_INFO_1, SAFETY_INFO_2},
                        {SAFETY_INFO_1, VEHICLE_STATUS_INFO_1},
                        {SAFETY_INFO_1, ANNOUNCEMENT_INFO_1},

                        {VEHICLE_STATUS_INFO_1, MEDIA_INFO_1},
                        {VEHICLE_STATUS_INFO_1, NAVIGATION_INFO_1},
                        {VEHICLE_STATUS_INFO_1, CALL_RING_INFO_1},
                        {VEHICLE_STATUS_INFO_1, CALL_INFO_1},
                        {VEHICLE_STATUS_INFO_1, ALARM_INFO_1},
                        {VEHICLE_STATUS_INFO_1, NOTIFICATION_INFO_1},
                        {VEHICLE_STATUS_INFO_1, SYSTEM_SOUND_INFO_1},
                        {VEHICLE_STATUS_INFO_1, SAFETY_INFO_1},
                        {VEHICLE_STATUS_INFO_1, VEHICLE_STATUS_INFO_2},
                        {VEHICLE_STATUS_INFO_1, ANNOUNCEMENT_INFO_1},

                        {ANNOUNCEMENT_INFO_1, NAVIGATION_INFO_1},
                        {ANNOUNCEMENT_INFO_1, NOTIFICATION_INFO_1},
                        {ANNOUNCEMENT_INFO_1, SYSTEM_SOUND_INFO_1},
                        {ANNOUNCEMENT_INFO_1, SAFETY_INFO_1},
                        {ANNOUNCEMENT_INFO_1, VEHICLE_STATUS_INFO_1},
                });
    }

    @Before
    public void setUp() {
        super.setUp();
        when(mCarAudioService.getZoneIdForUid(mExcludedAudioClientInfo.getClientUid()))
                .thenReturn(PRIMARY_ZONE_ID);
        when(mCarAudioService.getZoneIdForUid(mAcceptedAudioClientInfo.getClientUid()))
                .thenReturn(PRIMARY_ZONE_ID);
    }

    @After
    @Override
    public void tearDown() {
        super.tearDown();
    }

    @Test
    public void concurrentInteractionsForFocusGainNoPause_requestGrantedAndFocusLossSent() {
        CarZonesAudioFocus carZonesAudioFocus = getCarZonesAudioFocus();

        AudioFocusInfo excludedAudioFocusInfo =
                new AudioFocusInfoBuilder().setUsage(mExcludedAudioClientInfo.getUsage())
                        .setGainRequest(AUDIOFOCUS_GAIN)
                        .setClientId(mExcludedAudioClientInfo.getClientId())
                        .setClientUid(mExcludedAudioClientInfo.getClientUid())
                        .createAudioFocusInfo();

        AudioFocusInfo acceptedAudioFocusInfo =
                new AudioFocusInfoBuilder().setUsage(mAcceptedAudioClientInfo.getUsage())
                        .setGainRequest(AUDIOFOCUS_GAIN)
                        .setClientId(mAcceptedAudioClientInfo.getClientId())
                        .setClientUid(mAcceptedAudioClientInfo.getClientUid())
                        .createAudioFocusInfo();

        carZonesAudioFocus
                .onAudioFocusRequest(excludedAudioFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        requestFocusAndAssertIfRequestNotGranted(carZonesAudioFocus, acceptedAudioFocusInfo);
        verify(mMockAudioManager).dispatchAudioFocusChange(excludedAudioFocusInfo,
                AudioManager.AUDIOFOCUS_LOSS, mAudioPolicy);
    }

    @Test
    public void concurrentInteractionsForFocusGainNoPause_requestGrantedAndFocusNotGained() {
        CarZonesAudioFocus carZonesAudioFocus = getCarZonesAudioFocus();

        AudioFocusInfo excludedAudioFocusInfo =
                new AudioFocusInfoBuilder().setUsage(mExcludedAudioClientInfo.getUsage())
                        .setGainRequest(AUDIOFOCUS_GAIN)
                        .setClientId(mExcludedAudioClientInfo.getClientId())
                        .setClientUid(mExcludedAudioClientInfo.getClientUid())
                        .createAudioFocusInfo();

        AudioFocusInfo acceptedAudioFocusInfo =
                new AudioFocusInfoBuilder().setUsage(mAcceptedAudioClientInfo.getUsage())
                        .setGainRequest(AUDIOFOCUS_GAIN)
                        .setClientId(mAcceptedAudioClientInfo.getClientId())
                        .setClientUid(mAcceptedAudioClientInfo.getClientUid())
                        .createAudioFocusInfo();

        carZonesAudioFocus
                .onAudioFocusRequest(excludedAudioFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);
        carZonesAudioFocus
                .onAudioFocusRequest(acceptedAudioFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        abandonFocusAndAssertIfAbandonNotGranted(carZonesAudioFocus, acceptedAudioFocusInfo);
        verify(mMockAudioManager, never()).dispatchAudioFocusChange(excludedAudioFocusInfo,
                AUDIOFOCUS_GAIN, mAudioPolicy);
    }

    @Test
    public void concurrentInteractionsTransientNoPause_requestGrantedAndFocusLossSent() {
        testRequestForConcurrentTransientInteractions(/* pauseForDucking= */ false);
    }

    @Test
    public void concurrentInteractionsTransientNoPause_abandonSucceedAndFocusGained() {
        testAbandonForConcurrentTransientInteractions(/* pauseForDucking= */ false);
    }

    @Test
    public void concurrentInteractionsTransientPause_requestGrantedAndFocusLossSent() {
        testRequestForConcurrentTransientInteractions(/* pauseForDucking= */ true);
    }

    @Test
    public void concurrentInteractionsTransientPause_abandonSucceedAndFocusGained() {
        testAbandonForConcurrentTransientInteractions(/* pauseForDucking= */ true);
    }

    @Test
    public void concurrentInteractionsTransientMayDuckNoPause_requestGrantedAndFocusLossSent() {
        CarZonesAudioFocus carZonesAudioFocus = getCarZonesAudioFocus();

        AudioFocusInfo excludedAudioFocusInfo =
                new AudioFocusInfoBuilder().setUsage(mExcludedAudioClientInfo.getUsage())
                        .setGainRequest(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setClientId(mExcludedAudioClientInfo.getClientId())
                        .setClientUid(mExcludedAudioClientInfo.getClientUid())
                        .createAudioFocusInfo();

        AudioFocusInfo acceptedAudioFocusInfo =
                new AudioFocusInfoBuilder().setUsage(mAcceptedAudioClientInfo.getUsage())
                        .setGainRequest(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setClientId(mAcceptedAudioClientInfo.getClientId())
                        .setClientUid(mAcceptedAudioClientInfo.getClientUid())
                        .createAudioFocusInfo();

        carZonesAudioFocus.onAudioFocusRequest(excludedAudioFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        requestFocusAndAssertIfRequestNotGranted(carZonesAudioFocus, acceptedAudioFocusInfo);
        verify(mMockAudioManager, never()).dispatchAudioFocusChange(eq(excludedAudioFocusInfo),
                anyInt(), eq(mAudioPolicy));
    }

    @Test
    public void concurrentInteractionsTransientMayDuckNoPause_abandonSucceedAndFocusNotGained() {
        CarZonesAudioFocus carZonesAudioFocus = getCarZonesAudioFocus();

        AudioFocusInfo excludedAudioFocusInfo =
                new AudioFocusInfoBuilder().setUsage(mExcludedAudioClientInfo.getUsage())
                        .setGainRequest(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setClientId(mExcludedAudioClientInfo.getClientId())
                        .setClientUid(mExcludedAudioClientInfo.getClientUid())
                        .createAudioFocusInfo();

        AudioFocusInfo acceptedAudioFocusInfo =
                new AudioFocusInfoBuilder().setUsage(mAcceptedAudioClientInfo.getUsage())
                        .setGainRequest(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setClientId(mAcceptedAudioClientInfo.getClientId())
                        .setClientUid(mAcceptedAudioClientInfo.getClientUid())
                        .createAudioFocusInfo();

        carZonesAudioFocus.onAudioFocusRequest(excludedAudioFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);
        carZonesAudioFocus.onAudioFocusRequest(acceptedAudioFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        abandonFocusAndAssertIfAbandonNotGranted(carZonesAudioFocus, acceptedAudioFocusInfo);
        verify(mMockAudioManager, never()).dispatchAudioFocusChange(excludedAudioFocusInfo,
                AUDIOFOCUS_GAIN, mAudioPolicy);
    }

    @Test
    public void concurrentInteractionsTransientMayDuckPause_requestGrantedAndFocusLossSent() {
        CarZonesAudioFocus carZonesAudioFocus = getCarZonesAudioFocus();

        AudioFocusInfo excludedAudioFocusInfo =
                new AudioFocusInfoBuilder().setUsage(mExcludedAudioClientInfo.getUsage())
                        .setGainRequest(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setClientId(mExcludedAudioClientInfo.getClientId())
                        .setClientUid(mExcludedAudioClientInfo.getClientUid())
                        .setPausesOnDuckRequestEnable(true).createAudioFocusInfo();

        AudioFocusInfo acceptedAudioFocusInfo =
                new AudioFocusInfoBuilder().setUsage(mAcceptedAudioClientInfo.getUsage())
                        .setGainRequest(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setClientId(mAcceptedAudioClientInfo.getClientId())
                        .setClientUid(mAcceptedAudioClientInfo.getClientUid())
                        .setPausesOnDuckRequestEnable(true).createAudioFocusInfo();

        carZonesAudioFocus.onAudioFocusRequest(excludedAudioFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        requestFocusAndAssertIfRequestNotGranted(carZonesAudioFocus, acceptedAudioFocusInfo);
        verify(mMockAudioManager).dispatchAudioFocusChange(excludedAudioFocusInfo,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, mAudioPolicy);
    }

    @Test
    public void concurrentInteractionsTransientMayDuckPause_abandonSucceedAndFocusGained() {
        CarZonesAudioFocus carZonesAudioFocus = getCarZonesAudioFocus();

        AudioFocusInfo excludedAudioFocusInfo =
                new AudioFocusInfoBuilder().setUsage(mExcludedAudioClientInfo.getUsage())
                        .setGainRequest(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setClientId(mExcludedAudioClientInfo.getClientId())
                        .setClientUid(mExcludedAudioClientInfo.getClientUid())
                        .setPausesOnDuckRequestEnable(true).createAudioFocusInfo();

        AudioFocusInfo acceptedAudioFocusInfo =
                new AudioFocusInfoBuilder().setUsage(mAcceptedAudioClientInfo.getUsage())
                        .setGainRequest(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setClientId(mAcceptedAudioClientInfo.getClientId())
                        .setClientUid(mAcceptedAudioClientInfo.getClientUid())
                        .setPausesOnDuckRequestEnable(true).createAudioFocusInfo();

        carZonesAudioFocus.onAudioFocusRequest(excludedAudioFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);
        carZonesAudioFocus.onAudioFocusRequest(acceptedAudioFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        abandonFocusAndAssertIfAbandonNotGranted(carZonesAudioFocus, acceptedAudioFocusInfo);
        verify(mMockAudioManager).dispatchAudioFocusChange(excludedAudioFocusInfo,
                AUDIOFOCUS_GAIN, mAudioPolicy);
    }

    private void testRequestForConcurrentTransientInteractions(boolean pauseForDucking) {
        CarZonesAudioFocus carZonesAudioFocus = getCarZonesAudioFocus();

        AudioFocusInfo excludedAudioFocusInfo =
                new AudioFocusInfoBuilder().setUsage(mExcludedAudioClientInfo.getUsage())
                        .setGainRequest(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setClientId(mExcludedAudioClientInfo.getClientId())
                        .setClientUid(mExcludedAudioClientInfo.getClientUid())
                        .setPausesOnDuckRequestEnable(pauseForDucking).createAudioFocusInfo();

        AudioFocusInfo acceptedAudioFocusInfo =
                new AudioFocusInfoBuilder().setUsage(mAcceptedAudioClientInfo.getUsage())
                        .setGainRequest(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setClientId(mAcceptedAudioClientInfo.getClientId())
                        .setClientUid(mAcceptedAudioClientInfo.getClientUid())
                        .setPausesOnDuckRequestEnable(pauseForDucking).createAudioFocusInfo();

        carZonesAudioFocus
                .onAudioFocusRequest(excludedAudioFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        requestFocusAndAssertIfRequestNotGranted(carZonesAudioFocus, acceptedAudioFocusInfo);
        verify(mMockAudioManager).dispatchAudioFocusChange(excludedAudioFocusInfo,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, mAudioPolicy);
    }

    private void testAbandonForConcurrentTransientInteractions(boolean pauseForDucking) {
        CarZonesAudioFocus carZonesAudioFocus = getCarZonesAudioFocus();

        AudioFocusInfo excludedAudioFocusInfo =
                new AudioFocusInfoBuilder().setUsage(mExcludedAudioClientInfo.getUsage())
                        .setGainRequest(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setClientId(mExcludedAudioClientInfo.getClientId())
                        .setClientUid(mExcludedAudioClientInfo.getClientUid())
                        .setPausesOnDuckRequestEnable(pauseForDucking).createAudioFocusInfo();

        AudioFocusInfo acceptedAudioFocusInfo =
                new AudioFocusInfoBuilder().setUsage(mAcceptedAudioClientInfo.getUsage())
                        .setGainRequest(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setClientId(mAcceptedAudioClientInfo.getClientId())
                        .setClientUid(mAcceptedAudioClientInfo.getClientUid())
                        .setPausesOnDuckRequestEnable(pauseForDucking).createAudioFocusInfo();

        carZonesAudioFocus
                .onAudioFocusRequest(excludedAudioFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);
        carZonesAudioFocus
                .onAudioFocusRequest(acceptedAudioFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        when(mMockAudioManager.dispatchAudioFocusChange(excludedAudioFocusInfo,
                AUDIOFOCUS_GAIN, mAudioPolicy)).thenReturn(AUDIOFOCUS_REQUEST_GRANTED);

        abandonFocusAndAssertIfAbandonNotGranted(carZonesAudioFocus, acceptedAudioFocusInfo);
        verify(mMockAudioManager).dispatchAudioFocusChange(excludedAudioFocusInfo,
                AUDIOFOCUS_GAIN, mAudioPolicy);
    }
}
