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

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.car.media.CarAudioManager;
import android.car.settings.CarSettings;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.car.test.mocks.MockSettings;
import android.content.ContentResolver;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@RunWith(AndroidJUnit4.class)
public class CarAudioSettingsUnitTest extends AbstractExtendedMockitoTestCase {

    private static final int TEST_USER_ID_1 = 11;
    private static final int TEST_ZONE_ID = CarAudioManager.PRIMARY_AUDIO_ZONE;
    private static final int TEST_CONFIG_ID = 1;
    private static final int TEST_GROUP_ID = 0;
    private static final int TEST_GAIN_INDEX = 10;
    private static final String TEST_GAIN_INDEX_KEY = new StringBuilder()
            .append("android.car.VOLUME_GROUP/").append(TEST_ZONE_ID).append("/")
            .append(TEST_CONFIG_ID).append("/").append(TEST_GROUP_ID).toString();
    private static final String TEST_MUTE_KEY = new StringBuilder()
            .append("android.car.VOLUME_GROUP_MUTE/").append(TEST_ZONE_ID).append("/")
            .append(TEST_CONFIG_ID).append("/").append(TEST_GROUP_ID).toString();

    @Mock
    private Context mMockContext;

    @Mock
    private ContentResolver mMockContentResolver;

    private CarAudioSettings mCarAudioSettings;

    private MockSettings mMockSettings;

    public CarAudioSettingsUnitTest() {
        super(NO_LOG_TAGS);
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        mMockSettings = new MockSettings(session);
    }

    @Before
    public void setUp() {
        when(mMockContext.getContentResolver()).thenReturn(mMockContentResolver);
        when(mMockContext.createContextAsUser(any(), anyInt())).thenReturn(mMockContext);
        mCarAudioSettings = new CarAudioSettings(mMockContext);
    }

    @Test
    public void isRejectNavigationOnCallEnabledInSettings_whenSetToNotToReject_returnsFalse() {
        setRejectNavigationOnCallSettingsValues(0);

        assertWithMessage("Reject Navigation On Call Setting for userId %s",
                TEST_USER_ID_1).that(mCarAudioSettings
                .isRejectNavigationOnCallEnabledInSettings(TEST_USER_ID_1)).isFalse();
    }

    @Test
    public void isRejectNavigationOnCallEnabledInSettings_whenSetToToReject_returnsTrue() {
        setRejectNavigationOnCallSettingsValues(1);

        assertWithMessage("Reject Navigation On Call Setting for userId %s",
                TEST_USER_ID_1).that(mCarAudioSettings
                .isRejectNavigationOnCallEnabledInSettings(TEST_USER_ID_1)).isTrue();
    }

    @Test
    public void getStoredVolumeGainIndexForUser_returnsSavedValue() {
        setStoredVolumeGainIndexForUser(TEST_GAIN_INDEX);

        assertWithMessage("Volume Group Gain for userId %s, zoneId %s, and groupId %s",
                TEST_USER_ID_1, TEST_ZONE_ID, TEST_USER_ID_1).that(mCarAudioSettings
                .getStoredVolumeGainIndexForUser(TEST_USER_ID_1, TEST_ZONE_ID, TEST_CONFIG_ID,
                        TEST_GROUP_ID)).isEqualTo(TEST_GAIN_INDEX);
    }

    @Test
    public void storedVolumeGainIndexForUser_savesValue() {
        mCarAudioSettings.storeVolumeGainIndexForUser(TEST_USER_ID_1, TEST_ZONE_ID, TEST_CONFIG_ID,
                TEST_GROUP_ID, TEST_GAIN_INDEX);

        assertWithMessage("Volume Gain Setting Stored for userId %s, zoneId %s, and groupId %s",
                TEST_USER_ID_1, TEST_ZONE_ID, TEST_USER_ID_1)
                .that(mMockSettings.getInt(TEST_GAIN_INDEX_KEY)).isEqualTo(TEST_GAIN_INDEX);
    }

