/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.internal.net.ipsec.test.ike.message;

import static android.net.ipsec.test.ike.exceptions.IkeProtocolException.ERROR_TYPE_AUTHENTICATION_FAILED;
import static android.net.ipsec.test.ike.exceptions.IkeProtocolException.ERROR_TYPE_CHILD_SA_NOT_FOUND;
import static android.net.ipsec.test.ike.exceptions.IkeProtocolException.ERROR_TYPE_FAILED_CP_REQUIRED;
import static android.net.ipsec.test.ike.exceptions.IkeProtocolException.ERROR_TYPE_INTERNAL_ADDRESS_FAILURE;
import static android.net.ipsec.test.ike.exceptions.IkeProtocolException.ERROR_TYPE_INVALID_IKE_SPI;
import static android.net.ipsec.test.ike.exceptions.IkeProtocolException.ERROR_TYPE_INVALID_KE_PAYLOAD;
import static android.net.ipsec.test.ike.exceptions.IkeProtocolException.ERROR_TYPE_INVALID_SELECTORS;
import static android.net.ipsec.test.ike.exceptions.IkeProtocolException.ERROR_TYPE_INVALID_SYNTAX;
import static android.net.ipsec.test.ike.exceptions.IkeProtocolException.ERROR_TYPE_NO_ADDITIONAL_SAS;
import static android.net.ipsec.test.ike.exceptions.IkeProtocolException.ERROR_TYPE_NO_PROPOSAL_CHOSEN;
import static android.net.ipsec.test.ike.exceptions.IkeProtocolException.ERROR_TYPE_SINGLE_PAIR_REQUIRED;
import static android.net.ipsec.test.ike.exceptions.IkeProtocolException.ERROR_TYPE_TEMPORARY_FAILURE;
import static android.net.ipsec.test.ike.exceptions.IkeProtocolException.ERROR_TYPE_TS_UNACCEPTABLE;
import static android.net.ipsec.test.ike.exceptions.IkeProtocolException.ERROR_TYPE_UNSUPPORTED_CRITICAL_PAYLOAD;

import static com.android.internal.net.ipsec.test.ike.message.IkeNotifyPayload.NOTIFY_TYPE_COOKIE;
import static com.android.internal.net.ipsec.test.ike.message.IkeNotifyPayload.NOTIFY_TYPE_COOKIE2;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.net.ipsec.test.ike.SaProposal;
import android.net.ipsec.test.ike.exceptions.AuthenticationFailedException;
import android.net.ipsec.test.ike.exceptions.ChildSaNotFoundException;
import android.net.ipsec.test.ike.exceptions.FailedCpRequiredException;
import android.net.ipsec.test.ike.exceptions.IkeProtocolException;
import android.net.ipsec.test.ike.exceptions.InternalAddressFailureException;
import android.net.ipsec.test.ike.exceptions.InvalidIkeSpiException;
import android.net.ipsec.test.ike.exceptions.InvalidKeException;
import android.net.ipsec.test.ike.exceptions.InvalidSelectorsException;
import android.net.ipsec.test.ike.exceptions.InvalidSyntaxException;
import android.net.ipsec.test.ike.exceptions.NoAdditionalSasException;
import android.net.ipsec.test.ike.exceptions.NoValidProposalChosenException;
import android.net.ipsec.test.ike.exceptions.SinglePairRequiredException;
import android.net.ipsec.test.ike.exceptions.TemporaryFailureException;
import android.net.ipsec.test.ike.exceptions.TsUnacceptableException;
import android.net.ipsec.test.ike.exceptions.UnrecognizedIkeProtocolException;

import com.android.internal.net.TestUtils;

import org.junit.Test;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Random;

public final class IkeNotifyPayloadTest {
    private static final String NOTIFY_NAT_DETECTION_PAYLOAD_HEX_STRING =
            "2900001c00004004e54f73b7d83f6beb881eab2051d8663f421d10b0";
    private static final String NOTIFY_NAT_DETECTION_PAYLOAD_BODY_HEX_STRING =
            "00004004e54f73b7d83f6beb881eab2051d8663f421d10b0";
    private static final String NAT_DETECTION_DATA_HEX_STRING =
            "e54f73b7d83f6beb881eab2051d8663f421d10b0";
    private static final String PACKET_INFO_HEX_STRING =
            "4500009cafcd4000403208adc0a80064c0a800012ad4c0a200000001";

