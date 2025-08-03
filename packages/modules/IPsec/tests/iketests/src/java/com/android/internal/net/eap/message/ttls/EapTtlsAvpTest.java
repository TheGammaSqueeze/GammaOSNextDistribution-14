/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.internal.net.eap.test.message.ttls;

import static com.android.internal.net.TestUtils.hexStringToByteArray;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.internal.net.eap.test.EapResult.EapError;
import com.android.internal.net.eap.test.crypto.TlsSession;
import com.android.internal.net.eap.test.exceptions.ttls.EapTtlsParsingException;
import com.android.internal.net.eap.test.message.ttls.EapTtlsAvp.EapTtlsAvpDecoder;
import com.android.internal.net.eap.test.message.ttls.EapTtlsAvp.EapTtlsAvpDecoder.AvpDecodeResult;

import org.junit.Test;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

public class EapTtlsAvpTest {
    private static final int EAP_MESSAGE_AVP_CODE = 79;
    private static final int SAMPLE_VENDOR_ID = 100;
    private static final int DEFAULT_VENDOR_ID = 0;

    private static final String AVP_DUMMY_DATA_PADDING_REQUIRED = "160304";
    private static final String AVP_DUMMY_DATA_PADDING_NOT_REQUIRED = "16030406";

    private static final byte[] AVP_DUMMY_DATA_PADDING_REQUIRED_BYTES =
            hexStringToByteArray(AVP_DUMMY_DATA_PADDING_REQUIRED);
    private static final byte[] AVP_DUMMY_DATA_PADDING_NOT_REQUIRED_BYTES =
            hexStringToByteArray(AVP_DUMMY_DATA_PADDING_NOT_REQUIRED);

    private static final String EAP_MESSAGE_AVP_WITHOUT_VENDOR_ID_PADDING_REQUIRED =
            "0000004F" + "40" + "00000B" // AVP Code | AVP Flags | AVP Length
                    + AVP_DUMMY_DATA_PADDING_REQUIRED
                    + "00"; // Padding
    private static final String EAP_MESSAGE_AVP_WITH_VENDOR_ID_PADDING_REQUIRED =
            "0000004F" + "C0" + "00000F" // AVP Code | AVP Flags | AVP Length
                    + "00000064" // Vendor-ID
                    + AVP_DUMMY_DATA_PADDING_REQUIRED
                    + "00"; // Padding
    private static final String OTHER_AVP_WITH_VENDOR_ID =
            "0000000F" + "80" + "00000F" // AVP Code | AVP Flags | AVP Length
                    + "00000064" // Vendor-ID
                    + AVP_DUMMY_DATA_PADDING_REQUIRED
                    + "00"; // Padding
    private static final String OTHER_AVP_WITHOUT_VENDOR_ID =
            "0000000F" + "00" + "00000B" // AVP Code | AVP Flags | AVP Length
                    + AVP_DUMMY_DATA_PADDING_REQUIRED
                    + "00"; // Padding

