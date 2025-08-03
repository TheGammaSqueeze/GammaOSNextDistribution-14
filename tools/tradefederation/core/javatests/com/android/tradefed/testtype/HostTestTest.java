/*
 * Copyright (C) 2010 The Android Open Source Project
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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.DynamicRemoteFileResolver;
import com.android.tradefed.config.DynamicRemoteFileResolver.FileResolverLoader;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.config.remote.GcsRemoteFileResolver;
import com.android.tradefed.config.remote.IRemoteFileResolver;
import com.android.tradefed.config.remote.IRemoteFileResolver.RemoteFileResolverArgs;
import com.android.tradefed.config.remote.IRemoteFileResolver.ResolvedFile;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.result.proto.TestRecordProto.FailureStatus;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner.TestLogData;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner.TestMetrics;
import com.android.tradefed.testtype.junit4.AfterClassWithInfo;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.proto.TfMetricProtoUtil;

import com.google.common.collect.ImmutableMap;

import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.JUnit4;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Unit tests for {@link HostTest}. */
@SuppressWarnings("unchecked")
@RunWith(JUnit4.class)
public class HostTestTest {

    private static final File FAKE_REMOTE_FILE_PATH = new File("gs://bucket/path/file");

    private HostTest mHostTest;
    @Mock ITestInvocationListener mListener;
    @Mock ITestDevice mMockDevice;
    @Mock IBuildInfo mMockBuildInfo;
    private TestInformation mTestInfo;

    private IRemoteFileResolver mMockResolver;

    @MyAnnotation
    @MyAnnotation3
    public static class SuccessTestCase extends TestCase {

        public SuccessTestCase() {}

        public SuccessTestCase(String name) {
            super(name);
        }

        @MyAnnotation
        public void testPass() {}

        @MyAnnotation
        @MyAnnotation2
        public void testPass2() {}
    }

    public static class DynamicTestCase extends TestCase {

        @Option(name = "dynamic-option")
        private File mDynamicFile = FAKE_REMOTE_FILE_PATH;

        public DynamicTestCase() {}

        public DynamicTestCase(String name) {
            super(name);
        }

        public void testPass() {
            assertFalse(mDynamicFile.equals(new File("gs://bucket/path/file")));
        }
    }

    public static class TestMetricTestCase extends MetricTestCase {

        @Option(name = "test-option")
        public String testOption = null;

        @Option(name = "list-option")
        public List<String> listOption = new ArrayList<>();

        @Option(name = "map-option")
        public Map<String, String> mapOption = new HashMap<>();

        public void testPass() {
            addTestMetric("key1", "metric1");
        }

        public void testPass2() {
            addTestMetric("key2", "metric2");
            if (testOption != null) {
                addTestMetric("test-option", testOption);
            }
            if (!listOption.isEmpty()) {
                addTestMetric("list-option", listOption.toString());
            }
            if (!mapOption.isEmpty()) {
                addTestMetric("map-option", mapOption.toString());
            }
        }
    }

    public static class LogMetricTestCase extends MetricTestCase {

        public void testPass() {}

        public void testPass2() {
            addTestLog(
                    "test2_log",
                    LogDataType.TEXT,
                    new ByteArrayInputStreamSource("test_log".getBytes()));
            addTestMetric("key2", "metric2");
        }
    }

    @MyAnnotation
    public static class AnotherTestCase extends TestCase {
        public AnotherTestCase() {}

        public AnotherTestCase(String name) {
            super(name);
        }

        @MyAnnotation
        @MyAnnotation2
        @MyAnnotation3
        public void testPass3() {}

        @MyAnnotation
        public void testPass4() {}
    }

    /**
     * Test class, we have to annotate with full org.junit.Test to avoid name collision in import.
     */
    @RunWith(DeviceJUnit4ClassRunner.class)
    public static class Junit4TestClass {

        public Junit4TestClass() {}

        @Option(name = "junit4-option")
        public boolean mOption = false;

        @Option(name = "map-option")
        public Map<String, String> mapOption = new HashMap<>();

        @Rule public TestMetrics metrics = new TestMetrics();

        @MyAnnotation
        @MyAnnotation2
        @org.junit.Test
        public void testPass5() {
            // test log through the rule.
            metrics.addTestMetric("key", "value");
        }

        @MyAnnotation
        @org.junit.Test
        public void testPass6() {
            metrics.addTestMetric("key2", "value2");
            if (mOption) {
                metrics.addTestMetric("junit4-option", "true");
            }
            if (!mapOption.isEmpty()) {
                metrics.addTestMetric("map-option", mapOption.values().toString());
            }
        }
    }

    /**
     * Test class, we have to annotate with full org.junit.Test to avoid name collision in import.
     */
    @MyAnnotation
    @RunWith(DeviceJUnit4ClassRunner.class)
    public static class Junit4TestLogClass {

        public Junit4TestLogClass() {}

        @Rule public TestLogData logs = new TestLogData();

        @org.junit.Test
        public void testPass1() {
            ByteArrayInputStreamSource source = new ByteArrayInputStreamSource("test".getBytes());
            logs.addTestLog("TEST", LogDataType.TEXT, source);
            // Always cancel streams.
            StreamUtil.cancel(source);
        }

        @org.junit.Test
        public void testPass2() {
            ByteArrayInputStreamSource source = new ByteArrayInputStreamSource("test2".getBytes());
            logs.addTestLog("TEST2", LogDataType.TEXT, source);
            // Always cancel streams.
            StreamUtil.cancel(source);
        }
    }

    /**
     * Test class, we have to annotate with full org.junit.Test to avoid name collision in import.
     * And with one test marked as Ignored
     */
    @RunWith(DeviceJUnit4ClassRunner.class)
    public static class Junit4TestClassWithIgnore implements IDeviceTest {
        private ITestDevice mDevice;

        public Junit4TestClassWithIgnore() {}

        @BeforeClassWithInfo
        public static void beforeClassWithDevice(TestInformation testInfo) {
            assertNotNull(testInfo);
            assertNotNull(testInfo.getDevice());
            testInfo.properties().put("Junit4TestClassWithIgnore:test-prop", "test");
        }

        @AfterClassWithInfo
        public static void afterClassWithDevice(TestInformation testInfo) {
            assertNotNull(testInfo);
            assertNotNull(testInfo.getDevice());
            assertEquals("test", testInfo.properties().get("Junit4TestClassWithIgnore:test-prop"));
        }

        @org.junit.Test
        public void testPass5() {}

        @Ignore
        @org.junit.Test
        public void testPass6() {}

        @Override
        public void setDevice(ITestDevice device) {
            mDevice = device;
        }

        @Override
        public ITestDevice getDevice() {
            return mDevice;
        }
    }

    /** Test Class completely ignored */
    @Ignore
    @RunWith(JUnit4.class)
    public static class Junit4IgnoredClass {
        @org.junit.Test
        public void testPass() {}

        @org.junit.Test
        public void testPass2() {}
    }

    /**
     * Test class that run a test throwing an {@link AssumptionViolatedException} which should be
     * handled as the testAssumptionFailure.
     */
    @RunWith(DeviceJUnit4ClassRunner.class)
    public static class JUnit4TestClassAssume {

        @org.junit.Test
        public void testPass5() {
            Assume.assumeTrue(false);
        }
    }

    @RunWith(DeviceJUnit4ClassRunner.class)
    public static class JUnit4TestClassAssumeInStatic {

        @BeforeClass
        public static void setupClass() {
            Assume.assumeTrue(false);
        }

        @org.junit.Test
        public void testPass5() {}
    }

    @RunWith(DeviceJUnit4ClassRunner.class)
    public static class JUnit4TestClassAssumeInStaticAfter {

        @AfterClass
        public static void afterClass() {
            Assume.assumeTrue(false);
        }

        @org.junit.Test
        public void testPass5() {}
    }

    @RunWith(DeviceJUnit4ClassRunner.class)
    public static class JUnit4TestClassMultiException {

        @org.junit.Test
        public void testPass5() {
            Assume.assumeTrue(false);
        }

        @After
        public void tearDown() {
            Assert.assertTrue(false);
        }
    }

    @RunWith(DeviceJUnit4ClassRunner.class)
    public static class JUnit4TestClassMultiExceptionDnae {

        @org.junit.Test
        public void testPass5() {
            Assume.assumeTrue(false);
        }

        @After
        public void tearDown() throws Exception {
            throw new DeviceNotAvailableException(
                    "dnae", "serial", DeviceErrorIdentifier.DEVICE_UNAVAILABLE);
        }
    }

    @RunWith(DeviceSuite.class)
    @SuiteClasses({
        Junit4TestClass.class,
        SuccessTestCase.class,
    })
    public class Junit4SuiteClass {}

    @RunWith(Suite.class)
    @SuiteClasses({
        Junit4TestClass.class,
        Junit4IgnoredClass.class,
    })
    public class Junit4SuiteClassWithIgnored {}

    @RunWith(DeviceSuite.class)
    @SuiteClasses({
        Junit4TestClassWithIgnore.class,
        Junit4TestLogClass.class,
    })
    public class Junit4SuiteClassWithAnnotation {}

    /**
     * JUnit4 runner that implements {@link ISetOptionReceiver} but does not actually have the
     * set-option.
     */
    public static class InvalidJunit4Runner extends BlockJUnit4ClassRunner
            implements ISetOptionReceiver {
        public InvalidJunit4Runner(Class<?> klass) throws InitializationError {
            super(klass);
        }
    }

    @RunWith(InvalidJunit4Runner.class)
    public static class Junit4RegularClass {
        @Option(name = "option")
        private String mOption = null;

        @org.junit.Test
        public void testPass() {}
    }

    /** Malformed on purpose test class. */
    public static class Junit4MalformedTestClass {
        public Junit4MalformedTestClass() {}

        @Before
        protected void setUp() {
            // @Before should be on a public method.
        }

        @org.junit.Test
        public void testPass() {}
    }

    /** Simple Annotation class for testing */
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MyAnnotation {}

    /** Simple Annotation class for testing */
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MyAnnotation2 {}

    /** Simple Annotation class for testing */
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MyAnnotation3 {}

    public static class SuccessTestSuite extends TestSuite {
        public SuccessTestSuite() {
            super(SuccessTestCase.class);
        }
    }

    public static class SuccessHierarchySuite extends TestSuite {
        public SuccessHierarchySuite() {
            super();
            addTestSuite(SuccessTestCase.class);
        }
    }

    public static class SuccessDeviceTest extends DeviceTestCase {

        @Option(name = "option")
        public String mOption = null;

        public SuccessDeviceTest() {
            super();
        }

        public void testPass() {
            assertNotNull(getDevice());
            if (mOption != null) {
                addTestMetric("option", mOption);
            }
        }
    }

    @MyAnnotation
    public static class SuccessDeviceTest2 extends DeviceTestCase {
        public SuccessDeviceTest2() {
            super();
        }

        @MyAnnotation3
        public void testPass1() {
            assertNotNull(getDevice());
        }

        public void testPass2() {
            assertNotNull(getDevice());
        }
    }

    @MyAnnotation
    public static class InheritedDeviceTest3 extends SuccessDeviceTest2 {
        public InheritedDeviceTest3() {
            super();
        }

        @Override
        public void testPass1() {
            super.testPass1();
        }

        @MyAnnotation3
        public void testPass3() {}
    }

