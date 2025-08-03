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

package com.android.adservices.tests.cts.topics.appupdate;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static com.android.adservices.service.FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_TOPICS;
import static com.android.adservices.service.FlagsConstants.KEY_TOPICS_DISABLE_DIRECT_APP_CALLS;
import static com.android.adservices.service.FlagsConstants.KEY_TOPICS_EPOCH_JOB_PERIOD_MS;
import static com.android.adservices.service.FlagsConstants.KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC;
import static com.android.adservices.tests.cts.topics.appupdate.CtsAdServicesTopicsAppUpdateTestCase.TEST_EPOCH_JOB_PERIOD_MS;
import static com.android.adservices.tests.cts.topics.appupdate.CtsAdServicesTopicsAppUpdateTestCase.TEST_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.clients.topics.AdvertisingTopicsClient;
import android.adservices.topics.GetTopicsResponse;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.test.filters.FlakyTest;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.common.RequiresSdkLevelAtLeastT;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * This CTS test is to test app update flow for Topics API. It has two goals:
 *
 * <ul>
 *   <li>1. To test topic assignment for newly installed apps
 *   <li>2. To test data wiping-off for uninstalled apps
 * </ul>
 *
 * <p>It communicates with test apps, which are installed/uninstalled, call Topic API, etc. The
 * basic ideas are
 *
 * <ul>
 *   <li>Start test apps from this CTS test suite.
 *   <li>Test apps call Topics API in <code>onCreate()</code> method, and sends out broadcast to
 *       this CTS test suite.
 *   <li>This CTS test suite asserts the expected results in <code>onReceived()</code>
 *   <li>Test apps will be killed once it finishes calling Topic API.
 * </ul>
 *
 * <p>Specific steps:
 *
 * <ul>
 *   <li>1. CTS Test Suite app calls topics API so that Topics API has usage at epoch T. Then
 *       trigger epoch computation and get top/returned topics at epoch T.
 *   <li>2. Install test app at epoch T+1 and it'll be assigned with returned topics at T.
 *   <li>3. Forces Maintenance job to run to reconcile app installation mismatching, in case
 *       broadcast is missed due to system delay.
 *   <li>4. Then test app calls Topics API through sdk, the sdk will be assigned with returned topic
 *       for epoch T at the serving flow. Both calls will send broadcast to this CTS test suite app
 *       and this test suite app will verify the results are expected
 *   <li>5. Uninstall test app and wait for 3 epochs.
 *   <li>6. Install test app again and it won't be assigned with topics as no usage in the past 3
 *       epoch. Call topics API again via sdk. It should have empty response as all derived data
 *       been wiped off.
 *   <li>7. Finally verify the number of broadcast received is as expected. Then uninstall test app
 *       and unregister the listener.
 * </ul>
 *
 * <p>Expected running time: ~48s.
 */
@RequiresSdkLevelAtLeastT
public final class AppUpdateTest extends CtsAdServicesTopicsAppUpdateTestCase {

    private static final String TEST_APK_NAME = "CtsSampleTopicsApp1.apk";
    private static final String TEST_APK_PATH = "/data/local/tmp/cts/install/" + TEST_APK_NAME;
    private static final String TEST_PKG_NAME = "com.android.adservices.tests.cts.topics.testapp1";
    private static final String TEST_ACTIVITY_NAME = TEST_PKG_NAME + ".MainActivity";
    private static final ComponentName COMPONENT =
            new ComponentName(TEST_PKG_NAME, TEST_ACTIVITY_NAME);

    // Broadcast sent from test apps to test suite to pass GetTopicsResponse. Use two types of
    // broadcast to make the result more explicit.
    private static final String TOPIC_RESPONSE_BROADCAST_KEY = "topicIds";
    // Test app will send this broadcast to this CTS test suite if it receives non-empty topics.
    private static final String NON_EMPTY_TOPIC_RESPONSE_BROADCAST =
            "com.android.adservices.tests.cts.topics.NON_EMPTY_TOPIC_RESPONSE";
    // Test app will send this broadcast to this CTS test suite if it receives empty topics.
    private static final String EMPTY_TOPIC_RESPONSE_BROADCAST =
            "com.android.adservices.tests.cts.topics.EMPTY_TOPIC_RESPONSE";
    private static final String SDK_NAME = "sdk";

    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private final String mAdServicesPackageName =
            AdservicesTestHelper.getAdServicesPackageName(sContext, mTag);

