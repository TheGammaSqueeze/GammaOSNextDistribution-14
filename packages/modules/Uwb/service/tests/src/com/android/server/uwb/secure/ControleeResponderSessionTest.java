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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.os.test.TestLooper;

import com.android.server.uwb.pm.RunningProfileSessionInfo;
import com.android.server.uwb.secure.csml.ControleeInfo;
import com.android.server.uwb.secure.csml.DispatchResponse;
import com.android.server.uwb.secure.csml.FiRaCommand;
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

public class ControleeResponderSessionTest {
    @Mock
    private FiRaSecureChannel mFiRaSecureChannel;
    @Mock
    private SecureSession.Callback mSecureSessionCallback;
    @Mock
    private RunningProfileSessionInfo mRunningProfileSessionInfo;

    @Captor
    private ArgumentCaptor<FiRaSecureChannel.SecureChannelCallback> mSecureChannelCallbackCaptor;

    private ControleeResponderSession mControleeResponderSession;

    private final TestLooper mTestLooper = new TestLooper();

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    private void doInit(RunningProfileSessionInfo runningProfileSessionInfo) {

        mControleeResponderSession = new ControleeResponderSession(
                mTestLooper.getLooper(), mFiRaSecureChannel, mSecureSessionCallback,
                runningProfileSessionInfo);

        mControleeResponderSession.startSession();

        verify(mFiRaSecureChannel).init(mSecureChannelCallbackCaptor.capture());
    }

    private void doInit() {
        doInit(mRunningProfileSessionInfo);
    }

    @Test
    public void onSetupError() {
        doInit();
        mSecureChannelCallbackCaptor.getValue()
                .onSetUpError(FiRaSecureChannel.SetupError.OPEN_SE_CHANNEL);

        verify(mFiRaSecureChannel).cleanUpTerminatedOrAbortedSession();
        verify(mSecureSessionCallback).onSessionAborted();
    }

    @Test
    public void onSessionEstablishedPutControleeInfoFail() {
        RunningProfileSessionInfo runningProfileSessionInfo =
                new RunningProfileSessionInfo.Builder(
                        mock(UwbCapability.class), mock(ObjectIdentifier.class))
                        .setControleeInfo(new ControleeInfo.Builder().build())
                        .build();
        doInit(runningProfileSessionInfo);
        mSecureChannelCallbackCaptor.getValue().onEstablished(Optional.empty());

        ArgumentCaptor<FiRaCommand> cmdCaptor = ArgumentCaptor.forClass(FiRaCommand.class);
        ArgumentCaptor<FiRaSecureChannel.ExternalRequestCallback> cbCaptor =
                ArgumentCaptor.forClass(FiRaSecureChannel.ExternalRequestCallback.class);

        verify(mFiRaSecureChannel).sendLocalFiRaCommand(cmdCaptor.capture(), cbCaptor.capture());
        assertThat(cmdCaptor.getValue().getCommandApdu().getIns()).isEqualTo((byte) 0xDB);
        assertThat(cmdCaptor.getValue().getCommandApdu().getP1()).isEqualTo((byte) 0x3F);
        assertThat(cmdCaptor.getValue().getCommandApdu().getP2()).isEqualTo((byte) 0xFF);

        cbCaptor.getValue().onFailure();
        mTestLooper.dispatchAll();
        verify(mFiRaSecureChannel).terminateLocally();
        verify(mSecureSessionCallback).onSessionAborted();
    }

    @Test
    public void onTerminated() {
        doInit();
        mSecureChannelCallbackCaptor.getValue()
                .onTerminated(/*withError=*/ false);

        verify(mSecureSessionCallback).onSessionTerminated();
    }

    @Test
    public void terminateSession() {
        doInit();
        mControleeResponderSession.terminateSession();
        mTestLooper.dispatchAll();

        verify(mFiRaSecureChannel).terminateLocally();
    }

    @Test
    public void abortSessionNotification() {
        doInit();
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
    public void abortSessionWithWrongDispatchResponseStatusWord() {
        doInit();
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(new byte[0], 0x9032);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);
        mTestLooper.dispatchAll();

        verify(mFiRaSecureChannel).terminateLocally();
        verify(mSecureSessionCallback).onSessionAborted();
    }

    @Test
    public void rdsAvailableNotificationWithSessionData() {
        doInit();
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "711B80018181029000E112800100810102820A010107BF780480020101");
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);

        verify(mSecureSessionCallback).onSessionDataReady(anyInt(), any(), eq(false));
    }

    @Test
    public void rdsAvailableNotificationWithSessionDataInApplet() {
        doInit();
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "711380018181029000E10A80010081010282020101");
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);

        ArgumentCaptor<FiRaSecureChannel.ExternalRequestCallback> externalRequestCbCaptor =
                ArgumentCaptor.forClass(FiRaSecureChannel.ExternalRequestCallback.class);
        verify(mFiRaSecureChannel).sendLocalFiRaCommand(any(), externalRequestCbCaptor.capture());

        externalRequestCbCaptor.getValue().onSuccess(
                DataTypeConversionUtil.hexStringToByteArray("BF780480020101"));

        verify(mSecureSessionCallback).onSessionDataReady(anyInt(), any(), eq(false));
    }

    @Test
    public void failedToGetSessionDataFromApplet() {
        doInit();
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "711380018181029000E10A80010081010282020101");
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);

        ArgumentCaptor<FiRaSecureChannel.ExternalRequestCallback> externalRequestCbCaptor =
                ArgumentCaptor.forClass(FiRaSecureChannel.ExternalRequestCallback.class);
        verify(mFiRaSecureChannel).sendLocalFiRaCommand(any(), externalRequestCbCaptor.capture());

        externalRequestCbCaptor.getValue().onFailure();

        verify(mSecureSessionCallback).onSessionAborted();
    }

    @Test
    public void outboundDataToRemote() {
        doInit();
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "710780018081029000");
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);

        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);

        verify(mFiRaSecureChannel).sendRawDataToRemote(dataCaptor.capture());
        assertThat(dataCaptor.getValue()).isEqualTo(new byte[] {(byte) 0x90, (byte) 0x00});
    }
}
