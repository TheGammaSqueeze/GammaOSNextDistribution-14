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

package com.android.adservices.data.adselection;

import static com.android.adservices.data.adselection.EncryptionKeyConstants.EncryptionKeyType.ENCRYPTION_KEY_TYPE_AUCTION;
import static com.android.adservices.data.adselection.EncryptionKeyConstants.EncryptionKeyType.ENCRYPTION_KEY_TYPE_JOIN;
import static com.android.adservices.data.adselection.EncryptionKeyConstants.EncryptionKeyType.ENCRYPTION_KEY_TYPE_QUERY;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.SdkLevelSupportRule;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public class EncryptionKeyDaoTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final Long EXPIRY_TTL_SECONDS = 1209600L;
    private static final DBEncryptionKey ENCRYPTION_KEY_AUCTION =
            DBEncryptionKey.builder()
                    .setKeyIdentifier("key_id_1")
                    .setPublicKey("public_key_1")
                    .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_AUCTION)
                    .setExpiryTtlSeconds(EXPIRY_TTL_SECONDS)
                    .build();

    private static final DBEncryptionKey ENCRYPTION_KEY_JOIN =
            DBEncryptionKey.builder()
                    .setKeyIdentifier("key_id_2")
                    .setPublicKey("public_key_2")
                    .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_JOIN)
                    .setExpiryTtlSeconds(EXPIRY_TTL_SECONDS)
                    .build();

    private static final DBEncryptionKey ENCRYPTION_KEY_QUERY =
            DBEncryptionKey.builder()
                    .setKeyIdentifier("key_id_3")
                    .setPublicKey("public_key_3")
                    .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_QUERY)
                    .setExpiryTtlSeconds(EXPIRY_TTL_SECONDS)
                    .build();
    private static final DBEncryptionKey ENCRYPTION_KEY_AUCTION_TTL_5SECS =
            DBEncryptionKey.builder()
                    .setKeyIdentifier("key_id_4")
                    .setPublicKey("public_key_4")
                    .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_AUCTION)
                    .setExpiryTtlSeconds(5L)
                    .build();

    private static final DBEncryptionKey ENCRYPTION_KEY_JOIN_TTL_5SECS =
            DBEncryptionKey.builder()
                    .setKeyIdentifier("key_id_5")
                    .setPublicKey("public_key_5")
                    .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_JOIN)
                    .setExpiryTtlSeconds(5L)
                    .build();

    private static final DBEncryptionKey ENCRYPTION_KEY_QUERY_TTL_5SECS =
            DBEncryptionKey.builder()
                    .setKeyIdentifier("key_id_6")
                    .setPublicKey("public_key_6")
                    .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_QUERY)
                    .setExpiryTtlSeconds(5L)
                    .build();

    private EncryptionKeyDao mEncryptionKeyDao;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() {
        mEncryptionKeyDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, AdSelectionServerDatabase.class)
                        .build()
                        .encryptionKeyDao();
    }

    @Test
    public void test_getLatestExpiryNKeysOfType_returnsEmptyListWhenKeyAbsent() {
        assertThat(mEncryptionKeyDao.getLatestExpiryNKeysOfType(ENCRYPTION_KEY_TYPE_AUCTION, 1))
                .isEmpty();
        assertThat(mEncryptionKeyDao.getLatestExpiryNKeysOfType(ENCRYPTION_KEY_TYPE_JOIN, 1))
                .isEmpty();
        assertThat(mEncryptionKeyDao.getLatestExpiryNKeysOfType(ENCRYPTION_KEY_TYPE_QUERY, 1))
                .isEmpty();
    }

    @Test
    public void test_getLatestExpiryNKeysOfType_returnsNFreshestKey() {
        mEncryptionKeyDao.insertAllKeys(
                ImmutableList.of(
                        ENCRYPTION_KEY_AUCTION,
                        ENCRYPTION_KEY_JOIN,
                        ENCRYPTION_KEY_QUERY,
                        ENCRYPTION_KEY_AUCTION_TTL_5SECS,
                        ENCRYPTION_KEY_JOIN_TTL_5SECS,
                        ENCRYPTION_KEY_QUERY_TTL_5SECS));

        List<DBEncryptionKey> auctionKeys =
                mEncryptionKeyDao.getLatestExpiryNKeysOfType(ENCRYPTION_KEY_TYPE_AUCTION, 2);
        assertThat(auctionKeys).hasSize(2);
        assertThat(auctionKeys.get(0).getKeyIdentifier())
                .isEqualTo(ENCRYPTION_KEY_AUCTION.getKeyIdentifier());
        assertThat(auctionKeys.get(1).getKeyIdentifier())
                .isEqualTo(ENCRYPTION_KEY_AUCTION_TTL_5SECS.getKeyIdentifier());

        List<DBEncryptionKey> queryKeys =
                mEncryptionKeyDao.getLatestExpiryNKeysOfType(ENCRYPTION_KEY_TYPE_QUERY, 2);
        assertThat(queryKeys).hasSize(2);
        assertThat(queryKeys.get(0).getKeyIdentifier())
                .isEqualTo(ENCRYPTION_KEY_QUERY.getKeyIdentifier());
        assertThat(queryKeys.get(1).getKeyIdentifier())
                .isEqualTo(ENCRYPTION_KEY_QUERY_TTL_5SECS.getKeyIdentifier());

        List<DBEncryptionKey> joinKeys =
                mEncryptionKeyDao.getLatestExpiryNKeysOfType(ENCRYPTION_KEY_TYPE_JOIN, 2);
        assertThat(joinKeys).hasSize(2);
        assertThat(joinKeys.get(0).getKeyIdentifier())
                .isEqualTo(ENCRYPTION_KEY_JOIN.getKeyIdentifier());
        assertThat(joinKeys.get(1).getKeyIdentifier())
                .isEqualTo(ENCRYPTION_KEY_JOIN_TTL_5SECS.getKeyIdentifier());
    }

    @Test
    public void test_getLatestExpiryNActiveKeyOfType_returnsEmptyWhenKeyAbsent() {
        assertThat(
                        mEncryptionKeyDao.getLatestExpiryNActiveKeysOfType(
                                ENCRYPTION_KEY_TYPE_AUCTION, Instant.now(), 1))
                .isEmpty();
        assertThat(
                        mEncryptionKeyDao.getLatestExpiryNActiveKeysOfType(
                                ENCRYPTION_KEY_TYPE_JOIN, Instant.now(), 1))
                .isEmpty();
        assertThat(
                        mEncryptionKeyDao.getLatestExpiryNActiveKeysOfType(
                                ENCRYPTION_KEY_TYPE_QUERY, Instant.now(), 1))
                .isEmpty();
    }

    @Test
    public void test_getLatestExpiryNActiveKeyOfType_returnsNFreshestKey() {
        mEncryptionKeyDao.insertAllKeys(
                ImmutableList.of(
                        ENCRYPTION_KEY_AUCTION,
                        ENCRYPTION_KEY_JOIN,
                        ENCRYPTION_KEY_QUERY,
                        ENCRYPTION_KEY_AUCTION_TTL_5SECS,
                        ENCRYPTION_KEY_JOIN_TTL_5SECS,
                        ENCRYPTION_KEY_QUERY_TTL_5SECS));

        Instant currentInstant = Instant.now().plusSeconds(5L);

        List<DBEncryptionKey> auctionKeys =
                mEncryptionKeyDao.getLatestExpiryNActiveKeysOfType(
                        ENCRYPTION_KEY_TYPE_AUCTION, currentInstant, 2);
        assertThat(auctionKeys).hasSize(1);
        assertThat(auctionKeys.get(0).getKeyIdentifier())
                .isEqualTo(ENCRYPTION_KEY_AUCTION.getKeyIdentifier());

        List<DBEncryptionKey> queryKeys =
                mEncryptionKeyDao.getLatestExpiryNActiveKeysOfType(
                        ENCRYPTION_KEY_TYPE_QUERY, currentInstant, 2);
        assertThat(queryKeys).hasSize(1);
        assertThat(queryKeys.get(0).getKeyIdentifier())
                .isEqualTo(ENCRYPTION_KEY_QUERY.getKeyIdentifier());

        List<DBEncryptionKey> joinKeys =
                mEncryptionKeyDao.getLatestExpiryNActiveKeysOfType(
                        ENCRYPTION_KEY_TYPE_JOIN, currentInstant, 2);
        assertThat(joinKeys).hasSize(1);
        assertThat(joinKeys.get(0).getKeyIdentifier())
                .isEqualTo(ENCRYPTION_KEY_JOIN.getKeyIdentifier());
    }

    @Test
    public void test_getExpiredKeysForType_noExpiredKeys_returnsEmpty() {
        assertThat(
                        mEncryptionKeyDao.getExpiredKeysForType(
                                ENCRYPTION_KEY_TYPE_AUCTION, Instant.now()))
                .isEmpty();
        assertThat(mEncryptionKeyDao.getExpiredKeysForType(ENCRYPTION_KEY_TYPE_JOIN, Instant.now()))
                .isEmpty();
        assertThat(
                        mEncryptionKeyDao.getExpiredKeysForType(
                                ENCRYPTION_KEY_TYPE_QUERY, Instant.now()))
                .isEmpty();
    }

    @Test
    public void test_getExpiredKeysForType_returnsExpiredKeys_success() {
        mEncryptionKeyDao.insertAllKeys(
                ImmutableList.of(
                        ENCRYPTION_KEY_AUCTION,
                        ENCRYPTION_KEY_JOIN,
                        ENCRYPTION_KEY_QUERY,
                        ENCRYPTION_KEY_AUCTION_TTL_5SECS,
                        ENCRYPTION_KEY_JOIN_TTL_5SECS,
                        ENCRYPTION_KEY_QUERY_TTL_5SECS));

        Instant currentInstant = Instant.now().plusSeconds(5L);
        List<DBEncryptionKey> expiredAuctionKeys =
                mEncryptionKeyDao.getExpiredKeysForType(
                        ENCRYPTION_KEY_TYPE_AUCTION, currentInstant);
        assertThat(expiredAuctionKeys.size()).isEqualTo(1);
        assertThat(expiredAuctionKeys.stream().findFirst().get().getKeyIdentifier())
                .isEqualTo(ENCRYPTION_KEY_AUCTION_TTL_5SECS.getKeyIdentifier());

        List<DBEncryptionKey> expiredJoinKeys =
                mEncryptionKeyDao.getExpiredKeysForType(ENCRYPTION_KEY_TYPE_JOIN, currentInstant);
        assertThat(expiredJoinKeys.size()).isEqualTo(1);
        assertThat(expiredJoinKeys.stream().findFirst().get().getKeyIdentifier())
                .isEqualTo(ENCRYPTION_KEY_JOIN_TTL_5SECS.getKeyIdentifier());

        List<DBEncryptionKey> expiredQueryKeys =
                mEncryptionKeyDao.getExpiredKeysForType(ENCRYPTION_KEY_TYPE_QUERY, currentInstant);
        assertThat(expiredQueryKeys.size()).isEqualTo(1);
        assertThat(expiredQueryKeys.stream().findFirst().get().getKeyIdentifier())
                .isEqualTo(ENCRYPTION_KEY_QUERY_TTL_5SECS.getKeyIdentifier());
    }

    @Test
    public void test_getExpiredKeys_noExpiredKeys_returnsEmpty() {
        mEncryptionKeyDao.insertAllKeys(
                ImmutableList.of(
                        ENCRYPTION_KEY_AUCTION, ENCRYPTION_KEY_JOIN, ENCRYPTION_KEY_QUERY));
        assertThat(mEncryptionKeyDao.getExpiredKeys(Instant.now())).isEmpty();
    }

    @Test
    public void test_getExpiredKeys_returnsExpiredKeys() {
        mEncryptionKeyDao.insertAllKeys(
                ImmutableList.of(
                        ENCRYPTION_KEY_AUCTION,
                        ENCRYPTION_KEY_JOIN,
                        ENCRYPTION_KEY_QUERY,
                        ENCRYPTION_KEY_AUCTION_TTL_5SECS,
                        ENCRYPTION_KEY_JOIN_TTL_5SECS,
                        ENCRYPTION_KEY_QUERY_TTL_5SECS));

        Instant currentInstant = Instant.now().plusSeconds(5L);
        List<DBEncryptionKey> expiredKeys = mEncryptionKeyDao.getExpiredKeys(currentInstant);

        assertThat(expiredKeys.size()).isEqualTo(3);
        assertThat(expiredKeys.stream().map(k -> k.getKeyIdentifier()).collect(Collectors.toSet()))
                .containsExactly(
                        ENCRYPTION_KEY_AUCTION_TTL_5SECS.getKeyIdentifier(),
                        ENCRYPTION_KEY_JOIN_TTL_5SECS.getKeyIdentifier(),
                        ENCRYPTION_KEY_QUERY_TTL_5SECS.getKeyIdentifier());
    }

    @Test
    public void test_deleteExpiredKeys_noExpiredKeys_returnsZero() {
        mEncryptionKeyDao.insertAllKeys(ImmutableList.of(ENCRYPTION_KEY_AUCTION));
        assertThat(
                        mEncryptionKeyDao.deleteExpiredRowsByType(
                                ENCRYPTION_KEY_TYPE_AUCTION, Instant.now()))
                .isEqualTo(0);
    }

    @Test
    public void test_deleteExpiredKeys_deletesKeysSuccessfully() {
        mEncryptionKeyDao.insertAllKeys(
                ImmutableList.of(ENCRYPTION_KEY_JOIN, ENCRYPTION_KEY_AUCTION_TTL_5SECS));

        assertThat(mEncryptionKeyDao.getLatestExpiryNKeysOfType(ENCRYPTION_KEY_TYPE_AUCTION, 1))
                .isNotEmpty();

        mEncryptionKeyDao.deleteExpiredRowsByType(
                ENCRYPTION_KEY_TYPE_AUCTION, Instant.now().plusSeconds(10L));

        assertThat(mEncryptionKeyDao.getLatestExpiryNKeysOfType(ENCRYPTION_KEY_TYPE_AUCTION, 1))
                .isEmpty();
    }

    @Test
    public void test_insertAllKeys_validKeys_success() {
        assertThat(mEncryptionKeyDao.getLatestExpiryNKeysOfType(ENCRYPTION_KEY_TYPE_AUCTION, 1))
                .isEmpty();
        mEncryptionKeyDao.insertAllKeys(ImmutableList.of(ENCRYPTION_KEY_AUCTION));
        assertThat(mEncryptionKeyDao.getLatestExpiryNKeysOfType(ENCRYPTION_KEY_TYPE_AUCTION, 1))
                .isNotEmpty();
    }

    @Test
    public void test_deleteAllEncryptionKeys_success() {
        mEncryptionKeyDao.insertAllKeys(
                ImmutableList.of(
                        ENCRYPTION_KEY_AUCTION, ENCRYPTION_KEY_JOIN, ENCRYPTION_KEY_QUERY));
        assertThat(mEncryptionKeyDao.getLatestExpiryNKeysOfType(ENCRYPTION_KEY_TYPE_AUCTION, 1))
                .isNotEmpty();
        assertThat(mEncryptionKeyDao.getLatestExpiryNKeysOfType(ENCRYPTION_KEY_TYPE_JOIN, 1))
                .isNotEmpty();
        assertThat(mEncryptionKeyDao.getLatestExpiryNKeysOfType(ENCRYPTION_KEY_TYPE_QUERY, 1))
                .isNotEmpty();

        mEncryptionKeyDao.deleteAllEncryptionKeys();

        assertThat(mEncryptionKeyDao.getLatestExpiryNKeysOfType(ENCRYPTION_KEY_TYPE_AUCTION, 1))
                .isEmpty();
        assertThat(mEncryptionKeyDao.getLatestExpiryNKeysOfType(ENCRYPTION_KEY_TYPE_JOIN, 1))
                .isEmpty();
        assertThat(mEncryptionKeyDao.getLatestExpiryNKeysOfType(ENCRYPTION_KEY_TYPE_QUERY, 1))
                .isEmpty();
    }
}
