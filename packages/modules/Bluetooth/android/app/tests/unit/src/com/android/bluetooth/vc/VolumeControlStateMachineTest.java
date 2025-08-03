/*
 * Copyright 2020 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

package com.android.bluetooth.vc;

import static org.mockito.Mockito.after;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.HandlerThread;
import android.os.Message;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.btservice.AdapterService;

import org.hamcrest.core.IsInstanceOf;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class VolumeControlStateMachineTest {
    private BluetoothAdapter mAdapter;
    private Context mTargetContext;
    private HandlerThread mHandlerThread;
    private VolumeControlStateMachine mVolumeControlStateMachine;
    private BluetoothDevice mTestDevice;
    private static final int TIMEOUT_MS = 1000;

    @Mock private AdapterService mAdapterService;
    @Mock private VolumeControlService mVolumeControlService;
    @Mock private VolumeControlNativeInterface mVolumeControlNativeInterface;

    @Before
    public void setUp() throws Exception {
        mTargetContext = InstrumentationRegistry.getTargetContext();
        InstrumentationRegistry.getInstrumentation().getUiAutomation().adoptShellPermissionIdentity();
        // Set up mocks and test assets
        MockitoAnnotations.initMocks(this);
        TestUtils.setAdapterService(mAdapterService);

        mAdapter = BluetoothAdapter.getDefaultAdapter();

        // Get a device for testing
        mTestDevice = mAdapter.getRemoteDevice("00:01:02:03:04:05");

        // Set up thread and looper
        mHandlerThread = new HandlerThread("VolumeControlStateMachineTestHandlerThread");
        mHandlerThread.start();
        mVolumeControlStateMachine = new VolumeControlStateMachine(mTestDevice,
                mVolumeControlService, mVolumeControlNativeInterface, mHandlerThread.getLooper());
        // Override the timeout value to speed up the test
        mVolumeControlStateMachine.sConnectTimeoutMs = 1000;     // 1s
        mVolumeControlStateMachine.start();
    }

    @After
    public void tearDown() throws Exception {
        mHandlerThread.quit();
        TestUtils.clearAdapterService(mAdapterService);
    }

    /**
     * Test that default state is disconnected
     */
    @Test
    public void testDefaultDisconnectedState() {
        Assert.assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mVolumeControlStateMachine.getConnectionState());
    }

    /**
     * Allow/disallow connection to any device.
     *
     * @param allow if true, connection is allowed
     */
    private void allowConnection(boolean allow) {
        doReturn(allow).when(mVolumeControlService).okToConnect(any(BluetoothDevice.class));
    }

    /**
     * Test that an incoming connection with policy forbidding connection is rejected
     */
    @Test
    public void testIncomingPolicyReject() {
        allowConnection(false);

        // Inject an event for when incoming connection is requested
        VolumeControlStackEvent connStCh =
                new VolumeControlStackEvent(
                        VolumeControlStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connStCh.device = mTestDevice;
        connStCh.valueInt1 = VolumeControlStackEvent.CONNECTION_STATE_CONNECTED;
        mVolumeControlStateMachine.sendMessage(VolumeControlStateMachine.STACK_EVENT, connStCh);

        // Verify that no connection state broadcast is executed
        verify(mVolumeControlService, after(TIMEOUT_MS).never()).sendBroadcast(any(Intent.class),
                anyString());
        // Check that we are in Disconnected state
        Assert.assertThat(mVolumeControlStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(VolumeControlStateMachine.Disconnected.class));
    }

    /**
     * Test that an incoming connection with policy allowing connection is accepted
     */
    @Test
    public void testIncomingPolicyAccept() {
        allowConnection(true);

        // Inject an event for when incoming connection is requested
        VolumeControlStackEvent connStCh =
                new VolumeControlStackEvent(
                        VolumeControlStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connStCh.device = mTestDevice;
        connStCh.valueInt1 = VolumeControlStackEvent.CONNECTION_STATE_CONNECTING;
        mVolumeControlStateMachine.sendMessage(VolumeControlStateMachine.STACK_EVENT, connStCh);

        // Verify that one connection state broadcast is executed
        ArgumentCaptor<Intent> intentArgument1 = ArgumentCaptor.forClass(Intent.class);
        verify(mVolumeControlService, timeout(TIMEOUT_MS).times(1)).sendBroadcast(
                intentArgument1.capture(), anyString());
        Assert.assertEquals(BluetoothProfile.STATE_CONNECTING,
                intentArgument1.getValue().getIntExtra(BluetoothProfile.EXTRA_STATE, -1));

        // Check that we are in Connecting state
        Assert.assertThat(mVolumeControlStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(VolumeControlStateMachine.Connecting.class));

        // Send a message to trigger connection completed
        VolumeControlStackEvent connCompletedEvent =
                new VolumeControlStackEvent(VolumeControlStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connCompletedEvent.device = mTestDevice;
        connCompletedEvent.valueInt1 = VolumeControlStackEvent.CONNECTION_STATE_CONNECTED;
        mVolumeControlStateMachine.sendMessage(VolumeControlStateMachine.STACK_EVENT,
                connCompletedEvent);

        // Verify that the expected number of broadcasts are executed:
        // - two calls to broadcastConnectionState(): Disconnected -> Connecting -> Connected
        ArgumentCaptor<Intent> intentArgument2 = ArgumentCaptor.forClass(Intent.class);
        verify(mVolumeControlService, timeout(TIMEOUT_MS).times(2)).sendBroadcast(
                intentArgument2.capture(), anyString());
        // Check that we are in Connected state
        Assert.assertThat(mVolumeControlStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(VolumeControlStateMachine.Connected.class));
    }

    /**
     * Test that an outgoing connection times out
     */
    @Test
    public void testOutgoingTimeout() {
        allowConnection(true);
        doReturn(true).when(mVolumeControlNativeInterface).connectVolumeControl(any(
                BluetoothDevice.class));
        doReturn(true).when(mVolumeControlNativeInterface).disconnectVolumeControl(any(
                BluetoothDevice.class));

        // Send a connect request
        mVolumeControlStateMachine.sendMessage(VolumeControlStateMachine.CONNECT, mTestDevice);

        // Verify that one connection state broadcast is executed
        ArgumentCaptor<Intent> intentArgument1 = ArgumentCaptor.forClass(Intent.class);
        verify(mVolumeControlService, timeout(TIMEOUT_MS).times(1)).sendBroadcast(
                intentArgument1.capture(),
                anyString());
        Assert.assertEquals(BluetoothProfile.STATE_CONNECTING,
                intentArgument1.getValue().getIntExtra(BluetoothProfile.EXTRA_STATE, -1));

        // Check that we are in Connecting state
        Assert.assertThat(mVolumeControlStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(VolumeControlStateMachine.Connecting.class));

        // Verify that one connection state broadcast is executed
        ArgumentCaptor<Intent> intentArgument2 = ArgumentCaptor.forClass(Intent.class);
        verify(mVolumeControlService, timeout(VolumeControlStateMachine.sConnectTimeoutMs * 2)
                .times(2)).sendBroadcast(intentArgument2.capture(), anyString());
        Assert.assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                intentArgument2.getValue().getIntExtra(BluetoothProfile.EXTRA_STATE, -1));

        // Check that we are in Disconnected state
        Assert.assertThat(mVolumeControlStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(VolumeControlStateMachine.Disconnected.class));
        verify(mVolumeControlNativeInterface).disconnectVolumeControl(eq(mTestDevice));
    }

    /**
     * Test that an incoming connection times out
     */
    @Test
    public void testIncomingTimeout() {
        allowConnection(true);
        doReturn(true).when(mVolumeControlNativeInterface).connectVolumeControl(any(
                BluetoothDevice.class));
        doReturn(true).when(mVolumeControlNativeInterface).disconnectVolumeControl(any(
                BluetoothDevice.class));

        // Inject an event for when incoming connection is requested
        VolumeControlStackEvent connStCh =
                new VolumeControlStackEvent(
                        VolumeControlStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connStCh.device = mTestDevice;
        connStCh.valueInt1 = VolumeControlStackEvent.CONNECTION_STATE_CONNECTING;
        mVolumeControlStateMachine.sendMessage(VolumeControlStateMachine.STACK_EVENT, connStCh);

        // Verify that one connection state broadcast is executed
        ArgumentCaptor<Intent> intentArgument1 = ArgumentCaptor.forClass(Intent.class);
        verify(mVolumeControlService, timeout(TIMEOUT_MS).times(1)).sendBroadcast(
                intentArgument1.capture(),
                anyString());
        Assert.assertEquals(BluetoothProfile.STATE_CONNECTING,
                intentArgument1.getValue().getIntExtra(BluetoothProfile.EXTRA_STATE, -1));

        // Check that we are in Connecting state
        Assert.assertThat(mVolumeControlStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(VolumeControlStateMachine.Connecting.class));

        // Verify that one connection state broadcast is executed
        ArgumentCaptor<Intent> intentArgument2 = ArgumentCaptor.forClass(Intent.class);
        verify(mVolumeControlService, timeout(VolumeControlStateMachine.sConnectTimeoutMs * 2)
                .times(2)).sendBroadcast(intentArgument2.capture(), anyString());
        Assert.assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                intentArgument2.getValue().getIntExtra(BluetoothProfile.EXTRA_STATE, -1));

        // Check that we are in Disconnected state
        Assert.assertThat(mVolumeControlStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(VolumeControlStateMachine.Disconnected.class));
        verify(mVolumeControlNativeInterface).disconnectVolumeControl(eq(mTestDevice));
    }

    @Test
    public void testStatesChangesWithMessages() {
        allowConnection(true);
        doReturn(true).when(mVolumeControlNativeInterface).connectVolumeControl(any(
                BluetoothDevice.class));
        doReturn(true).when(mVolumeControlNativeInterface).disconnectVolumeControl(any(
                BluetoothDevice.class));

        // Check that we are in Disconnected state
        Assert.assertThat(mVolumeControlStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(VolumeControlStateMachine.Disconnected.class));

        mVolumeControlStateMachine.sendMessage(mVolumeControlStateMachine.DISCONNECT);
        // Check that we are in Disconnected state
        Assert.assertThat(mVolumeControlStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(VolumeControlStateMachine.Disconnected.class));

        // disconnected -> connecting
        sendMessageAndVerifyTransition(
                mVolumeControlStateMachine.obtainMessage(mVolumeControlStateMachine.CONNECT),
                VolumeControlStateMachine.Connecting.class);
        // connecting -> disconnected
        sendMessageAndVerifyTransition(
                mVolumeControlStateMachine.obtainMessage(VolumeControlStateMachine.CONNECT_TIMEOUT),
                VolumeControlStateMachine.Disconnected.class);

        // disconnected -> connecting
        VolumeControlStackEvent stackEvent = new VolumeControlStackEvent(
                VolumeControlStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        stackEvent.device = mTestDevice;
        stackEvent.valueInt1 = VolumeControlStackEvent.CONNECTION_STATE_CONNECTING;
        sendMessageAndVerifyTransition(
                mVolumeControlStateMachine.obtainMessage(
                        mVolumeControlStateMachine.STACK_EVENT, stackEvent),
                VolumeControlStateMachine.Connecting.class);

        // connecting -> disconnected
        sendMessageAndVerifyTransition(
                mVolumeControlStateMachine.obtainMessage(mVolumeControlStateMachine.DISCONNECT),
                VolumeControlStateMachine.Disconnected.class);

        // disconnected -> connecting
        sendMessageAndVerifyTransition(
                mVolumeControlStateMachine.obtainMessage(mVolumeControlStateMachine.CONNECT),
                VolumeControlStateMachine.Connecting.class);
        // connecting -> disconnecting
        stackEvent = new VolumeControlStackEvent(
                VolumeControlStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        stackEvent.device = mTestDevice;
        stackEvent.valueInt1 = VolumeControlStackEvent.CONNECTION_STATE_DISCONNECTING;
        sendMessageAndVerifyTransition(
                mVolumeControlStateMachine.obtainMessage(
                        mVolumeControlStateMachine.STACK_EVENT, stackEvent),
                VolumeControlStateMachine.Disconnecting.class);
        // disconnecting -> connecting
        stackEvent = new VolumeControlStackEvent(
                VolumeControlStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        stackEvent.device = mTestDevice;
        stackEvent.valueInt1 = VolumeControlStackEvent.CONNECTION_STATE_CONNECTING;
        sendMessageAndVerifyTransition(
                mVolumeControlStateMachine.obtainMessage(
                        mVolumeControlStateMachine.STACK_EVENT, stackEvent),
                VolumeControlStateMachine.Connecting.class);
        // connecting -> connected
        stackEvent = new VolumeControlStackEvent(
                VolumeControlStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        stackEvent.device = mTestDevice;
        stackEvent.valueInt1 = VolumeControlStackEvent.CONNECTION_STATE_CONNECTED;
        sendMessageAndVerifyTransition(
                mVolumeControlStateMachine.obtainMessage(
                        mVolumeControlStateMachine.STACK_EVENT, stackEvent),
                VolumeControlStateMachine.Connected.class);
        // connected -> disconnecting
        stackEvent = new VolumeControlStackEvent(
                VolumeControlStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        stackEvent.device = mTestDevice;
        stackEvent.valueInt1 = VolumeControlStackEvent.CONNECTION_STATE_DISCONNECTING;
        sendMessageAndVerifyTransition(
                mVolumeControlStateMachine.obtainMessage(
                        mVolumeControlStateMachine.STACK_EVENT, stackEvent),
                VolumeControlStateMachine.Disconnecting.class);
        // disconnecting -> disconnected
        sendMessageAndVerifyTransition(
                mVolumeControlStateMachine.obtainMessage(VolumeControlStateMachine.CONNECT_TIMEOUT),
                VolumeControlStateMachine.Disconnected.class);

        // disconnected -> connected
        stackEvent = new VolumeControlStackEvent(
                VolumeControlStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        stackEvent.device = mTestDevice;
        stackEvent.valueInt1 = VolumeControlStackEvent.CONNECTION_STATE_CONNECTED;
        sendMessageAndVerifyTransition(
                mVolumeControlStateMachine.obtainMessage(
                        mVolumeControlStateMachine.STACK_EVENT, stackEvent),
                VolumeControlStateMachine.Connected.class);
        // connected -> disconnected
        sendMessageAndVerifyTransition(
                mVolumeControlStateMachine.obtainMessage(
                        mVolumeControlStateMachine.DISCONNECT),
                VolumeControlStateMachine.Disconnecting.class);

        // disconnecting -> connected
        stackEvent = new VolumeControlStackEvent(
                VolumeControlStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        stackEvent.device = mTestDevice;
        stackEvent.valueInt1 = VolumeControlStackEvent.CONNECTION_STATE_CONNECTED;
        sendMessageAndVerifyTransition(
                mVolumeControlStateMachine.obtainMessage(
                        mVolumeControlStateMachine.STACK_EVENT, stackEvent),
                VolumeControlStateMachine.Connected.class);
        // connected -> disconnected
        stackEvent = new VolumeControlStackEvent(
                VolumeControlStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        stackEvent.device = mTestDevice;
        stackEvent.valueInt1 = VolumeControlStackEvent.CONNECTION_STATE_DISCONNECTED;
        sendMessageAndVerifyTransition(
                mVolumeControlStateMachine.obtainMessage(
                        VolumeControlStateMachine.STACK_EVENT, stackEvent),
                VolumeControlStateMachine.Disconnected.class);
    }

    private <T> void sendMessageAndVerifyTransition(Message msg, Class<T> type) {
        Mockito.clearInvocations(mVolumeControlService);
        mVolumeControlStateMachine.sendMessage(msg);
        // Verify that one connection state broadcast is executed
        verify(mVolumeControlService, timeout(TIMEOUT_MS).times(1)).sendBroadcast(
                any(Intent.class), anyString());
        Assert.assertThat(mVolumeControlStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(type));
    }
}
