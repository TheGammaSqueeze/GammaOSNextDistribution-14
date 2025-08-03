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

import android.os.Bundle;
import android.text.format.DateUtils;

import androidx.fragment.app.FragmentManager;
import androidx.test.annotation.UiThreadTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.R;
import com.android.car.settings.testutils.BaseCarSettingsTestActivity;
import com.android.settingslib.net.NetworkCycleChartData;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

/** Unit test for {@link AppDataUsageFragment}. */
@RunWith(AndroidJUnit4.class)
public class AppDataUsageFragmentTest {
    private static final String KEY_START = "start";
    private static final String KEY_END = "end";

    private TestAppDataUsageFragment mFragment;
    private FragmentManager mFragmentManager;

    @Rule
    public ActivityTestRule<BaseCarSettingsTestActivity> mActivityTestRule =
            new ActivityTestRule<>(BaseCarSettingsTestActivity.class);

    @Before
    public void setUp() throws Throwable {
        MockitoAnnotations.initMocks(this);
        mFragmentManager = mActivityTestRule.getActivity().getSupportFragmentManager();
    }

    @Test
    @UiThreadTest
    public void onActivityCreated_noDataCycles_startAndEndDateShouldHaveFourWeeksDifference()
            throws Throwable {
        setUpFragment(/* hasDataCycles= */ false);

        Bundle bundle = mFragment.getBundle();
        long start = bundle.getLong(KEY_START);
        long end = bundle.getLong(KEY_END);
        long timeDiff = end - start;

        assertThat(timeDiff).isEqualTo(DateUtils.WEEK_IN_MILLIS * 4);
    }

    @Test
    @UiThreadTest
    public void onActivityCreated_dataCyclePicked_showsDataCycle() throws Throwable {
        long startTime = System.currentTimeMillis();
        long endTime = System.currentTimeMillis() + DateUtils.WEEK_IN_MILLIS * 4;
        String cycle = "cycle_key";
        Map<CharSequence, NetworkCycleChartData> dataCycles = new HashMap<>();
        NetworkCycleChartData.Builder builder = new NetworkCycleChartData.Builder();
        builder.setStartTime(startTime)
                .setEndTime(endTime);
        dataCycles.put(cycle, builder.build());

        setUpFragment(/* hasDataCycles= */ true);
        mFragment.onDataCyclePicked(cycle, dataCycles);

        Bundle bundle = mFragment.getBundle();
        long start = bundle.getLong(KEY_START);
        long end = bundle.getLong(KEY_END);

        assertThat(start).isEqualTo(startTime);
        assertThat(end).isEqualTo(endTime);
    }

    private void setUpFragment(boolean hasDataCycles)
            throws Throwable {
        String appDataUsageFragmentTag = "app_data_usage_fragment";
        mActivityTestRule.runOnUiThread(() -> {
            mFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container,
                            TestAppDataUsageFragment.newInstance(hasDataCycles),
                            appDataUsageFragmentTag)
                    .commitNow();
        });

        mFragment = (TestAppDataUsageFragment) mFragmentManager
                .findFragmentByTag(appDataUsageFragmentTag);
    }

    public static class TestAppDataUsageFragment extends AppDataUsageFragment {

        // Ensure onDataCyclePicked() isn't called on test devices with data plans
        private boolean mHasDataCycles;

        public static TestAppDataUsageFragment newInstance(boolean hasDataCycles) {
            TestAppDataUsageFragment fragment = new TestAppDataUsageFragment();
            fragment.mHasDataCycles = hasDataCycles;
            return fragment;
        }

        @Override
        public void onDataCyclePicked(String cycle, Map<CharSequence,
                NetworkCycleChartData> usages) {
            if (!mHasDataCycles) {
                return;
            }
            super.onDataCyclePicked(cycle, usages);
        }
    }
}
