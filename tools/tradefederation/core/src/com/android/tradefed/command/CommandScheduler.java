/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.tradefed.command;

import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.Log;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.clearcut.ClearcutClient;
import com.android.tradefed.command.CommandFileParser.CommandLine;
import com.android.tradefed.command.CommandFileWatcher.ICommandFileListener;
import com.android.tradefed.command.CommandRunner.ExitCode;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.config.ArgsOptionParser;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.DynamicRemoteFileResolver;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.config.IDeviceConfiguration;
import com.android.tradefed.config.IGlobalConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.RetryConfigurationFactory;
import com.android.tradefed.config.SandboxConfigurationFactory;
import com.android.tradefed.config.proxy.ProxyConfiguration;
import com.android.tradefed.config.proxy.TradefedDelegator;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.DeviceManager;
import com.android.tradefed.device.DeviceManager.FastbootDevice;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceUnresponsiveException;
import com.android.tradefed.device.FreeDeviceState;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.IDeviceMonitor;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.RecoveryMode;
import com.android.tradefed.device.NoDeviceException;
import com.android.tradefed.device.NullDevice;
import com.android.tradefed.device.RemoteAndroidDevice;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.error.HarnessRuntimeException;
import com.android.tradefed.host.IHostOptions;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.IRescheduler;
import com.android.tradefed.invoker.ITestInvocation;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInvocation;
import com.android.tradefed.invoker.shard.ParentShardReplicate;
import com.android.tradefed.invoker.tracing.ActiveTrace;
import com.android.tradefed.invoker.tracing.CloseableTraceScope;
import com.android.tradefed.invoker.tracing.TracingLogger;
import com.android.tradefed.log.ILogRegistry.EventType;
import com.android.tradefed.log.LogRegistry;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ConsoleResultReporter;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ILogSaverListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.result.LogSaverResultForwarder;
import com.android.tradefed.result.ResultForwarder;
import com.android.tradefed.result.error.ErrorIdentifier;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.result.suite.SuiteResultReporter;
import com.android.tradefed.sandbox.ISandbox;
import com.android.tradefed.service.TradefedFeatureServer;
import com.android.tradefed.service.management.DeviceManagementGrpcServer;
import com.android.tradefed.service.management.TestInvocationManagementServer;
import com.android.tradefed.targetprep.DeviceFailedToBootError;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.SubprocessTfLauncher;
import com.android.tradefed.testtype.suite.retry.RetryRescheduler;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.Pair;
import com.android.tradefed.util.QuotationAwareTokenizer;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.SystemUtil;
import com.android.tradefed.util.TableFormatter;
import com.android.tradefed.util.TimeUtil;
import com.android.tradefed.util.hostmetric.IHostMonitor;
import com.android.tradefed.util.hostmetric.IHostMonitor.HostDataPoint;
import com.android.tradefed.util.hostmetric.IHostMonitor.HostMetricType;
import com.android.tradefed.util.keystore.IKeyStoreClient;
import com.android.tradefed.util.keystore.IKeyStoreFactory;
import com.android.tradefed.util.keystore.KeyStoreException;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A scheduler for running TradeFederation commands across all available devices.
 *
 * <p>Will attempt to prioritize commands to run based on a total running count of their execution
 * time. e.g. infrequent or fast running commands will get prioritized over long running commands.
 *
 * <p>Runs forever in background until shutdown.
 */
public class CommandScheduler extends Thread implements ICommandScheduler, ICommandFileListener {

    /** the queue of commands ready to be executed. */
    private List<ExecutableCommand> mReadyCommands;

    private Set<ExecutableCommand> mUnscheduledWarning;

    /** the queue of commands sleeping. */
    private Set<ExecutableCommand> mSleepingCommands;

    /** the queue of commands current executing. */
    private Set<ExecutableCommand> mExecutingCommands;

    /** map of device to active invocation threads */
    private Map<IInvocationContext, InvocationThread> mInvocationThreadMap;
    /** Track invocation that are done and terminating */
    private Map<IInvocationContext, InvocationThread> mInvocationThreadMapTerminating;

    /** timer for scheduling commands to be re-queued for execution */
    private ScheduledThreadPoolExecutor mCommandTimer;

    private CommandFileWatcher mCommandFileWatcher = null;

    /** latch used to notify other threads that this thread is running */
    private final CountDownLatch mRunLatch;

    /** Maximum time to wait for adb to initialize and get the physical devices discovered */
    private static final long ADB_INIT_TIME_MS = 25;

    /** used to assign unique ids to each CommandTracker created */
    private int mCurrentCommandId = 0;

    /** flag for instructing scheduler to exit when no commands are present */
    private boolean mShutdownOnEmpty = false;
    /** Prevent new tests from being scheduled. */
    private boolean mStopScheduling = false;

    private boolean mStarted = false;

    private WaitObj mCommandProcessWait = new WaitObj();

    /** The last {@link InvocationThread} that ran error code and error stack */
    private ExitCode mLastInvocationExitCode = ExitCode.NO_ERROR;

    private Throwable mLastInvocationThrowable = null;

    /** Client to report metric data of the harness. */
    private ClearcutClient mClient = null;

    @Option(
            name = "reload-cmdfiles",
            description = "Whether to enable the command file autoreload mechanism")
    // FIXME: enable this to be enabled or disabled on a per-cmdfile basis
    private boolean mReloadCmdfiles = false;

    @Option(
            name = "max-poll-time",
            description = "ms between forced command scheduler execution time")
    private long mPollTime = 60 * 1000; // 60 seconds

    @Option(
            name = "shutdown-on-cmdfile-error",
            description =
                    "terminate TF session if a configuration exception on command file occurs")
    private boolean mShutdownOnCmdfileError = false;

    @Option(
            name = "shutdown-delay",
            description =
                    "maximum time to wait before allowing final interruption of scheduler to"
                        + " terminate TF session. If value is zero, interruption will only be"
                        + " triggered when Invocation become interruptible. (Default behavior).",
            isTimeVal = true)
    private long mShutdownTimeout = 0;

    private HostState mHostState = HostState.UNKNOWN;

    private enum CommandState {
        WAITING_FOR_DEVICE("Wait_for_device"),
        EXECUTING("Executing"),
        SLEEPING("Sleeping");

        private final String mDisplayName;

        CommandState(String displayName) {
            mDisplayName = displayName;
        }

        public String getDisplayName() {
            return mDisplayName;
        }
    }

    /** Enums of different status of host */
    public enum HostState {
        UNKNOWN,
        RUNNING,
        QUITTING,
        KILLING;
    }

    private void setHostState(HostState state) {
        mHostState = state;
    }

    public HostState getHostState() {
        return mHostState;
    }

    /**
     * Represents one active command added to the scheduler. Will track total execution time of all
     * instances of this command
     */
    static class CommandTracker {
        private final int mId;
        private final String[] mArgs;
        private final String mCommandFilePath;
        private long mCount;

        /** the total amount of time this command was executing. Used to prioritize */
        private long mTotalExecTime = 0;

        CommandTracker(int id, String[] args, String commandFilePath) {
            mId = id;
            mArgs = args;
            mCommandFilePath = commandFilePath;
            mCount = 0;
        }

        synchronized void incrementExecTime(long execTime) {
            mTotalExecTime += execTime;
        }

        /**
         * @return the total amount of execution time for this command.
         */
        synchronized long getTotalExecTime() {
            return mTotalExecTime;
        }

        /** Get the full list of config arguments associated with this command. */
        String[] getArgs() {
            return mArgs;
        }

        int getId() {
            return mId;
        }

        /** Return the path of the file this command is associated with. null if not applicable */
        String getCommandFilePath() {
            return mCommandFilePath;
        }

        public void incrementScheduledCount() {
            mCount++;
        }

        public long getScheduledCount() {
            return mCount;
        }
    }

    /** Represents one instance of a command to be executed. */
    private class ExecutableCommand {
        private final CommandTracker mCmdTracker;
        private final IConfiguration mConfig;
        private final boolean mRescheduled;
        private final long mCreationTime;
        private Long mSleepTime;

        private ExecutableCommand(
                CommandTracker tracker, IConfiguration config, boolean rescheduled) {
            mConfig = config;
            mCmdTracker = tracker;
            mRescheduled = rescheduled;
            mCreationTime = System.currentTimeMillis();
        }

        /** Gets the {@link IConfiguration} for this command instance */
        public IConfiguration getConfiguration() {
            return mConfig;
        }

        /** Gets the associated {@link CommandTracker}. */
        CommandTracker getCommandTracker() {
            return mCmdTracker;
        }

        /** Callback to inform listener that command has started execution. */
        void commandStarted() {
            mSleepTime = null;
        }

        public void commandFinished(long elapsedTime) {
            getCommandTracker().incrementExecTime(elapsedTime);
            CLog.d("removing exec command for id %d", getCommandTracker().getId());
            synchronized (CommandScheduler.this) {
                mExecutingCommands.remove(this);
            }
            if (isShuttingDown()) {
                mCommandProcessWait.signalEventReceived();
            }
        }

        public boolean isRescheduled() {
            return mRescheduled;
        }

        public long getCreationTime() {
            return mCreationTime;
        }

        public boolean isLoopMode() {
            return mConfig.getCommandOptions().isLoopMode();
        }

        public long getMaxLoopCount() {
            return mConfig.getCommandOptions().getMaxLoopCount();
        }

        public Long getSleepTime() {
            return mSleepTime;
        }

        public String getCommandFilePath() {
            return mCmdTracker.getCommandFilePath();
        }
    }

    /** struct for a command and its state */
    private static class ExecutableCommandState {
        final ExecutableCommand cmd;
        final CommandState state;

        ExecutableCommandState(ExecutableCommand cmd, CommandState state) {
            this.cmd = cmd;
            this.state = state;
        }
    }

    /** A {@link IRescheduler} that will add a command back to the queue. */
    private class Rescheduler implements IRescheduler {

        private CommandTracker mCmdTracker;

        Rescheduler(CommandTracker cmdTracker) {
            mCmdTracker = cmdTracker;
        }

        /** {@inheritDoc} */
        @Override
        public boolean scheduleConfig(IConfiguration config) {
            // force loop mode to off, otherwise each rescheduled config will be treated as
            // a new command and added back to queue
            config.getCommandOptions().setLoopMode(false);
            ExecutableCommand rescheduledCmd = createExecutableCommand(mCmdTracker, config, true);
            return addExecCommandToQueue(rescheduledCmd, 0);
        }

        /** {@inheritDoc} */
        @Override
        public boolean rescheduleCommand() {
            try {
                CLog.d("rescheduling for command %d", mCmdTracker.getId());
                IConfiguration config =
                        getConfigFactory().createConfigurationFromArgs(mCmdTracker.getArgs());
                ExecutableCommand execCmd = createExecutableCommand(mCmdTracker, config, true);
                return addExecCommandToQueue(execCmd, config.getCommandOptions().getLoopTime());
            } catch (ConfigurationException e) {
                // FIXME: do this with jline somehow for ANSI support
                // note: make sure not to log (aka record) this line, as (args) may contain
                // passwords.
                System.out.println(
                        String.format(
                                "Error while processing args: %s",
                                Arrays.toString(mCmdTracker.getArgs())));
                System.out.println(e.getMessage());
                System.out.println();
                return false;
            }
        }
    }

