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

package android.healthconnect.cts.database;

import androidx.annotation.Nullable;

import java.util.HashMap;

/** HealthConnectDatabaseSchema stores the list of all tables present in the database. */
public class HealthConnectDatabaseSchema {
    /** HashMap with tableName and tableInfo as key - value mapping. */
    private final HashMap<String, TableInfo> mTableNameTableInfo;

    /** Creates an instance for HealthConnectDatabaseSchema. */
    HealthConnectDatabaseSchema(String inputSchema) {

        mTableNameTableInfo = HealthConnectDatabaseSchemaParser.getSchemaMap(inputSchema);
    }

    /**
     * @return the hashmap of all tables with <tableName,tableInfo> mapping.
     */
    @Nullable
    public HashMap<String, TableInfo> getTableInfo() {
        return mTableNameTableInfo;
    }
}
