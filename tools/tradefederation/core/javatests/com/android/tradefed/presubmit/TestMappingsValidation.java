/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tradefed.presubmit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import static java.lang.String.format;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.ConfigurationUtil;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.config.IDeviceConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.PushFilePreparer;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.suite.ITestSuite;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.ModuleTestTypeUtil;
import com.android.tradefed.util.ZipUtil2;
import com.android.tradefed.util.testmapping.TestInfo;
import com.android.tradefed.util.testmapping.TestMapping;
import com.android.tradefed.util.testmapping.TestOption;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Validation tests to run against the TEST_MAPPING files in tests_mappings.zip to ensure they
 * contains the essential suite settings and no conflict test options.
 *
 * <p>Do not add to UnitTests.java. This is meant to run standalone.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class TestMappingsValidation implements IBuildReceiver {

    @Option(name = "test-group-to-validate", description = "The test groups to be validated.")
    private Set<String> mTestGroupToValidate =
            new HashSet<>(Arrays.asList("presubmit", "postsubmit", "presubmit-large"));

    @Option(name = "skip-modules", description = "Test modules that could be skipped.")
    private Set<String> mSkipModules = new HashSet<>();

    @Option(name = "enforce-module-name-check", description = "Enforce failing test if it is set.")
    private boolean mEnforceModuleNameCheck = false;

    // pattern used to identify java class names conforming to java naming conventions.
    private static final Pattern CLASS_OR_METHOD_REGEX =
            Pattern.compile(
                    "^([\\p{L}_$][\\p{L}\\p{N}_$]*\\.)*[\\p{Lu}_$][\\p{L}\\p{N}_$]*"
                            + "(#[\\p{L}_$][\\p{L}\\p{N}_$]*)?(\\[.*\\])?$");
    // pattern used to identify if this is regular expression with at least 1 '*' or '?'.
    private static final Pattern REGULAR_EXPRESSION = Pattern.compile("(\\?+)|(\\*+)");
    private static final String MODULE_INFO = "module-info.json";
    private static final String TEST_MAPPINGS_ZIP = "test_mappings.zip";
    private static final String INCLUDE_FILTER = "include-filter";
    private static final String EXCLUDE_FILTER = "exclude-filter";
    private static final String LOCAL_COMPATIBILITY_SUITES = "compatibility_suites";
    private static final String GENERAL_TESTS = "general-tests";
    private static final String DEVICE_TESTS = "device-tests";
    private static final String TEST_MAPPING_BUILD_SCRIPT_LINK =
            "https://source.android.com/compatibility/tests/development/test-mapping#packaging_build_script_rules";
    private static final String MODULE_NAME = "module_name";
    private static final String MAINLINE = "mainline";

    // Pair that shouldn't be overlapping. Test can only exists in one or the other.
    private static final Set<String> INCOMPATIBLE_PAIRS =
            ImmutableSet.of("presubmit", "presubmit-large");

    // Check the mainline parameter configured in a test config must end with .apk, .apks, or .apex.
    private static final Set<String> MAINLINE_PARAMETERS_TO_VALIDATE =
            new HashSet<>(Arrays.asList(".apk", ".apks", ".apex"));

    private File testMappingsDir = null;
    private boolean deleteUnzip = true;
    private IConfigurationFactory mConfigFactory = null;
    private IDeviceBuildInfo deviceBuildInfo = null;
    private IBuildInfo mBuild;
    private JsonObject mergedModuleInfo = new JsonObject();
    private Map<String, Set<TestInfo>> allTests = null;

    /** Type of filters used in test options in TEST_MAPPING files. */
    enum Filters {
        // Test option is regular expression format.
        REGEX,
        // Test option is class/method format.
        CLASS_OR_METHOD,
        // Test option is package format.
        PACKAGE
    }

    // TODO: Evaluate those modules if they can be moved out.
    private static final Set<String> EXEMPTED_DUPLICATION_PRESUBMIT_LARGE =
            ImmutableSet.of(
                    "CtsLibcoreOjTestCases",
                    "FrameworksCoreTests",
                    "CtsAppTestCases",
                    "CtsAppSecurityHostTestCases",
                    "FrameworksServicesTests",
                    "CtsLibcoreTestCases",
                    "CtsDevicePolicyManagerTestCases",
                    "CtsAutoFillServiceTestCases",
                    "CtsOsTestCases",
                    "CtsStatsdAtomHostTestCases",
                    "CtsPermission3TestCases",
                    "CtsPermissionUiTestCases",// Renamed from Permission3 in 2023
                    "CtsMediaAudioTestCases");

    private static final Set<String> PRESUBMIT_LARGE_ALLOWLIST = ImmutableSet.of(
                    "binderRpcTestNoKernel",
                    "CtsTelecomTestCases",
                    "CtsAppTestCases",
                    "binderRpcTestSingleThreadedNoKernel",
                    "CtsSuspendAppsPermissionTestCases",
                    "CtsAppSecurityHostTestCases",
                    "CtsPackageManagerTestCases", // Renamed from CtsAppSecurityHostTestCases
                    "FrameworksServicesTests",
                    "NeuralNetworksTest_static",
                    "CtsNNAPITestCases",
                    "CtsLibcoreTestCases",
                    "OverlayRemountedTest",
                    "CtsPermission3TestCases",
                    "CtsPermissionUiTestCases",
                    "sharedlibs_host_tests",
                    "CtsDevicePolicyManagerTestCases",
                    "CtsMediaAudioTestCases",
                    "CtsScopedStoragePublicVolumeHostTest",
                    "CtsContentTestCases",
                    "CtsHostsideNetworkTests",
                    "vm-tests-tf",
                    "CtsStatsdAtomHostTestCases",
                    "CtsMediaPlayerTestCases",
                    "CtsMediaDecoderTestCases",
                    "CtsQuickAccessWalletTestCases",
                    "CtsMediaMiscTestCases",
                    "NetworkStagedRollbackTest",
                    "CtsUsesNativeLibraryTest",
                    "RollbackTest",
                    "CtsLibcoreOjTestCases",
                    "CtsMultiUserHostTestCases",
                    "binderRpcTestSingleThreaded",
                    "sdkextensions_e2e_tests",
                    "StagedRollbackTest",
                    "CtsMediaEncoderTestCases",
                    "CtsRootRollbackManagerHostTestCases",
                    "StagedInstallInternalTest",
                    "FrameworksWifiTests",
                    "MultiUserRollbackTest",
                    "binderRpcTest",
                    "CtsMediaCodecTestCases",
                    "CtsRollbackManagerHostTestCases",
                    "CtsAutoFillServiceTestCases",
                    "CtsOsTestCases",
                    "CtsDynamicMimeHostTestCases",
                    "VtsHalNeuralnetworksTargetTest",
                    "CtsWifiTestCases",
                    "SystemUITests",
                    "CellBroadcastReceiverComplianceTests",
                    "InputMethodStressTest",
                    "SystemUIClocksTests",
                    "GtsStagedInstallHostTestCases");

    private static final Set<String> REMOUNT_MODULES =
            ImmutableSet.of(
                    "libnativeloader_e2e_tests",
                    "BugreportManagerTestCases",
                    "CtsRootBugreportTestCases",
                    "ApexServiceTestCases",
                    "OverlayDeviceTests");

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuild = buildInfo;
    }

    @Before
    public void setUp() throws IOException {
        Assume.assumeTrue(mBuild instanceof IDeviceBuildInfo);
        mConfigFactory = ConfigurationFactory.getInstance();
        deviceBuildInfo = (IDeviceBuildInfo) mBuild;
        if (deviceBuildInfo.getFile(TEST_MAPPINGS_ZIP).isDirectory()) {
            testMappingsDir = deviceBuildInfo.getFile(TEST_MAPPINGS_ZIP);
            deleteUnzip = false;
        } else {
            testMappingsDir =
                    TestMapping.extractTestMappingsZip(deviceBuildInfo.getFile(TEST_MAPPINGS_ZIP));
            deleteUnzip = true;
        }
        mergeModuleInfo(deviceBuildInfo.getFile(MODULE_INFO));
        for (String fileKey : deviceBuildInfo.getVersionedFileKeys()) {
            if (fileKey.contains("additional-module-info")) {
                CLog.i("Merging additional %s.json", fileKey);
                mergeModuleInfo(deviceBuildInfo.getFile(fileKey));
            }
        }
        TestMapping testMapping = new TestMapping();
        allTests = testMapping.getAllTests(testMappingsDir);
    }

    @After
    public void tearDown() {
        if (deleteUnzip) {
            FileUtil.recursiveDelete(testMappingsDir);
        }
    }

    /**
     * Test all the test config files and make sure they are properly configured with parameterized
     * mainline modules.
     */
    @Test
    public void testValidTestConfigForParameterizedMainlineModules() throws IOException {
        File configZip = deviceBuildInfo.getFile("general-tests_configs.zip");
        Assume.assumeTrue(configZip != null);
        List<String> errors = new ArrayList<>();
        List<String> testConfigs = new ArrayList<>();
        File testConfigDir = ZipUtil2.extractZipToTemp(configZip, "general-tests_configs");
        testConfigs.addAll(
                ConfigurationUtil.getConfigNamesFromDirs(null, Arrays.asList(testConfigDir)));
        for (String configName : testConfigs) {
            try {
                IConfiguration config =
                        mConfigFactory.createConfigurationFromArgs(new String[] {configName});
                List<String> params = config.getConfigurationDescription().getMetaData(
                        ITestSuite.MAINLINE_PARAMETER_KEY);
                if (params == null || params.isEmpty()) {
                    continue;
                }
                for (String param : params) {
                    String error = validateMainlineModuleConfig(param, Set.of(configName));
                    if (!Strings.isNullOrEmpty(error)){
                        errors.add(error);
                    }
                }
            } catch (ConfigurationException e) {
                errors.add(String.format("\t%s: %s", configName, e.getMessage()));
            }
        }

        if (!errors.isEmpty()) {
            fail(String.format("Fail Test config check for parameterized mainline module: \n%s",
                    Joiner.on("\n").join(errors)));
        }
    }

    /**
     * This test can only be enabled for BVT only cause it needs many other build targets involved.
     * Test all TEST_MAPPING files and make sure each test entry is properly configured in build
     * targets.
     */
    @Test
    public void testValidateTestEntry() throws Exception {
        List<String> errors = new ArrayList<>();
        Set<String> checkedModule = new HashSet<>();

        Map<String, Set<String>> incompatiblePairsTracking = new HashMap<>();
        for (String s : INCOMPATIBLE_PAIRS) {
            incompatiblePairsTracking.put(s, new HashSet<>());
        }

        for (String testGroup : allTests.keySet()) {
            if (!mTestGroupToValidate.contains(testGroup)) {
                CLog.d("Skip checking tests with group: %s", testGroup);
                continue;
            }
            for (TestInfo testInfo : allTests.get(testGroup)) {
                String moduleName = testInfo.getName();
                if (mSkipModules.contains(moduleName)) {
                    CLog.w("Test Module: %s is in the skip list. Ignore checking...", moduleName);
                    continue;
                }
                if (testGroup.contains(MAINLINE)) {
                    errors.addAll(validateMainlineTest(testInfo));
                } else {
                    if (!mergedModuleInfo.has(moduleName)) {
                        errors.add(
                                format(
                                        "Test Module: %s doesn't exist in any build targets,"
                                                + " TEST_MAPPING file path: %s",
                                        testInfo.getName(), testInfo.getSources()));
                    }
                }
                checkedModule.add(moduleName);
                if (incompatiblePairsTracking.containsKey(testGroup)) {
                    incompatiblePairsTracking.get(testGroup).add(moduleName);
                }
            }
        }
        if (!errors.isEmpty()) {
            String error =
                    format(
                            "Fail test entry check. Some test modules are not found or the module"
                                + " name has been changed or been removed from build file. \n\n"
                                + "To locate owner that is responsible for the breakage, try to do"
                                + " code search on the test modules, check the changelog/blame of"
                                + " the broken TEST_MAPPING file or Android.bp/mk to locate the"
                                + " owner.\n\n"
                                + "Details: \n"
                                + "%s",
                            Joiner.on("\n").join(errors));
            if (!mEnforceModuleNameCheck) {
                CLog.w(error);
            } else {
                fail(error);
            }
        }
        // Validate incompatible pairs
        validateIncompatiblePairs(incompatiblePairsTracking);
        // Validate basic configuration in shared pools
        validateConfigsOfSharedPool(checkedModule);
    }

    /**
     * Test all the TEST_MAPPING files and make sure they contain the suite setting in
     * module-info.json.
     */
    @Test
    public void testTestSuiteSetting() {
        List<String> errors = new ArrayList<>();
        for (String testGroup : allTests.keySet()) {
            if (!mTestGroupToValidate.contains(testGroup)) {
                CLog.d("Skip checking tests with group: %s", testGroup);
                continue;
            }
            for (TestInfo testInfo : allTests.get(testGroup)) {
                String moduleName = testInfo.getName();
                if (mSkipModules.contains(moduleName)) {
                    CLog.w("Test Module: %s is in the skip list. Ignore checking...", moduleName);
                    continue;
                }
                if (testGroup.contains(MAINLINE)) {
                    Matcher matcher = TestMapping.MAINLINE_REGEX.matcher(moduleName);
                    if (matcher.find()) {
                        moduleName = matcher.group(1);
                    }
                }
                if (!validateSuiteSetting(moduleName)) {
                    errors.add(
                            String.format(
                                    "Missing test_suite setting for test: %s, test group: %s, "
                                            + "TEST_MAPPING file path: %s",
                                    testInfo.getName(), testGroup, testInfo.getSources()));
                }
            }
        }
        if (!errors.isEmpty()) {
            fail(
                    String.format(
                            "Fail test_suite setting check:\n%s\nPlease refer to following link " +
                                "for more details about test suite configuration.\n %s",
                                Joiner.on("\n").join(errors), TEST_MAPPING_BUILD_SCRIPT_LINK
                    )
            );
        }
    }

    /**
     * Test all the tests by each test group and make sure the file options aren't conflict to AJUR
     * rules.
     */
    @Test
    public void testFilterOptions() {
        List<String> errors = new ArrayList<>();
        for (String testGroup : allTests.keySet()) {
            for (String moduleName : getModuleNames(testGroup)) {
                errors.addAll(validateFilterOption(moduleName, INCLUDE_FILTER, testGroup));
                errors.addAll(validateFilterOption(moduleName, EXCLUDE_FILTER, testGroup));
            }
        }
        if (!errors.isEmpty()) {
            fail(
                    String.format(
                            "Fail include/exclude filter setting check:\n%s",
                            Joiner.on("\n").join(errors)));
        }
    }

    /** Test to ensure performance test modules are not included for test mapping. */
    @Test
    public void testNoPerformanceTests() throws IOException {
        Set<String> modules = new HashSet<>();

        for (String testGroup : allTests.keySet()) {
            if (!mTestGroupToValidate.contains(testGroup)) {
                CLog.d("Skip checking tests with group: %s", testGroup);
                continue;
            }
            for (TestInfo testInfo : allTests.get(testGroup)) {
                modules.add(testInfo.getName());
            }
        }

        File testConfigDir = null;
        File deviceTestConfigDir = null;
        try {
            File configZip = deviceBuildInfo.getFile("general-tests_configs.zip");
            File deviceConfigZip = deviceBuildInfo.getFile("device-tests_configs.zip");
            Assume.assumeTrue(configZip != null);
            List<String> testConfigs = new ArrayList<>();
            List<File> dirToLoad = new ArrayList<>();
            testConfigDir = ZipUtil2.extractZipToTemp(configZip, "general-tests_configs");
            dirToLoad.add(testConfigDir);
            if (deviceConfigZip != null) {
                deviceTestConfigDir =
                        ZipUtil2.extractZipToTemp(deviceConfigZip, "device-tests_configs");
                dirToLoad.add(deviceTestConfigDir);
            }
            testConfigs.addAll(ConfigurationUtil.getConfigNamesFromDirs(null, dirToLoad));
            CLog.d("Checking modules: %s. And configs: %s", modules, testConfigs);

            List<String> performanceModules = new ArrayList<>();
            for (String configName : testConfigs) {
                String module = FileUtil.getBaseName(new File(configName).getName());
                if (!modules.contains(module)) {
                    continue;
                }
                IConfiguration config =
                        mConfigFactory.createConfigurationFromArgs(new String[] {configName});
                if (ModuleTestTypeUtil.isPerformanceModule(config)) {
                    performanceModules.add(module);
                }
            }
            if (!performanceModules.isEmpty()) {
                fail(
                        String.format(
                                "Performance modules not allowed in test mapping:\n%s",
                                Joiner.on("\n").join(performanceModules)));
            }
        } catch (ConfigurationException e) {
            fail(e.toString());
        } finally {
            FileUtil.recursiveDelete(testConfigDir);
            FileUtil.recursiveDelete(deviceTestConfigDir);
        }
    }

    /**
     * Validate if the filter option of a test contains both class/method and package. options.
     *
     * @param moduleName A {@code String} name of a test module.
     * @param filterOption A {@code String} of the filter option defined in TEST MAPPING file.
     * @param testGroup A {@code String} name of the test group.
     * @return A {@code List<String>} of the validation errors.
     */
    private List<String> validateFilterOption(
            String moduleName, String filterOption, String testGroup) {
        List<String> errors = new ArrayList<>();
        for (TestInfo test : getTestInfos(moduleName, testGroup)) {
            Set<Filters> filterTypes = new HashSet<>();
            Map<Filters, Set<TestInfo>> filterTestInfos = new HashMap<>();
            for (TestOption options : test.getOptions()) {
                if (options.getName().equals(filterOption)) {
                    Filters optionType = getOptionType(options.getValue());
                    // Add optionType with each TestInfo to get the detailed information.
                    filterTestInfos.computeIfAbsent(optionType, k -> new HashSet<>()).add(test);
                }
            }
            filterTypes = filterTestInfos.keySet();
            // If the options of a test in one TEST_MAPPING file contain either REGEX,
            // CLASS_OR_METHOD, or PACKAGE, it should be caught and output the tests
            // information.
            // TODO(b/128947872): List the type with fewest options first.
            if (filterTypes.size() > 1) {
                errors.add(
                        String.format(
                                "Mixed filter types found. Test: %s , TestGroup: %s, Details:\n"
                                        + "%s",
                                moduleName,
                                testGroup,
                                getDetailedErrors(filterOption, filterTestInfos)));
            }
        }
        return errors;
    }

    /**
     * Get the detailed validation errors.
     *
     * @param filterOption A {@code String} of the filter option defined in TEST MAPPING file.
     * @param filterTestInfos A {@code Map<Filters, Set<TestInfo>>} of tests with the given filter
     *     type and its child test information.
     * @return A {@code String} of the detailed errors.
     */
    private String getDetailedErrors(
            String filterOption, Map<Filters, Set<TestInfo>> filterTestInfos) {
        StringBuilder errors = new StringBuilder("");
        Set<Map.Entry<Filters, Set<TestInfo>>> entries = filterTestInfos.entrySet();
        for (Map.Entry<Filters, Set<TestInfo>> entry : entries) {
            Set<TestInfo> testInfos = entry.getValue();
            StringBuilder detailedErrors = new StringBuilder("");
            for (TestInfo test : testInfos) {
                for (TestOption options : test.getOptions()) {
                    if (options.getName().equals(filterOption)) {
                        detailedErrors.append(
                                String.format(
                                        "  %s (%s)\n", options.getValue(), test.getSources()));
                    }
                }
            }
            errors.append(
                    String.format(
                            "Options using %s filter:\n%s",
                            entry.getKey().toString(), detailedErrors));
        }
        return errors.toString();
    }

    /**
     * Determine whether optionValue represents regrex, test class or method, or package.
     *
     * @param optionValue A {@code String} containing either an individual test regrex, class/method
     *     or a package.
     * @return A {@code Filters} representing regrex, test class or method, or package.
     */
    private Filters getOptionType(String optionValue) {
        if (REGULAR_EXPRESSION.matcher(optionValue).find()) {
            return Filters.REGEX;
        } else if (CLASS_OR_METHOD_REGEX.matcher(optionValue).find()) {
            return Filters.CLASS_OR_METHOD;
        }
        return Filters.PACKAGE;
    }

    /** Test {@link TestMappingsValidation#getOptionType(String)} */
    @Test
    public void testGetOptionType() throws Exception {
        assertEquals(Filters.PACKAGE, getOptionType("a.b"));
        assertEquals(Filters.CLASS_OR_METHOD, getOptionType("a.b.Class#someMethod"));
        assertEquals(Filters.CLASS_OR_METHOD, getOptionType("a.b.Class#someMethod[some_param]"));
        assertEquals(Filters.CLASS_OR_METHOD, getOptionType("a.b.Class"));
        assertEquals(Filters.REGEX, getOptionType("a.b.c\\.*"));
        assertEquals(Filters.REGEX, getOptionType("a.b.Class.\\.*"));
        assertEquals(Filters.REGEX, getOptionType("a.b.Class#method.*"));
    }

    /**
     * Validate if the mainline module parameter is properly configured in the config.
     *
     * @param param A {@code String} name of the mainline module parameter.
     * @param paths A {@code Set<String>} path of the test config or TEST_MAPPING file.
     * @return A {@code String} of the errors.
     */
    private String validateMainlineModuleConfig(String param, Set<String> paths) {
        StringBuilder errors = new StringBuilder("");
        if (!isInAlphabeticalOrder(param)) {
            errors.append(
                    String.format(
                            "Illegal mainline module parameter: \"%s\" configured in the %s. " +
                                "Parameter must be configured in alphabetical order and with no " +
                                "duplicated modules.", param, paths));
        }
        else if (!isValidMainlineParam(param)) {
            errors.append(
                    String.format(
                            "Illegal mainline module parameter: \"%s\" configured in the %s. " +
                                "Parameter must end with .apk/.apex/.apks and have no any spaces " +
                                "configured.", param, paths));
        }
        return errors.toString();
    }

    /** Whether a mainline parameter configured in a test config is in alphabetical order or not. */
    boolean isInAlphabeticalOrder(String param) {
        String previousString = "";
        for (String currentString : param.split(String.format("\\+"))) {
            // This is to check if the parameter is in alphabetical order or duplicated.
            if (currentString.compareTo(previousString) <= 0) {
                return false;
            }
            previousString = currentString;
        }
        return true;
    }

    /** Whether the mainline parameter configured in the test config is valid or not. */
    boolean isValidMainlineParam(String param) {
        if (param.contains(" ")) {
            return false;
        }
        for (String m : param.split(String.format("\\+"))) {
            if (!MAINLINE_PARAMETERS_TO_VALIDATE.stream().anyMatch(entry -> m.endsWith(entry))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validate if the name exists in module-info.json and with the correct suite setting.
     *
     * @param name A {@code String} name of the test.
     * @return true if name exists in module-info.json and matches either "general-tests" or
     *     "device-tests", or name doesn't exist in module-info.json.
     */
    private boolean validateSuiteSetting(String name) {
        if (!mergedModuleInfo.has(name)) {
            CLog.w("Test Module: %s can't be found in module-info.json. Ignore checking...", name);
            return true;
        }
        JsonArray compatibilitySuites =
                mergedModuleInfo
                        .getAsJsonObject(name)
                        .get(LOCAL_COMPATIBILITY_SUITES)
                        .getAsJsonArray();
        for (int i = 0; i < compatibilitySuites.size(); i++) {
            String suite = compatibilitySuites.get(i).getAsString();
            if (suite.equals(GENERAL_TESTS) || suite.equals(DEVICE_TESTS)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the module names for the given test group.
     *
     * @param testGroup A {@code String} name of the test group.
     * @return A {@code Set<String>} containing the module names for the given test group.
     */
    private Set<String> getModuleNames(String testGroup) {
        Set<String> moduleNames = new HashSet<>();
        for (TestInfo test : allTests.get(testGroup)) {
            moduleNames.add(test.getName());
        }
        return moduleNames;
    }

    /**
     * Get the test infos for the given module name and test group.
     *
     * @param moduleName A {@code String} name of a test module.
     * @param testGroup A {@code String} name of the test group.
     * @return A {@code Set<TestInfo>} of tests that each is for a unique test module.
     */
    private Set<TestInfo> getTestInfos(String moduleName, String testGroup) {
        Set<TestInfo> testInfos = new HashSet<>();
        for (TestInfo test : allTests.get(testGroup)) {
            if (test.getName().equals(moduleName)) {
                testInfos.add(test);
            }
        }
        return testInfos;
    }

    private void mergeModuleInfo(File file) throws IOException {
        JsonObject json = new Gson().fromJson(FileUtil.readStringFromFile(file), JsonObject.class);
        json.entrySet()
                .forEach(
                        moduleInfo ->
                                mergeModuleInfoByName(moduleInfo.getValue().getAsJsonObject()));
    }

    private void mergeModuleInfoByName(JsonObject jsonObject) {
        mergedModuleInfo.add(jsonObject.get(MODULE_NAME).getAsString(), jsonObject);
    }

    /** Validate mainline test with parameterized mainline modules is properly configured. */
    private List<String> validateMainlineTest(TestInfo testInfo) {
        List<String> errors = new ArrayList<>();
        try {
            Matcher matcher = TestMapping.getMainlineTestModuleName(testInfo);
            if (!mergedModuleInfo.has(matcher.group(1))) {
                errors.add(
                        format(
                                "Test Module: %s doesn't exist in any build targets,"
                                        + " TEST_MAPPING file path: %s",
                                testInfo.getName(), testInfo.getSources()));
            }
            String error = validateMainlineModuleConfig(matcher.group(2), testInfo.getSources());
            if (!Strings.isNullOrEmpty(error)) {
                errors.add(error);
            }
        } catch (ConfigurationException e) {
            errors.add(e.getMessage());
        }
        return errors;
    }

    private void validateConfigsOfSharedPool(Set<String> checkedModule) throws Exception {
        File configZip = deviceBuildInfo.getFile("general-tests_configs.zip");
        File deviceConfigZip = deviceBuildInfo.getFile("device-tests_configs.zip");
        Assume.assumeTrue(configZip != null);
        List<String> testConfigs = new ArrayList<>();
        List<File> dirToLoad = new ArrayList<>();
        File testConfigDir = ZipUtil2.extractZipToTemp(configZip, "general-tests_configs");
        File deviceTestConfigDir = null;
        dirToLoad.add(testConfigDir);
        if (deviceConfigZip != null) {
            deviceTestConfigDir =
                    ZipUtil2.extractZipToTemp(deviceConfigZip, "device-tests_configs");
            dirToLoad.add(deviceTestConfigDir);
        }
        try {
            testConfigs.addAll(ConfigurationUtil.getConfigNamesFromDirs(null, dirToLoad));
            CLog.d("Checking modules: %s. And configs: %s", checkedModule, testConfigs);
            List<String> errors = new ArrayList<>();
            for (String configName : testConfigs) {
                String fileName = FileUtil.getBaseName(new File(configName).getName());
                if (!checkedModule.contains(fileName)) {
                    continue;
                }
                try {
                    IConfiguration config =
                            mConfigFactory.createConfigurationFromArgs(new String[] {configName});
                    for (IDeviceConfiguration dConfig : config.getDeviceConfig()) {
                        for (ITargetPreparer prep : dConfig.getTargetPreparers()) {
                            if (prep instanceof PushFilePreparer) {
                                PushFilePreparer pushPreparer = (PushFilePreparer) prep;
                                if (pushPreparer.shouldRemountSystem()
                                        || pushPreparer.shouldRemountVendor()) {
                                    if (REMOUNT_MODULES.contains(fileName)) {
                                        continue;
                                    }
                                    throw new ConfigurationException(
                                            String.format(
                                                    "%s: Shouldn't use 'remount-system' or"
                                                        + " 'remount-vendor' in shared test mapping"
                                                        + " pools.",
                                                    fileName));
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    errors.add(e.toString());
                }
            }

            if (!errors.isEmpty()) {
                fail(Joiner.on("\n").join(errors));
            }
        } finally {
            FileUtil.recursiveDelete(testConfigDir);
            FileUtil.recursiveDelete(deviceTestConfigDir);
        }
    }

    private void validateIncompatiblePairs(Map<String, Set<String>> incompatiblePairsTracking)
            throws ConfigurationException {
        Set<String> presubmitLarge = incompatiblePairsTracking.get("presubmit-large");
        Set<String> presubmit = incompatiblePairsTracking.get("presubmit");

        Set<String> dual =
                presubmitLarge.stream()
                        .filter(e -> presubmit.contains(e))
                        .collect(Collectors.toSet());

        dual.removeIf(e -> EXEMPTED_DUPLICATION_PRESUBMIT_LARGE.contains(e));

        if (!dual.isEmpty()) {
            throw new ConfigurationException(
                    String.format(
                            "the following entries exist in both presubmit and "
                                    + "presubmit-large groups: %s. Do not use "
                                    + "presubmit-large group to run larger tests. "
                                    + "Make sure your tests meet presubmit SLO "
                                    + "and use 'presubmit' group.",
                            dual));
        }
        presubmitLarge.removeAll(PRESUBMIT_LARGE_ALLOWLIST);
        if (!presubmitLarge.isEmpty()) {
            throw new ConfigurationException(
                    String.format(
                            "Adding new modules to presubmit-large "
                                    + "isn't allowed. Those tests: %s. Do not use "
                                    + "presubmit-large group to run larger tests. Make sure"
                                    + " your tests meet presubmit SLO and use 'presubmit'"
                                    + "group.",
                            presubmitLarge));
        }
    }
}
