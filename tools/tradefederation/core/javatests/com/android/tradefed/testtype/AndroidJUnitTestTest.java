/*
 * Copyright (C) 2015 The Android Open Source Project
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.ITestLifeCycleReceiver;
import com.android.tradefed.result.proto.TestRecordProto.FailureStatus;
import com.android.tradefed.util.FileUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Unit tests for {@link AndroidJUnitTest} */
@RunWith(JUnit4.class)
public class AndroidJUnitTestTest {

    private static final String AJUR = "android.support.test.runner.AndroidJUnitRunner";
    private static final int TEST_TIMEOUT = 0;
    private static final long SHELL_TIMEOUT = 0;
    private static final String TEST_PACKAGE_VALUE = "com.foo";
    private static final TestIdentifier TEST1 = new TestIdentifier("Test", "test1");
    private static final TestIdentifier TEST2 = new TestIdentifier("Test", "test2");

    /** The {@link AndroidJUnitTest} under test, with all dependencies mocked out */
    private AndroidJUnitTest mAndroidJUnitTest;

    // The mock objects.
    @Mock IDevice mMockIDevice;
    @Mock ITestDevice mMockTestDevice;
    @Mock IRemoteAndroidTestRunner mMockRemoteRunner;
    @Mock ITestInvocationListener mMockListener;
    private TestInformation mTestInfo;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mMockTestDevice.getIDevice()).thenReturn(mMockIDevice);
        when(mMockTestDevice.getSerialNumber()).thenReturn("serial");

        mAndroidJUnitTest =
                new AndroidJUnitTest() {
                    @Override
                    IRemoteAndroidTestRunner createRemoteAndroidTestRunner(
                            String packageName,
                            String runnerName,
                            IDevice device,
                            TestInformation testInformation) {
                        return mMockRemoteRunner;
                    }
                };
        mAndroidJUnitTest.setRunnerName(AJUR);
        mAndroidJUnitTest.setPackageName(TEST_PACKAGE_VALUE);
        mAndroidJUnitTest.setConfiguration(new Configuration("", ""));
        mAndroidJUnitTest.setDevice(mMockTestDevice);
        // default to no rerun, for simplicity
        mAndroidJUnitTest.setRerunMode(false);
        // default to no timeout for simplicity
        mAndroidJUnitTest.setTestTimeout(TEST_TIMEOUT);
        mAndroidJUnitTest.setShellTimeout(SHELL_TIMEOUT);
        mMockRemoteRunner.setMaxTimeToOutputResponse(SHELL_TIMEOUT, TimeUnit.MILLISECONDS);
        mMockRemoteRunner.setMaxTimeout(0L, TimeUnit.MILLISECONDS);
        mMockRemoteRunner.addInstrumentationArg(
                InstrumentationTest.TEST_TIMEOUT_INST_ARGS_KEY, Long.toString(SHELL_TIMEOUT));
        mMockRemoteRunner.addInstrumentationArg(
                AndroidJUnitTest.NEW_RUN_LISTENER_ORDER_KEY, "true");
        IInvocationContext context = new InvocationContext();
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
    }

    /** Test list of tests to run is filtered by include filters. */
    @Test
    public void testRun_includeFilterClass() throws Exception {
        // expect this call

        setRunTestExpectations();

        mAndroidJUnitTest.addIncludeFilter(TEST1.toString());
        mAndroidJUnitTest.run(mTestInfo, mMockListener);

        verify(mMockRemoteRunner).addInstrumentationArg("class", TEST1.toString());
        verifyRunTestExpectations();
    }

    /** Test list of tests to run is filtered by include filters by input sequence order. */
    @Test
    public void testRun_MultipleIncludeFilterClassByOrder() throws Exception {
        // expect this call

        setRunTestExpectations();

        mAndroidJUnitTest.addIncludeFilter("a.b.c.Class3");
        mAndroidJUnitTest.addIncludeFilter("a.b.c.Class1");
        mAndroidJUnitTest.addIncludeFilter("a.b.c.Class2");
        mAndroidJUnitTest.run(mTestInfo, mMockListener);

        verify(mMockRemoteRunner)
                .addInstrumentationArg("class", "a.b.c.Class3,a.b.c.Class1,a.b.c.Class2");
        verifyRunTestExpectations();
    }

    /** Test list of tests to run is filtered by exclude filters. */
    @Test
    public void testRun_excludeFilterClass() throws Exception {
        // expect this call

        setRunTestExpectations();

        mAndroidJUnitTest.addExcludeFilter(TEST1.toString());
        mAndroidJUnitTest.run(mTestInfo, mMockListener);

        verify(mMockRemoteRunner).addInstrumentationArg("notClass", TEST1.toString());
        verifyRunTestExpectations();
    }

    /** Test list of tests to run is filtered by exclude filters by input sequence order. */
    @Test
    public void testRun_MultipleExcludeFilterClassByOrder() throws Exception {
        // expect this call

        setRunTestExpectations();

        mAndroidJUnitTest.addExcludeFilter("a.b.c.Class3");
        mAndroidJUnitTest.addExcludeFilter("a.b.c.Class1");
        mAndroidJUnitTest.addExcludeFilter("a.b.c.Class2");
        mAndroidJUnitTest.run(mTestInfo, mMockListener);

        verify(mMockRemoteRunner)
                .addInstrumentationArg("notClass", "a.b.c.Class3,a.b.c.Class1,a.b.c.Class2");
        verifyRunTestExpectations();
    }

    /** Test list of tests to run is filtered by include and exclude filters. */
    @Test
    public void testRun_includeAndExcludeFilterClass() throws Exception {
        // expect this call

        setRunTestExpectations();

        mAndroidJUnitTest.addIncludeFilter(TEST1.getClassName());
        mAndroidJUnitTest.addExcludeFilter(TEST2.toString());
        mAndroidJUnitTest.run(mTestInfo, mMockListener);

        verify(mMockRemoteRunner).addInstrumentationArg("class", TEST1.getClassName());
        verify(mMockRemoteRunner).addInstrumentationArg("notClass", TEST2.toString());
        verifyRunTestExpectations();
    }

    /** Test list of tests to run is filtered by include filters. */
    @Test
    public void testRun_includeFilterPackage() throws Exception {
        // expect this call

        setRunTestExpectations();

        mAndroidJUnitTest.addIncludeFilter("com.android.test");
        mAndroidJUnitTest.run(mTestInfo, mMockListener);

        verify(mMockRemoteRunner).addInstrumentationArg("package", "com.android.test");
        verifyRunTestExpectations();
    }

    /** Test list of tests to run is filtered by exclude filters. */
    @Test
    public void testRun_excludeFilterPackage() throws Exception {
        // expect this call

        setRunTestExpectations();

        mAndroidJUnitTest.addExcludeFilter("com.android.not");
        mAndroidJUnitTest.run(mTestInfo, mMockListener);

        verify(mMockRemoteRunner).addInstrumentationArg("notPackage", "com.android.not");
        verifyRunTestExpectations();
    }

    /** Test list of tests to run is filtered by include and exclude filters. */
    @Test
    public void testRun_includeAndExcludeFilterPackage() throws Exception {
        // expect this call

        setRunTestExpectations();

        mAndroidJUnitTest.addIncludeFilter("com.android.test");
        mAndroidJUnitTest.addExcludeFilter("com.android.not");
        mAndroidJUnitTest.run(mTestInfo, mMockListener);

        verify(mMockRemoteRunner).addInstrumentationArg("package", "com.android.test");
        verify(mMockRemoteRunner).addInstrumentationArg("notPackage", "com.android.not");
        verifyRunTestExpectations();
    }

    /** Test list of tests to run is filtered by include filters using regex. */
    @Test
    public void testRun_includeFilterSingleTestsRegex() throws Exception {
        // expect this call

        setRunTestExpectations();

        mAndroidJUnitTest.addIncludeFilter(".*testName$");
        mAndroidJUnitTest.run(mTestInfo, mMockListener);

        verify(mMockRemoteRunner).addInstrumentationArg("tests_regex", ".*testName$");
        verifyRunTestExpectations();
    }

    /** Test list of tests to run is filtered by include filters using multiple regex. */
    @Test
    public void testRun_includeFilterMultipleTestsRegex() throws Exception {
        // expect this call
        setRunTestExpectations();

        mAndroidJUnitTest.addIncludeFilter(".*test2");
        mAndroidJUnitTest.addIncludeFilter(".*testName$");
        mAndroidJUnitTest.run(mTestInfo, mMockListener);

        verify(mMockRemoteRunner).addInstrumentationArg("tests_regex", "\"(.*test2|.*testName$)\"");
        verifyRunTestExpectations();
    }

    /** Test list of parameterized tests to run is filtered by include filters. */
    @Test
    public void testRun_includeFilterParameterizedTestWithBrackets() throws Exception {
        // expect this call

        setRunTestExpectations();

        mAndroidJUnitTest.addIncludeFilter("a.b.c#myTestClass[a-b_c(1)]");
        mAndroidJUnitTest.run(mTestInfo, mMockListener);

        verify(mMockRemoteRunner).addInstrumentationArg("class", "a.b.c#myTestClass[a-b_c(1)]");
        verifyRunTestExpectations();
    }

    /** Test list of parameterized tests to run is filtered by include filters. */
    @Test
    public void testRun_includeFilterParameterizedTestWithList() throws Exception {
        // expect this call

        setRunTestExpectations();

        mAndroidJUnitTest.addIncludeFilter("a.b.c#myTestClass[[2,3],3.14]");
        mAndroidJUnitTest.run(mTestInfo, mMockListener);

        verify(mMockRemoteRunner).addInstrumentationArg("class", "a.b.c#myTestClass[[2,3],3.14]");
        verifyRunTestExpectations();
    }

    /** Test list of tests to run is filtered by using regex due to not end with ]. */
    @Test
    public void testRun_includeFilterEndWithStarNotParameterizedTest() throws Exception {
        // expect this call

        setRunTestExpectations();

        mAndroidJUnitTest.addIncludeFilter("a.b.c#myTestClass[a-b]*");
        mAndroidJUnitTest.run(mTestInfo, mMockListener);

        verify(mMockRemoteRunner).addInstrumentationArg("tests_regex", "a.b.c#myTestClass[a-b]*");
        verifyRunTestExpectations();
    }

    /** Test list of tests to run is filtered by include filters using invalid regex. */
    @Test
    public void testRun_includeFilterInvalidTestsRegex() throws Exception {
        setRunTestExpectations();

        // regex with unbalanced parenthesis.
        mAndroidJUnitTest.addIncludeFilter("(testName");
        try {
            mAndroidJUnitTest.run(mTestInfo, mMockListener);
        } catch (RuntimeException expected) {
            // expected.
            // don't verify test run either since it should fail out
            return;
        }
        fail("RuntimeException not raised for filter with invalid regular expression.");
    }

    /** Test list of tests to run is filtered by include and exclude filters. */
    @Test
    public void testRun_includeAndExcludeFilters() throws Exception {
        // expect this call

        setRunTestExpectations();

        mAndroidJUnitTest.addIncludeFilter(TEST1.getClassName());
        mAndroidJUnitTest.addExcludeFilter(TEST2.toString());
        mAndroidJUnitTest.addIncludeFilter("com.android.test");
        mAndroidJUnitTest.addExcludeFilter("com.android.not");
        mAndroidJUnitTest.run(mTestInfo, mMockListener);

        verify(mMockRemoteRunner).addInstrumentationArg("class", TEST1.getClassName());
        verify(mMockRemoteRunner).addInstrumentationArg("notClass", TEST2.toString());
        verify(mMockRemoteRunner).addInstrumentationArg("package", "com.android.test");
        verify(mMockRemoteRunner).addInstrumentationArg("notPackage", "com.android.not");
        verifyRunTestExpectations();
    }

    /** Test list of tests to run is filtered by include file. */
    @Test
    public void testRun_includeFile() throws Exception {

        setRunTestExpectations();
        when(mMockTestDevice.pushFile(Mockito.<File>any(), Mockito.<String>any(), Mockito.eq(true)))
                .thenReturn(Boolean.TRUE);
        when(mMockTestDevice.executeShellCommand(Mockito.<String>any())).thenReturn("");
        when(mMockTestDevice.doesFileExist(Mockito.<String>any())).thenReturn(true);

        File tmpFile = FileUtil.createTempFile("testFile", ".txt");
        FileUtil.writeToFile(TEST1.toString(), tmpFile);
        try {
            mAndroidJUnitTest.setIncludeTestFile(tmpFile);
            mAndroidJUnitTest.run(mTestInfo, mMockListener);
            verify(mMockRemoteRunner)
                    .addInstrumentationArg(Mockito.eq("testFile"), Mockito.<String>any());
            verify(mMockTestDevice).deleteFile("/data/local/tmp/ajur");
            verifyRunTestExpectations();
        } finally {
            FileUtil.deleteFile(tmpFile);
        }
    }

    /** Test list of tests to run is filtered by include file. */
    @Test
    public void testRun_includeFileWithChown() throws Exception {

        setRunTestExpectations();
        when(mMockTestDevice.pushFile(Mockito.<File>any(), Mockito.<String>any(), Mockito.eq(true)))
                .thenReturn(Boolean.TRUE);
        when(mMockTestDevice.executeShellCommand(Mockito.<String>any())).thenReturn("");
        when(mMockTestDevice.doesFileExist(Mockito.<String>any())).thenReturn(false);

        File tmpFile = FileUtil.createTempFile("testFile", ".txt");
        FileUtil.writeToFile(TEST1.toString(), tmpFile);
        try {
            mAndroidJUnitTest.setIncludeTestFile(tmpFile);
            mAndroidJUnitTest.run(mTestInfo, mMockListener);
            verify(mMockRemoteRunner)
                    .addInstrumentationArg(Mockito.eq("testFile"), Mockito.<String>any());
            verify(mMockTestDevice).deleteFile("/data/local/tmp/ajur");
            verify(mMockTestDevice, times(1)).executeShellCommand(Mockito.startsWith("chown "));
            verifyRunTestExpectations();
        } finally {
            FileUtil.deleteFile(tmpFile);
        }
    }
    /** Test list of tests to run is filtered by exclude file. */
    @Test
    public void testRun_excludeFile() throws Exception {

        setRunTestExpectations();
        when(mMockTestDevice.pushFile(Mockito.<File>any(), Mockito.<String>any(), Mockito.eq(true)))
                .thenReturn(Boolean.TRUE);
        when(mMockTestDevice.executeShellCommand(Mockito.<String>any())).thenReturn("");
        when(mMockTestDevice.doesFileExist(Mockito.<String>any())).thenReturn(true);

        File tmpFile = FileUtil.createTempFile("notTestFile", ".txt");
        FileUtil.writeToFile(TEST1.toString(), tmpFile);
        try {
            mAndroidJUnitTest.setExcludeTestFile(tmpFile);
            mAndroidJUnitTest.run(mTestInfo, mMockListener);
            verify(mMockRemoteRunner)
                    .addInstrumentationArg(Mockito.eq("notTestFile"), Mockito.<String>any());
            verify(mMockTestDevice).deleteFile("/data/local/tmp/ajur");
            verifyRunTestExpectations();
        } finally {
            FileUtil.deleteFile(tmpFile);
        }
    }

    /**
     * Test list of tests to run is filtered by include file, does not override existing filters.
     */
    @Test
    public void testRun_testFileAndFilters() throws Exception {

        setRunTestExpectations();
        when(mMockTestDevice.pushFile(Mockito.<File>any(), Mockito.<String>any(), Mockito.eq(true)))
                .thenReturn(Boolean.TRUE);
        when(mMockTestDevice.executeShellCommand(Mockito.<String>any())).thenReturn("");
        when(mMockTestDevice.doesFileExist(Mockito.<String>any())).thenReturn(true);

        File tmpFileInclude = FileUtil.createTempFile("includeFile", ".txt");
        FileUtil.writeToFile(TEST1.toString(), tmpFileInclude);
        File tmpFileExclude = FileUtil.createTempFile("excludeFile", ".txt");
        FileUtil.writeToFile(TEST2.toString(), tmpFileExclude);
        try {
            mAndroidJUnitTest.addIncludeFilter(TEST1.getClassName());
            mAndroidJUnitTest.addExcludeFilter(TEST2.toString());
            mAndroidJUnitTest.setIncludeTestFile(tmpFileInclude);
            mAndroidJUnitTest.setExcludeTestFile(tmpFileExclude);
            mAndroidJUnitTest.run(mTestInfo, mMockListener);
            verify(mMockTestDevice, times(2))
                    .pushFile(Mockito.<File>any(), Mockito.<String>any(), Mockito.eq(true));
            verify(mMockRemoteRunner)
                    .addInstrumentationArg(Mockito.eq("testFile"), Mockito.<String>any());
            verify(mMockRemoteRunner)
                    .addInstrumentationArg(Mockito.eq("notTestFile"), Mockito.<String>any());
            verify(mMockRemoteRunner).addInstrumentationArg("class", TEST1.getClassName());
            verify(mMockRemoteRunner).addInstrumentationArg("notClass", TEST2.toString());
            verify(mMockTestDevice).deleteFile("/data/local/tmp/ajur");
            verifyRunTestExpectations();
        } finally {
            FileUtil.deleteFile(tmpFileInclude);
            FileUtil.deleteFile(tmpFileExclude);
        }
    }

    /**
     * Test that when pushing the filters fails, we have a test run failure since we were not able
     * to run anything.
     */
    @Test
    public void testRun_testFileAndFilters_fails() throws Exception {
        mMockRemoteRunner = mock(IRemoteAndroidTestRunner.class);
        when(mMockTestDevice.pushFile(Mockito.<File>any(), Mockito.<String>any(), Mockito.eq(true)))
                .thenThrow(new DeviceNotAvailableException("failed to push", "device1"));
        when(mMockTestDevice.doesFileExist(Mockito.<String>any())).thenReturn(true);

        FailureDescription failure = FailureDescription.create("failed to push");
        failure.setFailureStatus(FailureStatus.INFRA_FAILURE);

        File tmpFileInclude = FileUtil.createTempFile("includeFile", ".txt");
        FileUtil.writeToFile(TEST1.toString(), tmpFileInclude);
        File tmpFileExclude = FileUtil.createTempFile("excludeFile", ".txt");
        FileUtil.writeToFile(TEST2.toString(), tmpFileExclude);
        try {
            mAndroidJUnitTest.addIncludeFilter(TEST1.getClassName());
            mAndroidJUnitTest.addExcludeFilter(TEST2.toString());
            mAndroidJUnitTest.setIncludeTestFile(tmpFileInclude);
            mAndroidJUnitTest.setExcludeTestFile(tmpFileExclude);
            mAndroidJUnitTest.run(mTestInfo, mMockListener);
            fail("Should have thrown an exception.");
        } catch (DeviceNotAvailableException expected) {
            // expected
        } finally {
            FileUtil.deleteFile(tmpFileInclude);
            FileUtil.deleteFile(tmpFileExclude);
        }

        verify(mMockListener).testRunStarted(Mockito.any(), Mockito.eq(0));
        verify(mMockListener).testRunFailed(failure);
        verify(mMockListener).testRunEnded(0, new HashMap<String, Metric>());
    }

    /** Test that setting option for "test-file-filter" works as intended */
    @Test
    public void testRun_setTestFileOptions() throws Exception {

        setRunTestExpectations();
        when(mMockTestDevice.pushFile(Mockito.<File>any(), Mockito.<String>any(), Mockito.eq(true)))
                .thenReturn(Boolean.TRUE);
        when(mMockTestDevice.executeShellCommand(Mockito.<String>any())).thenReturn("");
        when(mMockTestDevice.doesFileExist(Mockito.<String>any())).thenReturn(true);

        File tmpFileInclude = FileUtil.createTempFile("includeFile", ".txt");
        FileUtil.writeToFile(TEST1.toString(), tmpFileInclude);
        File tmpFileExclude = FileUtil.createTempFile("excludeFile", ".txt");
        FileUtil.writeToFile(TEST2.toString(), tmpFileExclude);
        try {
            OptionSetter setter = new OptionSetter(mAndroidJUnitTest);
            setter.setOptionValue("test-file-include-filter", tmpFileInclude.getAbsolutePath());
            setter.setOptionValue("test-file-exclude-filter", tmpFileExclude.getAbsolutePath());
            mAndroidJUnitTest.run(mTestInfo, mMockListener);
            verify(mMockTestDevice, times(2))
                    .pushFile(Mockito.<File>any(), Mockito.<String>any(), Mockito.eq(true));
            verify(mMockRemoteRunner)
                    .addInstrumentationArg(Mockito.eq("testFile"), Mockito.<String>any());
            verify(mMockRemoteRunner)
                    .addInstrumentationArg(Mockito.eq("notTestFile"), Mockito.<String>any());
            verify(mMockTestDevice).deleteFile("/data/local/tmp/ajur");
            verifyRunTestExpectations();
        } finally {
            FileUtil.deleteFile(tmpFileInclude);
            FileUtil.deleteFile(tmpFileExclude);
        }
    }

    private void setRunTestExpectations() throws DeviceNotAvailableException {
        when(mMockTestDevice.runInstrumentationTests(
                        Mockito.eq(mMockRemoteRunner), (ITestLifeCycleReceiver) Mockito.any()))
                .thenReturn(Boolean.TRUE);
    }

    private void verifyRunTestExpectations() throws DeviceNotAvailableException {
        verify(mMockTestDevice)
                .runInstrumentationTests(
                        Mockito.eq(mMockRemoteRunner), (ITestLifeCycleReceiver) Mockito.any());
    }

    /**
     * Test isClassOrMethod returns true for <package>.<class> and <package>.<class>#<method> but
     * not for <package>.
     */
    @Test
    public void testIsClassOrMethod() throws Exception {
        assertFalse("String was just package", mAndroidJUnitTest.isClassOrMethod("android.test"));
        assertTrue("String was class", mAndroidJUnitTest.isClassOrMethod("android.test.Foo"));
        assertTrue("String was method", mAndroidJUnitTest.isClassOrMethod("android.test.Foo#bar"));
    }

    /** Test that {@link AndroidJUnitTest#split()} returns null if the runner is not shardable. */
    @Test
    public void testSplit_notShardable() {
        mAndroidJUnitTest.setRunnerName("fake.runner.not.shardable");
        assertNull(mAndroidJUnitTest.split());
    }

    /** Test that {@link AndroidJUnitTest#split()} returns null if no shards have been requested. */
    @Test
    public void testSplit_noShardRequested() {
        assertEquals(AJUR, mAndroidJUnitTest.getRunnerName());
        assertNull(mAndroidJUnitTest.split());
    }

    /** Test that {@link AndroidJUnitTest#split()} returns the split if no runner specified. */
    @Test
    public void testSplit_noRunner() {
        AndroidJUnitTest test = new AndroidJUnitTest();
        test.setRunnerName(null);
        assertNull(test.getRunnerName());
        Collection<IRemoteTest> listTests = test.split(4);
        assertNotNull(listTests);
        assertEquals(4, listTests.size());
    }

    /** Test that {@link AndroidJUnitTest#split(int)} returns 3 shards when requested to do so. */
    @Test
    public void testSplit_threeShards() throws Exception {
        mAndroidJUnitTest = new AndroidJUnitTest();
        mAndroidJUnitTest.setRunnerName(AJUR);
        assertEquals(AJUR, mAndroidJUnitTest.getRunnerName());
        OptionSetter setter = new OptionSetter(mAndroidJUnitTest);
        setter.setOptionValue("runtime-hint", "60s");
        List<IRemoteTest> res = (List<IRemoteTest>) mAndroidJUnitTest.split(3);
        assertNotNull(res);
        assertEquals(3, res.size());
        // Third of the execution time on each shard.
        assertEquals(20000L, ((AndroidJUnitTest) res.get(0)).getRuntimeHint());
        assertEquals(20000L, ((AndroidJUnitTest) res.get(1)).getRuntimeHint());
        assertEquals(20000L, ((AndroidJUnitTest) res.get(2)).getRuntimeHint());
        // Make sure shards cannot be re-sharded
        assertNull(((AndroidJUnitTest) res.get(0)).split(2));
        assertNull(((AndroidJUnitTest) res.get(0)).split());
    }

    /**
     * Test that {@link AndroidJUnitTest#split(int)} can only split up to the ajur-max-shard option.
     */
    @Test
    public void testSplit_maxShard() throws Exception {
        mAndroidJUnitTest = new AndroidJUnitTest();
        mAndroidJUnitTest.setRunnerName(AJUR);
        assertEquals(AJUR, mAndroidJUnitTest.getRunnerName());
        OptionSetter setter = new OptionSetter(mAndroidJUnitTest);
        setter.setOptionValue("runtime-hint", "60s");
        setter.setOptionValue("ajur-max-shard", "2");
        List<IRemoteTest> res = (List<IRemoteTest>) mAndroidJUnitTest.split(3);
        assertNotNull(res);
        assertEquals(2, res.size());
        // Third of the execution time on each shard.
        assertEquals(30000L, ((AndroidJUnitTest) res.get(0)).getRuntimeHint());
        assertEquals(30000L, ((AndroidJUnitTest) res.get(1)).getRuntimeHint());
        // Make sure shards cannot be re-sharded
        assertNull(((AndroidJUnitTest) res.get(0)).split(2));
        assertNull(((AndroidJUnitTest) res.get(0)).split());
    }
}
