/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.uwb.discovery;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.discovery.Transport.DataReceiver;
import com.android.server.uwb.discovery.Transport.SendingDataCallback;
import com.android.server.uwb.discovery.TransportProvider.TerminationReason;
import com.android.server.uwb.discovery.info.AdminErrorMessage;
import com.android.server.uwb.discovery.info.AdminErrorMessage.ErrorType;
import com.android.server.uwb.discovery.info.AdminEventMessage;
import com.android.server.uwb.discovery.info.AdminEventMessage.EventType;
import com.android.server.uwb.discovery.info.FiraConnectorMessage;
import com.android.server.uwb.discovery.info.FiraConnectorMessage.InstructionCode;
import com.android.server.uwb.discovery.info.FiraConnectorMessage.MessageType;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit test for {@link TransportProvider} */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class TransportProviderTest {

    private static final int SECID = 3;
    private static final int SECID2 = 4;
    private static final int SECID3 = 5;
    private static final byte[] MESSAGE_PAYLOAD = new byte[] {(byte) 0xF4, 0x00, 0x40};
    private static final FiraConnectorMessage MESSAGE =
            new FiraConnectorMessage(
                    MessageType.EVENT, InstructionCode.DATA_EXCHANGE, MESSAGE_PAYLOAD);

    /** Fake implementation of the TransportProvider for testing. */
    static class FakeTransportProvider extends TransportProvider {
        public boolean sendMessageSuccess = true;
        public FiraConnectorMessage lastSendMessage;
        public int lastSendMessageSecid = -1;
        public TerminationReason lastTerminationReason = null;

        FakeTransportProvider() {
            super(SECID);
        }

        @Override
        public boolean sendMessage(int secid, FiraConnectorMessage message) {
            if (sendMessageSuccess) {
                lastSendMessageSecid = secid;
                lastSendMessage = message;
            }
            return sendMessageSuccess;
        }

        @Override
        public boolean start() {
            if (!super.start()) {
                return false;
            }
            mStarted = true;
            return true;
        }

        @Override
        public boolean stop() {
            if (!super.stop()) {
                return false;
            }
            mStarted = false;
            return true;
        }

        @Override
        protected void terminateOnError(TerminationReason reason) {
            lastTerminationReason = reason;
        }
    }

    @Mock DataReceiver mMockDataReceiver;
    @Mock DataReceiver mMockDataReceiver2;
    @Mock SendingDataCallback mMockSendingDataCallback;

    private FakeTransportProvider mFakeTransportProvider;
    private TransportProvider mTransportProvider;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mFakeTransportProvider = new FakeTransportProvider();
        mTransportProvider = mFakeTransportProvider;
    }

    @Test
    public void testStartAndStop() {
        assertThat(mTransportProvider.start()).isTrue();
        assertThat(mTransportProvider.start()).isFalse();
        assertThat(mTransportProvider.stop()).isTrue();
        assertThat(mTransportProvider.stop()).isFalse();
        assertThat(mTransportProvider.start()).isTrue();
    }

    @Test
    public void testRegisterAndUnregisterDataReceiver() {
        mTransportProvider.registerDataReceiver(mMockDataReceiver);
        mTransportProvider.registerDataReceiver(mMockDataReceiver2);
        mTransportProvider.onMessageReceived(SECID, MESSAGE);

        verify(mMockDataReceiver, times(1)).onDataReceived(MESSAGE_PAYLOAD);
        verifyZeroInteractions(mMockDataReceiver2);

        mTransportProvider.unregisterDataReceiver();
        mTransportProvider.onMessageReceived(SECID, MESSAGE);

        verifyNoMoreInteractions(mMockDataReceiver);
        verifyZeroInteractions(mMockDataReceiver2);

        mTransportProvider.registerDataReceiver(mMockDataReceiver2);
        mTransportProvider.onMessageReceived(SECID, MESSAGE);

        verifyNoMoreInteractions(mMockDataReceiver);
        verify(mMockDataReceiver2, times(1)).onDataReceived(MESSAGE_PAYLOAD);
    }

    @Test
    public void testSendData_succeed() {
        mTransportProvider.sendData(MessageType.COMMAND, MESSAGE_PAYLOAD, mMockSendingDataCallback);
        verify(mMockSendingDataCallback, times(1)).onSuccess();
    }

    @Test
    public void testSendData_failed() {
        mFakeTransportProvider.sendMessageSuccess = false;

        mTransportProvider.sendData(MessageType.COMMAND , MESSAGE_PAYLOAD,
                mMockSendingDataCallback);
        verify(mMockSendingDataCallback, times(1)).onFailure();
    }

    @Test
    public void testSendData_updateDestinationSecid() {
        mTransportProvider.setDestinationSecid(SECID3);
        mTransportProvider.sendData(MessageType.COMMAND, MESSAGE_PAYLOAD, mMockSendingDataCallback);
        verify(mMockSendingDataCallback, times(1)).onSuccess();
        assertThat(mFakeTransportProvider.lastSendMessageSecid).isEqualTo(SECID3);
        assertThat(mFakeTransportProvider.lastSendMessage.toString())
                .isEqualTo(
                        new FiraConnectorMessage(
                                        MessageType.COMMAND,
                                        InstructionCode.DATA_EXCHANGE,
                                        MESSAGE_PAYLOAD)
                                .toString());
    }

    @Test
    public void testOnMessageReceived_secidMismatch() {
        mTransportProvider.registerDataReceiver(mMockDataReceiver);
        mTransportProvider.onMessageReceived(SECID2, MESSAGE);

        verifyZeroInteractions(mMockDataReceiver);
    }

    private void verifyAdminErrorMessageReceive(ErrorType errorType, TerminationReason reason) {
        mTransportProvider.registerDataReceiver(mMockDataReceiver);
        mTransportProvider.onMessageReceived(
                TransportProvider.ADMIN_SECID, new AdminErrorMessage(errorType));
        verify(mMockDataReceiver, never()).onDataReceived(any());
        assertThat(mFakeTransportProvider.lastTerminationReason).isEqualTo(reason);
    }

    @Test
    public void testOnMessageReceived_receiveAdminErrorPacket() {
        verifyAdminErrorMessageReceive(
                ErrorType.DATA_PACKET_LENGTH_OVERFLOW,
                TerminationReason.REMOTE_DEVICE_MESSAGE_ERROR);
        verifyAdminErrorMessageReceive(
                ErrorType.MESSAGE_LENGTH_OVERFLOW, TerminationReason.REMOTE_DEVICE_MESSAGE_ERROR);
        verifyAdminErrorMessageReceive(
                ErrorType.TOO_MANY_CONCURRENT_FRAGMENTED_MESSAGE_SESSIONS,
                TerminationReason.REMOTE_DEVICE_MESSAGE_ERROR);
        verifyAdminErrorMessageReceive(
                ErrorType.SECID_INVALID, TerminationReason.REMOTE_DEVICE_SECID_ERROR);
        verifyAdminErrorMessageReceive(
                ErrorType.SECID_INVALID_FOR_RESPONSE, TerminationReason.REMOTE_DEVICE_SECID_ERROR);
        verifyAdminErrorMessageReceive(
                ErrorType.SECID_BUSY, TerminationReason.REMOTE_DEVICE_SECID_ERROR);
        verifyAdminErrorMessageReceive(
                ErrorType.SECID_PROTOCOL_ERROR, TerminationReason.REMOTE_DEVICE_SECID_ERROR);
        verifyAdminErrorMessageReceive(
                ErrorType.SECID_INTERNAL_ERROR, TerminationReason.REMOTE_DEVICE_SECID_ERROR);
    }

    @Test
    public void testOnMessageReceived_receiveAdminEventPacket() {
        mTransportProvider.registerDataReceiver(mMockDataReceiver);
        mTransportProvider.onMessageReceived(
                TransportProvider.ADMIN_SECID,
                new AdminEventMessage(EventType.CAPABILITIES_CHANGED, new byte[] {}));
        verify(mMockDataReceiver, never()).onDataReceived(any());
        assertThat(mFakeTransportProvider.lastTerminationReason).isNull();
    }

    @Test
    public void testOnMessageReceived_receiveInvalidAdminPacket() {
        mTransportProvider.registerDataReceiver(mMockDataReceiver);
        mTransportProvider.onMessageReceived(
                TransportProvider.ADMIN_SECID,
                new FiraConnectorMessage(
                        MessageType.COMMAND, InstructionCode.DATA_EXCHANGE, MESSAGE_PAYLOAD));
        verify(mMockDataReceiver, never()).onDataReceived(any());
        assertThat(mFakeTransportProvider.lastTerminationReason).isNull();
    }

    @Test
    public void testSentAdminErrorMessage() {
        mTransportProvider.sentAdminErrorMessage(ErrorType.DATA_PACKET_LENGTH_OVERFLOW);

        assertThat(mFakeTransportProvider.lastSendMessageSecid)
                .isEqualTo(TransportProvider.ADMIN_SECID);
        assertThat(mFakeTransportProvider.lastSendMessage.toString())
                .isEqualTo(new AdminErrorMessage(ErrorType.DATA_PACKET_LENGTH_OVERFLOW).toString());
    }

    @Test
    public void testSentAdminErrorMessage_failed() {
        mFakeTransportProvider.sendMessageSuccess = false;
        mTransportProvider.sentAdminErrorMessage(ErrorType.DATA_PACKET_LENGTH_OVERFLOW);

        assertThat(mFakeTransportProvider.lastSendMessageSecid).isEqualTo(-1);
        assertThat(mFakeTransportProvider.lastSendMessage).isNull();
    }

    @Test
    public void testSentAdminEventMessage() {
        mTransportProvider.sentAdminEventMessage(EventType.CAPABILITIES_CHANGED, new byte[] {});

        assertThat(mFakeTransportProvider.lastSendMessageSecid)
                .isEqualTo(TransportProvider.ADMIN_SECID);
        assertThat(mFakeTransportProvider.lastSendMessage.toString())
                .isEqualTo(
                        new AdminEventMessage(EventType.CAPABILITIES_CHANGED, new byte[] {})
                                .toString());
    }

    @Test
    public void testSentAdminEventMessage_failed() {
        mFakeTransportProvider.sendMessageSuccess = false;
        mTransportProvider.sentAdminEventMessage(EventType.CAPABILITIES_CHANGED, new byte[] {});

        assertThat(mFakeTransportProvider.lastSendMessageSecid).isEqualTo(-1);
        assertThat(mFakeTransportProvider.lastSendMessage).isNull();
    }
}
