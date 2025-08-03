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
package com.android.performance.tests;

import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.loganalysis.item.DmesgActionInfoItem;
import com.android.loganalysis.item.DmesgServiceInfoItem;
import com.android.loganalysis.item.DmesgStageInfoItem;
import com.android.loganalysis.item.GenericTimingItem;
import com.android.loganalysis.item.SystemServicesTimingItem;
import com.android.loganalysis.parser.DmesgParser;
import com.android.loganalysis.parser.TimingsLogParser;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.LogcatReceiver;
import com.android.tradefed.device.metric.IMetricCollector;
import com.android.tradefed.device.metric.IMetricCollectorReceiver;
import com.android.tradefed.error.IHarnessException;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.error.ErrorIdentifier;
import com.android.tradefed.result.proto.TestRecordProto.FailureStatus;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.InstalledInstrumentationsTest;
import com.android.tradefed.testtype.IInvocationContextReceiver;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import com.google.common.annotations.VisibleForTesting;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Performs successive reboots and computes various boottime metrics */
public class BootTimeTest extends InstalledInstrumentationsTest
        implements IRemoteTest,
                IDeviceTest,
                IBuildReceiver,
                IConfigurationReceiver,
                IInvocationContextReceiver,
                IMetricCollectorReceiver {
    protected static final String ONLINE = "online";
    protected static final String BOOTTIME_TEST = "BootTimeTest";
    protected static final long INVALID_TIME_DURATION = -1;

    private static final String SUCCESSIVE_BOOT_TEST = "SuccessiveBootTest";
    private static final String SUCCESSIVE_BOOT_UNLOCK_TEST = "SuccessiveBootUnlockTest";
    private static final String SUCCESSIVE_ONLINE = "successive-online";
    private static final String SUCCESSIVE_BOOT = "successive-boot";
    private static final String LOGCAT_CMD = "logcat *:V";
    private static final String LOGCAT_CMD_ALL = "logcat -b all *:V";
    private static final String LOGCAT_CMD_STATISTICS = "logcat --statistics --pid %d";
    private static final String LOGCAT_CMD_STATISTICS_ALL = "logcat --statistics";
    private static final long LOGCAT_SIZE = 80 * 1024 * 1024;
    private static final String BOOT_COMPLETED_PROP = "getprop sys.boot_completed";
    private static final String BOOT_COMPLETED_VAL = "1";
    private static final String BOOT_TIME_PROP = "ro.boot.boottime";
    private static final String BOOTLOADER_PREFIX = "bootloader-";
    private static final String BOOTLOADER_TIME = "bootloader_time";
    private static final String LOGCAT_FILE = "Successive_reboots_logcat";
    private static final String LOGCAT_UNLOCK_FILE = "Successive_reboots_unlock_logcat";
    private static final String BOOT_COMPLETE_ACTION = "sys.boot_completed=1";
    private static final String RUNNER = "androidx.test.runner.AndroidJUnitRunner";
    private static final String PACKAGE_NAME = "com.android.boothelper";
    private static final String CLASS_NAME = "com.android.boothelper.BootHelperTest";
    private static final String SETUP_PIN_TEST = "setupLockScreenPin";
    private static final String UNLOCK_PIN_TEST = "unlockScreenWithPin";
    private static final String UNLOCK_TIME = "screen_unlocktime";
    private static final String F2FS_SHUTDOWN_COMMAND = "f2fs_io shutdown 4 /data";
    private static final String F2FS_SHUTDOWN_SUCCESS_OUTPUT = "Shutdown /data with level=4";
    private static final int BOOT_COMPLETE_POLL_INTERVAL = 1000;
    private static final int BOOT_COMPLETE_POLL_RETRY_COUNT = 45;
    private static final String ROOT = "root";
    private static final String DMESG_FILE = "/data/local/tmp/dmesglogs.txt";
    private static final String DUMP_DMESG = String.format("dmesg > %s", DMESG_FILE);
    private static final String INIT = "init_";
    private static final String START_TIME = "_START_TIME";
    private static final String DURATION = "_DURATION";
    private static final String END_TIME = "_END_TIME";
    private static final String ACTION = "action_";
    private static final String INIT_STAGE = "init_stage_";
    /** logcat command for selinux, pinnerservice, fatal messages */
    private static final String LOGCAT_COMBINED_CMD =
            "logcat -b all auditd:* PinnerService:* \\*:F";
    private static final String TOTAL_BOOT_TIME = "TOTAL_BOOT_TIME";
    private static final String BOOT_PHASE_1000 = "starting_phase_1000";
    private static final String METRIC_KEY_SEPARATOR = "_";
    private static final String LOGCAT_STATISTICS_SIZE = "logcat_statistics_size_bytes";
    private static final String LOGCAT_STATISTICS_DIFF_SIZE =
            "logcat_statistics_size_bytes_dropped";
    private static final String DMESG_BOOT_COMPLETE_TIME =
            "dmesg_action_sys.boot_completed_first_timestamp";
    private static final String MAKE_DIR = "mkdir %s";
    private static final String FOLDER_NAME_FORMAT = "sample_%s";
    private static final String RANDOM_FILE_CMD = "dd if=/dev/urandom of=%s bs=%d%s count=1";
    private static final String KB_IDENTIFIER = "k";
    private static final String MB_IDENTIFIER = "m";
    private static final String BYTES_TRANSFERRED = "bytes transferred";
    private static final String RM_DIR = "rm -rf %s";
    private static final String RAMDUMP_STATUS = "ramdump -s";
    private static final String BOOTLOADER_PHASE_SW = "SW";
    private static final String STORAGE_TYPE = "ro.boot.hardware.ufs";
    private static final String PERFETTO_TRACE_FILE_CHECK_CMD = "ls -l /data/misc/perfetto-traces";
    private static final String FINAL_PERFETTO_TRACE_LOCATION = "/data/local/tmp/%s.perfetto-trace";
    private static final String PERFETTO_TRACE_MV_CMD =
            "mv /data/misc/perfetto-traces/boottrace.perfetto-trace %s";

    private static final String TIMESTAMP_PID =
            "^(\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.\\d{3})\\s+" + "(\\d+)\\s+(\\d+)\\s+([A-Z])\\s+";
    // 04-05 18:26:52.637 2161 2176 I BootHelperTest: Screen Unlocked
    private static final String ALL_PROCESS_CMD = "ps -A";
    private static final Pattern SCREEN_UNLOCKED =
            Pattern.compile(TIMESTAMP_PID + "(.+?)\\s*: Screen Unlocked$");
    // 04-05 18:26:54.320 1013 1121 I ActivityManager: Displayed
    // com.google.android.apps.nexuslauncher/.NexusLauncherActivity: +648ms
    private static final Pattern DISPLAYED_LAUNCHER =
            Pattern.compile(
                    TIMESTAMP_PID
                            + "(.+?)\\s*: Displayed"
                            + " com.google.android.apps.nexuslauncher/.NexusLauncherActivity:"
                            + "\\s*(.*)$");
    /** Matches the line indicating kernel start. It is starting point of the whole boot process */
    private static final Pattern KERNEL_START_PATTERN = Pattern.compile("Linux version");
    /** Matches the logcat line indicating boot completed */
    private static final Pattern LOGCAT_BOOT_COMPLETED = Pattern.compile("Starting phase 1000");

    private static final Pattern LOGCAT_STATISTICS_HEADER_PATTERN =
            Pattern.compile(
                    "^size\\/num\\s*(\\w+)\\s*(\\w+)\\s*(\\w+)\\s*(\\w+)\\s*(\\w+)*",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern LOGCAT_STATISTICS_TOTAL_PATTERN =
            Pattern.compile(
                    "Total\\s*(\\d+)\\/(\\d+)\\s*(\\d+)\\/(\\d+)\\s*(\\d+)\\/(\\d+)\\s*"
                            + "(\\d+)\\/(\\d+)\\s*(\\d+)\\/(\\d+).*",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern LOGCAT_STATISTICS_NOW_PATTERN =
            Pattern.compile(
                    "Now\\s+(\\d+)\\/(\\d+)\\s*(\\d+)\\/(\\d+)\\s*(\\d+)\\/(\\d+)\\s*"
                            + "(\\d+)\\/(\\d+)\\s*(\\d+)\\/(\\d+).*",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern LOGCAT_STATISTICS_PID_PATTERN =
            Pattern.compile(
                    "Logging for this PID.*\\s+([0-9]+)$",
                    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);

    private static final String METRIC_COUNT = "MetricCount";

    @Option(name = "test-run-name", description = "run name to report to result reporters")
    private String mTestRunName = BOOTTIME_TEST;

    @Option(name = "device-boot-time", description = "Max time in ms to wait for device to boot.")
    protected long mDeviceBootTime = 5 * 60 * 1000;

    @Option(
            name = "first-boot",
            description = "Calculate the boot time metrics after flashing the device")
    private boolean mFirstBoot = true;

    @Option(
            name = "successive-boot-prepare-cmd",
            description =
                    "A list of adb commands to run after first boot to prepare for successive"
                            + " boot tests")
    private List<String> mDeviceSetupCommands = new ArrayList<>();

    @Option(
            name = "test-file-name",
            description =
                    "Name of a apk in expanded test zip to install on device. Can be repeated.",
            importance = Importance.IF_UNSET)
    private List<File> mTestFileNames = new ArrayList<>();

    @Option(name = "successive-boot", description = "Calculate the successive boot delay info")
    private boolean mSuccessiveBoot = false;

    @Option(
            name = "boot-count",
            description =
                    "Number of times to boot the devices to calculate the successive boot delay."
                            + " Second boot after the first boot will be  skipped for correctness.")
    private int mBootCount = 5;

    @Option(
            name = "boot-delay",
            isTimeVal = true,
            description = "Time to wait between the successive boots.")
    private long mBootDelayTime = 2000;

    @Option(
            name = "after-boot-delay",
            isTimeVal = true,
            description = "Time to wait immediately after the successive boots.")
    private long mAfterBootDelayTime = 0;

    @Option(
            name = "post-initial-boot-idle",
            isTimeVal = true,
            description =
                    "Time to keep device idle after the initial boot up. This can help stablize "
                            + "certain metrics like detecting post boot crashes and "
                            + "SELinux denials.")
    private long mPostInitialBootIdle = 0L;

    @Option(
            name = "granular-boot-info",
            description = "Parse the granular timing info from successive boot time.")
    private boolean mGranularBootInfo = false;

    @Option(
            name = "boot-time-pattern",
            description =
                    "Named boot time regex patterns which are used to capture signals in logcat and"
                            + " calculate duration between device boot to the signal being logged."
                            + " Key: name of custom boot metric, Value: regex to match single"
                            + " logcat line. Maybe repeated.")
    private Map<String, String> mBootTimePatterns = new HashMap<>();

    @Option(name = "dmesg-info", description = "Collect the init services info from dmesg logs.")
    private boolean mDmesgInfo = false;

    // Use this flag not to dump the dmesg logs immediately after the  device is online.
    // Use it only if some of the boot dmesg logs are cleared when waiting until boot completed.
    // By default this is set to true which might result in duplicate logging.
    @Option(
            name = "dump-dmesg-immediate",
            description =
                    "Whether to dump the dmesg logs" + "immediately after the device is online")
    private boolean mDumpDmesgImmediate = true;

    @Option(name = "bootloader-info", description = "Collect the boot loader timing.")
    private boolean mBootloaderInfo = false;

    @Option(
            name = "report-storage-suffix-metric",
            description = "separately report boot time" + " results for storage type")
    private boolean mReportStorageSuffixMetric = false;

    @Option(
            name = "report-boottime-per-iteration",
            description = "separately report boot time results per iteration")
    private boolean mBootTimePerIteration = true;

    // 03-10 21:43:40.328 1005 1005 D SystemServerTiming:StartWifi took to
    // complete: 3474ms
    // 03-10 21:43:40.328 1005 1005 D component:subcomponent took to complete:
    // 3474ms
    @Option(
            name = "components",
            shortName = 'c',
            description =
                    "Comma separated list of component names to parse the granular boot info"
                            + " printed in the logcat.")
    private String mComponentNames = null;

    @Option(
            name = "full-components",
            shortName = 'f',
            description =
                    "Comma separated list of component_subcomponent names to parse the granular"
                            + " boot info printed in the logcat.")
    private String mFullCompNames = null;

    @Option(
            name = "test-reboot-unlock",
            description = "Test the reboot scenario with" + "screen unlock.")
    private boolean mRebootUnlock = false;

    @Option(
            name = "force-f2fs-shutdown",
            description = "Force f2fs shutdown to trigger fsck check during the reboot.")
    private boolean mForceF2FsShutdown = false;

    @Option(
            name = "skip-pin-setup",
            description =
                    "Skip the pin setup if already set once"
                            + "and not needed for the second run especially in local testing.")
    private boolean mSkipPinSetup = false;

    @Option(
            name = "collect-logcat-info",
            description = "Run logcat --statistics command and collect data")
    private boolean mCollectLogcat = false;

    @Option(
            name = "metric-prefix-pattern-for-count",
            description =
                    "A list of metric prefix pattern that will be used to count number of metrics"
                            + " generated in the test")
    private List<String> mMetricPrefixPatternForCount = new ArrayList<>();

    private IBuildInfo mBuildInfo;
    private IConfiguration mConfiguration;
    private TestInformation mTestInfo;
    private Map<String, List<Double>> mBootInfo = new LinkedHashMap<>();
    private Map<String, Double> mBootIterationInfo = new LinkedHashMap<>();
    private LogcatReceiver mRebootLogcatReceiver = null;
    protected String mExtraFirstBootError = null;
    private IRemoteAndroidTestRunner mRunner = null;
    private Set<String> mParsedLines = new HashSet<String>();
    private List<String> mInstalledPackages = new ArrayList<String>();
    private IInvocationContext mInvocationContext = null;
    private List<IMetricCollector> mCollectors = new ArrayList<>();

    /** {@inheritDoc} */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    /** {@inheritDoc} */
    @Override
    public void setConfiguration(IConfiguration configuration) {
        // Ensure the configuration is set on InstalledInstrumentationsTest
        super.setConfiguration(configuration);
        mConfiguration = configuration;
    }

    public TestInformation getTestInformation() {
        return mTestInfo;
    }

    @VisibleForTesting
    void setRebootLogcatReceiver(LogcatReceiver receiver) {
        mRebootLogcatReceiver = receiver;
    }

    @VisibleForTesting
    void setDmesgBootCompleteTime(List<Double> bootCompleteTime) {
        mBootInfo.put(DMESG_BOOT_COMPLETE_TIME, bootCompleteTime);
    }

    @VisibleForTesting
    void setDmesgBootIterationTime(Double bootCompleteTime) {
        mBootIterationInfo.put(DMESG_BOOT_COMPLETE_TIME, bootCompleteTime);
    }

    @VisibleForTesting
    List<Double> getBootMetricValues(String bootMetricKey) {
        return mBootInfo.getOrDefault(bootMetricKey, new ArrayList<>());
    }

    @Override
    public void setMetricCollectors(List<IMetricCollector> collectors) {
        mCollectors = collectors;
    }

    @Override
    public void setInvocationContext(IInvocationContext invocationContext) {
        mInvocationContext = invocationContext;
    }

    /** Returns the {@link IBuildInfo} for the test. */
    public IBuildInfo getBuildInfo() {
        return mBuildInfo;
    }

    /** Returns the {@link IRunUtil} instance to use to run some command. */
    public IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    /** {@inheritDoc} */
    @Override
    public void run(TestInformation testInfo, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        mTestInfo = testInfo;
        long start = System.currentTimeMillis();
        listener.testRunStarted(mTestRunName, 1);
        for (IMetricCollector collector : mCollectors) {
            listener = collector.init(mInvocationContext, listener);
        }
        try {
            try {
                // Set the current date from the host in test device.
                getDevice().setDate(null);

                // Setup device for successive boots, e.g. dismiss SuW
                setupDeviceForSuccessiveBoots();

                Map<String, String> successiveResult = new HashMap<>();
                boolean isSuccessiveBootsSuccess = true;
                TestDescription successiveBootTestId =
                        new TestDescription(
                                String.format("%s.%s", BOOTTIME_TEST, BOOTTIME_TEST),
                                SUCCESSIVE_BOOT_TEST);
                try {
                    // Skip second boot from successive boot delay calculation
                    doSecondBoot();
                    testSuccessiveBoots(false, listener);
                    // Reports average value of individual metrics collected
                    listener.testStarted(successiveBootTestId);
                } catch (RuntimeException re) {
                    CLog.e(re);
                    isSuccessiveBootsSuccess = false;
                    listener.testStarted(successiveBootTestId);
                    listener.testFailed(
                            successiveBootTestId,
                            String.format("RuntimeException during successive reboots: %s", re));
                } catch (DeviceNotAvailableException dnae) {
                    CLog.e("Device not available after successive reboots");
                    CLog.e(dnae);
                    isSuccessiveBootsSuccess = false;
                    listener.testStarted(successiveBootTestId);
                    listener.testFailed(
                            successiveBootTestId,
                            String.format(
                                    "Device not available after successive reboots; %s", dnae));
                } finally {
                    if (isSuccessiveBootsSuccess) {
                        if (null != mRebootLogcatReceiver) {
                            try (InputStreamSource logcatData =
                                    mRebootLogcatReceiver.getLogcatData()) {
                                listener.testLog(LOGCAT_FILE, LogDataType.TEXT, logcatData);
                            }
                            mRebootLogcatReceiver.stop();
                        }
                        listener.testEnded(successiveBootTestId, successiveResult);
                        // Report separately the hardware Storage specific boot time results.
                        if (!successiveResult.isEmpty() && mReportStorageSuffixMetric) {
                            reportStorageSpecificMetrics(listener, successiveResult);
                        }
                    } else {
                        listener.testEnded(successiveBootTestId, successiveResult);
                    }
                }

                // Test to measure the reboot time and time from unlocking the
                // screen using the pin
                // till the NexusLauncherActivity is displayed.
                if (mRebootUnlock) {
                    mBootInfo.clear();
                    Map<String, String> successiveBootUnlockResult = new HashMap<>();
                    TestDescription successiveBootUnlockTestId =
                            new TestDescription(
                                    String.format("%s.%s", BOOTTIME_TEST, BOOTTIME_TEST),
                                    SUCCESSIVE_BOOT_UNLOCK_TEST);
                    try {
                        // If pin is already set skip the setup method otherwise
                        // setup the pin.
                        if (!mSkipPinSetup) {
                            mRunner = createRemoteAndroidTestRunner(SETUP_PIN_TEST);
                            getDevice()
                                    .runInstrumentationTests(mRunner, new CollectingTestListener());
                        }
                        testSuccessiveBoots(true, listener);
                    } finally {
                        if (null != mRebootLogcatReceiver) {
                            try (InputStreamSource logcatData =
                                    mRebootLogcatReceiver.getLogcatData()) {
                                listener.testLog(LOGCAT_UNLOCK_FILE, LogDataType.TEXT, logcatData);
                            }
                            mRebootLogcatReceiver.stop();
                        }
                        listener.testStarted(successiveBootUnlockTestId);
                        listener.testEnded(successiveBootUnlockTestId, successiveBootUnlockResult);
                    }
                }
            } finally {
                // Finish run for boot test. Health check below will start its own test run.
                listener.testRunEnded(
                        System.currentTimeMillis() - start, new HashMap<String, Metric>());
            }
        } finally {
            finalTearDown(listener);
        }
    }

    /** Final optional tear down step for the Boot test. */
    public void finalTearDown(ITestInvocationListener listener) throws DeviceNotAvailableException {
        // empty on purpose
    }

    /**
     * Look for the perfetto trace file collected during reboot under /data/misc/perfetto-traces and
     * copy the file under /data/local/tmp using the test iteration name and return the path to the
     * newly copied trace file.
     */
    private String processPerfettoFile(String testId) throws DeviceNotAvailableException {
        CommandResult result = getDevice().executeShellV2Command(PERFETTO_TRACE_FILE_CHECK_CMD);
        if (result != null) {
            CLog.i(
                    "Command Output: Cmd - %s, Output - %s, Error - %s, Status - %s",
                    PERFETTO_TRACE_FILE_CHECK_CMD,
                    result.getStdout(),
                    result.getStderr(),
                    result.getStatus());
            if (CommandStatus.SUCCESS.equals(result.getStatus())
                    && result.getStdout().contains("boottrace.perfetto-trace")) {
                // Move the perfetto trace file to the new location and rename it using the test
                // name.
                String finalTraceFileLocation =
                        String.format(FINAL_PERFETTO_TRACE_LOCATION, testId);
                CommandResult moveResult =
                        getDevice()
                                .executeShellV2Command(
                                        String.format(
                                                PERFETTO_TRACE_MV_CMD, finalTraceFileLocation));
                if (moveResult != null) {
                    CLog.i(
                            "Command Output: Cmd - %s, Output - %s, Error - %s, Status - %s",
                            PERFETTO_TRACE_MV_CMD,
                            moveResult.getStdout(),
                            moveResult.getStderr(),
                            moveResult.getStatus());
                    if (CommandStatus.SUCCESS.equals(result.getStatus())) {
                        return finalTraceFileLocation;
                    }
                }
            }
        }
        return null;
    }

    private void setupDeviceForSuccessiveBoots() throws DeviceNotAvailableException {
        // Run the list of post first boot setup commands
        for (String cmd : mDeviceSetupCommands) {
            CommandResult result;
            result = getDevice().executeShellV2Command(cmd);
            if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
                CLog.w(
                        "Post boot setup cmd: '%s' failed, returned:\nstdout:%s\nstderr:%s",
                        cmd, result.getStdout(), result.getStderr());
            }
        }
    }

    /** Do the second boot on the device to exclude from the successive boot time calcualtions */
    private void doSecondBoot() throws DeviceNotAvailableException {
        getDevice().nonBlockingReboot();
        getDevice().waitForDeviceOnline();
        getDevice().waitForDeviceAvailable(mDeviceBootTime);
    }

    /**
     * Clear the existing logs and start capturing the logcat
     *
     * <p>It will record from all logcat buffers if user provided any custom boot time metric
     * patterns
     */
    private void clearAndStartLogcat() throws DeviceNotAvailableException {
        getDevice().executeShellCommand("logcat -c");
        boolean allBuffers = !mBootTimePatterns.isEmpty();
        if (mRebootLogcatReceiver == null) {
            mRebootLogcatReceiver =
                    new LogcatReceiver(
                            getDevice(), allBuffers ? LOGCAT_CMD_ALL : LOGCAT_CMD, LOGCAT_SIZE, 0);
        }
        mRebootLogcatReceiver.start();
    }

    /**
     * To perform the successive boot for given boot count and take the average of boot time and
     * online time delays
     *
     * @param dismissPin to dismiss pin after reboot
     */
    private void testSuccessiveBoots(boolean dismissPin, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        CLog.v("Waiting for %d msecs before successive boots.", mBootDelayTime);
        getRunUtil().sleep(mBootDelayTime);
        for (int count = 0; count < mBootCount; count++) {
            getDevice().enableAdbRoot();
            // Property used for collecting the perfetto trace file on boot.
            getDevice().executeShellCommand("setprop persist.debug.perfetto.boottrace 1");
            // Attempt to shurdown F2FS if the option is enabled.
            if (mForceF2FsShutdown) {
                String output = getDevice().executeShellCommand(F2FS_SHUTDOWN_COMMAND).trim();
                if (!F2FS_SHUTDOWN_SUCCESS_OUTPUT.equalsIgnoreCase(output)) {
                    CLog.e("Unable to shutdown the F2FS.");
                } else {
                    CLog.i("F2FS shutdown successful.");
                }
            }

            DmesgParser dmesgLogParser = null;
            double bootStart = INVALID_TIME_DURATION;
            double onlineTime = INVALID_TIME_DURATION;
            double bootTime = INVALID_TIME_DURATION;
            String testId = String.format("%s.%s$%d", BOOTTIME_TEST, BOOTTIME_TEST, (count + 1));
            TestDescription successiveBootIterationTestId;
            if (!dismissPin) {
                successiveBootIterationTestId =
                        new TestDescription(testId, String.format("%s", SUCCESSIVE_BOOT_TEST));
            } else {
                successiveBootIterationTestId =
                        new TestDescription(
                                testId, String.format("%s", SUCCESSIVE_BOOT_UNLOCK_TEST));
            }
            if (mGranularBootInfo || dismissPin) {
                clearAndStartLogcat();
                getRunUtil().sleep(5000);
            }
            getDevice().nonBlockingReboot();
            bootStart = System.currentTimeMillis();
            getDevice().waitForDeviceOnline();
            onlineTime = System.currentTimeMillis() - bootStart;
            getDevice().enableAdbRoot();
            dmesgLogParser = new DmesgParser();
            if (mDmesgInfo && mDumpDmesgImmediate) {
                // Collect the dmesg logs after device is online and
                // after the device is boot completed to avoid losing the
                // initial logs in some devices.
                parseDmesgInfo(dmesgLogParser);
            }
            try {
                waitForBootCompleted();
            } catch (InterruptedException e) {
                CLog.e("Sleep Interrupted");
                CLog.e(e);
            } catch (DeviceNotAvailableException dne) {
                CLog.e("Device not available");
                CLog.e(dne);
            }
            bootTime = System.currentTimeMillis() - bootStart;
            if (mDmesgInfo) {
                // Collect the dmesg logs after device is online and
                // after the device is boot completed to avoid losing the
                // initial logs.
                parseDmesgInfo(dmesgLogParser);
                if (!dmesgLogParser.getServiceInfoItems().isEmpty()) {
                    analyzeDmesgServiceInfo(dmesgLogParser.getServiceInfoItems().values());
                }
                if (!dmesgLogParser.getStageInfoItems().isEmpty()) {
                    analyzeDmesgStageInfo(dmesgLogParser.getStageInfoItems());
                }
                if (!dmesgLogParser.getActionInfoItems().isEmpty()) {
                    analyzeDmesgActionInfo(dmesgLogParser.getActionInfoItems());
                }
            }

            // Parse logcat info
            Map<String, String> collectLogcatInfoResult = new HashMap<>();
            if (mCollectLogcat) {
                collectLogcatInfoResult.putAll(collectLogcatInfo());
            }

            // Parse bootloader timing info
            if (mBootloaderInfo) analyzeBootloaderTimingInfo();

            if (dismissPin) {
                getRunUtil().sleep(2000);
                mRunner = createRemoteAndroidTestRunner(UNLOCK_PIN_TEST);
                getDevice().runInstrumentationTests(mRunner, new CollectingTestListener());
            }

            if (mBootTimePerIteration) {
                listener.testStarted(successiveBootIterationTestId);
            }

            CLog.v("Waiting for %d msecs immediately after successive boot.", mAfterBootDelayTime);
            getRunUtil().sleep(mAfterBootDelayTime);
            if (onlineTime != INVALID_TIME_DURATION) {
                if (mBootInfo.containsKey(SUCCESSIVE_ONLINE)) {
                    mBootInfo.get(SUCCESSIVE_ONLINE).add(onlineTime);
                } else {
                    List<Double> onlineDelayList = new ArrayList<Double>();
                    onlineDelayList.add(onlineTime);
                    mBootInfo.put(SUCCESSIVE_ONLINE, onlineDelayList);
                }
                mBootIterationInfo.put(SUCCESSIVE_ONLINE, onlineTime);
            }
            if (bootTime != INVALID_TIME_DURATION) {
                if (mBootInfo.containsKey(SUCCESSIVE_BOOT)) {
                    mBootInfo.get(SUCCESSIVE_BOOT).add(bootTime);
                } else {
                    List<Double> bootDelayList = new ArrayList<>();
                    bootDelayList.add(bootTime);
                    mBootInfo.put(SUCCESSIVE_BOOT, bootDelayList);
                }
                mBootIterationInfo.put(SUCCESSIVE_BOOT, bootTime);
            }
            if (mGranularBootInfo) {
                getRunUtil().sleep(15000);
                analyzeGranularBootInfo();
                analyzeCustomBootInfo();
                try (InputStreamSource logcatData = mRebootLogcatReceiver.getLogcatData()) {
                    listener.testLog(
                            String.format("%s_%d", LOGCAT_FILE, (count + 1)),
                            LogDataType.TEXT,
                            logcatData);
                }
                if (count != (mBootCount - 1)) {
                    mRebootLogcatReceiver.stop();
                    mRebootLogcatReceiver = null;
                }
            }
            if (dismissPin) {
                analyzeUnlockBootInfo();
                if (count != (mBootCount - 1)) {
                    mRebootLogcatReceiver.stop();
                    mRebootLogcatReceiver = null;
                }
            }

            String perfettoTraceFilePath =
                    processPerfettoFile(
                            String.format(
                                    "%s_%s",
                                    successiveBootIterationTestId.getClassName().replace("$", "_"),
                                    successiveBootIterationTestId.getTestName()));

            if (mBootTimePerIteration) {
                Map<String, String> iterationResult = new HashMap<>();
                for (Map.Entry<String, Double> bootData : mBootIterationInfo.entrySet()) {
                    iterationResult.put(bootData.getKey(), bootData.getValue().toString());
                }
                if (perfettoTraceFilePath != null) {
                    iterationResult.put("perfetto_file_path", perfettoTraceFilePath);
                }
                if (!collectLogcatInfoResult.isEmpty()) {
                    iterationResult.putAll(collectLogcatInfoResult);
                }
                // If  metric-prefix-pattern-for-count is present, calculate the count
                // of all metrics with the prefix pattern and add the count as a new metric to the
                // iterationResult map.
                if (!mMetricPrefixPatternForCount.isEmpty()) {
                    for (String metricPrefixPattern : mMetricPrefixPatternForCount) {
                        long metricCount =
                                iterationResult.entrySet().stream()
                                        .filter(
                                                (entry) ->
                                                        entry.getKey()
                                                                .startsWith(metricPrefixPattern))
                                        .count();
                        iterationResult.put(
                                metricPrefixPattern + METRIC_COUNT, Long.toString(metricCount));
                    }
                }
                listener.testEnded(successiveBootIterationTestId, iterationResult);
            }

            mBootIterationInfo.clear();
            CLog.v("Waiting for %d msecs after boot completed.", mBootDelayTime);
            getRunUtil().sleep(mBootDelayTime);
        }
    }

    /** Get the logcat statistics info */
    private Map<String, String> collectLogcatInfo() throws DeviceNotAvailableException {
        Map<String, String> results = new HashMap<>();

        // Parse logcat in global level
        results.putAll(extractLogcatInfo(-1, "general"));

        // Get all the process PIDs first
        Map<Integer, String> pids = getPids();
        if (pids.size() == 0) {
            CLog.e("Failed to get all the valid process PIDs");
            return results;
        }
        // Parse logcat in process level
        for (Map.Entry<Integer, String> process : pids.entrySet()) {
            results.putAll(extractLogcatInfo(process.getKey(), process.getValue()));
        }

        return results;
    }

    /** Get pid of all processes */
    private Map<Integer, String> getPids() throws DeviceNotAvailableException {
        // return pids
        Map<Integer, String> pids = new HashMap<>();
        String pidOutput = getDevice().executeShellCommand(ALL_PROCESS_CMD);
        // Sample output for the process info
        // Sample command : "ps -A"
        // Sample output :
        // system   4533   410 13715708 78536 do_freezer_trap 0 S    com.android.keychain
        // root    32552     2        0     0   worker_thread 0 I [kworker/6:0-memlat_wq]
        String[] lines = pidOutput.split(System.lineSeparator());
        for (String line : lines) {
            String[] splitLine = line.split("\\s+");
            // Skip the first (i.e header) line from "ps -A" output.
            if (splitLine[1].equalsIgnoreCase("PID")) {
                continue;
            }
            String processName = splitLine[splitLine.length - 1].replace("\n", "").trim();
            pids.put(Integer.parseInt(splitLine[1]), processName);
        }
        return pids;
    }

    /** Get the logcat statistics info */
    private Map<String, String> extractLogcatInfo(int pid, String processName)
            throws DeviceNotAvailableException {
        Map<String, String> results = new HashMap<>();
        String runCommand =
                pid == -1 ? LOGCAT_CMD_STATISTICS_ALL : String.format(LOGCAT_CMD_STATISTICS, pid);
        String output = getDevice().executeShellCommand(runCommand);
        String[] outputList = output.split("\\n\\n");

        if (pid == -1) {
            // General statistics
            // Sample output for $ logcat --statistics
            // size/num main         system          crash           kernel   Total
            // Total    33/23        96/91           3870/4          70/1     513/41
            // Now      92/70        4/15            0/0             13/11    33/26
            // Logspan  5:15:15.15   11d 20:37:31.37 13:20:54.185    11d 20:40:06.816
            // Overhead 253454       56415               255139      1330477
            Matcher matcherHeader = LOGCAT_STATISTICS_HEADER_PATTERN.matcher(outputList[0]);
            Matcher matcherTotal = LOGCAT_STATISTICS_TOTAL_PATTERN.matcher(outputList[0]);
            Matcher matcherNow = LOGCAT_STATISTICS_NOW_PATTERN.matcher(outputList[0]);
            boolean headerFound = matcherHeader.find();
            boolean totalFound = matcherTotal.find();
            boolean nowFound = matcherNow.find();
            if (headerFound && totalFound && nowFound) {
                // There are 6 columns in the output, but we just want to extract column 1 to 4
                for (int i = 1; i < 5; i++) {
                    String bufferHeader = matcherHeader.group(i).trim();
                    results.put(
                            String.join(
                                    METRIC_KEY_SEPARATOR,
                                    LOGCAT_STATISTICS_SIZE,
                                    bufferHeader,
                                    processName),
                            matcherTotal.group(i * 2 - 1).trim());
                    results.put(
                            String.join(
                                    METRIC_KEY_SEPARATOR,
                                    LOGCAT_STATISTICS_DIFF_SIZE,
                                    bufferHeader,
                                    processName),
                            Integer.toString(
                                    Integer.valueOf(matcherTotal.group(i * 2 - 1).trim())
                                            - Integer.valueOf(matcherNow.group(i * 2 - 1).trim())));
                }
            }
        } else if (outputList.length > 1) {
            // Process statistics
            // Sample output for $ logcat --statistics --pid 3330
            // Logging for this PID:                                        Size        Pruned
            //  PID/UID   COMMAND LINE                                    BYTES           NUM
            // 3330/10171 ...ogle.android.googlequicksearchbox:interactor 13024
            boolean pidFound = false;
            for (int i = 0; i < outputList.length; i++) {
                Matcher matcherPid = LOGCAT_STATISTICS_PID_PATTERN.matcher(outputList[i]);
                pidFound = matcherPid.find();
                if (!pidFound) continue;
                CLog.d(
                        "Process %s with pid %d : logcat statistics output = %s",
                        processName, pid, outputList[i]);
                results.put(
                        String.join(METRIC_KEY_SEPARATOR, LOGCAT_STATISTICS_SIZE, processName),
                        matcherPid.group(1).trim());
                break;
            }
            if (!pidFound) {
                // the process doesn't found in the logcat statistics output
                CLog.d(
                        "Process %s with pid %d doesn't exist in logcat statistics.",
                        processName, pid);
            }
        }
        return results;
    }

    /**
     * Parse the logcat file for granular boot info (eg different system services start time) based
     * on the component name or full component name (i.e component_subcompname)
     */
    private void analyzeGranularBootInfo() {
        String[] compStr = new String[0];
        String[] fullCompStr = new String[0];
        boolean isFilterSet = false;

        if (null != mComponentNames) {
            compStr = mComponentNames.split(",");
            isFilterSet = true;
        }
        if (null != mFullCompNames) {
            fullCompStr = mFullCompNames.split(",");
            isFilterSet = true;
        }

        Set<String> compSet = new HashSet<>(Arrays.asList(compStr));
        Set<String> fullCompSet = new HashSet<>(Arrays.asList(fullCompStr));

        try (InputStreamSource logcatData = mRebootLogcatReceiver.getLogcatData();
                InputStream logcatStream = logcatData.createInputStream();
                InputStreamReader logcatReader = new InputStreamReader(logcatStream);
                BufferedReader br = new BufferedReader(logcatReader)) {

            TimingsLogParser parser = new TimingsLogParser();
            List<SystemServicesTimingItem> items = parser.parseSystemServicesTimingItems(br);
            for (SystemServicesTimingItem item : items) {
                String componentName = item.getComponent();
                String fullCompName =
                        String.format("%s_%s", item.getComponent(), item.getSubcomponent());
                // If filter not set then capture timing info for all the
                // components otherwise
                // only for the given component names and full component
                // names.
                if (!isFilterSet
                        || compSet.contains(componentName)
                        || fullCompSet.contains(fullCompName)) {
                    Double time =
                            item.getDuration() != null ? item.getDuration() : item.getStartTime();
                    if (time == null) {
                        continue;
                    }
                    if (mBootInfo.containsKey(fullCompName)) {
                        mBootInfo.get(fullCompName).add(time);
                    } else {
                        List<Double> delayList = new ArrayList<>();
                        delayList.add(time);
                        mBootInfo.put(fullCompName, delayList);
                    }
                    mBootIterationInfo.put(fullCompName, time);
                }
            }
        } catch (IOException ioe) {
            CLog.e("Problem in parsing the granular boot delay information");
            CLog.e(ioe);
        }
    }

    /** Parse the logcat file to get boot time metrics given patterns defined by tester. */
    private void analyzeCustomBootInfo() {
        if (mBootTimePatterns.isEmpty()) return;
        Double dmesgBootCompleteTimes;
        TimingsLogParser parser = new TimingsLogParser();
        parser.addDurationPatternPair(BOOT_PHASE_1000, KERNEL_START_PATTERN, LOGCAT_BOOT_COMPLETED);
        for (Map.Entry<String, String> pattern : mBootTimePatterns.entrySet()) {
            CLog.d(
                    "Adding boot metric with name: %s, pattern: %s",
                    pattern.getKey(), pattern.getValue());
            parser.addDurationPatternPair(
                    pattern.getKey(), KERNEL_START_PATTERN, Pattern.compile(pattern.getValue()));
        }
        try (InputStreamSource logcatData = mRebootLogcatReceiver.getLogcatData();
                InputStream logcatStream = logcatData.createInputStream();
                InputStreamReader logcatReader = new InputStreamReader(logcatStream);
                BufferedReader br = new BufferedReader(logcatReader)) {

            if (mBootIterationInfo.containsKey(DMESG_BOOT_COMPLETE_TIME)) {
                dmesgBootCompleteTimes = mBootIterationInfo.get(DMESG_BOOT_COMPLETE_TIME);
            } else {
                CLog.d("Missing dmesg boot complete signals");
                return;
            }

            List<GenericTimingItem> items = parser.parseGenericTimingItems(br);

            Map<String, GenericTimingItem> itemsMap = new HashMap<>();
            GenericTimingItem logcatBootCompleteItem = new GenericTimingItem();
            for (GenericTimingItem item : items) {
                if (BOOT_PHASE_1000.equals(item.getName())) {
                    logcatBootCompleteItem = item;
                } else {
                    itemsMap.put(item.getName(), item);
                }
            }
            if (logcatBootCompleteItem.getName() == null) {
                CLog.e("Missing boot complete signals from logcat");
                return;
            }
            for (Map.Entry<String, GenericTimingItem> metric : itemsMap.entrySet()) {
                GenericTimingItem itemsForMetric = metric.getValue();
                if (itemsForMetric.getName().isEmpty()) {
                    CLog.e("Missing value for metric %s", metric.getKey());
                    continue;
                }
                List<Double> values = mBootInfo.getOrDefault(metric.getKey(), new ArrayList<>());
                double duration =
                        dmesgBootCompleteTimes
                                + itemsForMetric.getEndTime()
                                - logcatBootCompleteItem.getEndTime();
                values.add(duration);
                mBootInfo.put(metric.getKey(), values);
                mBootIterationInfo.put(metric.getKey(), duration);
                CLog.d("Added boot metric: %s with duration values: %s", metric.getKey(), values);
            }
        } catch (IOException e) {
            CLog.e("Problem when parsing custom boot time info");
            CLog.e(e);
        }
    }

    /**
     * Collect the dmesg logs and parse the service info(start and end time), start time of boot
     * stages and actions being processed, logged in the dmesg file.
     */
    private void parseDmesgInfo(DmesgParser dmesgLogParser) throws DeviceNotAvailableException {
        // Dump the dmesg logs to a file in the device
        getDevice().executeShellCommand(DUMP_DMESG);
        try {
            File dmesgFile = getDevice().pullFile(DMESG_FILE);
            BufferedReader input =
                    new BufferedReader(new InputStreamReader(new FileInputStream(dmesgFile)));
            dmesgLogParser.parseInfo(input);
            dmesgFile.delete();
        } catch (IOException ioe) {
            CLog.e("Failed to analyze the dmesg logs", ioe);
        }
    }

    /**
     * Analyze the services info parsed from the dmesg logs and construct the metrics as a part of
     * boot time data.
     *
     * @param serviceInfoItems contains the start time, end time and the duration of each service
     *     logged in the dmesg log file.
     */
    private void analyzeDmesgServiceInfo(Collection<DmesgServiceInfoItem> serviceInfoItems) {
        for (DmesgServiceInfoItem infoItem : serviceInfoItems) {
            if (infoItem.getStartTime() != null) {
                String key = String.format("%s%s%s", INIT, infoItem.getServiceName(), START_TIME);
                if (mBootInfo.get(key) != null) {
                    mBootInfo.get(key).add(infoItem.getStartTime().doubleValue());
                } else {
                    List<Double> timeList = new ArrayList<Double>();
                    timeList.add(infoItem.getStartTime().doubleValue());
                    mBootInfo.put(key, timeList);
                }
                mBootIterationInfo.put(key, infoItem.getStartTime().doubleValue());
            }
            if (infoItem.getServiceDuration() != -1L) {
                String key = String.format("%s%s%s", INIT, infoItem.getServiceName(), DURATION);
                if (mBootInfo.get(key) != null) {
                    mBootInfo.get(key).add(infoItem.getServiceDuration().doubleValue());
                } else {
                    List<Double> timeList = new ArrayList<Double>();
                    timeList.add(infoItem.getServiceDuration().doubleValue());
                    mBootInfo.put(key, timeList);
                }
                mBootIterationInfo.put(key, infoItem.getServiceDuration().doubleValue());
            }
            if (infoItem.getEndTime() != null) {
                String key = String.format("%s%s%s", INIT, infoItem.getServiceName(), END_TIME);
                if (mBootInfo.get(key) != null) {
                    mBootInfo.get(key).add(infoItem.getEndTime().doubleValue());
                } else {
                    List<Double> timeList = new ArrayList<Double>();
                    timeList.add(infoItem.getEndTime().doubleValue());
                    mBootInfo.put(key, timeList);
                }
                mBootIterationInfo.put(key, infoItem.getEndTime().doubleValue());
            }
        }
    }

    /**
     * Analyze the boot stages info parsed from the dmesg logs and construct the metrics as a part
     * of boot time data.
     *
     * @param stageInfoItems contains the start time of each stage logged in the dmesg log file.
     */
    private void analyzeDmesgStageInfo(Collection<DmesgStageInfoItem> stageInfoItems) {
        for (DmesgStageInfoItem stageInfoItem : stageInfoItems) {
            if (stageInfoItem.getStartTime() != null) {
                String key =
                        String.format(
                                "%s%s%s", INIT_STAGE, stageInfoItem.getStageName(), START_TIME);
                List<Double> values = mBootInfo.getOrDefault(key, new ArrayList<>());
                values.add(stageInfoItem.getStartTime().doubleValue());
                mBootInfo.put(key, values);
                mBootIterationInfo.put(key, stageInfoItem.getStartTime().doubleValue());
            } else if (stageInfoItem.getDuration() != null) {
                List<Double> values =
                        mBootInfo.getOrDefault(stageInfoItem.getStageName(), new ArrayList<>());
                values.add(stageInfoItem.getDuration().doubleValue());
                mBootInfo.put(stageInfoItem.getStageName(), values);
                mBootIterationInfo.put(
                        stageInfoItem.getStageName(), stageInfoItem.getDuration().doubleValue());
            }
        }
    }

    /**
     * Analyze each action info parsed from the dmesg logs and construct the metrics as a part of
     * boot time data.
     *
     * @param actionInfoItems contains the start time of processing of each action logged in the
     *     dmesg log file.
     */
    private void analyzeDmesgActionInfo(Collection<DmesgActionInfoItem> actionInfoItems) {
        boolean isFirstBootCompletedAction = true;
        for (DmesgActionInfoItem actionInfoItem : actionInfoItems) {
            if (actionInfoItem.getStartTime() != null) {
                if (actionInfoItem.getActionName().startsWith(BOOT_COMPLETE_ACTION)
                        && isFirstBootCompletedAction) {
                    CLog.i(
                            "Using Action: %s_%s for first boot complete timestamp :%s",
                            actionInfoItem.getActionName(),
                            actionInfoItem.getSourceName(),
                            actionInfoItem.getStartTime().doubleValue());
                    // Record the first boot complete time stamp.
                    List<Double> dmesgBootCompleteTimes =
                            mBootInfo.getOrDefault(DMESG_BOOT_COMPLETE_TIME, new ArrayList<>());
                    dmesgBootCompleteTimes.add(actionInfoItem.getStartTime().doubleValue());
                    mBootInfo.put(DMESG_BOOT_COMPLETE_TIME, dmesgBootCompleteTimes);
                    mBootIterationInfo.put(
                            DMESG_BOOT_COMPLETE_TIME, actionInfoItem.getStartTime().doubleValue());
                    isFirstBootCompletedAction = false;
                }
                String key =
                        String.format(
                                "%s%s_%s%s",
                                ACTION,
                                actionInfoItem.getActionName(),
                                actionInfoItem.getSourceName() != null
                                        ? actionInfoItem.getSourceName()
                                        : "",
                                START_TIME);
                List<Double> values = mBootInfo.getOrDefault(key, new ArrayList<>());
                values.add(actionInfoItem.getStartTime().doubleValue());
                mBootInfo.put(key, values);
                mBootIterationInfo.put(key, actionInfoItem.getStartTime().doubleValue());
            }
        }
    }

    /**
     * Analyze the time taken by different phases in boot loader by parsing the system property
     * ro.boot.boottime
     */
    private void analyzeBootloaderTimingInfo() throws DeviceNotAvailableException {
        String bootLoaderVal = getDevice().getProperty(BOOT_TIME_PROP);
        // Sample Output : 1BLL:89,1BLE:590,2BLL:0,2BLE:1344,SW:6734,KL:1193
        if (bootLoaderVal != null) {
            String[] bootLoaderPhases = bootLoaderVal.split(",");
            double bootLoaderTotalTime = 0d;
            for (String bootLoaderPhase : bootLoaderPhases) {
                String[] bootKeyVal = bootLoaderPhase.split(":");
                String key = String.format("%s%s", BOOTLOADER_PREFIX, bootKeyVal[0]);
                if (mBootInfo.containsKey(key)) {
                    mBootInfo.get(key).add(Double.parseDouble(bootKeyVal[1]));
                } else {
                    List<Double> timeList = new ArrayList<Double>();
                    timeList.add(Double.parseDouble(bootKeyVal[1]));
                    mBootInfo.put(key, timeList);
                }
                mBootIterationInfo.put(key, Double.parseDouble(bootKeyVal[1]));
                // SW is the time spent on the warning screen. So ignore it in
                // final boot time calculation.
                if (!BOOTLOADER_PHASE_SW.equalsIgnoreCase(bootKeyVal[0])) {
                    bootLoaderTotalTime += Double.parseDouble(bootKeyVal[1]);
                }
            }

            // Report bootloader time as well in the dashboard.
            CLog.i("Bootloader time is :%s", bootLoaderTotalTime);
            if (mBootInfo.containsKey(BOOTLOADER_TIME)) {
                mBootInfo.get(BOOTLOADER_TIME).add(bootLoaderTotalTime);
            } else {
                List<Double> timeList = new ArrayList<Double>();
                timeList.add(bootLoaderTotalTime);
                mBootInfo.put(BOOTLOADER_TIME, timeList);
            }
            mBootIterationInfo.put(BOOTLOADER_TIME, bootLoaderTotalTime);

            // First "action_sys.boot_completed=1_START_TIME" is parsed already from dmesg logs.
            // Current dmesg boot complete time should always be the last one in value list.
            // Calculate the sum of bootLoaderTotalTime and boot completed flag set time.
            List<Double> bootCompleteTimes =
                    mBootInfo.getOrDefault(DMESG_BOOT_COMPLETE_TIME, new ArrayList<>());
            double bootCompleteTime =
                    bootCompleteTimes.isEmpty()
                            ? 0L
                            : bootCompleteTimes.get(bootCompleteTimes.size() - 1);
            double totalBootTime = bootLoaderTotalTime + bootCompleteTime;
            if (mBootInfo.containsKey(TOTAL_BOOT_TIME)) {
                mBootInfo.get(TOTAL_BOOT_TIME).add(totalBootTime);
            } else {
                List<Double> timeList = new ArrayList<Double>();
                timeList.add(totalBootTime);
                mBootInfo.put(TOTAL_BOOT_TIME, timeList);
            }
            mBootIterationInfo.put(TOTAL_BOOT_TIME, totalBootTime);
        }
    }

    /**
     * Parse the logcat file and calculate the time difference between the screen unlocked timestamp
     * till the Nexus launcher activity is displayed.
     */
    private void analyzeUnlockBootInfo() {
        try (InputStreamSource logcatData = mRebootLogcatReceiver.getLogcatData();
                InputStream logcatStream = logcatData.createInputStream();
                InputStreamReader logcatReader = new InputStreamReader(logcatStream);
                BufferedReader br = new BufferedReader(logcatReader)) {
            boolean logOrderTracker = false;
            double unlockInMillis = 0d;
            String line;
            while ((line = br.readLine()) != null) {
                Matcher match = null;
                if ((match = matches(SCREEN_UNLOCKED, line)) != null && !isDuplicateLine(line)) {
                    mParsedLines.add(line);
                    Date time = parseTime(match.group(1));
                    unlockInMillis = time.getTime();
                    logOrderTracker = true;
                } else if ((match = matches(DISPLAYED_LAUNCHER, line)) != null
                        && !isDuplicateLine(line)
                        && logOrderTracker) {
                    Date time = parseTime(match.group(1));
                    if (mBootInfo.containsKey(UNLOCK_TIME)) {
                        mBootInfo.get(UNLOCK_TIME).add(time.getTime() - unlockInMillis);
                    } else {
                        List<Double> screenUnlockTime = new ArrayList<Double>();
                        screenUnlockTime.add(time.getTime() - unlockInMillis);
                        mBootInfo.put(UNLOCK_TIME, screenUnlockTime);
                    }
                    mBootIterationInfo.put(UNLOCK_TIME, time.getTime() - unlockInMillis);
                    logOrderTracker = false;
                }
            }
        } catch (IOException ioe) {
            CLog.e("Problem in parsing screen unlock delay from logcat.");
            CLog.e(ioe);
        }
    }

    /**
     * To check if the line is duplicate entry in the log file.
     *
     * @return true if log line are duplicated
     */
    private boolean isDuplicateLine(String currentLine) {
        if (mParsedLines.contains(currentLine)) {
            return true;
        } else {
            mParsedLines.add(currentLine);
            return false;
        }
    }

    /**
     * Report the reboot time results separately under the storage specific reporting unit.
     *
     * @param results contains boot time test data
     */
    private void reportStorageSpecificMetrics(
            ITestInvocationListener listener, Map<String, String> results)
            throws DeviceNotAvailableException {
        String storageType = getDevice().getProperty(STORAGE_TYPE);
        if (null != storageType && !storageType.isEmpty()) {
            // Construct reporting id based on UFS type
            // Example : DeviceBootTest.DeviceBootTest#SuccessiveBootTest-64GB
            TestDescription successiveStorageBootTestId =
                    new TestDescription(
                            String.format("%s.%s", BOOTTIME_TEST, BOOTTIME_TEST),
                            String.format("%s-%s", SUCCESSIVE_BOOT_TEST, storageType));
            listener.testStarted(successiveStorageBootTestId);
            listener.testEnded(successiveStorageBootTestId, results);
        }
    }

    /** Concatenate given list of values to comma separated string */
    public String concatenateTimeValues(List<Double> timeInfo) {
        StringBuilder timeString = new StringBuilder();
        for (Double time : timeInfo) {
            timeString.append(time);
            timeString.append(",");
        }
        return timeString.toString();
    }

    /**
     * Checks whether {@code line} matches the given {@link Pattern}.
     *
     * @return The resulting {@link Matcher} obtained by matching the {@code line} against {@code
     *     pattern}, or null if the {@code line} does not match.
     */
    private static Matcher matches(Pattern pattern, String line) {
        Matcher ret = pattern.matcher(line);
        return ret.matches() ? ret : null;
    }

    /**
     * Method to create the runner with given testName
     *
     * @return the {@link IRemoteAndroidTestRunner} to use.
     */
    IRemoteAndroidTestRunner createRemoteAndroidTestRunner(String testName)
            throws DeviceNotAvailableException {
        RemoteAndroidTestRunner runner =
                new RemoteAndroidTestRunner(PACKAGE_NAME, RUNNER, getDevice().getIDevice());
        runner.setMethodName(CLASS_NAME, testName);
        return runner;
    }

    /** Wait until the sys.boot_completed is set */
    private void waitForBootCompleted() throws InterruptedException, DeviceNotAvailableException {
        for (int i = 0; i < BOOT_COMPLETE_POLL_RETRY_COUNT; i++) {
            if (isBootCompleted()) {
                return;
            }
            Thread.sleep(BOOT_COMPLETE_POLL_INTERVAL);
        }
    }

    /** Returns true if boot completed property is set to true. */
    private boolean isBootCompleted() throws DeviceNotAvailableException {
        return BOOT_COMPLETED_VAL.equals(
                getDevice().executeShellCommand(BOOT_COMPLETED_PROP).trim());
    }

    /**
     * Parse the timestamp and return a {@link Date}.
     *
     * @param timeStr The timestamp in the format {@code MM-dd HH:mm:ss.SSS}.
     * @return The {@link Date}.
     */
    private Date parseTime(String timeStr) {
        DateFormat yearFormatter = new SimpleDateFormat("yyyy");
        String mYear = yearFormatter.format(new Date());
        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        try {
            return formatter.parse(String.format("%s-%s", mYear, timeStr));
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * @return the time to wait between the reboots.
     */
    public long getBootDelayTime() {
        return mBootDelayTime;
    }

    final FailureDescription createFailureFromException(
            String additionalMessage, Exception exception, FailureStatus defaultStatus) {
        String message = exception.getMessage();
        if (additionalMessage != null) {
            message = String.format("%s\n%s", additionalMessage, message);
        }
        FailureDescription failure = FailureDescription.create(message).setCause(exception);
        failure.setFailureStatus(defaultStatus);
        if (exception instanceof IHarnessException) {
            ErrorIdentifier id = ((IHarnessException) exception).getErrorId();
            failure.setErrorIdentifier(id);
            failure.setOrigin(((IHarnessException) exception).getOrigin());
            if (id != null) {
                failure.setFailureStatus(id.status());
            }
        }
        return failure;
    }
}
