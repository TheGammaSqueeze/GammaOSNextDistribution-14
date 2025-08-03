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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.tradefed.build.BuildInfoKey.BuildInfoFileKey;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.ConfigurationDef;
import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.error.HarnessRuntimeException;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.testtype.Abi;
import com.android.tradefed.testtype.HostTest;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.StubTest;
import com.android.tradefed.util.AbiUtils;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.ZipUtil;
import com.android.tradefed.util.testmapping.TestInfo;
import com.android.tradefed.util.testmapping.TestMapping;
import com.android.tradefed.util.testmapping.TestOption;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/** Unit tests for {@link TestMappingSuiteRunner}. */
@RunWith(JUnit4.class)
public class TestMappingSuiteRunnerTest {

    @Rule public TemporaryFolder mTempFolder = new TemporaryFolder();

    private static final String ABI_1 = "arm64-v8a";
    private static final String ABI_2 = "armeabi-v7a";
    private static final String DISABLED_PRESUBMIT_TESTS = "disabled-presubmit-tests";
    private static final String EMPTY_CONFIG = "empty";
    private static final String TEST_CONFIG_NAME = "test";
    private static final String TEST_DATA_DIR = "testdata";
    private static final String TEST_MAPPING = "TEST_MAPPING";
    private static final String TEST_MAPPINGS_ZIP = "test_mappings.zip";

    private TestMappingSuiteRunner mRunner;
    private OptionSetter mOptionSetter;
    private OptionSetter mMainlineOptionSetter;
    private TestMappingSuiteRunner mRunner2;
    private TestMappingSuiteRunner mMainlineRunner;
    @Mock IDeviceBuildInfo mBuildInfo;
    @Mock ITestDevice mMockDevice;
    private TestInformation mTestInfo;
    private IConfiguration mStubMainConfiguration;

    private static final String TEST_MAINLINE_CONFIG =
            "<configuration description=\"Runs a stub tests part of some suite\">\n"
                + "    <option name=\"config-descriptor:metadata\" key=\"mainline-param\""
                + " value=\"mod1.apk\" />    <option name=\"config-descriptor:metadata\""
                + " key=\"mainline-param\" value=\"mod2.apk\" />    <option"
                + " name=\"config-descriptor:metadata\" key=\"mainline-param\""
                + " value=\"mod1.apk+mod2.apk\" />    <option name=\"config-descriptor:metadata\""
                + " key=\"mainline-param\" value=\"mod1.apk+mod2.apk+mod3.apk\" />    <test"
                + " class=\"com.android.tradefed.testtype.HostTest\" />\n"
                + "</configuration>";

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mRunner = new AbiTestMappingSuite();
        mRunner.setBuild(mBuildInfo);
        mRunner.setDevice(mMockDevice);
        mRunner.setSkipjarLoading(false);

        mOptionSetter = new OptionSetter(mRunner);
        mOptionSetter.setOptionValue("suite-config-prefix", "suite");

        mRunner2 = new FakeTestMappingSuiteRunner();
        mRunner2.setBuild(mBuildInfo);
        mRunner2.setDevice(mMockDevice);
        mRunner2.setSkipjarLoading(false);

        mMainlineRunner = new FakeMainlineTMSR();
        mMainlineRunner.setBuild(mBuildInfo);
        mMainlineRunner.setDevice(mMockDevice);
        mStubMainConfiguration = new Configuration("stub", "stub");
        mMainlineRunner.setConfiguration(mStubMainConfiguration);
        mMainlineOptionSetter = new OptionSetter(mMainlineRunner);

        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice(ConfigurationDef.DEFAULT_DEVICE_NAME, mMockDevice);
        context.addDeviceBuildInfo(ConfigurationDef.DEFAULT_DEVICE_NAME, mBuildInfo);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();

