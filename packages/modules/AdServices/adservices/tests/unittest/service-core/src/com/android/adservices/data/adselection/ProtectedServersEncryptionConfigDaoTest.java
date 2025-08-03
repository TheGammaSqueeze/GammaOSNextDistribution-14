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

public class ProtectedServersEncryptionConfigDaoTest {

    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final Long EXPIRY_TTL_SECONDS = 1209600L;
    private static final String COORDINATOR_URL = "https://example.com";

    private static final String COORDINATOR_URL_2 = "https://example2.com";

    private static final DBProtectedServersEncryptionConfig ENCRYPTION_KEY_AUCTION =
            DBProtectedServersEncryptionConfig.builder()
                    .setCoordinatorUrl(COORDINATOR_URL)
                    .setKeyIdentifier("key_id_1")
                    .setPublicKey("public_key_1")
                    .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_AUCTION)
                    .setExpiryTtlSeconds(EXPIRY_TTL_SECONDS)
                    .build();

    private static final DBProtectedServersEncryptionConfig ENCRYPTION_KEY_JOIN =
            DBProtectedServersEncryptionConfig.builder()
                    .setCoordinatorUrl(COORDINATOR_URL)
                    .setKeyIdentifier("key_id_2")
                    .setPublicKey("public_key_2")
                    .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_JOIN)
                    .setExpiryTtlSeconds(EXPIRY_TTL_SECONDS)
                    .build();

    private static final DBProtectedServersEncryptionConfig ENCRYPTION_KEY_QUERY =
            DBProtectedServersEncryptionConfig.builder()
                    .setCoordinatorUrl(COORDINATOR_URL)
                    .setKeyIdentifier("key_id_3")
                    .setPublicKey("public_key_3")
                    .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_QUERY)
                    .setExpiryTtlSeconds(EXPIRY_TTL_SECONDS)
                    .build();
    private static final DBProtectedServersEncryptionConfig ENCRYPTION_KEY_AUCTION_TTL_5SECS =
            DBProtectedServersEncryptionConfig.builder()
                    .setCoordinatorUrl(COORDINATOR_URL)
                    .setKeyIdentifier("key_id_4")
                    .setPublicKey("public_key_4")
                    .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_AUCTION)
                    .setExpiryTtlSeconds(5L)
                    .build();

    private static final DBProtectedServersEncryptionConfig ENCRYPTION_KEY_JOIN_TTL_5SECS =
            DBProtectedServersEncryptionConfig.builder()
                    .setCoordinatorUrl(COORDINATOR_URL)
                    .setKeyIdentifier("key_id_5")
                    .setPublicKey("public_key_5")
                    .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_JOIN)
                    .setExpiryTtlSeconds(5L)
                    .build();

    private static final DBProtectedServersEncryptionConfig ENCRYPTION_KEY_QUERY_TTL_5SECS =
            DBProtectedServersEncryptionConfig.builder()
                    .setCoordinatorUrl(COORDINATOR_URL)
                    .setKeyIdentifier("key_id_6")
                    .setPublicKey("public_key_6")
                    .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_QUERY)
                    .setExpiryTtlSeconds(5L)
                    .build();

    private static final DBProtectedServersEncryptionConfig
            ENCRYPTION_AUCTION_WITH_DIFF_COORDINATOR =
                    DBProtectedServersEncryptionConfig.builder()
                            .setCoordinatorUrl(COORDINATOR_URL_2)
                            .setKeyIdentifier("key_id_6")
                            .setPublicKey("public_key_6")
                            .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_AUCTION)
                            .setExpiryTtlSeconds(EXPIRY_TTL_SECONDS)
                            .build();

    private ProtectedServersEncryptionConfigDao mProtectedServersEncryptionConfigDao;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() {
        mProtectedServersEncryptionConfigDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, AdSelectionServerDatabase.class)
                        .build()
                        .protectedServersEncryptionConfigDao();
    }

    @Test
    public void test_getLatestExpiryNKeysOfType_returnsEmptyListWhenKeyAbsent() {
        assertThat(
                        mProtectedServersEncryptionConfigDao.getLatestExpiryNKeys(
                                ENCRYPTION_KEY_TYPE_AUCTION, COORDINATOR_URL, 1))
                .isEmpty();
        assertThat(
                        mProtectedServersEncryptionConfigDao.getLatestExpiryNKeys(
                                ENCRYPTION_KEY_TYPE_JOIN, COORDINATOR_URL, 1))
                .isEmpty();
        assertThat(
                        mProtectedServersEncryptionConfigDao.getLatestExpiryNKeys(
                                ENCRYPTION_KEY_TYPE_QUERY, COORDINATOR_URL, 1))
                .isEmpty();
    }

    @Test
    public void test_getLatestExpiryNKeysOfType_returnsNFreshestKey() {
        mProtectedServersEncryptionConfigDao.insertKeys(
                ImmutableList.of(
                        ENCRYPTION_KEY_AUCTION,
                        ENCRYPTION_KEY_JOIN,
                        ENCRYPTION_KEY_QUERY,
                        ENCRYPTION_KEY_AUCTION_TTL_5SECS,
                        ENCRYPTION_KEY_JOIN_TTL_5SECS,
                        ENCRYPTION_KEY_QUERY_TTL_5SECS));

        List<DBProtectedServersEncryptionConfig> auctionKeys =
                mProtectedServersEncryptionConfigDao.getLatestExpiryNKeys(
                        ENCRYPTION_KEY_TYPE_AUCTION, COORDINATOR_URL, 2);
        assertThat(auctionKeys).hasSize(2);
        assertThat(auctionKeys.get(0).getKeyIdentifier())
                .isEqualTo(ENCRYPTION_KEY_AUCTION.getKeyIdentifier());
        assertThat(auctionKeys.get(0).getCoordinatorUrl()).isEqualTo(COORDINATOR_URL);
        assertThat(auctionKeys.get(1).getKeyIdentifier())
                .isEqualTo(ENCRYPTION_KEY_AUCTION_TTL_5SECS.getKeyIdentifier());

        List<DBProtectedServersEncryptionConfig> queryKeys =
                mProtectedServersEncryptionConfigDao.getLatestExpiryNKeys(
                        ENCRYPTION_KEY_TYPE_QUERY, COORDINATOR_URL, 2);
        assertThat(queryKeys).hasSize(2);
        assertThat(queryKeys.get(0).getKeyIdentifier())
                .isEqualTo(ENCRYPTION_KEY_QUERY.getKeyIdentifier());
        assertThat(queryKeys.get(1).getKeyIdentifier())
                .isEqualTo(ENCRYPTION_KEY_QUERY_TTL_5SECS.getKeyIdentifier());

        List<DBProtectedServersEncryptionConfig> joinKeys =
                mProtectedServersEncryptionConfigDao.getLatestExpiryNKeys(
                        ENCRYPTION_KEY_TYPE_JOIN, COORDINATOR_URL, 2);
        assertThat(joinKeys).hasSize(2);
        assertThat(joinKeys.get(0).getKeyIdentifier())
                .isEqualTo(ENCRYPTION_KEY_JOIN.getKeyIdentifier());
        assertThat(joinKeys.get(1).getKeyIdentifier())
                .isEqualTo(ENCRYPTION_KEY_JOIN_TTL_5SECS.getKeyIdentifier());
    }

    @Test
    public void test_getLatestExpiryNKeysOfType_diffCoordinators_returnsNFreshestKey() {
        mProtectedServersEncryptionConfigDao.insertKeys(
                ImmutableList.of(ENCRYPTION_KEY_AUCTION, ENCRYPTION_AUCTION_WITH_DIFF_COORDINATOR));

        List<DBProtectedServersEncryptionConfig> auctionKeys =
                mProtectedServersEncryptionConfigDao.getLatestExpiryNKeys(
                        ENCRYPTION_KEY_TYPE_AUCTION, COORDINATOR_URL, 2);
        assertThat(auctionKeys).hasSize(1);
        assertThat(auctionKeys.get(0).getKeyIdentifier())
                .isEqualTo(ENCRYPTION_KEY_AUCTION.getKeyIdentifier());
        assertThat(auctionKeys.get(0).getCoordinatorUrl()).isEqualTo(COORDINATOR_URL);

        auctionKeys =
                mProtectedServersEncryptionConfigDao.getLatestExpiryNKeys(
                        ENCRYPTION_KEY_TYPE_AUCTION, COORDINATOR_URL_2, 2);
        assertThat(auctionKeys).hasSize(1);
        assertThat(auctionKeys.get(0).getKeyIdentifier())
                .isEqualTo(ENCRYPTION_AUCTION_WITH_DIFF_COORDINATOR.getKeyIdentifier());
        assertThat(auctionKeys.get(0).getCoordinatorUrl()).isEqualTo(COORDINATOR_URL_2);
    }

    @Test
    public void test_getLatestExpiryNActiveKeyOfType_returnsEmptyWhenKeyAbsent() {
        assertThat(
                        mProtectedServersEncryptionConfigDao.getLatestExpiryNActiveKeys(
                                ENCRYPTION_KEY_TYPE_AUCTION, COORDINATOR_URL, Instant.now(), 1))
                .isEmpty();
        assertThat(
                        mProtectedServersEncryptionConfigDao.getLatestExpiryNActiveKeys(
                                ENCRYPTION_KEY_TYPE_JOIN, COORDINATOR_URL, Instant.now(), 1))
                .isEmpty();
        assertThat(
                        mProtectedServersEncryptionConfigDao.getLatestExpiryNActiveKeys(
                                ENCRYPTION_KEY_TYPE_QUERY, COORDINATOR_URL, Instant.now(), 1))
                .isEmpty();
    }

    @Test
    public void test_getLatestExpiryNActiveKeyOfType_returnsNFreshestKey() {
        mProtectedServersEncryptionConfigDao.insertKeys(
                ImmutableList.of(
                        ENCRYPTION_KEY_AUCTION,
                        ENCRYPTION_KEY_JOIN,
                        ENCRYPTION_KEY_QUERY,
                        ENCRYPTION_KEY_AUCTION_TTL_5SECS,
                        ENCRYPTION_KEY_JOIN_TTL_5SECS,
                        ENCRYPTION_KEY_QUERY_TTL_5SECS));

        Instant currentInstant = Instant.now().plusSeconds(5L);

        List<DBProtectedServersEncryptionConfig> auctionKeys =
                mProtectedServersEncryptionConfigDao.getLatestExpiryNActiveKeys(
                        ENCRYPTION_KEY_TYPE_AUCTION, COORDINATOR_URL, currentInstant, 2);
        assertThat(auctionKeys).hasSize(1);
        assertThat(auctionKeys.get(0).getKeyIdentifier())
                .isEqualTo(ENCRYPTION_KEY_AUCTION.getKeyIdentifier());

        List<DBProtectedServersEncryptionConfig> queryKeys =
                mProtectedServersEncryptionConfigDao.getLatestExpiryNActiveKeys(
                        ENCRYPTION_KEY_TYPE_QUERY, COORDINATOR_URL, currentInstant, 2);
        assertThat(queryKeys).hasSize(1);
        assertThat(queryKeys.get(0).getKeyIdentifier())
                .isEqualTo(ENCRYPTION_KEY_QUERY.getKeyIdentifier());

        List<DBProtectedServersEncryptionConfig> joinKeys =
                mProtectedServersEncryptionConfigDao.getLatestExpiryNActiveKeys(
                        ENCRYPTION_KEY_TYPE_JOIN, COORDINATOR_URL, currentInstant, 2);
        assertThat(joinKeys).hasSize(1);
        assertThat(joinKeys.get(0).getKeyIdentifier())
                .isEqualTo(ENCRYPTION_KEY_JOIN.getKeyIdentifier());
    }

    @Test
    public void test_getExpiredKeysForType_noExpiredKeys_returnsEmpty() {
        assertThat(
                        mProtectedServersEncryptionConfigDao.getExpiredKeys(
                                ENCRYPTION_KEY_TYPE_AUCTION, COORDINATOR_URL, Instant.now()))
                .isEmpty();
        assertThat(
                        mProtectedServersEncryptionConfigDao.getExpiredKeys(
                                ENCRYPTION_KEY_TYPE_JOIN, COORDINATOR_URL, Instant.now()))
                .isEmpty();
        assertThat(
                        mProtectedServersEncryptionConfigDao.getExpiredKeys(
                                ENCRYPTION_KEY_TYPE_QUERY, COORDINATOR_URL, Instant.now()))
                .isEmpty();
    }

    @Test
    public void test_getExpiredKeysForType_returnsExpiredKeys_success() {
        mProtectedServersEncryptionConfigDao.insertKeys(
                ImmutableList.of(
                        ENCRYPTION_KEY_AUCTION,
                        ENCRYPTION_KEY_JOIN,
                        ENCRYPTION_KEY_QUERY,
                        ENCRYPTION_KEY_AUCTION_TTL_5SECS,
                        ENCRYPTION_KEY_JOIN_TTL_5SECS,
                        ENCRYPTION_KEY_QUERY_TTL_5SECS));

        Instant currentInstant = Instant.now().plusSeconds(5L);
        List<DBProtectedServersEncryptionConfig> expiredAuctionKeys =
                mProtectedServersEncryptionConfigDao.getExpiredKeys(
                        ENCRYPTION_KEY_TYPE_AUCTION, COORDINATOR_URL, currentInstant);
        assertThat(expiredAuctionKeys.size()).isEqualTo(1);
        assertThat(expiredAuctionKeys.stream().findFirst().get().getKeyIdentifier())
                .isEqualTo(ENCRYPTION_KEY_AUCTION_TTL_5SECS.getKeyIdentifier());

        List<DBProtectedServersEncryptionConfig> expiredJoinKeys =
                mProtectedServersEncryptionConfigDao.getExpiredKeys(
                        ENCRYPTION_KEY_TYPE_JOIN, COORDINATOR_URL, currentInstant);
        assertThat(expiredJoinKeys.size()).isEqualTo(1);
        assertThat(expiredJoinKeys.stream().findFirst().get().getKeyIdentifier())
                .isEqualTo(ENCRYPTION_KEY_JOIN_TTL_5SECS.getKeyIdentifier());

        List<DBProtectedServersEncryptionConfig> expiredQueryKeys =
                mProtectedServersEncryptionConfigDao.getExpiredKeys(
                        ENCRYPTION_KEY_TYPE_QUERY, COORDINATOR_URL, currentInstant);
        assertThat(expiredQueryKeys.size()).isEqualTo(1);
        assertThat(expiredQueryKeys.stream().findFirst().get().getKeyIdentifier())
                .isEqualTo(ENCRYPTION_KEY_QUERY_TTL_5SECS.getKeyIdentifier());
    }

    @Test
    public void test_getExpiredKeys_noExpiredKeys_returnsEmpty() {
        mProtectedServersEncryptionConfigDao.insertKeys(
                ImmutableList.of(
                        ENCRYPTION_KEY_AUCTION, ENCRYPTION_KEY_JOIN, ENCRYPTION_KEY_QUERY));
        assertThat(mProtectedServersEncryptionConfigDao.getAllExpiredKeys(Instant.now())).isEmpty();
    }

    @Test
    public void test_getExpiredKeys_returnsExpiredKeys() {
        mProtectedServersEncryptionConfigDao.insertKeys(
                ImmutableList.of(
                        ENCRYPTION_KEY_AUCTION,
                        ENCRYPTION_KEY_JOIN,
                        ENCRYPTION_KEY_QUERY,
                        ENCRYPTION_KEY_AUCTION_TTL_5SECS,
                        ENCRYPTION_KEY_JOIN_TTL_5SECS,
                        ENCRYPTION_KEY_QUERY_TTL_5SECS));

        Instant currentInstant = Instant.now().plusSeconds(5L);
        List<DBProtectedServersEncryptionConfig> expiredKeys =
                mProtectedServersEncryptionConfigDao.getAllExpiredKeys(currentInstant);

        assertThat(expiredKeys.size()).isEqualTo(3);
        assertThat(expiredKeys.stream().map(k -> k.getKeyIdentifier()).collect(Collectors.toSet()))
                .containsExactly(
                        ENCRYPTION_KEY_AUCTION_TTL_5SECS.getKeyIdentifier(),
                        ENCRYPTION_KEY_JOIN_TTL_5SECS.getKeyIdentifier(),
                        ENCRYPTION_KEY_QUERY_TTL_5SECS.getKeyIdentifier());
    }

    @Test
    public void test_deleteExpiredKeys_noExpiredKeys_returnsZero() {
        mProtectedServersEncryptionConfigDao.insertKeys(ImmutableList.of(ENCRYPTION_KEY_AUCTION));
        assertThat(
                        mProtectedServersEncryptionConfigDao.deleteExpiredRows(
                                ENCRYPTION_KEY_TYPE_AUCTION, COORDINATOR_URL, Instant.now()))
                .isEqualTo(0);
    }

    @Test
    public void test_deleteExpiredKeys_deletesKeysSuccessfully() {
        mProtectedServersEncryptionConfigDao.insertKeys(
                ImmutableList.of(ENCRYPTION_KEY_JOIN, ENCRYPTION_KEY_AUCTION_TTL_5SECS));

        assertThat(
                        mProtectedServersEncryptionConfigDao.getLatestExpiryNKeys(
                                ENCRYPTION_KEY_TYPE_AUCTION, COORDINATOR_URL, 1))
                .isNotEmpty();

        mProtectedServersEncryptionConfigDao.deleteExpiredRows(
                ENCRYPTION_KEY_TYPE_AUCTION, COORDINATOR_URL, Instant.now().plusSeconds(10L));

        assertThat(
                        mProtectedServersEncryptionConfigDao.getLatestExpiryNKeys(
                                ENCRYPTION_KEY_TYPE_AUCTION, COORDINATOR_URL, 1))
                .isEmpty();
    }

    @Test
    public void test_insertAllKeys_validKeys_success() {
        assertThat(
                        mProtectedServersEncryptionConfigDao.getLatestExpiryNKeys(
                                ENCRYPTION_KEY_TYPE_AUCTION, COORDINATOR_URL, 1))
                .isEmpty();
        mProtectedServersEncryptionConfigDao.insertKeys(ImmutableList.of(ENCRYPTION_KEY_AUCTION));
        assertThat(
                        mProtectedServersEncryptionConfigDao.getLatestExpiryNKeys(
                                ENCRYPTION_KEY_TYPE_AUCTION, COORDINATOR_URL, 1))
                .isNotEmpty();
    }

    @Test
    public void test_insertKeys_duplicateRowId_olderEntryReplaced() {
        mProtectedServersEncryptionConfigDao.insertKeys(
                ImmutableList.of(
                        DBProtectedServersEncryptionConfig.builder()
                                .setRowId(1L)
                                .setCoordinatorUrl(COORDINATOR_URL)
                                .setKeyIdentifier("key_id_1")
                                .setPublicKey("public_key_1")
                                .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_AUCTION)
                                .setExpiryTtlSeconds(EXPIRY_TTL_SECONDS)
                                .build(),
                        DBProtectedServersEncryptionConfig.builder()
                                .setRowId(1L)
                                .setCoordinatorUrl(COORDINATOR_URL)
                                .setKeyIdentifier("key_id_2")
                                .setPublicKey("public_key_2")
                                .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_AUCTION)
                                .setExpiryTtlSeconds(EXPIRY_TTL_SECONDS)
                                .build()));
        assertThat(
                        mProtectedServersEncryptionConfigDao
                                .getLatestExpiryNKeys(
                                        ENCRYPTION_KEY_TYPE_AUCTION, COORDINATOR_URL, 2)
                                .size())
                .isEqualTo(1);

        // The insert conflict strategy is set to replace
        assertThat(
                        mProtectedServersEncryptionConfigDao
                                .getLatestExpiryNKeys(
                                        ENCRYPTION_KEY_TYPE_AUCTION, COORDINATOR_URL, 10)
                                .get(0)
                                .getPublicKey())
                .isEqualTo("public_key_2");
    }

    @Test
    public void test_insertKeys_uniqueRecordOverridden_olderEntryReplaced() {
        mProtectedServersEncryptionConfigDao.insertKeys(
                ImmutableList.of(
                        DBProtectedServersEncryptionConfig.builder()
                                .setCoordinatorUrl(COORDINATOR_URL)
                                .setKeyIdentifier("key_id_1")
                                .setPublicKey("public_key_1")
                                .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_AUCTION)
                                .setExpiryTtlSeconds(EXPIRY_TTL_SECONDS)
                                .build(),
                        DBProtectedServersEncryptionConfig.builder()
                                .setCoordinatorUrl(COORDINATOR_URL)
                                .setKeyIdentifier("key_id_1")
                                .setPublicKey("public_key_2")
                                .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_AUCTION)
                                .setExpiryTtlSeconds(EXPIRY_TTL_SECONDS)
                                .build()));
        assertThat(
                        mProtectedServersEncryptionConfigDao
                                .getLatestExpiryNKeys(
                                        ENCRYPTION_KEY_TYPE_AUCTION, COORDINATOR_URL, 2)
                                .size())
                .isEqualTo(1);

        // The insert conflict strategy is set to replace
        assertThat(
                        mProtectedServersEncryptionConfigDao
                                .getLatestExpiryNKeys(
                                        ENCRYPTION_KEY_TYPE_AUCTION, COORDINATOR_URL, 10)
                                .get(0)
                                .getPublicKey())
                .isEqualTo("public_key_2");
    }

    @Test
    public void test_deleteAllEncryptionKeys_success() {
        mProtectedServersEncryptionConfigDao.insertKeys(
                ImmutableList.of(
                        ENCRYPTION_KEY_AUCTION, ENCRYPTION_KEY_JOIN, ENCRYPTION_KEY_QUERY));
        assertThat(
                        mProtectedServersEncryptionConfigDao.getLatestExpiryNKeys(
                                ENCRYPTION_KEY_TYPE_AUCTION, COORDINATOR_URL, 1))
                .isNotEmpty();
        assertThat(
                        mProtectedServersEncryptionConfigDao.getLatestExpiryNKeys(
                                ENCRYPTION_KEY_TYPE_JOIN, COORDINATOR_URL, 1))
                .isNotEmpty();
        assertThat(
                        mProtectedServersEncryptionConfigDao.getLatestExpiryNKeys(
                                ENCRYPTION_KEY_TYPE_QUERY, COORDINATOR_URL, 1))
                .isNotEmpty();

        mProtectedServersEncryptionConfigDao.deleteAllEncryptionKeys();

        assertThat(
                        mProtectedServersEncryptionConfigDao.getLatestExpiryNKeys(
                                ENCRYPTION_KEY_TYPE_AUCTION, COORDINATOR_URL, 1))
                .isEmpty();
        assertThat(
                        mProtectedServersEncryptionConfigDao.getLatestExpiryNKeys(
                                ENCRYPTION_KEY_TYPE_JOIN, COORDINATOR_URL, 1))
                .isEmpty();
        assertThat(
                        mProtectedServersEncryptionConfigDao.getLatestExpiryNKeys(
                                ENCRYPTION_KEY_TYPE_QUERY, COORDINATOR_URL, 1))
                .isEmpty();
    }
}
