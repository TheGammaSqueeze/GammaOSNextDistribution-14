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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.test.TestLooper;

import com.android.server.uwb.pm.RunningProfileSessionInfo;
import com.android.server.uwb.secure.csml.ControleeInfo;
import com.android.server.uwb.secure.csml.CsmlUtil;
import com.android.server.uwb.secure.csml.DispatchResponse;
import com.android.server.uwb.secure.csml.FiRaCommand;
import com.android.server.uwb.secure.csml.GetDoCommand;
import com.android.server.uwb.secure.csml.UwbCapability;
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

public class ControleeInitiatorSessionTest {
    private static final int UNIQUE_SESSION_ID = 1;
    @Mock
    private FiRaSecureChannel mFiRaSecureChannel;
    @Mock
    private SecureSession.Callback mSecureSessionCallback;


    @Captor
    private ArgumentCaptor<FiRaSecureChannel.SecureChannelCallback> mSecureChannelCallbackCaptor;

    private ControleeInitiatorSession mControleeInitiatorSession;

    private final TestLooper mTestLooper = new TestLooper();

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        RunningProfileSessionInfo runningProfileSessionInfo =
                new RunningProfileSessionInfo.Builder(mock(UwbCapability.class), mock(
                        ObjectIdentifier.class))
                        .setControleeInfo(new ControleeInfo.Builder().build())
                        .build();
        mControleeInitiatorSession = new ControleeInitiatorSession(
                mTestLooper.getLooper(), mFiRaSecureChannel, mSecureSessionCallback,
                runningProfileSessionInfo);

        mControleeInitiatorSession.startSession();

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

    private void putControleeInfo() {
        ArgumentCaptor<FiRaSecureChannel.ExternalRequestCallback> externalRequestCallbackCaptor =
                ArgumentCaptor.forClass(FiRaSecureChannel.ExternalRequestCallback.class);
        when(mFiRaSecureChannel.isEstablished()).thenReturn(true);

        mSecureChannelCallbackCaptor.getValue().onEstablished(Optional.empty());

        ArgumentCaptor<byte[]> controleeInfoCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mFiRaSecureChannel).tunnelToRemoteDevice(
                controleeInfoCaptor.capture(), externalRequestCallbackCaptor.capture());
        assertThat(externalRequestCallbackCaptor.getValue()).isNotNull();
        assertThat(DataTypeConversionUtil.byteArrayToHexString(controleeInfoCaptor.getValue()))
                .startsWith("00DB3FFF"); // PUT DO COMMAND
        assertThat(DataTypeConversionUtil.byteArrayToHexString(controleeInfoCaptor.getValue()))
                .contains("BF70"); // controlee info DO tag

