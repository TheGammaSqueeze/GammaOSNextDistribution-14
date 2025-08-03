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

package com.android.tests.sandbox.measurement;


import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.content.Context;
import android.os.Bundle;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.common.AdservicesTestHelper;
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
 * Test Measurement APIs running within the Sandbox.
 */
@RunWith(JUnit4.class)
public class SandboxedMeasurementManagerTest {
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final String SDK_NAME = "com.android.tests.providers.sdkmeasurement";

    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    @Before
    public void setup() throws TimeoutException {
        // Skip the test if it runs on unsupported platforms.
        Assume.assumeTrue(AdservicesTestHelper.isDeviceSupported());

        // Start a foreground activity
        SimpleActivity.startAndWaitForSimpleActivity(sContext, Duration.ofMillis(10_000));

        enforceMeasurementEnrollmentCheck(true);

        // Allow sandbox package name to be able to execute Measurement APIs
        allowSandboxPackageNameAccessMeasurementApis();

        overrideMeasurementKillSwitches(true);
    }

    @After
    public void shutDown() {
        SimpleActivity.stopSimpleActivity(sContext);

        // Reset back the original values.
        resetAllowSandboxPackageNameAccessMeasurementApis();

        overrideMeasurementKillSwitches(false);
    }

    @Test
    public void loadSdkAndRunMeasurementApi() {
        // The setup for this test:
        // SandboxedMeasurementManagerTest is the test app.
        // It will load the SdkMeasurement into the Sandbox.
        // The SdkMeasurement (running within the Sandbox) will call all Measurement APIs and verify
        // no errors are thrown.
        // After SdkMeasurement finishes, it will communicate back to the
        // SandboxedMeasurementManagerTest via the loadSdk's callback.
        // In this test, we use the loadSdk's callback as a 2-way communications between the Test
        // app (this class) and the Sdk running within the Sandbox process.

        final SdkSandboxManager sdkSandboxManager =
                sContext.getSystemService(SdkSandboxManager.class);

        // The enrolled URLs should time out when registering to them, because we don't control
        // them; each timeout is 5 seconds, plus some wiggle room
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback(25);

        // Load SdkMeasurement
        sdkSandboxManager.loadSdk(SDK_NAME, new Bundle(), CALLBACK_EXECUTOR, callback);

        // This verifies SdkMeasurement finished without errors.
        callback.assertLoadSdkIsSuccessful();
    }

    private void enforceMeasurementEnrollmentCheck(boolean shouldEnforce) {
        ShellUtils.runShellCommand(
                "device_config put adservices disable_measurement_enrollment_check %s",
                !shouldEnforce);
    }

    private void allowSandboxPackageNameAccessMeasurementApis() {
        final String sdkSbxName = "com.android.tests.sandbox.measurement";
        ShellUtils.runShellCommand(
                "device_config put adservices web_context_client_allow_list " + sdkSbxName);
    }

    private void resetAllowSandboxPackageNameAccessMeasurementApis() {
        ShellUtils.runShellCommand(
                "device_config put adservices web_context_client_allow_list null");
    }

    // Override measurement related kill switch to ignore the effect of actual PH values.
    // If isOverride = true, override measurement related kill switch to OFF to allow adservices
    // If isOverride = false, override measurement related kill switch to meaningless value so that
    // PhFlags will use the default value.
    private void overrideMeasurementKillSwitches(boolean isOverride) {
        String overrideString = isOverride ? "false" : "null";
        ShellUtils.runShellCommand(
                "device_config put adservices global_kill_switch " + overrideString);
        ShellUtils.runShellCommand(
                "device_config put adservices measurement_kill_switch " + overrideString);
        ShellUtils.runShellCommand(
                "device_config put adservices measurement_api_register_source_kill_switch "
                        + overrideString);
        ShellUtils.runShellCommand(
                "device_config put adservices measurement_api_register_trigger_kill_switch "
                        + overrideString);
        ShellUtils.runShellCommand(
                "device_config put adservices measurement_api_register_web_source_kill_switch "
                        + overrideString);
        ShellUtils.runShellCommand(
                "device_config put adservices measurement_api_register_web_trigger_kill_switch "
                        + overrideString);
        ShellUtils.runShellCommand(
                "device_config put adservices measurement_api_delete_registrations_kill_switch "
                        + overrideString);
        ShellUtils.runShellCommand(
                "device_config put adservices measurement_api_status_kill_switch "
                        + overrideString);
        ShellUtils.runShellCommand(
                "device_config put adservices adid_kill_switch " + overrideString);
    }
}
