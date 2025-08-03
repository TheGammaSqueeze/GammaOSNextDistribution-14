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
package com.android.bluetooth.sdp;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

/** Native interface to be used by SdpManager */
public class SdpManagerNativeInterface {
    private static final String TAG = SdpManagerNativeInterface.class.getSimpleName();

    @GuardedBy("INSTANCE_LOCK")
    private static SdpManagerNativeInterface sInstance;

    private static final Object INSTANCE_LOCK = new Object();

    private SdpManager mSdpManager;
    private boolean mNativeAvailable = false;

    /** This class is a singleton because native library should only be loaded once */
    public static SdpManagerNativeInterface getInstance() {
        synchronized (INSTANCE_LOCK) {
            if (sInstance == null) {
                sInstance = new SdpManagerNativeInterface();
            }
            return sInstance;
        }
    }

    /** Set singleton instance. */
    @VisibleForTesting
    public static void setInstance(SdpManagerNativeInterface instance) {
        synchronized (INSTANCE_LOCK) {
            sInstance = instance;
        }
    }

    void init(SdpManager sdpManager) {
        mSdpManager = sdpManager;
        initializeNative();
        mNativeAvailable = true;
    }

    void cleanup() {
        mNativeAvailable = false;
        cleanupNative();
    }

    public boolean isAvailable() {
        return mNativeAvailable;
    }

    void sdpSearch(byte[] address, byte[] uuid) {
        sdpSearchNative(address, uuid);
    }

    /**
     * Create a server side Message Access Profile Service Record. Create the record once, and reuse
     * it for all connections. If changes to a record is needed remove the old record using {@link
     * removeSdpRecord} and then create a new one.
     *
     * @param serviceName The textual name of the service
     * @param masId The MAS ID to associate with this SDP record
     * @param rfcommChannel The RFCOMM channel that clients can connect to (obtain from
     *     BluetoothServerSocket)
     * @param l2capPsm The L2CAP PSM channel that clients can connect to (obtain from
     *     BluetoothServerSocket) Supply -1 to omit the L2CAP PSM from the record.
     * @param version The Profile version number (As specified in the Bluetooth MAP specification)
     * @param msgTypes The supported message types bit mask (As specified in the Bluetooth MAP
     *     specification)
     * @param features The feature bit mask (As specified in the Bluetooth MAP specification)
     * @return a handle to the record created. The record can be removed again using {@link
     *     removeSdpRecord}(). The record is not linked to the creation/destruction of
     *     BluetoothSockets, hence SDP record cleanup is a separate process.
     */
    public int createMapMasRecord(
            String serviceName,
            int masId,
            int rfcommChannel,
            int l2capPsm,
            int version,
            int msgTypes,
            int features) {
        if (!mNativeAvailable) {
            throw new RuntimeException(TAG + " mNativeAvailable == false - native not initialized");
        }
        return sdpCreateMapMasRecordNative(
                serviceName, masId, rfcommChannel, l2capPsm, version, msgTypes, features);
    }

    /**
     * Create a client side Message Access Profile Service Record. Create the record once, and reuse
     * it for all connections. If changes to a record is needed remove the old record using {@link
     * removeSdpRecord} and then create a new one.
     *
     * @param serviceName The textual name of the service
     * @param rfcommChannel The RFCOMM channel that clients can connect to (obtain from
     *     BluetoothServerSocket)
     * @param l2capPsm The L2CAP PSM channel that clients can connect to (obtain from
     *     BluetoothServerSocket) Supply -1 to omit the L2CAP PSM from the record.
     * @param version The Profile version number (As specified in the Bluetooth MAP specification)
     * @param features The feature bit mask (As specified in the Bluetooth MAP specification)
     * @return a handle to the record created. The record can be removed again using {@link
     *     removeSdpRecord}(). The record is not linked to the creation/destruction of
     *     BluetoothSockets, hence SDP record cleanup is a separate process.
     */
    public int createMapMnsRecord(
            String serviceName, int rfcommChannel, int l2capPsm, int version, int features) {
        if (!mNativeAvailable) {
            throw new RuntimeException(TAG + " mNativeAvailable == false - native not initialized");
        }
        return sdpCreateMapMnsRecordNative(serviceName, rfcommChannel, l2capPsm, version, features);
    }

