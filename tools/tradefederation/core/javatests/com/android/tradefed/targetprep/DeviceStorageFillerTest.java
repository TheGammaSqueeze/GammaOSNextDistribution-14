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

/** Unit Tests for {@link DeviceStorageFiller}. */
@RunWith(JUnit4.class)
public class DeviceStorageFillerTest {
    private DeviceStorageFiller mDeviceStorageFiller;
    @Mock ITestDevice mMockDevice;
    @Mock IBuildInfo mMockBuildInfo;
    private TestInformation mTestInfo;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mDeviceStorageFiller = new DeviceStorageFiller();

        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device", mMockDevice);
        context.addDeviceBuildInfo("device", mMockBuildInfo);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
    }

    @Test
    public void testSetUpWriteFile() throws Exception {
        OptionSetter optionSetter = new OptionSetter(mDeviceStorageFiller);
        optionSetter.setOptionValue("free-bytes", "24");
        optionSetter.setOptionValue("partition", "/p");
        optionSetter.setOptionValue("file-name", "f");

        when(mMockDevice.getPartitionFreeSpace("/p")).thenReturn(1L);
        when(mMockDevice.executeShellCommand("fallocate -l 1000 /p/f")).thenReturn(null);

        mDeviceStorageFiller.setUp(mTestInfo);
        verify(mMockDevice, times(1)).getPartitionFreeSpace("/p");
        verify(mMockDevice, times(1)).executeShellCommand("fallocate -l 1000 /p/f");
    }

    @Test
    public void testSetUpSkip() throws Exception {
        OptionSetter optionSetter = new OptionSetter(mDeviceStorageFiller);
        optionSetter.setOptionValue("free-bytes", "2000");
        optionSetter.setOptionValue("partition", "/p");
        optionSetter.setOptionValue("file-name", "f");
        when(mMockDevice.getPartitionFreeSpace("/p")).thenReturn(1L);

        mDeviceStorageFiller.setUp(mTestInfo);
        verify(mMockDevice, times(1)).getPartitionFreeSpace("/p");
    }

    @Test
    public void testTearDown() throws Exception {
        OptionSetter optionSetter = new OptionSetter(mDeviceStorageFiller);
        optionSetter.setOptionValue("free-bytes", "24");
        optionSetter.setOptionValue("partition", "/p");
        optionSetter.setOptionValue("file-name", "f");
        when(mMockDevice.executeShellCommand("rm -f /p/f")).thenReturn(null);

        mDeviceStorageFiller.tearDown(mTestInfo, null);
        verify(mMockDevice, times(1)).executeShellCommand("rm -f /p/f");
    }
}
