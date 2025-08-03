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

import android.os.RemoteException;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

/**
 * GATT Profile Native Interface to/from JNI.
 */
public class GattNativeInterface {
    private static final String TAG = GattNativeInterface.class.getSimpleName();

    private GattService mGattService;

    @GuardedBy("INSTANCE_LOCK")
    private static GattNativeInterface sInstance;

    private static final Object INSTANCE_LOCK = new Object();

    private GattNativeInterface() {}

    GattService getGattService() {
        return mGattService;
    }

    /**
     * This class is a singleton because native library should only be loaded once
     *
     * @return default instance
     */
    public static GattNativeInterface getInstance() {
        synchronized (INSTANCE_LOCK) {
            if (sInstance == null) {
                sInstance = new GattNativeInterface();
            }
            return sInstance;
        }
    }

    /** Set singleton instance. */
    @VisibleForTesting
    public static void setInstance(GattNativeInterface instance) {
        synchronized (INSTANCE_LOCK) {
            sInstance = instance;
        }
    }

    /* Callbacks */

    void onScanResult(int eventType, int addressType, String address, int primaryPhy,
            int secondaryPhy, int advertisingSid, int txPower, int rssi, int periodicAdvInt,
            byte[] advData, String originalAddress) {
        getGattService().onScanResult(eventType, addressType, address, primaryPhy, secondaryPhy,
                advertisingSid, txPower, rssi, periodicAdvInt, advData, originalAddress);
    }

    void onScannerRegistered(int status, int scannerId, long uuidLsb, long uuidMsb)
            throws RemoteException {
        getGattService().onScannerRegistered(status, scannerId, uuidLsb, uuidMsb);
    }

    void onClientRegistered(int status, int clientIf, long uuidLsb, long uuidMsb)
            throws RemoteException {
        getGattService().onClientRegistered(status, clientIf, uuidLsb, uuidMsb);
    }

    void onConnected(int clientIf, int connId, int status, String address) throws RemoteException {
        getGattService().onConnected(clientIf, connId, status, address);
    }

    void onDisconnected(int clientIf, int connId, int status, String address)
            throws RemoteException {
        getGattService().onDisconnected(clientIf, connId, status, address);
    }

    void onClientPhyUpdate(int connId, int txPhy, int rxPhy, int status) throws RemoteException {
        getGattService().onClientPhyUpdate(connId, txPhy, rxPhy, status);
    }

    void onClientPhyRead(int clientIf, String address, int txPhy, int rxPhy, int status)
            throws RemoteException {
        getGattService().onClientPhyRead(clientIf, address, txPhy, rxPhy, status);
    }

    void onClientConnUpdate(int connId, int interval, int latency, int timeout, int status)
            throws RemoteException {
        getGattService().onClientConnUpdate(connId, interval, latency, timeout, status);
    }

    void onServiceChanged(int connId) throws RemoteException {
        getGattService().onServiceChanged(connId);
    }

    void onClientSubrateChange(int connId, int subrateFactor, int latency, int contNum, int timeout,
            int status) throws RemoteException {
        getGattService().onClientSubrateChange(connId, subrateFactor, latency, contNum, timeout,
                status);
    }

    void onServerPhyUpdate(int connId, int txPhy, int rxPhy, int status) throws RemoteException {
        getGattService().onServerPhyUpdate(connId, txPhy, rxPhy, status);
    }

    void onServerPhyRead(int serverIf, String address, int txPhy, int rxPhy, int status)
            throws RemoteException {
        getGattService().onServerPhyRead(serverIf, address, txPhy, rxPhy, status);
    }

    void onServerConnUpdate(int connId, int interval, int latency, int timeout, int status)
            throws RemoteException {
        getGattService().onServerConnUpdate(connId, interval, latency, timeout, status);
    }

    void onServerSubrateChange(int connId, int subrateFactor, int latency, int contNum, int timeout,
            int status)
            throws RemoteException {
        getGattService().onServerSubrateChange(connId, subrateFactor, latency, contNum, timeout,
                status);
    }

