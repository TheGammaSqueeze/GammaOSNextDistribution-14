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
package com.android.tradefed.testtype;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.android.compatibility.common.tradefed.testtype.JarHostTest;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.ExecutionFiles.FilesKey;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner.TestMetrics;
import com.android.tradefed.testtype.suite.ModuleDefinition;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.proto.TfMetricProtoUtil;

import com.google.common.truth.Truth;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Unit tests for {@link HostTest} jar handling functionalities. */
@RunWith(JUnit4.class)
public class JarHostTestTest {

    private static final String QUALIFIED_PATH = "/com/android/tradefed/referencetests";
    private static final String TEST_JAR1 = "/MultipleClassesTest.jar";
    private static final String TEST_JAR2 = "/OnePassingOneFailingTest.jar";
    private HostTest mTest;
    private DeviceBuildInfo mStubBuildInfo;
    private TestInformation mTestInfo;
    private File mTestDir = null;
    @Mock ITestInvocationListener mListener;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mTest = new HostTest();
        mTestDir = FileUtil.createTempDir("jarhostest");

        OptionSetter setter = new OptionSetter(mTest);
        setter.setOptionValue("enable-pretty-logs", "false");
        mStubBuildInfo = new DeviceBuildInfo();
        mStubBuildInfo.setTestsDir(mTestDir, "v1");
        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("device", mStubBuildInfo);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
        mTestInfo.executionFiles().put(FilesKey.TESTS_DIRECTORY, mTestDir);
    }

    @After
    public void tearDown() throws Exception {
        FileUtil.recursiveDelete(mTestDir);
    }

    /**
     * Helper to read a file from the res/testtype directory and return it.
     *
     * @param filename the name of the file in the res/testtype directory
     * @param parentDir dir where to put the jar. Null if in default tmp directory.
     * @return the extracted jar file.
     */
    protected File getJarResource(String filename, File parentDir) throws IOException {
        InputStream jarFileStream = getClass().getResourceAsStream(filename);
        if (jarFileStream == null) {
            jarFileStream = getClass().getResourceAsStream(QUALIFIED_PATH + filename);
        }
        File jarFile = FileUtil.createTempFile("test", ".jar", parentDir);
        FileUtil.writeToFile(jarFileStream, jarFile);
        return jarFile;
    }

    /**
     * Test class, we have to annotate with full org.junit.Test to avoid name collision in import.
     */
    @RunWith(JUnit4.class)
    public static class Junit4TestClass {
        public Junit4TestClass() {}

        @org.junit.Test
        public void testPass1() {}
    }

    /**
     * Test class, we have to annotate with full org.junit.Test to avoid name collision in import.
     */
    @RunWith(JUnit4.class)
    public static class Junit4TestClass2 {
        public Junit4TestClass2() {}

        @Rule public TestMetrics metrics = new TestMetrics();

        @org.junit.Test
        public void testPass2() {
            metrics.addTestMetric("key", "value");
        }
    }

    /** Test that {@link HostTest#split(int)} can split classes coming from a jar. */
    @Test
    public void testSplit_withJar() throws Exception {
        File testJar = getJarResource(TEST_JAR1, mTestDir);
        mTest = new HostTestLoader(testJar);
        mTest.setBuild(mStubBuildInfo);
        ITestDevice device = mock(ITestDevice.class);
        mTest.setDevice(device);
        OptionSetter setter = new OptionSetter(mTest);
        setter.setOptionValue("enable-pretty-logs", "false");
        setter.setOptionValue("jar", testJar.getName());
        // full class count without sharding
        mTest.setTestInformation(mTestInfo);
        assertEquals(4, mTest.countTestCases());

        List<IRemoteTest> tests = new ArrayList<>(mTest.split(5, mTestInfo));
        // HostTest sharding does not respect the shard-count hint (expected)
        assertEquals(3, tests.size());

        // 5 shards total the number of tests.
        int total = 0;
        IRemoteTest shard1 = tests.get(0);
        assertTrue(shard1 instanceof HostTest);
        ((HostTest) shard1).setTestInformation(mTestInfo);
        ((HostTest) shard1).setBuild(new BuildInfo());
        ((HostTest) shard1).setDevice(device);
        assertEquals(2, ((HostTest) shard1).countTestCases());
        total += ((HostTest) shard1).countTestCases();

        IRemoteTest shard2 = tests.get(1);
        assertTrue(shard2 instanceof HostTest);
        ((HostTest) shard2).setTestInformation(mTestInfo);
        ((HostTest) shard2).setBuild(new BuildInfo());
        ((HostTest) shard2).setDevice(device);
        assertEquals(1, ((HostTest) shard2).countTestCases());
        total += ((HostTest) shard2).countTestCases();

        IRemoteTest shard3 = tests.get(2);
        assertTrue(shard3 instanceof HostTest);
        ((HostTest) shard3).setTestInformation(mTestInfo);
        ((HostTest) shard3).setBuild(new BuildInfo());
        ((HostTest) shard3).setDevice(device);
        assertEquals(1, ((HostTest) shard3).countTestCases());
        total += ((HostTest) shard3).countTestCases();

        assertEquals(4, total);
    }

    /** Avoid collision between --class and --jar when they reference common classes. */
    @Test
    public void testSplit_countWithFilter() throws Exception {
        File testJar = getJarResource(TEST_JAR1, mTestDir);
        mTest = new HostTestLoader(testJar);
        mTest.setBuild(mStubBuildInfo);
        ITestDevice device = mock(ITestDevice.class);
        mTest.setDevice(device);
        OptionSetter setter = new OptionSetter(mTest);
        setter.setOptionValue("enable-pretty-logs", "false");
        setter.setOptionValue("jar", testJar.getName());
        // Explicitly request a class from the jar
        setter.setOptionValue("class", "com.android.tradefed.referencetests.SimplePassingTest");
        // full class count without sharding should be 1
        mTest.setTestInformation(mTestInfo);
        assertEquals(1, mTest.countTestCases());
    }

    /**
     * Testable version of {@link HostTest} that allows adding jar to classpath for testing purpose.
     */
    public static class HostTestLoader extends HostTest {

        private static File mTestJar;

        public HostTestLoader() {}

        public HostTestLoader(File jar) {
            mTestJar = jar;
        }

        @Override
        protected ClassLoader getClassLoader() {
            ClassLoader child = super.getClassLoader();
            try {
                child =
                        new URLClassLoader(
                                Arrays.asList(mTestJar.toURI().toURL()).toArray(new URL[] {}),
                                super.getClassLoader());
            } catch (MalformedURLException e) {
                CLog.e(e);
            }
            return child;
        }
    }

    /**
     * If a jar file is not found, the countTest will fail but we still want to report a
     * testRunStart and End pair for results.
     */
    @Test
    public void testCountTestFails() throws Exception {
        OptionSetter setter = new OptionSetter(mTest);
        setter.setOptionValue("jar", "thisjardoesnotexistatall.jar");
        mTest.setBuild(new BuildInfo());

        ArgumentCaptor<FailureDescription> captured =
                ArgumentCaptor.forClass(FailureDescription.class);

        mTest.run(mTestInfo, mListener);

        verify(mListener).testRunStarted(HostTest.class.getName(), 0);
        verify(mListener).testRunFailed(captured.capture());
        verify(mListener).testRunEnded(0L, new HashMap<String, Metric>());
        Truth.assertThat(captured.getValue().getErrorMessage())
                .contains(
                        "java.io.FileNotFoundException: "
                                + "Could not find an artifact file associated with "
                                + "thisjardoesnotexistatall.jar");
    }

    /** Test that metrics from tests in JarHost are reported and accounted for. */
    @Test
    public void testJarHostMetrics() throws Exception {
        OptionSetter setter = new OptionSetter(mTest);
        setter.setOptionValue("class", Junit4TestClass2.class.getName());

        TestDescription tid = new TestDescription(Junit4TestClass2.class.getName(), "testPass2");

        Map<String, String> metrics = new HashMap<>();
        metrics.put("key", "value");

        mTest.run(mTestInfo, mListener);

        verify(mListener).testRunStarted(Mockito.any(), Mockito.eq(1));
        verify(mListener).testStarted(Mockito.eq(tid));
        verify(mListener)
                .testEnded(Mockito.eq(tid), Mockito.eq(TfMetricProtoUtil.upgradeConvert(metrics)));
        verify(mListener).testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    @Test
    public void testRunWithJar() throws Exception {
        File testJar = getJarResource(TEST_JAR2, mTestDir);
        mTest = new HostTest();
        mTest.setBuild(mStubBuildInfo);
        ITestDevice device = mock(ITestDevice.class);
        mTest.setDevice(device);
        OptionSetter setter = new OptionSetter(mTest);
        setter.setOptionValue("enable-pretty-logs", "false");
        setter.setOptionValue("jar", testJar.getName());
        // full class count without sharding
        mTest.setTestInformation(mTestInfo);
        assertEquals(2, mTest.countTestCases());

        TestDescription testOne =
                new TestDescription(
                        "com.android.tradefed.referencetests.OnePassingOneFailingTest",
                        "test1Passing");
        TestDescription testTwo =
                new TestDescription(
                        "com.android.tradefed.referencetests.OnePassingOneFailingTest",
                        "test2Failing");

        mTest.run(mTestInfo, mListener);

        verify(mListener)
                .testRunStarted("com.android.tradefed.referencetests.OnePassingOneFailingTest", 2);
        verify(mListener).testStarted(testOne);
        verify(mListener).testEnded(Mockito.eq(testOne), Mockito.<HashMap<String, Metric>>any());
        verify(mListener).testStarted(testTwo);
        verify(mListener).testFailed(Mockito.eq(testTwo), (String) Mockito.any());
        verify(mListener).testEnded(Mockito.eq(testTwo), Mockito.<HashMap<String, Metric>>any());
        verify(mListener).testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    @Test
    public void testRunWithClassFromExternalJar() throws Exception {
        File testJar = getJarResource(TEST_JAR2, mTestDir);
        mTestInfo
                .getContext()
                .addInvocationAttribute(
                        ModuleDefinition.MODULE_NAME, FileUtil.getBaseName(testJar.getName()));
        mTest = new HostTest();
        mTest.setBuild(mStubBuildInfo);
        ITestDevice device = mock(ITestDevice.class);
        mTest.setDevice(device);
        OptionSetter setter = new OptionSetter(mTest);
        setter.setOptionValue("enable-pretty-logs", "false");
        setter.setOptionValue(
                "class", "com.android.tradefed.referencetests.OnePassingOneFailingTest");
        // full class count without sharding
        mTest.setTestInformation(mTestInfo);
        assertEquals(2, mTest.countTestCases());

        TestDescription testOne =
                new TestDescription(
                        "com.android.tradefed.referencetests.OnePassingOneFailingTest",
                        "test1Passing");
        TestDescription testTwo =
                new TestDescription(
                        "com.android.tradefed.referencetests.OnePassingOneFailingTest",
                        "test2Failing");

        mTest.run(mTestInfo, mListener);

        verify(mListener)
                .testRunStarted("com.android.tradefed.referencetests.OnePassingOneFailingTest", 2);
        verify(mListener).testStarted(testOne);
        verify(mListener).testEnded(Mockito.eq(testOne), Mockito.<HashMap<String, Metric>>any());
        verify(mListener).testStarted(testTwo);
        verify(mListener).testFailed(Mockito.eq(testTwo), (String) Mockito.any());
        verify(mListener).testEnded(Mockito.eq(testTwo), Mockito.<HashMap<String, Metric>>any());
        verify(mListener).testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /** Test that {@link JarHostTest#split()} inherited from {@link HostTest} is still good. */
    @Test
    public void testSplit_withoutJar() throws Exception {
        mTest = new JarHostTest();
        OptionSetter setter = new OptionSetter(mTest);
        setter.setOptionValue(
                "class", "com.android.tradefed.testtype.JarHostTestTest$Junit4TestClass");
        setter.setOptionValue(
                "class", "com.android.tradefed.testtype." + "JarHostTestTest$Junit4TestClass2");
        // sharCount is ignored; will split by number of classes
        List<IRemoteTest> res = (List<IRemoteTest>) mTest.split(1, mTestInfo);
        assertEquals(2, res.size());
        assertTrue(res.get(0) instanceof JarHostTest);
        assertTrue(res.get(1) instanceof JarHostTest);
    }
}
