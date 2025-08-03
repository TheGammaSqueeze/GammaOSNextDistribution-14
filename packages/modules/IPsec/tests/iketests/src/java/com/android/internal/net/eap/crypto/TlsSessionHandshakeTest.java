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

import static com.android.internal.net.TestUtils.hexStringToByteArray;
import static com.android.internal.net.eap.test.crypto.TlsSession.TLS_STATUS_CLOSED;
import static com.android.internal.net.eap.test.crypto.TlsSession.TLS_STATUS_FAILURE;
import static com.android.internal.net.eap.test.crypto.TlsSession.TLS_STATUS_SUCCESS;
import static com.android.internal.net.eap.test.crypto.TlsSession.TLS_STATUS_TUNNEL_ESTABLISHED;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.internal.net.eap.test.crypto.TlsSession.TlsResult;

import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;

import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;

public class TlsSessionHandshakeTest extends TlsSessionTest {

    private static final byte[] EAP_IDENTITY_AVP = hexStringToByteArray("0000004F4000000B16030400");
    private static final ByteBuffer APPLICATION_BUFFER_AVP_POSITION_RESET =
            (ByteBuffer)
                    ByteBuffer.allocate(APPLICATION_BUFFER_SIZE_TLS_MESSAGE)
                            .put(EAP_IDENTITY_AVP)
                            .flip();
    private static final ByteBuffer APPLICATION_BUFFER_AVP_CLEARED =
            (ByteBuffer)
                    ByteBuffer.allocate(APPLICATION_BUFFER_SIZE_TLS_MESSAGE)
                            .put(EAP_IDENTITY_AVP)
                            .clear();

    @Before
    @Override
    public void setUp() {
        super.setUp();
        mTlsSession.mHandshakeStatus = SSLEngineResult.HandshakeStatus.NEED_UNWRAP;
    }

    @Test
    public void testStartHandshake() throws Exception {
        setupWrap(EMPTY_APPLICATION_BUFFER, EMPTY_PACKET_BUFFER, RESULT_NEED_UNWRAP_OK);

        TlsResult result = mTlsSession.startHandshake();

        assertEquals(TLS_STATUS_SUCCESS, result.status);
        assertArrayEquals(SAMPLE_PACKET_TLS_MESSAGE, result.data);
        verify(mMockSslEngine).wrap(eq(EMPTY_APPLICATION_BUFFER), eq(PACKET_BUFFER_POSITION_LIMIT));
    }

    @Test
    public void testStartHandshake_fail() throws Exception {
        when(mMockSslEngine.wrap(eq(EMPTY_APPLICATION_BUFFER), eq(EMPTY_PACKET_BUFFER)))
                .thenThrow(SSLException.class);

        TlsResult result = mTlsSession.startHandshake();

        assertEquals(TLS_STATUS_FAILURE, result.status);
        assertArrayEquals(EMPTY_BYTE_ARRAY, result.data);
        verify(mMockSslEngine).wrap(eq(EMPTY_APPLICATION_BUFFER), eq(EMPTY_PACKET_BUFFER));
    }

    @Test
    public void testprocessHandshakeData_success() throws Exception {
        when(mMockSslEngine.unwrap(
                        eq(PACKET_BUFFER_POSITION_RESET),
                        eq(APPLICATION_BUFFER_AVP_POSITION_RESET)))
                .thenReturn(RESULT_NEED_WRAP_OK);
        setupWrap(
                APPLICATION_BUFFER_AVP_POSITION_RESET,
                PACKET_BUFFER_POSITION_RESET,
                RESULT_NEED_UNWRAP_OK);

        TlsResult result =
                mTlsSession.processHandshakeData(SAMPLE_PACKET_TLS_MESSAGE, EAP_IDENTITY_AVP);

        assertEquals(TLS_STATUS_SUCCESS, result.status);
        assertArrayEquals(SAMPLE_PACKET_TLS_MESSAGE, result.data);
        verify(mMockSslEngine)
                .unwrap(
                        eq(PACKET_BUFFER_POSITION_LIMIT),
                        eq(APPLICATION_BUFFER_AVP_POSITION_RESET));
        verify(mMockSslEngine)
                .wrap(eq(APPLICATION_BUFFER_AVP_POSITION_RESET), eq(PACKET_BUFFER_POSITION_LIMIT));
    }

