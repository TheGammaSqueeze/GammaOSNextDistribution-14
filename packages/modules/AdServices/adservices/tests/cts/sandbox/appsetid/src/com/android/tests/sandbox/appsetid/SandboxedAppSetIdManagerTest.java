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

package com.android.tests.sandbox.appsetid;


import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.content.Context;
import android.os.Bundle;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.compatibility.common.util.ConnectivityUtils;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

/*
 * Test AppSetId API running within the Sandbox.
 */
@RunWith(JUnit4.class)
public class SandboxedAppSetIdManagerTest {
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final String SDK_NAME = "com.android.tests.providers.appsetidsdk";

    private static final int LOAD_SDK_FROM_INTERNET_TIMEOUT_SEC = 60;

    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    @Before
    public void setup() throws TimeoutException, InterruptedException {
        // Start a foreground activity
        SimpleActivity.startAndWaitForSimpleActivity(sContext, Duration.ofMillis(1000));
        overridingBeforeTest();
    }

    @After
    public void shutDown() {
        overridingAfterTest();
        SimpleActivity.stopSimpleActivity(sContext);
    }

    @Test
    public void loadSdkAndRunAppSetIdApi() throws Exception {
        // Skip the test if it runs on unsupported platforms.
        Assume.assumeTrue(AdservicesTestHelper.isDeviceSupported());
        Assume.assumeTrue(ConnectivityUtils.isNetworkConnected(sContext));

        final SdkSandboxManager sdkSandboxManager =
                sContext.getSystemService(SdkSandboxManager.class);

        final FakeLoadSdkCallback callback =
                new FakeLoadSdkCallback(LOAD_SDK_FROM_INTERNET_TIMEOUT_SEC);

        sdkSandboxManager.loadSdk(SDK_NAME, new Bundle(), CALLBACK_EXECUTOR, callback);

        // This verifies that the appsetidsdk in the Sandbox gets back the correct appsetid.
        // If the appsetidsdk did not get correct appsetid, it will trigger the
        // callback.onLoadSdkError.
        callback.assertLoadSdkIsSuccessful("Load SDK from internet");
    }

    private void overridingBeforeTest() {
        overridingAdservicesLoggingLevel("VERBOSE");
        // The setup for this test:
        // SandboxedAppSetIdManagerTest is the test app. It will load the appsetidsdk into the
        // Sandbox.
        // The appsetidsdk (running within the Sandbox) will query AppSetId API and verify that the
        // correct
        // appsetid are returned.
        // After appsetidsdk verifies the result, it will communicate back to the
        // SandboxedAppSetIdManagerTest via the loadSdk's callback.
        // In this test, we use the loadSdk's callback as a 2-way communications between the Test
        // app (this class) and the Sdk running within the Sandbox process.

        overrideAdservicesAppSetIdKillSwitch(true);
    }

    // Reset back the original values.
    private void overridingAfterTest() {
        overridingAdservicesLoggingLevel("INFO");
        overrideAdservicesAppSetIdKillSwitch(false);
    }

    private void overridingAdservicesLoggingLevel(String loggingLevel) {
        ShellUtils.runShellCommand("setprop log.tag.adservices %s", loggingLevel);
    }

    // Override appsetid_kill_switch to ignore the effect of actual PH values.
    // If shouldOverride = true, override appsetid_kill_switch to OFF to allow adservices
    // If shouldOverride = false, override appsetid_kill_switch to meaningless value so that PhFlags
    // will
    // use the default value.
    private void overrideAdservicesAppSetIdKillSwitch(boolean shouldOverride) {
        String overrideString = shouldOverride ? "false" : "null";
        ShellUtils.runShellCommand(
                "device_config put adservices appsetid_kill_switch " + overrideString);
    }
}
