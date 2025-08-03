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

import static com.android.server.healthconnect.migration.PriorityMigrationHelper.CATEGORY_COLUMN_NAME;
import static com.android.server.healthconnect.migration.PriorityMigrationHelper.PRE_MIGRATION_TABLE_NAME;
import static com.android.server.healthconnect.migration.PriorityMigrationHelper.PRIORITY_ORDER_COLUMN_NAME;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.database.Cursor;
import android.health.connect.HealthDataCategory;

import androidx.test.runner.AndroidJUnit4;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;
import com.android.server.healthconnect.storage.utils.StorageUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.quality.Strictness;
import org.mockito.verification.VerificationMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class PriorityMigrationHelperTest {

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .mockStatic(TransactionManager.class)
                    .mockStatic(HealthDataCategoryPriorityHelper.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    private static final long APP_PACKAGE_ID = 1;
    private static final long APP_PACKAGE_ID_2 = 2;
    private static final long APP_PACKAGE_ID_3 = 3;
    private static final long APP_PACKAGE_ID_4 = 4;
    private static final int PRIORITY_ORDER_COLUMN_INDEX = 2;
    private static final int CATEGORY_COLUMN_INDEX = 1;

    @Mock private Cursor mCursor;
    @Mock private TransactionManager mTransactionManager;
    @Mock private HealthDataCategoryPriorityHelper mHealthDataCategoryPriorityHelper;
    private PriorityMigrationHelper mPriorityMigrationHelper;

    @Before
    public void setUp() throws Exception {
        when(HealthDataCategoryPriorityHelper.getInstance())
                .thenReturn(mHealthDataCategoryPriorityHelper);
        when(TransactionManager.getInitialisedInstance()).thenReturn(mTransactionManager);

        mPriorityMigrationHelper = PriorityMigrationHelper.getInstance();
    }

    @After
    public void tearDown() throws Exception {
        mPriorityMigrationHelper.clearData(mTransactionManager);
    }

    @Test
    public void testPopulatePreMigrationPriority_preMigrationPriorityExists_prioritySaved() {
        Map<Integer, List<Long>> preMigrationPriority = new HashMap<>();
        preMigrationPriority.put(
                HealthDataCategory.BODY_MEASUREMENTS, List.of(APP_PACKAGE_ID, APP_PACKAGE_ID_2));
        preMigrationPriority.put(
                HealthDataCategory.ACTIVITY, List.of(APP_PACKAGE_ID_3, APP_PACKAGE_ID_4));
        preMigrationPriority.put(HealthDataCategory.SLEEP, new ArrayList<>());

        when(mHealthDataCategoryPriorityHelper.getHealthDataCategoryToAppIdPriorityMapImmutable())
                .thenReturn(preMigrationPriority);
        when(mTransactionManager.getNumberOfEntriesInTheTable(eq(PRE_MIGRATION_TABLE_NAME)))
                .thenReturn(0L);

        mPriorityMigrationHelper.populatePreMigrationPriority();

        verifyPreMigrationPriorityWrite(
                times(1),
                HealthDataCategory.BODY_MEASUREMENTS,
                List.of(APP_PACKAGE_ID, APP_PACKAGE_ID_2));
        verifyPreMigrationPriorityWrite(
                times(1), HealthDataCategory.ACTIVITY, List.of(APP_PACKAGE_ID_3, APP_PACKAGE_ID_4));
        verifyPreMigrationPriorityWrite(never(), HealthDataCategory.SLEEP, new ArrayList<>());
    }

    @Test
    public void
            testPopulatePreMigrationPriority_preMigrationTableIsPopulated_noOperationPerformed() {
        Map<Integer, List<Long>> preMigrationPriority = new HashMap<>();
        preMigrationPriority.put(
                HealthDataCategory.BODY_MEASUREMENTS, List.of(APP_PACKAGE_ID, APP_PACKAGE_ID_2));
        preMigrationPriority.put(
                HealthDataCategory.ACTIVITY, List.of(APP_PACKAGE_ID_3, APP_PACKAGE_ID_4));
        preMigrationPriority.put(HealthDataCategory.SLEEP, new ArrayList<>());

        when(mHealthDataCategoryPriorityHelper.getHealthDataCategoryToAppIdPriorityMapImmutable())
                .thenReturn(preMigrationPriority);
        when(mTransactionManager.getNumberOfEntriesInTheTable(eq(PRE_MIGRATION_TABLE_NAME)))
                .thenReturn(1L);
        mPriorityMigrationHelper.populatePreMigrationPriority();

        verify(mTransactionManager, never()).insert(any());
    }

    @Test
    public void testPopulatePreMigrationPriority_NoPreMigrationPriorityExists_placeholderSaved() {
        Map<Integer, List<Long>> preMigrationPriority = new HashMap<>();
        preMigrationPriority.put(HealthDataCategory.SLEEP, new ArrayList<>());

        when(mHealthDataCategoryPriorityHelper.getHealthDataCategoryToAppIdPriorityMapImmutable())
                .thenReturn(preMigrationPriority);
        when(mTransactionManager.getNumberOfEntriesInTheTable(eq(PRE_MIGRATION_TABLE_NAME)))
                .thenReturn(0L);

        mPriorityMigrationHelper.populatePreMigrationPriority();

        verifyPreMigrationPriorityWrite(never(), HealthDataCategory.SLEEP, new ArrayList<>());
        verifyPreMigrationPriorityWrite(times(1), HealthDataCategory.UNKNOWN, new ArrayList<>());
    }

    @Test
    public void testGetPreMigrationPriority_priorityReadFromDatabased() {
        when(mTransactionManager.read(any())).thenReturn(mCursor);
        when(mCursor.moveToNext()).thenReturn(true, true, false);
        when(mCursor.getColumnIndex(CATEGORY_COLUMN_NAME)).thenReturn(CATEGORY_COLUMN_INDEX);
        when(mCursor.getColumnIndex(PRIORITY_ORDER_COLUMN_NAME))
                .thenReturn(PRIORITY_ORDER_COLUMN_INDEX);
        when(mCursor.getInt(CATEGORY_COLUMN_INDEX))
                .thenReturn(HealthDataCategory.BODY_MEASUREMENTS, HealthDataCategory.ACTIVITY);
        when(mCursor.getString(PRIORITY_ORDER_COLUMN_INDEX))
                .thenReturn(
                        StorageUtils.flattenLongList(List.of(APP_PACKAGE_ID, APP_PACKAGE_ID_2)),
                        StorageUtils.flattenLongList(List.of(APP_PACKAGE_ID_3, APP_PACKAGE_ID_4)));

        List<Long> bodyMeasurementPriority =
                mPriorityMigrationHelper.getPreMigrationPriority(
                        HealthDataCategory.BODY_MEASUREMENTS);
        assertThat(bodyMeasurementPriority).isEqualTo(List.of(APP_PACKAGE_ID, APP_PACKAGE_ID_2));
        List<Long> activityPriority =
                mPriorityMigrationHelper.getPreMigrationPriority(HealthDataCategory.ACTIVITY);
        assertThat(activityPriority).isEqualTo(List.of(APP_PACKAGE_ID_3, APP_PACKAGE_ID_4));
    }

    @Test
    public void testClearPreMigrationPriority_dataDeleted() {
        mPriorityMigrationHelper.clearData(mTransactionManager);
        verify(mTransactionManager, times(1))
                .delete(
                        argThat(
                                deleteRequest ->
                                        PRE_MIGRATION_TABLE_NAME.equals(
                                                deleteRequest.getTableName())));
    }

    private void verifyPreMigrationPriorityWrite(
            VerificationMode verificationMode, int category, List<Long> priorityOrder) {
        verify(mTransactionManager, verificationMode)
                .insert(
                        argThat(
                                request ->
                                        PRE_MIGRATION_TABLE_NAME.equals(request.getTable())
                                                && request.getContentValues()
                                                        .getAsInteger(CATEGORY_COLUMN_NAME)
                                                        .equals(category)
                                                && request.getContentValues()
                                                        .getAsString(
                                                                PriorityMigrationHelper
                                                                        .PRIORITY_ORDER_COLUMN_NAME)
                                                        .equalsIgnoreCase(
                                                                StorageUtils.flattenLongList(
                                                                        priorityOrder))));
    }
}
