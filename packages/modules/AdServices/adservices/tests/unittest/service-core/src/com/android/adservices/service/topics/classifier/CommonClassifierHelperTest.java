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

import static com.android.adservices.mockito.ExtendedMockitoExpectations.doNothingOnErrorLogUtilError;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockGetFlagsForTest;
import static com.android.adservices.service.topics.classifier.CommonClassifierHelper.computeClassifierAssetChecksum;
import static com.android.adservices.service.topics.classifier.CommonClassifierHelper.getTopTopics;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.MockRandom;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.EpochComputationGetTopTopicsStats;

import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.mobiledatadownload.ClientConfigProto.ClientFile;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Tests for {@link CommonClassifierHelper}.
 *
 * <p><b> Note: Some tests in this test class are depend on the ordering of topicIds in
 * adservices/tests/unittest/service-core/assets/classifier/labels_test_topics.txt, because we will
 * use Random() or MockRandom() to generate random integer index to get random topicIds. Topics will
 * be selected from the topics list in order by their index in the topics list. </b>
 */
public class CommonClassifierHelperTest {
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final String TEST_LABELS_FILE_PATH = "classifier/labels_test_topics.txt";
    private static final String TEST_PRECOMPUTED_FILE_PATH =
            "classifier/precomputed_test_app_list.csv";
    private static final String TEST_CLASSIFIER_ASSETS_METADATA_PATH =
            "classifier/classifier_test_assets_metadata.json";
    private static final String TEST_CLASSIFIER_INPUT_CONFIG_PATH =
            "classifier/classifier_input_config.txt";
    private static final String PRODUCTION_LABELS_FILE_PATH = "classifier/labels_topics.txt";
    private static final String PRODUCTION_APPS_FILE_PATH = "classifier/precomputed_app_list.csv";
    private static final String PRODUCTION_CLASSIFIER_ASSETS_METADATA_PATH =
            "classifier/classifier_assets_metadata.json";
    private static final String PRODUCTION_CLASSIFIER_INPUT_CONFIG_PATH =
            "classifier/classifier_input_config.txt";
    private static final String BUNDLED_MODEL_FILE_PATH = "classifier/model.tflite";

    private ModelManager mTestModelManager;
    private ModelManager mProductionModelManager;
    private ImmutableList<Integer> testLabels;
    private ImmutableMap<String, ImmutableMap<String, String>> testClassifierAssetsMetadata;
    private long mTestTaxonomyVersion;
    private long mTestModelVersion;

    private ImmutableList<Integer> productionLabels;
    private ImmutableMap<String, ImmutableMap<String, String>> productionClassifierAssetsMetadata;
    private long mProductionTaxonomyVersion;
    private long mProductionModelVersion;

    @Mock SynchronousFileStorage mMockFileStorage;
    @Mock Map<String, ClientFile> mMockDownloadedFiles;
    @Mock AdServicesLogger mLogger;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Rule(order = 1)
    public final AdServicesExtendedMockitoRule adServicesExtendedMockitoRule =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .spyStatic(FlagsFactory.class)
                    .spyStatic(ErrorLogUtil.class)
                    .build();

