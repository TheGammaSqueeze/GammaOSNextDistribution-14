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

package com.android.bluetooth.btservice;

import android.bluetooth.OobData;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.io.FileDescriptor;

/** Native interface to be used by AdapterService */
public class AdapterNativeInterface {
    private static final String TAG = AdapterNativeInterface.class.getSimpleName();

    private JniCallbacks mJniCallbacks;

    @GuardedBy("INSTANCE_LOCK")
    private static AdapterNativeInterface sInstance;

    private static final Object INSTANCE_LOCK = new Object();

    private AdapterNativeInterface() {}

    static AdapterNativeInterface getInstance() {
        synchronized (INSTANCE_LOCK) {
            if (sInstance == null) {
                sInstance = new AdapterNativeInterface();
            }
            return sInstance;
        }
    }

    /** Set singleton instance. */
    @VisibleForTesting
    public static void setInstance(AdapterNativeInterface instance) {
        synchronized (INSTANCE_LOCK) {
            sInstance = instance;
        }
    }

    JniCallbacks getCallbacks() {
        return mJniCallbacks;
    }

    boolean init(
            AdapterService service,
            AdapterProperties adapterProperties,
            boolean startRestricted,
            boolean isCommonCriteriaMode,
            int configCompareResult,
            String[] initFlags,
            boolean isAtvDevice,
            String userDataDirectory) {
        mJniCallbacks = new JniCallbacks(service, adapterProperties);
        return initNative(
                startRestricted,
                isCommonCriteriaMode,
                configCompareResult,
                initFlags,
                isAtvDevice,
                userDataDirectory);
    }

    void cleanup() {
        cleanupNative();
    }

    boolean enable() {
        return enableNative();
    }

    boolean disable() {
        return disableNative();
    }

    boolean setAdapterProperty(int type, byte[] val) {
        return setAdapterPropertyNative(type, val);
    }

    boolean getAdapterProperties() {
        return getAdapterPropertiesNative();
    }

    boolean getAdapterProperty(int type) {
        return getAdapterPropertyNative(type);
    }

    boolean setDeviceProperty(byte[] address, int type, byte[] val) {
        return setDevicePropertyNative(address, type, val);
    }

    boolean getDeviceProperty(byte[] address, int type) {
        return getDevicePropertyNative(address, type);
    }

    boolean createBond(byte[] address, int addressType, int transport) {
        return createBondNative(address, addressType, transport);
    }

    boolean createBondOutOfBand(byte[] address, int transport, OobData p192Data, OobData p256Data) {
        return createBondOutOfBandNative(address, transport, p192Data, p256Data);
    }

    boolean removeBond(byte[] address) {
        return removeBondNative(address);
    }

    boolean cancelBond(byte[] address) {
        return cancelBondNative(address);
    }

    boolean pairingIsBusy() {
        return pairingIsBusyNative();
    }

    void generateLocalOobData(int transport) {
        generateLocalOobDataNative(transport);
    }

    boolean sdpSearch(byte[] address, byte[] uuid) {
        return sdpSearchNative(address, uuid);
    }

    int getConnectionState(byte[] address) {
        return getConnectionStateNative(address);
    }

    boolean startDiscovery() {
        return startDiscoveryNative();
    }

    boolean cancelDiscovery() {
        return cancelDiscoveryNative();
    }

    boolean pinReply(byte[] address, boolean accept, int len, byte[] pin) {
        return pinReplyNative(address, accept, len, pin);
    }

    boolean sspReply(byte[] address, int type, boolean accept, int passkey) {
        return sspReplyNative(address, type, accept, passkey);
    }

    boolean getRemoteServices(byte[] address, int transport) {
        return getRemoteServicesNative(address, transport);
    }

    boolean getRemoteMasInstances(byte[] address) {
        return getRemoteMasInstancesNative(address);
    }

    int readEnergyInfo() {
        return readEnergyInfoNative();
    }

    boolean factoryReset() {
        return factoryResetNative();
    }

    void dump(FileDescriptor fd, String[] arguments) {
        dumpNative(fd, arguments);
    }

    byte[] dumpMetrics() {
        return dumpMetricsNative();
    }

    byte[] obfuscateAddress(byte[] address) {
        return obfuscateAddressNative(address);
    }

    boolean setBufferLengthMillis(int codec, int value) {
        return setBufferLengthMillisNative(codec, value);
    }

    int getMetricId(byte[] address) {
        return getMetricIdNative(address);
    }

    int connectSocket(byte[] address, int type, byte[] uuid, int port, int flag, int callingUid) {
        return connectSocketNative(address, type, uuid, port, flag, callingUid);
    }

    int createSocketChannel(
            int type, String serviceName, byte[] uuid, int port, int flag, int callingUid) {
        return createSocketChannelNative(type, serviceName, uuid, port, flag, callingUid);
    }

