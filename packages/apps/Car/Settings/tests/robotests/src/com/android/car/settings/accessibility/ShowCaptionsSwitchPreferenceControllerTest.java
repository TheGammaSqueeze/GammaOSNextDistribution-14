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

package com.android.car.settings.accessibility;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.provider.Settings;

import androidx.lifecycle.Lifecycle;

import com.android.car.settings.common.ColoredSwitchPreference;
import com.android.car.settings.common.PreferenceControllerTestHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class ShowCaptionsSwitchPreferenceControllerTest {

    private Context mContext;
    private PreferenceControllerTestHelper<ShowCaptionsSwitchPreferenceController>
            mPreferenceControllerHelper;
    private ColoredSwitchPreference mColoredSwitchPreference;

    @Before
    public void setup() {
        mContext = RuntimeEnvironment.application;
        mColoredSwitchPreference = new ColoredSwitchPreference(mContext);
        mPreferenceControllerHelper =
                new PreferenceControllerTestHelper<>(mContext,
                        ShowCaptionsSwitchPreferenceController.class, mColoredSwitchPreference);
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
    }

    @After
    public void tearDown() {
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_ENABLED, 0);
    }

    @Test
    public void testRefreshUi_captionsDisabled_switchSetToOff() {
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_ENABLED, 0);

        mPreferenceControllerHelper.getController().refreshUi();

        assertThat(mColoredSwitchPreference.isChecked()).isFalse();
    }

    @Test
    public void testRefreshUi_captionsEnabled_switchSetToOn() {
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_ENABLED, 1);

        mPreferenceControllerHelper.getController().refreshUi();

        assertThat(mColoredSwitchPreference.isChecked()).isTrue();
    }

    @Test
    public void testSwitchedSetOn_setsSystemCaptionSettingsEnabled() {
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_ENABLED, 0);

        mColoredSwitchPreference.getOnPreferenceChangeListener().onPreferenceChange(
                mColoredSwitchPreference, true);

        assertThat(Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_ENABLED, 0)).isEqualTo(1);
    }

    @Test
    public void testSwitchedSetOff_setsSystemCaptionSettingsDisabled() {
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_ENABLED, 1);

        mColoredSwitchPreference.getOnPreferenceChangeListener().onPreferenceChange(
                mColoredSwitchPreference, false);

        assertThat(Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_ENABLED, -1)).isEqualTo(0);
    }
}
