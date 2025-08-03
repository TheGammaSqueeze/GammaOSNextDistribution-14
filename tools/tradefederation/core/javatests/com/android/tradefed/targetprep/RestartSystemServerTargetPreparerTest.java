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
package com.android.tradefed.targetprep;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationDef;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit Tests for {@link RestartSystemServerTargetPreparer}. */
@RunWith(JUnit4.class)
public class RestartSystemServerTargetPreparerTest {

    private RestartSystemServerTargetPreparer mRestartSystemServerTargetPreparer;
    private TestInformation mTestInformation;
    @Mock ITestDevice mMockDevice;
    @Mock IBuildInfo mMockBuildInfo;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice(ConfigurationDef.DEFAULT_DEVICE_NAME, mMockDevice);
        context.addDeviceBuildInfo(ConfigurationDef.DEFAULT_DEVICE_NAME, mMockBuildInfo);
        mTestInformation = TestInformation.newBuilder().setInvocationContext(context).build();
        mRestartSystemServerTargetPreparer = new RestartSystemServerTargetPreparer();
    }

    @Test
    public void testSetUp_bootComplete() throws Exception {
        when(mMockDevice.enableAdbRoot()).thenReturn(true);
        when(mMockDevice.executeShellCommand("setprop dev.bootcomplete 0")).thenReturn(null);
        when(mMockDevice.executeShellCommand(RestartSystemServerTargetPreparer.KILL_SERVER_COMMAND))
                .thenReturn(null);

        mRestartSystemServerTargetPreparer.setUp(mTestInformation);
        verify(mMockDevice, times(1)).waitForDeviceAvailable();
        verify(mMockDevice, times(1)).enableAdbRoot();
        verify(mMockDevice, times(1))
                .executeShellCommand(RestartSystemServerTargetPreparer.KILL_SERVER_COMMAND);
    }

    @Test(expected = DeviceNotAvailableException.class)
    public void testSetUp_bootNotComplete() throws Exception {
        when(mMockDevice.enableAdbRoot()).thenReturn(true);
        when(mMockDevice.executeShellCommand("setprop dev.bootcomplete 0")).thenReturn(null);
        when(mMockDevice.executeShellCommand(RestartSystemServerTargetPreparer.KILL_SERVER_COMMAND))
                .thenReturn(null);
        doThrow(new DeviceNotAvailableException("test", "serial"))
                .when(mMockDevice)
                .waitForDeviceAvailable();

        mRestartSystemServerTargetPreparer.setUp(mTestInformation);
        verify(mMockDevice, times(1)).enableAdbRoot();
        verify(mMockDevice, times(1))
                .executeShellCommand(RestartSystemServerTargetPreparer.KILL_SERVER_COMMAND);
    }

    @Test
    public void testTearDown_restart() throws Exception {
        OptionSetter optionSetter = new OptionSetter(mRestartSystemServerTargetPreparer);
        optionSetter.setOptionValue("restart-setup", "false");
        optionSetter.setOptionValue("restart-teardown", "true");

        when(mMockDevice.enableAdbRoot()).thenReturn(true);
        when(mMockDevice.executeShellCommand("setprop dev.bootcomplete 0")).thenReturn(null);
        when(mMockDevice.executeShellCommand(RestartSystemServerTargetPreparer.KILL_SERVER_COMMAND))
                .thenReturn(null);

        mRestartSystemServerTargetPreparer.tearDown(mTestInformation, null);
        verify(mMockDevice, times(1)).waitForDeviceAvailable();
        verify(mMockDevice, times(1)).enableAdbRoot();
        verify(mMockDevice, times(1))
                .executeShellCommand(RestartSystemServerTargetPreparer.KILL_SERVER_COMMAND);
    }

    @Test
    public void testNone() throws Exception {
        OptionSetter optionSetter = new OptionSetter(mRestartSystemServerTargetPreparer);
        optionSetter.setOptionValue("restart-setup", "false");
        optionSetter.setOptionValue("restart-teardown", "false");

        mRestartSystemServerTargetPreparer.setUp(mTestInformation);
    }
}
