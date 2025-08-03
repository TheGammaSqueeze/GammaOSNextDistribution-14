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

package com.android.server.uwb.secure;

import static com.android.server.uwb.secure.FiRaSecureChannel.CMD_SEND_OOB_DATA;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.test.TestLooper;

import com.android.server.uwb.discovery.Transport;
import com.android.server.uwb.pm.RunningProfileSessionInfo;
import com.android.server.uwb.secure.csml.ControleeInfo;
import com.android.server.uwb.secure.csml.FiRaCommand;
import com.android.server.uwb.secure.csml.SelectAdfCommand;
import com.android.server.uwb.secure.csml.SwapInAdfCommand;
import com.android.server.uwb.secure.csml.UwbCapability;
import com.android.server.uwb.secure.iso7816.CommandApdu;
import com.android.server.uwb.secure.iso7816.ResponseApdu;
import com.android.server.uwb.secure.iso7816.StatusWord;
import com.android.server.uwb.secure.omapi.OmapiConnection;
import com.android.server.uwb.util.DataTypeConversionUtil;
import com.android.server.uwb.util.ObjectIdentifier;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;

public class ResponderSecureChannelTest {
    private static final ObjectIdentifier PROVISIONED_ADF_OID =
            ObjectIdentifier.fromBytes(new byte[] {(byte) 0x01});

    @Mock
    private SecureElementChannel mSecureElementChannel;
    @Mock
    private Transport mTransport;

    private TestLooper mTestLooper = new TestLooper();

    @Mock
    FiRaSecureChannel.SecureChannelCallback mSecureChannelCallback;

    private ResponderSecureChannel mResponderSecureChannel;

    @Captor
    private ArgumentCaptor<OmapiConnection.InitCompletionCallback>
            mInitCompletionCallbackCaptor;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    private void doInit(RunningProfileSessionInfo runningProfileSessionInfo) {
        mResponderSecureChannel = new ResponderSecureChannel(mSecureElementChannel,
                mTransport,
                mTestLooper.getLooper(),
                runningProfileSessionInfo);
        doNothing().when(mSecureElementChannel).init(mInitCompletionCallbackCaptor.capture());

        mResponderSecureChannel.init(mSecureChannelCallback);
        mTestLooper.dispatchNext();
        mInitCompletionCallbackCaptor.getValue().onInitCompletion();
    }

    @Test
    public void init() {
        doInit(mock(RunningProfileSessionInfo.class));

        assertThat(mTestLooper.nextMessage()).isNull();
        assertThat(mResponderSecureChannel.getStatus())
                .isEqualTo(FiRaSecureChannel.Status.INITIALIZED);
    }

    @Test
    public void openChannelSuccess() throws IOException {
        RunningProfileSessionInfo runningProfileSessionInfo =
                new RunningProfileSessionInfo.Builder(
                        mock(UwbCapability.class), mock(ObjectIdentifier.class))
                        .build();
        doInit(runningProfileSessionInfo);
        when(mSecureElementChannel.openChannelWithResponse())
                .thenReturn(ResponseApdu.SW_SUCCESS_APDU);
        // select command trigger
        CommandApdu selectCommand = CommandApdu.builder(0x00, 0xA4, 0x04, 0x00).build();

        mResponderSecureChannel.processRemoteCommandOrResponse(selectCommand.getEncoded());
        mTestLooper.dispatchNext();

        assertThat(mResponderSecureChannel.getStatus()).isEqualTo(
                FiRaSecureChannel.Status.CHANNEL_OPENED);
        assertThat(mTestLooper.nextMessage().what).isEqualTo(CMD_SEND_OOB_DATA);
    }

    @Test
    public void remoteSelectAdfWithMatchedAdfOid() throws IOException {
        RunningProfileSessionInfo runningProfileSessionInfo =
                new RunningProfileSessionInfo.Builder(
                        mock(UwbCapability.class), PROVISIONED_ADF_OID)
                        .build();
        doInit(runningProfileSessionInfo);
        when(mSecureElementChannel.openChannelWithResponse())
                .thenReturn(ResponseApdu.SW_SUCCESS_APDU);
        // select command trigger
        CommandApdu selectCommand = CommandApdu.builder(0x00, 0xA4, 0x04, 0x00).build();

        mResponderSecureChannel.processRemoteCommandOrResponse(selectCommand.getEncoded());
        mTestLooper.dispatchAll();

        when(mSecureElementChannel.isOpened()).thenReturn(true);
        SelectAdfCommand selectAdfCommand = SelectAdfCommand.build(PROVISIONED_ADF_OID);
        byte[] responseData = DataTypeConversionUtil.hexStringToByteArray(
                "711280018081029000E109800100810100820101"); // ADF SELECTED notification
        when(mSecureElementChannel.transmit(any(FiRaCommand.class)))
                .thenReturn(ResponseApdu.fromDataAndStatusWord(
                        responseData, StatusWord.SW_NO_ERROR.toInt()));
        mResponderSecureChannel.processRemoteCommandOrResponse(
                selectAdfCommand.getCommandApdu().getEncoded());

        assertThat(mResponderSecureChannel.getStatus()).isEqualTo(
                FiRaSecureChannel.Status.ADF_SELECTED);
    }

