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
package com.android.tradefed.invoker.shard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.StubBuildProvider;
import com.android.tradefed.command.CommandOptions;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.ConfigurationDef;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.DeviceConfigurationHolder;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.IRescheduler;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IInvocationContextReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IShardableTest;
import com.android.tradefed.testtype.StubTest;
import com.android.tradefed.testtype.suite.ITestSuite;
import com.android.tradefed.testtype.suite.ModuleDefinition;
import com.android.tradefed.util.FileUtil;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Unit tests for {@link StrictShardHelper}. */
@RunWith(JUnit4.class)
public class StrictShardHelperTest {

    private static final String TEST_CONFIG =
            "<configuration description=\"shard config test\">\n"
                    + "    <%s class=\"%s\" />\n"
                    + "</configuration>";

    private StrictShardHelper mHelper;
    private IConfiguration mConfig;
    private ILogSaver mMockLogSaver;
    private TestInformation mTestInfo;
    private IInvocationContext mContext;
    private IRescheduler mRescheduler;

    @Before
    public void setUp() {
        mHelper = new StrictShardHelper();
        mConfig = new Configuration("fake_sharding_config", "desc");
        mContext = new InvocationContext();
        mContext.addAllocatedDevice(
                ConfigurationDef.DEFAULT_DEVICE_NAME, Mockito.mock(ITestDevice.class));
        mContext.addDeviceBuildInfo(ConfigurationDef.DEFAULT_DEVICE_NAME, new BuildInfo());
        mTestInfo = TestInformation.newBuilder().setInvocationContext(mContext).build();
        mRescheduler = Mockito.mock(IRescheduler.class);
        mMockLogSaver = Mockito.mock(ILogSaver.class);
        mConfig.setLogSaver(mMockLogSaver);
    }

    /** Test sharding using Tradefed internal algorithm. */
    @Test
    public void testShardConfig_internal() throws Exception {
        try {
            GlobalConfiguration.createGlobalConfiguration(new String[] {"empty"});
        } catch (IllegalStateException ignore) {
            // Ignore
        }
        File configFile =
                createTmpConfig(Configuration.BUILD_PROVIDER_TYPE_NAME, new StubBuildProvider());
        try {
            DeviceConfigurationHolder holder =
                    new DeviceConfigurationHolder(ConfigurationDef.DEFAULT_DEVICE_NAME);
            holder.addSpecificConfig(new StubBuildProvider());
            mConfig.setDeviceConfig(holder);
            CommandOptions options = new CommandOptions();
            OptionSetter setter = new OptionSetter(options);
            setter.setOptionValue("shard-count", "5");
            mConfig.setCommandOptions(options);
            mConfig.setCommandLine(new String[] {configFile.getAbsolutePath()});
            StubTest test = new StubTest();
            setter = new OptionSetter(test);
            setter.setOptionValue("num-shards", "5");
            mConfig.setTest(test);
            assertEquals(1, mConfig.getTests().size());
            assertTrue(mHelper.shardConfig(mConfig, mTestInfo, mRescheduler, null));
            // Ensure that we did split 1 tests per shard rescheduled.
            Mockito.verify(mRescheduler, Mockito.times(5))
                    .scheduleConfig(
                            Mockito.argThat(
                                    new ArgumentMatcher<IConfiguration>() {
                                        @Override
                                        public boolean matches(IConfiguration argument) {
                                            assertEquals(1, argument.getTests().size());
                                            return true;
                                        }
                                    }));
        } finally {
            FileUtil.deleteFile(configFile);
        }
    }

