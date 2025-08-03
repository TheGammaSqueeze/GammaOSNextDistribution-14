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
import static android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC;
import static android.media.AudioDeviceInfo.TYPE_BUS;
import static android.media.AudioDeviceInfo.TYPE_FM_TUNER;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.car.test.AbstractExpectableTestCase;
import android.media.AudioDeviceAttributes;
import android.util.SparseArray;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CarAudioZonesValidatorTest extends AbstractExpectableTestCase {

    @Test
    public void validate_thereIsAtLeastOneZone() {
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> CarAudioZonesValidator.validate(new SparseArray<CarAudioZone>(),
                        /* useCoreAudioRouting= */ false));

        expectWithMessage("Empty zones exception").that(exception).hasMessageThat()
                .contains("At least one zone should be defined");

    }

    @Test
    public void validate_failsOnEmptyInputDevices() {
        CarAudioZone zone = new MockBuilder().withInputDevices(new ArrayList<>()).build();
        SparseArray<CarAudioZone> zones = new SparseArray<>();
        zones.put(zone.getId(), zone);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> CarAudioZonesValidator.validate(zones, /* useCoreAudioRouting= */ false));

        expectWithMessage("Empty input devices exception").that(exception).hasMessageThat()
                .contains("Primary Zone Input Devices");
    }

    @Test
    public void validate_failsOnNullInputDevices() {
        CarAudioZone zone = new MockBuilder().withInputDevices(null).build();
        SparseArray<CarAudioZone> zones = new SparseArray<>();
        zones.put(zone.getId(), zone);

        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> CarAudioZonesValidator.validate(zones, /* useCoreAudioRouting= */ false));

        expectWithMessage("Null input devices exception").that(exception).hasMessageThat()
                .contains("Primary Zone Input Devices");
    }

    @Test
    public void validate_failsOnMissingMicrophoneInputDevices() {
        CarAudioZone zone = new MockBuilder().withInputDevices(
                List.of(generateInputAudioDeviceAttributeInfo("tuner", TYPE_FM_TUNER)))
                .build();
        SparseArray<CarAudioZone> zones = new SparseArray<>();
        zones.put(zone.getId(), zone);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> CarAudioZonesValidator.validate(zones, /* useCoreAudioRouting= */ false));

        expectWithMessage("Missing microphone exception").that(exception).hasMessageThat()
                .contains("Primary Zone must have");
    }

    @Test
    public void validate_zoneConfigsForEachZone() {
        SparseArray<CarAudioZone> zones = generateAudioZonesWithPrimary();
        CarAudioZone zoneOne = new MockBuilder()
                .withInvalidZoneConfigs()
                .withZoneId(1)
                .build();
        zones.put(zoneOne.getId(), zoneOne);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> CarAudioZonesValidator.validate(zones, /* useCoreAudioRouting= */ false));

        expectWithMessage("Invalid zone config exception").that(exception).hasMessageThat()
                .contains("Invalid zone configurations for zone " + 1);
    }

    @Test
    public void validate_addressesCanNotRepeatAcrossZones() {
        CarVolumeGroup mockVolumeGroup = generateVolumeGroup(List.of("one", "two", "three"));
        CarAudioZoneConfig primaryZoneConfig = new MockConfigBuilder()
                .withVolumeGroups(new CarVolumeGroup[]{mockVolumeGroup})
                .build();
        CarAudioZone primaryZone = new MockBuilder()
                .withZoneConfigs(List.of(primaryZoneConfig))
                .build();
        CarVolumeGroup mockSecondaryVolumeGroup = generateVolumeGroup(
                List.of("three", "four", "five"));
        CarAudioZoneConfig secondaryZoneConfig = new MockConfigBuilder()
                .withVolumeGroups(new CarVolumeGroup[]{mockSecondaryVolumeGroup})
                .build();
        CarAudioZone secondaryZone = new MockBuilder()
                .withZoneId(1)
                .withZoneConfigs(List.of(secondaryZoneConfig))
                .build();
        SparseArray<CarAudioZone> zones = new SparseArray<>();
        zones.put(primaryZone.getId(), primaryZone);
        zones.put(secondaryZone.getId(), secondaryZone);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> CarAudioZonesValidator.validate(zones, /* useCoreAudioRouting= */ false));

        expectWithMessage("Repeating addresses in zones exception").that(exception)
                .hasMessageThat().contains("repeats among multiple zones");
    }

    @Test
    public void validate_addressesCanNotRepeatAcrossConfigs() {
        CarVolumeGroup mockVolumeGroup1 = generateVolumeGroup(List.of("one", "two", "three"));
        CarVolumeGroup mockVolumeGroup2 = generateVolumeGroup(List.of("three", "four", "five"));
        CarAudioZoneConfig primaryZoneConfig = new MockConfigBuilder()
                .withVolumeGroups(new CarVolumeGroup[]{mockVolumeGroup1, mockVolumeGroup2})
                .build();
        CarAudioZone primaryZone = new MockBuilder()
                .withZoneConfigs(List.of(primaryZoneConfig))
                .build();
        SparseArray<CarAudioZone> zones = new SparseArray<>();
        zones.put(primaryZone.getId(), primaryZone);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> CarAudioZonesValidator.validate(zones, /* useCoreAudioRouting= */ false));

        expectWithMessage("Repeating addresses in same config exception").that(exception)
                .hasMessageThat().contains("multiple volume groups in the same configuration");
    }

    @Test
    public void validate_passesWithoutExceptionForValidZoneConfiguration() {
        SparseArray<CarAudioZone> zones = generateAudioZonesWithPrimary();

        CarAudioZonesValidator.validate(zones, /* useCoreAudioRouting= */ false);
    }

    @Test
    public void validate_passesWithoutExceptionForRepeatAddressInDifferentConfigs() {
        CarVolumeGroup mockVolumeGroup1 = generateVolumeGroup(List.of("BT", "two", "three"));
        CarVolumeGroup mockVolumeGroup2 = generateVolumeGroup(List.of("BT", "four", "five"));
        CarAudioZoneConfig primaryZoneConfig1 = new MockConfigBuilder()
                .withVolumeGroups(new CarVolumeGroup[]{mockVolumeGroup1})
                .build();
        CarAudioZoneConfig primaryZoneConfig2 = new MockConfigBuilder()
                .withVolumeGroups(new CarVolumeGroup[]{mockVolumeGroup2})
                .build();
        CarAudioZone primaryZone = new MockBuilder()
                .withInputDevices(getValidInputDevices())
                .withZoneConfigs(List.of(primaryZoneConfig1, primaryZoneConfig2))
                .build();
        SparseArray<CarAudioZone> zones = new SparseArray<>();
        zones.put(primaryZone.getId(), primaryZone);

        CarAudioZonesValidator.validate(zones, /* useCoreAudioRouting= */ false);
    }

    @Test
    public void validate_passesWithoutExceptionForRepeatEmptyAddress() {
        CarVolumeGroup mockVolumeGroup1 = generateVolumeGroup(List.of("BT", "one", "", ""));
        CarVolumeGroup mockVolumeGroup2 = generateVolumeGroup(List.of("BT", "", ""));
        CarAudioZoneConfig primaryZoneConfig1 = new MockConfigBuilder()
                .withVolumeGroups(new CarVolumeGroup[]{mockVolumeGroup1})
                .build();
        CarAudioZoneConfig primaryZoneConfig2 = new MockConfigBuilder()
                .withVolumeGroups(new CarVolumeGroup[]{mockVolumeGroup2})
                .build();
        CarAudioZone primaryZone = new MockBuilder()
                .withInputDevices(getValidInputDevices())
                .withZoneConfigs(List.of(primaryZoneConfig1, primaryZoneConfig2))
                .build();
        SparseArray<CarAudioZone> zones = new SparseArray<>();
        zones.put(primaryZone.getId(), primaryZone);

        CarAudioZonesValidator.validate(zones, /* useCoreAudioRouting= */ false);
    }

    private SparseArray<CarAudioZone> generateAudioZonesWithPrimary() {
        CarAudioZone zone = new MockBuilder().withInputDevices(getValidInputDevices()).build();
        SparseArray<CarAudioZone> zones = new SparseArray<>();
        zones.put(zone.getId(), zone);
        return zones;
    }

    private CarVolumeGroup generateVolumeGroup(List<String> deviceAddresses) {
        CarVolumeGroup mockVolumeGroup = Mockito.mock(CarVolumeGroup.class);
        when(mockVolumeGroup.getAddresses()).thenReturn(deviceAddresses);
        return mockVolumeGroup;
    }

    private List<AudioDeviceAttributes> getValidInputDevices() {
        return List.of(generateInputAudioDeviceAttributeInfo("mic", TYPE_BUILTIN_MIC),
                generateInputAudioDeviceAttributeInfo("tuner", TYPE_FM_TUNER),
                generateInputAudioDeviceAttributeInfo("bus", TYPE_BUS));
    }
    private static class MockBuilder {
        private boolean mHasValidZoneConfigs = true;
        private int mZoneId = PRIMARY_AUDIO_ZONE;

        private List<CarAudioZoneConfig> mZoneConfigs = new ArrayList<>();
        private List<AudioDeviceAttributes> mInputDevices = new ArrayList<>();

        CarAudioZone build() {
            CarAudioZone zoneMock = Mockito.mock(CarAudioZone.class);
            when(zoneMock.getId()).thenReturn(mZoneId);
            when(zoneMock.validateZoneConfigs(/* useCoreAudioRouting= */ false))
                    .thenReturn(mHasValidZoneConfigs);
            when(zoneMock.validateCanUseDynamicMixRouting(/* useCoreAudioRouting= */ false))
                    .thenReturn(mHasValidZoneConfigs);
            when(zoneMock.getAllCarAudioZoneConfigs()).thenReturn(mZoneConfigs);
            when(zoneMock.getInputAudioDevices()).thenReturn(mInputDevices);
            return zoneMock;
        }

        MockBuilder withInvalidZoneConfigs() {
            mHasValidZoneConfigs = false;
            return this;
        }

        MockBuilder withZoneId(int zoneId) {
            mZoneId = zoneId;
            return this;
        }

        MockBuilder withZoneConfigs(List<CarAudioZoneConfig> zoneConfigs) {
            mZoneConfigs = zoneConfigs;
            return this;
        }

        MockBuilder withInputDevices(List<AudioDeviceAttributes> inputDevices) {
            mInputDevices = inputDevices;
            return this;
        }
    }
    private static class MockConfigBuilder {
        private CarVolumeGroup[] mVolumeGroups = new CarVolumeGroup[0];

        CarAudioZoneConfig build() {
            CarAudioZoneConfig zoneConfigMock = Mockito.mock(CarAudioZoneConfig.class);
            when(zoneConfigMock.getVolumeGroups()).thenReturn(mVolumeGroups);
            return zoneConfigMock;
        }

        MockConfigBuilder withVolumeGroups(CarVolumeGroup[] volumeGroups) {
            mVolumeGroups = volumeGroups;
            return this;
        }
    }

    private AudioDeviceAttributes generateInputAudioDeviceAttributeInfo(String address, int type) {
        AudioDeviceAttributes inputMock = mock(AudioDeviceAttributes.class);
        when(inputMock.getAddress()).thenReturn(address);
        when(inputMock.getType()).thenReturn(type);
        return inputMock;
    }
}
