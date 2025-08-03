/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.NativeDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.FileUtil;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;

/** Unit tests for {@link InteractiveResultCollector}. */
@RunWith(JUnit4.class)
public final class InteractiveResultCollectorTest {

    private static final String DEVICE_PATH = "/sdcard/documents/xts/screenshot";

    private final InteractiveResultCollector mCollector = new InteractiveResultCollector();
    private OptionSetter mOptionSetter;
    private TestInformation mTestInfo;

    @Test
    public void setUp_nonDeviceBuildInfo_throwException() throws Exception {
        initTestInfo(new BuildInfo(), mock(NativeDevice.class));

        assertThrows(TargetSetupError.class, () -> mCollector.setUp(mTestInfo));
    }

    @Test
    public void setUp_deviceCleanup_emptyDevicePaths_doNothing() throws Exception {
        ITestDevice testDevice = mock(ITestDevice.class);
        initTestInfo(new DeviceBuildInfo("0", ""), testDevice);

        mCollector.setUp(mTestInfo);

        verify(testDevice, never())
                .executeAdbCommand(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    public void setUp_deviceClenup_emptyDevicePathSkipped() throws Exception {
        ITestDevice testDevice = mock(ITestDevice.class);
        initTestInfo(new DeviceBuildInfo("0", ""), testDevice);
        mOptionSetter.setOptionValue("device-paths", "");
        mOptionSetter.setOptionValue("device-paths", DEVICE_PATH);
        mOptionSetter.setOptionValue("device-paths", "");

        mCollector.setUp(mTestInfo);

        // Only one execution for DEVICE_PATH.
        verify(testDevice).executeAdbCommand(anyString(), anyString(), anyString(), anyString());
        verify(testDevice).executeAdbCommand("shell", "rm", "-rf", DEVICE_PATH);
    }

    @Test
    public void tearDown_deviceNotAvailableException_doNothing() throws Exception {
        mCollector.tearDown(/* testInfo= */ null, new DeviceNotAvailableException("", ""));
    }

    @Test
    public void tearDown_emptyDevicePaths_doNothing() throws Exception {
        mCollector.tearDown(/* testInfo= */ null, /* e= */ null);
    }

    @Test
    public void tearDown_failToGetResultDir_doNothing() throws Exception {
        initTestInfo(new DeviceBuildInfo("0", ""), mock(NativeDevice.class));
        mOptionSetter.setOptionValue("device-paths", DEVICE_PATH);

        mCollector.tearDown(mTestInfo, /* e= */ null);
    }

    @Test
    public void tearDown_pullDir_emptyDevicePathSkipped() throws Exception {
        ITestDevice testDevice = mock(ITestDevice.class);
        IDeviceBuildInfo buildInfo = new DeviceBuildInfo("0", "");
        // Init the resultDir.
        File rootDirForTesting = FileUtil.createTempDir("InteractiveResultCollectorTest");
        buildInfo.addBuildAttribute(
                CompatibilityBuildHelper.ROOT_DIR, rootDirForTesting.getAbsolutePath());
        buildInfo.addBuildAttribute(CompatibilityBuildHelper.SUITE_NAME, "cts");
        buildInfo.addBuildAttribute(CompatibilityBuildHelper.START_TIME_MS, "1000000");
        new File(rootDirForTesting, "android-cts").mkdir();

        initTestInfo(buildInfo, testDevice);
        mOptionSetter.setOptionValue("device-paths", "");
        mOptionSetter.setOptionValue("device-paths", DEVICE_PATH);
        mOptionSetter.setOptionValue("device-paths", "");

        mCollector.tearDown(mTestInfo, /* e= */ null);

        // Only one execution for DEVICE_PATH.
        verify(testDevice).pullDir(anyString(), any(File.class));
        verify(testDevice).pullDir(eq(DEVICE_PATH), any(File.class));
    }

    /**
     * Initializes the {@link TestInformation} for tests by the given {@link IBuildInfo} and {@link
     * ITestDevice}.
     */
    private void initTestInfo(IBuildInfo buildInfo, ITestDevice testDevice) throws Exception {
        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("device", buildInfo);
        context.addAllocatedDevice("device", testDevice);

        mOptionSetter = new OptionSetter(mCollector);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
    }
}
