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
import static android.car.VehicleAreaSeat.SEAT_ROW_1_LEFT;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.car.CarOccupantZoneManager.OccupantZoneInfo;

import org.junit.Test;

public final class ReceiverEndpointIdTest {

    private static final OccupantZoneInfo OCCUPANT_ZONE =
            new OccupantZoneInfo(/* zoneId= */ 0, OCCUPANT_TYPE_DRIVER, SEAT_ROW_1_LEFT);
    private static final int USER_ID = 123;
    private static final String PACKAGE_NAME = "my_package_name";
    private static final String RECEIVER_ENDPOINT_ID = "test_receiver_id";

    @Test
    public void testConstructReceiverEndpointId() {
        ReceiverEndpointId endpointId = new ReceiverEndpointId(
                new ClientId(OCCUPANT_ZONE, USER_ID, PACKAGE_NAME), RECEIVER_ENDPOINT_ID);

        assertThat(endpointId.clientId.occupantZone).isEqualTo(OCCUPANT_ZONE);
        assertThat(endpointId.clientId.userId).isEqualTo(USER_ID);
        assertThat(endpointId.clientId.packageName).isEqualTo(PACKAGE_NAME);
        assertThat(endpointId.endpointId).isEqualTo(RECEIVER_ENDPOINT_ID);
    }

    @Test
    public void testEquals() {
        ClientId clientId = new ClientId(OCCUPANT_ZONE, USER_ID, PACKAGE_NAME);
        ReceiverEndpointId endpointId =
                new ReceiverEndpointId(clientId, RECEIVER_ENDPOINT_ID);
        ReceiverEndpointId endpointId2 =
                new ReceiverEndpointId(clientId, RECEIVER_ENDPOINT_ID);

        assertThat(endpointId).isEqualTo(endpointId2);
        assertThat(endpointId.hashCode()).isEqualTo(endpointId2.hashCode());
    }

    @Test
    public void testNotEqualWithDifferentClientId() {
        ClientId clientId = new ClientId(OCCUPANT_ZONE, USER_ID, PACKAGE_NAME);
        ReceiverEndpointId endpointId =
                new ReceiverEndpointId(clientId, RECEIVER_ENDPOINT_ID);
        ClientId clientId2 = new ClientId(OCCUPANT_ZONE, USER_ID + 1, PACKAGE_NAME);
        ReceiverEndpointId endpointId2 =
                new ReceiverEndpointId(clientId2, RECEIVER_ENDPOINT_ID);

        assertThat(endpointId).isNotEqualTo(endpointId2);
        assertThat(endpointId.hashCode()).isNotEqualTo(endpointId2.hashCode());
    }

    @Test
    public void testNotEqualWithDifferentId() {
        ClientId clientId = new ClientId(OCCUPANT_ZONE, USER_ID, PACKAGE_NAME);
        ReceiverEndpointId endpointId =
                new ReceiverEndpointId(clientId, RECEIVER_ENDPOINT_ID);
        ReceiverEndpointId endpointId2 =
                new ReceiverEndpointId(clientId, RECEIVER_ENDPOINT_ID + "a");

        assertThat(endpointId).isNotEqualTo(endpointId2);
        assertThat(endpointId.hashCode()).isNotEqualTo(endpointId2.hashCode());
    }

    @Test
    public void testNullParameter_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new ReceiverEndpointId(null, RECEIVER_ENDPOINT_ID));

        ClientId clientId = new ClientId(OCCUPANT_ZONE, USER_ID, PACKAGE_NAME);
        assertThrows(NullPointerException.class,
                () -> new ReceiverEndpointId(clientId, null));
    }
}