    void onSearchCompleted(int connId, int status) throws RemoteException {
        getGattService().onSearchCompleted(connId, status);
    }

    GattDbElement getSampleGattDbElement() {
        return getGattService().getSampleGattDbElement();
    }

    void onGetGattDb(int connId, ArrayList<GattDbElement> db) throws RemoteException {
        getGattService().onGetGattDb(connId, db);
    }

    void onRegisterForNotifications(int connId, int status, int registered, int handle) {
        getGattService().onRegisterForNotifications(connId, status, registered, handle);
    }

    void onNotify(int connId, String address, int handle, boolean isNotify, byte[] data)
            throws RemoteException {
        getGattService().onNotify(connId, address, handle, isNotify, data);
    }

    void onReadCharacteristic(int connId, int status, int handle, byte[] data)
            throws RemoteException {
        getGattService().onReadCharacteristic(connId, status, handle, data);
    }

    void onWriteCharacteristic(int connId, int status, int handle, byte[] data)
            throws RemoteException {
        getGattService().onWriteCharacteristic(connId, status, handle, data);
    }

    void onExecuteCompleted(int connId, int status) throws RemoteException {
        getGattService().onExecuteCompleted(connId, status);
    }

    void onReadDescriptor(int connId, int status, int handle, byte[] data) throws RemoteException {
        getGattService().onReadDescriptor(connId, status, handle, data);
    }

    void onWriteDescriptor(int connId, int status, int handle, byte[] data) throws RemoteException {
        getGattService().onWriteDescriptor(connId, status, handle, data);
    }

    void onReadRemoteRssi(int clientIf, String address, int rssi, int status)
            throws RemoteException {
        getGattService().onReadRemoteRssi(clientIf, address, rssi, status);
    }

    void onScanFilterEnableDisabled(int action, int status, int clientIf) {
        getGattService().onScanFilterEnableDisabled(action, status, clientIf);
    }

    void onScanFilterParamsConfigured(int action, int status, int clientIf, int availableSpace) {
        getGattService().onScanFilterParamsConfigured(action, status, clientIf, availableSpace);
    }

    void onScanFilterConfig(int action, int status, int clientIf, int filterType,
            int availableSpace) {
        getGattService().onScanFilterConfig(action, status, clientIf, filterType, availableSpace);
    }

    void onBatchScanStorageConfigured(int status, int clientIf) {
        getGattService().onBatchScanStorageConfigured(status, clientIf);
    }

    void onBatchScanStartStopped(int startStopAction, int status, int clientIf) {
        getGattService().onBatchScanStartStopped(startStopAction, status, clientIf);
    }

    void onBatchScanReports(int status, int scannerId, int reportType, int numRecords,
            byte[] recordData) throws RemoteException {
        getGattService().onBatchScanReports(status, scannerId, reportType, numRecords, recordData);
    }

    void onBatchScanThresholdCrossed(int clientIf) {
        getGattService().onBatchScanThresholdCrossed(clientIf);
    }

    AdvtFilterOnFoundOnLostInfo createOnTrackAdvFoundLostObject(int clientIf, int advPktLen,
            byte[] advPkt, int scanRspLen, byte[] scanRsp, int filtIndex, int advState,
            int advInfoPresent, String address, int addrType, int txPower, int rssiValue,
            int timeStamp) {
        return getGattService().createOnTrackAdvFoundLostObject(clientIf, advPktLen, advPkt,
                scanRspLen, scanRsp, filtIndex, advState, advInfoPresent, address, addrType,
                txPower, rssiValue, timeStamp);
    }

    void onTrackAdvFoundLost(AdvtFilterOnFoundOnLostInfo trackingInfo) throws RemoteException {
        getGattService().onTrackAdvFoundLost(trackingInfo);
    }

    void onScanParamSetupCompleted(int status, int scannerId) throws RemoteException {
        getGattService().onScanParamSetupCompleted(status, scannerId);
    }

