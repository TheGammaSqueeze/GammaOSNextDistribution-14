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

package com.android.net.module.util;

import static android.net.DnsResolver.CLASS_IN;
import static android.net.DnsResolver.TYPE_A;
import static android.net.DnsResolver.TYPE_AAAA;

import static com.android.net.module.util.DnsPacket.TYPE_SVCB;
import static com.android.testutils.MiscAsserts.assertThrows;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.annotation.NonNull;
import android.net.InetAddresses;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(JUnit4.class)
public class DnsSvcbPacketTest {
    private static final short TEST_TRANSACTION_ID = 0x4321;
    private static final byte[] TEST_DNS_RESPONSE_HEADER_FLAG =  new byte[] { (byte) 0x81, 0x00 };

    // A common DNS SVCB Question section with Name = "_dns.resolver.arpa".
    private static final byte[] TEST_DNS_SVCB_QUESTION_SECTION = new byte[] {
            0x04, '_', 'd', 'n', 's', 0x08, 'r', 'e', 's', 'o', 'l', 'v', 'e', 'r',
            0x04, 'a', 'r', 'p', 'a', 0x00, 0x00, 0x40, 0x00, 0x01,
    };

    // mandatory=ipv4hint,alpn,key333
    private static final byte[] TEST_SVC_PARAM_MANDATORY = new byte[] {
            0x00, 0x00, 0x00, 0x06, 0x00, 0x04, 0x00, 0x01, 0x01, 0x4d,
    };

    // alpn=doq
    private static final byte[] TEST_SVC_PARAM_ALPN_DOQ = new byte[] {
            0x00, 0x01, 0x00, 0x04, 0x03, 'd', 'o', 'q'
    };

    // alpn=h2,http/1.1
    private static final byte[] TEST_SVC_PARAM_ALPN_HTTPS = new byte[] {
            0x00, 0x01, 0x00, 0x0c, 0x02, 'h', '2',
            0x08, 'h', 't', 't', 'p', '/', '1', '.', '1',
    };

    // no-default-alpn
    private static final byte[] TEST_SVC_PARAM_NO_DEFAULT_ALPN = new byte[] {
            0x00, 0x02, 0x00, 0x00,
    };

    // port=5353
    private static final byte[] TEST_SVC_PARAM_PORT = new byte[] {
            0x00, 0x03, 0x00, 0x02, 0x14, (byte) 0xe9,
    };

    // ipv4hint=1.2.3.4,6.7.8.9
    private static final byte[] TEST_SVC_PARAM_IPV4HINT_1 = new byte[] {
            0x00, 0x04, 0x00, 0x08, 0x01, 0x02, 0x03, 0x04, 0x06, 0x07, 0x08, 0x09,
    };

    // ipv4hint=4.3.2.1
    private static final byte[] TEST_SVC_PARAM_IPV4HINT_2 = new byte[] {
            0x00, 0x04, 0x00, 0x04, 0x04, 0x03, 0x02, 0x01,
    };

    // ech=aBcDe
    private static final byte[] TEST_SVC_PARAM_ECH = new byte[] {
            0x00, 0x05, 0x00, 0x05, 'a', 'B', 'c', 'D', 'e',
    };

    // ipv6hint=2001:db8::1
    private static final byte[] TEST_SVC_PARAM_IPV6HINT = new byte[] {
            0x00, 0x06, 0x00, 0x10, 0x20, 0x01, 0x0d, (byte) 0xb8, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01,
    };

    // dohpath=/some-path{?dns}
    private static final byte[] TEST_SVC_PARAM_DOHPATH = new byte[] {
            0x00, 0x07, 0x00, 0x10,
            '/', 's', 'o', 'm', 'e', '-', 'p', 'a', 't', 'h', '{', '?', 'd', 'n', 's', '}',
    };

    // key12345=1A2B0C
    private static final byte[] TEST_SVC_PARAM_GENERIC_WITH_VALUE = new byte[] {
            0x30, 0x39, 0x00, 0x03, 0x1a, 0x2b, 0x0c,
    };

