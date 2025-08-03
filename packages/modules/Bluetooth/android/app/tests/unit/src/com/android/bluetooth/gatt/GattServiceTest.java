/*
 * Copyright 2023 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.IBluetoothGattCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.DistanceMeasurementMethod;
import android.bluetooth.le.DistanceMeasurementParams;
import android.bluetooth.le.IDistanceMeasurementCallback;
import android.bluetooth.le.IPeriodicAdvertisingCallback;
import android.bluetooth.le.IScannerCallback;
import android.bluetooth.le.PeriodicAdvertisingParameters;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.AttributionSource;
import android.content.Context;
import android.content.res.Resources;
import android.location.LocationManager;
import android.os.Binder;
import android.os.RemoteException;
import android.os.WorkSource;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.rule.ServiceTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.CompanionManager;
import com.android.bluetooth.le_scan.AppScanStats;
import com.android.bluetooth.le_scan.PeriodicScanManager;
import com.android.bluetooth.le_scan.ScanClient;
import com.android.bluetooth.le_scan.ScanManager;
import com.android.bluetooth.le_scan.TransitionalScanHelper;

import com.android.bluetooth.flags.Flags;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Test cases for {@link GattService}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class GattServiceTest {

    private static final String REMOTE_DEVICE_ADDRESS = "00:00:00:00:00:00";

    private static final int TIMES_UP_AND_DOWN = 3;
    private static final int TIMEOUT_MS = 5_000;
    private Context mTargetContext;
    private GattService mService;
    @Mock private GattService.ClientMap mClientMap;
    @Mock private TransitionalScanHelper.ScannerMap mScannerMap;

    @SuppressWarnings("NonCanonicalType")
    @Mock
    private TransitionalScanHelper.ScannerMap.App mApp;

    @Mock private GattService.PendingIntentInfo mPiInfo;
    @Mock private PeriodicScanManager mPeriodicScanManager;
    @Mock private ScanManager mScanManager;
    @Mock private Set<String> mReliableQueue;
    @Mock private GattService.ServerMap mServerMap;
    @Mock private DistanceMeasurementManager mDistanceMeasurementManager;
    @Mock private AdvertiseManagerNativeInterface mAdvertiseManagerNativeInterface;

    @Rule public final ServiceTestRule mServiceRule = new ServiceTestRule();
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private BluetoothDevice mDevice;
    private BluetoothAdapter mAdapter;
    private AttributionSource mAttributionSource;

    @Mock private Resources mResources;
    @Mock private AdapterService mAdapterService;
    @Mock private GattObjectsFactory mFactory;
    @Mock private GattNativeInterface mNativeInterface;
    private BluetoothDevice mCurrentDevice;
    private CompanionManager mBtCompanionManager;

    @Before
    public void setUp() throws Exception {
        mTargetContext = InstrumentationRegistry.getTargetContext();

        MockitoAnnotations.initMocks(this);
        TestUtils.setAdapterService(mAdapterService);

        GattObjectsFactory.setInstanceForTesting(mFactory);
        doReturn(mNativeInterface).when(mFactory).getNativeInterface();
        doReturn(mScanManager).when(mFactory).createScanManager(any(), any(), any(), any());
        doReturn(mPeriodicScanManager).when(mFactory).createPeriodicScanManager(any());
        doReturn(mDistanceMeasurementManager).when(mFactory)
                .createDistanceMeasurementManager(any());

        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mAttributionSource = mAdapter.getAttributionSource();
        mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(REMOTE_DEVICE_ADDRESS);

        when(mAdapterService.getResources()).thenReturn(mResources);
        when(mResources.getInteger(anyInt())).thenReturn(0);
        when(mAdapterService.getSharedPreferences(anyString(), anyInt()))
                .thenReturn(InstrumentationRegistry.getTargetContext()
                        .getSharedPreferences("GattServiceTestPrefs", Context.MODE_PRIVATE));

        TestUtils.mockGetSystemService(
                mAdapterService, Context.LOCATION_SERVICE, LocationManager.class);

        mBtCompanionManager = new CompanionManager(mAdapterService, null);
        doReturn(mBtCompanionManager).when(mAdapterService).getCompanionManager();

        AdvertiseManagerNativeInterface.setInstance(mAdvertiseManagerNativeInterface);
        mService = new GattService(InstrumentationRegistry.getTargetContext());
        mService.start();

        mService.mClientMap = mClientMap;
        mService.mTransitionalScanHelper.setScannerMap(mScannerMap);
        mService.mReliableQueue = mReliableQueue;
        mService.mServerMap = mServerMap;
    }

    @After
    public void tearDown() throws Exception {
        mService.stop();
        mService = null;
        AdvertiseManagerNativeInterface.setInstance(null);

        TestUtils.clearAdapterService(mAdapterService);
        GattObjectsFactory.setInstanceForTesting(null);
    }

    @Test
    public void testServiceUpAndDown() throws Exception {
        for (int i = 0; i < TIMES_UP_AND_DOWN; i++) {
            mService.stop();
            mService = null;

            TestUtils.clearAdapterService(mAdapterService);
            reset(mAdapterService);
            TestUtils.setAdapterService(mAdapterService);

            mService = new GattService(InstrumentationRegistry.getTargetContext());
            mService.start();
        }
    }

    @Test
    public void testParseBatchTimestamp() {
        long timestampNanos = mService.parseTimestampNanos(new byte[]{
                -54, 7
        });
        Assert.assertEquals(99700000000L, timestampNanos);
    }

    @Test
    public void emptyClearServices() {
        int serverIf = 1;

        mService.clearServices(serverIf, mAttributionSource);
        verify(mNativeInterface, times(0)).gattServerDeleteService(eq(serverIf), anyInt());
    }

    @Test
    public void clientReadPhy() {
        int clientIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;

        Integer connId = 1;
        doReturn(connId).when(mClientMap).connIdByAddress(clientIf, address);

        mService.clientReadPhy(clientIf, address, mAttributionSource);
        verify(mNativeInterface).gattClientReadPhy(clientIf, address);
    }

    @Test
    public void clientSetPreferredPhy() {
        int clientIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;
        int txPhy = 2;
        int rxPhy = 1;
        int phyOptions = 3;

        Integer connId = 1;
        doReturn(connId).when(mClientMap).connIdByAddress(clientIf, address);

        mService.clientSetPreferredPhy(clientIf, address, txPhy, rxPhy, phyOptions,
                mAttributionSource);
        verify(mNativeInterface).gattClientSetPreferredPhy(clientIf, address, txPhy, rxPhy,
                phyOptions);
    }

    @Test
    public void connectionParameterUpdate() {
        int clientIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;

        int connectionPriority = BluetoothGatt.CONNECTION_PRIORITY_HIGH;
        mService.connectionParameterUpdate(clientIf, address, connectionPriority,
                mAttributionSource);

        connectionPriority = BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER;
        mService.connectionParameterUpdate(clientIf, address, connectionPriority,
                mAttributionSource);

        connectionPriority = BluetoothGatt.CONNECTION_PRIORITY_BALANCED;
        mService.connectionParameterUpdate(clientIf, address, connectionPriority,
                mAttributionSource);

        verify(mNativeInterface, times(3)).gattConnectionParameterUpdate(eq(clientIf),
                eq(address), anyInt(), anyInt(), anyInt(), anyInt(), eq(0), eq(0));
    }

    @Test
    public void testDumpDoesNotCrash() {
        mService.dump(new StringBuilder());
    }

    @Test
    public void continuePiStartScan() {
        int scannerId = 1;

        mPiInfo.settings = new ScanSettings.Builder().build();
        mApp.info = mPiInfo;

        AppScanStats appScanStats = mock(AppScanStats.class);
        doReturn(appScanStats).when(mScannerMap).getAppScanStatsById(scannerId);

        mService.continuePiStartScan(scannerId, mApp);

        verify(appScanStats).recordScanStart(
                mPiInfo.settings, mPiInfo.filters, false, false, scannerId);
        verify(mScanManager).startScan(any());
    }

    @Test
    public void continuePiStartScanCheckUid() {
        int scannerId = 1;

        mPiInfo.settings = new ScanSettings.Builder().build();
        mPiInfo.callingUid = 123;
        mApp.info = mPiInfo;

        AppScanStats appScanStats = mock(AppScanStats.class);
        doReturn(appScanStats).when(mScannerMap).getAppScanStatsById(scannerId);

        mService.continuePiStartScan(scannerId, mApp);

        verify(appScanStats)
                .recordScanStart(mPiInfo.settings, mPiInfo.filters, false, false, scannerId);
        verify(mScanManager)
                .startScan(
                        argThat(
                                new ArgumentMatcher<ScanClient>() {
                                    @Override
                                    public boolean matches(ScanClient client) {
                                        return mPiInfo.callingUid == client.appUid;
                                    }
                                }));
    }

    @Test
    public void onBatchScanReportsInternal_deliverBatchScan() throws RemoteException {
        int status = 1;
        int scannerId = 2;
        int reportType = ScanManager.SCAN_RESULT_TYPE_FULL;
        int numRecords = 1;
        byte[] recordData = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05,
                0x06, 0x07, 0x08, 0x09, 0x00, 0x00, 0x00, 0x00};

        Set<ScanClient> scanClientSet = new HashSet<>();
        ScanClient scanClient = new ScanClient(scannerId);
        scanClient.associatedDevices = new ArrayList<>();
        scanClient.associatedDevices.add("02:00:00:00:00:00");
        scanClient.scannerId = scannerId;
        scanClientSet.add(scanClient);
        doReturn(scanClientSet).when(mScanManager).getFullBatchScanQueue();
        doReturn(mApp).when(mScannerMap).getById(scanClient.scannerId);

        mService.onBatchScanReportsInternal(status, scannerId, reportType, numRecords, recordData);
        verify(mScanManager).callbackDone(scannerId, status);

        reportType = ScanManager.SCAN_RESULT_TYPE_TRUNCATED;
        recordData = new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x02,
                0x06, 0x04, 0x02, 0x02, 0x00, 0x00, 0x02};
        doReturn(scanClientSet).when(mScanManager).getBatchScanQueue();
        IScannerCallback callback = mock(IScannerCallback.class);
        mApp.callback = callback;

        mService.onBatchScanReportsInternal(status, scannerId, reportType, numRecords, recordData);
        verify(callback).onBatchScanResults(any());
    }

    @Test
    public void clientConnect() throws Exception {
        int clientIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;
        int addressType = BluetoothDevice.ADDRESS_TYPE_RANDOM;
        boolean isDirect = false;
        int transport = 2;
        boolean opportunistic = true;
        int phy = 3;

        mService.clientConnect(clientIf, address, addressType, isDirect, transport,
                opportunistic, phy, mAttributionSource);

        verify(mNativeInterface).gattClientConnect(clientIf, address, addressType,
                isDirect, transport, opportunistic, phy);
    }

    @Test
    public void disconnectAll() {
        Map<Integer, String> connMap = new HashMap<>();
        int clientIf = 1;
        String address = "02:00:00:00:00:00";
        connMap.put(clientIf, address);
        doReturn(connMap).when(mClientMap).getConnectedMap();
        Integer connId = 1;
        doReturn(connId).when(mClientMap).connIdByAddress(clientIf, address);

        mService.disconnectAll(mAttributionSource);
        verify(mNativeInterface).gattClientDisconnect(clientIf, address, connId);
    }

    @Test
    public void enforceReportDelayFloor() {
        long reportDelayFloorHigher = GattService.DEFAULT_REPORT_DELAY_FLOOR + 1;
        ScanSettings scanSettings = new ScanSettings.Builder()
                .setReportDelay(reportDelayFloorHigher)
                .build();

        ScanSettings newScanSettings = mService.enforceReportDelayFloor(scanSettings);

        assertThat(newScanSettings.getReportDelayMillis())
                .isEqualTo(scanSettings.getReportDelayMillis());

        ScanSettings scanSettingsFloor = new ScanSettings.Builder()
                .setReportDelay(1)
                .build();

        ScanSettings newScanSettingsFloor = mService.enforceReportDelayFloor(scanSettingsFloor);

        assertThat(newScanSettingsFloor.getReportDelayMillis())
                .isEqualTo(GattService.DEFAULT_REPORT_DELAY_FLOOR);
    }

    @Test
    public void setAdvertisingData() {
        int advertiserId = 1;
        AdvertiseData data = new AdvertiseData.Builder().build();

        mService.setAdvertisingData(advertiserId, data, mAttributionSource);
    }

    @Test
    public void setAdvertisingParameters() {
        int advertiserId = 1;
        AdvertisingSetParameters parameters = new AdvertisingSetParameters.Builder().build();

        mService.setAdvertisingParameters(advertiserId, parameters, mAttributionSource);
    }

    @Test
    public void setPeriodicAdvertisingData() {
        int advertiserId = 1;
        AdvertiseData data = new AdvertiseData.Builder().build();

        mService.setPeriodicAdvertisingData(advertiserId, data, mAttributionSource);
    }

    @Test
    public void setPeriodicAdvertisingEnable() {
        int advertiserId = 1;
        boolean enable = true;

        mService.setPeriodicAdvertisingEnable(advertiserId, enable, mAttributionSource);
    }

    @Test
    public void setPeriodicAdvertisingParameters() {
        int advertiserId = 1;
        PeriodicAdvertisingParameters parameters =
                new PeriodicAdvertisingParameters.Builder().build();

        mService.setPeriodicAdvertisingParameters(advertiserId, parameters, mAttributionSource);
    }

    @Test
    public void setScanResponseData() {
        int advertiserId = 1;
        AdvertiseData data = new AdvertiseData.Builder().build();

        mService.setScanResponseData(advertiserId, data, mAttributionSource);
    }

    @Test
    public void getDevicesMatchingConnectionStates() {
        int[] states = new int[] {BluetoothProfile.STATE_CONNECTED};

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:01:02:03:04:05");
        BluetoothDevice[] bluetoothDevices = new BluetoothDevice[]{testDevice};
        doReturn(bluetoothDevices).when(mAdapterService).getBondedDevices();

        Set<String> connectedDevices = new HashSet<>();
        String address = "02:00:00:00:00:00";
        connectedDevices.add(address);
        doReturn(connectedDevices).when(mClientMap).getConnectedDevices();

        List<BluetoothDevice> deviceList =
                mService.getDevicesMatchingConnectionStates(states, mAttributionSource);

        int expectedSize = 1;
        assertThat(deviceList.size()).isEqualTo(expectedSize);

        BluetoothDevice bluetoothDevice = deviceList.get(0);
        assertThat(bluetoothDevice.getAddress()).isEqualTo(address);
    }

    @Test
    public void registerClient() {
        UUID uuid = UUID.randomUUID();
        IBluetoothGattCallback callback = mock(IBluetoothGattCallback.class);
        boolean eattSupport = true;

        mService.registerClient(uuid, callback, eattSupport, mAttributionSource);
        verify(mNativeInterface).gattClientRegisterApp(uuid.getLeastSignificantBits(),
                uuid.getMostSignificantBits(), eattSupport);
    }

    @Test
    public void unregisterClient() {
        int clientIf = 3;

        mService.unregisterClient(clientIf, mAttributionSource);
        verify(mClientMap).remove(clientIf);
        verify(mNativeInterface).gattClientUnregisterApp(clientIf);
    }

    @Test
    public void registerScanner() throws Exception {
        IScannerCallback callback = mock(IScannerCallback.class);
        WorkSource workSource = mock(WorkSource.class);

        AppScanStats appScanStats = mock(AppScanStats.class);
        doReturn(appScanStats).when(mScannerMap).getAppScanStatsByUid(Binder.getCallingUid());

        mService.registerScanner(callback, workSource, mAttributionSource);
        verify(mScannerMap).add(any(), eq(workSource), eq(callback), eq(null), eq(mService));
        verify(mScanManager).registerScanner(any());
    }

    @Test
    public void flushPendingBatchResults() {
        int scannerId = 3;

        mService.flushPendingBatchResults(scannerId, mAttributionSource);
        verify(mScanManager).flushBatchScanResults(new ScanClient(scannerId));
    }

    @Test
    public void onScanResult_remoteException_clientDied() throws Exception {
        mSetFlagsRule.enableFlags(Flags.FLAG_LE_SCAN_FIX_REMOTE_EXCEPTION);
        int scannerId = 1;

        int eventType = 0;
        int addressType = 0;
        String address = "02:00:00:00:00:00";
        int primaryPhy = 0;
        int secondPhy = 0;
        int advertisingSid = 0;
        int txPower = 0;
        int rssi = 0;
        int periodicAdvInt = 0;
        byte[] advData = new byte[0];

        ScanClient scanClient = new ScanClient(scannerId);
        scanClient.scannerId = scannerId;
        scanClient.hasNetworkSettingsPermission = true;
        scanClient.settings =
                new ScanSettings.Builder()
                        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                        .setLegacy(false)
                        .build();

        AppScanStats appScanStats = mock(AppScanStats.class);
        IScannerCallback callback = mock(IScannerCallback.class);

        mApp.callback = callback;
        mApp.appScanStats = appScanStats;
        Set<ScanClient> scanClientSet = Collections.singleton(scanClient);

        doReturn(address).when(mAdapterService).getIdentityAddress(anyString());
        doReturn(scanClientSet).when(mScanManager).getRegularScanQueue();
        doReturn(mApp).when(mScannerMap).getById(scanClient.scannerId);
        doReturn(appScanStats).when(mScannerMap).getAppScanStatsById(scanClient.scannerId);

        // Simulate remote client crash
        doThrow(new RemoteException()).when(callback).onScanResult(any());

        mService.onScanResult(
                eventType,
                addressType,
                address,
                primaryPhy,
                secondPhy,
                advertisingSid,
                txPower,
                rssi,
                periodicAdvInt,
                advData,
                address);

        assertThat(scanClient.appDied).isTrue();
        verify(appScanStats).recordScanStop(scannerId);
    }

    @Test
    public void readCharacteristic() {
        int clientIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;
        int handle = 2;
        int authReq = 3;

        Integer connId = 1;
        doReturn(connId).when(mClientMap).connIdByAddress(clientIf, address);

        mService.readCharacteristic(clientIf, address, handle, authReq, mAttributionSource);
        verify(mNativeInterface).gattClientReadCharacteristic(connId, handle, authReq);
    }

    @Test
    public void readUsingCharacteristicUuid() {
        int clientIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;
        UUID uuid = UUID.randomUUID();
        int startHandle = 2;
        int endHandle = 3;
        int authReq = 4;

        Integer connId = 1;
        doReturn(connId).when(mClientMap).connIdByAddress(clientIf, address);

        mService.readUsingCharacteristicUuid(clientIf, address, uuid, startHandle, endHandle,
                authReq, mAttributionSource);
        verify(mNativeInterface).gattClientReadUsingCharacteristicUuid(connId,
                uuid.getLeastSignificantBits(), uuid.getMostSignificantBits(), startHandle,
                endHandle, authReq);
    }

    @Test
    public void writeCharacteristic() {
        int clientIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;
        int handle = 2;
        int writeType = 3;
        int authReq = 4;
        byte[] value = new byte[] {5, 6};

        Integer connId = 1;
        doReturn(connId).when(mClientMap).connIdByAddress(clientIf, address);

        int writeCharacteristicResult = mService.writeCharacteristic(clientIf, address, handle,
                writeType, authReq, value, mAttributionSource);
        assertThat(writeCharacteristicResult)
                .isEqualTo(BluetoothStatusCodes.ERROR_DEVICE_NOT_CONNECTED);
    }

    @Test
    public void readDescriptor() throws Exception {
        int clientIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;
        int handle = 2;
        int authReq = 3;

        Integer connId = 1;
        doReturn(connId).when(mClientMap).connIdByAddress(clientIf, address);

        mService.readDescriptor(clientIf, address, handle, authReq, mAttributionSource);
        verify(mNativeInterface).gattClientReadDescriptor(connId, handle, authReq);
    }

    @Test
    public void beginReliableWrite() {
        int clientIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;

        mService.beginReliableWrite(clientIf, address, mAttributionSource);
        verify(mReliableQueue).add(address);
    }

    @Test
    public void endReliableWrite() {
        int clientIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;
        boolean execute = true;

        Integer connId = 1;
        doReturn(connId).when(mClientMap).connIdByAddress(clientIf, address);

        mService.endReliableWrite(clientIf, address, execute, mAttributionSource);
        verify(mReliableQueue).remove(address);
        verify(mNativeInterface).gattClientExecuteWrite(connId, execute);
    }

    @Test
    public void registerForNotification() throws Exception {
        int clientIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;
        int handle = 2;
        boolean enable = true;

        Integer connId = 1;
        doReturn(connId).when(mClientMap).connIdByAddress(clientIf, address);

        mService.registerForNotification(clientIf, address, handle, enable, mAttributionSource);

        verify(mNativeInterface).gattClientRegisterForNotifications(clientIf, address, handle,
                enable);
    }

    @Test
    public void readRemoteRssi() {
        int clientIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;

        mService.readRemoteRssi(clientIf, address, mAttributionSource);
        verify(mNativeInterface).gattClientReadRemoteRssi(clientIf, address);
    }

    @Test
    public void configureMTU() {
        int clientIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;
        int mtu = 2;

        Integer connId = 1;
        doReturn(connId).when(mClientMap).connIdByAddress(clientIf, address);

        mService.configureMTU(clientIf, address, mtu, mAttributionSource);
        verify(mNativeInterface).gattClientConfigureMTU(connId, mtu);
    }

    @Test
    public void leConnectionUpdate() throws Exception {
        int clientIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;
        int minInterval = 3;
        int maxInterval = 4;
        int peripheralLatency = 5;
        int supervisionTimeout = 6;
        int minConnectionEventLen = 7;
        int maxConnectionEventLen = 8;

        mService.leConnectionUpdate(clientIf, address, minInterval, maxInterval,
                peripheralLatency, supervisionTimeout, minConnectionEventLen,
                maxConnectionEventLen, mAttributionSource);

        verify(mNativeInterface).gattConnectionParameterUpdate(clientIf, address, minInterval,
                maxInterval, peripheralLatency, supervisionTimeout, minConnectionEventLen,
                maxConnectionEventLen);
    }

    @Test
    public void serverConnect() {
        int serverIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;
        boolean isDirect = true;
        int transport = 2;

        mService.serverConnect(serverIf, address, isDirect, transport, mAttributionSource);
        verify(mNativeInterface).gattServerConnect(serverIf, address, isDirect, transport);
    }

    @Test
    public void serverDisconnect() {
        int serverIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;

        Integer connId = 1;
        doReturn(connId).when(mServerMap).connIdByAddress(serverIf, address);

        mService.serverDisconnect(serverIf, address, mAttributionSource);
        verify(mNativeInterface).gattServerDisconnect(serverIf, address, connId);
    }

    @Test
    public void serverSetPreferredPhy() throws Exception {
        int serverIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;
        int txPhy = 2;
        int rxPhy = 1;
        int phyOptions = 3;

        mService.serverSetPreferredPhy(serverIf, address, txPhy, rxPhy, phyOptions,
                mAttributionSource);
        verify(mNativeInterface).gattServerSetPreferredPhy(serverIf, address, txPhy, rxPhy,
                phyOptions);
    }

    @Test
    public void serverReadPhy() {
        int serverIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;

        mService.serverReadPhy(serverIf, address, mAttributionSource);
        verify(mNativeInterface).gattServerReadPhy(serverIf, address);
    }

    @Test
    public void sendNotification() throws Exception {
        int serverIf = 1;
        String address = REMOTE_DEVICE_ADDRESS;
        int handle = 2;
        boolean confirm = true;
        byte[] value = new byte[] {5, 6};

        Integer connId = 1;
        doReturn(connId).when(mServerMap).connIdByAddress(serverIf, address);

        mService.sendNotification(serverIf, address, handle, confirm, value, mAttributionSource);
        verify(mNativeInterface).gattServerSendIndication(serverIf, handle, connId, value);

        confirm = false;

        mService.sendNotification(serverIf, address, handle, confirm, value, mAttributionSource);
        verify(mNativeInterface).gattServerSendNotification(serverIf, handle, connId, value);
    }

    @Test
    public void getOwnAddress() throws Exception {
        int advertiserId = 1;

        mService.getOwnAddress(advertiserId, mAttributionSource);
    }

    @Test
    public void enableAdvertisingSet() throws Exception {
        int advertiserId = 1;
        boolean enable = true;
        int duration = 3;
        int maxExtAdvEvents = 4;

        mService.enableAdvertisingSet(advertiserId, enable, duration, maxExtAdvEvents,
                mAttributionSource);
    }

    @Test
    public void registerSync() {
        ScanResult scanResult = new ScanResult(mDevice, 1, 2, 3, 4, 5, 6, 7, null, 8);
        int skip = 1;
        int timeout = 2;
        IPeriodicAdvertisingCallback callback = mock(IPeriodicAdvertisingCallback.class);

        mService.registerSync(scanResult, skip, timeout, callback, mAttributionSource);
        verify(mPeriodicScanManager).startSync(scanResult, skip, timeout, callback);
    }

    @Test
    public void transferSync() {
        int serviceData = 1;
        int syncHandle = 2;

        mService.transferSync(mDevice, serviceData, syncHandle, mAttributionSource);
        verify(mPeriodicScanManager).transferSync(mDevice, serviceData, syncHandle);
    }

    @Test
    public void transferSetInfo() {
        int serviceData = 1;
        int advHandle = 2;
        IPeriodicAdvertisingCallback callback = mock(IPeriodicAdvertisingCallback.class);

        mService.transferSetInfo(mDevice, serviceData, advHandle, callback,
                mAttributionSource);
        verify(mPeriodicScanManager).transferSetInfo(mDevice, serviceData, advHandle, callback);
    }

    @Test
    public void unregisterSync() {
        IPeriodicAdvertisingCallback callback = mock(IPeriodicAdvertisingCallback.class);

        mService.unregisterSync(callback, mAttributionSource);
        verify(mPeriodicScanManager).stopSync(callback);
    }

    @Test
    public void unregAll() throws Exception {
        int appId = 1;
        List<Integer> appIds = new ArrayList<>();
        appIds.add(appId);
        doReturn(appIds).when(mClientMap).getAllAppsIds();

        mService.unregAll(mAttributionSource);
        verify(mClientMap).remove(appId);
        verify(mNativeInterface).gattClientUnregisterApp(appId);
    }

    @Test
    public void numHwTrackFiltersAvailable() {
        mService.numHwTrackFiltersAvailable(mAttributionSource);
        verify(mScanManager).getCurrentUsedTrackingAdvertisement();
    }

    @Test
    public void getSupportedDistanceMeasurementMethods() {
        mService.getSupportedDistanceMeasurementMethods();
        verify(mDistanceMeasurementManager).getSupportedDistanceMeasurementMethods();
    }

    @Test
    public void startDistanceMeasurement() {
        UUID uuid = UUID.randomUUID();
        BluetoothDevice device = mAdapter.getRemoteDevice("00:01:02:03:04:05");
        DistanceMeasurementParams params = new DistanceMeasurementParams.Builder(device)
                .setDurationSeconds(123)
                .setFrequency(DistanceMeasurementParams.REPORT_FREQUENCY_LOW)
                .build();
        IDistanceMeasurementCallback callback = mock(IDistanceMeasurementCallback.class);
        mService.startDistanceMeasurement(uuid, params, callback);
        verify(mDistanceMeasurementManager).startDistanceMeasurement(uuid, params, callback);
    }

    @Test
    public void stopDistanceMeasurement() {
        UUID uuid = UUID.randomUUID();
        BluetoothDevice device = mAdapter.getRemoteDevice("00:01:02:03:04:05");
        int method = DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI;
        mService.stopDistanceMeasurement(uuid, device, method);
        verify(mDistanceMeasurementManager).stopDistanceMeasurement(uuid, device, method, false);
    }

    @Test
    public void cleanUp_doesNotCrash() {
        mService.cleanup();
    }

    @Test
    public void profileConnectionStateChanged_notifyScanManager() {
        mService.notifyProfileConnectionStateChange(
                BluetoothProfile.A2DP,
                BluetoothProfile.STATE_CONNECTING,
                BluetoothProfile.STATE_CONNECTED);
        verify(mScanManager)
                .handleBluetoothProfileConnectionStateChanged(
                        BluetoothProfile.A2DP,
                        BluetoothProfile.STATE_CONNECTING,
                        BluetoothProfile.STATE_CONNECTED);
    }

    @Test
    public void restrictedHandles() throws Exception {
        mSetFlagsRule.enableFlags(Flags.FLAG_GATT_CLEANUP_RESTRICTED_HANDLES);
        int clientIf = 1;
        int connId = 1;
        ArrayList<GattDbElement> db = new ArrayList<>();

        @SuppressWarnings("NonCanonicalType")
        GattService.ClientMap.App app = mock(GattService.ClientMap.App.class);
        IBluetoothGattCallback callback = mock(IBluetoothGattCallback.class);

        doReturn(app).when(mClientMap).getByConnId(connId);
        app.callback = callback;

        GattDbElement hidService =
                GattDbElement.createPrimaryService(
                        UUID.fromString("00001812-0000-1000-8000-00805F9B34FB"));
        hidService.id = 1;

        GattDbElement hidInfoChar =
                GattDbElement.createCharacteristic(
                        UUID.fromString("00002A4A-0000-1000-8000-00805F9B34FB"), 0, 0);
        hidInfoChar.id = 2;

        GattDbElement randomChar =
                GattDbElement.createCharacteristic(
                        UUID.fromString("0000FFFF-0000-1000-8000-00805F9B34FB"), 0, 0);
        randomChar.id = 3;

        db.add(hidService);
        db.add(hidInfoChar);
        db.add(randomChar);

        mService.onGetGattDb(connId, db);
        // HID characteristics should be restricted
        assertThat(mService.mRestrictedHandles.get(connId)).contains(hidInfoChar.id);
        assertThat(mService.mRestrictedHandles.get(connId)).doesNotContain(randomChar.id);

        mService.onDisconnected(
                clientIf, connId, BluetoothGatt.GATT_SUCCESS, REMOTE_DEVICE_ADDRESS);
        assertThat(mService.mRestrictedHandles).doesNotContainKey(connId);
    }
}