    /** Test sharding using Tradefed internal algorithm. */
    @Test
    public void testShardConfig_internal_shardIndex() throws Exception {
        CommandOptions options = new CommandOptions();
        OptionSetter setter = new OptionSetter(options);
        setter.setOptionValue("shard-count", "5");
        setter.setOptionValue("shard-index", "2");
        mConfig.setCommandOptions(options);
        mConfig.setCommandLine(new String[] {"empty"});
        StubTest test = new StubTest();
        setter = new OptionSetter(test);
        setter.setOptionValue("num-shards", "5");
        mConfig.setTest(test);
        assertEquals(1, mConfig.getTests().size());
        // We do not shard, we are relying on the current invocation to run.
        assertFalse(mHelper.shardConfig(mConfig, mTestInfo, mRescheduler, null));
        // Rescheduled is NOT called because we use the current invocation to run the index.
        Mockito.verify(mRescheduler, Mockito.times(0)).scheduleConfig(Mockito.any());
        assertEquals(1, mConfig.getTests().size());
        // Original IRemoteTest was replaced by the sharded one in the configuration.
        assertNotEquals(test, mConfig.getTests().get(0));
    }

    /**
     * Test sharding using Tradefed internal algorithm. On a non shardable IRemoteTest and getting
     * the shard 0.
     */
    @Test
    public void testShardConfig_internal_shardIndex_notShardable_shard0() throws Exception {
        CommandOptions options = new CommandOptions();
        OptionSetter setter = new OptionSetter(options);
        setter.setOptionValue("shard-count", "5");
        setter.setOptionValue("shard-index", "0");
        mConfig.setCommandOptions(options);
        mConfig.setCommandLine(new String[] {"empty"});
        IRemoteTest test =
                new IRemoteTest() {
                    @Override
                    public void run(TestInformation testInfo, ITestInvocationListener listener)
                            throws DeviceNotAvailableException {
                        // do nothing.
                    }
                };
        mConfig.setTest(test);
        assertEquals(1, mConfig.getTests().size());
        // We do not shard, we are relying on the current invocation to run.
        assertFalse(mHelper.shardConfig(mConfig, mTestInfo, mRescheduler, null));
        // Rescheduled is NOT called because we use the current invocation to run the index.
        Mockito.verify(mRescheduler, Mockito.times(0)).scheduleConfig(Mockito.any());
        assertEquals(1, mConfig.getTests().size());
        // Original IRemoteTest is the same since the test was not shardable
        assertSame(test, mConfig.getTests().get(0));
    }

    /**
     * Test sharding using Tradefed internal algorithm. On a non shardable IRemoteTest and getting
     * the shard 1.
     */
    @Test
    public void testShardConfig_internal_shardIndex_notShardable_shard1() throws Exception {
        CommandOptions options = new CommandOptions();
        OptionSetter setter = new OptionSetter(options);
        setter.setOptionValue("shard-count", "5");
        setter.setOptionValue("shard-index", "1");
        mConfig.setCommandOptions(options);
        mConfig.setCommandLine(new String[] {"empty"});
        IRemoteTest test =
                new IRemoteTest() {
                    @Override
                    public void run(TestInformation testInfo, ITestInvocationListener listener)
                            throws DeviceNotAvailableException {
                        // do nothing.
                    }
                };
        mConfig.setTest(test);
        assertEquals(1, mConfig.getTests().size());
        // We do not shard, we are relying on the current invocation to run.
        assertFalse(mHelper.shardConfig(mConfig, mTestInfo, mRescheduler, null));
        // Rescheduled is NOT called because we use the current invocation to run the index.
        Mockito.verify(mRescheduler, Mockito.times(0)).scheduleConfig(Mockito.any());
        // We have no tests to put in shard-index 1 so it's empty.
        assertEquals(0, mConfig.getTests().size());
    }

    /** Test class to simulate an ITestSuite getting split. */
    public static class SplitITestSuite extends ITestSuite {

        private String mName;
        private IRemoteTest mForceTest = null;

        public SplitITestSuite() {}

        public SplitITestSuite(String name) {
            mName = name;
        }

        public SplitITestSuite(String name, IRemoteTest test) {
            this(name);
            mForceTest = test;
        }

