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

package com.android.internal.net.eap.test.crypto;

import static com.android.internal.net.eap.test.crypto.TlsSession.TLS_STATUS_CLOSED;
import static com.android.internal.net.eap.test.crypto.TlsSession.TLS_STATUS_SUCCESS;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.internal.net.eap.test.crypto.TlsSession.TlsResult;

import org.junit.Test;

import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;

public class TlsSessionTunnelTest extends TlsSessionTest {

    @Test
    public void testEncryptMessage_success() throws Exception {
        setupWrap(
                APPLICATION_BUFFER_POSITION_RESET, EMPTY_PACKET_BUFFER, RESULT_NOT_HANDSHAKING_OK);

        TlsResult result = mTlsSession.processOutgoingData(SAMPLE_APPLICATION_TLS_MESSAGE);

        assertEquals(TLS_STATUS_SUCCESS, result.status);
        assertArrayEquals(SAMPLE_PACKET_TLS_MESSAGE, result.data);
        verify(mMockSslEngine)
                .wrap(eq(APPLICATION_BUFFER_POSITION_RESET), eq(PACKET_BUFFER_POSITION_LIMIT));
    }

    @Test
    public void testEncryptMessage_failure() throws Exception {
        setupChainedWrap_exception(
                APPLICATION_BUFFER_POSITION_RESET,
                EMPTY_PACKET_BUFFER,
                RESULT_NOT_HANDSHAKING_CLOSED);
        when(mMockSslEngine.getHandshakeStatus())
                .thenReturn(SSLEngineResult.HandshakeStatus.NEED_WRAP);

        TlsResult result = mTlsSession.processOutgoingData(SAMPLE_APPLICATION_TLS_MESSAGE);

        assertEquals(TLS_STATUS_CLOSED, result.status);
        assertArrayEquals(SAMPLE_PACKET_TLS_MESSAGE, result.data);
        verify(mMockSslEngine, times(2))
                .wrap(eq(APPLICATION_BUFFER_POSITION_RESET), eq(PACKET_BUFFER_POSITION_LIMIT));
    }

    @Test
    public void testEncryptMessage_overflow() throws Exception {
        setupChainedWrap(
                APPLICATION_BUFFER_POSITION_RESET,
                EMPTY_PACKET_BUFFER,
                RESULT_NOT_HANDSHAKING_OVERFLOW,
                RESULT_NOT_HANDSHAKING_CLOSED);
        when(mMockSslEngine.getHandshakeStatus())
                .thenReturn(SSLEngineResult.HandshakeStatus.NEED_WRAP);

        TlsResult result = mTlsSession.processOutgoingData(SAMPLE_APPLICATION_TLS_MESSAGE);

        assertEquals(TLS_STATUS_CLOSED, result.status);
        assertArrayEquals(SAMPLE_PACKET_TLS_MESSAGE, result.data);
        verify(mMockSslEngine, times(2))
                .wrap(eq(APPLICATION_BUFFER_POSITION_RESET), eq(PACKET_BUFFER_POSITION_LIMIT));
    }

    @Test
    public void testEncryptMessage_underflow() throws Exception {
        setupChainedWrap(
                APPLICATION_BUFFER_POSITION_RESET,
                EMPTY_PACKET_BUFFER,
                RESULT_NOT_HANDSHAKING_UNDERFLOW,
                RESULT_NOT_HANDSHAKING_CLOSED);
        when(mMockSslEngine.getHandshakeStatus())
                .thenReturn(SSLEngineResult.HandshakeStatus.NEED_WRAP);

        TlsResult result = mTlsSession.processOutgoingData(SAMPLE_APPLICATION_TLS_MESSAGE);

        assertEquals(TLS_STATUS_CLOSED, result.status);
        assertArrayEquals(SAMPLE_PACKET_TLS_MESSAGE, result.data);
        verify(mMockSslEngine, times(2))
                .wrap(eq(APPLICATION_BUFFER_POSITION_RESET), eq(PACKET_BUFFER_POSITION_LIMIT));
    }

    @Test
    public void testEncryptMessage_closed() throws Exception {
        when(mMockSslEngine.wrap(eq(APPLICATION_BUFFER_POSITION_RESET), eq(EMPTY_PACKET_BUFFER)))
                .thenReturn(RESULT_NOT_HANDSHAKING_CLOSED);
        when(mMockSslEngine.getHandshakeStatus())
                .thenReturn(SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING);

        TlsResult result = mTlsSession.processOutgoingData(SAMPLE_APPLICATION_TLS_MESSAGE);

        assertEquals(TLS_STATUS_CLOSED, result.status);
        assertArrayEquals(EMPTY_BYTE_ARRAY, result.data);
    }

