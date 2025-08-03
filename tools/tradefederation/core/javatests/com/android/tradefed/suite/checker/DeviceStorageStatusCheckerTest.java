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
package com.android.tradefed.suite.checker;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.suite.checker.StatusCheckerResult.CheckStatus;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

/** Unit tests for {@link DeviceStorageStatusChecker} */
@RunWith(JUnit4.class)
public class DeviceStorageStatusCheckerTest {

    private DeviceStorageStatusChecker mChecker;
    private ITestDevice mMockDevice;
    private OptionSetter mOptionSetter;

    @Before
    public void setup() throws Exception {
        mMockDevice = mock(ITestDevice.class);
        mChecker = new DeviceStorageStatusChecker();
        mOptionSetter = new OptionSetter(mChecker);
        mOptionSetter.setOptionValue("partition", "/data");
        mOptionSetter.setOptionValue("partition", "/data2");
    }

    /** Test that device checker passes if device has enough storage. */
    @Test
    public void testEnoughDeviceStorage() throws Exception {
        when(mMockDevice.getPartitionFreeSpace(Mockito.eq("/data"))).thenReturn(54L * 1024); // 54MB
        when(mMockDevice.getPartitionFreeSpace(Mockito.eq("/data2")))
                .thenReturn(54L * 1024); // 54MB

        assertEquals(CheckStatus.SUCCESS, mChecker.preExecutionCheck(mMockDevice).getStatus());
    }

    /** Test that device checker fails if device has not enough storage on 1 partition. */
    @Test
    public void testInEnoughDeviceStorageOn1Partition() throws Exception {
        when(mMockDevice.getPartitionFreeSpace(Mockito.eq("/data"))).thenReturn(48L * 1024); // 48MB
        when(mMockDevice.getPartitionFreeSpace(Mockito.eq("/data2")))
                .thenReturn(54L * 1024); // 54MB

        assertEquals(CheckStatus.FAILED, mChecker.preExecutionCheck(mMockDevice).getStatus());
    }

    /** Test that device checker fails if device has not enough given storage. */
    @Test
    public void testNotEnoughDeviceStorageWithGivenStorageParameter() throws Exception {
        mOptionSetter.setOptionValue("minimal-storage-bytes", "104857600"); // 100MB
        when(mMockDevice.getPartitionFreeSpace(Mockito.eq("/data"))).thenReturn(54L * 1024); // 54MB
        when(mMockDevice.getPartitionFreeSpace(Mockito.eq("/data2")))
                .thenReturn(54L * 1024); // 54MB

        assertEquals(CheckStatus.FAILED, mChecker.preExecutionCheck(mMockDevice).getStatus());
    }

    /** Test that device checker passes if device has enough given storage. */
    @Test
    public void testEnoughDeviceStorageWithGivenStorageParameter() throws Exception {
        mOptionSetter.setOptionValue("minimal-storage-bytes", "104857600"); // 100MB
        when(mMockDevice.getPartitionFreeSpace(Mockito.eq("/data")))
                .thenReturn(101L * 1024); // 101MB
        when(mMockDevice.getPartitionFreeSpace(Mockito.eq("/data2")))
                .thenReturn(101L * 1024); // 101MB

        assertEquals(CheckStatus.SUCCESS, mChecker.preExecutionCheck(mMockDevice).getStatus());
    }
}
