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

package com.android.adservices.data.signals;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AllowLists;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class ProtectedSignalsDaoTest {
    public static AdTechIdentifier BUYER_1 = CommonFixture.VALID_BUYER_1;
    public static AdTechIdentifier BUYER_2 = CommonFixture.VALID_BUYER_2;
    public static String PACKAGE_1 = CommonFixture.TEST_PACKAGE_NAME_1;
    public static String PACKAGE_2 = CommonFixture.TEST_PACKAGE_NAME_2;

    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();

    @Mock private EnrollmentDao mEnrollmentDaoMock;
    @Mock private PackageManager mPackageManagerMock;
    @Mock private Flags mFlagsMock;

    private ProtectedSignalsDao mProtectedSignalsDao;

    private MockitoSession mStaticMockSession;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    @Before
    public void setup() {
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(PackageManagerCompatUtils.class)
                        .initMocks(this)
                        .startMocking();
        mProtectedSignalsDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, ProtectedSignalsDatabase.class)
                        .build()
                        .protectedSignalsDao();
    }

    @After
    public void teardown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
        mProtectedSignalsDao.deleteByBuyers(Arrays.asList(BUYER_1, BUYER_2));
    }

    @Test
    public void testInsertThenRead() {
        List<DBProtectedSignal> readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertTrue(readResult.isEmpty());
        mProtectedSignalsDao.insertSignals(Arrays.asList(DBProtectedSignalFixture.SIGNAL));
        readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertEquals(1, readResult.size());
        assertNotNull(readResult.get(0).getId());
        assertEqualsExceptId(DBProtectedSignalFixture.SIGNAL, readResult.get(0));
    }

    @Test
    public void testInsertThenDeleteThenRead() {
        mProtectedSignalsDao.insertSignals(Arrays.asList(DBProtectedSignalFixture.SIGNAL));
        // Need to read before deleting, so that we have the correct id
        List<DBProtectedSignal> readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertEquals(1, readResult.size());
        mProtectedSignalsDao.deleteSignals(readResult);
        readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertEquals(0, readResult.size());
    }

    @Test
    public void testTwoIdenticalSignals() {
        mProtectedSignalsDao.insertSignals(
                Arrays.asList(DBProtectedSignalFixture.SIGNAL, DBProtectedSignalFixture.SIGNAL));

        List<DBProtectedSignal> readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertEquals(2, readResult.size());
        assertNotNull(readResult.get(0).getId());
        assertNotNull(readResult.get(1).getId());
        assertEqualsExceptId(DBProtectedSignalFixture.SIGNAL, readResult.get(0));
        assertEqualsExceptId(DBProtectedSignalFixture.SIGNAL, readResult.get(1));
    }

    @Test
    public void testTwoSignalsOneDelete() {
        mProtectedSignalsDao.insertSignals(
                Arrays.asList(DBProtectedSignalFixture.SIGNAL, DBProtectedSignalFixture.SIGNAL));

        List<DBProtectedSignal> readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        mProtectedSignalsDao.deleteSignals(readResult.subList(0, 1));
        readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertEquals(1, readResult.size());
        assertNotNull(readResult.get(0).getId());
        assertEqualsExceptId(DBProtectedSignalFixture.SIGNAL, readResult.get(0));
    }

    @Test
    public void testTwoSignalsDeleteAll() {
        mProtectedSignalsDao.insertSignals(
                Arrays.asList(DBProtectedSignalFixture.SIGNAL, DBProtectedSignalFixture.SIGNAL));

        List<DBProtectedSignal> readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        mProtectedSignalsDao.deleteAllSignals();
        readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertEquals(0, readResult.size());
    }

    @Test
    public void testTwoBuyers() {
        DBProtectedSignal signal1 =
                DBProtectedSignal.builder()
                        .setId(null)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setKey(DBProtectedSignalFixture.KEY)
                        .setValue(DBProtectedSignalFixture.VALUE)
                        .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setPackageName(CommonFixture.TEST_PACKAGE_NAME_1)
                        .build();
        DBProtectedSignal signal2 =
                DBProtectedSignal.builder()
                        .setId(null)
                        .setBuyer(CommonFixture.VALID_BUYER_2)
                        .setKey(DBProtectedSignalFixture.KEY)
                        .setValue(DBProtectedSignalFixture.VALUE)
                        .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setPackageName(CommonFixture.TEST_PACKAGE_NAME_1)
                        .build();

        mProtectedSignalsDao.insertSignals(Arrays.asList(signal1, signal2));

        List<DBProtectedSignal> readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertEquals(1, readResult.size());
        assertNotNull(readResult.get(0).getId());
        assertEqualsExceptId(signal1, readResult.get(0));

        readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_2);
        assertEquals(1, readResult.size());
        assertNotNull(readResult.get(0).getId());
        assertEqualsExceptId(signal2, readResult.get(0));
    }

    @Test
    public void testInsertAndDelete() {
        // Insert two signals
        mProtectedSignalsDao.insertAndDelete(
                BUYER_1,
                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                Arrays.asList(DBProtectedSignalFixture.SIGNAL, DBProtectedSignalFixture.SIGNAL),
                Collections.emptyList());
        // Get all the signals for the test buyer
        List<DBProtectedSignal> readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertEquals(
                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_1).getLastSignalsUpdatedTime());
        // Delete one of the signals and insert two more
        mProtectedSignalsDao.insertAndDelete(
                BUYER_1,
                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                Arrays.asList(DBProtectedSignalFixture.SIGNAL, DBProtectedSignalFixture.SIGNAL),
                readResult.subList(0, 1));
        // Check that the deletions and insertion occurred
        readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertEquals(
                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_1).getLastSignalsUpdatedTime());

        assertEquals(3, readResult.size());
        assertNotNull(readResult.get(0).getId());
        assertEqualsExceptId(DBProtectedSignalFixture.SIGNAL, readResult.get(0));
        assertEqualsExceptId(DBProtectedSignalFixture.SIGNAL, readResult.get(1));
        assertEqualsExceptId(DBProtectedSignalFixture.SIGNAL, readResult.get(2));
    }

    @Test
    public void testDeleteSignalsBeforeTime() {
        // Insert two signals
        mProtectedSignalsDao.insertAndDelete(
                BUYER_1,
                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                Arrays.asList(
                        DBProtectedSignalFixture.SIGNAL,
                        DBProtectedSignalFixture.LATER_TIME_SIGNAL),
                Collections.emptyList());
        assertEquals(
                DBSignalsUpdateMetadata.builder()
                        .setBuyer(BUYER_1)
                        .setLastSignalsUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build(),
                mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_1));
        // Delete the older signal
        assertEquals(
                1,
                mProtectedSignalsDao.deleteExpiredSignalsAndUpdateSignalsUpdateMetadata(
                        DBProtectedSignalFixture.SIGNAL
                                .getCreationTime()
                                .plus(Duration.ofMillis(1)),
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI));
        assertEquals(
                DBSignalsUpdateMetadata.builder()
                        .setBuyer(BUYER_1)
                        .setLastSignalsUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build(),
                mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_1));
        // Check that the deletions and insertion occurred
        List<DBProtectedSignal> readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertEquals(1, readResult.size());
        assertNotNull(readResult.get(0).getId());
        assertEqualsExceptId(DBProtectedSignalFixture.LATER_TIME_SIGNAL, readResult.get(0));
    }

    @Test
    public void testDeleteDisallowedBuyerSignalsNoSignals() {
        assertEquals(0, mProtectedSignalsDao.deleteDisallowedBuyerSignals(mEnrollmentDaoMock));
    }

    @Test
    public void testDeleteDisallowedBuyerSignalsAllAllowed() {
        DBProtectedSignal signal1 = DBProtectedSignalFixture.getBuilder().setBuyer(BUYER_1).build();
        DBProtectedSignal signal2 = DBProtectedSignalFixture.getBuilder().setBuyer(BUYER_2).build();
        // Insert two signals
        mProtectedSignalsDao.insertAndDelete(
                BUYER_1,
                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                Arrays.asList(signal1, signal2),
                Collections.emptyList());
        when(mEnrollmentDaoMock.getAllFledgeEnrolledAdTechs())
                .thenReturn(new HashSet<>(Arrays.asList(BUYER_1, BUYER_2)));
        assertEquals(0, mProtectedSignalsDao.deleteDisallowedBuyerSignals(mEnrollmentDaoMock));
        // Check that no deletion occurred
        List<DBProtectedSignal> readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertEquals(1, readResult.size());
        assertNotNull(readResult.get(0).getId());
        assertEqualsExceptId(signal1, readResult.get(0));
        assertEquals(
                DBSignalsUpdateMetadata.builder()
                        .setBuyer(BUYER_1)
                        .setLastSignalsUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build(),
                mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_1));
        readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_2);
        assertEquals(1, readResult.size());
        assertNotNull(readResult.get(0).getId());
        assertEqualsExceptId(signal2, readResult.get(0));
        assertNull(mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_2));
    }

    @Test
    public void testDeleteDisallowedBuyerSignals() {
        DBProtectedSignal signal1 =
                DBProtectedSignalFixture.getBuilder().setBuyer(CommonFixture.VALID_BUYER_1).build();
        DBProtectedSignal signal2 =
                DBProtectedSignalFixture.getBuilder().setBuyer(CommonFixture.VALID_BUYER_2).build();
        // Insert two signals
        mProtectedSignalsDao.insertAndDelete(
                BUYER_1,
                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                List.of(signal1),
                Collections.emptyList());
        mProtectedSignalsDao.insertAndDelete(
                BUYER_2,
                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                List.of(signal2),
                Collections.emptyList());
        assertEquals(
                DBSignalsUpdateMetadata.builder()
                        .setBuyer(BUYER_1)
                        .setLastSignalsUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build(),
                mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_1));
        assertEquals(
                DBSignalsUpdateMetadata.builder()
                        .setBuyer(BUYER_2)
                        .setLastSignalsUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build(),
                mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_2));
        when(mEnrollmentDaoMock.getAllFledgeEnrolledAdTechs())
                .thenReturn(Collections.singleton(CommonFixture.VALID_BUYER_1));
        assertEquals(1, mProtectedSignalsDao.deleteDisallowedBuyerSignals(mEnrollmentDaoMock));
        assertEquals(
                DBSignalsUpdateMetadata.builder()
                        .setBuyer(BUYER_1)
                        .setLastSignalsUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build(),
                mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_1));
        assertNull(mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_2));
        // Check that the correct deletion occurred
        List<DBProtectedSignal> readResult = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertEquals(1, readResult.size());
        assertNotNull(readResult.get(0).getId());
        assertEqualsExceptId(signal1, readResult.get(0));
    }

    @Test
    public void testDeleteDisallowedPackageSignalsNoSignals() {
        assertEquals(
                0,
                mProtectedSignalsDao.deleteAllDisallowedPackageSignalsAndUpdateSignalUpdateMetadata(
                        mPackageManagerMock,
                        mFlagsMock,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI));
    }

    @Test
    public void testDeleteAllDisallowedPackageSignalsAllAllowed() {
        DBProtectedSignal signal1 =
                DBProtectedSignalFixture.getBuilder().setPackageName(PACKAGE_1).build();
        DBProtectedSignal signal2 =
                DBProtectedSignalFixture.getBuilder().setPackageName(PACKAGE_2).build();
        final class FlagsWithAllAppsAllowed implements Flags {
            @Override
            public String getPasAppAllowList() {
                return AllowLists.ALLOW_ALL;
            }
        }
        ApplicationInfo installedPackage1 = new ApplicationInfo();
        installedPackage1.packageName = PACKAGE_1;
        ApplicationInfo installedPackage2 = new ApplicationInfo();
        installedPackage2.packageName = PACKAGE_2;
        doReturn(Arrays.asList(installedPackage1, installedPackage2))
                .when(() -> PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()));
        // Insert two signals
        mProtectedSignalsDao.insertSignals(Arrays.asList(signal1, signal2));
        List<DBProtectedSignal> readResult1 = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertEquals(2, readResult1.size());
        assertEquals(
                0,
                mProtectedSignalsDao.deleteAllDisallowedPackageSignalsAndUpdateSignalUpdateMetadata(
                        mPackageManagerMock,
                        new FlagsWithAllAppsAllowed(),
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI));
        // Check that no deletion occurred
        List<DBProtectedSignal> readResult2 = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertEquals(2, readResult2.size());
    }

    @Test
    public void testDeleteAllDisallowedPackageSignals() {
        DBProtectedSignal signal1 =
                DBProtectedSignalFixture.getBuilder().setPackageName(PACKAGE_1).build();
        DBProtectedSignal signal2 =
                DBProtectedSignalFixture.getBuilder().setPackageName(PACKAGE_2).build();
        class FlagsThatAllowOneApp implements Flags {
            @Override
            public String getPasAppAllowList() {
                return PACKAGE_1;
            }
        }
        ApplicationInfo installedPackage1 = new ApplicationInfo();
        installedPackage1.packageName = PACKAGE_1;
        ApplicationInfo installedPackage2 = new ApplicationInfo();
        installedPackage2.packageName = PACKAGE_2;
        doReturn(Arrays.asList(installedPackage1, installedPackage2))
                .when(() -> PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()));
        mProtectedSignalsDao.insertSignals(Arrays.asList(signal1, signal2));
        List<DBProtectedSignal> readResult1 = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertEquals(2, readResult1.size());
        assertEquals(
                1,
                mProtectedSignalsDao.deleteAllDisallowedPackageSignalsAndUpdateSignalUpdateMetadata(
                        CONTEXT.getPackageManager(),
                        new FlagsThatAllowOneApp(),
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI));
        List<DBProtectedSignal> readResult2 = mProtectedSignalsDao.getSignalsByBuyer(BUYER_1);
        assertEquals(1, readResult2.size());
        assertEquals(PACKAGE_1, readResult2.get(0).getPackageName());
    }

    @Test
    public void testPersistMetadata() {
        assertNull(
                "Initial state of the table should be empty",
                mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_1));

        DBSignalsUpdateMetadata signalsUpdateMetadata =
                DBSignalsUpdateMetadata.builder()
                        .setBuyer(BUYER_1)
                        .setLastSignalsUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build();
        assertEquals(
                "One entry should have been inserted",
                1,
                mProtectedSignalsDao.persistSignalsUpdateMetadata(signalsUpdateMetadata));
    }

    @Test
    public void testDeleteMetadata() {
        assertNull(
                "Initial state of the table should be empty",
                mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_1));

        DBSignalsUpdateMetadata signalsUpdateMetadata =
                DBSignalsUpdateMetadata.builder()
                        .setBuyer(BUYER_1)
                        .setLastSignalsUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build();
        assertEquals(
                "One entry should have been inserted",
                1,
                mProtectedSignalsDao.persistSignalsUpdateMetadata(signalsUpdateMetadata));

        mProtectedSignalsDao.deleteSignalsUpdateMetadata(BUYER_1);
        assertNull(
                "Metadata should have been deleted",
                mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_1));
    }

    @Test
    public void testQuerySignalsUpdateMetadata() {
        assertNull(
                "Initial state of the table should be empty",
                mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_1));

        DBSignalsUpdateMetadata signalsUpdateMetadata =
                DBSignalsUpdateMetadata.builder()
                        .setBuyer(BUYER_1)
                        .setLastSignalsUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build();
        assertEquals(
                "One entry should have been inserted",
                1,
                mProtectedSignalsDao.persistSignalsUpdateMetadata(signalsUpdateMetadata));
        DBSignalsUpdateMetadata retrieved = mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_1);

        assertEquals(signalsUpdateMetadata.getBuyer(), retrieved.getBuyer());
        assertEquals(
                signalsUpdateMetadata.getLastSignalsUpdatedTime(),
                retrieved.getLastSignalsUpdatedTime());
    }

    @Test
    public void testPersistMetadataReplacesExisting() {
        assertNull(
                "Initial state of the table should be empty",
                mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_1));

        DBSignalsUpdateMetadata.Builder anBuilder =
                DBSignalsUpdateMetadata.builder().setBuyer(BUYER_1);
        DBSignalsUpdateMetadata previous =
                anBuilder
                        .setLastSignalsUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build();
        mProtectedSignalsDao.persistSignalsUpdateMetadata(previous);

        DBSignalsUpdateMetadata retrieved = mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_1);
        assertEquals(previous.getLastSignalsUpdatedTime(), retrieved.getLastSignalsUpdatedTime());

        DBSignalsUpdateMetadata updated =
                anBuilder
                        .setLastSignalsUpdatedTime(
                                CommonFixture.FIXED_NEXT_ONE_DAY.truncatedTo(ChronoUnit.MILLIS))
                        .build();

        mProtectedSignalsDao.persistSignalsUpdateMetadata(updated);
        retrieved = mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER_1);
        assertNotEquals(
                previous.getLastSignalsUpdatedTime(), retrieved.getLastSignalsUpdatedTime());
        assertEquals(updated.getLastSignalsUpdatedTime(), retrieved.getLastSignalsUpdatedTime());
    }

    private void assertEqualsExceptId(DBProtectedSignal expected, DBProtectedSignal actual) {
        assertEquals(expected.getBuyer(), actual.getBuyer());
        assertArrayEquals(expected.getKey(), actual.getKey());
        assertArrayEquals(expected.getValue(), actual.getValue());
        assertEquals(expected.getCreationTime(), actual.getCreationTime());
        assertEquals(expected.getPackageName(), actual.getPackageName());
    }
}
