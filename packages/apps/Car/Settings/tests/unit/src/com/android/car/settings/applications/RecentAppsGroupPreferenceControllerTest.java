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
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
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
public class RecentAppsGroupPreferenceControllerTest {

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private RecentAppsGroupPreferenceController mPreferenceController;
    private LogicalPreferenceGroup mPreference;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mMockFragmentController;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreferenceController = new RecentAppsGroupPreferenceController(mContext,
                /* preferenceKey= */ "key", mMockFragmentController,
                mCarUxRestrictions);
        mPreference = new LogicalPreferenceGroup(mContext);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
    }

    @Test
    public void onCreate_isAvailableByDefault() {
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceController.getAvailabilityStatus())
                .isEqualTo(AVAILABLE);
    }

    @Test
    public void onCreate_isAvailableByDefault_zoneWrite() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
    }

    @Test
    public void onCreate_isAvailableByDefault_zoneRead() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void onCreate_isAvailableByDefault_zoneHidden() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onRecentAppsCallback_empty_isUnavailable() {
        List<UsageStats> usageStats = new ArrayList<>();
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onRecentAppStatsLoaded(usageStats);

        assertThat(mPreferenceController.getAvailabilityStatus())
                .isEqualTo(CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onRecentAppsCallback_empty_isUnavailable_zoneWrite() {
        List<UsageStats> usageStats = new ArrayList<>();
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onRecentAppStatsLoaded(usageStats);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onRecentAppsCallback_empty_isUnavailable_zoneRead() {
        List<UsageStats> usageStats = new ArrayList<>();
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onRecentAppStatsLoaded(usageStats);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onRecentAppsCallback_empty_isUnavailable_zoneHidden() {
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
                .isEqualTo(AVAILABLE);
    }

    @Test
    public void onRecentAppsCallback_notEmpty_isUnavailable_zoneWrite() {
        List<UsageStats> usageStats = new ArrayList<>();
        usageStats.add(new UsageStats());
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onRecentAppStatsLoaded(usageStats);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
    }

    @Test
    public void onRecentAppsCallback_notEmpty_isUnavailable_zoneRead() {
        List<UsageStats> usageStats = new ArrayList<>();
        usageStats.add(new UsageStats());
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onRecentAppStatsLoaded(usageStats);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
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
}