    @Before
    public void setUp() {
        mockGetFlagsForTest();

        mTestModelManager =
                new ModelManager(
                        sContext,
                        TEST_LABELS_FILE_PATH,
                        TEST_PRECOMPUTED_FILE_PATH,
                        TEST_CLASSIFIER_ASSETS_METADATA_PATH,
                        TEST_CLASSIFIER_INPUT_CONFIG_PATH,
                        BUNDLED_MODEL_FILE_PATH,
                        mMockFileStorage,
                        mMockDownloadedFiles);

        mProductionModelManager =
                new ModelManager(
                        sContext,
                        PRODUCTION_LABELS_FILE_PATH,
                        PRODUCTION_APPS_FILE_PATH,
                        PRODUCTION_CLASSIFIER_ASSETS_METADATA_PATH,
                        PRODUCTION_CLASSIFIER_INPUT_CONFIG_PATH,
                        BUNDLED_MODEL_FILE_PATH,
                        mMockFileStorage,
                        mMockDownloadedFiles);

        doNothingOnErrorLogUtilError();

        testLabels = mTestModelManager.retrieveLabels();
        testClassifierAssetsMetadata = mTestModelManager.retrieveClassifierAssetsMetadata();
        mTestTaxonomyVersion =
                Long.parseLong(
                        testClassifierAssetsMetadata.get("labels_topics").get("asset_version"));
        mTestModelVersion =
                Long.parseLong(
                        testClassifierAssetsMetadata.get("tflite_model").get("asset_version"));

        productionLabels = mProductionModelManager.retrieveLabels();
        productionClassifierAssetsMetadata =
                mProductionModelManager.retrieveClassifierAssetsMetadata();
        mProductionTaxonomyVersion =
                Long.parseLong(
                        productionClassifierAssetsMetadata
                                .get("labels_topics")
                                .get("asset_version"));
        mProductionModelVersion =
                Long.parseLong(
                        productionClassifierAssetsMetadata
                                .get("tflite_model")
                                .get("asset_version"));
    }

    @Test
    public void testGetTopTopics_legalInput() {
        ArgumentCaptor<EpochComputationGetTopTopicsStats> argument =
                ArgumentCaptor.forClass(EpochComputationGetTopTopicsStats.class);
        // construction the appTopics map so that when sorting by the number of occurrences,
        // the order of topics are:
        // topic1, topic2, topic3, topic4, topic5, ...,
        Map<String, List<Topic>> appTopics = new HashMap<>();
        appTopics.put("app1", getTestTopics(Arrays.asList(1, 2, 3, 4, 5)));
        appTopics.put("app2", getTestTopics(Arrays.asList(1, 2, 3, 4, 5)));
        appTopics.put("app3", getTestTopics(Arrays.asList(1, 2, 3, 4, 16)));
        appTopics.put("app4", getTestTopics(Arrays.asList(1, 2, 3, 13, 17)));
        appTopics.put("app5", getTestTopics(Arrays.asList(1, 2, 11, 14, 18)));
        appTopics.put("app6", getTestTopics(Arrays.asList(1, 10, 12, 15, 19)));

        // This test case should return top 5 topics from appTopics and 1 random topic
        List<Topic> testResponse =
                getTopTopics(
                        appTopics,
                        testLabels,
                        new Random(),
                        /* numberOfTopTopics */ 5,
                        /* numberOfRandomTopics */ 1,
                        mLogger);

        assertThat(testResponse.get(0)).isEqualTo(getTestTopic(1));
        assertThat(testResponse.get(1)).isEqualTo(getTestTopic(2));
        assertThat(testResponse.get(2)).isEqualTo(getTestTopic(3));
        assertThat(testResponse.get(3)).isEqualTo(getTestTopic(4));
        assertThat(testResponse.get(4)).isEqualTo(getTestTopic(5));
        // Check the random topic is not empty
        // The random topic is at the end
        assertThat(testResponse.get(5)).isNotNull();

        verify(mLogger).logEpochComputationGetTopTopicsStats(argument.capture());
        assertThat(argument.getValue())
                .isEqualTo(
                        EpochComputationGetTopTopicsStats.builder()
                                .setTopTopicCount(5)
                                .setPaddedRandomTopicsCount(0)
                                .setAppsConsideredCount(6)
                                .setSdksConsideredCount(-1)
                                .build());
    }

