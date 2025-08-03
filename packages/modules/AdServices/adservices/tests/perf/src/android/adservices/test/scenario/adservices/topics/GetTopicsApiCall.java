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

import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_MANAGER_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_TOPICS_ENROLLMENT_CHECK;

import android.adservices.clients.topics.AdvertisingTopicsClient;
import android.adservices.topics.GetTopicsResponse;
import android.content.Context;
import android.platform.test.option.StringOption;
import android.platform.test.scenario.annotation.Scenario;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.AdServicesFlagsSetterRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Crystalball test for Topics API to collect System Heath metrics. */
@Scenario
@RunWith(JUnit4.class)
public class GetTopicsApiCall {
    private static final String TAG = "GetTopicsApiCall";

    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final String DEFAULT_SDKNAME = "sdk1";

    private static final String OPTION_SDKNAME = "sdk_name";

    // To supply value, use {@code -e sdk_name sdk1} in the instrumentation command.
    @Rule(order = 0)
    public StringOption sdkOption =
            new StringOption(OPTION_SDKNAME).setRequired(false).setDefault(DEFAULT_SDKNAME);

    @Rule(order = 1)
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.forGlobalKillSwitchDisabledTests()
                    .setTopicsKillSwitch(false)
                    .setSystemProperty(KEY_CONSENT_MANAGER_DEBUG_MODE, true)
                    .setFlag(KEY_DISABLE_TOPICS_ENROLLMENT_CHECK, true)
                    .setCompatModeFlags();

    private void measureGetTopics(String label) throws Exception {
        Log.i(TAG, "Calling getTopics()");
        final long start = System.currentTimeMillis();
        AdvertisingTopicsClient advertisingTopicsClient =
                new android.adservices.clients.topics.AdvertisingTopicsClient.Builder()
                        .setContext(sContext)
                        .setSdkName(sdkOption.get())
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        GetTopicsResponse unUsedTopicsResponse = advertisingTopicsClient.getTopics().get();

        final long duration = System.currentTimeMillis() - start;
        // TODO(b/234452723): In the future, we will want to use either statsd or perfetto instead
        // Two major benefits are that statsd or perfetto will allow you to collect field data and
        // the metrics are going to be less reliable if you collect them from logcat because they
        // are impacted by latency in writing the messages to logcat and making the API calls.
        Log.i(TAG, "(" + label + ": " + duration + ")");
    }

    @Test
    public void testTopicsManager() throws Exception {
        measureGetTopics("TOPICS_COLD_START_LATENCY_METRIC");
        // We need to sleep here to prevent going above the Rate Limit.
        Thread.sleep(1000);
        measureGetTopics("TOPICS_HOT_START_LATENCY_METRIC");
    }
}
