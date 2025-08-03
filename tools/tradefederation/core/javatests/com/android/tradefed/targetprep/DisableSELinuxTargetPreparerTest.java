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
package com.android.tradefed.targetprep;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit Tests for {@link DisableSELinuxTargetPreparerTest}. */
@RunWith(JUnit4.class)
public class DisableSELinuxTargetPreparerTest {

    private DisableSELinuxTargetPreparer mDisableSELinuxTargetPreparer;
    @Mock ITestDevice mMockDevice;
    @Mock IBuildInfo mMockBuildInfo;
    private TestInformation mTestInfo;
    private static final String PERMISSIVE = "Permissive";
    private static final String ENFORCED = "Enforced";
    private static final String GETENFORCE = "getenforce";
    private static final String SETENFORCE = "setenforce ";

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mDisableSELinuxTargetPreparer = new DisableSELinuxTargetPreparer();

        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device", mMockDevice);
        context.addDeviceBuildInfo("device", mMockBuildInfo);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
    }

    @Test
    public void testSetUpSuccess_permissive() throws Exception {
        CommandResult result = new CommandResult();
        result.setStdout(PERMISSIVE);
        result.setStatus(CommandStatus.SUCCESS);
        when(mMockDevice.executeShellV2Command(GETENFORCE)).thenReturn(result);

        mDisableSELinuxTargetPreparer.setUp(mTestInfo);
        mDisableSELinuxTargetPreparer.tearDown(mTestInfo, null);
        verify(mMockDevice, times(1)).executeShellV2Command(GETENFORCE);
    }

    @Test
    public void testSetUpSuccess_enforced_rootBefore() throws Exception {
        CommandResult result = new CommandResult();
        result.setStdout(ENFORCED);
        result.setStatus(CommandStatus.SUCCESS);
        when(mMockDevice.executeShellV2Command(GETENFORCE)).thenReturn(result);
        when(mMockDevice.isAdbRoot()).thenReturn(true);
        when(mMockDevice.executeShellV2Command(SETENFORCE + "0")).thenReturn(result);
        when(mMockDevice.executeShellV2Command(SETENFORCE + "1")).thenReturn(result);

        mDisableSELinuxTargetPreparer.setUp(mTestInfo);
        mDisableSELinuxTargetPreparer.tearDown(mTestInfo, null);
        verify(mMockDevice, times(1)).executeShellV2Command(GETENFORCE);
        verify(mMockDevice, times(1)).isAdbRoot();
        verify(mMockDevice, times(1)).executeShellV2Command(SETENFORCE + "0");
        verify(mMockDevice, times(1)).executeShellV2Command(SETENFORCE + "1");
    }

    @Test
    public void testSetUpSuccess_enforced_notRootBefore() throws Exception {
        CommandResult result = new CommandResult();
        result.setStdout(ENFORCED);
        result.setStatus(CommandStatus.SUCCESS);
        when(mMockDevice.executeShellV2Command(GETENFORCE)).thenReturn(result);
        when(mMockDevice.isAdbRoot()).thenReturn(false);
        when(mMockDevice.enableAdbRoot()).thenReturn(true);
        when(mMockDevice.disableAdbRoot()).thenReturn(true);

        when(mMockDevice.executeShellV2Command(SETENFORCE + "0")).thenReturn(result);
        when(mMockDevice.executeShellV2Command(SETENFORCE + "1")).thenReturn(result);

        mDisableSELinuxTargetPreparer.setUp(mTestInfo);
        mDisableSELinuxTargetPreparer.tearDown(mTestInfo, null);
        verify(mMockDevice, times(1)).executeShellV2Command(GETENFORCE);
        verify(mMockDevice, times(1)).isAdbRoot();
        verify(mMockDevice, times(2)).enableAdbRoot();
        verify(mMockDevice, times(2)).disableAdbRoot();
        verify(mMockDevice, times(1)).executeShellV2Command(SETENFORCE + "0");
        verify(mMockDevice, times(1)).executeShellV2Command(SETENFORCE + "1");
    }

    @Test(expected = TargetSetupError.class)
    public void testSetUp_rootFail() throws Exception {
        CommandResult result = new CommandResult();
        result.setStdout(ENFORCED);
        when(mMockDevice.executeShellV2Command(GETENFORCE)).thenReturn(result);
        when(mMockDevice.isAdbRoot()).thenReturn(false);
        when(mMockDevice.enableAdbRoot()).thenReturn(false);
        when(mMockDevice.getDeviceDescriptor()).thenReturn(null);
        try {
            mDisableSELinuxTargetPreparer.setUp(mTestInfo);
        } finally {
            verify(mMockDevice, times(1)).executeShellV2Command(GETENFORCE);
            verify(mMockDevice, times(1)).isAdbRoot();
            verify(mMockDevice, times(1)).enableAdbRoot();
            verify(mMockDevice, times(1)).getDeviceDescriptor();
        }
    }

    @Test(expected = TargetSetupError.class)
    public void testSetUp_disableSELinuxFail() throws Exception {
        CommandResult result = new CommandResult();
        result.setStdout(ENFORCED);
        result.setStatus(CommandStatus.FAILED);
        when(mMockDevice.getDeviceDescriptor()).thenReturn(null);
        when(mMockDevice.executeShellV2Command(GETENFORCE)).thenReturn(result);
        when(mMockDevice.isAdbRoot()).thenReturn(false);
        when(mMockDevice.enableAdbRoot()).thenReturn(true);
        when(mMockDevice.executeShellV2Command(SETENFORCE + "0")).thenReturn(result);
        try {
            mDisableSELinuxTargetPreparer.setUp(mTestInfo);
        } finally {
            verify(mMockDevice, times(1)).executeShellV2Command(GETENFORCE);
            verify(mMockDevice, times(1)).isAdbRoot();
            verify(mMockDevice, times(1)).enableAdbRoot();
            verify(mMockDevice, times(1)).executeShellV2Command(SETENFORCE + "0");
        }
    }
}
