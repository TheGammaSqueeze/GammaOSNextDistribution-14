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

package com.android.car.audio.hal;


import static android.media.audio.common.AudioDeviceDescription.CONNECTION_BUS;
import static android.media.audio.common.AudioDeviceDescription.CONNECTION_USB;
import static android.media.audio.common.AudioDeviceType.IN_DEVICE;
import static android.media.audio.common.AudioDeviceType.OUT_DEVICE;
import static android.media.audio.common.AudioDeviceType.OUT_SPEAKER;
import static android.media.audio.common.AudioGainMode.CHANNELS;
import static android.media.audio.common.AudioGainMode.JOINT;

import static org.junit.Assert.assertThrows;

import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.media.audio.common.AudioDevice;
import android.media.audio.common.AudioDeviceAddress;
import android.media.audio.common.AudioDeviceDescription;
import android.media.audio.common.AudioGain;
import android.media.audio.common.AudioPort;
import android.media.audio.common.AudioPortDeviceExt;
import android.media.audio.common.AudioPortExt;
import android.media.audio.common.AudioPortMixExt;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;


@RunWith(AndroidJUnit4.class)
public final class HalAudioDeviceInfoTest extends AbstractExtendedMockitoTestCase {

    private static final int PORT_ID = 0;
    private static final String PORT_NAME = "Media Port";
    private static final String PORT_NAME_ALT = "Nav Port";
    private static final String ADDRESS_BUS_MEDIA = "BUS100_MEDIA";
    private static final String ADDRESS_BUS_NAV = "BUS200_NAV";
    private static final int TEST_GAIN_MIN_VALUE = 0;
    private static final int TEST_GAIN_MAX_VALUE = 100;
    private static final int TEST_GAIN_DEFAULT_VALUE = 50;
    private static final int TEST_GAIN_STEP_VALUE = 2;
    private static final AudioGain[] GAINS = new AudioGain[] {
            new AudioGain() {{
                mode = JOINT;
                minValue = TEST_GAIN_MIN_VALUE;
                maxValue = TEST_GAIN_MAX_VALUE;
                defaultValue = TEST_GAIN_DEFAULT_VALUE;
                stepValue = TEST_GAIN_STEP_VALUE;
            }}
    };

    @Test
    public void constructor_succeeds() {
        AudioPortDeviceExt deviceExt = createAudioPortDeviceExt(OUT_DEVICE, CONNECTION_BUS,
                ADDRESS_BUS_MEDIA);
        AudioPort audioPort = createAudioPort(PORT_ID, PORT_NAME, GAINS, deviceExt);

        HalAudioDeviceInfo deviceInfo = new HalAudioDeviceInfo(audioPort);

        expectWithMessage("Hal Audio Device Info id")
                .that(deviceInfo.getId())
                .isEqualTo(PORT_ID);
        expectWithMessage("Hal Audio Device Info name")
                .that(deviceInfo.getName())
                .isEqualTo(PORT_NAME);
        expectWithMessage("Hal Audio Device Info gain min value")
                .that(deviceInfo.getGainMinValue())
                .isEqualTo(TEST_GAIN_MIN_VALUE);
        expectWithMessage("Hal Audio Device Info gain max value")
                .that(deviceInfo.getGainMaxValue())
                .isEqualTo(TEST_GAIN_MAX_VALUE);
        expectWithMessage("Hal Audio Device Info gain default value")
                .that(deviceInfo.getGainDefaultValue())
                .isEqualTo(TEST_GAIN_DEFAULT_VALUE);
        expectWithMessage("Hal Audio Device Info gain step value")
                .that(deviceInfo.getGainStepValue())
                .isEqualTo(TEST_GAIN_STEP_VALUE);
        expectWithMessage("Hal Audio Device Info device type")
                .that(deviceInfo.getType())
                .isEqualTo(OUT_DEVICE);
        expectWithMessage("Hal Audio Device Info conntection")
                .that(deviceInfo.getConnection())
                .isEqualTo(CONNECTION_BUS);
        expectWithMessage("Hal Audio Device Info address")
                .that(deviceInfo.getAddress())
                .isEqualTo(ADDRESS_BUS_MEDIA);
        expectWithMessage("Hal Audio Device Info is output device")
                .that(deviceInfo.isOutputDevice())
                .isTrue();
        expectWithMessage("Hal Audio Device Info is input device")
                .that(deviceInfo.isInputDevice())
                .isFalse();
    }

