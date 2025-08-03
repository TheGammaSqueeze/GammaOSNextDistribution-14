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

import static android.car.PlatformVersion.VERSION_CODES.UPSIDE_DOWN_CAKE_0;
import static android.car.media.CarAudioManager.PRIMARY_AUDIO_ZONE;
import static android.car.test.mocks.AndroidMockitoHelper.mockCarGetPlatformVersion;
import static android.media.AudioAttributes.USAGE_ANNOUNCEMENT;
import static android.media.AudioAttributes.USAGE_EMERGENCY;
import static android.media.AudioAttributes.USAGE_SAFETY;
import static android.media.AudioAttributes.USAGE_VEHICLE_STATUS;
import static android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC;
import static android.media.AudioDeviceInfo.TYPE_BUS;
import static android.media.AudioDeviceInfo.TYPE_FM_TUNER;
import static android.media.audiopolicy.Flags.FLAG_ENABLE_FADE_MANAGER_CONFIGURATION;

import static com.android.car.audio.CarAudioService.CAR_DEFAULT_AUDIO_ATTRIBUTE;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.feature.Flags;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.SparseArray;
import android.util.SparseIntArray;

import androidx.test.core.app.ApplicationProvider;

import com.android.car.R;
import com.android.car.internal.util.LocalLog;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(MockitoJUnitRunner.class)
public class CarAudioZonesHelperTest extends AbstractExtendedMockitoTestCase {
    private static final String TAG = CarAudioZonesHelperTest.class.getSimpleName();

    private static final CarAudioContextInfo OEM_CONTEXT_INFO_MUSIC =
            new CarAudioContextInfo(new AudioAttributes[] {
                    CarAudioContext.getAudioAttributeFromUsage(AudioAttributes.USAGE_UNKNOWN),
                    CarAudioContext.getAudioAttributeFromUsage(AudioAttributes.USAGE_GAME),
                    CarAudioContext.getAudioAttributeFromUsage(AudioAttributes.USAGE_MEDIA)
            }, "OEM_MUSIC", 1);

    private static final CarAudioContextInfo OEM_CONTEXT_INFO_NAVIGATION =
            new CarAudioContextInfo(new AudioAttributes[] {
                    CarAudioContext.getAudioAttributeFromUsage(AudioAttributes
                            .USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)}, "OEM_NAVIGATION", 2);

    private static final CarAudioContextInfo OEM_CONTEXT_INFO_VOICE_COMMAND =
            new CarAudioContextInfo(new AudioAttributes[] {
                    CarAudioContext.getAudioAttributeFromUsage(
                            AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY),
                    CarAudioContext.getAudioAttributeFromUsage(AudioAttributes.USAGE_ASSISTANT)
            }, "OEM_VOICE_COMMAND", 3);

    private static final CarAudioContextInfo OEM_CONTEXT_INFO_CALL_RING =
            new CarAudioContextInfo(new AudioAttributes[] {
                    CarAudioContext.getAudioAttributeFromUsage(
                            AudioAttributes.USAGE_NOTIFICATION_RINGTONE)}, "OEM_CALL_RING", 4);

    private static final CarAudioContextInfo OEM_CONTEXT_INFO_CALL =
            new CarAudioContextInfo(new AudioAttributes[] {
                    CarAudioContext.getAudioAttributeFromUsage(
                            AudioAttributes.USAGE_VOICE_COMMUNICATION),
                    CarAudioContext.getAudioAttributeFromUsage(
                            AudioAttributes.USAGE_CALL_ASSISTANT),
                    CarAudioContext.getAudioAttributeFromUsage(AudioAttributes
                            .USAGE_VOICE_COMMUNICATION_SIGNALLING)
            }, "OEM_CALL", 5);

    private static final CarAudioContextInfo OEM_CONTEXT_INFO_ALARM =
            new CarAudioContextInfo(new AudioAttributes[]{
                    CarAudioContext.getAudioAttributeFromUsage(AudioAttributes.USAGE_ALARM)
            }, "OEM_ALARM", 6);

    private static final CarAudioContextInfo OEM_CONTEXT_INFO_NOTIFICATION =
            new CarAudioContextInfo(new AudioAttributes[]{
                    CarAudioContext.getAudioAttributeFromUsage(AudioAttributes.USAGE_NOTIFICATION),
                    CarAudioContext.getAudioAttributeFromUsage(
                            AudioAttributes.USAGE_NOTIFICATION_EVENT)}, "OEM_NOTIFICATION", 7);

    private static final CarAudioContextInfo OEM_CONTEXT_INFO_SYSTEM_SOUND =
            new CarAudioContextInfo(new AudioAttributes[]{
                    CarAudioContext.getAudioAttributeFromUsage(
                            AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)},
                    "OEM_SYSTEM_SOUND", 8);

    private static final CarAudioContextInfo OEM_CONTEXT_INFO_EMERGENCY =
            new CarAudioContextInfo(new AudioAttributes[]{
                    CarAudioContext.getAudioAttributeFromUsage(AudioAttributes.USAGE_EMERGENCY)
            }, "OEM_EMERGENCY", 9);

    private static final CarAudioContextInfo OEM_CONTEXT_INFO_SAFETY =
            new CarAudioContextInfo(new AudioAttributes[]{
                    CarAudioContext.getAudioAttributeFromUsage(AudioAttributes.USAGE_SAFETY)
            }, "OEM_SAFETY", 10);

    private static final CarAudioContextInfo OEM_CONTEXT_INFO_VEHICLE_STATUS =
            new CarAudioContextInfo(new AudioAttributes[]{
                    CarAudioContext.getAudioAttributeFromUsage(
                            AudioAttributes.USAGE_VEHICLE_STATUS)}, "OEM_VEHICLE_STATUS", 11);

    private static final CarAudioContextInfo OEM_CONTEXT_INFO_ANNOUNCEMENT =
            new CarAudioContextInfo(new AudioAttributes[]{
                    CarAudioContext.getAudioAttributeFromUsage(AudioAttributes.USAGE_ANNOUNCEMENT)
            }, "OEM_ANNOUNCEMENT", 12);

    public static final CarAudioContext TEST_CAR_AUDIO_CONTEXT =
            new CarAudioContext(CarAudioContext.getAllContextsInfo(),
                    /* useCoreAudioRouting= */ false);
    public static final int TEST_DEFAULT_CONTEXT_ID = TEST_CAR_AUDIO_CONTEXT
            .getContextForAudioAttribute(CAR_DEFAULT_AUDIO_ATTRIBUTE);
    public static final int TEST_EMERGENCY_CONTEXT_ID = TEST_CAR_AUDIO_CONTEXT
            .getContextForAudioAttribute(CarAudioContext
                    .getAudioAttributeFromUsage(USAGE_EMERGENCY));
    public static final int TEST_SAFETY_CONTEXT_ID = TEST_CAR_AUDIO_CONTEXT
            .getContextForAudioAttribute(CarAudioContext
                    .getAudioAttributeFromUsage(USAGE_SAFETY));
    public static final int TEST_VEHICLE_STATUS_CONTEXT_ID = TEST_CAR_AUDIO_CONTEXT
            .getContextForAudioAttribute(CarAudioContext
                    .getAudioAttributeFromUsage(USAGE_VEHICLE_STATUS));
    public static final int TEST_ANNOUNCEMENT_CONTEXT_ID = TEST_CAR_AUDIO_CONTEXT
            .getContextForAudioAttribute(CarAudioContext
                    .getAudioAttributeFromUsage(USAGE_ANNOUNCEMENT));
    private static final String BUS_MIRROR_DEVICE_1 = "mirror_bus_device_1";
    private static final String BUS_MIRROR_DEVICE_2 = "mirror_bus_device_2";
    private static final String PRIMARY_ZONE_NAME = "primary zone";
    private static final String PRIMARY_ZONE_CONFIG_NAME = "primary zone config 1";
    private static final String REAR_ZONE_CONFIG_NAME_1 = "rear seat zone config 1";
    private static final String REAR_ZONE_CONFIG_NAME_2 = "rear seat zone config 2";

    private static final String BUS_0_ADDRESS = "bus0_media_out";
    private static final String BUS_1_ADDRESS = "bus1_navigation_out";
    private static final String BUS_3_ADDRESS = "bus3_call_ring_out";
    private static final String BUS_100_ADDRESS = "bus100_rear_seat";
    private static final String BUS_101_ADDRESS = "bus101_rear_seat";
    private static final String BUS_1000_ADDRESS_DOES_NOT_EXIST = "bus1000_does_not_exist";

