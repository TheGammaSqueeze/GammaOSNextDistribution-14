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

import static android.net.eap.test.EapSessionConfig.EapMethodConfig.EAP_TYPE_TTLS;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static com.android.internal.net.TestUtils.hexStringToByteArray;
import static com.android.internal.net.eap.test.crypto.TlsSession.TLS_STATUS_CLOSED;
import static com.android.internal.net.eap.test.message.EapData.EAP_NOTIFICATION;
import static com.android.internal.net.eap.test.message.EapMessage.EAP_CODE_FAILURE;
import static com.android.internal.net.eap.test.message.EapMessage.EAP_CODE_REQUEST;
import static com.android.internal.net.eap.test.message.EapMessage.EAP_CODE_SUCCESS;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.EAP_RESPONSE_NOTIFICATION_PACKET;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.EAP_RESPONSE_TTLS_WITH_LENGTH;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.EAP_TTLS_DUMMY_DATA_BYTES;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.EAP_TTLS_DUMMY_DATA_FINAL_FRAGMENT_BYTES;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.EAP_TTLS_DUMMY_DATA_INITIAL_FRAGMENT_BYTES;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.ID_INT;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.MSCHAP_V2_PASSWORD;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.MSCHAP_V2_USERNAME;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.eap.test.EapSessionConfig;
import android.net.eap.test.EapSessionConfig.EapTtlsConfig;

import com.android.internal.net.eap.test.EapResult;
import com.android.internal.net.eap.test.EapResult.EapError;
import com.android.internal.net.eap.test.EapResult.EapFailure;
import com.android.internal.net.eap.test.EapResult.EapResponse;
import com.android.internal.net.eap.test.crypto.TlsSession;
import com.android.internal.net.eap.test.crypto.TlsSession.TlsResult;
import com.android.internal.net.eap.test.crypto.TlsSessionFactory;
import com.android.internal.net.eap.test.exceptions.EapInvalidRequestException;
import com.android.internal.net.eap.test.message.EapData;
import com.android.internal.net.eap.test.message.EapMessage;
import com.android.internal.net.eap.test.message.ttls.EapTtlsInboundFragmentationHelper;
import com.android.internal.net.eap.test.message.ttls.EapTtlsOutboundFragmentationHelper;
import com.android.internal.net.eap.test.message.ttls.EapTtlsTypeData;
import com.android.internal.net.eap.test.message.ttls.EapTtlsTypeData.EapTtlsTypeDataDecoder;
import com.android.internal.net.eap.test.message.ttls.EapTtlsTypeData.EapTtlsTypeDataDecoder.DecodeResult;
import com.android.internal.net.eap.test.statemachine.EapMethodStateMachine.EapMethodState;
import com.android.internal.net.eap.test.statemachine.EapMethodStateMachine.FinalState;
import com.android.internal.net.eap.test.statemachine.EapTtlsMethodStateMachine.ErroredAndAwaitingClosureState;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.security.SecureRandom;

public class EapTtlsStateTest {

    static final String NOTIFICATION_MESSAGE = "test";
    static final byte[] DUMMY_EAP_TYPE_DATA = hexStringToByteArray("112233445566");
    static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    static final int BUFFER_SIZE_FRAGMENT_ONE = EAP_TTLS_DUMMY_DATA_INITIAL_FRAGMENT_BYTES.length;
    static final int BUFFER_SIZE_FRAGMENT_TWO = EAP_TTLS_DUMMY_DATA_FINAL_FRAGMENT_BYTES.length;
    static final int BUFFER_SIZE_ASSEMBLED_FRAGMENTS =
            BUFFER_SIZE_FRAGMENT_ONE + BUFFER_SIZE_FRAGMENT_TWO;

    Context mContext;
    SecureRandom mMockSecureRandom;
    EapTtlsTypeDataDecoder mMockTypeDataDecoder;
    TlsSessionFactory mMockTlsSessionFactory;
    TlsSession mMockTlsSession;

    EapTtlsConfig mEapTtlsConfig;
    EapTtlsMethodStateMachine mStateMachine;
    EapTtlsInboundFragmentationHelper mInboundFragmentationHelper;
    EapTtlsOutboundFragmentationHelper mOutboundFragmentationHelper;

    @Before
    public void setUp() throws Exception {
        mContext = getInstrumentation().getContext();
        mMockSecureRandom = mock(SecureRandom.class);
        mMockTypeDataDecoder = mock(EapTtlsTypeDataDecoder.class);
        mMockTlsSessionFactory = mock(TlsSessionFactory.class);
        mMockTlsSession = mock(TlsSession.class);

        EapSessionConfig innerEapSessionConfig =
                new EapSessionConfig.Builder()
                        .setEapMsChapV2Config(MSCHAP_V2_USERNAME, MSCHAP_V2_PASSWORD)
                        .build();
        EapSessionConfig eapSessionConfig =
                new EapSessionConfig.Builder()
                        .setEapTtlsConfig(null /* trustedCa */, innerEapSessionConfig)
                        .build();
        mEapTtlsConfig = eapSessionConfig.getEapTtlsConfig();

        mInboundFragmentationHelper = new EapTtlsInboundFragmentationHelper();
        mOutboundFragmentationHelper =
                new EapTtlsOutboundFragmentationHelper(BUFFER_SIZE_FRAGMENT_ONE);

        mStateMachine =
                new EapTtlsMethodStateMachine(
                        mContext,
                        mEapTtlsConfig,
                        mMockSecureRandom,
                        mMockTypeDataDecoder,
                        mInboundFragmentationHelper,
                        mOutboundFragmentationHelper);
        EapTtlsMethodStateMachine.sTlsSessionFactory = mMockTlsSessionFactory;
        when(mMockTlsSessionFactory.newInstance(any(), any())).thenReturn(mMockTlsSession);
    }

