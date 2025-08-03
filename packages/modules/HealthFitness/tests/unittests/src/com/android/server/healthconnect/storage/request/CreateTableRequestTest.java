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

package com.android.server.healthconnect.storage.request;

import static com.google.common.truth.Truth.assertThat;

import android.util.Pair;

import com.android.server.healthconnect.storage.utils.StorageUtils;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CreateTableRequestTest {
    private CreateTableRequest mCreateTableRequest;
    private static final String TABLE_NAME = "sample_table";
    private static final String REFERENCE_TABLE = "reference_table";
    private static final String COLUMN_NAME = "sampleColumn";
    private static final String REFERENCE_COULMN = "referenceColumn";
    private static final String COLUMN_TYPE = StorageUtils.INTEGER;
    private List<Pair<String, String>> mColumnInfo = new ArrayList<>();

    @Before
    public void setUp() {
        mColumnInfo.add(new Pair<>(COLUMN_NAME, COLUMN_TYPE));
        mCreateTableRequest = new CreateTableRequest(TABLE_NAME, mColumnInfo);
    }

    @Test
    public void testCreateTable_getCreateCommand() {
        assertThat(mCreateTableRequest.getCreateCommand()).isNotNull();
        assertThat(mCreateTableRequest.getCreateCommand()).contains(TABLE_NAME);
        assertThat(mCreateTableRequest.getCreateCommand()).contains(COLUMN_NAME);
        assertThat(mCreateTableRequest.getCreateCommand()).contains(COLUMN_TYPE);
        mCreateTableRequest.addForeignKey(
                REFERENCE_TABLE,
                Collections.singletonList(REFERENCE_COULMN),
                Collections.singletonList(COLUMN_TYPE));
        mCreateTableRequest.createIndexOn(REFERENCE_COULMN);
        assertThat(mCreateTableRequest.getCreateIndexStatements()).isNotNull();
    }

    @Test
    public void testCreateTable_getChildTableRequests() {
        List<CreateTableRequest> childTables = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String tableName = "child_table_" + i;
            childTables.add(new CreateTableRequest(tableName, mColumnInfo));
        }
        mCreateTableRequest.setChildTableRequests(childTables);
        assertThat(mCreateTableRequest.getChildTableRequests()).isNotNull();
    }
}
