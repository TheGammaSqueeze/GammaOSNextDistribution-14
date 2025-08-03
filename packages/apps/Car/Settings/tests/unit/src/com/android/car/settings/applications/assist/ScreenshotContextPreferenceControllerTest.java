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

package com.android.car.settings.applications.assist;

import static com.google.common.truth.Truth.assertThat;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.provider.Settings;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.TwoStatePreference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.car.ui.preference.CarUiSwitchPreference;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class ScreenshotContextPreferenceControllerTest {

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private TwoStatePreference mPreference;
    private ScreenshotContextPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mMockFragmentController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mPreference = new CarUiSwitchPreference(mContext);
        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreferenceController = new ScreenshotContextPreferenceController(mContext,
                /* preferenceKey= */ "key", mMockFragmentController,
                mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);

        mPreferenceController.onCreate(mLifecycleOwner);
    }

    @Test
    public void refreshUi_screenshotEnabled_preferenceChecked() {
        mPreference.setChecked(false);

        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.ASSIST_SCREENSHOT_ENABLED, 1);
        mPreferenceController.refreshUi();

        assertThat(mPreference.isChecked()).isTrue();
    }

    @Test
    public void refreshUi_screenshotDisabled_preferenceUnchecked() {
        mPreference.setChecked(true);

        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.ASSIST_SCREENSHOT_ENABLED, 0);
        mPreferenceController.refreshUi();

        assertThat(mPreference.isChecked()).isFalse();
    }

    @Test
    public void refreshUi_structureEnabled_preferenceEnabled() {
        mPreference.setEnabled(false);

        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.ASSIST_STRUCTURE_ENABLED, 1);
        mPreferenceController.refreshUi();

        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    public void refreshUi_structureDisabled_preferenceDisabled() {
        mPreference.setEnabled(true);

        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.ASSIST_STRUCTURE_ENABLED, 0);
        mPreferenceController.refreshUi();

        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void callChangeListener_toggleTrue_screenshotEnabled() {
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.ASSIST_SCREENSHOT_ENABLED, 0);
        mPreference.callChangeListener(true);

        assertThat(Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.ASSIST_SCREENSHOT_ENABLED, 0)).isEqualTo(1);
    }

    @Test
    public void callChangeListener_toggleFalse_screenshotDisabled() {
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.ASSIST_SCREENSHOT_ENABLED, 1);
        mPreference.callChangeListener(false);

        assertThat(Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.ASSIST_SCREENSHOT_ENABLED, 1)).isEqualTo(0);
    }
}
