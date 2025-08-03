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

package com.android.bluetooth.opp;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import android.content.Context;
import android.content.ContextWrapper;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.BluetoothMethodProxy;
import com.android.bluetooth.BluetoothObexTransport;
import com.android.obex.HeaderSet;
import com.android.obex.Operation;
import com.android.obex.ResponseCodes;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@RunWith(AndroidJUnit4.class)
public class BluetoothOppObexServerSessionTest {
    @Mock
    BluetoothMethodProxy mMethodProxy;

    Context mTargetContext;
    @Mock
    BluetoothObexTransport mTransport;

    @Mock
    BluetoothOppService mBluetoothOppService;
    @Mock
    Operation mOperation;

    BluetoothOppObexServerSession mServerSession;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
        mTargetContext = spy(
                new ContextWrapper(
                        InstrumentationRegistry.getInstrumentation().getTargetContext()));
        mServerSession = new BluetoothOppObexServerSession(mTargetContext, mTransport,
                mBluetoothOppService);

        // to control the mServerSession.mSession
        InputStream input = mock(InputStream.class);
        OutputStream output = mock(OutputStream.class);
        doReturn(-1).when(input).read();
        doReturn(input).when(mTransport).openInputStream();
        doReturn(output).when(mTransport).openOutputStream();

        BluetoothMethodProxy.setInstanceForTesting(mMethodProxy);
    }

    @After
    public void tearDown() {
        BluetoothMethodProxy.setInstanceForTesting(null);
    }

    @Test
    public void constructor_createInstanceCorrectly() {
        mServerSession = new BluetoothOppObexServerSession(mTargetContext, mTransport,
                mBluetoothOppService);
        assertThat(mServerSession.mBluetoothOppService).isEqualTo(mBluetoothOppService);
        assertThat(mServerSession.mTransport).isEqualTo(mTransport);
        assertThat(mServerSession.mContext).isEqualTo(mTargetContext);
    }

    @Test
    public void unblock() {
        assertThat(mServerSession.mServerBlocking).isTrue();
        mServerSession.unblock();
        assertThat(mServerSession.mServerBlocking).isFalse();
    }

    @Test
    public void preStart_thenStart_thenStop_flowWorksCorrectly() {
        Handler handler = mock(Handler.class);
        assertThat(mServerSession.mSession).isNull();
        assertThat(mServerSession.mCallback).isNull();
        mServerSession.preStart();
        assertThat(mServerSession.mSession).isNotNull();
        assertThat(mServerSession.mCallback).isNull();
        mServerSession.start(handler, 0);
        assertThat(mServerSession.mSession).isNotNull();
        assertThat(mServerSession.mCallback).isEqualTo(handler);
        mServerSession.stop();
        assertThat(mServerSession.mSession).isNull();
        assertThat(mServerSession.mCallback).isNull();
    }

    @Test
    public void addShare_updatesShareInfo() {
        Uri uri = Uri.parse("file://Idontknow//Justmadeitup");
        String hintString = "this is a object that take 4 bytes";
        String filename = "random.jpg";
        String mimetype = "image/jpeg";
        int direction = BluetoothShare.DIRECTION_INBOUND;
        String destination = "01:23:45:67:89:AB";
        int visibility = BluetoothShare.VISIBILITY_VISIBLE;
        int confirm = BluetoothShare.USER_CONFIRMATION_CONFIRMED;
        int status = BluetoothShare.STATUS_PENDING;
        int totalBytes = 1023;
        int currentBytes = 42;
        int timestamp = 123456789;
        boolean mediaScanned = false;
        BluetoothOppShareInfo info = new BluetoothOppShareInfo(0, uri, hintString, filename,
                mimetype, direction, destination, visibility, confirm, status, totalBytes,
                currentBytes, timestamp, mediaScanned);

        mServerSession.addShare(info);
        assertThat(mServerSession.mInfo).isEqualTo(info);
    }

    @Test
    public void onPut_withUserConfirmationDenied_returnsObexHttpForbidden() {
        mServerSession.mAccepted = BluetoothShare.USER_CONFIRMATION_DENIED;
        assertThat(mServerSession.onPut(mOperation)).isEqualTo(ResponseCodes.OBEX_HTTP_FORBIDDEN);
    }

    @Test
    public void onPut_withClosedOperation_returnsObexHttpBadRequest() throws IOException {
        doThrow(new IOException()).when(mOperation).getReceivedHeader();
        assertThat(mServerSession.onPut(mOperation)).isEqualTo(ResponseCodes.OBEX_HTTP_BAD_REQUEST);
    }

    @Test
    public void onPut_withZeroLengthInHeader_returnsLengthRequired() throws IOException {
        String name = "";
        long length = 0;
        String mimeType = "text/plain";
        HeaderSet headerSet = new HeaderSet();
        doReturn(headerSet).when(mOperation).getReceivedHeader();
        headerSet.setHeader(HeaderSet.NAME, name);
        headerSet.setHeader(HeaderSet.LENGTH, length);
        headerSet.setHeader(HeaderSet.TYPE, mimeType);
        assertThat(mServerSession.onPut(mOperation)).isEqualTo(
                ResponseCodes.OBEX_HTTP_LENGTH_REQUIRED);
    }

    @Test
    public void onPut_withZeroLengthNameInHeader_returnsHttpBadRequest() throws IOException {
        String name = "";
        long length = 10;
        String mimeType = "text/plain";
        HeaderSet headerSet = new HeaderSet();
        doReturn(headerSet).when(mOperation).getReceivedHeader();
        headerSet.setHeader(HeaderSet.NAME, name);
        headerSet.setHeader(HeaderSet.LENGTH, length);
        headerSet.setHeader(HeaderSet.TYPE, mimeType);
        assertThat(mServerSession.onPut(mOperation)).isEqualTo(ResponseCodes.OBEX_HTTP_BAD_REQUEST);
    }

    @Test
    public void onPut_withNoMimeTypeInHeader_returnsHttpBadRequest() throws IOException {
        String name = "randomFile";
        long length = 10;
        String mimeType = null;
        HeaderSet headerSet = new HeaderSet();
        doReturn(headerSet).when(mOperation).getReceivedHeader();
        headerSet.setHeader(HeaderSet.NAME, name);
        headerSet.setHeader(HeaderSet.LENGTH, length);
        headerSet.setHeader(HeaderSet.TYPE, mimeType);
        assertThat(mServerSession.onPut(mOperation)).isEqualTo(ResponseCodes.OBEX_HTTP_BAD_REQUEST);
    }

    @Test
    public void onPut_withUnsupportedMimeTypeInHeader_returnsHttpBadRequest() throws IOException {
        String name = "randomFile.3danimation";
        long length = 10;
        String mimeType = "3danimation/superultrasonic";
        HeaderSet headerSet = new HeaderSet();
        headerSet.setHeader(HeaderSet.NAME, name);
        headerSet.setHeader(HeaderSet.LENGTH, length);
        headerSet.setHeader(HeaderSet.TYPE, mimeType);
        doReturn(headerSet).when(mOperation).getReceivedHeader();
        assertThat(mServerSession.onPut(mOperation)).isEqualTo(
                ResponseCodes.OBEX_HTTP_UNSUPPORTED_TYPE);
    }

    @Test
    public void onPut_returnsObexHttpOk() throws IOException {
        // The flow of this test is as follow
        // onPut(mOperation) -> check many fileName, length, mimeType from op.getReceivedHeader()
        // insert the newly received info into ContentResolver
        // unblock the server and remove timeout message
        // modify mInfo & mFileInfo, manipulate receiveFile() then return ResponseCodes.OBEX_HTTP_OK

        Assume.assumeTrue("Ignore test when if there is not media mounted",
                Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED));
        String name = "randomFile.txt";
        long length = 10;
        String mimeType = "text/plain";
        Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/1");
        Uri uri = Uri.parse("file://Idontknow//Justmadeitup");
        int direction = BluetoothShare.DIRECTION_INBOUND;
        String hint = "file://Idontknow//Justmadeitup//" + name;
        String destination = "01:23:45:67:89:AB";
        int visibility = BluetoothShare.VISIBILITY_VISIBLE;
        int confirm = BluetoothShare.USER_CONFIRMATION_CONFIRMED;
        int status = BluetoothShare.STATUS_SUCCESS;
        int totalBytes = 1023;
        int currentBytes = 42;
        int timestamp = 123456789;
        boolean mediaScanned = false;
        mServerSession.mInfo = new BluetoothOppShareInfo(0, uri, hint, name,
                mimeType, direction, destination, visibility, confirm, status, totalBytes,
                currentBytes, timestamp, mediaScanned);
        mServerSession.mFileInfo = new BluetoothOppReceiveFileInfo(name, length, uri, status);

        HeaderSet headerSet = new HeaderSet();
        headerSet.setHeader(HeaderSet.NAME, name);
        headerSet.setHeader(HeaderSet.LENGTH, length);
        headerSet.setHeader(HeaderSet.TYPE, mimeType);
        doReturn(headerSet).when(mOperation).getReceivedHeader();

        doReturn(contentUri).when(mMethodProxy)
                .contentResolverInsert(any(), eq(BluetoothShare.CONTENT_URI), any());

        // unblocking the session
        mServerSession.unblock();
        mServerSession.mAccepted = BluetoothShare.USER_CONFIRMATION_CONFIRMED;
        Handler handler = mock(Handler.class);
        doAnswer(arg -> {
            mServerSession.unblock();
            // to ignore removeMessage, which is not mockable
            mServerSession.mTimeoutMsgSent = false;
            return true;
        }).when(handler).sendMessageAtTime(
                argThat(arg -> arg.what == BluetoothOppObexSession.MSG_CONNECT_TIMEOUT), anyLong());
        mServerSession.start(handler, 0);

        // manipulate ReceiveFile
        InputStream is = mock(InputStream.class);
        OutputStream os = mock(OutputStream.class);
        doReturn(is).when(mOperation).openInputStream();
        doReturn(10).when(mOperation).getMaxPacketSize();
        doReturn(os).when(mMethodProxy).contentResolverOpenOutputStream(any(), eq(uri));
        doReturn((int) length, -1).when(is).read(any());

        assertThat(mServerSession.onPut(mOperation)).isEqualTo(ResponseCodes.OBEX_HTTP_OK);
    }

    @Test
    public void onConnect_withNonNullTargetInHeader_returnsHttpNotAcceptable() {
        HeaderSet request = new HeaderSet();
        HeaderSet reply = new HeaderSet();
        byte[] target = new byte[10];
        request.setHeader(HeaderSet.TARGET, target);
        assertThat(mServerSession.onConnect(request, reply)).isEqualTo(
                ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE);
    }

    @Test
    public void onConnect_returnsObexHttpOk() {
        HeaderSet request = new HeaderSet();
        HeaderSet reply = new HeaderSet();
        request.setHeader(HeaderSet.TARGET, null);
        BluetoothOppManager bluetoothOppManager = spy(
                BluetoothOppManager.getInstance(mTargetContext));
        BluetoothOppManager.setInstance(bluetoothOppManager);
        doReturn(true).when(bluetoothOppManager).isAcceptlisted(any());
        doNothing().when(mTargetContext).sendBroadcast(any(),
                eq(Constants.HANDOVER_STATUS_PERMISSION), any());

        assertThat(mServerSession.onConnect(request, reply)).isEqualTo(ResponseCodes.OBEX_HTTP_OK);
        BluetoothOppManager.setInstance(null);
    }

    @Test
    public void onDisconnect_repliesObexHttpOk() {
        HeaderSet request = new HeaderSet();
        HeaderSet reply = new HeaderSet();
        mServerSession.onDisconnect(request, reply);
        assertThat(reply.responseCode).isEqualTo(ResponseCodes.OBEX_HTTP_OK);
    }

    @Test
    public void onClose_doesNotThrow() {
        Handler handler = mock(Handler.class);
        mServerSession.start(handler, 0);
        mServerSession.onClose();
    }
}
