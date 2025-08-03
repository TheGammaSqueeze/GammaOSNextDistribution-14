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

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class AlterTableRequestTest {
    private static final String TABLE_NAME = "sample_table";
    private static final String COLUMN_NAME = "sample_column";
    private static final String COLUMN_TYPE = StorageUtils.INTEGER;

    @Test
    public void testAlterTable_getAlterTableAddColumnsCommand() {
        List<Pair<String, String>> columnInfo = new ArrayList<>();
        columnInfo.add(new Pair<>(COLUMN_NAME, COLUMN_TYPE));
        AlterTableRequest alterTableRequest = new AlterTableRequest(TABLE_NAME, columnInfo);
        assertThat(alterTableRequest.getAlterTableAddColumnsCommand()).isNotNull();
        assertThat(alterTableRequest.getAlterTableAddColumnsCommand()).contains(TABLE_NAME);
        assertThat(alterTableRequest.getAlterTableAddColumnsCommand()).contains(COLUMN_NAME);
        assertThat(alterTableRequest.getAlterTableAddColumnsCommand()).contains(COLUMN_TYPE);
    }
}
