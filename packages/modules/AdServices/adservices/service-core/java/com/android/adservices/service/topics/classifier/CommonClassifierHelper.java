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

package com.android.adservices.service.topics.classifier;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_MESSAGE_DIGEST_ALGORITHM_NOT_FOUND;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_READ_CLASSIFIER_ASSET_FILE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;

import android.annotation.NonNull;
import android.content.Context;
import android.content.res.AssetManager;
import android.util.JsonReader;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.EpochComputationGetTopTopicsStats;
import com.android.internal.util.Preconditions;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/** Helper methods for shared implementations of {@link Classifier}. */
public class CommonClassifierHelper {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getTopicsLogger();
    // The key name of asset metadata property in classifier_assets_metadata.json
    private static final String ASSET_PROPERTY_NAME = "property";
    // The key name of asset element in classifier_assets_metadata.json
    private static final String ASSET_ELEMENT_NAME = "asset_name";
    // The algorithm name of checksum
    private static final String SHA256_DIGEST_ALGORITHM_NAME = "SHA-256";
    private static final String BUILD_ID_FIELD = "build_id";

    // Defined constants for error codes which have very long names.
    private static final int TOPICS_READ_CLASSIFIER_ASSET_FILE_FAILURE =
            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_READ_CLASSIFIER_ASSET_FILE_FAILURE;
    private static final int TOPICS_MESSAGE_DIGEST_ALGORITHM_NOT_FOUND =
            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_MESSAGE_DIGEST_ALGORITHM_NOT_FOUND;

