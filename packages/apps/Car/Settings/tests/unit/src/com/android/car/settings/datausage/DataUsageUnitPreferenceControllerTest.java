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

package com.android.car.settings.datausage;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class DataUsageUnitPreferenceControllerTest {

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private CarUxRestrictions mCarUxRestrictions;
    private LogicalPreferenceGroup mPreferenceGroup;
    private DataUsageUnitPreferenceController mPreferenceController;

    @Mock
    private FragmentController mMockFragmentController;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreferenceController = new DataUsageUnitPreferenceController(mContext,
                /* preferenceKey= */ "key", mMockFragmentController,
                mCarUxRestrictions);
        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        screen.addPreference(mPreferenceGroup);
    }

    @Test
    public void checkInitialized_noDefaultSelection_throwException() {
        assertThrows(IllegalStateException.class,
                () -> PreferenceControllerTestUtil.assignPreference(mPreferenceController,
                        mPreferenceGroup));
    }

    @Test
    public void onCreate_setsGbDefault_mbSet() {
        mPreferenceController.setDefaultSelection(/* isGb= */ true);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreferenceGroup);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceController.isGbSelected()).isTrue();
    }

    @Test
    public void onCreate_setsMbDefault_mbSet() {
        mPreferenceController.setDefaultSelection(/* isGb= */ false);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreferenceGroup);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceController.isGbSelected()).isFalse();
    }

    @Test
    public void onCreate_setsGbDefault_onToggleMbSet() {
        mPreferenceController.setDefaultSelection(/* isGb= */ true);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreferenceGroup);
        mPreferenceController.onCreate(mLifecycleOwner);
        Preference mbPreference = mPreferenceGroup
                .findPreference(mContext.getString(R.string.pk_data_usage_radio_mb));
        mbPreference.performClick();

        assertThat(mPreferenceController.isGbSelected()).isFalse();
    }
}
