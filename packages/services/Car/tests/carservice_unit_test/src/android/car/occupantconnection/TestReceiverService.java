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
package android.car.occupantconnection;

import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Pair;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

final class TestReceiverService extends AbstractReceiverService {

    // The following lists are used to verify an onFoo() method was invoked with certain parameters.
    public final List<Pair<OccupantZoneInfo, Payload>> onPayloadReceivedInvokedRecords =
            new ArrayList<>();
    public final List<String> onReceiverRegisteredInvokedRecords = new ArrayList<>();
    public final List<OccupantZoneInfo> onConnectionInitiatedInvokedRecords = new ArrayList<>();
    public final List<OccupantZoneInfo> onConnectedInvokedRecords = new ArrayList<>();
    public final List<OccupantZoneInfo> onConnectionCanceledInvokedRecords = new ArrayList<>();
    public final List<OccupantZoneInfo> onDisconnectedInvokedRecords = new ArrayList<>();

    public final Binder localBinder = new Binder();

    @Override
    public void onPayloadReceived(OccupantZoneInfo senderZone, Payload payload) {
        onPayloadReceivedInvokedRecords.add(new Pair(senderZone, payload));
    }

    @Override
    public void onReceiverRegistered(String receiverEndpointId) {
        onReceiverRegisteredInvokedRecords.add(receiverEndpointId);
    }

    @Override
    public void onConnectionInitiated(OccupantZoneInfo senderZone) {
        onConnectionInitiatedInvokedRecords.add(senderZone);
    }

    @Override
    public void onConnected(OccupantZoneInfo senderZone) {
        onConnectedInvokedRecords.add(senderZone);
    }

    @Override
    public void onConnectionCanceled(OccupantZoneInfo senderZone) {
        onConnectionCanceledInvokedRecords.add(senderZone);
    }

    @Override
    public void onDisconnected(OccupantZoneInfo senderZone) {
        onDisconnectedInvokedRecords.add(senderZone);
    }

    @Nullable
    @Override
    public IBinder onLocalServiceBind(Intent intent) {
        return localBinder;
    }
}
