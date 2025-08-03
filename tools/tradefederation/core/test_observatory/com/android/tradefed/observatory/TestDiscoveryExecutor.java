/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tradefed.observatory;

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.Log;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.invoker.tracing.ActiveTrace;
import com.android.tradefed.invoker.tracing.CloseableTraceScope;
import com.android.tradefed.invoker.tracing.TracingLogger;
import com.android.tradefed.log.LogRegistry;
import com.android.tradefed.log.StdoutLogger;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.suite.BaseTestSuite;
import com.android.tradefed.testtype.suite.ITestSuite.MultiDeviceModuleStrategy;
import com.android.tradefed.testtype.suite.SuiteTestFilter;
import com.android.tradefed.testtype.suite.TestMappingSuiteRunner;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IDisableable;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.keystore.DryRunKeyStore;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A class for getting test modules and target preparers for a given command line args.
 *
 * <p>TestDiscoveryExecutor will consume the command line args and print test module names and
 * target preparer apks on stdout for the parent TradeFed process to receive and parse it.
 *
 * <p>
 */
public class TestDiscoveryExecutor {

    IConfigurationFactory getConfigurationFactory() {
        return ConfigurationFactory.getInstance();
    }

    private boolean mReportPartialFallback = false;
    private boolean mReportNoPossibleDiscovery = false;

    private static boolean hasOutputResultFile() {
        return System.getenv(TestDiscoveryInvoker.OUTPUT_FILE) != null;
    }

    /**
     * An TradeFederation entry point that will use command args to discover test artifact
     * information.
     *
     * <p>Intended for use with cts partial download to only download necessary files for the test
     * run.
     *
     * <p>Will only exit with 0 when successfully discovered test modules.
     *
     * <p>Expected arguments: [commands options] (config to run)
     */
    public static void main(String[] args) {
        long pid = ProcessHandle.current().pid();
        long tid = Thread.currentThread().getId();
        ActiveTrace trace = TracingLogger.createActiveTrace(pid, tid);
        trace.startTracing(false);
        DiscoveryExitCode exitCode = DiscoveryExitCode.SUCCESS;
        TestDiscoveryExecutor testDiscoveryExecutor = new TestDiscoveryExecutor();
        try (CloseableTraceScope ignored = new CloseableTraceScope("main_discovery")) {
            String testModules = testDiscoveryExecutor.discoverDependencies(args);
            if (hasOutputResultFile()) {
                FileUtil.writeToFile(
                        testModules, new File(System.getenv(TestDiscoveryInvoker.OUTPUT_FILE)));
            }
            System.out.print(testModules);
        } catch (TestDiscoveryException e) {
            System.err.print(e.getMessage());
            if (e.exitCode() != null) {
                exitCode = e.exitCode();
            } else {
                exitCode = DiscoveryExitCode.ERROR;
            }
        } catch (ConfigurationException e) {
            System.err.print(e.getMessage());
            exitCode = DiscoveryExitCode.CONFIGURATION_EXCEPTION;
        } catch (Exception e) {
            System.err.print(e.getMessage());
            exitCode = DiscoveryExitCode.ERROR;
        }
        File traceFile = trace.finalizeTracing();
        if (traceFile != null) {
            if (System.getenv(TestDiscoveryInvoker.DISCOVERY_TRACE_FILE) != null) {
                try {
                    FileUtil.copyFile(
                            traceFile,
                            new File(System.getenv(TestDiscoveryInvoker.DISCOVERY_TRACE_FILE)));
                } catch (IOException | RuntimeException e) {
                    System.err.print(e.getMessage());
                }
            }
            FileUtil.deleteFile(traceFile);
        }
        System.exit(exitCode.exitCode());
    }