        @Override
        public LinkedHashMap<String, IConfiguration> loadTests() {
            LinkedHashMap<String, IConfiguration> configs = new LinkedHashMap<>();
            IConfiguration configuration = null;
            try {
                configuration =
                        ConfigurationFactory.getInstance()
                                .createConfigurationFromArgs(
                                        new String[] {"empty", "--num-shards", "2"});
                if (mForceTest != null) {
                    configuration.setTest(mForceTest);
                }
            } catch (ConfigurationException e) {
                throw new RuntimeException(e);
            }
            configs.put(mName, configuration);
            return configs;
        }
    }

    public class FakeStrictShardHelper extends StrictShardHelper {
        List<IRemoteTest> fakeModules = new ArrayList<>();

        public FakeStrictShardHelper(List<IRemoteTest> modules) {
            fakeModules.addAll(modules);
        }

        @Override
        protected List<List<IRemoteTest>> splitTests(
                List<IRemoteTest> fullList, int shardCount, boolean useEvenModuleSharding) {
            List<List<IRemoteTest>> shards = new ArrayList<>();
            shards.add(new ArrayList<>(fakeModules));
            shards.add(new ArrayList<>(fakeModules));
            return shards;
        }
    }

    private ITestSuite createFakeSuite(String name) throws Exception {
        ITestSuite suite = new SplitITestSuite(name);
        return suite;
    }

    private ITestSuite createFakeSuite(String name, boolean intraModuleSharding) throws Exception {
        ITestSuite suite = new SplitITestSuite(name);
        if (!intraModuleSharding) {
            OptionSetter setter = new OptionSetter(suite);
            setter.setOptionValue("intra-module-sharding", "false");
        }
        return suite;
    }

    private List<IRemoteTest> testShard(int shardIndex) throws Exception {
        return testShard(shardIndex, false);
    }

    private List<IRemoteTest> testShard(int shardIndex, boolean useEvenModuleSharding)
            throws Exception {
        mContext.addAllocatedDevice("default", mock(ITestDevice.class));
        List<IRemoteTest> test = new ArrayList<>();
        test.add(createFakeSuite("module2"));
        test.add(createFakeSuite("module1"));
        test.add(createFakeSuite("module3"));
        test.add(createFakeSuite("module1"));
        test.add(createFakeSuite("module1"));
        test.add(createFakeSuite("module2"));
        test.add(createFakeSuite("module3"));
        CommandOptions options = new CommandOptions();
        OptionSetter setter = new OptionSetter(options);
        setter.setOptionValue("shard-count", "3");
        setter.setOptionValue("shard-index", Integer.toString(shardIndex));
        setter.setOptionValue("use-even-module-sharding", Boolean.toString(useEvenModuleSharding));
        mConfig.setCommandOptions(options);
        mConfig.setCommandLine(new String[] {"empty"});
        mConfig.setTests(test);
        mHelper.shardConfig(mConfig, mTestInfo, mRescheduler, null);
        return mConfig.getTests();
    }

    private List<IRemoteTest> createITestSuiteList(List<String> modules) throws Exception {
        List<IRemoteTest> tests = new ArrayList<>();
        for (String name : modules) {
            tests.add(createFakeSuite(name, false).split(2, mTestInfo).iterator().next());
        }

        CommandOptions options = new CommandOptions();
        OptionSetter setter = new OptionSetter(options);
        setter.setOptionValue("shard-count", "2");
        setter.setOptionValue("shard-index", Integer.toString(1));
        setter.setOptionValue("optimize-mainline-test", "true");
        mConfig.setCommandOptions(options);
        mConfig.setCommandLine(new String[] {"empty"});
        mConfig.setTests(tests);

        FakeStrictShardHelper fakeHelper = new FakeStrictShardHelper(tests);
        fakeHelper.shardConfig(mConfig, mTestInfo, mRescheduler, null);
        return mConfig.getTests();
    }

