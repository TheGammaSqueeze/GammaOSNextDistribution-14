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

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ArgsOptionParser;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.invoker.logger.InvocationMetricLogger;
import com.android.tradefed.invoker.logger.InvocationMetricLogger.InvocationMetricKey;
import com.android.tradefed.invoker.tracing.CloseableTraceScope;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.QuotationAwareTokenizer;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StringEscapeUtils;
import com.android.tradefed.util.SystemUtil;
import com.android.tradefed.util.testmapping.TestMapping;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A class for test launcher to call the TradeFed jar that packaged in the test suite to discover
 * test modules.
 *
 * <p>TestDiscoveryInvoker will take {@link IConfiguration} and the test root directory from the
 * launch control provider to make the launch control provider to invoke the workflow to use the
 * config to query the packaged TradeFed jar file in the test suite root directory to retrieve test
 * module names.
 */
public class TestDiscoveryInvoker {

    private final IConfiguration mConfiguration;
    private final String mDefaultConfigName;
    private final File mRootDir;
    private final IRunUtil mRunUtil = new RunUtil();
    private final boolean mHasConfigFallback;
    private final boolean mUseCurrentTradefed;
    private File mTestDir;
    private File mTestMappingZip;
    private IBuildInfo mBuildInfo;
    private ITestLogger mLogger;

    public static final String TRADEFED_OBSERVATORY_ENTRY_PATH =
            TestDiscoveryExecutor.class.getName();
    public static final String TEST_DEPENDENCIES_LIST_KEY = "TestDependencies";
    public static final String TEST_MODULES_LIST_KEY = "TestModules";
    public static final String PARTIAL_FALLBACK_KEY = "PartialFallback";
    public static final String NO_POSSIBLE_TEST_DISCOVERY_KEY = "NoPossibleTestDiscovery";
    public static final String TEST_MAPPING_ZIP_FILE = "TF_TEST_MAPPING_ZIP_FILE";
    public static final String ROOT_DIRECTORY_ENV_VARIABLE_KEY =
            "ROOT_TEST_DISCOVERY_USE_TEST_DIRECTORY";

    public static final String OUTPUT_FILE = "DISCOVERY_OUTPUT_FILE";
    public static final String DISCOVERY_TRACE_FILE = "DISCOVERY_TRACE_FILE";

    private static final long DISCOVERY_TIMEOUT_MS = 180000L;

    @VisibleForTesting
    IRunUtil getRunUtil() {
        return mRunUtil;
    }

    @VisibleForTesting
    String getJava() {
        return SystemUtil.getRunningJavaBinaryPath().getAbsolutePath();
    }

    @VisibleForTesting
    File createOutputFile() throws IOException {
        return FileUtil.createTempFile("discovery-output", ".txt");
    }

    @VisibleForTesting
    File createTraceFile() throws IOException {
        return FileUtil.createTempFile("discovery-trace", ".txt");
    }

    public File getTestDir() {
        return mTestDir;
    }

    public void setTestDir(File testDir) {
        mTestDir = testDir;
    }

    public void setTestMappingZip(File testMappingZip) {
        mTestMappingZip = testMappingZip;
    }

