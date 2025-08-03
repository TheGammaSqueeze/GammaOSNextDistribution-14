/*
 * Copyright 2023 The Android Open Source Project
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

import static com.android.bluetooth.opp.BluetoothOppObexSession.MSG_SESSION_COMPLETE;
import static com.android.bluetooth.opp.BluetoothOppObexSession.MSG_SHARE_INTERRUPTED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.BluetoothMethodProxy;
import com.android.bluetooth.BluetoothObexTransport;
import com.android.obex.ClientSession;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class BluetoothOppObexClientSessionTest {
    @Mock
    BluetoothMethodProxy mMethodProxy;

    Context mTargetContext;
    @Mock
    BluetoothObexTransport mTransport;

    BluetoothOppObexClientSession mClientSession;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
        mTargetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mClientSession = new BluetoothOppObexClientSession(mTargetContext, mTransport);

        // to control the mServerSession.mSession
        InputStream input = mock(InputStream.class);
        OutputStream output = mock(OutputStream.class);
        doReturn(input).when(mTransport).openInputStream();
        doReturn(output).when(mTransport).openOutputStream();

        BluetoothMethodProxy.setInstanceForTesting(mMethodProxy);
    }

    @After
    public void tearDown() {
        BluetoothMethodProxy.setInstanceForTesting(null);
    }

    @Test
    public void startThenStop_startsAndStopsClientThread() throws Exception {
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
        BluetoothOppShareInfo shareInfo = new BluetoothOppShareInfo(0, uri, hintString, filename,
                mimetype, direction, destination, visibility, confirm, status, totalBytes,
                currentBytes, timestamp, mediaScanned);
        BluetoothOppSendFileInfo sendFileInfo = new BluetoothOppSendFileInfo(
                filename, mimetype, totalBytes, null, status);

        BluetoothOppUtility.putSendFileInfo(uri, sendFileInfo);
        // throw exception so the session will not connect
        doThrow(new IOException()).when(mTransport).openInputStream();
        doThrow(new IOException()).when(mTransport).openOutputStream();

        CountDownLatch sessionCompletedLatch = new CountDownLatch(1);
        mClientSession.start(new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                if (msg.what == MSG_SESSION_COMPLETE) {
                    sessionCompletedLatch.countDown();
                }
            }
        }, 1);

        // make mWaitingForShare be false
        mClientSession.addShare(shareInfo);

        // if the thread start, doSend should execute after a share is added
        // check if doSend() executed
        verify(mTransport, timeout(3_000)).openInputStream();

        // stop client session
        mClientSession.stop();

        BluetoothOppUtility.sSendFileMap.clear();
        assertThat(sessionCompletedLatch.await(3_000, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void clientThreadSendFile_clientSessionDisconnected_returnsObexDataError()
            throws IOException {
        Uri uri = Uri.parse("file://Idontknow//Justmadeitup");
        String hintString = "this is a object that take 4 bytes";
        // to cover applyRemoteDeviceQuirks
        String filename = "random.name.jpg";
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
        BluetoothOppShareInfo shareInfo = new BluetoothOppShareInfo(0, uri, hintString, filename,
                mimetype, direction, destination, visibility, confirm, status, totalBytes,
                currentBytes, timestamp, mediaScanned);
        BluetoothOppSendFileInfo sendFileInfo = new BluetoothOppSendFileInfo(
                filename, mimetype, totalBytes, null, status);

        BluetoothOppObexClientSession.ClientThread thread =
                mClientSession
                .new ClientThread(
                        mTargetContext, mTransport, 0, new Handler(Looper.getMainLooper()));
        InputStream is = mock(InputStream.class);
        OutputStream os = mock(OutputStream.class);
        doReturn(is).when(mTransport).openInputStream();
        doReturn(os).when(mTransport).openOutputStream();
        thread.mCs = new ClientSession(mTransport);
        thread.addShare(shareInfo);

        //thread.mCs.put() will throw because the obexconnection is not connected
        assertThat(thread.sendFile(sendFileInfo)).isEqualTo(BluetoothShare.STATUS_OBEX_DATA_ERROR);
    }

    @Test
    public void clientThreadInterrupt_sendMessageShareInterrupted() throws InterruptedException {
        CountDownLatch sessionInterruptLatch = new CountDownLatch(1);
        BluetoothOppObexClientSession.ClientThread thread =
                mClientSession
                .new ClientThread(
                        mTargetContext,
                        mTransport,
                        0,
                        new Handler(Looper.getMainLooper()) {
                            @Override
                            public void handleMessage(Message msg) {
                                super.handleMessage(msg);
                                if (msg.what == MSG_SHARE_INTERRUPTED) {
                                    sessionInterruptLatch.countDown();
                                }
                            }
                        });
        mClientSession.mWaitingForRemote = true;
        thread.interrupt();
        assertThat(sessionInterruptLatch.await(3_000, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void readFully() throws IOException {
        InputStream is = mock(InputStream.class);
        byte[] buffer = new byte[2];
        int size = 10000;
        doReturn(500, 500, -1).when(is).read(eq(buffer), anyInt(),
                anyInt());
        assertThat(BluetoothOppObexClientSession.readFully(is, buffer, size)).isEqualTo(1000);
    }
}
