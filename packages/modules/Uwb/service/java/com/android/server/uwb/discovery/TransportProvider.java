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
package com.android.server.uwb.discovery;

import android.annotation.IntRange;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.uwb.discovery.info.AdminErrorMessage;
import com.android.server.uwb.discovery.info.AdminErrorMessage.ErrorType;
import com.android.server.uwb.discovery.info.AdminEventMessage;
import com.android.server.uwb.discovery.info.AdminEventMessage.EventType;
import com.android.server.uwb.discovery.info.FiraConnectorMessage;
import com.android.server.uwb.discovery.info.FiraConnectorMessage.InstructionCode;
import com.android.server.uwb.discovery.info.FiraConnectorMessage.MessageType;

import java.nio.ByteBuffer;

/** Abstract class for Transport Provider */
public abstract class TransportProvider implements Transport {
    private static final String TAG = TransportProvider.class.getSimpleName();

    public enum TerminationReason {
        /** Disconnection of the remote GATT service. */
        REMOTE_DISCONNECTED,
        /** remote GATT service discovery failure. */
        SERVICE_DISCOVERY_FAILURE,
        /** Characterstic read failure */
        CHARACTERSTIC_READ_FAILURE,
        /** Characterstic write failure */
        CHARACTERSTIC_WRITE_FAILURE,
        /** Descriptor write failure */
        DESCRIPTOR_WRITE_FAILURE,
        /** Remote device message error */
        REMOTE_DEVICE_MESSAGE_ERROR,
        /** Remote device SECID error */
        REMOTE_DEVICE_SECID_ERROR,
    }

    /** Callback for listening to transport events. */
    public interface TransportCallback {

        /** Called when the transport started processing. */
        void onProcessingStarted();

        /** Called when the transport stopped processing. */
        void onProcessingStopped();

        /**
         * Called when the transport terminated the connection due to an unrecoverable errors.
         *
         * @param reason indicates the termination reason.
         */
        void onTerminated(TerminationReason reason);
    }

    /**
     * administrative SECID shall be exposed on each CS implementation at all times. It shall be
     * marked as static.
     */
    public static final int ADMIN_SECID = 1;

    private DataReceiver mDataReceiver;

    /** Assigned SECID value (unsigned integer in the range 2..127, values 0 and 1 are reserved). */
    private int mSecid = 2;

    /**
     * Remote device SECID value (unsigned integer in the range 2..127, values 0 and 1 are
     * reserved).
     */
    private int mDestinationSecid = 2;

    /** Wraps Fira Connector Message byte array and the associated SECID. */
    public static class MessagePacket {
        public final int secid;
        public ByteBuffer messageBytes;

        public MessagePacket(int secid, ByteBuffer messageBytes) {
            this.secid = secid;
            this.messageBytes = messageBytes;
        }
    }

    protected TransportProvider(@IntRange(from = 2, to = 127) int secid) {
        mSecid = secid;
    }

    /**
     * Set the Destination SECID on the Remote device.
     *
     * @param secid 7-bit secure component ID.
     */
    public void setDestinationSecid(@IntRange(from = 2, to = 127) int secid) {
        mDestinationSecid = secid;
    }

    @Override
    public void sendData(
            MessageType messageType,
            @NonNull byte[] data,
            SendingDataCallback sendingDataCallback) {
        if (sendMessage(
                mDestinationSecid,
                new FiraConnectorMessage(
                        messageType,
                        /*Default instrcution code for message exchange.*/
                        InstructionCode.DATA_EXCHANGE,
                        data))) {
            sendingDataCallback.onSuccess();
        } else {
            sendingDataCallback.onFailure();
        }
    }

    @Override
    public void registerDataReceiver(DataReceiver dataReceiver) {
        if (mDataReceiver != null) {
            Log.w(TAG, "Already has a registered data receiver.");
            return;
        }
        mDataReceiver = dataReceiver;
    }

    @Override
    public void unregisterDataReceiver() {
        mDataReceiver = null;
    }

    /* Indicates whether the server has started.
     */
    protected boolean mStarted = false;

    /**
     * Checks if the server has started.
     *
     * @return indicates if the server has started.
     */
    public boolean isStarted() {
        return mStarted;
    }

