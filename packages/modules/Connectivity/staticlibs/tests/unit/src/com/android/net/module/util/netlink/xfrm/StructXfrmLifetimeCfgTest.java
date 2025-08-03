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

import static com.android.net.module.util.netlink.xfrm.XfrmNetlinkMessage.XFRM_INF;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.net.module.util.HexDump;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class StructXfrmLifetimeCfgTest {
    private static final String EXPECTED_HEX_STRING =
            "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
                    + "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000";
    private static final byte[] EXPECTED_HEX = HexDump.hexStringToByteArray(EXPECTED_HEX_STRING);

    @Test
    public void testEncode() throws Exception {
        final StructXfrmLifetimeCfg struct = new StructXfrmLifetimeCfg();

        final ByteBuffer buffer = ByteBuffer.allocate(EXPECTED_HEX.length);
        buffer.order(ByteOrder.nativeOrder());
        struct.writeToByteBuffer(buffer);

        assertArrayEquals(EXPECTED_HEX, buffer.array());
    }

    @Test
    public void testDecode() throws Exception {
        final ByteBuffer buffer = ByteBuffer.wrap(EXPECTED_HEX);
        buffer.order(ByteOrder.nativeOrder());
        final StructXfrmLifetimeCfg struct =
                StructXfrmLifetimeCfg.parse(StructXfrmLifetimeCfg.class, buffer);

        assertEquals(XFRM_INF, struct.softByteLimit);
        assertEquals(XFRM_INF, struct.hardByteLimit);
        assertEquals(XFRM_INF, struct.softPacketLimit);
        assertEquals(XFRM_INF, struct.hardPacketLimit);
        assertEquals(BigInteger.ZERO, struct.softAddExpiresSeconds);
        assertEquals(BigInteger.ZERO, struct.hardAddExpiresSeconds);
        assertEquals(BigInteger.ZERO, struct.softUseExpiresSeconds);
        assertEquals(BigInteger.ZERO, struct.hardUseExpiresSeconds);
    }
}
