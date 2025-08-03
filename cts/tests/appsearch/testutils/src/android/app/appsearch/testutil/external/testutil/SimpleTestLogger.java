/*
 * Copyright 2020 The Android Open Source Project
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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.appsearch.stats.SchemaMigrationStats;

import com.android.server.appsearch.external.localstorage.AppSearchLogger;
import com.android.server.appsearch.external.localstorage.stats.CallStats;
import com.android.server.appsearch.external.localstorage.stats.InitializeStats;
import com.android.server.appsearch.external.localstorage.stats.OptimizeStats;
import com.android.server.appsearch.external.localstorage.stats.PutDocumentStats;
import com.android.server.appsearch.external.localstorage.stats.RemoveStats;
import com.android.server.appsearch.external.localstorage.stats.SearchStats;
import com.android.server.appsearch.external.localstorage.stats.SetSchemaStats;

import java.util.ArrayList;
import java.util.List;

/**
 * Non-thread-safe simple logger implementation for testing.
 *
 * @hide
 */
public final class SimpleTestLogger implements AppSearchLogger {
    /** Holds {@link CallStats} after logging. */
    @Nullable public CallStats mCallStats;
    /** Holds {@link PutDocumentStats} after logging. */
    @Nullable public PutDocumentStats mPutDocumentStats;
    /** Holds {@link InitializeStats} after logging. */
    @Nullable public InitializeStats mInitializeStats;
    /** Holds {@link SearchStats} after logging. */
    @Nullable public SearchStats mSearchStats;
    /** Holds {@link RemoveStats} after logging. */
    @Nullable public RemoveStats mRemoveStats;
    /** Holds {@link OptimizeStats} after logging. */
    @Nullable public OptimizeStats mOptimizeStats;
    /** Holds {@link SetSchemaStats} after logging. */
    @NonNull public List<SetSchemaStats> mSetSchemaStats = new ArrayList<>();
    /** Holds {@link android.app.appsearch.stats.SchemaMigrationStats} after logging. */
    @Nullable public SchemaMigrationStats mSchemaMigrationStats;

    @Override
    public void logStats(@NonNull CallStats stats) {
        mCallStats = stats;
    }

    @Override
    public void logStats(@NonNull PutDocumentStats stats) {
        mPutDocumentStats = stats;
    }

    @Override
    public void logStats(@NonNull InitializeStats stats) {
        mInitializeStats = stats;
    }

    @Override
    public void logStats(@NonNull SearchStats stats) {
        mSearchStats = stats;
    }

    @Override
    public void logStats(@NonNull RemoveStats stats) {
        mRemoveStats = stats;
    }

    @Override
    public void logStats(@NonNull OptimizeStats stats) {
        mOptimizeStats = stats;
    }

    @Override
    public void logStats(@NonNull SetSchemaStats stats) {
        mSetSchemaStats.add(stats);
    }

    @Override
    public void logStats(@NonNull SchemaMigrationStats stats) {
        mSchemaMigrationStats = stats;
    }
}