    @Test
    public void testMergeSuite_aggregate() throws Exception {
        List<List<IRemoteTest>> shards =
                List.of(testShard(0, true), testShard(1, true), testShard(2, true));
        List<List<ModuleDefinition>> moduleShards = new ArrayList<>();
        for (List<IRemoteTest> shard : shards) {
            List<ModuleDefinition> modules = new ArrayList<>();
            for (IRemoteTest test : shard) {
                assertTrue(test instanceof ITestSuite);
                modules.add(((ITestSuite) test).getDirectModule());
            }
            moduleShards.add(modules);
        }

        // Make sure we still have all modules
        assertEquals(
                Set.of("module1", "module2", "module3"),
                moduleShards.stream()
                        .flatMap(shard -> shard.stream().map(ModuleDefinition::getId))
                        .collect(Collectors.toSet()));
        // .. and all tests
        assertEquals(
                14,
                moduleShards.stream()
                        .flatMapToInt(shard -> shard.stream().mapToInt(ModuleDefinition::numTests))
                        .sum());

        for (var shard : moduleShards) {
            var shardSize = shard.stream().mapToInt(ModuleDefinition::numTests).sum();
            assertTrue(shardSize <= 5);
        }
    }

    /**
     * Total for all the _shardX test should be 14 tests (2 per modules). 6 for module1: 3 module1
     * shard * 2 4 for module2: 2 module2 shard * 2 4 for module3: 2 module3 shard * 2
     */
    @Test
    public void testMergeSuite_shard0() throws Exception {
        List<IRemoteTest> res = testShard(0);
        assertEquals(3, res.size());

        assertTrue(res.get(0) instanceof ITestSuite);
        assertEquals("module3", ((ITestSuite) res.get(0)).getDirectModule().getId());
        assertEquals(2, ((ITestSuite) res.get(0)).getDirectModule().numTests());

        assertTrue(res.get(1) instanceof ITestSuite);
        assertEquals("module1", ((ITestSuite) res.get(1)).getDirectModule().getId());
        assertEquals(1, ((ITestSuite) res.get(1)).getDirectModule().numTests());

        assertTrue(res.get(2) instanceof ITestSuite);
        assertEquals("module2", ((ITestSuite) res.get(2)).getDirectModule().getId());
        assertEquals(1, ((ITestSuite) res.get(2)).getDirectModule().numTests());
    }

    @Test
    public void testMergeSuite_shard0_even() throws Exception {
        List<IRemoteTest> res = testShard(0, true);
        assertEquals(3, res.size());

        assertTrue(res.get(0) instanceof ITestSuite);
        assertEquals("module3", ((ITestSuite) res.get(0)).getDirectModule().getId());
        assertEquals(2, ((ITestSuite) res.get(0)).getDirectModule().numTests());

        assertTrue(res.get(1) instanceof ITestSuite);
        assertEquals("module1", ((ITestSuite) res.get(1)).getDirectModule().getId());
        assertEquals(2, ((ITestSuite) res.get(1)).getDirectModule().numTests());

        assertTrue(res.get(2) instanceof ITestSuite);
        assertEquals("module2", ((ITestSuite) res.get(2)).getDirectModule().getId());
        assertEquals(1, ((ITestSuite) res.get(2)).getDirectModule().numTests());
    }

    /** Test that the unsorted test modules are re-ordered. */
    @Test
    public void testReorderTestModules() throws Exception {
        List<String> unSortedModules =
                Arrays.asList(
                        "module1[com.android.mod1.apex]",
                        "module1[com.android.mod1.apex+com.android.mod2.apex]",
                        "module2[com.android.mod1.apex]",
                        "module1[com.android.mod3.apk]",
                        "module2[com.android.mod1.apex+com.android.mod2.apex]",
                        "module2[com.android.mod3.apk]",
                        "module3[com.android.mod1.apex+com.android.mod2.apex]",
                        "module3[com.android.mod3.apk]",
                        "module4[com.android.mod3.apk]",
                        "module5[com.android.mod3.apk]");
        List<IRemoteTest> res = createITestSuiteList(unSortedModules);

        List<String> sortedModules =
                Arrays.asList(
                        "module1[com.android.mod1.apex]",
                        "module2[com.android.mod1.apex]",
                        "module1[com.android.mod1.apex+com.android.mod2.apex]",
                        "module2[com.android.mod1.apex+com.android.mod2.apex]",
                        "module3[com.android.mod1.apex+com.android.mod2.apex]",
                        "module1[com.android.mod3.apk]",
                        "module2[com.android.mod3.apk]",
                        "module3[com.android.mod3.apk]",
                        "module4[com.android.mod3.apk]",
                        "module5[com.android.mod3.apk]");
        for (int i = 0; i < sortedModules.size(); i++) {
            assertEquals(sortedModules.get(i), ((ITestSuite) res.get(i)).getDirectModule().getId());
        }
    }

