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

package com.android.adservices.service.common.cache;

import android.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.profiling.Tracing;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableMap;
import com.google.common.net.HttpHeaders;

import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * An instance of {@link HttpCache} that is responsible for caching network requests corresponding
 * to Fledge web requests
 */
public class FledgeHttpCache implements HttpCache {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private final CacheEntryDao mCacheEntryDao;
    private final ExecutorService mExecutorService;
    private final long mMaxAgeSeconds;
    private final long mMaxEntriesCount;
    private List<CacheObserver> mCacheObservers;

    @VisibleForTesting static final String PROPERTY_NO_CACHE = "no-cache";
    @VisibleForTesting static final String PROPERTY_NO_STORE = "no-store";
    @VisibleForTesting static final String PROPERTY_MAX_AGE = "max-age";
    @VisibleForTesting static final String PROPERTY_MAX_AGE_SEPARATOR = "=";

    public FledgeHttpCache(
            @NonNull CacheEntryDao cacheEntryDao, long maxAgeSeconds, long maxEntriesCount) {
        this(
                cacheEntryDao,
                AdServicesExecutors.getBackgroundExecutor(),
                maxAgeSeconds,
                maxEntriesCount);
    }

    @VisibleForTesting
    FledgeHttpCache(
            @NonNull CacheEntryDao cacheEntryDao,
            @NonNull ExecutorService executorService,
            long maxAgeSeconds,
            long maxEntriesCount) {
        mCacheEntryDao = cacheEntryDao;
        mExecutorService = executorService;
        mMaxAgeSeconds = maxAgeSeconds;
        mMaxEntriesCount = maxEntriesCount;
        mCacheObservers = new ArrayList<>();
    }

    /**
     * Retrieves an entry from the cache
     *
     * @param url primary key, the link for which this data would have been cached
     * @return the cached instance of {@link DBCacheEntry}
     */
    @Override
    public DBCacheEntry get(URL url) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.CACHE_GET);
        incrementRequestCount();
        DBCacheEntry entry = mCacheEntryDao.getCacheEntry(url.toString(), Instant.now());
        if (entry != null) {
            sLogger.v("Returning Cached results for Url: %s", url.toString());
            incrementHitCount();
        }
        notifyObservers(CacheEventType.GET);
        Tracing.endAsyncSection(Tracing.CACHE_GET, traceCookie);
        return entry;
    }

    /**
     * Puts an entry in the cache
     *
     * @param url The primary key corresponding to which data is cached
     * @param body The response for the url for which this data was originally fetched
     * @param requestPropertiesMap associated with the original url connection
     */
    @Override
    public void put(
            URL url,
            String body,
            Map<String, List<String>> requestPropertiesMap,
            Map<String, List<String>> responseHeaders) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.CACHE_PUT);
        List<String> cacheProperties = new ArrayList<>();
        List<String> requestCacheProperties = requestPropertiesMap.get(HttpHeaders.CACHE_CONTROL);
        if (requestCacheProperties != null && !requestCacheProperties.isEmpty()) {
            cacheProperties.addAll(requestCacheProperties);
        }
        List<String> responseCacheProperties = responseHeaders.get(HttpHeaders.CACHE_CONTROL);
        if (responseCacheProperties != null && !responseCacheProperties.isEmpty()) {
            cacheProperties.addAll(responseCacheProperties);
        }
        if ((cacheProperties != null)
                && (cacheProperties.contains(PROPERTY_NO_CACHE)
                        || cacheProperties.contains(PROPERTY_NO_STORE))) {
            return;
        }
        long requestMaxAge = getRequestMaxAgeSeconds(cacheProperties);
        sLogger.v("Caching results for Url: %s", url.toString());
        DBCacheEntry entry =
                DBCacheEntry.builder()
                        .setUrl(url.toString())
                        .setResponseBody(body)
                        .setCreationTimestamp(Instant.now())
                        .setMaxAgeSeconds(Math.min(requestMaxAge, mMaxAgeSeconds))
                        .setResponseHeaders(
                                ImmutableMap.<String, List<String>>builder()
                                        .putAll(responseHeaders.entrySet())
                                        .build())
                        .build();
        mCacheEntryDao.persistCacheEntry(entry);
        notifyObservers(CacheEventType.PUT);
        Tracing.endAsyncSection(Tracing.CACHE_PUT, traceCookie);
    }

    @Override
    public long getCachedEntriesCount() {
        return mCacheEntryDao.getDBEntriesCount();
    }

    /** @return No of requests that were served directly from cache, saving a network call */
    // TODO(b/259751299) Support hit and request count in cache
    @Override
    public long getHitCount() {
        return 0;
    }

    private void incrementHitCount() {
        // TODO(b/259751299) No - op
    }

    /** @return no of get requests received by the cache */
    @Override
    public long getRequestCount() {
        return 0;
    }

    private void incrementRequestCount() {
        // TODO(b/259751299) No - op
    }

    /** Deletes all entries from the cache */
    @Override
    public void delete() {
        // TODO(b/259751299) also clear hit and request counts
        mCacheEntryDao.deleteAll();
        notifyObservers(CacheEventType.DELETE);
    }

    /**
     * Does two things, in the following order:
     *
     * <ul>
     *   <li>Deletes stale entries from the cache.
     *   <li>Prunes the cache to bound it within max permissible size of entries.
     * </ul>
     */
    @Override
    public void cleanUp() {
        mCacheEntryDao.deleteStaleRows(mMaxAgeSeconds, Instant.now());
        prune();
        notifyObservers(CacheEventType.CLEANUP);
    }

    @Override
    public void addObserver(CacheObserver observer) {
        mCacheObservers.add(observer);
    }

    @Override
    public void notifyObservers(CacheEventType cacheEvent) {
        mCacheObservers.parallelStream().forEach(o -> o.update(cacheEvent));
    }

    private float getHitRate() {
        long totalRequests = getRequestCount();
        if (totalRequests == 0) {
            return 0;
        }
        return getHitCount() / totalRequests;
    }

    @VisibleForTesting
    long getRequestMaxAgeSeconds(final List<String> cacheProperties) {
        if (cacheProperties == null) {
            return mMaxAgeSeconds;
        }
        return Math.abs(
                cacheProperties.parallelStream()
                        .filter(property -> property.startsWith(PROPERTY_MAX_AGE))
                        .map(
                                maxAgeProperty ->
                                        Long.valueOf(
                                                maxAgeProperty.substring(
                                                        maxAgeProperty.lastIndexOf(
                                                                        PROPERTY_MAX_AGE_SEPARATOR)
                                                                + 1)))
                        .findFirst()
                        .orElse(mMaxAgeSeconds));
    }

    private void prune() {
        mCacheEntryDao.prune(mMaxEntriesCount);
    }

    /** A type of {@link CacheObserver} that helps observer the {@link FledgeHttpCache}'s events */
    public static class HttpCacheObserver implements CacheObserver {
        @Override
        public void update(CacheEventType cacheEvent) {
            sLogger.v("Fledge Cache event completed: %s", cacheEvent);
        }
    }
}
