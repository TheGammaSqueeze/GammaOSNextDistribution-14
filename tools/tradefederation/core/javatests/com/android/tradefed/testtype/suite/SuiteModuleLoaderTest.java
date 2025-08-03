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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationDef;
import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceFoldableState;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.targetprep.BaseTargetPreparer;
import com.android.tradefed.targetprep.InstallApexModuleTargetPreparer;
import com.android.tradefed.testtype.Abi;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.ITestFileFilterReceiver;
import com.android.tradefed.testtype.ITestFilterReceiver;
import com.android.tradefed.testtype.suite.params.ModuleParameters;
import com.android.tradefed.util.FileUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Unit tests for {@link SuiteModuleLoader}. */
@RunWith(JUnit4.class)
public class SuiteModuleLoaderTest {

    private static final String TEST_CONFIG =
            "<configuration description=\"Runs a stub tests part of some suite\">\n"
                + "    <target_preparer"
                + " class=\"com.android.tradefed.testtype.suite.SuiteModuleLoaderTest$PreparerInject\""
                + " />\n"
                + "    <test"
                + " class=\"com.android.tradefed.testtype.suite.SuiteModuleLoaderTest$TestInject\""
                + " />\n"
                + "</configuration>";

    private static final String TEST_NOT_MULTI_ABI_CONFIG =
            "<configuration description=\"Runs a stub tests part of some suite\">\n"
                    + "    <option name=\"config-descriptor:metadata\" key=\"parameter\""
                    + " value=\"not_multi_abi\" />\n"
                    + "    <test class=\"com.android.tradefed.testtype.suite.TestSuiteStub\" />\n"
                    + "</configuration>";

    private static final String TEST_INSTANT_CONFIG =
            "<configuration description=\"Runs a stub tests part of some suite\">\n"
                    + "    <option name=\"config-descriptor:metadata\" key=\"parameter\""
                    + " value=\"instant_app\" />"
                    // Duplicate parameter should not have impact
                    + "    <option name=\"config-descriptor:metadata\" key=\"parameter\""
                    + " value=\"instant_app\" />    <test"
                    + " class=\"com.android.tradefed.testtype.suite.TestSuiteStub\" />\n"
                    + "</configuration>";

    private static final String TEST_FOLDABLE_CONFIG =
            "<configuration description=\"Runs a stub tests part of some suite\">\n"
                    + "    <option name=\"config-descriptor:metadata\" key=\"parameter\""
                    + " value=\"all_foldable_states\" />    <test"
                    + " class=\"com.android.tradefed.testtype.suite.TestSuiteStub\" />\n"
                    + "</configuration>";

    private static final String TEST_MAINLINE_CONFIG =
            "<configuration description=\"Runs a stub tests part of some suite\">\n"
                    + "    <option name=\"config-descriptor:metadata\" key=\"mainline-param\""
                    + " value=\"mod1.apk\" />"
                    // Duplicate parameter should not have impact
                    + "    <option name=\"config-descriptor:metadata\" key=\"mainline-param\""
                    + " value=\"mod2.apk\" />    <option name=\"config-descriptor:metadata\""
                    + " key=\"mainline-param\" value=\"mod1.apk+mod2.apk\" />    <option"
                    + " name=\"config-descriptor:metadata\" key=\"mainline-param\""
                    + " value=\"mod1.apk+mod2.apk\" />    <option"
                    + " name=\"config-descriptor:metadata\" key=\"mainline-param\""
                    + " value=\"mod1.apk\" />    <test"
                    + " class=\"com.android.tradefed.testtype.suite.TestSuiteStub\" />\n"
                    + "</configuration>";

    private SuiteModuleLoader mRepo;
    private File mTestsDir;
    private Set<IAbi> mAbis;
    private IInvocationContext mContext;
    @Mock IBuildInfo mMockBuildInfo;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mRepo =
                new SuiteModuleLoader(
                        new LinkedHashMap<String, LinkedHashSet<SuiteTestFilter>>(),
                        new LinkedHashMap<String, LinkedHashSet<SuiteTestFilter>>(),
                        new ArrayList<>(),
                        new ArrayList<>());
        mTestsDir = FileUtil.createTempDir("suite-module-loader-tests");
        mAbis = new LinkedHashSet<>();
        mAbis.add(new Abi("armeabi-v7a", "32"));
        mContext = new InvocationContext();

        when(mMockBuildInfo.getBuildBranch()).thenReturn("branch");
        when(mMockBuildInfo.getBuildFlavor()).thenReturn("flavor");
        when(mMockBuildInfo.getBuildId()).thenReturn("id");