    /**
     * Compute the SHA256 checksum of classifier asset.
     *
     * @return A string of classifier asset's SHA256 checksum.
     */
    static String computeClassifierAssetChecksum(
            @NonNull AssetManager assetManager, @NonNull String classifierAssetsMetadataPath) {
        StringBuilder assetSha256CheckSum = new StringBuilder();
        try {
            MessageDigest sha256Digest = MessageDigest.getInstance(SHA256_DIGEST_ALGORITHM_NAME);

            try (InputStream inputStream = assetManager.open(classifierAssetsMetadataPath)) {

                // Create byte array to read data in chunks
                byte[] byteArray = new byte[8192];
                int byteCount = 0;

                // Read file data and update in message digest
                while ((byteCount = inputStream.read(byteArray)) != -1) {
                    sha256Digest.update(byteArray, 0, byteCount);
                }

                // Get the hash's bytes
                byte[] bytes = sha256Digest.digest();

                // This bytes[] has bytes in decimal format;
                // Convert it to hexadecimal format
                for (int i = 0; i < bytes.length; i++) {
                    assetSha256CheckSum.append(
                            Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
                }
            } catch (IOException e) {
                sLogger.e(e, "Unable to read classifier asset file");
                ErrorLogUtil.e(
                        e,
                        TOPICS_READ_CLASSIFIER_ASSET_FILE_FAILURE,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
                // When catching IOException -> return empty string.
                return "";
            }
        } catch (NoSuchAlgorithmException e) {
            sLogger.e(e, "Unable to find correct message digest algorithm.");
            // When catching NoSuchAlgorithmException -> return empty string.
            ErrorLogUtil.e(
                    e,
                    TOPICS_MESSAGE_DIGEST_ALGORITHM_NOT_FOUND,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
            return "";
        }

        return assetSha256CheckSum.toString();
    }

    /**
     * Create a list of top topicIds with numberOfTopTopics + numberOfRandomTopics topicIds.
     *
     * @param appTopics appPackageName to topics map.
     * @param labelIds all topicIds from the labels file.
     * @param random to fetch random elements from the labelIds.
     * @param numberOfTopTopics number of top topics to be added at the start of the list.
     * @param numberOfRandomTopics number of random topics to be added at the end of the list.
     * @return a list of topics with numberOfTopTopics top predicted topics and numberOfRandomTopics
     *     random topics.
     */
    @NonNull
    static List<Topic> getTopTopics(
            @NonNull Map<String, List<Topic>> appTopics,
            @NonNull List<Integer> labelIds,
            @NonNull Random random,
            @NonNull int numberOfTopTopics,
            @NonNull int numberOfRandomTopics,
            @NonNull AdServicesLogger logger) {
        Preconditions.checkArgument(
                numberOfTopTopics > 0, "numberOfTopTopics should larger than 0");
        Preconditions.checkArgument(
                numberOfRandomTopics > 0, "numberOfRandomTopics should larger than 0");

        // A map from Topics to the count of its occurrences.
        Map<Topic, Integer> topicsToAppTopicCount = new HashMap<>();
        for (List<Topic> appTopic : appTopics.values()) {
            for (Topic topic : appTopic) {
                topicsToAppTopicCount.put(topic, topicsToAppTopicCount.getOrDefault(topic, 0) + 1);
            }
        }

        // If there are no topic in the appTopics list, an empty topic list will be returned.
        if (topicsToAppTopicCount.isEmpty()) {
            sLogger.w("Unable to retrieve any topics from device.");
            // Log atom for getTopTopics call.
            logger.logEpochComputationGetTopTopicsStats(
                    EpochComputationGetTopTopicsStats.builder()
                            .setTopTopicCount(0)
                            .setPaddedRandomTopicsCount(0)
                            .setAppsConsideredCount(appTopics.size())
                            .setSdksConsideredCount(-1)
                            .build());
            return new ArrayList<>();
        }

        // Sort the topics by their count.
        List<Topic> allSortedTopics =
                topicsToAppTopicCount.entrySet().stream()
                        .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());

        // The number of topics to pad in top topics.
        int numberOfRandomPaddingTopics = Math.max(0, numberOfTopTopics - allSortedTopics.size());
        List<Topic> topTopics =
                allSortedTopics.subList(0, Math.min(numberOfTopTopics, allSortedTopics.size()));

        // Log atom for getTopTopics call.
        // TODO(b/256638889): Log apps and sdk considered count.
        logger.logEpochComputationGetTopTopicsStats(
                EpochComputationGetTopTopicsStats.builder()
                        .setTopTopicCount(numberOfTopTopics)
                        .setPaddedRandomTopicsCount(numberOfRandomPaddingTopics)
                        .setAppsConsideredCount(appTopics.size())
                        .setSdksConsideredCount(-1)
                        .build());

        // If the size of topTopics smaller than numberOfTopTopics,
        // the top topics list will be padded by numberOfRandomPaddingTopics random topics.
        return getRandomTopics(
                labelIds, random, topTopics, numberOfRandomTopics + numberOfRandomPaddingTopics);
    }

    // This helper function will populate numOfRandomTopics random topics in the topTopics list.
    @NonNull
    private static List<Topic> getRandomTopics(
            @NonNull List<Integer> labelIds,
            @NonNull Random random,
            @NonNull List<Topic> topTopics,
            @NonNull int numberOfRandomTopics) {
        if (numberOfRandomTopics <= 0) {
            return topTopics;
        }

        // Get version information from the first top topic if present
        // (all topics' versions are identical in a given classification).
        long taxonomyVersion = 0L;
        long modelVersion = 0L;
        if (!topTopics.isEmpty()) {
            Topic firstTopic = topTopics.get(0);
            taxonomyVersion = firstTopic.getTaxonomyVersion();
            modelVersion = firstTopic.getModelVersion();
        }

        List<Topic> returnedTopics = new ArrayList<>();

        // First add all the topTopics.
        returnedTopics.addAll(topTopics);

        // Counter of how many random topics need to be added.
        int topicsCounter = numberOfRandomTopics;

        // Then add random topics.
        while (topicsCounter > 0 && returnedTopics.size() < labelIds.size()) {
            // Pick up a random topic from labels list and check if it is a duplicate.
            int randTopicId = labelIds.get(random.nextInt(labelIds.size()));
            Topic randTopic = Topic.create(randTopicId, taxonomyVersion, modelVersion);
            if (returnedTopics.contains(randTopic)) {
                continue;
            }

            returnedTopics.add(randTopic);
            topicsCounter--;
        }

        return returnedTopics;
    }

    /**
     * Gets bundled model build_id from classifierAssetsMetadata file. Returns the default value of
     * -1 if there is no build_id available.
     *
     * @return bundled model build_id
     */
    public static long getBundledModelBuildId(
            @NonNull Context context, @NonNull String classifierAssetsMetadataPath) {
        InputStream inputStream = null; // InputStream.nullInputStream() is not available on S-.
        try {
            inputStream = context.getAssets().open(classifierAssetsMetadataPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read bundled metadata file", e);
        }
        JsonReader reader = new JsonReader(new InputStreamReader(inputStream));
        try {
            reader.beginArray();
            while (reader.hasNext()) {
                // Read through each JSONObject.
                reader.beginObject();
                while (reader.hasNext()) {
                    // Read through version info object and find build_id.
                    String elementKeyName = reader.nextName();
                    if (BUILD_ID_FIELD.equals(elementKeyName)) {
                        return reader.nextLong();
                    } else {
                        reader.skipValue();
                    }
                }
                reader.endObject();
            }
            reader.endArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse classifier assets metadata file", e);
        }
        return -1;
    }
}