    @AfterClass
    public static void teardown() {
        EapTtlsMethodStateMachine.sTlsSessionFactory = new TlsSessionFactory();
    }

    @Test
    public void testHandleEapFailureNotification() throws Exception {
        EapResult result = mStateMachine.process(new EapMessage(EAP_CODE_FAILURE, ID_INT, null));
        assertTrue(result instanceof EapFailure);
        assertTrue(mStateMachine.getState() instanceof FinalState);
    }

    @Test
    public void testHandleEapSuccessNotification() throws Exception {
        EapResult result = mStateMachine.process(new EapMessage(EAP_CODE_SUCCESS, ID_INT, null));
        EapError eapError = (EapError) result;
        assertTrue(eapError.cause instanceof EapInvalidRequestException);
    }

    @Test
    public void testHandleEapNotification() throws Exception {
        EapData eapData = new EapData(EAP_NOTIFICATION, NOTIFICATION_MESSAGE.getBytes());
        EapMessage eapMessage = new EapMessage(EAP_CODE_REQUEST, ID_INT, eapData);
        EapMethodState preNotification = (EapMethodState) mStateMachine.getState();

        EapResult result = mStateMachine.process(eapMessage);
        assertEquals(preNotification, mStateMachine.getState());

        EapResponse eapResponse = (EapResponse) result;
        assertArrayEquals(EAP_RESPONSE_NOTIFICATION_PACKET, eapResponse.packet);
    }

    EapTtlsTypeData getEapTtlsStartTypeData() throws Exception {
        return getEapTtlsTypeData(
                false /* isFragmented */, true /* isStart */, 0 /* length */, EMPTY_BYTE_ARRAY);
    }

    EapTtlsTypeData getEapTtlsFragmentTypeData(boolean isFragment, int length, byte[] data)
            throws Exception {
        return getEapTtlsTypeData(isFragment, false /* isStart */, length, data);
    }

    EapTtlsTypeData getEapTtlsTypeData(byte[] data) throws Exception {
        return getEapTtlsTypeData(
                false /* isFragmented */, false /* isStart */, 0 /* length */, data);
    }

    EapTtlsTypeData getEapTtlsTypeData(
            boolean isFragmented, boolean isStart, int length, byte[] data) throws Exception {
        return EapTtlsTypeData.getEapTtlsTypeData(
                isFragmented, isStart, 0 /* version */, length, data);
    }

    void mockTypeDataDecoding(EapTtlsTypeData decodedTypeData) throws Exception {
        when(mMockTypeDataDecoder.decodeEapTtlsRequestPacket(eq(DUMMY_EAP_TYPE_DATA)))
                .thenReturn(new DecodeResult(decodedTypeData));
    }

    /** Runs a test and verifies the EAP response returned by the state */
    void processMessageAndVerifyEapResponse(byte[] expectedResponse) throws Exception {
        EapData eapData = new EapData(EAP_TYPE_TTLS, DUMMY_EAP_TYPE_DATA);
        EapMessage eapMessage = new EapMessage(EAP_CODE_REQUEST, ID_INT, eapData);

        EapResult result = mStateMachine.process(eapMessage);
        EapResponse eapResponse = (EapResponse) result;
        assertArrayEquals(expectedResponse, eapResponse.packet);
    }

    /**
     * Completes a run of operations that requires CloseConnection to be called
     *
     * @param decodedTypeData the type data that is decoded by the type data decoder
     */
    void processMessageAndVerifyConnectionClosed(EapTtlsTypeData decodedTypeData) throws Exception {
        mockTypeDataDecoding(decodedTypeData);
        when(mMockTlsSession.closeConnection())
                .thenReturn(
                        mMockTlsSession
                        .new TlsResult(EAP_TTLS_DUMMY_DATA_BYTES, TLS_STATUS_CLOSED));

        processMessageAndVerifyEapResponse(EAP_RESPONSE_TTLS_WITH_LENGTH);
        verify(mMockTypeDataDecoder).decodeEapTtlsRequestPacket(eq(DUMMY_EAP_TYPE_DATA));
        verify(mMockTlsSession).closeConnection();
        assertTrue(mStateMachine.getState() instanceof ErroredAndAwaitingClosureState);
    }

    /**
     * Runs a test and verifies the EAP error returned by the state
     *
     * @param error the exception within the EapError
     */
    void processMessageAndVerifyEapError(Class<? extends Exception> error) throws Exception {
        EapData eapData = new EapData(EAP_TYPE_TTLS, DUMMY_EAP_TYPE_DATA);
        EapMessage eapMessage = new EapMessage(EAP_CODE_REQUEST, ID_INT, eapData);

        EapResult result = mStateMachine.process(eapMessage);
        EapError eapError = (EapError) result;
        assertTrue(error.isInstance(eapError.cause));
    }
}
