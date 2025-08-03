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
package com.android.tradefed.testtype.suite;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.DeviceConfigurationHolder;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IDeviceConfiguration;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.targetprep.ITargetPreparer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

/** Unit tests for {@link ITestSuite} when used with multiple devices. */
@RunWith(JUnit4.class)
public class ITestSuiteMultiTest {

    private static final String EMPTY_CONFIG = "empty";
    private static final String TEST_CONFIG_NAME = "test";
    private static final String DEVICE_NAME_1 = "device1";
    private static final String DEVICE_NAME_2 = "device2";

    private ITestSuite mTestSuite;
    @Mock ITestInvocationListener mMockListener;
    private IInvocationContext mContext;
    private TestInformation mTestInfo;
    @Mock ITestDevice mMockDevice1;
    @Mock IBuildInfo mMockBuildInfo1;
    @Mock ITestDevice mMockDevice2;
    @Mock IBuildInfo mMockBuildInfo2;

    @Mock ITargetPreparer mMockTargetPrep;
    private IConfiguration mStubMainConfiguration;
    @Mock ILogSaver mMockLogSaver;

    static class TestSuiteMultiDeviceImpl extends ITestSuite {
        private int mNumTests = 1;
        private ITargetPreparer mPreparer;

        public TestSuiteMultiDeviceImpl() {
            setMultiDeviceStrategy(MultiDeviceModuleStrategy.ONLY_MULTI_DEVICES);
        }

        public TestSuiteMultiDeviceImpl(int numTests, ITargetPreparer targetPrep) {
            this();
            mNumTests = numTests;
            mPreparer = targetPrep;
        }

        @Override
        public LinkedHashMap<String, IConfiguration> loadTests() {
            LinkedHashMap<String, IConfiguration> testConfig = new LinkedHashMap<>();
            try {
                for (int i = 1; i < mNumTests; i++) {
                    IConfiguration extraConfig =
                            ConfigurationFactory.getInstance()
                                    .createConfigurationFromArgs(new String[] {EMPTY_CONFIG});
                    List<IDeviceConfiguration> deviceConfigs = new ArrayList<>();
                    deviceConfigs.add(new DeviceConfigurationHolder(DEVICE_NAME_1));

                    IDeviceConfiguration holder2 = new DeviceConfigurationHolder(DEVICE_NAME_2);
                    deviceConfigs.add(holder2);
                    holder2.addSpecificConfig(mPreparer);

                    extraConfig.setDeviceConfigList(deviceConfigs);

                    MultiDeviceStubTest test = new MultiDeviceStubTest();
                    test.setExceptedDevice(2);
                    extraConfig.setTest(test);
                    testConfig.put(TEST_CONFIG_NAME + i, extraConfig);
                }
            } catch (ConfigurationException e) {
                CLog.e(e);
                throw new RuntimeException(e);
            }
            return testConfig;
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mTestSuite = new TestSuiteMultiDeviceImpl(2, mMockTargetPrep);

        // 2 devices and 2 builds
        when(mMockDevice1.getSerialNumber()).thenReturn("SERIAL1");
        when(mMockDevice1.getDeviceDate()).thenReturn(0L);
        when(mMockDevice1.getIDevice()).thenReturn(mock(IDevice.class));

        when(mMockBuildInfo1.getRemoteFiles()).thenReturn(null);

        when(mMockDevice2.getSerialNumber()).thenReturn("SERIAL2");
        when(mMockDevice2.getDeviceDate()).thenReturn(0L);
        when(mMockDevice2.getIDevice()).thenReturn(mock(IDevice.class));

        mStubMainConfiguration = new Configuration("stub", "stub");
        mStubMainConfiguration.setLogSaver(mMockLogSaver);

        mTestSuite.setConfiguration(mStubMainConfiguration);
    }

    /**
     * Test that a multi-devices test will execute through without hitting issues since all
     * structures are properly injected.
     */
    @Test
    public void testMultiDeviceITestSuite() throws Exception {
        mTestSuite.setDevice(mMockDevice1);
        mTestSuite.setBuild(mMockBuildInfo1);

        mContext = new InvocationContext();
        mContext.addAllocatedDevice(DEVICE_NAME_1, mMockDevice1);
        mContext.addDeviceBuildInfo(DEVICE_NAME_1, mMockBuildInfo1);
        mContext.addAllocatedDevice(DEVICE_NAME_2, mMockDevice2);
        mContext.addDeviceBuildInfo(DEVICE_NAME_2, mMockBuildInfo2);
        mTestSuite.setInvocationContext(mContext);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(mContext).build();

        mTestSuite.setSystemStatusChecker(new ArrayList<>());

        TestDescription test1 =
                new TestDescription(MultiDeviceStubTest.class.getSimpleName(), "test0");

        TestDescription test2 =
                new TestDescription(MultiDeviceStubTest.class.getSimpleName(), "test1");

        // Target preparation is triggered against the preparer in the second device.
        when(mMockTargetPrep.isDisabled()).thenReturn(false);
        when(mMockTargetPrep.isTearDownDisabled()).thenReturn(true);

        mTestSuite.run(mTestInfo, mMockListener);
        verify(mMockTargetPrep, times(2)).isDisabled();
        verify(mMockListener).testModuleStarted(Mockito.any());
        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("test1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        verify(mMockListener).testStarted(test1, 0L);
        verify(mMockListener).testEnded(test1, 5L, new HashMap<String, Metric>());
        verify(mMockListener).testStarted(test2, 0L);
        verify(mMockListener).testEnded(test2, 5L, new HashMap<String, Metric>());
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener).testModuleEnded();
        verify(mMockTargetPrep).setUp(Mockito.any());
        verify(mMockBuildInfo1, times(1)).getRemoteFiles();
    }
}
