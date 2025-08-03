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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.targetprep.BaseTargetPreparer;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.suite.BaseTestSuite;
import com.android.tradefed.testtype.suite.ITestSuite.MultiDeviceModuleStrategy;
import com.android.tradefed.testtype.suite.TestMappingSuiteRunner;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.keystore.DryRunKeyStore;

import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Truth;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Unit tests for {@link TestDiscoveryExecutor}. */
@RunWith(JUnit4.class)
public class TestDiscoveryExecutorTest {

    private ConfigurationFactory mMockConfigFactory;
    private Configuration mMockedConfiguration;
    private TestDiscoveryExecutor mTestDiscoveryExecutor;

    @Before
    public void setUp() throws Exception {
        mMockConfigFactory = Mockito.spy((ConfigurationFactory) ConfigurationFactory.getInstance());
        mMockedConfiguration = Mockito.mock(Configuration.class);
        mTestDiscoveryExecutor =
                new TestDiscoveryExecutor() {
                    @Override
                    IConfigurationFactory getConfigurationFactory() {
                        return mMockConfigFactory;
                    }

                    @Override
                    protected String getEnvironment(String var) {
                        return "not-null";
                    }
                };
        doReturn(mMockedConfiguration)
                .when(mMockConfigFactory)
                .createPartialConfigurationFromArgs(
                        Mockito.any(),
                        Mockito.isA(DryRunKeyStore.class),
                        Mockito.argThat(
                                new ArgumentMatcher<Set<String>>() {
                                    @Override
                                    public boolean matches(Set<String> argument) {
                                        return argument.containsAll(
                                                Set.of(
                                                        Configuration.TEST_TYPE_NAME,
                                                        Configuration.TARGET_PREPARER_TYPE_NAME));
                                    }
                                    ;
                                }),
                        Mockito.isNull());
    }

    public static class DiscoverablePreparer extends BaseTargetPreparer
            implements IDiscoverDependencies {
        @Override
        public Set<String> reportDependencies() {
            return ImmutableSet.of("someapk.apk");
        }
    }

    /** Test the executor to discover test modules from multiple tests. */
    @Test
    public void testDiscoverTestDependencies() throws Exception {
        // Mock to return some include filters
        BaseTestSuite test1 = new BaseTestSuite();
        Set<String> includeFilters1 = new HashSet<>();
        includeFilters1.add("TestModule1 class#function1");
        includeFilters1.add("TestModule2");
        includeFilters1.add("x86_64 TestModule3 class#function3");
        test1.setIncludeFilter(includeFilters1);

        BaseTestSuite test2 = new BaseTestSuite();
        Set<String> includeFilters2 = new HashSet<>();
        includeFilters2.add("TestModule1 class#function6");
        includeFilters2.add("x86 TestModule4");
        includeFilters2.add("TestModule5 class#function2");
        includeFilters2.add("TestModule6");
        test2.setIncludeFilter(includeFilters2);

        List<IRemoteTest> testList = new ArrayList<>();
        testList.add(test1);
        testList.add(test2);
        when(mMockedConfiguration.getTests()).thenReturn(testList);
        List<Object> preparers = new ArrayList<>();
        preparers.add(new DiscoverablePreparer());
        when(mMockedConfiguration.getAllConfigurationObjectsOfType(
                        Configuration.TARGET_PREPARER_TYPE_NAME))
                .thenReturn(preparers);

        // We don't test with real command line input here. Because for a real command line input,
        // the test module names will be different with respect to those test config resource files
        // can be changed in different builds.
        String output = mTestDiscoveryExecutor.discoverDependencies(new String[0]);
        JSONObject outputJson = new JSONObject(output);
        JSONArray moduleObject = outputJson.getJSONArray("TestModules");
        List<String> moduleArray = new ArrayList<String>();
        for (int i = 0; i < moduleObject.length(); i++) {
            moduleArray.add(moduleObject.getString(i));
        }
        Truth.assertThat(moduleArray)
                .containsExactly(
                        "TestModule1",
                        "TestModule2",
                        "TestModule3",
                        "TestModule4",
                        "TestModule5",
                        "TestModule6");
        JSONArray depObject = outputJson.getJSONArray("TestDependencies");
        List<String> depArray = new ArrayList<String>();
        for (int i = 0; i < depObject.length(); i++) {
            depArray.add(depObject.getString(i));
        }
        Truth.assertThat(depArray).containsExactly("someapk.apk");
    }

