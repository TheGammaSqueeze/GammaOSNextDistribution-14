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

import androidx.annotation.NonNull;

import com.android.server.uwb.discovery.info.FiraConnectorMessage.MessageType;

/** The interface of the physical data communication channel. */
public interface Transport {
    /**
     * Send data to the remote device.
     *
     * @param messageType message type of the data to be sent.
     * @param data Raw bytes of data to be sent.
     * @param sendingDataCallback Callback for sneding data.
     */
    void sendData(
            MessageType messageType, @NonNull byte[] data, SendingDataCallback sendingDataCallback);

    /**
     * Register the data receiver, only one receiver is allowed.
     *
     * @param dataReceiver Receiver of the data from remote device.
     */
    void registerDataReceiver(DataReceiver dataReceiver);

    /** Unregister the current registered data receiver; */
    void unregisterDataReceiver();

    /** The receiver handles the incoming data from the remote device. */
    interface DataReceiver {

        /**
         * Called when new data is received from the remote device.
         *
         * @param data Raw bytes of data received.
         */
        void onDataReceived(@NonNull byte[] data);
    }

    /** The callback to notify if the data is sent out or not. */
    interface SendingDataCallback {

        /** The data is sent out. */
        void onSuccess();

        /** The data failed to be sent out. */
        void onFailure();
    }
}
