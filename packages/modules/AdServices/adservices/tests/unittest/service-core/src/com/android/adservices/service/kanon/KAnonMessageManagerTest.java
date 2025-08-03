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

package com.android.adservices.service.kanon;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.kanon.DBKAnonMessage;
import com.android.adservices.data.kanon.KAnonDatabase;
import com.android.adservices.data.kanon.KAnonMessageConstants;
import com.android.adservices.data.kanon.KAnonMessageDao;
import com.android.adservices.service.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(MockitoJUnitRunner.class)
public class KAnonMessageManagerTest {
    private KAnonMessageDao mKAnonMessageDao;
    private Context mContext = ApplicationProvider.getApplicationContext();
    @Mock private Clock mockClock;
    @Mock private Flags mockFlags;
    private KAnonMessageManager mKAnonMessageManager;

    private static final Instant FIXED_TIME = Instant.now();
    private static final long AD_SELECTION_ID_1 = 1;
    private static final long AD_SELECTION_ID_2 = 2;
    private static final long AD_SELECTION_ID_3 = 3;
    private static final String HASH_SET_1 = "somestring";
    private static final String HASH_SET_2 = "somestring2";
    private static final String HASH_SET_3 = "somestring3";
    private final KAnonMessageEntity mKAnonMessageEntity =
            KAnonMessageEntity.builder()
                    .setAdSelectionId(AD_SELECTION_ID_1)
                    .setHashSet(HASH_SET_1)
                    .setStatus(KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED)
                    .build();
    private final KAnonMessageEntity mKAnonMessageEntity2 =
            KAnonMessageEntity.builder()
                    .setAdSelectionId(AD_SELECTION_ID_2)
                    .setHashSet(HASH_SET_2)
                    .setStatus(KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED)
                    .build();
    private final KAnonMessageEntity mKAnonMessageEntity3 =
            KAnonMessageEntity.builder()
                    .setAdSelectionId(AD_SELECTION_ID_3)
                    .setHashSet(HASH_SET_3)
                    .setStatus(KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED)
                    .build();
    private final DBKAnonMessage mDbkAnonMessage1 =
            DBKAnonMessage.builder()
                    .setKanonHashSet(HASH_SET_1)
                    .setAdSelectionId(AD_SELECTION_ID_1)
                    .setCorrespondingClientParametersExpiryInstant(FIXED_TIME)
                    .setExpiryInstant(FIXED_TIME)
                    .setCreatedAt(FIXED_TIME)
                    .setStatus(KAnonMessageConstants.MessageStatus.NOT_PROCESSED)
                    .build();
    private final DBKAnonMessage mDbkAnonMessage2 =
            DBKAnonMessage.builder()
                    .setKanonHashSet(HASH_SET_2)
                    .setAdSelectionId(AD_SELECTION_ID_2)
                    .setCorrespondingClientParametersExpiryInstant(FIXED_TIME)
                    .setExpiryInstant(FIXED_TIME)
                    .setCreatedAt(FIXED_TIME)
                    .setStatus(KAnonMessageConstants.MessageStatus.NOT_PROCESSED)
                    .build();
    private final DBKAnonMessage mDbkAnonMessage3 =
            DBKAnonMessage.builder()
                    .setKanonHashSet(HASH_SET_3)
                    .setAdSelectionId(AD_SELECTION_ID_3)
                    .setCorrespondingClientParametersExpiryInstant(FIXED_TIME)
                    .setExpiryInstant(FIXED_TIME)
                    .setCreatedAt(FIXED_TIME)
                    .setStatus(KAnonMessageConstants.MessageStatus.SIGNED)
                    .build();

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() {
        mKAnonMessageDao =
                Room.inMemoryDatabaseBuilder(mContext, KAnonDatabase.class)
                        .build()
                        .kAnonMessageDao();
        when(mockClock.instant()).thenReturn(FIXED_TIME);
        mKAnonMessageManager = new KAnonMessageManager(mKAnonMessageDao, mockFlags, mockClock);
    }

    @Test
    public void testPersistNewAnonMessageEntities_shouldPersistSuccessfully() {
        long secondsTtl = 100;
        Instant fixedInstant = Instant.now();
        when(mockClock.instant()).thenReturn(fixedInstant);
        when(mockFlags.getFledgeKAnonMessageTtlSeconds()).thenReturn(secondsTtl);
        mKAnonMessageManager = new KAnonMessageManager(mKAnonMessageDao, mockFlags, mockClock);
        mKAnonMessageManager.persistNewAnonMessageEntities(List.of(mKAnonMessageEntity));

        List<DBKAnonMessage> dbkAnonMessages =
                mKAnonMessageDao.getNLatestKAnonMessagesWithStatus(
                        5, KAnonMessageConstants.MessageStatus.NOT_PROCESSED);

        assertThat(dbkAnonMessages.size()).isEqualTo(1);
        assertThat(dbkAnonMessages.get(0).getKanonHashSet())
                .isEqualTo(mKAnonMessageEntity.getHashSet());
        assertThat(dbkAnonMessages.get(0).getAdSelectionId())
                .isEqualTo(mKAnonMessageEntity.getAdSelectionId());
    }

