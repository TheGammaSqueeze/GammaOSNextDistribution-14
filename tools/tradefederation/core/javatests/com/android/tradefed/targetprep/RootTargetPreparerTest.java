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

import com.android.ddmlib.IDevice;
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

/** Unit Tests for {@link RootTargetPreparer}. */
@RunWith(JUnit4.class)
public class RootTargetPreparerTest {

    private RootTargetPreparer mRootTargetPreparer;
    @Mock ITestDevice mMockDevice;
    @Mock IDevice mMockIDevice;
    @Mock IBuildInfo mMockBuildInfo;
    private TestInformation mTestInfo;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mRootTargetPreparer = new RootTargetPreparer();

        when(mMockDevice.getIDevice()).thenReturn(mMockIDevice);

        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device", mMockDevice);
        context.addDeviceBuildInfo("device", mMockBuildInfo);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
    }

    @Test
    public void testSetUpSuccess_rootBefore() throws Exception {
        when(mMockDevice.isAdbRoot()).thenReturn(true);

        mRootTargetPreparer.setUp(mTestInfo);
        mRootTargetPreparer.tearDown(mTestInfo, null);
        verify(mMockDevice, times(1)).isAdbRoot();
    }

    @Test
    public void testSetUpSuccess_notRootBefore() throws Exception {
        when(mMockDevice.isAdbRoot()).thenReturn(false);
        when(mMockDevice.enableAdbRoot()).thenReturn(true);
        when(mMockDevice.disableAdbRoot()).thenReturn(true);

        mRootTargetPreparer.setUp(mTestInfo);
        mRootTargetPreparer.tearDown(mTestInfo, null);
        verify(mMockDevice, times(1)).isAdbRoot();
        verify(mMockDevice, times(1)).enableAdbRoot();
        verify(mMockDevice, times(1)).disableAdbRoot();
    }

    @Test(expected = TargetSetupError.class)
    public void testSetUpFail() throws Exception {
        when(mMockDevice.isAdbRoot()).thenReturn(false);
        when(mMockDevice.enableAdbRoot()).thenReturn(false);
        when(mMockDevice.getDeviceDescriptor()).thenReturn(null);

        mRootTargetPreparer.setUp(mTestInfo);
    }

    @Test
    public void testSetUpSuccess_rootBefore_forceUnroot() throws Exception {
        OptionSetter setter = new OptionSetter(mRootTargetPreparer);
        setter.setOptionValue("force-root", "false");

        when(mMockDevice.isAdbRoot()).thenReturn(true);
        when(mMockDevice.disableAdbRoot()).thenReturn(true);
        when(mMockDevice.enableAdbRoot()).thenReturn(true);

        mRootTargetPreparer.setUp(mTestInfo);
        mRootTargetPreparer.tearDown(mTestInfo, null);
        verify(mMockDevice, times(1)).isAdbRoot();
        verify(mMockDevice, times(1)).disableAdbRoot();
        verify(mMockDevice, times(1)).enableAdbRoot();
    }

    @Test
    public void testSetUpSuccess_notRootBefore_forceUnroot() throws Exception {
        OptionSetter setter = new OptionSetter(mRootTargetPreparer);
        setter.setOptionValue("force-root", "false");

        when(mMockDevice.isAdbRoot()).thenReturn(false);

        mRootTargetPreparer.setUp(mTestInfo);
        mRootTargetPreparer.tearDown(mTestInfo, null);
        verify(mMockDevice, times(1)).isAdbRoot();
    }

    @Test(expected = TargetSetupError.class)
    public void testSetUpFail_forceUnroot() throws Exception {
        OptionSetter setter = new OptionSetter(mRootTargetPreparer);
        setter.setOptionValue("force-root", "false");

        when(mMockDevice.isAdbRoot()).thenReturn(true);
        when(mMockDevice.disableAdbRoot()).thenReturn(false);
        when(mMockDevice.getDeviceDescriptor()).thenReturn(null);

        mRootTargetPreparer.setUp(mTestInfo);
    }

    @Test
    public void testSetUpFail_forceRoot_ignoresFailure() throws Exception {
        OptionSetter setter = new OptionSetter(mRootTargetPreparer);
        setter.setOptionValue("throw-on-error", "false");

        when(mMockDevice.isAdbRoot()).thenReturn(false);
        when(mMockDevice.enableAdbRoot()).thenReturn(false);
        when(mMockDevice.getDeviceDescriptor()).thenReturn(null);
        when(mMockDevice.disableAdbRoot()).thenReturn(true);

        mRootTargetPreparer.setUp(mTestInfo);
        mRootTargetPreparer.tearDown(mTestInfo, null);
    }

    @Test
    public void testSetUpFail_forceUnroot_ignoresFailure() throws Exception {
        OptionSetter setter = new OptionSetter(mRootTargetPreparer);
        setter.setOptionValue("force-root", "false");
        setter.setOptionValue("throw-on-error", "false");

        when(mMockDevice.isAdbRoot()).thenReturn(true);
        when(mMockDevice.disableAdbRoot()).thenReturn(false);
        when(mMockDevice.getDeviceDescriptor()).thenReturn(null);
        when(mMockDevice.enableAdbRoot()).thenReturn(true);

        mRootTargetPreparer.setUp(mTestInfo);
        mRootTargetPreparer.tearDown(mTestInfo, null);
        verify(mMockDevice, times(1)).isAdbRoot();
        verify(mMockDevice, times(1)).disableAdbRoot();
        verify(mMockDevice, times(1)).getDeviceDescriptor();
        verify(mMockDevice, times(1)).enableAdbRoot();
    }
}