    private static final int COOKIE_INVALID_DATA_LEN_SMALL = 0;
    private static final int COOKIE_INVALID_DATA_LEN_LARGE = 65;
    private static final int COOKIE_DATA_LEN = 64;

    private static final int COOKIE2_INVALID_DATA_LEN_SMALL = 7;
    private static final int COOKIE2_INVALID_DATA_LEN_LARGE = 65;
    private static final int COOKIE2_DATA_LEN = 64;

    private static final String NOTIFY_REKEY_PAYLOAD_BODY_HEX_STRING = "030440092ad4c0a2";
    private static final int CHILD_SPI = 0x2ad4c0a2;

    private static final String IKE_INITIATOR_SPI_HEX_STRING = "5f54bf6d8b48e6e1";
    private static final String IKE_RESPODNER_SPI_HEX_STRING = "0000000000000000";
    private static final String IP_ADDR = "10.80.80.13";
    private static final int PORT = 500;

    private static final int PROTOCOL_ID_OFFSET = 0;

    @Test
    public void testDecodeNotifyPayloadSpiUnset() throws Exception {
        byte[] inputPacket =
                TestUtils.hexStringToByteArray(NOTIFY_NAT_DETECTION_PAYLOAD_BODY_HEX_STRING);
        byte[] notifyData = TestUtils.hexStringToByteArray(NAT_DETECTION_DATA_HEX_STRING);

        IkeNotifyPayload payload = new IkeNotifyPayload(false, inputPacket);
        assertEquals(IkePayload.PROTOCOL_ID_UNSET, payload.protocolId);
        assertEquals(IkePayload.SPI_LEN_NOT_INCLUDED, payload.spiSize);
        assertEquals(IkeNotifyPayload.NOTIFY_TYPE_NAT_DETECTION_SOURCE_IP, payload.notifyType);
        assertEquals(IkePayload.SPI_NOT_INCLUDED, payload.spi);
        assertArrayEquals(notifyData, payload.notifyData);
    }

    @Test
    public void testDecodeNotifyPayloadSpiSet() throws Exception {
        byte[] inputPacket = TestUtils.hexStringToByteArray(NOTIFY_REKEY_PAYLOAD_BODY_HEX_STRING);

        IkeNotifyPayload payload = new IkeNotifyPayload(false, inputPacket);
        assertEquals(IkePayload.PROTOCOL_ID_ESP, payload.protocolId);
        assertEquals(IkePayload.SPI_LEN_IPSEC, payload.spiSize);
        assertEquals(IkeNotifyPayload.NOTIFY_TYPE_REKEY_SA, payload.notifyType);
        assertEquals(CHILD_SPI, payload.spi);
        assertArrayEquals(new byte[0], payload.notifyData);
    }

    @Test
    public void testGenerateNatDetectionData() throws Exception {
        long initiatorIkeSpi = Long.parseLong(IKE_INITIATOR_SPI_HEX_STRING, 16);
        long responderIkespi = Long.parseLong(IKE_RESPODNER_SPI_HEX_STRING, 16);
        InetAddress inetAddress = InetAddress.getByName(IP_ADDR);

        byte[] netDetectionData =
                IkeNotifyPayload.generateNatDetectionData(
                        initiatorIkeSpi, responderIkespi, inetAddress, PORT);

        byte[] expectedBytes = TestUtils.hexStringToByteArray(NAT_DETECTION_DATA_HEX_STRING);
        assertArrayEquals(expectedBytes, netDetectionData);
    }

    private void verifyHandleCookieAndGenerateCopy(boolean isCookie2, int dataLen)
            throws Exception {
        final byte[] cookieData = new byte[dataLen];
        new Random().nextBytes(cookieData);
        int cookieType = isCookie2 ? NOTIFY_TYPE_COOKIE2 : NOTIFY_TYPE_COOKIE;
        IkeNotifyPayload inboundCookieNotify = new IkeNotifyPayload(cookieType, cookieData);

        IkeNotifyPayload outboundCookieNotify =
                isCookie2
                        ? IkeNotifyPayload.handleCookie2AndGenerateCopy(inboundCookieNotify)
                        : IkeNotifyPayload.handleCookieAndGenerateCopy(inboundCookieNotify);

        assertArrayEquals(cookieData, outboundCookieNotify.notifyData);
        assertEquals(cookieType, outboundCookieNotify.notifyType);
    }