    @Test
    public void testDecryptMessage_success() throws Exception {
        setupUnwrap(
                EMPTY_APPLICATION_BUFFER, PACKET_BUFFER_POSITION_RESET, RESULT_NOT_HANDSHAKING_OK);

        TlsResult result = mTlsSession.processIncomingData(SAMPLE_PACKET_TLS_MESSAGE);

        assertEquals(TLS_STATUS_SUCCESS, result.status);
        assertArrayEquals(SAMPLE_APPLICATION_TLS_MESSAGE, result.data);
        verify(mMockSslEngine)
                .unwrap(eq(PACKET_BUFFER_POSITION_RESET), eq(APPLICATION_BUFFER_POSITION_LIMIT));
    }

    @Test
    public void testDecryptMessage_failure() throws Exception {
        when(mMockSslEngine.unwrap(eq(PACKET_BUFFER_POSITION_RESET), eq(EMPTY_APPLICATION_BUFFER)))
                .thenThrow(SSLException.class);
        setupWrap(
                EMPTY_APPLICATION_BUFFER,
                PACKET_BUFFER_POSITION_RESET,
                RESULT_NOT_HANDSHAKING_CLOSED);
        when(mMockSslEngine.getHandshakeStatus())
                .thenReturn(SSLEngineResult.HandshakeStatus.NEED_WRAP);

        TlsResult result = mTlsSession.processIncomingData(SAMPLE_PACKET_TLS_MESSAGE);

        assertEquals(TLS_STATUS_CLOSED, result.status);
        assertArrayEquals(SAMPLE_PACKET_TLS_MESSAGE, result.data);
        verify(mMockSslEngine)
                .unwrap(eq(PACKET_BUFFER_POSITION_LIMIT), eq(EMPTY_APPLICATION_BUFFER));
        verify(mMockSslEngine).wrap(eq(EMPTY_APPLICATION_BUFFER), eq(PACKET_BUFFER_POSITION_LIMIT));
    }

    @Test
    public void testDecryptMessage_overflow() throws Exception {
        when(mMockSslEngine.unwrap(eq(PACKET_BUFFER_POSITION_RESET), eq(EMPTY_APPLICATION_BUFFER)))
                .thenReturn(RESULT_NOT_HANDSHAKING_OVERFLOW);
        setupWrap(
                EMPTY_APPLICATION_BUFFER,
                PACKET_BUFFER_POSITION_RESET,
                RESULT_NOT_HANDSHAKING_CLOSED);
        when(mMockSslEngine.getHandshakeStatus())
                .thenReturn(SSLEngineResult.HandshakeStatus.NEED_WRAP);

        TlsResult result = mTlsSession.processIncomingData(SAMPLE_PACKET_TLS_MESSAGE);

        assertEquals(TLS_STATUS_CLOSED, result.status);
        assertArrayEquals(SAMPLE_PACKET_TLS_MESSAGE, result.data);
        verify(mMockSslEngine)
                .unwrap(eq(PACKET_BUFFER_POSITION_LIMIT), eq(EMPTY_APPLICATION_BUFFER));
        verify(mMockSslEngine).wrap(eq(EMPTY_APPLICATION_BUFFER), eq(PACKET_BUFFER_POSITION_LIMIT));
    }

    @Test
    public void testDecryptMessage_underflow() throws Exception {
        when(mMockSslEngine.unwrap(eq(PACKET_BUFFER_POSITION_RESET), eq(EMPTY_APPLICATION_BUFFER)))
                .thenReturn(RESULT_NOT_HANDSHAKING_UNDERFLOW);
        setupWrap(
                EMPTY_APPLICATION_BUFFER,
                PACKET_BUFFER_POSITION_RESET,
                RESULT_NOT_HANDSHAKING_CLOSED);
        when(mMockSslEngine.getHandshakeStatus())
                .thenReturn(SSLEngineResult.HandshakeStatus.NEED_WRAP);

        TlsResult result = mTlsSession.processIncomingData(SAMPLE_PACKET_TLS_MESSAGE);

        assertEquals(TLS_STATUS_CLOSED, result.status);
        assertArrayEquals(SAMPLE_PACKET_TLS_MESSAGE, result.data);
    }

    @Test
    public void testDecryptMessage_closed() throws Exception {
        when(mMockSslEngine.unwrap(eq(PACKET_BUFFER_POSITION_RESET), eq(EMPTY_APPLICATION_BUFFER)))
                .thenReturn(RESULT_NOT_HANDSHAKING_CLOSED);
        setupWrap(
                EMPTY_APPLICATION_BUFFER,
                PACKET_BUFFER_POSITION_RESET,
                RESULT_NOT_HANDSHAKING_CLOSED);
        when(mMockSslEngine.getHandshakeStatus())
                .thenReturn(SSLEngineResult.HandshakeStatus.NEED_WRAP);

        TlsResult result = mTlsSession.processIncomingData(SAMPLE_PACKET_TLS_MESSAGE);

        assertEquals(TLS_STATUS_CLOSED, result.status);
        assertArrayEquals(SAMPLE_PACKET_TLS_MESSAGE, result.data);
    }
}
