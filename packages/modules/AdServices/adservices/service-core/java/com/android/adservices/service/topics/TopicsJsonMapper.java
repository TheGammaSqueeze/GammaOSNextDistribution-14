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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ENCRYPTION_SERIALIZATION_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Optional;

/**
 * Helper class to map {@link Topic} to JSON string.
 *
 * <p>Example JSON string : {@code {"taxonomy_version": 5, "model_version": 2, "topic_id": 10010 }}
 */
public class TopicsJsonMapper {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getTopicsLogger();

    // Keys for Topics JSON Object.
    /** Key for {@link Topic#getTopic()}. */
    @VisibleForTesting static final String KEY_TOPIC_ID = "topic_id";

    /** Key for {@link Topic#getTaxonomyVersion()}. */
    @VisibleForTesting static final String KEY_TAXONOMY_VERSION = "taxonomy_version";

    /** Key for {@link Topic#getModelVersion()}. */
    @VisibleForTesting static final String KEY_MODEL_VERSION = "model_version";

    // To prevent instantiation.
    private TopicsJsonMapper() {}
    ;

    /**
     * Convert {@link Topic} to {@link JSONObject} wrapped in {@link Optional}.
     *
     * @param topic object to convert.
     * @return {@code Optional<JSONObject>} for the corresponding topic. Returns {@link
     *     Optional#empty()} if serialization to JSON fails.
     */
    public static Optional<JSONObject> toJson(Topic topic) {
        try {
            return Optional.of(
                    new JSONObject()
                            .put(KEY_TOPIC_ID, topic.getTopic())
                            .put(KEY_MODEL_VERSION, topic.getModelVersion())
                            .put(KEY_TAXONOMY_VERSION, topic.getTaxonomyVersion()));
        } catch (JSONException e) {
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ENCRYPTION_SERIALIZATION_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
            sLogger.e("JSON serialization failed for %s", topic);
            return Optional.empty();
        }
    }
}
