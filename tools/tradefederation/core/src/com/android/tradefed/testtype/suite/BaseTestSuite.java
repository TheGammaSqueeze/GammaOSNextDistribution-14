/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tradefed.testtype.suite;

import com.android.tradefed.build.BuildInfoKey.BuildInfoFileKey;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceFoldableState;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.error.HarnessRuntimeException;
import com.android.tradefed.invoker.logger.InvocationMetricLogger;
import com.android.tradefed.invoker.logger.InvocationMetricLogger.InvocationMetricKey;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.ITestFileFilterReceiver;
import com.android.tradefed.testtype.ITestFilterReceiver;
import com.android.tradefed.testtype.suite.params.FoldableExpandingHandler;
import com.android.tradefed.testtype.suite.params.IModuleParameterHandler;
import com.android.tradefed.testtype.suite.params.ModuleParameters;
import com.android.tradefed.testtype.suite.params.ModuleParametersHelper;
import com.android.tradefed.testtype.suite.params.NegativeHandler;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.FileUtil;

import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

/** A Test for running Compatibility Test Suite with new suite system. */
@OptionClass(alias = "base-suite")
public class BaseTestSuite extends ITestSuite {

    public static final String INCLUDE_FILTER_OPTION = "include-filter";
    public static final String EXCLUDE_FILTER_OPTION = "exclude-filter";
    public static final String MODULE_OPTION = "module";
    public static final char MODULE_OPTION_SHORT_NAME = 'm';
    public static final String TEST_ARG_OPTION = "test-arg";
    public static final String TEST_OPTION = "test";
    public static final char TEST_OPTION_SHORT_NAME = 't';
    public static final String CONFIG_PATTERNS_OPTION = "config-patterns";
    private static final String MODULE_ARG_OPTION = "module-arg";
    private static final String REVERSE_EXCLUDE_FILTERS = "reverse-exclude-filters";
    private static final int MAX_FILTER_DISPLAY = 20;

    @Option(
            name = INCLUDE_FILTER_OPTION,
            description =
                    "the include module filters to apply. Format: '[abi] <module-name> [test]'. See"
                        + " documentation:"
                        + "https://source.android.com/docs/core/tests/tradefed/testing/through-suite/option-passing",
            importance = Importance.ALWAYS)
    private Set<String> mIncludeFilters = new LinkedHashSet<>();

    @Option(
            name = EXCLUDE_FILTER_OPTION,
            description =
                    "the exclude module filters to apply. Format: '[abi] <module-name> [test]'. See"
                        + " documentation:"
                        + "https://source.android.com/docs/core/tests/tradefed/testing/through-suite/option-passing",
            importance = Importance.ALWAYS)
    private Set<String> mExcludeFilters = new LinkedHashSet<>();

    @Option(
            name = REVERSE_EXCLUDE_FILTERS,
            description =
                    "Flip exclude-filters into include-filters, in order to run only the excluded "
                            + "set.")
    private boolean mReverseExcludeFilters = false;

    @Option(
            name = MODULE_OPTION,
            shortName = MODULE_OPTION_SHORT_NAME,
            description = "the test module to run. Only works for configuration in the tests dir.",
            importance = Importance.IF_UNSET)
    private String mModuleName = null;

    @Option(
            name = TEST_OPTION,
            shortName = TEST_OPTION_SHORT_NAME,
            description = "the test to run.",
            importance = Importance.IF_UNSET)
    private String mTestName = null;

    @Option(
            name = MODULE_ARG_OPTION,
            description =
                    "the arguments to pass to a module. The expected format is"
                            + "\"<module-name>:[{alias}]<arg-name>:[<arg-key>:=]<arg-value>\"",
            importance = Importance.ALWAYS)
    private List<String> mModuleArgs = new ArrayList<>();

    @Option(
            name = TEST_ARG_OPTION,
            description =
                    "The arguments to pass to a test or its preparers. The expected format is"
                            + "\"<test-class>:<arg-name>:[<arg-key>:=]<arg-value>\"",
            importance = Importance.ALWAYS)
    private List<String> mTestArgs = new ArrayList<>();

