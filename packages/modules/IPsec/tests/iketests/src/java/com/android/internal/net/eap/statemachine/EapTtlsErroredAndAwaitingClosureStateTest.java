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

import static com.android.internal.net.eap.test.crypto.TlsSession.TLS_STATUS_CLOSED;
import static com.android.internal.net.eap.test.crypto.TlsSession.TLS_STATUS_FAILURE;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.EAP_RESPONSE_TTLS_WITH_LENGTH;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.EAP_TTLS_DUMMY_DATA_BYTES;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.ID_INT;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.internal.net.eap.test.EapResult.EapError;
import com.android.internal.net.eap.test.EapResult.EapResponse;
import com.android.internal.net.eap.test.exceptions.ttls.EapTtlsParsingException;
import com.android.internal.net.eap.test.statemachine.EapTtlsMethodStateMachine.ErroredAndAwaitingClosureState;

import org.junit.Before;
import org.junit.Test;

public class EapTtlsErroredAndAwaitingClosureStateTest extends EapTtlsStateTest {
    private static final String TAG = EapTtlsHandshakeStateTest.class.getSimpleName();

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        mStateMachine.mTlsSession = mMockTlsSession;
    }

    @Test
    public void testTransitionToClosureState_closureResponse() throws Exception {
        when(mMockTlsSession.closeConnection())
                .thenReturn(
                        mMockTlsSession
                        .new TlsResult(EAP_TTLS_DUMMY_DATA_BYTES, TLS_STATUS_CLOSED));

        EapResponse eapResponse =
                (EapResponse)
                        mStateMachine.transitionToErroredAndAwaitingClosureState(
                                TAG,
                                ID_INT,
                                new EapError(new EapTtlsParsingException("Sample Exception")));
        assertArrayEquals(EAP_RESPONSE_TTLS_WITH_LENGTH, eapResponse.packet);
        assertTrue(mStateMachine.getState() instanceof ErroredAndAwaitingClosureState);
        verify(mMockTlsSession).closeConnection();
    }

    @Test
    public void testTransitionToClosureState_eapError() throws Exception {
        when(mMockTlsSession.closeConnection())
                .thenReturn(mMockTlsSession.new TlsResult(TLS_STATUS_FAILURE));

        EapError eapError =
                (EapError)
                        mStateMachine.transitionToErroredAndAwaitingClosureState(
                                TAG,
                                ID_INT,
                                new EapError(new EapTtlsParsingException("Sample Exception")));
        assertTrue(eapError.cause instanceof EapTtlsParsingException);
        verify(mMockTlsSession).closeConnection();
    }

    @Test
    public void testClosureState_closureNotifyRequest() throws Exception {
        mStateMachine.transitionTo(
                mStateMachine
                .new ErroredAndAwaitingClosureState(
                        new EapError(new EapTtlsParsingException("Sample Exception"))));
        mockTypeDataDecoding(getEapTtlsTypeData(EAP_TTLS_DUMMY_DATA_BYTES));

        processMessageAndVerifyEapError(EapTtlsParsingException.class);
    }
}
