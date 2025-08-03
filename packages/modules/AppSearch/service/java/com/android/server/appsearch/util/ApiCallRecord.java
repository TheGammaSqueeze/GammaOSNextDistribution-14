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

package com.android.server.appsearch.util;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.appsearch.AppSearchResult;
import android.os.SystemClock;

import com.android.server.appsearch.external.localstorage.stats.CallStats;
import com.android.server.appsearch.external.localstorage.stats.OptimizeStats;

import java.util.Objects;

/**
 * A class that wraps basic information of AppSearch API calls for dumpsys.
 */
public class ApiCallRecord {
    // The time when the API call is logged, in the form of the milliseconds since boot.
    private final long mTimeMillis;

    @CallStats.CallType
    private final int mCallType;

    @Nullable
    private final String mPackageName;

    @Nullable
    private final String mDatabaseName;

    @AppSearchResult.ResultCode
    private final int mStatusCode;

    private final int mTotalLatencyMillis;

    public ApiCallRecord(@NonNull CallStats callStats) {
        Objects.requireNonNull(callStats);

        mTimeMillis = SystemClock.elapsedRealtime();
        mCallType = callStats.getCallType();
        mPackageName = callStats.getPackageName();
        mDatabaseName = callStats.getDatabase();
        mStatusCode = callStats.getStatusCode();
        mTotalLatencyMillis = callStats.getTotalLatencyMillis();
    }

    public ApiCallRecord(@NonNull OptimizeStats stats) {
        Objects.requireNonNull(stats);

        mTimeMillis = SystemClock.elapsedRealtime();
        mCallType = CallStats.CALL_TYPE_OPTIMIZE;
        mPackageName = null;
        mDatabaseName = null;
        mStatusCode = stats.getStatusCode();
        mTotalLatencyMillis = stats.getTotalLatencyMillis();
    }

    public long getTimeMillis() {
        return mTimeMillis;
    }

    @CallStats.CallType
    public int getCallType() {
        return mCallType;
    }

    @Nullable
    public String getPackageName() {
        return mPackageName;
    }

    @Nullable
    public String getDatabaseName() {
        return mDatabaseName;
    }

    @AppSearchResult.ResultCode
    public int getStatusCode() {
        return mStatusCode;
    }

    public int getTotalLatencyMillis() {
        return mTotalLatencyMillis;
    }

    /**
     * Returns the name of the type of the call. Please update the function when a new type of
     * {@link CallStats} is added.
     */
    // TODO(b/271616139) Consider moving this method as a static function in CallStats, which
    //  requires to change Jetpack first.
    @NonNull
    public String getCallTypeName() {
        switch (mCallType) {
            case CallStats.CALL_TYPE_INITIALIZE:
                return "initialize";
            case CallStats.CALL_TYPE_SET_SCHEMA:
                return "set_schema";
            case CallStats.CALL_TYPE_PUT_DOCUMENTS:
                return "put_documents";
            case CallStats.CALL_TYPE_GET_DOCUMENTS:
                return "get_documents";
            case CallStats.CALL_TYPE_REMOVE_DOCUMENTS_BY_ID:
                return "remove_documents_by_id";
            case CallStats.CALL_TYPE_PUT_DOCUMENT:
                return "put_document";
            case CallStats.CALL_TYPE_GET_DOCUMENT:
                return "get_document";
            case CallStats.CALL_TYPE_REMOVE_DOCUMENT_BY_ID:
                return "remove_document_by_id";
            case CallStats.CALL_TYPE_SEARCH:
                return "search";
            case CallStats.CALL_TYPE_OPTIMIZE:
                return "optimize";
            case CallStats.CALL_TYPE_FLUSH:
                return "flush";
            case CallStats.CALL_TYPE_GLOBAL_SEARCH:
                return "global_search";
            case CallStats.CALL_TYPE_REMOVE_DOCUMENTS_BY_SEARCH:
                return "remove_documents_by_search";
            case CallStats.CALL_TYPE_REMOVE_DOCUMENT_BY_SEARCH:
                return "remove_document_by_search";
            case CallStats.CALL_TYPE_GLOBAL_GET_DOCUMENT_BY_ID:
                return "global_get_document_by_id";
            case CallStats.CALL_TYPE_SCHEMA_MIGRATION:
                return "schema_migration";
            default:
                return "unknown_type_" + mCallType;
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Time: ").append(mTimeMillis);
        builder.append(", CallType: ").append(getCallTypeName());
        if (mPackageName != null) {
            builder.append(", PackageName: ").append(mPackageName);
        }
        if (mDatabaseName != null) {
            builder.append(", DatabaseName: ").append(
                    AdbDumpUtil.generateFingerprintMd5(mDatabaseName));
        }
        builder.append(", StatusCode: ").append(mStatusCode);
        builder.append(", TotalLatencyMillis: ").append(mTotalLatencyMillis);
        return builder.toString();
    }
}
