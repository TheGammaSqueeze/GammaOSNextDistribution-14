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
 * limitations under the License.
 */
package com.android.tradefed.device.metric;

import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.invoker.logger.CurrentInvocation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.ZipUtil;
import com.android.tradefed.util.proto.TfMetricProtoUtil;

import java.io.File;
import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A {@link BaseDeviceMetricCollector} that listen for metrics key coming from the device and pull
 * them as a file from the device. Can be extended for extra-processing of the file.
 */
public abstract class FilePullerDeviceMetricCollector extends BaseDeviceMetricCollector {

    @Option(
            name = "pull-pattern-keys",
            description =
                    "The pattern key name to be pull from the device as a file. Can be repeated.")
    private Set<String> mKeys = new HashSet<>();

    @Option(
            name = "directory-keys",
            description = "Path to the directory on the device that contains the metrics.")
    protected Set<String> mDirectoryKeys = new HashSet<>();

    @Option(name = "compress-directories",
            description = "Compress multiple files in the matching directory into zip file")
    private boolean mCompressDirectory = false;

    @Option(
        name = "clean-up",
        description = "Whether to delete the file from the device after pulling it or not."
    )
    private boolean mCleanUp = true;

    @Option(
        name = "collect-on-run-ended-only",
        description =
                "Attempt to collect the files on test run end only instead of on both test cases "
                        + "and test run ended. This is safer since test case level collection isn't"
                        + " synchronous."
    )
    private boolean mCollectOnRunEndedOnly = true;
    public Map<String, String> mTestCaseMetrics = new HashMap<String, String>();

    @Override
    public void onTestEnd(DeviceMetricData testData, Map<String, Metric> currentTestCaseMetrics)
            throws DeviceNotAvailableException {
        if (mCollectOnRunEndedOnly) {
            // Track test cases metrics in case we don't process here.
            mTestCaseMetrics.putAll(TfMetricProtoUtil.compatibleConvert(currentTestCaseMetrics));
            return;
        }
        processMetricRequest(testData, currentTestCaseMetrics);
    }

    @Override
    public void onTestRunEnd(DeviceMetricData runData, final Map<String, Metric> currentRunMetrics)
            throws DeviceNotAvailableException {
        processMetricRequest(runData, currentRunMetrics);
        mTestCaseMetrics = new HashMap<>();
    }

    /** Adds additional pattern keys to the pull from the device. */
    protected void addKeys(String... keys) {
        mKeys.addAll(Arrays.asList(keys));
    }

    /**
     * Implementation of the method should allow to log the file, parse it for metrics to be put in
     * {@link DeviceMetricData}.
     *
     * @param key the option key associated to the file that was pulled.
     * @param metricFile the {@link File} pulled from the device matching the option key.
     * @param data the {@link DeviceMetricData} where metrics can be stored.
     */
    public abstract void processMetricFile(String key, File metricFile, DeviceMetricData data);

    /**
     * Implementation of the method should allow to log the directory, parse it for metrics to be
     * put in {@link DeviceMetricData}.
     *
     * @param key the option key associated to the directory that was pulled.
     * @param metricDirectory the {@link File} pulled from the device matching the option key.
     * @param data the {@link DeviceMetricData} where metrics can be stored.
     */
    public abstract void processMetricDirectory(
            String key, File metricDirectory, DeviceMetricData data);

    /**
     * Process the file associated with the matching key or directory name and update the data with
     * any additional metrics.
     *
     * @param data where the final metrics will be stored.
     * @param metrics where the key or directory name will be matched to the keys.
     */
    private void processMetricRequest(DeviceMetricData data, Map<String, Metric> metrics)
            throws DeviceNotAvailableException {
        Map<String, String> currentMetrics = TfMetricProtoUtil
                .compatibleConvert(metrics);
        currentMetrics.putAll(mTestCaseMetrics);
        if (mKeys.isEmpty() && mDirectoryKeys.isEmpty()) {
            return;
        }
        Map<ITestDevice, Integer> deviceUsers = new HashMap<>();
        if (!mKeys.isEmpty()) {
            for (ITestDevice device : getRealDevices()) {
                if (!TestDeviceState.ONLINE.equals(device.getDeviceState())) {
                    CLog.d(
                            "Device '%s' is in state '%s' skipping file puller",
                            device.getSerialNumber(), device.getDeviceState());
                    return;
                }
                deviceUsers.put(device, device.getCurrentUser());
            }
        }
        for (String key : mKeys) {
            Map<String, File> pulledMetrics = pullMetricFile(key, currentMetrics, deviceUsers);

            // Process all the metric files that matched the key pattern.
            for (Map.Entry<String, File> entry : pulledMetrics.entrySet()) {
                processMetricFile(entry.getKey(), entry.getValue(), data);
            }
        }

        for (String key : mDirectoryKeys) {
            Entry<String, File> pulledMetrics = pullMetricDirectory(key);
            if (pulledMetrics != null) {
                if (mCompressDirectory) {
                    File pulledDirectory = pulledMetrics.getValue();
                    if (pulledDirectory.isDirectory()) {
                        try {
                            File compressedFile = ZipUtil.createZip(pulledDirectory,
                                    getFileName(key));
                            processMetricFile(key, compressedFile, data);
                        } catch (IOException e) {
                            CLog.e("Unable to compress the directory.");
                        }
                        FileUtil.recursiveDelete(pulledDirectory);
                    }
                    continue;
                }
                processMetricDirectory(pulledMetrics.getKey(), pulledMetrics.getValue(), data);
            }
        }
    }

