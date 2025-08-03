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

package com.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.host.IHostOptions;
import com.android.tradefed.host.IHostOptions.PermitLimitType;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestLoggerReceiver;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.error.TestErrorIdentifier;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.QuotationAwareTokenizer;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.PythonVirtualenvHelper;

import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Target preparer to run arbitrary host commands before and after running the test. */
@OptionClass(alias = "run-host-command")
public class RunHostCommandTargetPreparer extends BaseTargetPreparer
        implements ITestLoggerReceiver {

    /** Placeholder to be replaced with real device serial number in commands */
    private static final String DEVICE_SERIAL_PLACEHOLDER = "$SERIAL";

    private static final String EXTRA_FILE_PATTERSTRING = "\\$EXTRA_FILE\\(([^()]+)\\)";

    private static final String BG_COMMAND_LOG_PREFIX = "bg_command_log_";

    @Option(name = "work-dir", description = "Working directory to be used when running commands.")
    private File mWorkDir = null;

    @Option(
        name = "host-setup-command",
        description =
                "Command to be run before the test. Can be repeated. "
                        + DEVICE_SERIAL_PLACEHOLDER
                        + " can be used as placeholder to be replaced "
                        + "with real device serial number at runtime."
    )
    private List<String> mSetUpCommands = new ArrayList<>();

    @Option(
        name = "host-teardown-command",
        description = "Command to be run after the test. Can be repeated."
    )
    private List<String> mTearDownCommands = new ArrayList<>();

    @Option(
        name = "host-background-command",
        description =
                "Background command to be run before the test. Can be repeated. "
                        + "They will be forced to terminate after the test. "
                        + DEVICE_SERIAL_PLACEHOLDER
                        + " can be used as placeholder to be replaced "
                        + "with real device serial number at runtime."
    )
    private List<String> mBgCommands = new ArrayList<>();

    @Option(name = "host-cmd-timeout", description = "Timeout for each command specified.")
    private Duration mTimeout = Duration.ofMinutes(1L);

    @Option(
            name = "use-flashing-permit",
            description = "Acquire a flashing permit before running commands.")
    private boolean mUseFlashingPermit = false;

    @Option(
            name = "python-virtualenv",
            description =
                "Activate existing python virtualenv created by"
                          + "PythonVirtualenvPreparer if set to True."
                          + "Do not activate otherwise"
    )
    private boolean mUseVenv = false;

    private List<Process> mBgProcesses = new ArrayList<>();
    private List<BgCommandLog> mBgCommandLogs = new ArrayList<>();
    private ITestLogger mLogger;
    private IRunUtil mRunUtil;

    /**
     * An interface simply wraps the OutputStream and InputStreamSource for the background command
     * log.
     */
    @VisibleForTesting
    interface BgCommandLog {
        OutputStream getOutputStream();

        InputStreamSource getInputStreamSource();

        String getName();
    }

    /** An implementation for BgCommandLog that is based on a file. */
    private static class BgCommandFileLog implements BgCommandLog {
        private File mFile;
        private OutputStream mOutputStream;
        private InputStreamSource mInputStreamSource;

        public BgCommandFileLog(File file) throws IOException {
            mFile = file;
            mOutputStream = new FileOutputStream(mFile);
            //The file will be deleted on cancel
            mInputStreamSource = new FileInputStreamSource(file, true);
        }

        @Override
        public OutputStream getOutputStream() {
            return mOutputStream;
        }

        @Override
        public InputStreamSource getInputStreamSource() {
            return mInputStreamSource;
        }

        @Override
        public String getName() {
            return mFile.getName();
        }
    }

    @Override
    public void setTestLogger(ITestLogger testLogger) {
        mLogger = testLogger;
    }

    /** {@inheritDoc} */
    @Override
    public final void setUp(TestInformation testInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        if (mWorkDir != null) {
            getRunUtil().setWorkingDir(mWorkDir);
        }
        ITestDevice device = testInfo.getDevice();
        IBuildInfo buildInfo = testInfo.getBuildInfo();

        if (mUseVenv) {
            File venvDir = buildInfo.getFile("VIRTUAL_ENV");
            if (venvDir != null && venvDir.exists()) {
                PythonVirtualenvHelper.activate(getRunUtil(), venvDir);
            } else {
                CLog.d("No virtualenv configured.");
            }
        }

        replaceSerialNumber(mSetUpCommands, device);
        replaceExtraFile(mSetUpCommands, buildInfo);
        try {
            if (mUseFlashingPermit) {
                getHostOptions().takePermit(PermitLimitType.CONCURRENT_FLASHER);
            }
            runCommandList(mSetUpCommands, device);
        } finally {
            if (mUseFlashingPermit) {
                getHostOptions().returnPermit(PermitLimitType.CONCURRENT_FLASHER);
            }
        }

        try {
            mBgCommandLogs = createBgCommandLogs();
            replaceSerialNumber(mBgCommands, device);
            replaceExtraFile(mBgCommands, buildInfo);
            runBgCommandList(mBgCommands, mBgCommandLogs);
        } catch (IOException e) {
            throw new TargetSetupError(
                    e.toString(),
                    device.getDeviceDescriptor(),
                    TestErrorIdentifier.HOST_COMMAND_FAILED);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void tearDown(TestInformation testInfo, Throwable e) throws DeviceNotAvailableException {
        ITestDevice device = testInfo.getDevice();
        replaceSerialNumber(mTearDownCommands, device);
        replaceExtraFile(mTearDownCommands, testInfo.getBuildInfo());
        try {
            if (mUseFlashingPermit) {
                getHostOptions().takePermit(PermitLimitType.CONCURRENT_FLASHER);
            }
            runCommandList(mTearDownCommands, device);
        } catch (TargetSetupError tse) {
            CLog.e(tse);
        } finally {
            if (mUseFlashingPermit) {
                getHostOptions().returnPermit(PermitLimitType.CONCURRENT_FLASHER);
            }
        }

        // Terminate background commands after test finished
        for (Process process : mBgProcesses) {
            process.destroy();
        }

        // Upload log of each background command use external logger.
        for (BgCommandLog log : mBgCommandLogs) {
            try {
                log.getOutputStream().close();
                mLogger.testLog(log.getName(), LogDataType.TEXT, log.getInputStreamSource());
                log.getInputStreamSource().close(); //Also delete the file
            } catch (IOException exception) {
                CLog.e("Failed to close background command log output stream.", exception);
            }
        }
    }

    /**
     * Sequentially runs command from specified list.
     *
     * @param commands list of commands to run.
     * @param device device being prepared
     */
    private void runCommandList(final List<String> commands, ITestDevice device)
            throws TargetSetupError {
        for (final String command : commands) {
            final CommandResult result =
                    getRunUtil()
                            .runTimedCmd(
                                    mTimeout.toMillis(),
                                    QuotationAwareTokenizer.tokenizeLine(command));
            switch (result.getStatus()) {
                case SUCCESS:
                    CLog.i(
                            "Command %s finished successfully, stdout = [%s], stderr = [%s].",
                            command, result.getStdout(), result.getStderr());
                    break;
                case FAILED:
                    throw new TargetSetupError(
                            String.format(
                                    "Command %s failed, stdout = [%s], stderr = [%s].",
                                    command, result.getStdout(), result.getStderr()),
                            device.getDeviceDescriptor(),
                            TestErrorIdentifier.HOST_COMMAND_FAILED);
                case TIMED_OUT:
                    throw new TargetSetupError(
                            String.format(
                                    "Command %s timed out, stdout = [%s], stderr = [%s].",
                                    command, result.getStdout(), result.getStderr()),
                            device.getDeviceDescriptor(),
                            TestErrorIdentifier.HOST_COMMAND_FAILED);
                case EXCEPTION:
                    throw new TargetSetupError(
                            String.format(
                                    "Exception occurred when running command %s, stdout = [%s],"
                                            + " stderr = [%s].",
                                    command, result.getStdout(), result.getStderr()),
                            device.getDeviceDescriptor(),
                            TestErrorIdentifier.HOST_COMMAND_FAILED);
            }
        }
    }

    /**
     * Sequentially runs background command from specified list.
     *
     * @param bgCommands list of commands to run.
     */
    private void runBgCommandList(
            final List<String> bgCommands, final List<BgCommandLog> bgCommandLogs)
            throws IOException {
        for (int i = 0; i < bgCommands.size(); i++) {
            String command = bgCommands.get(i);
            CLog.d("About to run host background command: %s", command);
            Process process =
                    getRunUtil()
                            .runCmdInBackground(
                                    List.of(QuotationAwareTokenizer.tokenizeLine(command)),
                                    bgCommandLogs.get(i).getOutputStream());
            if (process == null) {
                CLog.e("Failed to run command: %s", command);
                continue;
            }
            mBgProcesses.add(process);
        }
    }

    /**
     * For each command in the list, replace placeholder (if any) with real device serial number in
     * place.
     *
     * @param commands list of host commands
     * @param device device with which the host commands execute
     */
    private void replaceSerialNumber(final List<String> commands, ITestDevice device) {
        for (int i = 0; i < commands.size(); i++) {
            String command =
                    commands.get(i).replace(DEVICE_SERIAL_PLACEHOLDER, device.getSerialNumber());
            commands.set(i, command);
        }
    }

    /**
     * For each command in the list, replace placeholder (if any) with the file name indicated in
     * the build information
     *
     * @param commands list of host commands
     * @param buildInfo build artifact information
     */
    private void replaceExtraFile(final List<String> commands, IBuildInfo buildInfo) {
        Pattern pattern = Pattern.compile(EXTRA_FILE_PATTERSTRING);
        for (int i = 0; i < commands.size(); i++) {
            Matcher matcher = pattern.matcher(commands.get(i));
            StringBuffer command = new StringBuffer();

            while (matcher.find()) {
                String fileName = matcher.group(1);
                File file = buildInfo.getFile(fileName);
                if (file == null || !file.exists()) {
                    continue;
                }
                matcher.appendReplacement(command, file.getPath());
            }
            matcher.appendTail(command);

            commands.set(i, command.toString());
        }
    }

    /**
     * Gets instance of {@link IRunUtil}.
     *
     * @return instance of {@link IRunUtil}.
     */
    @VisibleForTesting
    IRunUtil getRunUtil() {
        if (mRunUtil == null) {
            mRunUtil = new RunUtil();
        }
        return mRunUtil;
    }

    /** @return {@link IHostOptions} instance used for flashing permits */
    @VisibleForTesting
    IHostOptions getHostOptions() {
        return GlobalConfiguration.getInstance().getHostOptions();
    }

    /**
     * Create a BgCommandLog object that is based on a temporary file for each background command
     *
     * @return A list of BgCommandLog object matching each command
     * @throws IOException
     */
    @VisibleForTesting
    List<BgCommandLog> createBgCommandLogs() throws IOException {
        List<BgCommandLog> bgCommandLogs = new ArrayList<>();
        for (String command : mBgCommands) {
            File file = FileUtil.createTempFile(BG_COMMAND_LOG_PREFIX, ".txt");
            CLog.d("Redirect output to %s for command: %s", file.getAbsolutePath(), command);
            bgCommandLogs.add(new BgCommandFileLog(file));
        }
        return bgCommandLogs;
    }
}
