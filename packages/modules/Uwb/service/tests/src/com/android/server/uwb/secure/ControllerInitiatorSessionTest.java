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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.test.TestLooper;

import com.android.server.uwb.pm.RunningProfileSessionInfo;
import com.android.server.uwb.secure.csml.CsmlUtil;
import com.android.server.uwb.secure.csml.DispatchResponse;
import com.android.server.uwb.secure.csml.GetDoCommand;
import com.android.server.uwb.secure.csml.UwbCapability;
import com.android.server.uwb.secure.iso7816.CommandApdu;
import com.android.server.uwb.secure.iso7816.ResponseApdu;
import com.android.server.uwb.secure.iso7816.TlvDatum;
import com.android.server.uwb.util.DataTypeConversionUtil;
import com.android.server.uwb.util.ObjectIdentifier;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

public class ControllerInitiatorSessionTest {
    @Mock
    private FiRaSecureChannel mFiRaSecureChannel;
    @Mock
    private SecureSession.Callback mSecureSessionCallback;

    @Captor
    private ArgumentCaptor<FiRaSecureChannel.SecureChannelCallback> mSecureChannelCallbackCaptor;

    private ControllerInitiatorSession mControllerInitiatorSession;

    private final TestLooper mTestLooper = new TestLooper();

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        RunningProfileSessionInfo runningProfileSessionInfo =
                new RunningProfileSessionInfo.Builder(mock(UwbCapability.class),
                        mock(ObjectIdentifier.class)).build();

        mControllerInitiatorSession = new ControllerInitiatorSession(
                mTestLooper.getLooper(), mFiRaSecureChannel, mSecureSessionCallback,
                runningProfileSessionInfo);

        mControllerInitiatorSession.startSession();

