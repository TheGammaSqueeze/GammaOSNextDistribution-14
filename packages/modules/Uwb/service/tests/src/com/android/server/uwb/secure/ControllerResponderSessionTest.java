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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.test.TestLooper;

import com.android.server.uwb.pm.RunningProfileSessionInfo;
import com.android.server.uwb.secure.csml.DispatchResponse;
import com.android.server.uwb.secure.csml.FiRaCommand;
import com.android.server.uwb.secure.csml.GetDoCommand;
import com.android.server.uwb.secure.csml.PutDoCommand;
import com.android.server.uwb.secure.csml.UwbCapability;
import com.android.server.uwb.secure.iso7816.ResponseApdu;
import com.android.server.uwb.util.DataTypeConversionUtil;
import com.android.server.uwb.util.ObjectIdentifier;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

public class ControllerResponderSessionTest {
    private static final int DEFAULT_SESSION_ID = 1;

    @Mock
    private FiRaSecureChannel mFiRaSecureChannel;
    @Mock
    private SecureSession.Callback mSecureSessionCallback;

    @Captor
    private ArgumentCaptor<FiRaSecureChannel.SecureChannelCallback> mSecureChannelCallbackCaptor;

    private ControllerResponderSession mControllerResponderSession;

    private final TestLooper mTestLooper = new TestLooper();

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        RunningProfileSessionInfo runningProfileSessionInfo =
                new RunningProfileSessionInfo.Builder(
                        mock(UwbCapability.class), mock(ObjectIdentifier.class)).build();
        mControllerResponderSession = new ControllerResponderSession(
                mTestLooper.getLooper(), mFiRaSecureChannel, mSecureSessionCallback,
                runningProfileSessionInfo);

        mControllerResponderSession.startSession();