    private static final String PRIMARY_ZONE_MICROPHONE_ADDRESS = "Built-In Mic";
    private static final String PRIMARY_ZONE_FM_TUNER_ADDRESS = "fm_tuner";
    private static final String SECONDARY_ZONE_BACK_MICROPHONE_ADDRESS = "Built-In Back Mic";
    private static final String SECONDARY_ZONE_BUS_1000_INPUT_ADDRESS = "bus_1000_input";

    private static final int PRIMARY_OCCUPANT_ID = 1;
    private static final int SECONDARY_ZONE_ID = 2;
    private List<CarAudioDeviceInfo> mCarAudioOutputDeviceInfos;
    private AudioDeviceInfo[] mInputAudioDeviceInfos;
    private InputStream mInputStream;
    private Context mContext;
    private CarAudioSettings mCarAudioSettings;
    @Mock
    private AudioManager mAudioManager;

    @Mock
    private CarAudioDeviceInfo mTestCarMirrorDeviceOne;
    @Mock
    private CarAudioDeviceInfo mTestCarMirrorDeviceTwo;
    @Mock
    private LocalLog mServiceEventLogger;

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    public CarAudioZonesHelperTest() {
        super(TAG);
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session
                .spyStatic(AudioManager.class)
                .spyStatic(Car.class)
                .spyStatic(CoreAudioHelper.class);
    }

    @Before
    public void setUp() {
        setupAudioManagerMock();

        mCarAudioOutputDeviceInfos = generateCarDeviceInfos();
        mInputAudioDeviceInfos = generateInputDeviceInfos();
        mContext = ApplicationProvider.getApplicationContext();
        mInputStream = mContext.getResources().openRawResource(R.raw.car_audio_configuration);
        mCarAudioSettings = mock(CarAudioSettings.class);
        mockCarGetPlatformVersion(UPSIDE_DOWN_CAKE_0);
    }

    @After
    public void tearDown() throws IOException {
        if (mInputStream != null) {
            mInputStream.close();
        }
    }

    private List<CarAudioDeviceInfo> generateCarDeviceInfos() {
        mTestCarMirrorDeviceOne = generateCarAudioDeviceInfo(BUS_MIRROR_DEVICE_1);
        mTestCarMirrorDeviceTwo = generateCarAudioDeviceInfo(BUS_MIRROR_DEVICE_2);
        return ImmutableList.of(
                generateCarAudioDeviceInfo(BUS_0_ADDRESS),
                generateCarAudioDeviceInfo(BUS_1_ADDRESS),
                generateCarAudioDeviceInfo(BUS_3_ADDRESS),
                generateCarAudioDeviceInfo(BUS_100_ADDRESS),
                generateCarAudioDeviceInfo(BUS_101_ADDRESS),
                generateCarAudioDeviceInfo(""),
                generateCarAudioDeviceInfo(""),
                generateCarAudioDeviceInfo(null),
                generateCarAudioDeviceInfo(null),
                mTestCarMirrorDeviceOne,
                mTestCarMirrorDeviceTwo
        );
    }

    private AudioDeviceInfo[] generateInputDeviceInfos() {
        return new AudioDeviceInfo[]{
                generateInputAudioDeviceInfo(PRIMARY_ZONE_MICROPHONE_ADDRESS, TYPE_BUILTIN_MIC),
                generateInputAudioDeviceInfo(PRIMARY_ZONE_FM_TUNER_ADDRESS, TYPE_FM_TUNER),
                generateInputAudioDeviceInfo(SECONDARY_ZONE_BACK_MICROPHONE_ADDRESS, TYPE_BUS),
                generateInputAudioDeviceInfo(SECONDARY_ZONE_BUS_1000_INPUT_ADDRESS,
                        TYPE_BUILTIN_MIC)
        };
    }

    private CarAudioDeviceInfo generateCarAudioDeviceInfo(String address) {
        CarAudioDeviceInfo cadiMock = mock(CarAudioDeviceInfo.class);
        when(cadiMock.getStepValue()).thenReturn(1);
        when(cadiMock.getDefaultGain()).thenReturn(2);
        when(cadiMock.getMaxGain()).thenReturn(5);
        when(cadiMock.getMinGain()).thenReturn(0);
        when(cadiMock.getAddress()).thenReturn(address);
        return cadiMock;
    }

    private AudioDeviceInfo generateInputAudioDeviceInfo(String address, int type) {
        AudioDeviceInfo inputMock = mock(AudioDeviceInfo.class);
        when(inputMock.getAddress()).thenReturn(address);
        when(inputMock.getType()).thenReturn(type);
        when(inputMock.isSource()).thenReturn(true);
        when(inputMock.isSink()).thenReturn(false);
        when(inputMock.getInternalType()).thenReturn(
                AudioDeviceInfo.convertDeviceTypeToInternalInputDevice(type));
        return inputMock;
    }

    @Test
    public void loadAudioZones_parsesAllZones() throws Exception {
        CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                mInputStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                /* useFadeManagerConfiguration= */ false,
                /* carAudioFadeConfigurationHelper= */ null);

        SparseArray<CarAudioZone> zones = cazh.loadAudioZones();

