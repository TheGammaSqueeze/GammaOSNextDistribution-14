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
package com.android.adservices.service.measurement.aggregation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.data.measurement.DatastoreManager;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.stubbing.Answer;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.net.ssl.HttpsURLConnection;

/**
 * Unit tests for {@link AggregateEncryptionKeyFetcher}
 */
@SmallTest
public final class AggregateEncryptionKeyManagerTest {
    private static final int NUM_KEYS_REQUESTED = 5;
    private static final String AGGREGATION_COORDINATOR_ORIGIN_1 =
            "https://not-going-to-be-visited.test";
    private static final String AGGREGATION_COORDINATOR_ORIGIN_2 =
            "https://again-not-going-to-be-visited.test";
    private static final String AGGREGATION_ORIGIN_LIST =
            String.join(
                    ",",
                    List.of(AGGREGATION_COORDINATOR_ORIGIN_1, AGGREGATION_COORDINATOR_ORIGIN_2));
    private static final String AGGREGATION_COORDINATOR_ORIGIN_PATH = "test/path";

    private static final String LOCALHOST = "https://localhost";

    @Mock DatastoreManager mDatastoreManager;

    @Spy
    AggregateEncryptionKeyFetcher mFetcher =
            new AggregateEncryptionKeyFetcher(ApplicationProvider.getApplicationContext());

    @Mock HttpsURLConnection mUrlConnection;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void getAggregateEncryptionKeys_hasKeysInDatastore_doesNotCallFetcher()
            throws Exception {
        // Mock the datastore to return the expected seed key list; we are testing that the fetcher
        // is not called.
        doAnswer((Answer<Optional<List<AggregateEncryptionKey>>>)
                invocation -> Optional.of(getExpectedKeys(AGGREGATION_COORDINATOR_ORIGIN_1)))
                .when(mDatastoreManager).runInTransactionWithResult(any());
        AggregateEncryptionKeyManager aggregateEncryptionKeyManager =
                new AggregateEncryptionKeyManager(
                        mDatastoreManager,
                        mFetcher,
                        Clock.systemUTC(),
                        AGGREGATION_ORIGIN_LIST,
                        AGGREGATION_COORDINATOR_ORIGIN_PATH);
        List<AggregateEncryptionKey> providedKeys =
                aggregateEncryptionKeyManager.getAggregateEncryptionKeys(
                        Uri.parse(AGGREGATION_COORDINATOR_ORIGIN_1), NUM_KEYS_REQUESTED);
        assertTrue(
                "aggregationEncryptionKeyManager.getAggregateEncryptionKeys returned "
                        + "unexpected results:"
                        + AggregateEncryptionKeyTestUtil.prettify(providedKeys),
                AggregateEncryptionKeyTestUtil.isSuperset(
                                getExpectedKeys(AGGREGATION_COORDINATOR_ORIGIN_1), providedKeys)
                        && providedKeys.size() == NUM_KEYS_REQUESTED);
        verify(mFetcher, never()).fetch(any(), any(Uri.class), anyLong());
    }

    @Test
    public void getAggregateEncryptionKeys_fetcherFails_returnsEmptyList() throws Exception {
        // Mock the datastore to return an empty list.
        doAnswer((Answer<Optional<List<AggregateEncryptionKey>>>)
                invocation -> Optional.of(new ArrayList<>()))
                .when(mDatastoreManager).runInTransactionWithResult(any());
        // Mock the fetcher as failing.
        doReturn(mUrlConnection).when(mFetcher).openUrl(any());
        when(mUrlConnection.getResponseCode()).thenReturn(400);
        AggregateEncryptionKeyManager aggregateEncryptionKeyManager =
                new AggregateEncryptionKeyManager(
                        mDatastoreManager,
                        mFetcher,
                        Clock.systemUTC(),
                        AGGREGATION_ORIGIN_LIST,
                        AGGREGATION_COORDINATOR_ORIGIN_PATH);
        List<AggregateEncryptionKey> providedKeys =
                aggregateEncryptionKeyManager.getAggregateEncryptionKeys(
                        Uri.parse(AGGREGATION_COORDINATOR_ORIGIN_2), NUM_KEYS_REQUESTED);
        assertTrue(
                "aggregationEncryptionKeyManager.getAggregateEncryptionKeys returned "
                        + "unexpected results:"
                        + AggregateEncryptionKeyTestUtil.prettify(providedKeys),
                providedKeys.isEmpty());
    }