        verify(mFiRaSecureChannel).init(mSecureChannelCallbackCaptor.capture());
    }

    private void getControleeInfo() {
        ArgumentCaptor<FiRaSecureChannel.ExternalRequestCallback> externalRequestCallbackCaptor =
                ArgumentCaptor.forClass(FiRaSecureChannel.ExternalRequestCallback.class);
        when(mFiRaSecureChannel.isEstablished()).thenReturn(true);

        mSecureChannelCallbackCaptor.getValue().onEstablished(Optional.empty());
        ArgumentCaptor<byte[]> tunnelDataCaptor = ArgumentCaptor.forClass(byte[].class);

        verify(mFiRaSecureChannel).tunnelToRemoteDevice(
                tunnelDataCaptor.capture(), externalRequestCallbackCaptor.capture());
        assertThat(externalRequestCallbackCaptor.getValue()).isNotNull();
        assertThat(DataTypeConversionUtil.byteArrayToHexString(tunnelDataCaptor.getValue()))
                .contains("BF70"); // controlee info do tag
        assertThat(DataTypeConversionUtil.byteArrayToHexString(tunnelDataCaptor.getValue()))
                .contains("00CB3FFF");  // get do command cla|ins|p1|p2

        externalRequestCallbackCaptor.getValue().onSuccess(new byte[0]);
    }

    @Test
    public void onSecureChannelEstablishedPutControleeInfoSuccess() {
        getControleeInfo();
        mTestLooper.moveTimeForward(2000);

        // timeout callback
        assertThat(mTestLooper.nextMessage().getCallback()).isNotNull();
    }

    @Test
    public void onSecureChannelEstablishedPutControleeInfoTimeOut() {
        getControleeInfo();

        mTestLooper.moveTimeForward(2000);
        mTestLooper.dispatchNext(); // timeout callback

        verify(mFiRaSecureChannel).terminateLocally();
        verify(mSecureSessionCallback).onSessionAborted();
    }

    private byte[] getTerminateRemoteSessionData() {
        TlvDatum terminateSessionDo = CsmlUtil.constructTerminateSessionGetDoTlv();
        return GetDoCommand.build(terminateSessionDo).getCommandApdu().getEncoded();
    }

    @Test
    public void onSecureChannelEstablishedGetControleeInfoFail() {
        ArgumentCaptor<FiRaSecureChannel.ExternalRequestCallback> externalRequestCallbackCaptor =
                ArgumentCaptor.forClass(FiRaSecureChannel.ExternalRequestCallback.class);

        mSecureChannelCallbackCaptor.getValue().onEstablished(Optional.empty());

        verify(mFiRaSecureChannel).tunnelToRemoteDevice(
                any(), externalRequestCallbackCaptor.capture());
        assertThat(externalRequestCallbackCaptor.getValue()).isNotNull();

        externalRequestCallbackCaptor.getValue().onFailure();

        verify(mSecureSessionCallback).onSessionAborted();
    }

    @Test
    public void getControleeInfoErrorResponse() {
        getControleeInfo();
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "710780018181029000"); // outbound to host without ControleeInfo data
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);
        mTestLooper.dispatchAll();

        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mFiRaSecureChannel, times(2)).tunnelToRemoteDevice(dataCaptor.capture(), any());

        assertThat(dataCaptor.getValue()).isEqualTo(getTerminateRemoteSessionData());
        verify(mSecureSessionCallback).onSessionAborted();
    }

    private void doGetControleeInfoResponse() {
        getControleeInfo();
        // Response of getControleeInfo
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "710A8001818105BF70020A0B"); // controlee info DO (BF70xxxx)
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);
        mTestLooper.dispatchAll();
        ArgumentCaptor<FiRaSecureChannel.ExternalRequestCallback> externalRequestCallbackCaptor =
                ArgumentCaptor.forClass(FiRaSecureChannel.ExternalRequestCallback.class);
        verify(mFiRaSecureChannel, times(2)).tunnelToRemoteDevice(
                any(), externalRequestCallbackCaptor.capture());
        externalRequestCallbackCaptor.getValue().onSuccess(new byte[0]);
    }

    @Test
    public void getControleeInfoSuccessResponse() {
        doGetControleeInfoResponse();

        ArgumentCaptor<byte[]> tunnelDataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mFiRaSecureChannel, times(2))
                .tunnelToRemoteDevice(tunnelDataCaptor.capture(), any());

        assertThat(DataTypeConversionUtil.byteArrayToHexString(tunnelDataCaptor.getValue()))
                .contains("00DB3FFF"); // PUT DO command
        assertThat(DataTypeConversionUtil.byteArrayToHexString(tunnelDataCaptor.getValue()))
                .contains("BF78"); // SESSION DATA DO TAG
    }

    @Test
    public void putControleeSessionDataSuccessResponseWithNotification() {
        doGetControleeInfoResponse();
        // response of put SessionData to controlee
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "711380018181029000E10A80010081010282020101");
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);
        mTestLooper.dispatchAll();

        verify(mSecureSessionCallback).onSessionDataReady(eq(1), any(), eq(false));
    }

    @Test
    public void putControleeSessionDataSuccessResponseWithoutNotification() {
        doGetControleeInfoResponse();
        // response of put ControleeSessionData
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "710780018181029000");
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);
        mTestLooper.dispatchAll();
        ArgumentCaptor<CommandApdu> putSessionDataCmdCaptor =
                ArgumentCaptor.forClass(CommandApdu.class);
        ArgumentCaptor<FiRaSecureChannel.ExternalRequestCallback> localExternalRequestCbCaptor =
                ArgumentCaptor.forClass(FiRaSecureChannel.ExternalRequestCallback.class);
        verify(mFiRaSecureChannel).sendLocalCommandApdu(
                putSessionDataCmdCaptor.capture(),
                localExternalRequestCbCaptor.capture());

        localExternalRequestCbCaptor.getValue().onSuccess(new byte[0]);

        assertThat(putSessionDataCmdCaptor.getValue().getIns())
                .isEqualTo((byte) 0xDB);
        assertThat(putSessionDataCmdCaptor.getValue().getP1()).isEqualTo((byte) 0x3F);
        assertThat(putSessionDataCmdCaptor.getValue().getP2()).isEqualTo((byte) 0xFF);
        assertThat(DataTypeConversionUtil.byteArrayToHexString(
                putSessionDataCmdCaptor.getValue().getCommandData())).startsWith("BF78");

        verify(mSecureSessionCallback).onSessionDataReady(anyInt(), any(), eq(false));
    }

    @Test
    public void failToPutSessionDataToLocalFiRaApplet() {
        doGetControleeInfoResponse();
        // response of put ControleeSessionData
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "710780018181029000");
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);
        mTestLooper.dispatchAll();
        ArgumentCaptor<CommandApdu> putSessionDataCmdCaptor =
                ArgumentCaptor.forClass(CommandApdu.class);
        ArgumentCaptor<FiRaSecureChannel.ExternalRequestCallback> localExternalRequestCbCaptor =
                ArgumentCaptor.forClass(FiRaSecureChannel.ExternalRequestCallback.class);
        verify(mFiRaSecureChannel).sendLocalCommandApdu(
                putSessionDataCmdCaptor.capture(),
                localExternalRequestCbCaptor.capture());

        localExternalRequestCbCaptor.getValue().onFailure();

        assertThat(putSessionDataCmdCaptor.getValue().getIns())
                .isEqualTo((byte) 0xDB);
        assertThat(putSessionDataCmdCaptor.getValue().getP1()).isEqualTo((byte) 0x3F);
        assertThat(putSessionDataCmdCaptor.getValue().getP2()).isEqualTo((byte) 0xFF);
        assertThat(DataTypeConversionUtil.byteArrayToHexString(
                putSessionDataCmdCaptor.getValue().getCommandData())).startsWith("BF78");

        verify(mSecureSessionCallback).onSessionAborted();
    }

    @Test
    public void putControleeSessionDataResponseDataWrongResponseFromRemote() {
        doGetControleeInfoResponse();
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "710781018081029001");
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);
        mTestLooper.dispatchAll();

        verify(mSecureSessionCallback).onSessionAborted();
    }

    @Test
    public void abortSessionNotification() {
        doGetControleeInfoResponse();

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
    public void dispatchCommandFailureFromApplet() {
        doGetControleeInfoResponse();

        mSecureChannelCallbackCaptor.getValue().onDispatchCommandFailure();
        mTestLooper.dispatchAll();

        verify(mFiRaSecureChannel).terminateLocally();
        verify(mSecureSessionCallback).onSessionAborted();
    }
}
