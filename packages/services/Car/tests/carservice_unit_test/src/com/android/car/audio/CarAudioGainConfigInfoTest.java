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

import static com.google.common.truth.Truth.assertWithMessage;

import android.hardware.automotive.audiocontrol.AudioGainConfigInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;

@RunWith(AndroidJUnit4.class)
public final class CarAudioGainConfigInfoTest {
    private static final int PRIMARY_ZONE_ID = 0;
    private static final int SECONDARY_ZONE_ID = 1;
    private static final String PRIMARY_MUSIC_ADDRESS = "primary music";
    private static final String PRIMARY_NAVIGATION_ADDRESS = "primary navigation";

    @Test
    public void constructor_succeeds() {
        AudioGainConfigInfo gainInfo = new AudioGainConfigInfo();
        gainInfo.zoneId = PRIMARY_ZONE_ID;
        gainInfo.devicePortAddress = PRIMARY_MUSIC_ADDRESS;
        gainInfo.volumeIndex = 666;
        CarAudioGainConfigInfo carGainInfo = new CarAudioGainConfigInfo(gainInfo);

        assertWithMessage("Audio Gain Config")
                .that(carGainInfo.getAudioGainConfigInfo())
                .isEqualTo(gainInfo);
        assertWithMessage("Audio Gain Config zone id")
                .that(carGainInfo.getZoneId())
                .isEqualTo(PRIMARY_ZONE_ID);
        assertWithMessage("Audio Gain Config device address")
                .that(carGainInfo.getDeviceAddress())
                .isEqualTo(PRIMARY_MUSIC_ADDRESS);
        assertWithMessage("Audio Gain Config volume index")
                .that(carGainInfo.getVolumeIndex())
                .isEqualTo(666);
        assertWithMessage("Audio Gain Config Literal").that(carGainInfo.toString()).isNotNull();
    }

    @Test
    public void hash_forTheSameAudioGainConfigs_equals() {
        AudioGainConfigInfo gainInfo = new AudioGainConfigInfo();
        gainInfo.zoneId = PRIMARY_ZONE_ID;
        gainInfo.devicePortAddress = PRIMARY_MUSIC_ADDRESS;
        gainInfo.volumeIndex = 666;
        CarAudioGainConfigInfo carGainInfo1 = new CarAudioGainConfigInfo(gainInfo);

        assertWithMessage("Audio Gain Configs")
                .that(Objects.hash(PRIMARY_ZONE_ID, PRIMARY_MUSIC_ADDRESS, 666))
                .isEqualTo(carGainInfo1.hashCode());
    }

    @Test
    public void hash_withDifferentZoneIds_notEquals() {
        AudioGainConfigInfo gainInfo = new AudioGainConfigInfo();
        gainInfo.zoneId = PRIMARY_ZONE_ID;
        gainInfo.devicePortAddress = PRIMARY_MUSIC_ADDRESS;
        gainInfo.volumeIndex = 666;
        CarAudioGainConfigInfo carGainInfo1 = new CarAudioGainConfigInfo(gainInfo);

        assertWithMessage("Audio Gain Configs")
                .that(Objects.hash(PRIMARY_ZONE_ID, PRIMARY_MUSIC_ADDRESS, 665))
                .isNotEqualTo(carGainInfo1.hashCode());
    }

    @Test
    public void hash_forTheSameObject_succeeds() {
        AudioGainConfigInfo gainInfo = new AudioGainConfigInfo();
        gainInfo.zoneId = PRIMARY_ZONE_ID;
        gainInfo.devicePortAddress = PRIMARY_MUSIC_ADDRESS;
        gainInfo.volumeIndex = 666;
        CarAudioGainConfigInfo carGainInfo1 = new CarAudioGainConfigInfo(gainInfo);
        CarAudioGainConfigInfo carGainInfo2 = new CarAudioGainConfigInfo(gainInfo);

        assertWithMessage("Audio Gain Configs").that(carGainInfo1.hashCode())
                .isEqualTo(carGainInfo2.hashCode());
    }

    @Test
    public void equals_succeeds() {
        AudioGainConfigInfo gainInfo = new AudioGainConfigInfo();
        gainInfo.zoneId = PRIMARY_ZONE_ID;
        gainInfo.devicePortAddress = PRIMARY_MUSIC_ADDRESS;
        gainInfo.volumeIndex = 666;
        CarAudioGainConfigInfo carGainInfo1 = new CarAudioGainConfigInfo(gainInfo);
        CarAudioGainConfigInfo carGainInfo2 = new CarAudioGainConfigInfo(gainInfo);

        assertWithMessage("Audio Gain Configs").that(carGainInfo1 == carGainInfo2).isFalse();
        assertWithMessage("Audio Gain Configs").that(carGainInfo1.equals(carGainInfo2)).isTrue();
        assertWithMessage("Audio Gain Configs").that(carGainInfo1).isEqualTo(carGainInfo2);
    }

    @Test
    public void equals_fails() {
        AudioGainConfigInfo gainInfo1 = new AudioGainConfigInfo();
        gainInfo1.zoneId = PRIMARY_ZONE_ID;
        gainInfo1.devicePortAddress = PRIMARY_MUSIC_ADDRESS;
        gainInfo1.volumeIndex = 666;
        CarAudioGainConfigInfo carGainInfo1 = new CarAudioGainConfigInfo(gainInfo1);

        AudioGainConfigInfo gainInfo2 = new AudioGainConfigInfo();
        gainInfo2.zoneId = PRIMARY_ZONE_ID;
        gainInfo2.devicePortAddress = PRIMARY_MUSIC_ADDRESS;
        gainInfo2.volumeIndex = 999;
        CarAudioGainConfigInfo carGainInfo2 = new CarAudioGainConfigInfo(gainInfo2);

        assertWithMessage("Audio Gain Configs").that(carGainInfo1.equals(carGainInfo2)).isFalse();
        assertWithMessage("Audio Gain Configs").that(carGainInfo1).isNotEqualTo(carGainInfo2);

        AudioGainConfigInfo gainInfo3 = new AudioGainConfigInfo();
        gainInfo3.zoneId = PRIMARY_ZONE_ID;
        gainInfo3.devicePortAddress = PRIMARY_NAVIGATION_ADDRESS;
        gainInfo3.volumeIndex = 666;
        CarAudioGainConfigInfo carGainInfo3 = new CarAudioGainConfigInfo(gainInfo3);

        assertWithMessage("Audio Gain Configs").that(carGainInfo1.equals(carGainInfo3)).isFalse();
        assertWithMessage("Audio Gain Configs").that(carGainInfo1).isNotEqualTo(carGainInfo3);

        AudioGainConfigInfo gainInfo4 = new AudioGainConfigInfo();
        gainInfo4.zoneId = SECONDARY_ZONE_ID;
        gainInfo4.devicePortAddress = PRIMARY_NAVIGATION_ADDRESS;
        gainInfo4.volumeIndex = 666;
        CarAudioGainConfigInfo carGainInfo4 = new CarAudioGainConfigInfo(gainInfo4);

        assertWithMessage("Audio Gain Configs").that(carGainInfo1.equals(carGainInfo4)).isFalse();
        assertWithMessage("Audio Gain Configs").that(carGainInfo1).isNotEqualTo(carGainInfo4);
    }
}