    /**
     * Discover test dependencies base on command line args.
     *
     * @param args the command line args of the test.
     * @return A JSON string with one test module names array and one other test dependency array.
     */
    public String discoverDependencies(String[] args)
            throws TestDiscoveryException, ConfigurationException, JSONException {
        // Create IConfiguration base on command line args.
        IConfiguration config = getConfiguration(args);

        if (hasOutputResultFile()) {
            DdmPreferences.setLogLevel(LogLevel.VERBOSE.getStringValue());
            Log.setLogOutput(LogRegistry.getLogRegistry());
            StdoutLogger logger = new StdoutLogger();
            logger.setLogLevel(LogLevel.VERBOSE);
            LogRegistry.getLogRegistry().registerLogger(logger);
        }
        try {
            // Get tests from the configuration.
            List<IRemoteTest> tests = config.getTests();

            // Tests could be empty if input args are corrupted.
            if (tests == null || tests.isEmpty()) {
                throw new TestDiscoveryException(
                        "Tradefed Observatory discovered no tests from the IConfiguration created"
                                + " from command line args.",
                        null,
                        DiscoveryExitCode.ERROR);
            }

            List<String> testModules = new ArrayList<>(discoverTestModulesFromTests(tests));
            List<String> testDependencies = new ArrayList<>(discoverDependencies(config));
            Collections.sort(testModules);
            Collections.sort(testDependencies);

            try (CloseableTraceScope ignored = new CloseableTraceScope("format_results")) {
                JSONObject j = new JSONObject();
                j.put(TestDiscoveryInvoker.TEST_MODULES_LIST_KEY, new JSONArray(testModules));
                j.put(
                        TestDiscoveryInvoker.TEST_DEPENDENCIES_LIST_KEY,
                        new JSONArray(testDependencies));
                if (mReportPartialFallback) {
                    j.put(TestDiscoveryInvoker.PARTIAL_FALLBACK_KEY, "true");
                }
                if (mReportNoPossibleDiscovery) {
                    j.put(TestDiscoveryInvoker.NO_POSSIBLE_TEST_DISCOVERY_KEY, "true");
                }
                return j.toString();
            }
        } finally {
            if (hasOutputResultFile()) {
                LogRegistry.getLogRegistry().unregisterLogger();
            }
        }
    }

    /**
     * Retrieve configuration base on command line args.
     *
     * @param args the command line args of the test.
     * @return A {@link IConfiguration} which constructed based on command line args.
     */
    private IConfiguration getConfiguration(String[] args) throws ConfigurationException {
        try (CloseableTraceScope ignored = new CloseableTraceScope("create_configuration")) {
            IConfigurationFactory configurationFactory = getConfigurationFactory();
            return configurationFactory.createPartialConfigurationFromArgs(
                    args,
                    new DryRunKeyStore(),
                    Set.of(Configuration.TEST_TYPE_NAME, Configuration.TARGET_PREPARER_TYPE_NAME),
                    null);
        }
    }

