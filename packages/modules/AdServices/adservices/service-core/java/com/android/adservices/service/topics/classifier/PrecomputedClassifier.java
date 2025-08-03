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

import android.annotation.NonNull;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.data.topics.Topic;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.EpochComputationClassifierStats;
import com.android.adservices.service.stats.EpochComputationClassifierStats.ClassifierType;
import com.android.adservices.service.stats.EpochComputationClassifierStats.OnDeviceClassifierStatus;
import com.android.adservices.service.stats.EpochComputationClassifierStats.PrecomputedClassifierStatus;
import com.android.adservices.service.topics.CacheManager;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Precomputed Classifier.
 *
 * <p>This Classifier will classify app into list of Topics using the server side classifier. The
 * classification results for the top K apps are computed on the server and stored on the device.
 *
 * <p>This class is not thread safe.
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
@NotThreadSafe
public class PrecomputedClassifier implements Classifier {

    private static PrecomputedClassifier sSingleton;

    private static final String MODEL_ASSET_FIELD = "tflite_model";
    private static final String LABELS_ASSET_FIELD = "labels_topics";
    private static final String ASSET_VERSION_FIELD = "asset_version";
    private static final String VERSION_INFO_FIELD = "version_info";
    private static final String BUILD_ID_FIELD = "build_id";

    private final ModelManager mModelManager;
    private final CacheManager mCacheManager;

    // Used to mark whether the assets are loaded
    private boolean mLoaded;
    private ImmutableList<Integer> mLabels;
    // The app topics map Map<App, List<Topic>>
    private Map<String, List<Integer>> mAppTopics = new HashMap<>();
    private List<Integer> mBlockedTopicIds;
    private long mModelVersion;
    private long mLabelsVersion;
    private int mBuildId;
    private final AdServicesLogger mLogger;

    PrecomputedClassifier(
            @NonNull ModelManager modelManager,
            @NonNull CacheManager cacheManager,
            @NonNull AdServicesLogger logger) {
        mModelManager = modelManager;
        mCacheManager = cacheManager;
        mLoaded = false;
        mLogger = logger;
    }

    @NonNull
    @Override
    public Map<String, List<Topic>> classify(@NonNull Set<String> apps) {
        if (!isLoaded()) {
            load();
        }

        Map<String, List<Topic>> appsToClassifiedTopics = new HashMap<>(apps.size());

        for (String app : apps) {
            if (app != null && !app.isEmpty()) {
                List<Integer> topicIds = mAppTopics.getOrDefault(app, ImmutableList.of());
                ImmutableList.Builder<Integer> topicIdsToReturn = ImmutableList.builder();
                ImmutableList.Builder<Topic> topicsToReturn = ImmutableList.builder();

                for (int topicId : topicIds) {
                    if (mBlockedTopicIds.contains(topicId)) {
                        continue;
                    }
                    topicIdsToReturn.add(topicId);
                    topicsToReturn.add(createTopic(topicId));
                }

                // Log atom for getTopTopics call.
                mLogger.logEpochComputationClassifierStats(
                        EpochComputationClassifierStats.builder()
                                .setTopicIds(topicIdsToReturn.build())
                                .setBuildId(mBuildId)
                                .setAssetVersion(Long.toString(mModelVersion))
                                .setClassifierType(ClassifierType.PRECOMPUTED_CLASSIFIER)
                                .setOnDeviceClassifierStatus(
                                        OnDeviceClassifierStatus
                                                .ON_DEVICE_CLASSIFIER_STATUS_NOT_INVOKED)
                                .setPrecomputedClassifierStatus(
                                        topicIds.isEmpty()
                                                ? PrecomputedClassifierStatus
                                                        .PRECOMPUTED_CLASSIFIER_STATUS_FAILURE
                                                : PrecomputedClassifierStatus
                                                        .PRECOMPUTED_CLASSIFIER_STATUS_SUCCESS)
                                .build());

                appsToClassifiedTopics.put(app, topicsToReturn.build());
            }
        }
        return appsToClassifiedTopics;
    }

    @NonNull
    @Override
    public List<Topic> getTopTopics(
            @NonNull Map<String, List<Topic>> appTopics,
            @NonNull int numberOfTopTopics,
            @NonNull int numberOfRandomTopics) {
        // Load assets if not loaded already.
        if (!isLoaded()) {
            load();
        }

        return CommonClassifierHelper.getTopTopics(
                appTopics, mLabels, new Random(), numberOfTopTopics, numberOfRandomTopics, mLogger);
    }

    List<Integer> getLabels() {
        // Load assets if not loaded already.
        if (!isLoaded()) {
            load();
        }

        return mLabels;
    }

    long getModelVersion() {
        // Load assets if not loaded already.
        if (!isLoaded()) {
            load();
        }

        return mModelVersion;
    }

    long getLabelsVersion() {
        // Load assets if not loaded already.
        if (!isLoaded()) {
            load();
        }

        return mLabelsVersion;
    }

    private Topic createTopic(int topicId) {
        return Topic.create(topicId, mLabelsVersion, mModelVersion);
    }

    // Load labels and app topics.
    private void load() {
        mLabels = mModelManager.retrieveLabels();
        mAppTopics = mModelManager.retrieveAppClassificationTopics();

        // Load blocked topic IDs.
        mBlockedTopicIds =
                mCacheManager.getTopicsWithRevokedConsent().stream()
                        .map(Topic::getTopic)
                        .collect(Collectors.toList());

        // Load classifier assets metadata.
        ImmutableMap<String, ImmutableMap<String, String>> classifierAssetsMetadata =
                mModelManager.retrieveClassifierAssetsMetadata();
        mModelVersion =
                Long.parseLong(
                        classifierAssetsMetadata.get(MODEL_ASSET_FIELD).get(ASSET_VERSION_FIELD));
        mLabelsVersion =
                Long.parseLong(
                        classifierAssetsMetadata.get(LABELS_ASSET_FIELD).get(ASSET_VERSION_FIELD));
        mBuildId = Ints.saturatedCast(mModelManager.getBuildId());
        mLoaded = true;
    }

    // Indicates whether labels and app topics are loaded.
    private boolean isLoaded() {
        return mLoaded;
    }
}
