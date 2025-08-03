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

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.SdkLevelSupportRule;

import com.google.common.collect.ImmutableMap;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

/**
 * TopicId to TopicName utility test {@link TopicIdNameUtil}
 */
public class TopicIdNameUtilTest {
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static TopicIdNameUtil sTopicIdNameUtil;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setUp() throws IOException {
        sTopicIdNameUtil = new TopicIdNameUtil(sContext);
    }

    @Test
    public void checkLoadedTopicIdToNameMap() throws IOException {
        ImmutableMap<Integer, String> topicIdToName = sTopicIdNameUtil.retrieveTopicIdToName();

        // Check size of map
        // The topicId to topicName file contains 446 topics.
        assertThat(topicIdToName.size()).isEqualTo(446);

        // Check some topicIds to topicNames in the map
        assertThat(topicIdToName.get(10046)).isEqualTo(
                "/Arts & Entertainment/Music & Audio/Rock Music/Indie & Alternative Music");

        assertThat(topicIdToName.get(10340)).isEqualTo(
                "/People & Society/Family & Relationships/Parenting/Child Internet Safety");

        assertThat(topicIdToName.get(10430)).isEqualTo(
                "/Travel & Transportation/Business Travel");
    }
}