    @Test
    public void getAggregateEncryptionKeys_localhostCoordinator_doesNotCacheKeys()
            throws Exception {
        // Mock the datastore to return an empty list.
        doAnswer((Answer<Optional<List<AggregateEncryptionKey>>>)
                invocation -> Optional.of(new ArrayList<>()))
                .when(mDatastoreManager).runInTransactionWithResult(any());
        List<AggregateEncryptionKey> expectedKeys = getExpectedKeys(LOCALHOST);
        // Mock the fetcher returning keys.
        doReturn(Optional.of(expectedKeys)).when(mFetcher).fetch(any(), any(Uri.class), anyLong());
        AggregateEncryptionKeyManager aggregateEncryptionKeyManager =
                new AggregateEncryptionKeyManager(
                        mDatastoreManager,
                        mFetcher,
                        Clock.systemUTC(),
                        LOCALHOST,
                        AGGREGATION_COORDINATOR_ORIGIN_PATH);
        List<AggregateEncryptionKey> providedKeys =
                aggregateEncryptionKeyManager.getAggregateEncryptionKeys(
                        Uri.parse(LOCALHOST), NUM_KEYS_REQUESTED);
        assertTrue(
                "aggregationEncryptionKeyManager.getAggregateEncryptionKeys returned "
                        + "unexpected results:"
                        + AggregateEncryptionKeyTestUtil.prettify(providedKeys),
                AggregateEncryptionKeyTestUtil.isSuperset(expectedKeys, providedKeys)
                        && providedKeys.size() == NUM_KEYS_REQUESTED);
        // Datastore is called once to delete expired encryption keys.
        verify(mDatastoreManager, times(1)).runInTransaction(any());
    }

    @Test
    public void createURL() {
        // Slash in origin, slash in path
        assertEquals(
                Uri.parse("https://a.test//test/qwe"),
                AggregateEncryptionKeyManager.createURL(Uri.parse("https://a.test/"), "/test/qwe"));

        // No slash in origin, slash in path
        assertEquals(
                Uri.parse("https://a.test//test/qwe"),
                AggregateEncryptionKeyManager.createURL(Uri.parse("https://a.test"), "/test/qwe"));

        // No slash in origin, no slash in path
        assertEquals(
                Uri.parse("https://a.test/test/qwe"),
                AggregateEncryptionKeyManager.createURL(Uri.parse("https://a.test"), "test/qwe"));

        // Slash in origin, no slash in path
        assertEquals(
                Uri.parse("https://a.test/test/qwe"),
                AggregateEncryptionKeyManager.createURL(Uri.parse("https://a.test/"), "test/qwe"));
    }

    private static List<AggregateEncryptionKey> getExpectedKeys(String origin) {
        List<AggregateEncryptionKey> result = new ArrayList<>();
        result.add(
                new AggregateEncryptionKey.Builder()
                        .setKeyId(AggregateEncryptionKeyTestUtil.DEFAULT_KEY_1.KEY_ID)
                        .setPublicKey(AggregateEncryptionKeyTestUtil.DEFAULT_KEY_1.PUBLIC_KEY)
                        .setExpiry(AggregateEncryptionKeyTestUtil.DEFAULT_EXPIRY)
                        .setAggregationCoordinatorOrigin(Uri.parse(origin))
                        .build());
        result.add(
                new AggregateEncryptionKey.Builder()
                        .setKeyId(AggregateEncryptionKeyTestUtil.DEFAULT_KEY_2.KEY_ID)
                        .setPublicKey(AggregateEncryptionKeyTestUtil.DEFAULT_KEY_2.PUBLIC_KEY)
                        .setExpiry(AggregateEncryptionKeyTestUtil.DEFAULT_EXPIRY)
                        .setAggregationCoordinatorOrigin(Uri.parse(origin))
                        .build());
        return result;
    }
}