    @Test
    public void testHandleCookieAndGenerateCopy() throws Exception {
        verifyHandleCookieAndGenerateCopy(false /* isCookie2 */, COOKIE_DATA_LEN);
    }

    @Test(expected = InvalidSyntaxException.class)
    public void testHandleCookieWithTooSmallLengthOfData() throws Exception {
        verifyHandleCookieAndGenerateCopy(false /* isCookie2 */, COOKIE_INVALID_DATA_LEN_SMALL);
    }

    @Test(expected = InvalidSyntaxException.class)
    public void testHandleCookieWithTooLargeLengthOfData() throws Exception {
        verifyHandleCookieAndGenerateCopy(false /* isCookie2 */, COOKIE_INVALID_DATA_LEN_SMALL);
    }

    @Test
    public void testHandleCookie2AndGenerateCopy() throws Exception {
        verifyHandleCookieAndGenerateCopy(true /* isCookie2 */, COOKIE2_DATA_LEN);
    }

    @Test(expected = InvalidSyntaxException.class)
    public void testHandleCookie2WithTooSmallLengthOfData() throws Exception {
        verifyHandleCookieAndGenerateCopy(true /* isCookie2 */, COOKIE2_INVALID_DATA_LEN_SMALL);
    }

    @Test(expected = InvalidSyntaxException.class)
    public void testHandleCookie2WithTooLargeLengthOfData() throws Exception {
        verifyHandleCookieAndGenerateCopy(true /* isCookie2 */, COOKIE2_INVALID_DATA_LEN_SMALL);
    }

    @Test
    public void testBuildIkeErrorNotifyWithData() throws Exception {
        int payloadType = 1;
        IkeNotifyPayload notifyPayload =
                new IkeNotifyPayload(
                        IkeProtocolException.ERROR_TYPE_UNSUPPORTED_CRITICAL_PAYLOAD,
                        new byte[] {(byte) payloadType});

        assertArrayEquals(new byte[] {(byte) payloadType}, notifyPayload.notifyData);
        assertTrue(notifyPayload.isErrorNotify());
        assertFalse(notifyPayload.isNewChildSaNotify());
    }

    @Test
    public void testBuildIkeErrorNotifyWithoutData() throws Exception {
        IkeNotifyPayload notifyPayload =
                new IkeNotifyPayload(IkeProtocolException.ERROR_TYPE_INVALID_SYNTAX);

        assertArrayEquals(new byte[0], notifyPayload.notifyData);
        assertTrue(notifyPayload.isErrorNotify());
        assertFalse(notifyPayload.isNewChildSaNotify());
    }

    @Test
    public void testBuildChildConfigNotify() throws Exception {
        IkeNotifyPayload notifyPayload =
                new IkeNotifyPayload(IkeNotifyPayload.NOTIFY_TYPE_USE_TRANSPORT_MODE);

        assertArrayEquals(new byte[0], notifyPayload.notifyData);
        assertFalse(notifyPayload.isErrorNotify());
        assertTrue(notifyPayload.isNewChildSaNotify());
    }

    @Test
    public void testBuildChildErrorNotify() throws Exception {
        IkeNotifyPayload notifyPayload =
                new IkeNotifyPayload(IkeProtocolException.ERROR_TYPE_INTERNAL_ADDRESS_FAILURE);

        assertArrayEquals(new byte[0], notifyPayload.notifyData);
        assertTrue(notifyPayload.isErrorNotify());
        assertTrue(notifyPayload.isNewChildSaNotify());
    }

