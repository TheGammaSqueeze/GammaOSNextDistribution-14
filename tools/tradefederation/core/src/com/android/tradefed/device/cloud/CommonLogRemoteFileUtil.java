/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tradefed.device.cloud;

import com.android.tradefed.device.TestDeviceOptions;
import com.android.tradefed.device.TestDeviceOptions.InstanceType;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.ZipUtil;
import com.google.common.base.Strings;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This utility allows to avoid code duplication across the different remote device representation
 * for the remote log fetching logic of common files.
 */
public class CommonLogRemoteFileUtil {

    /** The directory where to find debug logs for a nested remote instance. */
    public static final String NESTED_REMOTE_LOG_DIR = "/home/%s/cuttlefish_runtime/";
    /** The directory where to find debug logs for an emulator instance. */
    public static final String EMULATOR_REMOTE_LOG_DIR = "/home/%s/log/";
    public static final String TOMBSTONES_ZIP_NAME = "tombstones-zip";

    /** The directory where to find emulator logs from Oxygen service. */
    public static final String OXYGEN_EMULATOR_LOG_DIR = "/tmp/device_launcher/";
    /** The directory where to find Oxygen device logs. */
    public static final String OXYGEN_CUTTLEFISH_LOG_DIR =
            "/tmp/cfbase/3/cuttlefish/instances/cvd-1/logs/";
    /**
     * The directory where to find Oxygen device runtime logs. Only use this if
     * OXYGEN_CUTTLEFISH_LOG_DIR is not found.
     */
    public static final String OXYGEN_RUNTIME_LOG_DIR = "/tmp/cfbase/3/cuttlefish_runtime/";

    /** The directory where to find goldfish logs from Oxygen service. */
    public static final String OXYGEN_GOLDFISH_LOG_DIR = "/tmp/android_platform_gf*/logs/";

    /** The directory where to find netsim logs from Oxygen service. */
    public static final String NETSIM_LOG_DIR = "/tmp/android/netsimd/";

    public static final String NETSIM_USER_LOG_DIR = "/tmp/android-%s/netsimd/";

    public static final List<KnownLogFileEntry> NETSIM_LOG_FILES = new ArrayList<>();

    public static final List<KnownLogFileEntry> OXYGEN_LOG_FILES = new ArrayList<>();
    /** For older version of cuttlefish, log files only exists in cuttlefish_runtime directory. */
    public static final List<KnownLogFileEntry> OXYGEN_LOG_FILES_FALLBACK = new ArrayList<>();

    public static final MultiMap<InstanceType, KnownLogFileEntry> KNOWN_FILES_TO_FETCH =
            new MultiMap<>();

