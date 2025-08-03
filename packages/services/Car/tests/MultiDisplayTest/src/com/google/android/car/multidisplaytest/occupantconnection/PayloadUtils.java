/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.google.android.car.multidisplaytest.occupantconnection;

import android.car.occupantconnection.Payload;

import com.google.android.car.multidisplaytest.occupantconnection.PayloadProto.Data;
import com.google.protobuf.InvalidProtocolBufferException;

/** Utility methods for serializing and deserializing the Payload. */
class PayloadUtils {

    private PayloadUtils() {
    }

    static Payload createPayload(String receiverEndpointId, String text) {
        Data data = Data.newBuilder()
                .setReceiverEndpointId(receiverEndpointId)
                .setText(text)
                .build();
        return new Payload(data.toByteArray());
    }

    static Payload createPayload(String receiverEndpointId, int progress) {
        Data data = Data.newBuilder()
                .setReceiverEndpointId(receiverEndpointId)
                .setProgress(progress)
                .build();
        return new Payload(data.toByteArray());
    }

    static Data parseData(Payload payload) {
        try {
            return Data.parseFrom(payload.getBytes());
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Invalid payload: " + e);
        }
    }
}
