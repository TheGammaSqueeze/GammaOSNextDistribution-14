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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.settingslib.applications.ApplicationsState;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class RecentAppsItemManagerTest {

    private static final String MOCK_PACKAGE_NAME_1 = "pkg1";
    private static final String MOCK_PACKAGE_NAME_2 = "pkg2";
    private static final String MOCK_PACKAGE_NAME_3 = "pkg3";

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private RecentAppsItemManager mRecentAppsItemManager;

    @Mock
    private UsageStatsManager mUsageStatsManager;
    @Mock
    private ApplicationsState mAppState;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private ApplicationsState.AppEntry mAppEntry;
    @Mock
    private ApplicationInfo mApplicationInfo;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mContext.getApplicationContext()).thenReturn(mContext);
        when(mContext.getSystemService(UsageStatsManager.class)).thenReturn(mUsageStatsManager);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);

        mRecentAppsItemManager = new RecentAppsItemManager(mContext,
                3 /* maximumApps */, mAppState);
    }

    @Test
    public void loadDisplayableRecentApps_oneValidRecentAppSet_shouldHaveOneRecentApp() {
        final List<UsageStats> stats = new ArrayList<>();
        final UsageStats stat1 = createUsageStats(MOCK_PACKAGE_NAME_1);
        stats.add(stat1);
        // stat1 is valid app.
        when(mAppState.getEntry(eq(stat1.mPackageName), anyInt()))
                .thenReturn(mAppEntry);
        when(mPackageManager.resolveActivity(any(Intent.class), anyInt()))
                .thenReturn(new ResolveInfo());
        when(mUsageStatsManager.queryUsageStats(anyInt(), anyLong(), anyLong()))
                .thenReturn(stats);
        mAppEntry.info = mApplicationInfo;

        mRecentAppsItemManager.loadDisplayableRecentApps(3);

        assertThat(mRecentAppsItemManager.mRecentApps.size()).isEqualTo(1);
    }

    @Test
    public void loadDisplayableRecentApps_oneValidAndTwoInvalidSet_shouldHaveOneRecentApp() {
        final List<UsageStats> stats = new ArrayList<>();
        final UsageStats stat1 = createUsageStats(MOCK_PACKAGE_NAME_1);
        final UsageStats stat2 = createUsageStats(MOCK_PACKAGE_NAME_2);
        final UsageStats stat3 = createUsageStats(MOCK_PACKAGE_NAME_3);
        stats.add(stat1);
        stats.add(stat2);
        stats.add(stat3);

        // stat1, stat2 are valid apps. stat3 is invalid.
        when(mAppState.getEntry(stat1.mPackageName, UserHandle.myUserId()))
                .thenReturn(mAppEntry);
        when(mAppState.getEntry(stat2.mPackageName, UserHandle.myUserId()))
                .thenReturn(mAppEntry);
        when(mAppState.getEntry(stat3.mPackageName, UserHandle.myUserId()))
                .thenReturn(null);
        when(mPackageManager.resolveActivity(any(Intent.class), anyInt()))
                .thenReturn(new ResolveInfo());
        when(mUsageStatsManager.queryUsageStats(anyInt(), anyLong(), anyLong()))
                .thenReturn(stats);
        mAppEntry.info = mApplicationInfo;

        mRecentAppsItemManager.loadDisplayableRecentApps(3);

        // Only stat1. stat2 is skipped because of the package name, stat3 skipped because
        // it's invalid app.
        assertThat(mRecentAppsItemManager.mRecentApps.size()).isEqualTo(2);
    }

    private UsageStats createUsageStats(String packageName) {
        UsageStats usageStats = new UsageStats();
        usageStats.mPackageName = packageName;
        usageStats.mLastTimeUsed = System.currentTimeMillis();
        return usageStats;
    }
}
