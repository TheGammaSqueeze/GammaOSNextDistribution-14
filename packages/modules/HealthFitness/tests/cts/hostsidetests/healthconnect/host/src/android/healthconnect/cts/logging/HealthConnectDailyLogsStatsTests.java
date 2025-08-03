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

package android.healthconnect.cts.logging;

import static android.healthconnect.cts.HostSideTestUtil.isHardwareSupported;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.healthconnect.cts.HostSideTestUtil;

import com.android.os.StatsLog;
import com.android.os.healthfitness.api.ApiExtensionAtoms;
import com.android.os.healthfitness.api.HealthConnectStorageStats;
import com.android.os.healthfitness.api.HealthConnectUsageStats;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.RunUtil;

import com.google.protobuf.ExtensionRegistry;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

public class HealthConnectDailyLogsStatsTests extends DeviceTestCase implements IBuildReceiver {

    public static final String TEST_APP_PKG_NAME = "android.healthconnect.cts.testhelper";
    private static final int NUMBER_OF_RETRIES = 10;
    private static final String DAILY_LOG_TESTS_ACTIVITY = ".DailyLogsTests";
    private static final String HEALTH_CONNECT_SERVICE_LOG_TESTS_ACTIVITY =
            ".HealthConnectServiceLogsTests";
    private IBuildInfo mCtsBuild;
    private Instant mTestStartTime;
    private Instant mTestStartTimeOnDevice;

    @Override
    protected void setUp() throws Exception {
        if (!isHardwareSupported(getDevice())) {
            return;
        }
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        assertThat(isHardwareSupported(getDevice())).isTrue();
        // TODO(b/313055175): Do not disable rate limiting once b/300238889 is resolved.
        HostSideTestUtil.setupRateLimitingFeatureFlag(getDevice());
        mTestStartTime = Instant.now();
        mTestStartTimeOnDevice = Instant.ofEpochMilli(getDevice().getDeviceDate());
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        clearData();
        // Doing this to avoid any access log entries which might make the test flaky.
        increaseDeviceTimeByDays(/* numberOfDays= */ 31);
    }

