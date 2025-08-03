/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.car.cts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.PollingCheck;
import com.android.internal.os.StatsdConfigProto.StatsdConfig;
import com.android.os.AtomsProto.Atom;
import com.android.os.AtomsProto.CarWatchdogIoOveruseStats;
import com.android.os.AtomsProto.CarWatchdogIoOveruseStatsReported;
import com.android.os.AtomsProto.CarWatchdogKillStatsReported;
import com.android.os.StatsLog.EventMetricData;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CarWatchdogHostTest extends CarHostJUnit4TestCase {

    /**
     * CarWatchdog device-side test package.
     */
    protected static final String WATCHDOG_TEST_PKG = "android.car.cts.watchdog.test";

    /**
     * CarWatchdog device-side test class.
     */
    protected static final String WATCHDOG_TEST_CLASS =
            "android.car.cts.app.watchdog.CarWatchdogDeviceAppTest";

    /**
     * CarWatchdog app package.
     */
    protected static final String WATCHDOG_APP_PKG = "android.car.cts.watchdog.sharedapp";

    /**
     * Second CarWatchdog app package.
     */
    protected static final String WATCHDOG_APP_PKG_2 = "android.car.cts.watchdog.second.sharedapp";

    /**
     * CarWatchdog app shared user id.
     */
    protected static final String WATCHDOG_APP_SHARED_USER_ID =
            "shared:android.car.cts.uid.watchdog.sharedapp";

    /**
     * The class name of the main activity in the APK.
     */
    private static final String ACTIVITY_CLASS = APP_PKG + ".CarWatchdogTestActivity";

    /**
     * The command to start a custom performance collection with CarWatchdog.
     */
    private static final String START_CUSTOM_PERF_COLLECTION_CMD =
            "dumpsys android.automotive.watchdog.ICarWatchdog/default --start_perf --max_duration"
                    + " 600 --interval 1";

    /**
     * The command to stop a custom performance collection in CarWatchdog.
     */
    private static final String STOP_CUSTOM_PERF_COLLECTION_CMD =
            "dumpsys android.automotive.watchdog.ICarWatchdog/default --stop_perf";

    /**
     * The command to reset I/O overuse counters in the adb shell, which clears any previous
     * stats saved by watchdog.
     */
    private static final String RESET_RESOURCE_OVERUSE_CMD = String.format(
            "dumpsys android.automotive.watchdog.ICarWatchdog/default "
                    + "--reset_resource_overuse_stats %s,%s,%s",
                    APP_PKG, WATCHDOG_APP_SHARED_USER_ID, WATCHDOG_TEST_PKG);

    /**
     * The command to kill a package due to resource overuse.
     */
    private static final String RESOURCE_OVERUSE_KILL_CMD =
            String.format("cmd car_service watchdog-resource-overuse-kill %s", WATCHDOG_TEST_PKG);

    /**
     * The command to get I/O overuse foreground bytes threshold in the adb shell.
     */
    private static final String GET_IO_OVERUSE_FOREGROUNG_BYTES_CMD =
            "cmd car_service watchdog-io-get-3p-foreground-bytes";

    /**
     * The command to set I/O overuse foreground bytes threshold in the adb shell.
     */
    private static final String SET_IO_OVERUSE_FOREGROUNG_BYTES_CMD =
            "cmd car_service watchdog-io-set-3p-foreground-bytes";

    private static final String DEFINE_ENABLE_DISPLAY_POWER_POLICY_CMD =
            "cmd car_service define-power-policy cts_car_watchdog_enable_display "
                    + "--enable DISPLAY";

    private static final String DEFINE_DISABLE_DISPLAY_POWER_POLICY_CMD =
            "cmd car_service define-power-policy cts_car_watchdog_disable_display "
                    + "--disable DISPLAY";

    private static final String APPLY_ENABLE_DISPLAY_POWER_POLICY_CMD =
            "cmd car_service apply-power-policy cts_car_watchdog_enable_display";

    private static final String APPLY_DISABLE_DISPLAY_POWER_POLICY_CMD =
            "cmd car_service apply-power-policy cts_car_watchdog_disable_display";

    private static final String REBOOT_CMD = "cmd car_service power-off --skip-garagemode --reboot";
    public static final String PRIORITIZE_APP_PERFORMANCE_TEXT =
            "'Prioritize app performance' app settings is used to determine whether or not app "
                    + "performance should be prioritized over system stability or long-term "
                    + "hardware stability.";
    private static final String START_CUSTOM_COLLECTION_SUCCESS_MSG =
            "Successfully started custom perf collection";
    private static final String KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE =
            "android.car.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE";

    private static final long BROADCAST_DELAY_MS = TimeUnit.SECONDS.toMillis(10);
    private static final long FIFTY_MEGABYTES = 1024 * 1024 * 50;
    private static final long TWO_HUNDRED_MEGABYTES = 1024 * 1024 * 200;

    private static final int RECURRING_OVERUSE_COUNT = 3;

    private static final Pattern DUMP_PATTERN = Pattern.compile(
            "CarWatchdogTestActivity:\\s(.+)");

    private static final Pattern FOREGROUND_BYTES_PATTERN = Pattern.compile(
            "foregroundModeBytes = (\\d+)");

    private static final int BUILD_VERSION_CODE_TIRAMISU = 33;

    // System event performance data collections are extended for at least 30 seconds after
    // receiving the corresponding system event completion notification. During these periods
    // (on <= Android T releases), a custom collection cannot be started. Thus, retry starting
    // custom collection for at least twice this duration.
    private static final long START_CUSTOM_COLLECTION_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(60);
    private static final long DEVICE_RESPONSE_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(2);
    private static final long WATCHDOG_ACTION_TIMEOUT_MS = 30_000;

    /* Used in order to pass instrumentation arguments to the device-side test */
    private DeviceTestRunOptions mDeviceTestRunOptions;
    private int mCurrentUser;
    private boolean mDidModifyDateTime;
    private long mOriginalForegroundBytes;

    @Before
    public void dateSetUp() throws Exception {
        checkAndSetDate();
    }

    @After
    public void dateReset() throws Exception {
        checkAndResetDate();
    }

    @Before
    public void setUp() throws Exception {
        mDeviceTestRunOptions = new DeviceTestRunOptions(WATCHDOG_TEST_PKG)
            .setTestClassName(WATCHDOG_TEST_CLASS)
            .setDevice(getDevice());
        mCurrentUser = getCurrentUserId();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        executeCommand(DEFINE_ENABLE_DISPLAY_POWER_POLICY_CMD);
        executeCommand(DEFINE_DISABLE_DISPLAY_POWER_POLICY_CMD);
        mOriginalForegroundBytes = parseForegroundBytesFromMessage(executeCommand(
                GET_IO_OVERUSE_FOREGROUNG_BYTES_CMD));
        executeCommand("%s %d", SET_IO_OVERUSE_FOREGROUNG_BYTES_CMD, TWO_HUNDRED_MEGABYTES);
        executeCommand("logcat -c");
        startCustomCollection();
        executeCommand(RESET_RESOURCE_OVERUSE_CMD);
    }

    @After
    public void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        executeCommand(APPLY_ENABLE_DISPLAY_POWER_POLICY_CMD);
        // Enable the CTS packages by running the reset resource overuse command.
        executeCommand(RESET_RESOURCE_OVERUSE_CMD);
        executeCommand(STOP_CUSTOM_PERF_COLLECTION_CMD);
        executeCommand("%s %d", SET_IO_OVERUSE_FOREGROUNG_BYTES_CMD, mOriginalForegroundBytes);
    }

    @Test
    public void testKillableSettingPersistedOnDevice() throws Exception {
        try {
            assertWithMessage("%s Must toggle on 'Prioritize app performance' app settings.",
                    PRIORITIZE_APP_PERFORMANCE_TEXT)
                    .that(runDeviceTest("testSetPackageKillableStateAsNo")).isTrue();

            rebootDeviceAndWait();

            assertWithMessage("%s 'Prioritize app performance' app settings should be toggled on "
                            + "after reboot.", PRIORITIZE_APP_PERFORMANCE_TEXT)
                    .that(runDeviceTest("testVerifyPackageKillableStateAsNo")).isTrue();
        } finally {
            runDeviceTest("testSetPackageKillableStateAsYes");
        }
    }

    @Test
    public void testResourceOveruseConfigurationPersistedOnDevice() throws Exception {
        try {
            assertWithMessage("Must write initial resource overuse configurations to disk")
                    .that(runDeviceTest("testWriteResourceOveruseConfigurationsToDisk")).isTrue();

            assertWithMessage("Must set the test resource overuse configurations")
                    .that(runDeviceTest("testSetResourceOveruseConfigurations")).isTrue();

            rebootDeviceAndWait();

            assertWithMessage("Must persist resource overuse configurations across system reboot")
                    .that(runDeviceTest("testVerifyResourceOveruseConfigurationsPersisted"))
                    .isTrue();
        } finally {
            runDeviceTest("testResetOriginalResourceOveruseConfigurations");
        }
    }

    @Test
    public void testResourceOveruseStatsPersistedOnDevice() throws Exception {
        try {
            assertWithMessage("Must return valid resource overuse stats for initial write")
                    .that(runDeviceTest("testVerifyInitialResourceOveruseStats")).isTrue();

            rebootDeviceAndWait();
            startCustomCollection();

            assertWithMessage("Must return aggregated resource overuse stats for writes made across"
                    + " system reboot")
                    .that(runDeviceTest("testVerifyResourceOveruseStatsAfterReboot")).isTrue();
        } finally {
            runDeviceTest("testDeleteTestFile");
            executeCommand(STOP_CUSTOM_PERF_COLLECTION_CMD);
        }
    }

    @Test
    public void testVerifyPackagesDisabledOnResourceOveruseSettingsString() throws Exception {
        assertWithMessage("%s settings default value", KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE)
                .that(readPackagesDisabledOnResourceOveruseSettings())
                .doesNotContain(WATCHDOG_TEST_PKG);

        executeCommand(RESOURCE_OVERUSE_KILL_CMD);

        assertWithMessage("%s settings value after killing test package due to resource overuse",
                KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE)
                .that(readPackagesDisabledOnResourceOveruseSettings()).contains(WATCHDOG_TEST_PKG);

        String result = executeCommand("pm enable --user %d %s", mCurrentUser, WATCHDOG_TEST_PKG);
        assertWithMessage("Package enable command result").that(result).contains("enabled");

        // CarService updates KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE settings value on receiving
        // package enabled broadcast. This broadcast may take few seconds to reach CarService.
        PollingCheck.check(String.format("%s settings value after enabling test package",
                        KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE),
                BROADCAST_DELAY_MS, () -> {
                    return !readPackagesDisabledOnResourceOveruseSettings()
                            .contains(WATCHDOG_TEST_PKG);
                });
    }

    @Test
    public void testIoOveruseKillAfterDisplayTurnOff() throws Exception {
        uploadStatsdConfig(APP_PKG);

        for (int i = 0; i < RECURRING_OVERUSE_COUNT; ++i) {
            overuseDiskIo(APP_PKG, getTestRunningUserId());
            verifyAtomIoOveruseStatsReported(APP_PKG, getTestRunningUserId(),
                    /* overuseTimes= */ i + 1);
            ReportUtils.clearReports(getDevice());
        }

        executeCommand(APPLY_DISABLE_DISPLAY_POWER_POLICY_CMD);

        verifyTestAppsKilled(APP_PKG);
        verifyAtomKillStatsReported(APP_PKG, getTestRunningUserId());
    }

    @Test
    public void testIoOveruseKillAfterDisplayTurnOffWithSharedUserIdApps() throws Exception {
        // Stats collection is based on uid. Packages with shared uids can be used interchangeably.
        uploadStatsdConfig(WATCHDOG_APP_PKG);

        for (int i = 0; i < RECURRING_OVERUSE_COUNT; i++) {
            overuseDiskIo(i % 2 == 0 ? WATCHDOG_APP_PKG : WATCHDOG_APP_PKG_2,
                    getTestRunningUserId());
            verifyAtomIoOveruseStatsReported(i % 2 == 0 ? WATCHDOG_APP_PKG_2 : WATCHDOG_APP_PKG,
                    getTestRunningUserId(), /* overuseTimes= */ i + 1);
            ReportUtils.clearReports(getDevice());
        }

        executeCommand(APPLY_DISABLE_DISPLAY_POWER_POLICY_CMD);

        verifyTestAppsKilled(WATCHDOG_APP_PKG, WATCHDOG_APP_PKG_2);
        verifyAtomKillStatsReported(WATCHDOG_APP_PKG, getTestRunningUserId());
    }

    private void uploadStatsdConfig(String packageName) throws Exception {
        StatsdConfig.Builder config = ConfigUtils.createConfigBuilder("AID_SYSTEM");
        ConfigUtils.addEventMetricForUidAtom(config,
                Atom.CAR_WATCHDOG_IO_OVERUSE_STATS_REPORTED_FIELD_NUMBER,
                /* uidInAttributionChain= */ false, packageName);
        ConfigUtils.addEventMetricForUidAtom(config,
                Atom.CAR_WATCHDOG_KILL_STATS_REPORTED_FIELD_NUMBER,
                /* uidInAttributionChain= */ false, packageName);
        ConfigUtils.uploadConfig(getDevice(), config);
    }

    private void verifyAtomIoOveruseStatsReported(String packageName, int userId, int overuseTimes)
            throws Exception {
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertWithMessage("Reported I/O overuse event metrics data").that(data).hasSize(1);

        CarWatchdogIoOveruseStatsReported atom =
                data.get(0).getAtom().getCarWatchdogIoOveruseStatsReported();

        int appUid = DeviceUtils.getAppUidForUser(getDevice(), packageName, userId);
        assertWithMessage("UID in atom from " + overuseTimes + " overuse").that(atom.getUid())
                .isEqualTo(appUid);
        assertWithMessage("Atom has I/O overuse stats from " + overuseTimes + " overuse")
                .that(atom.hasIoOveruseStats()).isTrue();
        verifyAtomIoOveruseStats(atom.getIoOveruseStats(), overuseTimes * TWO_HUNDRED_MEGABYTES,
                "I/O overuse stats atom from " + overuseTimes + " overuse");
    }

    private void verifyAtomKillStatsReported(String packageName, int userId)
            throws Exception {
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertWithMessage("Reported kill event metrics data").that(data).isNotEmpty();

        assertWithMessage("CarWatchdogKillStatsReported atom").that(data.get(
                0).getAtom().hasCarWatchdogKillStatsReported()).isTrue();
        CarWatchdogKillStatsReported atom =
                data.get(0).getAtom().getCarWatchdogKillStatsReported();

        int appUid = DeviceUtils.getAppUidForUser(getDevice(), packageName, userId);
        assertWithMessage("UID in kill stats").that(atom.getUid()).isEqualTo(appUid);
        assertWithMessage("Kill reason from kill stats").that(atom.getKillReason())
                .isEqualTo(CarWatchdogKillStatsReported.KillReason.KILLED_ON_IO_OVERUSE);
        assertWithMessage("System state from kill stats").that(atom.getSystemState())
                .isEqualTo(CarWatchdogKillStatsReported.SystemState.USER_NO_INTERACTION_MODE);
        assertWithMessage("Atom has I/O overuse stats from overuse kill")
                .that(atom.hasIoOveruseStats()).isTrue();
        verifyAtomIoOveruseStats(atom.getIoOveruseStats(),
                RECURRING_OVERUSE_COUNT * TWO_HUNDRED_MEGABYTES,
                "I/O overuse stats atom from overuse kill");
    }

    private void verifyAtomIoOveruseStats(CarWatchdogIoOveruseStats ioOveruseStats,
            long foregroundWrittenBytes, String statsType) {
        assertWithMessage(statsType + " has period").that(ioOveruseStats.hasPeriod()).isTrue();
        assertWithMessage("Period in " + statsType).that(ioOveruseStats.getPeriod())
                .isEqualTo(CarWatchdogIoOveruseStats.Period.DAILY);
        assertWithMessage(statsType + " has threshold").that(ioOveruseStats.hasThreshold())
                .isTrue();
        assertWithMessage("Foreground threshold bytes in " + statsType)
                .that(ioOveruseStats.getThreshold().getForegroundBytes())
                .isEqualTo(TWO_HUNDRED_MEGABYTES);
        assertWithMessage(statsType + " has written bytes").that(ioOveruseStats.hasWrittenBytes())
                .isTrue();
        // Watchdog daemon's polling/syncing interval and the disk I/O writes performed by the
        // device side app are asynchronous. So, the actual number of bytes written by the app might
        // be greater than the expected written bytes. Thus verify that the reported written bytes
        // are in the range of 50MiB.
        assertWithMessage("Foreground written bytes in " + statsType)
                .that(ioOveruseStats.getWrittenBytes().getForegroundBytes())
                .isAtLeast(foregroundWrittenBytes);
        assertWithMessage("Foreground written bytes in " + statsType)
                .that(ioOveruseStats.getWrittenBytes().getForegroundBytes())
                .isAtMost(foregroundWrittenBytes + FIFTY_MEGABYTES);
    }

    private void overuseDiskIo(String packageName, int userId) throws Exception {
        startMainActivity(packageName, userId);

        long remainingBytes = readForegroundBytesFromActivityDump(packageName);

        sendBytesToKillApp(remainingBytes, packageName, userId);

        remainingBytes = readForegroundBytesFromActivityDump(packageName);

        assertWithMessage("Application " + packageName + "'s remaining write bytes")
                .that(remainingBytes).isEqualTo(0);
    }

    private long readForegroundBytesFromActivityDump(String packageName) throws Exception {
        AtomicReference<String> notification = new AtomicReference<>();
        PollingCheck.check("No notification received in the activity dump",
                WATCHDOG_ACTION_TIMEOUT_MS,
                () -> {
                    String dump = fetchActivityDumpsys(packageName);
                    if (dump.startsWith("INFO") && dump.contains("--Notification--")) {
                        notification.set(dump);
                        return true;
                    }
                    return false;
                });

        return parseForegroundBytesFromMessage(notification.get());
    }

    private long parseForegroundBytesFromMessage(String message) throws IllegalArgumentException {
        Matcher m = FOREGROUND_BYTES_PATTERN.matcher(message);
        if (m.find()) {
            return Long.parseLong(m.group(1));
        }
        throw new IllegalArgumentException("Invalid message format: " + message);
    }

    private void verifyTestAppsKilled(String... packageNames) throws Exception {
        ArrayList<String> packages = new ArrayList<>(List.of(packageNames));
        try {
            PollingCheck.check("Failed to kill applications", WATCHDOG_ACTION_TIMEOUT_MS, () -> {
                for (int i = packages.size() - 1; i >= 0; i--) {
                    // Check activity dump for errors. Throws exception on error.
                    String packageName = packages.get(i);
                    fetchActivityDumpsys(packageName);
                    if (!isPackageRunning(packageName)) {
                        packages.remove(i);
                    }
                }
                return packages.isEmpty();
            });
        } catch (AssertionError e) {
            assertWithMessage("Failed to kill applications: %s", packages).fail();
        }
    }

    private String fetchActivityDumpsys(String packageName) throws Exception {
        String dump = executeCommand("dumpsys activity %s/%s", packageName, ACTIVITY_CLASS);
        Matcher m = DUMP_PATTERN.matcher(dump);
        if (!m.find()) {
            return "";
        }
        String message = Objects.requireNonNull(m.group(1)).trim();
        if (message.startsWith("ERROR")) {
            throw new Exception(message);
        }
        return message;
    }

    private void startMainActivity(String packageName, int userId) throws Exception {
        String result = executeCommand("pm clear --user %d %s", userId, packageName);
        assertWithMessage("pm clear").that(result.trim()).isEqualTo("Success");

        executeCommand("am start --user %d -W -a android.intent.action.MAIN -n %s/%s",
                userId, packageName, ACTIVITY_CLASS);

        assertWithMessage("Is %s running?", packageName).that(isPackageRunning(packageName))
                .isTrue();
    }

    private void sendBytesToKillApp(long remainingBytes, String appPkg, int userId)
            throws Exception {
        executeCommand(
                "am start --user %d -W -a android.intent.action.MAIN -n %s/%s"
                        + " --el bytes_to_kill %d",
                userId, appPkg, ACTIVITY_CLASS, remainingBytes);
    }

    private void checkAndSetDate() throws Exception {
        // Get date in ISO-8601 format
        LocalDateTime now = LocalDateTime.parse(executeCommand("date +%%FT%%T").trim());
        if (now.getHour() < 23) {
            return;
        }
        executeCommand("date %s", now.minusHours(1));
        CLog.d("DateTime changed from %s to %s", now, now.minusHours(1));
        mDidModifyDateTime = true;
    }

    private void checkAndResetDate() throws Exception {
        if (!mDidModifyDateTime) {
            return;
        }
        LocalDateTime now = LocalDateTime.parse(executeCommand("date +%%FT%%T").trim());
        executeCommand("date %s", now.plusHours(1));
        CLog.d("DateTime changed from %s to %s", now, now.plusHours(1));
    }

    private void startCustomCollection() throws Exception {
        if (ApiLevelUtil.isAfter(getDevice(), BUILD_VERSION_CODE_TIRAMISU)) {
            String result = executeCommand(START_CUSTOM_PERF_COLLECTION_CMD);
            assertWithMessage("Custom collection start message").that(result)
                    .contains(START_CUSTOM_COLLECTION_SUCCESS_MSG);
            return;
        }
        // TODO(b/261869056): Remove the polling check once it is safe to remove.
        PollingCheck.check("Failed to start custom collect performance data collection",
                START_CUSTOM_COLLECTION_TIMEOUT_MS,
                () -> {
                    String result = executeCommand(START_CUSTOM_PERF_COLLECTION_CMD);
                    return result.contains(START_CUSTOM_COLLECTION_SUCCESS_MSG) || result.isEmpty();
                });
    }

    private void rebootDeviceAndWait() throws Exception {
        /* ADB doesn't trigger AAOS specific shutdown procedure on all devices when
         * performing "adb reboot". CarWatchdog listens for shutdown/suspend enter and writes I/O
         * overuse stats and user package settings to DB when state changes. Perform system reboot
         * with |REBOOT_CMD|, which will trigger the system to enter garage mode, force suspend,
         * and reboot.
         *
         * TODO(b/200084065): Use the regular reboot command, once it follows the AAOS shutdown
         *  process.
         */
        executeCommand(REBOOT_CMD);
        /* Check if device shows as unavailable (as expected after reboot). */
        assertThat(getDevice().waitForDeviceNotAvailable(DEVICE_RESPONSE_TIMEOUT_MS)).isTrue();
        getDevice().waitForDeviceAvailable(DEVICE_RESPONSE_TIMEOUT_MS);
    }

    private boolean runDeviceTest(String testMethodName) throws DeviceNotAvailableException {
        mDeviceTestRunOptions.setTestMethodName(testMethodName);
        return runDeviceTests(mDeviceTestRunOptions);
    }

    private String readPackagesDisabledOnResourceOveruseSettings() throws Exception {
        String value = executeCommand("settings get --user %d secure %s", mCurrentUser,
                        KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE).trim();
        return value.equals("null") ? "" : value;
    }
}
