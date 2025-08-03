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

import static com.android.internal.net.eap.test.crypto.TlsSession.TLS_STATUS_SUCCESS;
import static com.android.internal.net.eap.test.message.EapMessage.EAP_CODE_REQUEST;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.EAP_TTLS_DUMMY_DATA_BYTES;
import static com.android.internal.net.eap.test.message.EapTestMessageDefinitions.ID_INT;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.internal.net.eap.test.exceptions.EapInvalidRequestException;
import com.android.internal.net.eap.test.message.EapData;
import com.android.internal.net.eap.test.message.EapMessage;
import com.android.internal.net.eap.test.message.ttls.EapTtlsTypeData.EapTtlsAcknowledgement;
import com.android.internal.net.eap.test.statemachine.EapTtlsMethodStateMachine.CreatedState;
import com.android.internal.net.eap.test.statemachine.EapTtlsMethodStateMachine.HandshakeState;

import org.junit.Test;

public class EapTtlsCreatedStateTest extends EapTtlsStateTest {

    @Test
    public void testStartRequest() throws Exception {
        EapData eapData = new EapData(EAP_TYPE_TTLS, DUMMY_EAP_TYPE_DATA);
        EapMessage eapMessage = new EapMessage(EAP_CODE_REQUEST, ID_INT, eapData);

        mockTypeDataDecoding(getEapTtlsStartTypeData());
        when(mMockTlsSessionFactory.newInstance(any(), any())).thenReturn(mMockTlsSession);
        when(mMockTlsSession.startHandshake())
                .thenReturn(
                        mMockTlsSession
                        .new TlsResult(EAP_TTLS_DUMMY_DATA_BYTES, TLS_STATUS_SUCCESS));

        mStateMachine.process(eapMessage);
        verify(mMockTypeDataDecoder, times(2)).decodeEapTtlsRequestPacket(eq(DUMMY_EAP_TYPE_DATA));
        assertTrue(mStateMachine.getState() instanceof HandshakeState);
    }

    @Test
    public void testUnexpectedRequest() throws Exception {
        mockTypeDataDecoding(EapTtlsAcknowledgement.getEapTtlsAcknowledgement());

        processMessageAndVerifyEapError(EapInvalidRequestException.class);
        verify(mMockTypeDataDecoder).decodeEapTtlsRequestPacket(eq(DUMMY_EAP_TYPE_DATA));
        assertTrue(mStateMachine.getState() instanceof CreatedState);
    }
}