    @Test
    public void constructor_requiresNonNullAudioPort() {
        Throwable thrown = assertThrows(NullPointerException.class,
                () -> new HalAudioDeviceInfo(null));

        expectWithMessage("Constructor exception")
                .that(thrown).hasMessageThat().contains("Audio port can not be null");
    }

    @Test
    public void constructor_requiresNonNullAudioGains() {
        AudioPortDeviceExt deviceExt = createAudioPortDeviceExt(OUT_DEVICE, CONNECTION_BUS,
                ADDRESS_BUS_MEDIA);
        AudioPort audioPort = createAudioPort(PORT_ID, PORT_NAME, null, deviceExt);

        Throwable thrown = assertThrows(NullPointerException.class,
                () -> new HalAudioDeviceInfo(audioPort));

        expectWithMessage("Constructor exception for audio gain")
                .that(thrown).hasMessageThat().contains("Audio gains can not be null");
    }

    @Test
    public void constructor_requiresJointModeAudioGain() {
        AudioGain[] gains = new AudioGain[] {
                new AudioGain() {{
                    mode = CHANNELS;
                    minValue = TEST_GAIN_MIN_VALUE;
                    maxValue = TEST_GAIN_MAX_VALUE;
                    defaultValue = TEST_GAIN_DEFAULT_VALUE;
                    stepValue = TEST_GAIN_STEP_VALUE;
                }}
        };
        AudioPortDeviceExt deviceExt = createAudioPortDeviceExt(OUT_DEVICE, CONNECTION_BUS,
                ADDRESS_BUS_MEDIA);
        AudioPort audioPort = createAudioPort(PORT_ID, PORT_NAME, gains, deviceExt);

        Throwable thrown = assertThrows(IllegalStateException.class,
                () -> new HalAudioDeviceInfo(audioPort));

        expectWithMessage("Constructor exception for audio gain")
                .that(thrown).hasMessageThat().contains("Audio port");
    }

    @Test
    public void constructor_requiresMaxGainLargerThanMin() {
        AudioGain[] gains = new AudioGain[] {
                new AudioGain() {{
                    mode = JOINT;
                    minValue = 25;
                    maxValue = 10;
                    defaultValue = TEST_GAIN_DEFAULT_VALUE;
                    stepValue = TEST_GAIN_STEP_VALUE;
                }}
        };
        AudioPortDeviceExt deviceExt = createAudioPortDeviceExt(OUT_DEVICE, CONNECTION_BUS,
                ADDRESS_BUS_MEDIA);
        AudioPort audioPort = createAudioPort(PORT_ID, PORT_NAME, gains, deviceExt);

        Throwable thrown = assertThrows(IllegalArgumentException.class,
                () -> new HalAudioDeviceInfo(audioPort));

        expectWithMessage("Constructor exception for audio gain")
                .that(thrown).hasMessageThat().contains("lower than");
    }

    @Test
    public void constructor_requiresDefaultGainLargerThanMin() {
        AudioGain[] gains = new AudioGain[] {
                new AudioGain() {{
                    mode = JOINT;
                    minValue = 20;
                    maxValue = TEST_GAIN_MAX_VALUE;
                    defaultValue = 10;
                    stepValue = TEST_GAIN_STEP_VALUE;
                }}
        };
        AudioPortDeviceExt deviceExt = createAudioPortDeviceExt(OUT_DEVICE, CONNECTION_BUS,
                ADDRESS_BUS_MEDIA);
        AudioPort audioPort = createAudioPort(PORT_ID, PORT_NAME, gains, deviceExt);

        Throwable thrown = assertThrows(IllegalArgumentException.class,
                () -> new HalAudioDeviceInfo(audioPort));

        expectWithMessage("Constructor exception for audio gain")
                .that(thrown).hasMessageThat().contains("not in range");
    }

    @Test
    public void constructor_requiresDefaultGainSmallerThanMax() {
        AudioGain[] gains = new AudioGain[] {
                new AudioGain() {{
                    mode = JOINT;
                    minValue = TEST_GAIN_MIN_VALUE;
                    maxValue = TEST_GAIN_MAX_VALUE;
                    defaultValue = 110;
                    stepValue = TEST_GAIN_STEP_VALUE;
                }}
        };
        AudioPortDeviceExt deviceExt = createAudioPortDeviceExt(OUT_DEVICE, CONNECTION_BUS,
                ADDRESS_BUS_MEDIA);
        AudioPort audioPort = createAudioPort(PORT_ID, PORT_NAME, gains, deviceExt);

        Throwable thrown = assertThrows(IllegalArgumentException.class,
                () -> new HalAudioDeviceInfo(audioPort));

        expectWithMessage("Constructor exception for audio gain")
                .that(thrown).hasMessageThat().contains("not in range");
    }

