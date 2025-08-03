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

package com.android.tradefed.util;

import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.error.HarnessException;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.error.InfraErrorIdentifier;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

/** A Utility class to execute device actions. */
public class DeviceActionUtil {

    private static final String BUNDLETOOL_FLAG = "--da_bundletool";
    private static final String CREDENTIAL_FLAG = "--da_cred_file";
    private static final String ADB_FLAG = "--adb";
    private static final String AAPT_FLAG = "--aapt";
    private static final String JAVA_BIN_FLAG = "--java_command_path";
    private static final String GEN_DIR_FLAG = "--da_gen_file_dir";
    private static final String TMP_DIR_FLAG = "--tmp_dir_root";
    private static final String GOOGLE_APPLICATION_CREDENTIALS = "GOOGLE_APPLICATION_CREDENTIALS";
    private static final String PATH = "PATH";
    private static final String HOST_LOG = "host_log_";
    private static final String DA_TMP_DIR = "da_tmp_dir";
    private static final String DA_GEN_DIR = "da_gen_dir";
    private static final String ANY_TXT_FILE_PATTERN = ".*\\.txt";
    private static final long EXECUTION_TIMEOUT = Duration.ofHours(1).toMillis();

    private final File mDeviceActionMainJar;
    private final String[] mConfigFlags;
    private final IRunUtil mRunUtil;
    private final File mGenDir;

    /** Commands for device action. */
    public enum Command {
        INSTALL_MAINLINE("install_mainline"),
        RESET("reset");

        private final String cmdName;

        Command(String cmdName) {
            this.cmdName = cmdName;
        }
    }

    /** Exception for config error. */
    public static class DeviceActionConfigError extends HarnessException {
        private static final long serialVersionUID = 2202987086655357212L;

        public DeviceActionConfigError(String message, @Nullable Throwable cause) {
            super(message, cause, InfraErrorIdentifier.INTERNAL_CONFIG_ERROR);
        }

        public DeviceActionConfigError(String message) {
            super(message, InfraErrorIdentifier.INTERNAL_CONFIG_ERROR);
        }
    }

    @VisibleForTesting
    DeviceActionUtil(
            File deviceActionMainJar, String[] configFlags, IRunUtil runUtil, File genDir) {
        mDeviceActionMainJar = deviceActionMainJar;
        mConfigFlags = configFlags;
        mRunUtil = runUtil;
        mGenDir = genDir;
    }

    /** Creates an instance. */
    public static DeviceActionUtil create(File deviceActionMainJar, File bundletoolJar)
            throws DeviceActionConfigError {
        checkFile(deviceActionMainJar);
        checkFile(bundletoolJar);
        File genDir = createDir(DA_GEN_DIR);
        File tmpDir = createDir(DA_TMP_DIR);
        final String[] flags =
                createConfigFlags(
                        bundletoolJar,
                        getCredential(),
                        genDir,
                        tmpDir,
                        getAdb(),
                        getAapt(),
                        SystemUtil.getRunningJavaBinaryPath());
        return new DeviceActionUtil(deviceActionMainJar, flags, new RunUtil(), genDir);
    }

    /**
     * Executes a device action command.
     *
     * @param command to execute.
     * @param deviceId of the device.
     * @param actionArgs action args for the {@code command}.
     */
    public CommandResult execute(Command command, String deviceId, List<String> actionArgs) {
        return execute(command, createActionFlags(deviceId, actionArgs));
    }

    /** Generates the host log file. */
    public void generateLogFile(CommandResult result) throws IOException {
        File logFile = FileUtil.createTempFile(HOST_LOG, ".txt", mGenDir);
        FileUtil.writeToFile(result.getStdout(), logFile);
        FileUtil.writeToFile(result.getStderr(), logFile, /* append= */ true);
    }

