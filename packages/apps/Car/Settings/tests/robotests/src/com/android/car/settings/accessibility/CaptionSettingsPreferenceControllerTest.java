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
import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.PreferenceControllerTestHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class CaptionSettingsPreferenceControllerTest {

    private Context mContext;

    private PreferenceControllerTestHelper<CaptionSettingsPreferenceController>
            mPreferenceControllerHelper;
    private Preference mPreference;

    @Before
    public void setup() {
        mContext = RuntimeEnvironment.application;
        mPreference = new Preference(mContext);
        mPreferenceControllerHelper =
                new PreferenceControllerTestHelper<>(mContext,
                        CaptionSettingsPreferenceController.class, mPreference);
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
    }

    @After
    public void tearDown() {
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_ENABLED, 0);
    }

    @Test
    public void testRefreshUi_captionsDisabled_summarySetToOff() {
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_ENABLED, 0);

        mPreferenceControllerHelper.getController().refreshUi();

        assertThat(mPreference.getSummary()).isEqualTo(
                mContext.getString(R.string.captions_settings_off));
    }

    @Test
    public void testRefreshUi_captionsEnabled_summarySetToOn() {
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_ENABLED, 1);

        mPreferenceControllerHelper.getController().refreshUi();

        assertThat(mPreference.getSummary()).isEqualTo(
                mContext.getString(R.string.captions_settings_on));
    }
}
