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

package android.adservices.test.scenario.adservices.topics;

import static com.android.adservices.service.FlagsConstants.KEY_CLASSIFIER_NUMBER_OF_TOP_LABELS;
import static com.android.adservices.service.FlagsConstants.KEY_CLASSIFIER_THRESHOLD;
import static com.android.adservices.service.FlagsConstants.KEY_CLASSIFIER_TYPE;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.clients.topics.AdvertisingTopicsClient;
import android.adservices.topics.GetTopicsResponse;
import android.adservices.topics.Topic;
import android.content.Context;
import android.platform.test.scenario.annotation.Scenario;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.FlakyTest;

import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Crystalball test for Topics API to test epoch computation using the On Device classifier. */
@Scenario
@RunWith(JUnit4.class)
public class TopicsEpochComputationOnDeviceClassifier {
    private static final String TAG = "TopicsEpochComputation";

    // Metric name for Crystalball test
    private static final String EPOCH_COMPUTATION_DURATION = "EPOCH_COMPUTATION_DURATION";

    // The JobId of the Epoch Computation.
    private static final int EPOCH_JOB_ID = 2;

    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private static final String ADSERVICES_PACKAGE_NAME =
            AdservicesTestHelper.getAdServicesPackageName(sContext, TAG);

    private static final DateTimeFormatter LOG_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private static final String EPOCH_COMPUTATION_START_LOG = "Start of Epoch Computation";

    private static final String EPOCH_COMPUTATION_END_LOG = "End of Epoch Computation";

    private static final String EPOCH_START_TIMESTAMP_KEY = "start";

    private static final String EPOCH_STOP_TIMESTAMP_KEY = "end";

    // Classifier test constants.
    private static final int TEST_CLASSIFIER_NUMBER_OF_TOP_LABELS = 5;
    // Each app is given topics with a confidence score between 0.0 to 1.0 float value. This
    // denotes how confident are you that a particular topic t1 is related to the app x that is
    // classified.
    // Threshold value for classifier confidence set to 0 to allow all topics and avoid filtering.
    private static final float TEST_CLASSIFIER_THRESHOLD = 0.0f;
    // ON_DEVICE_CLASSIFIER
    private static final int TEST_CLASSIFIER_TYPE = 1;
    // Use 0 percent for random topic in the test so that we can verify the returned topic.
    private static final int TEST_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC = 0;
    // Override the Epoch Job Period to this value to speed up the epoch computation.
    private static final long TEST_EPOCH_JOB_PERIOD_MS = 4000;

    @Rule
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.forTopicsPerfTests(
                            TEST_EPOCH_JOB_PERIOD_MS, TEST_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC)
                    .setFlag(KEY_CLASSIFIER_TYPE, TEST_CLASSIFIER_TYPE)
                    .setFlag(
                            KEY_CLASSIFIER_NUMBER_OF_TOP_LABELS,
                            TEST_CLASSIFIER_NUMBER_OF_TOP_LABELS)
                    .setFlag(KEY_CLASSIFIER_THRESHOLD, TEST_CLASSIFIER_THRESHOLD);

    @Before
    public void setup() throws Exception {
        // We need to skip 3 epochs so that if there is any usage from other test runs, it will
        // not be used for epoch retrieval.
        Thread.sleep(3 * TEST_EPOCH_JOB_PERIOD_MS);
    }

    @Test
    @FlakyTest(bugId = 290122696)
    public void testEpochComputation() throws Exception {
        // The Test App has 1 SDK: sdk3
        // sdk3 calls the Topics API.
        AdvertisingTopicsClient advertisingTopicsClient =
                new AdvertisingTopicsClient.Builder()
                        .setContext(sContext)
                        .setSdkName("sdk3")
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        // At beginning, Sdk3 receives no topic.
        GetTopicsResponse sdk3Result = advertisingTopicsClient.getTopics().get();
        assertThat(sdk3Result.getTopics()).isEmpty();

        Instant startTime = Clock.systemUTC().instant();
        // Now force the Epoch Computation Job. This should be done in the same epoch for
        // callersCanLearnMap to have the entry for processing.
        forceEpochComputationJob();

        // Wait to the next epoch. We will not need to do this after we implement the fix in
        // go/rb-topics-epoch-scheduling
        Thread.sleep(TEST_EPOCH_JOB_PERIOD_MS);

        // calculate and log epoch computation duration after some delay so that epoch
        // computation job is finished.
        logEpochComputationDuration(startTime);

        // Since the sdk3 called the Topics API in the previous Epoch, it should receive some topic.
        sdk3Result = advertisingTopicsClient.getTopics().get();
        assertThat(sdk3Result.getTopics()).isNotEmpty();

        // We only have 1 test app which has 5 classification topics: 10147,10253,10175,10254,10333
        // in the precomputed list.
        // These 5 classification topics will become top 5 topics of the epoch since there is
        // no other apps calling Topics API.
        // The app will be assigned one random topic from one of these 5 topics.
        assertThat(sdk3Result.getTopics()).hasSize(1);
        Topic topic = sdk3Result.getTopics().get(0);
        // Verify topic ids is valid.
        assertThat(topic.getTopicId()).isAtLeast(10000);

        assertThat(topic.getModelVersion()).isAtLeast(1L);
        assertThat(topic.getTaxonomyVersion()).isAtLeast(1L);
    }

