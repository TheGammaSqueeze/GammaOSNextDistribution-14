/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.topics;

import android.content.Context;

import com.android.adservices.data.topics.Topic;

import com.google.common.collect.ImmutableList;

import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Utility class providing static methods to map {@link Topic#getTopicId()} to Android resource
 * string (R). This is possible because the names of the topics are formatted: "topic{topicId}".
 *
 * <p>E.g. <string name="topic10001">/Arts &amp; Entertainment</string>
 */
public class TopicsMapper {

    /** @return a Android resource Id for provided {@link Topic} or 0 if it doesn't exist. */
    public static int getResourceIdByTopic(Topic topic, Context context) {
        return context.getResources()
                .getIdentifier(
                        String.format(Locale.ENGLISH, "topic%d", topic.getTopic()),
                        "string",
                        context.getPackageName());
    }

    /**
     * @return an {@link ImmutableList<Integer>} containing Android resource Ids generated from
     *     provided {@link ImmutableList<Topic>}.
     */
    public static ImmutableList<Integer> getResourcesIdMapByTopicsList(
            ImmutableList<Topic> topics, Context context) {
        return ImmutableList.copyOf(
                topics.stream()
                        .map(topic -> getResourceIdByTopic(topic, context))
                        .collect(Collectors.toList()));
    }
}
