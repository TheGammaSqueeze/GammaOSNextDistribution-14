/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static android.server.wm.WindowManagerState.STATE_RESUMED;

import static com.android.compatibility.common.util.TestUtils.waitUntil;

import android.annotation.CallSuper;
import android.annotation.TargetApi;
import android.app.Instrumentation;
import android.app.job.JobScheduler;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.jobscheduler.MockJobService;
import android.jobscheduler.TestActivity;
import android.jobscheduler.TriggerContentJobService;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.server.wm.WindowManagerStateHelper;
import android.test.InstrumentationTestCase;
import android.util.Log;

import com.android.compatibility.common.util.BatteryUtils;
import com.android.compatibility.common.util.DeviceConfigStateHelper;
import com.android.compatibility.common.util.SystemUtil;

import java.io.IOException;

/**
 * Common functionality from which the other test case classes derive.
 */
@TargetApi(21)
public abstract class BaseJobSchedulerTest extends InstrumentationTestCase {
    private static final String TAG = BaseJobSchedulerTest.class.getSimpleName();
    static final int HW_TIMEOUT_MULTIPLIER = SystemProperties.getInt("ro.hw_timeout_multiplier", 1);

    /** Environment that notifies of JobScheduler callbacks. */
    static MockJobService.TestEnvironment kTestEnvironment =
            MockJobService.TestEnvironment.getTestEnvironment();
    static TriggerContentJobService.TestEnvironment kTriggerTestEnvironment =
            TriggerContentJobService.TestEnvironment.getTestEnvironment();
    /** Handle for the service which receives the execution callbacks from the JobScheduler. */
    static ComponentName kJobServiceComponent;
    static ComponentName kTriggerContentServiceComponent;
    JobScheduler mJobScheduler;

    Context mContext;
    DeviceConfigStateHelper mDeviceConfigStateHelper;

    static final String MY_PACKAGE = "android.jobscheduler.cts";

    static final String JOBPERM_PACKAGE = "android.jobscheduler.cts.jobperm";
    static final String JOBPERM_AUTHORITY = "android.jobscheduler.cts.jobperm.provider";
    static final String JOBPERM_PERM = "android.jobscheduler.cts.jobperm.perm";

    Uri mFirstUri;
    Bundle mFirstUriBundle;
    Uri mSecondUri;
    Bundle mSecondUriBundle;
    ClipData mFirstClipData;
    ClipData mSecondClipData;

    boolean mStorageStateChanged;
    boolean mActivityStarted;

    private boolean mDeviceIdleEnabled;
    private boolean mDeviceLightIdleEnabled;

    private String mInitialBatteryStatsConstants;

    @Override
    public void injectInstrumentation(Instrumentation instrumentation) {
        super.injectInstrumentation(instrumentation);
        mContext = instrumentation.getContext();
        kJobServiceComponent = new ComponentName(getContext(), MockJobService.class);
        kTriggerContentServiceComponent = new ComponentName(getContext(),
                TriggerContentJobService.class);
        mJobScheduler = (JobScheduler) getContext().getSystemService(Context.JOB_SCHEDULER_SERVICE);
        mFirstUri = Uri.parse("content://" + JOBPERM_AUTHORITY + "/protected/foo");
        mFirstUriBundle = new Bundle();
        mFirstUriBundle.putParcelable("uri", mFirstUri);
        mSecondUri = Uri.parse("content://" + JOBPERM_AUTHORITY + "/protected/bar");
        mSecondUriBundle = new Bundle();
        mSecondUriBundle.putParcelable("uri", mSecondUri);
        mFirstClipData = new ClipData("JobPerm1", new String[] { "application/*" },
                new ClipData.Item(mFirstUri));
        mSecondClipData = new ClipData("JobPerm2", new String[] { "application/*" },
                new ClipData.Item(mSecondUri));
        try {
            SystemUtil.runShellCommand(getInstrumentation(), "cmd activity set-inactive "
                    + mContext.getPackageName() + " false");
        } catch (IOException e) {
            Log.w("ConstraintTest", "Failed setting inactive false", e);
        }
        if (HW_TIMEOUT_MULTIPLIER != 0) {
            Log.i(TAG, "HW multiplier set to " + HW_TIMEOUT_MULTIPLIER);
        }
    }

