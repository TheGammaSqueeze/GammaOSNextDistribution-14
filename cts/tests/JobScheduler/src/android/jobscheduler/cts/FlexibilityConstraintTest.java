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

package android.jobscheduler.cts;

import static com.android.compatibility.common.util.TestUtils.waitUntil;

import android.app.job.JobInfo;
import android.content.pm.PackageManager;
import android.jobscheduler.cts.jobtestapp.TestJobSchedulerReceiver;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresDevice;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.util.Log;

import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.AppStandbyUtils;
import com.android.compatibility.common.util.BatteryUtils;
import com.android.compatibility.common.util.DeviceConfigStateHelper;
import com.android.compatibility.common.util.SystemUtil;

import java.util.Collections;
import java.util.Map;

public class FlexibilityConstraintTest extends BaseJobSchedulerTest {
    private static final String TAG = "FlexibilityConstraintTest";
    public static final int FLEXIBLE_JOB_ID = FlexibilityConstraintTest.class.hashCode();
    private static final long FLEXIBILITY_TIMEOUT_MILLIS = 5_000L * HW_TIMEOUT_MULTIPLIER;

    // Same values as in JobStatus.java
    private static final int CONSTRAINT_BATTERY_NOT_LOW = 1 << 1;
    private static final int CONSTRAINT_CHARGING = 1 << 0;
    private static final int CONSTRAINT_CONNECTIVITY = 1 << 28;
    private static final int CONSTRAINT_IDLE = 1 << 2;
    private static final int ALL_CONSTRAINTS = CONSTRAINT_BATTERY_NOT_LOW
            | CONSTRAINT_CHARGING | CONSTRAINT_CONNECTIVITY | CONSTRAINT_IDLE;

    private static final int SUPPORTED_CONSTRAINTS_AUTOMOTIVE = 0;
    private static final int SUPPORTED_CONSTRAINTS_EMBEDDED = 0;
    private static final int SUPPORTED_CONSTRAINTS_DEFAULT = ALL_CONSTRAINTS;

    private JobInfo.Builder mBuilder;
    private UiDevice mUiDevice;

    // Store previous values.
    private String mInitialDisplayTimeout;
    private String mInitialDeviceConfigSyncMode;
    private String mPreviousLowPowerTriggerLevel;

    private DeviceConfigStateHelper mAlarmManagerDeviceConfigStateHelper;
    private NetworkingHelper mNetworkingHelper;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mBuilder = new JobInfo.Builder(FLEXIBLE_JOB_ID, kJobServiceComponent);
        mUiDevice = UiDevice.getInstance(getInstrumentation());
        mNetworkingHelper = new NetworkingHelper(getInstrumentation(), getContext());

        mAlarmManagerDeviceConfigStateHelper =
                new DeviceConfigStateHelper(DeviceConfig.NAMESPACE_ALARM_MANAGER);
        mAlarmManagerDeviceConfigStateHelper
                .set("delay_nonwakeup_alarms_while_screen_off", "false");

        mInitialDeviceConfigSyncMode = mUiDevice.executeShellCommand(
                "device_config get_sync_disabled_for_tests");
        // TODO(271128261): disable sync during all JobScheduler tests
        mUiDevice.executeShellCommand(
                "device_config set_sync_disabled_for_tests until_reboot");
        // Apply
        //  * CONSTRAINT_BATTERY_NOT_LOW
        //  * CONSTRAINT_CHARGING
        //  * CONSTRAINT_CONNECTIVITY
        //  * CONSTRAINT_IDLE
        setDeviceConfigFlag("fc_applied_constraints", "268435463", false);
        setDeviceConfigFlag("fc_flexibility_deadline_proximity_limit_ms", "0", false);
        // Using jobs with no deadline, but having a short fallback deadline, lets us test jobs
        // whose lifecycle is smaller than the minimum allowed by JobStatus.
        setDeviceConfigFlag("fc_fallback_flexibility_deadlines",
                "500=100000,400=100000,300=100000,200=100000,100=100000", false);
        setDeviceConfigFlag("fc_fallback_flexibility_deadline_scores",
                "500=0,400=0,300=0,200=0,100=0", false);
        setDeviceConfigFlag("fc_min_time_between_flexibility_alarms_ms", "0", false);
        setDeviceConfigFlag("fc_percents_to_drop_flexible_constraints",
                "500=3|6|12|25"
                        + ",400=3|6|12|25"
                        + ",300=3|6|12|25"
                        + ",200=3|6|12|25"
                        + ",100=3|6|12|25",
                false);
        waitUntil("Config didn't update appropriately", 10 /* seconds */,
                () -> "268435463".equals(getConfigValue("fc_applied_constraints"))
                && "0".equals(getConfigValue("fc_flexibility_deadline_proximity_limit_ms"))
                && "500=100000,400=100000,300=100000,200=100000,100=100000"
                        .equals(getConfigValue("fc_fallback_flexibility_deadlines"))
                && "500=0,400=0,300=0,200=0,100=0"
                        .equals(getConfigValue("fc_fallback_flexibility_deadline_scores"))
                && "0".equals(getConfigValue("fc_min_time_between_flexibility_alarms_ms"))
                && "500=3|6|12|25,400=3|6|12|25,300=3|6|12|25,200=3|6|12|25,100=3|6|12|25"
                        .equals(getConfigValue("fc_percents_to_drop_flexible_constraints")));

        // Disable power save mode.
        mPreviousLowPowerTriggerLevel = Settings.Global.getString(getContext().getContentResolver(),
                Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL);
        Settings.Global.putInt(getContext().getContentResolver(),
                Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL, 0);

        // Make sure the screen doesn't turn off when the test turns it on.
        mInitialDisplayTimeout = mUiDevice.executeShellCommand(
                "settings get system screen_off_timeout");
        mUiDevice.executeShellCommand("settings put system screen_off_timeout 300000");

