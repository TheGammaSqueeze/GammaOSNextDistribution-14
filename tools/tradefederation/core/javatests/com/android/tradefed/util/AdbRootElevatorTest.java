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
package com.android.tradefed.util;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.INativeDevice;

import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link AdbRootElevator}. */
@RunWith(JUnit4.class)
public class AdbRootElevatorTest {
    @Mock INativeDevice mMockDevice;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @SuppressWarnings("try")
    @Test
    public void testEnablesAndDisablesRoot() throws Exception {
        when(mMockDevice.isAdbRoot()).thenReturn(false);
        when(mMockDevice.enableAdbRoot()).thenReturn(true);

        try (AdbRootElevator adbRoot = new AdbRootElevator(mMockDevice)) {
            mMockDevice.waitForDeviceAvailable();
        }

        InOrder inOrder = Mockito.inOrder(mMockDevice);
        inOrder.verify(mMockDevice).isAdbRoot();
        inOrder.verify(mMockDevice).enableAdbRoot();
        inOrder.verify(mMockDevice).waitForDeviceAvailable();
        inOrder.verify(mMockDevice).disableAdbRoot();
    }

    @SuppressWarnings("try")
    @Test
    public void testRootAlreadyEnabled_doesNotEnableOrDisableRoot() throws Exception {
        when(mMockDevice.isAdbRoot()).thenReturn(true);

        try (AdbRootElevator adbRoot = new AdbRootElevator(mMockDevice)) {
            mMockDevice.waitForDeviceAvailable();
        }

        InOrder inOrder = Mockito.inOrder(mMockDevice);
        inOrder.verify(mMockDevice).isAdbRoot();
        inOrder.verify(mMockDevice).waitForDeviceAvailable();
        inOrder.verifyNoMoreInteractions();
    }

    @SuppressWarnings("try")
    @Test
    public void testFailsToEnableAdbRoot_throwsException() throws Exception {
        when(mMockDevice.isAdbRoot()).thenReturn(false);
        when(mMockDevice.enableAdbRoot()).thenReturn(false);

        try (AdbRootElevator adbRoot = new AdbRootElevator(mMockDevice)) {
            fail("Exception should have already been thrown.");
        } catch (Exception e) {
            // Expected.
        }

        verify(mMockDevice, never()).disableAdbRoot();
    }

    @SuppressWarnings("try")
    @Test
    public void testDeviceNotAvailableOnRoot_throwsException() throws Exception {
        when(mMockDevice.isAdbRoot()).thenReturn(false);
        when(mMockDevice.enableAdbRoot()).thenThrow(new DeviceNotAvailableException());

        try (AdbRootElevator adbRoot = new AdbRootElevator(mMockDevice)) {
            fail("Exception should have already been thrown.");
        } catch (DeviceNotAvailableException e) {
            // Expected.
        }

        verify(mMockDevice, never()).disableAdbRoot();
    }
}
