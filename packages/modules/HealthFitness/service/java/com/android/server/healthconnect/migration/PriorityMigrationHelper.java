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

import static com.android.server.healthconnect.storage.request.UpsertTableRequest.TYPE_STRING;
import static com.android.server.healthconnect.storage.utils.StorageUtils.DELIMITER;
import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER_UNIQUE;
import static com.android.server.healthconnect.storage.utils.StorageUtils.TEXT_NOT_NULL;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.Cursor;
import android.health.connect.HealthDataCategory;
import android.util.Pair;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.DatabaseHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;
import com.android.server.healthconnect.storage.request.CreateTableRequest;
import com.android.server.healthconnect.storage.request.DeleteTableRequest;
import com.android.server.healthconnect.storage.request.ReadTableRequest;
import com.android.server.healthconnect.storage.request.UpsertTableRequest;
import com.android.server.healthconnect.storage.utils.StorageUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class to get migrate priority of the apps for each {@link HealthDataCategory} from
 * migration aware apk to module.
 *
 * @hide
 */
public final class PriorityMigrationHelper extends DatabaseHelper {

    @VisibleForTesting
    public static final String PRE_MIGRATION_TABLE_NAME = "pre_migration_category_priority_table";

    @VisibleForTesting static final String CATEGORY_COLUMN_NAME = "category";
    @VisibleForTesting static final String PRIORITY_ORDER_COLUMN_NAME = "priority_order";
    private static final List<Pair<String, Integer>> UNIQUE_COLUMN_INFO =
            Collections.singletonList(new Pair<>(CATEGORY_COLUMN_NAME, TYPE_STRING));

    private static final Object sPriorityMigrationHelperLock = new Object();

    @SuppressWarnings("NullAway.Init") // TODO(b/317029272): fix this suppression
    private static volatile PriorityMigrationHelper sPriorityMigrationHelper;

    private final Object mPriorityMigrationHelperInstanceLock = new Object();
    private Map<Integer, List<Long>> mPreMigrationPriorityCache;

    @SuppressWarnings("NullAway.Init") // TODO(b/317029272): fix this suppression
    private PriorityMigrationHelper() {}

    /**
     * Populate the pre-migration priority table by copying entries from priority table at the start
     * of migration.
     */
    public void populatePreMigrationPriority() {
        synchronized (mPriorityMigrationHelperInstanceLock) {
            // Populating table only if it was not already populated.
            if (TransactionManager.getInitialisedInstance()
                            .getNumberOfEntriesInTheTable(PRE_MIGRATION_TABLE_NAME)
                    == 0) {
                populatePreMigrationTable();
            }
        }
    }

    /**
     * Returns priority order stored for data category in module at the time migration was started.
     */
    public List<Long> getPreMigrationPriority(int dataCategory) {
        synchronized (mPriorityMigrationHelperInstanceLock) {
            if (mPreMigrationPriorityCache == null) {
                cachePreMigrationTable();
            }

            return Collections.unmodifiableList(
                    mPreMigrationPriorityCache.getOrDefault(dataCategory, new ArrayList<>()));
        }
    }

    /**
     * Read pre-migration table and populate cache which would be used for writing priority
     * migration.
     */
    private void cachePreMigrationTable() {
        Map<Integer, List<Long>> preMigrationCategoryPriorityMap = new HashMap<>();
        TransactionManager transactionManager = TransactionManager.getInitialisedInstance();
        try (Cursor cursor =
                transactionManager.read(new ReadTableRequest(PRE_MIGRATION_TABLE_NAME))) {
            while (cursor.moveToNext()) {
                int dataCategory = cursor.getInt(cursor.getColumnIndex(CATEGORY_COLUMN_NAME));
                List<Long> appIdsInOrder =
                        StorageUtils.getCursorLongList(
                                cursor, PRIORITY_ORDER_COLUMN_NAME, DELIMITER);
                preMigrationCategoryPriorityMap.put(dataCategory, appIdsInOrder);
            }
        }
        mPreMigrationPriorityCache = preMigrationCategoryPriorityMap;
    }

    /** Delete pre-migration priority data when migration is finished. */
    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    public void clearData(@NonNull TransactionManager transactionManager) {
        synchronized (mPriorityMigrationHelperInstanceLock) {
            transactionManager.delete(new DeleteTableRequest(PRE_MIGRATION_TABLE_NAME));
            mPreMigrationPriorityCache = null;
        }
    }

    /** Returns a requests for creating pre-migration priority table. */
    @NonNull
    public CreateTableRequest getCreateTableRequest() {
        return new CreateTableRequest(PRE_MIGRATION_TABLE_NAME, getColumnInfo());
    }

    @Override
    protected String getMainTableName() {
        return PRE_MIGRATION_TABLE_NAME;
    }

    /**
     * Populate the pre-migration priority table if table is newly created by copying entries from
     * priority table.
     */
    private void populatePreMigrationTable() {
        Map<Integer, List<Long>> existingPriority =
                HealthDataCategoryPriorityHelper.getInstance()
                        .getHealthDataCategoryToAppIdPriorityMapImmutable();

        TransactionManager transactionManager = TransactionManager.getInitialisedInstance();
        existingPriority.forEach(
                (category, priority) -> {
                    if (!priority.isEmpty()) {
                        UpsertTableRequest request =
                                new UpsertTableRequest(
                                        PRE_MIGRATION_TABLE_NAME,
                                        getContentValuesFor(category, priority),
                                        UNIQUE_COLUMN_INFO);
                        transactionManager.insert(request);
                    }
                });
        if (existingPriority.values().stream()
                .filter(priority -> !priority.isEmpty())
                .findAny()
                .isEmpty()) {
            /*
            Adding placeholder row to signify that pre-migration have no priority for
            any category and the table should not be repopulated even after multiple calls to
            startMigration
            */
            UpsertTableRequest request =
                    new UpsertTableRequest(
                            PRE_MIGRATION_TABLE_NAME,
                            getContentValuesFor(HealthDataCategory.UNKNOWN, new ArrayList<>()),
                            UNIQUE_COLUMN_INFO);
            transactionManager.insert(request);
        }
    }

    /**
     * This implementation should return the column names with which the table should be created.
     */
    @Override
    @NonNull
    protected List<Pair<String, String>> getColumnInfo() {
        ArrayList<Pair<String, String>> columnInfo = new ArrayList<>();
        columnInfo.add(new Pair<>(CATEGORY_COLUMN_NAME, INTEGER_UNIQUE));
        columnInfo.add(new Pair<>(PRIORITY_ORDER_COLUMN_NAME, TEXT_NOT_NULL));

        return columnInfo;
    }

    /** Create content values for storing priority in the database. */
    private ContentValues getContentValuesFor(
            @HealthDataCategory.Type int dataCategory, List<Long> priorityList) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(CATEGORY_COLUMN_NAME, dataCategory);
        contentValues.put(PRIORITY_ORDER_COLUMN_NAME, StorageUtils.flattenLongList(priorityList));

        return contentValues;
    }

    /** Creates(if it was not already created) and returns instance of PriorityMigrationHelper. */
    @NonNull
    public static PriorityMigrationHelper getInstance() {
        if (sPriorityMigrationHelper == null) {
            synchronized (sPriorityMigrationHelperLock) {
                if (sPriorityMigrationHelper == null) {
                    sPriorityMigrationHelper = new PriorityMigrationHelper();
                }
            }
        }

        return sPriorityMigrationHelper;
    }
}