    /**
     * Comparator for {@link ExecutableCommand}.
     *
     * <p>Delegates to {@link CommandTrackerTimeComparator}.
     */
    private static class ExecutableCommandComparator implements Comparator<ExecutableCommand> {
        CommandTrackerTimeComparator mTrackerComparator = new CommandTrackerTimeComparator();

        /** {@inheritDoc} */
        @Override
        public int compare(ExecutableCommand c1, ExecutableCommand c2) {
            return mTrackerComparator.compare(c1.getCommandTracker(), c2.getCommandTracker());
        }
    }

    /**
     * Comparator for {@link CommandTracker}.
     *
     * <p>Compares by mTotalExecTime, prioritizing configs with lower execution time
     */
    private static class CommandTrackerTimeComparator implements Comparator<CommandTracker> {

        @Override
        public int compare(CommandTracker c1, CommandTracker c2) {
            if (c1.getTotalExecTime() == c2.getTotalExecTime()) {
                return 0;
            } else if (c1.getTotalExecTime() < c2.getTotalExecTime()) {
                return -1;
            } else {
                return 1;
            }
        }
    }

    /**
     * Comparator for {@link CommandTracker}.
     *
     * <p>Compares by id.
     */
    static class CommandTrackerIdComparator implements Comparator<CommandTracker> {

        @Override
        public int compare(CommandTracker c1, CommandTracker c2) {
            if (c1.getId() == c2.getId()) {
                return 0;
            } else if (c1.getId() < c2.getId()) {
                return -1;
            } else {
                return 1;
            }
        }
    }

    /**
     * An {@link com.android.tradefed.command.ICommandScheduler.IScheduledInvocationListener} for
     * locally scheduled commands added via addCommand.
     *
     * <p>Returns device to device manager and remote handover server if applicable.
     */
    private class FreeDeviceHandler extends ResultForwarder
            implements IScheduledInvocationListener {

        private final IDeviceManager mDeviceManager;
        private boolean mDeviceReleased = false;
        private Map<ITestDevice, FreeDeviceState> mDevicesStates = null;

        FreeDeviceHandler(IDeviceManager deviceManager, IScheduledInvocationListener... listeners) {
            super(listeners);
            mDeviceManager = deviceManager;
        }

        @Override
        public void releaseDevices(
                IInvocationContext context, Map<ITestDevice, FreeDeviceState> devicesStates) {
            if (mDeviceReleased) {
                return;
            }
            mDevicesStates = devicesStates;
            for (ITestDevice device : context.getDevices()) {
                mDeviceManager.freeDevice(device, devicesStates.get(device));
            }
            mDeviceReleased = true;
        }

        @Override
        public void invocationComplete(
                IInvocationContext context, Map<ITestDevice, FreeDeviceState> devicesStates) {
            if (mDevicesStates != null) {
                devicesStates = mDevicesStates;
            }
            for (ITestInvocationListener listener : getListeners()) {
                try {
                    ((IScheduledInvocationListener) listener)
                            .invocationComplete(context, devicesStates);
                } catch (Exception e) {
                    CLog.e("Exception during invocationComplete:");
                    CLog.e(e);
                }
            }
            releaseDevices(context, devicesStates);
        }
    }

    /** A custom {@link FreeDeviceHandler} that frees only {@link NullDevice}. */
    private class FreeNullDeviceHandler extends FreeDeviceHandler {

        private final IDeviceManager mDeviceManager;
        private boolean mDeviceReleased = false;

        FreeNullDeviceHandler(
                IDeviceManager deviceManager, IScheduledInvocationListener... listeners) {
            super(deviceManager, listeners);
            mDeviceManager = deviceManager;
        }

        @Override
        public void releaseDevices(
                IInvocationContext context, Map<ITestDevice, FreeDeviceState> devicesStates) {
            if (mDeviceReleased) {
                return;
            }
            for (ITestDevice device : context.getDevices()) {
                if (!(device.getIDevice() instanceof NullDevice)) {
                    continue;
                }
                mDeviceManager.freeDevice(device, devicesStates.get(device));
            }
            mDeviceReleased = true;
        }
    }

    /** Monitor Class for {@link InvocationThread} to make sure it does not run for too long. */
    private class InvocationThreadMonitor extends TimerTask {

        private InvocationThread mInvocationThread = null;
        private boolean mTriggered = false;

        public InvocationThreadMonitor(InvocationThread toMonitor) {
            mInvocationThread = toMonitor;
        }

        @Override
        public void run() {
            if (mInvocationThread != null) {
                mTriggered = true;
                mInvocationThread.stopInvocation(
                        "Invocation Timeout Reached.", InfraErrorIdentifier.INVOCATION_TIMEOUT);
            }
        }

        public boolean isTriggered() {
            return mTriggered;
        }
    }

    private class InvocationThread extends Thread {

        private static final int EXPECTED_THREAD_COUNT = 1;
        private static final String INVOC_END_EVENT_ID_KEY = "id";
        private static final String INVOC_END_EVENT_ELAPSED_KEY = "elapsed-time";
        private static final String INVOC_END_EVENT_TAG_KEY = "test-tag";

        private final IScheduledInvocationListener[] mListeners;
        private final IInvocationContext mInvocationContext;
        private final ExecutableCommand mCmd;
        private final ITestInvocation mInvocation;
        private final InvocationThreadMonitor mInvocationThreadMonitor;
        private final Timer mExecutionTimer;
        private long mStartTime = -1;

        public InvocationThread(
                String name,
                IInvocationContext invocationContext,
                ExecutableCommand command,
                IScheduledInvocationListener... listeners) {
            // create a thread group so LoggerRegistry can identify this as an invocationThread
            super(new ThreadGroup(name), name);
            mListeners = listeners;
            mInvocationContext = invocationContext;
            mCmd = command;
            mInvocation = createRunInstance();

            // Daemon timer
            mExecutionTimer = new Timer(true);
            mInvocationThreadMonitor = new InvocationThreadMonitor(this);
        }

        public long getStartTime() {
            return mStartTime;
        }

        @Override
        public void run() {
            if (mClient != null) {
                mClient.notifyTradefedInvocationStartEvent();
            }
            IConfiguration config = mCmd.getConfiguration();
            if (config.getCommandOptions().isTracingEnabled()) {
                long pid = ProcessHandle.current().pid();
                long tid = Thread.currentThread().getId();
                ActiveTrace trace = TracingLogger.createActiveTrace(pid, tid);
                trace.startTracing(
                        config.getCommandOptions()
                                .getInvocationData()
                                .containsKey(SubprocessTfLauncher.SUBPROCESS_TAG_NAME));
            }
            // Set experimental flags
            if (config.getCommandOptions().isExperimentEnabled()) {
                try {
                    for (Map.Entry<String, String> entry :
                            config.getCommandOptions().getExperimentalFlags().entrySet()) {
                        String optionName = entry.getKey();
                        String optionValue = entry.getValue();
                        // Support map experiments, where optionValue is a key=value pair
                        int equalsIndex = optionValue.indexOf('=');
                        if (equalsIndex != -1) {
                            String mapKey = optionValue.substring(0, equalsIndex);
                            String mapValue = optionValue.substring(equalsIndex + 1);
                            config.injectOptionValue(optionName, mapKey, mapValue);
                        } else {
                            config.injectOptionValue(optionName, optionValue);
                        }
                        mInvocationContext.addInvocationAttribute(
                                "experiment:" + optionName, optionValue);
                    }
                } catch (ConfigurationException e) {
                    CLog.e("Configuration Exception caught while setting experimental flags.");
                    CLog.e(e);
                }
            }
            mStartTime = System.currentTimeMillis();
            ITestInvocation instance = getInvocation();
            instance.setClearcutClient(mClient);
            try (CloseableTraceScope ignore = new CloseableTraceScope("init")) {
                for (final IScheduledInvocationListener listener : mListeners) {
                    try {
                        listener.invocationInitiated(mInvocationContext);
                    } catch (Throwable anyException) {
                        CLog.e("Exception caught while calling invocationInitiated:");
                        CLog.e(anyException);
                    }
                }
            }
            Exception trackDeviceException = null;
            boolean lastInvocationSet = false;
            try (CloseableTraceScope ignore = new CloseableTraceScope("test-invocation")) {
                // Copy the command options invocation attributes to the invocation if it has not
                // been already done.
                if (!config.getConfigurationDescription().shouldUseSandbox()
                        && !config.getCommandOptions().getInvocationData().isEmpty()) {
                    mInvocationContext.addInvocationAttributes(
                            config.getCommandOptions().getInvocationData());
                }
                mCmd.commandStarted();
                long invocTimeout = config.getCommandOptions().getInvocationTimeout();
                if (invocTimeout > 0) {
                    CLog.i("Setting a timer for the invocation in %sms", invocTimeout);
                    mExecutionTimer.schedule(mInvocationThreadMonitor, invocTimeout);
                }
                instance.invoke(
                        mInvocationContext,
                        config,
                        new Rescheduler(mCmd.getCommandTracker()),
                        mListeners);
            } catch (DeviceUnresponsiveException e) {
                CLog.w("Device %s is unresponsive. Reason: %s", e.getSerial(), e.getMessage());
                trackDeviceException = e;
                setLastInvocationExitCode(ExitCode.DEVICE_UNRESPONSIVE, e);
                lastInvocationSet = true;
            } catch (DeviceNotAvailableException e) {
                CLog.w("Device %s is not available. Reason: %s", e.getSerial(), e.getMessage());
                trackDeviceException = e;
                setLastInvocationExitCode(ExitCode.DEVICE_UNAVAILABLE, e);
                lastInvocationSet = true;
            } catch (FatalHostError e) {
                String errorMessage =
                        String.format("Fatal error occurred: %s, shutting down", e.getMessage());
                CLog.wtf(errorMessage, e);
                setLastInvocationExitCode(ExitCode.FATAL_HOST_ERROR, e);
                lastInvocationSet = true;
                shutdownHard(true, errorMessage, e.getErrorId());
            } catch (Throwable e) {
                setLastInvocationExitCode(ExitCode.THROWABLE_EXCEPTION, e);
                lastInvocationSet = true;
                CLog.e(e);
            } finally {
                mExecutionTimer.cancel();
                if (mInvocationThreadMonitor.isTriggered()) {
                    CLog.e("Invocation reached its timeout. Cleaning up.");
                }
                long elapsedTime = System.currentTimeMillis() - mStartTime;
                CLog.logAndDisplay(
                        LogLevel.INFO,
                        "Updating command %d with elapsed time %d ms",
                        mCmd.getCommandTracker().getId(),
                        elapsedTime);
                try (CloseableTraceScope ignore = new CloseableTraceScope("finalize_invocation")) {
                    // remove invocation thread first so another invocation can be started on device
                    // when freed
                    removeInvocationThread(this);

                    checkStrayThreads();

                    Map<ITestDevice, FreeDeviceState> deviceStates =
                            createReleaseMap(mInvocationContext, trackDeviceException);
                    for (final IScheduledInvocationListener listener : mListeners) {
                        try {
                            listener.invocationComplete(mInvocationContext, deviceStates);
                        } catch (Throwable anyException) {
                            CLog.e("Exception caught while calling invocationComplete:");
                            CLog.e(anyException);
                        }
                    }
                    if (!lastInvocationSet && instance.getExitInfo() != null) {
                        setLastInvocationExitCode(
                                instance.getExitInfo().mExitCode, instance.getExitInfo().mStack);
                    }
                    if (getFeatureServer() != null) {
                        getFeatureServer().unregisterInvocation(config);
                    }
                    mCmd.commandFinished(elapsedTime);
                    logInvocationEndedEvent(
                            mCmd.getCommandTracker().getId(), elapsedTime, mInvocationContext);
                }
                CLog.logAndDisplay(LogLevel.INFO, "Finalizing the logger and invocation.");
                ActiveTrace trace = TracingLogger.getActiveTrace();
                if (trace != null) {
                    File traceFile = trace.finalizeTracing();
                    if (traceFile != null) {
                        ActiveTrace main = TracingLogger.getMainTrace();
                        if (main != null) {
                            main.addSubprocessTrace(traceFile);
                        }
                        logTrace(traceFile, config);
                    }
                }
                if (config.getCommandOptions().reportInvocationComplete()) {
                    LogSaverResultForwarder.reportEndHostLog(
                            config.getTestInvocationListeners(),
                            config.getLogSaver(),
                            TestInvocation.TRADEFED_INVOC_COMPLETE_HOST_LOG);
                    config.getLogOutput().closeLog();
                    LogRegistry.getLogRegistry().unregisterLogger();
                }
                clearTerminating(this);
            }
        }

