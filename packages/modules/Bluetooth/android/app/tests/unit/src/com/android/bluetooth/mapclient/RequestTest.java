/*
 * Copyright (C) 2021 The Android Open Source Project
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

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.FakeObexServer;
import com.android.bluetooth.map.BluetoothMapAppParams;
import com.android.bluetooth.map.BluetoothMapFolderElement;
import com.android.bluetooth.mapclient.RequestSetMessageStatus.StatusIndicator;
import com.android.obex.ClientSession;
import com.android.obex.HeaderSet;
import com.android.obex.Operation;
import com.android.obex.ResponseCodes;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class RequestTest {

    private static final String SIMPLE_MMS_MESSAGE =
            "BEGIN:BMSG\r\nVERSION:1.0\r\nSTATUS:READ\r\nTYPE:MMS\r\nFOLDER:null\r\nBEGIN:BENV\r\n"
                    + "BEGIN:VCARD\r\nVERSION:2.1\r\nN:null;;;;\r\nTEL:555-5555\r\nEND:VCARD\r\n"
                    + "BEGIN:BBODY\r\nLENGTH:39\r\nBEGIN:MSG\r\nThis is a new msg\r\nEND:MSG\r\n"
                    + "END:BBODY\r\nEND:BENV\r\nEND:BMSG\r\n";

    private static final String TYPE_GET_FOLDER_LISTING = "x-obex/folder-listing";
    private static final String TYPE_GET_MESSAGE_LISTING = "x-bt/MAP-msg-listing";
    private static final String TYPE_MESSAGE = "x-bt/message";
    private static final String TYPE_SET_MESSAGE_STATUS = "x-bt/messageStatus";
    private static final String TYPE_SET_NOTIFICATION_REGISTRATION =
            "x-bt/MAP-NotificationRegistration";


    private static final String HANDLE = "0000001";

    private static final Bmessage TEST_MESSAGE = BmessageParser.createBmessage(SIMPLE_MMS_MESSAGE);
    private static final ArrayList<String> TEST_FOLDER_LIST = new ArrayList<String>(
            Arrays.asList("folder1"));
    private static final ArrayList<Message> TEST_MESSAGE_LIST = new ArrayList<Message>();
    private static final Date TEST_TIME = new Date();
    private static final byte TEST_STATUS_INDICATOR = Request.STATUS_INDICATOR_READ;
    private static final byte TEST_STATUS_VALUE = Request.STATUS_YES;

    private ClientSession mFakeClientSession;
    private FakeObexServer mFakeMapObexServer;

    @Before
    public void setUp() throws IOException {
        mFakeMapObexServer = new FakeMapObexServer();
        mFakeClientSession = new ClientSession(mFakeMapObexServer.mClientObexTransport);
        mFakeClientSession.connect(new HeaderSet());
    }

    @Test
    public void testRequestGetMessagesListing() throws IOException {
        RequestGetMessagesListing newRequest = new RequestGetMessagesListing(
                TEST_FOLDER_LIST.get(0), /*parameters*/ 0, /*filter*/ null, /*subjectLength*/ 0,
                /*maxListCount*/ 0, /*listStartOffset*/ 0);
        assertThat(newRequest).isNotNull();
        newRequest.execute(mFakeClientSession);

        assertThat(newRequest.isSuccess()).isTrue();
        assertThat(newRequest.getList().toString()).isEqualTo(TEST_MESSAGE_LIST.toString());
        assertThat(newRequest.getNewMessageStatus()).isTrue();
        assertThat(newRequest.getMseTime().toString()).isEqualTo(TEST_TIME.toString());
    }

    @Test
    public void testRequestGetMessage() throws IOException {
        RequestGetMessage newRequest = new RequestGetMessage(HANDLE, MasClient.CharsetType.UTF_8,
                /*attachment*/ false);
        assertThat(newRequest).isNotNull();
        assertThat(newRequest.getHandle()).isEqualTo(HANDLE);
        newRequest.execute(mFakeClientSession);

        assertThat(newRequest.isSuccess()).isTrue();
        assertThat(newRequest.getMessage().toString()).isEqualTo(TEST_MESSAGE.toString());
        assertThat(newRequest.getHandle()).isEqualTo(HANDLE);
    }

    @Test
    public void testRequestGetFolderListing() throws IOException {
        RequestGetFolderListing newRequest = new RequestGetFolderListing(/*maxListCount*/ 255,
                /*listStartOffset*/ 0);
        assertThat(newRequest).isNotNull();
        newRequest.execute(mFakeClientSession);

        assertThat(newRequest.isSuccess()).isTrue();
        assertThat(newRequest.getList().toString()).isEqualTo(TEST_FOLDER_LIST.toString());
    }

    @Test
    public void testRequestPushMessage() throws IOException {
        RequestPushMessage newRequest = new RequestPushMessage(TEST_FOLDER_LIST.get(0),
                TEST_MESSAGE, /*charset*/ null, /*transparent*/ false, /*retry*/ false);
        assertThat(newRequest).isNotNull();
        assertThat(newRequest.getMsgHandle()).isEqualTo(null);
        newRequest.execute(mFakeClientSession);

        assertThat(newRequest.isSuccess()).isTrue();
        assertThat(newRequest.getBMsg().toString()).isEqualTo(TEST_MESSAGE.toString());
        assertThat(newRequest.getMsgHandle()).isEqualTo(HANDLE);
    }

    @Test
    public void testRequestSetMessageStatus() throws IOException {
        RequestSetMessageStatus newRequest = new RequestSetMessageStatus(HANDLE,
                StatusIndicator.READ, TEST_STATUS_VALUE);
        assertThat(newRequest).isNotNull();
        assertThat(newRequest.getHandle()).isEqualTo(HANDLE);
        newRequest.execute(mFakeClientSession);

        assertThat(newRequest.isSuccess()).isTrue();
        assertThat(newRequest.getValue()).isEqualTo(TEST_STATUS_VALUE);
        assertThat(newRequest.getHandle()).isEqualTo(HANDLE);
    }

    @Test
    public void testRequestSetNotificationRegistration() throws IOException {
        RequestSetNotificationRegistration newRequest = new RequestSetNotificationRegistration(
                /*status*/ true);
        assertThat(newRequest).isNotNull();
        newRequest.execute(mFakeClientSession);

        assertThat(newRequest.getStatus()).isTrue();
    }

    @Test
    public void testRequestSetPath() throws IOException {
        RequestSetPath newRequest = new RequestSetPath(TEST_FOLDER_LIST.get(0));
        assertThat(newRequest).isNotNull();
        newRequest.execute(mFakeClientSession);

        assertThat(newRequest.isSuccess()).isTrue();
    }

    static class FakeMapObexServer extends FakeObexServer {

        FakeMapObexServer() throws IOException {
            super();
        }

        @Override
        public int onGetValidator(final Operation op) {
            OutputStream outputStream;
            HeaderSet replyHeaders = new HeaderSet();
            BluetoothMapAppParams outAppParams = new BluetoothMapAppParams();
            try {

                HeaderSet request = op.getReceivedHeader();
                String type = (String) request.getHeader(HeaderSet.TYPE);
                switch (type) {
                    case TYPE_GET_FOLDER_LISTING:
                        op.sendHeaders(replyHeaders);
                        outputStream = op.openOutputStream();
                        BluetoothMapFolderElement root =
                                new BluetoothMapFolderElement(/*name*/ "root", /*parent*/ null);
                        root.addFolder("Folder1");
                        outputStream.write(root.encode(/*offset*/ 0, /*count*/ 1));
                        outputStream.close();
                        return ResponseCodes.OBEX_HTTP_OK;

                    case TYPE_MESSAGE:
                        op.sendHeaders(replyHeaders);
                        outputStream = op.openOutputStream();
                        outputStream.write(SIMPLE_MMS_MESSAGE.getBytes());
                        outputStream.close();
                        return ResponseCodes.OBEX_HTTP_OK;

                    case TYPE_GET_MESSAGE_LISTING:
                        outAppParams.setNewMessage(1);
                        outAppParams.setMseTime(TEST_TIME.getTime());
                        replyHeaders.setHeader(HeaderSet.APPLICATION_PARAMETER,
                                outAppParams.encodeParams());
                        op.sendHeaders(replyHeaders);
                        return ResponseCodes.OBEX_HTTP_OK;
                }
            } catch (Exception e) {
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }

        @Override
        public int onPutValidator(final Operation op) {
            try {
                HeaderSet request = op.getReceivedHeader();
                String type = (String) request.getHeader(HeaderSet.TYPE);
                byte[] appParamRaw = (byte[]) request.getHeader(HeaderSet.APPLICATION_PARAMETER);
                BluetoothMapAppParams appParams;
                if (appParamRaw != null) {
                    appParams = new BluetoothMapAppParams(appParamRaw);
                } else {
                    return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
                }
                switch (type) {
                    case TYPE_SET_MESSAGE_STATUS:
                        if (appParams.getStatusIndicator() != TEST_STATUS_INDICATOR) {
                            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
                        }
                        if (appParams.getStatusValue() != TEST_STATUS_VALUE) {
                            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
                        }
                        return ResponseCodes.OBEX_HTTP_OK;

                    case TYPE_SET_NOTIFICATION_REGISTRATION:
                        if (appParams.getNotificationStatus() != 1) {
                            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
                        }
                        return ResponseCodes.OBEX_HTTP_OK;

                    case TYPE_MESSAGE:
                        HeaderSet replyHeaders = new HeaderSet();
                        replyHeaders.setHeader(HeaderSet.NAME, HANDLE);
                        op.sendHeaders(replyHeaders);
                        return ResponseCodes.OBEX_HTTP_OK;
                }
            } catch (Exception e) {
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }

        @Override
        public int onSetPathValidator(final HeaderSet request, HeaderSet reply,
                final boolean backup,
                final boolean create) {
            try {
                String tmpPath = (String) request.getHeader(HeaderSet.NAME);
                assertThat(tmpPath).isEqualTo(TEST_FOLDER_LIST.get(0));
                return ResponseCodes.OBEX_HTTP_OK;
            } catch (Exception e) {
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }
        }
    }
}