    private static final byte[] EAP_MESSAGE_AVP_WITHOUT_VENDOR_ID_PADDING_REQUIRED_BYTES =
            hexStringToByteArray(EAP_MESSAGE_AVP_WITHOUT_VENDOR_ID_PADDING_REQUIRED);
    private static final byte[] EAP_MESSAGE_AVP_WITH_VENDOR_ID_PADDING_REQUIRED_BYTES =
            hexStringToByteArray(EAP_MESSAGE_AVP_WITH_VENDOR_ID_PADDING_REQUIRED);
    private static final byte[] MULTIPLE_AVPS_EAP_MESSAGE_WITH_VENDOR_ID_BYTES =
            hexStringToByteArray(
                    OTHER_AVP_WITH_VENDOR_ID
                            + OTHER_AVP_WITH_VENDOR_ID
                            + OTHER_AVP_WITHOUT_VENDOR_ID
                            + EAP_MESSAGE_AVP_WITH_VENDOR_ID_PADDING_REQUIRED);
    private static final byte[] MULTIPLE_AVPS_EAP_MESSAGE_WITHOUT_VENDOR_ID_BYTES =
            hexStringToByteArray(
                    OTHER_AVP_WITHOUT_VENDOR_ID
                            + OTHER_AVP_WITH_VENDOR_ID
                            + EAP_MESSAGE_AVP_WITHOUT_VENDOR_ID_PADDING_REQUIRED
                            + OTHER_AVP_WITH_VENDOR_ID);
    private static final byte[] MULTIPLE_EAP_MESSAGE_AVPS_BYTES =
            hexStringToByteArray(
                    OTHER_AVP_WITH_VENDOR_ID
                            + EAP_MESSAGE_AVP_WITHOUT_VENDOR_ID_PADDING_REQUIRED
                            + OTHER_AVP_WITHOUT_VENDOR_ID
                            + EAP_MESSAGE_AVP_WITH_VENDOR_ID_PADDING_REQUIRED);
    private static final byte[] MULTIPLE_AVPS_WITHOUT_EAP_MESSAGE_BYTES =
            hexStringToByteArray(
                    OTHER_AVP_WITHOUT_VENDOR_ID
                            + OTHER_AVP_WITH_VENDOR_ID
                            + OTHER_AVP_WITH_VENDOR_ID);
    private static final byte[] EAP_MESSAGE_AVP_WITHOUT_VENDOR_ID_PADDING_NOT_REQUIRED_BYTES =
            hexStringToByteArray(
                    "0000004F" + "40" + "00000C" // AVP Code | AVP Flags | AVP Length
                            + AVP_DUMMY_DATA_PADDING_NOT_REQUIRED);
    private static final byte[] EAP_MESSAGE_AVP_WITH_VENDOR_ID_PADDING_NOT_REQUIRED_BYTES =
            hexStringToByteArray(
                    "0000004F" + "C0" + "000010" // AVP Code | AVP Flags | AVP Length
                            + "00000064" // Vendor-ID
                            + AVP_DUMMY_DATA_PADDING_NOT_REQUIRED);
    private static final byte[] EAP_MESSAGE_AVP_INVALID_UNDERFLOW_BYTES =
            hexStringToByteArray(
                    "0000004F" + "40" + "00000F" // AVP Code | AVP Flags | AVP Length
                            + AVP_DUMMY_DATA_PADDING_REQUIRED
                            + "00"); // Padding
    private static final byte[] EAP_MESSAGE_AVP_INVALID_PADDING_BYTES =
            hexStringToByteArray(
                    "0000004F" + "40" + "00000B" // AVP Code | AVP Flags | AVP Length
                            + AVP_DUMMY_DATA_PADDING_REQUIRED);
    private static final byte[] EAP_MESSAGE_AVP_INVALID_LENGTH_BYTES =
            hexStringToByteArray(
                    "0000004F" + "40" + "000007" // AVP Code | AVP Flags | AVP Length
                            + AVP_DUMMY_DATA_PADDING_REQUIRED
                            + "00");
    private static final byte[] OTHER_AVP_MANDATORY_BIT_SET_BYTES =
            hexStringToByteArray(
                    "0000000F" + "40" + "00000B" // AVP Code | AVP Flags | AVP Length
                            + AVP_DUMMY_DATA_PADDING_REQUIRED
                            + "00"); // Padding

    private static final int AVP_LENGTH_WITH_VENDOR_ID_PADDING_NOT_REQUIRED = 16;
    private static final int AVP_LENGTH_WITH_VENDOR_ID_PADDING_REQUIRED = 15;
    private static final int AVP_LENGTH_WITHOUT_VENDOR_ID_PADDING_NOT_REQUIRED = 12;
    private static final int AVP_LENGTH_WITHOUT_VENDOR_ID_PADDING_REQUIRED = 11;

    // This is an unreastically large number in order to validate behaviour when the most
    // significant bit is set
    private static final byte[] AVP_LENGTH_BYTE_ARRAY = hexStringToByteArray("FFEEED");
    private static final ByteBuffer AVP_LENGTH_BUFFER = ByteBuffer.wrap(AVP_LENGTH_BYTE_ARRAY);
    private static final int AVP_LENGTH_BUFFER_HEX = 0xFFEEED;

    private final EapTtlsAvpDecoder mAvpDecoder = new EapTtlsAvpDecoder();

    @Test
    public void testEapTtlsAvp_success_withoutVendorId() throws Exception {
        EapTtlsAvp avp =
                new EapTtlsAvp(
                        ByteBuffer.wrap(EAP_MESSAGE_AVP_WITHOUT_VENDOR_ID_PADDING_REQUIRED_BYTES));

        assertEquals(EAP_MESSAGE_AVP_CODE, avp.avpCode);
        assertFalse(avp.isVendorIdPresent);
        assertTrue(avp.isMandatory);
        assertEquals(DEFAULT_VENDOR_ID, avp.vendorId);
        assertEquals(AVP_LENGTH_WITHOUT_VENDOR_ID_PADDING_REQUIRED, avp.avpLength);
        assertArrayEquals(AVP_DUMMY_DATA_PADDING_REQUIRED_BYTES, avp.data);
    }

