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

package com.android.tradefed.testtype;

import com.android.annotations.VisibleForTesting;
import com.android.tradefed.command.CommandFileParser;
import com.android.tradefed.command.CommandFileParser.CommandLine;
import com.android.tradefed.command.CommandOptions;
import com.android.tradefed.command.CommandScheduler;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.SandboxConfigurationFactory;
import com.android.tradefed.config.proxy.TradefedDelegator;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.invoker.tracing.CloseableTraceScope;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.ITestLifeCycleReceiver;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.sandbox.ISandbox;
import com.android.tradefed.sandbox.TradefedSandbox;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.QuotationAwareTokenizer;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.executor.ParallelDeviceExecutor;
import com.android.tradefed.util.keystore.DryRunKeyStore;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Run noisy dry run on a command file.
 */
public class NoisyDryRunTest implements IRemoteTest {

    private static final long SLEEP_INTERVAL_MILLI_SEC = 5 * 1000;

    @Option(name = "cmdfile", description = "The cmdfile to run noisy dry run on.")
    private File mCmdfile = null;

    @Option(name = "timeout",
            description = "The timeout to wait cmd file be ready.",
            isTimeVal = true)
    private long mTimeoutMilliSec = 0;

    @Override
    public void run(TestInformation testInfo, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        List<CommandLine> commands = testCommandFile(listener, mCmdfile);
        if (commands != null) {
            testCommandLines(listener, commands);
        }
    }

    private List<CommandLine> testCommandFile(ITestInvocationListener listener, File file) {
        listener.testRunStarted(NoisyDryRunTest.class.getCanonicalName() + "_parseFile", 1);
        TestDescription parseFileTest =
                new TestDescription(NoisyDryRunTest.class.getCanonicalName(), "parseFile");
        listener.testStarted(parseFileTest);
        CommandFileParser parser = new CommandFileParser();
        try (CloseableTraceScope ignored = new CloseableTraceScope("parseCommandFile")) {
            checkFileWithTimeout(file);
            return parser.parseFile(file);
        } catch (IOException | ConfigurationException e) {
            listener.testFailed(parseFileTest, StreamUtil.getStackTrace(e));
            return null;
        } finally {
            listener.testEnded(parseFileTest, new HashMap<String, Metric>());
            listener.testRunEnded(0, new HashMap<String, Metric>());
        }
    }

    /**
     * If the file doesn't exist, we want to wait a while and check.
     *
     * @param file
     * @throws IOException
     */
    @VisibleForTesting
    void checkFileWithTimeout(File file) throws IOException {
        long timeout = currentTimeMillis() + mTimeoutMilliSec;
        boolean canRead = false;
        while (!(canRead = checkFile(file)) && currentTimeMillis() < timeout) {
            CLog.w("Can not read %s, wait and recheck.", file.getAbsoluteFile());
            sleep();
        }
        if (!canRead) {
            throw new IOException(String.format("Can not read %s.", file.getAbsoluteFile()));
        }
    }

    /** Check if the file is readable or not. */
    private boolean checkFile(File file) {
        if (!file.exists()) {
            CLog.w("%s doesn't exist.", file.getAbsoluteFile());
            return false;
        }
        if (!file.canRead()) {
            CLog.w("No read access to %s.", file.getAbsoluteFile());
            return false;
        }
        try {
            FileUtil.readStringFromFile(file);
        } catch (IOException e) {
            CLog.w("Fail to read %s.", file.getAbsoluteFile());
            return false;
        }
        return true;
    }

    @VisibleForTesting
    long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    @VisibleForTesting
    void sleep() {
        RunUtil.getDefault().sleep(SLEEP_INTERVAL_MILLI_SEC);
    }

    private void testCommandLines(ITestInvocationListener listener, List<CommandLine> commands) {
        listener.testRunStarted(NoisyDryRunTest.class.getCanonicalName() + "_parseCommands",
                commands.size());
        StoreAndForwardTestCases forwarder = new StoreAndForwardTestCases(listener);
        ParallelDeviceExecutor<Boolean> executor =
                new ParallelDeviceExecutor<Boolean>(Math.min(20, commands.size()));
        List<Callable<Boolean>> callableTasks = new ArrayList<>();
        for (int i = 0; i < commands.size(); ++i) {
            final int j = i;
            Callable<Boolean> callableTask =
                    () -> {
                        String[] args = commands.get(j).asArray();
                        parseOneConfig(forwarder, args, j);
                        return true;
                    };
            callableTasks.add(callableTask);
        }
        // No timeout
        executor.invokeAll(callableTasks, 0, TimeUnit.HOURS);
        listener.testRunEnded(0, new HashMap<String, Metric>());
    }

