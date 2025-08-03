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

import static com.android.net.module.util.netlink.xfrm.XfrmNetlinkMessage.NETLINK_XFRM;
import static com.android.net.module.util.netlink.xfrm.XfrmNetlinkMessage.XFRM_MODE_TRANSPORT;

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
public class XfrmNetlinkNewSaMessageTest {
    private static final String EXPECTED_HEX_STRING =
            "2004000010000000000000003FE1D4B6"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000A00000000000000"
                    + "000000000000000020010DB800000000"
                    + "0000000000000111AABBCCDD32000000"
                    + "20010DB8000000000000000000000222"
                    + "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
                    + "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "FD464C65000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "024000000A0000000000000000000000"
                    + "5C000100686D61632873686131290000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000A000000055F01AC07E15E437"
                    + "115DDE0AEDD18A822BA9F81E60001400"
                    + "686D6163287368613129000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "A00000006000000055F01AC07E15E437"
                    + "115DDE0AEDD18A822BA9F81E58000200"
                    + "63626328616573290000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "800000006AED4975ADF006D65C76F639"
                    + "23A6265B1C0217008000000000000000"
                    + "00000000000000000000000000100000"
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
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000";

    private static final byte[] EXPECTED_HEX = HexDump.hexStringToByteArray(EXPECTED_HEX_STRING);

    private static final InetAddress DEST_ADDRESS =
            InetAddresses.parseNumericAddress("2001:db8::111");
    private static final InetAddress SOURCE_ADDRESS =
            InetAddresses.parseNumericAddress("2001:db8::222");
    private static final int FAMILY = OsConstants.AF_INET6;
    private static final long SPI = 0xaabbccddL;
    private static final long SEQ = 0L;
    private static final long REQ_ID = 16386L;
    private static final short MODE = XFRM_MODE_TRANSPORT;
    private static final short REPLAY_WINDOW_LEGACY = 0;
    private static final short FLAGS = 0;
    private static final byte[] BITMAP = new byte[512];

    @Test
    public void testDecode() throws Exception {
        final ByteBuffer buffer = ByteBuffer.wrap(EXPECTED_HEX);
        buffer.order(ByteOrder.nativeOrder());
        final XfrmNetlinkNewSaMessage message =
                (XfrmNetlinkNewSaMessage) NetlinkMessage.parse(buffer, NETLINK_XFRM);
        final StructXfrmUsersaInfo xfrmUsersaInfo = message.getXfrmUsersaInfo();

        assertEquals(DEST_ADDRESS, xfrmUsersaInfo.getDestAddress());
        assertEquals(SOURCE_ADDRESS, xfrmUsersaInfo.getSrcAddress());
        assertEquals(SPI, xfrmUsersaInfo.getSpi());
        assertEquals(SEQ, xfrmUsersaInfo.seq);
        assertEquals(REQ_ID, xfrmUsersaInfo.reqId);
        assertEquals(FAMILY, xfrmUsersaInfo.family);
        assertEquals(MODE, xfrmUsersaInfo.mode);
        assertEquals(REPLAY_WINDOW_LEGACY, xfrmUsersaInfo.replayWindowLegacy);
        assertEquals(FLAGS, xfrmUsersaInfo.flags);

        assertArrayEquals(BITMAP, message.getBitmap());
        assertEquals(0L, message.getRxSequenceNumber());
        assertEquals(0L, message.getTxSequenceNumber());
    }
}