    /** Test that the there exist a module with invalid parameterized modules defined. */
    @Test
    public void testReorderTestModulesWithUnexpectedMainlineModules() throws Exception {
        List<String> modules = Arrays.asList("module1[com.mod1.apex]", "module1[com.mod1]");
        try {
            createITestSuiteList(modules);
            fail("Should have thrown an exception.");
        } catch (RuntimeException expected) {
            // expected
            assertTrue(
                    expected.getMessage()
                            .contains(
                                    "Module: module1[com.mod1] doesn't match the pattern for"
                                            + " mainline modules. The pattern should end with"
                                            + " apk/apex/apks."));
        }
    }

    @Test
    public void testMergeSuite_shard1() throws Exception {
        List<IRemoteTest> res = testShard(1);
        assertEquals(3, res.size());

        assertTrue(res.get(0) instanceof ITestSuite);
        assertEquals("module1", ((ITestSuite) res.get(0)).getDirectModule().getId());
        assertEquals(1, ((ITestSuite) res.get(0)).getDirectModule().numTests());

        assertTrue(res.get(1) instanceof ITestSuite);
        assertEquals("module3", ((ITestSuite) res.get(1)).getDirectModule().getId());
        assertEquals(1, ((ITestSuite) res.get(1)).getDirectModule().numTests());

        assertTrue(res.get(2) instanceof ITestSuite);
        assertEquals("module2", ((ITestSuite) res.get(2)).getDirectModule().getId());
        assertEquals(2, ((ITestSuite) res.get(2)).getDirectModule().numTests());
    }

    @Test
    public void testMergeSuite_shard1_even() throws Exception {
        List<IRemoteTest> res = testShard(1, true);
        assertEquals(3, res.size());

        assertTrue(res.get(0) instanceof ITestSuite);
        assertEquals("module3", ((ITestSuite) res.get(0)).getDirectModule().getId());
        assertEquals(1, ((ITestSuite) res.get(0)).getDirectModule().numTests());

        assertTrue(res.get(1) instanceof ITestSuite);
        assertEquals("module2", ((ITestSuite) res.get(1)).getDirectModule().getId());
        assertEquals(2, ((ITestSuite) res.get(1)).getDirectModule().numTests());

        assertTrue(res.get(2) instanceof ITestSuite);
        assertEquals("module1", ((ITestSuite) res.get(2)).getDirectModule().getId());
        assertEquals(2, ((ITestSuite) res.get(2)).getDirectModule().numTests());
    }

    @Test
    public void testMergeSuite_shard2() throws Exception {
        List<IRemoteTest> res = testShard(2);
        assertEquals(3, res.size());

        assertTrue(res.get(0) instanceof ITestSuite);
        assertEquals("module1", ((ITestSuite) res.get(0)).getDirectModule().getId());
        assertEquals(4, ((ITestSuite) res.get(0)).getDirectModule().numTests());

        assertTrue(res.get(1) instanceof ITestSuite);
        assertEquals("module2", ((ITestSuite) res.get(1)).getDirectModule().getId());
        assertEquals(1, ((ITestSuite) res.get(1)).getDirectModule().numTests());

        assertTrue(res.get(1) instanceof ITestSuite);
        assertEquals("module3", ((ITestSuite) res.get(2)).getDirectModule().getId());
        assertEquals(1, ((ITestSuite) res.get(2)).getDirectModule().numTests());
    }

