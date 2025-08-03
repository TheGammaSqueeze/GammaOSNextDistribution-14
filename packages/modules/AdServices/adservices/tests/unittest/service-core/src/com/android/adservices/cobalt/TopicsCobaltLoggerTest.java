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

package com.android.adservices.cobalt;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.topics.Topic;
import com.android.cobalt.CobaltLogger;
import com.android.cobalt.domain.Project;

import com.google.cobalt.MetricDefinition;
import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

@SmallTest
public final class TopicsCobaltLoggerTest {
    private static final int COUNT = 5;

    private static final int METRIC_ID = 1;
    private static final int UNKNOWN_TOPIC_EVENT_CODE = 0;
    private static final int FIRST_EVENT_CODE = 1;
    private static final int FIRST_TOPIC = 10001;
    private static final int SUPPORTED_TOPICS_COUNT = 500;
    private static final int LAST_TOPIC = FIRST_TOPIC + SUPPORTED_TOPICS_COUNT - 1;
    private static final int LOGGED_TOPICS_COUNT = 10;

    private static final Context sContext = ApplicationProvider.getApplicationContext();

    @Mock private CobaltLogger mMockCobaltLogger;
    private TopicsCobaltLogger mTopicsCobaltLogger;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mTopicsCobaltLogger = new TopicsCobaltLogger(mMockCobaltLogger);
    }

    @Test
    public void expectationsMatchRegistryValues() throws Exception {
        // Parse the actual Cobalt registry for AdServices to ensure to topics logger's assumptions
        // are compatible with what is actually in the registry.
        //
        // See
        // //packages/modules/AdServices/adservices/service-core/resources/cobalt_registry.textpb
        // for the actual registy.
        Project cobaltRegistry = CobaltRegistryLoader.getRegistry(sContext);
        MetricDefinition topicsMetric =
                cobaltRegistry.getMetrics().stream()
                        .filter(m -> m.getMetricName().equals("returned_topics"))
                        .findFirst()
                        .orElseThrow();
        assertThat(topicsMetric.getMetricDimensionsCount()).isAtLeast(1);
        assertThat(topicsMetric.getMetricDimensions(0).getDimension()).isEqualTo("topic");
        assertThat(topicsMetric.getId()).isEqualTo(METRIC_ID);
        Map<Integer, String> eventCodes = topicsMetric.getMetricDimensions(0).getEventCodes();
        int unknownTopicEventCode =
                eventCodes.entrySet().stream()
                        .filter(e -> e.getValue().equals("UNKNOWN"))
                        .map(e -> e.getKey())
                        .findFirst()
                        .orElseThrow();
        int firstEventCode =
                eventCodes.entrySet().stream()
                        .mapToInt(Map.Entry::getKey)
                        .filter(i -> i != UNKNOWN_TOPIC_EVENT_CODE)
                        .min()
                        .orElseThrow();

        assertThat(unknownTopicEventCode).isEqualTo(UNKNOWN_TOPIC_EVENT_CODE);
        assertThat(firstEventCode).isEqualTo(FIRST_EVENT_CODE);
        assertThat(topicsMetric.getMetricDimensions(0).getMaxEventCode())
                .isEqualTo(SUPPORTED_TOPICS_COUNT);
    }

    @Test
    public void logTopicOccurrence_topicTooLow_logsUnknown() throws Exception {
        when(mMockCobaltLogger.logOccurrence(anyLong(), anyLong(), any())).thenReturn(null);
        mTopicsCobaltLogger.logTopicOccurrence(createTopic(FIRST_TOPIC - 1));
        verify(mMockCobaltLogger)
                .logOccurrence(
                        METRIC_ID, /* count= */ 1, ImmutableList.of(UNKNOWN_TOPIC_EVENT_CODE));
    }

    @Test
    public void logTopicOccurrence_topicTooHigh_logsUnknown() throws Exception {
        when(mMockCobaltLogger.logOccurrence(anyLong(), anyLong(), any())).thenReturn(null);
        mTopicsCobaltLogger.logTopicOccurrence(createTopic(LAST_TOPIC + 1));
        verify(mMockCobaltLogger)
                .logOccurrence(
                        METRIC_ID, /* count= */ 1, ImmutableList.of(UNKNOWN_TOPIC_EVENT_CODE));
    }

    @Test
    public void logTopicOccurrences_topicTooLow_logsUnknown() throws Exception {
        when(mMockCobaltLogger.logOccurrence(anyLong(), anyLong(), any())).thenReturn(null);
        mTopicsCobaltLogger.logTopicOccurrences(createTopic(FIRST_TOPIC - 1), COUNT);
        verify(mMockCobaltLogger)
                .logOccurrence(METRIC_ID, COUNT, ImmutableList.of(UNKNOWN_TOPIC_EVENT_CODE));
    }

    @Test
    public void logTopicOccurrences_topicTooHigh_logsUnknown() throws Exception {
        when(mMockCobaltLogger.logOccurrence(anyLong(), anyLong(), any())).thenReturn(null);
        mTopicsCobaltLogger.logTopicOccurrences(createTopic(LAST_TOPIC + 1), COUNT);
        verify(mMockCobaltLogger)
                .logOccurrence(METRIC_ID, COUNT, ImmutableList.of(UNKNOWN_TOPIC_EVENT_CODE));
    }

    @Test
    public void logTopicOccurrences_zeroCount_notLogged() throws Exception {
        verifyNoMoreInteractions(mMockCobaltLogger);
        mTopicsCobaltLogger.logTopicOccurrences(createTopic(FIRST_TOPIC), 0);
    }

    @Test
    public void logTopicOccurrences_negativeCount_notLogged() throws Exception {
        verifyNoMoreInteractions(mMockCobaltLogger);
        mTopicsCobaltLogger.logTopicOccurrences(createTopic(FIRST_TOPIC), -1);
    }

    @Test
    public void logTopicOccurrence_allTopicsLoggedCorrectly() throws Exception {
        when(mMockCobaltLogger.logOccurrence(anyLong(), anyLong(), any())).thenReturn(null);
        for (int i = 0; i < LOGGED_TOPICS_COUNT; ++i) {
            mTopicsCobaltLogger.logTopicOccurrence(createTopic(FIRST_TOPIC + i));
            verify(mMockCobaltLogger)
                    .logOccurrence(
                            METRIC_ID, /* count= */ 1, ImmutableList.of(FIRST_EVENT_CODE + i));
        }
    }

    @Test
    public void logTopicOccurrences_allTopicsLoggedCorrectly() throws Exception {
        when(mMockCobaltLogger.logOccurrence(anyLong(), anyLong(), any())).thenReturn(null);
        for (int i = 0; i < LOGGED_TOPICS_COUNT; ++i) {
            mTopicsCobaltLogger.logTopicOccurrences(createTopic(FIRST_TOPIC + i), COUNT);
            verify(mMockCobaltLogger)
                    .logOccurrence(METRIC_ID, COUNT, ImmutableList.of(FIRST_EVENT_CODE + i));
        }
    }

    private Topic createTopic(int topic) {
        return Topic.create(topic, /* taxonomyVersion= */ 1, /* modelVersion= */ 1);
    }
}