    @Test
    public void testGetTopTopics_largeTopTopicsInput() {
        ArgumentCaptor<EpochComputationGetTopTopicsStats> argument =
                ArgumentCaptor.forClass(EpochComputationGetTopTopicsStats.class);

        Map<String, List<Topic>> appTopics = new HashMap<>();
        appTopics.put("app1", getTestTopics(Arrays.asList(1, 2, 3, 4, 5)));

        // We only have 5 topics but requesting for 15 topics,
        // so we will pad them with 10 random topics.
        List<Topic> testResponse =
                getTopTopics(
                        appTopics,
                        testLabels,
                        new Random(),
                        /* numberOfTopTopics */ 15,
                        /* numberOfRandomTopics */ 1,
                        mLogger);

        // The response body should contain 11 topics.
        assertThat(testResponse.size()).isEqualTo(16);
        verify(mLogger).logEpochComputationGetTopTopicsStats(argument.capture());
        assertThat(argument.getValue())
                .isEqualTo(
                        EpochComputationGetTopTopicsStats.builder()
                                .setTopTopicCount(15)
                                .setPaddedRandomTopicsCount(10)
                                .setAppsConsideredCount(1)
                                .setSdksConsideredCount(-1)
                                .build());
    }

    @Test
    public void testGetTopTopics_zeroTopTopics() {
        Map<String, List<Topic>> appTopics = new HashMap<>();
        appTopics.put("app1", getTestTopics(Arrays.asList(1, 2, 3, 4, 5)));

        // This test case should throw an IllegalArgumentException if numberOfTopTopics is 0.
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        getTopTopics(
                                appTopics,
                                testLabels,
                                new Random(),
                                /* numberOfTopTopics */ 0,
                                /* numberOfRandomTopics */ 1,
                                mLogger));
    }

    @Test
    public void testGetTopTopics_zeroRandomTopics() {
        Map<String, List<Topic>> appTopics = new HashMap<>();
        appTopics.put("app1", getTestTopics(Arrays.asList(1, 2, 3, 4, 5)));
        // This test case should throw an IllegalArgumentException if numberOfRandomTopics is 0.
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        getTopTopics(
                                appTopics,
                                testLabels,
                                new Random(),
                                /* numberOfTopTopics */ 3,
                                /* numberOfRandomTopics */ 0,
                                mLogger));
    }

    @Test
    public void testGetTopTopics_negativeTopTopics() {
        Map<String, List<Topic>> appTopics = new HashMap<>();
        appTopics.put("app1", getTestTopics(Arrays.asList(1, 2, 3, 4, 5)));

        // This test case should throw an IllegalArgumentException if numberOfTopTopics is negative.
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        getTopTopics(
                                appTopics,
                                testLabels,
                                new Random(),
                                /* numberOfTopTopics */ -5,
                                /* numberOfRandomTopics */ 1,
                                mLogger));
    }

    @Test
    public void testGetTopTopics_negativeRandomTopics() {
        Map<String, List<Topic>> appTopics = new HashMap<>();
        appTopics.put("app1", getTestTopics(Arrays.asList(1, 2, 3, 4, 5)));

        // This test case should throw an IllegalArgumentException
        // if numberOfRandomTopics is negative.
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        getTopTopics(
                                appTopics,
                                testLabels,
                                new Random(),
                                /* numberOfTopTopics */ 3,
                                /* numberOfRandomTopics */ -1,
                                mLogger));
    }

    @Test
    public void testGetTopTopics_emptyAppTopicsMap() {
        Map<String, List<Topic>> appTopics = new HashMap<>();

        // The device does not have an app, an empty top topics list should be returned.
        List<Topic> testResponse =
                getTopTopics(
                        appTopics,
                        testLabels,
                        new Random(),
                        /* numberOfTopTopics */ 5,
                        /* numberOfRandomTopics */ 1,
                        mLogger);

        // The response body should be empty.
        assertThat(testResponse).isEmpty();
    }

    @Test
    public void testGetTopTopics_emptyTopicInEachApp() {
        ArgumentCaptor<EpochComputationGetTopTopicsStats> argument =
                ArgumentCaptor.forClass(EpochComputationGetTopTopicsStats.class);
        Map<String, List<Topic>> appTopics = new HashMap<>();

        // app1 and app2 do not have any classification topics.
        appTopics.put("app1", new ArrayList<>());
        appTopics.put("app2", new ArrayList<>());

        // The device have some apps but the topic corresponding to the app cannot be obtained.
        // In this test case, an empty top topics list should be returned.
        List<Topic> testResponse =
                getTopTopics(
                        appTopics,
                        testLabels,
                        new Random(),
                        /* numberOfTopTopics */ 5,
                        /* numberOfRandomTopics */ 1,
                        mLogger);

        // The response body should be empty
        assertThat(testResponse).isEmpty();
        verify(mLogger).logEpochComputationGetTopTopicsStats(argument.capture());
        assertThat(argument.getValue())
                .isEqualTo(
                        EpochComputationGetTopTopicsStats.builder()
                                .setTopTopicCount(0)
                                .setPaddedRandomTopicsCount(0)
                                .setAppsConsideredCount(2)
                                .setSdksConsideredCount(-1)
                                .build());
    }

    @Test
    public void testGetTopTopics_selectSingleRandomTopic() {
        // In this test, in order to make test result to be deterministic so CommonClassifierHelper
        // has to be mocked to get a random topic. However, real CommonClassifierHelper need to
        // be tested as well. Therefore, real methods will be called for the other top topics.
        //
        // Initialize MockRandom. Append 3 random positive integers (20, 100, 300) to MockRandom
        // array,
        // their corresponding topicIds in the topics list will not overlap with
        // the topicIds of app1 below.
        MockRandom mockRandom = new MockRandom(new long[] {20, 100, 300});

        Map<String, List<Topic>> testAppTopics = new HashMap<>();
        // We label app1 with the first 5 topics in topics list.
        testAppTopics.put("app1", getTestTopics(Arrays.asList(253, 146, 277, 59, 127)));

        // Test the random topic with labels file in test assets.
        List<Topic> testResponse =
                getTopTopics(
                        testAppTopics,
                        testLabels,
                        mockRandom,
                        /* numberOfTopTopics */ 5,
                        /* numberOfRandomTopics */ 1,
                        mLogger);

        // The response body should contain 5 topics + 1 random topic.
        assertThat(testResponse.size()).isEqualTo(6);

        // In the following test, we need to verify that the mock random integer index
        // can match the correct topic in classifier/precomputed_test_app_list_chrome_topics.csv.
        // "random = n, topicId = m" means this topicId m is from the nth (0-indexed)
        // topicId in the topics list.
        // random = 20, topicId = 10021
        assertThat(testResponse.get(5)).isEqualTo(getTestTopic(10021));

        Map<String, List<Topic>> productionAppTopics = new HashMap<>();
        // We label app1 with the same topic IDs as testAppTopics, but using production metadata.
        productionAppTopics.put("app1", getProductionTopics(Arrays.asList(253, 146, 277, 59, 127)));

        // Test the random topic with labels file in production assets.
        List<Topic> productionResponse =
                getTopTopics(
                        productionAppTopics,
                        productionLabels,
                        new MockRandom(new long[] {50, 100, 300}),
                        /* numberOfTopTopics */ 5,
                        /* numberOfRandomTopics */ 1,
                        mLogger);

        // The response body should contain 5 topics + 1 random topic.
        assertThat(productionResponse.size()).isEqualTo(6);

        // In the following test, we need to verify that the mock random integer index
        // can match the correct topic in classifier/precomputed_app_list_chrome_topics.csv.
        // "random = n, topicId = m" means this topicId m is from the nth (0-indexed)
        // topicId in the topics list.
        // random = 50, topicId = 10051
        assertThat(productionResponse.get(5)).isEqualTo(getProductionTopic(10051));
    }

    @Test
    public void testGetTopTopics_selectMultipleRandomTopic() {
        // In this test, in order to make test result to be deterministic so CommonClassifierHelper
        // has to be mocked to get some random topics. However, real CommonClassifierHelper need to
        // be tested as well. Therefore, real methods will be called for the other top topics.
        //
        // Initialize MockRandom. Randomly select 7 indices in MockRandom, their corresponding
        // topicIds in the topics list
        // will not overlap with the topicIds of app1 below. 500 in MockRandom exceeds the length
        // of topics list, so what it represents should be 151st (500 % 349 = 151) topicId
        // in topics list.
        MockRandom mockRandom = new MockRandom(new long[] {10, 20, 50, 75, 100, 300, 500});

        Map<String, List<Topic>> appTopics = new HashMap<>();
        // The topicId we use is verticals4 and its index range is from 0 to 1918.
        // We label app1 with the first 5 topicIds in topics list.
        appTopics.put("app1", getTestTopics(Arrays.asList(34, 89, 69, 349, 241)));

        List<Topic> testResponse =
                getTopTopics(
                        appTopics,
                        testLabels,
                        mockRandom,
                        /* numberOfTopTopics */ 5,
                        /* numberOfRandomTopics */ 7,
                        mLogger);

        // The response body should contain 5 topics + 7 random topic.
        assertThat(testResponse.size()).isEqualTo(12);

        // In the following tests, we need to verify that the mock random integer index
        // can match the correct topic in classifier/precomputed_test_app_list_chrome_topics.csv.
        // "random = n, topicId = m" means this topicId m is from the nth (0-indexed)
        // topicId in the topics list.
        // random = 10, topicId = 10011
        assertThat(testResponse.get(5)).isEqualTo(getTestTopic(10011));

        // random = 20, topicId = 10021
        assertThat(testResponse.get(6)).isEqualTo(getTestTopic(10021));

        // random = 50, topicId = 10051
        assertThat(testResponse.get(7)).isEqualTo(getTestTopic(10051));

        // random = 75, topicId = 10076
        assertThat(testResponse.get(8)).isEqualTo(getTestTopic(10076));

        // random = 100, topicId = 10101
        assertThat(testResponse.get(9)).isEqualTo(getTestTopic(10101));

        // random = 300, topicId = 10301
        assertThat(testResponse.get(10)).isEqualTo(getTestTopic(10301));

        // random = 500, size of labels list is 446,
        // index should be 500 % 446 = 54, topicId = 10055
        assertThat(testResponse.get(11)).isEqualTo(getTestTopic(10055));
    }

    @Test
    public void testGetTopTopics_selectDuplicateRandomTopic() {
        ArgumentCaptor<EpochComputationGetTopTopicsStats> argument =
                ArgumentCaptor.forClass(EpochComputationGetTopTopicsStats.class);
        // In this test, in order to make test result to be deterministic so CommonClassifierHelper
        // has to be mocked to get a random topic. However, real CommonClassifierHelper need to
        // be tested as well. Therefore, real methods will be called for the other top topics.
        //
        // Initialize MockRandom. Randomly select 6 indices in MockRandom, their first 5
        // corresponding topicIds
        // in the topics list will overlap with the topicIds of app1 below.
        MockRandom mockRandom = new MockRandom(new long[] {1, 5, 10, 25, 100, 300});

        Map<String, List<Topic>> appTopics = new HashMap<>();

        // If the random topic duplicates with the real topic, then pick another random
        // one until no duplicates. In this test, we will let app1 have five topicIds of
        // 2, 6, 11, 26, 101. These topicIds are the same as the topicIds in the
        // classifier/precomputed_test_app_list_chrome_topics.csv corresponding to
        // the first five indices in the MockRandomArray.
        appTopics.put("app1", getTestTopics(Arrays.asList(2, 6, 11, 26, 101)));

        List<Topic> testResponse =
                getTopTopics(
                        appTopics,
                        testLabels,
                        mockRandom,
                        /* numberOfTopTopics */ 5,
                        /* numberOfRandomTopics */ 1,
                        mLogger);

        // The response body should contain 5 topics + 1 random topic
        assertThat(testResponse.size()).isEqualTo(6);

        // In the following tests, we need to verify that the mock random integer index
        // can match the correct topic in classifier/precomputed_test_app_list_chrome_topics.csv.
        // "random = n, topicId = m" means this topicId m is from the nth (0-indexed)
        // topicId in the topics list.
        // In this test, if we want to select a random topic that does not repeat,
        // we should select the one corresponding to the sixth index
        // in the MockRandom array topicId, i.e. random = 1, topicId = 10002
        assertThat(testResponse.get(5)).isEqualTo(getTestTopic(10002));
        verify(mLogger).logEpochComputationGetTopTopicsStats(argument.capture());
        assertThat(argument.getValue())
                .isEqualTo(
                        EpochComputationGetTopTopicsStats.builder()
                                .setTopTopicCount(5)
                                .setPaddedRandomTopicsCount(0)
                                .setAppsConsideredCount(1)
                                .setSdksConsideredCount(-1)
                                .build());
    }

    @Test
    public void testComputeTestAssetChecksum() {
        // Compute SHA256 checksum of labels topics file in test assets and check the result
        // can match the checksum saved in the test classifier assets metadata file.
        String labelsTestTopicsChecksum =
                computeClassifierAssetChecksum(sContext.getAssets(), TEST_LABELS_FILE_PATH);
        assertThat(labelsTestTopicsChecksum)
                .isEqualTo(testClassifierAssetsMetadata.get("labels_topics").get("checksum"));

        // Compute SHA256 checksum of precomputed apps topics file in test assets
        // and check the result can match the checksum saved in the classifier assets metadata file.
        String precomputedAppsTestChecksum =
                computeClassifierAssetChecksum(sContext.getAssets(), TEST_PRECOMPUTED_FILE_PATH);
        assertThat(precomputedAppsTestChecksum)
                .isEqualTo(
                        testClassifierAssetsMetadata.get("precomputed_app_list").get("checksum"));
    }

    @Test
    public void testComputeProductionAssetChecksum() {
        // Compute SHA256 checksum of labels topics file in production assets and check the result
        // can match the checksum saved in the production classifier assets metadata file.
        String labelsProductionTopicsChecksum =
                computeClassifierAssetChecksum(sContext.getAssets(), PRODUCTION_LABELS_FILE_PATH);
        assertThat(labelsProductionTopicsChecksum)
                .isEqualTo(productionClassifierAssetsMetadata.get("labels_topics").get("checksum"));

        // Compute SHA256 checksum of precomputed apps topics file in production assets
        // and check the result can match the checksum saved in the classifier assets metadata file.
        String precomputedAppsProductionChecksum =
                computeClassifierAssetChecksum(sContext.getAssets(), PRODUCTION_APPS_FILE_PATH);
        assertThat(precomputedAppsProductionChecksum)
                .isEqualTo(
                        productionClassifierAssetsMetadata
                                .get("precomputed_app_list")
                                .get("checksum"));
    }

    @Test
    public void testGetBundledModelBuildId() {
        // Verify bundled model build_id. This should be changed along with model update.
        assertThat(
                        CommonClassifierHelper.getBundledModelBuildId(
                                sContext, PRODUCTION_CLASSIFIER_ASSETS_METADATA_PATH))
                .isEqualTo(1986);
        // Verify test model build_id.
        assertThat(
                        CommonClassifierHelper.getBundledModelBuildId(
                                sContext, TEST_CLASSIFIER_ASSETS_METADATA_PATH))
                .isEqualTo(8);
    }

    private Topic getTestTopic(int topicId) {
        return Topic.create(topicId, mTestTaxonomyVersion, mTestModelVersion);
    }

    private List<Topic> getTestTopics(List<Integer> topicIds) {
        return topicIds.stream().map(this::getTestTopic).collect(Collectors.toList());
    }

    private Topic getProductionTopic(int topicId) {
        return Topic.create(topicId, mProductionTaxonomyVersion, mProductionModelVersion);
    }

    private List<Topic> getProductionTopics(List<Integer> topicIds) {
        return topicIds.stream().map(this::getProductionTopic).collect(Collectors.toList());
    }
}
