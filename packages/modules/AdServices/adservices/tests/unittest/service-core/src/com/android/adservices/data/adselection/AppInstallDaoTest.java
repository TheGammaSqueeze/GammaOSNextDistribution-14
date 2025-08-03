/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.data.adselection;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.content.Context;
import android.content.pm.ApplicationInfo;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.MockitoSession;

import java.util.Arrays;
import java.util.List;

public class AppInstallDaoTest {
    public static AdTechIdentifier BUYER_1 = CommonFixture.VALID_BUYER_1;
    public static AdTechIdentifier BUYER_2 = CommonFixture.VALID_BUYER_2;
    public static String PACKAGE_1 = CommonFixture.TEST_PACKAGE_NAME_1;
    public static String PACKAGE_2 = CommonFixture.TEST_PACKAGE_NAME_2;

    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();

    private AppInstallDao mAppInstallDao;
    private MockitoSession mStaticMockSession = null;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() {
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .mockStatic(PackageManagerCompatUtils.class)
                        .initMocks(this)
                        .startMocking();
        mAppInstallDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, SharedStorageDatabase.class)
                        .build()
                        .appInstallDao();
    }

    @After
    public void cleanup() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
        mAppInstallDao.deleteByPackageName(PACKAGE_1);
        mAppInstallDao.deleteByPackageName(PACKAGE_2);
    }

    @Test
    public void testSetThenRead() {
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1, Arrays.asList(new DBAppInstallPermissions(BUYER_1, PACKAGE_1)));

        assertTrue(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1));
    }

    @Test
    public void testSetThenDelete() {
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1, Arrays.asList(new DBAppInstallPermissions(BUYER_1, PACKAGE_1)));
        assertEquals(1, mAppInstallDao.deleteByPackageName(PACKAGE_1));
    }

    @Test
    public void testSetThenDeleteThenRead() {
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1, Arrays.asList(new DBAppInstallPermissions(BUYER_1, PACKAGE_1)));
        mAppInstallDao.deleteByPackageName(PACKAGE_1);

        assertFalse(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1));
    }

    @Test
    public void testSetThenReadMultiple() {
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1,
                Arrays.asList(
                        new DBAppInstallPermissions(BUYER_1, PACKAGE_1),
                        new DBAppInstallPermissions(BUYER_2, PACKAGE_2)));

        assertTrue(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1));
        assertTrue(mAppInstallDao.canBuyerFilterPackage(BUYER_2, PACKAGE_2));
    }

    @Test
    public void testSetThenReadMultipleSeparateCalls() {
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1, Arrays.asList(new DBAppInstallPermissions(BUYER_1, PACKAGE_1)));
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_2, Arrays.asList(new DBAppInstallPermissions(BUYER_2, PACKAGE_2)));

        assertTrue(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1));
        assertTrue(mAppInstallDao.canBuyerFilterPackage(BUYER_2, PACKAGE_2));
    }

    @Test
    public void testSetThenReadMultipleBuyers() {
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1,
                Arrays.asList(
                        new DBAppInstallPermissions(BUYER_1, PACKAGE_1),
                        new DBAppInstallPermissions(BUYER_2, PACKAGE_1)));
        assertTrue(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1));
        assertTrue(mAppInstallDao.canBuyerFilterPackage(BUYER_2, PACKAGE_1));
    }

    @Test
    public void testSetThenDeleteMultipleBuyers() {
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1,
                Arrays.asList(
                        new DBAppInstallPermissions(BUYER_1, PACKAGE_1),
                        new DBAppInstallPermissions(BUYER_2, PACKAGE_1)));
        assertEquals(2, mAppInstallDao.deleteByPackageName(PACKAGE_1));
    }

    @Test
    public void testSetThenDeleteThenReadMultipleBuyers() {
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1,
                Arrays.asList(
                        new DBAppInstallPermissions(BUYER_1, PACKAGE_1),
                        new DBAppInstallPermissions(BUYER_2, PACKAGE_1)));
        mAppInstallDao.deleteByPackageName(PACKAGE_1);
        assertFalse(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1));
        assertFalse(mAppInstallDao.canBuyerFilterPackage(BUYER_2, PACKAGE_1));
    }

    @Test
    public void testSetThenReadMultiplePackages() {
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1,
                Arrays.asList(
                        new DBAppInstallPermissions(BUYER_1, PACKAGE_1),
                        new DBAppInstallPermissions(BUYER_1, PACKAGE_2)));
        assertTrue(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1));
        assertTrue(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_2));
    }

    @Test
    public void testSetThenDeleteThenReadMultiplePackages() {
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1,
                Arrays.asList(
                        new DBAppInstallPermissions(BUYER_1, PACKAGE_1),
                        new DBAppInstallPermissions(BUYER_1, PACKAGE_2)));
        mAppInstallDao.deleteByPackageName(PACKAGE_1);
        mAppInstallDao.deleteByPackageName(PACKAGE_2);
        assertFalse(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1));
        assertFalse(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_2));
    }

    @Test
    public void testSetAdTechsForPackageDeletesExisting() {
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1,
                Arrays.asList(
                        new DBAppInstallPermissions(BUYER_1, PACKAGE_1),
                        new DBAppInstallPermissions(BUYER_2, PACKAGE_2)));
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1, Arrays.asList(new DBAppInstallPermissions(BUYER_2, PACKAGE_1)));
        assertFalse(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1));
        assertTrue(mAppInstallDao.canBuyerFilterPackage(BUYER_2, PACKAGE_2));
        assertTrue(mAppInstallDao.canBuyerFilterPackage(BUYER_2, PACKAGE_1));
    }

    @Test
    public void testDeleteAll() {
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1,
                Arrays.asList(
                        new DBAppInstallPermissions(BUYER_1, PACKAGE_1),
                        new DBAppInstallPermissions(BUYER_2, PACKAGE_1)));
        mAppInstallDao.deleteAllAppInstallData();
        assertFalse(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1));
        assertFalse(mAppInstallDao.canBuyerFilterPackage(BUYER_2, PACKAGE_1));
    }

    @Test
    public void testGetAllPackageNames() {
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1, Arrays.asList(new DBAppInstallPermissions(BUYER_1, PACKAGE_1)));
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_2, Arrays.asList(new DBAppInstallPermissions(BUYER_1, PACKAGE_2)));
        List<String> unorderedExpected = Arrays.asList(PACKAGE_1, PACKAGE_2);
        assertThat(mAppInstallDao.getAllPackageNames())
                .containsExactlyElementsIn(unorderedExpected);
    }

    @Test
    public void testDeleteAllDisallowedPackageEntries() {
        class FlagsThatAllowOneApp implements Flags {
            @Override
            public String getPpapiAppAllowList() {
                return PACKAGE_1;
            }
        }
        ApplicationInfo installedPackage1 = new ApplicationInfo();
        installedPackage1.packageName = PACKAGE_1;
        ApplicationInfo installedPackage2 = new ApplicationInfo();
        installedPackage2.packageName = PACKAGE_2;
        doReturn(Arrays.asList(installedPackage1, installedPackage2))
                .when(() -> PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()));
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1, Arrays.asList(new DBAppInstallPermissions(BUYER_1, PACKAGE_1)));
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_2, Arrays.asList(new DBAppInstallPermissions(BUYER_1, PACKAGE_2)));
        assertEquals(
                1,
                mAppInstallDao.deleteAllDisallowedPackageEntries(
                        CONTEXT.getPackageManager(), new FlagsThatAllowOneApp()));
        assertEquals(Arrays.asList(PACKAGE_1), mAppInstallDao.getAllPackageNames());
    }
}
