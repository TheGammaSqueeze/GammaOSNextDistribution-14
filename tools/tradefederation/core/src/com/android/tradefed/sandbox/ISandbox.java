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
package com.android.tradefed.sandbox;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IFolderBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.keystore.IKeyStoreClient;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Interface defining a sandbox that can be used to run an invocation. */
public interface ISandbox {

    /**
     * Prepare the environment for the sandbox to run properly.
     *
     * @param context the current invocation {@link IInvocationContext}.
     * @param configuration the {@link IConfiguration} for the command to run.
     * @param listener the current invocation {@link ITestInvocationListener} where final results
     *     should be piped.
     * @return an {@link Exception} containing the failure. or Null if successful.
     */
    public Exception prepareEnvironment(
            IInvocationContext context,
            IConfiguration configuration,
            ITestInvocationListener listener);

    /**
     * Sub-step of {@link #prepareEnvironment(IInvocationContext, IConfiguration,
     * ITestInvocationListener)} which fetch additional files needed for the sandbox.
     *
     * @param context the current invocation {@link IInvocationContext}.
     * @param configuration the {@link IConfiguration} for the command to run.
     * @param args the command line arguments.
     * @return the fetched build for the additional sandboxed files.
     * @throws BuildRetrievalError
     * @throws ConfigurationException
     * @throws IOException
     */
    public default IFolderBuildInfo fetchSandboxExtraArtifacts(
            IInvocationContext context, IConfiguration configuration, String[] args)
            throws BuildRetrievalError, ConfigurationException, IOException {
        return null;
    }

    /**
     * A sub-step of {@link #prepareEnvironment(IInvocationContext, IConfiguration,
     * ITestInvocationListener)} which discovers tests if {@link
     * SandboxOptions#shouldUseTestDiscovery()} is enabled.
     *
     * @param context the current invocation {@link IInvocationContext}.
     * @param configuration the {@link IConfiguration} for the command to run.
     * @return The map of discovered tests or null if unsupported or failed.
     */
    public default Map<String, List<String>> discoverTests(
            IInvocationContext context, IConfiguration configuration) {
        return null;
    }

    public default Map<String, List<String>> discoverTests(
            IInvocationContext context, IConfiguration configuration, ITestLogger logger) {
        return discoverTests(context, configuration);
    }

    /**
     * Run the sandbox with the environment that was set.
     *
     * @param info the {@link TestInformation} describing the invocation
     * @param configuration the {@link IConfiguration} for the command to run.
     * @param logger an {@link ITestLogger} where we can log files.
     * @return a {@link CommandResult} with the status of the sandbox run and logs.
     */
    public CommandResult run(TestInformation info, IConfiguration configuration, ITestLogger logger)
            throws Throwable;

    /** Clean up any states, files or environment that may have been changed. */
    public void tearDown();

    /**
     * Returns the sandbox environment TF to be used based on the command line arguments.
     *
     * @param context the {@link IInvocationContext} of the parent.
     * @param nonVersionedConfig the {@link IConfiguration} representing the non versioned objects.
     * @param logger the {@link ITestLogger} to log additional files.
     * @param args the command line arguments.
     * @return a {@link File} directory containing the TF sandbox environment jars.
     */
    public default File getTradefedSandboxEnvironment(
            IInvocationContext context, IConfiguration nonVersionedConfig, String[] args)
            throws Exception {
        return null;
    }

    public default File getTradefedSandboxEnvironment(
            IInvocationContext context,
            IConfiguration nonVersionedConfig,
            ITestLogger logger,
            String[] args)
            throws Exception {
        return getTradefedSandboxEnvironment(context, nonVersionedConfig, args);
    }

    /**
     * Create a classpath based on the environment and the working directory returned by {@link
     * #getTradefedSandboxEnvironment(IInvocationContext, IConfiguration, String[])}.
     *
     * @param workingDir the current working directory for the sandbox.
     * @return The classpath to be use.
     */
    public String createClasspath(File workingDir) throws ConfigurationException;

    /**
     * Special mode disconnected from main run: When a configuration does not appear to exists in
     * the parent, we fallback to thin launcher where we attempt to setup the sandbox with currently
     * known informations and fill up the working directory to create the config fully in the
     * versioned dir.
     *
     * @param args The original command line args.
     * @param keyStoreClient the current keystore client to use to create configurations.
     * @param runUtil the current {@link IRunUtil} to run host commands.
     * @param globalConfig The global configuration to use to run subprocesses of TF.
     * @return a File pointing to the configuration XML of TF for NON_VERSIONED objects. Returns
     *     null if no thin launcher config could be created.
     */
    public IConfiguration createThinLauncherConfig(
            String[] args, IKeyStoreClient keyStoreClient, IRunUtil runUtil, File globalConfig);
}