    void requestMaximumTxDataLength(byte[] address) {
        requestMaximumTxDataLengthNative(address);
    }

    boolean allowLowLatencyAudio(boolean allowed, byte[] address) {
        return allowLowLatencyAudioNative(allowed, address);
    }

    void metadataChanged(byte[] address, int key, byte[] value) {
        metadataChangedNative(address, key, value);
    }

    boolean interopMatchAddr(String featureName, String address) {
        return interopMatchAddrNative(featureName, address);
    }

    boolean interopMatchName(String featureName, String name) {
        return interopMatchNameNative(featureName, name);
    }

    boolean interopMatchAddrOrName(String featureName, String address) {
        return interopMatchAddrOrNameNative(featureName, address);
    }

    void interopDatabaseAddRemoveAddr(
            boolean doAdd, String featureName, String address, int length) {
        interopDatabaseAddRemoveAddrNative(doAdd, featureName, address, length);
    }

    void interopDatabaseAddRemoveName(boolean doAdd, String featureName, String name) {
        interopDatabaseAddRemoveNameNative(doAdd, featureName, name);
    }

    int getRemotePbapPceVersion(String address) {
        return getRemotePbapPceVersionNative(address);
    }

    boolean pbapPseDynamicVersionUpgradeIsEnabled() {
        return pbapPseDynamicVersionUpgradeIsEnabledNative();
    }

    boolean isLogRedactionEnabled() {
        return isLogRedactionEnabledNative();
    }

    /**********************************************************************************************/
    /*********************************** callbacks from native ************************************/
    /**********************************************************************************************/

    // See JniCallbacks.java

    /**********************************************************************************************/
    /******************************************* native *******************************************/
    /**********************************************************************************************/

    private native boolean initNative(
            boolean startRestricted,
            boolean isCommonCriteriaMode,
            int configCompareResult,
            String[] initFlags,
            boolean isAtvDevice,
            String userDataDirectory);

    private native void cleanupNative();

    private native boolean enableNative();

    private native boolean disableNative();

    private native boolean setAdapterPropertyNative(int type, byte[] val);

    private native boolean getAdapterPropertiesNative();

    private native boolean getAdapterPropertyNative(int type);

    private native boolean setDevicePropertyNative(byte[] address, int type, byte[] val);

    private native boolean getDevicePropertyNative(byte[] address, int type);

    private native boolean createBondNative(byte[] address, int addressType, int transport);

    private native boolean createBondOutOfBandNative(
            byte[] address, int transport, OobData p192Data, OobData p256Data);

    private native boolean removeBondNative(byte[] address);

    private native boolean cancelBondNative(byte[] address);

    private native boolean pairingIsBusyNative();

    private native void generateLocalOobDataNative(int transport);

    private native boolean sdpSearchNative(byte[] address, byte[] uuid);

    private native int getConnectionStateNative(byte[] address);

    private native boolean startDiscoveryNative();

    private native boolean cancelDiscoveryNative();

    private native boolean pinReplyNative(byte[] address, boolean accept, int len, byte[] pin);

    private native boolean sspReplyNative(byte[] address, int type, boolean accept, int passkey);

    private native boolean getRemoteServicesNative(byte[] address, int transport);

    private native boolean getRemoteMasInstancesNative(byte[] address);

    private native int readEnergyInfoNative();

    private native boolean factoryResetNative();

    private native void dumpNative(FileDescriptor fd, String[] arguments);

    private native byte[] dumpMetricsNative();

    private native byte[] obfuscateAddressNative(byte[] address);

    private native boolean setBufferLengthMillisNative(int codec, int value);

    private native int getMetricIdNative(byte[] address);

    private native int connectSocketNative(
            byte[] address, int type, byte[] uuid, int port, int flag, int callingUid);

    private native int createSocketChannelNative(
            int type, String serviceName, byte[] uuid, int port, int flag, int callingUid);

    private native void requestMaximumTxDataLengthNative(byte[] address);

    private native boolean allowLowLatencyAudioNative(boolean allowed, byte[] address);

    private native void metadataChangedNative(byte[] address, int key, byte[] value);

    private native boolean interopMatchAddrNative(String featureName, String address);

    private native boolean interopMatchNameNative(String featureName, String name);

    private native boolean interopMatchAddrOrNameNative(String featureName, String address);

    private native void interopDatabaseAddRemoveAddrNative(
            boolean doAdd, String featureName, String address, int length);

    private native void interopDatabaseAddRemoveNameNative(
            boolean doAdd, String featureName, String name);

    private native int getRemotePbapPceVersionNative(String address);

    private native boolean pbapPseDynamicVersionUpgradeIsEnabledNative();

    private native boolean isLogRedactionEnabledNative();
}
