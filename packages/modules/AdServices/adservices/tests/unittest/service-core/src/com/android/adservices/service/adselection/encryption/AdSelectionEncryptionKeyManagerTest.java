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

import static android.adservices.adselection.AuctionEncryptionKeyFixture.AUCTION_KEY_1;
import static android.adservices.adselection.AuctionEncryptionKeyFixture.ENCRYPTION_KEY_AUCTION;
import static android.adservices.adselection.AuctionEncryptionKeyFixture.ENCRYPTION_KEY_AUCTION_TTL_1SECS;

import static com.android.adservices.service.adselection.encryption.JoinEncryptionKeyTestUtil.ENCRYPTION_KEY_JOIN;
import static com.android.adservices.service.adselection.encryption.JoinEncryptionKeyTestUtil.ENCRYPTION_KEY_JOIN_TTL_1SECS;
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
import com.android.adservices.data.adselection.EncryptionKeyDao;
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
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class AdSelectionEncryptionKeyManagerTest {
    private static final Long EXPIRY_TTL_1SEC = 1L;

    private static final int TIMEOUT_MS = 500;

    private static final String AUCTION_KEY_FETCH_URI = "https://foo.bar/auctionkey";
    private static final String JOIN_KEY_FETCH_URI = "https://foo.bar/joinkey";
    private static final DevContext DEV_CONTEXT_DISABLED = DevContext.createForDevOptionsDisabled();

    @Rule public final MockitoRule mockito = MockitoJUnit.rule();
    @Mock private AdServicesHttpsClient mMockHttpClient;
    @Spy private Clock mClock = Clock.systemUTC();
    private EncryptionKeyDao mEncryptionKeyDao;
    private Flags mFlags = new AdSelectionEncryptionKeyManagerTestFlags();

    private ExecutorService mLightweightExecutor;
    private AuctionEncryptionKeyParser mAuctionEncryptionKeyParser =
            new AuctionEncryptionKeyParser(mFlags);
    private JoinEncryptionKeyParser mJoinEncryptionKeyParser = new JoinEncryptionKeyParser(mFlags);
    private AdSelectionEncryptionKeyManager mKeyManager;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setUp() {
        mLightweightExecutor = AdServicesExecutors.getLightWeightExecutor();
        mEncryptionKeyDao =
                Room.inMemoryDatabaseBuilder(
                                ApplicationProvider.getApplicationContext(),
                                AdSelectionServerDatabase.class)
                        .build()
                        .encryptionKeyDao();
        mKeyManager =
                new AdSelectionEncryptionKeyManager(
                        mEncryptionKeyDao,
                        mFlags,
                        mClock,
                        mAuctionEncryptionKeyParser,
                        mJoinEncryptionKeyParser,
                        mMockHttpClient,
                        mLightweightExecutor);
    }

    @Test
    public void test_getLatestActiveJoinKey_noJoinKey_returnsNull() {
        assertThat(
                        mKeyManager.getLatestActiveKeyOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN))
                .isNull();
    }

    @Test
    public void test_getLatestActiveAuctionKey_noAuctionKey_returnsNull() {
        assertThat(
                        mKeyManager.getLatestActiveKeyOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION))
                .isNull();
    }

    @Test
    public void test_getLatestActiveJoinKey_noActiveJoinKey_returnsNull() throws Exception {
        mEncryptionKeyDao.insertAllKeys(ImmutableList.of(ENCRYPTION_KEY_JOIN_TTL_1SECS));

        addDelayToExpireKeys(EXPIRY_TTL_1SEC);

        assertThat(
                        mKeyManager.getLatestActiveKeyOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN))
                .isNull();
    }

    @Test
    public void test_getLatestActiveAuctionKey_noActiveAuctionKey_returnsNull() throws Exception {
        mEncryptionKeyDao.insertAllKeys(ImmutableList.of(ENCRYPTION_KEY_JOIN_TTL_1SECS));

        addDelayToExpireKeys(EXPIRY_TTL_1SEC);

        assertThat(
                        mKeyManager.getLatestActiveKeyOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION))
                .isNull();
    }

    @Test
    public void test_getLatestActiveJoinKey_returnsActiveJoinKey() throws Exception {
        assertThat(
                        mKeyManager.getLatestActiveKeyOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN))
                .isNull();

        mEncryptionKeyDao.insertAllKeys(
                ImmutableList.of(ENCRYPTION_KEY_JOIN, ENCRYPTION_KEY_JOIN_TTL_1SECS));

        addDelayToExpireKeys(EXPIRY_TTL_1SEC);

        AdSelectionEncryptionKey actualKey =
                mKeyManager.getLatestActiveKeyOfType(
                        AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN);
        assertThat(actualKey).isNotNull();
        assertThat(actualKey.keyIdentifier()).isEqualTo(ENCRYPTION_KEY_JOIN.getKeyIdentifier());
        assertThat(actualKey.publicKey())
                .isEqualTo(
                        BaseEncoding.base16()
                                .lowerCase()
                                .decode(ENCRYPTION_KEY_JOIN.getPublicKey()));
    }

    @Test
    public void test_getLatestActiveAuctionKey_returnsActiveAuctionKey()
            throws InterruptedException {
        assertThat(
                        mKeyManager.getLatestActiveKeyOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION))
                .isNull();

        mEncryptionKeyDao.insertAllKeys(
                ImmutableList.of(ENCRYPTION_KEY_AUCTION, ENCRYPTION_KEY_AUCTION_TTL_1SECS));

        addDelayToExpireKeys(EXPIRY_TTL_1SEC);

        AdSelectionEncryptionKey actualKey =
                mKeyManager.getLatestActiveKeyOfType(
                        AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION);
        assertThat(actualKey).isNotNull();
        assertThat(actualKey.keyIdentifier()).isEqualTo(ENCRYPTION_KEY_AUCTION.getKeyIdentifier());
        assertThat(actualKey.publicKey())
                .isEqualTo(
                        Base64.getDecoder()
                                .decode(
                                        ENCRYPTION_KEY_AUCTION
                                                .getPublicKey()
                                                .getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void test_getLatestJoinKey_noJoinKey_returnsNull() {
        assertThat(
                        mKeyManager.getLatestKeyOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN))
                .isNull();
    }

    @Test
    public void test_getLatestAuctionKey_noAuctionKey_returnsNull() {
        assertThat(
                        mKeyManager.getLatestKeyOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION))
                .isNull();
    }

    @Test
    public void test_getLatestJoinKey_returnsJoinKey() {
        assertThat(
                        mKeyManager.getLatestActiveKeyOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN))
                .isNull();

        mEncryptionKeyDao.insertAllKeys(
                ImmutableList.of(ENCRYPTION_KEY_JOIN, ENCRYPTION_KEY_JOIN_TTL_1SECS));

        AdSelectionEncryptionKey actualKey =
                mKeyManager.getLatestKeyOfType(
                        AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN);
        assertThat(actualKey).isNotNull();
    }

    @Test
    public void test_getLatestAuctionKey_returnsAuctionKey() {
        assertThat(
                        mKeyManager.getLatestActiveKeyOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION))
                .isNull();

        mEncryptionKeyDao.insertAllKeys(
                ImmutableList.of(ENCRYPTION_KEY_AUCTION, ENCRYPTION_KEY_AUCTION_TTL_1SECS));

        AdSelectionEncryptionKey actualKey =
                mKeyManager.getLatestKeyOfType(
                        AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION);
        assertThat(actualKey).isNotNull();
    }

    @Test
    public void test_fetchAndPersistAuctionKey_fetchSuccess_returnsLatestActiveAuctionKey()
            throws Exception {
        when(mMockHttpClient.fetchPayload(Uri.parse(AUCTION_KEY_FETCH_URI), DEV_CONTEXT_DISABLED))
                .thenReturn(
                        Futures.immediateFuture(
                                AuctionEncryptionKeyFixture.mockAuctionKeyFetchResponse()));

        assertThat(
                        mKeyManager.getLatestActiveKeyOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION))
                .isNull();

        AdSelectionEncryptionKey actualKey =
                mKeyManager
                        .fetchPersistAndGetActiveKeyOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                                TIMEOUT_MS)
                        .get();

        assertThat(actualKey).isNotNull();
    }

    @Test
    public void test_fetchAndPersistJoinKey_fetchSuccess_returnsLatestActiveJoinKey()
            throws Exception {
        AdServicesHttpClientRequest fetchKeyRequest =
                AdServicesHttpClientRequest.builder()
                        .setUri(Uri.parse(JOIN_KEY_FETCH_URI))
                        .setRequestProperties(REQUEST_PROPERTIES_PROTOBUF_CONTENT_TYPE)
                        .setResponseHeaderKeys(RESPONSE_PROPERTIES_CONTENT_TYPE)
                        .setDevContext(DevContext.createForDevOptionsDisabled())
                        .build();
        when(mMockHttpClient.performRequestGetResponseInBase64String(fetchKeyRequest))
                .thenReturn(
                        Futures.immediateFuture(
                                JoinEncryptionKeyTestUtil.mockJoinKeyFetchResponse()));

        assertThat(
                        mKeyManager.getLatestActiveKeyOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN))
                .isNull();

        AdSelectionEncryptionKey actualKey =
                mKeyManager
                        .fetchPersistAndGetActiveKeyOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN,
                                TIMEOUT_MS)
                        .get();

        assertThat(actualKey).isNotNull();
    }

    @Test
    public void test_getLatestOhttpKeyConfigOfType_typeAuction_returnsLatestKey() throws Exception {
        mEncryptionKeyDao.insertAllKeys(ImmutableList.of(ENCRYPTION_KEY_AUCTION));

        ObliviousHttpKeyConfig actualKeyConfig =
                mKeyManager
                        .getLatestOhttpKeyConfigOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                                TIMEOUT_MS,
                                null)
                        .get();

        byte[] expectedPublicKey =
                Base64.getDecoder()
                        .decode(
                                ENCRYPTION_KEY_AUCTION
                                        .getPublicKey()
                                        .getBytes(StandardCharsets.UTF_8));
        assertThat(actualKeyConfig.getPublicKey()).isEqualTo(expectedPublicKey);
    }

    @Test
    public void test_getLatestOhttpKeyConfigOfType_withExpiredKey_shouldReturnExpiredKey()
            throws Exception {
        mEncryptionKeyDao.insertAllKeys(ImmutableList.of(ENCRYPTION_KEY_AUCTION_TTL_1SECS));
        addDelayToExpireKeys(EXPIRY_TTL_1SEC);

        ObliviousHttpKeyConfig actualKeyConfig =
                mKeyManager
                        .getLatestOhttpKeyConfigOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                                TIMEOUT_MS,
                                null)
                        .get();

        byte[] expectedPublicKey =
                Base64.getDecoder()
                        .decode(
                                ENCRYPTION_KEY_AUCTION_TTL_1SECS
                                        .getPublicKey()
                                        .getBytes(StandardCharsets.UTF_8));
        assertThat(actualKeyConfig.getPublicKey()).isEqualTo(expectedPublicKey);
    }

    @Test
    public void
            test_getLatestActiveOhttpKeyConfig_withExpiredKey_shouldFetchAndPersistAndReturnNewKey()
                    throws Exception {
        mEncryptionKeyDao.insertAllKeys(ImmutableList.of(ENCRYPTION_KEY_AUCTION_TTL_1SECS));
        addDelayToExpireKeys(EXPIRY_TTL_1SEC);
        when(mMockHttpClient.fetchPayload(Uri.parse(AUCTION_KEY_FETCH_URI), DEV_CONTEXT_DISABLED))
                .thenReturn(
                        Futures.immediateFuture(
                                AuctionEncryptionKeyFixture
                                        .mockAuctionKeyFetchResponseWithOneKey()));

        ObliviousHttpKeyConfig actualKeyConfig =
                mKeyManager
                        .getLatestActiveOhttpKeyConfigOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                                TIMEOUT_MS)
                        .get();

        byte[] expectedPublicKey =
                Base64.getDecoder()
                        .decode(AUCTION_KEY_1.publicKey().getBytes(StandardCharsets.UTF_8));
        List<DBEncryptionKey> keys =
                mEncryptionKeyDao.getLatestExpiryNKeysOfType(
                        AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION, 2);
        assertThat(keys.size()).isEqualTo(1);
        assertThat(actualKeyConfig.getPublicKey()).isEqualTo(expectedPublicKey);
    }

    @Test
    public void
            test_getLatestActiveOhttpKeyConfigOfType_withNoKey_shouldFetchPersistAndReturnNewKey()
                    throws Exception {
        addDelayToExpireKeys(EXPIRY_TTL_1SEC);
        when(mMockHttpClient.fetchPayload(Uri.parse(AUCTION_KEY_FETCH_URI), DEV_CONTEXT_DISABLED))
                .thenReturn(
                        Futures.immediateFuture(
                                AuctionEncryptionKeyFixture
                                        .mockAuctionKeyFetchResponseWithOneKey()));

        ObliviousHttpKeyConfig actualKeyConfig =
                mKeyManager
                        .getLatestActiveOhttpKeyConfigOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                                TIMEOUT_MS)
                        .get();

        byte[] expectedPublicKey =
                Base64.getDecoder()
                        .decode(AUCTION_KEY_1.publicKey().getBytes(StandardCharsets.UTF_8));
        assertThat(actualKeyConfig.getPublicKey()).isEqualTo(expectedPublicKey);
    }

    @Test
    public void
            test_getLatestActiveOhttpKeyConfigOfType_withActiveAndExpiredKey_shouldGetActiveKey()
                    throws Exception {
        mEncryptionKeyDao.insertAllKeys(
                ImmutableList.of(ENCRYPTION_KEY_AUCTION, ENCRYPTION_KEY_AUCTION_TTL_1SECS));
        addDelayToExpireKeys(EXPIRY_TTL_1SEC);

        ObliviousHttpKeyConfig actualKeyConfig =
                mKeyManager
                        .getLatestActiveOhttpKeyConfigOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                                TIMEOUT_MS)
                        .get();

        byte[] expectedPublicKey =
                Base64.getDecoder()
                        .decode(
                                ENCRYPTION_KEY_AUCTION
                                        .getPublicKey()
                                        .getBytes(StandardCharsets.UTF_8));
        assertThat(actualKeyConfig.getPublicKey()).isEqualTo(expectedPublicKey);
    }

    private static class AdSelectionEncryptionKeyManagerTestFlags implements Flags {
        AdSelectionEncryptionKeyManagerTestFlags() {}

        @Override
        public String getFledgeAuctionServerAuctionKeyFetchUri() {
            return AUCTION_KEY_FETCH_URI;
        }

        @Override
        public String getFledgeAuctionServerJoinKeyFetchUri() {
            return JOIN_KEY_FETCH_URI;
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
