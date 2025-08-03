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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.EpochComputationClassifierStats;
import com.android.adservices.service.topics.CacheManager;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.mobiledatadownload.ClientConfigProto.ClientFile;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Precomputed Topics Classifier Test {@link PrecomputedClassifier}. */
public class PrecomputedClassifierTest {
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final String LABELS_FILE_PATH = "classifier/labels_topics.txt";
    private static final String APPS_FILE_PATH = "classifier/precomputed_app_list.csv";
    private static final String CLASSIFIER_ASSETS_METADATA_FILE_PATH =
            "classifier/classifier_assets_metadata.json";
    private static final String CLASSIFIER_INPUT_CONFIG_PATH =
            "classifier/classifier_input_config.txt";
    private static final String MODEL_FILE_PATH = "classifier/model.tflite";
    private PrecomputedClassifier sPrecomputedClassifier;
    private ModelManager mModelManager;
    @Mock private CacheManager mCacheManager;
    private MockitoSession mMockitoSession = null;
    @Mock private SynchronousFileStorage mMockFileStorage;
    @Mock Map<String, ClientFile> mMockDownloadedFiles;
    @Mock AdServicesLogger mLogger;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
        mMockitoSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(ModelManager.class)
                        .initMocks(this)
                        .strictness(Strictness.WARN)
                        .startMocking();