    void onConfigureMTU(int connId, int status, int mtu) throws RemoteException {
        getGattService().onConfigureMTU(connId, status, mtu);
    }

    void onClientCongestion(int connId, boolean congested) throws RemoteException {
        getGattService().onClientCongestion(connId, congested);
    }

    /* Server callbacks */

    void onServerRegistered(int status, int serverIf, long uuidLsb, long uuidMsb)
            throws RemoteException {
        getGattService().onServerRegistered(status, serverIf, uuidLsb, uuidMsb);
    }

    void onServiceAdded(int status, int serverIf, List<GattDbElement> service)
            throws RemoteException {
        getGattService().onServiceAdded(status, serverIf, service);
    }

    void onServiceStopped(int status, int serverIf, int srvcHandle) throws RemoteException {
        getGattService().onServiceStopped(status, serverIf, srvcHandle);
    }

    void onServiceDeleted(int status, int serverIf, int srvcHandle) {
        getGattService().onServiceDeleted(status, serverIf, srvcHandle);
    }

    void onClientConnected(String address, boolean connected, int connId, int serverIf)
            throws RemoteException {
        getGattService().onClientConnected(address, connected, connId, serverIf);
    }

    void onServerReadCharacteristic(String address, int connId, int transId, int handle, int offset,
            boolean isLong) throws RemoteException {
        getGattService().onServerReadCharacteristic(address, connId, transId, handle, offset,
                isLong);
    }

    void onServerReadDescriptor(String address, int connId, int transId, int handle, int offset,
            boolean isLong) throws RemoteException {
        getGattService().onServerReadDescriptor(address, connId, transId, handle, offset, isLong);
    }

    void onServerWriteCharacteristic(String address, int connId, int transId, int handle,
            int offset, int length, boolean needRsp, boolean isPrep, byte[] data)
            throws RemoteException {
        getGattService().onServerWriteCharacteristic(address, connId, transId, handle, offset,
                length, needRsp, isPrep, data);
    }

    void onServerWriteDescriptor(String address, int connId, int transId, int handle, int offset,
            int length, boolean needRsp, boolean isPrep, byte[] data) throws RemoteException {
        getGattService().onServerWriteDescriptor(address, connId, transId, handle, offset, length,
                needRsp, isPrep, data);
    }

    void onExecuteWrite(String address, int connId, int transId, int execWrite)
            throws RemoteException {
        getGattService().onExecuteWrite(address, connId, transId, execWrite);
    }

    void onResponseSendCompleted(int status, int attrHandle) {
        getGattService().onResponseSendCompleted(status, attrHandle);
    }

    void onNotificationSent(int connId, int status) throws RemoteException {
        getGattService().onNotificationSent(connId, status);
    }

    void onServerCongestion(int connId, boolean congested) throws RemoteException {
        getGattService().onServerCongestion(connId, congested);
    }

    void onMtuChanged(int connId, int mtu) throws RemoteException {
        getGattService().onMtuChanged(connId, mtu);
    }

    /**********************************************************************************************/
    /******************************************* native *******************************************/
    /**********************************************************************************************/

    private native void initializeNative();

