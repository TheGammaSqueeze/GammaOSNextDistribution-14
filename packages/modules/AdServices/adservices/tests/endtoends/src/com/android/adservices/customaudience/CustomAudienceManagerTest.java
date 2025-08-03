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

package com.android.adservices.customaudience;

import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_ENROLLMENT_TEST_SEED;
import static com.android.adservices.service.FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_CUSTOM_AUDIENCE;
import static com.android.adservices.service.FlagsConstants.KEY_SDK_REQUEST_PERMITS_PER_SECOND;

import static com.google.common.truth.Truth.assertWithMessage;

import android.Manifest;
import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.CustomAudienceManager;
import android.adservices.customaudience.FetchAndJoinCustomAudienceRequest;
import android.adservices.customaudience.LeaveCustomAudienceRequest;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.FlakyTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.LoggerFactory;
import com.android.adservices.common.AdServicesDeviceSupportedRule;
import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.common.OutcomeReceiverForTests;
import com.android.adservices.common.RequiresLowRamDevice;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

public final class CustomAudienceManagerTest {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private static final String TAG = "CustomAudienceManagerTest";
    private static final String SERVICE_APK_NAME = "com.android.adservices.api";
    private static final int MAX_RETRY = 50;

    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private static final int DELAY_TO_AVOID_THROTTLE_MS = 1001;

    // TODO(b/291488819) - Remove SDK Level check if Fledge is enabled on R.
    // Ignore tests when device is not at least S
    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    // Skip the test if it runs on unsupported platforms.
    @Rule(order = 1)
    public final AdServicesDeviceSupportedRule adServicesDeviceSupportedRule =
            new AdServicesDeviceSupportedRule();

    @Rule(order = 1)
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.forGlobalKillSwitchDisabledTests()
                    .setCompatModeFlags()
                    .setPpapiAppAllowList(CONTEXT.getPackageName())
                    .setFlag(KEY_ENABLE_ENROLLMENT_TEST_SEED, true)
                    // Disable API throttling
                    .setFlag(KEY_SDK_REQUEST_PERMITS_PER_SECOND, Integer.MAX_VALUE)
                    // This test is running in background
                    .setFlag(KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_CUSTOM_AUDIENCE, false);

    @Before
    public void setUp() throws TimeoutException {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.WRITE_DEVICE_CONFIG);

        // Kill AdServices process
        AdservicesTestHelper.killAdservicesProcess(CONTEXT);
    }

    private void measureJoinCustomAudience(String label) throws Exception {
        Log.i(TAG, "Calling joinCustomAudience()");
        Thread.sleep(DELAY_TO_AVOID_THROTTLE_MS);
        final long start = System.currentTimeMillis();

        AdvertisingCustomAudienceClient client =
                new AdvertisingCustomAudienceClient.Builder()
                        .setContext(CONTEXT)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        client.joinCustomAudience(
                        CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                                .build())
                .get();

        final long duration = System.currentTimeMillis() - start;
        Log.i(TAG, "joinCustomAudience() took " + duration + " ms: " + label);
    }

    private void measureLeaveCustomAudience(String label) throws Exception {
        Log.i(TAG, "Calling joinCustomAudience()");
        final long start = System.currentTimeMillis();

        AdvertisingCustomAudienceClient client =
                new AdvertisingCustomAudienceClient.Builder()
                        .setContext(CONTEXT)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        client.leaveCustomAudience(
                        CommonFixture.VALID_BUYER_1,
                        CustomAudienceFixture.VALID_NAME)
                .get();

        final long duration = System.currentTimeMillis() - start;
        Log.i(TAG, "joinCustomAudience() took " + duration + " ms: " + label);
    }

    @Test
    public void testCustomAudienceManager() throws Exception {
        measureJoinCustomAudience("no-kill, 1st call");
        measureJoinCustomAudience("no-kill, 2nd call");
        measureLeaveCustomAudience("no-kill, 1st call");
        measureLeaveCustomAudience("no-kill, 2nd call");
    }

    /**
     * Test to measure an "end-to-end" latency of registerSource() and registerTrigger, * when the
     * service process isn't running.
     *
     * <p>To run this test alone, use the following command. {@code atest
     * com.android.adservices.customaudience
     * .CustomAudienceManagerTest#testCallCustomAudienceAPIAfterKillingService}
     *
     * <p>Note the performance varies depending on various factors (examples below), so getting the
     * "real world number" is really hard. - What other processes are running, what they're doing,
     * and the temperature of the device, which affects the CPU clock, disk I/O performance, etc...
     * The busy the CPU is, the higher the clock gets, but that causes the CPU to become hot, which
     * then will lower the CPU clock. For micro-benchmarks, we fixate to a lower clock speed to
     * avoid fluctuation, which works okay for comparing multiple algorithms, but not a good way to
     * get the "actual" number.
     */
    // TODO(b/271338417): Remove @FlakyTest after stabilizing test
    @FlakyTest(bugId = 271338417)
    @Test
    public void testCallCustomAudienceAPIAfterKillingService() throws Exception {
        // Kill the service process, if it's already running.
        // Give the system time to calm down.
        // If we know process isn't running for sure, then we don't need it.
        Thread.sleep(1000);
        // Kill the service process.
        ShellUtils.runShellCommand("su 0 killall -9 " + SERVICE_APK_NAME);

        // TODO(b/230873929): Extract to util method.
        int count = 0;
        boolean succeed = false;
        while (count < MAX_RETRY) {
            try {
                measureJoinCustomAudience("with-kill, 1st call");
                succeed = true;
                break;
            } catch (Exception exception) {
                sLogger.e(exception, "Failure testing Custom Audience API");
                Thread.sleep(1000);
                count++;
            }
        }
        assertWithMessage("success()").that(succeed).isTrue();

        measureJoinCustomAudience("with-kill, 2nd call");
        measureLeaveCustomAudience("with-kill, 1st call");
        measureLeaveCustomAudience("with-kill, 2nd call");
    }

    @Ignore("TODO(b/295231590): remove annotation when bug is fixed")
    @Test
    @RequiresLowRamDevice
    public void testGetchAndJoinCustomAudience_lowRamDevice() throws Exception {
        OutcomeReceiverForTests<Object> receiver = new OutcomeReceiverForTests<>();

        CustomAudienceManager manager = CustomAudienceManager.get(CONTEXT);
        assertWithMessage("manager").that(manager).isNotNull();

        manager.fetchAndJoinCustomAudience(
                new FetchAndJoinCustomAudienceRequest.Builder(
                                Uri.parse("https://buyer.example.com/fetch/ca"))
                        .build(),
                CALLBACK_EXECUTOR,
                receiver);

        receiver.assertFailure(IllegalStateException.class);
    }

    @Ignore("TODO(b/295231590): remove annotation when bug is fixed")
    @Test
    @RequiresLowRamDevice
    public void testLeaveCustomAudienceRequest_lowRamDevice() throws Exception {
        OutcomeReceiverForTests<Object> receiver = new OutcomeReceiverForTests<>();
        CustomAudienceManager manager = CustomAudienceManager.get(CONTEXT);
        assertWithMessage("manager").that(manager).isNotNull();

        manager.leaveCustomAudience(
                new LeaveCustomAudienceRequest.Builder()
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setName("D.H.A.R.M.A.")
                        .build(),
                CALLBACK_EXECUTOR,
                receiver);

        receiver.assertFailure(IllegalStateException.class);
    }
}
