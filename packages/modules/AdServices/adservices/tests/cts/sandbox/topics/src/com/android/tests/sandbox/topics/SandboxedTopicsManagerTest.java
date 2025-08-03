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

package com.android.tests.sandbox.topics;

import static com.android.tests.sandbox.topics.CtsSandboxedTopicsManagerTestsTestCase.TEST_EPOCH_JOB_PERIOD_MS;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.clients.topics.AdvertisingTopicsClient;
import android.adservices.topics.GetTopicsResponse;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.os.Bundle;

import androidx.test.filters.FlakyTest;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.common.annotations.SetIntegerFlag;
import com.android.adservices.common.annotations.SetLongFlag;
import com.android.adservices.service.FlagsConstants;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

/*
 * Test Topics API running within the Sandbox.
 */
@SetLongFlag(name = FlagsConstants.KEY_TOPICS_EPOCH_JOB_PERIOD_MS, value = TEST_EPOCH_JOB_PERIOD_MS)
// We need to turn off random topic so that we can verify the returned topic.
@SetIntegerFlag(name = FlagsConstants.KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC, value = 0)
public final class SandboxedTopicsManagerTest extends CtsSandboxedTopicsManagerTestsTestCase {
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final String SDK_NAME = "com.android.tests.providers.sdk1";

    // The JobId of the Epoch Computation.
    private static final int EPOCH_JOB_ID = 2;

    private final String mAdServicesPackageName =
            AdservicesTestHelper.getAdServicesPackageName(sContext, mTag);

    @Before
    public void setup() throws TimeoutException, InterruptedException {
        // Kill adservices process to avoid interfering from other tests.
        AdservicesTestHelper.killAdservicesProcess(mAdServicesPackageName);

        // We need to skip 3 epochs so that if there is any usage from other test runs, it will
        // not be used for epoch retrieval.
        Thread.sleep(3 * TEST_EPOCH_JOB_PERIOD_MS);

        // Start a foreground activity
        SimpleActivity.startAndWaitForSimpleActivity(sContext, Duration.ofMillis(1000));
    }

    @After
    public void shutDown() {
        SimpleActivity.stopSimpleActivity(sContext);
    }

    @Test
    @FlakyTest(bugId = 301370748)
    public void loadSdkAndRunTopicsApi() throws Exception {
        SdkSandboxManager sdkSandboxManager = sContext.getSystemService(SdkSandboxManager.class);

        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();

        // Let EpochJobService finish onStart() when first getting scheduled.
        Thread.sleep(TEST_EPOCH_JOB_PERIOD_MS);

        // Call Topics API once to record usage for epoch computation, so that SDK can get topics
        // when calling Topics API.
        // Note this invocation mocks SDK calling Topics API by setting SdkName. This way avoids
        // the async problem between epoch computation and Topics API invocation from SDK.
        AdvertisingTopicsClient advertisingTopicsClient =
                new AdvertisingTopicsClient.Builder()
                        .setContext(sContext)
                        .setSdkName(SDK_NAME)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();
        GetTopicsResponse response = advertisingTopicsClient.getTopics().get();
        assertThat(response.getTopics()).isEmpty();

        // Now force the Epoch Computation Job. This should be done in the same epoch for
        // callersCanLearnMap to have the entry for processing.
        forceEpochComputationJob();

        // Wait to the next epoch.
        Thread.sleep(TEST_EPOCH_JOB_PERIOD_MS);

        sdkSandboxManager.loadSdk(SDK_NAME, new Bundle(), CALLBACK_EXECUTOR, callback);

        // This verifies that the Sdk1 in the Sandbox gets back the correct topic.
        // If the Sdk1 did not get correct topic, it will trigger the callback.onLoadSdkError
        callback.assertLoadSdkIsSuccessful();
    }

    /** Forces JobScheduler to run the Epoch Computation job */
    private void forceEpochComputationJob() {
        ShellUtils.runShellCommand(
                "cmd jobscheduler run -f" + " " + mAdServicesPackageName + " " + EPOCH_JOB_ID);
    }
}