        /** Special handling to send the trace file from subprocess when needed. */
        private void logTrace(File traceFile, IConfiguration config) {
            if (config.getCommandOptions()
                    .getInvocationData()
                    .containsKey(SubprocessTfLauncher.SUBPROCESS_TAG_NAME)) {
                CLog.logAndDisplay(LogLevel.INFO, "Sending trace from subprocess");
                LogFile perfettoTrace =
                        new LogFile(traceFile.getAbsolutePath(), null, LogDataType.PERFETTO);
                for (ITestInvocationListener listener : config.getTestInvocationListeners()) {
                    try {
                        if (listener instanceof ILogSaverListener) {
                            ((ILogSaverListener) listener)
                                    .logAssociation(ActiveTrace.TRACE_KEY, perfettoTrace);
                        }
                    } catch (Exception e) {
                        CLog.logAndDisplay(LogLevel.ERROR, e.getMessage());
                        CLog.e(e);
                    }
                }
            } else {
                try (FileInputStreamSource source = new FileInputStreamSource(traceFile, true)) {
                    LogSaverResultForwarder.logFile(
                            config.getTestInvocationListeners(),
                            config.getLogSaver(),
                            source,
                            ActiveTrace.TRACE_KEY,
                            LogDataType.PERFETTO);
                }
            }
        }

        /** Check the number of thread in the ThreadGroup, only one should exists (itself). */
        private void checkStrayThreads() {
            int numThread = this.getThreadGroup().activeCount();
            if (numThread == EXPECTED_THREAD_COUNT) {
                // No stray thread detected at the end of invocation
                return;
            }
            // After calling parallelStream(), Some ForkJoinWorkerThreads stay around.
            // They are common pool workers and shouldn't be counted as stray threads.
            List<Thread> listThreads = getListOfThreadsExcludingForkJoinWorkers(numThread);
            numThread = listThreads.size();
            if (numThread == EXPECTED_THREAD_COUNT) {
                return;
            }

            List<String> cmd = Arrays.asList(mCmd.getCommandTracker().getArgs());
            CLog.e(
                    "Stray thread detected for command %d, %s. %d threads instead of %d",
                    mCmd.getCommandTracker().getId(), cmd, numThread, EXPECTED_THREAD_COUNT);
            // This is the best we have for debug, it prints to stdout.
            CLog.e("List of remaining threads: %s", listThreads);
            List<IHostMonitor> hostMonitors = GlobalConfiguration.getHostMonitorInstances();
            if (hostMonitors != null) {
                for (IHostMonitor hm : hostMonitors) {
                    HostDataPoint data = new HostDataPoint("numThread", numThread, cmd.toString());
                    hm.addHostEvent(HostMetricType.INVOCATION_STRAY_THREAD, data);
                }
            }
            // printing to stderr will help to catch them.
            System.err.println(
                    String.format(
                            "We have %s threads instead of 1: %s. Check the logs for list of "
                                    + "threads.",
                            numThread, listThreads));
        }

        private List<Thread> getListOfThreadsExcludingForkJoinWorkers(int numThread) {
            Thread[] listThreads = new Thread[numThread];
            this.getThreadGroup().enumerate(listThreads);

            return Arrays.asList(listThreads).stream()
                    .filter(t -> !(t instanceof ForkJoinWorkerThread))
                    .collect(Collectors.toList());
        }

        /** Helper to log an invocation ended event. */
        private void logInvocationEndedEvent(
                int invocId, long elapsedTime, final IInvocationContext context) {
            Map<String, String> args = new HashMap<>();
            args.put(INVOC_END_EVENT_ID_KEY, Integer.toString(invocId));
            args.put(INVOC_END_EVENT_ELAPSED_KEY, TimeUtil.formatElapsedTime(elapsedTime));
            args.put(INVOC_END_EVENT_TAG_KEY, context.getTestTag());
            // Add all the invocation attributes to the final event logging.
            // UniqueMultiMap cannot be easily converted to Map so we just iterate.
            for (String key : context.getAttributes().keySet()) {
                args.put(key, context.getAttributes().get(key).get(0));
            }
            logEvent(EventType.INVOCATION_END, args);
        }

        ITestInvocation getInvocation() {
            return mInvocation;
        }

        IInvocationContext getInvocationContext() {
            return mInvocationContext;
        }

        /** Notify invocation on {@link CommandScheduler#shutdown()}. */
        public void notifyInvocationStop(String message) {
            getInvocation().notifyInvocationStopped(message);
        }

        /**
         * Stops a running invocation. {@link CommandScheduler#shutdownHard()} will stop all running
         * invocations.
         */
        public void stopInvocation(String message) {
            stopInvocation(message, null);
        }

        /**
         * Stops a running invocation. {@link CommandScheduler#shutdownHard()} will stop all running
         * invocations.
         */
        public void stopInvocation(String message, ErrorIdentifier errorId) {
            getInvocation().notifyInvocationForceStopped(message, errorId);
            for (ITestDevice device : mInvocationContext.getDevices()) {
                if (TestDeviceState.ONLINE.equals(device.getDeviceState())) {
                    // Kill all running processes on device.
                    if (!(device.getIDevice() instanceof StubDevice)) {
                        try {
                            device.executeShellCommand("am kill-all");
                        } catch (DeviceNotAvailableException e) {
                            CLog.e("failed to kill process on device %s", device.getSerialNumber());
                            CLog.e(e);
                        }
                    }
                }
                // Finish with device tear down: We try to ensure that regardless of the invocation
                // state during the interruption we at least do minimal tear down of devices with
                // their built-in clean up.
                CLog.d("Attempting postInvocationTearDown in stopInvocation");
                device.postInvocationTearDown(null);
            }
            // If invocation is not currently in an interruptible state we provide a timer
            // after which it will become interruptible.
            // If timeout is 0, we do not enforce future interruption.
            if (getShutdownTimeout() != 0) {
                RunUtil.getDefault().setInterruptibleInFuture(this, getShutdownTimeout());
            }
            RunUtil.getDefault().interrupt(this, message, errorId);
        }

        /**
         * Disable the reporting from reporters that implements a non-default {@link
         * ITestInvocationListener#invocationInterrupted()}. Should be called on shutdown.
         */
        public void disableReporters() {
            for (ITestInvocationListener listener :
                    mCmd.getConfiguration().getTestInvocationListeners()) {
                listener.invocationInterrupted();
            }
        }

        /**
         * Checks whether the device battery level is above the required value to keep running the
         * invocation.
         */
        public void checkDeviceBatteryLevel() {
            for (String deviceName : mInvocationContext.getDeviceConfigNames()) {
                if (mCmd.getConfiguration().getDeviceConfigByName(deviceName).getDeviceOptions()
                        == null) {
                    CLog.d("No deviceOptions in the configuration, cannot do Battery level check");
                    return;
                }
                final Integer cutoffBattery =
                        mCmd.getConfiguration()
                                .getDeviceConfigByName(deviceName)
                                .getDeviceOptions()
                                .getCutoffBattery();

                if (mInvocationContext.getDevice(deviceName) != null && cutoffBattery != null) {
                    final ITestDevice device = mInvocationContext.getDevice(deviceName);
                    Integer batteryLevel = device.getBattery();
                    if (batteryLevel == null) {
                        return;
                    }
                    CLog.d("device %s: battery level=%d%%", device.getSerialNumber(), batteryLevel);
                    // This logic is based on the assumption that batterLevel will be 0 or -1 if TF
                    // fails to fetch a valid battery level or the device is not using a battery.
                    // So batteryLevel=0 will not trigger a stop.
                    if (0 < batteryLevel && batteryLevel < cutoffBattery) {
                        if (RunUtil.getDefault().isInterruptAllowed()) {
                            CLog.i(
                                    "Stopping %s: battery too low (%d%% < %d%%)",
                                    getName(), batteryLevel, cutoffBattery);
                            stopInvocation(
                                    String.format(
                                            "battery too low (%d%% < %d%%)",
                                            batteryLevel, cutoffBattery));
                        } else {
                            // In this case, the battery is check periodically by CommandScheduler
                            // so there will be more opportunity to terminate the invocation when
                            // it's interruptible.
                            CLog.w(
                                    "device: %s has a low battery but is in uninterruptible state.",
                                    device.getSerialNumber());
                        }
                    }
                }
            }
        }
    }

