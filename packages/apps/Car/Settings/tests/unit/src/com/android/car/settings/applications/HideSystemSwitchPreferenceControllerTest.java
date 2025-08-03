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

package com.android.car.settings.applications;

import static com.google.common.truth.Truth.assertThat;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.SwitchPreference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.ColoredSwitchPreference;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class HideSystemSwitchPreferenceControllerTest {
    private Context mContext = ApplicationProvider.getApplicationContext();
    private SwitchPreference mSwitchPreference;
    private HideSystemSwitchPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private LifecycleOwner mLifecycleOwner;
    private SharedPreferences mSharedPreferences;

    @Mock
    private FragmentController mFragmentController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mSharedPreferences = mContext.getSharedPreferences(
                AppListFragment.SHARED_PREFERENCE_PATH, Context.MODE_PRIVATE);

        mSwitchPreference = new ColoredSwitchPreference(mContext);
        mPreferenceController = new HideSystemSwitchPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mSwitchPreference);
    }

    @After
    public void tearDown() {
        mSharedPreferences.edit().clear().apply();
    }

    @Test
    public void onStart_sharedPreferenceTrue_switchChecked() {
        mSharedPreferences.edit().putBoolean(AppListFragment.KEY_HIDE_SYSTEM, true).apply();

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        assertThat(mSwitchPreference.isChecked()).isTrue();
    }

    @Test
    public void onStart_sharedPreferenceFalse_switchUnchecked() {
        mSharedPreferences.edit().putBoolean(AppListFragment.KEY_HIDE_SYSTEM, false).apply();

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        assertThat(mSwitchPreference.isChecked()).isFalse();
    }

    @Test
    public void onPreferenceClicked_sharedPreferenceToggled() {
        mSharedPreferences.edit().putBoolean(AppListFragment.KEY_HIDE_SYSTEM, true).apply();

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        mSwitchPreference.performClick();
        assertThat(mSharedPreferences.getBoolean(AppListFragment.KEY_HIDE_SYSTEM,
                /* defaultValue= */ true)).isFalse();

        mSwitchPreference.performClick();
        assertThat(mSharedPreferences.getBoolean(AppListFragment.KEY_HIDE_SYSTEM,
                /* defaultValue= */ false)).isTrue();
    }
}
