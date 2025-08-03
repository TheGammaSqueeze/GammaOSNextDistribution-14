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

package com.android.net.module.util.netlink.xfrm;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.net.module.util.HexDump;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class StructXfrmReplayStateEsnTest {
    private static final String EXPECTED_HEX_STRING =
            "80000000000000000000000000000000"
                    + "00000000001000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "0000000000000000";

    private static final byte[] EXPECTED_HEX = HexDump.hexStringToByteArray(EXPECTED_HEX_STRING);

    private static final long BMP_LEN = 128;
    private static final long REPLAY_WINDOW = 4096;
    private static final byte[] BITMAP = new byte[512];

    @Test
    public void testEncode() throws Exception {
        final StructXfrmReplayStateEsn struct =
                new StructXfrmReplayStateEsn(BMP_LEN, 0L, 0L, 0L, 0L, REPLAY_WINDOW, BITMAP);

        final ByteBuffer buffer = ByteBuffer.allocate(struct.getStructSize());
        buffer.order(ByteOrder.nativeOrder());
        struct.writeToByteBuffer(buffer);

        assertArrayEquals(EXPECTED_HEX, buffer.array());
    }

    @Test
    public void testDecode() throws Exception {
        final ByteBuffer buffer = ByteBuffer.wrap(EXPECTED_HEX);
        buffer.order(ByteOrder.nativeOrder());

        final StructXfrmReplayStateEsn struct = StructXfrmReplayStateEsn.parse(buffer);

        assertEquals(BMP_LEN, struct.getBmpLen());
        assertEquals(REPLAY_WINDOW, struct.getReplayWindow());
        assertArrayEquals(BITMAP, struct.getBitmap());
        assertEquals(0L, struct.getRxSequenceNumber());
        assertEquals(0L, struct.getTxSequenceNumber());
    }

    @Test
    public void testGetSequenceNumber() throws Exception {
        final long low = 0x00ab112233L;
        final long hi = 0x01L;

        assertEquals(0x01ab112233L, StructXfrmReplayStateEsn.getSequenceNumber(hi, low));
        assertEquals(0xab11223300000001L, StructXfrmReplayStateEsn.getSequenceNumber(low, hi));
    }

    // TODO: Add test cases that the test bitmap is not all zeros
}
