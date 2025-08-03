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

import android.adservices.topics.Topic;
import android.annotation.NonNull;
import android.app.adservices.topics.TopicParcel;

import com.android.server.adservices.data.topics.TopicsDao;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Class to manage blocked topics
 *
 * @hide
 */
public class BlockedTopicsManager {
    private final TopicsDao mTopicsDao;
    private final int mUserIdentifier;

    public BlockedTopicsManager(@NonNull TopicsDao topicsDao, int userIdentifier) {
        Objects.requireNonNull(topicsDao);

        mTopicsDao = topicsDao;
        mUserIdentifier = userIdentifier;
    }

    /**
     * Record a {@code List} of blocked topics.
     *
     * @param blockedTopicParcels the blocked topics to record
     */
    public void recordBlockedTopic(@NonNull List<TopicParcel> blockedTopicParcels) {
        List<Topic> blockedTopics =
                blockedTopicParcels.stream()
                        .map(this::convertTopicParcelToTopic)
                        .collect(Collectors.toList());
        mTopicsDao.recordBlockedTopic(blockedTopics, mUserIdentifier);
    }

    /**
     * Remove a blocked topic.
     *
     * @param blockedTopicParcel the blocked topic to remove
     */
    public void removeBlockedTopic(@NonNull TopicParcel blockedTopicParcel) {
        Topic blockedTopic = convertTopicParcelToTopic(blockedTopicParcel);
        mTopicsDao.removeBlockedTopic(blockedTopic, mUserIdentifier);
    }

    /**
     * Get all blocked topics.
     *
     * @return a {@code List} of all blocked topics.
     */
    public List<TopicParcel> retrieveAllBlockedTopics() {
        List<TopicParcel> topicParcelList = new ArrayList<>();
        for (Topic topic : mTopicsDao.retrieveAllBlockedTopics(mUserIdentifier)) {
            topicParcelList.add(
                    new TopicParcel.Builder()
                            .setModelVersion(topic.getModelVersion())
                            .setTaxonomyVersion(topic.getTaxonomyVersion())
                            .setTopicId(topic.getTopicId())
                            .build());
        }

        return topicParcelList;
    }

    /** Clear all BlockedTopics */
    public void clearAllBlockedTopics() {
        mTopicsDao.clearAllBlockedTopicsOfUser(mUserIdentifier);
    }

    private Topic convertTopicParcelToTopic(@NonNull TopicParcel topicParcel) {
        return new Topic(
                topicParcel.getTaxonomyVersion(),
                topicParcel.getModelVersion(),
                topicParcel.getTopicId());
    }

    /** Dumps its internal state. */
    public void dump(PrintWriter writer, String prefix) {
        writer.printf("%sBlockedTopicsManager:\n", prefix);
        String prefix2 = prefix + "  ";

        // NOTE: don't need to dump userId and mTopicsDao as these are already dumped by up in the
        // call stack

        List<TopicParcel> blockedTopics = retrieveAllBlockedTopics();
        int size = blockedTopics.size();
        writer.printf("%s%d blocked topics\n", prefix2, size);

        // TODO(b/299942046): dump the blocked topics themselves
    }
}
