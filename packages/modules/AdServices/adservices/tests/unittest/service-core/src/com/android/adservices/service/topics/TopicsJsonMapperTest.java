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

package com.android.adservices.service.topics;

import static com.android.adservices.service.topics.TopicsJsonMapper.KEY_MODEL_VERSION;
import static com.android.adservices.service.topics.TopicsJsonMapper.KEY_TAXONOMY_VERSION;
import static com.android.adservices.service.topics.TopicsJsonMapper.KEY_TOPIC_ID;

import static com.google.common.truth.Truth.assertThat;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.topics.Topic;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;

import java.util.Locale;

/** Unit tests for {@link TopicsJsonMapper}. */
public class TopicsJsonMapperTest {
    private static final int TOPIC_ID = 7;
    private static final long MODEL_VERSION = 5L;
    private static final long TAXONOMY_VERSION = 2L;
    private static final Topic SAMPLE_TOPIC =
            Topic.create(TOPIC_ID, TAXONOMY_VERSION, MODEL_VERSION);

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void getTopicJson() throws JSONException {
        JSONObject topicJson = TopicsJsonMapper.toJson(SAMPLE_TOPIC).get();

        // Verify individual fields
        assertThat(topicJson.getInt(KEY_TOPIC_ID)).isEqualTo(TOPIC_ID);
        assertThat(topicJson.getInt(KEY_MODEL_VERSION)).isEqualTo(MODEL_VERSION);
        assertThat(topicJson.getInt(KEY_TAXONOMY_VERSION)).isEqualTo(TAXONOMY_VERSION);

        // Validate JSON string
        assertThat(topicJson.toString())
                .isEqualTo(
                        String.format(
                                Locale.US,
                                "{\"topic_id\":%d,\"model_version\":%d,"
                                        + "\"taxonomy_version\":%d}",
                                TOPIC_ID,
                                MODEL_VERSION,
                                TAXONOMY_VERSION));
    }

    @Test
    public void getTopicJson_deserialization() throws JSONException {
        // Create JSONObject from Topic json string.
        JSONObject expectedJSON =
                new JSONObject(
                        String.format(
                                Locale.US,
                                "{\"topic_id\":%d,\"model_version\":%d,"
                                        + "\"taxonomy_version\":%d}",
                                TOPIC_ID,
                                MODEL_VERSION,
                                TAXONOMY_VERSION));

        // Validate same JSONObjects.
        JSONObject returnedJSON = TopicsJsonMapper.toJson(SAMPLE_TOPIC).get();
        assertThat(returnedJSON.toString()).isEqualTo(expectedJSON.toString());

        // Validate Topic object created from JSONObject.
        assertThat(
                        Topic.create(
                                returnedJSON.getInt(KEY_TOPIC_ID),
                                returnedJSON.getLong(KEY_TAXONOMY_VERSION),
                                returnedJSON.getLong(KEY_MODEL_VERSION)))
                .isEqualTo(SAMPLE_TOPIC);
    }
}