    /** Test the executor to discover parameterized test modules. */
    @Test
    public void testDiscoverTestDependencies_parameterizedModules() throws Exception {
        // Mock to return some include filters
        BaseTestSuite test1 = new BaseTestSuite();
        Set<String> includeFilters1 = new HashSet<>();
        includeFilters1.add("TestModule1[Instant]");
        includeFilters1.add("TestModule2");
        test1.setIncludeFilter(includeFilters1);

        List<IRemoteTest> testList = new ArrayList<>();
        testList.add(test1);
        when(mMockedConfiguration.getTests()).thenReturn(testList);
        List<Object> preparers = new ArrayList<>();
        preparers.add(new DiscoverablePreparer());
        when(mMockedConfiguration.getAllConfigurationObjectsOfType(
                        Configuration.TARGET_PREPARER_TYPE_NAME))
                .thenReturn(preparers);

        String output = mTestDiscoveryExecutor.discoverDependencies(new String[0]);
        JSONObject outputJson = new JSONObject(output);
        JSONArray moduleObject = outputJson.getJSONArray("TestModules");
        List<String> moduleArray = new ArrayList<String>();
        for (int i = 0; i < moduleObject.length(); i++) {
            moduleArray.add(moduleObject.getString(i));
        }
        Truth.assertThat(moduleArray).containsExactly("TestModule1", "TestModule2");
        JSONArray depObject = outputJson.getJSONArray("TestDependencies");
        List<String> depArray = new ArrayList<String>();
        for (int i = 0; i < depObject.length(); i++) {
            depArray.add(depObject.getString(i));
        }
        Truth.assertThat(depArray).containsExactly("someapk.apk");
    }

    /** Test the executor to handle where there is no tests from the config. */
    @Test
    public void testDiscoverDependencies_NoTestModules() throws Exception {
        // Mock to return no include filters
        when(mMockedConfiguration.getTests()).thenReturn(new ArrayList<>());

        try {
            mTestDiscoveryExecutor.discoverDependencies(new String[0]);
            fail("Should throw an TestDiscoveryException");
        } catch (TestDiscoveryException e) {
            assertEquals(DiscoveryExitCode.ERROR, e.exitCode());
        }
    }

    /** Test the executor when a metadata include filter option is in the config. */
    @Test
    public void testDiscoverDependencies_UnsupportedOptions() throws Exception {
        // Mock to return some include filters
        BaseTestSuite test1 = new BaseTestSuite();
        Set<String> emptyFilters = new HashSet<>();

        // Metadata include filter exist
        Map<String, String> map = new HashMap<>();
        map.put("component", "media");
        test1.addModuleMetadataIncludeFilters(new MultiMap<>(map));
        test1.setIncludeFilter(emptyFilters);

        List<IRemoteTest> testList = new ArrayList<>();
        testList.add(test1);
        when(mMockedConfiguration.getTests()).thenReturn(testList);
        List<Object> preparers = new ArrayList<>();
        preparers.add(new DiscoverablePreparer());
        when(mMockedConfiguration.getAllConfigurationObjectsOfType(
                        Configuration.TARGET_PREPARER_TYPE_NAME))
                .thenReturn(preparers);

        try {
            mTestDiscoveryExecutor.discoverDependencies(new String[0]);
            fail("Should throw an TestDiscoveryException");
        } catch (TestDiscoveryException e) {
            assertTrue(
                    e.getMessage()
                            .equals(
                                    "Tradefed Observatory can't do test discovery because the"
                                            + " existence of metadata include filter option."));
            assertEquals(DiscoveryExitCode.COMPONENT_METADATA, e.exitCode());
        }
    }