    @Option(
            name = "run-suite-tag",
            description =
                    "The tag that must be run. If specified, only configurations containing the "
                            + "matching suite tag will be able to run.")
    private String mSuiteTag = null;

    @Option(
            name = "prioritize-host-config",
            description =
                    "If there are duplicate test configs for host/target, prioritize the host"
                            + " config, otherwise use the target config.")
    private boolean mPrioritizeHostConfig = false;

    @Option(
            name = "suite-config-prefix",
            description = "Search only configs with given prefix for suite tags.")
    private String mSuitePrefix = null;

    @Option(
            name = "skip-loading-config-jar",
            description =
                    "Whether or not to skip loading configurations from the JAR on the classpath.")
    private boolean mSkipJarLoading = false;

    @Option(
            name = CONFIG_PATTERNS_OPTION,
            description =
                    "The pattern(s) of the configurations that should be loaded from a directory."
                            + " If none is explicitly specified, .*.xml and .*.config will be used."
                            + " Can be repeated.")
    private List<String> mConfigPatterns = new ArrayList<>();

    @Option(
            name = "enable-parameterized-modules",
            description =
                    "Whether or not to enable parameterized modules. This is a feature flag for"
                            + " work in development.")
    private boolean mEnableParameter = false;

    @Option(
            name = "enable-mainline-parameterized-modules",
            description =
                    "Whether or not to enable mainline parameterized modules. This is a feature"
                            + " flag for work in development.")
    private boolean mEnableMainlineParameter = false;

    @Option(
            name = "enable-optional-parameterization",
            description =
                    "Whether or not to enable optional parameters. Optional parameters are "
                            + "parameters not usually used by default.")
    private boolean mEnableOptionalParameter = false;

    @Option(
            name = "module-parameter",
            description =
                    "Allows to run only one module parameter type instead of all the combinations."
                        + " For example: 'instant_app' would only run the instant_app version of "
                        + "modules")
    private ModuleParameters mForceParameter = null;

    @Option(
            name = "exclude-module-parameters",
            description =
                    "Exclude some modules parameter from being evaluated in the run"
                            + " combinations.For example: 'instant_app' would exclude all the"
                            + " instant_app version of modules.")
    private Set<ModuleParameters> mExcludedModuleParameters = new HashSet<>();

    @Option(
            name = "fail-on-everything-filtered",
            description =
                    "Whether or not to fail the invocation in case test filter returns"
                            + " an empty result.")
    private boolean mFailOnEverythingFiltered = false;

    @Option(
            name = "ignore-non-preloaded-mainline-module",
            description =
                    "Skip installing the module(s) when the module(s) that are not"
                            + "preloaded on device. Otherwise an exception will be thrown.")
    private boolean mIgnoreNonPreloadedMainlineModule = false;

    @Option(
            name = "load-configs-with-include-filters",
            description =
                    "An experimental flag to improve the performance of loading test configs with "
                            + "given module defined in include-filter.")
    private boolean mLoadConfigsWithIncludeFilters = false;

    private SuiteModuleLoader mModuleRepo;
    private Map<String, LinkedHashSet<SuiteTestFilter>> mIncludeFiltersParsed =
            new LinkedHashMap<>();
    private Map<String, LinkedHashSet<SuiteTestFilter>> mExcludeFiltersParsed =
            new LinkedHashMap<>();
    private List<File> mConfigPaths = new ArrayList<>();
    private Set<IAbi> mAbis = new LinkedHashSet<>();
    private Set<DeviceFoldableState> mFoldableStates = new LinkedHashSet<>();

    public void setSkipjarLoading(boolean skipJarLoading) {
        mSkipJarLoading = skipJarLoading;
    }

