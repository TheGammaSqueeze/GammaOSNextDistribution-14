/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.healthconnect.storage;

import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_SKIN_TEMPERATURE;

import android.annotation.NonNull;
import android.database.sqlite.SQLiteDatabase;

import com.android.server.healthconnect.storage.datatypehelpers.RecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.SkinTemperatureRecordHelper;
import com.android.server.healthconnect.storage.utils.RecordHelperProvider;

import java.util.function.Consumer;

/** Class that contains all database upgrades. */
final class DatabaseUpgradeHelper {
    public static final int DB_VERSION_UUID_BLOB = 9;
    public static final int DB_VERSION_GENERATED_LOCAL_TIME = 10;
    public static final int DB_VERSION_SKIN_TEMPERATURE = 11;

    static void onUpgrade(
            @NonNull SQLiteDatabase db,
            HealthConnectDatabase healthConnectDatabase,
            int oldVersion) {
        if (oldVersion < DB_VERSION_UUID_BLOB) {
            healthConnectDatabase.dropAllTables(db);
            healthConnectDatabase.onCreate(db);
            // OnCreate brings us to current schema. No further upgrades required, so return early.
            return;
        }
        if (oldVersion < DB_VERSION_GENERATED_LOCAL_TIME) {
            forEachRecordHelper(it -> it.applyGeneratedLocalTimeUpgrade(db));
        }
        if (oldVersion < DB_VERSION_SKIN_TEMPERATURE) {
            DatabaseUpgradeHelper.<SkinTemperatureRecordHelper>getRecordHelper(
                            RECORD_TYPE_SKIN_TEMPERATURE)
                    .applySkinTemperatureUpgrade(db);
        }
    }

    private static void forEachRecordHelper(Consumer<RecordHelper<?>> action) {
        RecordHelperProvider.getInstance().getRecordHelpers().values().forEach(action);
    }

    @SuppressWarnings("unchecked")
    private static <T> T getRecordHelper(int recordTypeIdentifier) {
        return (T) RecordHelperProvider.getInstance().getRecordHelper(recordTypeIdentifier);
    }
}