        expectWithMessage("Zones parsed").that(zones.size()).isEqualTo(2);
    }

    @Test
    public void loadAudioZones_versionTwoParsesAllZones() throws Exception {
        try (InputStream versionTwoStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_V2)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    versionTwoStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);

            SparseArray<CarAudioZone> zones = cazh.loadAudioZones();

            expectWithMessage("Zones parsed for version-two configuration")
                    .that(zones.size()).isEqualTo(2);
        }
    }

    @Test
    public void loadAudioZones_versionOneParsesAllZones() throws Exception {
        try (InputStream versionOneStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_V1)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    versionOneStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);

            SparseArray<CarAudioZone> zones = cazh.loadAudioZones();

            assertThat(zones.size()).isEqualTo(2);
        }
    }

    @Test
    public void loadAudioZones_parsesAudioZoneId() throws Exception {
        CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                mInputStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                /* useFadeManagerConfiguration= */ false,
                /* carAudioFadeConfigurationHelper= */ null);

        SparseArray<CarAudioZone> zones = cazh.loadAudioZones();

        expectWithMessage("Primary zone id in zones parsed")
                .that(zones.contains(PRIMARY_AUDIO_ZONE)).isTrue();
        expectWithMessage("Secondary zone id in zones parsed")
                .that(zones.contains(SECONDARY_ZONE_ID)).isTrue();
    }

    @Test
    public void loadAudioZones_versionTwoParsesAudioZoneId() throws Exception {
        try (InputStream versionTwoStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_V2)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    versionTwoStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);

            SparseArray<CarAudioZone> zones = cazh.loadAudioZones();

            expectWithMessage("Primary zone id in zones parsed in version-two configuration")
                    .that(zones.contains(PRIMARY_AUDIO_ZONE)).isTrue();
            expectWithMessage("Secondary zone id in zones parsed in version-two configuration")
                    .that(zones.contains(SECONDARY_ZONE_ID)).isTrue();
        }
    }

    @Test
    public void loadAudioZones_parsesOccupantZoneId() throws Exception {
        CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                mInputStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                /* useFadeManagerConfiguration= */ false,
                /* carAudioFadeConfigurationHelper= */ null);

        cazh.loadAudioZones();

        SparseIntArray audioZoneIdToOccupantZoneIdMapping =
                cazh.getCarAudioZoneIdToOccupantZoneIdMapping();
        expectWithMessage("Occupant zone id of primary zone")
                .that(audioZoneIdToOccupantZoneIdMapping.get(PRIMARY_AUDIO_ZONE))
                .isEqualTo(PRIMARY_OCCUPANT_ID);
        expectWithMessage("Occupant zone id of secondary zone")
                .that(audioZoneIdToOccupantZoneIdMapping.get(SECONDARY_ZONE_ID,
                        /* valueIfKeyNotFound= */ -1)).isEqualTo(-1);
    }

    @Test
    public void loadAudioZones_versionTwoParsesOccupantZoneId() throws Exception {
        try (InputStream versionTwoStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_V2)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    versionTwoStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);

            cazh.loadAudioZones();

            SparseIntArray audioZoneIdToOccupantZoneIdMapping =
                    cazh.getCarAudioZoneIdToOccupantZoneIdMapping();
            expectWithMessage("Occupant zone id of primary zone in version-two configuration")
                    .that(audioZoneIdToOccupantZoneIdMapping.get(PRIMARY_AUDIO_ZONE))
                    .isEqualTo(PRIMARY_OCCUPANT_ID);
            expectWithMessage("Occupant zone id of secondary zone in version-two configuration")
                    .that(audioZoneIdToOccupantZoneIdMapping.get(SECONDARY_ZONE_ID,
                            /* valueIfKeyNotFound= */ -1)).isEqualTo(-1);
        }
    }

    @Test
    public void loadAudioZones_parsesZoneName() throws Exception {
        CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                mInputStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                /* useFadeManagerConfiguration= */ false,
                /* carAudioFadeConfigurationHelper= */ null);

        SparseArray<CarAudioZone> zones = cazh.loadAudioZones();

        CarAudioZone primaryZone = zones.get(0);
        expectWithMessage("Primary zone name")
                .that(primaryZone.getName()).isEqualTo(PRIMARY_ZONE_NAME);
    }

    @Test
    public void loadAudioZones_versionTwoParsesZoneName() throws Exception {
        try (InputStream versionTwoStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_V2)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    versionTwoStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);

            SparseArray<CarAudioZone> zones = cazh.loadAudioZones();

            CarAudioZone primaryZone = zones.get(0);
            expectWithMessage("Primary zone name in version-two configuration")
                    .that(primaryZone.getName()).isEqualTo(PRIMARY_ZONE_NAME);
        }
    }

    @Test
    public void loadAudioZones_parsesIsPrimary() throws Exception {
        CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                mInputStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                /* useFadeManagerConfiguration= */ false,
                /* carAudioFadeConfigurationHelper= */ null);

        SparseArray<CarAudioZone> zones = cazh.loadAudioZones();

        expectWithMessage("Primary zone").that(zones.get(0).isPrimaryZone()).isTrue();
        expectWithMessage("Primary secondary zone").that(zones.get(2).isPrimaryZone()).isFalse();
    }

    @Test
    public void loadAudioZones_versionTwoParsesIsPrimary() throws Exception {
        try (InputStream versionTwoStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_V2)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    versionTwoStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);

            SparseArray<CarAudioZone> zones = cazh.loadAudioZones();

            expectWithMessage("Primary zone in version-two configuration")
                    .that(zones.get(0).isPrimaryZone()).isTrue();
            expectWithMessage("Primary secondary zone in version-two configuration")
                    .that(zones.get(2).isPrimaryZone()).isFalse();
        }
    }

    @Test
    public void loadAudioZones_parsesZoneConfigs() throws Exception {
        CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                mInputStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                /* useFadeManagerConfiguration= */ false,
                /* carAudioFadeConfigurationHelper= */ null);

        SparseArray<CarAudioZone> zones = cazh.loadAudioZones();

        List<CarAudioZoneConfig> primaryZoneConfigs = zones.get(0).getAllCarAudioZoneConfigs();
        expectWithMessage("Primary zone configs").that(primaryZoneConfigs.size()).isEqualTo(1);
        expectWithMessage("Primary zone config name")
                .that(primaryZoneConfigs.get(0).getName()).isEqualTo(PRIMARY_ZONE_CONFIG_NAME);
        expectWithMessage("Primary zone default config")
                .that(primaryZoneConfigs.get(0).isDefault()).isTrue();
        List<CarAudioZoneConfig> secondaryZoneConfigs = zones.get(2).getAllCarAudioZoneConfigs();
        expectWithMessage("Secondary zone configs")
                .that(secondaryZoneConfigs.size()).isEqualTo(2);
        expectWithMessage("Secondary zone config names")
                .that(List.of(secondaryZoneConfigs.get(0).getName(),
                        secondaryZoneConfigs.get(1).getName()))
                .containsExactly(REAR_ZONE_CONFIG_NAME_1, REAR_ZONE_CONFIG_NAME_2);
    }

    @Test
    public void loadAudioZones_parsesDefaultZoneConfigs() throws Exception {
        CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                mInputStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                /* useFadeManagerConfiguration= */ false,
                /* carAudioFadeConfigurationHelper= */ null);

        SparseArray<CarAudioZone> zones = cazh.loadAudioZones();

        CarAudioZoneConfig primaryZoneConfig = zones.get(0).getCurrentCarAudioZoneConfig();
        expectWithMessage("Default primary zone config name")
                .that(primaryZoneConfig.getName()).isEqualTo(PRIMARY_ZONE_CONFIG_NAME);
        CarAudioZoneConfig secondaryZoneConfig = zones.get(2).getCurrentCarAudioZoneConfig();
        expectWithMessage("Default secondary zone config name")
                .that(secondaryZoneConfig.getName()).isEqualTo(REAR_ZONE_CONFIG_NAME_1);
    }

    @Test
    public void loadAudioZones_parsesVolumeGroups() throws Exception {
        CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                mInputStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                /* useFadeManagerConfiguration= */ false,
                /* carAudioFadeConfigurationHelper= */ null);

        SparseArray<CarAudioZone> zones = cazh.loadAudioZones();

        CarAudioZone primaryZone = zones.get(0);
        CarAudioZone secondaryZone = zones.get(2);
        expectWithMessage("Volume group count for primary zone")
                .that(primaryZone.getCurrentVolumeGroupCount()).isEqualTo(2);
        expectWithMessage("Volume group count for secondary zone")
                .that(secondaryZone.getCurrentVolumeGroupCount()).isEqualTo(1);
    }

    @Test
    public void loadAudioZones_versionTwoParsesVolumeGroups() throws Exception {
        try (InputStream versionTwoStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_V2)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    versionTwoStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);

            SparseArray<CarAudioZone> zones = cazh.loadAudioZones();

            CarAudioZone primaryZone = zones.get(0);
            expectWithMessage("Volume group count for primary zone in version-two configuration")
                    .that(primaryZone.getCurrentVolumeGroupCount()).isEqualTo(2);
        }
    }

    @Test
    public void loadAudioZones_parsesAddresses() throws Exception {
        CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                mInputStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                /* useFadeManagerConfiguration= */ false,
                /* carAudioFadeConfigurationHelper= */ null);

        SparseArray<CarAudioZone> zones = cazh.loadAudioZones();

        CarAudioZone primaryZone = zones.get(0);
        CarVolumeGroup primaryVolumeGroup = primaryZone.getCurrentVolumeGroups()[0];
        List<String> primaryAddresses = primaryVolumeGroup.getAddresses();
        expectWithMessage("Primary zone addresses")
                .that(primaryAddresses).containsExactly(BUS_0_ADDRESS, BUS_3_ADDRESS);
        CarAudioZone secondaryZone = zones.get(2);
        CarVolumeGroup secondaryVolumeGroup = secondaryZone.getCurrentVolumeGroups()[0];
        List<String> secondaryAddresses = secondaryVolumeGroup.getAddresses();
        expectWithMessage("Secondary zone addresses")
                .that(secondaryAddresses).containsExactly(BUS_100_ADDRESS);
    }

    @Test
    public void loadAudioZones_versionTwoParsesAddresses() throws Exception {
        try (InputStream versionTwoStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_V2)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    versionTwoStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);

            SparseArray<CarAudioZone> zones = cazh.loadAudioZones();

            CarAudioZone primaryZone = zones.get(0);
            CarVolumeGroup volumeGroup = primaryZone.getCurrentVolumeGroups()[0];
            List<String> addresses = volumeGroup.getAddresses();
            expectWithMessage("Primary zone addresses")
                    .that(addresses).containsExactly(BUS_0_ADDRESS, BUS_3_ADDRESS);
        }
    }

    @Test
    public void loadAudioZones_parsesContexts() throws Exception {
        CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                mInputStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                /* useFadeManagerConfiguration= */ false,
                /* carAudioFadeConfigurationHelper= */ null);

        SparseArray<CarAudioZone> zones = cazh.loadAudioZones();

        CarVolumeGroup volumeGroup = zones.get(0).getCurrentVolumeGroups()[0];
        expectWithMessage("Contexts of primary zone address " + BUS_0_ADDRESS)
                .that(volumeGroup.getContextsForAddress(BUS_0_ADDRESS))
                .containsExactly(TEST_CAR_AUDIO_CONTEXT.getContextForAttributes(CarAudioContext
                        .getAudioAttributeFromUsage(AudioAttributes.USAGE_MEDIA)));
        CarVolumeGroup rseVolumeGroup = zones.get(2).getCurrentVolumeGroups()[0];
        List<Integer> contextForBusList = rseVolumeGroup.getContextsForAddress(BUS_100_ADDRESS);
        expectWithMessage("Contexts of secondary zone address" + BUS_100_ADDRESS)
                .that(contextForBusList).containsExactlyElementsIn(TEST_CAR_AUDIO_CONTEXT
                .getAllContextsIds());
    }

    @Test
    public void getCarAudioContext_withOEMContexts() throws Exception {
        try (InputStream oemDefinedContextStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_using_oem_defined_context)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    oemDefinedContextStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);
            cazh.loadAudioZones();

            CarAudioContext contexts = cazh.getCarAudioContext();

            assertWithMessage("OEM defined contexts")
                    .that(contexts.getContextsInfo()).containsExactly(OEM_CONTEXT_INFO_MUSIC,
                            OEM_CONTEXT_INFO_NAVIGATION, OEM_CONTEXT_INFO_VOICE_COMMAND,
                            OEM_CONTEXT_INFO_CALL_RING, OEM_CONTEXT_INFO_CALL,
                            OEM_CONTEXT_INFO_ALARM, OEM_CONTEXT_INFO_NOTIFICATION,
                            OEM_CONTEXT_INFO_SYSTEM_SOUND, OEM_CONTEXT_INFO_EMERGENCY,
                            OEM_CONTEXT_INFO_SAFETY, OEM_CONTEXT_INFO_VEHICLE_STATUS,
                            OEM_CONTEXT_INFO_ANNOUNCEMENT);
        }
    }

    @Test
    public void loadAudioZones_versionTwoParsesContexts() throws Exception {
        try (InputStream versionTwoStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_V2)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    versionTwoStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);

            SparseArray<CarAudioZone> zones = cazh.loadAudioZones();

            CarVolumeGroup volumeGroup = zones.get(0).getCurrentVolumeGroups()[0];
            expectWithMessage("Contexts of primary zone address in version-two configuration"
                    + BUS_0_ADDRESS).that(volumeGroup.getContextsForAddress(BUS_0_ADDRESS))
                    .containsExactly(TEST_CAR_AUDIO_CONTEXT.getContextForAttributes(CarAudioContext
                            .getAudioAttributeFromUsage(AudioAttributes.USAGE_MEDIA)));
            CarVolumeGroup rseVolumeGroup = zones.get(2).getCurrentVolumeGroups()[0];
            List<Integer> contextForBus100List =
                    rseVolumeGroup.getContextsForAddress(BUS_100_ADDRESS);
            expectWithMessage("Contexts of secondary zone address in version-two configuration"
                    + BUS_100_ADDRESS).that(contextForBus100List)
                    .containsExactlyElementsIn(TEST_CAR_AUDIO_CONTEXT.getAllContextsIds());
        }
    }

    @Test
    public void loadAudioZones_forVersionOne_bindsNonLegacyContextsToDefault() throws Exception {
        try (InputStream versionOneStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_V1)) {

            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager,
                    mCarAudioSettings, versionOneStream,
                    mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);

            SparseArray<CarAudioZone> zones = cazh.loadAudioZones();

            CarAudioZone defaultZone = zones.get(0);
            CarVolumeGroup volumeGroup = defaultZone.getCurrentVolumeGroups()[0];
            List<Integer> audioContexts = Arrays.stream(volumeGroup.getContexts()).boxed()
                    .collect(Collectors.toList());

            assertThat(audioContexts).containsAtLeast(TEST_DEFAULT_CONTEXT_ID,
                    TEST_EMERGENCY_CONTEXT_ID, TEST_SAFETY_CONTEXT_ID,
                    TEST_VEHICLE_STATUS_CONTEXT_ID, TEST_ANNOUNCEMENT_CONTEXT_ID);
        }
    }

    @Test
    public void loadAudioZones_forVersionOneWithNonLegacyContexts_throws() throws Exception {
        try (InputStream v1NonLegacyContextStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_V1_with_non_legacy_contexts)) {

            CarAudioZonesHelper cazh =
                    new CarAudioZonesHelper(mAudioManager,
                            mCarAudioSettings, v1NonLegacyContextStream,
                            mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                            mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                            /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                            /* useFadeManagerConfiguration= */ false,
                            /* carAudioFadeConfigurationHelper= */ null);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    cazh::loadAudioZones);

            assertThat(exception).hasMessageThat().contains("Non-legacy audio contexts such as");
        }
    }

    @Test
    public void loadAudioZones_passesOnMissingAudioZoneIdForPrimary() throws Exception {
        try (InputStream missingAudioZoneIdStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_no_audio_zone_id_for_primary_zone)) {
            CarAudioZonesHelper cazh =
                    new CarAudioZonesHelper(mAudioManager,
                            mCarAudioSettings, missingAudioZoneIdStream,
                            mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                            mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                            /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                            /* useFadeManagerConfiguration= */ false,
                            /* carAudioFadeConfigurationHelper= */ null);

            SparseArray<CarAudioZone> zones = cazh.loadAudioZones();

            assertThat(zones.size()).isEqualTo(2);
            assertThat(zones.contains(PRIMARY_AUDIO_ZONE)).isTrue();
            assertThat(zones.contains(SECONDARY_ZONE_ID)).isTrue();
        }
    }

    @Test
    public void loadAudioZones_versionOneFailsOnAudioZoneId() throws Exception {
        try (InputStream versionOneAudioZoneIdStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_V1_with_audio_zone_id)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager,
                    mCarAudioSettings, versionOneAudioZoneIdStream, mCarAudioOutputDeviceInfos,
                    mInputAudioDeviceInfos, mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);
            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class,
                            () -> cazh.loadAudioZones());
            assertThat(thrown).hasMessageThat().contains("Invalid audio attribute audioZoneId");
        }
    }

    @Test
    public void loadAudioZones_versionOneFailsOnOccupantZoneId() throws Exception {
        try (InputStream versionOneOccupantIdStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_V1_with_occupant_zone_id)) {
            CarAudioZonesHelper cazh =
                    new CarAudioZonesHelper(mAudioManager,
                            mCarAudioSettings, versionOneOccupantIdStream,
                            mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                            mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                            /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                            /* useFadeManagerConfiguration= */ false,
                            /* carAudioFadeConfigurationHelper= */ null);
            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class,
                            () -> cazh.loadAudioZones());
            assertThat(thrown).hasMessageThat().contains("Invalid audio attribute occupantZoneId");
        }
    }

    @Test
    public void loadAudioZones_primaryZoneHasInputDevices() throws Exception {
        CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                mInputStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                /* useFadeManagerConfiguration= */ false,
                /* carAudioFadeConfigurationHelper= */ null);

        SparseArray<CarAudioZone> zones = cazh.loadAudioZones();

        CarAudioZone primaryZone = zones.get(PRIMARY_AUDIO_ZONE);
        expectWithMessage("Input devices for primary zone")
                .that(primaryZone.getInputAudioDevices()).hasSize(2);
    }

    @Test
    public void loadAudioZones_versionTwoPrimaryZoneHasInputDevices() throws Exception {
        try (InputStream versionTwoStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_V2)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    versionTwoStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);

            SparseArray<CarAudioZone> zones = cazh.loadAudioZones();

            CarAudioZone primaryZone = zones.get(PRIMARY_AUDIO_ZONE);
            expectWithMessage("Input devices for primary zone in version-two configuration")
                    .that(primaryZone.getInputAudioDevices()).hasSize(2);
        }
    }

    @Test
    public void loadAudioZones_primaryZoneHasMicrophoneDevice() throws Exception {
        CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                mInputStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                /* useFadeManagerConfiguration= */ false,
                /* carAudioFadeConfigurationHelper= */ null);

        SparseArray<CarAudioZone> zones = cazh.loadAudioZones();

        CarAudioZone primaryZone = zones.get(PRIMARY_AUDIO_ZONE);
        for (AudioDeviceAttributes info : primaryZone.getInputAudioDevices()) {
            assertThat(info.getType()).isEqualTo(TYPE_BUILTIN_MIC);
        }
    }

    @Test
    public void loadAudioZones_versionTwoPrimaryZoneHasMicrophoneDevice() throws Exception {
        try (InputStream versionTwoStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_V2)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    versionTwoStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);

            SparseArray<CarAudioZone> zones = cazh.loadAudioZones();

            CarAudioZone primaryZone = zones.get(PRIMARY_AUDIO_ZONE);
            for (AudioDeviceAttributes info : primaryZone.getInputAudioDevices()) {
                expectWithMessage("Type of primary zone device attribute " + info)
                        .that(info.getType()).isEqualTo(TYPE_BUILTIN_MIC);
            }
        }
    }

    @Test
    public void loadAudioZones_parsesInputDevices() throws Exception {
        try (InputStream inputDevicesStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_with_input_devices)) {
            CarAudioZonesHelper cazh =
                    new CarAudioZonesHelper(mAudioManager,
                            mCarAudioSettings, inputDevicesStream,
                            mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                            mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                            /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                            /* useFadeManagerConfiguration= */ false,
                            /* carAudioFadeConfigurationHelper= */ null);

            SparseArray<CarAudioZone> zones = cazh.loadAudioZones();

            CarAudioZone primaryZone = zones.get(0);
            List<AudioDeviceAttributes> primaryZoneInputDevices =
                    primaryZone.getInputAudioDevices();
            assertThat(primaryZoneInputDevices).hasSize(2);

            List<String> primaryZoneInputAddresses =
                    primaryZoneInputDevices.stream().map(a -> a.getAddress()).collect(
                            Collectors.toList());
            assertThat(primaryZoneInputAddresses).containsExactly(PRIMARY_ZONE_FM_TUNER_ADDRESS,
                    PRIMARY_ZONE_MICROPHONE_ADDRESS).inOrder();

            CarAudioZone secondaryZone = zones.get(1);
            List<AudioDeviceAttributes> secondaryZoneInputDevices =
                    secondaryZone.getInputAudioDevices();
            List<String> secondaryZoneInputAddresses =
                    secondaryZoneInputDevices.stream().map(a -> a.getAddress()).collect(
                            Collectors.toList());
            assertThat(secondaryZoneInputAddresses).containsExactly(
                    SECONDARY_ZONE_BUS_1000_INPUT_ADDRESS,
                    SECONDARY_ZONE_BACK_MICROPHONE_ADDRESS).inOrder();
        }
    }

    @Test
    public void loadAudioZones_failsOnDuplicateOccupantZoneId() throws Exception {
        try (InputStream duplicateOccupantZoneIdStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_duplicate_occupant_zone_id)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager,
                    mCarAudioSettings, duplicateOccupantZoneIdStream, mCarAudioOutputDeviceInfos,
                    mInputAudioDeviceInfos, mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);
            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class,
                            () -> cazh.loadAudioZones());
            assertThat(thrown).hasMessageThat().contains("already associated with a zone");
        }
    }

    @Test
    public void loadAudioZones_failsOnDuplicateAudioZoneId() throws Exception {
        try (InputStream duplicateAudioZoneIdStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_duplicate_audio_zone_id)) {
            CarAudioZonesHelper cazh =
                    new CarAudioZonesHelper(mAudioManager,
                            mCarAudioSettings, duplicateAudioZoneIdStream,
                            mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                            mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                            /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                            /* useFadeManagerConfiguration= */ false,
                            /* carAudioFadeConfigurationHelper= */ null);
            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class,
                            () -> cazh.loadAudioZones());
            assertThat(thrown).hasMessageThat().contains("already associated with a zone");
        }
    }

    @Test
    public void loadAudioZones_failsOnDuplicateZoneConfigName() throws Exception {
        try (InputStream duplicateZoneConfigNameStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_duplicate_zone_config_name)) {
            CarAudioZonesHelper cazh =
                    new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                            duplicateZoneConfigNameStream, mCarAudioOutputDeviceInfos,
                            mInputAudioDeviceInfos, mServiceEventLogger,
                            /* useCarVolumeGroupMute= */ false, /* useCoreAudioVolume= */ false,
                            /* useCoreAudioRouting= */ false,
                            /* useFadeManagerConfiguration= */ false,
                            /* carAudioFadeConfigurationHelper= */ null);

            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class, () -> cazh.loadAudioZones());

            expectWithMessage("Exception for duplicate zone config name").that(thrown)
                    .hasMessageThat().contains("can not repeat.");
        }
    }

    @Test
    public void loadAudioZones_failsOnEmptyZoneConfigName() throws Exception {
        try (InputStream emptyZoneConfigNameStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_empty_zone_config_name)) {
            CarAudioZonesHelper cazh =
                    new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                            emptyZoneConfigNameStream, mCarAudioOutputDeviceInfos,
                            mInputAudioDeviceInfos, mServiceEventLogger,
                            /* useCarVolumeGroupMute= */ false, /* useCoreAudioVolume= */ false,
                            /* useCoreAudioRouting= */ false,
                            /* useFadeManagerConfiguration= */ false,
                            /* carAudioFadeConfigurationHelper= */ null);

            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class, () -> cazh.loadAudioZones());

            expectWithMessage("Exception for empty zone config name").that(thrown)
                    .hasMessageThat().contains("empty.");
        }
    }

    @Test
    public void loadAudioZones_failsOnMissingZoneConfigName() throws Exception {
        try (InputStream missingZoneConfigNameStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_missing_zone_config_name)) {
            CarAudioZonesHelper cazh =
                    new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                            missingZoneConfigNameStream, mCarAudioOutputDeviceInfos,
                            mInputAudioDeviceInfos, mServiceEventLogger,
                            /* useCarVolumeGroupMute= */ false, /* useCoreAudioVolume= */ false,
                            /* useCoreAudioRouting= */ false,
                            /* useFadeManagerConfiguration= */ false,
                            /* carAudioFadeConfigurationHelper= */ null);

            NullPointerException thrown =
                    assertThrows(NullPointerException.class, () -> cazh.loadAudioZones());

            expectWithMessage("Exception for missing zone config name").that(thrown)
                    .hasMessageThat().contains("must be present.");
        }
    }

    @Test
    public void loadAudioZones_failsOnPrimaryZoneWithMultipleConfigs() throws Exception {
        try (InputStream missingZoneConfigNameStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_primary_zone_with_multiple_configs)) {
            CarAudioZonesHelper cazh =
                    new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                            missingZoneConfigNameStream, mCarAudioOutputDeviceInfos,
                            mInputAudioDeviceInfos, mServiceEventLogger,
                            /* useCarVolumeGroupMute= */ false, /* useCoreAudioVolume= */ false,
                            /* useCoreAudioRouting= */ false,
                            /* useFadeManagerConfiguration= */ false,
                            /* carAudioFadeConfigurationHelper= */ null);

            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class, () -> cazh.loadAudioZones());

            expectWithMessage("Exception for multiple configurations in primary zone").that(thrown)
                    .hasMessageThat().contains(
                            "Primary zone cannot have multiple zone configurations");
        }
    }

    @Test
    public void loadAudioZones_failsOnEmptyInputDeviceAddress() throws Exception {
        try (InputStream inputDevicesStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_empty_input_device)) {
            CarAudioZonesHelper cazh =
                    new CarAudioZonesHelper(mAudioManager,
                            mCarAudioSettings, inputDevicesStream,
                            mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                            mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                            /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                            /* useFadeManagerConfiguration= */ false,
                            /* carAudioFadeConfigurationHelper= */ null);

            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class,
                            () -> cazh.loadAudioZones());
            assertThat(thrown).hasMessageThat().contains("empty.");
        }
    }

    @Test
    public void loadAudioZones_failsOnNonNumericalAudioZoneId() throws Exception {
        try (InputStream nonNumericalStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_non_numerical_audio_zone_id)) {
            CarAudioZonesHelper cazh =
                    new CarAudioZonesHelper(mAudioManager,
                            mCarAudioSettings, nonNumericalStream,
                            mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                            mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                            /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                            /* useFadeManagerConfiguration= */ false,
                            /* carAudioFadeConfigurationHelper= */ null);
            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class,
                            () -> cazh.loadAudioZones());
            assertThat(thrown).hasMessageThat().contains("was \"primary\" instead.");
        }
    }

    @Test
    public void loadAudioZones_failsOnNegativeAudioZoneId() throws Exception {
        try (InputStream negativeAudioZoneIdStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_negative_audio_zone_id)) {
            CarAudioZonesHelper cazh =
                    new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                            negativeAudioZoneIdStream,
                            mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                            mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                            /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                            /* useFadeManagerConfiguration= */ false,
                            /* carAudioFadeConfigurationHelper= */ null);
            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class,
                            () -> cazh.loadAudioZones());
            assertThat(thrown).hasMessageThat().contains("but was \"-1\" instead.");
        }
    }

    @Test
    public void loadAudioZones_failsOnMissingInputDevice() throws Exception {
        try (InputStream inputDevicesStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_missing_address)) {
            CarAudioZonesHelper cazh =
                    new CarAudioZonesHelper(mAudioManager,
                            mCarAudioSettings, inputDevicesStream,
                            mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                            mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                            /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                            /* useFadeManagerConfiguration= */ false,
                            /* carAudioFadeConfigurationHelper= */ null);

            NullPointerException thrown =
                    assertThrows(NullPointerException.class,
                            () -> cazh.loadAudioZones());
            assertThat(thrown).hasMessageThat().contains("must be present.");
        }
    }

    @Test
    public void loadAudioZones_failsOnNonNumericalOccupantZoneId() throws Exception {
        try (InputStream nonNumericalStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_non_numerical_occupant_zone_id)) {
            CarAudioZonesHelper cazh =
                    new CarAudioZonesHelper(mAudioManager,
                            mCarAudioSettings, nonNumericalStream,
                            mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                            mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                            /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                            /* useFadeManagerConfiguration= */ false,
                            /* carAudioFadeConfigurationHelper= */ null);
            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class,
                            () -> cazh.loadAudioZones());
            assertThat(thrown).hasMessageThat().contains("was \"one\" instead.");
        }
    }

    @Test
    public void loadAudioZones_failsOnNegativeOccupantZoneId() throws Exception {
        try (InputStream negativeOccupantZoneIdStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_negative_occupant_zone_id)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager,
                    mCarAudioSettings,
                    negativeOccupantZoneIdStream, mCarAudioOutputDeviceInfos,
                    mInputAudioDeviceInfos, mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);
            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class,
                            () -> cazh.loadAudioZones());
            assertThat(thrown).hasMessageThat().contains("was \"-1\" instead.");
        }
    }

    @Test
    public void loadAudioZones_failsOnNonExistentInputDevice() throws Exception {
        try (InputStream inputDevicesStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_non_existent_input_device)) {
            CarAudioZonesHelper cazh =
                    new CarAudioZonesHelper(mAudioManager,
                            mCarAudioSettings, inputDevicesStream,
                            mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                            mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                            /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                            /* useFadeManagerConfiguration= */ false,
                            /* carAudioFadeConfigurationHelper= */ null);

            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class,
                            () -> cazh.loadAudioZones());
            assertThat(thrown).hasMessageThat().contains("does not exist");
        }
    }

    @Test
    public void loadAudioZones_failsOnEmptyOccupantZoneId() throws Exception {
        try (InputStream emptyOccupantZoneIdStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_empty_occupant_zone_id)) {
            CarAudioZonesHelper cazh =
                    new CarAudioZonesHelper(mAudioManager,
                            mCarAudioSettings, emptyOccupantZoneIdStream,
                            mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                            mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                            /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                            /* useFadeManagerConfiguration= */ false,
                            /* carAudioFadeConfigurationHelper= */ null);
            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class,
                            () -> cazh.loadAudioZones());
            assertThat(thrown).hasMessageThat().contains("but was \"\" instead.");
        }
    }

    @Test
    public void loadAudioZones_failsOnNonZeroAudioZoneIdForPrimary() throws Exception {
        try (InputStream nonZeroForPrimaryStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_primary_zone_with_non_zero_audio_zone_id)) {
            CarAudioZonesHelper cazh =
                    new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                            nonZeroForPrimaryStream,
                            mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                            mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                            /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                            /* useFadeManagerConfiguration= */ false,
                            /* carAudioFadeConfigurationHelper= */ null);
            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class,
                            () -> cazh.loadAudioZones());
            assertThat(thrown).hasMessageThat().contains("it can be left empty.");
        }
    }

    @Test
    public void loadAudioZones_failsOnZeroAudioZoneIdForSecondary() throws Exception {
        try (InputStream zeroZoneIdForSecondaryStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_non_primary_zone_with_primary_audio_zone_id)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager,
                    mCarAudioSettings,
                    zeroZoneIdForSecondaryStream, mCarAudioOutputDeviceInfos,
                    mInputAudioDeviceInfos, mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);
            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class,
                            () -> cazh.loadAudioZones());
            assertThat(thrown).hasMessageThat().contains(PRIMARY_ZONE_NAME);
        }
    }

    @Test
    public void loadAudioZones_failsOnRepeatedInputDevice() throws Exception {
        try (InputStream inputDevicesStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_repeat_input_device)) {
            CarAudioZonesHelper cazh =
                    new CarAudioZonesHelper(mAudioManager,
                            mCarAudioSettings, inputDevicesStream,
                            mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                            mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                            /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                            /* useFadeManagerConfiguration= */ false,
                            /* carAudioFadeConfigurationHelper= */ null);

            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class,
                            () -> cazh.loadAudioZones());
            assertThat(thrown).hasMessageThat().contains("can not repeat.");
        }
    }

    @Test
    public void loadAudioZones_failsOnMissingOutputDevice() throws Exception {
        try (InputStream outputDevicesStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_output_address_does_not_exist)) {
            CarAudioZonesHelper cazh =
                    new CarAudioZonesHelper(mAudioManager,
                            mCarAudioSettings, outputDevicesStream,
                            mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                            mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                            /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                            /* useFadeManagerConfiguration= */ false,
                            /* carAudioFadeConfigurationHelper= */ null);

            IllegalStateException thrown =
                    assertThrows(IllegalStateException.class,
                            () -> cazh.loadAudioZones());
            assertThat(thrown).hasMessageThat().contains(BUS_1000_ADDRESS_DOES_NOT_EXIST);
        }
    }

    @Test
    public void loadAudioZones_usingCoreAudioVersionThreeParsesAllZones() throws Exception {
        try (InputStream versionOneStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_using_core_routing_and_volume)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    versionOneStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ true, /* useCoreAudioRouting= */ true,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);

            SparseArray<CarAudioZone> zones = cazh.loadAudioZones();

            assertThat(zones.size()).isEqualTo(1);
        }
    }

    @Test
    public void loadAudioZones_usingCoreAudioVersionThree_failsOnFirstInvalidAttributes()
            throws Exception {
        try (InputStream versionOneStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_using_core_routing_and_volume_invalid_strategy)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    versionOneStream,  mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ true, /* useCoreAudioRouting= */ true,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);
            AudioAttributes unsupportedAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();

            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class, () -> cazh.loadAudioZones());

            assertWithMessage("First unsupported attributes exception").that(thrown)
                    .hasMessageThat().contains("Invalid attributes " + unsupportedAttributes
                            + " for context: OEM_CONTEXT");
        }
    }

    @Test
    public void loadAudioZones_usingCoreAudioVersionThree_failsOnInvalidAttributes()
            throws Exception {
        try (InputStream versionOneStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_using_core_routing_and_volume_invalid_strategy_2)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    versionOneStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ true, /* useCoreAudioRouting= */ true,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);
            AudioAttributes unsupportedAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();

            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class, () -> cazh.loadAudioZones());

            assertWithMessage("Validation of all supported attributes exception").that(thrown)
                    .hasMessageThat().contains("Invalid attributes "
                            + unsupportedAttributes.toString() + " for context: NAV_CONTEXT");
        }
    }

    @Test
    public void loadAudioZones_usingCoreAudioVersionThree_failsOnEmptyGroupName()
            throws Exception {
        try (InputStream versionOneStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_using_core_routing_and_volume_empty_group_name)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    versionOneStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ true, /* useCoreAudioRouting= */ true,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);

            RuntimeException thrown =
                    assertThrows(RuntimeException.class, () -> cazh.loadAudioZones());

            assertWithMessage("Empty group name exception").that(thrown).hasMessageThat().contains(
                    "group name attribute can not be empty when relying on core volume groups");
        }
    }

    @Test
    public void loadAudioZones_usingCoreAudioVersionThree_failsOnInvalidOrder()
            throws Exception {
        try (InputStream versionOneStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_using_core_routing_and_volume_invalid_order)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    versionOneStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ true, /* useCoreAudioRouting= */ true,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);

            RuntimeException thrown =
                    assertThrows(IllegalArgumentException.class, () -> cazh.loadAudioZones());

            expectWithMessage("After zone OEM audio contexts definition exception")
                    .that(thrown).hasMessageThat().matches("Car audio context .* is invalid");
        }
    }

    @Test
    public void loadAudioZones_usingCoreAudioVersionThree_succeedsOnAttributesWithOptionalFields()
            throws Exception {
        try (InputStream versionOneStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_using_core_routing_attr_valid_optional_fields)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    versionOneStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ true, /* useCoreAudioRouting= */ true,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);

            SparseArray<CarAudioZone> zones = cazh.loadAudioZones();

            expectWithMessage("Succeeded to parse zones").that(zones.size()).isEqualTo(1);
        }
    }

    @Test
    public void loadAudioZones_usingCoreAudioVersionThree_failsOnEmptyAttributes()
            throws Exception {
        try (InputStream versionOneStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_using_core_routing_attr_invalid_empty_fields)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    versionOneStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ true, /* useCoreAudioRouting= */ true,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);

            RuntimeException thrown =
                    assertThrows(RuntimeException.class, () -> cazh.loadAudioZones());

            expectWithMessage("Music context has empty attributes").that(thrown)
                    .hasMessageThat().contains("Empty attributes for context: MUSIC_CONTEXT");
        }
    }

    @Test
    public void getMirrorDeviceInfos() throws Exception {
        CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                mInputStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                /* useFadeManagerConfiguration= */ false,
                /* carAudioFadeConfigurationHelper= */ null);
        cazh.loadAudioZones();

        expectWithMessage("Mirror devices").that(cazh.getMirrorDeviceInfos())
                .containsExactly(mTestCarMirrorDeviceOne, mTestCarMirrorDeviceTwo);
    }

    @Test
    public void getMirrorDeviceInfos_withOutMirroringDevices() throws Exception {
        try (InputStream versionOneStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_without_mirroring)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    versionOneStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);
            cazh.loadAudioZones();

            expectWithMessage("Mirror devices for empty configuration")
                    .that(cazh.getMirrorDeviceInfos()).isEmpty();
        }
    }

    @Test
    public void loadAudioZones_failsOnMirroringDevicesInV2() throws Exception {
        try (InputStream versionOneStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_with_mirroring_V2)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    versionOneStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);

            IllegalStateException thrown = assertThrows(IllegalStateException.class,
                    () -> cazh.loadAudioZones());

            expectWithMessage("Mirror devices in v2 configuration exception")
                    .that(thrown).hasMessageThat().contains("mirroringDevices");
        }
    }

    @Test
    public void loadAudioZones_failsOnDuplicateMirroringDevices() throws Exception {
        try (InputStream versionOneStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_duplicate_mirror_devices)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    versionOneStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);

            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> cazh.loadAudioZones());

            expectWithMessage("Duplicates mirror devices in configuration exception")
                    .that(thrown).hasMessageThat().contains("can not repeat");
        }
    }

    @Test
    public void loadAudioZones_withPrimaryZoneAndDynamicAudioDevices() throws Exception {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        try (InputStream versionFourStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_with_dynamic_devices_for_primary_zone)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    versionFourStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);

            SparseArray<CarAudioZone> zones = cazh.loadAudioZones();

            expectWithMessage("Primary zone with dynamic device configurations")
                    .that(zones.size()).isEqualTo(1);
            CarAudioZone zone = zones.get(0);
            List<CarAudioZoneConfig> configs = zone.getAllCarAudioZoneConfigs();
            expectWithMessage("Configurations for primary zone with dynamic devices")
                    .that(configs).hasSize(3);
            CarAudioZoneConfig configBTMedia = configs.get(1);
            CarVolumeGroup mediaVolumeGroup = configBTMedia.getVolumeGroup("media");
            expectWithMessage("Media BT dynamic device").that(
                    mediaVolumeGroup.getAudioDeviceForContext(OEM_CONTEXT_INFO_MUSIC.getId())
                            .getType()).isEqualTo(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP);
            CarAudioZoneConfig configBTAll = configs.get(2);
            List<AudioDeviceAttributes> allDevices = configBTAll.getAudioDevice();
            expectWithMessage("BT all audio config devices").that(allDevices).hasSize(1);
            AudioDeviceAttributes btDevice = allDevices.get(0);
            expectWithMessage("BT all audio config type").that(btDevice.getType())
                    .isEqualTo(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP);
        }
    }

    @Test
    public void loadAudioZones_withDynamicAudioDevices_forVersionThree_fails() throws Exception {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        try (InputStream versionFourStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_with_dynamic_devices_for_primary_zone_in_v3)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    versionFourStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);

            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    cazh::loadAudioZones);

            expectWithMessage("Dynamic devices support in v3 exception").that(thrown)
                    .hasMessageThat().contains("Audio device type");
        }
    }

    @Test
    public void loadAudioZones_withPrimaryZoneAndDynamicAudioDevicesAndNoDynamicSupport()
            throws Exception {
        mSetFlagsRule.disableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        try (InputStream versionFourStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_with_dynamic_devices_for_primary_zone)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    versionFourStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);

            SparseArray<CarAudioZone> zones = cazh.loadAudioZones();

            expectWithMessage("Primary zone with dynamic device configurations"
                    + " and dynamic flag disabled").that(zones.size()).isEqualTo(1);
            CarAudioZone zone = zones.get(0);
            List<CarAudioZoneConfig> configs = zone.getAllCarAudioZoneConfigs();
            expectWithMessage("Configurations for primary zone with dynamic devices"
                    + " and dynamic flag disabled").that(configs).hasSize(1);
            CarAudioZoneConfig defaultConfig = configs.get(0);
            expectWithMessage("Default configuration for dynamic configuration with dynamic"
                    + " devices disabled").that(defaultConfig.isDefault()).isTrue();
        }
    }

    @Test
    public void loadAudioZones_withMinMaxActivationVolumeAndNoActivationVolumeSupport()
            throws Exception {
        mSetFlagsRule.disableFlags(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME);
        try (InputStream versionFourStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_with_min_max_activation_volume)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    versionFourStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);

            SparseArray<CarAudioZone> zones = cazh.loadAudioZones();

            CarAudioZoneConfig zoneConfig = zones.get(0).getCurrentCarAudioZoneConfig();
            CarVolumeGroup[] volumeGroups = zoneConfig.getVolumeGroups();
            expectWithMessage(
                    "Primary zone volume group 0 min activation volume with disabled flag")
                    .that(volumeGroups[0].getMinActivationGainIndex())
                    .isEqualTo(volumeGroups[0].getMinGainIndex());
            expectWithMessage(
                    "Primary zone volume group 0 max activation volume with disabled flag")
                    .that(volumeGroups[0].getMaxActivationGainIndex())
                    .isEqualTo(volumeGroups[0].getMaxGainIndex());
            expectWithMessage(
                    "Primary zone volume group 1 min activation volume with disabled flag")
                    .that(volumeGroups[1].getMinActivationGainIndex())
                    .isEqualTo(volumeGroups[1].getMinGainIndex());
            expectWithMessage(
                    "Primary zone volume group 1 max activation volume with disabled flag")
                    .that(volumeGroups[1].getMaxActivationGainIndex())
                    .isEqualTo(volumeGroups[1].getMaxGainIndex());
        }
    }

    @Test
    public void loadAudioZones_withMinMaxActivationVolume() throws Exception {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME);
        try (InputStream versionFourStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_with_min_max_activation_volume)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    versionFourStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);

            SparseArray<CarAudioZone> zones = cazh.loadAudioZones();

            CarAudioZoneConfig zoneConfig = zones.get(0).getCurrentCarAudioZoneConfig();
            CarVolumeGroup[] volumeGroups = zoneConfig.getVolumeGroups();
            expectWithMessage("Primary zone volume group 0 min activation volume")
                    .that(volumeGroups[0].getMinActivationGainIndex())
                    .isGreaterThan(volumeGroups[0].getMinGainIndex());
            expectWithMessage("Primary zone volume group 0 max activation volume")
                    .that(volumeGroups[0].getMaxActivationGainIndex())
                    .isLessThan(volumeGroups[0].getMaxGainIndex());
            expectWithMessage("Primary zone volume group 1 min activation volume")
                    .that(volumeGroups[1].getMinActivationGainIndex())
                    .isEqualTo(volumeGroups[1].getMinGainIndex());
            expectWithMessage("Primary zone volume group 1 max activation volume")
                    .that(volumeGroups[1].getMaxActivationGainIndex())
                    .isEqualTo(volumeGroups[1].getMaxGainIndex());
        }
    }

    @Test
    public void loadAudioZones_withMinMaxActivationVolume_forVersionThree_fails()
            throws Exception {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME);
        try (InputStream versionFourStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_with_min_max_activation_volume_in_v3)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    versionFourStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);

            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    cazh::loadAudioZones);

            expectWithMessage("Min/max activation volume support in v3 exception")
                    .that(thrown).hasMessageThat().contains("not supported for versions less than");
        }
    }

    @Test
    public void loadAudioZones_withMinMaxActivationVolumeOutOfRange_fails() throws Exception {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME);
        try (InputStream versionFourStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_with_min_max_activation_volume_out_of_range)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    versionFourStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);

            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    cazh::loadAudioZones);

            expectWithMessage("Min/max activation volume out of range exception")
                    .that(thrown).hasMessageThat().contains("can not be outside the range");
        }
    }

    @Test
    public void loadAudioZones_withMinGreaterThanMaxActivationVolume_fails() throws Exception {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME);
        try (InputStream versionFourStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_with_min_greater_than_max_activation_volume)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    versionFourStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                    /* useFadeManagerConfiguration= */ false,
                    /* carAudioFadeConfigurationHelper= */ null);

            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    cazh::loadAudioZones);

            expectWithMessage("Min greater than max activation volume exception")
                    .that(thrown).hasMessageThat().contains("can not be larger than or equal to");
        }
    }

    @Test
    public void loadAudioZones_applyFadeConfigs_forVersionThree_fails() throws Exception {
        mSetFlagsRule.enableFlags(FLAG_ENABLE_FADE_MANAGER_CONFIGURATION);
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_FADE_MANAGER_CONFIGURATION);
        try (InputStream versionFourStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_apply_fade_configs_in_v3)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    versionFourStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                    /* useFadeManagerConfiguration= */ true,
                    /* carAudioFadeConfigurationHelper= */ null);

            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class, cazh::loadAudioZones);

            expectWithMessage("Fade configs in v3 exception").that(thrown)
                    .hasMessageThat().contains("Fade configurations not");
        }
    }

    @Test
    public void loadAudioZones_applyFadeConfigs_nullCarAudioFadeConfigurationHelper_fails()
            throws Exception {
        mSetFlagsRule.enableFlags(FLAG_ENABLE_FADE_MANAGER_CONFIGURATION);
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_FADE_MANAGER_CONFIGURATION);
        try (InputStream versionFourStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_apply_fade_configs)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    versionFourStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                    /* useFadeManagerConfiguration= */ true,
                    /* carAudioFadeConfigurationHelper= */ null);

            NullPointerException thrown =
                    assertThrows(NullPointerException.class, () -> cazh.loadAudioZones());

            expectWithMessage("Fade configs with null car audio fade config helper exception")
                    .that(thrown).hasMessageThat()
                    .contains("Car audio fade configuration helper can not be");
        }
    }

    @Test
    public void loadAudioZones_applyFadeConfigs_withEmptyAddress_fails() throws Exception {
        mSetFlagsRule.enableFlags(FLAG_ENABLE_FADE_MANAGER_CONFIGURATION);
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_FADE_MANAGER_CONFIGURATION);
        CarAudioFadeConfigurationHelper fadeConfiguration = getCarAudioFadeConfigurationHelper(
                R.raw.car_audio_fade_configuration);
        try (InputStream versionFourStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_apply_fade_configs_with_empty_address)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    versionFourStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                    /* useFadeManagerConfiguration= */ true, fadeConfiguration);

            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class, cazh::loadAudioZones);

            expectWithMessage("Fade configs with empty config name exception").that(thrown)
                    .hasMessageThat().contains("Fade config name");
        }
    }

    @Test
    public void loadAudioZones_applyFadeConfigs_withNeitherDefaultNorAudioAttributes_fails()
            throws Exception {
        mSetFlagsRule.enableFlags(FLAG_ENABLE_FADE_MANAGER_CONFIGURATION);
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_FADE_MANAGER_CONFIGURATION);
        CarAudioFadeConfigurationHelper fadeConfiguration = getCarAudioFadeConfigurationHelper(
                R.raw.car_audio_fade_configuration);
        try (InputStream versionFourStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_apply_fade_configs_with_neitherDefaultNorTransient)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    versionFourStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                    /* useFadeManagerConfiguration= */ true, fadeConfiguration);

            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class, cazh::loadAudioZones);

            expectWithMessage("Fade configs with neither default or audio attributes").that(thrown)
                    .hasMessageThat().contains("Transient fade configs must have valid");
        }
    }

    @Test
    public void loadAudioZones_applyFadeConfigs_withUnavailableConfig_fails()
            throws Exception {
        mSetFlagsRule.enableFlags(FLAG_ENABLE_FADE_MANAGER_CONFIGURATION);
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_FADE_MANAGER_CONFIGURATION);
        CarAudioFadeConfigurationHelper fadeConfiguration = getCarAudioFadeConfigurationHelper(
                R.raw.car_audio_fade_configuration);
        try (InputStream versionFourStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_apply_fade_configs_with_unavailable_config)) {
            CarAudioZonesHelper cazh = new CarAudioZonesHelper(mAudioManager, mCarAudioSettings,
                    versionFourStream, mCarAudioOutputDeviceInfos, mInputAudioDeviceInfos,
                    mServiceEventLogger, /* useCarVolumeGroupMute= */ false,
                    /* useCoreAudioVolume= */ false, /* useCoreAudioRouting= */ false,
                    /* useFadeManagerConfiguration= */ true, fadeConfiguration);

            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class, cazh::loadAudioZones);

            expectWithMessage("Fade configs with unavailable config exception").that(thrown)
                    .hasMessageThat().contains("No config available");
        }
    }

    private CarAudioFadeConfigurationHelper getCarAudioFadeConfigurationHelper(int resource) {
        try (InputStream inputStream = mContext.getResources().openRawResource(resource)) {
            return new CarAudioFadeConfigurationHelper(inputStream);
        } catch (IOException | XmlPullParserException e) {
            throw new RuntimeException("Failed to parse audio fade configuration", e);
        }
    }

    private void setupAudioManagerMock() {
        doReturn(CoreAudioRoutingUtils.getProductStrategies())
                .when(() -> AudioManager.getAudioProductStrategies());
        doReturn(CoreAudioRoutingUtils.getVolumeGroups())
                .when(() -> AudioManager.getAudioVolumeGroups());

        doReturn(CoreAudioRoutingUtils.MUSIC_GROUP_ID)
                .when(() -> CoreAudioHelper.getVolumeGroupIdForAudioAttributes(
                        CoreAudioRoutingUtils.MUSIC_ATTRIBUTES));
        doReturn(CoreAudioRoutingUtils.MUSIC_ATTRIBUTES)
                .when(() -> CoreAudioHelper.selectAttributesForVolumeGroupName(
                        CoreAudioRoutingUtils.MUSIC_GROUP_NAME));

        doReturn(CoreAudioRoutingUtils.NAV_GROUP_ID)
                .when(() -> CoreAudioHelper.getVolumeGroupIdForAudioAttributes(
                        CoreAudioRoutingUtils.NAV_ATTRIBUTES));
        doReturn(CoreAudioRoutingUtils.NAV_ATTRIBUTES)
                .when(() -> CoreAudioHelper.selectAttributesForVolumeGroupName(
                        CoreAudioRoutingUtils.NAV_GROUP_NAME));

        doReturn(CoreAudioRoutingUtils.OEM_GROUP_ID)
                .when(() -> CoreAudioHelper.getVolumeGroupIdForAudioAttributes(
                        CoreAudioRoutingUtils.OEM_ATTRIBUTES));
        doReturn(CoreAudioRoutingUtils.OEM_ATTRIBUTES)
                .when(() -> CoreAudioHelper.selectAttributesForVolumeGroupName(
                        CoreAudioRoutingUtils.OEM_GROUP_NAME));
    }
}
