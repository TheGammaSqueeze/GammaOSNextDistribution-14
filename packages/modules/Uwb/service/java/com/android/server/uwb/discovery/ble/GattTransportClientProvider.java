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
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.content.AttributionSource;
import android.content.Context;
import android.content.ContextParams;
import android.util.Log;

import androidx.annotation.WorkerThread;

import com.android.server.uwb.discovery.TransportClientProvider;
import com.android.server.uwb.discovery.TransportClientProvider.TransportClientCallback;
import com.android.server.uwb.discovery.TransportProvider.MessagePacket;
import com.android.server.uwb.discovery.TransportProvider.TerminationReason;
import com.android.server.uwb.discovery.info.AdminErrorMessage.ErrorType;
import com.android.server.uwb.discovery.info.FiraConnectorCapabilities;
import com.android.server.uwb.discovery.info.FiraConnectorDataPacket;
import com.android.server.uwb.discovery.info.FiraConnectorMessage;
import com.android.server.uwb.discovery.info.SecureComponentInfo;
import com.android.server.uwb.discovery.info.TransportClientInfo;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.concurrent.Executor;

/**
 * Class for FiRa CS UWB transport client provider using Bluetooth GATT.
 *
 * <p>The GATT client is responsible for the entire Service discovery procedure. Once the device
 * discovery phase passed, the client establishes the Bluetooth connection and perform GATT service
 * and GATT characterstics discovery. When discovery complete, the client writes it's FiRa Connector
 * Capabilities into the Capabilities characteristic and enable Value Notifications on the "OUT"
 * Control Point characteristic on the remote GATT server. Afterwards, the client can starts
 * exchange data with the server over the "IN" and "OUT" Control Point characteristic. When any
 * unrecoverable event occurred, GATT client will be terminated.
 */
@WorkerThread
public class GattTransportClientProvider extends TransportClientProvider {
    private static final String TAG = GattTransportClientProvider.class.getSimpleName();

    private final Executor mCallbackExecutor;
    private final Context mContext;
    private TransportClientCallback mTransportClientCallback;
    private BluetoothDevice mRemoteBluetoothDevice;
    private BluetoothGatt mBluetoothGatt;

    private FiraConnectorCapabilities mCapabilities;

    private boolean mConnected;
    private boolean mServiceDiscovered;
    private boolean mCapabilitiesWritten;
    private boolean mNotificationEnabled;
    private boolean mIsProcessing;

    private BluetoothGattCharacteristic mInControlPointCharacteristic;
    private BluetoothGattCharacteristic mOutControlPointCharacteristic;
    private BluetoothGattCharacteristic mCapabilitiesCharacteristic;

    private BluetoothGattDescriptor mOutControlPointCccdDescriptor;

    /* Queue of Fira Connector Data Packets from the mOutControlPointCharacteristic that are
     * incomplete to be constructed as FiRa Connector Message.
     */
    private ArrayDeque<FiraConnectorDataPacket> mIncompleteOutDataPacketQueue;

    /* Queue of Fira Connector Message wrapped as MessagePacket to be sent via the
     * mInControlPointCharacteristic.
     */
    private ArrayDeque<MessagePacket> mInMessageQueue;

