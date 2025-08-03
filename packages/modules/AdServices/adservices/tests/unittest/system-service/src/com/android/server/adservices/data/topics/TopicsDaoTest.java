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

package com.android.server.adservices.data.topics;

import static com.android.server.adservices.data.topics.TopicsTables.DUMMY_MODEL_VERSION;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.adservices.topics.Topic;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import java.io.PrintWriter;
import java.util.List;
import java.util.Set;

/** Unit test to test class {@link com.android.server.adservices.data.topics.TopicsDao} */
public class TopicsDaoTest {
    private static final long TAXONOMY_VERSION = 1L;

    private final TopicsDbHelper mTopicsDBHelper = TopicsDbTestUtil.getDbHelperForTest();
    private final TopicsDao mTopicsDao = new TopicsDao(mTopicsDBHelper);

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @After
    public void tearDown() {
        // Erase all existing data.
        TopicsDbTestUtil.deleteTable(TopicsTables.BlockedTopicsContract.TABLE);
    }

    @Test
    public void testRecordBlockedTopicAndRetrieveBlockedTopics() {
        final int topicId = 1;
        final int user0 = 0;
        final int user1 = 1;
        final int user2 = 2;
        Topic topicToBlock = new Topic(TAXONOMY_VERSION, DUMMY_MODEL_VERSION, topicId);
        mTopicsDao.recordBlockedTopic(List.of(topicToBlock), user0);

        Set<Topic> blockedTopics = mTopicsDao.retrieveAllBlockedTopics(user0);

        // User 0 should have 1 blocked topic
        assertThat(blockedTopics).hasSize(1);
        assertThat(blockedTopics).containsExactly(topicToBlock);

        // User 1 should have no blocked topic.
        assertThat(mTopicsDao.retrieveAllBlockedTopics(user1)).isEmpty();

        // Non-existent user should have no blocked topics.
        assertThat(mTopicsDao.retrieveAllBlockedTopics(user2)).isEmpty();
    }

    @Test
    public void testRecordBlockedTopicAndRetrieveBlockedTopics_duplicateTopics() {
        final int topicId = 1;
        final int user = 0;
        Topic topicToBlock = new Topic(TAXONOMY_VERSION, DUMMY_MODEL_VERSION, topicId);
        // Persist same topic in 1 invocation and in multiple invocations
        mTopicsDao.recordBlockedTopic(List.of(topicToBlock, topicToBlock), user);
        mTopicsDao.recordBlockedTopic(List.of(topicToBlock), user);

        Set<Topic> blockedTopics = mTopicsDao.retrieveAllBlockedTopics(user);

        // User should have 1 blocked topic
        assertThat(blockedTopics).hasSize(1);
        assertThat(blockedTopics).containsExactly(topicToBlock);
    }

    @Test
    public void testRecordBlockedTopicAndRemoveBlockedTopic() {
        final int topicId = 1;
        final int user0 = 0;
        final int user1 = 1;
        Topic topicToBlock = new Topic(TAXONOMY_VERSION, DUMMY_MODEL_VERSION, topicId);
        mTopicsDao.recordBlockedTopic(List.of(topicToBlock), user0);
        mTopicsDao.recordBlockedTopic(List.of(topicToBlock), user1);

        Set<Topic> blockedTopics0 = mTopicsDao.retrieveAllBlockedTopics(user0);
        Set<Topic> blockedTopics1 = mTopicsDao.retrieveAllBlockedTopics(user1);

        // Make sure that what we write to db is equal to what we read from db.
        assertThat(blockedTopics0).hasSize(1);
        assertThat(blockedTopics0).containsExactly(topicToBlock);
        assertThat(blockedTopics1).hasSize(1);
        assertThat(blockedTopics1).containsExactly(topicToBlock);

        mTopicsDao.removeBlockedTopic(topicToBlock, user0);

        // User 0 should have no blocked topics and User 1 should still have 1 blocked topic
        assertThat(mTopicsDao.retrieveAllBlockedTopics(user0)).isEmpty();
        assertThat(blockedTopics1).hasSize(1);
        assertThat(blockedTopics1).containsExactly(topicToBlock);
    }

