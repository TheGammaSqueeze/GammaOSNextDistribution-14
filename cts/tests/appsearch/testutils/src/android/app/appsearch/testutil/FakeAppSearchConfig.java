/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.os.Build;

import com.android.server.appsearch.AppSearchRateLimitConfig;
import com.android.server.appsearch.Denylist;
import com.android.server.appsearch.FrameworkAppSearchConfig;
import com.android.server.appsearch.external.localstorage.IcingOptionsConfig;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An instance of {@link FrameworkAppSearchConfig} which does not read from any flag system, but
 * simply returns the defaults for each key.
 *
 * <p>This class is thread safe.
 *
 * @hide
 */
public final class FakeAppSearchConfig implements FrameworkAppSearchConfig {
    private final AtomicBoolean mIsClosed = new AtomicBoolean();
    private static final AppSearchRateLimitConfig DEFAULT_APPSEARCH_RATE_LIMIT_CONFIG =
            AppSearchRateLimitConfig.create(
                    DEFAULT_RATE_LIMIT_TASK_QUEUE_TOTAL_CAPACITY,
                    DEFAULT_RATE_LIMIT_TASK_QUEUE_PER_PACKAGE_CAPACITY_PERCENTAGE,
                    DEFAULT_RATE_LIMIT_API_COSTS_STRING);

    @Override
    public void close() {
        mIsClosed.set(true);
    }

    @Override
    public long getCachedMinTimeIntervalBetweenSamplesMillis() {
        throwIfClosed();
        return DEFAULT_MIN_TIME_INTERVAL_BETWEEN_SAMPLES_MILLIS;
    }

    @Override
    public int getCachedSamplingIntervalDefault() {
        throwIfClosed();
        return DEFAULT_SAMPLING_INTERVAL;
    }

    @Override
    public int getCachedSamplingIntervalForBatchCallStats() {
        throwIfClosed();
        return getCachedSamplingIntervalDefault();
    }

    @Override
    public int getCachedSamplingIntervalForPutDocumentStats() {
        throwIfClosed();
        return getCachedSamplingIntervalDefault();
    }

    @Override
    public int getCachedSamplingIntervalForInitializeStats() {
        throwIfClosed();
        return getCachedSamplingIntervalDefault();
    }

    @Override
    public int getCachedSamplingIntervalForSearchStats() {
        throwIfClosed();
        return getCachedSamplingIntervalDefault();
    }

    @Override
    public int getCachedSamplingIntervalForGlobalSearchStats() {
        throwIfClosed();
        return getCachedSamplingIntervalDefault();
    }

    @Override
    public int getCachedSamplingIntervalForOptimizeStats() {
        throwIfClosed();
        return getCachedSamplingIntervalDefault();
    }

    @Override
    public int getMaxDocumentSizeBytes() {
        throwIfClosed();
        return DEFAULT_LIMIT_CONFIG_MAX_DOCUMENT_SIZE_BYTES;
    }

    @Override
    public int getMaxDocumentCount() {
        throwIfClosed();
        return DEFAULT_LIMIT_CONFIG_MAX_DOCUMENT_COUNT;
    }

    @Override
    public int getMaxSuggestionCount() {
        throwIfClosed();
        return DEFAULT_LIMIT_CONFIG_MAX_SUGGESTION_COUNT;
    }

    @Override
    public int getCachedBytesOptimizeThreshold() {
        throwIfClosed();
        return DEFAULT_BYTES_OPTIMIZE_THRESHOLD;
    }

    @Override
    public int getCachedTimeOptimizeThresholdMs() {
        throwIfClosed();
        return DEFAULT_TIME_OPTIMIZE_THRESHOLD_MILLIS;
    }

    @Override
    public int getCachedDocCountOptimizeThreshold() {
        throwIfClosed();
        return DEFAULT_DOC_COUNT_OPTIMIZE_THRESHOLD;
    }

    @Override
    public int getCachedMinTimeOptimizeThresholdMs() {
        throwIfClosed();
        return DEFAULT_MIN_TIME_OPTIMIZE_THRESHOLD_MILLIS;
    }

