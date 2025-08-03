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

import org.junit.Test;

import java.util.Collections;

public class CreateIndexRequestTest {
    private static final String TABLE_NAME = "sample_table";
    private static final String INDEX_NAME = "index_column";
    private static final String COLUMN_NAME = "sample_column";
    private static final String UNIQUE = "UNIQUE";

    @Test
    public void testCreateIndex_getCommandWithUnique() {
        CreateIndexRequest createIndexRequest =
                new CreateIndexRequest(
                        TABLE_NAME, INDEX_NAME, true, Collections.singletonList(COLUMN_NAME));
        assertThat(createIndexRequest.getCommand()).contains(TABLE_NAME);
        assertThat(createIndexRequest.getCommand()).contains(INDEX_NAME);
        assertThat(createIndexRequest.getCommand()).contains(COLUMN_NAME);
        assertThat(createIndexRequest.getCommand()).contains(UNIQUE);
    }

    @Test
    public void testCreateIndex_getCommandWithoutUnique() {
        CreateIndexRequest createIndexRequest =
                new CreateIndexRequest(
                        TABLE_NAME, INDEX_NAME, false, Collections.singletonList(COLUMN_NAME));
        assertThat(createIndexRequest.getCommand()).contains(TABLE_NAME);
        assertThat(createIndexRequest.getCommand()).contains(INDEX_NAME);
        assertThat(createIndexRequest.getCommand()).contains(COLUMN_NAME);
        assertThat(createIndexRequest.getCommand()).doesNotContain(UNIQUE);
    }
}
