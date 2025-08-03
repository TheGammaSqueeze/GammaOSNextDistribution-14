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

package com.android.server.appsearch;

import static com.android.internal.util.ConcurrentUtils.DIRECT_EXECUTOR;
import static com.android.server.appsearch.FrameworkAppSearchConfig.DEFAULT_API_CALL_STATS_LIMIT;
import static com.android.server.appsearch.FrameworkAppSearchConfig.DEFAULT_BYTES_OPTIMIZE_THRESHOLD;
import static com.android.server.appsearch.FrameworkAppSearchConfig.DEFAULT_DOC_COUNT_OPTIMIZE_THRESHOLD;
import static com.android.server.appsearch.FrameworkAppSearchConfig.DEFAULT_ICING_CONFIG_USE_READ_ONLY_SEARCH;
import static com.android.server.appsearch.FrameworkAppSearchConfig.DEFAULT_INTEGER_INDEX_BUCKET_SPLIT_THRESHOLD;
import static com.android.server.appsearch.FrameworkAppSearchConfig.DEFAULT_LIMIT_CONFIG_MAX_DOCUMENT_COUNT;
import static com.android.server.appsearch.FrameworkAppSearchConfig.DEFAULT_LIMIT_CONFIG_MAX_DOCUMENT_SIZE_BYTES;
import static com.android.server.appsearch.FrameworkAppSearchConfig.DEFAULT_LIMIT_CONFIG_MAX_SUGGESTION_COUNT;
import static com.android.server.appsearch.FrameworkAppSearchConfig.DEFAULT_LITE_INDEX_SORT_AT_INDEXING;
import static com.android.server.appsearch.FrameworkAppSearchConfig.DEFAULT_LITE_INDEX_SORT_SIZE;
import static com.android.server.appsearch.FrameworkAppSearchConfig.DEFAULT_MIN_TIME_INTERVAL_BETWEEN_SAMPLES_MILLIS;
import static com.android.server.appsearch.FrameworkAppSearchConfig.DEFAULT_RATE_LIMIT_ENABLED;
import static com.android.server.appsearch.FrameworkAppSearchConfig.DEFAULT_RATE_LIMIT_TASK_QUEUE_PER_PACKAGE_CAPACITY_PERCENTAGE;
import static com.android.server.appsearch.FrameworkAppSearchConfig.DEFAULT_RATE_LIMIT_TASK_QUEUE_TOTAL_CAPACITY;
import static com.android.server.appsearch.FrameworkAppSearchConfig.DEFAULT_SAMPLING_INTERVAL;
import static com.android.server.appsearch.FrameworkAppSearchConfig.DEFAULT_TIME_OPTIMIZE_THRESHOLD_MILLIS;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_API_CALL_STATS_LIMIT;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_BYTES_OPTIMIZE_THRESHOLD;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_DENYLIST;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_DOC_COUNT_OPTIMIZE_THRESHOLD;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_ICING_COMPRESSION_LEVEL;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_ICING_DOCUMENT_STORE_NAMESPACE_ID_FINGERPRINT;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_ICING_INDEX_MERGE_SIZE;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_ICING_INTEGER_INDEX_BUCKET_SPLIT_THRESHOLD;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_ICING_LITE_INDEX_SORT_AT_INDEXING;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_ICING_LITE_INDEX_SORT_SIZE;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_ICING_MAX_PAGE_BYTES_LIMIT;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_ICING_MAX_TOKEN_LENGTH;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_ICING_OPTIMIZE_REBUILD_INDEX_THRESHOLD;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_ICING_USE_PERSISTENT_HASHMAP;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_ICING_USE_PRE_MAPPING_WITH_FILE_BACKED_VECTOR;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_ICING_USE_READ_ONLY_SEARCH;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_LIMIT_CONFIG_MAX_DOCUMENT_COUNT;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_LIMIT_CONFIG_MAX_DOCUMENT_SIZE_BYTES;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_LIMIT_CONFIG_MAX_SUGGESTION_COUNT;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_MIN_TIME_INTERVAL_BETWEEN_SAMPLES_MILLIS;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_MIN_TIME_OPTIMIZE_THRESHOLD_MILLIS;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_RATE_LIMIT_API_COSTS;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_RATE_LIMIT_ENABLED;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_RATE_LIMIT_TASK_QUEUE_PER_PACKAGE_CAPACITY_PERCENTAGE;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_RATE_LIMIT_TASK_QUEUE_TOTAL_CAPACITY;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_SAMPLING_INTERVAL_DEFAULT;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_SAMPLING_INTERVAL_FOR_BATCH_CALL_STATS;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_SAMPLING_INTERVAL_FOR_GLOBAL_SEARCH_STATS;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_SAMPLING_INTERVAL_FOR_INITIALIZE_STATS;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_SAMPLING_INTERVAL_FOR_OPTIMIZE_STATS;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_SAMPLING_INTERVAL_FOR_PUT_DOCUMENT_STATS;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_SAMPLING_INTERVAL_FOR_SEARCH_STATS;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_TIME_OPTIMIZE_THRESHOLD_MILLIS;
import static com.android.server.appsearch.FrameworkAppSearchConfigImpl.KEY_USE_NEW_QUALIFIED_ID_JOIN_INDEX;
import static com.android.server.appsearch.external.localstorage.IcingOptionsConfig.DEFAULT_USE_NEW_QUALIFIED_ID_JOIN_INDEX;
import static com.google.common.truth.Truth.assertThat;

