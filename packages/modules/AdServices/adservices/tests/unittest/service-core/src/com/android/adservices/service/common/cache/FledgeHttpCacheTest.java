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

import static com.android.adservices.service.common.cache.FledgeHttpCache.PROPERTY_MAX_AGE;
import static com.android.adservices.service.common.cache.FledgeHttpCache.PROPERTY_MAX_AGE_SEPARATOR;
import static com.android.adservices.service.common.cache.FledgeHttpCache.PROPERTY_NO_CACHE;
import static com.android.adservices.service.common.cache.FledgeHttpCache.PROPERTY_NO_STORE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.HttpHeaders;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class FledgeHttpCacheTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final long MAX_AGE_SECONDS = 2 * 24 * 60 * 60;
    private static final long MAX_ENTRIES = 20;
    private URL mUrl;
    private String mBody;
    private long mMaxAgeSeconds;
    private DBCacheEntry mCacheEntry;
    private Flags mFlags;
    private FledgeHttpCache mCache;
    private ExecutorService mExecutorService;
    private Map<String, List<String>> mCachingPropertiesMap;
    private ImmutableMap<String, List<String>> mResponseHeadersMap;

    @Mock private CacheEntryDao mCacheEntryDaoMock;
    @Mock private HttpCache.CacheObserver mObserver;
    @Captor private ArgumentCaptor<DBCacheEntry> mCacheEntryArgumentCaptor;
    @Captor private ArgumentCaptor<Long> mCacheMaxAgeCaptor;
    @Captor private ArgumentCaptor<Long> mCacheMaxEntriesCaptor;
    @Rule public MockitoRule rule = MockitoJUnit.rule();

    @Before
    public void setup() throws MalformedURLException {
        mUrl = new URL("https://google.com");
        mBody = "This is the Google home page";
        mMaxAgeSeconds = 1000;
        mCachingPropertiesMap = new HashMap<>();
        mResponseHeadersMap =
                ImmutableMap.<String, List<String>>builder()
                        .build()
                        .of(
                                "header_1",
                                ImmutableList.of("h1_value1", "h1_value2"),
                                "header_2",
                                ImmutableList.of("h2_value1", "h2_value2"));
        mCacheEntry =
                DBCacheEntry.builder()
                        .setUrl(mUrl.toString())
                        .setResponseBody(mBody)
                        .setResponseHeaders(mResponseHeadersMap)
                        .setCreationTimestamp(Instant.now())
                        .setMaxAgeSeconds(mMaxAgeSeconds)
                        .build();
        mFlags = FlagsFactory.getFlags();
        mExecutorService = AdServicesExecutors.getBackgroundExecutor();

        mCache =
                new FledgeHttpCache(
                        mCacheEntryDaoMock, mExecutorService, MAX_AGE_SECONDS, MAX_ENTRIES);
        mCache.addObserver(mObserver);
    }

    @Test
    public void test_CacheGetEmpty_ReturnsNull() {
        assertNull(mCache.get(mUrl));
    }

    @Test
    public void test_CachePutEntry_Succeeds() {
        mCache.put(mUrl, mBody, mCachingPropertiesMap, mResponseHeadersMap);
        verify(mCacheEntryDaoMock).persistCacheEntry(mCacheEntryArgumentCaptor.capture());
        assertEquals(
                "Cached key should have been same",
                mUrl.toString(),
                mCacheEntryArgumentCaptor.getValue().getUrl());
        assertEquals(
                "Cached body should have been same",
                mBody,
                mCacheEntryArgumentCaptor.getValue().getResponseBody());
        assertEquals(
                "Cached response headers should have been the same",
                mResponseHeadersMap,
                mCacheEntryArgumentCaptor.getValue().getResponseHeaders());
        verify(mObserver).update(HttpCache.CacheEventType.PUT);
    }

    @Test
    public void test_CachePutEntryNoCache_SkipsCache() {
        List<String> skipCacheProperties = ImmutableList.of(PROPERTY_NO_CACHE);
        Map<String, List<String>> skipCacheMap =
                ImmutableMap.of(HttpHeaders.CACHE_CONTROL, skipCacheProperties);
        mCache.put(mUrl, mBody, skipCacheMap, mResponseHeadersMap);
        verify(mCacheEntryDaoMock, times(0)).persistCacheEntry(any(DBCacheEntry.class));
        verify(mObserver, never()).update(HttpCache.CacheEventType.PUT);
    }

    @Test
    public void test_CachePutEntryNoStoreCache_SkipsCache() {
        List<String> skipCacheProperties = ImmutableList.of(PROPERTY_NO_STORE);
        Map<String, List<String>> skipCacheMap =
                ImmutableMap.of(HttpHeaders.CACHE_CONTROL, skipCacheProperties);
        mCache.put(mUrl, mBody, skipCacheMap, mResponseHeadersMap);
        verify(mCacheEntryDaoMock, times(0)).persistCacheEntry(any(DBCacheEntry.class));
        verify(mObserver, never()).update(HttpCache.CacheEventType.PUT);
    }

    @Test
    public void test_CacheGetEntry_Succeeds() {
        doReturn(mCacheEntry)
                .when(mCacheEntryDaoMock)
                .getCacheEntry(eq(mUrl.toString()), any(Instant.class));
        DBCacheEntry cacheFetchedResponse = mCache.get(mUrl);
        assertEquals(
                "Response body should have been the same",
                mBody,
                cacheFetchedResponse.getResponseBody());
        verify(mObserver).update(HttpCache.CacheEventType.GET);
    }

    @Test
    public void test_CacheGetTotalEntries_Succeeds() {
        doReturn(123L).when(mCacheEntryDaoMock).getDBEntriesCount();
        assertEquals("No of entries in cache mismatch", 123L, mCache.getCachedEntriesCount());
    }

    @Test
    public void test_CacheCleanUp_Succeeds() {
        mCache.cleanUp();
        verify(mCacheEntryDaoMock)
                .deleteStaleRows(mCacheMaxAgeCaptor.capture(), any(Instant.class));
        long maxAgeValue = mCacheMaxAgeCaptor.getValue();
        assertEquals("Default max age for cache not consistent", MAX_AGE_SECONDS, maxAgeValue);

        verify(mCacheEntryDaoMock).prune(mCacheMaxEntriesCaptor.capture());
        long maxEntriesValue = mCacheMaxEntriesCaptor.getValue();
        assertEquals("Default max entries for cache not consistent", MAX_ENTRIES, maxEntriesValue);
        verify(mObserver).update(HttpCache.CacheEventType.CLEANUP);
    }

    @Test
    public void test_CacheDelete_DeletesAll() {
        mCache.delete();
        verify(mCacheEntryDaoMock).deleteAll();
        verify(mObserver).update(HttpCache.CacheEventType.DELETE);
    }

    @Test
    public void test_GetCacheRequestMaxAge_Success() {
        long expectedAgeSeconds = 60;
        ImmutableList<String> cacheProperties =
                ImmutableList.of(
                        PROPERTY_MAX_AGE + PROPERTY_MAX_AGE_SEPARATOR + expectedAgeSeconds);
        assertEquals(expectedAgeSeconds, mCache.getRequestMaxAgeSeconds(cacheProperties));
    }

    @Test
    public void test_MaxAgeUpperBounded_GlobalMaxAge() {
        long reallyLongMaxAge = MAX_AGE_SECONDS * 5;
        List<String> maxCacheAgeProperties =
                ImmutableList.of(PROPERTY_MAX_AGE + PROPERTY_MAX_AGE_SEPARATOR + reallyLongMaxAge);
        Map<String, List<String>> cachePropertiesMap =
                ImmutableMap.of(HttpHeaders.CACHE_CONTROL, maxCacheAgeProperties);
        mCache.put(mUrl, mBody, cachePropertiesMap, mResponseHeadersMap);
        verify(mCacheEntryDaoMock).persistCacheEntry(mCacheEntryArgumentCaptor.capture());
        assertEquals(
                "The max age should not have been more than default max age",
                MAX_AGE_SECONDS,
                mCacheEntryArgumentCaptor.getValue().getMaxAgeSeconds());
    }

    @Test
    public void test_MaxAgeLowerBounded_RequestMaxAge() {
        long reallySmallMaxAge = 5;
        List<String> maxCacheAgeProperties =
                ImmutableList.of(PROPERTY_MAX_AGE + PROPERTY_MAX_AGE_SEPARATOR + reallySmallMaxAge);
        Map<String, List<String>> cachePropertiesMap =
                ImmutableMap.of(HttpHeaders.CACHE_CONTROL, maxCacheAgeProperties);
        mCache.put(mUrl, mBody, cachePropertiesMap, mResponseHeadersMap);
        verify(mCacheEntryDaoMock).persistCacheEntry(mCacheEntryArgumentCaptor.capture());
        assertEquals(
                "The max age should have been set to value in the request headers",
                reallySmallMaxAge,
                mCacheEntryArgumentCaptor.getValue().getMaxAgeSeconds());
    }

    @Test
    public void test_CacheE2ESetDefaultMaxAge_GarbledMaxAge() {
        List<String> maxCacheAgeProperties = ImmutableList.of("garbled-max-age-param=2000ABC");
        Map<String, List<String>> cachePropertiesMap =
                ImmutableMap.of(HttpHeaders.CACHE_CONTROL, maxCacheAgeProperties);
        mCache.put(mUrl, mBody, cachePropertiesMap, mResponseHeadersMap);
        verify(mCacheEntryDaoMock).persistCacheEntry(mCacheEntryArgumentCaptor.capture());
        assertEquals(
                "Cached entry max age does not match default",
                MAX_AGE_SECONDS,
                mCacheEntryArgumentCaptor.getValue().getMaxAgeSeconds());
    }

    @Test
    public void test_CacheE2ESetDefaultMaxAge_MissingMaxAge() {
        mCache.put(mUrl, mBody, Collections.emptyMap(), mResponseHeadersMap);
        verify(mCacheEntryDaoMock).persistCacheEntry(mCacheEntryArgumentCaptor.capture());
        assertEquals(
                "Cache entry max age does not match default",
                MAX_AGE_SECONDS,
                mCacheEntryArgumentCaptor.getValue().getMaxAgeSeconds());
    }

    /** This test uses real Dao to check the actual cache contracts put and get */
    @Test
    public void test_CacheE2EPutAndGet_Success() {
        CacheEntryDao realDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, CacheDatabase.class)
                        .build()
                        .getCacheEntryDao();
        FledgeHttpCache cache =
                new FledgeHttpCache(realDao, mExecutorService, MAX_AGE_SECONDS, MAX_ENTRIES);
        cache.put(mUrl, mBody, mCachingPropertiesMap, mResponseHeadersMap);
        assertEquals("Cache should have persisted one entry", 1, cache.getCachedEntriesCount());
        assertEquals(
                "Cached response does not match original",
                mBody,
                cache.get(mUrl).getResponseBody());
    }

    @Test
    public void test_CacheE2EHonorsMaxAge_Success() {
        CacheEntryDao realDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, CacheDatabase.class)
                        .build()
                        .getCacheEntryDao();
        FledgeHttpCache cache =
                new FledgeHttpCache(realDao, mExecutorService, MAX_AGE_SECONDS, MAX_ENTRIES);
        long reallySmallMaxAge = 0;
        List<String> maxCacheAgeProperties =
                ImmutableList.of(PROPERTY_MAX_AGE + PROPERTY_MAX_AGE_SEPARATOR + reallySmallMaxAge);
        Map<String, List<String>> cachePropertiesMap =
                ImmutableMap.of(HttpHeaders.CACHE_CONTROL, maxCacheAgeProperties);
        cache.put(mUrl, mBody, cachePropertiesMap, mResponseHeadersMap);
        assertEquals("Cache should have persisted one entry", 1, cache.getCachedEntriesCount());
        assertNull("Entries past their max-age should not be fetched", cache.get(mUrl));
    }
}
