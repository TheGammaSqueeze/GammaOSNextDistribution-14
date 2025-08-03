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

import static org.mockito.ArgumentMatchers.any;

import android.app.Activity;
import android.text.format.DateFormat;
import android.widget.TimePicker;

import androidx.fragment.app.FragmentManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.R;
import com.android.car.settings.testutils.BaseCarSettingsTestActivity;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

@RunWith(AndroidJUnit4.class)
public class TimePickerFragmentTest {
    private TimePickerFragment mFragment;
    private BaseCarSettingsTestActivity mActivity;
    private FragmentManager mFragmentManager;
    private MockitoSession mSession;

    @Rule
    public ActivityTestRule<BaseCarSettingsTestActivity> mActivityTestRule =
            new ActivityTestRule<>(BaseCarSettingsTestActivity.class);

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mActivity = mActivityTestRule.getActivity();
        mFragmentManager = mActivityTestRule.getActivity().getSupportFragmentManager();

        mSession = ExtendedMockito.mockitoSession().spyStatic(DateFormat.class).startMocking();
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void onActivityCreated_isNot24HourFormat_timePickerShouldShow12HourTimeFormat()
            throws Throwable {
        ExtendedMockito.doReturn(false).when(() -> DateFormat.is24HourFormat(any()));
        setUpFragment();
        TimePicker timePicker = findTimePicker(mFragment.requireActivity());

        assertThat(timePicker.is24HourView()).isFalse();
    }

    @Test
    public void onActivityCreated_is24HourFormat_timePickerShouldShow24HourTimeFormat()
            throws Throwable {
        ExtendedMockito.doReturn(true).when(() -> DateFormat.is24HourFormat(any()));
        setUpFragment();
        TimePicker timePicker = findTimePicker(mFragment.requireActivity());

        assertThat(timePicker.is24HourView()).isTrue();
    }

    private void setUpFragment() throws Throwable {
        String timePickerFragmentTag = "time_picker_fragment";
        mActivityTestRule.runOnUiThread(() -> {
            mFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, new TimePickerFragment(),
                            timePickerFragmentTag)
                    .commitNow();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        mFragment = (TimePickerFragment)
                mFragmentManager.findFragmentByTag(timePickerFragmentTag);
    }

    private TimePicker findTimePicker(Activity activity) {
        return activity.findViewById(R.id.time_picker);
    }
}
