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

package com.android.bluetooth.le_audio;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.after;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
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
import android.os.Bundle;
import android.os.HandlerThread;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.btservice.AdapterService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class LeAudioStateMachineTest {
    private BluetoothAdapter mAdapter;
    private Context mTargetContext;
    private HandlerThread mHandlerThread;
    private LeAudioStateMachine mLeAudioStateMachine;
    private BluetoothDevice mTestDevice;
    private static final int TIMEOUT_MS = 1000;

    @Mock private AdapterService mAdapterService;
    @Mock private LeAudioService mLeAudioService;
    @Mock private LeAudioNativeInterface mLeAudioNativeInterface;

    @Before
    public void setUp() throws Exception {
        mTargetContext = InstrumentationRegistry.getTargetContext();
        // Set up mocks and test assets
        MockitoAnnotations.initMocks(this);
        TestUtils.setAdapterService(mAdapterService);

        mAdapter = BluetoothAdapter.getDefaultAdapter();

        // Get a device for testing
        mTestDevice = mAdapter.getRemoteDevice("00:01:02:03:04:05");

        // Set up thread and looper
        mHandlerThread = new HandlerThread("LeAudioStateMachineTestHandlerThread");
        mHandlerThread.start();
        // Override the timeout value to speed up the test
        LeAudioStateMachine.sConnectTimeoutMs = 1000; // 1s
        mLeAudioStateMachine =
                LeAudioStateMachine.make(
                        mTestDevice,
                        mLeAudioService,
                        mLeAudioNativeInterface,
                        mHandlerThread.getLooper());
    }

    @After
    public void tearDown() throws Exception {
        mLeAudioStateMachine.doQuit();
        mHandlerThread.quit();
        TestUtils.clearAdapterService(mAdapterService);
    }

    /**
     * Test that default state is disconnected
     */
    @Test
    public void testDefaultDisconnectedState() {
        assertThat(BluetoothProfile.STATE_DISCONNECTED).isEqualTo(
                mLeAudioStateMachine.getConnectionState());
    }

    /**
     * Allow/disallow connection to any device.
     *
     * @param allow if true, connection is allowed
     */
    private void allowConnection(boolean allow) {
        doReturn(allow).when(mLeAudioService).okToConnect(any(BluetoothDevice.class));
    }

    /**
     * Test that an incoming connection with low priority is rejected
     */
    @Test
    public void testIncomingPriorityReject() {
        allowConnection(false);

        // Inject an event for when incoming connection is requested
        LeAudioStackEvent connStCh =
                new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connStCh.device = mTestDevice;
        connStCh.valueInt1 = LeAudioStackEvent.CONNECTION_STATE_CONNECTED;
        mLeAudioStateMachine.sendMessage(LeAudioStateMachine.STACK_EVENT, connStCh);

        // Verify that no connection state broadcast is executed
        verify(mLeAudioService, after(TIMEOUT_MS).never()).sendBroadcast(any(Intent.class),
                anyString(), any(Bundle.class));
        // Check that we are in Disconnected state
        assertThat(mLeAudioStateMachine.getCurrentState())
                .isInstanceOf(LeAudioStateMachine.Disconnected.class);
    }

    /**
     * Test that an incoming connection with high priority is accepted
     */
    @Test
    public void testIncomingPriorityAccept() {
        allowConnection(true);

        // Inject an event for when incoming connection is requested
        LeAudioStackEvent connStCh =
                    new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connStCh.device = mTestDevice;
        connStCh.valueInt1 = LeAudioStackEvent.CONNECTION_STATE_CONNECTING;
        mLeAudioStateMachine.sendMessage(LeAudioStateMachine.STACK_EVENT, connStCh);

        // Verify that one connection state change is notifyed
        verify(mLeAudioService, timeout(TIMEOUT_MS))
                .notifyConnectionStateChanged(
                        any(), eq(BluetoothProfile.STATE_CONNECTING), anyInt());

        // Check that we are in Connecting state
        assertThat(mLeAudioStateMachine.getCurrentState())
                .isInstanceOf(LeAudioStateMachine.Connecting.class);

        // Send a message to trigger connection completed
        LeAudioStackEvent connCompletedEvent =
                new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connCompletedEvent.device = mTestDevice;
        connCompletedEvent.valueInt1 = LeAudioStackEvent.CONNECTION_STATE_CONNECTED;
        mLeAudioStateMachine.sendMessage(LeAudioStateMachine.STACK_EVENT, connCompletedEvent);

        // Verify that the expected number of notification are called:
        // - two calls to notifyConnectionStateChanged(): Disconnected -> Connecting -> Connected
        verify(mLeAudioService, timeout(TIMEOUT_MS))
                .notifyConnectionStateChanged(
                        any(), eq(BluetoothProfile.STATE_CONNECTING), anyInt());
        verify(mLeAudioService, timeout(TIMEOUT_MS))
                .notifyConnectionStateChanged(
                        any(), eq(BluetoothProfile.STATE_CONNECTED), anyInt());
        // Check that we are in Connected state
        assertThat(mLeAudioStateMachine.getCurrentState())
                .isInstanceOf(LeAudioStateMachine.Connected.class);
    }

    /**
     * Test that an outgoing connection times out
     */
    @Test
    public void testOutgoingTimeout() {
        allowConnection(true);
        doReturn(true).when(mLeAudioNativeInterface).connectLeAudio(any(
                BluetoothDevice.class));
        doReturn(true).when(mLeAudioNativeInterface).disconnectLeAudio(any(
                BluetoothDevice.class));

        // Send a connect request
        mLeAudioStateMachine.sendMessage(LeAudioStateMachine.CONNECT, mTestDevice);

        // Verify that one connection state change is notified
        verify(mLeAudioService, timeout(TIMEOUT_MS))
                .notifyConnectionStateChanged(
                        any(), eq(BluetoothProfile.STATE_CONNECTING), anyInt());

        // Check that we are in Connecting state
        assertThat(mLeAudioStateMachine.getCurrentState())
                .isInstanceOf(LeAudioStateMachine.Connecting.class);

        // Verify that one connection state change is notified
        verify(mLeAudioService, timeout(LeAudioStateMachine.sConnectTimeoutMs * 2))
                .notifyConnectionStateChanged(
                        any(), eq(BluetoothProfile.STATE_DISCONNECTED), anyInt());

        // Check that we are in Disconnected state
        assertThat(mLeAudioStateMachine.getCurrentState())
                .isInstanceOf(LeAudioStateMachine.Disconnected.class);
    }

    /**
     * Test that an incoming connection times out
     */
    @Test
    public void testIncomingTimeout() {
        allowConnection(true);
        doReturn(true).when(mLeAudioNativeInterface).connectLeAudio(any(
                BluetoothDevice.class));
        doReturn(true).when(mLeAudioNativeInterface).disconnectLeAudio(any(
                BluetoothDevice.class));

        // Inject an event for when incoming connection is requested
        LeAudioStackEvent connStCh =
                new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connStCh.device = mTestDevice;
        connStCh.valueInt1 = LeAudioStackEvent.CONNECTION_STATE_CONNECTING;
        mLeAudioStateMachine.sendMessage(LeAudioStateMachine.STACK_EVENT, connStCh);

        // Verify that one connection state change is notified
        verify(mLeAudioService, timeout(TIMEOUT_MS))
                .notifyConnectionStateChanged(
                        any(), eq(BluetoothProfile.STATE_CONNECTING), anyInt());

        // Check that we are in Connecting state
        assertThat(mLeAudioStateMachine.getCurrentState())
                .isInstanceOf(LeAudioStateMachine.Connecting.class);

        // Verify that one connection state change is notified
        verify(mLeAudioService, timeout(LeAudioStateMachine.sConnectTimeoutMs * 2))
                .notifyConnectionStateChanged(
                        any(), eq(BluetoothProfile.STATE_DISCONNECTED), anyInt());

        // Check that we are in Disconnected state
        assertThat(mLeAudioStateMachine.getCurrentState())
                .isInstanceOf(LeAudioStateMachine.Disconnected.class);
    }
}
