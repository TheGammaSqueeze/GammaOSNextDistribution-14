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

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionSetter;
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

import java.io.File;

/** Unit Tests for {@link DeviceStringPusher}. */
@RunWith(JUnit4.class)
public class DeviceStringPusherTest {
    private DeviceStringPusher mDeviceStringPusher;
    @Mock ITestDevice mMockDevice;
    @Mock IBuildInfo mMockBuildInfo;
    private TestInformation mTestInfo;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mDeviceStringPusher = new DeviceStringPusher();

        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device", mMockDevice);
        context.addDeviceBuildInfo("device", mMockBuildInfo);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
    }

    @Test(expected = TargetSetupError.class)
    public void testFail() throws Exception {
        OptionSetter optionSetter = new OptionSetter(mDeviceStringPusher);
        optionSetter.setOptionValue("file-path", "file");
        optionSetter.setOptionValue("file-content", "hi");
        when(mMockDevice.doesFileExist("file")).thenReturn(false);
        when(mMockDevice.pushString("hi", "file")).thenReturn(false);
        when(mMockDevice.getDeviceDescriptor()).thenReturn(null);

        mDeviceStringPusher.setUp(mTestInfo);

        verify(mMockDevice, times(1)).doesFileExist("file");
        verify(mMockDevice, times(1)).pushString("hi", "file");
        verify(mMockDevice, times(1)).getDeviceDescriptor();
    }

    @Test
    public void testDoesntExist() throws Exception {
        OptionSetter optionSetter = new OptionSetter(mDeviceStringPusher);
        optionSetter.setOptionValue("file-path", "file");
        optionSetter.setOptionValue("file-content", "hi");
        when(mMockDevice.doesFileExist("file")).thenReturn(false);
        when(mMockDevice.pushString("hi", "file")).thenReturn(true);

        mDeviceStringPusher.setUp(mTestInfo);
        mDeviceStringPusher.tearDown(mTestInfo, null);

        verify(mMockDevice, times(1)).doesFileExist("file");
        verify(mMockDevice, times(1)).pushString("hi", "file");
        verify(mMockDevice).deleteFile("file");
    }

    @Test
    public void testAlreadyExists() throws Exception {
        OptionSetter optionSetter = new OptionSetter(mDeviceStringPusher);
        File file = new File("a");
        optionSetter.setOptionValue("file-path", "file");
        optionSetter.setOptionValue("file-content", "hi");
        when(mMockDevice.doesFileExist("file")).thenReturn(true);
        when(mMockDevice.pullFile("file")).thenReturn(file);
        when(mMockDevice.pushString("hi", "file")).thenReturn(true);
        when(mMockDevice.pushFile(file, "file")).thenReturn(true);

        mDeviceStringPusher.setUp(mTestInfo);
        mDeviceStringPusher.tearDown(mTestInfo, null);

        verify(mMockDevice, times(1)).doesFileExist("file");
        verify(mMockDevice, times(1)).pullFile("file");
        verify(mMockDevice, times(1)).pushString("hi", "file");
        verify(mMockDevice, times(1)).pushFile(file, "file");
    }
}
