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

package com.android.tradefed.device.metric;

import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceRuntimeException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.util.BluetoothUtils;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.util.Map;

/**
 * Collector to enable Bluetooth HCI snoop logging on the DUT and to collect the log for each test.
 * The collector will configure and enable snoop logging for the test run and revert the settings
 * after the test run.
 */
@OptionClass(alias = "bluetooth-hci-snoop-log-collector")
public class BluetoothHciSnoopLogCollector extends FilePullerDeviceMetricCollector {

    // Settings for HCI-snoop-log reporting.
    private String reportingDir = null;
    public static final String SNOOP_LOG_MODE_PROPERTY = "persist.bluetooth.btsnooplogmode";
    private String initialSnoopLogMode = null;
    // Snoop-log-file header is defined in:
    // https://cs.android.com/android/platform/superproject/+/master:packages/modules/Bluetooth/system/gd/hal/snoop_logger_common.h
    private static final int SNOOP_LOG_FILE_HEADER_BYTE_SIZE = 16;

    @Override
    public void onTestRunStart(DeviceMetricData runData) throws DeviceNotAvailableException {
        // Remember the initial snoop-log mode on the device.
        initialSnoopLogMode = getSnoopLogModeProperty();
        // Enable snoop logging on device.
        setSnoopLogModeProperty("full");
        for (ITestDevice device : getRealDevices()) {
            // Restart Bluetooth service, to allow the snoop-log setting to take effect.
            disableBluetoothService(device);
            enableBluetoothService(device);
        }
    }

    @Override
    public void onTestRunEnd(DeviceMetricData runData, final Map<String, Metric> currentRunMetrics)
            throws DeviceNotAvailableException {
        // Disable snoop logging on device.
        setSnoopLogModeProperty(initialSnoopLogMode);
        for (ITestDevice device : getRealDevices()) {
            // Wind down Bluetooth service after testing.
            disableBluetoothService(device);
        }
    }

    @Override
    public void onTestStart(DeviceMetricData testData) throws DeviceNotAvailableException {
        // Create the reporting directory for test snoop log.
        deleteReportingDirectory();
        createReportingDirectory();
        for (ITestDevice device : getRealDevices()) {
            // Get a clean slate on the snoop log for the test by only preserving header.
            executeShellCommand(
                    device,
                    String.format(
                            "truncate -s %s %s",
                            SNOOP_LOG_FILE_HEADER_BYTE_SIZE, BluetoothUtils.GOLD_BTSNOOP_LOG_PATH));
        }
    }

    @Override
    public void onTestEnd(
            DeviceMetricData testData,
            final Map<String, Metric> currentTestCaseMetrics,
            TestDescription test)
            throws DeviceNotAvailableException {
        // Saving HCI snoop logs for the test.
        String testName = test.toString();
        String normalisedTestName = normaliseTestName(testName);

        for (ITestDevice device : getRealDevices()) {
            String serialNumber = device.getSerialNumber();
            String testSnoopLogFilename =
                    String.format(getHciSnoopLogPathFormat(), normalisedTestName, serialNumber);
            executeShellCommand(
                    device,
                    String.format(
                            "cp -p %s %s",
                            BluetoothUtils.GOLD_BTSNOOP_LOG_PATH, testSnoopLogFilename));
        }

        super.onTestEnd(testData, currentTestCaseMetrics);
    }

    @Override
    public final void processMetricFile(String key, File metricFile, DeviceMetricData runData) {
        try (InputStreamSource source = new FileInputStreamSource(metricFile, true)) {
            testLog(FileUtil.getBaseName(metricFile.getName()), LogDataType.BT_SNOOP_LOG, source);
        }
    }