    public static class TestRemoteNotCollector implements IDeviceTest, IRemoteTest {
        @Override
        public void run(TestInformation testInfo, ITestInvocationListener listener)
                throws DeviceNotAvailableException {}

        @Override
        public void setDevice(ITestDevice device) {}

        @Override
        public ITestDevice getDevice() {
            return null;
        }
    }

    /** Non-public class; should fail to load. */
    private static class PrivateTest extends TestCase {
        @SuppressWarnings("unused")
        public void testPrivate() {}
    }

    /** class without default constructor; should fail to load */
    public static class NoConstructorTest extends TestCase {
        public NoConstructorTest(String name) {
            super(name);
        }

        public void testNoConstructor() {}
    }

    public static class OptionEscapeColonTestCase extends TestCase {
        @Option(name = "gcs-bucket-file")
        private File mGcsBucketFile = null;

        @Option(name = "hello")
        private String mHelloWorld = null;

        @Option(name = "foobar")
        private String mFoobar = null;

        @Rule public TestMetrics metrics = new TestMetrics();

        public OptionEscapeColonTestCase() {}

        public OptionEscapeColonTestCase(String name) {
            super(name);
        }

        public void testGcsBucket() {
            assertTrue(
                    "Expect a GCS bucket file: "
                            + (mGcsBucketFile != null ? mGcsBucketFile.toString() : "null"),
                    "/downloaded/somewhere".equals(mGcsBucketFile.getPath()));
            metrics.addTestMetric("gcs-bucket-file", mGcsBucketFile.toURI().toString());
        }

        public void testEscapeStrings() {
            assertTrue(mHelloWorld != null && mFoobar != null);
            assertTrue(
                    "Expects 'hello' value to be 'hello:world'", mHelloWorld.equals("hello:world"));
            assertTrue("Expects 'foobar' value to be 'baz:qux=wap'", mFoobar.equals("baz:qux=wap"));

            metrics.addTestMetric("hello", mHelloWorld);
            metrics.addTestMetric("foobar", mFoobar);
        }
    }

    @RunWith(Parameterized.class)
    public static class ParameterizedTest {}

    public static class TestableHostTest extends HostTest {

        private IRemoteFileResolver mRemoteFileResolver;

        public TestableHostTest() {
            mRemoteFileResolver = null;
        }

        public TestableHostTest(IRemoteFileResolver remoteFileResolver) {
            mRemoteFileResolver = remoteFileResolver;
        }

        @Override
        protected DynamicRemoteFileResolver createResolver() {
            FileResolverLoader resolverLoader =
                    new FileResolverLoader() {
                        @Override
                        public IRemoteFileResolver load(String scheme, Map<String, String> config) {
                            return ImmutableMap.of(
                                            GcsRemoteFileResolver.PROTOCOL, mRemoteFileResolver)
                                    .get(scheme);
                        }
                    };
            return new DynamicRemoteFileResolver(resolverLoader);
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mMockResolver = Mockito.mock(IRemoteFileResolver.class);
        mHostTest = new TestableHostTest(mMockResolver);

        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device", mMockDevice);
        context.addDeviceBuildInfo("device", mMockBuildInfo);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
        mHostTest.setDevice(mMockDevice);
        mHostTest.setBuild(mMockBuildInfo);
        OptionSetter setter = new OptionSetter(mHostTest);
        // Disable pretty logging for testing
        setter.setOptionValue("enable-pretty-logs", "false");
    }