    @Override
    public int getCachedApiCallStatsLimit() {
        throwIfClosed();
        return DEFAULT_API_CALL_STATS_LIMIT;
    }

    @Override
    public Denylist getCachedDenylist() {
        return Denylist.EMPTY_INSTANCE;
    }

    @Override
    public int getMaxTokenLength() {
        throwIfClosed();
        return IcingOptionsConfig.DEFAULT_MAX_TOKEN_LENGTH;
    }

    @Override
    public int getIndexMergeSize() {
        throwIfClosed();
        return IcingOptionsConfig.DEFAULT_INDEX_MERGE_SIZE;
    }

    @Override
    public boolean getDocumentStoreNamespaceIdFingerprint() {
        throwIfClosed();
        return IcingOptionsConfig.DEFAULT_DOCUMENT_STORE_NAMESPACE_ID_FINGERPRINT;
    }

    @Override
    public float getOptimizeRebuildIndexThreshold() {
        throwIfClosed();
        return IcingOptionsConfig.DEFAULT_OPTIMIZE_REBUILD_INDEX_THRESHOLD;
    }

    @Override
    public int getCompressionLevel() {
        throwIfClosed();
        return IcingOptionsConfig.DEFAULT_COMPRESSION_LEVEL;
    }

    @Override
    public boolean getAllowCircularSchemaDefinitions() {
        throwIfClosed();
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
    }

    @Override
    public boolean getUseReadOnlySearch() {
        throwIfClosed();
        return DEFAULT_ICING_CONFIG_USE_READ_ONLY_SEARCH;
    }

    @Override
    public boolean getUsePreMappingWithFileBackedVector() {
        throwIfClosed();
        return IcingOptionsConfig.DEFAULT_USE_PREMAPPING_WITH_FILE_BACKED_VECTOR;
    }

    @Override
    public boolean getUsePersistentHashMap() {
        throwIfClosed();
        return IcingOptionsConfig.DEFAULT_USE_PERSISTENT_HASH_MAP;
    }

    @Override
    public int getMaxPageBytesLimit() {
        throwIfClosed();
        return IcingOptionsConfig.DEFAULT_MAX_PAGE_BYTES_LIMIT;
    }

    @Override
    public boolean getCachedRateLimitEnabled() {
        throwIfClosed();
        return DEFAULT_RATE_LIMIT_ENABLED;
    }

    @Override
    public AppSearchRateLimitConfig getCachedRateLimitConfig() {
        throwIfClosed();
        return DEFAULT_APPSEARCH_RATE_LIMIT_CONFIG;
    }

    @Override
    public int getIntegerIndexBucketSplitThreshold() {
        throwIfClosed();
        return DEFAULT_INTEGER_INDEX_BUCKET_SPLIT_THRESHOLD;
    }

    @Override
    public boolean getLiteIndexSortAtIndexing() {
        throwIfClosed();
        return DEFAULT_LITE_INDEX_SORT_AT_INDEXING;
    }

    @Override
    public int getLiteIndexSortSize() {
        throwIfClosed();
        return DEFAULT_LITE_INDEX_SORT_SIZE;
    }

    @Override
    public boolean shouldStoreParentInfoAsSyntheticProperty() {
        throwIfClosed();
        return true;
    }

    @Override
    public boolean shouldRetrieveParentInfo() {
        throwIfClosed();
        return DEFAULT_SHOULD_RETRIEVE_PARENT_INFO;
    }

    @Override
    public boolean getUseNewQualifiedIdJoinIndex() {
        throwIfClosed();
        return DEFAULT_USE_NEW_QUALIFIED_ID_JOIN_INDEX;
    }

    @Override
    public boolean getBuildPropertyExistenceMetadataHits() {
        throwIfClosed();
        return DEFAULT_BUILD_PROPERTY_EXISTENCE_METADATA_HITS;
    }

    private void throwIfClosed() {
        if (mIsClosed.get()) {
            throw new IllegalStateException(
                "Trying to use a closed FrameworkAppSearchConfig instance.");
        }
    }
}
