/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.net.module.util.netlink;

import static com.android.net.module.util.NetworkStackConstants.ICMPV6_ND_OPTION_RDNSS;
import static com.android.testutils.MiscAsserts.assertThrows;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.net.InetAddresses;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.net.module.util.structs.RdnssOption;

import libcore.util.HexEncoding;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.Inet6Address;
import java.nio.ByteBuffer;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class StructNdOptRdnssTest {
    private static final String DNS_SERVER1 = "2001:4860:4860::64";
    private static final String DNS_SERVER2 = "2001:4860:4860::6464";

    private static final Inet6Address[] DNS_SERVER_ADDRESSES = new Inet6Address[] {
            (Inet6Address) InetAddresses.parseNumericAddress(DNS_SERVER1),
            (Inet6Address) InetAddresses.parseNumericAddress(DNS_SERVER2),
    };

    private static final String RDNSS_OPTION_BYTES =
            "1905"                                 // type=25, len=5 (40 bytes)
            + "0000"                               // reserved
            + "00000E10"                           // lifetime=3600
            + "20014860486000000000000000000064"   // 2001:4860:4860::64
            + "20014860486000000000000000006464";  // 2001:4860:4860::6464

    private static final String RDNSS_INFINITY_LIFETIME_OPTION_BYTES =
            "1905"                                 // type=25, len=3 (24 bytes)
            + "0000"                               // reserved
            + "FFFFFFFF"                           // lifetime=0xffffffff
            + "20014860486000000000000000000064"   // 2001:4860:4860::64
            + "20014860486000000000000000006464";  // 2001:4860:4860::6464

    private void assertRdnssOptMatches(final StructNdOptRdnss opt, int length, long lifetime,
            final Inet6Address[] servers) {
        assertEquals(StructNdOptRdnss.TYPE, opt.type);
        assertEquals(length, opt.length);
        assertEquals(lifetime, opt.header.lifetime);
        assertEquals(servers, opt.servers);
    }

    private ByteBuffer makeRdnssOption(byte type, byte length, long lifetime, String... servers)
            throws Exception {
        final ByteBuffer buf = ByteBuffer.allocate(8 + servers.length * 16)
                .put(type)
                .put(length)
                .putShort((short) 0) // Reserved
                .putInt((int) (lifetime & 0xFFFFFFFFL));
        for (int i = 0; i < servers.length; i++) {
            final byte[] rawBytes =
                    ((Inet6Address) InetAddresses.parseNumericAddress(servers[i])).getAddress();
            buf.put(rawBytes);
        }
        buf.flip();
        return buf;
    }

    private void assertToByteBufferMatches(StructNdOptRdnss opt, String expected) {
        String actual = HexEncoding.encodeToString(opt.toByteBuffer().array());
        assertEquals(expected, actual);
    }

    private void doRdnssOptionParsing(final String optionHexString, int length, long lifetime,
            final Inet6Address[] servers) {
        final byte[] rawBytes = HexEncoding.decode(optionHexString);
        final StructNdOptRdnss opt = StructNdOptRdnss.parse(ByteBuffer.wrap(rawBytes));
        assertRdnssOptMatches(opt, length, lifetime, servers);
        assertToByteBufferMatches(opt, optionHexString);
    }

    @Test
    public void testParsing() throws Exception {
        doRdnssOptionParsing(RDNSS_OPTION_BYTES, 5 /* length */, 3600 /* lifetime */,
                DNS_SERVER_ADDRESSES);
    }

    @Test
    public void testParsing_infinityLifetime() throws Exception {
        doRdnssOptionParsing(RDNSS_INFINITY_LIFETIME_OPTION_BYTES, 5 /* length */,
                0xffffffffL /* lifetime */, DNS_SERVER_ADDRESSES);
    }

    @Test
    public void testToByteBuffer() {
        final StructNdOptRdnss rdnss = new StructNdOptRdnss(DNS_SERVER_ADDRESSES, 3600);
        assertToByteBufferMatches(rdnss, RDNSS_OPTION_BYTES);
    }

    @Test
    public void testToByteBuffer_infinityLifetime() {
        final StructNdOptRdnss rdnss = new StructNdOptRdnss(DNS_SERVER_ADDRESSES, 0xffffffffL);
        assertToByteBufferMatches(rdnss, RDNSS_INFINITY_LIFETIME_OPTION_BYTES);
    }

    @Test
    public void testParsing_invalidType() throws Exception {
        final ByteBuffer buf = makeRdnssOption((byte) 38, (byte) 5 /* length */,
                3600 /* lifetime */, DNS_SERVER1, DNS_SERVER2);
        assertNull(StructNdOptRdnss.parse(buf));
    }

    @Test
    public void testParsing_smallOptionLength() throws Exception {
        final ByteBuffer buf = makeRdnssOption((byte) ICMPV6_ND_OPTION_RDNSS,
                (byte) 2 /* length */, 3600 /* lifetime */, DNS_SERVER1, DNS_SERVER2);
        assertNull(StructNdOptRdnss.parse(buf));
    }

    @Test
    public void testParsing_oddOptionLength() throws Exception {
        final ByteBuffer buf = makeRdnssOption((byte) ICMPV6_ND_OPTION_RDNSS,
                (byte) 6 /* length */, 3600 /* lifetime */, DNS_SERVER1, DNS_SERVER2);
        assertNull(StructNdOptRdnss.parse(buf));
    }

    @Test
    public void testParsing_truncatedByteBuffer() throws Exception {
        ByteBuffer buf = makeRdnssOption((byte) ICMPV6_ND_OPTION_RDNSS,
                (byte) 5 /* length */, 3600 /* lifetime */, DNS_SERVER1, DNS_SERVER2);
        final int len = buf.limit();
        for (int i = 0; i < buf.limit() - 1; i++) {
            buf.flip();
            buf.limit(i);
            assertNull("Option truncated to " + i + " bytes, should have returned null",
                    StructNdOptRdnss.parse(buf));
        }
        buf.flip();
        buf.limit(len);

        final StructNdOptRdnss opt = StructNdOptRdnss.parse(buf);
        assertRdnssOptMatches(opt, 5 /* length */, 3600 /* lifetime */, DNS_SERVER_ADDRESSES);
    }

    @Test
    public void testParsing_invalidByteBufferLength() throws Exception {
        final ByteBuffer buf = makeRdnssOption((byte) ICMPV6_ND_OPTION_RDNSS,
                (byte) 5 /* length */, 3600 /* lifetime */, DNS_SERVER1, DNS_SERVER2);
        buf.limit(20); // less than MIN_OPT_LEN * 8
        assertNull(StructNdOptRdnss.parse(buf));
    }

    @Test
    public void testConstructor_nullDnsServerAddressArray() {
        assertThrows(NullPointerException.class,
                () -> new StructNdOptRdnss(null /* servers */, 3600 /* lifetime */));
    }

    @Test
    public void testConstructor_emptyDnsServerAddressArray() {
        assertThrows(IllegalArgumentException.class,
                () -> new StructNdOptRdnss(new Inet6Address[0] /* empty server array */,
                                           3600 /* lifetime*/));
    }

    @Test
    public void testToString() {
        final ByteBuffer buf = RdnssOption.build(3600 /* lifetime */, DNS_SERVER1, DNS_SERVER2);
        final StructNdOptRdnss opt = StructNdOptRdnss.parse(buf);
        final String expected = "NdOptRdnss(type: 25, length: 5, reserved: 0, lifetime: 3600,"
                + "servers:[2001:4860:4860::64,2001:4860:4860::6464])";
        assertRdnssOptMatches(opt, 5 /* length */, 3600 /* lifetime */, DNS_SERVER_ADDRESSES);
        assertEquals(expected, opt.toString());
    }
}