    @Test
    public void testFetchNKAnonMessagesWithStatus_shouldFetchListOfCorrectMessages() {
        mKAnonMessageDao.insertAllKAnonMessages(
                List.of(mDbkAnonMessage1, mDbkAnonMessage2, mDbkAnonMessage3));

        List<KAnonMessageEntity> fetchedKAnonEntities =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        5, KAnonMessageConstants.MessageStatus.NOT_PROCESSED);

        List<Long> fetchedAdSelectionIds =
                fetchedKAnonEntities.stream()
                        .map(KAnonMessageEntity::getAdSelectionId)
                        .collect(Collectors.toList());
        assertThat(AD_SELECTION_ID_1).isIn(fetchedAdSelectionIds);
        assertThat(AD_SELECTION_ID_2).isIn(fetchedAdSelectionIds);
    }

    @Test
    public void testFetchKAnonMessagesWithMessage_shouldFetchCorrectMessage() {
        mKAnonMessageDao.insertAllKAnonMessages(
                List.of(mDbkAnonMessage1, mDbkAnonMessage2, mDbkAnonMessage3));

        List<KAnonMessageEntity> fetchedKAnonMessage =
                mKAnonMessageManager.fetchKAnonMessageEntityWithMessage(HASH_SET_1);

        assertThat(fetchedKAnonMessage.size()).isEqualTo(1);
        assertThat(fetchedKAnonMessage.get(0).getAdSelectionId()).isEqualTo(AD_SELECTION_ID_1);
    }

    @Test
    public void testFetchNKAnonMessagesWithStatus_withNoCorrectMessage_shouldReturnEmptyList() {
        mKAnonMessageDao.insertAllKAnonMessages(
                List.of(mDbkAnonMessage1, mDbkAnonMessage2, mDbkAnonMessage3));

        // searching messages with JOINED status.
        List<KAnonMessageEntity> fetchedKAnonEntities =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        5, KAnonMessageConstants.MessageStatus.JOINED);

        assertThat(fetchedKAnonEntities).isEmpty();
    }

    @Test
    public void testFetchKAnonMessagesWithMessage_withNoMessagePresent_shouldReturnNull() {
        mKAnonMessageDao.insertAllKAnonMessages(
                List.of(mDbkAnonMessage1, mDbkAnonMessage2, mDbkAnonMessage3));

        List<KAnonMessageEntity> fetchedKAnonMessage =
                mKAnonMessageManager.fetchKAnonMessageEntityWithMessage("unsearchablestring");

        assertThat(fetchedKAnonMessage).isEmpty();
    }

    @Test
    public void testPersistNewAnonMessageEntities_messageWithUnsetId_shouldHaveIdGenerated() {
        List<KAnonMessageEntity> insertedEntries =
                mKAnonMessageManager.persistNewAnonMessageEntities(List.of(mKAnonMessageEntity));

        List<DBKAnonMessage> dbkAnonMessages =
                mKAnonMessageDao.getNLatestKAnonMessagesWithStatus(
                        5, KAnonMessageConstants.MessageStatus.NOT_PROCESSED);

        assertThat(insertedEntries.size()).isEqualTo(1);
        assertThat(dbkAnonMessages.size()).isEqualTo(1);
        assertThat(dbkAnonMessages.get(0).getMessageId())
                .isEqualTo(insertedEntries.get(0).getMessageId());
    }

    @Test
    public void persistNewAnonMessageEntities_multipleMessages_returnsEntriesWithCorrectId() {
        List<KAnonMessageEntity> insertedEntries =
                mKAnonMessageManager.persistNewAnonMessageEntities(
                        List.of(mKAnonMessageEntity, mKAnonMessageEntity2, mKAnonMessageEntity3));

        List<KAnonMessageEntity> messageEntitiesFromDB =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        10, KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED);

        assertThat(insertedEntries.size()).isEqualTo(3);
        assertThat(messageEntitiesFromDB.size()).isEqualTo(3);
        for (KAnonMessageEntity entityInDB : messageEntitiesFromDB) {
            String hashSet = entityInDB.getHashSet();
            List<KAnonMessageEntity> entityWithSameHashSet =
                    insertedEntries.stream()
                            .filter(message -> message.getHashSet().equals(hashSet))
                            .collect(Collectors.toList());
            assertThat(entityWithSameHashSet.size()).isEqualTo(1);
            assertThat(entityWithSameHashSet.get(0).getMessageId())
                    .isEqualTo(entityInDB.getMessageId());
        }
    }

    @Test
    public void testPersistNewAnonMessageEntities_ttlIsPickedUpFromDatabase() {
        long secondsTtl = 100;
        Instant fixedInstant = Instant.now();
        when(mockClock.instant()).thenReturn(fixedInstant);
        when(mockFlags.getFledgeKAnonMessageTtlSeconds()).thenReturn(secondsTtl);
        mKAnonMessageManager = new KAnonMessageManager(mKAnonMessageDao, mockFlags, mockClock);

        mKAnonMessageManager.persistNewAnonMessageEntities(List.of(mKAnonMessageEntity));

        List<DBKAnonMessage> dbkAnonMessages =
                mKAnonMessageDao.getNLatestKAnonMessagesWithStatus(
                        5, KAnonMessageConstants.MessageStatus.NOT_PROCESSED);

        assertThat(dbkAnonMessages.size()).isEqualTo(1);
        assertThat(dbkAnonMessages.get(0).getExpiryInstant().toEpochMilli())
                .isEqualTo(fixedInstant.plusSeconds(secondsTtl).toEpochMilli());
    }
}