    @Test
    public void storeVolumeGroupMuteForUser_withUnMutedState_savesValue() {
        mCarAudioSettings.storeVolumeGroupMuteForUser(TEST_USER_ID_1, TEST_ZONE_ID, TEST_CONFIG_ID,
                TEST_GROUP_ID, /* isMuted= */ false);

        assertWithMessage("Volume Group Setting Stored for userId %s, zoneId %s, and groupId %s",
                TEST_USER_ID_1, TEST_ZONE_ID, TEST_USER_ID_1)
                .that(mMockSettings.getInt(TEST_MUTE_KEY)).isEqualTo(0);
    }

    @Test
    public void storeVolumeGroupMuteForUser_withMutedState_savesValue() {
        mCarAudioSettings.storeVolumeGroupMuteForUser(TEST_USER_ID_1, TEST_ZONE_ID, TEST_CONFIG_ID,
                TEST_GROUP_ID, /* isMuted= */ true);

        assertWithMessage("Volume Group Setting Stored for userId %s, zoneId %s, and groupId %s",
                TEST_USER_ID_1, TEST_ZONE_ID, TEST_USER_ID_1)
                .that(mMockSettings.getInt(TEST_MUTE_KEY)).isEqualTo(1);
    }

    @Test
    public void getVolumeGroupMuteForUser_withUnMutedState_returnsFalse() {
        setStoredVolumeMuteForUser(0);

        boolean muteState = mCarAudioSettings.getVolumeGroupMuteForUser(TEST_USER_ID_1,
                TEST_ZONE_ID, TEST_CONFIG_ID, TEST_GROUP_ID);

        assertWithMessage("Mute State for userId %s, zoneId %s, and groupId %s",
                TEST_USER_ID_1, TEST_ZONE_ID, TEST_USER_ID_1)
                .that(muteState).isEqualTo(false);
    }

    @Test
    public void getVolumeGroupMuteForUser_withMutedState_returnsTrue() {
        setStoredVolumeMuteForUser(1);

        boolean muteState = mCarAudioSettings.getVolumeGroupMuteForUser(TEST_USER_ID_1,
                TEST_ZONE_ID, TEST_CONFIG_ID, TEST_GROUP_ID);

        assertWithMessage("Mute State for userId %s, zoneId %s, and groupId %s",
                TEST_USER_ID_1, TEST_ZONE_ID, TEST_USER_ID_1)
                .that(muteState).isEqualTo(true);
    }

    @Test
    public void isPersistVolumeGroupMuteEnabled_whenSetToNotToDisabled_returnsFalse() {
        setPersistVolumeGroupMuteSettingsValues(0);

        assertWithMessage("Persist volume group mute for userId %s",
                TEST_USER_ID_1).that(mCarAudioSettings
                .isPersistVolumeGroupMuteEnabled(TEST_USER_ID_1)).isFalse();
    }

    @Test
    public void isPersistVolumeGroupMuteEnabled_whenSetToToEnabled_returnsTrue() {
        setPersistVolumeGroupMuteSettingsValues(1);

        assertWithMessage("Persist volume group mute for userId %s",
                TEST_USER_ID_1).that(mCarAudioSettings
                .isPersistVolumeGroupMuteEnabled(TEST_USER_ID_1)).isTrue();
    }

    private void setStoredVolumeGainIndexForUser(int gainIndexForUser) {
        mMockSettings.putInt(TEST_GAIN_INDEX_KEY, gainIndexForUser);
    }

    private void setStoredVolumeMuteForUser(int volumeSetting) {
        mMockSettings.putInt(TEST_MUTE_KEY, volumeSetting);
    }

    private void setRejectNavigationOnCallSettingsValues(int settingsValue) {
        mMockSettings.putInt(CarSettings.Secure.KEY_AUDIO_FOCUS_NAVIGATION_REJECTED_DURING_CALL,
                settingsValue);
    }

    private void setPersistVolumeGroupMuteSettingsValues(int persistMuteSetting) {
        mMockSettings.putInt(CarSettings.Secure.KEY_AUDIO_PERSIST_VOLUME_GROUP_MUTE_STATES,
                persistMuteSetting);
    }
}