    // Expected topic responses used for assertion. This is the expected order of broadcasts that
    // the test sample app will send back to the main test app. So the order is important.
    private static final String[] EXPECTED_TOPIC_RESPONSE_BROADCASTS = {
        NON_EMPTY_TOPIC_RESPONSE_BROADCAST,
        EMPTY_TOPIC_RESPONSE_BROADCAST,
    };

    // The JobId of the Epoch Computation and Maintenance job.
    private static final int EPOCH_JOB_ID = 2;
    private static final int MAINTENANCE_JOB_ID = 1;

    private int mExpectedTopicResponseBroadCastIndex;
    private BroadcastReceiver mTopicsResponseReceiver;

    @Before
    public void setup() throws Exception {
        // Kill AdServices process so that background jobs don't get skipped due to starting
        // with same params.
        AdservicesTestHelper.killAdservicesProcess(mAdServicesPackageName);
        // We need to skip 3 epochs so that if there is any usage from other test runs, it will
        // not be used for epoch retrieval.
        Thread.sleep(3 * TEST_EPOCH_JOB_PERIOD_MS);

        flags.setFlag(KEY_TOPICS_EPOCH_JOB_PERIOD_MS, TEST_EPOCH_JOB_PERIOD_MS);
        // We need to turn off random topic so that we can verify the returned topic.
        flags.setFlag(
                KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC, TEST_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC);
        flags.setFlag(KEY_TOPICS_DISABLE_DIRECT_APP_CALLS, true);
        flags.setFlag(KEY_ENFORCE_FOREGROUND_STATUS_TOPICS, false);
        registerTopicResponseReceiver();
    }

    @Test
    @FlakyTest(bugId = 298870452)
    public void testAppUpdate() throws Exception {
        // Invoke Topics API once to compute top topics so that following installed test apps are
        // able to get top topics assigned when getting installed.
        AdvertisingTopicsClient advertisingTopicsClient =
                new AdvertisingTopicsClient.Builder()
                        .setContext(sContext)
                        .setSdkName(SDK_NAME)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        // At beginning, Sdk1 receives no topic.
        GetTopicsResponse sdkResponse = advertisingTopicsClient.getTopics().get();
        assertThat(sdkResponse.getTopics()).isEmpty();

        // Now force the Epoch Computation Job. This should be done in the same epoch for
        // callersCanLearnMap to have the entry for processing.
        forceEpochComputationJob();

        // Wait to the next epoch. We will not need to do this after we implement the fix in
        // go/rb-topics-epoch-scheduling
        Thread.sleep(TEST_EPOCH_JOB_PERIOD_MS);

        // Install test app1.
        installTestSampleApp();
        Thread.sleep(EXECUTION_WAITING_TIME);

        // Forces Maintenance job if broadcast for installation is missed or delayed.
        forceMaintenanceJob();
        Thread.sleep(EXECUTION_WAITING_TIME);

        // Invoke test app1. The test app1 should be assigned with topics as there are usages
        // and top topics in the last 3 epochs.
        Intent testAppIntent =
                new Intent().setComponent(COMPONENT).addFlags(FLAG_ACTIVITY_NEW_TASK);
        sContext.startActivity(testAppIntent);
        Thread.sleep(EXECUTION_WAITING_TIME);

        // Uninstall test app1. All derived data for test app1 should be wiped off.
        uninstallTestSampleApp();

        // Skip 3 epochs so that newly installed apps won't be assigned with topics.
        Thread.sleep(3 * TEST_EPOCH_JOB_PERIOD_MS);

        // Install the test app1 again. It should not be assigned with new topics.
        installTestSampleApp();
        Thread.sleep(EXECUTION_WAITING_TIME);

        // Forces Maintenance job if broadcast for installation is missed or delayed.
        // This is the second verification to justify the test even broadcast is missed.
        forceMaintenanceJob();
        Thread.sleep(EXECUTION_WAITING_TIME);

        // Invoke test app1. It should get empty returned topics because its derived data was wiped
        // off from the uninstallation.
        sContext.startActivity(testAppIntent);
        Thread.sleep(EXECUTION_WAITING_TIME);

        // Unregistered the receiver and uninstall the test app1
        // Note aosp_x86 requires --user 0 to uninstall though arm doesn't.
        sContext.unregisterReceiver(mTopicsResponseReceiver);
        uninstallTestSampleApp();

        // Finally, assert that the number of received broadcasts matches with expectation
        assertThat(mExpectedTopicResponseBroadCastIndex)
                .isEqualTo(EXPECTED_TOPIC_RESPONSE_BROADCASTS.length);
    }

