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

package com.android.adservices.cobalt;

import com.android.adservices.data.topics.Topic;
import com.android.cobalt.CobaltLogger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Range;

import java.util.List;

/** Wrapper around {@link CobaltLogger} that logs {@link Topic} occurrences to Cobalt. */
public final class TopicsCobaltLogger {
    // The topics occurrence count metric has an id of 1.
    //
    // See //packages/modules/AdServices/adservices/service-core/resources/cobalt_registry.textpb
    // for the full metric.
    private static final int METRIC_ID = 1;

    // Cobalt supports logging occurrence counts for up to 500 topics.
    //
    // Note, this assumes topics increase monotonically, which is the case for topics 10001 to
    // 10446, and is considered acceptable to allow for easy mapping between topics and event codes,
    // plus growth in the topics list. However, an explicit map will need to be created in the
    // future if topics are no longer monotonic.
    //
    // See //packages/modules/AdServices/adservices/service-core/resources/cobalt_registry.textpb
    // for the full list of topics.
    private static final Range<Integer> LOGGABLE_TOPICS_RANGE = Range.closed(10001, 10500);

    // Each topic has an event code which is equal to its value, minus 10000, e.g. topic 10001 has
    // an event code of 1.
    private static final int TOPICS_OFFSET = 10000;

    // 0 is the event code for an unknown topic.
    private static final int UNKNOWN_TOPIC = 0;

    private final CobaltLogger mCobaltLogger;

    public TopicsCobaltLogger(CobaltLogger cobaltLogger) {
        this.mCobaltLogger = cobaltLogger;
    }

    /** Log a list of {@link Topic}s to Cobalt. */
    public void logTopicOccurrences(List<Topic> topics) {
        for (Topic topic : topics) {
            logTopicOccurrences(topic, /* count= */ 1);
        }
    }

    /**
     * Log a {@link Topic} as occurring once to Cobalt.
     *
     * @param topic the {@link Topic} which occurred
     */
    public void logTopicOccurrence(Topic topic) {
        logTopicOccurrences(topic, /* count= */ 1);
    }

    /**
     * Log a {@link Topic} as occurring at least once to Cobalt.
     *
     * @param topic the {@link Topic} which occurred
     */
    public void logTopicOccurrences(Topic topic, int count) {
        if (count < 1) {
            return;
        }
        int eventCode =
                LOGGABLE_TOPICS_RANGE.contains(topic.getTopic())
                        ? topic.getTopic() - TOPICS_OFFSET
                        : UNKNOWN_TOPIC;

        // Ignore the returned future because occurrence logging does not need to block.
        mCobaltLogger.logOccurrence(METRIC_ID, count, ImmutableList.of(eventCode));
    }
}