    /**
     * Starts the transport.
     *
     * @return indicates if successfully started.
     */
    public boolean start() {
        if (isStarted()) {
            Log.i(TAG, "Transport already started.");
            return false;
        }
        return true;
    }

    /**
     * Stops the transport.
     *
     * @return indicates if successfully stopped.
     */
    public boolean stop() {
        if (!isStarted()) {
            Log.i(TAG, "Transport already stopped.");
            return false;
        }
        return true;
    }

    /**
     * Send a FiRa connector message to the remote device through the transport.
     *
     * @param secid destination SECID on remote device.
     * @param message message to be send.
     * @return indicates if successfully started.
     */
    public abstract boolean sendMessage(int secid, FiraConnectorMessage message);

    /**
     * Called when the client received a new FiRa connector message from the remote device.
     *
     * @param secid destination SECID on this device.
     * @param message FiRa connector message.
     */
    protected void onMessageReceived(int secid, FiraConnectorMessage message) {
        if (secid == ADMIN_SECID) {
            processAdminMessage(message);
            return;
        }
        if (secid != mSecid) {
            Log.w(
                    TAG,
                    "onMessageReceived rejected due to invalid SECID. Expect:"
                            + mSecid
                            + " Received:"
                            + secid);
            sentAdminErrorMessage(ErrorType.SECID_INVALID);
            return;
        }
        if (mDataReceiver != null) {
            mDataReceiver.onDataReceived(message.payload);
        }
    }

    /**
     * Send a FiRa OOB administrative Error message to the administrative SECID on the remote
     * device.
     *
     * @param errorType ErrorType of the message.
     */
    protected void sentAdminErrorMessage(ErrorType errorType) {
        if (!sendMessage(ADMIN_SECID, new AdminErrorMessage(errorType))) {
            Log.w(TAG, "sentAdminErrorMessage with ErrorType:" + errorType + " failed.");
        }
    }

    /**
     * Send a FiRa OOB administrative Event message to the administrative SECID on the remote
     * device.
     *
     * @param eventType EventType of the message.
     * @param additionalData additional data associated with the event.
     */
    protected void sentAdminEventMessage(EventType eventType, byte[] additionalData) {
        if (!sendMessage(ADMIN_SECID, new AdminEventMessage(eventType, additionalData))) {
            Log.w(TAG, "sentAdminEventMessage with EventType:" + eventType + " failed.");
        }
    }

    /**
     * Process FiRa OOB administrative message from the remote device.
     *
     * @param message FiRa connector message.
     */
    private void processAdminMessage(FiraConnectorMessage message) {
        if (AdminErrorMessage.isAdminErrorMessage(message)) {
            AdminErrorMessage errorMessage = AdminErrorMessage.convertToAdminErrorMessage(message);
            Log.w(TAG, "Received AdminErrorMessage:" + errorMessage);
            switch (errorMessage.errorType) {
                case DATA_PACKET_LENGTH_OVERFLOW:
                case MESSAGE_LENGTH_OVERFLOW:
                case TOO_MANY_CONCURRENT_FRAGMENTED_MESSAGE_SESSIONS:
                    terminateOnError(TerminationReason.REMOTE_DEVICE_MESSAGE_ERROR);
                    break;
                case SECID_INVALID:
                case SECID_INVALID_FOR_RESPONSE:
                case SECID_BUSY:
                case SECID_PROTOCOL_ERROR:
                case SECID_INTERNAL_ERROR:
                    terminateOnError(TerminationReason.REMOTE_DEVICE_SECID_ERROR);
                    break;
            }
        } else if (AdminEventMessage.isAdminEventMessage(message)) {
            AdminEventMessage eventMessage = AdminEventMessage.convertToAdminEventMessage(message);
            Log.w(TAG, "Received AdminEventMessage:" + eventMessage);
            switch (eventMessage.eventType) {
                case CAPABILITIES_CHANGED:
                    // No-op since this is only applicatble for CS with the role of GATT Server,
                    // which isn't mandated by FiRa.
                    break;
            }
        } else {
            Log.e(TAG, "Invalid Admin FiraConnectorMessage received:" + message);
        }
    }

    /**
     * Terminates the transport provider.
     *
     * @param reason reason for the termination.
     */
    protected abstract void terminateOnError(TerminationReason reason);
}