    @Override
    protected void tearDown() throws Exception {
        if (!isHardwareSupported(getDevice())) {
            return;
        }
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        clearData();
        // TODO(b/313055175): Do not disable rate limiting once b/300238889 is resolved.
        HostSideTestUtil.restoreRateLimitingFeatureFlag(getDevice());
        resetTime();
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    public void testConnectedApps() throws Exception {
        if (!isHardwareSupported(getDevice())) {
            return;
        }
        ConfigUtils.uploadConfigForPushedAtoms(
                getDevice(),
                TEST_APP_PKG_NAME,
                new int[] {ApiExtensionAtoms.HEALTH_CONNECT_USAGE_STATS_FIELD_NUMBER});

        List<StatsLog.EventMetricData> data =
                getEventMetricDataList(/* testName= */ null, NUMBER_OF_RETRIES);
        assertThat(data.size()).isAtLeast(1);
        HealthConnectUsageStats atom =
                data.get(data.size() - 1)
                        .getAtom()
                        .getExtension(ApiExtensionAtoms.healthConnectUsageStats);

        assertThat(atom.getConnectedAppsCount()).isGreaterThan(0);
        assertThat(atom.getAvailableAppsCount()).isGreaterThan(0);
    }

    public void testDatabaseStats() throws Exception {
        if (!isHardwareSupported(getDevice())) {
            return;
        }
        ConfigUtils.uploadConfigForPushedAtoms(
                getDevice(),
                TEST_APP_PKG_NAME,
                new int[] {ApiExtensionAtoms.HEALTH_CONNECT_STORAGE_STATS_FIELD_NUMBER});

        List<StatsLog.EventMetricData> data =
                getEventMetricDataList("testInsertRecordsSucceed", NUMBER_OF_RETRIES);
        assertThat(data.size()).isAtLeast(1);
        HealthConnectStorageStats atom =
                data.get(data.size() - 1)
                        .getAtom()
                        .getExtension(ApiExtensionAtoms.healthConnectStorageStats);

        assertThat(atom.getDatabaseSize()).isGreaterThan(0);
        assertThat(atom.getInstantDataCount()).isEqualTo(1);
        assertThat(atom.getIntervalDataCount()).isEqualTo(1);
        assertThat(atom.getSeriesDataCount()).isEqualTo(1);
        assertThat(atom.getChangelogCount()).isGreaterThan(2);
    }

    public void testIsUserActive_insertRecord_userMonthlyActiveNextDay() throws Exception {
        if (!isHardwareSupported(getDevice())) {
            return;
        }

        ConfigUtils.uploadConfigForPushedAtoms(
                getDevice(),
                TEST_APP_PKG_NAME,
                new int[] {ApiExtensionAtoms.HEALTH_CONNECT_USAGE_STATS_FIELD_NUMBER});
        triggerTestInTestApp(
                HEALTH_CONNECT_SERVICE_LOG_TESTS_ACTIVITY, "testHealthConnectInsertRecords");
        increaseDeviceTimeByDays(/* numberOfDays= */ 1);

        List<StatsLog.EventMetricData> data =
                getEventMetricDataList(/* testName= */ null, NUMBER_OF_RETRIES);
        assertThat(data.size()).isAtLeast(1);
        HealthConnectUsageStats atom =
                data.get(data.size() - 1)
                        .getAtom()
                        .getExtension(ApiExtensionAtoms.healthConnectUsageStats);

        assertThat(atom.getIsMonthlyActiveUser()).isTrue();
    }

    public void testIsUserActive_insertRecord_userMonthlyActiveBetween7To30days() throws Exception {
        if (!isHardwareSupported(getDevice())) {
            return;
        }

        ConfigUtils.uploadConfigForPushedAtoms(
                getDevice(),
                TEST_APP_PKG_NAME,
                new int[] {ApiExtensionAtoms.HEALTH_CONNECT_USAGE_STATS_FIELD_NUMBER});
        triggerTestInTestApp(
                HEALTH_CONNECT_SERVICE_LOG_TESTS_ACTIVITY, "testHealthConnectInsertRecords");
        increaseDeviceTimeByDays(/* numberOfDays= */ 10);

        List<StatsLog.EventMetricData> data =
                getEventMetricDataList(/* testName= */ null, NUMBER_OF_RETRIES);
        assertThat(data.size()).isAtLeast(1);
        HealthConnectUsageStats atom =
                data.get(data.size() - 1)
                        .getAtom()
                        .getExtension(ApiExtensionAtoms.healthConnectUsageStats);

        assertThat(atom.getIsMonthlyActiveUser()).isTrue();
    }

    public void testIsUserActive_insertRecord_userNotMonthlyActiveAfter30Days() throws Exception {
        if (!isHardwareSupported(getDevice())) {
            return;
        }

        ConfigUtils.uploadConfigForPushedAtoms(
                getDevice(),
                TEST_APP_PKG_NAME,
                new int[] {ApiExtensionAtoms.HEALTH_CONNECT_USAGE_STATS_FIELD_NUMBER});
        triggerTestInTestApp(
                HEALTH_CONNECT_SERVICE_LOG_TESTS_ACTIVITY, "testHealthConnectInsertRecords");
        increaseDeviceTimeByDays(/* numberOfDays= */ 35);

        List<StatsLog.EventMetricData> data =
                getEventMetricDataList(/* testName= */ null, NUMBER_OF_RETRIES);
        assertThat(data.size()).isAtLeast(1);
        HealthConnectUsageStats atom =
                data.get(data.size() - 1)
                        .getAtom()
                        .getExtension(ApiExtensionAtoms.healthConnectUsageStats);

        assertThat(atom.getIsMonthlyActiveUser()).isFalse();
    }

    public void testIsUserActive_readRecord_userMonthlyActiveNextDay() throws Exception {
        if (!isHardwareSupported(getDevice())) {
            return;
        }

        ConfigUtils.uploadConfigForPushedAtoms(
                getDevice(),
                TEST_APP_PKG_NAME,
                new int[] {ApiExtensionAtoms.HEALTH_CONNECT_USAGE_STATS_FIELD_NUMBER});
        triggerTestInTestApp(
                HEALTH_CONNECT_SERVICE_LOG_TESTS_ACTIVITY, "testHealthConnectReadRecords");
        increaseDeviceTimeByDays(/* numberOfDays= */ 1);

        List<StatsLog.EventMetricData> data =
                getEventMetricDataList(/* testName= */ null, NUMBER_OF_RETRIES);
        assertThat(data.size()).isAtLeast(1);
        HealthConnectUsageStats atom =
                data.get(data.size() - 1)
                        .getAtom()
                        .getExtension(ApiExtensionAtoms.healthConnectUsageStats);

        assertThat(atom.getIsMonthlyActiveUser()).isTrue();
    }

    public void testIsUserActive_readRecord_userMonthlyActiveBetween7To30days() throws Exception {
        if (!isHardwareSupported(getDevice())) {
            return;
        }

        ConfigUtils.uploadConfigForPushedAtoms(
                getDevice(),
                TEST_APP_PKG_NAME,
                new int[] {ApiExtensionAtoms.HEALTH_CONNECT_USAGE_STATS_FIELD_NUMBER});
        triggerTestInTestApp(
                HEALTH_CONNECT_SERVICE_LOG_TESTS_ACTIVITY, "testHealthConnectReadRecords");
        increaseDeviceTimeByDays(/* numberOfDays= */ 10);

        List<StatsLog.EventMetricData> data =
                getEventMetricDataList(/* testName= */ null, NUMBER_OF_RETRIES);
        assertThat(data.size()).isAtLeast(1);
        HealthConnectUsageStats atom =
                data.get(data.size() - 1)
                        .getAtom()
                        .getExtension(ApiExtensionAtoms.healthConnectUsageStats);

        assertThat(atom.getIsMonthlyActiveUser()).isTrue();
    }

    public void testIsUserActive_readRecord_userNotMonthlyActiveAfter30Days() throws Exception {
        if (!isHardwareSupported(getDevice())) {
            return;
        }

        ConfigUtils.uploadConfigForPushedAtoms(
                getDevice(),
                TEST_APP_PKG_NAME,
                new int[] {ApiExtensionAtoms.HEALTH_CONNECT_USAGE_STATS_FIELD_NUMBER});
        triggerTestInTestApp(
                HEALTH_CONNECT_SERVICE_LOG_TESTS_ACTIVITY, "testHealthConnectReadRecords");
        increaseDeviceTimeByDays(/* numberOfDays= */ 35);

        List<StatsLog.EventMetricData> data =
                getEventMetricDataList(/* testName= */ null, NUMBER_OF_RETRIES);
        assertThat(data.size()).isAtLeast(1);
        HealthConnectUsageStats atom =
                data.get(data.size() - 1)
                        .getAtom()
                        .getExtension(ApiExtensionAtoms.healthConnectUsageStats);

        assertThat(atom.getIsMonthlyActiveUser()).isFalse();
    }

    private List<StatsLog.EventMetricData> getEventMetricDataList(String testName, int retryCount)
            throws Exception {
        if (retryCount == 0) {
            throw new RuntimeException("Could not collect metrics.");
        }

        ExtensionRegistry extensionRegistry =
                triggerTestInTestApp(DAILY_LOG_TESTS_ACTIVITY, testName);
        triggerDailyJob(); // This will run the job which calls DailyLogger to log some metrics.
        List<StatsLog.EventMetricData> data =
                ReportUtils.getEventMetricDataList(getDevice(), extensionRegistry);

        if (data.size() == 0) {
            return getEventMetricDataList(testName, retryCount - 1);
        }
        return data;
    }

    private void clearData() throws Exception {
        triggerTestInTestApp(DAILY_LOG_TESTS_ACTIVITY, "deleteAllRecordsAddedForTest");
        // Next two lines will delete newly added Access Logs as all access logs over 7 days are
        // deleted by the AutoDeleteService which is run by the daily job.
        increaseDeviceTimeByDays(10);
    }

    private ExtensionRegistry triggerTestInTestApp(String className, String testName)
            throws Exception {

        if (testName != null) {
            DeviceUtils.runDeviceTests(getDevice(), TEST_APP_PKG_NAME, className, testName);
        }

        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        ApiExtensionAtoms.registerAllExtensions(registry);
        return registry;
    }

    private void triggerDailyJob() throws Exception {

        // There are multiple instances of HealthConnectDailyService. This command finds the one
        // that needs to be triggered for this test using the job param 'hc_daily_job'.
        String output =
                getDevice()
                        .executeShellCommand(
                                "dumpsys jobscheduler | grep -m1 -A0 -B10 \"hc_daily_job\"");
        int indexOfStart = output.indexOf("/") + 1;
        String jobId = output.substring(indexOfStart, output.indexOf(":", indexOfStart));
        String jobExecutionCommand =
                "cmd jobscheduler run --namespace HEALTH_CONNECT_DAILY_JOB -f android " + jobId;

        executeLoggingJob(jobExecutionCommand, NUMBER_OF_RETRIES);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    private void executeLoggingJob(String jobExecutionCommand, int retry)
            throws DeviceNotAvailableException, RuntimeException {
        if (retry == 0) {
            throw new RuntimeException("Could not execute job");
        }
        if (getDevice().executeShellV2Command(jobExecutionCommand).getStatus()
                != CommandStatus.SUCCESS) {
            executeLoggingJob(jobExecutionCommand, retry - 1);
        }
    }

    private void increaseDeviceTimeByDays(int numberOfDays) throws Exception {
        // This is to ensure that the essential daily functions run before increasing device time.
        triggerDailyJob();

        Instant deviceDate = Instant.ofEpochMilli(getDevice().getDeviceDate());
        getDevice().setDate(Date.from(deviceDate.plus(numberOfDays, ChronoUnit.DAYS)));
        getDevice()
                .executeShellCommand(
                        "cmd time_detector set_time_state_for_tests --unix_epoch_time "
                                + deviceDate.plus(numberOfDays, ChronoUnit.DAYS).toEpochMilli()
                                + " --user_should_confirm_time false --elapsed_realtime 0");

        getDevice().executeShellCommand("am broadcast -a android.intent.action.TIME_SET");

        // This is to ensure that the essential daily functions run after increasing device time.
        triggerDailyJob();
    }

    private void resetTime() throws DeviceNotAvailableException {
        long timeDiff = Duration.between(mTestStartTime, Instant.now()).toMillis();

        getDevice()
                .executeShellCommand(
                        "cmd time_detector set_time_state_for_tests --unix_epoch_time "
                                + mTestStartTimeOnDevice.plusMillis(timeDiff).toEpochMilli()
                                + " --user_should_confirm_time false --elapsed_realtime 0");
        getDevice().executeShellCommand("am broadcast -a android.intent.action.TIME_SET");
    }
}