    /** Forces JobScheduler to run the Epoch Computation job */
    private void forceEpochComputationJob() {
        ShellUtils.runShellCommand(
                "cmd jobscheduler run -f" + " " + ADSERVICES_PACKAGE_NAME + " " + EPOCH_JOB_ID);
    }

    private void logEpochComputationDuration(Instant startTime) throws Exception {
        long epoch_computation_duration =
                processLogCatStreamToGetMetricMap(getMetricsEvents(startTime));
        Log.i(TAG, "(" + EPOCH_COMPUTATION_DURATION + ": " + epoch_computation_duration + ")");
    }

    /** Return AdServices(EpochManager) logs that will be used to build the test metrics. */
    public InputStream getMetricsEvents(Instant startTime) throws IOException {
        ProcessBuilder pb =
                new ProcessBuilder(
                        Arrays.asList(
                                "logcat",
                                "-s",
                                "adservices.topics:V",
                                "-t",
                                LOG_TIME_FORMATTER.format(startTime),
                                "|",
                                "grep",
                                "Epoch"));
        return pb.start().getInputStream();
    }

    /**
     * Filters the start and end log for the epoch computation and based on that calculates the
     * duration of epoch computation. If we fail to parse the start or end log for epoch
     * computation, we catch ParseException and in the end throw an exception.
     *
     * @param inputStream the logcat stream which contains start and end time info for the epoch
     *     computation
     * @return the value of epoch computation latency
     * @throws Exception if the test failed to get the time point for epoch computation's start and
     *     end.
     */
    private Long processLogCatStreamToGetMetricMap(InputStream inputStream) throws Exception {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        Map<String, Long> output = new HashMap<String, Long>();
        bufferedReader
                .lines()
                .filter(
                        line ->
                                line.contains(EPOCH_COMPUTATION_START_LOG)
                                        || line.contains(EPOCH_COMPUTATION_END_LOG))
                .forEach(
                        line -> {
                            if (line.contains(EPOCH_COMPUTATION_START_LOG)) {
                                try {
                                    output.put(
                                            EPOCH_START_TIMESTAMP_KEY, getTimestampFromLog(line));
                                } catch (ParseException e) {
                                    Log.e(
                                            TAG,
                                            String.format(
                                                    "Caught ParseException when fetching start"
                                                            + " time for epoch computation: %s",
                                                    e.toString()));
                                }
                            } else {
                                try {
                                    output.put(EPOCH_STOP_TIMESTAMP_KEY, getTimestampFromLog(line));
                                } catch (ParseException e) {
                                    Log.e(
                                            TAG,
                                            String.format(
                                                    "Caught ParseException when fetching end time"
                                                            + " for epoch computation: %s",
                                                    e.toString()));
                                }
                            }
                        });

        if (output.containsKey(EPOCH_START_TIMESTAMP_KEY)
                && output.containsKey(EPOCH_STOP_TIMESTAMP_KEY)) {
            return output.get(EPOCH_STOP_TIMESTAMP_KEY) - output.get(EPOCH_START_TIMESTAMP_KEY);
        }
        throw new Exception("Cannot get the time of Epoch Computation's start and end");
    }

    /**
     * Parses the timestamp from the log. Example log: 10-06 17:58:20.173 14950 14966 D adservices:
     * Start of Epoch Computation
     */
    private static Long getTimestampFromLog(String log) throws ParseException {
        String[] words = log.split(" ");
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd hh:mm:ss.SSS");
        Date parsedDate = dateFormat.parse(words[0] + " " + words[1]);
        return parsedDate.getTime();
    }
}