        when(mBuildInfo.getFile(BuildInfoFileKey.TARGET_LINKED_DIR)).thenReturn(null);
        when(mBuildInfo.getTestsDir()).thenReturn(mTempFolder.newFolder());
        when(mMockDevice.getProperty(Mockito.any())).thenReturn(ABI_1);
        when(mMockDevice.getProperty(Mockito.any())).thenReturn(ABI_2);
        when(mMockDevice.getIDevice()).thenReturn(mock(IDevice.class));
        when(mMockDevice.getFoldableStates()).thenReturn(new HashSet<>());
    }

    /**
     * Test TestMappingSuiteRunner that hardcodes the abis to avoid failures related to running the
     * tests against a particular abi build of tradefed.
     */
    public static class AbiTestMappingSuite extends TestMappingSuiteRunner {

        @Override
        public Set<IAbi> getAbis(ITestDevice device) throws DeviceNotAvailableException {
            Set<IAbi> abis = new LinkedHashSet<>();
            abis.add(new Abi(ABI_1, AbiUtils.getBitness(ABI_1)));
            abis.add(new Abi(ABI_2, AbiUtils.getBitness(ABI_2)));
            return abis;
        }
    }

    /** Test TestMappingSuiteRunner that create a fake IConfiguration with fake a test object. */
    public static class FakeTestMappingSuiteRunner extends TestMappingSuiteRunner {
        @Override
        public Set<IAbi> getAbis(ITestDevice device) throws DeviceNotAvailableException {
            Set<IAbi> abis = new HashSet<>();
            abis.add(new Abi(ABI_1, AbiUtils.getBitness(ABI_1)));
            abis.add(new Abi(ABI_2, AbiUtils.getBitness(ABI_2)));
            return abis;
        }

        @Override
        public LinkedHashMap<String, IConfiguration> loadingStrategy(
                Set<IAbi> abis, List<File> testsDirs, String suitePrefix, String suiteTag) {
            LinkedHashMap<String, IConfiguration> testConfig = new LinkedHashMap<>();
            try {
                IConfiguration config =
                        ConfigurationFactory.getInstance()
                                .createConfigurationFromArgs(new String[] {EMPTY_CONFIG});
                config.setTest(new StubTest());
                config.getConfigurationDescription().setModuleName(TEST_CONFIG_NAME);
                testConfig.put(TEST_CONFIG_NAME, config);
            } catch (ConfigurationException e) {
                throw new RuntimeException(e);
            }
            return testConfig;
        }
    }

    /** Test TestMappingSuiteRunner that create a fake IConfiguration with fake a test object. */
    public static class FakeMainlineTMSR extends TestMappingSuiteRunner {
        @Override
        public Set<IAbi> getAbis(ITestDevice device) throws DeviceNotAvailableException {
            Set<IAbi> abis = new HashSet<>();
            abis.add(new Abi(ABI_1, AbiUtils.getBitness(ABI_1)));
            return abis;
        }
    }

    /**
     * Test for {@link TestMappingSuiteRunner#loadTests()} that the configuration is created with
     * the remote test timeout information and the hash code of each IRemoteTest object with the
     * corresponding test mapping's path.
     */
    @Test
    public void testLoadTestsWhenRemoteTestTimeoutIsSet() throws Exception {
        File tempDir = null;
        try {
            mOptionSetter.setOptionValue("test-mapping-test-group", "postsubmit");
            mOptionSetter.setOptionValue(
                    RemoteTestTimeOutEnforcer.REMOTE_TEST_TIMEOUT_OPTION, "15m");

            tempDir = FileUtil.createTempDir("test_mapping");

            File srcDir = FileUtil.createTempDir("src", tempDir);
            String srcFile =
                    File.separator + TEST_DATA_DIR + File.separator + DISABLED_PRESUBMIT_TESTS;
            InputStream resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, srcDir, DISABLED_PRESUBMIT_TESTS);

            srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_1";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, srcDir, TEST_MAPPING);
            File subDir = FileUtil.createTempDir("sub_dir", srcDir);
            srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_2";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, subDir, TEST_MAPPING);

            List<File> filesToZip =
                    Arrays.asList(srcDir, new File(tempDir, DISABLED_PRESUBMIT_TESTS));
            File zipFile = Paths.get(tempDir.getAbsolutePath(), TEST_MAPPINGS_ZIP).toFile();
            ZipUtil.createZip(filesToZip, zipFile);

            IDeviceBuildInfo mockBuildInfo = mock(IDeviceBuildInfo.class);
            when(mockBuildInfo.getFile(BuildInfoFileKey.TARGET_LINKED_DIR)).thenReturn(null);
            when(mockBuildInfo.getTestsDir()).thenReturn(mTempFolder.newFolder());
            when(mockBuildInfo.getFile(TEST_MAPPINGS_ZIP)).thenReturn(zipFile);

            mRunner.setBuild(mockBuildInfo);

            LinkedHashMap<String, IConfiguration> configMap = mRunner.loadTests();

            for (IConfiguration config : configMap.values()) {
                ConfigurationDescriptor configDesc = config.getConfigurationDescription();
                assertEquals(
                        configDesc
                                .getMetaData(RemoteTestTimeOutEnforcer.REMOTE_TEST_TIMEOUT_OPTION)
                                .get(0),
                        "PT15M");
                for (IRemoteTest test : config.getTests()) {
                    assertNotNull(configDesc.getMetaData(Integer.toString(test.hashCode())));
                }
            }
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    /**
     * Test for {@link TestMappingSuiteRunner#loadTests()} to fail when both options include-filter
     * and test-mapping-test-group are set.
     */
    @Test(expected = RuntimeException.class)
    public void testLoadTests_conflictTestGroup() throws Exception {
        mOptionSetter.setOptionValue("include-filter", "test1");
        mOptionSetter.setOptionValue("test-mapping-test-group", "group");
        mRunner.loadTests();
    }

    /**
     * Test for {@link TestMappingSuiteRunner#loadTests()} to fail when both options include-filter
     * and test-mapping-path are set.
     */
    @Test(expected = RuntimeException.class)
    public void testLoadTests_conflictOptions() throws Exception {
        mOptionSetter.setOptionValue("include-filter", "test1");
        mOptionSetter.setOptionValue("test-mapping-path", "path1");
        mRunner.loadTests();
    }

    /** Test for {@link TestMappingSuiteRunner#loadTests()} to fail when no test option is set. */
    @Test(expected = RuntimeException.class)
    public void testLoadTests_noOption() throws Exception {
        mRunner.loadTests();
    }

    /**
     * Test for {@link TestMappingSuiteRunner#loadTests()} to fail when option test-mapping-keyword
     * is used but test-mapping-test-group is not set.
     */
    @Test(expected = RuntimeException.class)
    public void testLoadTests_conflictKeyword() throws Exception {
        mOptionSetter.setOptionValue("include-filter", "test1");
        mOptionSetter.setOptionValue("test-mapping-keyword", "key1");
        mRunner.loadTests();
    }

    /**
     * Test for {@link TestMappingSuiteRunner#loadTests()} for loading tests from test_mappings.zip.
     */
    @Test
    public void testLoadTests_testMappingsZip() throws Exception {
        File tempDir = null;
        try {
            mOptionSetter.setOptionValue("test-mapping-test-group", "postsubmit");

            tempDir = FileUtil.createTempDir("test_mapping");

            File srcDir = FileUtil.createTempDir("src", tempDir);
            String srcFile =
                    File.separator + TEST_DATA_DIR + File.separator + DISABLED_PRESUBMIT_TESTS;
            InputStream resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, srcDir, DISABLED_PRESUBMIT_TESTS);

            srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_1";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, srcDir, TEST_MAPPING);
            File subDir = FileUtil.createTempDir("sub_dir", srcDir);
            srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_2";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, subDir, TEST_MAPPING);

            List<File> filesToZip =
                    Arrays.asList(srcDir, new File(tempDir, DISABLED_PRESUBMIT_TESTS));
            File zipFile = Paths.get(tempDir.getAbsolutePath(), TEST_MAPPINGS_ZIP).toFile();
            ZipUtil.createZip(filesToZip, zipFile);

            IDeviceBuildInfo mockBuildInfo = mock(IDeviceBuildInfo.class);
            when(mockBuildInfo.getFile(BuildInfoFileKey.TARGET_LINKED_DIR)).thenReturn(null);
            when(mockBuildInfo.getTestsDir()).thenReturn(mTempFolder.newFolder());
            when(mockBuildInfo.getFile(TEST_MAPPINGS_ZIP)).thenReturn(zipFile);

            mRunner.setBuild(mockBuildInfo);

            LinkedHashMap<String, IConfiguration> configMap = mRunner.loadTests();

            assertEquals(4, configMap.size());
            assertTrue(configMap.containsKey(ABI_1 + " suite/stub1"));
            assertTrue(configMap.containsKey(ABI_1 + " suite/stub2"));
            assertTrue(configMap.containsKey(ABI_2 + " suite/stub1"));
            assertTrue(configMap.containsKey(ABI_2 + " suite/stub2"));

            // Confirm test sources are stored in test's ConfigurationDescription.
            Map<String, Integer> testSouceCount = new HashMap<>();
            testSouceCount.put("suite/stub1", 1);
            testSouceCount.put("suite/stub2", 1);

            for (IConfiguration config : configMap.values()) {
                assertTrue(testSouceCount.containsKey(config.getName()));
                assertEquals(
                        testSouceCount.get(config.getName()).intValue(),
                        config.getConfigurationDescription()
                                .getMetaData(TestMapping.TEST_SOURCES)
                                .size());
            }
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    /**
     * Test for {@link TestMappingSuiteRunner#loadTests()} for loading tests matching keywords
     * setting from test_mappings.zip.
     */
    @Test
    public void testLoadTests_testMappingsZipFoundTestsWithKeywords() throws Exception {
        File tempDir = null;
        try {
            mOptionSetter.setOptionValue("test-mapping-keyword", "key_1");
            mOptionSetter.setOptionValue("test-mapping-test-group", "presubmit");

            tempDir = FileUtil.createTempDir("test_mapping");

            File srcDir = FileUtil.createTempDir("src", tempDir);
            String srcFile =
                    File.separator + TEST_DATA_DIR + File.separator + DISABLED_PRESUBMIT_TESTS;
            InputStream resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, srcDir, DISABLED_PRESUBMIT_TESTS);

            srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_1";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, srcDir, TEST_MAPPING);
            File subDir = FileUtil.createTempDir("sub_dir", srcDir);
            srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_2";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, subDir, TEST_MAPPING);

            List<File> filesToZip =
                    Arrays.asList(srcDir, new File(tempDir, DISABLED_PRESUBMIT_TESTS));
            File zipFile = Paths.get(tempDir.getAbsolutePath(), TEST_MAPPINGS_ZIP).toFile();
            ZipUtil.createZip(filesToZip, zipFile);

            IDeviceBuildInfo mockBuildInfo = mock(IDeviceBuildInfo.class);
            when(mockBuildInfo.getFile(BuildInfoFileKey.TARGET_LINKED_DIR)).thenReturn(null);
            when(mockBuildInfo.getTestsDir()).thenReturn(mTempFolder.newFolder());
            when(mockBuildInfo.getFile(TEST_MAPPINGS_ZIP)).thenReturn(zipFile);

            mRunner.setBuild(mockBuildInfo);

            LinkedHashMap<String, IConfiguration> configMap = mRunner.loadTests();

            // Only suite/stub2 should be listed as it contains key_1 in keywords.
            assertTrue(mRunner.getIncludeFilter().contains("suite/stub2"));

            assertEquals(2, configMap.size());
            assertTrue(configMap.containsKey(ABI_1 + " suite/stub2"));
            assertTrue(configMap.containsKey(ABI_2 + " suite/stub2"));

            // Confirm test sources are stored in test's ConfigurationDescription.
            // Only the test in test_mapping_1 has keywords matched, so there should be only 1 test
            // source for the test.
            Map<String, Integer> testSouceCount = new HashMap<>();
            testSouceCount.put("suite/stub2", 1);

            for (IConfiguration config : configMap.values()) {
                assertTrue(testSouceCount.containsKey(config.getName()));
                assertEquals(
                        testSouceCount.get(config.getName()).intValue(),
                        config.getConfigurationDescription()
                                .getMetaData(TestMapping.TEST_SOURCES)
                                .size());
            }
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    /**
     * Test for {@link TestMappingSuiteRunner#loadTests()} for loading tests matching keywords
     * setting from test_mappings.zip and no test should be found.
     */
    @Test(expected = RuntimeException.class)
    public void testLoadTests_testMappingsZipFailWithKeywords() throws Exception {
        File tempDir = null;
        try {
            mOptionSetter.setOptionValue("test-mapping-keyword", "key_2");
            mOptionSetter.setOptionValue("test-mapping-test-group", "presubmit");

            tempDir = FileUtil.createTempDir("test_mapping");

            File srcDir = FileUtil.createTempDir("src", tempDir);
            String srcFile =
                    File.separator + TEST_DATA_DIR + File.separator + DISABLED_PRESUBMIT_TESTS;
            InputStream resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, srcDir, DISABLED_PRESUBMIT_TESTS);

            srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_1";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, srcDir, TEST_MAPPING);
            File subDir = FileUtil.createTempDir("sub_dir", srcDir);
            srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_2";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, subDir, TEST_MAPPING);

            List<File> filesToZip =
                    Arrays.asList(srcDir, new File(tempDir, DISABLED_PRESUBMIT_TESTS));
            File zipFile = Paths.get(tempDir.getAbsolutePath(), TEST_MAPPINGS_ZIP).toFile();
            ZipUtil.createZip(filesToZip, zipFile);

            IDeviceBuildInfo mockBuildInfo = mock(IDeviceBuildInfo.class);
            when(mockBuildInfo.getFile(BuildInfoFileKey.TARGET_LINKED_DIR)).thenReturn(null);
            when(mockBuildInfo.getTestsDir()).thenReturn(new File("non-existing-dir"));
            when(mockBuildInfo.getFile(TEST_MAPPINGS_ZIP)).thenReturn(zipFile);

            mRunner.setBuild(mockBuildInfo);

            // No test should be found with keyword key_2, loadTests method shall raise
            // RuntimeException.
            LinkedHashMap<String, IConfiguration> configMap = mRunner.loadTests();
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    /**
     * Test for {@link TestMappingSuiteRunner#loadTests()} for loading host tests from
     * test_mappings.zip.
     */
    @Test
    public void testLoadTests_testMappingsZipHostTests() throws Exception {
        File tempDir = null;
        try {
            mOptionSetter.setOptionValue("test-mapping-test-group", "presubmit");

            tempDir = FileUtil.createTempDir("test_mapping");

            File srcDir = FileUtil.createTempDir("src", tempDir);
            String srcFile =
                    File.separator + TEST_DATA_DIR + File.separator + DISABLED_PRESUBMIT_TESTS;
            InputStream resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, tempDir, DISABLED_PRESUBMIT_TESTS);

            srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_1";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, srcDir, TEST_MAPPING);
            File subDir = FileUtil.createTempDir("sub_dir", srcDir);
            srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_2";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, subDir, TEST_MAPPING);

            List<File> filesToZip =
                    Arrays.asList(srcDir, new File(tempDir, DISABLED_PRESUBMIT_TESTS));
            File zipFile = Paths.get(tempDir.getAbsolutePath(), TEST_MAPPINGS_ZIP).toFile();
            ZipUtil.createZip(filesToZip, zipFile);

            IDeviceBuildInfo mockBuildInfo = mock(IDeviceBuildInfo.class);
            when(mockBuildInfo.getFile(BuildInfoFileKey.HOST_LINKED_DIR)).thenReturn(null);
            when(mockBuildInfo.getTestsDir()).thenReturn(mTempFolder.newFolder());
            when(mockBuildInfo.getFile(TEST_MAPPINGS_ZIP)).thenReturn(zipFile);

            mRunner.setBuild(mockBuildInfo);

            mRunner.setPrioritizeHostConfig(true);
            LinkedHashMap<String, IConfiguration> configMap = mRunner.loadTests();

            // Test configs in test_mapping_1 doesn't exist, but should be listed in
            // include-filters.
            assertTrue(mRunner.getIncludeFilter().contains("test1"));
            assertEquals(1, mRunner.getIncludeFilter().size());
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    /**
     * Test for {@link TestMappingSuiteRunner#loadTests()} for loading tests from test_mappings.zip
     * and run with shard.
     */
    @Test
    public void testLoadTests_shard() throws Exception {
        File tempDir = null;
        try {
            mOptionSetter.setOptionValue("test-mapping-test-group", "postsubmit");

            tempDir = FileUtil.createTempDir("test_mapping");

            File srcDir = FileUtil.createTempDir("src", tempDir);
            String srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_1";
            InputStream resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, srcDir, TEST_MAPPING);
            File subDir = FileUtil.createTempDir("sub_dir", srcDir);
            srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_2";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, subDir, TEST_MAPPING);

            File zipFile = Paths.get(tempDir.getAbsolutePath(), TEST_MAPPINGS_ZIP).toFile();
            ZipUtil.createZip(srcDir, zipFile);

            IDeviceBuildInfo mockBuildInfo = mock(IDeviceBuildInfo.class);
            when(mockBuildInfo.getFile(BuildInfoFileKey.TARGET_LINKED_DIR)).thenReturn(null);
            when(mockBuildInfo.getTestsDir()).thenReturn(mTempFolder.newFolder());
            when(mockBuildInfo.getFile(TEST_MAPPINGS_ZIP)).thenReturn(zipFile);
            when(mockBuildInfo.getRemoteFiles()).thenReturn(null);

            mTestInfo
                    .getContext()
                    .addDeviceBuildInfo(ConfigurationDef.DEFAULT_DEVICE_NAME, mockBuildInfo);

            Collection<IRemoteTest> tests = mRunner.split(2, mTestInfo);
            assertEquals(4, tests.size());
            verify(mockBuildInfo, times(1)).getRemoteFiles();
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    /**
     * Test for {@link TestMappingSuiteRunner#loadTests()} for loading tests from test_mappings.zip
     * and run with shard, and no test is split due to exclude-filter.
     */
    @Test
    public void testLoadTests_shardNoTest() throws Exception {
        File tempDir = null;
        try {
            tempDir = FileUtil.createTempDir("test_mapping");

            File srcDir = FileUtil.createTempDir("src", tempDir);
            String srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_1";
            InputStream resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, srcDir, TEST_MAPPING);
            File subDir = FileUtil.createTempDir("sub_dir", srcDir);
            srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_2";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, subDir, TEST_MAPPING);

            File zipFile = Paths.get(tempDir.getAbsolutePath(), TEST_MAPPINGS_ZIP).toFile();
            ZipUtil.createZip(srcDir, zipFile);

            mOptionSetter.setOptionValue("test-mapping-test-group", "postsubmit");
            mOptionSetter.setOptionValue("test-mapping-path", srcDir.getName());
            mOptionSetter.setOptionValue("exclude-filter", "suite/stub1");

            IDeviceBuildInfo mockBuildInfo = mock(IDeviceBuildInfo.class);
            when(mockBuildInfo.getFile(BuildInfoFileKey.TARGET_LINKED_DIR)).thenReturn(null);
            when(mockBuildInfo.getTestsDir()).thenReturn(mTempFolder.newFolder());
            when(mockBuildInfo.getFile(TEST_MAPPINGS_ZIP)).thenReturn(zipFile);

            mTestInfo
                    .getContext()
                    .addDeviceBuildInfo(ConfigurationDef.DEFAULT_DEVICE_NAME, mockBuildInfo);

            Collection<IRemoteTest> tests = mRunner.split(2, mTestInfo);
            assertEquals(null, tests);
            assertEquals(2, mRunner.getIncludeFilter().size());
            assertEquals(null, mRunner.getTestGroup());
            assertEquals(0, mRunner.getTestMappingPaths().size());
            assertEquals(false, mRunner.getUseTestMappingPath());
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    /** Test for {@link TestMappingSuiteRunner#loadTests()} to fail when no test is found. */
    @Test(expected = RuntimeException.class)
    public void testLoadTests_noTest() throws Exception {
        File tempDir = null;
        try {
            mOptionSetter.setOptionValue("test-mapping-test-group", "none-exist");

            tempDir = FileUtil.createTempDir("test_mapping");

            File srcDir = FileUtil.createTempDir("src", tempDir);

            File zipFile = Paths.get(tempDir.getAbsolutePath(), TEST_MAPPINGS_ZIP).toFile();
            ZipUtil.createZip(srcDir, zipFile);

            IDeviceBuildInfo mockBuildInfo = mock(IDeviceBuildInfo.class);
            when(mockBuildInfo.getTestsDir()).thenReturn(mTempFolder.newFolder());
            when(mockBuildInfo.getFile(TEST_MAPPINGS_ZIP)).thenReturn(zipFile);

            mRunner.setBuild(mockBuildInfo);

            mRunner.loadTests();
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    /**
     * Test for {@link TestMappingSuiteRunner#loadTests()} that when a test config supports
     * IAbiReceiver, multiple instances of the config are queued up.
     */
    @Test
    public void testLoadTestsForMultiAbi() throws Exception {
        mOptionSetter.setOptionValue("include-filter", "suite/stubAbi");

        LinkedHashMap<String, IConfiguration> configMap = mRunner.loadTests();
        assertEquals(2, configMap.size());
        assertTrue(configMap.containsKey(ABI_1 + " suite/stubAbi"));
        assertTrue(configMap.containsKey(ABI_2 + " suite/stubAbi"));
    }

    /**
     * Test for {@link TestMappingSuiteRunner#loadTests()} that when force-test-mapping-module is
     * specified, tests would be filtered.
     */
    @Test
    public void testLoadTestsWithModule() throws Exception {
        File tempDir = null;
        try {
            mOptionSetter.setOptionValue("test-mapping-test-group", "postsubmit");
            mOptionSetter.setOptionValue("force-test-mapping-module", "suite/stub1");

            tempDir = FileUtil.createTempDir("test_mapping");

            File srcDir = FileUtil.createTempDir("src", tempDir);
            String srcFile =
                    File.separator + TEST_DATA_DIR + File.separator + DISABLED_PRESUBMIT_TESTS;
            InputStream resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, srcDir, DISABLED_PRESUBMIT_TESTS);

            srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_1";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, srcDir, TEST_MAPPING);
            File subDir = FileUtil.createTempDir("sub_dir", srcDir);
            srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_2";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, subDir, TEST_MAPPING);

            List<File> filesToZip =
                    Arrays.asList(srcDir, new File(tempDir, DISABLED_PRESUBMIT_TESTS));
            File zipFile = Paths.get(tempDir.getAbsolutePath(), TEST_MAPPINGS_ZIP).toFile();
            ZipUtil.createZip(filesToZip, zipFile);

            IDeviceBuildInfo mockBuildInfo = mock(IDeviceBuildInfo.class);
            when(mockBuildInfo.getFile(BuildInfoFileKey.TARGET_LINKED_DIR)).thenReturn(null);
            when(mockBuildInfo.getTestsDir()).thenReturn(mTempFolder.newFolder());
            when(mockBuildInfo.getFile(TEST_MAPPINGS_ZIP)).thenReturn(zipFile);

            mRunner.setBuild(mockBuildInfo);

            LinkedHashMap<String, IConfiguration> configMap = mRunner.loadTests();
            assertEquals(2, configMap.size());
            assertTrue(configMap.containsKey(ABI_1 + " suite/stub1"));
            assertTrue(configMap.containsKey(ABI_2 + " suite/stub1"));
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    /**
     * Test for {@link TestMappingSuiteRunner#loadTests()} that when multi force-test-mapping-module
     * are specified, tests would be filtered.
     */
    @Test
    public void testLoadTestsWithMultiModules() throws Exception {
        File tempDir = null;
        try {
            mOptionSetter.setOptionValue("test-mapping-test-group", "postsubmit");
            mOptionSetter.setOptionValue("force-test-mapping-module", "suite/stub1");
            mOptionSetter.setOptionValue("force-test-mapping-module", "suite/stub2");

            tempDir = FileUtil.createTempDir("test_mapping");

            File srcDir = FileUtil.createTempDir("src", tempDir);
            String srcFile =
                    File.separator + TEST_DATA_DIR + File.separator + DISABLED_PRESUBMIT_TESTS;
            InputStream resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, srcDir, DISABLED_PRESUBMIT_TESTS);

            srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_1";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, srcDir, TEST_MAPPING);
            File subDir = FileUtil.createTempDir("sub_dir", srcDir);
            srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_2";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, subDir, TEST_MAPPING);

            List<File> filesToZip =
                    Arrays.asList(srcDir, new File(tempDir, DISABLED_PRESUBMIT_TESTS));
            File zipFile = Paths.get(tempDir.getAbsolutePath(), TEST_MAPPINGS_ZIP).toFile();
            ZipUtil.createZip(filesToZip, zipFile);

            IDeviceBuildInfo mockBuildInfo = mock(IDeviceBuildInfo.class);
            when(mockBuildInfo.getFile(BuildInfoFileKey.TARGET_LINKED_DIR)).thenReturn(null);
            when(mockBuildInfo.getTestsDir()).thenReturn(mTempFolder.newFolder());
            when(mockBuildInfo.getFile(TEST_MAPPINGS_ZIP)).thenReturn(zipFile);

            mRunner.setBuild(mockBuildInfo);

            LinkedHashMap<String, IConfiguration> configMap = mRunner.loadTests();
            assertEquals(4, configMap.size());
            assertTrue(configMap.containsKey(ABI_1 + " suite/stub1"));
            assertTrue(configMap.containsKey(ABI_1 + " suite/stub2"));
            assertTrue(configMap.containsKey(ABI_2 + " suite/stub1"));
            assertTrue(configMap.containsKey(ABI_2 + " suite/stub2"));
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    /**
     * Test for {@link TestMappingSuiteRunner#getTestInfos(Set, String)} that when a module is
     * specified, tests would be still found correctly.
     */
    @Test
    public void testGetTestInfos() throws Exception {
        Set<TestInfo> testInfos = new HashSet<>();
        testInfos.add(createTestInfo("test", "path"));
        testInfos.add(createTestInfo("test", "path2"));
        testInfos.add(createTestInfo("test2", "path2"));

        assertEquals(2, mRunner.getTestInfos(testInfos, "test").size());
        assertEquals(1, mRunner.getTestInfos(testInfos, "test2").size());
    }

    /**
     * Test for {@link TestMappingSuiteRunner#dedupTestInfos(File, Set)} that tests with the same
     * test options would be filtered out.
     */
    @Test
    public void testDedupTestInfos() throws Exception {
        Set<TestInfo> testInfos = new HashSet<>();
        testInfos.add(createTestInfo("test", "path"));
        testInfos.add(createTestInfo("test", "path2"));
        assertEquals(1, mRunner.dedupTestInfos(new File("anything"), testInfos).size());

        TestInfo anotherInfo = new TestInfo("test", "folder3", false);
        anotherInfo.addOption(new TestOption("include-filter", "value1"));
        testInfos.add(anotherInfo);
        assertEquals(2, mRunner.dedupTestInfos(new File("anything"), testInfos).size());

        // Aggregate the test-mapping sources with the same test options.
        TestInfo anotherInfo2 = new TestInfo("test", "folder4", false);
        anotherInfo2.addOption(new TestOption("include-filter", "value1"));
        TestInfo anotherInfo3 = new TestInfo("test", "folder5", false);
        anotherInfo3.addOption(new TestOption("include-filter", "value1"));
        testInfos.clear();
        testInfos = new HashSet<>(Arrays.asList(anotherInfo, anotherInfo2, anotherInfo3));
        Set<TestInfo> dedupTestInfos = mRunner.dedupTestInfos(new File("anything"), testInfos);
        assertEquals(1, dedupTestInfos.size());
        TestInfo dedupTestInfo = dedupTestInfos.iterator().next();
        Set<String> expected_sources =
                new HashSet<>(Arrays.asList("folder3", "folder4", "folder5"));
        assertEquals(expected_sources, dedupTestInfo.getSources());
    }

    /**
     * Test for {@link TestMappingSuiteRunner#getTestSources(Set)} that test sources would be found
     * correctly.
     */
    @Test
    public void testGetTestSources() throws Exception {
        Set<TestInfo> testInfos = new HashSet<>();
        testInfos.add(createTestInfo("test", "path"));
        testInfos.add(createTestInfo("test", "path2"));
        List<String> results = mRunner.getTestSources(testInfos);
        assertEquals(2, results.size());
    }

    /**
     * Test for {@link TestMappingSuiteRunner#parseOptions(TestInfo)} that the test options are
     * injected correctly.
     */
    @Test
    public void testParseOptions() throws Exception {
        TestInfo info = createTestInfo("test", "path");
        mRunner.parseOptions(info);
        assertEquals(1, mRunner.getIncludeFilter().size());
        assertEquals(1, mRunner.getExcludeFilter().size());
    }

    /**
     * Test for {@link TestMappingSuiteRunner#createIndividualTests(Set, IConfiguration, IAbi)} that
     * IRemoteTest object are created according to the test infos with different test options.
     */
    @Test
    public void testCreateIndividualTestsWithDifferentTestInfos() throws Exception {
        IConfiguration config =
                ConfigurationFactory.getInstance()
                        .createConfigurationFromArgs(new String[] {EMPTY_CONFIG});
        config.setTest(new StubTest());
        config.getConfigurationDescription().setModuleName(TEST_CONFIG_NAME);
        Set<TestInfo> testInfos = new HashSet<>();
        testInfos.add(createTestInfo("test", "path"));
        testInfos.add(createTestInfo("test2", "path"));
        assertEquals(2, mRunner2.createIndividualTests(testInfos, config, null).size());
        assertEquals(1, mRunner2.getIncludeFilter().size());
        assertEquals(1, mRunner2.getExcludeFilter().size());
    }

    /**
     * Test for {@link TestMappingSuiteRunner#createIndividualTests(Set, IConfiguration, IAbi)} that
     * IRemoteTest object are created according to the test infos with multiple test options.
     */
    @Test
    public void testCreateIndividualTestsWithDifferentTestOptions() throws Exception {
        IConfiguration config =
                ConfigurationFactory.getInstance()
                        .createConfigurationFromArgs(new String[] {EMPTY_CONFIG});
        config.setTest(new StubTest());
        config.getConfigurationDescription().setModuleName(TEST_CONFIG_NAME);
        Set<TestInfo> testInfos = new HashSet<>();
        testInfos.add(createTestInfo("test", "path"));
        TestInfo info = new TestInfo("test", "path", false);
        info.addOption(new TestOption("include-filter", "include-filter"));
        testInfos.add(info);
        assertEquals(2, mRunner2.createIndividualTests(testInfos, config, null).size());
        assertEquals(1, mRunner2.getIncludeFilter().size());
        assertEquals(0, mRunner2.getExcludeFilter().size());
    }

    /**
     * Test for {@link TestMappingSuiteRunner#createIndividualTests(Set, IConfiguration, IAbi)} that
     * IRemoteTest object are created according to the test infos with multiple test options and top
     * level exclude-filter tests.
     */
    @Test
    public void testCreateIndividualTestsWithExcludeFilterFromTFCommandLine() throws Exception {
        IConfiguration config =
                ConfigurationFactory.getInstance()
                        .createConfigurationFromArgs(new String[] {EMPTY_CONFIG});
        config.setTest(new StubTest());
        // Inject top level exclude-filter test options into runner
        mRunner2.setExcludeFilter(new HashSet<>(Arrays.asList("some-exclude-filter")));
        config.getConfigurationDescription().setModuleName(TEST_CONFIG_NAME);
        Set<TestInfo> testInfos = new HashSet<>();
        testInfos.add(createTestInfo("test", "path"));
        TestInfo info = new TestInfo("test", "path", false);
        info.addOption(new TestOption("include-filter", "include-filter"));
        testInfos.add(info);
        assertEquals(2, mRunner2.createIndividualTests(testInfos, config, null).size());
        assertEquals(1, mRunner2.getIncludeFilter().size());
        // Ensure exclude filter are kept
        assertEquals(1, mRunner2.getExcludeFilter().size());
    }

    @Test
    public void testLoadTests_moduleDifferentoptions() throws Exception {
        File tempDir = null;
        try {
            mOptionSetter.setOptionValue("test-mapping-test-group", "presubmit");

            tempDir = FileUtil.createTempDir("test_mapping");

            File srcDir = FileUtil.createTempDir("src", tempDir);
            String srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_1";
            InputStream resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, srcDir, TEST_MAPPING);
            File subDir = FileUtil.createTempDir("sub_dir", srcDir);
            srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_2";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, subDir, TEST_MAPPING);

            File zipFile = Paths.get(tempDir.getAbsolutePath(), TEST_MAPPINGS_ZIP).toFile();
            ZipUtil.createZip(srcDir, zipFile);

            IDeviceBuildInfo mockBuildInfo = mock(IDeviceBuildInfo.class);
            when(mockBuildInfo.getFile(BuildInfoFileKey.TARGET_LINKED_DIR)).thenReturn(null);
            when(mockBuildInfo.getTestsDir()).thenReturn(mTempFolder.newFolder());
            when(mockBuildInfo.getFile(TEST_MAPPINGS_ZIP)).thenReturn(zipFile);
            when(mockBuildInfo.getBuildBranch()).thenReturn("branch");
            when(mockBuildInfo.getBuildFlavor()).thenReturn("flavor");
            when(mockBuildInfo.getBuildId()).thenReturn("id");

            IInvocationContext mContext = new InvocationContext();
            mContext.addDeviceBuildInfo(ConfigurationDef.DEFAULT_DEVICE_NAME, mockBuildInfo);
            mRunner.setInvocationContext(mContext);
            mRunner.setBuild(mockBuildInfo);

            LinkedHashMap<String, IConfiguration> configMap = mRunner.loadTests();
            assertEquals(2, configMap.size());
            assertTrue(configMap.keySet().contains("armeabi-v7a suite/stub1"));
            assertTrue(configMap.keySet().contains("arm64-v8a suite/stub1"));

            for (Entry<String, IConfiguration> config : configMap.entrySet()) {
                IConfiguration currentConfig = config.getValue();
                IAbi abi = currentConfig.getConfigurationDescription().getAbi();
                // Ensure that all the subtests abi match the module abi
                for (IRemoteTest test : currentConfig.getTests()) {
                    if (test instanceof IAbiReceiver) {
                        assertEquals(abi, ((IAbiReceiver) test).getAbi());
                    }
                }
            }
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    /**
     * Test for {@link TestMappingSuiteRunner#loadTests()} that IRemoteTest object are created
     * according to the test infos with multiple test options.
     */
    @Test
    public void testLoadTestsForMainline() throws Exception {
        File tempDir = null;
        File tempTestsDir = null;
        try {
            tempDir = FileUtil.createTempDir("test_mapping");
            tempTestsDir = FileUtil.createTempDir("test_mapping_testcases");

            File zipFile = createMainlineTestMappingZip(tempDir);
            createMainlineModuleConfig(tempTestsDir.getAbsolutePath());

            IDeviceBuildInfo mockBuildInfo = mock(IDeviceBuildInfo.class);
            when(mockBuildInfo.getFile(BuildInfoFileKey.TARGET_LINKED_DIR)).thenReturn(null);
            when(mockBuildInfo.getTestsDir()).thenReturn(tempTestsDir);
            when(mockBuildInfo.getFile(TEST_MAPPINGS_ZIP)).thenReturn(zipFile);
            when(mockBuildInfo.getBuildBranch()).thenReturn("branch");
            when(mockBuildInfo.getBuildFlavor()).thenReturn("flavor");
            when(mockBuildInfo.getBuildId()).thenReturn("id");

            IInvocationContext mContext = new InvocationContext();
            mContext.addDeviceBuildInfo(ConfigurationDef.DEFAULT_DEVICE_NAME, mockBuildInfo);
            mMainlineRunner.setInvocationContext(mContext);
            mMainlineRunner.setBuild(mockBuildInfo);

            mMainlineOptionSetter.setOptionValue("enable-mainline-parameterized-modules", "true");
            mMainlineOptionSetter.setOptionValue("skip-loading-config-jar", "true");
            mMainlineOptionSetter.setOptionValue("test-mapping-test-group", "mainline-presubmit");
            LinkedHashMap<String, IConfiguration> configMap = mMainlineRunner.loadTests();

            assertEquals(3, configMap.size());
            assertTrue(configMap.containsKey(ABI_1 + " test[mod1.apk]"));
            assertTrue(configMap.containsKey(ABI_1 + " test[mod2.apk]"));
            assertTrue(configMap.containsKey(ABI_1 + " test[mod1.apk+mod2.apk]"));
            HostTest test = (HostTest) configMap.get(ABI_1 + " test[mod1.apk]").getTests().get(0);
            assertTrue(test.getIncludeFilters().contains("test-filter"));

            test = (HostTest) configMap.get(ABI_1 + " test[mod2.apk]").getTests().get(0);
            assertTrue(test.getIncludeFilters().contains("test-filter2"));

            test = (HostTest) configMap.get(ABI_1 + " test[mod1.apk+mod2.apk]").getTests().get(0);
            assertTrue(test.getIncludeFilters().isEmpty());
            assertEquals(1, test.getExcludeAnnotations().size());
            assertEquals("test-annotation", test.getExcludeAnnotations().iterator().next());
        } finally {
            FileUtil.recursiveDelete(tempDir);
            FileUtil.recursiveDelete(tempTestsDir);
        }
    }

    /**
     * Test for {@link TestMappingSuiteRunner#createIndividualTests(Set, IConfiguration, IAbi)} that
     * IRemoteTest object are created according to the test infos with the same test options and
     * name.
     */
    @Test
    public void testCreateIndividualTestsWithSameTestInfos() throws Exception {
        IConfiguration config =
                ConfigurationFactory.getInstance()
                        .createConfigurationFromArgs(new String[] {EMPTY_CONFIG});
        Set<TestInfo> testInfos = new HashSet<>();
        testInfos.add(createTestInfo("test", "path"));
        testInfos.add(createTestInfo("test", "path"));
        assertEquals(1, mRunner2.createIndividualTests(testInfos, config, null).size());
        assertEquals(1, mRunner2.getIncludeFilter().size());
        assertEquals(1, mRunner2.getExcludeFilter().size());
    }

    /**
     * Test for {@link TestMappingSuiteRunner#loadTests()} for loading tests from test_mappings.zip
     * with ignore-test-mapping-imports flag.
     */
    @Test
    public void testLoadTests_testMappingsIncludeImports() throws Exception {
        File tempDir = null;
        try {
            mOptionSetter.setOptionValue("ignore-test-mapping-imports", "false");
            mOptionSetter.setOptionValue("test-mapping-test-group", "presubmit");
            mOptionSetter.setOptionValue("test-mapping-path", "path2/path3");

            tempDir = FileUtil.createTempDir("test_mapping");
            File path1 = new File(tempDir.getAbsolutePath() + "/path1");
            path1.mkdir();
            String srcFile =
                    File.separator + TEST_DATA_DIR + File.separator + "test_mapping_with_import_1";
            InputStream resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, path1, TEST_MAPPING);

            File path2 = new File(tempDir.getAbsolutePath() + "/path2");
            path2.mkdir();
            srcFile =
                    File.separator + TEST_DATA_DIR + File.separator + "test_mapping_with_import_2";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, path2, TEST_MAPPING);

            File path3 = new File(path2.getAbsolutePath() + "/path3");
            path3.mkdir();
            srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_1";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, path3, TEST_MAPPING);

            srcFile = File.separator + TEST_DATA_DIR + File.separator + DISABLED_PRESUBMIT_TESTS;
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, tempDir, DISABLED_PRESUBMIT_TESTS);
            List<File> filesToZip =
                    Arrays.asList(path1, path2, new File(tempDir, DISABLED_PRESUBMIT_TESTS));

            File zipFile = Paths.get(tempDir.getAbsolutePath(), TEST_MAPPINGS_ZIP).toFile();
            ZipUtil.createZip(filesToZip, zipFile);

            IDeviceBuildInfo mockBuildInfo = mock(IDeviceBuildInfo.class);
            when(mockBuildInfo.getFile(BuildInfoFileKey.TARGET_LINKED_DIR)).thenReturn(null);
            when(mockBuildInfo.getTestsDir()).thenReturn(mTempFolder.newFolder());
            when(mockBuildInfo.getFile(TEST_MAPPINGS_ZIP)).thenReturn(zipFile);
            mRunner.setBuild(mockBuildInfo);
            mRunner.setPrioritizeHostConfig(true);

            LinkedHashMap<String, IConfiguration> configMap = mRunner.loadTests();
            assertEquals(3, mRunner.getIncludeFilter().size());
            assertTrue(mRunner.getIncludeFilter().contains("import-test1"));
            assertTrue(mRunner.getIncludeFilter().contains("import-test2"));
            assertTrue(mRunner.getIncludeFilter().contains("test1"));
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    /**
     * Test for {@link TestMappingSuiteRunner#filterByAllowedTestLists(Set)} for filtering tests
     * from a list of allowed test lists.
     */
    @Test
    public void testFilterByAllowedTestLists() throws Exception {
        File tempDir = null;
        try {
            mOptionSetter.setOptionValue("test-mapping-allowed-tests-list", "test-list.zip");

            tempDir = FileUtil.createTempDir("test_lists");
            File listFile = Paths.get(tempDir.getAbsolutePath(), "test-list").toFile();
            FileUtil.writeToFile("test1.config\n", listFile);
            FileUtil.writeToFile("dir/test2.config", listFile, true);

            List<File> filesToZip = Arrays.asList(listFile);
            File zipFile = Paths.get(tempDir.getAbsolutePath(), "test-list.zip").toFile();
            ZipUtil.createZip(filesToZip, zipFile);

            IDeviceBuildInfo mockBuildInfo = mock(IDeviceBuildInfo.class);
            when(mockBuildInfo.getFile("test-list.zip")).thenReturn(zipFile);
            mRunner.setBuild(mockBuildInfo);

            Set<TestInfo> testInfos = new HashSet<>();
            TestInfo test1 = createTestInfo("test1", "path");
            testInfos.add(test1);
            TestInfo test2 = createTestInfo("test2", "path");
            testInfos.add(test2);
            testInfos.add(createTestInfo("test3", "path"));

            testInfos = mRunner.filterByAllowedTestLists(mockBuildInfo, testInfos);
            assertEquals(2, testInfos.size());
            assertTrue(testInfos.contains(test1));
            assertTrue(testInfos.contains(test2));
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    @Test
    public void testLoadTests_WithCollisionAdditionalTestMappingZip() throws Exception {
        File tempDir = null;
        try {
            mOptionSetter.setOptionValue("test-mapping-test-group", "presubmit");
            mOptionSetter.setOptionValue("additional-test-mapping-zip", "extra-zip");

            tempDir = FileUtil.createTempDir("test_mapping");
            File zipFile = createTestMappingZip(tempDir);

            IDeviceBuildInfo mockBuildInfo = mock(IDeviceBuildInfo.class);
            when(mockBuildInfo.getFile(BuildInfoFileKey.TARGET_LINKED_DIR)).thenReturn(null);
            when(mockBuildInfo.getTestsDir()).thenReturn(mTempFolder.newFolder());
            when(mockBuildInfo.getFile(TEST_MAPPINGS_ZIP)).thenReturn(zipFile);
            when(mockBuildInfo.getFile("extra-zip")).thenReturn(zipFile);
            mRunner.setBuild(mockBuildInfo);

            mRunner.loadTests();
            fail("Should have thrown an exception.");
        } catch (HarnessRuntimeException expected) {
            // expected
            assertTrue(expected.getMessage().contains("Collision of Test Mapping file"));
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    /**
     * Test for {@link TestMappingSuiteRunner#loadTests()} for loading tests when full run is
     * forced.
     */
    @Test
    public void testLoadTests_ForceFullRun() throws Exception {
        File tempDir = null;
        try {
            mOptionSetter.setOptionValue("test-mapping-test-group", "postsubmit");
            mOptionSetter.setOptionValue("force-full-run", "true");

            tempDir = FileUtil.createTempDir("test_mapping");

            File srcDir = FileUtil.createTempDir("src", tempDir);
            String srcFile =
                    File.separator + TEST_DATA_DIR + File.separator + DISABLED_PRESUBMIT_TESTS;
            InputStream resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, srcDir, DISABLED_PRESUBMIT_TESTS);

            srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_1";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, srcDir, TEST_MAPPING);
            File subDir = FileUtil.createTempDir("sub_dir", srcDir);
            srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_2";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, subDir, TEST_MAPPING);

            List<File> filesToZip =
                    Arrays.asList(srcDir, new File(tempDir, DISABLED_PRESUBMIT_TESTS));
            File zipFile = Paths.get(tempDir.getAbsolutePath(), TEST_MAPPINGS_ZIP).toFile();
            ZipUtil.createZip(filesToZip, zipFile);

            mOptionSetter.setOptionValue("test-mapping-path", srcDir.getName());

            IDeviceBuildInfo mockBuildInfo = mock(IDeviceBuildInfo.class);
            when(mockBuildInfo.getFile(BuildInfoFileKey.TARGET_LINKED_DIR)).thenReturn(null);
            when(mockBuildInfo.getTestsDir()).thenReturn(mTempFolder.newFolder());
            when(mockBuildInfo.getFile(TEST_MAPPINGS_ZIP)).thenReturn(zipFile);
            mRunner.setBuild(mockBuildInfo);
            LinkedHashMap<String, IConfiguration> configMap = mRunner.loadTests();
            assertEquals(4, configMap.size());

        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    @Test
    public void testLoadTests_WithoutCollisionAdditionalTestMappingZip() throws Exception {
        File tempDir = null;
        File tempDir2 = null;
        try {
            mOptionSetter.setOptionValue("test-mapping-test-group", "presubmit");
            mOptionSetter.setOptionValue("additional-test-mapping-zip", "extra-zip");

            tempDir = FileUtil.createTempDir("test_mapping");
            tempDir2 = FileUtil.createTempDir("test_mapping");
            File zipFile = createTestMappingZip(tempDir);
            File zipFile2 = createTestMappingZip(tempDir2);

            IDeviceBuildInfo mockBuildInfo = mock(IDeviceBuildInfo.class);
            when(mockBuildInfo.getFile(BuildInfoFileKey.TARGET_LINKED_DIR)).thenReturn(null);
            when(mockBuildInfo.getTestsDir()).thenReturn(mTempFolder.newFolder());
            when(mockBuildInfo.getFile(TEST_MAPPINGS_ZIP)).thenReturn(zipFile);
            when(mockBuildInfo.getFile("extra-zip")).thenReturn(zipFile2);
            mRunner.setBuild(mockBuildInfo);

            LinkedHashMap<String, IConfiguration> configMap = mRunner.loadTests();
            assertEquals(2, configMap.size());
            verify(mockBuildInfo, times(1)).getFile(TEST_MAPPINGS_ZIP);
            verify(mockBuildInfo, times(1)).getFile("extra-zip");
        } finally {
            FileUtil.recursiveDelete(tempDir);
            FileUtil.recursiveDelete(tempDir2);
        }
    }

    @Test
    public void testLoadTests_WithMissingAdditionalTestMappingZips() throws Exception {
        File tempDir = null;
        try {
            mOptionSetter.setOptionValue("test-mapping-test-group", "presubmit");
            mOptionSetter.setOptionValue("additional-test-mapping-zip", "extra-zip");

            tempDir = FileUtil.createTempDir("test_mapping");
            File zipFile = createTestMappingZip(tempDir);

            IDeviceBuildInfo mockBuildInfo = mock(IDeviceBuildInfo.class);
            when(mockBuildInfo.getFile(BuildInfoFileKey.TARGET_LINKED_DIR)).thenReturn(null);
            when(mockBuildInfo.getTestsDir()).thenReturn(mTempFolder.newFolder());
            when(mockBuildInfo.getFile(TEST_MAPPINGS_ZIP)).thenReturn(zipFile);
            when(mockBuildInfo.getFile("extra-zip")).thenReturn(null);
            mRunner.setBuild(mockBuildInfo);
            mRunner.loadTests();
            fail("Should have thrown an exception.");
        } catch (HarnessRuntimeException expected) {
            // expected
            assertEquals(
                "Missing extra-zip in the BuildInfo file.", expected.getMessage());
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    /**
     * Test for {@link TestMappingSuiteRunner#loadTests()} for loading tests with checking modified
     * files matched file patterns.
     */
    @Test
    public void testLoadTestsWithFilePatternsExample1() throws Exception {
        // Test directory structure:
        // ├── a/TEST_MAPPING (File patterns: *.java)
        // └── b/TEST_MAPPING (No file patterns)
        File tempDir = null;
        try {
            mOptionSetter.setOptionValue("test-mapping-test-group", "presubmit");
            mOptionSetter.setOptionValue("test-mapping-path", "a");
            mOptionSetter.setOptionValue("test-mapping-path", "b");
            mOptionSetter.setOptionValue("test-mapping-matched-pattern-paths", "a/b.java");
            mOptionSetter.setOptionValue(
                    "test-mapping-matched-pattern-paths", "a/BroadcastQueueImpl.java");

            tempDir = FileUtil.createTempDir("test_mapping");
            File subDir_a = new File(tempDir.getAbsolutePath() + File.separator + "a");
            subDir_a.mkdir();
            String srcFile =
                    File.separator
                            + TEST_DATA_DIR
                            + File.separator
                            + "test_mapping_with_file_patterns_java";
            InputStream resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, subDir_a, TEST_MAPPING);
            File subDir_b = new File(tempDir.getAbsolutePath() + File.separator + "b");
            subDir_b.mkdir();
            srcFile =
                    File.separator
                            + TEST_DATA_DIR
                            + File.separator
                            + "test_mapping_with_no_file_patterns";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, subDir_b, TEST_MAPPING);

            List<File> filesToZip = Arrays.asList(subDir_a, subDir_b);
            File zipFile = Paths.get(tempDir.getAbsolutePath(), TEST_MAPPINGS_ZIP).toFile();
            ZipUtil.createZip(filesToZip, zipFile);

            IDeviceBuildInfo mockBuildInfo = mock(IDeviceBuildInfo.class);
            when(mockBuildInfo.getFile(BuildInfoFileKey.TARGET_LINKED_DIR)).thenReturn(null);
            when(mockBuildInfo.getTestsDir()).thenReturn(mTempFolder.newFolder());
            when(mockBuildInfo.getFile(TEST_MAPPINGS_ZIP)).thenReturn(zipFile);
            mRunner.setBuild(mockBuildInfo);

            LinkedHashMap<String, IConfiguration> configMap = mRunner.loadTests();
            assertEquals(3, mRunner.getIncludeFilter().size());
            assertTrue(mRunner.getIncludeFilter().contains("test_java"));
            assertTrue(mRunner.getIncludeFilter().contains("Broadcast_java"));
            assertTrue(mRunner.getIncludeFilter().contains("test_no_pattern"));

        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    /**
     * Test for {@link TestMappingSuiteRunner#loadTests()} for loading tests with checking modified
     * files matched file patterns.
     */
    @Test
    public void testLoadTestsWithFilePatternsExample2() throws Exception {
        // Test directory structure:
        // ├── a/TEST_MAPPING (File patterns: *.java)
        // ├── a/b/TEST_MAPPING (File patterns: *.txt)
        // └── a/c/TEST_MAPPING (No file patterns)
        File tempDir = null;
        try {
            mOptionSetter.setOptionValue("test-mapping-test-group", "presubmit");
            mOptionSetter.setOptionValue("test-mapping-path", "a/b");
            mOptionSetter.setOptionValue("test-mapping-path", "a/c");
            mOptionSetter.setOptionValue("test-mapping-matched-pattern-paths", "a/c.java");
            mOptionSetter.setOptionValue("test-mapping-matched-pattern-paths", "a/b/d.txt");

            tempDir = FileUtil.createTempDir("test_mapping");
            File subDir_a = new File(tempDir.getAbsolutePath() + File.separator + "a");
            subDir_a.mkdir();
            String srcFile =
                    File.separator
                            + TEST_DATA_DIR
                            + File.separator
                            + "test_mapping_with_file_patterns_java";
            InputStream resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, subDir_a, TEST_MAPPING);
            File subDir_b = new File(subDir_a.getAbsolutePath() + File.separator + "b");
            subDir_b.mkdir();
            srcFile =
                    File.separator
                            + TEST_DATA_DIR
                            + File.separator
                            + "test_mapping_with_file_patterns_txt";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, subDir_b, TEST_MAPPING);
            File subDir_c = new File(subDir_a.getAbsolutePath() + File.separator + "c");
            subDir_c.mkdir();
            srcFile =
                    File.separator
                            + TEST_DATA_DIR
                            + File.separator
                            + "test_mapping_with_no_file_patterns";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, subDir_c, TEST_MAPPING);

            List<File> filesToZip = Arrays.asList(subDir_a);
            File zipFile = Paths.get(tempDir.getAbsolutePath(), TEST_MAPPINGS_ZIP).toFile();
            ZipUtil.createZip(filesToZip, zipFile);

            IDeviceBuildInfo mockBuildInfo = mock(IDeviceBuildInfo.class);
            when(mockBuildInfo.getFile(BuildInfoFileKey.TARGET_LINKED_DIR)).thenReturn(null);
            when(mockBuildInfo.getTestsDir()).thenReturn(mTempFolder.newFolder());
            when(mockBuildInfo.getFile(TEST_MAPPINGS_ZIP)).thenReturn(zipFile);
            mRunner.setBuild(mockBuildInfo);

            LinkedHashMap<String, IConfiguration> configMap = mRunner.loadTests();
            assertEquals(3, mRunner.getIncludeFilter().size());
            assertTrue(mRunner.getIncludeFilter().contains("test_java"));
            assertTrue(mRunner.getIncludeFilter().contains("test_txt"));
            assertTrue(mRunner.getIncludeFilter().contains("test_no_pattern"));

        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    /**
     * Test for {@link TestMappingSuiteRunner#loadTests()} for loading tests with checking modified
     * files matched file patterns.
     */
    @Test
    public void testLoadTestsWithFilePatternsExample3() throws Exception {
        // Test directory structure:
        // ├── a/TEST_MAPPING (File patterns: *.java)
        // ├── a/b/TEST_MAPPING (No file patterns)
        // ├── a/b/c/TEST_MAPPING (File patterns: *.txt)
        // └── a/b/c/d/TEST_MAPPING (No file patterns)
        File tempDir = null;
        try {
            mOptionSetter.setOptionValue("test-mapping-test-group", "presubmit");
            mOptionSetter.setOptionValue("test-mapping-path", "a/b/c/d");
            mOptionSetter.setOptionValue("test-mapping-matched-pattern-paths", "a/b/c/d/e.java");
            mOptionSetter.setOptionValue("test-mapping-matched-pattern-paths", "a/b/c/d.txt");

            tempDir = FileUtil.createTempDir("test_mapping");
            File subDir_a = new File(tempDir.getAbsolutePath() + File.separator + "a");
            subDir_a.mkdir();
            String srcFile =
                    File.separator
                            + TEST_DATA_DIR
                            + File.separator
                            + "test_mapping_with_file_patterns_java";
            InputStream resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, subDir_a, TEST_MAPPING);
            File subDir_b = new File(subDir_a.getAbsolutePath() + File.separator + "b");
            subDir_b.mkdir();
            srcFile =
                    File.separator
                            + TEST_DATA_DIR
                            + File.separator
                            + "test_mapping_with_no_file_patterns";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, subDir_b, TEST_MAPPING);
            File subDir_c = new File(subDir_b.getAbsolutePath() + File.separator + "c");
            subDir_c.mkdir();
            srcFile =
                    File.separator
                            + TEST_DATA_DIR
                            + File.separator
                            + "test_mapping_with_file_patterns_txt";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, subDir_c, TEST_MAPPING);
            File subDir_d = new File(subDir_c.getAbsolutePath() + File.separator + "d");
            subDir_d.mkdir();
            srcFile =
                    File.separator
                            + TEST_DATA_DIR
                            + File.separator
                            + "test_mapping_with_no_file_patterns";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, subDir_d, TEST_MAPPING);

            List<File> filesToZip = Arrays.asList(subDir_a);
            File zipFile = Paths.get(tempDir.getAbsolutePath(), TEST_MAPPINGS_ZIP).toFile();
            ZipUtil.createZip(filesToZip, zipFile);

            IDeviceBuildInfo mockBuildInfo = mock(IDeviceBuildInfo.class);
            when(mockBuildInfo.getFile(BuildInfoFileKey.TARGET_LINKED_DIR)).thenReturn(null);
            when(mockBuildInfo.getTestsDir()).thenReturn(mTempFolder.newFolder());
            when(mockBuildInfo.getFile(TEST_MAPPINGS_ZIP)).thenReturn(zipFile);
            mRunner.setBuild(mockBuildInfo);

            LinkedHashMap<String, IConfiguration> configMap = mRunner.loadTests();
            assertEquals(3, mRunner.getIncludeFilter().size());
            assertTrue(mRunner.getIncludeFilter().contains("test_java"));
            assertTrue(mRunner.getIncludeFilter().contains("test_txt"));
            assertTrue(mRunner.getIncludeFilter().contains("test_no_pattern"));

        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    /** Helper to create specific test infos. */
    private TestInfo createTestInfo(String name, String source) {
        TestInfo info = new TestInfo(name, source, false);
        info.addOption(new TestOption("include-filter", name));
        info.addOption(new TestOption("exclude-filter", name));
        info.addOption(new TestOption("other", name));
        return info;
    }

    /** Helper to create test_mappings.zip for Mainline. */
    private File createMainlineTestMappingZip(File tempDir) throws IOException {
        File srcDir = FileUtil.createTempDir("src", tempDir);
        String srcFile = File.separator + TEST_DATA_DIR + File.separator + DISABLED_PRESUBMIT_TESTS;
        InputStream resourceStream = this.getClass().getResourceAsStream(srcFile);
        FileUtil.saveResourceFile(resourceStream, srcDir, DISABLED_PRESUBMIT_TESTS);

        srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_with_mainline";
        resourceStream = this.getClass().getResourceAsStream(srcFile);
        FileUtil.saveResourceFile(resourceStream, srcDir, TEST_MAPPING);
        File subDir = FileUtil.createTempDir("sub_dir", srcDir);
        srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_2";
        resourceStream = this.getClass().getResourceAsStream(srcFile);
        FileUtil.saveResourceFile(resourceStream, subDir, TEST_MAPPING);

        List<File> filesToZip = Arrays.asList(srcDir, new File(tempDir, DISABLED_PRESUBMIT_TESTS));
        File zipFile = Paths.get(tempDir.getAbsolutePath(), TEST_MAPPINGS_ZIP).toFile();
        ZipUtil.createZip(filesToZip, zipFile);

        return zipFile;
    }

    /** Helper to create test_mappings.zip. */
    private File createTestMappingZip(File tempDir) throws IOException {
        File srcDir = FileUtil.createTempDir("src", tempDir);
        String srcFile = File.separator + TEST_DATA_DIR + File.separator + DISABLED_PRESUBMIT_TESTS;
        InputStream resourceStream = this.getClass().getResourceAsStream(srcFile);
        FileUtil.saveResourceFile(resourceStream, srcDir, DISABLED_PRESUBMIT_TESTS);

        srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_1";
        resourceStream = this.getClass().getResourceAsStream(srcFile);
        FileUtil.saveResourceFile(resourceStream, srcDir, TEST_MAPPING);
        File subDir = FileUtil.createTempDir("sub_dir", srcDir);
        srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_2";
        resourceStream = this.getClass().getResourceAsStream(srcFile);
        FileUtil.saveResourceFile(resourceStream, subDir, TEST_MAPPING);

        List<File> filesToZip = Arrays.asList(srcDir, new File(tempDir, DISABLED_PRESUBMIT_TESTS));
        File zipFile = Paths.get(tempDir.getAbsolutePath(), TEST_MAPPINGS_ZIP).toFile();
        ZipUtil.createZip(filesToZip, zipFile);

        return zipFile;
    }

    /** Helper to create module config with parameterized mainline modules. */
    private File createMainlineModuleConfig(String tempTestsDir) throws IOException {
        File moduleConfig = new File(tempTestsDir, "test" + SuiteModuleLoader.CONFIG_EXT);
        FileUtil.writeToFile(TEST_MAINLINE_CONFIG, moduleConfig);
        return moduleConfig;
    }
}