    /**
     * Discover configuration by a list of {@link IRemoteTest}.
     *
     * @param testList a list of {@link IRemoteTest}.
     * @return A set of test module names.
     */
    private Set<String> discoverTestModulesFromTests(List<IRemoteTest> testList)
            throws IllegalStateException, TestDiscoveryException {
        try (CloseableTraceScope ignored =
                new CloseableTraceScope("discoverTestModulesFromTests")) {
            Set<String> testModules = new LinkedHashSet<String>();
            Set<String> includeFilters = new LinkedHashSet<String>();
            Set<String> excludeFilters = new LinkedHashSet<String>();
            // Collect include filters from every test.
            boolean discoveredLogic = true;
            for (IRemoteTest test : testList) {
                if (!(test instanceof BaseTestSuite)) {
                    throw new TestDiscoveryException(
                            "Tradefed Observatory can't do test discovery on non suite-based test"
                                    + " runner.",
                            null,
                            DiscoveryExitCode.ERROR);
                }
                if (test instanceof TestMappingSuiteRunner) {
                    ((TestMappingSuiteRunner) test).loadTestInfos();
                }
                Set<String> suiteIncludeFilters = ((BaseTestSuite) test).getIncludeFilter();
                excludeFilters.addAll(((BaseTestSuite) test).getExcludeFilter());
                MultiMap<String, String> moduleMetadataIncludeFilters =
                        ((BaseTestSuite) test).getModuleMetadataIncludeFilters();
                // Include/Exclude filters in suites are evaluated first,
                // then metadata are applied on top, so having metadata filters
                // and include-filters can actually be resolved to a super-set
                // which is better than falling back.
                if (!excludeFilters.isEmpty() && ((BaseTestSuite) test).reverseExcludeFilters()) {
                    includeFilters.addAll(excludeFilters);
                    excludeFilters.clear();
                } else if (!suiteIncludeFilters.isEmpty()) {
                    includeFilters.addAll(suiteIncludeFilters);
                } else if (!moduleMetadataIncludeFilters.isEmpty()) {
                    String rootDirPath =
                            getEnvironment(TestDiscoveryInvoker.ROOT_DIRECTORY_ENV_VARIABLE_KEY);
                    boolean throwException = true;
                    if (rootDirPath != null) {
                        File rootDir = new File(rootDirPath);
                        if (rootDir.exists() && rootDir.isDirectory()) {
                            Set<String> configs =
                                    searchConfigsForMetadata(rootDir, moduleMetadataIncludeFilters);
                            if (configs != null) {
                                testModules.addAll(configs);
                                throwException = false;
                                mReportPartialFallback = true;
                            }
                        }
                    }
                    if (throwException) {
                        throw new TestDiscoveryException(
                                "Tradefed Observatory can't do test discovery because the existence"
                                        + " of metadata include filter option.",
                                null,
                                DiscoveryExitCode.COMPONENT_METADATA);
                    }
                } else if (MultiDeviceModuleStrategy.ONLY_MULTI_DEVICES.equals(
                        ((BaseTestSuite) test).getMultiDeviceStrategy())) {
                    String rootDirPath =
                            getEnvironment(TestDiscoveryInvoker.ROOT_DIRECTORY_ENV_VARIABLE_KEY);
                    boolean throwException = true;
                    if (rootDirPath != null) {
                        File rootDir = new File(rootDirPath);
                        if (rootDir.exists() && rootDir.isDirectory()) {
                            Set<String> configs = searchForMultiDevicesConfig(rootDir);
                            if (configs != null) {
                                testModules.addAll(configs);
                                throwException = false;
                                mReportPartialFallback = true;
                            }
                        }
                    }
                    if (throwException) {
                        throw new TestDiscoveryException(
                                "Tradefed Observatory can't do test discovery because the existence"
                                        + " of multi-devices option.",
                                null,
                                DiscoveryExitCode.COMPONENT_METADATA);
                    }
                } else if (!Strings.isNullOrEmpty(((BaseTestSuite) test).getRunSuiteTag())) {
                    String rootDirPath =
                            getEnvironment(TestDiscoveryInvoker.ROOT_DIRECTORY_ENV_VARIABLE_KEY);
                    boolean throwException = true;
                    if (rootDirPath != null) {
                        File rootDir = new File(rootDirPath);
                        if (rootDir.exists() && rootDir.isDirectory()) {
                            Set<String> configs =
                                    searchConfigsForSuiteTag(
                                            rootDir, ((BaseTestSuite) test).getRunSuiteTag());
                            if (configs != null) {
                                testModules.addAll(configs);
                                throwException = false;
                                mReportPartialFallback = true;
                            }
                        }
                    }
                    if (throwException) {
                        throw new TestDiscoveryException(
                                "Tradefed Observatory can't do test discovery because the existence"
                                        + " of run-suite-tag option.",
                                null,
                                DiscoveryExitCode.COMPONENT_METADATA);
                    }
                } else {
                    discoveredLogic = false;
                }
            }
            if (!discoveredLogic) {
                mReportNoPossibleDiscovery = true;
            }
            // Extract test module names from included filters.
            if (hasOutputResultFile()) {
                System.out.println(String.format("include filters: %s", includeFilters));
            }
            Set<String> moduleOnlyIncludeFilters =
                    extractTestModulesFromIncludeFilters(includeFilters);
            testModules.addAll(moduleOnlyIncludeFilters);
            testModules.addAll(findExtraConfigsParents(moduleOnlyIncludeFilters));
            // Any directly excluded won't be discovered since it shouldn't run
            testModules.removeAll(excludeFilters);
            return testModules;
        }
    }

    /**
     * Extract test module names from include filters.
     *
     * @param includeFilters a set of include filters.
     * @return A set of test module names.
     */
    private Set<String> extractTestModulesFromIncludeFilters(Set<String> includeFilters)
            throws IllegalStateException {
        Set<String> testModuleNames = new LinkedHashSet<>();
        // Extract module name from each include filter.
        // TODO: Ensure if a module is fully excluded then it's excluded.
        for (String includeFilter : includeFilters) {
            String testModuleName = SuiteTestFilter.createFrom(includeFilter).getBaseName();
            if (testModuleName == null) {
                // If unable to parse an include filter, throw exception to exit.
                throw new IllegalStateException(
                        String.format(
                                "Unable to parse test module name from include filter %s",
                                includeFilter));
            } else {
                testModuleNames.add(testModuleName);
            }
        }
        return testModuleNames;
    }

    /**
     * When using extra_test_configs in Soong, they end up in the original module named folder, so
     * search for it to backfill the download
     */
    private Set<String> findExtraConfigsParents(Set<String> moduleNames) {
        Set<String> parentModules = new HashSet<>();
        String rootDirPath = getEnvironment(TestDiscoveryInvoker.ROOT_DIRECTORY_ENV_VARIABLE_KEY);
        if (rootDirPath == null) {
            CLog.w("root dir env not set.");
            return parentModules;
        }
        CLog.d("Seaching parent configs.");
        try (CloseableTraceScope ignored = new CloseableTraceScope("find parent configs")) {
            for (String name : moduleNames) {
                File config = FileUtil.findFile(new File(rootDirPath), name + ".config");
                if (config != null) {
                    if (!config.getParentFile().getName().equals(name)) {
                        CLog.d(
                                "Parent: %s being added for the extra configs",
                                config.getParentFile().getName());
                        parentModules.add(config.getParentFile().getName());
                    }
                }
            }
        }
        CLog.d("Done searching parent configs.");
        return parentModules;
    }

