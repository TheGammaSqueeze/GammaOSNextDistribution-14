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

package com.android.server.healthconnect.storage.datatypehelpers;

import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.PRIMARY_COLUMN_NAME;
import static com.android.server.healthconnect.storage.request.UpsertTableRequest.TYPE_STRING;
import static com.android.server.healthconnect.storage.utils.StorageUtils.PRIMARY;
import static com.android.server.healthconnect.storage.utils.StorageUtils.TEXT_NOT_NULL_UNIQUE;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.util.Pair;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.request.CreateTableRequest;
import com.android.server.healthconnect.storage.request.DeleteTableRequest;
import com.android.server.healthconnect.storage.request.UpsertTableRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A class to help with the DB transaction for storing migration entity identifiers, user for
 * deduplication logic during the migration process.
 *
 * @hide
 */
public final class MigrationEntityHelper extends DatabaseHelper {

    @VisibleForTesting public static final String TABLE_NAME = "migration_entity_table";
    private static final String COLUMN_ENTITY_ID = "entity_id";
    public static final List<Pair<String, Integer>> UNIQUE_COLUMN_INFO =
            Collections.singletonList(new Pair<>(COLUMN_ENTITY_ID, TYPE_STRING));
    private static final Object sGetInstanceLock = new Object();
    private static final int DB_VERSION_TABLE_CREATED = 3;

    @SuppressWarnings("NullAway.Init") // TODO(b/317029272): fix this suppression
    private static volatile MigrationEntityHelper sInstance;

    private MigrationEntityHelper() {}

    /** Clears all data related to this helper. */
    public void clearData(@NonNull TransactionManager transactionManager) {
        transactionManager.delete(new DeleteTableRequest(TABLE_NAME));
    }

    /** Returns a request to create a table for this helper. */
    @NonNull
    public CreateTableRequest getCreateTableRequest() {
        return new CreateTableRequest(
                TABLE_NAME,
                List.of(
                        new Pair<>(PRIMARY_COLUMN_NAME, PRIMARY),
                        new Pair<>(COLUMN_ENTITY_ID, TEXT_NOT_NULL_UNIQUE)));
    }

    @Override
    protected String getMainTableName() {
        return TABLE_NAME;
    }

    @Override
    protected List<Pair<String, String>> getColumnInfo() {
        ArrayList<Pair<String, String>> columnInfo = new ArrayList<>();
        columnInfo.add(new Pair<>(PRIMARY_COLUMN_NAME, PRIMARY));
        columnInfo.add(new Pair<>(COLUMN_ENTITY_ID, TEXT_NOT_NULL_UNIQUE));

        return columnInfo;
    }

    /** Returns a request to insert the provided {@code entityId}. */
    @NonNull
    public UpsertTableRequest getInsertRequest(@NonNull String entityId) {
        final ContentValues values = new ContentValues();
        values.put(COLUMN_ENTITY_ID, entityId);
        return new UpsertTableRequest(TABLE_NAME, values, UNIQUE_COLUMN_INFO);
    }

    /** Returns a shared instance of {@link MigrationEntityHelper}. */
    @NonNull
    public static MigrationEntityHelper getInstance() {
        if (sInstance == null) {
            synchronized (sGetInstanceLock) {
                if (sInstance == null) {
                    sInstance = new MigrationEntityHelper();
                }
            }
        }

        return sInstance;
    }
}
