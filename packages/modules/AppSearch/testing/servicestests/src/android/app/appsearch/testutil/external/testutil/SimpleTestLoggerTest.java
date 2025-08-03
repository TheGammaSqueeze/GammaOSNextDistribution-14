/*
 * Copyright 2021 The Android Open Source Project
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

package android.app.appsearch.testutil;

import static com.google.common.truth.Truth.assertThat;

import android.app.appsearch.stats.SchemaMigrationStats;

import com.android.server.appsearch.external.localstorage.stats.CallStats;
import com.android.server.appsearch.external.localstorage.stats.InitializeStats;
import com.android.server.appsearch.external.localstorage.stats.OptimizeStats;
import com.android.server.appsearch.external.localstorage.stats.PutDocumentStats;
import com.android.server.appsearch.external.localstorage.stats.RemoveStats;
import com.android.server.appsearch.external.localstorage.stats.SearchStats;
import com.android.server.appsearch.external.localstorage.stats.SetSchemaStats;

import org.junit.Test;

public class SimpleTestLoggerTest {
    @Test
    public void testLogger_fieldsAreNullByDefault() {
        SimpleTestLogger logger = new SimpleTestLogger();

        assertThat(logger.mCallStats).isNull();
        assertThat(logger.mPutDocumentStats).isNull();
        assertThat(logger.mInitializeStats).isNull();
        assertThat(logger.mSearchStats).isNull();
        assertThat(logger.mRemoveStats).isNull();
        assertThat(logger.mOptimizeStats).isNull();
        assertThat(logger.mSetSchemaStats).isEmpty();
        assertThat(logger.mSchemaMigrationStats).isNull();
    }

    @Test
    public void testLogger_fieldsAreSetAfterLogging() {
        SimpleTestLogger logger = new SimpleTestLogger();

        logger.logStats(new CallStats.Builder().build());
        logger.logStats(new PutDocumentStats.Builder("package", "db").build());
        logger.logStats(new InitializeStats.Builder().build());
        logger.logStats(
                new SearchStats.Builder(SearchStats.VISIBILITY_SCOPE_UNKNOWN, "package").build());
        logger.logStats(new RemoveStats.Builder("package", "db").build());
        logger.logStats(new OptimizeStats.Builder().build());
        logger.logStats(new SetSchemaStats.Builder("package", "db").build());
        logger.logStats(new SchemaMigrationStats.Builder("package", "db").build());

        assertThat(logger.mCallStats).isNotNull();
        assertThat(logger.mPutDocumentStats).isNotNull();
        assertThat(logger.mInitializeStats).isNotNull();
        assertThat(logger.mSearchStats).isNotNull();
        assertThat(logger.mRemoveStats).isNotNull();
        assertThat(logger.mOptimizeStats).isNotNull();
        assertThat(logger.mSetSchemaStats).isNotNull();
        assertThat(logger.mSchemaMigrationStats).isNotNull();
    }
}