    private native void cleanupNative();
    private native int gattClientGetDeviceTypeNative(String address);
    private native void gattClientRegisterAppNative(long appUuidLsb, long appUuidMsb,
            boolean eattSupport);
    private native void gattClientUnregisterAppNative(int clientIf);
    private native void gattClientConnectNative(int clientIf, String address, int addressType,
            boolean isDirect, int transport, boolean opportunistic, int initiatingPhys);
    private native void gattClientDisconnectNative(int clientIf, String address, int connId);
    private native void gattClientSetPreferredPhyNative(int clientIf, String address, int txPhy,
            int rxPhy, int phyOptions);
    private native void gattClientReadPhyNative(int clientIf, String address);
    private native void gattClientRefreshNative(int clientIf, String address);
    private native void gattClientSearchServiceNative(int connId, boolean searchAll,
            long serviceUuidLsb, long serviceUuidMsb);
    private native void gattClientDiscoverServiceByUuidNative(int connId, long serviceUuidLsb,
            long serviceUuidMsb);
    private native void gattClientGetGattDbNative(int connId);
    private native void gattClientReadCharacteristicNative(int connId, int handle, int authReq);
    private native void gattClientReadUsingCharacteristicUuidNative(int connId, long uuidMsb,
            long uuidLsb, int sHandle, int eHandle, int authReq);
    private native void gattClientReadDescriptorNative(int connId, int handle, int authReq);
    private native void gattClientWriteCharacteristicNative(int connId, int handle, int writeType,
            int authReq, byte[] value);
    private native void gattClientWriteDescriptorNative(int connId, int handle, int authReq,
            byte[] value);
    private native void gattClientExecuteWriteNative(int connId, boolean execute);
    private native void gattClientRegisterForNotificationsNative(int clientIf, String address,
            int handle, boolean enable);
    private native void gattClientReadRemoteRssiNative(int clientIf, String address);
    private native void gattClientConfigureMTUNative(int connId, int mtu);
    private native void gattConnectionParameterUpdateNative(int clientIf, String address,
            int minInterval, int maxInterval, int latency, int timeout, int minConnectionEventLen,
            int maxConnectionEventLen);
    private native void gattServerRegisterAppNative(long appUuidLsb, long appUuidMsb,
            boolean eattSupport);
    private native void gattServerUnregisterAppNative(int serverIf);
    private native void gattServerConnectNative(int serverIf, String address, boolean isDirect,
            int transport);
    private native void gattServerDisconnectNative(int serverIf, String address, int connId);
    private native void gattServerSetPreferredPhyNative(int clientIf, String address, int txPhy,
            int rxPhy, int phyOptions);
    private native void gattServerReadPhyNative(int clientIf, String address);
    private native void gattServerAddServiceNative(int serverIf, List<GattDbElement> service);
    private native void gattServerStopServiceNative(int serverIf, int svcHandle);
    private native void gattServerDeleteServiceNative(int serverIf, int svcHandle);
    private native void gattServerSendIndicationNative(int serverIf, int attrHandle, int connId,
            byte[] val);
    private native void gattServerSendNotificationNative(int serverIf, int attrHandle, int connId,
            byte[] val);
    private native void gattServerSendResponseNative(int serverIf, int connId, int transId,
            int status, int handle, int offset, byte[] val, int authReq);
    private native void gattSubrateRequestNative(int clientIf, String address, int subrateMin,
            int subrateMax, int maxLatency, int contNumber, int supervisionTimeout);
    private native void gattTestNative(int command, long uuid1Lsb, long uuid1Msb, String bda1,
            int p1, int p2, int p3, int p4, int p5);

    /**
     * Initialize the native interface and native components
     */
    public void init(GattService gattService) {
        mGattService = gattService;
        initializeNative();
    }

    /**
     * Clean up the native interface and native components
     */
    public void cleanup() {
        cleanupNative();
        mGattService = null;
    }

    /**
     * Get the type of Bluetooth device
     *
     * @param address address of the Bluetooth device
     * @return type of Bluetooth device 0 for BR/EDR, 1 for BLE, 2 for DUAL mode (To be confirmed)
     */
    public int gattClientGetDeviceType(String address) {
        return gattClientGetDeviceTypeNative(address);
    }

    /**
     * Register the given client
     * It will invoke {@link #onClientRegistered(int, int, long, long)}.
     */
    public void gattClientRegisterApp(long appUuidLsb, long appUuidMsb, boolean eattSupport) {
        gattClientRegisterAppNative(appUuidLsb, appUuidMsb, eattSupport);
    }

    /**
     * Unregister the client
     */
    public void gattClientUnregisterApp(int clientIf) {
        gattClientUnregisterAppNative(clientIf);
    }

