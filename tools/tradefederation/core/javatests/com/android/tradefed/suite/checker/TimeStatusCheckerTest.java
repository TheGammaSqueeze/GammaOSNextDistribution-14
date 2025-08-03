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
package com.android.tradefed.suite.checker;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.suite.checker.StatusCheckerResult.CheckStatus;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link TimeStatusChecker}. */
@RunWith(JUnit4.class)
public class TimeStatusCheckerTest {

    private TimeStatusChecker mChecker;
    @Mock ITestDevice mMockDevice;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mChecker = new TimeStatusChecker();
    }

    /**
     * Test that the status checker is successful and does not resync the time for small
     * differences.
     */
    @Test
    public void testCheckTimeDiff_small() throws DeviceNotAvailableException {
        when(mMockDevice.getDeviceTimeOffset(Mockito.any())).thenReturn(2000L);

        assertEquals(CheckStatus.SUCCESS, mChecker.postExecutionCheck(mMockDevice).getStatus());
    }

    /**
     * Test that when the time difference is bigger, we fail the status checker and resync the time
     * between the host and the device.
     */
    @Test
    public void testCheckTimeDiff_large() throws DeviceNotAvailableException {
        when(mMockDevice.getDeviceTimeOffset(Mockito.any())).thenReturn(15000L);

        assertEquals(CheckStatus.FAILED, mChecker.postExecutionCheck(mMockDevice).getStatus());

        verify(mMockDevice)
                .logOnDevice(Mockito.any(), Mockito.any(), Mockito.contains("reset the time."));
        verify(mMockDevice).setDate(Mockito.any());
    }

    /**
     * Test that the second failure in a row is not reported, just logged to avoid capturing
     * bugreport constantly.
     */
    @Test
    public void testCheckTimeDiff_multiFailure() throws DeviceNotAvailableException {
        when(mMockDevice.getDeviceTimeOffset(Mockito.any())).thenReturn(15000L);

        when(mMockDevice.getSerialNumber()).thenReturn("serial");

        assertEquals(CheckStatus.FAILED, mChecker.postExecutionCheck(mMockDevice).getStatus());
        assertEquals(CheckStatus.SUCCESS, mChecker.postExecutionCheck(mMockDevice).getStatus());
        verify(mMockDevice, times(2))
                .logOnDevice(Mockito.any(), Mockito.any(), Mockito.contains("reset the time."));
        verify(mMockDevice, times(2)).setDate(Mockito.any());
        verify(mMockDevice, times(2)).getDeviceTimeOffset(Mockito.any());
    }
}
