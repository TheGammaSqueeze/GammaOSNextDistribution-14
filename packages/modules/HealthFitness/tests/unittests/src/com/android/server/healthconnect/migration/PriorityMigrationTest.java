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

package com.android.server.healthconnect.migration;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.health.connect.HealthDataCategory;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.migration.MigrationEntity;
import android.health.connect.migration.PriorityMigrationPayload;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.healthconnect.permission.FirstGrantTimeManager;
import com.android.server.healthconnect.permission.HealthConnectPermissionHelper;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.ActivityDateHelper;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.DeviceInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;
import com.android.server.healthconnect.storage.datatypehelpers.MigrationEntityHelper;
import com.android.server.healthconnect.storage.utils.RecordHelperProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class PriorityMigrationTest {

    private static final String APP_PACKAGE_NAME = "android.healthconnect.mocked.app";
    private static final String APP_PACKAGE_NAME_2 = "android.healthconnect.mocked.app2";
    private static final String APP_PACKAGE_NAME_3 = "android.healthconnect.mocked.app3";
    private static final String APP_PACKAGE_NAME_4 = "android.healthconnect.mocked.app4";

    private static final long APP_PACKAGE_ID = 1;
    private static final long APP_PACKAGE_ID_2 = 2;
    private static final long APP_PACKAGE_ID_3 = 3;
    private static final long APP_PACKAGE_ID_4 = 4;

    private static final String PRIORITY_MIGRATION_ENTITY_ID = "priorityMigrationEntityId";

    @Mock Context mUserContext;
    @Mock TransactionManager mTransactionManager;
    @Mock HealthConnectPermissionHelper mHealthConnectPermissionHelper;
    @Mock FirstGrantTimeManager mFirstGrantTimeManager;
    @Mock DeviceInfoHelper mDeviceInfoHelper;
    @Mock AppInfoHelper mAppInfoHelper;
    @Mock MigrationEntityHelper mMigrationEntityHelper;
    @Mock RecordHelperProvider mRecordHelperProvider;
    @Mock HealthDataCategoryPriorityHelper mHealthDataCategoryPriorityHelper;
    @Mock PriorityMigrationHelper mPriorityMigrationHelper;
    @Mock ActivityDateHelper mActivityDateHelper;
    @Mock SQLiteDatabase mSQLiteDatabase;

    DataMigrationManager mDataMigrationManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        doAnswer(
                        new Answer<Void>() {
                            @Override
                            public Void answer(InvocationOnMock invocation) throws Throwable {
                                TransactionManager.TransactionRunnable runnable =
                                        invocation.getArgument(0);
                                runnable.run(mSQLiteDatabase);
                                return null;
                            }
                        })
                .when(mTransactionManager)
                .runAsTransaction(any());

        mDataMigrationManager =
                new DataMigrationManager(
                        mUserContext,
                        mTransactionManager,
                        mHealthConnectPermissionHelper,
                        mFirstGrantTimeManager,
                        mDeviceInfoHelper,
                        mAppInfoHelper,
                        mMigrationEntityHelper,
                        mRecordHelperProvider,
                        mHealthDataCategoryPriorityHelper,
                        mPriorityMigrationHelper,
                        mActivityDateHelper);
    }

    @Test
    public void testMigratePriority_noPreMigrationPriorityAdditionalAppsInPayload_priorityMigrated()
            throws DataMigrationManager.EntityWriteException {
        when(mPriorityMigrationHelper.getPreMigrationPriority(HealthDataCategory.BODY_MEASUREMENTS))
                .thenReturn(new ArrayList<>());

        PriorityMigrationPayload migrationPayload =
                new PriorityMigrationPayload.Builder()
                        .setDataCategory(HealthDataCategory.BODY_MEASUREMENTS)
                        .addDataOrigin(
                                new DataOrigin.Builder().setPackageName(APP_PACKAGE_NAME).build())
                        .addDataOrigin(
                                new DataOrigin.Builder().setPackageName(APP_PACKAGE_NAME_2).build())
                        .build();

        mDataMigrationManager.apply(
                List.of(new MigrationEntity(PRIORITY_MIGRATION_ENTITY_ID, migrationPayload)));

        verify(mHealthDataCategoryPriorityHelper, times(1))
                .setPriorityOrder(
                        eq(HealthDataCategory.BODY_MEASUREMENTS),
                        eq(List.of(APP_PACKAGE_NAME, APP_PACKAGE_NAME_2)));
    }

    @Test
    public void testMigratePriority_preMigrationPriorityNewAppsInPayload_priorityMigrated()
            throws DataMigrationManager.EntityWriteException {
        when(mPriorityMigrationHelper.getPreMigrationPriority(HealthDataCategory.BODY_MEASUREMENTS))
                .thenReturn(List.of(APP_PACKAGE_ID_4, APP_PACKAGE_ID_3));
        when(mAppInfoHelper.getPackageNames(List.of(APP_PACKAGE_ID_4, APP_PACKAGE_ID_3)))
                .thenReturn(List.of(APP_PACKAGE_NAME_4, APP_PACKAGE_NAME_3));

        PriorityMigrationPayload migrationPayload =
                new PriorityMigrationPayload.Builder()
                        .setDataCategory(HealthDataCategory.BODY_MEASUREMENTS)
                        .addDataOrigin(
                                new DataOrigin.Builder().setPackageName(APP_PACKAGE_NAME).build())
                        .addDataOrigin(
                                new DataOrigin.Builder().setPackageName(APP_PACKAGE_NAME_2).build())
                        .build();

        mDataMigrationManager.apply(
                List.of(new MigrationEntity(PRIORITY_MIGRATION_ENTITY_ID, migrationPayload)));

        verify(mHealthDataCategoryPriorityHelper, times(1))
                .setPriorityOrder(
                        eq(HealthDataCategory.BODY_MEASUREMENTS),
                        eq(
                                List.of(
                                        APP_PACKAGE_NAME_4,
                                        APP_PACKAGE_NAME_3,
                                        APP_PACKAGE_NAME,
                                        APP_PACKAGE_NAME_2)));
    }

    @Test
    public void testMigratePriority_preMigrationPriorityWithChangedOrderInPayload_priorityMigrated()
            throws DataMigrationManager.EntityWriteException {
        when(mPriorityMigrationHelper.getPreMigrationPriority(HealthDataCategory.BODY_MEASUREMENTS))
                .thenReturn(
                        List.of(
                                APP_PACKAGE_ID,
                                APP_PACKAGE_ID_2,
                                APP_PACKAGE_ID_3,
                                APP_PACKAGE_ID_4));
        when(mAppInfoHelper.getPackageNames(
                        List.of(
                                APP_PACKAGE_ID,
                                APP_PACKAGE_ID_2,
                                APP_PACKAGE_ID_3,
                                APP_PACKAGE_ID_4)))
                .thenReturn(
                        List.of(
                                APP_PACKAGE_NAME,
                                APP_PACKAGE_NAME_2,
                                APP_PACKAGE_NAME_3,
                                APP_PACKAGE_NAME_4));

        PriorityMigrationPayload migrationPayload =
                new PriorityMigrationPayload.Builder()
                        .setDataCategory(HealthDataCategory.BODY_MEASUREMENTS)
                        .addDataOrigin(
                                new DataOrigin.Builder().setPackageName(APP_PACKAGE_NAME_4).build())
                        .addDataOrigin(
                                new DataOrigin.Builder().setPackageName(APP_PACKAGE_NAME_3).build())
                        .addDataOrigin(
                                new DataOrigin.Builder().setPackageName(APP_PACKAGE_NAME_2).build())
                        .addDataOrigin(
                                new DataOrigin.Builder().setPackageName(APP_PACKAGE_NAME).build())
                        .build();
        mDataMigrationManager.apply(
                List.of(new MigrationEntity(PRIORITY_MIGRATION_ENTITY_ID, migrationPayload)));

        verify(mHealthDataCategoryPriorityHelper, times(1))
                .setPriorityOrder(
                        eq(HealthDataCategory.BODY_MEASUREMENTS),
                        eq(
                                List.of(
                                        APP_PACKAGE_NAME, APP_PACKAGE_NAME_2,
                                        APP_PACKAGE_NAME_3, APP_PACKAGE_NAME_4)));
    }

    @Test
    public void
            testMigratePriority_preMigrationPriorityNewAppChangedOrderInPayload_priorityMigrated()
                    throws DataMigrationManager.EntityWriteException {
        when(mPriorityMigrationHelper.getPreMigrationPriority(HealthDataCategory.BODY_MEASUREMENTS))
                .thenReturn(List.of(APP_PACKAGE_ID, APP_PACKAGE_ID_2, APP_PACKAGE_ID_3));
        when(mAppInfoHelper.getPackageNames(
                        List.of(APP_PACKAGE_ID, APP_PACKAGE_ID_2, APP_PACKAGE_ID_3)))
                .thenReturn(List.of(APP_PACKAGE_NAME, APP_PACKAGE_NAME_2, APP_PACKAGE_NAME_3));

        PriorityMigrationPayload migrationPayload =
                new PriorityMigrationPayload.Builder()
                        .setDataCategory(HealthDataCategory.BODY_MEASUREMENTS)
                        .addDataOrigin(
                                new DataOrigin.Builder().setPackageName(APP_PACKAGE_NAME_4).build())
                        .addDataOrigin(
                                new DataOrigin.Builder().setPackageName(APP_PACKAGE_NAME_2).build())
                        .build();
        mDataMigrationManager.apply(
                List.of(new MigrationEntity(PRIORITY_MIGRATION_ENTITY_ID, migrationPayload)));

        verify(mHealthDataCategoryPriorityHelper, times(1))
                .setPriorityOrder(
                        eq(HealthDataCategory.BODY_MEASUREMENTS),
                        eq(
                                List.of(
                                        APP_PACKAGE_NAME, APP_PACKAGE_NAME_2,
                                        APP_PACKAGE_NAME_3, APP_PACKAGE_NAME_4)));
    }

    @Test
    public void testMigratePriority_preMigrationPriorityWithNoAppsInPayload_priorityNotMigrated()
            throws DataMigrationManager.EntityWriteException {
        when(mPriorityMigrationHelper.getPreMigrationPriority(HealthDataCategory.BODY_MEASUREMENTS))
                .thenReturn(
                        List.of(
                                APP_PACKAGE_ID,
                                APP_PACKAGE_ID_2,
                                APP_PACKAGE_ID_3,
                                APP_PACKAGE_ID_4));
        when(mAppInfoHelper.getPackageNames(
                        List.of(
                                APP_PACKAGE_ID,
                                APP_PACKAGE_ID_2,
                                APP_PACKAGE_ID_3,
                                APP_PACKAGE_ID_4)))
                .thenReturn(
                        List.of(
                                APP_PACKAGE_NAME,
                                APP_PACKAGE_NAME_2,
                                APP_PACKAGE_NAME_3,
                                APP_PACKAGE_NAME_4));

        PriorityMigrationPayload migrationPayload =
                new PriorityMigrationPayload.Builder()
                        .setDataCategory(HealthDataCategory.BODY_MEASUREMENTS)
                        .build();
        mDataMigrationManager.apply(
                List.of(new MigrationEntity(PRIORITY_MIGRATION_ENTITY_ID, migrationPayload)));

        verify(mHealthDataCategoryPriorityHelper, never())
                .setPriorityOrder(eq(HealthDataCategory.BODY_MEASUREMENTS), any());
    }
}