    // key12346
    private static final byte[] TEST_SVC_PARAM_GENERIC_WITHOUT_VALUE = new byte[] {
            0x30, 0x3a, 0x00, 0x00,
    };

    private static byte[] makeDnsResponseHeaderAsByteArray(int qdcount, int ancount, int nscount,
                int arcount) {
        final ByteBuffer buffer = ByteBuffer.wrap(new byte[12]);
        buffer.putShort(TEST_TRANSACTION_ID); /* Transaction ID */
        buffer.put(TEST_DNS_RESPONSE_HEADER_FLAG); /* Flags */
        buffer.putShort((short) qdcount);
        buffer.putShort((short) ancount);
        buffer.putShort((short) nscount);
        buffer.putShort((short) arcount);
        return buffer.array();
    }

    private static DnsSvcbRecord makeDnsSvcbRecordFromByteArray(@NonNull byte[] data)
                throws IOException {
        return new DnsSvcbRecord(DnsPacket.ANSECTION, ByteBuffer.wrap(data));
    }

    private static DnsSvcbRecord makeDnsSvcbRecordWithSingleSvcParam(@NonNull byte[] svcParam)
            throws IOException {
        return makeDnsSvcbRecordFromByteArray(new TestDnsRecordByteArrayBuilder()
                .setRRType(TYPE_SVCB)
                .setTargetName("test.com")
                .addRdata(svcParam)
                .build());
    }

    // Converts a Short to a byte array in big endian.
    private static byte[] shortToByteArray(short value) {
        return new byte[] { (byte) (value >> 8), (byte) value };
    }

    private static byte[] getRemainingByteArray(@NonNull ByteBuffer buffer) {
        final byte[] out = new byte[buffer.remaining()];
        buffer.get(out);
        return out;
    }

    // A utility to make a DNS record as byte array.
    private static class TestDnsRecordByteArrayBuilder {
        private static final byte[] NAME_COMPRESSION_POINTER = new byte[] { (byte) 0xc0, 0x0c };

        private final String mRRName = "dns.com";
        private short mRRType = 0;
        private final short mRRClass = CLASS_IN;
        private final int mRRTtl = 10;
        private int mRdataLen = 0;
        private final ArrayList<byte[]> mRdata = new ArrayList<>();
        private String mTargetName = null;
        private short mSvcPriority = 1;
        private boolean mNameCompression = false;

        TestDnsRecordByteArrayBuilder setNameCompression(boolean value) {
            mNameCompression = value;
            return this;
        }

        TestDnsRecordByteArrayBuilder setRRType(int value) {
            mRRType = (short) value;
            return this;
        }

        TestDnsRecordByteArrayBuilder setTargetName(@NonNull String value) throws IOException {
            mTargetName = value;
            return this;
        }

        TestDnsRecordByteArrayBuilder setSvcPriority(int value) {
            mSvcPriority = (short) value;
            return this;
        }

        TestDnsRecordByteArrayBuilder addRdata(@NonNull byte[] value) {
            mRdata.add(value);
            mRdataLen += value.length;
            return this;
        }

        byte[] build() throws IOException {
            final ByteArrayOutputStream os = new ByteArrayOutputStream();
            final byte[] name = mNameCompression ? NAME_COMPRESSION_POINTER
                    : DnsPacketUtils.DnsRecordParser.domainNameToLabels(mRRName);
            os.write(name);
            os.write(shortToByteArray(mRRType));
            os.write(shortToByteArray(mRRClass));
            os.write(HexDump.toByteArray(mRRTtl));
            if (mTargetName == null) {
                os.write(shortToByteArray((short) mRdataLen));
            } else {
                final byte[] targetNameLabels =
                        DnsPacketUtils.DnsRecordParser.domainNameToLabels(mTargetName);
                mRdataLen += (Short.BYTES + targetNameLabels.length);
                os.write(shortToByteArray((short) mRdataLen));
                os.write(shortToByteArray(mSvcPriority));
                os.write(targetNameLabels);
            }
            for (byte[] data : mRdata) {
                os.write(data);
            }
            return os.toByteArray();
        }
    }

