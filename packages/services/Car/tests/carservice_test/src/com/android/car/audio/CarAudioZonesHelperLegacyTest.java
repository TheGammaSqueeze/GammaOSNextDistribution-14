/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.car.media.CarAudioManager.PRIMARY_AUDIO_ZONE;
import static android.media.AudioAttributes.USAGE_ALARM;
import static android.media.AudioAttributes.USAGE_ANNOUNCEMENT;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION;
import static android.media.AudioAttributes.USAGE_ASSISTANT;
import static android.media.AudioAttributes.USAGE_EMERGENCY;
import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioAttributes.USAGE_NOTIFICATION;
import static android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE;
import static android.media.AudioAttributes.USAGE_VEHICLE_STATUS;
import static android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION;
import static android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC;
import static android.media.AudioDeviceInfo.TYPE_FM_TUNER;

import static com.android.car.audio.CarAudioService.CAR_DEFAULT_AUDIO_ATTRIBUTE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.annotation.XmlRes;
import android.content.Context;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.util.SparseArray;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.R;
import com.android.car.audio.hal.AudioControlWrapperV1;

import com.google.common.collect.Lists;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RunWith(AndroidJUnit4.class)
public class CarAudioZonesHelperLegacyTest {

    private static final int INVALID_BUS = -1;

