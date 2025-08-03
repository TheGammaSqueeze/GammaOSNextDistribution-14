/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static android.adservices.adselection.AuctionEncryptionKeyFixture.AUCTION_KEY_1;
import static android.adservices.adselection.AuctionEncryptionKeyFixture.AUCTION_KEY_2;
import static android.adservices.adselection.AuctionEncryptionKeyFixture.COORDINATOR_URL_AUCTION;
import static android.adservices.adselection.AuctionEncryptionKeyFixture.COORDINATOR_URL_AUCTION_ORIGIN;
import static android.adservices.adselection.AuctionEncryptionKeyFixture.ENCRYPTION_KEY_AUCTION_WITH_COORDINATOR;

import static com.android.adservices.service.adselection.encryption.JoinEncryptionKeyTestUtil.COORDINATOR_URL_JOIN;
import static com.android.adservices.service.adselection.encryption.JoinEncryptionKeyTestUtil.ENCRYPTION_KEY_JOIN_WITH_COORDINATOR;
import static com.android.adservices.service.common.httpclient.AdServicesHttpUtil.REQUEST_PROPERTIES_PROTOBUF_CONTENT_TYPE;
import static com.android.adservices.service.common.httpclient.AdServicesHttpUtil.RESPONSE_PROPERTIES_CONTENT_TYPE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.adservices.adselection.AuctionEncryptionKeyFixture;
import android.net.Uri;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionServerDatabase;
import com.android.adservices.data.adselection.DBEncryptionKey;
import com.android.adservices.data.adselection.DBProtectedServersEncryptionConfig;
import com.android.adservices.data.adselection.ProtectedServersEncryptionConfigDao;
import com.android.adservices.ohttp.ObliviousHttpKeyConfig;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientRequest;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.DevContext;

