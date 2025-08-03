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

package com.android.car.settings.wifi.preferences;

import static com.google.common.truth.Truth.assertThat;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.provider.Settings;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.SwitchPreference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class CellularFallbackTogglePreferenceControllerTest {
    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private SwitchPreference mSwitchPreference;
    private CellularFallbackTogglePreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mFragmentController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mSwitchPreference = new SwitchPreference(mContext);
        mPreferenceController = new CellularFallbackTogglePreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mSwitchPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
    }

    @Test
    public void refreshUi_unchecked() {
        Settings.Global.putString(mContext.getContentResolver(),
                Settings.Global.NETWORK_AVOID_BAD_WIFI, null);
        mSwitchPreference.setChecked(true);

        mPreferenceController.refreshUi();
        assertThat(mSwitchPreference.isChecked()).isFalse();
    }

    @Test
    public void refreshUi_checked() {
        Settings.Global.putString(mContext.getContentResolver(),
                Settings.Global.NETWORK_AVOID_BAD_WIFI, "1");
        mSwitchPreference.setChecked(false);

        mPreferenceController.refreshUi();
        assertThat(mSwitchPreference.isChecked()).isTrue();
    }

    @Test
    public void handlePreferenceChanged_toggleFalse_setsNull() {
        Settings.Global.putString(mContext.getContentResolver(),
                Settings.Global.NETWORK_AVOID_BAD_WIFI, "1");

        mSwitchPreference.callChangeListener(false);
        assertThat(Settings.Global.getString(mContext.getContentResolver(),
                Settings.Global.NETWORK_AVOID_BAD_WIFI)).isNull();
    }

    @Test
    public void handlePreferenceChanged_toggleTrue_setsEnabled() {
        Settings.Global.putString(mContext.getContentResolver(),
                Settings.Global.NETWORK_AVOID_BAD_WIFI, null);

        mSwitchPreference.callChangeListener(true);
        assertThat(Settings.Global.getString(mContext.getContentResolver(),
                Settings.Global.NETWORK_AVOID_BAD_WIFI)).isEqualTo("1");
    }
}
