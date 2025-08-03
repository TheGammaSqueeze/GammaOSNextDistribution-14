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

package com.android.federatedcompute.services.encryption;

import static com.android.federatedcompute.services.data.FederatedComputeEncryptionKey.KEY_TYPE_ENCRYPTION;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.federatedcompute.services.common.Clock;
import com.android.federatedcompute.services.common.FederatedComputeExecutors;
import com.android.federatedcompute.services.common.Flags;
import com.android.federatedcompute.services.common.MonotonicClock;
import com.android.federatedcompute.services.data.FederatedComputeDbHelper;
import com.android.federatedcompute.services.data.FederatedComputeEncryptionKey;
import com.android.federatedcompute.services.data.FederatedComputeEncryptionKeyDao;
import com.android.federatedcompute.services.http.FederatedComputeHttpResponse;
import com.android.federatedcompute.services.http.HttpClient;

import com.google.common.util.concurrent.Futures;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class FederatedComputeKeyFetchManagerTest {

    private static final Map<String, List<String>> SAMPLE_RESPONSE_HEADER =
            Map.of(
                    "Cache-Control", List.of("public,max-age=6000"),
                    "Age", List.of("1"),
                    "Content-Type", List.of("json"));

    private static final String SAMPLE_RESPONSE_PAYLOAD =
            """
{ "keys": [{ "id": "0cc9b4c9-08bd", "key": "BQo+c1Tw6TaQ+VH/b+9PegZOjHuKAFkl8QdmS0IjRj8" """
                    + "} ] }";

    private FederatedComputeEncryptionKeyManager mFederatedComputeEncryptionKeyManager;

    @Mock private HttpClient mMockHttpClient;

    private FederatedComputeEncryptionKeyDao mEncryptionKeyDao;

    private Context mContext;

    private Clock mClock;

    private Flags mMockFlags;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = ApplicationProvider.getApplicationContext();
        mClock = MonotonicClock.getInstance();
        mEncryptionKeyDao = FederatedComputeEncryptionKeyDao.getInstanceForTest(mContext);
        mMockFlags = Mockito.mock(Flags.class);
        mFederatedComputeEncryptionKeyManager =
                new FederatedComputeEncryptionKeyManager(
                        mClock,
                        mEncryptionKeyDao,
                        mMockFlags,
                        mMockHttpClient,
                        FederatedComputeExecutors.getBackgroundExecutor());
        String overrideUrl = "https://real-coordinator/v1alpha/publicKeys";
        doReturn(overrideUrl).when(mMockFlags).getEncryptionKeyFetchUrl();
    }

    @After
    public void teadDown() {
        FederatedComputeDbHelper dbHelper = FederatedComputeDbHelper.getInstanceForTest(mContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
    }

    @Test
    public void testGetTTL_fullInfo() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Cache-Control", List.of("public,max-age=3600"));
        headers.put("Age", List.of("8"));

        long ttl = FederatedComputeEncryptionKeyManager.getTTL(headers);

        assertThat(ttl).isEqualTo(3600 - 8);
    }

    @Test
    public void testGetTTL_noCache() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Age", List.of("8"));

        long ttl = FederatedComputeEncryptionKeyManager.getTTL(headers);

        assertThat(ttl).isEqualTo(0);
    }

    @Test
    public void testGetTTL_noAge() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Cache-Control", List.of("public,max-age=3600"));

        long ttl = FederatedComputeEncryptionKeyManager.getTTL(headers);

        assertThat(ttl).isEqualTo(3600);
    }

    @Test
    public void testGetTTL_empty() {
        Map<String, List<String>> headers = Collections.EMPTY_MAP;

        long ttl = FederatedComputeEncryptionKeyManager.getTTL(headers);

        assertThat(ttl).isEqualTo(0);
    }

    @Test
    public void testGetTTL_failedParse() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Cache-Control", List.of("public,max-age==3600"));
        headers.put("Age", List.of("8"));

        long ttl = FederatedComputeEncryptionKeyManager.getTTL(headers);

        assertThat(ttl).isEqualTo(0);
    }

    @Test
    public void testFetchAndPersistActiveKeys_scheduled_success() throws Exception {
        doReturn(
                        Futures.immediateFuture(
                                new FederatedComputeHttpResponse.Builder()
                                        .setHeaders(SAMPLE_RESPONSE_HEADER)
                                        .setPayload(SAMPLE_RESPONSE_PAYLOAD.getBytes())
                                        .setStatusCode(200)
                                        .build()))
                .when(mMockHttpClient)
                .performRequestAsyncWithRetry(any());

        List<FederatedComputeEncryptionKey> keys =
                mFederatedComputeEncryptionKeyManager
                        .fetchAndPersistActiveKeys(KEY_TYPE_ENCRYPTION, /* isScheduledJob= */ true)
                        .get();

        assertThat(keys.size()).isGreaterThan(0);
    }

    @Test
    public void testFetchAndPersistActiveKeys_nonScheduled_success() throws Exception {
        doReturn(
                        Futures.immediateFuture(
                                new FederatedComputeHttpResponse.Builder()
                                        .setHeaders(SAMPLE_RESPONSE_HEADER)
                                        .setPayload(SAMPLE_RESPONSE_PAYLOAD.getBytes())
                                        .setStatusCode(200)
                                        .build()))
                .when(mMockHttpClient)
                .performRequestAsyncWithRetry(any());

        List<FederatedComputeEncryptionKey> keys =
                mFederatedComputeEncryptionKeyManager
                        .fetchAndPersistActiveKeys(KEY_TYPE_ENCRYPTION, /* isScheduledJob= */ false)
                        .get();

        assertThat(keys.size()).isGreaterThan(0);
    }

    @Test
    public void testFetchAndPersistActiveKeys_EmptyUrl_throws() {
        doReturn("").when(mMockFlags).getEncryptionKeyFetchUrl();
        assertThrows(
                ExecutionException.class,
                () ->
                        mFederatedComputeEncryptionKeyManager
                                .fetchAndPersistActiveKeys(
                                        KEY_TYPE_ENCRYPTION, /* isScheduledJob= */ true)
                                .get());
    }

    @Test
    public void testFetchAndPersistActiveKeys_NullUrl_throws() {
        doReturn(null).when(mMockFlags).getEncryptionKeyFetchUrl();
        assertThrows(
                ExecutionException.class,
                () ->
                        mFederatedComputeEncryptionKeyManager
                                .fetchAndPersistActiveKeys(
                                        KEY_TYPE_ENCRYPTION, /* isScheduledJob= */ true)
                                .get());
    }

    @Test
    public void testFetchAndPersistActiveKeys_InvalidUrl_throws() {
        doReturn("1").when(mMockFlags).getEncryptionKeyFetchUrl();
        assertThrows(
                ExecutionException.class,
                () ->
                        mFederatedComputeEncryptionKeyManager
                                .fetchAndPersistActiveKeys(
                                        KEY_TYPE_ENCRYPTION, /* isScheduledJob= */ true)
                                .get());
    }

    @Test
    public void testFetchAndPersistActiveKeys_scheduled_throws() {
        doReturn(
                        Futures.immediateFailedFuture(
                                new ExecutionException(
                                        "fetchAndPersistActiveKeys keys failed.",
                                        new IllegalStateException("http 404"))))
                .when(mMockHttpClient)
                .performRequestAsyncWithRetry(any());

        assertThrows(
                ExecutionException.class,
                () ->
                        mFederatedComputeEncryptionKeyManager
                                .fetchAndPersistActiveKeys(
                                        KEY_TYPE_ENCRYPTION, /* isScheduledJob= */ true)
                                .get());
    }

    @Test
    public void testFetchAndPersistActiveKeys_nonScheduled_throws() {
        doReturn(
                        Futures.immediateFailedFuture(
                                new ExecutionException(
                                        "fetchAndPersistActiveKeys keys failed.",
                                        new IllegalStateException("http 404"))))
                .when(mMockHttpClient)
                .performRequestAsyncWithRetry(any());

        assertThrows(
                ExecutionException.class,
                () ->
                        mFederatedComputeEncryptionKeyManager
                                .fetchAndPersistActiveKeys(
                                        KEY_TYPE_ENCRYPTION, /* isScheduledJob= */ false)
                                .get());
    }

    @Test
    public void testFetchAndPersistActiveKeys_scheduledNoDeletion() throws Exception {
        doReturn(
                        Futures.immediateFuture(
                                new FederatedComputeHttpResponse.Builder()
                                        .setHeaders(SAMPLE_RESPONSE_HEADER)
                                        .setPayload(SAMPLE_RESPONSE_PAYLOAD.getBytes())
                                        .setStatusCode(200)
                                        .build()))
                .when(mMockHttpClient)
                .performRequestAsyncWithRetry(any());

        mFederatedComputeEncryptionKeyManager
                .fetchAndPersistActiveKeys(KEY_TYPE_ENCRYPTION, /* isScheduledJob= */ true)
                .get();
        List<FederatedComputeEncryptionKey> keys =
                mEncryptionKeyDao.readFederatedComputeEncryptionKeysFromDatabase(
                        ""
                        /* selection= */ ,
                        new String[0]
                        /* selectionArgs= */ ,
                        ""
                        /* orderBy= */ ,
                        -1
                        /* count= */);

        assertThat(keys.size()).isEqualTo(1);
        assertThat(
                        keys.stream()
                                .map(FederatedComputeEncryptionKey::getKeyIdentifier)
                                .collect(Collectors.toList()))
                .containsAtLeastElementsIn(List.of("0cc9b4c9-08bd"));
    }

    @Test
    public void testFetchAndPersistActiveKeys_nonScheduledNoDeletion() throws Exception {
        doReturn(
                        Futures.immediateFuture(
                                new FederatedComputeHttpResponse.Builder()
                                        .setHeaders(SAMPLE_RESPONSE_HEADER)
                                        .setPayload(SAMPLE_RESPONSE_PAYLOAD.getBytes())
                                        .setStatusCode(200)
                                        .build()))
                .when(mMockHttpClient)
                .performRequestAsyncWithRetry(any());

        mFederatedComputeEncryptionKeyManager
                .fetchAndPersistActiveKeys(KEY_TYPE_ENCRYPTION, /* isScheduledJob= */ false)
                .get();
        List<FederatedComputeEncryptionKey> keys =
                mEncryptionKeyDao.readFederatedComputeEncryptionKeysFromDatabase(
                        ""
                        /* selection= */ ,
                        new String[0]
                        /* selectionArgs= */ ,
                        ""
                        /* orderBy= */ ,
                        -1
                        /* count= */);

        assertThat(keys.size()).isEqualTo(1);
        assertThat(
                        keys.stream()
                                .map(FederatedComputeEncryptionKey::getKeyIdentifier)
                                .collect(Collectors.toList()))
                .containsAtLeastElementsIn(List.of("0cc9b4c9-08bd"));
    }

    @Test
    public void testFetchAndPersistActiveKeys_scheduledWithDeletion() throws Exception {
        doReturn(
                        Futures.immediateFuture(
                                new FederatedComputeHttpResponse.Builder()
                                        .setHeaders(SAMPLE_RESPONSE_HEADER)
                                        .setPayload(SAMPLE_RESPONSE_PAYLOAD.getBytes())
                                        .setStatusCode(200)
                                        .build()))
                .when(mMockHttpClient)
                .performRequestAsyncWithRetry(any());
        long currentTime = mClock.currentTimeMillis();
        mEncryptionKeyDao.insertEncryptionKey(
                new FederatedComputeEncryptionKey.Builder()
                        .setKeyIdentifier("5161e286-63e5")
                        .setPublicKey("YuOorP14obQLqASrvqbkNxyijjcAUIDx/xeMGZOyykc")
                        .setKeyType(KEY_TYPE_ENCRYPTION)
                        .setCreationTime(currentTime)
                        .setExpiryTime(currentTime)
                        .build());

        mFederatedComputeEncryptionKeyManager
                .fetchAndPersistActiveKeys(KEY_TYPE_ENCRYPTION, /* isScheduledJob= */ true)
                .get();

        List<FederatedComputeEncryptionKey> keys =
                mEncryptionKeyDao.readFederatedComputeEncryptionKeysFromDatabase(
                        ""
                        /* selection= */ ,
                        new String[0]
                        /* selectionArgs= */ ,
                        ""
                        /* orderBy= */ ,
                        -1
                        /* count= */);

        assertThat(keys.size()).isEqualTo(1);
    }

    @Test
    public void testFetchAndPersistActiveKeys_nonScheduledWithDeletion() throws Exception {
        doReturn(
                        Futures.immediateFuture(
                                new FederatedComputeHttpResponse.Builder()
                                        .setHeaders(SAMPLE_RESPONSE_HEADER)
                                        .setPayload(SAMPLE_RESPONSE_PAYLOAD.getBytes())
                                        .setStatusCode(200)
                                        .build()))
                .when(mMockHttpClient)
                .performRequestAsyncWithRetry(any());
        long currentTime = mClock.currentTimeMillis();
        mEncryptionKeyDao.insertEncryptionKey(
                new FederatedComputeEncryptionKey.Builder()
                        .setKeyIdentifier("5161e286-63e5")
                        .setPublicKey("YuOorP14obQLqASrvqbkNxyijjcAUIDx/xeMGZOyykc")
                        .setKeyType(KEY_TYPE_ENCRYPTION)
                        .setCreationTime(currentTime)
                        .setExpiryTime(currentTime)
                        .build());

        mFederatedComputeEncryptionKeyManager
                .fetchAndPersistActiveKeys(KEY_TYPE_ENCRYPTION, /* isScheduledJob= */ false)
                .get();

        List<FederatedComputeEncryptionKey> keys =
                mEncryptionKeyDao.readFederatedComputeEncryptionKeysFromDatabase(
                        ""
                        /* selection= */ ,
                        new String[0]
                        /* selectionArgs= */ ,
                        ""
                        /* orderBy= */ ,
                        -1
                        /* count= */);

        assertThat(keys.size()).isEqualTo(2);

        List<FederatedComputeEncryptionKey> activeKeys = mEncryptionKeyDao.getLatestExpiryNKeys(2);
        assertThat(activeKeys.size()).isEqualTo(1);
    }

    @Test
    public void testGetOrFetchActiveKeys_fetch() {
        doReturn(
                        Futures.immediateFuture(
                                new FederatedComputeHttpResponse.Builder()
                                        .setHeaders(SAMPLE_RESPONSE_HEADER)
                                        .setPayload(SAMPLE_RESPONSE_PAYLOAD.getBytes())
                                        .setStatusCode(200)
                                        .build()))
                .when(mMockHttpClient)
                .performRequestAsyncWithRetry(any());

        List<FederatedComputeEncryptionKey> keys =
                mFederatedComputeEncryptionKeyManager.getOrFetchActiveKeys(
                        KEY_TYPE_ENCRYPTION, /* keyCount= */ 2);

        verify(mMockHttpClient, times(1)).performRequestAsyncWithRetry(any());
        assertThat(keys.size()).isEqualTo(1);
    }

    @Test
    public void testGetOrFetchActiveKeys_noFetch() {
        long currentTime = mClock.currentTimeMillis();
        mEncryptionKeyDao.insertEncryptionKey(
                new FederatedComputeEncryptionKey.Builder()
                        .setKeyIdentifier("5161e286-63e5")
                        .setPublicKey("YuOorP14obQLqASrvqbkNxyijjcAUIDx/xeMGZOyykc")
                        .setKeyType(KEY_TYPE_ENCRYPTION)
                        .setCreationTime(currentTime)
                        .setExpiryTime(currentTime + 5000L)
                        .build());
        doReturn(
                        Futures.immediateFuture(
                                new FederatedComputeHttpResponse.Builder()
                                        .setHeaders(SAMPLE_RESPONSE_HEADER)
                                        .setPayload(SAMPLE_RESPONSE_PAYLOAD.getBytes())
                                        .setStatusCode(200)
                                        .build()))
                .when(mMockHttpClient)
                .performRequestAsyncWithRetry(any());

        List<FederatedComputeEncryptionKey> keys =
                mFederatedComputeEncryptionKeyManager.getOrFetchActiveKeys(
                        KEY_TYPE_ENCRYPTION, /* keyCount= */ 2);

        verify(mMockHttpClient, never()).performRequestAsyncWithRetry(any());
        assertThat(keys.size()).isEqualTo(1);
    }

    @Test
    public void testGetOrFetchActiveKeys_failure() {
        doReturn(Futures.immediateFailedFuture(new InterruptedException()))
                .when(mMockHttpClient)
                .performRequestAsyncWithRetry(any());

        List<FederatedComputeEncryptionKey> keys =
                mFederatedComputeEncryptionKeyManager.getOrFetchActiveKeys(
                        KEY_TYPE_ENCRYPTION, /* keyCount= */ 2);

        assertThat(keys.size()).isEqualTo(0);
        verify(mMockHttpClient, times(1)).performRequestAsyncWithRetry(any());
    }
}
