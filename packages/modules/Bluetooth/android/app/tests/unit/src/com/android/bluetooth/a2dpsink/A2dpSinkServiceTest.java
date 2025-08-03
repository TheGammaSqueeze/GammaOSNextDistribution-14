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
package com.android.bluetooth.a2dpsink;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.*;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAudioConfig;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.media.AudioFormat;
import android.os.test.TestLooper;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ServiceTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.storage.DatabaseManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class A2dpSinkServiceTest {
    private A2dpSinkService mService = null;
    private BluetoothAdapter mAdapter = null;
    private Context mTargetContext;

    @Rule public final ServiceTestRule mServiceRule = new ServiceTestRule();

    @Mock private AdapterService mAdapterService;
    @Mock private DatabaseManager mDatabaseManager;
    @Mock private A2dpSinkNativeInterface mNativeInterface;

    private BluetoothDevice mDevice1;
    private BluetoothDevice mDevice2;
    private BluetoothDevice mDevice3;
    private BluetoothDevice mDevice4;
    private BluetoothDevice mDevice5;
    private BluetoothDevice mDevice6;

    private TestLooper mLooper;

    private static final int TEST_SAMPLE_RATE = 44;
    private static final int TEST_CHANNEL_COUNT = 1;

    @Before
    public void setUp() throws Exception {
        mTargetContext = InstrumentationRegistry.getTargetContext();
        MockitoAnnotations.initMocks(this);

        mLooper = new TestLooper();

        mAdapter = BluetoothAdapter.getDefaultAdapter();
        assertThat(mAdapter).isNotNull();
        mDevice1 = mAdapter.getRemoteDevice("11:11:11:11:11:11");
        mDevice2 = mAdapter.getRemoteDevice("22:22:22:22:22:22");
        mDevice3 = mAdapter.getRemoteDevice("33:33:33:33:33:33");
        mDevice4 = mAdapter.getRemoteDevice("44:44:44:44:44:44");
        mDevice5 = mAdapter.getRemoteDevice("55:55:55:55:55:55");
        mDevice6 = mAdapter.getRemoteDevice("66:66:66:66:66:66");
        BluetoothDevice[] bondedDevices = new BluetoothDevice[]{
            mDevice1, mDevice2, mDevice3, mDevice4, mDevice5, mDevice6
        };

        doReturn(true).when(mDatabaseManager).setProfileConnectionPolicy(any(), anyInt(), anyInt());

        doReturn(mDatabaseManager).when(mAdapterService).getDatabase();
        doReturn(bondedDevices).when(mAdapterService).getBondedDevices();
        doReturn(1).when(mAdapterService).getMaxConnectedAudioDevices();

        TestUtils.setAdapterService(mAdapterService);

        doReturn(true).when(mNativeInterface).setActiveDevice(any());

        mService = new A2dpSinkService(mTargetContext, mNativeInterface, mLooper.getLooper());
        mService.start();
        assertThat(mLooper.nextMessage()).isNull();
    }

    @After
    public void tearDown() throws Exception {
        assertThat(mLooper.nextMessage()).isNull();

        mService.stop();
        assertThat(A2dpSinkService.getA2dpSinkService()).isNull();
        TestUtils.clearAdapterService(mAdapterService);
    }

    private void syncHandler(int... what) {
        TestUtils.syncHandler(mLooper, what);
    }

    private void setupDeviceConnection(BluetoothDevice device) {
        assertThat(mLooper.nextMessage()).isNull();
        assertThat(mService.getConnectionState(device)).isEqualTo(
                BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mLooper.nextMessage()).isNull();

        assertThat(mService.connect(device)).isTrue();
        syncHandler(-2 /* SM_INIT_CMD */, A2dpSinkStateMachine.CONNECT);
        StackEvent nativeEvent =
                StackEvent.connectionStateChanged(device, StackEvent.CONNECTION_STATE_CONNECTED);
        mService.messageFromNative(nativeEvent);
        syncHandler(A2dpSinkStateMachine.STACK_EVENT);
        assertThat(mService.getConnectionState(device)).isEqualTo(
                BluetoothProfile.STATE_CONNECTED);
    }

    /**
     * Mock the priority of a bluetooth device
     *
     * @param device - The bluetooth device you wish to mock the priority of
     * @param priority - The priority value you want the device to have
     */
    private void mockDevicePriority(BluetoothDevice device, int priority) {
        when(mDatabaseManager.getProfileConnectionPolicy(device, BluetoothProfile.A2DP_SINK))
                .thenReturn(priority);
    }

    /**
     * Test that initialization of the service completes and that we can get a instance
     */
    @Test
    public void testInitialize() {
        assertThat(A2dpSinkService.getA2dpSinkService()).isEqualTo(mService);
    }

    /**
     * Test that asking to connect with a null device fails
     */
    @Test
    public void testConnectNullDevice() {
        assertThrows(IllegalArgumentException.class, () -> mService.connect(null));
    }

    /**
     * Test that a CONNECTION_POLICY_ALLOWED device can connected
     */
    @Test
    public void testConnectPolicyAllowedDevice() {
        mockDevicePriority(mDevice1, BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        setupDeviceConnection(mDevice1);
    }

    /**
     * Test that a CONNECTION_POLICY_FORBIDDEN device is not allowed to connect
     */
    @Test
    public void testConnectPolicyForbiddenDevice() {
        mockDevicePriority(mDevice1, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
        assertThat(mService.connect(mDevice1)).isFalse();
        assertThat(mService.getConnectionState(mDevice1)).isEqualTo(
                BluetoothProfile.STATE_DISCONNECTED);
    }

    /**
     * Test that a CONNECTION_POLICY_UNKNOWN device is allowed to connect
     */
    @Test
    public void testConnectPolicyUnknownDevice() {
        mockDevicePriority(mDevice1, BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        setupDeviceConnection(mDevice1);
    }

    /**
     * Test that we can connect multiple devices
     */
    @Test
    public void testConnectMultipleDevices() {
        doReturn(5).when(mAdapterService).getMaxConnectedAudioDevices();

        mockDevicePriority(mDevice1, BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        mockDevicePriority(mDevice2, BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        mockDevicePriority(mDevice3, BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        mockDevicePriority(mDevice4, BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        mockDevicePriority(mDevice5, BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        mockDevicePriority(mDevice6, BluetoothProfile.CONNECTION_POLICY_ALLOWED);

        setupDeviceConnection(mDevice1);
        setupDeviceConnection(mDevice2);
        setupDeviceConnection(mDevice3);
        setupDeviceConnection(mDevice4);
        setupDeviceConnection(mDevice5);
    }

    /**
     * Test to make sure we can disconnect a connected device
     */
    @Test
    public void testDisconnect() {
        mockDevicePriority(mDevice1, BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        setupDeviceConnection(mDevice1);

        assertThat(mService.disconnect(mDevice1)).isTrue();
        syncHandler(A2dpSinkStateMachine.DISCONNECT);
        assertThat(mService.getConnectionState(mDevice1)).isEqualTo(
                BluetoothProfile.STATE_DISCONNECTED);

        syncHandler(A2dpSinkStateMachine.CLEANUP, -1 /* SM_QUIT_CMD */);
    }

    /**
     * Assure disconnect() fails with a device that's not connected
     */
    @Test
    public void testDisconnectDeviceDoesNotExist() {
        assertThat(mService.disconnect(mDevice1)).isFalse();
    }

    /**
     * Assure disconnect() fails with an invalid device
     */
    @Test
    public void testDisconnectNullDevice() {
        assertThrows(IllegalArgumentException.class, () -> mService.disconnect(null));
    }

    /**
     * Assure dump() returns something and does not crash
     */
    @Test
    public void testDump() {
        StringBuilder sb = new StringBuilder();
        mService.dump(sb);
        assertThat(sb.toString()).isNotNull();
    }

    /**
     * Test that we can set the active device to a valid device and receive it back from
     * GetActiveDevice()
     */
    @Test
    public void testSetActiveDevice() {
        mockDevicePriority(mDevice1, BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        assertThat(mService.getActiveDevice()).isNotEqualTo(mDevice1);
        assertThat(mService.setActiveDevice(mDevice1)).isTrue();
        assertThat(mService.getActiveDevice()).isEqualTo(mDevice1);
    }

    /**
     * Test that calls to set a null active device succeed in unsetting the active device
     */
    @Test
    public void testSetActiveDeviceNullDevice() {
        assertThat(mService.setActiveDevice(null)).isTrue();
        assertThat(mService.getActiveDevice()).isNull();
    }

    /**
     * Make sure we can receive the set audio configuration
     */
    @Test
    public void testGetAudioConfiguration() {
        mockDevicePriority(mDevice1, BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        setupDeviceConnection(mDevice1);

        StackEvent audioConfigChanged =
                StackEvent.audioConfigChanged(mDevice1, TEST_SAMPLE_RATE, TEST_CHANNEL_COUNT);
        mService.messageFromNative(audioConfigChanged);
        syncHandler(A2dpSinkStateMachine.STACK_EVENT);

        BluetoothAudioConfig expected = new BluetoothAudioConfig(TEST_SAMPLE_RATE,
                TEST_CHANNEL_COUNT, AudioFormat.ENCODING_PCM_16BIT);
        BluetoothAudioConfig config = mService.getAudioConfig(mDevice1);
        assertThat(config).isEqualTo(expected);
    }

    /** Make sure we ignore audio configuration changes for disconnected/unknown devices */
    @Test
    public void testOnAudioConfigChanged_withNullDevice_eventDropped() {
        StackEvent audioConfigChanged =
                StackEvent.audioConfigChanged(null, TEST_SAMPLE_RATE, TEST_CHANNEL_COUNT);
        mService.messageFromNative(audioConfigChanged);
        assertThat(mService.getAudioConfig(null)).isNull();
    }

    /** Make sure we ignore audio configuration changes for disconnected/unknown devices */
    @Test
    public void testOnAudioConfigChanged_withUnknownDevice_eventDropped() {
        assertThat(mService.getConnectionState(mDevice1))
                .isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
        StackEvent audioConfigChanged =
                StackEvent.audioConfigChanged(mDevice1, TEST_SAMPLE_RATE, TEST_CHANNEL_COUNT);
        mService.messageFromNative(audioConfigChanged);
        assertThat(mService.getAudioConfig(mDevice1)).isNull();
    }

    /**
     * Getting an audio config for a device that hasn't received one yet should return null
     */
    @Test
    public void testGetAudioConfigWithConfigUnset() {
        mockDevicePriority(mDevice1, BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        setupDeviceConnection(mDevice1);
        assertThat(mService.getAudioConfig(mDevice1)).isNull();
    }

    /**
     * Getting an audio config for a null device should return null
     */
    @Test
    public void testGetAudioConfigNullDevice() {
        assertThat(mService.getAudioConfig(null)).isNull();
    }

    /**
     * Test that a newly connected device ends up in the set returned by
     * getConnectedDevices
     */
    @Test
    public void testGetConnectedDevices() {
        ArrayList<BluetoothDevice> expected = new ArrayList<BluetoothDevice>();
        expected.add(mDevice1);

        mockDevicePriority(mDevice1, BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        setupDeviceConnection(mDevice1);

        List<BluetoothDevice> devices = mService.getConnectedDevices();
        assertThat(devices).isEqualTo(expected);
    }

    /**
     * Test that a newly connected device ends up in the set returned by
     * testGetDevicesMatchingConnectionStates
     */
    @Test
    public void testGetDevicesMatchingConnectionStatesConnected() {
        ArrayList<BluetoothDevice> expected = new ArrayList<BluetoothDevice>();
        expected.add(mDevice1);
        mockDevicePriority(mDevice1, BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        setupDeviceConnection(mDevice1);

        List<BluetoothDevice> devices = mService.getDevicesMatchingConnectionStates(
                new int[] {BluetoothProfile.STATE_CONNECTED});
        assertThat(devices).isEqualTo(expected);
    }

    /**
     * Test that a all bonded device end up in the set returned by
     * testGetDevicesMatchingConnectionStates, even when they're disconnected
     */
    @Test
    public void testGetDevicesMatchingConnectionStatesDisconnected() {
        ArrayList<BluetoothDevice> expected = new ArrayList<BluetoothDevice>();
        expected.add(mDevice1);
        expected.add(mDevice2);
        expected.add(mDevice3);
        expected.add(mDevice4);
        expected.add(mDevice5);
        expected.add(mDevice6);

        List<BluetoothDevice> devices = mService.getDevicesMatchingConnectionStates(
                new int[] {BluetoothProfile.STATE_DISCONNECTED});
        assertThat(devices).isEqualTo(expected);
    }

    /**
     * Test that GetConnectionPolicy() can get a device with policy "Allowed"
     */
    @Test
    public void testGetConnectionPolicyDeviceAllowed() {
        mockDevicePriority(mDevice1, BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        assertThat(mService.getConnectionPolicy(mDevice1)).isEqualTo(
                BluetoothProfile.CONNECTION_POLICY_ALLOWED);
    }

    /**
     * Test that GetConnectionPolicy() can get a device with policy "Forbidden"
     */
    @Test
    public void testGetConnectionPolicyDeviceForbidden() {
        mockDevicePriority(mDevice1, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
        assertThat(mService.getConnectionPolicy(mDevice1)).isEqualTo(
                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
    }

    /**
     * Test that GetConnectionPolicy() can get a device with policy "Unknown"
     */
    @Test
    public void testGetConnectionPolicyDeviceUnknown() {
        mockDevicePriority(mDevice1, BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        assertThat(mService.getConnectionPolicy(mDevice1)).isEqualTo(
                BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
    }

    /**
     * Test that SetConnectionPolicy() can change a device's policy to "Allowed"
     */
    @Test
    public void testSetConnectionPolicyDeviceAllowed() {
        assertThat(mService.setConnectionPolicy(mDevice1,
                BluetoothProfile.CONNECTION_POLICY_ALLOWED)).isTrue();
        verify(mDatabaseManager)
                .setProfileConnectionPolicy(
                        mDevice1,
                        BluetoothProfile.A2DP_SINK,
                        BluetoothProfile.CONNECTION_POLICY_ALLOWED);
    }

    /**
     * Test that SetConnectionPolicy() can change a device's policy to "Forbidden"
     */
    @Test
    public void testSetConnectionPolicyDeviceForbiddenWhileNotConnected() {
        assertThat(mService.setConnectionPolicy(mDevice1,
                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN)).isTrue();
        verify(mDatabaseManager)
                .setProfileConnectionPolicy(
                        mDevice1,
                        BluetoothProfile.A2DP_SINK,
                        BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
    }

    /**
     * Test that SetConnectionPolicy() can change a connected device's policy to "Forbidden"
     * and that the new "Forbidden" policy causes a disconnect of the device.
     */
    @Test
    public void testSetConnectionPolicyDeviceForbiddenWhileConnected() {
        mockDevicePriority(mDevice1, BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        setupDeviceConnection(mDevice1);

        assertThat(mService.setConnectionPolicy(mDevice1,
                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN)).isTrue();
        verify(mDatabaseManager)
                .setProfileConnectionPolicy(
                        mDevice1,
                        BluetoothProfile.A2DP_SINK,
                        BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);

        syncHandler(A2dpSinkStateMachine.DISCONNECT);
        verify(mNativeInterface).disconnectA2dpSink(eq(mDevice1));
        assertThat(mService.getConnectionState(mDevice1)).isEqualTo(
                BluetoothProfile.STATE_DISCONNECTED);

        syncHandler(A2dpSinkStateMachine.CLEANUP, -1 /* SM_QUIT_CMD */);
    }

    /**
     * Test that SetConnectionPolicy() can change a device's policy to "Unknown"
     */
    @Test
    public void testSetConnectionPolicyDeviceUnknown() {
        assertThat(mService.setConnectionPolicy(mDevice1,
                BluetoothProfile.CONNECTION_POLICY_UNKNOWN)).isTrue();
        verify(mDatabaseManager)
                .setProfileConnectionPolicy(
                        mDevice1,
                        BluetoothProfile.A2DP_SINK,
                        BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
    }

    /**
     * Test that SetConnectionPolicy is robust to DatabaseManager failures
     */
    @Test
    public void testSetConnectionPolicyDatabaseWriteFails() {
        when(mDatabaseManager.setProfileConnectionPolicy(any(), anyInt(),
                anyInt())).thenReturn(false);
        assertThat(mService.setConnectionPolicy(mDevice1,
                BluetoothProfile.CONNECTION_POLICY_ALLOWED)).isFalse();
    }

    @Test
    public void testDumpDoesNotCrash() {
        mockDevicePriority(mDevice1, BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        setupDeviceConnection(mDevice1);

        mService.dump(new StringBuilder());
    }
}
