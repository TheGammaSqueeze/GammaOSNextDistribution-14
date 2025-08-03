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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.app.usage.UsageStats;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.ApplicationInfo;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.settingslib.applications.ApplicationsState;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class RecentAppsListPreferenceControllerTest {

    private static final String MOCK_PACKAGE_NAME_1 = "pkg1";
    private static final String MOCK_PACKAGE_NAME_2 = "pkg2";
    private static final String MOCK_PACKAGE_NAME_3 = "pkg3";
    private static final String MOCK_PACKAGE_NAME_4 = "pkg4";

    private static final String MOCK_APP_NAME_1 = "title1";
    private static final String MOCK_APP_NAME_2 = "title2";
    private static final String MOCK_APP_NAME_3 = "title3";
    private static final String MOCK_APP_NAME_4 = "title4";

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private RecentAppsListPreferenceController mPreferenceController;
    private PreferenceCategory mPreferenceCategory;
    private CarUxRestrictions mCarUxRestrictions;
    private int mMaxEntryCount;

    @Mock
    private FragmentController mMockFragmentController;
    @Mock
    private ApplicationsState mMockApplicationsState;
    @Mock
    private ApplicationInfo mApplicationInfo;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreferenceController = new RecentAppsListPreferenceController(mContext,
                /* preferenceKey= */ "key", mMockFragmentController,
                mCarUxRestrictions, mMockApplicationsState);
        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreferenceCategory = new PreferenceCategory(mContext);
        screen.addPreference(mPreferenceCategory);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreferenceCategory);

        mMaxEntryCount = mContext.getResources().getInteger(
                R.integer.recent_notifications_apps_list_count);
    }

    @Test
    public void onRecentAppsLoaded_displaysApps() {
        mPreferenceController.onCreate(mLifecycleOwner);

        UsageStats usageStats = createUsageStats(MOCK_PACKAGE_NAME_1);
        List<UsageStats> mUsageStatsList = new ArrayList<>();
        mUsageStatsList.add(usageStats);
        ApplicationsState.AppEntry appEntry = createAppEntry(MOCK_APP_NAME_1);

        when(mMockApplicationsState.getEntry(eq(MOCK_PACKAGE_NAME_1), anyInt()))
                .thenReturn(appEntry);

        mPreferenceController.onRecentAppStatsLoaded(mUsageStatsList);

        assertThat(mPreferenceCategory.getPreference(0).getTitle()).isEqualTo(appEntry.label);
    }

    @Test
    public void onRecentAppsLoaded_doesNotDisplayInvalidApps() {
        mPreferenceController.onCreate(mLifecycleOwner);

        UsageStats usageStats1 = createUsageStats(MOCK_PACKAGE_NAME_1);
        UsageStats usageStats2 = createUsageStats(MOCK_PACKAGE_NAME_2);
        List<UsageStats> mUsageStatsList = new ArrayList<>();
        mUsageStatsList.add(usageStats1);
        mUsageStatsList.add(usageStats2);
        ApplicationsState.AppEntry appEntry = createAppEntry(MOCK_APP_NAME_1);

        when(mMockApplicationsState.getEntry(eq(MOCK_PACKAGE_NAME_1), anyInt()))
                .thenReturn(appEntry);
        when(mMockApplicationsState.getEntry(eq(MOCK_PACKAGE_NAME_2), anyInt())).thenReturn(null);

        mPreferenceController.onRecentAppStatsLoaded(mUsageStatsList);

        assertThat(mPreferenceCategory.getPreferenceCount()).isEqualTo(1);
    }

    @Test
    public void onRecentAppsLoaded_moreThanMaximumAllowed_maximumShown() {
        mPreferenceController.onCreate(mLifecycleOwner);

        UsageStats usageStats1 = createUsageStats(MOCK_PACKAGE_NAME_1);
        UsageStats usageStats2 = createUsageStats(MOCK_PACKAGE_NAME_2);
        UsageStats usageStats3 = createUsageStats(MOCK_PACKAGE_NAME_3);
        UsageStats usageStats4 = createUsageStats(MOCK_PACKAGE_NAME_4);
        List<UsageStats> mUsageStatsList = new ArrayList<>();
        mUsageStatsList.add(usageStats1);
        mUsageStatsList.add(usageStats2);
        mUsageStatsList.add(usageStats3);
        mUsageStatsList.add(usageStats4);
        ApplicationsState.AppEntry appEntry1 = createAppEntry(MOCK_APP_NAME_1);
        ApplicationsState.AppEntry appEntry2 = createAppEntry(MOCK_APP_NAME_2);
        ApplicationsState.AppEntry appEntry3 = createAppEntry(MOCK_APP_NAME_3);
        ApplicationsState.AppEntry appEntry4 = createAppEntry(MOCK_APP_NAME_4);

        when(mMockApplicationsState.getEntry(eq(MOCK_PACKAGE_NAME_1), anyInt()))
                .thenReturn(appEntry1);
        when(mMockApplicationsState.getEntry(eq(MOCK_PACKAGE_NAME_2), anyInt()))
                .thenReturn(appEntry2);
        when(mMockApplicationsState.getEntry(eq(MOCK_PACKAGE_NAME_3), anyInt()))
                .thenReturn(appEntry3);
        when(mMockApplicationsState.getEntry(eq(MOCK_PACKAGE_NAME_4), anyInt()))
                .thenReturn(appEntry4);

        mPreferenceController.onRecentAppStatsLoaded(mUsageStatsList);

        assertThat(mPreferenceCategory.getPreferenceCount()).isEqualTo(mMaxEntryCount);
    }

    private UsageStats createUsageStats(String packageName) {
        UsageStats usageStats = new UsageStats();
        usageStats.mPackageName = packageName;
        usageStats.mEndTimeStamp = System.currentTimeMillis();
        return usageStats;
    }

    private ApplicationsState.AppEntry createAppEntry(String label) {
        ApplicationsState.AppEntry appEntry = mock(ApplicationsState.AppEntry.class);
        appEntry.info = mApplicationInfo;
        appEntry.label = label;
        return appEntry;
    }
}