    private static final CarAudioContext TEST_CAR_AUDIO_CONTEXT =
            new CarAudioContext(CarAudioContext.getAllContextsInfo(),
                    /* useCoreAudioRouting= */ false);
    private static final @CarAudioContext.AudioContext int TEST_MEDIA_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(
                    CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA));
    private static final @CarAudioContext.AudioContext int TEST_ALARM_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(
                    CarAudioContext.getAudioAttributeFromUsage(USAGE_ALARM));
    private static final @CarAudioContext.AudioContext int TEST_CALL_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(
                    CarAudioContext.getAudioAttributeFromUsage(USAGE_VOICE_COMMUNICATION));
    private static final @CarAudioContext.AudioContext int TEST_CALL_RING_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(
                    CarAudioContext.getAudioAttributeFromUsage(USAGE_NOTIFICATION_RINGTONE));
    private static final @CarAudioContext.AudioContext int TEST_EMERGENCY_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(
                    CarAudioContext.getAudioAttributeFromUsage(USAGE_EMERGENCY));
    private static final @CarAudioContext.AudioContext int TEST_NAVIGATION_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(CarAudioContext
                    .getAudioAttributeFromUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE));
    private static final @CarAudioContext.AudioContext int TEST_NOTIFICATION_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(CarAudioContext
                    .getAudioAttributeFromUsage(USAGE_NOTIFICATION));
    private static final @CarAudioContext.AudioContext int TEST_ANNOUNCEMENT_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(CarAudioContext
                    .getAudioAttributeFromUsage(USAGE_ANNOUNCEMENT));
    private static final @CarAudioContext.AudioContext int TEST_SYSTEM_SOUND_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(CarAudioContext
                    .getAudioAttributeFromUsage(USAGE_ASSISTANCE_SONIFICATION));
    private static final @CarAudioContext.AudioContext int TEST_VEHICLE_STATUS_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(CarAudioContext
                    .getAudioAttributeFromUsage(USAGE_VEHICLE_STATUS));
    private static final @CarAudioContext.AudioContext int TEST_ASSISTANT_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(CarAudioContext
                    .getAudioAttributeFromUsage(USAGE_ASSISTANT));

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private AudioControlWrapperV1 mMockAudioControlWrapper;
    @Mock
    private CarAudioSettings mMockCarAudioSettings;

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final @XmlRes int mCarVolumeGroups = R.xml.test_car_volume_groups;

    @Test
    public void constructor_checksForNoDuplicateBusNumbers() {
        List<CarAudioDeviceInfo> carAudioDeviceInfos = getCarAudioDeviceInfoWithDuplicateBuses();

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> new CarAudioZonesHelperLegacy(mContext, TEST_CAR_AUDIO_CONTEXT,
                        mCarVolumeGroups, carAudioDeviceInfos, mMockAudioControlWrapper,
                        mMockCarAudioSettings, getInputDevices()));

        assertThat(exception).hasMessageThat().contains("Two addresses map to same bus number:");
    }

    @Test
    public void constructor_throwsIfLegacyContextNotAssignedToBus() throws Exception {
        List<CarAudioDeviceInfo> carAudioDeviceInfos = getValidCarAudioDeviceInfos();

        when(mMockAudioControlWrapper.getBusForContext(anyInt())).thenReturn(INVALID_BUS);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> new CarAudioZonesHelperLegacy(mContext, TEST_CAR_AUDIO_CONTEXT,
                        mCarVolumeGroups, carAudioDeviceInfos, mMockAudioControlWrapper,
                        mMockCarAudioSettings, getInputDevices()));

        assertThat(exception).hasMessageThat()
                .contains("Invalid bus -1 was associated with context");
    }

    @Test
    public void constructor_throwsIfNullInputDevices() throws Exception {
        List<CarAudioDeviceInfo> carAudioDeviceInfos = getValidCarAudioDeviceInfos();

        when(mMockAudioControlWrapper.getBusForContext(anyInt())).thenReturn(INVALID_BUS);

        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> new CarAudioZonesHelperLegacy(mContext, TEST_CAR_AUDIO_CONTEXT,
                        mCarVolumeGroups, carAudioDeviceInfos, mMockAudioControlWrapper,
                        mMockCarAudioSettings, null));

        assertThat(exception).hasMessageThat().contains("Input Devices");
    }

    @Test
    public void constructor_throwsIfNullContext() throws Exception {
        List<CarAudioDeviceInfo> carAudioDeviceInfos = getValidCarAudioDeviceInfos();

        when(mMockAudioControlWrapper.getBusForContext(anyInt())).thenReturn(INVALID_BUS);

        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> new CarAudioZonesHelperLegacy(null, TEST_CAR_AUDIO_CONTEXT,
                        mCarVolumeGroups, carAudioDeviceInfos, mMockAudioControlWrapper,
                        mMockCarAudioSettings, getInputDevices()));

        assertThat(exception).hasMessageThat().contains("Context");
    }

    @Test
    public void constructor_throwsIfNullCarAudioDeviceInfo() throws Exception {
        when(mMockAudioControlWrapper.getBusForContext(anyInt())).thenReturn(INVALID_BUS);

        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> new CarAudioZonesHelperLegacy(mContext, TEST_CAR_AUDIO_CONTEXT,
                        mCarVolumeGroups, null, mMockAudioControlWrapper,
                        mMockCarAudioSettings, getInputDevices()));

        assertThat(exception).hasMessageThat().contains("Car Audio Device Info");
    }

    @Test
    public void constructor_throwsIfNullCarAudioControl() throws Exception {
        List<CarAudioDeviceInfo> carAudioDeviceInfos = getValidCarAudioDeviceInfos();

        when(mMockAudioControlWrapper.getBusForContext(anyInt())).thenReturn(INVALID_BUS);

        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> new CarAudioZonesHelperLegacy(mContext, TEST_CAR_AUDIO_CONTEXT,
                        mCarVolumeGroups, carAudioDeviceInfos, null,
                        mMockCarAudioSettings, getInputDevices()));

        assertThat(exception).hasMessageThat().contains("Car Audio Control");
    }

    @Test
    public void constructor_throwsIfNullCarAudioSettings() throws Exception {
        List<CarAudioDeviceInfo> carAudioDeviceInfos = getValidCarAudioDeviceInfos();

        when(mMockAudioControlWrapper.getBusForContext(anyInt())).thenReturn(INVALID_BUS);

        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> new CarAudioZonesHelperLegacy(mContext, null,
                        mCarVolumeGroups, carAudioDeviceInfos, mMockAudioControlWrapper,
                        mMockCarAudioSettings, getInputDevices()));

        assertThat(exception).hasMessageThat().contains("Car audio context");
    }

    @Test
    public void constructor_throwsIfNullCarAudioContexts() throws Exception {
        List<CarAudioDeviceInfo> carAudioDeviceInfos = getValidCarAudioDeviceInfos();

        when(mMockAudioControlWrapper.getBusForContext(anyInt())).thenReturn(INVALID_BUS);

        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> new CarAudioZonesHelperLegacy(mContext, TEST_CAR_AUDIO_CONTEXT,
                        mCarVolumeGroups, carAudioDeviceInfos, mMockAudioControlWrapper,
                        null, getInputDevices()));

        assertThat(exception).hasMessageThat().contains("Car Audio Settings");
    }

    @Test
    public void loadAudioZones_succeeds() throws Exception {
        List<CarAudioDeviceInfo> carAudioDeviceInfos = getValidCarAudioDeviceInfos();
        when(mMockAudioControlWrapper.getBusForContext(anyInt())).thenReturn(1);

        CarAudioZonesHelperLegacy helper = new CarAudioZonesHelperLegacy(mContext,
                TEST_CAR_AUDIO_CONTEXT, mCarVolumeGroups, carAudioDeviceInfos,
                mMockAudioControlWrapper, mMockCarAudioSettings, getInputDevices());

        SparseArray<CarAudioZone> zones = helper.loadAudioZones();

        assertThat(zones.size()).isEqualTo(1);
    }

    @Test
    public void loadAudioZones_parsesAllVolumeGroups() throws Exception {
        List<CarAudioDeviceInfo> carAudioDeviceInfos = getValidCarAudioDeviceInfos();

        when(mMockAudioControlWrapper.getBusForContext(anyInt())).thenReturn(1);

        CarAudioZonesHelperLegacy helper = new CarAudioZonesHelperLegacy(mContext,
                TEST_CAR_AUDIO_CONTEXT, mCarVolumeGroups, carAudioDeviceInfos,
                mMockAudioControlWrapper, mMockCarAudioSettings, getInputDevices());

        SparseArray<CarAudioZone> zones = helper.loadAudioZones();
        CarVolumeGroup[] volumeGroups = zones.get(0).getCurrentVolumeGroups();
        assertThat(volumeGroups).hasLength(2);
    }

    @Test
    public void loadAudioZones_primaryZoneHasInputDevice() throws Exception {
        List<CarAudioDeviceInfo> carAudioDeviceInfos = getValidCarAudioDeviceInfos();

        when(mMockAudioControlWrapper.getBusForContext(anyInt())).thenReturn(1);

        CarAudioZonesHelperLegacy helper = new CarAudioZonesHelperLegacy(mContext,
                TEST_CAR_AUDIO_CONTEXT, mCarVolumeGroups, carAudioDeviceInfos,
                mMockAudioControlWrapper, mMockCarAudioSettings, getInputDevices());

        SparseArray<CarAudioZone> zones = helper.loadAudioZones();
        CarAudioZone primaryZone = zones.get(PRIMARY_AUDIO_ZONE);
        assertThat(primaryZone.getInputAudioDevices()).hasSize(1);
    }

    @Test
    public void loadAudioZones_primaryZoneHasMicrophoneInputDevice() throws Exception {
        List<CarAudioDeviceInfo> carAudioDeviceInfos = getValidCarAudioDeviceInfos();

        when(mMockAudioControlWrapper.getBusForContext(anyInt())).thenReturn(1);

        CarAudioZonesHelperLegacy helper = new CarAudioZonesHelperLegacy(mContext,
                TEST_CAR_AUDIO_CONTEXT, mCarVolumeGroups, carAudioDeviceInfos,
                mMockAudioControlWrapper, mMockCarAudioSettings, getInputDevices());

        SparseArray<CarAudioZone> zones = helper.loadAudioZones();
        CarAudioZone primaryZone = zones.get(PRIMARY_AUDIO_ZONE);
        List<AudioDeviceAttributes> audioDeviceInfos =
                primaryZone.getInputAudioDevices();
        assertThat(audioDeviceInfos.get(0).getType()).isEqualTo(TYPE_BUILTIN_MIC);
    }

    @Test
    public void loadAudioZones_associatesLegacyContextsWithCorrectBuses() throws Exception {
        List<CarAudioDeviceInfo> carAudioDeviceInfos = getValidCarAudioDeviceInfos();

        when(mMockAudioControlWrapper.getBusForContext(anyInt())).thenReturn(2);
        when(mMockAudioControlWrapper.getBusForContext(TEST_MEDIA_CONTEXT)).thenReturn(1);

        CarAudioZonesHelperLegacy helper = new CarAudioZonesHelperLegacy(mContext,
                TEST_CAR_AUDIO_CONTEXT, mCarVolumeGroups, carAudioDeviceInfos,
                mMockAudioControlWrapper, mMockCarAudioSettings, getInputDevices());

        SparseArray<CarAudioZone> zones = helper.loadAudioZones();

        CarVolumeGroup[] volumeGroups = zones.get(0).getCurrentVolumeGroups();
        CarVolumeGroup mediaVolumeGroup = volumeGroups[0];
        List<Integer> contexts = IntStream.of(mediaVolumeGroup.getContexts()).boxed().collect(
                Collectors.toList());
        assertThat(contexts).contains(TEST_MEDIA_CONTEXT);

        CarVolumeGroup secondVolumeGroup = volumeGroups[1];
        List<Integer> secondContexts = IntStream.of(secondVolumeGroup.getContexts()).boxed()
                .collect(Collectors.toList());
        assertThat(secondContexts).containsAtLeast(TEST_NAVIGATION_CONTEXT,
                TEST_ASSISTANT_CONTEXT, TEST_CALL_RING_CONTEXT,
                TEST_CALL_CONTEXT,
                TEST_ALARM_CONTEXT, TEST_NOTIFICATION_CONTEXT,
                TEST_SYSTEM_SOUND_CONTEXT);

    }

    @Test
    public void loadAudioZones_associatesNonLegacyContextsWithMediaBus() throws Exception {
        List<CarAudioDeviceInfo> carAudioDeviceInfos = getValidCarAudioDeviceInfos();
        when(mMockAudioControlWrapper.getBusForContext(anyInt())).thenReturn(2);
        when(mMockAudioControlWrapper.getBusForContext(TEST_CAR_AUDIO_CONTEXT
                .getContextForAudioAttribute(CAR_DEFAULT_AUDIO_ATTRIBUTE))).thenReturn(1);

        CarAudioZonesHelperLegacy helper = new CarAudioZonesHelperLegacy(mContext,
                TEST_CAR_AUDIO_CONTEXT, mCarVolumeGroups, carAudioDeviceInfos,
                mMockAudioControlWrapper, mMockCarAudioSettings, getInputDevices());

        SparseArray<CarAudioZone> zones = helper.loadAudioZones();

        CarVolumeGroup[] volumeGroups = zones.get(0).getCurrentVolumeGroups();
        CarVolumeGroup mediaVolumeGroup = volumeGroups[0];
        List<Integer> contexts = IntStream.of(mediaVolumeGroup.getContexts()).boxed().collect(
                Collectors.toList());
        assertThat(contexts).containsAtLeast(TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(
                CAR_DEFAULT_AUDIO_ATTRIBUTE),
                TEST_EMERGENCY_CONTEXT, TEST_VEHICLE_STATUS_CONTEXT, TEST_ANNOUNCEMENT_CONTEXT);
    }

    private List<CarAudioDeviceInfo> getCarAudioDeviceInfoWithDuplicateBuses() {
        CarAudioDeviceInfo deviceInfo1 = Mockito.mock(CarAudioDeviceInfo.class);
        when(deviceInfo1.getAddress()).thenReturn("bus001_media");
        CarAudioDeviceInfo deviceInfo2 = Mockito.mock(CarAudioDeviceInfo.class);
        when(deviceInfo2.getAddress()).thenReturn("bus001_notifications");
        return Lists.newArrayList(deviceInfo1, deviceInfo2);
    }

    private AudioDeviceInfo[] getInputDevices() {
        AudioDeviceInfo deviceInfo1 = Mockito.mock(AudioDeviceInfo.class);
        when(deviceInfo1.getType()).thenReturn(TYPE_BUILTIN_MIC);
        when(deviceInfo1.getAddress()).thenReturn("mic");
        when(deviceInfo1.isSink()).thenReturn(false);
        AudioDeviceInfo deviceInfo2 = Mockito.mock(AudioDeviceInfo.class);
        when(deviceInfo2.getAddress()).thenReturn("tuner");
        when(deviceInfo2.getType()).thenReturn(TYPE_FM_TUNER);
        when(deviceInfo2.isSink()).thenReturn(false);
        return new AudioDeviceInfo[]{deviceInfo1, deviceInfo2};
    }

    private List<CarAudioDeviceInfo> getValidCarAudioDeviceInfos() {
        CarAudioDeviceInfo deviceInfo1 = Mockito.mock(CarAudioDeviceInfo.class);
        when(deviceInfo1.getAddress()).thenReturn("bus001_media");
        when(deviceInfo1.getStepValue()).thenReturn(10);
        CarAudioDeviceInfo deviceInfo2 = Mockito.mock(CarAudioDeviceInfo.class);
        when(deviceInfo2.getAddress()).thenReturn("bus002_notifications");
        when(deviceInfo2.getStepValue()).thenReturn(10);
        return Lists.newArrayList(deviceInfo1, deviceInfo2);
    }
}
