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
import androidx.preference.ListPreference;

import com.android.car.settings.common.PreferenceControllerTestHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
public class CaptionsTextStyleListPreferenceControllerTest {

    private Context mContext;
    private PreferenceControllerTestHelper<CaptionsTextStyleListPreferenceController>
            mPreferenceControllerHelper;
    private ListPreference mListPreference;
    private String[] mFontStyleTitles;

    @Before
    public void setup() {
        mContext = RuntimeEnvironment.application;
        mListPreference = new ListPreference(mContext);
        mPreferenceControllerHelper =
                new PreferenceControllerTestHelper<>(mContext,
                        CaptionsTextStyleListPreferenceController.class, mListPreference);
        mFontStyleTitles = mPreferenceControllerHelper.getController().mFontStyleTitles;
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
    }

    @After
    public void tearDown() {
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_PRESET, 4);
    }

    @Test
    public void testUpdateUi_includesAllTextStyleOptions() {
        mPreferenceControllerHelper.getController().refreshUi();

        assertThat(Arrays.asList(mListPreference.getEntries())).containsExactlyElementsIn(
                mFontStyleTitles);
    }

    @Test
    public void testUpdateUi_selectsCurrentFontStylePreset() {
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_PRESET, 0);

        mPreferenceControllerHelper.getController().refreshUi();

        assertThat(mListPreference.getValue()).isEqualTo("0");
        assertThat(mListPreference.getSummary()).isEqualTo(mFontStyleTitles[1]);
    }

    @Test
    public void testUpdateUi_noKnownValueSelected_selectsDefaultFontStylePreset() {
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_PRESET, 50);

        mPreferenceControllerHelper.getController().refreshUi();

        assertThat(mListPreference.getValue()).isEqualTo("4");
        assertThat(mListPreference.getSummary()).isEqualTo(mFontStyleTitles[0]);
    }

    @Test
    public void testFontSizeSelected_updatesFontSizeConstant() {
        mListPreference.getOnPreferenceChangeListener().onPreferenceChange(
                mListPreference, "1");

        assertThat(Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_PRESET, 4)).isEqualTo(1);
    }
}
