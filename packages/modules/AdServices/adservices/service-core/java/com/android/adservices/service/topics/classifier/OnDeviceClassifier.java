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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_LOAD_ML_MODEL_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ON_DEVICE_CLASSIFY_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ON_DEVICE_NUMBER_FORMAT_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;

import android.annotation.NonNull;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.EpochComputationClassifierStats;
import com.android.adservices.service.stats.EpochComputationClassifierStats.ClassifierType;
import com.android.adservices.service.stats.EpochComputationClassifierStats.OnDeviceClassifierStatus;
import com.android.adservices.service.stats.EpochComputationClassifierStats.PrecomputedClassifierStatus;
import com.android.adservices.service.topics.CacheManager;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Ints;

import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.text.nlclassifier.BertNLClassifier;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This Classifier classifies app into list of Topics using the on-device classification ML Model.
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class OnDeviceClassifier implements Classifier {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getTopicsLogger();

    private static final String MODEL_ASSET_FIELD = "tflite_model";
    private static final String LABELS_ASSET_FIELD = "labels_topics";
    private static final String ASSET_VERSION_FIELD = "asset_version";

    private static final String NO_VERSION_INFO = "NO_VERSION_INFO";

    // Defined constants for error codes which have very long names.
    private static final int TOPICS_ON_DEVICE_NUMBER_FORMAT_EXCEPTION =
            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ON_DEVICE_NUMBER_FORMAT_EXCEPTION;

    private final Random mRandom;
    private final ModelManager mModelManager;
    private final CacheManager mCacheManager;
    private final ClassifierInputManager mClassifierInputManager;

    private BertNLClassifier mBertNLClassifier;
    private ImmutableList<Integer> mLabels;
    private ClassifierInputConfig mClassifierInputConfig;
    private List<Integer> mBlockedTopicIds;
    private long mModelVersion;
    private long mLabelsVersion;
    private int mBuildId;
    private boolean mLoaded;
    private final AdServicesLogger mLogger;

    OnDeviceClassifier(
            @NonNull Random random,
            @NonNull ModelManager modelManager,
            @NonNull CacheManager cacheManager,
            @NonNull ClassifierInputManager classifierInputManager,
            @NonNull AdServicesLogger logger) {
        mRandom = random;
        mLoaded = false;
        mModelManager = modelManager;
        mCacheManager = cacheManager;
        mClassifierInputManager = classifierInputManager;
        mLogger = logger;
    }

    @Override
    @NonNull
    public ImmutableMap<String, List<Topic>> classify(@NonNull Set<String> appPackageNames) {
        if (appPackageNames.isEmpty()) {
            return ImmutableMap.of();
        }

        if (!mModelManager.isModelAvailable()) {
            // Return empty map since no model is available.
            sLogger.d("[ML] No ML model available for classification. Return empty Map.");
            return ImmutableMap.of();
        }

        // Load the assets if not loaded already.
        if (!isLoaded()) {
            mLoaded = load();
        }

        ImmutableMap.Builder<String, List<Topic>> packageNameToTopics = ImmutableMap.builder();
        for (String appPackageName : appPackageNames) {
            String classifierInput =
                    mClassifierInputManager.getClassifierInput(
                            mClassifierInputConfig, appPackageName);
            List<Topic> appClassificationTopics = getAppClassificationTopics(classifierInput);
            logEpochComputationClassifierStats(appClassificationTopics);
            sLogger.v(
                    "[ML] Top classification for classifier input \""
                            + classifierInput
                            + "\" is "
                            + Iterables.getFirst(appClassificationTopics, /*default value*/ -1));
            packageNameToTopics.put(appPackageName, appClassificationTopics);
        }

        return packageNameToTopics.build();
    }

    private void logEpochComputationClassifierStats(List<Topic> topics) {
        // Log atom for getTopTopics call.
        ImmutableList.Builder<Integer> topicIds = ImmutableList.builder();
        for (Topic topic : topics) {
            topicIds.add(topic.getTopic());
        }
        mLogger.logEpochComputationClassifierStats(
                EpochComputationClassifierStats.builder()
                        .setTopicIds(topicIds.build())
                        .setBuildId(mBuildId)
                        .setAssetVersion(Long.toString(mModelVersion))
                        .setClassifierType(ClassifierType.ON_DEVICE_CLASSIFIER)
                        .setOnDeviceClassifierStatus(
                                topics.isEmpty()
                                        ? OnDeviceClassifierStatus
                                                .ON_DEVICE_CLASSIFIER_STATUS_FAILURE
                                        : OnDeviceClassifierStatus
                                                .ON_DEVICE_CLASSIFIER_STATUS_SUCCESS)
                        .setPrecomputedClassifierStatus(
                                PrecomputedClassifierStatus
                                        .PRECOMPUTED_CLASSIFIER_STATUS_NOT_INVOKED)
                        .build());
    }

    @Override
    @NonNull
    public List<Topic> getTopTopics(
            Map<String, List<Topic>> appTopics, int numberOfTopTopics, int numberOfRandomTopics) {
        // Load assets if necessary.
        if (!isLoaded()) {
            load();
        }

        return CommonClassifierHelper.getTopTopics(
                appTopics, mLabels, mRandom, numberOfTopTopics, numberOfRandomTopics, mLogger);
    }

    // Uses the BertNLClassifier to fetch the most relevant topic id based on the classifier input.
    private List<Topic> getAppClassificationTopics(@NonNull String classifierInput) {
        // Returns list of labelIds with their corresponding score in Category for the classifier
        // input.
        List<Category> classifications;
        try {
            classifications = mBertNLClassifier.classify(classifierInput);
        } catch (Exception e) {
            // (TODO:b/242926783): Update to more granular Exception after resolving JNI error
            // propagation.
            sLogger.e("[ML] classify call failed for mBertNLClassifier.");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ON_DEVICE_CLASSIFY_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
            return ImmutableList.of();
        }
        // Get the highest score first. Sort in decreasing order.
        classifications.sort(Comparator.comparing(Category::getScore).reversed());

        // Limit the number of entries to first MAX_LABELS_PER_APP.
        // TODO(b/235435229): Evaluate the strategy to use first x elements.
        int numberOfTopLabels = FlagsFactory.getFlags().getClassifierNumberOfTopLabels();
        float classifierThresholdValue = FlagsFactory.getFlags().getClassifierThreshold();
        sLogger.i(
                "numberOfTopLabels = %s | classifierThresholdValue = %s",
                numberOfTopLabels, classifierThresholdValue);
        return classifications.stream()
                .sorted((c1, c2) -> Float.compare(c2.getScore(), c1.getScore())) // Reverse sorted.
                .filter(category -> isAboveThreshold(category, classifierThresholdValue))
                .map(OnDeviceClassifier::convertCategoryLabelToTopicId)
                .filter(topicId -> !mBlockedTopicIds.contains(topicId))
                .map(this::createTopic)
                .limit(numberOfTopLabels)
                .collect(Collectors.toList());
    }

    // Filter category above the required threshold.
    private static boolean isAboveThreshold(Category category, float classifierThresholdValue) {
        return category.getScore() >= classifierThresholdValue;
    }

    // Converts Category Label to TopicId. Expects label to be labelId of the classified category.
    // Returns -1 if conversion to int fails for the label.
    private static int convertCategoryLabelToTopicId(Category category) {
        try {
            // Category label is expected to be the topicId of the predicted topic.
            return Integer.parseInt(category.getLabel());
        } catch (NumberFormatException numberFormatException) {
            sLogger.e(
                    numberFormatException,
                    "ML model did not return a topic id. Label returned is %s",
                    category.getLabel());
            ErrorLogUtil.e(
                    numberFormatException,
                    TOPICS_ON_DEVICE_NUMBER_FORMAT_EXCEPTION,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
            return -1;
        }
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

    long getBertModelVersion() {
        // Load assets if necessary.
        if (!isLoaded()) {
            load();
        }

        String modelVersion = mBertNLClassifier.getModelVersion();
        if (modelVersion.equals(NO_VERSION_INFO)) {
            return 0;
        }

        return Long.parseLong(modelVersion);
    }

    long getLabelsVersion() {
        // Load assets if not loaded already.
        if (!isLoaded()) {
            load();
        }

        return mLabelsVersion;
    }

    long getBertLabelsVersion() {
        // Load assets if necessary.
        if (!isLoaded()) {
            load();
        }

        String labelsVersion = mBertNLClassifier.getLabelsVersion();
        if (labelsVersion.equals(NO_VERSION_INFO)) {
            return 0;
        }

        return Long.parseLong(labelsVersion);
    }

    private Topic createTopic(int topicId) {
        return Topic.create(topicId, mLabelsVersion, mModelVersion);
    }

    // Indicates whether assets are loaded.
    private boolean isLoaded() {
        return mLoaded;
    }

    // Loads classifier model evaluation assets needed for classification.
    // Return true on successful loading of all assets. Return false if any loading fails.
    private boolean load() {

        // Load Bert model.
        try {
            mBertNLClassifier = loadModel();
        } catch (Exception e) {
            sLogger.e(e, "Loading ML model failed.");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_LOAD_ML_MODEL_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
            return false;
        }

        // Load labels.
        mLabels = mModelManager.retrieveLabels();

        // Load classifier input config.
        mClassifierInputConfig = mModelManager.retrieveClassifierInputConfig();

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
        return true;
    }

    // Load BertNLClassifier Model from the corresponding modelFilePath.
    private BertNLClassifier loadModel() throws IOException {
        return BertNLClassifier.createFromBuffer(mModelManager.retrieveModel());
    }
}