    /** Test the executor when a metadata include filter option is in the config. */
    @Test
    public void testDiscoverDependencies_fallback() throws Exception {
        File rootDir = FileUtil.createTempDir("discovery-tests");
        try {
            File mediaConfig = new File(rootDir, "CtsMedia.config");
            FileUtil.writeToFile(
                    "<configuration><option name=\"config-descriptor:metadata\" key=\"component\""
                            + " value=\"media\" /></configuration>",
                    mediaConfig);
            File secondNotRunConfig = new File(rootDir, "another.config");
            FileUtil.writeToFile("<configuration></configuration>", secondNotRunConfig);
            mTestDiscoveryExecutor =
                    new TestDiscoveryExecutor() {
                        @Override
                        IConfigurationFactory getConfigurationFactory() {
                            return mMockConfigFactory;
                        }

                        @Override
                        protected String getEnvironment(String var) {
                            return rootDir.getAbsolutePath();
                        }
                    };

            // Mock to return some include filters
            BaseTestSuite test1 = new BaseTestSuite();
            Set<String> emptyFilters = new HashSet<>();

            // Metadata include filter exist
            Map<String, String> map = new HashMap<>();
            map.put("component", "media");
            test1.addModuleMetadataIncludeFilters(new MultiMap<>(map));
            test1.setIncludeFilter(emptyFilters);

            List<IRemoteTest> testList = new ArrayList<>();
            testList.add(test1);
            when(mMockedConfiguration.getTests()).thenReturn(testList);

            String output = mTestDiscoveryExecutor.discoverDependencies(new String[0]);
            JSONObject outputJson = new JSONObject(output);
            JSONArray moduleObject = outputJson.getJSONArray("TestModules");
            List<String> moduleArray = new ArrayList<String>();
            for (int i = 0; i < moduleObject.length(); i++) {
                moduleArray.add(moduleObject.getString(i));
            }
            Truth.assertThat(moduleArray).containsExactly("CtsMedia");
            boolean fallback = outputJson.getBoolean("PartialFallback");
            assertTrue(fallback);
        } finally {
            FileUtil.recursiveDelete(rootDir);
        }
    }

    /** Test the executor when a multi-devices only option is in the config. */
    @Test
    public void testDiscoverDependencies_multiDevices() throws Exception {
        File rootDir = FileUtil.createTempDir("discovery-tests");
        try {
            File multiConfig = new File(rootDir, "CtsMulti.config");
            FileUtil.writeToFile(
                    "<configuration><option name=\"config-descriptor:metadata\" key=\"component\""
                            + " value=\"media\" />"
                            + " <device name=\"one\"/>"
                            + " <device name=\"two\"/>"
                            + "</configuration>",
                    multiConfig);
            File secondNotRunConfig = new File(rootDir, "another.config");
            FileUtil.writeToFile("<configuration></configuration>", secondNotRunConfig);
            mTestDiscoveryExecutor =
                    new TestDiscoveryExecutor() {
                        @Override
                        IConfigurationFactory getConfigurationFactory() {
                            return mMockConfigFactory;
                        }

                        @Override
                        protected String getEnvironment(String var) {
                            return rootDir.getAbsolutePath();
                        }
                    };

            // Mock to return some include filters
            BaseTestSuite test1 = new BaseTestSuite();
            test1.setMultiDeviceStrategy(MultiDeviceModuleStrategy.ONLY_MULTI_DEVICES);
            Set<String> emptyFilters = new HashSet<>();
            test1.setIncludeFilter(emptyFilters);

            List<IRemoteTest> testList = new ArrayList<>();
            testList.add(test1);
            when(mMockedConfiguration.getTests()).thenReturn(testList);

            String output = mTestDiscoveryExecutor.discoverDependencies(new String[0]);
            JSONObject outputJson = new JSONObject(output);
            JSONArray moduleObject = outputJson.getJSONArray("TestModules");
            List<String> moduleArray = new ArrayList<String>();
            for (int i = 0; i < moduleObject.length(); i++) {
                moduleArray.add(moduleObject.getString(i));
            }
            Truth.assertThat(moduleArray).containsExactly("CtsMulti");
            boolean fallback = outputJson.getBoolean("PartialFallback");
            assertTrue(fallback);
        } finally {
            FileUtil.recursiveDelete(rootDir);
        }
    }