        // Satisfy no constraints by default.
        satisfySystemWideConstraints(false, false, false);
    }

    @Override
    public void tearDown() throws Exception {
        mJobScheduler.cancel(FLEXIBLE_JOB_ID);

        mDeviceConfigStateHelper.restoreOriginalValues();
        mAlarmManagerDeviceConfigStateHelper.restoreOriginalValues();
        mUiDevice.executeShellCommand(
                "settings put system screen_off_timeout " + mInitialDisplayTimeout);
        Settings.Global.putString(getContext().getContentResolver(),
                Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL, mPreviousLowPowerTriggerLevel);

        mNetworkingHelper.tearDown();
        mUiDevice.executeShellCommand(
                "device_config set_sync_disabled_for_tests " + mInitialDeviceConfigSyncMode);

        super.tearDown();
    }

    /**
     * Tests that flex constraints don't affect jobs on devices that don't support flex constraints.
     */
    public void testUnsupportedDevice() throws Exception {
        if (deviceSupportsAnyFlexConstraints(
                CONSTRAINT_BATTERY_NOT_LOW | CONSTRAINT_CHARGING | CONSTRAINT_IDLE)) {
            Log.d(TAG, "Skipping test since device supports constraints");
            return;
        }
        // Make it so that constraints won't drop in time.
        setDeviceConfigFlag("fc_percents_to_drop_flexible_constraints",
                "500=25|30|35|50"
                        + ",400=25|30|35|50"
                        + ",300=25|30|35|50"
                        + ",200=25|30|35|50"
                        + ",100=25|30|35|50",
                true);
        scheduleJobToExecute();

        // Job should fire even though constraints haven't dropped.
        runJob();
        assertTrue("Job didn't fire on unsupported flex constraint device",
                kTestEnvironment.awaitExecution(FLEXIBILITY_TIMEOUT_MILLIS));
    }

    public void testUnsupportedConstraint_batteryNotLow() throws Exception {
        if (deviceSupportsAllFlexConstraints(CONSTRAINT_BATTERY_NOT_LOW)) {
            Log.d(TAG, "Skipping test since device supports constraints");
            return;
        }
        if (!deviceSupportsAnyFlexConstraints(CONSTRAINT_CHARGING | CONSTRAINT_IDLE)) {
            Log.d(TAG, "Skipping test since device doesn't support constraints");
            return;
        }
        // Increase timeouts to make sure the test doesn't start passing because of transpired time.
        setDeviceConfigFlag("fc_fallback_flexibility_deadlines",
                "500=360000000,400=360000000,300=360000000,200=360000000,100=360000000",
                true);
        scheduleJobToExecute();

        assertJobNotReady();

        satisfySystemWideConstraints(true, false, true);

        runJob();
        assertTrue("Job didn't fire on unsupported constraint device",
                kTestEnvironment.awaitExecution(FLEXIBILITY_TIMEOUT_MILLIS));
    }

    public void testUnsupportedConstraint_charging() throws Exception {
        if (deviceSupportsAllFlexConstraints(CONSTRAINT_CHARGING)) {
            Log.d(TAG, "Skipping test since device supports constraints");
            return;
        }
        if (!deviceSupportsAnyFlexConstraints(CONSTRAINT_BATTERY_NOT_LOW | CONSTRAINT_IDLE)) {
            Log.d(TAG, "Skipping test since device doesn't support constraints");
            return;
        }
        // Increase timeouts to make sure the test doesn't start passing because of transpired time.
        setDeviceConfigFlag("fc_percents_to_drop_flexible_constraints",
                "500=900000|1800000|3600000|7200000"
                        + ",400=900000|1800000|3600000|7200000"
                        + ",300=900000|1800000|3600000|7200000"
                        + ",200=900000|1800000|3600000|7200000"
                        + ",100=900000|1800000|3600000|7200000",
                true);
        scheduleJobToExecute();

        assertJobNotReady();

        satisfySystemWideConstraints(false, true, true);

        runJob();
        assertTrue("Job didn't fire on unsupported constraint device",
                kTestEnvironment.awaitExecution(FLEXIBILITY_TIMEOUT_MILLIS));
    }

    public void testUnsupportedConstraint_idle() throws Exception {
        if (deviceSupportsAllFlexConstraints(CONSTRAINT_IDLE)) {
            Log.d(TAG, "Skipping test since device supports constraints");
            return;
        }
        if (!deviceSupportsAnyFlexConstraints(CONSTRAINT_BATTERY_NOT_LOW | CONSTRAINT_CHARGING)) {
            Log.d(TAG, "Skipping test since device doesn't support constraints");
            return;
        }
        // Increase timeouts to make sure the test doesn't start passing because of transpired time.
        setDeviceConfigFlag("fc_fallback_flexibility_deadlines",
                "500=360000000,400=360000000,300=360000000,200=360000000,100=360000000",
                true);
        scheduleJobToExecute();

        assertJobNotReady();

        satisfySystemWideConstraints(true, true, false);

        runJob();
        assertTrue("Job didn't fire on unsupported constraint device",
                kTestEnvironment.awaitExecution(FLEXIBILITY_TIMEOUT_MILLIS));
    }

    /**
     * Schedule a job to run, don't satisfy any constraints, then verify it runs only when no
     * constraints are required.
     */
    public void testNoConstraintSatisfied() throws Exception {
        if (!deviceSupportsAllFlexConstraints(
                CONSTRAINT_BATTERY_NOT_LOW | CONSTRAINT_CHARGING | CONSTRAINT_IDLE)) {
            Log.d(TAG, "Skipping test since device doesn't support any constraints");
            return;
        }
        setDeviceConfigFlag("fc_percents_to_drop_flexible_constraints",
                "500=3|5|7|50"
                        + ",400=3|5|7|50"
                        + ",300=3|5|7|50"
                        + ",200=3|5|7|50"
                        + ",100=3|5|7|50",
                true);
        try (TestAppInterface testAppInterface =
                     new TestAppInterface(getContext(), FLEXIBLE_JOB_ID)) {
            testAppInterface.scheduleJob(Collections.emptyMap(), Collections.emptyMap());
            testAppInterface.assertJobNotReady(FLEXIBLE_JOB_ID);

            // Wait for the first constraint to drop.
            assertFalse("Job fired before flexible constraints dropped",
                    testAppInterface.awaitJobStart(3000));

            // Remaining time before all constraints should have dropped.
            Thread.sleep(5000);
            testAppInterface.runSatisfiedJob();

            assertTrue(
                    "Job with flexible constraint did not fire when no constraints were required",
                    testAppInterface.awaitJobStart(FLEXIBILITY_TIMEOUT_MILLIS));
        }
    }

    /**
     * Schedule a job to run, verify that if all constraints are satisfied that it runs.
     */
    public void testAllConstraintsSatisfied() throws Exception {
        if (!deviceSupportsAnyFlexConstraints(
                CONSTRAINT_BATTERY_NOT_LOW | CONSTRAINT_CHARGING | CONSTRAINT_IDLE)) {
            Log.d(TAG, "Skipping test since device doesn't support any constraints");
            return;
        }
        scheduleJobToExecute();
        assertJobNotReady();

        satisfyAllSystemWideConstraints();

        runJob();
        assertTrue("Job with flexible constraint did not fire when all constraints were satisfied",
                kTestEnvironment.awaitExecution(FLEXIBILITY_TIMEOUT_MILLIS));
    }

    /**
     * Schedule a job to run, verify it runs when applied constraints are reduced to the satisfied
     * set.
     */
    public void testAppliedConstraints_batteryNotLow() throws Exception {
        if (!deviceSupportsAllFlexConstraints(CONSTRAINT_BATTERY_NOT_LOW)) {
            Log.d(TAG, "Skipping test that requires device support for battery_not_low constraint");
            return;
        }
        // The test needs the device to support at least one other constraint to test proper
        // functionality.
        if (!deviceSupportsAnyFlexConstraints(CONSTRAINT_CHARGING | CONSTRAINT_IDLE)) {
            Log.d(TAG, "Skipping test since device doesn't support any constraints");
            return;
        }
        // Increase timeouts to make sure the test doesn't start passing because of transpired time.
        setDeviceConfigFlag("fc_fallback_flexibility_deadlines",
                "500=360000000,400=360000000,300=360000000,200=360000000,100=360000000",
                true);

        scheduleJobToExecute();

        satisfySystemWideConstraints(false, true, false);

        assertJobNotReady();

        // CONSTRAINT_BATTERY_NOT_LOW
        setDeviceConfigFlag("fc_applied_constraints", "2", true);

        runJob();
        assertTrue("Job did not fire when applied constraints were satisfied",
                kTestEnvironment.awaitExecution(FLEXIBILITY_TIMEOUT_MILLIS));
    }

    /**
     * Schedule a job to run, verify it runs when applied constraints are reduced to the satisfied
     * set.
     */
    @RequiresDevice // Emulators don't always have access to wifi/network
    public void testAppliedConstraints_batteryNotLowAndConnectivity() throws Exception {
        if (!deviceSupportsAllFlexConstraints(
                CONSTRAINT_BATTERY_NOT_LOW | CONSTRAINT_CONNECTIVITY)) {
            Log.d(TAG, "Skipping test that requires device support for"
                    + " battery_not_low & connectivity constraints");
            return;
        }
        // The test needs the device to support at least one other constraint to test proper
        // functionality.
        if (!deviceSupportsAnyFlexConstraints(CONSTRAINT_CHARGING | CONSTRAINT_IDLE)) {
            Log.d(TAG, "Skipping test since device doesn't support any constraints");
            return;
        }
        if (!mNetworkingHelper.hasWifiFeature()) {
            Log.d(TAG, "Skipping test that requires the device be WiFi enabled.");
            return;
        }
        // Increase timeouts to make sure the test doesn't start passing because of transpired time.
        setDeviceConfigFlag("fc_fallback_flexibility_deadlines",
                "500=360000000,400=360000000,300=360000000,200=360000000,100=360000000",
                true);
        final boolean hasNonWifiNetwork =
                mNetworkingHelper.hasCellularNetwork() || mNetworkingHelper.hasEthernetConnection();
        if (hasNonWifiNetwork) {
            mNetworkingHelper.setAllNetworksEnabled(true);
        }
        mNetworkingHelper.setWifiState(false);

        final int connectivityJobId = FLEXIBLE_JOB_ID;
        final int nonConnectivityJobId = FLEXIBLE_JOB_ID + 1;
        try (TestAppInterface testAppInterface =
                     new TestAppInterface(getContext(), FLEXIBLE_JOB_ID)) {
            testAppInterface.scheduleJob(Collections.emptyMap(),
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_JOB_ID_KEY, connectivityJobId,
                            TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE,
                            JobInfo.NETWORK_TYPE_ANY
                    )
            );
            testAppInterface.scheduleJob(Collections.emptyMap(),
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_JOB_ID_KEY, nonConnectivityJobId
                    )
            );

            testAppInterface.assertJobNotReady(nonConnectivityJobId);
            testAppInterface.assertJobNotReady(connectivityJobId);

            // CONSTRAINT_BATTERY_NOT_LOW
            setDeviceConfigFlag("fc_applied_constraints", "2", true);

            satisfySystemWideConstraints(false, true, false);
            testAppInterface.runSatisfiedJob(nonConnectivityJobId);
            assertTrue("Job did not fire when applied constraints were satisfied",
                    testAppInterface.awaitJobStart(nonConnectivityJobId,
                            FLEXIBILITY_TIMEOUT_MILLIS));
            if (hasNonWifiNetwork) {
                // Connectivity isn't in the set of applied constraints, so the job should be able
                // to run.
                testAppInterface.runSatisfiedJob(connectivityJobId);
                assertTrue("Job did not fire when applied constraints were satisfied",
                        testAppInterface.awaitJobStart(connectivityJobId,
                                FLEXIBILITY_TIMEOUT_MILLIS));
            }

            if (mNetworkingHelper.hasEthernetConnection()) {
                // We currently can't control the ethernet connection, so skip the remainder
                // of the test.
                Log.d(TAG, "Skipping remainder of test because of an active ethernet connection");
                return;
            }

            // CONSTRAINT_BATTERY_NOT_LOW | CONSTRAINT_CONNECTIVITY
            // Connectivity job needs both to run
            setDeviceConfigFlag("fc_applied_constraints", "268435458", true);

            testAppInterface.scheduleJob(Collections.emptyMap(),
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_JOB_ID_KEY, connectivityJobId,
                            TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE,
                            JobInfo.NETWORK_TYPE_ANY
                    )
            );

            // Connectivity is now in the set of applied constraints, so the job should be held
            // back by the lack of Wi-Fi.
            testAppInterface.assertJobNotReady(connectivityJobId);

            mNetworkingHelper.setWifiState(true);

            testAppInterface.runSatisfiedJob(connectivityJobId);
            assertTrue("Job did not fire when applied constraints were satisfied",
                    testAppInterface.awaitJobStart(connectivityJobId, FLEXIBILITY_TIMEOUT_MILLIS));
        }
    }

    /**
     * Schedule a job to run, verify it runs when applied constraints are reduced to the satisfied
     * set.
     */
    public void testAppliedConstraints_charging() throws Exception {
        if (!deviceSupportsAllFlexConstraints(CONSTRAINT_CHARGING)) {
            Log.d(TAG, "Skipping test that requires device support for charging constraint");
            return;
        }
        // The test needs the device to support at least one other constraint to test proper
        // functionality.
        if (!deviceSupportsAnyFlexConstraints(CONSTRAINT_BATTERY_NOT_LOW | CONSTRAINT_IDLE)) {
            Log.d(TAG, "Skipping test since device doesn't support any constraints");
            return;
        }
        // Increase timeouts to make sure the test doesn't start passing because of transpired time.
        setDeviceConfigFlag("fc_fallback_flexibility_deadlines",
                "500=360000000,400=360000000,300=360000000,200=360000000,100=360000000",
                true);

        scheduleJobToExecute();

        satisfySystemWideConstraints(true, false, false);

        assertJobNotReady();

        // CONSTRAINT_CHARGING
        setDeviceConfigFlag("fc_applied_constraints", "1", true);

        runJob();
        assertTrue("Job did not fire when applied constraints were satisfied",
                kTestEnvironment.awaitExecution(FLEXIBILITY_TIMEOUT_MILLIS));
    }

    /**
     * Schedule a job to run, verify it runs when applied constraints are reduced to the satisfied
     * set.
     */
    @RequiresDevice // Emulators don't always have access to wifi/network
    public void testAppliedConstraints_connectivity() throws Exception {
        if (!deviceSupportsAllFlexConstraints(CONSTRAINT_CONNECTIVITY)) {
            Log.d(TAG, "Skipping test that requires device support for connectivity constraint");
            return;
        }
        // The test needs the device to support at least one other constraint to test proper
        // functionality.
        if (!deviceSupportsAnyFlexConstraints(
                CONSTRAINT_BATTERY_NOT_LOW | CONSTRAINT_CHARGING | CONSTRAINT_IDLE)) {
            Log.d(TAG, "Skipping test since device doesn't support any constraints");
            return;
        }
        if (!mNetworkingHelper.hasWifiFeature()) {
            Log.d(TAG, "Skipping test that requires the device be WiFi enabled.");
            return;
        }
        // Increase timeouts to make sure the test doesn't start passing because of transpired time.
        setDeviceConfigFlag("fc_fallback_flexibility_deadlines",
                "500=360000000,400=360000000,300=360000000,200=360000000,100=360000000",
                true);
        mNetworkingHelper.setWifiState(false);

        mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        scheduleJobToExecute();

        mNetworkingHelper.setWifiState(true);

        assertJobNotReady();

        // CONSTRAINT_CONNECTIVITY
        setDeviceConfigFlag("fc_applied_constraints", "268435456", true);

        runJob();
        assertTrue("Job did not fire when applied constraints were satisfied",
                kTestEnvironment.awaitExecution(FLEXIBILITY_TIMEOUT_MILLIS));
    }

    /**
     * Schedule a job to run, verify it runs when applied constraints are reduced to the satisfied
     * set.
     */
    public void testAppliedConstraints_idle() throws Exception {
        if (!deviceSupportsAllFlexConstraints(CONSTRAINT_IDLE)) {
            Log.d(TAG, "Skipping test that requires device support for idle constraint");
            return;
        }
        // The test needs the device to support at least one other constraint to test proper
        // functionality.
        if (!deviceSupportsAnyFlexConstraints(CONSTRAINT_BATTERY_NOT_LOW | CONSTRAINT_CHARGING)) {
            Log.d(TAG, "Skipping test since device doesn't support any constraints");
            return;
        }
        // Increase timeouts to make sure the test doesn't start passing because of transpired time.
        setDeviceConfigFlag("fc_fallback_flexibility_deadlines",
                "500=360000000,400=360000000,300=360000000,200=360000000,100=360000000",
                true);

        scheduleJobToExecute();

        satisfySystemWideConstraints(false, false, true);

        assertJobNotReady();

        // CONSTRAINT_IDLE
        setDeviceConfigFlag("fc_applied_constraints", "4", true);

        runJob();
        assertTrue("Job did not fire when applied constraints were satisfied",
                kTestEnvironment.awaitExecution(FLEXIBILITY_TIMEOUT_MILLIS));
    }

    /**
     * Schedule an expedited job, verify it runs immediately.
     */
    public void testExpeditedJobBypassesFlexibility() throws Exception {
        mBuilder.setExpedited(true);
        scheduleJobToExecute();
        runJob();
        assertTrue("Expedited job did not start.",
                kTestEnvironment.awaitExecution(FLEXIBILITY_TIMEOUT_MILLIS));
    }

    /**
     * Verify default jobs of allowlisted apps are excluded
     */
    public void testAllowlistBypassesFlexibility() throws Exception {
        if (!AppStandbyUtils.isAppStandbyEnabled()) {
            return;
        }
        if (!deviceSupportsAnyFlexConstraints(
                CONSTRAINT_BATTERY_NOT_LOW | CONSTRAINT_CHARGING | CONSTRAINT_IDLE)) {
            Log.d(TAG, "Skipping test since device doesn't support any constraints");
            return;
        }

        // Increase timeouts to make sure the test doesn't start passing because of transpired time.
        setDeviceConfigFlag("fc_fallback_flexibility_deadlines",
                "500=360000000,400=360000000,300=360000000,200=360000000,100=360000000",
                true);
        satisfySystemWideConstraints(false, false, false);

        final int jobIdHigh = FLEXIBLE_JOB_ID;
        final int jobIdDefault = FLEXIBLE_JOB_ID + 1;
        final int jobIdLow = FLEXIBLE_JOB_ID + 2;
        final int jobIdMin = FLEXIBLE_JOB_ID + 3;
        try (TestAppInterface testAppInterface =
                     new TestAppInterface(getContext(), FLEXIBLE_JOB_ID)) {
            SystemUtil.runShellCommand(
                    "cmd deviceidle whitelist +" + TestAppInterface.TEST_APP_PACKAGE);
            testAppInterface.scheduleJob(Collections.emptyMap(),
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_JOB_ID_KEY, jobIdHigh,
                            TestJobSchedulerReceiver.EXTRA_PRIORITY, JobInfo.PRIORITY_HIGH
                    )
            );
            testAppInterface.scheduleJob(Collections.emptyMap(),
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_JOB_ID_KEY, jobIdDefault,
                            TestJobSchedulerReceiver.EXTRA_PRIORITY, JobInfo.PRIORITY_DEFAULT
                    )
            );
            testAppInterface.scheduleJob(Collections.emptyMap(),
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_JOB_ID_KEY, jobIdLow,
                            TestJobSchedulerReceiver.EXTRA_PRIORITY, JobInfo.PRIORITY_LOW
                    )
            );
            testAppInterface.scheduleJob(Collections.emptyMap(),
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_JOB_ID_KEY, jobIdMin,
                            TestJobSchedulerReceiver.EXTRA_PRIORITY, JobInfo.PRIORITY_MIN
                    )
            );
            assertTrue("High priority job did not start.",
                    testAppInterface.awaitJobStart(jobIdHigh, FLEXIBILITY_TIMEOUT_MILLIS));
            assertTrue("Default priority job did not start.",
                    testAppInterface.awaitJobStart(jobIdDefault, FLEXIBILITY_TIMEOUT_MILLIS));
            assertFalse("Low priority job started.",
                    testAppInterface.awaitJobStart(jobIdLow, FLEXIBILITY_TIMEOUT_MILLIS));
            assertFalse("Min priority job started.",
                    testAppInterface.awaitJobStart(jobIdMin, FLEXIBILITY_TIMEOUT_MILLIS));
        }
    }

    /**
     * Verify default jobs of FGS apps are excluded
     */
    public void testFgsBypassesFlexibility() throws Exception {
        if (!deviceSupportsAnyFlexConstraints(
                CONSTRAINT_BATTERY_NOT_LOW | CONSTRAINT_CHARGING | CONSTRAINT_IDLE)) {
            Log.d(TAG, "Skipping test since device doesn't support any constraints");
            return;
        }

        // Increase timeouts to make sure the test doesn't start passing because of transpired time.
        setDeviceConfigFlag("fc_fallback_flexibility_deadlines",
                "500=360000000,400=360000000,300=360000000,200=360000000,100=360000000",
                true);
        satisfySystemWideConstraints(false, false, false);

        final int jobIdHigh = FLEXIBLE_JOB_ID;
        final int jobIdDefault = FLEXIBLE_JOB_ID + 1;
        final int jobIdLow = FLEXIBLE_JOB_ID + 2;
        final int jobIdMin = FLEXIBLE_JOB_ID + 3;
        try (TestAppInterface testAppInterface =
                     new TestAppInterface(getContext(), FLEXIBLE_JOB_ID)) {
            toggleScreenOn(true);
            testAppInterface.startAndKeepTestActivity(true);
            testAppInterface.startFgs();
            testAppInterface.closeActivity(true);
            toggleScreenOn(false);
            testAppInterface.scheduleJob(Collections.emptyMap(),
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_JOB_ID_KEY, jobIdHigh,
                            TestJobSchedulerReceiver.EXTRA_PRIORITY, JobInfo.PRIORITY_HIGH
                    )
            );
            testAppInterface.scheduleJob(Collections.emptyMap(),
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_JOB_ID_KEY, jobIdDefault,
                            TestJobSchedulerReceiver.EXTRA_PRIORITY, JobInfo.PRIORITY_DEFAULT
                    )
            );
            testAppInterface.scheduleJob(Collections.emptyMap(),
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_JOB_ID_KEY, jobIdLow,
                            TestJobSchedulerReceiver.EXTRA_PRIORITY, JobInfo.PRIORITY_LOW
                    )
            );
            testAppInterface.scheduleJob(Collections.emptyMap(),
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_JOB_ID_KEY, jobIdMin,
                            TestJobSchedulerReceiver.EXTRA_PRIORITY, JobInfo.PRIORITY_MIN
                    )
            );
            assertTrue("High priority job did not start.",
                    testAppInterface.awaitJobStart(jobIdHigh, FLEXIBILITY_TIMEOUT_MILLIS));
            assertTrue("Default priority job did not start.",
                    testAppInterface.awaitJobStart(jobIdDefault, FLEXIBILITY_TIMEOUT_MILLIS));
            assertFalse("Low priority job started.",
                    testAppInterface.awaitJobStart(jobIdLow, FLEXIBILITY_TIMEOUT_MILLIS));
            assertFalse("Min priority job started.",
                    testAppInterface.awaitJobStart(jobIdMin, FLEXIBILITY_TIMEOUT_MILLIS));
        }
    }

    /**
     * Verify that an already running job doesn't get stopped because of flex policy.
     */
    public void testRunningJobBypassesFlexibility() throws Exception {
        if (!deviceSupportsAnyFlexConstraints(
                CONSTRAINT_BATTERY_NOT_LOW | CONSTRAINT_CHARGING | CONSTRAINT_IDLE)) {
            Log.d(TAG, "Skipping test since device doesn't support any constraints");
            return;
        }
        try (TestAppInterface testAppInterface =
                     new TestAppInterface(getContext(), FLEXIBLE_JOB_ID)) {
            testAppInterface.scheduleJob(Collections.emptyMap(),
                    Map.of(TestJobSchedulerReceiver.EXTRA_JOB_ID_KEY, FLEXIBLE_JOB_ID));

            satisfyAllSystemWideConstraints();

            testAppInterface.runSatisfiedJob();
            assertTrue(
                    "Job with flexible constraint did not fire when all constraints were satisfied",
                    testAppInterface.awaitJobStart(FLEXIBILITY_TIMEOUT_MILLIS));

            satisfySystemWideConstraints(false, false, false);
            assertFalse(
                    "Job stopped when flex constraints became unsatisfied",
                    testAppInterface.awaitJobStop(FLEXIBILITY_TIMEOUT_MILLIS));
        }
    }

    /**
     * Verify default jobs of TOP apps are excluded
     */
    public void testTopBypassesFlexibility() throws Exception {
        // Increase timeouts to make sure the test doesn't start passing because of transpired time.
        setDeviceConfigFlag("fc_fallback_flexibility_deadlines",
                "500=360000000,400=360000000,300=360000000,200=360000000,100=360000000",
                true);
        satisfySystemWideConstraints(false, false, false);
        toggleScreenOn(true);

        final int jobIdHigh = FLEXIBLE_JOB_ID;
        final int jobIdDefault = FLEXIBLE_JOB_ID + 1;
        final int jobIdLow = FLEXIBLE_JOB_ID + 2;
        final int jobIdMin = FLEXIBLE_JOB_ID + 3;
        try (TestAppInterface testAppInterface =
                     new TestAppInterface(getContext(), FLEXIBLE_JOB_ID)) {
            testAppInterface.startAndKeepTestActivity(true);
            testAppInterface.scheduleJob(Collections.emptyMap(),
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_JOB_ID_KEY, jobIdHigh,
                            TestJobSchedulerReceiver.EXTRA_PRIORITY, JobInfo.PRIORITY_HIGH
                    )
            );
            testAppInterface.scheduleJob(Collections.emptyMap(),
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_JOB_ID_KEY, jobIdDefault,
                            TestJobSchedulerReceiver.EXTRA_PRIORITY, JobInfo.PRIORITY_DEFAULT
                    )
            );
            testAppInterface.scheduleJob(Collections.emptyMap(),
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_JOB_ID_KEY, jobIdLow,
                            TestJobSchedulerReceiver.EXTRA_PRIORITY, JobInfo.PRIORITY_LOW
                    )
            );
            testAppInterface.scheduleJob(Collections.emptyMap(),
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_JOB_ID_KEY, jobIdMin,
                            TestJobSchedulerReceiver.EXTRA_PRIORITY, JobInfo.PRIORITY_MIN
                    )
            );
            assertTrue("High priority job did not start.",
                    testAppInterface.awaitJobStart(jobIdHigh, FLEXIBILITY_TIMEOUT_MILLIS));
            assertTrue("Default priority job did not start.",
                    testAppInterface.awaitJobStart(jobIdDefault, FLEXIBILITY_TIMEOUT_MILLIS));
            assertTrue("Low priority job did not start.",
                    testAppInterface.awaitJobStart(jobIdLow, FLEXIBILITY_TIMEOUT_MILLIS));
            assertTrue("Min priority job did not start.",
                    testAppInterface.awaitJobStart(jobIdMin, FLEXIBILITY_TIMEOUT_MILLIS));
        }
    }

    /**
     * Set the deadline proximity limit to close to a jobs enqueue time, verify it runs without
     * satisfied constraints past that limit.
     */
    public void testCutoffWindowRemovesConstraints() throws Exception {
        if (!deviceSupportsAllFlexConstraints(
                CONSTRAINT_BATTERY_NOT_LOW | CONSTRAINT_CHARGING | CONSTRAINT_IDLE)) {
            Log.d(TAG, "Skipping test that requires device support required constraints");
            return;
        }
        setDeviceConfigFlag("fc_flexibility_deadline_proximity_limit_ms", "95000", true);
        // Let Flexibility Controller update.
        Thread.sleep(1_000L);

        scheduleJobToExecute();
        assertJobNotReady();

        // Wait for constraints to drop.
        Thread.sleep(10_000L);

        runJob();
        assertTrue("Job with flexible constraint did not fire when the deadline was close",
                kTestEnvironment.awaitExecution(FLEXIBILITY_TIMEOUT_MILLIS));
    }

    /**
     * Schedule a job, wait for one constraint to be required, verify the job runs if the device
     * becomes idle.
     */
    public void testIdleSatisfiesFlexibleConstraints() throws Exception {
        if (!deviceSupportsAllFlexConstraints(
                CONSTRAINT_BATTERY_NOT_LOW | CONSTRAINT_CHARGING | CONSTRAINT_IDLE)) {
            Log.d(TAG, "Skipping test that requires device support required constraints");
            return;
        }
        scheduleJobToExecute();
        assertJobNotReady();

        // Wait for all but one constraint to drop.
        Thread.sleep(6_000);
        assertJobNotReady();
        toggleIdle(true);

        runJob();
        assertTrue("Job with flexible constraint did not fire"
                        + " when idle was satisfied",
                kTestEnvironment.awaitExecution(FLEXIBILITY_TIMEOUT_MILLIS));
    }

    /**
     * Schedule a job, wait for one constraint to be required, verify the job runs if the device
     * is charging.
     */
    public void testChargingSatisfiesFlexibleConstraints() throws Exception {
        if (!deviceSupportsAllFlexConstraints(
                CONSTRAINT_BATTERY_NOT_LOW | CONSTRAINT_CHARGING | CONSTRAINT_IDLE)) {
            Log.d(TAG, "Skipping test that requires device support required constraints");
            return;
        }
        scheduleJobToExecute();
        assertJobNotReady();

        // Wait for all but one constraint to drop.
        Thread.sleep(6_000);
        satisfySystemWideConstraints(true, false, false);

        runJob();
        assertTrue("Job with flexible constraint did not fire when charging was satisfied",
                kTestEnvironment.awaitExecution(FLEXIBILITY_TIMEOUT_MILLIS));
    }

    /**
     * Schedule a job, wait for one constraint to be required, verify the job runs if the device
     * is battery not low.
     */
    public void testBatteryNotLowSatisfiesFlexibleConstraints() throws Exception {
        if (!deviceSupportsAllFlexConstraints(
                CONSTRAINT_BATTERY_NOT_LOW | CONSTRAINT_CHARGING | CONSTRAINT_IDLE)) {
            Log.d(TAG, "Skipping test that requires device support required constraints");
            return;
        }
        scheduleJobToExecute();
        assertJobNotReady();

        // Wait for all but one constraint to drop.
        Thread.sleep(6_000);

        assertJobNotReady();
        satisfySystemWideConstraints(false, true, false);

        runJob();
        assertTrue("Job with flexible constraint did not fire when battery not low was satisfied",
                kTestEnvironment.awaitExecution(FLEXIBILITY_TIMEOUT_MILLIS));
    }

    /**
     * Schedule a job that requires any network, wait for one constraint to be required,
     * verify the job runs if the device is connected to wifi.
     */
    @RequiresDevice // Emulators don't always have access to wifi/network
    public void testWifiSatisfiesFlexibleConstraints() throws Exception {
        if (!deviceSupportsAllFlexConstraints(
                CONSTRAINT_BATTERY_NOT_LOW | CONSTRAINT_CHARGING | CONSTRAINT_IDLE)) {
            Log.d(TAG, "Skipping test that requires device support required constraints");
            return;
        }
        if (!deviceSupportsFlexTransportAffinities()) {
            return;
        }
        if (mNetworkingHelper.hasEthernetConnection()) {
            Log.d(TAG, "Can't run test with an active ethernet connection");
            return;
        }
        if (!mNetworkingHelper.hasWifiFeature()) {
            Log.d(TAG, "Skipping test that requires the device be WiFi enabled.");
            return;
        }
        if (!mNetworkingHelper.hasCellularNetwork()) {
            Log.d(TAG, "Skipping test that requires a cellular network");
            return;
        }

        // Switch device to cellular network
        mNetworkingHelper.setAirplaneMode(false);
        mNetworkingHelper.setWifiState(false);

        mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        scheduleJobToExecute();

        assertJobNotReady();

        // Wait for all but one constraint to drop.
        Thread.sleep(12_000);

        assertJobNotReady();
        mNetworkingHelper.setWifiState(true);

        runJob();

        assertTrue("Job with flexible constraint did not fire when connectivity was satisfied",
                kTestEnvironment.awaitExecution(FLEXIBILITY_TIMEOUT_MILLIS));
    }

    /**
     * Schedule a job that requires no network.
     * Verify it only requires 3 flexible constraints.
     */
    public void testPreferredTransport_RequiredNone() throws Exception {
        if (!deviceSupportsAnyFlexConstraints(
                CONSTRAINT_BATTERY_NOT_LOW | CONSTRAINT_CHARGING | CONSTRAINT_IDLE)) {
            Log.d(TAG, "Skipping test that requires device support any required constraints");
            return;
        }
        mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE);
        scheduleJobToExecute();
        assertJobNotReady();

        satisfyAllSystemWideConstraints();

        runJob();
        assertTrue("Job with flexible constraint did not fire when wifi was not required",
                kTestEnvironment.awaitExecution(FLEXIBILITY_TIMEOUT_MILLIS));
    }

    /**
     * Schedule a job that requires a cellular network.
     * Verify it only requires 3 flexible constraints.
     */
    @RequiresDevice // Emulators don't always have access to wifi/network
    public void testPreferredTransport_RequiredCellular() throws Exception {
        if (!deviceSupportsAllFlexConstraints(
                CONSTRAINT_BATTERY_NOT_LOW | CONSTRAINT_CHARGING | CONSTRAINT_IDLE)) {
            Log.d(TAG, "Skipping test that requires device support required constraints");
            return;
        }
        if (!deviceSupportsFlexTransportAffinities()) {
            return;
        }
        if (!mNetworkingHelper.hasCellularNetwork()) {
            Log.d(TAG, "Skipping test that requires a cellular network");
            return;
        }

        mNetworkingHelper.setAirplaneMode(false);
        if (mNetworkingHelper.hasWifiFeature()) {
            mNetworkingHelper.setWifiState(false);
        }
        mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_CELLULAR);
        scheduleJobToExecute();
        assertJobNotReady();
        // Wait for one constraint to drop.
        Thread.sleep(3_000);
        assertJobNotReady();
        satisfyAllSystemWideConstraints();

        runJob();
        assertTrue("Job with flexible constraint did not fire when wifi was not required",
                kTestEnvironment.awaitExecution(FLEXIBILITY_TIMEOUT_MILLIS));
    }

    /**
     * Schedule a job that requires any network, confirm that the preferred transport logic doesn't
     * apply when it's not defined for the device.
     */
    @RequiresDevice // Emulators don't always have access to wifi/network
    public void testPreferredTransport_UnsupportedDevice() throws Exception {
        if (!deviceSupportsAnyFlexConstraints(
                CONSTRAINT_BATTERY_NOT_LOW | CONSTRAINT_CHARGING | CONSTRAINT_IDLE)) {
            Log.d(TAG, "Skipping test that requires device support required constraints");
            return;
        }
        if (deviceSupportsFlexTransportAffinities()) {
            return;
        }
        if (mNetworkingHelper.hasEthernetConnection()) {
            Log.d(TAG, "Can't run test with an active ethernet connection");
            return;
        }
        if (!mNetworkingHelper.hasCellularNetwork()) {
            Log.d(TAG, "Skipping test that requires a cellular network");
            return;
        }

        // Switch device to cellular network
        mNetworkingHelper.setAirplaneMode(false);
        if (mNetworkingHelper.hasWifiFeature()) {
            mNetworkingHelper.setWifiState(false);
        }

        mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        scheduleJobToExecute();

        assertJobNotReady();

        satisfyAllSystemWideConstraints();

        runJob();

        assertTrue(
                "Job with flexible constraint did not fire when transport affinity not applicable",
                kTestEnvironment.awaitExecution(FLEXIBILITY_TIMEOUT_MILLIS));
    }

    /**
     * Schedule jobs with different priorities, don't satisfy any constraints, then verify only
     * the job with the shorter fallback deadline runs when no constraints are required.
     */
    public void testSeparateFallbackDeadlines() throws Exception {
        if (!deviceSupportsAllFlexConstraints(
                CONSTRAINT_BATTERY_NOT_LOW | CONSTRAINT_CHARGING | CONSTRAINT_IDLE)) {
            Log.d(TAG, "Skipping test since device doesn't support any constraints");
            return;
        }
        setDeviceConfigFlag("fc_percents_to_drop_flexible_constraints",
                "500=3|5|7|50"
                        + ",400=3|5|7|50"
                        + ",300=3|5|7|50"
                        + ",200=3|5|7|50"
                        + ",100=3|5|7|50", true);
        setDeviceConfigFlag("fc_fallback_flexibility_deadlines",
                "500=100000,400=100000,300=360000000,200=360000000,100=360000000", true);
        final int jobIdHigh = FLEXIBLE_JOB_ID;
        final int jobIdDefault = FLEXIBLE_JOB_ID + 1;
        try (TestAppInterface testAppInterface =
                     new TestAppInterface(getContext(), FLEXIBLE_JOB_ID)) {
            testAppInterface.scheduleJob(Collections.emptyMap(),
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_JOB_ID_KEY, jobIdHigh,
                            TestJobSchedulerReceiver.EXTRA_PRIORITY, JobInfo.PRIORITY_HIGH
                    )
            );
            testAppInterface.scheduleJob(Collections.emptyMap(),
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_JOB_ID_KEY, jobIdDefault,
                            TestJobSchedulerReceiver.EXTRA_PRIORITY, JobInfo.PRIORITY_DEFAULT
                    )
            );
            testAppInterface.assertJobNotReady(jobIdHigh);
            testAppInterface.assertJobNotReady(jobIdDefault);

            // Wait for the first constraint to drop.
            assertFalse("Job fired before flexible constraints dropped",
                    testAppInterface.awaitJobStart(jobIdHigh, 3000));

            // Remaining time before all constraints should have dropped.
            Thread.sleep(5000);
            testAppInterface.runSatisfiedJob(jobIdHigh);
            testAppInterface.runSatisfiedJob(jobIdDefault);

            assertTrue(
                    "Job with flexible constraint did not fire when no constraints were required",
                    testAppInterface.awaitJobStart(jobIdHigh, FLEXIBILITY_TIMEOUT_MILLIS));
            assertFalse(
                    "Job with flexible constraint fired even though constraints were required",
                    testAppInterface.awaitJobStart(jobIdDefault, 2000));
        }
    }

    /**
     * Schedule jobs with different priorities, don't satisfy any constraints, then verify only
     * the job with the lower percents-to-drop runs when no constraints are required.
     */
    public void testSeparatePercentsToDrop() throws Exception {
        if (!deviceSupportsAllFlexConstraints(
                CONSTRAINT_BATTERY_NOT_LOW | CONSTRAINT_CHARGING | CONSTRAINT_IDLE)) {
            Log.d(TAG, "Skipping test since device doesn't support any constraints");
            return;
        }
        setDeviceConfigFlag("fc_percents_to_drop_flexible_constraints",
                "500=3|5|7|50"
                        + ",400=3|5|7|50"
                        + ",300=90|92|95|99"
                        + ",200=90|92|95|99"
                        + ",100=90|92|95|99",
                true);
        setDeviceConfigFlag("fc_fallback_flexibility_deadlines",
                "500=100000,400=100000,300=100000,200=100000,100=100000", true);
        final int jobIdHigh = FLEXIBLE_JOB_ID;
        final int jobIdDefault = FLEXIBLE_JOB_ID + 1;
        try (TestAppInterface testAppInterface =
                     new TestAppInterface(getContext(), FLEXIBLE_JOB_ID)) {
            testAppInterface.scheduleJob(Collections.emptyMap(),
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_JOB_ID_KEY, jobIdHigh,
                            TestJobSchedulerReceiver.EXTRA_PRIORITY, JobInfo.PRIORITY_HIGH
                    )
            );
            testAppInterface.scheduleJob(Collections.emptyMap(),
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_JOB_ID_KEY, jobIdDefault,
                            TestJobSchedulerReceiver.EXTRA_PRIORITY, JobInfo.PRIORITY_DEFAULT
                    )
            );
            testAppInterface.assertJobNotReady(jobIdHigh);
            testAppInterface.assertJobNotReady(jobIdDefault);

            // Wait for the first constraint to drop.
            assertFalse("Job fired before flexible constraints dropped",
                    testAppInterface.awaitJobStart(jobIdHigh, 3000));

            // Remaining time before all constraints should have dropped.
            Thread.sleep(5000);
            testAppInterface.runSatisfiedJob(jobIdHigh);
            testAppInterface.runSatisfiedJob(jobIdDefault);

            assertTrue(
                    "Job with flexible constraint did not fire when no constraints were required",
                    testAppInterface.awaitJobStart(jobIdHigh, FLEXIBILITY_TIMEOUT_MILLIS));
            assertFalse(
                    "Job with flexible constraint fired even though constraints were required",
                    testAppInterface.awaitJobStart(jobIdDefault, 2000));
        }
    }

    private boolean deviceSupportsAllFlexConstraints(int constraints) {
        if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_EMBEDDED)) {
            return (constraints & SUPPORTED_CONSTRAINTS_EMBEDDED) == constraints;
        }
        if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            return (constraints & SUPPORTED_CONSTRAINTS_AUTOMOTIVE) == constraints;
        }
        return (constraints & SUPPORTED_CONSTRAINTS_DEFAULT) == constraints;
    }

    private boolean deviceSupportsAnyFlexConstraints(int constraints) {
        if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_EMBEDDED)) {
            return (constraints & SUPPORTED_CONSTRAINTS_EMBEDDED) != 0;
        }
        if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            return (constraints & SUPPORTED_CONSTRAINTS_AUTOMOTIVE) != 0;
        }
        return (constraints & SUPPORTED_CONSTRAINTS_DEFAULT) != 0;
    }

    private boolean deviceSupportsFlexTransportAffinities() {
        // Transport affinities aren't enabled on watches by default.
        return !getContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_WATCH);
    }

    private void satisfyAllSystemWideConstraints() throws Exception {
        satisfySystemWideConstraints(true, true, true);
    }

    private void satisfySystemWideConstraints(
            boolean charging, boolean batteryNotLow, boolean idle) throws Exception {
        if (BatteryUtils.hasBattery()) {
            setBatteryState(charging, batteryNotLow ? 100 : 5);
        }
        toggleIdle(idle);
    }

    private void toggleIdle(boolean state) throws Exception {
        toggleScreenOn(!state);
        IdleConstraintTest.triggerIdleMaintenance(mUiDevice);
    }

    private void scheduleJobToExecute() {
        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(mBuilder.build());
    }

    void assertJobNotReady() throws Exception {
        assertJobNotReady(FLEXIBLE_JOB_ID);
    }

    private void runJob() throws Exception {
        runJob(FLEXIBLE_JOB_ID);
    }

    private void runJob(int jobId) throws Exception {
        mUiDevice.executeShellCommand("cmd jobscheduler run -s"
                + " -u " + UserHandle.myUserId()
                + " " + kJobServiceComponent.getPackageName()
                + " " + jobId);
    }
}
