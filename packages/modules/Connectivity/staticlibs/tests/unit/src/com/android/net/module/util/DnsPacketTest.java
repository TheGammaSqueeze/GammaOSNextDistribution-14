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

package com.android.net.module.util;

import static android.net.DnsResolver.CLASS_IN;
import static android.net.DnsResolver.TYPE_A;
import static android.net.DnsResolver.TYPE_AAAA;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.annotation.NonNull;
import android.annotation.Nullable;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import libcore.net.InetAddressUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class DnsPacketTest {
    private static final int TEST_DNS_PACKET_ID = 0x7722;
    private static final int TEST_DNS_PACKET_FLAGS = 0x8180;

    private void assertHeaderParses(DnsPacket.DnsHeader header, int id, int flag,
            int qCount, int aCount, int nsCount, int arCount) {
        assertEquals(header.getId(), id);
        assertEquals(header.getFlags(), flag);
        assertEquals(header.getRecordCount(DnsPacket.QDSECTION), qCount);
        assertEquals(header.getRecordCount(DnsPacket.ANSECTION), aCount);
        assertEquals(header.getRecordCount(DnsPacket.NSSECTION), nsCount);
        assertEquals(header.getRecordCount(DnsPacket.ARSECTION), arCount);
    }

    private void assertRecordParses(DnsPacket.DnsRecord record, String dname,
            int dtype, int dclass, int ttl, byte[] rr) {
        assertEquals(record.dName, dname);
        assertEquals(record.nsType, dtype);
        assertEquals(record.nsClass, dclass);
        assertEquals(record.ttl, ttl);
        assertTrue(Arrays.equals(record.getRR(), rr));
    }

    static class TestDnsPacket extends DnsPacket {
        TestDnsPacket(byte[] data) throws DnsPacket.ParseException {
            super(data);
        }

        TestDnsPacket(@NonNull DnsHeader header, @Nullable ArrayList<DnsRecord> qd,
                @Nullable ArrayList<DnsRecord> an) {
            super(header, qd, an);
        }

        public DnsHeader getHeader() {
            return mHeader;
        }
        public List<DnsRecord> getRecordList(int secType) {
            return mRecords[secType];
        }
    }

    @Test
    public void testNullDisallowed() {
        try {
            new TestDnsPacket(null);
            fail("Exception not thrown for null byte array");
        } catch (DnsPacket.ParseException e) {
        }
    }

    @Test
    public void testV4Answer() throws Exception {
        final byte[] v4blob = new byte[] {
            /* Header */
            0x55, 0x66, /* Transaction ID */
            (byte) 0x81, (byte) 0x80, /* Flags */
            0x00, 0x01, /* Questions */
            0x00, 0x01, /* Answer RRs */
            0x00, 0x00, /* Authority RRs */
            0x00, 0x00, /* Additional RRs */
            /* Queries */
            0x03, 0x77, 0x77, 0x77, 0x06, 0x67, 0x6F, 0x6F, 0x67, 0x6c, 0x65,
            0x03, 0x63, 0x6f, 0x6d, 0x00, /* Name */
            0x00, 0x01, /* Type */
            0x00, 0x01, /* Class */
            /* Answers */
            (byte) 0xc0, 0x0c, /* Name */
            0x00, 0x01, /* Type */
            0x00, 0x01, /* Class */
            0x00, 0x00, 0x01, 0x2b, /* TTL */
            0x00, 0x04, /* Data length */
            (byte) 0xac, (byte) 0xd9, (byte) 0xa1, (byte) 0x84 /* Address */
        };
        TestDnsPacket packet = new TestDnsPacket(v4blob);

        // Header part
        assertHeaderParses(packet.getHeader(), 0x5566, 0x8180, 1, 1, 0, 0);

        // Record part
        List<DnsPacket.DnsRecord> qdRecordList =
                packet.getRecordList(DnsPacket.QDSECTION);
        assertEquals(qdRecordList.size(), 1);
        assertRecordParses(qdRecordList.get(0), "www.google.com", 1, 1, 0, null);

        List<DnsPacket.DnsRecord> anRecordList =
                packet.getRecordList(DnsPacket.ANSECTION);
        assertEquals(anRecordList.size(), 1);
        assertRecordParses(anRecordList.get(0), "www.google.com", 1, 1, 0x12b,
                new byte[]{ (byte) 0xac, (byte) 0xd9, (byte) 0xa1, (byte) 0x84 });
    }

    @Test
    public void testV6Answer() throws Exception {
        final byte[] v6blob = new byte[] {
            /* Header */
            0x77, 0x22, /* Transaction ID */
            (byte) 0x81, (byte) 0x80, /* Flags */
            0x00, 0x01, /* Questions */
            0x00, 0x01, /* Answer RRs */
            0x00, 0x00, /* Authority RRs */
            0x00, 0x00, /* Additional RRs */
            /* Queries */
            0x03, 0x77, 0x77, 0x77, 0x06, 0x67, 0x6F, 0x6F, 0x67, 0x6c, 0x65,
            0x03, 0x63, 0x6f, 0x6d, 0x00, /* Name */
            0x00, 0x1c, /* Type */
            0x00, 0x01, /* Class */
            /* Answers */
            (byte) 0xc0, 0x0c, /* Name */
            0x00, 0x1c, /* Type */
            0x00, 0x01, /* Class */
            0x00, 0x00, 0x00, 0x37, /* TTL */
            0x00, 0x10, /* Data length */
            0x24, 0x04, 0x68, 0x00, 0x40, 0x05, 0x08, 0x0d,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x20, 0x04 /* Address */
        };
        TestDnsPacket packet = new TestDnsPacket(v6blob);

        // Header part
        assertHeaderParses(packet.getHeader(), 0x7722, 0x8180, 1, 1, 0, 0);

        // Record part
        List<DnsPacket.DnsRecord> qdRecordList =
                packet.getRecordList(DnsPacket.QDSECTION);
        assertEquals(qdRecordList.size(), 1);
        assertRecordParses(qdRecordList.get(0), "www.google.com", 28, 1, 0, null);

        List<DnsPacket.DnsRecord> anRecordList =
                packet.getRecordList(DnsPacket.ANSECTION);
        assertEquals(anRecordList.size(), 1);
        assertRecordParses(anRecordList.get(0), "www.google.com", 28, 1, 0x37,
                new byte[]{ 0x24, 0x04, 0x68, 0x00, 0x40, 0x05, 0x08, 0x0d,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x20, 0x04 });
    }

    /** Verifies that the synthesized {@link DnsPacket.DnsHeader} can be parsed correctly. */
    @Test
    public void testDnsHeaderSynthesize() {
        final DnsPacket.DnsHeader testHeader = new DnsPacket.DnsHeader(TEST_DNS_PACKET_ID,
                TEST_DNS_PACKET_FLAGS, 3 /* qcount */, 5 /* ancount */);
        final DnsPacket.DnsHeader actualHeader = new DnsPacket.DnsHeader(
                ByteBuffer.wrap(testHeader.getBytes()));
        assertEquals(testHeader, actualHeader);
    }

    /** Verifies that the synthesized {@link DnsPacket.DnsRecord} can be parsed correctly. */
    @Test
    public void testDnsRecordSynthesize() throws IOException {
        assertDnsRecordRoundTrip(
                DnsPacket.DnsRecord.makeAOrAAAARecord(DnsPacket.ANSECTION,
                        "test.com", CLASS_IN, 5 /* ttl */,
                        InetAddressUtils.parseNumericAddress("abcd::fedc")));
        assertDnsRecordRoundTrip(DnsPacket.DnsRecord.makeQuestion("test.com", TYPE_AAAA, CLASS_IN));
        assertDnsRecordRoundTrip(DnsPacket.DnsRecord.makeCNameRecord(DnsPacket.ANSECTION,
                "test.com", CLASS_IN, 0 /* ttl */, "example.com"));
    }

    /** Verifies that the type of implementation returned from DnsRecord#parse is correct */
    @Test
    public void testDnsRecordParse() throws IOException {
        final byte[] svcbQuestionRecord = new byte[] {
                0x07, 'e', 'x', 'a', 'm', 'p', 'l', 'e', 0x03, 'c', 'o', 'm', 0x00, /* Name */
                0x00, 0x40, /* Type */
                0x00, 0x01, /* Class */
        };
        assertTrue(DnsPacket.DnsRecord.parse(DnsPacket.QDSECTION,
                ByteBuffer.wrap(svcbQuestionRecord)) instanceof DnsSvcbRecord);

        final byte[] svcbAnswerRecord = new byte[] {
                0x07, 'e', 'x', 'a', 'm', 'p', 'l', 'e', 0x03, 'c', 'o', 'm', 0x00, /* Name */
                0x00, 0x40, /* Type */
                0x00, 0x01, /* Class */
                0x00, 0x00, 0x01, 0x2b, /* TTL */
                0x00, 0x0b, /* Data length */
                0x00, 0x01, /* SvcPriority */
                0x03, 'd', 'o', 't', 0x03, 'c', 'o', 'm', 0x00, /* TargetName */
        };
        assertTrue(DnsPacket.DnsRecord.parse(DnsPacket.ANSECTION,
                ByteBuffer.wrap(svcbAnswerRecord)) instanceof DnsSvcbRecord);
    }

    /**
     * Verifies ttl/rData error handling when parsing
     * {@link DnsPacket.DnsRecord} from bytes.
     */
    @Test
    public void testDnsRecordTTLRDataErrorHandling() throws IOException {
        // Verify the constructor ignore ttl/rData of questions even if they are supplied.
        final byte[] qdWithTTLRData = new byte[]{
                0x03, 0x77, 0x77, 0x77, 0x06, 0x67, 0x6F, 0x6F, 0x67, 0x6c, 0x65,
                0x03, 0x63, 0x6f, 0x6d, 0x00, /* Name */
                0x00, 0x00, /* Type */
                0x00, 0x01, /* Class */
                0x00, 0x00, 0x01, 0x2b, /* TTL */
                0x00, 0x04, /* Data length */
                (byte) 0xac, (byte) 0xd9, (byte) 0xa1, (byte) 0x84 /* Address */};
        final DnsPacket.DnsRecord questionsFromBytes =
                DnsPacket.DnsRecord.parse(DnsPacket.QDSECTION, ByteBuffer.wrap(qdWithTTLRData));
        assertEquals(0, questionsFromBytes.ttl);
        assertNull(questionsFromBytes.getRR());

        // Verify ANSECTION must have rData when constructing.
        final byte[] anWithoutTTLRData = new byte[]{
                0x03, 0x77, 0x77, 0x77, 0x06, 0x67, 0x6F, 0x6F, 0x67, 0x6c, 0x65,
                0x03, 0x63, 0x6f, 0x6d, 0x00, /* Name */
                0x00, 0x01, /* Type */
                0x00, 0x01, /* Class */};
        assertThrows(BufferUnderflowException.class, () ->
                DnsPacket.DnsRecord.parse(DnsPacket.ANSECTION, ByteBuffer.wrap(anWithoutTTLRData)));
    }

    private void assertDnsRecordRoundTrip(DnsPacket.DnsRecord before)
            throws IOException {
        final DnsPacket.DnsRecord after = DnsPacket.DnsRecord.parse(before.rType,
                ByteBuffer.wrap(before.getBytes()));
        assertEquals(after, before);
    }

    /** Verifies that the synthesized {@link DnsPacket} can be parsed correctly. */
    @Test
    public void testDnsPacketSynthesize() throws IOException {
        // Ipv4 dns response packet generated by scapy:
        //   dns_r = scapy.DNS(
        //      id=0xbeef,
        //      qr=1,
        //      qd=scapy.DNSQR(qname="hello.example.com"),
        //      an=scapy.DNSRR(rrname="hello.example.com", type="CNAME", rdata='test.com') /
        //      scapy.DNSRR(rrname="hello.example.com", rdata='1.2.3.4'))
        //   scapy.hexdump(dns_r)
        //   dns_r.show2()
        // Note that since the synthesizing does not support name compression yet, the domain
        // name of the sample need to be uncompressed when generating.
        final byte[] v4BlobUncompressed = new byte[]{
                /* Header */
                (byte) 0xbe, (byte) 0xef, /* Transaction ID */
                (byte) 0x81, 0x00, /* Flags */
                0x00, 0x01, /* Questions */
                0x00, 0x02, /* Answer RRs */
                0x00, 0x00, /* Authority RRs */
                0x00, 0x00, /* Additional RRs */
                /* Queries */
                0x05, 0x68, 0x65, 0x6C, 0x6C, 0x6F, 0x07, 0x65, 0x78, 0x61,
                0x6D, 0x70, 0x6C, 0x65, 0x03, 0x63, 0x6F, 0x6D, 0x00, /* Name: hello.example.com */
                0x00, 0x01, /* Type */
                0x00, 0x01, /* Class */
                /* Answers */
                0x05, 0x68, 0x65, 0x6C, 0x6C, 0x6F, 0x07, 0x65, 0x78, 0x61,
                0x6D, 0x70, 0x6C, 0x65, 0x03, 0x63, 0x6F, 0x6D, 0x00, /* Name: hello.example.com */
                0x00, 0x05, /* Type */
                0x00, 0x01, /* Class */
                0x00, 0x00, 0x00, 0x00, /* TTL */
                0x00, 0x0A, /* Data length */
                0x04, 0x74, 0x65, 0x73, 0x74, 0x03, 0x63, 0x6F, 0x6D, 0x00, /* Alias: test.com */
                0x05, 0x68, 0x65, 0x6C, 0x6C, 0x6F, 0x07, 0x65, 0x78, 0x61,
                0x6D, 0x70, 0x6C, 0x65, 0x03, 0x63, 0x6F, 0x6D, 0x00, /* Name: hello.example.com */
                0x00, 0x01, /* Type */
                0x00, 0x01, /* Class */
                0x00, 0x00, 0x00, 0x00, /* TTL */
                0x00, 0x04, /* Data length */
                0x01, 0x02, 0x03, 0x04, /* Address: 1.2.3.4 */
        };

        // Forge one via constructors.
        final DnsPacket.DnsHeader testHeader = new DnsPacket.DnsHeader(0xbeef,
                0x8100, 1 /* qcount */, 2 /* ancount */);
        final ArrayList<DnsPacket.DnsRecord> qlist = new ArrayList<>();
        final ArrayList<DnsPacket.DnsRecord> alist = new ArrayList<>();
        qlist.add(DnsPacket.DnsRecord.makeQuestion(
                "hello.example.com", TYPE_A, CLASS_IN));
        alist.add(DnsPacket.DnsRecord.makeCNameRecord(
                DnsPacket.ANSECTION, "hello.example.com", CLASS_IN, 0 /* ttl */, "test.com"));
        alist.add(DnsPacket.DnsRecord.makeAOrAAAARecord(
                DnsPacket.ANSECTION, "hello.example.com", CLASS_IN, 0 /* ttl */,
                InetAddressUtils.parseNumericAddress("1.2.3.4")));
        final TestDnsPacket testPacket = new TestDnsPacket(testHeader, qlist, alist);

        // Assert content equals in both ways.
        assertTrue(Arrays.equals(v4BlobUncompressed, testPacket.getBytes()));
        assertEquals(new TestDnsPacket(v4BlobUncompressed), testPacket);
    }

    @Test
    public void testDnsPacketSynthesize_recordCountMismatch() throws IOException {
        final DnsPacket.DnsHeader testHeader = new DnsPacket.DnsHeader(0xbeef,
                0x8100, 1 /* qcount */, 1 /* ancount */);
        final ArrayList<DnsPacket.DnsRecord> qlist = new ArrayList<>();
        final ArrayList<DnsPacket.DnsRecord> alist = new ArrayList<>();
        qlist.add(DnsPacket.DnsRecord.makeQuestion(
                "hello.example.com", TYPE_A, CLASS_IN));

        // Assert throws if the supplied answer records fewer than the declared count.
        assertThrows(IllegalArgumentException.class, () ->
                new TestDnsPacket(testHeader, qlist, alist));

        // Assert throws if the supplied answer records more than the declared count.
        alist.add(DnsPacket.DnsRecord.makeCNameRecord(
                DnsPacket.ANSECTION, "hello.example.com", CLASS_IN, 0 /* ttl */, "test.com"));
        alist.add(DnsPacket.DnsRecord.makeAOrAAAARecord(
                DnsPacket.ANSECTION, "hello.example.com", CLASS_IN, 0 /* ttl */,
                InetAddressUtils.parseNumericAddress("1.2.3.4")));
        assertThrows(IllegalArgumentException.class, () ->
                new TestDnsPacket(testHeader, qlist, alist));

        // Assert counts matched if the byte buffer still has data when parsing ended.
        final byte[] blobTooMuchData = new byte[]{
                /* Header */
                (byte) 0xbe, (byte) 0xef, /* Transaction ID */
                (byte) 0x81, 0x00, /* Flags */
                0x00, 0x00, /* Questions */
                0x00, 0x00, /* Answer RRs */
                0x00, 0x00, /* Authority RRs */
                0x00, 0x00, /* Additional RRs */
                /* Queries */
                0x05, 0x68, 0x65, 0x6C, 0x6C, 0x6F, 0x07, 0x65, 0x78, 0x61,
                0x6D, 0x70, 0x6C, 0x65, 0x03, 0x63, 0x6F, 0x6D, 0x00, /* Name */
                0x00, 0x01, /* Type */
                0x00, 0x01, /* Class */
        };
        final TestDnsPacket packetFromTooMuchData = new TestDnsPacket(blobTooMuchData);
        for (int i = 0; i < DnsPacket.NUM_SECTIONS; i++) {
            assertEquals(0, packetFromTooMuchData.getRecordList(i).size());
            assertEquals(0, packetFromTooMuchData.getHeader().getRecordCount(i));
        }

        // Assert throws if the byte buffer ended when expecting more records.
        final byte[] blobNotEnoughData = new byte[]{
                /* Header */
                (byte) 0xbe, (byte) 0xef, /* Transaction ID */
                (byte) 0x81, 0x00, /* Flags */
                0x00, 0x01, /* Questions */
                0x00, 0x02, /* Answer RRs */
                0x00, 0x00, /* Authority RRs */
                0x00, 0x00, /* Additional RRs */
                /* Queries */
                0x05, 0x68, 0x65, 0x6C, 0x6C, 0x6F, 0x07, 0x65, 0x78, 0x61,
                0x6D, 0x70, 0x6C, 0x65, 0x03, 0x63, 0x6F, 0x6D, 0x00, /* Name */
                0x00, 0x01, /* Type */
                0x00, 0x01, /* Class */
                /* Answers */
                0x05, 0x68, 0x65, 0x6C, 0x6C, 0x6F, 0x07, 0x65, 0x78, 0x61,
                0x6D, 0x70, 0x6C, 0x65, 0x03, 0x63, 0x6F, 0x6D, 0x00, /* Name */
                0x00, 0x01, /* Type */
                0x00, 0x01, /* Class */
                0x00, 0x00, 0x00, 0x00, /* TTL */
                0x00, 0x04, /* Data length */
                0x01, 0x02, 0x03, 0x04, /* Address */
        };
        assertThrows(DnsPacket.ParseException.class, () -> new TestDnsPacket(blobNotEnoughData));
    }

    @Test
    public void testEqualsAndHashCode() throws IOException {
        // Verify DnsHeader equals and hashCode.
        final DnsPacket.DnsHeader testHeader = new DnsPacket.DnsHeader(TEST_DNS_PACKET_ID,
                TEST_DNS_PACKET_FLAGS, 1 /* qcount */, 1 /* ancount */);
        final DnsPacket.DnsHeader emptyHeader = new DnsPacket.DnsHeader(TEST_DNS_PACKET_ID + 1,
                TEST_DNS_PACKET_FLAGS + 0x08, 0 /* qcount */, 0 /* ancount */);
        final DnsPacket.DnsHeader headerFromBytes =
                new DnsPacket.DnsHeader(ByteBuffer.wrap(testHeader.getBytes()));
        assertEquals(testHeader, headerFromBytes);
        assertEquals(testHeader.hashCode(), headerFromBytes.hashCode());
        assertNotEquals(testHeader, emptyHeader);
        assertNotEquals(testHeader.hashCode(), emptyHeader.hashCode());
        assertNotEquals(headerFromBytes, emptyHeader);
        assertNotEquals(headerFromBytes.hashCode(), emptyHeader.hashCode());

        // Verify DnsRecord equals and hashCode.
        final DnsPacket.DnsRecord testQuestion = DnsPacket.DnsRecord.makeQuestion(
                "test.com", TYPE_AAAA, CLASS_IN);
        final DnsPacket.DnsRecord testAnswer = DnsPacket.DnsRecord.makeCNameRecord(
                DnsPacket.ANSECTION, "test.com", CLASS_IN, 9, "www.test.com");
        final DnsPacket.DnsRecord questionFromBytes = DnsPacket.DnsRecord.parse(DnsPacket.QDSECTION,
                ByteBuffer.wrap(testQuestion.getBytes()));
        assertEquals(testQuestion, questionFromBytes);
        assertEquals(testQuestion.hashCode(), questionFromBytes.hashCode());
        assertNotEquals(testQuestion, testAnswer);
        assertNotEquals(testQuestion.hashCode(), testAnswer.hashCode());
        assertNotEquals(questionFromBytes, testAnswer);
        assertNotEquals(questionFromBytes.hashCode(), testAnswer.hashCode());

        // Verify DnsPacket equals and hashCode.
        final ArrayList<DnsPacket.DnsRecord> qlist = new ArrayList<>();
        final ArrayList<DnsPacket.DnsRecord> alist = new ArrayList<>();
        qlist.add(testQuestion);
        alist.add(testAnswer);
        final TestDnsPacket testPacket = new TestDnsPacket(testHeader, qlist, alist);
        final TestDnsPacket emptyPacket = new TestDnsPacket(
                emptyHeader, new ArrayList<>(), new ArrayList<>());
        final TestDnsPacket packetFromBytes = new TestDnsPacket(testPacket.getBytes());
        assertEquals(testPacket, packetFromBytes);
        assertEquals(testPacket.hashCode(), packetFromBytes.hashCode());
        assertNotEquals(testPacket, emptyPacket);
        assertNotEquals(testPacket.hashCode(), emptyPacket.hashCode());
        assertNotEquals(packetFromBytes, emptyPacket);
        assertNotEquals(packetFromBytes.hashCode(), emptyPacket.hashCode());

        // Verify DnsPacket with empty list.
        final TestDnsPacket emptyPacketFromBytes = new TestDnsPacket(emptyPacket.getBytes());
        assertEquals(emptyPacket, emptyPacketFromBytes);
        assertEquals(emptyPacket.hashCode(), emptyPacketFromBytes.hashCode());
    }
}