import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.Futures;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class ProtectedServersEncryptionConfigManagerTest {

    private static final Long EXPIRY_TTL_1SEC = 1L;

    private static final int TIMEOUT_MS = 500;

    private static final String AUCTION_KEY_FETCH_DEFAULT_URI = "https://foo.bar/auctionkey";
    private static final String JOIN_KEY_FETCH_DEFAULT_URI = "https://foo.bar/joinkey";
    private static final DevContext DEV_CONTEXT_DISABLED = DevContext.createForDevOptionsDisabled();

    @Rule public final MockitoRule mockito = MockitoJUnit.rule();
    @Mock private AdServicesHttpsClient mMockHttpClient;
    @Spy private Clock mClock = Clock.systemUTC();
    private ProtectedServersEncryptionConfigDao mProtectedServersEncryptionConfigDao;
    private Flags mFlags = new ProtectedServersEncryptionConfigManagerTestFlags();

    private ExecutorService mLightweightExecutor;
    private AuctionEncryptionKeyParser mAuctionEncryptionKeyParser =
            new AuctionEncryptionKeyParser(mFlags);
    private JoinEncryptionKeyParser mJoinEncryptionKeyParser = new JoinEncryptionKeyParser(mFlags);
    private ProtectedServersEncryptionConfigManager mKeyManager;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setUp() {
        mLightweightExecutor = AdServicesExecutors.getLightWeightExecutor();
        mProtectedServersEncryptionConfigDao =
                Room.inMemoryDatabaseBuilder(
                                ApplicationProvider.getApplicationContext(),
                                AdSelectionServerDatabase.class)
                        .build()
                        .protectedServersEncryptionConfigDao();
        mKeyManager =
                new ProtectedServersEncryptionConfigManager(
                        mProtectedServersEncryptionConfigDao,
                        mFlags,
                        mClock,
                        mAuctionEncryptionKeyParser,
                        mJoinEncryptionKeyParser,
                        mMockHttpClient,
                        mLightweightExecutor);
    }

    @Test
    public void test_getLatestJoinKey_noJoinKey_returnsNull() {
        assertThat(
                        mKeyManager.getLatestKeyFromDatabase(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN,
                                COORDINATOR_URL_JOIN))
                .isNull();
    }

    @Test
    public void test_getLatestAuctionKey_noAuctionKey_returnsNull() {
        assertThat(
                        mKeyManager.getLatestKeyFromDatabase(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                                COORDINATOR_URL_JOIN))
                .isNull();
    }

    @Test
    public void test_getLatestJoinKey_returnsJoinKey() {
        assertThat(
                        mKeyManager.getLatestKeyFromDatabase(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN,
                                COORDINATOR_URL_JOIN))
                .isNull();

        mProtectedServersEncryptionConfigDao.insertKeys(
                ImmutableList.of(ENCRYPTION_KEY_JOIN_WITH_COORDINATOR));

        AdSelectionEncryptionKey actualKey =
                mKeyManager.getLatestKeyFromDatabase(
                        AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN,
                        COORDINATOR_URL_JOIN);

        assertThat(actualKey).isNotNull();
        assertThat(actualKey.keyIdentifier())
                .isEqualTo(ENCRYPTION_KEY_JOIN_WITH_COORDINATOR.getKeyIdentifier());
        assertThat(actualKey.publicKey())
                .isEqualTo(
                        BaseEncoding.base16()
                                .lowerCase()
                                .decode(ENCRYPTION_KEY_JOIN_WITH_COORDINATOR.getPublicKey()));
    }

    @Test
    public void test_getLatestAuctionKey_returnsAuctionKey() {
        assertThat(
                        mKeyManager.getLatestKeyFromDatabase(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                                COORDINATOR_URL_AUCTION))
                .isNull();

        mProtectedServersEncryptionConfigDao.insertKeys(
                ImmutableList.of(ENCRYPTION_KEY_AUCTION_WITH_COORDINATOR));

        AdSelectionEncryptionKey actualKey =
                mKeyManager.getLatestKeyFromDatabase(
                        AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                        COORDINATOR_URL_AUCTION);

        assertThat(actualKey).isNotNull();
        assertThat(actualKey.keyIdentifier())
                .isEqualTo(ENCRYPTION_KEY_AUCTION_WITH_COORDINATOR.getKeyIdentifier());
        assertThat(actualKey.publicKey())
                .isEqualTo(
                        Base64.getDecoder()
                                .decode(
                                        ENCRYPTION_KEY_AUCTION_WITH_COORDINATOR
                                                .getPublicKey()
                                                .getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void test_fetchAndPersistAuctionKey_fetchSuccess_returnsLatestActiveAuctionKey()
            throws Exception {
        when(mMockHttpClient.fetchPayload(Uri.parse(COORDINATOR_URL_AUCTION), DEV_CONTEXT_DISABLED))
                .thenReturn(
                        Futures.immediateFuture(
                                AuctionEncryptionKeyFixture.mockAuctionKeyFetchResponse()));

        assertThat(
                        mKeyManager.getLatestKeyFromDatabase(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                                COORDINATOR_URL_AUCTION))
                .isNull();

        AdSelectionEncryptionKey actualKey =
                mKeyManager
                        .fetchPersistAndGetActiveKey(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                                Uri.parse(COORDINATOR_URL_AUCTION),
                                TIMEOUT_MS)
                        .get();

        assertThat(actualKey).isNotNull();
    }

    @Test
    public void test_multipleCoordinatorKeys_fetchSuccess_returnsLatestActiveAuctionKey()
            throws Exception {
        when(mMockHttpClient.fetchPayload(Uri.parse(COORDINATOR_URL_AUCTION), DEV_CONTEXT_DISABLED))
                .thenReturn(
                        Futures.immediateFuture(
                                AuctionEncryptionKeyFixture.mockAuctionKeyFetchResponseWithGivenKey(
                                        AUCTION_KEY_1)));

        when(mMockHttpClient.fetchPayload(
                        Uri.parse(AUCTION_KEY_FETCH_DEFAULT_URI), DEV_CONTEXT_DISABLED))
                .thenReturn(
                        Futures.immediateFuture(
                                AuctionEncryptionKeyFixture.mockAuctionKeyFetchResponseWithGivenKey(
                                        AUCTION_KEY_2)));

        assertThat(
                        mKeyManager.getLatestKeyFromDatabase(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                                COORDINATOR_URL_AUCTION))
                .isNull();
        assertThat(
                        mKeyManager.getLatestKeyFromDatabase(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                                AUCTION_KEY_FETCH_DEFAULT_URI))
                .isNull();

        AdSelectionEncryptionKey actualKey =
                mKeyManager
                        .fetchPersistAndGetActiveKey(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                                Uri.parse(COORDINATOR_URL_AUCTION),
                                TIMEOUT_MS)
                        .get();

        assertThat(actualKey.keyIdentifier()).isEqualTo(AUCTION_KEY_1.keyId());

        actualKey =
                mKeyManager
                        .fetchPersistAndGetActiveKey(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                                Uri.parse(AUCTION_KEY_FETCH_DEFAULT_URI),
                                TIMEOUT_MS)
                        .get();

        assertThat(actualKey.keyIdentifier()).isEqualTo(AUCTION_KEY_2.keyId());
    }

    @Test
    public void test_fetchAndPersistJoinKey_fetchSuccess_returnsLatestActiveJoinKey()
            throws Exception {
        AdServicesHttpClientRequest fetchKeyRequest =
                AdServicesHttpClientRequest.builder()
                        .setUri(Uri.parse(COORDINATOR_URL_JOIN))
                        .setRequestProperties(REQUEST_PROPERTIES_PROTOBUF_CONTENT_TYPE)
                        .setResponseHeaderKeys(RESPONSE_PROPERTIES_CONTENT_TYPE)
                        .setDevContext(DevContext.createForDevOptionsDisabled())
                        .build();
        when(mMockHttpClient.performRequestGetResponseInBase64String(fetchKeyRequest))
                .thenReturn(
                        Futures.immediateFuture(
                                JoinEncryptionKeyTestUtil.mockJoinKeyFetchResponse()));

        assertThat(
                        mKeyManager.getLatestKeyFromDatabase(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN,
                                COORDINATOR_URL_JOIN))
                .isNull();

        AdSelectionEncryptionKey actualKey =
                mKeyManager
                        .fetchPersistAndGetActiveKey(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN,
                                Uri.parse(COORDINATOR_URL_JOIN),
                                TIMEOUT_MS)
                        .get();

        assertThat(actualKey).isNotNull();
    }

    @Test
    public void test_fetchAndPersistActiveKeysOfType_persistsJey() throws Exception {
        when(mMockHttpClient.fetchPayload(Uri.parse(COORDINATOR_URL_AUCTION), DEV_CONTEXT_DISABLED))
                .thenReturn(
                        Futures.immediateFuture(
                                AuctionEncryptionKeyFixture.mockAuctionKeyFetchResponse()));

        mProtectedServersEncryptionConfigDao.insertKeys(
                Arrays.asList(
                        DBProtectedServersEncryptionConfig.builder()
                                .setEncryptionKeyType(
                                        AdSelectionEncryptionKey.AdSelectionEncryptionKeyType
                                                .AUCTION)
                                .setKeyIdentifier("7b6724dc-839c-4108-bfa7-2e73eb19e5fe")
                                .setPublicKey("t/dzKzHJKe7k//n2u7wDdvxRtgXy9SncfXz6g8JB/m4=")
                                .setCoordinatorUrl(COORDINATOR_URL_AUCTION)
                                .setExpiryTtlSeconds(-1L)
                                .build()));
        assertThat(
                        mKeyManager.getLatestKeyFromDatabase(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                                COORDINATOR_URL_AUCTION))
                .isNotNull();

        List<DBEncryptionKey> actualKeys =
                mKeyManager
                        .fetchAndPersistActiveKeysOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                                mClock.instant().plusSeconds(1000L),
                                TIMEOUT_MS,
                                Uri.parse(COORDINATOR_URL_AUCTION))
                        .get();

        assertThat(actualKeys).isNotNull();
        assertThat(actualKeys.size()).isEqualTo(5);
    }

    @Test
    public void test_getLatestOhttpKeyConfigOfType_typeAuction_returnsLatestKey() throws Exception {
        mProtectedServersEncryptionConfigDao.insertKeys(
                ImmutableList.of(ENCRYPTION_KEY_AUCTION_WITH_COORDINATOR));

        ObliviousHttpKeyConfig actualKeyConfig =
                mKeyManager
                        .getLatestOhttpKeyConfigOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                                TIMEOUT_MS,
                                Uri.parse(COORDINATOR_URL_AUCTION_ORIGIN))
                        .get();

        byte[] expectedPublicKey =
                Base64.getDecoder()
                        .decode(
                                ENCRYPTION_KEY_AUCTION_WITH_COORDINATOR
                                        .getPublicKey()
                                        .getBytes(StandardCharsets.UTF_8));
        assertThat(actualKeyConfig.getPublicKey()).isEqualTo(expectedPublicKey);
    }

    @Test
    public void test_getLatestOhttpKeyConfigOfType_withNoKeys_returnsLatestKey() throws Exception {
        when(mMockHttpClient.fetchPayload(Uri.parse(COORDINATOR_URL_AUCTION), DEV_CONTEXT_DISABLED))
                .thenReturn(
                        Futures.immediateFuture(
                                AuctionEncryptionKeyFixture.mockAuctionKeyFetchResponseWithGivenKey(
                                        AUCTION_KEY_1)));

        ObliviousHttpKeyConfig actualKeyConfig =
                mKeyManager
                        .getLatestOhttpKeyConfigOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                                TIMEOUT_MS,
                                Uri.parse(COORDINATOR_URL_AUCTION_ORIGIN))
                        .get();

        byte[] expectedPublicKey =
                Base64.getDecoder()
                        .decode(AUCTION_KEY_1.publicKey().getBytes(StandardCharsets.UTF_8));
        assertThat(actualKeyConfig.getPublicKey()).isEqualTo(expectedPublicKey);
    }

    @Test
    public void test_getLatestOhttpKeyConfigOfType_withNoKeys_persistsToDatabase()
            throws Exception {
        when(mMockHttpClient.fetchPayload(Uri.parse(COORDINATOR_URL_AUCTION), DEV_CONTEXT_DISABLED))
                .thenReturn(
                        Futures.immediateFuture(
                                AuctionEncryptionKeyFixture.mockAuctionKeyFetchResponseWithGivenKey(
                                        AUCTION_KEY_1)));

        ObliviousHttpKeyConfig actualKeyConfig =
                mKeyManager
                        .getLatestOhttpKeyConfigOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                                TIMEOUT_MS,
                                Uri.parse(COORDINATOR_URL_AUCTION_ORIGIN))
                        .get();

        byte[] expectedPublicKey =
                Base64.getDecoder()
                        .decode(AUCTION_KEY_1.publicKey().getBytes(StandardCharsets.UTF_8));
        assertThat(actualKeyConfig.getPublicKey()).isEqualTo(expectedPublicKey);

        List<DBProtectedServersEncryptionConfig> databaseEntries =
                mProtectedServersEncryptionConfigDao.getLatestExpiryNKeys(
                        AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                        COORDINATOR_URL_AUCTION,
                        TIMEOUT_MS);

        assertThat(databaseEntries.size()).isEqualTo(1);
        assertThat(databaseEntries.get(0).getCoordinatorUrl()).isEqualTo(COORDINATOR_URL_AUCTION);
        assertThat(databaseEntries.get(0).getKeyIdentifier()).isEqualTo(AUCTION_KEY_1.keyId());
    }

    @Test
    public void test_getLatestOhttpKeyConfigOfType_nullCoordinator_returnsDefaultKey()
            throws Exception {
        when(mMockHttpClient.fetchPayload(
                        Uri.parse(AUCTION_KEY_FETCH_DEFAULT_URI), DEV_CONTEXT_DISABLED))
                .thenReturn(
                        Futures.immediateFuture(
                                AuctionEncryptionKeyFixture.mockAuctionKeyFetchResponseWithGivenKey(
                                        AUCTION_KEY_1)));

        // Insert a key with the coordinator
        mProtectedServersEncryptionConfigDao.insertKeys(
                ImmutableList.of(ENCRYPTION_KEY_AUCTION_WITH_COORDINATOR));

        // No coordinator provided
        ObliviousHttpKeyConfig actualKeyConfig =
                mKeyManager
                        .getLatestOhttpKeyConfigOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                                TIMEOUT_MS,
                                null)
                        .get();

        // Should fetch the default key
        byte[] expectedPublicKey =
                Base64.getDecoder()
                        .decode(AUCTION_KEY_1.publicKey().getBytes(StandardCharsets.UTF_8));
        assertThat(actualKeyConfig.getPublicKey()).isEqualTo(expectedPublicKey);
    }

    private static class ProtectedServersEncryptionConfigManagerTestFlags implements Flags {
        ProtectedServersEncryptionConfigManagerTestFlags() {}

        @Override
        public String getFledgeAuctionServerAuctionKeyFetchUri() {
            return AUCTION_KEY_FETCH_DEFAULT_URI;
        }

        @Override
        public String getFledgeAuctionServerCoordinatorUrlAllowlist() {
            return COORDINATOR_URL_AUCTION;
        }

        @Override
        public String getFledgeAuctionServerJoinKeyFetchUri() {
            return JOIN_KEY_FETCH_DEFAULT_URI;
        }

        @Override
        public int getFledgeAuctionServerAuctionKeySharding() {
            return 5;
        }

        @Override
        public long getFledgeAuctionServerEncryptionKeyMaxAgeSeconds() {
            return EXPIRY_TTL_1SEC;
        }
    }

    private void addDelayToExpireKeys(long delaySeconds) {
        when(mClock.instant()).thenReturn(Clock.systemUTC().instant().plusSeconds(delaySeconds));
    }
}
