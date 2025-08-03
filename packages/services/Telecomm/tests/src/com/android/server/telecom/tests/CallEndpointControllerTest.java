/*
 * Copyright 2022, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.telecom.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothDevice;
import android.os.ResultReceiver;
import android.telecom.CallAudioState;
import android.telecom.CallEndpoint;
import android.test.mock.MockContext;

import com.android.server.telecom.Call;
import com.android.server.telecom.CallAudioManager;
import com.android.server.telecom.CallEndpointController;
import com.android.server.telecom.CallsManager;
import com.android.server.telecom.ConnectionServiceWrapper;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(JUnit4.class)
public class CallEndpointControllerTest extends TelecomTestCase {
    private static final BluetoothDevice bluetoothDevice1 =
            BluetoothRouteManagerTest.makeBluetoothDevice("00:00:00:00:00:01");
    private static final BluetoothDevice bluetoothDevice2 =
            BluetoothRouteManagerTest.makeBluetoothDevice("00:00:00:00:00:02");
    private static final Collection<BluetoothDevice> availableBluetooth1 =
            Arrays.asList(bluetoothDevice1, bluetoothDevice2);
    private static final Collection<BluetoothDevice> availableBluetooth2 =
            Arrays.asList(bluetoothDevice1);

    private static final CallAudioState audioState1 = new CallAudioState(false,
            CallAudioState.ROUTE_EARPIECE, CallAudioState.ROUTE_ALL, null, availableBluetooth1);
    private static final CallAudioState audioState2 = new CallAudioState(false,
            CallAudioState.ROUTE_BLUETOOTH, CallAudioState.ROUTE_ALL, bluetoothDevice1,
            availableBluetooth1);
    private static final CallAudioState audioState3 = new CallAudioState(false,
            CallAudioState.ROUTE_BLUETOOTH, CallAudioState.ROUTE_ALL, bluetoothDevice2,
            availableBluetooth1);
    private static final CallAudioState audioState4 = new CallAudioState(false,
            CallAudioState.ROUTE_BLUETOOTH, CallAudioState.ROUTE_ALL, bluetoothDevice1,
            availableBluetooth2);
    private static final CallAudioState audioState5 = new CallAudioState(true,
            CallAudioState.ROUTE_EARPIECE, CallAudioState.ROUTE_ALL, null, availableBluetooth1);
    private static final CallAudioState audioState6 = new CallAudioState(false,
            CallAudioState.ROUTE_EARPIECE, CallAudioState.ROUTE_EARPIECE, null,
            availableBluetooth1);
    private static final CallAudioState audioState7 = new CallAudioState(false,
            CallAudioState.ROUTE_STREAMING, CallAudioState.ROUTE_ALL, null, availableBluetooth1);
    private static final CallAudioState audioState8 = new CallAudioState(false,
            CallAudioState.ROUTE_EARPIECE, CallAudioState.ROUTE_ALL, bluetoothDevice1,
            availableBluetooth2);

    private CallEndpointController mCallEndpointController;

    @Mock private CallsManager mCallsManager;
    @Mock private Call mCall;
    @Mock private ConnectionServiceWrapper mConnectionService;
    @Mock private CallAudioManager mCallAudioManager;
    @Mock private MockContext mMockContext;
    @Mock private ResultReceiver mResultReceiver;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        mCallEndpointController = new CallEndpointController(mMockContext, mCallsManager);
        doReturn(new HashSet<>(Arrays.asList(mCall))).when(mCallsManager).getTrackedCalls();
        doReturn(mConnectionService).when(mCall).getConnectionService();
        doReturn(mCallAudioManager).when(mCallsManager).getCallAudioManager();
        when(mMockContext.getText(R.string.callendpoint_name_earpiece)).thenReturn("Earpiece");
        when(mMockContext.getText(R.string.callendpoint_name_bluetooth)).thenReturn("Bluetooth");
        when(mMockContext.getText(R.string.callendpoint_name_wiredheadset))
                .thenReturn("Wired headset");
        when(mMockContext.getText(R.string.callendpoint_name_speaker)).thenReturn("Speaker");
        when(mMockContext.getText(R.string.callendpoint_name_streaming)).thenReturn("External");
        when(mMockContext.getText(R.string.callendpoint_name_unknown)).thenReturn("Unknown");
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testCurrentEndpointChangedToBluetooth() throws Exception {
        mCallEndpointController.onCallAudioStateChanged(audioState1, audioState2);
        CallEndpoint endpoint = mCallEndpointController.getCurrentCallEndpoint();
        Set<CallEndpoint> availableEndpoints = mCallEndpointController.getAvailableEndpoints();
        String bluetoothAddress = mCallEndpointController.getBluetoothAddress(endpoint);

        // Earpiece, Wired headset, Speaker and two Bluetooth endpoint is available
        assertEquals(5, availableEndpoints.size());
        // type of current CallEndpoint is Bluetooth
        assertEquals(CallEndpoint.TYPE_BLUETOOTH, endpoint.getEndpointType());
        assertEquals(bluetoothDevice1.getAddress(), bluetoothAddress);

        verify(mCallsManager).updateCallEndpoint(eq(endpoint));
        verify(mConnectionService, times(1)).onCallEndpointChanged(eq(mCall), eq(endpoint));
        verify(mCallsManager, never()).updateAvailableCallEndpoints(any());
        verify(mConnectionService, never()).onAvailableCallEndpointsChanged(any(), any());
        verify(mCallsManager, never()).updateMuteState(anyBoolean());
        verify(mConnectionService, never()).onMuteStateChanged(any(), anyBoolean());
    }

    @Test
    public void testCurrentEndpointChangedToStreaming() throws Exception {
        mCallEndpointController.onCallAudioStateChanged(audioState1, audioState7);
        CallEndpoint endpoint = mCallEndpointController.getCurrentCallEndpoint();
        Set<CallEndpoint> availableEndpoints = mCallEndpointController.getAvailableEndpoints();

        // Only Streaming is available, but it will not be reported via the available endpoints list
        assertEquals(0, availableEndpoints.size());
        assertNotNull(availableEndpoints);
        // type of current CallEndpoint is Streaming
        assertEquals(CallEndpoint.TYPE_STREAMING, endpoint.getEndpointType());

        verify(mCallsManager).updateCallEndpoint(eq(endpoint));
        verify(mConnectionService, times(1)).onCallEndpointChanged(eq(mCall), eq(endpoint));
        verify(mCallsManager).updateAvailableCallEndpoints(eq(availableEndpoints));
        verify(mConnectionService, times(1)).onAvailableCallEndpointsChanged(eq(mCall),
                eq(availableEndpoints));
        verify(mCallsManager, never()).updateMuteState(anyBoolean());
        verify(mConnectionService, never()).onMuteStateChanged(any(), anyBoolean());
    }

    @Test
    public void testCurrentEndpointChangedBetweenBluetooth() throws Exception {
        mCallEndpointController.onCallAudioStateChanged(audioState2, audioState3);
        CallEndpoint endpoint = mCallEndpointController.getCurrentCallEndpoint();
        Set<CallEndpoint> availableEndpoints = mCallEndpointController.getAvailableEndpoints();
        String bluetoothAddress = mCallEndpointController.getBluetoothAddress(endpoint);

        // Earpiece, Wired headset, Speaker and two Bluetooth endpoint is available
        assertEquals(5, availableEndpoints.size());
        // type of current CallEndpoint is Bluetooth
        assertEquals(CallEndpoint.TYPE_BLUETOOTH, endpoint.getEndpointType());
        assertEquals(bluetoothDevice2.getAddress(), bluetoothAddress);

        verify(mCallsManager).updateCallEndpoint(eq(endpoint));
        verify(mConnectionService, times(1)).onCallEndpointChanged(eq(mCall), eq(endpoint));
        verify(mCallsManager, never()).updateAvailableCallEndpoints(any());
        verify(mConnectionService, never()).onAvailableCallEndpointsChanged(any(), any());
        verify(mCallsManager, never()).updateMuteState(anyBoolean());
        verify(mConnectionService, never()).onMuteStateChanged(any(), anyBoolean());
    }

    /**
     * Ensure that {@link CallAudioManager#setAudioRoute(int, String)} is invoked when the user
     * requests to switch to a bluetooth CallEndpoint.  This is an edge case where bluetooth is not
     * the current CallEndpoint but the CallAudioState shows the bluetooth device is
     * active/available.
     */
    @Test
    public void testSwitchFromEarpieceToBluetooth() {
        // simulate an audio state where the EARPIECE is active but a bluetooth device is active.
        mCallEndpointController.onCallAudioStateChanged(null, audioState8 /* Ear but BT active */);
        CallEndpoint btEndpoint = mCallEndpointController.getAvailableEndpoints().stream()
                .filter(e -> e.getEndpointType() == CallEndpoint.TYPE_BLUETOOTH)
                .toList().get(0); // get the only available BT endpoint

        // verify the CallEndpointController shows EARPIECE active + BT endpoint is active device
        assertEquals(CallEndpoint.TYPE_EARPIECE,
                mCallEndpointController.getCurrentCallEndpoint().getEndpointType());
        assertNotNull(btEndpoint);

        // request an endpoint change from earpiece to the bluetooth
        doReturn(audioState8).when(mCallAudioManager).getCallAudioState();
        mCallEndpointController.requestCallEndpointChange(btEndpoint, mResultReceiver);

        // verify the transaction was successful and CallAudioManager#setAudioRoute was called
        verify(mResultReceiver, never()).send(eq(CallEndpoint.ENDPOINT_OPERATION_FAILED), any());
        verify(mCallAudioManager, times(1)).setAudioRoute(eq(CallAudioState.ROUTE_BLUETOOTH),
                eq(bluetoothDevice1.getAddress()));
    }


    /**
     * Ensure that {@link CallAudioManager#setAudioRoute(int, String)} is invoked when the user
     * requests to switch to from one bluetooth device to another.
     */
    @Test
    public void testBtDeviceSwitch() {
        // bluetoothDevice1 should start as active and bluetoothDevice2 is available
        mCallEndpointController.onCallAudioStateChanged(null, audioState2 /* BT active D1 */);
        CallEndpoint currentEndpoint = mCallEndpointController.getCurrentCallEndpoint();
        List<CallEndpoint> btEndpoints = mCallEndpointController.getAvailableEndpoints().stream()
                .filter(e -> e.getEndpointType() == CallEndpoint.TYPE_BLUETOOTH)
                .toList(); // get the only available BT endpoint

        // verify the initial state of the test
        assertEquals(2, btEndpoints.size());
        assertEquals(CallEndpoint.TYPE_BLUETOOTH, currentEndpoint.getEndpointType());

        CallEndpoint otherBluetoothEndpoint = null;
        for (CallEndpoint e : btEndpoints) {
            if (!e.equals(currentEndpoint)) {
                otherBluetoothEndpoint = e;
            }
        }

        assertNotNull(otherBluetoothEndpoint);
        assertNotEquals(currentEndpoint, otherBluetoothEndpoint);

        // request an endpoint change from BT D1 --> BT D2
        doReturn(audioState2).when(mCallAudioManager).getCallAudioState();
        mCallEndpointController.requestCallEndpointChange(otherBluetoothEndpoint, mResultReceiver);

        // verify the transaction was successful and CallAudioManager#setAudioRoute was called
        verify(mResultReceiver, never()).send(eq(CallEndpoint.ENDPOINT_OPERATION_FAILED), any());
        verify(mCallAudioManager, times(1))
                .setAudioRoute(eq(CallAudioState.ROUTE_BLUETOOTH),
                        eq(bluetoothDevice2.getAddress()));
    }

    @Test
    public void testAvailableEndpointChanged() throws Exception {
        mCallEndpointController.onCallAudioStateChanged(audioState1, audioState6);
        CallEndpoint endpoint = mCallEndpointController.getCurrentCallEndpoint();
        Set<CallEndpoint> availableEndpoints = mCallEndpointController.getAvailableEndpoints();

        // Only Earpiece is available
        assertEquals(1, availableEndpoints.size());
        // type of current CallEndpoint is Earpiece
        assertEquals(CallEndpoint.TYPE_EARPIECE, endpoint.getEndpointType());
        assertTrue(availableEndpoints.contains(endpoint));

        verify(mCallsManager, never()).updateCallEndpoint(any());
        verify(mConnectionService, never()).onCallEndpointChanged(any(), any());
        verify(mCallsManager).updateAvailableCallEndpoints(eq(availableEndpoints));
        verify(mConnectionService, times(1)).onAvailableCallEndpointsChanged(eq(mCall),
                eq(availableEndpoints));
        verify(mCallsManager, never()).updateMuteState(anyBoolean());
        verify(mConnectionService, never()).onMuteStateChanged(any(), anyBoolean());
    }

    @Test
    public void testAvailableBluetoothEndpointChanged() throws Exception {
        mCallEndpointController.onCallAudioStateChanged(audioState2, audioState4);
        CallEndpoint endpoint = mCallEndpointController.getCurrentCallEndpoint();
        Set<CallEndpoint> availableEndpoints = mCallEndpointController.getAvailableEndpoints();
        String bluetoothAddress = mCallEndpointController.getBluetoothAddress(endpoint);

        // Earpiece, Wired headset, Speaker and one Bluetooth endpoint is available
        assertEquals(4, availableEndpoints.size());
        // type of current CallEndpoint is Bluetooth
        assertEquals(CallEndpoint.TYPE_BLUETOOTH, endpoint.getEndpointType());
        assertEquals(bluetoothDevice1.getAddress(), bluetoothAddress);

        verify(mCallsManager, never()).updateCallEndpoint(any());
        verify(mConnectionService, never()).onCallEndpointChanged(any(), any());
        verify(mCallsManager).updateAvailableCallEndpoints(eq(availableEndpoints));
        verify(mConnectionService, times(1)).onAvailableCallEndpointsChanged(eq(mCall),
                eq(availableEndpoints));
        verify(mCallsManager, never()).updateMuteState(anyBoolean());
        verify(mConnectionService, never()).onMuteStateChanged(any(), anyBoolean());
    }

    @Test
    public void testMuteStateChanged() throws Exception {
        mCallEndpointController.onCallAudioStateChanged(audioState1, audioState5);
        CallEndpoint endpoint = mCallEndpointController.getCurrentCallEndpoint();
        Set<CallEndpoint> availableEndpoints = mCallEndpointController.getAvailableEndpoints();

        // Earpiece, Wired headset, Speaker and two Bluetooth endpoint is available
        assertEquals(5, availableEndpoints.size());
        // type of current CallEndpoint is Earpiece
        assertEquals(CallEndpoint.TYPE_EARPIECE, endpoint.getEndpointType());

        verify(mCallsManager, never()).updateCallEndpoint(any());
        verify(mConnectionService, never()).onCallEndpointChanged(any(), any());
        verify(mCallsManager, never()).updateAvailableCallEndpoints(any());
        verify(mConnectionService, never()).onAvailableCallEndpointsChanged(any(), any());
        verify(mCallsManager).updateMuteState(eq(true));
        verify(mConnectionService, times(1)).onMuteStateChanged(eq(mCall), eq(true));
    }

    @Test
    public void testNotifyForcely() throws Exception {
        mCallEndpointController.onCallAudioStateChanged(audioState1, audioState1);
        CallEndpoint endpoint = mCallEndpointController.getCurrentCallEndpoint();
        Set<CallEndpoint> availableEndpoints = mCallEndpointController.getAvailableEndpoints();

        // Earpiece, Wired headset, Speaker and two Bluetooth endpoint is available
        assertEquals(5, availableEndpoints.size());
        // type of current CallEndpoint is Earpiece
        assertEquals(CallEndpoint.TYPE_EARPIECE, endpoint.getEndpointType());

        verify(mCallsManager).updateCallEndpoint(eq(endpoint));
        verify(mConnectionService, times(1)).onCallEndpointChanged(eq(mCall), eq(endpoint));
        verify(mCallsManager).updateAvailableCallEndpoints(eq(availableEndpoints));
        verify(mConnectionService, times(1)).onAvailableCallEndpointsChanged(eq(mCall),
                eq(availableEndpoints));
        verify(mCallsManager).updateMuteState(eq(false));
        verify(mConnectionService, times(1)).onMuteStateChanged(eq(mCall), eq(false));
    }

    @Test
    public void testEndpointChangeRequest() throws Exception {
        mCallEndpointController.onCallAudioStateChanged(null, audioState1);
        CallEndpoint endpoint1 = mCallEndpointController.getCurrentCallEndpoint();

        mCallEndpointController.onCallAudioStateChanged(audioState1, audioState2);
        CallEndpoint endpoint2 = mCallEndpointController.getCurrentCallEndpoint();

        mCallEndpointController.requestCallEndpointChange(endpoint1, mResultReceiver);
        verify(mCallAudioManager).setAudioRoute(eq(CallAudioState.ROUTE_EARPIECE), eq(null));

        mCallEndpointController.requestCallEndpointChange(endpoint2, mResultReceiver);
        verify(mCallAudioManager).setAudioRoute(eq(CallAudioState.ROUTE_BLUETOOTH),
                eq(bluetoothDevice1.getAddress()));
    }

    @Test
    public void testEndpointChangeRequest_EndpointDoesNotExist() throws Exception {
        mCallEndpointController.onCallAudioStateChanged(null, audioState2);
        CallEndpoint endpoint = mCallEndpointController.getCurrentCallEndpoint();
        mCallEndpointController.onCallAudioStateChanged(audioState2, audioState6);

        mCallEndpointController.requestCallEndpointChange(endpoint, mResultReceiver);
        verify(mCallAudioManager, never()).setAudioRoute(eq(CallAudioState.ROUTE_BLUETOOTH),
                eq(bluetoothDevice1.getAddress()));
        verify(mResultReceiver).send(eq(CallEndpoint.ENDPOINT_OPERATION_FAILED), any());
    }
}