    /**
     * Return the last folder name from the path the in the device where the
     * directory is pulled.
     */
    private String getFileName(String key) {
        return key.substring(key.lastIndexOf("/")+1);
    }

    private Map<String, File> pullMetricFile(
            String pattern,
            final Map<String, String> currentMetrics,
            Map<ITestDevice, Integer> deviceUsers)
            throws DeviceNotAvailableException {
        Map<String, File> matchedFiles = new HashMap<>();
        Pattern p = Pattern.compile(pattern);

        for (Entry<String, String> entry : currentMetrics.entrySet()) {
            if (p.matcher(entry.getKey()).find()) {
                for (ITestDevice device : getRealDevices()) {
                    if (!shouldCollect(device)) {
                        continue;
                    }
                    try {
                        File attemptPull =
                                retrieveFile(device, entry.getValue(), deviceUsers.get(device));
                        if (attemptPull != null) {
                            if (mCleanUp) {
                                device.deleteFile(entry.getValue());
                            }
                            // Store all the keys that matches the pattern and the corresponding
                            // files pulled from the device.
                            matchedFiles.put(entry.getKey(), attemptPull);
                        }
                    } catch (RuntimeException e) {
                        CLog.e(
                                "Exception when pulling metric file '%s' from %s",
                                entry.getValue(), device.getSerialNumber());
                        CLog.e(e);
                    }
                }
            }
        }

        if (matchedFiles.isEmpty()) {
            // Not a hard failure, just nice to know
            CLog.d("Could not find a device file associated to pattern '%s'.", pattern);

        }
        return matchedFiles;
    }

    /**
     * Pull the file from the specified path in the device.
     *
     * @param device which has the file.
     * @param remoteFilePath location in the device.
     * @param userId the user id to pull from
     * @return File retrieved from the given path in the device.
     * @throws DeviceNotAvailableException
     */
    protected File retrieveFile(ITestDevice device, String remoteFilePath, int userId)
            throws DeviceNotAvailableException {
        return device.pullFile(remoteFilePath, userId);
    }

    /**
     * Pulls the directory and all its content from the device and save it in the host under the
     * metric_tmp folder.
     *
     * @param keyDirectory path to the source directory in the device.
     * @return Key,value pair of the directory name and path to the directory in the local host.
     */
    private Entry<String, File> pullMetricDirectory(String keyDirectory)
            throws DeviceNotAvailableException {
        try {
            File tmpDestDir =
                    FileUtil.createTempDir("metric_tmp", CurrentInvocation.getWorkFolder());
            for (ITestDevice device : getRealDevices()) {
                if (!shouldCollect(device)) {
                    continue;
                }
                try {
                    if (device.pullDir(keyDirectory, tmpDestDir)) {
                        if (mCleanUp) {
                            device.deleteFile(keyDirectory);
                        }
                        return new SimpleEntry<String, File>(keyDirectory, tmpDestDir);
                    }
                } catch (RuntimeException e) {
                    CLog.e(
                            "Exception when pulling directory '%s' from %s",
                            keyDirectory, device.getSerialNumber());
                    CLog.e(e);
                }
            }
        } catch (IOException ioe) {
            CLog.e("Exception while creating the local directory");
            CLog.e(ioe);
        }
        CLog.e("Could not find a device directory associated to path '%s'.", keyDirectory);
        return null;
    }

    private boolean shouldCollect(ITestDevice device) {
        TestDeviceState state = device.getDeviceState();
        if (!TestDeviceState.ONLINE.equals(state)) {
            CLog.d("Skip %s device is in state '%s'", this, state);
            return false;
        }
        return true;
    }
}
