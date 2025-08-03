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

package com.android.server.adservices;

import static com.android.adservices.shared.testing.common.DumpHelper.assertDumpHasPrefix;
import static com.android.adservices.shared.testing.common.DumpHelper.dump;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.adservices.topics.TopicParcel;
import android.os.Build;

import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.server.adservices.data.topics.TopicsDao;
import com.android.server.adservices.data.topics.TopicsDbHelper;
import com.android.server.adservices.data.topics.TopicsDbTestUtil;
import com.android.server.adservices.data.topics.TopicsTables;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

/** Class to test {@link com.android.server.adservices.BlockedTopicsManager} */
public class BlockedTopicsManagerTest {
    private static final long TAXONOMY_VERSION = 1L;
    private static final long MODEL_VERSION = 1L;

    private static final int USER_ID = 0;

    private BlockedTopicsManager mBlockedTopicsManager;

    @Rule
    public final AdServicesExtendedMockitoRule extendedMockitoRule =
            new AdServicesExtendedMockitoRule.Builder().spyStatic(Build.class).build();

    @Before
    public void setup() {
        TopicsDbHelper mDBHelper = TopicsDbTestUtil.getDbHelperForTest();
        mBlockedTopicsManager = new BlockedTopicsManager(new TopicsDao(mDBHelper), USER_ID);
    }

    @After
    public void tearDown() {
        // Clear BlockedTopics table in the database.
        TopicsDbTestUtil.deleteTable(TopicsTables.BlockedTopicsContract.TABLE);
    }

    @Test
    public void testRecordAndRetrieveBlockedTopic() {
        final int topicId = 1;

        TopicParcel topicParcel =
                new TopicParcel.Builder()
                        .setTopicId(topicId)
                        .setTaxonomyVersion(TAXONOMY_VERSION)
                        .setModelVersion(MODEL_VERSION)
                        .build();

        mBlockedTopicsManager.recordBlockedTopic(List.of(topicParcel));
        //  Verify the topic is recorded.
        List<TopicParcel> resultTopicParcels = mBlockedTopicsManager.retrieveAllBlockedTopics();
        assertThat(resultTopicParcels).hasSize(1);
        assertThat(resultTopicParcels.get(0)).isEqualTo(topicParcel);
    }

    @Test
    public void testRecordAndRemoveBlockedTopic() {
        final int topicId = 1;

        TopicParcel topicParcel =
                new TopicParcel.Builder()
                        .setTopicId(topicId)
                        .setTaxonomyVersion(TAXONOMY_VERSION)
                        .setModelVersion(MODEL_VERSION)
                        .build();
        mBlockedTopicsManager.recordBlockedTopic(List.of(topicParcel));

        //  Verify the topic is recorded.
        List<TopicParcel> resultTopicParcels = mBlockedTopicsManager.retrieveAllBlockedTopics();
        assertThat(resultTopicParcels).hasSize(1);
        assertThat(resultTopicParcels.get(0)).isEqualTo(topicParcel);

        // Verify the topic is  removed
        mBlockedTopicsManager.removeBlockedTopic(topicParcel);
        assertThat(mBlockedTopicsManager.retrieveAllBlockedTopics()).isEmpty();
    }

    @Test
    public void testClearAllBlockedTopics() {
        final int topicId1 = 1;
        final int topicId2 = 2;

        TopicParcel topicParcel1 =
                new TopicParcel.Builder()
                        .setTopicId(topicId1)
                        .setTaxonomyVersion(TAXONOMY_VERSION)
                        .setModelVersion(MODEL_VERSION)
                        .build();
        TopicParcel topicParcel2 =
                new TopicParcel.Builder()
                        .setTopicId(topicId2)
                        .setTaxonomyVersion(TAXONOMY_VERSION)
                        .setModelVersion(MODEL_VERSION)
                        .build();
        List<TopicParcel> topicParcelList = List.of(topicParcel1, topicParcel2);
        mBlockedTopicsManager.recordBlockedTopic(topicParcelList);

        //  Verify the topic is recorded.
        assertThat(mBlockedTopicsManager.retrieveAllBlockedTopics()).isEqualTo(topicParcelList);

        // Verify the topic is  removed
        mBlockedTopicsManager.clearAllBlockedTopics();
        assertThat(mBlockedTopicsManager.retrieveAllBlockedTopics()).isEmpty();
    }

    @Test
    public void testDump_noBlockedTopics() throws Exception {
        String prefix = "_";

        String dump = dump(pw -> mBlockedTopicsManager.dump(pw, prefix));

        assertDumpHasPrefix(dump, prefix);
        assertWithMessage("content of dump()").that(dump).contains("0 blocked topics\n");
    }

    @Test
    public void testDump_blockedTopics() throws Exception {
        String prefix = "_";
        int topicId = 1;
        TopicParcel topicParcel =
                new TopicParcel.Builder()
                        .setTopicId(topicId)
                        .setTaxonomyVersion(TAXONOMY_VERSION)
                        .setModelVersion(MODEL_VERSION)
                        .build();

        mBlockedTopicsManager.recordBlockedTopic(List.of(topicParcel));

        String dump = dump(pw -> mBlockedTopicsManager.dump(pw, prefix));

        assertWithMessage("content of dump()")
                .that(dump)
                .startsWith(prefix + "BlockedTopicsManager:");
        assertDumpHasPrefix(dump, prefix);
        assertWithMessage("content of dump()").that(dump).contains("1 blocked topics\n");
        assertWithMessage("content of dump() (blockedtopic)")
                .that(dump)
                .doesNotContainMatch(
                        ".*id.*"
                                + topicId
                                + ".*model.*"
                                + MODEL_VERSION
                                + ".*taxonomy.*"
                                + TAXONOMY_VERSION
                                + ".*");
    }
}
