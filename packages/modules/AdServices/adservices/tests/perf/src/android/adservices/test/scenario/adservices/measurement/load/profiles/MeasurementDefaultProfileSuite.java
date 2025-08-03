/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.adservices.test.scenario.adservices.measurement.load.profiles;

import android.Manifest;
import android.adservices.test.longevity.concurrent.ProfileSuite;
import android.adservices.test.scenario.adservices.measurement.load.scenarios.CallRegisterSource;
import android.adservices.test.scenario.adservices.measurement.load.scenarios.DeviceChangeTime;
import android.adservices.test.scenario.adservices.measurement.load.scenarios.DeviceExecuteShellCommand;
import android.adservices.test.scenario.adservices.measurement.load.scenarios.ForceRunJob;
import android.adservices.test.scenario.adservices.measurement.load.utils.MeasurementMockServerDispatcher;
import android.adservices.test.scenario.adservices.utils.MockWebServerRule;
import android.content.Context;
import android.platform.test.rule.CleanPackageRule;
import android.platform.test.rule.DropCachesRule;
import android.platform.test.rule.KillAppsRule;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.service.FlagsConstants;

import com.google.mockwebserver.MockWebServer;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.junit.runners.Suite.SuiteClasses;

import java.io.IOException;

@RunWith(ProfileSuite.class)
@SuiteClasses({
    CallRegisterSource.class,
    ForceRunJob.class,
    DeviceChangeTime.class,
    DeviceExecuteShellCommand.class
})
public class MeasurementDefaultProfileSuite {
    protected static final String TAG = "MeasurementDefaultProfileSuite";
    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    protected static final int DEFAULT_PORT = 38383;
    public static MockWebServer sMockWebServer;
    public static MockWebServerRule sMockWebServerRule;

    // Class Rule to set Flags for Suite
    @ClassRule
    public static final AdServicesFlagsSetterRule sFlags =
            AdServicesFlagsSetterRule.forGlobalKillSwitchDisabledTests()
                    // Override consent manager behavior to give user consent.
                    .setSystemProperty(FlagsConstants.KEY_CONSENT_MANAGER_DEBUG_MODE, true)
                    // Override adid kill switch.
                    .setFlag(FlagsConstants.KEY_ADID_KILL_SWITCH, false)
                    // Override the flag to allow current package to call APIs.
                    .setPpapiAppAllowList("*")
                    // Override the flag to allow current package to call delete API.
                    .setMsmtWebContextClientAllowList("*")
                    // Override the flag for the global kill switch.
                    .setFlag(FlagsConstants.KEY_GLOBAL_KILL_SWITCH, false)
                    // Override measurement kill switch.
                    .setFlag(FlagsConstants.KEY_MEASUREMENT_KILL_SWITCH, false)
                    // Override measurement registration job kill switch.
                    .setFlag(
                            FlagsConstants.KEY_MEASUREMENT_REGISTRATION_JOB_QUEUE_KILL_SWITCH,
                            false)
                    // Disable enrollment checks.
                    .setFlag(FlagsConstants.KEY_DISABLE_MEASUREMENT_ENROLLMENT_CHECK, true)
                    // Disable foreground checks.
                    .setFlag(
                            FlagsConstants
                                    .KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_SOURCE,
                            true)
                    .setFlag(
                            FlagsConstants
                                    .KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_TRIGGER,
                            true)
                    // Set flag to pre seed enrollment.
                    .setFlag(FlagsConstants.KEY_ENABLE_ENROLLMENT_TEST_SEED, true)
                    // Set flag not match origin.
                    .setFlag(FlagsConstants.KEY_MEASUREMENT_ENFORCE_ENROLLMENT_ORIGIN_MATCH, false)
                    .setMeasurementTags()
                    .setCompatModeFlags();

    /** Clean Slate rule for starting run. */
    @ClassRule
    public static final RuleChain sRules =
            RuleChain.outerRule(
                            new CleanPackageRule(
                                    AdservicesTestHelper.getAdServicesPackageName(
                                            ApplicationProvider.getApplicationContext()),
                                    /* clearOnStarting */ true,
                                    /* clearOnFinished */ false))
                    .around(
                            new KillAppsRule(
                                    AdservicesTestHelper.getAdServicesPackageName(
                                            ApplicationProvider.getApplicationContext())))
                    .around(new DropCachesRule());

    /** Sets DeviceWrite to allow setting flags Sets up MockWebServer for Mock Outbound HTTPS */
    @BeforeClass
    public static void setup() throws Exception {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.WRITE_DEVICE_CONFIG);
        Log.i(TAG, "Added DeviceWriteConfig Permission");
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .executeShellCommand("settings put global auto_time false");
        sMockWebServerRule = createForHttps(DEFAULT_PORT);
        sMockWebServer =
                sMockWebServerRule.startMockWebServer(new MeasurementMockServerDispatcher());
    }

    /** Run Teardown */
    @AfterClass
    public static void tearDown() throws Exception {
        sMockWebServer.shutdown();
        Log.i(TAG, "Load Test Complete");
        Log.i(TAG, "Mock Server Requests: " + sMockWebServer.getRequestCount());
    }

    private static MockWebServerRule createForHttps(int port) {
        MockWebServerRule mockWebServerRule =
                MockWebServerRule.forHttps(
                        sContext, "adservices_measurement_test_server.p12", "adservices");
        try {
            mockWebServerRule.reserveServerListeningPort(port);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return mockWebServerRule;
    }
}
