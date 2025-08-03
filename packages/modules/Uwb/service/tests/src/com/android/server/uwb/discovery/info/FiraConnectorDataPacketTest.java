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

package com.android.server.uwb.discovery.info;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

/** Unit test for {@link FiraConnectorDataPacket} */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class FiraConnectorDataPacketTest {
    private static final byte[] TEST_BYTES =
            new byte[] {
                /* Last chining bit */
                (byte)
                        (0x80
                                |
                                /* secid */
                                0x71),
                /* PAYLOAD */
                (byte) 0xF4,
                0x00,
                0x40
            };

    private static final boolean LAST_CHAINING_PACKET = true;
    private static final boolean NOT_LAST_CHAINING_PACKET = false;
    private static final int SECID = 113;
    private static final byte[] PAYLOAD = new byte[] {(byte) 0xF4, 0x00, 0x40};

    @Test
    public void fromBytes_emptyData() {
        assertThat(FiraConnectorDataPacket.fromBytes(new byte[] {})).isNull();
    }

    @Test
    public void fromBytes_succeedLastPacket() {
        FiraConnectorDataPacket packet = FiraConnectorDataPacket.fromBytes(TEST_BYTES);
        assertThat(packet).isNotNull();

        assertThat(packet.lastChainingPacket).isEqualTo(LAST_CHAINING_PACKET);
        assertThat(packet.secid).isEqualTo(SECID);
        assertThat(packet.payload).isEqualTo(PAYLOAD);
    }

    @Test
    public void fromBytes_succeedNotLastPacket() {
        byte[] bytes = Arrays.copyOf(TEST_BYTES, TEST_BYTES.length);
        bytes[0] = (byte) (bytes[0] & 0x7f);
        FiraConnectorDataPacket packet = FiraConnectorDataPacket.fromBytes(bytes);
        assertThat(packet).isNotNull();

        assertThat(packet.lastChainingPacket).isEqualTo(NOT_LAST_CHAINING_PACKET);
        assertThat(packet.secid).isEqualTo(SECID);
        assertThat(packet.payload).isEqualTo(PAYLOAD);
    }

    @Test
    public void toBytes_succeed() {
        FiraConnectorDataPacket packet =
                new FiraConnectorDataPacket(LAST_CHAINING_PACKET, SECID, PAYLOAD);
        assertThat(packet).isNotNull();

        byte[] result = packet.toBytes();
        assertThat(result.length).isEqualTo(TEST_BYTES.length);
        assertThat(result).isEqualTo(TEST_BYTES);
        assertThat(packet.toString())
                .isEqualTo(FiraConnectorDataPacket.fromBytes(TEST_BYTES).toString());
    }
}