    // Broadcast Receiver to receive getTopicResponse broadcast from test apps
    private void registerTopicResponseReceiver() {
        final IntentFilter topicResponseIntentFilter = new IntentFilter();
        topicResponseIntentFilter.addAction(NON_EMPTY_TOPIC_RESPONSE_BROADCAST);
        topicResponseIntentFilter.addAction(EMPTY_TOPIC_RESPONSE_BROADCAST);

        // Assert the result at each time the CTS test suite receives the broadcast. Specifically,
        // First time: test app1 should get non-empty returned topics as it calls Topics API via
        //              sdk. This is to test sdk topics assignment for newly installed apps.
        // Second time: test app1 should get empty returned topics as it wasn't assigned with topics
        //             when it gets installed due to zero usage in last 3 epochs, as well as it was
        //             uninstalled. This is to test the uninstallation behaviors.

        mTopicsResponseReceiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String action = intent.getAction();

                        // Verify the broadcast type.
                        assertThat(mExpectedTopicResponseBroadCastIndex)
                                .isLessThan(EXPECTED_TOPIC_RESPONSE_BROADCASTS.length);
                        assertThat(action)
                                .isEqualTo(
                                        EXPECTED_TOPIC_RESPONSE_BROADCASTS[
                                                mExpectedTopicResponseBroadCastIndex]);

                        // Verify the returned topics if there is any
                        if (action.equals(NON_EMPTY_TOPIC_RESPONSE_BROADCAST)) {
                            int[] topics =
                                    intent.getExtras().getIntArray(TOPIC_RESPONSE_BROADCAST_KEY);

                            // In current test infra, it has the chance that multiple tests run
                            // together. Instead of asserting the deterministic result, check if
                            // the targeted topic exists in the Topics API result.
                            assertThat(Arrays.stream(topics).boxed().collect(Collectors.toList()))
                                    .containsAnyIn(
                                            Arrays.asList(10147, 10253, 10175, 10254, 10333));
                        }

                        mExpectedTopicResponseBroadCastIndex++;
                    }
                };

        sContext.registerReceiver(
                mTopicsResponseReceiver,
                topicResponseIntentFilter,
                Context.RECEIVER_EXPORTED /*UNAUDITED*/);
    }

    // Install test sample app 1 and verify the installation.
    private void installTestSampleApp() {
        String installMessage = ShellUtils.runShellCommand("pm install -r " + TEST_APK_PATH);
        assertThat(installMessage).contains("Success");
    }

    // Note aosp_x86 requires --user 0 to uninstall though arm doesn't.
    private void uninstallTestSampleApp() {
        ShellUtils.runShellCommand("pm uninstall --user 0 " + TEST_PKG_NAME);
    }

    /** Forces JobScheduler to run the Epoch Computation job */
    private void forceEpochComputationJob() {
        ShellUtils.runShellCommand(
                "cmd jobscheduler run -f" + " " + mAdServicesPackageName + " " + EPOCH_JOB_ID);
    }

    // Forces JobScheduler to run the Maintenance job.
    private void forceMaintenanceJob() {
        ShellUtils.runShellCommand(
                "cmd jobscheduler run -f"
                        + " "
                        + mAdServicesPackageName
                        + " "
                        + MAINTENANCE_JOB_ID);
    }
}