    @Test
    public void testMergeSuite_shard2_even() throws Exception {
        List<IRemoteTest> res = testShard(2, true);
        assertEquals(3, res.size());

        assertTrue(res.get(0) instanceof ITestSuite);
        assertEquals("module1", ((ITestSuite) res.get(0)).getDirectModule().getId());
        assertEquals(2, ((ITestSuite) res.get(0)).getDirectModule().numTests());

        assertTrue(res.get(1) instanceof ITestSuite);
        assertEquals("module2", ((ITestSuite) res.get(1)).getDirectModule().getId());
        assertEquals(1, ((ITestSuite) res.get(1)).getDirectModule().numTests());

        assertTrue(res.get(1) instanceof ITestSuite);
        assertEquals("module3", ((ITestSuite) res.get(2)).getDirectModule().getId());
        assertEquals(1, ((ITestSuite) res.get(2)).getDirectModule().numTests());
    }

    @Test
    public void testShardSuite() throws Exception {
        // mConfig
        mHelper.shardConfig(mConfig, mTestInfo, mRescheduler, null);
    }

    /**
     * Test class to ensure that when sharding interfaces are properly called and forwarded so the
     * tests have all their information for sharding.
     */
    public static class TestInterfaceClass implements IShardableTest, IInvocationContextReceiver {

        @Override
        public void setInvocationContext(IInvocationContext invocationContext) {
            Assert.assertNotNull(invocationContext);
        }

        @Override
        public void run(TestInformation testInfo, ITestInvocationListener listener)
                throws DeviceNotAvailableException {
            // ignore
        }

        @Override
        public Collection<IRemoteTest> split(int hintShard) {
            if (hintShard > 1) {
                List<IRemoteTest> shards = new ArrayList<IRemoteTest>(hintShard);
                for (int i = 0; i < hintShard; i++) {
                    shards.add(new TestInterfaceClass());
                }
                return shards;
            }
            return null;
        }
    }

    /** Test that no exception occurs when sharding for any possible interfaces. */
    @Test
    public void testSuite_withAllInterfaces() throws Exception {
        mContext.addAllocatedDevice("default", mock(ITestDevice.class));
        IRemoteTest forceTest = new TestInterfaceClass();
        IRemoteTest test = new SplitITestSuite("suite-interface", forceTest);

        CommandOptions options = new CommandOptions();
        OptionSetter setter = new OptionSetter(options);
        setter.setOptionValue("shard-count", "3");
        setter.setOptionValue("shard-index", Integer.toString(0));
        mConfig.setCommandOptions(options);
        mConfig.setTest(test);
        mHelper.shardConfig(mConfig, mTestInfo, mRescheduler, null);

        List<IRemoteTest> res = mConfig.getTests();
        assertEquals(1, res.size());

        assertTrue(res.get(0) instanceof ITestSuite);
        assertEquals("suite-interface", ((ITestSuite) res.get(0)).getDirectModule().getId());
        assertEquals(1, ((ITestSuite) res.get(0)).getDirectModule().numTests());
    }