    public Context getContext() {
        return mContext;
    }

    @CallSuper
    @Override
    public void setUp() throws Exception {
        super.setUp();
        mDeviceConfigStateHelper =
                new DeviceConfigStateHelper(DeviceConfig.NAMESPACE_JOB_SCHEDULER);
        SystemUtil.runShellCommand("cmd jobscheduler cache-config-changes on");
        // Disable batching behavior.
        mDeviceConfigStateHelper.set("min_ready_cpu_only_jobs_count", "0");
        mDeviceConfigStateHelper.set("min_ready_non_active_jobs_count", "0");
        mDeviceConfigStateHelper.set("conn_transport_batch_threshold", "");
        // Disable flex behavior.
        mDeviceConfigStateHelper.set("fc_applied_constraints", "0");
        kTestEnvironment.setUp();
        kTriggerTestEnvironment.setUp();
        mJobScheduler.cancelAll();

        mDeviceIdleEnabled = isDeviceIdleEnabled();
        mDeviceLightIdleEnabled = isDeviceLightIdleEnabled();
        if (mDeviceIdleEnabled || mDeviceLightIdleEnabled) {
            // Make sure the device isn't dozing since it will affect execution of regular jobs
            setDeviceIdleState(false);
        }

        mInitialBatteryStatsConstants = Settings.Global.getString(mContext.getContentResolver(),
                Settings.Global.BATTERY_STATS_CONSTANTS);
        // Make sure ACTION_CHARGING is sent immediately.
        Settings.Global.putString(mContext.getContentResolver(),
                Settings.Global.BATTERY_STATS_CONSTANTS, "battery_charged_delay_ms=0");
    }

    @CallSuper
    @Override
    public void tearDown() throws Exception {
        SystemUtil.runShellCommand("cmd jobscheduler cache-config-changes off");
        SystemUtil.runShellCommand(getInstrumentation(), "cmd jobscheduler monitor-battery off");
        SystemUtil.runShellCommand(getInstrumentation(), "cmd battery reset");
        Settings.Global.putString(mContext.getContentResolver(),
                Settings.Global.BATTERY_STATS_CONSTANTS, mInitialBatteryStatsConstants);
        if (mStorageStateChanged) {
            // Put storage service back in to normal operation.
            SystemUtil.runShellCommand(getInstrumentation(), "cmd devicestoragemonitor reset");
            mStorageStateChanged = false;
        }
        SystemUtil.runShellCommand(getInstrumentation(),
                "cmd jobscheduler reset-execution-quota -u current "
                        + kJobServiceComponent.getPackageName());
        mDeviceConfigStateHelper.restoreOriginalValues();

        if (mActivityStarted) {
            closeActivity();
        }

        if (mDeviceIdleEnabled || mDeviceLightIdleEnabled) {
            resetDeviceIdleState();
        }

        // The super method should be called at the end.
        super.tearDown();
    }

