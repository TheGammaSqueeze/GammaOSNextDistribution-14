/*
 * Copyright 2017 The Android Open Source Project
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

package com.android.bluetooth.hfp;

import static android.Manifest.permission.BLUETOOTH_CONNECT;

import static org.mockito.Mockito.*;
import static org.junit.Assume.assumeTrue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.HandlerThread;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.btservice.ActiveDeviceManager;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.RemoteDevices;
import com.android.bluetooth.btservice.SilenceDeviceManager;
import com.android.bluetooth.btservice.storage.DatabaseManager;
import com.android.bluetooth.flags.Flags;

import org.hamcrest.core.IsInstanceOf;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;

/**
 * Tests for {@link HeadsetStateMachine}
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class HeadsetStateMachineTest {
    private static final int CONNECT_TIMEOUT_TEST_MILLIS = 1000;
    private static final int CONNECT_TIMEOUT_TEST_WAIT_MILLIS = CONNECT_TIMEOUT_TEST_MILLIS * 3 / 2;
    private static final int ASYNC_CALL_TIMEOUT_MILLIS = 250;
    private static final String TEST_PHONE_NUMBER = "1234567890";
    private static final int MAX_RETRY_DISCONNECT_AUDIO = 3;

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private Context mTargetContext;
    private BluetoothAdapter mAdapter;
    private HandlerThread mHandlerThread;
    private HeadsetStateMachine mHeadsetStateMachine;
    private BluetoothDevice mTestDevice;
    private ArgumentCaptor<Intent> mIntentArgument = ArgumentCaptor.forClass(Intent.class);

    @Mock private AdapterService mAdapterService;
    @Mock private ActiveDeviceManager mActiveDeviceManager;
    @Mock private SilenceDeviceManager mSilenceDeviceManager;
    @Mock private DatabaseManager mDatabaseManager;
    @Mock private HeadsetService mHeadsetService;
    @Mock private HeadsetSystemInterface mSystemInterface;
    @Mock private AudioManager mAudioManager;
    @Mock private HeadsetPhoneState mPhoneState;
    @Mock private Intent mIntent;
    private MockContentResolver mMockContentResolver;
    @Mock private HeadsetNativeInterface mNativeInterface;
    @Mock private RemoteDevices mRemoteDevices;

    @Before
    public void setUp() throws Exception {
        mTargetContext = InstrumentationRegistry.getTargetContext();
        // Setup mocks and test assets
        MockitoAnnotations.initMocks(this);
        TestUtils.setAdapterService(mAdapterService);
        // Stub system interface
        doReturn(mPhoneState).when(mSystemInterface).getHeadsetPhoneState();
        doReturn(mAudioManager).when(mSystemInterface).getAudioManager();
        // This line must be called to make sure relevant objects are initialized properly
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        // Get a device for testing
        mTestDevice = mAdapter.getRemoteDevice("00:01:02:03:04:05");
        // Get a database
        doReturn(mDatabaseManager).when(mAdapterService).getDatabase();
        doReturn(true).when(mDatabaseManager).setAudioPolicyMetadata(anyObject(), anyObject());
        // Get an active device manager
        doReturn(mActiveDeviceManager).when(mAdapterService).getActiveDeviceManager();
        // Get a silence device manager
        doReturn(mSilenceDeviceManager).when(mAdapterService).getSilenceDeviceManager();
        doReturn(mRemoteDevices).when(mAdapterService).getRemoteDevices();
        doReturn(true).when(mNativeInterface).connectHfp(mTestDevice);
        doReturn(true).when(mNativeInterface).disconnectHfp(mTestDevice);
        doReturn(true).when(mNativeInterface).connectAudio(mTestDevice);
        doReturn(true).when(mNativeInterface).disconnectAudio(mTestDevice);
        // Stub headset service
        mMockContentResolver = new MockContentResolver();
        when(mHeadsetService.getContentResolver()).thenReturn(mMockContentResolver);
        doReturn(BluetoothDevice.BOND_BONDED).when(mAdapterService)
                .getBondState(any(BluetoothDevice.class));
        when(mHeadsetService.bindService(any(Intent.class), any(ServiceConnection.class), anyInt()))
                .thenReturn(true);
        when(mHeadsetService.getResources()).thenReturn(
                InstrumentationRegistry.getTargetContext().getResources());
        when(mHeadsetService.getPackageManager()).thenReturn(
                InstrumentationRegistry.getContext().getPackageManager());
        when(mHeadsetService.getConnectionPolicy(any(BluetoothDevice.class))).thenReturn(
                BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        when(mHeadsetService.getForceScoAudio()).thenReturn(true);
        when(mHeadsetService.okToAcceptConnection(any(BluetoothDevice.class), anyBoolean()))
                .thenReturn(true);
        when(mHeadsetService.isScoAcceptable(any(BluetoothDevice.class))).thenReturn(
                BluetoothStatusCodes.SUCCESS);
        // Setup thread and looper
        mHandlerThread = new HandlerThread("HeadsetStateMachineTestHandlerThread");
        mHandlerThread.start();
        // Modify CONNECT timeout to a smaller value for test only
        HeadsetStateMachine.sConnectTimeoutMs = CONNECT_TIMEOUT_TEST_MILLIS;
        mHeadsetStateMachine = HeadsetObjectsFactory.getInstance()
                .makeStateMachine(mTestDevice, mHandlerThread.getLooper(), mHeadsetService,
                        mAdapterService, mNativeInterface, mSystemInterface);
    }

    @After
    public void tearDown() throws Exception {
        HeadsetObjectsFactory.getInstance().destroyStateMachine(mHeadsetStateMachine);
        mHandlerThread.quit();
        TestUtils.clearAdapterService(mAdapterService);
    }

    /**
     * Test that default state is Disconnected
     */
    @Test
    public void testDefaultDisconnectedState() {
        Assert.assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mHeadsetStateMachine.getConnectionState());
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.Disconnected.class));
    }

    /**
     * Test that state is Connected after calling setUpConnectedState()
     */
    @Test
    public void testSetupConnectedState() {
        setUpConnectedState();
        Assert.assertEquals(BluetoothProfile.STATE_CONNECTED,
                mHeadsetStateMachine.getConnectionState());
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.Connected.class));
    }

    /**
     * Test state transition from Disconnected to Connecting state via CONNECT message
     */
    @Test
    public void testStateTransition_DisconnectedToConnecting_Connect() {
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.CONNECT, mTestDevice);
        verify(mHeadsetService, timeout(ASYNC_CALL_TIMEOUT_MILLIS)).sendBroadcastAsUser(
                mIntentArgument.capture(), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        HeadsetTestUtils.verifyConnectionStateBroadcast(mTestDevice,
                BluetoothProfile.STATE_CONNECTING, BluetoothProfile.STATE_DISCONNECTED,
                mIntentArgument.getValue());
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.Connecting.class));
    }

    /**
     * Test state transition from Disconnected to Connecting state via StackEvent.CONNECTED message
     */
    @Test
    public void testStateTransition_DisconnectedToConnecting_StackConnected() {
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED,
                        HeadsetHalConstants.CONNECTION_STATE_CONNECTED, mTestDevice));
        verify(mHeadsetService, timeout(ASYNC_CALL_TIMEOUT_MILLIS)).sendBroadcastAsUser(
                mIntentArgument.capture(), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        HeadsetTestUtils.verifyConnectionStateBroadcast(mTestDevice,
                BluetoothProfile.STATE_CONNECTING, BluetoothProfile.STATE_DISCONNECTED,
                mIntentArgument.getValue());
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.Connecting.class));
    }

    /**
     * Test state transition from Disconnected to Connecting state via StackEvent.CONNECTING message
     */
    @Test
    public void testStateTransition_DisconnectedToConnecting_StackConnecting() {
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED,
                        HeadsetHalConstants.CONNECTION_STATE_CONNECTING, mTestDevice));
        verify(mHeadsetService, timeout(ASYNC_CALL_TIMEOUT_MILLIS)).sendBroadcastAsUser(
                mIntentArgument.capture(), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        HeadsetTestUtils.verifyConnectionStateBroadcast(mTestDevice,
                BluetoothProfile.STATE_CONNECTING, BluetoothProfile.STATE_DISCONNECTED,
                mIntentArgument.getValue());
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.Connecting.class));
    }

    /**
     * Test state transition from Connecting to Disconnected state via StackEvent.DISCONNECTED
     * message
     */
    @Test
    public void testStateTransition_ConnectingToDisconnected_StackDisconnected() {
        int numBroadcastsSent = setUpConnectingState();
        // Indicate disconnecting to test state machine, which should do nothing
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED,
                        HeadsetHalConstants.CONNECTION_STATE_DISCONNECTING, mTestDevice));
        // Should do nothing new
        verify(mHeadsetService,
                after(ASYNC_CALL_TIMEOUT_MILLIS).times(numBroadcastsSent)).sendBroadcastAsUser(
                any(Intent.class), any(UserHandle.class), anyString(), any(Bundle.class));
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.Connecting.class));

        // Indicate connection failed to test state machine
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED,
                        HeadsetHalConstants.CONNECTION_STATE_DISCONNECTED, mTestDevice));

        numBroadcastsSent++;
        verify(mHeadsetService,
                timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(numBroadcastsSent)).sendBroadcastAsUser(
                mIntentArgument.capture(), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        HeadsetTestUtils.verifyConnectionStateBroadcast(mTestDevice,
                BluetoothProfile.STATE_DISCONNECTED, BluetoothProfile.STATE_CONNECTING,
                mIntentArgument.getValue());
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.Disconnected.class));
    }

    /**
     * Test state transition from Connecting to Disconnected state via CONNECT_TIMEOUT message
     */
    @Test
    public void testStateTransition_ConnectingToDisconnected_Timeout() {
        int numBroadcastsSent = setUpConnectingState();
        // Let the connection timeout
        numBroadcastsSent++;
        verify(mHeadsetService, timeout(CONNECT_TIMEOUT_TEST_WAIT_MILLIS).times(
                numBroadcastsSent)).sendBroadcastAsUser(mIntentArgument.capture(),
                eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        HeadsetTestUtils.verifyConnectionStateBroadcast(mTestDevice,
                BluetoothProfile.STATE_DISCONNECTED, BluetoothProfile.STATE_CONNECTING,
                mIntentArgument.getValue());
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.Disconnected.class));
    }

    /**
     * Test state transition from Connecting to Connected state via StackEvent.SLC_CONNECTED message
     */
    @Test
    public void testStateTransition_ConnectingToConnected_StackSlcConnected() {
        int numBroadcastsSent = setUpConnectingState();
        // Indicate connecting to test state machine
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED,
                        HeadsetHalConstants.CONNECTION_STATE_CONNECTING, mTestDevice));
        // Should do nothing
        verify(mHeadsetService,
                after(ASYNC_CALL_TIMEOUT_MILLIS).times(numBroadcastsSent)).sendBroadcastAsUser(
                any(Intent.class), any(UserHandle.class), anyString(), any(Bundle.class));
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.Connecting.class));

        // Indicate RFCOMM connection is successful to test state machine
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED,
                        HeadsetHalConstants.CONNECTION_STATE_CONNECTED, mTestDevice));
        // Should do nothing
        verify(mHeadsetService,
                after(ASYNC_CALL_TIMEOUT_MILLIS).times(numBroadcastsSent)).sendBroadcastAsUser(
                any(Intent.class), any(UserHandle.class), anyString(), any(Bundle.class));
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.Connecting.class));

        // Indicate SLC connection is successful to test state machine
        numBroadcastsSent++;
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED,
                        HeadsetHalConstants.CONNECTION_STATE_SLC_CONNECTED, mTestDevice));
        verify(mHeadsetService,
                timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(numBroadcastsSent)).sendBroadcastAsUser(
                mIntentArgument.capture(), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        HeadsetTestUtils.verifyConnectionStateBroadcast(mTestDevice,
                BluetoothProfile.STATE_CONNECTED, BluetoothProfile.STATE_CONNECTING,
                mIntentArgument.getValue());
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.Connected.class));
    }

    /**
     * Test state transition from Disconnecting to Disconnected state via StackEvent.DISCONNECTED
     * message
     */
    @Test
    public void testStateTransition_DisconnectingToDisconnected_StackDisconnected() {
        int numBroadcastsSent = setUpDisconnectingState();
        // Send StackEvent.DISCONNECTED message
        numBroadcastsSent++;
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED,
                        HeadsetHalConstants.CONNECTION_STATE_DISCONNECTED, mTestDevice));
        verify(mHeadsetService,
                timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(numBroadcastsSent)).sendBroadcastAsUser(
                mIntentArgument.capture(), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        HeadsetTestUtils.verifyConnectionStateBroadcast(mTestDevice,
                BluetoothProfile.STATE_DISCONNECTED, BluetoothProfile.STATE_DISCONNECTING,
                mIntentArgument.getValue());
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.Disconnected.class));
    }

    /**
     * Test state transition from Disconnecting to Disconnected state via CONNECT_TIMEOUT
     * message
     */
    @Test
    public void testStateTransition_DisconnectingToDisconnected_Timeout() {
        int numBroadcastsSent = setUpDisconnectingState();
        // Let the connection timeout
        numBroadcastsSent++;
        verify(mHeadsetService, timeout(CONNECT_TIMEOUT_TEST_WAIT_MILLIS).times(
                numBroadcastsSent)).sendBroadcastAsUser(mIntentArgument.capture(),
                eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        HeadsetTestUtils.verifyConnectionStateBroadcast(mTestDevice,
                BluetoothProfile.STATE_DISCONNECTED, BluetoothProfile.STATE_DISCONNECTING,
                mIntentArgument.getValue());
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.Disconnected.class));
    }

    /**
     * Test state transition from Disconnecting to Connected state via StackEvent.SLC_CONNECTED
     * message
     */
    @Test
    public void testStateTransition_DisconnectingToConnected_StackSlcCconnected() {
        int numBroadcastsSent = setUpDisconnectingState();
        // Send StackEvent.SLC_CONNECTED message
        numBroadcastsSent++;
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED,
                        HeadsetHalConstants.CONNECTION_STATE_SLC_CONNECTED, mTestDevice));
        verify(mHeadsetService,
                timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(numBroadcastsSent)).sendBroadcastAsUser(
                mIntentArgument.capture(), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        HeadsetTestUtils.verifyConnectionStateBroadcast(mTestDevice,
                BluetoothProfile.STATE_CONNECTED, BluetoothProfile.STATE_DISCONNECTING,
                mIntentArgument.getValue());
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.Connected.class));
    }

    /**
     * Test state transition from Connected to Disconnecting state via DISCONNECT message
     */
    @Test
    public void testStateTransition_ConnectedToDisconnecting_Disconnect() {
        int numBroadcastsSent = setUpConnectedState();
        // Send DISCONNECT message
        numBroadcastsSent++;
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.DISCONNECT, mTestDevice);
        verify(mHeadsetService,
                timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(numBroadcastsSent)).sendBroadcastAsUser(
                mIntentArgument.capture(), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        HeadsetTestUtils.verifyConnectionStateBroadcast(mTestDevice,
                BluetoothProfile.STATE_DISCONNECTING, BluetoothProfile.STATE_CONNECTED,
                mIntentArgument.getValue());
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.Disconnecting.class));
    }

    /**
     * Test state transition from Connected to Disconnecting state via StackEvent.DISCONNECTING
     * message
     */
    @Test
    public void testStateTransition_ConnectedToDisconnecting_StackDisconnecting() {
        int numBroadcastsSent = setUpConnectedState();
        // Send StackEvent.DISCONNECTING message
        numBroadcastsSent++;
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED,
                        HeadsetHalConstants.CONNECTION_STATE_DISCONNECTING, mTestDevice));
        verify(mHeadsetService,
                timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(numBroadcastsSent)).sendBroadcastAsUser(
                mIntentArgument.capture(), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        HeadsetTestUtils.verifyConnectionStateBroadcast(mTestDevice,
                BluetoothProfile.STATE_DISCONNECTING, BluetoothProfile.STATE_CONNECTED,
                mIntentArgument.getValue());
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.Disconnecting.class));
    }

    /**
     * Test state transition from Connected to Disconnected state via StackEvent.DISCONNECTED
     * message
     */
    @Test
    public void testStateTransition_ConnectedToDisconnected_StackDisconnected() {
        int numBroadcastsSent = setUpConnectedState();
        // Send StackEvent.DISCONNECTED message
        numBroadcastsSent++;
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED,
                        HeadsetHalConstants.CONNECTION_STATE_DISCONNECTED, mTestDevice));
        verify(mHeadsetService,
                timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(numBroadcastsSent)).sendBroadcastAsUser(
                mIntentArgument.capture(), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        HeadsetTestUtils.verifyConnectionStateBroadcast(mTestDevice,
                BluetoothProfile.STATE_DISCONNECTED, BluetoothProfile.STATE_CONNECTED,
                mIntentArgument.getValue());
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.Disconnected.class));
    }

    /**
     * Test state transition from Connected to AudioConnecting state via CONNECT_AUDIO message
     */
    @Test
    public void testStateTransition_ConnectedToAudioConnecting_ConnectAudio() {
        int numBroadcastsSent = setUpConnectedState();
        // Send CONNECT_AUDIO message
        numBroadcastsSent++;
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.CONNECT_AUDIO, mTestDevice);
        verify(mHeadsetService,
                timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(numBroadcastsSent)).sendBroadcastAsUser(
                mIntentArgument.capture(), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        HeadsetTestUtils.verifyAudioStateBroadcast(mTestDevice,
                BluetoothHeadset.STATE_AUDIO_CONNECTING, BluetoothHeadset.STATE_AUDIO_DISCONNECTED,
                mIntentArgument.getValue());
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.AudioConnecting.class));
    }

    /**
     * Test state transition from Connected to AudioConnecting state via
     * StackEvent.AUDIO_CONNECTING message
     */
    @Test
    public void testStateTransition_ConnectedToAudioConnecting_StackAudioConnecting() {
        int numBroadcastsSent = setUpConnectedState();
        // Send StackEvent.AUDIO_CONNECTING message
        numBroadcastsSent++;
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_AUDIO_STATE_CHANGED,
                        HeadsetHalConstants.AUDIO_STATE_CONNECTING, mTestDevice));
        verify(mHeadsetService,
                timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(numBroadcastsSent)).sendBroadcastAsUser(
                mIntentArgument.capture(), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        HeadsetTestUtils.verifyAudioStateBroadcast(mTestDevice,
                BluetoothHeadset.STATE_AUDIO_CONNECTING, BluetoothHeadset.STATE_AUDIO_DISCONNECTED,
                mIntentArgument.getValue());
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.AudioConnecting.class));
    }

    /**
     * Test state transition from Connected to AudioOn state via StackEvent.AUDIO_CONNECTED message
     */
    @Test
    public void testStateTransition_ConnectedToAudioOn_StackAudioConnected() {
        int numBroadcastsSent = setUpConnectedState();
        // Send StackEvent.AUDIO_CONNECTED message
        numBroadcastsSent++;
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_AUDIO_STATE_CHANGED,
                        HeadsetHalConstants.AUDIO_STATE_CONNECTED, mTestDevice));
        verify(mHeadsetService,
                timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(numBroadcastsSent)).sendBroadcastAsUser(
                mIntentArgument.capture(), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        HeadsetTestUtils.verifyAudioStateBroadcast(mTestDevice,
                BluetoothHeadset.STATE_AUDIO_CONNECTED, BluetoothHeadset.STATE_AUDIO_DISCONNECTED,
                mIntentArgument.getValue());
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.AudioOn.class));
    }

    /**
     * Test state transition from AudioConnecting to Connected state via CONNECT_TIMEOUT message
     */
    @Test
    public void testStateTransition_AudioConnectingToConnected_Timeout() {
        int numBroadcastsSent = setUpAudioConnectingState();
        // Wait for connection to timeout
        numBroadcastsSent++;
        verify(mHeadsetService, timeout(CONNECT_TIMEOUT_TEST_WAIT_MILLIS).times(
                numBroadcastsSent)).sendBroadcastAsUser(mIntentArgument.capture(),
                eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        HeadsetTestUtils.verifyAudioStateBroadcast(mTestDevice,
                BluetoothHeadset.STATE_AUDIO_DISCONNECTED, BluetoothHeadset.STATE_AUDIO_CONNECTING,
                mIntentArgument.getValue());
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.Connected.class));
    }

    /**
     * Test state transition from AudioConnecting to Connected state via
     * StackEvent.AUDIO_DISCONNECTED message
     */
    @Test
    public void testStateTransition_AudioConnectingToConnected_StackAudioDisconnected() {
        int numBroadcastsSent = setUpAudioConnectingState();
        // Send StackEvent.AUDIO_DISCONNECTED message
        numBroadcastsSent++;
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_AUDIO_STATE_CHANGED,
                        HeadsetHalConstants.AUDIO_STATE_DISCONNECTED, mTestDevice));
        verify(mHeadsetService,
                timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(numBroadcastsSent)).sendBroadcastAsUser(
                mIntentArgument.capture(), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        HeadsetTestUtils.verifyAudioStateBroadcast(mTestDevice,
                BluetoothHeadset.STATE_AUDIO_DISCONNECTED, BluetoothHeadset.STATE_AUDIO_CONNECTING,
                mIntentArgument.getValue());
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.Connected.class));
    }

    /**
     * Test state transition from AudioConnecting to Disconnected state via
     * StackEvent.DISCONNECTED message
     */
    @Test
    public void testStateTransition_AudioConnectingToDisconnected_StackDisconnected() {
        int numBroadcastsSent = setUpAudioConnectingState();
        // Send StackEvent.DISCONNECTED message
        numBroadcastsSent += 2;
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED,
                        HeadsetHalConstants.CONNECTION_STATE_DISCONNECTED, mTestDevice));
        verify(mHeadsetService,
                timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(numBroadcastsSent)).sendBroadcastAsUser(
                mIntentArgument.capture(), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        HeadsetTestUtils.verifyAudioStateBroadcast(mTestDevice,
                BluetoothHeadset.STATE_AUDIO_DISCONNECTED, BluetoothHeadset.STATE_AUDIO_CONNECTING,
                mIntentArgument.getAllValues().get(mIntentArgument.getAllValues().size() - 2));
        HeadsetTestUtils.verifyConnectionStateBroadcast(mTestDevice,
                BluetoothProfile.STATE_DISCONNECTED, BluetoothProfile.STATE_CONNECTED,
                mIntentArgument.getAllValues().get(mIntentArgument.getAllValues().size() - 1));
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.Disconnected.class));
    }

    /**
     * Test state transition from AudioConnecting to Disconnecting state via
     * StackEvent.DISCONNECTING message
     */
    @Test
    public void testStateTransition_AudioConnectingToDisconnecting_StackDisconnecting() {
        int numBroadcastsSent = setUpAudioConnectingState();
        // Send StackEvent.DISCONNECTED message
        numBroadcastsSent += 2;
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED,
                        HeadsetHalConstants.CONNECTION_STATE_DISCONNECTING, mTestDevice));
        verify(mHeadsetService,
                timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(numBroadcastsSent)).sendBroadcastAsUser(
                mIntentArgument.capture(), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        HeadsetTestUtils.verifyAudioStateBroadcast(mTestDevice,
                BluetoothHeadset.STATE_AUDIO_DISCONNECTED, BluetoothHeadset.STATE_AUDIO_CONNECTING,
                mIntentArgument.getAllValues().get(mIntentArgument.getAllValues().size() - 2));
        HeadsetTestUtils.verifyConnectionStateBroadcast(mTestDevice,
                BluetoothProfile.STATE_DISCONNECTING, BluetoothProfile.STATE_CONNECTED,
                mIntentArgument.getAllValues().get(mIntentArgument.getAllValues().size() - 1));
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.Disconnecting.class));
    }

    /**
     * Test state transition from AudioConnecting to AudioOn state via
     * StackEvent.AUDIO_CONNECTED message
     */
    @Test
    public void testStateTransition_AudioConnectingToAudioOn_StackAudioConnected() {
        int numBroadcastsSent = setUpAudioConnectingState();
        // Send StackEvent.AUDIO_DISCONNECTED message
        numBroadcastsSent++;
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_AUDIO_STATE_CHANGED,
                        HeadsetHalConstants.AUDIO_STATE_CONNECTED, mTestDevice));
        verify(mHeadsetService,
                timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(numBroadcastsSent)).sendBroadcastAsUser(
                mIntentArgument.capture(), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        HeadsetTestUtils.verifyAudioStateBroadcast(mTestDevice,
                BluetoothHeadset.STATE_AUDIO_CONNECTED, BluetoothHeadset.STATE_AUDIO_CONNECTING,
                mIntentArgument.getValue());
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.AudioOn.class));
    }

    /**
     * Test state transition from AudioOn to AudioDisconnecting state via
     * StackEvent.AUDIO_DISCONNECTING message
     */
    @Test
    public void testStateTransition_AudioOnToAudioDisconnecting_StackAudioDisconnecting() {
        int numBroadcastsSent = setUpAudioOnState();
        // Send StackEvent.AUDIO_DISCONNECTING message
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_AUDIO_STATE_CHANGED,
                        HeadsetHalConstants.AUDIO_STATE_DISCONNECTING, mTestDevice));
        verify(mHeadsetService,
                after(ASYNC_CALL_TIMEOUT_MILLIS).times(numBroadcastsSent)).sendBroadcastAsUser(
                any(Intent.class), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.AudioDisconnecting.class));
    }

    /**
     * Test state transition from AudioOn to AudioDisconnecting state via
     * DISCONNECT_AUDIO message
     */
    @Test
    public void testStateTransition_AudioOnToAudioDisconnecting_DisconnectAudio() {
        int numBroadcastsSent = setUpAudioOnState();
        // Send DISCONNECT_AUDIO message
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.DISCONNECT_AUDIO, mTestDevice);
        // Should not sent any broadcast due to lack of AUDIO_DISCONNECTING intent value
        verify(mHeadsetService,
                after(ASYNC_CALL_TIMEOUT_MILLIS).times(numBroadcastsSent)).sendBroadcastAsUser(
                any(Intent.class), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.AudioDisconnecting.class));
    }

    /**
     * Test state transition from AudioOn to AudioDisconnecting state via
     * Stack.AUDIO_DISCONNECTED message
     */
    @Test
    public void testStateTransition_AudioOnToConnected_StackAudioDisconnected() {
        int numBroadcastsSent = setUpAudioOnState();
        // Send DISCONNECT_AUDIO message
        numBroadcastsSent++;
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_AUDIO_STATE_CHANGED,
                        HeadsetHalConstants.AUDIO_STATE_DISCONNECTED, mTestDevice));
        verify(mHeadsetService,
                timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(numBroadcastsSent)).sendBroadcastAsUser(
                mIntentArgument.capture(), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        HeadsetTestUtils.verifyAudioStateBroadcast(mTestDevice,
                BluetoothHeadset.STATE_AUDIO_DISCONNECTED, BluetoothHeadset.STATE_AUDIO_CONNECTED,
                mIntentArgument.getValue());
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.Connected.class));
    }

    /**
     * Test state transition from AudioOn to Disconnected state via
     * Stack.DISCONNECTED message
     */
    @Test
    public void testStateTransition_AudioOnToDisconnected_StackDisconnected() {
        int numBroadcastsSent = setUpAudioOnState();
        // Send StackEvent.DISCONNECTED message
        numBroadcastsSent += 2;
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED,
                        HeadsetHalConstants.CONNECTION_STATE_DISCONNECTED, mTestDevice));
        verify(mHeadsetService,
                timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(numBroadcastsSent)).sendBroadcastAsUser(
                mIntentArgument.capture(), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        HeadsetTestUtils.verifyAudioStateBroadcast(mTestDevice,
                BluetoothHeadset.STATE_AUDIO_DISCONNECTED, BluetoothHeadset.STATE_AUDIO_CONNECTED,
                mIntentArgument.getAllValues().get(mIntentArgument.getAllValues().size() - 2));
        HeadsetTestUtils.verifyConnectionStateBroadcast(mTestDevice,
                BluetoothProfile.STATE_DISCONNECTED, BluetoothProfile.STATE_CONNECTED,
                mIntentArgument.getAllValues().get(mIntentArgument.getAllValues().size() - 1));
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.Disconnected.class));
    }

    /**
     * Test state transition from AudioOn to Disconnecting state via
     * Stack.DISCONNECTING message
     */
    @Test
    public void testStateTransition_AudioOnToDisconnecting_StackDisconnecting() {
        int numBroadcastsSent = setUpAudioOnState();
        // Send StackEvent.DISCONNECTING message
        numBroadcastsSent += 2;
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED,
                        HeadsetHalConstants.CONNECTION_STATE_DISCONNECTING, mTestDevice));
        verify(mHeadsetService,
                timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(numBroadcastsSent)).sendBroadcastAsUser(
                mIntentArgument.capture(), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        HeadsetTestUtils.verifyAudioStateBroadcast(mTestDevice,
                BluetoothHeadset.STATE_AUDIO_DISCONNECTED, BluetoothHeadset.STATE_AUDIO_CONNECTED,
                mIntentArgument.getAllValues().get(mIntentArgument.getAllValues().size() - 2));
        HeadsetTestUtils.verifyConnectionStateBroadcast(mTestDevice,
                BluetoothProfile.STATE_DISCONNECTING, BluetoothProfile.STATE_CONNECTED,
                mIntentArgument.getAllValues().get(mIntentArgument.getAllValues().size() - 1));
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.Disconnecting.class));
    }

    /**
     * Test state transition from AudioDisconnecting to AudioOn state via CONNECT_TIMEOUT message
     * until retry count is reached, then test transition to Disconnecting state.
     */
    @Test
    public void testStateTransition_AudioDisconnectingToAudioOnAndDisconnecting_Timeout() {
        int numBroadcastsSent = setUpAudioDisconnectingState();
        // Wait for connection to timeout
        numBroadcastsSent++;
        for (int i = 0; i <= MAX_RETRY_DISCONNECT_AUDIO; i++) {
            if (i > 0) { // Skip first AUDIO_DISCONNECTING init as it was setup before the loop
                mHeadsetStateMachine.sendMessage(HeadsetStateMachine.DISCONNECT_AUDIO, mTestDevice);
                // No new broadcast due to lack of AUDIO_DISCONNECTING intent variable
                verify(mHeadsetService, after(ASYNC_CALL_TIMEOUT_MILLIS)
                        .times(numBroadcastsSent)).sendBroadcastAsUser(
                        any(Intent.class), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                        any(Bundle.class));
                Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                        IsInstanceOf.instanceOf(HeadsetStateMachine.AudioDisconnecting.class));
                if (i == MAX_RETRY_DISCONNECT_AUDIO) {
                    // Increment twice numBroadcastsSent as DISCONNECT message is added on max retry
                    numBroadcastsSent += 2;
                } else {
                    numBroadcastsSent++;
                }
            }
            verify(mHeadsetService, timeout(CONNECT_TIMEOUT_TEST_WAIT_MILLIS).times(
                    numBroadcastsSent)).sendBroadcastAsUser(mIntentArgument.capture(),
                    eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT), any(Bundle.class));
            if (i < MAX_RETRY_DISCONNECT_AUDIO) { // Test if state is AudioOn before max retry
                HeadsetTestUtils.verifyAudioStateBroadcast(mTestDevice,
                        BluetoothHeadset.STATE_AUDIO_CONNECTED,
                        BluetoothHeadset.STATE_AUDIO_CONNECTED,
                        mIntentArgument.getValue());
                Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                        IsInstanceOf.instanceOf(HeadsetStateMachine.AudioOn.class));
            } else { // Max retry count reached, test Disconnecting state
                HeadsetTestUtils.verifyConnectionStateBroadcast(mTestDevice,
                        BluetoothHeadset.STATE_DISCONNECTING,
                        BluetoothHeadset.STATE_CONNECTED,
                        mIntentArgument.getValue());
                Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                        IsInstanceOf.instanceOf(HeadsetStateMachine.Disconnecting.class));
            }
        }
    }

    /**
     * Test state transition from AudioDisconnecting to Connected state via
     * Stack.AUDIO_DISCONNECTED message
     */
    @Test
    public void testStateTransition_AudioDisconnectingToConnected_StackAudioDisconnected() {
        int numBroadcastsSent = setUpAudioDisconnectingState();
        // Send Stack.AUDIO_DISCONNECTED message
        numBroadcastsSent++;
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_AUDIO_STATE_CHANGED,
                        HeadsetHalConstants.AUDIO_STATE_DISCONNECTED, mTestDevice));
        verify(mHeadsetService,
                timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(numBroadcastsSent)).sendBroadcastAsUser(
                mIntentArgument.capture(), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        HeadsetTestUtils.verifyAudioStateBroadcast(mTestDevice,
                BluetoothHeadset.STATE_AUDIO_DISCONNECTED, BluetoothHeadset.STATE_AUDIO_CONNECTED,
                mIntentArgument.getValue());
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.Connected.class));
    }

    /**
     * Test state transition from AudioDisconnecting to AudioOn state via
     * Stack.AUDIO_CONNECTED message
     */
    @Test
    public void testStateTransition_AudioDisconnectingToAudioOn_StackAudioConnected() {
        int numBroadcastsSent = setUpAudioDisconnectingState();
        // Send Stack.AUDIO_CONNECTED message
        numBroadcastsSent++;
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_AUDIO_STATE_CHANGED,
                        HeadsetHalConstants.AUDIO_STATE_CONNECTED, mTestDevice));
        verify(mHeadsetService,
                after(ASYNC_CALL_TIMEOUT_MILLIS).times(numBroadcastsSent)).sendBroadcastAsUser(
                mIntentArgument.capture(), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        HeadsetTestUtils.verifyAudioStateBroadcast(mTestDevice,
                BluetoothHeadset.STATE_AUDIO_CONNECTED, BluetoothHeadset.STATE_AUDIO_CONNECTED,
                mIntentArgument.getValue());
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.AudioOn.class));
    }

    /**
     * Test state transition from AudioDisconnecting to Disconnecting state via
     * Stack.DISCONNECTING message
     */
    @Test
    public void testStateTransition_AudioDisconnectingToDisconnecting_StackDisconnecting() {
        int numBroadcastsSent = setUpAudioDisconnectingState();
        // Send StackEvent.DISCONNECTING message
        numBroadcastsSent += 2;
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED,
                        HeadsetHalConstants.CONNECTION_STATE_DISCONNECTING, mTestDevice));
        verify(mHeadsetService,
                timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(numBroadcastsSent)).sendBroadcastAsUser(
                mIntentArgument.capture(), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        HeadsetTestUtils.verifyAudioStateBroadcast(mTestDevice,
                BluetoothHeadset.STATE_AUDIO_DISCONNECTED, BluetoothHeadset.STATE_AUDIO_CONNECTED,
                mIntentArgument.getAllValues().get(mIntentArgument.getAllValues().size() - 2));
        HeadsetTestUtils.verifyConnectionStateBroadcast(mTestDevice,
                BluetoothProfile.STATE_DISCONNECTING, BluetoothProfile.STATE_CONNECTED,
                mIntentArgument.getAllValues().get(mIntentArgument.getAllValues().size() - 1));
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.Disconnecting.class));
    }

    /**
     * Test state transition from AudioDisconnecting to Disconnecting state via
     * Stack.DISCONNECTED message
     */
    @Test
    public void testStateTransition_AudioDisconnectingToDisconnected_StackDisconnected() {
        int numBroadcastsSent = setUpAudioDisconnectingState();
        // Send StackEvent.DISCONNECTED message
        numBroadcastsSent += 2;
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED,
                        HeadsetHalConstants.CONNECTION_STATE_DISCONNECTED, mTestDevice));
        verify(mHeadsetService,
                timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(numBroadcastsSent)).sendBroadcastAsUser(
                mIntentArgument.capture(), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        HeadsetTestUtils.verifyAudioStateBroadcast(mTestDevice,
                BluetoothHeadset.STATE_AUDIO_DISCONNECTED, BluetoothHeadset.STATE_AUDIO_CONNECTED,
                mIntentArgument.getAllValues().get(mIntentArgument.getAllValues().size() - 2));
        HeadsetTestUtils.verifyConnectionStateBroadcast(mTestDevice,
                BluetoothProfile.STATE_DISCONNECTED, BluetoothProfile.STATE_CONNECTED,
                mIntentArgument.getAllValues().get(mIntentArgument.getAllValues().size() - 1));
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.Disconnected.class));
    }

    /**
     * A test to verify that we correctly subscribe to phone state updates for service and signal
     * strength information and further updates via AT+BIA command results in update
     */
    @Test
    public void testAtBiaEvent_initialSubscriptionWithUpdates() {
        setUpConnectedState();
        verify(mPhoneState).listenForPhoneState(mTestDevice, PhoneStateListener.LISTEN_SERVICE_STATE
                | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_BIA,
                        new HeadsetAgIndicatorEnableState(true, true, false, false), mTestDevice));
        verify(mPhoneState, timeout(ASYNC_CALL_TIMEOUT_MILLIS)).listenForPhoneState(mTestDevice,
                PhoneStateListener.LISTEN_SERVICE_STATE);
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_BIA,
                        new HeadsetAgIndicatorEnableState(false, true, true, false), mTestDevice));
        verify(mPhoneState, timeout(ASYNC_CALL_TIMEOUT_MILLIS)).listenForPhoneState(mTestDevice,
                PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_BIA,
                        new HeadsetAgIndicatorEnableState(false, true, false, false), mTestDevice));
        verify(mPhoneState, timeout(ASYNC_CALL_TIMEOUT_MILLIS)).listenForPhoneState(mTestDevice,
                PhoneStateListener.LISTEN_NONE);
    }

    /**
     * A test to verify that we correctly handles key pressed event from a HSP headset
     */
    @Test
    public void testKeyPressedEventWhenIdleAndAudioOff_dialCall() {
        setUpConnectedState();
        Cursor cursor = mock(Cursor.class);
        when(cursor.getCount()).thenReturn(1);
        when(cursor.moveToNext()).thenReturn(true);
        int magicNumber = 42;
        when(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)).thenReturn(magicNumber);
        when(cursor.getString(magicNumber)).thenReturn(TEST_PHONE_NUMBER);
        MockContentProvider mockContentProvider = new MockContentProvider() {
            @Override
            public Cursor query(Uri uri, String[] projection, Bundle queryArgs,
                        CancellationSignal cancellationSignal) {
                if (uri == null || !uri.equals(CallLog.Calls.CONTENT_URI)) {
                    return null;
                }
                if (projection == null || (projection.length == 0) || !projection[0].equals(
                        CallLog.Calls.NUMBER)) {
                    return null;
                }
                if (queryArgs == null
                        || !queryArgs.getString(ContentResolver.QUERY_ARG_SQL_SELECTION).equals(
                                Calls.TYPE + "=" + Calls.OUTGOING_TYPE)
                        || !queryArgs.getString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER).equals(
                                Calls.DEFAULT_SORT_ORDER)
                        || queryArgs.getInt(ContentResolver.QUERY_ARG_LIMIT) != 1) {
                    return null;
                }
                if (cancellationSignal != null) {
                    return null;
                }
                return cursor;
            }
        };
        mMockContentResolver.addProvider(CallLog.AUTHORITY, mockContentProvider);
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_KEY_PRESSED, mTestDevice));
        verify(mHeadsetService, timeout(ASYNC_CALL_TIMEOUT_MILLIS)).dialOutgoingCall(mTestDevice,
                TEST_PHONE_NUMBER);
    }

    /**
     * A test to verify that we correctly handles key pressed event from a HSP headset
     */
    @Test
    public void testKeyPressedEventDuringRinging_answerCall() {
        setUpConnectedState();
        when(mSystemInterface.isRinging()).thenReturn(true);
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_KEY_PRESSED, mTestDevice));
        verify(mSystemInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS)).answerCall(mTestDevice);
    }

    /**
     * A test to verify that we correctly handles key pressed event from a HSP headset
     */
    @Test
    public void testKeyPressedEventInCallButAudioOff_setActiveDevice() {
        setUpConnectedState();
        when(mSystemInterface.isInCall()).thenReturn(true);
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_KEY_PRESSED, mTestDevice));
        verify(mHeadsetService, timeout(ASYNC_CALL_TIMEOUT_MILLIS)).setActiveDevice(mTestDevice);
    }

    /**
     * A test to verify that we correctly handles key pressed event from a HSP headset
     */
    @Test
    public void testKeyPressedEventInCallAndAudioOn_hangupCall() {
        setUpAudioOnState();
        when(mSystemInterface.isInCall()).thenReturn(true);
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_KEY_PRESSED, mTestDevice));
        verify(mSystemInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS)).hangupCall(mTestDevice);
    }

    /** A test to verify that we correctly send CIND response when a call is in progress */
    @Test
    public void testCindEventWhenCallIsInProgress() {
        mSetFlagsRule.enableFlags(Flags.FLAG_PRETEND_NETWORK_SERVICE);
        when(mPhoneState.getCindService())
                .thenReturn(HeadsetHalConstants.NETWORK_STATE_NOT_AVAILABLE);
        when(mHeadsetService.isVirtualCallStarted()).thenReturn(false);
        when(mPhoneState.getNumActiveCall()).thenReturn(1);

        setUpAudioOnState();

        mHeadsetStateMachine.sendMessage(
                HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_AT_CIND, mTestDevice));
        // wait state machine to process the message
        if (Flags.pretendNetworkService()) {
            verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS))
                    .cindResponse(
                            eq(mTestDevice),
                            eq(HeadsetHalConstants.NETWORK_STATE_AVAILABLE),
                            anyInt(),
                            anyInt(),
                            anyInt(),
                            anyInt(),
                            anyInt(),
                            anyInt());
        } else {
            verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS))
                    .cindResponse(
                            eq(mTestDevice),
                            eq(HeadsetHalConstants.NETWORK_STATE_NOT_AVAILABLE),
                            anyInt(),
                            anyInt(),
                            anyInt(),
                            anyInt(),
                            anyInt(),
                            anyInt());
        }
    }

    /**
     * A test to verify that we correctly handles key pressed event from a HSP headset
     */
    @Test
    public void testKeyPressedEventWhenIdleAndAudioOn_disconnectAudio() {
        setUpAudioOnState();
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_KEY_PRESSED, mTestDevice));
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS)).disconnectAudio(mTestDevice);
    }

    /**
     * A test to verfiy that we correctly handles AT+BIND event with driver safety case from HF
     */
    @Test
    public void testAtBindWithDriverSafetyEventWhenConnecting() {
        setUpConnectingState();

        String atString = "1";
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_BIND, atString, mTestDevice));
        ArgumentCaptor<Intent> intentArgument = ArgumentCaptor.forClass(Intent.class);
        verify(mHeadsetService, timeout(ASYNC_CALL_TIMEOUT_MILLIS)).sendBroadcast(
                intentArgument.capture(), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        verify(mHeadsetService, times(1)).sendBroadcast(any(), any(),
                any());
        Assert.assertEquals(mTestDevice, intentArgument.getValue().getExtra(
                BluetoothDevice.EXTRA_DEVICE, null));
        Assert.assertEquals(HeadsetHalConstants.HF_INDICATOR_ENHANCED_DRIVER_SAFETY,
                intentArgument.getValue().getIntExtra(
                        BluetoothHeadset.EXTRA_HF_INDICATORS_IND_ID, -1));
        Assert.assertEquals(-1, intentArgument.getValue().getIntExtra(
                BluetoothHeadset.EXTRA_HF_INDICATORS_IND_VALUE, -2));
    }

    /**
     * A test to verfiy that we correctly handles AT+BIND event with battery level case from HF
     */
    @Test
    public void testAtBindEventWithBatteryLevelEventWhenConnecting() {
        setUpConnectingState();

        String atString = "2";
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_BIND, atString, mTestDevice));
        ArgumentCaptor<Intent> intentArgument = ArgumentCaptor.forClass(Intent.class);
        verify(mHeadsetService, timeout(ASYNC_CALL_TIMEOUT_MILLIS)).sendBroadcast(
                intentArgument.capture(), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        verify(mHeadsetService, times(1)).sendBroadcast(any(), any(),
                any());
        Assert.assertEquals(mTestDevice, intentArgument.getValue().getExtra(
                BluetoothDevice.EXTRA_DEVICE, null));
        Assert.assertEquals(HeadsetHalConstants.HF_INDICATOR_BATTERY_LEVEL_STATUS,
                intentArgument.getValue().getIntExtra(
                        BluetoothHeadset.EXTRA_HF_INDICATORS_IND_ID, -1));
        Assert.assertEquals(-1, intentArgument.getValue().getIntExtra(
                BluetoothHeadset.EXTRA_HF_INDICATORS_IND_VALUE, -2));
    }

    /**
     * A test to verfiy that we correctly handles AT+BIND event with error case from HF
     */
    @Test
    public void testAtBindEventWithErrorEventWhenConnecting() {
        setUpConnectingState();

        String atString = "err,A,123,,1";
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_BIND, atString, mTestDevice));
        ArgumentCaptor<Intent> intentArgument = ArgumentCaptor.forClass(Intent.class);
        verify(mHeadsetService, timeout(ASYNC_CALL_TIMEOUT_MILLIS)).sendBroadcast(
                intentArgument.capture(), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        verify(mHeadsetService, times(1)).sendBroadcast(any(), any(),
                any());
        Assert.assertEquals(mTestDevice, intentArgument.getValue().getExtra(
                BluetoothDevice.EXTRA_DEVICE, null));
        Assert.assertEquals(HeadsetHalConstants.HF_INDICATOR_ENHANCED_DRIVER_SAFETY,
                intentArgument.getValue().getIntExtra(
                        BluetoothHeadset.EXTRA_HF_INDICATORS_IND_ID, -1));
        Assert.assertEquals(-1, intentArgument.getValue().getIntExtra(
                BluetoothHeadset.EXTRA_HF_INDICATORS_IND_VALUE, -2));
    }

    /**
     * A test to verify that we correctly set AG indicator mask when enter/exit silence mode
     */
    @Test
    public void testSetSilenceDevice() {
        doNothing().when(mPhoneState).listenForPhoneState(any(BluetoothDevice.class), anyInt());
        mHeadsetStateMachine.setSilenceDevice(true);
        mHeadsetStateMachine.setSilenceDevice(false);
        verify(mPhoneState, times(2)).listenForPhoneState(mTestDevice,
                PhoneStateListener.LISTEN_NONE);
    }

    @Test
    public void testBroadcastVendorSpecificEventIntent() {
        mHeadsetStateMachine.broadcastVendorSpecificEventIntent(
                "command", 1, 1, null, mTestDevice);
        verify(mHeadsetService, timeout(ASYNC_CALL_TIMEOUT_MILLIS)).sendBroadcastAsUser(
                mIntentArgument.capture(), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
    }

    @Test
    public void testFindChar_withCharFound() {
        char ch = 's';
        String input = "test";
        int fromIndex = 0;

        Assert.assertEquals(HeadsetStateMachine.findChar(ch, input, fromIndex), 2);
    }

    @Test
    public void testFindChar_withCharNotFound() {
        char ch = 'x';
        String input = "test";
        int fromIndex = 0;

        Assert.assertEquals(HeadsetStateMachine.findChar(ch, input, fromIndex), input.length());
    }

    @Test
    public void testFindChar_withQuotes() {
        char ch = 's';
        String input = "te\"st";
        int fromIndex = 0;

        Assert.assertEquals(HeadsetStateMachine.findChar(ch, input, fromIndex), input.length());
    }

    @Test
    public void testGenerateArgs() {
        String input = "11,notint";
        ArrayList<Object> expected = new ArrayList<Object>();
        expected.add(11);
        expected.add("notint");

        Assert.assertEquals(HeadsetStateMachine.generateArgs(input), expected.toArray());
    }

    @Test
    public void testGetAtCommandType() {
        String atCommand = "start?";
        Assert.assertEquals(mHeadsetStateMachine.getAtCommandType(atCommand),
                AtPhonebook.TYPE_READ);

        atCommand = "start=?";
        Assert.assertEquals(mHeadsetStateMachine.getAtCommandType(atCommand),
                AtPhonebook.TYPE_TEST);

        atCommand = "start=comm";
        Assert.assertEquals(mHeadsetStateMachine.getAtCommandType(atCommand), AtPhonebook.TYPE_SET);

        atCommand = "start!";
        Assert.assertEquals(mHeadsetStateMachine.getAtCommandType(atCommand),
                AtPhonebook.TYPE_UNKNOWN);
    }

    @Test
    public void testParseUnknownAt() {
        String atString = "\"command\"";

        Assert.assertEquals(mHeadsetStateMachine.parseUnknownAt(atString), "\"command\"");
    }

    @Test
    public void testParseUnknownAt_withUnmatchingQuotes() {
        String atString = "\"command";

        Assert.assertEquals(mHeadsetStateMachine.parseUnknownAt(atString), "\"command\"");
    }

    @Test
    public void testParseUnknownAt_withCharOutsideQuotes() {
        String atString = "a\"command\"";

        Assert.assertEquals(mHeadsetStateMachine.parseUnknownAt(atString), "A\"command\"");
    }

    @Ignore("b/265556073")
    @Test
    public void testHandleAccessPermissionResult_withNoChangeInAtCommandResult() {
        when(mIntent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)).thenReturn(null);
        when(mIntent.getAction()).thenReturn(BluetoothDevice.ACTION_CONNECTION_ACCESS_REPLY);
        when(mIntent.getIntExtra(BluetoothDevice.EXTRA_CONNECTION_ACCESS_RESULT,
                BluetoothDevice.CONNECTION_ACCESS_NO))
                .thenReturn(BluetoothDevice.CONNECTION_ACCESS_NO);
        when(mIntent.getBooleanExtra(BluetoothDevice.EXTRA_ALWAYS_ALLOWED, false)).thenReturn(true);
        mHeadsetStateMachine.mPhonebook.setCheckingAccessPermission(true);

        mHeadsetStateMachine.handleAccessPermissionResult(mIntent);

        verify(mNativeInterface).atResponseCode(null, 0, 0);
    }

    @Test
    public void testProcessAtBievCommand() {
        mHeadsetStateMachine.processAtBiev(1, 1, mTestDevice);

        verify(mHeadsetService, timeout(ASYNC_CALL_TIMEOUT_MILLIS)).sendBroadcast(
                mIntentArgument.capture(), eq(BLUETOOTH_CONNECT), any(Bundle.class));
    }

    @Test
    public void testProcessAtChld_withProcessChldTrue() {
        int chld = 1;
        when(mSystemInterface.processChld(chld)).thenReturn(true);

        mHeadsetStateMachine.processAtChld(chld, mTestDevice);

        verify(mNativeInterface).atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_OK, 0);
    }

    @Test
    public void testProcessAtChld_withProcessChldFalse() {
        int chld = 1;
        when(mSystemInterface.processChld(chld)).thenReturn(false);

        mHeadsetStateMachine.processAtChld(chld, mTestDevice);

        verify(mNativeInterface).atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_ERROR,
                0);
    }

    @Test
    public void testProcessAtClcc_withVirtualCallStarted() {
        when(mHeadsetService.isVirtualCallStarted()).thenReturn(true);
        when(mSystemInterface.getSubscriberNumber()).thenReturn(null);

        mHeadsetStateMachine.processAtClcc(mTestDevice);

        verify(mNativeInterface).clccResponse(mTestDevice, 0, 0, 0, 0, false, "", 0);
    }

    @Test
    public void testProcessAtClcc_withVirtualCallNotStarted() {
        when(mHeadsetService.isVirtualCallStarted()).thenReturn(false);
        when(mSystemInterface.listCurrentCalls()).thenReturn(false);

        mHeadsetStateMachine.processAtClcc(mTestDevice);

        verify(mNativeInterface).clccResponse(mTestDevice, 0, 0, 0, 0, false, "", 0);
    }

    @Test
    public void testProcessAtCops() {
        ServiceState serviceState = mock(ServiceState.class);
        when(serviceState.getOperatorAlphaLong()).thenReturn("");
        when(serviceState.getOperatorAlphaShort()).thenReturn("");
        HeadsetPhoneState phoneState = mock(HeadsetPhoneState.class);
        when(phoneState.getServiceState()).thenReturn(serviceState);
        when(mSystemInterface.getHeadsetPhoneState()).thenReturn(phoneState);
        when(mSystemInterface.isInCall()).thenReturn(true);
        when(mSystemInterface.getNetworkOperator()).thenReturn(null);

        mHeadsetStateMachine.processAtCops(mTestDevice);

        verify(mNativeInterface).copsResponse(mTestDevice, "");
    }

    @Test
    public void testProcessAtCpbr() {
        String atString = "command=ERR";
        int type = AtPhonebook.TYPE_SET;

        mHeadsetStateMachine.processAtCpbr(atString, type, mTestDevice);

        verify(mNativeInterface).atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_ERROR,
                BluetoothCmeError.TEXT_HAS_INVALID_CHARS);
    }

    @Test
    public void testProcessAtCpbs() {
        String atString = "command=ERR";
        int type = AtPhonebook.TYPE_SET;

        mHeadsetStateMachine.processAtCpbs(atString, type, mTestDevice);

        verify(mNativeInterface).atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_ERROR,
                BluetoothCmeError.OPERATION_NOT_ALLOWED);
    }

    @Test
    public void testProcessAtCscs() {
        String atString = "command=GSM";
        int type = AtPhonebook.TYPE_SET;

        mHeadsetStateMachine.processAtCscs(atString, type, mTestDevice);

        verify(mNativeInterface).atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_OK,
                -1);
    }

    @Test
    public void testProcessAtXapl() {
        Object[] args = new Object[2];
        args[0] = "1-12-3";
        args[1] = 1;

        mHeadsetStateMachine.processAtXapl(args, mTestDevice);

        verify(mNativeInterface).atResponseString(mTestDevice, "+XAPL=iPhone," + String.valueOf(2));
    }

    @Test
    public void testProcessSendVendorSpecificResultCode() {
        HeadsetVendorSpecificResultCode resultCode = new HeadsetVendorSpecificResultCode(
                mTestDevice, "command", "arg");

        mHeadsetStateMachine.processSendVendorSpecificResultCode(resultCode);

        verify(mNativeInterface).atResponseString(mTestDevice, "command" + ": " + "arg");
    }

    @Test
    public void testProcessSubscriberNumberRequest_withSubscriberNumberNull() {
        when(mSystemInterface.getSubscriberNumber()).thenReturn(null);

        mHeadsetStateMachine.processSubscriberNumberRequest(mTestDevice);

        verify(mNativeInterface).atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_OK, 0);
    }

    @Test
    public void testProcessSubscriberNumberRequest_withSubscriberNumberNotNull() {
        String number = "1111";
        when(mSystemInterface.getSubscriberNumber()).thenReturn(number);

        mHeadsetStateMachine.processSubscriberNumberRequest(mTestDevice);

        verify(mNativeInterface).atResponseString(mTestDevice,
                "+CNUM: ,\"" + number + "\"," + PhoneNumberUtils.toaFromString(number) + ",,4");
        verify(mNativeInterface).atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_OK, 0);
    }

    @Test
    public void testProcessUnknownAt() {
        String atString = "+CSCS=invalid";
        mHeadsetStateMachine.processUnknownAt(atString, mTestDevice);
        verify(mNativeInterface).atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_ERROR,
                BluetoothCmeError.OPERATION_NOT_SUPPORTED);
        Mockito.clearInvocations(mNativeInterface);

        atString = "+CPBS=";
        mHeadsetStateMachine.processUnknownAt(atString, mTestDevice);
        verify(mNativeInterface).atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_ERROR,
                BluetoothCmeError.OPERATION_NOT_SUPPORTED);

        atString = "+CPBR=ERR";
        mHeadsetStateMachine.processUnknownAt(atString, mTestDevice);
        verify(mNativeInterface).atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_ERROR,
                BluetoothCmeError.TEXT_HAS_INVALID_CHARS);

        atString = "inval=";
        mHeadsetStateMachine.processUnknownAt(atString, mTestDevice);
        verify(mNativeInterface).atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_ERROR,
                0);
    }

    @Test
    public void testProcessVendorSpecificAt_withNonExceptedNoEqualSignCommand() {
        String atString = "invalid_command";

        mHeadsetStateMachine.processVendorSpecificAt(atString, mTestDevice);

        verify(mNativeInterface).atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_ERROR,
                0);
    }

    @Test
    public void testProcessVendorSpecificAt_withUnsupportedCommand() {
        String atString = "invalid_command=";

        mHeadsetStateMachine.processVendorSpecificAt(atString, mTestDevice);

        verify(mNativeInterface).atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_ERROR,
                0);
    }

    @Test
    public void testProcessVendorSpecificAt_withQuestionMarkArg() {
        String atString = BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_XEVENT + "=?arg";

        mHeadsetStateMachine.processVendorSpecificAt(atString, mTestDevice);

        verify(mNativeInterface).atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_ERROR,
                0);
    }

    @Test
    public void testProcessVendorSpecificAt_withValidCommandAndArg() {
        String atString = BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_XAPL + "=1-12-3,1";

        mHeadsetStateMachine.processVendorSpecificAt(atString, mTestDevice);

        verify(mNativeInterface).atResponseString(mTestDevice, "+XAPL=iPhone," + "2");
        verify(mNativeInterface).atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_OK, 0);
    }

    @Test
    public void testProcessVendorSpecificAt_withExceptedNoEqualSignCommandCGMI() {
        String atString = BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_CGMI;

        mHeadsetStateMachine.processVendorSpecificAt(atString, mTestDevice);

        verify(mNativeInterface).atResponseString(mTestDevice, Build.MANUFACTURER);
        verify(mNativeInterface).atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_OK, 0);
    }

    @Test
    public void testProcessVendorSpecificAt_withExceptedNoEqualSignCommandCGMM() {
        String atString = BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_CGMM;

        mHeadsetStateMachine.processVendorSpecificAt(atString, mTestDevice);

        verify(mNativeInterface).atResponseString(mTestDevice, Build.MODEL);
        verify(mNativeInterface).atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_OK, 0);
    }

    @Test
    public void testProcessVendorSpecificAt_withExceptedNoEqualSignCommandCGMR() {
        String atString = BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_CGMR;

        mHeadsetStateMachine.processVendorSpecificAt(atString, mTestDevice);

        verify(mNativeInterface)
                .atResponseString(
                        mTestDevice,
                        String.format("%s (%s)", Build.VERSION.RELEASE, Build.VERSION.INCREMENTAL));
        verify(mNativeInterface).atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_OK, 0);
    }

    @Test
    public void testProcessVendorSpecificAt_withExceptedNoEqualSignCommandCGSN() {
        String atString = BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_CGSN;

        mHeadsetStateMachine.processVendorSpecificAt(atString, mTestDevice);

        verify(mNativeInterface).atResponseString(mTestDevice, Build.getSerial());
        verify(mNativeInterface).atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_OK, 0);
    }

    @Test
    public void testProcessVolumeEvent_withVolumeTypeMic() {
        when(mHeadsetService.getActiveDevice()).thenReturn(mTestDevice);

        mHeadsetStateMachine.processVolumeEvent(HeadsetHalConstants.VOLUME_TYPE_MIC, 1);

        Assert.assertEquals(mHeadsetStateMachine.mMicVolume, 1);
    }

    @Test
    public void testProcessVolumeEvent_withVolumeTypeSpk() {
        when(mHeadsetService.getActiveDevice()).thenReturn(mTestDevice);
        AudioManager mockAudioManager = mock(AudioManager.class);
        when(mockAudioManager.getStreamVolume(AudioManager.STREAM_BLUETOOTH_SCO)).thenReturn(1);
        when(mSystemInterface.getAudioManager()).thenReturn(mockAudioManager);

        mHeadsetStateMachine.processVolumeEvent(HeadsetHalConstants.VOLUME_TYPE_SPK, 2);

        Assert.assertEquals(mHeadsetStateMachine.mSpeakerVolume, 2);
        verify(mockAudioManager).setStreamVolume(AudioManager.STREAM_BLUETOOTH_SCO, 2, 0);
    }

    @Test
    public void testDump_doesNotCrash() {
        StringBuilder sb = new StringBuilder();

        mHeadsetStateMachine.dump(sb);
    }

    /**
     * A test to validate received Android AT commands and processing
     */
    @Test
    public void testCheckAndProcessAndroidAt() {
        // Commands that will be handled
        int counter_ok = 0;
        int counter_error = 0;
        Assert.assertTrue(mHeadsetStateMachine.checkAndProcessAndroidAt(
            "+ANDROID=?" , mTestDevice));
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(++counter_ok))
                .atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_OK, 0);
        Assert.assertTrue(mHeadsetStateMachine.checkAndProcessAndroidAt(
            "+ANDROID=SINKAUDIOPOLICY,1,1,1" , mTestDevice));
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(++counter_ok))
                .atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_OK, 0);
        Assert.assertTrue(mHeadsetStateMachine.checkAndProcessAndroidAt(
            "+ANDROID=SINKAUDIOPOLICY,100,100,100" , mTestDevice));
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(++counter_ok))
                .atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_OK, 0);
        Assert.assertTrue(mHeadsetStateMachine.checkAndProcessAndroidAt(
            "+ANDROID=SINKAUDIOPOLICY,1,2,3,4,5" , mTestDevice));
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(++counter_error))
                .atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_ERROR, 0);
        Assert.assertTrue(mHeadsetStateMachine.checkAndProcessAndroidAt("+ANDROID=1", mTestDevice));
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(++counter_error))
                .atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_ERROR, 0);
        Assert.assertTrue(
                mHeadsetStateMachine.checkAndProcessAndroidAt("+ANDROID=1,2", mTestDevice));
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(++counter_error))
                .atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_ERROR, 0);
        Assert.assertTrue(
                mHeadsetStateMachine.checkAndProcessAndroidAt("+ANDROID=1,2,3", mTestDevice));
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(++counter_error))
                .atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_ERROR, 0);
        Assert.assertTrue(
                mHeadsetStateMachine.checkAndProcessAndroidAt(
                        "+ANDROID=1,2,3,4,5,6,7", mTestDevice));
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(++counter_error))
                .atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_ERROR, 0);

        // Commands with correct format but will not be handled
        Assert.assertFalse(mHeadsetStateMachine.checkAndProcessAndroidAt(
            "+ANDROID=" , mTestDevice));
        Assert.assertFalse(mHeadsetStateMachine.checkAndProcessAndroidAt(
            "+ANDROID: PROBE,1,\"`AB\"" , mTestDevice));
        Assert.assertFalse(mHeadsetStateMachine.checkAndProcessAndroidAt(
            "+ANDROID= PROBE,1,\"`AB\"" , mTestDevice));
        Assert.assertFalse(mHeadsetStateMachine.checkAndProcessAndroidAt(
            "AT+ANDROID=PROBE,1,1,\"PQGHRSBCTU__\"" , mTestDevice));

        // Incorrect format AT command
        Assert.assertFalse(mHeadsetStateMachine.checkAndProcessAndroidAt(
            "RANDOM FORMAT" , mTestDevice));

        // Check no any AT result was sent for the failed ones
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(counter_ok))
                .atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_OK, 0);
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(counter_error))
                .atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_ERROR, 0);
    }

    @Test
    public void testCheckAndProcessAndroidAt_replyAndroidAtFeatureRequest() {
        // Commands that will be handled
        Assert.assertTrue(mHeadsetStateMachine.checkAndProcessAndroidAt(
            "+ANDROID=?" , mTestDevice));
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS)).atResponseString(
                mTestDevice, "+ANDROID: (SINKAUDIOPOLICY)");
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS)).atResponseCode(
                mTestDevice, HeadsetHalConstants.AT_RESPONSE_OK, 0);
    }

    /**
     * A end to end test to validate received Android AT commands and processing
     */
    @Test
    public void testCehckAndProcessAndroidAtFromStateMachine() {
        // setAudioPolicyMetadata is invoked in HeadsetStateMachine.init() so start from 1
        int expectCallTimes = 1;

        // setup Audio Policy Feature
        setUpConnectedState();

        setUpAudioPolicy();
        // receive and set android policy
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_UNKNOWN_AT,
                        "+ANDROID=SINKAUDIOPOLICY,1,1,1", mTestDevice));
        expectCallTimes++;
        verify(mDatabaseManager, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(expectCallTimes))
                .setAudioPolicyMetadata(anyObject(), anyObject());

        // receive and not set android policy
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_UNKNOWN_AT,
                        "AT+ANDROID=PROBE,1,1,\"PQGHRSBCTU__\"", mTestDevice));
        verify(mDatabaseManager, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(expectCallTimes))
                .setAudioPolicyMetadata(anyObject(), anyObject());
    }

    /**
     * A test to verify whether the sink audio policy command is valid
     */
    @Test
    public void testProcessAndroidAtSinkAudioPolicy() {
        // expected format
        Assert.assertTrue(setSinkAudioPolicyArgs("SINKAUDIOPOLICY,0,0,0", mTestDevice));
        Assert.assertTrue(setSinkAudioPolicyArgs("SINKAUDIOPOLICY,0,0,1", mTestDevice));
        Assert.assertTrue(setSinkAudioPolicyArgs("SINKAUDIOPOLICY,0,1,0", mTestDevice));
        Assert.assertTrue(setSinkAudioPolicyArgs("SINKAUDIOPOLICY,1,0,0", mTestDevice));
        Assert.assertTrue(setSinkAudioPolicyArgs("SINKAUDIOPOLICY,1,1,1", mTestDevice));

        // invalid format
        Assert.assertFalse(setSinkAudioPolicyArgs("SINKAUDIOPOLICY,0", mTestDevice));
        Assert.assertFalse(setSinkAudioPolicyArgs("SINKAUDIOPOLICY,0,0", mTestDevice));
        Assert.assertFalse(setSinkAudioPolicyArgs("SINKAUDIOPOLICY,0,0,0,0", mTestDevice));
        Assert.assertFalse(setSinkAudioPolicyArgs("SINKAUDIOPOLICY,NOT,INT,TYPE", mTestDevice));
        Assert.assertFalse(setSinkAudioPolicyArgs("RANDOM,VALUE-#$%,*(&^", mTestDevice));

        // wrong device
        BluetoothDevice device = mAdapter.getRemoteDevice("01:01:01:01:01:01");
        Assert.assertFalse(setSinkAudioPolicyArgs("SINKAUDIOPOLICY,0,0,0", device));
    }

    /** Test setting audio parameters according to received SWB event. SWB AptX is enabled. */
    @Test
    public void testSetAudioParameters_SwbAptxEnabled() {
        mSetFlagsRule.enableFlags(Flags.FLAG_HFP_CODEC_APTX_VOICE);
        assumeTrue(SystemProperties.getBoolean("bluetooth.hfp.codec_aptx_voice.enabled", false));
        setUpConnectedState();
        mHeadsetStateMachine.sendMessage(
                HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(
                        HeadsetStackEvent.EVENT_TYPE_SWB,
                        HeadsetHalConstants.BTHF_SWB_CODEC_VENDOR_APTX,
                        HeadsetHalConstants.BTHF_SWB_YES,
                        mTestDevice));

        mHeadsetStateMachine.sendMessage(
                HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(
                        HeadsetStackEvent.EVENT_TYPE_AUDIO_STATE_CHANGED,
                        HeadsetHalConstants.AUDIO_STATE_CONNECTED,
                        mTestDevice));
        verifyAudioSystemSetParametersInvocation(false, true);
    }

    /** Test setting audio parameters according to received SWB event. SWB LC3 is enabled. */
    @Test
    public void testSetAudioParameters_SwbLc3Enabled() {
        setUpConnectedState();
        mHeadsetStateMachine.sendMessage(
                HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(
                        HeadsetStackEvent.EVENT_TYPE_SWB,
                        HeadsetHalConstants.BTHF_SWB_CODEC_LC3,
                        HeadsetHalConstants.BTHF_SWB_YES,
                        mTestDevice));

        mHeadsetStateMachine.sendMessage(
                HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(
                        HeadsetStackEvent.EVENT_TYPE_AUDIO_STATE_CHANGED,
                        HeadsetHalConstants.AUDIO_STATE_CONNECTED,
                        mTestDevice));
        verifyAudioSystemSetParametersInvocation(true, false);
    }

    /** Test setting audio parameters according to received SWB event. All SWB disabled. */
    @Test
    public void testSetAudioParameters_SwbDisabled() {
        setUpConnectedState();
        mHeadsetStateMachine.sendMessage(
                HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(
                        HeadsetStackEvent.EVENT_TYPE_SWB,
                        HeadsetHalConstants.BTHF_SWB_CODEC_LC3,
                        HeadsetHalConstants.BTHF_SWB_NO,
                        mTestDevice));

        mHeadsetStateMachine.sendMessage(
                HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(
                        HeadsetStackEvent.EVENT_TYPE_AUDIO_STATE_CHANGED,
                        HeadsetHalConstants.AUDIO_STATE_CONNECTED,
                        mTestDevice));
        verifyAudioSystemSetParametersInvocation(false, false);
    }

    /**
     * verify parameters given to audio system
     *
     * @param lc3Enabled if true check if SWB LC3 was enabled
     * @param aptxEnabled if true check if SWB AptX was enabled
     */
    private void verifyAudioSystemSetParametersInvocation(boolean lc3Enabled, boolean aptxEnabled) {
        verify(mAudioManager, timeout(ASYNC_CALL_TIMEOUT_MILLIS))
                .setParameters(lc3Enabled ? "bt_lc3_swb=on" : "bt_lc3_swb=off");

        if (mHeadsetStateMachine.IS_APTX_SUPPORT_ENABLED) {
            verify(mAudioManager, timeout(ASYNC_CALL_TIMEOUT_MILLIS))
                    .setParameters(aptxEnabled ? "bt_swb=0" : "bt_swb=65535");
        }
    }

    /**
     * set sink audio policy
     * @param arg body of the AT command
     * @return the result from processAndroidAtSinkAudioPolicy
     */
    private boolean setSinkAudioPolicyArgs(String arg, BluetoothDevice device) {
        Object[] args = HeadsetStateMachine.generateArgs(arg);
        return mHeadsetStateMachine.processAndroidAtSinkAudioPolicy(args, device);
    }

    /**
     * Setup Connecting State
     * @return number of times mHeadsetService.sendBroadcastAsUser() has been invoked
     */
    private int setUpConnectingState() {
        // Put test state machine in connecting state
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.CONNECT, mTestDevice);
        verify(mHeadsetService, timeout(ASYNC_CALL_TIMEOUT_MILLIS)).sendBroadcastAsUser(
                mIntentArgument.capture(), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        HeadsetTestUtils.verifyConnectionStateBroadcast(mTestDevice,
                BluetoothProfile.STATE_CONNECTING, BluetoothProfile.STATE_DISCONNECTED,
                mIntentArgument.getValue());
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.Connecting.class));
        return 1;
    }

    /**
     * Setup Connected State
     * @return number of times mHeadsetService.sendBroadcastAsUser() has been invoked
     */
    private int setUpConnectedState() {
        // Put test state machine into connected state
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED,
                        HeadsetHalConstants.CONNECTION_STATE_CONNECTED, mTestDevice));
        verify(mHeadsetService, timeout(ASYNC_CALL_TIMEOUT_MILLIS)).sendBroadcastAsUser(
                mIntentArgument.capture(), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        HeadsetTestUtils.verifyConnectionStateBroadcast(mTestDevice,
                BluetoothProfile.STATE_CONNECTING, BluetoothProfile.STATE_DISCONNECTED,
                mIntentArgument.getValue());
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.Connecting.class));
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED,
                        HeadsetHalConstants.CONNECTION_STATE_SLC_CONNECTED, mTestDevice));
        verify(mHeadsetService, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(2)).sendBroadcastAsUser(
                mIntentArgument.capture(), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        HeadsetTestUtils.verifyConnectionStateBroadcast(mTestDevice,
                BluetoothProfile.STATE_CONNECTED, BluetoothProfile.STATE_CONNECTING,
                mIntentArgument.getValue());
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.Connected.class));
        return 2;
    }

    private int setUpAudioConnectingState() {
        int numBroadcastsSent = setUpConnectedState();
        // Send CONNECT_AUDIO
        numBroadcastsSent++;
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.CONNECT_AUDIO, mTestDevice);
        verify(mHeadsetService,
                timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(numBroadcastsSent)).sendBroadcastAsUser(
                mIntentArgument.capture(), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        HeadsetTestUtils.verifyAudioStateBroadcast(mTestDevice,
                BluetoothHeadset.STATE_AUDIO_CONNECTING, BluetoothHeadset.STATE_AUDIO_DISCONNECTED,
                mIntentArgument.getValue());
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.AudioConnecting.class));
        return numBroadcastsSent;
    }

    private int setUpAudioOnState() {
        int numBroadcastsSent = setUpAudioConnectingState();
        // Send StackEvent.AUDIO_DISCONNECTED message
        numBroadcastsSent++;
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_AUDIO_STATE_CHANGED,
                        HeadsetHalConstants.AUDIO_STATE_CONNECTED, mTestDevice));
        verify(mHeadsetService,
                timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(numBroadcastsSent)).sendBroadcastAsUser(
                mIntentArgument.capture(), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        HeadsetTestUtils.verifyAudioStateBroadcast(mTestDevice,
                BluetoothHeadset.STATE_AUDIO_CONNECTED, BluetoothHeadset.STATE_AUDIO_CONNECTING,
                mIntentArgument.getValue());
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.AudioOn.class));
        return numBroadcastsSent;
    }

    private int setUpAudioDisconnectingState() {
        int numBroadcastsSent = setUpAudioOnState();
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.DISCONNECT_AUDIO, mTestDevice);
        // No new broadcast due to lack of AUDIO_DISCONNECTING intent variable
        verify(mHeadsetService,
                after(ASYNC_CALL_TIMEOUT_MILLIS).times(numBroadcastsSent)).sendBroadcastAsUser(
                any(Intent.class), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.AudioDisconnecting.class));
        return numBroadcastsSent;
    }

    private int setUpDisconnectingState() {
        int numBroadcastsSent = setUpConnectedState();
        // Send DISCONNECT message
        numBroadcastsSent++;
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.DISCONNECT, mTestDevice);
        verify(mHeadsetService,
                timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(numBroadcastsSent)).sendBroadcastAsUser(
                mIntentArgument.capture(), eq(UserHandle.ALL), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        HeadsetTestUtils.verifyConnectionStateBroadcast(mTestDevice,
                BluetoothProfile.STATE_DISCONNECTING, BluetoothProfile.STATE_CONNECTED,
                mIntentArgument.getValue());
        Assert.assertThat(mHeadsetStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(HeadsetStateMachine.Disconnecting.class));
        return numBroadcastsSent;
    }

    private void setUpAudioPolicy() {
        mHeadsetStateMachine.sendMessage(HeadsetStateMachine.STACK_EVENT,
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_UNKNOWN_AT,
                        "+ANDROID=?", mTestDevice));
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS)).atResponseString(
                anyObject(), anyString());
    }
}
