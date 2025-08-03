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

package android.compilation.cts;

import com.android.tradefed.util.RunUtil;
import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import com.google.common.io.ByteStreams;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

/**
 * Tests background dex optimization which runs as idle job.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class BackgroundDexOptimizationTest extends BaseHostJUnit4Test {
    private static final long REBOOT_TIMEOUT_MS = 600_000;
    private static final long JOB_START_TIMEOUT_MS = 30_000;
    private static final long DEXOPT_TIMEOUT_MS = 1_200_000;
    // Cancel should be faster. It will be usually much shorter but we cannot make it too short
    // as CTS cannot enforce unspecified performance.
    private static final long DEXOPT_CANCEL_TIMEOUT_MS = 30_000;
    private static final long POLLING_TIME_SLICE = 200;

    private static final String CMD_DUMP_PACKAGE_DEXOPT = "dumpsys -t 100 package dexopt";

    private static final String CMD_START_POST_BOOT = "cmd jobscheduler run android 801";
    private static final String CMD_CANCEL_POST_BOOT = "cmd jobscheduler timeout android 801";
    private static final String CMD_START_IDLE = "cmd jobscheduler run android 800";
    private static final String CMD_CANCEL_IDLE = "cmd jobscheduler timeout android 800";

    private static final String APPLICATION_PACKAGE = "android.compilation.cts";
    private static final String APPLICATION_APK = "CtsCompilationApp";
    private static final String CMD_APP_ACTIVITY_LAUNCH =
            "am start -n " + APPLICATION_PACKAGE + "/.CompilationTargetActivity";

    private static final String CMD_DELETE_ODEX = "pm delete-dexopt " + APPLICATION_PACKAGE;

    private static final boolean DBG_LOG_CMD = true;

    // Uses internal consts defined in BackgroundDexOptService only for testing purpose.
    private static final int STATUS_OK = 0;
    private static final int STATUS_CANCELLED = 1;
    // We allow package level failure in dexopt, which will lead into this error state.
    private static final int STATUS_DEX_OPT_FAILED = 5;

    private ITestDevice mDevice;

    @Before
    public void setUp() throws Exception {
        mDevice = getDevice();
        assumeFalse(mDevice.getBooleanProperty("dalvik.vm.useartservice", false));
        assertThat(mDevice.waitForBootComplete(REBOOT_TIMEOUT_MS)).isTrue();
        // Turn off the display to simulate the idle state in terms of power consumption.
        toggleScreenOn(false);
    }

    @After
    public void tearDown() throws Exception {
        // Restore the display state. CTS runs display on state by default. So we need to turn it
        // on again.
        toggleScreenOn(true);
        // Cancel all active dexopt jobs.
        executeShellCommand(CMD_CANCEL_IDLE);
        executeShellCommand(CMD_CANCEL_POST_BOOT);
        mDevice.uninstallPackage(APPLICATION_PACKAGE);
    }

    @Test
    public void testIdleOptimizationCompleted() throws Exception {
        assumeTrue(checkDexOptEnabled());
        // We check if post boot optimization is completed, and wait for it to be completed if not.
        // Note that this won't work if the system server has been restarted (e.g., by a `stop &&
        // start`) AND this test case is run individually, in which case,
        // `checkFinishedPostBootUpdate` will return false because the system server will lose track
        // of a completed post boot optimization run, but `completePostBootOptimization` will get
        // stuck retrying to start the job since it has already completed.
        ensurePostBootOptimizationCompleted();

        completeIdleOptimization();
        // idle job can run again.
        completeIdleOptimization();
    }

    @Test
    public void testIdleOptimizationCancelled() throws Exception {
        assumeTrue(checkDexOptEnabled());
        // We check if post boot optimization is completed, and wait for it to be completed if not.
        // Note that this won't work if the system server has been restarted (e.g., by a `stop &&
        // start`) AND this test case is run individually, in which case,
        // `checkFinishedPostBootUpdate` will return false because the system server will lose track
        // of a completed post boot optimization run, but `completePostBootOptimization` will get
        // stuck retrying to start the job since it has already completed.
        ensurePostBootOptimizationCompleted();

        reinstallAppPackage();
        LastDeviceExecutionTime timeBefore = getLastExecutionTime();
        postJobSchedulerJob(CMD_START_IDLE);

        // Wait until it is started.
        pollingCheck("Idle start timeout", JOB_START_TIMEOUT_MS,
                () -> getLastExecutionTime().startTime >= timeBefore.deviceCurrentTime);

        // Now cancel it.
        executeShellCommand(CMD_CANCEL_IDLE);

        // Wait until it is completed or cancelled.
        pollingCheck("Idle cancel timeout", DEXOPT_CANCEL_TIMEOUT_MS,
                () -> getLastExecutionTime().duration >= 0);

        int status = getLastDexOptStatus();
        assertThat(status).isAnyOf(STATUS_OK, STATUS_DEX_OPT_FAILED, STATUS_CANCELLED);
        if (status == STATUS_CANCELLED) {
            // If cancelled, we can complete it by running it again.
            completeIdleOptimization();
        }
    }

    private String executeShellCommand(String cmd) throws Exception {
        String result =  mDevice.executeShellCommand(cmd);
        if (DBG_LOG_CMD) {
            CLog.i("Executed cmd:" + cmd + ", result:" + result);
        }
        return result;
    }

    private void completePostBootOptimization() throws Exception {
        reinstallAppPackage();
        LastDeviceExecutionTime timeBefore = getLastExecutionTime();
        postJobSchedulerJob(CMD_START_POST_BOOT);

        pollingCheck("Post boot optimization timeout", DEXOPT_TIMEOUT_MS,
                () -> checkFinishedPostBootUpdate());

        LastDeviceExecutionTime timeAfter = getLastExecutionTime();
        assertThat(timeAfter.startTime).isAtLeast(timeBefore.deviceCurrentTime);
        assertThat(timeAfter.duration).isAtLeast(0);
        int status = getLastDexOptStatus();
        assertThat(status).isAnyOf(STATUS_OK, STATUS_DEX_OPT_FAILED);
    }

    private void completeIdleOptimization() throws Exception {
        reinstallAppPackage();
        LastDeviceExecutionTime timeBefore = getLastExecutionTime();
        postJobSchedulerJob(CMD_START_IDLE);

        pollingCheck("Idle optimization timeout", DEXOPT_TIMEOUT_MS,
                () -> {
                    LastDeviceExecutionTime executionTime = getLastExecutionTime();
                    return executionTime.startTime >= timeBefore.deviceCurrentTime
                            && executionTime.duration >= 0;
                });

        int status = getLastDexOptStatus();
        assertThat(status).isAnyOf(STATUS_OK, STATUS_DEX_OPT_FAILED);
    }

    /**
     * Turns on or off the screen.
     */
    private void toggleScreenOn(boolean on) throws Exception {
        if (on) {
            executeShellCommand("input keyevent KEYCODE_WAKEUP");
        } else {
            executeShellCommand("input keyevent KEYCODE_SLEEP");
        }
    }

    private void postJobSchedulerJob(String cmd) throws Exception {
        // Do retry as job may not be registered yet during boot up.
        pollingCheck("Starting job timeout:" + cmd, DEXOPT_TIMEOUT_MS,
                () -> {
                    String r = executeShellCommand(cmd);
                    return r.contains("Running");
                });
    }

    private void reinstallAppPackage() throws Exception {
        mDevice.uninstallPackage(APPLICATION_PACKAGE);

        File apkFile = File.createTempFile(APPLICATION_APK, ".apk");
        try (OutputStream outputStream = new FileOutputStream(apkFile)) {
            InputStream inputStream = getClass().getResourceAsStream(
                    "/" + APPLICATION_APK + ".apk");
            ByteStreams.copy(inputStream, outputStream);
        }
        String error = mDevice.installPackage(apkFile, /* reinstall= */ false);

        assertThat(error).isNull();

        // Delete odex files.
        executeShellCommand(CMD_DELETE_ODEX);
        executeShellCommand(CMD_APP_ACTIVITY_LAUNCH);
        // Give short time to run some code.
        RunUtil.getDefault().sleep(500);
    }

    private boolean checkDexOptEnabled() throws Exception {
        return checkBooleanDumpValue("enabled");
    }

    private boolean checkFinishedPostBootUpdate() throws Exception {
        return checkBooleanDumpValue("mFinishedPostBootUpdate");
    }

    private boolean checkBooleanDumpValue(String key) throws Exception {
        String value = findDumpValueForKey(key);
        assertThat(value).isNotNull();
        return value.equals("true");
    }

    private String findDumpValueForKey(String key) throws Exception {
        for (String line: getDexOptDumpForBgDexOpt()) {
            String[] vals = line.split(":");
            if (vals[0].equals(key)) {
                return vals[1];
            }
        }
        return null;
    }

    private List<String> getDexOptDumpForBgDexOpt() throws Exception {
        String dump = executeShellCommand(CMD_DUMP_PACKAGE_DEXOPT);
        String[] lines = dump.split("\n");
        LinkedList<String> bgDexOptDumps = new LinkedList<>();
        // BgDexopt state is located in the last part from the dexopt dump. So there is no separate
        // end of the dump check.
        boolean inBgDexOptDump = false;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("BgDexopt state:")) {
                inBgDexOptDump = true;
            } else if (inBgDexOptDump) {
                bgDexOptDumps.add(lines[i].trim());
            }
        }
        // dumpsys package can expire due to the lock while bgdexopt is running.
        if (dump.contains("DUMP TIMEOUT")) {
            CLog.w("package dump timed out");
            throw new TimeoutException();
        }
        return bgDexOptDumps;
    }

    private int getLastDexOptStatus() throws Exception {
        String value = findDumpValueForKey("mLastExecutionStatus");
        assertThat(value).isNotNull();
        return Integer.parseInt(value);
    }

    private LastDeviceExecutionTime getLastExecutionTime() throws Exception {
        long startTime = 0;
        long duration = 0;
        long deviceCurrentTime = 0;
        for (String line: getDexOptDumpForBgDexOpt()) {
            String[] vals = line.split(":");
            switch (vals[0]) {
                case "mLastExecutionStartUptimeMs":
                    startTime = Long.parseLong(vals[1]);
                    break;
                case "mLastExecutionDurationMs":
                    duration = Long.parseLong(vals[1]);
                    break;
                case "now":
                    deviceCurrentTime = Long.parseLong(vals[1]);
                    break;
            }
        }
        assertThat(deviceCurrentTime).isNotEqualTo(0);
        return new LastDeviceExecutionTime(startTime, duration, deviceCurrentTime);
    }

    private String getLastStatusDump() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n--LastStatus--\n");
        try {
            sb.append(getLastExecutionTime());
            sb.append("\n");
            sb.append("Last DexOpt Status:");
            sb.append(getLastDexOptStatus());
            sb.append("\n");
        } catch (Exception e) {
            sb.append("\nGetting status failed:" + e);
        }

        return sb.toString();
    }

    private void pollingCheck(CharSequence message, long timeout,
            Callable<Boolean> condition) throws Exception {
        long expirationTime = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < expirationTime) {
            try {
                if (condition.call()) {
                    return;
                }
            } catch (TimeoutException e) {
                // DUMP TIMEOUT has happened. Ignore it as we have to retry.
            }
            RunUtil.getDefault().sleep(POLLING_TIME_SLICE);
        }
        fail(message.toString() + getLastStatusDump());
    }

    private void ensurePostBootOptimizationCompleted() throws Exception {
        if (!checkFinishedPostBootUpdate()) {
            completePostBootOptimization();
        }
    }

    private static class LastDeviceExecutionTime {
        public final long startTime;
        public final long duration;
        public final long deviceCurrentTime;

        private LastDeviceExecutionTime(long startTime, long duration, long deviceCurrentTime) {
            this.startTime = startTime;
            this.duration = duration;
            this.deviceCurrentTime = deviceCurrentTime;
        }

        @Override
        public String toString() {
            return String.format("LastExecution{startTime=%d, duration=%d, deviceTime=%d}",
                    startTime, duration, deviceCurrentTime);
        }
    }
}
