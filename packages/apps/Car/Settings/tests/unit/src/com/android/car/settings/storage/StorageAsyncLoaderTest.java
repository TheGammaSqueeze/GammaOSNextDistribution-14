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

package com.android.car.settings.storage;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.app.usage.StorageStats;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.util.DataUnit;
import android.util.SparseArray;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.profiles.ProfileHelper;
import com.android.settingslib.applications.StorageStatsSource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class StorageAsyncLoaderTest {

    private static final int PRIMARY_USER_ID = 0;
    private static final int SECONDARY_USER_ID = 10;
    private static final String PACKAGE_NAME_1 = "com.blah.test";
    private static final String PACKAGE_NAME_2 = "com.blah.test2";
    private static final long DEFAULT_QUOTA = DataUnit.MEBIBYTES.toBytes(64);

    private Context mContext = ApplicationProvider.getApplicationContext();
    private List<UserInfo> mUsers;

    private StorageAsyncLoader mLoader;

    @Mock
    private StorageStatsSource mMockSource;
    @Mock
    private PackageManager mMockPackageManager;
    @Mock
    private ProfileHelper mMockProfileHelper;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mLoader = new StorageAsyncLoader(mContext, mMockSource, mMockPackageManager,
                mMockProfileHelper);
        UserInfo info = new UserInfo();
        info.id = PRIMARY_USER_ID;
        mUsers = new ArrayList<>();
        mUsers.add(info);
        when(mMockProfileHelper.getAllProfiles()).thenReturn(mUsers);
        when(mMockSource.getCacheQuotaBytes(any(), anyInt())).thenReturn(DEFAULT_QUOTA);
    }

    @Test
    public void testLoadingApps() throws Exception {
        ApplicationInfo appInfo1 = createAppInfo(PACKAGE_NAME_1, 0, 1, 10,
                ApplicationInfo.CATEGORY_UNDEFINED);
        ApplicationInfo appInfo2 = createAppInfo(PACKAGE_NAME_2, 0, 100, 1000,
                ApplicationInfo.CATEGORY_UNDEFINED);
        when(mMockPackageManager.getInstalledApplicationsAsUser(
                /* getAllInstalledApplications= */ 0, PRIMARY_USER_ID))
                .thenReturn(Arrays.asList(appInfo1, appInfo2));

        SparseArray<StorageAsyncLoader.AppsStorageResult> result = mLoader.loadInBackground();

        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get(PRIMARY_USER_ID).getGamesSize()).isEqualTo(0L);
        assertThat(result.get(PRIMARY_USER_ID).getOtherAppsSize()).isEqualTo(2200L);
    }

    @Test
    public void testGamesAreFiltered() throws Exception {
        ApplicationInfo appInfo = createAppInfo(PACKAGE_NAME_1, 0, 1, 10,
                ApplicationInfo.CATEGORY_GAME);
        when(mMockPackageManager.getInstalledApplicationsAsUser(
                /* getAllInstalledApplications= */ 0, PRIMARY_USER_ID))
                .thenReturn(Collections.singletonList(appInfo));

        SparseArray<StorageAsyncLoader.AppsStorageResult> result = mLoader.loadInBackground();

        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get(PRIMARY_USER_ID).getGamesSize()).isEqualTo(11L);
        assertThat(result.get(PRIMARY_USER_ID).getOtherAppsSize()).isEqualTo(0L);
    }

    @Test
    public void testLegacyGamesAreFiltered() throws Exception {
        ApplicationInfo appInfo = createAppInfo(PACKAGE_NAME_1, 0, 1, 10,
                ApplicationInfo.CATEGORY_UNDEFINED);
        appInfo.flags = ApplicationInfo.FLAG_IS_GAME;
        when(mMockPackageManager.getInstalledApplicationsAsUser(
                /* getAllInstalledApplications= */ 0, PRIMARY_USER_ID))
                .thenReturn(Collections.singletonList(appInfo));

        SparseArray<StorageAsyncLoader.AppsStorageResult> result = mLoader.loadInBackground();

        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get(PRIMARY_USER_ID).getGamesSize()).isEqualTo(11L);
        assertThat(result.get(PRIMARY_USER_ID).getOtherAppsSize()).isEqualTo(0L);
    }

    @Test
    public void testCacheIsNotIgnored() throws Exception {
        ApplicationInfo appInfo = createAppInfo(PACKAGE_NAME_1, 100, 1, 10,
                ApplicationInfo.CATEGORY_UNDEFINED);
        when(mMockPackageManager.getInstalledApplicationsAsUser(
                /* getAllInstalledApplications= */ 0, PRIMARY_USER_ID))
                .thenReturn(Collections.singletonList(appInfo));

        SparseArray<StorageAsyncLoader.AppsStorageResult> result = mLoader.loadInBackground();

        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get(PRIMARY_USER_ID).getOtherAppsSize()).isEqualTo(111L);
    }

    @Test
    public void testMultipleUsers() throws Exception {
        UserInfo info = new UserInfo();
        info.id = SECONDARY_USER_ID;
        mUsers.add(info);
        ApplicationInfo appInfo = createAppInfo(PACKAGE_NAME_1, 100, 1, 10,
                ApplicationInfo.CATEGORY_UNDEFINED);
        when(mMockPackageManager.getInstalledApplicationsAsUser(
                /* getAllInstalledApplications= */ eq(0), anyInt()))
                .thenReturn(Collections.singletonList(appInfo));
        when(mMockSource.getExternalStorageStats(any(), eq(UserHandle.SYSTEM)))
                .thenReturn(new StorageStatsSource.ExternalStorageStats(9, 2, 3, 4, 0));
        when(mMockSource.getExternalStorageStats(any(), eq(new UserHandle(SECONDARY_USER_ID))))
                .thenReturn(new StorageStatsSource.ExternalStorageStats(10, 3, 3, 4, 0));
        SparseArray<StorageAsyncLoader.AppsStorageResult> result = mLoader.loadInBackground();

        assertThat(result.size()).isEqualTo(2);
        assertThat(result.get(PRIMARY_USER_ID).getExternalStats().totalBytes).isEqualTo(9L);
        assertThat(result.get(SECONDARY_USER_ID).getExternalStats().totalBytes).isEqualTo(10L);
    }

    @Test
    public void testUpdatedSystemAppCodeSizeIsCounted() throws Exception {
        ApplicationInfo appInfo = createAppInfo(PACKAGE_NAME_1, 100, 1, 10,
                ApplicationInfo.CATEGORY_UNDEFINED);
        appInfo.flags = ApplicationInfo.FLAG_SYSTEM & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
        when(mMockPackageManager.getInstalledApplicationsAsUser(
                /* getAllInstalledApplications= */ 0, PRIMARY_USER_ID))
                .thenReturn(Collections.singletonList(appInfo));

        SparseArray<StorageAsyncLoader.AppsStorageResult> result = mLoader.loadInBackground();

        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get(PRIMARY_USER_ID).getOtherAppsSize()).isEqualTo(111L);
    }

    @Test
    public void testVideoAppsAreFiltered() throws Exception {
        ApplicationInfo appInfo = createAppInfo(PACKAGE_NAME_1, 0, 1, 10,
                ApplicationInfo.CATEGORY_VIDEO);
        when(mMockPackageManager.getInstalledApplicationsAsUser(
                /* getAllInstalledApplications= */ 0, PRIMARY_USER_ID))
                .thenReturn(Collections.singletonList(appInfo));

        SparseArray<StorageAsyncLoader.AppsStorageResult> result = mLoader.loadInBackground();

        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get(PRIMARY_USER_ID).getVideoAppsSize()).isEqualTo(11L);
        assertThat(result.get(PRIMARY_USER_ID).getOtherAppsSize()).isEqualTo(0L);
    }

    @Test
    public void testRemovedPackageDoesNotCrash() throws Exception {
        ApplicationInfo appInfo = createAppInfo(PACKAGE_NAME_1, 0, 1, 10,
                ApplicationInfo.CATEGORY_UNDEFINED);
        when(mMockPackageManager.getInstalledApplicationsAsUser(
                /* getAllInstalledApplications= */ 0, PRIMARY_USER_ID))
                .thenReturn(Collections.singletonList(appInfo));
        when(mMockSource.getStatsForPackage(any(), anyString(), any(UserHandle.class)))
                .thenThrow(new PackageManager.NameNotFoundException());

        SparseArray<StorageAsyncLoader.AppsStorageResult> result = mLoader.loadInBackground();

        // Should not crash.
    }

    @Test
    public void testPackageIsNotDoubleCounted() throws Exception {
        UserInfo info = new UserInfo();
        info.id = SECONDARY_USER_ID;
        mUsers.add(info);
        when(mMockSource.getExternalStorageStats(anyString(), eq(UserHandle.SYSTEM)))
                .thenReturn(new StorageStatsSource.ExternalStorageStats(9, 2, 3,
                        4, 0));
        when(mMockSource.getExternalStorageStats(anyString(),
                eq(new UserHandle(SECONDARY_USER_ID))))
                .thenReturn(new StorageStatsSource.ExternalStorageStats(10, 3, 3,
                        4, 0));

        ApplicationInfo appInfo = createAppInfo(PACKAGE_NAME_1, 0, 1, 10,
                ApplicationInfo.CATEGORY_VIDEO);
        when(mMockPackageManager.getInstalledApplicationsAsUser(
                /* getAllInstalledApplications= */ eq(0), anyInt()))
                .thenReturn(Collections.singletonList(appInfo));

        SparseArray<StorageAsyncLoader.AppsStorageResult> result = mLoader.loadInBackground();

        assertThat(result.size()).isEqualTo(2);
        assertThat(result.get(PRIMARY_USER_ID).getVideoAppsSize()).isEqualTo(11L);
        // No code size for the second user.
        assertThat(result.get(SECONDARY_USER_ID).getVideoAppsSize()).isEqualTo(10L);
    }

    @Test
    public void testCacheOveragesAreCountedAsFree() throws Exception {
        ApplicationInfo appInfo = createAppInfo(PACKAGE_NAME_1, DEFAULT_QUOTA + 100, 1, 10,
                ApplicationInfo.CATEGORY_UNDEFINED);

        when(mMockPackageManager.getInstalledApplicationsAsUser(
                /* getAllInstalledApplications= */ 0, PRIMARY_USER_ID))
                .thenReturn(Collections.singletonList(appInfo));

        SparseArray<StorageAsyncLoader.AppsStorageResult> result = mLoader.loadInBackground();

        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get(PRIMARY_USER_ID).getOtherAppsSize()).isEqualTo(DEFAULT_QUOTA + 11);
    }

    private ApplicationInfo createAppInfo(String packageName, long cacheSize, long codeSize,
            long dataSize, int category) throws Exception {
        StorageStats stats = new StorageStats();
        stats.codeBytes = codeSize;
        stats.dataBytes = dataSize + cacheSize;
        stats.cacheBytes = cacheSize;
        StorageStatsSource.AppStorageStats storageStats =
                new StorageStatsSource.AppStorageStatsImpl(stats);

        when(mMockSource.getStatsForPackage(any(), anyString(), any(UserHandle.class)))
                .thenReturn(storageStats);

        ApplicationInfo info = new ApplicationInfo();
        info.packageName = packageName;
        info.category = category;
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.applicationInfo = info;
        packageInfo.packageName = packageName;

        return info;
    }
}
