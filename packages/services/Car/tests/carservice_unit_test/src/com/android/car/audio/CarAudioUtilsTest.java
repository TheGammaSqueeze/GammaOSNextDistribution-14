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
import static android.media.AudioAttributes.USAGE_ASSISTANT;
import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioDeviceInfo.TYPE_AUX_LINE;
import static android.media.AudioDeviceInfo.TYPE_BLE_BROADCAST;
import static android.media.AudioDeviceInfo.TYPE_BLE_HEADSET;
import static android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER;
import static android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP;
import static android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC;
import static android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
import static android.media.AudioDeviceInfo.TYPE_BUS;
import static android.media.AudioDeviceInfo.TYPE_FM_TUNER;
import static android.media.AudioDeviceInfo.TYPE_HDMI;
import static android.media.AudioDeviceInfo.TYPE_USB_ACCESSORY;
import static android.media.AudioDeviceInfo.TYPE_USB_DEVICE;
import static android.media.AudioDeviceInfo.TYPE_USB_HEADSET;
import static android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES;
import static android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET;

import static com.android.car.audio.CarAudioUtils.excludesDynamicDevices;
import static com.android.car.audio.CarAudioUtils.getDynamicDevicesInConfig;
import static com.android.car.audio.CarAudioUtils.hasExpired;
import static com.android.car.audio.CarAudioUtils.isMicrophoneInputDevice;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.car.feature.Flags;
import android.car.media.CarAudioZoneConfigInfo;
import android.car.media.CarVolumeGroupInfo;
import android.car.test.AbstractExpectableTestCase;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.internal.util.DebugUtils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CarAudioUtilsTest extends AbstractExpectableTestCase {

    public static final String TEST_ADDRESS_1 = "test_address_1";
    public static final String TEST_ADDRESS_2 = "test_address_2";
    public static final String TEST_NOT_AVAILABLE_ADDRESS = "test_not_available_address";
    private static final AudioAttributes TEST_MEDIA_AUDIO_ATTRIBUTE =
            new AudioAttributes.Builder().setUsage(USAGE_MEDIA).build();
    private static final AudioAttributes TEST_NAV_AUDIO_ATTRIBUTE =
            new AudioAttributes.Builder().setUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).build();
    private static final AudioAttributes TEST_ASSISTANT_AUDIO_ATTRIBUTE =
            new AudioAttributes.Builder().setUsage(USAGE_ASSISTANT).build();
    private static final AudioDeviceAttributes TEST_BT_DEVICE =
            getMockDevice(/* address= */ "", TYPE_BLUETOOTH_A2DP);
    private static final AudioDeviceAttributes TEST_BUS_DEVICE_1 =
            getMockDevice(TEST_ADDRESS_1, TYPE_BUS);
    private static final AudioDeviceAttributes TEST_BUS_DEVICE_2 =
            getMockDevice(TEST_ADDRESS_2, TYPE_BUS);

    private static final AudioDeviceInfo TEST_BUS_DEVICE_1_INFO =
            getMockDeviceInfo(TEST_ADDRESS_1, TYPE_BUS);
    private static final AudioDeviceInfo TEST_BUS_DEVICE_2_INFO =
            getMockDeviceInfo(TEST_ADDRESS_2, TYPE_BUS);
    private static final AudioDeviceInfo TEST_BT_DEVICE_INFO =
            getMockDeviceInfo(/* address= */ "", TYPE_BLUETOOTH_A2DP);

    private static AudioDeviceInfo getMockDeviceInfo(String address, int type) {
        AudioDeviceInfo device = Mockito.mock(AudioDeviceInfo.class);
        when(device.getAddress()).thenReturn(address);
        when(device.getType()).thenReturn(type);
        return device;
    }

    private static final String TEST_MEDIA_GROUP_NAME = "media volume group";
    private static final String TEST_BT_GROUP_NAME = "bt volume group";
    private static final String TEST_NAV_GROUP_NAME = "nav volume group";
    private static final int TEST_ZONE_ID = 1;
    private static final int TEST_MEDIA_GROUP_ID = 0;
    private static final int TEST_BT_GROUP_ID = 1;
    private static final int TEST_NAV_GROUP_ID = 2;
    private static final int TEST_MAX_GAIN_INDEX = 100;
    private static final int TEST_MIN_GAIN_INDEX = 0;
    private static final int TEST_MAX_ACTIVATION_GAIN_INDEX = 80;
    private static final int TEST_MIN_ACTIVATION_GAIN_INDEX = 20;
    private static final CarVolumeGroupInfo TEST_MEDIA_VOLUME_INFO =
            new CarVolumeGroupInfo.Builder(TEST_MEDIA_GROUP_NAME, TEST_ZONE_ID, TEST_BT_GROUP_ID)
                    .setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                    .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX)
                    .setMaxActivationVolumeGainIndex(TEST_MAX_ACTIVATION_GAIN_INDEX)
                    .setMinActivationVolumeGainIndex(TEST_MIN_ACTIVATION_GAIN_INDEX)
                    .setAudioAttributes(List.of(TEST_MEDIA_AUDIO_ATTRIBUTE))
                    .setAudioDeviceAttributes(List.of(TEST_BUS_DEVICE_1)).build();
    private static final CarVolumeGroupInfo TEST_NAV_VOLUME_INFO =
            new CarVolumeGroupInfo.Builder(TEST_NAV_GROUP_NAME, TEST_ZONE_ID, TEST_NAV_GROUP_ID)
                    .setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                    .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX)
                    .setMaxActivationVolumeGainIndex(TEST_MAX_ACTIVATION_GAIN_INDEX)
                    .setMinActivationVolumeGainIndex(TEST_MIN_ACTIVATION_GAIN_INDEX)
                    .setAudioAttributes(List.of(TEST_NAV_AUDIO_ATTRIBUTE))
                    .setAudioDeviceAttributes(List.of(TEST_BUS_DEVICE_2)).build();
    private static final CarVolumeGroupInfo TEST_BT_VOLUME_INFO =
            new CarVolumeGroupInfo.Builder(TEST_BT_GROUP_NAME, TEST_ZONE_ID, TEST_MEDIA_GROUP_ID)
                    .setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                    .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX)
                    .setMaxActivationVolumeGainIndex(TEST_MAX_ACTIVATION_GAIN_INDEX)
                    .setMinActivationVolumeGainIndex(TEST_MIN_ACTIVATION_GAIN_INDEX)
                    .setAudioAttributes(List.of(TEST_ASSISTANT_AUDIO_ATTRIBUTE))
                    .setAudioDeviceAttributes(List.of(TEST_BT_DEVICE)).build();
    private static final int TEST_CONFIG_ID = 0;
    private static final boolean TEST_ACTIVE_STATUS = true;
    private static final boolean TEST_SELECTED_STATUS = true;
    private static final boolean TEST_DEFAULT_STATUS = true;

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Test
    public void hasExpired_forCurrentTimeBeforeTimeout() {
        expectWithMessage("Unexpired state").that(hasExpired(/*startTimeMs= */ 0,
                /*currentTimeMs= */ 100, /*timeoutMs= */ 200)).isFalse();
    }

    @Test
    public void hasExpired_forCurrentTimeAfterTimeout() {
        expectWithMessage("Expired state").that(hasExpired(/*startTimeMs= */ 0,
                /*currentTimeMs= */ 300, /*timeoutMs= */ 200)).isTrue();
    }

    @Test
    public void isMicrophoneInputDevice_forMicrophoneDevice() {
        AudioDeviceInfo deviceInfo = Mockito.mock(AudioDeviceInfo.class);
        when(deviceInfo.getType()).thenReturn(TYPE_BUILTIN_MIC);
        expectWithMessage("Microphone device").that(isMicrophoneInputDevice(deviceInfo)).isTrue();
    }

    @Test
    public void isMicrophoneInputDevice_forNonMicrophoneDevice() {
        AudioDeviceInfo deviceInfo = Mockito.mock(AudioDeviceInfo.class);
        when(deviceInfo.getType()).thenReturn(TYPE_FM_TUNER);
        expectWithMessage("Non microphone device")
                .that(isMicrophoneInputDevice(deviceInfo)).isFalse();
    }

    @Test
    public void getAudioDeviceInfo() {
        AudioDeviceInfo info1 = getTestAudioDeviceInfo(TEST_ADDRESS_1);
        AudioDeviceInfo info2 = getTestAudioDeviceInfo(TEST_ADDRESS_2);
        AudioManager audioManager = Mockito.mock(AudioManager.class);
        when(audioManager.getDevices(anyInt())).thenReturn(new AudioDeviceInfo[]{info2, info1});
        AudioDeviceAttributes attributes =
                new AudioDeviceAttributes(TYPE_BLUETOOTH_A2DP, TEST_ADDRESS_1);

        AudioDeviceInfo info = CarAudioUtils.getAudioDeviceInfo(attributes, audioManager);

        expectWithMessage("Audio device info").that(info).isEqualTo(info1);
    }

    @Test
    public void getAudioDeviceInfo_withDeviceNotAvailable() {
        AudioDeviceInfo info1 = getTestAudioDeviceInfo(TEST_ADDRESS_1);
        AudioDeviceInfo info2 = getTestAudioDeviceInfo(TEST_ADDRESS_2);
        AudioManager audioManager = Mockito.mock(AudioManager.class);
        when(audioManager.getDevices(anyInt())).thenReturn(new AudioDeviceInfo[]{info2, info1});
        AudioDeviceAttributes attributes =
                new AudioDeviceAttributes(TYPE_BLUETOOTH_A2DP, TEST_NOT_AVAILABLE_ADDRESS);

        AudioDeviceInfo info = CarAudioUtils.getAudioDeviceInfo(attributes, audioManager);

        expectWithMessage("Not available audio device info").that(info).isNull();
    }

    @Test
    public void isDynamicDeviceType_forDynamicDevices() {
        List<Integer> dynamicDevices = List.of(TYPE_WIRED_HEADSET, TYPE_WIRED_HEADPHONES,
                TYPE_BLUETOOTH_A2DP, TYPE_HDMI, TYPE_USB_ACCESSORY, TYPE_USB_DEVICE,
                TYPE_USB_HEADSET, TYPE_AUX_LINE, TYPE_BLE_HEADSET, TYPE_BLE_SPEAKER,
                TYPE_BLE_BROADCAST);

        for (int dynamicDeviceType : dynamicDevices) {
            expectWithMessage("Dynamic Audio device type %s", DebugUtils.constantToString(
                    AudioDeviceInfo.class, /* prefix= */ "TYPE_", dynamicDeviceType))
                    .that(CarAudioUtils.isDynamicDeviceType(dynamicDeviceType)).isTrue();
        }
    }

    @Test
    public void isDynamicDeviceType_forNonDynamicDevice() {
        List<Integer> dynamicDevices = List.of(TYPE_BUILTIN_SPEAKER, TYPE_BUS);

        for (int dynamicDeviceType : dynamicDevices) {
            expectWithMessage("Non dynamic audio device type %s", DebugUtils.constantToString(
                    AudioDeviceInfo.class, /* prefix= */ "TYPE_", dynamicDeviceType))
                    .that(CarAudioUtils.isDynamicDeviceType(dynamicDeviceType)).isFalse();
        }
    }

    @Test
    public void excludesDynamicDevices_withOutDynamicDevices() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioZoneConfigInfo testNoDynamicDevicesConfig =
                new CarAudioZoneConfigInfo("Non-dynamic-devices-config",
                        List.of(TEST_MEDIA_VOLUME_INFO, TEST_NAV_VOLUME_INFO),
                        TEST_ZONE_ID, TEST_CONFIG_ID, TEST_ACTIVE_STATUS, TEST_SELECTED_STATUS,
                        TEST_DEFAULT_STATUS);

        expectWithMessage("Info without dynamic devices")
                .that(excludesDynamicDevices(testNoDynamicDevicesConfig)).isTrue();
    }

    @Test
    public void excludesDynamicDevices_withDynamicDevices() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioZoneConfigInfo testDynamicDevicesConfig =
                new CarAudioZoneConfigInfo("dynamic-devices-config",
                        List.of(TEST_MEDIA_VOLUME_INFO, TEST_NAV_VOLUME_INFO, TEST_BT_VOLUME_INFO),
                        TEST_ZONE_ID, TEST_CONFIG_ID, TEST_ACTIVE_STATUS, TEST_SELECTED_STATUS,
                        TEST_DEFAULT_STATUS);

        expectWithMessage("Info with dynamic devices")
                .that(excludesDynamicDevices(testDynamicDevicesConfig)).isFalse();
    }

    @Test
    public void excludesDynamicDevices_withOutDynamicDevices_withDynamicFlagsDisabled() {
        mSetFlagsRule.disableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioZoneConfigInfo testNoDynamicDevicesConfig =
                new CarAudioZoneConfigInfo("Non-dynamic-devices-config",
                        List.of(TEST_MEDIA_VOLUME_INFO, TEST_NAV_VOLUME_INFO),
                        TEST_ZONE_ID, TEST_CONFIG_ID, TEST_ACTIVE_STATUS, TEST_SELECTED_STATUS,
                        TEST_DEFAULT_STATUS);

        expectWithMessage("Info without dynamic devices with dynamic flags disable")
                .that(excludesDynamicDevices(testNoDynamicDevicesConfig)).isTrue();
    }

    @Test
    public void excludesDynamicDevices_withDynamicDevices_withDynamicFlagsDisabled() {
        mSetFlagsRule.disableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioZoneConfigInfo testDynamicDevicesConfig =
                new CarAudioZoneConfigInfo("dynamic-devices-config",
                        List.of(TEST_MEDIA_VOLUME_INFO, TEST_NAV_VOLUME_INFO, TEST_BT_VOLUME_INFO),
                        TEST_ZONE_ID, TEST_CONFIG_ID, TEST_ACTIVE_STATUS, TEST_SELECTED_STATUS,
                        TEST_DEFAULT_STATUS);

        expectWithMessage("Info with dynamic devices with dynamic flags disable")
                .that(excludesDynamicDevices(testDynamicDevicesConfig)).isTrue();
    }

    @Test
    public void getDynamicDevicesInConfig_withDynamicDevices() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        AudioManager manager = setUpMockAudioManager();
        CarAudioZoneConfigInfo testDynamicDevicesConfig =
                new CarAudioZoneConfigInfo("dynamic-devices-config",
                        List.of(TEST_MEDIA_VOLUME_INFO, TEST_NAV_VOLUME_INFO, TEST_BT_VOLUME_INFO),
                        TEST_ZONE_ID, TEST_CONFIG_ID, TEST_ACTIVE_STATUS, TEST_SELECTED_STATUS,
                        TEST_DEFAULT_STATUS);

        expectWithMessage("Non-dynamic devices")
                .that(getDynamicDevicesInConfig(testDynamicDevicesConfig, manager))
                .containsExactly(TEST_BT_DEVICE_INFO);
    }

    @Test
    public void getDynamicDevicesInConfig_withoutDynamicDevices() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        AudioManager manager = setUpMockAudioManager();
        CarAudioZoneConfigInfo testDynamicDevicesConfig =
                new CarAudioZoneConfigInfo("dynamic-devices-config",
                        List.of(TEST_MEDIA_VOLUME_INFO, TEST_NAV_VOLUME_INFO),
                        TEST_ZONE_ID, TEST_CONFIG_ID, TEST_ACTIVE_STATUS, TEST_SELECTED_STATUS,
                        TEST_DEFAULT_STATUS);

        expectWithMessage("Dynamic devices")
                .that(getDynamicDevicesInConfig(testDynamicDevicesConfig, manager)).isEmpty();
    }

    @Test
    public void getDynamicDevicesInConfig_withDynamicDevices_andFlagDisabled() {
        mSetFlagsRule.disableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        AudioManager manager = setUpMockAudioManager();
        CarAudioZoneConfigInfo testDynamicDevicesConfig =
                new CarAudioZoneConfigInfo("dynamic-devices-config",
                        List.of(TEST_MEDIA_VOLUME_INFO, TEST_NAV_VOLUME_INFO, TEST_BT_VOLUME_INFO),
                        TEST_ZONE_ID, TEST_CONFIG_ID, TEST_ACTIVE_STATUS, TEST_SELECTED_STATUS,
                        TEST_DEFAULT_STATUS);

        expectWithMessage("Dynamic devices with flags disabled")
                .that(getDynamicDevicesInConfig(testDynamicDevicesConfig, manager)).isEmpty();
    }

    @Test
    public void getDynamicDevicesInConfig_withoutDynamicDevices_andFlagDisabled() {
        mSetFlagsRule.disableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        AudioManager manager = setUpMockAudioManager();
        CarAudioZoneConfigInfo testDynamicDevicesConfig =
                new CarAudioZoneConfigInfo("dynamic-devices-config",
                        List.of(TEST_MEDIA_VOLUME_INFO, TEST_NAV_VOLUME_INFO),
                        TEST_ZONE_ID, TEST_CONFIG_ID, TEST_ACTIVE_STATUS, TEST_SELECTED_STATUS,
                        TEST_DEFAULT_STATUS);

        expectWithMessage("Non-dynamic devices with flags disabled")
                .that(getDynamicDevicesInConfig(testDynamicDevicesConfig, manager)).isEmpty();
    }

    private AudioManager setUpMockAudioManager() {
        AudioManager manager = Mockito.mock(AudioManager.class);
        when(manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS))
                .thenReturn(getMockOutputDevices());
        return manager;
    }

    private AudioDeviceInfo[] getMockOutputDevices() {
        return new AudioDeviceInfo[] { TEST_BUS_DEVICE_1_INFO, TEST_BUS_DEVICE_2_INFO,
                TEST_BT_DEVICE_INFO};
    }

    private static AudioDeviceAttributes getMockDevice(String address, int type) {
        AudioDeviceAttributes attributeMock = Mockito.mock(AudioDeviceAttributes.class);
        when(attributeMock.getAddress()).thenReturn(address);
        when(attributeMock.getType()).thenReturn(type);
        return attributeMock;
    }

    private static AudioDeviceInfo getTestAudioDeviceInfo(String address) {
        AudioDeviceInfo deviceInfo = Mockito.mock(AudioDeviceInfo.class);
        when(deviceInfo.getAddress()).thenReturn(address);
        return deviceInfo;
    }
}
