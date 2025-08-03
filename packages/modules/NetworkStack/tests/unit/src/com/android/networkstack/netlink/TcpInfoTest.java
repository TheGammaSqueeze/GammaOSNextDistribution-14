/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.networkstack.netlink;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import libcore.util.HexEncoding;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class TcpInfoTest {
    private static final int TCP_INFO_LENGTH_V1 = 192;
    private static final int SHORT_TEST_TCP_INFO = 8;
    private static final String TCP_ESTABLISHED = "TCP_ESTABLISHED";
    private static final String TCP_FIN_WAIT1 = "TCP_FIN_WAIT1";
    private static final String TCP_SYN_SENT = "TCP_SYN_SENT";
    private static final String UNKNOWN_20 = "UNKNOWN:20";
    // Refer to rfc793 for the value definition.
    private static final String TCP_INFO_HEX =
            "01" +                // state = TCP_ESTABLISHED
            "00" +                // ca_state = TCP_CA_OPEN
            "00" +                // retransmits = 0
            "00" +                // probes = 0
            "00" +                // backoff = 0
            "07" +                // option = TCPI_OPT_WSCALE|TCPI_OPT_SACK|TCPI_OPT_TIMESTAMPS
            "88" +                // wscale = 8
            "00" +                // delivery_rate_app_limited = 0
            "4A911B00" +          // rto = 1806666
            "00000000" +          // ato = 0
            "2E050000" +          // sndMss = 1326
            "18020000" +          // rcvMss = 536
            "00000000" +          // unsacked = 0
            "00000000" +          // acked = 0
            "00000000" +          // lost = 0
            "00000000" +          // retrans = 0
            "00000000" +          // fackets = 0
            "BB000000" +          // lastDataSent = 187
            "00000000" +          // lastAckSent = 0
            "BB000000" +          // lastDataRecv = 187
            "BB00000000" +          // lastDataAckRecv = 187
            "DC0500" +          // pmtu = 1500
            "30560100" +          // rcvSsthresh = 87600
            "3E2C0900" +          // rttt = 601150
            "1F960400" +          // rttvar = 300575
            "78050000" +          // sndSsthresh = 1400
            "0A000000" +          // sndCwnd = 10
            "A8050000" +          // advmss = 1448
            "02000000" +          // reordering = 3
            "00000000" +          // rcvrtt = 0
            "30560100" +          // rcvspace = 87600
            "05000000" +          // totalRetrans = 5
            "53AC000000000000" +  // pacingRate = 44115
            "FFFFFFFFFFFFFFFF" +  // maxPacingRate = 18446744073709551615
            "0100000000000001" +  // bytesAcked = 1
            "0000000000000000" +  // bytesReceived = 0
            "02000000" +          // SegsOut = 2
            "01000000" +          // SegsIn = 1
            "00000000" +          // NotSentBytes = 0
            "3E2C0900" +          // minRtt = 601150
            "00000000" +          // DataSegsIn = 0
            "00000000" +          // DataSegsOut = 0
            "0000000000000000" +  // deliverRate = 0
            "0000000000000000" +  // busyTime = 0
            "0000000000000000" +  // RwndLimited = 0
            "0000000000000000";   // sndBufLimited = 0
    private static final byte[] TCP_INFO_BYTES =
            HexEncoding.decode(TCP_INFO_HEX.toCharArray(), false);
    private static final TcpInfo TEST_TCPINFO =
            new TcpInfo(2 /* segsOut */, 1 /* segsIn */, 5 /* totalRetrans */);

    private static final String EXPANDED_TCP_INFO_HEX = TCP_INFO_HEX
            + "00000000"         // tcpi_delivered
            + "00000000";        // tcpi_delivered_ce
    private static final byte[] EXPANDED_TCP_INFO_BYTES =
            HexEncoding.decode(EXPANDED_TCP_INFO_HEX.toCharArray(), false);
    private static final int EXPANDED_TCP_INFO_LENGTH =
            EXPANDED_TCP_INFO_BYTES.length - TCP_INFO_BYTES.length;
    @Test
    public void testParseTcpInfo() {
        final ByteBuffer buffer = ByteBuffer.wrap(TCP_INFO_BYTES);
        // Android is always little-endian. Refer to https://developer.android.com/ndk/guides/abis.
        buffer.order(ByteOrder.nativeOrder());
        // Length is less than required
        assertNull(TcpInfo.parse(buffer, SHORT_TEST_TCP_INFO));
        assertEquals(TEST_TCPINFO, TcpInfo.parse(buffer, TCP_INFO_LENGTH_V1));

        // Make a data that TcpInfo is not started from the beginning of the buffer.
        final ByteBuffer buffer2 = ByteBuffer.wrap(TCP_INFO_BYTES);
        buffer2.order(ByteOrder.nativeOrder());
        // Move to certain position.
        buffer2.position(2);
        // Parsing is started in an incorrect position. This results in a failed parsing.
        assertNotEquals(TEST_TCPINFO, TcpInfo.parse(buffer2, TCP_INFO_LENGTH_V1));

        // Make a TcpInfo with extra tcp info fields. Parsing is only performed with
        // TCP_INFO_LENGTH_V1 length. Result is the same as parsing with TCP_INFO_BYTES.
        final ByteBuffer bufferExtraInfo =
                ByteBuffer.allocate(EXPANDED_TCP_INFO_BYTES.length + TCP_INFO_BYTES.length);
        bufferExtraInfo.order(ByteOrder.nativeOrder());
        bufferExtraInfo.put(TCP_INFO_BYTES);
        bufferExtraInfo.put(EXPANDED_TCP_INFO_BYTES);
        bufferExtraInfo.position(0);
        assertEquals(TEST_TCPINFO, TcpInfo.parse(bufferExtraInfo, TCP_INFO_LENGTH_V1));
    }

    @Test
    public void testFieldOffset() {
        assertEquals(TcpInfo.RETRANSMITS_OFFSET, 2);
        assertEquals(TcpInfo.LOST_OFFSET, 32);
        assertEquals(TcpInfo.TOTAL_RETRANS_OFFSET, 100);
        assertEquals(TcpInfo.SEGS_OUT_OFFSET, 136);
        assertEquals(TcpInfo.SEGS_IN_OFFSET, 140);
    }

    @Test
    public void testParseTcpInfoExpanded() {
        final ByteBuffer buffer = ByteBuffer.wrap(EXPANDED_TCP_INFO_BYTES);
        // Android is always little-endian. Refer to https://developer.android.com/ndk/guides/abis.
        buffer.order(ByteOrder.nativeOrder());
        final TcpInfo parsedInfo =
                TcpInfo.parse(buffer, TCP_INFO_LENGTH_V1 + EXPANDED_TCP_INFO_LENGTH);

        assertEquals(TEST_TCPINFO, parsedInfo);
        assertEquals(buffer.limit(), buffer.position());

        // reset the index.
        buffer.position(0);
        final TcpInfo parsedInfoShorterLen = TcpInfo.parse(buffer, TCP_INFO_LENGTH_V1);
        assertEquals(TEST_TCPINFO, parsedInfoShorterLen);
        assertEquals(TCP_INFO_LENGTH_V1, buffer.position());
    }

    @Test
    public void testTcpStateName() {
        assertEquals(TCP_FIN_WAIT1, TcpInfo.getTcpStateName(4));
        assertEquals(TCP_ESTABLISHED, TcpInfo.getTcpStateName(1));
        assertEquals(TCP_SYN_SENT, TcpInfo.getTcpStateName(2));
        assertEquals(UNKNOWN_20, TcpInfo.getTcpStateName(20));
    }

    private static final String MALFORMED_TCP_INFO_HEX =
            "01" +                // state = TCP_ESTABLISHED
            "00" +                // ca_state = TCP_CA_OPEN
            "00" +                // retransmits = 0
            "00" +                // probes = 0
            "00" +                // backoff = 0
            "07" +                // option = TCPI_OPT_WSCALE|TCPI_OPT_SACK|TCPI_OPT_TIMESTAMPS
            "88" +                // wscale = 8
            "00" +                // delivery_rate_app_limited = 0
            "001B";               // Incomplete bytes, expect to be an int.
    private static final byte[] MALFORMED_TCP_INFO_BYTES =
            HexEncoding.decode(MALFORMED_TCP_INFO_HEX.toCharArray(), false);
    @Test
    public void testMalformedTcpInfo() {
        final ByteBuffer buffer = ByteBuffer.wrap(MALFORMED_TCP_INFO_BYTES);
        assertNull(TcpInfo.parse(buffer, SHORT_TEST_TCP_INFO));
        assertNull(TcpInfo.parse(buffer, TCP_INFO_LENGTH_V1));
    }

    // Make a TcpInfo contains only first 8 bytes.
    private Map<TcpInfo.Field, Number> makeShortTestTcpInfoHash() {
        final Map<TcpInfo.Field, Number> info = new LinkedHashMap<>();
        info.put(TcpInfo.Field.STATE, (byte) 0x01);
        info.put(TcpInfo.Field.CASTATE, (byte) 0x00);
        info.put(TcpInfo.Field.RETRANSMITS, (byte) 0x00);
        info.put(TcpInfo.Field.PROBES, (byte) 0x00);
        info.put(TcpInfo.Field.BACKOFF, (byte) 0x00);
        info.put(TcpInfo.Field.OPTIONS, (byte) 0x07);
        info.put(TcpInfo.Field.WSCALE, (byte) 0x88);
        info.put(TcpInfo.Field.DELIVERY_RATE_APP_LIMITED, (byte) 0x00);

        return info;
    }

    private Map<TcpInfo.Field, Number> makeTestTcpInfoHash() {
        final Map<TcpInfo.Field, Number> info = makeShortTestTcpInfoHash();
        info.put(TcpInfo.Field.RTO, 1806666);
        info.put(TcpInfo.Field.ATO, 0);
        info.put(TcpInfo.Field.SND_MSS, 1326);
        info.put(TcpInfo.Field.RCV_MSS, 536);
        info.put(TcpInfo.Field.UNACKED, 0);
        info.put(TcpInfo.Field.SACKED, 0);
        info.put(TcpInfo.Field.LOST, 0);
        info.put(TcpInfo.Field.RETRANS, 0);
        info.put(TcpInfo.Field.FACKETS, 0);
        info.put(TcpInfo.Field.LAST_DATA_SENT, 187);
        info.put(TcpInfo.Field.LAST_ACK_SENT, 0);
        info.put(TcpInfo.Field.LAST_DATA_RECV, 187);
        info.put(TcpInfo.Field.LAST_ACK_RECV, 187);
        info.put(TcpInfo.Field.PMTU, 1500);
        info.put(TcpInfo.Field.RCV_SSTHRESH, 87600);
        info.put(TcpInfo.Field.RTT, 601150);
        info.put(TcpInfo.Field.RTTVAR, 300575);
        info.put(TcpInfo.Field.SND_SSTHRESH, 1400);
        info.put(TcpInfo.Field.SND_CWND, 10);
        info.put(TcpInfo.Field.ADVMSS, 1448);
        info.put(TcpInfo.Field.REORDERING, 3);
        info.put(TcpInfo.Field.RCV_RTT, 0);
        info.put(TcpInfo.Field.RCV_SPACE, 87600);
        info.put(TcpInfo.Field.TOTAL_RETRANS, 0);
        info.put(TcpInfo.Field.PACING_RATE, 44115L);
        info.put(TcpInfo.Field.MAX_PACING_RATE, -1L);
        info.put(TcpInfo.Field.BYTES_ACKED, 1L);
        info.put(TcpInfo.Field.BYTES_RECEIVED, 0L);
        info.put(TcpInfo.Field.SEGS_OUT, 2);
        info.put(TcpInfo.Field.SEGS_IN, 1);
        info.put(TcpInfo.Field.NOTSENT_BYTES, 0);
        info.put(TcpInfo.Field.MIN_RTT, 601150);
        info.put(TcpInfo.Field.DATA_SEGS_IN, 0);
        info.put(TcpInfo.Field.DATA_SEGS_OUT, 0);
        info.put(TcpInfo.Field.DELIVERY_RATE, 0L);
        info.put(TcpInfo.Field.BUSY_TIME, 0L);
        info.put(TcpInfo.Field.RWND_LIMITED, 0L);
        info.put(TcpInfo.Field.SNDBUF_LIMITED, 0L);

        return info;
    }

    @Test
    public void testHashCode() {
        final TcpInfo info = new TcpInfo(2, 1, 5);
        final TcpInfo info2 = new TcpInfo(2, 1, 5);

        assertEquals(info, info2);
        assertEquals(info.hashCode(), info2.hashCode());
    }

    @Test
    public void testDecodeWscale() {
        assertEquals("0:0", TcpInfo.decodeWscale((byte) 0));
        assertEquals("15:15", TcpInfo.decodeWscale((byte) 255));
        assertEquals("15:0", TcpInfo.decodeWscale((byte) 240));
    }
}
