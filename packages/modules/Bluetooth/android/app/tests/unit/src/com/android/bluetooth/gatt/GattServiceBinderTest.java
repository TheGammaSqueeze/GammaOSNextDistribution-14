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

package com.android.bluetooth.gatt;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.IBluetoothGattCallback;
import android.bluetooth.IBluetoothGattServerCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.IAdvertisingSetCallback;
import android.bluetooth.le.IPeriodicAdvertisingCallback;
import android.bluetooth.le.IScannerCallback;
import android.bluetooth.le.PeriodicAdvertisingParameters;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.AttributionSource;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelUuid;
import android.os.WorkSource;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.x.com.android.modules.utils.SynchronousResultReceiver;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class GattServiceBinderTest {

    private static final String REMOTE_DEVICE_ADDRESS = "00:00:00:00:00:00";

    @Mock
    private GattService mService;

    private Context mContext;
    private BluetoothDevice mDevice;
    private PendingIntent mPendingIntent;
    private AttributionSource mAttributionSource;

    private GattService.BluetoothGattBinder mBinder;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        Intent intent = new Intent();
        mPendingIntent = PendingIntent.getBroadcast(mContext, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);
        MockitoAnnotations.initMocks(this);
        when(mService.isAvailable()).thenReturn(true);
        mBinder = new GattService.BluetoothGattBinder(mService);
        mAttributionSource = new AttributionSource.Builder(1).build();
        mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(REMOTE_DEVICE_ADDRESS);
    }

    @Test
    public void getDevicesMatchingConnectionStates() {
        int[] states = new int[] {BluetoothProfile.STATE_CONNECTED};

        mBinder.getDevicesMatchingConnectionStates(states, mAttributionSource,
                SynchronousResultReceiver.get());

        verify(mService).getDevicesMatchingConnectionStates(states, mAttributionSource);
    }

    @Test
    public void registerClient() {
        UUID uuid = UUID.randomUUID();
        IBluetoothGattCallback callback = mock(IBluetoothGattCallback.class);
        boolean eattSupport = true;

        mBinder.registerClient(new ParcelUuid(uuid), callback, eattSupport, mAttributionSource,
                SynchronousResultReceiver.get());

        verify(mService).registerClient(uuid, callback, eattSupport, mAttributionSource);
    }

    @Test
    public void unregisterClient() {
        int clientIf = 3;

        mBinder.unregisterClient(clientIf, mAttributionSource, SynchronousResultReceiver.get());

        verify(mService).unregisterClient(clientIf, mAttributionSource);
    }

    @Test
    public void registerScanner() throws Exception {
        IScannerCallback callback = mock(IScannerCallback.class);
        WorkSource workSource = mock(WorkSource.class);

        mBinder.registerScanner(callback, workSource, mAttributionSource,
                SynchronousResultReceiver.get());

        verify(mService).registerScanner(callback, workSource, mAttributionSource);
    }

    @Test
    public void unregisterScanner() {
        int scannerId = 3;

        mBinder.unregisterScanner(scannerId, mAttributionSource, SynchronousResultReceiver.get());

        verify(mService).unregisterScanner(scannerId, mAttributionSource);
    }

    @Test
    public void startScan() throws Exception {
        int scannerId = 1;
        ScanSettings settings = new ScanSettings.Builder().build();
        List<ScanFilter> filters = new ArrayList<>();

        mBinder.startScan(scannerId, settings, filters, mAttributionSource,
                SynchronousResultReceiver.get());

        verify(mService).startScan(scannerId, settings, filters, mAttributionSource);
    }

    @Test
    public void startScanForIntent() throws Exception {
        ScanSettings settings = new ScanSettings.Builder().build();
        List<ScanFilter> filters = new ArrayList<>();

        mBinder.startScanForIntent(mPendingIntent, settings, filters, mAttributionSource,
                SynchronousResultReceiver.get());

        verify(mService).registerPiAndStartScan(mPendingIntent, settings, filters,
                mAttributionSource);
    }

    @Test
    public void stopScanForIntent() throws Exception {
        mBinder.stopScanForIntent(mPendingIntent, mAttributionSource,
                SynchronousResultReceiver.get());

        verify(mService).stopScan(mPendingIntent, mAttributionSource);
    }

    @Test
    public void stopScan() throws Exception {
        int scannerId = 3;

        mBinder.stopScan(scannerId, mAttributionSource, SynchronousResultReceiver.get());

        verify(mService).stopScan(scannerId, mAttributionSource);
    }

    @Test
    public void flushPendingBatchResults() throws Exception {
        int scannerId = 3;

        mBinder.flushPendingBatchResults(scannerId, mAttributionSource,
                SynchronousResultReceiver.get());

        verify(mService).flushPendingBatchResults(scannerId, mAttributionSource);
    }

    @Test
    public void clientConnect() throws Exception {
        int clientIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;
        int addressType = BluetoothDevice.ADDRESS_TYPE_RANDOM;
        boolean isDirect = true;
        int transport = 2;
        boolean opportunistic = true;
        int phy = 3;

        mBinder.clientConnect(clientIf, address, addressType, isDirect, transport, opportunistic,
                phy, mAttributionSource, SynchronousResultReceiver.get());

        verify(mService).clientConnect(clientIf, address, addressType, isDirect, transport,
                opportunistic, phy, mAttributionSource);
    }

    @Test
    public void clientDisconnect() throws Exception {
        int clientIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;

        mBinder.clientDisconnect(clientIf, address, mAttributionSource,
                SynchronousResultReceiver.get());

        verify(mService).clientDisconnect(clientIf, address, mAttributionSource);
    }

    @Test
    public void clientSetPreferredPhy() throws Exception {
        int clientIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;
        int txPhy = 2;
        int rxPhy = 1;
        int phyOptions = 3;

        mBinder.clientSetPreferredPhy(clientIf, address, txPhy, rxPhy, phyOptions,
                mAttributionSource, SynchronousResultReceiver.get());

        verify(mService).clientSetPreferredPhy(clientIf, address, txPhy, rxPhy, phyOptions,
                mAttributionSource);
    }

    @Test
    public void clientReadPhy() throws Exception {
        int clientIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;

        mBinder.clientReadPhy(clientIf, address, mAttributionSource,
                SynchronousResultReceiver.get());

        verify(mService).clientReadPhy(clientIf, address, mAttributionSource);
    }

    @Test
    public void refreshDevice() throws Exception {
        int clientIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;

        mBinder.refreshDevice(clientIf, address, mAttributionSource,
                SynchronousResultReceiver.get());

        verify(mService).refreshDevice(clientIf, address, mAttributionSource);
    }

    @Test
    public void discoverServices() throws Exception {
        int clientIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;

        mBinder.discoverServices(clientIf, address, mAttributionSource,
                SynchronousResultReceiver.get());

        verify(mService).discoverServices(clientIf, address, mAttributionSource);
    }

    @Test
    public void discoverServiceByUuid() throws Exception {
        int clientIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;
        UUID uuid = UUID.randomUUID();

        mBinder.discoverServiceByUuid(clientIf, address, new ParcelUuid(uuid), mAttributionSource,
                SynchronousResultReceiver.get());

        verify(mService).discoverServiceByUuid(clientIf, address, uuid, mAttributionSource);
    }

    @Test
    public void readCharacteristic() throws Exception {
        int clientIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;
        int handle = 2;
        int authReq = 3;

        mBinder.readCharacteristic(clientIf, address, handle, authReq, mAttributionSource,
                SynchronousResultReceiver.get());

        verify(mService).readCharacteristic(clientIf, address, handle, authReq, mAttributionSource);
    }

    @Test
    public void readUsingCharacteristicUuid() throws Exception {
        int clientIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;
        UUID uuid = UUID.randomUUID();
        int startHandle = 2;
        int endHandle = 3;
        int authReq = 4;

        mBinder.readUsingCharacteristicUuid(clientIf, address, new ParcelUuid(uuid),
                startHandle, endHandle, authReq, mAttributionSource,
                SynchronousResultReceiver.get());

        verify(mService).readUsingCharacteristicUuid(clientIf, address, uuid, startHandle,
                endHandle, authReq, mAttributionSource);
    }

    @Test
    public void writeCharacteristic() throws Exception {
        int clientIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;
        int handle = 2;
        int writeType = 3;
        int authReq = 4;
        byte[] value = new byte[] {5, 6};

        mBinder.writeCharacteristic(clientIf, address, handle, writeType, authReq,
                value, mAttributionSource, SynchronousResultReceiver.get());

        verify(mService).writeCharacteristic(clientIf, address, handle, writeType, authReq, value,
                mAttributionSource);
    }

    @Test
    public void readDescriptor() throws Exception {
        int clientIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;
        int handle = 2;
        int authReq = 3;

        mBinder.readDescriptor(clientIf, address, handle, authReq, mAttributionSource,
                SynchronousResultReceiver.get());

        verify(mService).readDescriptor(clientIf, address, handle, authReq, mAttributionSource);
    }

    @Test
    public void writeDescriptor() throws Exception {
        int clientIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;
        int handle = 2;
        int authReq = 3;
        byte[] value = new byte[] {4, 5};

        mBinder.writeDescriptor(clientIf, address, handle, authReq, value,
                mAttributionSource, SynchronousResultReceiver.get());

        verify(mService).writeDescriptor(clientIf, address, handle, authReq, value,
                mAttributionSource);
    }

    @Test
    public void beginReliableWrite() throws Exception {
        int clientIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;

        mBinder.beginReliableWrite(clientIf, address, mAttributionSource,
                SynchronousResultReceiver.get());

        verify(mService).beginReliableWrite(clientIf, address, mAttributionSource);
    }

    @Test
    public void endReliableWrite() throws Exception {
        int clientIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;
        boolean execute = true;

        mBinder.endReliableWrite(clientIf, address, execute, mAttributionSource,
                SynchronousResultReceiver.get());

        verify(mService).endReliableWrite(clientIf, address, execute, mAttributionSource);
    }

    @Test
    public void registerForNotification() throws Exception {
        int clientIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;
        int handle = 2;
        boolean enable = true;

        mBinder.registerForNotification(clientIf, address, handle, enable,
                mAttributionSource, SynchronousResultReceiver.get());

        verify(mService).registerForNotification(clientIf, address, handle, enable,
                mAttributionSource);
    }

    @Test
    public void readRemoteRssi() throws Exception {
        int clientIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;

        mBinder.readRemoteRssi(clientIf, address, mAttributionSource,
                SynchronousResultReceiver.get());

        verify(mService).readRemoteRssi(clientIf, address, mAttributionSource);
    }

    @Test
    public void configureMTU() throws Exception {
        int clientIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;
        int mtu = 2;

        mBinder.configureMTU(clientIf, address, mtu, mAttributionSource,
                SynchronousResultReceiver.get());

        verify(mService).configureMTU(clientIf, address, mtu, mAttributionSource);
    }

    @Test
    public void connectionParameterUpdate() throws Exception {
        int clientIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;
        int connectionPriority = 2;

        mBinder.connectionParameterUpdate(clientIf, address, connectionPriority,
                mAttributionSource, SynchronousResultReceiver.get());

        verify(mService).connectionParameterUpdate(clientIf, address, connectionPriority,
                mAttributionSource);
    }

    @Test
    public void leConnectionUpdate() throws Exception {
        int clientIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;
        int minConnectionInterval = 3;
        int maxConnectionInterval = 4;
        int peripheralLatency = 5;
        int supervisionTimeout = 6;
        int minConnectionEventLen = 7;
        int maxConnectionEventLen = 8;

        mBinder.leConnectionUpdate(clientIf, address, minConnectionInterval, maxConnectionInterval,
                peripheralLatency, supervisionTimeout, minConnectionEventLen,
                maxConnectionEventLen, mAttributionSource, SynchronousResultReceiver.get());

        verify(mService).leConnectionUpdate(
                clientIf, address, minConnectionInterval, maxConnectionInterval,
                peripheralLatency, supervisionTimeout, minConnectionEventLen,
                maxConnectionEventLen, mAttributionSource);
    }

    @Test
    public void registerServer() {
        UUID uuid = UUID.randomUUID();
        IBluetoothGattServerCallback callback = mock(IBluetoothGattServerCallback.class);
        boolean eattSupport = true;

        mBinder.registerServer(new ParcelUuid(uuid), callback, eattSupport, mAttributionSource,
                SynchronousResultReceiver.get());

        verify(mService).registerServer(uuid, callback, eattSupport, mAttributionSource);
    }

    @Test
    public void unregisterServer() {
        int serverIf = 3;

        mBinder.unregisterServer(serverIf, mAttributionSource, SynchronousResultReceiver.get());

        verify(mService).unregisterServer(serverIf, mAttributionSource);
    }

    @Test
    public void serverConnect() {
        int serverIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;
        boolean isDirect = true;
        int transport = 2;

        mBinder.serverConnect(serverIf, address, isDirect, transport, mAttributionSource,
                SynchronousResultReceiver.get());

        verify(mService).serverConnect(serverIf, address, isDirect, transport, mAttributionSource);
    }

    @Test
    public void serverDisconnect() {
        int serverIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;

        mBinder.serverDisconnect(serverIf, address, mAttributionSource,
                SynchronousResultReceiver.get());

        verify(mService).serverDisconnect(serverIf, address, mAttributionSource);
    }

    @Test
    public void serverSetPreferredPhy() throws Exception {
        int serverIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;
        int txPhy = 2;
        int rxPhy = 1;
        int phyOptions = 3;

        mBinder.serverSetPreferredPhy(serverIf, address, txPhy, rxPhy, phyOptions,
                mAttributionSource, SynchronousResultReceiver.get());

        verify(mService).serverSetPreferredPhy(serverIf, address, txPhy, rxPhy, phyOptions,
                mAttributionSource);
    }

    @Test
    public void serverReadPhy() throws Exception {
        int serverIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;

        mBinder.serverReadPhy(serverIf, address, mAttributionSource,
                SynchronousResultReceiver.get());

        verify(mService).serverReadPhy(serverIf, address, mAttributionSource);
    }

    @Test
    public void addService() {
        int serverIf = 1;
        BluetoothGattService svc = mock(BluetoothGattService.class);

        mBinder.addService(serverIf, svc, mAttributionSource,
                SynchronousResultReceiver.get());

        verify(mService).addService(serverIf, svc, mAttributionSource);
    }

    @Test
    public void removeService() {
        int serverIf = 1;
        int handle = 2;

        mBinder.removeService(serverIf, handle, mAttributionSource,
                SynchronousResultReceiver.get());

        verify(mService).removeService(serverIf, handle, mAttributionSource);
    }

    @Test
    public void clearServices() {
        int serverIf = 1;

        mBinder.clearServices(serverIf, mAttributionSource, SynchronousResultReceiver.get());

        verify(mService).clearServices(serverIf, mAttributionSource);
    }

    @Test
    public void sendResponse() throws Exception {
        int serverIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;
        int requestId = 2;
        int status = 3;
        int offset = 4;
        byte[] value = new byte[] {5, 6};

        mBinder.sendResponse(serverIf, address, requestId, status, offset, value,
                mAttributionSource, SynchronousResultReceiver.get());

        verify(mService).sendResponse(serverIf, address, requestId, status, offset, value,
                mAttributionSource);
    }

    @Test
    public void sendNotification() throws Exception {
        int serverIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;
        int handle = 2;
        boolean confirm = true;
        byte[] value = new byte[] {5, 6};

        mBinder.sendNotification(serverIf, address, handle, confirm, value,
                mAttributionSource, SynchronousResultReceiver.get());

        verify(mService).sendNotification(serverIf, address, handle, confirm, value,
                mAttributionSource);
    }

    @Test
    public void startAdvertisingSet() throws Exception {
        AdvertisingSetParameters parameters = new AdvertisingSetParameters.Builder().build();
        AdvertiseData advertiseData = new AdvertiseData.Builder().build();
        AdvertiseData scanResponse = new AdvertiseData.Builder().build();
        PeriodicAdvertisingParameters periodicParameters =
                new PeriodicAdvertisingParameters.Builder().build();
        AdvertiseData periodicData = new AdvertiseData.Builder().build();
        int duration = 1;
        int maxExtAdvEvents = 2;
        int serverIf = 3;
        IAdvertisingSetCallback callback = mock(IAdvertisingSetCallback.class);

        mBinder.startAdvertisingSet(parameters, advertiseData, scanResponse, periodicParameters,
                periodicData, duration, maxExtAdvEvents, serverIf, callback,
                mAttributionSource, SynchronousResultReceiver.get());

        verify(mService).startAdvertisingSet(parameters, advertiseData, scanResponse,
                periodicParameters, periodicData, duration, maxExtAdvEvents,
                serverIf, callback, mAttributionSource);
    }

    @Test
    public void stopAdvertisingSet() throws Exception {
        IAdvertisingSetCallback callback = mock(IAdvertisingSetCallback.class);

        mBinder.stopAdvertisingSet(callback, mAttributionSource, SynchronousResultReceiver.get());

        verify(mService).stopAdvertisingSet(callback, mAttributionSource);
    }

    @Test
    public void getOwnAddress() throws Exception {
        int advertiserId = 1;

        mBinder.getOwnAddress(advertiserId, mAttributionSource, SynchronousResultReceiver.get());

        verify(mService).getOwnAddress(advertiserId, mAttributionSource);
    }

    @Test
    public void enableAdvertisingSet() throws Exception {
        int advertiserId = 1;
        boolean enable = true;
        int duration = 3;
        int maxExtAdvEvents = 4;

        mBinder.enableAdvertisingSet(advertiserId, enable, duration, maxExtAdvEvents,
                mAttributionSource, SynchronousResultReceiver.get());

        verify(mService).enableAdvertisingSet(advertiserId, enable, duration, maxExtAdvEvents,
                mAttributionSource);
    }

    @Test
    public void setAdvertisingData() throws Exception {
        int advertiserId = 1;
        AdvertiseData data = new AdvertiseData.Builder().build();

        mBinder.setAdvertisingData(advertiserId, data,
                mAttributionSource, SynchronousResultReceiver.get());

        verify(mService).setAdvertisingData(advertiserId, data, mAttributionSource);
    }

    @Test
    public void setScanResponseData() throws Exception {
        int advertiserId = 1;
        AdvertiseData data = new AdvertiseData.Builder().build();

        mBinder.setScanResponseData(advertiserId, data,
                mAttributionSource, SynchronousResultReceiver.get());

        verify(mService).setScanResponseData(advertiserId, data, mAttributionSource);
    }

    @Test
    public void setAdvertisingParameters() throws Exception {
        int advertiserId = 1;
        AdvertisingSetParameters parameters = new AdvertisingSetParameters.Builder().build();

        mBinder.setAdvertisingParameters(advertiserId, parameters,
                mAttributionSource, SynchronousResultReceiver.get());

        verify(mService).setAdvertisingParameters(advertiserId, parameters, mAttributionSource);
    }

    @Test
    public void setPeriodicAdvertisingParameters() throws Exception {
        int advertiserId = 1;
        PeriodicAdvertisingParameters parameters =
                new PeriodicAdvertisingParameters.Builder().build();

        mBinder.setPeriodicAdvertisingParameters(advertiserId, parameters,
                mAttributionSource, SynchronousResultReceiver.get());

        verify(mService).setPeriodicAdvertisingParameters(advertiserId, parameters,
                mAttributionSource);
    }

    @Test
    public void setPeriodicAdvertisingData() throws Exception {
        int advertiserId = 1;
        AdvertiseData data = new AdvertiseData.Builder().build();

        mBinder.setPeriodicAdvertisingData(advertiserId, data,
                mAttributionSource, SynchronousResultReceiver.get());

        verify(mService).setPeriodicAdvertisingData(advertiserId, data, mAttributionSource);
    }

    @Test
    public void setPeriodicAdvertisingEnable() throws Exception {
        int advertiserId = 1;
        boolean enable = true;

        mBinder.setPeriodicAdvertisingEnable(advertiserId, enable,
                mAttributionSource, SynchronousResultReceiver.get());

        verify(mService).setPeriodicAdvertisingEnable(advertiserId, enable, mAttributionSource);
    }

    @Test
    public void registerSync() throws Exception {
        ScanResult scanResult = new ScanResult(mDevice, 1, 2, 3, 4, 5, 6, 7, null, 8);
        int skip = 1;
        int timeout = 2;
        IPeriodicAdvertisingCallback callback = mock(IPeriodicAdvertisingCallback.class);

        mBinder.registerSync(scanResult, skip, timeout, callback,
                mAttributionSource, SynchronousResultReceiver.get());

        verify(mService).registerSync(scanResult, skip, timeout, callback, mAttributionSource);
    }

    @Test
    public void transferSync() throws Exception {
        int serviceData = 1;
        int syncHandle = 2;

        mBinder.transferSync(mDevice, serviceData, syncHandle,
                mAttributionSource, SynchronousResultReceiver.get());

        verify(mService).transferSync(mDevice, serviceData, syncHandle, mAttributionSource);
    }

    @Test
    public void transferSetInfo() throws Exception {
        int serviceData = 1;
        int advHandle = 2;
        IPeriodicAdvertisingCallback callback = mock(IPeriodicAdvertisingCallback.class);

        mBinder.transferSetInfo(mDevice, serviceData, advHandle, callback,
                mAttributionSource, SynchronousResultReceiver.get());

        verify(mService).transferSetInfo(mDevice, serviceData, advHandle, callback,
                mAttributionSource);
    }

    @Test
    public void unregisterSync() throws Exception {
        IPeriodicAdvertisingCallback callback = mock(IPeriodicAdvertisingCallback.class);

        mBinder.unregisterSync(callback, mAttributionSource, SynchronousResultReceiver.get());

        verify(mService).unregisterSync(callback, mAttributionSource);
    }

    @Test
    public void disconnectAll() throws Exception {
        IPeriodicAdvertisingCallback callback = mock(IPeriodicAdvertisingCallback.class);

        mBinder.disconnectAll(mAttributionSource, SynchronousResultReceiver.get());

        verify(mService).disconnectAll(mAttributionSource);
    }

    @Test
    public void numHwTrackFiltersAvailable() throws Exception {
        mBinder.numHwTrackFiltersAvailable(mAttributionSource, SynchronousResultReceiver.get());

        verify(mService).numHwTrackFiltersAvailable(mAttributionSource);
    }

    @Test
    public void cleanUp_doesNotCrash() {
        mBinder.cleanup();
    }
}