    /** Create a map of the devices state so they can be released appropriately. */
    public static Map<ITestDevice, FreeDeviceState> createReleaseMap(
            IInvocationContext context, Throwable e) {
        Map<ITestDevice, FreeDeviceState> deviceStates = new LinkedHashMap<>();
        for (ITestDevice device : context.getDevices()) {
            deviceStates.put(device, FreeDeviceState.AVAILABLE);
        }
        if (context.wasReleasedEarly()) {
            // Logic was already used, no need to run through it again.
            return deviceStates;
        }

        for (ITestDevice device : context.getDevices()) {
            if ((device.getIDevice() instanceof StubDevice
                            && !(device.getIDevice() instanceof FastbootDevice))
                    || device instanceof RemoteAndroidDevice) {
                // Never release stub and Tcp devices, otherwise they will disappear
                // during deallocation since they are only placeholder.
                deviceStates.put(device, FreeDeviceState.AVAILABLE);
            } else {
                TestDeviceState deviceState = device.getDeviceState();
                CLog.d(
                        "TestDeviceState for releasing '%s(%s)' is '%s'",
                        device.getSerialNumber(), device.getClass(), deviceState);
                if (SystemUtil.isLocalMode()) {
                    // Locally release directly
                    deviceStates.put(device, FreeDeviceState.AVAILABLE);
                } else if (!TestDeviceState.ONLINE.equals(deviceState)) {
                    // If the device is offline at the end of the test
                    deviceStates.put(device, FreeDeviceState.UNAVAILABLE);
                } else if (!device.waitForDeviceShell(30000L)) {
                    // If device cannot pass basic shell responsiveness test.
                    deviceStates.put(device, FreeDeviceState.UNAVAILABLE);
                }
            }
            // Reset the recovery mode at the end of the invocation.
            device.setRecoveryMode(RecoveryMode.AVAILABLE);
        }
        // For releasing, also investigate cause for possible DNAE
        if (e instanceof DeviceFailedToBootError
                && e.getCause() instanceof DeviceNotAvailableException) {
            e = e.getCause();
        }

        DeviceNotAvailableException dnae = null;
        FreeDeviceState unavailable = null;
        if (e instanceof DeviceUnresponsiveException) {
            dnae = (DeviceUnresponsiveException) e;
            unavailable = FreeDeviceState.UNRESPONSIVE;
        } else if (e instanceof DeviceNotAvailableException) {
            dnae = (DeviceNotAvailableException) e;
            unavailable = FreeDeviceState.UNAVAILABLE;
        }
        if (dnae != null) {
            // If we are carrying an INVOCATION_CANCELLED it has priority
            if (!InfraErrorIdentifier.INVOCATION_CANCELLED.equals(dnae.getErrorId())) {
                ITestDevice badDevice = context.getDeviceBySerial(dnae.getSerial());
                if (badDevice != null && !(badDevice.getIDevice() instanceof StubDevice)) {
                    deviceStates.put(badDevice, unavailable);
                }
            } else {
                CLog.d("Invocation cancelled detected.");
            }
        }
        CLog.d("Release map of the devices: %s", deviceStates);
        return deviceStates;
    }

    /**
     * A {@link IDeviceMonitor} that signals scheduler to process commands when an available device
     * is added.
     */
    private class AvailDeviceMonitor implements IDeviceMonitor {

        @Override
        public void run() {
            // ignore
        }

        @Override
        public void stop() {
            // ignore
        }

        @Override
        public void setDeviceLister(DeviceLister lister) {
            // ignore
        }

        @Override
        public void notifyDeviceStateChange(
                String serial, DeviceAllocationState oldState, DeviceAllocationState newState) {
            if (newState.equals(DeviceAllocationState.Available)) {
                // new avail device was added, wake up scheduler
                mCommandProcessWait.signalEventReceived();
            }
        }
    }

    /**
     * Creates a {@link CommandScheduler}.
     *
     * <p>Note: start must be called before use.
     */
    public CommandScheduler() {
        super("CommandScheduler"); // set the thread name
        mReadyCommands = new LinkedList<>();
        mUnscheduledWarning = new HashSet<>();
        mSleepingCommands = new HashSet<>();
        mExecutingCommands = new HashSet<>();
        mInvocationThreadMap = new HashMap<IInvocationContext, InvocationThread>();
        mInvocationThreadMapTerminating = new HashMap<IInvocationContext, InvocationThread>();
        // use a ScheduledThreadPoolExecutorTimer as a single-threaded timer. This class
        // is used instead of a java.util.Timer because it offers advanced shutdown options
        mCommandTimer = new ScheduledThreadPoolExecutor(1);
        mRunLatch = new CountDownLatch(1);
    }

    /** Starts the scheduler including setting up of logging, init of {@link DeviceManager} etc */
    @Override
    public synchronized void start() {
        synchronized (this) {
            if (mStarted) {
                throw new IllegalStateException("scheduler has already been started");
            }
            initLogging();

            try (CloseableTraceScope ignored = new CloseableTraceScope("initDeviceManager")) {
                initDeviceManager();
            }

            mStarted = true;
        }
        super.start();
        setHostState(HostState.RUNNING);
    }

    /** {@inheritDoc} */
    @Override
    public synchronized CommandFileWatcher getCommandFileWatcher() {
        assertStarted();
        if (mCommandFileWatcher == null) {
            mCommandFileWatcher = new CommandFileWatcher(this);
            mCommandFileWatcher.start();
        }
        return mCommandFileWatcher;
    }

    /** Initialize the device manager, optionally using a global device filter if specified. */
    void initDeviceManager() {
        getDeviceManager().init();
        if (getDeviceManager().waitForFirstDeviceAdded(ADB_INIT_TIME_MS)) {
            // If a first device is added we wait a short extra time to allow more devices to be
            // discovered.
            RunUtil.getDefault().sleep(ADB_INIT_TIME_MS);
        }
    }

    /**
     * Factory method for creating a {@link TestInvocation}.
     *
     * @return the {@link ITestInvocation} to use
     */
    ITestInvocation createRunInstance() {
        return new TestInvocation();
    }

    /**
     * Factory method for getting a reference to the {@link IDeviceManager}
     *
     * @return the {@link IDeviceManager} to use
     */
    protected IDeviceManager getDeviceManager() {
        return GlobalConfiguration.getDeviceManagerInstance();
    }

    protected IHostOptions getHostOptions() {
        return GlobalConfiguration.getInstance().getHostOptions();
    }

    /**
     * Factory method for getting a reference to the {@link IHostMonitor}
     *
     * @return the {@link IHostMonitor} to use
     */
    List<IHostMonitor> getHostMonitor() {
        return GlobalConfiguration.getHostMonitorInstances();
    }

    /**
     * Factory method for getting a reference to the {@link IConfigurationFactory}
     *
     * @return the {@link IConfigurationFactory} to use
     */
    protected IConfigurationFactory getConfigFactory() {
        return ConfigurationFactory.getInstance();
    }

    protected TradefedFeatureServer getFeatureServer() {
        return GlobalConfiguration.getInstance().getFeatureServer();
    }

    protected TestInvocationManagementServer getTestInvocationManagementServer() {
        return GlobalConfiguration.getInstance().getTestInvocationManagementSever();
    }

    protected DeviceManagementGrpcServer getDeviceManagementServer() {
        return GlobalConfiguration.getInstance().getDeviceManagementServer();
    }

    /**
     * Fetches a {@link IKeyStoreClient} using the {@link IKeyStoreFactory} declared in {@link
     * IGlobalConfiguration} or null if none is defined.
     *
     * @return IKeyStoreClient
     */
    protected IKeyStoreClient getKeyStoreClient() {
        try {
            IKeyStoreFactory f = GlobalConfiguration.getInstance().getKeyStoreFactory();
            if (f != null) {
                try {
                    return f.createKeyStoreClient();
                } catch (KeyStoreException e) {
                    CLog.e("Failed to create key store client");
                    CLog.e(e);
                }
            }
        } catch (IllegalStateException e) {
            CLog.w("Global configuration has not been created, failed to get keystore");
            CLog.e(e);
        }
        return null;
    }

    /** The main execution block of this thread. */
    @Override
    public void run() {
        assertStarted();
        try {
            IDeviceManager manager = getDeviceManager();

            // Notify other threads that we're running.
            mRunLatch.countDown();

            // add a listener that will wake up scheduler when a new avail device is added
            manager.addDeviceMonitor(new AvailDeviceMonitor());

            while (!isShutdown()) {
                // wait until processing is required again
                mCommandProcessWait.waitAndReset(mPollTime);
                checkInvocations();
                try {
                    processReadyCommands(manager);
                } catch (RuntimeException e) {
                    CLog.e(e);
                    Map<String, String> information = new HashMap<>();
                    information.put("Exception", "CommandScheduler");
                    information.put("stack", StreamUtil.getStackTrace(e));
                    logEvent(EventType.UNEXPECTED_EXCEPTION, information);
                }
            }
            mCommandTimer.shutdown();
            // We signal the device manager to stop device recovery threads because it could
            // potentially create more invocations.
            manager.terminateDeviceRecovery();
            manager.terminateDeviceMonitor();
            CLog.logAndDisplay(LogLevel.INFO, "Waiting for invocation threads to complete");
            waitForAllInvocationThreads();
            waitForTerminatingInvocationThreads();
            exit(manager);
            cleanUp();
            CLog.logAndDisplay(LogLevel.INFO, "All done");
            // Stop Feature Server after invocations are completed in case
            // they still need it during shutdown.
            if (getFeatureServer() != null) {
                try {
                    getFeatureServer().shutdown();
                } catch (InterruptedException e) {
                    CLog.e(e);
                }
            }
            if (getDeviceManagementServer() != null) {
                try {
                    getDeviceManagementServer().shutdown();
                } catch (InterruptedException e) {
                    CLog.e(e);
                }
            }
            // Stop TestInvocationManagementServer after invocations are completed as the client
            // need the server to get invocation details.
            if (getTestInvocationManagementServer() != null) {
                try {
                    getTestInvocationManagementServer().shutdown();
                } catch (InterruptedException e) {
                    CLog.e(e);
                }
            }
            if (mClient != null) {
                mClient.notifyTradefedFinishedEvent();
                mClient.stop();
            }
        } finally {
            // Make sure that we don't quit with messages still in the buffers
            System.err.flush();
            System.out.flush();
        }
    }

    void checkInvocations() {
        CLog.d("Checking invocations...");
        final List<InvocationThread> copy;
        synchronized (this) {
            copy = new ArrayList<InvocationThread>(mInvocationThreadMap.values());
        }
        for (InvocationThread thread : copy) {
            thread.checkDeviceBatteryLevel();
        }
    }

    protected void processReadyCommands(IDeviceManager manager) {
        CLog.d("processReadyCommands...");
        Map<ExecutableCommand, IInvocationContext> scheduledCommandMap = new HashMap<>();
        // minimize length of synchronized block by just matching commands with device first,
        // then scheduling invocations/adding looping commands back to queue
        synchronized (this) {
            // sort ready commands by priority, so high priority commands are matched first
            Collections.sort(mReadyCommands, new ExecutableCommandComparator());
            Iterator<ExecutableCommand> cmdIter = mReadyCommands.iterator();
            while (cmdIter.hasNext()) {
                ExecutableCommand cmd = cmdIter.next();
                IConfiguration config = cmd.getConfiguration();
                IInvocationContext context = new InvocationContext();
                context.setConfigurationDescriptor(config.getConfigurationDescription());
                DeviceAllocationResult allocationResults = allocateDevices(config, manager);
                if (allocationResults.wasAllocationSuccessful()) {
                    Map<String, ITestDevice> devices = allocationResults.getAllocatedDevices();
                    cmdIter.remove();
                    mExecutingCommands.add(cmd);
                    context.addAllocatedDevice(devices);

                    // track command matched with device
                    scheduledCommandMap.put(cmd, context);
                    // clean warned list to avoid piling over time.
                    mUnscheduledWarning.remove(cmd);
                } else {
                    if (!mUnscheduledWarning.contains(cmd)) {
                        CLog.logAndDisplay(
                                LogLevel.DEBUG,
                                "No available device matching all the "
                                        + "config's requirements for cmd id %d.",
                                cmd.getCommandTracker().getId());
                        // make sure not to record since it may contains password
                        System.out.println(
                                String.format(
                                        "Command will be rescheduled: %s",
                                        Arrays.toString(cmd.getCommandTracker().getArgs())));
                        mUnscheduledWarning.add(cmd);
                    }
                }
            }
        }

        // now actually execute the commands
        for (Map.Entry<ExecutableCommand, IInvocationContext> cmdDeviceEntry :
                scheduledCommandMap.entrySet()) {
            ExecutableCommand cmd = cmdDeviceEntry.getKey();
            try (CloseableTraceScope ignored = new CloseableTraceScope("startInvocation")) {
                startInvocation(
                        cmdDeviceEntry.getValue(), cmd, new FreeDeviceHandler(getDeviceManager()));
            }
            cmd.getCommandTracker().incrementScheduledCount();
            if (cmd.isLoopMode()
                    && cmd.getCommandTracker().getScheduledCount() < cmd.getMaxLoopCount()) {
                addNewExecCommandToQueue(cmd.getCommandTracker());
            }
        }
        CLog.d("done processReadyCommands...");
    }