    public void assertHasUriPermission(Uri uri, int grantFlags) {
        if ((grantFlags&Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
            assertEquals(PackageManager.PERMISSION_GRANTED,
                    getContext().checkUriPermission(uri, Process.myPid(),
                            Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION));
        }
        if ((grantFlags&Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0) {
            assertEquals(PackageManager.PERMISSION_GRANTED,
                    getContext().checkUriPermission(uri, Process.myPid(),
                            Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
        }
    }

    void waitPermissionRevoke(Uri uri, int access, long timeout) {
        long startTime = SystemClock.elapsedRealtime();
        while (getContext().checkUriPermission(uri, Process.myPid(), Process.myUid(), access)
                != PackageManager.PERMISSION_DENIED) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
            }
            if ((SystemClock.elapsedRealtime()-startTime) >= timeout) {
                fail("Timed out waiting for permission revoke");
            }
        }
    }

    boolean isDeviceIdleFeatureEnabled() throws Exception {
        return mDeviceIdleEnabled || mDeviceLightIdleEnabled;
    }

    static boolean isDeviceIdleEnabled() throws Exception {
        final String output = SystemUtil.runShellCommand("cmd deviceidle enabled deep").trim();
        return Integer.parseInt(output) != 0;
    }

    static boolean isDeviceLightIdleEnabled() throws Exception {
        final String output = SystemUtil.runShellCommand("cmd deviceidle enabled light").trim();
        return Integer.parseInt(output) != 0;
    }

    /** Returns the current storage-low state, as believed by JobScheduler. */
    private boolean isJsStorageStateLow() throws Exception {
        return !Boolean.parseBoolean(
                SystemUtil.runShellCommand(getInstrumentation(),
                        "cmd jobscheduler get-storage-not-low").trim());
    }

    // Note we are just using storage state as a way to control when the job gets executed.
    void setStorageStateLow(boolean low) throws Exception {
        if (isJsStorageStateLow() == low) {
            // Nothing to do here
            return;
        }
        mStorageStateChanged = true;
        String res;
        if (low) {
            res = SystemUtil.runShellCommand(getInstrumentation(),
                    "cmd devicestoragemonitor force-low -f");
        } else {
            res = SystemUtil.runShellCommand(getInstrumentation(),
                    "cmd devicestoragemonitor force-not-low -f");
        }
        int seq = Integer.parseInt(res.trim());
        long startTime = SystemClock.elapsedRealtime();

        // Wait for the storage update to be processed by job scheduler before proceeding.
        int curSeq;
        do {
            curSeq = Integer.parseInt(SystemUtil.runShellCommand(getInstrumentation(),
                    "cmd jobscheduler get-storage-seq").trim());
            if (curSeq == seq) {
                return;
            }
            Thread.sleep(500);
        } while ((SystemClock.elapsedRealtime() - startTime) < 10_000);

        fail("Timed out waiting for job scheduler: expected seq=" + seq + ", cur=" + curSeq);
    }

    void startAndKeepTestActivity() {
        final Intent testActivity = new Intent();
        testActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ComponentName testComponentName = new ComponentName(mContext, TestActivity.class);
        testActivity.setComponent(testComponentName);
        mContext.startActivity(testActivity);
        new WindowManagerStateHelper().waitForActivityState(testComponentName, STATE_RESUMED);
        mActivityStarted = true;
    }

    void closeActivity() {
        mContext.sendBroadcast(new Intent(TestActivity.ACTION_FINISH_ACTIVITY));
        mActivityStarted = false;
    }

    void setDeviceConfigFlag(String key, String value, boolean waitForConfirmation)
            throws Exception {
        mDeviceConfigStateHelper.set(key, value);
        if (waitForConfirmation) {
            waitUntil("Config didn't update appropriately to '" + value
                            + "'. Current value=" + getConfigValue(key),
                    5 /* seconds */,
                    () -> {
                        final String curVal = getConfigValue(key);
                        if (value == null) {
                            return "null".equals(curVal);
                        } else {
                            return curVal.equals(value);
                        }
                    });
        }
    }

    static String getConfigValue(String key) {
        return SystemUtil.runShellCommand("cmd jobscheduler get-config-value " + key).trim();
    }

    String getJobState(int jobId) throws Exception {
        return SystemUtil.runShellCommand(getInstrumentation(),
                "cmd jobscheduler get-job-state --user cur "
                        + kJobServiceComponent.getPackageName() + " " + jobId).trim();
    }

    void assertJobReady(int jobId) throws Exception {
        String state = getJobState(jobId);
        assertTrue("Job unexpectedly not ready, in state: " + state, state.contains("ready"));
    }

    void assertJobWaiting(int jobId) throws Exception {
        String state = getJobState(jobId);
        assertTrue("Job unexpectedly not waiting, in state: " + state, state.contains("waiting"));
    }

    void assertJobNotReady(int jobId) throws Exception {
        String state = getJobState(jobId);
        assertTrue("Job unexpectedly ready, in state: " + state, !state.contains("ready"));
    }

    /**
     * Set the screen state.
     */
    static void toggleScreenOn(final boolean screenon) throws Exception {
        BatteryUtils.turnOnScreen(screenon);
        if (screenon) {
            SystemUtil.runShellCommand("wm dismiss-keyguard");
        }
        // Wait a little bit for the broadcasts to be processed.
        Thread.sleep(2_000);
    }

    void resetDeviceIdleState() throws Exception {
        SystemUtil.runShellCommand("cmd deviceidle unforce");
    }

    void setBatteryState(boolean plugged, int level) throws Exception {
        SystemUtil.runShellCommand(getInstrumentation(), "cmd jobscheduler monitor-battery on");
        if (plugged) {
            SystemUtil.runShellCommand(getInstrumentation(), "cmd battery set ac 1");
            final int curLevel = Integer.parseInt(SystemUtil.runShellCommand(getInstrumentation(),
                    "dumpsys battery get level").trim());
            if (curLevel >= level) {
                // Lower the level so when we set it to the desired level, JobScheduler thinks
                // the device is charging.
                SystemUtil.runShellCommand(getInstrumentation(),
                        "cmd battery set level " + Math.max(1, level - 1));
            }
        } else {
            SystemUtil.runShellCommand(getInstrumentation(), "cmd battery unplug");
        }
        int seq = Integer.parseInt(SystemUtil.runShellCommand(getInstrumentation(),
                "cmd battery set -f level " + level).trim());

        // Wait for the battery update to be processed by job scheduler before proceeding.
        waitUntil("JobScheduler didn't update charging status to " + plugged, 15 /* seconds */,
                () -> {
                    int curSeq;
                    boolean curCharging;
                    curSeq = Integer.parseInt(SystemUtil.runShellCommand(getInstrumentation(),
                            "cmd jobscheduler get-battery-seq").trim());
                    curCharging = Boolean.parseBoolean(
                            SystemUtil.runShellCommand(getInstrumentation(),
                                    "cmd jobscheduler get-battery-charging").trim());
                    return curSeq >= seq && curCharging == plugged;
                });
    }

    void setDeviceIdleState(final boolean idle) throws Exception {
        final String changeCommand;
        if (idle) {
            changeCommand = "force-idle " + (mDeviceIdleEnabled ? "deep" : "light");
        } else {
            changeCommand = "force-active";
        }
        SystemUtil.runShellCommand("cmd deviceidle " + changeCommand);
        waitUntil("Could not change device idle state to " + idle, 15 /* seconds */,
                () -> {
                    PowerManager powerManager = getContext().getSystemService(PowerManager.class);
                    if (idle) {
                        return mDeviceIdleEnabled
                                ? powerManager.isDeviceIdleMode()
                                : powerManager.isDeviceLightIdleMode();
                    } else {
                        return !powerManager.isDeviceIdleMode()
                                && !powerManager.isDeviceLightIdleMode();
                    }
                });
    }

    /** Asks (not forces) JobScheduler to run the job if constraints are met. */
    void runSatisfiedJob(int jobId) throws Exception {
        runSatisfiedJob(jobId, null);
    }

    void runSatisfiedJob(int jobId, String namespace) throws Exception {
        if (HW_TIMEOUT_MULTIPLIER > 1) {
            // Device has increased HW multiplier. Wait a short amount of time before sending the
            // run command since there's a higher chance JobScheduler's processing is delayed.
            Thread.sleep(1_000L);
        }
        SystemUtil.runShellCommand(getInstrumentation(),
                "cmd jobscheduler run -s"
                + " -u " + UserHandle.myUserId()
                + (namespace == null ? "" : " -n " + namespace)
                + " " + kJobServiceComponent.getPackageName()
                + " " + jobId);
    }
}
