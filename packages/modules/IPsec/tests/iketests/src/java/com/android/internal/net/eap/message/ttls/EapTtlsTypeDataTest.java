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
import com.android.internal.net.eap.test.exceptions.ttls.EapTtlsParsingException;
import com.android.internal.net.eap.test.message.ttls.EapTtlsTypeData.EapTtlsAcknowledgement;
import com.android.internal.net.eap.test.message.ttls.EapTtlsTypeData.EapTtlsTypeDataDecoder;
import com.android.internal.net.eap.test.message.ttls.EapTtlsTypeData.EapTtlsTypeDataDecoder.DecodeResult;

import org.junit.Test;

public class EapTtlsTypeDataTest {
    private static final String TAG = EapTtlsTypeDataTest.class.getSimpleName();

    private static final String EAP_TTLS_REQUEST_DATA =
            "16030300310200002d03037071727374757677787"
                    + "97a7b7c7d7e7f808182838485868788898a8b8c8d8e8f00c013000005ff01000100";
    private static final byte[] EAP_TTLS_REQUEST_DATA_BYTES =
            hexStringToByteArray(EAP_TTLS_REQUEST_DATA);

    private static final byte[] EAP_TTLS_REQUEST =
            hexStringToByteArray("00" + EAP_TTLS_REQUEST_DATA);
    private static final byte[] EAP_TTLS_REQUEST_START_SET =
            hexStringToByteArray("20" + EAP_TTLS_REQUEST_DATA);
    private static final byte[] EAP_TTLS_REQUEST_NO_FRAG_LENGTH_SET =
            hexStringToByteArray("80" + "00000000" + EAP_TTLS_REQUEST_DATA);
    private static final byte[] EAP_TTLS_REQUEST_START = hexStringToByteArray("20");
    // to test an expected start request without a start bit set
    private static final byte[] EAP_TTLS_REQUEST_START_NOT_SET = hexStringToByteArray("00");
    private static final byte[] EAP_TTLS_RESPONSE_ACK = hexStringToByteArray("00");

    private final EapTtlsTypeDataDecoder mTypeDataDecoder = new EapTtlsTypeDataDecoder();

    @Test
    public void testDecodeResult_successfulDecode() throws Exception {
        DecodeResult result =
                new DecodeResult(
                        EapTtlsTypeData.getEapTtlsTypeData(
                                false /* isDataFragmented */,
                                false /* isStart */,
                                0 /* version */,
                                0 /* isLengthIncluded */,
                                EAP_TTLS_REQUEST_DATA_BYTES));

        assertTrue(result.isSuccessfulDecode());
    }

    @Test
    public void testDecodeResult_unsuccessfulDecode() throws Exception {
        DecodeResult result = new DecodeResult(new EapError(new Exception()));

        assertFalse(result.isSuccessfulDecode());
    }

    @Test
    public void testDecodeEapTtlsRequest() {
        DecodeResult decodeResult = mTypeDataDecoder.decodeEapTtlsRequestPacket(EAP_TTLS_REQUEST);
        assertTrue(decodeResult.isSuccessfulDecode());
        EapTtlsTypeData typeData = decodeResult.eapTypeData;

        assertFalse(typeData.isLengthIncluded);
        assertFalse(typeData.isDataFragmented);
        assertFalse(typeData.isStart);
        assertEquals(0, typeData.version);
        assertEquals(0, typeData.messageLength);
        assertArrayEquals(EAP_TTLS_REQUEST_DATA_BYTES, typeData.data);
    }

    @Test
    public void testDecodeEapTtlsRequest_incorrectMessageLength() {
        DecodeResult decodeResult =
                mTypeDataDecoder.decodeEapTtlsRequestPacket(EAP_TTLS_REQUEST_NO_FRAG_LENGTH_SET);

        assertFalse(decodeResult.isSuccessfulDecode());
        EapError eapError = decodeResult.eapError;
        assertTrue(eapError.cause instanceof EapTtlsParsingException);
    }

    @Test
    public void testDecodeEapTtlsStart() {
        DecodeResult decodeResult =
                mTypeDataDecoder.decodeEapTtlsRequestPacket(EAP_TTLS_REQUEST_START);
        assertTrue(decodeResult.isSuccessfulDecode());
        EapTtlsTypeData typeData = decodeResult.eapTypeData;

        assertFalse(typeData.isLengthIncluded);
        assertFalse(typeData.isDataFragmented);
        assertTrue(typeData.isStart);
        assertEquals(0, typeData.version);
        assertEquals(0, typeData.messageLength);
        assertArrayEquals(new byte[0], typeData.data);
    }

    @Test
    public void testEncodeEapTtlsTypeData() {
        EapTtlsTypeData typeData =
                EapTtlsTypeData.getEapTtlsTypeData(
                        false /* isDataFragmented */,
                        false /* isStart */,
                        0 /* version */,
                        0 /* isLengthIncluded */,
                        EAP_TTLS_REQUEST_DATA_BYTES);
        byte[] encodeResult = typeData.encode();

        assertArrayEquals(EAP_TTLS_REQUEST, encodeResult);
    }

    @Test
    public void testEapTtlsAcknowledgementResponse() {
        EapTtlsAcknowledgement eapTtlsAcknowledgement =
                EapTtlsAcknowledgement.getEapTtlsAcknowledgement();
        byte[] encodeResult = eapTtlsAcknowledgement.encode();

        assertArrayEquals(EAP_TTLS_RESPONSE_ACK, encodeResult);
    }

    @Test
    public void testEapTtlsIsAcknowledgment_valid() {
        EapTtlsAcknowledgement eapTtlsAcknowledgement =
                EapTtlsAcknowledgement.getEapTtlsAcknowledgement();

        assertTrue(eapTtlsAcknowledgement.isAcknowledgmentPacket());
        assertArrayEquals(EAP_TTLS_RESPONSE_ACK, eapTtlsAcknowledgement.encode());
    }

    @Test
    public void testEapTtlsIsAcknowledgment_invalidWithData() {
        EapTtlsTypeData eapTtlsTypeData =
                EapTtlsTypeData.getEapTtlsTypeData(
                        false /* packetFragmented */,
                        false /* start */,
                        0 /* version */,
                        0 /* messageLength */,
                        EAP_TTLS_REQUEST);

        assertFalse(eapTtlsTypeData.isAcknowledgmentPacket());
    }

    @Test
    public void testEapTtlsIsAcknowledgment_invalidWithoutData() {
        EapTtlsTypeData eapTtlsTypeData =
                EapTtlsTypeData.getEapTtlsTypeData(
                        false /* packetFragmented */,
                        true /* start */,
                        0 /* version */,
                        0 /* messageLength */,
                        new byte[0]);

        assertFalse(eapTtlsTypeData.isAcknowledgmentPacket());
    }
}