    @Test
    public void testprocessHandshakeData_wrapFailure() throws Exception {
        when(mMockSslEngine.unwrap(
                        eq(PACKET_BUFFER_POSITION_RESET),
                        eq(APPLICATION_BUFFER_AVP_POSITION_RESET)))
                .thenReturn(RESULT_NEED_WRAP_OK);
        when(mMockSslEngine.wrap(
                        eq(APPLICATION_BUFFER_AVP_POSITION_RESET),
                        eq(PACKET_BUFFER_POSITION_RESET)))
                .thenThrow(SSLException.class);
        setupWrap(
                APPLICATION_BUFFER_AVP_CLEARED,
                PACKET_BUFFER_POSITION_RESET,
                RESULT_NOT_HANDSHAKING_CLOSED);
        when(mMockSslEngine.getHandshakeStatus())
                .thenReturn(SSLEngineResult.HandshakeStatus.NEED_WRAP);

        TlsResult result =
                mTlsSession.processHandshakeData(SAMPLE_PACKET_TLS_MESSAGE, EAP_IDENTITY_AVP);

        assertEquals(TLS_STATUS_CLOSED, result.status);
        assertArrayEquals(SAMPLE_PACKET_TLS_MESSAGE, result.data);
        verify(mMockSslEngine)
                .unwrap(eq(PACKET_BUFFER_POSITION_LIMIT), eq(APPLICATION_BUFFER_AVP_CLEARED));
        verify(mMockSslEngine, times(2))
                .wrap(eq(APPLICATION_BUFFER_AVP_CLEARED), eq(PACKET_BUFFER_POSITION_LIMIT));
    }

    @Test
    public void testprocessHandshakeData_unwrapFailure() throws Exception {
        when(mMockSslEngine.unwrap(
                        eq(PACKET_BUFFER_POSITION_RESET),
                        eq(APPLICATION_BUFFER_AVP_POSITION_RESET)))
                .thenThrow(SSLException.class);

        verifyCloseConnection();
    }

    @Test
    public void testprocessHandshakeData_underflow() throws Exception {
        when(mMockSslEngine.unwrap(
                        eq(PACKET_BUFFER_POSITION_RESET),
                        eq(APPLICATION_BUFFER_AVP_POSITION_RESET)))
                .thenReturn(RESULT_NEED_UNWRAP_UNDERFLOW);

        verifyCloseConnection();
    }

    @Test
    public void testprocessHandshakeData_overflow() throws Exception {
        when(mMockSslEngine.unwrap(
                        eq(PACKET_BUFFER_POSITION_RESET),
                        eq(APPLICATION_BUFFER_AVP_POSITION_RESET)))
                .thenReturn(RESULT_NEED_UNWRAP_OVERFLOW);

        verifyCloseConnection();
    }

    @Test
    public void testprocessHandshakeData_wrapClosed() throws Exception {
        setupWrap(
                APPLICATION_BUFFER_AVP_POSITION_RESET,
                PACKET_BUFFER_POSITION_RESET,
                RESULT_NOT_HANDSHAKING_CLOSED);
        when(mMockSslEngine.getHandshakeStatus())
                .thenReturn(SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING);

        mTlsSession.mHandshakeStatus = SSLEngineResult.HandshakeStatus.NEED_WRAP;
        TlsResult result =
                mTlsSession.processHandshakeData(SAMPLE_PACKET_TLS_MESSAGE, EAP_IDENTITY_AVP);

        assertEquals(TLS_STATUS_CLOSED, result.status);
        assertArrayEquals(EMPTY_BYTE_ARRAY, result.data);
        verify(mMockSslEngine)
                .wrap(eq(APPLICATION_BUFFER_AVP_POSITION_RESET), eq(PACKET_BUFFER_POSITION_LIMIT));
    }

    @Test
    public void testprocessHandshakeData_unwrapClosed() throws Exception {
        when(mMockSslEngine.unwrap(
                        eq(PACKET_BUFFER_POSITION_RESET),
                        eq(APPLICATION_BUFFER_AVP_POSITION_RESET)))
                .thenReturn(RESULT_NEED_WRAP_CLOSED);

        verifyCloseConnection();
    }

