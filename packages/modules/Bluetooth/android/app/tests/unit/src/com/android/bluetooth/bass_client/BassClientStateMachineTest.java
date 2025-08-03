/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.bluetooth.bass_client;

import static android.bluetooth.BluetoothGatt.GATT_FAILURE;
import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;

import static com.android.bluetooth.bass_client.BassClientStateMachine.ADD_BCAST_SOURCE;
import static com.android.bluetooth.bass_client.BassClientStateMachine.CONNECT;
import static com.android.bluetooth.bass_client.BassClientStateMachine.CONNECTION_STATE_CHANGED;
import static com.android.bluetooth.bass_client.BassClientStateMachine.CONNECT_TIMEOUT;
import static com.android.bluetooth.bass_client.BassClientStateMachine.DISCONNECT;
import static com.android.bluetooth.bass_client.BassClientStateMachine.GATT_TXN_PROCESSED;
import static com.android.bluetooth.bass_client.BassClientStateMachine.GATT_TXN_TIMEOUT;
import static com.android.bluetooth.bass_client.BassClientStateMachine.PSYNC_ACTIVE_TIMEOUT;
import static com.android.bluetooth.bass_client.BassClientStateMachine.REACHED_MAX_SOURCE_LIMIT;
import static com.android.bluetooth.bass_client.BassClientStateMachine.READ_BASS_CHARACTERISTICS;
import static com.android.bluetooth.bass_client.BassClientStateMachine.REMOTE_SCAN_START;
import static com.android.bluetooth.bass_client.BassClientStateMachine.REMOTE_SCAN_STOP;
import static com.android.bluetooth.bass_client.BassClientStateMachine.REMOVE_BCAST_SOURCE;
import static com.android.bluetooth.bass_client.BassClientStateMachine.SELECT_BCAST_SOURCE;
import static com.android.bluetooth.bass_client.BassClientStateMachine.SET_BCAST_CODE;
import static com.android.bluetooth.bass_client.BassClientStateMachine.START_SCAN_OFFLOAD;
import static com.android.bluetooth.bass_client.BassClientStateMachine.STOP_SCAN_OFFLOAD;
import static com.android.bluetooth.bass_client.BassClientStateMachine.SWITCH_BCAST_SOURCE;
import static com.android.bluetooth.bass_client.BassClientStateMachine.UPDATE_BCAST_SOURCE;
import static com.android.bluetooth.bass_client.BassConstants.CLIENT_CHARACTERISTIC_CONFIG;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothLeAudioCodecConfigMetadata;
import android.bluetooth.BluetoothLeAudioContentMetadata;
import android.bluetooth.BluetoothLeBroadcastChannel;
import android.bluetooth.BluetoothLeBroadcastMetadata;
import android.bluetooth.BluetoothLeBroadcastReceiveState;
import android.bluetooth.BluetoothLeBroadcastSubgroup;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.le.PeriodicAdvertisingCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;

import com.android.bluetooth.BluetoothMethodProxy;
import com.android.bluetooth.TestUtils;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.flags.FakeFeatureFlagsImpl;
import com.android.bluetooth.flags.FeatureFlags;
import com.android.bluetooth.flags.Flags;

import org.hamcrest.core.IsInstanceOf;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@MediumTest
@RunWith(JUnit4.class)
public class BassClientStateMachineTest {
    @Rule
    public final MockitoRule mockito = MockitoJUnit.rule();

    private static final int CONNECTION_TIMEOUT_MS = 1_000;
    private static final int TIMEOUT_MS = 2_000;
    private static final int WAIT_MS = 1_200;
    private static final String TEST_BROADCAST_NAME = "Test";
    private static final String EMPTY_BLUETOOTH_DEVICE_ADDRESS = "00:00:00:00:00:00";
    private Context mTargetContext;
    private BluetoothAdapter mAdapter;
    private HandlerThread mHandlerThread;
    private StubBassClientStateMachine mBassClientStateMachine;
    private BluetoothDevice mTestDevice;
    private BluetoothDevice mSourceTestDevice;
    private BluetoothDevice mEmptyTestDevice;
    private FakeFeatureFlagsImpl mFakeFlagsImpl;

    @Mock private AdapterService mAdapterService;
    @Mock private BassClientService mBassClientService;
    @Mock private BluetoothMethodProxy mMethodProxy;

    @Before
    public void setUp() throws Exception {
        mTargetContext = InstrumentationRegistry.getTargetContext();
        BluetoothManager manager = mTargetContext.getSystemService(BluetoothManager.class);
        assertThat(manager).isNotNull();
        mAdapter = manager.getAdapter();

        mEmptyTestDevice = mAdapter.getRemoteDevice(EMPTY_BLUETOOTH_DEVICE_ADDRESS);
        assertThat(mEmptyTestDevice).isNotNull();

        TestUtils.setAdapterService(mAdapterService);

        mFakeFlagsImpl = new FakeFeatureFlagsImpl();
        mFakeFlagsImpl.setFlag(Flags.FLAG_LEAUDIO_BROADCAST_MONITOR_SOURCE_SYNC_STATUS, false);
        mFakeFlagsImpl.setFlag(Flags.FLAG_LEAUDIO_BROADCAST_AUDIO_HANDOVER_POLICIES, false);
        BluetoothMethodProxy.setInstanceForTesting(mMethodProxy);
        doNothing().when(mMethodProxy).periodicAdvertisingManagerTransferSync(
                any(), any(), anyInt(), anyInt());

        // Get a device for testing
        mTestDevice = TestUtils.getTestDevice(mAdapter, 0);
        mSourceTestDevice = TestUtils.getTestDevice(mAdapter, 1);

        doReturn(mEmptyTestDevice)
                .when(mAdapterService)
                .getDeviceFromByte(Utils.getBytesFromAddress(EMPTY_BLUETOOTH_DEVICE_ADDRESS));
        doReturn(mTestDevice)
                .when(mAdapterService)
                .getDeviceFromByte(Utils.getBytesFromAddress(mTestDevice.getAddress()));
        doReturn(mSourceTestDevice)
                .when(mAdapterService)
                .getDeviceFromByte(Utils.getBytesFromAddress(mSourceTestDevice.getAddress()));
        doReturn(mEmptyTestDevice)
                .when(mAdapterService)
                .getDeviceFromByte(Utils.getBytesFromAddress(mEmptyTestDevice.getAddress()));

        // Set up thread and looper
        mHandlerThread = new HandlerThread("BassClientStateMachineTestHandlerThread");
        mHandlerThread.start();
        mBassClientStateMachine =
                new StubBassClientStateMachine(
                        mTestDevice,
                        mBassClientService,
                        mAdapterService,
                        mHandlerThread.getLooper(),
                        CONNECTION_TIMEOUT_MS,
                        mFakeFlagsImpl);
        assertThat(mBassClientStateMachine).isNotNull();
        mBassClientStateMachine.start();
    }

    @After
    public void tearDown() throws Exception {
        if (mBassClientStateMachine == null) {
            return;
        }

        mBassClientStateMachine.doQuit();
        mHandlerThread.quit();
        TestUtils.clearAdapterService(mAdapterService);
    }

