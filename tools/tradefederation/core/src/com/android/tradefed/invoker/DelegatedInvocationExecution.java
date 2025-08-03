/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tradefed.invoker;

import com.android.tradefed.command.CommandRunner;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.proxy.AutomatedReporters;
import com.android.tradefed.config.proxy.TradefedDelegator;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.error.HarnessRuntimeException;
import com.android.tradefed.invoker.TestInvocation.Stage;
import com.android.tradefed.invoker.logger.CurrentInvocation;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.result.proto.StreamProtoReceiver;
import com.android.tradefed.service.TradefedFeatureServer;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.IRunUtil.EnvPriority;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.SubprocessExceptionParser;
import com.android.tradefed.util.SystemUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** {@link InvocationExecution} which delegate the execution to another Tradefed binary. */
public class DelegatedInvocationExecution extends InvocationExecution {

    /** If present the invocation is executing within a delegated mode */
    public static final String DELEGATED_MODE_VAR = "DELEGATED_MODE";

    /** Timeout to wait for the events received from subprocess to finish being processed. */
    private static final long EVENT_THREAD_JOIN_TIMEOUT_MS = 30 * 1000;

    private File mTmpDelegatedDir = null;
    private File mGlobalConfig = null;
    // Output reporting
    private File mStdoutFile = null;
    private File mStderrFile = null;
    private OutputStream mStderr = null;
    private OutputStream mStdout = null;

    @Override
    public void reportLogs(ITestDevice device, ITestLogger logger, Stage stage) {
        // Do nothing
    }

    @Override
    public boolean shardConfig(
            IConfiguration config,
            TestInformation testInfo,
            IRescheduler rescheduler,
            ITestLogger logger) {
        return false;
    }