    public void setBuildInfo(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    public void setTestLogger(ITestLogger logger) {
        mLogger = logger;
    }

    /** Creates an {@link TestDiscoveryInvoker} with a {@link IConfiguration} and root directory. */
    public TestDiscoveryInvoker(IConfiguration config, File rootDir) {
        this(config, null, rootDir);
    }

    /**
     * Creates an {@link TestDiscoveryInvoker} with a {@link IConfiguration}, test launcher's
     * default config name and root directory.
     */
    public TestDiscoveryInvoker(IConfiguration config, String defaultConfigName, File rootDir) {
        this(config, defaultConfigName, rootDir, false, false);
    }

    /**
     * Creates an {@link TestDiscoveryInvoker} with a {@link IConfiguration}, test launcher's
     * default config name, root directory and if fallback is required.
     */
    public TestDiscoveryInvoker(
            IConfiguration config,
            String defaultConfigName,
            File rootDir,
            boolean hasConfigFallback,
            boolean useCurrentTradefed) {
        mConfiguration = config;
        mDefaultConfigName = defaultConfigName;
        mRootDir = rootDir;
        mTestDir = null;
        mHasConfigFallback = hasConfigFallback;
        mUseCurrentTradefed = useCurrentTradefed;
    }

    /**
     * Retrieve a map of xTS test dependency names - categorized by either test modules or other
     * test dependencies.
     *
     * @return A map of test dependencies which grouped by TEST_MODULES_LIST_KEY and
     *     TEST_DEPENDENCIES_LIST_KEY.
     * @throws IOException
     * @throws JSONException
     * @throws ConfigurationException
     * @throws TestDiscoveryException
     */
    public Map<String, List<String>> discoverTestDependencies()
            throws IOException, JSONException, ConfigurationException, TestDiscoveryException {
        File outputFile = createOutputFile();
        File traceFile = createTraceFile();
        try (CloseableTraceScope ignored = new CloseableTraceScope("discoverTestDependencies")) {
            Map<String, List<String>> dependencies = new HashMap<>();
            // Build the classpath base on test root directory which should contain all the jars
            String classPath = buildXtsClasspath(mRootDir);
            // Build command line args to query the tradefed.jar in the root directory
            List<String> args = buildJavaCmdForXtsDiscovery(classPath);
            String[] subprocessArgs = args.toArray(new String[args.size()]);

            if (mHasConfigFallback) {
                getRunUtil()
                        .setEnvVariable(
                                ROOT_DIRECTORY_ENV_VARIABLE_KEY, mRootDir.getAbsolutePath());
            }
            getRunUtil().setEnvVariable(OUTPUT_FILE, outputFile.getAbsolutePath());
            getRunUtil().setEnvVariable(DISCOVERY_TRACE_FILE, traceFile.getAbsolutePath());
            CommandResult res = getRunUtil().runTimedCmd(DISCOVERY_TIMEOUT_MS, subprocessArgs);
            String stdout = res.getStdout();
            CLog.i(String.format("Tradefed Observatory returned in stdout: %s", stdout));
            if (res.getExitCode() != 0 || !res.getStatus().equals(CommandStatus.SUCCESS)) {
                DiscoveryExitCode exitCode = null;
                if (res.getExitCode() != null) {
                    for (DiscoveryExitCode code : DiscoveryExitCode.values()) {
                        if (code.exitCode() == res.getExitCode()) {
                            exitCode = code;
                        }
                    }
                }
                if (DiscoveryExitCode.CONFIGURATION_EXCEPTION.equals(exitCode)) {
                    throw new ConfigurationException(
                            res.getStderr(), InfraErrorIdentifier.OPTION_CONFIGURATION_ERROR);
                }
                throw new TestDiscoveryException(
                        String.format(
                                "Tradefed observatory error, unable to discover test module names."
                                        + " command used: %s error: %s",
                                Joiner.on(" ").join(subprocessArgs), res.getStderr()),
                        null,
                        exitCode);
            }
            try (CloseableTraceScope discoResults =
                    new CloseableTraceScope("parse_discovery_results")) {

                String result = FileUtil.readStringFromFile(outputFile);
                CLog.i("output file content: %s", result);

                // For backward compatibility
                try {
                    new JSONObject(result);
                } catch (JSONException e) {
                    CLog.w("Output file was incorrect. Try falling back stdout");
                    result = stdout;
                }

                boolean noDiscovery = hasNoPossibleDiscovery(result);
                if (noDiscovery) {
                    dependencies.put(NO_POSSIBLE_TEST_DISCOVERY_KEY, Arrays.asList("true"));
                }
                List<String> testModules = parseTestDiscoveryOutput(result, TEST_MODULES_LIST_KEY);
                if (!noDiscovery) {
                    InvocationMetricLogger.addInvocationMetrics(
                            InvocationMetricKey.TEST_DISCOVERY_MODULE_COUNT, testModules.size());
                }
                if (!testModules.isEmpty()) {
                    dependencies.put(TEST_MODULES_LIST_KEY, testModules);
                } else {
                    // Only report no finding if discovery actually took effect
                    if (!noDiscovery) {
                        mConfiguration.getSkipManager().reportDiscoveryWithNoTests();
                    }
                }

                List<String> testDependencies =
                        parseTestDiscoveryOutput(result, TEST_DEPENDENCIES_LIST_KEY);
                if (!testDependencies.isEmpty()) {
                    dependencies.put(TEST_DEPENDENCIES_LIST_KEY, testDependencies);
                }

                String partialFallback = parsePartialFallback(result);
                if (partialFallback != null) {
                    dependencies.put(PARTIAL_FALLBACK_KEY, Arrays.asList(partialFallback));
                }
                if (!noDiscovery) {
                    mConfiguration
                            .getSkipManager()
                            .reportDiscoveryDependencies(testModules, testDependencies);
                }
                return dependencies;
            }
        } finally {
            FileUtil.deleteFile(outputFile);
            try (FileInputStreamSource source = new FileInputStreamSource(traceFile, true)) {
                if (mLogger != null) {
                    mLogger.testLog("discovery-trace", LogDataType.PERFETTO, source);
                }
            }
        }
    }

    /**
     * Retrieve a map of test mapping test module names.
     *
     * @return A map of test module names which grouped by TEST_MODULES_LIST_KEY.
     * @throws IOException
     * @throws JSONException
     * @throws ConfigurationException
     * @throws TestDiscoveryException
     */
    public Map<String, List<String>> discoverTestMappingDependencies()
            throws IOException, JSONException, ConfigurationException, TestDiscoveryException {
        File outputFile = createOutputFile();
        File traceFile = createTraceFile();
        try (CloseableTraceScope ignored =
                new CloseableTraceScope("discoverTestMappingDependencies")) {
            List<String> fullCommandLineArgs =
                    new ArrayList<String>(
                            Arrays.asList(
                                    QuotationAwareTokenizer.tokenizeLine(
                                            mConfiguration.getCommandLine())));
            // first arg is config name
            fullCommandLineArgs.remove(0);
            final ConfigurationTestMappingParserSettings mappingParserSettings =
                    new ConfigurationTestMappingParserSettings();
            ArgsOptionParser mappingOptionParser = new ArgsOptionParser(mappingParserSettings);
            // Parse to collect all values of --cts-params as well config name
            mappingOptionParser.parseBestEffort(fullCommandLineArgs, true);

            Map<String, List<String>> dependencies = new HashMap<>();
            // Build the classpath base on the working directory
            String classPath = buildTestMappingClasspath(mRootDir);
            // Build command line args to query the tradefed.jar in the working directory
            List<String> args = buildJavaCmdForTestMappingDiscovery(classPath);
            String[] subprocessArgs = args.toArray(new String[args.size()]);

            // Pass the test mapping zip path to subprocess by environment variable
            if (mTestMappingZip != null) {
                getRunUtil()
                        .setEnvVariable(TEST_MAPPING_ZIP_FILE, mTestMappingZip.getAbsolutePath());
            }
            if (mBuildInfo != null) {
                if (mBuildInfo.getFile(TestMapping.TEST_MAPPINGS_ZIP) != null) {
                    getRunUtil()
                            .setEnvVariable(
                                    TEST_MAPPING_ZIP_FILE,
                                    mBuildInfo
                                            .getFile(TestMapping.TEST_MAPPINGS_ZIP)
                                            .getAbsolutePath());
                    getRunUtil()
                            .setEnvVariable(
                                    TestMapping.TEST_MAPPINGS_ZIP,
                                    mBuildInfo
                                            .getFile(TestMapping.TEST_MAPPINGS_ZIP)
                                            .getAbsolutePath());
                }
                for (String allowedList : mappingParserSettings.mAllowedTestLists) {
                    if (mBuildInfo.getFile(allowedList) != null) {
                        getRunUtil()
                                .setEnvVariable(
                                        allowedList,
                                        mBuildInfo.getFile(allowedList).getAbsolutePath());
                    }
                }
            }

            if (mHasConfigFallback) {
                getRunUtil()
                        .setEnvVariable(
                                ROOT_DIRECTORY_ENV_VARIABLE_KEY, mRootDir.getAbsolutePath());
            }
            getRunUtil().setEnvVariable(DISCOVERY_TRACE_FILE, traceFile.getAbsolutePath());
            getRunUtil().setEnvVariable(OUTPUT_FILE, outputFile.getAbsolutePath());
            CommandResult res = getRunUtil().runTimedCmd(DISCOVERY_TIMEOUT_MS, subprocessArgs);
            String stdout = res.getStdout();
            CLog.i(String.format("Tradefed Observatory returned in stdout:\n %s", stdout));
            if (res.getExitCode() != 0 || !res.getStatus().equals(CommandStatus.SUCCESS)) {
                throw new TestDiscoveryException(
                        String.format(
                                "Tradefed observatory error, unable to discover test module names."
                                        + " command used: %s error: %s",
                                Joiner.on(" ").join(subprocessArgs), res.getStderr()),
                        null);
            }
            try (CloseableTraceScope discoResults =
                    new CloseableTraceScope("parse_discovery_results")) {
                String result = FileUtil.readStringFromFile(outputFile);

                boolean noDiscovery = hasNoPossibleDiscovery(result);
                if (noDiscovery) {
                    dependencies.put(NO_POSSIBLE_TEST_DISCOVERY_KEY, Arrays.asList("true"));
                }
                List<String> testModules = parseTestDiscoveryOutput(result, TEST_MODULES_LIST_KEY);
                if (!noDiscovery) {
                    InvocationMetricLogger.addInvocationMetrics(
                            InvocationMetricKey.TEST_DISCOVERY_MODULE_COUNT, testModules.size());
                }
                if (!testModules.isEmpty()) {
                    dependencies.put(TEST_MODULES_LIST_KEY, testModules);
                } else {
                    if (!noDiscovery) {
                        mConfiguration.getSkipManager().reportDiscoveryWithNoTests();
                    }
                }
                String partialFallback = parsePartialFallback(result);
                if (partialFallback != null) {
                    dependencies.put(PARTIAL_FALLBACK_KEY, Arrays.asList(partialFallback));
                }
                if (!noDiscovery) {
                    if (!noDiscovery) {
                        mConfiguration
                                .getSkipManager()
                                .reportDiscoveryDependencies(testModules, new ArrayList<String>());
                    }
                }
                return dependencies;
            }
        } finally {
            FileUtil.deleteFile(outputFile);
            try (FileInputStreamSource source = new FileInputStreamSource(traceFile, true)) {
                if (mLogger != null) {
                    mLogger.testLog("discovery-trace", LogDataType.PERFETTO, source);
                }
            }
        }
    }

    /**
     * Build java cmd for invoking a subprocess to discover test mapping test module names.
     *
     * @return A list of java command args.
     */
    private List<String> buildJavaCmdForTestMappingDiscovery(String classpath) {
        List<String> fullCommandLineArgs =
                new ArrayList<String>(
                        Arrays.asList(
                                QuotationAwareTokenizer.tokenizeLine(
                                        mConfiguration.getCommandLine())));

        List<String> args = new ArrayList<>();

        args.add(getJava());

        args.add("-cp");
        args.add(classpath);

        args.add(TRADEFED_OBSERVATORY_ENTRY_PATH);

        // Delete invocation data from args which test discovery don't need
        int i = 0;
        while (i < fullCommandLineArgs.size()) {
            if (fullCommandLineArgs.get(i).equals("--invocation-data")) {
                i = i + 2;
            } else {
                args.add(fullCommandLineArgs.get(i));
                i = i + 1;
            }
        }
        return args;
    }

    /**
     * Build java cmd for invoking a subprocess to discover XTS test module names.
     *
     * @return A list of java command args.
     * @throws ConfigurationException
     */
    private List<String> buildJavaCmdForXtsDiscovery(String classpath)
            throws ConfigurationException, TestDiscoveryException {
        List<String> fullCommandLineArgs =
                new ArrayList<String>(
                        Arrays.asList(
                                QuotationAwareTokenizer.tokenizeLine(
                                        mConfiguration.getCommandLine())));
        // first arg is config name
        fullCommandLineArgs.remove(0);

        final ConfigurationCtsParserSettings ctsParserSettings =
                new ConfigurationCtsParserSettings();
        ArgsOptionParser ctsOptionParser = new ArgsOptionParser(ctsParserSettings);

        // Parse to collect all values of --cts-params as well config name
        ctsOptionParser.parseBestEffort(fullCommandLineArgs, true);

        List<String> ctsParams = ctsParserSettings.mCtsParams;
        String configName = ctsParserSettings.mConfigName;

        if (configName == null) {
            if (mDefaultConfigName == null) {
                throw new TestDiscoveryException(
                        String.format(
                                "Failed to extract config-name from parent test command options,"
                                        + " unable to build args to invoke tradefed observatory."
                                        + " Parent test command options is: %s",
                                fullCommandLineArgs),
                        null,
                        DiscoveryExitCode.ERROR);
            } else {
                CLog.i(
                        String.format(
                                "No config name provided in the command args, use default config"
                                        + " name %s",
                                mDefaultConfigName));
                configName = mDefaultConfigName;
            }
        }
        List<String> args = new ArrayList<>();
        args.add(getJava());

        args.add("-cp");
        args.add(classpath);

        // Cts V2 requires CTS_ROOT to be set or VTS_ROOT for vts run
        args.add(
                String.format(
                        "-D%s=%s", ctsParserSettings.mRootdirVar, mRootDir.getAbsolutePath()));

        args.add(TRADEFED_OBSERVATORY_ENTRY_PATH);
        args.add(configName);

        // Tokenize args to be passed to CtsTest/XtsTest
        args.addAll(StringEscapeUtils.paramsToArgs(ctsParams));

        return args;
    }

    /**
     * Build the classpath string based on jars in the sandbox's working directory.
     *
     * @return A string of classpaths.
     * @throws IOException
     */
    private String buildTestMappingClasspath(File workingDir) throws IOException {
        try (CloseableTraceScope ignored = new CloseableTraceScope("build_classpath")) {
            List<String> classpathList = new ArrayList<>();

            if (!workingDir.exists()) {
                throw new FileNotFoundException("Couldn't find the build directory");
            }

            if (workingDir.listFiles().length == 0) {
                throw new FileNotFoundException(
                        String.format(
                                "Could not find any files under %s", workingDir.getAbsolutePath()));
            }
            for (File toolsFile : workingDir.listFiles()) {
                if (toolsFile.getName().endsWith(".jar")) {
                    classpathList.add(toolsFile.getAbsolutePath());
                }
            }
            Collections.sort(classpathList);
            if (mUseCurrentTradefed) {
                classpathList.add(getCurrentClassPath());
            }

            return Joiner.on(":").join(classpathList);
        }
    }

    private String getCurrentClassPath() {
        return System.getProperty("java.class.path");
    }

    /**
     * Build the classpath string based on jars in the XTS test root directory's tools folder.
     *
     * @return A string of classpaths.
     * @throws IOException
     */
    private String buildXtsClasspath(File ctsRoot) throws IOException {
        List<File> classpathList = new ArrayList<>();

        if (!ctsRoot.exists()) {
            throw new FileNotFoundException("Couldn't find the build directory: " + ctsRoot);
        }

        // Safe to assume single dir from extracted zip
        if (ctsRoot.list().length != 1) {
            throw new RuntimeException(
                    "List of sub directory does not contain only one item "
                            + "current list is:"
                            + Arrays.toString(ctsRoot.list()));
        }
        String mainDirName = ctsRoot.list()[0];
        // Jar files from the downloaded cts/xts
        File jarCtsPath = new File(new File(ctsRoot, mainDirName), "tools");
        if (jarCtsPath.listFiles().length == 0) {
            throw new FileNotFoundException(
                    String.format(
                            "Could not find any files under %s", jarCtsPath.getAbsolutePath()));
        }
        for (File toolsFile : jarCtsPath.listFiles()) {
            if (toolsFile.getName().endsWith(".jar")) {
                classpathList.add(toolsFile);
            }
        }
        Collections.sort(classpathList);

        return Joiner.on(":").join(classpathList);
    }

    /**
     * Parse test module names from the tradefed observatory's output JSON string.
     *
     * @param discoveryOutput JSON string from test discovery
     * @param dependencyListKey test dependency type
     * @return A list of test module names.
     * @throws JSONException
     */
    private List<String> parseTestDiscoveryOutput(String discoveryOutput, String dependencyListKey)
            throws JSONException {
        JSONObject jsonObject = new JSONObject(discoveryOutput);
        List<String> testModules = new ArrayList<>();
        if (jsonObject.has(dependencyListKey)) {
            JSONArray jsonArray = jsonObject.getJSONArray(dependencyListKey);
            for (int i = 0; i < jsonArray.length(); i++) {
                testModules.add(jsonArray.getString(i));
            }
        }
        return testModules;
    }

    private String parsePartialFallback(String discoveryOutput) throws JSONException {
        JSONObject jsonObject = new JSONObject(discoveryOutput);
        if (jsonObject.has(PARTIAL_FALLBACK_KEY)) {
            return jsonObject.getString(PARTIAL_FALLBACK_KEY);
        }
        return null;
    }

    private boolean hasNoPossibleDiscovery(String discoveryOutput) throws JSONException {
        JSONObject jsonObject = new JSONObject(discoveryOutput);
        if (jsonObject.has(NO_POSSIBLE_TEST_DISCOVERY_KEY)) {
            return true;
        }
        return false;
    }
}
