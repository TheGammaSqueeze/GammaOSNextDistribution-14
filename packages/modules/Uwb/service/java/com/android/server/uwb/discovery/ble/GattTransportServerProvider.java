/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.server.uwb.discovery.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.AttributionSource;
import android.content.Context;
import android.content.ContextParams;
import android.util.Log;

import androidx.annotation.WorkerThread;

import com.android.server.uwb.discovery.TransportProvider.MessagePacket;
import com.android.server.uwb.discovery.TransportProvider.TerminationReason;
import com.android.server.uwb.discovery.TransportServerProvider;
import com.android.server.uwb.discovery.TransportServerProvider.TransportServerCallback;
import com.android.server.uwb.discovery.info.FiraConnectorCapabilities;
import com.android.server.uwb.discovery.info.FiraConnectorDataPacket;
import com.android.server.uwb.discovery.info.FiraConnectorMessage;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Class for FiRa CP UWB transport server provider using Bluetooth GATT.
 *
 * <p>The GATT server simply waits for the discovery from client side. It shall also wait for at
 * least one valid update of FiRa Connector Capabilities characteristic value from the client side.
 * Until this happens and until the client enables the Handle Value Notification method on the "OUT"
 * Control Point characteristic (through Client Characteristic Configuration Descriptor), the server
 * shall ignore all commands sent by Write methods through the "IN" Control Point characteristic.
 */
@WorkerThread
public class GattTransportServerProvider extends TransportServerProvider {
    private static final String TAG = GattTransportServerProvider.class.getSimpleName();

    private TransportServerCallback mTransportServerCallback;
    private BluetoothManager mBluetoothManager;
    private BluetoothGattServer mBluetoothGattServer;
    private BluetoothDevice mRemoteGattDevice;
    private FiraConnectorCapabilities mRemoteCapabilities;
    private boolean mConnected;
    private boolean mNotificationEnabled;

    private BluetoothGattService mFiraCPService =
            new BluetoothGattService(
                    UuidConstants.FIRA_CP_PARCEL_UUID.getUuid(),
                    BluetoothGattService.SERVICE_TYPE_PRIMARY);

    private BluetoothGattCharacteristic mInControlPointCharacteristic;
    private BluetoothGattCharacteristic mOutControlPointCharacteristic;
    private BluetoothGattCharacteristic mCapabilitiesCharacteristic;

    private BluetoothGattDescriptor mOutControlPointCccdDescriptor;

    /* Queue of Fira Connector Data Packets from the mInControlPointCharacteristic that are
     * incomplete to be constructed as FiRa Connector Message.
     */
    private ArrayDeque<FiraConnectorDataPacket> mIncompleteInDataPacketQueue;

    /* Queue of Fira Connector Message wrapped as MessagePacket to be sent via the
     * mOutControlPointCharacteristic.
     */
    private ArrayDeque<MessagePacket> mOutMessageQueue;

