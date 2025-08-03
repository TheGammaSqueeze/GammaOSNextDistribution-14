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

import android.annotation.NonNull;
import android.health.connect.HealthConnectDataState;
import android.util.Slog;

import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.MigrationEntityHelper;

/**
 * Deletes temporary data after migration is complete.
 *
 * @hide
 */
public final class MigrationCleaner {

    private static final String LOG_TAG = "MigrationCleaner";

    private final TransactionManager mTransactionManager;
    private final MigrationEntityHelper mMigrationEntityHelper;
    private final PriorityMigrationHelper mPriorityMigrationHelper;

    public MigrationCleaner(
            @NonNull TransactionManager transactionManager,
            @NonNull MigrationEntityHelper migrationEntityHelper,
            @NonNull PriorityMigrationHelper priorityMigrationHelper) {
        mTransactionManager = transactionManager;
        mMigrationEntityHelper = migrationEntityHelper;
        mPriorityMigrationHelper = priorityMigrationHelper;
    }

    /** Attaches this migration cleaner to the provided {@link MigrationStateManager}. */
    public void attachTo(@NonNull MigrationStateManager migrationStateManager) {
        migrationStateManager.addStateChangedListener(this::onMigrationStateChanged);
    }

    private void onMigrationStateChanged(@HealthConnectDataState.DataMigrationState int state) {
        if (state == HealthConnectDataState.MIGRATION_STATE_COMPLETE) {
            clean();
        }
    }

    private void clean() {
        try {
            mMigrationEntityHelper.clearData(mTransactionManager);
        } catch (RuntimeException e) {
            Slog.e(LOG_TAG, "Error clearing MigrationEntityHelper", e);
        }

        try {
            mPriorityMigrationHelper.clearData(mTransactionManager);
        } catch (RuntimeException e) {
            Slog.e(LOG_TAG, "Error clearing PriorityMigrationHelper", e);
        }
    }
}
