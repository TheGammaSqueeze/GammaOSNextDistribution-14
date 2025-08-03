/*
 * Copyright (C) 2011 The Android Open Source Project
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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;

@RunWith(JUnit4.class)
public class SystemUpdaterDeviceFlasherTest {

    private static final String A_BUILD_ID = "1";

    private static final String TEST_STRING = "foo";

    private SystemUpdaterDeviceFlasher mFlasher;

    @Mock ITestDevice mMockDevice;
    @Mock IDeviceBuildInfo mMockDeviceBuild;

    private InOrder mInOrder;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mInOrder = inOrder(mMockDevice, mMockDeviceBuild);

        ITestsZipInstaller mockZipInstaller = mock(ITestsZipInstaller.class);
        mFlasher = new SystemUpdaterDeviceFlasher();
        mFlasher.setTestsZipInstaller(mockZipInstaller);
        when(mMockDevice.getSerialNumber()).thenReturn(TEST_STRING);
        when(mMockDevice.getProductType()).thenReturn(TEST_STRING);
        when(mMockDevice.getDeviceDescriptor()).thenReturn(null);
    }

    @Test
    public void testFlash() throws DeviceNotAvailableException, TargetSetupError {
        yieldDifferentBuilds(true);
        File fakeImage = new File("fakeImageFile");
        when(mMockDeviceBuild.getOtaPackageFile()).thenReturn(fakeImage);
        when(mMockDevice.pushFile(fakeImage, "/cache/update.zip", true)).thenReturn(true);
        String commandsRegex =
                "echo +--update_package +> +/cache/recovery/command +&& *"
                        + "echo +/cache/update.zip +>> +/cache/recovery/command";
        when(mMockDevice.executeShellCommand(Mockito.matches(commandsRegex))).thenReturn("foo");

        mFlasher.flash(mMockDevice, mMockDeviceBuild);

        mInOrder.verify(mMockDeviceBuild).getOtaPackageFile();
        mInOrder.verify(mMockDevice)
                .pushFile(Mockito.eq(fakeImage), Mockito.eq("/cache/update.zip"), Mockito.eq(true));
        mInOrder.verify(mMockDevice).executeShellCommand(Mockito.matches(commandsRegex));
        mInOrder.verify(mMockDevice).rebootIntoRecovery();
        mInOrder.verify(mMockDevice).waitForDeviceAvailable();
        mInOrder.verify(mMockDevice).reboot();
    }

    @Test
    public void testFlash_noOta() throws DeviceNotAvailableException {
        yieldDifferentBuilds(true);
        when(mMockDeviceBuild.getOtaPackageFile()).thenReturn(null);

        try {
            mFlasher.flash(mMockDevice, mMockDeviceBuild);
            fail(
                    "didn't throw expected exception when OTA is missing: "
                            + TargetSetupError.class.getSimpleName());
        } catch (TargetSetupError e) {
            assertTrue(true);
        } finally {
            verify(mMockDeviceBuild).getOtaPackageFile();
        }
    }

    @Test
    public void testFlashSameBuild() throws DeviceNotAvailableException, TargetSetupError {
        yieldDifferentBuilds(false);
        mFlasher.flash(mMockDevice, mMockDeviceBuild);

        mInOrder.verify(mMockDeviceBuild, times(0)).getOtaPackageFile();
        mInOrder.verify(mMockDevice, times(0))
                .pushFile(Mockito.any(), Mockito.eq("/cache/update.zip"));
        mInOrder.verify(mMockDevice, times(0)).executeShellCommand(Mockito.any());
        mInOrder.verify(mMockDevice, times(0)).rebootIntoRecovery();
        mInOrder.verify(mMockDevice, times(0)).waitForDeviceAvailable();
        mInOrder.verify(mMockDevice, times(0)).reboot();
    }

    private void yieldDifferentBuilds(boolean different) throws DeviceNotAvailableException {
        when(mMockDevice.getBuildId()).thenReturn(A_BUILD_ID);
        when(mMockDeviceBuild.getDeviceBuildId())
                .thenReturn((different ? A_BUILD_ID + 1 : A_BUILD_ID));
    }
}