        // tunnel the put controlee info cmd.
        externalRequestCallbackCaptor.getValue().onSuccess(new byte[0]);
    }

    @Test
    public void onSecureChannelEstablishedPutControleeInfoSuccess() {
        putControleeInfo();
        mTestLooper.moveTimeForward(2000);

        // timeout callback
        assertThat(mTestLooper.nextMessage().getCallback()).isNotNull();
    }

    @Test
    public void onSecureChannelEstablishedPutControleeInfoTimeOut() {
        putControleeInfo();

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
    public void onSecureChannelEstablishedPutControleeInfoFail() {
        ArgumentCaptor<FiRaSecureChannel.ExternalRequestCallback> externalRequestCallbackCaptor =
                ArgumentCaptor.forClass(FiRaSecureChannel.ExternalRequestCallback.class);

        mSecureChannelCallbackCaptor.getValue().onEstablished(Optional.of(UNIQUE_SESSION_ID));

        verify(mFiRaSecureChannel).tunnelToRemoteDevice(
                any(), externalRequestCallbackCaptor.capture());
        assertThat(externalRequestCallbackCaptor.getValue()).isNotNull();

        externalRequestCallbackCaptor.getValue().onFailure();

        verify(mSecureSessionCallback).onSessionAborted();
    }

    private void getControleeSessionData() {
        putControleeInfo();
        // get DispatchResponse for Tunnel(PutControleeInfo)
        byte[] data = DataTypeConversionUtil.hexStringToByteArray("710780018181029000");
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);
        mTestLooper.dispatchAll();
        ArgumentCaptor<FiRaSecureChannel.ExternalRequestCallback> externalRequestCallbackCaptor =
                ArgumentCaptor.forClass(FiRaSecureChannel.ExternalRequestCallback.class);
        verify(mFiRaSecureChannel, times(2)).tunnelToRemoteDevice(
                any(), externalRequestCallbackCaptor.capture());

        // tunnel(GetSessionData)
        externalRequestCallbackCaptor.getValue().onSuccess(new byte[0]);
    }

    @Test
    public void tunnelCorrectGetSessionData() {
        getControleeSessionData();

        ArgumentCaptor<byte[]> tunnelDataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mFiRaSecureChannel, times(2))
                .tunnelToRemoteDevice(tunnelDataCaptor.capture(), any());

        assertThat(DataTypeConversionUtil.byteArrayToHexString(tunnelDataCaptor.getValue()))
                .startsWith("00CB3FFF"); // GET DO command
        assertThat(DataTypeConversionUtil.byteArrayToHexString(tunnelDataCaptor.getValue()))
                .contains("BF78");  // SessionData DO tag
    }

    @Test
    public void putControleeInfoErrorResponse() {
        putControleeInfo();
        byte[] data = DataTypeConversionUtil.hexStringToByteArray("710780018181029090");
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);
        mTestLooper.dispatchAll();
        ArgumentCaptor<byte[]> tunnelDataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mFiRaSecureChannel, times(2))
                .tunnelToRemoteDevice(tunnelDataCaptor.capture(), any());

        assertThat(tunnelDataCaptor.getValue()).isEqualTo(getTerminateRemoteSessionData());
        verify(mSecureSessionCallback).onSessionAborted();
    }

    @Test
    public void getControleeSessionDataSuccessResponseWithRdsAvailable() {
        getControleeSessionData();
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "71188001818107BF780480020101E10A80010081010282020101");
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);
        mTestLooper.dispatchAll();

        // send PutSessionData to local applet.
        verify(mFiRaSecureChannel, never()).sendLocalFiRaCommand(
                any(), any());

        verify(mSecureSessionCallback)
                .onSessionDataReady(eq(UNIQUE_SESSION_ID), any(), eq(false));
    }

    @Test
    public void successToGetSessionDataFromLocalAppletAsRdsAvailable() {
        getControleeSessionData();
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "711380018181029000E10A80010081010282020101");
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);
        mTestLooper.dispatchAll();

        ArgumentCaptor<FiRaSecureChannel.ExternalRequestCallback> cbCaptor =
                ArgumentCaptor.forClass(FiRaSecureChannel.ExternalRequestCallback.class);
        ArgumentCaptor<FiRaCommand> fiRaCommandCaptor =
                ArgumentCaptor.forClass(FiRaCommand.class);
        // send PutSessionData to local applet.
        verify(mFiRaSecureChannel).sendLocalFiRaCommand(
                fiRaCommandCaptor.capture(), cbCaptor.capture());
        assertThat(fiRaCommandCaptor.getValue().getCommandApdu().getIns()).isEqualTo((byte) 0xCB);
        assertThat(fiRaCommandCaptor.getValue().getCommandApdu().getP1()).isEqualTo((byte) 0x3F);
        assertThat(fiRaCommandCaptor.getValue().getCommandApdu().getP2()).isEqualTo((byte) 0xFF);

        // success get session data from local applet
        cbCaptor.getValue().onSuccess(
                DataTypeConversionUtil.hexStringToByteArray("BF780480020101"));

        verify(mSecureSessionCallback)
                .onSessionDataReady(eq(UNIQUE_SESSION_ID), any(), eq(false));
    }

    @Test
    public void failToGetSessionDataFromLocalAppletAsRdsAvailable() {
        getControleeSessionData();
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "711380018181029000E10A80010081010282020101");
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);
        mTestLooper.dispatchAll();

        ArgumentCaptor<FiRaSecureChannel.ExternalRequestCallback> cbCaptor =
                ArgumentCaptor.forClass(FiRaSecureChannel.ExternalRequestCallback.class);
        // send getSessionData from local applet.
        verify(mFiRaSecureChannel).sendLocalFiRaCommand(
                any(), cbCaptor.capture());

        // fail to get session data from local applet
        cbCaptor.getValue().onFailure();

        verify(mSecureSessionCallback).onSessionAborted();
    }

    @Test
    public void getControleeSessionDataSuccessResponseWithoutRdsAvailable() {
        getControleeSessionData();
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "7112800181810DBF780A80020101810400000001");
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);
        mTestLooper.dispatchAll();

        ArgumentCaptor<FiRaSecureChannel.ExternalRequestCallback> cbCaptor =
                ArgumentCaptor.forClass(FiRaSecureChannel.ExternalRequestCallback.class);
        ArgumentCaptor<FiRaCommand> fiRaCommandCaptor =
                ArgumentCaptor.forClass(FiRaCommand.class);
        // send PutSessionData to local applet.
        verify(mFiRaSecureChannel).sendLocalFiRaCommand(
                fiRaCommandCaptor.capture(), cbCaptor.capture());
        assertThat(fiRaCommandCaptor.getValue().getCommandApdu().getIns()).isEqualTo((byte) 0xDB);
        assertThat(fiRaCommandCaptor.getValue().getCommandApdu().getP1()).isEqualTo((byte) 0x3F);
        assertThat(fiRaCommandCaptor.getValue().getCommandApdu().getP2()).isEqualTo((byte) 0xFF);

        // success put session data to local applet
        cbCaptor.getValue().onSuccess(new byte[0]);

        verify(mSecureSessionCallback)
                .onSessionDataReady(eq(UNIQUE_SESSION_ID), any(), eq(false));
    }

    @Test
    public void failedPutSessionDataToLocalApplet() {
        getControleeSessionData();
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "710C8001818107BF780480020101");
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);
        mTestLooper.dispatchAll();

        ArgumentCaptor<FiRaSecureChannel.ExternalRequestCallback> cbCaptor =
                ArgumentCaptor.forClass(FiRaSecureChannel.ExternalRequestCallback.class);
        // send PutSessionData to local applet.
        verify(mFiRaSecureChannel).sendLocalFiRaCommand(
                any(), cbCaptor.capture());

        // success put session data to local applet
        cbCaptor.getValue().onFailure();

        verify(mSecureSessionCallback).onSessionAborted();
    }

    @Test
    public void getControleeSessionDataResponseWithTargetRemoteData() {
        getControleeSessionData();
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "710780018081029000"); // outbound to remote
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);
        mTestLooper.dispatchAll();

        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mFiRaSecureChannel).sendRawDataToRemote(dataCaptor.capture());
        assertThat(dataCaptor.getValue())
                .isEqualTo(DataTypeConversionUtil.hexStringToByteArray("9000"));
    }

    @Test
    public void getControleeSessionDataRetry() {
        getControleeSessionData();
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "71088001818103870100"); // session data not available.
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);
        mTestLooper.dispatchAll();

        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mFiRaSecureChannel, times(2)).tunnelToRemoteDevice(dataCaptor.capture(), any());
        assertThat(DataTypeConversionUtil.byteArrayToHexString(dataCaptor.getValue()))
                .startsWith("00CB3FFF"); // GET DO command
        assertThat(DataTypeConversionUtil.byteArrayToHexString(dataCaptor.getValue()))
                .contains("BF78");  // SessionData DO tag
    }

    @Test
    public void abortSessionNotification() {
        getControleeSessionData();

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
        getControleeSessionData();

        mSecureChannelCallbackCaptor.getValue().onDispatchCommandFailure();
        mTestLooper.dispatchAll();

        verify(mFiRaSecureChannel).terminateLocally();
        verify(mSecureSessionCallback).onSessionAborted();
    }
}