    @Test
    public void testEncodeNotifyPayload() throws Exception {
        byte[] inputPacket =
                TestUtils.hexStringToByteArray(NOTIFY_NAT_DETECTION_PAYLOAD_BODY_HEX_STRING);
        IkeNotifyPayload payload = new IkeNotifyPayload(false, inputPacket);

        ByteBuffer byteBuffer = ByteBuffer.allocate(payload.getPayloadLength());
        payload.encodeToByteBuffer(IkePayload.PAYLOAD_TYPE_NOTIFY, byteBuffer);

        byte[] expectedNoncePayload =
                TestUtils.hexStringToByteArray(NOTIFY_NAT_DETECTION_PAYLOAD_HEX_STRING);
        assertArrayEquals(expectedNoncePayload, byteBuffer.array());
    }

    @Test
    public void testValidateAndBuildIkeExceptionWithData() throws Exception {
        // Invalid Message ID
        byte[] dhGroup = new byte[] {(byte) 0x00, (byte) 0x0e};
        int expectedDhGroup = SaProposal.DH_GROUP_2048_BIT_MODP;

        IkeNotifyPayload payload = new IkeNotifyPayload(ERROR_TYPE_INVALID_KE_PAYLOAD, dhGroup);
        IkeProtocolException exception = payload.validateAndBuildIkeException();

        assertTrue(exception instanceof InvalidKeException);
        assertEquals(ERROR_TYPE_INVALID_KE_PAYLOAD, exception.getErrorType());
        assertArrayEquals(dhGroup, exception.getErrorData());
        assertEquals(expectedDhGroup, ((InvalidKeException) exception).getDhGroup());
    }

    @Test
    public void testValidateAndBuildInvalidSelectorsException() throws Exception {
        byte[] packetInfoBytes = TestUtils.hexStringToByteArray(PACKET_INFO_HEX_STRING);

        IkeNotifyPayload errNotify =
                new IkeNotifyPayload(
                        IkePayload.PROTOCOL_ID_ESP,
                        CHILD_SPI,
                        ERROR_TYPE_INVALID_SELECTORS,
                        packetInfoBytes);

        InvalidSelectorsException exception =
                (InvalidSelectorsException) errNotify.validateAndBuildIkeException();

        assertEquals(ERROR_TYPE_INVALID_SELECTORS, exception.getErrorType());
        assertEquals(CHILD_SPI, exception.getIpSecSpi());
        assertArrayEquals(packetInfoBytes, exception.getIpSecPacketInfo());
    }

    private <T extends IkeProtocolException> void verifyValidateAndBuildIkeExceptionWithoutData(
            int errorType, Class<T> exceptionClass) throws Exception {
        IkeNotifyPayload payload = new IkeNotifyPayload(errorType);
        verifyIkeExceptionWithoutData(
                payload.validateAndBuildIkeException(), errorType, exceptionClass);
    }

    private <T extends IkeProtocolException> void verifyIkeExceptionWithoutData(
            IkeProtocolException exception, int errorType, Class<T> exceptionClass)
            throws Exception {
        assertTrue(exceptionClass.isInstance(exception));
        assertEquals(errorType, exception.getErrorType());
        assertArrayEquals(new byte[0], exception.getErrorData());
    }

    @Test
    public void testValidateAndBuildAuthFailException() throws Exception {
        verifyValidateAndBuildIkeExceptionWithoutData(
                ERROR_TYPE_AUTHENTICATION_FAILED, AuthenticationFailedException.class);
    }

    @Test
    public void testValidateAndBuildInvalidIkeSpiException() throws Exception {
        verifyValidateAndBuildIkeExceptionWithoutData(
                ERROR_TYPE_INVALID_IKE_SPI, InvalidIkeSpiException.class);
    }

    @Test
    public void testValidateAndBuildInvalidSyntaxException() throws Exception {
        verifyValidateAndBuildIkeExceptionWithoutData(
                ERROR_TYPE_INVALID_SYNTAX, InvalidSyntaxException.class);
    }

    @Test
    public void testValidateAndBuildNoProposalChosenException() throws Exception {
        verifyValidateAndBuildIkeExceptionWithoutData(
                ERROR_TYPE_NO_PROPOSAL_CHOSEN, NoValidProposalChosenException.class);
    }

    @Test
    public void testValidateAndBuildSinglePairRequiredException() throws Exception {
        verifyValidateAndBuildIkeExceptionWithoutData(
                ERROR_TYPE_SINGLE_PAIR_REQUIRED, SinglePairRequiredException.class);
    }