    static {
        // Cuttlefish known files to collect
        KNOWN_FILES_TO_FETCH.put(
                InstanceType.CUTTLEFISH,
                new KnownLogFileEntry(
                        "/home/%s/fetcher_config.json", null, LogDataType.CUTTLEFISH_LOG));
        KNOWN_FILES_TO_FETCH.put(
                InstanceType.CUTTLEFISH,
                new KnownLogFileEntry(
                        NESTED_REMOTE_LOG_DIR + "kernel.log", null, LogDataType.CUTTLEFISH_LOG));
        KNOWN_FILES_TO_FETCH.put(
                InstanceType.CUTTLEFISH,
                new KnownLogFileEntry(
                        NESTED_REMOTE_LOG_DIR + "logcat", "full_gce_logcat", LogDataType.LOGCAT));
        KNOWN_FILES_TO_FETCH.put(
                InstanceType.CUTTLEFISH,
                new KnownLogFileEntry(
                        NESTED_REMOTE_LOG_DIR + "cuttlefish_config.json",
                        null,
                        LogDataType.CUTTLEFISH_LOG));
        KNOWN_FILES_TO_FETCH.put(
                InstanceType.CUTTLEFISH,
                new KnownLogFileEntry(
                        NESTED_REMOTE_LOG_DIR + "launcher.log",
                        "cuttlefish_launcher.log",
                        LogDataType.CUTTLEFISH_LOG));
        KNOWN_FILES_TO_FETCH.put(
                InstanceType.CUTTLEFISH,
                new KnownLogFileEntry(
                        NESTED_REMOTE_LOG_DIR + "crosvm_openwrt_boot.log",
                        null,
                        LogDataType.CUTTLEFISH_LOG));
        KNOWN_FILES_TO_FETCH.put(
                InstanceType.CUTTLEFISH,
                new KnownLogFileEntry(
                        NESTED_REMOTE_LOG_DIR + "crosvm_openwrt.log",
                        null,
                        LogDataType.CUTTLEFISH_LOG));
        KNOWN_FILES_TO_FETCH.put(
                InstanceType.CUTTLEFISH,
                new KnownLogFileEntry(
                        "/var/log/kern.log", "host_kernel.log", LogDataType.CUTTLEFISH_LOG));
        // Emulator known files to collect
        KNOWN_FILES_TO_FETCH.put(
                InstanceType.EMULATOR,
                new KnownLogFileEntry(
                        EMULATOR_REMOTE_LOG_DIR + "logcat.log",
                        "full_gce_emulator_logcat",
                        LogDataType.LOGCAT));
        KNOWN_FILES_TO_FETCH.put(
                InstanceType.EMULATOR,
                new KnownLogFileEntry(
                        EMULATOR_REMOTE_LOG_DIR + "adb.log", null, LogDataType.CUTTLEFISH_LOG));
        KNOWN_FILES_TO_FETCH.put(
                InstanceType.EMULATOR,
                new KnownLogFileEntry(
                        EMULATOR_REMOTE_LOG_DIR + "kernel.log", null, LogDataType.CUTTLEFISH_LOG));
        KNOWN_FILES_TO_FETCH.put(
                InstanceType.EMULATOR,
                new KnownLogFileEntry("/var/log/daemon.log", null, LogDataType.CUTTLEFISH_LOG));
        KNOWN_FILES_TO_FETCH.put(
                InstanceType.EMULATOR,
                new KnownLogFileEntry(
                        "/var/log/kern.log", "host_kernel.log", LogDataType.CUTTLEFISH_LOG));

        OXYGEN_LOG_FILES.add(new KnownLogFileEntry(OXYGEN_EMULATOR_LOG_DIR, null, LogDataType.DIR));
        OXYGEN_LOG_FILES.add(
                new KnownLogFileEntry(OXYGEN_CUTTLEFISH_LOG_DIR, null, LogDataType.DIR));
        OXYGEN_LOG_FILES.add(new KnownLogFileEntry(OXYGEN_GOLDFISH_LOG_DIR, null, LogDataType.DIR));
        NETSIM_LOG_FILES.add(new KnownLogFileEntry(NETSIM_LOG_DIR, null, LogDataType.DIR));
        NETSIM_LOG_FILES.add(new KnownLogFileEntry(NETSIM_USER_LOG_DIR, null, LogDataType.DIR));
        OXYGEN_LOG_FILES_FALLBACK.add(
                new KnownLogFileEntry(
                        OXYGEN_RUNTIME_LOG_DIR + "launcher.log", null, LogDataType.CUTTLEFISH_LOG));
        OXYGEN_LOG_FILES_FALLBACK.add(
                new KnownLogFileEntry(
                        OXYGEN_RUNTIME_LOG_DIR + "crosvm_openwrt_boot.log",
                        null,
                        LogDataType.CUTTLEFISH_LOG));
        OXYGEN_LOG_FILES_FALLBACK.add(
                new KnownLogFileEntry(
                        OXYGEN_RUNTIME_LOG_DIR + "crosvm_openwrt.log",
                        null,
                        LogDataType.CUTTLEFISH_LOG));
        OXYGEN_LOG_FILES_FALLBACK.add(
                new KnownLogFileEntry(
                        OXYGEN_RUNTIME_LOG_DIR + "vdl_stdout.txt",
                        null,
                        LogDataType.CUTTLEFISH_LOG));
        OXYGEN_LOG_FILES_FALLBACK.add(
                new KnownLogFileEntry(
                        OXYGEN_RUNTIME_LOG_DIR + "kernel.log", null, LogDataType.CUTTLEFISH_LOG));
        OXYGEN_LOG_FILES_FALLBACK.add(
                new KnownLogFileEntry(
                        OXYGEN_RUNTIME_LOG_DIR + "logcat", null, LogDataType.CUTTLEFISH_LOG));
    }

    /** A representation of a known log entry for remote devices. */
    public static class KnownLogFileEntry {
        public String path;
        public String logName;
        public LogDataType type;

