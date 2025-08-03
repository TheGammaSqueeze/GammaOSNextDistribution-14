/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IShellOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.targetprep.ArtChrootPreparer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link ArtGTest}. */
@RunWith(JUnit4.class)
public class ArtGTestTest {
    @Mock ITestInvocationListener mMockInvocationListener;
    @Mock IShellOutputReceiver mMockReceiver;
    @Mock ITestDevice mMockITestDevice;
    private GTest mGTest;
    private TestInformation mTestInfo;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mGTest =
                new ArtGTest() {
                    @Override
                    IShellOutputReceiver createResultParser(
                            String runName, ITestInvocationListener listener) {
                        return mMockReceiver;
                    }
                };
        mGTest.setDevice(mMockITestDevice);
        mGTest.setNativeTestDevicePath(ArtChrootPreparer.CHROOT_PATH);
        mTestInfo = TestInformation.newBuilder().build();

        when(mMockITestDevice.getSerialNumber()).thenReturn("serial");
    }

    @Test
    public void testChroot_testRun() throws DeviceNotAvailableException {
        final String nativeTestPath = ArtChrootPreparer.CHROOT_PATH;
        final String test1 = "test1";
        final String testPath1 = String.format("%s/%s", nativeTestPath, test1);
        final String findCmd = String.format("find -L %s -type f -perm -a=x", nativeTestPath);

        when(mMockITestDevice.doesFileExist(nativeTestPath)).thenReturn(true);
        when(mMockITestDevice.executeShellCommand(findCmd)).thenReturn(testPath1);

        mGTest.run(mTestInfo, mMockInvocationListener);

        verify(mMockITestDevice)
                .executeShellCommand(
                        Mockito.startsWith("chroot /data/local/tmp/art-test-chroot /test1"),
                        Mockito.any(),
                        Mockito.anyLong(),
                        Mockito.any(),
                        Mockito.anyInt());
    }
}
