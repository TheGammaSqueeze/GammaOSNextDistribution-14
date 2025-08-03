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

package com.android.car.settings.datetime;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.SwitchPreference;
import androidx.preference.TwoStatePreference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class TimeFormatTogglePreferenceControllerTest {

    private Context mContext;
    private TwoStatePreference mPreference;
    private TimeFormatTogglePreferenceController mController;

    @Mock
    private FragmentController mFragmentController;

    @Before
    public void setUp() {
        LifecycleOwner lifecycleOwner = new TestLifecycleOwner();
        MockitoAnnotations.initMocks(this);

        mContext = spy(ApplicationProvider.getApplicationContext());
        mPreference = new SwitchPreference(mContext);

        CarUxRestrictions carUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mController = new TimeFormatTogglePreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, carUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(lifecycleOwner);
    }

    @Test
    public void testRefreshUi_24HourSet_shouldCheckPreference() {
        Settings.System.putString(mContext.getContentResolver(), Settings.System.TIME_12_24,
                TimeFormatTogglePreferenceController.HOURS_24);
        mController.refreshUi();
        assertThat(mPreference.isChecked()).isTrue();
    }

    @Test
    public void testRefreshUi_12HourSet_shouldUncheckPreference() {
        Settings.System.putString(mContext.getContentResolver(), Settings.System.TIME_12_24,
                TimeFormatTogglePreferenceController.HOURS_12);
        mController.refreshUi();
        assertThat(mPreference.isChecked()).isFalse();
    }

    @Test
    public void testOnPreferenceChange_24HourSet_shouldSendIntent() {
        mPreference.setChecked(true);
        mPreference.callChangeListener(true);

        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(1)).sendBroadcast(captor.capture());
        List<Intent> intentsFired = captor.getAllValues();
        assertThat(intentsFired.size()).isEqualTo(1);
        Intent intentFired = intentsFired.get(0);
        assertThat(intentFired.getAction()).isEqualTo(Intent.ACTION_TIME_CHANGED);
        assertThat(intentFired.getIntExtra(Intent.EXTRA_TIME_PREF_24_HOUR_FORMAT, -1))
                .isEqualTo(Intent.EXTRA_TIME_PREF_VALUE_USE_24_HOUR);
    }

    @Test
    public void testOnPreferenceChange_12HourSet_shouldSendIntent() {
        mPreference.setChecked(false);
        mPreference.callChangeListener(false);

        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(1)).sendBroadcast(captor.capture());
        List<Intent> intentsFired = captor.getAllValues();
        assertThat(intentsFired.size()).isEqualTo(1);
        Intent intentFired = intentsFired.get(0);
        assertThat(intentFired.getAction()).isEqualTo(Intent.ACTION_TIME_CHANGED);
        assertThat(intentFired.getIntExtra(Intent.EXTRA_TIME_PREF_24_HOUR_FORMAT, -1))
                .isEqualTo(Intent.EXTRA_TIME_PREF_VALUE_USE_12_HOUR);
    }
}
