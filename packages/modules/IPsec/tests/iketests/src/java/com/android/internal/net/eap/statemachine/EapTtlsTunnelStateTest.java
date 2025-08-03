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

import static android.net.eap.test.EapSessionConfig.EapMethodConfig.EAP_TYPE_MSCHAP_V2;

import static com.android.internal.net.eap.test.crypto.TlsSession.TLS_STATUS_CLOSED;
import static com.android.internal.net.eap.test.crypto.TlsSession.TLS_STATUS_FAILURE;
import static com.android.internal.net.eap.test.crypto.TlsSession.TLS_STATUS_SUCCESS;
import static com.android.internal.net.eap.test.message.EapMessage.EAP_CODE_FAILURE;
import static com.android.internal.net.eap.test.message.EapMessage.EAP_CODE_RESPONSE;
import static com.android.internal.net.eap.test.message.EapMessage.EAP_CODE_SUCCESS;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.EAP_DUMMY_REQUEST_BYTES;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.EAP_FAILURE_PACKET;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.EAP_MESSAGE_AVP_EAP_REQUEST;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.EAP_MESSAGE_AVP_EAP_RESPONSE;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.EAP_RESPONSE_TTLS_ACK;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.EAP_RESPONSE_TTLS_FINAL_FRAGMENT;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.EAP_RESPONSE_TTLS_INITIAL_FRAGMENT;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.EAP_RESPONSE_TTLS_WITH_LENGTH;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.EAP_SUCCESS_PACKET;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.EAP_TTLS_DUMMY_DATA_ASSEMBLED_FRAGMENT_BYTES;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.EAP_TTLS_DUMMY_DATA_BYTES;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.EAP_TTLS_DUMMY_DATA_FINAL_FRAGMENT_BYTES;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.EAP_TTLS_DUMMY_DATA_INITIAL_FRAGMENT_BYTES;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.EMSK;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.ID_INT;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.MSCHAP_V2_EMSK;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.MSCHAP_V2_MSK;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.MSK;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.internal.net.eap.test.EapResult;
import com.android.internal.net.eap.test.EapResult.EapError;
import com.android.internal.net.eap.test.EapResult.EapFailure;
import com.android.internal.net.eap.test.EapResult.EapResponse;
import com.android.internal.net.eap.test.EapResult.EapSuccess;
import com.android.internal.net.eap.test.exceptions.EapInvalidRequestException;
import com.android.internal.net.eap.test.exceptions.mschapv2.EapMsChapV2ParsingException;
import com.android.internal.net.eap.test.exceptions.ttls.EapTtlsParsingException;
import com.android.internal.net.eap.test.message.EapData;
import com.android.internal.net.eap.test.message.EapMessage;
import com.android.internal.net.eap.test.message.ttls.EapTtlsAvp;
import com.android.internal.net.eap.test.message.ttls.EapTtlsAvp.EapTtlsAvpDecoder;
import com.android.internal.net.eap.test.message.ttls.EapTtlsAvp.EapTtlsAvpDecoder.AvpDecodeResult;
import com.android.internal.net.eap.test.message.ttls.EapTtlsTypeData;
import com.android.internal.net.eap.test.message.ttls.EapTtlsTypeData.EapTtlsAcknowledgement;
import com.android.internal.net.eap.test.statemachine.EapMethodStateMachine.EapMethodState;
import com.android.internal.net.eap.test.statemachine.EapMethodStateMachine.FinalState;
import com.android.internal.net.eap.test.statemachine.EapTtlsMethodStateMachine.ErroredAndAwaitingClosureState;
import com.android.internal.net.eap.test.statemachine.EapTtlsMethodStateMachine.TunnelState;

import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;

import javax.net.ssl.SSLException;

public class EapTtlsTunnelStateTest extends EapTtlsStateTest {

    private static final EapTtlsAvp EAP_MESSAGE_AVP_WITH_EAP_REQUEST =
            EapTtlsAvp.getEapMessageAvp(0 /* default Vendor-ID */, EAP_DUMMY_REQUEST_BYTES);

    private EapTtlsAvpDecoder mMockAvpDecoder;
    private EapStateMachine mMockInnerEapStateMachine;

