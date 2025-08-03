/*
 * Copyright (C) 2016 The Android Open Source Project
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
import static org.mockito.Mockito.verify;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner.TestMetrics;
import com.android.tradefed.util.proto.TfMetricProtoUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.Suite.SuiteClasses;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.Map;

/** Unit Tests for {@link DeviceSuite}. */
@RunWith(JUnit4.class)
public class DeviceSuiteTest {

    // We use HostTest as a runner for JUnit4 Suite
    private HostTest mHostTest;
    @Mock ITestDevice mMockDevice;
    private TestInformation mTestInfo;
    @Mock ITestInvocationListener mListener;
    @Mock IBuildInfo mMockBuildInfo;
    @Mock IAbi mMockAbi;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mHostTest = new HostTest();

        mHostTest.setDevice(mMockDevice);
        mHostTest.setBuild(mMockBuildInfo);
        mHostTest.setAbi(mMockAbi);
        OptionSetter setter = new OptionSetter(mHostTest);
        // Disable pretty logging for testing
        setter.setOptionValue("enable-pretty-logs", "false");
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device", mMockDevice);
        context.addDeviceBuildInfo("device", mMockBuildInfo);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
    }

    @RunWith(DeviceJUnit4ClassRunner.class)
    public static class Junit4DeviceTestclass implements IDeviceTest, IAbiReceiver, IBuildReceiver {
        public static ITestDevice sDevice;
        public static IBuildInfo sBuildInfo;
        public static IAbi sAbi;

        @Rule public TestMetrics metrics = new TestMetrics();

        @Option(name = "option")
        private String mOption = null;

        public Junit4DeviceTestclass() {
            sDevice = null;
            sBuildInfo = null;
            sAbi = null;
        }

        @Test
        @MyAnnotation1
        public void testPass1() {
            if (mOption != null) {
                metrics.addTestMetric("option", mOption);
            }
        }

        @Test
        public void testPass2() {}

        @Override
        public void setDevice(ITestDevice device) {
            sDevice = device;
        }

        @Override
        public ITestDevice getDevice() {
            return sDevice;
        }

        @Override
        public void setBuild(IBuildInfo buildInfo) {
            sBuildInfo = buildInfo;
        }

        @Override
        public void setAbi(IAbi abi) {
            sAbi = abi;
        }
    }

    @RunWith(DeviceSuite.class)
    @SuiteClasses({
        Junit4DeviceTestclass.class,
    })
    public class Junit4DeviceSuite {}

    /** JUnit3 test class */
    public static class JUnit3DeviceTestCase extends DeviceTestCase
            implements IBuildReceiver, IAbiReceiver {
        private IBuildInfo mBuild;
        private IAbi mAbi;

        public void testOne() {
            assertNotNull(getDevice());
            assertNotNull(mBuild);
            assertNotNull(mAbi);
        }

        @Override
        public void setBuild(IBuildInfo buildInfo) {
            mBuild = buildInfo;
        }

        @Override
        public void setAbi(IAbi abi) {
            mAbi = abi;
        }
    }

    /** JUnit4 style suite that contains a JUnit3 class. */
    @RunWith(DeviceSuite.class)
    @SuiteClasses({
        JUnit3DeviceTestCase.class,
    })
    public class JUnit4SuiteWithJunit3 {}

    /** Simple Annotation class for testing */
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MyAnnotation1 {}

    @Test
    public void testRunDeviceSuite() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", Junit4DeviceSuite.class.getName());

        TestDescription test1 =
                new TestDescription(Junit4DeviceTestclass.class.getName(), "testPass1");
        TestDescription test2 =
                new TestDescription(Junit4DeviceTestclass.class.getName(), "testPass2");

        mHostTest.run(mTestInfo, mListener);

        verify(mListener)
                .testRunStarted(
                        Mockito.eq(
                                "com.android.tradefed.testtype.DeviceSuiteTest$Junit4DeviceSuite"),
                        Mockito.eq(2));
        verify(mListener).testStarted(Mockito.eq(test1));
        verify(mListener).testEnded(Mockito.eq(test1), Mockito.eq(new HashMap<String, Metric>()));
        verify(mListener).testStarted(Mockito.eq(test2));
        verify(mListener).testEnded(Mockito.eq(test2), Mockito.eq(new HashMap<String, Metric>()));
        verify(mListener)
                .testRunEnded(Mockito.anyLong(), Mockito.eq(new HashMap<String, Metric>()));
        // Verify that all setters were called on Test class inside suite
        assertEquals(mMockDevice, Junit4DeviceTestclass.sDevice);
        assertEquals(mMockBuildInfo, Junit4DeviceTestclass.sBuildInfo);
        assertEquals(mMockAbi, Junit4DeviceTestclass.sAbi);
    }

    /** Test the run with filtering to include only one annotation. */
    @Test
    public void testRun_withFiltering() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", Junit4DeviceSuite.class.getName());
        mHostTest.addIncludeAnnotation(
                "com.android.tradefed.testtype.DeviceSuiteTest$MyAnnotation1");
        assertEquals(1, mHostTest.countTestCases());

        TestDescription test1 =
                new TestDescription(Junit4DeviceTestclass.class.getName(), "testPass1");

        mHostTest.run(mTestInfo, mListener);

        verify(mListener)
                .testRunStarted(
                        Mockito.eq(
                                "com.android.tradefed.testtype.DeviceSuiteTest$Junit4DeviceSuite"),
                        Mockito.eq(1));
        verify(mListener).testStarted(Mockito.eq(test1));
        verify(mListener).testEnded(Mockito.eq(test1), Mockito.eq(new HashMap<String, Metric>()));
        verify(mListener)
                .testRunEnded(Mockito.anyLong(), Mockito.eq(new HashMap<String, Metric>()));
    }

    /** Tests that options are piped from Suite to the sub-runners. */
    @Test
    public void testRun_withOption() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", Junit4DeviceSuite.class.getName());
        setter.setOptionValue("set-option", "option:value_test");

        TestDescription test1 =
                new TestDescription(Junit4DeviceTestclass.class.getName(), "testPass1");
        TestDescription test2 =
                new TestDescription(Junit4DeviceTestclass.class.getName(), "testPass2");

        Map<String, String> expected = new HashMap<>();
        expected.put("option", "value_test");

        mHostTest.run(mTestInfo, mListener);

        verify(mListener)
                .testRunStarted(
                        Mockito.eq(
                                "com.android.tradefed.testtype.DeviceSuiteTest$Junit4DeviceSuite"),
                        Mockito.eq(2));
        verify(mListener).testStarted(Mockito.eq(test1));
        verify(mListener)
                .testEnded(
                        Mockito.eq(test1), Mockito.eq(TfMetricProtoUtil.upgradeConvert(expected)));
        verify(mListener).testStarted(Mockito.eq(test2));
        verify(mListener).testEnded(Mockito.eq(test2), Mockito.eq(new HashMap<String, Metric>()));
        verify(mListener)
                .testRunEnded(Mockito.anyLong(), Mockito.eq(new HashMap<String, Metric>()));
    }

    /** Test that a JUnit3 class inside our JUnit4 suite can receive the usual values. */
    @Test
    public void testRunDeviceSuite_junit3() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", JUnit4SuiteWithJunit3.class.getName());

        TestDescription test1 =
                new TestDescription(JUnit3DeviceTestCase.class.getName(), "testOne");

        mHostTest.run(mTestInfo, mListener);

        verify(mListener)
                .testRunStarted(
                        Mockito.eq(
                                "com.android.tradefed.testtype.DeviceSuiteTest$JUnit4SuiteWithJunit3"),
                        Mockito.eq(1));
        verify(mListener).testStarted(Mockito.eq(test1));
        verify(mListener).testEnded(Mockito.eq(test1), Mockito.eq(new HashMap<String, Metric>()));
        verify(mListener)
                .testRunEnded(Mockito.anyLong(), Mockito.eq(new HashMap<String, Metric>()));
    }
}