        ExtendedMockito.doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);
        ExtendedMockito.doReturn(null)
                .when(() -> ModelManager.getDownloadedFiles(any(Context.class)));

        mModelManager =
                new ModelManager(
                        sContext,
                        LABELS_FILE_PATH,
                        APPS_FILE_PATH,
                        CLASSIFIER_ASSETS_METADATA_FILE_PATH,
                        CLASSIFIER_INPUT_CONFIG_PATH,
                        MODEL_FILE_PATH,
                        mMockFileStorage,
                        mMockDownloadedFiles);
        sPrecomputedClassifier = new PrecomputedClassifier(mModelManager, mCacheManager, mLogger);
        when(mCacheManager.getTopicsWithRevokedConsent()).thenReturn(ImmutableList.of());
    }

    @After
    public void tearDown() {
        if (mMockitoSession != null) {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testClassify_existingApp() {
        ArgumentCaptor<EpochComputationClassifierStats> argument =
                ArgumentCaptor.forClass(EpochComputationClassifierStats.class);
        // Using sample App. This app has 5 classification topic.
        List<Topic> expectedSampleAppTopics =
                createRealTopics(Arrays.asList(10222, 10223, 10116, 10243, 10254));

        Map<String, List<Topic>> expectedAppTopicsResponse = new HashMap<>();
        expectedAppTopicsResponse.put(
                "com.example.adservices.samples.topics.sampleapp", expectedSampleAppTopics);

        Map<String, List<Topic>> testResponse =
                sPrecomputedClassifier.classify(
                        new HashSet<>(
                                Arrays.asList("com.example.adservices.samples.topics.sampleapp")));

        // The correct response body should be exactly the same as expectedAppTopicsResponse
        assertThat(testResponse).isEqualTo(expectedAppTopicsResponse);
        // Verify logged atom.
        verify(mLogger).logEpochComputationClassifierStats(argument.capture());
        assertThat(argument.getValue())
                .isEqualTo(
                        EpochComputationClassifierStats.builder()
                                .setTopicIds(ImmutableList.of(10222, 10223, 10116, 10243, 10254))
                                .setBuildId(1986)
                                .setAssetVersion("5")
                                .setClassifierType(
                                        EpochComputationClassifierStats.ClassifierType
                                                .PRECOMPUTED_CLASSIFIER)
                                .setOnDeviceClassifierStatus(
                                        EpochComputationClassifierStats.OnDeviceClassifierStatus
                                                .ON_DEVICE_CLASSIFIER_STATUS_NOT_INVOKED)
                                .setPrecomputedClassifierStatus(
                                        EpochComputationClassifierStats.PrecomputedClassifierStatus
                                                .PRECOMPUTED_CLASSIFIER_STATUS_SUCCESS)
                                .build());
    }

    @Test
    public void testClassify_nonExistingApp() {
        ArgumentCaptor<EpochComputationClassifierStats> argument =
                ArgumentCaptor.forClass(EpochComputationClassifierStats.class);
        // Check the non-existing app "random_app"
        Map<String, List<Topic>> testResponse =
                sPrecomputedClassifier.classify(new HashSet<>(Arrays.asList("random_app")));

        // The topics list of "random_app" should be empty
        assertThat(testResponse.get("random_app")).isEmpty();
        // Verify logged atom.
        verify(mLogger).logEpochComputationClassifierStats(argument.capture());
        assertThat(argument.getValue())
                .isEqualTo(
                        EpochComputationClassifierStats.builder()
                                .setTopicIds(ImmutableList.of())
                                .setBuildId(1986)
                                .setAssetVersion("5")
                                .setClassifierType(
                                        EpochComputationClassifierStats.ClassifierType
                                                .PRECOMPUTED_CLASSIFIER)
                                .setOnDeviceClassifierStatus(
                                        EpochComputationClassifierStats.OnDeviceClassifierStatus
                                                .ON_DEVICE_CLASSIFIER_STATUS_NOT_INVOKED)
                                .setPrecomputedClassifierStatus(
                                        EpochComputationClassifierStats.PrecomputedClassifierStatus
                                                .PRECOMPUTED_CLASSIFIER_STATUS_FAILURE)
                                .build());
    }

    @Test
    public void testClassify_appWithBlockedTopic() {
        // This sample app has five app classification topics, one of which will be blocked.
        String sampleAppPackageName = "com.example.adservices.samples.topics.sampleapp";
        int blockedTopicId = 10222;
        List<Integer> nonBlockedTopicIds = ImmutableList.of(10223, 10116, 10243, 10254);
        List<Integer> sampleAppTopicIds =
                ImmutableList.<Integer>builder()
                        .add(blockedTopicId)
                        .addAll(nonBlockedTopicIds)
                        .build();

        // Check that all five topics are initially present.
        assertThat(mModelManager.retrieveAppClassificationTopics().get(sampleAppPackageName))
                .containsExactlyElementsIn(sampleAppTopicIds);

        // Block one of the five topics.
        when(mCacheManager.getTopicsWithRevokedConsent())
                .thenReturn(ImmutableList.of(createDummyTopic(blockedTopicId)));

        List<Topic> expectedAppClassificationTopics = createRealTopics(nonBlockedTopicIds);
        Map<String, List<Topic>> classifications =
                sPrecomputedClassifier.classify(ImmutableSet.of(sampleAppPackageName));

        // The correct response should contain only the non-blocked topics.
        assertThat(classifications.get(sampleAppPackageName))
                .containsExactlyElementsIn(expectedAppClassificationTopics);
    }

    @Test
    public void testClassify_appWithAllTopicsBlocked() {
        // This sample app has five app classification topics, all of which will be blocked.
        String sampleAppPackageName = "com.example.adservices.samples.topics.sampleapp";
        List<Integer> sampleAppTopicIds = ImmutableList.of(10222, 10223, 10116, 10243, 10254);

        // Check that all five topics are initially present.
        assertThat(mModelManager.retrieveAppClassificationTopics().get(sampleAppPackageName))
                .containsExactlyElementsIn(sampleAppTopicIds);

        // Block all five topics.
        when(mCacheManager.getTopicsWithRevokedConsent())
                .thenReturn(ImmutableList.copyOf(createDummyTopics(sampleAppTopicIds)));

        Map<String, List<Topic>> classifications =
                sPrecomputedClassifier.classify(ImmutableSet.of(sampleAppPackageName));

        // The correct response should contain no topics.
        assertThat(classifications.get(sampleAppPackageName)).isEmpty();
    }

    @Test
    public void testClassify_emptyStringApp() {
        // Check if input contains empty string or null
        Map<String, List<Topic>> testResponse =
                sPrecomputedClassifier.classify(new HashSet<>(Arrays.asList("", null)));

        // The response body should be empty
        assertThat(testResponse).isEmpty();
    }

    @Test
    public void testClassify_emptyAppList() {
        // Check if input is empty
        Map<String, List<Topic>> testResponse =
                sPrecomputedClassifier.classify(new HashSet<>(new ArrayList<>()));

        // The response body should be empty
        assertThat(testResponse).isEmpty();
    }

    @Test
    public void testGetTopTopics_legalInput() {
        // construction the appTopics map so that when sorting by the number of occurrences,
        // the order of topics are:
        // topic1, topic2, topic3, topic4, topic5, ...,
        Map<String, List<Topic>> appTopics = new HashMap<>();
        appTopics.put("app1", createRealTopics(Arrays.asList(1, 2, 3, 4, 5)));
        appTopics.put("app2", createRealTopics(Arrays.asList(1, 2, 3, 4, 5)));
        appTopics.put("app3", createRealTopics(Arrays.asList(1, 2, 3, 4, 16)));
        appTopics.put("app4", createRealTopics(Arrays.asList(1, 2, 3, 13, 17)));
        appTopics.put("app5", createRealTopics(Arrays.asList(1, 2, 11, 14, 18)));
        appTopics.put("app6", createRealTopics(Arrays.asList(1, 10, 12, 15, 19)));

        // This test case should return top 5 topics from appTopics and 1 random topic
        List<Topic> testResponse =
                sPrecomputedClassifier.getTopTopics(
                        appTopics, /* numberOfTopTopics= */ 5, /* numberOfRandomTopics= */ 1);

        assertThat(testResponse.get(0)).isEqualTo(createRealTopic(1));
        assertThat(testResponse.get(1)).isEqualTo(createRealTopic(2));
        assertThat(testResponse.get(2)).isEqualTo(createRealTopic(3));
        assertThat(testResponse.get(3)).isEqualTo(createRealTopic(4));
        assertThat(testResponse.get(4)).isEqualTo(createRealTopic(5));
        // Check the random topic is not empty
        // The random topic is at the end
        assertThat(testResponse.get(5)).isNotNull();
    }

    // Creates a dummy topic.  Not suitable for tests where
    // label/model version are verified, but suitable for mocks
    // where classifier methods cannot first be called.
    private static Topic createDummyTopic(int topicId) {
        return Topic.create(topicId, 0, 0);
    }

    private static List<Topic> createDummyTopics(List<Integer> topicIds) {
        return topicIds.stream()
                .map(PrecomputedClassifierTest::createDummyTopic)
                .collect(Collectors.toList());
    }

    private Topic createRealTopic(int topicId) {
        return Topic.create(
                topicId,
                sPrecomputedClassifier.getLabelsVersion(),
                sPrecomputedClassifier.getModelVersion());
    }

    private List<Topic> createRealTopics(List<Integer> topicIds) {
        return topicIds.stream().map(this::createRealTopic).collect(Collectors.toList());
    }
}
