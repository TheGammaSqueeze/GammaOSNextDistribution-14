/*
 * Copyright (C) 2022 The Android Open Source Project
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

package androidx.core.uwb.backend.impl;

import static androidx.core.uwb.backend.impl.internal.Utils.RANGE_DATA_NTF_ENABLE;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;

import androidx.core.uwb.backend.IRangingSessionCallback;
import androidx.core.uwb.backend.RangingControleeParameters;
import androidx.core.uwb.backend.RangingParameters;
import androidx.core.uwb.backend.UwbAddress;
import androidx.core.uwb.backend.UwbRangeDataNtfConfig;
import androidx.core.uwb.backend.impl.internal.RangingController;
import androidx.core.uwb.backend.impl.internal.RangingSessionCallback;
import androidx.core.uwb.backend.impl.internal.UwbComplexChannel;
import androidx.core.uwb.backend.impl.internal.UwbServiceImpl;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class UwbControllerClientTest {

    @Mock private RangingController mRangingController;
    @Mock private UwbServiceImpl mUwbService;
    @Mock private IRangingSessionCallback mRangingSessionCallback;
    @Captor
    ArgumentCaptor<androidx.core.uwb.backend.impl.internal.UwbAddress> mAddressCaptor;
    @Captor
    ArgumentCaptor<androidx.core.uwb.backend.impl.internal.RangingControleeParameters>
            mControleeParamCaptor;
    private UwbControllerClient mUwbControllerClient;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mUwbControllerClient = new UwbControllerClient(mRangingController, mUwbService);
    }

    @Test
    public void testGetComplexChannel() throws  RemoteException {
        UwbComplexChannel channel = new UwbComplexChannel(9, 9);
        when(mRangingController.getComplexChannel()).thenReturn(channel);
        androidx.core.uwb.backend.UwbComplexChannel complexChannel =
                mUwbControllerClient.getComplexChannel();
        assertEquals(complexChannel.channel, channel.getChannel());
        assertEquals(complexChannel.preambleIndex, channel.getPreambleIndex());
    }

    @Test
    public void testStartRanging() throws RemoteException {
        RangingParameters params = new RangingParameters();
        params.complexChannel = new androidx.core.uwb.backend.UwbComplexChannel();
        params.complexChannel.channel = 9;
        params.complexChannel.preambleIndex = 9;
        params.peerDevices = new ArrayList<>();
        params.uwbRangeDataNtfConfig = new UwbRangeDataNtfConfig();
        params.uwbRangeDataNtfConfig.rangeDataNtfConfigType = RANGE_DATA_NTF_ENABLE;
        params.uwbRangeDataNtfConfig.ntfProximityNearCm = 100;
        params.uwbRangeDataNtfConfig.ntfProximityFarCm = 300;
        params.slotDuration = 1;
        mUwbControllerClient.startRanging(params, mRangingSessionCallback);
        verify(mRangingController).setRangingParameters(any(
                androidx.core.uwb.backend.impl.internal.RangingParameters.class));
        verify(mRangingController).startRanging(
                any(RangingSessionCallback.class), any(ExecutorService.class));
    }

    @Test
    public void testStopRanging() throws RemoteException {
        mUwbControllerClient.stopRanging(mRangingSessionCallback);
        verify(mRangingController).stopRanging();
    }

    @Test
    public void testAddControlee() throws RemoteException {
        UwbAddress address = new UwbAddress();
        address.address = new byte[] {0x1, 0x2};
        RangingControleeParameters params = new RangingControleeParameters();
        params.address = address;
        mUwbControllerClient.addControleeWithSessionParams(params);
        verify(mRangingController).addControleeWithSessionParams(mControleeParamCaptor.capture());
        assertArrayEquals(address.address, mControleeParamCaptor.getValue().getAddress().toBytes());
    }

    @Test
    public void testRemoveControlee() throws RemoteException {
        UwbAddress address = new UwbAddress();
        address.address = new byte[] {0x1, 0x2};
        mUwbControllerClient.removeControlee(address);
        verify(mRangingController).removeControlee(mAddressCaptor.capture());
        assertArrayEquals(address.address, mAddressCaptor.getValue().toBytes());
    }
}
