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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ID_TO_NAME_LIST_READ_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;

import android.annotation.NonNull;
import android.content.Context;
import android.content.res.AssetManager;

import com.android.adservices.LoggerFactory;
import com.android.adservices.errorlogging.ErrorLogUtil;

import com.google.common.collect.ImmutableMap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/** Util class that retrieves topicId to topicName {@link ImmutableMap}. */
public class TopicIdNameUtil {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getTopicsLogger();
    // Use "\t" as a delimiter to read the topicId to topicName file
    private static final String DELIMITER = "\t";
    private static final String TOPIC_ID_TO_TOPIC_NAME_FILE_PATH =
            "classifier/topic_id_to_name.csv";

    private final AssetManager mAssetManager;

    public TopicIdNameUtil(@NonNull Context context) {
        mAssetManager = context.getAssets();
    }

    /**
     * Retrieves the topicId to topicName map. An empty map will be return when catching an
     * IOException.
     *
     * @return The map from topicId to the topicName.
     */
    @NonNull
    public ImmutableMap<Integer, String> retrieveTopicIdToName() {
        // topicIdToNameMap = ImmutableMap<TopicId, topicName>
        ImmutableMap.Builder<Integer, String> topicIdToNameMap = new ImmutableMap.Builder();
        String line;

        try (InputStreamReader inputStreamReader =
                new InputStreamReader(mAssetManager.open(TOPIC_ID_TO_TOPIC_NAME_FILE_PATH))) {
            BufferedReader reader = new BufferedReader(inputStreamReader);

            // Skip first line (columns name)
            reader.readLine();

            while ((line = reader.readLine()) != null) {
                String[] columns = line.split(DELIMITER);

                // The first column is topicId
                Integer topicId = Integer.parseInt(columns[0]);

                // The second column is topicName
                String topicName = columns[1];

                topicIdToNameMap.put(topicId, topicName);
            }
        } catch (IOException e) {
            sLogger.e(e, "Unable to read topicId to topicName list");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ID_TO_NAME_LIST_READ_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
            return ImmutableMap.<Integer, String>builder().build();
        }

        return topicIdToNameMap.build();
    }
}
