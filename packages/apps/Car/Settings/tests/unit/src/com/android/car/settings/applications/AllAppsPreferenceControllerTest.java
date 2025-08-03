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

package com.android.car.settings.applications;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;

import android.app.usage.UsageStats;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class AllAppsPreferenceControllerTest {

    private static final int TEST_APP_COUNT = 3;

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private AllAppsPreferenceController mPreferenceController;
    private Preference mPreference;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mMockFragmentController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreferenceController = new AllAppsPreferenceController(mContext,
                /* preferenceKey= */ "key", mMockFragmentController,
                mCarUxRestrictions);
        mPreference = new Preference(mContext);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
    }

    @Test
    public void onCreate_isUnavailableByDefault() {
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceController.getAvailabilityStatus())
                .isEqualTo(CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onCreate_isUnavailableByDefault_zoneWrite() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onCreate_isUnavailableByDefault_zoneRead() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onCreate_isUnavailableByDefault_zoneHidden() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onRecentAppsCallback_empty_isAvailable() {
        List<UsageStats> usageStats = new ArrayList<>();
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onRecentAppStatsLoaded(usageStats);

        assertThat(mPreferenceController.getAvailabilityStatus())
                .isEqualTo(AVAILABLE);
    }

    @Test
    public void onRecentAppsCallback_empty_isAvailable_zoneWrite() {
        List<UsageStats> usageStats = new ArrayList<>();
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onRecentAppStatsLoaded(usageStats);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
    }

    @Test
    public void onRecentAppsCallback_empty_isAvailable_zoneRead() {
        List<UsageStats> usageStats = new ArrayList<>();
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onRecentAppStatsLoaded(usageStats);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void onRecentAppsCallback_empty_isAvailable_zoneHidden() {
        List<UsageStats> usageStats = new ArrayList<>();
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onRecentAppStatsLoaded(usageStats);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onRecentAppsCallback_notEmpty_isUnavailable() {
        List<UsageStats> usageStats = new ArrayList<>();
        usageStats.add(new UsageStats());
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onRecentAppStatsLoaded(usageStats);

        assertThat(mPreferenceController.getAvailabilityStatus())
                .isEqualTo(CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onRecentAppsCallback_notEmpty_isUnavailable_zoneWrite() {
        List<UsageStats> usageStats = new ArrayList<>();
        usageStats.add(new UsageStats());
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onRecentAppStatsLoaded(usageStats);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onRecentAppsCallback_notEmpty_isUnavailable_zoneRead() {
        List<UsageStats> usageStats = new ArrayList<>();
        usageStats.add(new UsageStats());
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onRecentAppStatsLoaded(usageStats);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onRecentAppsCallback_notEmpty_isUnavailable_zoneHidden() {
        List<UsageStats> usageStats = new ArrayList<>();
        usageStats.add(new UsageStats());
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onRecentAppStatsLoaded(usageStats);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onAppCountCallback_summarySet() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onInstalledAppCountLoaded(TEST_APP_COUNT);

        assertThat(mPreference.getSummary()).isEqualTo(mContext.getResources().getString(
                R.string.apps_view_all_apps_title, TEST_APP_COUNT));
    }
}