    /** GATT callbacks responsible for handling events from the remote device GATT server. */
    private BluetoothGattCallback mBluetoothGattCallback =
            new BluetoothGattCallback() {

                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    Log.i(TAG, "onConnectionStateChange state:" + newState);
                    mBluetoothGatt = gatt;
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.w(TAG, "onConnectionStateChange failed");
                        terminateOnError(TerminationReason.REMOTE_DISCONNECTED);
                        return;
                    }
                    if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        mConnected = false;
                        terminateOnError(TerminationReason.REMOTE_DISCONNECTED);
                        mCallbackExecutor.execute(() -> startProcessing());
                    } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                        mConnected = true;
                        mCallbackExecutor.execute(() -> startProcessing());
                    }
                }

                @Override
                public void onServiceChanged(BluetoothGatt gatt) {
                    Log.d(TAG, "onServiceChanged");
                    // Service changed event, call to re-discover the services.
                    gatt.discoverServices();
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    mBluetoothGatt = gatt;
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.w(TAG, "onServicesDiscovered failed");
                        terminateOnError(TerminationReason.SERVICE_DISCOVERY_FAILURE);
                        return;
                    }

                    BluetoothGattService service =
                            gatt.getService(UuidConstants.FIRA_CP_PARCEL_UUID.getUuid());
                    if (service == null) {
                        Log.w(
                                TAG,
                                "onServicesDiscovered FiRa CP Gatt service not found on remote"
                                        + " device.");
                        terminateOnError(TerminationReason.SERVICE_DISCOVERY_FAILURE);
                        return;
                    }
                    mInControlPointCharacteristic =
                            service.getCharacteristic(
                                    UuidConstants.CP_IN_CONTROL_POINT_UUID.getUuid());
                    mOutControlPointCharacteristic =
                            service.getCharacteristic(
                                    UuidConstants.CP_OUT_CONTROL_POINT_UUID.getUuid());
                    mCapabilitiesCharacteristic =
                            service.getCharacteristic(
                                    UuidConstants.CP_FIRA_CONNECTOR_CAPABILITIES_UUID.getUuid());
                    mOutControlPointCccdDescriptor =
                            mOutControlPointCharacteristic.getDescriptor(
                                    UuidConstants.CCCD_UUID.getUuid());

                    if (mInControlPointCharacteristic == null
                            || mOutControlPointCharacteristic == null
                            || mCapabilitiesCharacteristic == null
                            || mOutControlPointCccdDescriptor == null) {
                        Log.w(
                                TAG,
                                "onServicesDiscovered FiRa CP Gatt service characteristics and/or"
                                        + " descriptor not found.");
                        terminateOnError(TerminationReason.SERVICE_DISCOVERY_FAILURE);
                        return;
                    }
                    mServiceDiscovered = true;
                    mCallbackExecutor.execute(() -> startProcessing());
                }

                @Override
                public void onCharacteristicChanged(
                        BluetoothGatt gatt,
                        BluetoothGattCharacteristic characteristic,
                        byte[] value) {
                    if (characteristic.getUuid().equals(mOutControlPointCharacteristic.getUuid())) {
                        mBluetoothGatt = gatt;
                        mCallbackExecutor.execute(() -> readOutControlPointCharacteristic());
                    }
                }

                @Override
                public void onCharacteristicRead(
                        BluetoothGatt gatt,
                        BluetoothGattCharacteristic characteristic,
                        byte[] value,
                        int status) {
                    mBluetoothGatt = gatt;
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.e(TAG, "onCharacteristicRead failed uuid:" + characteristic.getUuid());
                        terminateOnError(TerminationReason.CHARACTERSTIC_READ_FAILURE);
                        return;
                    }
                    if (characteristic.getUuid().equals(mOutControlPointCharacteristic.getUuid())) {
                        mCallbackExecutor.execute(() -> processOutDataPacket(value));
                    }
                }

                @Override
                public void onCharacteristicWrite(
                        BluetoothGatt gatt,
                        BluetoothGattCharacteristic characteristic,
                        int status) {
                    mBluetoothGatt = gatt;
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.e(TAG, "onCharacteristicWrite failed uuid:" + characteristic.getUuid());
                        terminateOnError(TerminationReason.CHARACTERSTIC_WRITE_FAILURE);
                        return;
                    }
                    if (characteristic.getUuid().equals(mCapabilitiesCharacteristic.getUuid())) {
                        mCapabilitiesWritten = true;
                        mCallbackExecutor.execute(() -> startProcessing());
                    } else if (characteristic
                            .getUuid()
                            .equals(mInControlPointCharacteristic.getUuid())) {
                        mCallbackExecutor.execute(() -> processInDataPacket());
                    }
                }

                @Override
                public void onDescriptorWrite(
                        BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                    mBluetoothGatt = gatt;
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.e(TAG, "onDescriptorWrite failed uuid:" + descriptor.getUuid());
                        terminateOnError(TerminationReason.DESCRIPTOR_WRITE_FAILURE);
                        return;
                    }
                    if (descriptor.getUuid().equals(mOutControlPointCccdDescriptor.getUuid())) {
                        mNotificationEnabled = true;
                        mCallbackExecutor.execute(() -> startProcessing());
                    }
                }

                @Override
                public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                    mBluetoothGatt = gatt;
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.e(TAG, "onMtuChanged failed");
                        return;
                    }
                    Log.d(TAG, "onMtuChanged new mtu=" + mtu);
                    // Update Capabilities if changed, and sent to server.
                    int newDataPacketSize = mtu - 3;
                    if (newDataPacketSize == mCapabilities.optimizedDataPacketSize) {
                        return;
                    }
                    FiraConnectorCapabilities.Builder builder =
                            new FiraConnectorCapabilities.Builder()
                                    .setProtocolVersion(mCapabilities.protocolVersion)
                                    .setMaxMessageBufferSize(mCapabilities.maxMessageBufferSize)
                                    .setMaxConcurrentFragmentedMessageSessionSupported(
                                            mCapabilities
                                                    .maxConcurrentFragmentedMessageSessionSupported)
                                    .setOptimizedDataPacketSize(newDataPacketSize);
                    for (SecureComponentInfo info : mCapabilities.secureComponentInfos) {
                        builder.addSecureComponentInfo(info);
                    }
                    mCallbackExecutor.execute(() -> setCapabilites(builder.build()));
                }
            };

    public GattTransportClientProvider(
            AttributionSource attributionSource,
            Context context,
            Executor executor,
            int secid,
            TransportClientInfo transportClientInfo,
            TransportClientCallback transportServerCallback) {
        super(secid);
        mCallbackExecutor = executor;
        mContext =
                context.createContext(
                        new ContextParams.Builder()
                                .setNextAttributionSource(attributionSource)
                                .build());
        mTransportClientCallback = transportServerCallback;
        mRemoteBluetoothDevice = transportClientInfo.scanResult.getDevice();

        // Using FiRa defined default connector capabilities.
        mCapabilities = new FiraConnectorCapabilities.Builder().build();

        mIncompleteOutDataPacketQueue = new ArrayDeque();
        mInMessageQueue = new ArrayDeque();
    }

    @Override
    public boolean start() {
        if (!super.start()) {
            return false;
        }
        if (mRemoteBluetoothDevice == null) {
            Log.w(TAG, "start failed due to BluetoothDevice is null.");
            return false;
        }
        boolean succeed = true;
        if (mBluetoothGatt == null) {
            // Connects to the GATT server on the remote device.
            mBluetoothGatt =
                    mRemoteBluetoothDevice.connectGatt(
                            mContext,
                            /*autoConnect=*/ false,
                            mBluetoothGattCallback,
                            BluetoothDevice.TRANSPORT_LE);
        } else {
            succeed = mBluetoothGatt.connect();
        }

        mStarted = succeed;
        return succeed;
    }

    @Override
    public boolean stop() {
        if (!super.stop()) {
            return false;
        }
        if (mBluetoothGatt == null) {
            Log.w(TAG, "stop failed due to BluetoothGatt is null.");
            return false;
        }
        mBluetoothGatt.disconnect();

        // Clear in/out message queue.
        mIncompleteOutDataPacketQueue.clear();
        mInMessageQueue.clear();

        mStarted = false;
        mConnected = false;
        mServiceDiscovered = false;
        mCapabilitiesWritten = false;
        mNotificationEnabled = false;
        startProcessing();
        return true;
    }

    @Override
    public boolean sendMessage(int secid, FiraConnectorMessage message) {
        if (!isProcessing()) {
            Log.w(TAG, "Sent request failed due to server not ready for processing.");
            return false;
        }
        byte[] messageBytes = message.toBytes();
        if (messageBytes.length > mCapabilities.maxMessageBufferSize) {
            Log.w(TAG, "Sent request failed due to message size exceeded device capabilities.");
            return false;
        }
        mInMessageQueue.add(new MessagePacket(secid, ByteBuffer.wrap(messageBytes)));

        // No existing meesage in progress, sent this message immediately.
        if (mInMessageQueue.size() == 1) {
            return processInDataPacket();
        }
        return true;
    }

    @Override
    public boolean setCapabilites(FiraConnectorCapabilities capabilities) {
        if (capabilities == null) {
            Log.e(TAG, "setCapabilites failed null capabilities.");
            return false;
        }
        Log.d(TAG, "setCapabilites new capabilities:" + capabilities);
        mCapabilities = capabilities;
        if (!mStarted || !mServiceDiscovered) {
            Log.w(
                    TAG,
                    "setCapabilites only updated locally since client hasn't started or service not"
                            + " discovered.");
            return false;
        }
        return writeCapabilitiesCharacteristic();
    }

    /**
     * Process the next in control data packet from the queue. Write new data packet to {@link
     * mInControlPointCharacteristic}.
     *
     * @return indicate if next in data packet was process successfully.
     */
    private boolean processInDataPacket() {
        if (!isProcessing()) {
            Log.w(TAG, "processInDataPacket failed due to client not ready for processing.");
            return false;
        }
        if (mInMessageQueue.isEmpty()) {
            Log.w(TAG, "processInDataPacket skipped due to empty queue.");
            return false;
        }
        MessagePacket messagePacket = mInMessageQueue.peek();
        ByteBuffer byteBuffer = messagePacket.messageBytes;
        byte[] nextPayload =
                new byte
                        [Math.min(
                                byteBuffer.remaining(),
                                mCapabilities.optimizedDataPacketSize
                                        - FiraConnectorDataPacket.HEADER_SIZE)];
        byteBuffer.get(nextPayload);

        final FiraConnectorDataPacket dataPacket =
                new FiraConnectorDataPacket(
                        /*lastChainingPacket=*/ !byteBuffer.hasRemaining(),
                        messagePacket.secid,
                        nextPayload);

        if (!byteBuffer.hasRemaining()) {
            mInMessageQueue.pop();
        }
        final int status =
                mBluetoothGatt.writeCharacteristic(
                        mInControlPointCharacteristic,
                        dataPacket.toBytes(),
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        if (status != BluetoothStatusCodes.SUCCESS) {
            Log.w(TAG, "processInDataPacket failed due to fail to writeCharacteristic.");
            terminateOnError(TerminationReason.CHARACTERSTIC_WRITE_FAILURE);
            return false;
        }
        return true;
    }

    /**
     * Process the new out control data packet. Construct the FiraConnectorMessage if data is
     * complete, and notify callback with the constructed message.
     *
     * @return indicate if new out data packet was process successfully.
     */
    private boolean processOutDataPacket(byte[] bytes) {
        if (!isProcessing()) {
            Log.w(TAG, "processOutDataPacket failed due to server not ready for processing.");
            return false;
        }
        if (bytes.length > mCapabilities.optimizedDataPacketSize) {
            Log.w(TAG, "processOutDataPacket failed due to data packet length overflow.");
            super.sentAdminErrorMessage(ErrorType.DATA_PACKET_LENGTH_OVERFLOW);
            return false;
        }
        FiraConnectorDataPacket latestDataPacket = FiraConnectorDataPacket.fromBytes(bytes);
        if (latestDataPacket == null) {
            Log.w(
                    TAG,
                    "processOutDataPacket failed due to latest FiraConnectorDataPacket cannot be"
                            + " constructed from bytes.");
            return false;
        }
        if (!mIncompleteOutDataPacketQueue.isEmpty()
                && latestDataPacket.secid != mIncompleteOutDataPacketQueue.peek().secid) {
            Log.w(
                    TAG,
                    "processOutDataPacket failed due to latest FiraConnectorDataPacket's SECID"
                            + " doesn't match previous data packet.");
            super.sentAdminErrorMessage(ErrorType.TOO_MANY_CONCURRENT_FRAGMENTED_MESSAGE_SESSIONS);
            return false;
        }
        mIncompleteOutDataPacketQueue.add(latestDataPacket);
        if (!latestDataPacket.lastChainingPacket) {
            return true;
        }
        // All data packets of the message has been received. Constructing the message.
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        for (FiraConnectorDataPacket dataPacket : mIncompleteOutDataPacketQueue) {
            byteStream.write(dataPacket.payload, /*off=*/ 0, dataPacket.payload.length);
        }
        mIncompleteOutDataPacketQueue.clear();

        if (byteStream.size() > mCapabilities.maxMessageBufferSize) {
            Log.w(TAG, "processOutDataPacket failed due to message length overflow.");
            super.sentAdminErrorMessage(ErrorType.MESSAGE_LENGTH_OVERFLOW);
            return false;
        }

        FiraConnectorMessage message = FiraConnectorMessage.fromBytes(byteStream.toByteArray());
        if (message == null) {
            Log.w(
                    TAG,
                    "processOutDataPacket failed due to FiraConnectorMessage cannot be constructed"
                            + " from bytes.");
            return false;
        }

        super.onMessageReceived(latestDataPacket.secid, message);
        return true;
    }

    /**
     * Start processing of the FiRa Connector Data Packets and the FiRa Connector Messages through
     * the In/Out control point characterstic on the remote GATT server once the setup procedure
     * defined by FiRa OOB spec is complete,
     */
    private void startProcessing() {
        Log.d(
                TAG,
                "startProcessing: isProcessing="
                        + isProcessing()
                        + " (connected="
                        + mConnected
                        + ", service discovered="
                        + mServiceDiscovered
                        + ", capabilities written="
                        + mCapabilitiesWritten
                        + ", notification enabled="
                        + mNotificationEnabled
                        + ")");

        if (mConnected) {
            if (!mServiceDiscovered) {
                mBluetoothGatt.discoverServices();
            } else if (!mCapabilitiesWritten) {
                writeCapabilitiesCharacteristic();
            } else if (!mNotificationEnabled) {
                enableNotification();
            }
        }

        boolean isProcessing =
                mConnected && mServiceDiscovered && mCapabilitiesWritten && mNotificationEnabled;
        if (isProcessing == mIsProcessing) {
            return;
        }
        mIsProcessing = isProcessing;
        if (mIsProcessing) {
            mTransportClientCallback.onProcessingStarted();
        } else {
            mTransportClientCallback.onProcessingStopped();
        }
    }

    /**
     * Check if processing has started.
     *
     * @return indicate if server has started processing.
     */
    private boolean isProcessing() {
        return mIsProcessing;
    }

    private boolean writeCapabilitiesCharacteristic() {
        if (mBluetoothGatt == null) {
            Log.e(TAG, "writeCapabilitiesCharacteristic failed due to Gatt is null.");
            terminateOnError(TerminationReason.CHARACTERSTIC_WRITE_FAILURE);
            return false;
        }
        if (mCapabilitiesCharacteristic == null) {
            Log.e(TAG, "writeCapabilitiesCharacteristic failed due to characteristic is null.");
            terminateOnError(TerminationReason.CHARACTERSTIC_WRITE_FAILURE);
            return false;
        }
        final int status =
                mBluetoothGatt.writeCharacteristic(
                        mCapabilitiesCharacteristic,
                        mCapabilities.toBytes(),
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        if (status != BluetoothStatusCodes.SUCCESS) {
            terminateOnError(TerminationReason.CHARACTERSTIC_WRITE_FAILURE);
            return false;
        }
        return true;
    }

    private void enableNotification() {
        final int status =
                mBluetoothGatt.writeDescriptor(
                        mOutControlPointCccdDescriptor,
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        if (status != BluetoothStatusCodes.SUCCESS) {
            terminateOnError(TerminationReason.DESCRIPTOR_WRITE_FAILURE);
        }
    }

    private void readOutControlPointCharacteristic() {
        if (mBluetoothGatt == null) {
            Log.e(TAG, "readOutControlPointCharacteristic failed due to Gatt is null.");
            terminateOnError(TerminationReason.CHARACTERSTIC_READ_FAILURE);
            return;
        }
        if (!mBluetoothGatt.readCharacteristic(mOutControlPointCharacteristic)) {
            terminateOnError(TerminationReason.CHARACTERSTIC_READ_FAILURE);
        }
    }

    @Override
    protected void terminateOnError(TerminationReason reason) {
        Log.e(TAG, "GattTransportClientProvider terminated with reason:" + reason);
        stop();
        mTransportClientCallback.onTerminated(reason);
    }
}