        KnownLogFileEntry(String path, String logName, LogDataType type) {
            this.path = path;
            this.logName = logName;
            this.type = type;
        }
    }

    /**
     * Fetch and log the commonly known files from remote instances.
     *
     * @param testLogger The {@link ITestLogger} where to log the files.
     * @param gceAvd The descriptor of the remote instance.
     * @param options The {@link TestDeviceOptions} describing the device options
     * @param runUtil A {@link IRunUtil} to execute commands.
     */
    public static void fetchCommonFiles(
            ITestLogger testLogger,
            GceAvdInfo gceAvd,
            TestDeviceOptions options,
            IRunUtil runUtil) {
        if (gceAvd == null || gceAvd.hostAndPort() == null) {
            CLog.e("GceAvdInfo or its host setting was null, cannot collect remote files.");
            return;
        }
        List<KnownLogFileEntry> toFetch = new ArrayList<>(NETSIM_LOG_FILES);
        if (options.useOxygen()) {
            // Override the list of logs to collect when the device is hosted by Oxygen service.
            toFetch.addAll(OXYGEN_LOG_FILES);
            if (!RemoteFileUtil.doesRemoteFileExist(
                    gceAvd, options, runUtil, 60000, OXYGEN_CUTTLEFISH_LOG_DIR)) {
                toFetch.addAll(OXYGEN_LOG_FILES_FALLBACK);
            }
        } else {
            boolean reported = false;
            for (GceAvdInfo.LogFileEntry entry : gceAvd.getLogs()) {
                if (logRemoteFile(
                        testLogger,
                        gceAvd,
                        options,
                        runUtil,
                        entry.path,
                        entry.type,
                        Strings.isNullOrEmpty(entry.name) ? null : entry.name)) {
                    reported = true;
                }
            }
            if (!reported) {
                CLog.i("GceAvdInfo does not contain logs. Fall back to known log files.");
                List<KnownLogFileEntry> knownFileFetch =
                        KNOWN_FILES_TO_FETCH.get(options.getInstanceType());
                if (knownFileFetch != null) {
                    toFetch.addAll(knownFileFetch);
                }
            }
        }
        for (KnownLogFileEntry entry : toFetch) {
            logRemoteFile(
                    testLogger,
                    gceAvd,
                    options,
                    runUtil,
                    // Default fetch rely on main user
                    String.format(entry.path, options.getInstanceUser()),
                    entry.type,
                    entry.logName);
        }

        if (options.getRemoteFetchFilePattern().isEmpty()) {
            return;
        }
        for (String file : options.getRemoteFetchFilePattern()) {
            // TODO: Improve type of files.
            logRemoteFile(
                    testLogger, gceAvd, options, runUtil, file, LogDataType.CUTTLEFISH_LOG, null);
        }
    }

    /**
     * Execute a command to validate the ssh connection to the remote GCE instance.
     *
     * @param gceAvd The {@link GceAvdInfo} that describe the device.
     * @param options a {@link TestDeviceOptions} describing the device options to be used for the
     *     GCE device.
     * @param runUtil a {@link IRunUtil} to execute commands.
     * @return A boolean which indicate whether the remote GCE is reachable by ssh.
     */
    public static boolean isRemoteGceReachableBySsh(
            GceAvdInfo gceAvd, TestDeviceOptions options, IRunUtil runUtil) {
        CommandStatus sshAttemptStatus =
                RemoteSshUtil.remoteSshCommandExec(gceAvd, options, runUtil, 10000, "exit")
                        .getStatus();
        if (!CommandStatus.SUCCESS.equals(sshAttemptStatus)) {
            CLog.e(
                    String.format(
                            "Unable to ssh to the remote GCE, ssh failed with status %s.",
                            sshAttemptStatus));
            return false;
        }
        return true;
    }

