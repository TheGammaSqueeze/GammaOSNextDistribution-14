/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.bluetooth.avrcpcontroller;

import static com.google.common.truth.Truth.assertThat;

import android.support.v4.media.session.PlaybackStateCompat;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PlayerApplicationSettingsTest {

    @Test
    public void makeSupportedSettings() {
        byte[] btAvrcpAttributeList = new byte[3];
        btAvrcpAttributeList[0] = PlayerApplicationSettings.REPEAT_STATUS;
        btAvrcpAttributeList[1] = 1;
        btAvrcpAttributeList[2] = PlayerApplicationSettings.JNI_REPEAT_STATUS_ALL_TRACK_REPEAT;

        PlayerApplicationSettings settings = PlayerApplicationSettings.makeSupportedSettings(
                btAvrcpAttributeList);

        assertThat(settings.supportsSetting(PlayerApplicationSettings.REPEAT_STATUS)).isTrue();
    }

    @Test
    public void makeSettings() {
        byte[] btAvrcpAttributeList = new byte[2];
        btAvrcpAttributeList[0] = PlayerApplicationSettings.REPEAT_STATUS;
        btAvrcpAttributeList[1] = PlayerApplicationSettings.JNI_REPEAT_STATUS_GROUP_REPEAT;

        PlayerApplicationSettings settings = PlayerApplicationSettings.makeSettings(
                btAvrcpAttributeList);

        assertThat(settings.getSetting(PlayerApplicationSettings.REPEAT_STATUS)).isEqualTo(
                PlaybackStateCompat.REPEAT_MODE_GROUP);
    }

    @Test
    public void setSupport() {
        byte[] btAvrcpAttributeList = new byte[2];
        btAvrcpAttributeList[0] = PlayerApplicationSettings.REPEAT_STATUS;
        btAvrcpAttributeList[1] = PlayerApplicationSettings.JNI_REPEAT_STATUS_GROUP_REPEAT;
        PlayerApplicationSettings settings = PlayerApplicationSettings.makeSettings(
                btAvrcpAttributeList);
        PlayerApplicationSettings settingsFromSetSupport = new PlayerApplicationSettings();

        settingsFromSetSupport.setSupport(settings);

        assertThat(settingsFromSetSupport.getSetting(
                PlayerApplicationSettings.REPEAT_STATUS)).isEqualTo(
                PlaybackStateCompat.REPEAT_MODE_GROUP);
    }

    @Test
    public void mapAttribIdValtoAvrcpPlayerSetting() {
        assertThat(PlayerApplicationSettings.mapAttribIdValtoAvrcpPlayerSetting(
                PlayerApplicationSettings.REPEAT_STATUS,
                PlayerApplicationSettings.JNI_REPEAT_STATUS_ALL_TRACK_REPEAT)).isEqualTo(
                PlaybackStateCompat.REPEAT_MODE_ALL);
        assertThat(PlayerApplicationSettings.mapAttribIdValtoAvrcpPlayerSetting(
                PlayerApplicationSettings.REPEAT_STATUS,
                PlayerApplicationSettings.JNI_REPEAT_STATUS_GROUP_REPEAT)).isEqualTo(
                PlaybackStateCompat.REPEAT_MODE_GROUP);
        assertThat(PlayerApplicationSettings.mapAttribIdValtoAvrcpPlayerSetting(
                PlayerApplicationSettings.REPEAT_STATUS,
                PlayerApplicationSettings.JNI_REPEAT_STATUS_OFF)).isEqualTo(
                PlaybackStateCompat.REPEAT_MODE_NONE);
        assertThat(PlayerApplicationSettings.mapAttribIdValtoAvrcpPlayerSetting(
                PlayerApplicationSettings.REPEAT_STATUS,
                PlayerApplicationSettings.JNI_REPEAT_STATUS_SINGLE_TRACK_REPEAT)).isEqualTo(
                PlaybackStateCompat.REPEAT_MODE_ONE);
        assertThat(PlayerApplicationSettings.mapAttribIdValtoAvrcpPlayerSetting(
                PlayerApplicationSettings.SHUFFLE_STATUS,
                PlayerApplicationSettings.JNI_SHUFFLE_STATUS_ALL_TRACK_SHUFFLE)).isEqualTo(
                PlaybackStateCompat.SHUFFLE_MODE_ALL);
        assertThat(PlayerApplicationSettings.mapAttribIdValtoAvrcpPlayerSetting(
                PlayerApplicationSettings.SHUFFLE_STATUS,
                PlayerApplicationSettings.JNI_SHUFFLE_STATUS_GROUP_SHUFFLE)).isEqualTo(
                PlaybackStateCompat.SHUFFLE_MODE_GROUP);
        assertThat(PlayerApplicationSettings.mapAttribIdValtoAvrcpPlayerSetting(
                PlayerApplicationSettings.SHUFFLE_STATUS,
                PlayerApplicationSettings.JNI_SHUFFLE_STATUS_OFF)).isEqualTo(
                PlaybackStateCompat.SHUFFLE_MODE_NONE);
        assertThat(PlayerApplicationSettings.mapAttribIdValtoAvrcpPlayerSetting(
                PlayerApplicationSettings.JNI_STATUS_INVALID,
                PlayerApplicationSettings.JNI_STATUS_INVALID)).isEqualTo(
                PlayerApplicationSettings.JNI_STATUS_INVALID);
    }

    @Test
    public void mapAvrcpPlayerSettingstoBTattribVal() {
        assertThat(PlayerApplicationSettings.mapAvrcpPlayerSettingstoBTattribVal(
                PlayerApplicationSettings.REPEAT_STATUS,
                PlaybackStateCompat.REPEAT_MODE_NONE)).isEqualTo(
                PlayerApplicationSettings.JNI_REPEAT_STATUS_OFF);
        assertThat(PlayerApplicationSettings.mapAvrcpPlayerSettingstoBTattribVal(
                PlayerApplicationSettings.REPEAT_STATUS,
                PlaybackStateCompat.REPEAT_MODE_ONE)).isEqualTo(
                PlayerApplicationSettings.JNI_REPEAT_STATUS_SINGLE_TRACK_REPEAT);
        assertThat(PlayerApplicationSettings.mapAvrcpPlayerSettingstoBTattribVal(
                PlayerApplicationSettings.REPEAT_STATUS,
                PlaybackStateCompat.REPEAT_MODE_ALL)).isEqualTo(
                PlayerApplicationSettings.JNI_REPEAT_STATUS_ALL_TRACK_REPEAT);
        assertThat(PlayerApplicationSettings.mapAvrcpPlayerSettingstoBTattribVal(
                PlayerApplicationSettings.REPEAT_STATUS,
                PlaybackStateCompat.REPEAT_MODE_GROUP)).isEqualTo(
                PlayerApplicationSettings.JNI_REPEAT_STATUS_GROUP_REPEAT);
        assertThat(PlayerApplicationSettings.mapAvrcpPlayerSettingstoBTattribVal(
                PlayerApplicationSettings.SHUFFLE_STATUS,
                PlaybackStateCompat.SHUFFLE_MODE_NONE)).isEqualTo(
                PlayerApplicationSettings.JNI_SHUFFLE_STATUS_OFF);
        assertThat(PlayerApplicationSettings.mapAvrcpPlayerSettingstoBTattribVal(
                PlayerApplicationSettings.SHUFFLE_STATUS,
                PlaybackStateCompat.SHUFFLE_MODE_ALL)).isEqualTo(
                PlayerApplicationSettings.JNI_SHUFFLE_STATUS_ALL_TRACK_SHUFFLE);
        assertThat(PlayerApplicationSettings.mapAvrcpPlayerSettingstoBTattribVal(
                PlayerApplicationSettings.SHUFFLE_STATUS,
                PlaybackStateCompat.SHUFFLE_MODE_GROUP)).isEqualTo(
                PlayerApplicationSettings.JNI_SHUFFLE_STATUS_GROUP_SHUFFLE);
        assertThat(PlayerApplicationSettings.mapAvrcpPlayerSettingstoBTattribVal(-1, -1)).isEqualTo(
                PlayerApplicationSettings.JNI_STATUS_INVALID);
    }
}