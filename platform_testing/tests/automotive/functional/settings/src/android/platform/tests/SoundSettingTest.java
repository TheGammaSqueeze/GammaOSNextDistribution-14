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

package android.platform.tests;

import static junit.framework.Assert.assertTrue;

import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IAutoSettingHelper;
import android.platform.helpers.IAutoSoundsSettingHelper;
import android.platform.helpers.IAutoSoundsSettingHelper.SoundType;
import android.platform.helpers.IAutoSoundsSettingHelper.VolumeType;
import android.platform.helpers.SettingsConstants;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SoundSettingTest {
    private static final String RINGTONE = "Luna";
    private static final String NOTIFICATION_SOUND = "Selenium";
    private static final String ALARM_SOUND = "Platinum";
    private static final int INDEX = 20;

    private HelperAccessor<IAutoSoundsSettingHelper> mSoundsSettingHelper;
    private HelperAccessor<IAutoSettingHelper> mSettingHelper;

    public SoundSettingTest() throws Exception {
        mSoundsSettingHelper = new HelperAccessor<>(IAutoSoundsSettingHelper.class);
        mSettingHelper = new HelperAccessor<>(IAutoSettingHelper.class);
    }


    @Before
    public void openSoundsSettingFacet() {
        mSettingHelper.get().openSetting(SettingsConstants.SOUND_SETTINGS);
        assertTrue(
                "Sound setting did not open",
                mSettingHelper.get().checkMenuExists("In-call volume"));
    }

    @After
    public void goBackToSettingsScreen() {
        mSettingHelper.get().goBackToSettingsScreen();
    }

    @Test
    public void testChangeMediaVolume() {
        mSoundsSettingHelper.get().setVolume(VolumeType.MEDIA, INDEX);
        int volume = mSoundsSettingHelper.get().getVolume(VolumeType.MEDIA);
        assertTrue("Volume was not set", volume == INDEX);
    }

    @Test
    public void testChangeAlarmVolume() {
        mSoundsSettingHelper.get().setVolume(VolumeType.ALARM, INDEX);
        int volume = mSoundsSettingHelper.get().getVolume(VolumeType.ALARM);
        assertTrue("Volume was not set", volume == INDEX);
    }

    @Test
    public void testChangeNavigationVolume() {
        mSoundsSettingHelper.get().setVolume(VolumeType.NAVIGATION, INDEX);
        int volume = mSoundsSettingHelper.get().getVolume(VolumeType.NAVIGATION);
        assertTrue("Volume was not set", volume == INDEX);
    }

    @Test
    public void testChangeInCallVolume() {
        mSoundsSettingHelper.get().setVolume(VolumeType.INCALL, INDEX);
        int volume = mSoundsSettingHelper.get().getVolume(VolumeType.INCALL);
        assertTrue("Volume was not set", volume == INDEX);
    }

    @Test
    public void testChangeRingtone() {
        mSoundsSettingHelper.get().setSound(SoundType.RINGTONE, RINGTONE);
        String sound = mSoundsSettingHelper.get().getSound(SoundType.RINGTONE);
        assertTrue("Sound was not changed", sound.equals(RINGTONE));
    }

    @Test
    public void testChangeNotificationSound() {
        mSoundsSettingHelper.get().setSound(SoundType.NOTIFICATION, NOTIFICATION_SOUND);
        String sound = mSoundsSettingHelper.get().getSound(SoundType.NOTIFICATION);
        assertTrue("Sound was not changed", sound.equals(NOTIFICATION_SOUND));
    }

    @Test
    public void testChangeAlarmSound() {
        mSoundsSettingHelper.get().setSound(SoundType.ALARM, ALARM_SOUND);
        String sound = mSoundsSettingHelper.get().getSound(SoundType.ALARM);
        assertTrue("Sound was not changed", sound.equals(ALARM_SOUND));
    }
}
