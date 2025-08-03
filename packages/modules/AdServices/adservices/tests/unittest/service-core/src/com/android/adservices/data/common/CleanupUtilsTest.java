/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.data.common;

import static com.android.adservices.service.common.AllowLists.ALLOW_ALL;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.junit.Assert.assertEquals;

import android.adservices.common.CommonFixture;
import android.content.Context;
import android.content.pm.ApplicationInfo;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoSession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CleanupUtilsTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private MockitoSession mStaticMockSession = null;

    @Before
    public void setup() {
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .mockStatic(PackageManagerCompatUtils.class)
                        .initMocks(this)
                        .startMocking();
    }

    @After
    public void teardown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testEmpty() {
        List<String> packageList = new ArrayList<>();

        CleanupUtils.removeAllowedPackages(
                packageList,
                CONTEXT.getPackageManager(),
                Arrays.asList(CommonFixture.TEST_PACKAGE_NAME_1));

        assertEquals(new ArrayList<>(), packageList);
    }

    @Test
    public void testCleanupNotUninstalled() {
        ApplicationInfo installedPackage1 = new ApplicationInfo();
        installedPackage1.packageName = CommonFixture.TEST_PACKAGE_NAME_1;
        ApplicationInfo installedPackage2 = new ApplicationInfo();
        installedPackage2.packageName = CommonFixture.TEST_PACKAGE_NAME_2;
        doReturn(Arrays.asList(installedPackage1, installedPackage2))
                .when(() -> PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()));
        List<String> packageList =
                new ArrayList<>(
                        Arrays.asList(
                                CommonFixture.TEST_PACKAGE_NAME_1,
                                CommonFixture.TEST_PACKAGE_NAME_2));
        List<String> expected = Arrays.asList(CommonFixture.TEST_PACKAGE_NAME_2);

        CleanupUtils.removeAllowedPackages(
                packageList,
                CONTEXT.getPackageManager(),
                Arrays.asList(CommonFixture.TEST_PACKAGE_NAME_1));

        assertEquals(expected, packageList);
    }

    @Test
    public void testCleanupNotAllowed() {
        ApplicationInfo installedPackage2 = new ApplicationInfo();
        installedPackage2.packageName = CommonFixture.TEST_PACKAGE_NAME_2;
        doReturn(Arrays.asList(installedPackage2))
                .when(() -> PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()));
        List<String> packageList =
                new ArrayList<>(
                        Arrays.asList(
                                CommonFixture.TEST_PACKAGE_NAME_1,
                                CommonFixture.TEST_PACKAGE_NAME_2));
        List<String> expected = Arrays.asList(CommonFixture.TEST_PACKAGE_NAME_1);

        CleanupUtils.removeAllowedPackages(
                packageList, CONTEXT.getPackageManager(), Arrays.asList(ALLOW_ALL));

        assertEquals(expected, packageList);
    }

    @Test
    public void testCleanupMultipleAllowLists() {
        ApplicationInfo installedPackage1 = new ApplicationInfo();
        installedPackage1.packageName = CommonFixture.TEST_PACKAGE_NAME_1;
        ApplicationInfo installedPackage2 = new ApplicationInfo();
        installedPackage2.packageName = CommonFixture.TEST_PACKAGE_NAME_2;
        doReturn(Arrays.asList(installedPackage1, installedPackage2))
                .when(() -> PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()));
        List<String> packageList =
                new ArrayList<>(
                        Arrays.asList(
                                CommonFixture.TEST_PACKAGE_NAME_1,
                                CommonFixture.TEST_PACKAGE_NAME_2));
        List<String> expected = Collections.emptyList();

        CleanupUtils.removeAllowedPackages(
                packageList,
                CONTEXT.getPackageManager(),
                Arrays.asList(
                        CommonFixture.TEST_PACKAGE_NAME_1, CommonFixture.TEST_PACKAGE_NAME_2));

        assertEquals(expected, packageList);
    }

    @Test
    public void testCleanupMultipleAllowListsWildCard() {
        ApplicationInfo installedPackage1 = new ApplicationInfo();
        installedPackage1.packageName = CommonFixture.TEST_PACKAGE_NAME_1;
        ApplicationInfo installedPackage2 = new ApplicationInfo();
        installedPackage2.packageName = CommonFixture.TEST_PACKAGE_NAME_2;
        doReturn(Arrays.asList(installedPackage1, installedPackage2))
                .when(() -> PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()));
        List<String> packageList =
                new ArrayList<>(
                        Arrays.asList(
                                CommonFixture.TEST_PACKAGE_NAME_1,
                                CommonFixture.TEST_PACKAGE_NAME_2));
        List<String> expected = Collections.emptyList();

        CleanupUtils.removeAllowedPackages(
                packageList,
                CONTEXT.getPackageManager(),
                Arrays.asList(CommonFixture.TEST_PACKAGE_NAME_1, ALLOW_ALL));

        assertEquals(expected, packageList);
    }

    @Test
    public void testCleanupMultipleAllowListsRedundant() {
        ApplicationInfo installedPackage1 = new ApplicationInfo();
        installedPackage1.packageName = CommonFixture.TEST_PACKAGE_NAME_1;
        ApplicationInfo installedPackage2 = new ApplicationInfo();
        installedPackage2.packageName = CommonFixture.TEST_PACKAGE_NAME_2;
        doReturn(Arrays.asList(installedPackage1, installedPackage2))
                .when(() -> PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()));
        List<String> packageList =
                new ArrayList<>(
                        Arrays.asList(
                                CommonFixture.TEST_PACKAGE_NAME_1,
                                CommonFixture.TEST_PACKAGE_NAME_2));
        List<String> expected = Arrays.asList(CommonFixture.TEST_PACKAGE_NAME_2);

        CleanupUtils.removeAllowedPackages(
                packageList,
                CONTEXT.getPackageManager(),
                Arrays.asList(
                        CommonFixture.TEST_PACKAGE_NAME_1, CommonFixture.TEST_PACKAGE_NAME_1));

        assertEquals(expected, packageList);
    }
}
