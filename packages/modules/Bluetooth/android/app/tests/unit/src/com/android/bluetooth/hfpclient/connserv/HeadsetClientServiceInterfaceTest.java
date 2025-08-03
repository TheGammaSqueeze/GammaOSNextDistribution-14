/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.bluetooth.hfpclient;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Bundle;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class HeadsetClientServiceInterfaceTest {
    private static final String TEST_DEVICE_ADDRESS = "00:11:22:33:44:55";
    private static final BluetoothDevice TEST_DEVICE =
            ((BluetoothManager) InstrumentationRegistry.getTargetContext()
                    .getSystemService(Context.BLUETOOTH_SERVICE))
            .getAdapter().getRemoteDevice(TEST_DEVICE_ADDRESS);
    private static final String TEST_NUMBER = "000-111-2222";
    private static final byte TEST_CODE = 0;
    private static final int TEST_CALL_INDEX = 0;
    private static final HfpClientCall TEST_CALL = new HfpClientCall(TEST_DEVICE, /* id= */ 0,
            HfpClientCall.CALL_STATE_ACTIVE, /* number= */ TEST_NUMBER,
            /* multiParty= */ false, /* outgoing= */false, /* inBandRing= */true);
    private static final int TEST_FLAGS = 0;
    private static final Bundle TEST_BUNDLE = new Bundle();
    static {
        TEST_BUNDLE.putInt("test_int", 0);
    }

    @Mock private HeadsetClientService mMockHeadsetClientService;
    private HeadsetClientServiceInterface mServiceInterface;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        HeadsetClientService.setHeadsetClientService(mMockHeadsetClientService);
        mServiceInterface = new HeadsetClientServiceInterface();
    }

    @After
    public void tearDown() {
        HeadsetClientService.setHeadsetClientService(null);
        assertThat(HeadsetClientService.getHeadsetClientService()).isNull();
    }

    private void makeHeadsetClientServiceAvailable() {
        when(mMockHeadsetClientService.isAvailable()).thenReturn(true);
        assertThat(HeadsetClientService.getHeadsetClientService())
            .isEqualTo(mMockHeadsetClientService);
    }

    @Test
    public void testDial() {
        assertThat(mServiceInterface.dial(TEST_DEVICE, TEST_NUMBER)).isNull();
        makeHeadsetClientServiceAvailable();

        doReturn(TEST_CALL).when(mMockHeadsetClientService).dial(TEST_DEVICE, TEST_NUMBER);
        assertThat(mServiceInterface.dial(TEST_DEVICE, TEST_NUMBER)).isEqualTo(TEST_CALL);
    }

    @Test
    public void testEnterPrivateMode() {
        assertThat(mServiceInterface.enterPrivateMode(TEST_DEVICE, TEST_CALL_INDEX)).isFalse();
        makeHeadsetClientServiceAvailable();

        doReturn(false).when(mMockHeadsetClientService)
            .enterPrivateMode(TEST_DEVICE, TEST_CALL_INDEX);
        assertThat(mServiceInterface.enterPrivateMode(TEST_DEVICE, TEST_CALL_INDEX)).isFalse();
        doReturn(true).when(mMockHeadsetClientService)
            .enterPrivateMode(TEST_DEVICE, TEST_CALL_INDEX);
        assertThat(mServiceInterface.enterPrivateMode(TEST_DEVICE, TEST_CALL_INDEX)).isTrue();
    }

    @Test
    public void testSendDTMF() {
        assertThat(mServiceInterface.sendDTMF(TEST_DEVICE, TEST_CODE)).isFalse();
        makeHeadsetClientServiceAvailable();

        doReturn(false).when(mMockHeadsetClientService).sendDTMF(TEST_DEVICE, TEST_CODE);
        assertThat(mServiceInterface.sendDTMF(TEST_DEVICE, TEST_CODE)).isFalse();
        doReturn(true).when(mMockHeadsetClientService).sendDTMF(TEST_DEVICE, TEST_CODE);
        assertThat(mServiceInterface.sendDTMF(TEST_DEVICE, TEST_CODE)).isTrue();
    }

    @Test
    public void testTerminateCall() {
        assertThat(mServiceInterface.terminateCall(TEST_DEVICE, TEST_CALL)).isFalse();
        makeHeadsetClientServiceAvailable();

        doReturn(true).when(mMockHeadsetClientService)
                .terminateCall(TEST_DEVICE, TEST_CALL.getUUID());
        assertThat(mServiceInterface.terminateCall(TEST_DEVICE, TEST_CALL)).isTrue();
        doReturn(false).when(mMockHeadsetClientService)
                .terminateCall(TEST_DEVICE, TEST_CALL.getUUID());
        assertThat(mServiceInterface.terminateCall(TEST_DEVICE, TEST_CALL)).isFalse();
    }

    @Test
    public void testHoldCall() {
        assertThat(mServiceInterface.holdCall(TEST_DEVICE)).isFalse();
        makeHeadsetClientServiceAvailable();

        doReturn(false).when(mMockHeadsetClientService).holdCall(TEST_DEVICE);
        assertThat(mServiceInterface.holdCall(TEST_DEVICE)).isFalse();
        doReturn(true).when(mMockHeadsetClientService).holdCall(TEST_DEVICE);
        assertThat(mServiceInterface.holdCall(TEST_DEVICE)).isTrue();
    }

    @Test
    public void testAcceptCall() {
        assertThat(mServiceInterface.acceptCall(TEST_DEVICE, TEST_FLAGS)).isFalse();
        makeHeadsetClientServiceAvailable();

        doReturn(false).when(mMockHeadsetClientService).acceptCall(TEST_DEVICE, TEST_FLAGS);
        assertThat(mServiceInterface.acceptCall(TEST_DEVICE, TEST_CODE)).isFalse();
        doReturn(true).when(mMockHeadsetClientService).acceptCall(TEST_DEVICE, TEST_FLAGS);
        assertThat(mServiceInterface.acceptCall(TEST_DEVICE, TEST_CODE)).isTrue();
    }

    @Test
    public void testRejectCall() {
        assertThat(mServiceInterface.rejectCall(TEST_DEVICE)).isFalse();
        makeHeadsetClientServiceAvailable();

        doReturn(false).when(mMockHeadsetClientService).rejectCall(TEST_DEVICE);
        assertThat(mServiceInterface.rejectCall(TEST_DEVICE)).isFalse();
        doReturn(true).when(mMockHeadsetClientService).rejectCall(TEST_DEVICE);
        assertThat(mServiceInterface.rejectCall(TEST_DEVICE)).isTrue();
    }

    @Test
    public void testConnectAudio() {
        assertThat(mServiceInterface.connectAudio(TEST_DEVICE)).isFalse();
        makeHeadsetClientServiceAvailable();

        doReturn(false).when(mMockHeadsetClientService).connectAudio(TEST_DEVICE);
        assertThat(mServiceInterface.connectAudio(TEST_DEVICE)).isFalse();
        doReturn(true).when(mMockHeadsetClientService).connectAudio(TEST_DEVICE);
        assertThat(mServiceInterface.connectAudio(TEST_DEVICE)).isTrue();
    }

    @Test
    public void testDisconnectAudio() {
        assertThat(mServiceInterface.disconnectAudio(TEST_DEVICE)).isFalse();
        makeHeadsetClientServiceAvailable();

        doReturn(false).when(mMockHeadsetClientService).disconnectAudio(TEST_DEVICE);
        assertThat(mServiceInterface.disconnectAudio(TEST_DEVICE)).isFalse();
        doReturn(true).when(mMockHeadsetClientService).disconnectAudio(TEST_DEVICE);
        assertThat(mServiceInterface.disconnectAudio(TEST_DEVICE)).isTrue();
    }

    @Test
    public void testGetCurrentAgFeatures() {
        Set<Integer> features = Set.of(HeadsetClientHalConstants.PEER_FEAT_3WAY);
        assertThat(mServiceInterface.getCurrentAgFeatures(TEST_DEVICE)).isNull();
        makeHeadsetClientServiceAvailable();

        doReturn(features).when(mMockHeadsetClientService).getCurrentAgFeatures(TEST_DEVICE);
        assertThat(mServiceInterface.getCurrentAgFeatures(TEST_DEVICE)).isEqualTo(features);
    }

    @Test
    public void testGetCurrentAgEvents() {
        assertThat(mServiceInterface.getCurrentAgEvents(TEST_DEVICE)).isNull();
        makeHeadsetClientServiceAvailable();

        doReturn(TEST_BUNDLE).when(mMockHeadsetClientService).getCurrentAgEvents(TEST_DEVICE);
        assertThat(mServiceInterface.getCurrentAgEvents(TEST_DEVICE)).isEqualTo(TEST_BUNDLE);
    }

    @Test
    public void testGetConnectedDevices() {
        List<BluetoothDevice> devices = List.of(TEST_DEVICE);
        assertThat(mServiceInterface.getConnectedDevices()).isNull();
        makeHeadsetClientServiceAvailable();

        doReturn(devices).when(mMockHeadsetClientService).getConnectedDevices();
        assertThat(mServiceInterface.getConnectedDevices()).isEqualTo(devices);
    }

    @Test
    public void testGetCurrentCalls() {
        assertThat(mServiceInterface.getCurrentCalls(TEST_DEVICE)).isNull();
        makeHeadsetClientServiceAvailable();

        List<HfpClientCall> calls = List.of(TEST_CALL);
        doReturn(calls).when(mMockHeadsetClientService).getCurrentCalls(TEST_DEVICE);
        assertThat(mServiceInterface.getCurrentCalls(TEST_DEVICE)).isEqualTo(calls);
    }

    @Test
    public void testHasHfpClientEcc() {
        Set<Integer> features = Set.of(HeadsetClientHalConstants.PEER_FEAT_3WAY);
        assertThat(mServiceInterface.hasHfpClientEcc(TEST_DEVICE)).isFalse();
        makeHeadsetClientServiceAvailable();

        doReturn(features).when(mMockHeadsetClientService).getCurrentAgFeatures(TEST_DEVICE);
        assertThat(mServiceInterface.hasHfpClientEcc(TEST_DEVICE)).isFalse();

        Set<Integer> featuresWithEcc = Set.of(HeadsetClientHalConstants.PEER_FEAT_ECC);
        doReturn(featuresWithEcc).when(mMockHeadsetClientService).getCurrentAgFeatures(TEST_DEVICE);
        assertThat(mServiceInterface.hasHfpClientEcc(TEST_DEVICE)).isTrue();
    }
}