    /**
     * GATT server callbacks responsible for servicing read and write calls from the remote device
     */
    private BluetoothGattServerCallback mBluetoothGattServerCallback =
            new BluetoothGattServerCallback() {
                @Override
                public void onConnectionStateChange(
                        BluetoothDevice device, int status, int newState) {
                    Log.i(TAG, "onConnectionStateChange state:" + newState + " Device:" + device);
                    if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        mConnected = false;
                        startProcessing(device);
                    } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                        mConnected = true;
                        startProcessing(device);
                    }
                }

                @Override
                public void onCharacteristicReadRequest(
                        BluetoothDevice device,
                        int requestId,
                        int offset,
                        BluetoothGattCharacteristic characteristic) {
                    Log.d(TAG, "onCharacteristicReadRequest");
                    if (characteristic.getUuid().equals(mOutControlPointCharacteristic.getUuid())) {
                        Log.d(TAG, "onRead OutControlPointCharacteristic");
                        mBluetoothGattServer.sendResponse(
                                device,
                                requestId,
                                BluetoothGatt.GATT_SUCCESS,
                                offset,
                                mOutControlPointCharacteristic.getValue());
                        processOutDataPacket();
                    } else {
                        Log.w(TAG, "onRead unknown " + characteristic.getUuid());
                    }
                }

                @Override
                public void onCharacteristicWriteRequest(
                        BluetoothDevice device,
                        int requestId,
                        BluetoothGattCharacteristic characteristic,
                        boolean preparedWrite,
                        boolean responseNeeded,
                        int offset,
                        byte[] value) {
                    Log.d(
                            TAG,
                            "onCharacteristicWriteRequest uuid:"
                                    + characteristic.getUuid()
                                    + ", Length: "
                                    + value.length);
                    if (characteristic.getUuid().equals(mCapabilitiesCharacteristic.getUuid())) {
                        Log.i(TAG, "onWrite CapabilitiesCharacteristic");
                        mRemoteCapabilities = FiraConnectorCapabilities.fromBytes(value);

                        if (mRemoteCapabilities != null) {
                            mTransportServerCallback.onCapabilitesUpdated(mRemoteCapabilities);
                            startProcessing(device);

                            if (responseNeeded) {
                                mBluetoothGattServer.sendResponse(
                                        device,
                                        requestId,
                                        BluetoothGatt.GATT_SUCCESS,
                                        offset,
                                        value);
                            }
                            return;
                        }
                        if (responseNeeded) {
                            mBluetoothGattServer.sendResponse(
                                    device,
                                    requestId,
                                    BluetoothGatt.GATT_FAILURE,
                                    offset,
                                    /*value=*/ null);
                        }
                    } else if (characteristic
                            .getUuid()
                            .equals(mInControlPointCharacteristic.getUuid())) {
                        Log.d(TAG, "onWrite InControlPointCharacteristic");

                        boolean success = processInDataPacket(value);

                        if (responseNeeded) {
                            if (success) {
                                mBluetoothGattServer.sendResponse(
                                        device,
                                        requestId,
                                        BluetoothGatt.GATT_SUCCESS,
                                        offset,
                                        value);
                            } else {
                                mBluetoothGattServer.sendResponse(
                                        device,
                                        requestId,
                                        BluetoothGatt.GATT_FAILURE,
                                        offset,
                                        /*value=*/ null);
                            }
                        }
                    } else {
                        Log.w(TAG, "onWrite unknown " + characteristic.getUuid());
                    }
                }

                @Override
                public void onDescriptorWriteRequest(
                        BluetoothDevice device,
                        int requestId,
                        BluetoothGattDescriptor descriptor,
                        boolean preparedWrite,
                        boolean responseNeeded,
                        int offset,
                        byte[] value) {
                    Log.d(
                            TAG,
                            "onDescriptorWriteRequest uuid:"
                                    + descriptor.getUuid()
                                    + ", Length: "
                                    + value.length);
                    if (descriptor.getUuid().equals(mOutControlPointCccdDescriptor.getUuid())) {
                        if (Arrays.equals(
                                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, value)) {
                            Log.d(TAG, "Enable OutControlPoint value notifications: " + device);
                            mNotificationEnabled = true;
                            startProcessing(device);
                        } else if (Arrays.equals(
                                BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, value)) {
                            Log.d(TAG, "Disable OutControlPoint value notifications: " + device);
                            mNotificationEnabled = false;
                            startProcessing(device);
                        }
                        if (responseNeeded) {
                            mBluetoothGattServer.sendResponse(
                                    device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                        }
                    } else {
                        Log.w(TAG, "onDescriptorWrite unknown " + descriptor.getUuid());
                        if (responseNeeded) {
                            mBluetoothGattServer.sendResponse(
                                    device,
                                    requestId,
                                    BluetoothGatt.GATT_FAILURE,
                                    offset,
                                    /*value=*/ null);
                        }
                    }
                }
            };

    public GattTransportServerProvider(
            AttributionSource attributionSource,
            Context context,
            int secid,
            TransportServerCallback transportServerCallback) {
        super(secid);
        Context attributedContext =
                context.createContext(
                        new ContextParams.Builder()
                                .setNextAttributionSource(attributionSource)
                                .build());
        mTransportServerCallback = transportServerCallback;
        mBluetoothManager = attributedContext.getSystemService(BluetoothManager.class);
        mBluetoothGattServer =
                mBluetoothManager.openGattServer(attributedContext, mBluetoothGattServerCallback);

        mIncompleteInDataPacketQueue = new ArrayDeque();
        mOutMessageQueue = new ArrayDeque();

        setupGattCharacteristic();
    }

    @Override
    public boolean start() {
        if (!super.start()) {
            return false;
        }
        if (mBluetoothGattServer == null) {
            Log.w(TAG, "start failed due to mBluetoothGattServer is null.");
            return false;
        }
        boolean succeed = mBluetoothGattServer.addService(mFiraCPService);

        mStarted = succeed;
        return succeed;
    }

    @Override
    public boolean stop() {
        if (!super.stop()) {
            return false;
        }
        boolean succeed = mBluetoothGattServer.removeService(mFiraCPService);

        // Clear in/out message queue.
        mIncompleteInDataPacketQueue.clear();
        mOutMessageQueue.clear();

        mStarted = !succeed;
        return succeed;
    }

    @Override
    public boolean sendMessage(int secid, FiraConnectorMessage message) {
        if (!isProcessing()) {
            Log.w(TAG, "Sent request failed due to server not ready for processing.");
            return false;
        }
        byte[] messageBytes = message.toBytes();
        if (messageBytes.length > mRemoteCapabilities.maxMessageBufferSize) {
            Log.w(
                    TAG,
                    "Sent request failed due to message size exceeded remote device capabilities.");
            return false;
        }
        mOutMessageQueue.add(new MessagePacket(secid, ByteBuffer.wrap(messageBytes)));

        // No existing meesage in progress, send this message immediately.
        if (mOutMessageQueue.size() == 1) {
            return processOutDataPacket();
        }
        return true;
    }

    /**
     * Process the next out control data packet from the queue. Notify remote device if new data
     * packet is set in the {@link mOutControlPointCharacteristic}.
     *
     * @return indicate if next out data packet was process successfully.
     */
    private boolean processOutDataPacket() {
        if (!isProcessing()) {
            Log.w(TAG, "processOutDataPacket failed due to server not ready for processing.");
            return false;
        }
        if (mOutMessageQueue.isEmpty()) {
            Log.d(TAG, "processOutDataPacket skipped due to empty queue.");
            return false;
        }
        MessagePacket messagePacket = mOutMessageQueue.peek();
        ByteBuffer byteBuffer = messagePacket.messageBytes;
        byte[] nextPayload =
                new byte
                        [Math.min(
                                byteBuffer.remaining(),
                                mRemoteCapabilities.optimizedDataPacketSize
                                        - FiraConnectorDataPacket.HEADER_SIZE)];
        byteBuffer.get(nextPayload);

        FiraConnectorDataPacket dataPacket =
                new FiraConnectorDataPacket(
                        /*lastChainingPacket=*/ !byteBuffer.hasRemaining(),
                        messagePacket.secid,
                        nextPayload);

        if (!byteBuffer.hasRemaining()) {
            mOutMessageQueue.pop();
        }
        if (!mOutControlPointCharacteristic.setValue(dataPacket.toBytes())) {
            Log.w(
                    TAG,
                    "processOutDataPacket failed due to fail to set"
                            + " mOutControlPointCharacteristic.");
            return false;
        }
        if (!mBluetoothGattServer.notifyCharacteristicChanged(
                mRemoteGattDevice, mOutControlPointCharacteristic, /*confirm=*/ false)) {
            Log.w(TAG, "processOutDataPacket failed due to fail to notifyCharacteristicChanged.");
            return false;
        }
        return true;
    }

    /**
     * Process the next in control data packet. Construct the FiraConnectorMEssage if data is
     * complete, and notify callback with the constructed message.
     *
     * @return indicate if next in data packet was process successfully.
     */
    private boolean processInDataPacket(byte[] bytes) {
        if (!isProcessing()) {
            Log.w(TAG, "processInDataPacket failed due to server not ready for processing.");
            return false;
        }
        FiraConnectorDataPacket latestDataPacket = FiraConnectorDataPacket.fromBytes(bytes);
        if (latestDataPacket == null) {
            Log.w(
                    TAG,
                    "processInDataPacket failed due to latest FiraConnectorDataPacket cannot be"
                            + " constructed from bytes.");
            return false;
        }
        if (!mIncompleteInDataPacketQueue.isEmpty()
                && latestDataPacket.secid != mIncompleteInDataPacketQueue.peek().secid) {
            Log.w(
                    TAG,
                    "processInDataPacket failed due to latest FiraConnectorDataPacket's SECID"
                            + " doesn't match previous data packet.");
            return false;
        }
        mIncompleteInDataPacketQueue.add(latestDataPacket);
        if (!latestDataPacket.lastChainingPacket) {
            return true;
        }
        // All data packets of the message has been received. Constructing the message.
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        for (FiraConnectorDataPacket dataPacket : mIncompleteInDataPacketQueue) {
            byteStream.write(dataPacket.payload, /*off=*/ 0, dataPacket.payload.length);
        }
        mIncompleteInDataPacketQueue.clear();

        FiraConnectorMessage message = FiraConnectorMessage.fromBytes(byteStream.toByteArray());
        if (message == null) {
            Log.w(
                    TAG,
                    "processInDataPacket failed due to FiraConnectorMessage cannot be constructed"
                            + " from bytes.");
            return false;
        }

        super.onMessageReceived(latestDataPacket.secid, message);
        return true;
    }

    /**
     * Start processing of the FiRa Connector Data Packets and the FiRa Connector Messages through
     * the In/Out control point characterstic when all conditions are meet to start the FiRa GATT
     * server.
     *
     * @param device Remote Bluetooth device.
     */
    private void startProcessing(BluetoothDevice device) {
        if (!mConnected || !mNotificationEnabled || mRemoteCapabilities == null) {
            Log.d(
                    TAG,
                    "Gatt server not fully ready: connected="
                            + mConnected
                            + ", notification enabled="
                            + mNotificationEnabled
                            + ", valid"
                            + " capabilities="
                            + (mRemoteCapabilities = null));
            boolean stopping = isProcessing();
            mRemoteGattDevice = null;
            if (stopping) {
                mTransportServerCallback.onProcessingStopped();
            }
            return;
        }
        mRemoteGattDevice = device;
        mTransportServerCallback.onProcessingStarted();
    }

    /**
     * Check if processing has started.
     *
     * @return indicate if server has started processing.
     */
    private boolean isProcessing() {
        return mRemoteGattDevice != null;
    }

    /**
     * Initialize all of the GATT characteristics with appropriate default values and the required
     * configurations.
     */
    private void setupGattCharacteristic() {
        mInControlPointCharacteristic =
                new BluetoothGattCharacteristic(
                        UuidConstants.CP_IN_CONTROL_POINT_UUID.getUuid(),
                        BluetoothGattCharacteristic.PROPERTY_WRITE,
                        BluetoothGattCharacteristic.PERMISSION_WRITE);
        mFiraCPService.addCharacteristic(mInControlPointCharacteristic);

        mOutControlPointCharacteristic =
                new BluetoothGattCharacteristic(
                        UuidConstants.CP_OUT_CONTROL_POINT_UUID.getUuid(),
                        BluetoothGattCharacteristic.PROPERTY_READ
                                | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                        BluetoothGattCharacteristic.PERMISSION_READ);
        mOutControlPointCccdDescriptor =
                new BluetoothGattDescriptor(
                        UuidConstants.CCCD_UUID.getUuid(), BluetoothGattDescriptor.PERMISSION_READ);
        mOutControlPointCharacteristic.addDescriptor(mOutControlPointCccdDescriptor);
        mFiraCPService.addCharacteristic(mOutControlPointCharacteristic);

        mCapabilitiesCharacteristic =
                new BluetoothGattCharacteristic(
                        UuidConstants.CP_FIRA_CONNECTOR_CAPABILITIES_UUID.getUuid(),
                        BluetoothGattCharacteristic.PROPERTY_WRITE,
                        BluetoothGattCharacteristic.PERMISSION_WRITE);
        mFiraCPService.addCharacteristic(mCapabilitiesCharacteristic);
    }

    @Override
    protected void terminateOnError(TerminationReason reason) {
        Log.e(TAG, "GattTransportServerProvider terminated with reason:" + reason);
        stop();
        mTransportServerCallback.onTerminated(reason);
    }
}
