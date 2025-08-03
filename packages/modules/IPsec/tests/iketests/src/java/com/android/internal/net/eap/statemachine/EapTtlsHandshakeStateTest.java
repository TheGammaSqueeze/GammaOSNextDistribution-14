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

package com.android.internal.net.eap.test.statemachine;

import static com.android.internal.net.TestUtils.hexStringToByteArray;
import static com.android.internal.net.eap.test.crypto.TlsSession.TLS_STATUS_CLOSED;
import static com.android.internal.net.eap.test.crypto.TlsSession.TLS_STATUS_FAILURE;
import static com.android.internal.net.eap.test.crypto.TlsSession.TLS_STATUS_SUCCESS;
import static com.android.internal.net.eap.test.crypto.TlsSession.TLS_STATUS_TUNNEL_ESTABLISHED;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.EAP_RESPONSE_TTLS_ACK;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.EAP_RESPONSE_TTLS_FINAL_FRAGMENT;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.EAP_RESPONSE_TTLS_INITIAL_FRAGMENT;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.EAP_RESPONSE_TTLS_WITH_LENGTH;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.EAP_TTLS_DUMMY_DATA_ASSEMBLED_FRAGMENT_BYTES;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.EAP_TTLS_DUMMY_DATA_BYTES;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.EAP_TTLS_DUMMY_DATA_FINAL_FRAGMENT_BYTES;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.EAP_TTLS_DUMMY_DATA_INITIAL_FRAGMENT_BYTES;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.ID_INT;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.internal.net.eap.test.crypto.TlsSession.TlsResult;
import com.android.internal.net.eap.test.exceptions.ttls.EapTtlsHandshakeException;
import com.android.internal.net.eap.test.message.ttls.EapTtlsTypeData;
import com.android.internal.net.eap.test.message.ttls.EapTtlsTypeData.EapTtlsAcknowledgement;
import com.android.internal.net.eap.test.statemachine.EapMethodStateMachine.EapMethodState;
import com.android.internal.net.eap.test.statemachine.EapMethodStateMachine.FinalState;
import com.android.internal.net.eap.test.statemachine.EapTtlsMethodStateMachine.ErroredAndAwaitingClosureState;
import com.android.internal.net.eap.test.statemachine.EapTtlsMethodStateMachine.HandshakeState;
import com.android.internal.net.eap.test.statemachine.EapTtlsMethodStateMachine.TunnelState;

import org.junit.Before;
import org.junit.Test;

import java.security.GeneralSecurityException;

public class EapTtlsHandshakeStateTest extends EapTtlsStateTest {

    private static final byte[] DUMMY_EAP_IDENTITY_AVP =
            hexStringToByteArray(
                    "0000004F" + "40" + "00000D" // AVP Code | AVP Flags | AVP Length
                            + "0210000501" // EAP-Response/Identity
                            + "000000"); // Padding