    /**
     * Test that default state is disconnected
     */
    @Test
    public void testDefaultDisconnectedState() {
        Assert.assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mBassClientStateMachine.getConnectionState());
    }

    /**
     * Allow/disallow connection to any device.
     *
     * @param allow if true, connection is allowed
     */
    private void allowConnection(boolean allow) {
        when(mBassClientService.okToConnect(any(BluetoothDevice.class))).thenReturn(allow);
    }

    private void allowConnectGatt(boolean allow) {
        mBassClientStateMachine.mShouldAllowGatt = allow;
    }

    /**
     * Test that an incoming connection with policy forbidding connection is rejected
     */
    @Test
    public void testOkToConnectFails() {
        allowConnection(false);
        allowConnectGatt(true);

        // Inject an event for when incoming connection is requested
        mBassClientStateMachine.sendMessage(CONNECT);

        // Verify that no connection state broadcast is executed
        verify(mBassClientService, after(WAIT_MS).never()).sendBroadcast(any(Intent.class),
                anyString());

        // Check that we are in Disconnected state
        Assert.assertThat(mBassClientStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(BassClientStateMachine.Disconnected.class));
    }

    @Test
    public void testFailToConnectGatt() {
        allowConnection(true);
        allowConnectGatt(false);

        // Inject an event for when incoming connection is requested
        mBassClientStateMachine.sendMessage(CONNECT);

        // Verify that no connection state broadcast is executed
        verify(mBassClientService, after(WAIT_MS).never()).sendBroadcast(any(Intent.class),
                anyString());

        // Check that we are in Disconnected state
        Assert.assertThat(mBassClientStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(BassClientStateMachine.Disconnected.class));
        assertNull(mBassClientStateMachine.mBluetoothGatt);
    }

    @Test
    public void testSuccessfullyConnected() {
        allowConnection(true);
        allowConnectGatt(true);

        // Inject an event for when incoming connection is requested
        mBassClientStateMachine.sendMessage(CONNECT);

        // Verify that one connection state broadcast is executed
        ArgumentCaptor<Intent> intentArgument1 = ArgumentCaptor.forClass(Intent.class);
        verify(mBassClientService, timeout(TIMEOUT_MS).times(1)).sendBroadcast(
                intentArgument1.capture(), anyString(), any(Bundle.class));
        Assert.assertEquals(BluetoothProfile.STATE_CONNECTING,
                intentArgument1.getValue().getIntExtra(BluetoothProfile.EXTRA_STATE, -1));

        Assert.assertThat(mBassClientStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(BassClientStateMachine.Connecting.class));

        assertNotNull(mBassClientStateMachine.mGattCallback);
        mBassClientStateMachine.notifyConnectionStateChanged(
                GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED);

        // Verify that the expected number of broadcasts are executed:
        // - two calls to broadcastConnectionState(): Disconnected -> Connecting -> Connected
        ArgumentCaptor<Intent> intentArgument2 = ArgumentCaptor.forClass(Intent.class);
        verify(mBassClientService, timeout(TIMEOUT_MS).times(2)).sendBroadcast(
                intentArgument2.capture(), anyString(), any(Bundle.class));

        Assert.assertThat(mBassClientStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(BassClientStateMachine.Connected.class));
    }

    @Test
    public void testConnectGattTimeout() {
        allowConnection(true);
        allowConnectGatt(true);

        // Inject an event for when incoming connection is requested
        mBassClientStateMachine.sendMessage(CONNECT);

        // Verify that one connection state broadcast is executed
        ArgumentCaptor<Intent> intentArgument1 = ArgumentCaptor.forClass(Intent.class);
        verify(mBassClientService, timeout(TIMEOUT_MS).times(1)).sendBroadcast(
                intentArgument1.capture(), anyString(), any(Bundle.class));
        Assert.assertEquals(BluetoothProfile.STATE_CONNECTING,
                intentArgument1.getValue().getIntExtra(BluetoothProfile.EXTRA_STATE, -1));

        Assert.assertThat(mBassClientStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(BassClientStateMachine.Connecting.class));

        // Verify that one connection state broadcast is executed
        ArgumentCaptor<Intent> intentArgument2 = ArgumentCaptor.forClass(Intent.class);
        verify(mBassClientService, timeout(TIMEOUT_MS).times(
                2)).sendBroadcast(intentArgument2.capture(), anyString(), any(Bundle.class));
        Assert.assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                intentArgument2.getValue().getIntExtra(BluetoothProfile.EXTRA_STATE, -1));

        Assert.assertThat(mBassClientStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(BassClientStateMachine.Disconnected.class));
    }

    @Test
    public void testStatesChangesWithMessages() {
        allowConnection(true);
        allowConnectGatt(true);

        // need this to ensure expected mock behavior for getActiveSyncedSource
        when(mBassClientService.getActiveSyncedSources(any())).thenReturn(null);

        assertThat(mBassClientStateMachine.getCurrentState())
                .isInstanceOf(BassClientStateMachine.Disconnected.class);

        // disconnected -> connecting ---timeout---> disconnected
        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(CONNECT),
                BassClientStateMachine.Connecting.class);
        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(BassClientStateMachine.CONNECT_TIMEOUT),
                BassClientStateMachine.Disconnected.class);

        // disconnected -> connecting ---DISCONNECT---> disconnected
        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(CONNECT),
                BassClientStateMachine.Connecting.class);
        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(BassClientStateMachine.DISCONNECT),
                BassClientStateMachine.Disconnected.class);

        // disconnected -> connecting ---CONNECTION_STATE_CHANGED(connected)---> connected -->
        // disconnected
        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(CONNECT),
                BassClientStateMachine.Connecting.class);
        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(
                        CONNECTION_STATE_CHANGED,
                        Integer.valueOf(BluetoothProfile.STATE_CONNECTED)),
                BassClientStateMachine.Connected.class);
        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(
                        CONNECTION_STATE_CHANGED,
                        Integer.valueOf(BluetoothProfile.STATE_DISCONNECTED)),
                BassClientStateMachine.Disconnected.class);

        // disconnected -> connecting ---CONNECTION_STATE_CHANGED(non-connected) --> disconnected
        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(CONNECT),
                BassClientStateMachine.Connecting.class);
        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(
                        CONNECTION_STATE_CHANGED,
                        Integer.valueOf(BluetoothProfile.STATE_DISCONNECTED)),
                BassClientStateMachine.Disconnected.class);

        // change default state to connected for the next tests
        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(CONNECT),
                BassClientStateMachine.Connecting.class);
        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(
                        CONNECTION_STATE_CHANGED,
                        Integer.valueOf(BluetoothProfile.STATE_CONNECTED)),
                BassClientStateMachine.Connected.class);

        // connected ----READ_BASS_CHARACTERISTICS---> connectedProcessing --GATT_TXN_PROCESSED
        // --> connected

        // Make bluetoothGatt non-null so state will transit
        mBassClientStateMachine.mBluetoothGatt = Mockito.mock(
                BassClientStateMachine.BluetoothGattTestableWrapper.class);
        mBassClientStateMachine.mBroadcastScanControlPoint =
                new BluetoothGattCharacteristic(
                        BassConstants.BASS_BCAST_AUDIO_SCAN_CTRL_POINT,
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
                                | BluetoothGattCharacteristic.PROPERTY_WRITE,
                        BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED);

        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(
                        READ_BASS_CHARACTERISTICS,
                        new BluetoothGattCharacteristic(
                                UUID.randomUUID(),
                                BluetoothGattCharacteristic.PROPERTY_READ
                                        | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED)),
                BassClientStateMachine.ConnectedProcessing.class);
        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(GATT_TXN_PROCESSED),
                BassClientStateMachine.Connected.class);

        // connected ----READ_BASS_CHARACTERISTICS---> connectedProcessing --GATT_TXN_TIMEOUT -->
        // connected
        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(
                        READ_BASS_CHARACTERISTICS,
                        new BluetoothGattCharacteristic(
                                UUID.randomUUID(),
                                BluetoothGattCharacteristic.PROPERTY_READ
                                        | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED)),
                BassClientStateMachine.ConnectedProcessing.class);
        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(GATT_TXN_TIMEOUT),
                BassClientStateMachine.Connected.class);

        // connected ----START_SCAN_OFFLOAD---> connectedProcessing --GATT_TXN_PROCESSED-->
        // connected
        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(BassClientStateMachine.START_SCAN_OFFLOAD),
                BassClientStateMachine.ConnectedProcessing.class);
        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(GATT_TXN_PROCESSED),
                BassClientStateMachine.Connected.class);

        // connected ----STOP_SCAN_OFFLOAD---> connectedProcessing --GATT_TXN_PROCESSED--> connected
        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(STOP_SCAN_OFFLOAD),
                BassClientStateMachine.ConnectedProcessing.class);
        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(GATT_TXN_PROCESSED),
                BassClientStateMachine.Connected.class);
    }

    @Test
    public void acquireAllBassChars() {
        BassClientStateMachine.BluetoothGattTestableWrapper btGatt = Mockito.mock(
                BassClientStateMachine.BluetoothGattTestableWrapper.class);
        mBassClientStateMachine.mBluetoothGatt = btGatt;
        // Do nothing when mBluetoothGatt.getService returns null
        mBassClientStateMachine.acquireAllBassChars();

        BluetoothGattService gattService = Mockito.mock(BluetoothGattService.class);
        when(btGatt.getService(BassConstants.BASS_UUID)).thenReturn(gattService);

        List<BluetoothGattCharacteristic> characteristics = new ArrayList<>();
        BluetoothGattCharacteristic scanControlPoint =
                new BluetoothGattCharacteristic(
                        BassConstants.BASS_BCAST_AUDIO_SCAN_CTRL_POINT,
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
                                | BluetoothGattCharacteristic.PROPERTY_WRITE,
                        BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED);
        characteristics.add(scanControlPoint);

        BluetoothGattCharacteristic bassCharacteristic =
                new BluetoothGattCharacteristic(
                        UUID.randomUUID(),
                        BluetoothGattCharacteristic.PROPERTY_READ
                                | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                        BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED);
        characteristics.add(bassCharacteristic);

        when(gattService.getCharacteristics()).thenReturn(characteristics);
        mBassClientStateMachine.acquireAllBassChars();
        assertThat(mBassClientStateMachine.mBroadcastScanControlPoint).isEqualTo(scanControlPoint);
        assertThat(mBassClientStateMachine.mBroadcastCharacteristics).contains(bassCharacteristic);
    }

    @Test
    public void simpleMethods() {
        // dump() shouldn't crash
        StringBuilder sb = new StringBuilder();
        mBassClientStateMachine.dump(sb);

        // log() shouldn't crash
        String msg = "test-log-message";
        mBassClientStateMachine.log(msg);

        // messageWhatToString() shouldn't crash
        for (int i = CONNECT; i <= CONNECT_TIMEOUT + 1; ++i) {
            mBassClientStateMachine.messageWhatToString(i);
        }

        final int invalidSourceId = -100;
        assertThat(mBassClientStateMachine.getCurrentBroadcastMetadata(invalidSourceId)).isNull();
        assertThat(mBassClientStateMachine.getDevice()).isEqualTo(mTestDevice);
        assertThat(mBassClientStateMachine.hasPendingSourceOperation()).isFalse();
        assertThat(mBassClientStateMachine.isEmpty(new byte[] { 0 })).isTrue();
        assertThat(mBassClientStateMachine.isEmpty(new byte[] { 1 })).isFalse();
        assertThat(mBassClientStateMachine.isPendingRemove(invalidSourceId)).isFalse();
    }

    @Test
    public void parseScanRecord_withoutBaseData_callCancelActiveSync() {
        byte[] scanRecord = new byte[]{
                0x02, 0x01, 0x1a, // advertising flags
                0x05, 0x02, 0x0b, 0x11, 0x0a, 0x11, // 16 bit service uuids
                0x04, 0x09, 0x50, 0x65, 0x64, // name
                0x02, 0x0A, (byte) 0xec, // tx power level
                0x05, 0x16, 0x0b, 0x11, 0x50, 0x64, // service data
                0x05, (byte) 0xff, (byte) 0xe0, 0x00, 0x02, 0x15, // manufacturer specific data
                0x03, 0x50, 0x01, 0x02, // an unknown data type won't cause trouble
        };
        // need this to ensure expected mock behavior for getActiveSyncedSource
        when(mBassClientService.getActiveSyncedSources(any())).thenReturn(null);

        ScanRecord data = ScanRecord.parseFromBytes(scanRecord);
        mBassClientStateMachine.parseScanRecord(0, data);
        // verify getActiveSyncedSource got called in CancelActiveSync
        verify(mBassClientService).getActiveSyncedSources(any());
    }

    @Test
    public void parseScanRecord_withBaseData_callsUpdateBase() {
        byte[] scanRecordWithBaseData =
                new byte[] {
                    (byte) 0x02,
                    (byte) 0x01,
                    (byte) 0x1a, // advertising flags
                    (byte) 0x05,
                    (byte) 0x02,
                    (byte) 0x51,
                    (byte) 0x18,
                    (byte) 0x0a,
                    (byte) 0x11, // 16 bit service uuids
                    (byte) 0x04,
                    (byte) 0x09,
                    (byte) 0x50,
                    (byte) 0x65,
                    (byte) 0x64, // name
                    (byte) 0x02,
                    (byte) 0x0A,
                    (byte) 0xec, // tx power level
                    (byte) 0x19,
                    (byte) 0x16,
                    (byte) 0x51,
                    (byte) 0x18, // service data (base data with 18 bytes)
                    // LEVEL 1
                    (byte) 0x01,
                    (byte) 0x02,
                    (byte) 0x03, // presentationDelay
                    (byte) 0x01, // numSubGroups
                    // LEVEL 2
                    (byte) 0x01, // numSubGroups
                    (byte) 0x00,
                    (byte) 0x00,
                    (byte) 0x00,
                    (byte) 0x00,
                    (byte) 0x00, // UNKNOWN_CODEC
                    (byte) 0x02, // codecConfigLength
                    (byte) 0x01,
                    (byte) 'A', // codecConfigInfo
                    (byte) 0x03, // metaDataLength
                    (byte) 0x06,
                    (byte) 0x07,
                    (byte) 0x08, // metaData
                    // LEVEL 3
                    (byte) 0x04, // index
                    (byte) 0x03, // codecConfigLength
                    (byte) 0x02,
                    (byte) 'B',
                    (byte) 'C', // codecConfigInfo
                    (byte) 0x05,
                    (byte) 0xff,
                    (byte) 0xe0,
                    (byte) 0x00,
                    (byte) 0x02,
                    (byte) 0x15, // manufacturer specific data
                    (byte) 0x03,
                    (byte) 0x50,
                    (byte) 0x01,
                    (byte) 0x02, // an unknown data type won't cause trouble
                };
        ScanRecord data = ScanRecord.parseFromBytes(scanRecordWithBaseData);
        assertThat(data.getServiceUuids()).contains(BassConstants.BASIC_AUDIO_UUID);
        assertThat(data.getServiceData(BassConstants.BASIC_AUDIO_UUID)).isNotNull();
        mBassClientStateMachine.parseScanRecord(0, data);
        verify(mBassClientService).updateBase(anyInt(), any());
    }

    @Test
    public void gattCallbackOnConnectionStateChange_changedToConnected()
            throws InterruptedException {
        mBassClientStateMachine.connectGatt(true);
        BluetoothGattCallback cb = mBassClientStateMachine.mGattCallback;
        BassClientStateMachine.BluetoothGattTestableWrapper btGatt = Mockito.mock(
                BassClientStateMachine.BluetoothGattTestableWrapper.class);
        mBassClientStateMachine.mBluetoothGatt = btGatt;

        // disallow connection
        allowConnection(false);
        int status = BluetoothProfile.STATE_CONNECTING;
        int newState = BluetoothProfile.STATE_CONNECTED;
        cb.onConnectionStateChange(null, status, newState);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());

        verify(btGatt).disconnect();
        verify(btGatt).close();
        assertThat(mBassClientStateMachine.mBluetoothGatt).isNull();
        assertThat(mBassClientStateMachine.mMsgWhats).contains(CONNECTION_STATE_CHANGED);
        mBassClientStateMachine.mMsgWhats.clear();

        mBassClientStateMachine.mBluetoothGatt = btGatt;
        allowConnection(true);
        mBassClientStateMachine.mDiscoveryInitiated = false;
        status = BluetoothProfile.STATE_DISCONNECTED;
        newState = BluetoothProfile.STATE_CONNECTED;
        cb.onConnectionStateChange(null, status, newState);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());

        assertThat(mBassClientStateMachine.mDiscoveryInitiated).isTrue();
        assertThat(mBassClientStateMachine.mMsgWhats).contains(CONNECTION_STATE_CHANGED);
        assertThat(mBassClientStateMachine.mMsgObj).isEqualTo(newState);
        mBassClientStateMachine.mMsgWhats.clear();
    }

    @Test
    public void gattCallbackOnConnectionStateChanged_changedToDisconnected()
            throws InterruptedException {
        initToConnectingState();
        mBassClientStateMachine.connectGatt(true);
        BluetoothGattCallback cb = mBassClientStateMachine.mGattCallback;
        BassClientStateMachine.BluetoothGattTestableWrapper btGatt = Mockito.mock(
                BassClientStateMachine.BluetoothGattTestableWrapper.class);
        mBassClientStateMachine.mBluetoothGatt = btGatt;

        allowConnection(false);
        int status = BluetoothProfile.STATE_CONNECTING;
        int newState = BluetoothProfile.STATE_DISCONNECTED;
        cb.onConnectionStateChange(null, status, newState);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());

        assertThat(mBassClientStateMachine.mMsgWhats).contains(CONNECTION_STATE_CHANGED);
        assertThat(mBassClientStateMachine.mMsgObj).isEqualTo(newState);
        mBassClientStateMachine.mMsgWhats.clear();
    }

    @Test
    public void gattCallbackOnServicesDiscovered() {
        mBassClientStateMachine.connectGatt(true);
        BluetoothGattCallback cb = mBassClientStateMachine.mGattCallback;
        BassClientStateMachine.BluetoothGattTestableWrapper btGatt = Mockito.mock(
                BassClientStateMachine.BluetoothGattTestableWrapper.class);
        mBassClientStateMachine.mBluetoothGatt = btGatt;

        // Do nothing if mDiscoveryInitiated is false.
        mBassClientStateMachine.mDiscoveryInitiated = false;
        int status = GATT_FAILURE;
        cb.onServicesDiscovered(null, status);

        verify(btGatt, never()).requestMtu(anyInt());

        // Do nothing if status is not GATT_SUCCESS.
        mBassClientStateMachine.mDiscoveryInitiated = true;
        status = GATT_FAILURE;
        cb.onServicesDiscovered(null, status);

        verify(btGatt, never()).requestMtu(anyInt());

        // call requestMtu() if status is GATT_SUCCESS.
        mBassClientStateMachine.mDiscoveryInitiated = true;
        status = GATT_SUCCESS;
        cb.onServicesDiscovered(null, status);

        verify(btGatt).requestMtu(anyInt());
    }

    /**
     * This also tests BassClientStateMachine#processBroadcastReceiverState.
     */
    @Test
    public void gattCallbackOnCharacteristicRead() {
        mBassClientStateMachine.mShouldHandleMessage = false;
        mBassClientStateMachine.connectGatt(true);
        BluetoothGattCallback cb = mBassClientStateMachine.mGattCallback;
        BluetoothGattDescriptor desc = Mockito.mock(BluetoothGattDescriptor.class);
        BassClientService.Callbacks callbacks = Mockito.mock(BassClientService.Callbacks.class);
        BluetoothGattCharacteristic characteristic =
                Mockito.mock(BluetoothGattCharacteristic.class);
        BassClientStateMachine.BluetoothGattTestableWrapper btGatt = Mockito.mock(
                BassClientStateMachine.BluetoothGattTestableWrapper.class);
        when(characteristic.getUuid()).thenReturn(BassConstants.BASS_BCAST_RECEIVER_STATE);
        when(mBassClientService.getCallbacks()).thenReturn(callbacks);

        // Characteristic read success with null value
        when(characteristic.getValue()).thenReturn(null);
        cb.onCharacteristicRead(null, characteristic, GATT_SUCCESS);
        verify(characteristic, never()).getDescriptor(any());

        // Characteristic read failed and mBluetoothGatt is null.
        mBassClientStateMachine.mBluetoothGatt = null;
        cb.onCharacteristicRead(null, characteristic, GATT_FAILURE);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());

        assertThat(mBassClientStateMachine.mMsgWhats).contains(GATT_TXN_PROCESSED);
        assertThat(mBassClientStateMachine.mMsgAgr1).isEqualTo(GATT_FAILURE);
        mBassClientStateMachine.mMsgWhats.clear();


        // Characteristic read failed and mBluetoothGatt is not null.
        mBassClientStateMachine.mBluetoothGatt = btGatt;
        when(characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)).thenReturn(desc);
        cb.onCharacteristicRead(null, characteristic, GATT_FAILURE);

        verify(btGatt).setCharacteristicNotification(any(), anyBoolean());
        verify(btGatt).writeDescriptor(desc);
        verify(desc).setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);

        // Tests for processBroadcastReceiverState
        int sourceId = 1;
        byte[] value = new byte[] { };
        mBassClientStateMachine.mNumOfBroadcastReceiverStates = 2;
        mBassClientStateMachine.mPendingOperation = REMOVE_BCAST_SOURCE;
        mBassClientStateMachine.mPendingSourceId = (byte) sourceId;
        when(characteristic.getValue()).thenReturn(value);
        when(characteristic.getInstanceId()).thenReturn(sourceId);

        cb.onCharacteristicRead(null, characteristic, GATT_SUCCESS);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        verify(callbacks).notifyReceiveStateChanged(any(), anyInt(), any());

        mBassClientStateMachine.mPendingOperation = 0;
        mBassClientStateMachine.mPendingSourceId = 0;
        sourceId = 2; // mNextId would become 2
        when(characteristic.getInstanceId()).thenReturn(sourceId);

        Mockito.clearInvocations(callbacks);
        cb.onCharacteristicRead(null, characteristic, GATT_SUCCESS);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        verify(callbacks).notifyReceiveStateChanged(any(), anyInt(), any());

        mBassClientStateMachine.mPendingMetadata = createBroadcastMetadata();
        sourceId = 1;
        value =
                new byte[] {
                    (byte) sourceId, // sourceId
                    (byte) (mSourceTestDevice.getAddressType() & 0xFF), // sourceAddressType
                    Utils.getByteAddress(mSourceTestDevice)[5],
                    Utils.getByteAddress(mSourceTestDevice)[4],
                    Utils.getByteAddress(mSourceTestDevice)[3],
                    Utils.getByteAddress(mSourceTestDevice)[2],
                    Utils.getByteAddress(mSourceTestDevice)[1],
                    Utils.getByteAddress(mSourceTestDevice)[0], // sourceAddress
                    0x00, // sourceAdvSid
                    0x00,
                    0x00,
                    0x00, // broadcastIdBytes
                    (byte) BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_NO_PAST,
                    (byte) BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_BAD_CODE,
                    // 16 bytes badBroadcastCode
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x01, // numSubGroups
                    // SubGroup #1
                    0x00,
                    0x00,
                    0x00,
                    0x00, // audioSyncIndex
                    0x02, // metaDataLength
                    0x00,
                    0x00, // metadata
                };
        when(characteristic.getValue()).thenReturn(value);
        when(characteristic.getInstanceId()).thenReturn(sourceId);

        Mockito.clearInvocations(callbacks);
        cb.onCharacteristicRead(null, characteristic, GATT_SUCCESS);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());

        verify(callbacks).notifySourceAdded(any(), any(), anyInt());
        verify(callbacks).notifyReceiveStateChanged(any(), anyInt(), any());

        // set some values for covering more lines of processPASyncState()
        mBassClientStateMachine.mPendingMetadata = null;
        mBassClientStateMachine.mSetBroadcastCodePending = true;
        mBassClientStateMachine.mIsPendingRemove = true;
        value[BassConstants.BCAST_RCVR_STATE_PA_SYNC_IDX] =
                BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_SYNCINFO_REQUEST;
        value[BassConstants.BCAST_RCVR_STATE_ENC_STATUS_IDX] =
                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_CODE_REQUIRED;
        value[35] = 0; // set metaDataLength of subgroup #1 0
        PeriodicAdvertisementResult paResult = Mockito.mock(PeriodicAdvertisementResult.class);
        when(characteristic.getValue()).thenReturn(value);
        when(mBassClientService.getPeriodicAdvertisementResult(any(), anyInt()))
                .thenReturn(paResult);
        when(paResult.getSyncHandle()).thenReturn(100);

        Mockito.clearInvocations(callbacks);
        cb.onCharacteristicRead(null, characteristic, GATT_SUCCESS);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());

        verify(callbacks).notifyReceiveStateChanged(any(), anyInt(), any());
        assertThat(mBassClientStateMachine.mMsgWhats).contains(REMOVE_BCAST_SOURCE);

        mBassClientStateMachine.mIsPendingRemove = null;
        // set some values for covering more lines of processPASyncState()
        mBassClientStateMachine.mPendingMetadata = createBroadcastMetadata();
        for (int i = 0; i < BassConstants.BCAST_RCVR_STATE_SRC_ADDR_SIZE; ++i) {
            value[BassConstants.BCAST_RCVR_STATE_SRC_ADDR_START_IDX + i] = 0x00;
        }
        when(mBassClientService.getPeriodicAdvertisementResult(any(), anyInt())).thenReturn(null);
        when(mBassClientService.isLocalBroadcast(any(BluetoothLeBroadcastMetadata.class)))
                .thenReturn(true);
        when(characteristic.getValue()).thenReturn(value);
        mBassClientStateMachine.mPendingSourceToSwitch = mBassClientStateMachine.mPendingMetadata;

        Mockito.clearInvocations(callbacks);
        cb.onCharacteristicRead(null, characteristic, GATT_SUCCESS);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());

        verify(callbacks)
                .notifySourceRemoved(
                        any(), anyInt(), eq(BluetoothStatusCodes.REASON_LOCAL_STACK_REQUEST));
        verify(callbacks).notifyReceiveStateChanged(any(), anyInt(), any());
        assertThat(mBassClientStateMachine.mPendingSourceToSwitch).isEqualTo(null);
    }

    @Test
    public void gattCallbackOnCharacteristicChanged() {
        mBassClientStateMachine.connectGatt(true);
        BluetoothGattCallback cb = mBassClientStateMachine.mGattCallback;
        mBassClientStateMachine.mNumOfBroadcastReceiverStates = 1;
        BassClientService.Callbacks callbacks = Mockito.mock(BassClientService.Callbacks.class);
        when(mBassClientService.getCallbacks()).thenReturn(callbacks);

        BluetoothGattCharacteristic characteristic =
                Mockito.mock(BluetoothGattCharacteristic.class);
        when(characteristic.getUuid()).thenReturn(BassConstants.BASS_BCAST_RECEIVER_STATE);
        when(characteristic.getValue()).thenReturn(null);

        cb.onCharacteristicChanged(null, characteristic);
        verify(characteristic, atLeast(1)).getUuid();
        verify(characteristic).getValue();
        verify(callbacks, never()).notifyReceiveStateChanged(any(), anyInt(), any());
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());

        mBassClientStateMachine.mNumOfBroadcastReceiverStates = 1;
        Mockito.clearInvocations(characteristic);
        when(characteristic.getValue()).thenReturn(new byte[] { });
        cb.onCharacteristicChanged(null, characteristic);
        verify(characteristic, atLeast(1)).getUuid();
        verify(characteristic, atLeast(1)).getValue();
        verify(callbacks).notifyReceiveStateChanged(any(), anyInt(), any());
    }

    @Test
    public void gattCharacteristicWrite() {
        mBassClientStateMachine.connectGatt(true);
        BluetoothGattCallback cb = mBassClientStateMachine.mGattCallback;

        BluetoothGattCharacteristic characteristic =Mockito.mock(BluetoothGattCharacteristic.class);
        when(characteristic.getUuid()).thenReturn(BassConstants.BASS_BCAST_AUDIO_SCAN_CTRL_POINT);

        cb.onCharacteristicWrite(null, characteristic, GATT_SUCCESS);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        assertThat(mBassClientStateMachine.mMsgWhats).contains(GATT_TXN_PROCESSED);
    }

    @Test
    public void gattCallbackOnDescriptorWrite() {
        mBassClientStateMachine.connectGatt(true);
        BluetoothGattCallback cb = mBassClientStateMachine.mGattCallback;
        BluetoothGattDescriptor descriptor = Mockito.mock(BluetoothGattDescriptor.class);
        when(descriptor.getUuid()).thenReturn(BassConstants.CLIENT_CHARACTERISTIC_CONFIG);

        cb.onDescriptorWrite(null, descriptor, GATT_SUCCESS);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        assertThat(mBassClientStateMachine.mMsgWhats).contains(GATT_TXN_PROCESSED);
    }

    @Test
    public void gattCallbackOnMtuChanged() {
        mBassClientStateMachine.connectGatt(true);
        BluetoothGattCallback cb = mBassClientStateMachine.mGattCallback;
        mBassClientStateMachine.mMTUChangeRequested = true;

        cb.onMtuChanged(null, 10, GATT_SUCCESS);
        assertThat(mBassClientStateMachine.mMTUChangeRequested).isTrue();

        BassClientStateMachine.BluetoothGattTestableWrapper btGatt = Mockito.mock(
                BassClientStateMachine.BluetoothGattTestableWrapper.class);
        mBassClientStateMachine.mBluetoothGatt = btGatt;

        cb.onMtuChanged(null, 10, GATT_SUCCESS);
        assertThat(mBassClientStateMachine.mMTUChangeRequested).isFalse();
    }

    @Test
    public void sendConnectMessage_inDisconnectedState() {
        initToDisconnectedState();

        BassClientStateMachine.BluetoothGattTestableWrapper btGatt = Mockito.mock(
                BassClientStateMachine.BluetoothGattTestableWrapper.class);
        mBassClientStateMachine.mBluetoothGatt = btGatt;

        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(CONNECT),
                BassClientStateMachine.Connecting.class);
        verify(btGatt).disconnect();
        verify(btGatt).close();
    }

    @Test
    public void sendDisconnectMessage_inDisconnectedState() {
        initToDisconnectedState();

        BassClientStateMachine.BluetoothGattTestableWrapper btGatt = Mockito.mock(
                BassClientStateMachine.BluetoothGattTestableWrapper.class);
        mBassClientStateMachine.mBluetoothGatt = btGatt;

        mBassClientStateMachine.sendMessage(DISCONNECT);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        verify(btGatt).disconnect();
        verify(btGatt).close();
    }

    @Test
    public void sendStateChangedMessage_inDisconnectedState() {
        initToDisconnectedState();

        BassClientStateMachine.BluetoothGattTestableWrapper btGatt = Mockito.mock(
                BassClientStateMachine.BluetoothGattTestableWrapper.class);
        mBassClientStateMachine.mBluetoothGatt = btGatt;

        Message msgToConnectingState =
                mBassClientStateMachine.obtainMessage(CONNECTION_STATE_CHANGED);
        msgToConnectingState.obj = BluetoothProfile.STATE_CONNECTING;

        mBassClientStateMachine.sendMessage(msgToConnectingState);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        verify(mBassClientService, never()).sendBroadcast(any(Intent.class), anyString(), any());

        Message msgToConnectedState =
                mBassClientStateMachine.obtainMessage(CONNECTION_STATE_CHANGED);
        msgToConnectedState.obj = BluetoothProfile.STATE_CONNECTED;
        sendMessageAndVerifyTransition(msgToConnectedState, BassClientStateMachine.Connected.class);
    }

    @Test
    public void sendOtherMessages_inDisconnectedState_doesNotChangeState() {
        initToDisconnectedState();

        // need this to ensure expected mock behavior for getActiveSyncedSource
        when(mBassClientService.getActiveSyncedSources(any())).thenReturn(null);

        mBassClientStateMachine.sendMessage(PSYNC_ACTIVE_TIMEOUT);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        verify(mBassClientService, never()).sendBroadcast(any(Intent.class), anyString(), any());

        mBassClientStateMachine.sendMessage(-1);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        verify(mBassClientService, never()).sendBroadcast(any(Intent.class), anyString(), any());
    }

    @Test
    public void sendConnectMessages_inConnectingState_doesNotChangeState() {
        initToConnectingState();

        mBassClientStateMachine.sendMessage(CONNECT);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        verify(mBassClientService, never()).sendBroadcast(any(Intent.class), anyString(), any());
    }

    @Test
    public void sendDisconnectMessages_inConnectingState_defersMessage() {
        initToConnectingState();

        mBassClientStateMachine.sendMessage(DISCONNECT);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        assertThat(mBassClientStateMachine.hasDeferredMessagesSuper(DISCONNECT)).isTrue();
    }

    @Test
    public void sendReadBassCharacteristicsMessage_inConnectingState_defersMessage() {
        initToConnectingState();

        mBassClientStateMachine.sendMessage(READ_BASS_CHARACTERISTICS);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        assertThat(mBassClientStateMachine.hasDeferredMessagesSuper(READ_BASS_CHARACTERISTICS))
                .isTrue();
    }

    @Test
    public void sendPsyncActiveTimeoutMessage_inConnectingState_defersMessage() {
        initToConnectingState();

        mBassClientStateMachine.sendMessage(PSYNC_ACTIVE_TIMEOUT);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        assertThat(mBassClientStateMachine.hasDeferredMessagesSuper(PSYNC_ACTIVE_TIMEOUT)).isTrue();
    }

    @Test
    public void sendStateChangedToNonConnectedMessage_inConnectingState_movesToDisconnected() {
        initToConnectingState();

        Message msg = mBassClientStateMachine.obtainMessage(CONNECTION_STATE_CHANGED);
        msg.obj = BluetoothProfile.STATE_CONNECTING;
        BassClientStateMachine.BluetoothGattTestableWrapper btGatt =
                Mockito.mock(BassClientStateMachine.BluetoothGattTestableWrapper.class);
        mBassClientStateMachine.mBluetoothGatt = btGatt;
        sendMessageAndVerifyTransition(msg, BassClientStateMachine.Disconnected.class);
        verify(btGatt).close();
        assertNull(mBassClientStateMachine.mBluetoothGatt);
    }

    @Test
    public void sendStateChangedToConnectedMessage_inConnectingState_movesToConnected() {
        initToConnectingState();

        Message msg = mBassClientStateMachine.obtainMessage(CONNECTION_STATE_CHANGED);
        msg.obj = BluetoothProfile.STATE_CONNECTED;
        sendMessageAndVerifyTransition(msg, BassClientStateMachine.Connected.class);
    }

    @Test
    public void sendConnectTimeMessage_inConnectingState() {
        initToConnectingState();

        Message timeoutWithDifferentDevice = mBassClientStateMachine.obtainMessage(CONNECT_TIMEOUT,
                mAdapter.getRemoteDevice("00:00:00:00:00:00"));
        mBassClientStateMachine.sendMessage(timeoutWithDifferentDevice);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        verify(mBassClientService, never()).sendBroadcast(any(Intent.class), anyString(), any());

        BassClientStateMachine.BluetoothGattTestableWrapper btGatt =
                Mockito.mock(BassClientStateMachine.BluetoothGattTestableWrapper.class);
        mBassClientStateMachine.mBluetoothGatt = btGatt;
        Message msg = mBassClientStateMachine.obtainMessage(CONNECT_TIMEOUT, mTestDevice);
        sendMessageAndVerifyTransition(msg, BassClientStateMachine.Disconnected.class);
        verify(btGatt).close();
        assertNull(mBassClientStateMachine.mBluetoothGatt);
    }

    @Test
    public void sendInvalidMessage_inConnectingState_doesNotChangeState() {
        initToConnectingState();
        mBassClientStateMachine.sendMessage(-1);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        verify(mBassClientService, never()).sendBroadcast(any(Intent.class), anyString(), any());
    }

    @Test
    public void sendConnectMessage_inConnectedState() {
        initToConnectedState();

        mBassClientStateMachine.sendMessage(CONNECT);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        verify(mBassClientService, never()).sendBroadcast(any(Intent.class), anyString(), any());
    }

    @Test
    public void sendDisconnectMessage_inConnectedState() {
        initToConnectedState();

        mBassClientStateMachine.mBluetoothGatt = null;
        // need this to ensure expected mock behavior for getActiveSyncedSource
        when(mBassClientService.getActiveSyncedSources(any())).thenReturn(null);

        mBassClientStateMachine.sendMessage(DISCONNECT);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        verify(mBassClientService, never()).sendBroadcast(any(Intent.class), anyString(), any());

        BassClientStateMachine.BluetoothGattTestableWrapper btGatt = Mockito.mock(
                BassClientStateMachine.BluetoothGattTestableWrapper.class);
        mBassClientStateMachine.mBluetoothGatt = btGatt;
        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(DISCONNECT),
                BassClientStateMachine.Disconnected.class);
        verify(btGatt).disconnect();
        verify(btGatt).close();
    }

    @Test
    public void sendStateChangedMessage_inConnectedState() {
        initToConnectedState();

        Message connectedMsg = mBassClientStateMachine.obtainMessage(CONNECTION_STATE_CHANGED);
        connectedMsg.obj = BluetoothProfile.STATE_CONNECTED;

        // need this to ensure expected mock behavior for getActiveSyncedSource
        when(mBassClientService.getActiveSyncedSources(any())).thenReturn(null);

        mBassClientStateMachine.sendMessage(connectedMsg);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        verify(mBassClientService, never()).sendBroadcast(any(Intent.class), anyString(), any());

        BassClientStateMachine.BluetoothGattTestableWrapper btGatt =
                Mockito.mock(BassClientStateMachine.BluetoothGattTestableWrapper.class);
        mBassClientStateMachine.mBluetoothGatt = btGatt;
        Message noneConnectedMsg = mBassClientStateMachine.obtainMessage(CONNECTION_STATE_CHANGED);
        noneConnectedMsg.obj = BluetoothProfile.STATE_DISCONNECTING;
        sendMessageAndVerifyTransition(noneConnectedMsg, BassClientStateMachine.Disconnected.class);
        verify(btGatt).close();
        assertNull(mBassClientStateMachine.mBluetoothGatt);
    }

    @Test
    public void sendReadBassCharacteristicsMessage_inConnectedState() {
        initToConnectedState();
        BluetoothGattCharacteristic gattCharacteristic = Mockito.mock(
                BluetoothGattCharacteristic.class);

        mBassClientStateMachine.sendMessage(READ_BASS_CHARACTERISTICS, gattCharacteristic);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        verify(mBassClientService, never()).sendBroadcast(any(Intent.class), anyString(), any());

        BassClientStateMachine.BluetoothGattTestableWrapper btGatt = Mockito.mock(
                BassClientStateMachine.BluetoothGattTestableWrapper.class);
        mBassClientStateMachine.mBluetoothGatt = btGatt;
        sendMessageAndVerifyTransition(mBassClientStateMachine.obtainMessage(
                READ_BASS_CHARACTERISTICS, gattCharacteristic),
                BassClientStateMachine.ConnectedProcessing.class);
    }

    @Test
    public void sendStartScanOffloadMessage_inConnectedState() {
        initToConnectedState();
        BassClientStateMachine.BluetoothGattTestableWrapper btGatt = Mockito.mock(
                BassClientStateMachine.BluetoothGattTestableWrapper.class);
        mBassClientStateMachine.mBluetoothGatt = btGatt;

        mBassClientStateMachine.sendMessage(START_SCAN_OFFLOAD);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        verify(mBassClientService, never()).sendBroadcast(any(Intent.class), anyString(), any());

        BluetoothGattCharacteristic scanControlPoint = Mockito.mock(
                BluetoothGattCharacteristic.class);
        mBassClientStateMachine.mBroadcastScanControlPoint = scanControlPoint;

        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(START_SCAN_OFFLOAD),
                BassClientStateMachine.ConnectedProcessing.class);
        verify(btGatt).writeCharacteristic(scanControlPoint);
        verify(scanControlPoint).setValue(REMOTE_SCAN_START);
    }

    @Test
    public void sendStopScanOffloadMessage_inConnectedState() {
        initToConnectedState();
        BassClientStateMachine.BluetoothGattTestableWrapper btGatt = Mockito.mock(
                BassClientStateMachine.BluetoothGattTestableWrapper.class);
        mBassClientStateMachine.mBluetoothGatt = btGatt;

        mBassClientStateMachine.sendMessage(STOP_SCAN_OFFLOAD);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        verify(mBassClientService, never()).sendBroadcast(any(Intent.class), anyString(), any());

        BluetoothGattCharacteristic scanControlPoint = Mockito.mock(
                BluetoothGattCharacteristic.class);
        mBassClientStateMachine.mBroadcastScanControlPoint = scanControlPoint;

        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(STOP_SCAN_OFFLOAD),
                BassClientStateMachine.ConnectedProcessing.class);
        verify(btGatt).writeCharacteristic(scanControlPoint);
        verify(scanControlPoint).setValue(REMOTE_SCAN_STOP);
    }

    @Test
    public void sendPsyncActiveMessage_inConnectedState() {
        initToConnectedState();
        // need this to ensure expected mock behavior for getActiveSyncedSource
        when(mBassClientService.getActiveSyncedSources(any())).thenReturn(null);

        mBassClientStateMachine.sendMessage(PSYNC_ACTIVE_TIMEOUT);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        // verify getActiveSyncedSource got called in CancelActiveSync
        verify(mBassClientService).getActiveSyncedSources(any());
    }

    @Test
    public void sendInvalidMessage_inConnectedState_doesNotChangeState() {
        initToConnectedState();

        mBassClientStateMachine.sendMessage(-1);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        verify(mBassClientService, never()).sendBroadcast(any(Intent.class), anyString(), any());
    }

    @Test
    public void sendSelectBcastSourceMessage_inConnectedState() {
        initToConnectedState();

        byte[] scanRecord = new byte[]{
                0x02, 0x01, 0x1a, // advertising flags
                0x05, 0x02, 0x52, 0x18, 0x0a, 0x11, // 16 bit service uuids
                0x04, 0x09, 0x50, 0x65, 0x64, // name
                0x02, 0x0A, (byte) 0xec, // tx power level
                0x05, 0x30, 0x54, 0x65, 0x73, 0x74, // broadcast name: Test
                0x06, 0x16, 0x52, 0x18, 0x50, 0x64, 0x65, // service data
                0x08, 0x16, 0x56, 0x18, 0x07, 0x03, 0x06, 0x07, 0x08,
                // service data - public broadcast,
                // feature - 0x7, metadata len - 0x3, metadata - 0x6, 0x7, 0x8
                0x05, (byte) 0xff, (byte) 0xe0, 0x00, 0x02, 0x15, // manufacturer specific data
                0x03, 0x50, 0x01, 0x02, // an unknown data type won't cause trouble
        };
        ScanRecord record = ScanRecord.parseFromBytes(scanRecord);

        doNothing().when(mMethodProxy).periodicAdvertisingManagerRegisterSync(
                any(), any(), anyInt(), anyInt(), any(), any());
        ScanResult scanResult = new ScanResult(mTestDevice, 0, 0, 0, 0, 0, 0, 0, record, 0);
        mBassClientStateMachine.sendMessage(
                SELECT_BCAST_SOURCE, BassConstants.AUTO, 0, scanResult);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        verify(mBassClientService).updatePeriodicAdvertisementResultMap(
                any(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                any(), eq(TEST_BROADCAST_NAME));
    }

    @Test
    public void sendAddBcastSourceMessage_inConnectedState() {
        initToConnectedState();

        BassClientService.Callbacks callbacks = Mockito.mock(BassClientService.Callbacks.class);
        when(mBassClientService.getCallbacks()).thenReturn(callbacks);

        BluetoothLeBroadcastMetadata metadata = createBroadcastMetadata();
        // verify local broadcast doesn't require active synced source
        when(mBassClientService.isLocalBroadcast(any(BluetoothLeBroadcastMetadata.class)))
                .thenReturn(true);
        mBassClientStateMachine.sendMessage(ADD_BCAST_SOURCE, metadata);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());

        verify(mBassClientService).getCallbacks();
        verify(callbacks).notifySourceAddFailed(any(), any(), anyInt());

        BassClientStateMachine.BluetoothGattTestableWrapper btGatt = Mockito.mock(
                BassClientStateMachine.BluetoothGattTestableWrapper.class);
        mBassClientStateMachine.mBluetoothGatt = btGatt;
        BluetoothGattCharacteristic scanControlPoint = Mockito.mock(
                BluetoothGattCharacteristic.class);
        mBassClientStateMachine.mBroadcastScanControlPoint = scanControlPoint;

        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(ADD_BCAST_SOURCE, metadata),
                BassClientStateMachine.ConnectedProcessing.class);
        verify(scanControlPoint).setValue(any(byte[].class));
        verify(btGatt).writeCharacteristic(any());
    }

    @Test
    public void sendReachedMaxSourceLimitMessage_inConnectedState() {
        initToConnectedState();
        // need this to ensure expected mock behavior for getActiveSyncedSource
        when(mBassClientService.getActiveSyncedSources(any())).thenReturn(null);

        mBassClientStateMachine.sendMessage(REACHED_MAX_SOURCE_LIMIT);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        // verify getActiveSyncedSource got called in CancelActiveSync
        verify(mBassClientService).getActiveSyncedSources(any());
    }

    @Test
    public void sendSwitchSourceMessage_inConnectedState() {
        initToConnectedState();
        BluetoothLeBroadcastMetadata metadata = createBroadcastMetadata();
        Integer sourceId = 1;

        BassClientStateMachine.BluetoothGattTestableWrapper btGatt =
                Mockito.mock(BassClientStateMachine.BluetoothGattTestableWrapper.class);
        mBassClientStateMachine.mBluetoothGatt = btGatt;
        BluetoothGattCharacteristic scanControlPoint =
                Mockito.mock(BluetoothGattCharacteristic.class);
        mBassClientStateMachine.mBroadcastScanControlPoint = scanControlPoint;

        mBassClientStateMachine.sendMessage(SWITCH_BCAST_SOURCE, sourceId, 0, metadata);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        assertThat(mBassClientStateMachine.mPendingSourceToSwitch).isEqualTo(metadata);
    }

    @Test
    public void sendUpdateBcastSourceMessage_inConnectedState() {
        initToConnectedState();
        mBassClientStateMachine.connectGatt(true);
        mBassClientStateMachine.mNumOfBroadcastReceiverStates = 2;

        // Prepare mBluetoothLeBroadcastReceiveStates for test
        BassClientService.Callbacks callbacks = Mockito.mock(BassClientService.Callbacks.class);
        when(mBassClientService.getCallbacks()).thenReturn(callbacks);
        int sourceId = 1;
        int paSync = BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE;
        byte[] value =
                new byte[] {
                    (byte) sourceId, // sourceId
                    (byte) (mSourceTestDevice.getAddressType() & 0xFF), // sourceAddressType
                    Utils.getByteAddress(mSourceTestDevice)[5],
                    Utils.getByteAddress(mSourceTestDevice)[4],
                    Utils.getByteAddress(mSourceTestDevice)[3],
                    Utils.getByteAddress(mSourceTestDevice)[2],
                    Utils.getByteAddress(mSourceTestDevice)[1],
                    Utils.getByteAddress(mSourceTestDevice)[0], // sourceAddress
                    0x00, // sourceAdvSid
                    0x00,
                    0x00,
                    0x00, // broadcastIdBytes
                    (byte) paSync,
                    (byte) BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_BAD_CODE,
                    // 16 bytes badBroadcastCode
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x01, // numSubGroups
                    // SubGroup #1
                    0x00,
                    0x00,
                    0x00,
                    0x00, // audioSyncIndex
                    0x02, // metaDataLength
                    0x00,
                    0x00, // metadata
                };
        BluetoothGattCharacteristic characteristic =
                Mockito.mock(BluetoothGattCharacteristic.class);
        when(characteristic.getValue()).thenReturn(value);
        when(characteristic.getInstanceId()).thenReturn(sourceId);
        when(characteristic.getUuid()).thenReturn(BassConstants.BASS_BCAST_RECEIVER_STATE);
        mBassClientStateMachine.mGattCallback.onCharacteristicRead(
                null, characteristic, GATT_SUCCESS);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());

        BluetoothLeBroadcastMetadata metadata = createBroadcastMetadata();
        when(mBassClientService.getPeriodicAdvertisementResult(any(), anyInt())).thenReturn(null);

        mBassClientStateMachine.sendMessage(UPDATE_BCAST_SOURCE, sourceId, paSync, metadata);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());

        PeriodicAdvertisementResult paResult = Mockito.mock(PeriodicAdvertisementResult.class);
        when(mBassClientService.getPeriodicAdvertisementResult(any(), anyInt()))
                .thenReturn(paResult);
        when(mBassClientService.getBase(anyInt())).thenReturn(null);
        Mockito.clearInvocations(callbacks);

        mBassClientStateMachine.sendMessage(UPDATE_BCAST_SOURCE, sourceId, paSync, metadata);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());

        BaseData data = Mockito.mock(BaseData.class);
        when(mBassClientService.getBase(anyInt())).thenReturn(data);
        when(data.getNumberOfSubgroupsofBIG()).thenReturn((byte) 1);
        Mockito.clearInvocations(callbacks);

        mBassClientStateMachine.sendMessage(UPDATE_BCAST_SOURCE, sourceId, paSync, metadata);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        verify(callbacks).notifySourceModifyFailed(any(), anyInt(), anyInt());

        BassClientStateMachine.BluetoothGattTestableWrapper btGatt = Mockito.mock(
                BassClientStateMachine.BluetoothGattTestableWrapper.class);
        BluetoothGattCharacteristic scanControlPoint = Mockito.mock(
                BluetoothGattCharacteristic.class);
        mBassClientStateMachine.mBluetoothGatt = btGatt;
        mBassClientStateMachine.mBroadcastScanControlPoint = scanControlPoint;
        mBassClientStateMachine.mPendingOperation = 0;
        mBassClientStateMachine.mPendingSourceId = 0;
        mBassClientStateMachine.mPendingMetadata = null;
        Mockito.clearInvocations(callbacks);

        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(
                        UPDATE_BCAST_SOURCE, sourceId, paSync, metadata),
                BassClientStateMachine.ConnectedProcessing.class);
        assertThat(mBassClientStateMachine.mPendingOperation).isEqualTo(UPDATE_BCAST_SOURCE);
        assertThat(mBassClientStateMachine.mPendingSourceId).isEqualTo(sourceId);
        assertThat(mBassClientStateMachine.mPendingMetadata).isEqualTo(metadata);
    }

    @Test
    public void sendSetBcastCodeMessage_inConnectedState() {
        initToConnectedState();
        mBassClientStateMachine.connectGatt(true);
        mBassClientStateMachine.mNumOfBroadcastReceiverStates = 2;
        BassClientService.Callbacks callbacks = Mockito.mock(BassClientService.Callbacks.class);
        when(mBassClientService.getCallbacks()).thenReturn(callbacks);

        // Prepare mBluetoothLeBroadcastReceiveStates with metadata for test
        mBassClientStateMachine.mShouldHandleMessage = false;
        int sourceId = 1;
        byte[] value =
                new byte[] {
                    (byte) sourceId, // sourceId
                    (byte) (mSourceTestDevice.getAddressType() & 0xFF), // sourceAddressType
                    Utils.getByteAddress(mSourceTestDevice)[5],
                    Utils.getByteAddress(mSourceTestDevice)[4],
                    Utils.getByteAddress(mSourceTestDevice)[3],
                    Utils.getByteAddress(mSourceTestDevice)[2],
                    Utils.getByteAddress(mSourceTestDevice)[1],
                    Utils.getByteAddress(mSourceTestDevice)[0], // sourceAddress
                    0x00, // sourceAdvSid
                    0x00,
                    0x00,
                    0x00, // broadcastIdBytes
                    (byte) BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                    (byte) BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_CODE_REQUIRED,
                    // 16 bytes badBroadcastCode
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x01, // numSubGroups
                    // SubGroup #1
                    0x00,
                    0x00,
                    0x00,
                    0x00, // audioSyncIndex
                    0x02, // metaDataLength
                    0x00,
                    0x00, // metadata
                };
        mBassClientStateMachine.mPendingOperation = REMOVE_BCAST_SOURCE;
        mBassClientStateMachine.mPendingSourceId = (byte) sourceId;
        BluetoothGattCharacteristic characteristic =
                Mockito.mock(BluetoothGattCharacteristic.class);
        when(characteristic.getValue()).thenReturn(value);
        when(characteristic.getInstanceId()).thenReturn(sourceId);
        when(characteristic.getUuid()).thenReturn(BassConstants.BASS_BCAST_RECEIVER_STATE);

        mBassClientStateMachine.mGattCallback.onCharacteristicRead(
                null, characteristic, GATT_SUCCESS);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());

        mBassClientStateMachine.mPendingMetadata = createBroadcastMetadata();
        mBassClientStateMachine.mGattCallback.onCharacteristicRead(
                null, characteristic, GATT_SUCCESS);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        mBassClientStateMachine.mShouldHandleMessage = true;

        BluetoothLeBroadcastReceiveState recvState = new BluetoothLeBroadcastReceiveState(
                2,
                BluetoothDevice.ADDRESS_TYPE_PUBLIC,
                mAdapter.getRemoteLeDevice("00:00:00:00:00:00",
                        BluetoothDevice.ADDRESS_TYPE_PUBLIC),
                0,
                0,
                BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_CODE_REQUIRED,
                null,
                0,
                Arrays.asList(new Long[0]),
                Arrays.asList(new BluetoothLeAudioContentMetadata[0])
        );
        mBassClientStateMachine.mSetBroadcastCodePending = false;
        mBassClientStateMachine.sendMessage(SET_BCAST_CODE, recvState);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        assertThat(mBassClientStateMachine.mSetBroadcastCodePending).isTrue();

        recvState = new BluetoothLeBroadcastReceiveState(
                sourceId,
                BluetoothDevice.ADDRESS_TYPE_PUBLIC,
                mAdapter.getRemoteLeDevice("00:00:00:00:00:00",
                        BluetoothDevice.ADDRESS_TYPE_PUBLIC),
                0,
                0,
                BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_CODE_REQUIRED,
                null,
                0,
                Arrays.asList(new Long[0]),
                Arrays.asList(new BluetoothLeAudioContentMetadata[0])
        );
        mBassClientStateMachine.sendMessage(SET_BCAST_CODE, recvState);
        BassClientStateMachine.BluetoothGattTestableWrapper btGatt = Mockito.mock(
                BassClientStateMachine.BluetoothGattTestableWrapper.class);
        mBassClientStateMachine.mBluetoothGatt = btGatt;
        BluetoothGattCharacteristic scanControlPoint = Mockito.mock(
                BluetoothGattCharacteristic.class);
        mBassClientStateMachine.mBroadcastScanControlPoint = scanControlPoint;

        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(SET_BCAST_CODE, recvState),
                BassClientStateMachine.ConnectedProcessing.class);
        assertThat(mBassClientStateMachine.mPendingOperation).isEqualTo(SET_BCAST_CODE);
        assertThat(mBassClientStateMachine.mPendingSourceId).isEqualTo(sourceId);
        verify(btGatt).writeCharacteristic(any());
        verify(scanControlPoint).setValue(any(byte[].class));
    }

    @Test
    public void sendRemoveBcastSourceMessage_inConnectedState() {
        initToConnectedState();
        BassClientService.Callbacks callbacks = Mockito.mock(BassClientService.Callbacks.class);
        when(mBassClientService.getCallbacks()).thenReturn(callbacks);

        int sid = 10;
        mBassClientStateMachine.sendMessage(REMOVE_BCAST_SOURCE, sid);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        verify(callbacks).notifySourceRemoveFailed(any(), anyInt(), anyInt());

        BassClientStateMachine.BluetoothGattTestableWrapper btGatt = Mockito.mock(
                BassClientStateMachine.BluetoothGattTestableWrapper.class);
        mBassClientStateMachine.mBluetoothGatt = btGatt;
        BluetoothGattCharacteristic scanControlPoint = Mockito.mock(
                BluetoothGattCharacteristic.class);
        mBassClientStateMachine.mBroadcastScanControlPoint = scanControlPoint;

        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(REMOVE_BCAST_SOURCE, sid),
                BassClientStateMachine.ConnectedProcessing.class);
        verify(scanControlPoint).setValue(any(byte[].class));
        verify(btGatt).writeCharacteristic(any());
        assertThat(mBassClientStateMachine.mPendingOperation).isEqualTo(REMOVE_BCAST_SOURCE);
        assertThat(mBassClientStateMachine.mPendingSourceId).isEqualTo(sid);
    }

    @Test
    public void sendConnectMessage_inConnectedProcessingState_doesNotChangeState() {
        initToConnectedProcessingState();

        mBassClientStateMachine.sendMessage(CONNECT);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        verify(mBassClientService, never()).sendBroadcast(any(Intent.class), anyString(), any());
    }

    @Test
    public void sendDisconnectMessage_inConnectedProcessingState_doesNotChangeState() {
        initToConnectedProcessingState();

        // Mock instance of btGatt was created in initToConnectedProcessingState().
        BassClientStateMachine.BluetoothGattTestableWrapper btGatt =
                mBassClientStateMachine.mBluetoothGatt;
        // need this to ensure expected mock behavior for getActiveSyncedSource
        when(mBassClientService.getActiveSyncedSources(any())).thenReturn(null);

        mBassClientStateMachine.mBluetoothGatt = null;
        mBassClientStateMachine.sendMessage(DISCONNECT);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        verify(mBassClientService, never()).sendBroadcast(any(Intent.class), anyString(), any());

        mBassClientStateMachine.mBluetoothGatt = btGatt;
        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(DISCONNECT),
                BassClientStateMachine.Disconnected.class);
        verify(btGatt).disconnect();
        verify(btGatt).close();
    }

    @Test
    public void sendStateChangedMessage_inConnectedProcessingState() {
        initToConnectedProcessingState();

        Message msgToConnectedState =
                mBassClientStateMachine.obtainMessage(CONNECTION_STATE_CHANGED);
        msgToConnectedState.obj = BluetoothProfile.STATE_CONNECTED;

        // need this to ensure expected mock behavior for getActiveSyncedSource
        when(mBassClientService.getActiveSyncedSources(any())).thenReturn(null);

        mBassClientStateMachine.sendMessage(msgToConnectedState);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        verify(mBassClientService, never()).sendBroadcast(any(Intent.class), anyString(), any());

        BassClientStateMachine.BluetoothGattTestableWrapper btGatt =
                Mockito.mock(BassClientStateMachine.BluetoothGattTestableWrapper.class);
        mBassClientStateMachine.mBluetoothGatt = btGatt;
        Message msgToNoneConnectedState =
                mBassClientStateMachine.obtainMessage(CONNECTION_STATE_CHANGED);
        msgToNoneConnectedState.obj = BluetoothProfile.STATE_DISCONNECTING;
        sendMessageAndVerifyTransition(
                msgToNoneConnectedState, BassClientStateMachine.Disconnected.class);
        verify(btGatt).close();
        assertNull(mBassClientStateMachine.mBluetoothGatt);
    }

    /**
     * This also tests BassClientStateMachine#sendPendingCallbacks
     */
    @Test
    public void sendGattTxnProcessedMessage_inConnectedProcessingState() {
        initToConnectedProcessingState();
        BassClientService.Callbacks callbacks = Mockito.mock(BassClientService.Callbacks.class);
        when(mBassClientService.getCallbacks()).thenReturn(callbacks);
        // need this to ensure expected mock behavior for getActiveSyncedSource
        when(mBassClientService.getActiveSyncedSources(any())).thenReturn(null);

        // Test sendPendingCallbacks(START_SCAN_OFFLOAD, ERROR_UNKNOWN)
        mBassClientStateMachine.mPendingOperation = START_SCAN_OFFLOAD;
        mBassClientStateMachine.mAutoTriggered = false;
        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(GATT_TXN_PROCESSED, GATT_FAILURE),
                BassClientStateMachine.Connected.class);
        // verify getActiveSyncedSource got called in CancelActiveSync
        verify(mBassClientService).getActiveSyncedSources(any());

        // Test sendPendingCallbacks(START_SCAN_OFFLOAD, ERROR_UNKNOWN)
        moveConnectedStateToConnectedProcessingState();
        mBassClientStateMachine.mPendingOperation = START_SCAN_OFFLOAD;
        mBassClientStateMachine.mAutoTriggered = true;
        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(GATT_TXN_PROCESSED, GATT_FAILURE),
                BassClientStateMachine.Connected.class);
        assertThat(mBassClientStateMachine.mAutoTriggered).isFalse();

        // Test sendPendingCallbacks(ADD_BCAST_SOURCE, ERROR_UNKNOWN)
        moveConnectedStateToConnectedProcessingState();
        mBassClientStateMachine.mPendingMetadata = createBroadcastMetadata();
        mBassClientStateMachine.mPendingOperation = ADD_BCAST_SOURCE;
        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(GATT_TXN_PROCESSED, GATT_FAILURE),
                BassClientStateMachine.Connected.class);
        verify(callbacks).notifySourceAddFailed(any(), any(), anyInt());

        // Test sendPendingCallbacks(UPDATE_BCAST_SOURCE, REASON_LOCAL_APP_REQUEST)
        moveConnectedStateToConnectedProcessingState();
        mBassClientStateMachine.mPendingOperation = UPDATE_BCAST_SOURCE;
        mBassClientStateMachine.mAutoTriggered = true;
        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(GATT_TXN_PROCESSED, GATT_SUCCESS),
                BassClientStateMachine.Connected.class);
        assertThat(mBassClientStateMachine.mAutoTriggered).isFalse();

        // Test sendPendingCallbacks(UPDATE_BCAST_SOURCE, ERROR_UNKNOWN)
        moveConnectedStateToConnectedProcessingState();
        mBassClientStateMachine.mPendingOperation = UPDATE_BCAST_SOURCE;
        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(GATT_TXN_PROCESSED, GATT_FAILURE),
                BassClientStateMachine.Connected.class);
        verify(callbacks).notifySourceModifyFailed(any(), anyInt(), anyInt());

        // Test sendPendingCallbacks(REMOVE_BCAST_SOURCE, ERROR_UNKNOWN)
        moveConnectedStateToConnectedProcessingState();
        mBassClientStateMachine.mPendingOperation = REMOVE_BCAST_SOURCE;
        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(GATT_TXN_PROCESSED, GATT_FAILURE),
                BassClientStateMachine.Connected.class);
        verify(callbacks).notifySourceRemoveFailed(any(), anyInt(), anyInt());

        // Test sendPendingCallbacks(SET_BCAST_CODE, REASON_LOCAL_APP_REQUEST)
        moveConnectedStateToConnectedProcessingState();
        mBassClientStateMachine.mPendingOperation = REMOVE_BCAST_SOURCE;
        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(GATT_TXN_PROCESSED, GATT_FAILURE),
                BassClientStateMachine.Connected.class);
        // Nothing to verify more

        // Test sendPendingCallbacks(SET_BCAST_CODE, REASON_LOCAL_APP_REQUEST)
        moveConnectedStateToConnectedProcessingState();
        mBassClientStateMachine.mPendingOperation = -1;
        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(GATT_TXN_PROCESSED, GATT_FAILURE),
                BassClientStateMachine.Connected.class);
        // Nothing to verify more
    }

    @Test
    public void sendGattTxnTimeoutMessage_inConnectedProcessingState_doesNotChangeState() {
        initToConnectedProcessingState();

        mBassClientStateMachine.mPendingOperation = SET_BCAST_CODE;
        mBassClientStateMachine.mPendingSourceId = 0;
        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(GATT_TXN_TIMEOUT, GATT_FAILURE),
                BassClientStateMachine.Connected.class);
        assertThat(mBassClientStateMachine.mPendingOperation).isEqualTo(-1);
        assertThat(mBassClientStateMachine.mPendingSourceId).isEqualTo(-1);
    }

    @Test
    public void sendMessageForDeferring_inConnectedProcessingState_defersMessage() {
        initToConnectedProcessingState();

        mBassClientStateMachine.sendMessage(READ_BASS_CHARACTERISTICS);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        assertThat(mBassClientStateMachine.hasDeferredMessagesSuper(READ_BASS_CHARACTERISTICS))
                .isTrue();

        mBassClientStateMachine.sendMessage(START_SCAN_OFFLOAD);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        assertThat(mBassClientStateMachine.hasDeferredMessagesSuper(START_SCAN_OFFLOAD))
                .isTrue();

        mBassClientStateMachine.sendMessage(STOP_SCAN_OFFLOAD);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        assertThat(mBassClientStateMachine.hasDeferredMessagesSuper(STOP_SCAN_OFFLOAD))
                .isTrue();

        mBassClientStateMachine.sendMessage(SELECT_BCAST_SOURCE);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        assertThat(mBassClientStateMachine.hasDeferredMessagesSuper(SELECT_BCAST_SOURCE))
                .isTrue();

        mBassClientStateMachine.sendMessage(ADD_BCAST_SOURCE);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        assertThat(mBassClientStateMachine.hasDeferredMessagesSuper(ADD_BCAST_SOURCE))
                .isTrue();

        mBassClientStateMachine.sendMessage(SET_BCAST_CODE);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        assertThat(mBassClientStateMachine.hasDeferredMessagesSuper(SET_BCAST_CODE))
                .isTrue();

        mBassClientStateMachine.sendMessage(REMOVE_BCAST_SOURCE);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        assertThat(mBassClientStateMachine.hasDeferredMessagesSuper(REMOVE_BCAST_SOURCE))
                .isTrue();

        mBassClientStateMachine.sendMessage(PSYNC_ACTIVE_TIMEOUT);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        assertThat(mBassClientStateMachine.hasDeferredMessagesSuper(PSYNC_ACTIVE_TIMEOUT))
                .isTrue();

        mBassClientStateMachine.sendMessage(REACHED_MAX_SOURCE_LIMIT);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        assertThat(mBassClientStateMachine.hasDeferredMessagesSuper(REACHED_MAX_SOURCE_LIMIT))
                .isTrue();

        mBassClientStateMachine.sendMessage(SWITCH_BCAST_SOURCE);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        assertThat(mBassClientStateMachine.hasDeferredMessagesSuper(SWITCH_BCAST_SOURCE)).isTrue();
    }

    @Test
    public void sendInvalidMessage_inConnectedProcessingState_doesNotChangeState() {
        initToConnectedProcessingState();

        mBassClientStateMachine.sendMessage(-1);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        verify(mBassClientService, never()).sendBroadcast(any(Intent.class), anyString(), any());
    }

    @Test
    public void dump_doesNotCrash() {
        mBassClientStateMachine.dump(new StringBuilder());
    }

    @Test
    public void sendAddBcastSourceMessage_NoResponseWrite() {
        mBassClientStateMachine.connectGatt(true);
        BluetoothGattCallback cb = mBassClientStateMachine.mGattCallback;
        cb.onMtuChanged(null, 250, GATT_SUCCESS);
        initToConnectedState();

        BassClientService.Callbacks callbacks = Mockito.mock(BassClientService.Callbacks.class);
        when(mBassClientService.getCallbacks()).thenReturn(callbacks);

        BluetoothLeBroadcastMetadata metadata = createBroadcastMetadata();
        // verify local broadcast doesn't require active synced source
        when(mBassClientService.isLocalBroadcast(any(BluetoothLeBroadcastMetadata.class)))
                .thenReturn(true);
        mBassClientStateMachine.sendMessage(ADD_BCAST_SOURCE, metadata);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());

        verify(mBassClientService).getCallbacks();
        verify(callbacks).notifySourceAddFailed(any(), any(), anyInt());

        BassClientStateMachine.BluetoothGattTestableWrapper btGatt =
                Mockito.mock(BassClientStateMachine.BluetoothGattTestableWrapper.class);
        mBassClientStateMachine.mBluetoothGatt = btGatt;
        BluetoothGattCharacteristic scanControlPoint =
                Mockito.mock(BluetoothGattCharacteristic.class);
        mBassClientStateMachine.mBroadcastScanControlPoint = scanControlPoint;

        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(ADD_BCAST_SOURCE, metadata),
                BassClientStateMachine.ConnectedProcessing.class);
        verify(scanControlPoint).setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        verify(scanControlPoint).setValue(any(byte[].class));
        verify(btGatt).writeCharacteristic(any());
    }

    @Test
    public void sendAddBcastSourceMessage_LongWrite() {
        mBassClientStateMachine.connectGatt(true);
        BluetoothGattCallback cb = mBassClientStateMachine.mGattCallback;
        cb.onMtuChanged(null, 23, GATT_SUCCESS);
        initToConnectedState();

        BassClientService.Callbacks callbacks = Mockito.mock(BassClientService.Callbacks.class);
        when(mBassClientService.getCallbacks()).thenReturn(callbacks);

        BluetoothLeBroadcastMetadata metadata = createBroadcastMetadata();
        // verify local broadcast doesn't require active synced source
        when(mBassClientService.isLocalBroadcast(any(BluetoothLeBroadcastMetadata.class)))
                .thenReturn(true);
        mBassClientStateMachine.sendMessage(ADD_BCAST_SOURCE, metadata);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());

        verify(mBassClientService).getCallbacks();
        verify(callbacks).notifySourceAddFailed(any(), any(), anyInt());

        BassClientStateMachine.BluetoothGattTestableWrapper btGatt =
                Mockito.mock(BassClientStateMachine.BluetoothGattTestableWrapper.class);
        mBassClientStateMachine.mBluetoothGatt = btGatt;
        BluetoothGattCharacteristic scanControlPoint =
                Mockito.mock(BluetoothGattCharacteristic.class);
        mBassClientStateMachine.mBroadcastScanControlPoint = scanControlPoint;

        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(ADD_BCAST_SOURCE, metadata),
                BassClientStateMachine.ConnectedProcessing.class);
        verify(scanControlPoint).setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        verify(scanControlPoint).setValue(any(byte[].class));
        verify(btGatt).writeCharacteristic(any());
    }

    @Test
    public void selectBcastSource_withSameBroadcastId() {
        final int testSyncHandle = 1;
        initToConnectedState();

        byte[] scanRecord = new byte[]{
                0x02, 0x01, 0x1a, // advertising flags
                0x05, 0x02, 0x52, 0x18, 0x0a, 0x11, // 16 bit service uuids
                0x04, 0x09, 0x50, 0x65, 0x64, // name
                0x02, 0x0A, (byte) 0xec, // tx power level
                0x05, 0x30, 0x54, 0x65, 0x73, 0x74, // broadcast name: Test
                0x06, 0x16, 0x52, 0x18, 0x2A, 0x00, 0x00, // service data, broadcast id 42
                0x08, 0x16, 0x56, 0x18, 0x07, 0x03, 0x06, 0x07, 0x08,
                // service data - public broadcast,
                // feature - 0x7, metadata len - 0x3, metadata - 0x6, 0x7, 0x8
                0x05, (byte) 0xff, (byte) 0xe0, 0x00, 0x02, 0x15, // manufacturer specific data
                0x03, 0x50, 0x01, 0x02, // an unknown data type won't cause trouble
        };
        ScanRecord record = ScanRecord.parseFromBytes(scanRecord);
        ScanResult scanResult = new ScanResult(mTestDevice, 0, 0, 0, 0, 0, 0, 0, record, 0);

        List<Integer> activeSyncedSrc = new ArrayList<>();
        activeSyncedSrc.add(testSyncHandle);
        // need this to ensure expected mock behavior for getActiveSyncedSource
        when(mBassClientService.getActiveSyncedSources(any())).thenReturn(activeSyncedSrc);
        when(mBassClientService.getSyncHandleForBroadcastId(anyInt())).thenReturn(testSyncHandle);
        doNothing()
                .when(mMethodProxy)
                .periodicAdvertisingManagerRegisterSync(
                        any(), any(), anyInt(), anyInt(), any(), any());

        mBassClientStateMachine.sendMessage(SELECT_BCAST_SOURCE, BassConstants.AUTO, 0, scanResult);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        // validate syncing to the same broadcast id will be skipped
        verify(mBassClientService, never()).updatePeriodicAdvertisementResultMap(
                any(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                any(), any());

        // need this to ensure expected mock behavior for getActiveSyncedSource
        when(mBassClientService.getActiveSyncedSources(any())).thenReturn(null);

        mBassClientStateMachine.sendMessage(SELECT_BCAST_SOURCE, BassConstants.AUTO, 0, scanResult);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        verify(mBassClientService).updatePeriodicAdvertisementResultMap(
                any(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                any(), eq(TEST_BROADCAST_NAME));
    }

    @Test
    public void addBcastSource_withCachedScanResults() {
        initToConnectedState();

        BassClientService.Callbacks callbacks = Mockito.mock(BassClientService.Callbacks.class);
        when(mBassClientService.getCallbacks()).thenReturn(callbacks);

        BluetoothLeBroadcastMetadata metadata = createBroadcastMetadata();
        when(mBassClientService.isLocalBroadcast(any(BluetoothLeBroadcastMetadata.class)))
                .thenReturn(false);
        when(mBassClientService.getActiveSyncedSources(any())).thenReturn(null);
        when(mBassClientService.getCachedBroadcast(anyInt())).thenReturn(null);
        mBassClientStateMachine.sendMessage(ADD_BCAST_SOURCE, metadata);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());

        verify(mBassClientService).getCallbacks();
        verify(callbacks).notifySourceAddFailed(any(), any(), anyInt());

        byte[] scanRecord = new byte[]{
                0x02, 0x01, 0x1a, // advertising flags
                0x05, 0x02, 0x52, 0x18, 0x0a, 0x11, // 16 bit service uuids
                0x04, 0x09, 0x50, 0x65, 0x64, // name
                0x02, 0x0A, (byte) 0xec, // tx power level
                0x05, 0x30, 0x54, 0x65, 0x73, 0x74, // broadcast name: Test
                0x06, 0x16, 0x52, 0x18, 0x2A, 0x00, 0x00, // service data, broadcast id 42
                0x08, 0x16, 0x56, 0x18, 0x07, 0x03, 0x06, 0x07, 0x08,
                // service data - public broadcast,
                // feature - 0x7, metadata len - 0x3, metadata - 0x6, 0x7, 0x8
                0x05, (byte) 0xff, (byte) 0xe0, 0x00, 0x02, 0x15, // manufacturer specific data
                0x03, 0x50, 0x01, 0x02, // an unknown data type won't cause trouble
        };
        ScanRecord record = ScanRecord.parseFromBytes(scanRecord);
        ScanResult scanResult = new ScanResult(mAdapter.getRemoteLeDevice("00:11:22:33:44:55",
                        BluetoothDevice.ADDRESS_TYPE_RANDOM), 0, 0, 0, 0, 0, 0, 0, record, 0);
        when(mBassClientService.getCachedBroadcast(anyInt())).thenReturn(scanResult);
        doNothing().when(mMethodProxy).periodicAdvertisingManagerRegisterSync(
                any(), any(), anyInt(), anyInt(), any(), any());
        // validate add source will trigger select source and update mPendingSourceToAdd
        mBassClientStateMachine.sendMessage(ADD_BCAST_SOURCE, metadata);
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());

        Assert.assertEquals(mBassClientStateMachine.mPendingSourceToAdd, metadata);
        verify(mBassClientService, never()).sendBroadcast(any(Intent.class), anyString(), any());
    }

    private void initToDisconnectedState() {
        allowConnection(true);
        allowConnectGatt(true);
        assertThat(mBassClientStateMachine.getCurrentState())
                .isInstanceOf(BassClientStateMachine.Disconnected.class);
    }

    @Test
    public void periodicAdvertisingCallbackOnSyncLost_notifySourceLost() {
        mFakeFlagsImpl.setFlag(Flags.FLAG_LEAUDIO_BROADCAST_MONITOR_SOURCE_SYNC_STATUS, true);
        PeriodicAdvertisingCallback cb = mBassClientStateMachine.mLocalPeriodicAdvCallback;
        BassClientService.Callbacks callbacks = Mockito.mock(BassClientService.Callbacks.class);
        int syncHandle = 1;
        int broadcastId = 2;
        when(mBassClientService.getBroadcastIdForSyncHandle(syncHandle)).thenReturn(broadcastId);
        when(mBassClientService.getCallbacks()).thenReturn(callbacks);
        cb.onSyncLost(syncHandle);

        verify(callbacks).notifySourceLost(broadcastId);
    }

    @Test
    public void periodicAdvertisingCallbackOnBigInfoAdvertisingReport_updateRssi() {
        mFakeFlagsImpl.setFlag(Flags.FLAG_LEAUDIO_BROADCAST_MONITOR_SOURCE_SYNC_STATUS, true);
        PeriodicAdvertisingCallback cb = mBassClientStateMachine.mLocalPeriodicAdvCallback;
        BassClientService.Callbacks callbacks = Mockito.mock(BassClientService.Callbacks.class);
        int testRssi = -40;
        int syncHandle = 1;
        final String testMacAddress = "00:11:22:33:44:55";
        BluetoothDevice testDevice =
                mAdapter.getRemoteLeDevice(testMacAddress, BluetoothDevice.ADDRESS_TYPE_RANDOM);

        byte[] scanRecordWithBaseData =
                new byte[] {
                    (byte) 0x02,
                    (byte) 0x01,
                    (byte) 0x1a, // advertising flags
                    (byte) 0x05,
                    (byte) 0x02,
                    (byte) 0x51,
                    (byte) 0x18,
                    (byte) 0x0a,
                    (byte) 0x11, // 16 bit service uuids
                    (byte) 0x04,
                    (byte) 0x09,
                    (byte) 0x50,
                    (byte) 0x65,
                    (byte) 0x64, // name
                    (byte) 0x02,
                    (byte) 0x0A,
                    (byte) 0xec, // tx power level
                    (byte) 0x19,
                    (byte) 0x16,
                    (byte) 0x51,
                    (byte) 0x18, // service data (base data with 18 bytes)
                    // LEVEL 1
                    (byte) 0x01,
                    (byte) 0x02,
                    (byte) 0x03, // presentationDelay
                    (byte) 0x01, // numSubGroups
                    // LEVEL 2
                    (byte) 0x01, // numSubGroups
                    (byte) 0x00,
                    (byte) 0x00,
                    (byte) 0x00,
                    (byte) 0x00,
                    (byte) 0x00, // UNKNOWN_CODEC
                    (byte) 0x02, // codecConfigLength
                    (byte) 0x01,
                    (byte) 'A', // codecConfigInfo
                    (byte) 0x03, // metaDataLength
                    (byte) 0x06,
                    (byte) 0x07,
                    (byte) 0x08, // metaData
                    // LEVEL 3
                    (byte) 0x04, // index
                    (byte) 0x03, // codecConfigLength
                    (byte) 0x02,
                    (byte) 'B',
                    (byte) 'C', // codecConfigInfo
                    (byte) 0x05,
                    (byte) 0xff,
                    (byte) 0xe0,
                    (byte) 0x00,
                    (byte) 0x02,
                    (byte) 0x15, // manufacturer specific data
                    (byte) 0x03,
                    (byte) 0x50,
                    (byte) 0x01,
                    (byte) 0x02, // an unknown data type won't cause trouble
                };

        ScanRecord record = ScanRecord.parseFromBytes(scanRecordWithBaseData);
        ScanResult scanResult =
                new ScanResult(
                        mAdapter.getRemoteLeDevice(
                                "00:11:22:33:44:55", BluetoothDevice.ADDRESS_TYPE_RANDOM),
                        0,
                        0,
                        0,
                        0,
                        0,
                        testRssi,
                        0,
                        record,
                        0);
        BaseData data =
                BaseData.parseBaseData(record.getServiceData(BassConstants.BASIC_AUDIO_UUID));

        when(mBassClientService.getDeviceForSyncHandle(syncHandle)).thenReturn(testDevice);
        PeriodicAdvertisementResult paResult = Mockito.mock(PeriodicAdvertisementResult.class);
        when(mBassClientService.getPeriodicAdvertisementResult(any(), anyInt()))
                .thenReturn(paResult);
        when(paResult.isNotified()).thenReturn(false);
        when(mBassClientService.getBase(anyInt())).thenReturn(data);
        when(mBassClientService.getCachedBroadcast(anyInt())).thenReturn(scanResult);
        when(mBassClientService.getCallbacks()).thenReturn(callbacks);

        cb.onBigInfoAdvertisingReport(syncHandle, true);
        ArgumentCaptor<BluetoothLeBroadcastMetadata> metaData =
                ArgumentCaptor.forClass(BluetoothLeBroadcastMetadata.class);
        verify(callbacks).notifySourceFound(metaData.capture());

        Assert.assertEquals(testRssi, metaData.getValue().getRssi());
    }

    private void initToConnectingState() {
        allowConnection(true);
        allowConnectGatt(true);
        sendMessageAndVerifyTransition(
                mBassClientStateMachine.obtainMessage(CONNECT),
                BassClientStateMachine.Connecting.class);
        Mockito.clearInvocations(mBassClientService);
    }

    private void initToConnectedState() {
        initToConnectingState();

        Message msg = mBassClientStateMachine.obtainMessage(CONNECTION_STATE_CHANGED);
        msg.obj = BluetoothProfile.STATE_CONNECTED;
        sendMessageAndVerifyTransition(msg, BassClientStateMachine.Connected.class);
        Mockito.clearInvocations(mBassClientService);
    }

    private void initToConnectedProcessingState() {
        initToConnectedState();
        moveConnectedStateToConnectedProcessingState();
    }

    private void moveConnectedStateToConnectedProcessingState() {
        BluetoothGattCharacteristic gattCharacteristic = Mockito.mock(
                BluetoothGattCharacteristic.class);
        BassClientStateMachine.BluetoothGattTestableWrapper btGatt = Mockito.mock(
                BassClientStateMachine.BluetoothGattTestableWrapper.class);
        mBassClientStateMachine.mBluetoothGatt = btGatt;
        sendMessageAndVerifyTransition(mBassClientStateMachine.obtainMessage(
                        READ_BASS_CHARACTERISTICS, gattCharacteristic),
                BassClientStateMachine.ConnectedProcessing.class);
        Mockito.clearInvocations(mBassClientService);
    }

    private <T> void sendMessageAndVerifyTransition(Message msg, Class<T> type) {
        Mockito.clearInvocations(mBassClientService);
        mBassClientStateMachine.sendMessage(msg);
        // Verify that one connection state broadcast is executed
        verify(mBassClientService, timeout(TIMEOUT_MS)
                .times(1))
                .sendBroadcast(any(Intent.class), anyString(), any());
        Assert.assertThat(mBassClientStateMachine.getCurrentState(), IsInstanceOf.instanceOf(type));
    }

    private BluetoothLeBroadcastMetadata createBroadcastMetadata() {
        final String testMacAddress = "00:11:22:33:44:55";
        final int testBroadcastId = 42;
        final int testAdvertiserSid = 1234;
        final int testPaSyncInterval = 100;
        final int testPresentationDelayMs = 345;

        BluetoothDevice testDevice =
                mAdapter.getRemoteLeDevice(testMacAddress, BluetoothDevice.ADDRESS_TYPE_RANDOM);

        BluetoothLeBroadcastMetadata.Builder builder = new BluetoothLeBroadcastMetadata.Builder()
                .setEncrypted(false)
                .setSourceDevice(testDevice, BluetoothDevice.ADDRESS_TYPE_RANDOM)
                .setSourceAdvertisingSid(testAdvertiserSid)
                .setBroadcastId(testBroadcastId)
                .setBroadcastCode(new byte[] { 0x00, 0x01, 0x00, 0x02 })
                .setPaSyncInterval(testPaSyncInterval)
                .setPresentationDelayMicros(testPresentationDelayMs);
        // builder expect at least one subgroup
        builder.addSubgroup(createBroadcastSubgroup());
        return builder.build();
    }

    private BluetoothLeBroadcastSubgroup createBroadcastSubgroup() {
        final long testAudioLocationFrontLeft = 0x01;
        final long testAudioLocationFrontRight = 0x02;
        // For BluetoothLeAudioContentMetadata
        final String testProgramInfo = "Test";
        // German language code in ISO 639-3
        final String testLanguage = "deu";
        final int testCodecId = 42;
        final int testChannelIndex = 56;

        BluetoothLeAudioCodecConfigMetadata codecMetadata =
                new BluetoothLeAudioCodecConfigMetadata.Builder()
                        .setAudioLocation(testAudioLocationFrontLeft).build();
        BluetoothLeAudioContentMetadata contentMetadata =
                new BluetoothLeAudioContentMetadata.Builder()
                        .setProgramInfo(testProgramInfo).setLanguage(testLanguage).build();
        BluetoothLeBroadcastSubgroup.Builder builder = new BluetoothLeBroadcastSubgroup.Builder()
                .setCodecId(testCodecId)
                .setCodecSpecificConfig(codecMetadata)
                .setContentMetadata(contentMetadata);

        BluetoothLeAudioCodecConfigMetadata channelCodecMetadata =
                new BluetoothLeAudioCodecConfigMetadata.Builder()
                        .setAudioLocation(testAudioLocationFrontRight).build();

        // builder expect at least one channel
        BluetoothLeBroadcastChannel channel =
                new BluetoothLeBroadcastChannel.Builder()
                        .setSelected(true)
                        .setChannelIndex(testChannelIndex)
                        .setCodecMetadata(channelCodecMetadata)
                        .build();
        builder.addChannel(channel);
        return builder.build();
    }

    // It simulates GATT connection for testing.
    public static class StubBassClientStateMachine extends BassClientStateMachine {
        boolean mShouldAllowGatt = true;
        boolean mShouldHandleMessage = true;
        Boolean mIsPendingRemove;
        List<Integer> mMsgWhats = new ArrayList<>();
        int mMsgWhat;
        int mMsgAgr1;
        int mMsgArg2;
        Object mMsgObj;

        StubBassClientStateMachine(
                BluetoothDevice device,
                BassClientService service,
                AdapterService adapterService,
                Looper looper,
                int connectTimeout,
                FeatureFlags featureFlags) {
            super(device, service, adapterService, looper, connectTimeout, featureFlags);
        }

        @Override
        public boolean connectGatt(Boolean autoConnect) {
            mGattCallback = new GattCallback();
            return mShouldAllowGatt;
        }

        @Override
        public void sendMessage(Message msg) {
            mMsgWhats.add(msg.what);
            mMsgWhat = msg.what;
            mMsgAgr1 = msg.arg1;
            mMsgArg2 = msg.arg2;
            mMsgObj = msg.obj;
            if (mShouldHandleMessage) {
                super.sendMessage(msg);
            }
        }

        public void notifyConnectionStateChanged(int status, int newState) {
            if (mGattCallback != null) {
                BluetoothGatt gatt = null;
                if (mBluetoothGatt != null) {
                    gatt = mBluetoothGatt.mWrappedBluetoothGatt;
                }
                mGattCallback.onConnectionStateChange(gatt, status, newState);
            }
        }

        public boolean hasDeferredMessagesSuper(int what) {
            return super.hasDeferredMessages(what);
        }

        @Override
        boolean isPendingRemove(Integer sourceId) {
            if (mIsPendingRemove == null) {
                return super.isPendingRemove(sourceId);
            }
            return mIsPendingRemove;
        }
    }
}