    @Override
    public void doSetup(TestInformation testInfo, IConfiguration config, ITestLogger listener)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        // Do nothing
    }

    @Override
    public void runDevicePreInvocationSetup(
            IInvocationContext context, IConfiguration config, ITestLogger logger)
            throws DeviceNotAvailableException, TargetSetupError {
        // Do nothing
    }

    @Override
    public void runDevicePostInvocationTearDown(
            IInvocationContext context, IConfiguration config, Throwable exception) {
        // Do nothing
    }

    @Override
    public void doTeardown(
            TestInformation testInfo,
            IConfiguration config,
            ITestLogger logger,
            Throwable exception)
            throws Throwable {
        // Do nothing
    }

    @Override
    public void runTests(
            TestInformation info, IConfiguration config, ITestInvocationListener listener)
            throws Throwable {
        // Dump the delegated config for debugging
        File dumpConfig = FileUtil.createTempFile("delegated-config", ".xml");
        try (PrintWriter pw = new PrintWriter(dumpConfig)) {
            config.dumpXml(pw);
        }
        logAndCleanFile(dumpConfig, LogDataType.HARNESS_CONFIG, listener);

        if (config.getConfigurationObject(TradefedDelegator.DELEGATE_OBJECT) == null) {
            throw new ConfigurationException(
                    "Delegate object should not be null in DelegatedInvocation");
        }
        TradefedDelegator delegator =
                (TradefedDelegator)
                        config.getConfigurationObject(TradefedDelegator.DELEGATE_OBJECT);
        if (!delegator.getTfRootDir().exists() || !delegator.getTfRootDir().isDirectory()) {
            throw new ConfigurationException(
                    String.format(
                            "delegated-tf was misconfigured and doesn't point to a valid"
                                    + " location: %s",
                            delegator.getTfRootDir()),
                    InfraErrorIdentifier.OPTION_CONFIGURATION_ERROR);
        }
        List<String> commandLine = new ArrayList<>();
        commandLine.add(SystemUtil.getRunningJavaBinaryPath().getAbsolutePath());
        mTmpDelegatedDir =
                FileUtil.createTempDir("delegated-invocation", CurrentInvocation.getWorkFolder());
        commandLine.add(String.format("-Djava.io.tmpdir=%s", mTmpDelegatedDir.getAbsolutePath()));
        commandLine.add("-cp");
        // Add classpath
        commandLine.add(delegator.createClasspath());
        // Carry the updated TF_JAR_DIR to delegate, this will simulate tradefed.sh environment.
        commandLine.add(
                String.format("-DTF_JAR_DIR=%s", delegator.getTfRootDir().getAbsolutePath()));
        commandLine.add("com.android.tradefed.command.CommandRunner");
        // Add command line
        commandLine.addAll(Arrays.asList(delegator.getCommandLine()));

        try (StreamProtoReceiver receiver = createReceiver(listener, info.getContext())) {
            mStdoutFile = FileUtil.createTempFile("stdout_delegate_", ".log", mTmpDelegatedDir);
            mStderrFile = FileUtil.createTempFile("stderr_delegate_", ".log", mTmpDelegatedDir);
            mStderr = new FileOutputStream(mStderrFile);
            mStdout = new FileOutputStream(mStdoutFile);
            IRunUtil runUtil = createRunUtil(receiver.getSocketServerPort(), config);
            CommandResult result = null;
            RuntimeException runtimeException = null;
            CLog.d("Command line: %s", commandLine);
            try {
                result =
                        runUtil.runTimedCmd(
                                config.getCommandOptions().getInvocationTimeout(),
                                mStdout,
                                mStderr,
                                commandLine.toArray(new String[0]));
            } catch (RuntimeException e) {
                CLog.e("Delegated runtimedCmd threw an exception");
                CLog.e(e);
                runtimeException = e;
                result = new CommandResult(CommandStatus.EXCEPTION);
                result.setStdout(StreamUtil.getStackTrace(e));
            }
            boolean failedStatus = false;
            String stderrText;
            try {
                stderrText = FileUtil.readStringFromFile(mStderrFile);
            } catch (IOException e) {
                stderrText = "Could not read the stderr output from process.";
            }
            if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
                failedStatus = true;
                result.setStderr(stderrText);
            }
            boolean joinResult = receiver.joinReceiver(EVENT_THREAD_JOIN_TIMEOUT_MS);
            if (runtimeException != null) {
                throw runtimeException;
            }
            if (!joinResult) {
                if (!failedStatus) {
                    result.setStatus(CommandStatus.EXCEPTION);
                }
                result.setStderr(
                        String.format("Event receiver thread did not complete.:\n%s", stderrText));
            }
            receiver.completeModuleEvents();
            if (result.getStatus().equals(CommandStatus.TIMED_OUT)) {
                throw new HarnessRuntimeException(
                        "Delegated invocation timed out.", InfraErrorIdentifier.INVOCATION_TIMEOUT);
            }
            if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
                CLog.e(
                        "Sandbox finished with status: %s and exit code: %s",
                        result.getStatus(), result.getExitCode());
                SubprocessExceptionParser.handleStderrException(result);
            }
        } finally {
            StreamUtil.close(mStderr);
            StreamUtil.close(mStdout);
            logAndCleanFile(mStdoutFile, LogDataType.HARNESS_STD_LOG, listener);
            logAndCleanFile(mStderrFile, LogDataType.HARNESS_STD_LOG, listener);
            logAndCleanFile(mGlobalConfig, LogDataType.HARNESS_CONFIG, listener);
        }
    }

    @Override
    public void doCleanUp(IInvocationContext context, IConfiguration config, Throwable exception) {
        super.doCleanUp(context, config, exception);
        FileUtil.recursiveDelete(mTmpDelegatedDir);
        FileUtil.deleteFile(mGlobalConfig);
    }

    private IRunUtil createRunUtil(int port, IConfiguration config) throws IOException {
        IRunUtil runUtil = new RunUtil();
        // Handle the global configs for the subprocess
        runUtil.unsetEnvVariable(GlobalConfiguration.GLOBAL_CONFIG_VARIABLE);
        runUtil.unsetEnvVariable(GlobalConfiguration.GLOBAL_CONFIG_SERVER_CONFIG_VARIABLE);
        runUtil.setEnvVariablePriority(EnvPriority.SET);
        mGlobalConfig = createGlobalConfig();
        runUtil.setEnvVariable(
                GlobalConfiguration.GLOBAL_CONFIG_VARIABLE, mGlobalConfig.getAbsolutePath());
        runUtil.setEnvVariable(AutomatedReporters.PROTO_REPORTING_PORT, Integer.toString(port));
        // Set a variable to detect delegated mode
        runUtil.setEnvVariable(DELEGATED_MODE_VAR, "1");
        // Trigger the feature server to be restarted in the delegate
        // this ensures all the code is being delegated.
        runUtil.setEnvVariable(CommandRunner.START_FEATURE_SERVER, "1");
        ServerSocket s = new ServerSocket(0);
        s.setReuseAddress(true);
        int servicePort = s.getLocalPort();
        s.close();
        runUtil.setEnvVariable(
                TradefedFeatureServer.TF_SERVICE_PORT, Integer.toString(servicePort));
        return runUtil;
    }

    private StreamProtoReceiver createReceiver(
            ITestInvocationListener listener, IInvocationContext mainContext) throws IOException {
        StreamProtoReceiver receiver =
                new StreamProtoReceiver(
                        listener, mainContext, false, false, /* report logs */ false, "");
        return receiver;
    }

    private File createGlobalConfig() throws IOException {
        String[] configList =
                new String[] {
                    GlobalConfiguration.DEVICE_MANAGER_TYPE_NAME,
                    GlobalConfiguration.KEY_STORE_TYPE_NAME,
                    GlobalConfiguration.HOST_OPTIONS_TYPE_NAME,
                    GlobalConfiguration.SANDBOX_FACTORY_TYPE_NAME,
                    "android-build"
                };
        File filteredGlobalConfig =
                GlobalConfiguration.getInstance().cloneConfigWithFilter(configList);
        return filteredGlobalConfig;
    }

    /**
     * Log the content of given file to listener, then remove the file.
     *
     * @param fileToExport the {@link File} pointing to the file to log.
     * @param type the {@link LogDataType} of the data
     * @param listener the {@link ITestInvocationListener} where to report the test.
     */
    private void logAndCleanFile(
            File fileToExport, LogDataType type, ITestInvocationListener listener) {
        if (fileToExport == null) { return; }

        try (FileInputStreamSource inputStream = new FileInputStreamSource(fileToExport, true)) {
            listener.testLog(fileToExport.getName(), type, inputStream);
        }
    }
}