    @Test
    public void remoteSelectAdfWithMismatchedAdfOid() throws IOException {
        RunningProfileSessionInfo runningProfileSessionInfo =
                new RunningProfileSessionInfo.Builder(
                        mock(UwbCapability.class), PROVISIONED_ADF_OID)
                        .build();
        doInit(runningProfileSessionInfo);
        when(mSecureElementChannel.openChannelWithResponse())
                .thenReturn(ResponseApdu.SW_SUCCESS_APDU);
        // select command trigger
        CommandApdu selectCommand = CommandApdu.builder(0x00, 0xA4, 0x04, 0x00).build();

        mResponderSecureChannel.processRemoteCommandOrResponse(selectCommand.getEncoded());
        mTestLooper.dispatchNext();

        when(mSecureElementChannel.isOpened()).thenReturn(true);
        SelectAdfCommand selectAdfCommand = SelectAdfCommand.build(PROVISIONED_ADF_OID);
        byte[] responseData = DataTypeConversionUtil.hexStringToByteArray(
                "711280018081029000E109800100810100820102"); // ADF SELECTED notification
        when(mSecureElementChannel.transmit(any(FiRaCommand.class)))
                .thenReturn(ResponseApdu.fromDataAndStatusWord(
                        responseData, StatusWord.SW_NO_ERROR.toInt()));
        mResponderSecureChannel.processRemoteCommandOrResponse(
                selectAdfCommand.getCommandApdu().getEncoded());

        assertThat(mResponderSecureChannel.getStatus()).isEqualTo(
                FiRaSecureChannel.Status.CHANNEL_OPENED);
        verify(mSecureChannelCallback)
                .onSetUpError(eq(FiRaSecureChannel.SetupError.ADF_NOT_MATCHED));
    }

    @Test
    public void openChannelFail() throws IOException {
        doInit(mock(RunningProfileSessionInfo.class));
        when(mSecureElementChannel.openChannelWithResponse())
                .thenReturn(ResponseApdu.SW_FILE_NOT_FOUND_APDU);
        // select command trigger
        CommandApdu selectCommand = CommandApdu.builder(0x00, 0xA4, 0x04, 0x00).build();

        mResponderSecureChannel.processRemoteCommandOrResponse(selectCommand.getEncoded());
        mTestLooper.dispatchNext();

        assertThat(mResponderSecureChannel.getStatus()).isEqualTo(
                FiRaSecureChannel.Status.INITIALIZED);
        assertThat(mTestLooper.nextMessage().what).isEqualTo(CMD_SEND_OOB_DATA);
        verify(mSecureChannelCallback)
                .onSetUpError(eq(FiRaSecureChannel.SetupError.OPEN_SE_CHANNEL));
    }

    @Test
    public void openChannelWithException() throws IOException {
        doInit(mock(RunningProfileSessionInfo.class));
        when(mSecureElementChannel.openChannelWithResponse())
                .thenThrow(new IOException());
        // select command trigger
        CommandApdu selectCommand = CommandApdu.builder(0x00, 0xA4, 0x04, 0x00).build();

        mResponderSecureChannel.processRemoteCommandOrResponse(selectCommand.getEncoded());
        mTestLooper.dispatchNext();

        assertThat(mResponderSecureChannel.getStatus()).isEqualTo(
                FiRaSecureChannel.Status.INITIALIZED);
        assertThat(mTestLooper.nextMessage().what).isEqualTo(CMD_SEND_OOB_DATA);
        verify(mSecureChannelCallback)
                .onSetUpError(eq(FiRaSecureChannel.SetupError.OPEN_SE_CHANNEL));
    }

    @Test
    public void openChannelSwapInAdfFailed() throws IOException {
        ControleeInfo mockControleeInfo = mock(ControleeInfo.class);
        when(mockControleeInfo.toBytes()).thenReturn(new byte[0]);
        RunningProfileSessionInfo runningProfileSessionInfo =
                new RunningProfileSessionInfo.Builder(mock(UwbCapability.class),
                        ObjectIdentifier.fromBytes(new byte[] { (byte) 0x01 }))
                        .setSecureBlob(new byte[0])
                        .setControleeInfo(mockControleeInfo)
                        .build();
        doInit(runningProfileSessionInfo);
        when(mSecureElementChannel.openChannelWithResponse())
                .thenReturn(ResponseApdu.SW_SUCCESS_APDU);
        when(mSecureElementChannel.transmit(any(SwapInAdfCommand.class)))
                .thenReturn(ResponseApdu.SW_CONDITIONS_NOT_SATISFIED_APDU);
        // select command trigger
        CommandApdu selectCommand = CommandApdu.builder(0x00, 0xA4, 0x04, 0x00).build();

        mResponderSecureChannel.processRemoteCommandOrResponse(selectCommand.getEncoded());
        mTestLooper.dispatchNext();

        assertThat(mResponderSecureChannel.getStatus()).isEqualTo(
                FiRaSecureChannel.Status.INITIALIZED);
        assertThat(mTestLooper.nextMessage().what).isEqualTo(CMD_SEND_OOB_DATA);
        verify(mSecureElementChannel).closeChannel();
        verify(mSecureChannelCallback)
                .onSetUpError(eq(FiRaSecureChannel.SetupError.OPEN_SE_CHANNEL));
    }

    @Test(expected = IllegalStateException.class)
    public void tunnelDataToRemoteDevice() {
        doInit(mock(RunningProfileSessionInfo.class));
        mResponderSecureChannel.tunnelToRemoteDevice(new byte[0], mock(
                FiRaSecureChannel.ExternalRequestCallback.class));
    }
}
