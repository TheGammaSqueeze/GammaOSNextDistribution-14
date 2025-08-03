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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class ConnectionRecordTest {

    private static final String PACKAGE_NAME = "my_package_name";
    private static final int SENDER_ZONE_ID = 123;
    private static final int RECEIVER_ZONE_ID = 456;

    private ConnectionRecord mConnectionRecord;

    @Before
    public void setUp() {
        mConnectionRecord = new ConnectionRecord(PACKAGE_NAME, SENDER_ZONE_ID, RECEIVER_ZONE_ID);
    }

    @Test
    public void testConstructConnectionRecord() {
        assertThat(mConnectionRecord.packageName).isEqualTo(PACKAGE_NAME);
        assertThat(mConnectionRecord.senderZoneId).isEqualTo(SENDER_ZONE_ID);
        assertThat(mConnectionRecord.receiverZoneId).isEqualTo(RECEIVER_ZONE_ID);
    }

    @Test
    public void testEquals() {
        ConnectionRecord connectionRecord2 =
                new ConnectionRecord(PACKAGE_NAME, SENDER_ZONE_ID, RECEIVER_ZONE_ID);

        assertThat(connectionRecord2).isEqualTo(mConnectionRecord);
        assertThat(connectionRecord2.hashCode()).isEqualTo(mConnectionRecord.hashCode());
    }

    @Test
    public void testNotEqualWithDifferentPackageName() {
        ConnectionRecord connectionRecord2 =
                new ConnectionRecord(PACKAGE_NAME + "a", SENDER_ZONE_ID, RECEIVER_ZONE_ID);

        assertThat(connectionRecord2).isNotEqualTo(mConnectionRecord);
        assertThat(connectionRecord2.hashCode()).isNotEqualTo(mConnectionRecord.hashCode());
    }

    @Test
    public void testNotEqualWithDifferentSenderZone() {
        ConnectionRecord connectionRecord2 =
                new ConnectionRecord(PACKAGE_NAME, SENDER_ZONE_ID + 1, RECEIVER_ZONE_ID);

        assertThat(connectionRecord2).isNotEqualTo(mConnectionRecord);
        assertThat(connectionRecord2.hashCode()).isNotEqualTo(mConnectionRecord.hashCode());
    }

    @Test
    public void testNotEqualWithDifferentReceiverZone() {
        ConnectionRecord connectionRecord2 =
                new ConnectionRecord(PACKAGE_NAME, SENDER_ZONE_ID, RECEIVER_ZONE_ID + 1);

        assertThat(connectionRecord2).isNotEqualTo(mConnectionRecord);
        assertThat(connectionRecord2.hashCode()).isNotEqualTo(mConnectionRecord.hashCode());
    }

    @Test
    public void testNullParameter_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new ConnectionRecord(/* packageName= */ null, SENDER_ZONE_ID,
                        RECEIVER_ZONE_ID));
    }
}
