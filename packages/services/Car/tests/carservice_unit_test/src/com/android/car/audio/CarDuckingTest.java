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

package com.android.car.audio;

import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.media.AudioAttributes;
import android.media.AudioFocusInfo;
import android.util.SparseArray;

import com.android.car.CarLocalServices;
import com.android.car.audio.hal.AudioControlWrapper;
import com.android.car.oem.CarOemAudioDuckingProxyService;
import com.android.car.oem.CarOemProxyService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public final class CarDuckingTest {
    private static final int PRIMARY_ZONE_ID = 0;
    private static final int PASSENGER_ZONE_ID = 1;
    private static final int[] ONE_ZONE_CHANGE = new int[]{PRIMARY_ZONE_ID};
    private static final int REAR_ZONE_ID = 2;
    private static final String PRIMARY_MEDIA_ADDRESS = "primary_media";
    private static final String PRIMARY_NAVIGATION_ADDRESS = "primary_navigation_address";
    private static final String REAR_MEDIA_ADDRESS = "rear_media";

    private static final CarAudioContext TEST_CAR_AUDIO_CONTEXT =
            new CarAudioContext(CarAudioContext.getAllContextsInfo(),
                    /* useCoreAudioRouting= */ false);

    private static final @CarAudioContext.AudioContext int TEST_MEDIA_AUDIO_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(
                    CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA));
    private static final @CarAudioContext.AudioContext int TEST_NAVIGATION_AUDIO_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(CarAudioContext
                    .getAudioAttributeFromUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE));

    private final SparseArray<CarAudioZone> mCarAudioZones = generateZoneMocks();
    private final AudioFocusInfo mMediaFocusInfo = generateAudioFocusInfoForUsage(USAGE_MEDIA);
    private final AudioFocusInfo mNavigationFocusInfo =
            generateAudioFocusInfoForUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);
    private final SparseArray<List<AudioFocusInfo>> mMediaFocusHolders = new SparseArray<>();
    private final SparseArray<List<AudioFocusInfo>> mMediaNavFocusHolders = new SparseArray<>();

    @Mock
    private AudioControlWrapper mMockAudioControlWrapper;
    @Mock
    private CarOemProxyService mMockCarOemProxyService;
    @Mock
    private CarOemAudioDuckingProxyService mMockCarDuckingProxyService;

    @Captor
    private ArgumentCaptor<List<CarDuckingInfo>> mCarDuckingInfosCaptor;

    private CarDucking mCarDucking;

    @Before
    public void setUp() {
        mCarDucking = new CarDucking(mCarAudioZones, mMockAudioControlWrapper);
        mMediaFocusHolders.put(PRIMARY_ZONE_ID, List.of(mMediaFocusInfo));
        mMediaNavFocusHolders.put(PRIMARY_ZONE_ID, List.of(mMediaFocusInfo, mNavigationFocusInfo));
        CarLocalServices.removeServiceForTest(CarOemProxyService.class);
        CarLocalServices.addService(CarOemProxyService.class, mMockCarOemProxyService);
    }

    @After
    public void tearDown() {
        CarLocalServices.removeServiceForTest(CarOemProxyService.class);
    }

    @Test
    public void constructor_withNullAudioZones_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> {
            new CarDucking(null, mMockAudioControlWrapper);
        });

        assertWithMessage("Null audio zone constructor exception")
                .that(thrown).hasMessageThat().contains("Car audio zones can not be null");
    }

    @Test
    public void constructor_withNullAudioControlWrapper_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> {
            new CarDucking(mCarAudioZones, null);
        });

        assertWithMessage("Null audio control wrapper constructor exception")
                .that(thrown).hasMessageThat().contains("Audio control wrapper can not be null");
    }

    @Test
    public void constructor_initializesEmptyDuckingInfoForZones() {
        SparseArray<CarDuckingInfo> currentDuckingInfo = mCarDucking.getCurrentDuckingInfo();

        assertWithMessage("Ducking info size")
                .that(currentDuckingInfo.size()).isEqualTo(mCarAudioZones.size());
        for (int i = 0; i < mCarAudioZones.size(); i++) {
            int zoneId = mCarAudioZones.keyAt(i);
            CarDuckingInfo duckingInfo = currentDuckingInfo.get(zoneId);
            assertWithMessage("Ducking info object for zone %s", zoneId)
                    .that(duckingInfo).isNotNull();
            assertWithMessage("Ducking info metadata holding focus for zone %s", zoneId)
                    .that(duckingInfo.mPlaybackMetaDataHoldingFocus).isEmpty();
            assertWithMessage("Ducking info addresses to duck for zone %s", zoneId)
                    .that(duckingInfo.mAddressesToDuck).isEmpty();
            assertWithMessage("Ducking info addresses to unduck for zone %s", zoneId)
                    .that(duckingInfo.mAddressesToUnduck).isEmpty();
        }
    }

    @Test
    public void onFocusChange_forPrimaryZone_updatesUsagesHoldingFocus() {
        mCarDucking.onFocusChange(ONE_ZONE_CHANGE, mMediaFocusHolders);

        SparseArray<CarDuckingInfo> newDuckingInfo = mCarDucking.getCurrentDuckingInfo();

        assertWithMessage("Audio attributes holding focus")
                .that(CarHalAudioUtils.metadataToAudioAttributes(newDuckingInfo
                        .get(PRIMARY_ZONE_ID).mPlaybackMetaDataHoldingFocus))
                .containsExactly(CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA));
    }

    @Test
    public void onFocusChange_forPrimaryZone_doesNotUpdateSecondaryZones() {
        mCarDucking.onFocusChange(ONE_ZONE_CHANGE, mMediaFocusHolders);

        SparseArray<CarDuckingInfo> newDuckingInfo = mCarDucking.getCurrentDuckingInfo();

        assertWithMessage("Passenger zone playback metadata holding focus")
                .that(newDuckingInfo.get(PASSENGER_ZONE_ID)
                        .mPlaybackMetaDataHoldingFocus).isEmpty();
        assertWithMessage("Rear zone playback metadata holding focus")
                .that(newDuckingInfo.get(REAR_ZONE_ID).mPlaybackMetaDataHoldingFocus).isEmpty();
    }

    @Test
    public void onFocusChange_withMultipleFocusHolders_updatesAddressesToDuck() {
        mCarDucking.onFocusChange(ONE_ZONE_CHANGE, mMediaNavFocusHolders);

        SparseArray<CarDuckingInfo> newDuckingInfo = mCarDucking.getCurrentDuckingInfo();

        assertWithMessage("Ducking info addresses to duck for multiple focus")
                .that(newDuckingInfo.get(PRIMARY_ZONE_ID).mAddressesToDuck)
                .containsExactly(PRIMARY_MEDIA_ADDRESS);
    }

    @Test
    public void onFocusChange_withDuckedDevices_updatesAddressesToUnduck() {
        mCarDucking.onFocusChange(ONE_ZONE_CHANGE, mMediaNavFocusHolders);

        mCarDucking.onFocusChange(ONE_ZONE_CHANGE, mMediaFocusHolders);

        SparseArray<CarDuckingInfo> newDuckingInfo = mCarDucking.getCurrentDuckingInfo();

        assertWithMessage("Ducking info addresses to unduck for multiple focus")
                .that(newDuckingInfo.get(PRIMARY_ZONE_ID).mAddressesToUnduck)
                .containsExactly(PRIMARY_MEDIA_ADDRESS);
    }

    @Test
    public void onFocusChange_notifiesHalOfUsagesHoldingFocus() {
        mCarDucking.onFocusChange(ONE_ZONE_CHANGE, mMediaFocusHolders);

        verify(mMockAudioControlWrapper).onDevicesToDuckChange(mCarDuckingInfosCaptor.capture());

        List<AudioAttributes> audioAttributesList =
                CarHalAudioUtils.metadataToAudioAttributes(
                        mCarDuckingInfosCaptor.getValue().get(0).mPlaybackMetaDataHoldingFocus);

        assertWithMessage("Audio attributes holding focus")
                .that(audioAttributesList)
                .containsExactly(CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA));
    }

    @Test
    public void onFocusChange_notifiesHalOfAddressesToDuck() {
        mCarDucking.onFocusChange(ONE_ZONE_CHANGE, mMediaNavFocusHolders);

        verify(mMockAudioControlWrapper).onDevicesToDuckChange(mCarDuckingInfosCaptor.capture());

        List<String> addressesToDuck = mCarDuckingInfosCaptor.getValue().get(0).mAddressesToDuck;
        assertWithMessage("Notified addresses to duck")
                .that(addressesToDuck).containsExactly(PRIMARY_MEDIA_ADDRESS);
    }

    @Test
    public void onFocusChange_notifiesHalOfAddressesToUnduck() {
        mCarDucking.onFocusChange(ONE_ZONE_CHANGE, mMediaNavFocusHolders);

        mCarDucking.onFocusChange(ONE_ZONE_CHANGE, mMediaFocusHolders);

        verify(mMockAudioControlWrapper, times(2))
                .onDevicesToDuckChange(mCarDuckingInfosCaptor.capture());
        List<String> addressesToUnduck = mCarDuckingInfosCaptor.getValue().get(0)
                .mAddressesToUnduck;
        assertWithMessage("Notified addresses to unduck")
                .that(addressesToUnduck).containsExactly(PRIMARY_MEDIA_ADDRESS);
    }

    @Test
    public void onFocusChange_withMultipleZones_notifiesForEachZone() {
        int[] zoneIds = new int[]{PRIMARY_ZONE_ID, PASSENGER_ZONE_ID};
        SparseArray<List<AudioFocusInfo>> focusChanges = new SparseArray<>();
        focusChanges.put(PRIMARY_ZONE_ID, List.of(mMediaFocusInfo));
        focusChanges.put(PASSENGER_ZONE_ID, List.of(mNavigationFocusInfo));

        mCarDucking.onFocusChange(zoneIds, focusChanges);

        verify(mMockAudioControlWrapper).onDevicesToDuckChange(mCarDuckingInfosCaptor.capture());
        assertWithMessage("Notified ducking info, zone size")
                .that(mCarDuckingInfosCaptor.getValue().size()).isEqualTo(zoneIds.length);
    }

    @Test
    public void onFocusChange_withOemServiceAvailable_notifiesForEachZone() {
        when(mMockCarOemProxyService.isOemServiceEnabled()).thenReturn(true);
        when(mMockCarOemProxyService.isOemServiceReady()).thenReturn(false);
        int[] zoneIds = new int[]{PRIMARY_ZONE_ID};
        SparseArray<List<AudioFocusInfo>> focusChanges = new SparseArray<>();
        focusChanges.put(PRIMARY_ZONE_ID, List.of(mMediaFocusInfo));

        mCarDucking.onFocusChange(zoneIds, focusChanges);

        verify(mMockAudioControlWrapper).onDevicesToDuckChange(mCarDuckingInfosCaptor.capture());
        assertWithMessage("Notified ducking info with OEM enabled but not ready, zone size")
                .that(mCarDuckingInfosCaptor.getValue().size()).isEqualTo(zoneIds.length);
    }

    @Test
    public void onFocusChange_withOemServiceReady_notifiesForEachZone() {
        when(mMockCarOemProxyService.isOemServiceEnabled()).thenReturn(true);
        when(mMockCarOemProxyService.isOemServiceReady()).thenReturn(true);
        int[] zoneIds = new int[]{PRIMARY_ZONE_ID};
        SparseArray<List<AudioFocusInfo>> focusChanges = new SparseArray<>();
        focusChanges.put(PRIMARY_ZONE_ID, List.of(mMediaFocusInfo));

        mCarDucking.onFocusChange(zoneIds, focusChanges);

        verify(mMockAudioControlWrapper).onDevicesToDuckChange(mCarDuckingInfosCaptor.capture());
        assertWithMessage("Notified ducking info with OEM service and ready, zone size")
                .that(mCarDuckingInfosCaptor.getValue().size()).isEqualTo(zoneIds.length);
    }

    @Test
    public void onFocusChange_withOemDucking_notifiesForEachZone() {
        enableOemDuckingService();
        when(mMockCarDuckingProxyService.evaluateAttributesToDuck(any())).thenReturn(
                Collections.EMPTY_LIST);
        int[] zoneIds = new int[]{PRIMARY_ZONE_ID};
        SparseArray<List<AudioFocusInfo>> focusChanges = new SparseArray<>();
        focusChanges.put(PRIMARY_ZONE_ID, List.of(mMediaFocusInfo));

        mCarDucking.onFocusChange(zoneIds, focusChanges);

        verify(mMockAudioControlWrapper).onDevicesToDuckChange(mCarDuckingInfosCaptor.capture());
        assertWithMessage("Notified ducking info with Ducking OEM service active, zone size")
                .that(mCarDuckingInfosCaptor.getValue().size()).isEqualTo(zoneIds.length);
    }

    @Test
    public void onFocusChange_withOemDuckingAndReturnMedia_ducksMedia() {
        enableOemDuckingService();
        when(mMockCarDuckingProxyService.evaluateAttributesToDuck(any()))
                .thenReturn(List.of(CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA)));
        int[] zoneIds = new int[]{PRIMARY_ZONE_ID};
        SparseArray<List<AudioFocusInfo>> focusChanges = new SparseArray<>();
        focusChanges.put(PRIMARY_ZONE_ID, List.of(mMediaFocusInfo));

        mCarDucking.onFocusChange(zoneIds, focusChanges);

        verify(mMockAudioControlWrapper).onDevicesToDuckChange(mCarDuckingInfosCaptor.capture());
        CarDuckingInfo info = mCarDuckingInfosCaptor.getValue().get(0);
        assertWithMessage("Ducked device address from OEM service")
                .that(info.getAddressesToDuck()).containsExactly(PRIMARY_MEDIA_ADDRESS);
    }

    @Test
    public void onFocusChange_withOemDuckingCalledTwice_unducksMedia() {
        enableOemDuckingService();
        when(mMockCarDuckingProxyService.evaluateAttributesToDuck(any()))
                .thenReturn(List.of(CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA)))
                .thenReturn(List.of());
        int[] zoneIds = new int[]{PRIMARY_ZONE_ID};
        SparseArray<List<AudioFocusInfo>> focusChanges = new SparseArray<>();
        focusChanges.put(PRIMARY_ZONE_ID, List.of(mMediaFocusInfo));
        mCarDucking.onFocusChange(zoneIds, focusChanges);

        mCarDucking.onFocusChange(zoneIds, focusChanges);

        verify(mMockAudioControlWrapper, times(2))
                .onDevicesToDuckChange(mCarDuckingInfosCaptor.capture());
        CarDuckingInfo info = mCarDuckingInfosCaptor.getValue().get(0);
        assertWithMessage("Un-ducked device address from OEM service")
                .that(info.getAddressesToUnduck()).containsExactly(PRIMARY_MEDIA_ADDRESS);
    }

    private void enableOemDuckingService() {
        when(mMockCarOemProxyService.isOemServiceEnabled()).thenReturn(true);
        when(mMockCarOemProxyService.isOemServiceReady()).thenReturn(true);
        when(mMockCarOemProxyService.getCarOemAudioDuckingService())
                .thenReturn(mMockCarDuckingProxyService);
    }

    private AudioFocusInfo generateAudioFocusInfoForUsage(int usage) {
        AudioAttributes attributes = new AudioAttributes.Builder().setUsage(usage).build();
        return new AudioFocusInfo(attributes, 0, "client_id", "package.name",
                AUDIOFOCUS_GAIN_TRANSIENT, 0, 0, 0);
    }

    private static SparseArray<CarAudioZone> generateZoneMocks() {
        SparseArray<CarAudioZone> zones = new SparseArray<>();
        CarAudioZone primaryZone = mock(CarAudioZone.class);
        when(primaryZone.getId()).thenReturn(PRIMARY_ZONE_ID);
        when(primaryZone.getAddressForContext(TEST_MEDIA_AUDIO_CONTEXT))
                .thenReturn(PRIMARY_MEDIA_ADDRESS);
        when(primaryZone.getAddressForContext(TEST_NAVIGATION_AUDIO_CONTEXT))
                .thenReturn(PRIMARY_NAVIGATION_ADDRESS);
        when(primaryZone.getCarAudioContext()).thenReturn(TEST_CAR_AUDIO_CONTEXT);
        zones.append(PRIMARY_ZONE_ID, primaryZone);

        CarAudioZone passengerZone = mock(CarAudioZone.class);
        when(passengerZone.getId()).thenReturn(PASSENGER_ZONE_ID);
        when(passengerZone.getAddressForContext(anyInt())).thenReturn("");
        when(passengerZone.getCarAudioContext()).thenReturn(TEST_CAR_AUDIO_CONTEXT);
        zones.append(PASSENGER_ZONE_ID, passengerZone);

        CarAudioZone rearZone = mock(CarAudioZone.class);
        when(rearZone.getId()).thenReturn(REAR_ZONE_ID);
        when(rearZone.getAddressForContext(TEST_MEDIA_AUDIO_CONTEXT))
                .thenReturn(REAR_MEDIA_ADDRESS);
        when(rearZone.getCarAudioContext()).thenReturn(TEST_CAR_AUDIO_CONTEXT);
        zones.append(REAR_ZONE_ID, rearZone);

        return zones;
    }
}
