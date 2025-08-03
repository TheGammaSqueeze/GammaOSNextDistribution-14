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

package com.android.adservices.tests.cts.topics;

import static com.android.adservices.service.FlagsConstants.KEY_TOPICS_EPOCH_JOB_PERIOD_MS;
import static com.android.adservices.service.FlagsConstants.KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.clients.topics.AdvertisingTopicsClient;
import android.adservices.topics.GetTopicsRequest;
import android.adservices.topics.GetTopicsResponse;
import android.adservices.topics.Topic;

import androidx.test.filters.FlakyTest;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public final class PreviewApiTest extends CtsTopicsEndToEndTestCase {

    // The JobId of the Epoch Computation.
    private static final int EPOCH_JOB_ID = 2;

    // Override the Epoch Job Period to this value to speed up the epoch computation.
    private static final long TEST_EPOCH_JOB_PERIOD_MS = 5_000;

    // Use 0 percent for random topic in the test so that we can verify the returned topic.
    private static final int TEST_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC = 0;

    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private static final String ADSERVICES_PACKAGE_NAME =
            AdservicesTestHelper.getAdServicesPackageName(sContext);

    @Before
    public void setup() throws Exception {
        // Kill adservices process to avoid interfering from other tests.
        AdservicesTestHelper.killAdservicesProcess(ADSERVICES_PACKAGE_NAME);

        // We need to skip 3 epochs so that if there is any usage from other test runs, it will
        // not be used for epoch retrieval.
        Thread.sleep(3 * TEST_EPOCH_JOB_PERIOD_MS);

        flags.setFlag(KEY_TOPICS_EPOCH_JOB_PERIOD_MS, TEST_EPOCH_JOB_PERIOD_MS);
        // We need to turn off random topic so that we can verify the returned topic.
        flags.setFlag(
                KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC, TEST_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC);
    }

    @Test
    @FlakyTest(bugId = 298870400)
    public void testRecordObservation() throws Exception {
        // The Test app has 2 SDKs: sdk1 calls the Topics API. This will record the usage for Sdk1
        // by default, recordObservation is true.
        AdvertisingTopicsClient advertisingTopicsClient1 =
                new AdvertisingTopicsClient.Builder()
                        .setContext(sContext)
                        .setSdkName("sdk1")
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        // Sdk2 calls the Topics API and set the Record Observation to false. This will not record
        // the usage for sdk2.
        AdvertisingTopicsClient advertisingTopicsClient2 =
                new AdvertisingTopicsClient.Builder()
                        .setContext(sContext)
                        .setSdkName("sdk2")
                        .setShouldRecordObservation(false)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        // At beginning, Sdk1 receives no topic.
        GetTopicsResponse sdk1Result = advertisingTopicsClient1.getTopics().get();
        assertThat(sdk1Result.getTopics()).isEmpty();

        // At beginning, Sdk2 receives no topic.
        GetTopicsResponse sdk2Result = advertisingTopicsClient2.getTopics().get();
        assertThat(sdk2Result.getTopics()).isEmpty();

        // Now force the Epoch Computation Job. This should be done in the same epoch for
        // callersCanLearnMap to have the entry for processing.
        forceEpochComputationJob();

        // Wait to the next epoch. We will not need to do this after we implement the fix in
        // go/rb-topics-epoch-scheduling
        Thread.sleep(TEST_EPOCH_JOB_PERIOD_MS);

        // Since the sdk1 called the Topics API in the previous Epoch, it should receive some topic.
        sdk1Result = advertisingTopicsClient1.getTopics().get();
        assertThat(sdk1Result.getTopics()).isNotEmpty();

        // We only have 1 test app which has 5 classification topics: 10147,10253,10175,10254,10333
        // in the precomputed list.
        // These 5 classification topics will become top 5 topics of the epoch since there is
        // no other apps calling Topics API.
        // The app will be assigned one random topic from one of these 5 topics.
        assertThat(sdk1Result.getTopics()).hasSize(1);
        Topic topic = sdk1Result.getTopics().get(0);

        // topic is one of the 5 classification topics of the Test App.
        assertThat(topic.getTopicId()).isIn(Arrays.asList(10147, 10253, 10175, 10254, 10333));
        assertThat(topic.getModelVersion()).isAtLeast(1L);
        assertThat(topic.getTaxonomyVersion()).isAtLeast(1L);

        // Sdk2 can not get any topics in this epoch because sdk2 sets not to record
        // observation in previous epoch.
        sdk2Result = advertisingTopicsClient2.getTopics().get();
        assertThat(sdk2Result.getTopics()).isEmpty();
    }

    // This test is to add test/line coverage for Topics API. There is no specific logic to test.
    @Test
    public void testForTopicsAPITestCoverage() {
        String sdkName = "sdk1";
        boolean shouldRecordObservation = false; // default value is true
        GetTopicsRequest.Builder builder = new GetTopicsRequest.Builder();
        builder.setAdsSdkName(sdkName);
        builder.setShouldRecordObservation(shouldRecordObservation);

        GetTopicsRequest request = builder.build();
        assertThat(request.getAdsSdkName()).isEqualTo(sdkName);
        assertThat(request.shouldRecordObservation()).isEqualTo(shouldRecordObservation);

        // Below are for test coverage purpose. There is no assertion against them.
        Topic mockedTopic =
                new Topic(/* taxonomyVersion */ 1L, /* modelVersion*/ 1L, /* topicId */ 1);
        GetTopicsResponse.Builder mockedBuilder =
                new GetTopicsResponse.Builder(List.of(mockedTopic));
        mockedBuilder.build();
    }

    /** Forces JobScheduler to run the Epoch Computation job */
    private void forceEpochComputationJob() {
        ShellUtils.runShellCommand(
                "cmd jobscheduler run -f" + " " + ADSERVICES_PACKAGE_NAME + " " + EPOCH_JOB_ID);
    }
}