    @Test
    public void constructor_requiresGainStepSizeFactorOfRange() {
        AudioGain[] gains = new AudioGain[] {
                new AudioGain() {{
                    mode = JOINT;
                    minValue = TEST_GAIN_MIN_VALUE;
                    maxValue = TEST_GAIN_MAX_VALUE;
                    defaultValue = TEST_GAIN_DEFAULT_VALUE;
                    stepValue = 7;
                }}
        };
        AudioPortDeviceExt deviceExt = createAudioPortDeviceExt(OUT_DEVICE, CONNECTION_BUS,
                ADDRESS_BUS_MEDIA);
        AudioPort audioPort = createAudioPort(PORT_ID, PORT_NAME, gains, deviceExt);

        Throwable thrown = assertThrows(IllegalArgumentException.class,
                () -> new HalAudioDeviceInfo(audioPort));

        expectWithMessage("Constructor exception for audio gain")
                .that(thrown).hasMessageThat().contains("greater than min gain to max gain range");
    }

    @Test
    public void constructor_requiresGainStepSizeFactorOfRangeToDefault() {
        AudioGain[] gains = new AudioGain[] {
                new AudioGain() {{
                    mode = JOINT;
                    minValue = TEST_GAIN_MIN_VALUE;
                    maxValue = 98;
                    defaultValue = TEST_GAIN_DEFAULT_VALUE;
                    stepValue = 7;
                }}
        };
        AudioPortDeviceExt deviceExt = createAudioPortDeviceExt(OUT_DEVICE, CONNECTION_BUS,
                ADDRESS_BUS_MEDIA);
        AudioPort audioPort = createAudioPort(PORT_ID, PORT_NAME, gains, deviceExt);

        Throwable thrown = assertThrows(IllegalArgumentException.class,
                () -> new HalAudioDeviceInfo(audioPort));

        expectWithMessage("Constructor exception for audio gain").that(thrown).hasMessageThat()
                .contains("greater than min gain to default gain range");
    }

    @Test
    public void constructor_requiresAudioPortDeviceExt() {
        AudioPortMixExt mixExt = new AudioPortMixExt();
        AudioPort audioPort = new AudioPort();
        audioPort.id = PORT_ID;
        audioPort.name = PORT_NAME;
        audioPort.gains = GAINS;
        audioPort.ext = AudioPortExt.mix(mixExt);

        Throwable thrown = assertThrows(IllegalArgumentException.class,
                () -> new HalAudioDeviceInfo(audioPort));

        expectWithMessage("Constructor exception for audio device").that(thrown).hasMessageThat()
                .contains("Invalid audio port ext");
    }

    @Test
    public void constructor_requiresConnectionBus() {
        AudioPortDeviceExt deviceExt = createAudioPortDeviceExt(OUT_DEVICE, CONNECTION_USB,
                ADDRESS_BUS_MEDIA);
        AudioPort audioPort = createAudioPort(PORT_ID, PORT_NAME, GAINS, deviceExt);

        Throwable thrown = assertThrows(IllegalArgumentException.class,
                () -> new HalAudioDeviceInfo(audioPort));

        expectWithMessage("Constructor exception for audio device")
                .that(thrown).hasMessageThat().contains("Invalid audio device connection");
    }

    @Test
    public void constructor_requiresDeviceTypeInOrOut() {
        AudioPortDeviceExt deviceExt = createAudioPortDeviceExt(OUT_SPEAKER, CONNECTION_BUS,
                ADDRESS_BUS_MEDIA);
        AudioPort audioPort = createAudioPort(PORT_ID, PORT_NAME, GAINS, deviceExt);

        Throwable thrown = assertThrows(IllegalArgumentException.class,
                () -> new HalAudioDeviceInfo(audioPort));

        expectWithMessage("Constructor exception for audio device")
                .that(thrown).hasMessageThat().contains("Invalid audio device type");
    }

    @Test
    public void constructor_requiresNotEmptyAddress() {
        AudioPortDeviceExt deviceExt = createAudioPortDeviceExt(OUT_DEVICE, CONNECTION_BUS,
                /* address= */ "");
        AudioPort audioPort = createAudioPort(PORT_ID, PORT_NAME, GAINS, deviceExt);

        Throwable thrown = assertThrows(IllegalArgumentException.class,
                () -> new HalAudioDeviceInfo(audioPort));

        expectWithMessage("Constructor exception for audio device")
                .that(thrown).hasMessageThat().contains("address cannot");
    }

