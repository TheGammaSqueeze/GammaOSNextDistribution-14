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

import static com.android.net.module.util.netlink.xfrm.XfrmNetlinkMessage.IPPROTO_ESP;
import static com.android.net.module.util.netlink.xfrm.XfrmNetlinkMessage.NETLINK_XFRM;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.net.InetAddresses;
import android.system.OsConstants;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.net.module.util.HexDump;
import com.android.net.module.util.netlink.NetlinkMessage;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class XfrmNetlinkGetSaMessageTest {
    private static final String EXPECTED_HEX_STRING =
            "28000000120001000000000000000000"
                    + "C0000201000000000000000000000000"
                    + "7768440002003200";
    private static final byte[] EXPECTED_HEX = HexDump.hexStringToByteArray(EXPECTED_HEX_STRING);
    private static final InetAddress DEST_ADDRESS = InetAddresses.parseNumericAddress("192.0.2.1");
    private static final long SPI = 0x77684400;
    private static final int FAMILY = OsConstants.AF_INET;
    private static final short PROTO = IPPROTO_ESP;

    @Test
    public void testEncode() throws Exception {
        final byte[] result =
                XfrmNetlinkGetSaMessage.newXfrmNetlinkGetSaMessage(DEST_ADDRESS, SPI, PROTO);
        assertArrayEquals(EXPECTED_HEX, result);
    }

    @Test
    public void testDecode() throws Exception {
        final ByteBuffer buffer = ByteBuffer.wrap(EXPECTED_HEX);
        buffer.order(ByteOrder.nativeOrder());
        final XfrmNetlinkGetSaMessage message =
                (XfrmNetlinkGetSaMessage) NetlinkMessage.parse(buffer, NETLINK_XFRM);
        final StructXfrmUsersaId struct = message.getStructXfrmUsersaId();

        assertEquals(DEST_ADDRESS, struct.getDestAddress());
        assertEquals(SPI, struct.spi);
        assertEquals(FAMILY, struct.family);
        assertEquals(PROTO, struct.proto);
        assertEquals(0, buffer.remaining());
    }
}