    /** Saves all generated files to test logs. */
    public void saveToLogs(Command cmd, ITestLogger testLogger) throws IOException {
        for (String filePath : FileUtil.findFiles(mGenDir, ANY_TXT_FILE_PATTERN)) {
            File file = new File(filePath);
            try (InputStreamSource inputStreamSource = new FileInputStreamSource(file)) {
                String dataName =
                        String.format("da_%s_%s", cmd.name(), file.getName().split("\\.", 2)[0]);
                testLogger.testLog(dataName, LogDataType.TEXT, inputStreamSource);
            }
        }
    }

    /** Executes a device action command. */
    private CommandResult execute(Command command, List<String> args) {
        ArrayList<String> cmd =
                new ArrayList<>(
                        Arrays.asList(
                                SystemUtil.getRunningJavaBinaryPath().getAbsolutePath(),
                                "-Djava.util.logging.config.class=com.google.common.logging.GoogleConsoleLogConfig",
                                "-Dgoogle.debug_log_levels=*=INFO",
                                "-jar",
                                mDeviceActionMainJar.getAbsolutePath(),
                                command.cmdName));
        cmd.addAll(Arrays.asList(mConfigFlags));
        cmd.addAll(args);
        return mRunUtil.runTimedCmd(EXECUTION_TIMEOUT, cmd.toArray(new String[0]));
    }

    @VisibleForTesting
    static String[] createConfigFlags(
            File bundletoolJar,
            File credential,
            File genDir,
            File tmpDir,
            File adb,
            File aapt,
            File java) {
        return new String[] {
            BUNDLETOOL_FLAG,
            bundletoolJar.getAbsolutePath(),
            CREDENTIAL_FLAG,
            credential.getAbsolutePath(),
            GEN_DIR_FLAG,
            genDir.getAbsolutePath(),
            TMP_DIR_FLAG,
            tmpDir.getAbsolutePath(),
            ADB_FLAG,
            adb.getAbsolutePath(),
            AAPT_FLAG,
            aapt.getAbsolutePath(),
            JAVA_BIN_FLAG,
            java.getAbsolutePath()
        };
    }

    private static List<String> createActionFlags(String deviceId, List<String> args) {
        ArrayList<String> flags = new ArrayList<>(Arrays.asList("--device1", "serial=" + deviceId));
        for (String arg : args) {
            flags.addAll(Arrays.asList("--action", arg));
        }
        return flags;
    }

    private static void checkFile(File file) throws DeviceActionConfigError {
        if (file == null || !file.exists() || !file.isFile()) {
            throw new DeviceActionConfigError("Missing file " + file);
        }
    }

    private static File getAdb() throws DeviceActionConfigError {
        File adbFile = new File(GlobalConfiguration.getDeviceManagerInstance().getAdbPath());
        if (!adbFile.exists()) {
            // Assume adb is in the PATH.
            adbFile = findExecutableOnPath("adb");
        }
        return adbFile;
    }

    private static File getAapt() throws DeviceActionConfigError {
        // Assume aapt2 is in the PATH. @see doc for {@link AaptParser}.
        return findExecutableOnPath("aapt2");
    }

    private static File getCredential() throws DeviceActionConfigError {
        File credentialFile = new File(System.getenv(GOOGLE_APPLICATION_CREDENTIALS));
        return Optional.of(credentialFile)
                .filter(File::exists)
                .orElseThrow(
                        () ->
                                new DeviceActionConfigError(
                                        "Missing GOOGLE_APPLICATION_CREDENTIALS"));
    }

    private static File createDir(String prefix) throws DeviceActionConfigError {
        try {
            return FileUtil.createTempDir(prefix);
        } catch (IOException e) {
            throw new DeviceActionConfigError("Failed to create " + prefix, e);
        }
    }

    private static File findExecutableOnPath(String name) throws DeviceActionConfigError {
        return Splitter.on(File.pathSeparator).splitToList(System.getenv(PATH)).stream()
                .map(dir -> new File(dir, name))
                .filter(f -> f.isFile() && f.canExecute())
                .findFirst()
                .orElseThrow(
                        () -> new DeviceActionConfigError("Failed to find the executable " + name));
    }
}
