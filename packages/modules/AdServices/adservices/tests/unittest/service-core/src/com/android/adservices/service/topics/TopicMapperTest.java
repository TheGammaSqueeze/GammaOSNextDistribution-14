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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;

import android.content.Context;
import android.content.res.Resources;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.topics.Topic;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/** Tests for {@link TopicsMapper}. */
public class TopicMapperTest {
    @Mock private Context mContext;
    @Mock private Resources mResources;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    /** Setup needed before every test in this class. */
    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    /** Test single {@link Topic} to Android resource id mapper.. */
    @Test
    public void getResourceIdByTopicTest() {
        int topicId = 1;
        long taxonomyId = 1L;
        long modelVersion = 1L;
        int expectedResourceId = 123;
        Mockito.when(mResources.getIdentifier(any(), any(), any())).thenReturn(expectedResourceId);
        Mockito.when(mContext.getResources()).thenReturn(mResources);

        int resourceId =
                TopicsMapper.getResourceIdByTopic(
                        Topic.create(topicId, taxonomyId, modelVersion), mContext);

        assertThat(resourceId).isEqualTo(expectedResourceId);
    }

    /** Test a list of {@link Topic}s to Android resources id mapper. */
    @Test
    public void getResourceIdsByTopicListTest() {
        int firstTopicId = 1;
        int secondTopicId = 2;
        long taxonomyId = 1L;
        long modelVersion = 1L;
        int firstTopicExpectedResourceId = 1;
        int secondTopicExpectedResourceId = 2;
        Topic firstTopic = Topic.create(firstTopicId, taxonomyId, modelVersion);
        Topic secondTopic = Topic.create(secondTopicId, taxonomyId, modelVersion);
        Mockito.when(mResources.getIdentifier(any(), any(), any()))
                .thenReturn(firstTopicExpectedResourceId)
                .thenReturn(secondTopicExpectedResourceId);
        Mockito.when(mContext.getResources()).thenReturn(mResources);

        ImmutableList<Integer> topicsListContainingResourceId =
                TopicsMapper.getResourcesIdMapByTopicsList(
                        ImmutableList.of(firstTopic, secondTopic), mContext);

        assertThat(topicsListContainingResourceId)
                .containsExactly(firstTopicExpectedResourceId, secondTopicExpectedResourceId);
    }
}
