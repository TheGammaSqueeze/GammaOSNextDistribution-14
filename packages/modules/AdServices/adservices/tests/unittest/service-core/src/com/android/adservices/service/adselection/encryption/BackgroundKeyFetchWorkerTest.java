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

package com.android.adservices.service.adselection.encryption;

import static com.android.adservices.data.adselection.EncryptionKeyConstants.EncryptionKeyType.ENCRYPTION_KEY_TYPE_AUCTION;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_AD_SELECTION_DATA_BACKGROUND_KEY_FETCH_NETWORK_CONNECT_TIMEOUT_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_AD_SELECTION_DATA_BACKGROUND_KEY_FETCH_NETWORK_READ_TIMEOUT_MS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.adselection.AuctionEncryptionKeyFixture;
import android.adservices.adselection.DBEncryptionKeyFixture;
import android.adservices.common.CommonFixture;
import android.annotation.NonNull;
import android.content.Context;
import android.net.Uri;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.LoggerFactory;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionServerDatabase;
import com.android.adservices.data.adselection.DBEncryptionKey;
import com.android.adservices.data.adselection.DBProtectedServersEncryptionConfig;
import com.android.adservices.data.adselection.EncryptionKeyDao;
import com.android.adservices.data.adselection.ProtectedServersEncryptionConfigDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.DevContext;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class BackgroundKeyFetchWorkerTest {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private final Flags mFlags =
            new BackgroundKeyFetchWorkerTest.BackgroundKeyFetchWorkerTestFlags();
    private final ExecutorService mExecutorService = Executors.newFixedThreadPool(8);

    @Rule public MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Mock private Clock mClockMock;

    @Mock private AdServicesHttpsClient mAdServicesHttpsClientMock;

    private EncryptionKeyDao mEncryptionKeyDaoSpy;
    private ProtectedServersEncryptionConfigDao mProtectedServersEncryptionConfigDaoSpy;
    private AdSelectionEncryptionKeyManager mKeyManagerSpy;

    @Mock
    private ProtectedServersEncryptionConfigManager mProtectedServersEncryptionConfigManagerMock;

    private BackgroundKeyFetchWorker mBackgroundKeyFetchWorker;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() throws Exception {
        mEncryptionKeyDaoSpy =
                Mockito.spy(
                        Room.inMemoryDatabaseBuilder(CONTEXT, AdSelectionServerDatabase.class)
                                .build()
                                .encryptionKeyDao());

        mProtectedServersEncryptionConfigDaoSpy =
                Mockito.spy(
                        Room.inMemoryDatabaseBuilder(CONTEXT, AdSelectionServerDatabase.class)
                                .build()
                                .protectedServersEncryptionConfigDao());

        mKeyManagerSpy =
                Mockito.spy(
                        new AdSelectionEncryptionKeyManager(
                                mEncryptionKeyDaoSpy,
                                mFlags,
                                mAdServicesHttpsClientMock,
                                mExecutorService));

        mBackgroundKeyFetchWorker =
                new BackgroundKeyFetchWorker(mKeyManagerSpy, mFlags, mClockMock);
        mEncryptionKeyDaoSpy.insertAllKeys(DBEncryptionKeyFixture.getKeysExpiringInTtl(1L));
        mProtectedServersEncryptionConfigDaoSpy.insertKeys(getEncryptionConfigs(0L));
    }

    @Test
    public void testBackgroundKeyFetchWorker_nullInputs_causeFailure() {
        assertThrows(
                NullPointerException.class,
                () -> new BackgroundKeyFetchWorker(
                        null, FlagsFactory.getFlagsForTest(), mClockMock));

        assertThrows(
                NullPointerException.class,
                () -> new BackgroundKeyFetchWorker(mKeyManagerSpy, null, mClockMock));

        assertThrows(
                NullPointerException.class,
                () ->
                        new BackgroundKeyFetchWorker(
                                mKeyManagerSpy, FlagsFactory.getFlagsForTest(), null));
    }

    @Test
    public void testRunBackgroundKeyFetch_longRuntime_throwsTimeoutDuringFetch() {
        class FlagsWithSmallTimeout implements Flags {
            @Override
            public long getFledgeAuctionServerBackgroundKeyFetchJobMaxRuntimeMs() {
                return 100L;
            }

            @Override
            public boolean getFledgeAuctionServerBackgroundAuctionKeyFetchEnabled() {
                return true;
            }

            @Override
            public boolean getFledgeAuctionServerBackgroundJoinKeyFetchEnabled() {
                return true;
            }
        }

        class AdSelectionEncryptionKeyManagerWithSleep extends AdSelectionEncryptionKeyManager {
            AdSelectionEncryptionKeyManagerWithSleep(
                    @NonNull EncryptionKeyDao encryptionKeyDao, @NonNull Flags flags) {
                super(encryptionKeyDao, flags, mAdServicesHttpsClientMock, mExecutorService);
            }

            @Override
            public FluentFuture<List<DBEncryptionKey>> fetchAndPersistActiveKeysOfType(
                    @AdSelectionEncryptionKey.AdSelectionEncryptionKeyType int adSelectionKeyType,
                    Instant keyExpiryInstant,
                    long timeoutMs,
                    Uri unusedCoordinatorUrl) {

                return FluentFuture.from(
                        AdServicesExecutors.getBlockingExecutor()
                                .submit(
                                        () -> {
                                            try {
                                                Thread.sleep(500L);
                                            } catch (InterruptedException e) {
                                                e.printStackTrace();
                                            }
                                            return null;
                                        }));
            }
        }

        Flags flagsWithSmallTimeout = new FlagsWithSmallTimeout();
        AdSelectionEncryptionKeyManager keyManagerWithSleep =
                new AdSelectionEncryptionKeyManagerWithSleep(
                        mEncryptionKeyDaoSpy, flagsWithSmallTimeout);
        BackgroundKeyFetchWorker backgroundKeyFetchWorkerThatTimesOut =
                new BackgroundKeyFetchWorker(
                        keyManagerWithSleep, flagsWithSmallTimeout, mClockMock);

        when(mClockMock.instant()).thenReturn(Instant.now().plusSeconds(100));

        // Time out while fetching active keys
        ExecutionException expected =
                assertThrows(
                        ExecutionException.class,
                        () -> backgroundKeyFetchWorkerThatTimesOut.runBackgroundKeyFetch().get());
        assertThat(expected.getCause()).isInstanceOf(TimeoutException.class);
    }

    @Test
    public void testRunBackgroundFetch_noExpiredKeys_nothingToFetch()
            throws ExecutionException, InterruptedException {
        mEncryptionKeyDaoSpy.deleteAllEncryptionKeys();

        when(mClockMock.instant()).thenReturn(CommonFixture.FIXED_NOW);
        mBackgroundKeyFetchWorker.runBackgroundKeyFetch().get();

        verify(mKeyManagerSpy).getExpiredAdSelectionEncryptionKeyTypes(CommonFixture.FIXED_NOW);
    }

    @Test
    @Ignore
    public void testRunBackgroundKeyFetch_keyFetchJobDisabled_nothingToFetch()
            throws ExecutionException, InterruptedException {
        class FlagsWithKeyFetchDisabled implements Flags {
            FlagsWithKeyFetchDisabled() {}

            @Override
            public boolean getFledgeAuctionServerBackgroundKeyFetchJobEnabled() {
                return false;
            }

            @Override
            public boolean getFledgeAuctionServerBackgroundAuctionKeyFetchEnabled() {
                return false;
            }

            @Override
            public boolean getFledgeAuctionServerBackgroundJoinKeyFetchEnabled() {
                return false;
            }

            @Override
            public long getFledgeAuctionServerBackgroundKeyFetchJobMaxRuntimeMs() {
                return 30000L;
            }
        }
        mBackgroundKeyFetchWorker =
                new BackgroundKeyFetchWorker(
                        mKeyManagerSpy, new FlagsWithKeyFetchDisabled(), mClockMock);

        when(mClockMock.instant()).thenReturn(CommonFixture.FIXED_NOW.plusSeconds(100));

        mBackgroundKeyFetchWorker.runBackgroundKeyFetch().get();

        verify(mKeyManagerSpy, times(1)).getExpiredAdSelectionEncryptionKeyTypes(any());
        verify(mKeyManagerSpy, never())
                .fetchAndPersistActiveKeysOfType(anyInt(), any(), anyLong(), any());
        assertThat(mKeyManagerSpy.getExpiredAdSelectionEncryptionKeyTypes(mClockMock.instant()))
                .containsExactly(
                        AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                        AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN);
    }

    @Test
    @Ignore
    public void testRunBackgroundKeyFetch_auctionKeyFetchJobDisabled_joinKeysFetched()
            throws ExecutionException, InterruptedException {
        class FlagsWithKeyFetchDisabled implements Flags {
            private FlagsWithKeyFetchDisabled() {}

            @Override
            public boolean getFledgeAuctionServerBackgroundJoinKeyFetchEnabled() {
                return true;
            }

            @Override
            public String getFledgeAuctionServerJoinKeyFetchUri() {
                return "https://foo.bar";
            }

            @Override
            public long getFledgeAuctionServerBackgroundKeyFetchJobMaxRuntimeMs() {
                return 100;
            }

            @Override
            public long getFledgeAuctionServerEncryptionKeyMaxAgeSeconds() {
                return 30000;
            }
        }
        when(mAdServicesHttpsClientMock.fetchPayload(any(Uri.class), any(DevContext.class)))
                .thenReturn(
                        Futures.immediateFuture(
                                JoinEncryptionKeyTestUtil.mockJoinKeyFetchResponse()));
        mBackgroundKeyFetchWorker =
                new BackgroundKeyFetchWorker(
                        mKeyManagerSpy, new FlagsWithKeyFetchDisabled(), mClockMock);

        when(mClockMock.instant()).thenReturn(CommonFixture.FIXED_NOW.plusSeconds(100));

        mBackgroundKeyFetchWorker.runBackgroundKeyFetch().get();

        verify(mKeyManagerSpy).getExpiredAdSelectionEncryptionKeyTypes(any());
        verify(mEncryptionKeyDaoSpy).getExpiredKeys(any());
        verify(mKeyManagerSpy).fetchAndPersistActiveKeysOfType(anyInt(), any(), anyLong(), any());
        // InsertAllKeys called twice - once to insert expired keys in setUp, second after fetching
        // active keys.
        verify(mEncryptionKeyDaoSpy, times(2)).insertAllKeys(any(List.class));
        verify(mEncryptionKeyDaoSpy).deleteExpiredRowsByType(anyInt(), any());

        List<DBEncryptionKey> joinKeys =
                mEncryptionKeyDaoSpy.getLatestExpiryNKeysOfType(
                        AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION, 3);
        assertThat(joinKeys).hasSize(1);
    }

    @Test
    @Ignore
    public void testRunBackgroundKeyFetch_joinKeyFetchJobDisabled_auctionKeysFetched()
            throws ExecutionException, InterruptedException, JSONException {
        class FlagsWithKeyFetchDisabled implements Flags {
            @Override
            public boolean getFledgeAuctionServerBackgroundAuctionKeyFetchEnabled() {
                return true;
            }

            @Override
            public boolean getFledgeAuctionServerBackgroundJoinKeyFetchEnabled() {
                return false;
            }

            @Override
            public String getFledgeAuctionServerAuctionKeyFetchUri() {
                return "https://foo.bar";
            }

            @Override
            public long getFledgeAuctionServerBackgroundKeyFetchJobMaxRuntimeMs() {
                return 100;
            }
        }
        when(mAdServicesHttpsClientMock.fetchPayload(any(Uri.class), any(DevContext.class)))
                .thenReturn(
                        Futures.immediateFuture(
                                AuctionEncryptionKeyFixture.mockAuctionKeyFetchResponse()));
        mBackgroundKeyFetchWorker =
                new BackgroundKeyFetchWorker(
                        mKeyManagerSpy, new FlagsWithKeyFetchDisabled(), mClockMock);

        when(mClockMock.instant()).thenReturn(CommonFixture.FIXED_NOW.plusSeconds(100));

        mBackgroundKeyFetchWorker.runBackgroundKeyFetch().get();

        verify(mKeyManagerSpy).getExpiredAdSelectionEncryptionKeyTypes(any());
        verify(mEncryptionKeyDaoSpy).getExpiredKeys(any());
        verify(mKeyManagerSpy).fetchAndPersistActiveKeysOfType(anyInt(), any(), anyLong(), any());
        // InsertAllKeys called twice - once to insert expired keys in setUp, second after fetching
        // active keys.
        verify(mEncryptionKeyDaoSpy, times(2)).insertAllKeys(any(List.class));
        verify(mEncryptionKeyDaoSpy).deleteExpiredRowsByType(anyInt(), any());
        assertThat(mKeyManagerSpy.getExpiredAdSelectionEncryptionKeyTypes(mClockMock.instant()))
                .containsExactly(AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN);

        List<DBEncryptionKey> auctionKeys =
                mEncryptionKeyDaoSpy.getLatestExpiryNKeysOfType(
                        AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION, 6);
        assertThat(auctionKeys).hasSize(5);
    }

    @Test
    public void testRunBackgroundKeyFetch_multiCloudEnabled_allAuctionKeysFetched()
            throws ExecutionException, InterruptedException, JSONException {
        String gcpCoordinatorUrl = "https://foo.bar/gcp";
        String awsCoordinatorUrl = "https://foo.bar/aws";
        class FlagsWithMultiCloudEnabled implements Flags {
            @Override
            public boolean getFledgeAuctionServerBackgroundAuctionKeyFetchEnabled() {
                return true;
            }

            @Override
            public boolean getFledgeAuctionServerBackgroundJoinKeyFetchEnabled() {
                return false;
            }

            @Override
            public String getFledgeAuctionServerAuctionKeyFetchUri() {
                return gcpCoordinatorUrl;
            }

            @Override
            public boolean getFledgeAuctionServerMultiCloudEnabled() {
                return true;
            }

            @Override
            public String getFledgeAuctionServerCoordinatorUrlAllowlist() {
                return gcpCoordinatorUrl + "," + awsCoordinatorUrl + "," + "https://foo.bar/azure";
            }

            @Override
            public long getFledgeAuctionServerBackgroundKeyFetchJobMaxRuntimeMs() {
                return 100;
            }
        }
        when(mAdServicesHttpsClientMock.fetchPayload(any(Uri.class), any(DevContext.class)))
                .thenReturn(
                        Futures.immediateFuture(
                                AuctionEncryptionKeyFixture.mockAuctionKeyFetchResponse()));
        Set<Integer> set = new HashSet<Integer>();
        set.add(AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION);
        when(mProtectedServersEncryptionConfigManagerMock.getExpiredAdSelectionEncryptionKeyTypes(
                        any()))
                .thenReturn(set);
        when(mProtectedServersEncryptionConfigManagerMock.fetchAndPersistActiveKeysOfType(
                        anyInt(), any(), anyLong(), any()))
                .thenReturn(
                        FluentFuture.from(
                                Futures.immediateFuture(new ArrayList<DBEncryptionKey>())));

        mBackgroundKeyFetchWorker =
                new BackgroundKeyFetchWorker(
                        mProtectedServersEncryptionConfigManagerMock,
                        new FlagsWithMultiCloudEnabled(),
                        mClockMock);

        when(mClockMock.instant()).thenReturn(CommonFixture.FIXED_NOW.plusSeconds(100));

        mBackgroundKeyFetchWorker.runBackgroundKeyFetch().get();

        verify(mProtectedServersEncryptionConfigManagerMock)
                .getExpiredAdSelectionEncryptionKeyTypes(any());

        // called once per url in allowlist
        verify(mProtectedServersEncryptionConfigManagerMock, times(3))
                .fetchAndPersistActiveKeysOfType(anyInt(), any(), anyLong(), any());
    }

    @Test
    @Ignore
    public void test_runBackgroundKeyFetchInSequence()
            throws InterruptedException, ExecutionException {
        int fetchKeyCount = 2;
        CountDownLatch completionLatch = new CountDownLatch(fetchKeyCount);

        // Count the number of times fetch and persist key is run
        AtomicInteger completionCount = new AtomicInteger(0);
        doAnswer(
                        unused -> {
                            Thread.sleep(100);
                            completionLatch.countDown();
                            completionCount.getAndIncrement();
                            return FluentFuture.from(Futures.immediateFuture(null));
                        })
                .when(mKeyManagerSpy)
                .fetchAndPersistActiveKeysOfType(anyInt(), any(), anyLong(), any());

        when(mClockMock.instant()).thenReturn(CommonFixture.FIXED_NOW.plusSeconds(100));

        CountDownLatch bgfWorkStoppedLatch = new CountDownLatch(1);
        mExecutorService.execute(
                () -> {
                    try {
                        mBackgroundKeyFetchWorker.runBackgroundKeyFetch().get();
                    } catch (Exception exception) {
                        sLogger.e(
                                exception, "Exception encountered while running background fetch");
                    } finally {
                        bgfWorkStoppedLatch.countDown();
                    }
                });

        // Wait til fetch and persist are complete, then try running background fetch again and
        // verify the second run, calls fetch and persist again.
        completionLatch.await();
        bgfWorkStoppedLatch.await();
        when(mClockMock.instant()).thenReturn(CommonFixture.FIXED_NOW.plusSeconds(1));
        mBackgroundKeyFetchWorker.runBackgroundKeyFetch().get();

        verify(mKeyManagerSpy, times(2)).getExpiredAdSelectionEncryptionKeyTypes(any());
        verify(mEncryptionKeyDaoSpy, times(2)).getExpiredKeys(any());
        verify(mKeyManagerSpy, times(4))
                .fetchAndPersistActiveKeysOfType(anyInt(), any(), anyLong(), any());
        assertThat(completionCount.get()).isEqualTo(fetchKeyCount * 2);
    }

    public static final DBProtectedServersEncryptionConfig.Builder ENCRYPTION_KEY_WITH_COORDINATOR =
            DBProtectedServersEncryptionConfig.builder()
                    .setKeyIdentifier("key_id_5")
                    .setPublicKey("public_key_5")
                    .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_AUCTION)
                    .setCoordinatorUrl("https://foo.bar/gcp")
                    .setExpiryTtlSeconds(5L);

    private static List<DBProtectedServersEncryptionConfig> getEncryptionConfigs(
            long expiryTtlSeconds) {
        return ImmutableList.of(
                ENCRYPTION_KEY_WITH_COORDINATOR.setExpiryTtlSeconds(expiryTtlSeconds).build());
    }

    static class BackgroundKeyFetchWorkerTestFlags implements Flags {
        public static final String AUCTION_KEY_FETCH_URI = "https://foo.auction";
        public static final String JOIN_KEY_FETCH_URI = "https://foo.join";

        BackgroundKeyFetchWorkerTestFlags() {}

        @Override
        public boolean getFledgeAuctionServerBackgroundKeyFetchJobEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeAuctionServerBackgroundAuctionKeyFetchEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeAuctionServerBackgroundJoinKeyFetchEnabled() {
            return true;
        }

        @Override
        public int getFledgeAuctionServerBackgroundKeyFetchNetworkConnectTimeoutMs() {
            return EXTENDED_AD_SELECTION_DATA_BACKGROUND_KEY_FETCH_NETWORK_CONNECT_TIMEOUT_MS;
        }

        @Override
        public int getFledgeAuctionServerBackgroundKeyFetchNetworkReadTimeoutMs() {
            return EXTENDED_AD_SELECTION_DATA_BACKGROUND_KEY_FETCH_NETWORK_READ_TIMEOUT_MS;
        }

        @Override
        public int getFledgeAuctionServerBackgroundKeyFetchMaxResponseSizeB() {
            return 100;
        }

        @Override
        public long getFledgeAuctionServerBackgroundKeyFetchJobMaxRuntimeMs() {
            return 500;
        }

        @Override
        public long getFledgeAuctionServerBackgroundKeyFetchJobPeriodMs() {
            return TimeUnit.SECONDS.toMillis(1);
        }

        @Override
        public long getFledgeAuctionServerBackgroundKeyFetchJobFlexMs() {
            return 10;
        }

        @Override
        public String getFledgeAuctionServerAuctionKeyFetchUri() {
            return AUCTION_KEY_FETCH_URI;
        }

        @Override
        public String getFledgeAuctionServerJoinKeyFetchUri() {
            return JOIN_KEY_FETCH_URI;
        }
    }
}