    private Set<String> discoverDependencies(IConfiguration config) {
        Set<String> dependencies = new HashSet<>();
        try (CloseableTraceScope ignored = new CloseableTraceScope("discoverDependencies")) {
            for (Object o :
                    config.getAllConfigurationObjectsOfType(
                            Configuration.TARGET_PREPARER_TYPE_NAME)) {
                if (o instanceof IDisableable) {
                    if (((IDisableable) o).isDisabled()) {
                        continue;
                    }
                }
                if (o instanceof IDiscoverDependencies) {
                    dependencies.addAll(((IDiscoverDependencies) o).reportDependencies());
                }
            }
            return dependencies;
        }
    }

    private Set<String> searchForMultiDevicesConfig(File rootDir) {
        try {
            Set<File> configFiles = FileUtil.findFilesObject(rootDir, ".*\\.config$");
            if (configFiles.isEmpty()) {
                return null;
            }
            Set<File> shouldRunFiles =
                    configFiles.stream()
                            .filter(
                                    f -> {
                                        try {
                                            IConfiguration c =
                                                    getConfigurationFactory()
                                                            .createPartialConfigurationFromArgs(
                                                                    new String[] {
                                                                        f.getAbsolutePath()
                                                                    },
                                                                    new DryRunKeyStore(),
                                                                    ImmutableSet.of(
                                                                            Configuration
                                                                                    .CONFIGURATION_DESCRIPTION_TYPE_NAME),
                                                                    null);
                                            return c.getDeviceConfig().size() > 1;
                                        } catch (ConfigurationException e) {
                                            return false;
                                        }
                                    })
                            .collect(Collectors.toSet());
            return shouldRunFiles.stream()
                    .map(c -> FileUtil.getBaseName(c.getName()))
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            System.err.println(e);
        }
        return null;
    }

    private Set<String> searchConfigsForMetadata(
            File rootDir, MultiMap<String, String> moduleMetadataIncludeFilters) {
        try {
            Set<File> configFiles = FileUtil.findFilesObject(rootDir, ".*\\.config$");
            if (configFiles.isEmpty()) {
                return null;
            }
            Set<File> shouldRunFiles =
                    configFiles.stream()
                            .filter(
                                    f -> {
                                        try {
                                            IConfiguration c =
                                                    getConfigurationFactory()
                                                            .createPartialConfigurationFromArgs(
                                                                    new String[] {
                                                                        f.getAbsolutePath()
                                                                    },
                                                                    new DryRunKeyStore(),
                                                                    ImmutableSet.of(
                                                                            Configuration
                                                                                    .CONFIGURATION_DESCRIPTION_TYPE_NAME),
                                                                    null);
                                            return new BaseTestSuite()
                                                    .filterByConfigMetadata(
                                                            c,
                                                            moduleMetadataIncludeFilters,
                                                            new MultiMap<String, String>());
                                        } catch (ConfigurationException e) {
                                            return false;
                                        }
                                    })
                            .collect(Collectors.toSet());
            return shouldRunFiles.stream()
                    .map(c -> FileUtil.getBaseName(c.getName()))
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            System.err.println(e);
        }
        return null;
    }

    private Set<String> searchConfigsForSuiteTag(File rootDir, String suiteTag) {
        try {
            Set<File> configFiles = FileUtil.findFilesObject(rootDir, ".*\\.config$");
            if (configFiles.isEmpty()) {
                return null;
            }
            Set<File> shouldRunFiles =
                    configFiles.stream()
                            .filter(
                                    f -> {
                                        try {
                                            // TODO: make it more robust to detect
                                            String content = FileUtil.readStringFromFile(f);
                                            return content.contains("test-suite-tag")
                                                    && content.contains(suiteTag);
                                        } catch (IOException e) {
                                            return false;
                                        }
                                    })
                            .collect(Collectors.toSet());
            return shouldRunFiles.stream()
                    .map(c -> FileUtil.getBaseName(c.getName()))
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            System.err.println(e);
        }
        return null;
    }

    @VisibleForTesting
    protected String getEnvironment(String var) {
        return System.getenv(var);
    }
}
