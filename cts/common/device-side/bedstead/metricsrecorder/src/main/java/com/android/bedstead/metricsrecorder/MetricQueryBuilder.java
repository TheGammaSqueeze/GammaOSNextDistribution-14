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

package com.android.bedstead.metricsrecorder;

import android.util.Log;

import com.android.bedstead.nene.exceptions.NeneException;
import com.android.queryable.Queryable;
import com.android.queryable.queries.BooleanQuery;
import com.android.queryable.queries.BooleanQueryHelper;
import com.android.queryable.queries.IntegerQuery;
import com.android.queryable.queries.IntegerQueryHelper;
import com.android.queryable.queries.ListQueryHelper;
import com.android.queryable.queries.StringQuery;
import com.android.queryable.queries.StringQueryHelper;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * {@link Queryable} for querying logged metrics.
 */
public class MetricQueryBuilder implements Queryable {

    private static final String LOG_TAG = "MetricQueryBuilder";

    private final EnterpriseMetricsRecorder mRecorder;
    private boolean hasStartedFetchingResults = false;
    private int mSkippedNextResults = 0;
    private int mSkippedPollResults = 0;
    private Set<EnterpriseMetricInfo> mNonMatchingMetrics = new HashSet<>();

    private final IntegerQueryHelper<MetricQueryBuilder> mTypeQuery =
            new IntegerQueryHelper<>(this);
    private final StringQueryHelper<MetricQueryBuilder> mAdminPackageNameQuery =
            new StringQueryHelper<>(this);
    private final BooleanQueryHelper<MetricQueryBuilder> mBooleanQuery =
            new BooleanQueryHelper<>(this);
    private final ListQueryHelper<MetricQueryBuilder, String> mStringsQuery =
            new ListQueryHelper<>(this);
    private final IntegerQueryHelper<MetricQueryBuilder> mIntegerQuery =
            new IntegerQueryHelper<>(this);

    MetricQueryBuilder(EnterpriseMetricsRecorder recorder) {
        mRecorder = recorder;
    }

    /** Query for {@link EnterpriseMetricInfo#type()}. */
    public IntegerQuery<MetricQueryBuilder> whereType() {
        if (hasStartedFetchingResults) {
            throw new IllegalStateException("Cannot modify query after fetching results");
        }
        return mTypeQuery;
    }

    /** Query for {@link EnterpriseMetricInfo#adminPackageName()}. */
    public StringQuery<MetricQueryBuilder> whereAdminPackageName() {
        if (hasStartedFetchingResults) {
            throw new IllegalStateException("Cannot modify query after fetching results");
        }
        return mAdminPackageNameQuery;
    }

    /** Query for {@link EnterpriseMetricInfo#Boolean()}. */
    public BooleanQuery<MetricQueryBuilder> whereBoolean() {
        if (hasStartedFetchingResults) {
            throw new IllegalStateException("Cannot modify query after fetching results");
        }
        return mBooleanQuery;
    }

    /** Query for {@link EnterpriseMetricInfo#integer()}. */
    public IntegerQuery<MetricQueryBuilder> whereInteger() {
        if (hasStartedFetchingResults) {
            throw new IllegalStateException("Cannot modify query after fetching results");
        }
        return mIntegerQuery;
    }

    public ListQueryHelper<MetricQueryBuilder, String> whereStrings() {
        if (hasStartedFetchingResults) {
            throw new IllegalStateException("Cannot modify query after fetching results");
        }
        return mStringsQuery;
    }

    public EnterpriseMetricInfo get() {
        return get(/* skipResults= */ 0);
    }

    private EnterpriseMetricInfo get(int skipResults) {
        hasStartedFetchingResults = true;
        for (EnterpriseMetricInfo m : mRecorder.fetchLatestData()) {
            if (matches(m)) {
                skipResults -= 1;
                if (skipResults < 0) {
                    return m;
                }
            } else {
                Log.d(LOG_TAG, "Found non-matching metric " + m);
                mNonMatchingMetrics.add(m);
            }
        }

        return null;
    }

    public EnterpriseMetricInfo next() {
        hasStartedFetchingResults = true;

        EnterpriseMetricInfo nextResult = get(mSkippedNextResults);
        if (nextResult != null) {
            mSkippedNextResults++;
        }

        return nextResult;
    }

    public EnterpriseMetricInfo poll() {
        return poll(Duration.ofSeconds(30));
    }

    public EnterpriseMetricInfo poll(Duration timeout) {
        hasStartedFetchingResults = true;
        Instant endTime = Instant.now().plus(timeout);

        while (Instant.now().isBefore(endTime)) {
            EnterpriseMetricInfo nextResult = get(mSkippedPollResults);
            if (nextResult != null) {
                mSkippedPollResults++;
                return nextResult;
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new NeneException("Interrupted while polling", e);
            }
        }

        return null;
    }

    /**
     * Get metrics which were received but didn't match the query.
     */
    public Set<EnterpriseMetricInfo> nonMatchingMetrics() {
        return mNonMatchingMetrics;
    }

    @Override
    public boolean isEmptyQuery() {
        return Queryable.isEmptyQuery(mAdminPackageNameQuery)
                && Queryable.isEmptyQuery(mTypeQuery)
                && Queryable.isEmptyQuery(mBooleanQuery)
                && Queryable.isEmptyQuery(mStringsQuery)
                && Queryable.isEmptyQuery(mIntegerQuery);
    }

    private boolean matches(EnterpriseMetricInfo metric) {
        return mAdminPackageNameQuery.matches(metric.adminPackageName())
                && mTypeQuery.matches(metric.type())
                && mBooleanQuery.matches(metric.Boolean())
                && mStringsQuery.matches(metric.strings())
                && mIntegerQuery.matches(metric.integer());
    }

    @Override
    public String describeQuery(String fieldName) {
        return "{" + Queryable.joinQueryStrings(
                mAdminPackageNameQuery.describeQuery("adminPackageName"),
                mTypeQuery.describeQuery("type"),
                mBooleanQuery.describeQuery("boolean"),
                mStringsQuery.describeQuery("strings"),
                mIntegerQuery.describeQuery("integer")
        ) + "}";
    }

    @Override
    public String toString() {
        return describeQuery("");
    }
}