    /** Helper for distribution tests to simply populate a list of a given count. */
    private List<IRemoteTest> createFakeTestList(int count) {
        List<IRemoteTest> testList = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            testList.add(new StubTest());
        }
        return testList;
    }

    /**
     * The distribution tests bellow expose an issue that arised with some combination of number of
     * tests and shard-count. The number of tests allocated to each shard made us use the full list
     * of tests before reaching the last shard, resulting in some OutOfBounds exception. Logic was
     * added to detect these cases and properly handle them as well as ensuring a proper balancing.
     */

    /** Test that the special ratio 130 tests for 20 shards is properly redistributed. */
    @Test
    public void testDistribution_hightests_highcount() {
        List<IRemoteTest> testList = createFakeTestList(130);
        int shardCount = 20;
        List<List<IRemoteTest>> res = mHelper.splitTests(testList, shardCount, false);
        assertEquals(7, res.get(0).size());
        assertEquals(7, res.get(1).size());
        assertEquals(7, res.get(2).size());
        assertEquals(7, res.get(3).size());
        assertEquals(7, res.get(4).size());
        assertEquals(7, res.get(5).size());
        assertEquals(7, res.get(6).size());
        assertEquals(7, res.get(7).size());
        assertEquals(7, res.get(8).size());
        assertEquals(7, res.get(9).size());
        assertEquals(6, res.get(10).size());
        assertEquals(6, res.get(11).size());
        assertEquals(6, res.get(12).size());
        assertEquals(6, res.get(13).size());
        assertEquals(6, res.get(14).size());
        assertEquals(6, res.get(15).size());
        assertEquals(6, res.get(16).size());
        assertEquals(6, res.get(17).size());
        assertEquals(6, res.get(18).size());
        assertEquals(6, res.get(19).size());
    }

    /** Test that the special ratio 7 tests for 6 shards is properly redistributed. */
    @Test
    public void testDistribution_lowtests_lowcount() {
        List<IRemoteTest> testList = createFakeTestList(7);
        int shardCount = 6;
        List<List<IRemoteTest>> res = mHelper.splitTests(testList, shardCount, false);
        assertEquals(2, res.get(0).size());
        assertEquals(1, res.get(1).size());
        assertEquals(1, res.get(2).size());
        assertEquals(1, res.get(3).size());
        assertEquals(1, res.get(4).size());
        assertEquals(1, res.get(5).size());
    }

    /** Test that the special ratio 13 tests for 6 shards is properly redistributed. */
    @Test
    public void testDistribution_lowtests_lowcount2() {
        List<IRemoteTest> testList = createFakeTestList(13);
        int shardCount = 6;
        List<List<IRemoteTest>> res = mHelper.splitTests(testList, shardCount, false);
        assertEquals(3, res.get(0).size());
        assertEquals(2, res.get(1).size());
        assertEquals(2, res.get(2).size());
        assertEquals(2, res.get(3).size());
        assertEquals(2, res.get(4).size());
        assertEquals(2, res.get(5).size());
    }

    /** Test that the special ratio 9 tests for 5 shards is properly redistributed. */
    @Test
    public void testDistribution_lowtests_lowcount3() {
        List<IRemoteTest> testList = createFakeTestList(9);
        int shardCount = 5;
        List<List<IRemoteTest>> res = mHelper.splitTests(testList, shardCount, true);
        assertEquals(2, res.get(0).size());
        assertEquals(2, res.get(1).size());
        assertEquals(2, res.get(2).size());
        assertEquals(2, res.get(3).size());
        assertEquals(1, res.get(4).size());
    }

    @Test
    public void testShardList() {
        for (int shardCount = 1; shardCount < 50; shardCount++) {
            for (int testCount = 0; testCount < 200; testCount++) {
                var fullList = IntStream.range(0, testCount).boxed().collect(Collectors.toList());
                var shards = StrictShardHelper.shardList(fullList, shardCount);
                var testCase =
                        "shardCount="
                                + shardCount
                                + " testCount="
                                + testCount
                                + " shards="
                                + shards;

                assertEquals(testCase, shardCount, shards.size());
                assertEquals(
                        testCase,
                        fullList,
                        shards.stream().flatMap(List::stream).collect(Collectors.toList()));

                var maxShardSize = shards.stream().map(List::size).max(Integer::compareTo).get();
                var minShardSize = shards.stream().map(List::size).min(Integer::compareTo).get();
                assertTrue(testCase, maxShardSize - minShardSize <= 1);
            }
        }
    }

    private File createTmpConfig(String objType, Object obj) throws IOException {
        File configFile = FileUtil.createTempFile("shard-helper-test", ".xml");
        String content = String.format(TEST_CONFIG, objType, obj.getClass().getCanonicalName());
        FileUtil.writeToFile(content, configFile);
        return configFile;
    }
}
