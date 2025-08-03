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

package com.android.bluetooth.csip;

import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTING;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTING;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;
import com.android.bluetooth.TestUtils;
import com.android.bluetooth.btservice.AdapterService;
import org.hamcrest.core.IsInstanceOf;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class CsipSetCoordinatorStateMachineTest {
    private final String mFlagDexmarker = System.getProperty("dexmaker.share_classloader", "false");

    private BluetoothAdapter mAdapter;
    private BluetoothDevice mTestDevice;
    private HandlerThread mHandlerThread;
    private CsipSetCoordinatorStateMachineWrapper mStateMachine;
    private static final int TIMEOUT_MS = 1000;

    @Mock private AdapterService mAdapterService;
    @Mock private CsipSetCoordinatorService mService;
    @Mock private CsipSetCoordinatorNativeInterface mNativeInterface;

    @Before
    public void setUp() throws Exception {
        if (!mFlagDexmarker.equals("true")) {
            System.setProperty("dexmaker.share_classloader", "true");
        }

        // Set up mocks and test assets
        MockitoAnnotations.initMocks(this);
        TestUtils.setAdapterService(mAdapterService);

        mAdapter = BluetoothAdapter.getDefaultAdapter();

        // Get device for testing
        mTestDevice = mAdapter.getRemoteDevice("00:01:02:03:04:05");

        // Set up thread and looper
        mHandlerThread = new HandlerThread("CsipSetCoordinatorServiceTestHandlerThread");
        mHandlerThread.start();
        mStateMachine = spy(new CsipSetCoordinatorStateMachineWrapper(
                mTestDevice, mService, mNativeInterface, mHandlerThread.getLooper()));

        // Override the timeout value to speed up the test
        CsipSetCoordinatorStateMachine.sConnectTimeoutMs = 1000;
        mStateMachine.start();
    }

    @After
    public void tearDown() throws Exception {
        if (!mFlagDexmarker.equals("true")) {
            System.setProperty("dexmaker.share_classloader", mFlagDexmarker);
        }
        mStateMachine.doQuit();
        mHandlerThread.quit();
        TestUtils.clearAdapterService(mAdapterService);
    }

    /**
     * Test that default state is disconnected
     */
    @Test
    public void testDefaultDisconnectedState() {
        Assert.assertEquals(
                STATE_DISCONNECTED, mStateMachine.getConnectionState());
    }

    /**
     * Allow/disallow connection to any device
     *
     * @param allow if true, connection is allowed
     */
    private void allowConnection(boolean allow) {
        doReturn(allow).when(mService).okToConnect(any(BluetoothDevice.class));
    }

    /**
     * Test that an incoming connection with policy forbidding connection is
     * rejected
     */
    @Test
    public void testIncomingPolicyReject() {
        allowConnection(false);

        // Inject an event for when incoming connection is requested
        CsipSetCoordinatorStackEvent connStCh = new CsipSetCoordinatorStackEvent(
                CsipSetCoordinatorStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connStCh.device = mTestDevice;
        connStCh.valueInt1 = CsipSetCoordinatorStackEvent.CONNECTION_STATE_CONNECTED;
        mStateMachine.sendMessage(CsipSetCoordinatorStateMachine.STACK_EVENT, connStCh);

        // Verify that no connection state broadcast is executed
        verify(mService, after(TIMEOUT_MS).never()).sendBroadcast(any(Intent.class), anyString());
        // Check that we are in Disconnected state
        Assert.assertThat(mStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(CsipSetCoordinatorStateMachine.Disconnected.class));
    }

    /**
     * Test that an incoming connection with policy allowing connection is accepted
     */
    @Test
    public void testIncomingPolicyAccept() {
        allowConnection(true);

        // Inject an event for when incoming connection is requested
        CsipSetCoordinatorStackEvent connStCh = new CsipSetCoordinatorStackEvent(
                CsipSetCoordinatorStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connStCh.device = mTestDevice;
        connStCh.valueInt1 = connStCh.CONNECTION_STATE_CONNECTING;
        mStateMachine.sendMessage(CsipSetCoordinatorStateMachine.STACK_EVENT, connStCh);

        // Verify that one connection state broadcast is executed
        ArgumentCaptor<Intent> intentArgument1 = ArgumentCaptor.forClass(Intent.class);
        verify(mService, timeout(TIMEOUT_MS).times(1))
                .sendBroadcast(intentArgument1.capture(), anyString());
        Assert.assertEquals(STATE_CONNECTING,
                intentArgument1.getValue().getIntExtra(BluetoothProfile.EXTRA_STATE, -1));

        // Check that we are in Connecting state
        Assert.assertThat(mStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(CsipSetCoordinatorStateMachine.Connecting.class));

        // Send a message to trigger connection completed
        CsipSetCoordinatorStackEvent connCompletedEvent = new CsipSetCoordinatorStackEvent(
                CsipSetCoordinatorStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connCompletedEvent.device = mTestDevice;
        connCompletedEvent.valueInt1 = CsipSetCoordinatorStackEvent.CONNECTION_STATE_CONNECTED;
        mStateMachine.sendMessage(CsipSetCoordinatorStateMachine.STACK_EVENT, connCompletedEvent);

        // Verify that the expected number of broadcasts are executed:
        // - two calls to broadcastConnectionState(): Disconnected -> Connecting ->
        // Connected
        ArgumentCaptor<Intent> intentArgument2 = ArgumentCaptor.forClass(Intent.class);
        verify(mService, timeout(TIMEOUT_MS).times(2))
                .sendBroadcast(intentArgument2.capture(), anyString());
        // Check that we are in Connected state
        Assert.assertThat(mStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(CsipSetCoordinatorStateMachine.Connected.class));
    }

    /**
     * Test that an outgoing connection times out
     */
    @Test
    public void testOutgoingTimeout() {
        allowConnection(true);
        doReturn(true).when(mNativeInterface).connect(any(BluetoothDevice.class));
        doReturn(true).when(mNativeInterface).disconnect(any(BluetoothDevice.class));

        // Send a connect request
        mStateMachine.sendMessage(CsipSetCoordinatorStateMachine.CONNECT, mTestDevice);

        // Verify that one connection state broadcast is executed
        ArgumentCaptor<Intent> intentArgument1 = ArgumentCaptor.forClass(Intent.class);
        verify(mService, timeout(TIMEOUT_MS).times(1))
                .sendBroadcast(intentArgument1.capture(), anyString());
        Assert.assertEquals(STATE_CONNECTING,
                intentArgument1.getValue().getIntExtra(BluetoothProfile.EXTRA_STATE, -1));

        // Check that we are in Connecting state
        Assert.assertThat(mStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(CsipSetCoordinatorStateMachine.Connecting.class));

        // Verify that one connection state broadcast is executed
        ArgumentCaptor<Intent> intentArgument2 = ArgumentCaptor.forClass(Intent.class);
        verify(mService, timeout(CsipSetCoordinatorStateMachine.sConnectTimeoutMs * 2).times(2))
                .sendBroadcast(intentArgument2.capture(), anyString());
        Assert.assertEquals(STATE_DISCONNECTED,
                intentArgument2.getValue().getIntExtra(BluetoothProfile.EXTRA_STATE, -1));

        // Check that we are in Disconnected state
        Assert.assertThat(mStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(CsipSetCoordinatorStateMachine.Disconnected.class));
        verify(mNativeInterface).disconnect(eq(mTestDevice));
    }

    /**
     * Test that an incoming connection times out
     */
    @Test
    public void testIncomingTimeout() {
        allowConnection(true);
        doReturn(true).when(mNativeInterface).connect(any(BluetoothDevice.class));
        doReturn(true).when(mNativeInterface).disconnect(any(BluetoothDevice.class));

        // Inject an event for when incoming connection is requested
        CsipSetCoordinatorStackEvent connStCh = new CsipSetCoordinatorStackEvent(
                CsipSetCoordinatorStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connStCh.device = mTestDevice;
        connStCh.valueInt1 = CsipSetCoordinatorStackEvent.CONNECTION_STATE_CONNECTING;
        mStateMachine.sendMessage(CsipSetCoordinatorStateMachine.STACK_EVENT, connStCh);

        // Verify that one connection state broadcast is executed
        ArgumentCaptor<Intent> intentArgument1 = ArgumentCaptor.forClass(Intent.class);
        verify(mService, timeout(TIMEOUT_MS).times(1))
                .sendBroadcast(intentArgument1.capture(), anyString());
        Assert.assertEquals(STATE_CONNECTING,
                intentArgument1.getValue().getIntExtra(BluetoothProfile.EXTRA_STATE, -1));

        // Check that we are in Connecting state
        Assert.assertThat(mStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(CsipSetCoordinatorStateMachine.Connecting.class));

        // Verify that one connection state broadcast is executed
        ArgumentCaptor<Intent> intentArgument2 = ArgumentCaptor.forClass(Intent.class);
        verify(mService, timeout(CsipSetCoordinatorStateMachine.sConnectTimeoutMs * 2).times(2))
                .sendBroadcast(intentArgument2.capture(), anyString());
        Assert.assertEquals(STATE_DISCONNECTED,
                intentArgument2.getValue().getIntExtra(BluetoothProfile.EXTRA_STATE, -1));

        // Check that we are in Disconnected state
        Assert.assertThat(mStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(CsipSetCoordinatorStateMachine.Disconnected.class));
        verify(mNativeInterface).disconnect(eq(mTestDevice));
    }

    @Test
    public void testGetDevice() {
        Assert.assertEquals(mTestDevice, mStateMachine.getDevice());
    }

    @Test
    public void testIsConnected() {
        Assert.assertFalse(mStateMachine.isConnected());

        initToConnectedState();
        Assert.assertTrue(mStateMachine.isConnected());
    }

    @Test
    public void testDumpDoesNotCrash() {
        mStateMachine.dump(new StringBuilder());
    }

    @Test
    public void testProcessDisconnectMessage_onDisconnectedState() {
        mStateMachine.sendMessage(CsipSetCoordinatorStateMachine.DISCONNECT);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        Assert.assertEquals(STATE_DISCONNECTED, mStateMachine.getConnectionState());
    }

    @Test
    public void testProcessConnectMessage_onDisconnectedState() {
        allowConnection(false);
        mStateMachine.sendMessage(CsipSetCoordinatorStateMachine.CONNECT);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        Assert.assertEquals(STATE_DISCONNECTED, mStateMachine.getConnectionState());

        allowConnection(false);
        mStateMachine.sendMessage(CsipSetCoordinatorStateMachine.CONNECT);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        Assert.assertEquals(STATE_DISCONNECTED, mStateMachine.getConnectionState());

        allowConnection(true);
        doReturn(true).when(mNativeInterface).connect(any(BluetoothDevice.class));
        sendMessageAndVerifyTransition(
                mStateMachine.obtainMessage(CsipSetCoordinatorStateMachine.CONNECT),
                CsipSetCoordinatorStateMachine.Connecting.class);
    }

    @Test
    public void testStackEvent_withoutStateChange_onDisconnectedState() {
        allowConnection(false);

        CsipSetCoordinatorStackEvent event = new CsipSetCoordinatorStackEvent(-1);
        mStateMachine.sendMessage(CsipSetCoordinatorStateMachine.STACK_EVENT, event);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        Assert.assertEquals(STATE_DISCONNECTED, mStateMachine.getConnectionState());

        event = new CsipSetCoordinatorStackEvent(
                CsipSetCoordinatorStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.valueInt1 = CsipSetCoordinatorStackEvent.CONNECTION_STATE_DISCONNECTED;
        mStateMachine.sendMessage(CsipSetCoordinatorStateMachine.STACK_EVENT, event);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        Assert.assertEquals(STATE_DISCONNECTED, mStateMachine.getConnectionState());

        event = new CsipSetCoordinatorStackEvent(
                CsipSetCoordinatorStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.valueInt1 = CsipSetCoordinatorStackEvent.CONNECTION_STATE_CONNECTING;
        mStateMachine.sendMessage(CsipSetCoordinatorStateMachine.STACK_EVENT, event);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        Assert.assertEquals(STATE_DISCONNECTED, mStateMachine.getConnectionState());
        verify(mNativeInterface).disconnect(mTestDevice);

        Mockito.clearInvocations(mNativeInterface);
        event = new CsipSetCoordinatorStackEvent(
                CsipSetCoordinatorStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.valueInt1 = CsipSetCoordinatorStackEvent.CONNECTION_STATE_CONNECTED;
        mStateMachine.sendMessage(CsipSetCoordinatorStateMachine.STACK_EVENT, event);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        Assert.assertEquals(STATE_DISCONNECTED, mStateMachine.getConnectionState());
        verify(mNativeInterface).disconnect(mTestDevice);

        event = new CsipSetCoordinatorStackEvent(
                CsipSetCoordinatorStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.valueInt1 = CsipSetCoordinatorStackEvent.CONNECTION_STATE_DISCONNECTING;
        mStateMachine.sendMessage(CsipSetCoordinatorStateMachine.STACK_EVENT, event);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        Assert.assertEquals(STATE_DISCONNECTED, mStateMachine.getConnectionState());

        event = new CsipSetCoordinatorStackEvent(
                CsipSetCoordinatorStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.valueInt1 = -1;
        mStateMachine.sendMessage(CsipSetCoordinatorStateMachine.STACK_EVENT, event);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        Assert.assertEquals(STATE_DISCONNECTED, mStateMachine.getConnectionState());
    }

    @Test
    public void testStackEvent_toConnectingState_onDisconnectedState() {
        allowConnection(true);
        CsipSetCoordinatorStackEvent event = new CsipSetCoordinatorStackEvent(
                CsipSetCoordinatorStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.valueInt1 = CsipSetCoordinatorStackEvent.CONNECTION_STATE_CONNECTING;
        sendMessageAndVerifyTransition(
                mStateMachine.obtainMessage(CsipSetCoordinatorStateMachine.STACK_EVENT, event),
                CsipSetCoordinatorStateMachine.Connecting.class);
    }

    @Test
    public void testStackEvent_toConnectedState_onDisconnectedState() {
        allowConnection(true);
        CsipSetCoordinatorStackEvent event = new CsipSetCoordinatorStackEvent(
                CsipSetCoordinatorStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.valueInt1 = CsipSetCoordinatorStackEvent.CONNECTION_STATE_CONNECTED;
        sendMessageAndVerifyTransition(
                mStateMachine.obtainMessage(CsipSetCoordinatorStateMachine.STACK_EVENT, event),
                CsipSetCoordinatorStateMachine.Connected.class);
    }

    @Test
    public void testProcessConnectMessage_onConnectingState() {
        initToConnectingState();
        mStateMachine.sendMessage(CsipSetCoordinatorStateMachine.CONNECT);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        Assert.assertTrue(mStateMachine.doesSuperHaveDeferredMessages(
                CsipSetCoordinatorStateMachine.CONNECT));
    }

    @Test
    public void testProcessConnectTimeoutMessage_onConnectingState() {
        initToConnectingState();
        Message msg = mStateMachine.obtainMessage(CsipSetCoordinatorStateMachine.CONNECT_TIMEOUT);
        sendMessageAndVerifyTransition(msg, CsipSetCoordinatorStateMachine.Disconnected.class);
    }

    @Test
    public void testProcessDisconnectMessage_onConnectingState() {
        initToConnectingState();
        Message msg = mStateMachine.obtainMessage(CsipSetCoordinatorStateMachine.DISCONNECT);
        sendMessageAndVerifyTransition(msg, CsipSetCoordinatorStateMachine.Disconnected.class);
    }

    @Test
    public void testStackEvent_withoutStateChange_onConnectingState() {
        initToConnectingState();
        CsipSetCoordinatorStackEvent event = new CsipSetCoordinatorStackEvent(-1);
        mStateMachine.sendMessage(CsipSetCoordinatorStateMachine.STACK_EVENT, event);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        Assert.assertEquals(STATE_CONNECTING, mStateMachine.getConnectionState());

        event = new CsipSetCoordinatorStackEvent(
                CsipSetCoordinatorStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.valueInt1 = CsipSetCoordinatorStackEvent.CONNECTION_STATE_CONNECTING;
        mStateMachine.sendMessage(CsipSetCoordinatorStateMachine.STACK_EVENT, event);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        Assert.assertEquals(STATE_CONNECTING, mStateMachine.getConnectionState());

        event = new CsipSetCoordinatorStackEvent(
                CsipSetCoordinatorStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.valueInt1 = 10000;
        mStateMachine.sendMessage(CsipSetCoordinatorStateMachine.STACK_EVENT, event);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        Assert.assertEquals(STATE_CONNECTING, mStateMachine.getConnectionState());
    }

    @Test
    public void testStackEvent_toDisconnectedState_onConnectingState() {
        initToConnectingState();
        CsipSetCoordinatorStackEvent event = new CsipSetCoordinatorStackEvent(
                CsipSetCoordinatorStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.valueInt1 = CsipSetCoordinatorStackEvent.CONNECTION_STATE_DISCONNECTED;
        sendMessageAndVerifyTransition(
                mStateMachine.obtainMessage(CsipSetCoordinatorStateMachine.STACK_EVENT, event),
                CsipSetCoordinatorStateMachine.Disconnected.class);
    }

    @Test
    public void testStackEvent_toConnectedState_onConnectingState() {
        initToConnectingState();
        CsipSetCoordinatorStackEvent event = new CsipSetCoordinatorStackEvent(
                CsipSetCoordinatorStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.valueInt1 = CsipSetCoordinatorStackEvent.CONNECTION_STATE_CONNECTED;
        sendMessageAndVerifyTransition(
                mStateMachine.obtainMessage(CsipSetCoordinatorStateMachine.STACK_EVENT, event),
                CsipSetCoordinatorStateMachine.Connected.class);
    }

    @Test
    public void testStackEvent_toDisconnectingState_onConnectingState() {
        initToConnectingState();
        CsipSetCoordinatorStackEvent event = new CsipSetCoordinatorStackEvent(
                CsipSetCoordinatorStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.valueInt1 = CsipSetCoordinatorStackEvent.CONNECTION_STATE_DISCONNECTING;
        sendMessageAndVerifyTransition(
                mStateMachine.obtainMessage(CsipSetCoordinatorStateMachine.STACK_EVENT, event),
                CsipSetCoordinatorStateMachine.Disconnecting.class);
    }

    @Test
    public void testProcessConnectMessage_onConnectedState() {
        initToConnectedState();
        mStateMachine.sendMessage(CsipSetCoordinatorStateMachine.CONNECT);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        Assert.assertEquals(STATE_CONNECTED, mStateMachine.getConnectionState());
    }

    @Test
    public void testProcessDisconnectMessage_onConnectedState() {
        initToConnectedState();
        doReturn(true).when(mNativeInterface).disconnect(any(BluetoothDevice.class));
        sendMessageAndVerifyTransition(
                mStateMachine.obtainMessage(CsipSetCoordinatorStateMachine.DISCONNECT),
                CsipSetCoordinatorStateMachine.Disconnecting.class);
    }

    @Test
    public void testProcessDisconnectMessage_onConnectedState_withNativeError() {
        initToConnectedState();
        doReturn(false).when(mNativeInterface).disconnect(any(BluetoothDevice.class));
        sendMessageAndVerifyTransition(
                mStateMachine.obtainMessage(CsipSetCoordinatorStateMachine.DISCONNECT),
                CsipSetCoordinatorStateMachine.Disconnected.class);
    }

    @Test
    public void testStackEvent_withoutStateChange_onConnectedState() {
        initToConnectedState();
        CsipSetCoordinatorStackEvent event = new CsipSetCoordinatorStackEvent(-1);
        mStateMachine.sendMessage(CsipSetCoordinatorStateMachine.STACK_EVENT, event);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        Assert.assertEquals(STATE_CONNECTED, mStateMachine.getConnectionState());

        event = new CsipSetCoordinatorStackEvent(
                CsipSetCoordinatorStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.valueInt1 = CsipSetCoordinatorStackEvent.CONNECTION_STATE_CONNECTING;
        mStateMachine.sendMessage(CsipSetCoordinatorStateMachine.STACK_EVENT, event);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        Assert.assertEquals(STATE_CONNECTED, mStateMachine.getConnectionState());
    }

    @Test
    public void testStackEvent_toDisconnectedState_onConnectedState() {
        initToConnectedState();
        CsipSetCoordinatorStackEvent event = new CsipSetCoordinatorStackEvent(
                CsipSetCoordinatorStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.valueInt1 = CsipSetCoordinatorStackEvent.CONNECTION_STATE_DISCONNECTED;
        sendMessageAndVerifyTransition(
                mStateMachine.obtainMessage(CsipSetCoordinatorStateMachine.STACK_EVENT, event),
                CsipSetCoordinatorStateMachine.Disconnected.class);
    }

    @Test
    public void testStackEvent_toDisconnectingState_onConnectedState() {
        initToConnectedState();
        CsipSetCoordinatorStackEvent event = new CsipSetCoordinatorStackEvent(
                CsipSetCoordinatorStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.valueInt1 = CsipSetCoordinatorStackEvent.CONNECTION_STATE_DISCONNECTING;
        sendMessageAndVerifyTransition(
                mStateMachine.obtainMessage(CsipSetCoordinatorStateMachine.STACK_EVENT, event),
                CsipSetCoordinatorStateMachine.Disconnecting.class);
    }

    @Test
    public void testProcessConnectMessage_onDisconnectingState() {
        initToDisconnectingState();
        Message msg = mStateMachine.obtainMessage(CsipSetCoordinatorStateMachine.CONNECT);
        mStateMachine.sendMessage(msg);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        verify(mStateMachine).deferMessage(msg);
    }

    @Test
    public void testProcessConnectTimeoutMessage_onDisconnectingState() {
        initToConnectingState();
        Message msg = mStateMachine.obtainMessage(CsipSetCoordinatorStateMachine.CONNECT_TIMEOUT);
        sendMessageAndVerifyTransition(msg, CsipSetCoordinatorStateMachine.Disconnected.class);
    }

    @Test
    public void testProcessDisconnectMessage_onDisconnectingState() {
        initToDisconnectingState();
        Message msg = mStateMachine.obtainMessage(CsipSetCoordinatorStateMachine.DISCONNECT);
        mStateMachine.sendMessage(msg);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        verify(mStateMachine).deferMessage(msg);
    }

    @Test
    public void testStackEvent_withoutStateChange_onDisconnectingState() {
        initToDisconnectingState();
        CsipSetCoordinatorStackEvent event = new CsipSetCoordinatorStackEvent(-1);
        mStateMachine.sendMessage(CsipSetCoordinatorStateMachine.STACK_EVENT, event);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        Assert.assertEquals(STATE_DISCONNECTING, mStateMachine.getConnectionState());

        allowConnection(false);
        event = new CsipSetCoordinatorStackEvent(
                CsipSetCoordinatorStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.valueInt1 = CsipSetCoordinatorStackEvent.CONNECTION_STATE_CONNECTED;
        mStateMachine.sendMessage(CsipSetCoordinatorStateMachine.STACK_EVENT, event);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        verify(mNativeInterface).disconnect(any());

        Mockito.clearInvocations(mNativeInterface);
        event = new CsipSetCoordinatorStackEvent(
                CsipSetCoordinatorStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.valueInt1 = CsipSetCoordinatorStackEvent.CONNECTION_STATE_CONNECTING;
        mStateMachine.sendMessage(CsipSetCoordinatorStateMachine.STACK_EVENT, event);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        verify(mNativeInterface).disconnect(any());

        event = new CsipSetCoordinatorStackEvent(
                CsipSetCoordinatorStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.valueInt1 = CsipSetCoordinatorStackEvent.CONNECTION_STATE_DISCONNECTING;
        mStateMachine.sendMessage(CsipSetCoordinatorStateMachine.STACK_EVENT, event);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        Assert.assertEquals(STATE_DISCONNECTING, mStateMachine.getConnectionState());

        event = new CsipSetCoordinatorStackEvent(
                CsipSetCoordinatorStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.valueInt1 = 10000;
        mStateMachine.sendMessage(CsipSetCoordinatorStateMachine.STACK_EVENT, event);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        Assert.assertEquals(STATE_DISCONNECTING, mStateMachine.getConnectionState());
    }

    @Test
    public void testStackEvent_toConnectedState_onDisconnectingState() {
        initToDisconnectingState();
        allowConnection(true);
        CsipSetCoordinatorStackEvent event = new CsipSetCoordinatorStackEvent(
                CsipSetCoordinatorStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.valueInt1 = CsipSetCoordinatorStackEvent.CONNECTION_STATE_CONNECTED;
        sendMessageAndVerifyTransition(
                mStateMachine.obtainMessage(CsipSetCoordinatorStateMachine.STACK_EVENT, event),
                CsipSetCoordinatorStateMachine.Connected.class);
    }

    @Test
    public void testStackEvent_toConnectedState_butNotAllowed_onDisconnectingState() {
        initToDisconnectingState();
        allowConnection(false);
        CsipSetCoordinatorStackEvent event = new CsipSetCoordinatorStackEvent(
                CsipSetCoordinatorStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.valueInt1 = CsipSetCoordinatorStackEvent.CONNECTION_STATE_CONNECTED;
        mStateMachine.sendMessage(CsipSetCoordinatorStateMachine.STACK_EVENT, event);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        verify(mNativeInterface).disconnect(any());
    }

    @Test
    public void testStackEvent_toConnectingState_onDisconnectingState() {
        initToDisconnectingState();
        allowConnection(true);
        CsipSetCoordinatorStackEvent event = new CsipSetCoordinatorStackEvent(
                CsipSetCoordinatorStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.valueInt1 = CsipSetCoordinatorStackEvent.CONNECTION_STATE_CONNECTING;
        sendMessageAndVerifyTransition(
                mStateMachine.obtainMessage(CsipSetCoordinatorStateMachine.STACK_EVENT, event),
                CsipSetCoordinatorStateMachine.Connecting.class);
    }

    @Test
    public void testStackEvent_toConnectingState_butNotAllowed_onDisconnectingState() {
        initToDisconnectingState();
        allowConnection(false);
        CsipSetCoordinatorStackEvent event = new CsipSetCoordinatorStackEvent(
                CsipSetCoordinatorStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.valueInt1 = CsipSetCoordinatorStackEvent.CONNECTION_STATE_CONNECTED;
        mStateMachine.sendMessage(CsipSetCoordinatorStateMachine.STACK_EVENT, event);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        verify(mNativeInterface).disconnect(any());
    }

    private void initToConnectingState() {
        allowConnection(true);
        CsipSetCoordinatorStackEvent event = new CsipSetCoordinatorStackEvent(
                CsipSetCoordinatorStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.valueInt1 = CsipSetCoordinatorStackEvent.CONNECTION_STATE_CONNECTING;
        sendMessageAndVerifyTransition(
                mStateMachine.obtainMessage(CsipSetCoordinatorStateMachine.STACK_EVENT, event),
                CsipSetCoordinatorStateMachine.Connecting.class);
        allowConnection(false);
    }

    private void initToConnectedState() {
        allowConnection(true);
        CsipSetCoordinatorStackEvent event = new CsipSetCoordinatorStackEvent(
                CsipSetCoordinatorStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.valueInt1 = CsipSetCoordinatorStackEvent.CONNECTION_STATE_CONNECTED;
        sendMessageAndVerifyTransition(
                mStateMachine.obtainMessage(CsipSetCoordinatorStateMachine.STACK_EVENT, event),
                CsipSetCoordinatorStateMachine.Connected.class);
        allowConnection(false);
    }

    private void initToDisconnectingState() {
        initToConnectingState();
        CsipSetCoordinatorStackEvent event = new CsipSetCoordinatorStackEvent(
                CsipSetCoordinatorStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.valueInt1 = CsipSetCoordinatorStackEvent.CONNECTION_STATE_DISCONNECTING;
        sendMessageAndVerifyTransition(
                mStateMachine.obtainMessage(CsipSetCoordinatorStateMachine.STACK_EVENT, event),
                CsipSetCoordinatorStateMachine.Disconnecting.class);
    }

    private <T> void sendMessageAndVerifyTransition(Message msg, Class<T> type) {
        Mockito.clearInvocations(mService);
        mStateMachine.sendMessage(msg);
        // Verify that one connection state broadcast is executed
        verify(mService, timeout(TIMEOUT_MS)).sendBroadcast(any(Intent.class), anyString());
        Assert.assertThat(mStateMachine.getCurrentState(), IsInstanceOf.instanceOf(type));
    }

    public static class CsipSetCoordinatorStateMachineWrapper
            extends CsipSetCoordinatorStateMachine {

        CsipSetCoordinatorStateMachineWrapper(BluetoothDevice device,
                CsipSetCoordinatorService svc,
                CsipSetCoordinatorNativeInterface nativeInterface, Looper looper) {
            super(device, svc, nativeInterface, looper);
        }

        public boolean doesSuperHaveDeferredMessages(int what) {
            return super.hasDeferredMessages(what);
        }
    }
}
