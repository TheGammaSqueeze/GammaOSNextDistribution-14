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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.net.NetworkTemplate;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.ListPreference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.settingslib.NetworkPolicyEditor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.TimeZone;

@RunWith(AndroidJUnit4.class)
public class CycleResetDayOfMonthPickerPreferenceControllerTest {
    private static int STARTING_VALUE = 15;

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private CarUxRestrictions mCarUxRestrictions;
    private CycleResetDayOfMonthPickerPreferenceController mPreferenceController;
    private ListPreference mPreference;

    @Mock
    private FragmentController mMockFragmentController;
    @Mock
    private NetworkPolicyEditor mMockPolicyEditor;
    @Mock
    private NetworkTemplate mMockNetworkTemplate;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreferenceController = new CycleResetDayOfMonthPickerPreferenceController(mContext,
                /* preferenceKey= */ "key", mMockFragmentController,
                mCarUxRestrictions);
        mPreference = new ListPreference(mContext);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.setNetworkPolicyEditor(mMockPolicyEditor);
        mPreferenceController.setNetworkTemplate(mMockNetworkTemplate);
        when(mMockPolicyEditor.getPolicyCycleDay(mMockNetworkTemplate)).thenReturn(STARTING_VALUE);
        mPreferenceController.onCreate(mLifecycleOwner);
    }

    @Test
    public void onCreate_createEntries() {
        CharSequence[] entries = mPreference.getEntries();

        assertThat(entries[0]).isEqualTo(
                String.valueOf(CycleResetDayOfMonthPickerPreferenceController.MIN_DAY));
        assertThat(entries[entries.length - 1]).isEqualTo(
                String.valueOf(CycleResetDayOfMonthPickerPreferenceController.MAX_DAY));
    }

    @Test
    public void onCreate_setsSummary() {
        String prefix = mContext.getString(R.string.cycle_reset_day_of_month_picker_subtitle);
        assertThat(mPreference.getSummary()).isEqualTo(BidiFormatter.getInstance().unicodeWrap(
                prefix + " " + STARTING_VALUE,
                TextDirectionHeuristics.LOCALE));
    }

    @Test
    public void onPreferenceChanged_setsCycleDay() {
        int newValue = STARTING_VALUE + 1;
        mPreferenceController.handlePreferenceChanged(mPreference, String.valueOf(newValue));

        ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
        verify(mMockPolicyEditor).setPolicyCycleDay(
                eq(mMockNetworkTemplate), captor.capture(), eq(TimeZone.getDefault().getID()));
        assertThat(captor.getValue()).isEqualTo(newValue);
    }
}
