/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package android.jobscheduler.cts;

import static android.app.job.JobInfo.NETWORK_TYPE_ANY;
import static android.app.job.JobInfo.NETWORK_TYPE_NONE;
import static android.jobscheduler.cts.TestAppInterface.TEST_APP_PACKAGE;

import static com.android.compatibility.common.util.TestUtils.waitUntil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.AppOpsManager;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.content.Context;
import android.content.pm.PackageManager;
import android.jobscheduler.cts.jobtestapp.TestJobSchedulerReceiver;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.Temperature;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresDevice;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.AppOpsUtils;
import com.android.compatibility.common.util.AppStandbyUtils;
import com.android.compatibility.common.util.BatteryUtils;
import com.android.compatibility.common.util.DeviceConfigStateHelper;
import com.android.compatibility.common.util.ThermalUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

/**
 * Tests related to job throttling -- device idle, app standby and battery saver.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class JobThrottlingTest {
    private static final String TAG = JobThrottlingTest.class.getSimpleName();
    private static final long BACKGROUND_JOBS_EXPECTED_DELAY = 3_000;
    private static final long POLL_INTERVAL = 500;
    private static final long DEFAULT_WAIT_TIMEOUT = 5000;
    private static final long SHELL_TIMEOUT = 3_000;
    // TODO: mark Settings.System.SCREEN_OFF_TIMEOUT as @TestApi
    private static final String SCREEN_OFF_TIMEOUT = "screen_off_timeout";

    enum Bucket {
        ACTIVE,
        WORKING_SET,
        FREQUENT,
        RARE,
        RESTRICTED,
        NEVER
    }

    private Context mContext;
    private UiDevice mUiDevice;
    private NetworkingHelper mNetworkingHelper;
    private PowerManager mPowerManager;
    private int mTestJobId;
    private boolean mDeviceIdleEnabled;
    private boolean mDeviceLightIdleEnabled;
    private boolean mAppStandbyEnabled;
    private String mInitialActivityManagerConstants;
    private String mInitialDisplayTimeout;
    private String mInitialBatteryStatsConstants;
    private boolean mAutomotiveDevice;
    private boolean mLeanbackOnly;

    private TestAppInterface mTestAppInterface;
    private DeviceConfigStateHelper mDeviceConfigStateHelper;
    private DeviceConfigStateHelper mActivityManagerDeviceConfigStateHelper;
    private DeviceConfigStateHelper mTareDeviceConfigStateHelper;

    private static boolean isDeviceIdleEnabled(UiDevice uiDevice) throws Exception {
        final String output = uiDevice.executeShellCommand("cmd deviceidle enabled deep").trim();
        return Integer.parseInt(output) != 0;
    }

    private static boolean isDeviceLightIdleEnabled(UiDevice uiDevice) throws Exception {
        final String output = uiDevice.executeShellCommand("cmd deviceidle enabled light").trim();
        return Integer.parseInt(output) != 0;
    }

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mNetworkingHelper =
                new NetworkingHelper(InstrumentationRegistry.getInstrumentation(), mContext);
        mPowerManager = mContext.getSystemService(PowerManager.class);
        mTestJobId = (int) (SystemClock.uptimeMillis() / 1000);
        mTestAppInterface = new TestAppInterface(mContext, mTestJobId);

        makeTestPackageIdle();
        mDeviceIdleEnabled = isDeviceIdleEnabled(mUiDevice);
        mDeviceLightIdleEnabled = isDeviceLightIdleEnabled(mUiDevice);
        if (mDeviceIdleEnabled || mDeviceLightIdleEnabled) {
            // Make sure the device isn't dozing since it will affect execution of regular jobs
            toggleDozeState(false);
        }
        mAppStandbyEnabled = AppStandbyUtils.isAppStandbyEnabled();
        if (mAppStandbyEnabled) {
            setTestPackageStandbyBucket(Bucket.ACTIVE);
        } else {
            Log.w(TAG, "App standby not enabled on test device");
        }
        mInitialBatteryStatsConstants = Settings.Global.getString(mContext.getContentResolver(),
                Settings.Global.BATTERY_STATS_CONSTANTS);
        // Make sure ACTION_CHARGING is sent immediately.
        Settings.Global.putString(mContext.getContentResolver(),
                Settings.Global.BATTERY_STATS_CONSTANTS, "battery_charged_delay_ms=0");
        // Make sure test jobs can run regardless of bucket.
        mDeviceConfigStateHelper =
                new DeviceConfigStateHelper(DeviceConfig.NAMESPACE_JOB_SCHEDULER);
        mDeviceConfigStateHelper.set(
                new DeviceConfig.Properties.Builder(DeviceConfig.NAMESPACE_JOB_SCHEDULER)
                        .setInt("min_ready_non_active_jobs_count", 0)
                        // Disable batching behavior.
                        .setInt("min_ready_cpu_only_jobs_count", 0)
                        .setInt("min_ready_non_active_jobs_count", 0)
                        .setString("conn_transport_batch_threshold", "")
                        // Disable flex behavior.
                        .setInt("fc_applied_constraints", 0).build());
        mActivityManagerDeviceConfigStateHelper =
                new DeviceConfigStateHelper(DeviceConfig.NAMESPACE_ACTIVITY_MANAGER);
        mTareDeviceConfigStateHelper = new DeviceConfigStateHelper(DeviceConfig.NAMESPACE_TARE);
        toggleAutoRestrictedBucketOnBgRestricted(false);
        // Make sure the screen doesn't turn off when the test turns it on.
        mInitialDisplayTimeout =
                Settings.System.getString(mContext.getContentResolver(), SCREEN_OFF_TIMEOUT);
        Settings.System.putString(mContext.getContentResolver(), SCREEN_OFF_TIMEOUT, "300000");

        mInitialActivityManagerConstants = Settings.Global.getString(mContext.getContentResolver(),
                Settings.Global.ACTIVITY_MANAGER_CONSTANTS);
        // Delete any activity manager constants overrides so that the default transition time
        // of 60 seconds for UID active to UID idle is used.
        Settings.Global.putString(mContext.getContentResolver(),
                Settings.Global.ACTIVITY_MANAGER_CONSTANTS, null);

        // In automotive device, always-on screen and endless battery charging are assumed.
        mAutomotiveDevice =
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
        // In leanback devices, it is assumed that there is no battery.
        mLeanbackOnly =
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY);
        if (mAutomotiveDevice || mLeanbackOnly) {
            setScreenState(true);
            // TODO(b/159176758): make sure that initial power supply is on.
            setChargingState(true);
        }

        // Kill as many things in the background as possible so we avoid LMK interfering with the
        // test.
        mUiDevice.executeShellCommand("am kill-all");
    }

    @Test
    public void testAllowWhileIdleJobInTempwhitelist() throws Exception {
        assumeTrue("device idle not enabled", mDeviceIdleEnabled);

        toggleDozeState(true);
        Thread.sleep(DEFAULT_WAIT_TIMEOUT);
        sendScheduleJobBroadcast(true);
        assertFalse("Job started without being tempwhitelisted",
                mTestAppInterface.awaitJobStart(5_000));
        tempWhitelistTestApp(5_000);
        assertTrue("Job with allow_while_idle flag did not start when the app was tempwhitelisted",
                mTestAppInterface.awaitJobStart(5_000));
    }

    @Test
    public void testForegroundJobsStartImmediately() throws Exception {
        assumeTrue("device idle not enabled", mDeviceIdleEnabled);

        sendScheduleJobBroadcast(false);
        runJob();
        assertTrue("Job did not start after scheduling",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
        toggleDozeState(true);
        assertTrue("Job did not stop on entering doze",
                mTestAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT));
        Thread.sleep(TestJobSchedulerReceiver.JOB_INITIAL_BACKOFF);
        // The adb command will force idle even with the screen on, so we need to turn Doze off
        // explicitly.
        toggleDozeState(false);
        // Turn the screen on to ensure the test app ends up in TOP.
        setScreenState(true);
        mTestAppInterface.startAndKeepTestActivity();
        assertTrue("Job for foreground app did not start immediately when device exited doze",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
    }

    @Test
    public void testBackgroundJobsDelayed() throws Exception {
        assumeTrue("device idle not enabled", mDeviceIdleEnabled);

        sendScheduleJobBroadcast(false);
        runJob();
        assertTrue("Job did not start after scheduling",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
        toggleDozeState(true);
        assertTrue("Job did not stop on entering doze",
                mTestAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT));
        Thread.sleep(TestJobSchedulerReceiver.JOB_INITIAL_BACKOFF);
        toggleDozeState(false);
        assertFalse("Job for background app started immediately when device exited doze",
                mTestAppInterface.awaitJobStart(2000));
        Thread.sleep(BACKGROUND_JOBS_EXPECTED_DELAY - 2000);
        assertTrue("Job for background app did not start after the expected delay of "
                        + BACKGROUND_JOBS_EXPECTED_DELAY + "ms",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
    }

    @Test
    public void testJobStoppedWhenRestricted() throws Exception {
        sendScheduleJobBroadcast(false);
        runJob();
        assertTrue("Job did not start after scheduling",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
        toggleAutoRestrictedBucketOnBgRestricted(true);
        setTestPackageRestricted(true);
        assertFalse("Job stopped after test app was restricted with auto-restricted-bucket on",
                mTestAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT));
        toggleAutoRestrictedBucketOnBgRestricted(false);
        assertTrue("Job did not stop after test app was restricted",
                mTestAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT));
        assertEquals(JobParameters.STOP_REASON_BACKGROUND_RESTRICTION,
                mTestAppInterface.getLastParams().getStopReason());
    }

    @Test
    public void testRestrictedJobStartedWhenUnrestricted() throws Exception {
        setTestPackageRestricted(true);
        sendScheduleJobBroadcast(false);
        assertFalse("Job started for restricted app",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
        setTestPackageRestricted(false);
        assertTrue("Job did not start when app was unrestricted",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
    }

    @Test
    public void testRestrictedJobAllowedWhenUidActive() throws Exception {
        setTestPackageRestricted(true);
        sendScheduleJobBroadcast(false);
        assertFalse("Job started for restricted app",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
        // Turn the screen on to ensure the app gets into the TOP state.
        setScreenState(true);
        mTestAppInterface.startAndKeepTestActivity(true);
        assertTrue("Job did not start when app had an activity",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));

        mTestAppInterface.closeActivity();
        // Don't put full minute as the timeout to give some leeway with test timing/processing.
        assertFalse("Job stopped within grace period after activity closed",
                mTestAppInterface.awaitJobStop(55_000L));
        assertTrue("Job did not stop after grace period ended",
                mTestAppInterface.awaitJobStop(15_000L));
        assertEquals(JobParameters.STOP_REASON_BACKGROUND_RESTRICTION,
                mTestAppInterface.getLastParams().getStopReason());
    }

    @Test
    public void testRestrictedJobAllowedInPowerAllowlist() throws Exception {
        setTestPackageRestricted(true);
        sendScheduleJobBroadcast(false);
        assertFalse("Job started for restricted app",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));

        setPowerAllowlistState(true);
        assertTrue("Job did not start when app was in the power allowlist",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));

        setPowerAllowlistState(false);
        assertTrue("Job did not stop when the app was removed from the power allowlist",
                mTestAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT));
        assertEquals(JobParameters.STOP_REASON_BACKGROUND_RESTRICTION,
                mTestAppInterface.getLastParams().getStopReason());
    }

    @Test
    public void testRestrictedJobAllowedWhenAutoRestrictedBucketFeatureOn() throws Exception {
        setTestPackageRestricted(true);
        sendScheduleJobBroadcast(false);
        assertFalse("Job started for restricted app",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
        toggleAutoRestrictedBucketOnBgRestricted(true);
        assertTrue("Job did not start after scheduling",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
    }

    @Test
    public void testEJStoppedWhenRestricted() throws Exception {
        mTestAppInterface.scheduleJob(false, JobInfo.NETWORK_TYPE_NONE, true);
        runJob();
        assertTrue("Job did not start after scheduling",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
        toggleAutoRestrictedBucketOnBgRestricted(true);
        setTestPackageRestricted(true);
        assertFalse("Job stopped after test app was restricted with auto-restricted-bucket on",
                mTestAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT));
        toggleAutoRestrictedBucketOnBgRestricted(false);
        assertTrue("Job did not stop after test app was restricted",
                mTestAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT));
        assertEquals(JobParameters.STOP_REASON_BACKGROUND_RESTRICTION,
                mTestAppInterface.getLastParams().getStopReason());
    }

    @Test
    public void testRestrictedEJStartedWhenUnrestricted() throws Exception {
        setTestPackageRestricted(true);
        mTestAppInterface.scheduleJob(false, JobInfo.NETWORK_TYPE_NONE, true);
        assertFalse("Job started for restricted app",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
        setTestPackageRestricted(false);
        assertTrue("Job did not start when app was unrestricted",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
    }

    @Test
    public void testRestrictedEJAllowedWhenUidActive() throws Exception {
        setTestPackageRestricted(true);
        mTestAppInterface.scheduleJob(false, JobInfo.NETWORK_TYPE_NONE, true);
        assertFalse("Job started for restricted app",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
        // Turn the screen on to ensure the app gets into the TOP state.
        setScreenState(true);
        mTestAppInterface.startAndKeepTestActivity(true);
        assertTrue("Job did not start when app had an activity",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));

        mTestAppInterface.closeActivity();
        // Don't put full minute as the timeout to give some leeway with test timing/processing.
        assertFalse("Job stopped within grace period after activity closed",
                mTestAppInterface.awaitJobStop(55_000L));
        assertTrue("Job did not stop after grace period ended",
                mTestAppInterface.awaitJobStop(15_000L));
        assertEquals(JobParameters.STOP_REASON_BACKGROUND_RESTRICTION,
                mTestAppInterface.getLastParams().getStopReason());
    }

    @Test
    public void testRestrictedEJAllowedWhenAutoRestrictedBucketFeatureOn() throws Exception {
        setTestPackageRestricted(true);
        mTestAppInterface.scheduleJob(false, JobInfo.NETWORK_TYPE_NONE, true);
        assertFalse("Job started for restricted app",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));

        toggleAutoRestrictedBucketOnBgRestricted(true);
        assertTrue("Job did not start when app was background unrestricted",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
    }

    @Test
    public void testBackgroundRegJobsThermal() throws Exception {
        mTestAppInterface.scheduleJob(false, NETWORK_TYPE_NONE, false);
        runJob();
        assertTrue("Job did not start after scheduling",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));

        ThermalUtils.overrideThermalStatus(Temperature.THROTTLING_LIGHT);
        assertFalse("Job stopped below thermal throttling threshold",
                mTestAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT));

        ThermalUtils.overrideThermalStatus(Temperature.THROTTLING_SEVERE);
        assertTrue("Job did not stop on thermal throttling",
                mTestAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT));
        final long jobStopTime = System.currentTimeMillis();

        ThermalUtils.overrideThermalStatus(Temperature.THROTTLING_CRITICAL);
        runJob();
        assertFalse("Job started above thermal throttling threshold",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));

        ThermalUtils.overrideThermalStatus(Temperature.THROTTLING_EMERGENCY);
        runJob();
        assertFalse("Job started above thermal throttling threshold",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));

        Thread.sleep(Math.max(0, TestJobSchedulerReceiver.JOB_INITIAL_BACKOFF
                - (System.currentTimeMillis() - jobStopTime)));
        ThermalUtils.overrideThermalNotThrottling();
        runJob();
        assertTrue("Job did not start back from throttling",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
    }

    @Test
    public void testBackgroundEJsThermal() throws Exception {
        mTestAppInterface.scheduleJob(false, NETWORK_TYPE_NONE, true);
        runJob();
        assertTrue("Job did not start after scheduling",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));

        ThermalUtils.overrideThermalStatus(Temperature.THROTTLING_MODERATE);
        assertFalse("Job stopped below thermal throttling threshold",
                mTestAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT));

        ThermalUtils.overrideThermalStatus(Temperature.THROTTLING_SEVERE);
        assertTrue("Job did not stop on thermal throttling",
                mTestAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT));
        final long jobStopTime = System.currentTimeMillis();

        ThermalUtils.overrideThermalStatus(Temperature.THROTTLING_CRITICAL);
        runJob();
        assertFalse("Job started above thermal throttling threshold",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));

        ThermalUtils.overrideThermalStatus(Temperature.THROTTLING_EMERGENCY);
        runJob();
        assertFalse("Job started above thermal throttling threshold",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));

        Thread.sleep(Math.max(0, TestJobSchedulerReceiver.JOB_INITIAL_BACKOFF
                - (System.currentTimeMillis() - jobStopTime)));
        ThermalUtils.overrideThermalNotThrottling();
        runJob();
        assertTrue("Job did not start back from throttling",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
    }

    @Test
    public void testBackgroundUIJsThermal() throws Exception {
        try (TestNotificationListener.NotificationHelper notificationHelper =
                     new TestNotificationListener.NotificationHelper(
                             mContext, TestAppInterface.TEST_APP_PACKAGE)) {
            mNetworkingHelper.setAllNetworksEnabled(true);
            mTestAppInterface.postUiInitiatingNotification(
                    Map.of(TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true),
                    Map.of(TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE, NETWORK_TYPE_ANY));
            notificationHelper.clickNotification();

            assertTrue("Job did not start after scheduling",
                    mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));

            ThermalUtils.overrideThermalStatus(Temperature.THROTTLING_MODERATE);
            assertFalse("Job stopped below thermal throttling threshold",
                    mTestAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT));

            ThermalUtils.overrideThermalStatus(Temperature.THROTTLING_SEVERE);
            assertTrue("Job did not stop on thermal throttling",
                    mTestAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT));
            final long jobStopTime = System.currentTimeMillis();

            ThermalUtils.overrideThermalStatus(Temperature.THROTTLING_CRITICAL);
            runJob();
            assertFalse("Job started above thermal throttling threshold",
                    mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));

            ThermalUtils.overrideThermalStatus(Temperature.THROTTLING_EMERGENCY);
            runJob();
            assertFalse("Job started above thermal throttling threshold",
                    mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));

            Thread.sleep(Math.max(0, TestJobSchedulerReceiver.JOB_INITIAL_BACKOFF
                    - (System.currentTimeMillis() - jobStopTime)));
            ThermalUtils.overrideThermalNotThrottling();
            runJob();
            assertTrue("Job did not start back from throttling",
                    mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
        }
    }

    @Test
    public void testForegroundJobsThermal() throws Exception {
        // Turn the screen on to ensure the app gets into the TOP state.
        setScreenState(true);
        mTestAppInterface.startAndKeepTestActivity(true);
        mTestAppInterface.scheduleJob(false, NETWORK_TYPE_NONE, false);
        runJob();
        assertTrue("Job did not start after scheduling",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));

        ThermalUtils.overrideThermalStatus(Temperature.THROTTLING_MODERATE);
        assertFalse("Job stopped below thermal throttling threshold",
                mTestAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT));

        ThermalUtils.overrideThermalStatus(Temperature.THROTTLING_SEVERE);
        assertFalse("Job stopped despite being TOP app",
                mTestAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT));

        ThermalUtils.overrideThermalStatus(Temperature.THROTTLING_CRITICAL);
        assertFalse("Job stopped despite being TOP app",
                mTestAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT));
    }

    /** Tests that apps in the RESTRICTED bucket still get their one parole session per day. */
    @Test
    public void testJobsInRestrictedBucket_ParoleSession() throws Exception {
        assumeTrue("app standby not enabled", mAppStandbyEnabled);
        assumeTrue("device doesn't have battery", BatteryUtils.hasBattery());

        // This test is designed for the old quota system.
        mTareDeviceConfigStateHelper.set("enable_tare_mode", "0");

        // Disable coalescing
        mDeviceConfigStateHelper.set("qc_timing_session_coalescing_duration_ms", "0");

        setScreenState(true);

        setChargingState(false);
        setTestPackageStandbyBucket(Bucket.RESTRICTED);
        Thread.sleep(DEFAULT_WAIT_TIMEOUT);
        sendScheduleJobBroadcast(false);
        runJob();
        assertTrue("Parole job didn't start in RESTRICTED bucket",
                mTestAppInterface.awaitJobStart(3_000));

        sendScheduleJobBroadcast(false);
        assertFalse("New job started in RESTRICTED bucket", mTestAppInterface.awaitJobStart(3_000));
    }

    /**
     * Tests that apps in the RESTRICTED bucket have their parole sessions properly counted even
     * when charging (but not idle).
     */
    @Test
    public void testJobsInRestrictedBucket_CorrectParoleWhileCharging() throws Exception {
        assumeTrue("app standby not enabled", mAppStandbyEnabled);
        assumeFalse("not testable in automotive device", mAutomotiveDevice);
        assumeFalse("not testable in leanback device", mLeanbackOnly);

        // This test is designed for the old quota system.
        mTareDeviceConfigStateHelper.set("enable_tare_mode", "0");

        // Disable coalescing
        mDeviceConfigStateHelper.set("qc_timing_session_coalescing_duration_ms", "0");
        mDeviceConfigStateHelper.set("qc_max_session_count_restricted", "1");

        setScreenState(true);
        setChargingState(true);
        BatteryUtils.runDumpsysBatterySetLevel(100);

        setTestPackageStandbyBucket(Bucket.RESTRICTED);
        Thread.sleep(DEFAULT_WAIT_TIMEOUT);
        sendScheduleJobBroadcast(false);
        runJob();
        assertTrue("Parole job didn't start in RESTRICTED bucket",
                mTestAppInterface.awaitJobStart(3_000));

        sendScheduleJobBroadcast(false);
        assertFalse("New job started in RESTRICTED bucket after parole used",
                mTestAppInterface.awaitJobStart(3_000));
    }

    /**
     * Tests that apps in the RESTRICTED bucket that have used their one parole session per day
     * don't get to run again until the device is charging + idle.
     */
    @Test
    public void testJobsInRestrictedBucket_DeferredUntilFreeResources() throws Exception {
        assumeTrue("app standby not enabled", mAppStandbyEnabled);
        assumeTrue("device doesn't have battery", BatteryUtils.hasBattery());

        // This test is designed for the old quota system.
        mTareDeviceConfigStateHelper.set("enable_tare_mode", "0");

        // Disable coalescing
        mDeviceConfigStateHelper.set("qc_timing_session_coalescing_duration_ms", "0");

        setScreenState(true);

        setChargingState(false);
        setTestPackageStandbyBucket(Bucket.RESTRICTED);
        Thread.sleep(DEFAULT_WAIT_TIMEOUT);
        sendScheduleJobBroadcast(false);
        runJob();
        assertTrue("Parole job didn't start in RESTRICTED bucket",
                mTestAppInterface.awaitJobStart(3_000));

        sendScheduleJobBroadcast(false);
        assertFalse("New job started in RESTRICTED bucket after parole used",
                mTestAppInterface.awaitJobStart(3_000));

        setChargingState(true);
        BatteryUtils.runDumpsysBatterySetLevel(100);
        assertFalse("New job started in RESTRICTED bucket after parole when charging but not idle",
                mTestAppInterface.awaitJobStart(3_000));

        setScreenState(false);
        triggerJobIdle();
        assertTrue("Job didn't start in RESTRICTED bucket when charging + idle",
                mTestAppInterface.awaitJobStart(3_000));

        // Make sure job can be stopped and started again when charging + idle
        sendScheduleJobBroadcast(false);
        runJob();
        assertTrue("Job didn't restart in RESTRICTED bucket when charging + idle",
                mTestAppInterface.awaitJobStart(3_000));
    }

    @Test
    public void testJobsInRestrictedBucket_NoRequiredNetwork() throws Exception {
        assumeTrue("app standby not enabled", mAppStandbyEnabled);
        assumeTrue("device doesn't have battery", BatteryUtils.hasBattery());
        assumeFalse("not testable, since ethernet is connected", hasEthernetConnection());

        // This test is designed for the old quota system.
        mTareDeviceConfigStateHelper.set("enable_tare_mode", "0");

        // Disable coalescing and the parole session
        mDeviceConfigStateHelper.set("qc_timing_session_coalescing_duration_ms", "0");
        mDeviceConfigStateHelper.set("qc_max_session_count_restricted", "0");

        mNetworkingHelper.setAllNetworksEnabled(false);
        setScreenState(true);

        setChargingState(false);
        setTestPackageStandbyBucket(Bucket.RESTRICTED);
        Thread.sleep(DEFAULT_WAIT_TIMEOUT);
        mTestAppInterface.scheduleJob(false, NETWORK_TYPE_NONE, false);
        assertFalse("New job started in RESTRICTED bucket", mTestAppInterface.awaitJobStart(3_000));

        // Slowly add back required bucket constraints.

        // Battery charging and high.
        setChargingState(true);
        assertFalse("New job started in RESTRICTED bucket", mTestAppInterface.awaitJobStart(3_000));
        BatteryUtils.runDumpsysBatterySetLevel(100);
        assertFalse("New job started in RESTRICTED bucket", mTestAppInterface.awaitJobStart(3_000));

        // Device is idle.
        setScreenState(false);
        assertFalse("New job started in RESTRICTED bucket", mTestAppInterface.awaitJobStart(3_000));
        triggerJobIdle();
        assertTrue("New job didn't start in RESTRICTED bucket",
                mTestAppInterface.awaitJobStart(3_000));
    }

    @RequiresDevice // Emulators don't always have access to wifi/network
    @Test
    public void testJobsInRestrictedBucket_WithRequiredNetwork() throws Exception {
        assumeTrue("app standby not enabled", mAppStandbyEnabled);
        assumeTrue("device doesn't have battery", BatteryUtils.hasBattery());
        assumeFalse("not testable, since ethernet is connected", hasEthernetConnection());
        assumeTrue(mNetworkingHelper.hasWifiFeature());

        // This test is designed for the old quota system.
        mTareDeviceConfigStateHelper.set("enable_tare_mode", "0");

        // Disable coalescing and the parole session
        mDeviceConfigStateHelper.set("qc_timing_session_coalescing_duration_ms", "0");
        mDeviceConfigStateHelper.set("qc_max_session_count_restricted", "0");

        mNetworkingHelper.setAllNetworksEnabled(false);
        setScreenState(true);

        setChargingState(false);
        setTestPackageStandbyBucket(Bucket.RESTRICTED);
        Thread.sleep(DEFAULT_WAIT_TIMEOUT);
        mTestAppInterface.scheduleJob(false, NETWORK_TYPE_ANY, false);
        runJob();
        assertFalse("New job started in RESTRICTED bucket", mTestAppInterface.awaitJobStart(3_000));

        // Slowly add back required bucket constraints.

        // Battery charging and high.
        setChargingState(true);
        runJob();
        assertFalse("New job started in RESTRICTED bucket", mTestAppInterface.awaitJobStart(3_000));
        BatteryUtils.runDumpsysBatterySetLevel(100);
        runJob();
        assertFalse("New job started in RESTRICTED bucket", mTestAppInterface.awaitJobStart(3_000));

        // Device is idle.
        setScreenState(false);
        runJob();
        assertFalse("New job started in RESTRICTED bucket", mTestAppInterface.awaitJobStart(3_000));
        triggerJobIdle();
        runJob();
        assertFalse("New job started in RESTRICTED bucket", mTestAppInterface.awaitJobStart(3_000));

        // Add network
        mNetworkingHelper.setAllNetworksEnabled(true);
        mNetworkingHelper.setWifiMeteredState(false);
        runJob();
        assertTrue("New job didn't start in RESTRICTED bucket",
                mTestAppInterface.awaitJobStart(5_000));
    }

    @Test
    public void testJobsInNeverApp() throws Exception {
        assumeTrue("app standby not enabled", mAppStandbyEnabled);
        assumeTrue("device doesn't have battery", BatteryUtils.hasBattery());

        // This test is designed for the old quota system.
        mTareDeviceConfigStateHelper.set("enable_tare_mode", "0");

        setChargingState(false);
        setTestPackageStandbyBucket(Bucket.NEVER);
        Thread.sleep(DEFAULT_WAIT_TIMEOUT);
        sendScheduleJobBroadcast(false);
        assertFalse("New job started in NEVER bucket", mTestAppInterface.awaitJobStart(3_000));
    }

    @Test
    public void testUidActiveBypassesStandby() throws Exception {
        assumeTrue("app standby not enabled", mAppStandbyEnabled);
        assumeTrue("device doesn't have battery", BatteryUtils.hasBattery());

        // This test is designed for the old quota system.
        mTareDeviceConfigStateHelper.set("enable_tare_mode", "0");

        setChargingState(false);
        setTestPackageStandbyBucket(Bucket.NEVER);
        tempWhitelistTestApp(6_000);
        Thread.sleep(DEFAULT_WAIT_TIMEOUT);
        sendScheduleJobBroadcast(false);
        assertTrue("New job in uid-active app failed to start in NEVER standby",
                mTestAppInterface.awaitJobStart(4_000));
    }

    @Test
    public void testBatterySaverOff() throws Exception {
        BatteryUtils.assumeBatterySaverFeature();

        setChargingState(false);
        BatteryUtils.enableBatterySaver(false);
        sendScheduleJobBroadcast(false);
        assertTrue("New job failed to start with battery saver OFF",
                mTestAppInterface.awaitJobStart(3_000));
    }

    @Test
    public void testBatterySaverOn() throws Exception {
        BatteryUtils.assumeBatterySaverFeature();

        setChargingState(false);
        BatteryUtils.enableBatterySaver(true);
        sendScheduleJobBroadcast(false);
        assertFalse("New job started with battery saver ON",
                mTestAppInterface.awaitJobStart(3_000));
    }

    @Test
    public void testUidActiveBypassesBatterySaverOn() throws Exception {
        BatteryUtils.assumeBatterySaverFeature();

        setChargingState(false);
        BatteryUtils.enableBatterySaver(true);
        tempWhitelistTestApp(6_000);
        sendScheduleJobBroadcast(false);
        assertTrue("New job in uid-active app failed to start with battery saver ON",
                mTestAppInterface.awaitJobStart(3_000));
    }

    @Test
    public void testBatterySaverOnThenUidActive() throws Exception {
        BatteryUtils.assumeBatterySaverFeature();

        // Enable battery saver, and schedule a job. It shouldn't run.
        setChargingState(false);
        BatteryUtils.enableBatterySaver(true);
        sendScheduleJobBroadcast(false);
        assertFalse("New job started with battery saver ON",
                mTestAppInterface.awaitJobStart(3_000));

        // Then make the UID active. Now the job should run.
        tempWhitelistTestApp(120_000);
        assertTrue("New job in uid-active app failed to start with battery saver OFF",
                mTestAppInterface.awaitJobStart(120_000));
    }

    @Test
    public void testExpeditedJobBypassesBatterySaverOn() throws Exception {
        BatteryUtils.assumeBatterySaverFeature();

        setChargingState(false);
        BatteryUtils.enableBatterySaver(true);
        mTestAppInterface.scheduleJob(false, JobInfo.NETWORK_TYPE_NONE, true);
        assertTrue("New expedited job failed to start with battery saver ON",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
    }

    @Test
    public void testExpeditedJobBypassesBatterySaver_toggling() throws Exception {
        BatteryUtils.assumeBatterySaverFeature();

        setChargingState(false);
        BatteryUtils.enableBatterySaver(false);
        mTestAppInterface.scheduleJob(false, JobInfo.NETWORK_TYPE_NONE, true);
        assertTrue("New expedited job failed to start with battery saver ON",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
        BatteryUtils.enableBatterySaver(true);
        assertFalse("Job stopped when battery saver turned on",
                mTestAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT));
    }

    @Test
    public void testExpeditedJobBypassesDeviceIdle() throws Exception {
        assumeTrue("device idle not enabled", mDeviceIdleEnabled);

        toggleDozeState(true);
        mTestAppInterface.scheduleJob(false, JobInfo.NETWORK_TYPE_NONE, true);
        runJob();
        assertTrue("Job did not start after scheduling",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
    }

    @Test
    public void testExpeditedJobBypassesDeviceIdle_toggling() throws Exception {
        assumeTrue("device idle not enabled", mDeviceIdleEnabled);

        toggleDozeState(false);
        mTestAppInterface.scheduleJob(false, JobInfo.NETWORK_TYPE_NONE, true);
        runJob();
        assertTrue("Job did not start after scheduling",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
        toggleDozeState(true);
        assertFalse("Job stopped when device enabled turned on",
                mTestAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT));
    }

    @Test
    public void testExpeditedJobDeferredAfterTimeoutInDoze() throws Exception {
        assumeTrue("device idle not enabled", mDeviceIdleEnabled);
        // Intentionally set a value below 1 minute to ensure the range checks work.
        mDeviceConfigStateHelper.set("runtime_min_ej_guarantee_ms", Long.toString(30_000L));

        toggleDozeState(true);
        mTestAppInterface.scheduleJob(false, JobInfo.NETWORK_TYPE_NONE, true);
        runJob();
        assertTrue("Job did not start after scheduling",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
        // Don't put full minute as the timeout to give some leeway with test timing/processing.
        assertFalse("Job stopped before min runtime limit",
                mTestAppInterface.awaitJobStop(55_000L));
        assertTrue("Job did not stop after timeout", mTestAppInterface.awaitJobStop(15_000L));
        assertEquals(JobParameters.STOP_REASON_DEVICE_STATE,
                mTestAppInterface.getLastParams().getStopReason());
        // Should be rescheduled.
        assertJobNotReady();
        assertJobWaiting();
        Thread.sleep(TestJobSchedulerReceiver.JOB_INITIAL_BACKOFF);
        runJob();
        assertFalse("Job started after timing out in Doze",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));

        // Should start when Doze is turned off.
        toggleDozeState(false);
        assertTrue("Job did not start after Doze turned off",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
    }

    @Test
    public void testExpeditedJobDeferredAfterTimeoutInBatterySaver() throws Exception {
        BatteryUtils.assumeBatterySaverFeature();

        // Intentionally set a value below 1 minute to ensure the range checks work.
        mDeviceConfigStateHelper.set("runtime_min_ej_guarantee_ms", Long.toString(47_000L));

        setChargingState(false);
        BatteryUtils.enableBatterySaver(true);
        mTestAppInterface.scheduleJob(false, JobInfo.NETWORK_TYPE_NONE, true);
        runJob();
        assertTrue("Job did not start after scheduling",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
        // Don't put full minute as the timeout to give some leeway with test timing/processing.
        assertFalse("Job stopped before min runtime limit",
                mTestAppInterface.awaitJobStop(55_000L));
        assertTrue("Job did not stop after timeout", mTestAppInterface.awaitJobStop(15_000L));
        assertEquals(JobParameters.STOP_REASON_DEVICE_STATE,
                mTestAppInterface.getLastParams().getStopReason());
        // Should be rescheduled.
        assertJobNotReady();
        assertJobWaiting();
        Thread.sleep(TestJobSchedulerReceiver.JOB_INITIAL_BACKOFF);
        runJob();
        assertFalse("Job started after timing out in battery saver",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));

        // Should start when battery saver is turned off.
        BatteryUtils.enableBatterySaver(false);
        assertTrue("Job did not start after battery saver turned off",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
    }

    @Test
    public void testExpeditedJobDeferredAfterTimeout_DozeAndBatterySaver() throws Exception {
        BatteryUtils.assumeBatterySaverFeature();
        assumeTrue("device idle not enabled", mDeviceIdleEnabled);
        mDeviceConfigStateHelper.set("runtime_min_ej_guarantee_ms", Long.toString(60_000L));

        setChargingState(false);
        toggleDozeState(true);
        mTestAppInterface.scheduleJob(false, JobInfo.NETWORK_TYPE_NONE, true);
        runJob();
        assertTrue("Job did not start after scheduling",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
        // Don't put full minute as the timeout to give some leeway with test timing/processing.
        assertFalse("Job stopped before min runtime limit",
                mTestAppInterface.awaitJobStop(55_000L));
        assertTrue("Job did not stop after timeout", mTestAppInterface.awaitJobStop(15_000L));
        assertEquals(JobParameters.STOP_REASON_DEVICE_STATE,
                mTestAppInterface.getLastParams().getStopReason());
        // Should be rescheduled.
        assertJobNotReady();
        assertJobWaiting();
        // Battery saver kicks in before Doze ends. Job shouldn't start while BS is on.
        BatteryUtils.enableBatterySaver(true);
        toggleDozeState(false);
        Thread.sleep(TestJobSchedulerReceiver.JOB_INITIAL_BACKOFF);
        runJob();
        assertFalse("Job started while power restrictions active after timing out",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));

        // Should start when battery saver is turned off.
        BatteryUtils.enableBatterySaver(false);
        assertTrue("Job did not start after power restrictions turned off",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
    }

    @Test
    public void testLongExpeditedJobStoppedByDoze() throws Exception {
        assumeTrue("device idle not enabled", mDeviceIdleEnabled);
        // Intentionally set a value below 1 minute to ensure the range checks work.
        mDeviceConfigStateHelper.set("runtime_min_ej_guarantee_ms", Long.toString(59_000L));

        toggleDozeState(false);
        mTestAppInterface.scheduleJob(false, JobInfo.NETWORK_TYPE_NONE, true);
        runJob();
        assertTrue("Job did not start after scheduling",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
        // Should get to run past min runtime.
        assertFalse("Job stopped after min runtime", mTestAppInterface.awaitJobStop(90_000L));

        // Should stop when Doze is turned on.
        toggleDozeState(true);
        assertTrue("Job did not stop after Doze turned on",
                mTestAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT));
        assertEquals(JobParameters.STOP_REASON_DEVICE_STATE,
                mTestAppInterface.getLastParams().getStopReason());
    }

    @Test
    public void testLongExpeditedJobStoppedByBatterySaver() throws Exception {
        BatteryUtils.assumeBatterySaverFeature();

        // Intentionally set a value below 1 minute to ensure the range checks work.
        mDeviceConfigStateHelper.set("runtime_min_ej_guarantee_ms", Long.toString(0L));

        setChargingState(false);
        BatteryUtils.enableBatterySaver(false);
        mTestAppInterface.scheduleJob(false, JobInfo.NETWORK_TYPE_NONE, true);
        runJob();
        assertTrue("Job did not start after scheduling",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
        // Should get to run past min runtime.
        assertFalse("Job stopped after runtime", mTestAppInterface.awaitJobStop(90_000L));

        // Should stop when battery saver is turned on.
        BatteryUtils.enableBatterySaver(true);
        assertTrue("Job did not stop after battery saver turned on",
                mTestAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT));
        assertEquals(JobParameters.STOP_REASON_DEVICE_STATE,
                mTestAppInterface.getLastParams().getStopReason());
    }

    @Test
    public void testUserInitiatedJobBypassesBatterySaverOn() throws Exception {
        BatteryUtils.assumeBatterySaverFeature();
        mNetworkingHelper.setAllNetworksEnabled(true);

        try (TestNotificationListener.NotificationHelper notificationHelper =
                     new TestNotificationListener.NotificationHelper(
                             mContext, TestAppInterface.TEST_APP_PACKAGE)) {
            setChargingState(false);
            BatteryUtils.enableBatterySaver(true);

            mTestAppInterface.postUiInitiatingNotification(
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true
                    ),
                    Map.of(TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE, NETWORK_TYPE_ANY));
            notificationHelper.clickNotification();

            assertTrue("New user-initiated job failed to start with battery saver ON",
                    mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
        }
    }

    @Test
    public void testUserInitiatedJobBypassesBatterySaver_toggling() throws Exception {
        BatteryUtils.assumeBatterySaverFeature();
        mNetworkingHelper.setAllNetworksEnabled(true);

        try (TestNotificationListener.NotificationHelper notificationHelper =
                     new TestNotificationListener.NotificationHelper(
                             mContext, TestAppInterface.TEST_APP_PACKAGE)) {
            setChargingState(false);
            BatteryUtils.enableBatterySaver(false);

            mTestAppInterface.postUiInitiatingNotification(
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true
                    ),
                    Map.of(TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE, NETWORK_TYPE_ANY));
            notificationHelper.clickNotification();

            assertTrue("New user-initiated job failed to start with battery saver ON",
                    mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));

            BatteryUtils.enableBatterySaver(true);
            assertFalse("Job stopped when battery saver turned on",
                    mTestAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT));
        }
    }

    @Test
    public void testUserInitiatedJobBypassesDeviceIdle() throws Exception {
        assumeTrue("device idle not enabled", mDeviceIdleEnabled);
        mNetworkingHelper.setAllNetworksEnabled(true);

        try (TestNotificationListener.NotificationHelper notificationHelper =
                     new TestNotificationListener.NotificationHelper(
                             mContext, TestAppInterface.TEST_APP_PACKAGE)) {
            toggleDozeState(true);

            mTestAppInterface.postUiInitiatingNotification(
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true
                    ),
                    Map.of(TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE, NETWORK_TYPE_ANY));
            notificationHelper.clickNotification();

            assertTrue("Job did not start after scheduling",
                    mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
        }
    }

    @Test
    public void testUserInitiatedJobBypassesDeviceIdle_toggling() throws Exception {
        assumeTrue("device idle not enabled", mDeviceIdleEnabled);
        mNetworkingHelper.setAllNetworksEnabled(true);

        try (TestNotificationListener.NotificationHelper notificationHelper =
                     new TestNotificationListener.NotificationHelper(
                             mContext, TestAppInterface.TEST_APP_PACKAGE)) {
            toggleDozeState(false);

            mTestAppInterface.postUiInitiatingNotification(
                    Map.of(
                            TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, true
                    ),
                    Map.of(TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE, NETWORK_TYPE_ANY));
            notificationHelper.clickNotification();

            assertTrue("Job did not start after scheduling",
                    mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));

            toggleDozeState(true);
            assertFalse("Job stopped when device enabled turned on",
                    mTestAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT));
        }
    }

    @Test
    public void testRestrictingStopReason_RestrictedBucket_connectivity() throws Exception {
        assumeTrue("app standby not enabled", mAppStandbyEnabled);
        // Tests cannot disable ethernet network.
        assumeFalse("not testable, since ethernet is connected", hasEthernetConnection());

        assumeTrue(BatteryUtils.hasBattery());
        assumeTrue(mNetworkingHelper.hasWifiFeature());

        // This test is designed for the old quota system.
        mTareDeviceConfigStateHelper.set("enable_tare_mode", "0");

        setTestPackageStandbyBucket(Bucket.RESTRICTED);

        // Disable coalescing and the parole session
        mDeviceConfigStateHelper.set("qc_timing_session_coalescing_duration_ms", "0");
        mDeviceConfigStateHelper.set("qc_max_session_count_restricted", "0");

        // Satisfy all additional constraints.
        mNetworkingHelper.setAllNetworksEnabled(true);
        mNetworkingHelper.setWifiMeteredState(false);
        setChargingState(true);
        BatteryUtils.runDumpsysBatterySetLevel(100);
        setScreenState(false);
        triggerJobIdle();

        // Connectivity
        mTestAppInterface.scheduleJob(false, NETWORK_TYPE_ANY, false);
        runJob();
        assertTrue("New job didn't start in RESTRICTED bucket",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
        mNetworkingHelper.setAllNetworksEnabled(false);
        assertTrue("New job didn't stop when connectivity dropped",
                mTestAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT));
        assertEquals(JobParameters.STOP_REASON_CONSTRAINT_CONNECTIVITY,
                mTestAppInterface.getLastParams().getStopReason());
    }

    @Test
    public void testRestrictingStopReason_RestrictedBucket_idle() throws Exception {
        assumeTrue("app standby not enabled", mAppStandbyEnabled);

        // This test is designed for the old quota system.
        mTareDeviceConfigStateHelper.set("enable_tare_mode", "0");

        setTestPackageStandbyBucket(Bucket.RESTRICTED);

        // Disable coalescing and the parole session
        mDeviceConfigStateHelper.set("qc_timing_session_coalescing_duration_ms", "0");
        mDeviceConfigStateHelper.set("qc_max_session_count_restricted", "0");

        // Satisfy all additional constraints.
        setChargingState(true);
        BatteryUtils.runDumpsysBatterySetLevel(100);
        setScreenState(false);
        triggerJobIdle();

        // Idle
        mTestAppInterface.scheduleJob(false, NETWORK_TYPE_NONE, false);
        runJob();
        assertTrue("New job didn't start in RESTRICTED bucket",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
        setScreenState(true);
        assertTrue("New job didn't stop when device no longer idle",
                mTestAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT));
        assertEquals(JobParameters.STOP_REASON_APP_STANDBY,
                mTestAppInterface.getLastParams().getStopReason());
    }

    @Test
    public void testRestrictingStopReason_RestrictedBucket_charging() throws Exception {
        assumeTrue("app standby not enabled", mAppStandbyEnabled);
        // Can't toggle charging state if there's no battery.
        assumeTrue("device doesn't have battery", BatteryUtils.hasBattery());

        // This test is designed for the old quota system.
        mTareDeviceConfigStateHelper.set("enable_tare_mode", "0");

        setTestPackageStandbyBucket(Bucket.RESTRICTED);

        // Disable coalescing and the parole session
        mDeviceConfigStateHelper.set("qc_timing_session_coalescing_duration_ms", "0");
        mDeviceConfigStateHelper.set("qc_max_session_count_restricted", "0");

        // Satisfy all additional constraints.
        setChargingState(true);
        BatteryUtils.runDumpsysBatterySetLevel(100);
        setScreenState(false);
        triggerJobIdle();

        // Charging
        mTestAppInterface.scheduleJob(false, NETWORK_TYPE_NONE, false);
        runJob();
        assertTrue("New job didn't start in RESTRICTED bucket",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
        setChargingState(false);
        assertTrue("New job didn't stop when device no longer charging",
                mTestAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT));
        assertEquals(JobParameters.STOP_REASON_APP_STANDBY,
                mTestAppInterface.getLastParams().getStopReason());
    }

    @Test
    public void testRestrictingStopReason_RestrictedBucket_batteryNotLow() throws Exception {
        assumeTrue("app standby not enabled", mAppStandbyEnabled);
        assumeTrue("device doesn't have battery", BatteryUtils.hasBattery());

        // This test is designed for the old quota system.
        mTareDeviceConfigStateHelper.set("enable_tare_mode", "0");

        setTestPackageStandbyBucket(Bucket.RESTRICTED);

        // Disable coalescing and the parole session
        mDeviceConfigStateHelper.set("qc_timing_session_coalescing_duration_ms", "0");
        mDeviceConfigStateHelper.set("qc_max_session_count_restricted", "0");

        // Satisfy all additional constraints.
        setChargingState(true);
        BatteryUtils.runDumpsysBatterySetLevel(100);
        setScreenState(false);
        triggerJobIdle();

        // Battery not low
        mTestAppInterface.scheduleJob(false, NETWORK_TYPE_NONE, false);
        runJob();
        assertTrue("New job didn't start in RESTRICTED bucket",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
        BatteryUtils.runDumpsysBatterySetLevel(1);
        assertTrue("New job didn't stop when battery too low",
                mTestAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT));
        assertEquals(JobParameters.STOP_REASON_APP_STANDBY,
                mTestAppInterface.getLastParams().getStopReason());
    }

    @Test
    public void testRestrictingStopReason_Quota() throws Exception {
        assumeTrue("app standby not enabled", mAppStandbyEnabled);
        assumeTrue("device doesn't have battery", BatteryUtils.hasBattery());

        // This test is designed for the old quota system.
        mTareDeviceConfigStateHelper.set("enable_tare_mode", "0");

        // Reduce allowed time for testing.
        mDeviceConfigStateHelper.set("qc_allowed_time_per_period_rare_ms", "60000");
        setChargingState(false);
        setTestPackageStandbyBucket(Bucket.RARE);

        sendScheduleJobBroadcast(false);
        runJob();
        assertTrue("New job didn't start",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));

        Thread.sleep(60000);

        assertTrue("New job didn't stop after using up quota",
                mTestAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT));
        assertEquals(JobParameters.STOP_REASON_QUOTA,
                mTestAppInterface.getLastParams().getStopReason());
    }

    /*
    Tests currently disabled because they require changes inside the framework to lower the minimum
    EJ quota to one minute (from 5 minutes).
    TODO(224533485): make JS testable enough to enable these tests

    @Test
    public void testRestrictingStopReason_ExpeditedQuota_startOnCharging() throws Exception {
        assumeTrue("app standby not enabled", mAppStandbyEnabled);
        assumeFalse("not testable in automotive device", mAutomotiveDevice); // Test needs battery
        assumeFalse("not testable in leanback device", mLeanbackOnly); // Test needs battery

        // Reduce allowed time for testing. System to cap the time above 30 seconds.
        mDeviceConfigStateHelper.set("qc_ej_limit_rare_ms", "30000");
        mDeviceConfigStateHelper.set("runtime_min_ej_guarantee_ms", "30000");
        // Start with charging so JobScheduler thinks the job can run for the maximum amount of
        // time. We turn off charging later so quota clearly comes into effect.
        setChargingState(true);
        setTestPackageStandbyBucket(Bucket.RARE);

        mTestAppInterface.scheduleJob(false, NETWORK_TYPE_NONE, true);
        runJob();
        assertTrue("New job didn't start",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
        assertTrue(mTestAppInterface.getLastParams().isExpeditedJob());
        setChargingState(false);

        assertFalse("Job stopped before using up quota",
                mTestAppInterface.awaitJobStop(45_000));
        Thread.sleep(15_000);

        assertTrue("Job didn't stop after using up quota",
                mTestAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT));
        assertEquals(JobParameters.STOP_REASON_QUOTA,
                mTestAppInterface.getLastParams().getStopReason());
    }

    @Test
    public void testRestrictingStopReason_ExpeditedQuota_noCharging() throws Exception {
        assumeTrue("app standby not enabled", mAppStandbyEnabled);
        assumeFalse("not testable in automotive device", mAutomotiveDevice); // Test needs battery
        assumeFalse("not testable in leanback device", mLeanbackOnly); // Test needs battery

        // Reduce allowed time for testing.
        mDeviceConfigStateHelper.set("qc_ej_limit_rare_ms", "30000");
        mDeviceConfigStateHelper.set("runtime_min_ej_guarantee_ms", "30000");
        setChargingState(false);
        setTestPackageStandbyBucket(Bucket.RARE);

        mTestAppInterface.scheduleJob(false, NETWORK_TYPE_NONE, true);
        runJob();
        assertTrue("New job didn't start",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));
        assertTrue(mTestAppInterface.getLastParams().isExpeditedJob());

        assertFalse("Job stopped before using up quota",
                mTestAppInterface.awaitJobStop(45_000));
        Thread.sleep(15_000);

        assertTrue("Job didn't stop after using up quota",
                mTestAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT));
        // Charging state was false when the job started, so the trigger the timeout before
        // QuotaController officially marks the quota finished.
        final int stopReason = mTestAppInterface.getLastParams().getStopReason();
        assertTrue(stopReason == JobParameters.STOP_REASON_TIMEOUT
                || stopReason == JobParameters.STOP_REASON_QUOTA);
    }
     */

    @Test
    public void testRestrictingStopReason_BatterySaver() throws Exception {
        BatteryUtils.assumeBatterySaverFeature();

        setChargingState(false);
        BatteryUtils.enableBatterySaver(false);
        sendScheduleJobBroadcast(false);
        runJob();
        assertTrue("Job did not start after scheduling",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));

        BatteryUtils.enableBatterySaver(true);
        assertTrue("Job did not stop on entering battery saver",
                mTestAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT));
        assertEquals(JobParameters.STOP_REASON_DEVICE_STATE,
                mTestAppInterface.getLastParams().getStopReason());
    }

    @Test
    public void testRestrictingStopReason_Doze() throws Exception {
        assumeTrue("device idle not enabled", mDeviceIdleEnabled);

        toggleDozeState(false);
        mTestAppInterface.scheduleJob(false, NETWORK_TYPE_NONE, false);
        runJob();
        assertTrue("Job did not start after scheduling",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT));

        toggleDozeState(true);
        assertTrue("Job did not stop on entering doze",
                mTestAppInterface.awaitJobStop(DEFAULT_WAIT_TIMEOUT));
        assertEquals(JobParameters.STOP_REASON_DEVICE_STATE,
                mTestAppInterface.getLastParams().getStopReason());
    }

    @After
    public void tearDown() throws Exception {
        AppOpsUtils.reset(TEST_APP_PACKAGE);
        // Lock thermal service to not throttling
        ThermalUtils.overrideThermalNotThrottling();
        if (mDeviceIdleEnabled || mDeviceLightIdleEnabled) {
            resetDozeState();
        }
        mTestAppInterface.cleanup();
        mUiDevice.executeShellCommand("cmd jobscheduler monitor-battery off");
        BatteryUtils.runDumpsysBatteryReset();
        BatteryUtils.resetBatterySaver();
        Settings.Global.putString(mContext.getContentResolver(),
                Settings.Global.BATTERY_STATS_CONSTANTS, mInitialBatteryStatsConstants);
        setPowerAllowlistState(false);

        mNetworkingHelper.tearDown();
        mDeviceConfigStateHelper.restoreOriginalValues();
        mActivityManagerDeviceConfigStateHelper.restoreOriginalValues();
        mTareDeviceConfigStateHelper.restoreOriginalValues();

        mUiDevice.executeShellCommand(
                "cmd jobscheduler reset-execution-quota -u " + UserHandle.myUserId()
                        + " " + TEST_APP_PACKAGE);

        Settings.System.putString(
                mContext.getContentResolver(), SCREEN_OFF_TIMEOUT, mInitialDisplayTimeout);

        Settings.Global.putString(mContext.getContentResolver(),
                Settings.Global.ACTIVITY_MANAGER_CONSTANTS, mInitialActivityManagerConstants);
    }

    private void setTestPackageRestricted(boolean restricted) throws Exception {
        AppOpsUtils.setOpMode(TEST_APP_PACKAGE, "RUN_ANY_IN_BACKGROUND",
                restricted ? AppOpsManager.MODE_IGNORED : AppOpsManager.MODE_ALLOWED);
    }

    private void toggleAutoRestrictedBucketOnBgRestricted(boolean enable) {
        mActivityManagerDeviceConfigStateHelper.set("bg_auto_restricted_bucket_on_bg_restricted",
                Boolean.toString(enable));
    }

    private void sendScheduleJobBroadcast(boolean allowWhileIdle) throws Exception {
        mTestAppInterface.scheduleJob(allowWhileIdle, NETWORK_TYPE_NONE, false);
    }

    private void resetDozeState() throws Exception {
        mUiDevice.executeShellCommand("cmd deviceidle unforce");
    }

    private void toggleDozeState(final boolean idle) throws Exception {
        final String changeCommand;
        if (idle) {
            changeCommand = "force-idle " + (mDeviceIdleEnabled ? "deep" : "light");
        } else {
            changeCommand = "force-active";
        }
        mUiDevice.executeShellCommand("cmd deviceidle " + changeCommand);
        assertTrue("Could not change device idle state to " + idle,
                waitUntilTrue(SHELL_TIMEOUT, () -> {
                    if (idle) {
                        return mDeviceIdleEnabled
                                ? mPowerManager.isDeviceIdleMode()
                                : mPowerManager.isDeviceLightIdleMode();
                    } else {
                        return !mPowerManager.isDeviceIdleMode()
                                && !mPowerManager.isDeviceLightIdleMode();
                    }
                }));
    }

    private void tempWhitelistTestApp(long duration) throws Exception {
        mUiDevice.executeShellCommand("cmd deviceidle tempwhitelist -d " + duration
                + " -u " + UserHandle.myUserId()
                + " " + TEST_APP_PACKAGE);
    }

    private void setPowerAllowlistState(boolean add) throws Exception {
        mUiDevice.executeShellCommand("cmd deviceidle whitelist " + (add ? "+" : "-")
                + TEST_APP_PACKAGE);
    }

    private void makeTestPackageIdle() throws Exception {
        mUiDevice.executeShellCommand("am make-uid-idle --user current " + TEST_APP_PACKAGE);
    }

    void setTestPackageStandbyBucket(Bucket bucket) throws Exception {
        setTestPackageStandbyBucket(mUiDevice, bucket);
    }

    static void setTestPackageStandbyBucket(UiDevice uiDevice, Bucket bucket) throws Exception {
        final String bucketName;
        switch (bucket) {
            case ACTIVE:
                bucketName = "active";
                break;
            case WORKING_SET:
                bucketName = "working";
                break;
            case FREQUENT:
                bucketName = "frequent";
                break;
            case RARE:
                bucketName = "rare";
                break;
            case RESTRICTED:
                bucketName = "restricted";
                break;
            case NEVER:
                bucketName = "never";
                break;
            default:
                throw new IllegalArgumentException("Requested unknown bucket " + bucket);
        }
        uiDevice.executeShellCommand("am set-standby-bucket " + TEST_APP_PACKAGE
                + " " + bucketName);
    }

    /**
     * Set the screen state.
     */
    private void setScreenState(boolean on) throws Exception {
        setScreenState(mUiDevice, on);
    }

    static void setScreenState(UiDevice uiDevice, boolean on) throws Exception {
        PowerManager powerManager =
                InstrumentationRegistry.getContext().getSystemService(PowerManager.class);
        if (on) {
            uiDevice.executeShellCommand("input keyevent KEYCODE_WAKEUP");
            uiDevice.executeShellCommand("wm dismiss-keyguard");
            waitUntil("Device not interactive", () -> powerManager.isInteractive());
        } else {
            uiDevice.executeShellCommand("input keyevent KEYCODE_SLEEP");
            waitUntil("Device still interactive", () -> !powerManager.isInteractive());
        }
        // Wait a little bit to make sure the screen state has changed.
        Thread.sleep(4_000);
    }

    private void setChargingState(boolean isCharging) throws Exception {
        mUiDevice.executeShellCommand("cmd jobscheduler monitor-battery on");

        final String command;
        if (isCharging) {
            mUiDevice.executeShellCommand("cmd battery set ac 1");
            final int curLevel = Integer.parseInt(
                    mUiDevice.executeShellCommand("dumpsys battery get level").trim());
            command = "cmd battery set -f level " + Math.min(100, curLevel + 1);
        } else {
            command = "cmd battery unplug -f";
        }
        int seq = Integer.parseInt(mUiDevice.executeShellCommand(command).trim());

        // Wait for the battery update to be processed by job scheduler before proceeding.
        waitUntil("JobScheduler didn't update charging status to " + isCharging, 15 /* seconds */,
                () -> {
                    int curSeq;
                    boolean curCharging;
                    curSeq = Integer.parseInt(mUiDevice.executeShellCommand(
                            "cmd jobscheduler get-battery-seq").trim());
                    curCharging = Boolean.parseBoolean(mUiDevice.executeShellCommand(
                            "cmd jobscheduler get-battery-charging").trim());
                    return curSeq >= seq && curCharging == isCharging;
                });
    }

    /**
     * Trigger job idle (not device idle);
     */
    private void triggerJobIdle() throws Exception {
        mUiDevice.executeShellCommand("cmd activity idle-maintenance");
        // Wait a moment to let that happen before proceeding.
        Thread.sleep(2_000);
    }

    /** Asks (not forces) JobScheduler to run the job if constraints are met. */
    private void runJob() throws Exception {
        // Since connectivity is a functional constraint, calling the "run" command without force
        // will only get the job to run if the constraint is satisfied.
        mUiDevice.executeShellCommand("cmd jobscheduler run -s"
                + " -u " + UserHandle.myUserId() + " " + TEST_APP_PACKAGE + " " + mTestJobId);
    }

    private boolean hasEthernetConnection() {
        return mNetworkingHelper.hasEthernetConnection();
    }

    private String getJobState() throws Exception {
        return mUiDevice.executeShellCommand("cmd jobscheduler get-job-state --user cur "
                + TEST_APP_PACKAGE + " " + mTestJobId).trim();
    }

    private void assertJobWaiting() throws Exception {
        String state = getJobState();
        assertTrue("Job unexpectedly not waiting, in state: " + state, state.contains("waiting"));
    }

    private void assertJobNotReady() throws Exception {
        String state = getJobState();
        assertFalse("Job unexpectedly ready, in state: " + state, state.contains("ready"));
    }

    private void assertJobReady() throws Exception {
        String state = getJobState();
        assertTrue("Job unexpectedly not ready, in state: " + state, state.contains("ready"));
    }

    private boolean waitUntilTrue(long maxWait, Condition condition) throws Exception {
        final long deadLine = SystemClock.uptimeMillis() + maxWait;
        do {
            Thread.sleep(POLL_INTERVAL);
        } while (!condition.isTrue() && SystemClock.uptimeMillis() < deadLine);
        return condition.isTrue();
    }

    private interface Condition {
        boolean isTrue() throws Exception;
    }
}