    @Test
    public void testRecordBlockedTopicAndRemoveBlockedTopic_duplicateTopics() {
        final int topicId = 1;
        final int user = 0;
        Topic topicToBlock = new Topic(TAXONOMY_VERSION, DUMMY_MODEL_VERSION, topicId);
        mTopicsDao.recordBlockedTopic(List.of(topicToBlock, topicToBlock), user);
        mTopicsDao.recordBlockedTopic(List.of(topicToBlock), user);

        Set<Topic> blockedTopics0 = mTopicsDao.retrieveAllBlockedTopics(user);

        // Make sure that what we write to db is equal to what we read from db.
        assertThat(blockedTopics0).hasSize(1);
        assertThat(blockedTopics0).containsExactly(topicToBlock);

        mTopicsDao.removeBlockedTopic(topicToBlock, user);
        // User 0 should have no blocked topics and User 1 should still have 1 blocked topic
        assertThat(mTopicsDao.retrieveAllBlockedTopics(user)).isEmpty();

        // Nothing would happen to remove the same topic twice
        mTopicsDao.removeBlockedTopic(topicToBlock, user);
        assertThat(mTopicsDao.retrieveAllBlockedTopics(user)).isEmpty();
    }

    @Test
    public void testRecordBlockedTopicAndRemoveBlockedTopic_notExistedTopic() {
        final int topicId = 1;
        final int user = 0;
        Topic topicToBlock = new Topic(TAXONOMY_VERSION, DUMMY_MODEL_VERSION, topicId);

        assertThat(mTopicsDao.retrieveAllBlockedTopics(user)).isEmpty();

        // Nothing would happen to remove a non-existent topic.
        mTopicsDao.removeBlockedTopic(topicToBlock, user);
        assertThat(mTopicsDao.retrieveAllBlockedTopics(user)).isEmpty();
    }

    @Test
    public void testClearAllBlockedTopicsOfUser() {
        final int topicId1 = 1;
        final int topicId2 = 2;
        final int user0 = 0;
        final int user1 = 1;
        Topic topicToBlock1 = new Topic(TAXONOMY_VERSION, DUMMY_MODEL_VERSION, topicId1);
        Topic topicToBlock2 = new Topic(TAXONOMY_VERSION, DUMMY_MODEL_VERSION, topicId2);
        mTopicsDao.recordBlockedTopic(List.of(topicToBlock1), user0);
        mTopicsDao.recordBlockedTopic(List.of(topicToBlock2), user0);
        mTopicsDao.recordBlockedTopic(List.of(topicToBlock1), user1);

        Set<Topic> blockedTopics0 = mTopicsDao.retrieveAllBlockedTopics(user0);
        Set<Topic> blockedTopics1 = mTopicsDao.retrieveAllBlockedTopics(user1);

        // Make sure that what we write to db is equal to what we read from db.
        assertThat(blockedTopics0).hasSize(2);
        assertThat(blockedTopics0).containsExactly(topicToBlock1, topicToBlock2);
        assertThat(blockedTopics1).hasSize(1);
        assertThat(blockedTopics1).containsExactly(topicToBlock1);

        mTopicsDao.clearAllBlockedTopicsOfUser(user0);

        // User 0 should have no blocked topics and User 1 should still have 1 blocked topic
        assertThat(mTopicsDao.retrieveAllBlockedTopics(user0)).isEmpty();
        assertThat(blockedTopics1).hasSize(1);
        assertThat(blockedTopics1).containsExactly(topicToBlock1);
    }

    @Test
    public void testDump() {
        // Currently, TopicsDao just delegates to helper, so we're being pragmactic and just
        // testing that
        PrintWriter writer = mock(PrintWriter.class);
        String prefix = "TOPICS DAO";
        String[] args = new String[] {"Y", "U", "NO", "FAKE?"};
        TopicsDbHelper mockTopicsDbHelper = mock(TopicsDbHelper.class);
        TopicsDao topicsDao = new TopicsDao(mockTopicsDbHelper);

        topicsDao.dump(writer, prefix, args);

        verify(mockTopicsDbHelper).dump(writer, prefix, args);
    }
}