    @Test
    public void testValidateAndBuildNoAdditionalSasException() throws Exception {
        verifyValidateAndBuildIkeExceptionWithoutData(
                ERROR_TYPE_NO_ADDITIONAL_SAS, NoAdditionalSasException.class);
    }

    @Test
    public void testValidateAndBuildInternalAddressFailException() throws Exception {
        verifyValidateAndBuildIkeExceptionWithoutData(
                ERROR_TYPE_INTERNAL_ADDRESS_FAILURE, InternalAddressFailureException.class);
    }

    @Test
    public void testValidateAndBuildTsUnacceptableException() throws Exception {
        verifyValidateAndBuildIkeExceptionWithoutData(
                ERROR_TYPE_TS_UNACCEPTABLE, TsUnacceptableException.class);
    }

    @Test
    public void testValidateAndBuildTemporaryFailureException() throws Exception {
        verifyValidateAndBuildIkeExceptionWithoutData(
                ERROR_TYPE_TEMPORARY_FAILURE, TemporaryFailureException.class);
    }

    @Test
    public void testValidateAndBuildFailedCpRequiredException() throws Exception {
        verifyValidateAndBuildIkeExceptionWithoutData(
                ERROR_TYPE_FAILED_CP_REQUIRED, FailedCpRequiredException.class);
    }

    @Test
    public void testValidateAndBuildChildSaNotFoundException() throws Exception {
        IkeNotifyPayload errNotify =
                new IkeNotifyPayload(
                        IkePayload.PROTOCOL_ID_ESP,
                        CHILD_SPI,
                        ERROR_TYPE_CHILD_SA_NOT_FOUND,
                        new byte[0]);
        ChildSaNotFoundException exception =
                (ChildSaNotFoundException) errNotify.validateAndBuildIkeException();

        verifyIkeExceptionWithoutData(
                exception, ERROR_TYPE_CHILD_SA_NOT_FOUND, ChildSaNotFoundException.class);

        assertEquals(CHILD_SPI, exception.getIpSecSpi());
    }

    @Test
    public void testValidateAndBuildUnrecognizedIkeException() throws Exception {
        int unrecognizedType = 0;
        IkeNotifyPayload payload = new IkeNotifyPayload(unrecognizedType);
        IkeProtocolException exception = payload.validateAndBuildIkeException();

        assertTrue(exception instanceof UnrecognizedIkeProtocolException);
        assertEquals(unrecognizedType, exception.getErrorType());
        assertArrayEquals(new byte[0], exception.getErrorData());
    }

    @Test
    public void testValidateAndBuildIkeExceptionWithInvalidPayload() throws Exception {
        // Build a invalid notify payload
        IkeNotifyPayload payload = new IkeNotifyPayload(ERROR_TYPE_UNSUPPORTED_CRITICAL_PAYLOAD);

        try {
            payload.validateAndBuildIkeException();
            fail("Expected to fail due to invalid error data");
        } catch (InvalidSyntaxException expected) {
        }
    }

    @Test
    public void testBuildIkeExceptionWithStatusNotify() throws Exception {
        // Rekey notification
        byte[] inputPacket = TestUtils.hexStringToByteArray(NOTIFY_REKEY_PAYLOAD_BODY_HEX_STRING);
        IkeNotifyPayload payload = new IkeNotifyPayload(false, inputPacket);

        assertFalse(payload.isErrorNotify());

        try {
            payload.validateAndBuildIkeException();
            fail("Expected to fail because it is not error notification");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testGetNotifyTypeString() throws Exception {
        IkeNotifyPayload payload = new IkeNotifyPayload(ERROR_TYPE_AUTHENTICATION_FAILED);

        assertEquals("Notify(Authentication failed)", payload.getTypeString());
    }

    @Test
    public void testGetNotifyTypeStringForUnrecoginizedNotify() throws Exception {
        int unrecognizedType = 0;
        IkeNotifyPayload payload = new IkeNotifyPayload(unrecognizedType);

        assertEquals("Notify(0)", payload.getTypeString());
    }
}