    @Test
    public void testDiscoverDependencies_metadataAndIncludeFilters() throws Exception {
        // Mock to return some include filters
        BaseTestSuite test1 = new BaseTestSuite();
        Set<String> includeFilters1 = new HashSet<>();
        includeFilters1.add("TestModule1 class#function1");
        includeFilters1.add("TestModule2");
        includeFilters1.add("x86_64 TestModule3 class#function3");

        // Metadata include filter exist
        Map<String, String> map = new HashMap<>();
        map.put("component", "media");
        test1.addModuleMetadataIncludeFilters(new MultiMap<>(map));
        test1.setIncludeFilter(includeFilters1);

        List<IRemoteTest> testList = new ArrayList<>();
        testList.add(test1);
        when(mMockedConfiguration.getTests()).thenReturn(testList);
        List<Object> preparers = new ArrayList<>();
        preparers.add(new DiscoverablePreparer());
        when(mMockedConfiguration.getAllConfigurationObjectsOfType(
                        Configuration.TARGET_PREPARER_TYPE_NAME))
                .thenReturn(preparers);

        String output = mTestDiscoveryExecutor.discoverDependencies(new String[0]);
        JSONObject outputJson = new JSONObject(output);
        JSONArray moduleObject = outputJson.getJSONArray("TestModules");
        List<String> moduleArray = new ArrayList<String>();
        for (int i = 0; i < moduleObject.length(); i++) {
            moduleArray.add(moduleObject.getString(i));
        }
        Truth.assertThat(moduleArray).containsExactly("TestModule1", "TestModule2", "TestModule3");
        JSONArray depObject = outputJson.getJSONArray("TestDependencies");
        List<String> depArray = new ArrayList<String>();
        for (int i = 0; i < depObject.length(); i++) {
            depArray.add(depObject.getString(i));
        }
        Truth.assertThat(depArray).containsExactly("someapk.apk");
    }

    /** Test the executor to discover test modules from tests. */
    @Test
    public void testDiscoverTestMappingSuiteRunner() throws Exception {
        // Mock to return some include filters
        TestMappingSuiteRunner test1 = Mockito.mock(TestMappingSuiteRunner.class);
        Mockito.doAnswer(
                        new Answer<Object>() {
                            @Override
                            public Object answer(InvocationOnMock mock) throws Throwable {
                                Set<String> includeFilters1 = new HashSet<>();
                                includeFilters1.add("TestModule1 class#function1");
                                includeFilters1.add("TestModule2");
                                return includeFilters1;
                            }
                        })
                .when(test1)
                .getIncludeFilter();

        Mockito.doAnswer(
                        new Answer<Object>() {
                            @Override
                            public Object answer(InvocationOnMock mock) throws Throwable {
                                return new LinkedHashMap<>();
                            }
                        })
                .when(test1)
                .loadTests();

        List<IRemoteTest> testList = new ArrayList<>();
        testList.add(test1);
        when(mMockedConfiguration.getTests()).thenReturn(testList);

        String output = mTestDiscoveryExecutor.discoverDependencies(new String[0]);
        JSONObject outputJson = new JSONObject(output);
        JSONArray moduleObject = outputJson.getJSONArray("TestModules");
        List<String> moduleArray = new ArrayList<String>();
        for (int i = 0; i < moduleObject.length(); i++) {
            moduleArray.add(moduleObject.getString(i));
        }
        Truth.assertThat(moduleArray).containsExactly("TestModule1", "TestModule2");
        JSONArray depObject = outputJson.getJSONArray("TestDependencies");
        List<String> depArray = new ArrayList<String>();
        for (int i = 0; i < depObject.length(); i++) {
            depArray.add(depObject.getString(i));
        }
        Truth.assertThat(depArray).isEmpty();

        // In test discovery, the loadTest() should have been called exactly once
        Mockito.verify(test1, Mockito.times(1)).loadTestInfos();
    }
}
