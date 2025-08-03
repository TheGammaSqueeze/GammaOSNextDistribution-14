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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.ConfigurationDef;
import com.android.tradefed.config.DeviceConfigurationHolder;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IDeviceConfiguration;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.testtype.IRemoteTest;

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
import java.util.Map;

/** Unit tests for {@link ModuleDefinition} when exercised by a multi-device config. */
@RunWith(JUnit4.class)
public class ModuleDefinitionMultiTest {

    private static final String MODULE_NAME = "fakeName";
    private static final String DEVICE_NAME_1 = "device1";
    private static final String DEVICE_NAME_2 = "device2";

    private ModuleDefinition mModule;
    private List<IRemoteTest> mTestList;
    private Map<String, List<ITargetPreparer>> mMapDeviceTargetPreparer;

    @Mock ITestDevice mDevice1;
    @Mock ITestDevice mDevice2;

    @Mock IBuildInfo mBuildInfo1;
    @Mock IBuildInfo mBuildInfo2;

    @Mock ITestInvocationListener mListener;

    @Mock ITargetPreparer mMockTargetPrep;

    private IConfiguration mMultiDeviceConfiguration;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mMultiDeviceConfiguration = new Configuration("name", "description");
        List<IDeviceConfiguration> deviceConfigList = new ArrayList<>();
        deviceConfigList.add(new DeviceConfigurationHolder(DEVICE_NAME_1));
        deviceConfigList.add(new DeviceConfigurationHolder(DEVICE_NAME_2));
        mMultiDeviceConfiguration.setDeviceConfigList(deviceConfigList);

        when(mDevice1.getDeviceDate()).thenReturn(0L);
        when(mDevice1.getIDevice()).thenReturn(mock(IDevice.class));

        when(mDevice2.getDeviceDate()).thenReturn(0L);
        when(mDevice2.getIDevice()).thenReturn(mock(IDevice.class));

        mTestList = new ArrayList<>();

        mMapDeviceTargetPreparer = new LinkedHashMap<>();
        mMapDeviceTargetPreparer.put(DEVICE_NAME_1, new ArrayList<>());
        mMapDeviceTargetPreparer.put(DEVICE_NAME_2, new ArrayList<>());
    }

    /**
     * Create a multi device module and run it. Ensure that target preparers against each device are
     * running.
     */
    @Test
    public void testCreateAndRun() throws Exception {
        // Add a preparer to the second device
        mMapDeviceTargetPreparer.get(DEVICE_NAME_2).add(mMockTargetPrep);

        mModule =
                new ModuleDefinition(
                        MODULE_NAME,
                        mTestList,
                        mMapDeviceTargetPreparer,
                        new ArrayList<>(),
                        mMultiDeviceConfiguration);
        // Simulate injection of devices from ITestSuite
        mModule.getModuleInvocationContext().addAllocatedDevice(DEVICE_NAME_1, mDevice1);
        mModule.getModuleInvocationContext().addDeviceBuildInfo(DEVICE_NAME_1, mBuildInfo1);
        mModule.getModuleInvocationContext().addAllocatedDevice(DEVICE_NAME_2, mDevice2);
        mModule.getModuleInvocationContext().addDeviceBuildInfo(DEVICE_NAME_2, mBuildInfo2);

        TestInformation moduleInfo =
                TestInformation.newBuilder()
                        .setInvocationContext(mModule.getModuleInvocationContext())
                        .build();

        // Target preparation is triggered against the preparer in the second device.
        when(mMockTargetPrep.isDisabled()).thenReturn(false);

        when(mMockTargetPrep.isTearDownDisabled()).thenReturn(true);
        mModule.run(moduleInfo, mListener, null, null, 1);
        verify(mMockTargetPrep, times(2)).isDisabled();
        verify(mListener)
                .testRunStarted(
                        Mockito.eq(MODULE_NAME), Mockito.eq(0), Mockito.eq(0), Mockito.anyLong());
        verify(mListener).testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        verify(mMockTargetPrep).setUp(moduleInfo);
    }

    /** Create a single device module running against a multi device main configuration. */
    @Test
    public void testPreparer_mismatch() throws Exception {
        // The module configuration only contains the default device.
        mMapDeviceTargetPreparer.clear();
        List<ITargetPreparer> preparers = new ArrayList<>();
        preparers.add(mMockTargetPrep);
        mMapDeviceTargetPreparer.put(ConfigurationDef.DEFAULT_DEVICE_NAME, preparers);

        mModule =
                new ModuleDefinition(
                        MODULE_NAME,
                        mTestList,
                        mMapDeviceTargetPreparer,
                        new ArrayList<>(),
                        mMultiDeviceConfiguration);
        // Simulate injection of devices from ITestSuite
        mModule.getModuleInvocationContext().addAllocatedDevice(DEVICE_NAME_1, mDevice1);
        mModule.getModuleInvocationContext().addDeviceBuildInfo(DEVICE_NAME_1, mBuildInfo1);
        mModule.getModuleInvocationContext().addAllocatedDevice(DEVICE_NAME_2, mDevice2);
        mModule.getModuleInvocationContext().addDeviceBuildInfo(DEVICE_NAME_2, mBuildInfo2);

        TestInformation moduleInfo =
                TestInformation.newBuilder()
                        .setInvocationContext(mModule.getModuleInvocationContext())
                        .build();

        // Target preparation is of first device in module configuration is triggered against the
        // first device from main configuration
        when(mMockTargetPrep.isDisabled()).thenReturn(false);

        when(mMockTargetPrep.isTearDownDisabled()).thenReturn(true);
        mModule.run(moduleInfo, mListener, null, null, 1);
        verify(mMockTargetPrep, times(2)).isDisabled();
        verify(mListener)
                .testRunStarted(
                        Mockito.eq(MODULE_NAME), Mockito.eq(0), Mockito.eq(0), Mockito.anyLong());
        verify(mListener).testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        verify(mMockTargetPrep).setUp(moduleInfo);
    }
}