    @Test
    public void hash_forTheSameHalAudioDeviceInfos_equals() {
        AudioGain gain = new AudioGain() {{
                mode = JOINT;
                minValue = TEST_GAIN_MIN_VALUE;
                maxValue = TEST_GAIN_MAX_VALUE;
                defaultValue = TEST_GAIN_DEFAULT_VALUE;
                stepValue = TEST_GAIN_STEP_VALUE;
            }};
        AudioPortDeviceExt deviceExt = createAudioPortDeviceExt(OUT_DEVICE, CONNECTION_BUS,
                ADDRESS_BUS_MEDIA);
        AudioPort audioPort = createAudioPort(PORT_ID, PORT_NAME, GAINS, deviceExt);
        HalAudioDeviceInfo deviceInfo = new HalAudioDeviceInfo(audioPort);

        expectWithMessage("Hal Audio Device Infos hash")
                .that(Objects.hash(PORT_NAME, gain, OUT_DEVICE, CONNECTION_BUS,
                        ADDRESS_BUS_MEDIA))
                .isEqualTo(deviceInfo.hashCode());
    }

    @Test
    public void hash_withDifferentAudioGains_notEquals() {
        AudioGain gain = new AudioGain() {{
                mode = JOINT;
                minValue = TEST_GAIN_MIN_VALUE;
                maxValue = 50;
                defaultValue = TEST_GAIN_DEFAULT_VALUE;
                stepValue = TEST_GAIN_STEP_VALUE;
            }};
        AudioPortDeviceExt deviceExt = createAudioPortDeviceExt(OUT_DEVICE, CONNECTION_BUS,
                ADDRESS_BUS_MEDIA);
        AudioPort audioPort = createAudioPort(PORT_ID, PORT_NAME, GAINS, deviceExt);
        HalAudioDeviceInfo deviceInfo = new HalAudioDeviceInfo(audioPort);

        expectWithMessage("Hal Audio Device Infos hash")
                .that(Objects.hash(PORT_NAME, gain, OUT_DEVICE, CONNECTION_BUS, ADDRESS_BUS_MEDIA))
                .isNotEqualTo(deviceInfo.hashCode());
    }

    @Test
    public void hash_forTheSameObject_Equals() {
        AudioPortDeviceExt deviceExt = createAudioPortDeviceExt(OUT_DEVICE, CONNECTION_BUS,
                ADDRESS_BUS_MEDIA);
        AudioPort audioPort = createAudioPort(PORT_ID, PORT_NAME, GAINS, deviceExt);
        HalAudioDeviceInfo deviceInfo1 = new HalAudioDeviceInfo(audioPort);
        HalAudioDeviceInfo deviceInfo2 = new HalAudioDeviceInfo(audioPort);

        expectWithMessage("Hal Audio Device Infos hash")
                .that(deviceInfo1.hashCode()).isEqualTo(deviceInfo2.hashCode());
    }

    @Test
    public void equals_forTheSameObject_succeeds() {
        AudioPortDeviceExt deviceExt = createAudioPortDeviceExt(OUT_DEVICE, CONNECTION_BUS,
                ADDRESS_BUS_MEDIA);
        AudioPort audioPort = createAudioPort(PORT_ID, PORT_NAME, GAINS, deviceExt);
        HalAudioDeviceInfo deviceInfo1 = new HalAudioDeviceInfo(audioPort);
        HalAudioDeviceInfo deviceInfo2 = new HalAudioDeviceInfo(audioPort);

        expectWithMessage("Hal Audio Device Infos equals").that(deviceInfo1.equals(deviceInfo2))
                .isTrue();
        expectWithMessage("Hal Audio Device Infos equals").that(deviceInfo1)
                .isEqualTo(deviceInfo2);
    }

    @Test
    public void equals_withDifferentGains_fails() {
        AudioGain[] gains = new AudioGain[]{
                new AudioGain() {{
                    mode = JOINT;
                    minValue = TEST_GAIN_MIN_VALUE;
                    maxValue = 50;
                    defaultValue = TEST_GAIN_DEFAULT_VALUE;
                    stepValue = TEST_GAIN_STEP_VALUE;
                }}
        };
        AudioPortDeviceExt deviceExtRef = createAudioPortDeviceExt(OUT_DEVICE, CONNECTION_BUS,
                ADDRESS_BUS_MEDIA);
        AudioPort audioPortRef = createAudioPort(PORT_ID, PORT_NAME, GAINS, deviceExtRef);
        AudioPort audioPort = createAudioPort(PORT_ID, PORT_NAME, gains, deviceExtRef);
        HalAudioDeviceInfo deviceInfoRef = new HalAudioDeviceInfo(audioPortRef);
        HalAudioDeviceInfo deviceInfo = new HalAudioDeviceInfo(audioPort);

        expectWithMessage("Hal Audio Device Infos equals").that(deviceInfoRef.equals(deviceInfo))
                .isFalse();
        expectWithMessage("Hal Audio Device Infos equals").that(deviceInfoRef)
                .isNotEqualTo(deviceInfo);
    }