    private void parseOneConfig(ITestLifeCycleReceiver listener, String[] args, int i) {
        TestDescription parseCmdTest =
                new TestDescription(NoisyDryRunTest.class.getCanonicalName(), "parseCommand" + i);
        try (CloseableTraceScope ignored = new CloseableTraceScope(parseCmdTest.toString())) {
            listener.testStarted(parseCmdTest);

            String cmdLine = QuotationAwareTokenizer.combineTokens(args);
            try {
                TradefedDelegator delegator = CommandScheduler.checkDelegation(args);
                if (delegator.shouldUseDelegation()) {
                    // TODO: Add some validation of delegated config.
                    return;
                }

                if (cmdLine.contains("--" + CommandOptions.USE_SANDBOX)) {
                    // Handle the sandboxed command use case.
                    testSandboxCommand(args);
                } else {
                    // Use dry run keystore to always work for any keystore.
                    // FIXME: the DryRunKeyStore is a temporary fixed until each config can be
                    // validated against its own keystore.
                    IConfiguration config =
                            ConfigurationFactory.getInstance()
                                    .createConfigurationFromArgs(args, null, new DryRunKeyStore());
                    // Do not resolve dynamic files
                    config.validateOptions();
                }
            } catch (ConfigurationException e) {
                String errorMessage = String.format("Failed to parse command line: %s.", cmdLine);
                CLog.e(errorMessage);
                CLog.e(e);
                listener.testFailed(
                        parseCmdTest,
                        String.format("%s\n%s", errorMessage, StreamUtil.getStackTrace(e)));
            } finally {
                listener.testEnded(parseCmdTest, new HashMap<String, Metric>());
            }
        }
    }

    /** Test loading a sandboxed command. */
    public void testSandboxCommand(String[] args) throws ConfigurationException {
        // This only partially check the sandbox setup. It only checks that the NON_VERSIONED part
        // of the configuration is fine.
        // TODO(b/75033502, b/110545254): also run the noisy dry run in the sandbox.
        File globalConfig = null;
        try {
            globalConfig = FileUtil.createTempFile("noisy_dry_run_global_config", ".xml");
            FileUtil.writeToFile("<configuration></configuration>", globalConfig);
            IConfiguration config =
                    SandboxConfigurationFactory.getInstance()
                            .createConfigurationFromArgs(
                                    args,
                                    new DryRunKeyStore(),
                                    createSandbox(),
                                    createRunUtil(),
                                    globalConfig,
                                    true /*skip java check */);
            // Do not resolve dynamic files
            config.validateOptions();
        } catch (IOException e) {
            throw new ConfigurationException(e.getMessage(), e);
        } finally {
            FileUtil.deleteFile(globalConfig);
        }
    }

    /** Returns a {@link IRunUtil} implementation. */
    @VisibleForTesting
    IRunUtil createRunUtil() {
        return new RunUtil();
    }

    /** Returns the {@link ISandbox} implementation to tests the command. */
    @VisibleForTesting
    ISandbox createSandbox() {
        return new TradefedSandbox();
    }

    private class StoreAndForwardTestCases implements ITestLifeCycleReceiver {

        private final ITestInvocationListener mForwarder;
        private Map<TestDescription, String> mTestTracker =
                Collections.synchronizedMap(new HashMap<>());

        public StoreAndForwardTestCases(ITestInvocationListener forwarder) {
            mForwarder = forwarder;
        }

        @Override
        public void testStarted(TestDescription test) {
            mTestTracker.put(test, null);
        }

        @Override
        public void testFailed(TestDescription test, String trace) {
            mTestTracker.put(test, trace);
        }

        @Override
        public void testEnded(TestDescription test, HashMap<String, Metric> metrics) {
            synchronized (mForwarder) {
                mForwarder.testStarted(test);
                if (mTestTracker.get(test) != null) {
                    mForwarder.testFailed(test, mTestTracker.get(test));
                }
                mForwarder.testEnded(test, metrics);
            }
            mTestTracker.remove(test);
        }
    }
}