    /**
     * Create a Client side Phone Book Access Profile Service Record. Create the record once, and
     * reuse it for all connections. If changes to a record is needed remove the old record using
     * {@link removeSdpRecord} and then create a new one.
     *
     * @param serviceName The textual name of the service
     * @param version The Profile version number (As specified in the Bluetooth PBAP specification)
     * @return a handle to the record created. The record can be removed again using {@link
     *     removeSdpRecord}(). The record is not linked to the creation/destruction of
     *     BluetoothSockets, hence SDP record cleanup is a separate process.
     */
    public int createPbapPceRecord(String serviceName, int version) {
        if (!mNativeAvailable) {
            throw new RuntimeException(TAG + " mNativeAvailable == false - native not initialized");
        }
        return sdpCreatePbapPceRecordNative(serviceName, version);
    }

    /**
     * Create a Server side Phone Book Access Profile Service Record. Create the record once, and
     * reuse it for all connections. If changes to a record is needed remove the old record using
     * {@link removeSdpRecord} and then create a new one.
     *
     * @param serviceName The textual name of the service
     * @param rfcommChannel The RFCOMM channel that clients can connect to (obtain from
     *     BluetoothServerSocket)
     * @param l2capPsm The L2CAP PSM channel that clients can connect to (obtain from
     *     BluetoothServerSocket) Supply -1 to omit the L2CAP PSM from the record.
     * @param version The Profile version number (As specified in the Bluetooth PBAP specification)
     * @param repositories The supported repositories bit mask (As specified in the Bluetooth PBAP
     *     specification)
     * @param features The feature bit mask (As specified in the Bluetooth PBAP specification)
     * @return a handle to the record created. The record can be removed again using {@link
     *     removeSdpRecord}(). The record is not linked to the creation/destruction of
     *     BluetoothSockets, hence SDP record cleanup is a separate process.
     */
    public int createPbapPseRecord(
            String serviceName,
            int rfcommChannel,
            int l2capPsm,
            int version,
            int repositories,
            int features) {
        if (!mNativeAvailable) {
            throw new RuntimeException(TAG + " mNativeAvailable == false - native not initialized");
        }
        return sdpCreatePbapPseRecordNative(
                serviceName, rfcommChannel, l2capPsm, version, repositories, features);
    }

    /**
     * Create a Server side Object Push Profile Service Record. Create the record once, and reuse it
     * for all connections. If changes to a record is needed remove the old record using {@link
     * removeSdpRecord} and then create a new one.
     *
     * @param serviceName The textual name of the service
     * @param rfcommChannel The RFCOMM channel that clients can connect to (obtain from
     *     BluetoothServerSocket)
     * @param l2capPsm The L2CAP PSM channel that clients can connect to (obtain from
     *     BluetoothServerSocket) Supply -1 to omit the L2CAP PSM from the record.
     * @param version The Profile version number (As specified in the Bluetooth OPP specification)
     * @param formatsList A list of the supported formats (As specified in the Bluetooth OPP
     *     specification)
     * @return a handle to the record created. The record can be removed again using {@link
     *     removeSdpRecord}(). The record is not linked to the creation/destruction of
     *     BluetoothSockets, hence SDP record cleanup is a separate process.
     */
    public int createOppOpsRecord(
            String serviceName, int rfcommChannel, int l2capPsm, int version, byte[] formatsList) {
        if (!mNativeAvailable) {
            throw new RuntimeException(TAG + " mNativeAvailable == false - native not initialized");
        }
        return sdpCreateOppOpsRecordNative(
                serviceName, rfcommChannel, l2capPsm, version, formatsList);
    }

    /**
     * Create a server side Sim Access Profile Service Record. Create the record once, and reuse it
     * for all connections. If changes to a record is needed remove the old record using {@link
     * removeSdpRecord} and then create a new one.
     *
     * @param serviceName The textual name of the service
     * @param rfcommChannel The RFCOMM channel that clients can connect to (obtain from
     *     BluetoothServerSocket)
     * @param version The Profile version number (As specified in the Bluetooth SAP specification)
     * @return a handle to the record created. The record can be removed again using {@link
     *     removeSdpRecord}(). The record is not linked to the creation/destruction of
     *     BluetoothSockets, hence SDP record cleanup is a separate process.
     */
    public int createSapsRecord(String serviceName, int rfcommChannel, int version) {
        if (!mNativeAvailable) {
            throw new RuntimeException(TAG + " mNativeAvailable == false - native not initialized");
        }
        return sdpCreateSapsRecordNative(serviceName, rfcommChannel, version);
    }

    /**
     * Remove a SDP record. When Bluetooth is disabled all records will be deleted, hence there is
     * no need to call this function when bluetooth is disabled.
     *
     * @param recordId The Id returned by on of the createXxxXxxRecord() functions.
     * @return TRUE if the record removal was initiated successfully. FALSE if the record handle is
     *     not known/have already been removed.
     */
    public boolean removeSdpRecord(int recordId) {
        if (!mNativeAvailable) {
            throw new RuntimeException(TAG + " mNativeAvailable == false - native not initialized");
        }
        return sdpRemoveSdpRecordNative(recordId);
    }