        verify(mFiRaSecureChannel).init(mSecureChannelCallbackCaptor.capture());

    }

    @Test
    public void onSetupError() {
        mSecureChannelCallbackCaptor.getValue()
                .onSetUpError(FiRaSecureChannel.SetupError.OPEN_SE_CHANNEL);

        verify(mFiRaSecureChannel).cleanUpTerminatedOrAbortedSession();
        verify(mSecureSessionCallback).onSessionAborted();
    }

    @Test
    public void onTerminated() {
        mSecureChannelCallbackCaptor.getValue()
                .onTerminated(/*withError=*/ false);

        verify(mSecureSessionCallback).onSessionTerminated();
    }

    @Test
    public void terminateSession() {
        mControllerResponderSession.terminateSession();
        mTestLooper.dispatchAll();

        verify(mFiRaSecureChannel).terminateLocally();
    }

    @Test
    public void abortSessionNotification() {
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "71038001FF"); // transaction complete with errors
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);
        mTestLooper.dispatchAll();

        verify(mFiRaSecureChannel).cleanUpTerminatedOrAbortedSession();
        verify(mSecureSessionCallback).onSessionAborted();
    }

    @Test
    public void controleeInfoAvailableNotificationContainsData() {
        mSecureChannelCallbackCaptor.getValue().onEstablished(Optional.of(DEFAULT_SESSION_ID));
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "711780018081029000E10E8001008101038206BF7003800101");
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);

        ArgumentCaptor<FiRaCommand> fiRaCommandCaptor = ArgumentCaptor.forClass(FiRaCommand.class);

        verify(mFiRaSecureChannel).sendLocalFiRaCommand(
                fiRaCommandCaptor.capture(), any());
        assertThat(fiRaCommandCaptor.getValue().getCommandApdu().getIns()).isEqualTo((byte) 0xDB);
        assertThat(fiRaCommandCaptor.getValue().getCommandApdu().getP1()).isEqualTo((byte) 0x3F);
        assertThat(fiRaCommandCaptor.getValue().getCommandApdu().getP2()).isEqualTo((byte) 0xFF);
        assertThat(DataTypeConversionUtil.byteArrayToHexString(
                fiRaCommandCaptor.getValue().getCommandApdu().getEncoded())).contains("BF78");

        // rds available
        byte[] rdsData = DataTypeConversionUtil.hexStringToByteArray(
                "711380018081029000E10A80010081010282020101");
        ResponseApdu rdsResponseApdu = ResponseApdu.fromDataAndStatusWord(rdsData, 0x9000);
        DispatchResponse rdsDispatchResponse = DispatchResponse.fromResponseApdu(rdsResponseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(rdsDispatchResponse);

        verify(mSecureSessionCallback).onSessionDataReady(
                eq(DEFAULT_SESSION_ID), any(), eq(false));
    }

    @Test
    public void controleeInfoAvailableNotificationContainsDataSendLocalSessionDataFailed() {
        mSecureChannelCallbackCaptor.getValue().onEstablished(Optional.of(DEFAULT_SESSION_ID));
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "711780018081029000E10E8001008101038206BF7003800101");
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);

        ArgumentCaptor<FiRaSecureChannel.ExternalRequestCallback> cbCaptor =
                ArgumentCaptor.forClass(FiRaSecureChannel.ExternalRequestCallback.class);

        verify(mFiRaSecureChannel).sendLocalFiRaCommand(
                any(), cbCaptor.capture());

        cbCaptor.getValue().onFailure();
        verify(mSecureSessionCallback).onSessionAborted();
    }

    @Test
    public void controleeInfoAvailableNotificationDataInApplet() {
        mSecureChannelCallbackCaptor.getValue().onEstablished(Optional.of(DEFAULT_SESSION_ID));
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "710F80018081029000E106800100810103");
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);

        ArgumentCaptor<GetDoCommand> getDoCaptor = ArgumentCaptor.forClass(GetDoCommand.class);
        ArgumentCaptor<FiRaSecureChannel.ExternalRequestCallback> getDoCbCaptor =
                ArgumentCaptor.forClass(FiRaSecureChannel.ExternalRequestCallback.class);

        verify(mFiRaSecureChannel).sendLocalFiRaCommand(
                getDoCaptor.capture(), getDoCbCaptor.capture());
        assertThat(getDoCaptor.getValue().getCommandApdu().getIns()).isEqualTo((byte) 0xCB);
        assertThat(getDoCaptor.getValue().getCommandApdu().getP1()).isEqualTo((byte) 0x3F);
        assertThat(getDoCaptor.getValue().getCommandApdu().getP2()).isEqualTo((byte) 0xFF);
        assertThat(DataTypeConversionUtil.byteArrayToHexString(
                getDoCaptor.getValue().getCommandApdu().getCommandData())).contains("BF70");

        getDoCbCaptor.getValue().onSuccess(
                DataTypeConversionUtil.hexStringToByteArray("BF7003800101"));

        ArgumentCaptor<PutDoCommand> putDoCaptor =
                ArgumentCaptor.forClass(PutDoCommand.class);

        verify(mFiRaSecureChannel, times(2)).sendLocalFiRaCommand(
                putDoCaptor.capture(), any());
        assertThat(putDoCaptor.getValue().getCommandApdu().getIns()).isEqualTo((byte) 0xDB);
        assertThat(putDoCaptor.getValue().getCommandApdu().getP1()).isEqualTo((byte) 0x3F);
        assertThat(putDoCaptor.getValue().getCommandApdu().getP2()).isEqualTo((byte) 0xFF);
        assertThat(DataTypeConversionUtil.byteArrayToHexString(
                putDoCaptor.getValue().getCommandApdu().getEncoded())).contains("BF78");
    }

    @Test
    public void controleeInfoAvailableNotificationFailedToGetDataInApplet() {
        mSecureChannelCallbackCaptor.getValue().onEstablished(Optional.of(DEFAULT_SESSION_ID));
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "710F80018081029000E106800100810103");
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);

        ArgumentCaptor<FiRaSecureChannel.ExternalRequestCallback> getDoCbCaptor =
                ArgumentCaptor.forClass(FiRaSecureChannel.ExternalRequestCallback.class);

        verify(mFiRaSecureChannel).sendLocalFiRaCommand(any(), getDoCbCaptor.capture());

        getDoCbCaptor.getValue().onFailure();

        verify(mSecureSessionCallback).onSessionAborted();
    }

    @Test
    public void rdsAvailableButNoSessionData() {
        mSecureChannelCallbackCaptor.getValue().onEstablished(Optional.of(DEFAULT_SESSION_ID));
        byte[] rdsData = DataTypeConversionUtil.hexStringToByteArray(
                "711380018081029000E10A80010081010282020101");
        ResponseApdu rdsResponseApdu = ResponseApdu.fromDataAndStatusWord(rdsData, 0x9000);
        DispatchResponse rdsDispatchResponse = DispatchResponse.fromResponseApdu(rdsResponseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(rdsDispatchResponse);

        verify(mSecureSessionCallback).onSessionAborted();
    }
}