    @Test
    public void testprocessHandshakeData_wrapFinished() throws Exception {
        when(mMockSslEngine.unwrap(
                        eq(PACKET_BUFFER_POSITION_RESET),
                        eq(APPLICATION_BUFFER_AVP_POSITION_RESET)))
                .thenReturn(RESULT_NEED_UNWRAP_OK)
                .thenReturn(RESULT_NEED_WRAP_OK);
        setupWrap(
                APPLICATION_BUFFER_AVP_POSITION_RESET,
                PACKET_BUFFER_POSITION_RESET,
                RESULT_FINISHED_OK);

        TlsResult result =
                mTlsSession.processHandshakeData(SAMPLE_PACKET_TLS_MESSAGE, EAP_IDENTITY_AVP);

        assertEquals(TLS_STATUS_TUNNEL_ESTABLISHED, result.status);
        assertArrayEquals(SAMPLE_PACKET_TLS_MESSAGE, result.data);
        verify(mMockSslEngine, times(2))
                .unwrap(
                        eq(PACKET_BUFFER_POSITION_LIMIT),
                        eq(APPLICATION_BUFFER_AVP_POSITION_RESET));
        verify(mMockSslEngine)
                .wrap(eq(APPLICATION_BUFFER_AVP_POSITION_RESET), eq(PACKET_BUFFER_POSITION_LIMIT));
    }

    @Test
    public void testprocessHandshakeData_notHandshaking() throws Exception {
        mTlsSession.mHandshakeStatus = SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
        TlsResult result =
                mTlsSession.processHandshakeData(SAMPLE_PACKET_TLS_MESSAGE, EAP_IDENTITY_AVP);

        assertArrayEquals(EMPTY_BYTE_ARRAY, result.data);
        assertEquals(TLS_STATUS_FAILURE, result.status);
    }

    @Test
    public void testprocessHandshakeData_delegatedTasks() throws Exception {
        when(mMockSslEngine.getDelegatedTask()).thenReturn(mMockRunnable);
        when(mMockSslEngine.getHandshakeStatus())
                .thenReturn(SSLEngineResult.HandshakeStatus.NEED_WRAP);
        setupWrap(
                APPLICATION_BUFFER_AVP_POSITION_RESET,
                PACKET_BUFFER_POSITION_RESET,
                RESULT_NEED_UNWRAP_OK);

        mTlsSession.mHandshakeStatus = SSLEngineResult.HandshakeStatus.NEED_TASK;
        TlsResult result =
                mTlsSession.processHandshakeData(SAMPLE_PACKET_TLS_MESSAGE, EAP_IDENTITY_AVP);

        assertEquals(TLS_STATUS_SUCCESS, result.status);
        assertArrayEquals(SAMPLE_PACKET_TLS_MESSAGE, result.data);
        verify(mMockSslEngine)
                .wrap(eq(APPLICATION_BUFFER_AVP_POSITION_RESET), eq(PACKET_BUFFER_POSITION_LIMIT));
        verify(mMockRunnable).run();
    }

    /** Shared logic for any failure case that requires the connection to be closed */
    private void verifyCloseConnection() throws Exception {
        setupWrap(
                APPLICATION_BUFFER_AVP_CLEARED,
                PACKET_BUFFER_POSITION_RESET,
                RESULT_NOT_HANDSHAKING_CLOSED);
        when(mMockSslEngine.getHandshakeStatus())
                .thenReturn(SSLEngineResult.HandshakeStatus.NEED_WRAP);

        TlsResult result =
                mTlsSession.processHandshakeData(SAMPLE_PACKET_TLS_MESSAGE, EAP_IDENTITY_AVP);

        assertEquals(TLS_STATUS_CLOSED, result.status);
        assertArrayEquals(SAMPLE_PACKET_TLS_MESSAGE, result.data);
        verify(mMockSslEngine)
                .unwrap(eq(PACKET_BUFFER_POSITION_LIMIT), eq(APPLICATION_BUFFER_AVP_CLEARED));
        verify(mMockSslEngine)
                .wrap(eq(APPLICATION_BUFFER_AVP_CLEARED), eq(PACKET_BUFFER_POSITION_LIMIT));
    }
}
