/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.clearcut.ClearcutClient;
import com.android.tradefed.clearcut.TerminateClearcutClient;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.device.NoDeviceException;
import com.android.tradefed.invoker.tracing.ActiveTrace;
import com.android.tradefed.invoker.tracing.CloseableTraceScope;
import com.android.tradefed.invoker.tracing.TracingLogger;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.service.TradefedFeatureServer;
import com.android.tradefed.testtype.suite.TestSuiteInfo;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.SerializationUtil;
import com.android.tradefed.util.SystemUtil;

import com.google.common.annotations.VisibleForTesting;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import sun.misc.Signal;
import sun.misc.SignalHandler;

/**
 * An alternate TradeFederation entry point that will run command specified in command
 * line arguments and then quit.
 * <p/>
 * Intended for use with a debugger and other non-interactive modes of operation.
 * <p/>
 * Expected arguments: [commands options] (config to run)
 */
public class CommandRunner {
    private ICommandScheduler mScheduler;
    private ExitCode mErrorCode = ExitCode.NO_ERROR;

    public static final String EXCEPTION_KEY = "serialized_exception";
    public static final String START_FEATURE_SERVER = "START_FEATURE_SERVER";
    private static final long CHECK_DEVICE_TIMEOUT = 60000;

    public CommandRunner() {}

    public ExitCode getErrorCode() {
        return mErrorCode;
    }

    /**
     * Initialize the required global configuration.
     */
    @VisibleForTesting
    void initGlobalConfig(String[] args) throws ConfigurationException {
        GlobalConfiguration.createGlobalConfiguration(args);
        GlobalConfiguration.getInstance().setup();
    }

    /** Get the {@link ICommandScheduler} instance from the global configuration. */
    @VisibleForTesting
    ICommandScheduler getCommandScheduler() {
        return GlobalConfiguration.getInstance().getCommandScheduler();
    }

    /** Prints the exception stack to stderr. */
    @VisibleForTesting
    void printStackTrace(Throwable e) {
        e.printStackTrace();
        File serializedException = null;
        try {
            serializedException = SerializationUtil.serialize(e);
            JSONObject json = new JSONObject();
            json.put(EXCEPTION_KEY, serializedException.getAbsolutePath());
            System.err.println(json.toString());
            System.err.flush();
        } catch (IOException | JSONException io) {
            io.printStackTrace();
            FileUtil.deleteFile(serializedException);
        }
    }

    /** Returns the timeout after which to check for the command. */
    @VisibleForTesting
    long getCheckDeviceTimeout() {
        return CHECK_DEVICE_TIMEOUT;
    }

    /**
     * The main method to run the command.
     *
     * @param args the config name to run and its options
     */
    public void run(String[] args) {
        try {
            CompletableFuture<ClearcutClient> futureClient =
                    CompletableFuture.supplyAsync(() -> createClient());
            try (CloseableTraceScope ignored = new CloseableTraceScope("initGlobalConfig")) {
                initGlobalConfig(args);
            }

            ClearcutClient client = futureClient.get();
            Runtime.getRuntime().addShutdownHook(new TerminateClearcutClient(client));
            client.notifyTradefedStartEvent();
            if (System.getenv(START_FEATURE_SERVER) != null) {
                // Starting the server takes 100ms so do it in parallel
                CompletableFuture.supplyAsync(() -> startFeatureSever());
            }

            mScheduler = getCommandScheduler();
            mScheduler.setClearcutClient(client);
            mScheduler.start();
            SignalHandler handler =
                    new SignalHandler() {
                        @Override
                        public void handle(Signal sig) {
                            CLog.logAndDisplay(
                                    LogLevel.INFO,
                                    String.format(
                                            "Received signal %s. Shutting down.", sig.getName()));
                            mScheduler.shutdownHard(false);
                        }
                    };
            Signal.handle(new Signal("TERM"), handler);

            mScheduler.addCommand(args);
        } catch (ConfigurationException
                | RuntimeException
                | InterruptedException
                | ExecutionException e) {
            printStackTrace(e);
            mErrorCode = ExitCode.CONFIG_EXCEPTION;
            return;
        } finally {
            if (mScheduler != null) {
                mScheduler.shutdownOnEmpty();
            }
        }
        try {
            mScheduler.join(getCheckDeviceTimeout());
            // FIXME: if possible make the no_device allocated check deterministic.
            // After 1 min we check if the command was executed.
            if (mScheduler.getReadyCommandCount() > 0
                    && mScheduler.getExecutingCommandCount() == 0) {
                printStackTrace(
                        new NoDeviceException(
                                "No device was allocated for the command.",
                                InfraErrorIdentifier.RUNNER_ALLOCATION_ERROR));
                mErrorCode = ExitCode.NO_DEVICE_ALLOCATED;
                mScheduler.removeAllCommands();
                mScheduler.shutdown();
                return;
            }
            mScheduler.join();
            // If no error code has been raised yet, we checked the invocation error code.
            if (ExitCode.NO_ERROR.equals(mErrorCode)) {
                mErrorCode = mScheduler.getLastInvocationExitCode();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            mErrorCode = ExitCode.THROWABLE_EXCEPTION;
        } finally {
            GlobalConfiguration.getInstance().cleanup();
        }
        if (!ExitCode.NO_ERROR.equals(mErrorCode)
                && mScheduler.getLastInvocationThrowable() != null) {
            // Print error to the stderr so that it can be recovered.
            printStackTrace(mScheduler.getLastInvocationThrowable());
        }
    }

    protected ClearcutClient createClient() {
        try (CloseableTraceScope ignored = new CloseableTraceScope("createClient")) {
            return new ClearcutClient(
                    TestSuiteInfo.getInstance().didLoadFromProperties()
                            ? TestSuiteInfo.getInstance().getName()
                            : "");
        }
    }

    public static void main(final String[] mainArgs) {
        long pid = ProcessHandle.current().pid();
        long tid = Thread.currentThread().getId();
        ActiveTrace trace = null;
        // Enable Full Tradefed tracing in local mode only for now
        if (SystemUtil.isLocalMode()) {
            trace = TracingLogger.createActiveTrace(pid, tid, true);
            trace.startTracing(false);
        }
        CommandRunner console = new CommandRunner();
        try (CloseableTraceScope ignored = new CloseableTraceScope("end_to_end_command")) {
            console.run(mainArgs);
        } finally {
            if (trace != null) {
                trace.finalizeTracing();
            }
        }
        System.exit(console.getErrorCode().getCodeValue());
    }

    /**
     * Error codes that are possible to exit with.
     */
    public static enum ExitCode {
        NO_ERROR(0),
        CONFIG_EXCEPTION(1),
        NO_BUILD(2),
        DEVICE_UNRESPONSIVE(3),
        DEVICE_UNAVAILABLE(4),
        FATAL_HOST_ERROR(5),
        THROWABLE_EXCEPTION(6),
        NO_DEVICE_ALLOCATED(7),
        WRONG_JAVA_VERSION(8);

        private final int mCodeValue;

        ExitCode(int codeValue) {
            mCodeValue = codeValue;
        }

        public int getCodeValue() {
            return mCodeValue;
        }
    }

    private boolean startFeatureSever() {
        try (CloseableTraceScope f = new CloseableTraceScope("start_fr_client")) {
            TradefedFeatureServer server = new TradefedFeatureServer();
            server.start();
            GlobalConfiguration.getInstance().setTradefedFeatureServer(server);
        } catch (RuntimeException e) {
            System.out.println(String.format("Error starting feature server: %s", e));
        }
        return true;
    }
}
