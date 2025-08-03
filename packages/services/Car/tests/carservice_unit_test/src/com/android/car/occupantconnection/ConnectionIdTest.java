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

package com.android.car.occupantconnection;

import static android.car.CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER;
import static android.car.CarOccupantZoneManager.OCCUPANT_TYPE_FRONT_PASSENGER;
import static android.car.VehicleAreaSeat.SEAT_ROW_1_LEFT;
import static android.car.VehicleAreaSeat.SEAT_ROW_1_RIGHT;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.car.CarOccupantZoneManager.OccupantZoneInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class ConnectionIdTest {

    private static final OccupantZoneInfo SENDER_ZONE = new OccupantZoneInfo(/* zoneId= */ 0,
            OCCUPANT_TYPE_DRIVER, SEAT_ROW_1_LEFT);
    private static final OccupantZoneInfo RECEIVER_ZONE = new OccupantZoneInfo(/* zoneId= */ 1,
            OCCUPANT_TYPE_FRONT_PASSENGER, SEAT_ROW_1_RIGHT);
    private static final int SENDER_USER_ID = 123;
    private static final int RECEIVER_USER_ID = 456;
    private static final String PACKAGE_NAME = "my_package_name";

    private final ClientId mSenderClient =
            new ClientId(SENDER_ZONE, SENDER_USER_ID, PACKAGE_NAME);
    private final ClientId mReceiverClient =
            new ClientId(RECEIVER_ZONE, RECEIVER_USER_ID, PACKAGE_NAME);
    private final ConnectionId mConnectionId =
            new ConnectionId(mSenderClient, mReceiverClient);

    @Test
    public void testConstructConnectionId() {
        assertThat(mConnectionId.senderClient).isEqualTo(mSenderClient);
        assertThat(mConnectionId.receiverClient).isEqualTo(mReceiverClient);
    }

    @Test
    public void testEquals() {
        ConnectionId connectionId2 = new ConnectionId(mSenderClient, mReceiverClient);

        assertThat(connectionId2).isEqualTo(mConnectionId);
        assertThat(connectionId2.hashCode()).isEqualTo(mConnectionId.hashCode());
    }

    @Test
    public void testNotEqualWithDifferentSender() {
        ClientId senderClient2 = new ClientId(SENDER_ZONE, /* userId= */ 12, PACKAGE_NAME);
        ConnectionId connectionId2 = new ConnectionId(senderClient2, mReceiverClient);

        assertThat(connectionId2).isNotEqualTo(mConnectionId);
        assertThat(connectionId2.hashCode()).isNotEqualTo(mConnectionId.hashCode());
    }

    @Test
    public void testNotEqualWithDifferentReceiver() {
        ClientId receiverClient2 =
                new ClientId(RECEIVER_ZONE, /* userId= */ 34, PACKAGE_NAME);
        ConnectionId connectionId2 = new ConnectionId(mSenderClient, receiverClient2);

        assertThat(connectionId2).isNotEqualTo(mConnectionId);
        assertThat(connectionId2.hashCode()).isNotEqualTo(mConnectionId.hashCode());
    }

    @Test
    public void testNullParameter_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new ConnectionId(mSenderClient, /* receiverClient= */ null));
        assertThrows(NullPointerException.class,
                () -> new ConnectionId(/* senderClient= */ null, mReceiverClient));
    }
}