    private HandshakeState mHandshakeState;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        mHandshakeState = mStateMachine.new HandshakeState();
        mStateMachine.mTlsSession = mMockTlsSession;
        mStateMachine.transitionTo(mHandshakeState);
    }

    @Test
    public void testBuildEapIdentityResponseAvp() throws Exception {
        assertArrayEquals(
                DUMMY_EAP_IDENTITY_AVP, mHandshakeState.buildEapIdentityResponseAvp(ID_INT));
    }

    @Test
    public void testStartHandshake_success() throws Exception {
        mStateMachine.mTlsSession = null;
        mockTypeDataDecoding(getEapTtlsStartTypeData());
        when(mMockTlsSessionFactory.newInstance(any(), any())).thenReturn(mMockTlsSession);
        when(mMockTlsSession.startHandshake())
                .thenReturn(
                        mMockTlsSession
                        .new TlsResult(EAP_TTLS_DUMMY_DATA_BYTES, TLS_STATUS_SUCCESS));

        processMessageAndVerifyEapResponse(EAP_RESPONSE_TTLS_WITH_LENGTH);
        verify(mMockTypeDataDecoder).decodeEapTtlsRequestPacket(eq(DUMMY_EAP_TYPE_DATA));
        assertTrue(mStateMachine.getState() instanceof HandshakeState);
    }

    @Test
    public void testStartHandshake_tlsSetUpFailure() throws Exception {
        mStateMachine.mTlsSession = null;
        when(mMockTlsSessionFactory.newInstance(any(), any()))
                .thenThrow(GeneralSecurityException.class);

        testHandshakeFailure_eapError(getEapTtlsStartTypeData(), EapTtlsHandshakeException.class);
    }

    @Test
    public void testStartHandshake_failure() throws Exception {
        mStateMachine.mTlsSession = null;
        when(mMockTlsSessionFactory.newInstance(any(), any())).thenReturn(mMockTlsSession);
        when(mMockTlsSession.startHandshake())
                .thenReturn(mMockTlsSession.new TlsResult(TLS_STATUS_FAILURE));

        testHandshakeFailure_eapError(getEapTtlsStartTypeData(), EapTtlsHandshakeException.class);
        assertTrue(mStateMachine.getState() instanceof FinalState);
    }

    @Test
    public void testSecondStartRequest() throws Exception {
        processMessageAndVerifyConnectionClosed(getEapTtlsStartTypeData());
    }

    @Test
    public void testHandshake_handshakeComplete() throws Exception {
        testHandshake(
                getEapTtlsTypeData(EAP_TTLS_DUMMY_DATA_BYTES),
                TLS_STATUS_TUNNEL_ESTABLISHED,
                EAP_TTLS_DUMMY_DATA_BYTES,
                EAP_RESPONSE_TTLS_WITH_LENGTH,
                TunnelState.class);
    }

    @Test
    public void testHandshake_intermediateResponse() throws Exception {
        testHandshake(
                getEapTtlsTypeData(EAP_TTLS_DUMMY_DATA_BYTES),
                TLS_STATUS_SUCCESS,
                EAP_TTLS_DUMMY_DATA_BYTES,
                EAP_RESPONSE_TTLS_WITH_LENGTH,
                HandshakeState.class);
    }

    @Test
    public void testHandshake_failure() throws Exception {
        when(mMockTlsSession.processHandshakeData(
                        eq(EAP_TTLS_DUMMY_DATA_BYTES), eq(DUMMY_EAP_IDENTITY_AVP)))
                .thenReturn(mMockTlsSession.new TlsResult(TLS_STATUS_FAILURE));

        testHandshakeFailure_eapError(
                getEapTtlsTypeData(EAP_TTLS_DUMMY_DATA_BYTES), EapTtlsHandshakeException.class);

        verify(mMockTlsSession)
                .processHandshakeData(eq(EAP_TTLS_DUMMY_DATA_BYTES), eq(DUMMY_EAP_IDENTITY_AVP));
        assertTrue(mStateMachine.getState() instanceof FinalState);
    }

    @Test
    public void testHandshake_closed() throws Exception {
        testHandshake(
                getEapTtlsTypeData(EAP_TTLS_DUMMY_DATA_BYTES),
                TLS_STATUS_CLOSED,
                EAP_TTLS_DUMMY_DATA_BYTES,
                EAP_RESPONSE_TTLS_WITH_LENGTH,
                ErroredAndAwaitingClosureState.class);
    }

    @Test
    public void testHandshake_inboundFragmentation_initialFragment() throws Exception {
        mockTypeDataDecoding(
                getEapTtlsFragmentTypeData(
                        true /* isFragmented */,
                        BUFFER_SIZE_ASSEMBLED_FRAGMENTS,
                        EAP_TTLS_DUMMY_DATA_INITIAL_FRAGMENT_BYTES));

        processMessageAndVerifyEapResponse(EAP_RESPONSE_TTLS_ACK);
        assertTrue(mInboundFragmentationHelper.isAwaitingFragments());
        verify(mMockTypeDataDecoder).decodeEapTtlsRequestPacket(eq(DUMMY_EAP_TYPE_DATA));
        assertTrue(mStateMachine.getState() instanceof HandshakeState);
    }

    @Test
    public void testHandshake_inboundFragmentation_finalFragment() throws Exception {
        mInboundFragmentationHelper.assembleInboundMessage(
                getEapTtlsFragmentTypeData(
                        true /* isFragmented */,
                        BUFFER_SIZE_ASSEMBLED_FRAGMENTS,
                        EAP_TTLS_DUMMY_DATA_INITIAL_FRAGMENT_BYTES));

        testHandshake(
                getEapTtlsTypeData(EAP_TTLS_DUMMY_DATA_FINAL_FRAGMENT_BYTES),
                EAP_TTLS_DUMMY_DATA_ASSEMBLED_FRAGMENT_BYTES,
                TLS_STATUS_SUCCESS,
                EAP_TTLS_DUMMY_DATA_BYTES,
                EAP_RESPONSE_TTLS_WITH_LENGTH,
                HandshakeState.class);
    }

    @Test
    public void testHandshake_inboundFragmentation_noLength() throws Exception {
        processMessageAndVerifyConnectionClosed(
                getEapTtlsFragmentTypeData(
                        true /* isFragmented */,
                        0 /* messageLength */,
                        EAP_TTLS_DUMMY_DATA_INITIAL_FRAGMENT_BYTES));
    }

    @Test
    public void testHandshake_inboundFragmentation_overflow() throws Exception {
        mInboundFragmentationHelper.assembleInboundMessage(
                getEapTtlsFragmentTypeData(
                        true /* isFragmented */,
                        BUFFER_SIZE_FRAGMENT_ONE,
                        EAP_TTLS_DUMMY_DATA_INITIAL_FRAGMENT_BYTES));

        processMessageAndVerifyConnectionClosed(
                getEapTtlsFragmentTypeData(
                        true /* isFragmented */,
                        0 /* messageLength */,
                        EAP_TTLS_DUMMY_DATA_FINAL_FRAGMENT_BYTES));
    }

    @Test
    public void testHandshake_inboundFragmentation_lengthBitSet() throws Exception {
        mInboundFragmentationHelper.assembleInboundMessage(
                getEapTtlsFragmentTypeData(
                        true /* isFragmented */,
                        BUFFER_SIZE_ASSEMBLED_FRAGMENTS,
                        EAP_TTLS_DUMMY_DATA_INITIAL_FRAGMENT_BYTES));

        processMessageAndVerifyConnectionClosed(
                getEapTtlsFragmentTypeData(
                        true /* isFragmented */,
                        BUFFER_SIZE_ASSEMBLED_FRAGMENTS,
                        EAP_TTLS_DUMMY_DATA_FINAL_FRAGMENT_BYTES));
    }

    @Test
    public void testHandshake_outboundFragmentation_initialFragment() throws Exception {
        testHandshake(
                getEapTtlsTypeData(EAP_TTLS_DUMMY_DATA_BYTES),
                TLS_STATUS_SUCCESS,
                EAP_TTLS_DUMMY_DATA_ASSEMBLED_FRAGMENT_BYTES,
                EAP_RESPONSE_TTLS_INITIAL_FRAGMENT,
                HandshakeState.class);

        verify(mMockTypeDataDecoder).decodeEapTtlsRequestPacket(eq(DUMMY_EAP_TYPE_DATA));
    }

    @Test
    public void testHandshake_outboundFragmentation_finalFragment() throws Exception {
        mOutboundFragmentationHelper.setupOutboundFragmentation(
                EAP_TTLS_DUMMY_DATA_ASSEMBLED_FRAGMENT_BYTES);
        mOutboundFragmentationHelper.getNextOutboundFragment();

        mockTypeDataDecoding(getEapTtlsTypeData(EMPTY_BYTE_ARRAY));

        processMessageAndVerifyEapResponse(EAP_RESPONSE_TTLS_FINAL_FRAGMENT);
        verify(mMockTypeDataDecoder).decodeEapTtlsRequestPacket(eq(DUMMY_EAP_TYPE_DATA));
        assertTrue(mStateMachine.getState() instanceof HandshakeState);
    }

    @Test
    public void testHandshake_outboundFragmentation_receivedNonAck() throws Exception {
        mOutboundFragmentationHelper.setupOutboundFragmentation(
                EAP_TTLS_DUMMY_DATA_ASSEMBLED_FRAGMENT_BYTES);
        mOutboundFragmentationHelper.getNextOutboundFragment();

        processMessageAndVerifyConnectionClosed(getEapTtlsTypeData(EAP_TTLS_DUMMY_DATA_BYTES));
    }

    @Test
    public void testHandshake_unexpectedAck() throws Exception {
        processMessageAndVerifyConnectionClosed(EapTtlsAcknowledgement.getEapTtlsAcknowledgement());
    }

    /**
     * Completes an entire run of all operations in the handshake state
     *
     * <p>Note that in this case, the decoded data is used in processIncomingData. In other words,
     * this method does not test for fragmentation.
     *
     * @param decodedTypeData the type data that is decoded by the type data decoder
     * @param tlsStatus the status returned by processHandshakeData
     * @param tlsResultData the data returned by processHandshakeData
     * @param expectedResponse the expected EAP-Response
     * @param expectedState the expected method state following a response
     */
    private void testHandshake(
            EapTtlsTypeData decodedTypeData,
            int tlsStatus,
            byte[] tlsResultData,
            byte[] expectedResponse,
            Class<? extends EapMethodState> expectedState)
            throws Exception {
        testHandshake(
                decodedTypeData,
                decodedTypeData.data,
                tlsStatus,
                tlsResultData,
                expectedResponse,
                expectedState);
    }

    /**
     * Completes an entire run of all operations in the handshake state
     *
     * @param decodedTypeData the type data that is decoded by the type data decoder
     * @param assembledData the data passed into processHandshakeData
     * @param tlsStatus the status returned by processHandshakeData
     * @param tlsResultData the data returned by processHandshakeData
     * @param expectedResponse the expected EAP-Response
     * @param expectedState the expected method state following a response
     */
    private void testHandshake(
            EapTtlsTypeData decodedTypeData,
            byte[] assembledData,
            int tlsStatus,
            byte[] tlsResultData,
            byte[] expectedResponse,
            Class<? extends EapMethodState> expectedState)
            throws Exception {
        mockTypeDataDecoding(decodedTypeData);
        when(mMockTlsSession.processHandshakeData(eq(assembledData), eq(DUMMY_EAP_IDENTITY_AVP)))
                .thenReturn(mMockTlsSession.new TlsResult(tlsResultData, tlsStatus));

        processMessageAndVerifyEapResponse(expectedResponse);
        verify(mMockTypeDataDecoder).decodeEapTtlsRequestPacket(eq(DUMMY_EAP_TYPE_DATA));
        verify(mMockTlsSession).processHandshakeData(eq(assembledData), eq(DUMMY_EAP_IDENTITY_AVP));
        assertTrue(expectedState.isInstance(mStateMachine.getState()));
    }

    /**
     * Completes a run of operations in the handshake state that results in an EapError
     *
     * @param decodedTypeData the type data that is decoded by the type data decoder
     * @param expectedError the expected error within the EapError
     */
    private void testHandshakeFailure_eapError(
            EapTtlsTypeData decodedTypeData, Class<? extends Exception> expectedError)
            throws Exception {
        mockTypeDataDecoding(decodedTypeData);

        processMessageAndVerifyEapError(expectedError);
        verify(mMockTypeDataDecoder).decodeEapTtlsRequestPacket(eq(DUMMY_EAP_TYPE_DATA));
    }
}