    /**
     * Connect to the remote Gatt server
     * @see {@link BluetoothDevice#connectGatt} for parameters.
     */
    public void gattClientConnect(int clientIf, String address, int addressType,
            boolean isDirect, int transport, boolean opportunistic, int initiatingPhys) {
        gattClientConnectNative(clientIf, address, addressType, isDirect, transport,
                opportunistic, initiatingPhys);
    }

    /**
     * Disconnect from the remote Gatt server
     */
    public void gattClientDisconnect(int clientIf, String address, int connId) {
        gattClientDisconnectNative(clientIf, address, connId);
    }

    /**
     * Set the preferred connection PHY for the client
     */
    public void gattClientSetPreferredPhy(int clientIf, String address, int txPhy,
            int rxPhy, int phyOptions) {
        gattClientSetPreferredPhyNative(clientIf, address, txPhy, rxPhy, phyOptions);
    }

    /**
     * Read the current transmitter PHY and receiver PHY of the client
     */
    public void gattClientReadPhy(int clientIf, String address) {
        gattClientReadPhyNative(clientIf, address);
    }

    /**
     * Clear the internal cache and force a refresh of the services from the remote device
     */
    public void gattClientRefresh(int clientIf, String address) {
        gattClientRefreshNative(clientIf, address);
    }

    /**
     * Discover GATT services
     */
    public void gattClientSearchService(int connId, boolean searchAll, long serviceUuidLsb,
            long serviceUuidMsb) {
        gattClientSearchServiceNative(connId, searchAll, serviceUuidLsb, serviceUuidMsb);
    }

    /**
     * Discover the GATT service by the given UUID
     */
    public void gattClientDiscoverServiceByUuid(int connId, long serviceUuidLsb,
            long serviceUuidMsb) {
        gattClientDiscoverServiceByUuidNative(connId, serviceUuidLsb, serviceUuidMsb);
    }

    /**
     * Get GATT DB of the remote device
     */
    public void gattClientGetGattDb(int connId) {
        gattClientGetGattDbNative(connId);
    }

    /**
     * Read a characteristic by the given handle
     */
    public void gattClientReadCharacteristic(int connId, int handle, int authReq) {
        gattClientReadCharacteristicNative(connId, handle, authReq);
    }


    /**
     * Read a characteristic by the given UUID
     */
    public void gattClientReadUsingCharacteristicUuid(int connId, long uuidMsb,
            long uuidLsb, int sHandle, int eHandle, int authReq) {
        gattClientReadUsingCharacteristicUuidNative(connId, uuidMsb, uuidLsb, sHandle, eHandle,
                authReq);
    }

    /**
     * Read a descriptor by the given handle
     */
    public void gattClientReadDescriptor(int connId, int handle, int authReq) {
        gattClientReadDescriptorNative(connId, handle, authReq);
    }

    /**
     * Write a characteristic by the given handle
     */
    public void gattClientWriteCharacteristic(int connId, int handle, int writeType,
            int authReq, byte[] value) {
        gattClientWriteCharacteristicNative(connId, handle, writeType, authReq, value);
    }

    /**
     * Write a descriptor by the given handle
     */
    public void gattClientWriteDescriptor(int connId, int handle, int authReq,
            byte[] value) {
        gattClientWriteDescriptorNative(connId, handle, authReq, value);
    }

    /**
     * Execute a reliable write transaction
     * @param connId
     * @param execute
     */
    public void gattClientExecuteWrite(int connId, boolean execute) {
        gattClientExecuteWriteNative(connId, execute);
    }

    /**
     * Register notification for the characteristic
     */
    public void gattClientRegisterForNotifications(int clientIf, String address,
            int handle, boolean enable) {
        gattClientRegisterForNotificationsNative(clientIf, address, handle, enable);
    }

    /**
     * Read the RSSI for a connected remote device
     * @param clientIf
     * @param address
     */
    public void gattClientReadRemoteRssi(int clientIf, String address) {
        gattClientReadRemoteRssiNative(clientIf, address);
    }