    /**********************************************************************************************/
    /*********************************** callbacks from native ************************************/
    /**********************************************************************************************/

    void sdpRecordFoundCallback(
            int status, byte[] address, byte[] uuid, int sizeRecord, byte[] record) {
        mSdpManager.sdpRecordFoundCallback(status, address, uuid, sizeRecord, record);
    }

    void sdpMasRecordFoundCallback(
            int status,
            byte[] address,
            byte[] uuid,
            int masInstanceId,
            int l2capPsm,
            int rfcommCannelNumber,
            int profileVersion,
            int supportedFeatures,
            int supportedMessageTypes,
            String serviceName,
            boolean moreResults) {
        mSdpManager.sdpMasRecordFoundCallback(
                status,
                address,
                uuid,
                masInstanceId,
                l2capPsm,
                rfcommCannelNumber,
                profileVersion,
                supportedFeatures,
                supportedMessageTypes,
                serviceName,
                moreResults);
    }

    void sdpMnsRecordFoundCallback(
            int status,
            byte[] address,
            byte[] uuid,
            int l2capPsm,
            int rfcommCannelNumber,
            int profileVersion,
            int supportedFeatures,
            String serviceName,
            boolean moreResults) {
        mSdpManager.sdpMnsRecordFoundCallback(
                status,
                address,
                uuid,
                l2capPsm,
                rfcommCannelNumber,
                profileVersion,
                supportedFeatures,
                serviceName,
                moreResults);
    }

    void sdpPseRecordFoundCallback(
            int status,
            byte[] address,
            byte[] uuid,
            int l2capPsm,
            int rfcommCannelNumber,
            int profileVersion,
            int supportedFeatures,
            int supportedRepositories,
            String serviceName,
            boolean moreResults) {
        mSdpManager.sdpPseRecordFoundCallback(
                status,
                address,
                uuid,
                l2capPsm,
                rfcommCannelNumber,
                profileVersion,
                supportedFeatures,
                supportedRepositories,
                serviceName,
                moreResults);
    }

    void sdpOppOpsRecordFoundCallback(
            int status,
            byte[] address,
            byte[] uuid,
            int l2capPsm,
            int rfcommCannelNumber,
            int profileVersion,
            String serviceName,
            byte[] formatsList,
            boolean moreResults) {
        mSdpManager.sdpOppOpsRecordFoundCallback(
                status,
                address,
                uuid,
                l2capPsm,
                rfcommCannelNumber,
                profileVersion,
                serviceName,
                formatsList,
                moreResults);
    }

    void sdpSapsRecordFoundCallback(
            int status,
            byte[] address,
            byte[] uuid,
            int rfcommCannelNumber,
            int profileVersion,
            String serviceName,
            boolean moreResults) {
        mSdpManager.sdpSapsRecordFoundCallback(
                status,
                address,
                uuid,
                rfcommCannelNumber,
                profileVersion,
                serviceName,
                moreResults);
    }

    void sdpDipRecordFoundCallback(
            int status,
            byte[] address,
            byte[] uuid,
            int specificationId,
            int vendorId,
            int vendorIdSource,
            int productId,
            int version,
            boolean primaryRecord,
            boolean moreResults) {
        mSdpManager.sdpDipRecordFoundCallback(
                status,
                address,
                uuid,
                specificationId,
                vendorId,
                vendorIdSource,
                productId,
                version,
                primaryRecord,
                moreResults);
    }

    /**********************************************************************************************/
    /******************************************* native *******************************************/
    /**********************************************************************************************/

    private native void initializeNative();

    private native void cleanupNative();

    private native boolean sdpSearchNative(byte[] address, byte[] uuid);

    private native int sdpCreateMapMasRecordNative(
            String serviceName,
            int masId,
            int rfcommChannel,
            int l2capPsm,
            int version,
            int msgTypes,
            int features);

    private native int sdpCreateMapMnsRecordNative(
            String serviceName, int rfcommChannel, int l2capPsm, int version, int features);

    private native int sdpCreatePbapPceRecordNative(String serviceName, int version);

    private native int sdpCreatePbapPseRecordNative(
            String serviceName,
            int rfcommChannel,
            int l2capPsm,
            int version,
            int repositories,
            int features);

    private native int sdpCreateOppOpsRecordNative(
            String serviceName, int rfcommChannel, int l2capPsm, int version, byte[] formatsList);

    private native int sdpCreateSapsRecordNative(
            String serviceName, int rfcommChannel, int version);

    private native boolean sdpRemoveSdpRecordNative(int recordId);
}
