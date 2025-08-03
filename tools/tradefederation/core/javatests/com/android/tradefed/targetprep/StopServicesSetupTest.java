/*
 * Copyright (C) 2012 The Android Open Source Project
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;

import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link StopServicesSetup} */
@RunWith(JUnit4.class)
public class StopServicesSetupTest {

    private StopServicesSetup mPreparer = null;
    @Mock ITestDevice mMockDevice;
    private TestInformation mTestInfo = null;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mPreparer = new StopServicesSetup();
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device", mMockDevice);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
    }

    /** Test that the framework is stopped in the default case. */
    @Test
    public void testNoop() throws DeviceNotAvailableException {
        when(mMockDevice.executeShellCommand("stop")).thenReturn(null);

        mPreparer.setUp(mTestInfo);
        verify(mMockDevice).executeShellCommand("stop");
        verifyNoMoreInteractions(mMockDevice);
    }

    /** Test that stopping the framework can be overwritten. */
    @Test
    public void testNoStopFramework() throws DeviceNotAvailableException {
        mPreparer.setStopFramework(false);

        mPreparer.setUp(mTestInfo);
        verifyNoMoreInteractions(mMockDevice);
    }

    /** Test that additional services are stopped if specified. */
    @Test
    public void testStopServices() throws DeviceNotAvailableException {
        mPreparer.addService("service1");
        mPreparer.addService("service2");

        when(mMockDevice.executeShellCommand("stop")).thenReturn(null);
        when(mMockDevice.executeShellCommand("stop service1")).thenReturn(null);
        when(mMockDevice.executeShellCommand("stop service2")).thenReturn(null);

        mPreparer.setUp(mTestInfo);

        InOrder inOrder = inOrder(mMockDevice);
        inOrder.verify(mMockDevice).executeShellCommand("stop");
        inOrder.verify(mMockDevice).executeShellCommand("stop service1");
        inOrder.verify(mMockDevice).executeShellCommand("stop service2");
        verifyNoMoreInteractions(mMockDevice);
    }

    /** Test that framework and services are started during tearDown. */
    @Test
    public void testTearDown() throws DeviceNotAvailableException {
        mPreparer.addService("service1");
        mPreparer.addService("service2");

        when(mMockDevice.executeShellCommand("start")).thenReturn(null);

        when(mMockDevice.executeShellCommand("start service1")).thenReturn(null);
        when(mMockDevice.executeShellCommand("start service2")).thenReturn(null);
        mPreparer.tearDown(mTestInfo, null);

        InOrder inOrder = inOrder(mMockDevice);
        inOrder.verify(mMockDevice).executeShellCommand("start");
        inOrder.verify(mMockDevice).waitForDeviceAvailable();
        inOrder.verify(mMockDevice).executeShellCommand("start service1");
        inOrder.verify(mMockDevice).executeShellCommand("start service2");
        verify(mMockDevice).waitForDeviceAvailable();
        verifyNoMoreInteractions(mMockDevice);
    }
}
