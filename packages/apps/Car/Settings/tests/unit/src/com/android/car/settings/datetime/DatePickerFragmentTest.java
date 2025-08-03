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

import android.app.Activity;
import android.app.timedetector.TimeDetectorHelper;
import android.widget.DatePicker;

import androidx.fragment.app.FragmentManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.R;
import com.android.car.settings.testutils.BaseCarSettingsTestActivity;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Calendar;
import java.util.GregorianCalendar;

@RunWith(AndroidJUnit4.class)
public class DatePickerFragmentTest {
    private DatePickerFragment mFragment;
    private FragmentManager mFragmentManager;

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();
    @Rule
    public ActivityTestRule<BaseCarSettingsTestActivity> mActivityTestRule =
            new ActivityTestRule<>(BaseCarSettingsTestActivity.class);

    @Before
    public void setUp() throws Throwable {
        mFragmentManager = mActivityTestRule.getActivity().getSupportFragmentManager();
        setUpFragment();
    }

    @Test
    public void onActivityCreated_setMinDate() {
        DatePicker datePicker = findDatePicker(mFragment.requireActivity());

        GregorianCalendar calendar = new GregorianCalendar();
        calendar.clear();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.MONTH, Calendar.JANUARY);
        calendar.set(Calendar.YEAR, TimeDetectorHelper.INSTANCE.getManualDateSelectionYearMin());

        assertThat(datePicker.getMinDate()).isEqualTo(calendar.getTimeInMillis());
    }

    @Test
    public void onActivityCreated_setMaxDate() {
        DatePicker datePicker = findDatePicker(mFragment.requireActivity());

        GregorianCalendar calendar = new GregorianCalendar();
        calendar.clear();
        calendar.set(Calendar.DAY_OF_MONTH, 31);
        calendar.set(Calendar.MONTH, Calendar.DECEMBER);
        calendar.set(Calendar.YEAR, TimeDetectorHelper.INSTANCE.getManualDateSelectionYearMax());

        assertThat(datePicker.getMaxDate()).isEqualTo(calendar.getTimeInMillis());
    }

    private void setUpFragment() throws Throwable {
        String datePickerFragmentTag = "date_picker_fragment";
        mActivityTestRule.runOnUiThread(() -> {
            mFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, new DatePickerFragment(),
                            datePickerFragmentTag)
                    .commitNow();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        mFragment = (DatePickerFragment)
                mFragmentManager.findFragmentByTag(datePickerFragmentTag);
    }

    private DatePicker findDatePicker(Activity activity) {
        return activity.findViewById(R.id.date_picker);
    }
}
