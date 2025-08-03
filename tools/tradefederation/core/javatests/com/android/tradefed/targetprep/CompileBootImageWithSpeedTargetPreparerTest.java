/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.config.OptionSetter;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit Tests for {@link CompileBootImageWithSpeedTargetPreparer}. */
@RunWith(JUnit4.class)
public class CompileBootImageWithSpeedTargetPreparerTest {

    private CompileBootImageWithSpeedTargetPreparer mBootImagePreparer;
    private TestInformation mTestInfo;
    private OptionSetter mSetter;
    @Mock ITestDevice mMockDevice;
    @Mock IBuildInfo mMockBuildInfo;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device", mMockDevice);
        context.addDeviceBuildInfo("device", mMockBuildInfo);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
        mBootImagePreparer = new CompileBootImageWithSpeedTargetPreparer();
        mSetter = new OptionSetter(mBootImagePreparer);
    }

    /** Tests compile boot image with speed. */
    @Test
    public void testSpeed() throws Exception {
        mBootImagePreparer.setUp(mTestInfo);
        verify(mMockDevice, times(1)).executeShellCommand("stop");
        verify(mMockDevice, times(1)).executeShellCommand("setprop dev.bootcomplete 0");
        verify(mMockDevice, times(1))
                .executeShellCommand(mBootImagePreparer.COMPILE_BOOT_IMAGE_SPEED_CMD);
        verify(mMockDevice, times(1)).executeShellCommand("start");
        verify(mMockDevice, times(1)).waitForDeviceAvailable();
    }

    /** Tests compile boot image with speed-profile. */
    @Test
    public void testSpeedProfile() throws Exception {
        mSetter.setOptionValue("compile-boot-image-speed", "false");
        mBootImagePreparer.setUp(mTestInfo);
        verify(mMockDevice, times(1)).executeShellCommand("stop");
        verify(mMockDevice, times(1)).executeShellCommand("setprop dev.bootcomplete 0");
        verify(mMockDevice, times(1))
                .executeShellCommand(mBootImagePreparer.COMPILE_BOOT_IMAGE_SPEED_PROFILE_CMD);
        verify(mMockDevice, times(1)).executeShellCommand("start");
        verify(mMockDevice, times(1)).waitForDeviceAvailable();
    }

    @Test
    public void testTearDown() throws Exception {
        mBootImagePreparer.tearDown(mTestInfo, null);
    }
}
