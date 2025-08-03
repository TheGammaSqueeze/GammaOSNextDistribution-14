/*
 * Copyright 2021 The Android Open Source Project
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
package com.android.bluetooth.a2dpsink;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAudioConfig;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.media.AudioFormat;
import android.os.test.TestLooper;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.TestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class A2dpSinkStateMachineTest {
    private static final String DEVICE_ADDRESS = "11:11:11:11:11:11";
    private static final int UNHANDLED_MESSAGE = 9999;

    @Mock private A2dpSinkService mService;
    @Mock private A2dpSinkNativeInterface mNativeInterface;

    private A2dpSinkStateMachine mStateMachine;
    private BluetoothAdapter mAdapter;
    private BluetoothDevice mDevice;
    private Context mTargetContext;
    private TestLooper mLooper;

    @Before
    public void setUp() throws Exception {
        mTargetContext = InstrumentationRegistry.getTargetContext();
        MockitoAnnotations.initMocks(this);

        mLooper = new TestLooper();

        mAdapter = BluetoothAdapter.getDefaultAdapter();
        assertThat(mAdapter).isNotNull();
        mDevice = mAdapter.getRemoteDevice(DEVICE_ADDRESS);

        doNothing().when(mService).removeStateMachine(any(A2dpSinkStateMachine.class));

        mStateMachine =
                new A2dpSinkStateMachine(mLooper.getLooper(), mDevice, mService, mNativeInterface);
        mStateMachine.start();
        syncHandler(-2 /* SM_INIT_CMD */);

        assertThat(mStateMachine.getDevice()).isEqualTo(mDevice);
        assertThat(mStateMachine.getAudioConfig()).isNull();
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
    }

    @After
    public void tearDown() throws Exception {
        assertThat(mLooper.nextMessage()).isNull();

        mStateMachine = null;
        mDevice = null;
        mAdapter = null;
    }

    private void syncHandler(int... what) {
        TestUtils.syncHandler(mLooper, what);
    }

    private void mockDeviceConnectionPolicy(BluetoothDevice device, int policy) {
        doReturn(policy).when(mService).getConnectionPolicy(device);
    }

    private void sendConnectionEvent(int state) {
        mStateMachine.sendMessage(A2dpSinkStateMachine.STACK_EVENT,
                StackEvent.connectionStateChanged(mDevice, state));
        syncHandler(A2dpSinkStateMachine.STACK_EVENT);
    }

    private void sendAudioConfigChangedEvent(int sampleRate, int channelCount) {
        mStateMachine.sendMessage(A2dpSinkStateMachine.STACK_EVENT,
                StackEvent.audioConfigChanged(mDevice, sampleRate, channelCount));
        syncHandler(A2dpSinkStateMachine.STACK_EVENT);
    }

    /**********************************************************************************************
     * DISCONNECTED STATE TESTS                                                                   *
     *********************************************************************************************/

    @Test
    public void testConnectInDisconnected() {
        mStateMachine.connect();
        syncHandler(A2dpSinkStateMachine.CONNECT);
        verify(mNativeInterface).connectA2dpSink(mDevice);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTING);
    }

    @Test
    public void testDisconnectInDisconnected() {
        mStateMachine.disconnect();
        syncHandler(A2dpSinkStateMachine.DISCONNECT);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
    }

    @Test
    public void testAudioConfigChangedInDisconnected() {
        sendAudioConfigChangedEvent(44, 1);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mStateMachine.getAudioConfig()).isNull();
    }

    @Test
    public void testIncomingConnectedInDisconnected() {
        sendConnectionEvent(BluetoothProfile.STATE_CONNECTED);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTED);
    }

    @Test
    public void testAllowedIncomingConnectionInDisconnected() {
        mockDeviceConnectionPolicy(mDevice, BluetoothProfile.CONNECTION_POLICY_ALLOWED);

        sendConnectionEvent(BluetoothProfile.STATE_CONNECTING);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTING);
        verify(mNativeInterface, times(0)).connectA2dpSink(mDevice);
    }

    @Test
    public void testForbiddenIncomingConnectionInDisconnected() {
        mockDeviceConnectionPolicy(mDevice, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);

        sendConnectionEvent(BluetoothProfile.STATE_CONNECTING);
        verify(mNativeInterface).disconnectA2dpSink(mDevice);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
    }

    @Test
    public void testUnknownIncomingConnectionInDisconnected() {
        mockDeviceConnectionPolicy(mDevice, BluetoothProfile.CONNECTION_POLICY_UNKNOWN);

        sendConnectionEvent(BluetoothProfile.STATE_CONNECTING);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTING);
        verify(mNativeInterface, times(0)).connectA2dpSink(mDevice);
    }

    @Test
    public void testIncomingDisconnectInDisconnected() {
        sendConnectionEvent(BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);

        syncHandler(A2dpSinkStateMachine.CLEANUP);
        verify(mService).removeStateMachine(mStateMachine);
    }

    @Test
    public void testIncomingDisconnectingInDisconnected() {
        sendConnectionEvent(BluetoothProfile.STATE_DISCONNECTING);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
        verify(mService, times(0)).removeStateMachine(mStateMachine);
    }

    @Test
    public void testIncomingConnectingInDisconnected() {
        sendConnectionEvent(BluetoothProfile.STATE_CONNECTING);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
    }

    @Test
    public void testUnhandledMessageInDisconnected() {
        mStateMachine.sendMessage(UNHANDLED_MESSAGE);
        mStateMachine.sendMessage(UNHANDLED_MESSAGE, 0 /* arbitrary payload */);
        syncHandler(UNHANDLED_MESSAGE, UNHANDLED_MESSAGE);
    }

    /**********************************************************************************************
     * CONNECTING STATE TESTS                                                                     *
     *********************************************************************************************/

    @Test
    public void testConnectedInConnecting() {
        testConnectInDisconnected();

        sendConnectionEvent(BluetoothProfile.STATE_CONNECTED);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTED);
    }

    @Test
    public void testConnectingInConnecting() {
        testConnectInDisconnected();

        sendConnectionEvent(BluetoothProfile.STATE_CONNECTING);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTING);
    }

    @Test
    public void testDisconnectingInConnecting() {
        testConnectInDisconnected();

        sendConnectionEvent(BluetoothProfile.STATE_DISCONNECTING);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTING);
    }

    @Test
    public void testDisconnectedInConnecting() {
        testConnectInDisconnected();

        sendConnectionEvent(BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);

        syncHandler(A2dpSinkStateMachine.CLEANUP);
        verify(mService).removeStateMachine(mStateMachine);
    }

    @Test
    public void testConnectionTimeoutInConnecting() {
        testConnectInDisconnected();

        mLooper.moveTimeForward(120_000); // Skip time so the timeout fires
        syncHandler(A2dpSinkStateMachine.CONNECT_TIMEOUT);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);

        syncHandler(A2dpSinkStateMachine.CLEANUP);
        verify(mService).removeStateMachine(mStateMachine);
    }

    @Test
    public void testAudioStateChangeInConnecting() {
        testConnectInDisconnected();

        sendAudioConfigChangedEvent(44, 1);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTING);
        assertThat(mStateMachine.getAudioConfig()).isNull();
    }

    @Test
    public void testConnectInConnecting() {
        testConnectInDisconnected();

        mStateMachine.connect();
        syncHandler(A2dpSinkStateMachine.CONNECT);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTING);
    }

    @Test
    public void testDisconnectInConnecting_disconnectDeferredAndProcessed() {
        testConnectInDisconnected();

        mStateMachine.disconnect();
        syncHandler(A2dpSinkStateMachine.DISCONNECT);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTING);

        // send connected, disconnect should get processed
        sendConnectionEvent(BluetoothProfile.STATE_CONNECTED);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTED);

        syncHandler(A2dpSinkStateMachine.DISCONNECT); // message was defer
        verify(mNativeInterface).disconnectA2dpSink(mDevice);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);

        syncHandler(A2dpSinkStateMachine.CLEANUP);
        verify(mService).removeStateMachine(mStateMachine);
    }

    /**********************************************************************************************
     * CONNECTED STATE TESTS                                                                      *
     *********************************************************************************************/

    @Test
    public void testConnectInConnected() {
        testConnectedInConnecting();

        mStateMachine.connect();
        syncHandler(A2dpSinkStateMachine.CONNECT);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTED);
    }

    @Test
    public void testDisconnectInConnected() {
        testConnectedInConnecting();

        mStateMachine.disconnect();
        syncHandler(A2dpSinkStateMachine.DISCONNECT);
        verify(mNativeInterface).disconnectA2dpSink(mDevice);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);

        syncHandler(A2dpSinkStateMachine.CLEANUP);
        verify(mService).removeStateMachine(mStateMachine);
    }

    @Test
    public void testAudioStateChangeInConnected() {
        testConnectedInConnecting();

        sendAudioConfigChangedEvent(44, 1);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTED);

        BluetoothAudioConfig expected =
                new BluetoothAudioConfig(44, 1, AudioFormat.ENCODING_PCM_16BIT);
        assertThat(mStateMachine.getAudioConfig()).isEqualTo(expected);
    }

    @Test
    public void testConnectedInConnected() {
        testConnectedInConnecting();

        sendConnectionEvent(BluetoothProfile.STATE_CONNECTED);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTED);
    }

    @Test
    public void testConnectingInConnected() {
        testConnectedInConnecting();

        sendConnectionEvent(BluetoothProfile.STATE_CONNECTING);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTED);
    }

    @Test
    public void testDisconnectingInConnected() {
        testConnectedInConnecting();

        sendConnectionEvent(BluetoothProfile.STATE_DISCONNECTING);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);

        syncHandler(A2dpSinkStateMachine.CLEANUP);
        verify(mService).removeStateMachine(mStateMachine);
    }

    @Test
    public void testDisconnectedInConnected() {
        testConnectedInConnecting();

        sendConnectionEvent(BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);

        syncHandler(A2dpSinkStateMachine.CLEANUP);
        verify(mService).removeStateMachine(mStateMachine);
    }

    /**********************************************************************************************
     * OTHER TESTS                                                                                *
     *********************************************************************************************/

    @Test
    public void testDump() {
        StringBuilder sb = new StringBuilder();
        mStateMachine.dump(sb);
        assertThat(sb.toString()).isNotNull();
    }
}
