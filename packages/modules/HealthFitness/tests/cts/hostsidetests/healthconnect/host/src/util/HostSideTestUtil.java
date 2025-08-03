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

package android.healthconnect.cts;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.DeviceUtils;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.RunUtil;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

public class HostSideTestUtil {

    public static final String TEST_APP_PKG_NAME = "android.healthconnect.cts.testhelper";
    public static final String DAILY_LOG_TESTS_ACTIVITY = ".DailyLogsTests";
    private static final int NUMBER_OF_RETRIES = 10;

    private static final String FEATURE_TV = "android.hardware.type.television";
    private static final String FEATURE_EMBEDDED = "android.hardware.type.embedded";
    private static final String FEATURE_WATCH = "android.hardware.type.watch";
    private static final String FEATURE_LEANBACK = "android.software.leanback";
    private static final String FEATURE_AUTOMOTIVE = "android.hardware.type.automotive";

    private static final String ENABLE_RATE_LIMITER_FLAG = "enable_rate_limiter";
    private static final String NAMESPACE_HEALTH_FITNESS = "health_fitness";
    private static String sRateLimiterFlagDefaultValue;

    /** Clears all data on the device, including access logs. */
    public static void clearData(ITestDevice device) throws Exception {
        triggerTestInTestApp(device, DAILY_LOG_TESTS_ACTIVITY, "deleteAllRecordsAddedForTest");
        // Next two lines will delete newly added Access Logs as all access logs over 7 days are
        // deleted by the AutoDeleteService which is run by the daily job.
        increaseDeviceTimeByDays(device, 10);
        triggerDailyJob(device);
    }

    /** Triggers a test on the device with the given className and testName. */
    public static void triggerTestInTestApp(ITestDevice device, String className, String testName)
            throws Exception {

        if (testName != null) {
            DeviceUtils.runDeviceTests(device, TEST_APP_PKG_NAME, className, testName);
        }
    }

    /** Increases the device clock by the given numberOfDays. */
    public static void increaseDeviceTimeByDays(ITestDevice device, int numberOfDays)
            throws DeviceNotAvailableException {
        Instant deviceDate = Instant.ofEpochMilli(device.getDeviceDate());

        device.setDate(Date.from(deviceDate.plus(numberOfDays, ChronoUnit.DAYS)));
        device.executeShellCommand(
                "cmd time_detector set_time_state_for_tests --unix_epoch_time "
                        + deviceDate.plus(numberOfDays, ChronoUnit.DAYS).toEpochMilli()
                        + " --user_should_confirm_time false --elapsed_realtime 0");

        device.executeShellCommand("am broadcast -a android.intent.action.TIME_SET");
    }

    /** Reset device time to revert all changes made during the test. */
    public static void resetTime(ITestDevice device, Instant testStartTime, Instant deviceStartTime)
            throws DeviceNotAvailableException {
        long timeDiff = Duration.between(testStartTime, Instant.now()).toMillis();

        device.executeShellCommand(
                "cmd time_detector set_time_state_for_tests --unix_epoch_time "
                        + deviceStartTime.plusMillis(timeDiff).toEpochMilli()
                        + " --user_should_confirm_time false --elapsed_realtime 0");
        device.executeShellCommand("am broadcast -a android.intent.action.TIME_SET");
    }

    /** Triggers the Health Connect daily job. */
    public static void triggerDailyJob(ITestDevice device) throws Exception {

        // There are multiple instances of HealthConnectDailyService. This command finds the one
        // that needs to be triggered for this test using the job param 'hc_daily_job'.
        String output =
                device.executeShellCommand(
                        "dumpsys jobscheduler | grep -m1 -A0 -B10 \"hc_daily_job\"");
        int indexOfStart = output.indexOf("/") + 1;
        String jobId = output.substring(indexOfStart, output.indexOf(":", indexOfStart));
        String jobExecutionCommand =
                "cmd jobscheduler run --namespace HEALTH_CONNECT_DAILY_JOB -f android " + jobId;

        executeJob(device, jobExecutionCommand, NUMBER_OF_RETRIES);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    private static void executeJob(ITestDevice device, String jobExecutionCommand, int retry)
            throws DeviceNotAvailableException, RuntimeException {
        if (retry == 0) {
            throw new RuntimeException("Could not execute job");
        }
        if (device.executeShellV2Command(jobExecutionCommand).getStatus()
                != CommandStatus.SUCCESS) {
            executeJob(device, jobExecutionCommand, retry - 1);
        }
    }

    /** Checks if the hardware supports Health Connect. */
    public static boolean isHardwareSupported(ITestDevice device) {
        // These UI tests are not optimised for Watches, TVs, Auto;
        // IoT devices do not have a UI to run these UI tests
        try {
            return !DeviceUtils.hasFeature(device, FEATURE_TV)
                    && !DeviceUtils.hasFeature(device, FEATURE_EMBEDDED)
                    && !DeviceUtils.hasFeature(device, FEATURE_WATCH)
                    && !DeviceUtils.hasFeature(device, FEATURE_LEANBACK)
                    && !DeviceUtils.hasFeature(device, FEATURE_AUTOMOTIVE);
        } catch (Exception e) {
            return false;
        }
    }

    /** Temporarily disables the rate limiter feature flag. */
    public static void setupRateLimitingFeatureFlag(ITestDevice device) throws Exception {
        // Store default value of the flag on device for teardown.
        sRateLimiterFlagDefaultValue =
                DeviceUtils.getDeviceConfigFeature(
                        device, NAMESPACE_HEALTH_FITNESS, ENABLE_RATE_LIMITER_FLAG);

        DeviceUtils.putDeviceConfigFeature(
                device, NAMESPACE_HEALTH_FITNESS, ENABLE_RATE_LIMITER_FLAG, "false");
    }

    /** Restores the rate limiter feature flag. */
    public static void restoreRateLimitingFeatureFlag(ITestDevice device) throws Exception {
        if (sRateLimiterFlagDefaultValue == null || sRateLimiterFlagDefaultValue.equals("null")) {
            DeviceUtils.deleteDeviceConfigFeature(
                    device, NAMESPACE_HEALTH_FITNESS, ENABLE_RATE_LIMITER_FLAG);
        } else {
            DeviceUtils.putDeviceConfigFeature(
                    device,
                    NAMESPACE_HEALTH_FITNESS,
                    ENABLE_RATE_LIMITER_FLAG,
                    sRateLimiterFlagDefaultValue);
        }
    }
}