    @Test
    public void testEapTtlsAvp_success_withVendorId() throws Exception {
        EapTtlsAvp avp =
                new EapTtlsAvp(
                        ByteBuffer.wrap(EAP_MESSAGE_AVP_WITH_VENDOR_ID_PADDING_REQUIRED_BYTES));

        assertEquals(EAP_MESSAGE_AVP_CODE, avp.avpCode);
        assertTrue(avp.isVendorIdPresent);
        assertTrue(avp.isMandatory);
        assertEquals(SAMPLE_VENDOR_ID, avp.vendorId);
        assertEquals(AVP_LENGTH_WITH_VENDOR_ID_PADDING_REQUIRED, avp.avpLength);
        assertArrayEquals(AVP_DUMMY_DATA_PADDING_REQUIRED_BYTES, avp.data);
    }

    @Test(expected = BufferUnderflowException.class)
    public void testEapTtlsAvp_failure_invalidUnderflow() throws Exception {
        EapTtlsAvp avp = new EapTtlsAvp(ByteBuffer.wrap(EAP_MESSAGE_AVP_INVALID_UNDERFLOW_BYTES));
    }

    @Test(expected = BufferUnderflowException.class)
    public void testEapTtlsAvp_failure_invalidPadding() throws Exception {
        EapTtlsAvp avp = new EapTtlsAvp(ByteBuffer.wrap(EAP_MESSAGE_AVP_INVALID_PADDING_BYTES));
    }

    @Test(expected = EapTtlsParsingException.class)
    public void testEapTtlsAvp_failure_invalidLength() throws Exception {
        EapTtlsAvp avp = new EapTtlsAvp(ByteBuffer.wrap(EAP_MESSAGE_AVP_INVALID_LENGTH_BYTES));
    }

    @Test
    public void testAvpDecoding_success_multipleAvps_withoutVendorId() throws Exception {
        AvpDecodeResult decodeResult =
                mAvpDecoder.decode(MULTIPLE_AVPS_EAP_MESSAGE_WITHOUT_VENDOR_ID_BYTES);
        assertTrue(decodeResult.isSuccessfulDecode());
        EapTtlsAvp avp = decodeResult.eapTtlsAvp;

        assertEquals(EAP_MESSAGE_AVP_CODE, avp.avpCode);
        assertFalse(avp.isVendorIdPresent);
        assertTrue(avp.isMandatory);
        assertEquals(DEFAULT_VENDOR_ID, avp.vendorId);
        assertEquals(AVP_LENGTH_WITHOUT_VENDOR_ID_PADDING_REQUIRED, avp.avpLength);
        assertArrayEquals(AVP_DUMMY_DATA_PADDING_REQUIRED_BYTES, avp.data);
    }

    @Test
    public void testAvpDecoding_success_multipleAvps_withVendorId() throws Exception {
        AvpDecodeResult decodeResult =
                mAvpDecoder.decode(MULTIPLE_AVPS_EAP_MESSAGE_WITH_VENDOR_ID_BYTES);
        assertTrue(decodeResult.isSuccessfulDecode());
        EapTtlsAvp avp = decodeResult.eapTtlsAvp;

        assertEquals(EAP_MESSAGE_AVP_CODE, avp.avpCode);
        assertTrue(avp.isVendorIdPresent);
        assertTrue(avp.isMandatory);
        assertEquals(SAMPLE_VENDOR_ID, avp.vendorId);
        assertEquals(AVP_LENGTH_WITH_VENDOR_ID_PADDING_REQUIRED, avp.avpLength);
        assertArrayEquals(AVP_DUMMY_DATA_PADDING_REQUIRED_BYTES, avp.data);
    }

    @Test
    public void testAvpDecoding_failure_multipleAvps_noEapMessage() throws Exception {
        verifyAvpDecodingFailure(mAvpDecoder.decode(MULTIPLE_AVPS_WITHOUT_EAP_MESSAGE_BYTES));
    }