        mContext.addDeviceBuildInfo(ConfigurationDef.DEFAULT_DEVICE_NAME, mMockBuildInfo);
    }

    @After
    public void tearDown() {
        FileUtil.recursiveDelete(mTestsDir);
    }

    private void createModuleConfig(String moduleName) throws IOException {
        File module = new File(mTestsDir, moduleName + SuiteModuleLoader.CONFIG_EXT);
        FileUtil.writeToFile(TEST_CONFIG, module);
    }

    private void createNotMultiAbiModuleConfig(String moduleName) throws IOException {
        File module = new File(mTestsDir, moduleName + SuiteModuleLoader.CONFIG_EXT);
        FileUtil.writeToFile(TEST_NOT_MULTI_ABI_CONFIG, module);
    }

    private void createInstantModuleConfig(String moduleName) throws IOException {
        File module = new File(mTestsDir, moduleName + SuiteModuleLoader.CONFIG_EXT);
        FileUtil.writeToFile(TEST_INSTANT_CONFIG, module);
    }

    private void createFoldableModuleConfig(String moduleName) throws IOException {
        File module = new File(mTestsDir, moduleName + SuiteModuleLoader.CONFIG_EXT);
        FileUtil.writeToFile(TEST_FOLDABLE_CONFIG, module);
    }

    private void createMainlineModuleConfig(String moduleName) throws IOException {
        File moduleConfig = new File(mTestsDir, moduleName + SuiteModuleLoader.CONFIG_EXT);
        FileUtil.writeToFile(TEST_MAINLINE_CONFIG, moduleConfig);
    }

    @OptionClass(alias = "preparer-inject")
    public static class PreparerInject extends BaseTargetPreparer {
        @Option(name = "preparer-string")
        public String preparer = null;
    }

    @OptionClass(alias = "test-inject")
    public static class TestInject
            implements IRemoteTest, ITestFileFilterReceiver, ITestFilterReceiver {
        @Option(name = "simple-string")
        public String test = null;

        @Option(name = "empty-string")
        public String testEmpty = null;

        @Option(name = "alias-option")
        public String testAlias = null;

        @Option(name = "list-string")
        public List<String> testList = new ArrayList<>();

        @Option(name = "map-string")
        public Map<String, String> testMap = new HashMap<>();

        public File mIncludeTestFile;
        public File mExcludeTestFile;

        @Override
        public void run(TestInformation testInfo, ITestInvocationListener listener)
                throws DeviceNotAvailableException {}

        @Override
        public void setIncludeTestFile(File testFile) {
            mIncludeTestFile = testFile;
        }

        @Override
        public void setExcludeTestFile(File testFile) {
            mExcludeTestFile = testFile;
        }

        @Override
        public void addIncludeFilter(String filter) {
            // NA
        }

        @Override
        public void addAllIncludeFilters(Set<String> filters) {
            // NA
        }

        @Override
        public void addExcludeFilter(String filter) {
            // NA
        }

        @Override
        public void addAllExcludeFilters(Set<String> filters) {
            // NA
        }

        @Override
        public Set<String> getIncludeFilters() {
            return null;
        }

        @Override
        public Set<String> getExcludeFilters() {
            return null;
        }

        @Override
        public void clearIncludeFilters() {
            // NA
        }

        @Override
        public void clearExcludeFilters() {
            // NA
        }
    }

    /** Test an end-to-end injection of --module-arg. */
    @Test
    public void testInjectConfigOptions_moduleArgs() throws Exception {
        List<String> moduleArgs = new ArrayList<>();
        moduleArgs.add("module1[test]:simple-string:value1");
        moduleArgs.add("module1[test]:empty-string:"); // value is the empty string

        moduleArgs.add("module1[test]:list-string:value2");
        moduleArgs.add("module1[test]:list-string:value3");
        moduleArgs.add("module1[test]:list-string:set-option:moreoption");
        moduleArgs.add("module1[test]:list-string:"); // value is the empty string
        moduleArgs.add("module1[test]:map-string:set-option:=moreoption");
        moduleArgs.add("module1[test]:map-string:empty-option:="); // value is the empty string

        createModuleConfig("module1[test]");

        Map<String, LinkedHashSet<SuiteTestFilter>> includeFilter = new LinkedHashMap<>();
        SuiteTestFilter filter = SuiteTestFilter.createFrom("armeabi-v7a module1[test] test#test");
        LinkedHashSet<SuiteTestFilter> mapFilters = new LinkedHashSet<>();
        mapFilters.add(filter);
        includeFilter.put("armeabi-v7a module1[test]", mapFilters);
        mRepo =
                new SuiteModuleLoader(
                        includeFilter,
                        new LinkedHashMap<String, LinkedHashSet<SuiteTestFilter>>(),
                        new ArrayList<>(),
                        moduleArgs);
        List<String> patterns = new ArrayList<>();
        patterns.add(".*.config");
        patterns.add(".*.xml");
        LinkedHashMap<String, IConfiguration> res =
                mRepo.loadConfigsFromDirectory(
                        Arrays.asList(mTestsDir), mAbis, null, null, patterns);
        assertNotNull(res.get("armeabi-v7a module1[test]"));
        IConfiguration config = res.get("armeabi-v7a module1[test]");

        TestInject checker = (TestInject) config.getTests().get(0);
        assertEquals("value1", checker.test);
        assertEquals("", checker.testEmpty);
        // Check list
        assertTrue(checker.testList.size() == 4);
        assertTrue(checker.testList.contains("value2"));
        assertTrue(checker.testList.contains("value3"));
        assertTrue(checker.testList.contains("set-option:moreoption"));
        assertTrue(checker.testList.contains(""));
        // Chech map
        assertTrue(checker.testMap.size() == 2);
        assertEquals("moreoption", checker.testMap.get("set-option"));
        assertEquals("", checker.testMap.get("empty-option"));
        // Check filters
        assertNotNull(checker.mIncludeTestFile);
        assertNull(checker.mExcludeTestFile);
        assertTrue(checker.mIncludeTestFile.getName().contains("armeabi-v7a%20module1%5Btest%5"));
        FileUtil.deleteFile(checker.mIncludeTestFile);
    }

    /** Test an end-to-end injection of --test-arg. */
    @Test
    public void testInjectConfigOptions_testArgs() throws Exception {
        List<String> testArgs = new ArrayList<>();
        // Value for ITargetPreparer
        testArgs.add(
                "com.android.tradefed.testtype.suite.SuiteModuleLoaderTest$PreparerInject:"
                        + "preparer-string:preparer");
        // Values for IRemoteTest
        testArgs.add(
                "com.android.tradefed.testtype.suite.SuiteModuleLoaderTest$TestInject:"
                        + "simple-string:value1");
        testArgs.add(
                "com.android.tradefed.testtype.suite.SuiteModuleLoaderTest$TestInject:"
                        + "empty-string:"); // value is the empty string
        testArgs.add(
                "com.android.tradefed.testtype.suite.SuiteModuleLoaderTest$TestInject:"
                        + "list-string:value2");
        testArgs.add(
                "com.android.tradefed.testtype.suite.SuiteModuleLoaderTest$TestInject:"
                        + "list-string:value3");
        testArgs.add(
                "com.android.tradefed.testtype.suite.SuiteModuleLoaderTest$TestInject:"
                        + "list-string:set-option:moreoption");
        testArgs.add(
                "com.android.tradefed.testtype.suite.SuiteModuleLoaderTest$TestInject:"
                        + "list-string:"); // value is the empty string
        testArgs.add(
                "com.android.tradefed.testtype.suite.SuiteModuleLoaderTest$TestInject:"
                        + "map-string:set-option:=moreoption");
        testArgs.add(
                "com.android.tradefed.testtype.suite.SuiteModuleLoaderTest$TestInject:"
                        + "map-string:empty-option:="); // value is the empty string

        createModuleConfig("module1");

        mRepo =
                new SuiteModuleLoader(
                        new LinkedHashMap<String, LinkedHashSet<SuiteTestFilter>>(),
                        new LinkedHashMap<String, LinkedHashSet<SuiteTestFilter>>(),
                        testArgs,
                        new ArrayList<>());
        List<String> patterns = new ArrayList<>();
        patterns.add(".*.config");
        patterns.add(".*.xml");
        LinkedHashMap<String, IConfiguration> res =
                mRepo.loadConfigsFromDirectory(
                        Arrays.asList(mTestsDir), mAbis, null, null, patterns);
        assertNotNull(res.get("armeabi-v7a module1"));
        IConfiguration config = res.get("armeabi-v7a module1");

        PreparerInject preparer = (PreparerInject) config.getTargetPreparers().get(0);
        assertEquals("preparer", preparer.preparer);

        TestInject checker = (TestInject) config.getTests().get(0);
        assertEquals("value1", checker.test);
        assertEquals("", checker.testEmpty);
        // Check list
        assertTrue(checker.testList.size() == 4);
        assertTrue(checker.testList.contains("value2"));
        assertTrue(checker.testList.contains("value3"));
        assertTrue(checker.testList.contains("set-option:moreoption"));
        assertTrue(checker.testList.contains(""));
        // Chech map
        assertTrue(checker.testMap.size() == 2);
        assertEquals("moreoption", checker.testMap.get("set-option"));
        assertEquals("", checker.testMap.get("empty-option"));
    }

    @Test
    public void testInjectConfigOptions_moduleArgs_alias() throws Exception {
        List<String> moduleArgs = new ArrayList<>();
        moduleArgs.add("module1:{test-inject}alias-option:value1");

        createModuleConfig("module1");

        mRepo =
                new SuiteModuleLoader(
                        new LinkedHashMap<String, LinkedHashSet<SuiteTestFilter>>(),
                        new LinkedHashMap<String, LinkedHashSet<SuiteTestFilter>>(),
                        new ArrayList<>(),
                        moduleArgs);
        List<String> patterns = new ArrayList<>();
        patterns.add(".*.config");
        patterns.add(".*.xml");
        LinkedHashMap<String, IConfiguration> res =
                mRepo.loadConfigsFromDirectory(
                        Arrays.asList(mTestsDir), mAbis, null, null, patterns);
        assertNotNull(res.get("armeabi-v7a module1"));
        IConfiguration config = res.get("armeabi-v7a module1");

        TestInject checker = (TestInject) config.getTests().get(0);
        assertEquals("value1", checker.testAlias);
    }

    @Test
    public void testLoad_notMultiAbi() throws Exception {
        createNotMultiAbiModuleConfig("module1");

        mRepo =
                new SuiteModuleLoader(
                        new LinkedHashMap<String, LinkedHashSet<SuiteTestFilter>>(),
                        new LinkedHashMap<String, LinkedHashSet<SuiteTestFilter>>(),
                        new ArrayList<>(),
                        new ArrayList<>());
        // No parameterization
        mRepo.setParameterizedModules(false);
        List<String> patterns = new ArrayList<>();
        patterns.add(".*.config");
        patterns.add(".*.xml");
        mAbis = new LinkedHashSet<>();
        mAbis.add(new Abi("arm64-v8a", "64"));
        mAbis.add(new Abi("armeabi-v7a", "32"));
        LinkedHashMap<String, IConfiguration> res =
                mRepo.loadConfigsFromDirectory(
                        Arrays.asList(mTestsDir), mAbis, null, null, patterns);
        assertEquals(1, res.size());
        assertNotNull(res.get("arm64-v8a module1"));
    }

    /**
     * Test that if the base module is excluded in full, the filters of parameterized modules are
     * still populated with the proper filters.
     */
    @Test
    public void testFilterParameterized() throws Exception {
        Map<String, LinkedHashSet<SuiteTestFilter>> excludeFilters = new LinkedHashMap<>();
        createInstantModuleConfig("basemodule");
        SuiteTestFilter fullFilter = SuiteTestFilter.createFrom("armeabi-v7a basemodule");
        LinkedHashSet<SuiteTestFilter> mapFilters = new LinkedHashSet<>();
        mapFilters.add(fullFilter);
        excludeFilters.put("armeabi-v7a basemodule", mapFilters);

        SuiteTestFilter instantMethodFilter =
                SuiteTestFilter.createFrom(
                        "armeabi-v7a basemodule[instant] NativeDnsAsyncTest#Async_Cancel");
        LinkedHashSet<SuiteTestFilter> instantFilter = new LinkedHashSet<>();
        instantFilter.add(instantMethodFilter);
        excludeFilters.put("armeabi-v7a basemodule[instant]", instantFilter);

        mRepo =
                new SuiteModuleLoader(
                        new LinkedHashMap<String, LinkedHashSet<SuiteTestFilter>>(),
                        excludeFilters,
                        new ArrayList<>(),
                        new ArrayList<>());
        mRepo.setParameterizedModules(true);

        List<String> patterns = new ArrayList<>();
        patterns.add(".*.config");
        patterns.add(".*.xml");
        LinkedHashMap<String, IConfiguration> res =
                mRepo.loadConfigsFromDirectory(
                        Arrays.asList(mTestsDir), mAbis, null, null, patterns);
        assertEquals(1, res.size());
        // Full module was excluded completely
        IConfiguration instantModule = res.get("armeabi-v7a basemodule[instant]");
        assertNotNull(instantModule);
        TestSuiteStub stubTest = (TestSuiteStub) instantModule.getTests().get(0);
        assertEquals(1, stubTest.getExcludeFilters().size());
        assertEquals(
                "NativeDnsAsyncTest#Async_Cancel", stubTest.getExcludeFilters().iterator().next());
        // Ensure that appropriate metadata are set on the module config descriptor
        ConfigurationDescriptor descriptor = instantModule.getConfigurationDescription();
        assertEquals(
                1,
                descriptor
                        .getAllMetaData()
                        .get(ConfigurationDescriptor.ACTIVE_PARAMETER_KEY)
                        .size());
        assertEquals(
                "instant",
                descriptor
                        .getAllMetaData()
                        .getUniqueMap()
                        .get(ConfigurationDescriptor.ACTIVE_PARAMETER_KEY));
        assertEquals("armeabi-v7a", descriptor.getAbi().getName());
    }

    @Test
    public void testLoad_foldable() throws Exception {
        Set<DeviceFoldableState> foldableStates = new LinkedHashSet<>();
        foldableStates.add(new DeviceFoldableState(0, "DEFAULT"));
        foldableStates.add(new DeviceFoldableState(1, "CLOSED"));
        foldableStates.add(new DeviceFoldableState(2, "OPEN"));
        Map<String, LinkedHashSet<SuiteTestFilter>> excludeFilters = new LinkedHashMap<>();
        createFoldableModuleConfig("basemodule");
        Set<String> excludeFilterString = new LinkedHashSet<>();
        excludeFilterString.add("armeabi-v7a basemodule");
        excludeFilterString.add(
                "armeabi-v7a basemodule[foldable:1:CLOSED] NativeDnsAsyncTest#Async_Cancel");
        // All foldable configs will get injected
        excludeFilterString.add(
                "armeabi-v7a basemodule[all_foldable_states] NativeDnsAsyncTest#test2");
        SuiteModuleLoader.addFilters(excludeFilterString, excludeFilters, null, foldableStates);

        mRepo =
                new SuiteModuleLoader(
                        new LinkedHashMap<String, LinkedHashSet<SuiteTestFilter>>(),
                        excludeFilters,
                        new ArrayList<>(),
                        new ArrayList<>());
        mRepo.setParameterizedModules(true);
        mRepo.setFoldableStates(foldableStates);

        List<String> patterns = new ArrayList<>();
        patterns.add(".*.config");
        patterns.add(".*.xml");
        LinkedHashMap<String, IConfiguration> res =
                mRepo.loadConfigsFromDirectory(
                        Arrays.asList(mTestsDir), mAbis, null, null, patterns);
        assertEquals(2, res.size());
        // Full module was excluded completely, only foldable are created
        IConfiguration foldable1 = res.get("armeabi-v7a basemodule[foldable:1:CLOSED]");
        assertNotNull(foldable1);
        TestSuiteStub stubTest = (TestSuiteStub) foldable1.getTests().get(0);
        assertEquals(2, stubTest.getExcludeFilters().size());
        Iterator<String> iteFilters = stubTest.getExcludeFilters().iterator();
        assertEquals("NativeDnsAsyncTest#Async_Cancel", iteFilters.next());
        assertEquals("NativeDnsAsyncTest#test2", iteFilters.next());
        // Ensure that appropriate metadata are set on the module config descriptor
        ConfigurationDescriptor descriptor = foldable1.getConfigurationDescription();
        assertEquals(
                1,
                descriptor
                        .getAllMetaData()
                        .get(ConfigurationDescriptor.ACTIVE_PARAMETER_KEY)
                        .size());
        assertEquals(
                "foldable:1:CLOSED",
                descriptor
                        .getAllMetaData()
                        .getUniqueMap()
                        .get(ConfigurationDescriptor.ACTIVE_PARAMETER_KEY));
        assertEquals("armeabi-v7a", descriptor.getAbi().getName());

        IConfiguration foldable2 = res.get("armeabi-v7a basemodule[foldable:2:OPEN]");
        assertNotNull(foldable2);
        TestSuiteStub stubTest2 = (TestSuiteStub) foldable2.getTests().get(0);
        assertEquals(1, stubTest2.getExcludeFilters().size());
        Iterator<String> iteFilters2 = stubTest2.getExcludeFilters().iterator();
        assertEquals("NativeDnsAsyncTest#test2", iteFilters2.next());
        ConfigurationDescriptor descriptor2 = foldable2.getConfigurationDescription();
        assertEquals(
                1,
                descriptor2
                        .getAllMetaData()
                        .get(ConfigurationDescriptor.ACTIVE_PARAMETER_KEY)
                        .size());
        assertEquals(
                "foldable:2:OPEN",
                descriptor2
                        .getAllMetaData()
                        .getUniqueMap()
                        .get(ConfigurationDescriptor.ACTIVE_PARAMETER_KEY));
        assertEquals("armeabi-v7a", descriptor2.getAbi().getName());
    }

    @Test
    public void testLoad_foldable_moduleParam() throws Exception {
        Set<DeviceFoldableState> foldableStates = new LinkedHashSet<>();
        foldableStates.add(new DeviceFoldableState(0, "DEFAULT"));
        foldableStates.add(new DeviceFoldableState(1, "CLOSED"));
        foldableStates.add(new DeviceFoldableState(2, "OPEN"));
        createFoldableModuleConfig("basemodule");

        mRepo =
                new SuiteModuleLoader(
                        new LinkedHashMap<String, LinkedHashSet<SuiteTestFilter>>(),
                        new LinkedHashMap<String, LinkedHashSet<SuiteTestFilter>>(),
                        new ArrayList<>(),
                        new ArrayList<>());
        mRepo.setParameterizedModules(true);
        mRepo.setModuleParameter(ModuleParameters.ALL_FOLDABLE_STATES);
        mRepo.setFoldableStates(foldableStates);

        List<String> patterns = new ArrayList<>();
        patterns.add(".*.config");
        patterns.add(".*.xml");
        LinkedHashMap<String, IConfiguration> res =
                mRepo.loadConfigsFromDirectory(
                        Arrays.asList(mTestsDir), mAbis, null, null, patterns);
        assertEquals(2, res.size());
        // Full module was excluded completely, only foldable are created
        IConfiguration foldable1 = res.get("armeabi-v7a basemodule[foldable:1:CLOSED]");
        assertNotNull(foldable1);

        IConfiguration foldable2 = res.get("armeabi-v7a basemodule[foldable:2:OPEN]");
        assertNotNull(foldable2);
    }

    /**
     * Test that if the base module is excluded in full, the filters of parameterized modules are
     * still populated with the proper filters.
     */
    @Test
    public void testFilterParameterized_excludeFilter_parameter() throws Exception {
        Map<String, LinkedHashSet<SuiteTestFilter>> excludeFilters = new LinkedHashMap<>();
        createInstantModuleConfig("basemodule");
        SuiteTestFilter fullFilter = SuiteTestFilter.createFrom("armeabi-v7a basemodule[instant]");
        LinkedHashSet<SuiteTestFilter> mapFilter = new LinkedHashSet<>();
        mapFilter.add(fullFilter);
        excludeFilters.put("basemodule[instant]", mapFilter);

        mRepo =
                new SuiteModuleLoader(
                        new LinkedHashMap<String, LinkedHashSet<SuiteTestFilter>>(),
                        excludeFilters,
                        new ArrayList<>(),
                        new ArrayList<>());
        mRepo.setParameterizedModules(true);

        List<String> patterns = new ArrayList<>();
        patterns.add(".*.config");
        patterns.add(".*.xml");
        LinkedHashMap<String, IConfiguration> res =
                mRepo.loadConfigsFromDirectory(
                        Arrays.asList(mTestsDir), mAbis, null, null, patterns);
        assertEquals(1, res.size());
        // Full module was excluded completely
        IConfiguration instantModule = res.get("armeabi-v7a basemodule[instant]");
        assertNull(instantModule);
    }

    @Test
    public void testFilterParameterized_includeFilter_base() throws Exception {
        Map<String, LinkedHashSet<SuiteTestFilter>> includeFilters = new LinkedHashMap<>();
        createInstantModuleConfig("basemodule");
        SuiteTestFilter fullFilter = SuiteTestFilter.createFrom("armeabi-v7a basemodule");
        LinkedHashSet<SuiteTestFilter> mapFilter = new LinkedHashSet<>();
        mapFilter.add(fullFilter);
        includeFilters.put("armeabi-v7a basemodule", mapFilter);

        mRepo =
                new SuiteModuleLoader(
                        includeFilters,
                        new LinkedHashMap<String, LinkedHashSet<SuiteTestFilter>>(),
                        new ArrayList<>(),
                        new ArrayList<>());
        mRepo.setParameterizedModules(true);

        List<String> patterns = new ArrayList<>();
        patterns.add(".*.config");
        patterns.add(".*.xml");
        LinkedHashMap<String, IConfiguration> res =
                mRepo.loadConfigsFromDirectory(
                        Arrays.asList(mTestsDir), mAbis, null, null, patterns);
        assertEquals(1, res.size());
        // Parameterized module was excluded completely
        IConfiguration baseModule = res.get("armeabi-v7a basemodule");
        assertNotNull(baseModule);
    }

    @Test
    public void testFilterParameterized_includeFilter_param() throws Exception {
        Map<String, LinkedHashSet<SuiteTestFilter>> includeFilters = new LinkedHashMap<>();
        createInstantModuleConfig("basemodule");
        SuiteTestFilter fullFilter = SuiteTestFilter.createFrom("armeabi-v7a basemodule[instant]");
        LinkedHashSet<SuiteTestFilter> mapFilter = new LinkedHashSet<>();
        mapFilter.add(fullFilter);
        includeFilters.put("armeabi-v7a basemodule[instant]", mapFilter);

        mRepo =
                new SuiteModuleLoader(
                        includeFilters,
                        new LinkedHashMap<String, LinkedHashSet<SuiteTestFilter>>(),
                        new ArrayList<>(),
                        new ArrayList<>());
        mRepo.setParameterizedModules(true);

        List<String> patterns = new ArrayList<>();
        patterns.add(".*.config");
        patterns.add(".*.xml");
        LinkedHashMap<String, IConfiguration> res =
                mRepo.loadConfigsFromDirectory(
                        Arrays.asList(mTestsDir), mAbis, null, null, patterns);
        assertEquals(1, res.size());
        // Full module was excluded completely
        IConfiguration instantModule = res.get("armeabi-v7a basemodule[instant]");
        assertNotNull(instantModule);
    }

    @Test
    public void testFilterParameterized_WithModuleArg() throws Exception {
        List<String> moduleArgs = new ArrayList<>();
        createInstantModuleConfig("basemodule");
        moduleArgs.add("basemodule[instant]:exclude-annotation:test-annotation");

        mRepo =
                new SuiteModuleLoader(
                        new LinkedHashMap<String, LinkedHashSet<SuiteTestFilter>>(),
                        new LinkedHashMap<String, LinkedHashSet<SuiteTestFilter>>(),
                        new ArrayList<>(),
                        moduleArgs);
        mRepo.setParameterizedModules(true);

        List<String> patterns = new ArrayList<>();
        patterns.add(".*.config");
        patterns.add(".*.xml");
        LinkedHashMap<String, IConfiguration> res =
                mRepo.loadConfigsFromDirectory(
                        Arrays.asList(mTestsDir), mAbis, null, null, patterns);
        assertEquals(2, res.size());
        IConfiguration instantModule = res.get("armeabi-v7a basemodule[instant]");
        assertNotNull(instantModule);
        TestSuiteStub stubTest = (TestSuiteStub) instantModule.getTests().get(0);
        assertEquals(2, stubTest.getExcludeAnnotations().size());
        List<String> expected =
                Arrays.asList("android.platform.test.annotations.AppModeFull", "test-annotation");
        assertTrue(stubTest.getExcludeAnnotations().containsAll(expected));
    }

    /** Test that the configuration can be found if specifying specific path. */
    @Test
    public void testLoadConfigsFromSpecifiedPaths_OneModule() throws Exception {
        createModuleConfig("module1");
        File module1 = new File(mTestsDir, "module1" + SuiteModuleLoader.CONFIG_EXT);

        mRepo =
                new SuiteModuleLoader(
                        new LinkedHashMap<String, LinkedHashSet<SuiteTestFilter>>(),
                        new LinkedHashMap<String, LinkedHashSet<SuiteTestFilter>>(),
                        new ArrayList<>(),
                        new ArrayList<>());

        LinkedHashMap<String, IConfiguration> res =
                mRepo.loadConfigsFromSpecifiedPaths(Arrays.asList(module1), mAbis, null);
        assertEquals(1, res.size());
        assertNotNull(res.get("armeabi-v7a module1"));
    }

    /** Test that multiple configurations can be found if specifying specific paths. */
    @Test
    public void testLoadConfigsFromSpecifiedPaths_MultipleModules() throws Exception {
        createModuleConfig("module1");
        File module1 = new File(mTestsDir, "module1" + SuiteModuleLoader.CONFIG_EXT);
        createModuleConfig("module2");
        File module2 = new File(mTestsDir, "module2" + SuiteModuleLoader.CONFIG_EXT);

        mRepo =
                new SuiteModuleLoader(
                        new LinkedHashMap<String, LinkedHashSet<SuiteTestFilter>>(),
                        new LinkedHashMap<String, LinkedHashSet<SuiteTestFilter>>(),
                        new ArrayList<>(),
                        new ArrayList<>());

        LinkedHashMap<String, IConfiguration> res =
                mRepo.loadConfigsFromSpecifiedPaths(Arrays.asList(module1, module2), mAbis, null);
        assertEquals(2, res.size());
        assertNotNull(res.get("armeabi-v7a module1"));
        assertNotNull(res.get("armeabi-v7a module2"));
    }

    /**
     * Test that configuration can be found correctly if specifying specific paths but someone is
     * excluded.
     */
    @Test
    public void testLoadConfigsFromSpecifiedPaths_WithExcludeFilter() throws Exception {
        createModuleConfig("module1");
        File module1 = new File(mTestsDir, "module1" + SuiteModuleLoader.CONFIG_EXT);
        createModuleConfig("module2");
        File module2 = new File(mTestsDir, "module2" + SuiteModuleLoader.CONFIG_EXT);

        Map<String, LinkedHashSet<SuiteTestFilter>> excludeFilters = new LinkedHashMap<>();
        SuiteTestFilter filter = SuiteTestFilter.createFrom("armeabi-v7a module2");
        LinkedHashSet<SuiteTestFilter> mapFilter = new LinkedHashSet<>();
        mapFilter.add(filter);
        excludeFilters.put("armeabi-v7a module2", mapFilter);

        mRepo =
                new SuiteModuleLoader(
                        new LinkedHashMap<String, LinkedHashSet<SuiteTestFilter>>(),
                        excludeFilters,
                        new ArrayList<>(),
                        new ArrayList<>());

        LinkedHashMap<String, IConfiguration> res =
                mRepo.loadConfigsFromSpecifiedPaths(Arrays.asList(module1, module2), mAbis, null);
        assertEquals(1, res.size());
        assertNotNull(res.get("armeabi-v7a module1"));
        assertNull(res.get("armeabi-v7a module2"));
    }

    /** Test deduplicate the given mainline parameters. */
    @Test
    public void testDedupMainlineParameters() throws Exception {
        List<String> parameters = new ArrayList<>();
        parameters.add("mod1.apk");
        parameters.add("mod1.apk");
        parameters.add("mod1.apk+mod2.apk");
        parameters.add("mod1.apk+mod2.apk");
        Set<String> results = mRepo.dedupMainlineParameters(parameters, "configName");
        assertEquals(2, results.size());

        boolean IsEqual = true;
        for (String result : results) {
            if (!(result.equals("mod1.apk") || result.equals("mod1.apk+mod2.apk"))) {
                IsEqual = false;
            }
        }
        assertTrue(IsEqual);
    }

    /** Test deduplicate the given mainline parameters with invalid spaces configured. */
    @Test
    public void testDedupMainlineParameters_WithSpaces() throws Exception {
        List<String> parameters = new ArrayList<>();
        parameters.add("mod1.apk");
        parameters.add(" mod1.apk");
        parameters.add("mod1.apk+mod2.apk ");
        try {
            mRepo.dedupMainlineParameters(parameters, "configName");
            fail("Should have thrown an exception.");
        } catch (ConfigurationException expected) {
            // expected
            assertTrue(expected.getMessage().contains("Illegal mainline module parameter:"));
        }
    }

    /** Test deduplicate the given mainline parameters end with invalid extension. */
    @Test
    public void testDedupMainlineParameters_WithInvalidExtension() throws Exception {
        List<String> parameters = new ArrayList<>();
        parameters.add("mod1.apk");
        parameters.add("mod1.apk+mod2.unknown");
        try {
            mRepo.dedupMainlineParameters(parameters, "configName");
            fail("Should have thrown an exception.");
        } catch (ConfigurationException expected) {
            // expected
            assertTrue(expected.getMessage().contains("Illegal mainline module parameter:"));
        }
    }

    /** Test deduplicate the given mainline parameters end with invalid format. */
    @Test
    public void testDedupMainlineParameters_WithInvalidFormat() throws Exception {
        List<String> parameters = new ArrayList<>();
        parameters.add("mod1.apk");
        parameters.add("+mod2.apex");
        try {
            mRepo.dedupMainlineParameters(parameters, "configName");
            fail("Should have thrown an exception.");
        } catch (ConfigurationException expected) {
            // expected
            assertTrue(expected.getMessage().contains("Illegal mainline module parameter:"));
        }
    }

    /** Test deduplicate the given mainline parameter with duplicated modules configured. */
    @Test
    public void testDedupMainlineParameters_WithDuplicatedMainlineModules() throws Exception {
        List<String> parameters = new ArrayList<>();
        parameters.add("mod1.apk+mod1.apk");
        try {
            mRepo.dedupMainlineParameters(parameters, "configName");
            fail("Should have thrown an exception.");
        } catch (ConfigurationException expected) {
            // expected
            assertTrue(expected.getMessage().contains("Illegal mainline module parameter:"));
        }
    }

    /** Test deduplicate the given mainline parameters are not configured in alphabetical order. */
    @Test
    public void testDedupMainlineParameters_ParameterNotInAlphabeticalOrder() throws Exception {
        List<String> parameters = new ArrayList<>();
        parameters.add("mod1.apk");
        parameters.add("mod2.apex+mod1.apk");
        try {
            mRepo.dedupMainlineParameters(parameters, "configName");
            fail("Should have thrown an exception.");
        } catch (ConfigurationException expected) {
            // expected
            assertTrue(expected.getMessage().contains("Illegal mainline module parameter:"));
        }
    }

    /** Test get the mainline parameters defined in the test config. */
    @Test
    public void testGetMainlineModuleParameters() throws Exception {
        createMainlineModuleConfig("mainline_module");
        IConfiguration config =
                ConfigurationFactory.getInstance()
                        .createConfigurationFromArgs(
                                new String[] {
                                    mTestsDir.getAbsolutePath() + "/mainline_module.config"
                                });

        List<String> results = mRepo.getMainlineModuleParameters(config);
        assertEquals(3, results.size());

        boolean IsEqual = true;
        for (String id : results) {
            if (!(id.equals("mod1.apk")
                    || id.equals("mod2.apk")
                    || id.equals("mod1.apk+mod2.apk"))) {
                IsEqual = false;
            }
        }
        assertTrue(IsEqual);
    }

    /**
     * Test that generate the correct IConfiguration objects based on the defined mainline modules.
     */
    @Test
    public void testLoadParameterizedMainlineModules() throws Exception {
        createMainlineModuleConfig("basemodule");
        mRepo =
                new SuiteModuleLoader(
                        new LinkedHashMap<String, LinkedHashSet<SuiteTestFilter>>(),
                        new LinkedHashMap<String, LinkedHashSet<SuiteTestFilter>>(),
                        new ArrayList<>(),
                        new ArrayList<>());
        mRepo.setInvocationContext(mContext);
        mRepo.setMainlineParameterizedModules(true);

        List<String> patterns = new ArrayList<>();
        patterns.add(".*.config");
        patterns.add(".*.xml");
        LinkedHashMap<String, IConfiguration> res =
                mRepo.loadConfigsFromDirectory(
                        Arrays.asList(mTestsDir), mAbis, null, null, patterns);
        assertEquals(3, res.size());
        IConfiguration module1 = res.get("armeabi-v7a basemodule[mod1.apk]");
        assertNotNull(module1);
        assertTrue(module1.getTargetPreparers().get(0) instanceof InstallApexModuleTargetPreparer);
    }

    /**
     * Test that generate the correct IConfiguration objects based on the defined mainline modules
     * with given exclude-filter.
     */
    @Test
    public void testLoadParameterizedMainlineModule_WithFilters() throws Exception {
        Map<String, LinkedHashSet<SuiteTestFilter>> excludeFilters = new LinkedHashMap<>();
        createMainlineModuleConfig("basemodule");
        SuiteTestFilter fullFilter = SuiteTestFilter.createFrom("armeabi-v7a basemodule");
        LinkedHashSet<SuiteTestFilter> mapFilter = new LinkedHashSet<>();
        mapFilter.add(fullFilter);
        excludeFilters.put("armeabi-v7a basemodule", mapFilter);

        SuiteTestFilter filter =
                SuiteTestFilter.createFrom("armeabi-v7a basemodule[mod1.apk] class#method");
        LinkedHashSet<SuiteTestFilter> mainlineFilter = new LinkedHashSet<>();
        mainlineFilter.add(filter);
        excludeFilters.put("armeabi-v7a basemodule[mod1.apk]", mainlineFilter);

        mRepo =
                new SuiteModuleLoader(
                        new LinkedHashMap<String, LinkedHashSet<SuiteTestFilter>>(),
                        excludeFilters,
                        new ArrayList<>(),
                        new ArrayList<>());
        mRepo.setInvocationContext(mContext);
        mRepo.setMainlineParameterizedModules(true);

        List<String> patterns = new ArrayList<>();
        patterns.add(".*.config");
        patterns.add(".*.xml");
        LinkedHashMap<String, IConfiguration> res =
                mRepo.loadConfigsFromDirectory(
                        Arrays.asList(mTestsDir), mAbis, null, null, patterns);
        assertEquals(3, res.size());
        IConfiguration module1 = res.get("armeabi-v7a basemodule[mod1.apk]");
        assertNotNull(module1);
        TestSuiteStub stubTest = (TestSuiteStub) module1.getTests().get(0);
        assertEquals(1, stubTest.getExcludeFilters().size());
        assertEquals("class#method", stubTest.getExcludeFilters().iterator().next());
    }

    /**
     * Test that generate the correct IConfiguration objects based on the defined mainline modules
     * with given module args.
     */
    @Test
    public void testLoadParameterizedMainlineModule_WithModuleArgs() throws Exception {
        List<String> moduleArgs = new ArrayList<>();
        moduleArgs.add("basemodule[mod1.apk]:exclude-annotation:test-annotation");
        createMainlineModuleConfig("basemodule");

        mRepo =
                new SuiteModuleLoader(
                        new LinkedHashMap<String, LinkedHashSet<SuiteTestFilter>>(),
                        new LinkedHashMap<String, LinkedHashSet<SuiteTestFilter>>(),
                        new ArrayList<>(),
                        moduleArgs);
        mRepo.setInvocationContext(mContext);
        mRepo.setMainlineParameterizedModules(true);

        List<String> patterns = new ArrayList<>();
        patterns.add(".*.config");
        patterns.add(".*.xml");
        LinkedHashMap<String, IConfiguration> res =
                mRepo.loadConfigsFromDirectory(
                        Arrays.asList(mTestsDir), mAbis, null, null, patterns);
        assertEquals(3, res.size());
        IConfiguration module1 = res.get("armeabi-v7a basemodule[mod1.apk]");
        assertNotNull(module1);
        TestSuiteStub stubTest = (TestSuiteStub) module1.getTests().get(0);
        assertEquals(1, stubTest.getExcludeAnnotations().size());
        assertEquals("test-annotation", stubTest.getExcludeAnnotations().iterator().next());
    }

    /**
     * Test that generate the correct IConfiguration objects based on the defined mainline modules
     * with given include-filter and exclude-filter.
     */
    @Test
    public void testLoadParameterizedMainlineModules_WithMultipleFilters() throws Exception {
        Map<String, LinkedHashSet<SuiteTestFilter>> includeFilters = new LinkedHashMap<>();
        Map<String, LinkedHashSet<SuiteTestFilter>> excludeFilters = new LinkedHashMap<>();
        createMainlineModuleConfig("basemodule");
        SuiteTestFilter filter = SuiteTestFilter.createFrom("armeabi-v7a basemodule[mod1.apk]");
        LinkedHashSet<SuiteTestFilter> mapFilter = new LinkedHashSet<>();
        mapFilter.add(filter);
        includeFilters.put("armeabi-v7a basemodule[mod1.apk]", mapFilter);

        filter = SuiteTestFilter.createFrom("armeabi-v7a basemodule[[mod2.apk]]");
        LinkedHashSet<SuiteTestFilter> mapFilter2 = new LinkedHashSet<>();
        mapFilter2.add(filter);
        excludeFilters.put("armeabi-v7a basemodule[mod2.apk]", mapFilter2);

        mRepo =
                new SuiteModuleLoader(
                        includeFilters, excludeFilters, new ArrayList<>(), new ArrayList<>());
        mRepo.setInvocationContext(mContext);
        mRepo.setMainlineParameterizedModules(true);

        List<String> patterns = new ArrayList<>();
        patterns.add(".*.config");
        patterns.add(".*.xml");
        LinkedHashMap<String, IConfiguration> res =
                mRepo.loadConfigsFromDirectory(
                        Arrays.asList(mTestsDir), mAbis, null, null, patterns);
        assertEquals(1, res.size());

        IConfiguration module1 = res.get("armeabi-v7a basemodule[mod1.apk]");
        assertNotNull(module1);

        module1 = res.get("armeabi-v7a basemodule[mod2.apk]");
        assertNull(module1);

        module1 = res.get("armeabi-v7a basemodule[mod1.apk+mod2.apk]");
        assertNull(module1);
    }

    /** Test that the mainline parameter configured in the test config is valid. */
    @Test
    public void testIsValidMainlineParam() throws Exception {
        assertTrue(mRepo.isValidMainlineParam("mod1.apk"));
        assertTrue(mRepo.isValidMainlineParam("mod1.apk+mod2.apex"));
        assertFalse(mRepo.isValidMainlineParam("  mod1.apk"));
        assertFalse(mRepo.isValidMainlineParam("+mod1.apk"));
        assertFalse(mRepo.isValidMainlineParam("mod1.apeks"));
        assertFalse(mRepo.isValidMainlineParam("mod1.apk +mod2.apex"));
        assertFalse(mRepo.isValidMainlineParam("mod1.apk+mod2.apex "));
    }

    /** Test that the mainline parameter configured in the test config is in alphabetical order. */
    @Test
    public void testIsInAlphabeticalOrder() throws Exception {
        assertTrue(mRepo.isInAlphabeticalOrder("mod1.apk"));
        assertTrue(mRepo.isInAlphabeticalOrder("mod1.apk+mod2.apex"));
        assertFalse(mRepo.isInAlphabeticalOrder("mod2.apk+mod1.apex"));
        assertFalse(mRepo.isInAlphabeticalOrder("mod1.apk+mod1.apk"));
        assertTrue(
                mRepo.isInAlphabeticalOrder(
                        "com.android.cellbroadcast.apex+com.android.ipsec.apex+com.android.permission.apex"));
        assertFalse(
                mRepo.isInAlphabeticalOrder(
                        "com.android.permission.apex+com.android.ipsec.apex+com.android.cellbroadcast.apex"));
    }

    /** Test that when no include-filter are given we fallback to default loading. */
    @Test
    public void testLoadConfigsWithNoIncludeFilters() throws Exception {
        createModuleConfig("module1");
        createModuleConfig("module2");

        mRepo =
                new SuiteModuleLoader(
                        new LinkedHashMap<String, LinkedHashSet<SuiteTestFilter>>(),
                        new LinkedHashMap<String, LinkedHashSet<SuiteTestFilter>>(),
                        new ArrayList<>(),
                        new ArrayList<>());

        mRepo.setLoadConfigsWithIncludeFilters(true);
        List<String> patterns = new ArrayList<>();
        patterns.add(".*.config");
        patterns.add(".*.xml");
        LinkedHashMap<String, IConfiguration> res =
                mRepo.loadConfigsFromDirectory(
                        Arrays.asList(mTestsDir), mAbis, null, null, patterns);

        // When no filter exists, fallback to load everything
        assertEquals(2, res.size());
    }

    /** Test that the test config is loaded based on the given include-filter. */
    @Test
    public void testLoadConfigsWithIncludeFilters() throws Exception {
        createModuleConfig("module1");
        createModuleConfig("module2");

        Map<String, LinkedHashSet<SuiteTestFilter>> includeFilters = new LinkedHashMap<>();
        SuiteTestFilter filter = SuiteTestFilter.createFrom("armeabi-v7a module1");
        LinkedHashSet<SuiteTestFilter> mapFilter = new LinkedHashSet<>();
        mapFilter.add(filter);
        includeFilters.put("armeabi-v7a module1", mapFilter);

        mRepo =
            new SuiteModuleLoader(
                includeFilters,
                new LinkedHashMap<String, LinkedHashSet<SuiteTestFilter>>(),
                new ArrayList<>(),
                new ArrayList<>());

        mRepo.setLoadConfigsWithIncludeFilters(false);
        List<String> patterns = new ArrayList<>();
        patterns.add(".*.config");
        patterns.add(".*.xml");
        LinkedHashMap<String, IConfiguration> res =
            mRepo.loadConfigsFromDirectory(
                Arrays.asList(mTestsDir), mAbis, null, null, patterns);

        // Ensure only module1.config is loaded.
        assertEquals(1, res.size());

        IConfiguration module = res.get("armeabi-v7a module1");
        assertNotNull(module);

        module = res.get("armeabi-v7a module2");
        assertNull(module);
    }
}
