/*
 * Copyright 2018 The Android Open Source Project
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

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSinkAudioPolicy;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.BluetoothUuid;
import android.content.Context;
import android.media.AudioManager;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.os.SystemClock;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.btservice.ActiveDeviceManager;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.RemoteDevices;
import com.android.bluetooth.btservice.SilenceDeviceManager;
import com.android.bluetooth.btservice.storage.DatabaseManager;

import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

/**
 * Tests for {@link HeadsetService}
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class HeadsetServiceTest {
    private static final int MAX_HEADSET_CONNECTIONS = 5;
    private static final ParcelUuid[] FAKE_HEADSET_UUID = {BluetoothUuid.HFP};
    private static final int ASYNC_CALL_TIMEOUT_MILLIS = 250;
    private static final String TEST_PHONE_NUMBER = "1234567890";

    private Context mTargetContext;
    private HeadsetService mHeadsetService;
    private BluetoothAdapter mAdapter;
    private BluetoothDevice mCurrentDevice;
    private final HashMap<BluetoothDevice, HeadsetStateMachine> mStateMachines = new HashMap<>();

    @Spy private HeadsetObjectsFactory mObjectsFactory = HeadsetObjectsFactory.getInstance();
    @Mock private AdapterService mAdapterService;
    @Mock private ActiveDeviceManager mActiveDeviceManager;
    @Mock private SilenceDeviceManager mSilenceDeviceManager;
    @Mock private DatabaseManager mDatabaseManager;
    @Mock private HeadsetSystemInterface mSystemInterface;
    @Mock private HeadsetNativeInterface mNativeInterface;
    @Mock private AudioManager mAudioManager;
    @Mock private HeadsetPhoneState mPhoneState;
    @Mock private RemoteDevices mRemoteDevices;

    @Before
    public void setUp() throws Exception {
        mTargetContext = InstrumentationRegistry.getTargetContext();
        MockitoAnnotations.initMocks(this);
        TestUtils.setAdapterService(mAdapterService);
        // We cannot mock HeadsetObjectsFactory.getInstance() with Mockito.
        // Hence we need to use reflection to call a private method to
        // initialize properly the HeadsetObjectsFactory.sInstance field.
        Method method = HeadsetObjectsFactory.class.getDeclaredMethod("setInstanceForTesting",
                HeadsetObjectsFactory.class);
        method.setAccessible(true);
        method.invoke(null, mObjectsFactory);
        doReturn(MAX_HEADSET_CONNECTIONS).when(mAdapterService).getMaxConnectedAudioDevices();
        doReturn(new ParcelUuid[]{BluetoothUuid.HFP}).when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));
        // This line must be called to make sure relevant objects are initialized properly
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        // Mock methods in AdapterService
        doReturn(FAKE_HEADSET_UUID).when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));
        doReturn(BluetoothDevice.BOND_BONDED).when(mAdapterService)
                .getBondState(any(BluetoothDevice.class));
        doReturn(mActiveDeviceManager).when(mAdapterService).getActiveDeviceManager();
        doReturn(mSilenceDeviceManager).when(mAdapterService).getSilenceDeviceManager();
        doReturn(mDatabaseManager).when(mAdapterService).getDatabase();
        doReturn(mRemoteDevices).when(mAdapterService).getRemoteDevices();
        doAnswer(invocation -> {
            Set<BluetoothDevice> keys = mStateMachines.keySet();
            return keys.toArray(new BluetoothDevice[keys.size()]);
        }).when(mAdapterService).getBondedDevices();
        doReturn(new BluetoothSinkAudioPolicy.Builder().build()).when(mAdapterService)
                .getRequestedAudioPolicyAsSink(any(BluetoothDevice.class));
        // Mock system interface
        doNothing().when(mSystemInterface).stop();
        when(mSystemInterface.getHeadsetPhoneState()).thenReturn(mPhoneState);
        when(mSystemInterface.getAudioManager()).thenReturn(mAudioManager);
        when(mSystemInterface.isCallIdle()).thenReturn(true, false, true, false);
        // Mock methods in HeadsetNativeInterface
        doNothing().when(mNativeInterface).init(anyInt(), anyBoolean());
        doNothing().when(mNativeInterface).cleanup();
        doReturn(true).when(mNativeInterface).connectHfp(any(BluetoothDevice.class));
        doReturn(true).when(mNativeInterface).disconnectHfp(any(BluetoothDevice.class));
        doReturn(true).when(mNativeInterface).connectAudio(any(BluetoothDevice.class));
        doReturn(true).when(mNativeInterface).disconnectAudio(any(BluetoothDevice.class));
        doReturn(true).when(mNativeInterface).setActiveDevice(any(BluetoothDevice.class));
        doReturn(true).when(mNativeInterface).sendBsir(any(BluetoothDevice.class), anyBoolean());
        // Mock methods in HeadsetObjectsFactory
        doAnswer(invocation -> {
            Assert.assertNotNull(mCurrentDevice);
            final HeadsetStateMachine stateMachine = mock(HeadsetStateMachine.class);
            doReturn(BluetoothProfile.STATE_DISCONNECTED).when(stateMachine).getConnectionState();
            doReturn(BluetoothHeadset.STATE_AUDIO_DISCONNECTED).when(stateMachine).getAudioState();
            mStateMachines.put(mCurrentDevice, stateMachine);
            return stateMachine;
        }).when(mObjectsFactory).makeStateMachine(any(), any(), any(), any(), any(), any());
        doReturn(mSystemInterface).when(mObjectsFactory).makeSystemInterface(any());
        doReturn(mNativeInterface).when(mObjectsFactory).getNativeInterface();
        HeadsetNativeInterface.setInstance(mNativeInterface);
        mHeadsetService = new HeadsetService(mTargetContext);
        mHeadsetService.start();
        mHeadsetService.setAvailable(true);
        verify(mObjectsFactory).makeSystemInterface(mHeadsetService);
        verify(mObjectsFactory).getNativeInterface();
        mHeadsetService.setForceScoAudio(true);
    }

    @After
    public void tearDown() throws Exception {
        mHeadsetService.stop();
        HeadsetNativeInterface.setInstance(null);
        mHeadsetService = HeadsetService.getHeadsetService();
        Assert.assertNull(mHeadsetService);
        mStateMachines.clear();
        mCurrentDevice = null;
        Method method = HeadsetObjectsFactory.class.getDeclaredMethod("setInstanceForTesting",
                HeadsetObjectsFactory.class);
        method.setAccessible(true);
        method.invoke(null, (HeadsetObjectsFactory) null);
        TestUtils.clearAdapterService(mAdapterService);
    }

    /**
     * Test to verify that HeadsetService can be successfully started
     */
    @Test
    public void testGetHeadsetService() {
        Assert.assertEquals(mHeadsetService, HeadsetService.getHeadsetService());
        // Verify default connection and audio states
        mCurrentDevice = TestUtils.getTestDevice(mAdapter, 0);
        Assert.assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mHeadsetService.getConnectionState(mCurrentDevice));
        Assert.assertEquals(BluetoothHeadset.STATE_AUDIO_DISCONNECTED,
                mHeadsetService.getAudioState(mCurrentDevice));
    }

    /**
     *  Test okToAcceptConnection method using various test cases
     */
    @Test
    public void testOkToAcceptConnection() {
        mCurrentDevice = TestUtils.getTestDevice(mAdapter, 0);
        int badPriorityValue = 1024;
        int badBondState = 42;
        testOkToAcceptConnectionCase(mCurrentDevice, BluetoothDevice.BOND_NONE,
                BluetoothProfile.CONNECTION_POLICY_UNKNOWN, false);
        testOkToAcceptConnectionCase(mCurrentDevice, BluetoothDevice.BOND_NONE,
                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN, false);
        testOkToAcceptConnectionCase(mCurrentDevice, BluetoothDevice.BOND_NONE,
                BluetoothProfile.CONNECTION_POLICY_ALLOWED, false);
        testOkToAcceptConnectionCase(mCurrentDevice, BluetoothDevice.BOND_NONE, badPriorityValue,
                false);
        testOkToAcceptConnectionCase(mCurrentDevice, BluetoothDevice.BOND_BONDING,
                BluetoothProfile.CONNECTION_POLICY_UNKNOWN, false);
        testOkToAcceptConnectionCase(mCurrentDevice, BluetoothDevice.BOND_BONDING,
                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN, false);
        testOkToAcceptConnectionCase(mCurrentDevice, BluetoothDevice.BOND_BONDING,
                BluetoothProfile.CONNECTION_POLICY_ALLOWED, false);
        testOkToAcceptConnectionCase(mCurrentDevice, BluetoothDevice.BOND_BONDING, badPriorityValue,
                false);
        testOkToAcceptConnectionCase(mCurrentDevice, BluetoothDevice.BOND_BONDED,
                BluetoothProfile.CONNECTION_POLICY_UNKNOWN, true);
        testOkToAcceptConnectionCase(mCurrentDevice, BluetoothDevice.BOND_BONDED,
                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN, false);
        testOkToAcceptConnectionCase(mCurrentDevice, BluetoothDevice.BOND_BONDED,
                BluetoothProfile.CONNECTION_POLICY_ALLOWED, true);
        testOkToAcceptConnectionCase(mCurrentDevice, BluetoothDevice.BOND_BONDED, badPriorityValue,
                false);
        testOkToAcceptConnectionCase(mCurrentDevice, badBondState,
                BluetoothProfile.CONNECTION_POLICY_UNKNOWN, false);
        testOkToAcceptConnectionCase(mCurrentDevice, badBondState,
                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN, false);
        testOkToAcceptConnectionCase(mCurrentDevice, badBondState,
                BluetoothProfile.CONNECTION_POLICY_ALLOWED, false);
        testOkToAcceptConnectionCase(mCurrentDevice, badBondState, badPriorityValue, false);
    }

    /**
     * Test to verify that {@link HeadsetService#connect(BluetoothDevice)} returns true when the
     * device was not connected and number of connected devices is less than
     * {@link #MAX_HEADSET_CONNECTIONS}
     */
    @Test
    public void testConnectDevice_connectDeviceBelowLimit() {
        when(mDatabaseManager.getProfileConnectionPolicy(any(BluetoothDevice.class),
                eq(BluetoothProfile.HEADSET)))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        mCurrentDevice = TestUtils.getTestDevice(mAdapter, 0);
        Assert.assertTrue(mHeadsetService.connect(mCurrentDevice));
        verify(mObjectsFactory).makeStateMachine(mCurrentDevice,
                mHeadsetService.getStateMachinesThreadLooper(), mHeadsetService, mAdapterService,
                mNativeInterface, mSystemInterface);
        verify(mStateMachines.get(mCurrentDevice)).sendMessage(HeadsetStateMachine.CONNECT,
                mCurrentDevice);
        when(mStateMachines.get(mCurrentDevice).getDevice()).thenReturn(mCurrentDevice);
        when(mStateMachines.get(mCurrentDevice).getConnectionState()).thenReturn(
                BluetoothProfile.STATE_CONNECTING);
        Assert.assertEquals(BluetoothProfile.STATE_CONNECTING,
                mHeadsetService.getConnectionState(mCurrentDevice));
        when(mStateMachines.get(mCurrentDevice).getConnectionState()).thenReturn(
                BluetoothProfile.STATE_CONNECTED);
        Assert.assertEquals(BluetoothProfile.STATE_CONNECTED,
                mHeadsetService.getConnectionState(mCurrentDevice));
        Assert.assertEquals(Collections.singletonList(mCurrentDevice),
                mHeadsetService.getConnectedDevices());
        // 2nd connection attempt will fail
        Assert.assertFalse(mHeadsetService.connect(mCurrentDevice));
        // Verify makeStateMachine is only called once
        verify(mObjectsFactory).makeStateMachine(any(), any(), any(), any(), any(), any());
        // Verify CONNECT is only sent once
        verify(mStateMachines.get(mCurrentDevice)).sendMessage(eq(HeadsetStateMachine.CONNECT),
                any());
    }

    /**
     * Test that {@link HeadsetService#messageFromNative(HeadsetStackEvent)} will send correct
     * message to the underlying state machine
     */
    @Test
    public void testMessageFromNative_deviceConnected() {
        mCurrentDevice = TestUtils.getTestDevice(mAdapter, 0);
        // Test connect from native
        HeadsetStackEvent connectedEvent =
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED,
                        HeadsetHalConstants.CONNECTION_STATE_CONNECTED, mCurrentDevice);
        mHeadsetService.messageFromNative(connectedEvent);
        verify(mObjectsFactory).makeStateMachine(mCurrentDevice,
                mHeadsetService.getStateMachinesThreadLooper(), mHeadsetService, mAdapterService,
                mNativeInterface, mSystemInterface);
        verify(mStateMachines.get(mCurrentDevice)).sendMessage(HeadsetStateMachine.STACK_EVENT,
                connectedEvent);
        when(mStateMachines.get(mCurrentDevice).getDevice()).thenReturn(mCurrentDevice);
        when(mStateMachines.get(mCurrentDevice).getConnectionState()).thenReturn(
                BluetoothProfile.STATE_CONNECTED);
        Assert.assertEquals(BluetoothProfile.STATE_CONNECTED,
                mHeadsetService.getConnectionState(mCurrentDevice));
        Assert.assertEquals(Collections.singletonList(mCurrentDevice),
                mHeadsetService.getConnectedDevices());
        // Test disconnect from native
        HeadsetStackEvent disconnectEvent =
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED,
                        HeadsetHalConstants.CONNECTION_STATE_DISCONNECTED, mCurrentDevice);
        mHeadsetService.messageFromNative(disconnectEvent);
        verify(mStateMachines.get(mCurrentDevice)).sendMessage(HeadsetStateMachine.STACK_EVENT,
                disconnectEvent);
        when(mStateMachines.get(mCurrentDevice).getConnectionState()).thenReturn(
                BluetoothProfile.STATE_DISCONNECTED);
        Assert.assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mHeadsetService.getConnectionState(mCurrentDevice));
        Assert.assertEquals(Collections.EMPTY_LIST, mHeadsetService.getConnectedDevices());
    }

    /**
     * Stack connection event to {@link HeadsetHalConstants#CONNECTION_STATE_CONNECTING} should
     * create new state machine
     */
    @Test
    public void testMessageFromNative_deviceConnectingUnknown() {
        mCurrentDevice = TestUtils.getTestDevice(mAdapter, 0);
        HeadsetStackEvent connectingEvent =
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED,
                        HeadsetHalConstants.CONNECTION_STATE_CONNECTING, mCurrentDevice);
        mHeadsetService.messageFromNative(connectingEvent);
        verify(mObjectsFactory).makeStateMachine(mCurrentDevice,
                mHeadsetService.getStateMachinesThreadLooper(), mHeadsetService, mAdapterService,
                mNativeInterface, mSystemInterface);
        verify(mStateMachines.get(mCurrentDevice)).sendMessage(HeadsetStateMachine.STACK_EVENT,
                connectingEvent);
    }

    /**
     * Stack connection event to {@link HeadsetHalConstants#CONNECTION_STATE_DISCONNECTED} should
     * crash by throwing {@link IllegalStateException} if the device is unknown
     */
    @Test
    public void testMessageFromNative_deviceDisconnectedUnknown() {
        mCurrentDevice = TestUtils.getTestDevice(mAdapter, 0);
        HeadsetStackEvent connectingEvent =
                new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED,
                        HeadsetHalConstants.CONNECTION_STATE_DISCONNECTED, mCurrentDevice);
        try {
            mHeadsetService.messageFromNative(connectingEvent);
            Assert.fail("Expect an IllegalStateException");
        } catch (IllegalStateException exception) {
            // Do nothing
        }
        verifyNoMoreInteractions(mObjectsFactory);
    }

    /**
     * Test to verify that {@link HeadsetService#connect(BluetoothDevice)} fails after
     * {@link #MAX_HEADSET_CONNECTIONS} connection requests
     */
    @Test
    public void testConnectDevice_connectDeviceAboveLimit() {
        ArrayList<BluetoothDevice> connectedDevices = new ArrayList<>();
        when(mDatabaseManager.getProfileConnectionPolicy(any(BluetoothDevice.class),
                eq(BluetoothProfile.HEADSET)))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        for (int i = 0; i < MAX_HEADSET_CONNECTIONS; ++i) {
            mCurrentDevice = TestUtils.getTestDevice(mAdapter, i);
            Assert.assertTrue(mHeadsetService.connect(mCurrentDevice));
            verify(mObjectsFactory).makeStateMachine(mCurrentDevice,
                    mHeadsetService.getStateMachinesThreadLooper(), mHeadsetService,
                    mAdapterService, mNativeInterface, mSystemInterface);
            verify(mObjectsFactory, times(i + 1)).makeStateMachine(any(BluetoothDevice.class),
                    eq(mHeadsetService.getStateMachinesThreadLooper()), eq(mHeadsetService),
                    eq(mAdapterService), eq(mNativeInterface), eq(mSystemInterface));
            verify(mStateMachines.get(mCurrentDevice)).sendMessage(HeadsetStateMachine.CONNECT,
                    mCurrentDevice);
            verify(mStateMachines.get(mCurrentDevice)).sendMessage(eq(HeadsetStateMachine.CONNECT),
                    any(BluetoothDevice.class));
            // Put device to connecting
            when(mStateMachines.get(mCurrentDevice).getConnectionState()).thenReturn(
                    BluetoothProfile.STATE_CONNECTING);
            Assert.assertThat(mHeadsetService.getConnectedDevices(),
                    Matchers.containsInAnyOrder(connectedDevices.toArray()));
            // Put device to connected
            connectedDevices.add(mCurrentDevice);
            when(mStateMachines.get(mCurrentDevice).getDevice()).thenReturn(mCurrentDevice);
            when(mStateMachines.get(mCurrentDevice).getConnectionState()).thenReturn(
                    BluetoothProfile.STATE_CONNECTED);
            Assert.assertEquals(BluetoothProfile.STATE_CONNECTED,
                    mHeadsetService.getConnectionState(mCurrentDevice));
            Assert.assertThat(mHeadsetService.getConnectedDevices(),
                    Matchers.containsInAnyOrder(connectedDevices.toArray()));
        }
        // Connect the next device will fail
        mCurrentDevice = TestUtils.getTestDevice(mAdapter, MAX_HEADSET_CONNECTIONS);
        Assert.assertFalse(mHeadsetService.connect(mCurrentDevice));
        // Though connection failed, a new state machine is still lazily created for the device
        verify(mObjectsFactory, times(MAX_HEADSET_CONNECTIONS + 1)).makeStateMachine(
                any(BluetoothDevice.class), eq(mHeadsetService.getStateMachinesThreadLooper()),
                eq(mHeadsetService), eq(mAdapterService), eq(mNativeInterface),
                eq(mSystemInterface));
        Assert.assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mHeadsetService.getConnectionState(mCurrentDevice));
        Assert.assertThat(mHeadsetService.getConnectedDevices(),
                Matchers.containsInAnyOrder(connectedDevices.toArray()));
    }

    /**
     * Test to verify that {@link HeadsetService#connectAudio(BluetoothDevice)} return true when
     * the device is connected and audio is not connected and returns false when audio is already
     * connecting
     */
    @Test
    public void testConnectAudio_withOneDevice() {
        when(mDatabaseManager.getProfileConnectionPolicy(any(BluetoothDevice.class),
                eq(BluetoothProfile.HEADSET)))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        mCurrentDevice = TestUtils.getTestDevice(mAdapter, 0);
        Assert.assertTrue(mHeadsetService.connect(mCurrentDevice));
        verify(mObjectsFactory).makeStateMachine(mCurrentDevice,
                mHeadsetService.getStateMachinesThreadLooper(), mHeadsetService, mAdapterService,
                mNativeInterface, mSystemInterface);
        verify(mStateMachines.get(mCurrentDevice)).sendMessage(HeadsetStateMachine.CONNECT,
                mCurrentDevice);
        when(mStateMachines.get(mCurrentDevice).getDevice()).thenReturn(mCurrentDevice);
        when(mStateMachines.get(mCurrentDevice).getConnectionState()).thenReturn(
                BluetoothProfile.STATE_CONNECTED);
        when(mStateMachines.get(mCurrentDevice).getConnectingTimestampMs()).thenReturn(
                SystemClock.uptimeMillis());
        Assert.assertEquals(BluetoothProfile.STATE_CONNECTED,
                mHeadsetService.getConnectionState(mCurrentDevice));
        Assert.assertEquals(Collections.singletonList(mCurrentDevice),
                mHeadsetService.getConnectedDevices());
        mHeadsetService.onConnectionStateChangedFromStateMachine(mCurrentDevice,
                BluetoothProfile.STATE_DISCONNECTED, BluetoothProfile.STATE_CONNECTED);
        // Test connect audio - set the device first as the active device
        Assert.assertTrue(mHeadsetService.setActiveDevice(mCurrentDevice));
        Assert.assertEquals(BluetoothStatusCodes.SUCCESS,
                mHeadsetService.connectAudio(mCurrentDevice));
        verify(mStateMachines.get(mCurrentDevice)).sendMessage(HeadsetStateMachine.CONNECT_AUDIO,
                mCurrentDevice);
        when(mStateMachines.get(mCurrentDevice).getAudioState()).thenReturn(
                BluetoothHeadset.STATE_AUDIO_CONNECTING);
        // 2nd connection attempt for the same device will succeed as well
        Assert.assertEquals(BluetoothStatusCodes.SUCCESS,
                mHeadsetService.connectAudio(mCurrentDevice));
        // Verify CONNECT_AUDIO is only sent once
        verify(mStateMachines.get(mCurrentDevice)).sendMessage(
                eq(HeadsetStateMachine.CONNECT_AUDIO), any());
        // Test disconnect audio
        Assert.assertEquals(BluetoothStatusCodes.SUCCESS,
                mHeadsetService.disconnectAudio(mCurrentDevice));
        verify(mStateMachines.get(mCurrentDevice)).sendMessage(HeadsetStateMachine.DISCONNECT_AUDIO,
                mCurrentDevice);
        when(mStateMachines.get(mCurrentDevice).getAudioState()).thenReturn(
                BluetoothHeadset.STATE_AUDIO_DISCONNECTED);
        // Further disconnection requests will fail
        Assert.assertEquals(BluetoothStatusCodes.ERROR_AUDIO_DEVICE_ALREADY_DISCONNECTED,
                mHeadsetService.disconnectAudio(mCurrentDevice));
        verify(mStateMachines.get(mCurrentDevice)).sendMessage(
                eq(HeadsetStateMachine.DISCONNECT_AUDIO), any(BluetoothDevice.class));
    }

    /**
     * Test to verify that HFP audio connection can be initiated when multiple devices are connected
     * and can be canceled or disconnected as well
     */
    @Test
    public void testConnectAudio_withMultipleDevices() {
        ArrayList<BluetoothDevice> connectedDevices = new ArrayList<>();
        when(mDatabaseManager.getProfileConnectionPolicy(any(BluetoothDevice.class),
                eq(BluetoothProfile.HEADSET)))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        for (int i = 0; i < MAX_HEADSET_CONNECTIONS; ++i) {
            mCurrentDevice = TestUtils.getTestDevice(mAdapter, i);
            Assert.assertTrue(mHeadsetService.connect(mCurrentDevice));
            verify(mObjectsFactory).makeStateMachine(mCurrentDevice,
                    mHeadsetService.getStateMachinesThreadLooper(), mHeadsetService,
                    mAdapterService, mNativeInterface, mSystemInterface);
            verify(mObjectsFactory, times(i + 1)).makeStateMachine(any(BluetoothDevice.class),
                    eq(mHeadsetService.getStateMachinesThreadLooper()), eq(mHeadsetService),
                    eq(mAdapterService), eq(mNativeInterface), eq(mSystemInterface));
            verify(mStateMachines.get(mCurrentDevice)).sendMessage(HeadsetStateMachine.CONNECT,
                    mCurrentDevice);
            verify(mStateMachines.get(mCurrentDevice)).sendMessage(eq(HeadsetStateMachine.CONNECT),
                    any(BluetoothDevice.class));
            // Put device to connecting
            when(mStateMachines.get(mCurrentDevice).getConnectionState()).thenReturn(
                    BluetoothProfile.STATE_CONNECTING);
            mHeadsetService.onConnectionStateChangedFromStateMachine(mCurrentDevice,
                    BluetoothProfile.STATE_DISCONNECTED, BluetoothProfile.STATE_CONNECTING);
            Assert.assertThat(mHeadsetService.getConnectedDevices(),
                    Matchers.containsInAnyOrder(connectedDevices.toArray()));
            // Put device to connected
            connectedDevices.add(mCurrentDevice);
            when(mStateMachines.get(mCurrentDevice).getDevice()).thenReturn(mCurrentDevice);
            when(mStateMachines.get(mCurrentDevice).getConnectionState()).thenReturn(
                    BluetoothProfile.STATE_CONNECTED);
            when(mStateMachines.get(mCurrentDevice).getConnectingTimestampMs()).thenReturn(
                    SystemClock.uptimeMillis());
            Assert.assertEquals(BluetoothProfile.STATE_CONNECTED,
                    mHeadsetService.getConnectionState(mCurrentDevice));
            mHeadsetService.onConnectionStateChangedFromStateMachine(mCurrentDevice,
                    BluetoothProfile.STATE_CONNECTING, BluetoothProfile.STATE_CONNECTED);
            Assert.assertThat(mHeadsetService.getConnectedDevices(),
                    Matchers.containsInAnyOrder(connectedDevices.toArray()));
            // Try to connect audio
            // Should fail
            Assert.assertEquals(BluetoothStatusCodes.ERROR_NOT_ACTIVE_DEVICE,
                    mHeadsetService.connectAudio(mCurrentDevice));
            // Should succeed after setActiveDevice()
            Assert.assertTrue(mHeadsetService.setActiveDevice(mCurrentDevice));
            Assert.assertEquals(BluetoothStatusCodes.SUCCESS,
                    mHeadsetService.connectAudio(mCurrentDevice));
            verify(mStateMachines.get(mCurrentDevice)).sendMessage(
                    HeadsetStateMachine.CONNECT_AUDIO, mCurrentDevice);
            // Put device to audio connecting state
            when(mStateMachines.get(mCurrentDevice).getAudioState()).thenReturn(
                    BluetoothHeadset.STATE_AUDIO_CONNECTING);
            // 2nd connection attempt will also succeed
            Assert.assertEquals(BluetoothStatusCodes.SUCCESS,
                    mHeadsetService.connectAudio(mCurrentDevice));
            // Verify CONNECT_AUDIO is only sent once
            verify(mStateMachines.get(mCurrentDevice)).sendMessage(
                    eq(HeadsetStateMachine.CONNECT_AUDIO), any());
            // Put device to audio connected state
            when(mStateMachines.get(mCurrentDevice).getAudioState()).thenReturn(
                    BluetoothHeadset.STATE_AUDIO_CONNECTED);
            // Disconnect audio
            Assert.assertEquals(BluetoothStatusCodes.SUCCESS,
                    mHeadsetService.disconnectAudio(mCurrentDevice));
            verify(mStateMachines.get(mCurrentDevice)).sendMessage(
                    HeadsetStateMachine.DISCONNECT_AUDIO, mCurrentDevice);
            when(mStateMachines.get(mCurrentDevice).getAudioState()).thenReturn(
                    BluetoothHeadset.STATE_AUDIO_DISCONNECTED);
            // Further disconnection requests will fail
            Assert.assertEquals(BluetoothStatusCodes.ERROR_AUDIO_DEVICE_ALREADY_DISCONNECTED,
                    mHeadsetService.disconnectAudio(mCurrentDevice));
            verify(mStateMachines.get(mCurrentDevice)).sendMessage(
                    eq(HeadsetStateMachine.DISCONNECT_AUDIO), any(BluetoothDevice.class));
        }
    }

    /**
     * Verify that only one device can be in audio connecting or audio connected state, further
     * attempt to call {@link HeadsetService#connectAudio(BluetoothDevice)} should fail by returning
     * false
     */
    @Test
    public void testConnectAudio_connectTwoAudioChannelsShouldFail() {
        ArrayList<BluetoothDevice> connectedDevices = new ArrayList<>();
        when(mDatabaseManager.getProfileConnectionPolicy(any(BluetoothDevice.class),
                eq(BluetoothProfile.HEADSET)))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        for (int i = 0; i < MAX_HEADSET_CONNECTIONS; ++i) {
            mCurrentDevice = TestUtils.getTestDevice(mAdapter, i);
            Assert.assertTrue(mHeadsetService.connect(mCurrentDevice));
            verify(mObjectsFactory).makeStateMachine(mCurrentDevice,
                    mHeadsetService.getStateMachinesThreadLooper(), mHeadsetService,
                    mAdapterService, mNativeInterface, mSystemInterface);
            verify(mObjectsFactory, times(i + 1)).makeStateMachine(any(BluetoothDevice.class),
                    eq(mHeadsetService.getStateMachinesThreadLooper()), eq(mHeadsetService),
                    eq(mAdapterService), eq(mNativeInterface), eq(mSystemInterface));
            verify(mStateMachines.get(mCurrentDevice)).sendMessage(HeadsetStateMachine.CONNECT,
                    mCurrentDevice);
            verify(mStateMachines.get(mCurrentDevice)).sendMessage(eq(HeadsetStateMachine.CONNECT),
                    any(BluetoothDevice.class));
            // Put device to connecting
            when(mStateMachines.get(mCurrentDevice).getConnectionState()).thenReturn(
                    BluetoothProfile.STATE_CONNECTING);
            mHeadsetService.onConnectionStateChangedFromStateMachine(mCurrentDevice,
                    BluetoothProfile.STATE_DISCONNECTED, BluetoothProfile.STATE_CONNECTING);
            Assert.assertThat(mHeadsetService.getConnectedDevices(),
                    Matchers.containsInAnyOrder(connectedDevices.toArray()));
            // Put device to connected
            connectedDevices.add(mCurrentDevice);
            when(mStateMachines.get(mCurrentDevice).getDevice()).thenReturn(mCurrentDevice);
            when(mStateMachines.get(mCurrentDevice).getConnectionState()).thenReturn(
                    BluetoothProfile.STATE_CONNECTED);
            when(mStateMachines.get(mCurrentDevice).getConnectingTimestampMs()).thenReturn(
                    SystemClock.uptimeMillis());
            mHeadsetService.onConnectionStateChangedFromStateMachine(mCurrentDevice,
                    BluetoothProfile.STATE_CONNECTING, BluetoothProfile.STATE_CONNECTED);
            Assert.assertEquals(BluetoothProfile.STATE_CONNECTED,
                    mHeadsetService.getConnectionState(mCurrentDevice));
            Assert.assertThat(mHeadsetService.getConnectedDevices(),
                    Matchers.containsInAnyOrder(connectedDevices.toArray()));
        }
        if (MAX_HEADSET_CONNECTIONS >= 2) {
            // Try to connect audio
            BluetoothDevice firstDevice = connectedDevices.get(0);
            BluetoothDevice secondDevice = connectedDevices.get(1);
            // Set the first device as the active device
            Assert.assertTrue(mHeadsetService.setActiveDevice(firstDevice));
            Assert.assertEquals(BluetoothStatusCodes.SUCCESS,
                    mHeadsetService.connectAudio(firstDevice));
            verify(mStateMachines.get(firstDevice)).sendMessage(HeadsetStateMachine.CONNECT_AUDIO,
                    firstDevice);
            // Put device to audio connecting state
            when(mStateMachines.get(firstDevice).getAudioState()).thenReturn(
                    BluetoothHeadset.STATE_AUDIO_CONNECTING);
            // 2nd connection attempt will succeed for the same device
            Assert.assertEquals(BluetoothStatusCodes.SUCCESS,
                    mHeadsetService.connectAudio(firstDevice));
            // Connect to 2nd device will fail
            Assert.assertEquals(BluetoothStatusCodes.ERROR_NOT_ACTIVE_DEVICE,
                    mHeadsetService.connectAudio(secondDevice));
            verify(mStateMachines.get(secondDevice), never()).sendMessage(
                    HeadsetStateMachine.CONNECT_AUDIO, secondDevice);
            // Put device to audio connected state
            when(mStateMachines.get(firstDevice).getAudioState()).thenReturn(
                    BluetoothHeadset.STATE_AUDIO_CONNECTED);
            // Connect to 2nd device will fail
            Assert.assertEquals(BluetoothStatusCodes.ERROR_NOT_ACTIVE_DEVICE,
                    mHeadsetService.connectAudio(secondDevice));
            verify(mStateMachines.get(secondDevice), never()).sendMessage(
                    HeadsetStateMachine.CONNECT_AUDIO, secondDevice);
        }
    }

    /**
     * Verify that {@link HeadsetService#connectAudio()} will connect to first connected/connecting
     * device
     */
    @Test
    public void testConnectAudio_firstConnectedAudioDevice() {
        ArrayList<BluetoothDevice> connectedDevices = new ArrayList<>();
        when(mDatabaseManager.getProfileConnectionPolicy(any(BluetoothDevice.class),
                eq(BluetoothProfile.HEADSET)))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        doAnswer(invocation -> {
            BluetoothDevice[] devicesArray = new BluetoothDevice[connectedDevices.size()];
            return connectedDevices.toArray(devicesArray);
        }).when(mAdapterService).getBondedDevices();
        for (int i = 0; i < MAX_HEADSET_CONNECTIONS; ++i) {
            mCurrentDevice = TestUtils.getTestDevice(mAdapter, i);
            Assert.assertTrue(mHeadsetService.connect(mCurrentDevice));
            verify(mObjectsFactory).makeStateMachine(mCurrentDevice,
                    mHeadsetService.getStateMachinesThreadLooper(), mHeadsetService,
                    mAdapterService, mNativeInterface, mSystemInterface);
            verify(mObjectsFactory, times(i + 1)).makeStateMachine(any(BluetoothDevice.class),
                    eq(mHeadsetService.getStateMachinesThreadLooper()), eq(mHeadsetService),
                    eq(mAdapterService), eq(mNativeInterface), eq(mSystemInterface));
            verify(mStateMachines.get(mCurrentDevice)).sendMessage(HeadsetStateMachine.CONNECT,
                    mCurrentDevice);
            verify(mStateMachines.get(mCurrentDevice)).sendMessage(eq(HeadsetStateMachine.CONNECT),
                    any(BluetoothDevice.class));
            // Put device to connecting
            when(mStateMachines.get(mCurrentDevice).getConnectingTimestampMs()).thenReturn(
                    SystemClock.uptimeMillis());
            when(mStateMachines.get(mCurrentDevice).getConnectionState()).thenReturn(
                    BluetoothProfile.STATE_CONNECTING);
            mHeadsetService.onConnectionStateChangedFromStateMachine(mCurrentDevice,
                    BluetoothProfile.STATE_DISCONNECTED, BluetoothProfile.STATE_CONNECTING);
            Assert.assertThat(mHeadsetService.getConnectedDevices(),
                    Matchers.containsInAnyOrder(connectedDevices.toArray()));
            // Put device to connected
            connectedDevices.add(mCurrentDevice);
            when(mStateMachines.get(mCurrentDevice).getDevice()).thenReturn(mCurrentDevice);
            when(mStateMachines.get(mCurrentDevice).getConnectionState()).thenReturn(
                    BluetoothProfile.STATE_CONNECTED);
            when(mStateMachines.get(mCurrentDevice).getConnectingTimestampMs()).thenReturn(
                    SystemClock.uptimeMillis());
            Assert.assertEquals(BluetoothProfile.STATE_CONNECTED,
                    mHeadsetService.getConnectionState(mCurrentDevice));
            Assert.assertThat(mHeadsetService.getConnectedDevices(),
                    Matchers.containsInAnyOrder(connectedDevices.toArray()));
            mHeadsetService.onConnectionStateChangedFromStateMachine(mCurrentDevice,
                    BluetoothProfile.STATE_CONNECTING, BluetoothProfile.STATE_CONNECTED);
        }
        // Try to connect audio
        BluetoothDevice firstDevice = connectedDevices.get(0);
        Assert.assertTrue(mHeadsetService.setActiveDevice(firstDevice));
        Assert.assertEquals(BluetoothStatusCodes.SUCCESS, mHeadsetService.connectAudio());
        verify(mStateMachines.get(firstDevice)).sendMessage(HeadsetStateMachine.CONNECT_AUDIO,
                firstDevice);
    }

    /**
     * Test to verify that {@link HeadsetService#connectAudio(BluetoothDevice)} fails if device
     * was never connected
     */
    @Test
    public void testConnectAudio_deviceNeverConnected() {
        mCurrentDevice = TestUtils.getTestDevice(mAdapter, 0);
        Assert.assertEquals(BluetoothStatusCodes.ERROR_PROFILE_NOT_CONNECTED,
                mHeadsetService.connectAudio(mCurrentDevice));
    }

    /**
     * Test to verify that {@link HeadsetService#connectAudio(BluetoothDevice)} fails if device
     * is disconnected
     */
    @Test
    public void testConnectAudio_deviceDisconnected() {
        when(mDatabaseManager.getProfileConnectionPolicy(any(BluetoothDevice.class),
                eq(BluetoothProfile.HEADSET)))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        mCurrentDevice = TestUtils.getTestDevice(mAdapter, 0);
        HeadsetCallState headsetCallState =
                new HeadsetCallState(1, 0, HeadsetHalConstants.CALL_STATE_ALERTING,
                        TEST_PHONE_NUMBER, 128, "");
        Assert.assertTrue(mHeadsetService.connect(mCurrentDevice));
        verify(mObjectsFactory).makeStateMachine(mCurrentDevice,
                mHeadsetService.getStateMachinesThreadLooper(), mHeadsetService, mAdapterService,
                mNativeInterface, mSystemInterface);
        verify(mStateMachines.get(mCurrentDevice)).sendMessage(HeadsetStateMachine.CONNECT,
                mCurrentDevice);
        when(mStateMachines.get(mCurrentDevice).getDevice()).thenReturn(mCurrentDevice);
        // Put device in disconnected state
        when(mStateMachines.get(mCurrentDevice).getConnectionState()).thenReturn(
                BluetoothProfile.STATE_DISCONNECTED);
        Assert.assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mHeadsetService.getConnectionState(mCurrentDevice));
        Assert.assertEquals(Collections.EMPTY_LIST, mHeadsetService.getConnectedDevices());
        mHeadsetService.onConnectionStateChangedFromStateMachine(mCurrentDevice,
                BluetoothProfile.STATE_CONNECTED, BluetoothProfile.STATE_DISCONNECTED);
        // connectAudio should fail
        Assert.assertEquals(BluetoothStatusCodes.ERROR_NOT_ACTIVE_DEVICE,
                mHeadsetService.connectAudio(mCurrentDevice));
        verify(mStateMachines.get(mCurrentDevice), never()).sendMessage(
                eq(HeadsetStateMachine.CONNECT_AUDIO), any());
    }

    /**
     * Verifies that phone state change will trigger a system-wide saving of call state even when
     * no device is connected
     *
     * @throws RemoteException if binder call fails
     */
    @Test
    public void testPhoneStateChange_noDeviceSaveState() throws RemoteException {
        HeadsetCallState headsetCallState =
                new HeadsetCallState(1, 0, HeadsetHalConstants.CALL_STATE_ALERTING,
                        TEST_PHONE_NUMBER, 128, "");
        mHeadsetService.phoneStateChanged(headsetCallState.mNumActive,
                headsetCallState.mNumHeld, headsetCallState.mCallState, headsetCallState.mNumber,
                headsetCallState.mType, headsetCallState.mName, false);
        TestUtils.waitForLooperToFinishScheduledTask(
                mHeadsetService.getStateMachinesThreadLooper());
        verify(mAudioManager, never()).setA2dpSuspended(true);
        verify(mAudioManager, never()).setLeAudioSuspended(true);
        HeadsetTestUtils.verifyPhoneStateChangeSetters(mPhoneState, headsetCallState,
                ASYNC_CALL_TIMEOUT_MILLIS);
    }

    /**
     * Verifies that phone state change will trigger a system-wide saving of call state and send
     * state change to connected devices
     *
     * @throws RemoteException if binder call fails
     */
    @Test
    public void testPhoneStateChange_oneDeviceSaveState() throws RemoteException {
        HeadsetCallState headsetCallState =
                new HeadsetCallState(0, 0, HeadsetHalConstants.CALL_STATE_IDLE,
                        TEST_PHONE_NUMBER, 128, "");
        when(mDatabaseManager.getProfileConnectionPolicy(any(BluetoothDevice.class),
                eq(BluetoothProfile.HEADSET)))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        mCurrentDevice = TestUtils.getTestDevice(mAdapter, 0);
        final ArrayList<BluetoothDevice> connectedDevices = new ArrayList<>();
        // Connect one device
        Assert.assertTrue(mHeadsetService.connect(mCurrentDevice));
        verify(mObjectsFactory).makeStateMachine(mCurrentDevice,
                mHeadsetService.getStateMachinesThreadLooper(), mHeadsetService, mAdapterService,
                mNativeInterface, mSystemInterface);
        verify(mStateMachines.get(mCurrentDevice)).sendMessage(HeadsetStateMachine.CONNECT,
                mCurrentDevice);
        when(mStateMachines.get(mCurrentDevice).getDevice()).thenReturn(mCurrentDevice);
        // Put device to connecting
        when(mStateMachines.get(mCurrentDevice).getConnectingTimestampMs()).thenReturn(
                SystemClock.uptimeMillis());
        when(mStateMachines.get(mCurrentDevice).getConnectionState()).thenReturn(
                BluetoothProfile.STATE_CONNECTING);
        mHeadsetService.onConnectionStateChangedFromStateMachine(mCurrentDevice,
                BluetoothProfile.STATE_DISCONNECTED, BluetoothProfile.STATE_CONNECTING);
        Assert.assertThat(mHeadsetService.getConnectedDevices(),
                Matchers.containsInAnyOrder(connectedDevices.toArray()));
        // Put device to connected
        connectedDevices.add(mCurrentDevice);
        when(mStateMachines.get(mCurrentDevice).getDevice()).thenReturn(mCurrentDevice);
        when(mStateMachines.get(mCurrentDevice).getConnectionState()).thenReturn(
                BluetoothProfile.STATE_CONNECTED);
        when(mStateMachines.get(mCurrentDevice).getConnectingTimestampMs()).thenReturn(
                SystemClock.uptimeMillis());
        Assert.assertEquals(BluetoothProfile.STATE_CONNECTED,
                mHeadsetService.getConnectionState(mCurrentDevice));
        Assert.assertThat(mHeadsetService.getConnectedDevices(),
                Matchers.containsInAnyOrder(connectedDevices.toArray()));
        mHeadsetService.onConnectionStateChangedFromStateMachine(mCurrentDevice,
                BluetoothProfile.STATE_CONNECTING, BluetoothProfile.STATE_CONNECTED);
        // Change phone state
        mHeadsetService.phoneStateChanged(headsetCallState.mNumActive,
                headsetCallState.mNumHeld, headsetCallState.mCallState, headsetCallState.mNumber,
                headsetCallState.mType, headsetCallState.mName, false);
        TestUtils.waitForLooperToFinishScheduledTask(
                mHeadsetService.getStateMachinesThreadLooper());

        // Should not ask Audio HAL to suspend A2DP or LE Audio without active device
        verify(mAudioManager, never()).setA2dpSuspended(true);
        verify(mAudioManager, never()).setLeAudioSuspended(true);
        // Make sure we notify device about this change
        verify(mStateMachines.get(mCurrentDevice)).sendMessage(
                HeadsetStateMachine.CALL_STATE_CHANGED, headsetCallState);
        // Make sure state is updated once in phone state holder
        HeadsetTestUtils.verifyPhoneStateChangeSetters(mPhoneState, headsetCallState,
                ASYNC_CALL_TIMEOUT_MILLIS);

        // Set the device first as the active device
        Assert.assertTrue(mHeadsetService.setActiveDevice(mCurrentDevice));
        // Change phone state
        headsetCallState.mCallState = HeadsetHalConstants.CALL_STATE_ALERTING;
        mHeadsetService.phoneStateChanged(headsetCallState.mNumActive,
                headsetCallState.mNumHeld, headsetCallState.mCallState, headsetCallState.mNumber,
                headsetCallState.mType, headsetCallState.mName, false);
        TestUtils.waitForLooperToFinishScheduledTask(
                mHeadsetService.getStateMachinesThreadLooper());
        // Ask Audio HAL to suspend A2DP and LE Audio
        verify(mAudioManager).setA2dpSuspended(true);
        verify(mAudioManager).setLeAudioSuspended(true);
        // Make sure state is updated
        verify(mStateMachines.get(mCurrentDevice)).sendMessage(
                HeadsetStateMachine.CALL_STATE_CHANGED, headsetCallState);
        verify(mPhoneState).setCallState(eq(headsetCallState.mCallState));
    }

    /**
     * Verifies that phone state change will trigger a system-wide saving of call state and send
     * state change to connected devices
     *
     * @throws RemoteException if binder call fails
     */
    @Test
    public void testPhoneStateChange_multipleDevicesSaveState() throws RemoteException {
        HeadsetCallState headsetCallState =
                new HeadsetCallState(1, 0, HeadsetHalConstants.CALL_STATE_ALERTING,
                        TEST_PHONE_NUMBER, 128, "");
        final ArrayList<BluetoothDevice> connectedDevices = new ArrayList<>();
        when(mDatabaseManager.getProfileConnectionPolicy(any(BluetoothDevice.class),
                eq(BluetoothProfile.HEADSET)))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        for (int i = 0; i < MAX_HEADSET_CONNECTIONS; ++i) {
            mCurrentDevice = TestUtils.getTestDevice(mAdapter, i);
            Assert.assertTrue(mHeadsetService.connect(mCurrentDevice));
            verify(mObjectsFactory).makeStateMachine(mCurrentDevice,
                    mHeadsetService.getStateMachinesThreadLooper(), mHeadsetService,
                    mAdapterService, mNativeInterface, mSystemInterface);
            verify(mObjectsFactory, times(i + 1)).makeStateMachine(any(BluetoothDevice.class),
                    eq(mHeadsetService.getStateMachinesThreadLooper()), eq(mHeadsetService),
                    eq(mAdapterService), eq(mNativeInterface), eq(mSystemInterface));
            verify(mStateMachines.get(mCurrentDevice)).sendMessage(HeadsetStateMachine.CONNECT,
                    mCurrentDevice);
            verify(mStateMachines.get(mCurrentDevice)).sendMessage(eq(HeadsetStateMachine.CONNECT),
                    any(BluetoothDevice.class));
            // Put device to connecting
            when(mStateMachines.get(mCurrentDevice).getConnectingTimestampMs()).thenReturn(
                    SystemClock.uptimeMillis());
            when(mStateMachines.get(mCurrentDevice).getConnectionState()).thenReturn(
                    BluetoothProfile.STATE_CONNECTING);
            mHeadsetService.onConnectionStateChangedFromStateMachine(mCurrentDevice,
                    BluetoothProfile.STATE_DISCONNECTED, BluetoothProfile.STATE_CONNECTING);
            Assert.assertThat(mHeadsetService.getConnectedDevices(),
                    Matchers.containsInAnyOrder(connectedDevices.toArray()));
            // Put device to connected
            connectedDevices.add(mCurrentDevice);
            when(mStateMachines.get(mCurrentDevice).getDevice()).thenReturn(mCurrentDevice);
            when(mStateMachines.get(mCurrentDevice).getConnectionState()).thenReturn(
                    BluetoothProfile.STATE_CONNECTED);
            when(mStateMachines.get(mCurrentDevice).getConnectingTimestampMs()).thenReturn(
                    SystemClock.uptimeMillis());
            Assert.assertEquals(BluetoothProfile.STATE_CONNECTED,
                    mHeadsetService.getConnectionState(mCurrentDevice));
            Assert.assertThat(mHeadsetService.getConnectedDevices(),
                    Matchers.containsInAnyOrder(connectedDevices.toArray()));
            mHeadsetService.onConnectionStateChangedFromStateMachine(mCurrentDevice,
                    BluetoothProfile.STATE_CONNECTING, BluetoothProfile.STATE_CONNECTED);
            Assert.assertTrue(mHeadsetService.setActiveDevice(mCurrentDevice));
        }
        // Change phone state
        mHeadsetService.phoneStateChanged(headsetCallState.mNumActive,
                headsetCallState.mNumHeld, headsetCallState.mCallState, headsetCallState.mNumber,
                headsetCallState.mType, headsetCallState.mName, false);
        // Ask Audio HAL to suspend A2DP and LE Audio
        verify(mAudioManager, timeout(ASYNC_CALL_TIMEOUT_MILLIS)).setA2dpSuspended(true);
        verify(mAudioManager, timeout(ASYNC_CALL_TIMEOUT_MILLIS)).setLeAudioSuspended(true);
        // Make sure we notify devices about this change
        for (BluetoothDevice device : connectedDevices) {
            verify(mStateMachines.get(device)).sendMessage(HeadsetStateMachine.CALL_STATE_CHANGED,
                    headsetCallState);
        }
        // Make sure state is updated once in phone state holder
        HeadsetTestUtils.verifyPhoneStateChangeSetters(mPhoneState, headsetCallState,
                ASYNC_CALL_TIMEOUT_MILLIS);
    }

    /**
     * Verifies that all CLCC responses are sent to the connected device.
     */
    @Test
    public void testClccResponse_withOneDevice() {
        when(mDatabaseManager.getProfileConnectionPolicy(any(BluetoothDevice.class),
                eq(BluetoothProfile.HEADSET)))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        mCurrentDevice = TestUtils.getTestDevice(mAdapter, 0);
        Assert.assertTrue(mHeadsetService.connect(mCurrentDevice));
        verify(mObjectsFactory).makeStateMachine(mCurrentDevice,
                mHeadsetService.getStateMachinesThreadLooper(), mHeadsetService, mAdapterService,
                mNativeInterface, mSystemInterface);
        when(mStateMachines.get(mCurrentDevice).getDevice()).thenReturn(mCurrentDevice);
        when(mStateMachines.get(mCurrentDevice).getConnectionState()).thenReturn(
                BluetoothProfile.STATE_CONNECTED);
        Assert.assertEquals(BluetoothProfile.STATE_CONNECTED,
                mHeadsetService.getConnectionState(mCurrentDevice));
        mHeadsetService.clccResponse(1, 0, 0, 0, false, "8225319000", 0);
        // index 0 is the end mark of CLCC response.
        mHeadsetService.clccResponse(0, 0, 0, 0, false, "8225319000", 0);
        verify(mStateMachines.get(mCurrentDevice), times(2)).sendMessage(
                eq(HeadsetStateMachine.SEND_CLCC_RESPONSE), any(HeadsetClccResponse.class));
    }

    /**
     * Verifies that all CLCC responses are sent to the connected devices even it is connected in
     * the middle of generating CLCC responses.
     */
    @Test
    public void testClccResponse_withMultipleDevices() {
        ArrayList<BluetoothDevice> connectedDevices = new ArrayList<>();
        when(mDatabaseManager.getProfileConnectionPolicy(any(BluetoothDevice.class),
                eq(BluetoothProfile.HEADSET)))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        for (int i = 2; i >= 0; i--) {
            mCurrentDevice = TestUtils.getTestDevice(mAdapter, i);
            Assert.assertTrue(mHeadsetService.connect(mCurrentDevice));
            verify(mObjectsFactory).makeStateMachine(mCurrentDevice,
                    mHeadsetService.getStateMachinesThreadLooper(), mHeadsetService,
                    mAdapterService, mNativeInterface, mSystemInterface);
            when(mStateMachines.get(mCurrentDevice).getDevice()).thenReturn(mCurrentDevice);
            when(mStateMachines.get(mCurrentDevice).getConnectionState()).thenReturn(
                    BluetoothProfile.STATE_CONNECTED);
            connectedDevices.add(mCurrentDevice);
            // index 0 is the end mark of CLCC response.
            mHeadsetService.clccResponse(i, 0, 0, 0, false, "8225319000", 0);
        }
        for (int i = 2; i >= 0; i--) {
            verify(mStateMachines.get(connectedDevices.get(i)), times(3)).sendMessage(
                    eq(HeadsetStateMachine.SEND_CLCC_RESPONSE), any(HeadsetClccResponse.class));
        }
    }

    /**
     * Test that whether active device been removed after enable silence mode
     */
    @Test
    public void testSetSilenceMode() {
        when(mDatabaseManager.getProfileConnectionPolicy(any(BluetoothDevice.class),
                eq(BluetoothProfile.HEADSET)))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        for (int i = 0; i < 2; i++) {
            mCurrentDevice = TestUtils.getTestDevice(mAdapter, i);
            Assert.assertTrue(mHeadsetService.connect(mCurrentDevice));
            when(mStateMachines.get(mCurrentDevice).getDevice()).thenReturn(mCurrentDevice);
            when(mStateMachines.get(mCurrentDevice).getConnectionState()).thenReturn(
                    BluetoothProfile.STATE_CONNECTED);
            when(mStateMachines.get(mCurrentDevice).setSilenceDevice(
                    anyBoolean())).thenReturn(true);
        }
        mCurrentDevice = TestUtils.getTestDevice(mAdapter, 0);
        BluetoothDevice otherDevice = TestUtils.getTestDevice(mAdapter, 1);

        // Test whether active device been removed after enable silence mode.
        Assert.assertTrue(mHeadsetService.setActiveDevice(mCurrentDevice));
        Assert.assertEquals(mCurrentDevice, mHeadsetService.getActiveDevice());
        Assert.assertTrue(mHeadsetService.setSilenceMode(mCurrentDevice, true));
        Assert.assertNull(mHeadsetService.getActiveDevice());

        // Test whether active device been resumed after disable silence mode.
        Assert.assertTrue(mHeadsetService.setSilenceMode(mCurrentDevice, false));
        Assert.assertEquals(mCurrentDevice, mHeadsetService.getActiveDevice());

        // Test that active device should not be changed when silence a non-active device
        Assert.assertTrue(mHeadsetService.setActiveDevice(mCurrentDevice));
        Assert.assertEquals(mCurrentDevice, mHeadsetService.getActiveDevice());
        Assert.assertTrue(mHeadsetService.setSilenceMode(otherDevice, true));
        Assert.assertEquals(mCurrentDevice, mHeadsetService.getActiveDevice());

        // Test that active device should not be changed when another device exits silence mode
        Assert.assertTrue(mHeadsetService.setSilenceMode(otherDevice, false));
        Assert.assertEquals(mCurrentDevice, mHeadsetService.getActiveDevice());
    }

    /**
     * Test that whether active device been removed after enable silence mode
     */
    @Test
    public void testSetActiveDevice_AudioNotAllowed() {
        when(mDatabaseManager.getProfileConnectionPolicy(any(BluetoothDevice.class),
                eq(BluetoothProfile.HEADSET)))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        mCurrentDevice = TestUtils.getTestDevice(mAdapter, 0);
        mHeadsetService.setForceScoAudio(false);

        Assert.assertTrue(mHeadsetService.connect(mCurrentDevice));
        when(mStateMachines.get(mCurrentDevice).getDevice()).thenReturn(mCurrentDevice);
        when(mStateMachines.get(mCurrentDevice).getConnectionState()).thenReturn(
                BluetoothProfile.STATE_CONNECTED);

        Assert.assertTrue(mHeadsetService.setActiveDevice(null));
        when(mSystemInterface.isInCall()).thenReturn(true);
        mHeadsetService.setAudioRouteAllowed(false);

        // Test that active device should not be changed if audio is not allowed
        Assert.assertFalse(mHeadsetService.setActiveDevice(mCurrentDevice));
        Assert.assertEquals(null, mHeadsetService.getActiveDevice());
    }

    @Test
    public void testDump_doesNotCrash() {
        StringBuilder sb = new StringBuilder();

        mHeadsetService.dump(sb);
    }

    @Test
    public void testGetFallbackCandidates() {
        BluetoothDevice deviceA = TestUtils.getTestDevice(mAdapter, 0);
        BluetoothDevice deviceB = TestUtils.getTestDevice(mAdapter, 1);
        when(mDatabaseManager.getCustomMeta(any(BluetoothDevice.class),
                any(Integer.class))).thenReturn(null);

        // No connected device
        Assert.assertTrue(mHeadsetService.getFallbackCandidates(mDatabaseManager).isEmpty());

        // One connected device
        addConnectedDeviceHelper(deviceA);
        Assert.assertTrue(mHeadsetService.getFallbackCandidates(mDatabaseManager)
                .contains(deviceA));

        // Two connected devices
        addConnectedDeviceHelper(deviceB);
        Assert.assertTrue(mHeadsetService.getFallbackCandidates(mDatabaseManager)
                .contains(deviceA));
        Assert.assertTrue(mHeadsetService.getFallbackCandidates(mDatabaseManager)
                .contains(deviceB));
    }

    @Test
    public void testGetFallbackCandidates_HasWatchDevice() {
        BluetoothDevice deviceWatch = TestUtils.getTestDevice(mAdapter, 0);
        BluetoothDevice deviceRegular = TestUtils.getTestDevice(mAdapter, 1);

        // Make deviceWatch a watch
        when(mDatabaseManager.getCustomMeta(deviceWatch, BluetoothDevice.METADATA_DEVICE_TYPE))
                .thenReturn(BluetoothDevice.DEVICE_TYPE_WATCH.getBytes());
        when(mDatabaseManager.getCustomMeta(deviceRegular, BluetoothDevice.METADATA_DEVICE_TYPE))
                .thenReturn(null);

        // Has a connected watch device
        addConnectedDeviceHelper(deviceWatch);
        Assert.assertTrue(mHeadsetService.getFallbackCandidates(mDatabaseManager).isEmpty());

        // Two connected devices with one watch
        addConnectedDeviceHelper(deviceRegular);
        Assert.assertFalse(mHeadsetService.getFallbackCandidates(mDatabaseManager)
                .contains(deviceWatch));
        Assert.assertTrue(mHeadsetService.getFallbackCandidates(mDatabaseManager)
                .contains(deviceRegular));
    }

    @Test
    public void testConnectDeviceNotAllowedInbandRingPolicy_InbandRingStatus() {
        when(mDatabaseManager.getProfileConnectionPolicy(any(BluetoothDevice.class),
                eq(BluetoothProfile.HEADSET)))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        mCurrentDevice = TestUtils.getTestDevice(mAdapter, 0);
        Assert.assertTrue(mHeadsetService.connect(mCurrentDevice));
        when(mStateMachines.get(mCurrentDevice).getDevice()).thenReturn(mCurrentDevice);
        when(mStateMachines.get(mCurrentDevice).getConnectionState()).thenReturn(
                BluetoothProfile.STATE_CONNECTED);
        when(mStateMachines.get(mCurrentDevice).getConnectingTimestampMs()).thenReturn(
                SystemClock.uptimeMillis());
        Assert.assertEquals(Collections.singletonList(mCurrentDevice),
                mHeadsetService.getConnectedDevices());
        mHeadsetService.onConnectionStateChangedFromStateMachine(mCurrentDevice,
                BluetoothProfile.STATE_DISCONNECTED, BluetoothProfile.STATE_CONNECTED);

        when(mStateMachines.get(mCurrentDevice).getHfpCallAudioPolicy()).thenReturn(
                new BluetoothSinkAudioPolicy.Builder()
                        .setCallEstablishPolicy(BluetoothSinkAudioPolicy.POLICY_ALLOWED)
                        .setActiveDevicePolicyAfterConnection(
                                BluetoothSinkAudioPolicy.POLICY_ALLOWED)
                        .setInBandRingtonePolicy(BluetoothSinkAudioPolicy.POLICY_ALLOWED)
                        .build()
        );
        Assert.assertEquals(true, mHeadsetService.isInbandRingingEnabled());

        when(mStateMachines.get(mCurrentDevice).getHfpCallAudioPolicy()).thenReturn(
                new BluetoothSinkAudioPolicy.Builder()
                        .setCallEstablishPolicy(BluetoothSinkAudioPolicy.POLICY_ALLOWED)
                        .setActiveDevicePolicyAfterConnection(
                                BluetoothSinkAudioPolicy.POLICY_ALLOWED)
                        .setInBandRingtonePolicy(BluetoothSinkAudioPolicy.POLICY_NOT_ALLOWED)
                        .build()
        );
        Assert.assertEquals(false, mHeadsetService.isInbandRingingEnabled());
    }

    private void addConnectedDeviceHelper(BluetoothDevice device) {
        mCurrentDevice = device;
        when(mDatabaseManager.getProfileConnectionPolicy(any(BluetoothDevice.class),
                eq(BluetoothProfile.HEADSET)))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        Assert.assertTrue(mHeadsetService.connect(device));
        when(mStateMachines.get(device).getDevice()).thenReturn(device);
        when(mStateMachines.get(device).getConnectionState()).thenReturn(
                BluetoothProfile.STATE_CONNECTING);
        Assert.assertEquals(BluetoothProfile.STATE_CONNECTING,
                mHeadsetService.getConnectionState(device));
        when(mStateMachines.get(mCurrentDevice).getConnectionState()).thenReturn(
                BluetoothProfile.STATE_CONNECTED);
        Assert.assertEquals(BluetoothProfile.STATE_CONNECTED,
                mHeadsetService.getConnectionState(device));
        Assert.assertTrue(mHeadsetService.getConnectedDevices().contains(device));
    }

    /*
     *  Helper function to test okToAcceptConnection() method
     *
     *  @param device test device
     *  @param bondState bond state value, could be invalid
     *  @param priority value, could be invalid, coudl be invalid
     *  @param expected expected result from okToAcceptConnection()
     */
    private void testOkToAcceptConnectionCase(BluetoothDevice device, int bondState, int priority,
            boolean expected) {
        doReturn(bondState).when(mAdapterService).getBondState(device);
        when(mDatabaseManager.getProfileConnectionPolicy(device, BluetoothProfile.HEADSET))
                .thenReturn(priority);
        Assert.assertEquals(expected, mHeadsetService.okToAcceptConnection(device, false));
    }

}
