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

import static com.android.bluetooth.opp.BluetoothOppTransfer.TRANSPORT_CONNECTED;
import static com.android.bluetooth.opp.BluetoothOppTransfer.TRANSPORT_ERROR;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothUuid;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Looper;
import android.os.Message;

import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.BluetoothMethodProxy;
import com.android.bluetooth.BluetoothObexTransport;
import com.android.obex.ObexTransport;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Objects;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class BluetoothOppTransferTest {
    private final Uri mUri = Uri.parse("file://Idontknow/Justmadeitup");
    private final String mHintString = "this is a object that take 4 bytes";
    private final String mFilename = "random.jpg";
    private final String mMimetype = "image/jpeg";
    private final int mDirection = BluetoothShare.DIRECTION_INBOUND;
    private final String mDestination = "01:23:45:67:89:AB";
    private final int mVisibility = BluetoothShare.VISIBILITY_VISIBLE;
    private final int mConfirm = BluetoothShare.USER_CONFIRMATION_AUTO_CONFIRMED;
    private final int mStatus = BluetoothShare.STATUS_PENDING;
    private final int mTotalBytes = 1023;
    private final int mCurrentBytes = 42;
    private final int mTimestamp = 123456789;
    private final boolean mMediaScanned = false;

    @Mock
    BluetoothOppObexSession mSession;
    @Mock
    BluetoothMethodProxy mCallProxy;
    Context mContext;
    BluetoothOppBatch mBluetoothOppBatch;
    BluetoothOppTransfer mTransfer;
    BluetoothOppTransfer.EventHandler mEventHandler;
    BluetoothOppShareInfo mInitShareInfo;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        BluetoothMethodProxy.setInstanceForTesting(mCallProxy);
        doReturn(0).when(mCallProxy).contentResolverDelete(any(), nullable(Uri.class),
                nullable(String.class), nullable(String[].class));
        doReturn(0).when(mCallProxy).contentResolverUpdate(any(), nullable(Uri.class),
                nullable(ContentValues.class), nullable(String.class), nullable(String[].class));

        mInitShareInfo = new BluetoothOppShareInfo(8765, mUri, mHintString, mFilename, mMimetype,
                mDirection, mDestination, mVisibility, mConfirm, mStatus, mTotalBytes,
                mCurrentBytes,
                mTimestamp, mMediaScanned);
        mContext = spy(
                new ContextWrapper(
                        InstrumentationRegistry.getInstrumentation().getTargetContext()));
        mBluetoothOppBatch = spy(new BluetoothOppBatch(mContext, mInitShareInfo));
        mTransfer = new BluetoothOppTransfer(mContext, mBluetoothOppBatch, mSession);
        mEventHandler = mTransfer.new EventHandler(Looper.getMainLooper());
    }

    @After
    public void tearDown() {
        BluetoothMethodProxy.setInstanceForTesting(null);
    }

    @Test
    public void onShareAdded_checkFirstPendingShare() {
        BluetoothOppShareInfo newShareInfo = new BluetoothOppShareInfo(1, mUri, mHintString,
                mFilename, mMimetype, BluetoothShare.DIRECTION_INBOUND, mDestination, mVisibility,
                BluetoothShare.USER_CONFIRMATION_AUTO_CONFIRMED, mStatus, mTotalBytes,
                mCurrentBytes,
                mTimestamp, mMediaScanned);

        doAnswer(invocation -> {
            assertThat((BluetoothOppShareInfo) invocation.getArgument(0))
                    .isEqualTo(mInitShareInfo);
            return null;
        }).when(mSession).addShare(any(BluetoothOppShareInfo.class));

        // This will trigger mTransfer.onShareAdded(), which will call mTransfer
        // .processCurrentShare(),
        // which will add the first pending share to the session
        mBluetoothOppBatch.addShare(newShareInfo);
        verify(mSession).addShare(any(BluetoothOppShareInfo.class));
    }

    @Test
    public void onBatchCanceled_checkStatus() {
        // This will trigger mTransfer.onBatchCanceled(),
        // which will then change the status of the batch accordingly
        mBluetoothOppBatch.cancelBatch();
        assertThat(mBluetoothOppBatch.mStatus).isEqualTo(Constants.BATCH_STATUS_FINISHED);
    }

    @Test
    public void start_bluetoothDisabled_batchFail() {
        mTransfer.start();
        // Since Bluetooth is disabled, the batch will fail
        assertThat(mBluetoothOppBatch.mStatus).isEqualTo(Constants.BATCH_STATUS_FAILED);
    }

    @Test
    public void start_receiverRegistered() {
        doReturn(true).when(mCallProxy).bluetoothAdapterIsEnabled(any());
        mTransfer.start();
        verify(mContext).registerReceiver(any(), any(IntentFilter.class));
        // need this, or else the handler thread might throw in middle of the next test
        mTransfer.stop();
    }

    @Test
    public void stop_unregisterRegistered() {
        doReturn(true).when(mCallProxy).bluetoothAdapterIsEnabled(any());
        mTransfer.start();
        mTransfer.stop();
        verify(mContext).unregisterReceiver(any());
    }

    @Test
    public void eventHandler_handleMessage_TRANSPORT_ERROR_connectThreadIsNull() {
        Message message = Message.obtain(mEventHandler, TRANSPORT_ERROR);
        mEventHandler.handleMessage(message);
        assertThat(mTransfer.mConnectThread).isNull();
        assertThat(mBluetoothOppBatch.mStatus).isEqualTo(Constants.BATCH_STATUS_FAILED);
    }