    @Test
    public void testAvpDecoding_failure_multipleEapMessageAvps() throws Exception {
        verifyAvpDecodingFailure(mAvpDecoder.decode(MULTIPLE_EAP_MESSAGE_AVPS_BYTES));
    }

    @Test
    public void testAvpDecoding_failure_mandatoryUnsupportedAvp() throws Exception {
        verifyAvpDecodingFailure(mAvpDecoder.decode(OTHER_AVP_MANDATORY_BIT_SET_BYTES));
    }

    private void verifyAvpDecodingFailure(AvpDecodeResult decodeResult) {
        assertFalse(decodeResult.isSuccessfulDecode());
        EapError eapError = decodeResult.eapError;
        assertTrue(eapError.cause instanceof EapTtlsParsingException);
    }

    @Test
    public void testEapMessageAvpEncoding_withoutVendorId_paddingRequired() {
        verifyEapMessageAvpEncoding(
                DEFAULT_VENDOR_ID,
                AVP_DUMMY_DATA_PADDING_REQUIRED_BYTES,
                EAP_MESSAGE_AVP_WITHOUT_VENDOR_ID_PADDING_REQUIRED_BYTES);
    }

    @Test
    public void testEapMessageAvpEncoding_withoutVendorId_paddingNotRequired() {
        verifyEapMessageAvpEncoding(
                DEFAULT_VENDOR_ID,
                AVP_DUMMY_DATA_PADDING_NOT_REQUIRED_BYTES,
                EAP_MESSAGE_AVP_WITHOUT_VENDOR_ID_PADDING_NOT_REQUIRED_BYTES);
    }

    @Test
    public void testEapMessageAvpEncoding_withVendorId_paddingRequired() {
        verifyEapMessageAvpEncoding(
                SAMPLE_VENDOR_ID,
                AVP_DUMMY_DATA_PADDING_REQUIRED_BYTES,
                EAP_MESSAGE_AVP_WITH_VENDOR_ID_PADDING_REQUIRED_BYTES);
    }

    @Test
    public void testEapMessageAvpEncoding_withVendorId_paddingNotRequired() {
        verifyEapMessageAvpEncoding(
                SAMPLE_VENDOR_ID,
                AVP_DUMMY_DATA_PADDING_NOT_REQUIRED_BYTES,
                EAP_MESSAGE_AVP_WITH_VENDOR_ID_PADDING_NOT_REQUIRED_BYTES);
    }

    private void verifyEapMessageAvpEncoding(int vendorId, byte[] avpData, byte[] expectedResult) {
        EapTtlsAvp eapTtlsAvp = EapTtlsAvp.getEapMessageAvp(vendorId, avpData);

        assertArrayEquals(expectedResult, eapTtlsAvp.encode());
    }

    @Test
    public void testDecodeAndEncodeAvp() {
        AvpDecodeResult decodeResult =
                mAvpDecoder.decode(MULTIPLE_AVPS_EAP_MESSAGE_WITHOUT_VENDOR_ID_BYTES);
        assertTrue(decodeResult.isSuccessfulDecode());
        byte[] encodedAvp = decodeResult.eapTtlsAvp.encode();

        assertArrayEquals(EAP_MESSAGE_AVP_WITHOUT_VENDOR_ID_PADDING_REQUIRED_BYTES, encodedAvp);
    }

    @Test
    public void testGetAvpPadding() throws Exception {
        assertEquals(1, EapTtlsAvp.getAvpPadding(AVP_LENGTH_WITHOUT_VENDOR_ID_PADDING_REQUIRED));
    }

    @Test
    public void testGetAvpPadding_alreadyPadded() throws Exception {
        assertEquals(
                0, EapTtlsAvp.getAvpPadding(AVP_LENGTH_WITHOUT_VENDOR_ID_PADDING_NOT_REQUIRED));
    }

    @Test
    public void testGetAvpLengthFromBuffer_success() throws Exception {
        assertEquals(AVP_LENGTH_BUFFER_HEX, EapTtlsAvp.getAvpLength(AVP_LENGTH_BUFFER));
    }

    @Test
    public void testEncodeAvpLength_success() throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(AVP_LENGTH_WITH_VENDOR_ID_PADDING_REQUIRED);
        EapTtlsAvp.encodeAvpLength(buffer, AVP_LENGTH_BUFFER_HEX);

        assertArrayEquals(AVP_LENGTH_BYTE_ARRAY, TlsSession.getByteArrayFromBuffer(buffer));
    }
}