    /**
     * Test success case for {@link HostTest#run(TestInformation, ITestInvocationListener)}, where
     * test to run is a {@link TestCase}.
     */
    @org.junit.Test
    public void testRun_testcase() throws Exception {
        mHostTest.setClassName(SuccessTestCase.class.getName());
        TestDescription test1 = new TestDescription(SuccessTestCase.class.getName(), "testPass");
        TestDescription test2 = new TestDescription(SuccessTestCase.class.getName(), "testPass2");

        mHostTest.run(mTestInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(2));
        verify(mListener).testStarted(Mockito.eq(test1));
        verify(mListener).testEnded(Mockito.eq(test1), (HashMap<String, Metric>) Mockito.any());
        verify(mListener).testStarted(Mockito.eq(test2));
        verify(mListener).testEnded(Mockito.eq(test2), (HashMap<String, Metric>) Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /**
     * Test success case for {@link HostTest#run(TestInformation, ITestInvocationListener)}, where
     * test to run is a {@link MetricTestCase}.
     */
    @org.junit.Test
    public void testRun_MetricTestCase() throws Exception {
        mHostTest.setClassName(TestMetricTestCase.class.getName());
        TestDescription test1 = new TestDescription(TestMetricTestCase.class.getName(), "testPass");
        TestDescription test2 =
                new TestDescription(TestMetricTestCase.class.getName(), "testPass2");

        mHostTest.run(mTestInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(2));
        verify(mListener).testStarted(Mockito.eq(test1));
        // test1 should only have its metrics
        Map<String, String> metric1 = new HashMap<>();
        metric1.put("key1", "metric1");
        verify(mListener).testEnded(test1, TfMetricProtoUtil.upgradeConvert(metric1));
        verify(mListener).testStarted(Mockito.eq(test2));
        // test2 should only have its metrics
        Map<String, String> metric2 = new HashMap<>();
        metric2.put("key2", "metric2");
        verify(mListener).testEnded(test2, TfMetricProtoUtil.upgradeConvert(metric2));
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /**
     * Test a case where a test use {@link MetricTestCase#addTestLog(String, LogDataType,
     * InputStreamSource)} in order to log data for all the reporters to know about.
     */
    @org.junit.Test
    public void testRun_LogMetricTestCase() throws Exception {
        mHostTest.setClassName(LogMetricTestCase.class.getName());
        TestDescription test1 = new TestDescription(LogMetricTestCase.class.getName(), "testPass");
        TestDescription test2 = new TestDescription(LogMetricTestCase.class.getName(), "testPass2");

        mHostTest.run(mTestInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(2));
        verify(mListener).testStarted(Mockito.eq(test1));
        // test1 should only have its metrics
        verify(mListener).testEnded(test1, new HashMap<String, Metric>());
        verify(mListener).testStarted(Mockito.eq(test2));
        verify(mListener)
                .testLog(Mockito.eq("test2_log"), Mockito.eq(LogDataType.TEXT), Mockito.any());
        // test2 should only have its metrics
        Map<String, String> metric2 = new HashMap<>();
        metric2.put("key2", "metric2");
        verify(mListener).testEnded(test2, TfMetricProtoUtil.upgradeConvert(metric2));
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /**
     * Test success case for {@link HostTest#run(TestInformation, ITestInvocationListener)}, where
     * test to run is a {@link MetricTestCase} and where an option is set to get extra metrics.
     */
    @org.junit.Test
    public void testRun_MetricTestCase_withOption() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("set-option", "test-option:test");
        // List option can take several values.
        setter.setOptionValue("set-option", "list-option:test1");
        setter.setOptionValue("set-option", "list-option:test2");
        // Map option
        setter.setOptionValue("set-option", "map-option:key=value");
        mHostTest.setClassName(TestMetricTestCase.class.getName());
        TestDescription test1 = new TestDescription(TestMetricTestCase.class.getName(), "testPass");
        TestDescription test2 =
                new TestDescription(TestMetricTestCase.class.getName(), "testPass2");

        mHostTest.run(mTestInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(2));
        verify(mListener).testStarted(Mockito.eq(test1));
        // test1 should only have its metrics
        Map<String, String> metric1 = new HashMap<>();
        metric1.put("key1", "metric1");
        verify(mListener).testEnded(test1, TfMetricProtoUtil.upgradeConvert(metric1));
        verify(mListener).testStarted(Mockito.eq(test2));
        // test2 should only have its metrics
        Map<String, String> metric2 = new HashMap<>();
        metric2.put("key2", "metric2");
        metric2.put("test-option", "test");
        metric2.put("list-option", "[test1, test2]");
        metric2.put("map-option", "{key=value}");
        verify(mListener).testEnded(test2, TfMetricProtoUtil.upgradeConvert(metric2));
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /**
     * Test success case for {@link HostTest#run(TestInformation, ITestInvocationListener)}, where
     * test to run is a {@link TestSuite}.
     */
    @org.junit.Test
    public void testRun_testSuite() throws Exception {
        mHostTest.setClassName(SuccessTestSuite.class.getName());
        TestDescription test1 = new TestDescription(SuccessTestCase.class.getName(), "testPass");
        TestDescription test2 = new TestDescription(SuccessTestCase.class.getName(), "testPass2");

        mHostTest.run(mTestInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(2));
        verify(mListener).testStarted(Mockito.eq(test1));
        verify(mListener).testEnded(Mockito.eq(test1), (HashMap<String, Metric>) Mockito.any());
        verify(mListener).testStarted(Mockito.eq(test2));
        verify(mListener).testEnded(Mockito.eq(test2), (HashMap<String, Metric>) Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /**
     * Test success case for {@link HostTest#run(TestInformation, ITestInvocationListener)}, where
     * test to run is a {@link TestSuite} and has dynamic options.
     */
    @org.junit.Test
    public void testRun_junit3TestSuite_dynamicOptions() throws Exception {
        doReturn(new ResolvedFile(new File("/downloaded/somewhere")))
                .when(mMockResolver)
                .resolveRemoteFile((RemoteFileResolverArgs) Mockito.any());
        mHostTest.setClassName(DynamicTestCase.class.getName());

        mHostTest.run(mTestInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(1));
        TestDescription test1 = new TestDescription(DynamicTestCase.class.getName(), "testPass");
        verify(mListener).testStarted(Mockito.eq(test1));
        verify(mListener).testEnded(Mockito.eq(test1), (HashMap<String, Metric>) Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /**
     * Test success case for {@link HostTest#run(TestInformation, ITestInvocationListener)}, where
     * test to run is a hierarchy of {@link TestSuite}s.
     */
    @org.junit.Test
    public void testRun_testHierarchySuite() throws Exception {
        mHostTest.setClassName(SuccessHierarchySuite.class.getName());

        mHostTest.run(mTestInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(2));
        TestDescription test1 = new TestDescription(SuccessTestCase.class.getName(), "testPass");
        verify(mListener).testStarted(Mockito.eq(test1));
        verify(mListener).testEnded(Mockito.eq(test1), (HashMap<String, Metric>) Mockito.any());
        TestDescription test2 = new TestDescription(SuccessTestCase.class.getName(), "testPass2");
        verify(mListener).testStarted(Mockito.eq(test2));
        verify(mListener).testEnded(Mockito.eq(test2), (HashMap<String, Metric>) Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /**
     * Test success case for {@link HostTest#run(TestInformation, ITestInvocationListener)}, where
     * test to run is a {@link TestCase} and methodName is set.
     */
    @org.junit.Test
    public void testRun_testMethod() throws Exception {
        mHostTest.setClassName(SuccessTestCase.class.getName());
        mHostTest.setMethodName("testPass");

        mHostTest.run(mTestInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(1));
        TestDescription test1 = new TestDescription(SuccessTestCase.class.getName(), "testPass");
        verify(mListener).testStarted(Mockito.eq(test1));
        verify(mListener).testEnded(Mockito.eq(test1), (HashMap<String, Metric>) Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /**
     * Test for {@link HostTest#run(TestInformation, ITestInvocationListener)}, where className is
     * not set.
     */
    @org.junit.Test
    public void testRun_missingClass() throws Exception {
        mHostTest.run(mTestInfo, mListener);

        verify(mListener).testRunStarted(TestableHostTest.class.getCanonicalName(), 0);
        ArgumentCaptor<FailureDescription> captured =
                ArgumentCaptor.forClass(FailureDescription.class);
        verify(mListener).testRunFailed(captured.capture());
        verify(mListener).testRunEnded(0L, new HashMap<String, Metric>());
        assertTrue(
                captured.getValue()
                        .getErrorMessage()
                        .contains("No '--class' option was specified."));
    }

    /**
     * Test for {@link HostTest#run(TestInformation, ITestInvocationListener)}, for an invalid
     * class.
     */
    @org.junit.Test
    public void testRun_invalidClass() throws Exception {
        mHostTest.setClassName("foo");

        mHostTest.run(mTestInfo, mListener);

        verify(mListener).testRunStarted(TestableHostTest.class.getCanonicalName(), 0);
        ArgumentCaptor<FailureDescription> captured =
                ArgumentCaptor.forClass(FailureDescription.class);
        verify(mListener).testRunFailed(captured.capture());
        verify(mListener).testRunEnded(0L, new HashMap<String, Metric>());
        assertTrue(captured.getValue().getErrorMessage().contains("Could not load Test class foo"));
    }

    /**
     * Test for {@link HostTest#run(TestInformation, ITestInvocationListener)}, for a valid class
     * that is not a {@link Test}.
     */
    @org.junit.Test
    public void testRun_notTestClass() throws Exception {
        try {
            mHostTest.setClassName(String.class.getName());
            mHostTest.run(mTestInfo, mListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test for {@link HostTest#run(TestInformation, ITestInvocationListener)}, for a private class.
     */
    @org.junit.Test
    public void testRun_privateClass() throws Exception {
        try {
            mHostTest.setClassName(PrivateTest.class.getName());
            mHostTest.run(mTestInfo, mListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test for {@link HostTest#run(TestInformation, ITestInvocationListener)}, for a test class
     * with no default constructor.
     */
    @org.junit.Test
    public void testRun_noConstructorClass() throws Exception {
        try {
            mHostTest.setClassName(NoConstructorTest.class.getName());
            mHostTest.run(mTestInfo, mListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test for {@link HostTest#run(TestInformation, ITestInvocationListener)}, for multiple test
     * classes.
     */
    @org.junit.Test
    public void testRun_multipleClass() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", SuccessTestCase.class.getName());
        setter.setOptionValue("class", AnotherTestCase.class.getName());

        mHostTest.run(mTestInfo, mListener);

        verify(mListener, times(2)).testRunStarted((String) Mockito.any(), Mockito.eq(2));
        TestDescription test1 = new TestDescription(SuccessTestCase.class.getName(), "testPass");
        verify(mListener).testStarted(Mockito.eq(test1));
        verify(mListener).testEnded(Mockito.eq(test1), (HashMap<String, Metric>) Mockito.any());
        TestDescription test2 = new TestDescription(SuccessTestCase.class.getName(), "testPass2");
        verify(mListener).testStarted(Mockito.eq(test2));
        verify(mListener).testEnded(Mockito.eq(test2), (HashMap<String, Metric>) Mockito.any());
        verify(mListener, times(2))
                .testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
        TestDescription test3 = new TestDescription(AnotherTestCase.class.getName(), "testPass3");
        verify(mListener).testStarted(Mockito.eq(test3));
        verify(mListener).testEnded(Mockito.eq(test3), (HashMap<String, Metric>) Mockito.any());
        TestDescription test4 = new TestDescription(AnotherTestCase.class.getName(), "testPass4");
        verify(mListener).testStarted(Mockito.eq(test4));
        verify(mListener).testEnded(Mockito.eq(test4), (HashMap<String, Metric>) Mockito.any());
    }

    /**
     * Test for {@link HostTest#run(TestInformation, ITestInvocationListener)}, for multiple test
     * classes with a method name.
     */
    @org.junit.Test
    public void testRun_multipleClassAndMethodName() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", SuccessTestCase.class.getName());
        setter.setOptionValue("class", AnotherTestCase.class.getName());
        mHostTest.setMethodName("testPass3");

        mHostTest.run(mTestInfo, mListener);

        verify(mListener).testRunStarted(TestableHostTest.class.getCanonicalName(), 0);
        ArgumentCaptor<FailureDescription> captured =
                ArgumentCaptor.forClass(FailureDescription.class);
        verify(mListener).testRunFailed(captured.capture());
        verify(mListener).testRunEnded(0L, new HashMap<String, Metric>());
        assertTrue(
                captured.getValue()
                        .getErrorMessage()
                        .contains(
                                "'--method' only supports one '--class' name. Multiple were"
                                        + " given:"));
    }

    /**
     * Test for {@link HostTest#run(TestInformation, ITestInvocationListener)}, for a {@link
     * IDeviceTest}.
     */
    @org.junit.Test
    public void testRun_deviceTest() throws Exception {
        final ITestDevice device = mock(ITestDevice.class);
        mHostTest.setClassName(SuccessDeviceTest.class.getName());
        mHostTest.setDevice(device);
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("set-option", "option:value");

        mHostTest.run(mTestInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(1));
        TestDescription test1 = new TestDescription(SuccessDeviceTest.class.getName(), "testPass");
        verify(mListener).testStarted(Mockito.eq(test1));
        Map<String, String> expected = new HashMap<>();
        expected.put("option", "value");
        verify(mListener)
                .testEnded(
                        Mockito.eq(test1), Mockito.eq(TfMetricProtoUtil.upgradeConvert(expected)));
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /**
     * Test for {@link HostTest#run(TestInformation, ITestInvocationListener)}, for a {@link
     * IDeviceTest} where no device has been provided.
     */
    @org.junit.Test
    public void testRun_missingDevice() throws Exception {
        mHostTest.setClassName(SuccessDeviceTest.class.getName());
        mHostTest.setDevice(null);
        try {
            mHostTest.run(mTestInfo, mListener);
            fail("expected IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /** Test for {@link HostTest#countTestCases()} */
    @org.junit.Test
    public void testCountTestCases() throws Exception {
        mHostTest.setClassName(SuccessTestCase.class.getName());
        assertEquals("Incorrect test case count", 2, mHostTest.countTestCases());
    }

    /** Test for {@link HostTest#countTestCases()} */
    @org.junit.Test
    public void testCountTestCases_dirtyCount() throws Exception {
        mHostTest.setClassName(SuccessTestCase.class.getName());
        assertEquals("Incorrect test case count", 2, mHostTest.countTestCases());
        TestDescription test1 = new TestDescription(SuccessTestCase.class.getName(), "testPass");
        mHostTest.addIncludeFilter(test1.toString());
        assertEquals("Incorrect test case count", 1, mHostTest.countTestCases());
    }

    /** Test for {@link HostTest#countTestCases()} with filtering on JUnit4 tests */
    @org.junit.Test
    public void testCountTestCasesJUnit4WithFiltering() throws Exception {
        mHostTest.setClassName(Junit4TestClass.class.getName());
        mHostTest.addIncludeFilter(
                "com.android.tradefed.testtype.HostTestTest$Junit4TestClass#testPass5");
        assertEquals("Incorrect test case count", 1, mHostTest.countTestCases());
    }

    /**
     * Test for {@link HostTest#countTestCases()}, if JUnit4 test class is malformed it will count
     * as 1 in the total number of tests.
     */
    @org.junit.Test
    public void testCountTestCasesJUnit4Malformed() throws Exception {
        mHostTest.setClassName(Junit4MalformedTestClass.class.getName());
        assertEquals("Incorrect test case count", 1, mHostTest.countTestCases());
    }

    /**
     * Test for {@link HostTest#countTestCases()} with filtering on JUnit4 tests and no test remain.
     */
    @org.junit.Test
    public void testCountTestCasesJUnit4WithFiltering_no_more_tests() throws Exception {
        mHostTest.setClassName(Junit4TestClass.class.getName());
        mHostTest.addExcludeFilter(
                "com.android.tradefed.testtype.HostTestTest$Junit4TestClass#testPass5");
        mHostTest.addExcludeFilter(
                "com.android.tradefed.testtype.HostTestTest$Junit4TestClass#testPass6");
        assertEquals("Incorrect test case count", 0, mHostTest.countTestCases());
    }

    /** Test for {@link HostTest#countTestCases()} with tests of varying JUnit versions */
    @org.junit.Test
    public void testCountTestCasesJUnitVersionMixed() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", SuccessTestCase.class.getName()); // 2 tests
        setter.setOptionValue("class", Junit4TestClass.class.getName()); // 2 tests
        setter.setOptionValue("class", Junit4SuiteClass.class.getName()); // 4 tests
        assertEquals("Incorrect test case count", 8, mHostTest.countTestCases());
    }

    /**
     * Test for {@link HostTest#countTestCases()} with filtering on tests of varying JUnit versions
     */
    @org.junit.Test
    public void testCountTestCasesJUnitVersionMixedWithFiltering() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", SuccessTestCase.class.getName()); // 2 tests
        setter.setOptionValue("class", Junit4TestClass.class.getName()); // 2 tests
        mHostTest.addIncludeFilter(
                "com.android.tradefed.testtype.HostTestTest$SuccessTestCase#testPass");
        mHostTest.addIncludeFilter(
                "com.android.tradefed.testtype.HostTestTest$Junit4TestClass#testPass5");
        assertEquals("Incorrect test case count", 2, mHostTest.countTestCases());
    }

    /** Test for {@link HostTest#countTestCases()} with annotation filtering */
    @org.junit.Test
    public void testCountTestCasesAnnotationFiltering() throws Exception {
        mHostTest.setClassName(SuccessTestCase.class.getName());
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation2");
        assertEquals("Incorrect test case count", 1, mHostTest.countTestCases());
    }

    /**
     * Test success case for {@link HostTest#run(TestInformation, ITestInvocationListener)}, where
     * test to run is a {@link TestCase} with annotation filtering.
     */
    @org.junit.Test
    public void testRun_testcaseAnnotationFiltering() throws Exception {
        mHostTest.setClassName(SuccessTestCase.class.getName());
        mHostTest.addIncludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation");

        mHostTest.run(mTestInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(2));
        TestDescription test1 = new TestDescription(SuccessTestCase.class.getName(), "testPass");
        verify(mListener).testStarted(Mockito.eq(test1));
        verify(mListener).testEnded(Mockito.eq(test1), (HashMap<String, Metric>) Mockito.any());
        TestDescription test2 = new TestDescription(SuccessTestCase.class.getName(), "testPass2");
        verify(mListener).testStarted(Mockito.eq(test2));
        verify(mListener).testEnded(Mockito.eq(test2), (HashMap<String, Metric>) Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /**
     * Test success case for {@link HostTest#run(TestInformation, ITestInvocationListener)}, where
     * test to run is a {@link TestCase} with notAnnotationFiltering
     */
    @org.junit.Test
    public void testRun_testcaseNotAnnotationFiltering() throws Exception {
        mHostTest.setClassName(SuccessTestCase.class.getName());
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation2");

        mHostTest.run(mTestInfo, mListener);

        // Only test1 will run, test2 should be filtered out.
        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(1));
        TestDescription test1 = new TestDescription(SuccessTestCase.class.getName(), "testPass");
        verify(mListener).testStarted(Mockito.eq(test1));
        verify(mListener).testEnded(Mockito.eq(test1), (HashMap<String, Metric>) Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /**
     * Test success case for {@link HostTest#run(TestInformation, ITestInvocationListener)}, where
     * test to run is a {@link TestCase} with both annotation filtering.
     */
    @org.junit.Test
    public void testRun_testcaseBothAnnotationFiltering() throws Exception {
        mHostTest.setClassName(AnotherTestCase.class.getName());
        mHostTest.addIncludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation");
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation2");

        mHostTest.run(mTestInfo, mListener);

        // Only a test with MyAnnotation and Without MyAnnotation2 will run. Here testPass4
        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(1));
        TestDescription test4 = new TestDescription(AnotherTestCase.class.getName(), "testPass4");
        verify(mListener).testStarted(Mockito.eq(test4));
        verify(mListener).testEnded(Mockito.eq(test4), (HashMap<String, Metric>) Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /**
     * Test success case for {@link HostTest#run(TestInformation, ITestInvocationListener)}, where
     * test to run is a {@link TestCase} with multiple include annotation, test must contains them
     * all.
     */
    @org.junit.Test
    public void testRun_testcaseMultiInclude() throws Exception {
        mHostTest.setClassName(AnotherTestCase.class.getName());
        mHostTest.addIncludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation");
        mHostTest.addIncludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation2");

        mHostTest.run(mTestInfo, mListener);

        // Only a test with MyAnnotation and with MyAnnotation2 will run. Here testPass3
        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(1));
        TestDescription test3 = new TestDescription(AnotherTestCase.class.getName(), "testPass3");
        verify(mListener).testStarted(Mockito.eq(test3));
        verify(mListener).testEnded(Mockito.eq(test3), (HashMap<String, Metric>) Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /**
     * Test success case for {@link HostTest#shouldTestRun(AnnotatedElement)}, where a class is
     * properly annotated to run.
     */
    @org.junit.Test
    public void testRun_shouldTestRun_Success() throws Exception {
        mHostTest.addIncludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation");
        assertTrue(mHostTest.shouldTestRun(SuccessTestCase.class));
    }

    /**
     * Test success case for {@link HostTest#shouldTestRun(AnnotatedElement)}, where a class is
     * properly annotated to run with multiple annotation expected.
     */
    @org.junit.Test
    public void testRun_shouldTestRunMulti_Success() throws Exception {
        mHostTest.addIncludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation");
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation2");
        assertTrue(mHostTest.shouldTestRun(AnotherTestCase.class));
    }

    /**
     * Test case for {@link HostTest#shouldTestRun(AnnotatedElement)}, where a class is properly
     * annotated to be filtered.
     */
    @org.junit.Test
    public void testRun_shouldNotRun() throws Exception {
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation");
        assertFalse(mHostTest.shouldTestRun(SuccessTestCase.class));
    }

    /**
     * Test case for {@link HostTest#shouldTestRun(AnnotatedElement)}, where a class is properly
     * annotated to be filtered because one of its two annotations is part of the exclude.
     */
    @org.junit.Test
    public void testRun_shouldNotRunMulti() throws Exception {
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation");
        assertFalse(mHostTest.shouldTestRun(SuccessTestCase.class));
        mHostTest = new HostTest();
        // If only the other annotation is excluded.
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation3");

        assertFalse(mHostTest.shouldTestRun(SuccessTestCase.class));
    }

    /**
     * Test success case for {@link HostTest#shouldTestRun(AnnotatedElement)}, where a class is
     * annotated with a different annotation from the exclude filter.
     */
    @org.junit.Test
    public void testRun_shouldRun_exclude() throws Exception {
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation2");
        assertTrue(mHostTest.shouldTestRun(SuccessTestCase.class));
    }

    /**
     * Test success case for {@link HostTest#run(TestInformation, ITestInvocationListener)}, where
     * test to run is a {@link TestCase} with annotation filtering.
     */
    @org.junit.Test
    public void testRun_testcaseCollectMode() throws Exception {
        mHostTest.setClassName(SuccessTestCase.class.getName());
        mHostTest.setCollectTestsOnly(true);

        mHostTest.run(mTestInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(2));
        verify(mListener, times(2)).testStarted((TestDescription) Mockito.any());
        verify(mListener, times(2))
                .testEnded(
                        (TestDescription) Mockito.any(), (HashMap<String, Metric>) Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /**
     * Test success case for {@link HostTest#run(TestInformation, ITestInvocationListener)}, where
     * the {@link IRemoteTest} does not implements {@link ITestCollector}
     */
    @org.junit.Test
    public void testRun_testcaseCollectMode_IRemotedevice() throws Exception {
        final ITestDevice device = mock(ITestDevice.class);
        mHostTest.setClassName(TestRemoteNotCollector.class.getName());
        mHostTest.setDevice(device);
        mHostTest.setCollectTestsOnly(true);

        try {
            mHostTest.run(mTestInfo, mListener);
        } catch (IllegalArgumentException expected) {
            return;
        }
        fail("HostTest run() should have thrown an exception.");
    }

    /**
     * Test for {@link HostTest#run(TestInformation, ITestInvocationListener)}, for test with Junit4
     * style.
     */
    @org.junit.Test
    public void testRun_junit4style() throws Exception {
        mHostTest.setClassName(Junit4TestClass.class.getName());
        TestDescription test1 = new TestDescription(Junit4TestClass.class.getName(), "testPass5");
        TestDescription test2 = new TestDescription(Junit4TestClass.class.getName(), "testPass6");

        mHostTest.run(mTestInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(2));
        verify(mListener).testStarted(Mockito.eq(test1));
        Map<String, String> metrics = new HashMap<>();
        metrics.put("key", "value");
        verify(mListener).testEnded(test1, TfMetricProtoUtil.upgradeConvert(metrics));
        verify(mListener).testStarted(Mockito.eq(test2));
        // test cases do not share metrics.
        Map<String, String> metrics2 = new HashMap<>();
        metrics2.put("key2", "value2");
        verify(mListener)
                .testEnded(
                        Mockito.eq(test2), Mockito.eq(TfMetricProtoUtil.upgradeConvert(metrics2)));
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /**
     * Test for {@link HostTest#run(TestInformation, ITestInvocationListener)}, for test with Junit4
     * style and handling of @Ignored.
     */
    @org.junit.Test
    public void testRun_junit4style_ignored() throws Exception {
        mHostTest.setClassName(Junit4TestClassWithIgnore.class.getName());

        mHostTest.run(mTestInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(2));
        TestDescription test1 =
                new TestDescription(Junit4TestClassWithIgnore.class.getName(), "testPass5");
        verify(mListener).testStarted(Mockito.eq(test1));
        verify(mListener).testEnded(Mockito.eq(test1), (HashMap<String, Metric>) Mockito.any());
        TestDescription test2 =
                new TestDescription(Junit4TestClassWithIgnore.class.getName(), "testPass6");
        verify(mListener).testStarted(Mockito.eq(test2));
        verify(mListener).testIgnored(Mockito.eq(test2));
        verify(mListener).testEnded(Mockito.eq(test2), (HashMap<String, Metric>) Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /**
     * Test for {@link HostTest#run(TestInformation, ITestInvocationListener)}, for test with Junit4
     * style and handling of @Ignored on the class.
     */
    @org.junit.Test
    public void testRun_junit4style_class_ignored() throws Exception {
        mHostTest.setClassName(Junit4IgnoredClass.class.getName());
        assertEquals(1, mHostTest.countTestCases());

        mHostTest.run(mTestInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(1));
        TestDescription test1 = new TestDescription(Junit4IgnoredClass.class.getName(), "No Tests");
        verify(mListener).testStarted(Mockito.eq(test1));
        verify(mListener).testIgnored(Mockito.eq(test1));
        verify(mListener).testEnded(Mockito.eq(test1), (HashMap<String, Metric>) Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /**
     * Test for {@link HostTest#run(TestInformation, ITestInvocationListener)}, for test with Junit4
     * style and handling of @Ignored on the class and collect-tests-only.
     */
    @org.junit.Test
    public void testRun_junit4style_class_ignored_collect() throws Exception {
        mHostTest.setCollectTestsOnly(true);
        mHostTest.setClassName(Junit4IgnoredClass.class.getName());
        assertEquals(1, mHostTest.countTestCases());

        mHostTest.run(mTestInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(1));
        TestDescription test1 = new TestDescription(Junit4IgnoredClass.class.getName(), "No Tests");
        verify(mListener).testStarted(Mockito.eq(test1));
        verify(mListener).testIgnored(Mockito.eq(test1));
        verify(mListener).testEnded(Mockito.eq(test1), (HashMap<String, Metric>) Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /**
     * Test for {@link HostTest#run(TestInformation, ITestInvocationListener)}, for test with Junit4
     * style and handling of Assume.
     */
    @org.junit.Test
    public void testRun_junit4style_assumeFailure() throws Exception {
        mHostTest.setClassName(JUnit4TestClassAssume.class.getName());

        mHostTest.run(mTestInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(1));
        TestDescription test1 =
                new TestDescription(JUnit4TestClassAssume.class.getName(), "testPass5");
        verify(mListener).testStarted(Mockito.eq(test1));
        verify(mListener).testAssumptionFailure(Mockito.eq(test1), (String) Mockito.any());
        verify(mListener).testEnded(Mockito.eq(test1), (HashMap<String, Metric>) Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    @org.junit.Test
    public void testRun_junit4style_assumeFailure_static() throws Exception {
        mHostTest.setClassName(JUnit4TestClassAssumeInStatic.class.getName());

        mHostTest.run(mTestInfo, mListener);

        InOrder inOrder = Mockito.inOrder(mListener);
        inOrder.verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(1));
        TestDescription test1 =
                new TestDescription(JUnit4TestClassAssumeInStatic.class.getName(), "testPass5");
        inOrder.verify(mListener).testStarted(Mockito.eq(test1));
        inOrder.verify(mListener).testAssumptionFailure(Mockito.eq(test1), (String) Mockito.any());
        inOrder.verify(mListener)
                .testEnded(Mockito.eq(test1), (HashMap<String, Metric>) Mockito.any());
        inOrder.verify(mListener)
                .testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
        inOrder.verifyNoMoreInteractions();
        Mockito.verifyNoMoreInteractions(mListener);
    }

    @org.junit.Test
    public void testRun_junit4style_assumeFailure_after_static() throws Exception {
        mHostTest.setClassName(JUnit4TestClassAssumeInStaticAfter.class.getName());

        mHostTest.run(mTestInfo, mListener);

        InOrder inOrder = Mockito.inOrder(mListener);
        inOrder.verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(1));
        TestDescription test1 =
                new TestDescription(
                        JUnit4TestClassAssumeInStaticAfter.class.getName(), "testPass5");
        inOrder.verify(mListener).testStarted(Mockito.eq(test1));
        inOrder.verify(mListener)
                .testEnded(Mockito.eq(test1), (HashMap<String, Metric>) Mockito.any());
        inOrder.verify(mListener).testRunFailed((String) Mockito.any());
        inOrder.verify(mListener)
                .testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
        inOrder.verifyNoMoreInteractions();
        Mockito.verifyNoMoreInteractions(mListener);
    }

    /**
     * Test for {@link HostTest#run(TestInformation, ITestInvocationListener)}, for test with Junit4
     * style and handling of Multiple exception one from @Test one from @After. Junit replay both as
     * failure.
     */
    @org.junit.Test
    public void testRun_junit4style_multiException() throws Exception {
        mListener = mock(ITestInvocationListener.class);
        mHostTest.setClassName(JUnit4TestClassMultiException.class.getName());

        mHostTest.run(mTestInfo, mListener);

        TestDescription test1 =
                new TestDescription(JUnit4TestClassMultiException.class.getName(), "testPass5");
        InOrder inOrder = Mockito.inOrder(mListener);

        inOrder.verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(1));
        inOrder.verify(mListener).testStarted(Mockito.eq(test1));
        inOrder.verify(mListener)
                .testFailed(
                        Mockito.eq(test1),
                        Mockito.contains("MultipleFailureException, There were 2 errors:"));
        inOrder.verify(mListener)
                .testEnded(Mockito.eq(test1), (HashMap<String, Metric>) Mockito.any());
        inOrder.verify(mListener)
                .testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(1));
        verify(mListener).testStarted(Mockito.eq(test1));
        verify(mListener)
                .testFailed(
                        Mockito.eq(test1),
                        Mockito.contains("MultipleFailureException, There were 2 errors:"));
        verify(mListener).testEnded(Mockito.eq(test1), (HashMap<String, Metric>) Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    @org.junit.Test
    public void testRun_junit4style_multiException_dnae() throws Exception {
        mListener = mock(ITestInvocationListener.class);
        mHostTest.setClassName(JUnit4TestClassMultiExceptionDnae.class.getName());

        try {
            mHostTest.run(mTestInfo, mListener);
            fail("Should have thrown an exception.");
        } catch (DeviceNotAvailableException expected) {
            // Expected
        }

        TestDescription test1 =
                new TestDescription(JUnit4TestClassMultiExceptionDnae.class.getName(), "testPass5");

        InOrder inOrder = Mockito.inOrder(mListener);
        inOrder.verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(1));
        inOrder.verify(mListener).testStarted(Mockito.eq(test1));
        inOrder.verify(mListener)
                .testFailed(
                        Mockito.eq(test1),
                        Mockito.contains("MultipleFailureException, There were 2 errors:"));
        inOrder.verify(mListener)
                .testEnded(Mockito.eq(test1), (HashMap<String, Metric>) Mockito.any());
        inOrder.verify(mListener).testRunFailed((FailureDescription) Mockito.any());
        inOrder.verify(mListener)
                .testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(1));
        verify(mListener).testStarted(Mockito.eq(test1));
        verify(mListener)
                .testFailed(
                        Mockito.eq(test1),
                        Mockito.contains("MultipleFailureException, There were 2 errors:"));
        verify(mListener).testEnded(Mockito.eq(test1), (HashMap<String, Metric>) Mockito.any());
        ArgumentCaptor<FailureDescription> captureRunFailure =
                ArgumentCaptor.forClass(FailureDescription.class);
        verify(mListener).testRunFailed(captureRunFailure.capture());
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
        FailureDescription failure = captureRunFailure.getValue();
        assertTrue(
                failure.getErrorMessage()
                        .startsWith(
                                "com.android.tradefed.device.DeviceNotAvailableException"
                                        + "[DEVICE_UNAVAILABLE|520750|LOST_SYSTEM_UNDER_TEST]: "
                                        + "dnae"));
        assertEquals(FailureStatus.LOST_SYSTEM_UNDER_TEST, failure.getFailureStatus());
    }

    /**
     * Test for {@link HostTest#run(TestInformation, ITestInvocationListener)}, for test with Junit4
     * style and with method filtering. Only run the expected method.
     */
    @org.junit.Test
    public void testRun_junit4_withMethodFilter() throws Exception {
        mHostTest.setClassName(Junit4TestClass.class.getName());
        mHostTest.setMethodName("testPass6");

        mHostTest.run(mTestInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(1));
        TestDescription test2 = new TestDescription(Junit4TestClass.class.getName(), "testPass6");
        verify(mListener).testStarted(Mockito.eq(test2));
        verify(mListener).testEnded(Mockito.eq(test2), (HashMap<String, Metric>) Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /**
     * Test for {@link HostTest#run(TestInformation, ITestInvocationListener)}, for a mix of test
     * junit3 and 4
     */
    @org.junit.Test
    public void testRun_junit_version_mix() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", SuccessTestCase.class.getName());
        setter.setOptionValue("class", Junit4TestClass.class.getName());
        runMixJunitTest(mHostTest, 2, 2);
    }

    /**
     * Test for {@link HostTest#run(TestInformation, ITestInvocationListener)}, for a mix of test
     * junit3 and 4 in collect only mode
     */
    @org.junit.Test
    public void testRun_junit_version_mix_collect() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", SuccessTestCase.class.getName());
        setter.setOptionValue("class", Junit4TestClass.class.getName());
        setter.setOptionValue("collect-tests-only", "true");
        runMixJunitTest(mHostTest, 2, 2);
    }

    /**
     * Test for {@link HostTest#run(TestInformation, ITestInvocationListener)}, for a mix of test
     * junit3 and 4 in a Junit 4 suite class.
     */
    @org.junit.Test
    public void testRun_junit_suite_mix() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", Junit4SuiteClass.class.getName());
        runMixJunitTest(mHostTest, 4, 1);
    }

    /**
     * Test for {@link HostTest#run(TestInformation, ITestInvocationListener)}, for a mix of test
     * junit3 and 4 in a Junit 4 suite class, in collect only mode.
     */
    @org.junit.Test
    public void testRun_junit_suite_mix_collect() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", Junit4SuiteClass.class.getName());
        setter.setOptionValue("collect-tests-only", "true");
        runMixJunitTest(mHostTest, 4, 1);
    }

    /** Helper for test option variation and avoid repeating the same setup */
    private void runMixJunitTest(HostTest hostTest, int expectedTest, int expectedRun)
            throws Exception {

        hostTest.run(mTestInfo, mListener);
        verify(mListener, times(expectedRun))
                .testRunStarted((String) Mockito.any(), Mockito.eq(expectedTest));
        verify(mListener, times(expectedRun))
                .testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
        TestDescription test1 = new TestDescription(SuccessTestCase.class.getName(), "testPass");
        verify(mListener).testStarted(Mockito.eq(test1));
        verify(mListener).testEnded(Mockito.eq(test1), (HashMap<String, Metric>) Mockito.any());
        TestDescription test2 = new TestDescription(SuccessTestCase.class.getName(), "testPass2");
        verify(mListener).testStarted(Mockito.eq(test2));
        verify(mListener).testEnded(Mockito.eq(test2), (HashMap<String, Metric>) Mockito.any());
        TestDescription test3 = new TestDescription(Junit4TestClass.class.getName(), "testPass5");
        verify(mListener).testStarted(Mockito.eq(test3));
        verify(mListener).testEnded(Mockito.eq(test3), (HashMap<String, Metric>) Mockito.any());
        TestDescription test4 = new TestDescription(Junit4TestClass.class.getName(), "testPass6");
        verify(mListener).testStarted(Mockito.eq(test4));
        verify(mListener).testEnded(Mockito.eq(test4), (HashMap<String, Metric>) Mockito.any());
    }

    /** Test a Junit4 suite with Ignored class in it. */
    @org.junit.Test
    public void testRun_junit_suite_mix_ignored() throws Exception {
        mHostTest.setClassName(Junit4SuiteClassWithIgnored.class.getName());

        assertEquals(3, mHostTest.countTestCases());
        mHostTest.run(mTestInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(3));
        TestDescription test1 = new TestDescription(Junit4TestClass.class.getName(), "testPass5");
        verify(mListener).testStarted(Mockito.eq(test1));
        verify(mListener).testEnded(Mockito.eq(test1), (HashMap<String, Metric>) Mockito.any());
        TestDescription test2 = new TestDescription(Junit4TestClass.class.getName(), "testPass6");
        verify(mListener).testStarted(Mockito.eq(test2));
        verify(mListener).testEnded(Mockito.eq(test2), (HashMap<String, Metric>) Mockito.any());
        TestDescription test3 = new TestDescription(Junit4IgnoredClass.class.getName(), "No Tests");
        verify(mListener).testStarted(Mockito.eq(test3));
        verify(mListener).testIgnored(test3);
        verify(mListener).testEnded(Mockito.eq(test3), (HashMap<String, Metric>) Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    @org.junit.Test
    public void testRun_junit_suite_annotation() throws Exception {
        mHostTest.setClassName(Junit4SuiteClassWithAnnotation.class.getName());
        mHostTest.addExcludeAnnotation(MyAnnotation.class.getName());

        assertEquals(2, mHostTest.countTestCases());
        mHostTest.run(mTestInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(2));
        TestDescription test1 =
                new TestDescription(Junit4TestClassWithIgnore.class.getName(), "testPass5");
        verify(mListener).testStarted(Mockito.eq(test1));
        verify(mListener).testEnded(Mockito.eq(test1), (HashMap<String, Metric>) Mockito.any());
        TestDescription test2 =
                new TestDescription(Junit4TestClassWithIgnore.class.getName(), "testPass6");
        verify(mListener).testStarted(Mockito.eq(test2));
        verify(mListener).testIgnored(test2);
        verify(mListener).testEnded(Mockito.eq(test2), (HashMap<String, Metric>) Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /**
     * Test success case for {@link HostTest#run(TestInformation, ITestInvocationListener)} with a
     * filtering and junit 4 handling.
     */
    @org.junit.Test
    public void testRun_testcase_Junit4TestNotAnnotationFiltering() throws Exception {
        mHostTest.setClassName(Junit4TestClass.class.getName());
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation2");
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("set-option", "junit4-option:true");

        mHostTest.run(mTestInfo, mListener);

        // Only test1 will run, test2 should be filtered out.
        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(1));
        TestDescription test1 = new TestDescription(Junit4TestClass.class.getName(), "testPass6");
        verify(mListener).testStarted(Mockito.eq(test1));
        Map<String, String> metrics = new HashMap<>();
        metrics.put("key2", "value2");
        // If the option was correctly set, this metric should be true.
        metrics.put("junit4-option", "true");
        verify(mListener)
                .testEnded(
                        Mockito.eq(test1), Mockito.eq(TfMetricProtoUtil.upgradeConvert(metrics)));
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /**
     * Test success case for {@link HostTest#run(TestInformation, ITestInvocationListener)} when
     * passing a dedicated option to it.
     */
    @org.junit.Test
    public void testRun_testcase_TargetedOptionPassing() throws Exception {
        mHostTest.setClassName(Junit4TestClass.class.getName());
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation2");
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue(
                "set-option", Junit4TestClass.class.getName() + ":junit4-option:true");
        setter.setOptionValue(
                "set-option", Junit4TestClass.class.getName() + ":map-option:key=test");

        mHostTest.run(mTestInfo, mListener);

        // Only test1 will run, test2 should be filtered out.
        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(1));
        TestDescription test1 = new TestDescription(Junit4TestClass.class.getName(), "testPass6");
        verify(mListener).testStarted(Mockito.eq(test1));
        Map<String, String> metrics = new HashMap<>();
        metrics.put("key2", "value2");
        // If the option was correctly set, this metric should be true.
        metrics.put("junit4-option", "true");
        metrics.put("map-option", "[test]");
        verify(mListener)
                .testEnded(
                        Mockito.eq(test1), Mockito.eq(TfMetricProtoUtil.upgradeConvert(metrics)));
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /**
     * Test success case for {@link HostTest#run(TestInformation, ITestInvocationListener)} when
     * passing a dedicated option to it. The class without the option doesn't throw an exception
     * since it's not targeted.
     */
    @org.junit.Test
    public void testRun_testcase_multiTargetedOptionPassing() throws Exception {
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation2");
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", Junit4TestClass.class.getName());
        setter.setOptionValue("class", Junit4TestLogClass.class.getName());
        setter.setOptionValue(
                "set-option", Junit4TestClass.class.getName() + ":junit4-option:true");

        mHostTest.run(mTestInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(2));
        TestDescription test1 =
                new TestDescription(Junit4TestLogClass.class.getName(), "testPass1");
        verify(mListener).testStarted(Mockito.eq(test1));
        verify(mListener).testLog(Mockito.eq("TEST"), Mockito.eq(LogDataType.TEXT), Mockito.any());
        verify(mListener).testEnded(test1, new HashMap<String, Metric>());
        TestDescription test2 =
                new TestDescription(Junit4TestLogClass.class.getName(), "testPass2");
        verify(mListener).testStarted(Mockito.eq(test2));
        // test cases do not share logs, only the second test logs are seen.
        verify(mListener).testLog(Mockito.eq("TEST2"), Mockito.eq(LogDataType.TEXT), Mockito.any());
        verify(mListener).testEnded(test2, new HashMap<String, Metric>());
        verify(mListener, times(2))
                .testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
        TestDescription test6 = new TestDescription(Junit4TestClass.class.getName(), "testPass6");
        // Only test1 will run, test2 should be filtered out.
        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(1));
        verify(mListener).testStarted(Mockito.eq(test6));
        Map<String, String> metrics = new HashMap<>();
        metrics.put("key2", "value2");
        // If the option was correctly set, this metric should be true.
        metrics.put("junit4-option", "true");
        verify(mListener)
                .testEnded(
                        Mockito.eq(test6), Mockito.eq(TfMetricProtoUtil.upgradeConvert(metrics)));
    }

    /**
     * Test success case for {@link HostTest#run(TestInformation, ITestInvocationListener)}, where
     * filtering is applied and results in 0 tests to run.
     */
    @org.junit.Test
    public void testRun_testcase_Junit4Test_filtering_no_more_tests() throws Exception {
        mHostTest.setClassName(Junit4TestClass.class.getName());
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation");

        mHostTest.run(mTestInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(0));
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /**
     * Test that in case the class attempted to be ran is malformed we bubble up the test failure.
     */
    @org.junit.Test
    public void testRun_Junit4Test_malformed() throws Exception {
        mHostTest.setClassName(Junit4MalformedTestClass.class.getName());

        mHostTest.run(mTestInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(1));
        ArgumentCaptor<TestDescription> captured = ArgumentCaptor.forClass(TestDescription.class);
        verify(mListener).testStarted(captured.capture());
        verify(mListener).testFailed((TestDescription) Mockito.any(), (String) Mockito.any());
        verify(mListener)
                .testEnded(
                        (TestDescription) Mockito.any(), (HashMap<String, Metric>) Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());

        assertEquals(Junit4MalformedTestClass.class.getName(), captured.getValue().getClassName());
        assertEquals("initializationError", captured.getValue().getTestName());
    }

    /**
     * Test for {@link HostTest#run(TestInformation, ITestInvocationListener)}, for a mix of test
     * junit3 and 4 in a Junit 4 suite class, and filtering is applied.
     */
    @org.junit.Test
    public void testRun_junit_suite_mix_filtering() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", Junit4SuiteClass.class.getName());
        runMixJunitTestWithFilter(mHostTest);
    }

    /**
     * Test for {@link HostTest#run(TestInformation, ITestInvocationListener)}, for a mix of test
     * junit3 and 4 in a Junit 4 suite class, and filtering is applied, in collect mode
     */
    @org.junit.Test
    public void testRun_junit_suite_mix_filtering_collect() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", Junit4SuiteClass.class.getName());
        setter.setOptionValue("collect-tests-only", "true");
        runMixJunitTestWithFilter(mHostTest);
    }

    /** Helper for test option variation and avoid repeating the same setup */
    private void runMixJunitTestWithFilter(HostTest hostTest) throws Exception {
        hostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation2");

        hostTest.run(mTestInfo, mListener);

        verify(mListener, times(1)).testRunStarted((String) Mockito.any(), Mockito.eq(2));
        TestDescription test1 = new TestDescription(SuccessTestCase.class.getName(), "testPass");
        verify(mListener, times(1))
                .testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
        verify(mListener).testStarted(Mockito.eq(test1));
        verify(mListener).testEnded(Mockito.eq(test1), (HashMap<String, Metric>) Mockito.any());
        TestDescription test4 = new TestDescription(Junit4TestClass.class.getName(), "testPass6");
        verify(mListener).testStarted(Mockito.eq(test4));
        verify(mListener).testEnded(Mockito.eq(test4), (HashMap<String, Metric>) Mockito.any());
    }

    /**
     * Test for {@link HostTest#split(int)} making sure each test type is properly handled and added
     * with a container or directly.
     */
    @org.junit.Test
    public void testRun_junit_suite_split() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        mHostTest.setDevice(mMockDevice);
        mHostTest.setBuild(mMockBuildInfo);
        setter.setOptionValue("class", Junit4SuiteClass.class.getName());
        setter.setOptionValue("class", SuccessTestSuite.class.getName());
        setter.setOptionValue("class", TestRemoteNotCollector.class.getName());
        List<IRemoteTest> list = (ArrayList<IRemoteTest>) mHostTest.split(1, mTestInfo);
        // split by class; numShards parameter should be ignored
        assertEquals(3, list.size());
        assertEquals(
                "com.android.tradefed.testtype.HostTestTest$TestableHostTest",
                list.get(0).getClass().getName());
        assertEquals(
                "com.android.tradefed.testtype.HostTestTest$TestableHostTest",
                list.get(1).getClass().getName());
        assertEquals(
                "com.android.tradefed.testtype.HostTestTest$TestableHostTest",
                list.get(2).getClass().getName());

        // Run the JUnit4 Container
        ((IBuildReceiver) list.get(0)).setBuild(mMockBuildInfo);
        ((IDeviceTest) list.get(0)).setDevice(mMockDevice);
        list.get(0).run(mTestInfo, mListener);

        // We expect all the test from the JUnit4 suite to run under the original suite classname
        // not under the container class name.
        verify(mListener)
                .testRunStarted(
                        Mockito.eq("com.android.tradefed.testtype.HostTestTest$Junit4SuiteClass"),
                        Mockito.eq(4));
        TestDescription test1 = new TestDescription(Junit4TestClass.class.getName(), "testPass5");
        verify(mListener).testStarted(test1);
        verify(mListener).testEnded(Mockito.eq(test1), (HashMap<String, Metric>) Mockito.any());
        TestDescription test2 = new TestDescription(SuccessTestCase.class.getName(), "testPass");
        verify(mListener).testStarted(Mockito.eq(test2));
        verify(mListener).testEnded(Mockito.eq(test2), (HashMap<String, Metric>) Mockito.any());
        TestDescription test3 = new TestDescription(SuccessTestCase.class.getName(), "testPass2");
        verify(mListener).testStarted(Mockito.eq(test3));
        verify(mListener).testEnded(Mockito.eq(test3), (HashMap<String, Metric>) Mockito.any());
        TestDescription test4 = new TestDescription(Junit4TestClass.class.getName(), "testPass6");
        verify(mListener).testStarted(Mockito.eq(test4));
        verify(mListener).testEnded(Mockito.eq(test4), (HashMap<String, Metric>) Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /** Similar to {@link #testRun_junit_suite_split()} but with shard-unit set to method */
    @org.junit.Test
    public void testRun_junit_suite_split_by_method() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        mHostTest.setDevice(mMockDevice);
        mHostTest.setBuild(mMockBuildInfo);
        setter.setOptionValue("class", Junit4SuiteClass.class.getName());
        setter.setOptionValue("class", SuccessTestSuite.class.getName());
        setter.setOptionValue("class", TestRemoteNotCollector.class.getName());
        setter.setOptionValue("shard-unit", "method");
        final Class<?>[] expectedTestCaseClasses =
                new Class<?>[] {
                    Junit4TestClass.class,
                    Junit4TestClass.class,
                    SuccessTestCase.class,
                    SuccessTestCase.class,
                    SuccessTestSuite.class,
                    SuccessTestSuite.class,
                    TestRemoteNotCollector.class,
                };
        List<IRemoteTest> list =
                (ArrayList<IRemoteTest>) mHostTest.split(expectedTestCaseClasses.length, mTestInfo);
        assertEquals(expectedTestCaseClasses.length, list.size());
        for (int i = 0; i < expectedTestCaseClasses.length; i++) {
            IRemoteTest shard = list.get(i);
            assertTrue(HostTest.class.isInstance(shard));
            HostTest hostTest = (HostTest) shard;
            assertEquals(1, hostTest.getClasses().size());
            assertEquals(1, hostTest.countTestCases());
            assertEquals(expectedTestCaseClasses[i], hostTest.getClasses().get(0));
        }

        // Run the JUnit4 Container
        ((IBuildReceiver) list.get(0)).setBuild(mMockBuildInfo);
        ((IDeviceTest) list.get(0)).setDevice(mMockDevice);
        list.get(0).run(mTestInfo, mListener);

        // We expect all the test from the JUnit4 suite to run under the original suite classname
        // not under the container class name.
        TestDescription test = new TestDescription(Junit4TestClass.class.getName(), "testPass5");
        verify(mListener).testRunStarted(test.getClassName(), 1);
        verify(mListener).testStarted(test);
        verify(mListener).testEnded(Mockito.eq(test), (HashMap<String, Metric>) Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /** Test for {@link HostTest#split(int)} when no class is specified throws an exception */
    @org.junit.Test
    public void testSplit_noClass() throws Exception {
        try {
            mHostTest.split(1, mTestInfo);
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Missing Test class name", e.getMessage());
        }
    }

    /**
     * Test for {@link HostTest#split(int)} when multiple classes are specified with a method option
     * too throws an exception
     */
    @org.junit.Test
    public void testSplit_methodAndMultipleClass() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", Junit4SuiteClass.class.getName());
        setter.setOptionValue("class", SuccessTestSuite.class.getName());
        mHostTest.setMethodName("testPass2");
        try {
            mHostTest.split(1, mTestInfo);
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Method name given with multiple test classes", e.getMessage());
        }
    }

    /**
     * Test for {@link HostTest#split(int)} when a single class is specified, no splitting can occur
     * and it returns null.
     */
    @org.junit.Test
    public void testSplit_singleClass() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", SuccessTestSuite.class.getName());
        mHostTest.setMethodName("testPass2");
        assertNull(mHostTest.split(1));
    }

    /** Test {@link IShardableTest} interface and check the sharding is correct. */
    @org.junit.Test
    public void testGetTestShardable_wrapping_shardUnit_method() throws Exception {
        final ITestDevice device = mock(ITestDevice.class);
        mHostTest.setDevice(device);
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", Junit4SuiteClass.class.getName());
        setter.setOptionValue("class", SuccessTestSuite.class.getName());
        setter.setOptionValue("class", TestRemoteNotCollector.class.getName());
        setter.setOptionValue("class", SuccessHierarchySuite.class.getName());
        setter.setOptionValue("class", SuccessDeviceTest.class.getName());
        setter.setOptionValue("runtime-hint", "2m");
        setter.setOptionValue("shard-unit", "method");
        final Class<?>[] expectedTestCaseClasses =
                new Class<?>[] {
                    Junit4TestClass.class,
                    SuccessTestCase.class,
                    TestRemoteNotCollector.class,
                    SuccessDeviceTest.class,
                    Junit4TestClass.class,
                    SuccessTestSuite.class,
                    SuccessHierarchySuite.class,
                    SuccessTestCase.class,
                    SuccessTestSuite.class,
                    SuccessHierarchySuite.class,
                };
        final int numShards = 3;
        final long runtimeHint = 2 * 60 * 1000; // 2 minutes in microseconds
        int numTestCases = mHostTest.countTestCases();
        assertEquals(expectedTestCaseClasses.length, numTestCases);
        for (int i = 0, j = 0; i < numShards; i++) {
            IRemoteTest shard;
            shard = new ArrayList<>(mHostTest.split(numShards, mTestInfo)).get(i);
            assertTrue(shard instanceof HostTest);
            HostTest hostTest = (HostTest) shard;
            int q = numTestCases / numShards;
            int r = numTestCases % numShards;
            int n = q + (i < r ? 1 : 0);
            assertEquals(n, hostTest.countTestCases());
            assertEquals(n, hostTest.getClasses().size());
            assertEquals(runtimeHint * n / numTestCases, hostTest.getRuntimeHint());
            for (int k = 0; k < n; k++) {
                assertEquals(expectedTestCaseClasses[j++], hostTest.getClasses().get(k));
            }
        }
    }

    /** An annotation on the class exclude it. All the method of the class should be excluded. */
    @org.junit.Test
    public void testClassAnnotation_excludeAll() throws Exception {
        mHostTest.setClassName(SuccessTestCase.class.getName());
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation3");
        assertEquals(0, mHostTest.countTestCases());
        // nothing run.

        mHostTest.run(mTestInfo, mListener);
    }

    /** An annotation on the class include it. We include all the method inside it. */
    @org.junit.Test
    public void testClassAnnotation_includeAll() throws Exception {
        mHostTest.setClassName(SuccessTestCase.class.getName());
        mHostTest.addIncludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation3");
        assertEquals(2, mHostTest.countTestCases());

        mHostTest.run(mTestInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(2));
        TestDescription test1 = new TestDescription(SuccessTestCase.class.getName(), "testPass");
        verify(mListener).testStarted(Mockito.eq(test1));
        verify(mListener).testEnded(Mockito.eq(test1), (HashMap<String, Metric>) Mockito.any());
        TestDescription test2 = new TestDescription(SuccessTestCase.class.getName(), "testPass2");
        verify(mListener).testStarted(Mockito.eq(test2));
        verify(mListener).testEnded(Mockito.eq(test2), (HashMap<String, Metric>) Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /**
     * An annotation on the method (no annotation on class) exclude it. This method does not run.
     */
    @org.junit.Test
    public void testMethodAnnotation_excludeAll() throws Exception {
        mHostTest.setClassName(AnotherTestCase.class.getName());
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation3");
        assertEquals(1, mHostTest.countTestCases());

        mHostTest.run(mTestInfo, mListener);

        TestDescription test1 = new TestDescription(AnotherTestCase.class.getName(), "testPass4");
        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(1));
        verify(mListener).testStarted(Mockito.eq(test1));
        verify(mListener).testEnded(Mockito.eq(test1), (HashMap<String, Metric>) Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /** An annotation on the method (no annotation on class) include it. Only this method run. */
    @org.junit.Test
    public void testMethodAnnotation_includeAll() throws Exception {
        mHostTest.setClassName(AnotherTestCase.class.getName());
        mHostTest.addIncludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation3");
        assertEquals(1, mHostTest.countTestCases());

        mHostTest.run(mTestInfo, mListener);

        TestDescription test1 = new TestDescription(AnotherTestCase.class.getName(), "testPass3");
        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(1));
        verify(mListener).testStarted(Mockito.eq(test1));
        verify(mListener).testEnded(Mockito.eq(test1), (HashMap<String, Metric>) Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /**
     * Check that a method annotation in a {@link DeviceTestCase} is properly included with an
     * include filter during collect-tests-only
     */
    @org.junit.Test
    public void testMethodAnnotation_includeAll_collect() throws Exception {
        mHostTest.setCollectTestsOnly(true);
        mHostTest.setClassName(SuccessDeviceTest2.class.getName());
        mHostTest.addIncludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation3");
        assertEquals(1, mHostTest.countTestCases());

        mHostTest.run(mTestInfo, mListener);

        TestDescription test1 =
                new TestDescription(SuccessDeviceTest2.class.getName(), "testPass1");
        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(1));
        verify(mListener).testStarted(Mockito.eq(test1));
        verify(mListener).testEnded(Mockito.eq(test1), (HashMap<String, Metric>) Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /**
     * Test that a method annotated and overridden is not included because the child method is not
     * annotated (annotation are not inherited).
     */
    @org.junit.Test
    public void testMethodAnnotation_inherited() throws Exception {
        mHostTest.setCollectTestsOnly(true);
        mHostTest.setClassName(InheritedDeviceTest3.class.getName());
        mHostTest.addIncludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation3");
        assertEquals(1, mHostTest.countTestCases());

        mHostTest.run(mTestInfo, mListener);

        TestDescription test1 =
                new TestDescription(InheritedDeviceTest3.class.getName(), "testPass3");
        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(1));
        verify(mListener).testStarted(Mockito.eq(test1));
        verify(mListener).testEnded(Mockito.eq(test1), (HashMap<String, Metric>) Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /**
     * Test that a method annotated and overridden is not excluded if the child method does not have
     * the annotation.
     */
    @org.junit.Test
    public void testMethodAnnotation_inherited_exclude() throws Exception {
        mHostTest.setCollectTestsOnly(true);
        mHostTest.setClassName(InheritedDeviceTest3.class.getName());
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation3");
        assertEquals(2, mHostTest.countTestCases());

        mHostTest.run(mTestInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(2));
        TestDescription test1 =
                new TestDescription(InheritedDeviceTest3.class.getName(), "testPass1");
        verify(mListener).testStarted(Mockito.eq(test1));
        verify(mListener).testEnded(Mockito.eq(test1), (HashMap<String, Metric>) Mockito.any());
        TestDescription test2 =
                new TestDescription(InheritedDeviceTest3.class.getName(), "testPass2");
        verify(mListener).testStarted(Mockito.eq(test2));
        verify(mListener).testEnded(Mockito.eq(test2), (HashMap<String, Metric>) Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /** Check that a {@link DeviceTestCase} is properly excluded when the class is excluded. */
    @org.junit.Test
    public void testDeviceTestCase_excludeClass() throws Exception {
        mHostTest.setClassName(SuccessDeviceTest2.class.getName());
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation");
        assertEquals(0, mHostTest.countTestCases());

        mHostTest.run(mTestInfo, mListener);
    }

    /**
     * Check that a {@link DeviceTestCase} is properly excluded when the class is excluded in
     * collect-tests-only mode (yielding the same result as above).
     */
    @org.junit.Test
    public void testDeviceTestCase_excludeClass_collect() throws Exception {
        mHostTest.setCollectTestsOnly(true);
        mHostTest.setClassName(SuccessDeviceTest2.class.getName());
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation");
        assertEquals(0, mHostTest.countTestCases());

        mHostTest.run(mTestInfo, mListener);
    }

    /**
     * Test for {@link HostTest#split(int)} when the exclude-filter is set, it should be carried
     * over to shards.
     */
    @org.junit.Test
    public void testSplit_withExclude() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", SuccessTestCase.class.getName());
        setter.setOptionValue("class", AnotherTestCase.class.getName());
        mHostTest.addExcludeFilter(
                "com.android.tradefed.testtype.HostTestTest$SuccessTestCase#testPass");
        Collection<IRemoteTest> res = mHostTest.split(1, mTestInfo);
        // split by class; numShards parameter should be ignored
        assertEquals(2, res.size());

        for (IRemoteTest test : res) {
            assertTrue(test instanceof HostTest);
            ((HostTest) test).setDevice(mMockDevice);
            test.run(mTestInfo, mListener);
        }

        // only one tests in the SuccessTestCase because it's been filtered out.
        verify(mListener)
                .testRunStarted(
                        Mockito.eq("com.android.tradefed.testtype.HostTestTest$SuccessTestCase"),
                        Mockito.eq(1));
        TestDescription tid2 =
                new TestDescription(
                        "com.android.tradefed.testtype.HostTestTest$SuccessTestCase", "testPass2");
        verify(mListener).testStarted(tid2);
        verify(mListener).testEnded(tid2, new HashMap<String, Metric>());
        verify(mListener, times(2))
                .testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
        verify(mListener)
                .testRunStarted(
                        Mockito.eq("com.android.tradefed.testtype.HostTestTest$AnotherTestCase"),
                        Mockito.eq(2));
        TestDescription tid3 =
                new TestDescription(
                        "com.android.tradefed.testtype.HostTestTest$AnotherTestCase", "testPass3");
        verify(mListener).testStarted(tid3);
        verify(mListener).testEnded(tid3, new HashMap<String, Metric>());
        TestDescription tid4 =
                new TestDescription(
                        "com.android.tradefed.testtype.HostTestTest$AnotherTestCase", "testPass4");
        verify(mListener).testStarted(tid4);
        verify(mListener).testEnded(tid4, new HashMap<String, Metric>());
    }

    /**
     * Test that when the 'set-option' format is not respected, an exception is thrown. Only one '='
     * is allowed in the value.
     */
    @org.junit.Test
    public void testRun_setOption_invalid() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        // Map option with invalid format
        setter.setOptionValue("set-option", "map-option:key=value=2");
        mHostTest.setClassName(TestMetricTestCase.class.getName());

        try {
            mHostTest.run(mTestInfo, mListener);
            fail("Should have thrown an exception.");
        } catch (RuntimeException expected) {
            // expected
        }
    }

    /**
     * Test that when a JUnit runner implements {@link ISetOptionReceiver} we attempt to pass it the
     * hostTest set-option.
     */
    @org.junit.Test
    public void testSetOption_regularJUnit4_fail() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        // Map option with invalid format
        setter.setOptionValue("set-option", "option:value");
        mHostTest.setClassName(Junit4RegularClass.class.getName());

        try {
            mHostTest.run(mTestInfo, mListener);
            fail("Should have thrown an exception.");
        } catch (RuntimeException expected) {
            // expected
        }

        verify(mListener)
                .testRunStarted(
                        Mockito.eq("com.android.tradefed.testtype.HostTestTest$Junit4RegularClass"),
                        Mockito.eq(1));
        verify(mListener).testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /**
     * Test for {@link HostTest#run(TestInformation, ITestInvocationListener)}, for test with Junit4
     * style that log some data.
     */
    @org.junit.Test
    public void testRun_junit4style_log() throws Exception {
        mHostTest.setClassName(Junit4TestLogClass.class.getName());

        mHostTest.run(mTestInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(2));
        TestDescription test1 =
                new TestDescription(Junit4TestLogClass.class.getName(), "testPass1");
        verify(mListener).testStarted(Mockito.eq(test1));
        verify(mListener).testLog(Mockito.eq("TEST"), Mockito.eq(LogDataType.TEXT), Mockito.any());
        verify(mListener).testEnded(test1, new HashMap<String, Metric>());
        TestDescription test2 =
                new TestDescription(Junit4TestLogClass.class.getName(), "testPass2");
        verify(mListener).testStarted(Mockito.eq(test2));
        // test cases do not share logs, only the second test logs are seen.
        verify(mListener).testLog(Mockito.eq("TEST2"), Mockito.eq(LogDataType.TEXT), Mockito.any());
        verify(mListener).testEnded(test2, new HashMap<String, Metric>());
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    @org.junit.Test
    public void testRun_junit4style_excluded() throws Exception {
        mHostTest.setClassName(Junit4TestLogClass.class.getName());
        mHostTest.addExcludeAnnotation(MyAnnotation.class.getName());

        mHostTest.run(mTestInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(0));
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /** Explicitly include only the class annotation should run all method inside it. */
    @org.junit.Test
    public void testRun_junit4style_included() throws Exception {
        mHostTest.setClassName(Junit4TestLogClass.class.getName());
        mHostTest.addIncludeAnnotation(MyAnnotation.class.getName());

        mHostTest.run(mTestInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(2));
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /** Similar to {@link #testSplit_withExclude()} but with shard-unit set to method */
    @org.junit.Test
    public void testSplit_excludeTestCase_shardUnit_method() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", SuccessTestCase.class.getName());
        setter.setOptionValue("class", AnotherTestCase.class.getName());

        // only one tests in the SuccessTestCase because it's been filtered out.
        TestDescription tid2 = new TestDescription(SuccessTestCase.class.getName(), "testPass2");
        TestDescription tid3 = new TestDescription(AnotherTestCase.class.getName(), "testPass3");
        TestDescription tid4 = new TestDescription(AnotherTestCase.class.getName(), "testPass4");
        testSplit_excludeFilter_shardUnit_Method(
                SuccessTestCase.class.getName() + "#testPass",
                new TestDescription[] {tid2, tid3, tid4});
    }

    /** Similar to {@link #testSplit_excludeTestCase_shardUnit_method()} but exclude class */
    @org.junit.Test
    public void testSplit_excludeTestClass_shardUnit_method() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", SuccessTestCase.class.getName());
        setter.setOptionValue("class", AnotherTestCase.class.getName());

        TestDescription tid3 = new TestDescription(AnotherTestCase.class.getName(), "testPass3");
        TestDescription tid4 = new TestDescription(AnotherTestCase.class.getName(), "testPass4");
        testSplit_excludeFilter_shardUnit_Method(
                SuccessTestCase.class.getName(), new TestDescription[] {tid3, tid4});
    }

    private void testSplit_excludeFilter_shardUnit_Method(
            String excludeFilter, TestDescription[] expectedTids)
            throws DeviceNotAvailableException, ConfigurationException {
        mHostTest.addExcludeFilter(excludeFilter);
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("shard-unit", "method");

        Collection<IRemoteTest> res = mHostTest.split(expectedTids.length, mTestInfo);
        assertEquals(expectedTids.length, res.size());

        for (IRemoteTest test : res) {
            assertTrue(test instanceof HostTest);
            ((HostTest) test).setDevice(mMockDevice);
            test.run(mTestInfo, mListener);
        }

        HashMap<String, Integer> testClassRunCounts = new HashMap<>();
        for (TestDescription tid : expectedTids) {
            Integer count = testClassRunCounts.getOrDefault(tid.getClassName(), 0);
            count = count + 1;
            testClassRunCounts.put(tid.getClassName(), count);
            verify(mListener).testStarted(tid);
            verify(mListener).testEnded(tid, new HashMap<String, Metric>());
        }
        for (Map.Entry<String, Integer> entry : testClassRunCounts.entrySet()) {
            verify(mListener, times(entry.getValue())).testRunStarted(entry.getKey(), 1);
        }
        verify(mListener, times(expectedTids.length))
                .testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /** JUnit 4 class that throws within its @BeforeClass */
    @RunWith(JUnit4.class)
    public static class JUnit4FailedBeforeClass {
        @BeforeClass
        public static void beforeClass() {
            throw new RuntimeException();
        }

        @org.junit.Test
        public void test1() {}
    }

    /**
     * Test that when an exception is thrown from within @BeforeClass, we correctly report a failure
     * since we cannot run each individual test.
     */
    @org.junit.Test
    public void testRun_junit4ExceptionBeforeClass() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", JUnit4FailedBeforeClass.class.getName());
        setter.setOptionValue("class", Junit4TestClass.class.getName());

        assertEquals(3, mHostTest.countTestCases());
        mHostTest.run(mTestInfo, mListener);

        // First class fail with the run failure
        verify(mListener).testRunStarted(Mockito.any(), Mockito.eq(1));
        ArgumentCaptor<FailureDescription> capture =
                ArgumentCaptor.forClass(FailureDescription.class);
        verify(mListener).testRunFailed(capture.capture());
        verify(mListener, times(2))
                .testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
        // Second class run properly
        verify(mListener).testRunStarted(Mockito.any(), Mockito.eq(2));
        TestDescription tid2 = new TestDescription(Junit4TestClass.class.getName(), "testPass5");
        verify(mListener).testStarted(Mockito.eq(tid2));
        verify(mListener).testEnded(Mockito.eq(tid2), (HashMap<String, Metric>) Mockito.any());
        TestDescription tid3 = new TestDescription(Junit4TestClass.class.getName(), "testPass6");
        verify(mListener).testStarted(Mockito.eq(tid3));
        verify(mListener).testEnded(Mockito.eq(tid3), (HashMap<String, Metric>) Mockito.any());
        FailureDescription failure = capture.getValue();
        assertEquals("Exception with no error message", failure.getErrorMessage());
    }

    /** JUnit4 class that throws within its @Before */
    @RunWith(JUnit4.class)
    public static class JUnit4FailedBefore {
        @Before
        public void before() {
            throw new RuntimeException();
        }

        @org.junit.Test
        public void test1() {}
    }

    /**
     * Test that when an exception is thrown within @Before, the test are reported and failed with
     * the exception.
     */
    @org.junit.Test
    public void testRun_junit4ExceptionBefore() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", JUnit4FailedBefore.class.getName());
        setter.setOptionValue("class", Junit4TestClass.class.getName());

        assertEquals(3, mHostTest.countTestCases());
        mHostTest.run(mTestInfo, mListener);

        // First class has a test failure because of the @Before
        verify(mListener).testRunStarted(Mockito.any(), Mockito.eq(1));
        TestDescription tid = new TestDescription(JUnit4FailedBefore.class.getName(), "test1");
        verify(mListener).testStarted(Mockito.eq(tid));
        verify(mListener).testFailed(Mockito.eq(tid), (String) Mockito.any());
        verify(mListener).testEnded(Mockito.eq(tid), (HashMap<String, Metric>) Mockito.any());
        verify(mListener, times(2))
                .testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
        // Second class run properly
        verify(mListener).testRunStarted(Mockito.any(), Mockito.eq(2));
        TestDescription tid2 = new TestDescription(Junit4TestClass.class.getName(), "testPass5");
        verify(mListener).testStarted(Mockito.eq(tid2));
        verify(mListener).testEnded(Mockito.eq(tid2), (HashMap<String, Metric>) Mockito.any());
        TestDescription tid3 = new TestDescription(Junit4TestClass.class.getName(), "testPass6");
        verify(mListener).testStarted(Mockito.eq(tid3));
        verify(mListener).testEnded(Mockito.eq(tid3), (HashMap<String, Metric>) Mockito.any());
    }

    /**
     * Test that when all tests are filtered out, we properly shard them with 0 runtime, and they
     * will be completely skipped during execution.
     */
    @org.junit.Test
    public void testSplit_withFilter() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", Junit4TestClass.class.getName());
        setter.setOptionValue("class", AnotherTestCase.class.getName());
        // Filter everything out
        mHostTest.addExcludeFilter(Junit4TestClass.class.getName());
        mHostTest.addExcludeFilter(AnotherTestCase.class.getName());

        Collection<IRemoteTest> tests = mHostTest.split(6, mTestInfo);
        assertEquals(2, tests.size());
        for (IRemoteTest test : tests) {
            assertTrue(test instanceof HostTest);
            assertEquals(0L, ((HostTest) test).getRuntimeHint());
            assertEquals(0, ((HostTest) test).countTestCases());
        }
    }

    @org.junit.Test
    public void testEarlyFailure() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", "i.cannot.be.resolved");

        mHostTest.run(mTestInfo, mListener);

        verify(mListener).testRunStarted(HostTestTest.class.getName() + ".TestableHostTest", 0);
        ArgumentCaptor<FailureDescription> captured =
                ArgumentCaptor.forClass(FailureDescription.class);
        verify(mListener).testRunFailed(captured.capture());
        verify(mListener).testRunEnded(0L, new HashMap<String, Metric>());
        assertTrue(
                captured.getValue()
                        .getErrorMessage()
                        .contains("Could not load Test class i.cannot.be.resolved"));
    }

    /**
     * Test success case for {@link HostTest#run(TestInformation, ITestInvocationListener)}, where
     * test to run is a {@link TestSuite} and has set-options with the char ':' escaped.
     */
    @org.junit.Test
    public void testRun_junit3TestSuite_optionEscapeColon() throws Exception {
        doReturn(new ResolvedFile(new File("/downloaded/somewhere")))
                .when(mMockResolver)
                .resolveRemoteFile((RemoteFileResolverArgs) Mockito.any());
        mHostTest.setClassName(OptionEscapeColonTestCase.class.getName());
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue(
                "set-option",
                OptionEscapeColonTestCase.class.getName()
                        + ":gcs-bucket-file:gs\\://bucket/path/file");
        setter.setOptionValue("set-option", "hello:hello\\:world");
        setter.setOptionValue(
                "set-option",
                OptionEscapeColonTestCase.class.getName() + ":foobar:baz\\:qux\\=wap");
        TestDescription testGcsBucket =
                new TestDescription(OptionEscapeColonTestCase.class.getName(), "testGcsBucket");
        TestDescription testEscapeStrings =
                new TestDescription(OptionEscapeColonTestCase.class.getName(), "testEscapeStrings");

        assertEquals(2, mHostTest.countTestCases());
        mHostTest.run(mTestInfo, mListener);

        verify(mListener).testRunStarted((String) Mockito.any(), Mockito.eq(2));
        verify(mListener).testStarted(Mockito.eq(testGcsBucket));
        verify(mListener, times(0)).testFailed(Mockito.eq(testGcsBucket), (String) Mockito.any());
        verify(mListener)
                .testEnded(Mockito.eq(testGcsBucket), (HashMap<String, Metric>) Mockito.any());
        verify(mListener).testStarted(Mockito.eq(testEscapeStrings));
        verify(mListener, times(0))
                .testFailed(Mockito.eq(testEscapeStrings), (String) Mockito.any());
        verify(mListener)
                .testEnded(Mockito.eq(testEscapeStrings), (HashMap<String, Metric>) Mockito.any());
        verify(mListener).testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }
}