    /** {@inheritDoc} */
    @Override
    public LinkedHashMap<String, IConfiguration> loadTests() {
        try {
            File testsDir = getTestsDir();
            try {
                mFoldableStates = getFoldableStates(getDevice());
            } catch (UnsupportedOperationException e) {
                // Foldable state isn't always supported
                CLog.e(e);
            }
            setupFilters(testsDir);
            mAbis = getAbis(getDevice());

            if (mReverseExcludeFilters) {
                if (mExcludeFilters.isEmpty()) {
                    return new LinkedHashMap<String, IConfiguration>();
                }
                mIncludeFilters.clear();
                mIncludeFilters.addAll(mExcludeFilters);
                mExcludeFilters.clear();
            }

            // Create and populate the filters here
            SuiteModuleLoader.addFilters(
                    mIncludeFilters, mIncludeFiltersParsed, mAbis, mFoldableStates);
            SuiteModuleLoader.addFilters(
                    mExcludeFilters, mExcludeFiltersParsed, mAbis, mFoldableStates);

            String includeFilters = "";
            if (mIncludeFiltersParsed.size() > MAX_FILTER_DISPLAY) {
                if (isSplitting()) {
                    includeFilters = "Includes: <too long to display>";
                } else {
                    File suiteIncludeFilters = null;
                    try {
                        suiteIncludeFilters =
                                FileUtil.createTempFile("suite-include-filters", ".txt");
                        FileUtil.writeToFile(mIncludeFiltersParsed.toString(), suiteIncludeFilters);
                        logFilterFile(
                                suiteIncludeFilters,
                                suiteIncludeFilters.getName(),
                                LogDataType.TEXT);
                        includeFilters =
                                String.format("Includes: See %s", suiteIncludeFilters.getName());
                    } catch (IOException e) {
                        CLog.e(e);
                    } finally {
                        FileUtil.deleteFile(suiteIncludeFilters);
                    }
                }
            } else if (mIncludeFiltersParsed.size() > 0) {
                includeFilters = String.format("Includes: %s", mIncludeFiltersParsed.toString());
            }

            String excludeFilters = "";
            if (mExcludeFiltersParsed.size() > MAX_FILTER_DISPLAY) {
                if (isSplitting()) {
                    excludeFilters = "Excludes: <too long to display>";
                } else {
                    File suiteExcludeFilters = null;
                    try {
                        suiteExcludeFilters =
                                FileUtil.createTempFile("suite-exclude-filters", ".txt");
                        FileUtil.writeToFile(mExcludeFiltersParsed.toString(), suiteExcludeFilters);
                        logFilterFile(
                                suiteExcludeFilters,
                                suiteExcludeFilters.getName(),
                                LogDataType.TEXT);
                        excludeFilters =
                                String.format("Excludes: See %s", suiteExcludeFilters.getName());
                    } catch (IOException e) {
                        CLog.e(e);
                    } finally {
                        FileUtil.deleteFile(suiteExcludeFilters);
                    }
                }
            } else if (mExcludeFiltersParsed.size() > 0) {
                excludeFilters = String.format("Excludes: %s", mExcludeFiltersParsed.toString());
            }

            CLog.d(
                    "Initializing ModuleRepo\nABIs:%s\n" + "Test Args:%s\nModule Args:%s\n%s\n%s",
                    mAbis, mTestArgs, mModuleArgs, includeFilters, excludeFilters);
            if (!mFoldableStates.isEmpty()) {
                CLog.d("Foldable states: %s", mFoldableStates);
            }

            mModuleRepo =
                    createModuleLoader(
                            mIncludeFiltersParsed, mExcludeFiltersParsed, mTestArgs, mModuleArgs);
            if (mForceParameter != null && !mEnableParameter) {
                throw new IllegalArgumentException(
                        "'module-parameter' option was specified without "
                                + "'enable-parameterized-modules'");
            }
            if (mEnableOptionalParameter && !mEnableParameter) {
                throw new IllegalArgumentException(
                        "'enable-optional-parameterization' option was specified without "
                                + "'enable-parameterized-modules'");
            }

            if (mEnableMainlineParameter) {
                mModuleRepo.setMainlineParameterizedModules(mEnableMainlineParameter);
                mModuleRepo.setInvocationContext(getInvocationContext());
                mModuleRepo.setOptimizeMainlineTest(
                        getConfiguration().getCommandOptions().getOptimizeMainlineTest());
                mModuleRepo.setIgnoreNonPreloadedMainlineModule(mIgnoreNonPreloadedMainlineModule);
            }

            mModuleRepo.setParameterizedModules(mEnableParameter);
            mModuleRepo.setOptionalParameterizedModules(mEnableOptionalParameter);
            mModuleRepo.setModuleParameter(mForceParameter);
            mModuleRepo.setExcludedModuleParameters(mExcludedModuleParameters);
            mModuleRepo.setFoldableStates(mFoldableStates);
            mModuleRepo.setLoadConfigsWithIncludeFilters(mLoadConfigsWithIncludeFilters);

            List<File> testsDirectories = new ArrayList<>();

            // Include host or target first in the search if it exists, we have to this in
            // BaseTestSuite because it's the only one with the BuildInfo knowledge of linked files
            if (mPrioritizeHostConfig) {
                File hostSubDir = getBuildInfo().getFile(BuildInfoFileKey.HOST_LINKED_DIR);
                if (hostSubDir != null && hostSubDir.exists()) {
                    testsDirectories.add(hostSubDir);
                }
            } else {
                File targetSubDir = getBuildInfo().getFile(BuildInfoFileKey.TARGET_LINKED_DIR);
                if (targetSubDir != null && targetSubDir.exists()) {
                    testsDirectories.add(targetSubDir);
                }
            }

            // Finally add the full test cases directory in case there is no special sub-dir.
            testsDirectories.add(testsDir);
            // Actual loading of the configurations.
            long start = System.currentTimeMillis();
            LinkedHashMap<String, IConfiguration> loadedTests =
                    loadingStrategy(mAbis, testsDirectories, mSuitePrefix, mSuiteTag);
            long duration = System.currentTimeMillis() - start;
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.LOAD_TEST_CONFIGS_TIME, duration);
            if (mFailOnEverythingFiltered
                    && loadedTests.isEmpty()
                    && !mIncludeFiltersParsed.isEmpty()) {
                // remove modules with empty filters from the message
                Map<String, LinkedHashSet<SuiteTestFilter>> includeFiltersCleaned =
                        mIncludeFiltersParsed.entrySet().stream()
                                .filter(
                                        entry ->
                                                entry.getValue() != null
                                                        && !entry.getValue().isEmpty())
                                .collect(
                                        Collectors.toMap(
                                                Map.Entry::getKey,
                                                Map.Entry::getValue,
                                                (x, y) -> y,
                                                LinkedHashMap::new));
                String errorMessage =
                        String.format(
                                "Include filter '%s' was specified but resulted in an empty test"
                                        + " set.",
                                includeFiltersCleaned.toString());
                if (errorMessage.length() > 1000) {
                    CLog.e(errorMessage);
                    errorMessage =
                            String.format(
                                    "Include filter was specified for %d modules but resulted in an"
                                            + " empty test set. Check host log for complete list of"
                                            + " include filters.",
                                    includeFiltersCleaned.size());
                }
                throw new HarnessRuntimeException(
                        errorMessage, InfraErrorIdentifier.OPTION_CONFIGURATION_ERROR);
            }
            return loadedTests;
        } catch (DeviceNotAvailableException e) {
            throw new HarnessRuntimeException(e.getMessage(), e);
        } catch (FileNotFoundException fnfe) {
            throw new HarnessRuntimeException(
                    fnfe.getMessage(), fnfe, InfraErrorIdentifier.ARTIFACT_NOT_FOUND);
        }
    }

    /**
     * Default loading strategy will load from the resources and the tests directory. Can be
     * extended or replaced.
     *
     * @param abis The set of abis to run against.
     * @param testsDirs The tests directory.
     * @param suitePrefix A prefix to filter the resource directory.
     * @param suiteTag The suite tag a module should have to be included. Can be null.
     * @return A list of loaded configuration for the suite.
     */
    public LinkedHashMap<String, IConfiguration> loadingStrategy(
            Set<IAbi> abis, List<File> testsDirs, String suitePrefix, String suiteTag) {
        LinkedHashMap<String, IConfiguration> loadedConfigs = new LinkedHashMap<>();
        // Load and return directly the specific config files.
        if (!mConfigPaths.isEmpty()) {
            CLog.d(
                    "Loading the specified configs path '%s' and skip loading from the resources.",
                    mConfigPaths);
            return getModuleLoader().loadConfigsFromSpecifiedPaths(mConfigPaths, abis, suiteTag);
        }

        // Load configs that are part of the resources
        if (!mSkipJarLoading) {
            loadedConfigs.putAll(
                    getModuleLoader().loadConfigsFromJars(abis, suitePrefix, suiteTag));
        }

        // Load the configs that are part of the tests dir
        if (mConfigPatterns.isEmpty()) {
            // If no special pattern was configured, use the default configuration patterns we know
            mConfigPatterns.add(".*\\.config$");
            mConfigPatterns.add(".*\\.xml$");
        }

        loadedConfigs.putAll(
                getModuleLoader()
                        .loadConfigsFromDirectory(
                                testsDirs, abis, suitePrefix, suiteTag, mConfigPatterns));
        return loadedConfigs;
    }

    /** {@inheritDoc} */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        super.setBuild(buildInfo);
    }

    /** Sets include-filters for the compatibility test */
    public void setIncludeFilter(Set<String> includeFilters) {
        mIncludeFilters.addAll(includeFilters);
    }

    /** Gets a copy of include-filters for the compatibility test */
    public Set<String> getIncludeFilter() {
        return new LinkedHashSet<String>(mIncludeFilters);
    }

    public void clearIncludeFilter() {
        mIncludeFilters.clear();
    }

    /** Sets exclude-filters for the compatibility test */
    public void setExcludeFilter(Set<String> excludeFilters) {
        mExcludeFilters.addAll(excludeFilters);
    }

    public void clearExcludeFilter() {
        mExcludeFilters.clear();
    }

    /** Gets a copy of exclude-filters for the compatibility test */
    public Set<String> getExcludeFilter() {
        return new HashSet<String>(mExcludeFilters);
    }

    /** Returns the current {@link SuiteModuleLoader}. */
    public SuiteModuleLoader getModuleLoader() {
        return mModuleRepo;
    }

    public void reevaluateFilters() {
        SuiteModuleLoader.addFilters(
                mIncludeFilters, mIncludeFiltersParsed, mAbis, mFoldableStates);
        SuiteModuleLoader.addFilters(
                mExcludeFilters, mExcludeFiltersParsed, mAbis, mFoldableStates);
        if (getDirectModule() != null) {
            // Remove all entries for unrelated modules
            mExcludeFiltersParsed.keySet().removeIf(key -> !key.equals(getDirectModule().getId()));
            // Also clean exclude filters left over
            for (String filterString : new HashSet<>(mExcludeFilters)) {
                SuiteTestFilter parentFilter = SuiteTestFilter.createFrom(filterString);
                if (!parentFilter.getModuleId().equals(getDirectModule().getId())) {
                    mExcludeFilters.remove(filterString);
                }
            }
        }
    }

    /** Adds module args */
    public void addModuleArgs(Set<String> moduleArgs) {
        mModuleArgs.addAll(moduleArgs);
    }

    /** Clear the stored module args out */
    void clearModuleArgs() {
        mModuleArgs.clear();
    }

    /** Add config patterns */
    public void addConfigPatterns(List<String> patterns) {
        mConfigPatterns.addAll(patterns);
    }

    /** Set whether or not parameterized modules are enabled or not. */
    public void setEnableParameterizedModules(boolean enableParameter) {
        mEnableParameter = enableParameter;
    }

    /** Set whether or not optional parameterized modules are enabled or not. */
    public void setEnableOptionalParameterizedModules(boolean enableOptionalParameter) {
        mEnableOptionalParameter = enableOptionalParameter;
    }

    public void setModuleParameter(ModuleParameters forceParameter) {
        mForceParameter = forceParameter;
    }

    /**
     * Create the {@link SuiteModuleLoader} responsible to load the {@link IConfiguration} and
     * assign them some of the options.
     *
     * @param includeFiltersFormatted The formatted and parsed include filters.
     * @param excludeFiltersFormatted The formatted and parsed exclude filters.
     * @param testArgs the list of test ({@link IRemoteTest}) arguments.
     * @param moduleArgs the list of module arguments.
     * @return the created {@link SuiteModuleLoader}.
     */
    public SuiteModuleLoader createModuleLoader(
            Map<String, LinkedHashSet<SuiteTestFilter>> includeFiltersFormatted,
            Map<String, LinkedHashSet<SuiteTestFilter>> excludeFiltersFormatted,
            List<String> testArgs,
            List<String> moduleArgs) {
        return new SuiteModuleLoader(
                includeFiltersFormatted, excludeFiltersFormatted, testArgs, moduleArgs);
    }

    /**
     * Sets the include/exclude filters up based on if a module name was given.
     *
     * @throws FileNotFoundException if any file is not found.
     */
    protected void setupFilters(File testsDir) throws FileNotFoundException {
        if (mModuleName == null) {
            if (mTestName != null) {
                throw new IllegalArgumentException(
                        "Test name given without module name. Add --module <module-name>");
            }
            return;
        }
        // If this option (-m / --module) is set only the matching unique module should run.
        Set<File> modules =
                SuiteModuleLoader.getModuleNamesMatching(
                        testsDir, mSuitePrefix, String.format(".*%s.*.config", mModuleName));
        // If multiple modules match, do exact match.
        if (modules.size() > 1) {
            Set<File> newModules = new HashSet<>();
            String exactModuleName = String.format("%s.config", mModuleName);
            for (File module : modules) {
                if (module.getName().equals(exactModuleName)) {
                    newModules.add(module);
                    modules = newModules;
                    break;
                }
            }
        }
        if (modules.size() == 0) {
            throw new HarnessRuntimeException(
                    String.format("No modules found matching %s", mModuleName),
                    InfraErrorIdentifier.OPTION_CONFIGURATION_ERROR);
        } else if (modules.size() > 1) {
            throw new HarnessRuntimeException(
                    String.format(
                            "Multiple modules found matching %s:\n%s\nWhich one did you "
                                    + "mean?\n",
                            mModuleName, ArrayUtil.join("\n", modules)),
                    InfraErrorIdentifier.OPTION_CONFIGURATION_ERROR);
        } else {
            File mod = modules.iterator().next();
            String moduleName = mod.getName().replace(".config", "");
            checkFilters(mIncludeFilters, moduleName);
            checkFilters(mExcludeFilters, moduleName);
            mIncludeFilters.add(
                    new SuiteTestFilter(getRequestedAbi(), moduleName, mTestName).toString());
            // Create the matching filters for the parameterized version of it if needed.
            if (mEnableParameter) {
                for (ModuleParameters param : ModuleParameters.values()) {
                    Map<ModuleParameters, IModuleParameterHandler> moduleParamExpanded =
                            ModuleParametersHelper.resolveParam(param, mEnableOptionalParameter);
                    if (moduleParamExpanded == null) {
                        continue;
                    }
                    for (Entry<ModuleParameters, IModuleParameterHandler> moduleParam :
                            moduleParamExpanded.entrySet()) {
                        if (moduleParam.getValue() instanceof NegativeHandler) {
                            continue;
                        }
                        if (moduleParam.getValue() instanceof FoldableExpandingHandler) {
                            List<IModuleParameterHandler> foldableHandlers =
                                    ((FoldableExpandingHandler) moduleParam.getValue())
                                            .expandHandler(mFoldableStates);
                            for (IModuleParameterHandler foldableHandler : foldableHandlers) {
                                String paramModuleName =
                                        String.format(
                                                "%s[%s]",
                                                moduleName,
                                                foldableHandler.getParameterIdentifier());
                                mIncludeFilters.add(
                                        new SuiteTestFilter(
                                                        getRequestedAbi(),
                                                        paramModuleName,
                                                        mTestName)
                                                .toString());
                            }
                            continue;
                        }
                        String paramModuleName =
                                String.format(
                                        "%s[%s]",
                                        moduleName,
                                        moduleParam.getValue().getParameterIdentifier());
                        mIncludeFilters.add(
                                new SuiteTestFilter(getRequestedAbi(), paramModuleName, mTestName)
                                        .toString());
                    }
                }
            }
        }
    }

    @Override
    public void cleanUpSuiteSetup() {
        super.cleanUpSuiteSetup();
        // Clean the filters because at that point they have been applied to the runners.
        // This can save several GB of memories during sharding.
        mIncludeFilters.clear();
        mExcludeFilters.clear();
        mIncludeFiltersParsed.clear();
        mExcludeFiltersParsed.clear();
    }

    /**
     * Add the config path for {@link SuiteModuleLoader} to limit the search loading configurations.
     *
     * @param configPath A {@code File} with the absolute path of the configuration.
     */
    void addConfigPaths(File configPath) {
        mConfigPaths.add(configPath);
    }

    /** Clear the stored config paths out. */
    void clearConfigPaths() {
        mConfigPaths.clear();
    }

    /* Helper method designed to remove filters in a list not applicable to the given module */
    private static void checkFilters(Set<String> filters, String moduleName) {
        Set<String> cleanedFilters = new HashSet<String>();
        for (String filter : filters) {
            SuiteTestFilter filterObject = SuiteTestFilter.createFrom(filter);
            String filterName = filterObject.getName();
            String filterBaseName = filterObject.getBaseName();
            if (moduleName.equals(filterName) || moduleName.equals(filterBaseName)) {
                cleanedFilters.add(filter); // Module name matches, filter passes
            }
        }
        filters.clear();
        filters.addAll(cleanedFilters);
    }

    /* Return a {@link boolean} for the setting of prioritize-host-config.*/
    boolean getPrioritizeHostConfig() {
        return mPrioritizeHostConfig;
    }

    /**
     * Set option prioritize-host-config.
     *
     * @param prioritizeHostConfig true to prioritize host config, i.e., run host test if possible.
     */
    @VisibleForTesting
    protected void setPrioritizeHostConfig(boolean prioritizeHostConfig) {
        mPrioritizeHostConfig = prioritizeHostConfig;
    }

    /** Log a file directly to the result reporter. */
    private void logFilterFile(File filterFile, String dataName, LogDataType type) {
        if (getCurrentTestLogger() == null) {
            return;
        }
        try (FileInputStreamSource source = new FileInputStreamSource(filterFile)) {
            getCurrentTestLogger().testLog(dataName, type, source);
        }
    }

    @Override
    protected boolean shouldModuleRun(ModuleDefinition module) {
        String moduleId = module.getId();
        LinkedHashSet<SuiteTestFilter> excludeFilters = mExcludeFiltersParsed.get(moduleId);
        CLog.d("Filters for '%s': %s", moduleId, excludeFilters);
        if (excludeFilters == null || excludeFilters.isEmpty()) {
            return true;
        }
        for (SuiteTestFilter filter : excludeFilters) {
            if (filter.getTest() == null) {
                CLog.d("Skipping %s, it previously passed.", moduleId);
                return false;
            }
            for (IRemoteTest test : module.getTests()) {
                if (test instanceof ITestFileFilterReceiver) {
                    File excludeFilterFile = ((ITestFileFilterReceiver) test).getExcludeTestFile();
                    if (excludeFilterFile == null) {
                        try {
                            excludeFilterFile = FileUtil.createTempFile("exclude-filter", ".txt");
                        } catch (IOException e) {
                            throw new HarnessRuntimeException(
                                    e.getMessage(), e, InfraErrorIdentifier.FAIL_TO_CREATE_FILE);
                        }
                        ((ITestFileFilterReceiver) test).setExcludeTestFile(excludeFilterFile);
                    }
                    try {
                        FileUtil.writeToFile(filter.getTest() + "\n", excludeFilterFile, true);
                    } catch (IOException e) {
                        CLog.e(e);
                        continue;
                    }
                } else if (test instanceof ITestFilterReceiver) {
                    ((ITestFilterReceiver) test).addExcludeFilter(filter.getTest());
                }
            }
        }
        return true;
    }

    protected Set<DeviceFoldableState> getFoldableStates(ITestDevice device)
            throws DeviceNotAvailableException {
        if (device == null || device.getIDevice() instanceof StubDevice) {
            return mFoldableStates;
        }
        if (!mFoldableStates.isEmpty()) {
            return mFoldableStates;
        }
        mFoldableStates = device.getFoldableStates();
        return mFoldableStates;
    }

    public String getRunSuiteTag() {
        return mSuiteTag;
    }

    public boolean reverseExcludeFilters() {
        return mReverseExcludeFilters;
    }
}