    /**
     * Configure MTU size used for the connection
     */
    public void gattClientConfigureMTU(int connId, int mtu) {
        gattClientConfigureMTUNative(connId, mtu);
    }

    /**
     * Update connection parameter.
     */
    public void gattConnectionParameterUpdate(int clientIf, String address,
            int minInterval, int maxInterval, int latency, int timeout, int minConnectionEventLen,
            int maxConnectionEventLen) {
        gattConnectionParameterUpdateNative(clientIf, address, minInterval, maxInterval, latency,
                timeout, minConnectionEventLen, maxConnectionEventLen);
    }

    /**
     * Update connection parameter.
     */
    public void gattSubrateRequest(int clientIf, String address, int subrateMin, int subrateMax,
            int maxLatency, int contNumber, int supervisionTimeout) {
        gattSubrateRequestNative(clientIf, address, subrateMin, subrateMax, maxLatency, contNumber,
                supervisionTimeout);
    }

    /**
     * Register GATT server
     */
    public void gattServerRegisterApp(long appUuidLsb, long appUuidMsb, boolean eattSupport) {
        gattServerRegisterAppNative(appUuidLsb, appUuidMsb, eattSupport);
    }

    /**
     * Unregister GATT server
     */
    public void gattServerUnregisterApp(int serverIf) {
        gattServerUnregisterAppNative(serverIf);
    }

    /**
     * Connect to a remote device as a GATT server role
     */
    public void gattServerConnect(int serverIf, String address, boolean isDirect,
            int transport) {
        gattServerConnectNative(serverIf, address, isDirect, transport);
    }

    /**
     * Disconnects from a remote device as a GATT server role
     */
    public void gattServerDisconnect(int serverIf, String address, int connId) {
        gattServerDisconnectNative(serverIf, address, connId);
    }

    /**
     * Set the preferred connection PHY as a GATT server role
     */
    public void gattServerSetPreferredPhy(int clientIf, String address, int txPhy,
            int rxPhy, int phyOptions) {
        gattServerSetPreferredPhyNative(clientIf, address, txPhy, rxPhy, phyOptions);
    }

    /**
     * Read the current transmitter PHY and receiver PHY of the connection
     */
    public void gattServerReadPhy(int clientIf, String address) {
        gattServerReadPhyNative(clientIf, address);
    }

    /**
     * Add a service to the list of services to be hosted.
     */
    public void gattServerAddService(int serverIf, List<GattDbElement> service) {
        gattServerAddServiceNative(serverIf, service);
    }

    /**
     * Stop a service
     */
    public void gattServerStopService(int serverIf, int svcHandle) {
        gattServerStopServiceNative(serverIf, svcHandle);
    }

    /**
     * Removes a service from the list of services to be provided
     */
    public void gattServerDeleteService(int serverIf, int svcHandle) {
        gattServerDeleteServiceNative(serverIf, svcHandle);
    }

    /**
     * Send an indication of the characteristic
     */
    public void gattServerSendIndication(int serverIf, int attrHandle, int connId,
            byte[] val) {
        gattServerSendIndicationNative(serverIf, attrHandle, connId, val);
    }

    /**
     * Send a notification of the characteristic
     */
    public void gattServerSendNotification(int serverIf, int attrHandle, int connId,
            byte[] val) {
        gattServerSendNotificationNative(serverIf, attrHandle, connId, val);
    }

    /**
     * Send a response as a GATT server role
     */
    public void gattServerSendResponse(int serverIf, int connId, int transId,
            int status, int handle, int offset, byte[] val, int authReq) {
        gattServerSendResponseNative(serverIf, connId, transId, status, handle, offset, val,
                authReq);
    }

    /**
     * Send a test command
     */
    public void gattTest(int command, long uuid1Lsb, long uuid1Msb, String bda1,
            int p1, int p2, int p3, int p4, int p5) {
        gattTestNative(command, uuid1Lsb, uuid1Msb, bda1, p1, p2, p3, p4, p5);
    }
}

