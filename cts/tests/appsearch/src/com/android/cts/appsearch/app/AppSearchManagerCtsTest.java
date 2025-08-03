/*
 * Copyright (C) 2021 The Android Open Source Project
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
package android.app.appsearch.cts.app;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.appsearch.AppSearchManager;

import org.junit.Test;

public class AppSearchManagerCtsTest {
    @Test
    public void testSearchContext_databaseName() {
        AppSearchManager.SearchContext searchContext =
                new AppSearchManager.SearchContext.Builder(/*databaseName=*/"dbName")
                        .build();

        assertThat(searchContext.getDatabaseName()).isEqualTo("dbName");
    }

    @Test
    public void testSearchContext_withInvalidDatabaseName() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new AppSearchManager.SearchContext.Builder(
                        "testDatabaseNameEndWith/").build());
        assertThat(e).hasMessageThat().isEqualTo("Database name cannot contain '/'");
    }
}
