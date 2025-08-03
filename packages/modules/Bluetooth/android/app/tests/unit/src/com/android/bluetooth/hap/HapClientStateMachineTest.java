/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
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

package com.android.bluetooth.hap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.HandlerThread;
import android.os.Message;
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
public class HapClientStateMachineTest {
    private BluetoothAdapter mAdapter;
    private HandlerThread mHandlerThread;
    private HapClientStateMachine mHapClientStateMachine;
    private BluetoothDevice mTestDevice;
    private static final int TIMEOUT_MS = 1000;
    boolean mIsAdapterServiceSet;

    @Mock
    private AdapterService mAdapterService;
    @Mock
    private HapClientService mHapClientService;
    @Mock
    private HapClientNativeInterface mHearingAccessGattClientInterface;

    @Before
    public void setUp() throws Exception {
        // Set up mocks and test assets
        MockitoAnnotations.initMocks(this);
        TestUtils.setAdapterService(mAdapterService);
        mIsAdapterServiceSet = true;

        mAdapter = BluetoothAdapter.getDefaultAdapter();

        // Get a device for testing
        mTestDevice = mAdapter.getRemoteDevice("00:01:02:03:04:05");

        // Set up thread and looper
        mHandlerThread = new HandlerThread("HapClientStateMachineTestHandlerThread");
        mHandlerThread.start();
        mHapClientStateMachine = new HapClientStateMachine(mTestDevice,
                mHapClientService, mHearingAccessGattClientInterface, mHandlerThread.getLooper());
        // Override the timeout value to speed up the test
        HapClientStateMachine.sConnectTimeoutMs = 1000;     // 1s
        mHapClientStateMachine.start();
    }

    @After
    public void tearDown() throws Exception {
        if (!mIsAdapterServiceSet) {
            return;
        }
        if (mHapClientStateMachine != null) {
            mHapClientStateMachine.doQuit();
            mHandlerThread.quit();
        }
        TestUtils.clearAdapterService(mAdapterService);
    }