import android.provider.DeviceConfig;
import com.android.modules.utils.testing.TestableDeviceConfig;
import com.android.server.appsearch.external.localstorage.AppSearchConfig;
import com.android.server.appsearch.external.localstorage.IcingOptionsConfig;
import com.android.server.appsearch.external.localstorage.stats.CallStats;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class FrameworkAppSearchConfigTest {
    @Rule
    public final TestableDeviceConfig.TestableDeviceConfigRule
            mDeviceConfigRule = new TestableDeviceConfig.TestableDeviceConfigRule();

    @Test
    public void testDefaultValues_allCachedValue() {
        FrameworkAppSearchConfig appSearchConfig =
            FrameworkAppSearchConfigImpl.create(DIRECT_EXECUTOR);

        assertThat(appSearchConfig.getCachedMinTimeIntervalBetweenSamplesMillis()).isEqualTo(
                DEFAULT_MIN_TIME_INTERVAL_BETWEEN_SAMPLES_MILLIS);
        assertThat(appSearchConfig.getCachedSamplingIntervalDefault()).isEqualTo(
                DEFAULT_SAMPLING_INTERVAL);
        assertThat(appSearchConfig.getCachedSamplingIntervalForBatchCallStats()).isEqualTo(
                DEFAULT_SAMPLING_INTERVAL);
        assertThat(appSearchConfig.getCachedSamplingIntervalForPutDocumentStats()).isEqualTo(
                DEFAULT_SAMPLING_INTERVAL);
        assertThat(appSearchConfig.getCachedSamplingIntervalForInitializeStats()).isEqualTo(
                DEFAULT_SAMPLING_INTERVAL);
        assertThat(appSearchConfig.getCachedSamplingIntervalForSearchStats()).isEqualTo(
                DEFAULT_SAMPLING_INTERVAL);
        assertThat(appSearchConfig.getCachedSamplingIntervalForGlobalSearchStats()).isEqualTo(
                DEFAULT_SAMPLING_INTERVAL);
        assertThat(appSearchConfig.getCachedSamplingIntervalForOptimizeStats()).isEqualTo(
                DEFAULT_SAMPLING_INTERVAL);
        assertThat(appSearchConfig.getMaxDocumentSizeBytes()).isEqualTo(
                DEFAULT_LIMIT_CONFIG_MAX_DOCUMENT_SIZE_BYTES);
        assertThat(appSearchConfig.getMaxDocumentCount()).isEqualTo(
                DEFAULT_LIMIT_CONFIG_MAX_DOCUMENT_COUNT);
        assertThat(appSearchConfig.getMaxSuggestionCount()).isEqualTo(
                DEFAULT_LIMIT_CONFIG_MAX_SUGGESTION_COUNT);
        assertThat(appSearchConfig.getCachedBytesOptimizeThreshold()).isEqualTo(
                DEFAULT_BYTES_OPTIMIZE_THRESHOLD);
        assertThat(appSearchConfig.getCachedTimeOptimizeThresholdMs()).isEqualTo(
                DEFAULT_TIME_OPTIMIZE_THRESHOLD_MILLIS);
        assertThat(appSearchConfig.getCachedDocCountOptimizeThreshold()).isEqualTo(
                DEFAULT_DOC_COUNT_OPTIMIZE_THRESHOLD);
        assertThat(appSearchConfig.getCachedApiCallStatsLimit()).isEqualTo(
                DEFAULT_API_CALL_STATS_LIMIT);
        assertThat(appSearchConfig.getCachedDenylist()).isEqualTo(Denylist.EMPTY_INSTANCE);
        assertThat(appSearchConfig.getMaxTokenLength()).isEqualTo(
                IcingOptionsConfig.DEFAULT_MAX_TOKEN_LENGTH);
        assertThat(appSearchConfig.getIndexMergeSize()).isEqualTo(
                IcingOptionsConfig.DEFAULT_INDEX_MERGE_SIZE);
        assertThat(appSearchConfig.getDocumentStoreNamespaceIdFingerprint()).isEqualTo(
                IcingOptionsConfig.DEFAULT_DOCUMENT_STORE_NAMESPACE_ID_FINGERPRINT);
        assertThat(appSearchConfig.getOptimizeRebuildIndexThreshold()).isEqualTo(
                IcingOptionsConfig.DEFAULT_OPTIMIZE_REBUILD_INDEX_THRESHOLD);
        assertThat(appSearchConfig.getCompressionLevel()).isEqualTo(
                IcingOptionsConfig.DEFAULT_COMPRESSION_LEVEL);
        assertThat(appSearchConfig.getUseReadOnlySearch()).isEqualTo(
                DEFAULT_ICING_CONFIG_USE_READ_ONLY_SEARCH);
        assertThat(appSearchConfig.getUsePreMappingWithFileBackedVector()).isEqualTo(
                IcingOptionsConfig.DEFAULT_USE_PREMAPPING_WITH_FILE_BACKED_VECTOR);
        assertThat(appSearchConfig.getUsePersistentHashMap()).isEqualTo(
                IcingOptionsConfig.DEFAULT_USE_PERSISTENT_HASH_MAP);
        assertThat(appSearchConfig.getMaxPageBytesLimit()).isEqualTo(
                IcingOptionsConfig.DEFAULT_MAX_PAGE_BYTES_LIMIT);
        assertThat(appSearchConfig.getCachedRateLimitEnabled()).isEqualTo(
                DEFAULT_RATE_LIMIT_ENABLED);
        AppSearchRateLimitConfig rateLimitConfig = appSearchConfig.getCachedRateLimitConfig();
        assertThat(rateLimitConfig.getTaskQueueTotalCapacity()).isEqualTo(
                DEFAULT_RATE_LIMIT_TASK_QUEUE_TOTAL_CAPACITY);
        assertThat(rateLimitConfig.getTaskQueuePerPackageCapacity()).isEqualTo(
                (int) (DEFAULT_RATE_LIMIT_TASK_QUEUE_PER_PACKAGE_CAPACITY_PERCENTAGE
                        * DEFAULT_RATE_LIMIT_TASK_QUEUE_TOTAL_CAPACITY));
        // Check that rate limit api costs are set to default
        assertThat(rateLimitConfig.getApiCost(CallStats.CALL_TYPE_GET_DOCUMENT)).isEqualTo(
                AppSearchRateLimitConfig.DEFAULT_API_COST);
        assertThat(rateLimitConfig.getApiCost(CallStats.CALL_TYPE_GET_NEXT_PAGE)).isEqualTo(
                AppSearchRateLimitConfig.DEFAULT_API_COST);
        assertThat(rateLimitConfig.getApiCost(CallStats.CALL_TYPE_SET_SCHEMA)).isEqualTo(
                AppSearchRateLimitConfig.DEFAULT_API_COST);
        assertThat(rateLimitConfig.getApiCost(CallStats.CALL_TYPE_SEARCH)).isEqualTo(
                AppSearchRateLimitConfig.DEFAULT_API_COST);
        assertThat(appSearchConfig.getIntegerIndexBucketSplitThreshold()).isEqualTo(
                DEFAULT_INTEGER_INDEX_BUCKET_SPLIT_THRESHOLD);
        assertThat(appSearchConfig.getLiteIndexSortAtIndexing()).isEqualTo(
                DEFAULT_LITE_INDEX_SORT_AT_INDEXING);
        assertThat(appSearchConfig.getLiteIndexSortSize()).isEqualTo(DEFAULT_LITE_INDEX_SORT_SIZE);
        assertThat(appSearchConfig.getUseNewQualifiedIdJoinIndex())
            .isEqualTo(DEFAULT_USE_NEW_QUALIFIED_ID_JOIN_INDEX);
    }

    @Test
    public void testCustomizedValue_minTimeIntervalBetweenSamplesMillis() {
        final long minTimeIntervalBetweenSamplesMillis = -1;
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_MIN_TIME_INTERVAL_BETWEEN_SAMPLES_MILLIS,
                Long.toString(minTimeIntervalBetweenSamplesMillis),
                false);

        FrameworkAppSearchConfig appSearchConfig =
            FrameworkAppSearchConfigImpl.create(DIRECT_EXECUTOR);

        assertThat(appSearchConfig.getCachedMinTimeIntervalBetweenSamplesMillis()).isEqualTo(
                minTimeIntervalBetweenSamplesMillis);
    }

    @Test
    public void testCustomizedValueOverride_minTimeIntervalBetweenSamplesMillis() {
        long minTimeIntervalBetweenSamplesMillis = -1;
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_MIN_TIME_INTERVAL_BETWEEN_SAMPLES_MILLIS,
                Long.toString(minTimeIntervalBetweenSamplesMillis),
                false);
        FrameworkAppSearchConfig appSearchConfig =
            FrameworkAppSearchConfigImpl.create(DIRECT_EXECUTOR);

        minTimeIntervalBetweenSamplesMillis = -2;
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_MIN_TIME_INTERVAL_BETWEEN_SAMPLES_MILLIS,
                Long.toString(minTimeIntervalBetweenSamplesMillis),
                false);

        assertThat(appSearchConfig.getCachedMinTimeIntervalBetweenSamplesMillis()).isEqualTo(
                minTimeIntervalBetweenSamplesMillis);
    }

    @Test
    public void testCustomizedValue_allSamplingIntervals() {
        final int samplingIntervalDefault = -1;
        final int samplingIntervalPutDocumentStats = -2;
        final int samplingIntervalBatchCallStats = -3;
        final int samplingIntervalInitializeStats = -4;
        final int samplingIntervalSearchStats = -5;
        final int samplingIntervalGlobalSearchStats = -6;
        final int samplingIntervalOptimizeStats = -7;

        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_SAMPLING_INTERVAL_DEFAULT,
                Integer.toString(samplingIntervalDefault),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_SAMPLING_INTERVAL_FOR_PUT_DOCUMENT_STATS,
                Integer.toString(samplingIntervalPutDocumentStats),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_SAMPLING_INTERVAL_FOR_BATCH_CALL_STATS,
                Integer.toString(samplingIntervalBatchCallStats),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_SAMPLING_INTERVAL_FOR_INITIALIZE_STATS,
                Integer.toString(samplingIntervalInitializeStats),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_SAMPLING_INTERVAL_FOR_SEARCH_STATS,
                Integer.toString(samplingIntervalSearchStats),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_SAMPLING_INTERVAL_FOR_GLOBAL_SEARCH_STATS,
                Integer.toString(samplingIntervalGlobalSearchStats),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_SAMPLING_INTERVAL_FOR_OPTIMIZE_STATS,
                Integer.toString(samplingIntervalOptimizeStats),
                false);

        FrameworkAppSearchConfig appSearchConfig =
            FrameworkAppSearchConfigImpl.create(DIRECT_EXECUTOR);

        assertThat(appSearchConfig.getCachedSamplingIntervalDefault()).isEqualTo(
                samplingIntervalDefault);
        assertThat(appSearchConfig.getCachedSamplingIntervalForPutDocumentStats()).isEqualTo(
                samplingIntervalPutDocumentStats);
        assertThat(appSearchConfig.getCachedSamplingIntervalForBatchCallStats()).isEqualTo(
                samplingIntervalBatchCallStats);
        assertThat(appSearchConfig.getCachedSamplingIntervalForInitializeStats()).isEqualTo(
                samplingIntervalInitializeStats);
        assertThat(appSearchConfig.getCachedSamplingIntervalForSearchStats()).isEqualTo(
                samplingIntervalSearchStats);
        assertThat(appSearchConfig.getCachedSamplingIntervalForGlobalSearchStats()).isEqualTo(
                samplingIntervalGlobalSearchStats);
        assertThat(appSearchConfig.getCachedSamplingIntervalForOptimizeStats()).isEqualTo(
                samplingIntervalOptimizeStats);
    }

    @Test
    public void testCustomizedValueOverride_allSamplingIntervals() {
        int samplingIntervalDefault = -1;
        int samplingIntervalPutDocumentStats = -2;
        int samplingIntervalBatchCallStats = -3;
        int samplingIntervalInitializeStats = -4;
        int samplingIntervalSearchStats = -5;
        int samplingIntervalGlobalSearchStats = -6;
        int samplingIntervalOptimizeStats = -7;
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_SAMPLING_INTERVAL_DEFAULT,
                Integer.toString(samplingIntervalDefault),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_SAMPLING_INTERVAL_FOR_PUT_DOCUMENT_STATS,
                Integer.toString(samplingIntervalPutDocumentStats),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_SAMPLING_INTERVAL_FOR_BATCH_CALL_STATS,
                Integer.toString(samplingIntervalBatchCallStats),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_SAMPLING_INTERVAL_FOR_INITIALIZE_STATS,
                Integer.toString(samplingIntervalInitializeStats),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_SAMPLING_INTERVAL_FOR_SEARCH_STATS,
                Integer.toString(samplingIntervalSearchStats),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_SAMPLING_INTERVAL_FOR_GLOBAL_SEARCH_STATS,
                Integer.toString(samplingIntervalGlobalSearchStats),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_SAMPLING_INTERVAL_FOR_OPTIMIZE_STATS,
                Integer.toString(samplingIntervalOptimizeStats),
                false);
        FrameworkAppSearchConfig appSearchConfig =
            FrameworkAppSearchConfigImpl.create(DIRECT_EXECUTOR);

        // Overrides
        samplingIntervalDefault = -4;
        samplingIntervalPutDocumentStats = -5;
        samplingIntervalBatchCallStats = -6;
        samplingIntervalInitializeStats = -7;
        samplingIntervalSearchStats = -8;
        samplingIntervalGlobalSearchStats = -9;
        samplingIntervalOptimizeStats = -10;
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_SAMPLING_INTERVAL_DEFAULT,
                Integer.toString(samplingIntervalDefault),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_SAMPLING_INTERVAL_FOR_PUT_DOCUMENT_STATS,
                Integer.toString(samplingIntervalPutDocumentStats),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_SAMPLING_INTERVAL_FOR_BATCH_CALL_STATS,
                Integer.toString(samplingIntervalBatchCallStats),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_SAMPLING_INTERVAL_FOR_INITIALIZE_STATS,
                Integer.toString(samplingIntervalInitializeStats),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_SAMPLING_INTERVAL_FOR_SEARCH_STATS,
                Integer.toString(samplingIntervalSearchStats),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_SAMPLING_INTERVAL_FOR_GLOBAL_SEARCH_STATS,
                Integer.toString(samplingIntervalGlobalSearchStats),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_SAMPLING_INTERVAL_FOR_OPTIMIZE_STATS,
                Integer.toString(samplingIntervalOptimizeStats),
                false);

        assertThat(appSearchConfig.getCachedSamplingIntervalDefault()).isEqualTo(
                samplingIntervalDefault);
        assertThat(appSearchConfig.getCachedSamplingIntervalForPutDocumentStats()).isEqualTo(
                samplingIntervalPutDocumentStats);
        assertThat(appSearchConfig.getCachedSamplingIntervalForBatchCallStats()).isEqualTo(
                samplingIntervalBatchCallStats);
        assertThat(appSearchConfig.getCachedSamplingIntervalForInitializeStats()).isEqualTo(
                samplingIntervalInitializeStats);
        assertThat(appSearchConfig.getCachedSamplingIntervalForSearchStats()).isEqualTo(
                samplingIntervalSearchStats);
        assertThat(appSearchConfig.getCachedSamplingIntervalForGlobalSearchStats()).isEqualTo(
                samplingIntervalGlobalSearchStats);
        assertThat(appSearchConfig.getCachedSamplingIntervalForOptimizeStats()).isEqualTo(
                samplingIntervalOptimizeStats);
    }

    /**
     * Tests if we fall back to {@link AppSearchConfig#DEFAULT_SAMPLING_INTERVAL} if both default
     * sampling interval and custom value are not set in DeviceConfig, and there is some other
     * sampling interval set.
     */
    @Test
    public void testFallbackToDefaultSamplingValue_useHardCodedDefault() {
        final int samplingIntervalPutDocumentStats = -1;
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_SAMPLING_INTERVAL_FOR_PUT_DOCUMENT_STATS,
                Integer.toString(samplingIntervalPutDocumentStats),
                false);

        FrameworkAppSearchConfig appSearchConfig =
            FrameworkAppSearchConfigImpl.create(DIRECT_EXECUTOR);

        assertThat(appSearchConfig.getCachedSamplingIntervalForPutDocumentStats()).isEqualTo(
                samplingIntervalPutDocumentStats);
        assertThat(appSearchConfig.getCachedSamplingIntervalForBatchCallStats()).isEqualTo(
                DEFAULT_SAMPLING_INTERVAL);
    }

    // Tests if we fall back to configured default sampling interval if custom value is not set in
    // DeviceConfig.
    @Test
    public void testFallbackDefaultSamplingValue_useConfiguredDefault() {
        final int samplingIntervalPutDocumentStats = -1;
        final int samplingIntervalDefault = -2;
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_SAMPLING_INTERVAL_FOR_PUT_DOCUMENT_STATS,
                Integer.toString(samplingIntervalPutDocumentStats),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_SAMPLING_INTERVAL_DEFAULT,
                Integer.toString(samplingIntervalDefault),
                false);

        FrameworkAppSearchConfig appSearchConfig =
            FrameworkAppSearchConfigImpl.create(DIRECT_EXECUTOR);

        assertThat(appSearchConfig.getCachedSamplingIntervalForPutDocumentStats()).isEqualTo(
                samplingIntervalPutDocumentStats);
        assertThat(appSearchConfig.getCachedSamplingIntervalForBatchCallStats()).isEqualTo(
                samplingIntervalDefault);
    }

    // Tests that cached values should reflect latest values in DeviceConfig.
    @Test
    public void testFallbackDefaultSamplingValue_defaultValueChanged() {
        int samplingIntervalPutDocumentStats = -1;
        int samplingIntervalDefault = -2;
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_SAMPLING_INTERVAL_FOR_PUT_DOCUMENT_STATS,
                Integer.toString(samplingIntervalPutDocumentStats),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_SAMPLING_INTERVAL_DEFAULT,
                Integer.toString(samplingIntervalDefault),
                false);

        FrameworkAppSearchConfig appSearchConfig =
            FrameworkAppSearchConfigImpl.create(DIRECT_EXECUTOR);

        // Sampling values changed.
        samplingIntervalPutDocumentStats = -3;
        samplingIntervalDefault = -4;
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_SAMPLING_INTERVAL_FOR_PUT_DOCUMENT_STATS,
                Integer.toString(samplingIntervalPutDocumentStats),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_SAMPLING_INTERVAL_DEFAULT,
                Integer.toString(samplingIntervalDefault),
                false);

        assertThat(appSearchConfig.getCachedSamplingIntervalForPutDocumentStats()).isEqualTo(
                samplingIntervalPutDocumentStats);
        assertThat(appSearchConfig.getCachedSamplingIntervalForBatchCallStats()).isEqualTo(
                samplingIntervalDefault);
    }

    // Tests default sampling interval won't affect custom sampling intervals if they are set.
    @Test
    public void testShouldNotFallBack_ifValueConfigured() {
        int samplingIntervalDefault = -1;
        int samplingIntervalBatchCallStats = -2;
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_SAMPLING_INTERVAL_DEFAULT,
                Integer.toString(samplingIntervalDefault),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_SAMPLING_INTERVAL_FOR_BATCH_CALL_STATS,
                Integer.toString(samplingIntervalBatchCallStats),
                false);

        FrameworkAppSearchConfig appSearchConfig =
            FrameworkAppSearchConfigImpl.create(DIRECT_EXECUTOR);

        // Default sampling interval changed.
        samplingIntervalDefault = -3;
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_SAMPLING_INTERVAL_DEFAULT,
                Integer.toString(samplingIntervalDefault),
                false);

        assertThat(appSearchConfig.getCachedSamplingIntervalForBatchCallStats()).isEqualTo(
                samplingIntervalBatchCallStats);
    }

    @Test
    public void testCustomizedValueOverride_maxDocument() {
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_LIMIT_CONFIG_MAX_DOCUMENT_SIZE_BYTES,
                Integer.toString(2001),
                /*makeDefault=*/ false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_LIMIT_CONFIG_MAX_DOCUMENT_COUNT,
                Integer.toString(2002),
                /*makeDefault=*/ false);

        FrameworkAppSearchConfig appSearchConfig =
            FrameworkAppSearchConfigImpl.create(DIRECT_EXECUTOR);
        assertThat(appSearchConfig.getMaxDocumentSizeBytes()).isEqualTo(2001);
        assertThat(appSearchConfig.getMaxDocumentCount()).isEqualTo(2002);

        // Override
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_LIMIT_CONFIG_MAX_DOCUMENT_SIZE_BYTES,
                Integer.toString(1775),
                /*makeDefault=*/ false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_LIMIT_CONFIG_MAX_DOCUMENT_COUNT,
                Integer.toString(1776),
                /*makeDefault=*/ false);

        assertThat(appSearchConfig.getMaxDocumentSizeBytes()).isEqualTo(1775);
        assertThat(appSearchConfig.getMaxDocumentCount()).isEqualTo(1776);
    }

    @Test
    public void testCustomizedValueOverride_maxSuggestionCount() {
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_LIMIT_CONFIG_MAX_SUGGESTION_COUNT,
                Integer.toString(2003),
                /*makeDefault=*/ false);

        FrameworkAppSearchConfig appSearchConfig =
            FrameworkAppSearchConfigImpl.create(DIRECT_EXECUTOR);
        assertThat(appSearchConfig.getMaxSuggestionCount()).isEqualTo(2003);

        // Override
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_LIMIT_CONFIG_MAX_SUGGESTION_COUNT,
                Integer.toString(1777),
                /*makeDefault=*/ false);

        assertThat(appSearchConfig.getMaxSuggestionCount()).isEqualTo(1777);
    }

    @Test
    public void testCustomizedValue_optimizeThreshold() {
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_BYTES_OPTIMIZE_THRESHOLD,
                Integer.toString(147147),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_TIME_OPTIMIZE_THRESHOLD_MILLIS,
                Integer.toString(258258),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_DOC_COUNT_OPTIMIZE_THRESHOLD,
                Integer.toString(369369),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_MIN_TIME_OPTIMIZE_THRESHOLD_MILLIS,
                Integer.toString(1000),
                false);

        FrameworkAppSearchConfig appSearchConfig =
            FrameworkAppSearchConfigImpl.create(DIRECT_EXECUTOR);

        assertThat(appSearchConfig.getCachedBytesOptimizeThreshold()).isEqualTo(147147);
        assertThat(appSearchConfig.getCachedTimeOptimizeThresholdMs()).isEqualTo(258258);
        assertThat(appSearchConfig.getCachedDocCountOptimizeThreshold()).isEqualTo(369369);
        assertThat(appSearchConfig.getCachedMinTimeOptimizeThresholdMs()).isEqualTo(1000);
    }

    @Test
    public void testCustomizedValueOverride_optimizeThreshold() {
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_BYTES_OPTIMIZE_THRESHOLD,
                Integer.toString(147147),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_TIME_OPTIMIZE_THRESHOLD_MILLIS,
                Integer.toString(258258),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_DOC_COUNT_OPTIMIZE_THRESHOLD,
                Integer.toString(369369),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_MIN_TIME_OPTIMIZE_THRESHOLD_MILLIS,
                Integer.toString(1000),
                false);

        FrameworkAppSearchConfig appSearchConfig =
            FrameworkAppSearchConfigImpl.create(DIRECT_EXECUTOR);

        // Override
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_BYTES_OPTIMIZE_THRESHOLD,
                Integer.toString(741741),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_TIME_OPTIMIZE_THRESHOLD_MILLIS,
                Integer.toString(852852),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_DOC_COUNT_OPTIMIZE_THRESHOLD,
                Integer.toString(963963),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_MIN_TIME_OPTIMIZE_THRESHOLD_MILLIS,
                Integer.toString(2000),
                false);

        assertThat(appSearchConfig.getCachedBytesOptimizeThreshold()).isEqualTo(741741);
        assertThat(appSearchConfig.getCachedTimeOptimizeThresholdMs()).isEqualTo(852852);
        assertThat(appSearchConfig.getCachedDocCountOptimizeThreshold()).isEqualTo(963963);
        assertThat(appSearchConfig.getCachedMinTimeOptimizeThresholdMs()).isEqualTo(2000);
    }

    @Test
    public void testCustomizedValue_dumpsysStatsLimit() {
        final long dumpsysStatsLimit = 10;
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_API_CALL_STATS_LIMIT, Long.toString(dumpsysStatsLimit), false);

        FrameworkAppSearchConfig appSearchConfig =
            FrameworkAppSearchConfigImpl.create(DIRECT_EXECUTOR);

        assertThat(appSearchConfig.getCachedApiCallStatsLimit()).isEqualTo(dumpsysStatsLimit);
    }

    @Test
    public void testCustomizedValueOverride_dumpsysStatsLimit() {
        long dumpsysStatsLimit = 10;
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_API_CALL_STATS_LIMIT, Long.toString(dumpsysStatsLimit), false);
        FrameworkAppSearchConfig appSearchConfig =
            FrameworkAppSearchConfigImpl.create(DIRECT_EXECUTOR);

        long newDumpsysStatsLimit = 20;
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_API_CALL_STATS_LIMIT, Long.toString(newDumpsysStatsLimit), false);

        assertThat(appSearchConfig.getCachedApiCallStatsLimit()).isEqualTo(newDumpsysStatsLimit);
    }

    @Test
    public void testCustomizedValue_denylist() {
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_DENYLIST, "pkg=foo&db=bar&apis=localSetSchema,localGetSchema", false);

        FrameworkAppSearchConfig appSearchConfig =
            FrameworkAppSearchConfigImpl.create(DIRECT_EXECUTOR);
        assertThat(appSearchConfig.getCachedDenylist().checkDeniedPackageDatabase("foo", "bar",
                CallStats.CALL_TYPE_SET_SCHEMA)).isTrue();
        assertThat(appSearchConfig.getCachedDenylist().checkDeniedPackageDatabase("foo", "bar",
                CallStats.CALL_TYPE_GET_SCHEMA)).isTrue();
        assertThat(appSearchConfig.getCachedDenylist().checkDeniedPackageDatabase("foo", "bar",
                CallStats.CALL_TYPE_INITIALIZE)).isFalse();
    }

    @Test
    public void testCustomizedValueOverride_denylist() {
        FrameworkAppSearchConfig appSearchConfig =
            FrameworkAppSearchConfigImpl.create(DIRECT_EXECUTOR);

        // By default, denylist should be empty
        for (Integer apiType : CallStats.getAllApiCallTypes()) {
            assertThat(appSearchConfig.getCachedDenylist().checkDeniedPackageDatabase("foo", "bar",
                    apiType)).isFalse();
            assertThat(appSearchConfig.getCachedDenylist().checkDeniedPackage("foo", apiType))
                    .isFalse();
        }

        // Overriding with the flag creates a new denylist
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_DENYLIST, "pkg=foo&db=bar&apis=initialize", false);

        assertThat(appSearchConfig.getCachedDenylist().checkDeniedPackageDatabase("foo", "bar",
                CallStats.CALL_TYPE_INITIALIZE)).isTrue();

        // Overriding with an empty flag sets an empty denylist
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH, KEY_DENYLIST, "", false);

        for (Integer apiType : CallStats.getAllApiCallTypes()) {
            assertThat(appSearchConfig.getCachedDenylist().checkDeniedPackageDatabase("foo", "bar",
                    apiType)).isFalse();
            assertThat(appSearchConfig.getCachedDenylist().checkDeniedPackage("foo", apiType))
                    .isFalse();
        }
    }

    @Test
    public void testCustomizedValue_icingOptions() {
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_MAX_TOKEN_LENGTH, Integer.toString(15), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_INDEX_MERGE_SIZE, Integer.toString(1000), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_DOCUMENT_STORE_NAMESPACE_ID_FINGERPRINT, Boolean.toString(true), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_OPTIMIZE_REBUILD_INDEX_THRESHOLD, Float.toString(0.5f), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_COMPRESSION_LEVEL, Integer.toString(5), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_USE_READ_ONLY_SEARCH, Boolean.toString(false), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_USE_PRE_MAPPING_WITH_FILE_BACKED_VECTOR, Boolean.toString(true), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_USE_PERSISTENT_HASHMAP, Boolean.toString(true), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_MAX_PAGE_BYTES_LIMIT, Integer.toString(1001), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_INTEGER_INDEX_BUCKET_SPLIT_THRESHOLD, Integer.toString(1002), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_LITE_INDEX_SORT_AT_INDEXING, Boolean.toString(true), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_LITE_INDEX_SORT_SIZE, Integer.toString(1003), false);

        FrameworkAppSearchConfig appSearchConfig =
            FrameworkAppSearchConfigImpl.create(DIRECT_EXECUTOR);
        assertThat(appSearchConfig.getMaxTokenLength()).isEqualTo(15);
        assertThat(appSearchConfig.getIndexMergeSize()).isEqualTo(1000);
        assertThat(appSearchConfig.getDocumentStoreNamespaceIdFingerprint()).isEqualTo(true);
        assertThat(appSearchConfig.getOptimizeRebuildIndexThreshold()).isEqualTo(0.5f);
        assertThat(appSearchConfig.getCompressionLevel()).isEqualTo(5);
        assertThat(appSearchConfig.getUseReadOnlySearch()).isEqualTo(false);
        assertThat(appSearchConfig.getUsePreMappingWithFileBackedVector()).isEqualTo(true);
        assertThat(appSearchConfig.getUsePersistentHashMap()).isEqualTo(true);
        assertThat(appSearchConfig.getMaxPageBytesLimit()).isEqualTo(1001);
        assertThat(appSearchConfig.getIntegerIndexBucketSplitThreshold()).isEqualTo(1002);
        assertThat(appSearchConfig.getLiteIndexSortAtIndexing()).isEqualTo(true);
        assertThat(appSearchConfig.getLiteIndexSortSize()).isEqualTo(1003);
    }

    @Test
    public void testCustomizedValueOverride_icingOptions() {
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_MAX_TOKEN_LENGTH, Integer.toString(15), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_INDEX_MERGE_SIZE, Integer.toString(1000), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_DOCUMENT_STORE_NAMESPACE_ID_FINGERPRINT, Boolean.toString(true), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_OPTIMIZE_REBUILD_INDEX_THRESHOLD, Float.toString(0.5f), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_COMPRESSION_LEVEL, Integer.toString(5), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_USE_READ_ONLY_SEARCH, Boolean.toString(false), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_USE_PRE_MAPPING_WITH_FILE_BACKED_VECTOR, Boolean.toString(true), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_USE_PERSISTENT_HASHMAP, Boolean.toString(true), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_MAX_PAGE_BYTES_LIMIT, Integer.toString(1001), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_INTEGER_INDEX_BUCKET_SPLIT_THRESHOLD, Integer.toString(1002), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_LITE_INDEX_SORT_AT_INDEXING, Boolean.toString(true), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_LITE_INDEX_SORT_SIZE, Integer.toString(1003), false);

        FrameworkAppSearchConfig appSearchConfig =
            FrameworkAppSearchConfigImpl.create(DIRECT_EXECUTOR);

        // Override
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_MAX_TOKEN_LENGTH, Integer.toString(25), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_INDEX_MERGE_SIZE, Integer.toString(2000), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_DOCUMENT_STORE_NAMESPACE_ID_FINGERPRINT, Boolean.toString(false), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_OPTIMIZE_REBUILD_INDEX_THRESHOLD, Float.toString(0.9f), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_COMPRESSION_LEVEL, Integer.toString(9), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_USE_READ_ONLY_SEARCH, Boolean.toString(true), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_USE_PRE_MAPPING_WITH_FILE_BACKED_VECTOR, Boolean.toString(false), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_USE_PERSISTENT_HASHMAP, Boolean.toString(false), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_MAX_PAGE_BYTES_LIMIT, Integer.toString(1002), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_INTEGER_INDEX_BUCKET_SPLIT_THRESHOLD, Integer.toString(1003), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_LITE_INDEX_SORT_AT_INDEXING, Boolean.toString(false), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_ICING_LITE_INDEX_SORT_SIZE, Integer.toString(1004), false);

        assertThat(appSearchConfig.getMaxTokenLength()).isEqualTo(25);
        assertThat(appSearchConfig.getIndexMergeSize()).isEqualTo(2000);
        assertThat(appSearchConfig.getDocumentStoreNamespaceIdFingerprint()).isEqualTo(false);
        assertThat(appSearchConfig.getOptimizeRebuildIndexThreshold()).isEqualTo(0.9f);
        assertThat(appSearchConfig.getCompressionLevel()).isEqualTo(9);
        assertThat(appSearchConfig.getUseReadOnlySearch()).isEqualTo(true);
        assertThat(appSearchConfig.getUsePreMappingWithFileBackedVector()).isEqualTo(false);
        assertThat(appSearchConfig.getUsePersistentHashMap()).isEqualTo(false);
        assertThat(appSearchConfig.getMaxPageBytesLimit()).isEqualTo(1002);
        assertThat(appSearchConfig.getIntegerIndexBucketSplitThreshold()).isEqualTo(1003);
        assertThat(appSearchConfig.getLiteIndexSortAtIndexing()).isEqualTo(false);
        assertThat(appSearchConfig.getLiteIndexSortSize()).isEqualTo(1004);
    }

    @Test
    public void testCustomizedValueOverride_rateLimitConfig() {
        FrameworkAppSearchConfig appSearchConfig =
            FrameworkAppSearchConfigImpl.create(DIRECT_EXECUTOR);
        assertThat(appSearchConfig.getCachedRateLimitEnabled()).isEqualTo(
                DEFAULT_RATE_LIMIT_ENABLED);
        AppSearchRateLimitConfig rateLimitConfig = appSearchConfig.getCachedRateLimitConfig();
        assertThat(rateLimitConfig.getTaskQueueTotalCapacity()).isEqualTo(
                DEFAULT_RATE_LIMIT_TASK_QUEUE_TOTAL_CAPACITY);
        assertThat(rateLimitConfig.getTaskQueuePerPackageCapacity()).isEqualTo(
                (int) (DEFAULT_RATE_LIMIT_TASK_QUEUE_PER_PACKAGE_CAPACITY_PERCENTAGE
                        * DEFAULT_RATE_LIMIT_TASK_QUEUE_TOTAL_CAPACITY));

        // Don't update rateLimitConfig when rateLimitEnabled=false.
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_ENABLED, Boolean.toString(false), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_TASK_QUEUE_TOTAL_CAPACITY, Integer.toString(12345), false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_TASK_QUEUE_PER_PACKAGE_CAPACITY_PERCENTAGE,
                Float.toString(0.78f),
                false);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_API_COSTS,
                "localPutDocuments:5;localGetDocuments:11;localSetSchema:99",
                false);

        assertThat(appSearchConfig.getCachedRateLimitEnabled()).isFalse();
        // RateLimitConfig still retains original value
        rateLimitConfig = appSearchConfig.getCachedRateLimitConfig();
        assertThat(rateLimitConfig.getTaskQueueTotalCapacity()).isEqualTo(
                DEFAULT_RATE_LIMIT_TASK_QUEUE_TOTAL_CAPACITY);
        assertThat(rateLimitConfig.getTaskQueuePerPackageCapacity()).isEqualTo(
                (int) (DEFAULT_RATE_LIMIT_TASK_QUEUE_PER_PACKAGE_CAPACITY_PERCENTAGE
                        * DEFAULT_RATE_LIMIT_TASK_QUEUE_TOTAL_CAPACITY));

        // RateLimitConfig should update once rate limiting is enabled
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                KEY_RATE_LIMIT_ENABLED, Boolean.toString(true), false);

        assertThat(appSearchConfig.getCachedRateLimitEnabled()).isTrue();
        rateLimitConfig = appSearchConfig.getCachedRateLimitConfig();
        assertThat(rateLimitConfig.getTaskQueueTotalCapacity()).isEqualTo(12345);
        assertThat(rateLimitConfig.getTaskQueuePerPackageCapacity()).isEqualTo(
                (int) (12345 * 0.78));
        assertThat(rateLimitConfig.getApiCost(CallStats.CALL_TYPE_PUT_DOCUMENTS)).isEqualTo(5);
        assertThat(rateLimitConfig.getApiCost(CallStats.CALL_TYPE_GET_DOCUMENTS)).isEqualTo(11);
        assertThat(rateLimitConfig.getApiCost(CallStats.CALL_TYPE_SET_SCHEMA)).isEqualTo(99);
        // Unset API costs still equal the default
        assertThat(rateLimitConfig.getApiCost(CallStats.CALL_TYPE_GET_NAMESPACES)).isEqualTo(
                AppSearchRateLimitConfig.DEFAULT_API_COST);
        assertThat(rateLimitConfig.getApiCost(CallStats.CALL_TYPE_SEARCH)).isEqualTo(
                AppSearchRateLimitConfig.DEFAULT_API_COST);
    }

    @Test
    public void testCustomizedValue_joins() {
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
            KEY_USE_NEW_QUALIFIED_ID_JOIN_INDEX, Boolean.toString(true), false);

        FrameworkAppSearchConfig appSearchConfig =
            FrameworkAppSearchConfigImpl.create(DIRECT_EXECUTOR);

        assertThat(appSearchConfig.getUseNewQualifiedIdJoinIndex()).isEqualTo(true);
    }

    @Test
    public void testCustomizedValueOverride_joins() {
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
            KEY_USE_NEW_QUALIFIED_ID_JOIN_INDEX, Boolean.toString(true), false);

        FrameworkAppSearchConfig appSearchConfig =
            FrameworkAppSearchConfigImpl.create(DIRECT_EXECUTOR);

        // Override
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
            KEY_USE_NEW_QUALIFIED_ID_JOIN_INDEX, Boolean.toString(false), false);

        assertThat(appSearchConfig.getUseNewQualifiedIdJoinIndex()).isEqualTo(false);
    }

    @Test
    public void testNotUsable_afterClose() {
        FrameworkAppSearchConfig appSearchConfig =
            FrameworkAppSearchConfigImpl.create(DIRECT_EXECUTOR);

        appSearchConfig.close();

        Assert.assertThrows("Trying to use a closed AppSearchConfig instance.",
                IllegalStateException.class,
                () -> appSearchConfig.getCachedMinTimeIntervalBetweenSamplesMillis());
        Assert.assertThrows("Trying to use a closed AppSearchConfig instance.",
                IllegalStateException.class,
                () -> appSearchConfig.getCachedSamplingIntervalDefault());
        Assert.assertThrows("Trying to use a closed AppSearchConfig instance.",
                IllegalStateException.class,
                () -> appSearchConfig.getCachedSamplingIntervalForBatchCallStats());
        Assert.assertThrows("Trying to use a closed AppSearchConfig instance.",
                IllegalStateException.class,
                () -> appSearchConfig.getCachedSamplingIntervalForPutDocumentStats());
        Assert.assertThrows("Trying to use a closed AppSearchConfig instance.",
                IllegalStateException.class,
                () -> appSearchConfig.getCachedSamplingIntervalForInitializeStats());
        Assert.assertThrows("Trying to use a closed AppSearchConfig instance.",
                IllegalStateException.class,
                () -> appSearchConfig.getCachedSamplingIntervalForSearchStats());
        Assert.assertThrows("Trying to use a closed AppSearchConfig instance.",
                IllegalStateException.class,
                () -> appSearchConfig.getCachedSamplingIntervalForGlobalSearchStats());
        Assert.assertThrows("Trying to use a closed AppSearchConfig instance.",
                IllegalStateException.class,
                () -> appSearchConfig.getCachedSamplingIntervalForOptimizeStats());
        Assert.assertThrows("Trying to use a closed AppSearchConfig instance.",
                IllegalStateException.class,
                () -> appSearchConfig.getMaxDocumentSizeBytes());
        Assert.assertThrows("Trying to use a closed AppSearchConfig instance.",
                IllegalStateException.class,
                () -> appSearchConfig.getMaxDocumentCount());
        Assert.assertThrows("Trying to use a closed AppSearchConfig instance.",
                IllegalStateException.class,
                () -> appSearchConfig.getMaxSuggestionCount());
        Assert.assertThrows("Trying to use a closed AppSearchConfig instance.",
                IllegalStateException.class,
                () -> appSearchConfig.getCachedBytesOptimizeThreshold());
        Assert.assertThrows("Trying to use a closed AppSearchConfig instance.",
                IllegalStateException.class,
                () -> appSearchConfig.getCachedTimeOptimizeThresholdMs());
        Assert.assertThrows("Trying to use a closed AppSearchConfig instance.",
                IllegalStateException.class,
                () -> appSearchConfig.getCachedDocCountOptimizeThreshold());
        Assert.assertThrows("Trying to use a closed AppSearchConfig instance.",
                IllegalStateException.class,
                () -> appSearchConfig.getCachedApiCallStatsLimit());
        Assert.assertThrows("Trying to use a closed AppSearchConfig instance.",
                IllegalStateException.class,
                () -> appSearchConfig.getCachedDenylist());
        Assert.assertThrows("Trying to use a closed AppSearchConfig instance.",
                IllegalStateException.class,
                () -> appSearchConfig.getMaxTokenLength());
        Assert.assertThrows("Trying to use a closed AppSearchConfig instance.",
                IllegalStateException.class,
                () -> appSearchConfig.getIndexMergeSize());
        Assert.assertThrows("Trying to use a closed AppSearchConfig instance.",
                IllegalStateException.class,
                () -> appSearchConfig.getDocumentStoreNamespaceIdFingerprint());
        Assert.assertThrows("Trying to use a closed AppSearchConfig instance.",
                IllegalStateException.class,
                () -> appSearchConfig.getOptimizeRebuildIndexThreshold());
        Assert.assertThrows("Trying to use a closed AppSearchConfig instance.",
                IllegalStateException.class,
                () -> appSearchConfig.getCompressionLevel());
        Assert.assertThrows("Trying to use a closed AppSearchConfig instance.",
                IllegalStateException.class,
                () -> appSearchConfig.getUseReadOnlySearch());
        Assert.assertThrows("Trying to use a closed AppSearchConfig instance.",
                IllegalStateException.class,
                () -> appSearchConfig.getUsePreMappingWithFileBackedVector());
        Assert.assertThrows("Trying to use a closed AppSearchConfig instance.",
                IllegalStateException.class,
                () -> appSearchConfig.getUsePersistentHashMap());
        Assert.assertThrows("Trying to use a closed AppSearchConfig instance.",
                IllegalStateException.class,
                () -> appSearchConfig.getMaxPageBytesLimit());
        Assert.assertThrows("Trying to use a closed AppSearchConfig instance.",
                IllegalStateException.class,
                () -> appSearchConfig.getCachedRateLimitEnabled());
        Assert.assertThrows("Trying to use a closed AppSearchConfig instance.",
                IllegalStateException.class,
                () -> appSearchConfig.getCachedRateLimitConfig());
        Assert.assertThrows("Trying to use a closed AppSearchConfig instance.",
                IllegalStateException.class,
                () -> appSearchConfig.getIntegerIndexBucketSplitThreshold());
        Assert.assertThrows("Trying to use a closed AppSearchConfig instance.",
                IllegalStateException.class,
                () -> appSearchConfig.getLiteIndexSortAtIndexing());
        Assert.assertThrows("Trying to use a closed AppSearchConfig instance.",
                IllegalStateException.class,
                () -> appSearchConfig.getLiteIndexSortSize());
        Assert.assertThrows("Trying to use a closed AppSearchConfig instance.",
                IllegalStateException.class,
                () -> appSearchConfig.getUseNewQualifiedIdJoinIndex());
    }
}