// TODO: try to use ShadowBluetoothDevice
//    @Test
//    public void eventHandler_handleMessage_SOCKET_ERROR_RETRY_connectThreadInitiated() {
//        BluetoothDevice bluetoothDevice = ShadowBluetoothDevice();
//        Message message = Message.obtain(mEventHandler, SOCKET_ERROR_RETRY, bluetoothDevice);
//        mEventHandler.handleMessage(message);
//        assertThat(mTransfer.mConnectThread).isNotNull();
//    }

    @Test
    public void eventHandler_handleMessage_TRANSPORT_CONNECTED_obexSessionStarted() {
        ObexTransport transport = mock(BluetoothObexTransport.class);
        Message message = Message.obtain(mEventHandler, TRANSPORT_CONNECTED, transport);
        mEventHandler.handleMessage(message);
        assertThat(mBluetoothOppBatch.mStatus).isEqualTo(Constants.BATCH_STATUS_RUNNING);
    }

    @Test
    public void eventHandler_handleMessage_MSG_SHARE_COMPLETE_shareAdded() {
        Message message = Message.obtain(mEventHandler, BluetoothOppObexSession.MSG_SHARE_COMPLETE);

        mInitShareInfo = new BluetoothOppShareInfo(123, mUri, mHintString, mFilename, mMimetype,
                BluetoothShare.DIRECTION_OUTBOUND, mDestination, mVisibility, mConfirm, mStatus,
                mTotalBytes, mCurrentBytes, mTimestamp, mMediaScanned);
        mContext = spy(
                new ContextWrapper(
                        InstrumentationRegistry.getInstrumentation().getTargetContext()));
        mBluetoothOppBatch = spy(new BluetoothOppBatch(mContext, mInitShareInfo));
        mTransfer = new BluetoothOppTransfer(mContext, mBluetoothOppBatch, mSession);
        mEventHandler = mTransfer.new EventHandler(Looper.getMainLooper());
        mEventHandler.handleMessage(message);

        // Since there is still a share in mBluetoothOppBatch, it will be added into session
        verify(mSession).addShare(any(BluetoothOppShareInfo.class));
    }

    @Test
    public void eventHandler_handleMessage_MSG_SESSION_COMPLETE_batchFinished() {
        BluetoothOppShareInfo info = mock(BluetoothOppShareInfo.class);
        Message message = Message.obtain(mEventHandler,
                BluetoothOppObexSession.MSG_SESSION_COMPLETE,
                info);
        mEventHandler.handleMessage(message);

        assertThat(mBluetoothOppBatch.mStatus).isEqualTo(Constants.BATCH_STATUS_FINISHED);
    }

    @Test
    public void eventHandler_handleMessage_MSG_SESSION_ERROR_batchFailed() {
        BluetoothOppShareInfo info = mock(BluetoothOppShareInfo.class);
        Message message = Message.obtain(mEventHandler, BluetoothOppObexSession.MSG_SESSION_ERROR,
                info);
        mEventHandler.handleMessage(message);

        assertThat(mBluetoothOppBatch.mStatus).isEqualTo(Constants.BATCH_STATUS_FAILED);
    }

    @Test
    public void eventHandler_handleMessage_MSG_SHARE_INTERRUPTED_batchFailed() {

        mInitShareInfo = new BluetoothOppShareInfo(123, mUri, mHintString, mFilename, mMimetype,
                BluetoothShare.DIRECTION_OUTBOUND, mDestination, mVisibility, mConfirm, mStatus,
                mTotalBytes, mCurrentBytes, mTimestamp, mMediaScanned);
        mBluetoothOppBatch = spy(new BluetoothOppBatch(mContext, mInitShareInfo));
        mTransfer = new BluetoothOppTransfer(mContext, mBluetoothOppBatch, mSession);
        mEventHandler = mTransfer.new EventHandler(Looper.getMainLooper());

        BluetoothOppShareInfo info = mock(BluetoothOppShareInfo.class);
        Message message = Message.obtain(mEventHandler,
                BluetoothOppObexSession.MSG_SHARE_INTERRUPTED,
                info);
        mEventHandler.handleMessage(message);

        assertThat(mBluetoothOppBatch.mStatus).isEqualTo(Constants.BATCH_STATUS_FAILED);
    }

    @Test
    public void eventHandler_handleMessage_MSG_CONNECT_TIMEOUT() {
        Message message = Message.obtain(mEventHandler,
                BluetoothOppObexSession.MSG_CONNECT_TIMEOUT);
        BluetoothOppShareInfo newInfo = new BluetoothOppShareInfo(321, mUri, mHintString,
                mFilename, mMimetype, mDirection, mDestination, mVisibility, mConfirm, mStatus,
                mTotalBytes, mCurrentBytes, mTimestamp, mMediaScanned);
        // Adding new info will assign value to mCurrentShare
        mBluetoothOppBatch.addShare(newInfo);
        mEventHandler.handleMessage(message);

        verify(mContext).sendBroadcast(argThat(
                arg -> arg.getAction().equals(BluetoothShare.USER_CONFIRMATION_TIMEOUT_ACTION)));
    }

    @Test
    public void socketConnectThreadConstructors() {
        String address = "AA:BB:CC:EE:DD:11";
        BluetoothDevice device = (mContext.getSystemService(BluetoothManager.class))
                .getAdapter().getRemoteDevice(address);
        BluetoothOppTransfer transfer = new BluetoothOppTransfer(mContext, mBluetoothOppBatch);
        BluetoothOppTransfer.SocketConnectThread socketConnectThread =
                transfer.new SocketConnectThread(device, true);
        BluetoothOppTransfer.SocketConnectThread socketConnectThread2 =
                transfer.new SocketConnectThread(device, true, false, 0);
        assertThat(Objects.equals(socketConnectThread.mDevice, device)).isTrue();
        assertThat(Objects.equals(socketConnectThread2.mDevice, device)).isTrue();
    }

    @Test
    public void socketConnectThreadInterrupt() {
        String address = "AA:BB:CC:EE:DD:11";
        BluetoothDevice device = (mContext.getSystemService(BluetoothManager.class))
                .getAdapter().getRemoteDevice(address);
        BluetoothOppTransfer transfer = new BluetoothOppTransfer(mContext, mBluetoothOppBatch);
        BluetoothOppTransfer.SocketConnectThread socketConnectThread =
                transfer.new SocketConnectThread(device, true);
        socketConnectThread.interrupt();
        assertThat(socketConnectThread.mIsInterrupted).isTrue();
    }

    @Test
    @SuppressWarnings("DoNotCall")
    public void socketConnectThreadRun_bluetoothDisabled_connectionFailed() {
        String address = "AA:BB:CC:EE:DD:11";
        BluetoothDevice device = (mContext.getSystemService(BluetoothManager.class))
                .getAdapter().getRemoteDevice(address);
        BluetoothOppTransfer transfer = new BluetoothOppTransfer(mContext, mBluetoothOppBatch);
        BluetoothOppTransfer.SocketConnectThread socketConnectThread =
                transfer.new SocketConnectThread(device, true);
        transfer.mSessionHandler = mEventHandler;

        socketConnectThread.run();
        verify(mCallProxy).handlerSendEmptyMessage(any(), eq(TRANSPORT_ERROR));
    }

    @Test
    public void oppConnectionReceiver_onReceiveWithActionAclDisconnected_sendsConnectTimeout() {
        BluetoothDevice device = (mContext.getSystemService(BluetoothManager.class))
                .getAdapter().getRemoteDevice(mDestination);
        BluetoothOppTransfer transfer = new BluetoothOppTransfer(mContext, mBluetoothOppBatch);
        transfer.mCurrentShare = mInitShareInfo;
        transfer.mCurrentShare.mConfirm = BluetoothShare.USER_CONFIRMATION_PENDING;
        BluetoothOppTransfer.OppConnectionReceiver receiver = transfer.new OppConnectionReceiver();
        Intent intent = new Intent();
        intent.setAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);

        transfer.mSessionHandler = mEventHandler;
        receiver.onReceive(mContext, intent);
        verify(mCallProxy).handlerSendEmptyMessage(any(),
                eq(BluetoothOppObexSession.MSG_CONNECT_TIMEOUT));
    }

    @Test
    public void oppConnectionReceiver_onReceiveWithActionSdpRecord_sendsNoMessage() {
        BluetoothDevice device = (mContext.getSystemService(BluetoothManager.class))
                .getAdapter().getRemoteDevice(mDestination);
        BluetoothOppTransfer transfer = new BluetoothOppTransfer(mContext, mBluetoothOppBatch);
        transfer.mCurrentShare = mInitShareInfo;
        transfer.mCurrentShare.mConfirm = BluetoothShare.USER_CONFIRMATION_PENDING;
        transfer.mDevice = device;
        transfer.mSessionHandler = mEventHandler;
        BluetoothOppTransfer.OppConnectionReceiver receiver = transfer.new OppConnectionReceiver();
        Intent intent = new Intent();
        intent.setAction(BluetoothDevice.ACTION_SDP_RECORD);
        intent.putExtra(BluetoothDevice.EXTRA_UUID, BluetoothUuid.OBEX_OBJECT_PUSH);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);

        receiver.onReceive(mContext, intent);

        // bluetooth device name is null => skip without interaction
        verifyNoMoreInteractions(mCallProxy);
    }
}
