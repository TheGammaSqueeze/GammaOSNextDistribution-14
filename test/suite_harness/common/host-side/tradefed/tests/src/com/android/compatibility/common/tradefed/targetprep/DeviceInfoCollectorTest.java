/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.compatibility.common.tradefed.targetprep;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.android.compatibility.common.tradefed.targetprep.ApkInstrumentationPreparer.When;
import com.android.compatibility.common.util.FileUtil;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;

/** Unit tests for {@link DeviceInfoCollector}. */
@RunWith(JUnit4.class)
public class DeviceInfoCollectorTest {

    private DeviceInfoCollector mCollector;
    private ITestDevice mDevice;
    private IBuildInfo mBuildInfo;
    private TestInformation mTestInfo;

    @Before
    public void setUp() throws Exception {
        mCollector = new DeviceInfoCollector();
        mDevice = Mockito.mock(ITestDevice.class);
        mBuildInfo = new BuildInfo();
        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("device", mBuildInfo);
        context.addAllocatedDevice("device", mDevice);
        OptionSetter setter = new OptionSetter(mCollector);
        setter.setOptionValue("src-dir", "/sdcard/device-info-files/");
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
    }

    @After
    public void tearDown() {
        mBuildInfo.cleanUp();
    }

    @Test
    public void pullDeviceInfo_fail() throws Exception {
        mCollector.mWhen = When.AFTER;
        when(mDevice.getProperty(Mockito.any())).thenReturn("value");
        when(
                mDevice.pullDir(
                        Mockito.eq("/sdcard/device-info-files/"), Mockito.any()))
                .then(new Answer<Boolean>() {
            @Override
            public Boolean answer(InvocationOnMock invocation) throws Throwable {
                File dir = (File) invocation.getArgument(1);
                FileUtil.recursiveDelete(dir);
                return true;
            }
        });

        mCollector.setUp(mTestInfo);
        assertNull(mBuildInfo.getFile(DeviceInfoCollector.DEVICE_INFO_DIR));
    }

    @Test
    public void pullDeviceInfo() throws Exception {
        mCollector.mWhen = When.AFTER;
        when(mDevice.getProperty(Mockito.any())).thenReturn("value");
        when(
                        mDevice.pullDir(
                                Mockito.eq("/sdcard/device-info-files/"), Mockito.any()))
                .thenReturn(true);

        mCollector.setUp(mTestInfo);
        File infoDir = mBuildInfo.getFile(DeviceInfoCollector.DEVICE_INFO_DIR);
        assertNotNull(infoDir);
        assertTrue(infoDir.isDirectory());
    }
}
