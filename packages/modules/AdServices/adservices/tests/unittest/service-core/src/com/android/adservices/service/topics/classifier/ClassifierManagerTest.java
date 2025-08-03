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

import static com.android.adservices.service.Flags.ON_DEVICE_CLASSIFIER;
import static com.android.adservices.service.Flags.PRECOMPUTED_CLASSIFIER;
import static com.android.adservices.service.Flags.PRECOMPUTED_THEN_ON_DEVICE_CLASSIFIER;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.service.Flags;
import com.android.adservices.service.Flags.ClassifierType;
import com.android.adservices.service.FlagsFactory;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Manager Classifier Test {@link ClassifierManager}. */
public class ClassifierManagerTest {

    private MockitoSession mStaticMockSession;

    @Mock Flags mMockFlags;

    @Mock private OnDeviceClassifier mOnDeviceClassifier;
    @Mock private PrecomputedClassifier mPrecomputedClassifier;

    private ClassifierManager mClassifierManager;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);

        // Start a mockitoSession to mock static method
        mStaticMockSession =
                ExtendedMockito.mockitoSession().spyStatic(FlagsFactory.class).startMocking();

        // Mock static method FlagsFactory.getFlags() to return Mock Flags.
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);

        mClassifierManager =
                new ClassifierManager(
                        Suppliers.memoize(() -> mOnDeviceClassifier),
                        Suppliers.memoize(() -> mPrecomputedClassifier));
    }

    @After
    public void tearDown() {
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testClassify_onDeviceClassifier() {
        // Set classifier type to on-device classifier.
        setClassifierTypeFlag(ON_DEVICE_CLASSIFIER);

        String appPackage1 = "com.example.adservices.samples.topics.sampleapp1";
        ImmutableSet<String> appPackages = ImmutableSet.of(appPackage1);
        List<Topic> onDeviceTopics = createTopics(/* TopicIds */ 123, 72);
        when(mOnDeviceClassifier.classify(eq(appPackages)))
                .thenReturn(ImmutableMap.of(appPackage1, onDeviceTopics));

        Map<String, List<Topic>> classifications = mClassifierManager.classify(appPackages);

        // Verify the topics returned are from on-device classifier.
        assertThat(classifications.get(appPackage1)).containsExactlyElementsIn(onDeviceTopics);
        // Verify mPrecomputedClassifier is not called.
        verifyZeroInteractions(mPrecomputedClassifier);
        // Verify mOnDeviceClassifier to be called once.
        verify(mOnDeviceClassifier, times(1)).classify(eq(appPackages));
    }

    @Test
    public void testClassify_preComputedClassifier() {
        // Set classifier type to pre-computed classifier.
        setClassifierTypeFlag(PRECOMPUTED_CLASSIFIER);

        String appPackage1 = "com.example.adservices.samples.topics.sampleapp1";
        ImmutableSet<String> appPackages = ImmutableSet.of(appPackage1);
        List<Topic> precomputedTopics = createTopics(/* TopicIds */ 13, 235);
        when(mPrecomputedClassifier.classify(eq(appPackages)))
                .thenReturn(ImmutableMap.of(appPackage1, precomputedTopics));

        Map<String, List<Topic>> classifications = mClassifierManager.classify(appPackages);

        // Verify the topics returned are from precomputed classifier.
        assertThat(classifications.get(appPackage1)).containsExactlyElementsIn(precomputedTopics);
        // Verify mPrecomputedClassifier to be called once.
        verify(mPrecomputedClassifier, times(1)).classify(eq(appPackages));
        // Verify mOnDeviceClassifier is not called.
        verifyZeroInteractions(mOnDeviceClassifier);
    }

    @Test
    public void testClassify_precomputedThenOnDevice_verifyPriorityToPrecomputedClassifier() {
        // Set classifier type to PRECOMPUTED_THEN_ON_DEVICE classifier.
        setClassifierTypeFlag(PRECOMPUTED_THEN_ON_DEVICE_CLASSIFIER);

        String appPackage1 = "com.example.adservices.samples.topics.sampleapp1";
        ImmutableSet<String> appPackages = ImmutableSet.of(appPackage1);
        List<Topic> precomputedTopics = createTopics(/* TopicIds */ 13, 235);
        when(mPrecomputedClassifier.classify(eq(appPackages)))
                .thenReturn(ImmutableMap.of(appPackage1, precomputedTopics));
        // Expect no packages for OnDeviceClassifier.
        when(mOnDeviceClassifier.classify(eq(ImmutableSet.of()))).thenReturn(ImmutableMap.of());

        Map<String, List<Topic>> classifications = mClassifierManager.classify(appPackages);

        // Verify the topics returned are from precomputed classifier.
        assertThat(classifications.get(appPackage1)).containsExactlyElementsIn(precomputedTopics);
        // Verify PrecomputedClassifier is called once.
        verify(mPrecomputedClassifier, times(1)).classify(eq(appPackages));
        // Verify OnDeviceClassifier is called once with empty input.
        verify(mOnDeviceClassifier, times(1)).classify(eq(ImmutableSet.of()));
    }

    @Test
    public void testClassify_precomputedThenOnDevice_emptyPrecomputedTopics() {
        // Set classifier type to PRECOMPUTED_THEN_ON_DEVICE classifier.
        setClassifierTypeFlag(PRECOMPUTED_THEN_ON_DEVICE_CLASSIFIER);

        String appPackage1 = "com.example.adservices.samples.topics.sampleapp1";
        ImmutableSet<String> appPackages = ImmutableSet.of(appPackage1);
        List<Topic> onDeviceTopics = createTopics(/* TopicIds */ 123, 72);
        when(mOnDeviceClassifier.classify(eq(appPackages)))
                .thenReturn(ImmutableMap.of(appPackage1, onDeviceTopics));
        // Empty precomputed classifier topics.
        List<Topic> precomputedTopics = createTopics();
        when(mPrecomputedClassifier.classify(eq(appPackages)))
                .thenReturn(ImmutableMap.of(appPackage1, precomputedTopics));

        Map<String, List<Topic>> classifications = mClassifierManager.classify(appPackages);

        // Verify the topics returned are from on-device classifier.
        assertThat(classifications.get(appPackage1)).containsExactlyElementsIn(onDeviceTopics);
        // Verify PrecomputedClassifier and OnDeviceClassifier to be called once.
        verify(mPrecomputedClassifier, times(1)).classify(eq(appPackages));
        verify(mOnDeviceClassifier, times(1)).classify(eq(appPackages));
    }

    @Test
    public void testClassify_precomputedThenOnDevice_missingPrecomputedTopics() {
        // Set classifier type to PRECOMPUTED_THEN_ON_DEVICE classifier.
        setClassifierTypeFlag(PRECOMPUTED_THEN_ON_DEVICE_CLASSIFIER);

        String appPackage1 = "com.example.adservices.samples.topics.sampleapp1";
        ImmutableSet<String> appPackages = ImmutableSet.of(appPackage1);
        List<Topic> onDeviceTopics = createTopics(/* TopicIds *//* TopicIds */ 123, 72);
        when(mOnDeviceClassifier.classify(eq(appPackages)))
                .thenReturn(ImmutableMap.of(appPackage1, onDeviceTopics));
        // Empty map for precomputed topics.
        when(mPrecomputedClassifier.classify(eq(appPackages))).thenReturn(ImmutableMap.of());

        Map<String, List<Topic>> classifications = mClassifierManager.classify(appPackages);

        // Verify the topics returned are from on-device classifier.
        assertThat(classifications.get(appPackage1)).containsExactlyElementsIn(onDeviceTopics);
        // Verify PrecomputedClassifier and OnDeviceClassifier to be called once.
        verify(mPrecomputedClassifier, times(1)).classify(eq(appPackages));
        verify(mOnDeviceClassifier, times(1)).classify(eq(appPackages));
    }

    @Test
    public void testClassify_precomputedThenOnDevice_bothListsAreEmpty() {
        // Set classifier type to PRECOMPUTED_THEN_ON_DEVICE classifier.
        setClassifierTypeFlag(PRECOMPUTED_THEN_ON_DEVICE_CLASSIFIER);

        String appPackage1 = "com.example.adservices.samples.topics.sampleapp1";
        ImmutableSet<String> appPackages = ImmutableSet.of(appPackage1);
        // Empty list for in device classification.
        when(mOnDeviceClassifier.classify(eq(appPackages)))
                .thenReturn(ImmutableMap.of(appPackage1, createTopics()));
        // Empty list for precomputed classification.
        when(mPrecomputedClassifier.classify(eq(appPackages)))
                .thenReturn(ImmutableMap.of(appPackage1, createTopics()));

        Map<String, List<Topic>> classifications = mClassifierManager.classify(appPackages);

        // Verify the topics returned is an empty list.
        assertThat(classifications.get(appPackage1)).isEmpty();
        // Verify PrecomputedClassifier and OnDeviceClassifier to be called once.
        verify(mPrecomputedClassifier, times(1)).classify(eq(appPackages));
        verify(mOnDeviceClassifier, times(1)).classify(eq(appPackages));
    }

    @Test
    public void testClassify_invalidClassifier_verifyDefaultBehaviour() {
        // Set classifier type to an invalid option classifier.
        setClassifierTypeFlag(11);

        String appPackage1 = "com.example.adservices.samples.topics.sampleapp1";
        ImmutableSet<String> appPackages = ImmutableSet.of(appPackage1);
        // Return different values for the same package name.
        List<Topic> precomputedTopics = createTopics(/* TopicIds */ 13, 235);
        when(mPrecomputedClassifier.classify(eq(appPackages)))
                .thenReturn(ImmutableMap.of(appPackage1, precomputedTopics));
        // Expect no packages for OnDeviceClassifier.
        when(mOnDeviceClassifier.classify(eq(ImmutableSet.of()))).thenReturn(ImmutableMap.of());

        Map<String, List<Topic>> classifications = mClassifierManager.classify(appPackages);

        // Verify the topics returned are from precomputed classifier.
        assertThat(classifications.get(appPackage1)).containsExactlyElementsIn(precomputedTopics);
        // Verify PrecomputedClassifier is called once.
        verify(mPrecomputedClassifier, times(1)).classify(eq(appPackages));
        // Verify OnDeviceClassifier is called once with empty input.
        verify(mOnDeviceClassifier, times(1)).classify(eq(ImmutableSet.of()));
    }

    @Test
    public void testClassify_onDeviceClassifierKillSwitch_fallbackToPrecomputedClassifier() {
        // Set classifier type to ON_DEVICE_CLASSIFIER, then turn on kill switch.
        setClassifierTypeFlag(ON_DEVICE_CLASSIFIER);
        when(mMockFlags.getTopicsOnDeviceClassifierKillSwitch()).thenReturn(true);

        String appPackage1 = "com.example.adservices.samples.topics.sampleapp1";
        ImmutableSet<String> appPackages = ImmutableSet.of(appPackage1);
        List<Topic> precomputedTopics = createTopics(/* TopicIds */ 13, 235);
        when(mPrecomputedClassifier.classify(eq(appPackages)))
                .thenReturn(ImmutableMap.of(appPackage1, precomputedTopics));

        Map<String, List<Topic>> classifications = mClassifierManager.classify(appPackages);

        // Verify the topics returned are from precomputed classifier.
        assertThat(classifications.get(appPackage1)).containsExactlyElementsIn(precomputedTopics);
        // Verify mPrecomputedClassifier is called once.
        verify(mPrecomputedClassifier, times(1)).classify(eq(appPackages));
        // Verify mOnDeviceClassifier is not called.
        verifyZeroInteractions(mOnDeviceClassifier);
    }

    @Test
    public void testGetTopTopics_onDeviceClassifier() {
        // Set classifier type ON_DEVICE classifier.
        setClassifierTypeFlag(ON_DEVICE_CLASSIFIER);

        String appPackage1 = "com.example.adservices.samples.topics.sampleapp1";
        String appPackage2 = "com.example.adservices.samples.topics.sampleapp2";
        Map<String, List<Topic>> appTopics =
                ImmutableMap.of(
                        appPackage1,
                        createTopics(/* TopicIds */ 1, 2, 3, 4, 5),
                        appPackage2,
                        createTopics(/* TopicIds */ 1, 2, 101, 102, 103));
        int numberOfTopTopics = 5;
        int numberOfRandomTopics = 1;

        mClassifierManager.getTopTopics(appTopics, numberOfTopTopics, numberOfRandomTopics);

        // Verify to be called once.
        verify(mOnDeviceClassifier, times(1))
                .getTopTopics(eq(appTopics), eq(numberOfTopTopics), eq(numberOfRandomTopics));
    }

    @Test
    public void testGetTopTopics_precomputedClassifier() {
        // Set classifier type to PRE_COMPUTED classifier.
        setClassifierTypeFlag(PRECOMPUTED_CLASSIFIER);

        String appPackage1 = "com.example.adservices.samples.topics.sampleapp1";
        String appPackage2 = "com.example.adservices.samples.topics.sampleapp2";
        Map<String, List<Topic>> appTopics =
                ImmutableMap.of(
                        appPackage1,
                        createTopics(/* TopicIds */ 1, 2, 3, 4, 5),
                        appPackage2,
                        createTopics(/* TopicIds */ 1, 2, 101, 102, 103));
        int numberOfTopTopics = 5;
        int numberOfRandomTopics = 1;

        mClassifierManager.getTopTopics(appTopics, numberOfTopTopics, numberOfRandomTopics);

        // Verify to be called once.
        verify(mPrecomputedClassifier, times(1))
                .getTopTopics(eq(appTopics), eq(numberOfTopTopics), eq(numberOfRandomTopics));
    }

    @Test
    public void testGetTopTopics_defaultBehaviour() {
        // Set classifier type to PRECOMPUTED_THEN_ON_DEVICE classifier.
        setClassifierTypeFlag(PRECOMPUTED_THEN_ON_DEVICE_CLASSIFIER);

        String appPackage1 = "com.example.adservices.samples.topics.sampleapp1";
        String appPackage2 = "com.example.adservices.samples.topics.sampleapp2";
        Map<String, List<Topic>> appTopics =
                ImmutableMap.of(
                        appPackage1,
                        createTopics(/* TopicIds */ 1, 2, 3, 4, 5),
                        appPackage2,
                        createTopics(/* TopicIds */ 1, 2, 101, 102, 103));
        int numberOfTopTopics = 5;
        int numberOfRandomTopics = 1;

        mClassifierManager.getTopTopics(appTopics, numberOfTopTopics, numberOfRandomTopics);

        // Verify PrecomputedClassifier to be called once by default.
        verify(mPrecomputedClassifier, times(1))
                .getTopTopics(eq(appTopics), eq(numberOfTopTopics), eq(numberOfRandomTopics));
    }

    @Test
    public void testGetTopTopics_invalidFlagValue() {
        // Set classifier type to invalid value.
        setClassifierTypeFlag(11);

        String appPackage1 = "com.example.adservices.samples.topics.sampleapp1";
        String appPackage2 = "com.example.adservices.samples.topics.sampleapp2";
        Map<String, List<Topic>> appTopics =
                ImmutableMap.of(
                        appPackage1,
                        createTopics(/* TopicIds */ 1, 2, 3, 4, 5),
                        appPackage2,
                        createTopics(/* TopicIds */ 1, 2, 101, 102, 103));
        int numberOfTopTopics = 5;
        int numberOfRandomTopics = 1;

        mClassifierManager.getTopTopics(appTopics, numberOfTopTopics, numberOfRandomTopics);

        // Verify PrecomputedClassifier to be called once by default.
        verify(mPrecomputedClassifier, times(1))
                .getTopTopics(eq(appTopics), eq(numberOfTopTopics), eq(numberOfRandomTopics));
    }

    @Test
    public void testGetTopTopics_onDeviceClassifierKillSwitch_fallbackToPrecomputedClassifier() {
        // Set classifier type to ON_DEVICE_CLASSIFIER, then turn on kill switch.
        setClassifierTypeFlag(ON_DEVICE_CLASSIFIER);
        when(mMockFlags.getTopicsOnDeviceClassifierKillSwitch()).thenReturn(true);

        String appPackage1 = "com.example.adservices.samples.topics.sampleapp1";
        String appPackage2 = "com.example.adservices.samples.topics.sampleapp2";
        Map<String, List<Topic>> appTopics =
                ImmutableMap.of(
                        appPackage1,
                        createTopics(/* TopicIds */ 1, 2, 3, 4, 5),
                        appPackage2,
                        createTopics(/* TopicIds */ 1, 2, 101, 102, 103));
        int numberOfTopTopics = 5;
        int numberOfRandomTopics = 1;

        mClassifierManager.getTopTopics(appTopics, numberOfTopTopics, numberOfRandomTopics);

        // Verify mPrecomputedClassifier is called once.
        verify(mPrecomputedClassifier, times(1))
                .getTopTopics(eq(appTopics), eq(numberOfTopTopics), eq(numberOfRandomTopics));
        // Verify mOnDeviceClassifier is not called.
        verifyZeroInteractions(mOnDeviceClassifier);
    }

    @Test
    public void testGetTopicsTaxonomy_onDeviceClassifier() {
        // Set classifier type to on-device classifier.
        setClassifierTypeFlag(ON_DEVICE_CLASSIFIER);

        List<Integer> onDeviceTaxonomy = ImmutableList.of(1, 2, 3);
        when(mOnDeviceClassifier.getLabels()).thenReturn(onDeviceTaxonomy);

        List<Integer> topicsTaxonomy = mClassifierManager.getTopicsTaxonomy();

        // Verify the topics returned are from on-device classifier.
        assertThat(topicsTaxonomy).containsExactlyElementsIn(onDeviceTaxonomy);
        // Verify mPrecomputedClassifier is not called.
        verifyZeroInteractions(mPrecomputedClassifier);
        // Verify mOnDeviceClassifier to be called once.
        verify(mOnDeviceClassifier, times(1)).getLabels();
    }

    @Test
    public void testGetTopicsTaxonomy_precomputedClassifier() {
        // Set classifier type to pre-computed classifier.
        setClassifierTypeFlag(PRECOMPUTED_CLASSIFIER);

        List<Integer> precomputedTaxonomy = ImmutableList.of(1, 2, 3);
        when(mPrecomputedClassifier.getLabels()).thenReturn(precomputedTaxonomy);

        List<Integer> topicsTaxonomy = mClassifierManager.getTopicsTaxonomy();

        // Verify the topics returned are from on-device classifier.
        assertThat(topicsTaxonomy).containsExactlyElementsIn(precomputedTaxonomy);
        // Verify mOnDeviceClassifier is not called.
        verifyZeroInteractions(mOnDeviceClassifier);
        // Verify mPrecomputedClassifier to be called once.
        verify(mPrecomputedClassifier, times(1)).getLabels();
    }

    @Test
    public void testGetTopicsTaxonomy_defaultBehaviour() {
        // Set classifier type to PRECOMPUTED_THEN_ON_DEVICE classifier.
        setClassifierTypeFlag(PRECOMPUTED_THEN_ON_DEVICE_CLASSIFIER);

        mClassifierManager.getTopicsTaxonomy();

        // Verify PrecomputedClassifier to be called once by default.
        verify(mPrecomputedClassifier, times(1)).getLabels();
    }

    @Test
    public void testGetTopicsTaxonomy_invalidFlagValue() {
        // Set classifier type to PRECOMPUTED_THEN_ON_DEVICE classifier.
        setClassifierTypeFlag(11);

        mClassifierManager.getTopicsTaxonomy();

        // Verify PrecomputedClassifier to be called once by default.
        verify(mPrecomputedClassifier, times(1)).getLabels();
    }

    @Test
    public void
            testGetTopicsTaxonomy_onDeviceClassifierKillSwitch_fallbackToPrecomputedClassifier() {
        // Set classifier type to ON_DEVICE_CLASSIFIER, then turn on kill switch.
        setClassifierTypeFlag(ON_DEVICE_CLASSIFIER);
        when(mMockFlags.getTopicsOnDeviceClassifierKillSwitch()).thenReturn(true);

        mClassifierManager.getTopicsTaxonomy();

        // Verify mPrecomputedClassifier is called once.
        verify(mPrecomputedClassifier, times(1)).getLabels();
        // Verify mOnDeviceClassifier is not called.
        verifyZeroInteractions(mOnDeviceClassifier);
    }

    private List<Topic> createTopics(/* TopicIds */ Integer... topicIds) {
        return Arrays.stream(topicIds)
                .map(topicId -> Topic.create(topicId, /*taxonomyVersion*/ 1, /*modelVersion*/ 1))
                .collect(Collectors.toList());
    }

    private void setClassifierTypeFlag(@ClassifierType int overrideValue) {
        when(mMockFlags.getClassifierType()).thenReturn(overrideValue);
    }
}
