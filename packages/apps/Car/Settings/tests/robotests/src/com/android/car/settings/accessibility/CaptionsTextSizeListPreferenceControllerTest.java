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
public class CaptionsTextSizeListPreferenceControllerTest {

    private Context mContext;
    private PreferenceControllerTestHelper<CaptionsTextSizeListPreferenceController>
            mPreferenceControllerHelper;
    private ListPreference mListPreference;
    private String[] mFontSizeTitles;

    @Before
    public void setup() {
        mContext = RuntimeEnvironment.application;
        mListPreference = new ListPreference(mContext);
        mPreferenceControllerHelper =
                new PreferenceControllerTestHelper<>(mContext,
                        CaptionsTextSizeListPreferenceController.class, mListPreference);

        mFontSizeTitles = mPreferenceControllerHelper.getController().mFontSizeTitles;
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
    }

    @After
    public void tearDown() {
        Settings.Secure.putFloat(mContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_FONT_SCALE, 1.0F);
    }

    @Test
    public void testUpdateUi_includesAllTextSizeOptions() {
        mPreferenceControllerHelper.getController().refreshUi();

        assertThat(Arrays.asList(mListPreference.getEntries())).containsExactlyElementsIn(
                mFontSizeTitles);
    }

    @Test
    public void testUpdateUi_selectsCurrentFontSize() {
        Settings.Secure.putFloat(mContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_FONT_SCALE, 1.5F);

        mPreferenceControllerHelper.getController().refreshUi();

        assertThat(mListPreference.getValue()).isEqualTo("1.5");
        assertThat(mListPreference.getSummary()).isEqualTo(mFontSizeTitles[3]);
    }

    @Test
    public void testUpdateUi_noKnownValueSelected_selectsDefaultFontSize() {
        Settings.Secure.putFloat(mContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_FONT_SCALE, 100.0F);

        mPreferenceControllerHelper.getController().refreshUi();

        assertThat(mListPreference.getValue()).isEqualTo("1.0");
        assertThat(mListPreference.getSummary()).isEqualTo(mFontSizeTitles[2]);
    }

    @Test
    public void testFontSizeSelected_updatesFontSizeConstant() {
        mListPreference.getOnPreferenceChangeListener().onPreferenceChange(
                mListPreference, "1.5");

        assertThat(Settings.Secure.getFloat(mContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_FONT_SCALE, 1.0F)).isEqualTo(1.5F);
    }
}