    @Test
    public void testSliceAndAdvance() throws Exception {
        final ByteBuffer buffer = ByteBuffer.wrap(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9});
        final ByteBuffer slice1 = DnsSvcbRecord.sliceAndAdvance(buffer, 3);
        final ByteBuffer slice2 = DnsSvcbRecord.sliceAndAdvance(buffer, 4);
        assertEquals(0, slice1.position());
        assertEquals(3, slice1.capacity());
        assertEquals(3, slice1.remaining());
        assertTrue(slice1.isReadOnly());
        assertArrayEquals(new byte[] {1, 2, 3}, getRemainingByteArray(slice1));
        assertEquals(0, slice2.position());
        assertEquals(4, slice2.capacity());
        assertEquals(4, slice2.remaining());
        assertTrue(slice2.isReadOnly());
        assertArrayEquals(new byte[] {4, 5, 6, 7}, getRemainingByteArray(slice2));

        // Nothing is read if out-of-bound access happens.
        assertThrows(BufferUnderflowException.class,
                () -> DnsSvcbRecord.sliceAndAdvance(buffer, 5));
        assertEquals(7, buffer.position());
        assertEquals(9, buffer.capacity());
        assertEquals(2, buffer.remaining());
        assertArrayEquals(new byte[] {8, 9}, getRemainingByteArray(buffer));
    }

    @Test
    public void testDnsSvcbRecord_svcParamMandatory() throws Exception {
        final DnsSvcbRecord record = makeDnsSvcbRecordWithSingleSvcParam(TEST_SVC_PARAM_MANDATORY);
        // Check the content returned from toString() for now because the getter function for
        // this SvcParam hasn't been implemented.
        // TODO(b/240259333): Consider adding DnsSvcbRecord.isMandatory(String alpn) when needed.
        assertTrue(record.toString().contains("ipv4hint"));
        assertTrue(record.toString().contains("alpn"));
        assertTrue(record.toString().contains("key333"));
    }

    @Test
    public void testDnsSvcbRecord_svcParamAlpn() throws Exception {
        final DnsSvcbRecord record = makeDnsSvcbRecordWithSingleSvcParam(TEST_SVC_PARAM_ALPN_HTTPS);
        assertEquals(Arrays.asList("h2", "http/1.1"), record.getAlpns());
    }

    @Test
    public void testDnsSvcbRecord_svcParamNoDefaultAlpn() throws Exception {
        final DnsSvcbRecord record = makeDnsSvcbRecordWithSingleSvcParam(
                TEST_SVC_PARAM_NO_DEFAULT_ALPN);
        // Check the content returned from toString() for now because the getter function for
        // this SvcParam hasn't been implemented.
        // TODO(b/240259333): Consider adding DnsSvcbRecord.hasNoDefaultAlpn() when needed.
        assertTrue(record.toString().contains("no-default-alpn"));
    }

    @Test
    public void testDnsSvcbRecord_svcParamPort() throws Exception {
        final DnsSvcbRecord record = makeDnsSvcbRecordWithSingleSvcParam(TEST_SVC_PARAM_PORT);
        assertEquals(5353, record.getPort());
    }

    @Test
    public void testDnsSvcbRecord_svcParamIpv4Hint() throws Exception {
        final DnsSvcbRecord record = makeDnsSvcbRecordWithSingleSvcParam(TEST_SVC_PARAM_IPV4HINT_2);
        assertEquals(Arrays.asList(InetAddresses.parseNumericAddress("4.3.2.1")),
                record.getAddresses());
    }

    @Test
    public void testDnsSvcbRecord_svcParamEch() throws Exception {
        final DnsSvcbRecord record = makeDnsSvcbRecordWithSingleSvcParam(TEST_SVC_PARAM_ECH);
        // Check the content returned from toString() for now because the getter function for
        // this SvcParam hasn't been implemented.
        // TODO(b/240259333): Consider adding DnsSvcbRecord.getEch() when needed.
        assertTrue(record.toString().contains("ech=6142634465"));
    }

    @Test
    public void testDnsSvcbRecord_svcParamIpv6Hint() throws Exception {
        final DnsSvcbRecord record = makeDnsSvcbRecordWithSingleSvcParam(TEST_SVC_PARAM_IPV6HINT);
        assertEquals(Arrays.asList(InetAddresses.parseNumericAddress("2001:db8::1")),
                record.getAddresses());
    }

    @Test
    public void testDnsSvcbRecord_svcParamDohPath() throws Exception {
        final DnsSvcbRecord record = makeDnsSvcbRecordWithSingleSvcParam(TEST_SVC_PARAM_DOHPATH);
        assertEquals("/some-path{?dns}", record.getDohPath());
    }

    @Test
    public void testDnsSvcbRecord_svcParamGeneric_withValue() throws Exception {
        final DnsSvcbRecord record = makeDnsSvcbRecordWithSingleSvcParam(
                TEST_SVC_PARAM_GENERIC_WITH_VALUE);
        // Check the content returned from toString() for now because the getter function for
        // generic SvcParam hasn't been implemented.
        // TODO(b/240259333): Consider adding DnsSvcbRecord.getValueFromGenericSvcParam(int key)
        // when needed.
        assertTrue(record.toString().contains("key12345=1A2B0C"));
    }

    @Test
    public void testDnsSvcbRecord_svcParamGeneric_withoutValue() throws Exception {
        final DnsSvcbRecord record = makeDnsSvcbRecordWithSingleSvcParam(
                TEST_SVC_PARAM_GENERIC_WITHOUT_VALUE);
        // Check the content returned from toString() for now because the getter function for
        // generic SvcParam hasn't been implemented.
        // TODO(b/240259333): Consider adding DnsSvcbRecord.getValueFromGenericSvcParam(int key)
        // when needed.
        assertTrue(record.toString().contains("key12346"));
    }

    @Test
    public void testDnsSvcbRecord() throws Exception {
        final DnsSvcbRecord record = makeDnsSvcbRecordFromByteArray(
                new TestDnsRecordByteArrayBuilder()
                .setRRType(TYPE_SVCB)
                .setTargetName("doh.dns.com")
                .addRdata(TEST_SVC_PARAM_ALPN_HTTPS)
                .addRdata(TEST_SVC_PARAM_IPV4HINT_1)
                .addRdata(TEST_SVC_PARAM_IPV6HINT)
                .addRdata(TEST_SVC_PARAM_PORT)
                .addRdata(TEST_SVC_PARAM_DOHPATH)
                .build());
        assertEquals("doh.dns.com", record.getTargetName());
        assertEquals(Arrays.asList("h2", "http/1.1"), record.getAlpns());
        assertEquals(5353, record.getPort());
        assertEquals(Arrays.asList(
                InetAddresses.parseNumericAddress("1.2.3.4"),
                InetAddresses.parseNumericAddress("6.7.8.9"),
                InetAddresses.parseNumericAddress("2001:db8::1")), record.getAddresses());
        assertEquals("/some-path{?dns}", record.getDohPath());
    }

    @Test
    public void testDnsSvcbRecord_createdFromNullObject() throws Exception {
        assertThrows(NullPointerException.class, () -> makeDnsSvcbRecordFromByteArray(null));
    }

    @Test
    public void testDnsSvcbRecord_invalidDnsRecord() throws Exception {
        // The type is not SVCB.
        final byte[] bytes1 = new TestDnsRecordByteArrayBuilder()
                .setRRType(TYPE_A)
                .addRdata(InetAddresses.parseNumericAddress("1.2.3.4").getAddress())
                .build();
        assertThrows(IllegalStateException.class, () -> makeDnsSvcbRecordFromByteArray(bytes1));

        // TargetName is missing.
        final byte[] bytes2 = new TestDnsRecordByteArrayBuilder()
                .setRRType(TYPE_SVCB)
                .addRdata(new byte[] { 0x01, 0x01 })
                .build();
        assertThrows(BufferUnderflowException.class, () -> makeDnsSvcbRecordFromByteArray(bytes2));

        // Rdata is empty.
        final byte[] bytes3 = new TestDnsRecordByteArrayBuilder()
                .setRRType(TYPE_SVCB)
                .build();
        assertThrows(BufferUnderflowException.class, () -> makeDnsSvcbRecordFromByteArray(bytes3));
    }

    @Test
    public void testDnsSvcbRecord_repeatedKeyIsInvalid() throws Exception {
        final byte[] bytes = new TestDnsRecordByteArrayBuilder()
                .setRRType(TYPE_SVCB)
                .addRdata(TEST_SVC_PARAM_ALPN_HTTPS)
                .addRdata(TEST_SVC_PARAM_ALPN_DOQ)
                .build();
        assertThrows(DnsPacket.ParseException.class, () -> makeDnsSvcbRecordFromByteArray(bytes));
    }

    @Test
    public void testDnsSvcbRecord_invalidContent() throws Exception {
        final List<byte[]> invalidContents = Arrays.asList(
                // Invalid SvcParamValue for "mandatory":
                // - SvcParamValue must not be empty.
                // - SvcParamValue has less data than expected.
                // - SvcParamValue has more data than expected.
                // - SvcParamValue must be multiple of 2.
                new byte[] { 0x00, 0x00, 0x00, 0x00},
                new byte[] { 0x00, 0x00, 0x00, 0x02, 0x00, 0x04, 0x00, 0x06 },
                new byte[] { 0x00, 0x00, 0x00, 0x04, 0x00, 0x04, },
                new byte[] { 0x00, 0x00, 0x00, 0x03, 0x00, 0x04, 0x00 },

                // Invalid SvcParamValue for "alpn":
                // - SvcParamValue must not be empty.
                // - SvcParamValue has less data than expected.
                // - SvcParamValue has more data than expected.
                // - Alpn length is less than the actual data size.
                // - Alpn length is more than the actual data size.
                // - Alpn must be a non-empty string.
                new byte[] { 0x00, 0x01, 0x00, 0x00},
                new byte[] { 0x00, 0x01, 0x00, 0x02, 0x02, 'h', '2' },
                new byte[] { 0x00, 0x01, 0x00, 0x05, 0x02, 'h', '2' },
                new byte[] { 0x00, 0x01, 0x00, 0x04, 0x02, 'd', 'o', 't' },
                new byte[] { 0x00, 0x01, 0x00, 0x04, 0x08, 'd', 'o', 't' },
                new byte[] { 0x00, 0x01, 0x00, 0x08, 0x02, 'h', '2', 0x00 },

                // Invalid SvcParamValue for "no-default-alpn":
                // - SvcParamValue must be empty.
                // - SvcParamValue length must be 0.
                new byte[] { 0x00, 0x02, 0x00, 0x04, 'd', 'a', 't', 'a' },
                new byte[] { 0x00, 0x02, 0x00, 0x04 },

                // Invalid SvcParamValue for "port":
                // - SvcParamValue must not be empty.
                // - SvcParamValue has less data than expected.
                // - SvcParamValue has more data than expected.
                // - SvcParamValue length must be multiple of 2.
                new byte[] { 0x00, 0x03, 0x00, 0x00 },
                new byte[] { 0x00, 0x03, 0x00, 0x02, 0x01 },
                new byte[] { 0x00, 0x03, 0x00, 0x02, 0x01, 0x02, 0x03 },
                new byte[] { 0x00, 0x03, 0x00, 0x03, 0x01, 0x02, 0x03 },

                // Invalid SvcParamValue for "ipv4hint":
                // - SvcParamValue must not be empty.
                // - SvcParamValue has less data than expected.
                // - SvcParamValue has more data than expected.
                // - SvcParamValue must be multiple of 4.
                new byte[] { 0x00, 0x04, 0x00, 0x00 },
                new byte[] { 0x00, 0x04, 0x00, 0x04, 0x08 },
                new byte[] { 0x00, 0x04, 0x00, 0x04, 0x08, 0x08, 0x08, 0x08, 0x08 },
                new byte[] { 0x00, 0x04, 0x00, 0x05, 0x08, 0x08, 0x08, 0x08 },

                // Invalid SvcParamValue for "ipv6hint":
                // - SvcParamValue must not be empty.
                // - SvcParamValue has less data than expected.
                // - SvcParamValue has more data than expected.
                // - SvcParamValue must be multiple of 16.
                new byte[] { 0x00, 0x06, 0x00, 0x00 },
                new byte[] { 0x00, 0x06, 0x00, 0x10, 0x01 },
                new byte[] { 0x00, 0x06, 0x00, 0x10, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
                        0x08, 0x09, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17 },
                new byte[] { 0x00, 0x06, 0x00, 0x05, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
                        0x08, 0x09, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16 }
        );

        for (byte[] content : invalidContents) {
            final byte[] bytes = new TestDnsRecordByteArrayBuilder()
                        .setRRType(TYPE_SVCB)
                        .addRdata(content)
                        .build();
            assertThrows(DnsPacket.ParseException.class,
                        () -> makeDnsSvcbRecordFromByteArray(bytes));
        }
    }

    @Test
    public void testDnsSvcbPacket_createdFromNullObject() throws Exception {
        assertThrows(DnsPacket.ParseException.class, () -> DnsSvcbPacket.fromResponse(null));
    }

    @Test
    public void testDnsSvcbPacket() throws Exception {
        final String dohTargetName = "https.dns.com";
        final String doqTargetName = "doq.dns.com";
        final InetAddress[] expectedIpAddressesForHttps = new InetAddress[] {
                InetAddresses.parseNumericAddress("1.2.3.4"),
                InetAddresses.parseNumericAddress("6.7.8.9"),
                InetAddresses.parseNumericAddress("2001:db8::1"),
        };
        final InetAddress[] expectedIpAddressesForDoq = new InetAddress[] {
                InetAddresses.parseNumericAddress("4.3.2.1"),
        };

        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        os.write(makeDnsResponseHeaderAsByteArray(1 /* qdcount */, 2 /* ancount */, 0 /* nscount */,
                0 /* arcount */));
        os.write(TEST_DNS_SVCB_QUESTION_SECTION);
        // Add answer for alpn h2 and http/1.1.
        os.write(new TestDnsRecordByteArrayBuilder()
                .setNameCompression(true)
                .setRRType(TYPE_SVCB)
                .setTargetName(dohTargetName)
                .addRdata(TEST_SVC_PARAM_ALPN_HTTPS)
                .addRdata(TEST_SVC_PARAM_IPV4HINT_1)
                .addRdata(TEST_SVC_PARAM_IPV6HINT)
                .addRdata(TEST_SVC_PARAM_PORT)
                .addRdata(TEST_SVC_PARAM_DOHPATH)
                .build());
        // Add answer for alpn doq.
        os.write(new TestDnsRecordByteArrayBuilder()
                .setNameCompression(true)
                .setRRType(TYPE_SVCB)
                .setTargetName(doqTargetName)
                .setSvcPriority(2)
                .addRdata(TEST_SVC_PARAM_ALPN_DOQ)
                .addRdata(TEST_SVC_PARAM_IPV4HINT_2)
                .build());
        final DnsSvcbPacket pkt = DnsSvcbPacket.fromResponse(os.toByteArray());

        assertTrue(pkt.isSupported("http/1.1"));
        assertTrue(pkt.isSupported("h2"));
        assertTrue(pkt.isSupported("doq"));
        assertFalse(pkt.isSupported("http"));
        assertFalse(pkt.isSupported("h3"));
        assertFalse(pkt.isSupported(""));

        assertEquals(dohTargetName, pkt.getTargetName("http/1.1"));
        assertEquals(dohTargetName, pkt.getTargetName("h2"));
        assertEquals(doqTargetName, pkt.getTargetName("doq"));
        assertEquals(null, pkt.getTargetName("http"));
        assertEquals(null, pkt.getTargetName("h3"));
        assertEquals(null, pkt.getTargetName(""));

        assertEquals(5353, pkt.getPort("http/1.1"));
        assertEquals(5353, pkt.getPort("h2"));
        assertEquals(-1, pkt.getPort("doq"));
        assertEquals(-1, pkt.getPort("http"));
        assertEquals(-1, pkt.getPort("h3"));
        assertEquals(-1, pkt.getPort(""));

        assertArrayEquals(expectedIpAddressesForHttps, pkt.getAddresses("http/1.1").toArray());
        assertArrayEquals(expectedIpAddressesForHttps, pkt.getAddresses("h2").toArray());
        assertArrayEquals(expectedIpAddressesForDoq, pkt.getAddresses("doq").toArray());
        assertTrue(pkt.getAddresses("http").isEmpty());
        assertTrue(pkt.getAddresses("h3").isEmpty());
        assertTrue(pkt.getAddresses("").isEmpty());

        assertEquals("/some-path{?dns}", pkt.getDohPath("http/1.1"));
        assertEquals("/some-path{?dns}", pkt.getDohPath("h2"));
        assertEquals("", pkt.getDohPath("doq"));
        assertEquals(null, pkt.getDohPath("http"));
        assertEquals(null, pkt.getDohPath("h3"));
        assertEquals(null, pkt.getDohPath(""));
    }

    @Test
    public void testDnsSvcbPacket_noIpHint() throws Exception {
        final String targetName = "doq.dns.com";
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        os.write(makeDnsResponseHeaderAsByteArray(1 /* qdcount */, 1 /* ancount */, 0 /* nscount */,
                0 /* arcount */));
        os.write(TEST_DNS_SVCB_QUESTION_SECTION);
        // Add answer for alpn doq.
        os.write(new TestDnsRecordByteArrayBuilder()
                .setNameCompression(true)
                .setRRType(TYPE_SVCB)
                .setTargetName(targetName)
                .addRdata(TEST_SVC_PARAM_ALPN_DOQ)
                .build());
        final DnsSvcbPacket pkt = DnsSvcbPacket.fromResponse(os.toByteArray());

        assertTrue(pkt.isSupported("doq"));
        assertEquals(targetName, pkt.getTargetName("doq"));
        assertEquals(-1, pkt.getPort("doq"));
        assertArrayEquals(new InetAddress[] {}, pkt.getAddresses("doq").toArray());
        assertEquals("", pkt.getDohPath("doq"));
    }

    @Test
    public void testDnsSvcbPacket_hasAnswerInAdditionalSection() throws Exception {
        final InetAddress[] expectedIpAddresses = new InetAddress[] {
                InetAddresses.parseNumericAddress("1.2.3.4"),
                InetAddresses.parseNumericAddress("2001:db8::2"),
        };

        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        os.write(makeDnsResponseHeaderAsByteArray(1 /* qdcount */, 1 /* ancount */, 0 /* nscount */,
                2 /* arcount */));
        os.write(TEST_DNS_SVCB_QUESTION_SECTION);
        // Add SVCB record in the Answer section.
        os.write(new TestDnsRecordByteArrayBuilder()
                .setNameCompression(true)
                .setRRType(TYPE_SVCB)
                .setTargetName("doq.dns.com")
                .addRdata(TEST_SVC_PARAM_ALPN_DOQ)
                .addRdata(TEST_SVC_PARAM_IPV4HINT_2)
                .addRdata(TEST_SVC_PARAM_IPV6HINT)
                .build());
        // Add A/AAAA records in the Additional section.
        os.write(new TestDnsRecordByteArrayBuilder()
                .setNameCompression(true)
                .setRRType(TYPE_A)
                .addRdata(InetAddresses.parseNumericAddress("1.2.3.4").getAddress())
                .build());
        os.write(new TestDnsRecordByteArrayBuilder()
                .setNameCompression(true)
                .setRRType(TYPE_AAAA)
                .addRdata(InetAddresses.parseNumericAddress("2001:db8::2").getAddress())
                .build());
        final DnsSvcbPacket pkt = DnsSvcbPacket.fromResponse(os.toByteArray());

        // If there are A/AAAA records in the Additional section, getAddresses() returns the IP
        // addresses in those records instead of the IP addresses in ipv4hint/ipv6hint.
        assertArrayEquals(expectedIpAddresses, pkt.getAddresses("doq").toArray());
    }
}