    // From
    // tools/tradefederation/core/src/com/android/tradefed/device/metric/FilePullerLogCollector.java
    @Override
    public void processMetricDirectory(String key, File metricDirectory, DeviceMetricData runData) {
        if (metricDirectory.listFiles() == null) {
            CLog.e("metricDirectory.listFiles() is null.");
            return;
        }
        for (File file : metricDirectory.listFiles()) {
            if (file.isDirectory()) {
                processMetricDirectory(key, file, runData);
            } else {
                processMetricFile(key, file, runData);
            }
        }
        FileUtil.recursiveDelete(metricDirectory);
    }

    /** Retrieve the directory to report the HCI snoop logs to. */
    public String getReportingDir() {
        if (reportingDir == null) {
            if (mDirectoryKeys.size() == 0) {
                CLog.w("No directory key set.");
            } else if (mDirectoryKeys.size() > 1) {
                CLog.w("%s directory keys were set.", mDirectoryKeys.size());
            }
            // Assume that the first directory key contains the location to store the HCI snoop
            // logs.
            reportingDir = mDirectoryKeys.iterator().next();
        }
        return reportingDir;
    }

    /** Construct a filename path for HCI snoop logs, to be tagged with test name and device id. */
    private String getHciSnoopLogPathFormat() throws DeviceNotAvailableException {
        return getReportingDir() + "/%s-%s-btsnoop_hci.log";
    }

    private void createReportingDirectory() throws DeviceNotAvailableException {
        for (ITestDevice device : getRealDevices()) {
            executeShellCommand(device, "mkdir -p " + getReportingDir());
        }
    }

    private void deleteReportingDirectory() throws DeviceNotAvailableException {
        for (ITestDevice device : getRealDevices()) {
            executeShellCommand(device, "rm -rf  " + getReportingDir());
        }
    }

    private String getSnoopLogModeProperty() throws DeviceNotAvailableException {
        for (ITestDevice device : getRealDevices()) {
            return device.getProperty(SNOOP_LOG_MODE_PROPERTY);
        }
        return null;
    }

    private void setSnoopLogModeProperty(String mode) throws DeviceNotAvailableException {
        if (mode == null) {
            CLog.i("mode is null. Using empty string instead.");
            mode = "";
        }
        for (ITestDevice device : getRealDevices()) {
            boolean successfullySetPropOnDevice = device.setProperty(SNOOP_LOG_MODE_PROPERTY, mode);
            if (!successfullySetPropOnDevice) {
                CLog.w(
                        "Failed to set property [%s] to [%s] on [%s].",
                        SNOOP_LOG_MODE_PROPERTY, mode, device.getSerialNumber());
            }
        }
    }

    private void disableBluetoothService(ITestDevice device) throws DeviceNotAvailableException {
        executeShellCommand(device, "cmd bluetooth_manager disable");
        executeShellCommand(device, "cmd bluetooth_manager wait-for-state:STATE_OFF");
    }

    private void enableBluetoothService(ITestDevice device) throws DeviceNotAvailableException {
        executeShellCommand(device, "cmd bluetooth_manager enable");
        executeShellCommand(device, "cmd bluetooth_manager wait-for-state:STATE_ON");
    }

    /**
     * Execute shell command on the device. If the execution failed (non-zero exit code), throw a
     * {@link com.android.tradefed.device.DeviceRuntimeException}.
     *
     * @throws DeviceRuntimeException
     */
    protected void executeShellCommand(ITestDevice device, String command)
            throws DeviceNotAvailableException {
        CommandResult result = device.executeShellV2Command(command);
        if (result.getExitCode() != 0) {
            throw new DeviceRuntimeException(
                    "Failed to execute command: " + command,
                    DeviceErrorIdentifier.SHELL_COMMAND_ERROR);
        }
    }

    /**
     * Normalise the test name to avoid using slash /. For instance, "A2DP_SNK#A2DP/SNK/AS/BV-01-I"
     * would be updated to "A2DP_SNK#A2DP_SNK_AS_BV-01-I".
     */
    private String normaliseTestName(String testName) {
        return testName.replace("/", "_");
    }
}