    /**
     * Test that default state is disconnected
     */
    @Test
    public void testDefaultDisconnectedState() {
        Assert.assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mHapClientStateMachine.getConnectionState());
    }

    /**
     * Allow/disallow connection to any device.
     *
     * @param allow if true, connection is allowed
     */
    private void allowConnection(boolean allow) {
        doReturn(allow).when(mHapClientService).okToConnect(any(BluetoothDevice.class));
    }

    /**
     * Test that an incoming connection with policy forbidding connection is rejected
     */
    @Test
    public void testIncomingPolicyReject() {
        allowConnection(false);

        // Inject an event for when incoming connection is requested
        HapClientStackEvent connStCh =
                new HapClientStackEvent(
                        HapClientStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connStCh.device = mTestDevice;
        connStCh.valueInt1 = HapClientStackEvent.CONNECTION_STATE_CONNECTED;
        mHapClientStateMachine.sendMessage(HapClientStateMachine.STACK_EVENT, connStCh);

        // Verify that no connection state broadcast is executed
        verify(mHapClientService, after(TIMEOUT_MS).never()).sendBroadcast(any(Intent.class),
                anyString());
        // Check that we are in Disconnected state
        Assert.assertThat(mHapClientStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HapClientStateMachine.Disconnected.class));
    }

    /**
     * Test that an incoming connection with policy allowing connection is accepted
     */
    @Test
    public void testIncomingPolicyAccept() {
        allowConnection(true);

        // Inject an event for when incoming connection is requested
        HapClientStackEvent connStCh =
                new HapClientStackEvent(
                        HapClientStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connStCh.device = mTestDevice;
        connStCh.valueInt1 = HapClientStackEvent.CONNECTION_STATE_CONNECTING;
        mHapClientStateMachine.sendMessage(HapClientStateMachine.STACK_EVENT, connStCh);

        // Verify that one connection state broadcast is executed
        ArgumentCaptor<Intent> intentArgument1 = ArgumentCaptor.forClass(Intent.class);
        verify(mHapClientService, timeout(TIMEOUT_MS).times(1)).sendBroadcast(
                intentArgument1.capture(), anyString());
        Assert.assertEquals(BluetoothProfile.STATE_CONNECTING,
                intentArgument1.getValue().getIntExtra(BluetoothProfile.EXTRA_STATE, -1));

        // Check that we are in Connecting state
        Assert.assertThat(mHapClientStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HapClientStateMachine.Connecting.class));

        // Send a message to trigger connection completed
        HapClientStackEvent connCompletedEvent =
                new HapClientStackEvent(HapClientStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connCompletedEvent.device = mTestDevice;
        connCompletedEvent.valueInt1 = HapClientStackEvent.CONNECTION_STATE_CONNECTED;
        mHapClientStateMachine.sendMessage(HapClientStateMachine.STACK_EVENT,
                connCompletedEvent);

        // Verify that the expected number of broadcasts are executed:
        // - two calls to broadcastConnectionState(): Disconnected -> Connecting -> Connected
        ArgumentCaptor<Intent> intentArgument2 = ArgumentCaptor.forClass(Intent.class);
        verify(mHapClientService, timeout(TIMEOUT_MS).times(2)).sendBroadcast(
                intentArgument2.capture(), anyString());
        // Check that we are in Connected state
        Assert.assertThat(mHapClientStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HapClientStateMachine.Connected.class));
    }

    /**
     * Test that an outgoing connection times out
     */
    @Test
    public void testOutgoingTimeout() {
        allowConnection(true);
        doReturn(true).when(mHearingAccessGattClientInterface).connectHapClient(any(
                BluetoothDevice.class));
        doReturn(true).when(mHearingAccessGattClientInterface).disconnectHapClient(any(
                BluetoothDevice.class));

        // Send a connect request
        mHapClientStateMachine.sendMessage(HapClientStateMachine.CONNECT, mTestDevice);

        // Verify that one connection state broadcast is executed
        ArgumentCaptor<Intent> intentArgument1 = ArgumentCaptor.forClass(Intent.class);
        verify(mHapClientService, timeout(TIMEOUT_MS).times(1)).sendBroadcast(
                intentArgument1.capture(),
                anyString());
        Assert.assertEquals(BluetoothProfile.STATE_CONNECTING,
                intentArgument1.getValue().getIntExtra(BluetoothProfile.EXTRA_STATE, -1));

        // Check that we are in Connecting state
        Assert.assertThat(mHapClientStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HapClientStateMachine.Connecting.class));

        // Verify that one connection state broadcast is executed
        ArgumentCaptor<Intent> intentArgument2 = ArgumentCaptor.forClass(Intent.class);
        verify(mHapClientService, timeout(HapClientStateMachine.sConnectTimeoutMs * 2).times(
                2)).sendBroadcast(intentArgument2.capture(), anyString());
        Assert.assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                intentArgument2.getValue().getIntExtra(BluetoothProfile.EXTRA_STATE, -1));

        // Check that we are in Disconnected state
        Assert.assertThat(mHapClientStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HapClientStateMachine.Disconnected.class));
        verify(mHearingAccessGattClientInterface).disconnectHapClient(eq(mTestDevice));
    }

    /**
     * Test that an incoming connection times out
     */
    @Test
    public void testIncomingTimeout() {
        allowConnection(true);
        doReturn(true).when(mHearingAccessGattClientInterface).connectHapClient(any(
                BluetoothDevice.class));
        doReturn(true).when(mHearingAccessGattClientInterface).disconnectHapClient(any(
                BluetoothDevice.class));

        // Inject an event for when incoming connection is requested
        HapClientStackEvent connStCh =
                new HapClientStackEvent(
                        HapClientStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connStCh.device = mTestDevice;
        connStCh.valueInt1 = HapClientStackEvent.CONNECTION_STATE_CONNECTING;
        mHapClientStateMachine.sendMessage(HapClientStateMachine.STACK_EVENT, connStCh);

        // Verify that one connection state broadcast is executed
        ArgumentCaptor<Intent> intentArgument1 = ArgumentCaptor.forClass(Intent.class);
        verify(mHapClientService, timeout(TIMEOUT_MS).times(1)).sendBroadcast(
                intentArgument1.capture(),
                anyString());
        Assert.assertEquals(BluetoothProfile.STATE_CONNECTING,
                intentArgument1.getValue().getIntExtra(BluetoothProfile.EXTRA_STATE, -1));

        // Check that we are in Connecting state
        Assert.assertThat(mHapClientStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HapClientStateMachine.Connecting.class));

        // Verify that one connection state broadcast is executed
        ArgumentCaptor<Intent> intentArgument2 = ArgumentCaptor.forClass(Intent.class);
        verify(mHapClientService, timeout(HapClientStateMachine.sConnectTimeoutMs * 2).times(
                2)).sendBroadcast(intentArgument2.capture(), anyString());
        Assert.assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                intentArgument2.getValue().getIntExtra(BluetoothProfile.EXTRA_STATE, -1));

        // Check that we are in Disconnected state
        Assert.assertThat(mHapClientStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HapClientStateMachine.Disconnected.class));
        verify(mHearingAccessGattClientInterface).disconnectHapClient(eq(mTestDevice));
    }

    @Test
    public void testStatesChangesWithMessages() {
        allowConnection(true);
        doReturn(true).when(mHearingAccessGattClientInterface).connectHapClient(any(
                BluetoothDevice.class));

        // Check that we are in Disconnected state
        Assert.assertThat(mHapClientStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HapClientStateMachine.Disconnected.class));

        mHapClientStateMachine.sendMessage(HapClientStateMachine.DISCONNECT);
        // verify disconnectHapClient was called
        verify(mHearingAccessGattClientInterface, timeout(TIMEOUT_MS).times(1))
                .disconnectHapClient(any(BluetoothDevice.class));

        // disconnected -> connecting
        sendMessageAndVerifyTransition(
                mHapClientStateMachine.obtainMessage(HapClientStateMachine.CONNECT),
                HapClientStateMachine.Connecting.class);
        // connecting -> disconnected
        sendMessageAndVerifyTransition(
                mHapClientStateMachine.obtainMessage(HapClientStateMachine.CONNECT_TIMEOUT),
                HapClientStateMachine.Disconnected.class);

        // disconnected -> connecting
        sendMessageAndVerifyTransition(
                mHapClientStateMachine.obtainMessage(HapClientStateMachine.CONNECT),
                HapClientStateMachine.Connecting.class);
        // connecting -> disconnected
        sendMessageAndVerifyTransition(
                mHapClientStateMachine.obtainMessage(HapClientStateMachine.DISCONNECT),
                HapClientStateMachine.Disconnected.class);

        // disconnected -> connecting
        sendMessageAndVerifyTransition(
                mHapClientStateMachine.obtainMessage(HapClientStateMachine.CONNECT),
                HapClientStateMachine.Connecting.class);
        // connecting -> disconnecting
        HapClientStackEvent connStCh = new HapClientStackEvent(
                HapClientStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connStCh.device = mTestDevice;
        connStCh.valueInt1 = HapClientStackEvent.CONNECTION_STATE_DISCONNECTING;
        sendMessageAndVerifyTransition(
                mHapClientStateMachine.obtainMessage(HapClientStateMachine.STACK_EVENT, connStCh),
                HapClientStateMachine.Disconnecting.class);
        // disconnecting -> connecting
        connStCh = new HapClientStackEvent(
                HapClientStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connStCh.device = mTestDevice;
        connStCh.valueInt1 = HapClientStackEvent.CONNECTION_STATE_CONNECTING;
        sendMessageAndVerifyTransition(
                mHapClientStateMachine.obtainMessage(HapClientStateMachine.STACK_EVENT, connStCh),
                HapClientStateMachine.Connecting.class);
        // connecting -> connected
        connStCh = new HapClientStackEvent(
                HapClientStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connStCh.device = mTestDevice;
        connStCh.valueInt1 = HapClientStackEvent.CONNECTION_STATE_CONNECTED;
        sendMessageAndVerifyTransition(
                mHapClientStateMachine.obtainMessage(HapClientStateMachine.STACK_EVENT, connStCh),
                HapClientStateMachine.Connected.class);
        // connected -> disconnecting
        connStCh = new HapClientStackEvent(
                HapClientStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connStCh.device = mTestDevice;
        connStCh.valueInt1 = HapClientStackEvent.CONNECTION_STATE_DISCONNECTING;
        sendMessageAndVerifyTransition(
                mHapClientStateMachine.obtainMessage(HapClientStateMachine.STACK_EVENT, connStCh),
                HapClientStateMachine.Disconnecting.class);
        // disconnecting -> disconnected
        sendMessageAndVerifyTransition(
                mHapClientStateMachine.obtainMessage(HapClientStateMachine.CONNECT_TIMEOUT),
                HapClientStateMachine.Disconnected.class);

        // disconnected -> connected
        connStCh = new HapClientStackEvent(
                HapClientStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connStCh.device = mTestDevice;
        connStCh.valueInt1 = HapClientStackEvent.CONNECTION_STATE_CONNECTED;
        sendMessageAndVerifyTransition(
                mHapClientStateMachine.obtainMessage(HapClientStateMachine.STACK_EVENT, connStCh),
                HapClientStateMachine.Connected.class);
        // connected -> disconnected
        sendMessageAndVerifyTransition(
                mHapClientStateMachine.obtainMessage(HapClientStateMachine.DISCONNECT),
                HapClientStateMachine.Disconnected.class);

        // disconnected -> connected
        connStCh = new HapClientStackEvent(
                HapClientStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connStCh.device = mTestDevice;
        connStCh.valueInt1 = HapClientStackEvent.CONNECTION_STATE_CONNECTED;
        sendMessageAndVerifyTransition(
                mHapClientStateMachine.obtainMessage(HapClientStateMachine.STACK_EVENT, connStCh),
                HapClientStateMachine.Connected.class);
        // connected -> disconnected
        connStCh = new HapClientStackEvent(
                HapClientStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connStCh.device = mTestDevice;
        connStCh.valueInt1 = HapClientStackEvent.CONNECTION_STATE_DISCONNECTED;
        sendMessageAndVerifyTransition(
                mHapClientStateMachine.obtainMessage(HapClientStateMachine.STACK_EVENT, connStCh),
                HapClientStateMachine.Disconnected.class);
    }

    private <T> void sendMessageAndVerifyTransition(Message msg, Class<T> type) {
        Mockito.clearInvocations(mHapClientService);
        mHapClientStateMachine.sendMessage(msg);
        // Verify that one connection state broadcast is executed
        verify(mHapClientService, timeout(TIMEOUT_MS).times(1)).sendBroadcast(
                any(Intent.class), anyString());
        Assert.assertThat(mHapClientStateMachine.getCurrentState(), IsInstanceOf.instanceOf(type));
    }
}