    /** {@inheritDoc} */
    @Override
    public void await() throws InterruptedException {
        while (mRunLatch.getCount() > 0) {
            mRunLatch.await();
        }
    }

    private void waitForThread(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException e) {
            // ignore
            waitForThread(thread);
        }
    }

    /** Wait until all invocation threads complete. */
    private void waitForAllInvocationThreads() {
        List<InvocationThread> threadListCopy;
        synchronized (this) {
            threadListCopy = new ArrayList<>();
            threadListCopy.addAll(mInvocationThreadMap.values());
        }
        for (Thread thread : threadListCopy) {
            waitForThread(thread);
        }
    }

    private void waitForTerminatingInvocationThreads() {
        List<InvocationThread> threadListCopy;
        synchronized (this) {
            threadListCopy = new ArrayList<>();
            threadListCopy.addAll(mInvocationThreadMapTerminating.values());
        }
        for (Thread thread : threadListCopy) {
            waitForThread(thread);
        }
    }

    private void exit(IDeviceManager manager) {
        if (manager != null) {
            manager.terminate();
        }
    }

    /** {@inheritDoc} */
    @Override
    public Pair<Boolean, Integer> addCommand(String[] args) throws ConfigurationException {
        Integer cmdTracker = internalAddCommand(args, null);
        return Pair.create(cmdTracker >= 0, cmdTracker);
    }

    /** Returns true if {@link CommandOptions#USE_SANDBOX} is part of the command line. */
    private boolean isCommandSandboxed(String[] args) {
        boolean foundSandbox = false;
        // Since the order is important, mark the found sandbox when we find it, and unset it if
        // we find the negation.
        for (String arg : args) {
            if (("--" + CommandOptions.USE_SANDBOX).equals(arg)) {
                foundSandbox = true;
            } else if (("--no-" + CommandOptions.USE_SANDBOX).equals(arg)) {
                foundSandbox = false;
            }
        }
        return foundSandbox;
    }

    private boolean isProxyCommand(String[] args) throws ConfigurationException {
        ProxyConfiguration proxy = new ProxyConfiguration();
        ArgsOptionParser argsParser = new ArgsOptionParser(proxy);
        List<String> argsList = new ArrayList<>(Arrays.asList(args));
        argsList.remove(0);
        argsParser.parseBestEffort(argsList, true);
        return proxy.isProxySet();
    }

    private IConfiguration handleProxyCommand(String[] originalArgs) throws ConfigurationException {
        IConfiguration config =
                ((ConfigurationFactory) getConfigFactory())
                        .createPartialConfigurationFromArgs(
                                originalArgs,
                                getKeyStoreClient(),
                                ImmutableSet.of(ProxyConfiguration.PROXY_CONFIG_TYPE_KEY),
                                null);
        try {
            config.resolveDynamicOptions(new DynamicRemoteFileResolver());
        } catch (BuildRetrievalError e) {
            throw new ConfigurationException(e.getMessage(), e);
        }
        ProxyConfiguration proxy =
                (ProxyConfiguration)
                        config.getConfigurationObject(ProxyConfiguration.PROXY_CONFIG_TYPE_KEY);
        if (proxy.getProxyConfig() == null) {
            throw new ConfigurationException("No proxy configuration found.");
        }
        originalArgs[0] = proxy.getProxyConfig().getAbsolutePath();
        return config;
    }

    /** Returns true if the configuration used is a retry one. */
    private boolean isRetryCommand(IConfiguration config) {
        // If a configuration is made of the RetryRunner only, it is meant to run as a retry.
        if (config.getTests().size() != 1) {
            return false;
        }
        IRemoteTest rerunner = config.getTests().get(0);
        if (rerunner instanceof RetryRescheduler) {
            return true;
        }
        return false;
    }

    /** Create a {@link ISandbox} that the invocation will use to run. */
    public ISandbox createSandbox() {
        return GlobalConfiguration.getInstance().getSandboxFactory().createSandbox();
    }

    protected IConfiguration createConfiguration(String[] args) throws ConfigurationException {
        TradefedDelegator delegator = checkDelegation(args);
        if (delegator.shouldUseDelegation()) {
            args = TradefedDelegator.clearCommandline(args);
            // Do not use delegation on staging
            if (!delegator.isStaging()) {
                delegator.setCommandLine(args);
                CLog.d("Using commandline arguments as starting command: %s", Arrays.asList(args));
                IConfiguration config =
                        ((ConfigurationFactory) getConfigFactory())
                                .createPartialConfigurationFromArgs(
                                        args,
                                        getKeyStoreClient(),
                                        ImmutableSet.of(
                                                Configuration.DEVICE_REQUIREMENTS_TYPE_NAME,
                                                Configuration.LOGGER_TYPE_NAME,
                                                Configuration.LOG_SAVER_TYPE_NAME,
                                                Configuration.RESULT_REPORTER_TYPE_NAME),
                                        delegator);
                setDelegateLevelReporting(config);
                return config;
            }
        }

        // check if the command should be sandboxed
        if (isCommandSandboxed(args)) {
            // Create an sandboxed configuration based on the sandbox of the scheduler.
            ISandbox sandbox = createSandbox();
            return SandboxConfigurationFactory.getInstance()
                    .createConfigurationFromArgs(args, getKeyStoreClient(), sandbox, new RunUtil());
        }
        if (isProxyCommand(args)) {
            IConfiguration proxyConfig = handleProxyCommand(args);
            String[] argsWithoutDelegation = ProxyConfiguration.clearCommandline(args);
            IConfiguration resolvedConfig = null;
            try {
                resolvedConfig =
                        getConfigFactory()
                                .createConfigurationFromArgs(
                                        argsWithoutDelegation, null, getKeyStoreClient());
            } catch (ConfigurationException e) {
                proxyConfig.cleanConfigurationData();
                throw e;
            }
            resolvedConfig.addFilesToClean(proxyConfig.getFilesToClean());
            return resolvedConfig;
        }
        IConfiguration config =
                getConfigFactory().createConfigurationFromArgs(args, null, getKeyStoreClient());
        if (isRetryCommand(config)) {
            return RetryConfigurationFactory.getInstance().createRetryConfiguration(config);
        }
        return config;
    }

    /**
     * Create a delegator based on the command line to see if we need to delegate the run.
     *
     * @throws ConfigurationException
     */
    public static TradefedDelegator checkDelegation(String[] args) throws ConfigurationException {
        TradefedDelegator delegator = new TradefedDelegator();
        ArgsOptionParser argsParser = new ArgsOptionParser(delegator);
        List<String> argsList = new ArrayList<>(Arrays.asList(args));
        argsList.remove(0);
        argsParser.parseBestEffort(argsList, true);
        return delegator;
    }

    private void setDelegateLevelReporting(IConfiguration config) {
        List<ITestInvocationListener> delegateReporters = new ArrayList<>();
        // For debugging in the console, add a printer
        delegateReporters.add(new ConsoleResultReporter());
        delegateReporters.add(new SuiteResultReporter());
        try {
            Class<?> objectClass =
                    Class.forName("com.google.android.tradefed.result.teststorage.ResultReporter");
            Object infraReporter = objectClass.getDeclaredConstructor().newInstance();
            delegateReporters.add((ITestInvocationListener) infraReporter);
        } catch (Exception e) {
            CLog.e(e);
        }
        config.setTestInvocationListeners(delegateReporters);
        try {
            Class<?> objectClass =
                    Class.forName("com.google.android.tradefed.result.AndroidBuildApiLogSaver");
            Object infraLogger = objectClass.getDeclaredConstructor().newInstance();
            config.setLogSaver((ILogSaver) infraLogger);
        } catch (Exception e) {
            CLog.e(e);
        }
    }

    /**
     * Adds a command.
     *
     * @param args the config arguments.
     * @param cmdFilePath the filesystem path of command file
     * @return Command tracker id (non-negative value) if the command was added successfully, return
     *     0 when command is added for all devices. Otherwise -1.
     */
    private Integer internalAddCommand(String[] args, String cmdFilePath)
            throws ConfigurationException {
        assertStarted();
        IConfiguration config = createConfiguration(args);
        if (config.getCommandOptions().isHelpMode()) {
            getConfigFactory().printHelpForConfig(args, true, System.out);
        } else if (config.getCommandOptions().isFullHelpMode()) {
            getConfigFactory().printHelpForConfig(args, false, System.out);
        } else if (config.getCommandOptions().isDryRunMode()) {
            config.validateOptions();
            String cmdLine = QuotationAwareTokenizer.combineTokens(args);
            CLog.d("Dry run mode; skipping adding command: %s", cmdLine);
            if (config.getCommandOptions().isNoisyDryRunMode()) {
                System.out.println(cmdLine.replace("--noisy-dry-run", ""));
                System.out.println("");
            }
        } else {
            config.validateOptions();
            if (config.getCommandOptions().runOnAllDevices()) {
                addCommandForAllDevices(args, cmdFilePath);
                return 0;
            } else {
                CommandTracker cmdTracker = createCommandTracker(args, cmdFilePath);
                ExecutableCommand cmdInstance = createExecutableCommand(cmdTracker, config, false);
                addExecCommandToQueue(cmdInstance, 0);
                return cmdTracker.getId();
            }
        }
        return -1;
    }

    /** {@inheritDoc} */
    @Override
    public void addCommandFile(String cmdFilePath, List<String> extraArgs)
            throws ConfigurationException {
        // verify we aren't already watching this command file, don't want to add it twice!
        File cmdFile = new File(cmdFilePath);
        if (mReloadCmdfiles && getCommandFileWatcher().isFileWatched(cmdFile)) {
            CLog.logAndDisplay(
                    LogLevel.INFO,
                    "cmd file %s is already running and being watched for changes. Reloading",
                    cmdFilePath);
            removeCommandsFromFile(cmdFile);
        }
        internalAddCommandFile(cmdFile, extraArgs);
    }

    /** Adds a command file without verifying if its already being watched */
    private void internalAddCommandFile(File cmdFile, List<String> extraArgs)
            throws ConfigurationException {
        try {
            CommandFileParser parser = createCommandFileParser();

            List<CommandLine> commands = parser.parseFile(cmdFile);
            if (mReloadCmdfiles) {
                // note always should re-register for command file, even if already listening,
                // since the dependent file list might have changed
                getCommandFileWatcher().addCmdFile(cmdFile, extraArgs, parser.getIncludedFiles());
            }
            for (CommandLine command : commands) {
                command.addAll(extraArgs);
                String[] arrayCommand = command.asArray();
                final String prettyCmdLine = QuotationAwareTokenizer.combineTokens(arrayCommand);
                CLog.d("Adding command %s", prettyCmdLine);

                try {
                    internalAddCommand(arrayCommand, cmdFile.getAbsolutePath());
                } catch (ConfigurationException e) {
                    throw new ConfigurationException(
                            String.format(
                                    "Failed to add command '%s': %s",
                                    prettyCmdLine, e.getMessage()),
                            e);
                }
            }
        } catch (IOException e) {
            throw new ConfigurationException(
                    "Failed to read file " + cmdFile.getAbsolutePath(),
                    e,
                    InfraErrorIdentifier.CONFIGURATION_NOT_FOUND);
        }
    }

    /**
     * Factory method for creating a {@link CommandFileParser}.
     *
     * <p>Exposed for unit testing.
     */
    CommandFileParser createCommandFileParser() {
        return new CommandFileParser();
    }

    /**
     * Creates a new command for each connected device, and adds each to the queue.
     *
     * <p>Note this won't have the desired effect if user has specified other conflicting {@link
     * IConfiguration#getDeviceRequirements()}in the command.
     */
    private void addCommandForAllDevices(String[] args, String cmdFilePath)
            throws ConfigurationException {
        List<DeviceDescriptor> deviceDescs = getDeviceManager().listAllDevices();

        for (DeviceDescriptor deviceDesc : deviceDescs) {
            if (!deviceDesc.isStubDevice()) {
                String device = deviceDesc.getSerial();
                String[] argsWithDevice = Arrays.copyOf(args, args.length + 2);
                argsWithDevice[argsWithDevice.length - 2] = "-s";
                argsWithDevice[argsWithDevice.length - 1] = device;
                CommandTracker cmdTracker = createCommandTracker(argsWithDevice, cmdFilePath);
                IConfiguration config =
                        getConfigFactory()
                                .createConfigurationFromArgs(
                                        cmdTracker.getArgs(), null, getKeyStoreClient());
                CLog.logAndDisplay(
                        LogLevel.INFO, "Scheduling '%s' on '%s'", cmdTracker.getArgs()[0], device);
                config.getDeviceRequirements().setSerial(device);
                ExecutableCommand execCmd = createExecutableCommand(cmdTracker, config, false);
                addExecCommandToQueue(execCmd, 0);
            }
        }
    }

    /** Creates a new {@link CommandTracker} with a unique id. */
    private synchronized CommandTracker createCommandTracker(
            String[] args, String commandFilePath) {
        mCurrentCommandId++;
        CLog.d(
                "Creating command tracker id %d for command args: '%s'",
                mCurrentCommandId, ArrayUtil.join(" ", (Object[]) args));
        return new CommandTracker(mCurrentCommandId, args, commandFilePath);
    }

    /** Creates a new {@link ExecutableCommand}. */
    private ExecutableCommand createExecutableCommand(
            CommandTracker cmdTracker, IConfiguration config, boolean rescheduled) {
        ExecutableCommand cmd = new ExecutableCommand(cmdTracker, config, rescheduled);
        CLog.d("creating exec command for id %d", cmdTracker.getId());
        return cmd;
    }

    /**
     * Creates a new {@link ExecutableCommand}, and adds it to queue
     *
     * @param commandTracker
     */
    private void addNewExecCommandToQueue(CommandTracker commandTracker) {
        try {
            IConfiguration config = createConfiguration(commandTracker.getArgs());
            ExecutableCommand execCmd = createExecutableCommand(commandTracker, config, false);
            addExecCommandToQueue(execCmd, config.getCommandOptions().getLoopTime());
        } catch (ConfigurationException e) {
            CLog.e(e);
        }
    }

    /**
     * Adds executable command instance to queue, with optional delay.
     *
     * @param cmd the {@link ExecutableCommand} to return to queue
     * @param delayTime the time in ms to delay before adding command to queue
     * @return <code>true</code> if command will be added to queue, <code>false</code> otherwise
     */
    private synchronized boolean addExecCommandToQueue(
            final ExecutableCommand cmd, long delayTime) {
        // If the command is local sharding one being rescheduled, do not apply the shutdown yet
        // This allows commandAndExit to still works with local sharding.
        if (isShutdown()) {
            if (cmd.getConfiguration()
                            .getConfigurationDescription()
                            .getMetaData(ConfigurationDescriptor.LOCAL_SHARDED_KEY)
                    == null) {
                return false;
            }
        }
        if (delayTime > 0) {
            mSleepingCommands.add(cmd);
            // delay before making command active
            Runnable delayCommand =
                    new Runnable() {
                        @Override
                        public void run() {
                            synchronized (CommandScheduler.this) {
                                if (mSleepingCommands.remove(cmd)) {
                                    mReadyCommands.add(cmd);
                                    mCommandProcessWait.signalEventReceived();
                                }
                            }
                        }
                    };
            mCommandTimer.schedule(delayCommand, delayTime, TimeUnit.MILLISECONDS);
        } else {
            mReadyCommands.add(cmd);
            mCommandProcessWait.signalEventReceived();
        }
        return true;
    }

    /**
     * Helper method to return an array of {@link String} elements as a readable {@link String}
     *
     * @param args the {@link String}[] to use
     * @return a display friendly {@link String} of args contents
     */
    private String getArgString(String[] args) {
        return ArrayUtil.join(" ", (Object[]) args);
    }

    /** {@inheritDoc} */
    @Override
    public long execCommand(
            IInvocationContext context, IScheduledInvocationListener listener, String[] args)
            throws ConfigurationException, NoDeviceException {
        return execCommand(context, listener, new ArrayList<ITestDevice>(), args);
    }

    /** {@inheritDoc} */
    @Override
    public long execCommand(
            IScheduledInvocationListener listener, List<ITestDevice> reservedDevices, String[] args)
            throws ConfigurationException {
        return execCommand(null, listener, reservedDevices, args);
    }

    /**
     * Determines if a given command is a dry-run. If the command is a dry-run, validate it. If
     * there are any configs issue, it will throw a ConfigurationException.
     *
     * @param handler {@link InvocationEventHandler} to report events for dry-run validation.
     * @param args the command to validate.
     * @return true if the command are a dry run, false otherwise.
     * @throws ConfigurationException
     */
    protected void dryRunCommandReporting(
            final IScheduledInvocationListener handler, IConfiguration config)
            throws ConfigurationException {
        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("stub", new BuildInfo());
        handler.invocationStarted(context);
        config.validateOptions();
        handler.invocationEnded(0);
        IInvocationContext nullMeta = null;
        handler.invocationComplete(nullMeta, null);
    }

    @VisibleForTesting
    protected long execCommand(
            IInvocationContext context,
            IScheduledInvocationListener listener,
            List<ITestDevice> reservedDevices,
            String[] args)
            throws ConfigurationException {
        assertStarted();
        IDeviceManager manager = getDeviceManager();
        IConfiguration config = createConfiguration(args);
        config.validateOptions();
        if (config.getCommandOptions().isDryRunMode()) {
            dryRunCommandReporting(listener, config);
            return -2L;
        }
        CommandTracker cmdTracker = createCommandTracker(args, null);

        if (isShuttingDown()) {
            if (context != null) {
                // createConfiguration can be long for things like sandbox, so ensure we did not
                // start a shutdown in the meantime.
                CLog.w("Tradefed is shutting down, ignoring command.");
                return -1;
            }
            // Prevent scheduling if we are shutting down
            throw new ConfigurationException("Tradefed shutting down, skip scheduling.");
        }

        Map<String, ITestDevice> allocatedDevices = new LinkedHashMap<String, ITestDevice>();
        if (!reservedDevices.isEmpty()) {
            allocatedDevices = getAllocatedDevices(config.getDeviceConfig(), reservedDevices);
        }
        if (reservedDevices.size() < config.getDeviceConfig().size()) {
            CLog.d(
                    "%s of %s devices reserved.",
                    reservedDevices.size(), config.getDeviceConfig().size());
            // Allow devices (like NullDevices) and ignore already allocated devices (reserved).
            DeviceAllocationResult allocationResults =
                    allocateDevices(config, manager, new ArrayList<>(allocatedDevices.keySet()));
            if (!allocationResults.wasAllocationSuccessful()) {
                // Log adb output just to help debug
                if (getDeviceManager() instanceof DeviceManager) {
                    String adbOutput =
                            ((DeviceManager) getDeviceManager()).executeGlobalAdbCommand("devices");
                    CLog.e("'adb devices' output:\n%s", adbOutput);
                }
                throw new NoDeviceException(
                        String.format(
                                "No device match for allocation. Reason: %s.\ncommand: %s",
                                allocationResults.formattedReason(), Arrays.asList(args)),
                        InfraErrorIdentifier.SCHEDULER_ALLOCATION_ERROR);
            }
            // Ensure devices (reserved or not) are ordered the same as in device config.
            Map<String, ITestDevice> orderedDevices = new LinkedHashMap<String, ITestDevice>();
            for (IDeviceConfiguration deviceConfig : config.getDeviceConfig()) {
                String deviceName = deviceConfig.getDeviceName();
                ITestDevice device = allocatedDevices.get(deviceName);
                if (device == null) {
                    device = allocationResults.getAllocatedDevices().get(deviceName);
                }
                orderedDevices.put(deviceName, device);
            }
            allocatedDevices = orderedDevices;
        }

        ExecutableCommand execCmd = createExecutableCommand(cmdTracker, config, false);
        synchronized (this) {
            mExecutingCommands.add(execCmd);
        }
        context = (context != null) ? context : createInvocationContext();
        context.setConfigurationDescriptor(config.getConfigurationDescription());
        context.addAllocatedDevice(allocatedDevices);
        CLog.d("Executing '%s' on '%s'", cmdTracker.getArgs()[0], allocatedDevices);
        if (reservedDevices.isEmpty()) {
            startInvocation(context, execCmd, listener, new FreeDeviceHandler(manager));
        } else {
            // Free NullDevice only. Leave alone reserved devices.
            startInvocation(context, execCmd, listener, new FreeNullDeviceHandler(manager));
        }
        return execCmd.getCommandTracker().getId();
    }

    /** Returns a map of device config name & device for reserved devices, excluding null devices */
    Map<String, ITestDevice> getAllocatedDevices(
            List<IDeviceConfiguration> deviceConfig, List<ITestDevice> reservedDevices) {
        Map<String, ITestDevice> allocatedDevices = new LinkedHashMap<String, ITestDevice>();
        int iReserved = 0;
        for (IDeviceConfiguration config : deviceConfig) {
            if (!config.getDeviceRequirements().nullDeviceRequested()) {
                if (iReserved >= reservedDevices.size()) {
                    throw new NoDeviceException(
                            String.format(
                                    "Reserved devices (%s) fewer than required.",
                                    reservedDevices.size()),
                            InfraErrorIdentifier.SCHEDULER_ALLOCATION_ERROR);
                }
                allocatedDevices.put(config.getDeviceName(), reservedDevices.get(iReserved++));
            }
        }
        if (iReserved < reservedDevices.size()) {
            throw new NoDeviceException(
                    String.format(
                            "Reserved devices (%s) more than required (%s).",
                            reservedDevices.size(), iReserved),
                    InfraErrorIdentifier.SCHEDULER_ALLOCATION_ERROR);
        }
        return allocatedDevices;
    }

    /** {@inheritDoc} */
    @Override
    public long execCommand(
            IScheduledInvocationListener listener, ITestDevice device, String[] args)
            throws ConfigurationException {
        return execCommand(listener, Arrays.asList(device), args);
    }

    /** {@inheritDoc} */
    @Override
    public long execCommand(IScheduledInvocationListener listener, String[] args)
            throws ConfigurationException, NoDeviceException {
        return execCommand(createInvocationContext(), listener, args);
    }

    /**
     * Allocate devices for a config.
     *
     * @param config a {@link IConfiguration} has device requirements.
     * @param manager a {@link IDeviceManager}
     * @return allocated devices
     */
    DeviceAllocationResult allocateDevices(IConfiguration config, IDeviceManager manager) {
        return allocateDevices(config, manager, new ArrayList<String>());
    }

    DeviceAllocationResult allocateDevices(
            IConfiguration config, IDeviceManager manager, ArrayList<String> excludeDevices) {
        Map<String, ITestDevice> devices = new LinkedHashMap<String, ITestDevice>();
        ITestDevice device = null;
        if (config.getDeviceConfig().isEmpty()) {
            return null;
        }
        // If we need to replicate the setup on all devices
        ParentShardReplicate.replicatedSetup(config, getKeyStoreClient());
        synchronized (this) {
            DeviceAllocationResult allocationResults = new DeviceAllocationResult();
            for (IDeviceConfiguration deviceConfig : config.getDeviceConfig()) {
                if (excludeDevices.contains(deviceConfig.getDeviceName())) {
                    continue;
                }
                device =
                        manager.allocateDevice(
                                deviceConfig.getDeviceRequirements(), deviceConfig.isFake());
                if (device != null) {
                    devices.put(deviceConfig.getDeviceName(), device);
                } else {
                    allocationResults.addAllocationFailureReason(
                            deviceConfig.getDeviceName(),
                            deviceConfig.getDeviceRequirements().getNoMatchReason());
                    // If one of the several device cannot be allocated, we de-allocate
                    // all the previous one.
                    for (ITestDevice allocatedDevice : devices.values()) {
                        FreeDeviceState deviceState = FreeDeviceState.AVAILABLE;
                        if (allocatedDevice.getIDevice() instanceof StubDevice) {
                            deviceState = FreeDeviceState.AVAILABLE;
                        } else if (!TestDeviceState.ONLINE.equals(
                                allocatedDevice.getDeviceState())) {
                            // If the device is offline at the end of the test
                            deviceState = FreeDeviceState.UNAVAILABLE;
                        }
                        manager.freeDevice(allocatedDevice, deviceState);
                    }
                    // Could not allocate all devices
                    devices.clear();
                    break;
                }
            }
            allocationResults.addAllocatedDevices(devices);
            return allocationResults;
        }
    }

    @VisibleForTesting
    protected IInvocationContext createInvocationContext() {
        return new InvocationContext();
    }

    /**
     * Spawns off thread to run invocation for given device.
     *
     * @param context the {@link IInvocationContext}
     * @param cmd the {@link ExecutableCommand} to execute
     * @param listeners the {@link
     *     com.android.tradefed.command.ICommandScheduler.IScheduledInvocationListener}s to invoke
     *     when complete
     */
    private void startInvocation(
            IInvocationContext context,
            ExecutableCommand cmd,
            IScheduledInvocationListener... listeners) {
        // Check if device is not used in another invocation.
        try {
            throwIfDeviceInInvocationThread(context.getDevices());
        } catch (Exception e) {
            setLastInvocationExitCode(ExitCode.THROWABLE_EXCEPTION, e);
            for (ITestDevice device : context.getDevices()) {
                getDeviceManager().freeDevice(device, FreeDeviceState.AVAILABLE);
            }
            throw e;
        }

        int invocationId = cmd.getCommandTracker().getId();
        CLog.d("starting invocation for command id %d", invocationId);
        // Name invocation with first device serial
        final String invocationName = String.format("Invocation-%s", context.getSerials().get(0));
        InvocationThread invocationThread =
                new InvocationThread(invocationName, context, cmd, listeners);
        if (getFeatureServer() != null) {
            getFeatureServer()
                    .registerInvocation(
                            cmd.getConfiguration(),
                            invocationThread.getThreadGroup(),
                            Arrays.asList(listeners));
        }
        // Link context and command
        context.addInvocationAttribute(
                IInvocationContext.INVOCATION_ID, Integer.toString(invocationId));
        logInvocationStartedEvent(cmd.getCommandTracker(), context);
        invocationThread.start();
        addInvocationThread(invocationThread);
    }

    /** Helper to log an invocation started event. */
    private void logInvocationStartedEvent(CommandTracker tracker, IInvocationContext context) {
        Map<String, String> args = new HashMap<>();
        args.put("id", Integer.toString(tracker.getId()));
        int i = 0;
        for (String serial : context.getSerials()) {
            args.put("serial" + i, serial);
            i++;
        }
        args.put("args", ArrayUtil.join(" ", Arrays.asList(tracker.getArgs())));
        logEvent(EventType.INVOCATION_START, args);
    }

    /** Removes a {@link InvocationThread} from the active list. */
    private synchronized void removeInvocationThread(InvocationThread invThread) {
        mInvocationThreadMap.remove(invThread.getInvocationContext());
        mInvocationThreadMapTerminating.put(invThread.getInvocationContext(), invThread);
    }

    private synchronized void clearTerminating(InvocationThread invThread) {
        mInvocationThreadMapTerminating.remove(invThread.getInvocationContext());
    }

    private synchronized void throwIfDeviceInInvocationThread(List<ITestDevice> devices) {
        for (ITestDevice device : devices) {
            if (isDeviceInInvocationThread(device)) {
                throw new HarnessRuntimeException(
                        String.format(
                                "Attempting invocation on device %s when one is already "
                                        + "running",
                                device.getSerialNumber()),
                        InfraErrorIdentifier.SCHEDULING_ERROR);
            }
        }
    }

    /** Adds a {@link InvocationThread} to the active list. */
    private synchronized void addInvocationThread(InvocationThread invThread) {
        mInvocationThreadMap.put(invThread.getInvocationContext(), invThread);
    }

    protected synchronized boolean isShutdown() {
        return mCommandTimer.isShutdown() || (mShutdownOnEmpty && getAllCommandsSize() == 0);
    }

    public synchronized boolean isShuttingDown() {
        return mCommandTimer.isShutdown() || mShutdownOnEmpty || mStopScheduling;
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void stopScheduling() {
        mStopScheduling = true;
        setHostState(HostState.QUITTING);
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void shutdown(boolean notifyStop) {
        setHostState(HostState.QUITTING);
        doShutdown();

        if (notifyStop) {
            String reason = "Tradefed is notified to stop";
            for (InvocationThread thread : mInvocationThreadMap.values()) {
                thread.notifyInvocationStop(reason);
            }
        }
    }

    private synchronized void doShutdown() {
        assertStarted();
        if (!isShuttingDown()) {
            CLog.d("initiating shutdown");
            removeAllCommands();
            if (mCommandFileWatcher != null) {
                mCommandFileWatcher.cancel();
            }
            if (mCommandTimer != null) {
                mCommandTimer.shutdownNow();
            }
            mCommandProcessWait.signalEventReceived();
        }
        CLog.logAndDisplay(LogLevel.INFO, "Received shutdown request.");
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void shutdownOnEmpty() {
        if (!mStarted) {
            CLog.w("Scheduler was not started, yet shutdownOnEmpty was called.");
            return;
        }
        setHostState(HostState.QUITTING);
        if (!isShuttingDown()) {
            CLog.d("initiating shutdown on empty");
            mShutdownOnEmpty = true;
            mCommandProcessWait.signalEventReceived();
        }
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void removeAllCommands() {
        assertStarted();
        CLog.d("removing all commands");
        if (mReloadCmdfiles) {
            getCommandFileWatcher().removeAllFiles();
        }
        if (mCommandTimer != null) {
            for (Runnable task : mCommandTimer.getQueue()) {
                mCommandTimer.remove(task);
            }
        }
        mReadyCommands.clear();
        mSleepingCommands.clear();
        if (isShuttingDown()) {
            mCommandProcessWait.signalEventReceived();
        }
    }

    /**
     * Remove commands originally added via the given command file
     *
     * @param cmdFile
     */
    private synchronized void removeCommandsFromFile(File cmdFile) {
        Iterator<ExecutableCommand> cmdIter = mReadyCommands.iterator();
        while (cmdIter.hasNext()) {
            ExecutableCommand cmd = cmdIter.next();
            String path = cmd.getCommandFilePath();
            if (path != null && path.equals(cmdFile.getAbsolutePath())) {
                cmdIter.remove();
            }
        }
        cmdIter = mSleepingCommands.iterator();
        while (cmdIter.hasNext()) {
            ExecutableCommand cmd = cmdIter.next();
            String path = cmd.getCommandFilePath();
            if (path != null && path.equals(cmdFile.getAbsolutePath())) {
                cmdIter.remove();
            }
        }
        if (isShuttingDown()) {
            mCommandProcessWait.signalEventReceived();
        }
    }

    /**
     * @return the list of active {@link CommandTracker}. 'Active' here means all commands added to
     *     the scheduler that are either executing, waiting for a device to execute on, or looping.
     */
    List<CommandTracker> getCommandTrackers() {
        List<ExecutableCommandState> cmdCopy = getAllCommands();
        Set<CommandTracker> cmdTrackers = new LinkedHashSet<CommandTracker>();
        for (ExecutableCommandState cmdState : cmdCopy) {
            cmdTrackers.add(cmdState.cmd.getCommandTracker());
        }
        return new ArrayList<CommandTracker>(cmdTrackers);
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void shutdownHard() {
        shutdownHard(true);
    }

    private synchronized void shutdownHard(boolean killAdb, String reason, ErrorIdentifier error) {
        setHostState(HostState.KILLING);
        doShutdown();
        CLog.logAndDisplay(LogLevel.WARN, "Stopping invocation threads...");
        for (InvocationThread thread : mInvocationThreadMap.values()) {
            thread.disableReporters();
            // TODO(b/118891716): Improve tear down
            thread.stopInvocation(reason, error);
        }
        if (killAdb) {
            getDeviceManager().terminateHard(reason);
        } else {
            getDeviceManager().terminate();
        }
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void shutdownHard(boolean killAdb) {
        String reason = "Tradefed is shutting down";
        shutdownHard(killAdb, reason, InfraErrorIdentifier.TRADEFED_SHUTTING_DOWN);
    }

    /**
     * Initializes the ddmlib log.
     *
     * <p>Exposed so unit tests can mock.
     */
    @SuppressWarnings("deprecation")
    protected void initLogging() {
        DdmPreferences.setLogLevel(LogLevel.VERBOSE.getStringValue());
        Log.setLogOutput(LogRegistry.getLogRegistry());
    }

    /**
     * Closes the logs and does any other necessary cleanup before we quit.
     *
     * <p>Exposed so unit tests can mock.
     */
    protected void cleanUp() {
        LogRegistry.getLogRegistry().closeAndRemoveAllLogs();
    }

    /** log an event to the registry history logger. */
    @VisibleForTesting
    void logEvent(EventType event, Map<String, String> args) {
        LogRegistry.getLogRegistry().logEvent(LogLevel.DEBUG, event, args);
    }

    /** {@inheritDoc} */
    @Override
    public void displayInvocationsInfo(PrintWriter printWriter) {
        assertStarted();
        if (mInvocationThreadMap == null || mInvocationThreadMap.size() == 0) {
            return;
        }
        List<InvocationThread> copy = new ArrayList<InvocationThread>();
        synchronized (this) {
            copy.addAll(mInvocationThreadMap.values());
        }
        ArrayList<List<String>> displayRows = new ArrayList<List<String>>();
        displayRows.add(Arrays.asList("Command Id", "Exec Time", "Device", "State"));
        long curTime = System.currentTimeMillis();

        for (InvocationThread invThread : copy) {
            displayRows.add(
                    Arrays.asList(
                            Integer.toString(invThread.mCmd.getCommandTracker().getId()),
                            getTimeString(curTime - invThread.getStartTime()),
                            invThread.getInvocationContext().getSerials().toString(),
                            invThread.getInvocation().toString()));
        }
        new TableFormatter().displayTable(displayRows, printWriter);
    }

    private String getTimeString(long elapsedTime) {
        long duration = elapsedTime / 1000;
        long secs = duration % 60;
        long mins = (duration / 60) % 60;
        long hrs = duration / (60 * 60);
        String time = "unknown";
        if (hrs > 0) {
            time = String.format("%dh:%02d:%02d", hrs, mins, secs);
        } else {
            time = String.format("%dm:%02d", mins, secs);
        }
        return time;
    }

    /** {@inheritDoc} */
    @Override
    public synchronized boolean stopInvocation(ITestInvocation invocation) {
        for (InvocationThread thread : mInvocationThreadMap.values()) {
            if (thread.getInvocation() == invocation) {
                thread.interrupt();
                return true;
            }
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public synchronized boolean stopInvocation(int invocationId, String cause) {
        // TODO: make invocationID part of InvocationContext
        for (InvocationThread thread : mInvocationThreadMap.values()) {
            if (thread.mCmd.getCommandTracker().mId == invocationId) {
                if (cause == null) {
                    cause = "User requested stopping invocation " + invocationId;
                }
                thread.stopInvocation(cause);
                return true;
            }
        }
        CLog.w("No invocation found matching the id: %s", invocationId);
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public synchronized String getInvocationInfo(int invocationId) {
        for (InvocationThread thread : mInvocationThreadMap.values()) {
            if (thread.mCmd.getCommandTracker().mId == invocationId) {
                String info = Arrays.toString(thread.mCmd.getCommandTracker().getArgs());
                return info;
            }
        }
        CLog.w("No invocation found matching the id: %s", invocationId);
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public void displayCommandsInfo(PrintWriter printWriter, String regex) {
        assertStarted();
        Pattern regexPattern = null;
        if (regex != null) {
            regexPattern = Pattern.compile(regex);
        }

        List<CommandTracker> cmds = getCommandTrackers();
        Collections.sort(cmds, new CommandTrackerIdComparator());
        for (CommandTracker cmd : cmds) {
            String argString = getArgString(cmd.getArgs());
            if (regexPattern == null || regexPattern.matcher(argString).find()) {
                String cmdDesc =
                        String.format(
                                "Command %d: [%s] %s",
                                cmd.getId(), getTimeString(cmd.getTotalExecTime()), argString);
                printWriter.println(cmdDesc);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void dumpCommandsXml(PrintWriter printWriter, String regex) {
        assertStarted();
        Pattern regexPattern = null;
        if (regex != null) {
            regexPattern = Pattern.compile(regex);
        }

        List<ExecutableCommandState> cmdCopy = getAllCommands();
        for (ExecutableCommandState cmd : cmdCopy) {
            String[] args = cmd.cmd.getCommandTracker().getArgs();
            String argString = getArgString(args);
            if (regexPattern == null || regexPattern.matcher(argString).find()) {
                // Use the config name prefixed by config__ for the file path
                String xmlPrefix = "config__" + args[0].replace("/", "__") + "__";

                // If the command line contains --template:map test config, use that config for the
                // file path.  This is because in the template system, many tests will have same
                // base config and the distinguishing feature is the test included.
                boolean templateIncludeFound = false;
                boolean testFound = false;
                for (String arg : args) {
                    if ("--template:map".equals(arg)) {
                        templateIncludeFound = true;
                    } else if (templateIncludeFound && "test".equals(arg)) {
                        testFound = true;
                    } else {
                        if (templateIncludeFound && testFound) {
                            xmlPrefix = "config__" + arg.replace("/", "__") + "__";
                        }
                        templateIncludeFound = false;
                        testFound = false;
                    }
                }

                try {
                    File xmlFile = FileUtil.createTempFile(xmlPrefix, ".xml");
                    PrintWriter writer = new PrintWriter(xmlFile);
                    cmd.cmd.getConfiguration().dumpXml(writer);
                    printWriter.println(
                            String.format("Saved command dump to %s", xmlFile.getAbsolutePath()));
                } catch (IOException e) {
                    // Log exception and continue
                    CLog.e("Could not dump config xml");
                    CLog.e(e);
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void displayCommandQueue(PrintWriter printWriter) {
        assertStarted();
        List<ExecutableCommandState> cmdCopy = getAllCommands();
        if (cmdCopy.size() == 0) {
            return;
        }
        ArrayList<List<String>> displayRows = new ArrayList<List<String>>();
        displayRows.add(
                Arrays.asList(
                        "Id",
                        "Config",
                        "Created",
                        "Exec time",
                        "State",
                        "Sleep time",
                        "Rescheduled",
                        "Loop"));
        long curTime = System.currentTimeMillis();
        for (ExecutableCommandState cmd : cmdCopy) {
            dumpCommand(curTime, cmd, displayRows);
        }
        new TableFormatter().displayTable(displayRows, printWriter);
    }

    private void dumpCommand(
            long curTime, ExecutableCommandState cmdAndState, ArrayList<List<String>> displayRows) {
        ExecutableCommand cmd = cmdAndState.cmd;
        String sleepTime = cmd.getSleepTime() == null ? "N/A" : getTimeString(cmd.getSleepTime());
        displayRows.add(
                Arrays.asList(
                        Integer.toString(cmd.getCommandTracker().getId()),
                        cmd.getCommandTracker().getArgs()[0],
                        getTimeString(curTime - cmd.getCreationTime()),
                        getTimeString(cmd.mCmdTracker.getTotalExecTime()),
                        cmdAndState.state.getDisplayName(),
                        sleepTime,
                        Boolean.toString(cmd.isRescheduled()),
                        Boolean.toString(cmd.isLoopMode())));
    }

    /**
     * Helper object for allowing multiple threads to synchronize on an event.
     *
     * <p>Basically a modest wrapper around Object's wait and notify methods, that supports
     * remembering if a notify call was made.
     */
    private static class WaitObj {
        boolean mEventReceived = false;

        /**
         * Wait for signal for a max of given ms.
         *
         * @return true if event received before time elapsed, false otherwise
         */
        public synchronized boolean waitForEvent(long maxWaitTime) {
            if (maxWaitTime == 0) {
                return waitForEvent();
            }
            long startTime = System.currentTimeMillis();
            long remainingTime = maxWaitTime;
            while (!mEventReceived && remainingTime > 0) {
                try {
                    wait(remainingTime);
                } catch (InterruptedException e) {
                    CLog.w("interrupted");
                }
                remainingTime = maxWaitTime - (System.currentTimeMillis() - startTime);
            }
            return mEventReceived;
        }

        /**
         * Wait for signal indefinitely or until interrupted.
         *
         * @return true if event received, false otherwise
         */
        public synchronized boolean waitForEvent() {
            if (!mEventReceived) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    CLog.w("interrupted");
                }
            }
            return mEventReceived;
        }

        /** Reset the event received flag. */
        public synchronized void reset() {
            mEventReceived = false;
        }

        /**
         * Wait for given ms for event to be received, and reset state back to 'no event received'
         * upon completion.
         */
        public synchronized void waitAndReset(long maxWaitTime) {
            waitForEvent(maxWaitTime);
            reset();
        }

        /** Notify listeners that event was received. */
        public synchronized void signalEventReceived() {
            mEventReceived = true;
            notifyAll();
        }
    }

    private synchronized void assertStarted() {
        if (!mStarted) {
            throw new IllegalStateException("start() must be called before this method");
        }
    }

    @Override
    public void notifyFileChanged(File cmdFile, List<String> extraArgs) {
        CLog.logAndDisplay(
                LogLevel.INFO,
                "Detected update for cmdfile '%s'. Reloading",
                cmdFile.getAbsolutePath());
        removeCommandsFromFile(cmdFile);
        try {
            // just add the file again, including re-registering for command file watcher
            // don't want to remove the registration here in case file fails to load
            internalAddCommandFile(cmdFile, extraArgs);
        } catch (ConfigurationException e) {
            CLog.wtf(
                    String.format(
                            "Failed to automatically reload cmdfile %s", cmdFile.getAbsolutePath()),
                    e);
        }
    }

    /** Set the command file reloading flag. */
    @VisibleForTesting
    void setCommandFileReload(boolean b) {
        mReloadCmdfiles = b;
    }

    synchronized int getAllCommandsSize() {
        return mReadyCommands.size() + mExecutingCommands.size() + mSleepingCommands.size();
    }

    synchronized List<ExecutableCommandState> getAllCommands() {
        List<ExecutableCommandState> cmds = new ArrayList<>(getAllCommandsSize());
        for (ExecutableCommand cmd : mExecutingCommands) {
            cmds.add(new ExecutableCommandState(cmd, CommandState.EXECUTING));
        }
        for (ExecutableCommand cmd : mReadyCommands) {
            cmds.add(new ExecutableCommandState(cmd, CommandState.WAITING_FOR_DEVICE));
        }
        for (ExecutableCommand cmd : mSleepingCommands) {
            cmds.add(new ExecutableCommandState(cmd, CommandState.SLEEPING));
        }
        return cmds;
    }

    @Override
    public boolean shouldShutdownOnCmdfileError() {
        return mShutdownOnCmdfileError;
    }

    public long getShutdownTimeout() {
        return mShutdownTimeout;
    }

    @Override
    public ExitCode getLastInvocationExitCode() {
        return mLastInvocationExitCode;
    }

    @Override
    public Throwable getLastInvocationThrowable() {
        return mLastInvocationThrowable;
    }

    private void setLastInvocationExitCode(ExitCode code, Throwable throwable) {
        mLastInvocationExitCode = code;
        mLastInvocationThrowable = throwable;
    }

    @Override
    public synchronized int getReadyCommandCount() {
        return mReadyCommands.size();
    }

    @Override
    public synchronized int getExecutingCommandCount() {
        return mExecutingCommands.size();
    }

    @Override
    public void setClearcutClient(ClearcutClient client) {
        mClient = client;
    }

    @Override
    public synchronized boolean isDeviceInInvocationThread(ITestDevice device) {
        for (IInvocationContext context : mInvocationThreadMap.keySet()) {
            if (context.getDevices().contains(device)) {
                return !context.wasReleasedEarly();
            }
        }
        return false;
    }
}
