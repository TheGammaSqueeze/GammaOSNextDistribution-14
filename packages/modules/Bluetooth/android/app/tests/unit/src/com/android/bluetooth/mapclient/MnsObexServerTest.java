/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.bluetooth.mapclient;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.obex.HeaderSet;
import com.android.obex.Operation;
import com.android.obex.ResponseCodes;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class MnsObexServerTest {

    @Mock
    MceStateMachine mStateMachine;

    MnsObexServer mServer;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mServer = new MnsObexServer(mStateMachine, null);
    }

    @Test
    public void onConnect_whenUuidIsWrong() {
        byte[] wrongUuid = new byte[]{};
        HeaderSet request = new HeaderSet();
        request.setHeader(HeaderSet.TARGET, wrongUuid);
        HeaderSet reply = new HeaderSet();

        assertThat(mServer.onConnect(request, reply))
                .isEqualTo(ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE);
    }

    @Test
    public void onConnect_withCorrectUuid() throws Exception {
        HeaderSet request = new HeaderSet();
        request.setHeader(HeaderSet.TARGET, MnsObexServer.MNS_TARGET);
        HeaderSet reply = new HeaderSet();

        assertThat(mServer.onConnect(request, reply)).isEqualTo(ResponseCodes.OBEX_HTTP_OK);
        assertThat(reply.getHeader(HeaderSet.WHO)).isEqualTo(MnsObexServer.MNS_TARGET);
    }

    @Test
    public void onDisconnect_callsStateMachineDisconnect() {
        HeaderSet request = new HeaderSet();
        HeaderSet reply = new HeaderSet();

        mServer.onDisconnect(request, reply);

        verify(mStateMachine).disconnect();
    }

    @Test
    public void onGet_returnsBadRequest() {
        Operation op = mock(Operation.class);

        assertThat(mServer.onGet(op)).isEqualTo(ResponseCodes.OBEX_HTTP_BAD_REQUEST);
    }

    @Test
    public void onPut_whenTypeIsInvalid_returnsBadRequest() throws Exception {
        HeaderSet headerSet = new HeaderSet();
        headerSet.setHeader(HeaderSet.TYPE, "some_invalid_type");
        Operation op = mock(Operation.class);
        when(op.getReceivedHeader()).thenReturn(headerSet);

        assertThat(mServer.onPut(op)).isEqualTo(ResponseCodes.OBEX_HTTP_BAD_REQUEST);
    }

    @Test
    public void onPut_whenHeaderSetIsValid_returnsOk() throws Exception {
        final StringBuilder xml = new StringBuilder();
        xml.append("<event\n");
        xml.append("    type=\"test_type\"\n");
        xml.append("    handle=\"FFAB\"\n");
        xml.append("    folder=\"test_folder\"\n");
        xml.append("    old_folder=\"test_old_folder\"\n");
        xml.append("    msg_type=\"MMS\"\n");
        xml.append("/>\n");
        DataInputStream stream = new DataInputStream(
                new ByteArrayInputStream(xml.toString().getBytes()));

        byte[] applicationParameter = new byte[] {
                Request.OAP_TAGID_MAS_INSTANCE_ID,
                1, // length in byte
                (byte) 55
        };

        HeaderSet headerSet = new HeaderSet();
        headerSet.setHeader(HeaderSet.TYPE, MnsObexServer.TYPE);
        headerSet.setHeader(HeaderSet.APPLICATION_PARAMETER, applicationParameter);

        Operation op = mock(Operation.class);
        when(op.getReceivedHeader()).thenReturn(headerSet);
        when(op.openDataInputStream()).thenReturn(stream);

        assertThat(mServer.onPut(op)).isEqualTo(ResponseCodes.OBEX_HTTP_OK);

        verify(mStateMachine).receiveEvent(any());
    }

    @Test
    public void onAbort_returnsNotImplemented() {
        HeaderSet request = new HeaderSet();
        HeaderSet reply = new HeaderSet();

        assertThat(mServer.onAbort(request, reply))
                .isEqualTo(ResponseCodes.OBEX_HTTP_NOT_IMPLEMENTED);
    }

    @Test
    public void onSetPath_returnsBadRequest() {
        HeaderSet request = new HeaderSet();
        HeaderSet reply = new HeaderSet();

        assertThat(mServer.onSetPath(request, reply, false, false))
                .isEqualTo(ResponseCodes.OBEX_HTTP_BAD_REQUEST);
    }

    @Test
    public void onClose_doesNotCrash() {
        mServer.onClose();
    }
}