    @Test
    public void equals_withDifferentNames_fails() {
        AudioPortDeviceExt deviceExtRef = createAudioPortDeviceExt(OUT_DEVICE, CONNECTION_BUS,
                ADDRESS_BUS_MEDIA);
        AudioPort audioPortRef = createAudioPort(PORT_ID, PORT_NAME, GAINS, deviceExtRef);
        AudioPort audioPort = createAudioPort(PORT_ID, PORT_NAME_ALT, GAINS, deviceExtRef);
        HalAudioDeviceInfo deviceInfoRef = new HalAudioDeviceInfo(audioPortRef);
        HalAudioDeviceInfo deviceInfo = new HalAudioDeviceInfo(audioPort);

        expectWithMessage("Hal Audio Device Infos equals").that(deviceInfoRef.equals(deviceInfo))
                .isFalse();
        expectWithMessage("Hal Audio Device Infos equals").that(deviceInfoRef)
                .isNotEqualTo(deviceInfo);
    }

    @Test
    public void equals_withDifferentTypes_fails() {
        AudioPortDeviceExt deviceExtRef = createAudioPortDeviceExt(OUT_DEVICE, CONNECTION_BUS,
                ADDRESS_BUS_MEDIA);
        AudioPortDeviceExt deviceExt = createAudioPortDeviceExt(IN_DEVICE, CONNECTION_BUS,
                ADDRESS_BUS_MEDIA);
        AudioPort audioPortRef = createAudioPort(PORT_ID, PORT_NAME, GAINS, deviceExtRef);
        AudioPort audioPort = createAudioPort(PORT_ID, PORT_NAME, GAINS, deviceExt);
        HalAudioDeviceInfo deviceInfoRef = new HalAudioDeviceInfo(audioPortRef);
        HalAudioDeviceInfo deviceInfo = new HalAudioDeviceInfo(audioPort);

        expectWithMessage("Hal Audio Device Infos equals").that(deviceInfoRef.equals(deviceInfo))
                .isFalse();
        expectWithMessage("Hal Audio Device Infos equals").that(deviceInfoRef)
                .isNotEqualTo(deviceInfo);

    }

    @Test
    public void equals_withDifferentAddress_fails() {
        AudioPortDeviceExt deviceExtRef = createAudioPortDeviceExt(OUT_DEVICE, CONNECTION_BUS,
                ADDRESS_BUS_MEDIA);
        AudioPortDeviceExt deviceExt = createAudioPortDeviceExt(OUT_DEVICE, CONNECTION_BUS,
                ADDRESS_BUS_NAV);
        AudioPort audioPortRef = createAudioPort(PORT_ID, PORT_NAME, GAINS, deviceExtRef);
        AudioPort audioPort = createAudioPort(PORT_ID, PORT_NAME, GAINS, deviceExt);
        HalAudioDeviceInfo deviceInfoRef = new HalAudioDeviceInfo(audioPortRef);
        HalAudioDeviceInfo deviceInfo = new HalAudioDeviceInfo(audioPort);

        expectWithMessage("Hal Audio Device Infos equals").that(deviceInfoRef.equals(deviceInfo))
                .isFalse();
        expectWithMessage("Hal Audio Device Infos equals").that(deviceInfoRef)
                .isNotEqualTo(deviceInfo);
    }

    private static AudioPort createAudioPort(int id, String name, AudioGain[] gains,
            AudioPortDeviceExt deviceExt) {
        AudioPort audioPort = new AudioPort();
        audioPort.id = id;
        audioPort.name = name;
        audioPort.gains = gains;
        audioPort.ext = AudioPortExt.device(deviceExt);
        return audioPort;
    }

    private static AudioPortDeviceExt createAudioPortDeviceExt(int type, String connection,
            String address) {
        AudioPortDeviceExt deviceExt = new AudioPortDeviceExt();
        deviceExt.device = new AudioDevice();
        deviceExt.device.type = new AudioDeviceDescription();
        deviceExt.device.type.type = type;
        deviceExt.device.type.connection = connection;
        deviceExt.device.address = AudioDeviceAddress.id(address);
        return deviceExt;
    }
}