    private TunnelState mTunnelState;
    private EapResult mDummyEapResponse;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        mDummyEapResponse =
                EapResponse.getEapResponse(
                        new EapMessage(
                                EAP_CODE_RESPONSE,
                                ID_INT,
                                new EapData(EAP_TYPE_MSCHAP_V2, EMPTY_BYTE_ARRAY)));
        mMockAvpDecoder = mock(EapTtlsAvpDecoder.class);
        mMockInnerEapStateMachine = mock(EapStateMachine.class);

        mTunnelState = mStateMachine.new TunnelState();
        mTunnelState.mEapTtlsAvpDecoder = mMockAvpDecoder;
        mTunnelState.mInnerEapStateMachine = mMockInnerEapStateMachine;
        mStateMachine.mTlsSession = mMockTlsSession;

        mStateMachine.transitionTo(mTunnelState);
    }

    @Test
    @Override
    public void testHandleEapFailureNotification() throws Exception {
        EapMessage eapMessage = new EapMessage(EAP_CODE_FAILURE, ID_INT, null);

        when(mMockInnerEapStateMachine.process(eq(EAP_FAILURE_PACKET)))
                .thenReturn(new EapFailure());

        EapResult result = mStateMachine.process(eapMessage);
        assertTrue(mStateMachine.getState() instanceof FinalState);
        assertTrue(result instanceof EapFailure);
        verify(mMockInnerEapStateMachine).process(eq(EAP_FAILURE_PACKET));
        assertTrue(mStateMachine.getState() instanceof FinalState);
    }

    @Test
    @Override
    public void testHandleEapSuccessNotification() throws Exception {
        // TODO(b/161233250): Implement keying material generation in EAP-TTLS
        EapSuccess msChapV2Success = new EapSuccess(MSCHAP_V2_MSK, MSCHAP_V2_EMSK);
        EapMessage eapMessage = new EapMessage(EAP_CODE_SUCCESS, ID_INT, null);

        when(mMockInnerEapStateMachine.process(eq(EAP_SUCCESS_PACKET))).thenReturn(msChapV2Success);
        when(mMockTlsSession.generateKeyingMaterial())
                .thenReturn(mMockTlsSession.new EapTtlsKeyingMaterial(MSK, EMSK));

        EapResult result = mStateMachine.process(eapMessage);
        EapSuccess eapSuccess = (EapSuccess) result;
        assertArrayEquals(MSK, eapSuccess.msk);
        assertArrayEquals(EMSK, eapSuccess.emsk);
        assertTrue(mStateMachine.getState() instanceof FinalState);
        verify(mMockInnerEapStateMachine).process(eq(EAP_SUCCESS_PACKET));
        verify(mMockTlsSession).generateKeyingMaterial();
    }

    @Test
    public void testTunnel_prematureSuccess() throws Exception {
        EapMessage eapMessage = new EapMessage(EAP_CODE_SUCCESS, ID_INT, null);

        when(mMockInnerEapStateMachine.process(eq(EAP_SUCCESS_PACKET)))
                .thenReturn(new EapError(new EapInvalidRequestException("Error")));

        EapResult result = mStateMachine.process(eapMessage);
        EapError eapError = (EapError) result;
        assertTrue(eapError.cause instanceof EapInvalidRequestException);
        verify(mMockInnerEapStateMachine).process(eq(EAP_SUCCESS_PACKET));
    }

    @Test
    public void testTunnel_intermediateResponse() throws Exception {
        processMessageAndVerifyEapResponse(
                getEapTtlsTypeData(EAP_TTLS_DUMMY_DATA_BYTES),
                EAP_TTLS_DUMMY_DATA_BYTES,
                TLS_STATUS_SUCCESS,
                EAP_TTLS_DUMMY_DATA_BYTES,
                EAP_RESPONSE_TTLS_WITH_LENGTH,
                TunnelState.class);
    }

    @Test
    public void testTunnel_encryptClosed() throws Exception {
        processMessageAndVerifyEapResponse(
                getEapTtlsTypeData(EAP_TTLS_DUMMY_DATA_BYTES),
                EAP_TTLS_DUMMY_DATA_BYTES,
                TLS_STATUS_CLOSED,
                EAP_TTLS_DUMMY_DATA_BYTES,
                EAP_RESPONSE_TTLS_WITH_LENGTH,
                ErroredAndAwaitingClosureState.class);
    }

    @Test
    public void testTunnel_encryptFailure() throws Exception {
        mockTypeDataDecoding(getEapTtlsTypeData(EAP_TTLS_DUMMY_DATA_BYTES));
        mockInnerEapStateMachineProcessing(EAP_TTLS_DUMMY_DATA_BYTES, mDummyEapResponse);
        when(mMockTlsSession.processOutgoingData(eq(EAP_MESSAGE_AVP_EAP_RESPONSE)))
                .thenReturn(mMockTlsSession.new TlsResult(TLS_STATUS_FAILURE));

        processMessageAndVerifyEapError(SSLException.class);
        verify(mMockTypeDataDecoder).decodeEapTtlsRequestPacket(eq(DUMMY_EAP_TYPE_DATA));
        verify(mMockTlsSession).processIncomingData(eq(EAP_TTLS_DUMMY_DATA_BYTES));
        verify(mMockAvpDecoder).decode(eq(EAP_MESSAGE_AVP_EAP_REQUEST));
        verify(mMockInnerEapStateMachine).process(eq(EAP_DUMMY_REQUEST_BYTES));
        verify(mMockTlsSession).processOutgoingData(eq(EAP_MESSAGE_AVP_EAP_RESPONSE));
        assertTrue(mStateMachine.getState() instanceof FinalState);
    }

    @Test
    public void testTunnel_decryptClosed() throws Exception {
        mockTypeDataDecoding(getEapTtlsTypeData(EAP_TTLS_DUMMY_DATA_BYTES));
        when(mMockTlsSession.processIncomingData(eq(EAP_TTLS_DUMMY_DATA_BYTES)))
                .thenReturn(
                        mMockTlsSession
                        .new TlsResult(EAP_TTLS_DUMMY_DATA_BYTES, TLS_STATUS_CLOSED));

        processMessageAndVerifyEapResponse(EAP_RESPONSE_TTLS_WITH_LENGTH);
        verify(mMockTypeDataDecoder).decodeEapTtlsRequestPacket(eq(DUMMY_EAP_TYPE_DATA));
        verify(mMockTlsSession).processIncomingData(eq(EAP_TTLS_DUMMY_DATA_BYTES));
        assertTrue(mStateMachine.getState() instanceof ErroredAndAwaitingClosureState);
    }

    @Test
    public void testTunnel_decryptFailure() throws Exception {
        mockTypeDataDecoding(getEapTtlsTypeData(EAP_TTLS_DUMMY_DATA_BYTES));
        when(mMockTlsSession.processIncomingData(eq(EAP_TTLS_DUMMY_DATA_BYTES)))
                .thenReturn(mMockTlsSession.new TlsResult(TLS_STATUS_FAILURE));

        processMessageAndVerifyEapError(SSLException.class);
        verify(mMockTypeDataDecoder).decodeEapTtlsRequestPacket(eq(DUMMY_EAP_TYPE_DATA));
        verify(mMockTlsSession).processIncomingData(eq(EAP_TTLS_DUMMY_DATA_BYTES));
        assertTrue(mStateMachine.getState() instanceof FinalState);
    }

    @Test
    public void testTunnel_invalidAvp() throws Exception {
        when(mMockTlsSession.processIncomingData(eq(EAP_TTLS_DUMMY_DATA_BYTES)))
                .thenReturn(
                        mMockTlsSession
                        .new TlsResult(EAP_MESSAGE_AVP_EAP_REQUEST, TLS_STATUS_SUCCESS));
        when(mMockAvpDecoder.decode(eq(EAP_MESSAGE_AVP_EAP_REQUEST)))
                .thenReturn(
                        new AvpDecodeResult(
                                new EapError(new EapTtlsParsingException("Received bad AVP!"))));

        processMessageAndVerifyConnectionClosed(getEapTtlsTypeData(EAP_TTLS_DUMMY_DATA_BYTES));
        verify(mMockTlsSession).processIncomingData(eq(EAP_TTLS_DUMMY_DATA_BYTES));
        verify(mMockAvpDecoder).decode(eq(EAP_MESSAGE_AVP_EAP_REQUEST));
    }

    @Test
    public void testTunnel_innerMethodError() throws Exception {
        processMessageAndVerifyConnectionClosed(
                getEapTtlsTypeData(EAP_TTLS_DUMMY_DATA_BYTES),
                EAP_TTLS_DUMMY_DATA_BYTES,
                new EapError(new EapMsChapV2ParsingException("Random Inner Error")));
    }

    @Test
    public void testTunnel_inboundFragmentation_initialFragment() throws Exception {
        mockTypeDataDecoding(
                getEapTtlsFragmentTypeData(
                        true /* isFragmented */,
                        BUFFER_SIZE_ASSEMBLED_FRAGMENTS,
                        EAP_TTLS_DUMMY_DATA_INITIAL_FRAGMENT_BYTES));

        processMessageAndVerifyEapResponse(EAP_RESPONSE_TTLS_ACK);
        assertTrue(mInboundFragmentationHelper.isAwaitingFragments());
        verify(mMockTypeDataDecoder).decodeEapTtlsRequestPacket(eq(DUMMY_EAP_TYPE_DATA));
        assertTrue(mStateMachine.getState() instanceof TunnelState);
    }

    @Test
    public void testTunnel_inboundFragmentation_finalFragment() throws Exception {
        mInboundFragmentationHelper.mIsAwaitingFragments = true;
        mInboundFragmentationHelper.mFragmentedData =
                ByteBuffer.allocate(BUFFER_SIZE_ASSEMBLED_FRAGMENTS);
        mInboundFragmentationHelper.mFragmentedData.put(EAP_TTLS_DUMMY_DATA_INITIAL_FRAGMENT_BYTES);

        processMessageAndVerifyEapResponse(
                getEapTtlsTypeData(EAP_TTLS_DUMMY_DATA_FINAL_FRAGMENT_BYTES),
                EAP_TTLS_DUMMY_DATA_ASSEMBLED_FRAGMENT_BYTES,
                TLS_STATUS_SUCCESS,
                EAP_TTLS_DUMMY_DATA_BYTES,
                EAP_RESPONSE_TTLS_WITH_LENGTH,
                TunnelState.class);
        assertFalse(mStateMachine.mInboundFragmentationHelper.isAwaitingFragments());
    }

    @Test
    public void testTunnel_outboundFragmentation_initialFragment() throws Exception {
        processMessageAndVerifyEapResponse(
                getEapTtlsTypeData(EAP_TTLS_DUMMY_DATA_BYTES),
                EAP_TTLS_DUMMY_DATA_BYTES,
                TLS_STATUS_SUCCESS,
                EAP_TTLS_DUMMY_DATA_ASSEMBLED_FRAGMENT_BYTES,
                EAP_RESPONSE_TTLS_INITIAL_FRAGMENT,
                TunnelState.class);
        assertTrue(mStateMachine.mOutboundFragmentationHelper.hasRemainingFragments());
    }

    @Test
    public void testTunnel_outboundFragmentation_finalFragment() throws Exception {
        mOutboundFragmentationHelper.setupOutboundFragmentation(
                EAP_TTLS_DUMMY_DATA_ASSEMBLED_FRAGMENT_BYTES);
        mOutboundFragmentationHelper.getNextOutboundFragment();

        mockTypeDataDecoding(EapTtlsAcknowledgement.getEapTtlsAcknowledgement());

        processMessageAndVerifyEapResponse(EAP_RESPONSE_TTLS_FINAL_FRAGMENT);
        verify(mMockTypeDataDecoder).decodeEapTtlsRequestPacket(eq(DUMMY_EAP_TYPE_DATA));
        assertFalse(mStateMachine.mOutboundFragmentationHelper.hasRemainingFragments());
        assertTrue(mStateMachine.getState() instanceof TunnelState);
    }

    @Test
    public void testTunnel_outboundFragmentation_receivedNonAck() throws Exception {
        mOutboundFragmentationHelper.setupOutboundFragmentation(
                EAP_TTLS_DUMMY_DATA_ASSEMBLED_FRAGMENT_BYTES);
        mOutboundFragmentationHelper.getNextOutboundFragment();

        processMessageAndVerifyConnectionClosed(getEapTtlsTypeData(EAP_TTLS_DUMMY_DATA_BYTES));
    }

    @Test
    public void testTunnel_unexpectedAck() throws Exception {
        processMessageAndVerifyConnectionClosed(EapTtlsAcknowledgement.getEapTtlsAcknowledgement());
    }

    /**
     * Completes an entire run of all operations in the handshake state
     *
     * @param decodedTypeData the type data that is decoded by the type data decoder
     * @param assembledData the data passed into processIncomingData
     * @param encryptTlsStatus the status returned by processOutgoingData
     * @param encryptedOutgoingData the data returned by processOutgoingData
     * @param expectedResponse the expected EAP-Response
     */
    private void processMessageAndVerifyEapResponse(
            EapTtlsTypeData decodedTypeData,
            byte[] assembledData,
            int encryptResultStatus,
            byte[] encryptResultData,
            byte[] expectedResponse,
            Class<? extends EapMethodState> expectedState)
            throws Exception {
        mockTypeDataDecoding(decodedTypeData);
        mockInnerEapStateMachineProcessing(assembledData, mDummyEapResponse);
        when(mMockTlsSession.processOutgoingData(eq(EAP_MESSAGE_AVP_EAP_RESPONSE)))
                .thenReturn(mMockTlsSession.new TlsResult(encryptResultData, encryptResultStatus));

        processMessageAndVerifyEapResponse(expectedResponse);
        verify(mMockTypeDataDecoder).decodeEapTtlsRequestPacket(eq(DUMMY_EAP_TYPE_DATA));
        verify(mMockTlsSession).processIncomingData(eq(assembledData));
        verify(mMockAvpDecoder).decode(eq(EAP_MESSAGE_AVP_EAP_REQUEST));
        verify(mMockInnerEapStateMachine).process(eq(EAP_DUMMY_REQUEST_BYTES));
        verify(mMockTlsSession).processOutgoingData(eq(EAP_MESSAGE_AVP_EAP_RESPONSE));
        assertTrue(expectedState.isInstance(mStateMachine.getState()));
    }

    /**
     * Completes a run where the inner state machine returns a result that requires the TLS session
     * to be closed
     *
     * @param decodedTypeData the type data that is decoded by the type data decoder
     * @param assembledData the data passed into processIncomingData
     * @param innerResult the EAP result to return from the inner state machine
     */
    private void processMessageAndVerifyConnectionClosed(
            EapTtlsTypeData decodedTypeData, byte[] assembledData, EapResult innerResult)
            throws Exception {
        mockInnerEapStateMachineProcessing(assembledData, innerResult);

        processMessageAndVerifyConnectionClosed(decodedTypeData);
        verify(mMockTlsSession).processIncomingData(eq(assembledData));
        verify(mMockAvpDecoder).decode(eq(EAP_MESSAGE_AVP_EAP_REQUEST));
        verify(mMockInnerEapStateMachine).process(eq(EAP_DUMMY_REQUEST_BYTES));
    }

    /**
     * Mocks TLS Session operations including encrypt/decrypt as well as inner eap state processing
     *
     * @param assembledData the incoming data processed by TLS Session
     * @param innerResult the EAP result to return from the inner state machine
     */
    private void mockInnerEapStateMachineProcessing(byte[] assembledData, EapResult innerResult) {
        when(mMockTlsSession.processIncomingData(eq(assembledData)))
                .thenReturn(
                        mMockTlsSession
                        .new TlsResult(EAP_MESSAGE_AVP_EAP_REQUEST, TLS_STATUS_SUCCESS));
        when(mMockAvpDecoder.decode(eq(EAP_MESSAGE_AVP_EAP_REQUEST)))
                .thenReturn(new AvpDecodeResult(EAP_MESSAGE_AVP_WITH_EAP_REQUEST));
        when(mMockInnerEapStateMachine.process(eq(EAP_DUMMY_REQUEST_BYTES)))
                .thenReturn(innerResult);
    }
}
