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

import static com.android.net.module.util.DnsPacketUtils.DnsRecordParser;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.net.ParseException;
import android.os.Build;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.testutils.DevSdkIgnoreRule;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class DnsPacketUtilsTest {
    @Rule
    public final DevSdkIgnoreRule mIgnoreRule = new DevSdkIgnoreRule();

    /**
     * Verifies that the compressed NAME field in the answer section of the DNS message is parsed
     * successfully when name compression is permitted. Additionally, verifies that a
     * {@link DnsPacket.ParseException} is thrown in a hypothetical scenario where name compression
     * is not expected.
     */
    @Test
    public void testParsingAnswerSectionNameCompressed() throws Exception {
        final byte[] v4blobNameCompressedAnswer = new byte[] {
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
        final int answerOffsetBytePosition = 32;
        final ByteBuffer nameCompressedBuf = ByteBuffer.wrap(v4blobNameCompressedAnswer);

        nameCompressedBuf.position(answerOffsetBytePosition);
        assertThrows(DnsPacket.ParseException.class, () -> DnsRecordParser.parseName(
                nameCompressedBuf, /* depth= */ 0, /* isNameCompressionSupported= */false));

        nameCompressedBuf.position(answerOffsetBytePosition);
        String domainName = DnsRecordParser.parseName(
                nameCompressedBuf, /* depth= */ 0, /* isNameCompressionSupported= */true);
        assertEquals(domainName, "www.google.com");
    }

    /**
     * Verifies that an uncompressed NAME field in the answer section of the DNS message is parsed
     * successfully irrespective of whether name compression is permitted.
     */
    @Test
    public void testParsingAnswerSectionNoNameCompression() throws Exception {
        final byte[] v4blobNoNameCompression = new byte[] {
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
                0x03, 0x77, 0x77, 0x77, 0x06, 0x67, 0x6F, 0x6F, 0x67, 0x6c, 0x65,
                0x03, 0x63, 0x6f, 0x6d, 0x00, /* Name */
                0x00, 0x01, /* Type */
                0x00, 0x01, /* Class */
                0x00, 0x00, 0x01, 0x2b, /* TTL */
                0x00, 0x04, /* Data length */
                (byte) 0xac, (byte) 0xd9, (byte) 0xa1, (byte) 0x84 /* Address */
        };
        final int answerOffsetBytePosition = 32;
        final ByteBuffer notNameCompressedBuf = ByteBuffer.wrap(v4blobNoNameCompression);

        notNameCompressedBuf.position(answerOffsetBytePosition);
        String domainName = DnsRecordParser.parseName(
                notNameCompressedBuf, /* depth= */ 0, /* isNameCompressionSupported= */ true);
        assertEquals(domainName, "www.google.com");

        notNameCompressedBuf.position(answerOffsetBytePosition);
        domainName = DnsRecordParser.parseName(
                notNameCompressedBuf, /* depth= */ 0, /* isNameCompressionSupported= */ false);
        assertEquals(domainName, "www.google.com");
    }

    // Skip test on R- devices since ParseException only available on S+ devices.
    @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R)
    @Test
    public void testDomainNameToLabels() throws Exception {
        assertArrayEquals(
                new byte[]{3, 'w', 'w', 'w', 6, 'g', 'o', 'o', 'g', 'l', 'e', 3, 'c', 'o', 'm', 0},
                DnsRecordParser.domainNameToLabels("www.google.com"));
        assertThrows(ParseException.class, () ->
                DnsRecordParser.domainNameToLabels("aaa."));
        assertThrows(ParseException.class, () ->
                DnsRecordParser.domainNameToLabels("aaa"));
        assertThrows(ParseException.class, () ->
                DnsRecordParser.domainNameToLabels("."));
        assertThrows(ParseException.class, () ->
                DnsRecordParser.domainNameToLabels(""));
    }

    @Test
    public void testIsHostName() {
        Assert.assertTrue(DnsRecordParser.isHostName("www.google.com"));
        Assert.assertFalse(DnsRecordParser.isHostName("com"));
        Assert.assertFalse(DnsRecordParser.isHostName("1.2.3.4"));
        Assert.assertFalse(DnsRecordParser.isHostName("1234::5678"));
        Assert.assertFalse(DnsRecordParser.isHostName(null));
        Assert.assertFalse(DnsRecordParser.isHostName(""));
    }
}
