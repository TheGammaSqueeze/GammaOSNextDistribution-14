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

package com.android.adservices.data.kanon;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public class KAnonMessageDaoTest {

    private KAnonMessageDao mKAnonMessageDao;
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final String KANON_HASH_SET = "somehashset";
    private static final long AD_SELECTION_ID_1 = 1;
    private static final Instant INSTANT_1 = Instant.now();
    private static final Instant INSTANT_2 = Instant.now().plusSeconds(1234);
    private static final Instant INSTANT_3 = Instant.now().plusSeconds(14);
    private final DBKAnonMessage.Builder mDefaultDBKAnonMessageBuilder =
            DBKAnonMessage.builder()
                    .setKanonHashSet(KANON_HASH_SET)
                    .setAdSelectionId(AD_SELECTION_ID_1)
                    .setCorrespondingClientParametersExpiryInstant(INSTANT_1)
                    .setExpiryInstant(INSTANT_3)
                    .setCreatedAt(INSTANT_2)
                    .setStatus(KAnonMessageConstants.MessageStatus.SIGNED);

    @Before
    public void setup() {
        mKAnonMessageDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, KAnonDatabase.class)
                        .build()
                        .kAnonMessageDao();
    }

    @Test
    public void testGetNKAnonMessageWithStatus_withNoMessage_returnsEmptyList() {
        List<DBKAnonMessage> dbkAnonMessageList =
                mKAnonMessageDao.getNLatestKAnonMessagesWithStatus(
                        2, KAnonMessageConstants.MessageStatus.SIGNED);

        assertThat(dbkAnonMessageList).isEmpty();
    }

    @Test
    public void testGetNKAnonMessageWithStatus_fetchesMessagesWithStatus() {
        DBKAnonMessage message1 =
                mDefaultDBKAnonMessageBuilder
                        .setStatus(KAnonMessageConstants.MessageStatus.SIGNED)
                        .build();
        DBKAnonMessage message2 =
                mDefaultDBKAnonMessageBuilder
                        .setStatus(KAnonMessageConstants.MessageStatus.SIGNED)
                        .build();
        mKAnonMessageDao.insertAllKAnonMessages(List.of(message1, message2));

        List<DBKAnonMessage> fetchedDBKAnonMessagesList =
                mKAnonMessageDao.getNLatestKAnonMessagesWithStatus(
                        2, KAnonMessageConstants.MessageStatus.SIGNED);

        assertThat(fetchedDBKAnonMessagesList.size()).isEqualTo(2);
    }

    @Test
    public void testGetNKAnonMessageWithStatus_withDifferentStatus_fetchesCorrectMessages() {
        DBKAnonMessage message1 =
                mDefaultDBKAnonMessageBuilder
                        .setStatus(KAnonMessageConstants.MessageStatus.NOT_PROCESSED)
                        .build();
        DBKAnonMessage message2 =
                mDefaultDBKAnonMessageBuilder
                        .setStatus(KAnonMessageConstants.MessageStatus.SIGNED)
                        .build();
        mKAnonMessageDao.insertAllKAnonMessages(List.of(message1, message2));

        List<DBKAnonMessage> fetchedDBKAnonMessagesList =
                mKAnonMessageDao.getNLatestKAnonMessagesWithStatus(
                        2, KAnonMessageConstants.MessageStatus.NOT_PROCESSED);

        assertThat(fetchedDBKAnonMessagesList.size()).isEqualTo(1);
    }

    @Test
    public void testDeleteKAnonMessagesWithIds_deletesMessagesWithGivenIdList() {
        long messageIdToDelete = 1;
        long messageIdToDelete2 = 123;
        DBKAnonMessage message1 =
                mDefaultDBKAnonMessageBuilder
                        .setMessageId(messageIdToDelete)
                        .setStatus(KAnonMessageConstants.MessageStatus.NOT_PROCESSED)
                        .build();
        DBKAnonMessage message2 =
                mDefaultDBKAnonMessageBuilder
                        .setMessageId(messageIdToDelete2)
                        .setStatus(KAnonMessageConstants.MessageStatus.NOT_PROCESSED)
                        .build();
        mKAnonMessageDao.insertAllKAnonMessages(List.of(message1, message2));

        mKAnonMessageDao.deleteKAnonMessagesWithIds(List.of(messageIdToDelete, messageIdToDelete2));

        List<DBKAnonMessage> fetchedDBKAnonMessagesList =
                mKAnonMessageDao.getNLatestKAnonMessagesWithStatus(
                        2, KAnonMessageConstants.MessageStatus.NOT_PROCESSED);
        assertThat(fetchedDBKAnonMessagesList.size()).isEqualTo(0);
    }

    @Test
    public void testGetKAnonMessagesWithMessage_returnsTheCorrectMessage() {
        String searchText = "searchText";
        DBKAnonMessage message2 =
                mDefaultDBKAnonMessageBuilder
                        .setStatus(KAnonMessageConstants.MessageStatus.NOT_PROCESSED)
                        .build();
        DBKAnonMessage messageToSearch =
                mDefaultDBKAnonMessageBuilder
                        .setKanonHashSet(searchText)
                        .setStatus(KAnonMessageConstants.MessageStatus.NOT_PROCESSED)
                        .build();
        mKAnonMessageDao.insertAllKAnonMessages(List.of(messageToSearch, message2));

        List<DBKAnonMessage> fetchedDBKAnonMessages =
                mKAnonMessageDao.getKAnonMessagesWithMessage(searchText);

        assertThat(fetchedDBKAnonMessages.size()).isEqualTo(1);
        assertThat(fetchedDBKAnonMessages.get(0).getKanonHashSet()).isEqualTo(searchText);
    }

    @Test
    public void testDeleteAllMessages_deletesAllTheMessages() {
        DBKAnonMessage message1 = mDefaultDBKAnonMessageBuilder.build();
        DBKAnonMessage message2 = mDefaultDBKAnonMessageBuilder.build();
        mKAnonMessageDao.insertAllKAnonMessages(List.of(message1, message2));

        mKAnonMessageDao.deleteAllKAnonMessages();

        List<DBKAnonMessage> fetchedDBKAnonMessagesList =
                mKAnonMessageDao.getNLatestKAnonMessagesWithStatus(
                        2, KAnonMessageConstants.MessageStatus.SIGNED);
        assertThat(fetchedDBKAnonMessagesList.size()).isEqualTo(0);
    }

    @Test
    public void testUpdateMessagesStatus_updatesTheStatusCorrectly() {
        Long messageIdToUpdate = 1L;
        Long messageIdNotToUpdate = 2L;
        DBKAnonMessage message1 =
                mDefaultDBKAnonMessageBuilder
                        .setStatus(KAnonMessageConstants.MessageStatus.NOT_PROCESSED)
                        .setMessageId(messageIdToUpdate)
                        .build();
        DBKAnonMessage message2 =
                mDefaultDBKAnonMessageBuilder
                        .setMessageId(messageIdNotToUpdate)
                        .setStatus(KAnonMessageConstants.MessageStatus.NOT_PROCESSED)
                        .build();
        mKAnonMessageDao.insertAllKAnonMessages(List.of(message1, message2));

        mKAnonMessageDao.updateMessagesStatus(
                List.of(messageIdToUpdate), KAnonMessageConstants.MessageStatus.JOINED);

        List<DBKAnonMessage> fetchedMessagesWithJoinedStatus =
                mKAnonMessageDao.getNLatestKAnonMessagesWithStatus(
                        1, KAnonMessageConstants.MessageStatus.JOINED);
        List<DBKAnonMessage> fetchedMessagesWithNotProcessedStatus =
                mKAnonMessageDao.getNLatestKAnonMessagesWithStatus(
                        1, KAnonMessageConstants.MessageStatus.NOT_PROCESSED);
        assertThat(fetchedMessagesWithJoinedStatus.size()).isEqualTo(1);
        assertThat(fetchedMessagesWithJoinedStatus.get(0).getMessageId())
                .isEqualTo(messageIdToUpdate);
        assertThat(fetchedMessagesWithNotProcessedStatus.size()).isEqualTo(1);
        assertThat(fetchedMessagesWithNotProcessedStatus.get(0).getMessageId())
                .isEqualTo(messageIdNotToUpdate);
    }

    @Test
    public void testRemoveExpiredEntities_removesExpiredEntities() {
        Long messageIdToDelete1 = 1L;
        Long messageIdToDelete2 = 2L;
        Long messageIdToNotDelete1 = 3L;
        Long messageIdToNotDelete2 = 4L;
        Instant currentTime = Instant.now();
        // To be deleted because of expiry instant.
        DBKAnonMessage messageToDelete1 =
                DBKAnonMessage.builder()
                        .setKanonHashSet(KANON_HASH_SET)
                        .setAdSelectionId(AD_SELECTION_ID_1)
                        .setCreatedAt(INSTANT_2)
                        .setStatus(KAnonMessageConstants.MessageStatus.NOT_PROCESSED)
                        .setExpiryInstant(currentTime.minusSeconds(10))
                        .setMessageId(messageIdToDelete1)
                        .build();
        // To be deleted because of corresponding client params expiry instant
        DBKAnonMessage messageToDelete2 =
                DBKAnonMessage.builder()
                        .setKanonHashSet(KANON_HASH_SET)
                        .setAdSelectionId(AD_SELECTION_ID_1)
                        .setCreatedAt(INSTANT_2)
                        .setStatus(KAnonMessageConstants.MessageStatus.NOT_PROCESSED)
                        .setMessageId(messageIdToDelete2)
                        .setExpiryInstant(currentTime.plusSeconds(10))
                        .setCorrespondingClientParametersExpiryInstant(currentTime.minusSeconds(10))
                        .build();
        DBKAnonMessage messageToNotDelete1 =
                DBKAnonMessage.builder()
                        .setKanonHashSet(KANON_HASH_SET)
                        .setAdSelectionId(AD_SELECTION_ID_1)
                        .setCreatedAt(INSTANT_2)
                        .setStatus(KAnonMessageConstants.MessageStatus.NOT_PROCESSED)
                        .setExpiryInstant(currentTime.plusSeconds(10))
                        .setMessageId(messageIdToNotDelete1)
                        .build();
        DBKAnonMessage messageToNotDelete2 =
                DBKAnonMessage.builder()
                        .setKanonHashSet(KANON_HASH_SET)
                        .setAdSelectionId(AD_SELECTION_ID_1)
                        .setCreatedAt(INSTANT_2)
                        .setStatus(KAnonMessageConstants.MessageStatus.NOT_PROCESSED)
                        .setExpiryInstant(currentTime.plusSeconds(100))
                        .setMessageId(messageIdToNotDelete2)
                        .setCorrespondingClientParametersExpiryInstant(currentTime.plusSeconds(10))
                        .build();
        mKAnonMessageDao.insertAllKAnonMessages(
                List.of(
                        messageToDelete1,
                        messageToDelete2,
                        messageToNotDelete1,
                        messageToNotDelete2));

        mKAnonMessageDao.removeExpiredEntities(currentTime);

        List<DBKAnonMessage> fetchedMessagesWithNotProcessedStatus =
                mKAnonMessageDao.getNLatestKAnonMessagesWithStatus(
                        10, KAnonMessageConstants.MessageStatus.NOT_PROCESSED);
        List<Long> idsNotDeleted =
                fetchedMessagesWithNotProcessedStatus.stream()
                        .map(DBKAnonMessage::getMessageId)
                        .collect(Collectors.toList());

        assertThat(fetchedMessagesWithNotProcessedStatus.size()).isEqualTo(2);
        assertThat(idsNotDeleted).contains(messageIdToNotDelete1);
        assertThat(idsNotDeleted).contains(messageIdToNotDelete2);
    }

    @Test
    public void testInsertAllMessages_generatesAndReturnsMessageId() {
        DBKAnonMessage message1 = mDefaultDBKAnonMessageBuilder.build();

        long[] messageIds = mKAnonMessageDao.insertAllKAnonMessages(List.of(message1));

        List<DBKAnonMessage> fetchedDBKAnonMessagesList =
                mKAnonMessageDao.getNLatestKAnonMessagesWithStatus(
                        2, KAnonMessageConstants.MessageStatus.SIGNED);

        assertThat(messageIds.length).isEqualTo(1);
        assertThat(fetchedDBKAnonMessagesList.size()).isEqualTo(1);
        assertThat(fetchedDBKAnonMessagesList.get(0).getMessageId()).isEqualTo(messageIds[0]);
    }
}