    /**
     * Execute a command on remote instance and log its output
     *
     * @param testLogger The {@link ITestLogger} where to log the files.
     * @param gceAvd The descriptor of the remote instance.
     * @param options The {@link TestDeviceOptions} describing the device options
     * @param runUtil A {@link IRunUtil} to execute commands.
     * @param logName the log name to use when reporting to the {@link ITestLogger}
     * @param remoteCommand the command line to be executed on remote instance
     */
    public static void logRemoteCommandOutput(
            ITestLogger testLogger,
            GceAvdInfo gceAvd,
            TestDeviceOptions options,
            IRunUtil runUtil,
            String logName,
            String... remoteCommand) {
        if (gceAvd == null || gceAvd.hostAndPort() == null) {
            CLog.e("GceAvdInfo or its host setting was null, cannot collect remote files.");
            return;
        }
        CommandResult commandResult =
                GceManager.remoteSshCommandExecution(
                        gceAvd, options, runUtil, 60000, remoteCommand);
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("Command: %s\n", Arrays.asList(remoteCommand)));
        builder.append(
                String.format(
                        "Exit code: %d, Status: %s\n",
                        commandResult.getExitCode(), commandResult.getStatus()));
        builder.append(String.format("stdout:\n%s\n", commandResult.getStdout()));
        builder.append(String.format("stderr:\n%s\n", commandResult.getStderr()));
        testLogger.testLog(
                logName,
                LogDataType.CUTTLEFISH_LOG,
                new ByteArrayInputStreamSource(builder.toString().getBytes()));
    }

    /**
     * Fetch and log the tombstones from the remote instance.
     *
     * @param testLogger The {@link ITestLogger} where to log the files.
     * @param gceAvd The descriptor of the remote instance.
     * @param options The {@link TestDeviceOptions} describing the device options
     * @param runUtil A {@link IRunUtil} to execute commands.
     */
    public static void fetchTombstones(
            ITestLogger testLogger,
            GceAvdInfo gceAvd,
            TestDeviceOptions options,
            IRunUtil runUtil) {
        if (gceAvd == null || gceAvd.hostAndPort() == null) {
            CLog.e("GceAvdInfo or its host setting was null, cannot collect remote files.");
            return;
        }
        InstanceType type = options.getInstanceType();
        if (!InstanceType.CUTTLEFISH.equals(type) && !InstanceType.REMOTE_AVD.equals(type)) {
            return;
        }
        String path =
                String.format("/home/%s/cuttlefish_runtime/tombstones", options.getInstanceUser());
        for (GceAvdInfo.LogFileEntry entry : gceAvd.getLogs()) {
            if (entry.name.equals(TOMBSTONES_ZIP_NAME)) {
                path = entry.path;
                break;
            }
        }
        String pattern = path + "/*";
        CommandResult resultList =
                GceManager.remoteSshCommandExecution(
                        gceAvd, options, runUtil, 60000, "ls", "-A1", pattern);
        if (!CommandStatus.SUCCESS.equals(resultList.getStatus())) {
            CLog.e("Failed to list the tombstones: %s", resultList.getStderr());
            return;
        }
        if (resultList.getStdout().split("\n").length <= 0) {
            return;
        }
        File tombstonesDir = RemoteFileUtil.fetchRemoteDir(gceAvd, options, runUtil, 120000, path);
        if (tombstonesDir == null) {
            CLog.w("No tombstones directory was pulled.");
            return;
        }
        try {
            File zipTombstones = ZipUtil.createZip(tombstonesDir);
            try (InputStreamSource source = new FileInputStreamSource(zipTombstones, true)) {
                testLogger.testLog(TOMBSTONES_ZIP_NAME, LogDataType.TOMBSTONEZ, source);
            }
        } catch (IOException e) {
            CLog.e("Failed to zip the tombstones:");
            CLog.e(e);
        }
    }

    /**
     * Captures a log from the remote destination.
     *
     * @param testLogger The {@link ITestLogger} where to log the files.
     * @param gceAvd The descriptor of the remote instance.
     * @param options The {@link TestDeviceOptions} describing the device options
     * @param runUtil A {@link IRunUtil} to execute commands.
     * @param fileToRetrieve The remote path to the file to pull.
     * @param logType The expected type of the pulled log.
     * @param baseName The base name that will be used to log the file, if null the actually file
     *     name will be used.
     * @return whether the file is logged successfully.
     */
    private static boolean logRemoteFile(
            ITestLogger testLogger,
            GceAvdInfo gceAvd,
            TestDeviceOptions options,
            IRunUtil runUtil,
            String fileToRetrieve,
            LogDataType logType,
            String baseName) {
        if (baseName != null && baseName.startsWith(TOMBSTONES_ZIP_NAME)) {
            // TODO(b/154175542): Refactor fetchTombstones.
            return false;
        }
        return GceManager.logNestedRemoteFile(
                testLogger, gceAvd, options, runUtil, fileToRetrieve, logType, baseName);
    }
}
